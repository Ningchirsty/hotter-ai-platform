package org.dromara.hrtalent.domainservice;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.entity.TalentProfile;
import org.dromara.hrtalent.enums.TalentPermissionLevelEnum;
import org.dromara.hrtalent.enums.TalentStatusEnum;
import org.dromara.hrtalent.enums.TalentVisibilityTypeEnum;
import org.dromara.hrtalent.support.GrantSubject;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.TalentScopeGrantProvider;
import org.dromara.system.api.domain.RoleDTO;
import org.dromara.system.api.model.LoginUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 人才数据可见范围与资源级鉴权的唯一权威领域服务（设计文档 §21.5 / §21.14）。
 *
 * <p><b>职责边界</b>：统一生成人才列表的可见范围条件（{@link ScopeCondition}）、
 * 判定单条人才是否可见（{@link #checkTalentVisible}）、校验人才共享授权级别
 * （{@link #checkPermissionLevel}）。</p>
 *
 * <p><b>硬约束</b>：</p>
 * <ul>
 *     <li><b>不依赖前端传入字段</b>：可见范围完全由登录态（{@code LoginHelper}）与
 *     {@code hr_talent_scope_grant} 推导，禁止接受 Controller 传入的部门、公司或授权标记。</li>
 *     <li>人才 Mapper <b>不使用</b>框架 {@code @DataPermission}：框架 DataScopeType 无法表达
 *     「可见范围 + 显式授权」语义，人才查询只能走本服务产出的条件。</li>
 *     <li>「能不能看到人才」（本服务）与「能不能看电话明文 / 下附件 / 看背调 / 导出」
 *     （按钮权限 + {@link #checkPermissionLevel}）分开，前者通过不代表后者通过（设计文档 §9.4）。</li>
 *     <li>角色判断基于 {@code roleKey}（可能为逗号分隔多值），不基于角色名称或部门。</li>
 * </ul>
 *
 * <p><b>P1 简化与 P3 接入说明</b>：P1 只能取到登录用户的 {@code deptId}，
 * 尚无部门树与公司级授权数据源，因此
 * {@link ScopeCondition#allowedCompanyDeptIds()} 与 {@link ScopeCondition#allowedDeptIds()}
 * 均只含登录用户所属部门；集团级角色以 {@link ScopeCondition#unlimitedCompany()} 表达
 * 「集团全域」语义。P3 接入部门树与 {@code hr_talent_scope_grant} 后，
 * 由 {@link TalentScopeGrantProvider} 实现收敛为公司/部门授权集合，
 * 并直接消费 {@link ScopeCondition} 生成 §21.14 的 SQL 条件，本服务的判定分支无需改动。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TalentScopeDomainService {

    /**
     * 无权查看该人才时的统一提示。
     */
    public static final String DENY_TALENT = HrTalentErrorCode.MSG_HR_TALENT_002;

    /**
     * 人才不存在时的统一提示。
     */
    public static final String TALENT_NOT_FOUND = "人才不存在";

    /**
     * 无对应授权级别时的统一提示。
     */
    public static final String DENY_PERMISSION_LEVEL = "无该人才的授权级别，禁止访问";

    /**
     * 登录态失效时的统一提示。
     */
    public static final String NOT_LOGIN = "登录状态已失效，请重新登录";

    /**
     * 人才共享授权查询扩展点；P1 无实现时按「无任何显式授权」处理（fail-safe 拒绝）。
     */
    private final ObjectProvider<TalentScopeGrantProvider> grantProvider;

    /* ------------------------------------------------------------------ 可见范围 ------------------------------------------------------------------ */

    /**
     * 生成当前登录用户的人才可见范围查询条件（设计文档 §21.14）。
     *
     * <p><b>接法契约</b>：调用方必须把本方法返回的 wrapper <b>原样</b>交给
     * 只读 Mapper 方法 {@code TalentProfileMapper#selectVisibleTalentIds}
     * （SQL 形如 {@code SELECT p.talent_id FROM hr_talent_profile p ${ew.customSqlSegment}}），
     * 由 {@code ${ew.customSqlSegment}} 直接消费条件与参数；</p>
     *
     * <p><b>禁止</b>：把本 wrapper 的 {@code getCustomSqlSegment()} 搬到另一个 wrapper 上
     * （前导 {@code WHERE} 会重复且参数绑定错位），也禁止在 Controller / Service / Mapper 内
     * 另写一套可见范围规则（§11.1）。</p>
     *
     * @return 人才可见范围查询条件（条件中的表别名固定为 {@code p}）
     */
    public QueryWrapper<TalentProfile> visibleTalentWrapper() {
        return visibleTalentWrapper(currentScope());
    }

    /**
     * 按指定范围条件生成人才可见范围查询条件（设计文档 §21.14 的 SQL 分支）。
     *
     * <p><b>组合契约（重要）</b>：可见性分支必须合成<b>同一条</b> {@code apply} 片段，
     * 且所有取值<b>全部内联为字面量</b>（可见性编码取枚举常量，部门ID与用户ID来自登录态
     * {@code Long}，不存在注入面）。原因有两条：</p>
     * <ul>
     *     <li>用多次 {@code apply(...)} 拼接 OR 分支时，MyBatis-Plus 会在相邻片段之间补 {@code AND}，
     *     生成 {@code AND OR (...)} 与悬空 {@code AND )}，SQL 直接语法错误；</li>
     *     <li>带参数的 {@code apply(sql, values...)} <b>必须</b>使用 {@code {0}}/{@code {1}} 占位符
     *     （直接写 {@code ?} 会抛 {@code MybatisPlusException: sql not contains "{0}"}），
     *     而 {@code {0}} 会被渲染成属于<b>本 wrapper</b> 的
     *     {@code #{ew.paramNameValuePairs.MPGENVALn}}，片段一旦被搬进别的 wrapper 就会参数错位。</li>
     * </ul>
     * <p>内联字面量后，条件片段与本 wrapper 的参数表彻底解耦，可安全交给只读 Mapper 的
     * {@code ${ew.customSqlSegment}} 消费。</p>
     *
     * <p>分支顺序：集团共享 → 归属公司 → 归属部门 → 仅人才负责人 → 显式授权；
     * 空范围返回恒假条件，调用方按空结果处理。前置谓词固定为 {@code p.del_flag = '0'}
     * 与 {@code p.talent_status <> 'merged'}。</p>
     *
     * @param scope 可见范围条件，可为 null
     * @return 人才可见范围查询条件（条件中不含任何占位符参数）
     */
    public QueryWrapper<TalentProfile> visibleTalentWrapper(ScopeCondition scope) {
        // 表别名固定为 p，与只读 Mapper 的自定义 SQL 保持一致
        StringBuilder sql = new StringBuilder(256);
        sql.append("(p.del_flag = '").append(HrTalentConstants.DEL_FLAG_NORMAL).append('\'')
            .append(" AND p.talent_status <> '").append(TalentStatusEnum.MERGED.getCode()).append('\'');
        if (scope == null || scope.empty()) {
            // 无任何可见主体：恒假条件，直接返回空集，避免退化为全量查询
            sql.append(" AND 1 = 0)");
            return new QueryWrapper<TalentProfile>().apply(sql.toString());
        }
        if (!scope.unlimited() && !scope.unlimitedCompany()) {
            // 可见性 OR 分支：整体加一层括号、分支之间显式 OR，合并成同一条 apply 片段
            List<String> branches = new ArrayList<>();
            branches.add("p.visibility_type = '" + TalentVisibilityTypeEnum.GROUP.getCode() + "'");
            if (CollUtil.isNotEmpty(scope.allowedCompanyDeptIds())) {
                branches.add("(p.visibility_type = '" + TalentVisibilityTypeEnum.COMPANY.getCode()
                    + "' AND p.owner_dept_id IN (" + StringUtils.join(scope.allowedCompanyDeptIds(), ",") + "))");
            }
            if (CollUtil.isNotEmpty(scope.allowedDeptIds())) {
                branches.add("(p.visibility_type = '" + TalentVisibilityTypeEnum.DEPARTMENT.getCode()
                    + "' AND p.owner_dept_id IN (" + StringUtils.join(scope.allowedDeptIds(), ",") + "))");
            }
            if (scope.currentUserId() != null) {
                branches.add("p.owner_id = " + scope.currentUserId());
            }
            String grantPair = grantPairs(scope);
            if (!grantPair.isEmpty()) {
                branches.add("EXISTS (SELECT 1 FROM hr_talent_scope_grant g"
                    + " WHERE g.talent_id = p.talent_id AND g.del_flag = '0'"
                    + " AND g.valid_from <= NOW() AND (g.valid_to IS NULL OR g.valid_to > NOW())"
                    + " AND (" + grantPair + "))");
            }
            sql.append(" AND (").append(String.join(" OR ", branches)).append(')');
        }
        sql.append(')');
        return new QueryWrapper<TalentProfile>().apply(sql.toString());
    }

    /**
     * 构造显式授权主体条件：按「类型 + ID」成对展开，避免不同类型主体 ID 混入同一集合。
     *
     * <p>每对条件<b>各自加括号</b>后再用 {@code OR} 连接。虽然 SQL 中 {@code AND} 优先级高于 {@code OR}、
     * 不加括号语义也正确，但显式分组可避免后续有人在该片段前后追加条件时误判优先级。</p>
     *
     * @param scope 可见范围条件
     * @return 条件片段；无有效主体时返回空串
     */
    private String grantPairs(ScopeCondition scope) {
        if (CollUtil.isEmpty(scope.grantSubjects())) {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        for (GrantSubject subject : scope.grantSubjects()) {
            if (subject != null && subject.valid()) {
                pairs.add("(g.grantee_type = '" + subject.granteeType()
                    + "' AND g.grantee_id = " + subject.granteeId() + ")");
            }
        }
        return String.join(" OR ", pairs);
    }

    /**
     * 生成当前登录用户的人才可见范围条件（设计文档 §21.14）。
     * <p>返回的主体集合包含：公司部门 ID 集合、部门 ID 集合、当前用户 ID、授权主体集合
     * （用户 / 角色 / 公司部门 / 部门的成对主体）。</p>
     *
     * @return 可见范围条件
     * @throws ServiceException 登录态失效
     */
    public ScopeCondition currentScope() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new ServiceException(NOT_LOGIN);
        }
        Long currentUserId = loginUser.getUserId();
        Long currentDeptId = loginUser.getDeptId();
        Set<String> roleKeys = resolveRoleKeys(loginUser);
        boolean superAdmin = LoginHelper.isSuperAdmin(currentUserId);
        boolean groupAdmin = isGroupLevelAdmin(roleKeys);

        // 公司部门 / 部门集合：P1 只含登录用户所属部门，P3 由部门树与授权数据源补全
        Set<Long> allowedCompanyDeptIds = new LinkedHashSet<>();
        Set<Long> allowedDeptIds = new LinkedHashSet<>();
        if (currentDeptId != null) {
            allowedCompanyDeptIds.add(currentDeptId);
            allowedDeptIds.add(currentDeptId);
        }

        // 授权主体：用户 / 角色 / 公司部门 / 部门（类型与 ID 成对，禁止混用 ID 集合）
        Set<GrantSubject> grantSubjects = new LinkedHashSet<>();
        addSubject(grantSubjects, GrantSubject.TYPE_USER, currentUserId);
        if (CollUtil.isNotEmpty(loginUser.getRoles())) {
            for (RoleDTO role : loginUser.getRoles()) {
                if (role == null) {
                    continue;
                }
                addSubject(grantSubjects, GrantSubject.TYPE_ROLE, role.getRoleId());
            }
        }
        addSubject(grantSubjects, GrantSubject.TYPE_COMPANY_DEPT, currentDeptId);
        addSubject(grantSubjects, GrantSubject.TYPE_DEPT, currentDeptId);

        return new ScopeCondition(
            superAdmin,
            superAdmin || groupAdmin,
            currentUserId,
            currentDeptId,
            allowedCompanyDeptIds,
            allowedDeptIds,
            grantSubjects,
            roleKeys);
    }

    /**
     * 判定单条人才对当前用户是否可见（不抛异常）。
     *
     * @param target 人才可见性快照，可为 null
     * @return 是否可见
     */
    public boolean visible(TalentScopeTarget target) {
        return visible(target, currentScope());
    }

    /**
     * 判定单条人才对指定范围条件是否可见（不抛异常）。
     * <p>判定顺序与设计文档 §21.14 的 SQL 分支一一对应：
     * 超管/集团全域 → 集团共享 → 归属公司 → 归属部门 → 仅人才负责人 → 显式授权。
     * 未知或空的 {@code visibility_type} 一律拒绝（fail-safe）。</p>
     *
     * @param target 人才可见性快照，可为 null
     * @param scope  可见范围条件，可为 null
     * @return 是否可见
     */
    public boolean visible(TalentScopeTarget target, ScopeCondition scope) {
        if (target == null || target.deleted() || target.merged() || scope == null) {
            return false;
        }
        if (scope.unlimited()) {
            return true;
        }
        TalentVisibilityTypeEnum visibility = TalentVisibilityTypeEnum.find(target.visibilityType());
        if (visibility == null) {
            return false;
        }
        return switch (visibility) {
            // 集团共享：任何具备人才访问入口的登录用户可见
            case GROUP -> true;
            // 归属公司：P1 中集团级角色按集团全域处理
            case COMPANY -> scope.unlimitedCompany()
                || (target.ownerDeptId() != null && scope.allowedCompanyDeptIds().contains(target.ownerDeptId()));
            // 归属部门
            case DEPARTMENT -> target.ownerDeptId() != null && scope.allowedDeptIds().contains(target.ownerDeptId());
            // 仅人才负责人
            case OWNER -> target.ownerId() != null && target.ownerId().equals(scope.currentUserId());
            // 显式授权：命中有效授权主体
            case EXPLICIT -> hasGrant(target.talentId(), scope.grantSubjects(), TalentPermissionLevelEnum.SUMMARY);
        };
    }

    /**
     * 资源级鉴权入口：校验单条人才对当前用户可见，不可见时抛异常。
     * <p>人才不存在抛「人才不存在」；已合并抛 {@code HR_TALENT_003}，
     * 其余情况抛 {@code HR_TALENT_002}。</p>
     *
     * @param target 人才可见性快照
     * @throws ServiceException 不可见
     */
    public void checkTalentVisible(TalentScopeTarget target) {
        checkTalentVisible(target, currentScope());
    }

    /**
     * 资源级鉴权入口：校验单条人才在指定范围条件下可见。
     *
     * @param target 人才可见性快照
     * @param scope  可见范围条件
     * @throws ServiceException 不可见
     */
    public void checkTalentVisible(TalentScopeTarget target, ScopeCondition scope) {
        if (target == null || target.deleted()) {
            throw new ServiceException(TALENT_NOT_FOUND);
        }
        if (target.merged()) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_TALENT_003);
        }
        if (visible(target, scope)) {
            return;
        }
        // 仅记录人才ID与用户ID，不记录任何联系方式或简历内容
        log.warn("人才可见性校验未通过, talentId={}, userId={}", target.talentId(), scope == null ? null : scope.currentUserId());
        throw new ServiceException(DENY_TALENT);
    }

    /* ------------------------------------------------------------------ 授权级别 ------------------------------------------------------------------ */

    /**
     * 校验当前用户对该人才是否具备指定共享授权级别，不具备时抛异常。
     * <p>调用方必须<b>先</b>执行 {@link #checkTalentVisible}：可见范围与授权级别是两道独立闸门。</p>
     *
     * @param talentId      人才ID
     * @param requiredLevel 要求的最低授权级别（摘要 &lt; 明细 &lt; 附件）
     * @throws ServiceException 授权级别不足
     */
    public void checkPermissionLevel(Long talentId, TalentPermissionLevelEnum requiredLevel) {
        if (!hasPermissionLevel(talentId, requiredLevel)) {
            log.warn("人才授权级别校验未通过, talentId={}, requiredLevel={}, userId={}",
                talentId, requiredLevel == null ? null : requiredLevel.getCode(), LoginHelper.getUserId());
            throw new ServiceException(DENY_PERMISSION_LEVEL);
        }
    }

    /**
     * 判定当前用户对该人才是否具备指定共享授权级别。
     * <p>超管全量放行；集团级管理员满足「摘要 / 明细」，但
     * <b>高度敏感附件（附件级别）仍需独立显式授权</b>（设计文档 §6）。</p>
     *
     * @param talentId      人才ID
     * @param requiredLevel 要求的最低授权级别
     * @return 是否具备
     */
    public boolean hasPermissionLevel(Long talentId, TalentPermissionLevelEnum requiredLevel) {
        if (requiredLevel == null) {
            return false;
        }
        if (LoginHelper.isSuperAdmin()) {
            return true;
        }
        if (isGroupLevelAdmin(currentRoleKeys())
            && requiredLevel.ordinal() < TalentPermissionLevelEnum.ATTACHMENT.ordinal()) {
            return true;
        }
        return hasGrant(talentId, currentGrantSubjects(), requiredLevel);
    }

    /* ------------------------------------------------------------------ 登录态辅助 ------------------------------------------------------------------ */

    /**
     * 解析当前登录用户的角色标识集合。
     * <p>来源 {@code LoginHelper.getLoginUser().getRoles()}（{@code List<RoleDTO>}）；
     * 6.0.0 无 {@code getRoleList()}，且 {@code roleKey} 可能为逗号分隔多值，
     * 使用 {@code StringUtils.splitList} 展开，同时合并 {@code rolePermission}。</p>
     *
     * @return 角色标识集合（不为 null）
     */
    public Set<String> currentRoleKeys() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null) {
            return Set.of();
        }
        return resolveRoleKeys(loginUser);
    }

    /**
     * 当前用户是否拥有指定菜单权限标识。
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
     * 当前用户是否为集团级管理员（集团招聘管理员 / 集团人才管理员）。
     *
     * @return 是否集团级管理员
     */
    public boolean isGroupLevelAdmin() {
        return isGroupLevelAdmin(currentRoleKeys());
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 解析角色标识集合（合并 {@code rolePermission} 与 {@code roles.roleKey} 的多值拆分结果）。
     *
     * @param loginUser 登录用户
     * @return 角色标识集合（不为 null）
     */
    private Set<String> resolveRoleKeys(LoginUser loginUser) {
        Set<String> roleKeys = new LinkedHashSet<>();
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
        roleKeys.removeIf(StringUtils::isBlank);
        return Collections.unmodifiableSet(roleKeys);
    }

    /**
     * 判断角色集合中是否包含集团级管理员角色。
     *
     * @param roleKeys 角色标识集合
     * @return 是否集团级管理员
     */
    private boolean isGroupLevelAdmin(Set<String> roleKeys) {
        if (CollUtil.isEmpty(roleKeys)) {
            return false;
        }
        for (String roleKey : roleKeys) {
            if (HrTalentConstants.GROUP_LEVEL_ROLE_KEYS.contains(roleKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构造当前用户的授权主体集合（不做任何缓存，避免授权变更后残留权限）。
     *
     * @return 授权主体集合
     */
    private Set<GrantSubject> currentGrantSubjects() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null || loginUser.getUserId() == null) {
            return Set.of();
        }
        Set<GrantSubject> subjects = new LinkedHashSet<>();
        addSubject(subjects, GrantSubject.TYPE_USER, loginUser.getUserId());
        if (CollUtil.isNotEmpty(loginUser.getRoles())) {
            for (RoleDTO role : loginUser.getRoles()) {
                if (role == null) {
                    continue;
                }
                addSubject(subjects, GrantSubject.TYPE_ROLE, role.getRoleId());
            }
        }
        addSubject(subjects, GrantSubject.TYPE_COMPANY_DEPT, loginUser.getDeptId());
        addSubject(subjects, GrantSubject.TYPE_DEPT, loginUser.getDeptId());
        return subjects;
    }

    /**
     * 命中有效授权且级别足够时返回 true。
     * <p>未提供 {@link TalentScopeGrantProvider} 实现时返回 false（fail-safe）。</p>
     *
     * @param talentId      人才ID
     * @param subjects      授权主体集合
     * @param requiredLevel 要求的最低授权级别
     * @return 是否命中
     */
    private boolean hasGrant(Long talentId, Set<GrantSubject> subjects, TalentPermissionLevelEnum requiredLevel) {
        if (talentId == null || requiredLevel == null || CollUtil.isEmpty(subjects)) {
            return false;
        }
        TalentScopeGrantProvider provider = grantProvider.getIfAvailable();
        if (provider == null) {
            return false;
        }
        try {
            return provider.hasActiveGrant(talentId, subjects, requiredLevel, LocalDateTime.now());
        } catch (Exception e) {
            // 授权查询异常时按拒绝处理，避免故障放大为越权
            log.error("人才授权查询异常, talentId={}, exception={}", talentId, e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 添加一个有效授权主体。
     *
     * @param subjects    主体集合
     * @param granteeType 主体类型
     * @param granteeId   主体ID
     */
    private void addSubject(Set<GrantSubject> subjects, String granteeType, Long granteeId) {
        if (granteeId == null) {
            return;
        }
        GrantSubject subject = new GrantSubject(granteeType, granteeId);
        if (subject.valid()) {
            subjects.add(subject);
        }
    }

    /* ------------------------------------------------------------------ 模型定义 ------------------------------------------------------------------ */

    /**
     * 人才可见性快照。
     * <p>P1 尚未落地人才实体，故以最小快照承载 §21.14 判定所需字段；
     * P3 落地 {@code hr_talent_profile} 实体后由实体转换而来，
     * <b>禁止</b>把前端传入的可见范围字段直接装配为本对象。</p>
     *
     * @param talentId       人才ID（{@code hr_talent_profile.talent_id}）
     * @param ownerId        人才负责人ID（{@code owner_id}）
     * @param ownerDeptId    归属部门ID（{@code owner_dept_id}）
     * @param visibilityType 可见范围编码（{@code visibility_type}，见 {@link TalentVisibilityTypeEnum}）
     * @param talentStatus   人才生命周期状态编码（{@code talent_status}）
     * @param delFlag        逻辑删除标志
     * @author hr-talent
     */
    public record TalentScopeTarget(Long talentId,
                                    Long ownerId,
                                    Long ownerDeptId,
                                    String visibilityType,
                                    String talentStatus,
                                    String delFlag) {

        /**
         * 是否已逻辑删除。
         *
         * @return 是否已删除
         */
        public boolean deleted() {
            return HrTalentConstants.DEL_FLAG_DELETED.equals(delFlag);
        }

        /**
         * 是否为已合并主档。
         *
         * @return 是否已合并
         */
        public boolean merged() {
            return TalentStatusEnum.MERGED.getCode().equals(talentStatus);
        }
    }

    /**
     * 人才可见范围条件（设计文档 §21.14 的主体集合部分）。
     *
     * @param superAdmin             是否超级管理员（全平台，等同于 unconditional）
     * @param unlimitedCompany       是否集团全域（集团级管理员，公司级分支不做部门限定）
     * @param currentUserId          当前用户ID（对应 {@code p.owner_id = :currentUserId}）
     * @param currentDeptId          当前用户部门ID（为空表示无组织归属）
     * @param allowedCompanyDeptIds  可见公司部门ID集合（对应 {@code :allowedCompanyDeptIds}）
     * @param allowedDeptIds         可见部门ID集合（对应 {@code :allowedDeptIds}）
     * @param grantSubjects          授权主体集合（类型与 ID 成对）
     * @param roleKeys               当前用户角色标识集合
     * @author hr-talent
     */
    public record ScopeCondition(boolean superAdmin,
                                 boolean unlimitedCompany,
                                 Long currentUserId,
                                 Long currentDeptId,
                                 Set<Long> allowedCompanyDeptIds,
                                 Set<Long> allowedDeptIds,
                                 Set<GrantSubject> grantSubjects,
                                 Set<String> roleKeys) {

        /**
         * 紧凑构造器：对集合字段做空值与不可变保护。
         */
        public ScopeCondition {
            allowedCompanyDeptIds = immutable(allowedCompanyDeptIds);
            allowedDeptIds = immutable(allowedDeptIds);
            grantSubjects = immutable(grantSubjects);
            roleKeys = immutable(roleKeys);
        }

        /**
         * 是否不受可见范围约束（超级管理员）。
         *
         * @return 是否无约束
         */
        public boolean unlimited() {
            return superAdmin;
        }

        /**
         * 是否没有任何组织与授权主体（调用方应直接返回空页，不查库）。
         *
         * @return 是否为空范围
         */
        public boolean empty() {
            return !superAdmin
                && !unlimitedCompany
                && CollUtil.isEmpty(allowedCompanyDeptIds)
                && CollUtil.isEmpty(allowedDeptIds)
                && CollUtil.isEmpty(grantSubjects)
                && currentUserId == null;
        }

        /**
         * 是否集团级管理员。
         *
         * @return 是否集团级管理员
         */
        public boolean groupLevelAdmin() {
            if (CollUtil.isEmpty(roleKeys)) {
                return false;
            }
            for (String roleKey : roleKeys) {
                if (HrTalentConstants.GROUP_LEVEL_ROLE_KEYS.contains(roleKey)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 构造不可变集合副本。
         *
         * @param source 源集合
         * @param <T>    元素类型
         * @return 不可变集合（不为 null）
         */
        private static <T> Set<T> immutable(Set<T> source) {
            return source == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(source));
        }
    }

}
