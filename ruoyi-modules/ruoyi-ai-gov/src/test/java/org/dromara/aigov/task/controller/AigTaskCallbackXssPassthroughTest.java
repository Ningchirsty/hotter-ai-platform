package org.dromara.aigov.task.controller;

import org.dromara.aigov.task.domain.bo.AigTaskCallbackBo;
import org.dromara.aigov.task.domain.vo.AigCallbackVo;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.web.config.properties.XssProperties;
import org.dromara.common.web.filter.XssFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回调请求体必须<b>原样</b>到达控制器——用真实的 {@link XssFilter} 验证，而不是靠读配置推断。
 *
 * <p><b>为什么这条必须用真过滤器测</b>：XSS 过滤器注册在 {@code /*} 上，对
 * {@code Content-Type: application/json} 的请求会执行 {@code HtmlUtil.cleanHtmlTag(json).trim()}
 * ——<b>删标签、去首尾空白</b>。而回调的签名是对原始字节算的 HMAC：
 * 字节一变，签名必然对不上，现场却表现为「密钥配错/算法不对」，能把人带偏很久。
 * 读配置能证明「写了排除」，证明不了「写了就真的不洗」；这条测试把两者都钉住：
 * 排除时逐字节相等，<b>不排除时确实被改写</b>（负例是这条测试的价值所在——
 * 没有它，测试在实现被换掉之后仍可能"看起来是绿的"）。</p>
 *
 * <p><b>一处必须显式写的细节</b>：{@code XssFilter} 的排除判断用的是
 * {@code request.getServletPath()}，而 standalone 的 MockMvc 不会自动设置 servletPath
 * （真实容器里它就是请求路径）。不显式设置的话，排除永远匹配不上 →
 * 被测路径反而"看起来需要排除"，把一条本该绿的用例弄红。这类"测试装置的差异"
 * 正是让守卫测试说谎的来源，所以这里写死并留了注释。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskCallbackXssPassthroughTest {

    /**
     * 刻意带上首尾空白与 HTML 标签：这两种正是 {@code cleanHtmlTag(...).trim()} 会改动的地方。
     */
    private static final String RAW = "\n  {\"eventId\":\"evt-1\",\"providerJobId\":\"job-9\","
        + "\"toStatus\":\"RUNNING\",\"detail\":\"<b>渲染完成</b>\"}  \n";

    private final IAigTaskService taskService = mock(IAigTaskService.class);
    private final AigTaskCallbackController controller =
        new AigTaskCallbackController(taskService, JsonMapper.builder().build());

    private MockMvc mvc(boolean excludeCallbackPath) throws Exception {
        XssProperties properties = new XssProperties();
        properties.setEnabled(true);
        properties.setExcludeUrls(excludeCallbackPath
            ? List.of("/aigov/task/callback") : List.of());
        XssFilter filter = new XssFilter(properties);
        // 必须显式 init：排除清单是在 init(FilterConfig) 里装载的，而 MockMvc 手工 addFilters
        // 不会走容器生命周期。漏掉这一句时排除清单恒为空——于是"已排除"的用例会红、
        // 而真正该验的东西（排除是否生效）根本没被验到
        filter.init(null);
        return MockMvcBuilders.standaloneSetup(controller)
            .addFilters(filter)
            .build();
    }

    private String capturedRawPayload() {
        ArgumentCaptor<AigTaskCallbackBo> captor = ArgumentCaptor.forClass(AigTaskCallbackBo.class);
        verify(taskService).handleCallback(captor.capture());
        return captor.getValue().getRawPayload();
    }

    @Test
    @DisplayName("★ 排除路径后，请求体逐字节到达控制器（签名才不会莫名其妙对不上）")
    void excludedPathReceivesTheBodyUntouched() throws Exception {
        AigCallbackVo vo = new AigCallbackVo();
        vo.setProcessResult("ACCEPTED");
        when(taskService.handleCallback(any())).thenReturn(vo);

        mvc(true).perform(post("/aigov/task/callback")
                .servletPath("/aigov/task/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .header(AigTaskCallbackController.HEADER_PROVIDER, "bluocto")
                .header(AigTaskCallbackController.HEADER_SIGNATURE, "sig")
                .content(RAW))
            .andExpect(status().isOk());

        assertEquals(RAW, capturedRawPayload(),
            "请求体被改过：验签算的是原始字节，改一个空格就等于签名失效");
    }

    @Test
    @DisplayName("★ 负例（说明这条配置为什么必需）：不排除时请求体确实被 XSS 过滤器改写")
    void withoutExclusionTheBodyIsRewritten() throws Exception {
        AigCallbackVo vo = new AigCallbackVo();
        vo.setProcessResult("ACCEPTED");
        when(taskService.handleCallback(any())).thenReturn(vo);

        mvc(false).perform(post("/aigov/task/callback")
                .servletPath("/aigov/task/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .header(AigTaskCallbackController.HEADER_PROVIDER, "bluocto")
                .header(AigTaskCallbackController.HEADER_SIGNATURE, "sig")
                .content(RAW))
            .andExpect(status().isOk());

        String received = capturedRawPayload();
        assertNotEquals(RAW, received,
            "如果这里相等，说明过滤器没有改写请求体——那么 xss.excludeUrls 就不是必需的，"
                + "本测试的前提需要重新核对（别让一条永远通过的测试冒充守卫）");
        assertEquals(true, received.contains("渲染完成"), "内容还在，但标签/空白被清掉了：" + received);
    }

}
