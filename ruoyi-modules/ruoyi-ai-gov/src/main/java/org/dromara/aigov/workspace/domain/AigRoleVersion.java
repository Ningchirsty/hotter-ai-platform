package org.dromara.aigov.workspace.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位版本 {@code aig_role_version}（附件 §4、§5.1、§4.2）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_role_workspace.sql} 的列注释为准</b>。</p>
 *
 * <p><b>发布后不可变</b>：要改就出新版本（与 {@code aig_agent_version} 同一口径）。
 * {@link #manifestSha256} 是"这份清单有没有被改过 / 有没有未发布改动"的唯一判据，
 * 由 {@code AigRoleManifestHasher} 算（复用训练台那份规范化实现，避免两份漂移）。</p>
 *
 * <p><b>{@link #releaseStatus} 与"能力是否 STABLE"是两回事</b>：岗位状态判的是
 * "卡片上没上架、给谁看"（{@code AigRoleReleaseStatusEnum}），
 * 能力状态判的是"这个 Agent/Skill 能不能用"（{@code AigReleaseStatusEnum}）。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_role_version")
public class AigRoleVersion extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位版本ID
     */
    @TableId(value = "role_version_id")
    private Long roleVersionId;

    /**
     * 所属岗位
     */
    private Long roleId;

    /**
     * 版本号（同岗位内唯一；发布后不可改）
     */
    private String version;

    /**
     * 岗位发布状态（{@code AigRoleReleaseStatusEnum}）
     */
    private String releaseStatus;

    /**
     * 灰度通道（{@code AigReleaseChannelEnum}）
     */
    private String rolloutChannel;

    /**
     * 岗位包清单（含 category 列表；action.category_code 必须能在这里找到）
     */
    private String manifestJson;

    /**
     * 清单哈希（规范化 JSON 的 sha256）
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
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 版本说明
     */
    private String remark;

}
