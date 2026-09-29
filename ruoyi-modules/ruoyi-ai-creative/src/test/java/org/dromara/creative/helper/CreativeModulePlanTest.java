package org.dromara.creative.helper;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.DpDeliveryType;
import org.dromara.creative.domain.DpModuleDefinition;
import org.dromara.creative.domain.DpProjectModule;
import org.dromara.creative.domain.DpStoryboard;
import org.dromara.creative.domain.DpStoryboardScreen;
import org.dromara.creative.domain.bo.ProjectModulePlanBo;
import org.dromara.creative.domain.vo.ProjectModulePlanVo;
import org.dromara.creative.mapper.CreativeTaskStageMapper;
import org.dromara.creative.mapper.DpDetailPageVersionMapper;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.mapper.DpModuleDefinitionMapper;
import org.dromara.creative.mapper.DpProjectModuleMapper;
import org.dromara.creative.mapper.DpStoryboardMapper;
import org.dromara.creative.mapper.DpStoryboardScreenMapper;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.dromara.creative.service.impl.CreativeModuleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 模块规划的单元测试（V0.2 R22，文档 §24）。
 *
 * <p>这一轮的四件事必须被钉住，否则"能改计划"会变成"能改坏计划"：</p>
 * <ol>
 *   <li><b>启停真的影响出屏</b>：停用的模块不出屏，但留在计划里（不是删除）；</li>
 *   <li><b>复制允许同名模块</b>：同一个模块编码出现两次时，展示名必须自动区分
 *       （主图一/主图二），否则屏骨架校验会直接抛错、整个分镜生不出来；</li>
 *   <li><b>不可逆状态 fail-closed</b>：分镜已锁定 / 已出图 / 已渲染时，保存必须被拒绝并说明原因；</li>
 *   <li><b>存进去的顺序就是出屏的顺序</b>：整份覆盖式保存后，按 sortNo 读出来与提交顺序一致。</li>
 * </ol>
 *
 * <p>与 {@link CreativeModuleEngineTest} 一样用"假表"：Mockito 的 stub 不会执行 SQL，
 * 不把 insert/排序/软删补上，测的就不是真实行为。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeModulePlanTest {

    @Mock
    private DpModuleDefinitionMapper definitionMapper;

    @Mock
    private DpProjectModuleMapper projectModuleMapper;

    @Mock
    private ICreativeScenarioConfigService scenarioConfigService;

    @Mock
    private CreativeTaskStageMapper stageMapper;

    @Mock
    private IContentTaskService contentTaskService;

    @Mock
    private DpStoryboardMapper storyboardMapper;

    @Mock
    private DpStoryboardScreenMapper screenMapper;

    @Mock
    private DpGenerationMapper generationMapper;

    @Mock
    private DpDetailPageVersionMapper detailPageVersionMapper;

    @InjectMocks
    private CreativeModuleServiceImpl service;

    private static final long TASK = 1234L;

    /** 假表：insert 真进表、selectList 真排序、deleteById 真软删（del_flag=1 不再出现） */
    private final List<DpProjectModule> table = new ArrayList<>();

    @BeforeEach
    void setUp() {
        table.clear();
        when(scenarioConfigService.getScenario(any())).thenReturn(null);
        DpDeliveryType type = new DpDeliveryType();
        type.setDeliveryType("ECOM_DETAIL");
        type.setDeliveryName("商品详情页");
        when(scenarioConfigService.getDeliveryType(any())).thenReturn(type);
        when(definitionMapper.selectList(any())).thenReturn(ecomLibrary());
        Map<String, Object> meta = new HashMap<>();
        meta.put("taskId", TASK);
        meta.put("taskName", "R22 测试项目");
        meta.put("deliveryType", "ECOM_DETAIL");
        meta.put("delFlag", "0");
        // 真实读法是两个标量查询（R22 第一版用 Map 取列，取到 null → 字符串 "null" → 模块库静默 0 条）
        when(stageMapper.selectDelFlag(TASK)).thenReturn("0");
        when(stageMapper.selectDeliverableType(TASK)).thenReturn("ECOM_DETAIL");
        when(contentTaskService.getDetail(TASK)).thenReturn((ContentTaskDetailVo) null);
        // 默认：没锁定、没出图、没渲染、没分镜
        when(storyboardMapper.selectCount(any())).thenReturn(0L);
        when(storyboardMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(generationMapper.selectCount(any())).thenReturn(0L);
        when(detailPageVersionMapper.selectCount(any())).thenReturn(0L);
        when(screenMapper.selectList(any())).thenReturn(new ArrayList<>());

        when(projectModuleMapper.selectList(any())).thenAnswer(inv -> {
            List<DpProjectModule> rows = new ArrayList<>();
            for (DpProjectModule row : table) {
                if (!"1".equals(row.getDelFlag())) {
                    rows.add(row);
                }
            }
            rows.sort((a, b) -> Integer.compare(
                a.getSortNo() == null ? 0 : a.getSortNo(), b.getSortNo() == null ? 0 : b.getSortNo()));
            return rows;
        });
        when(projectModuleMapper.insert(any(DpProjectModule.class))).thenAnswer(inv -> {
            DpProjectModule row = inv.getArgument(0);
            if (row.getDelFlag() == null) {
                row.setDelFlag("0");
            }
            // 真库会回填雪花 ID，假表也要给一个——否则后面的 deleteById(id) 拿到 null，
            // 软删就不会发生，测试会"看起来通过了覆盖保存、其实旧行还活着"。
            if (row.getId() == null) {
                row.setId(100L + table.size() + idCounter++);
            }
            table.add(row);
            return 1;
        });
        when(projectModuleMapper.deleteById(any(java.io.Serializable.class))).thenAnswer(inv -> {
            Object id = inv.getArgument(0);
            for (DpProjectModule row : table) {
                if (id != null && id.equals(row.getId())) {
                    row.setDelFlag("1");
                }
            }
            return 1;
        });
    }

    private int idCounter = 0;

    /** 种子里的 ECOM_DETAIL 模块库（默认骨架 6 条 + 1 条可选） */
    private static List<DpModuleDefinition> ecomLibrary() {
        List<DpModuleDefinition> list = new ArrayList<>();
        list.add(definition("HERO", "主图", "HERO", "STRICT", "产品全貌，正视角", 1, 1, 10));
        list.add(definition("SELLING_POINT", "卖点", "SELLING_POINT", "LOOSE", "功能相关中近景", 2, 2, 20));
        list.add(definition("SCENE_DISPLAY", "使用场景", "SCENE", "LOOSE", "环境全景", 1, 2, 30));
        list.add(definition("CRAFT_DETAIL", "细节工艺", "DETAIL", "STRICT", "局部大特写", 1, 2, 40));
        list.add(definition("SIZE_ADVANTAGE", "尺寸参数", "SIZE", "STRICT", "含参照物平视", 1, 1, 50));
        list.add(definition("BRAND_END", "品牌收尾", "BRAND", "LOOSE", "产品与品牌元素合影", 1, 1, 60));
        list.add(definition("GIFT_EXPRESSION", "礼赠表达", "SCENE", "LOOSE", "礼赠场景全景", 1, 1, 0));
        return list;
    }

    private static DpModuleDefinition definition(String code, String name, String screenType, String lock,
                                                 String shot, int min, int max, int sortNo) {
        DpModuleDefinition d = new DpModuleDefinition();
        d.setDeliveryType("ECOM_DETAIL");
        d.setModuleCode(code);
        d.setModuleName(name);
        d.setScreenType(screenType);
        d.setProductLockLevel(lock);
        d.setShot(shot);
        d.setRequired("1");
        d.setMinScreens(min);
        d.setMaxScreens(max);
        d.setDefaultSelected(sortNo > 0 ? "1" : "0");
        d.setDefaultSortNo(sortNo);
        d.setEnabled("0");
        return d;
    }

    private static ProjectModulePlanBo.Item item(String code, int screens, String enabled) {
        ProjectModulePlanBo.Item item = new ProjectModulePlanBo.Item();
        item.setModuleCode(code);
        item.setScreenCount(screens);
        item.setEnabled(enabled);
        return item;
    }

    private ProjectModulePlanBo plan(ProjectModulePlanBo.Item... items) {
        ProjectModulePlanBo bo = new ProjectModulePlanBo();
        bo.setModules(new ArrayList<>(List.of(items)));
        return bo;
    }

    @Test
    @DisplayName("交付类型取不到（null 或字面量 \"null\"）→ 明确报错，绝不用它去查模块库")
    void missingDeliveryTypeIsRejectedInsteadOfQueryingNull() {
        // 真机踩过：Map 取列拿到 null → String.valueOf 变成字符串 "null" → 模块库静默 0 条。
        // 两种脏值都必须被挡住，而不是"照查不误、返回空"。
        for (String dirty : new String[] {null, "null", "  "}) {
            when(stageMapper.selectDeliverableType(TASK)).thenReturn(dirty);
            ServiceException ex = assertThrows(ServiceException.class, () -> service.planOf(TASK),
                "交付类型=" + dirty + " 时必须报错");
            assertTrue(ex.getMessage().contains("交付类型"), ex.getMessage());
        }
    }

    @Test
    @DisplayName("已删除的项目不能规划模块")
    void deletedTaskIsRejected() {
        when(stageMapper.selectDelFlag(TASK)).thenReturn("1");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planOf(TASK));
        assertTrue(ex.getMessage().contains("已删除"), ex.getMessage());
    }

    @Test
    @DisplayName("停用的模块不出屏，但留在计划里（不是删除）")
    void disabledModuleStaysInPlanButNotInScreens() {
        service.savePlan(TASK, plan(item("HERO", 1, "0"), item("SCENE_DISPLAY", 1, "1"),
            item("BRAND_END", 1, "0")));

        CreativeScreenSkeleton skeleton = service.skeletonOf(TASK, "ECOM_DETAIL");
        assertEquals(2, skeleton.size(), "停用的场景模块不该出屏");
        assertEquals(List.of("HERO", "BRAND"),
            skeleton.screens().stream().map(CreativeScreenSkeleton.ScreenSpec::type).toList());
        // 计划里仍是 3 行（停用那行还在，可再启用）
        assertEquals(3, service.listProjectModules(TASK).size());
        assertEquals(1, service.listProjectModules(TASK).stream()
            .filter(m -> "1".equals(m.getEnabled())).count());
    }

    @Test
    @DisplayName("复制同一个模块：展示名自动区分（主图一/主图二），不与契约的展示名唯一性冲突")
    void duplicatedModuleGetsDistinctLabels() {
        service.savePlan(TASK, plan(item("HERO", 1, "0"), item("HERO", 1, "0")));

        CreativeScreenSkeleton skeleton = service.skeletonOf(TASK, "ECOM_DETAIL");
        assertEquals(2, skeleton.size());
        assertEquals(List.of("主图一", "主图二"),
            skeleton.screens().stream().map(CreativeScreenSkeleton.ScreenSpec::label).toList());
    }

    @Test
    @DisplayName("全部停用 → 明确报错（不静默回落契约文件的 7 屏）")
    void allDisabledThrows() {
        assertThrows(ServiceException.class, () -> service.savePlan(TASK, plan(item("HERO", 1, "1"))));
        // 直接构造"有计划但全停用"的库内状态，再取骨架也应报错
        DpProjectModule row = new DpProjectModule();
        row.setId(1L);
        row.setTaskId(TASK);
        row.setModuleCode("HERO");
        row.setModuleName("主图");
        row.setScreenType("HERO");
        row.setScreenCount(1);
        row.setSortNo(10);
        row.setEnabled("1");
        row.setDelFlag("0");
        table.add(row);
        assertThrows(ServiceException.class, () -> service.skeletonOf(TASK, "ECOM_DETAIL"));
    }

    @Test
    @DisplayName("屏数超过模块库上限 → 拒绝（不是静默截断）")
    void screenCountAboveLibraryMaxThrows() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.savePlan(TASK, plan(item("HERO", 3, "0"))));
        assertTrue(ex.getMessage().contains("最多"), ex.getMessage());
    }

    @Test
    @DisplayName("模块编码不在库里的 → 拒绝（页面只能从模块库添加）")
    void unknownModuleCodeThrows() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.savePlan(TASK, plan(item("NOT_A_MODULE", 1, "0"))));
        assertTrue(ex.getMessage().contains("模块库"), ex.getMessage());
    }

    @Test
    @DisplayName("分镜已锁定 / 已出图 / 已渲染 → 保存被拒并说明原因（fail-closed）")
    void irreversibleStatesAreRefused() {
        when(storyboardMapper.selectCount(any())).thenReturn(1L);
        ServiceException locked = assertThrows(ServiceException.class,
            () -> service.savePlan(TASK, plan(item("HERO", 1, "0"))));
        assertTrue(locked.getMessage().contains("分镜已锁定"), locked.getMessage());

        when(storyboardMapper.selectCount(any())).thenReturn(0L);
        when(generationMapper.selectCount(any())).thenReturn(3L);
        ServiceException generated = assertThrows(ServiceException.class,
            () -> service.savePlan(TASK, plan(item("HERO", 1, "0"))));
        assertTrue(generated.getMessage().contains("已经出过图"), generated.getMessage());

        when(generationMapper.selectCount(any())).thenReturn(0L);
        when(detailPageVersionMapper.selectCount(any())).thenReturn(2L);
        ServiceException rendered = assertThrows(ServiceException.class,
            () -> service.savePlan(TASK, plan(item("HERO", 1, "0"))));
        assertTrue(rendered.getMessage().contains("渲染过"), rendered.getMessage());
    }

    @Test
    @DisplayName("不可逆时规划视图本身也要标成不可编辑并给出原因")
    void planViewReportsWhyItIsNotEditable() {
        when(generationMapper.selectCount(any())).thenReturn(1L);
        ProjectModulePlanVo vo = service.planOf(TASK);
        assertFalse(vo.getEditable());
        assertTrue(vo.getEditBlockReason().contains("出过图"), vo.getEditBlockReason());
    }

    @Test
    @DisplayName("保存后按提交顺序读出（整份覆盖 + 旧行软删，不物理删除）")
    void saveKeepsSubmittedOrderAndSoftDeletesOldRows() {
        service.savePlan(TASK, plan(item("HERO", 1, "0"), item("BRAND_END", 1, "0")));
        Long firstHeroId = service.listProjectModules(TASK).get(0).getId();
        assertNotNull(firstHeroId);

        service.savePlan(TASK, plan(item("BRAND_END", 1, "0"), item("SELLING_POINT", 2, "0")));

        List<DpProjectModule> live = service.listProjectModules(TASK);
        assertEquals(2, live.size());
        assertEquals(List.of("BRAND_END", "SELLING_POINT"),
            live.stream().map(DpProjectModule::getModuleCode).toList());
        assertEquals("卖点一", service.skeletonOf(TASK, "ECOM_DETAIL").screens().get(1).label());
        // 旧的两行还在表里，但已软删（留痕）
        long softDeleted = table.stream().filter(r -> "1".equals(r.getDelFlag())).count();
        assertEquals(2, softDeleted, "旧计划应被软删而不是物理删除");
        assertEquals("MANUAL", live.get(0).getSource());
    }

    @Test
    @DisplayName("屏预览与真正出屏用的是同一段展开逻辑（屏数/类型/展示名逐屏一致）+ 缺事实被标出")
    void previewMatchesRealExpansionAndFlagsMissingFacts() {
        ProjectModulePlanBo bo = plan(item("HERO", 1, "0"), item("SELLING_POINT", 2, "0"),
            item("BRAND_END", 1, "0"));
        bo.getModules().get(0).setRequiredFactCodes("product_name, color");
        bo.getModules().get(0).setCopyText("人工写的正文");
        service.savePlan(TASK, bo);

        ProjectModulePlanVo vo = service.planOf(TASK);
        CreativeScreenSkeleton skeleton = service.skeletonOf(TASK, "ECOM_DETAIL");
        assertEquals(skeleton.size(), vo.getScreens().size());
        for (int i = 0; i < skeleton.size(); i++) {
            assertEquals(skeleton.screens().get(i).type(), vo.getScreens().get(i).screenType());
            assertEquals(skeleton.screens().get(i).label(), vo.getScreens().get(i).label());
        }
        assertEquals(List.of("S01", "S02", "S03", "S04"),
            vo.getScreens().stream().map(ProjectModulePlanVo.ScreenPreview::screenNo).toList());
        // 没有任何已确认事实 → 两个所需事实都算缺
        assertEquals(List.of("product_name", "color"), vo.getScreens().get(0).missingFacts());
        assertTrue(vo.getScreens().get(1).missingFacts().isEmpty(), "没配所需事实就没得缺");
        assertEquals("人工写的正文", service.listProjectModules(TASK).get(0).getCopyText());
        assertEquals("product_name,color",
            service.listProjectModules(TASK).get(0).getRequiredFactCodes(), "编码串要规整成无空格形式");
        // 没有分镜时 storyboard 段是"还没有分镜"的说明，不是 null
        assertNotNull(vo.getStoryboard());
        assertNull(vo.getStoryboard().storyboardId());
    }

    @Test
    @DisplayName("分镜与计划不一致时标 stale（屏类型顺序不同就算不一致）")
    void storyboardStalenessIsComputed() {
        service.savePlan(TASK, plan(item("HERO", 1, "0"), item("BRAND_END", 1, "0")));

        DpStoryboard storyboard = new DpStoryboard();
        storyboard.setId(99L);
        storyboard.setTaskId(TASK);
        storyboard.setVersion(1);
        storyboard.setStatus("DRAFT");
        storyboard.setScreenCount(7);
        when(storyboardMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(storyboard)));
        List<DpStoryboardScreen> screens = new ArrayList<>();
        for (String type : List.of("HERO", "SELLING_POINT", "SELLING_POINT", "SCENE", "DETAIL", "SIZE", "BRAND")) {
            DpStoryboardScreen screen = new DpStoryboardScreen();
            screen.setStoryboardId(99L);
            screen.setScreenType(type);
            screens.add(screen);
        }
        when(screenMapper.selectList(any())).thenReturn(screens);

        ProjectModulePlanVo vo = service.planOf(TASK);
        assertTrue(vo.getStoryboard().stale(), "7 屏的旧分镜 vs 2 屏的新计划：必须标成不一致");
        assertTrue(vo.getStoryboard().note().contains("重新拆分镜"), vo.getStoryboard().note());
    }
}
