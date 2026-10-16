package org.dromara.aigov.controller;

import org.dromara.aigov.service.IAigCapabilityOverviewService;
import org.dromara.aigov.service.IAigCapabilityReferenceService;
import org.dromara.aigov.service.IAigCapabilityService;
import org.dromara.aigov.service.IAigModelBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 新增两个入口的路由烟测（能力概览聚合 / 候选编辑）。
 *
 * <p><b>为什么必须真的走一遍路由</b>：注解扫描看不出"路径拼接错、参数绑定失败、
 * 或返回值序列化不了"。这两个入口一个是"卡片与看板的唯一数据源"，一个是"候选改错只能删了重建"
 * 的解药 —— 路由坏了的表现是 404/400，而页面只会显示空白。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigCapabilityRoutesTest {

    private IAigCapabilityService capabilityService;
    private IAigCapabilityOverviewService overviewService;
    private IAigCapabilityReferenceService referenceService;
    private IAigModelBindingService modelBindingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        capabilityService = mock(IAigCapabilityService.class);
        overviewService = mock(IAigCapabilityOverviewService.class);
        referenceService = mock(IAigCapabilityReferenceService.class);
        modelBindingService = mock(IAigModelBindingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
            new AigCapabilityController(capabilityService, overviewService, referenceService),
            new AigModelBindingController(modelBindingService)).build();
    }

    @Test
    @DisplayName("GET /aigov/capability/overview：聚合清单可达且把筛选项透传下去")
    void overviewRouteIsWired() throws Exception {
        mockMvc.perform(get("/aigov/capability/overview")
                .param("capabilityName", "图片")
                .param("issueCode", "NO_MODEL_CANDIDATE")
                .param("candidateState", "NO"))
            .andExpect(status().isOk());

        verify(overviewService).overview(any(), any(), eq("NO_MODEL_CANDIDATE"), eq("NO"));
    }

    @Test
    @DisplayName("★ GET /aigov/capability/references 不被数字型详情路由抢走（字面路径优先）")
    void referencesRouteIsNotShadowedByDetailRoute() throws Exception {
        mockMvc.perform(get("/aigov/capability/references").param("capabilityCode", "image_generation"))
            .andExpect(status().isOk());

        verify(referenceService).listReferences("image_generation");
    }

    @Test
    @DisplayName("PUT /aigov/binding/{bindId}：候选编辑可达（此前只有新增与删除）")
    void bindingUpdateRouteIsWired() throws Exception {
        mockMvc.perform(put("/aigov/binding/5001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usageType\":\"FALLBACK\",\"priority\":20}"))
            .andExpect(status().isOk());

        verify(modelBindingService).update(eq(5001L), any());
    }

    @Test
    @DisplayName("非数字 bindId 不该命中编辑方法（参数转换失败 → 4xx，而不是打到别的接口）")
    void bindingUpdateRejectsNonNumericId() throws Exception {
        mockMvc.perform(put("/aigov/binding/abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().is4xxClientError());
    }

}
