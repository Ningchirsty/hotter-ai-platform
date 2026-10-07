package org.dromara.aigov.agent.state;

import org.dromara.aigov.agent.enums.AigReleaseGateEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发布状态机的行为锁定测试（设计 §5.4、§6.3）。
 *
 * <p>发布这件事「错了不会报错」：一个没跑过沙箱的版本被置为 STABLE，不会有任何异常，
 * 只是安静地放出去了。因此这里要钉住的不是「代码能跑」，而是三类<b>不会自己暴露</b>的违约：</p>
 * <ol>
 *     <li><b>跳步</b>：DRAFT 直接到 CANDIDATE/STABLE；</li>
 *     <li><b>门槛被绕过</b>：只过黄金用例、没过人工批准就进 CANDIDATE（两把是「与」关系）；</li>
 *     <li><b>版本被「退回重用」</b>：给版本加一条回退边，等于让同一份内容带着旧结论重新过门槛。</li>
 * </ol>
 *
 * <p>纯函数测试：不加载 Spring 上下文，也不碰数据库。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigReleaseStateMachineTest {

    @Test
    @DisplayName("主干逐段合法：DRAFT→VALIDATED→SANDBOX_TESTED→CANDIDATE→STABLE")
    void happyPathIsAllowed() {
        assertTrue(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.DRAFT, AigReleaseStatusEnum.VALIDATED));
        assertTrue(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.VALIDATED, AigReleaseStatusEnum.SANDBOX_TESTED));
        assertTrue(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.SANDBOX_TESTED, AigReleaseStatusEnum.CANDIDATE));
        assertTrue(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.CANDIDATE, AigReleaseStatusEnum.STABLE));
    }

    @Test
    @DisplayName("不许跳步：DRAFT 到不了沙箱/候选/稳定，VALIDATED 到不了候选/稳定")
    void skippingStepsIsRejected() {
        assertFalse(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.DRAFT, AigReleaseStatusEnum.SANDBOX_TESTED),
            "没校验就沙箱 = 跳过了 Manifest 与拒绝规则扫描");
        assertFalse(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.DRAFT, AigReleaseStatusEnum.CANDIDATE));
        assertFalse(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.DRAFT, AigReleaseStatusEnum.STABLE),
            "DRAFT 直达 STABLE 就是「未验证即发布」");
        assertFalse(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.VALIDATED, AigReleaseStatusEnum.CANDIDATE),
            "没跑沙箱就进候选，等于用例是在正式环境跑的");
        assertFalse(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.VALIDATED, AigReleaseStatusEnum.STABLE));
        assertFalse(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.SANDBOX_TESTED, AigReleaseStatusEnum.STABLE),
            "沙箱过了还不等于灰度达标");
    }

    @Test
    @DisplayName("刻意不设「退回」边：版本内容不可变，失败就停在原地或停用，要改就出新版本")
    void noBackwardEdgesBecauseVersionsAreImmutable() {
        assertFalse(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.VALIDATED, AigReleaseStatusEnum.DRAFT));
        assertFalse(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.SANDBOX_TESTED, AigReleaseStatusEnum.VALIDATED));
        assertFalse(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.CANDIDATE, AigReleaseStatusEnum.SANDBOX_TESTED),
            "灰度不达标不能退回重测：那会让同一版本带着旧结论重新过门槛");
        assertFalse(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.STABLE, AigReleaseStatusEnum.CANDIDATE),
            "STABLE 要改就出新版本，而不是把已发布的版本拉回候选");
    }

    @Test
    @DisplayName("尚未停用、且未归档的状态都能停用；所有非归档状态都能归档")
    void everyActiveStateCanBeDisabledAndEveryNonArchivedStateCanBeArchived() {
        for (AigReleaseStatusEnum status : AigReleaseStateMachine.nonTerminalStates()) {
            if (status != AigReleaseStatusEnum.DISABLED) {
                // DISABLED 本身已经停用，再「迁移到 DISABLED」是自环而不是状态变化，
                // 故不在本条断言范围内（自环由下面 noSelfLoops 那条独立钉住）
                assertTrue(AigReleaseStateMachine.canTransition(status, AigReleaseStatusEnum.DISABLED),
                    status.getCode() + " 必须能停用，否则出问题的版本下不了线");
            }
            assertTrue(AigReleaseStateMachine.canTransition(status, AigReleaseStatusEnum.ARCHIVED),
                status.getCode() + " 必须能归档，否则废弃版本会一直留在列表里");
        }
    }

    @Test
    @DisplayName("不许自环：迁移必须改变状态，否则事件流里会灌进一条无信息的记录")
    void noSelfLoops() {
        for (AigReleaseStatusEnum status : AigReleaseStatusEnum.values()) {
            assertFalse(AigReleaseStateMachine.canTransition(status, status),
                status.getCode() + " 存在自环：它不改变任何状态，只会让发布记录里多一条无意义的边");
        }
    }

    @Test
    @DisplayName("不变式：任何非终态都至少有一条出边（进入即卡住的状态不允许存在）")
    void everyNonTerminalStateHasAnOutgoingEdge() {
        for (AigReleaseStatusEnum status : AigReleaseStateMachine.nonTerminalStates()) {
            assertFalse(AigReleaseStateMachine.outgoing(status).isEmpty(),
                status.getCode() + " 没有任何出边，一旦进入就永远出不来");
        }
    }

    @Test
    @DisplayName("唯一终态是 ARCHIVED，且它没有任何出边")
    void archivedIsTheOnlyTerminalState() {
        for (AigReleaseStatusEnum status : AigReleaseStatusEnum.values()) {
            assertEquals(status == AigReleaseStatusEnum.ARCHIVED, status.isTerminal(),
                status.getCode() + " 的终态标记与设计不符（设计里只有 ARCHIVED 是终态）");
            assertEquals(status == AigReleaseStatusEnum.ARCHIVED,
                AigReleaseStateMachine.isTerminal(status));
        }
        assertTrue(AigReleaseStateMachine.outgoing(AigReleaseStatusEnum.ARCHIVED).isEmpty(),
            "归档必须彻底：还能再迁出去就不是归档");
        assertFalse(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.ARCHIVED, AigReleaseStatusEnum.STABLE));
        assertTrue(AigReleaseStateMachine.describeAllowed(AigReleaseStatusEnum.ARCHIVED)
            .contains("终态"));
    }

    @Test
    @DisplayName("门槛是「与」关系：沙箱之后要黄金用例**和**三方人工批准，两把都过才能进候选")
    void gatesAreConjunctive() {
        assertEquals(Set.of(AigReleaseGateEnum.MANIFEST_VALIDATION),
            AigReleaseStateMachine.requiredGates(AigReleaseStatusEnum.DRAFT));
        assertEquals(Set.of(AigReleaseGateEnum.SANDBOX_RUN),
            AigReleaseStateMachine.requiredGates(AigReleaseStatusEnum.VALIDATED));
        assertEquals(
            Set.of(AigReleaseGateEnum.GOLDEN_CASE, AigReleaseGateEnum.HUMAN_APPROVAL),
            AigReleaseStateMachine.requiredGates(AigReleaseStatusEnum.SANDBOX_TESTED),
            "设计 §5.4 要求黄金用例与人工批准**都**通过；只过一把就能发布是最大的漏洞");
        assertEquals(Set.of(AigReleaseGateEnum.CANARY),
            AigReleaseStateMachine.requiredGates(AigReleaseStatusEnum.CANDIDATE));

        assertNull(AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.SANDBOX_TESTED,
            List.of(AigReleaseGateEnum.GOLDEN_CASE)),
            "只过黄金用例、没过人工批准，不许进 CANDIDATE");
        assertEquals(AigReleaseStatusEnum.CANDIDATE,
            AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.SANDBOX_TESTED,
                List.of(AigReleaseGateEnum.GOLDEN_CASE, AigReleaseGateEnum.HUMAN_APPROVAL)));
    }

    @Test
    @DisplayName("门槛齐全才前进：主干每一步都由「过对应门槛」推动，缺一把就返回 null")
    void advanceRequiresAllGates() {
        assertEquals(AigReleaseStatusEnum.VALIDATED,
            AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.DRAFT,
                List.of(AigReleaseGateEnum.MANIFEST_VALIDATION)),
            "过 Manifest 校验 → VALIDATED");
        assertEquals(AigReleaseStatusEnum.SANDBOX_TESTED,
            AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.VALIDATED,
                List.of(AigReleaseGateEnum.SANDBOX_RUN)),
            "过沙箱 → SANDBOX_TESTED");
        assertEquals(AigReleaseStatusEnum.STABLE,
            AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.CANDIDATE,
                List.of(AigReleaseGateEnum.CANARY)),
            "灰度达标 → STABLE");

        assertNull(AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.CANDIDATE,
            List.of()), "缺门槛时必须返回 null，而不是放行");
        assertNull(AigReleaseStateMachine.nextIfAllGatesPassed(null,
            List.of(AigReleaseGateEnum.values())), "源状态为空不得推进");
        assertNull(AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.ARCHIVED,
            List.of(AigReleaseGateEnum.values())), "终态不得推进");
    }

    @Test
    @DisplayName("缺门槛时返回 null，且 missingGates 给出差额（含「一个都没过」这种最常见入参）")
    void missingGatesReportsTheGap() {
        assertEquals(Set.of(AigReleaseGateEnum.MANIFEST_VALIDATION),
            AigReleaseStateMachine.missingGates(AigReleaseStatusEnum.DRAFT, List.of()),
            "一个门槛都没过时不应抛异常，而要如实报出还差哪些");
        assertEquals(Set.of(AigReleaseGateEnum.HUMAN_APPROVAL),
            AigReleaseStateMachine.missingGates(AigReleaseStatusEnum.SANDBOX_TESTED,
                List.of(AigReleaseGateEnum.GOLDEN_CASE)));
        assertTrue(AigReleaseStateMachine.missingGates(AigReleaseStatusEnum.STABLE,
                List.of(AigReleaseGateEnum.CANARY))
            .isEmpty(),
            "不接受门槛推进的状态，差额为空（推进与否不由门槛决定）");
        assertNull(AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.DRAFT, List.of()),
            "缺门槛时必须返回 null，而不是放行");
    }

    @Test
    @DisplayName("门槛顺序不能错：在 DRAFT 声称灰度达标不算过（那是状态错乱）")
    void wrongGateForCurrentStateDoesNotAdvance() {
        assertNull(AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.DRAFT,
            List.of(AigReleaseGateEnum.CANARY)));
        assertNull(AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.VALIDATED,
            List.of(AigReleaseGateEnum.MANIFEST_VALIDATION)),
            "已过校验之后再报校验通过，不该把它推到沙箱之后");
    }

    @Test
    @DisplayName("多报的门槛不构成障碍：历史通过的集合一并传来应当照常前进（缺、跳才是硬约束）")
    void extraPassedGatesDoNotBlock() {
        assertEquals(AigReleaseStatusEnum.SANDBOX_TESTED,
            AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.VALIDATED,
                List.of(AigReleaseGateEnum.MANIFEST_VALIDATION, AigReleaseGateEnum.SANDBOX_RUN,
                    AigReleaseGateEnum.CANARY)));
    }

    @Test
    @DisplayName("STABLE / DISABLED / ARCHIVED 不接受门槛推进（停用与重新启用是运维动作，不是过门槛）")
    void statesWithoutGateAdvanceReturnNull() {
        List<AigReleaseGateEnum> all = List.of(AigReleaseGateEnum.values());
        assertTrue(AigReleaseStateMachine.requiredGates(AigReleaseStatusEnum.STABLE).isEmpty());
        assertTrue(AigReleaseStateMachine.requiredGates(AigReleaseStatusEnum.DISABLED).isEmpty());
        assertNull(AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.STABLE, all));
        assertNull(AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.DISABLED, all));
        assertNull(AigReleaseStateMachine.nextIfAllGatesPassed(AigReleaseStatusEnum.ARCHIVED, all));
    }

    @Test
    @DisplayName("停用后可重新启用（回滚目标复位）——但「是否曾 STABLE 过」状态机看不到，必须由服务层证明")
    void reEnablingFromDisabledIsAllowedButNeedsHistoryCheck() {
        assertTrue(AigReleaseStateMachine.canTransition(
            AigReleaseStatusEnum.DISABLED, AigReleaseStatusEnum.STABLE),
            "停用是可撤销的；若不放行，回滚就只能靠直接改库");
        // 边界说明（与生产代码注释一致）：状态机只看当前状态，看不到历史。
        // 若不核对历史，「先停用再启用」就能把从未发布过的版本推成 STABLE。
        // 这条不变式由服务层用发布记录兜住，这里显式记录一下这个分工。
    }

    @Test
    @DisplayName("describeAllowed 要列出允许的去处，不能只说「不行」")
    void describeAllowedListsTargets() {
        String draft = AigReleaseStateMachine.describeAllowed(AigReleaseStatusEnum.DRAFT);
        assertTrue(draft.contains(AigReleaseStatusEnum.VALIDATED.getCode()), draft);
        assertTrue(draft.contains(AigReleaseStatusEnum.DISABLED.getCode()), draft);
        assertTrue(AigReleaseStateMachine.describeAllowed(null).contains("不允许"));
        assertTrue(AigReleaseStateMachine.outgoing(null).isEmpty(), "未知/空状态返回空集合而不是 null");
        assertTrue(AigReleaseStateMachine.requiredGates(null).isEmpty());
    }

    @Test
    @DisplayName("枚举自带非空码与描述，且 find 大小写不敏感")
    void enumsCarryCodeAndDesc() {
        for (AigReleaseStatusEnum item : AigReleaseStatusEnum.values()) {
            assertNotNull(item.getCode());
            assertFalse(item.getCode().isBlank());
            assertFalse(item.getDesc().isBlank(), item.getCode() + " 缺描述，界面会显示空白");
        }
        for (AigReleaseGateEnum item : AigReleaseGateEnum.values()) {
            assertFalse(item.getBasis().isBlank(),
                item.getCode() + " 缺设计依据：评审时无法逐条对齐");
        }
        assertEquals(AigReleaseStatusEnum.CANDIDATE, AigReleaseStatusEnum.find(" candidate "));
        assertNull(AigReleaseStatusEnum.find("NOPE"));
        assertNull(AigReleaseStatusEnum.find(null));
        assertEquals(AigReleaseGateEnum.CANARY, AigReleaseGateEnum.find("canary"));
        assertNull(AigReleaseGateEnum.find("NOPE"));
    }

}
