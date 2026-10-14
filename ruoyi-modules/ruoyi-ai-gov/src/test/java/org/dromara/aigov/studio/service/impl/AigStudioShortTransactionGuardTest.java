package org.dromara.aigov.studio.service.impl;

import org.dromara.aigov.studio.domain.bo.AigStudioCopilotChatBo;
import org.dromara.aigov.studio.domain.bo.AigStudioTestRunBo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 训练台「长事务」守卫（设计 D08：短事务登记 / 独立执行 / 短事务收尾）。
 *
 * <h3>它防的是什么</h3>
 * <p>训练台测试与 Copilot 对话都遵循同一条纪律：<b>先把 RUNNING 证据落库，再发起网络调用</b>，
 * 以便崩溃时仍留下"这次可能真的调用了"的痕迹。但如果整个方法带 {@code @Transactional}，
 * 网络调用期间崩溃/进程被杀会让那条 RUNNING 行<b>随事务回滚</b>——纪律在最需要它的场景下失效。
 * 而单元测试里没有事务代理，这个缺陷<b>测不出来</b>，只有真库上的崩溃才会暴露。</p>
 *
 * <p>因此这里用反射钉住：{@code runTest} 与 {@code chat} 都<b>不得</b>带 {@code @Transactional}。
 * 哪天有人"顺手"给它们补上一个（"要写两张表，给它加个事务吧"），这条守卫会立刻变红。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioShortTransactionGuardTest {

    private static final Class<?>[] RUN_TEST_PARAMS = {Long.class, AigStudioTestRunBo.class, Long.class};

    private static final Class<?>[] CHAT_PARAMS = {Long.class, AigStudioCopilotChatBo.class, Long.class};

    @Test
    @DisplayName("★ runTest 不得带 @Transactional：否则崩溃会让 RUNNING 证据一起回滚")
    void runTestMustNotBeTransactional() throws Exception {
        Method method = AigStudioTestServiceImpl.class.getMethod("runTest", RUN_TEST_PARAMS);
        assertNull(method.getAnnotation(Transactional.class),
            "runTest 带 @Transactional 会把 RUNNING 证据与网络调用绑进同一事务，"
                + "崩溃即丢失证据（D08）。请保持登记/执行/收尾分属不同事务。");
    }

    @Test
    @DisplayName("★ chat 不得带 @Transactional：既保 RUNNING 证据，也避免内层 create 抛异常带崩整轮")
    void chatMustNotBeTransactional() throws Exception {
        Method method = AigStudioCopilotServiceImpl.class.getMethod("chat", CHAT_PARAMS);
        assertNull(method.getAnnotation(Transactional.class),
            "chat 带 @Transactional 时，内层 create 抛异常会把共享事务标成 rollback-only，"
                + "导致整轮对话（含 REJECTED 结论）被回滚（D08）。");
    }

}
