package org.dromara.aigov.knowledge.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;
import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识处理器：解析 → 分段 → 嵌入（设计 §6.4 的 {@code PARSING}/{@code CHUNKING}/{@code INDEXING}）。
 *
 * <p><b>纯计算，不碰数据库</b>：入参是字节，出参是"待落库的分段 + 向量"。这样整条处理链可以脱离
 * 数据库单测（解析引擎的坑、分段边界的坑都能被钉住），落库与状态机交给
 * {@code IAigKnowledgeIndexingService}。反过来，把 JDBC 混进来之后，"PDF 少了半页"这类问题
 * 就只能靠查库发现。</p>
 *
 * <h3>为什么处理器自己不做授权</h3>
 * <p>数据等级由集合决定、授权范围由调用方决定（设计 §7.2 的逐层相交）。处理器只接受一个已经
 * 定好的 {@code dataLevel} 并原样贴到每条分段上；它不做"猜等级"的动作——猜错的代价是把受限资料
 * 标成公开。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigKnowledgeProcessor {

    private final AigKnowledgeParseRegistry parseRegistry;

    private final KnowledgeEmbeddingPort embeddingPort;

    private final AigKnowledgePipelineProperties properties;

    /**
     * 处理一份文件。
     *
     * @param content    文件字节
     * @param sourceType 来源类型
     * @param fileName   文件名（错误文案用）
     * @param dataLevel  本级数据等级（由集合决定，原样贴到每条分段）
     * @return 处理结果（分段 + 向量）
     */
    public ProcessingResult process(byte[] content, AigKnowledgeSourceTypeEnum sourceType, String fileName,
                                    String dataLevel) {
        assertConfig();
        KnowledgeParsePort.ParseResult parsed = parseRegistry.parse(
            new KnowledgeParsePort.ParseRequest(content, sourceType, fileName));
        List<AigKnowledgeChunker.DraftChunk> drafts = AigKnowledgeChunker.chunk(parsed.blocks(), properties);

        int excerptMax = Math.max(1, properties.getExcerptMaxChars());
        List<ProcessedChunk> chunks = new ArrayList<>(drafts.size());
        int ordinal = 0;
        for (AigKnowledgeChunker.DraftChunk draft : drafts) {
            ordinal++;
            float[] vector;
            try {
                vector = embeddingPort.embed(draft.text());
            } catch (RuntimeException e) {
                throw new AigKnowledgePipelineException(AigKnowledgePipelineException.INDEX_FAILED,
                    "嵌入失败（engine=" + embeddingPort.engineId() + "，第 " + ordinal + " 段）：" + fileName, e);
            }
            chunks.add(new ProcessedChunk(ordinal, draft.text(), draft.locator(), dataLevel,
                excerpt(draft.text(), excerptMax), vector));
        }
        log.info("知识处理完成, file={}, parser={}, embedding={}, chunks={}, excerptMax={}",
            fileName, parsed.engineId(), embeddingPort.engineId(), chunks.size(), excerptMax);
        return new ProcessingResult(parsed.engineId(), embeddingPort.engineId(), parsed.encoding(), chunks);
    }

    /**
     * 配置自检：分段长度不得超过引用预览列宽。
     *
     * <p>分段正文只以 {@code text_excerpt} 落库（正文不落业务表，设计 §10.2）。如果分段长度超过
     * 预览列宽，超出部分<b>不会有任何地方存放</b>——检索到的资料就"被截掉了一截"，而界面上完全
     * 看不出来。这是配置错误，必须在处理前挡住，而不是在处理后截断。</p>
     */
    private void assertConfig() {
        if (properties.getChunkTargetChars() > properties.getExcerptMaxChars()) {
            throw new ServiceException("配置错误：aigov.knowledge.pipeline.chunk-target-chars("
                + properties.getChunkTargetChars() + ") 大于 excerpt-max-chars("
                + properties.getExcerptMaxChars() + ")。分段正文只以引用预览落库，超出的部分无处存放，"
                + "会让检索结果被静默截断。请调小分段长度或调大预览列宽。");
        }
    }

    /**
     * 截出引用预览。
     *
     * @param text 正文
     * @param max  上限
     * @return 预览
     */
    static String excerpt(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        // 截断处补省略号：让"预览不是全文"这件事在界面上看得出来，
        // 而不是让人以为资料只有这么长（text_ref 侧才是全文）
        return text.substring(0, Math.max(1, max - 1)) + "…";
    }

    /**
     * 处理结果。
     *
     * @param parserEngineId    解析引擎（回答"这份资料是哪个引擎解的"）
     * @param embeddingEngineId 嵌入引擎（回答"这些向量是哪个模型产的"）
     * @param encoding          编码/格式说明
     * @param chunks            分段（ordinal 从 1 起，连续）
     */
    public record ProcessingResult(String parserEngineId, String embeddingEngineId, String encoding,
                                   List<ProcessedChunk> chunks) {
    }

    /**
     * 一条处理好的分段。
     *
     * @param ordinal   段序（1 起）
     * @param text      正文（用于落库前的预览计算与索引引用）
     * @param locator   来源定位
     * @param dataLevel 数据等级
     * @param excerpt   引用预览（已按列宽截断）
     * @param vector    向量
     */
    public record ProcessedChunk(int ordinal, String text, String locator, String dataLevel, String excerpt,
                                 float[] vector) {
    }

}
