package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批单超时扫描结果（供定时任务与手动触发接口回报）。
 *
 * <p>与任务域 {@code AigTaskSweepVo} 同口径：**把动作数报出来**，
 * 否则「扫了一轮但什么都没做」与「压根没扫」在日志里长得一样。</p>
 *
 * @author ai-gov
 */
@Data
public class AigCallApprovalSweepVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 本次被置为「已超时」的申请单数
     */
    private int expired;

    /**
     * 本轮扫描时刻
     */
    private LocalDateTime scannedAt;

}
