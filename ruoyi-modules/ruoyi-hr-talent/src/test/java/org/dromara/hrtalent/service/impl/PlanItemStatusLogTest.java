package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.hrtalent.domainservice.PlanItemStatusDomainService;
import org.dromara.hrtalent.domain.entity.RecruitApplication;
import org.dromara.hrtalent.domain.entity.RecruitJob;
import org.dromara.hrtalent.domain.entity.RecruitPlan;
import org.dromara.hrtalent.domain.entity.RecruitPlanApplicationRel;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
import org.dromara.hrtalent.domain.entity.RecruitPlanItemStatusLog;
import org.dromara.hrtalent.domain.entity.RecruitPlanRollover;
import org.dromara.hrtalent.enums.ApplicationResultEnum;
import org.dromara.hrtalent.enums.CandidateStageEnum;
import org.dromara.hrtalent.enums.PlanCompletionStatusEnum;
import org.dromara.hrtalent.enums.PlanControlStatusEnum;
import org.dromara.hrtalent.enums.PlanExecutionStatusEnum;
import org.dromara.hrtalent.enums.PlanStatusEnum;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.mapper.RecruitJobMapper;
import org.dromara.hrtalent.mapper.RecruitPlanApplicationRelMapper;
import org.dromara.hrtalent.mapper.RecruitPlanItemMapper;
import org.dromara.hrtalent.mapper.RecruitPlanItemStatusLogMapper;
import org.dromara.hrtalent.mapper.RecruitPlanMapper;
import org.dromara.hrtalent.mapper.RecruitPlanRolloverMapper;
import org.dromara.hrtalent.service.recruitment.IPlanItemStatusService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计划任务状态变更日志写入单元测试（设计文档 §7.1.5「记录触发事件、原状态、新状态和刷新时间」、
 * §21.15 伪代码「append status change log」）。
 *
 * <p><b>覆盖范围</b>：</p>
 * <ol>
 *     <li>{@code execution_status} 发生变化时，向 {@code hr_recruit_plan_item_status_log}
 *     追加一条日志，且触发事件、原状态、新状态、刷新时间均正确；</li>
 *     <li>刷新前后 {@code execution_status} 与 {@code completion_status} 都未变化时
 *     <b>不写</b>日志（避免无意义刷表）；</li>
 *     <li>保留的旧签名 {@code refreshItemStatus(Long)} 委托到新重载，触发事件记为
 *     {@code manual_refresh}。</li>
 * </ol>
 *
 * <p><b>说明</b>：Mapper 替身使用 JDK 动态代理手工构造，不依赖 Mockito
 * （当前构建环境的 JVM 不允许 Mockito 以自附加方式装载 Byte Buddy Agent）。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class PlanItemStatusLogTest {

    /**
     * 被测领域服务（无状态，可安全共享）。
     */
    private final PlanItemStatusDomainService domainService = new PlanItemStatusDomainService();

    /**
     * 本次刷新中实际插入的状态变更日志。
     */
    private final List<RecruitPlanItemStatusLog> insertedLogs = new ArrayList<>();

    /**
     * 注册被测实体在 MyBatis-Plus 中的表元数据。
     *
     * <p>脱离 Spring 容器时 {@code TableInfoHelper} 尚无实体缓存，而 Lambda 条件构造器会立即
     * 解析列名，因此这里显式注册，仅用于让替身测试能构造 SQL 片段，不涉及任何数据库连接。</p>
     */
    @BeforeAll
    static void initTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(RecruitPlanItem.class, RecruitApplication.class, RecruitJob.class,
            RecruitPlanApplicationRel.class, RecruitPlan.class, RecruitPlanRollover.class,
            RecruitPlanItemStatusLog.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @BeforeEach
    void clearInsertedLogs() {
        insertedLogs.clear();
    }

    @Test
    @DisplayName("状态实际变化时写日志，且 from/to 与触发事件正确")
    void shouldAppendLogWhenStatusChanged() {
        RecruitPlanItem item = item(PlanExecutionStatusEnum.PENDING.getCode());
        List<RecruitApplication> applications = List.of(application(5001L,
            CandidateStageEnum.SECOND_INTERVIEW.getCode(), ApplicationResultEnum.PROCESSING.getCode()));
        PlanItemStatusServiceImpl service = service(item, applications);

        service.refreshItemStatus(item.getItemId(), IPlanItemStatusService.TRIGGER_APPLICATION_STAGE_CHANGED);

        assertEquals(1, insertedLogs.size(), "状态变化必须追加一条日志");
        RecruitPlanItemStatusLog statusLog = insertedLogs.get(0);
        assertEquals(item.getItemId(), statusLog.getItemId());
        assertEquals(item.getPlanId(), statusLog.getPlanId());
        assertEquals(IPlanItemStatusService.TRIGGER_APPLICATION_STAGE_CHANGED, statusLog.getTriggerEvent());
        assertEquals(PlanExecutionStatusEnum.PENDING.getCode(), statusLog.getFromExecutionStatus());
        assertEquals(PlanExecutionStatusEnum.INTERVIEWING.getCode(), statusLog.getToExecutionStatus());
        assertEquals(PlanCompletionStatusEnum.UNFINISHED.getCode(), statusLog.getFromCompletionStatus());
        assertEquals(PlanCompletionStatusEnum.UNFINISHED.getCode(), statusLog.getToCompletionStatus());
        assertNotNull(statusLog.getRefreshTime(), "日志必须带刷新时间");
        assertSame(item.getLastRefreshTime(), statusLog.getRefreshTime(),
            "日志刷新时间应与 last_refresh_time 同源");
    }

    @Test
    @DisplayName("状态未变化时不写日志")
    void shouldNotAppendLogWhenStatusUnchanged() {
        // 任务已是「面试中」，同一条面试阶段候选人重算后仍为「面试中」，完成状态也未变化
        RecruitPlanItem item = item(PlanExecutionStatusEnum.INTERVIEWING.getCode());
        List<RecruitApplication> applications = List.of(application(5001L,
            CandidateStageEnum.SECOND_INTERVIEW.getCode(), ApplicationResultEnum.PROCESSING.getCode()));
        PlanItemStatusServiceImpl service = service(item, applications);

        service.refreshItemStatus(item.getItemId(), IPlanItemStatusService.TRIGGER_INTERVIEW_RESULT_CHANGED);

        assertTrue(insertedLogs.isEmpty(), "状态未变化不得写日志（避免无意义刷表）");
        assertEquals(PlanExecutionStatusEnum.INTERVIEWING.getCode(), item.getExecutionStatus());
    }

    @Test
    @DisplayName("旧签名 refreshItemStatus(Long) 委托新重载并记为 manual_refresh")
    void shouldTagManualRefreshWhenUsingLegacySignature() {
        RecruitPlanItem item = item(PlanExecutionStatusEnum.PENDING.getCode());
        List<RecruitApplication> applications = List.of(application(5001L,
            CandidateStageEnum.PENDING_ARRIVAL.getCode(), ApplicationResultEnum.PROCESSING.getCode()));
        PlanItemStatusServiceImpl service = service(item, applications);

        service.refreshItemStatus(item.getItemId());

        assertEquals(1, insertedLogs.size());
        assertEquals(IPlanItemStatusService.TRIGGER_MANUAL_REFRESH, insertedLogs.get(0).getTriggerEvent());
        assertEquals(PlanExecutionStatusEnum.PENDING_ARRIVAL.getCode(), insertedLogs.get(0).getToExecutionStatus());
    }

    /* ------------------------------------------------------------------ 测试替身 ------------------------------------------------------------------ */

    /**
     * 构造被测服务（Mapper 全部使用动态代理替身）。
     *
     * @param item         内存中的计划任务
     * @param applications 该任务下的应聘记录
     * @return 服务实例
     */
    private PlanItemStatusServiceImpl service(RecruitPlanItem item, List<RecruitApplication> applications) {
        RecruitPlan plan = new RecruitPlan();
        plan.setPlanId(item.getPlanId());
        plan.setStatus(PlanStatusEnum.EXECUTING.getCode());
        RecruitPlanItemMapper itemMapper = stub(RecruitPlanItemMapper.class, (name, args) -> switch (name) {
            case "selectById" -> item;
            case "update" -> 1;
            default -> null;
        });
        RecruitPlanMapper planMapper = stub(RecruitPlanMapper.class,
            (name, args) -> "selectById".equals(name) ? plan : null);
        RecruitPlanRolloverMapper rolloverMapper = stub(RecruitPlanRolloverMapper.class,
            (name, args) -> "selectCount".equals(name) ? 0L : null);
        RecruitApplicationMapper applicationMapper = stub(RecruitApplicationMapper.class,
            (name, args) -> "selectList".equals(name) ? new ArrayList<>(applications) : null);
        RecruitJobMapper jobMapper = stub(RecruitJobMapper.class,
            (name, args) -> "selectList".equals(name) ? new ArrayList<>() : null);
        RecruitPlanApplicationRelMapper relMapper = stub(RecruitPlanApplicationRelMapper.class,
            (name, args) -> "selectList".equals(name) ? new ArrayList<>(List.of(rel())) : null);
        RecruitPlanItemStatusLogMapper statusLogMapper = stub(RecruitPlanItemStatusLogMapper.class,
            (name, args) -> {
                if ("insert".equals(name)) {
                    insertedLogs.add((RecruitPlanItemStatusLog) args[0]);
                    return 1;
                }
                return null;
            });
        return new PlanItemStatusServiceImpl(itemMapper, planMapper, rolloverMapper, domainService,
            applicationMapper, jobMapper, relMapper, statusLogMapper);
    }

    /**
     * 构造 Mapper 动态代理替身：{@code Object} 方法返回稳定值，其余交给处理器，未处理返回类型默认值。
     *
     * @param type    Mapper 类型
     * @param handler 方法处理器（方法名 + 参数 → 返回值）
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
     * 构造最小可用的计划任务实体。
     *
     * @param executionStatus 刷新前的自动执行阶段
     * @return 计划任务实体
     */
    private static RecruitPlanItem item(String executionStatus) {
        RecruitPlanItem item = new RecruitPlanItem();
        item.setItemId(1001L);
        item.setPlanId(9L);
        item.setPlanMonth("2026-09");
        item.setPlanQty(3);
        item.setCreditedArrivalQty(0);
        item.setRemainingQty(3);
        item.setControlStatus(PlanControlStatusEnum.NORMAL.getCode());
        item.setExecutionStatus(executionStatus);
        item.setCompletionStatus(PlanCompletionStatusEnum.UNFINISHED.getCode());
        return item;
    }

    /**
     * 构造当前有效的计划任务与应聘记录关联关系。
     *
     * @return 关联关系
     */
    private static RecruitPlanApplicationRel rel() {
        RecruitPlanApplicationRel rel = new RecruitPlanApplicationRel();
        rel.setRelId(7001L);
        rel.setPlanItemId(1001L);
        rel.setApplicationId(5001L);
        rel.setCreditedFlag("0");
        return rel;
    }

    /**
     * 构造应聘记录。
     *
     * @param applicationId 应聘记录ID
     * @param stage         当前阶段编码
     * @param status        当前结果编码
     * @return 应聘记录
     */
    private static RecruitApplication application(Long applicationId, String stage, String status) {
        RecruitApplication application = new RecruitApplication();
        application.setApplicationId(applicationId);
        application.setJobId(6001L);
        application.setCurrentStage(stage);
        application.setCurrentStatus(status);
        return application;
    }

}
