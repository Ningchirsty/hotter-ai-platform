package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才导出任务对象 hr_talent_export_task（设计文档 §8.20、§11.1、§21.10）。
 *
 * <p><b>记录内容</b>：筛选条件快照（{@link #scopeJson}）、字段清单快照（{@link #fieldsJson}）、
 * 导出人（{@link #exportedBy}）、用途（{@link #purpose}）、记录数（{@link #recordCount}）、
 * 结果文件对象标识（{@link #ossId}）与过期时间（{@link #expireTime}）。</p>
 *
 * <p><b>安全约束</b>：</p>
 * <ul>
 *     <li>{@link #ossId} 只保存<b>私有对象存储的对象标识（对象键）</b>，
 *     不保存任何长期公网地址或预签名地址（§8.13、§11.1）；</li>
 *     <li>{@link #scopeJson} / {@link #fieldsJson} 只写结构化条件与字段名，
 *     <b>禁止</b>写入电话明文、邮箱明文等敏感明文；</li>
 *     <li>{@link #failureReason} 只写技术性中文说明，禁止写入简历正文、联系方式与对象存储地址。</li>
 * </ul>
 *
 * <p>字段与 {@code script/sql/hr_talent.sql} 的建表语句逐列一致；主键列为 {@code task_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_export_task")
public class TalentExportTask extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导出任务ID（主键）
     */
    @TableId(value = "task_id")
    private Long taskId;

    /**
     * 任务编号（业务编号，唯一）
     */
    private String taskNo;

    /**
     * 导出类型（normal普通台账/sensitive敏感台账）
     */
    private String exportType;

    /**
     * 筛选条件快照（结构化 JSON，禁止写入敏感明文）
     */
    private String scopeJson;

    /**
     * 导出字段清单快照（结构化 JSON）
     */
    private String fieldsJson;

    /**
     * 导出人用户ID
     */
    private Long exportedBy;

    /**
     * 导出用途/原因（敏感台账必填，审计追溯用）
     */
    private String purpose;

    /**
     * 导出记录数（非负整数）
     */
    private Integer recordCount;

    /**
     * 结果文件OSS对象标识（不存长期公网地址）
     */
    private String ossId;

    /**
     * 结果文件名
     */
    private String fileName;

    /**
     * 任务状态（pending/running/success/failed/expired等稳定编码）
     */
    private String status;

    /**
     * 失败原因（只写技术性说明，禁止写入敏感明文）
     */
    private String failureReason;

    /**
     * 结果文件过期时间（到期不可下载，由后续定时任务清理对象）
     */
    private LocalDateTime expireTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishedTime;

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
