package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 人才项目经历对象 hr_talent_project（设计文档 §8.14、SPEC-P4 §2.2 B 线）。
 *
 * <p><b>归属关系</b>：通过 {@link #talentId} 挂在人才主档下；{@link #workId} 可选地关联
 * 同一人才的一条工作经历（服务层校验「存在且同属该人才」，避免跨人才挂接造成越权读取）。</p>
 *
 * <p><b>来源与解析边界</b>：{@link #sourceType} 表达来源（manual/import/resume/parse），
 * 解析产物在人工确认前保存在候选结果表中，绝不直接覆盖本表的正式经历（设计文档 §8.14）。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_project")
public class TalentProject extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目经历ID（主键）
     */
    @TableId(value = "project_id")
    private Long projectId;

    /**
     * 人才主档ID（归属主档，不可为空）
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
     * 来源类型（manual/import/resume/parse 等稳定编码）
     */
    private String sourceType;

    /**
     * 来源简历版本ID（人工确认为准）
     */
    private Long resumeId;

    /**
     * 关联工作经历ID（可空，必须属于同一人才）
     */
    private Long workId;

    /**
     * 排序号（倒序展示用）
     */
    private Integer sortNo;

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
