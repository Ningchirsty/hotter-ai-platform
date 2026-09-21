package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domain.bo.talent.TalentScopeGrantBo;
import org.dromara.hrtalent.domain.bo.talent.TalentScopeGrantQueryBo;
import org.dromara.hrtalent.domain.entity.TalentScopeGrant;
import org.dromara.hrtalent.domain.vo.talent.TalentScopeGrantVo;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.enums.TalentPermissionLevelEnum;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.mapper.TalentScopeGrantMapper;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
import org.dromara.hrtalent.service.talent.ITalentScopeGrantService;
import org.dromara.hrtalent.support.GrantSubject;
import org.dromara.system.api.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 人才共享授权 服务实现（SPEC-P4 §2.4、设计文档 §8.19 / §11.1 / §21.14）。
 *
 * <p><b>共享只扩大查看范围，不自动授予电话明文、附件下载、背调和导出权限</b>（设计文档 §8.19）：</p>
 * <ul>
 *     <li>授权记录里的 {@code permissionLevel} 只决定「能看到这位人才的哪个资料级别」，
 *     即只影响 {@code TalentScopeDomainService} 的 {@code EXPLICIT} 可见性分支与
 *     {@code checkPermissionLevel} 的级别比较；</li>
 *     <li>电话明文、附件下载 / 预览、背调明细、导出各自仍需<b>独立按钮权限</b>
 *     （{@code PERM_PROFILE_PHONE_VIEW} / {@code PERM_ATTACHMENT_DOWNLOAD} /
 *     {@code PERM_BACKGROUND_VIEW_SENSITIVE} / {@code PERM_PROFILE_EXPORT}），
 *     且必须在调用点<b>先</b> {@code checkTalentVisible}、<b>再</b>
 *     {@code checkPermissionLevel(talentId, DETAIL 或 ATTACHMENT)}，只拿 {@code summary} 级授权不得放行；</li>
 *     <li>本服务<b>不</b>写任何按钮权限、角色或菜单，也不改变登录态，授权只是数据层的可见范围扩展。</li>
 * </ul>
 *
 * <p><b>过期立即失效</b>（设计文档 §11.1）：有效性判定统一为
 * {@code del_flag='0' AND revoke_flag='0' AND valid_from <= now AND (valid_to IS NULL OR valid_to > now)}。
 * 本服务<b>不加任何授权缓存</b>，因此新增 / 撤销 / 到期都即时生效，无「缓存未失效」窗口。</p>
 *
 * <p><b>不得转授看不到的资料</b>：新增授权前先校验操作者本人对目标人才的授权级别不低于所授级别
 * （超管与集团级管理员由 {@link TalentScopeDomainService} 内部规则放行），
 * 防止「先拿到 summary 授权，再把它转授成 attachment」的越权放大。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentScopeGrantServiceImpl implements ITalentScopeGrantService {

    /**
     * 已撤销：否。
     */
    private static final String REVOKE_NO = "0";

    /**
     * 已撤销：是。
     */
    private static final String REVOKE_YES = "1";

    /**
     * 合法主体类型集合（与 {@link GrantSubject} 的常量一一对应，禁止裸字符串散落）。
     */
    private static final Set<String> GRANTEE_TYPES = Set.of(
        GrantSubject.TYPE_USER, GrantSubject.TYPE_ROLE, GrantSubject.TYPE_COMPANY_DEPT, GrantSubject.TYPE_DEPT);

    /**
     * 共享授权 Mapper。
     */
    private final TalentScopeGrantMapper talentScopeGrantMapper;

    /**
     * 人才主档 Mapper（只读：按可见范围解析授权列表涉及的 talent_id 集合）。
     */
    private final TalentProfileMapper talentProfileMapper;

    /**
     * 人才主档服务（可见性校验入口，不重复实现授权规则）。
     */
    private final ITalentProfileService talentProfileService;

    /**
     * 人才可见范围与资源级鉴权领域服务（唯一权威）。
     */
    private final TalentScopeDomainService talentScopeDomainService;

    /**
     * 系统用户服务（仅用于校验「用户」类型被授权主体存在）。
     */
    private final UserService userService;

    @Override
    public PageResult<TalentScopeGrantVo> queryPage(Long talentId, TalentScopeGrantQueryBo bo, PageQuery pageQuery) {
        // 访问前必须校验该人才对当前用户可见
        talentProfileService.requireVisible(talentId);
        TalentScopeGrantQueryBo query = bo == null ? new TalentScopeGrantQueryBo() : bo;
        LambdaQueryWrapper<TalentScopeGrant> wrapper = baseWrapper(query)
            .eq(TalentScopeGrant::getTalentId, talentId)
            .orderByDesc(TalentScopeGrant::getGrantedTime)
            .orderByDesc(TalentScopeGrant::getGrantId);
        Page<TalentScopeGrantVo> page = talentScopeGrantMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public PageResult<TalentScopeGrantVo> queryVisiblePage(TalentScopeGrantQueryBo bo, PageQuery pageQuery) {
        TalentScopeGrantQueryBo query = bo == null ? new TalentScopeGrantQueryBo() : bo;
        if (query.getTalentId() != null) {
            // 指定人才时逐条鉴权，避免越权枚举
            talentProfileService.requireVisible(query.getTalentId());
        }
        LambdaQueryWrapper<TalentScopeGrant> wrapper = baseWrapper(query);
        if (query.getTalentId() == null) {
            // 未指定人才：按当前用户可见范围过滤（唯一接法，见 TalentProfileMapper#selectVisibleTalentIds）
            List<Long> visibleTalentIds = talentProfileMapper.selectVisibleTalentIds(
                talentScopeDomainService.visibleTalentWrapper());
            if (visibleTalentIds == null || visibleTalentIds.isEmpty()) {
                return PageResult.build(List.of(), 0L);
            }
            wrapper.in(TalentScopeGrant::getTalentId, visibleTalentIds);
        } else {
            wrapper.eq(TalentScopeGrant::getTalentId, query.getTalentId());
        }
        wrapper.orderByDesc(TalentScopeGrant::getGrantedTime)
            .orderByDesc(TalentScopeGrant::getGrantId);
        Page<TalentScopeGrantVo> page = talentScopeGrantMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public TalentScopeGrantVo getDetail(Long grantId) {
        TalentScopeGrant grant = requireGrant(grantId);
        // 授权本身是「谁能看谁」的元数据：查看授权详情同样要求对该人才可见
        talentProfileService.requireVisible(grant.getTalentId());
        TalentScopeGrantVo vo = MapstructUtils.convert(grant, TalentScopeGrantVo.class);
        if (vo == null) {
            throw new ServiceException("授权记录不存在");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long talentId, TalentScopeGrantBo bo) {
        if (bo == null) {
            throw new ServiceException("授权入参不能为空");
        }
        // 1. 可见性闸门：人才必须对当前用户可见
        talentProfileService.requireVisible(talentId);
        // 2. 级别闸门：不允许把自己都看不到的资料级别转授他人（共享只扩大查看范围，不放大权限，§8.19）
        TalentPermissionLevelEnum level = requireLevel(bo.getPermissionLevel());
        talentScopeDomainService.checkPermissionLevel(talentId, level);
        // 3. 主体校验：类型必须为四类稳定编码之一，且「用户」类型要求用户真实存在
        String granteeType = requireGranteeType(bo.getGranteeType());
        validateGranteeExists(granteeType, bo.getGranteeId());
        validateValidRange(bo.getValidFrom(), bo.getValidTo());

        TalentScopeGrant entity = MapstructUtils.convert(bo, TalentScopeGrant.class);
        if (entity == null) {
            entity = new TalentScopeGrant();
        }
        // 服务端权威字段：主键、人才ID、级别编码、授权人、授权时间、撤销状态均不接受前端写入
        entity.setGrantId(null);
        entity.setTalentId(talentId);
        entity.setGranteeType(granteeType);
        entity.setPermissionLevel(level.getCode());
        entity.setGrantedBy(currentUserId());
        entity.setGrantedTime(LocalDateTime.now());
        entity.setRevokeFlag(REVOKE_NO);
        entity.setRevokedBy(null);
        entity.setRevokedTime(null);
        entity.setValidFrom(bo.getValidFrom() == null ? LocalDateTime.now() : bo.getValidFrom());
        talentScopeGrantMapper.insert(entity);
        // 授权变更不需要额外缓存失效：本服务不引入授权缓存，判定每次实时查库（§11.1）
        log.info("新增人才共享授权, grantId={}, talentId={}, granteeType={}, granteeId={}, level={}",
            entity.getGrantId(), talentId, granteeType, bo.getGranteeId(), level.getCode());
        return entity.getGrantId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long grantId, String reason) {
        TalentScopeGrant grant = requireGrant(grantId);
        talentProfileService.requireVisible(grant.getTalentId());
        if (REVOKE_YES.equals(grant.getRevokeFlag())) {
            throw new ServiceException("该授权已撤销，无需重复撤销");
        }
        LambdaUpdateWrapper<TalentScopeGrant> wrapper = new LambdaUpdateWrapper<TalentScopeGrant>()
            .eq(TalentScopeGrant::getGrantId, grant.getGrantId())
            .eq(TalentScopeGrant::getRevokeFlag, REVOKE_NO)
            .set(TalentScopeGrant::getRevokeFlag, REVOKE_YES)
            .set(TalentScopeGrant::getRevokedBy, currentUserId())
            .set(TalentScopeGrant::getRevokedTime, LocalDateTime.now())
            .set(StringUtils.isNotBlank(reason), TalentScopeGrant::getRemark, reason);
        int rows = talentScopeGrantMapper.update(null, wrapper);
        if (rows == 0) {
            throw new ServiceException("授权撤销失败，请刷新后重试");
        }
        // 撤销后立即失效：判定侧（TalentScopeGrantProviderImpl）实时查库并校验 revoke_flag，无缓存窗口
        log.info("撤销人才共享授权, grantId={}, talentId={}", grant.getGrantId(), grant.getTalentId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long[] grantIds) {
        if (grantIds == null || grantIds.length == 0) {
            return;
        }
        List<Long> ids = Arrays.stream(grantIds).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            TalentScopeGrant grant = requireGrant(id);
            talentProfileService.requireVisible(grant.getTalentId());
        }
        talentScopeGrantMapper.deleteByIds(ids);
        log.info("逻辑删除人才共享授权, grantIds={}", ids);
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 构造授权检索公共条件（含「过期立即失效」的有效性过滤）。
     *
     * @param query 检索条件
     * @return 条件构造器
     */
    private LambdaQueryWrapper<TalentScopeGrant> baseWrapper(TalentScopeGrantQueryBo query) {
        LambdaQueryWrapper<TalentScopeGrant> wrapper = new LambdaQueryWrapper<TalentScopeGrant>()
            .eq(StringUtils.isNotBlank(query.getGranteeType()), TalentScopeGrant::getGranteeType, query.getGranteeType())
            .eq(query.getGranteeId() != null, TalentScopeGrant::getGranteeId, query.getGranteeId())
            .eq(StringUtils.isNotBlank(query.getPermissionLevel()), TalentScopeGrant::getPermissionLevel, query.getPermissionLevel())
            .eq(StringUtils.isNotBlank(query.getRevokeFlag()), TalentScopeGrant::getRevokeFlag, query.getRevokeFlag());
        // 默认只返回当前有效授权：未撤销 + 已生效 +（未设置到期 或 未过期）
        if (!Boolean.FALSE.equals(query.getOnlyActive())) {
            LocalDateTime now = LocalDateTime.now();
            wrapper.eq(TalentScopeGrant::getRevokeFlag, REVOKE_NO)
                .le(TalentScopeGrant::getValidFrom, now)
                .and(inner -> inner.isNull(TalentScopeGrant::getValidTo).or().gt(TalentScopeGrant::getValidTo, now));
        }
        return wrapper;
    }

    /**
     * 加载授权记录，不存在时抛中文提示。
     *
     * @param grantId 授权ID
     * @return 授权实体
     */
    private TalentScopeGrant requireGrant(Long grantId) {
        if (grantId == null) {
            throw new ServiceException("授权ID不能为空");
        }
        TalentScopeGrant grant = talentScopeGrantMapper.selectById(grantId);
        if (grant == null) {
            throw new ServiceException("授权记录不存在或已删除");
        }
        return grant;
    }

    /**
     * 解析并校验授权级别编码（未知编码一律拒绝）。
     *
     * @param permissionLevel 级别编码
     * @return 级别枚举
     */
    private TalentPermissionLevelEnum requireLevel(String permissionLevel) {
        if (StringUtils.isBlank(permissionLevel)) {
            throw new ServiceException("授权级别不能为空");
        }
        TalentPermissionLevelEnum level = TalentPermissionLevelEnum.find(permissionLevel);
        if (level == null) {
            throw new ServiceException("授权级别不合法，请使用字典 talent_permission_level 的编码"
                + "（summary/detail/attachment）");
        }
        return level;
    }

    /**
     * 校验被授权主体类型（只接受四类稳定编码）。
     *
     * @param granteeType 主体类型
     * @return 规范化后的主体类型
     */
    private String requireGranteeType(String granteeType) {
        String type = StringUtils.trim(granteeType);
        if (!GRANTEE_TYPES.contains(type)) {
            throw new ServiceException("被授权主体类型不合法，只支持 user（用户）/role（角色）"
                + "/company_dept（公司部门）/dept（部门）");
        }
        return type;
    }

    /**
     * 校验被授权主体存在性。
     *
     * <p>P4 只能对「用户」类型做存在性校验（{@code UserService} 为模块可用的唯一组织/账号查询接口）；
     * 角色、公司部门与部门的存在性由前端选择器与后续落地的组织数据源保证，
     * 本方法对非用户类型<b>不做</b>臆测式拒绝，避免误伤尚未接入的数据源。</p>
     *
     * @param granteeType 主体类型
     * @param granteeId   主体ID
     */
    private void validateGranteeExists(String granteeType, Long granteeId) {
        if (granteeId == null) {
            throw new ServiceException("被授权主体ID不能为空");
        }
        if (!GrantSubject.TYPE_USER.equals(granteeType)) {
            return;
        }
        if (userService == null || userService.selectById(granteeId) == null) {
            throw new ServiceException("被授权用户不存在");
        }
    }

    /**
     * 校验授权有效期区间：起止均允许为空，但不允许「止早于起」。
     *
     * @param validFrom 有效期起
     * @param validTo   有效期止
     */
    private void validateValidRange(LocalDateTime validFrom, LocalDateTime validTo) {
        if (validFrom != null && validTo != null && !validTo.isAfter(validFrom)) {
            throw new ServiceException("授权有效期止必须晚于有效期起");
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
