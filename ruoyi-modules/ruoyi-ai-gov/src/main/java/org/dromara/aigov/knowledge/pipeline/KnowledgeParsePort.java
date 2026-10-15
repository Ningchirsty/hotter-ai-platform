package org.dromara.aigov.knowledge.pipeline;

import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;

import java.util.List;

/**
 * 知识文档解析端口（设计 §6.4 的 {@code PARSING} 阶段；附件「知识处理引擎：优先内部可部署，按试点评测选型」）。
 *
 * <h3>为什么先定端口、再接引擎</h3>
 * <p>附件没有把引擎定死，而是要求"内部可部署 + 试点评测选型"。如果直接在服务里 {@code new} 一个
 * 具体引擎，那么评测结论出来要换引擎时，改动会落在业务代码里；先有端口，换引擎就是"换一个
 * {@link #engineId()} 与实现"，业务路径不动（切换点见
 * {@code AigKnowledgePipelineProperties#setParserEngineId}）。</p>
 *
 * <h3>三条硬约束（都被测试钉住）</h3>
 * <ol>
 *     <li><b>取不到文本就失败，不许返回空</b>：空结果会一路变成"索引成功、检索不到"，
 *         看起来像"资料里没写"，实际是解析没成。所以引擎必须抛
 *         {@link AigKnowledgePipelineException}（分类 {@code PARSE_FAILED}）；</li>
 *     <li><b>定位（locator）随文本一起产出</b>：分段必须能回答"这段出自第几页/第几段"，
 *         否则引用无法点回来源（设计 §7.4）；</li>
 *     <li><b>不合并来源块</b>：一个 {@link ParsedBlock} 只能有一个定位，跨页/跨段合并会让引用说谎。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface KnowledgeParsePort {

    /**
     * 引擎标识（写入 {@code aig_knowledge_processing_job} 相关日志与索引引用，用于回答"这条分段是哪个引擎解的"）。
     *
     * @return 稳定的引擎 ID
     */
    String engineId();

    /**
     * 是否处理该来源类型。
     *
     * @param sourceType 来源类型
     * @return 支持返回 true
     */
    boolean supports(AigKnowledgeSourceTypeEnum sourceType);

    /**
     * 解析。
     *
     * @param request 解析请求
     * @return 解析结果（至少一个非空文本块）
     */
    ParseResult parse(ParseRequest request);

    /**
     * 解析请求。
     *
     * @param content    文件字节（必填、非空）
     * @param sourceType 来源类型（必填；由登记时的扩展名判定）
     * @param fileName   原文件名（用于错误文案，让人知道是哪份文件失败）
     */
    record ParseRequest(byte[] content, AigKnowledgeSourceTypeEnum sourceType, String fileName) {

        /**
         * 校验"内容不为空"这一条前置条件——放在构造器里，任何引擎都不可能绕过。
         */
        public ParseRequest {
            if (content == null || content.length == 0) {
                throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                    "文件内容为空，无法解析：" + fileName);
            }
            if (sourceType == null) {
                throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_NO_ENGINE,
                    "没有来源类型，无法选择解析引擎：" + fileName);
            }
        }
    }

    /**
     * 一个来源块（页/段/行）。
     *
     * @param text    文本（非空）
     * @param locator 来源定位（如 {@code 页 3}、{@code 第 12 段}、{@code 行 88}）
     */
    record ParsedBlock(String text, String locator) {
    }

    /**
     * 解析结果。
     *
     * @param engineId 产出该结果的引擎
     * @param encoding 编码/格式说明（如 {@code UTF-8}、{@code GBK}、{@code PDF (内置尽力而为)}）
     * @param blocks   来源块（非空）
     */
    record ParseResult(String engineId, String encoding, List<ParsedBlock> blocks) {

        /**
         * 校验结果可用性（"非空"这条不能靠引擎自觉）。
         */
        public ParseResult {
            if (blocks == null || blocks.isEmpty()) {
                throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                    "解析引擎 " + engineId + " 没有产出任何文本块（这会被读成「资料里没写」，所以必须失败）");
            }
        }

        /**
         * 全部文本（按块序拼接，块间换行）。
         *
         * @return 文本
         */
        public String fullText() {
            StringBuilder sb = new StringBuilder();
            for (ParsedBlock block : blocks) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(block.text());
            }
            return sb.toString();
        }
    }

}
