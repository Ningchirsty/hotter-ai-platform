package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentParseTask;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 简历解析任务视图对象 hr_talent_parse_task（SPEC-P4 §2.1 / 设计文档 §8.21）。
 *
 * <p>任务详情接口一并返回候选结果 {@link #results}，供人工逐字段复核；
 * 一期解析引擎未接入时任务停在 {@code pending} 或置 {@code failed}，
 * {@link #errorMessage} 给出明确中文提示（不写入简历正文）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentParseTask.class)
public class TalentParseTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 解析任务ID
     */
    private Long taskId;

    /**
     * 简历版本ID
     */
    private Long resumeId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 任务状态（pending/running/success/failed/cancelled 等稳定编码）
     */
    private String taskStatus;

    /**
     * 解析器类型
     */
    private String parserType;

    /**
     * 解析器版本
     */
    private String parserVersion;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 开始时间
     */
    private LocalDateTime startedTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishedTime;

    /**
     * 错误编码（稳定编码，不存中文）
     */
    private String errorCode;

    /**
     * 错误说明（禁止写入简历正文）
     */
    private String errorMessage;

    /**
     * 创建者用户ID
     */
    private Long createBy;

    /**
     * 创建人昵称（由 {@link #createBy} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 候选解析结果（逐字段原始值/标准化值/置信度/来源位置/复核结论）
     */
    private List<TalentParseResultVo> results;

}
