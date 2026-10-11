package org.dromara.aigov.studio.service;

import org.dromara.aigov.studio.domain.bo.AigStudioCopilotChatBo;
import org.dromara.aigov.studio.domain.vo.AigStudioCopilotTurnVo;

import java.util.List;

/**
 * 训练台「对话式 Copilot」服务（附件 07 增量 6）。
 *
 * <h3>它保证的核心不变式</h3>
 * <ol>
 *     <li><b>对话只产提案</b>：模型输出经过白名单规划器 → 落成一条待审提案
 *         （{@code aig_studio_change_proposal}）。<b>全程不写草稿</b>——改草稿只有"人采纳"那一步；</li>
 *     <li><b>模型的话不被信任</b>：越权路径、值类型不符、没有产生变化，一律被规划器拒绝，
 *         并把拒绝原因如实记进对话账本（{@code REJECTED}）。这条信号不能被吞掉；</li>
 *     <li><b>走网关</b>：模型调用经 {@code IAigInvokeService}（策略校验/配额/审计在同一层，
 *         训练台不开第二条模型通道）；开关 {@code aigov.studio.copilot.enabled} 默认关闭；</li>
 *     <li><b>不伪造灰度证据</b>：刻意不把 {@code agentVersionId} 传给网关——否则点几次对话会把
 *         该版本的 CANARY 计数/失败率抬上去（与训练台测试调用同一纪律）。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface IAigStudioCopilotService {

    /**
     * 发起一轮对话（会真实调用模型；草稿不会被改动）。
     *
     * @param draftId 草稿ID
     * @param bo      入参（消息 + 数据等级 + 可选预算）
     * @param actorId 操作用户ID（必须是草稿责任人：这一轮要花钱）
     * @return 本轮结论（含产出的提案ID，或被拒绝/失败的原因）
     */
    AigStudioCopilotTurnVo chat(Long draftId, AigStudioCopilotChatBo bo, Long actorId);

    /**
     * 会话历史（最近若干条，最新在前）。
     *
     * @param draftId 草稿ID
     * @return 轮次清单
     */
    List<AigStudioCopilotTurnVo> turns(Long draftId);

}
