package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aigov.agent.domain.vo.AigSandboxArtifactDownloadVo;
import org.dromara.aigov.agent.domain.vo.AigSandboxArtifactManifestVo;
import org.dromara.aigov.agent.service.IAigSandboxArtifactService;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 沙箱产物接口测试（ADR-016）。
 *
 * <p><b>最该被钉住的是下载响应头</b>：产物由不可信代码在沙箱里产生，可以是 HTML/SVG/JS。
 * 只要响应里回显了上传方声明的 {@code text/html}、或漏了 {@code nosniff}，
 * 这条通道就变成"在平台域下执行外部代码"的现成入口——而它不会报错，只会某天被利用。
 * 所以这里用真实 MockMvc 断言三条硬要求。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSandboxArtifactControllerTest {

    private static final long RUN_ID = 7701L;

    private final IAigSandboxArtifactService service = mock(IAigSandboxArtifactService.class);

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new AigSandboxArtifactController(service)).build();
    }

    @Test
    @DisplayName("★ 下载：三条硬要求（octet-stream + attachment + nosniff），且绝不回显上传方声明的类型")
    void downloadSetsHardenedHeaders() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        when(service.download(eq(RUN_ID), eq("out/page.html"), any(), any())).thenReturn(
            new IAigSandboxArtifactService.AigSandboxArtifactFile(html, "page.html", "sha-1"));

        mockMvc().perform(get("/aigov/sandbox/run/" + RUN_ID + "/artifact")
                .param("path", "out/page.html"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/octet-stream"))
            .andExpect(header().string("Content-Type", not(containsString("text/html"))))
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(header().string("Content-Disposition", containsString("filename=\"page.html\"")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(content().bytes(html));

        // 下载人一律取自登录态（这里没有登录态 → null），来源 IP 由 ServletUtils 取
        verify(service).download(eq(RUN_ID), eq("out/page.html"), isNull(), any());
    }

    @Test
    @DisplayName("★ 下载：文件名里的引号与分号被换掉（响应头注入），并给出 RFC 5987 可读名")
    void downloadSanitizesFileName() throws Exception {
        when(service.download(any(), any(), any(), any())).thenReturn(
            new IAigSandboxArtifactService.AigSandboxArtifactFile("x".getBytes(StandardCharsets.UTF_8),
                "_x__y.txt", "sha-2"));

        mockMvc().perform(get("/aigov/sandbox/run/" + RUN_ID + "/artifact").param("path", "out/a"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("filename=\"_x__y.txt\"")))
            .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''_x__y.txt")));
    }

    @Test
    @DisplayName("下载留痕：透传运行记录ID与 limit，并回传记录（谁/何时/哪个 IP）")
    void downloadsDelegates() throws Exception {
        AigSandboxArtifactDownloadVo vo = new AigSandboxArtifactDownloadVo();
        vo.setDownloadId(6601L);
        vo.setPath("out/a.txt");
        vo.setDownloadedBy(200L);
        vo.setClientIp("10.12.34.56");
        when(service.downloads(RUN_ID, 0)).thenReturn(List.of(vo));

        mockMvc().perform(get("/aigov/sandbox/run/" + RUN_ID + "/artifact-downloads"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("10.12.34.56")));
        verify(service).downloads(RUN_ID, 0);

        // limit 显式传入时按值透传
        when(service.downloads(RUN_ID, 5)).thenReturn(List.of());
        mockMvc().perform(get("/aigov/sandbox/run/" + RUN_ID + "/artifact-downloads").param("limit", "5"))
            .andExpect(status().isOk());
        verify(service).downloads(RUN_ID, 5);
    }

    @Test
    @DisplayName("上传：path 与文件透传给服务层，操作人取自登录态（单测里登录态为空，故不校验 id）")
    void uploadPassesPathAndBytes() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain",
            "abc".getBytes(StandardCharsets.UTF_8));

        mockMvc().perform(multipart("/aigov/sandbox/run/" + RUN_ID + "/artifact")
                .file(file)
                .param("path", "out/a.txt")
                .param("contentType", "text/plain"))
            .andExpect(status().isOk());

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> contentType = ArgumentCaptor.forClass(String.class);
        verify(service).upload(eq(RUN_ID), eq("out/a.txt"), body.capture(), contentType.capture(), any());
        assertEquals("abc", new String(body.getValue(), StandardCharsets.UTF_8));
        assertEquals("text/plain", contentType.getValue());
    }

    @Test
    @DisplayName("清单：透传运行记录ID并回传清单项")
    void listDelegates() throws Exception {
        AigSandboxArtifactManifestVo vo = new AigSandboxArtifactManifestVo();
        vo.setPath("out/a.txt");
        vo.setUploaded(true);
        when(service.list(RUN_ID)).thenReturn(List.of(vo));

        mockMvc().perform(get("/aigov/sandbox/run/" + RUN_ID + "/artifacts"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("out/a.txt")));
        verify(service).list(RUN_ID);
    }

    @Test
    @DisplayName("★ 权限：登记用 aig:sandbox:record（+防重复提交），列表/下载用 aig:sandbox:list；不新开权限点")
    void permissionsFollowTheExistingTwoTiers() throws Exception {
        assertPermission("upload", new Class<?>[]{Long.class, String.class, org.springframework.web.multipart.MultipartFile.class, String.class},
            AigConstants.PERM_SANDBOX_RECORD);
        assertPermission("list", new Class<?>[]{Long.class}, AigConstants.PERM_SANDBOX_LIST);
        assertPermission("download", new Class<?>[]{Long.class, String.class,
            jakarta.servlet.http.HttpServletRequest.class}, AigConstants.PERM_SANDBOX_LIST);
        assertPermission("downloads", new Class<?>[]{Long.class, Integer.class},
            AigConstants.PERM_SANDBOX_LIST);

        Method upload = AigSandboxArtifactController.class.getMethod("upload", Long.class, String.class,
            org.springframework.web.multipart.MultipartFile.class, String.class);
        assertNotNull(upload.getAnnotation(RepeatSubmit.class),
            "登记是追加型账本，双击应得到可读提示而不是两条记录");

        assertTrue(!AigConstants.PERM_SANDBOX_RECORD.equals(AigConstants.PERM_SANDBOX_LIST),
            "登记决定门槛能不能过，查看只是解释「为什么还不满足」——两者不该共用一个权限串");
    }

    private static void assertPermission(String method, Class<?>[] params, String expected)
        throws Exception {
        Method m = AigSandboxArtifactController.class.getMethod(method, params);
        SaCheckPermission annotation = m.getAnnotation(SaCheckPermission.class);
        assertNotNull(annotation, method + " 没有权限注解 = 任何登录用户都能调");
        Set<String> values = Set.copyOf(Arrays.asList(annotation.value()));
        assertTrue(values.contains(expected), method + " 的权限点应为 " + expected + "，实际=" + values);
    }

}
