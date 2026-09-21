package org.dromara.hrtalent.domain.bo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.converter.AssistantIdsConverter;
import org.dromara.hrtalent.domain.entity.TalentProfile;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 人才主档业务对象 hr_talent_profile（SPEC-P3 §2.1 / §3.1）。
 *
 * <p><b>联系方式说明</b>：入参只提供电话/邮箱<b>明文</b>（{@code phone} / {@code email}），
 * 服务层负责标准化与哈希计算，并写入 {@code phone_cipher} / {@code email_cipher} 密文列；
 * 本对象<b>不</b>承载 {@code phone_hash} / {@code email_hash}，避免前端伪造哈希绕过查重。</p>
 *
 * <p><b>服务端权威字段</b>：人才编号、状态、版本号、合并目标均由服务端维护，不在本对象内。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentProfile.class, reverseConvertGenerate = false, uses = AssistantIdsConverter.class)
public class TalentProfileBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才主档ID（编辑时必填）
     */
    @NotNull(message = "人才ID不能为空", groups = {EditGroup.class})
    private Long talentId;

    /**
     * 姓名
     */
    @NotBlank(message = "姓名不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 64, message = "姓名长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String name;

    /**
     * 曾用名（或英文名）
     */
    @Size(max = 64, message = "曾用名长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String formerName;

    /**
     * 性别（字典编码，如 male/female/unknown）
     */
    @Size(max = 32, message = "性别长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String gender;

    /**
     * 出生日期
     */
    private LocalDate birthDate;

    /**
     * 年龄快照（仅导入原值，不反推出生日期）
     */
    @PositiveOrZero(message = "年龄快照不能为负数", groups = {AddGroup.class, EditGroup.class})
    private Integer ageSnapshot;

    /**
     * 最高学历（字典编码）
     */
    @Size(max = 32, message = "最高学历长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String highestEducation;

    /**
     * 电话明文（服务端负责标准化、哈希与密文落库）
     */
    @Size(max = 64, message = "电话长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String phone;

    /**
     * 备用手机号明文
     */
    @Size(max = 64, message = "备用手机号长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String backupPhone;

    /**
     * 邮箱明文（服务端负责小写标准化、哈希与密文落库）
     */
    @Email(message = "邮箱格式不正确", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 128, message = "邮箱长度不能超过 128", groups = {AddGroup.class, EditGroup.class})
    private String email;

    /**
     * 其他联系方式明文（如微信/QQ 等）
     */
    @Size(max = 128, message = "其他联系方式长度不能超过 128", groups = {AddGroup.class, EditGroup.class})
    private String otherContact;

    /**
     * 当前所在城市
     */
    @Size(max = 64, message = "当前所在城市长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String currentCity;

    /**
     * 期望工作城市
     */
    @Size(max = 64, message = "期望工作城市长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String expectedCity;

    /**
     * 当前公司
     */
    @Size(max = 200, message = "当前公司长度不能超过 200", groups = {AddGroup.class, EditGroup.class})
    private String currentCompany;

    /**
     * 当前职位
     */
    @Size(max = 200, message = "当前职位长度不能超过 200", groups = {AddGroup.class, EditGroup.class})
    private String currentPosition;

    /**
     * 期望岗位（弱匹配依赖字段）
     */
    @Size(max = 200, message = "期望岗位长度不能超过 200", groups = {AddGroup.class, EditGroup.class})
    private String expectedPosition;

    /**
     * 期望薪资下限（不得大于上限）
     */
    @PositiveOrZero(message = "期望薪资下限不能为负数", groups = {AddGroup.class, EditGroup.class})
    private BigDecimal expectedSalaryMin;

    /**
     * 期望薪资上限（不得小于下限）
     */
    @PositiveOrZero(message = "期望薪资上限不能为负数", groups = {AddGroup.class, EditGroup.class})
    private BigDecimal expectedSalaryMax;

    /**
     * 工作年限（非负整数）
     */
    @PositiveOrZero(message = "工作年限不能为负数", groups = {AddGroup.class, EditGroup.class})
    private Integer workYears;

    /**
     * 所属行业
     */
    @Size(max = 64, message = "所属行业长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String industry;

    /**
     * 人才归属（负责人）用户ID
     */
    private Long ownerId;

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
     * 协助人用户ID数组（入库时以英文逗号拼接为 {@code assistant_ids}）
     */
    @Size(max = 20, message = "协助人最多 20 人", groups = {AddGroup.class, EditGroup.class})
    private Long[] assistantIds;

    /**
     * 可见范围（group/company/department/owner/explicit，字典 talent_visibility_type）
     */
    @Size(max = 32, message = "可见范围长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String visibilityType;

    /**
     * 数据分级（internal/sensitive/highly_sensitive，字典 recruit_data_level）
     */
    @Size(max = 32, message = "数据分级长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String dataLevel;

    /**
     * 主档来源（manual/resume_import/application/merge 等稳定编码）
     */
    @Size(max = 32, message = "主档来源长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String sourceType;

    /**
     * 首次来源渠道ID
     */
    private Long sourceChannelId;

    /**
     * 限制/禁止联系状态的原因（禁止联系状态必须填写）
     */
    @Size(max = 500, message = "状态原因长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String statusReason;

    /**
     * 状态到期日
     */
    private LocalDate statusExpireDate;

    /**
     * 乐观锁版本号（编辑时必填，命中冲突返回中文提示）
     */
    @NotNull(message = "版本号不能为空，请刷新后重试", groups = {EditGroup.class})
    private Integer version;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
