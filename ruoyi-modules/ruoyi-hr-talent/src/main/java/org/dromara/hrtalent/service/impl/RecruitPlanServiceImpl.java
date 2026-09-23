package org.dromara.hrtalent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domainservice.PlanItemStatusDomainService;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemActionBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemQueryBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitPlan;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanItemVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanVo;
import org.dromara.hrtalent.domain.vo.recruitment.RolloverChainVo;
import org.dromara.hrtalent.enums.PlanCompletionStatusEnum;
import org.dromara.hrtalent.enums.PlanControlStatusEnum;
import org.dromara.hrtalent.enums.PlanExecutionStatusEnum;
import org.dromara.hrtalent.enums.PlanSourceTypeEnum;
import org.dromara.hrtalent.enums.PlanStatusEnum;
import org.dromara.hrtalent.enums.UrgencyEnum;
import org.dromara.hrtalent.mapper.RecruitPlanItemMapper;
import org.dromara.hrtalent.mapper.RecruitPlanMapper;
import org.dromara.hrtalent.service.recruitment.IPlanItemStatusService;
import org.dromara.hrtalent.service.recruitment.IRecruitPlanService;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 公司月度计划与计划任务服务实现（SPEC-P2 §3.2 / §4.2）。
 *
 * <p><b>合并禁止</b>：{@link #addItem} 与结转入库都只做 insert，任何情况下<b>不查重、不覆盖、不复用</b>
 * 已有任务；同公司 / 同部门 / 同岗位的相似性只通过 {@link #similar} 提示（设计文档 §7.1.1）。</p>
 *
 * <p><b>月份不可迁移</b>：{@link #updateItem} 不接受 {@code planMonth}，
 * {@link #update} 拒绝修改表头的公司与月份，因此不存在「改月份把原月任务挪到下月」的路径。</p>
 *
 * <p><b>状态维度</b>：{@code control_status} 只由人工动作写入；{@code completion_status}
 * 与 {@code remaining_qty} 由 {@link PlanItemStatusDomainService} 纯函数计算后落库；
 * {@code execution_status} 在 P2 缺少候选人域，保持 {@code pending} 或由状态重算接口传入。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitPlanServiceImpl implements IRecruitPlanService {

    /**
     * 动作：确认。
     */
    private static final String ACTION_CONFIRM = "confirm";

    /**
     * 动作：关闭。
     */
    private static final String ACTION_CLOSE = "close";

    /**
     * 动作：暂停。
     */
    private static final String ACTION_PAUSE = "pause";

    /**
     * 动作：恢复。
     */
    private static final String ACTION_RESUME = "resume";

    /**
     * 动作：取消。
     */
    private static final String ACTION_CANCEL = "cancel";

    /**
     * 标志：是。
     */
    private static final String FLAG_YES = "1";

    /**
     * 默认允许自动结转。
     */
    private static final String DEFAULT_CARRYOVER_ENABLED = "1";

    /**
     * 计划任务编号生成冲突时的最大重试次数。
     */
    private static final int ITEM_NO_MAX_RETRY = 2;

    /**
     * 计划表头 Mapper。
     */
    private final RecruitPlanMapper recruitPlanMapper;

    /**
     * 计划任务 Mapper。
     */
    private final RecruitPlanItemMapper recruitPlanItemMapper;

    /**
     * 业务编号生成器。
     */
    private final RecruitBusinessNoGenerator businessNoGenerator;

    /**
     * 计划任务状态领域服务（纯计算）。
     */
    private final PlanItemStatusDomainService planItemStatusDomainService;

    /**
     * 计划任务状态重算服务。
     */
    private final IPlanItemStatusService planItemStatusService;

    /* ------------------------------------------------------------------ 表头 ------------------------------------------------------------------ */

    @Override
    public PageResult<RecruitPlanVo> queryPage(RecruitPlanQueryBo bo, PageQuery pageQuery) {
        RecruitPlanQueryBo query = bo == null ? new RecruitPlanQueryBo() : bo;
        LambdaQueryWrapper<RecruitPlan> wrapper = new LambdaQueryWrapper<RecruitPlan>()
            .like(StringUtils.isNotBlank(query.getPlanNo()), RecruitPlan::getPlanNo, query.getPlanNo())
            .like(StringUtils.isNotBlank(query.getCompanyName()), RecruitPlan::getCompanyName, query.getCompanyName())
            .eq(query.getCompanyDeptId() != null, RecruitPlan::getCompanyDeptId, query.getCompanyDeptId())
            .eq(StringUtils.isNotBlank(query.getPlanMonth()), RecruitPlan::getPlanMonth, query.getPlanMonth())
            .eq(StringUtils.isNotBlank(query.getStatus()), RecruitPlan::getStatus, query.getStatus())
            .orderByDesc(RecruitPlan::getPlanMonth)
            .orderByDesc(RecruitPlan::getPlanId);
        Page<RecruitPlanVo> page = recruitPlanMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public RecruitPlanVo getDetail(Long planId) {
        RecruitPlan plan = loadPlan(planId);
        RecruitPlanVo vo = recruitPlanMapper.selectVoById(plan.getPlanId());
        if (vo == null) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_PLAN_001);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RecruitPlanBo bo) {
        String planMonth = requirePlanMonth(bo.getPlanMonth());
        RecruitPlan exist = findPlan(bo.getCompanyDeptId(), planMonth);
        if (exist != null) {
            // 一个公司 + 一个自然月只有一张表头：由服务层判定并给出中文提示
            throw new ServiceException("该公司在 " + planMonth + " 已有月度计划（计划编号 "
                + exist.getPlanNo() + "），请直接在该计划下新增任务");
        }
        RecruitPlan plan = new RecruitPlan();
        plan.setPlanNo(businessNoGenerator.nextPlanNo(YearMonth.parse(planMonth)));
        plan.setCompanyDeptId(bo.getCompanyDeptId());
        plan.setCompanyName(bo.getCompanyName());
        plan.setPlanMonth(planMonth);
        plan.setStatus(PlanStatusEnum.DRAFT.getCode());
        plan.setGeneratedFlag("0");
        plan.setTotalPlanQty(0);
        plan.setTotalCreditedQty(0);
        plan.setTotalRemainingQty(0);
        plan.setRemark(bo.getRemark());
        try {
            recruitPlanMapper.insert(plan);
        } catch (DuplicateKeyException e) {
            // 编号冲突或并发创建：唯一索引兜底
            throw new ServiceException("该公司该月份的月度计划已存在或计划编号冲突，请刷新后重试");
        }
        log.info("新增公司月度计划, planId={}, planNo={}, companyDeptId={}, planMonth={}",
            plan.getPlanId(), plan.getPlanNo(), plan.getCompanyDeptId(), planMonth);
        return plan.getPlanId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RecruitPlanBo bo) {
        RecruitPlan exist = loadPlan(bo.getPlanId());
        if (!PlanStatusEnum.DRAFT.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅草稿状态的月度计划可以修改，当前状态："
                + statusDesc(exist.getStatus()));
        }
        if (bo.getCompanyDeptId() != null && !bo.getCompanyDeptId().equals(exist.getCompanyDeptId())) {
            throw new ServiceException("不允许修改月度计划所属公司，请新建计划");
        }
        if (StringUtils.isNotBlank(bo.getPlanMonth())
            && !bo.getPlanMonth().equals(exist.getPlanMonth())) {
            // 硬性规则：禁止通过修改月份把原月任务迁移到下月
            throw new ServiceException("计划月份不允许修改（禁止通过改月份迁移原月任务）");
        }
        recruitPlanMapper.update(null, new LambdaUpdateWrapper<RecruitPlan>()
            .eq(RecruitPlan::getPlanId, exist.getPlanId())
            .set(StringUtils.isNotBlank(bo.getCompanyName()), RecruitPlan::getCompanyName, bo.getCompanyName())
            .set(bo.getRemark() != null, RecruitPlan::getRemark, bo.getRemark()));
        log.info("修改公司月度计划, planId={}", exist.getPlanId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void action(Long planId, String action) {
        String code = action == null ? "" : action.trim();
        RecruitPlan plan = loadPlan(planId);
        LocalDateTime now = LocalDateTime.now();
        if (ACTION_CONFIRM.equals(code)) {
            if (PlanStatusEnum.CLOSED.getCode().equals(plan.getStatus())) {
                throw new ServiceException(HrTalentErrorCode.MSG_HR_PLAN_001);
            }
            if (PlanStatusEnum.EXECUTING.getCode().equals(plan.getStatus())) {
                throw new ServiceException("计划已确认，无需重复确认");
            }
            recruitPlanMapper.update(null, new LambdaUpdateWrapper<RecruitPlan>()
                .eq(RecruitPlan::getPlanId, planId)
                .set(RecruitPlan::getStatus, PlanStatusEnum.EXECUTING.getCode())
                .set(RecruitPlan::getConfirmedBy, LoginHelper.getUserId())
                .set(RecruitPlan::getConfirmedTime, now)
                .set(RecruitPlan::getGeneratedFlag, FLAG_YES)
                .set(RecruitPlan::getGeneratedTime, now));
        } else if (ACTION_CLOSE.equals(code)) {
            if (PlanStatusEnum.CLOSED.getCode().equals(plan.getStatus())) {
                throw new ServiceException("计划已关闭，无需重复关闭");
            }
            recruitPlanMapper.update(null, new LambdaUpdateWrapper<RecruitPlan>()
                .eq(RecruitPlan::getPlanId, planId)
                .set(RecruitPlan::getStatus, PlanStatusEnum.CLOSED.getCode())
                .set(RecruitPlan::getClosedBy, LoginHelper.getUserId())
                .set(RecruitPlan::getClosedTime, now));
        } else {
            throw new ServiceException("不支持的月度计划动作：" + code);
        }
        log.info("月度计划动作完成, planId={}, action={}", planId, code);
    }

    /* ------------------------------------------------------------------ 计划任务 ------------------------------------------------------------------ */

    @Override
    public PageResult<RecruitPlanItemVo> queryItemPage(RecruitPlanItemQueryBo bo, PageQuery pageQuery) {
        RecruitPlanItemQueryBo query = bo == null ? new RecruitPlanItemQueryBo() : bo;
        Page<RecruitPlanItemVo> page = recruitPlanItemMapper.selectVoPage(pageQuery.build(), itemWrapper(query));
        fillComputed(page.getRecords());
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public PageResult<RecruitPlanItemVo> queryItemPageOfPlan(Long planId, RecruitPlanItemQueryBo bo,
                                                              PageQuery pageQuery) {
        loadPlan(planId);
        RecruitPlanItemQueryBo query = bo == null ? new RecruitPlanItemQueryBo() : bo;
        query.setPlanId(planId);
        return queryItemPage(query, pageQuery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addItem(Long planId, RecruitPlanItemBo bo) {
        RecruitPlan plan = loadPlan(planId);
        if (PlanStatusEnum.CLOSED.getCode().equals(plan.getStatus())) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_PLAN_001);
        }
        if (bo == null) {
            throw new ServiceException("计划任务参数不能为空");
        }
        if (bo.getPlanQty() == null || bo.getPlanQty() <= 0) {
            throw new ServiceException("计划人数必须大于 0");
        }
        String urgency = resolveUrgency(bo.getUrgency(), true);
        String carryoverEnabled = resolveCarryoverEnabled(bo.getCarryoverEnabled(), true);

        RecruitPlanItem entity = new RecruitPlanItem();
        // 服务端权威字段：表头、月份、公司与来源类型一律以表头为准，不接受前端写入
        entity.setPlanId(plan.getPlanId());
        entity.setPlanMonth(plan.getPlanMonth());
        entity.setCompanyDeptId(plan.getCompanyDeptId());
        entity.setCompanyName(plan.getCompanyName());
        entity.setSourceType(PlanSourceTypeEnum.NEW.getCode());
        entity.setDemandId(bo.getDemandId());
        entity.setJobId(bo.getJobId());
        entity.setUseDeptId(bo.getUseDeptId());
        entity.setUseDeptName(bo.getUseDeptName());
        entity.setJobName(bo.getJobName());
        entity.setPlanQty(bo.getPlanQty());
        entity.setCreditedArrivalQty(0);
        entity.setRemainingQty(planItemStatusDomainService.resolveRemainingQty(bo.getPlanQty(), 0));
        entity.setControlStatus(PlanControlStatusEnum.NORMAL.getCode());
        entity.setExecutionStatus(PlanExecutionStatusEnum.PENDING.getCode());
        entity.setCompletionStatus(PlanCompletionStatusEnum.UNFINISHED.getCode());
        entity.setLastRefreshTime(LocalDateTime.now());
        entity.setCarryoverEnabled(carryoverEnabled);
        entity.setOwnerId(bo.getOwnerId());
        entity.setUrgency(urgency);
        entity.setStandardDays(bo.getStandardDays());
        entity.setRemark(bo.getRemark());
        Long itemId = insertItem(entity, plan.getPlanMonth());
        log.info("新增月度计划任务（不合并相似任务）, itemId={}, itemNo={}, planId={}, jobName={}",
            itemId, entity.getItemNo(), plan.getPlanId(), entity.getJobName());
        planItemStatusService.refreshPlanTotals(plan.getPlanId());
        return itemId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(RecruitPlanItemBo bo) {
        RecruitPlanItem exist = loadItem(bo.getItemId());
        if (planItemStatusDomainService.isFinalized(exist)) {
            throw new ServiceException(planItemStatusDomainService.finalizedMessage());
        }
        if (bo.getPlanId() != null && !bo.getPlanId().equals(exist.getPlanId())) {
            // 禁止通过改计划把任务挪到别的月份
            throw new ServiceException("不允许迁移计划任务所属计划或月份");
        }
        String urgency = resolveUrgency(bo.getUrgency(), false);
        String carryoverEnabled = resolveCarryoverEnabled(bo.getCarryoverEnabled(), false);
        LambdaUpdateWrapper<RecruitPlanItem> update = new LambdaUpdateWrapper<RecruitPlanItem>()
            .eq(RecruitPlanItem::getItemId, exist.getItemId())
            .set(StringUtils.isNotBlank(bo.getJobName()), RecruitPlanItem::getJobName, bo.getJobName())
            .set(bo.getUseDeptId() != null, RecruitPlanItem::getUseDeptId, bo.getUseDeptId())
            .set(StringUtils.isNotBlank(bo.getUseDeptName()), RecruitPlanItem::getUseDeptName, bo.getUseDeptName())
            .set(bo.getOwnerId() != null, RecruitPlanItem::getOwnerId, bo.getOwnerId())
            .set(bo.getDemandId() != null, RecruitPlanItem::getDemandId, bo.getDemandId())
            .set(bo.getJobId() != null, RecruitPlanItem::getJobId, bo.getJobId())
            .set(bo.getStandardDays() != null, RecruitPlanItem::getStandardDays, bo.getStandardDays())
            .set(bo.getRemark() != null, RecruitPlanItem::getRemark, bo.getRemark())
            .set(urgency != null, RecruitPlanItem::getUrgency, urgency)
            .set(carryoverEnabled != null, RecruitPlanItem::getCarryoverEnabled, carryoverEnabled);
        if (bo.getPlanQty() != null) {
            // 计划人数调整：剩余人数按 max(计划 - 到岗, 0) 重算，完成度同步刷新
            int remaining = planItemStatusDomainService.resolveRemainingQty(
                bo.getPlanQty(), exist.getCreditedArrivalQty());
            PlanCompletionStatusEnum completion = planItemStatusDomainService.resolveCompletionStatus(
                exist.getCreditedArrivalQty(), remaining, false);
            update.set(RecruitPlanItem::getPlanQty, bo.getPlanQty())
                .set(RecruitPlanItem::getRemainingQty, remaining)
                .set(RecruitPlanItem::getCompletionStatus, completion.getCode())
                .set(RecruitPlanItem::getLastRefreshTime, LocalDateTime.now());
        }
        recruitPlanItemMapper.update(null, update);
        planItemStatusService.refreshPlanTotals(exist.getPlanId());
        log.info("修改月度计划任务, itemId={}, planQty={}", exist.getItemId(), bo.getPlanQty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void itemAction(Long itemId, String action, RecruitPlanItemActionBo bo) {
        String code = action == null ? "" : action.trim();
        RecruitPlanItemActionBo params = bo == null ? new RecruitPlanItemActionBo() : bo;
        RecruitPlanItem item = loadItem(itemId);
        LambdaUpdateWrapper<RecruitPlanItem> update = new LambdaUpdateWrapper<RecruitPlanItem>()
            .eq(RecruitPlanItem::getItemId, itemId);
        boolean controlChanged = false;
        if (ACTION_PAUSE.equals(code)) {
            if (PlanControlStatusEnum.CANCELLED.getCode().equals(item.getControlStatus())) {
                throw new ServiceException("任务已取消，不能暂停");
            }
            if (PlanControlStatusEnum.PAUSED.getCode().equals(item.getControlStatus())) {
                throw new ServiceException("任务已暂停，无需重复暂停");
            }
            requireReason(params.getReason(), "暂停");
            update.set(RecruitPlanItem::getControlStatus, PlanControlStatusEnum.PAUSED.getCode())
                .set(RecruitPlanItem::getControlReason, params.getReason());
            controlChanged = true;
        } else if (ACTION_RESUME.equals(code)) {
            if (!PlanControlStatusEnum.PAUSED.getCode().equals(item.getControlStatus())) {
                throw new ServiceException("仅已暂停的任务可以恢复");
            }
            // 恢复后原暂停原因不再适用，显式置空
            update.set(RecruitPlanItem::getControlStatus, PlanControlStatusEnum.NORMAL.getCode())
                .set(RecruitPlanItem::getControlReason, null);
            controlChanged = true;
        } else if (ACTION_CANCEL.equals(code)) {
            if (PlanControlStatusEnum.CANCELLED.getCode().equals(item.getControlStatus())) {
                throw new ServiceException("任务已取消，无需重复取消");
            }
            if (planItemStatusDomainService.isFinalized(item)) {
                throw new ServiceException(planItemStatusDomainService.finalizedMessage());
            }
            requireReason(params.getReason(), "取消");
            update.set(RecruitPlanItem::getControlStatus, PlanControlStatusEnum.CANCELLED.getCode())
                .set(RecruitPlanItem::getControlReason, params.getReason());
            controlChanged = true;
        } else {
            throw new ServiceException("不支持的计划任务动作：" + code);
        }
        // 结转开关可在动作接口内一并调整（业务管理员结转前设置）
        String carryoverEnabled = resolveCarryoverEnabled(params.getCarryoverEnabled(), false);
        if (carryoverEnabled != null) {
            update.set(RecruitPlanItem::getCarryoverEnabled, carryoverEnabled);
        }
        update.set(RecruitPlanItem::getLastRefreshTime, LocalDateTime.now());
        recruitPlanItemMapper.update(null, update);
        if (controlChanged) {
            // 重算完成度与展示状态，但绝不覆盖人工控制状态
            planItemStatusService.refreshItemStatus(itemId);
        }
        log.info("月度计划任务动作完成, itemId={}, action={}, controlStatus={}",
            itemId, code, item.getControlStatus());
    }

    @Override
    public RolloverChainVo rolloverChain(Long itemId) {
        RecruitPlanItem item = loadItem(itemId);
        Long rootId = item.getRootPlanItemId() == null ? item.getItemId() : item.getRootPlanItemId();
        List<RecruitPlanItemVo> chain = recruitPlanItemMapper.selectVoList(
            new LambdaQueryWrapper<RecruitPlanItem>()
                .and(wrapper -> wrapper.eq(RecruitPlanItem::getRootPlanItemId, rootId)
                    .or().eq(RecruitPlanItem::getItemId, rootId))
                .orderByAsc(RecruitPlanItem::getPlanMonth)
                .orderByAsc(RecruitPlanItem::getItemId));
        fillComputed(chain);
        RolloverChainVo vo = new RolloverChainVo();
        vo.setCurrentItemId(itemId);
        vo.setRootPlanItemId(rootId);
        vo.setItemCount(chain.size());
        vo.setItems(chain);
        return vo;
    }

    @Override
    public void refreshItemStatus(Long itemId) {
        planItemStatusService.refreshItemStatus(itemId);
    }

    @Override
    public PageResult<RecruitPlanItemVo> similar(RecruitPlanItemQueryBo bo, PageQuery pageQuery) {
        RecruitPlanItemQueryBo query = bo == null ? new RecruitPlanItemQueryBo() : bo;
        // 相似计划只用于提示：不合并、不覆盖、不复用（设计文档 §7.1.1）
        LambdaQueryWrapper<RecruitPlanItem> wrapper = new LambdaQueryWrapper<RecruitPlanItem>()
            .eq(query.getCompanyDeptId() != null, RecruitPlanItem::getCompanyDeptId, query.getCompanyDeptId())
            .eq(query.getUseDeptId() != null, RecruitPlanItem::getUseDeptId, query.getUseDeptId())
            .like(StringUtils.isNotBlank(query.getJobName()), RecruitPlanItem::getJobName, query.getJobName())
            .ne(query.getExcludeItemId() != null, RecruitPlanItem::getItemId, query.getExcludeItemId())
            .orderByDesc(RecruitPlanItem::getPlanMonth)
            .orderByDesc(RecruitPlanItem::getItemId);
        Page<RecruitPlanItemVo> page = recruitPlanItemMapper.selectVoPage(pageQuery.build(), wrapper);
        fillComputed(page.getRecords());
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanHeader findOrCreatePlan(Long companyDeptId, String companyName, String planMonth) {
        if (companyDeptId == null) {
            throw new ServiceException("公司不能为空");
        }
        String month = requirePlanMonth(planMonth);
        // 命中即复用：一个公司一个月只有一张表头，导入不该因为表头已存在而失败
        RecruitPlan exist = findPlan(companyDeptId, month);
        if (exist != null) {
            return new PlanHeader(exist.getPlanId(), false);
        }
        RecruitPlanBo bo = new RecruitPlanBo();
        bo.setCompanyDeptId(companyDeptId);
        bo.setCompanyName(companyName);
        bo.setPlanMonth(month);
        bo.setRemark("由数据导入自动创建");
        return new PlanHeader(create(bo), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addImportedItem(Long planId, RecruitPlanItemBo bo, Long batchId) {
        RecruitPlan plan = loadPlan(planId);
        if (PlanStatusEnum.CLOSED.getCode().equals(plan.getStatus())) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_PLAN_001);
        }
        if (bo == null) {
            throw new ServiceException("计划任务参数不能为空");
        }
        if (bo.getPlanQty() == null || bo.getPlanQty() <= 0) {
            throw new ServiceException("计划人数必须大于 0");
        }
        RecruitPlanItem entity = new RecruitPlanItem();
        entity.setPlanId(plan.getPlanId());
        entity.setPlanMonth(plan.getPlanMonth());
        entity.setCompanyDeptId(plan.getCompanyDeptId());
        entity.setCompanyName(plan.getCompanyName());
        entity.setSourceType(PlanSourceTypeEnum.NEW.getCode());
        entity.setUseDeptId(bo.getUseDeptId());
        entity.setUseDeptName(bo.getUseDeptName());
        entity.setJobName(bo.getJobName());
        entity.setPlanQty(bo.getPlanQty());
        entity.setCreditedArrivalQty(0);
        entity.setRemainingQty(planItemStatusDomainService.resolveRemainingQty(bo.getPlanQty(), 0));
        entity.setControlStatus(PlanControlStatusEnum.NORMAL.getCode());
        entity.setExecutionStatus(PlanExecutionStatusEnum.PENDING.getCode());
        entity.setCompletionStatus(PlanCompletionStatusEnum.UNFINISHED.getCode());
        entity.setLastRefreshTime(LocalDateTime.now());
        entity.setCarryoverEnabled(resolveCarryoverEnabled(bo.getCarryoverEnabled(), true));
        entity.setOwnerId(bo.getOwnerId());
        entity.setUrgency(resolveUrgency(bo.getUrgency(), true));
        entity.setStandardDays(bo.getStandardDays());
        entity.setRemark(bo.getRemark());
        // 导入溯源：这条任务是哪一次导入、哪个文件带来的
        entity.setImportBatchId(batchId);
        Long itemId = insertItem(entity, plan.getPlanMonth());
        log.info("导入新增月度计划任务, itemId={}, itemNo={}, planId={}, batchId={}, jobName={}",
            itemId, entity.getItemNo(), plan.getPlanId(), batchId, entity.getJobName());
        planItemStatusService.refreshPlanTotals(plan.getPlanId());
        return itemId;
    }

    /**
     * 构造计划任务查询条件。
     *
     * @param query 查询入参
     * @return 查询条件
     */
    private LambdaQueryWrapper<RecruitPlanItem> itemWrapper(RecruitPlanItemQueryBo query) {
        return new LambdaQueryWrapper<RecruitPlanItem>()
            .like(StringUtils.isNotBlank(query.getItemNo()), RecruitPlanItem::getItemNo, query.getItemNo())
            .eq(query.getPlanId() != null, RecruitPlanItem::getPlanId, query.getPlanId())
            .eq(query.getDemandId() != null, RecruitPlanItem::getDemandId, query.getDemandId())
            .eq(query.getJobId() != null, RecruitPlanItem::getJobId, query.getJobId())
            .eq(query.getCompanyDeptId() != null, RecruitPlanItem::getCompanyDeptId, query.getCompanyDeptId())
            .eq(query.getUseDeptId() != null, RecruitPlanItem::getUseDeptId, query.getUseDeptId())
            .like(StringUtils.isNotBlank(query.getJobName()), RecruitPlanItem::getJobName, query.getJobName())
            .eq(StringUtils.isNotBlank(query.getPlanMonth()), RecruitPlanItem::getPlanMonth, query.getPlanMonth())
            .ge(StringUtils.isNotBlank(query.getPlanMonthBegin()), RecruitPlanItem::getPlanMonth, query.getPlanMonthBegin())
            .le(StringUtils.isNotBlank(query.getPlanMonthEnd()), RecruitPlanItem::getPlanMonth, query.getPlanMonthEnd())
            .eq(StringUtils.isNotBlank(query.getSourceType()), RecruitPlanItem::getSourceType, query.getSourceType())
            .eq(StringUtils.isNotBlank(query.getControlStatus()), RecruitPlanItem::getControlStatus, query.getControlStatus())
            .eq(StringUtils.isNotBlank(query.getExecutionStatus()), RecruitPlanItem::getExecutionStatus, query.getExecutionStatus())
            .eq(StringUtils.isNotBlank(query.getCompletionStatus()), RecruitPlanItem::getCompletionStatus, query.getCompletionStatus())
            .eq(query.getOwnerId() != null, RecruitPlanItem::getOwnerId, query.getOwnerId())
            .eq(StringUtils.isNotBlank(query.getUrgency()), RecruitPlanItem::getUrgency, query.getUrgency())
            .ne(query.getExcludeItemId() != null, RecruitPlanItem::getItemId, query.getExcludeItemId())
            .orderByDesc(RecruitPlanItem::getPlanMonth)
            .orderByDesc(RecruitPlanItem::getItemId);
    }

    /**
     * 为出参补齐主展示状态与「部分完成」标记（服务端权威计算，前端只读）。
     *
     * @param vos 计划任务出参列表
     */
    private void fillComputed(List<RecruitPlanItemVo> vos) {
        if (CollUtil.isEmpty(vos)) {
            return;
        }
        for (RecruitPlanItemVo vo : vos) {
            PlanItemStatusDomainService.DisplayStatus display = planItemStatusDomainService.resolveDisplayStatus(
                vo.getControlStatus(), vo.getExecutionStatus(), vo.getCompletionStatus(), vo.getRemainingQty());
            vo.setDisplayStatus(display.getCode());
            vo.setDisplayStatusLabel(display.getDesc());
            vo.setPartialCompleted(planItemStatusDomainService.isPartiallyCompleted(
                vo.getCreditedArrivalQty(), vo.getRemainingQty()));
        }
    }

    /**
     * 插入计划任务，业务编号冲突时重试。
     *
     * @param entity    计划任务实体
     * @param planMonth 计划月份（yyyy-MM）
     * @return 任务ID
     */
    private Long insertItem(RecruitPlanItem entity, String planMonth) {
        YearMonth month = YearMonth.parse(planMonth);
        for (int attempt = 0; attempt < ITEM_NO_MAX_RETRY; attempt++) {
            entity.setItemId(null);
            entity.setItemNo(businessNoGenerator.nextPlanItemNo(month));
            try {
                recruitPlanItemMapper.insert(entity);
                return entity.getItemId();
            } catch (DuplicateKeyException e) {
                log.warn("计划任务编号生成冲突, itemNo={}, attempt={}", entity.getItemNo(), attempt + 1);
            }
        }
        throw new ServiceException("计划任务编号生成冲突，请重试");
    }

    /**
     * 加载计划表头。
     *
     * @param planId 计划ID
     * @return 计划表头
     */
    private RecruitPlan loadPlan(Long planId) {
        if (planId == null) {
            throw new ServiceException("计划ID不能为空");
        }
        RecruitPlan plan = recruitPlanMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_PLAN_001);
        }
        return plan;
    }

    /**
     * 加载计划任务。
     *
     * @param itemId 计划任务ID
     * @return 计划任务
     */
    private RecruitPlanItem loadItem(Long itemId) {
        if (itemId == null) {
            throw new ServiceException("计划任务ID不能为空");
        }
        RecruitPlanItem item = recruitPlanItemMapper.selectById(itemId);
        if (item == null) {
            throw new ServiceException("计划任务不存在或已删除");
        }
        return item;
    }

    /**
     * 查询公司 + 月份的计划表头。
     *
     * @param companyDeptId 公司（平台部门）ID
     * @param planMonth     计划月份
     * @return 计划表头，不存在返回 null
     */
    private RecruitPlan findPlan(Long companyDeptId, String planMonth) {
        if (companyDeptId == null || StringUtils.isBlank(planMonth)) {
            return null;
        }
        return recruitPlanMapper.selectOne(new LambdaQueryWrapper<RecruitPlan>()
            .eq(RecruitPlan::getCompanyDeptId, companyDeptId)
            .eq(RecruitPlan::getPlanMonth, planMonth));
    }

    /**
     * 校验并规范化计划月份。
     *
     * @param planMonth 月份文本
     * @return 规范化后的月份
     */
    private String requirePlanMonth(String planMonth) {
        if (StringUtils.isBlank(planMonth)) {
            throw new ServiceException("计划月份不能为空");
        }
        try {
            return YearMonth.parse(planMonth.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new ServiceException("计划月份格式必须为 yyyy-MM");
        }
    }

    /**
     * 校验暂停 / 取消必须填写原因。
     *
     * @param reason 原因
     * @param action 动作中文名
     */
    private void requireReason(String reason, String action) {
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException(action + "计划任务必须填写原因");
        }
    }

    /**
     * 解析紧急程度编码。
     *
     * @param urgency 编码
     * @param create  是否新增场景（新增时空值回落为普通）
     * @return 规范化编码；更新场景返回 null 表示不修改
     */
    private String resolveUrgency(String urgency, boolean create) {
        if (StringUtils.isBlank(urgency)) {
            return create ? UrgencyEnum.NORMAL.getCode() : null;
        }
        if (UrgencyEnum.find(urgency) == null) {
            throw new ServiceException("紧急程度不合法，请使用字典 recruit_urgency 的编码"
                + "（normal/urgent/very_urgent）");
        }
        return urgency;
    }

    /**
     * 解析结转开关。
     *
     * @param carryoverEnabled 开关值
     * @param create           是否新增场景（新增时空值回落为允许结转）
     * @return 规范化开关；更新场景返回 null 表示不修改
     */
    private String resolveCarryoverEnabled(String carryoverEnabled, boolean create) {
        if (StringUtils.isBlank(carryoverEnabled)) {
            return create ? DEFAULT_CARRYOVER_ENABLED : null;
        }
        if (!"0".equals(carryoverEnabled) && !FLAG_YES.equals(carryoverEnabled)) {
            throw new ServiceException("是否允许自动结转只能为 0 或 1");
        }
        return carryoverEnabled;
    }

    /**
     * 计划状态中文名。
     *
     * @param status 状态编码
     * @return 中文名
     */
    private String statusDesc(String status) {
        PlanStatusEnum item = PlanStatusEnum.find(status);
        return item == null ? status : item.getDesc();
    }

}
