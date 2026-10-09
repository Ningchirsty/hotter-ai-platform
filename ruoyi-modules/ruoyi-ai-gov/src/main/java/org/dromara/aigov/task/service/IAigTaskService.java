package org.dromara.aigov.task.service;

import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskCallbackBo;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.domain.bo.AigTaskQueryBo;
import org.dromara.aigov.task.domain.bo.AigTaskResultBo;
import org.dromara.aigov.task.domain.bo.AigTaskResultSelectBo;
import org.dromara.aigov.task.domain.bo.AigTaskReviewBo;
import org.dromara.aigov.task.domain.vo.AigCallbackVo;
import org.dromara.aigov.task.domain.vo.AigTaskDetailVo;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.aigov.task.enums.AigTaskEventTypeEnum;
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
     * 登记一条<b>由业务域执行</b>的任务：创建 + 直接落到「已派发」，执行方标为
     * {@code EXTERNAL}（{@code AigTaskExecutionModeEnum}）。
     *
     * <p><b>给谁用</b>：把编排留在自己域内的业务侧（创作域的图像生成走图像内核
     * {@code ImageTaskSubmissionService}）。这类任务登记进统一任务视图是为了<b>可见</b>，
     * 不是为了交给平台执行——所以它<b>不</b>经历平台的策略校验与排队：那两步的语义是
     * 「平台在做策略校验 / 排队等平台执行」，对不经平台模型调用的任务不成立，
     * 伪造那段历史比跳过它更坏。</p>
     *
     * <p><b>平台不会碰它</b>：调度器的重试重排与超时清扫只处理
     * {@code execution_mode='PLATFORM'} 的任务（见 {@code AigTaskSchedulerImpl}）。
     * 否则会出现「把业务域正在跑的任务重新入队再执行一遍」或「内核还在出图、账上已判超时失败」。</p>
     *
     * <p><b>幂等</b>：沿用创建幂等键（业务域 + 提交人 + 键）。<b>第二次调用返回既有任务且不改动它</b>
     * ——不重置状态、不覆盖外部作业ID：任务的后续状态由业务域回写，重复登记不该把进度抹掉。</p>
     *
     * <p><b>状态回写</b>：业务域拿到结果后用 {@link #transition} / {@link #recordExecutionFacts}
     * 推进同一条任务（{@code DISPATCHED → RUNNING/SUCCEEDED/FAILED}）。回写是<b>拉模式</b>，
     * 一次刷新可能只观测到终态，因此状态机允许 {@code DISPATCHED → SUCCEEDED}——
     * 要求回写先补一条未被观测到的 {@code RUNNING}，等于让它伪造中间态。</p>
     *
     * @param bo             创建入参（快照内容由业务域组装成字符串传入）
     * @param providerCode   业务域侧的 Provider 编码（必填：回写/排障要靠它定位）
     * @param providerJobId  外部作业ID（可空：有的内核不给作业号）
     * @return 登记后的任务（含最终状态与执行方，供业务域记录 taskId）
     */
    AigTask createDispatched(AigTaskCreateBo bo, String providerCode, String providerJobId);

    /**
     * 取任务当前行（<b>业务域事件式回写专用</b>）。
     *
     * <p>回写方（如创作域）不知道这条任务被平台或人工动过几次，而 {@link #transition} 要求
     * 声明期望版本（乐观锁）。给它一个轻量读，让它先读版本再 CAS：这样并发被改时它会
     * <b>响亮地失败</b>，而不是安静地覆盖别人的结论。刻意返回实体而不是详情——
     * {@link #getDetail} 会连事件流与候选结果一起加载，回写用不上。</p>
     *
     * @param taskId 任务ID
     * @return 任务行；不存在时抛错
     */
    AigTask getTask(Long taskId);

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
     * 状态迁移（带失败信息）：与 {@link #transition} 完全一致，另外把失败分类与原因写进
     * {@code aig_task.error_code / error_message}。
     *
     * <p><b>为什么要有这个重载</b>：那两列此前<b>全表无人写入</b>，而治理台的任务列表与详情
     * <b>都在渲染它们</b>——等于那两处永远是空的。平台侧的执行失败走 {@code recordFailure}，
     * 业务域（创作域）回写失败走这里，两条路都要把「为什么失败」落到列上，而不是只塞进事件说明。</p>
     *
     * <p><b>只有失败才写，成功会清空</b>：目标状态是 {@code FAILED} 时写入
     * （{@code errorClass} 为空按 {@code UNKNOWN} 记，不假装分类已知）；目标是 {@code SUCCEEDED} 时
     * <b>清空</b>——一条成功的任务上挂着上一次尝试的旧错误码，比没有错误码更坏
     * （注意 MyBatis-Plus 默认忽略 null 字段，所以这里写的是空串而不是 null）。其余状态不碰这两列
     * （{@code RETRY_WAIT}/{@code NEED_HUMAN} 正需要保留失败原因）。</p>
     *
     * @param taskId          任务ID
     * @param expectedVersion 期望版本（乐观锁）
     * @param toStatus        目标状态
     * @param detail          可读说明（写入事件）
     * @param payloadJson     事件载荷（可空）
     * @param errorClass      错误分类（仅目标为 {@code FAILED} 时使用；可空）
     * @param errorMessage    可读失败原因（仅目标为 {@code FAILED} 时使用；可空）
     * @return 迁移后的任务
     */
    AigTask transition(Long taskId, Integer expectedVersion, AigTaskStatusEnum toStatus,
                       String detail, String payloadJson, AigErrorClassEnum errorClass, String errorMessage);

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
     * 记录一次执行的客观事实（路由快照 / 追踪ID / 是否外发 / 耗时），<b>不改变状态</b>。
     *
     * <p><b>为什么与状态迁移分开</b>：这些是「这次执行发生了什么」的事实，状态是「任务处在哪一步」。
     * 合成一个方法会让「只更新事实」也必须伪造一次状态迁移，而状态机不允许自迁移——
     * 于是要么放宽图（出现无意义的自环），要么在某些路径上丢掉事实（审计断链）。
     *
     * <p>路由快照尤其重要：它是执行与排障的<b>唯一依据</b>——治理配置会变，
     * 只有当时那一刻的路由结论能回答「为什么跑的是这个模型」。</p>
     *
     * @param taskId          任务ID
     * @param expectedVersion 期望版本（乐观锁）
     * @param traceId         调用链ID（关联 aig_invocation_audit）
     * @param routeSnapshot   路由快照（可空）
     * @param externalCall    本次是否发生外部调用
     * @param latencyMs       端到端耗时（可空）
     * @return 更新后的任务
     */
    AigTask recordExecutionFacts(Long taskId, Integer expectedVersion, String traceId,
                                 String routeSnapshot, boolean externalCall, Long latencyMs);

    /**
     * 人工选定候选资产（把候选置为 APPROVED 并记录选定人）。
     *
     * <p><b>这是「自动流程只筛除、不放行」的唯一出口</b>：{@link #recordResult} 会拒绝自动置
     * APPROVED，因此候选要成为交付物只能走这里，且必须记录选定人——一旦选错，
     * 「谁选的」是唯一能追到的责任点。</p>
     *
     * <p><b>口径</b>：①任务必须处于「待人工复核」——未成功、已取消的任务不该产出交付物；
     * ②候选必须属于该任务；③被自动 QA 筛除（REJECTED）的候选不接受选定
     * （要推翻自动结论需要另开显式通道，不能让「已知不合格」的候选顺手变成交付物）；
     * ④同一任务<b>单选</b>：选定一个会取消该任务此前的选定，避免出现两个「已选定」
     * 而无法回答「到底交付哪一张」。</p>
     *
     * @param bo 选定入参
     * @return 选定后的结果ID
     */
    Long selectCandidate(AigTaskResultSelectBo bo);

    /**
     * 向任务事件流追加一条<b>非状态迁移</b>的事实性事件。
     *
     * <p><b>为什么需要这个入口</b>：事件流有两类写入方——状态迁移（{@link #transition} 内部写）
     * 与「发生了别的事」（结果回写、制品入库…）。后者此前只能由本类自己写，
     * 于是别的账本（制品账本）要记事件时，只能自己拼一遍序号与操作者——
     * 那就是「序号生成」有了第二份实现，而它一旦不一致（重号、跳号），
     * 事件流的顺序就不是真相了。这里把序号与操作者收敛到同一处。</p>
     *
     * <p>入参刻意只有说明与载荷：{@code from_status}/{@code to_status} 一律为空
     * （它不是迁移），尝试次数与 traceId 取自任务当前行。</p>
     *
     * @param taskId      任务ID
     * @param type        事件类型（必须是 {@code AigTaskEventTypeEnum} 里的值）
     * @param detail      可读说明
     * @param payloadJson 载荷（可空；不得含密钥与受限原文）
     */
    void recordEvent(Long taskId, AigTaskEventTypeEnum type, String detail, String payloadJson);

    /**
     * 处理 Provider 回调：验签 → 幂等 → 定位任务 → 按状态机推进 → 记账。
     *
     * @param bo 回调入参（原始载荷，不得重新序列化）
     * @return 处理结果
     */
    AigCallbackVo handleCallback(AigTaskCallbackBo bo);

}
