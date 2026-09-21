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
 * 人才工作经历对象 hr_talent_work（设计文档 §8.14、SPEC-P4 §2.2 B 线）。
 *
 * <p><b>归属关系</b>：通过 {@link #talentId} 挂在人才主档下，访问/写入前必须经人才可见范围校验。</p>
 *
 * <p><b>「是否当前任职」口径</b>（SPEC-P4 §2.2 要求写明）：{@link #currentFlag} 为 {@code '1'}（在职）时，
 * {@link #endDate} <b>必须为空</b>；服务层在写入前统一把 {@code current_flag='1'} 的入参
 * {@code end_date} 归一化为 {@code null}（宁可丢弃一个与在职状态矛盾的日期，也不留下
 * 「在职却有离职日期」的脏数据）。反向不成立：{@link #currentFlag} 为 {@code '0'} 时
 * {@link #endDate} <b>允许为空</b>，语义是「已离职但离职日期不详」。</p>
 *
 * <p><b>来源与解析边界</b>：{@link #sourceType} 表达来源（manual/import/resume/parse），
 * 解析产物在人工确认前保存在候选结果表中，绝不直接覆盖本表的正式经历（设计文档 §8.14）。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_work")
public class TalentWork extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工作经历ID（主键）
     */
    @TableId(value = "work_id")
    private Long workId;

    /**
     * 人才主档ID（归属主档，不可为空）
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
     * 离职日期（在职为空，见类注释口径）
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
     * 来源类型（manual/import/resume/parse 等稳定编码）
     */
    private String sourceType;

    /**
     * 来源简历版本ID（人工确认为准）
     */
    private Long resumeId;

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
