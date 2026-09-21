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
import org.dromara.hrtalent.domain.bo.talent.CandidateCreateBo;
import org.dromara.hrtalent.domain.bo.talent.CandidateQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentPrecheckBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitApplicationBo;
import org.dromara.hrtalent.domain.entity.RecruitApplication;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.vo.talent.CandidateVo;
import org.dromara.hrtalent.domain.vo.talent.TalentPrecheckVo;
import org.dromara.hrtalent.domainservice.TalentDuplicateDomainService;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.TalentStatusEnum;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.service.recruitment.IRecruitApplicationService;
import org.dromara.hrtalent.service.talent.ICandidateService;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.dromara.hrtalent.support.TalentContactCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 候选人服务实现（SPEC-P3 §2.1、§3.1「一人一档」）。
 *
 * <p><b>实现要点</b>：</p>
 * <ul>
 *     <li>候选人列表 = 存在应聘记录的人才视图，SQL 由 {@link TalentProfileMapper#selectCandidatePage} 完成，
 *     可见范围条件由 {@link TalentScopeDomainService} 生成；</li>
 *     <li>新增候选人<b>必须</b>先查重；命中强/中匹配且未确认时拒绝静默创建，
 *     推荐处置是复用已有主档（{@code talentId} 非空）；</li>
 *     <li>电话明文查看<b>先写审计再返发明文</b>，用途为空直接拒绝并记 {@code denied}（§3.6）；</li>
 *     <li><b>不重复实现应聘记录域</b>：首条应聘记录一律经
 *     {@link IRecruitApplicationService#create(RecruitApplicationBo)} 创建，
 *     由应聘域统一负责编号、首条阶段历史 {@code hr_recruit_stage_log} 与计划任务计入关系，
 *     本服务<b>不</b>自行插入 {@code hr_recruit_application}。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateServiceImpl implements ICandidateService {

    /**
     * 候选人查询落空的统一提示。
     *
     * <p><b>有意不可区分</b>：无论「无应聘记录」还是「人才不存在 / 不可见」，电话明文查看都返回本提示，
     * 避免攻击者用错误文案差异探测候选人是否存在（见 {@link #viewPhone}）。</p>
     */
    private static final String MSG_CANDIDATE_NOT_FOUND = "候选人不存在或已删除";

    /**
     * 电话明文查看被拒绝的原因码：该人才没有任何应聘记录（数据未录入方向）。
     *
     * <p>只写入审计明细 {@code detail_json}，<b>不</b>对外暴露（对外统一提示见
     * {@link #MSG_CANDIDATE_NOT_FOUND}）。</p>
     */
    public static final String DENY_REASON_NO_APPLICATION = "no_application";

    /**
     * 电话明文查看被拒绝的原因码：人才不存在或不在当前用户可见范围内（权限/错误ID方向）。
     *
     * <p>两类原因对外不可区分，仅在审计明细中区分，避免对外提示差异被用作存在性探测旁路。</p>
     */
    public static final String DENY_REASON_OUT_OF_SCOPE_OR_ABSENT = "out_of_scope_or_absent";

    /**
     * 人才主档 Mapper（候选人视图查询）。
     */
    private final TalentProfileMapper talentProfileMapper;

    /**
     * 应聘记录 Mapper（只读：候选人身份判定与应聘记录计数）。
     */
    private final RecruitApplicationMapper recruitApplicationMapper;

    /**
     * 应聘记录服务（首条应聘记录的唯一写入入口）。
     */
    private final IRecruitApplicationService recruitApplicationService;

    /**
     * 人才主档服务（创建、可见性校验、电话明文）。
     */
    private final ITalentProfileService talentProfileService;

    /**
     * 重复检测领域服务。
     */
    private final TalentDuplicateDomainService talentDuplicateDomainService;

    /**
     * 人才可见范围领域服务。
     */
    private final TalentScopeDomainService talentScopeDomainService;

    /**
     * 敏感审计记录器（唯一入口）。
     */
    private final SensitiveAuditRecorder sensitiveAuditRecorder;

    @Override
    public PageResult<CandidateVo> queryPage(CandidateQueryBo bo, PageQuery pageQuery) {
        List<Long> visibleTalentIds = resolveVisibleTalentIds();
        if (visibleTalentIds.isEmpty()) {
            // 无任何可见人才：直接返回空页，不查库
            return PageResult.build(List.of(), 0L);
        }
        CandidateQueryBo query = bo == null ? new CandidateQueryBo() : bo;
        String phoneHash = TalentContactCodec.phoneHash(query.getPhone());
        String emailHash = TalentContactCodec.emailHash(query.getEmail());
        LambdaQueryWrapper<TalentProfile> wrapper = new LambdaQueryWrapper<TalentProfile>()
            // 可见范围硬约束：人才ID集合由 TalentScopeDomainService 的条件解析得出（§21.14）
            .in(TalentProfile::getTalentId, visibleTalentIds)
            .like(StringUtils.isNotBlank(query.getTalentNo()), TalentProfile::getTalentNo, query.getTalentNo())
            .like(StringUtils.isNotBlank(query.getName()), TalentProfile::getName, query.getName())
            .eq(phoneHash != null, TalentProfile::getPhoneHash, phoneHash)
            .eq(emailHash != null, TalentProfile::getEmailHash, emailHash)
            .eq(StringUtils.isNotBlank(query.getTalentStatus()), TalentProfile::getTalentStatus, query.getTalentStatus())
            .like(StringUtils.isNotBlank(query.getCurrentCity()), TalentProfile::getCurrentCity, query.getCurrentCity())
            .like(StringUtils.isNotBlank(query.getExpectedPosition()), TalentProfile::getExpectedPosition, query.getExpectedPosition())
            .like(StringUtils.isNotBlank(query.getCurrentCompany()), TalentProfile::getCurrentCompany, query.getCurrentCompany())
            .eq(StringUtils.isNotBlank(query.getHighestEducation()), TalentProfile::getHighestEducation, query.getHighestEducation())
            .eq(query.getOwnerDeptId() != null, TalentProfile::getOwnerDeptId, query.getOwnerDeptId())
            .eq(StringUtils.isNotBlank(query.getDataLevel()), TalentProfile::getDataLevel, query.getDataLevel())
            .eq(query.getSourceChannelId() != null, TalentProfile::getSourceChannelId, query.getSourceChannelId())
            .ne(TalentProfile::getTalentStatus, TalentStatusEnum.MERGED.getCode());
        Page<CandidateVo> page = (Page<CandidateVo>) talentProfileMapper.selectCandidatePage(
            pageQuery.build(),
            wrapper,
            StringUtils.isBlank(query.getStage()) ? null : query.getStage(),
            StringUtils.isBlank(query.getStatus()) ? null : query.getStatus(),
            query.getJobId(),
            query.getRecruiterId());
        List<CandidateVo> records = page.getRecords();
        if (CollUtil.isNotEmpty(records)) {
            records.forEach(this::maskContact);
        }
        return PageResult.build(records, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CandidateCreateBo bo) {
        boolean createApplication = !Boolean.FALSE.equals(bo.getCreateApplication());
        Long jobId = bo.getJobId();
        if (createApplication && jobId == null) {
            throw new ServiceException("创建应聘记录时岗位不能为空");
        }
        if (bo.getTalentId() != null) {
            // 复用已有主档：这是「疑似重复」时的推荐处置，不再创建新主档
            TalentProfile exist = talentProfileService.requireVisible(bo.getTalentId());
            if (TalentStatusEnum.ARCHIVED.getCode().equals(exist.getTalentStatus())) {
                throw new ServiceException("已归档的人才不能新增应聘记录，请先恢复");
            }
            if (createApplication) {
                createApplication(bo, exist.getTalentId(), jobId);
            }
            log.info("复用人才主档新增应聘记录, talentId={}, jobId={}", exist.getTalentId(), jobId);
            return exist.getTalentId();
        }
        // 新建主档：必须先查重，疑似重复不得静默创建
        TalentPrecheckVo precheck = talentDuplicateDomainService.precheck(bo.getName(), bo.getPhone(), bo.getEmail(),
            bo.getCurrentCompany(), null, null, bo.getExpectedPosition());
        if (talentDuplicateDomainService.needManualDispose(precheck) && !Boolean.TRUE.equals(bo.getDuplicateAck())) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_001);
        }
        Long operatorId = currentUserId();
        Long talentId = talentProfileService.create(bo, false, true, operatorId);
        if (createApplication) {
            createApplication(bo, talentId, jobId);
        }
        log.info("新增候选人, talentId={}, jobId={}, precheckDuplicated={}", talentId, jobId, precheck.isDuplicated());
        return talentId;
    }

    @Override
    public TalentPrecheckVo precheck(TalentPrecheckBo bo) {
        if (bo == null) {
            throw new ServiceException("预检入参不能为空");
        }
        return talentDuplicateDomainService.precheck(bo.getName(), bo.getPhone(), bo.getEmail(),
            bo.getCurrentCompany(), bo.getSchoolName(), bo.getResumeHash(), bo.getExpectedPosition());
    }

    @Override
    public void assertCandidate(Long talentId) {
        if (talentId == null) {
            throw new ServiceException("候选人ID不能为空");
        }
        if (countApplicationByTalentId(talentId) <= 0) {
            throw new ServiceException(MSG_CANDIDATE_NOT_FOUND);
        }
    }

    @Override
    public String viewPhone(Long talentId, String purpose) {
        if (StringUtils.isBlank(purpose)) {
            // 用途为空：先记 denied 审计，再拒绝；不泄露任何联系方式信息
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_PHONE_VIEW, SensitiveAuditRecorder.BIZ_TALENT,
                talentId, purpose, SensitiveAuditRecorder.RESULT_DENIED);
            throw new ServiceException("查看电话明文必须填写用途");
        }
        // 显式分两段捕获（不按异常 message 匹配，避免脆弱判定）：
        // 两段对外行为完全一致（异常类型、提示文案、审计的 event/biz/result 都相同），
        // 只有 detailJson 的 reason 不同，供运维区分「数据没录」与「权限配置」两类处置方向。
        try {
            assertCandidate(talentId);
        } catch (ServiceException e) {
            denyPhoneView(talentId, purpose, DENY_REASON_NO_APPLICATION);
            throw new ServiceException(MSG_CANDIDATE_NOT_FOUND);
        }
        String plain;
        try {
            plain = talentProfileService.getPhonePlain(talentId);
        } catch (ServiceException e) {
            denyPhoneView(talentId, purpose, DENY_REASON_OUT_OF_SCOPE_OR_ABSENT);
            throw new ServiceException(MSG_CANDIDATE_NOT_FOUND);
        }
        // 明文返发之前先写审计（SPEC-P3 §3.6）
        sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_PHONE_VIEW, SensitiveAuditRecorder.BIZ_TALENT,
            talentId, purpose, SensitiveAuditRecorder.RESULT_SUCCESS);
        return plain;
    }

    /**
     * 记录一次「电话明文查看被拒绝」的审计，并带上真实原因码。
     *
     * <p><b>为什么把原因写进审计</b>：对外必须不可区分（防存在性探测旁路），但运维侧需要能区分
     * 「该人才没有应聘记录」（数据未录入）与「人才存在但不在可见范围 / 不存在」（权限配置或错误ID），
     * 两者处置方向完全相反。原因码是纯枚举值，符合 {@link SensitiveAuditRecorder} 明细的脱敏约束
     * （明细中不含电话明文、正文与地址）。</p>
     *
     * <p>落入统一分支的语义：两类落空<b>只允许 detailJson 不同</b>，事件类型、业务对象、结果与对外异常
     * 必须完全一致。</p>
     *
     * @param talentId 人才主档ID
     * @param purpose  查看用途
     * @param reason   原因码，取 {@link #DENY_REASON_NO_APPLICATION} 或 {@link #DENY_REASON_OUT_OF_SCOPE_OR_ABSENT}
     */
    private void denyPhoneView(Long talentId, String purpose, String reason) {
        // 审计写入依赖 SensitiveAuditRecorder「永不抛异常」的既有契约，因此不会把拒绝变成 500
        sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_PHONE_VIEW, SensitiveAuditRecorder.BIZ_TALENT,
            talentId, purpose, SensitiveAuditRecorder.RESULT_DENIED, reasonDetail(reason));
    }

    /**
     * 构造原因码明细 JSON（值只来自固定枚举常量，不含任何敏感内容）。
     *
     * @param reason 原因码
     * @return 脱敏明细 JSON
     */
    private String reasonDetail(String reason) {
        return "{\"reason\":\"" + reason + "\"}";
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 解析当前登录用户可见的人才ID集合（设计文档 §21.14）。
     *
     * <p><b>唯一接法</b>：把 {@link TalentScopeDomainService#visibleTalentWrapper()} 返回的
     * wrapper <b>原样</b>交给 {@link TalentProfileMapper#selectVisibleTalentIds} 执行，由
     * {@code ${ew.customSqlSegment}} 直接消费该 wrapper 的条件与参数，再用返回的ID集合做
     * {@code IN} 过滤，<b>不</b>做「wrapper 片段搬家」、也<b>不</b>另写一套授权规则（§11.1）。</p>
     *
     * @return 可见的人才主档ID列表（不为 null，可能为空）
     */
    private List<Long> resolveVisibleTalentIds() {
        List<Long> ids = talentProfileMapper.selectVisibleTalentIds(
            talentScopeDomainService.visibleTalentWrapper());
        return ids == null ? List.of() : ids;
    }

    /**
     * 统计人才的有效应聘记录条数（只读使用应聘域 Mapper，不新建同表 Mapper）。
     *
     * @param talentId 人才主档ID
     * @return 应聘记录条数
     */
    private long countApplicationByTalentId(Long talentId) {
        return recruitApplicationMapper.selectCount(new LambdaQueryWrapper<RecruitApplication>()
            .eq(RecruitApplication::getTalentId, talentId));
    }

    /**
     * 通过应聘域服务创建首条应聘记录。
     *
     * <p>应聘记录保存业务快照（岗位、期望薪资、联系日期），<b>不复制</b>人才基础信息（§7.6.1）。
     * 编号生成、首条阶段历史与计划任务计入关系全部由应聘域负责，本服务不重复实现。</p>
     *
     * @param bo       候选人入参
     * @param talentId 人才主档ID
     * @param jobId    岗位执行项ID
     * @return 新建的应聘记录ID
     */
    private Long createApplication(CandidateCreateBo bo, Long talentId, Long jobId) {
        RecruitApplicationBo applicationBo = new RecruitApplicationBo();
        applicationBo.setTalentId(talentId);
        applicationBo.setJobId(jobId);
        applicationBo.setExpectedSalaryMin(bo.getExpectedSalaryMin());
        applicationBo.setExpectedSalaryMax(bo.getExpectedSalaryMax());
        applicationBo.setRecruiterId(bo.getRecruiterId());
        applicationBo.setContactDate(bo.getContactDate());
        applicationBo.setSourceChannelId(bo.getSourceChannelId());
        applicationBo.setSourceType(bo.getSourceType());
        applicationBo.setRemark(bo.getApplicationRemark());
        return recruitApplicationService.create(applicationBo);
    }

    /**
     * 候选人视图脱敏：电话/邮箱只返回脱敏串，协助人ID串拆回数组。
     *
     * @param vo 候选人视图对象
     */
    private void maskContact(CandidateVo vo) {
        vo.setPhoneMasked(TalentContactCodec.maskPhone(vo.getPhoneMasked()));
        vo.setEmailMasked(TalentContactCodec.maskEmail(vo.getEmailMasked()));
        if (vo.getAssistantIds() != null) {
            String joined = java.util.Arrays.stream(vo.getAssistantIds())
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
            vo.setAssistantIds(TalentContactCodec.splitIds(joined));
        }
    }

    /**
     * 取当前登录用户ID；无登录态返回 null。
     *
     * @return 用户ID或 null
     */
    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

}
