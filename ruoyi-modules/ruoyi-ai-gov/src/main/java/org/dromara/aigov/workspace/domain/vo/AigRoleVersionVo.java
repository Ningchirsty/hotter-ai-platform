package org.dromara.aigov.workspace.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位包版本视图（主文档线增量 1b）。
 *
 * <p>{@link #allowedTransitions} 与 {@link #visibleToEmployees} <b>由服务端算好</b>，
 * 不让前端自己判断：允许的流转是一张写死的边表，可见性是"只有 PUBLISHED"。
 * 若前端各写一份判断，迟早会出现"界面上说能点、后端拒绝"或
 * "界面看不到但门户里能看见"的两套口径。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRoleVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位版本ID
     */
    private Long roleVersionId;

    /**
     * 岗位定义ID
     */
    private Long roleId;

    /**
     * 岗位编码
     */
    private String roleCode;

    /**
     * 岗位名称
     */
    private String roleName;

    /**
     * 版本号
     */
    private String version;

    /**
     * 发布状态
     */
    private String releaseStatus;

    /**
     * 灰度通道
     */
    private String rolloutChannel;

    /**
     * 清单哈希（"这份清单是不是这一版"的判据）
     */
    private String manifestSha256;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 停用时间
     */
    private LocalDateTime disabledAt;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 版本说明
     */
    private String remark;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 从当前状态允许流转到哪些状态（服务端按边表算）
     */
    private List<String> allowedTransitions;

    /**
     * 是否对员工可见（只有 PUBLISHED 为 true）
     */
    private Boolean visibleToEmployees;

}
