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
 * <p><b>字典缺口说明</b>：性别与学历在设计文档 §10 未定义字典组，
 * 因此提供只读 getter {@link #getGenderText()} / {@link #getHighestEducationText()} 作为
 * 中文兜底，保证页面始终有中文可展示；若后续补齐字典，可把 {@code @Translation} 指向对应字典类型，
 * 无需改动前端契约。</p>
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
     * 最高学历（字典编码）
     */
    private String highestEducation;

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
     * 学历中文兜底（设计文档 §10 未定义学历字典，编码参考常见人力口径）。
     *
     * @return 中文，未知/空编码返回 null
     */
    public String getHighestEducationText() {
        return switch (highestEducation == null ? "" : highestEducation) {
            case "high_school" -> "高中及以下";
            case "junior_college" -> "大专";
            case "bachelor" -> "本科";
            case "master" -> "硕士";
            case "doctor" -> "博士";
            case "other" -> "其他";
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
