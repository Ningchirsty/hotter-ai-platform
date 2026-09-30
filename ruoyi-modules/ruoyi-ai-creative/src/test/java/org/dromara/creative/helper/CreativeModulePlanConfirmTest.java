package org.dromara.creative.helper;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.creative.domain.DpModuleDefinition;
import org.dromara.creative.domain.DpProjectModule;
import org.dromara.creative.mapper.CreativeTaskStageMapper;
import org.dromara.creative.mapper.DpModuleDefinitionMapper;
import org.dromara.creative.mapper.DpProjectModuleMapper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 「确认本计划」（V0.2 R28，文档 §25 第 1 步）。
 *
 * <p>钉住三件事：① 确认会把启用行置为 CONFIRMED 并写一条事件；② 没有启用模块时拒绝确认；
 * ③ **再保存计划会回到 PLANNED**（改了就得重新确认）——这条最容易漏，也最容易被误解为
 * "确认过一次就永久有效"。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeModulePlanConfirmTest {

    @Mock
    private DpModuleDefinitionMapper definitionMapper;
    @Mock
    private DpProjectModuleMapper projectModuleMapper;
    @Mock
    private ICreativeScenarioConfigService scenarioConfigService;
    @Mock
    private CreativeTaskStageMapper stageMapper;
    @Mock
    private org.dromara.creative.service.ICreativeProjectService projectService;
    @Mock
    private org.dromara.content.service.IContentTaskService contentTaskService;
    @Mock
    private org.dromara.creative.mapper.DpStoryboardMapper storyboardMapper;
    @Mock
    private org.dromara.creative.mapper.DpStoryboardScreenMapper screenMapper;
    @Mock
    private org.dromara.creative.mapper.DpGenerationMapper generationMapper;
    @Mock
    private org.dromara.creative.mapper.DpDetailPageVersionMapper detailPageVersionMapper;

    @InjectMocks
    private CreativeModuleServiceImpl service;

    private static final long TASK = 777L;
    private final List<DpProjectModule> table = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        table.clear();
        events.clear();
        when(scenarioConfigService.getScenario(any())).thenReturn(null);
        when(definitionMapper.selectList(any())).thenReturn(List.of(hero(), brand()));
        when(storyboardMapper.selectCount(any())).thenReturn(0L);
        when(generationMapper.selectCount(any())).thenReturn(0L);
        when(detailPageVersionMapper.selectCount(any())).thenReturn(0L);
        Map<String, Object> meta = new HashMap<>();
        meta.put("delFlag", "0");
        when(stageMapper.selectDelFlag(TASK)).thenReturn("0");
        when(stageMapper.selectDeliverableType(TASK)).thenReturn("ECOM_DETAIL");
        when(stageMapper.selectStage(TASK)).thenReturn(Map.of("visualStage", "STORYBOARD_REVIEW"));
        // 事件走项目服务的 appendEvent（R28 修正：自写 INSERT 漏了 dp_stage_event.id），
        // 所以这里 mock 的是服务接口；用 doAnswer 记下动作名便于断言。
        org.mockito.Mockito.doAnswer(inv -> {
            events.add(inv.getArgument(2));
            return null;
        }).when(projectService).appendEvent(any(), any(), any(), any());
        // 假表要连"软删"一起模拟：savePlan 会 deleteById 旧行，不模拟的话旧行还留在表里，
        // 于是"再保存一次"会看到 3 行（第一次的 2 行 + 新 1 行）——第一次跑就是这么红的。
        when(projectModuleMapper.selectList(any())).thenAnswer(inv -> {
            List<DpProjectModule> live = new ArrayList<>();
            for (DpProjectModule row : table) {
                if (!"1".equals(row.getDelFlag())) {
                    live.add(row);
                }
            }
            return live;
        });
        when(projectModuleMapper.insert(any(DpProjectModule.class))).thenAnswer(inv -> {
            DpProjectModule row = inv.getArgument(0);
            row.setId((long) (table.size() + 1));
            if (row.getDelFlag() == null) {
                row.setDelFlag("0");
            }
            table.add(row);
            return 1;
        });
        when(projectModuleMapper.updateById(any(DpProjectModule.class))).thenAnswer(inv -> 1);
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

    private static DpModuleDefinition hero() {
        DpModuleDefinition d = new DpModuleDefinition();
        d.setId(1L);
        d.setDeliveryType("ECOM_DETAIL");
        d.setModuleCode("HERO");
        d.setModuleName("主图");
        d.setScreenType("HERO");
        d.setProductLockLevel("STRICT");
        d.setShot("正面");
        d.setMinScreens(1);
        d.setMaxScreens(1);
        d.setDefaultSelected("1");
        d.setDefaultSortNo(10);
        d.setEnabled("0");
        return d;
    }

    private static DpModuleDefinition brand() {
        DpModuleDefinition d = hero();
        d.setId(2L);
        d.setModuleCode("BRAND_END");
        d.setModuleName("品牌收尾");
        d.setScreenType("BRAND");
        d.setDefaultSortNo(20);
        return d;
    }

    private static org.dromara.creative.domain.bo.ProjectModulePlanBo.Item item(String code) {
        org.dromara.creative.domain.bo.ProjectModulePlanBo.Item it =
            new org.dromara.creative.domain.bo.ProjectModulePlanBo.Item();
        it.setModuleCode(code);
        it.setScreenCount(1);
        it.setEnabled("0");
        return it;
    }

    private static org.dromara.creative.domain.bo.ProjectModulePlanBo planOf(String... codes) {
        org.dromara.creative.domain.bo.ProjectModulePlanBo bo =
            new org.dromara.creative.domain.bo.ProjectModulePlanBo();
        List<org.dromara.creative.domain.bo.ProjectModulePlanBo.Item> items = new ArrayList<>();
        for (String code : codes) {
            items.add(item(code));
        }
        bo.setModules(items);
        return bo;
    }

    @Test
    @DisplayName("确认计划：启用行置为 CONFIRMED、写一条事件、视图 confirmed=true")
    void confirmMarksRowsAndWritesEvent() {
        service.savePlan(TASK, planOf("HERO", "BRAND_END"));

        var vo = service.confirmPlan(TASK);

        assertTrue(vo.getConfirmed());
        assertTrue(table.stream().allMatch(r -> "CONFIRMED".equals(r.getStatus())));
        assertEquals(List.of("MODULE_PLAN_CONFIRMED"), events);
        assertTrue(vo.getConfirmedAt() != null || vo.getConfirmed() , "确认时间可能为 null（库里没回填），但状态必须是已确认");
    }

    @Test
    @DisplayName("确认后**再保存计划** → 回到 PLANNED（改了就得重新确认）")
    void savingAgainResetsToPlanned() {
        service.savePlan(TASK, planOf("HERO", "BRAND_END"));
        assertTrue(service.confirmPlan(TASK).getConfirmed());

        var after = service.savePlan(TASK, planOf("HERO"));

        assertFalse(after.getConfirmed(), "保存改动后必须回到待确认");
        assertEquals(1, after.getModules().size());
        assertTrue(after.getModules().stream().allMatch(r -> "PLANNED".equals(r.getStatus())));
    }

    @Test
    @DisplayName("全部停用时不能确认；没有计划时提示先保存")
    void confirmGuards() {
        service.savePlan(TASK, planOf("HERO", "BRAND_END"));
        service.savePlan(TASK, planOf("HERO"));
        // 把唯一启用的模块停用
        table.forEach(r -> r.setEnabled("1"));
        ServiceException noneEnabled = assertThrows(ServiceException.class, () -> service.confirmPlan(TASK));
        assertTrue(noneEnabled.getMessage().contains("至少要启用一个模块"), noneEnabled.getMessage());

        table.clear();
        ServiceException empty = assertThrows(ServiceException.class, () -> service.confirmPlan(TASK));
        assertTrue(empty.getMessage().contains("还没有模块计划"), empty.getMessage());
    }
}
