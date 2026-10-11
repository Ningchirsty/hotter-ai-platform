package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创作域候选侧的「父子对账」视图（增量 19）。
 *
 * <h3>它回答什么</h3>
 * <p>一条创意项目一旦由<b>岗位场景派发</b>创建，账上就有两层任务：</p>
 * <ul>
 *     <li><b>父</b>：派发出来的平台任务（{@code aig_task}，由 aigov 管）——
 *         项目本体（{@code cp_task}）的 {@code platform_task_id} 就指向它；</li>
 *     <li><b>子</b>：设计部在项目里出图时，每个候选由 {@code CreativeTaskLedger} 各登记一条
 *         {@code aig_task}（{@code execution_mode=EXTERNAL}、{@code project_type=CREATIVE}、
 *         {@code project_id=项目ID}），候选行的 {@code aig_task_id} 指向它。</li>
 * </ul>
 *
 * <p>两层账由<b>两段不同的代码</b>推进：候选状态由内核回报驱动，任务状态由创作域
 * {@code writebackKernel}/{@code writebackDecision} 回写。只要其中一条路漏写或写错，
 * 治理台与创作页就会各说各话。本视图把两边<b>并排摆出来</b>并给出结论，让"漂移"在页面上可见，
 * 而不是等有人发现「候选已经出图了，账上还在跑」才去查。</p>
 *
 * <h3>只读</h3>
 * <p>纯查询：不刷新内核状态、不回写任务、不改任何一行（与 {@code CreativeTaskMirrorProvider}
 * 同一条纪律——浏览列表不该触发副作用）。</p>
 *
 * @author creative
 */
@Data
public class CreativeLedgerReconciliationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目ID（{@code cp_task.task_id}）
     */
    private Long projectId;

    /**
     * 父任务ID（项目 {@code cp_task.platform_task_id}）；为空＝该项目不是派发创建的
     */
    private Long platformTaskId;

    /**
     * 父任务（平台任务）；非派发创建或父任务已不存在时为 null
     */
    private TaskRow parent;

    /**
     * 子任务（逐候选）；无候选时为空列表，不为 null
     */
    private List<ChildRow> children;

    /**
     * 汇总
     */
    private Summary summary;

    /**
     * 任务摘要（父与子共用同一形状）。
     *
     * @param taskId        任务ID
     * @param taskNo        任务号
     * @param taskType      任务类型
     * @param status        任务状态
     * @param executionMode 执行方（PLATFORM/EXTERNAL）
     * @param providerCode  Provider 编码（EXTERNAL 任务由谁执行）
     * @param scenarioCode  场景编码（父任务才有）
     * @param capabilityCode 能力编码
     * @param externalRef   派发快照里的 externalRef（父任务才有；即项目ID）
     * @param routeSnapshot 路由/派发快照原文（排障用）
     * @param createTime    创建时间
     */
    public record TaskRow(Long taskId, String taskNo, String taskType, String status,
                          String executionMode, String providerCode, String scenarioCode,
                          String capabilityCode, String externalRef, String routeSnapshot,
                          LocalDateTime createTime) {
    }

    /**
     * 候选行（子）。
     *
     * @param generationId         候选ID（{@code dp_generation.id}）
     * @param candidateNo          候选序号
     * @param candidateStatus      候选状态（内核口径，已落库值）
     * @param expectedLedgerStatus 按映射，该候选状态<b>应当</b>对应到的任务状态（可空＝两套状态机在此不映射）
     * @param ledgerTaskId         治理任务ID（{@code dp_generation.aig_task_id}；空＝未登记）
     * @param ledgerTaskNo         治理任务号
     * @param ledgerStatus         治理任务当前状态
     * @param consistent           两边是否对得上；未登记且无期望状态时为 null（无从判断）
     * @param drift                对账说明（不一致/未登记时给出可读原因；一致时为 null）
     */
    public record ChildRow(Long generationId, Integer candidateNo, String candidateStatus,
                           String expectedLedgerStatus, Long ledgerTaskId, String ledgerTaskNo,
                           String ledgerStatus, Boolean consistent, String drift) {
    }

    /**
     * 对账汇总。
     *
     * @param total        候选总数
     * @param consistent   一致数
     * @param drifted      漂移数（有登记但两边状态对不上）
     * @param unregistered 未登记数（候选没有治理任务ID）
     * @param parentMissing 父任务在派发创建的项目上却读不到（历史清理/数据缺失）
     */
    public record Summary(int total, int consistent, int drifted, int unregistered,
                          boolean parentMissing) {
    }

}
