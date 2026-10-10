package org.dromara.aigov.studio.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 训练草稿详情视图（专题 C §C3）。
 *
 * <p><b>{@link #unpublishedChanges} 是服务端算出来的事实</b>，不是前端布尔量：
 * 判据是 {@code contentHash != lastPublishedHash}（从未提交过则视为有未发布改动）。
 * 放在服务端算，刷新页面/换设备都不会丢或误报。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioDraftDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 训练草稿ID
     */
    private Long draftId;

    /**
     * 关联的 Agent 定义
     */
    private Long agentId;

    /**
     * 训练对象编码
     */
    private String agentCode;

    /**
     * 归属组织
     */
    private Long orgId;

    /**
     * 责任人
     */
    private Long ownerId;

    /**
     * 当前修订号
     */
    private Integer latestRevision;

    /**
     * 当前内容（规范化 JSON）
     */
    private String contentJson;

    /**
     * 当前内容哈希
     */
    private String contentHash;

    /**
     * 最近一次提交/发布时的内容哈希
     */
    private String lastPublishedHash;

    /**
     * 最近一次提交产生的 Agent 版本
     */
    private Long agentVersionId;

    /**
     * 草稿状态（code）
     */
    private String status;

    /**
     * 草稿状态的中文描述（服务端填充，避免页面各写一套映射）
     */
    private String statusLabel;

    /**
     * 是否有未提交的改动（服务端按哈希判定）
     */
    private Boolean unpublishedChanges;

    /**
     * 本次调用是否产生了新修订（保存/回滚时为 true；内容与当前一致时为 false）
     */
    private Boolean revisionCreated;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
