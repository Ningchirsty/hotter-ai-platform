package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentProject;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目经历视图对象 hr_talent_project（SPEC-P4 §2.2 B 线）。
 *
 * <p><b>翻译说明</b>：创建人昵称由 {@code @Translation(USER_ID_TO_NICKNAME)} 回填；
 * 来源类型无字典组，由只读兜底 getter {@link #getSourceTypeText()} 输出中文。</p>
 *
 * <p><b>本 VO 无多值字段</b>：不存在「翻译 mapper 源需拼逗号串 getter」的坑。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentProject.class)
public class TalentProjectVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目经历ID
     */
    private Long projectId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目角色
     */
    private String projectRole;

    /**
     * 项目开始日期
     */
    private LocalDate startDate;

    /**
     * 项目结束日期
     */
    private LocalDate endDate;

    /**
     * 项目描述（设计文档 §8.14 的「项目说明」）
     */
    private String description;

    /**
     * 项目职责
     */
    private String responsibility;

    /**
     * 项目业绩（设计文档 §8.14 的「成果」）
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
     * 关联工作经历ID
     */
    private Long workId;

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
