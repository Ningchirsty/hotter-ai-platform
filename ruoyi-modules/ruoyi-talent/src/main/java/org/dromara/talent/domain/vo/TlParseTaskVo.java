package org.dromara.talent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.talent.domain.TlParseTask;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 简历解析任务视图对象 tl_parse_task
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlParseTask.class)
public class TlParseTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 解析任务ID
     */
    private Long taskId;

    /**
     * 人才ID
     */
    private Long talentId;

    /**
     * 附件ID
     */
    private Long attachmentId;

    /**
     * 附件原始文件名
     */
    private String attachmentName;

    /**
     * 状态（DISABLED未启用/PENDING/PROCESSING/SUCCESS/FAILED）
     */
    private String status;

    /**
     * 状态说明（服务端按 ParseTaskStatusEnum 生成）
     */
    private String statusLabel;

    /**
     * 解析服务版本
     */
    private String parserVersion;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 错误摘要（禁止写入简历正文）
     */
    private String errorSummary;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime finishTime;

    /**
     * 解析字段复核列表
     */
    private List<TlParseFieldVo> fields;

}
