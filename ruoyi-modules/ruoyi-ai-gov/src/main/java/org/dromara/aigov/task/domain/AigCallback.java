package org.dromara.aigov.task.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 回调账本 aig_callback（验签 + 幂等去重；追加型）。
 *
 * <p><b>为什么「失败的回调」也要留痕</b>：验签失败、任务找不到、顺序过期——
 * 这些都不该推进状态，但恰恰是最需要证据的场景：是对方在乱推？是密钥配错了？
 * 还是有人伪造？只记成功回调的话，这些疑问永远无从回答。
 * 因此本表对<b>每一次</b>收到的回调都写一行，并在
 * {@code signature_verified}/{@link #processResult} 里写明结论。</p>
 *
 * <p><b>幂等键是 (provider_code, event_id)</b>：Provider 的重推、
 * 网络重试导致的重复投递是常态。唯一键让第二次插入直接失败，
 * 服务层据此返回 DUPLICATE 而不是再推进一次状态——否则一次成功回调被投递两次，
 * 就会出现两条「成功」事件，若下游按事件计费或计数就是重复统计。</p>
 *
 * @author ai-gov
 */
@Data
@TableName("aig_callback")
public class AigCallback implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 回调记录ID
     */
    @TableId(value = "callback_id")
    private Long callbackId;

    /**
     * 关联任务ID（按 provider_job_id 定位；<b>定位不到也留痕</b>）
     */
    private Long taskId;

    /**
     * Provider 编码
     */
    private String providerCode;

    /**
     * 外部作业ID
     */
    private String providerJobId;

    /**
     * 外部事件ID（与 provider_code 组成幂等键）
     */
    private String eventId;

    /**
     * 验签是否通过（Y/N）；<b>未通过一律不推进状态</b>
     */
    private String signatureVerified;

    /**
     * 签名算法（如 HMAC-SHA256）
     */
    private String signAlgorithm;

    /**
     * 载荷 SHA-256
     */
    private String payloadHash;

    /**
     * 是否命中重复（Y=重复回调，已幂等忽略）
     */
    private String idempotentHit;

    /**
     * 处理结果（ACCEPTED/DUPLICATE/REJECTED_UNSIGNED/TASK_NOT_FOUND/ORDER_STALE）
     */
    private String processResult;

    /**
     * 处理说明
     */
    private String detail;

    /**
     * 接收时间
     */
    private LocalDateTime receivedAt;

}
