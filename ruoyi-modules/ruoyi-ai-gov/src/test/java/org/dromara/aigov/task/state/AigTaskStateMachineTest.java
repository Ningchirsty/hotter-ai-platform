package org.dromara.aigov.task.state;

import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务状态机行为锁定测试（设计 §9.2）。
 *
 * <p><b>为什么这里要写得这么细</b>：状态迁移错一次不会抛异常，只会让任务卡住或让审计链
 * 自相矛盾——而两者都要到「有人发现某个任务一星期没动」时才暴露。因此本测试不只验
 * 「设计写的那几条边能走」，还穷举校验三条结构性不变式：</p>
 * <ol>
 *     <li><b>非终态必有一条出边</b>：漏了就会出现「进得去、出不来」的僵尸状态；</li>
 *     <li><b>终态必无出边</b>：否则终态还能继续变，账目与统计会前后不一致；</li>
 *     <li><b>{@code isTerminal()} 与状态图必须一致</b>：一处声明「谁是终态」、
 *         另一处声明「谁能走到谁」，两者不一致时总有一边是错的，而运行时不会有人告诉你。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskStateMachineTest {

    @Test
    @DisplayName("设计 §9.2 主干路径的每一条边都能走")
    void mainChainEdgesAreAllowed() {
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.DRAFT, AigTaskStatusEnum.POLICY_CHECKING),
            "DRAFT → POLICY_CHECKING");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.POLICY_CHECKING, AigTaskStatusEnum.QUEUED),
            "POLICY_CHECKING → QUEUED");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.QUEUED, AigTaskStatusEnum.DISPATCHED),
            "QUEUED → DISPATCHED");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.DISPATCHED, AigTaskStatusEnum.RUNNING),
            "DISPATCHED → RUNNING");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.RUNNING, AigTaskStatusEnum.SUCCEEDED),
            "RUNNING → SUCCEEDED");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.SUCCEEDED, AigTaskStatusEnum.REVIEW_PENDING),
            "SUCCEEDED → REVIEW_PENDING（执行成功不等于审核通过）");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.REVIEW_PENDING, AigTaskStatusEnum.APPROVED),
            "REVIEW_PENDING → APPROVED");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.REVIEW_PENDING, AigTaskStatusEnum.REJECTED),
            "REVIEW_PENDING → REJECTED");
    }

    @Test
    @DisplayName("设计 §9.2 异常分支的每一条边都能走")
    void exceptionBranchEdgesAreAllowed() {
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.POLICY_CHECKING, AigTaskStatusEnum.REJECTED),
            "POLICY_CHECKING → REJECTED");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.RUNNING, AigTaskStatusEnum.FAILED),
            "RUNNING → FAILED");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.FAILED, AigTaskStatusEnum.RETRY_WAIT),
            "FAILED → RETRY_WAIT");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.RETRY_WAIT, AigTaskStatusEnum.QUEUED),
            "RETRY_WAIT → QUEUED");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.RUNNING, AigTaskStatusEnum.CANCEL_REQUESTED),
            "RUNNING → CANCEL_REQUESTED");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.CANCEL_REQUESTED, AigTaskStatusEnum.CANCELLED),
            "CANCEL_REQUESTED → CANCELLED");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.FAILED, AigTaskStatusEnum.NEED_HUMAN),
            "FAILED → NEED_HUMAN");
    }

    @Test
    @DisplayName("不变式一：任何非终态都必须至少有一条出边（否则进去就出不来）")
    void everyNonTerminalStateHasAnExit() {
        for (AigTaskStatusEnum status : AigTaskStateMachine.nonTerminalStates()) {
            assertFalse(AigTaskStateMachine.outgoing(status).isEmpty(),
                status.getCode() + " 是非终态却没有出边——一旦进入就永远卡住，"
                    + "界面上表现为「一直转圈」，既无失败原因也无可操作项");
        }
    }

    @Test
    @DisplayName("不变式二：终态不得有任何出边（否则终态还能再变，统计与账目会前后不一致）")
    void terminalStatesHaveNoExit() {
        for (AigTaskStatusEnum status : AigTaskStatusEnum.values()) {
            if (status.isTerminal()) {
                assertTrue(AigTaskStateMachine.outgoing(status).isEmpty(),
                    status.getCode() + " 被声明为终态却有出边：" + AigTaskStateMachine.outgoing(status));
            }
        }
    }

    @Test
    @DisplayName("不变式三：枚举的 isTerminal 与状态图必须一致（两侧不一致时总有一边是错的）")
    void terminalFlagMatchesTheGraph() {
        for (AigTaskStatusEnum status : AigTaskStatusEnum.values()) {
            boolean noExit = AigTaskStateMachine.outgoing(status).isEmpty();
            assertEquals(status.isTerminal(), noExit,
                status.getCode() + " 的 terminal 标记与状态图不一致："
                    + "枚举说 terminal=" + status.isTerminal() + "，图里出边为空=" + noExit);
        }
    }

    @Test
    @DisplayName("不变式四：迁移图覆盖枚举里的每一个状态（新增状态忘了配边 → 僵尸状态）")
    void graphCoversEveryStatus() {
        for (AigTaskStatusEnum status : AigTaskStatusEnum.values()) {
            // outgoing 对未知状态返回空集合，因此必须另用一个「已知状态」来区分
            // 「终态（本就没有出边）」与「压根没配」
            assertTrue(AigTaskStateMachine.nonTerminalStates().contains(status) || status.isTerminal(),
                "状态 " + status.getCode() + " 既不在非终态集合里也没被声明为终态");
            if (!status.isTerminal()) {
                assertNotNull(AigTaskStateMachine.outgoing(status), status.getCode() + " 未在迁移图中配置");
            }
        }
    }

    @Test
    @DisplayName("反向与跨级迁移一律拒绝：已取消不能复活、待复核不能直接改执行中")
    void illegalTransitionsAreRejected() {
        assertFalse(AigTaskStateMachine.canTransition(AigTaskStatusEnum.CANCELLED, AigTaskStatusEnum.RUNNING),
            "已取消是终态，不能复活——否则会出现在跑的任务被记成已取消、或反之的双重事实");
        assertFalse(AigTaskStateMachine.canTransition(AigTaskStatusEnum.APPROVED, AigTaskStatusEnum.REVIEW_PENDING),
            "已通过不能再退回待复核");
        assertFalse(AigTaskStateMachine.canTransition(AigTaskStatusEnum.REVIEW_PENDING, AigTaskStatusEnum.RUNNING),
            "待复核不能直接改成执行中（要变更结论必须走新的任务）");
        assertFalse(AigTaskStateMachine.canTransition(AigTaskStatusEnum.DRAFT, AigTaskStatusEnum.RUNNING),
            "不能跳级：DRAFT 必须经 POLICY_CHECKING/QUEUED/DISPATCHED 才能到 RUNNING");
        assertFalse(AigTaskStateMachine.canTransition(null, AigTaskStatusEnum.QUEUED), "源状态为空视为非法");
        assertFalse(AigTaskStateMachine.canTransition(AigTaskStatusEnum.QUEUED, null), "目标状态为空视为非法");
    }

    @Test
    @DisplayName("取消是两阶段：请求取消后仍可被 Provider 的结果推回执行中")
    void cancelRequestedCanGoBackToRunning() {
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.CANCEL_REQUESTED, AigTaskStatusEnum.RUNNING),
            "Provider 可能拒绝取消（作业已不可中断或已生成完毕）。若只能去 CANCELLED，"
                + "就会把「其实跑完并出了结果」的任务记成已取消，结果与实际不一致");
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.CANCEL_REQUESTED, AigTaskStatusEnum.CANCELLED),
            "Provider 确认取消后落 CANCELLED");
    }

    @Test
    @DisplayName("提交失败有出口：DISPATCHED 也能转 FAILED")
    void dispatchFailureHasAnExit() {
        assertTrue(AigTaskStateMachine.canTransition(AigTaskStatusEnum.DISPATCHED, AigTaskStatusEnum.FAILED),
            "提交本身会失败（Provider 拒收/网络不通/建作业报错）。没有这条边，"
                + "提交失败就只能挂在 DISPATCHED，既不在跑也没失败，无法重试也无法告警");
    }

    @Test
    @DisplayName("策略结论映射：PASS→QUEUED、REJECT→REJECTED、MANUAL→NEED_HUMAN")
    void policyResultMapsToStatus() {
        assertEquals(AigTaskStatusEnum.QUEUED, AigTaskStateMachine.nextAfterPolicy("PASS"));
        assertEquals(AigTaskStatusEnum.REJECTED, AigTaskStateMachine.nextAfterPolicy("REJECT"));
        assertEquals(AigTaskStatusEnum.NEED_HUMAN, AigTaskStateMachine.nextAfterPolicy("MANUAL"),
            "路由判 MANUAL（如 fallbackToManual='Y' 且无可用模型）必须有人接手，"
                + "不能悄悄停在某个中间态");
        assertEquals(AigTaskStatusEnum.QUEUED, AigTaskStateMachine.nextAfterPolicy("pass"),
            "结论大小写不敏感");
        assertNull(AigTaskStateMachine.nextAfterPolicy("WHATEVER"),
            "无法识别的结论返回 null，由调用方拒绝——不猜一个状态，猜错会让任务走向错误的分支");
        assertNull(AigTaskStateMachine.nextAfterPolicy(null));
    }

    @Test
    @DisplayName("执行成功/失败的目标状态：只有执行中（含已派发）才谈得上结果")
    void successAndFailureTargets() {
        assertEquals(AigTaskStatusEnum.SUCCEEDED,
            AigTaskStateMachine.nextOnProviderSuccess(AigTaskStatusEnum.RUNNING));
        assertEquals(AigTaskStatusEnum.FAILED,
            AigTaskStateMachine.nextOnProviderFailure(AigTaskStatusEnum.RUNNING));
        assertNull(AigTaskStateMachine.nextOnProviderSuccess(AigTaskStatusEnum.DRAFT),
            "还没执行的草稿不可能「执行成功」——这类回调必须被拒绝，而不是照单全收");
        assertNull(AigTaskStateMachine.nextOnProviderFailure(AigTaskStatusEnum.APPROVED),
            "已通过的任务不该再被一条失败回调改写");
    }

    @Test
    @DisplayName("失败落点：可重试且还有次数→RETRY_WAIT；要人工→NEED_HUMAN；都不满足→停在 FAILED")
    void restingAfterFailureDecisions() {
        assertEquals(AigTaskStatusEnum.RETRY_WAIT,
            AigTaskStateMachine.restingAfterFailure(AigErrorClassEnum.TIMEOUT, 1, 3),
            "超时可重试且还有余额，应进入等待重试");
        assertEquals(AigTaskStatusEnum.NEED_HUMAN,
            AigTaskStateMachine.restingAfterFailure(AigErrorClassEnum.INVALID_REQUEST, 1, 3),
            "参数错重试一百次也是同样的错，必须转人工补输入");
        assertEquals(AigTaskStatusEnum.NEED_HUMAN,
            AigTaskStateMachine.restingAfterFailure(AigErrorClassEnum.OUTPUT_UNPARSABLE, 1, 3),
            "输出不可解析要人工确认，且不向同一模型重试");
        assertEquals(AigTaskStatusEnum.NEED_HUMAN,
            AigTaskStateMachine.restingAfterFailure(null, 1, 3),
            "错误分类未知时不自己决定重试——把不确定交给人工，而不是拿预算去试");
        assertEquals(AigTaskStatusEnum.FAILED,
            AigTaskStateMachine.restingAfterFailure(AigErrorClassEnum.AUTH_FAILED, 1, 3),
            "鉴权失败的正确动作是运维换密钥，不是反复重试同一个错密钥（会把账号打到风控）");
    }

    @Test
    @DisplayName("失败落点：重试次数耗尽后不再重试")
    void retryStopsWhenAttemptBudgetIsExhausted() {
        assertEquals(AigTaskStatusEnum.RETRY_WAIT,
            AigTaskStateMachine.restingAfterFailure(AigErrorClassEnum.UNAVAILABLE, 2, 3),
            "第 2 次失败后仍可进行第 3 次尝试");
        assertEquals(AigTaskStatusEnum.FAILED,
            AigTaskStateMachine.restingAfterFailure(AigErrorClassEnum.UNAVAILABLE, 3, 3),
            "已尝试 3 次（等于上限）后不得再自动重试——否则「最多 3 次」形同虚设，预算会被无声烧掉");
        assertEquals(AigTaskStatusEnum.FAILED,
            AigTaskStateMachine.restingAfterFailure(AigErrorClassEnum.UNAVAILABLE, 9, 3),
            "超出上限（数据异常）时同样不得重试");
    }

    @Test
    @DisplayName("次数上限为 0 或负数时退化为至少 1 次尝试，不会出现「一次都不试」")
    void degenerateMaxAttemptStillAllowsOneTry() {
        assertEquals(AigTaskStatusEnum.FAILED,
            AigTaskStateMachine.restingAfterFailure(AigErrorClassEnum.TIMEOUT, 1, 0),
            "上限配成 0 时不重试（第 1 次已用掉）");
        assertEquals(AigTaskStatusEnum.RETRY_WAIT,
            AigTaskStateMachine.restingAfterFailure(AigErrorClassEnum.TIMEOUT, 0, 0),
            "attemptNo=0 表示尚未真正尝试过，此时上限为 0 也应允许第一次尝试；"
                + "否则「预算 0」会让任务连一次都跑不了");
    }

    @Test
    @DisplayName("在途状态判定：已派发/执行中/已请求取消都算在途，且都允许取消")
    void inFlightStatuses() {
        Set<AigTaskStatusEnum> inFlight = EnumSet.noneOf(AigTaskStatusEnum.class);
        for (AigTaskStatusEnum status : AigTaskStatusEnum.values()) {
            if (status.isInFlight()) {
                inFlight.add(status);
            }
        }
        assertEquals(EnumSet.of(AigTaskStatusEnum.DISPATCHED, AigTaskStatusEnum.RUNNING,
            AigTaskStatusEnum.CANCEL_REQUESTED), inFlight);
        assertTrue(AigTaskStatusEnum.CANCEL_REQUESTED.isInFlight(),
            "请求取消后在途判定必须仍为真：Provider 仍可能回报完成，"
                + "那条回调若被当成「任务已结束」丢弃，结果就永远收不回来");
    }

    @Test
    @DisplayName("报错文案要给出允许的目标清单（否则排障的人只知道不行，不知道能去哪）")
    void describeAllowedListsTargets() {
        String text = AigTaskStateMachine.describeAllowed(AigTaskStatusEnum.POLICY_CHECKING);
        assertTrue(text.contains("QUEUED") && text.contains("REJECTED") && text.contains("NEED_HUMAN"),
            "应列出全部允许目标；实际=" + text);
        assertTrue(AigTaskStateMachine.describeAllowed(AigTaskStatusEnum.APPROVED).contains("终态"),
            "终态的说明要直接点明「不允许再迁移」");
    }

}
