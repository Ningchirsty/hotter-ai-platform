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
 * 简历解析任务对象 hr_talent_parse_task（设计文档 §8.21、§11.1、§21.6）。
 *
 * <p><b>异步边界</b>：解析接口只创建本任务，<b>不在 HTTP 请求内同步执行 OCR 或大模型调用</b>（§11.1）。
 * 一期只实现「任务 + 人工复核」，解析引擎未获批准接入前任务停在 {@code pending}
 * 或置 {@code failed} 并给出明确中文提示；<b>严禁把简历或个人数据发送给未经批准的第三方服务</b>（§8.21）。</p>
 *
 * <p><b>脱敏约束</b>：{@link #errorMessage} 只允许写技术性说明，<b>禁止写入简历正文、联系方式或对象存储地址</b>；
 * {@link #errorCode} 只存稳定编码，不存中文。</p>
 *
 * <p>字段与 {@code script/sql/hr_talent.sql} 的建表语句逐列一致；主键列为 {@code task_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_parse_task")
public class TalentParseTask extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 解析任务ID（主键）
     */
    @TableId(value = "task_id")
    private Long taskId;

    /**
     * 简历版本ID
     */
    private Long resumeId;

    /**
     * 人才主档ID（简历已归属人才时冗余）
     */
    private Long talentId;

    /**
     * 任务状态（pending/running/success/failed/cancelled 等稳定编码）
     */
    private String taskStatus;

    /**
     * 解析器类型（internal内置/ocr/third_party 等稳定编码）
     */
    private String parserType;

    /**
     * 解析器版本
     */
    private String parserVersion;

    /**
     * 重试次数（非负整数，只重试技术异常，不重复处理业务拒绝）
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
     * 错误说明（禁止写入简历正文与联系方式）
     */
    private String errorMessage;

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
