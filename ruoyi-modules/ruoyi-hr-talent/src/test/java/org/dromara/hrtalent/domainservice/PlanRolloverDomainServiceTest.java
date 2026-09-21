package org.dromara.hrtalent.domainservice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.domain.entity.RecruitPlan;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
import org.dromara.hrtalent.domain.entity.RecruitPlanRollover;
import org.dromara.hrtalent.enums.PlanCompletionStatusEnum;
import org.dromara.hrtalent.enums.PlanControlStatusEnum;
import org.dromara.hrtalent.enums.PlanExecutionStatusEnum;
import org.dromara.hrtalent.enums.PlanSourceTypeEnum;
import org.dromara.hrtalent.enums.UrgencyEnum;
import org.dromara.hrtalent.mapper.RecruitPlanItemMapper;
import org.dromara.hrtalent.mapper.RecruitPlanMapper;
import org.dromara.hrtalent.mapper.RecruitPlanRolloverMapper;
import org.dromara.hrtalent.service.recruitment.IPlanItemStatusService;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 月度结转领域规则单元测试（SPEC-P2 §4.3 / §21.11）。
 *
 * <p>重点覆盖三项硬要求：</p>
 * <ol>
 *     <li><b>幂等</b>：重复执行不重复生成下月任务（唯一键 {@code (source_item_id, target_month)} 为权威闸门）。</li>
 *     <li><b>失败隔离</b>：单条失败记录原因且不影响整批已成功条目，并可安全重试。</li>
 *     <li><b>不合并、不改月份</b>：每条来源任务各建一条下月任务并复制紧急程度 / 期限标准天数。</li>
 * </ol>
 *
 * <p><b>实现说明</b>：Mapper 替身使用 JDK 动态代理手工构造（本机 JVM 不允许 Mockito 自附加
 * Byte Buddy Agent，详见 C 组结论）；按条件检索的读取方法由测试子类覆写，
 * 更新语句则通过解析 {@code LambdaUpdateWrapper} 的 SET/WHERE 片段回放到内存数据。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class PlanRolloverDomainServiceTest {

    /**
     * 插入目标任务时用于模拟失败的岗位名称标记。
     */
    private static final String FAIL_JOB_NAME = "FAIL_JOB";

    /**
     * 来源月份。
     */
    private static final String SOURCE_MONTH = "2026-08";

    /**
     * 目标月份。
     */
    private static final String TARGET_MONTH = "2026-09";

    /**
     * 匹配 {@code 列名 = #{ew.paramNameValuePairs.键名}} 结构。
     */
    private static final Pattern PARAM_PATTERN =
        Pattern.compile("([a-z_]+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}");

    /**
     * 内存中的计划表头，按主键索引。
     */
    private final Map<Long, RecruitPlan> planStore = new HashMap<>();

    /**
     * 内存中的计划任务，按主键索引（保持插入顺序）。
     */
    private final Map<Long, RecruitPlanItem> itemStore = new LinkedHashMap<>();

    /**
     * 内存中的结转记录，按主键索引。
     */
    private final Map<Long, RecruitPlanRollover> rolloverStore = new LinkedHashMap<>();

    /**
     * 主键生成器。
     */
    private final AtomicLong idSequence = new AtomicLong(1000L);

    /**
     * 是否模拟目标任务写入失败。
     */
    private boolean simulateTargetInsertFailure = true;

    /**
     * 被测领域服务。
     */
    private TestableRolloverDomainService domainService;

    @BeforeEach
    void setUp() {
        planStore.clear();
        itemStore.clear();
        rolloverStore.clear();
        idSequence.set(1000L);
        simulateTargetInsertFailure = true;
        domainService = new TestableRolloverDomainService();
    }

    /**
     * 初始化 MyBatis-Plus 实体的字段缓存。
     *
     * <p>纯单元测试没有 SqlSessionFactory，{@code LambdaUpdateWrapper} 无法解析
     * 实体字段与列名；此处手工登记三张表的 TableInfo，使测试能够校验真实生成的更新语句。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, RecruitPlan.class);
        TableInfoHelper.initTableInfo(assistant, RecruitPlanItem.class);
        TableInfoHelper.initTableInfo(assistant, RecruitPlanRollover.class);
    }

    /* ------------------------------------------------------------------ 幂等 ------------------------------------------------------------------ */

    @Test
    @DisplayName("结转幂等：重复执行不重复生成下月任务，第二次全部安全跳过")
    void shouldBeIdempotentOnRepeatedExecution() {
        itemStore.put(1L, sourceItem(1L, "高级后端工程师", 3, 1, "1"));
        itemStore.put(2L, sourceItem(2L, "结构工程师", 2, 0, "1"));

        PlanRolloverDomainService.RolloverBatchOutcome first =
            domainService.executeBatch(null, SOURCE_MONTH, TARGET_MONTH, null, 9L);
        assertEquals(2, first.successCount());
        assertEquals(0, first.failedCount());
        // 结转人数 = (3-1) + (2-0) = 4
        assertEquals(4, first.totalCarryoverQty());
        // 两条来源任务各生成一条下月任务，绝不合并
        assertEquals(4, itemStore.size());
        assertEquals(2, rolloverStore.size());
        // 原任务被标记为已结转，但原计划人数与原月份保持不变
        RecruitPlanItem source = itemStore.get(1L);
        assertEquals(PlanCompletionStatusEnum.ROLLED_OVER.getCode(), source.getCompletionStatus());
        assertEquals(3, source.getPlanQty());
        assertEquals(SOURCE_MONTH, source.getPlanMonth());
        assertNotNull(source.getLastRefreshTime());

        PlanRolloverDomainService.RolloverBatchOutcome second =
            domainService.executeBatch(null, SOURCE_MONTH, TARGET_MONTH, null, 9L);
        assertEquals(0, second.successCount());
        assertEquals(2, second.skippedCount());
        assertEquals(0, second.failedCount());
        // 幂等：任务数与结转记录数都不再增长
        assertEquals(4, itemStore.size());
        assertEquals(2, rolloverStore.size());
        assertEquals(TARGET_MONTH, planStore.values().iterator().next().getPlanMonth());
    }

    @Test
    @DisplayName("结转跳过：已取消、已关闭结转开关、无剩余人数的任务都不生成下月任务")
    void shouldSkipIneligibleSourceItems() {
        RecruitPlanItem cancelled = sourceItem(1L, "已取消岗位", 3, 0, "1");
        cancelled.setControlStatus(PlanControlStatusEnum.CANCELLED.getCode());
        itemStore.put(1L, cancelled);

        itemStore.put(2L, sourceItem(2L, "关闭结转岗位", 3, 0, "0"));

        RecruitPlanItem finished = sourceItem(3L, "已完成岗位", 2, 2, "1");
        finished.setCompletionStatus(PlanCompletionStatusEnum.COMPLETED.getCode());
        itemStore.put(3L, finished);

        PlanRolloverDomainService.RolloverBatchOutcome outcome =
            domainService.executeBatch(null, SOURCE_MONTH, TARGET_MONTH, null, 9L);
        assertEquals(0, outcome.successCount());
        assertEquals(3, outcome.skippedCount());
        assertEquals(0, outcome.totalCarryoverQty());
        assertEquals(3, itemStore.size());
        assertTrue(rolloverStore.isEmpty());
    }

    /* ------------------------------------------------------------------ 失败隔离与重试 ------------------------------------------------------------------ */

    @Test
    @DisplayName("单条失败不影响整批：成功条目保留，失败条目记录原因且可安全重试")
    void shouldIsolateSingleFailureAndAllowRetry() {
        itemStore.put(1L, sourceItem(1L, "高级后端工程师", 3, 1, "1"));
        itemStore.put(2L, sourceItem(2L, FAIL_JOB_NAME, 2, 0, "1"));

        PlanRolloverDomainService.RolloverBatchOutcome first =
            domainService.executeBatch(null, SOURCE_MONTH, TARGET_MONTH, null, 9L);
        assertEquals(1, first.successCount());
        assertEquals(1, first.failedCount());
        // 成功的条目没有被回滚：两条来源任务 + 一条目标任务
        assertEquals(3, itemStore.size());
        assertEquals(2, rolloverStore.size());
        assertEquals(PlanCompletionStatusEnum.ROLLED_OVER.getCode(),
            itemStore.get(1L).getCompletionStatus());

        RecruitPlanRollover failed = rolloverStore.values().stream()
            .filter(record -> PlanRolloverDomainService.RESULT_FAILED.equals(record.getResult()))
            .findFirst().orElse(null);
        assertNotNull(failed, "失败条目必须落库并记录原因");
        assertNotNull(failed.getFailureReason());
        assertTrue(failed.getFailureReason().contains("模拟目标任务写入失败"),
            "失败原因应保留异常信息，实际为：" + failed.getFailureReason());
        assertNotNull(failed.getBatchNo());

        // 允许安全重试：放开失败模拟后按批次重试
        simulateTargetInsertFailure = false;
        PlanRolloverDomainService.RolloverBatchOutcome retry =
            domainService.retryBatch(first.batchNo(), 9L);
        assertEquals(1, retry.successCount());
        assertEquals(0, retry.failedCount());
        // 失败条目剩余人数为 2，重试后结转 2 人
        assertEquals(2, retry.totalCarryoverQty());
        // 重试后目标任务已生成，且未重复生成
        assertEquals(4, itemStore.size());
        assertEquals(2, rolloverStore.size());
        assertEquals(PlanRolloverDomainService.RESULT_SUCCESS, failed.getResult());
        assertNull(failed.getFailureReason());
        assertNotNull(failed.getTargetItemId());
    }

    /* ------------------------------------------------------------------ 复制规则 ------------------------------------------------------------------ */

    @Test
    @DisplayName("结转任务复制公司/部门/岗位/负责人/紧急程度/期限天数，人数取剩余人数并建立结转链")
    void shouldBuildCarryoverTargetItem() {
        RecruitPlanItem source = sourceItem(1L, "结构工程师", 3, 1, "1");
        source.setUrgency(UrgencyEnum.URGENT.getCode());
        source.setStandardDays(30);
        source.setOwnerId(88L);
        source.setCompanyName("示例集团");

        RecruitPlanItem target = domainService.buildTargetItem(source, 2001L, TARGET_MONTH, "ITM-1", 777L,
            LocalDateTime.of(2026, 9, 1, 0, 10));

        assertEquals("ITM-1", target.getItemNo());
        assertEquals(2001L, target.getPlanId());
        assertEquals(PlanSourceTypeEnum.CARRYOVER.getCode(), target.getSourceType());
        // 结转人数 = 原任务剩余人数，且不复制已计入到岗人数
        assertEquals(2, target.getPlanQty());
        assertEquals(2, target.getRemainingQty());
        assertEquals(0, target.getCreditedArrivalQty());
        // 执行信息复制
        assertEquals(source.getCompanyDeptId(), target.getCompanyDeptId());
        assertEquals("示例集团", target.getCompanyName());
        assertEquals(source.getUseDeptId(), target.getUseDeptId());
        assertEquals(source.getJobName(), target.getJobName());
        assertEquals(source.getOwnerId(), target.getOwnerId());
        assertEquals(UrgencyEnum.URGENT.getCode(), target.getUrgency());
        assertEquals(30, target.getStandardDays());
        // 结转链与批次
        assertEquals(source.getItemId(), target.getPreviousPlanItemId());
        assertEquals(source.getItemId(), target.getRootPlanItemId());
        assertEquals(777L, target.getCarryoverBatchId());
        assertEquals(PlanCompletionStatusEnum.UNFINISHED.getCode(), target.getCompletionStatus());
        assertEquals(PlanControlStatusEnum.NORMAL.getCode(), target.getControlStatus());
        assertEquals(PlanExecutionStatusEnum.PENDING.getCode(), target.getExecutionStatus());
        assertNotNull(target.getLastRefreshTime());
        // 原月任务不被改动
        assertEquals(SOURCE_MONTH, source.getPlanMonth());
        assertEquals(3, source.getPlanQty());

        // 二次结转：根任务保持不变
        source.setRootPlanItemId(500L);
        RecruitPlanItem second = domainService.buildTargetItem(source, 2002L, "2026-10", "ITM-2", 778L,
            LocalDateTime.now());
        assertEquals(500L, second.getRootPlanItemId());
        assertEquals(source.getItemId(), second.getPreviousPlanItemId());
    }

    @Test
    @DisplayName("绝不合并：同公司同部门同岗位人数完全相同的两条来源任务各生成一条下月任务")
    void shouldNeverMergeSimilarItems() {
        itemStore.put(1L, sourceItem(1L, "结构工程师", 2, 0, "1"));
        itemStore.put(2L, sourceItem(2L, "结构工程师", 2, 0, "1"));

        PlanRolloverDomainService.RolloverBatchOutcome outcome =
            domainService.executeBatch(null, SOURCE_MONTH, TARGET_MONTH, null, 9L);
        assertEquals(2, outcome.successCount());
        assertEquals(4, outcome.totalCarryoverQty());
        assertEquals(4, itemStore.size());
        List<RecruitPlanItem> targets = itemStore.values().stream()
            .filter(item -> TARGET_MONTH.equals(item.getPlanMonth()))
            .toList();
        assertEquals(2, targets.size());
    }

    /* ------------------------------------------------------------------ 规则校验 ------------------------------------------------------------------ */

    @Test
    @DisplayName("资格判定：未取消、未完成、有剩余且开启结转才可结转")
    void shouldJudgeCarryoverEligibility() {
        RecruitPlanItem item = sourceItem(1L, "结构工程师", 3, 1, "1");
        assertNull(domainService.ineligibleReason(item));
        assertEquals(2, domainService.remainingQty(item));

        item.setCarryoverEnabled("0");
        assertTrue(domainService.ineligibleReason(item).contains("关闭自动结转"));
        item.setCarryoverEnabled("1");

        item.setControlStatus(PlanControlStatusEnum.CANCELLED.getCode());
        assertTrue(domainService.ineligibleReason(item).contains("已取消"));
        item.setControlStatus(PlanControlStatusEnum.NORMAL.getCode());

        item.setPlanQty(1);
        item.setCreditedArrivalQty(1);
        assertTrue(domainService.ineligibleReason(item).contains("剩余人数为 0"));

        item.setCompletionStatus(PlanCompletionStatusEnum.COMPLETED.getCode());
        assertTrue(domainService.ineligibleReason(item).contains("已完成"));

        assertTrue(domainService.ineligibleReason(null).contains("不存在"));
    }

    @Test
    @DisplayName("月份校验：格式必须为 yyyy-MM，目标月份必须晚于来源月份")
    void shouldValidateMonthRange() {
        assertEquals("2026-09", domainService.parseMonth("2026-09", "目标月份").toString());
        ServiceException formatError = assertThrows(ServiceException.class,
            () -> domainService.parseMonth("2026/09", "目标月份"));
        assertEquals("目标月份格式必须为 yyyy-MM", formatError.getMessage());
        assertThrows(ServiceException.class, () -> domainService.parseMonth(null, "来源月份"));
        assertThrows(ServiceException.class,
            () -> domainService.validateMonthRange(SOURCE_MONTH, SOURCE_MONTH));
        assertThrows(ServiceException.class,
            () -> domainService.validateMonthRange(TARGET_MONTH, SOURCE_MONTH));
        domainService.validateMonthRange(SOURCE_MONTH, TARGET_MONTH);
    }

    /* ------------------------------------------------------------------ 测试脚手架 ------------------------------------------------------------------ */

    /**
     * 构造一条来源任务。
     *
     * @param itemId          任务ID
     * @param jobName         岗位名称
     * @param planQty         计划人数
     * @param credited        已计入到岗人数
     * @param carryoverEnable 结转开关
     * @return 来源任务
     */
    private RecruitPlanItem sourceItem(Long itemId, String jobName, int planQty, int credited,
                                       String carryoverEnable) {
        RecruitPlanItem item = new RecruitPlanItem();
        item.setItemId(itemId);
        item.setItemNo("ITM-" + itemId);
        item.setPlanId(100L);
        item.setCompanyDeptId(10L);
        item.setCompanyName("示例集团");
        item.setUseDeptId(20L);
        item.setUseDeptName("研发中心");
        item.setJobName(jobName);
        item.setPlanMonth(SOURCE_MONTH);
        item.setSourceType(PlanSourceTypeEnum.NEW.getCode());
        item.setPlanQty(planQty);
        item.setCreditedArrivalQty(credited);
        item.setRemainingQty(Math.max(planQty - credited, 0));
        item.setControlStatus(PlanControlStatusEnum.NORMAL.getCode());
        item.setExecutionStatus(PlanExecutionStatusEnum.RECRUITING.getCode());
        item.setCompletionStatus(PlanCompletionStatusEnum.UNFINISHED.getCode());
        item.setCarryoverEnabled(carryoverEnable);
        item.setOwnerId(66L);
        item.setUrgency(UrgencyEnum.NORMAL.getCode());
        item.setStandardDays(15);
        return item;
    }

    /**
     * 覆写按条件检索的读取方法，改用内存数据，避免在测试中解析 MyBatis-Plus 条件对象。
     *
     * @author hr-talent
     */
    private class TestableRolloverDomainService extends PlanRolloverDomainService {

        /**
         * 构造测试用领域服务（无 Spring 容器，自引用回落到 {@code this}）。
         */
        TestableRolloverDomainService() {
            super(planMapperStub(), itemMapperStub(), rolloverMapperStub(),
                new RecruitBusinessNoGenerator(), new PlanItemStatusDomainService(),
                statusServiceStub(), null);
        }

        @Override
        public List<RecruitPlanItem> listSourceItems(Long companyDeptId, String sourceMonth,
                                                     List<Long> sourceItemIds) {
            return itemStore.values().stream()
                .filter(item -> sourceMonth.equals(item.getPlanMonth()))
                .filter(item -> companyDeptId == null || companyDeptId.equals(item.getCompanyDeptId()))
                .filter(item -> sourceItemIds == null || sourceItemIds.isEmpty()
                    || sourceItemIds.contains(item.getItemId()))
                .sorted(Comparator.comparing(RecruitPlanItem::getItemId))
                .toList();
        }

        @Override
        public RecruitPlanRollover findRollover(Long sourceItemId, String targetMonth) {
            if (sourceItemId == null || targetMonth == null) {
                return null;
            }
            return rolloverStore.values().stream()
                .filter(record -> sourceItemId.equals(record.getSourceItemId()))
                .filter(record -> targetMonth.equals(record.getTargetMonth()))
                .findFirst().orElse(null);
        }

        @Override
        public RecruitPlan findPlan(Long companyDeptId, String planMonth) {
            return planStore.values().stream()
                .filter(plan -> companyDeptId.equals(plan.getCompanyDeptId()))
                .filter(plan -> planMonth.equals(plan.getPlanMonth()))
                .findFirst().orElse(null);
        }

        @Override
        public List<RecruitPlanRollover> listFailedOfBatch(String batchNo) {
            return rolloverStore.values().stream()
                .filter(record -> batchNo.equals(record.getBatchNo()))
                .filter(record -> !PlanRolloverDomainService.RESULT_SUCCESS.equals(record.getResult()))
                .sorted(Comparator.comparing(RecruitPlanRollover::getRolloverId))
                .toList();
        }
    }

    /**
     * 构造计划表头 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private RecruitPlanMapper planMapperStub() {
        return (RecruitPlanMapper) Proxy.newProxyInstance(
            RecruitPlanMapper.class.getClassLoader(),
            new Class<?>[]{RecruitPlanMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> planStore.get((Long) args[0]);
                case "insert" -> {
                    RecruitPlan plan = (RecruitPlan) args[0];
                    boolean duplicated = planStore.values().stream()
                        .anyMatch(exist -> exist.getCompanyDeptId().equals(plan.getCompanyDeptId())
                            && exist.getPlanMonth().equals(plan.getPlanMonth()));
                    if (duplicated) {
                        throw new DuplicateKeyException("uk_hr_recruit_plan_company_month");
                    }
                    plan.setPlanId(idSequence.incrementAndGet());
                    planStore.put(plan.getPlanId(), plan);
                    yield 1;
                }
                case "update", "updateById" -> 1;
                case "selectCount" -> 0L;
                case "toString" -> "RecruitPlanMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造计划任务 Mapper 替身。
     *
     * @return Mapper 替身
     */
    private RecruitPlanItemMapper itemMapperStub() {
        return (RecruitPlanItemMapper) Proxy.newProxyInstance(
            RecruitPlanItemMapper.class.getClassLoader(),
            new Class<?>[]{RecruitPlanItemMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "selectById" -> itemStore.get((Long) args[0]);
                case "insert" -> {
                    RecruitPlanItem item = (RecruitPlanItem) args[0];
                    if (simulateTargetInsertFailure && FAIL_JOB_NAME.equals(item.getJobName())) {
                        throw new IllegalStateException("模拟目标任务写入失败");
                    }
                    boolean duplicated = itemStore.values().stream()
                        .anyMatch(exist -> exist.getItemNo() != null && exist.getItemNo().equals(item.getItemNo()));
                    if (duplicated) {
                        throw new DuplicateKeyException("uk_hr_recruit_plan_item_no");
                    }
                    item.setItemId(idSequence.incrementAndGet());
                    itemStore.put(item.getItemId(), item);
                    yield 1;
                }
                case "update" -> {
                    applyItemUpdate(args);
                    yield 1;
                }
                case "updateById" -> 1;
                case "selectCount" -> 0L;
                case "toString" -> "RecruitPlanItemMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造结转记录 Mapper 替身（模拟 {@code (source_item_id, target_month)} 唯一索引）。
     *
     * @return Mapper 替身
     */
    private RecruitPlanRolloverMapper rolloverMapperStub() {
        return (RecruitPlanRolloverMapper) Proxy.newProxyInstance(
            RecruitPlanRolloverMapper.class.getClassLoader(),
            new Class<?>[]{RecruitPlanRolloverMapper.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "insert" -> {
                    RecruitPlanRollover record = (RecruitPlanRollover) args[0];
                    boolean duplicated = rolloverStore.values().stream()
                        .anyMatch(exist -> exist.getSourceItemId().equals(record.getSourceItemId())
                            && exist.getTargetMonth().equals(record.getTargetMonth()));
                    if (duplicated) {
                        throw new DuplicateKeyException("uk_hr_recruit_plan_rollover");
                    }
                    record.setRolloverId(idSequence.incrementAndGet());
                    rolloverStore.put(record.getRolloverId(), record);
                    yield 1;
                }
                case "update" -> {
                    applyRolloverUpdate(args);
                    yield 1;
                }
                case "updateById" -> 1;
                case "selectCount" -> 0L;
                case "toString" -> "RecruitPlanRolloverMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            });
    }

    /**
     * 构造计划任务状态服务替身（结转后重算汇总不在本测试范围内）。
     *
     * @return 服务替身
     */
    private IPlanItemStatusService statusServiceStub() {
        return (IPlanItemStatusService) Proxy.newProxyInstance(
            IPlanItemStatusService.class.getClassLoader(),
            new Class<?>[]{IPlanItemStatusService.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "toString" -> "PlanItemStatusServiceStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });
    }

    /**
     * 把计划任务的更新语句回放到内存数据（只处理本测试用到的字段）。
     *
     * @param args 动态代理参数（第一个为实体，第二个为条件包装器）
     */
    private void applyItemUpdate(Object[] args) {
        if (args == null || args.length < 2 || !(args[1] instanceof LambdaUpdateWrapper<?> update)) {
            return;
        }
        Map<String, Object> conditions = resolveEqConditions(update);
        Object itemId = conditions.get("item_id");
        if (itemId == null) {
            return;
        }
        RecruitPlanItem item = itemStore.get(Long.valueOf(String.valueOf(itemId)));
        if (item == null) {
            return;
        }
        Map<String, Object> values = resolveSetValues(update);
        if (values.containsKey("completion_status")) {
            item.setCompletionStatus(asString(values.get("completion_status")));
        }
        if (values.containsKey("last_refresh_time")) {
            item.setLastRefreshTime((LocalDateTime) values.get("last_refresh_time"));
        }
        if (values.containsKey("remaining_qty")) {
            item.setRemainingQty(asInteger(values.get("remaining_qty")));
        }
        if (values.containsKey("credited_arrival_qty")) {
            item.setCreditedArrivalQty(asInteger(values.get("credited_arrival_qty")));
        }
        if (values.containsKey("plan_qty")) {
            item.setPlanQty(asInteger(values.get("plan_qty")));
        }
    }

    /**
     * 把结转记录的更新语句回放到内存数据。
     *
     * @param args 动态代理参数（第一个为实体，第二个为条件包装器）
     */
    private void applyRolloverUpdate(Object[] args) {
        if (args == null || args.length < 2 || !(args[1] instanceof LambdaUpdateWrapper<?> update)) {
            return;
        }
        Map<String, Object> conditions = resolveEqConditions(update);
        RecruitPlanRollover record = null;
        if (conditions.get("rollover_id") != null) {
            record = rolloverStore.get(Long.valueOf(String.valueOf(conditions.get("rollover_id"))));
        } else if (conditions.get("source_item_id") != null && conditions.get("target_month") != null) {
            Long sourceItemId = Long.valueOf(String.valueOf(conditions.get("source_item_id")));
            String targetMonth = String.valueOf(conditions.get("target_month"));
            record = rolloverStore.values().stream()
                .filter(exist -> sourceItemId.equals(exist.getSourceItemId()))
                .filter(exist -> targetMonth.equals(exist.getTargetMonth()))
                .findFirst().orElse(null);
        }
        if (record == null) {
            return;
        }
        Map<String, Object> values = resolveSetValues(update);
        if (values.containsKey("result")) {
            record.setResult(asString(values.get("result")));
        }
        if (values.containsKey("failure_reason")) {
            record.setFailureReason(asString(values.get("failure_reason")));
        }
        if (values.containsKey("target_item_id")) {
            record.setTargetItemId(asLong(values.get("target_item_id")));
        }
        if (values.containsKey("retry_count")) {
            record.setRetryCount(asInteger(values.get("retry_count")));
        }
        if (values.containsKey("carryover_qty")) {
            record.setCarryoverQty(asInteger(values.get("carryover_qty")));
        }
    }

    /**
     * 解析更新语句中的等值条件（列名 → 参数值）。
     *
     * @param update 更新包装器
     * @return 条件映射
     */
    private Map<String, Object> resolveEqConditions(LambdaUpdateWrapper<?> update) {
        return resolve(update.getSqlSegment(), update.getParamNameValuePairs());
    }

    /**
     * 解析更新语句中的 SET 字段（列名 → 参数值）。
     *
     * @param update 更新包装器
     * @return 字段映射
     */
    private Map<String, Object> resolveSetValues(LambdaUpdateWrapper<?> update) {
        return resolve(update.getSqlSet(), update.getParamNameValuePairs());
    }

    /**
     * 按 {@code 列名 = #{ew.paramNameValuePairs.键}} 结构解析片段。
     *
     * @param sql    片段
     * @param params 参数表
     * @return 解析结果
     */
    private Map<String, Object> resolve(String sql, Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (sql == null || params == null) {
            return result;
        }
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        while (matcher.find()) {
            result.put(matcher.group(1), params.get(matcher.group(2)));
        }
        return result;
    }

    /**
     * 转字符串。
     *
     * @param value 原值
     * @return 字符串
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 转整数。
     *
     * @param value 原值
     * @return 整数
     */
    private Integer asInteger(Object value) {
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    /**
     * 转长整数。
     *
     * @param value 原值
     * @return 长整数
     */
    private Long asLong(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    /**
     * 取返回类型的默认值。
     *
     * @param type 返回类型
     * @return 默认值
     */
    private Object defaultValue(Class<?> type) {
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

}
