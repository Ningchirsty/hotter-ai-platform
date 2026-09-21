package org.dromara.hrtalent.domain.vo.talent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.converter.AssistantIdsConverter;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.enums.TalentStatusEnum;
import org.dromara.hrtalent.support.TalentContactCodec;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 人才主档列表视图对象 hr_talent_profile（SPEC-P3 §2.1 {@code GET /talent/profiles}）。
 *
 * <p><b>安全约束</b>：本 VO <b>只</b>返回脱敏电话/邮箱（{@link #phoneMasked} / {@link #emailMasked}），
 * 不包含任何密文字段、其他联系方式或身份证类信息；电话明文只能经
 * {@code POST /recruit/candidates/{id}/phone-view} 获取（并写审计）。</p>
 *
 * <p><b>多值字段陷阱</b>：{@link #assistantNames} 的翻译源必须是拼成逗号串的只读 getter
 * {@link #getAssistantIdText()}，否则 {@code @Translation} 会静默失效。</p>
 *
 * <p><b>标签来源</b>：最高学历的标签<b>统一由字典 {@code talent_education} 翻译产出</b>
 * （{@link #highestEducationText}，与 {@code hr_talent_education.education}、人才检索共用同一套编码，
 * 唯一编码为 {@code high_school/college/bachelor/master/doctor/other}，大专为 {@code college}），
 * 服务层不再维护第二套中文兜底映射。
 * 性别在设计文档 §10 仍无字典组，暂保留只读 getter {@link #getGenderText()} 作为中文兜底。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentProfile.class, uses = AssistantIdsConverter.class)
public class TalentProfileVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才主档ID
     */
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
     * 性别（字典编码，如 male/female/unknown）
     */
    private String gender;

    /**
     * 最高学历（字典 {@code talent_education} 编码：high_school/college/bachelor/master/doctor/other）
     */
    private String highestEducation;

    /**
     * 最高学历标签（字典 {@code talent_education}；唯一标签来源，勿再维护服务层兜底映射）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "highestEducation", other = "talent_education")
    private String highestEducationText;

    /**
     * 脱敏电话（列表默认口径，明文仅 phone-view 接口返回）
     */
    private String phoneMasked;

    /**
     * 脱敏邮箱（列表默认口径）
     */
    private String emailMasked;

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
     * 期望岗位
     */
    private String expectedPosition;

    /**
     * 工作年限
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
     * 负责人昵称（由 {@link #ownerId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "ownerId")
    private String ownerName;

    /**
     * 归属部门ID
     */
    private Long ownerDeptId;

    /**
     * 归属部门名称快照
     */
    private String ownerDeptName;

    /**
     * 协助人用户ID数组（由 {@code assistant_ids} 逗号串拆回）
     */
    private Long[] assistantIds;

    /**
     * 协助人昵称，多个以英文逗号分隔（由 {@link #getAssistantIdText()} 批量翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "assistantIdText")
    private String assistantNames;

    /**
     * 人才生命周期状态（字典 talent_status 编码）
     */
    private String talentStatus;

    /**
     * 人才状态标签（优先取字典 talent_status，未配置时由 {@link #getTalentStatusText()} 兜底）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "talentStatus", other = "talent_status")
    private String talentStatusLabel;

    /**
     * 状态原因（限制/禁止联系时必填）
     */
    private String statusReason;

    /**
     * 状态到期日
     */
    private LocalDate statusExpireDate;

    /**
     * 可见范围（字典 talent_visibility_type 编码）
     */
    private String visibilityType;

    /**
     * 可见范围标签（字典 talent_visibility_type）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "visibilityType", other = "talent_visibility_type")
    private String visibilityTypeLabel;

    /**
     * 数据分级（字典 recruit_data_level 编码）
     */
    private String dataLevel;

    /**
     * 数据分级标签（字典 recruit_data_level）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "dataLevel", other = "recruit_data_level")
    private String dataLevelLabel;

    /**
     * 主档来源（manual/resume_import/application 等稳定编码）
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
     * 乐观锁版本号（编辑提交时必须回传）
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 资料完整度（0~100，由服务层按设计文档 §8.12 主档基础字段实时计算）。
     *
     * <p><b>口径</b>：主档字段填充率 = 已填基础字段数 / 基础字段总数 × 100，
     * 基础字段取 §8.12「人才主档」的可用列（姓名、性别、出生日期或年龄快照、手机号、邮箱、
     * 最高学历、当前城市、意向城市、当前公司、当前职位、工作年限、期望岗位、期望薪资、来源、负责人）。
     * <b>不含</b>简历 / 教育 / 工作 / 标签等派生维度，避免逐行多表统计造成 N+1 查询；
     * 因此该值是可解释的「主档完整度」而非全量画像完整度。</p>
     */
    private Integer completeness;

    /**
     * 资料完整度分档（high/medium/low 稳定编码）
     */
    private String completenessLevel;

    /**
     * 资料完整度分档中文兜底（§10 未定义分档字典，避免依赖不存在的字典组）。
     *
     * @return 中文；完整度为空时返回 null
     */
    public String getCompletenessLevelText() {
        return switch (completenessLevel == null ? "" : completenessLevel) {
            case "high" -> "高";
            case "medium" -> "中";
            case "low" -> "低";
            default -> null;
        };
    }

    /**
     * 协助人用户ID入库原串（英文逗号分隔）。
     *
     * <p>不是数据库直出字段，而是由 {@link #assistantIds} 组合得到的<b>只读</b>属性：
     * 作为 {@link #assistantNames} 翻译的取数来源，同时用 {@code @JsonIgnore}
     * 避免把原始 ID 串重复输出给前端。</p>
     *
     * @return 逗号分隔的用户ID串，无协助人时返回 null
     */
    @JsonIgnore
    public String getAssistantIdText() {
        if (assistantIds == null || assistantIds.length == 0) {
            return null;
        }
        return Arrays.stream(assistantIds)
            .filter(Objects::nonNull)
            .distinct()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    }

    /**
     * 性别中文兜底（设计文档 §10 未定义性别字典，编码沿用 talent 域稳定编码）。
     *
     * @return 中文，未知/空编码返回 null
     */
    public String getGenderText() {
        return switch (gender == null ? "" : gender) {
            case "male" -> "男";
            case "female" -> "女";
            case "unknown" -> "未知";
            default -> null;
        };
    }

    /**
     * 从电话明文生成脱敏串（服务层在实体转换后调用，避免把明文放进 VO）。
     *
     * @param rawPhone 电话明文，可为空
     */
    public void maskPhone(String rawPhone) {
        this.phoneMasked = TalentContactCodec.maskPhone(rawPhone);
    }

    /**
     * 从邮箱明文生成脱敏串（服务层在实体转换后调用）。
     *
     * @param rawEmail 邮箱明文，可为空
     */
    public void maskEmail(String rawEmail) {
        this.emailMasked = TalentContactCodec.maskEmail(rawEmail);
    }

}
