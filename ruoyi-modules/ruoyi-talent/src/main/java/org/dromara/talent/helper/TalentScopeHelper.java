package org.dromara.talent.helper;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.domain.RoleDTO;
import org.dromara.system.api.model.LoginUser;
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.TlTalent;
import org.dromara.talent.domain.TlTalentAccessGrant;
import org.dromara.talent.domain.TlTalentAttachment;
import org.dromara.talent.enums.GrantPermissionEnum;
import org.dromara.talent.enums.ScanStatusEnum;
import org.dromara.talent.enums.TalentRegionEnum;
import org.dromara.talent.enums.TalentRoleEnum;
import org.dromara.talent.enums.TalentShareScopeEnum;
import org.dromara.talent.mapper.TlTalentAccessGrantMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 人才库数据范围与单条授权的唯一权威实现。
 * <p>
 * 硬约束：
 * <ul>
 *     <li>人才库 Mapper <b>不使用</b> {@code @DataPermission}（框架 DataScopeType 无法表达"区域"语义），
 *     所有数据范围判断只能走本类，禁止在 Controller / Service 里自行拼条件。</li>
 *     <li>区域判断必须基于<b>角色标识</b>（{@code roleKey}）而不是角色名或部门，roleKey 可能是逗号分隔的多值。</li>
 *     <li>超管一律放行，不查库。</li>
 * </ul>
 *
 * @author talent
 */
@Component
@RequiredArgsConstructor
public class TalentScopeHelper {

    /**
     * 无权访问人才档案的统一提示。
     */
    public static final String DENY_TALENT = "无权访问该人才档案";

    /**
     * 无权下载附件的统一提示。
     */
    public static final String DENY_DOWNLOAD = "无权下载该附件";

    /**
     * 人才档案不存在。
     */
    public static final String TALENT_NOT_FOUND = "人才档案不存在";

    /**
     * 区域授权范围访问授权 Mapper。
     */
    private final TlTalentAccessGrantMapper accessGrantMapper;

    /**
     * 当前用户可查的区域列表。
     * <p>
     * 超管 / 集团管理员 / 集团 HR → [GROUP,SZ,ST]；SZ HR → [SZ,GROUP]；ST HR → [ST,GROUP]；
     * 仅查阅者、审计员等无区域角色 → 空列表（只能走单条授权）。
     *
     * @return 可见区域编码列表（ORDER 固定，便于拼 SQL 与断言）
     */
    public List<String> currentVisibleRegions() {
        if (LoginHelper.isSuperAdmin()) {
            return allRegions();
        }
        Set<String> roleKeys = currentRoleKeys();
        if (roleKeys.contains(TalentRoleEnum.ADMIN.getCode()) || roleKeys.contains(TalentRoleEnum.HR_GROUP.getCode())) {
            return allRegions();
        }
        if (roleKeys.contains(TalentRoleEnum.HR_SZ.getCode())) {
            return List.of(TalentRegionEnum.SZ.getCode(), TalentRegionEnum.GROUP.getCode());
        }
        if (roleKeys.contains(TalentRoleEnum.HR_ST.getCode())) {
            return List.of(TalentRegionEnum.ST.getCode(), TalentRegionEnum.GROUP.getCode());
        }
        return List.of();
    }

    /**
     * 是否没有任何区域权限。
     * <p>
     * 调用方应立即返回空页，<b>不要</b>查库。
     *
     * @return true 表示无区域权限
     */
    public boolean hasNoRegionScope() {
        return currentVisibleRegions().isEmpty();
    }

