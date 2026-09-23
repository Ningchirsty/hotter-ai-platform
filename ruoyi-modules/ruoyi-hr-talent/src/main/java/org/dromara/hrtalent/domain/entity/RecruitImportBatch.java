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
 * 招聘数据导入批次对象 hr_recruit_import_batch（设计 §9.2 / §19.4）。
 *
 * <p><b>为什么要批次表</b>：导入不是一次性的「传上去就完事」。先预检、看清哪些行有问题，
 * 再由人确认才落库；批次表就是这两段之间以及事后追责的凭据——
 * {@code hr_recruit_plan_item.import_batch_id} 反向指回这里，能回答
 * 「这条计划任务是哪次导入、哪个文件、谁操作的」。</p>
 *
 * <p>源文件本体存对象存储（{@code source_file_oss_id}），<b>不存长期公网 URL</b>；
 * 确认导入时按该对象键取回重放，避免把解析结果放在进程内存里等着人来点确认。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_import_batch")
public class RecruitImportBatch extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批次ID（主键）
     */
    @TableId(value = "batch_id")
    private Long batchId;

    /**
     * 批次编号（业务编号，唯一）
     */
    private String batchNo;

    /**
     * 导入类型（见 RecruitImportTypeEnum）
     */
    private String importType;

    /**
     * 源文件 OSS 对象键
     */
    private String sourceFileOssId;

    /**
     * 源文件名称
     */
    private String sourceFileName;

    /**
     * 总记录数
     */
    private Integer totalCount;

    /**
     * 成功数
     */
    private Integer successCount;

    /**
     * 失败数
     */
    private Integer failureCount;

    /**
     * 批次状态（见 RecruitImportBatchStatusEnum）
     */
    private String status;

    /**
     * 开始时间
     */
    private LocalDateTime startedTime;

    /**
     * 结束时间
     */
    private LocalDateTime finishedTime;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
