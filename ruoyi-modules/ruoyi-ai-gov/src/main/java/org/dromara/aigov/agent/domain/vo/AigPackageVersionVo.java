package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigPackageVersion;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Package 版本列表视图。
 *
 * <p><b>刻意不带 {@code manifestJson}</b>：它是 longtext，列表页一页几十条全掏出来
 * 只是为了让用户扫一眼版本号。要看 Manifest 原文请走详情接口（或直接查实体）——
 * 这条裁剪与 {@code AigTaskVo} 对快照 JSON 的处理同口径。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigPackageVersion.class)
public class AigPackageVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 版本ID
     */
    private Long packageVersionId;

    /**
     * 所属 Package
     */
    private Long packageId;

    /**
     * 版本号
     */
    private String version;

    /**
     * Manifest 哈希（列表保留，便于肉眼核对与实体一致）
     */
    private String manifestHash;

    /**
     * 扫描结论
     */
    private String scanResult;

    /**
     * 扫描说明
     */
    private String scanDetail;

    /**
     * 发布状态
     */
    private String releaseStatus;

    /**
     * 发布通道
     */
    private String releaseChannel;

    /**
     * 沙箱测试项目
     */
    private Long sandboxProjectId;

    /**
     * 最近一次评测运行ID
     */
    private Long evaluationRunId;

    /**
     * 回滚目标版本
     */
    private Long rollbackTargetVersionId;

    /**
     * 审批人
     */
    private Long approvedBy;

    /**
     * 审批时间
     */
    private LocalDateTime approvedAt;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 版本说明
     */
    private String remark;

}
