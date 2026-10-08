package org.dromara.aigov.domain.vo;

import org.dromara.aigov.domain.AigInvocationAudit;
import org.dromara.aigov.domain.AigInvocationAuditToAigInvocationAuditVoMapperImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉住「{@code AigInvocationAudit.agentVersionId} 真的映射进调用明细 VO」这件事。
 *
 * <p><b>为什么值得单独一条</b>：审计列表（{@code GET /aigov/audit/list} 的读模型）走
 * {@code BaseMapperPlus.selectVoPage}，实体到 VO 的转换由 MapStruct-Plus 按<b>字段名</b>生成。
 * 于是有一类缺陷既不会编译报错、也不会运行报错：字段名对不上时，MapStruct 只是<b>不映射</b>，
 * 那一列在接口返回里恒为空——正好又落回「列在、值恒为空」这个本模块反复出问题的形态。</p>
 *
 * <p>所以这里直接调用<b>生成出来的转换器</b>（{@code ...MapperImpl}，MapStruct 生成的实现类，
 * 可以脱离 Spring 直接 new）验证值真的过去了。{@code MapstructUtils} 不行：它在类初始化时
 * 就 {@code SpringUtils.getBean(Converter.class)}，纯单测里起不来。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigInvocationAuditVoMappingTest {

    private final AigInvocationAuditToAigInvocationAuditVoMapperImpl mapper =
        new AigInvocationAuditToAigInvocationAuditVoMapperImpl();

    @Test
    @DisplayName("Agent 版本必须映射进 VO：否则调用明细里这一列看着永远是空的")
    void agentVersionIdReachesTheVo() {
        AigInvocationAudit entity = new AigInvocationAudit();
        entity.setAgentVersionId(4242L);
        entity.setModelVersion("v1");
        entity.setErrorClass("AUTH_FAILED");

        AigInvocationAuditVo vo = mapper.convert(entity);

        assertEquals(4242L, vo.getAgentVersionId(),
            "实体有值、VO 没值，说明字段没被映射（名字对不上时 MapStruct 静默跳过，不报错）");
        assertEquals("v1", vo.getModelVersion(),
            "模型版本列不受影响：model_version（模型）与 agent_version_id（Agent）是两个维度");
        assertEquals("AUTH_FAILED", vo.getErrorClass(),
            "错误分类同样要映射进明细：灰度依据它判「有无严重错误」，人也要能一眼看出严不严重");
    }

    @Test
    @DisplayName("不经任务的调用没有 Agent 版本：明细里留空，不伪造")
    void absentAgentVersionStaysNull() {
        AigInvocationAuditVo vo = mapper.convert(new AigInvocationAudit());

        assertNull(vo.getAgentVersionId(),
            "空值表示「本次未绑定某个 Agent 版本」；伪造一个会让按版本统计把无归属调用算到别人头上");
        assertNull(vo.getErrorClass(), "成功调用没有错误分类，留空表示「没有错误」而不是「不知道」");
    }

}
