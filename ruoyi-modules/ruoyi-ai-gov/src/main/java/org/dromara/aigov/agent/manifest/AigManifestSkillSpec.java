package org.dromara.aigov.agent.manifest;

import java.util.List;

/**
 * Manifest 里声明的一个 Skill（设计 §5.3、§10.2）。
 *
 * @param code               Skill 编码（跨版本稳定，安装时按它复用/新建 aig_skill）
 * @param name               Skill 名称
 * @param capabilities       能力清单（§10.2 capabilities；安装时写 aig_skill.capabilities，逗号分隔）
 * @param inputSchemaJson    输入 Schema 原文（可空）
 * @param outputSchemaJson   输出 Schema 原文（可空）
 * @param toolPolicyJson     工具策略原文（可空）
 * @param providerCapability 需要的 Provider 能力编码（可空）
 * @param allowExternal      是否允许外部调用（Y/N）
 * @author ai-gov
 */
public record AigManifestSkillSpec(
    String code,
    String name,
    List<String> capabilities,
    String inputSchemaJson,
    String outputSchemaJson,
    String toolPolicyJson,
    String providerCapability,
    String allowExternal
) {
}
