package org.dromara.aigov.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.vo.AigCallApprovalSweepVo;
import org.dromara.aigov.service.IAigCallApprovalService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审批单超时扫描定时触发器（可选，C3）。
 *
 * <p><b>两条触发路径，部署方按环境选一条</b>（与 {@code AigTaskSchedulerJob} 同一取舍）：</p>
 * <ol>
 *     <li><b>单实例 / 简单部署</b>：置 {@code aigov.approval.expire-scan-enabled=true}，
 *         由本类按 {@code aigov.approval.expire-scan-interval-ms} 周期扫描。
 *         <b>前提是容器启用了 {@code @EnableScheduling}</b>——本仓的启用点在
 *         {@code ruoyi-common-job} 的 {@code SnailJobConfig}，而 {@code ruoyi-ai-gov}
 *         并不依赖它。单独跑本模块时本类<b>不会</b>被触发，也不会有任何报错。</li>
 *     <li><b>集群 / 已有调度平台</b>：保持默认关闭，由 SnailJob 或运维 cron 调
 *         {@code POST /aigov/approval/expire-scan}。多实例同时调也不会重复计数——
 *         只更新仍是 PENDING 的行。</li>
 * </ol>
 *
 * <p><b>正确性不依赖本类</b>：授权判定看 {@code valid_until > now}（时间的函数），
 * 而批准动作会自己拒绝已过期的单子。扫描只是把「已超时」落进状态列，让积压一眼可见。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aigov.approval", name = "expire-scan-enabled", havingValue = "true")
public class AigCallApprovalExpireJob {

    private final IAigCallApprovalService approvalService;

    /**
     * 周期性把超过审批时限的待审批单置为「已超时」。
     *
     * <p>用 {@code fixedDelay} 而不是 {@code fixedRate}：上一轮跑完再计时，
     * 避免表大时把扫描叠加成并发。</p>
     */
    @Scheduled(fixedDelayString = "${aigov.approval.expire-scan-interval-ms:300000}")
    public void scan() {
        try {
            AigCallApprovalSweepVo result = approvalService.expireOverdue();
            if (result.getExpired() > 0) {
                log.info("审批单超时扫描：{} 张已置为「已超时」", result.getExpired());
            }
        } catch (Exception e) {
            // 定时任务抛异常会被容器吞掉、只留一行日志，且可能让下一次触发不再调度；
            // 显式接住，保证「一轮失败不影响下一轮」
            log.error("审批单超时扫描异常（本轮跳过，下一轮继续）", e);
        }
    }

}
