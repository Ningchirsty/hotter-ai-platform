package org.dromara.scenario.api;

import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;

/**
 * 场景"交给既有链路去跑"的跨模块端口（附件 §3.2；ADR-016）。
 *
 * <h3>它是什么、不是什么</h3>
 * <p><b>是</b>：一次跨模块调用的契约——把"这个平台任务该由哪条既有链路执行"这件事，
 * 从门户/治理模块（aigov）交给真正会干活的业务域（创作域阶段机 / 视频链路 / 内容链路）。
 * 平台<b>不自建执行引擎</b>（用户已冻 Q2），只把任务与它的输入快照递过去。</p>
 * <p><b>不是</b>：不是流程引擎，也不是沙箱。它不定义节点/连线，也不执行不可信代码。</p>
 *
 * <h3>各域怎么实现</h3>
 * <p>有既有链路的域各实现一个 Bean，{@link #adapter()} 返回它负责的适配器编码
 * （{@code AigScenarioAdapterEnum} 的 code）。aigov 侧的分发器按编码找到唯一的实现；
 * <b>找不到就拒绝执行</b>（fail-closed），而不是悄悄退化成一次普通模型调用——
 * 那会让"任务跑过了"变成一个假事实。</p>
 *
 * @author ai-gov
 */
public interface AigScenarioFlowPort {

    /**
     * 本实现负责的适配器编码（如 {@code CREATIVE_EXISTING_FLOW}）；同一编码只应有一个实现。
     *
     * @return 适配器编码
     */
    String adapter();

    /**
     * 把一个平台任务交给本域既有链路执行。
     *
     * <p>实现方要自己保证：能识别 {@link AigScenarioFlowRequest#getSnapshotJson()}、
     * 按本域口径做权限/归属校验，并返回可排查的 {@code externalRef}（本域自己的任务ID）。</p>
     *
     * @param request 平台任务与它的输入快照
     * @return 受理结果（未受理时给出可读原因）
     */
    AigScenarioFlowResult dispatch(AigScenarioFlowRequest request);

}
