package org.dromara.aigov.knowledge.pipeline;

import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 分段器（设计 §6.4 的 {@code CHUNKING} 阶段）。
 *
 * <h3>三条规则、每条都对应一个具体后果</h3>
 * <ol>
 *     <li><b>短块按顺序合并到目标长度</b>：TXT/Markdown 的"来源块"是行，一行一段会让检索结果
 *         只剩半句话，也会把分段上限用光。合并后定位写成<b>范围</b>（如 {@code 行 12～行 18}、
 *         {@code 页 1～页 3}），而不是只留第一条的定位——不然引用会指向比实际内容更窄的位置；</li>
 *     <li><b>不跨来源块重叠，只在超长块内部重叠</b>：跨块重叠会把两个页码的正文并进同一条分段，
 *         而一个分段只能有一个定位；同一块内部重叠没有歧义，反而能避免"答案正好落在切口两侧"；</li>
 *     <li><b>超出上限直接失败</b>：截断会让"索引成功"与"资料只进去一部分"同时成立，
 *         而检索只会给出"资料里没有"的错误答案。宁可失败。</li>
 * </ol>
 *
 * <p>分段长度按<b>字符</b>计（不是 token）：这里的长度只用来控制单段体量，与模型的 token 预算无关；
 * 真正的 token 预算属于执行计划（设计 §7.2）。</p>
 *
 * @author ai-gov
 */
public final class AigKnowledgeChunker {

    /**
     * 合并时块与块之间的分隔符。
     */
    private static final char JOIN = '\n';

    /**
     * 定位范围的分隔符（用全角波浪号，避免与定位里可能出现的 ASCII 字符混淆）。
     */
    private static final String RANGE = "～";

    private AigKnowledgeChunker() {
    }

    /**
     * 分段。
     *
     * @param blocks     来源块（非空）
     * @param properties 管道参数
     * @return 分段（至少一条；按来源块顺序）
     */
    public static List<DraftChunk> chunk(List<KnowledgeParsePort.ParsedBlock> blocks,
                                         AigKnowledgePipelineProperties properties) {
        int target = Math.max(1, properties.getChunkTargetChars());
        int overlap = Math.max(0, Math.min(properties.getChunkOverlapChars(), target - 1));
        int maxChunks = Math.max(1, properties.getMaxChunksPerVersion());
        List<DraftChunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String startLocator = null;
        String endLocator = null;
        for (KnowledgeParsePort.ParsedBlock block : blocks) {
            String text = block.text() == null ? "" : block.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > target) {
                // 超长块：先结算已积累的短块，再在块内切并保留重叠
                if (buffer.length() > 0) {
                    chunks.add(join(buffer, startLocator, endLocator));
                    buffer.setLength(0);
                    startLocator = null;
                }
                guardLimit(chunks, maxChunks);
                int part = 0;
                int start = 0;
                while (start < text.length()) {
                    int end = Math.min(text.length(), start + target);
                    part++;
                    String piece = text.substring(start, end).strip();
                    if (!piece.isEmpty()) {
                        chunks.add(new DraftChunk(piece, block.locator() + "（片段 " + part + "）"));
                        guardLimit(chunks, maxChunks);
                    }
                    if (end >= text.length()) {
                        break;
                    }
                    // overlap <= target-1（构造时已夹住），所以 start 必定前进，不存在死循环
                    start = end - overlap;
                }
                endLocator = null;
                continue;
            }
            if (buffer.length() > 0 && buffer.length() + 1 + text.length() > target) {
                chunks.add(join(buffer, startLocator, endLocator));
                buffer.setLength(0);
                startLocator = null;
                guardLimit(chunks, maxChunks);
            }
            if (buffer.length() == 0) {
                startLocator = block.locator();
            }
            if (buffer.length() > 0) {
                buffer.append(JOIN);
            }
            buffer.append(text);
            endLocator = block.locator();
        }
        if (buffer.length() > 0) {
            chunks.add(join(buffer, startLocator, endLocator));
        }
        if (chunks.isEmpty()) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                "分段结果为空：解析出的来源块全是空白（上游解析器本不该产出空块）");
        }
        guardLimit(chunks, maxChunks);
        return chunks;
    }

    /**
     * 结算缓冲区为一条分段。
     *
     * @param buffer       文本缓冲
     * @param startLocator 起始定位
     * @param endLocator   结束定位
     * @return 分段
     */
    private static DraftChunk join(StringBuilder buffer, String startLocator, String endLocator) {
        String locator = startLocator == null ? endLocator
            : (endLocator == null || endLocator.equals(startLocator) ? startLocator : startLocator + RANGE + endLocator);
        return new DraftChunk(buffer.toString().strip(), locator == null ? "" : locator);
    }

    /**
     * 上限守卫。
     *
     * @param chunks   已产出分段
     * @param maxChunks 上限
     */
    private static void guardLimit(List<DraftChunk> chunks, int maxChunks) {
        if (chunks.size() > maxChunks) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.CHUNK_LIMIT_EXCEEDED,
                "解析出的分段数超过上限 " + maxChunks + "（已到 " + chunks.size()
                    + "），已拒绝索引：只索引一部分会让检索结果看起来正常、实际资料不全。"
                    + "请拆分资料，或调整 aigov.knowledge.pipeline.max-chunks-per-version。");
        }
    }

    /**
     * 一条待写入的分段。
     *
     * @param text    正文（入库时会被截成引用预览，见 {@code excerpt-max-chars}）
     * @param locator 来源定位
     */
    public record DraftChunk(String text, String locator) {
    }

}
