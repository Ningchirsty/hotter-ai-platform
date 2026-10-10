package org.dromara.aigov.studio.service;

import org.dromara.aigov.studio.domain.bo.AigStudioTestRunBo;
import org.dromara.aigov.studio.domain.vo.AigStudioTestRunVo;

/**
 * 训练台测试调用服务（专题 C §C3.1 Playground / §C4 沙箱；增量 S5）。
 *
 * <h3>它是什么、不是什么</h3>
 * <p><b>是</b>：把某一版草稿内容真的发一次出去、看它产出什么，并把这次调用钉进
 * {@code aig_studio_execution_link} 证据链。</p>
 * <p><b>不是</b>：ADR-015/016 那个"宿主侧进程 + 一次性受限容器"的沙箱——那条链是给
 * **执行不可信代码/脚本**用的。本服务只发一次受治理的模型调用，**不自建任何执行沙箱**。</p>
 *
 * <h3>三条不可让步的约束</h3>
 * <ol>
 *     <li><b>默认关闭</b>（{@code aigov.studio.test.enabled=false}）：开着它就能花真钱。</li>
 *     <li><b>必须走网关</b>（{@code IAigInvokeService}）：策略校验与配额在网关执行点上，
 *         绕过它就等于给训练台开了一条"不经治理的模型通道"。</li>
 *     <li><b>不把测试调用算成灰度数据</b>：调用不带 {@code agentVersionId}，
 *         否则训练台点几次测试就会把 CANARY 的调用计数/失败率抬上去——
 *         那是"用测试伪造灰度证据"。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface IAigStudioTestService {

    /**
     * 用草稿的当前修订跑一次测试调用，并把证据落进链上。
     *
     * @param draftId 草稿ID
     * @param bo      入参（数据等级必填——测错等级等于没测）
     * @param actorId 操作用户ID（必须是草稿责任人）
     * @return 测试结果（含输出预览、实际选中的模型、traceId、证据链ID）
     */
    AigStudioTestRunVo runTest(Long draftId, AigStudioTestRunBo bo, Long actorId);

    /**
     * 读取一条测试证据（不改状态、不再调用）。
     *
     * @param linkId 证据链ID
     * @return 证据详情
     */
    AigStudioTestRunVo getTestRun(Long linkId);

}
