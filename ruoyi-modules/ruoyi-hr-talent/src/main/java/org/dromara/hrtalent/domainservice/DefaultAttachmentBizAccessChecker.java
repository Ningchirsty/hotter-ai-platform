package org.dromara.hrtalent.domainservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.hrtalent.domain.entity.RecruitApplication;
import org.dromara.hrtalent.domain.entity.RecruitBackground;
import org.dromara.hrtalent.domain.entity.RecruitInterview;
import org.dromara.hrtalent.domain.entity.TalentFollowUp;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.mapper.RecruitBackgroundMapper;
import org.dromara.hrtalent.mapper.RecruitInterviewMapper;
import org.dromara.hrtalent.mapper.TalentFollowUpMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 附件业务对象资源级鉴权的默认实现（设计文档 §8.8「下载前校验业务记录权限」、§15.3、§21.14）。
 *
 * <p><b>为什么需要它</b>：附件的按钮权限（{@code recruit:attachment:*}）只能证明「这个人有下载附件的资格」，
 * 不能证明「这个人有权看这条业务记录」。没有资源级鉴权时，任何持有下载权限的用户只要猜到
 * {@code attachmentId} 就能下载其他公司的候选人附件（横向越权）。因此本实现<b>默认存在</b>，
 * 未注册扩展实现时附件服务按<b>拒绝</b>处理（fail-closed），与本模块其它授权点的姿态一致。</p>
 *
 * <p><b>判定口径</b>：附件总是挂在某个业务对象上，业务对象最终都指向一名人才，
 * 因此解析链路统一收敛为「业务对象 → 人才ID → 人才可见范围」：</p>
 * <ul>
 *     <li>{@code talent}：{@code bizId} 即人才ID；</li>
 *     <li>{@code application}：经 {@code hr_recruit_application.talent_id} 解析；</li>
 *     <li>{@code offer}：录用资料登记在应聘记录上，故按应聘记录ID解析（<b>假设</b>，
 *     若后续独立出录用表需同步修正）；</li>
 *     <li>{@code interview}：经 {@code hr_recruit_interview.application_id} 再解析应聘记录；</li>
 *     <li>{@code background}：经 {@code hr_recruit_background.application_id} 再解析应聘记录；</li>
 *     <li>{@code follow_up}（P4）：经 {@code hr_talent_follow_up.talent_id} 直接解析人才
 *     （{@code bizId} 是跟进记录ID {@code follow_id}，不是人才ID）。</li>
 * </ul>
 *
 * <p><b>fail-closed</b>：业务类型不认识、业务记录不存在或已逻辑删除、链路中间断掉，
 * 一律拒绝（{@link #DENY_MESSAGE}），不做任何"放行并告警"。</p>
 *
 * <p><b>只读约定</b>：本实现只调用各域 Mapper 的 {@code selectById}，不新建同表 Mapper、
 * 不写任何其它域的表；可见范围判定统一委托给 {@link TalentScopeDomainService}，
 * <b>不</b>在此另造授权规则（§11.1）。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAttachmentBizAccessChecker implements AttachmentBizAccessChecker {

    /**
     * 业务记录无法解析或不可见时的统一拒绝提示（不泄露记录是否存在）。
     */
    public static final String DENY_MESSAGE = "无权访问该附件所属业务记录";

    /**
     * 应聘记录 Mapper（只读解析 talent_id）。
     */
    private final RecruitApplicationMapper recruitApplicationMapper;

    /**
     * 面试记录 Mapper（只读解析 application_id）。
     */
    private final RecruitInterviewMapper recruitInterviewMapper;

    /**
     * 背调记录 Mapper（只读解析 application_id）。
     */
    private final RecruitBackgroundMapper recruitBackgroundMapper;

    /**
     * 人才跟进记录 Mapper（只读解析 talent_id，P4 追加）。
     */
    private final TalentFollowUpMapper talentFollowUpMapper;

    /**
     * 人才主档 Mapper（只读装配可见范围快照）。
     */
    private final TalentProfileMapper talentProfileMapper;

    /**
     * 人才可见范围与资源级鉴权的唯一权威领域服务。
     */
    private final TalentScopeDomainService talentScopeDomainService;

    @Override
    public void check(String bizType, Long bizId) {
        if (StringUtils.isBlank(bizType) || bizId == null) {
            log.warn("附件业务记录鉴权拒绝：业务定位缺失, bizType={}, bizId={}", bizType, bizId);
            throw new ServiceException(DENY_MESSAGE);
        }
        String type = bizType.trim().toLowerCase(Locale.ROOT);
        Long talentId = resolveTalentId(type, bizId);
        if (talentId == null) {
            // fail-closed：记录不存在 / 链路断 / 业务类型不认识，一律拒绝
            log.warn("附件业务记录鉴权拒绝：无法解析所属人才, bizType={}, bizId={}", type, bizId);
            throw new ServiceException(DENY_MESSAGE);
        }
        TalentProfile profile = talentProfileMapper.selectById(talentId);
        if (profile == null) {
            log.warn("附件业务记录鉴权拒绝：人才主档不存在或已删除, bizType={}, bizId={}, talentId={}",
                type, bizId, talentId);
            throw new ServiceException(DENY_MESSAGE);
        }
        // 可见范围判定统一由 TalentScopeDomainService 负责：不可见抛 HR_TALENT_002，已合并抛 HR_TALENT_003
        talentScopeDomainService.checkTalentVisible(new TalentScopeDomainService.TalentScopeTarget(
            profile.getTalentId(),
            profile.getOwnerId(),
            profile.getOwnerDeptId(),
            profile.getVisibilityType(),
            profile.getTalentStatus(),
            profile.getDelFlag()));
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 解析业务对象所属人才ID；无法解析返回 {@code null}（调用方按拒绝处理）。
     *
     * @param bizType 业务类型稳定编码
     * @param bizId   业务对象ID
     * @return 人才ID；无法解析返回 null
     */
    private Long resolveTalentId(String bizType, Long bizId) {
        return switch (bizType) {
            case SensitiveAuditRecorder.BIZ_TALENT -> bizId;
            // 录用资料记录在应聘记录上，按应聘记录ID解析
            case SensitiveAuditRecorder.BIZ_APPLICATION, SensitiveAuditRecorder.BIZ_OFFER ->
                resolveTalentIdByApplication(bizId);
            case SensitiveAuditRecorder.BIZ_INTERVIEW -> {
                RecruitInterview interview = recruitInterviewMapper.selectById(bizId);
                yield interview == null ? null : resolveTalentIdByApplication(interview.getApplicationId());
            }
            case SensitiveAuditRecorder.BIZ_BACKGROUND -> {
                RecruitBackground background = recruitBackgroundMapper.selectById(bizId);
                yield background == null ? null : resolveTalentIdByApplication(background.getApplicationId());
            }
            // 跟进附件：bizId 是 hr_talent_follow_up.follow_id，经跟进记录解析人才ID
            case SensitiveAuditRecorder.BIZ_FOLLOW_UP -> {
                TalentFollowUp followUp = talentFollowUpMapper.selectById(bizId);
                yield followUp == null ? null : followUp.getTalentId();
            }
            default -> null;
        };
    }

    /**
     * 经应聘记录解析人才ID。
     *
     * @param applicationId 应聘记录ID
     * @return 人才ID；应聘记录不存在或未关联人才返回 null
     */
    private Long resolveTalentIdByApplication(Long applicationId) {
        if (applicationId == null) {
            return null;
        }
        RecruitApplication application = recruitApplicationMapper.selectById(applicationId);
        return application == null ? null : application.getTalentId();
    }

}