    /**
     * 当前用户是否可写指定区域（新增 / 编辑 / 上传 / 删除）。
     * <p>
     * 允许条件：超管、集团管理员、集团 HR；或 SZ HR 写 SZ；或 ST HR 写 ST。
     * GROUP 区域仅超管 / 集团管理员 / 集团 HR 可写。
     *
     * @param regionCode 区域编码
     * @return 是否可写
     */
    public boolean canWriteRegion(String regionCode) {
        if (StringUtils.isBlank(regionCode)) {
            return false;
        }
        String region = regionCode.trim().toUpperCase();
        if (TalentRegionEnum.find(region) == null) {
            throw new ServiceException("非法的区域编码");
        }
        if (LoginHelper.isSuperAdmin()) {
            return true;
        }
        Set<String> roleKeys = currentRoleKeys();
        if (roleKeys.contains(TalentRoleEnum.ADMIN.getCode()) || roleKeys.contains(TalentRoleEnum.HR_GROUP.getCode())) {
            return true;
        }
        if (TalentRegionEnum.GROUP.getCode().equals(region)) {
            return false;
        }
        if (roleKeys.contains(TalentRoleEnum.HR_SZ.getCode())) {
            return TalentRegionEnum.SZ.getCode().equals(region);
        }
        if (roleKeys.contains(TalentRoleEnum.HR_ST.getCode())) {
            return TalentRegionEnum.ST.getCode().equals(region);
        }
        return false;
    }

    /**
     * 单条人才是否对当前用户可见；不可见时抛 {@link ServiceException}。
     * <p>
     * 判定顺序：
     * <ol>
     *     <li>超管、集团管理员、集团 HR → 放行；</li>
     *     <li>命中 {@code tl_talent_access_grant}（USER 或 ROLE，未过期，status='0'）的 VIEW 授权 → 放行；</li>
     *     <li>{@code share_scope = GRANT_ONLY} → 拒绝（除上述管理员外只认单条授权）；</li>
     *     <li>{@code share_scope = GROUP} → 只要当前用户有任意区域权限即放行；</li>
     *     <li>否则区域编码必须落在 {@link #currentVisibleRegions()} 内。</li>
     * </ol>
     *
     * @param talent 人才实体（可为 null）
     */
    public void checkTalentVisible(TlTalent talent) {
        if (talent == null || isDeleted(talent.getDelFlag())) {
            throw new ServiceException(TALENT_NOT_FOUND);
        }
        if (LoginHelper.isSuperAdmin()) {
            return;
        }
        Set<String> roleKeys = currentRoleKeys();
        if (roleKeys.contains(TalentRoleEnum.ADMIN.getCode()) || roleKeys.contains(TalentRoleEnum.HR_GROUP.getCode())) {
            return;
        }
        if (hasGrant(talent.getTalentId(), GrantPermissionEnum.VIEW)) {
            return;
        }
        if (TalentShareScopeEnum.GRANT_ONLY.getCode().equals(talent.getShareScope())) {
            throw new ServiceException(DENY_TALENT);
        }
        List<String> visibleRegions = currentVisibleRegions();
        if (CollUtil.isEmpty(visibleRegions)) {
            throw new ServiceException(DENY_TALENT);
        }
        if (TalentShareScopeEnum.GROUP.getCode().equals(talent.getShareScope())) {
            return;
        }
        if (visibleRegions.contains(talent.getRegionCode())) {
            return;
        }
        throw new ServiceException(DENY_TALENT);
    }

    /**
     * 单条人才是否具备附件下载权限；不具备时抛 {@link ServiceException}。
     * <p>
     * 具备条件：区域可见（或单条授权 VIEW）<b>且</b>（具备 DOWNLOAD 单条授权 或 属于 HR / 管理员角色）。
     * 同时在此处做纵深防御：仅 {@code scan_status = CLEAN} 的附件可通过（与 §6.3 的显式校验互为兜底）。
     *
     * @param talent     人才实体
     * @param attachment 附件实体
     */
    public void checkAttachmentDownloadable(TlTalent talent, TlTalentAttachment attachment) {
        checkTalentVisible(talent);
        if (attachment == null || isDeleted(attachment.getDelFlag())) {
            throw new ServiceException("附件不存在");
        }
        if (!isDownloadableRole() && !hasGrant(talent.getTalentId(), GrantPermissionEnum.DOWNLOAD)) {
            throw new ServiceException(DENY_DOWNLOAD);
        }
        if (!ScanStatusEnum.CLEAN.getCode().equals(attachment.getScanStatus())) {
            throw new ServiceException("附件未通过安全扫描，禁止下载");
        }
    }

