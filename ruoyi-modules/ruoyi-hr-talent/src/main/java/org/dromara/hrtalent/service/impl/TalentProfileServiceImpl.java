package org.dromara.hrtalent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.dromara.hrtalent.domain.bo.talent.TalentPrecheckBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitApplication;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.domain.entity.TalentProfileChange;
import org.dromara.hrtalent.domain.vo.talent.TalentPrecheckVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileChangeVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileDetailVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileVo;
import org.dromara.hrtalent.domainservice.TalentDuplicateDomainService;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.TalentStatusEnum;
import org.dromara.hrtalent.enums.TalentVisibilityTypeEnum;
import org.dromara.hrtalent.event.TalentProfileChangedEvent;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.mapper.TalentProfileChangeMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.dromara.hrtalent.support.TalentContactCodec;
import org.dromara.system.api.model.LoginUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 人才主档服务实现（SPEC-P3 §2.1、§3.1）。
 *
 * <p><b>实现要点</b>：</p>
 * <ul>
 *     <li><b>一人一档</b>：{@code hr_talent_profile} 是唯一主档，候选人由应聘记录关联得出；</li>
 *     <li><b>入库前查重</b>：创建前调用 {@link TalentDuplicateDomainService}，
 *     强/中匹配且未确认时拒绝静默创建（设计文档 §7.6.3）；</li>
 *     <li><b>联系方式</b>：电话/邮箱明文由本服务标准化并计算 SHA-256 哈希，
 *     密文列由 {@code @EncryptField} 的 MyBatis 拦截器自动加解密；</li>
 *     <li><b>关键字段变更</b>：姓名/电话/邮箱/状态/负责人/可见范围/数据分级变化时
 *     追加 {@code hr_talent_profile_change}（只追加、不覆盖）；</li>
 *     <li><b>同一事务</b>：主档创建与首次联系方式写入在同一事务内完成（设计文档 §21.7）；</li>
 *     <li><b>可见范围</b>：列表/详情/变更历史统一经
 *     {@link TalentScopeDomainService}，不自行实现授权规则。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentProfileServiceImpl implements ITalentProfileService {

    /**
     * 变更类型：创建。
     */
    private static final String CHANGE_CREATE = "create";

    /**
     * 变更类型：更新。
     */
    private static final String CHANGE_UPDATE = "update";

    /**
     * 变更类型：状态变更。
     */
    private static final String CHANGE_STATUS = "status";

    /**
     * 变更类型：归档。
     */
    private static final String CHANGE_ARCHIVE = "archive";

    /**
     * 主档来源：手工录入。
     */
    private static final String SOURCE_MANUAL = "manual";

    /**
     * 协助人字段最大存储长度（与 DDL {@code assistant_ids varchar(255)} 一致）。
     */
    private static final int ASSISTANT_IDS_MAX_LENGTH = 255;

    /**
     * 手机号校验：仅做长度与字符集的基本校验，不强制大陆号段，避免误伤外籍/座机。
     */
    private static final String PHONE_PATTERN = "^[+]?[0-9\\-]{6,20}$";

    /**
     * 邮箱基本格式校验。
     */
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$";

    /**
     * 人才主档 Mapper。
     */
    private final TalentProfileMapper talentProfileMapper;

    /**
     * 人才关键字段变更历史 Mapper。
     */
    private final TalentProfileChangeMapper talentProfileChangeMapper;

    /**
     * 应聘记录 Mapper（只读：判断人才是否已存在应聘记录；不重复实现应聘域写操作）。
     */
    private final RecruitApplicationMapper recruitApplicationMapper;

    /**
     * 重复检测领域服务。
     */
    private final TalentDuplicateDomainService talentDuplicateDomainService;

    /**
     * 人才可见范围领域服务（唯一授权入口）。
     */
    private final TalentScopeDomainService talentScopeDomainService;

    /**
     * 业务编号生成器（人才编号唯一事实来源）。
     */
    private final RecruitBusinessNoGenerator businessNoGenerator;

    /**
     * 领域事件发布器。
     */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<TalentProfileVo> queryPage(TalentProfileQueryBo bo, PageQuery pageQuery) {
        List<Long> visibleTalentIds = resolveVisibleTalentIds();
        if (visibleTalentIds.isEmpty()) {
            // 无任何可见人才：直接返回空页，不查库
            return PageResult.build(List.of(), 0L);
        }
        TalentProfileQueryBo query = bo == null ? new TalentProfileQueryBo() : bo;
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
            .like(StringUtils.isNotBlank(query.getExpectedCity()), TalentProfile::getExpectedCity, query.getExpectedCity())
            .like(StringUtils.isNotBlank(query.getExpectedPosition()), TalentProfile::getExpectedPosition, query.getExpectedPosition())
            .like(StringUtils.isNotBlank(query.getCurrentCompany()), TalentProfile::getCurrentCompany, query.getCurrentCompany())
            .eq(StringUtils.isNotBlank(query.getHighestEducation()), TalentProfile::getHighestEducation, query.getHighestEducation())
            .like(StringUtils.isNotBlank(query.getIndustry()), TalentProfile::getIndustry, query.getIndustry())
            .eq(query.getOwnerId() != null, TalentProfile::getOwnerId, query.getOwnerId())
            .eq(query.getOwnerDeptId() != null, TalentProfile::getOwnerDeptId, query.getOwnerDeptId())
            .eq(StringUtils.isNotBlank(query.getVisibilityType()), TalentProfile::getVisibilityType, query.getVisibilityType())
            .eq(StringUtils.isNotBlank(query.getDataLevel()), TalentProfile::getDataLevel, query.getDataLevel())
            .eq(StringUtils.isNotBlank(query.getSourceType()), TalentProfile::getSourceType, query.getSourceType())
            .ge(query.getWorkYearsBegin() != null, TalentProfile::getWorkYears, query.getWorkYearsBegin())
            .le(query.getWorkYearsEnd() != null, TalentProfile::getWorkYears, query.getWorkYearsEnd())
            .ge(query.getCreateDateBegin() != null, TalentProfile::getCreateTime,
                query.getCreateDateBegin() == null ? null : query.getCreateDateBegin().atStartOfDay())
            .le(query.getCreateDateEnd() != null, TalentProfile::getCreateTime,
                query.getCreateDateEnd() == null ? null : query.getCreateDateEnd().atTime(23, 59, 59))
            // 已合并主档一律不出现在列表（设计文档 §21.14）
            .ne(TalentProfile::getTalentStatus, TalentStatusEnum.MERGED.getCode())
            .orderByDesc(TalentProfile::getCreateTime);
        // 不显式查询已归档时，默认把归档人才排除在日常检索之外
        if (StringUtils.isBlank(query.getTalentStatus()) && !Boolean.TRUE.equals(query.getIncludeArchived())) {
            wrapper.ne(TalentProfile::getTalentStatus, TalentStatusEnum.ARCHIVED.getCode());
        }
        if (query.getAssistantId() != null) {
            wrapper.apply("FIND_IN_SET({0}, assistant_ids)", query.getAssistantId());
        }
        // 仅查未被任何应聘记录引用的人才
        if (Boolean.TRUE.equals(query.getOnlyWithoutApplication())) {
            wrapper.apply("NOT EXISTS (SELECT 1 FROM hr_recruit_application a2 WHERE a2.talent_id = hr_talent_profile.talent_id AND a2.del_flag = '0')");
        }
        Page<TalentProfileVo> page = talentProfileMapper.selectVoPage(pageQuery.build(), wrapper);
        List<TalentProfileVo> records = page.getRecords();
        if (CollUtil.isNotEmpty(records)) {
            records.forEach(this::maskContact);
        }
        return PageResult.build(records, page.getTotal());
    }

    @Override
    public TalentProfileDetailVo getDetail(Long talentId) {
        TalentProfile profile = requireVisible(talentId);
        TalentProfileVo base = MapstructUtils.convert(profile, TalentProfileVo.class);
        if (base == null) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_001);
        }
        maskContact(base);
        TalentProfileDetailVo vo = copyToDetail(base, profile);
        vo.setHasApplication(hasApplication(talentId));
        return vo;
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
    @Transactional(rollbackFor = Exception.class)
    public Long create(TalentProfileBo bo, boolean precheck, boolean duplicateAck, Long operatorId) {
        validateProfile(bo, true);
        if (precheck) {
            TalentPrecheckVo result = talentDuplicateDomainService.precheck(bo.getName(), bo.getPhone(), bo.getEmail(),
                bo.getCurrentCompany(), null, null, bo.getExpectedPosition());
            if (talentDuplicateDomainService.needManualDispose(result) && !duplicateAck) {
                // 疑似重复不得静默创建：提示调用方复用已有主档或显式确认不是同一人
                throw new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_001);
            }
        }
        TalentProfile entity = MapstructUtils.convert(bo, TalentProfile.class);
        if (entity == null) {
            entity = new TalentProfile();
        }
        // 服务端权威字段：主键、编号、状态、版本、哈希与密文列均不接受前端写入
        entity.setTalentId(null);
        entity.setTalentNo(businessNoGenerator.nextTalentNo());
        entity.setVersion(0);
        entity.setTalentStatus(TalentStatusEnum.DRAFT.getCode());
        entity.setVisibilityType(StringUtils.isBlank(bo.getVisibilityType())
            ? TalentVisibilityTypeEnum.OWNER.getCode() : bo.getVisibilityType());
        entity.setSourceType(StringUtils.isBlank(bo.getSourceType()) ? SOURCE_MANUAL : bo.getSourceType());
        entity.setPhoneCipher(StringUtils.trim(bo.getPhone()));
        entity.setPhoneHash(TalentContactCodec.phoneHash(bo.getPhone()));
        entity.setBackupPhoneCipher(StringUtils.trim(bo.getBackupPhone()));
        entity.setBackupPhoneHash(TalentContactCodec.phoneHash(bo.getBackupPhone()));
        entity.setEmailCipher(TalentContactCodec.normalizeEmail(bo.getEmail()));
        entity.setEmailHash(TalentContactCodec.emailHash(bo.getEmail()));
        entity.setOtherContactCipher(StringUtils.trim(bo.getOtherContact()));
        entity.setAssistantIds(joinAssistantIds(bo.getAssistantIds()));
        fillOwnerDeptIfAbsent(entity, bo);
        fillOwnerIfAbsent(entity);
        validateStatus(TalentStatusEnum.DRAFT.getCode(), bo.getStatusReason());
        try {
            talentProfileMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            log.warn("人才编号生成冲突, talentNo={}", entity.getTalentNo());
            throw new ServiceException("人才编号生成冲突，请重试");
        }
        writeChange(entity.getTalentId(), CHANGE_CREATE, null, entity, operatorId);
        publishChanged(entity.getTalentId(), CHANGE_CREATE, operatorId);
        log.info("新增人才主档, talentId={}, talentNo={}, ownerId={}",
            entity.getTalentId(), entity.getTalentNo(), entity.getOwnerId());
        return entity.getTalentId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(TalentProfileBo bo) {
        if (bo.getTalentId() == null) {
            throw new ServiceException("人才ID不能为空");
        }
        if (bo.getVersion() == null) {
            throw new ServiceException("版本号不能为空，请刷新后重试");
        }
        validateProfile(bo, false);
        TalentProfile exist = requireVisible(bo.getTalentId());
        requireStatus(exist.getTalentStatus());
        if (TalentStatusEnum.MERGED.getCode().equals(exist.getTalentStatus())) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_003);
        }
        TalentProfile update = MapstructUtils.convert(bo, TalentProfile.class);
        if (update == null) {
            update = new TalentProfile();
        }
        update.setTalentId(exist.getTalentId());
        update.setVersion(bo.getVersion());
        // 联系方式：以「哈希是否变化」判定是否需要改写密文列，未提供明文时不触碰原值
        if (StringUtils.isNotBlank(bo.getPhone())) {
            update.setPhoneCipher(StringUtils.trim(bo.getPhone()));
            update.setPhoneHash(TalentContactCodec.phoneHash(bo.getPhone()));
        }
        if (StringUtils.isNotBlank(bo.getBackupPhone())) {
            update.setBackupPhoneCipher(StringUtils.trim(bo.getBackupPhone()));
            update.setBackupPhoneHash(TalentContactCodec.phoneHash(bo.getBackupPhone()));
        }
        if (StringUtils.isNotBlank(bo.getEmail())) {
            update.setEmailCipher(TalentContactCodec.normalizeEmail(bo.getEmail()));
            update.setEmailHash(TalentContactCodec.emailHash(bo.getEmail()));
        }
        if (StringUtils.isNotBlank(bo.getOtherContact())) {
            update.setOtherContactCipher(StringUtils.trim(bo.getOtherContact()));
        }
        if (bo.getAssistantIds() != null) {
            update.setAssistantIds(joinAssistantIds(bo.getAssistantIds()));
        }
        int rows = talentProfileMapper.updateById(update);
        if (rows == 0) {
            log.warn("人才主档更新版本冲突, talentId={}, version={}", exist.getTalentId(), bo.getVersion());
            throw new ServiceException("人才数据已被他人修改，请刷新后重试");
        }
        TalentProfile latest = talentProfileMapper.selectById(exist.getTalentId());
        writeChangedFields(exist, latest);
        publishChanged(exist.getTalentId(), CHANGE_UPDATE, currentUserId());
        log.info("更新人才主档, talentId={}, version={}", exist.getTalentId(), bo.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long[] talentIds) {
        if (talentIds == null || talentIds.length == 0) {
            return;
        }
        List<Long> ids = Arrays.stream(talentIds).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            requireVisible(id);
            if (hasApplication(id)) {
                throw new ServiceException("该人才已存在应聘记录，不能删除；如需停用请使用归档");
            }
            if (talentProfileMapper.selectCount(new LambdaQueryWrapper<TalentProfile>()
                .eq(TalentProfile::getMergedToId, id)) > 0) {
                throw new ServiceException("该人才已被合并引用，不能删除");
            }
        }
        talentProfileMapper.deleteByIds(ids);
        log.info("逻辑删除人才主档, talentIds={}", ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long talentId, String reason) {
        TalentProfile exist = requireVisible(talentId);
        if (TalentStatusEnum.ARCHIVED.getCode().equals(exist.getTalentStatus())) {
            throw new ServiceException("人才已归档，无需重复归档");
        }
        if (TalentStatusEnum.MERGED.getCode().equals(exist.getTalentStatus())) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_003);
        }
        LambdaUpdateWrapper<TalentProfile> wrapper = new LambdaUpdateWrapper<TalentProfile>()
            .eq(TalentProfile::getTalentId, talentId)
            .set(TalentProfile::getTalentStatus, TalentStatusEnum.ARCHIVED.getCode())
            .set(StringUtils.isNotBlank(reason), TalentProfile::getStatusReason, reason);
        int rows = talentProfileMapper.update(null, wrapper);
        if (rows == 0) {
            throw new ServiceException("归档失败，请刷新后重试");
        }
        Long operatorId = currentUserId();
        Map<String, Object> before = snapshot(exist);
        TalentProfile after = talentProfileMapper.selectById(talentId);
        writeChange(talentId, CHANGE_ARCHIVE, before, after, operatorId);
        publishChanged(talentId, CHANGE_ARCHIVE, operatorId);
        log.info("归档人才主档, talentId={}", talentId);
    }

    @Override
    public PageResult<TalentProfileChangeVo> queryChanges(Long talentId, PageQuery pageQuery) {
        requireVisible(talentId);
        LambdaQueryWrapper<TalentProfileChange> wrapper = new LambdaQueryWrapper<TalentProfileChange>()
            .eq(TalentProfileChange::getTalentId, talentId);
        Page<TalentProfileChangeVo> page = (Page<TalentProfileChangeVo>) talentProfileMapper.selectChangePage(
            pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public String getPhonePlain(Long talentId) {
        TalentProfile profile = requireVisible(talentId);
        return profile.getPhoneCipher();
    }

    @Override
    public TalentProfile requireVisible(Long talentId) {
        if (talentId == null) {
            throw new ServiceException("人才ID不能为空");
        }
        TalentProfile profile = talentProfileMapper.selectById(talentId);
        if (profile == null) {
            throw new ServiceException(org.dromara.hrtalent.domainservice.TalentScopeDomainService.TALENT_NOT_FOUND);
        }
        talentScopeDomainService.checkTalentVisible(new TalentScopeDomainService.TalentScopeTarget(
            profile.getTalentId(), profile.getOwnerId(), profile.getOwnerDeptId(),
            profile.getVisibilityType(), profile.getTalentStatus(), profile.getDelFlag()));
        return profile;
    }

    @Override
    public boolean hasApplication(Long talentId) {
        if (talentId == null) {
            return false;
        }
        // 只读使用应聘域 Mapper，不新建同表 Mapper；逻辑删除由实体 @TableLogic 自动过滤
        return recruitApplicationMapper.selectCount(new LambdaQueryWrapper<RecruitApplication>()
            .eq(RecruitApplication::getTalentId, talentId)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refreshStatusByEvent(Long talentId, String targetStatus, String reason) {
        if (talentId == null || StringUtils.isBlank(targetStatus)) {
            return false;
        }
        TalentStatusEnum target = TalentStatusEnum.find(targetStatus);
        if (target == null) {
            log.warn("事件驱动的状态刷新被忽略：未知状态, talentId={}, targetStatus={}", talentId, targetStatus);
            return false;
        }
        TalentProfile exist = talentProfileMapper.selectById(talentId);
        if (exist == null) {
            return false;
        }
        TalentStatusEnum current = TalentStatusEnum.find(exist.getTalentStatus());
        // 归档/合并/禁止联系/受限属人工终态，事件不得覆盖
        if (current == null || current == TalentStatusEnum.ARCHIVED || current == TalentStatusEnum.MERGED
            || current == TalentStatusEnum.DO_NOT_CONTACT || current == TalentStatusEnum.RESTRICTED) {
            return false;
        }
        if (current == target) {
            return false;
        }
        if (target != TalentStatusEnum.RECRUITING && target != TalentStatusEnum.HIRED
            && target != TalentStatusEnum.ACTIVE && target != TalentStatusEnum.RESERVED) {
            return false;
        }
        LambdaUpdateWrapper<TalentProfile> wrapper = new LambdaUpdateWrapper<TalentProfile>()
            .eq(TalentProfile::getTalentId, talentId)
            .set(TalentProfile::getTalentStatus, target.getCode())
            .set(StringUtils.isNotBlank(reason), TalentProfile::getStatusReason, reason);
        int rows = talentProfileMapper.update(null, wrapper);
        if (rows == 0) {
            return false;
        }
        TalentProfile after = talentProfileMapper.selectById(talentId);
        writeChangedFields(exist, after);
        publishChanged(talentId, CHANGE_STATUS, null);
        log.info("事件驱动的人才状态刷新, talentId={}, from={}, to={}", talentId, current.getCode(), target.getCode());
        return true;
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 组装详情 VO：在列表 VO 基础上补齐详情字段。
     *
     * <p>不使用 MapStruct 自动映射，避免 {@code phone_cipher} / {@code email_cipher}
     * 被顺手带入出参。</p>
     *
     * @param base    已脱敏的列表 VO
     * @param profile 人才主档实体
     * @return 详情 VO
     */
    private TalentProfileDetailVo copyToDetail(TalentProfileVo base, TalentProfile profile) {
        TalentProfileDetailVo vo = new TalentProfileDetailVo();
        vo.setTalentId(base.getTalentId());
        vo.setTalentNo(base.getTalentNo());
        vo.setName(base.getName());
        vo.setGender(base.getGender());
        vo.setHighestEducation(base.getHighestEducation());
        vo.setPhoneMasked(base.getPhoneMasked());
        vo.setEmailMasked(base.getEmailMasked());
        vo.setCurrentCity(base.getCurrentCity());
        vo.setExpectedCity(base.getExpectedCity());
        vo.setCurrentCompany(base.getCurrentCompany());
        vo.setCurrentPosition(base.getCurrentPosition());
        vo.setExpectedPosition(base.getExpectedPosition());
        vo.setWorkYears(base.getWorkYears());
        vo.setIndustry(base.getIndustry());
        vo.setOwnerId(base.getOwnerId());
        vo.setOwnerDeptId(base.getOwnerDeptId());
        vo.setOwnerDeptName(base.getOwnerDeptName());
        vo.setAssistantIds(base.getAssistantIds());
        vo.setTalentStatus(base.getTalentStatus());
        vo.setStatusReason(base.getStatusReason());
        vo.setStatusExpireDate(base.getStatusExpireDate());
        vo.setVisibilityType(base.getVisibilityType());
        vo.setDataLevel(base.getDataLevel());
        vo.setSourceType(base.getSourceType());
        vo.setSourceChannelId(base.getSourceChannelId());
        vo.setResumeUpdateTime(base.getResumeUpdateTime());
        vo.setVersion(base.getVersion());
        vo.setCreateTime(base.getCreateTime());
        vo.setUpdateTime(base.getUpdateTime());

        vo.setFormerName(profile.getFormerName());
        vo.setBirthDate(profile.getBirthDate());
        vo.setAgeSnapshot(profile.getAgeSnapshot());
        vo.setExpectedSalaryMin(profile.getExpectedSalaryMin());
        vo.setExpectedSalaryMax(profile.getExpectedSalaryMax());
        vo.setCurrentResumeId(profile.getCurrentResumeId());
        vo.setMergedToId(profile.getMergedToId());
        vo.setLastFollowTime(profile.getLastFollowTime());
        vo.setNextFollowTime(profile.getNextFollowTime());
        vo.setRemark(profile.getRemark());
        return vo;
    }

    /**
     * 对列表/详情 VO 填充脱敏电话与邮箱，并拆出协助人ID数组。
     *
     * @param vo 人才主档视图对象
     */
    private void maskContact(TalentProfileVo vo) {
        vo.setPhoneMasked(TalentContactCodec.maskPhone(vo.getPhoneMasked()));
        vo.setEmailMasked(TalentContactCodec.maskEmail(vo.getEmailMasked()));
        if (vo.getAssistantIds() != null) {
            vo.setAssistantIds(TalentContactCodec.splitIds(joinAssistantIds(vo.getAssistantIds())));
        }
    }

    /**
     * 校验人才主档入参（联系方式格式、薪资区间、状态原因等）。
     *
     * @param bo       入参
     * @param isCreate 是否新增场景
     */
    private void validateProfile(TalentProfileBo bo, boolean isCreate) {
        if (bo == null) {
            throw new ServiceException("人才主档入参不能为空");
        }
        if (StringUtils.isNotBlank(bo.getPhone()) && !bo.getPhone().trim().matches(PHONE_PATTERN)) {
            throw new ServiceException("电话格式不正确，只允许数字、+ 与横线");
        }
        if (StringUtils.isNotBlank(bo.getBackupPhone()) && !bo.getBackupPhone().trim().matches(PHONE_PATTERN)) {
            throw new ServiceException("备用手机号格式不正确，只允许数字、+ 与横线");
        }
        if (StringUtils.isNotBlank(bo.getEmail()) && !bo.getEmail().trim().matches(EMAIL_PATTERN)) {
            throw new ServiceException("邮箱格式不正确");
        }
        if (bo.getExpectedSalaryMin() != null && bo.getExpectedSalaryMax() != null
            && bo.getExpectedSalaryMin().compareTo(bo.getExpectedSalaryMax()) > 0) {
            throw new ServiceException("期望薪资下限不能大于上限");
        }
        if (StringUtils.isNotBlank(bo.getVisibilityType())
            && TalentVisibilityTypeEnum.find(bo.getVisibilityType()) == null) {
            throw new ServiceException("可见范围不合法，请使用字典 talent_visibility_type 的编码");
        }
        if (StringUtils.isNotBlank(bo.getDataLevel())
            && !List.of("internal", "sensitive", "highly_sensitive").contains(bo.getDataLevel())) {
            throw new ServiceException("数据分级不合法，请使用字典 recruit_data_level 的编码");
        }
        if (!isCreate && bo.getTalentId() == null) {
            throw new ServiceException("人才ID不能为空");
        }
    }

    /**
     * 校验生命状态编码合法性，以及「禁止联系」类状态必须填写原因。
     *
     * @param status       状态编码
     * @param statusReason 状态原因
     */
    private void validateStatus(String status, String statusReason) {
        TalentStatusEnum target = requireStatus(status);
        if ((TalentStatusEnum.DO_NOT_CONTACT == target || TalentStatusEnum.RESTRICTED == target)
            && StringUtils.isBlank(statusReason)) {
            throw new ServiceException("进入「" + target.getDesc() + "」状态必须填写原因");
        }
    }

    /**
     * 解析人才状态编码，未知编码一律拒绝（fail-safe）。
     *
     * @param status 状态编码
     * @return 状态枚举
     */
    private TalentStatusEnum requireStatus(String status) {
        TalentStatusEnum target = TalentStatusEnum.find(status);
        if (target == null) {
            throw new ServiceException("未知的人才状态：" + status);
        }
        return target;
    }

    /**
     * 归属部门未指定时回落到当前登录用户所属部门。
     *
     * @param entity 实体
     * @param bo     入参
     */
    private void fillOwnerDeptIfAbsent(TalentProfile entity, TalentProfileBo bo) {
        if (entity.getOwnerDeptId() != null) {
            return;
        }
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser != null) {
            entity.setOwnerDeptId(loginUser.getDeptId());
        }
    }

    /**
     * 负责人未指定时回落为当前登录用户。
     *
     * @param entity 实体
     */
    private void fillOwnerIfAbsent(TalentProfile entity) {
        if (entity.getOwnerId() != null) {
            return;
        }
        Long userId = currentUserId();
        if (userId != null) {
            entity.setOwnerId(userId);
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

    /**
     * 将协助人用户ID数组拼接为英文逗号分隔串（去重、去空），并校验长度。
     *
     * @param assistantIds 协助人用户ID数组，null 表示不修改
     * @return 逗号分隔串；无有效元素时返回 null
     */
    private String joinAssistantIds(Long[] assistantIds) {
        if (assistantIds == null) {
            return null;
        }
        LinkedHashSet<Long> distinct = Arrays.stream(assistantIds)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (distinct.isEmpty()) {
            return null;
        }
        String joined = distinct.stream().map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(","));
        if (joined.length() > ASSISTANT_IDS_MAX_LENGTH) {
            throw new ServiceException("协助人过多，存储长度已超过 " + ASSISTANT_IDS_MAX_LENGTH + " 个字符");
        }
        return joined;
    }

    /**
     * 追加一条变更历史（before 为空表示创建）。
     *
     * @param talentId  人才主档ID
     * @param changeType 变更类型
     * @param before    变更前快照，可为 null
     * @param after     变更后实体，可为 null
     * @param operatorId 操作人用户ID
     */
    private void writeChange(Long talentId, String changeType, Map<String, Object> before,
                             TalentProfile after, Long operatorId) {
        writeChange(talentId, changeType, before, after == null ? null : snapshot(after), operatorId);
    }

    /**
     * 追加一条变更历史。
     *
     * @param talentId   人才主档ID
     * @param changeType 变更类型
     * @param before     变更前快照
     * @param after      变更后快照
     * @param operatorId 操作人用户ID
     */
    private void writeChange(Long talentId, String changeType, Map<String, Object> before,
                             Map<String, Object> after, Long operatorId) {
        TalentProfileChange change = new TalentProfileChange();
        change.setTalentId(talentId);
        change.setChangeType(changeType);
        change.setBeforeJson(before == null ? null : JsonUtils.toJsonString(before));
        change.setAfterJson(after == null ? null : JsonUtils.toJsonString(after));
        change.setOperatorId(operatorId == null ? currentUserId() : operatorId);
        change.setOperateTime(LocalDateTime.now());
        talentProfileChangeMapper.insert(change);
    }

    /**
     * 比对更新前后的关键字段，变化时追加一条 {@code update} 变更记录。
     *
     * @param before 更新前实体
     * @param after  更新后实体
     */
    private void writeChangedFields(TalentProfile before, TalentProfile after) {
        if (after == null) {
            return;
        }
        Map<String, Object> beforeChanged = new LinkedHashMap<>();
        Map<String, Object> afterChanged = new LinkedHashMap<>();
        collectIfChanged(beforeChanged, afterChanged, "name", before.getName(), after.getName());
        collectIfChanged(beforeChanged, afterChanged, "gender", before.getGender(), after.getGender());
        collectIfChanged(beforeChanged, afterChanged, "highestEducation", before.getHighestEducation(), after.getHighestEducation());
        collectIfChanged(beforeChanged, afterChanged, "phoneHash", before.getPhoneHash(), after.getPhoneHash());
        collectIfChanged(beforeChanged, afterChanged, "emailHash", before.getEmailHash(), after.getEmailHash());
        collectIfChanged(beforeChanged, afterChanged, "backupPhoneHash", before.getBackupPhoneHash(), after.getBackupPhoneHash());
        collectIfChanged(beforeChanged, afterChanged, "talentStatus", before.getTalentStatus(), after.getTalentStatus());
        collectIfChanged(beforeChanged, afterChanged, "statusReason", before.getStatusReason(), after.getStatusReason());
        collectIfChanged(beforeChanged, afterChanged, "ownerId", before.getOwnerId(), after.getOwnerId());
        collectIfChanged(beforeChanged, afterChanged, "ownerDeptId", before.getOwnerDeptId(), after.getOwnerDeptId());
        collectIfChanged(beforeChanged, afterChanged, "visibilityType", before.getVisibilityType(), after.getVisibilityType());
        collectIfChanged(beforeChanged, afterChanged, "dataLevel", before.getDataLevel(), after.getDataLevel());
        collectIfChanged(beforeChanged, afterChanged, "assistantIds", before.getAssistantIds(), after.getAssistantIds());
        if (afterChanged.isEmpty()) {
            return;
        }
        // 电话/邮箱只记录哈希是否变化，绝不写入明文
        String changeType = beforeChanged.containsKey("talentStatus") ? CHANGE_STATUS : CHANGE_UPDATE;
        writeChange(after.getTalentId(), changeType, beforeChanged, afterChanged, currentUserId());
    }

    /**
     * 字段值不同时成对记入变更前/后快照。
     *
     * @param beforeMap 变更前快照
     * @param afterMap  变更后快照
     * @param field     字段名
     * @param before    变更前值
     * @param after     变更后值
     */
    private void collectIfChanged(Map<String, Object> beforeMap, Map<String, Object> afterMap,
                                  String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return;
        }
        beforeMap.put(field, before);
        afterMap.put(field, after);
    }

    /**
     * 构造实体的可展示快照（脱敏后，不含任何密文列）。
     *
     * @param profile 人才主档实体
     * @return 快照 Map，可能为空
     */
    private Map<String, Object> snapshot(TalentProfile profile) {
        if (profile == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", profile.getName());
        map.put("gender", profile.getGender());
        map.put("highestEducation", profile.getHighestEducation());
        map.put("talentStatus", profile.getTalentStatus());
        map.put("statusReason", profile.getStatusReason());
        map.put("ownerId", profile.getOwnerId());
        map.put("ownerDeptId", profile.getOwnerDeptId());
        map.put("visibilityType", profile.getVisibilityType());
        map.put("dataLevel", profile.getDataLevel());
        map.put("currentCompany", profile.getCurrentCompany());
        map.put("expectedPosition", profile.getExpectedPosition());
        return map;
    }

    /**
     * 解析当前登录用户可见的人才ID集合（设计文档 §21.14）。
     *
     * <p><b>唯一接法</b>：把 {@link TalentScopeDomainService#visibleTalentWrapper()} 返回的
     * wrapper <b>原样</b>交给 {@link TalentProfileMapper#selectVisibleTalentIds} 执行，
     * 由 {@code ${ew.customSqlSegment}} 直接消费该 wrapper 的条件与参数；
     * 调用方再用返回的ID集合做 {@code IN} 过滤。</p>
     *
     * <p><b>禁止</b>：把某个 wrapper 的 {@code getCustomSqlSegment()} 搬到另一个 wrapper 上
     * （会前导 {@code WHERE} 重复且参数绑定错位），也禁止在本类里另写一套可见范围规则（§11.1）。</p>
     *
     * @return 可见的人才主档ID列表（不为 null，可能为空）
     */
    private List<Long> resolveVisibleTalentIds() {
        List<Long> ids = talentProfileMapper.selectVisibleTalentIds(
            talentScopeDomainService.visibleTalentWrapper());
        return ids == null ? List.of() : ids;
    }

    /**
     * 发布人才主档变化事件（设计文档 §21.6）。
     *
     * @param talentId   人才主档ID
     * @param changeType 变化类型
     * @param operatorId 操作人用户ID
     */
    private void publishChanged(Long talentId, String changeType, Long operatorId) {
        eventPublisher.publishEvent(new TalentProfileChangedEvent(talentId, changeType, operatorId));
    }

}
