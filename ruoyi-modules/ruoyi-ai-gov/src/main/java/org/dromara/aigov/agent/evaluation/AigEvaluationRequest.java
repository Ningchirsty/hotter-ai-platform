package org.dromara.aigov.agent.evaluation;

/**
 * 评测执行入参（设计 §13.2）。
 *
 * <p>只传「执行器自己取不到的东西」：评测对象是谁、跑哪条用例、输入快照在哪里。
 * 刻意<b>不把 {@code expected_json} 交给执行器</b>——判据由评测服务在拿到产出后统一求值。
 * 否则执行器可以看着判据去凑答案（甚至直接把 expected 抄成输出），
 * 那样「黄金用例通过」就变成了自证。</p>
 *
 * @param targetType       评测对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION）
 * @param subjectCode      评测对象编码（Agent/Skill/Package 编码，跨版本稳定）
 * @param targetVersionId  评测对象版本ID
 * @param version          版本号（执行器可能要按版本走不同分支，如实透传）
 * @param caseCode         用例编码
 * @param caseType         用例类型（PLAN/VISUAL_DNA/IMAGE_QA）
 * @param scenarioCode     用例所属业务场景（可空）
 * @param inputSnapshotRef 输入快照引用（§13.2「记录输入快照」；只存引用不存副本）
 * @author ai-gov
 */
public record AigEvaluationRequest(
    String targetType,
    String subjectCode,
    Long targetVersionId,
    String version,
    String caseCode,
    String caseType,
    String scenarioCode,
    String inputSnapshotRef
) {
}
