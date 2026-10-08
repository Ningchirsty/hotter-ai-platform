package org.dromara.aigov.helper;

import org.dromara.aigov.domain.AigInvocationAudit;
import org.dromara.aigov.mapper.AigInvocationAuditMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 审计落库时「上下文 → 实体」的三列映射测试。
 *
 * <p><b>为什么单独测这一段</b>：{@link AigAuditContext} 里放对值与真的写进库之间还有一层，
 * 而漏掉这一层的症状是「接口返回一切正常、审计里那一列恒为空」——没有报错，
 * 只有等你真的去按供应商对账时才发现全表都是 NULL，那时历史数据已经补不回来了。</p>
 *
 * <p>同时钉住两列的截断：{@code usage_json} 是 varchar(1000)、
 * {@code input_snapshot_ref} 是 varchar(500)。不截断时超长写入会<b>整条审计插入失败</b>，
 * 而审计失败被刻意设计成不外抛（见 {@link AigAuditRecorder}）——于是表现为「这次调用
 * 完全没有审计记录」，最难查的一种。</p>
 *
 * <p>纯 Mockito：不加载 Spring 上下文，也不碰真实数据库。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigAuditRecorderColumnsTest {

    private AigInvocationAuditMapper auditMapper;
    private AigAuditRecorder recorder;

    @BeforeEach
    void setUp() {
        auditMapper = mock(AigInvocationAuditMapper.class);
        recorder = new AigAuditRecorder(auditMapper);
    }

    /**
     * 造一个「调用人与能力都已填好」的上下文，避免记录器去读 Sa-Token 静态登录态。
     *
     * @return 审计上下文
     */
    private static AigAuditContext context() {
        AigAuditContext ctx = new AigAuditContext();
        ctx.setTraceId("trace-1");
        ctx.setCapabilityCode("image_generation");
        ctx.setCallerId(9L);
        ctx.setCallerName("tester");
        ctx.setDataLevel("INTERNAL");
        ctx.setResult("0");
        ctx.setAuditLevel("SUMMARY");
        return ctx;
    }

    /**
     * 取写入审计表的实体。
     *
     * @return 审计实体
     */
    private AigInvocationAudit capture() {
        ArgumentCaptor<AigInvocationAudit> captor = ArgumentCaptor.forClass(AigInvocationAudit.class);
        verify(auditMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("三列都要落库：供应商、用量、输入快照引用")
    void mapsProviderUsageAndSnapshot() {
        AigAuditContext ctx = context();
        ctx.setProviderId(1001L);
        ctx.setUsageJson("{\"tokensUsed\":1234}");
        ctx.setInputSnapshotRef("oss://ai-input/snap/abc.json");
        ctx.setCost(new BigDecimal("0.25"));

        recorder.record(ctx);

        AigInvocationAudit audit = capture();
        assertEquals(1001L, audit.getProviderId(), "供应商必须落库，否则只能靠模型ID join 现查（查到的是今天的归属）");
        assertEquals("{\"tokensUsed\":1234}", audit.getUsageJson(), "用量必须落库，否则解析出来也会被丢掉");
        assertEquals("oss://ai-input/snap/abc.json", audit.getInputSnapshotRef(),
            "输入快照引用必须落库，它是事后复现的唯一入口");
        assertEquals(0, new BigDecimal("0.25").compareTo(audit.getCost()));
    }

    @Test
    @DisplayName("三列为空时要保持 null，不能写成空串——空串会让「未采集」看起来像「有值」")
    void keepsNullWhenAbsent() {
        recorder.record(context());

        AigInvocationAudit audit = capture();
        assertNull(audit.getProviderId());
        assertNull(audit.getUsageJson());
        assertNull(audit.getInputSnapshotRef());
    }

    @Test
    @DisplayName("Agent 版本列要从上下文落到实体：漏掉这一层就是「列在、值恒为空」")
    void mapsAgentVersionId() {
        AigAuditContext ctx = context();
        ctx.setModelVersion("v1");
        ctx.setAgentVersionId(4242L);

        recorder.record(ctx);

        AigInvocationAudit audit = capture();
        assertEquals(4242L, audit.getAgentVersionId(), "Agent 版本必须落库，否则灰度无从按版本统计");
        assertEquals("v1", audit.getModelVersion(),
            "模型版本列不受影响：model_version（模型）与 agent_version_id（Agent）是两个维度");
    }

    @Test
    @DisplayName("没有 Agent 版本时保持 null：空值表示「本次未绑定」，不是「不知道」")
    void absentAgentVersionStaysNull() {
        recorder.record(context());

        assertNull(capture().getAgentVersionId());
    }

    @Test
    @DisplayName("错误分类列要落库：灰度的「无严重错误」判据只能靠它，不能靠解析 policy_hit 文本")
    void mapsErrorClass() {
        AigAuditContext ctx = context();
        ctx.setResult("1");
        ctx.setErrorClass("AUTH_FAILED");

        recorder.record(ctx);

        assertEquals("AUTH_FAILED", capture().getErrorClass(),
            "分类必须落在独立列上：policy_hit 是 varchar(255) 且该片段最后追加，会被截掉");
    }

    @Test
    @DisplayName("成功调用不写错误分类：空表示「没有错误」，不是「不知道」")
    void absentErrorClassStaysNull() {
        recorder.record(context());

        assertNull(capture().getErrorClass());
    }

    @Test
    @DisplayName("超长 usage_json 必须截断到列宽，否则整条审计插入失败且失败被静默吞掉")
    void truncatesOversizedUsageJson() {
        AigAuditContext ctx = context();
        ctx.setUsageJson("x".repeat(1500));

        recorder.record(ctx);

        assertEquals(1000, capture().getUsageJson().length(),
            "usage_json 列宽 1000；不截断会整条插入失败，而审计失败不外抛 → 表现为这次调用没有审计");
    }

    @Test
    @DisplayName("超长 input_snapshot_ref 必须截断到 500")
    void truncatesOversizedSnapshotRef() {
        AigAuditContext ctx = context();
        ctx.setInputSnapshotRef("s".repeat(800));

        recorder.record(ctx);

        assertEquals(500, capture().getInputSnapshotRef().length(), "input_snapshot_ref 列宽 500");
    }

}
