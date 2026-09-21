package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentEducation;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 教育经历视图对象 hr_talent_education（SPEC-P4 §2.2 B 线）。
 *
 * <p><b>翻译说明</b>：创建人昵称由 {@code @Translation(USER_ID_TO_NICKNAME)} 回填。
 * 学历 {@link #education} 与学位 {@link #degree} 在 DDL 注释中标注为「字典编码」，
 * 但设计文档 §10 与 {@code hr_talent_menu.sql} <b>均未定义</b>对应字典组，
 * 因此不写 {@code @Translation(DICT_TYPE_TO_LABEL, other = "…")} 去引用一个不存在的字典
 * （那会让标签静默为空），改用只读兜底 getter {@link #getEducationText()} / {@link #getDegreeText()}
 * 输出中文；后续补齐字典组时再把 {@code @Translation} 指向字典类型，前端契约不变
 * （学历标签统一由字典 {@code talent_education} 翻译产出，与 P3 {@code TalentProfileVo.highestEducationText} 同一口径）。</p>
 *
 * <p><b>本 VO 无多值字段</b>：三张经历表没有逗号拼接的多值列，
 * 因此不存在「翻译 mapper 源需拼串 getter」的坑。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentEducation.class)
public class TalentEducationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 教育经历ID
     */
    private Long educationId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 学校名称
     */
    private String schoolName;

    /**
     * 专业
     */
    private String major;

    /**
     * 学历（字典编码）
     */
    private String education;

    /**
     * 学位（字典编码）
     */
    private String degree;

    /**
     * 入学日期
     */
    private LocalDate startDate;

    /**
     * 毕业日期
     */
    private LocalDate endDate;

    /**
     * 是否全日制（0否 1是）
     */
    private String fullTimeFlag;

    /**
     * 来源类型（manual/import/resume/parse）
     */
    private String sourceType;

    /**
     * 来源简历版本ID
     */
    private Long resumeId;

    /**
     * 排序号（倒序展示用）
     */
    private Integer sortNo;

    /**
     * 备注（设计文档 §8.14 的「说明」）
     */
    private String remark;

    /**
     * 创建者用户ID
     */
    private Long createBy;

    /**
     * 创建者昵称（由 {@link #createBy} 翻译回填）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 学历中文兜底（学历字典 {@code talent_education} 已由 P4 补齐，编码为
     * {@code high_school/college/bachelor/master/doctor/other}；本兜底仅作为无翻译切面场景的降级展示，
     * 唯一编码为 {@code college}）。
     *
     * @return 中文，未知/空编码返回 null
     */
    public String getEducationText() {
        return switch (education == null ? "" : education) {
            case "high_school" -> "高中及以下";
            case "college" -> "大专";
            case "bachelor" -> "本科";
            case "master" -> "硕士";
            case "doctor" -> "博士";
            case "other" -> "其他";
            default -> null;
        };
    }

    /**
     * 学位中文兜底（设计文档 §10 未定义学位字典）。
     *
     * @return 中文，未知/空编码返回 null
     */
    public String getDegreeText() {
        return switch (degree == null ? "" : degree) {
            case "none" -> "无";
            case "bachelor" -> "学士";
            case "master" -> "硕士";
            case "doctor" -> "博士";
            default -> null;
        };
    }

    /**
     * 是否全日制中文兜底（DDL 为 char(1) 的 0/1 标志，非字典编码）。
     *
     * @return 中文，未知/空编码返回 null
     */
    public String getFullTimeFlagText() {
        if ("1".equals(fullTimeFlag)) {
            return "全日制";
        }
        if ("0".equals(fullTimeFlag)) {
            return "非全日制";
        }
        return null;
    }

    /**
     * 来源类型中文兜底（设计文档 §10 未定义来源类型字典）。
     *
     * @return 中文，未知/空编码返回 null
     */
    public String getSourceTypeText() {
        return switch (sourceType == null ? "" : sourceType) {
            case "manual" -> "人工录入";
            case "import" -> "导入";
            case "resume" -> "简历解析";
            case "parse" -> "解析任务";
            default -> null;
        };
    }

}
