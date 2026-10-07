package org.dromara.aigov.task.service;

import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskCallbackBo;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.domain.bo.AigTaskQueryBo;
import org.dromara.aigov.task.domain.bo.AigTaskResultBo;
import org.dromara.aigov.task.domain.bo.AigTaskReviewBo;
import org.dromara.aigov.task.domain.vo.AigCallbackVo;
import org.dromara.aigov.task.domain.vo.AigTaskDetailVo;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * AI 统一任务编排服务（设计 §9）。
 *
 * <p><b>三条硬约束</b>（都由实现与测试守着）：</p>
 * <ol>
 *     <li><b>状态只能经 {@link #transition} 变更</b>：它校验迁移合法性（状态机）、
 *         施加乐观锁、并按序追加事件。任何绕过它直接 update 状态的做法都会让
 *         「事件流 = 状态变更历史」这条不变式失效，事后无法复盘；</li>
 *     <li><b>自动化流程不得置 APPROVED</b>：候选选定必须人工
 *         （{@link #recordResult} 会拒绝），任务级通过必须人工（{@link #review}）；</li>
 *     <li><b>回调未验签一律不推进状态</b>：见 {@link #handleCallback}。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface IAigTaskService {

    /**
     * 创建任务：冻结不可变输入快照 + 写创建事件。
     *
     * <p>同一「业务域 + 提交人 + 幂等键」重复提交只建一个任务，返回既有任务ID
     * （设计 §9.2 的幂等要求）。</p>
     *
     * @param bo 创建入参（快照内容由业务域组装成字符串传入）
     * @return 任务ID
     */
    Long create(AigTaskCreateBo bo);

    /**
     * 状态迁移（<b>唯一入口</b>）：校验合法边 + 乐观锁 + 追加事件。
     *
     * @param taskId          任务ID
     * @param expectedVersion 期望版本（乐观锁）
     * @param toStatus        目标状态
     * @param detail          可读说明（写入事件）
     * @param payloadJson     事件载荷（可空；不得含密钥与受限原文）
     * @return 迁移后的任务
     */
    AigTask transition(Long taskId, Integer expectedVersion, AigTaskStatusEnum toStatus,
                       String detail, String payloadJson);

    /**
     * 标记任务已派发：迁移到 DISPATCHED 并写入 Provider 与外部作业ID。
     *
     * <p><b>为什么必须与状态迁移在同一次更新里完成</b>：回调是按
     * {@code providerCode + providerJobId} 定位任务的。若先迁移状态、再单独写作业ID，
     * 中间那一小段时间里任务已经是 DISPATCHED 却还没有作业ID——
     * 此时到达的回调定位不到任务，会被记成 {@code TASK_NOT_FOUND} 并丢弃，
     * 而那个任务从此再没人推进。合并成一次更新就没有这个窗口。</p>
     *
     * @param taskId          任务ID
     * @param expectedVersion 期望版本
     * @param providerCode    Provider 编码
     * @param providerJobId   外部作业ID
     * @param externalCall    本次是否发生外部调用（由路由层按实际决策传入；
     *                        本地 Provider 也要如实记 N，否则合规统计会虚高）
     * @return 迁移后的任务
     */
    AigTask markDispatched(Long taskId, Integer expectedVersion, String providerCode,
                           String providerJobId, boolean externalCall);

    /**
     * 记录执行失败并给出落点（设计 §9.2：RUNNING→FAILED→RETRY_WAIT→QUEUED 或 NEED_HUMAN）。
     *
     * <p>先记 FAILED（把这次尝试计入 {@code attempt_no}），再按错误分类决定
     * 是等待重试还是转人工；分类未知时转人工而不是盲目重试。</p>
     *
     * @param taskId          任务ID
     * @param expectedVersion 期望版本
     * @param errorClass      错误分类（可空=未知，将转人工）
     * @param errorMessage    可读失败原因
     * @return 失败后的落点状态
     */
    AigTaskStatusEnum recordFailure(Long taskId, Integer expectedVersion, AigErrorClassEnum errorClass,
                                    String errorMessage);

    /**
     * 回写任务结果（候选资产）。
     *
     * <p><b>拒绝 {@code APPROVED}</b>：自动流程只筛除、不放行。
     * 选定候选请走人工选定入口（本期未开放，见 {@code AigCandidateStatusEnum} 的说明）。</p>
     *
     * @param bo 结果入参
     * @return 结果ID
     */
    Long recordResult(AigTaskResultBo bo);

    /**
     * 人工复核（任务级通过/拒绝）。
     *
     * @param bo 复核入参
     * @return 复核后的任务
     */
    AigTask review(AigTaskReviewBo bo);

    /**
     * 分页查询任务（只读列表视图，不含快照原文与事件流）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 任务分页结果
     */
    PageResult<AigTaskVo> queryPage(AigTaskQueryBo bo, PageQuery pageQuery);

    /**
     * 取任务详情：任务 + 输入快照 + 事件流 + 候选结果（一次给全）。
     *
     * @param taskId 任务ID
     * @return 详情
     */
    AigTaskDetailVo getDetail(Long taskId);

    /**
     * 处理 Provider 回调：验签 → 幂等 → 定位任务 → 按状态机推进 → 记账。
     *
     * @param bo 回调入参（原始载荷，不得重新序列化）
     * @return 处理结果
     */
    AigCallbackVo handleCallback(AigTaskCallbackBo bo);

}
