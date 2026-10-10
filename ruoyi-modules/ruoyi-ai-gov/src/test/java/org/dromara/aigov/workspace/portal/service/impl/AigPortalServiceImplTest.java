package org.dromara.aigov.workspace.portal.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.task.domain.bo.AigTaskQueryBo;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.workspace.domain.AigRoleAction;
import org.dromara.aigov.workspace.domain.AigRoleBinding;
import org.dromara.aigov.workspace.domain.AigRolePackageDraft;
import org.dromara.aigov.workspace.domain.AigRoleProfile;
import org.dromara.aigov.workspace.domain.AigRoleVersion;
import org.dromara.aigov.workspace.helper.AigRolePackageManifest;
import org.dromara.aigov.workspace.mapper.AigRoleActionMapper;
import org.dromara.aigov.workspace.mapper.AigRoleBindingMapper;
import org.dromara.aigov.workspace.mapper.AigRoleProfileMapper;
import org.dromara.aigov.workspace.mapper.AigRoleVersionMapper;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalActionVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleHomeVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalTaskVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalArtifactVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.mapper.AigPortalArtifactMapper;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户服务的"范围与过滤"测试（增量 2）。
 *
 * <p>这里只钉住服务层的三件事——可见性的数学已经在
 * {@code AigRoleVisibilityResolverTest} 里逐条钉过，不重复：
 * <ol>
 *     <li><b>我的任务恒为当前用户</b>：调用方传别人的 {@code createBy} 也会被覆盖。
 *         少了这一条，这个连任务权限点都没有的接口就成了"能读别人任务"的入口；</li>
 *     <li><b>卡片在服务端被过滤</b>：停用的卡片、挂在清单里不存在的分类上的卡片都不发出去；</li>
 *     <li><b>一个岗位只出现一次</b>：同岗位多个可见版本时取版本号<b>最大</b>的那个
 *         （{@code 1.10.0} 必须赢过 {@code 1.9.0}，字符串比较会判反）。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPortalServiceImplTest {

    private static final AigPortalActor ACTOR =
        new AigPortalActor(9L, 102L, Set.of(102L, 100L), Set.of());

    private AigRoleProfileMapper profileMapper;
    private AigRoleVersionMapper versionMapper;
    private AigRoleActionMapper actionMapper;
    private AigRoleBindingMapper bindingMapper;
    private AigPortalArtifactMapper artifactMapper;
    private IAigTaskService taskService;
    private AigPortalServiceImpl service;

    /**
     * MyBatis-Plus 的 lambda 条件需要先注册实体元信息，否则构建 wrapper 时会抛
     * "can not find lambda cache for this entity"（真库里由启动扫描注册，单测里要自己来）。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigRoleVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigRoleProfile.class);
        TableInfoHelper.initTableInfo(assistant, AigRoleAction.class);
        TableInfoHelper.initTableInfo(assistant, AigRoleBinding.class);
    }

    @BeforeEach
    void setUp() {
        profileMapper = mock(AigRoleProfileMapper.class);
        versionMapper = mock(AigRoleVersionMapper.class);
        actionMapper = mock(AigRoleActionMapper.class);
        bindingMapper = mock(AigRoleBindingMapper.class);
        artifactMapper = mock(AigPortalArtifactMapper.class);
        taskService = mock(IAigTaskService.class);
        service = new AigPortalServiceImpl(profileMapper, versionMapper, actionMapper, bindingMapper,
            artifactMapper, taskService);
    }

    @Test
    @DisplayName("我的产物恒为当前用户：userId 由登录态传入，调用方无法指定别人的")
    void myArtifactsIsScopedToCurrentUser() {
        AigPortalArtifactVo row = new AigPortalArtifactVo();
        row.setArtifactId(7L);
        row.setTaskId(11L);
        Page<AigPortalArtifactVo> page = new Page<>();
        page.setRecords(List.of(row));
        page.setTotal(1L);
        when(artifactMapper.selectMyArtifactPage(any(), any(), any())).thenReturn(page);

        PageResult<AigPortalArtifactVo> result = service.myArtifacts(11L, new PageQuery(), ACTOR);

        assertEquals(1, result.getRows().size());
        assertEquals(7L, result.getRows().iterator().next().getArtifactId());
        ArgumentCaptor<Long> userCaptor = ArgumentCaptor.forClass(Long.class);
        verify(artifactMapper).selectMyArtifactPage(any(), userCaptor.capture(), eq(11L));
        assertEquals(9L, userCaptor.getValue(), "查询的提交人必须是登录用户");
        // 没有登录用户直接拒绝（与门户其它接口同一口径）
        assertThrows(ServiceException.class, () -> service.myArtifacts(null, new PageQuery(), null));
    }

    @Test
    @DisplayName("我的任务恒为当前用户：调用方传别人的 createBy 也会被覆盖")
    void myTasksAlwaysScopedToCurrentUser() {
        AigTaskQueryBo bo = new AigTaskQueryBo();
        bo.setCreateBy(123456L);
        when(taskService.queryPage(any(), any())).thenReturn(PageResult.build(List.of(task(1L)), 1L));

        PageResult<AigPortalTaskVo> page = service.myTasks(bo, new PageQuery(), ACTOR);

        assertEquals(9L, bo.getCreateBy(), "提交人必须被覆盖成登录用户");
        assertEquals(1, page.getRows().size());
        assertEquals(1L, page.getRows().iterator().next().getTaskId());
    }

    @Test
    @DisplayName("bo 为 null 时也只看自己；没有登录用户直接拒绝")
    void myTasksRejectsAnonymous() {
        when(taskService.queryPage(any(), any())).thenReturn(PageResult.build(List.of(), 0L));
        service.myTasks(null, new PageQuery(), ACTOR);

        assertThrows(ServiceException.class, () -> service.myTasks(null, new PageQuery(), null),
            "门户没有'匿名'这种状态");
        assertThrows(ServiceException.class, () -> service.listMyRoles(null));
    }

    @Test
    @DisplayName("卡片在服务端被过滤：停用的、以及挂在清单外分类上的都不发出去")
    void actionsAreFilteredServerSide() {
        stubSingleRole(List.of(
            action("A_VISIBLE", "DETAIL_PAGE", "Y"),
            action("B_DISABLED", "DETAIL_PAGE", "N"),
            action("C_ORPHAN", "GONE_CATEGORY", "Y")));

        AigPortalRoleHomeVo home = service.getRoleHome("GRAPHIC_DESIGNER_AI", ACTOR);

        List<String> codes = home.getActions().stream().map(AigPortalActionVo::getActionCode).toList();
        assertEquals(List.of("A_VISIBLE"), codes);
        assertEquals(1, home.getActionCount());
        // 空分类也要出现（"这一栏暂时是空的"是员工该知道的事实）
        assertTrue(home.getCategories().stream().anyMatch(item -> "BANNER".equals(item.getCode())));
        assertEquals(0, home.getCategories().stream()
            .filter(item -> "BANNER".equals(item.getCode())).findFirst().orElseThrow().getActionCount());
    }

    @Test
    @DisplayName("同岗位多个可见版本时取版本号最大的那个：1.10.0 赢过 1.9.0")
    void newestVersionWinsPerRole() {
        AigRoleVersion older = version(11L, "1.9.0");
        AigRoleVersion newer = version(12L, "1.10.0");
        when(versionMapper.selectList(any())).thenReturn(List.of(newer, older));
        when(profileMapper.selectList(any())).thenReturn(List.of(profile()));
        // 每次进入 visibleRoleHomes 都会按同一顺序读这两个版本的卡片；
        // 用"轮流"而不是 thenReturn(...).thenReturn(...)：后者在被读第二遍时只会重复最后一个桩，
        // 于是测试会因为桩被用尽而得出与产品行为无关的结论
        int[] call = {0};
        when(actionMapper.selectList(any())).thenAnswer(invocation -> {
            boolean firstVersion = (call[0]++ % 2) == 0;
            return List.of(action(firstVersion ? "NEW_CARD" : "OLD_CARD", "DETAIL_PAGE", "Y"));
        });

        List<AigPortalRoleVo> roles = service.listMyRoles(ACTOR);

        assertEquals(1, roles.size(), "一个岗位只出现一次");
        assertEquals("1.10.0", roles.get(0).getVersion());
        AigPortalRoleHomeVo home = service.getRoleHome("GRAPHIC_DESIGNER_AI", ACTOR);
        assertEquals("1.10.0", home.getVersion(), "详情也必须是最新可见版本");
        assertEquals(List.of("NEW_CARD"), home.getActions().stream()
            .map(AigPortalActionVo::getActionCode).toList());
    }

    @Test
    @DisplayName("对当前用户不可见的岗位，详情接口不给'存在但没权限'这种确认")
    void invisibleRoleIsReportedAsAbsent() {
        when(versionMapper.selectList(any())).thenReturn(List.of());
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.getRoleHome("GRAPHIC_DESIGNER_AI", ACTOR));
        assertTrue(e.getMessage().contains("不存在"), "不要确认岗位存在：" + e.getMessage());
        assertFalse(e.getMessage().contains("权限"));
    }

    /**
     * 造一个"可见"的岗位版本（scope=ALL，无需绑定）。
     *
     * @param actions 卡片
     */
    private void stubSingleRole(List<AigRoleAction> actions) {
        when(versionMapper.selectList(any())).thenReturn(List.of(version(11L, "1.0.0")));
        when(profileMapper.selectList(any())).thenReturn(List.of(profile()));
        when(actionMapper.selectList(any())).thenReturn(actions);
    }

    private static AigRoleProfile profile() {
        AigRoleProfile profile = new AigRoleProfile();
        profile.setRoleId(1L);
        profile.setRoleCode("GRAPHIC_DESIGNER_AI");
        profile.setRoleName("平面设计 AI 工作台");
        profile.setDescription("做详情页、横幅");
        return profile;
    }

    private static AigRoleVersion version(long id, String version) {
        AigRoleVersion row = new AigRoleVersion();
        row.setRoleVersionId(id);
        row.setRoleId(1L);
        row.setVersion(version);
        row.setReleaseStatus("PUBLISHED");
        row.setManifestJson(AigRolePackageManifest.toJson(new AigRolePackageDraft(
            "GRAPHIC_DESIGNER_AI", "平面设计 AI 工作台", version,
            List.of(new AigRolePackageDraft.CategoryDraft("DETAIL_PAGE", "详情页设计"),
                new AigRolePackageDraft.CategoryDraft("BANNER", "横幅")),
            "DETAIL_PAGE", List.of(), "ALL", "INTERNAL", "INTERNAL")));
        return row;
    }

    private static AigRoleAction action(String code, String category, String enabled) {
        AigRoleAction action = new AigRoleAction();
        action.setActionId((long) code.hashCode());
        action.setRoleVersionId(11L);
        action.setActionCode(code);
        action.setCategoryCode(category);
        action.setTitle(code);
        action.setLaunchMode("QUICK");
        action.setTargetType("QUICK_CAPABILITY");
        action.setTargetRef("cap/" + code);
        action.setEnabled(enabled);
        action.setSortOrder(1);
        return action;
    }

    private static AigTaskVo task(long taskId) {
        AigTaskVo task = new AigTaskVo();
        task.setTaskId(taskId);
        task.setTaskNo("T-" + taskId);
        task.setStatus("SUCCEEDED");
        task.setStatusLabel("成功");
        return task;
    }

}
