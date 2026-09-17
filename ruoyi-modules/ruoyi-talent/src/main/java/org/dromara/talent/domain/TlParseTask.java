package org.dromara.talent.domain;

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
 * 简历解析任务对象 tl_parse_task
 *
 * @author talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tl_parse_task")
public class TlParseTask extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 解析任务ID
     */
    @TableId(value = "task_id")
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
     * 状态（DISABLED未启用/PENDING/PROCESSING/SUCCESS/FAILED）
     */
    private String status;

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
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
