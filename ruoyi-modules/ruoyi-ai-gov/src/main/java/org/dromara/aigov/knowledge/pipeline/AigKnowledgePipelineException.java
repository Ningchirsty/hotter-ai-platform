package org.dromara.aigov.knowledge.pipeline;

/**
 * 知识处理管道失败（带错误分类，设计 §6.4 的 {@code error_code}）。
 *
 * <p><b>为什么不直接抛 ServiceException</b>：{@code ServiceException} 是本仓的 {@code final} 类型，
 * 无法细分；而知识处理失败<b>必须带分类落库</b>（{@code aig_knowledge_processing_job.error_code}），
 * 否则运维只能看到一句人话，没法按类统计"是解析不出来的多、还是索引写不进去的多"。</p>
 *
 * <p>错误码取值见 {@link #PARSE_NO_ENGINE} 等常量；写库时原样落到 {@code error_code}。</p>
 *
 * @author ai-gov
 */
public class AigKnowledgePipelineException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 没有可用于该来源类型的解析引擎（多半是接了新格式但没实现引擎）。 */
    public static final String PARSE_NO_ENGINE = "PARSE_NO_ENGINE";

    /** 文本编码无法识别（既不是 UTF-8 也不是 GBK）。 */
    public static final String PARSE_ENCODING_UNKNOWN = "PARSE_ENCODING_UNKNOWN";

    /** 解析本身失败（文件损坏、结构不符、取不到任何文本）。 */
    public static final String PARSE_FAILED = "PARSE_FAILED";

    /** 分段数超过上限（拒绝"只索引一部分"）。 */
    public static final String CHUNK_LIMIT_EXCEEDED = "CHUNK_LIMIT_EXCEEDED";

    /** 嵌入/向量索引写入失败。 */
    public static final String INDEX_FAILED = "INDEX_FAILED";

    /**
     * 失败分类（落 {@code error_code}）。
     */
    private final String errorCode;

    /**
     * 构造一个带分类的处理失败。
     *
     * @param errorCode 失败分类
     * @param message   可读原因（会原样展示给维护人，必须写清"哪一步、为什么"）
     */
    public AigKnowledgePipelineException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造一个带分类与根因的处理失败。
     *
     * @param errorCode 失败分类
     * @param message   可读原因
     * @param cause     根因
     */
    public AigKnowledgePipelineException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 失败分类。
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

}
