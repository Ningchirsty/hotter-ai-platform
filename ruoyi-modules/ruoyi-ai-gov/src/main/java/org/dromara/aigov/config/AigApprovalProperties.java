package org.dromara.aigov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 调用授权审批配置（{@code aigov.approval.*}，C3）。
 *
 * <p><b>为什么没有「是否启用审批」这个总开关</b>：要不要审批是<b>逐策略</b>配的
 * （{@code aig_route_policy.require_approval}，默认 {@code 'N'}），那本身就是开关。
 * 再叠一个全局开关只会造出「策略说需要、全局说不用」这种自相矛盾的配置，
 * 而两者冲突时无论听谁的都要额外解释一遍（与 C3 灰度阈值同一取舍：证据/开关要单一来源）。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.approval")
public class AigApprovalProperties {

    /**
     * 批准后授权的有效时长（分钟，默认 30）。
     *
     * <p><b>批准的不是一次请求，也不是当日无限</b>，而是「这个人 × 这个能力 × 这个数据等级」
     * 在这段时间内的后续调用——这是刻意的折中：一次一批等于把审批当点击确认，
     * 当日常驻等于批完就不再有约束。30 分钟够跑完一轮工作，也让授权自然回到需要复核的状态。</p>
     */
    private long grantTtlMinutes = 30L;

    /**
     * 申请单的审批时限（分钟，默认 1440＝24 小时）。
     *
     * <p>超过这个时间仍是 PENDING 的单子会被置为「已超时」且<b>不得再批准</b>：
     * 一张三天前申请、当时合理、今天才被翻出来批准的单子，批准人已经无法判断当前情况，
     * 那时"批准"批的是历史而不是当下。要重来就重新提交。</p>
     */
    private long requestTtlMinutes = 1440L;

    /**
     * 是否启用内置超时扫描（需要容器启用了 {@code @EnableScheduling}）。
     *
     * <p>默认关闭，与 {@code aigov.task.scheduler.enabled} 同一取舍。但<b>正确性不依赖它</b>：
     * 授权判定看的是 {@code valid_until > now}（时间的函数），而批准动作自己会拒绝过期的单子。
     * 扫描只是把「已超时」落进状态列，让列表上一眼看得出积压。</p>
     */
    private boolean expireScanEnabled = false;

    /**
     * 超时扫描间隔（毫秒）
     */
    private long expireScanIntervalMs = 300000L;

}
