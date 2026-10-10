package org.dromara.aigov.workspace.portal.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 员工 AI 工作台偏好 {@code aig_user_workspace_pref}（主文档线增量 5）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_user_workspace_pref.sql} 的列注释为准</b>。</p>
 *
 * <p><b>它只是偏好</b>：收藏与默认岗位都不参与可见性判定。把偏好当授权用，
 * 会出现"收藏过的岗位在停用后仍然看得到"这类问题。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_user_workspace_pref")
public class AigUserWorkspacePref extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 偏好ID
     */
    @TableId(value = "pref_id")
    private Long prefId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 组织（无组织用 0；不能为 NULL，见脚本注释）
     */
    private Long orgId;

    /**
     * 默认打开的岗位编码
     */
    private String defaultRoleCode;

    /**
     * 收藏的岗位编码清单（JSON 数组字符串）
     */
    private String favoritesJson;

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
     * 备注
     */
    private String remark;

}
