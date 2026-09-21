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
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
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
     * 资料完整度基础字段总数（口径见 {@link #fillCompleteness}）。
     */
    private static final int COMPLETENESS_FIELD_TOTAL = 15;

    /**
     * 完整度分档阈值：高（≥80）。
     */
    private static final int COMPLETENESS_LEVEL_HIGH = 80;

    /**
     * 完整度分档阈值：中（≥50）。
     */
    private static final int COMPLETENESS_LEVEL_MEDIUM = 50;

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

    /**
     * 敏感操作审计统一入口（电话明文查看等动作必须留痕，且审计细节不落日志）。
     */
    private final SensitiveAuditRecorder sensitiveAuditRecorder;

    @Override
    public PageResult<TalentProfileVo> queryPage(TalentProfileQueryBo bo, PageQuery pageQuery) {
        List<Long> visibleTalentIds = resolveVisibleTalentIds();
        if (visibleTalentIds.isEmpty()) {
            // 无任何可见人才：直接返回空页，不查库
            return PageResult.build(List.of(), 0L);
        }
        TalentProfileQueryBo query = bo == null ? new TalentProfileQueryBo() : bo;
        LambdaQueryWrapper<TalentProfile> wrapper = buildSearchWrapper(query, visibleTalentIds);
        // 走实体查询：联系方式需要解密后由服务层脱敏，完整度也需要实体字段才能计算
        Page<TalentProfile> entityPage = talentProfileMapper.selectPage(pageQuery.build(), wrapper);
        List<TalentProfile> entities = entityPage.getRecords() == null ? List.of() : entityPage.getRecords();
        List<TalentProfileVo> records = new java.util.ArrayList<>(entities.size());
        for (TalentProfile entity : entities) {
            TalentProfileVo vo = MapstructUtils.convert(entity, TalentProfileVo.class);
            if (vo == null) {
                continue;
            }
            maskContact(vo, entity);
            fillCompleteness(vo, entity);
            records.add(vo);
        }
        return PageResult.build(records, entityPage.getTotal());
    }

    @Override
    public List<TalentProfile> searchForExport(TalentProfileQueryBo bo, int limit) {
        if (limit <= 0) {
            throw new ServiceException("导出条数上限必须为正数");
        }
        TalentProfileQueryBo query = bo == null ? new TalentProfileQueryBo() : bo;
        // 不支持的筛选条件先 fail-fast，避免因「无可见人才」提前返回而无提示
        requireSupportedFilters(query);
        List<Long> visibleTalentIds = resolveVisibleTalentIds();
        if (visibleTalentIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<TalentProfile> wrapper = buildSearchWrapper(query, visibleTalentIds);
        // 只多取一条用于「是否超过上限」的判定，由调用方决定拒绝或截断（§11.1 限制导出规模）
        wrapper.orderByDesc(TalentProfile::getCreateTime).last("LIMIT " + limit);
        List<TalentProfile> list = talentProfileMapper.selectList(wrapper);
        return list == null ? List.of() : list;
    }

    @Override
    public TalentProfileDetailVo getDetail(Long talentId) {
        TalentProfile profile = requireVisible(talentId);
        TalentProfileVo base = MapstructUtils.convert(profile, TalentProfileVo.class);
        if (base == null) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_001);
        }
        maskContact(base, profile);
        fillCompleteness(base, profile);
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
        // §8.17 后四位检索列：与哈希同源写入，保证「填了号码就能按后四位检索到」
        entity.setPhoneTail4(TalentContactCodec.phoneTail4(bo.getPhone()));
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
            // 后四位与哈希必须同步改写，避免出现「号码已换、后四位仍是旧值」的脏数据
            update.setPhoneTail4(TalentContactCodec.phoneTail4(bo.getPhone()));
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
    public String viewPhone(Long talentId, String purpose) {
        if (StringUtils.isBlank(purpose)) {
            // 用途为空：先写 denied 审计再拒绝，保证被拒绝的敏感访问同样留痕
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_PHONE_VIEW,
                SensitiveAuditRecorder.BIZ_TALENT, talentId, purpose, SensitiveAuditRecorder.RESULT_DENIED);
            throw new ServiceException("查看电话明文必须填写用途");
        }
        // 顺序：资源级鉴权 → 写审计 → 返回明文（与简历下载同口径）
        TalentProfile profile;
        try {
            // 只做人才主档可见性校验：人才档案域不要求存在应聘记录（区别于候选人侧 phone-view）
            profile = requireVisible(talentId);
        } catch (ServiceException e) {
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_PHONE_VIEW,
                SensitiveAuditRecorder.BIZ_TALENT, talentId, purpose, SensitiveAuditRecorder.RESULT_DENIED);
            throw e;
        }
        // 明文返发之前先写审计；审计只记事件/对象/用途/IP，不记明文
        sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_PHONE_VIEW,
            SensitiveAuditRecorder.BIZ_TALENT, talentId, purpose, SensitiveAuditRecorder.RESULT_SUCCESS);
        // phoneCipher 由 @EncryptField 出参拦截器自动解密；日志不记录返回值
        log.info("查看人才电话明文, talentId={}", talentId);
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
        // §8.17 资料完整度：列表与详情口径一致，均为主档字段填充率
        vo.setCompleteness(base.getCompleteness());
        vo.setCompletenessLevel(base.getCompletenessLevel());

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
     * 用实体中的联系方式明文填充列表 VO 的脱敏串，并拆出协助人ID数组。
     *
     * <p><b>为什么需要实体</b>：{@code TalentProfileVo} 只有 {@code phoneMasked} / {@code emailMasked}
     * 两个脱敏字段，MapStruct 无法从实体的 {@code phoneCipher} / {@code emailCipher} 自动映射，
     * 因此必须由服务层在实体（密文列已被 {@code @EncryptField} 解密拦截器还原为明文）上脱敏后回填。</p>
     *
     * @param vo      人才主档视图对象
     * @param profile 人才主档实体（明文）
     */
    private void maskContact(TalentProfileVo vo, TalentProfile profile) {
        vo.maskPhone(profile == null ? null : profile.getPhoneCipher());
        vo.maskEmail(profile == null ? null : profile.getEmailCipher());
        if (vo.getAssistantIds() != null) {
            vo.setAssistantIds(TalentContactCodec.splitIds(joinAssistantIds(vo.getAssistantIds())));
        }
    }

    /**
     * 计算并回填资料完整度（0~100 与分档）。
     *
     * <p><b>口径（设计文档 §8.12、§8.17）</b>：取主档基础字段的填充率，
     * 基础字段共 15 项 —— 姓名、性别、（出生日期或年龄快照）、手机号、邮箱、最高学历、
     * 当前城市、意向城市、当前公司、当前职位、工作年限、期望岗位、（期望薪资下限或上限）、
     * 主档来源、人才负责人。已填项数 ÷ 15 取整即完整度。</p>
     *
     * <p><b>为什么不包含简历 / 教育 / 工作 / 标签</b>：这些是跨表派生维度，
     * 逐行统计会造成 N+1 查询且极易与真实数据不一致；因此本值为可解释的「主档字段完整度」。
     * 完整度<b>不作为筛选条件</b>（{@code minCompleteness} 传入即拒绝），相关限制见
     * {@code docs/hr-talent/P2-决策与缺口台账.md}。</p>
     *
     * @param vo      人才主档视图对象
     * @param profile 人才主档实体
     */
    private void fillCompleteness(TalentProfileVo vo, TalentProfile profile) {
        if (vo == null || profile == null) {
            return;
        }
        int filled = 0;
        filled += hasText(profile.getName()) ? 1 : 0;
        filled += hasText(profile.getGender()) ? 1 : 0;
        filled += (profile.getBirthDate() != null || profile.getAgeSnapshot() != null) ? 1 : 0;
        filled += hasText(profile.getPhoneCipher()) ? 1 : 0;
        filled += hasText(profile.getEmailCipher()) ? 1 : 0;
        filled += hasText(profile.getHighestEducation()) ? 1 : 0;
        filled += hasText(profile.getCurrentCity()) ? 1 : 0;
        filled += hasText(profile.getExpectedCity()) ? 1 : 0;
        filled += hasText(profile.getCurrentCompany()) ? 1 : 0;
        filled += hasText(profile.getCurrentPosition()) ? 1 : 0;
        filled += profile.getWorkYears() != null ? 1 : 0;
        filled += hasText(profile.getExpectedPosition()) ? 1 : 0;
        filled += (profile.getExpectedSalaryMin() != null || profile.getExpectedSalaryMax() != null) ? 1 : 0;
        filled += hasText(profile.getSourceType()) ? 1 : 0;
        filled += profile.getOwnerId() != null ? 1 : 0;
        int percent = Math.min(100, filled * 100 / COMPLETENESS_FIELD_TOTAL);
        vo.setCompleteness(percent);
        vo.setCompletenessLevel(percent >= COMPLETENESS_LEVEL_HIGH ? "high"
            : percent >= COMPLETENESS_LEVEL_MEDIUM ? "medium" : "low");
    }

    /**
     * 判断字符串是否有内容。
     *
     * @param value 原值
     * @return 是否有内容
     */
    private boolean hasText(String value) {
        return StringUtils.isNotBlank(value);
    }

    /**
     * 构造人才检索条件（{@code GET /talent/profiles} 与人才导出共用，保证两处口径完全一致）。
     *
     * <p><b>可见范围硬约束</b>：调用方必须传入由 {@code TalentScopeDomainService} 条件解析得到的
     * 可见人才ID集合，本方法<b>不</b>实现任何授权规则（设计文档 §8.17、§11.1、§21.14）。</p>
     *
     * <p><b>§8.17 组合条件</b>：姓名、手机号（完整号哈希精确 + 后四位精确）、人才编号、
     * 当前/历史岗位、意向岗位与标签、学历/专业/毕业院校、当前与意向城市、工作年限/行业/当前公司、
     * 来源渠道/归属部门/负责人、人才池/人才状态/最近联系时间、是否存在当前简历/解析状态。
     * 其中的跨表条件（标签、人才池、教育、工作经历、简历）一律用 {@code EXISTS} 子查询表达，
     * 避免出现重复行并让 {@code IN} 过滤仍能命中主表索引。</p>
     *
     * <p><b>后四位检索</b>：基于 {@code hr_talent_profile.phone_tail4} 精确匹配，
     * <b>不解密全量比对</b>；不使用后四位哈希（{@code 10^4} 种取值可被瞬间穷举）。</p>
     *
     * @param query           检索条件（不为 null）
     * @param visibleTalentIds 当前用户可见的人才ID集合（不为空）
     * @return 检索条件包装器
     */
    private LambdaQueryWrapper<TalentProfile> buildSearchWrapper(TalentProfileQueryBo query, List<Long> visibleTalentIds) {
        requireSupportedFilters(query);
        String phoneHash = TalentContactCodec.phoneHash(query.getPhone());
        String emailHash = TalentContactCodec.emailHash(query.getEmail());
        LambdaQueryWrapper<TalentProfile> wrapper = new LambdaQueryWrapper<TalentProfile>()
            // 可见范围硬约束：人才ID集合由 TalentScopeDomainService 的条件解析得出（§21.14）
            .in(TalentProfile::getTalentId, visibleTalentIds)
            .like(StringUtils.isNotBlank(query.getTalentNo()), TalentProfile::getTalentNo, query.getTalentNo())
            .like(StringUtils.isNotBlank(query.getName()), TalentProfile::getName, query.getName())
            .eq(phoneHash != null, TalentProfile::getPhoneHash, phoneHash)
            .eq(emailHash != null, TalentProfile::getEmailHash, emailHash)
            // §8.17 手机号后四位（与脱敏展示同口径的部分信息，明文列精确匹配，可走索引）
            .eq(StringUtils.isNotBlank(query.getPhoneTail4()), TalentProfile::getPhoneTail4,
                StringUtils.trim(query.getPhoneTail4()))
            .eq(StringUtils.isNotBlank(query.getTalentStatus()), TalentProfile::getTalentStatus, query.getTalentStatus())
            .like(StringUtils.isNotBlank(query.getCurrentCity()), TalentProfile::getCurrentCity, query.getCurrentCity())
            .like(StringUtils.isNotBlank(query.getExpectedCity()), TalentProfile::getExpectedCity, query.getExpectedCity())
            .like(StringUtils.isNotBlank(query.getExpectedPosition()), TalentProfile::getExpectedPosition, query.getExpectedPosition())
            .like(StringUtils.isNotBlank(query.getCurrentPosition()), TalentProfile::getCurrentPosition, query.getCurrentPosition())
            .like(StringUtils.isNotBlank(query.getCurrentCompany()), TalentProfile::getCurrentCompany, query.getCurrentCompany())
            .eq(StringUtils.isNotBlank(query.getHighestEducation()), TalentProfile::getHighestEducation, query.getHighestEducation())
            .like(StringUtils.isNotBlank(query.getIndustry()), TalentProfile::getIndustry, query.getIndustry())
            .eq(query.getOwnerId() != null, TalentProfile::getOwnerId, query.getOwnerId())
            .eq(query.getOwnerDeptId() != null, TalentProfile::getOwnerDeptId, query.getOwnerDeptId())
            .eq(query.getSourceChannelId() != null, TalentProfile::getSourceChannelId, query.getSourceChannelId())
            .eq(StringUtils.isNotBlank(query.getVisibilityType()), TalentProfile::getVisibilityType, query.getVisibilityType())
            .eq(StringUtils.isNotBlank(query.getDataLevel()), TalentProfile::getDataLevel, query.getDataLevel())
            .eq(StringUtils.isNotBlank(query.getSourceType()), TalentProfile::getSourceType, query.getSourceType())
            .ge(query.getWorkYearsBegin() != null, TalentProfile::getWorkYears, query.getWorkYearsBegin())
            .le(query.getWorkYearsEnd() != null, TalentProfile::getWorkYears, query.getWorkYearsEnd())
            .ge(query.getLastFollowTimeBegin() != null, TalentProfile::getLastFollowTime, query.getLastFollowTimeBegin())
            .le(query.getLastFollowTimeEnd() != null, TalentProfile::getLastFollowTime, query.getLastFollowTimeEnd())
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
        // 历史岗位：命中任一工作经历即可（EXISTS 避免重复行，保持主表索引可用）
        if (StringUtils.isNotBlank(query.getHistoryPosition())) {
            wrapper.apply("EXISTS (SELECT 1 FROM hr_talent_work w WHERE w.talent_id = hr_talent_profile.talent_id"
                + " AND w.del_flag = '0' AND w.position_name LIKE CONCAT('%', {0}, '%'))",
                StringUtils.trim(query.getHistoryPosition()));
        }
        // 专业
        if (StringUtils.isNotBlank(query.getMajor())) {
            wrapper.apply("EXISTS (SELECT 1 FROM hr_talent_education e WHERE e.talent_id = hr_talent_profile.talent_id"
                + " AND e.del_flag = '0' AND e.major LIKE CONCAT('%', {0}, '%'))",
                StringUtils.trim(query.getMajor()));
        }
        // 毕业院校
        if (StringUtils.isNotBlank(query.getSchoolName())) {
            wrapper.apply("EXISTS (SELECT 1 FROM hr_talent_education e WHERE e.talent_id = hr_talent_profile.talent_id"
                + " AND e.del_flag = '0' AND e.school_name LIKE CONCAT('%', {0}, '%'))",
                StringUtils.trim(query.getSchoolName()));
        }
        // 人才标签：命中任一标签即可
        if (CollUtil.isNotEmpty(query.getTagIds())) {
            List<Long> tagIds = query.getTagIds().stream().filter(Objects::nonNull).distinct().toList();
            if (!tagIds.isEmpty()) {
                wrapper.apply("EXISTS (SELECT 1 FROM hr_talent_profile_tag pt WHERE pt.talent_id = hr_talent_profile.talent_id"
                    + " AND pt.del_flag = '0' AND pt.tag_id IN (" + StringUtils.join(tagIds, ",") + "))");
            }
        }
        // 人才池：必须是该池的有效成员
        if (query.getPoolId() != null) {
            wrapper.apply("EXISTS (SELECT 1 FROM hr_talent_pool_member pm WHERE pm.talent_id = hr_talent_profile.talent_id"
                + " AND pm.del_flag = '0' AND pm.member_status = 'active' AND pm.pool_id = {0})",
                query.getPoolId());
        }
        // 是否存在当前简历
        if (Boolean.TRUE.equals(query.getHasCurrentResume())) {
            wrapper.apply("current_resume_id IS NOT NULL");
        } else if (Boolean.FALSE.equals(query.getHasCurrentResume())) {
            wrapper.apply("current_resume_id IS NULL");
        }
        // 当前简历的解析状态
        if (StringUtils.isNotBlank(query.getResumeParseStatus())) {
            wrapper.apply("EXISTS (SELECT 1 FROM hr_talent_resume r WHERE r.talent_id = hr_talent_profile.talent_id"
                + " AND r.del_flag = '0' AND r.current_flag = '1' AND r.parse_status = {0})",
                StringUtils.trim(query.getResumeParseStatus()));
        }
        return wrapper;
    }

    /**
     * 校验本期明确不支持的检索条件，避免静默返回错误结果（fail-fast）。
     *
     * @param query 检索条件
     */
    private void requireSupportedFilters(TalentProfileQueryBo query) {
        if (query.getMinCompleteness() != null) {
            // 完整度是跨表派生值（§8.17），主档无完整度列且本期不物化，故筛选不支持
            throw new ServiceException("资料完整度筛选暂不支持：完整度为跨表派生值，本期仅在人才列表/详情中计算展示");
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
