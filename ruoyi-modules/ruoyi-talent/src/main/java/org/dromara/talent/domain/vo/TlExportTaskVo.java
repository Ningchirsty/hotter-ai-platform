package org.dromara.talent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.talent.domain.TlExportTask;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才台账导出任务视图对象 tl_export_task
 * <p>不返回 object_key / bucket / 预签名 URL，下载只能走受控接口。</p>
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlExportTask.class)
public class TlExportTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导出任务ID
     */
    private Long exportId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 状态（PENDING/RUNNING/SUCCESS/FAILED/EXPIRED）
     */
    private String status;

    /**
     * 状态说明（服务端按 ExportStatusEnum 生成）
     */
    private String statusLabel;

    /**
     * 下载文件名
     */
    private String fileName;

    /**
     * 导出行数
     */
    private Integer rowCount;

    /**
     * 错误摘要
     */
    private String errorSummary;

    /**
     * 过期时间（默认创建后24小时）
     */
    private LocalDateTime expireTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishTime;

    /**
     * 创建人
     */
    private Long createBy;

    /**
     * 创建人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否已过期（服务端按 expire_time 与当前时间判定）
     */
    private Boolean expired;

}
