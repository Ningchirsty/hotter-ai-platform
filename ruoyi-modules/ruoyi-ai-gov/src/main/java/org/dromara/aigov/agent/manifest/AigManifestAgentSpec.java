package org.dromara.aigov.agent.manifest;

import java.util.List;

/**
 * Manifest 里声明的一个 Agent（设计 §5.3 的逐版本配置项）。
 *
 * <p><b>为什么必须能声明「这个包带了哪些 Agent/Skill」</b>：§6.1 的最小白名单里没有这一项，
 * 于是「Package 安装」在实现时会发现<b>无物可建</b>——包类型说有 Agent，可 Manifest 里
 * 没有任何字段能说清是哪个、什么类别、用什么 Prompt。安装器不能靠猜，所以把内容物声明补进
 * 允许的可选字段（仍是声明式：只写配置，不写代码）。</p>
 *
 * <p>子字段同样走白名单：出现未声明的子字段即拒绝（理由与顶层一致——平台看不懂的字段
 * 今天被忽略、明天可能被执行）。</p>
 *
 * @param code               Agent 编码（跨版本稳定，安装时按它复用/新建 aig_agent）
 * @param name               Agent 名称
 * @param category           类别（PLANNING/VISUAL_DNA/GENERATION/QA）
 * @param scenarioCode       适用业务场景（可空；单个值，因为 aig_agent_version.scenario_code 是单值列）
 * @param promptTemplate     Prompt 模板（可空：确定性引擎不写 Prompt）
 * @param inputSchemaJson    输入 Schema 原文（可空）
 * @param outputSchemaJson   输出 Schema 原文（可空）
 * @param providerCapability 需要的 Provider 能力编码（可空）
 * @param allowExternal      是否允许外部调用（Y/N）
 * @param goldenCases        该 Agent 的黄金用例编码（写入 config_json.golden_cases）
 * @author ai-gov
 */
public record AigManifestAgentSpec(
    String code,
    String name,
    String category,
    String scenarioCode,
    String promptTemplate,
    String inputSchemaJson,
    String outputSchemaJson,
    String providerCapability,
    String allowExternal,
    List<String> goldenCases
) {
}
