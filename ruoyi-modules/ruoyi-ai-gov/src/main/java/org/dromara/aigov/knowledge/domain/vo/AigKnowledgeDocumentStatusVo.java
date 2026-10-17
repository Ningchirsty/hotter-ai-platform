package org.dromara.aigov.knowledge.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 集合内「文档 + 各自版本」的只读视图（知识中心文档表的状态列与摘要）。
 *
 * <p><b>为什么需要它</b>：知识中心这一页真正要回答的是"<b>我登记的资料现在能不能被检索</b>"。
 * 而文档表原来只有文档ID/文档名/来源类型/责任人——"能不能检索"取决于<b>版本</b>的处理状态
 * （只有 {@code READY} 的版本才会进检索），这两个信息分在两张表里，页面上拼不出来：
 * 要么手抄版本ID去另一个弹窗里查，要么干脆不知道。</p>
 *
 * <p>所以这里一次把"集合内所有文档 + 它们的所有版本"拿回来（一次查询，不是按文档 N+1），
 * 由前端按 {@code versionId} 取最新版本展示状态胶囊。**不在这里算"最新"**：最新 = 版本ID最大，
 * 这是展示口径，放前端纯函数里更好测，也不至于把"状态"和"顺序"两个概念混在服务端。</p>
 *
 * @author ai-gov
 */
@Data
public class AigKnowledgeDocumentStatusVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 文档名
     */
    private String sourceName;

    /**
     * 来源类型（PDF/DOCX/TXT/MARKDOWN）
     */
    private String sourceType;

    /**
     * 责任人
     */
    private Long ownerId;

    /**
     * 该文档的全部版本（按版本ID倒序，最新的在前；没有版本时为空列表而不是 null）。
     */
    private List<VersionBrief> versions = new ArrayList<>();

    /**
     * 版本摘要（只带页面要展示的字段，不带原文哈希与存储引用）。
     */
    @Data
    public static class VersionBrief implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 版本ID
         */
        private Long versionId;

        /**
         * 版本号（同文档内唯一）
         */
        private String version;

        /**
         * 处理/生效状态（UPLOADED/PARSED/CHUNKED/EMBEDDED/READY/FAILED/RETIRED）
         */
        private String status;

        /**
         * 生效开始
         */
        private LocalDateTime effectiveFrom;

        /**
         * 生效结束
         */
        private LocalDateTime effectiveTo;

        /**
         * 登记时间
         */
        private LocalDateTime createTime;

    }

}
