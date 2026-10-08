package org.dromara.aigov.config;

import org.dromara.aigov.domain.vo.AigModelHealthProbeVo;
import org.dromara.aigov.job.AigModelHealthProbeJob;
import org.dromara.aigov.service.IAigModelHealthProbeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 调度启用点（第 3 步）的行为锁定测试。
 *
 * <p><b>它守的是什么</b>：不是"配置项存在"，而是<b>"打开开关后 @Scheduled 真的会被触发"</b>。
 * 这一点必须由测试来钉，因为它的失效方式是<b>静默</b>的——{@code @Scheduled} 不生效时
 * 没有异常、没有日志、没有告警，只是"什么都没发生"（R65 就是查这个查了一轮）。</p>
 *
 * <p>所以这里断言的是两件事：容器里确实注册了
 * {@link ScheduledAnnotationBeanPostProcessor}（结构性），以及在打开开关后
 * <b>任务方法真的被调用了一次</b>（行为性）。只断言配置项文本会漏掉真正的坑。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSchedulingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        // ApplicationContextRunner 默认不装配占位符解析器，而 @Scheduled 的
        // fixedDelayString 用的是 ${...} —— 不装就会解析失败，测出来的是假象
        .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
        .withUserConfiguration(AigSchedulingConfig.class, AigModelHealthProbeJob.class);

    @Test
    @DisplayName("★ 打开 aigov.scheduling.enabled 后，@Scheduled 任务必须真的被触发（这才是第 3 步要关掉的坑）")
    void scheduledJobIsActuallyTriggeredWhenSchedulingEnabled() {
        CountDownLatch fired = new CountDownLatch(1);
        IAigModelHealthProbeService probe = mock(IAigModelHealthProbeService.class);
        doAnswer(inv -> {
            fired.countDown();
            return executedVo();
        }).when(probe).probeOnce();

        runner.withBean(IAigModelHealthProbeService.class, () -> probe)
            .withPropertyValues(
                "aigov.scheduling.enabled=true",
                "aigov.model.health-probe.enabled=true")
            .run(ctx -> {
                assertFalse(ctx.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty(),
                    "启用调度后，注册 @Scheduled 的后处理器必须存在");
                assertTrue(fired.await(10, TimeUnit.SECONDS),
                    "打开调度开关后任务必须真的跑过一次——否则就是 R65 那个『静默不执行』");
                assertTrue(ctx.getBeansOfType(AigModelHealthProbeJob.class).size() == 1,
                    "探测任务 bean 应当在容器里");
            });
    }

    @Test
    @DisplayName("★ 未打开调度开关时：不注册后处理器，且任务一次都不许跑（默认关闭=不改变现状）")
    void nothingRunsWhenSchedulingDisabled() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        IAigModelHealthProbeService probe = mock(IAigModelHealthProbeService.class);
        doAnswer(inv -> {
            fired.countDown();
            return executedVo();
        }).when(probe).probeOnce();

        runner.withBean(IAigModelHealthProbeService.class, () -> probe)
            // 探测开关是开的，唯独调度开关没开 —— 正是"允许但没人触发"的那种状态
            .withPropertyValues("aigov.model.health-probe.enabled=true")
            .run(ctx -> {
                assertTrue(ctx.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty(),
                    "未启用调度时不该注册 @Scheduled 后处理器");
                assertFalse(fired.await(1, TimeUnit.SECONDS),
                    "没启用调度就一次都不该跑（它会真的对外调用，误触发是要花钱的）");
            });
    }

    @Test
    @DisplayName("只打开调度、但探测自身的开关关闭时：连任务 bean 都不该存在")
    void probeJobBeanIsAbsentWhenProbeFlagIsOff() {
        IAigModelHealthProbeService probe = mock(IAigModelHealthProbeService.class);
        when(probe.probeOnce()).thenReturn(executedVo());

        runner.withBean(IAigModelHealthProbeService.class, () -> probe)
            .withPropertyValues("aigov.scheduling.enabled=true")
            .run(ctx -> assertTrue(ctx.getBeansOfType(AigModelHealthProbeJob.class).isEmpty(),
                "aigov.model.health-probe.enabled 未开时，任务 bean 不该被创建"));
    }

    private static AigModelHealthProbeVo executedVo() {
        AigModelHealthProbeVo vo = new AigModelHealthProbeVo();
        vo.setExecuted(true);
        return vo;
    }

}
