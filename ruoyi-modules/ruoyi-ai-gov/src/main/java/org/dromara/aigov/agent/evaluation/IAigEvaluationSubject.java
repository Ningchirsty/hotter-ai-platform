package org.dromara.aigov.agent.evaluation;

import java.math.BigDecimal;

/**
 * 评测执行器（SPI，设计 §13.2）。
 *
 * <p><b>为什么是 SPI 而不是把执行逻辑写在治理层</b>：评测要跑的是「某个 Agent 版本在一条
 * 黄金用例上到底产出什么」，而产出这件事由业务实现（策划引擎、视觉 DNA 分析、图像任务构建……）
 * 完成。治理模块若反过来依赖业务模块，依赖方向就反了（业务侧本来就依赖治理层做路由与审计），
 * 结果是两边互相依赖、谁也编不出来。因此治理层只定义「怎么被调用」与「怎么判定」，
 * 执行由业务侧注册实现——与既有的 {@code IAigTaskExecutor} 是同一手法。</p>
 *
 * <p><b>执行器按「评测对象」注册，不按用例类型注册</b>：一个执行器（某个 Agent 版本）可以承载
 * 多条不同类型的用例；派发依据是 {@code (targetType, subjectCode)}，用例编码则透传给执行器
 * 由它自己解释（例如「用哪份输入快照、走哪条分支」）。</p>
 *
 * @author ai-gov
 */
public interface IAigEvaluationSubject {

    /**
     * 是否负责这个评测对象。
     *
     * @param targetType  评测对象类型（{@code AigReleaseTargetTypeEnum} 编码）
     * @param subjectCode 评测对象编码（Agent/Skill/Package 的编码，跨版本稳定）
     * @return 负责则 true
     */
    boolean supports(String targetType, String subjectCode);

    /**
     * 执行器自述（派发失败时列出来，让运维知道平台认识哪些对象）。
     *
     * @return 可读描述，例如 {@code AGENT_VERSION:creative_planning}
     */
    String describe();

    /**
     * 执行一条用例。
     *
     * <p>执行器<b>拿不到</b> {@code expected_json}（见 {@link AigEvaluationRequest} 注释），
     * 因此「黄金用例通过」不可能靠执行器自己凑出来。</p>
     *
     * @param request 执行入参
     * @return 执行结果
     */
    AigEvaluationOutcome execute(AigEvaluationRequest request);

}
