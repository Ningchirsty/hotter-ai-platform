package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 简历候选解析结果对象 hr_talent_parse_result（设计文档 §8.21）。
 *
 * <p><b>逐字段留痕</b>：每条候选结果保存「原始值 / 标准化值 / 置信度 / 来源位置 / 复核结论」；
 * 低置信度字段默认不勾选，<b>正式字段只有在人工确认后才更新</b>人才主档或经历，
 * 任何解析结果都<b>不得自动覆盖</b>正式数据（§8.14、§8.21）。</p>
 *
 * <p><b>脱敏约束</b>：{@link #rawValue} 来自简历原文，只允许在受控的复核界面展示，
 * <b>禁止写入日志</b>；{@link #sourceLocation} 只保存页码或文本偏移，不保存正文。</p>
 *
 * <p>字段与 {@code script/sql/hr_talent.sql} 的建表语句逐列一致；主键列为 {@code result_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_parse_result")
public class TalentParseResult extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 解析结果ID（主键）
     */
    @TableId(value = "result_id")
    private Long resultId;

    /**
     * 解析任务ID
     */
    private Long taskId;

    /**
     * 字段路径（如 education[0].school_name）
     */
    private String fieldPath;

    /**
     * 简历原始值（禁止写入日志）
     */
    private String rawValue;

    /**
     * 标准化值（人工确认后可写入正式字段）
     */
    private String normalizedValue;

    /**
     * 置信度（0~1，低置信度默认不勾选）
     */
    private BigDecimal confidence;

    /**
     * 来源位置（页码或文本偏移）
     */
    private String sourceLocation;

    /**
     * 复核状态（pending待复核/reviewing复核中/confirmed已确认/rejected已否决，
     * 字典 talent_resume_review_status）
     */
    private String reviewStatus;

    /**
     * 复核人用户ID
     */
    private Long reviewedBy;

    /**
     * 复核时间
     */
    private LocalDateTime reviewedTime;

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
