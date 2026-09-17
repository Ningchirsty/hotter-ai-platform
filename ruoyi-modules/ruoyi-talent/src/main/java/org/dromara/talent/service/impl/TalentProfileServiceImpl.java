package org.dromara.talent.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.TlTalent;
import org.dromara.talent.domain.TlTalentContact;
import org.dromara.talent.domain.TlTalentDuplicate;
import org.dromara.talent.domain.bo.TlTalentArchiveBo;
import org.dromara.talent.domain.bo.TlTalentBo;
import org.dromara.talent.domain.bo.TlTalentContactBo;
import org.dromara.talent.domain.bo.TlTalentQueryBo;
import org.dromara.talent.domain.vo.TalentPermissionVo;
import org.dromara.talent.domain.vo.TlTalentContactVo;
import org.dromara.talent.domain.vo.TlTalentDetailVo;
import org.dromara.talent.domain.vo.TlTalentDuplicateVo;
import org.dromara.talent.domain.vo.TlTalentVo;
import org.dromara.talent.enums.AuditActionEnum;
import org.dromara.talent.enums.AuditTargetTypeEnum;
import org.dromara.talent.enums.DuplicateConclusionEnum;
import org.dromara.talent.enums.DuplicateMatchRuleEnum;
import org.dromara.talent.enums.GrantPermissionEnum;
import org.dromara.talent.enums.TalentRegionEnum;
import org.dromara.talent.enums.TalentShareScopeEnum;
import org.dromara.talent.enums.TalentStatusEnum;
import org.dromara.talent.helper.TalentAuditRecorder;
import org.dromara.talent.helper.TalentPhoneHelper;
import org.dromara.talent.helper.TalentScopeHelper;
import org.dromara.talent.mapper.TlTalentContactMapper;
import org.dromara.talent.mapper.TlTalentDuplicateMapper;
import org.dromara.talent.mapper.TlTalentMapper;
import org.dromara.talent.service.ITalentAttachmentService;
import org.dromara.talent.service.ITalentProfileService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 人才主档服务实现。
 * <p>
 * 安全口径见 SPEC §4：任何以 ID 访问的路径都先加载对象 → {@link TalentScopeHelper} 授权校验 → 再返回；
 * 列表查询强制追加 {@code region_code IN (可见区域)}，无区域权限时直接返回空页且不查库。
 *
 * @author talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentProfileServiceImpl implements ITalentProfileService {

    /**
     * 人才编号重试次数。
     */
    private static final int TALENT_NO_RETRY = 3;

    /**
     * 疑似重复提示语（不包含任何他人姓名 / 手机号，避免越权探测）。
     */
    private static final String DUPLICATE_HINT = "检测到 %d 条疑似重复人才，请先完成重复确认后再提交";

    /**
     * 人才主档 Mapper。
     */
    private final TlTalentMapper talentMapper;

    /**
     * 联系人记录 Mapper。
     */
    private final TlTalentContactMapper contactMapper;

    /**
     * 重复预警 Mapper。
     */
    private final TlTalentDuplicateMapper duplicateMapper;

    /**
     * 数据范围与单条授权。
     */
    private final TalentScopeHelper scopeHelper;

    /**
     * 手机号工具。
     */
    private final TalentPhoneHelper phoneHelper;

    /**
     * 审计记录器。
     */
    private final TalentAuditRecorder auditRecorder;

    /**
     * 附件服务（详情页需要当前版本附件列表，内部会再次做单条授权校验）。
     */
    private final ITalentAttachmentService attachmentService;

    /**
     * 分页查询人才台账。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @Override
    public PageResult<TlTalentVo> queryPage(TlTalentQueryBo bo, PageQuery pageQuery) {
        List<String> visibleRegions = scopeHelper.currentVisibleRegions();
        if (CollUtil.isEmpty(visibleRegions)) {
            // 无区域权限：不查库，直接空页（只能通过单条授权访问）
            return PageResult.build(List.of(), 0L);
        }
        if (bo != null && StringUtils.isNotBlank(bo.getRegionCode())) {
            validateRegion(bo.getRegionCode());
        }
        LambdaQueryWrapper<TlTalent> wrapper = buildQueryWrapper(bo, visibleRegions);
        Page<TlTalent> page = pageQuery.build();
        Page<TlTalentVo> voPage = talentMapper.selectVoPage(page, wrapper);
        List<TlTalentVo> rows = voPage.getRecords();
        if (CollUtil.isNotEmpty(rows)) {
            rows.forEach(this::fillTransientFields);
        }
        return PageResult.build(rows, voPage.getTotal());
    }

    /**
     * 人才详情。
     *
     * @param talentId 人才ID
     * @return 详情
     */
    @Override
    public TlTalentDetailVo getDetail(Long talentId) {
        TlTalent talent = loadTalent(talentId);
        scopeHelper.checkTalentVisible(talent);
        TlTalentDetailVo vo = BeanUtil.copyProperties(talent, TlTalentDetailVo.class);
        fillTransientFields(vo);
        // 明文手机号只在拥有 talent:profile:phone（或单条授权）时下发；其余情况只给脱敏值
        String plainPhone = talent.getPhoneCipher();
        if (StringUtils.isNotBlank(plainPhone)) {
            vo.setPhoneMasked(phoneHelper.mask(plainPhone));
            boolean fullPhoneAllowed = scopeHelper.canViewFullPhone(talentId);
            vo.setPhone(fullPhoneAllowed ? plainPhone : null);
            if (fullPhoneAllowed) {
                // 详情响应一旦携带明文手机号，同样按 VIEW_FULL_PHONE 落审计，避免绕过专用接口无痕迹查看
                auditRecorder.record(AuditActionEnum.VIEW_FULL_PHONE, AuditTargetTypeEnum.TALENT, talentId, talentId, true,
                    "查看人才详情（含完整手机号）");
            }
        }
        vo.setAttachments(attachmentService.listByTalent(talentId));
        vo.setContacts(listContactsInternal(talent));
        TalentPermissionVo permission = new TalentPermissionVo();
        permission.setView(true);
        permission.setDownload(scopeHelper.isDownloadableRole()
            || scopeHelper.hasGrant(talentId, GrantPermissionEnum.DOWNLOAD));
        permission.setViewFullPhone(scopeHelper.canViewFullPhone(talentId));
        vo.setPermissions(permission);
        auditRecorder.record(AuditActionEnum.VIEW_DETAIL, AuditTargetTypeEnum.TALENT, talentId, talentId, true, "查看人才详情");
        return vo;
    }

    /**
     * 返回完整手机号明文；内部写 VIEW_FULL_PHONE 审计。
     *
     * @param talentId 人才ID
     * @return 手机号明文
     */
    @Override
    public String getFullPhone(Long talentId) {
        TlTalent talent = loadTalent(talentId);
        scopeHelper.checkTalentVisible(talent);
        if (!scopeHelper.canViewFullPhone(talentId)) {
            auditRecorder.record(AuditActionEnum.VIEW_FULL_PHONE, AuditTargetTypeEnum.TALENT, talentId, talentId, false, "无查看完整手机号权限");
            throw new ServiceException("无权查看完整手机号");
        }
        String phone = talent.getPhoneCipher();
        if (StringUtils.isBlank(phone)) {
            auditRecorder.record(AuditActionEnum.VIEW_FULL_PHONE, AuditTargetTypeEnum.TALENT, talentId, talentId, false, "该人才未登记手机号");
            throw new ServiceException("该人才未登记手机号");
        }
        auditRecorder.record(AuditActionEnum.VIEW_FULL_PHONE, AuditTargetTypeEnum.TALENT, talentId, talentId, true, "查看完整手机号");
        return phone;
    }

    /**
     * 新建人才（含重复预检）。
     *
     * @param bo 人才信息
     * @return 新人才ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TlTalentBo bo) {
        validateRegion(bo.getRegionCode());
        if (!scopeHelper.canWriteRegion(bo.getRegionCode())) {
            throw new ServiceException("无权在该区域新增人才档案");
        }
        String phone = StringUtils.isBlank(bo.getPhone()) ? null : phoneHelper.normalize(bo.getPhone());
        String phoneHash = phone == null ? null : phoneHelper.hash(phone);
        String tail4 = phone == null ? null : phoneHelper.tail4(phone);

        List<Hit> hits = findHits(bo.getName(), phoneHash, tail4, bo.getRegionCode(), null);
        if (CollUtil.isNotEmpty(hits) && !Boolean.TRUE.equals(bo.getDuplicateConfirmed())) {
            // 只回条数，不回他人姓名 / 区域，避免越权探测；前端改调 preCheck 获取可见命中
            throw new ServiceException(String.format(DUPLICATE_HINT, hits.size()));
        }

        TlTalent entity = BeanUtil.copyProperties(bo, TlTalent.class);
        entity.setTalentId(null);
        entity.setPhone(null);
        entity.setPhoneCipher(phone);
        entity.setPhoneHash(phoneHash);
        entity.setPhoneTail4(tail4);
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(TalentStatusEnum.NEW.getCode());
        }
        if (StringUtils.isBlank(entity.getShareScope())) {
            entity.setShareScope(TalentShareScopeEnum.REGION.getCode());
        }
        insertWithTalentNo(entity);
        persistDuplicateHits(entity.getTalentId(), hits);
        return entity.getTalentId();
    }

    /**
     * 编辑人才档案。
     *
     * @param bo 人才信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(TlTalentBo bo) {
        if (bo.getTalentId() == null) {
            throw new ServiceException("人才ID不能为空");
        }
        TlTalent existing = loadTalent(bo.getTalentId());
        scopeHelper.checkTalentVisible(existing);
        if (!scopeHelper.canWriteRegion(existing.getRegionCode())) {
            throw new ServiceException("无权编辑该区域的人才档案");
        }
        if (StringUtils.isNotBlank(bo.getRegionCode())) {
            validateRegion(bo.getRegionCode());
            if (!bo.getRegionCode().equalsIgnoreCase(existing.getRegionCode()) && !scopeHelper.canWriteRegion(bo.getRegionCode())) {
                throw new ServiceException("无权将人才档案迁移到目标区域");
            }
        }
        TlTalent entity = BeanUtil.copyProperties(bo, TlTalent.class);
        entity.setPhone(null);
        String phone = null;
        if (StringUtils.isNotBlank(bo.getPhone())) {
            phone = phoneHelper.normalize(bo.getPhone());
            String phoneHash = phoneHelper.hash(phone);
            String tail4 = phoneHelper.tail4(phone);
            entity.setPhoneCipher(phone);
            entity.setPhoneHash(phoneHash);
            entity.setPhoneTail4(tail4);
            // 与新增同口径：手机号变更同样执行重复预检，禁止形成"更新绕过"的重复数据通道
            List<Hit> hits = findHits(bo.getName(), phoneHash, tail4, bo.getRegionCode(), existing.getTalentId());
            if (CollUtil.isNotEmpty(hits) && !Boolean.TRUE.equals(bo.getDuplicateConfirmed())) {
                throw new ServiceException(String.format(DUPLICATE_HINT, hits.size()));
            }
            persistDuplicateHits(existing.getTalentId(), hits);
        }
        talentMapper.updateById(entity);
    }

    /**
     * 归档人才档案。
     *
     * @param bo 归档参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(TlTalentArchiveBo bo) {
        TlTalent existing = loadTalent(bo.getTalentId());
        scopeHelper.checkTalentVisible(existing);
        if (!scopeHelper.canWriteRegion(existing.getRegionCode())) {
            throw new ServiceException("无权归档该区域的人才档案");
        }
        TlTalent entity = new TlTalent();
        entity.setTalentId(existing.getTalentId());
        entity.setStatus(TalentStatusEnum.ARCHIVED.getCode());
        if (StringUtils.isNotBlank(bo.getRemark())) {
            entity.setRemark(bo.getRemark());
        }
        talentMapper.updateById(entity);
        auditRecorder.record(AuditActionEnum.ARCHIVE, AuditTargetTypeEnum.TALENT, existing.getTalentId(), existing.getTalentId(), true, "归档人才档案");
    }

    /**
     * 重复预检（不落库）。
     *
     * @param bo 人才信息
     * @return 当前用户可见范围内的疑似重复清单
     */
    @Override
    public List<TlTalentDuplicateVo> preCheck(TlTalentBo bo) {
        if (bo == null) {
            return List.of();
        }
        List<String> visibleRegions = scopeHelper.currentVisibleRegions();
        if (CollUtil.isEmpty(visibleRegions)) {
            return List.of();
        }
        String phone = StringUtils.isBlank(bo.getPhone()) ? null : phoneHelper.normalize(bo.getPhone());
        String phoneHash = phone == null ? null : phoneHelper.hash(phone);
        String tail4 = phone == null ? null : phoneHelper.tail4(phone);
        List<Hit> hits = findHits(bo.getName(), phoneHash, tail4, bo.getRegionCode(), bo.getTalentId());
        List<TlTalentDuplicateVo> result = new ArrayList<>();
        for (Hit hit : hits) {
            if (!visibleRegions.contains(hit.matched.getRegionCode())) {
                // 不可见区域的命中不下发，避免通过预检探测越权数据
                continue;
            }
            result.add(toVo(hit, bo));
        }
        return result;
    }

    /**
     * 新增联系反馈。
     *
     * @param bo 联系记录
     * @return 联系记录ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addContact(TlTalentContactBo bo) {
        TlTalent talent = loadTalent(bo.getTalentId());
        scopeHelper.checkTalentVisible(talent);
        if (!scopeHelper.canWriteRegion(talent.getRegionCode())) {
            throw new ServiceException("无权在该区域维护人才联系记录");
        }
        TlTalentContact entity = BeanUtil.copyProperties(bo, TlTalentContact.class);
        entity.setContactId(null);
        entity.setTalentId(talent.getTalentId());
        entity.setContactorId(LoginHelper.getUserId());
        if (entity.getContactTime() == null) {
            entity.setContactTime(LocalDateTime.now());
        }
        contactMapper.insert(entity);
        return entity.getContactId();
    }

    /**
     * 人才联系记录列表。
     *
     * @param talentId 人才ID
     * @return 联系记录列表
     */
    @Override
    public List<TlTalentContactVo> listContacts(Long talentId) {
        TlTalent talent = loadTalent(talentId);
        scopeHelper.checkTalentVisible(talent);
        return listContactsInternal(talent);
    }

    /**
     * 组装列表查询条件：服务端区域范围是硬条件，bo.regionCode 只能作为额外收窄。
     *
     * @param bo             查询条件
     * @param visibleRegions 可见区域
     * @return 查询包装器
     */
    private LambdaQueryWrapper<TlTalent> buildQueryWrapper(TlTalentQueryBo bo, List<String> visibleRegions) {
        LambdaQueryWrapper<TlTalent> wrapper = new LambdaQueryWrapper<>();
        // 硬约束：服务端强制区域范围，任何入参都不能扩大
        wrapper.in(TlTalent::getRegionCode, visibleRegions);
        if (bo == null) {
            return wrapper.orderByDesc(TlTalent::getCreateTime);
        }
        wrapper.like(StringUtils.isNotBlank(bo.getName()), TlTalent::getName, bo.getName())
            .eq(StringUtils.isNotBlank(bo.getTalentNo()), TlTalent::getTalentNo, bo.getTalentNo())
            .eq(StringUtils.isNotBlank(bo.getPhoneTail4()), TlTalent::getPhoneTail4, bo.getPhoneTail4())
            .eq(StringUtils.isNotBlank(bo.getRegionCode()), TlTalent::getRegionCode, bo.getRegionCode())
            .eq(StringUtils.isNotBlank(bo.getStatus()), TlTalent::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getEducation()), TlTalent::getEducation, bo.getEducation())
            .like(StringUtils.isNotBlank(bo.getPosition()), TlTalent::getPosition, bo.getPosition())
            .eq(StringUtils.isNotBlank(bo.getSource()), TlTalent::getSource, bo.getSource())
            .ge(bo.getContactDateStart() != null, TlTalent::getContactDate, bo.getContactDateStart())
            .le(bo.getContactDateEnd() != null, TlTalent::getContactDate, bo.getContactDateEnd());
        if (Boolean.TRUE.equals(bo.getDuplicateOnly())) {
            wrapper.exists("select 1 from tl_talent_duplicate d where d.source_talent_id = tl_talent.talent_id"
                + " and d.conclusion = '" + DuplicateConclusionEnum.PENDING.getCode() + "' and d.del_flag = '0'");
        }
        wrapper.orderByDesc(TlTalent::getCreateTime);
        return wrapper;
    }

    /**
     * 重复预检核心：phone_hash（100）、姓名 + 手机后四位（70）、姓名 + 同区域（40），同一命中取其最高分。
     *
     * @param name           姓名
     * @param phoneHash      手机号哈希（可空）
     * @param tail4          手机号后四位（可空）
     * @param regionCode     区域（可空）
     * @param excludeTalentId 需排除的人才ID（编辑自身）
     * @return 命中清单（未落库）
     */
    private List<Hit> findHits(String name, String phoneHash, String tail4, String regionCode, Long excludeTalentId) {
        Map<Long, Hit> hits = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(phoneHash)) {
            for (TlTalent matched : talentMapper.selectByPhoneHash(phoneHash, excludeTalentId)) {
                mergeHit(hits, matched, DuplicateMatchRuleEnum.PHONE_HASH.getCode(), 100);
            }
        }
        if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(tail4)) {
            for (TlTalent matched : talentMapper.selectByNameAndTail4(name, tail4, excludeTalentId)) {
                mergeHit(hits, matched, DuplicateMatchRuleEnum.NAME_PHONE_TAIL4.getCode(), 70);
            }
        }
        if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(regionCode)) {
            LambdaQueryWrapper<TlTalent> wrapper = new LambdaQueryWrapper<TlTalent>()
                .eq(TlTalent::getName, name)
                .eq(TlTalent::getRegionCode, regionCode)
                .ne(excludeTalentId != null, TlTalent::getTalentId, excludeTalentId);
            for (TlTalent matched : talentMapper.selectList(wrapper)) {
                mergeHit(hits, matched, DuplicateMatchRuleEnum.NAME_REGION.getCode(), 40);
            }
        }
        List<Hit> result = new ArrayList<>(hits.values());
        result.sort(Comparator.comparingInt((Hit hit) -> hit.score).reversed());
        return result;
    }

    /**
     * 合并命中，取最高分规则。
     *
     * @param hits        命中集合
     * @param matched     命中人才
     * @param rule        规则
     * @param score       分数
     */
    private void mergeHit(Map<Long, Hit> hits, TlTalent matched, String rule, int score) {
        if (matched == null || matched.getTalentId() == null) {
            return;
        }
        Hit exists = hits.get(matched.getTalentId());
        if (exists == null || score > exists.score) {
            hits.put(matched.getTalentId(), new Hit(matched, rule, score));
        }
    }

    /**
     * 命中转 VO（不落库，仅用于预检返回）。
     *
     * @param hit 命中
     * @param bo  本次提交
     * @return 重复预警 VO
     */
    private TlTalentDuplicateVo toVo(Hit hit, TlTalentBo bo) {
        TlTalentDuplicateVo vo = new TlTalentDuplicateVo();
        vo.setSourceTalentId(bo == null ? null : bo.getTalentId());
        vo.setSourceName(bo == null ? null : bo.getName());
        vo.setMatchedTalentId(hit.matched.getTalentId());
        vo.setMatchedName(hit.matched.getName());
        vo.setMatchedTalentNo(hit.matched.getTalentNo());
        vo.setMatchedRegionCode(hit.matched.getRegionCode());
        vo.setMatchRule(hit.rule);
        vo.setMatchScore(hit.score);
        vo.setConclusion(DuplicateConclusionEnum.PENDING.getCode());
        return vo;
    }

    /**
     * 落库重复预警（conclusion = PENDING，只预警不合并）。
     *
     * @param sourceTalentId 来源人才ID
     * @param hits           命中清单
     */
    private void persistDuplicateHits(Long sourceTalentId, List<Hit> hits) {
        if (CollUtil.isEmpty(hits) || sourceTalentId == null) {
            return;
        }
        List<TlTalentDuplicate> rows = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            TlTalentDuplicate row = new TlTalentDuplicate();
            row.setSourceTalentId(sourceTalentId);
            row.setMatchedTalentId(hit.matched.getTalentId());
            row.setMatchRule(hit.rule);
            row.setMatchScore(hit.score);
            row.setConclusion(DuplicateConclusionEnum.PENDING.getCode());
            rows.add(row);
        }
        duplicateMapper.insertBatch(rows);
    }

    /**
     * 插入人才主档，人才编号唯一索引冲突时重试 3 次。
     *
     * @param entity 人才实体
     */
    private void insertWithTalentNo(TlTalent entity) {
        for (int attempt = 1; attempt <= TALENT_NO_RETRY; attempt++) {
            entity.setTalentNo(generateTalentNo());
            try {
                talentMapper.insert(entity);
                return;
            } catch (DuplicateKeyException e) {
                log.warn("人才编号冲突，第 {} 次重试", attempt);
            }
        }
        throw new ServiceException("生成人才编号失败，请重试");
    }

    /**
     * 生成人才编号：TL + yyyyMMdd + 6 位随机数（唯一索引兜底）。
     *
     * @return 人才编号
     */
    private String generateTalentNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int random = ThreadLocalRandom.current().nextInt(1_000_000);
        return TalentConstants.TALENT_NO_PREFIX + datePart + String.format("%06d", random);
    }

    /**
     * 加载人才，不存在直接抛业务异常。
     *
     * @param talentId 人才ID
     * @return 人才实体
     */
    private TlTalent loadTalent(Long talentId) {
        if (talentId == null) {
            throw new ServiceException(TalentScopeHelper.TALENT_NOT_FOUND);
        }
        TlTalent talent = talentMapper.selectById(talentId);
        if (talent == null || "1".equals(talent.getDelFlag())) {
            throw new ServiceException(TalentScopeHelper.TALENT_NOT_FOUND);
        }
        return talent;
    }

    /**
     * 联系记录列表（内部方法，调用方已完成授权）。
     *
     * @param talent 人才实体
     * @return 联系记录列表
     */
    private List<TlTalentContactVo> listContactsInternal(TlTalent talent) {
        LambdaQueryWrapper<TlTalentContact> wrapper = new LambdaQueryWrapper<TlTalentContact>()
            .eq(TlTalentContact::getTalentId, talent.getTalentId())
            .orderByDesc(TlTalentContact::getContactTime);
        return contactMapper.selectVoList(wrapper);
    }

    /**
     * 服务端计算年龄与薪资展示文案。
     *
     * @param vo 人才 VO
     */
    private void fillTransientFields(TlTalentVo vo) {
        if (vo == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (vo.getBirthDate() != null) {
            vo.setAge(Period.between(vo.getBirthDate(), today).getYears());
        } else if (vo.getAgeOnly() != null) {
            // 无出生日期时：识别年龄 + 识别日期至今的增量年数
            int extra = vo.getAgeSourceDate() == null ? 0 : Math.max(0, Period.between(vo.getAgeSourceDate(), today).getYears());
            vo.setAge(vo.getAgeOnly() + extra);
        }
        vo.setExpectSalaryText(formatSalary(vo.getExpectSalaryMin(), vo.getExpectSalaryMax()));
    }

    /**
     * 薪资展示：8500 → 8.5K，12000 → 12K，区间用 "-" 连接。
     *
     * @param min 下限
     * @param max 上限
     * @return 展示文案
     */
    private String formatSalary(Integer min, Integer max) {
        if (min == null && max == null) {
            return null;
        }
        if (min != null && max != null) {
            return formatK(min) + "-" + formatK(max);
        }
        return formatK(min != null ? min : max);
    }

    /**
     * 单个薪资值转 K 文案。
     *
     * @param value 金额（元）
     * @return 展示文案
     */
    private String formatK(Integer value) {
        BigDecimal k = BigDecimal.valueOf(value).divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
        return k.stripTrailingZeros().toPlainString() + "K";
    }

    /**
     * 区域合法性校验（只接受 GROUP / SZ / ST）。
     *
     * @param regionCode 区域编码
     */
    private void validateRegion(String regionCode) {
        if (StringUtils.isBlank(regionCode) || TalentRegionEnum.find(regionCode.trim().toUpperCase()) == null) {
            throw new ServiceException("非法的区域编码");
        }
    }

    /**
     * 重复命中（未落库）内部结构。
     */
    private static final class Hit {

        /**
         * 命中人才。
         */
        private final TlTalent matched;

        /**
         * 命中规则。
         */
        private final String rule;

        /**
         * 命中分数。
         */
        private final int score;

        /**
         * 构造命中。
         *
         * @param matched 命中人才
         * @param rule    规则
         * @param score   分数
         */
        private Hit(TlTalent matched, String rule, int score) {
            this.matched = matched;
            this.rule = rule;
            this.score = score;
        }
    }

}
