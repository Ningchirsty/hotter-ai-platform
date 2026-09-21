package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.encrypt.annotation.EncryptField;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统一人才主档对象 hr_talent_profile（设计文档 §7.6.1 一人一档原则）。
 *
 * <p><b>一人一档</b>：本表是人员唯一主档，候选人不是独立实体；「候选人列表」是存在应聘记录的人才视图
 * （{@code hr_recruit_application.talent_id} 关联本表）。</p>
 *
 * <p><b>联系方式密文与哈希</b>（设计文档 §9.4/§9.5）：</p>
 * <ul>
 *     <li>{@link #phoneCipher} / {@link #backupPhoneCipher} / {@link #emailCipher} / {@link #otherContactCipher}
 *     通过 {@code @EncryptField} 由入参加密拦截器自动加密落库、由出参解密拦截器自动解密读取，
 *     业务代码不保存明文列；</li>
 *     <li>{@link #phoneHash} / {@link #backupPhoneHash} / {@link #emailHash} 保存<b>标准化后</b>的
 *     SHA-256 小写十六进制哈希，仅用于重复预检，<b>不加唯一约束</b>（兼容家庭共用联系方式等例外场景）。</li>
 * </ul>
 *
 * <p><b>并发控制</b>：{@link #version} 为乐观锁字段（设计文档 §9.6）。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_profile")
public class TalentProfile extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才主档ID（主键）
     */
    @TableId(value = "talent_id")
    private Long talentId;

    /**
     * 人才编号（业务编号，唯一，数据库主键不对外展示）
     */
    private String talentNo;

    /**
     * 姓名
     */
    private String name;

    /**
     * 曾用名（或英文名）
     */
    private String formerName;

    /**
     * 性别（字典编码，如 male/female/unknown）
     */
    private String gender;

    /**
     * 出生日期（优先保存出生日期）
     */
    private LocalDate birthDate;

    /**
     * 年龄快照（仅导入原值，不反推出生日期）
     */
    private Integer ageSnapshot;

    /**
     * 最高学历（字典编码）
     */
    private String highestEducation;

    /**
     * 电话密文（禁止存明文，列表默认脱敏）
     */
    @EncryptField
    private String phoneCipher;

    /**
     * 电话标准化不可逆哈希（SHA-256 小写十六进制，重复预警用，不唯一）
     */
    private String phoneHash;

    /**
     * 备用手机号密文（禁止存明文）
     */
    @EncryptField
    private String backupPhoneCipher;

    /**
     * 备用手机号标准化不可逆哈希（与 phone_hash 同口径）
     */
    private String backupPhoneHash;

    /**
     * 邮箱密文（禁止存明文）
     */
    @EncryptField
    private String emailCipher;

    /**
     * 邮箱标准化（小写）不可逆哈希（SHA-256，重复预警用，不唯一）
     */
    private String emailHash;

    /**
     * 其他联系方式密文（如微信/QQ 等，禁止存明文）
     */
    @EncryptField
    private String otherContactCipher;

    /**
     * 当前所在城市
     */
    private String currentCity;

    /**
     * 期望工作城市
     */
    private String expectedCity;

    /**
     * 当前公司
     */
    private String currentCompany;

    /**
     * 当前职位
     */
    private String currentPosition;

    /**
     * 期望岗位（弱匹配依赖字段）
     */
    private String expectedPosition;

    /**
     * 期望薪资下限
     */
    private BigDecimal expectedSalaryMin;

    /**
     * 期望薪资上限
     */
    private BigDecimal expectedSalaryMax;

    /**
     * 工作年限（非负整数）
     */
    private Integer workYears;

    /**
     * 所属行业
     */
    private String industry;

    /**
     * 人才归属（负责人）用户ID
     */
    private Long ownerId;

    /**
     * 归属部门ID（数据权限按公司/部门判定）
     */
    private Long ownerDeptId;

    /**
     * 归属部门名称快照
     */
    private String ownerDeptName;

    /**
     * 协助人用户ID，多个以英文逗号分隔（可见范围参考）
     */
    private String assistantIds;

    /**
     * 人才生命周期状态（见 {@code TalentStatusEnum}，字典 talent_status）
     */
    private String talentStatus;

    /**
     * 限制/禁止联系状态的原因（禁止联系状态必须填写）
     */
    private String statusReason;

    /**
     * 状态到期日
     */
    private LocalDate statusExpireDate;

    /**
     * 可见范围（group/company/department/owner/explicit，字典 talent_visibility_type）
     */
    private String visibilityType;

    /**
     * 数据分级（internal/sensitive/highly_sensitive，字典 recruit_data_level）
     */
    private String dataLevel;

    /**
     * 当前简历版本ID（同一人才只能有一个当前版本）
     */
    private Long currentResumeId;

    /**
     * 被合并到的目标主档ID（非空表示已合并，不可再新建应聘或改资料）
     */
    private Long mergedToId;

    /**
     * 主档来源（resume_import/manual/application/merge 等稳定编码）
     */
    private String sourceType;

    /**
     * 首次来源渠道ID
     */
    private Long sourceChannelId;

    /**
     * 最近简历更新时间
     */
    private LocalDateTime resumeUpdateTime;

    /**
     * 最近跟进时间
     */
    private LocalDateTime lastFollowTime;

    /**
     * 下次联系时间
     */
    private LocalDateTime nextFollowTime;

    /**
     * 乐观锁版本号（设计文档 §9.6）
     */
    @Version
    private Integer version;

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
