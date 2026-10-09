package org.dromara.aigov.agent.service.impl;

import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.AigSandboxRun;
import org.dromara.aigov.agent.domain.bo.AigSandboxRunRecordBo;
import org.dromara.aigov.agent.evaluation.AigSandboxRunEvidence;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigSandboxRunMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 沙箱运行证据登记与查询的测试。
 *
 * <p>钉住的是这条路唯一重要的性质：<b>登记的东西必须是执行器真的吐出来的那一份</b>。
 * 因此这里逐条验证"缺字段就拒绝"，尤其是 {@code network} 与 {@code timedOut}——
 * 它们直接参与门槛判据，一旦"缺了就按默认值补"，这份证据就变成了由登记代码制造出来的，
 * 恰好把要证明的那件事给补上了。</p>
 *
 * <p>纯 Mockito：注入真实的 {@code JsonMapper}（与生产同一套解析），不加载 Spring 上下文。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSandboxRunServiceImplTest {

    private static final long VERSION_ID = 7401L;

    /**
     * 一份执行器真实输出的形状（字段名照抄 sandbox-run.sh 的结果行）
     */
    private static final String RESULT_JSON = """
        {"jobId":"job-20261010010101-1","image":"nginx@sha256:%s","exitCode":0,"timedOut":false,
         "durationMs":256,"network":"none","scratchFreeMb":1024,
         "artifacts":[{"path":"out/a.txt","bytes":6,"sha256":"%s"},
                      {"path":"out/b.txt","bytes":5,"sha256":"%s"}]}
        """.formatted("a".repeat(64), "b".repeat(64), "c".repeat(64));

    private AigSandboxRunMapper sandboxRunMapper;
    private AigAgentVersionMapper agentVersionMapper;
    private AigSandboxRunServiceImpl service;

    @BeforeEach
    void setUp() {
        sandboxRunMapper = mock(AigSandboxRunMapper.class);
        agentVersionMapper = mock(AigAgentVersionMapper.class);
        AigSkillVersionMapper skillVersionMapper = mock(AigSkillVersionMapper.class);
        AigPackageVersionMapper packageVersionMapper = mock(AigPackageVersionMapper.class);
        service = new AigSandboxRunServiceImpl(sandboxRunMapper, agentVersionMapper,
            skillVersionMapper, packageVersionMapper, JsonMapper.builder().build());

        AigAgentVersion version = new AigAgentVersion();
        version.setAgentVersionId(VERSION_ID);
        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version);
        // 模拟 MyBatis-Plus 的 ID 生成（@TableId 默认 ASSIGN_ID，雪花）：插入时回填主键
        when(sandboxRunMapper.insert(any(AigSandboxRun.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AigSandboxRun.class).setSandboxRunId(123L);
            return 1;
        });
    }

    private AigSandboxRunRecordBo bo(String resultJson) {
        AigSandboxRunRecordBo bo = new AigSandboxRunRecordBo();
        bo.setTargetType("AGENT_VERSION");
        bo.setTargetVersionId(VERSION_ID);
        bo.setJobId("job-20261010010101-1");
        bo.setAgentCode("vibeposter-design");
        bo.setResultJson(resultJson);
        return bo;
    }

    @Test
    @DisplayName("正例：登记执行器原文，摘要列由原文解析而来，原文与哈希一并留存")
    void recordValidResult() {
        Long id = service.record(bo(RESULT_JSON), 9001L);

        assertEquals(123L, id);
        ArgumentCaptor<AigSandboxRun> captor = ArgumentCaptor.forClass(AigSandboxRun.class);
        verify(sandboxRunMapper).insert(captor.capture());
        AigSandboxRun row = captor.getValue();
        assertEquals("AGENT_VERSION", row.getTargetType());
        assertEquals(VERSION_ID, row.getTargetVersionId());
        assertEquals("job-20261010010101-1", row.getJobId());
        assertEquals("vibeposter-design", row.getAgentCode());
        assertEquals("nginx@sha256:" + "a".repeat(64), row.getImageRef());
        assertEquals(0, row.getExitCode());
        assertEquals(false, row.getTimedOut());
        assertEquals(256L, row.getDurationMs());
        assertEquals("none", row.getNetwork());
        assertEquals(1024L, row.getScratchFreeMb());
        assertEquals(2, row.getArtifactCount());
        assertEquals(9001L, row.getRecordedBy());
        assertNotNull(row.getCreateTime());
        // 原文一字不改地留存，哈希由测试自己独立算一遍（同源实现不算验证）
        assertEquals(RESULT_JSON, row.getResultJson());
        assertEquals(sha256Hex(RESULT_JSON), row.getResultSha256());
    }

    @Test
    @DisplayName("原文缺 network → 拒绝登记（绝不允许默认成 none：那正是要证明的那件事）")
    void recordRejectsMissingNetwork() {
        String json = RESULT_JSON.replace("\"network\":\"none\",", "");

        ServiceException error = assertThrows(ServiceException.class, () -> service.record(bo(json), 9001L));

        assertTrue(error.getMessage().contains("network"), error.getMessage());
        verify(sandboxRunMapper, never()).insert(any(AigSandboxRun.class));
    }

    @Test
    @DisplayName("原文缺 timedOut → 拒绝登记（同样不许默认成「没超时」）")
    void recordRejectsMissingTimedOut() {
        String json = RESULT_JSON.replace("\"timedOut\":false,", "");

        ServiceException error = assertThrows(ServiceException.class, () -> service.record(bo(json), 9001L));

        assertTrue(error.getMessage().contains("timedOut"), error.getMessage());
        verify(sandboxRunMapper, never()).insert(any(AigSandboxRun.class));
    }

    @Test
    @DisplayName("timedOut/exitCode 类型不对（字符串）→ 拒绝登记，而不是宽松解析")
    void recordRejectsWrongTypes() {
        String json = RESULT_JSON.replace("\"timedOut\":false", "\"timedOut\":\"false\"");
        assertThrows(ServiceException.class, () -> service.record(bo(json), 9001L));

        String json2 = RESULT_JSON.replace("\"exitCode\":0", "\"exitCode\":\"0\"");
        ServiceException error = assertThrows(ServiceException.class, () -> service.record(bo(json2), 9001L));
        assertTrue(error.getMessage().contains("exitCode"), error.getMessage());

        verify(sandboxRunMapper, never()).insert(any(AigSandboxRun.class));
    }

    @Test
    @DisplayName("原文里的 jobId 与请求不一致 → 拒绝（防止张冠李戴）")
    void recordRejectsJobIdMismatch() {
        String json = RESULT_JSON.replace("job-20261010010101-1", "job-someone-else");

        ServiceException error = assertThrows(ServiceException.class, () -> service.record(bo(json), 9001L));

        assertTrue(error.getMessage().contains("不一致"), error.getMessage());
        verify(sandboxRunMapper, never()).insert(any(AigSandboxRun.class));
    }

    @Test
    @DisplayName("不是 JSON / 不是对象 → 拒绝登记")
    void recordRejectsNotJson() {
        assertThrows(ServiceException.class, () -> service.record(bo("not json at all"), 9001L));
        assertThrows(ServiceException.class, () -> service.record(bo("[1,2,3]"), 9001L));
        verify(sandboxRunMapper, never()).insert(any(AigSandboxRun.class));
    }

    @Test
    @DisplayName("目标版本不存在 → 拒绝（版本ID 打错不该在账本里留下一条指向空处的证据）")
    void recordRejectsUnknownTargetVersion() {
        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.record(bo(RESULT_JSON), 9001L));

        assertTrue(error.getMessage().contains("不存在"), error.getMessage());
        verify(sandboxRunMapper, never()).insert(any(AigSandboxRun.class));
    }

    @Test
    @DisplayName("未知对象类型 → 拒绝")
    void recordRejectsUnknownTargetType() {
        AigSandboxRunRecordBo bo = bo(RESULT_JSON);
        bo.setTargetType("WHATEVER");

        ServiceException error = assertThrows(ServiceException.class, () -> service.record(bo, 9001L));

        assertTrue(error.getMessage().contains("未知的对象类型"), error.getMessage());
    }

    @Test
    @DisplayName("同一作业登记两次 → 明确报错（job_id 唯一：重复登记就是假账）")
    void recordRejectsDuplicateJob() {
        when(sandboxRunMapper.insert(any(AigSandboxRun.class)))
            .thenThrow(new DuplicateKeyException("uk_aig_sandbox_job"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.record(bo(RESULT_JSON), 9001L));

        assertTrue(error.getMessage().contains("已经登记过"), error.getMessage());
    }

    @Test
    @DisplayName("证据查询：没有记录时给结论与原因，而不是抛异常（页面要能渲染）")
    void evidenceUnavailableWhenNoRun() {
        when(sandboxRunMapper.selectOne(any())).thenReturn(null);

        AigSandboxRunEvidence evidence = service.sandboxRunEvidence("AGENT_VERSION", VERSION_ID);

        assertNotNull(evidence);
        assertFalse(evidence.satisfied());
        assertTrue(evidence.reason().contains("没有任何沙箱运行记录"), evidence.reason());
    }

    @Test
    @DisplayName("证据查询：对象类型/版本ID 为空也给结论，不抛异常")
    void evidenceUnavailableWhenParamsMissing() {
        assertFalse(service.sandboxRunEvidence(null, VERSION_ID).satisfied());
        assertFalse(service.sandboxRunEvidence("AGENT_VERSION", null).satisfied());
        assertFalse(service.sandboxRunEvidence("NOPE", VERSION_ID).satisfied());
    }

    /**
     * 独立实现一遍 SHA-256（不复用被测代码的私有方法）。
     *
     * @param text 原文
     * @return 小写十六进制摘要
     */
    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
