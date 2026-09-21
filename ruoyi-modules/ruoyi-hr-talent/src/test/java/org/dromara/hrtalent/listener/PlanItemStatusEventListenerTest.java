package org.dromara.hrtalent.listener;

import org.dromara.hrtalent.domain.entity.RecruitApplication;
import org.dromara.hrtalent.domain.entity.RecruitJob;
import org.dromara.hrtalent.domain.entity.RecruitPlanApplicationRel;
import org.dromara.hrtalent.event.ApplicationStageChangedEvent;
import org.dromara.hrtalent.event.BackgroundResultChangedEvent;
import org.dromara.hrtalent.event.CandidateArrivedEvent;
import org.dromara.hrtalent.event.InterviewResultChangedEvent;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.mapper.RecruitJobMapper;
import org.dromara.hrtalent.mapper.RecruitPlanApplicationRelMapper;
import org.dromara.hrtalent.service.recruitment.IPlanItemStatusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计划任务状态刷新事件监听器单元测试（SPEC-P3 §0 线 F / 设计文档 §7.1.5、§21.6）。
 *
 * <p>覆盖 {@code planItemId} 的解析顺序（事件自带 → 计划计入关系 → 应聘记录岗位 → 岗位上的计划任务）、
 * 解析不到时记日志跳过而不抛异常、以及监听器内部异常不得向外传播。Mapper 与领域服务均用
 * JDK 动态代理手工构造（本机 JVM 禁止 Mockito 自附加）。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class PlanItemStatusEventListenerTest {

    /**
     * 被监听器刷新过的计划任务ID。
     */
    private final List<Long> refreshed = new ArrayList<>();

    /**
     * 构造被测监听器（Mapper 与领域服务均为替身）。
     *
     * @param rels         计划计入关系
     * @param job          应聘记录指向的岗位
     * @param refreshError 领域服务是否抛异常
     * @return 监听器实例
     */
    private PlanItemStatusEventListener listener(List<RecruitPlanApplicationRel> rels, RecruitJob job,
                                                boolean refreshError) {
        RecruitPlanApplicationRelMapper relMapper = stub(RecruitPlanApplicationRelMapper.class, (name, args) ->
            "selectList".equals(name) ? new ArrayList<>(rels) : null);
        RecruitApplicationMapper applicationMapper = stub(RecruitApplicationMapper.class, (name, args) -> {
            if (!"selectById".equals(name)) {
                return null;
            }
            RecruitApplication application = new RecruitApplication();
            application.setApplicationId((Long) args[0]);
            application.setJobId(job == null ? null : job.getJobId());
            return application;
        });
        RecruitJobMapper jobMapper = stub(RecruitJobMapper.class, (name, args) ->
            "selectById".equals(name) ? job : null);
        IPlanItemStatusService service = (IPlanItemStatusService) Proxy.newProxyInstance(
            IPlanItemStatusService.class.getClassLoader(), new Class<?>[]{IPlanItemStatusService.class},
            (proxy, method, args) -> {
                if ("refreshItemStatus".equals(method.getName())) {
                    if (refreshError) {
                        throw new IllegalStateException("模拟刷新失败");
                    }
                    refreshed.add((Long) args[0]);
                    return null;
                }
                if ("toString".equals(method.getName())) {
                    return "PlanItemStatusServiceStub";
                }
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
                return null;
            });
        return new PlanItemStatusEventListener(service, relMapper, applicationMapper, jobMapper);
    }

    /**
     * 构造 Mapper 动态代理替身：{@code Object} 方法返回稳定值，其余交给处理器，未处理返回类型默认值。
     *
     * @param type    Mapper 类型
     * @param handler 方法处理器
     * @param <T>     Mapper 泛型
     * @return 替身实例
     */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            String name = method.getName();
            if ("toString".equals(name)) {
                return type.getSimpleName() + "Stub";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            Object value = handler.apply(name, args);
            return value != null ? value : defaultValue(method.getReturnType());
        });
    }

    /**
     * 取返回类型的默认值。
     *
     * @param type 返回类型
     * @return 默认值
     */
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (char.class.equals(type)) {
            return (char) 0;
        }
        if (long.class.equals(type)) {
            return 0L;
        }
        if (short.class.equals(type)) {
            return (short) 0;
        }
        if (byte.class.equals(type)) {
            return (byte) 0;
        }
        if (double.class.equals(type)) {
            return 0D;
        }
        if (float.class.equals(type)) {
            return 0F;
        }
        return 0;
    }

    /**
     * 构造计划计入关系。
     *
     * @param planItemId 计划任务ID
     * @return 关联关系
     */
    private static RecruitPlanApplicationRel rel(Long planItemId) {
        RecruitPlanApplicationRel rel = new RecruitPlanApplicationRel();
        rel.setRelId(7001L);
        rel.setPlanItemId(planItemId);
        rel.setApplicationId(5001L);
        return rel;
    }

    @Test
    @DisplayName("事件自带计划任务ID时直接刷新该任务")
    void shouldRefreshByExplicitPlanItemId() {
        refreshed.clear();
        listener(List.of(rel(2002L)), null, false)
            .onApplicationStageChanged(new ApplicationStageChangedEvent(5001L, 3001L, 6001L, 1001L,
                "new", "resume_review", "move", 9L));

        assertEquals(List.of(1001L), refreshed);
    }

    @Test
    @DisplayName("事件未带计划任务ID时按计划计入关系解析")
    void shouldResolvePlanItemIdFromRelation() {
        refreshed.clear();
        listener(List.of(rel(2002L)), null, false)
            .onInterviewResultChanged(new InterviewResultChangedEvent(8001L, 5001L, null, 1, "pass", 9L));

        assertEquals(List.of(2002L), refreshed);
    }

    @Test
    @DisplayName("无有效计划计入关系时回落到应聘记录岗位上的计划任务")
    void shouldResolvePlanItemIdFromJob() {
        refreshed.clear();
        RecruitJob job = new RecruitJob();
        job.setJobId(6001L);
        job.setPlanItemId(3003L);

        listener(List.of(), job, false)
            .onBackgroundResultChanged(new BackgroundResultChangedEvent(9001L, 5001L, "pass", "finished", 9L, 9L));

        assertEquals(List.of(3003L), refreshed);
    }

    @Test
    @DisplayName("报到事件按自带计划任务ID刷新")
    void shouldRefreshOnCandidateArrived() {
        refreshed.clear();
        listener(List.of(), null, false)
            .onCandidateArrived(new CandidateArrivedEvent(5001L, 3001L, 4004L, LocalDate.of(2026, 9, 1), 9L));

        assertEquals(List.of(4004L), refreshed);
    }

    @Test
    @DisplayName("解析不到计划任务时记日志跳过，不抛异常也不刷新")
    void shouldSkipWhenPlanItemCannotBeResolved() {
        refreshed.clear();
        PlanItemStatusEventListener listener = listener(List.of(), null, false);

        assertDoesNotThrow(() -> listener.onApplicationStageChanged(
            new ApplicationStageChangedEvent(5001L, 3001L, null, null, "new", "invite", "move", 9L)));
        assertDoesNotThrow(() -> listener.onBackgroundResultChanged(
            new BackgroundResultChangedEvent(9001L, null, "pass", "finished", 9L, 9L)));
        assertDoesNotThrow(() -> listener.onApplicationStageChanged(null));

        assertTrue(refreshed.isEmpty());
    }

    @Test
    @DisplayName("监听器内部异常不得向外传播（不得影响已提交的业务事务）")
    void shouldSwallowRefreshException() {
        refreshed.clear();
        PlanItemStatusEventListener listener = listener(List.of(), null, true);

        assertDoesNotThrow(() -> listener.onApplicationStageChanged(
            new ApplicationStageChangedEvent(5001L, 3001L, null, 1001L, "new", "invite", "move", 9L)));
        assertTrue(refreshed.isEmpty());
    }

}
