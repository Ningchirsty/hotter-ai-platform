package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentWork;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作经历视图对象 hr_talent_work（SPEC-P4 §2.2 B 线）。
 *
 * <p><b>翻译说明</b>：创建人昵称由 {@code @Translation(USER_ID_TO_NICKNAME)} 回填；
 * 「是否当前在职」是 {@code char(1)} 的 0/1 标志，设计文档 §10 未定义对应字典组，
 * 由只读兜底 getter {@link #getCurrentFlagText()} 输出中文（不引用不存在的字典）。</p>
 *
 * <p><b>本 VO 无多值字段</b>：不存在「翻译 mapper 源需拼逗号串 getter」的坑。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentWork.class)
public class TalentWorkVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工作经历ID
     */
    private Long workId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 部门名称
     */
    private String departmentName;

    /**
     * 职位名称
     */
    private String positionName;

    /**
     * 所属行业
     */
    private String industry;

    /**
     * 入职日期
     */
    private LocalDate startDate;

    /**
     * 离职日期（在职为空，见 {@code TalentWork} 类注释口径）
     */
    private LocalDate endDate;

    /**
     * 离职原因（设计文档 §8.14）
     */
    private String leaveReason;

    /**
     * 是否当前在职（0否 1是）
     */
    private String currentFlag;

    /**
     * 工作职责
     */
    private String responsibility;

    /**
     * 工作业绩
     */
    private String achievement;

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
     * 备注
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
     * 是否当前在职中文兜底（DDL 为 char(1) 的 0/1 标志，非字典编码）。
     *
     * @return 中文，未知/空编码返回 null
     */
    public String getCurrentFlagText() {
        if ("1".equals(currentFlag)) {
            return "在职";
        }
        if ("0".equals(currentFlag)) {
            return "已离职";
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