    /**
     * 当前用户能否查看完整手机号。
     * <p>
     * 超管，或拥有 {@code talent:profile:phone} 菜单权限，或存在该人才的 VIEW_FULL_PHONE 单条授权。
     *
     * @param talentId 人才ID（可为 null，此时只判断角色与菜单权限）
     * @return 是否可查看完整手机号
     */
    public boolean canViewFullPhone(Long talentId) {
        if (LoginHelper.isSuperAdmin()) {
            return true;
        }
        if (hasMenuPermission(TalentConstants.PERM_PROFILE_PHONE)) {
            return true;
        }
        return talentId != null && hasGrant(talentId, GrantPermissionEnum.VIEW_FULL_PHONE);
    }

    /**
     * 当前用户是否属于可全量下载的 HR / 管理员角色（不含超管，超管由调用方单独判断）。
     *
     * @return 是否 HR / 管理员
     */
    public boolean isDownloadableRole() {
        if (LoginHelper.isSuperAdmin()) {
            return true;
        }
        Set<String> roleKeys = currentRoleKeys();
        return roleKeys.contains(TalentRoleEnum.ADMIN.getCode())
            || roleKeys.contains(TalentRoleEnum.HR_GROUP.getCode())
            || roleKeys.contains(TalentRoleEnum.HR_SZ.getCode())
            || roleKeys.contains(TalentRoleEnum.HR_ST.getCode());
    }

    /**
     * 解析当前用户角色标识集合。
     * <p>
     * 来源 {@code LoginHelper.getLoginUser().getRoles()}（{@code List<RoleDTO>}），
     * {@code roleKey} 可能是逗号分隔的多值，使用 {@code StringUtils.splitList} 处理。
     *
     * @return 角色标识集合（不为 null）
     */
    public Set<String> currentRoleKeys() {
        Set<String> roleKeys = new LinkedHashSet<>();
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null) {
            return roleKeys;
        }
        if (CollUtil.isNotEmpty(loginUser.getRolePermission())) {
            roleKeys.addAll(loginUser.getRolePermission());
        }
        List<RoleDTO> roles = loginUser.getRoles();
        if (CollUtil.isNotEmpty(roles)) {
            for (RoleDTO role : roles) {
                if (role == null || StringUtils.isBlank(role.getRoleKey())) {
                    continue;
                }
                roleKeys.addAll(StringUtils.splitList(role.getRoleKey()));
            }
        }
        return roleKeys;
    }

    /**
     * 当前用户是否拥有指定菜单权限。
     *
     * @param permission 权限标识
     * @return 是否拥有
     */
    public boolean hasMenuPermission(String permission) {
        if (StringUtils.isBlank(permission)) {
            return false;
        }
        LoginUser loginUser = LoginHelper.getLoginUser();
        return loginUser != null && CollUtil.contains(loginUser.getMenuPermission(), permission);
    }

    /**
     * 是否命中该人才的有效单条授权。
     *
     * @param talentId   人才ID
     * @param permission 需要的授权动作
     * @return 是否命中
     */
    public boolean hasGrant(Long talentId, GrantPermissionEnum permission) {
        if (talentId == null || permission == null) {
            return false;
        }
        List<TlTalentAccessGrant> grants = accessGrantMapper.selectActiveGrants(talentId, LoginHelper.getUserId(), currentRoleKeys());
        if (CollUtil.isEmpty(grants)) {
            return false;
        }
        for (TlTalentAccessGrant grant : grants) {
            if (grant == null || StringUtils.isBlank(grant.getPermission())) {
                continue;
            }
            for (String item : StringUtils.splitList(grant.getPermission())) {
                if (permission.getCode().equalsIgnoreCase(item.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 全区域列表（顺序固定）。
     *
     * @return 区域编码列表
     */
    private List<String> allRegions() {
        return List.of(TalentRegionEnum.GROUP.getCode(), TalentRegionEnum.SZ.getCode(), TalentRegionEnum.ST.getCode());
    }

    /**
     * 逻辑删除判断。
     *
     * @param delFlag 删除标志
     * @return 是否已删除
     */
    private boolean isDeleted(String delFlag) {
        return "1".equals(delFlag);
    }

}
