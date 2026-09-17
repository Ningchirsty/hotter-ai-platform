package org.dromara.talent.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.talent.domain.TlTalent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 人才主档业务对象 tl_talent
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlTalent.class, reverseConvertGenerate = false)
public class TlTalentBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才ID
     */
    @NotNull(message = "人才ID不能为空", groups = {EditGroup.class})
    private Long talentId;

    /**
     * 姓名（明文业务字段，按权限显示）
     */
    @NotBlank(message = "姓名不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 50, message = "姓名长度不能超过50个字符", groups = {AddGroup.class, EditGroup.class})
    private String name;

    /**
     * 性别（0未知 1男 2女，字典 tl_gender）
     */
    private String gender;

    /**
     * 出生日期（年龄实时计算，不单独维护）
     */
    private LocalDate birthDate;

    /**
     * 仅识别到的年龄（无出生日期时使用）
     */
    @Min(value = 0, message = "年龄不能小于0", groups = {AddGroup.class, EditGroup.class})
    @Max(value = 150, message = "年龄不能大于150", groups = {AddGroup.class, EditGroup.class})
    private Integer ageOnly;

    /**
     * 识别年龄对应的识别日期
     */
    private LocalDate ageSourceDate;

    /**
     * 手机号（明文入参，服务端标准化后加密落库）
     */
    @NotBlank(message = "手机号不能为空", groups = {AddGroup.class})
    private String phone;

    /**
     * 学历（字典 tl_education）
     */
    private String education;

    /**
     * 期望薪资下限（整数元/月）
     */
    @Min(value = 0, message = "期望薪资下限不能小于0", groups = {AddGroup.class, EditGroup.class})
    private Integer expectSalaryMin;

    /**
     * 期望薪资上限（整数元/月）
     */
    @Min(value = 0, message = "期望薪资上限不能小于0", groups = {AddGroup.class, EditGroup.class})
    private Integer expectSalaryMax;

    /**
     * 应聘/意向岗位
     */
    @Size(max = 100, message = "岗位长度不能超过100个字符", groups = {AddGroup.class, EditGroup.class})
    private String position;

    /**
     * 联系日期
     */
    private LocalDate contactDate;

    /**
     * 归属区域（GROUP/SZ/ST，字典 tl_region）
     */
    @NotBlank(message = "归属区域不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^(GROUP|SZ|ST)$", message = "归属区域只能为 GROUP/SZ/ST",
        groups = {AddGroup.class, EditGroup.class})
    private String regionCode;

    /**
     * 人才状态（字典 tl_talent_status）
     */
    private String status;

    /**
     * 共享范围（REGION区域共享 GROUP全集团 GRANT_ONLY仅授权）
     */
    private String shareScope;

    /**
     * 来源（字典 tl_source）
     */
    private String source;

    /**
     * 备注（禁止写入歧视性/无关敏感标签）
     */
    @Size(max = 500, message = "备注长度不能超过500个字符", groups = {AddGroup.class, EditGroup.class})
    private String remark;

    /**
     * 是否已确认重复（true 时允许在命中重复的情况下继续入库）
     */
    private Boolean duplicateConfirmed;

    /**
     * 重复确认说明
     */
    @Size(max = 255, message = "重复确认说明长度不能超过255个字符", groups = {AddGroup.class, EditGroup.class})
    private String duplicateRemark;

}
