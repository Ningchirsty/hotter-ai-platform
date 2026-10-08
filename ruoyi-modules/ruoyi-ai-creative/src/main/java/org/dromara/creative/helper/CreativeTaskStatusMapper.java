package org.dromara.creative.helper;

import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.creative.enums.DpGenerationStatusEnum;

import java.util.List;

/**
 * 创作域候选状态 → 治理层任务状态的映射（设计 §9「新任务走 aig_task」的登记/回写口径）。
 *
 * <p><b>为什么单独抽成纯类</b>：这是两套状态机之间的唯一接口，写错不会报错——只会在治理台里
 * 显示一个与事实不符的任务状态。纯函数才能把八种候选状态逐条钉死。</p>
 *
 * <h3>两套状态机不是一套，映射是刻意的</h3>
 * <p>候选的前六个状态与图像内核同名同义，直接透传；后两个是创作域自己的判断：
 * {@code REJECTED}（被质检筛除或人工否决）、{@code APPROVED}（该屏采用这张）。</p>
 *
 * <table border="1">
 *   <caption>映射表</caption>
 *   <tr><th>dp_generation</th><th>aig_task</th><th>说明</th></tr>
 *   <tr><td>QUEUED</td><td>DISPATCHED</td><td>内核已接单（登记那一刻就落 DISPATCHED，此处是幂等的不变式）</td></tr>
 *   <tr><td>RUNNING</td><td>RUNNING</td><td></td></tr>
 *   <tr><td>SUCCEEDED</td><td>SUCCEEDED</td><td>任务侧 {@code SUCCEEDED} 的定义本就是「执行成功（<b>待复核，不等于通过</b>）」，与「已出图」同义</td></tr>
 *   <tr><td>FAILED</td><td>FAILED</td><td></td></tr>
 *   <tr><td>TIMEOUT</td><td>FAILED</td><td>任务侧没有超时态，归到可重试的失败分类（{@link AigErrorClassEnum#TIMEOUT}）</td></tr>
 *   <tr><td>CANCELED</td><td>CANCELLED</td><td></td></tr>
 *   <tr><td>APPROVED / REJECTED</td><td>两步：{@code REVIEW_PENDING → APPROVED/REJECTED}</td>
 *       <td>人工结论<b>必须经由「待人工复核」</b>——任务侧有「APPROVED 必经 REVIEW_PENDING」这条不变式；
 *           而 {@code SUCCEEDED} 的含义本来就是「等复核」，所以这一步不是伪造</td></tr>
 * </table>
 *
 * <p><b>粒度前提：一候选一任务</b>（用户 2026-10-08 拍定）。{@code APPROVED} 在候选侧是
 * 「这一屏采用这张图」（候选级），只有在「一任务＝一候选」时才等同于任务级通过。
 * 若将来改成「一屏一任务」，本映射立即失效，必须重新定义。</p>
 *
 * @author creative
 */
public final class CreativeTaskStatusMapper {

    /**
     * 登记时的任务状态：内核已接单，直接落「已派发」。
     *
     * <p>不是 {@code QUEUED}——{@code QUEUED} 的含义是「排队等<b>平台</b>执行」，
     * 而这条任务的执行方是业务域，平台不执行也不扫描它。</p>
     */
    public static final AigTaskStatusEnum REGISTERED_STATUS = AigTaskStatusEnum.DISPATCHED;

    private CreativeTaskStatusMapper() {
    }

    /**
     * 内核侧状态 → 任务状态（Kernel-sourced statuses: the six the kernel itself reports）。
     *
     * @param kernel 候选状态（应来自内核回报）
     * @return 对应的任务状态；{@code APPROVED}/{@code REJECTED}（人工结论，非内核回报）
     *     以及 {@code null} 返回 {@code null}——它们不走这条路
     */
    public static AigTaskStatusEnum forKernel(DpGenerationStatusEnum kernel) {
        if (kernel == null) {
            return null;
        }
        return switch (kernel) {
            case QUEUED -> REGISTERED_STATUS;
            case RUNNING -> AigTaskStatusEnum.RUNNING;
            case SUCCEEDED -> AigTaskStatusEnum.SUCCEEDED;
            case FAILED, TIMEOUT -> AigTaskStatusEnum.FAILED;
            case CANCELED -> AigTaskStatusEnum.CANCELLED;
            // 人工结论不是内核回报，必须走 forDecision（两步），否则会绕过 REVIEW_PENDING
            case APPROVED, REJECTED -> null;
        };
    }

    /**
     * 人工结论 → 任务侧要走的<b>两步</b>（顺序即执行顺序）。
     *
     * <p>先 {@code REVIEW_PENDING}（「待人工复核」——候选出图后本来就停在等复核），
     * 再落结论。两步在同一事务里做，中间态对外不可见；这样既如实记录了"经过复核"，
     * 又保住了任务侧「{@code APPROVED} 必经 {@code REVIEW_PENDING}」这条既有不变式
     * （不必为了省一步去加 {@code SUCCEEDED → APPROVED} 这条边）。</p>
     *
     * @param decision 人工结论（只接受 {@code APPROVED}/{@code REJECTED}）
     * @return 要依次迁移到的状态；不是人工结论时返回空列表
     */
    public static List<AigTaskStatusEnum> forDecision(DpGenerationStatusEnum decision) {
        if (decision == DpGenerationStatusEnum.APPROVED) {
            return List.of(AigTaskStatusEnum.REVIEW_PENDING, AigTaskStatusEnum.APPROVED);
        }
        if (decision == DpGenerationStatusEnum.REJECTED) {
            return List.of(AigTaskStatusEnum.REVIEW_PENDING, AigTaskStatusEnum.REJECTED);
        }
        return List.of();
    }

    /**
     * 失败分类：内核超时要能被识别为可重试的超时，而不是笼统的失败。
     *
     * <p>只影响任务上的 {@code error_code}（治理台的失败原因），不影响状态映射——
     * 状态侧 {@code TIMEOUT} 与 {@code FAILED} 都落在 {@code FAILED}。</p>
     *
     * @param kernel 候选状态
     * @return 错误分类；非失败态返回 null
     */
    public static AigErrorClassEnum errorClassFor(DpGenerationStatusEnum kernel) {
        if (kernel == DpGenerationStatusEnum.TIMEOUT) {
            return AigErrorClassEnum.TIMEOUT;
        }
        if (kernel == DpGenerationStatusEnum.FAILED) {
            // 内核自己的错误码语义未与平台错误分类对齐，不猜；如实记 UNKNOWN（会转人工）
            return AigErrorClassEnum.UNKNOWN;
        }
        return null;
    }

}
