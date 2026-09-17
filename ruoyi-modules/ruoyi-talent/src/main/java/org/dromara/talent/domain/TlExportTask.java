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
 * 人才台账导出任务对象 tl_export_task
 *
 * @author talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tl_export_task")
public class TlExportTask extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导出任务ID
     */
    @TableId(value = "export_id")
    private Long exportId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 查询条件快照（JSON，不含明文手机号）
     */
    private String querySnapshot;

    /**
     * 状态（PENDING/RUNNING/SUCCESS/FAILED/EXPIRED）
     */
    private String status;

    /**
     * 存储桶
     */
    private String bucket;

    /**
     * 导出文件对象键 talent-private/exports/{exportId}/talent-ledger.xlsx
     */
    private String objectKey;

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
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
