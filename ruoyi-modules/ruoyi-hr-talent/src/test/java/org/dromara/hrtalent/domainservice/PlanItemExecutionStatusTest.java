package org.dromara.hrtalent.domainservice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.hrtalent.domain.entity.RecruitApplication;
import org.dromara.hrtalent.domain.entity.RecruitJob;
import org.dromara.hrtalent.domain.entity.RecruitPlan;
import org.dromara.hrtalent.domain.entity.RecruitPlanApplicationRel;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
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
import org.dromara.hrtalent.service.impl.PlanItemStatusServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计划任务自动执行阶段（{@code execution_status}）推导单元测试（SPEC-P3 §3.2 / 设计文档 §7.1.5）。
 *
 * <p><b>覆盖范围</b>：</p>
 * <ol>
 *     <li>纯函数 {@link PlanItemStatusDomainService#resolveExecutionStatus(boolean, java.util.Collection)}
 *     的优先级：无候选人 → 待启动；有候选人 → 招聘中；面试中 → 面试中；待录用 → 待录用；
 *     待报到 / 已报到 → 待报到；终态候选人一律不参与推导；多个候选人取最靠后阶段。</li>
 *     <li>{@link PlanItemStatusServiceImpl#refreshItemStatus(Long)} 的落库行为：自动推导结果写入
 *     {@code execution_status}、刷新 {@code last_refresh_time}，且更新语句<b>绝不</b>包含
 *     {@code control_status} / {@code control_reason}（人工暂停 / 取消不被覆盖）。</li>
 * </ol>
 *
 * <p><b>说明</b>：Mapper 替身使用 JDK 动态代理手工构造，不依赖 Mockito
 * （当前构建环境的 JVM 不允许 Mockito 以自附加方式装载 Byte Buddy Agent），
 * 与 P2 的 {@code RecruitJobServiceImplTest} 保持同一手法。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class PlanItemExecutionStatusTest {

    /**
     * 被测领域服务（无状态，可安全共享）。
     */
    private final PlanItemStatusDomainService domainService = new PlanItemStatusDomainService();

    /**
     * 注册被测实体在 MyBatis-Plus 中的表元数据。
     *
     * <p>脱离 Spring 容器时 {@code TableInfoHelper} 尚无实体缓存，而 Lambda 条件构造器的
     * {@code in(...)} 会立即解析列名，因此这里显式注册，仅用于让替身测试能构造 SQL 片段，
     * 不涉及任何数据库连接。</p>
     */
    @BeforeAll
    static void initTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(RecruitPlanItem.class, RecruitApplication.class,
            RecruitJob.class, RecruitPlanApplicationRel.class, RecruitPlan.class, RecruitPlanRollover.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    /* ------------------------------------------------------------------ 纯函数：执行阶段推导 ------------------------------------------------------------------ */

    @Test
    @DisplayName("无有效候选人时：任务未启动为待启动，任务已启动为招聘中")
    void shouldResolvePendingOrRecruitingWithoutCandidates() {
        assertEquals(PlanExecutionStatusEnum.PENDING,
            domainService.resolveExecutionStatus(false, List.of()));
        assertEquals(PlanExecutionStatusEnum.PENDING,
            domainService.resolveExecutionStatus(false, null));
        assertEquals(PlanExecutionStatusEnum.RECRUITING,
            domainService.resolveExecutionStatus(true, List.of()));
    }

    @Test
    @DisplayName("存在早期有效候选人时为招聘中")
    void shouldResolveRecruitingWithActiveCandidate() {
        for (CandidateStageEnum stage : List.of(CandidateStageEnum.NEW, CandidateStageEnum.RESUME_REVIEW,
            CandidateStageEnum.INVITE)) {
            assertEquals(PlanExecutionStatusEnum.RECRUITING,
                domainService.resolveExecutionStatus(false, List.of(
                    PlanItemStatusDomainService.ApplicationProgress.of(stage.getCode(),
                        ApplicationResultEnum.PROCESSING.getCode()))),
                "阶段 " + stage.getCode() + " 应推导为招聘中");
        }
    }

    @Test
    @DisplayName("存在面试阶段候选人时为面试中（一面 / 二面 / 背调）")
    void shouldResolveInterviewingWithInterviewStageCandidate() {
        for (CandidateStageEnum stage : List.of(CandidateStageEnum.FIRST_INTERVIEW,
            CandidateStageEnum.SECOND_INTERVIEW, CandidateStageEnum.BACKGROUND)) {
            assertEquals(PlanExecutionStatusEnum.INTERVIEWING,
                domainService.resolveExecutionStatus(false, List.of(
                    PlanItemStatusDomainService.ApplicationProgress.of(stage.getCode(),
                        ApplicationResultEnum.PROCESSING.getCode()))),
                "阶段 " + stage.getCode() + " 应推导为面试中");
        }
    }

    @Test
    @DisplayName("存在待录用候选人时为待录用")
    void shouldResolveOfferWithOfferStageCandidate() {
        assertEquals(PlanExecutionStatusEnum.OFFER,
            domainService.resolveExecutionStatus(false, List.of(
                PlanItemStatusDomainService.ApplicationProgress.of(CandidateStageEnum.OFFER.getCode(),
                    ApplicationResultEnum.PROCESSING.getCode()))));
        // 已通过最终面试（结果 = passed，尚未登记报到）同样至少为待录用
        assertEquals(PlanExecutionStatusEnum.OFFER,
            domainService.resolveExecutionStatus(false, List.of(
                PlanItemStatusDomainService.ApplicationProgress.of(CandidateStageEnum.OFFER.getCode(),
                    ApplicationResultEnum.PASSED.getCode()))));
    }

    @Test
    @DisplayName("存在待报到 / 已报到候选人或已登记报到时为待报到")
    void shouldResolvePendingArrival() {
        assertEquals(PlanExecutionStatusEnum.PENDING_ARRIVAL,
            domainService.resolveExecutionStatus(false, List.of(
                PlanItemStatusDomainService.ApplicationProgress.of(CandidateStageEnum.PENDING_ARRIVAL.getCode(),
                    ApplicationResultEnum.PROCESSING.getCode()))));
        // 已报到阶段
        assertEquals(PlanExecutionStatusEnum.PENDING_ARRIVAL,
            domainService.resolveExecutionStatus(false, List.of(
                PlanItemStatusDomainService.ApplicationProgress.of(CandidateStageEnum.ARRIVED.getCode(),
                    ApplicationResultEnum.PASSED.getCode()))));
        // 阶段尚未推进，但已登记实际报到：以「已登记报到」为准
        assertEquals(PlanExecutionStatusEnum.PENDING_ARRIVAL,
            domainService.resolveExecutionStatus(false, List.of(
                new PlanItemStatusDomainService.ApplicationProgress(CandidateStageEnum.OFFER.getCode(),
                    ApplicationResultEnum.PROCESSING.getCode(), true))));
    }

    @Test
    @DisplayName("多个候选人时取最靠后的有效阶段")
    void shouldTakeFurthestActiveCandidate() {
        List<PlanItemStatusDomainService.ApplicationProgress> progresses = List.of(
            PlanItemStatusDomainService.ApplicationProgress.of(CandidateStageEnum.NEW.getCode(),
                ApplicationResultEnum.PROCESSING.getCode()),
            PlanItemStatusDomainService.ApplicationProgress.of(CandidateStageEnum.SECOND_INTERVIEW.getCode(),
                ApplicationResultEnum.PROCESSING.getCode()),
            PlanItemStatusDomainService.ApplicationProgress.of(CandidateStageEnum.OFFER.getCode(),
                ApplicationResultEnum.REJECTED.getCode()));
        // 待录用的候选人已淘汰，不得把任务拉到待录用；剩余最靠后的是二面
        assertEquals(PlanExecutionStatusEnum.INTERVIEWING,
            domainService.resolveExecutionStatus(false, progresses));
    }

    @Test
    @DisplayName("终态候选人（淘汰 / 放弃 / 人才池 / 已完成）一律不参与执行阶段推导")
    void shouldIgnoreTerminalCandidates() {
        assertTrue(domainService.isTerminalOutcome(ApplicationResultEnum.REJECTED.getCode()));
        assertTrue(domainService.isTerminalOutcome(ApplicationResultEnum.WITHDRAWN.getCode()));
        assertTrue(domainService.isTerminalOutcome(ApplicationResultEnum.TALENT_POOL.getCode()));
        assertTrue(domainService.isTerminalOutcome(ApplicationResultEnum.PASSED.getCode()));
        assertFalse(domainService.isTerminalOutcome(ApplicationResultEnum.PROCESSING.getCode()));
        assertFalse(domainService.isTerminalOutcome(ApplicationResultEnum.PAUSED.getCode()));
        assertFalse(domainService.isTerminalOutcome(null));
        // 「已完成」是正向终态：不属于负向终态，仍表达至少待录用
        assertTrue(domainService.isNegativeTerminalOutcome(ApplicationResultEnum.REJECTED.getCode()));
        assertTrue(domainService.isNegativeTerminalOutcome(ApplicationResultEnum.WITHDRAWN.getCode()));
        assertTrue(domainService.isNegativeTerminalOutcome(ApplicationResultEnum.TALENT_POOL.getCode()));
        assertFalse(domainService.isNegativeTerminalOutcome(ApplicationResultEnum.PASSED.getCode()));
        assertFalse(domainService.isNegativeTerminalOutcome(ApplicationResultEnum.PAUSED.getCode()));

        for (ApplicationResultEnum result : List.of(ApplicationResultEnum.REJECTED,
            ApplicationResultEnum.WITHDRAWN, ApplicationResultEnum.TALENT_POOL)) {
            // 终态候选人即使停留在「待报到」阶段，也不得让任务显示待报到
            assertEquals(PlanExecutionStatusEnum.PENDING,
                domainService.resolveExecutionStatus(false, List.of(
                    PlanItemStatusDomainService.ApplicationProgress.of(
                        CandidateStageEnum.PENDING_ARRIVAL.getCode(), result.getCode()))),
                "结果 " + result.getCode() + " 不应参与推导");
        }
    }

    @Test
    @DisplayName("全部候选人终态后：未启动回落到待启动，已启动回落到招聘中（不回退到更早阶段）")
    void shouldNotRegressWhenAllCandidatesAreTerminal() {
        List<PlanItemStatusDomainService.ApplicationProgress> allTerminal = List.of(
            PlanItemStatusDomainService.ApplicationProgress.of(CandidateStageEnum.PENDING_ARRIVAL.getCode(),
                ApplicationResultEnum.REJECTED.getCode()),
            PlanItemStatusDomainService.ApplicationProgress.of(CandidateStageEnum.OFFER.getCode(),
                ApplicationResultEnum.WITHDRAWN.getCode()));

        assertEquals(PlanExecutionStatusEnum.PENDING,
            domainService.resolveExecutionStatus(false, allTerminal));
        // 任务已启动：全部候选人终态后仍为招聘中（不回落到待启动）
        assertEquals(PlanExecutionStatusEnum.RECRUITING,
            domainService.resolveExecutionStatus(true, allTerminal));
    }

    @Test
    @DisplayName("阶段编码未知的候选人被忽略，不影响其它有效候选人")
    void shouldIgnoreUnknownStage() {
        assertEquals(PlanExecutionStatusEnum.PENDING,
            domainService.resolveExecutionStatus(false, List.of(
                PlanItemStatusDomainService.ApplicationProgress.of("unknown_stage",
                    ApplicationResultEnum.PROCESSING.getCode()))));
        assertEquals(PlanExecutionStatusEnum.OFFER,
            domainService.resolveExecutionStatus(false, List.of(
                PlanItemStatusDomainService.ApplicationProgress.of("unknown_stage",
                    ApplicationResultEnum.PROCESSING.getCode()),
                PlanItemStatusDomainService.ApplicationProgress.of(CandidateStageEnum.OFFER.getCode(),
                    ApplicationResultEnum.PROCESSING.getCode()))));
    }

    /* ------------------------------------------------------------------ 服务层：刷新落库 ------------------------------------------------------------------ */

    @Test
    @DisplayName("刷新时按候选人自动推导 execution_status 并更新 last_refresh_time")
    void shouldDeriveExecutionStatusOnRefresh() {
        RecruitPlanItem item = item(PlanControlStatusEnum.NORMAL.getCode(), null);
        item.setExecutionStatus(PlanExecutionStatusEnum.PENDING.getCode());
        RecruitPlanApplicationRel rel = rel(5001L);
        List<RecruitApplication> applications = List.of(
            application(5001L, CandidateStageEnum.SECOND_INTERVIEW.getCode(),
                ApplicationResultEnum.PROCESSING.getCode()));

        PlanItemStatusServiceImpl service = service(item, rel, applications);
        service.refreshItemStatus(item.getItemId());

        assertNotNull(capturedWrapper, "刷新必须写库");
        String sqlSet = capturedWrapper.getSqlSet();
        assertTrue(sqlSet.contains("execution_status"), "更新语句必须写入执行阶段");
        assertTrue(sqlSet.contains("last_refresh_time"), "更新语句必须刷新最后刷新时间");
        assertTrue(capturedWrapper.getParamNameValuePairs().containsValue(
            PlanExecutionStatusEnum.INTERVIEWING.getCode()), "执行阶段应为面试中");
        assertEquals(PlanExecutionStatusEnum.INTERVIEWING.getCode(), item.getExecutionStatus());
        assertNotNull(item.getLastRefreshTime());
    }

    @Test
    @DisplayName("人工暂停 / 取消时：自动刷新推导执行阶段，但绝不覆盖 control_status 与 control_reason")
    void shouldNotOverrideManualControlStatusOnEventRefresh() {
        for (PlanControlStatusEnum control : List.of(PlanControlStatusEnum.PAUSED,
            PlanControlStatusEnum.CANCELLED)) {
            RecruitPlanItem item = item(control.getCode(), "编制冻结，暂停招聘");
            RecruitPlanApplicationRel rel = rel(5001L);
            List<RecruitApplication> applications = List.of(
                application(5001L, CandidateStageEnum.PENDING_ARRIVAL.getCode(),
                    ApplicationResultEnum.PROCESSING.getCode()));

            PlanItemStatusServiceImpl service = service(item, rel, applications);
            service.refreshItemStatus(item.getItemId());

            assertNotNull(capturedWrapper, "刷新必须写库");
            String sqlSet = capturedWrapper.getSqlSet();
            // 硬约束：更新语句中不得出现人工控制状态与其原因
            assertFalse(sqlSet.contains("control_status"), "更新语句不得写入 control_status");
            assertFalse(sqlSet.contains("control_reason"), "更新语句不得写入 control_reason");
            assertFalse(capturedWrapper.getParamNameValuePairs().containsValue(control.getCode()),
                "更新参数不得包含人工控制状态值");
            assertFalse(capturedWrapper.getParamNameValuePairs().containsValue("编制冻结，暂停招聘"),
                "更新参数不得包含人工控制原因");
            // 自动执行阶段照常推导
            assertTrue(capturedWrapper.getParamNameValuePairs().containsValue(
                PlanExecutionStatusEnum.PENDING_ARRIVAL.getCode()));
            assertEquals(control.getCode(), item.getControlStatus());
            assertEquals("编制冻结，暂停招聘", item.getControlReason());
            assertNotEquals(control.getCode(), item.getExecutionStatus());
        }
    }

    /* ------------------------------------------------------------------ 测试替身 ------------------------------------------------------------------ */

    /**
     * 最近一次 {@code update(null, wrapper)} 捕获到的更新条件构造器。
     */
    private LambdaUpdateWrapper<RecruitPlanItem> capturedWrapper;

    /**
     * 构造被测服务（Mapper 全部使用动态代理替身）。
     *
     * @param item         内存中的计划任务
     * @param rel          当前有效的计划任务关联关系
     * @param applications 该任务下的应聘记录
     * @return 服务实例
     */
    private PlanItemStatusServiceImpl service(RecruitPlanItem item, RecruitPlanApplicationRel rel,
                                              List<RecruitApplication> applications) {
        capturedWrapper = null;
        RecruitPlan plan = new RecruitPlan();
        plan.setPlanId(item.getPlanId());
        plan.setStatus(PlanStatusEnum.EXECUTING.getCode());
        RecruitPlanItemMapper itemMapper = stub(RecruitPlanItemMapper.class, (name, args) -> switch (name) {
            case "selectById" -> item;
            case "update" -> {
                capturedWrapper = castWrapper(args[1]);
                yield 1;
            }
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
            (name, args) -> "selectList".equals(name) ? new ArrayList<>(List.of(rel)) : null);
        RecruitPlanItemStatusLogMapper statusLogMapper = stub(RecruitPlanItemStatusLogMapper.class,
            (name, args) -> null);
        return new PlanItemStatusServiceImpl(itemMapper, planMapper, rolloverMapper, domainService,
            applicationMapper, jobMapper, relMapper, statusLogMapper);
    }

    /**
     * 构造 Mapper 动态代理替身：{@code Object} 方法返回稳定值，其余交给处理器，未处理返回类型默认值。
     *
     * @param type     Mapper 类型
     * @param handler  方法处理器（方法名 + 参数 → 返回值）
     * @param <T>      Mapper 泛型
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
     * 把捕获到的更新构造器转回计划任务类型。
     *
     * @param wrapper 更新构造器
     * @return 计划任务更新构造器
     */
    @SuppressWarnings("unchecked")
    private static LambdaUpdateWrapper<RecruitPlanItem> castWrapper(Object wrapper) {
        return (LambdaUpdateWrapper<RecruitPlanItem>) wrapper;
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
     * @param controlStatus 人工控制状态
     * @param controlReason 人工控制原因
     * @return 计划任务实体
     */
    private static RecruitPlanItem item(String controlStatus, String controlReason) {
        RecruitPlanItem item = new RecruitPlanItem();
        item.setItemId(1001L);
        item.setPlanId(9L);
        item.setPlanMonth("2026-09");
        item.setPlanQty(3);
        item.setCreditedArrivalQty(0);
        item.setRemainingQty(3);
        item.setControlStatus(controlStatus);
        item.setControlReason(controlReason);
        item.setExecutionStatus(PlanExecutionStatusEnum.PENDING.getCode());
        item.setCompletionStatus(PlanCompletionStatusEnum.UNFINISHED.getCode());
        return item;
    }

    /**
     * 构造当前有效的计划任务与应聘记录关联关系。
     *
     * @param applicationId 应聘记录ID
     * @return 关联关系
     */
    private static RecruitPlanApplicationRel rel(Long applicationId) {
        RecruitPlanApplicationRel rel = new RecruitPlanApplicationRel();
        rel.setRelId(7001L);
        rel.setPlanItemId(1001L);
        rel.setApplicationId(applicationId);
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
