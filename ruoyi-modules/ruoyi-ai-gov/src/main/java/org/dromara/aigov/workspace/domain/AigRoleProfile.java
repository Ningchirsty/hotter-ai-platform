package org.dromara.aigov.workspace.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 岗位定义 {@code aig_role_profile}（附件 §4、§5.1）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_role_workspace.sql} 的列注释为准</b>。</p>
 *
 * <p><b>{@link #roleCode} 不是 {@code sys_role} 的角色</b>：它是"业务层可版本化对象"的稳定编码，
 * 不替换身份角色、也不隐式赋权（附件 §2.3）。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_role_profile")
public class AigRoleProfile extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位定义ID
     */
    @TableId(value = "role_id")
    private Long roleId;

    /**
     * 岗位编码（唯一，跨版本稳定；不是 sys_role 的角色）
     */
    private String roleCode;

    /**
     * 岗位名称
     */
    private String roleName;

    /**
     * 负责人组织编码
     */
    private String ownerOrgCode;

    /**
     * 岗位简介（员工看到的说明）
     */
    private String description;

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
