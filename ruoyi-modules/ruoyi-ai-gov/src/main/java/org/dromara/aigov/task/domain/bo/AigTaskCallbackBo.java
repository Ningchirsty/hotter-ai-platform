package org.dromara.aigov.task.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Provider 回调入参。
 *
 * <p><b>{@link #rawPayload} 必须是「收到的原始字节」的字符串形式</b>，不要先解析再传：
 * 验签是对原始字节算 HMAC 的，任何重新序列化（字段顺序、空格、数字格式）都会让
 * 同一个载荷算出不同签名，于是真回调被误判为伪造——而伪造的反而偶尔能通过。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskCallbackBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Provider 编码
     */
    @NotBlank(message = "Provider 编码不能为空")
    @Size(max = 64, message = "Provider 编码长度不能超过 64")
    private String providerCode;

    /**
     * 外部作业ID（据此定位任务）
     */
    @Size(max = 128, message = "作业ID长度不能超过 128")
    private String providerJobId;

    /**
     * 外部事件ID（与 providerCode 组成幂等键）
     */
    @Size(max = 128, message = "事件ID长度不能超过 128")
    private String eventId;

    /**
     * 回调声明的签名算法（如 HMAC-SHA256）
     */
    @Size(max = 32, message = "签名算法长度不能超过 32")
    private String signAlgorithm;

    /**
     * 回调携带的签名
     */
    @Size(max = 256, message = "签名长度不能超过 256")
    private String signature;

    /**
     * 收到的原始载荷（验签与哈希都基于它）
     */
    @NotBlank(message = "回调载荷不能为空")
    private String rawPayload;

    /**
     * 回调声明的目标状态（必填）。
     *
     * <p><b>为什么由适配层负责映射、而不是本层猜</b>：各家 Provider 的完成语义写法不同
     * （{@code finished}/{@code success}/{@code state=2}…）。把「协议 → 我们的状态」
     * 这一步放在适配层（控制器/插件）是唯一正确的分层：协议知识属于对接方，
     * 若让本层去猜，猜错的方向是「把失败当成功」——那是会把坏结果交付出去的错。
     * 因此这里必填，本层只在「当前状态能否走到它」上做判断。</p>
     */
    @NotBlank(message = "回调目标状态不能为空（协议映射由回调适配层负责）")
    @Size(max = 24, message = "状态长度不能超过 24")
    private String toStatus;

    /**
     * 回调声明的错误码（失败回调时填）。
     *
     * <p><b>两种形态都接受</b>：本层 {@code AigErrorClassEnum} 的编码（{@code TIMEOUT}…），
     * 或 Provider 自有的码（{@code invalid api key}、{@code gateway_timeout}…）。
     * 服务层用 {@code AigErrorClassEnum.classify} 归类：先认结构化码，认不出再按文本兜底，
     * <b>都认不出就记 UNKNOWN（不猜）</b>。</p>
     *
     * <p><b>它决定的是「失败之后去哪」</b>：分类决定重试还是转人工（见
     * {@code AigTaskStateMachine#restingAfterFailure}）。此前这一路把声明码整条丢掉、
     * 一律记成 UNKNOWN，于是「Provider 说超时」的任务显示成「不知道为什么会失败」，
     * 处置也随之偏了。</p>
     */
    @Size(max = 64, message = "错误码长度不能超过 64")
    private String errorCode;

    /**
     * 可读说明
     */
    @Size(max = 1000, message = "说明长度不能超过 1000")
    private String detail;

    /**
     * 执行面回报的进度（0-100，可选）
     *
     * <p>异步执行面在 {@code RUNNING} 阶段回报进度用。平台的处理口径：
     * 只在任务处于 {@code RUNNING}/{@code DISPATCHED} 时接受、同一个百分比不重复记账
     * （见 {@code IAigTaskService#recordProgress}）；<b>写不进去也不让整条回调失败</b>——
     * 进度是信息性的，为它把一次合法的状态回调整体判失败，只会让 Provider 反复重推同一件事。
     * 未记录的原因随响应回给对方，并写日志。</p>
     */
    @Min(value = 0, message = "进度不能小于 0")
    @Max(value = 100, message = "进度不能大于 100")
    private Integer progress;

}
