package org.dromara.aigov.tool.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具调用账本 aig_tool_call（设计 §7.5、§7.6）。
 *
 * <p>幂等键唯一：写工具重试必须命中同一条 call，而不是重复变更业务数据。
 * {@link #approvalDigest} 记录批准时绑定的输入摘要——参数一变摘要就变，原批准自动失效。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_tool_call")
public class AigToolCall extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 调用ID */
    @TableId(value = "call_id")
    private Long callId;

    /** 工具编码 */
    private String toolCode;

    /** 工具版本 */
    private String toolVersion;

    /** 归属任务 */
    private Long taskId;

    /** 幂等键 */
    private String idempotencyKey;

    /** 调用状态 */
    private String status;

    /** 输入摘要 */
    private String inputDigest;

    /** 输入引用 */
    private String inputRef;

    /** 批准引用 */
    private String approvalRef;

    /** 批准绑定的输入摘要 */
    private String approvalDigest;

    /** 输出引用 */
    private String outputRef;

    /** 操作凭证引用 */
    private String receiptRef;

    /** 失败分类 */
    private String errorCode;

    /** 失败原因 */
    private String errorMessage;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;

    /** 备注 */
    private String remark;

}
