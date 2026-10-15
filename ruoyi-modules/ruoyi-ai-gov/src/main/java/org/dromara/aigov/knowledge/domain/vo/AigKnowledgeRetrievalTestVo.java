package org.dromara.aigov.knowledge.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识检索测试结果（设计 §7.4：片段、来源、文档版本、页码/章节、分值、等级）。
 *
 * <p>每条引用都带 {@code chunkId/versionId/documentId}，页面据此能点回"当前用户有权读取"的来源；
 * 引用 ID 必须来自服务端合法集合核验（防止模型或缓存带进不存在的来源）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigKnowledgeRetrievalTestVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 命中的片段
     */
    private List<Item> citations = new ArrayList<>();

    /**
     * 实际使用的检索模式（{@code KEYWORD} / {@code VECTOR}）。
     *
     * <p>回显是为了评测对比时不会把两种模式的结论搞混：同一句话在两种模式下的命中往往不同，
     * 少了这个字段，结果就只能靠调用方自己记着"刚才点的是哪个"。</p>
     */
    private String mode;

    /**
     * 是否"没有支持资料"
     */
    private Boolean noSupport;

    /**
     * 说明（noSupport 时给出原因）
     */
    private String reason;

    /**
     * 单条引用。
     */
    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 片段ID */
        private Long chunkId;

        /** 文档版本ID */
        private Long versionId;

        /** 文档ID */
        private Long documentId;

        /** 来源名（文档名） */
        private String sourceName;

        /** 来源定位（页码/标题/行列） */
        private String locator;

        /** 引用预览 */
        private String excerptRef;

        /** 片段数据等级 */
        private String dataLevel;
    }

}
