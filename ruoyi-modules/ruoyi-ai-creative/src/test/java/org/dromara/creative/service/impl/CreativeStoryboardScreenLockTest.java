package org.dromara.creative.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.content.service.IContentBrandBriefService;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.DpStoryboard;
import org.dromara.creative.domain.DpStoryboardScreen;
import org.dromara.creative.domain.bo.CreativeScreenBo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.helper.CreativeDraftBrain;
import org.dromara.creative.helper.CreativeScreenSkeletonRegistry;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.mapper.DpStoryboardMapper;
import org.dromara.creative.mapper.DpStoryboardScreenMapper;
import org.dromara.creative.service.ICreativeCopyService;
import org.dromara.creative.service.ICreativeDirectionService;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeModuleService;
import org.dromara.creative.service.ICreativeProjectService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.GenericApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分镜「逐屏锁定 + 屏数自定义」的单元测试（v1 人工测试反馈裁定 ④，2026-10-06）。
 *
 * <p><b>裁定原文</b>：「由使用人说了算，可自定义不同的屏数，可以原地锁定一个屏幕，但其余可以自定义」。
 * 以及本轮同时定的两条边界：加/删屏**只发生在整版锁定之前**；出图闸门**仍是整版锁定**，
 * 单屏锁定只是草稿期防误改。</p>
 *
 * <p>要钉住的四件事（每一条都是"少写一个 if 就会静默失效"的规则）：</p>
 * <ul>
 *   <li>锁住的那一屏不能改，<b>其余屏照旧能改</b>（只锁一屏，不是整版）；</li>
 *   <li>锁定一屏的前置条件与整版锁定一致：这一屏得先有画面独白；</li>
 *   <li>整版锁定后，逐屏锁/解锁、加屏、删屏全部拒绝（屏集合就是锁定那一刻的约定）；</li>
 *   <li>加屏是"人工新增的屏"：文案留空、不冒充某个模块（spec 里不带 moduleCode），屏号重排。</li>
 * </ul>
 *
 * <p>用确定性单测而不是真跑一遍：这些规则全在"状态分支"上，真机要造出"某屏已锁、其余未锁"
 * 需要走完生成+锁定+编辑（动数据、动阶段），成本高且结果不可控。</p>
 *
 * @author creative
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeStoryboardScreenLockTest {

    private static final long TASK_ID = 2104582766641799169L;
    private static final long STORYBOARD_ID = 2107020000000000001L;
    private static final long SCREEN_A = 2107020000000000011L;
    private static final long SCREEN_B = 2107020000000000012L;

    @Mock
    private CreativeScreenSkeletonRegistry skeletonRegistry;
    @Mock
    private DpStoryboardMapper storyboardMapper;
    @Mock
    private DpStoryboardScreenMapper screenMapper;
    @Mock
    private DpGenerationMapper generationMapper;
    @Mock
    private ICreativeDnaService dnaService;
    @Mock
    private ICreativeDirectionService directionService;
    @Mock
    private ICreativeProjectService projectService;
    @Mock
    private IContentTaskService contentTaskService;
    @Mock
    private CreativeDraftBrain brain;
    @Mock
    private IContentBrandBriefService briefService;
    @Mock
    private ICreativeCopyService copyService;
    @Mock
    private ICreativeModuleService moduleService;

    private CreativeStoryboardServiceImpl service;

    /**
     * 最小 Spring 上下文：只为 {@code JsonUtils} 备一个 {@code JsonMapper}。
     *
     * <p>为什么要这一步：{@code JsonUtils} 的静态 {@code JSON_MAPPER} 是
     * {@code SpringUtils.getBean(JsonMapper.class)} 拿的，纯单测里没有 Spring 环境，
     * 第一次用到它就会 {@code ExceptionInInitializerError}（然后整个 JVM 里这个类就废了）。
     * 而"编辑一屏""锁定一屏""加屏"这些路径**必然**要写事件/规格 JSON——不备好这一层，
     * 测出来的就不是业务规则，而是"这个类在单测里用不了"。</p>
     */
    private static GenericApplicationContext jsonContext;

    @BeforeAll
    static void initJson() {
        jsonContext = new GenericApplicationContext();
        jsonContext.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        jsonContext.refresh();
        // hutool 的 SpringUtil 只有**实例方法**能把上下文塞进它的静态字段（正常由 Spring 容器在
        // ApplicationContextAware 回调里调）。纯单测里没有容器，只能自己调这一次。
        SpringUtil springUtil = new SpringUtil();
        springUtil.setApplicationContext(jsonContext);
    }

    @BeforeEach
    void setUp() {
        service = new CreativeStoryboardServiceImpl(skeletonRegistry, storyboardMapper, screenMapper,
            generationMapper, dnaService, directionService, projectService, contentTaskService, brain,
            briefService, copyService, moduleService);
        // 注意：屏类型的中文短名来自 CreativeScreenSkeletonRegistry#skeleton()，
        // 那是**静态**方法（给静态工具类用的懒加载入口），Mockito 拦不住也不该拦——
        // 它会按内置契约懒加载一份真的骨架，于是 descOf 走的是与生产同一份契约。
    }

    private DpStoryboard storyboard(String status) {
        DpStoryboard entity = new DpStoryboard();
        entity.setId(STORYBOARD_ID);
        entity.setTaskId(TASK_ID);
        entity.setStoryboardNo("SB-20261006-0001");
        entity.setVersion(1);
        entity.setStatus(status);
        entity.setScreenCount(2);
        return entity;
    }

    private DpStoryboardScreen screen(long id, String screenNo, int sortNo, String lockStatus, String solo) {
        DpStoryboardScreen screen = new DpStoryboardScreen();
        screen.setId(id);
        screen.setStoryboardId(STORYBOARD_ID);
        screen.setTaskId(TASK_ID);
        screen.setScreenNo(screenNo);
        screen.setSortNo(sortNo);
        screen.setScreenType(sortNo == 1 ? "HERO" : "SELLING_POINT");
        screen.setProductLockLevel("STRICT");
        screen.setStatus("DRAFT");
        screen.setLockStatus(lockStatus);
        screen.setPictureSoloStatement(solo);
        return screen;
    }

    private CreativeScreenBo editBo(long screenId, String title) {
        CreativeScreenBo bo = new CreativeScreenBo();
        bo.setId(screenId);
        bo.setTitle(title);
        return bo;
    }

    // ------------------------------------------------------------------
    // 逐屏锁定
    // ------------------------------------------------------------------

    @Test
    @DisplayName("④ 锁住的那一屏不能改，报错说清是「这一屏」而不是「整版」")
    void lockedScreenCannotBeEdited() {
        when(storyboardMapper.selectById(STORYBOARD_ID)).thenReturn(storyboard("DRAFT"));
        when(screenMapper.selectById(SCREEN_A))
            .thenReturn(screen(SCREEN_A, "S01", 1, "LOCKED", "白底主图要讲清形态"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateScreen(TASK_ID, editBo(SCREEN_A, "改个标题")));
        assertTrue(ex.getMessage().contains("S01"), "报错要点名是哪一屏：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("解锁这一屏"), "报错要给出出路（解锁这一屏）：" + ex.getMessage());
        assertFalse(ex.getMessage().contains("整版"), "不许说成整版锁定，那会让人以为别的屏也动不了：" + ex.getMessage());
        verify(screenMapper, never()).updateById(any(DpStoryboardScreen.class));
    }

    @Test
    @DisplayName("④ 只锁了一屏时，其余屏照旧能改（这正是裁定的后半句）")
    void otherScreensStayEditable() {
        when(storyboardMapper.selectById(STORYBOARD_ID)).thenReturn(storyboard("DRAFT"));
        DpStoryboardScreen draft = screen(SCREEN_B, "S02", 2, "DRAFT", "卖点画面");
        when(screenMapper.selectById(SCREEN_B)).thenReturn(draft);

        DpStoryboardScreenVo vo = service.updateScreen(TASK_ID, editBo(SCREEN_B, "改过的卖点标题"));

        assertEquals("改过的卖点标题", draft.getTitle());
        verify(screenMapper).updateById(draft);
        assertTrue(vo.getEditable(), "未锁的屏必须标成可改：" + vo.getLockStatusDesc());
        assertEquals("未锁定，可改", vo.getLockStatusDesc());
    }

    @Test
    @DisplayName("④ 锁定一屏要求先有画面独白（与整版锁定同一条要求，只是下移到屏级）")
    void lockingAScreenNeedsSoloStatement() {
        when(storyboardMapper.selectById(STORYBOARD_ID)).thenReturn(storyboard("DRAFT"));
        when(screenMapper.selectById(SCREEN_A)).thenReturn(screen(SCREEN_A, "S01", 1, "DRAFT", "  "));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.lockScreen(TASK_ID, SCREEN_A, true));
        assertTrue(ex.getMessage().contains("画面独白"), ex.getMessage());
        verify(screenMapper, never()).updateById(any(DpStoryboardScreen.class));
    }

    @Test
    @DisplayName("④ 锁定一屏：只有这一屏被冻结，返回值也如实说清")
    void lockingAScreenFreezesOnlyThatScreen() {
        when(storyboardMapper.selectById(STORYBOARD_ID)).thenReturn(storyboard("DRAFT"));
        DpStoryboardScreen screen = screen(SCREEN_A, "S01", 1, "DRAFT", "白底主图要讲清形态");
        when(screenMapper.selectById(SCREEN_A)).thenReturn(screen);

        DpStoryboardScreenVo vo = service.lockScreen(TASK_ID, SCREEN_A, true);

        assertEquals("LOCKED", screen.getLockStatus());
        verify(screenMapper).updateById(screen);
        assertEquals("已单独锁定（这一屏已冻结）", vo.getLockStatusDesc());
        assertFalse(vo.getEditable());
        // 解锁：回到可改
        DpStoryboardScreenVo unlocked = service.lockScreen(TASK_ID, SCREEN_A, false);
        assertEquals("DRAFT", screen.getLockStatus());
        assertTrue(unlocked.getEditable());
    }

    @Test
    @DisplayName("④ 整版锁定后，逐屏锁/解锁不再起作用（要说清原因，而不是静默失败）")
    void screenLockRejectedAfterWholeLock() {
        when(storyboardMapper.selectById(STORYBOARD_ID)).thenReturn(storyboard("LOCKED"));
        when(screenMapper.selectById(SCREEN_A)).thenReturn(screen(SCREEN_A, "S01", 1, "LOCKED", "独白"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.lockScreen(TASK_ID, SCREEN_A, false));
        assertTrue(ex.getMessage().contains("整版分镜已锁定"), ex.getMessage());
        assertTrue(ex.getMessage().contains("重新生成分镜"), "要给出出路：" + ex.getMessage());
        verify(screenMapper, never()).updateById(any(DpStoryboardScreen.class));
    }

    @Test
    @DisplayName("④ 整版锁定时，这一版所有屏一起标成已锁定（逐屏状态与版本状态不许自相矛盾）")
    void wholeLockMarksEveryScreenLocked() {
        DpStoryboard entity = storyboard("DRAFT");
        when(storyboardMapper.selectById(STORYBOARD_ID)).thenReturn(entity);
        DpStoryboardScreen a = screen(SCREEN_A, "S01", 1, "LOCKED", "独白 A");
        DpStoryboardScreen b = screen(SCREEN_B, "S02", 2, "DRAFT", "独白 B");
        when(screenMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(a, b)));

        DpStoryboardVo vo;
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            vo = service.lock(TASK_ID, STORYBOARD_ID);
        }

        assertEquals("LOCKED", entity.getStatus());
        assertEquals("LOCKED", b.getLockStatus(), "整版锁定时未锁的屏也要标成已锁定");
        verify(screenMapper).updateById(b);
        assertEquals("整版已锁定，不可改", vo.getScreens().get(0).getLockStatusDesc());
        assertFalse(vo.getScreens().get(0).getEditable());
    }

    // ------------------------------------------------------------------
    // 屏数自定义（加屏 / 删屏）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("④ 加屏：文案留空、不冒充模块、屏号重排（不替人编一句）")
    void addScreenKeepsCopyEmptyAndDropsModuleOwner() {
        DpStoryboard entity = storyboard("DRAFT");
        when(storyboardMapper.selectById(STORYBOARD_ID)).thenReturn(entity);
        DpStoryboardScreen a = screen(SCREEN_A, "S01", 1, "DRAFT", "独白 A");
        a.setSpecJson("{\"shot\":\"中景\",\"moduleCode\":\"HERO_MAIN\",\"moduleName\":\"主图\","
            + "\"objective\":\"一眼认出产品\",\"qaRules\":{\"square\":true}}");
        DpStoryboardScreen b = screen(SCREEN_B, "S02", 2, "DRAFT", "独白 B");
        b.setSpecJson("{\"shot\":\"近景\",\"moduleCode\":\"SELLING_ONE\"}");
        List<DpStoryboardScreen> rows = new ArrayList<>(List.of(a, b));
        when(screenMapper.selectList(any())).thenReturn(rows);
        when(screenMapper.selectById(SCREEN_A)).thenReturn(a);

        DpStoryboardScreenVo vo = service.addScreen(TASK_ID, SCREEN_A);

        ArgumentCaptor<DpStoryboardScreen> inserted = ArgumentCaptor.forClass(DpStoryboardScreen.class);
        verify(screenMapper).insert(inserted.capture());
        DpStoryboardScreen added = inserted.getValue();
        assertNull(added.getTitle(), "新增的屏不许替人编标题");
        assertNull(added.getSubtitle());
        assertNull(added.getBodyText());
        assertNull(added.getPictureSoloStatement(), "新增的屏不许替人编画面独白");
        assertEquals("HERO", added.getScreenType(), "屏类型沿用参照屏（同一份基因与方向）");
        assertFalse(added.getSpecJson().contains("moduleCode"), "人工新增的屏不该冒充某个模块：" + added.getSpecJson());
        assertFalse(added.getSpecJson().contains("objective"), added.getSpecJson());
        assertTrue(added.getSpecJson().contains("manualAdded"), added.getSpecJson());
        assertTrue(added.getSpecJson().contains("中景"), "取景/构图这些仍沿用参照屏：" + added.getSpecJson());
        assertEquals("DRAFT", added.getLockStatus());

        // 屏号重排：新屏插在 S01 之后 → 它成为 S02，原来的 S02 变成 S03
        assertEquals(3, entity.getScreenCount(), "屏数要写回分镜");
        assertEquals("S02", vo.getScreenNo());
        assertEquals("S03", b.getScreenNo());
        assertEquals(3, b.getSortNo());
        assertEquals(1, a.getSortNo(), "S01 没动，就不该产生一次无谓的更新");
    }

    @Test
    @DisplayName("④ 整版锁定后不能加屏（屏集合就是锁定那一刻的约定）")
    void addScreenRejectedAfterWholeLock() {
        when(storyboardMapper.selectById(STORYBOARD_ID)).thenReturn(storyboard("LOCKED"));
        when(screenMapper.selectById(SCREEN_A))
            .thenReturn(screen(SCREEN_A, "S01", 1, "LOCKED", "独白"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.addScreen(TASK_ID, SCREEN_A));
        assertTrue(ex.getMessage().contains("屏集合"), ex.getMessage());
        assertTrue(ex.getMessage().contains("重新生成分镜"), ex.getMessage());
        verify(screenMapper, never()).insert(any(DpStoryboardScreen.class));
    }

    @Test
    @DisplayName("④ 已单独锁定的屏不能删（先解锁），最后一屏不能删")
    void deleteScreenGuards() {
        when(storyboardMapper.selectById(STORYBOARD_ID)).thenReturn(storyboard("DRAFT"));

        DpStoryboardScreen locked = screen(SCREEN_A, "S01", 1, "LOCKED", "独白 A");
        when(screenMapper.selectById(SCREEN_A)).thenReturn(locked);
        ServiceException lockedEx = assertThrows(ServiceException.class,
            () -> service.deleteScreen(TASK_ID, SCREEN_A));
        assertTrue(lockedEx.getMessage().contains("已单独锁定"), lockedEx.getMessage());

        DpStoryboardScreen only = screen(SCREEN_B, "S01", 1, "DRAFT", "独白");
        when(screenMapper.selectById(SCREEN_B)).thenReturn(only);
        when(screenMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(only)));
        ServiceException lastEx = assertThrows(ServiceException.class,
            () -> service.deleteScreen(TASK_ID, SCREEN_B));
        assertTrue(lastEx.getMessage().contains("最后一屏"), lastEx.getMessage());
        verify(screenMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("④ 已经有出图记录的屏不能删（否则已有候选会对不上屏）")
    void screenWithGenerationsCannotBeDeleted() {
        when(storyboardMapper.selectById(STORYBOARD_ID)).thenReturn(storyboard("DRAFT"));
        DpStoryboardScreen a = screen(SCREEN_A, "S01", 1, "DRAFT", "独白 A");
        DpStoryboardScreen b = screen(SCREEN_B, "S02", 2, "DRAFT", "独白 B");
        when(screenMapper.selectById(SCREEN_A)).thenReturn(a);
        when(screenMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(a, b)));
        when(generationMapper.selectCount(any())).thenReturn(2L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.deleteScreen(TASK_ID, SCREEN_A));
        assertTrue(ex.getMessage().contains("出图记录"), ex.getMessage());
        assertTrue(ex.getMessage().contains("2"), ex.getMessage());
        verify(screenMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("④ 删屏：真的删掉、屏号重排、屏数写回")
    void deleteScreenRenumbers() {
        DpStoryboard entity = storyboard("DRAFT");
        when(storyboardMapper.selectById(STORYBOARD_ID)).thenReturn(entity);
        DpStoryboardScreen a = screen(SCREEN_A, "S01", 1, "DRAFT", "独白 A");
        DpStoryboardScreen b = screen(SCREEN_B, "S02", 2, "DRAFT", "独白 B");
        when(screenMapper.selectById(SCREEN_A)).thenReturn(a);
        when(screenMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(a, b)));
        when(generationMapper.selectCount(any())).thenReturn(0L);

        service.deleteScreen(TASK_ID, SCREEN_A);

        verify(screenMapper).deleteById(SCREEN_A);
        assertEquals("S01", b.getScreenNo(), "删掉 S01 之后原来的 S02 要顶上成为 S01");
        assertEquals(1, entity.getScreenCount());
        verify(screenMapper, times(1)).updateById(b);
    }

    @Test
    @DisplayName("④ 别的项目的屏：先被归属校验拦住（守卫顺序不能反）")
    void screenOfAnotherTaskIsRejected() {
        DpStoryboardScreen foreign = screen(SCREEN_A, "S01", 1, "DRAFT", "独白");
        foreign.setTaskId(TASK_ID + 1);
        when(screenMapper.selectById(SCREEN_A)).thenReturn(foreign);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.lockScreen(TASK_ID, SCREEN_A, true));
        assertTrue(ex.getMessage().contains("不属于该项目"), ex.getMessage());
        verify(screenMapper, never()).updateById(any(DpStoryboardScreen.class));
    }
}
