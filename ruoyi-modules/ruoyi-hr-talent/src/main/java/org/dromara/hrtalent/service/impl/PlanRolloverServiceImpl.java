package org.dromara.hrtalent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domainservice.PlanRolloverDomainService;
import org.dromara.hrtalent.domain.bo.recruitment.RolloverExecuteBo;
import org.dromara.hrtalent.domain.bo.recruitment.RolloverPreviewBo;
import org.dromara.hrtalent.domain.entity.RecruitPlan;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
import org.dromara.hrtalent.domain.entity.RecruitPlanRollover;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanRolloverVo;
import org.dromara.hrtalent.domain.vo.recruitment.RolloverPreviewVo;
import org.dromara.hrtalent.mapper.RecruitPlanItemMapper;
import org.dromara.hrtalent.mapper.RecruitPlanRolloverMapper;
import org.dromara.hrtalent.service.recruitment.IPlanRolloverService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 月度结转服务实现（SPEC-P2 §3.3 / §4.3）。
 *
 * <p>本类只做入参校验、登录态解析与出参装配；结转的幂等、事务与状态规则全部由
 * {@link PlanRolloverDomainService} 负责（SPEC-P2 §21.5：领域服务不允许改原任务月份、
 * 不允许合并同岗位任务）。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanRolloverServiceImpl implements IPlanRolloverService {

    /**
     * 结转记录 Mapper。
     */
    private final RecruitPlanRolloverMapper recruitPlanRolloverMapper;

    /**
     * 计划任务 Mapper（装配明细展示信息）。
     */
    private final RecruitPlanItemMapper recruitPlanItemMapper;

    /**
     * 月度结转领域服务。
     */
    private final PlanRolloverDomainService planRolloverDomainService;

    @Override
    public RolloverPreviewVo preview(RolloverPreviewBo bo) {
        if (bo == null) {
            throw new ServiceException("预览条件不能为空");
        }
        planRolloverDomainService.validateMonthRange(bo.getSourceMonth(), bo.getTargetMonth());
        List<RecruitPlanItem> sources = planRolloverDomainService.listSourceItems(
            bo.getCompanyDeptId(), bo.getSourceMonth(), null);

        RolloverPreviewVo vo = new RolloverPreviewVo();
        vo.setCompanyDeptId(bo.getCompanyDeptId());
        vo.setSourceMonth(bo.getSourceMonth());
        vo.setTargetMonth(bo.getTargetMonth());
        fillTargetPlan(vo, bo.getCompanyDeptId(), bo.getTargetMonth());

        int eligibleCount = 0;
        int skippedCount = 0;
        int totalQty = 0;
        for (RecruitPlanItem source : sources) {
            RolloverPreviewVo.Item item = buildPreviewItem(source);
            String reason = planRolloverDomainService.ineligibleReason(source);
            if (reason == null) {
                RecruitPlanRollover exist = planRolloverDomainService.findRollover(
                    source.getItemId(), bo.getTargetMonth());
                if (exist != null && PlanRolloverDomainService.RESULT_SUCCESS.equals(exist.getResult())) {
                    // 幂等提示：同一来源任务已生成目标月份结转任务
                    reason = "同一来源任务已生成目标月份结转任务";
                }
            }
            if (reason == null) {
                eligibleCount++;
                totalQty += item.getCarryoverQty() == null ? 0 : item.getCarryoverQty();
                item.setEligible(true);
            } else {
                skippedCount++;
                item.setEligible(false);
                item.setSkipReason(reason);
            }
            vo.getItems().add(item);
        }
        vo.setTotalItemCount(sources.size());
        vo.setEligibleCount(eligibleCount);
        vo.setSuccessCount(0);
        vo.setSkippedCount(skippedCount);
        vo.setFailedCount(0);
        vo.setTotalCarryoverQty(totalQty);
        return vo;
    }

    @Override
    public RolloverPreviewVo execute(RolloverExecuteBo bo) {
        if (bo == null) {
            throw new ServiceException("结转执行条件不能为空");
        }
        Long operatorId = LoginHelper.getUserId();
        PlanRolloverDomainService.RolloverBatchOutcome outcome = planRolloverDomainService.executeBatch(
            bo.getCompanyDeptId(), bo.getSourceMonth(), bo.getTargetMonth(),
            bo.getSourceItemIds(), operatorId);
        return toVo(outcome, bo.getCompanyDeptId());
    }

    @Override
    public RolloverPreviewVo retry(String batchNo) {
        if (StringUtils.isBlank(batchNo)) {
            throw new ServiceException("结转批次号不能为空");
        }
        Long operatorId = LoginHelper.getUserId();
        PlanRolloverDomainService.RolloverBatchOutcome outcome =
            planRolloverDomainService.retryBatch(batchNo, operatorId);
        return toVo(outcome, null);
    }

    @Override
    public List<RecruitPlanRolloverVo> getBatch(String batchNo) {
        if (StringUtils.isBlank(batchNo)) {
            throw new ServiceException("结转批次号不能为空");
        }
        List<RecruitPlanRollover> records = planRolloverDomainService.listBatch(batchNo);
        List<RecruitPlanRolloverVo> vos = new ArrayList<>();
        for (RecruitPlanRollover record : records) {
            vos.add(toRolloverVo(record));
        }
        fillItemInfo(vos);
        return vos;
    }

    @Override
    public PageResult<RecruitPlanRolloverVo> queryPage(RolloverPreviewBo bo, PageQuery pageQuery) {
        RolloverPreviewBo query = bo == null ? new RolloverPreviewBo() : bo;
        LambdaQueryWrapper<RecruitPlanRollover> wrapper = new LambdaQueryWrapper<RecruitPlanRollover>()
            .eq(StringUtils.isNotBlank(query.getBatchNo()), RecruitPlanRollover::getBatchNo, query.getBatchNo())
            .eq(StringUtils.isNotBlank(query.getTargetMonth()), RecruitPlanRollover::getTargetMonth, query.getTargetMonth())
            .eq(StringUtils.isNotBlank(query.getSourceMonth()), RecruitPlanRollover::getSourceMonth, query.getSourceMonth())
            .eq(StringUtils.isNotBlank(query.getResult()), RecruitPlanRollover::getResult, query.getResult())
            .orderByDesc(RecruitPlanRollover::getExecuteTime)
            .orderByDesc(RecruitPlanRollover::getRolloverId);
        Page<RecruitPlanRolloverVo> page = recruitPlanRolloverMapper.selectVoPage(pageQuery.build(), wrapper);
        List<RecruitPlanRolloverVo> rows = page.getRecords();
        fillItemInfo(rows);
        return PageResult.build(rows, page.getTotal());
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 校验并填充目标月表头信息。
     *
     * @param vo            预览结果
     * @param companyDeptId 公司ID
     * @param targetMonth   目标月份
     */
    private void fillTargetPlan(RolloverPreviewVo vo, Long companyDeptId, String targetMonth) {
        if (companyDeptId == null) {
            // 跨公司预览：目标表头由执行时按公司逐一自动创建
            vo.setTargetPlanExists(false);
            return;
        }
        RecruitPlan plan = planRolloverDomainService.findPlan(companyDeptId, targetMonth);
        vo.setTargetPlanExists(plan != null);
        if (plan != null) {
            vo.setTargetPlanId(plan.getPlanId());
            vo.setCompanyName(plan.getCompanyName());
        }
    }

    /**
     * 构造预览明细行。
     *
     * @param source 来源任务
     * @return 明细行
     */
    private RolloverPreviewVo.Item buildPreviewItem(RecruitPlanItem source) {
        RolloverPreviewVo.Item item = new RolloverPreviewVo.Item();
        item.setSourceItemId(source.getItemId());
        item.setItemNo(source.getItemNo());
        item.setJobName(source.getJobName());
        item.setCompanyDeptId(source.getCompanyDeptId());
        item.setCompanyName(source.getCompanyName());
        item.setUseDeptId(source.getUseDeptId());
        item.setUseDeptName(source.getUseDeptName());
        item.setOwnerId(source.getOwnerId());
        item.setUrgency(source.getUrgency());
        item.setPlanQty(source.getPlanQty());
        item.setCreditedArrivalQty(source.getCreditedArrivalQty());
        int remaining = planRolloverDomainService.remainingQty(source);
        item.setRemainingQty(remaining);
        item.setCarryoverQty(remaining);
        item.setEligible(false);
        return item;
    }

    /**
     * 把批次执行结果装配为出参。
     *
     * @param outcome       批次结果
     * @param companyDeptId 公司ID（为空表示跨公司）
     * @return 出参
     */
    private RolloverPreviewVo toVo(PlanRolloverDomainService.RolloverBatchOutcome outcome, Long companyDeptId) {
        RolloverPreviewVo vo = new RolloverPreviewVo();
        vo.setCompanyDeptId(companyDeptId);
        vo.setSourceMonth(outcome.sourceMonth());
        vo.setTargetMonth(outcome.targetMonth());
        vo.setBatchId(outcome.batchId());
        vo.setBatchNo(outcome.batchNo());
        vo.setTotalItemCount(outcome.totalItemCount());
        vo.setEligibleCount(outcome.totalItemCount() - outcome.skippedCount());
        vo.setSuccessCount(outcome.successCount());
        vo.setSkippedCount(outcome.skippedCount());
        vo.setFailedCount(outcome.failedCount());
        vo.setTotalCarryoverQty(outcome.totalCarryoverQty());
        if (outcome.targetPlanIds().size() == 1) {
            vo.setTargetPlanId(outcome.targetPlanIds().values().iterator().next());
            vo.setTargetPlanExists(true);
        } else {
            vo.setTargetPlanExists(!outcome.targetPlanIds().isEmpty());
        }
        Map<Long, RecruitPlanItem> sourceMap = loadItems(outcome.items().stream()
            .map(PlanRolloverDomainService.RolloverItemOutcome::sourceItemId).toList());
        LocalDateTime now = LocalDateTime.now();
        for (PlanRolloverDomainService.RolloverItemOutcome result : outcome.items()) {
            RecruitPlanItem source = sourceMap.get(result.sourceItemId());
            RolloverPreviewVo.Item item = source == null
                ? new RolloverPreviewVo.Item()
                : buildPreviewItem(source);
            item.setSourceItemId(result.sourceItemId());
            if (source == null) {
                item.setCarryoverQty(0);
                item.setRemainingQty(0);
            }
            item.setResult(result.result());
            item.setTargetItemId(result.targetItemId());
            item.setExecuteTime(now);
            item.setEligible(result.isSuccess());
            if (result.isSkipped()) {
                item.setSkipReason(result.message());
                item.setCarryoverQty(0);
            }
            if (result.isFailed()) {
                item.setFailureReason(result.message());
                item.setCarryoverQty(0);
            }
            vo.getItems().add(item);
        }
        return vo;
    }

    /**
     * 批量加载计划任务详情。
     *
     * @param itemIds 任务ID集合
     * @return 任务ID → 任务
     */
    private Map<Long, RecruitPlanItem> loadItems(List<Long> itemIds) {
        List<Long> ids = itemIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<RecruitPlanItem> items = recruitPlanItemMapper.selectList(new LambdaQueryWrapper<RecruitPlanItem>()
            .in(RecruitPlanItem::getItemId, ids));
        Map<Long, RecruitPlanItem> map = new LinkedHashMap<>();
        for (RecruitPlanItem item : items) {
            map.put(item.getItemId(), item);
        }
        return map;
    }

    /**
     * 结转记录转出参。
     *
     * @param record 结转记录
     * @return 出参
     */
    private RecruitPlanRolloverVo toRolloverVo(RecruitPlanRollover record) {
        RecruitPlanRolloverVo vo = new RecruitPlanRolloverVo();
        vo.setRolloverId(record.getRolloverId());
        vo.setSourceItemId(record.getSourceItemId());
        vo.setTargetItemId(record.getTargetItemId());
        vo.setSourceMonth(record.getSourceMonth());
        vo.setTargetMonth(record.getTargetMonth());
        vo.setCarryoverQty(record.getCarryoverQty());
        vo.setBatchId(record.getBatchId());
        vo.setBatchNo(record.getBatchNo());
        vo.setExecuteTime(record.getExecuteTime());
        vo.setResult(record.getResult());
        vo.setFailureReason(record.getFailureReason());
        vo.setRetryCount(record.getRetryCount());
        vo.setOperatorId(record.getOperatorId());
        vo.setRemark(record.getRemark());
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());
        return vo;
    }

    /**
     * 为结转明细补齐来源 / 目标任务编号与岗位名称。
     *
     * @param vos 待补齐的明细
     */
    private void fillItemInfo(List<RecruitPlanRolloverVo> vos) {
        if (CollUtil.isEmpty(vos)) {
            return;
        }
        List<Long> itemIds = new ArrayList<>();
        for (RecruitPlanRolloverVo vo : vos) {
            if (vo.getSourceItemId() != null) {
                itemIds.add(vo.getSourceItemId());
            }
            if (vo.getTargetItemId() != null) {
                itemIds.add(vo.getTargetItemId());
            }
        }
        Map<Long, RecruitPlanItem> itemMap = loadItems(itemIds);
        for (RecruitPlanRolloverVo vo : vos) {
            RecruitPlanItem source = itemMap.get(vo.getSourceItemId());
            if (source != null) {
                vo.setSourceItemNo(source.getItemNo());
                vo.setSourceJobName(source.getJobName());
            }
            RecruitPlanItem target = itemMap.get(vo.getTargetItemId());
            if (target != null) {
                vo.setTargetItemNo(target.getItemNo());
            }
        }
    }

}
