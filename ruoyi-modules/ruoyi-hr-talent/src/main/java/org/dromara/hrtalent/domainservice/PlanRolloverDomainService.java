package org.dromara.hrtalent.domainservice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.hrtalent.domain.entity.RecruitPlan;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
import org.dromara.hrtalent.domain.entity.RecruitPlanRollover;
import org.dromara.hrtalent.enums.PlanCompletionStatusEnum;
import org.dromara.hrtalent.enums.PlanControlStatusEnum;
import org.dromara.hrtalent.enums.PlanExecutionStatusEnum;
import org.dromara.hrtalent.enums.PlanSourceTypeEnum;
import org.dromara.hrtalent.enums.PlanStatusEnum;
import org.dromara.hrtalent.enums.UrgencyEnum;
import org.dromara.hrtalent.mapper.RecruitPlanItemMapper;
import org.dromara.hrtalent.mapper.RecruitPlanMapper;
import org.dromara.hrtalent.mapper.RecruitPlanRolloverMapper;
import org.dromara.hrtalent.service.recruitment.IPlanItemStatusService;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 月度结转领域服务（SPEC-P2 §4.3 / 设计文档 §7.1.2、§9.6、§21.5、§21.15）。
 *
 * <p><b>职责</b>：计算剩余人数、判定结转资格、创建下月独立任务、维护结转链与幂等记录。
 * <b>禁止事项</b>：不修改原任务的 {@code plan_qty} 与原月份，不合并同公司 / 同部门 / 同岗位任务。</p>
 *
 * <p><b>幂等设计（硬要求）</b>：唯一索引
 * {@code uk_hr_recruit_plan_rollover(source_item_id, target_month)} 是唯一权威闸门。
 * 单条任务的处理顺序为「<b>先占位写入结转记录 → 再创建下月任务 → 最后回填结果</b>」：</p>
 * <ol>
 *     <li>占位插入 {@code result = processing}；并发或重复执行时命中唯一索引，直接判定为已结转并安全跳过。</li>
 *     <li>创建目标月份任务（{@code source_type = carryover}，人数取原任务剩余人数）。</li>
 *     <li>回填 {@code target_item_id} 与 {@code result = success}，并把原任务置为 {@code rolled_over}。</li>
 * </ol>
 *
 * <p><b>事务与失败隔离</b>：每条来源任务在<b>独立事务</b>（{@code REQUIRES_NEW}）中处理，
 * 单条失败由 {@link #recordFailure} 另起事务记录原因（{@code result = failed} + {@code failure_reason}），
 * 绝不回滚整批已成功的条目，也绝不重复生成下月任务（SPEC-P2 §4.3.6、设计文档 §9.6）。</p>
 *
 * <p><b>批次口径</b>：P1 DDL 未提供独立批次表，批次以 {@code batch_id}（本次执行唯一ID）与
 * {@code batch_no}（可读批次号）落在每条结转记录上，「先建批次再逐条执行」体现为
 * 先生成本次执行的批次标识并创建目标月表头，再逐条执行并记录结果。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanRolloverDomainService {

    /**
     * 结转执行结果：处理中（占位记录，用于幂等闸门）。
     */
    public static final String RESULT_PROCESSING = "processing";

    /**
     * 结转执行结果：成功。
     */
    public static final String RESULT_SUCCESS = "success";

    /**
     * 结转执行结果：失败（已记录原因，可安全重试）。
     */
    public static final String RESULT_FAILED = "failed";

    /**
     * 结转执行结果：跳过（不落库，仅出现在执行明细中）。
     */
    public static final String RESULT_SKIPPED = "skipped";

    /**
     * 是否允许自动结转：是。
     */
    private static final String CARRYOVER_ENABLED = "1";

    /**
     * 失败原因落库最大长度（与 DDL {@code failure_reason varchar(500)} 一致）。
     */
    private static final int FAILURE_REASON_MAX_LENGTH = 500;

    /**
     * 备注落库最大长度（与 DDL {@code remark varchar(500)} 一致）。
     */
    private static final int REMARK_MAX_LENGTH = 500;

    /**
     * 计划任务编号生成冲突时的最大重试次数。
     */
    private static final int ITEM_NO_MAX_RETRY = 2;

    /**
     * 公司月度计划表头 Mapper。
     */
    private final RecruitPlanMapper recruitPlanMapper;

    /**
     * 月度计划任务 Mapper。
     */
    private final RecruitPlanItemMapper recruitPlanItemMapper;

    /**
     * 月度结转记录 Mapper。
     */
    private final RecruitPlanRolloverMapper recruitPlanRolloverMapper;

    /**
     * 业务编号生成器（计划任务编号、批次号）。
     */
    private final RecruitBusinessNoGenerator businessNoGenerator;

    /**
     * 计划任务状态领域服务（纯计算）。
     */
    private final PlanItemStatusDomainService planItemStatusDomainService;

    /**
     * 计划任务状态服务（用于批次结束后重算表头汇总人数）。
     */
    private final IPlanItemStatusService planItemStatusService;

    /**
     * 自身代理引用：让 {@code REQUIRES_NEW} 的单条事务真正生效（避免自调用绕过事务代理）。
     */
    private final ObjectProvider<PlanRolloverDomainService> selfProvider;

    /* ------------------------------------------------------------------ 纯计算 ------------------------------------------------------------------ */

    /**
     * 取自身引用；单元测试未注入容器时回落到 {@code this}（此时事务注解不生效，行为等价）。
     *
     * @return 目标调用对象
     */
    private PlanRolloverDomainService self() {
        if (selfProvider == null) {
            return this;
        }
        PlanRolloverDomainService bean = selfProvider.getIfAvailable();
        return bean == null ? this : bean;
    }

    /**
     * 解析 {@code yyyy-MM} 月份。
     *
     * @param month     月份文本
     * @param fieldName 字段中文名（用于错误提示）
     * @return 年月对象
     * @throws ServiceException 为空或格式非法
     */
    public YearMonth parseMonth(String month, String fieldName) {
        if (StringUtils.isBlank(month)) {
            throw new ServiceException(fieldName + "不能为空");
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new ServiceException(fieldName + "格式必须为 yyyy-MM");
        }
    }

    /**
     * 校验结转月份区间：目标月份必须严格晚于来源月份。
     *
     * @param sourceMonth 来源月份（yyyy-MM）
     * @param targetMonth 目标月份（yyyy-MM）
     */
    public void validateMonthRange(String sourceMonth, String targetMonth) {
        YearMonth source = parseMonth(sourceMonth, "来源月份");
        YearMonth target = parseMonth(targetMonth, "目标月份");
        if (!target.isAfter(source)) {
            throw new ServiceException("目标月份必须晚于来源月份");
        }
    }

    /**
     * 计算原任务剩余人数（结转数量口径）。
     *
     * @param source 来源计划任务
     * @return 剩余人数，恒 &gt;= 0
     */
    public int remainingQty(RecruitPlanItem source) {
        if (source == null) {
            return 0;
        }
        return planItemStatusDomainService.resolveRemainingQty(source.getPlanQty(), source.getCreditedArrivalQty());
    }

    /**
     * 判定结转资格并给出不符合原因。
     * <p>满足全部条件才可生成下月任务：未取消、未完成、{@code remaining_qty > 0}、
     * {@code carryover_enabled = 1}；「该来源任务是否已生成目标月份结转任务」由幂等闸门单独判定。</p>
     *
     * @param source 来源计划任务
     * @return 不符合原因；返回 {@code null} 表示符合结转条件
     */
    public String ineligibleReason(RecruitPlanItem source) {
        if (source == null) {
            return "来源任务不存在或已删除";
        }
        if (PlanControlStatusEnum.CANCELLED.getCode().equals(source.getControlStatus())) {
            return "任务已取消，不参与自动结转";
        }
        if (!CARRYOVER_ENABLED.equals(source.getCarryoverEnabled())) {
            return "任务已关闭自动结转（carryover_enabled=0）";
        }
        if (PlanCompletionStatusEnum.COMPLETED.getCode().equals(source.getCompletionStatus())) {
            return "任务已完成，无剩余人数";
        }
        int remaining = remainingQty(source);
        if (remaining <= 0) {
            return "剩余人数为 0，无需结转";
        }
        return null;
    }

    /**
     * 构造下月结转任务（纯内存对象，调用方负责插入）。
     *
     * <p>复制公司、部门、岗位、负责人、紧急程度、招聘期限标准天数与结转开关；
     * {@code plan_qty} 取原任务剩余人数；{@code source_type = carryover}；
     * {@code previous_plan_item_id = 原任务 id}；{@code root_plan_item_id = 原任务 root ?? 原任务 id}。</p>
     *
     * <p><b>不复制</b> {@code demand_id} 与 {@code job_id}：需求与岗位执行项都属于原月上下文，
     * 岗位执行项（{@code hr_recruit_job.plan_item_id}）与计划任务是多对一关系，复制会产生一条
     * 跨月共享的岗位记录，与「每条计划任务独立跟进」冲突，故由岗位域在 P3 按需新建。</p>
     *
     * @param source        来源计划任务
     * @param targetPlanId  目标月计划表头ID
     * @param targetMonth   目标月份（yyyy-MM）
     * @param itemNo        新任务编号
     * @param batchId       结转批次ID
     * @param now           执行时间
     * @return 待插入的下月任务
     */
    public RecruitPlanItem buildTargetItem(RecruitPlanItem source,
                                           Long targetPlanId,
                                           String targetMonth,
                                           String itemNo,
                                           Long batchId,
                                           LocalDateTime now) {
        int carryoverQty = remainingQty(source);
        RecruitPlanItem target = new RecruitPlanItem();
        target.setItemNo(itemNo);
        target.setPlanId(targetPlanId);
        target.setCompanyDeptId(source.getCompanyDeptId());
        target.setCompanyName(source.getCompanyName());
        target.setUseDeptId(source.getUseDeptId());
        target.setUseDeptName(source.getUseDeptName());
        target.setJobName(source.getJobName());
        target.setPlanMonth(targetMonth);
        target.setSourceType(PlanSourceTypeEnum.CARRYOVER.getCode());
        target.setPlanQty(carryoverQty);
        target.setCreditedArrivalQty(0);
        target.setRemainingQty(carryoverQty);
        target.setControlStatus(PlanControlStatusEnum.NORMAL.getCode());
        target.setControlReason(null);
        target.setExecutionStatus(PlanExecutionStatusEnum.PENDING.getCode());
        target.setCompletionStatus(PlanCompletionStatusEnum.UNFINISHED.getCode());
        target.setLastRefreshTime(now);
        target.setCarryoverEnabled(source.getCarryoverEnabled() == null ? CARRYOVER_ENABLED : source.getCarryoverEnabled());
        target.setPreviousPlanItemId(source.getItemId());
        target.setRootPlanItemId(source.getRootPlanItemId() == null ? source.getItemId() : source.getRootPlanItemId());
        target.setCarryoverBatchId(batchId);
        target.setCarryoverTime(now);
        target.setOwnerId(source.getOwnerId());
        target.setUrgency(StringUtils.isBlank(source.getUrgency())
            ? UrgencyEnum.NORMAL.getCode() : source.getUrgency());
        target.setStandardDays(source.getStandardDays());
        target.setRemark(truncate("由 " + source.getPlanMonth() + " 计划任务 " + source.getItemNo() + " 结转生成",
            REMARK_MAX_LENGTH));
        return target;
    }

    /* ------------------------------------------------------------------ 数据读取 ------------------------------------------------------------------ */

    /**
     * 扫描符合来源月份（可选公司）的任务。
     *
     * @param companyDeptId 公司（平台部门）ID，可为空表示不限定
     * @param sourceMonth   来源月份（yyyy-MM）
     * @param sourceItemIds 指定任务ID集合，为空表示全量
     * @return 来源任务列表（按公司、任务ID升序）
     */
    public List<RecruitPlanItem> listSourceItems(Long companyDeptId, String sourceMonth, List<Long> sourceItemIds) {
        LambdaQueryWrapper<RecruitPlanItem> wrapper = new LambdaQueryWrapper<RecruitPlanItem>()
            .eq(RecruitPlanItem::getPlanMonth, sourceMonth)
            .eq(companyDeptId != null, RecruitPlanItem::getCompanyDeptId, companyDeptId)
            .in(CollUtil.isNotEmpty(sourceItemIds), RecruitPlanItem::getItemId, sourceItemIds)
            .orderByAsc(RecruitPlanItem::getCompanyDeptId)
            .orderByAsc(RecruitPlanItem::getItemId);
        return recruitPlanItemMapper.selectList(wrapper);
    }

    /**
     * 查询幂等记录：某来源任务对目标月份是否已有结转记录。
     *
     * @param sourceItemId 来源任务ID
     * @param targetMonth  目标月份（yyyy-MM）
     * @return 结转记录，不存在返回 null
     */
    public RecruitPlanRollover findRollover(Long sourceItemId, String targetMonth) {
        if (sourceItemId == null || StringUtils.isBlank(targetMonth)) {
            return null;
        }
        return recruitPlanRolloverMapper.selectOne(new LambdaQueryWrapper<RecruitPlanRollover>()
            .eq(RecruitPlanRollover::getSourceItemId, sourceItemId)
            .eq(RecruitPlanRollover::getTargetMonth, targetMonth));
    }

    /**
     * 按批次号查询结转明细。
     *
     * @param batchNo 批次号
     * @return 结转明细列表（按记录ID升序）
     */
    public List<RecruitPlanRollover> listBatch(String batchNo) {
        if (StringUtils.isBlank(batchNo)) {
            return List.of();
        }
        return recruitPlanRolloverMapper.selectList(new LambdaQueryWrapper<RecruitPlanRollover>()
            .eq(RecruitPlanRollover::getBatchNo, batchNo)
            .orderByAsc(RecruitPlanRollover::getRolloverId));
    }

    /**
     * 查询指定批次中执行失败（可重试）的记录。
     *
     * @param batchNo 批次号
     * @return 失败记录列表
     */
    public List<RecruitPlanRollover> listFailedOfBatch(String batchNo) {
        if (StringUtils.isBlank(batchNo)) {
            return List.of();
        }
        return recruitPlanRolloverMapper.selectList(new LambdaQueryWrapper<RecruitPlanRollover>()
            .eq(RecruitPlanRollover::getBatchNo, batchNo)
            .ne(RecruitPlanRollover::getResult, RESULT_SUCCESS)
            .orderByAsc(RecruitPlanRollover::getRolloverId));
    }

    /* ------------------------------------------------------------------ 事务单元 ------------------------------------------------------------------ */

    /**
     * 确保目标月份存在计划表头，不存在则自动创建（设计文档 §7.1.2 第 1 条）。
     *
     * <p>独立事务提交：即使后续某条任务结转失败，目标月表头也保留，供其他来源任务复用。
     * 并发创建时命中唯一索引 {@code (company_dept_id, plan_month)} 并回查，保证只有一个表头。</p>
     *
     * @param companyDeptId 公司（平台部门）ID
     * @param companyName   公司名称快照
     * @param targetMonth   目标月份（yyyy-MM）
     * @return 目标月计划表头ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long ensureTargetPlan(Long companyDeptId, String companyName, String targetMonth) {
        YearMonth month = parseMonth(targetMonth, "目标月份");
        RecruitPlan exist = findPlan(companyDeptId, targetMonth);
        if (exist != null) {
            return exist.getPlanId();
        }
        RecruitPlan plan = new RecruitPlan();
        plan.setPlanNo(businessNoGenerator.nextPlanNo(month));
        plan.setCompanyDeptId(companyDeptId);
        plan.setCompanyName(companyName);
        plan.setPlanMonth(targetMonth);
        // 自动结转生成的下月表头直接进入执行中，等待人工确认的草稿语义不适用于系统生成
        plan.setStatus(PlanStatusEnum.EXECUTING.getCode());
        plan.setGeneratedFlag("0");
        plan.setTotalPlanQty(0);
        plan.setTotalCreditedQty(0);
        plan.setTotalRemainingQty(0);
        plan.setRemark("月度结转自动创建");
        try {
            recruitPlanMapper.insert(plan);
            log.info("结转自动创建目标月计划表头, planId={}, companyDeptId={}, planMonth={}",
                plan.getPlanId(), companyDeptId, targetMonth);
            return plan.getPlanId();
        } catch (DuplicateKeyException e) {
            // 并发创建：回查已存在的表头
            RecruitPlan again = findPlan(companyDeptId, targetMonth);
            if (again == null) {
                throw new ServiceException("目标月份计划表头创建失败，请重试");
            }
            return again.getPlanId();
        }
    }

    /**
     * 结转<b>单条</b>来源任务（独立事务，失败只影响本条）。
     *
     * @param sourceItemId  来源任务ID
     * @param targetPlanId  目标月计划表头ID
     * @param targetMonth   目标月份（yyyy-MM）
     * @param batchId       结转批次ID
     * @param batchNo       结转批次号
     * @param operatorId    操作人用户ID
     * @return 单条执行结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public RolloverItemOutcome rolloverSingleItem(Long sourceItemId,
                                                  Long targetPlanId,
                                                  String targetMonth,
                                                  Long batchId,
                                                  String batchNo,
                                                  Long operatorId) {
        RecruitPlanItem source = recruitPlanItemMapper.selectById(sourceItemId);
        if (source == null) {
            return RolloverItemOutcome.skipped(sourceItemId, "来源任务不存在或已删除");
        }
        RecruitPlanRollover guard = findRollover(sourceItemId, targetMonth);
        if (guard != null && RESULT_SUCCESS.equals(guard.getResult())) {
            // 幂等：同一来源任务对同一目标月份已生成过结转任务
            return RolloverItemOutcome.skipped(sourceItemId, batchId, batchNo,
                guard.getTargetItemId(), "同一来源任务已生成目标月份结转任务");
        }
        String reason = ineligibleReason(source);
        if (reason != null) {
            return RolloverItemOutcome.skipped(sourceItemId, batchId, batchNo, null, reason);
        }
        int carryoverQty = remainingQty(source);
        if (guard == null) {
            guard = insertGuard(source, targetMonth, batchId, batchNo, carryoverQty, operatorId);
            if (guard == null) {
                // 占位插入命中唯一索引：并发执行已抢先，安全跳过
                return RolloverItemOutcome.skipped(sourceItemId, batchId, batchNo, null,
                    "同一来源任务已生成目标月份结转任务（并发执行）");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        Long targetItemId = guard.getTargetItemId();
        if (targetItemId == null) {
            RecruitPlanItem target = buildTargetItem(source, targetPlanId, targetMonth, null, batchId, now);
            targetItemId = insertTargetItem(target, targetMonth);
            // 回填幂等记录
            LambdaUpdateWrapper<RecruitPlanRollover> guardUpdate = new LambdaUpdateWrapper<RecruitPlanRollover>()
                .eq(RecruitPlanRollover::getRolloverId, guard.getRolloverId())
                .set(RecruitPlanRollover::getTargetItemId, targetItemId)
                .set(RecruitPlanRollover::getResult, RESULT_SUCCESS)
                .set(RecruitPlanRollover::getFailureReason, null)
                .set(RecruitPlanRollover::getCarryoverQty, carryoverQty)
                .set(RecruitPlanRollover::getExecuteTime, now)
                .set(RecruitPlanRollover::getBatchId, batchId)
                .set(RecruitPlanRollover::getBatchNo, batchNo)
                .set(RecruitPlanRollover::getOperatorId, operatorId);
            recruitPlanRolloverMapper.update(null, guardUpdate);
        }
        // 原任务置为已结转：只改完成度与刷新时间，绝不修改 plan_qty 与原月份
        recruitPlanItemMapper.update(null, new LambdaUpdateWrapper<RecruitPlanItem>()
            .eq(RecruitPlanItem::getItemId, sourceItemId)
            .set(RecruitPlanItem::getCompletionStatus, PlanCompletionStatusEnum.ROLLED_OVER.getCode())
            .set(RecruitPlanItem::getLastRefreshTime, now));
        log.info("月度结转成功, sourceItemId={}, targetItemId={}, targetMonth={}, carryoverQty={}, batchNo={}",
            sourceItemId, targetItemId, targetMonth, carryoverQty, batchNo);
        return RolloverItemOutcome.success(sourceItemId, batchId, batchNo, targetItemId, carryoverQty);
    }

    /**
     * 记录单条失败（独立事务，保证失败原因一定落库并允许安全重试）。
     *
     * @param sourceItemId 来源任务ID
     * @param sourceMonth  来源月份（yyyy-MM）
     * @param targetMonth  目标月份（yyyy-MM）
     * @param batchId      结转批次ID
     * @param batchNo      结转批次号
     * @param reason       失败原因
     * @param operatorId   操作人用户ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailure(Long sourceItemId,
                              String sourceMonth,
                              String targetMonth,
                              Long batchId,
                              String batchNo,
                              String reason,
                              Long operatorId) {
        String failureReason = truncate(reason, FAILURE_REASON_MAX_LENGTH);
        LocalDateTime now = LocalDateTime.now();
        RecruitPlanRollover exist = findRollover(sourceItemId, targetMonth);
        if (exist == null) {
            RecruitPlanItem source = recruitPlanItemMapper.selectById(sourceItemId);
            RecruitPlanRollover record = new RecruitPlanRollover();
            record.setSourceItemId(sourceItemId);
            record.setSourceMonth(sourceMonth);
            record.setTargetMonth(targetMonth);
            record.setCarryoverQty(source == null ? 0 : remainingQty(source));
            record.setBatchId(batchId);
            record.setBatchNo(batchNo);
            record.setExecuteTime(now);
            record.setResult(RESULT_FAILED);
            record.setFailureReason(failureReason);
            record.setRetryCount(1);
            record.setOperatorId(operatorId);
            try {
                recruitPlanRolloverMapper.insert(record);
            } catch (DuplicateKeyException e) {
                // 并发写入同一幂等键：改为更新
                updateFailure(sourceItemId, targetMonth, failureReason, now, batchId, batchNo, operatorId);
            }
        } else {
            updateFailure(sourceItemId, targetMonth, failureReason, now, batchId, batchNo, operatorId);
        }
    }

    /**
     * 标记目标月表头已生成计划任务。
     *
     * @param planId 目标月计划表头ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markPlanGenerated(Long planId) {
        if (planId == null) {
            return;
        }
        recruitPlanMapper.update(null, new LambdaUpdateWrapper<RecruitPlan>()
            .eq(RecruitPlan::getPlanId, planId)
            .set(RecruitPlan::getGeneratedFlag, "1")
            .set(RecruitPlan::getGeneratedTime, LocalDateTime.now()));
    }

    /* ------------------------------------------------------------------ 批次编排 ------------------------------------------------------------------ */

    /**
     * 执行一次月度结转（按公司 + 来源月份扫描，逐条独立事务）。
     *
     * <p>该方法<b>本身不在事务中</b>：批次先确定批次标识与目标月表头，再逐条执行；
     * 单条失败只记录该条原因，不影响其它条目。</p>
     *
     * @param companyDeptId 公司（平台部门）ID，可为空表示不限定
     * @param sourceMonth   来源月份（yyyy-MM）
     * @param targetMonth   目标月份（yyyy-MM）
     * @param sourceItemIds 指定来源任务ID集合，为空表示全量
     * @param operatorId    操作人用户ID
     * @return 批次执行结果
     */
    public RolloverBatchOutcome executeBatch(Long companyDeptId,
                                             String sourceMonth,
                                             String targetMonth,
                                             List<Long> sourceItemIds,
                                             Long operatorId) {
        validateMonthRange(sourceMonth, targetMonth);
        // 1. 先建批次：批次号 + 批次ID + 目标月表头，之后才逐条执行
        long batchId = IdUtil.getSnowflakeNextId();
        String batchNo = businessNoGenerator.nextRolloverBatchNo(parseMonth(targetMonth, "目标月份"));
        List<RecruitPlanItem> sources = listSourceItems(companyDeptId, sourceMonth, sourceItemIds);
        Map<Long, Long> targetPlanIds = ensureTargetPlans(sources, targetMonth);
        // 2. 逐条执行，单条失败单独记录
        List<RolloverItemOutcome> outcomes = new ArrayList<>();
        for (RecruitPlanItem source : sources) {
            outcomes.add(executeOne(source, targetPlanIds.get(source.getCompanyDeptId()), targetMonth,
                batchId, batchNo, operatorId));
        }
        // 3. 批次结束：回写表头标记并重算受影响表头的汇总人数
        for (Long planId : targetPlanIds.values()) {
            markPlanGenerated(planId);
        }
        RolloverBatchOutcome outcome = RolloverBatchOutcome.of(batchId, batchNo, sourceMonth, targetMonth,
            sources.size(), outcomes, targetPlanIds, sourcePlanIds(sources));
        for (Long planId : outcome.affectedPlanIds()) {
            refreshTotalsQuietly(planId);
        }
        log.info("月度结转批次完成, batchNo={}, sourceMonth={}, targetMonth={}, 总数={}, 成功={}, 跳过={}, 失败={}",
            batchNo, sourceMonth, targetMonth, outcome.totalItemCount(), outcome.successCount(),
            outcome.skippedCount(), outcome.failedCount());
        return outcome;
    }

    /**
     * 重试指定批次中执行失败的条目（幂等：已成功的条目不会被重复生成）。
     *
     * @param batchNo    批次号
     * @param operatorId 操作人用户ID
     * @return 批次执行结果
     */
    public RolloverBatchOutcome retryBatch(String batchNo, Long operatorId) {
        if (StringUtils.isBlank(batchNo)) {
            throw new ServiceException("结转批次号不能为空");
        }
        List<RecruitPlanRollover> failed = listFailedOfBatch(batchNo);
        if (CollUtil.isEmpty(failed)) {
            throw new ServiceException("该批次没有需要重试的结转记录");
        }
        List<RolloverItemOutcome> outcomes = new ArrayList<>();
        Map<Long, Long> targetPlanIds = new LinkedHashMap<>();
        for (RecruitPlanRollover record : failed) {
            RecruitPlanItem source = recruitPlanItemMapper.selectById(record.getSourceItemId());
            if (source == null) {
                outcomes.add(RolloverItemOutcome.skipped(record.getSourceItemId(),
                    "来源任务不存在或已删除"));
                continue;
            }
            Long targetPlanId = targetPlanIds.computeIfAbsent(source.getCompanyDeptId(),
                key -> ensureTargetPlan(key, source.getCompanyName(), record.getTargetMonth()));
            outcomes.add(executeOne(source, targetPlanId, record.getTargetMonth(),
                record.getBatchId(), batchNo, operatorId));
        }
        RolloverBatchOutcome outcome = RolloverBatchOutcome.of(
            failed.get(0).getBatchId(), batchNo,
            failed.get(0).getSourceMonth(), failed.get(0).getTargetMonth(),
            failed.size(), outcomes, targetPlanIds, List.of());
        for (Long planId : outcome.affectedPlanIds()) {
            refreshTotalsQuietly(planId);
        }
        log.info("月度结转重试完成, batchNo={}, 重试={}, 成功={}, 跳过={}, 失败={}",
            batchNo, outcome.totalItemCount(), outcome.successCount(), outcome.skippedCount(), outcome.failedCount());
        return outcome;
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 逐条执行并捕获异常，保证失败不会中断整批。
     *
     * @param source       来源任务
     * @param targetPlanId 目标月表头ID
     * @param targetMonth  目标月份
     * @param batchId      批次ID
     * @param batchNo      批次号
     * @param operatorId   操作人
     * @return 单条结果
     */
    private RolloverItemOutcome executeOne(RecruitPlanItem source,
                                           Long targetPlanId,
                                           String targetMonth,
                                           Long batchId,
                                           String batchNo,
                                           Long operatorId) {
        try {
            return self().rolloverSingleItem(source.getItemId(), targetPlanId, targetMonth,
                batchId, batchNo, operatorId);
        } catch (Exception e) {
            String reason = StringUtils.isBlank(e.getMessage()) ? e.getClass().getSimpleName() : e.getMessage();
            log.error("月度结转单条失败, sourceItemId={}, targetMonth={}, batchNo={}, exception={}",
                source.getItemId(), targetMonth, batchNo, e.getClass().getSimpleName());
            try {
                self().recordFailure(source.getItemId(), source.getPlanMonth(), targetMonth,
                    batchId, batchNo, reason, operatorId);
            } catch (Exception recordError) {
                log.error("月度结转失败原因记录失败, sourceItemId={}, exception={}",
                    source.getItemId(), recordError.getClass().getSimpleName());
            }
            return RolloverItemOutcome.failed(source.getItemId(), batchId, batchNo, reason);
        }
    }

    /**
     * 为涉及的公司逐一确保目标月表头存在。
     *
     * @param sources     来源任务列表
     * @param targetMonth 目标月份
     * @return 公司ID → 目标表头ID
     */
    private Map<Long, Long> ensureTargetPlans(List<RecruitPlanItem> sources, String targetMonth) {
        Map<Long, Long> planIds = new LinkedHashMap<>();
        for (RecruitPlanItem source : sources) {
            if (source.getCompanyDeptId() == null) {
                continue;
            }
            planIds.computeIfAbsent(source.getCompanyDeptId(),
                key -> self().ensureTargetPlan(key, source.getCompanyName(), targetMonth));
        }
        return planIds;
    }

    /**
     * 提取来源任务所属的计划表头ID集合。
     *
     * @param sources 来源任务列表
     * @return 计划表头ID集合（去重）
     */
    private List<Long> sourcePlanIds(List<RecruitPlanItem> sources) {
        Map<Long, Long> ids = new LinkedHashMap<>();
        for (RecruitPlanItem source : sources) {
            if (source.getPlanId() != null) {
                ids.put(source.getPlanId(), source.getPlanId());
            }
        }
        return new ArrayList<>(ids.values());
    }

    /**
     * 回查公司 + 月份的计划表头。
     *
     * @param companyDeptId 公司（平台部门）ID
     * @param planMonth     计划月份
     * @return 计划表头，不存在返回 null
     */
    public RecruitPlan findPlan(Long companyDeptId, String planMonth) {
        return recruitPlanMapper.selectOne(new LambdaQueryWrapper<RecruitPlan>()
            .eq(RecruitPlan::getCompanyDeptId, companyDeptId)
            .eq(RecruitPlan::getPlanMonth, planMonth));
    }

    /**
     * 占位插入幂等记录；命中唯一索引时返回 null（表示并发或重复执行）。
     *
     * @param source        来源任务
     * @param targetMonth   目标月份
     * @param batchId       批次ID
     * @param batchNo       批次号
     * @param carryoverQty  结转人数
     * @param operatorId    操作人
     * @return 已落库的占位记录；命中唯一索引时返回 null
     */
    private RecruitPlanRollover insertGuard(RecruitPlanItem source,
                                            String targetMonth,
                                            Long batchId,
                                            String batchNo,
                                            int carryoverQty,
                                            Long operatorId) {
        RecruitPlanRollover record = new RecruitPlanRollover();
        record.setSourceItemId(source.getItemId());
        record.setSourceMonth(source.getPlanMonth());
        record.setTargetMonth(targetMonth);
        record.setCarryoverQty(carryoverQty);
        record.setBatchId(batchId);
        record.setBatchNo(batchNo);
        record.setExecuteTime(LocalDateTime.now());
        record.setResult(RESULT_PROCESSING);
        record.setRetryCount(0);
        record.setOperatorId(operatorId);
        try {
            recruitPlanRolloverMapper.insert(record);
            return record;
        } catch (DuplicateKeyException e) {
            log.info("月度结转命中幂等唯一索引, sourceItemId={}, targetMonth={}", source.getItemId(), targetMonth);
            return null;
        }
    }

    /**
     * 插入目标任务，业务编号冲突时重试。
     *
     * @param target      目标任务（{@code itemNo} 为空时生成）
     * @param targetMonth 目标月份
     * @return 目标任务ID
     */
    private Long insertTargetItem(RecruitPlanItem target, String targetMonth) {
        YearMonth month = parseMonth(targetMonth, "目标月份");
        for (int attempt = 0; attempt < ITEM_NO_MAX_RETRY; attempt++) {
            if (StringUtils.isBlank(target.getItemNo())) {
                target.setItemNo(businessNoGenerator.nextPlanItemNo(month));
            }
            target.setItemId(null);
            try {
                recruitPlanItemMapper.insert(target);
                return target.getItemId();
            } catch (DuplicateKeyException e) {
                log.warn("计划任务编号生成冲突, itemNo={}, attempt={}", target.getItemNo(), attempt + 1);
                target.setItemNo(null);
            }
        }
        throw new ServiceException("计划任务编号生成冲突，请重试");
    }

    /**
     * 更新失败记录。
     *
     * @param sourceItemId  来源任务ID
     * @param targetMonth   目标月份
     * @param failureReason 失败原因
     * @param now           时间
     * @param batchId       批次ID
     * @param batchNo       批次号
     * @param operatorId    操作人
     */
    private void updateFailure(Long sourceItemId,
                               String targetMonth,
                               String failureReason,
                               LocalDateTime now,
                               Long batchId,
                               String batchNo,
                               Long operatorId) {
        RecruitPlanRollover exist = findRollover(sourceItemId, targetMonth);
        int retryCount = exist == null || exist.getRetryCount() == null ? 1 : exist.getRetryCount() + 1;
        recruitPlanRolloverMapper.update(null, new LambdaUpdateWrapper<RecruitPlanRollover>()
            .eq(RecruitPlanRollover::getSourceItemId, sourceItemId)
            .eq(RecruitPlanRollover::getTargetMonth, targetMonth)
            .set(RecruitPlanRollover::getResult, RESULT_FAILED)
            .set(RecruitPlanRollover::getFailureReason, failureReason)
            .set(RecruitPlanRollover::getRetryCount, retryCount)
            .set(RecruitPlanRollover::getExecuteTime, now)
            .set(RecruitPlanRollover::getBatchId, batchId)
            .set(RecruitPlanRollover::getBatchNo, batchNo)
            .set(RecruitPlanRollover::getOperatorId, operatorId));
    }

    /**
     * 重算表头汇总人数；失败只记日志，不影响结转批次结果。
     *
     * @param planId 计划表头ID
     */
    private void refreshTotalsQuietly(Long planId) {
        try {
            planItemStatusService.refreshPlanTotals(planId);
        } catch (Exception e) {
            log.warn("结转后重算计划表头汇总失败, planId={}, exception={}", planId, e.getClass().getSimpleName());
        }
    }

    /**
     * 截断超长文本，避免超出 DDL 列长度。
     *
     * @param text      原文
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    /* ------------------------------------------------------------------ 结果模型 ------------------------------------------------------------------ */

    /**
     * 单条来源任务的结转结果。
     *
     * @param result       执行结果（success/failed/skipped）
     * @param sourceItemId 来源任务ID
     * @param batchId      批次ID
     * @param batchNo      批次号
     * @param targetItemId 目标任务ID
     * @param carryoverQty 结转人数
     * @param message      说明（跳过原因或失败原因）
     * @author hr-talent
     */
    public record RolloverItemOutcome(String result,
                                      Long sourceItemId,
                                      Long batchId,
                                      String batchNo,
                                      Long targetItemId,
                                      Integer carryoverQty,
                                      String message) {

        /**
         * 构造成功结果。
         *
         * @param sourceItemId 来源任务ID
         * @param batchId      批次ID
         * @param batchNo      批次号
         * @param targetItemId 目标任务ID
         * @param carryoverQty 结转人数
         * @return 成功结果
         */
        public static RolloverItemOutcome success(Long sourceItemId, Long batchId, String batchNo,
                                                  Long targetItemId, int carryoverQty) {
            return new RolloverItemOutcome(RESULT_SUCCESS, sourceItemId, batchId, batchNo,
                targetItemId, carryoverQty, null);
        }

        /**
         * 构造跳过结果。
         *
         * @param sourceItemId 来源任务ID
         * @param message      跳过原因
         * @return 跳过结果
         */
        public static RolloverItemOutcome skipped(Long sourceItemId, String message) {
            return new RolloverItemOutcome(RESULT_SKIPPED, sourceItemId, null, null, null, 0, message);
        }

        /**
         * 构造带批次信息的跳过结果。
         *
         * @param sourceItemId 来源任务ID
         * @param batchId      批次ID
         * @param batchNo      批次号
         * @param targetItemId 已存在的目标任务ID（幂等命中时）
         * @param message      跳过原因
         * @return 跳过结果
         */
        public static RolloverItemOutcome skipped(Long sourceItemId, Long batchId, String batchNo,
                                                  Long targetItemId, String message) {
            return new RolloverItemOutcome(RESULT_SKIPPED, sourceItemId, batchId, batchNo,
                targetItemId, 0, message);
        }

        /**
         * 构造失败结果。
         *
         * @param sourceItemId 来源任务ID
         * @param batchId      批次ID
         * @param batchNo      批次号
         * @param message      失败原因
         * @return 失败结果
         */
        public static RolloverItemOutcome failed(Long sourceItemId, Long batchId, String batchNo, String message) {
            return new RolloverItemOutcome(RESULT_FAILED, sourceItemId, batchId, batchNo, null, 0, message);
        }

        /**
         * 是否成功。
         *
         * @return 是否成功
         */
        public boolean isSuccess() {
            return RESULT_SUCCESS.equals(result);
        }

        /**
         * 是否跳过。
         *
         * @return 是否跳过
         */
        public boolean isSkipped() {
            return RESULT_SKIPPED.equals(result);
        }

        /**
         * 是否失败。
         *
         * @return 是否失败
         */
        public boolean isFailed() {
            return RESULT_FAILED.equals(result);
        }
    }

    /**
     * 一次结转批次的执行结果。
     *
     * @param batchId         批次ID
     * @param batchNo         批次号
     * @param sourceMonth     来源月份
     * @param targetMonth     目标月份
     * @param totalItemCount  扫描到的来源任务总数
     * @param items           明细结果
     * @param targetPlanIds   公司ID → 目标月表头ID
     * @param sourcePlanIds   来源任务所属计划表头ID集合
     * @author hr-talent
     */
    public record RolloverBatchOutcome(Long batchId,
                                       String batchNo,
                                       String sourceMonth,
                                       String targetMonth,
                                       int totalItemCount,
                                       List<RolloverItemOutcome> items,
                                       Map<Long, Long> targetPlanIds,
                                       List<Long> sourcePlanIds) {

        /**
         * 构造批次结果。
         *
         * @param batchId        批次ID
         * @param batchNo        批次号
         * @param sourceMonth    来源月份
         * @param targetMonth    目标月份
         * @param totalItemCount 扫描总数
         * @param items          明细结果
         * @param targetPlanIds  公司 → 目标表头
         * @param sourcePlanIds  来源表头集合
         * @return 批次结果
         */
        public static RolloverBatchOutcome of(Long batchId, String batchNo, String sourceMonth, String targetMonth,
                                              int totalItemCount, List<RolloverItemOutcome> items,
                                              Map<Long, Long> targetPlanIds, List<Long> sourcePlanIds) {
            return new RolloverBatchOutcome(batchId, batchNo, sourceMonth, targetMonth,
                totalItemCount, List.copyOf(items), Map.copyOf(targetPlanIds),
                List.copyOf(sourcePlanIds == null ? List.of() : sourcePlanIds));
        }

        /**
         * 成功条数。
         *
         * @return 成功条数
         */
        public int successCount() {
            return (int) items.stream().filter(RolloverItemOutcome::isSuccess).count();
        }

        /**
         * 跳过条数。
         *
         * @return 跳过条数
         */
        public int skippedCount() {
            return (int) items.stream().filter(RolloverItemOutcome::isSkipped).count();
        }

        /**
         * 失败条数。
         *
         * @return 失败条数
         */
        public int failedCount() {
            return (int) items.stream().filter(RolloverItemOutcome::isFailed).count();
        }

        /**
         * 实际结转总人数。
         *
         * @return 结转总人数
         */
        public int totalCarryoverQty() {
            return items.stream()
                .filter(RolloverItemOutcome::isSuccess)
                .mapToInt(item -> item.carryoverQty() == null ? 0 : item.carryoverQty())
                .sum();
        }

        /**
         * 本次批次影响的计划表头（目标表头 + 来源任务所属表头）。
         *
         * @return 计划表头ID集合
         */
        public List<Long> affectedPlanIds() {
            Map<Long, Long> ids = new LinkedHashMap<>();
            for (Long planId : targetPlanIds.values()) {
                ids.put(planId, planId);
            }
            for (Long planId : sourcePlanIds) {
                ids.put(planId, planId);
            }
            return new ArrayList<>(ids.values());
        }
    }

}
