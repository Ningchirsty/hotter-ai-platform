package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentExportTask;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才导出任务视图对象 hr_talent_export_task（SPEC-P4 §2.6 F 线，{@code GET /talent/exports}）。
 *
 * <p><b>安全约束（硬约束）</b>：本 VO <b>不包含</b> {@code oss_id}（对象存储对象标识），
 * 只提供系统内<b>受控下载地址</b> {@link #downloadApi}（形如 {@code /talent/exports/{id}/download}），
 * 由受控下载接口在鉴权、用途校验与过期校验通过后流式输出；
 * <b>任何情况下都不返回对象存储永久地址或预签名地址</b>（设计文档 §8.13、§11.1）。</p>
 *
 * <p><b>字典说明</b>：设计文档 §10 未定义「导出类型 / 导出状态」字典组，
 * 因此这两个编码列不挂 {@code @Translation}，改为提供中文兜底只读 getter
 * （{@link #getExportTypeText()} / {@link #getStatusText()}），保证页面始终有中文可展示；
 * 后续若补齐字典，可直接把翻译指向对应字典类型，不改变前端契约。
 * 用户昵称仍统一走 {@code @Translation}（{@link #exportedByName}）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentExportTask.class)
public class TalentExportTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导出任务ID
     */
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
     * 筛选条件快照（结构化 JSON，不含敏感明文）
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
     * 导出人昵称（由 {@link #exportedBy} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "exportedBy")
    private String exportedByName;

    /**
     * 导出用途/原因（敏感台账必填）
     */
    private String purpose;

    /**
     * 导出记录数
     */
    private Integer recordCount;

    /**
     * 结果文件名（只用于展示与下载时设置响应头，不含对象存储信息）
     */
    private String fileName;

    /**
     * 任务状态（pending/running/success/failed/expired）
     */
    private String status;

    /**
     * 失败原因（技术性中文说明）
     */
    private String failureReason;

    /**
     * 结果文件过期时间（到期不可下载）
     */
    private LocalDateTime expireTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishedTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 系统内受控下载地址；仅当任务成功且未过期时由服务层填充，其余情况为 null。
     */
    private String downloadApi;

    /**
     * 导出类型中文兜底。
     *
     * @return 中文；未知编码返回 null
     */
    public String getExportTypeText() {
        return switch (exportType == null ? "" : exportType) {
            case "normal" -> "普通台账";
            case "sensitive" -> "敏感台账";
            default -> null;
        };
    }

    /**
     * 任务状态中文兜底。
     *
     * @return 中文；未知编码返回 null
     */
    public String getStatusText() {
        return switch (status == null ? "" : status) {
            case "pending" -> "待处理";
            case "running" -> "生成中";
            case "success" -> "已完成";
            case "failed" -> "失败";
            case "expired" -> "已过期";
            default -> null;
        };
    }

}
