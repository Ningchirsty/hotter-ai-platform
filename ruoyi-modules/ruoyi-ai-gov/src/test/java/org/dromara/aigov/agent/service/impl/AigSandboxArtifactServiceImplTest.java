package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.agent.config.AigSandboxArtifactProperties;
import org.dromara.aigov.agent.domain.AigSandboxArtifact;
import org.dromara.aigov.agent.domain.AigSandboxRun;
import org.dromara.aigov.agent.domain.vo.AigSandboxArtifactManifestVo;
import org.dromara.aigov.agent.helper.AigSandboxArtifactManifest;
import org.dromara.aigov.agent.helper.IAigSandboxArtifactStore;
import org.dromara.aigov.agent.mapper.AigSandboxArtifactMapper;
import org.dromara.aigov.agent.mapper.AigSandboxRunMapper;
import org.dromara.aigov.agent.service.IAigSandboxArtifactService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 沙箱产物服务测试（ADR-016）。
 *
 * <p><b>要钉死的是"只信账本"这条不变式</b>：路径不在清单里、字节数不符、摘要不符——
 * 三种都必须<b>拒绝且不落对象存储</b>（先是校验、后是落盘）。落到对象存储里再发现不对，
 * 桶里就多了一份"账本上没有、也不知道属于谁"的字节。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSandboxArtifactServiceImplTest {

    private static final long RUN_ID = 7701L;
    private static final long ARTIFACT_ID = 9901L;
    private static final long ACTOR = 200L;

    private static final byte[] ABC = "abc".getBytes(StandardCharsets.UTF_8);

    private static final String ABC_SHA = AigSandboxArtifactManifest.sha256(ABC);

    private AigSandboxArtifactMapper artifactMapper;
    private AigSandboxRunMapper sandboxRunMapper;
    private IAigSandboxArtifactStore artifactStore;
    private AigSandboxArtifactProperties properties;
    private AigSandboxArtifactServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigSandboxArtifact.class);
    }

    @BeforeEach
    void setUp() {
        artifactMapper = mock(AigSandboxArtifactMapper.class);
        sandboxRunMapper = mock(AigSandboxRunMapper.class);
        artifactStore = mock(IAigSandboxArtifactStore.class);
        properties = new AigSandboxArtifactProperties();
        service = new AigSandboxArtifactServiceImpl(artifactMapper, sandboxRunMapper, artifactStore,
            properties, JsonMapper.builder().build());

        when(sandboxRunMapper.selectById(RUN_ID)).thenReturn(run(
            "{\"jobId\":\"JOB-1\",\"image\":\"img:1\",\"exitCode\":0,\"timedOut\":false,\"network\":\"none\","
                + "\"artifacts\":[{\"path\":\"out/a.txt\",\"bytes\":3,\"sha256\":\"" + ABC_SHA + "\"}]}"));
        when(artifactMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(artifactMapper.selectOne(any())).thenReturn(null);
        when(artifactMapper.insert(any(AigSandboxArtifact.class))).thenAnswer(invocation -> {
            invocation.<AigSandboxArtifact>getArgument(0).setArtifactId(ARTIFACT_ID);
            return 1;
        });
        when(artifactStore.keyOf(any(), any())).thenReturn("aig-private/sandbox/" + RUN_ID + "/" + ABC_SHA);
    }

    private static AigSandboxRun run(String resultJson) {
        AigSandboxRun run = new AigSandboxRun();
        run.setSandboxRunId(RUN_ID);
        run.setJobId("JOB-1");
        run.setResultJson(resultJson);
        return run;
    }

    /* ------------------------------------------------------------------ 上传 */

    @Test
    @DisplayName("★ 上传成功：三道校验都过才落对象存储，对象键由服务端按摘要生成")
    void uploadStoresAfterAllChecks() {
        assertEquals(ARTIFACT_ID, service.upload(RUN_ID, "./out/a.txt", ABC, "text/plain", ACTOR));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(artifactStore).put(key.capture(), body.capture());
        assertEquals("aig-private/sandbox/" + RUN_ID + "/" + ABC_SHA, key.getValue(),
            "对象键由服务端按摘要生成，不用上传方的 path");
        assertEquals("abc", new String(body.getValue(), StandardCharsets.UTF_8));

        ArgumentCaptor<AigSandboxArtifact> row = ArgumentCaptor.forClass(AigSandboxArtifact.class);
        verify(artifactMapper).insert(row.capture());
        assertEquals(RUN_ID, row.getValue().getSandboxRunId());
        assertEquals("out/a.txt", row.getValue().getPath(), "路径要归一化（去 ./）");
        assertEquals(3L, row.getValue().getBytes(), "字节数是服务端实算的");
        assertEquals(ABC_SHA, row.getValue().getSha256());
        assertEquals("text/plain", row.getValue().getContentType(), "上传方声明的类型只记录");
        assertEquals(ACTOR, row.getValue().getUploadedBy());
    }

    @Test
    @DisplayName("★ 路径不在账本声明清单里 → 拒绝，且不进对象存储")
    void uploadRejectsUndeclaredPath() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.upload(RUN_ID, "out/evil.txt", ABC, null, ACTOR));

        assertTrue(error.getMessage().contains("没有声明"), error.getMessage());
        assertTrue(error.getMessage().contains("out/a.txt"), "要把可登记的清单列出来：" + error.getMessage());
        verify(artifactStore, never()).put(any(), any());
        verify(artifactMapper, never()).insert(any(AigSandboxArtifact.class));
    }

    @Test
    @DisplayName("★ 字节数不符 → 拒绝（并给出两个数字），且不进对象存储")
    void uploadRejectsWrongSize() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.upload(RUN_ID, "out/a.txt", "ab".getBytes(StandardCharsets.UTF_8), null, ACTOR));

        assertTrue(error.getMessage().contains("声明 3 字节，实际 2 字节"), error.getMessage());
        verify(artifactStore, never()).put(any(), any());
    }

    @Test
    @DisplayName("★ 摘要不符（内容被换过）→ 拒绝，且不进对象存储")
    void uploadRejectsWrongContent() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.upload(RUN_ID, "out/a.txt", "xyz".getBytes(StandardCharsets.UTF_8), null, ACTOR));

        assertTrue(error.getMessage().contains("SHA-256 与 result.json 声明不一致"), error.getMessage());
        assertTrue(error.getMessage().contains(ABC_SHA), "要说清声明值是哪个：" + error.getMessage());
        verify(artifactStore, never()).put(any(), any());
        verify(artifactMapper, never()).insert(any(AigSandboxArtifact.class));
    }

    @Test
    @DisplayName("上传：作业不存在 / 路径非法 / 字节为 null → 都拒绝")
    void uploadRejectsBadInput() {
        assertTrue(assertThrows(ServiceException.class,
            () -> service.upload(999L, "out/a.txt", ABC, null, ACTOR)).getMessage().contains("不存在"));
        assertThrows(ServiceException.class, () -> service.upload(null, "out/a.txt", ABC, null, ACTOR));
        assertTrue(assertThrows(ServiceException.class,
            () -> service.upload(RUN_ID, "../out/a.txt", ABC, null, ACTOR)).getMessage().contains(".."));
        assertTrue(assertThrows(ServiceException.class,
            () -> service.upload(RUN_ID, "out/a.txt", null, null, ACTOR)).getMessage().contains("不能为空"));
        verify(artifactStore, never()).put(any(), any());
    }

    @Test
    @DisplayName("上传：同一产物重复登记 → 拒绝（重复登记会让「存了几份」变成假账）")
    void uploadRejectsDuplicate() {
        AigSandboxArtifact existing = new AigSandboxArtifact();
        existing.setArtifactId(8888L);
        existing.setPath("out/a.txt");
        when(artifactMapper.selectOne(any())).thenReturn(existing);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.upload(RUN_ID, "out/a.txt", ABC, null, ACTOR));

        assertTrue(error.getMessage().contains("已经登记过"), error.getMessage());
        assertTrue(error.getMessage().contains("8888"), error.getMessage());
        verify(artifactStore, never()).put(any(), any());
    }

    @Test
    @DisplayName("★ 三个限额都是拒绝式的：单文件 / 每作业文件数 / 每作业总量")
    void uploadEnforcesLimits() {
        properties.setMaxFileBytes(2);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.upload(RUN_ID, "out/a.txt", ABC, null, ACTOR)).getMessage()
            .contains("超过上限"), "单文件上限（它同时是下载时的内存占用上限）");

        properties.setMaxFileBytes(1024);
        properties.setMaxFilesPerRun(1);
        List<AigSandboxArtifact> one = new ArrayList<>();
        AigSandboxArtifact row = new AigSandboxArtifact();
        row.setBytes(1L);
        one.add(row);
        when(artifactMapper.selectList(any())).thenReturn(one);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.upload(RUN_ID, "out/a.txt", ABC, null, ACTOR)).getMessage()
            .contains("数量已达上限"));

        properties.setMaxFilesPerRun(200);
        properties.setMaxTotalBytesPerRun(3);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.upload(RUN_ID, "out/a.txt", ABC, null, ACTOR)).getMessage()
            .contains("总字节数将超过上限"));

        verify(artifactStore, never()).put(any(), any());
    }

    @Test
    @DisplayName("上传：账本自己坏了（没有 artifacts / 非法 JSON）时给可执行原因，而不是说「没声明」")
    void uploadReportsBrokenManifest() {
        when(sandboxRunMapper.selectById(RUN_ID)).thenReturn(run(
            "{\"jobId\":\"J\",\"image\":\"i\",\"exitCode\":0,\"timedOut\":false,\"network\":\"none\"}"));
        assertTrue(assertThrows(ServiceException.class,
            () -> service.upload(RUN_ID, "out/a.txt", ABC, null, ACTOR)).getMessage()
            .contains("无法校验产物清单"), "账本没有清单是数据问题，不能说成「没声明这个产物」");
    }

    /* ------------------------------------------------------------------ 清单 */

    @Test
    @DisplayName("★ 清单以「声明的产物」为准，并标出哪些已入库（漏传的那个必须看得见）")
    void listIsDrivenByManifest() {
        when(sandboxRunMapper.selectById(RUN_ID)).thenReturn(run(
            "{\"jobId\":\"J\",\"image\":\"i\",\"exitCode\":0,\"timedOut\":false,\"network\":\"none\","
                + "\"artifacts\":[{\"path\":\"out/a.txt\",\"bytes\":3,\"sha256\":\"" + ABC_SHA + "\"},"
                + "{\"path\":\"out/b.bin\",\"bytes\":0,\"sha256\":\"" + ABC_SHA + "\"}]}"));
        AigSandboxArtifact stored = new AigSandboxArtifact();
        stored.setArtifactId(ARTIFACT_ID);
        stored.setPath("out/a.txt");
        stored.setBytes(3L);
        stored.setSha256(ABC_SHA);
        stored.setContentType("text/plain");
        List<AigSandboxArtifact> rows = new ArrayList<>();
        rows.add(stored);
        when(artifactMapper.selectList(any())).thenReturn(rows);

        List<AigSandboxArtifactManifestVo> result = service.list(RUN_ID);

        assertEquals(2, result.size());
        assertEquals("out/a.txt", result.get(0).getPath());
        assertEquals(Boolean.TRUE, result.get(0).getUploaded());
        assertEquals(ARTIFACT_ID, result.get(0).getArtifactId());
        assertEquals("text/plain", result.get(0).getContentType());
        assertEquals(Boolean.FALSE, result.get(1).getUploaded(), "没传的那只也必须出现，且标未入库");
        assertNull(result.get(1).getArtifactId());
        assertEquals(0L, result.get(1).getBytes());
    }

    /* ------------------------------------------------------------------ 下载 */

    @Test
    @DisplayName("下载：字节与账本一致 → 下发（文件名只取最后一段）")
    void downloadServesVerifiedBytes() {
        AigSandboxArtifact row = new AigSandboxArtifact();
        row.setArtifactId(ARTIFACT_ID);
        row.setPath("out/a.txt");
        row.setBytes(3L);
        row.setSha256(ABC_SHA);
        row.setObjectKey("aig-private/sandbox/1/" + ABC_SHA);
        when(artifactMapper.selectOne(any())).thenReturn(row);
        when(artifactStore.get("aig-private/sandbox/1/" + ABC_SHA)).thenReturn(ABC);

        IAigSandboxArtifactService.AigSandboxArtifactFile file = service.download(RUN_ID, "out/a.txt");

        assertEquals("abc", new String(file.content(), StandardCharsets.UTF_8));
        assertEquals("a.txt", file.fileName());
        assertEquals(ABC_SHA, file.sha256());
    }

    @Test
    @DisplayName("★ 下载：还没入库的产物 → 拒绝（账本只有声明，字节要单独交上来）")
    void downloadRejectsNotUploaded() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.download(RUN_ID, "out/a.txt"));

        assertTrue(error.getMessage().contains("还没登记"), error.getMessage());
        verify(artifactStore, never()).get(any());
    }

    @Test
    @DisplayName("★ 下载：取回的字节与账本摘要不符 → 拒绝下发（宁可报错，也不把与证据不符的字节交给人）")
    void downloadRejectsTamperedObject() {
        AigSandboxArtifact row = new AigSandboxArtifact();
        row.setPath("out/a.txt");
        row.setBytes(3L);
        row.setSha256(ABC_SHA);
        row.setObjectKey("K");
        when(artifactMapper.selectOne(any())).thenReturn(row);
        when(artifactStore.get("K")).thenReturn("xyz".getBytes(StandardCharsets.UTF_8));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.download(RUN_ID, "out/a.txt"));

        assertTrue(error.getMessage().contains("拒绝下发"), error.getMessage());
    }

    @Test
    @DisplayName("★ 下载：账本声明与入库记录摘要不一致（账本自相矛盾）→ 先拒绝，连对象存储都不去读")
    void downloadRejectsInconsistentLedger() {
        AigSandboxArtifact row = new AigSandboxArtifact();
        row.setPath("out/a.txt");
        row.setBytes(3L);
        row.setSha256(AigSandboxArtifactManifest.sha256("other".getBytes(StandardCharsets.UTF_8)));
        row.setObjectKey("K");
        when(artifactMapper.selectOne(any())).thenReturn(row);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.download(RUN_ID, "out/a.txt"));

        assertTrue(error.getMessage().contains("摘要不一致"), error.getMessage());
        verify(artifactStore, never()).get(any());
    }

}
