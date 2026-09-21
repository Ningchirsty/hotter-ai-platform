package org.dromara.hrtalent.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitDemandActionBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitDemandBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitDemandQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitDemand;
import org.dromara.hrtalent.domain.entity.RecruitDemandChange;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitDemandChangeVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitDemandVo;
import org.dromara.hrtalent.enums.DemandStatusEnum;
import org.dromara.hrtalent.enums.UrgencyEnum;
import org.dromara.hrtalent.mapper.RecruitDemandChangeMapper;
import org.dromara.hrtalent.mapper.RecruitDemandMapper;
import org.dromara.hrtalent.service.recruitment.IRecruitDemandService;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 招聘需求服务实现（SPEC-P2 §4.1）。
 *
 * <p><b>状态机</b>（draft → submitted → recruiting →（paused ⇄ recruiting）→ completed / closed）：</p>
 * <ul>
 *     <li>{@code submit}：草稿 → 已提交。</li>
 *     <li>{@code confirm}：已提交 → 招聘中（提交确认），写入确认人与确认时间；
 *     权限复用 {@link HrTalentConstants#PERM_DEMAND_SUBMIT}，不新增权限串。</li>
 *     <li>{@code pause}：招聘中 → 已暂停（必须填原因）。</li>
 *     <li>{@code resume}：已暂停 → 招聘中（必须填原因）。</li>
 *     <li>{@code complete}：招聘中/已暂停 → 已完成。</li>
 *     <li>{@code close}：草稿/已提交/招聘中/已暂停 → 已关闭（必须填原因）。</li>
 * </ul>
 *
 * <p><b>变更留痕</b>：创建、每次状态流转，以及进入招聘中之后修改关键字段
 * （人数、岗位、部门、负责人、到岗日期）都追加一条 {@code hr_recruit_demand_change}，
 * 其中 {@code before_json} / {@code after_json} 只含关键字段，不含任何敏感信息。</p>
 *
 * <p><b>并发</b>：实体带 {@code @Version}，更新命中 0 行即判定版本冲突并给出中文提示。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitDemandServiceImpl implements IRecruitDemandService {

    /**
     * 动作：提交。
     */
    private static final String ACTION_SUBMIT = "submit";

    /**
     * 动作：提交确认（进入招聘中）。
     */
    private static final String ACTION_CONFIRM = "confirm";

    /**
     * 动作：暂停。
     */
    private static final String ACTION_PAUSE = "pause";

    /**
     * 动作：复开。
     */
    private static final String ACTION_RESUME = "resume";

    /**
     * 动作：完成。
     */
    private static final String ACTION_COMPLETE = "complete";

    /**
     * 动作：关闭。
     */
    private static final String ACTION_CLOSE = "close";

    /**
     * 变更类型：新增需求。
     */
    private static final String CHANGE_CREATE = "create";

    /**
     * 变更类型：关键字段修改。
     */
    private static final String CHANGE_UPDATE = "update";

    /**
     * 变更类型：提交。
     */
    private static final String CHANGE_SUBMIT = "submit";

    /**
     * 变更类型：进入招聘中（提交确认）。
     */
    private static final String CHANGE_CONFIRM = "confirm";

    /**
     * 变更类型：暂停。
     */
    private static final String CHANGE_PAUSE = "pause";

    /**
     * 变更类型：复开。
     */
    private static final String CHANGE_RESUME = "resume";

    /**
     * 变更类型：完成。
     */
    private static final String CHANGE_COMPLETE = "complete";

    /**
     * 变更类型：关闭。
     */
    private static final String CHANGE_CLOSE = "close";

    /**
     * 动作 → 权限标识映射（权限串一律取 {@link HrTalentConstants}，禁止裸字符串）。
     */
    private static final Map<String, String> ACTION_PERMISSIONS = Map.of(
        ACTION_SUBMIT, HrTalentConstants.PERM_DEMAND_SUBMIT,
        ACTION_CONFIRM, HrTalentConstants.PERM_DEMAND_SUBMIT,
        ACTION_PAUSE, HrTalentConstants.PERM_DEMAND_PAUSE,
        ACTION_RESUME, HrTalentConstants.PERM_DEMAND_PAUSE,
        ACTION_COMPLETE, HrTalentConstants.PERM_DEMAND_CLOSE,
        ACTION_CLOSE, HrTalentConstants.PERM_DEMAND_CLOSE);

    /**
     * 必须填写原因的动作集合（暂停、复开、关闭）。
     */
    private static final List<String> REASON_REQUIRED_ACTIONS = List.of(ACTION_PAUSE, ACTION_RESUME, ACTION_CLOSE);

    /**
     * 需求 Mapper。
     */
    private final RecruitDemandMapper demandMapper;

    /**
     * 需求变更历史 Mapper。
     */
    private final RecruitDemandChangeMapper demandChangeMapper;

    /**
     * 业务编号生成器（需求编号唯一事实来源）。
     */
    private final RecruitBusinessNoGenerator businessNoGenerator;

    @Override
    public PageResult<RecruitDemandVo> queryPage(RecruitDemandQueryBo bo, PageQuery pageQuery) {
        RecruitDemandQueryBo query = bo == null ? new RecruitDemandQueryBo() : bo;
        PageQuery page = pageQuery == null ? new PageQuery() : pageQuery;
        LambdaQueryWrapper<RecruitDemand> wrapper = new LambdaQueryWrapper<RecruitDemand>()
            .like(StringUtils.isNotBlank(query.getDemandNo()), RecruitDemand::getDemandNo, query.getDemandNo())
            .like(StringUtils.isNotBlank(query.getDemandTitle()), RecruitDemand::getDemandTitle, query.getDemandTitle())
            .eq(query.getCompanyDeptId() != null, RecruitDemand::getCompanyDeptId, query.getCompanyDeptId())
            .eq(query.getUseDeptId() != null, RecruitDemand::getUseDeptId, query.getUseDeptId())
            .eq(query.getRecruiterId() != null, RecruitDemand::getRecruiterId, query.getRecruiterId())
            .like(StringUtils.isNotBlank(query.getJobName()), RecruitDemand::getJobName, query.getJobName())
            .eq(StringUtils.isNotBlank(query.getStatus()), RecruitDemand::getStatus, query.getStatus())
            .eq(StringUtils.isNotBlank(query.getUrgency()), RecruitDemand::getUrgency, query.getUrgency())
            .eq(StringUtils.isNotBlank(query.getRecruitMode()), RecruitDemand::getRecruitMode, query.getRecruitMode())
            .like(StringUtils.isNotBlank(query.getWorkCity()), RecruitDemand::getWorkCity, query.getWorkCity())
            .ge(query.getApplyDateBegin() != null, RecruitDemand::getApplyDate, query.getApplyDateBegin())
            .le(query.getApplyDateEnd() != null, RecruitDemand::getApplyDate, query.getApplyDateEnd())
            .orderByDesc(RecruitDemand::getCreateTime);
        Page<RecruitDemandVo> voPage = demandMapper.selectPageDemandList(page.build(), wrapper);
        if (voPage.getRecords() != null) {
            voPage.getRecords().forEach(this::fillDerivedFields);
        }
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public RecruitDemandVo getDetail(Long demandId) {
        if (demandId == null) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_001);
        }
        RecruitDemandVo vo = demandMapper.selectVoById(demandId);
        if (vo == null) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_001);
        }
        fillDerivedFields(vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RecruitDemandBo bo) {
        validateBusinessRules(bo.getDemandCount(), bo.getApplyDate(), bo.getExpectArrivalDate());
        RecruitDemand entity = MapstructUtils.convert(bo, RecruitDemand.class);
        if (entity == null) {
            entity = new RecruitDemand();
        }
        // 服务端权威字段：编号、状态、到岗人数、版本一律不接受前端写入
        entity.setDemandId(null);
        entity.setDemandNo(businessNoGenerator.nextDemandNo(bo.getApplyDate()));
        entity.setStatus(DemandStatusEnum.DRAFT.getCode());
        entity.setHiredCount(0);
        entity.setVersion(0);
        if (StringUtils.isBlank(entity.getUrgency())) {
            entity.setUrgency(UrgencyEnum.NORMAL.getCode());
        }
        demandMapper.insert(entity);
        RecruitDemand saved = demandMapper.selectById(entity.getDemandId());
        writeChange(saved, CHANGE_CREATE, null, buildSnapshot(saved), null, "创建招聘需求");
        log.info("新增招聘需求, demandId={}, demandNo={}", saved.getDemandId(), saved.getDemandNo());
        return saved.getDemandId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RecruitDemandBo bo) {
        RecruitDemand exist = loadDemand(bo.getDemandId());
        DemandStatusEnum current = requireStatus(exist.getStatus());
        if (DemandStatusEnum.COMPLETED == current || DemandStatusEnum.CLOSED == current) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_002 + "：已完成或已关闭的需求不可修改");
        }
        validateBusinessRules(
            bo.getDemandCount() == null ? exist.getDemandCount() : bo.getDemandCount(),
            bo.getApplyDate() == null ? exist.getApplyDate() : bo.getApplyDate(),
            bo.getExpectArrivalDate() == null ? exist.getExpectArrivalDate() : bo.getExpectArrivalDate());

        Map<String, Object> before = buildSnapshot(exist);
        RecruitDemand update = MapstructUtils.convert(bo, RecruitDemand.class);
        if (update == null) {
            update = new RecruitDemand();
        }
        update.setDemandId(exist.getDemandId());
        // 乐观锁：以入参版本号为准，缺失时直接判定为参数非法（BO 已分组校验，此处兜底）
        if (bo.getVersion() == null) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_003);
        }
        update.setVersion(bo.getVersion());
        int rows = demandMapper.updateById(update);
        if (rows == 0) {
            log.warn("招聘需求更新版本冲突, demandId={}, version={}", exist.getDemandId(), bo.getVersion());
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_003);
        }
        // 进入招聘中之后的关键字段修改必须留痕
        if (enteredRecruiting(current)) {
            RecruitDemand latest = demandMapper.selectById(exist.getDemandId());
            Map<String, Object> after = buildSnapshot(latest);
            if (!before.equals(after)) {
                writeChange(latest, CHANGE_UPDATE, before, after, bo.getReason(), "关键字段变更");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long[] demandIds) {
        if (demandIds == null || demandIds.length == 0) {
            return;
        }
        List<Long> ids = Arrays.stream(demandIds).distinct().toList();
        List<RecruitDemand> demands = demandMapper.selectByIds(ids);
        // 仅草稿可删，避免误删已进入执行的需求
        for (RecruitDemand demand : demands) {
            if (DemandStatusEnum.DRAFT != requireStatus(demand.getStatus())) {
                throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_004);
            }
        }
        demandMapper.deleteByIds(ids);
        log.info("逻辑删除招聘需求, demandIds={}", ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void action(Long demandId, String action, RecruitDemandActionBo bo) {
        String code = action == null ? "" : action.trim();
        String permission = ACTION_PERMISSIONS.get(code);
        if (permission == null) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_002 + "：不支持的动作 " + code);
        }
        // 动作级精确鉴权：控制器只做粗粒度放行，真正权限在此判定
        if (!LoginHelper.isSuperAdmin() && !StpUtil.hasPermissionOr(permission)) {
            log.warn("招聘需求动作鉴权未通过, demandId={}, action={}, userId={}", demandId, code, LoginHelper.getUserId());
            throw new ServiceException("无该招聘需求动作的操作权限");
        }
        RecruitDemandActionBo param = bo == null ? new RecruitDemandActionBo() : bo;
        if (REASON_REQUIRED_ACTIONS.contains(code) && StringUtils.isBlank(param.getReason())) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_005);
        }
        RecruitDemand demand = loadDemand(demandId);
        if (param.getVersion() != null && !param.getVersion().equals(demand.getVersion())) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_003);
        }
        DemandStatusEnum current = requireStatus(demand.getStatus());
        Map<String, Object> before = buildSnapshot(demand);
        RecruitDemand update = new RecruitDemand();
        update.setDemandId(demand.getDemandId());
        update.setVersion(demand.getVersion());
        if (StringUtils.isNotBlank(param.getRemark())) {
            update.setRemark(param.getRemark());
        }
        String changeType;
        LocalDateTime now = LocalDateTime.now();
        Long userId = LoginHelper.getUserId();
        switch (code) {
            case ACTION_SUBMIT -> {
                if (DemandStatusEnum.DRAFT != current) {
                    throw illegalTransition(current, code);
                }
                update.setStatus(DemandStatusEnum.SUBMITTED.getCode());
                update.setSubmittedBy(userId);
                update.setSubmittedTime(now);
                changeType = CHANGE_SUBMIT;
            }
            case ACTION_CONFIRM -> {
                if (DemandStatusEnum.SUBMITTED != current) {
                    throw illegalTransition(current, code);
                }
                update.setStatus(DemandStatusEnum.RECRUITING.getCode());
                update.setConfirmedBy(userId);
                update.setConfirmedTime(now);
                changeType = CHANGE_CONFIRM;
            }
            case ACTION_PAUSE -> {
                if (DemandStatusEnum.RECRUITING != current) {
                    throw illegalTransition(current, code);
                }
                update.setStatus(DemandStatusEnum.PAUSED.getCode());
                changeType = CHANGE_PAUSE;
            }
            case ACTION_RESUME -> {
                if (DemandStatusEnum.PAUSED != current) {
                    throw illegalTransition(current, code);
                }
                update.setStatus(DemandStatusEnum.RECRUITING.getCode());
                update.setConfirmedBy(userId);
                update.setConfirmedTime(now);
                changeType = CHANGE_RESUME;
            }
            case ACTION_COMPLETE -> {
                if (DemandStatusEnum.RECRUITING != current && DemandStatusEnum.PAUSED != current) {
                    throw illegalTransition(current, code);
                }
                update.setStatus(DemandStatusEnum.COMPLETED.getCode());
                changeType = CHANGE_COMPLETE;
            }
            case ACTION_CLOSE -> {
                update.setStatus(DemandStatusEnum.CLOSED.getCode());
                update.setClosedBy(userId);
                update.setClosedTime(now);
                changeType = CHANGE_CLOSE;
            }
            default -> throw illegalTransition(current, code);
        }
        int rows = demandMapper.updateById(update);
        if (rows == 0) {
            log.warn("招聘需求动作版本冲突, demandId={}, action={}", demandId, code);
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_003);
        }
        RecruitDemand latest = demandMapper.selectById(demandId);
        writeChange(latest, changeType, before, buildSnapshot(latest), param.getReason(), "需求状态动作：" + code);
        log.info("招聘需求动作完成, demandId={}, action={}, from={}, to={}",
            demandId, code, current.getCode(), latest.getStatus());
    }

    @Override
    public List<RecruitDemandChangeVo> queryChanges(Long demandId) {
        loadDemand(demandId);
        LambdaQueryWrapper<RecruitDemandChange> wrapper = new LambdaQueryWrapper<RecruitDemandChange>()
            .eq(RecruitDemandChange::getDemandId, demandId)
            .orderByDesc(RecruitDemandChange::getOperateTime)
            .orderByDesc(RecruitDemandChange::getChangeId);
        return demandChangeMapper.selectVoList(wrapper);
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 加载需求，不存在时抛中文提示异常。
     *
     * @param demandId 需求ID
     * @return 需求实体
     */
    private RecruitDemand loadDemand(Long demandId) {
        if (demandId == null) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_001);
        }
        RecruitDemand demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_001);
        }
        return demand;
    }

    /**
     * 解析状态编码，未知编码一律按非法处理（fail-safe）。
     *
     * @param status 状态编码
     * @return 状态枚举
     */
    private DemandStatusEnum requireStatus(String status) {
        DemandStatusEnum item = DemandStatusEnum.find(status);
        if (item == null) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_DEMAND_002 + "：未知的需求状态 " + status);
        }
        return item;
    }

    /**
     * 构造非法流转异常。
     *
     * @param current 当前状态
     * @param action  动作编码
     * @return 异常对象
     */
    private ServiceException illegalTransition(DemandStatusEnum current, String action) {
        return new ServiceException(
            HrTalentErrorCode.MSG_HR_DEMAND_002 + "：" + current.getDesc() + " 不允许执行 " + action);
    }

    /**
     * 校验业务规则（§4.1）。
     *
     * @param demandCount       需求人数
     * @param applyDate         申请日期
     * @param expectArrivalDate 计划到岗日期
     */
    private void validateBusinessRules(Integer demandCount, LocalDate applyDate, LocalDate expectArrivalDate) {
        if (demandCount == null || demandCount <= 0) {
            throw new ServiceException("需求人数必须大于 0");
        }
        if (applyDate != null && expectArrivalDate != null && expectArrivalDate.isBefore(applyDate)) {
            throw new ServiceException("计划到岗日期不得早于申请日期");
        }
    }

    /**
     * 是否已进入招聘中（招聘中 / 已暂停均视为已进入，完成后不可编辑）。
     *
     * @param status 当前状态
     * @return 是否已进入招聘中
     */
    private boolean enteredRecruiting(DemandStatusEnum status) {
        return DemandStatusEnum.RECRUITING == status || DemandStatusEnum.PAUSED == status;
    }

    /**
     * 计算派生字段：待招聘人数 = max(需求人数 - 已到岗人数, 0)。
     *
     * @param vo 需求视图对象
     */
    private void fillDerivedFields(RecruitDemandVo vo) {
        int demandCount = vo.getDemandCount() == null ? 0 : vo.getDemandCount();
        int hiredCount = vo.getHiredCount() == null ? 0 : vo.getHiredCount();
        vo.setRemainingCount(Math.max(demandCount - hiredCount, 0));
    }

    /**
     * 构造关键字段快照（用于变更历史，仅关键字段，不含敏感信息）。
     *
     * @param demand 需求实体
     * @return 关键字段快照（有序）
     */
    private Map<String, Object> buildSnapshot(RecruitDemand demand) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("demandCount", demand.getDemandCount());
        snapshot.put("jobName", demand.getJobName());
        snapshot.put("useDeptId", demand.getUseDeptId());
        snapshot.put("recruiterId", demand.getRecruiterId());
        snapshot.put("expectArrivalDate", demand.getExpectArrivalDate());
        snapshot.put("status", demand.getStatus());
        return snapshot;
    }

    /**
     * 追加一条变更历史。
     *
     * @param demand     需求实体（记录快照的来源）
     * @param changeType 变更类型
     * @param before     变更前快照，可为 null
     * @param after      变更后快照，可为 null
     * @param reason     变更原因，可为 null
     * @param remark     备注
     */
    private void writeChange(RecruitDemand demand, String changeType, Map<String, Object> before,
                             Map<String, Object> after, String reason, String remark) {
        RecruitDemandChange change = new RecruitDemandChange();
        change.setDemandId(demand.getDemandId());
        change.setChangeType(changeType);
        change.setBeforeJson(before == null ? null : JsonUtils.toJsonString(before));
        change.setAfterJson(after == null ? null : JsonUtils.toJsonString(after));
        change.setReason(reason);
        change.setOperatorId(LoginHelper.getUserId());
        change.setOperateTime(LocalDateTime.now());
        change.setRemark(remark);
        demandChangeMapper.insert(change);
    }

}
