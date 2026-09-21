package org.dromara.hrtalent.domain.bo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.TalentPool;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才池业务对象 hr_talent_pool（SPEC-P4 §2.3 C 线）。
 *
 * <p>入参只覆盖可编辑字段：{@code pool_code} 由服务端生成、{@code member_count} 由服务层重算，
 * 均不接受前端写入。可见范围只接受字典 {@code talent_visibility_type} 的稳定编码。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentPool.class, reverseConvertGenerate = false)
public class TalentPoolBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才池ID（编辑时必填）
     */
    @NotNull(message = "人才池ID不能为空", groups = {EditGroup.class})
    private Long poolId;

    /**
     * 人才池名称
     */
    @NotBlank(message = "人才池名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 100, message = "人才池名称长度不能超过 100", groups = {AddGroup.class, EditGroup.class})
    private String poolName;

    /**
     * 人才池类型（reserve储备/position岗位定向/talent专项等稳定编码）
     */
    @Size(max = 32, message = "人才池类型长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String poolType;

    /**
     * 归属部门ID
     */
    private Long ownerDeptId;

    /**
     * 归属部门名称快照
     */
    @Size(max = 100, message = "归属部门名称长度不能超过 100", groups = {AddGroup.class, EditGroup.class})
    private String ownerDeptName;

    /**
     * 池管理员用户ID
     */
    private Long managerId;

    /**
     * 可见范围（字典 talent_visibility_type 编码：group/company/department/owner/explicit）
     */
    @Size(max = 32, message = "可见范围长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String visibilityType;

    /**
     * 人才池说明
     */
    @Size(max = 500, message = "人才池说明长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String poolDesc;

    /**
     * 状态（active启用/archived归档等稳定编码）
     */
    @Size(max = 32, message = "状态长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
