package org.dromara.aigov.task.state;

import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * AI 任务状态机（设计 §9.2）。
 *
 * <p><b>为什么把状态图单独抽成一个纯类</b>：状态迁移是这套编排里唯一「错了就静默坏掉」的东西——
 * 一次非法的反向迁移（比如把已取消的任务改回执行中）不会报错，只会让任务永远卡住、
 * 让审计链前后矛盾。把它做成不依赖 Spring、不依赖数据库的纯函数，才能把
 * 「哪些边合法」「重试何时该停」这些规则完整地测出来；服务层只负责
 * 「按状态机的结论去写库、并在冲突时报错」。</p>
 *
 * <p><b>与设计 §9.2 的关系</b>：设计给出的主干与异常分支全部实现
 * （见 {@link #EDGES} 的注释标记）。此外补齐了若干<b>必要</b>的边，
 * 判据只有一条：<b>任何非终态都必须至少有一条出边</b>，否则一旦进入就永远出不来，
 * 而「卡住」在界面上表现为「任务一直转圈」，既没有失败原因也没有可操作项。
 * 这些补边都由 {@code AigTaskStateMachineTest} 的穷举用例守着，
 * 且不影响设计已定义的任何路径。</p>
 *
 * <p><b>本类不抛异常、不返回 null 作为「非法」的表示</b>：非法迁移由调用方
 * 依据 {@link #canTransition} 的结论自行报错（要有可读的、带允许目标列表的消息），
 * 查询类方法一律返回空集合而不是 null。</p>
 *
 * @author ai-gov
 */
public final class AigTaskStateMachine {

    /**
     * 状态迁移图：key = 源状态，value = 允许到达的状态集合。
     */
    private static final Map<AigTaskStatusEnum, Set<AigTaskStatusEnum>> EDGES = buildEdges();

    private AigTaskStateMachine() {
    }

    /**
     * 构造状态迁移图。
     *
     * @return 不可变的迁移图
     */
    private static Map<AigTaskStatusEnum, Set<AigTaskStatusEnum>> buildEdges() {
        Map<AigTaskStatusEnum, Set<AigTaskStatusEnum>> edges = new EnumMap<>(AigTaskStatusEnum.class);
        // 主干（设计 §9.2）：DRAFT→POLICY_CHECKING→QUEUED→DISPATCHED→RUNNING→SUCCEEDED→REVIEW_PENDING→APPROVED|REJECTED
        edges.put(AigTaskStatusEnum.DRAFT, EnumSet.of(
            // 设计：DRAFT → POLICY_CHECKING
            AigTaskStatusEnum.POLICY_CHECKING,
            // 补边：草稿必须能取消，否则误建的草稿永远留在列表里（它是唯一没有终态出口的入口状态）
            AigTaskStatusEnum.CANCELLED));
        edges.put(AigTaskStatusEnum.POLICY_CHECKING, EnumSet.of(
            // 设计：POLICY_CHECKING → QUEUED
            AigTaskStatusEnum.QUEUED,
            // 设计异常分支：POLICY_CHECKING → REJECTED
            AigTaskStatusEnum.REJECTED,
            // 补边：路由判定为 MANUAL（如 fallbackToManual='Y' 且无可用模型）时必须有人接手，
            //       设计 §4.4 的 MANUAL 结论在任务态上的落点就是这个状态
            AigTaskStatusEnum.NEED_HUMAN,
            // 补边：校验期间用户撤回
            AigTaskStatusEnum.CANCELLED));
        edges.put(AigTaskStatusEnum.QUEUED, EnumSet.of(
            // 设计：QUEUED → DISPATCHED
            AigTaskStatusEnum.DISPATCHED,
            // 补边：**同步 Provider 没有「派发」这一步**——请求发出去就在等结果，
            // 不存在一个可查询的外部作业。若强制先记 DISPATCHED 再记 RUNNING，
            // 每次同步调用都会凭空多一个状态与一条事件，而那个「已派发」既没有作业ID
            // 也不代表任何可观测的中间态。异步 Provider 仍走 QUEUED→DISPATCHED→RUNNING。
            AigTaskStatusEnum.RUNNING,
            // 补边：排队中可取消（还没提交给任何 Provider，取消没有副作用）
            AigTaskStatusEnum.CANCELLED));
        edges.put(AigTaskStatusEnum.DISPATCHED, EnumSet.of(
            // 设计：DISPATCHED → RUNNING
            AigTaskStatusEnum.RUNNING,
            // 补边：**提交本身也会失败**（Provider 拒收、网络不通、作业创建报错）。
            //       设计只写了 RUNNING→FAILED，若没有这条边，提交失败就只能在
            //       DISPATCHED 上永远挂着——既不在跑也没失败，无法重试也无法告警
            AigTaskStatusEnum.FAILED,
            // 补边：已提交但双方还没确认时也可以请求取消（Provider 可能回复「作业已结束」）
            AigTaskStatusEnum.CANCEL_REQUESTED));
        edges.put(AigTaskStatusEnum.RUNNING, EnumSet.of(
            // 设计：RUNNING → SUCCEEDED
            AigTaskStatusEnum.SUCCEEDED,
            // 设计异常分支：RUNNING → FAILED
            AigTaskStatusEnum.FAILED,
            // 设计异常分支：RUNNING → CANCEL_REQUESTED
            AigTaskStatusEnum.CANCEL_REQUESTED));
        edges.put(AigTaskStatusEnum.SUCCEEDED, EnumSet.of(
            // 设计：SUCCEEDED → REVIEW_PENDING（执行成功不等于审核通过）
            AigTaskStatusEnum.REVIEW_PENDING));
        edges.put(AigTaskStatusEnum.REVIEW_PENDING, EnumSet.of(
            // 设计：REVIEW_PENDING → APPROVED 或 REJECTED
            AigTaskStatusEnum.APPROVED,
            AigTaskStatusEnum.REJECTED));
        edges.put(AigTaskStatusEnum.FAILED, EnumSet.of(
            // 设计异常分支：FAILED → RETRY_WAIT
            AigTaskStatusEnum.RETRY_WAIT,
            // 设计异常分支：FAILED → NEED_HUMAN
            AigTaskStatusEnum.NEED_HUMAN));
        edges.put(AigTaskStatusEnum.RETRY_WAIT, EnumSet.of(
            // 设计异常分支：RETRY_WAIT → QUEUED
            AigTaskStatusEnum.QUEUED,
            // 补边：等重试期间也可以放弃
            AigTaskStatusEnum.CANCELLED));
        edges.put(AigTaskStatusEnum.CANCEL_REQUESTED, EnumSet.of(
            // 设计异常分支：CANCEL_REQUESTED → CANCELLED
            AigTaskStatusEnum.CANCELLED,
            // 补边：**Provider 可能拒绝取消**（作业已进入不可中断阶段/已生成完毕）。
            //       此时若只能去 CANCELLED，就会把「其实跑完了、还出了结果」的任务记成已取消，
            //       结果与实际都不一致；因此必须允许回到 RUNNING 把结果收完
            AigTaskStatusEnum.RUNNING));
        edges.put(AigTaskStatusEnum.NEED_HUMAN, EnumSet.of(
            // 补边：人工补好输入/修好配置后重新排队，否则 NEED_HUMAN 就是个死胡同
            AigTaskStatusEnum.QUEUED,
            // 补边：人工判定不值得继续
            AigTaskStatusEnum.REJECTED,
            // 补边：人工关闭
            AigTaskStatusEnum.CANCELLED));
        // 终态：没有任何出边
        edges.put(AigTaskStatusEnum.APPROVED, EnumSet.noneOf(AigTaskStatusEnum.class));
        edges.put(AigTaskStatusEnum.REJECTED, EnumSet.noneOf(AigTaskStatusEnum.class));
        edges.put(AigTaskStatusEnum.CANCELLED, EnumSet.noneOf(AigTaskStatusEnum.class));
        return Collections.unmodifiableMap(edges);
    }

    /**
     * 判断一次迁移是否合法。
     *
     * @param from 源状态（为 null 视为非法）
     * @param to   目标状态（为 null 视为非法）
     * @return 合法返回 true
     */
    public static boolean canTransition(AigTaskStatusEnum from, AigTaskStatusEnum to) {
        if (from == null || to == null) {
            return false;
        }
        return outgoing(from).contains(to);
    }

    /**
     * 取某状态的允许目标集合。
     *
     * @param from 源状态（可为 null）
     * @return 允许的目标集合；未知状态返回空集合（不返回 null）
     */
    public static Set<AigTaskStatusEnum> outgoing(AigTaskStatusEnum from) {
        if (from == null) {
            return Collections.emptySet();
        }
        Set<AigTaskStatusEnum> targets = EDGES.get(from);
        return targets == null ? Collections.emptySet() : targets;
    }

    /**
     * 是否为终态（依据 {@link AigTaskStatusEnum#isTerminal()}）。
     *
     * @param status 状态（可为 null）
     * @return 终态返回 true
     */
    public static boolean isTerminal(AigTaskStatusEnum status) {
        return status != null && status.isTerminal();
    }

    /**
     * 全部非终态（每个都应至少有一条出边）。
     *
     * @return 非终态集合
     */
    public static Set<AigTaskStatusEnum> nonTerminalStates() {
        Set<AigTaskStatusEnum> result = EnumSet.noneOf(AigTaskStatusEnum.class);
        for (AigTaskStatusEnum item : AigTaskStatusEnum.values()) {
            if (!item.isTerminal()) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 执行成功后的下一个状态（设计 §9.2：RUNNING → SUCCEEDED）。
     *
     * @param from 当前状态
     * @return 目标状态；当前状态不允许该迁移时返回 null（由调用方报错）
     */
    public static AigTaskStatusEnum nextOnProviderSuccess(AigTaskStatusEnum from) {
        return AigTaskStatusEnum.RUNNING.equals(from) ? AigTaskStatusEnum.SUCCEEDED : null;
    }

    /**
     * 执行失败后的下一个状态（设计 §9.2：RUNNING → FAILED）。
     *
     * @param from 当前状态
     * @return 目标状态；不允许时返回 null
     */
    public static AigTaskStatusEnum nextOnProviderFailure(AigTaskStatusEnum from) {
        if (isExecuting(from)) {
            return AigTaskStatusEnum.FAILED;
        }
        return null;
    }

    /**
     * 策略判定结果对应的下一个状态。
     *
     * <p>与设计 §4.4 的三个结论一一对应，从而把「路由说 MANUAL，任务态却不知道该去哪」
     * 这类断链消灭在编译期之外：</p>
     * <ul>
     *     <li>{@code PASS} → {@link AigTaskStatusEnum#QUEUED}</li>
     *     <li>{@code REJECT} → {@link AigTaskStatusEnum#REJECTED}</li>
     *     <li>{@code MANUAL} → {@link AigTaskStatusEnum#NEED_HUMAN}</li>
     * </ul>
     *
     * @param policyResult 策略结论（PASS/REJECT/MANUAL，大小写不敏感）
     * @return 目标状态；无法识别的结论返回 null（由调用方拒绝，而不是猜一个）
     */
    public static AigTaskStatusEnum nextAfterPolicy(String policyResult) {
        if (policyResult == null || policyResult.isBlank()) {
            return null;
        }
        return switch (policyResult.trim().toUpperCase()) {
            case "PASS" -> AigTaskStatusEnum.QUEUED;
            case "REJECT" -> AigTaskStatusEnum.REJECTED;
            case "MANUAL" -> AigTaskStatusEnum.NEED_HUMAN;
            default -> null;
        };
    }

    /**
     * 失败之后的落点：重试、转人工，还是停在 FAILED。
     *
     * <p><b>为什么与 {@link #nextOnProviderFailure} 分开</b>：设计 §9.2 的链路是
     * {@code RUNNING → FAILED → RETRY_WAIT → QUEUED}，即「先记失败，再决定要不要重试」。
     * 合成一个方法会让「失败」和「重试决策」互相掩盖——审计里就看不到那次失败发生过多少次。</p>
     *
     * <p><b>三个返回值都有明确含义</b>：</p>
     * <ul>
     *     <li>{@link AigTaskStatusEnum#RETRY_WAIT}：可自动重试且仍有次数余额；</li>
     *     <li>{@link AigTaskStatusEnum#NEED_HUMAN}：错误分类要求人工介入（参数错、输出不可解析），
     *         或分类未知——<b>认不出就不自己决定重试</b>，把不确定交给人工，
     *         而不是拿预算去试；</li>
     *     <li>{@link AigTaskStatusEnum#FAILED}：<b>不迁移</b>。表示「自动流程救不了、也不强制转人工」，
     *         例如鉴权失败（分类里 {@code needsHuman=false}、{@code circuitBreak=true}）：
     *         正确的动作是运维换密钥，而不是让系统反复重试同一个错密钥。</li>
     * </ul>
     *
     * @param errorClass 错误分类（可为 null，视为未知）
     * @param attemptNo  已尝试次数（含本次失败）
     * @param maxAttempt 允许的最大自动尝试次数
     * @return RETRY_WAIT / NEED_HUMAN / FAILED（FAILED 表示停在原地，不产生迁移）
     */
    public static AigTaskStatusEnum restingAfterFailure(AigErrorClassEnum errorClass, int attemptNo, int maxAttempt) {
        if (errorClass == null) {
            return AigTaskStatusEnum.NEED_HUMAN;
        }
        if (errorClass.isNeedsHuman()) {
            return AigTaskStatusEnum.NEED_HUMAN;
        }
        boolean budgetLeft = attemptNo < Math.max(1, maxAttempt);
        if (errorClass.isRetryable() && budgetLeft) {
            return AigTaskStatusEnum.RETRY_WAIT;
        }
        return AigTaskStatusEnum.FAILED;
    }

    /**
     * 是否处于「可被 Provider 结果推进」的状态。
     *
     * @param status 状态
     * @return 是则返回 true
     */
    public static boolean isExecuting(AigTaskStatusEnum status) {
        return AigTaskStatusEnum.RUNNING.equals(status) || AigTaskStatusEnum.DISPATCHED.equals(status);
    }

    /**
     * 生成可读的「允许去哪」说明，用于非法迁移的报错消息。
     *
     * @param from 源状态
     * @return 可读说明
     */
    public static String describeAllowed(AigTaskStatusEnum from) {
        Set<AigTaskStatusEnum> targets = outgoing(from);
        if (targets.isEmpty()) {
            return from == null ? "（源状态为空）" : from.getCode() + " 是终态，不允许再迁移";
        }
        Set<String> codes = new LinkedHashSet<>();
        for (AigTaskStatusEnum item : targets) {
            codes.add(item.getCode());
        }
        return from.getCode() + " 只允许迁移到 " + codes;
    }

}
