package org.dromara.aigov.workspace.portal.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.domain.bo.AigTaskQueryBo;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.workspace.domain.AigRoleAction;
import org.dromara.aigov.workspace.domain.AigRoleBinding;
import org.dromara.aigov.workspace.domain.AigRolePackageDraft;
import org.dromara.aigov.workspace.domain.AigRoleProfile;
import org.dromara.aigov.workspace.domain.AigRoleVersion;
import org.dromara.aigov.workspace.enums.AigRoleReleaseStatusEnum;
import org.dromara.aigov.workspace.mapper.AigRoleActionMapper;
import org.dromara.aigov.workspace.mapper.AigRoleBindingMapper;
import org.dromara.aigov.workspace.mapper.AigRoleProfileMapper;
import org.dromara.aigov.workspace.mapper.AigRoleVersionMapper;
import org.dromara.aigov.workspace.portal.domain.AigPortalActionContext;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalActionVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalCategoryVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleHomeVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalTaskVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.helper.AigRoleVisibilityResolver;
import org.dromara.aigov.workspace.portal.helper.AigVersionPick;
import org.dromara.aigov.workspace.portal.service.IAigPortalService;
import org.dromara.aigov.workspace.helper.AigRolePackageManifest;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工门户只读服务实现（主文档线增量 2）。
 *
 * <h3>可见性判定的两处判断是刻意的</h3>
 * <p>查询里先按 {@code release_status='PUBLISHED'} 粗筛（这是性能，也是"别让已经明确不可见的数据参与判定"），
 * 再逐版本走 {@link AigRoleVisibilityResolver}（这是<b>语义</b>：状态是否可见、范围声明是否可读、
 * 绑定是否命中与是否生效）。判定只有一处权威——解析器。查询条件变更不会悄悄扩大可见范围。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigPortalServiceImpl implements IAigPortalService {

    /**
     * 启用
     */
    private static final String ENABLED_YES = "Y";

    private final AigRoleProfileMapper roleProfileMapper;
    private final AigRoleVersionMapper roleVersionMapper;
    private final AigRoleActionMapper roleActionMapper;
    private final AigRoleBindingMapper roleBindingMapper;
    private final IAigTaskService taskService;

    @Override
    public List<AigPortalRoleVo> listMyRoles(AigPortalActor actor) {
        return visibleRoleHomes(actor).values().stream()
            .map(entry -> (AigPortalRoleVo) entry.home())
            .sorted(Comparator.comparing(AigPortalRoleVo::getRoleCode,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    @Override
    public AigPortalRoleHomeVo getRoleHome(String roleCode, AigPortalActor actor) {
        requireActor(actor);
        if (StringUtils.isBlank(roleCode)) {
            throw new ServiceException("岗位编码不能为空");
        }
        return requireHome(roleCode, actor).home();
    }

    @Override
    public AigPortalActionContext resolveAction(String roleCode, String actionCode, AigPortalActor actor) {
        if (StringUtils.isBlank(actionCode)) {
            throw new ServiceException("卡片编码不能为空");
        }
        HomeEntry entry = requireHome(roleCode, actor);
        AigRoleAction action = entry.actions().get(actionCode.trim());
        if (action == null) {
            // 卡片不存在、被停用、或分类已不在清单里（漂移）：对用户都是"这张卡片现在不可用"
            throw new ServiceException("卡片不存在或当前不可用：" + actionCode.trim());
        }
        return new AigPortalActionContext(entry.version().getRoleVersionId(),
            entry.version().getRoleId(), entry.home().getRoleCode(), entry.version().getVersion(),
            action.getActionCode(), action.getTitle(), action.getLaunchMode(), action.getTargetType(),
            action.getTargetRef(), action.getStudioRouteKey(), splitContextKeys(action.getRequiredContext()));
    }

    /**
     * 取某个岗位对当前用户可见的那一份（取不到就按"不存在"拒绝）。
     *
     * <p>刻意不说"这个岗位存在但你没权限"：那等于确认了岗位存在，会变成信息泄漏。</p>
     *
     * @param roleCode 岗位编码
     * @param actor    当前用户
     * @return 岗位条目
     */
    private HomeEntry requireHome(String roleCode, AigPortalActor actor) {
        requireActor(actor);
        if (StringUtils.isBlank(roleCode)) {
            throw new ServiceException("岗位编码不能为空");
        }
        HomeEntry entry = visibleRoleHomes(actor).get(roleCode.trim());
        if (entry == null) {
            throw new ServiceException("岗位不存在或当前没有对你开放的版本：" + roleCode.trim());
        }
        return entry;
    }

    @Override
    public PageResult<AigPortalTaskVo> myTasks(AigTaskQueryBo bo, PageQuery pageQuery, AigPortalActor actor) {
        requireActor(actor);
        AigTaskQueryBo query = bo == null ? new AigTaskQueryBo() : bo;
        // 强制覆盖：门户永远只看自己的任务。若信调用方传的 createBy，
        // 这个接口立刻变成一个"能读别人任务"的入口，而它连任务权限点都没有
        query.setCreateBy(actor.userId());
        PageResult<AigTaskVo> page = taskService.queryPage(query, pageQuery);
        List<AigPortalTaskVo> rows = new ArrayList<>();
        for (AigTaskVo task : page.getRows()) {
            rows.add(toTaskVo(task));
        }
        return PageResult.build(rows, page.getTotal());
    }

    /**
     * 当前用户可见的岗位 → 岗位首页（每个岗位取最新可见版本）。
     *
     * <p>列表与详情<b>共用这一条路径</b>：可见性只有一处实现，才不会出现
     * "列表里看得到、点进去说没有权限"这种只有用户能发现的不一致。
     * 代价是列表也会把卡片读出来（当前规模下可接受）；若将来岗位数量变大，
     * 应优化查询而不是另写一份判定。</p>
     *
     * @param actor 当前用户
     * @return 岗位编码 → 岗位条目（保留插入顺序）
     */
    private Map<String, HomeEntry> visibleRoleHomes(AigPortalActor actor) {
        requireActor(actor);
        Map<String, HomeEntry> result = new LinkedHashMap<>();
        List<AigRoleVersion> versions = roleVersionMapper.selectList(Wrappers.<AigRoleVersion>lambdaQuery()
            .eq(AigRoleVersion::getReleaseStatus, AigRoleReleaseStatusEnum.PUBLISHED.getCode())
            .orderByDesc(AigRoleVersion::getRoleVersionId));
        if (versions.isEmpty()) {
            return result;
        }
        Map<Long, AigRoleProfile> profiles = loadProfiles(versions);
        Map<Long, List<AigRoleBinding>> bindings = loadBindings(versions);
        LocalDateTime now = LocalDateTime.now();
        for (AigRoleVersion version : versions) {
            AigRoleProfile profile = profiles.get(version.getRoleId());
            if (profile == null || StringUtils.isBlank(profile.getRoleCode())) {
                continue;
            }
            List<AigRoleBinding> rows = bindings.getOrDefault(version.getRoleVersionId(), List.of());
            AigRolePackageDraft manifest = safeManifest(version);
            boolean visible = AigRoleVisibilityResolver.visible(version.getReleaseStatus(),
                manifest.audienceScope(), rows, actor.orgScope(), actor.brands(), now);
            if (!visible) {
                continue;
            }
            HomeEntry candidate = buildHome(profile, version, manifest);
            HomeEntry existing = result.get(profile.getRoleCode());
            if (existing == null
                || AigVersionPick.compare(version.getVersion(), existing.version().getVersion()) > 0) {
                result.put(profile.getRoleCode(), candidate);
            }
        }
        return result;
    }

    /**
     * 组装岗位首页（卡片在这里被过滤）。
     *
     * @param profile  岗位
     * @param version  版本
     * @param manifest 清单
     * @return 岗位条目（含过滤后的卡片行，供启动链路定位"确切的哪张卡片"）
     */
    private HomeEntry buildHome(AigRoleProfile profile, AigRoleVersion version,
                                AigRolePackageDraft manifest) {
        List<String> categoryCodes = new ArrayList<>();
        Map<String, String> categoryNames = new LinkedHashMap<>();
        for (AigRolePackageDraft.CategoryDraft category : manifest.categories()) {
            if (category == null || StringUtils.isBlank(category.code())) {
                continue;
            }
            categoryCodes.add(category.code().trim());
            categoryNames.put(category.code().trim(), category.name());
        }
        List<AigPortalActionVo> actions = new ArrayList<>();
        Map<String, AigRoleAction> actionRows = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String code : categoryCodes) {
            counts.put(code, 0);
        }
        for (AigRoleAction action : listActions(version.getRoleVersionId())) {
            // 服务端过滤：只有启用中的卡片，且分类必须能在清单里找到。
            // 分类找不到说明"清单与卡片表漂移"——这种卡片不能发出去（它会被挂在看不见的栏目下）
            if (!ENABLED_YES.equalsIgnoreCase(StringUtils.trim(action.getEnabled()))) {
                continue;
            }
            String category = action.getCategoryCode() == null ? null : action.getCategoryCode().trim();
            if (category == null || !counts.containsKey(category)) {
                continue;
            }
            actions.add(toActionVo(action));
            actionRows.put(action.getActionCode(), action);
            counts.put(category, counts.get(category) + 1);
        }
        AigPortalRoleHomeVo home = new AigPortalRoleHomeVo();
        home.setRoleCode(profile.getRoleCode());
        home.setRoleName(profile.getRoleName());
        home.setDescription(profile.getDescription());
        home.setVersion(version.getVersion());
        home.setActions(actions);
        home.setActionCount(actions.size());
        List<AigPortalCategoryVo> categories = new ArrayList<>();
        for (String code : categoryCodes) {
            AigPortalCategoryVo vo = new AigPortalCategoryVo();
            vo.setCode(code);
            vo.setName(categoryNames.get(code));
            vo.setActionCount(counts.getOrDefault(code, 0));
            categories.add(vo);
        }
        home.setCategories(categories);
        return new HomeEntry(version, home, actionRows);
    }

    /**
     * 岗位条目：可见的那一份版本 + 首页视图 + 通过过滤的卡片行。
     *
     * @param version 版本
     * @param home    首页视图
     * @param actions 卡片行（按卡片编码索引）
     * @author ai-gov
     */
    private record HomeEntry(AigRoleVersion version, AigPortalRoleHomeVo home,
                             Map<String, AigRoleAction> actions) {
    }

    /**
     * 拆分上下文字符串。
     *
     * @param requiredContext 逗号分隔
     * @return 键列表
     */
    private static List<String> splitContextKeys(String requiredContext) {
        List<String> keys = new ArrayList<>();
        if (StringUtils.isBlank(requiredContext)) {
            return keys;
        }
        for (String raw : requiredContext.split(",")) {
            if (StringUtils.isNotBlank(raw)) {
                keys.add(raw.trim());
            }
        }
        return keys;
    }

    /**
     * 读清单；读不出来时返回空清单（audienceScope 为 null ⇒ 判定为不可见，fail-closed）。
     *
     * @param version 版本
     * @return 草稿（无卡片）
     */
    private AigRolePackageDraft safeManifest(AigRoleVersion version) {
        try {
            return AigRolePackageManifest.fromJson(version.getManifestJson(), List.of());
        } catch (IllegalArgumentException e) {
            log.warn("岗位版本清单无法解析，按不可见处理：roleVersionId={} 异常={}",
                version.getRoleVersionId(), e.getMessage());
            return AigRolePackageManifest.fromJson("{}", List.of());
        }
    }

    /**
     * 读某版本的卡片（按分类/排序）。
     *
     * @param roleVersionId 版本ID
     * @return 卡片
     */
    private List<AigRoleAction> listActions(Long roleVersionId) {
        return roleActionMapper.selectList(Wrappers.<AigRoleAction>lambdaQuery()
            .eq(AigRoleAction::getRoleVersionId, roleVersionId)
            .orderByAsc(AigRoleAction::getCategoryCode)
            .orderByAsc(AigRoleAction::getSortOrder)
            .orderByAsc(AigRoleAction::getActionId));
    }

    /**
     * 批量取岗位。
     *
     * @param versions 版本
     * @return roleId → 岗位
     */
    private Map<Long, AigRoleProfile> loadProfiles(List<AigRoleVersion> versions) {
        Map<Long, AigRoleProfile> result = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>();
        for (AigRoleVersion version : versions) {
            if (version.getRoleId() != null && !result.containsKey(version.getRoleId())) {
                result.put(version.getRoleId(), null);
                ids.add(version.getRoleId());
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        for (AigRoleProfile profile : roleProfileMapper.selectList(Wrappers.<AigRoleProfile>lambdaQuery()
            .in(AigRoleProfile::getRoleId, ids))) {
            result.put(profile.getRoleId(), profile);
        }
        return result;
    }

    /**
     * 批量取绑定（一次查完，避免逐个版本查库）。
     *
     * @param versions 版本
     * @return roleVersionId → 绑定
     */
    private Map<Long, List<AigRoleBinding>> loadBindings(List<AigRoleVersion> versions) {
        Map<Long, List<AigRoleBinding>> result = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>();
        for (AigRoleVersion version : versions) {
            if (version.getRoleVersionId() != null && !result.containsKey(version.getRoleVersionId())) {
                result.put(version.getRoleVersionId(), new ArrayList<>());
                ids.add(version.getRoleVersionId());
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        for (AigRoleBinding binding : roleBindingMapper.selectList(Wrappers.<AigRoleBinding>lambdaQuery()
            .in(AigRoleBinding::getRoleVersionId, ids))) {
            result.computeIfAbsent(binding.getRoleVersionId(), key -> new ArrayList<>()).add(binding);
        }
        return result;
    }

    /**
     * 卡片 → 门户视图（只带门户需要的字段）。
     *
     * @param action 卡片
     * @return 视图
     */
    private AigPortalActionVo toActionVo(AigRoleAction action) {
        AigPortalActionVo vo = new AigPortalActionVo();
        vo.setActionCode(action.getActionCode());
        vo.setCategoryCode(action.getCategoryCode());
        vo.setTitle(action.getTitle());
        vo.setDescription(action.getDescription());
        vo.setLaunchMode(action.getLaunchMode());
        vo.setTargetType(action.getTargetType());
        vo.setTargetRef(action.getTargetRef());
        vo.setStudioRouteKey(action.getStudioRouteKey());
        vo.setRequiredContext(action.getRequiredContext());
        vo.setSortOrder(action.getSortOrder());
        return vo;
    }

    /**
     * 任务 → 门户视图（刻意只带对外需要的字段，理由见 {@code AigPortalTaskVo}）。
     *
     * @param task 任务视图
     * @return 门户视图
     */
    private AigPortalTaskVo toTaskVo(AigTaskVo task) {
        AigPortalTaskVo vo = new AigPortalTaskVo();
        vo.setTaskId(task.getTaskId());
        vo.setTaskNo(task.getTaskNo());
        vo.setTaskType(task.getTaskType());
        vo.setCapabilityCode(task.getCapabilityCode());
        vo.setScenarioCode(task.getScenarioCode());
        vo.setProjectType(task.getProjectType());
        vo.setProjectId(task.getProjectId());
        vo.setStatus(task.getStatus());
        vo.setStatusLabel(task.getStatusLabel());
        vo.setProgress(task.getProgress());
        vo.setDataLevel(task.getDataLevel());
        vo.setStartedAt(task.getStartedAt());
        vo.setFinishedAt(task.getFinishedAt());
        return vo;
    }

    /**
     * 门户必须有登录用户。
     *
     * @param actor 用户
     */
    private void requireActor(AigPortalActor actor) {
        if (actor == null || actor.userId() == null) {
            throw new ServiceException("AI 工作台需要登录用户");
        }
    }

}
