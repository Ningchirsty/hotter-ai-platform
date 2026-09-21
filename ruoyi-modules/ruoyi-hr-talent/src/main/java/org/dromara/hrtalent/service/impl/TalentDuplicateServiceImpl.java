package org.dromara.hrtalent.service.impl;

import cn.hutool.core.collection.CollUtil;
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
import org.dromara.hrtalent.domain.bo.talent.DuplicateConfirmBo;
import org.dromara.hrtalent.domain.bo.talent.DuplicateIgnoreBo;
import org.dromara.hrtalent.domain.bo.talent.TalentDuplicateQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentMergeBo;
import org.dromara.hrtalent.domain.bo.talent.TalentMergeFields;
import org.dromara.hrtalent.domain.entity.TalentDuplicateCase;
import org.dromara.hrtalent.domain.entity.TalentMergeLog;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.vo.talent.TalentDuplicateCaseVo;
import org.dromara.hrtalent.domain.vo.talent.TalentMergeLogVo;
import org.dromara.hrtalent.domain.vo.talent.TalentMergePreviewVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileVo;
import org.dromara.hrtalent.domainservice.ITalentMergeRelationMigrator;
import org.dromara.hrtalent.domainservice.TalentMergeRelationSnapshot;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.DuplicateMatchLevelEnum;
import org.dromara.hrtalent.enums.DuplicateStatusEnum;
import org.dromara.hrtalent.enums.TalentStatusEnum;
import org.dromara.hrtalent.event.TalentMergedEvent;
import org.dromara.hrtalent.mapper.TalentDuplicateCaseMapper;
import org.dromara.hrtalent.mapper.TalentMergeLogMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.service.talent.ITalentDuplicateService;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.dromara.hrtalent.support.TalentContactCodec;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 重复人才治理与合并服务实现（SPEC-P4 §2.5、设计文档 §8.18、§9.6、§11.1、§21.6、§21.7）。
 *
 * <p><b>合并的正确性要点（按实现顺序）</b>：</p>
 * <ol>
 *     <li><b>权限</b>：只有集团人才管理员（或超级管理员）可合并，判定统一走
 *     {@link TalentScopeDomainService}，本类不自写角色规则；</li>
 *     <li><b>版本</b>：两个主档都必须回传并校验 {@code version} 乐观锁（§9.6）；</li>
 *     <li><b>锁</b>：分布式锁由 Controller 层 {@code @Lock4j} 施加，键含两个人才ID，
 *     防止并发合并同一对人才；</li>
 *     <li><b>事务</b>：关系转移、冲突字段应用、主档标记、合并快照、审计记录
 *     全部在<b>同一个</b> {@code @Transactional} 方法内完成（§21.7）；</li>
 *     <li><b>不物理删除</b>：全程只有 UPDATE 与 INSERT，关系迁移入口
 *     {@link ITalentMergeRelationMigrator} 不提供任何删除能力（§8.18）；</li>
 *     <li><b>可追溯</b>：{@code hr_talent_merge_log} 保存冲突字段决策与迁移前后关系数量，
 *     审计经 {@link SensitiveAuditRecorder} 统一落库。</li>
 * </ol>
 *
 * <p><b>测试可见性</b>：权限判定与登录用户读取收敛在 {@link #isMergeAllowed()} 与
 * {@link #currentUserId()} 两个方法中，单元测试可在无 Sa-Token 上下文时覆写它们，
 * 从而覆盖「非集团管理员不得合并」的行为。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentDuplicateServiceImpl implements ITalentDuplicateService {

    /**
     * 处理动作：确认「是同一人」。
     */
    private static final String ACTION_CONFIRM = "confirm";

    /**
     * 处理动作：忽略。
     */
    private static final String ACTION_IGNORE = "ignore";

    /**
     * 冲突字段取值来源：保留主档。
     */
    private static final String FROM_KEEP = "keep";

    /**
     * 冲突字段取值来源：被合并主档。
     */
    private static final String FROM_MERGED = "merged";

    /**
     * 审计事件类型：人才合并。
     */
    private static final String EVENT_TALENT_MERGE = "talent_merge";

    /**
     * 疑似重复案件 Mapper。
     */
    private final TalentDuplicateCaseMapper talentDuplicateCaseMapper;

    /**
     * 人才合并日志 Mapper。
     */
    private final TalentMergeLogMapper talentMergeLogMapper;

    /**
     * 人才主档 Mapper（合并只写主档状态与冲突字段，不重复实现人才域 CRUD）。
     */
    private final TalentProfileMapper talentProfileMapper;

    /**
     * 合并关系迁移领域服务（唯一跨表改归属入口，绝不物理删除）。
     */
    private final ITalentMergeRelationMigrator talentMergeRelationMigrator;

    /**
     * 人才可见范围与授权级别领域服务（唯一授权入口）。
     */
    private final TalentScopeDomainService talentScopeDomainService;

    /**
     * 敏感操作审计记录器（永不抛异常，统一入口）。
     */
    private final SensitiveAuditRecorder sensitiveAuditRecorder;

    /**
     * 领域事件发布器。
     */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<TalentDuplicateCaseVo> queryPage(TalentDuplicateQueryBo bo, PageQuery pageQuery) {
        List<Long> visibleTalentIds = resolveVisibleTalentIds();
        if (visibleTalentIds.isEmpty()) {
            // 无任何可见人才：直接返回空页，不查库（§21.14）
            return PageResult.build(List.of(), 0L);
        }
        TalentDuplicateQueryBo query = bo == null ? new TalentDuplicateQueryBo() : bo;
        LambdaQueryWrapper<TalentDuplicateCase> wrapper = new LambdaQueryWrapper<TalentDuplicateCase>()
            // 可见范围硬约束：主档A 或 主档B 至少一方可见
            .and(w -> w.in(TalentDuplicateCase::getSourceTalentId, visibleTalentIds)
                .or()
                .in(TalentDuplicateCase::getTargetTalentId, visibleTalentIds))
            .eq(StringUtils.isNotBlank(query.getStatus()), TalentDuplicateCase::getStatus, query.getStatus())
            .eq(StringUtils.isNotBlank(query.getMatchLevel()), TalentDuplicateCase::getMatchLevel, query.getMatchLevel())
            .eq(query.getSourceTalentId() != null, TalentDuplicateCase::getSourceTalentId, query.getSourceTalentId())
            .eq(query.getTargetTalentId() != null, TalentDuplicateCase::getTargetTalentId, query.getTargetTalentId())
            .ge(query.getCreateDateBegin() != null, TalentDuplicateCase::getCreateTime,
                query.getCreateDateBegin() == null ? null : query.getCreateDateBegin().atStartOfDay())
            .le(query.getCreateDateEnd() != null, TalentDuplicateCase::getCreateTime,
                query.getCreateDateEnd() == null ? null : query.getCreateDateEnd().atTime(23, 59, 59));
        if (StringUtils.isNotBlank(query.getName()) || StringUtils.isNotBlank(query.getTalentNo())) {
            // 姓名/编号需要联表过滤：先解析出可见且命中的主档ID，再用 IN 收敛（不引入 HAVING/JOIN 复杂度）
            List<Long> matchedTalentIds = talentProfileMapper.selectObjs(
                new LambdaQueryWrapper<TalentProfile>()
                    .select(TalentProfile::getTalentId)
                    .in(TalentProfile::getTalentId, visibleTalentIds)
                    .like(StringUtils.isNotBlank(query.getName()), TalentProfile::getName, query.getName())
                    .like(StringUtils.isNotBlank(query.getTalentNo()), TalentProfile::getTalentNo, query.getTalentNo()),
                id -> (Long) id);
            if (CollUtil.isEmpty(matchedTalentIds)) {
                return PageResult.build(List.of(), 0L);
            }
            wrapper.and(w -> w.in(TalentDuplicateCase::getSourceTalentId, matchedTalentIds)
                .or()
                .in(TalentDuplicateCase::getTargetTalentId, matchedTalentIds));
        }
        wrapper.orderByDesc(TalentDuplicateCase::getCreateTime)
            .orderByDesc(TalentDuplicateCase::getCaseId);
        Page<TalentDuplicateCaseVo> page = talentDuplicateCaseMapper.selectVoPage(pageQuery.build(), wrapper);
        List<TalentDuplicateCaseVo> records = page.getRecords();
        if (CollUtil.isNotEmpty(records)) {
            records.forEach(TalentDuplicateCaseVo::fillEnumLabels);
        }
        return PageResult.build(records, page.getTotal());
    }

    @Override
    public TalentMergePreviewVo preview(Long caseId) {
        TalentDuplicateCase duplicateCase = requireCase(caseId);
        TalentProfile keep = requireProfile(duplicateCase.getSourceTalentId());
        TalentProfile merged = requireProfile(duplicateCase.getTargetTalentId());

        TalentMergePreviewVo vo = new TalentMergePreviewVo();
        vo.setKeepTalent(mask(MapstructUtils.convert(keep, TalentProfileVo.class)));
        vo.setMergedTalent(mask(MapstructUtils.convert(merged, TalentProfileVo.class)));
        vo.setFieldDiffs(buildFieldDiffs(keep, merged));

        Map<String, Long> mergedCounts = talentMergeRelationMigrator.snapshot(merged.getTalentId()).counts();
        Map<String, Long> keepCounts = talentMergeRelationMigrator.snapshot(keep.getTalentId()).counts();
        vo.setRelations(buildRelationSummaries(mergedCounts, keepCounts));

        vo.getWarnings().add("合并后不可普通撤销，且不物理删除任何资料：如需回退请依据合并快照人工处理");
        vo.getWarnings().add("联系方式、人才编号、归属公司部门、可见范围与数据分级不参与合并选择，保留主档取值");
        vo.getWarnings().add("同一人才池/分组内若保留主档已存在相同关系，冲突关系会被跳过（保留主档已有关系优先）");
        vo.setMergeAllowed(isMergeAllowed());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long caseId, DuplicateConfirmBo bo) {
        if (bo == null || bo.getSamePerson() == null) {
            throw new ServiceException("请明确是否为同一人");
        }
        if (StringUtils.isBlank(bo.getReason())) {
            throw new ServiceException("确认依据不能为空");
        }
        TalentDuplicateCase duplicateCase = requireCase(caseId);
        requireProfile(duplicateCase.getSourceTalentId());
        requireProfile(duplicateCase.getTargetTalentId());
        if (!DuplicateStatusEnum.PENDING.getCode().equals(duplicateCase.getStatus())) {
            throw new ServiceException("该疑似重复记录已处理，不能重复确认");
        }
        // 「确认是同一人」→ confirmed（已确认待合并）：与 pending（待人工判定）区分开，
        // 待处理工作队列按 status = pending 过滤时不会再捞到已确认案件；
        // 「确认不是同一人」→ not_same，直接终结。
        boolean samePerson = Boolean.TRUE.equals(bo.getSamePerson());
        String targetStatus = samePerson
            ? DuplicateStatusEnum.CONFIRMED.getCode()
            : DuplicateStatusEnum.NOT_SAME.getCode();
        String remark = ACTION_CONFIRM + ":" + bo.getReason()
            + (bo.getReviewDate() == null ? "" : ",reviewDate=" + bo.getReviewDate());
        updateCaseStatus(duplicateCase, targetStatus, remark);
        sensitiveAuditRecorder.record(ACTION_CONFIRM, SensitiveAuditRecorder.BIZ_TALENT,
            duplicateCase.getCaseId(), bo.getReason(), SensitiveAuditRecorder.RESULT_SUCCESS,
            auditDetail(duplicateCase, samePerson ? "same_person" : "not_same_person"));
        log.info("疑似重复案件确认, caseId={}, samePerson={}", caseId, samePerson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ignore(Long caseId, DuplicateIgnoreBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getReason())) {
            throw new ServiceException("忽略原因不能为空");
        }
        TalentDuplicateCase duplicateCase = requireCase(caseId);
        requireProfile(duplicateCase.getSourceTalentId());
        requireProfile(duplicateCase.getTargetTalentId());
        if (!DuplicateStatusEnum.PENDING.getCode().equals(duplicateCase.getStatus())) {
            throw new ServiceException("该疑似重复记录已处理，不能重复忽略");
        }
        boolean notSamePerson = Boolean.TRUE.equals(bo.getNotSamePerson());
        String targetStatus = notSamePerson ? DuplicateStatusEnum.NOT_SAME.getCode() : DuplicateStatusEnum.IGNORED.getCode();
        updateCaseStatus(duplicateCase, targetStatus, ACTION_IGNORE + ":" + bo.getReason());
        sensitiveAuditRecorder.record(ACTION_IGNORE, SensitiveAuditRecorder.BIZ_TALENT,
            duplicateCase.getCaseId(), bo.getReason(), SensitiveAuditRecorder.RESULT_SUCCESS,
            auditDetail(duplicateCase, notSamePerson ? "not_same_person" : "ignored"));
        log.info("疑似重复案件忽略, caseId={}, notSamePerson={}", caseId, notSamePerson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long merge(Long caseId, TalentMergeBo bo) {
        if (bo == null) {
            throw new ServiceException("合并入参不能为空");
        }
        // 1) 权限：只有集团人才管理员（或超级管理员）可以执行合并（§8.18）
        if (!isMergeAllowed()) {
            sensitiveAuditRecorder.record(EVENT_TALENT_MERGE, SensitiveAuditRecorder.BIZ_TALENT,
                caseId, "无权执行人才合并", SensitiveAuditRecorder.RESULT_DENIED, null);
            throw new ServiceException("仅集团人才管理员可以执行人才合并");
        }
        // 2) 入参校验：两个主档必须不同，且版本号必须回传
        Long keepTalentId = bo.getKeepTalentId();
        Long mergedTalentId = bo.getMergedTalentId();
        if (keepTalentId == null || mergedTalentId == null) {
            throw new ServiceException("保留主档与被合并主档ID不能为空");
        }
        if (keepTalentId.equals(mergedTalentId)) {
            throw new ServiceException("保留主档与被合并主档不能是同一条人才记录");
        }
        if (bo.getKeepVersion() == null || bo.getMergedVersion() == null) {
            throw new ServiceException("合并必须回传两个主档的版本号，请刷新后重试");
        }
        Map<String, String> decisions = normalizeDecisions(bo);

        // 3) 加载两侧主档并做资源级可见性鉴权
        TalentProfile keep = requireProfile(keepTalentId);
        TalentProfile merged = requireProfile(mergedTalentId);
        requireMergeable(keep, "保留主档");
        requireMergeable(merged, "被合并主档");

        // 4) 乐观锁版本校验（§9.6）
        if (!Objects.equals(keep.getVersion(), bo.getKeepVersion())) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_004);
        }
        if (!Objects.equals(merged.getVersion(), bo.getMergedVersion())) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_004);
        }

        // 5) 案件与两侧主档的对应关系必须自洽（防止前端串号）
        if (caseId != null) {
            TalentDuplicateCase duplicateCase = requireCase(caseId);
            boolean pairMatched = (keepTalentId.equals(duplicateCase.getSourceTalentId())
                && mergedTalentId.equals(duplicateCase.getTargetTalentId()))
                || (keepTalentId.equals(duplicateCase.getTargetTalentId())
                && mergedTalentId.equals(duplicateCase.getSourceTalentId()));
            if (!pairMatched) {
                throw new ServiceException("疑似重复记录与提交的主档不一致，请刷新后重试");
            }
        }

        // 6) 迁移前关系数量快照（操作人提前取出：关系表为自定义 SQL，需显式回写 update_by）
        Long operatorId = currentUserId();
        Map<String, Long> beforeCounts = talentMergeRelationMigrator.snapshot(mergedTalentId).counts();

        // 7) 同一事务内转移全部关系（改归属，绝不物理删除；显式回写 update_by/update_time）
        Map<String, Long> movedCounts = talentMergeRelationMigrator.migrate(keepTalentId, mergedTalentId, operatorId);

        // 8) 人工选择冲突字段保留值（在保留主档上落地）
        Map<String, Object> fieldDecisionJson = applyFieldDecisions(keep, merged, decisions);

        // 9) 被合并主档标记 merged 并保存目标主档ID
        //    顺序说明：先写入「确定的终态字段」，再调用带 @Version 的 updateById；
        //    失败（0 行）时<b>不</b>改动实体上的状态字段，避免事务回滚后在内存/日志里留下「已合并」假象。
        merged.setVersion(bo.getMergedVersion());
        merged.setMergedToId(keepTalentId);
        merged.setStatusReason("已合并至人才主档 " + keep.getTalentNo());
        merged.setTalentStatus(TalentStatusEnum.MERGED.getCode());
        int mergedRows = talentProfileMapper.updateById(merged);
        if (mergedRows == 0) {
            // 版本冲突：恢复实体内存快照，保持与数据库（事务将回滚）一致
            merged.setTalentStatus(TalentStatusEnum.ACTIVE.getCode());
            merged.setMergedToId(null);
            merged.setStatusReason(null);
            log.warn("人才合并版本冲突（被合并主档）, talentId={}, version={}", mergedTalentId, bo.getMergedVersion());
            throw new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_004);
        }
        if (!decisions.isEmpty()) {
            keep.setVersion(bo.getKeepVersion());
            int keepRows = talentProfileMapper.updateById(keep);
            if (keepRows == 0) {
                log.warn("人才合并版本冲突（保留主档）, talentId={}, version={}", keepTalentId, bo.getKeepVersion());
                throw new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_004);
            }
        }

        // 10) 迁移后关系数量快照（正常应全部为 0）
        Map<String, Long> afterCounts = talentMergeRelationMigrator.snapshot(mergedTalentId).counts();

        // 11) 写入完整合并快照
        LocalDateTime now = LocalDateTime.now();
        TalentMergeLog mergeLog = new TalentMergeLog();
        mergeLog.setKeepTalentId(keepTalentId);
        mergeLog.setMergedTalentId(mergedTalentId);
        mergeLog.setFieldDecisionJson(toJson(fieldDecisionJson));
        mergeLog.setRelationCountJson(toJson(relationCountSnapshot(beforeCounts, movedCounts, afterCounts)));
        mergeLog.setMergeReason(bo.getMergeReason());
        mergeLog.setOperatorId(operatorId);
        mergeLog.setOperateTime(now);
        talentMergeLogMapper.insert(mergeLog);

        // 12) 关闭相关疑似重复案件
        int closedCases = closeDuplicateCases(caseId, keepTalentId, mergedTalentId, operatorId, now);

        // 13) 审计留痕
        Map<String, Object> mergeAuditDetail = new LinkedHashMap<>();
        mergeAuditDetail.put("keepTalentId", keepTalentId);
        mergeAuditDetail.put("mergedTalentId", mergedTalentId);
        mergeAuditDetail.put("mergeId", mergeLog.getMergeId());
        mergeAuditDetail.put("closedCases", closedCases);
        mergeAuditDetail.put("movedRelations", movedCounts);
        mergeAuditDetail.put("residualRelations", afterCounts);
        sensitiveAuditRecorder.record(EVENT_TALENT_MERGE, SensitiveAuditRecorder.BIZ_TALENT, keepTalentId,
            bo.getMergeReason(), SensitiveAuditRecorder.RESULT_SUCCESS,
            toJson(mergeAuditDetail));

        // 14) 发布领域事件（供缓存失效、搜索索引与审计消费，§21.6）
        publishMerged(keepTalentId, mergedTalentId, mergeLog.getMergeId(), operatorId);

        log.info("人才合并完成, mergeId={}, keepTalentId={}, mergedTalentId={}, closedCases={}",
            mergeLog.getMergeId(), keepTalentId, mergedTalentId, closedCases);
        return mergeLog.getMergeId();
    }

    @Override
    public PageResult<TalentMergeLogVo> queryMergeLogs(Long keepTalentId, Long mergedTalentId, PageQuery pageQuery) {
        if (keepTalentId == null && mergedTalentId == null) {
            throw new ServiceException("请至少指定保留主档或被合并主档");
        }
        if (keepTalentId != null) {
            requireProfile(keepTalentId);
        }
        if (mergedTalentId != null) {
            requireProfile(mergedTalentId);
        }
        LambdaQueryWrapper<TalentMergeLog> wrapper = new LambdaQueryWrapper<TalentMergeLog>()
            .eq(keepTalentId != null, TalentMergeLog::getKeepTalentId, keepTalentId)
            .eq(mergedTalentId != null, TalentMergeLog::getMergedTalentId, mergedTalentId)
            .orderByDesc(TalentMergeLog::getOperateTime)
            .orderByDesc(TalentMergeLog::getMergeId);
        Page<TalentMergeLogVo> page = talentMergeLogMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /* ------------------------------------------------------------------ 权限与登录态 ------------------------------------------------------------------ */

    /**
     * 当前用户是否允许执行人才合并。
     *
     * <p><b>唯一判定入口</b>：集团级管理员或超级管理员（§8.18）。
     * 角色判定复用 {@link TalentScopeDomainService#isGroupLevelAdmin()}，
     * 本类<b>不</b>自行读取角色串或部门，避免出现第二套授权规则（§11.1）。</p>
     *
     * <p>无登录上下文（如定时任务、单元测试）时按「无权限」fail-safe 处理。</p>
     *
     * @return 是否允许合并
     */
    protected boolean isMergeAllowed() {
        if (LoginHelper.isSuperAdmin()) {
            return true;
        }
        return talentScopeDomainService.isGroupLevelAdmin();
    }

    /**
     * 取当前登录用户ID；无登录态返回 null。
     *
     * @return 用户ID或 null
     */
    protected Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 合并快照与审计明细的 JSON 序列化入口。
     *
     * <p>生产路径固定使用全仓统一的 {@code JsonUtils}（由 Spring 容器提供 {@code JsonMapper}）；
     * 单独抽出本方法并在首次调用时才触发 {@code JsonUtils} 的静态初始化，是为了让单元测试能在
     * <b>无 Spring 上下文</b>时覆写为可控实现 —— {@code JsonUtils} 的静态 {@code SpringUtils.getBean}
     * 在无上下文时会抛 {@code ExceptionInInitializerError}，且类初始化失败会永久污染该 JVM 中的类。</p>
     *
     * @param value 待序列化对象
     * @return JSON 字符串；入参为 null 时返回 null
     */
    protected String toJson(Object value) {
        return value == null ? null : JsonSupport.toJsonString(value);
    }

    /**
     * JSON 序列化出口持有者（静态内部类，保证 {@code JsonUtils} 只在真正序列化时才被初始化）。
     *
     * @author hr-talent
     */
    private static final class JsonSupport {

        /**
         * 工具类禁止实例化。
         */
        private JsonSupport() {
        }

        /**
         * 使用全仓统一的 JSON 工具序列化。
         *
         * @param value 待序列化对象
         * @return JSON 字符串
         */
        private static String toJsonString(Object value) {
            return JsonUtils.toJsonString(value);
        }

    }

    /* ------------------------------------------------------------------ 内部方法：主档与案件 ------------------------------------------------------------------ */

    /**
     * 加载疑似重复案件并做资源级鉴权。
     *
     * @param caseId 案件ID
     * @return 案件实体
     */
    private TalentDuplicateCase requireCase(Long caseId) {
        if (caseId == null) {
            throw new ServiceException("疑似重复记录ID不能为空");
        }
        TalentDuplicateCase duplicateCase = talentDuplicateCaseMapper.selectById(caseId);
        if (duplicateCase == null) {
            throw new ServiceException("疑似重复记录不存在");
        }
        return duplicateCase;
    }

    /**
     * 加载人才主档并执行统一的资源级可见性鉴权。
     *
     * <p>声明为 {@code protected} 是为了让单元测试能在<b>无 Sa-Token 上下文</b>时替换加载逻辑：
     * {@link TalentScopeDomainService#checkTalentVisible} 依赖登录会话，
     * 而「版本冲突被拒绝」「不物理删除」等断言属调用序列层面的规则，
     * 不应因为测试环境没有 Web 容器而无法覆盖（生产路径不做任何覆写）。</p>
     *
     * @param talentId 人才主档ID
     * @return 人才主档实体
     */
    protected TalentProfile requireProfile(Long talentId) {
        if (talentId == null) {
            throw new ServiceException("人才ID不能为空");
        }
        TalentProfile profile = talentProfileMapper.selectById(talentId);
        if (profile == null) {
            throw new ServiceException(TalentScopeDomainService.TALENT_NOT_FOUND);
        }
        talentScopeDomainService.checkTalentVisible(toScopeTarget(profile));
        return profile;
    }

    /**
     * 校验主档处于可合并状态（未逻辑删除、未合并、非同一人）。
     *
     * @param profile 人才主档
     * @param label   主档角色中文名，用于提示语
     */
    private void requireMergeable(TalentProfile profile, String label) {
        if (TalentStatusEnum.MERGED.getCode().equals(profile.getTalentStatus())) {
            throw new ServiceException(label + "已是合并终态（" + HrTalentErrorCode.MSG_HR_TALENT_003 + "）");
        }
    }

    /**
     * 把主档转换为可见性快照。
     *
     * @param profile 人才主档
     * @return 可见性快照
     */
    private TalentScopeDomainService.TalentScopeTarget toScopeTarget(TalentProfile profile) {
        return new TalentScopeDomainService.TalentScopeTarget(
            profile.getTalentId(), profile.getOwnerId(), profile.getOwnerDeptId(),
            profile.getVisibilityType(), profile.getTalentStatus(), profile.getDelFlag());
    }

    /**
     * 解析当前用户可见的人才ID集合（唯一接法：原样使用领域服务产出的 wrapper）。
     *
     * <p>声明为 {@code protected} 是为了让单元测试能在<b>无 Sa-Token 上下文</b>时替换可见范围解析；
     * 可见范围本身的正确性由 {@code TalentScopeDomainServiceTest} 覆盖。</p>
     *
     * @return 可见人才ID列表（不为 null，可能为空）
     */
    protected List<Long> resolveVisibleTalentIds() {
        List<Long> ids = talentProfileMapper.selectVisibleTalentIds(talentScopeDomainService.visibleTalentWrapper());
        return ids == null ? List.of() : ids;
    }

    /**
     * 更新疑似重复案件的处理状态与处理人。
     *
     * @param duplicateCase 案件实体
     * @param targetStatus  目标状态
     * @param remark        处理说明
     */
    private void updateCaseStatus(TalentDuplicateCase duplicateCase, String targetStatus, String remark) {
        duplicateCase.setStatus(targetStatus);
        duplicateCase.setHandledBy(currentUserId());
        duplicateCase.setHandledTime(LocalDateTime.now());
        duplicateCase.setRemark(remark);
        int rows = talentDuplicateCaseMapper.updateById(duplicateCase);
        if (rows == 0) {
            throw new ServiceException("疑似重复记录状态更新失败，请刷新后重试");
        }
    }

    /**
     * 关闭与本次合并相关的疑似重复案件（两主档之间所有待处理案件一并置为已合并）。
     *
     * <p>使用 {@link TalentDuplicateCaseMapper#closePendingCases} 的自定义 SQL 完成批量更新，
     * 不逐条 {@code updateById}（避免 N 次往返），也不触碰已忽略/非同一人的历史案件。</p>
     *
     * @param caseId         当前案件ID，可为 null
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param operatorId     操作人用户ID
     * @param now            操作时间（当前 SQL 由数据库取时间，本参数仅用于日志口径）
     * @return 关闭的案件数
     */
    private int closeDuplicateCases(Long caseId, Long keepTalentId, Long mergedTalentId,
                                    Long operatorId, LocalDateTime now) {
        int rows = talentDuplicateCaseMapper.closePendingCases(keepTalentId, mergedTalentId, operatorId);
        if (caseId != null && rows == 0) {
            // 案件可能已被其他管理员处理：不阻断合并，但保留告警日志
            log.warn("合并后未更新到疑似重复案件, caseId={}, keepTalentId={}, mergedTalentId={}, operateTime={}",
                caseId, keepTalentId, mergedTalentId, now);
        }
        return rows;
    }

    /* ------------------------------------------------------------------ 内部方法：冲突字段决策 ------------------------------------------------------------------ */

    /**
     * 规范化冲突字段决策：只接受白名单字段与 keep/merged 两种来源。
     *
     * @param bo 合并入参
     * @return 字段名 → 取值来源
     */
    private Map<String, String> normalizeDecisions(TalentMergeBo bo) {
        Map<String, String> decisions = new LinkedHashMap<>();
        if (CollUtil.isEmpty(bo.getFieldDecisions())) {
            return decisions;
        }
        for (TalentMergeBo.FieldDecision decision : bo.getFieldDecisions()) {
            if (decision == null || StringUtils.isBlank(decision.getField())) {
                continue;
            }
            String field = decision.getField().trim();
            if (!TalentMergeFields.allowed(field)) {
                throw new ServiceException("不允许在合并中选择该字段的取值：" + field);
            }
            String from = StringUtils.isBlank(decision.getFrom()) ? FROM_KEEP : decision.getFrom().trim().toLowerCase();
            if (!FROM_KEEP.equals(from) && !FROM_MERGED.equals(from)) {
                throw new ServiceException("字段 " + field + " 的取值来源只能为 keep 或 merged");
            }
            decisions.put(field, from);
        }
        return decisions;
    }

    /**
     * 应用冲突字段决策。
     *
     * <p><b>冲突口径</b>：只有「两侧都有值且不相同」才算冲突；无冲突字段一律保留保留主档现值，
     * 未出现在入参里的冲突字段同样保留保留主档现值（保守策略，不因漏选而丢数据）。</p>
     *
     * @param keep      保留主档实体（会被就地修改）
     * @param merged    被合并主档实体
     * @param decisions 字段 → 取值来源
     * @return 字段决策快照（可 JSON 序列化）
     */
    private Map<String, Object> applyFieldDecisions(TalentProfile keep, TalentProfile merged,
                                                    Map<String, String> decisions) {
        Map<String, Function<TalentProfile, Object>> getters = fieldGetters();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Function<TalentProfile, Object>> entry : getters.entrySet()) {
            String field = entry.getKey();
            Function<TalentProfile, Object> getter = entry.getValue();
            Object keepValue = getter.apply(keep);
            Object mergedValue = getter.apply(merged);
            if (isBlankValue(keepValue) || isBlankValue(mergedValue) || Objects.equals(keepValue, mergedValue)) {
                // 非冲突字段：保留主档有值则维持原值；保留主档为空而被合并主档有值时补全（不丢资料）
                if (isBlankValue(keepValue) && !isBlankValue(mergedValue)) {
                    applyField(keep, field, mergedValue);
                    snapshot.put(field, decisionItem(field, FROM_MERGED, mergedValue, keepValue, false));
                }
                continue;
            }
            String from = decisions.getOrDefault(field, FROM_KEEP);
            Object chosen = FROM_MERGED.equals(from) ? mergedValue : keepValue;
            if (FROM_MERGED.equals(from)) {
                applyField(keep, field, chosen);
            }
            snapshot.put(field, decisionItem(field, from, chosen, FROM_MERGED.equals(from) ? keepValue : mergedValue, true));
        }
        return snapshot;
    }

    /**
     * 构造单个字段的决策快照。
     *
     * @param field    字段名
     * @param from     取值来源
     * @param chosen   最终采用值
     * @param rejected 落选值
     * @param conflict 是否为冲突字段
     * @return 决策快照
     */
    private Map<String, Object> decisionItem(String field, String from, Object chosen, Object rejected, boolean conflict) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", TalentMergeFields.label(field));
        item.put("from", from);
        item.put("value", chosen);
        item.put("rejectedValue", rejected);
        item.put("conflict", conflict);
        return item;
    }

    /**
     * 字段名 → 取值函数（仅白名单字段；使用 javabean getter 反射读取，避免手写 10 段 if）。
     *
     * @return 字段取值函数表（有序）
     */
    private Map<String, Function<TalentProfile, Object>> fieldGetters() {
        Map<String, Function<TalentProfile, Object>> getters = new LinkedHashMap<>();
        getters.put(TalentMergeFields.NAME, TalentProfile::getName);
        getters.put(TalentMergeFields.GENDER, TalentProfile::getGender);
        getters.put(TalentMergeFields.HIGHEST_EDUCATION, TalentProfile::getHighestEducation);
        getters.put(TalentMergeFields.CURRENT_CITY, TalentProfile::getCurrentCity);
        getters.put(TalentMergeFields.EXPECTED_CITY, TalentProfile::getExpectedCity);
        getters.put(TalentMergeFields.CURRENT_COMPANY, TalentProfile::getCurrentCompany);
        getters.put(TalentMergeFields.CURRENT_POSITION, TalentProfile::getCurrentPosition);
        getters.put(TalentMergeFields.EXPECTED_POSITION, TalentProfile::getExpectedPosition);
        getters.put(TalentMergeFields.INDUSTRY, TalentProfile::getIndustry);
        getters.put(TalentMergeFields.WORK_YEARS, TalentProfile::getWorkYears);
        return getters;
    }

    /**
     * 把选中的值写回保留主档实体。
     *
     * @param keep  保留主档实体
     * @param field 字段名
     * @param value 取值
     */
    private void applyField(TalentProfile keep, String field, Object value) {
        switch (field) {
            case TalentMergeFields.NAME -> keep.setName((String) value);
            case TalentMergeFields.GENDER -> keep.setGender((String) value);
            case TalentMergeFields.HIGHEST_EDUCATION -> keep.setHighestEducation((String) value);
            case TalentMergeFields.CURRENT_CITY -> keep.setCurrentCity((String) value);
            case TalentMergeFields.EXPECTED_CITY -> keep.setExpectedCity((String) value);
            case TalentMergeFields.CURRENT_COMPANY -> keep.setCurrentCompany((String) value);
            case TalentMergeFields.CURRENT_POSITION -> keep.setCurrentPosition((String) value);
            case TalentMergeFields.EXPECTED_POSITION -> keep.setExpectedPosition((String) value);
            case TalentMergeFields.INDUSTRY -> keep.setIndustry((String) value);
            case TalentMergeFields.WORK_YEARS -> keep.setWorkYears((Integer) value);
            default -> throw new ServiceException("不允许在合并中选择该字段的取值：" + field);
        }
    }

    /**
     * 判断字段值是否视为空（null 或空白字符串）。
     *
     * @param value 字段值
     * @return 是否为空
     */
    private boolean isBlankValue(Object value) {
        return value == null || (value instanceof String str && StringUtils.isBlank(str));
    }

    /* ------------------------------------------------------------------ 内部方法：预览 ------------------------------------------------------------------ */

    /**
     * 构造冲突字段差异清单（展示全部白名单字段，标注是否可选）。
     *
     * @param keep   保留主档
     * @param merged 被合并主档
     * @return 字段差异清单
     */
    private List<TalentMergePreviewVo.FieldDiff> buildFieldDiffs(TalentProfile keep, TalentProfile merged) {
        List<TalentMergePreviewVo.FieldDiff> diffs = new ArrayList<>();
        for (Map.Entry<String, Function<TalentProfile, Object>> entry : fieldGetters().entrySet()) {
            String field = entry.getKey();
            Object keepValue = entry.getValue().apply(keep);
            Object mergedValue = entry.getValue().apply(merged);
            boolean conflict = !isBlankValue(keepValue) && !isBlankValue(mergedValue)
                && !Objects.equals(keepValue, mergedValue);
            TalentMergePreviewVo.FieldDiff diff = new TalentMergePreviewVo.FieldDiff();
            diff.setField(field);
            diff.setLabel(TalentMergeFields.label(field));
            diff.setKeepValue(stringify(keepValue));
            diff.setMergedValue(stringify(mergedValue));
            diff.setSelectable(true);
            diff.setDefaultFrom(conflict ? FROM_KEEP : (isBlankValue(keepValue) && !isBlankValue(mergedValue)
                ? FROM_MERGED : FROM_KEEP));
            diffs.add(diff);
        }
        return diffs;
    }

    /**
     * 构造关系数量影响面清单。
     *
     * @param mergedCounts 被合并主档关系数量
     * @param keepCounts   保留主档关系数量
     * @return 关系影响面清单
     */
    private List<TalentMergePreviewVo.RelationSummary> buildRelationSummaries(Map<String, Long> mergedCounts,
                                                                              Map<String, Long> keepCounts) {
        List<TalentMergePreviewVo.RelationSummary> summaries = new ArrayList<>();
        Map<String, String> labels = relationLabels();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            String table = entry.getKey();
            TalentMergePreviewVo.RelationSummary summary = new TalentMergePreviewVo.RelationSummary();
            summary.setTable(table);
            summary.setLabel(entry.getValue());
            summary.setMergedCount(toInt(mergedCounts.get(table)));
            summary.setKeepCount(toInt(keepCounts.get(table)));
            summary.setMigrateMode(migrateMode(table));
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * 关系表 → 中文名（顺序即页面展示顺序）。
     *
     * @return 关系表中文名映射
     */
    private Map<String, String> relationLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_APPLICATION, "应聘记录");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_RESUME, "简历版本");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_EDUCATION, "教育经历");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_WORK, "工作经历");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_PROJECT, "项目经历");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_ATTACHMENT, "应聘附件");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_TALENT_ATTACHMENT, "人才附件");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_POOL_MEMBER, "人才池成员");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_PROFILE_TAG, "标签关系");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_FOLLOW_UP, "跟进记录");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_GROUP_MEMBER, "分组成员");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_SCOPE_GRANT, "共享授权");
        labels.put(org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_PROFILE_CHANGE, "字段变更历史");
        return labels;
    }

    /**
     * 关系表迁移方式说明。
     *
     * @param table 关系表名
     * @return 迁移方式（move / skip_conflict / follow）
     */
    private String migrateMode(String table) {
        if (org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_POOL_MEMBER.equals(table)
            || org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_PROFILE_TAG.equals(table)
            || org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_GROUP_MEMBER.equals(table)) {
            return "skip_conflict";
        }
        if (org.dromara.hrtalent.domainservice.TalentMergeRelationMigrator.TABLE_ATTACHMENT.equals(table)) {
            return "follow";
        }
        return "move";
    }

    /* ------------------------------------------------------------------ 内部方法：快照与审计 ------------------------------------------------------------------ */

    /**
     * 合并关系数量快照（迁移前 / 实际迁移 / 迁移后）。
     *
     * @param beforeCounts 迁移前数量
     * @param movedCounts  实际迁移行数
     * @param afterCounts  迁移后数量
     * @return 可序列化的快照结构
     */
    private Map<String, Object> relationCountSnapshot(Map<String, Long> beforeCounts,
                                                      Map<String, Long> movedCounts,
                                                      Map<String, Long> afterCounts) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("before", beforeCounts);
        snapshot.put("moved", movedCounts);
        snapshot.put("after", afterCounts);
        long residual = afterCounts.values().stream().filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        snapshot.put("residualTotal", residual);
        snapshot.put("complete", residual == 0L);
        return snapshot;
    }

    /**
     * 构造脱敏审计明细（禁止任何联系方式、哈希与简历内容）。
     *
     * @param duplicateCase 案件
     * @param extra         附加说明
     * @return JSON 明细
     */
    private String auditDetail(TalentDuplicateCase duplicateCase, String extra) {
        if (duplicateCase == null) {
            return null;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("caseId", duplicateCase.getCaseId());
        detail.put("matchLevel", duplicateCase.getMatchLevel());
        detail.put("sourceTalentId", duplicateCase.getSourceTalentId());
        detail.put("targetTalentId", duplicateCase.getTargetTalentId());
        detail.put("note", extra);
        return toJson(detail);
    }

    /**
     * 发布人才合并事件（监听器异常不得回滚合并结果，§21.6：事件只负责后续派生刷新）。
     *
     * @param keepTalentId   保留主档ID
     * @param mergedTalentId 被合并主档ID
     * @param mergeId        合并日志ID
     * @param operatorId     操作人用户ID
     */
    private void publishMerged(Long keepTalentId, Long mergedTalentId, Long mergeId, Long operatorId) {
        try {
            eventPublisher.publishEvent(new TalentMergedEvent(keepTalentId, mergedTalentId, mergeId, operatorId));
        } catch (Exception e) {
            log.error("人才合并事件发布失败, keepTalentId={}, mergedTalentId={}", keepTalentId, mergedTalentId, e);
        }
    }

    /* ------------------------------------------------------------------ 内部方法：展示辅助 ------------------------------------------------------------------ */

    /**
     * 对主档视图对象做联系方式脱敏。
     *
     * @param vo 主档视图对象
     * @return 同一对象
     */
    private TalentProfileVo mask(TalentProfileVo vo) {
        if (vo == null) {
            return null;
        }
        vo.setPhoneMasked(TalentContactCodec.maskPhone(vo.getPhoneMasked()));
        vo.setEmailMasked(TalentContactCodec.maskEmail(vo.getEmailMasked()));
        return vo;
    }

    /**
     * 字段值字符串化（null 保持 null，便于页面显示短横线）。
     *
     * @param value 字段值
     * @return 字符串
     */
    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Long → Integer（数量展示）。
     *
     * @param value 数量
     * @return 数量
     */
    private int toInt(Long value) {
        return value == null ? 0 : value.intValue();
    }

}
