package org.dromara.aigov.studio.service.impl;

import org.dromara.aigov.studio.domain.bo.AigStudioProposalCreateBo;
import org.dromara.aigov.studio.service.IAigStudioProposalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 提案服务的<b>事务边界</b>守卫（一个真实线上故障的回归钉）。
 *
 * <h3>它防的是什么</h3>
 * <p>2026-10-12 生产实测 <b>3/3 复现</b>：对话式 Copilot 里，只要模型提出<b>越权路径</b>
 * （例如想改 {@code /providerCapability}），用户收到的不是设计好的 {@code REJECTED} 轮次，而是
 * {@code code=500 发生未知异常，请联系管理员 [错误编号: …]}；而且
 * <b>那一轮对话在 {@code aig_studio_copilot_turn} 里根本不存在</b>（整轮被回滚）。
 * 而该表的建表注释明确写着：模型越界记 {@code REJECTED} 是"这条通道最该被看见的信号"——
 * 也就是说，修之前最重要的那个信号在生产上永远看不到。</p>
 *
 * <h3>为什么会这样（机制）</h3>
 * <ol>
 *     <li>{@code AigStudioCopilotServiceImpl#chat} 带 {@code @Transactional}；</li>
 *     <li>它调用的 {@code create} 也带 {@code @Transactional}（默认 {@code PROPAGATION_REQUIRED}
 *         ⇒ <b>参与同一个事务</b>，不是新事务）；</li>
 *     <li>设计上 chat 要"捕获 create 抛出的『模型越界』业务异常 → 记 {@code REJECTED} 后继续写结论"；</li>
 *     <li>但内层事务方法抛异常时，Spring 会把<b>共享的那个事务</b>标记为 rollback-only；
 *         外层即使 catch 住并继续写完，<b>提交时仍抛 {@code UnexpectedRollbackException}</b>。</li>
 * </ol>
 *
 * <p>⇒ 修法：判定"模型越界"必须发生在调 {@code create} <b>之前</b>，用只读、不落库、
 * <b>不带事务注解</b>的 {@link IAigStudioProposalService#validateForCreate}。
 * 正因为那个方法<b>没有事务拦截器</b>，它抛出的业务异常才不会参与 rollback-only 标记。</p>
 *
 * <p>⚠️ 这条守卫存在的唯一原因，是防止有人"顺手"给 {@code validateForCreate} 补上
 * {@code @Transactional}（看起来完全合理："它要读草稿，给它加个只读事务吧"）——
 * 那会把上面这个故障原样带回来，而且<b>单元测试会全绿</b>：单测里没有事务代理，
 * catch 住就能继续往下写，只有真库上的提交才会炸。这正是它漏到线上的原因。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioProposalTransactionBoundaryTest {

    /**
     * {@code validateForCreate} 的形状（两处都要查：接口与实现）。
     */
    private static final Class<?>[] VALIDATE_PARAMS = {Long.class, AigStudioProposalCreateBo.class, Long.class};

    @Test
    @DisplayName("★ validateForCreate 绝不能带 @Transactional：它一抛异常就会把调用方的事务标成 rollback-only")
    void validateForCreateMustNotBeTransactional() throws Exception {
        for (Class<?> owner : new Class<?>[]{IAigStudioProposalService.class,
            AigStudioProposalServiceImpl.class}) {
            Method method = owner.getMethod("validateForCreate", VALIDATE_PARAMS);
            assertNull(method.getAnnotation(Transactional.class), rejectionMessage(owner));
        }
    }

    @Test
    @DisplayName("create 必须仍然是 @Transactional（校验通过后的落库要保持原子）")
    void createMustStayTransactional() {
        Method method = assertFound(AigStudioProposalServiceImpl.class, "create",
            new Class<?>[]{Long.class, AigStudioProposalCreateBo.class, Long.class});
        assertNotNull(method.getAnnotation(Transactional.class),
            "create 丢了 @Transactional：提案落库不再原子，异常时会留下半条提案。"
                + "（注：注解在实现类上，接口上看不到——别把这条守卫改成查接口）");
    }

    @Test
    @DisplayName("chat 必须仍然是 @Transactional：它与提案落库同事务，是「要么都成、要么都不成」的保证")
    void chatMustStayTransactional() {
        Method method = assertFound(AigStudioCopilotServiceImpl.class, "chat",
            new Class<?>[]{Long.class,
                org.dromara.aigov.studio.domain.bo.AigStudioCopilotChatBo.class, Long.class});
        assertNotNull(method.getAnnotation(Transactional.class),
            "chat 的 @Transactional 是它与提案落库同事务的保证；去掉会改变语义，"
                + "若真要改，请连同 AigStudioProposalTransactionBoundaryTest 的说明一起更新");
    }

    @Test
    @DisplayName("守卫本身要能定位到这些方法（否则它会静默变成空转）")
    void guardCanLocateTheMethods() {
        assertTrue(AigStudioProposalServiceImpl.class.getDeclaredMethods().length > 0);
        assertFound(AigStudioProposalServiceImpl.class, "create",
            new Class<?>[]{Long.class, AigStudioProposalCreateBo.class, Long.class});
        assertFound(AigStudioProposalServiceImpl.class, "validateForCreate", VALIDATE_PARAMS);
    }

    /**
     * 找方法；找不到直接 fail（守卫不能静默跳过）。
     *
     * @param owner  类
     * @param name   方法名
     * @param params 参数类型
     * @return 方法
     */
    private static Method assertFound(Class<?> owner, String name, Class<?>[] params) {
        try {
            return owner.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return fail("找不到 " + owner.getSimpleName() + "#" + name + "：方法被改名/删除了，"
                + "这条守卫会静默失效，请同步更新");
        }
    }

    /**
     * 命中 {@code @Transactional} 时的报错文案。
     *
     * @param owner 哪个类上带了这个注解
     * @return 文案
     */
    private static String rejectionMessage(Class<?> owner) {
        return owner.getSimpleName() + " 的 validateForCreate 被加上了 @Transactional："
            + "它被刻意设计成『只读、不落库、没有事务拦截器』，"
            + "这样它抛出的业务异常才不会参与共享事务的 rollback-only 标记。"
            + "加上注解会让「模型越界」在生产上变回「发生未知异常 + 整轮对话被回滚」"
            + "（2026-10-12 实测 3/3 复现，见 docs/aigov/13-生产核查记录（2026-10-12）.md §十八）。"
            + "需要事务的是 create，不是它。";
    }

}
