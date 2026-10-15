package org.dromara.aigov.workspace.launch.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.workspace.launch.domain.AigLaunchTemplate;
import org.dromara.aigov.workspace.launch.domain.bo.AigLaunchRequestBo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchTemplateVo;
import org.dromara.aigov.workspace.launch.mapper.AigLaunchTemplateMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 启动任务模板测试（设计 D11）。
 *
 * <p>要钉死的四条：</p>
 * <ol>
 *     <li><b>精确优先、默认兜底、都没有不套用</b>——匹配口径写错会让张冠李戴的模板生效；</li>
 *     <li><b>数据等级是服务端结论</b>：请求里传了更宽的值也被覆盖（不允许放宽）；</li>
 *     <li><b>快照由业务输入组装</b>（员工不写原始 JSON）；</li>
 *     <li><b>无模板时一字不改</b>（向后兼容）。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigLaunchTemplateServiceImplTest {

    private AigLaunchTemplateMapper templateMapper;
    private AigLaunchTemplateServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigLaunchTemplate.class);
    }

    @BeforeEach
    void setUp() {
        templateMapper = mock(AigLaunchTemplateMapper.class);
        service = new AigLaunchTemplateServiceImpl(templateMapper, JsonMapper.builder().build());
    }

    private static AigLaunchTemplate template(String actionCode, String taskType, String dataLevel, String snapshot) {
        AigLaunchTemplate t = new AigLaunchTemplate();
        t.setTemplateCode("tpl-" + (actionCode == null || actionCode.isEmpty() ? "default" : actionCode));
        t.setRoleCode("ROLE_A");
        t.setActionCode(actionCode);
        t.setTaskType(taskType);
        t.setProjectType("CREATIVE");
        t.setDataLevel(dataLevel);
        t.setBusinessFields("sku,market");
        t.setSnapshotFromContext(snapshot);
        t.setEnabled("Y");
        t.setStatus("0");
        return t;
    }

    private static AigLaunchRequestBo request() {
        AigLaunchRequestBo bo = new AigLaunchRequestBo();
        bo.setRoleCode("ROLE_A");
        bo.setActionCode("CARD_X");
        bo.setIdempotencyKey("key-1");
        bo.setContext(Map.of("sku", "S-1", "market", "日本"));
        return bo;
    }

    @Test
    @DisplayName("★ 匹配口径：精确 action 优先于岗位默认；都没有则返回 null")
    void pickPrefersExactThenDefault() {
        AigLaunchTemplate exact = template("CARD_X", "TEXT_GENERATION", "INTERNAL", "Y");
        AigLaunchTemplate fallback = template("", "IMAGE_GENERATION", "STRICT", "Y");

        assertEquals(exact, AigLaunchTemplateServiceImpl.pick(List.of(fallback, exact), "CARD_X"));
        assertEquals(fallback, AigLaunchTemplateServiceImpl.pick(List.of(fallback, exact), "OTHER_CARD"));
        assertNull(AigLaunchTemplateServiceImpl.pick(List.of(exact), "OTHER_CARD"));
        assertNull(AigLaunchTemplateServiceImpl.pick(List.of(), "CARD_X"));
        assertNull(AigLaunchTemplateServiceImpl.pick(null, "CARD_X"));
    }

    @Test
    @DisplayName("★ 套用模板：执行字段由服务端推导，数据等级请求值被覆盖（不允许放宽）")
    void applyOverridesExecutionFieldsIncludingDataLevel() {
        when(templateMapper.selectList(any()))
            .thenReturn(List.of(template("CARD_X", "TEXT_GENERATION", "STRICT", "Y")));
        AigLaunchRequestBo bo = request();
        // 员工本不该填这些；即便填了更宽的数据等级，也不生效
        bo.setDataLevel("PUBLIC");

        assertTrue(service.apply(bo));

        assertEquals("TEXT_GENERATION", bo.getTaskType());
        assertEquals("CREATIVE", bo.getProjectType());
        assertEquals("STRICT", bo.getDataLevel(), "数据处理范围是服务端结论，不能被请求放宽");
        assertTrue(bo.getSnapshotJson().contains("S-1"), "输入快照应由业务输入组装：" + bo.getSnapshotJson());
    }

    @Test
    @DisplayName("无模板：一字不改（向后兼容），返回 false")
    void noTemplateLeavesRequestUntouched() {
        when(templateMapper.selectList(any())).thenReturn(List.of());
        AigLaunchRequestBo bo = request();
        bo.setTaskType("TEXT_GENERATION");
        bo.setDataLevel("INTERNAL");

        assertFalse(service.apply(bo));

        assertEquals("TEXT_GENERATION", bo.getTaskType());
        assertEquals("INTERNAL", bo.getDataLevel());
        assertNull(bo.getSnapshotJson(), "没有模板时不应替调用方造快照");
    }

    @Test
    @DisplayName("模板声明 snapshot_from_context=N 时不自动组装快照")
    void snapshotNotBuiltWhenTemplateSaysNo() {
        when(templateMapper.selectList(any()))
            .thenReturn(List.of(template("CARD_X", "TEXT_GENERATION", "INTERNAL", "N")));
        AigLaunchRequestBo bo = request();

        service.apply(bo);

        assertNull(bo.getSnapshotJson());
    }

    @Test
    @DisplayName("describe：业务字段按逗号拆开，供前端只渲染业务表单")
    void describeExposesBusinessFields() {
        when(templateMapper.selectList(any()))
            .thenReturn(List.of(template("CARD_X", "TEXT_GENERATION", "INTERNAL", "Y")));

        AigLaunchTemplateVo vo = service.describe("ROLE_A", "CARD_X");

        assertEquals("tpl-CARD_X", vo.getTemplateCode());
        assertEquals(List.of("sku", "market"), vo.getBusinessFields());
        assertEquals(Boolean.TRUE, vo.getSnapshotFromContext());
        assertEquals("TEXT_GENERATION", vo.getTaskType());
    }

    @Test
    @DisplayName("describe：无模板返回 null（前端据此回退旧表单）")
    void describeReturnsNullWithoutTemplate() {
        when(templateMapper.selectList(any())).thenReturn(List.of());
        assertNull(service.describe("ROLE_A", "CARD_X"));
    }

}
