package org.dromara.creative.helper;

import org.dromara.creative.domain.DpModuleDefinition;
import org.dromara.creative.domain.DpProjectModule;
import org.dromara.creative.mapper.DpModuleDefinitionMapper;
import org.dromara.creative.mapper.DpProjectModuleMapper;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.dromara.creative.service.impl.CreativeModuleServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 模块引擎的单元测试（V0.2 R21，文档 §18/§21）。
 *
 * <p><b>最重要的一条是"等价性"</b>：把 ECOM_DETAIL 的 7 屏逐字镜像成 6 个模块之后，
 * 模块引擎算出来的骨架必须与**契约文件**（{@code creative/screen-skeleton.json}）**逐屏一致**
 * （类型/展示名/保真等级/取景，顺序也一样）。等价性成立，才敢把分镜的屏集合从契约文件切到模块计划——
 * 否则既有项目会莫名其妙少屏或错位，而且看起来还挺像对的。</p>
 *
 * <p>其余几条：主图（MAIN_IMAGE）出的是**另一套屏**（5 屏、MAIN_* 类型）；项目没有模块计划
 * 且交付类型没有默认骨架时返回 null（调用方回落契约文件，不静默变空）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeModuleEngineTest {

    @Mock
    private DpModuleDefinitionMapper definitionMapper;

    @Mock
    private DpProjectModuleMapper projectModuleMapper;

    @Mock
    private ICreativeScenarioConfigService scenarioConfigService;

    @InjectMocks
    private CreativeModuleServiceImpl service;

    /** 种子里的 ECOM_DETAIL 默认骨架（与 dp_creative_r21_module_engine.sql 逐字一致） */
    private static List<DpModuleDefinition> ecomDefaults() {
        List<DpModuleDefinition> list = new ArrayList<>();
        list.add(definition("HERO", "主图", "HERO", "STRICT", "产品全貌，正视角（或 15° 微侧）", 1, 10));
        list.add(definition("SELLING_POINT", "卖点", "SELLING_POINT", "LOOSE", "功能/卖点相关的中近景", 2, 20));
        list.add(definition("SCENE_DISPLAY", "使用场景", "SCENE", "LOOSE", "环境全景，产品占画面 {ratio}", 1, 30));
        list.add(definition("CRAFT_DETAIL", "细节工艺", "DETAIL", "STRICT", "局部大特写（材质/结构/接口）", 1, 40));
        list.add(definition("SIZE_ADVANTAGE", "尺寸参数", "SIZE", "STRICT", "含参照物的平视构图", 1, 50));
        list.add(definition("BRAND_END", "品牌收尾", "BRAND", "LOOSE", "产品与品牌元素的合影", 1, 60));
        return list;
    }

    /** 种子里的主图默认骨架（5 个模块各 1 屏） */
    private static List<DpModuleDefinition> mainImageDefaults() {
        List<DpModuleDefinition> list = new ArrayList<>();
        list.add(definition("MAIN_WHITE_BG", "白底主图", "MAIN_WHITE_BG", "STRICT", "产品居中、纯白背景、正视角", 1, 10));
        list.add(definition("MAIN_SELLING", "卖点图", "MAIN_SELLING_POINT", "LOOSE", "卖点相关中近景 + 留出文案位", 1, 20));
        list.add(definition("MAIN_SCENE", "场景图", "MAIN_SCENE", "LOOSE", "环境全景，产品占画面 {ratio}", 1, 30));
        list.add(definition("MAIN_DETAIL", "细节图", "MAIN_DETAIL", "STRICT", "局部大特写（材质/结构）", 1, 40));
        list.add(definition("MAIN_SIZE", "尺寸图", "MAIN_SIZE", "STRICT", "含参照物的平视构图", 1, 50));
        return list;
    }

    private static DpModuleDefinition definition(String code, String name, String screenType, String lock,
                                                 String shot, int minScreens, int sortNo) {
        DpModuleDefinition d = new DpModuleDefinition();
        d.setDeliveryType("ECOM_DETAIL");
        d.setModuleCode(code);
        d.setModuleName(name);
        d.setScreenType(screenType);
        d.setProductLockLevel(lock);
        d.setShot(shot);
        d.setRequired("1");
        d.setMinScreens(minScreens);
        d.setMaxScreens(minScreens);
        d.setDefaultSelected("1");
        d.setDefaultSortNo(sortNo);
        d.setEnabled("0");
        return d;
    }

    /**
     * 造一张"假表"：insert 会真的进表，selectList 会按 sortNo 排序。
     *
     * <p>为什么必须这么写：Mockito 的 stub 不会执行 SQL——**不会插、也不会排序**。
     * 第一版就因此两处误报：一次是"初始化后查不到刚插的行"（返回 null），
     * 一次是顺序没排（[BRAND, HERO, HERO]）。真实库里这两件事都由数据库做，
     * 假表要把它们补上，否则测的就不是我们要的行为。</p>
     *
     * @param table 假表（调用方传入，便于断言）
     */
    private void stubProjectModuleTable(List<DpProjectModule> table) {
        when(projectModuleMapper.selectList(any())).thenAnswer(inv -> {
            List<DpProjectModule> rows = new ArrayList<>(table);
            rows.sort((a, b) -> Integer.compare(
                a.getSortNo() == null ? 0 : a.getSortNo(),
                b.getSortNo() == null ? 0 : b.getSortNo()));
            return rows;
        });
        when(projectModuleMapper.insert(any(DpProjectModule.class))).thenAnswer(inv -> {
            table.add(inv.getArgument(0));
            return 1;
        });
    }

    @Test
    @DisplayName("等价性：ECOM_DETAIL 的 6 个模块 → 与契约文件逐屏一致（类型/展示名/保真/取景/顺序）")
    void ecomModulesReproduceContractSkeleton() {
        CreativeScreenSkeleton contract = CreativeScreenSkeleton.load(null);

        List<DpModuleDefinition> defaults = ecomDefaults();
        when(scenarioConfigService.getScenario("ECOM_DETAIL")).thenReturn(null);
        when(definitionMapper.selectList(any())).thenAnswer(inv -> {
            // 第一次调用是"取默认骨架"，之后的 definitionOf 查询按模块编码过滤——这里都返回同一批
            return defaults;
        });
        List<DpProjectModule> table = new ArrayList<>();
        stubProjectModuleTable(table);

        CreativeScreenSkeleton fromModules = service.skeletonOf(7L, "ECOM_DETAIL");
        assertEquals(6, table.size(), "初始化应写入 6 个模块行");

        assertEquals(contract.size(), fromModules.size(), "屏数必须一致");
        for (int i = 0; i < contract.size(); i++) {
            CreativeScreenSkeleton.ScreenSpec want = contract.screens().get(i);
            CreativeScreenSkeleton.ScreenSpec got = fromModules.screens().get(i);
            assertEquals(want.type(), got.type(), "第 " + (i + 1) + " 屏类型");
            assertEquals(want.label(), got.label(), "第 " + (i + 1) + " 屏展示名");
            assertEquals(want.productLockLevel(), got.productLockLevel(), "第 " + (i + 1) + " 屏保真等级");
            assertEquals(want.shot(), got.shot(), "第 " + (i + 1) + " 屏取景");
        }
        // 取景里的 {ratio} 占位符也要还在（派生提示词时会代入基因）
        assertEquals("环境全景，产品占画面 {ratio}", fromModules.screens().get(3).shot());
        // 卖点两屏的展示名来自"模块名 + 中文序号"
        assertEquals("卖点一", fromModules.screens().get(1).label());
        assertEquals("卖点二", fromModules.screens().get(2).label());
        // 类型短名（列表展示用）也要能映射
        assertEquals("主图", fromModules.descOf("HERO"));
        assertEquals("卖点", fromModules.descOf("SELLING_POINT"));
    }

    @Test
    @DisplayName("主图（MAIN_IMAGE）出的是另一套屏：5 屏、MAIN_* 类型")
    void mainImageModulesProduceDifferentScreens() {
        List<DpModuleDefinition> defaults = mainImageDefaults();
        for (DpModuleDefinition d : defaults) {
            d.setDeliveryType("MAIN_IMAGE");
        }
        when(scenarioConfigService.getScenario("MAIN_IMAGE")).thenReturn(null);
        when(definitionMapper.selectList(any())).thenReturn(defaults);
        List<DpProjectModule> table = new ArrayList<>();
        stubProjectModuleTable(table);

        CreativeScreenSkeleton skeleton = service.skeletonOf(8L, "MAIN_IMAGE");

        assertEquals(5, skeleton.size(), "主图 5 屏");
        assertEquals(List.of("MAIN_WHITE_BG", "MAIN_SELLING_POINT", "MAIN_SCENE", "MAIN_DETAIL", "MAIN_SIZE"),
            skeleton.screens().stream().map(CreativeScreenSkeleton.ScreenSpec::type).toList());
        assertEquals("白底主图", skeleton.screens().get(0).label());
        assertEquals(CreativeScreenSkeleton.LEVEL_STRICT, skeleton.screens().get(0).productLockLevel());
        // 与详情页骨架明显不同：屏数与类型都不一样（这正是"按交付类型匹配分镜"的证据）
        CreativeScreenSkeleton contract = CreativeScreenSkeleton.load(null);
        assertEquals(7, contract.size());
        assertEquals("HERO", contract.screens().get(0).type());
    }

    @Test
    @DisplayName("项目已有模块计划时不覆盖（人工调整优先），并按 sortNo 展开成屏")
    void existingPlanWinsAndExpandsBySortNo() {
        List<DpProjectModule> table = new ArrayList<>();
        table.add(projectModule("BRAND_END", "品牌收尾", "BRAND", 1, 30));
        table.add(projectModule("HERO", "主图", "HERO", 2, 10));   // 人为把主图改成 2 屏
        stubProjectModuleTable(table);
        when(definitionMapper.selectList(any())).thenReturn(ecomDefaults());

        CreativeScreenSkeleton skeleton = service.skeletonOf(9L, "ECOM_DETAIL");

        assertEquals(3, skeleton.size(), "2 + 1 = 3 屏");
        assertEquals(List.of("HERO", "HERO", "BRAND"),
            skeleton.screens().stream().map(CreativeScreenSkeleton.ScreenSpec::type).toList());
        assertEquals("主图一", skeleton.screens().get(0).label());
        assertEquals("主图二", skeleton.screens().get(1).label());
    }

    @Test
    @DisplayName("没有默认骨架且项目没有计划 → 返回 null（调用方回落契约文件，不静默变空）")
    void noDefaultNoPlanReturnsNull() {
        when(scenarioConfigService.getScenario("UNKNOWN_TYPE")).thenReturn(null);
        when(definitionMapper.selectList(any())).thenReturn(new ArrayList<>());
        List<DpProjectModule> table = new ArrayList<>();
        stubProjectModuleTable(table);

        assertNull(service.skeletonOf(10L, "UNKNOWN_TYPE"));
        assertEquals(0, table.size(), "没有默认骨架时不该写入任何模块行");
    }

    private static DpProjectModule projectModule(String code, String name, String screenType,
                                                 int screenCount, int sortNo) {
        DpProjectModule m = new DpProjectModule();
        m.setTaskId(9L);
        m.setModuleCode(code);
        m.setModuleName(name);
        m.setScreenType(screenType);
        m.setScreenCount(screenCount);
        m.setSortNo(sortNo);
        m.setStatus("PLANNED");
        m.setSource("DEFAULT");
        return m;
    }
}
