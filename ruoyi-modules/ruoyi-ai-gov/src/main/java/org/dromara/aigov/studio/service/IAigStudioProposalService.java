package org.dromara.aigov.studio.service;

import org.dromara.aigov.studio.domain.bo.AigStudioProposalCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalDecideBo;
import org.dromara.aigov.studio.domain.vo.AigStudioChangeProposalVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;

import java.util.List;

/**
 * 训练草稿「变更提案」服务（增量 21／附件 07 增量 6，ST-003/ST-004）。
 *
 * <h3>它保证的核心不变式</h3>
 * <ol>
 *     <li><b>只产提案</b>：创建提案<b>绝不</b>修改草稿内容与修订——
 *         "Copilot 改了草稿"在库里必须与人改的完全不同；</li>
 *     <li><b>白名单</b>：提案里的路径不在白名单内一律拒绝（越权 operation 拒绝），
 *         尤其不允许改类别、能力编码、外发许可与 Schema；</li>
 *     <li><b>采纳才产生修订</b>：采纳按 {@code (draftId, baseRevision)} 做 CAS，
 *         并重新计算内容哈希与提案里存的比对——内容漂移即拒绝；</li>
 *     <li><b>一次处理</b>：提案只能被处理一次（状态 CAS），并发采纳/拒绝只有一方成功。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface IAigStudioProposalService {

    /**
     * 创建一条变更提案（只校验与留档，不改草稿）。
     *
     * @param draftId 草稿ID
     * @param bo      入参（{@code baseRevision} 必填；操作清单非空）
     * @param actorId 操作用户ID（必须是草稿责任人）
     * @return 提案视图（含改动预览）
     */
    AigStudioChangeProposalVo create(Long draftId, AigStudioProposalCreateBo bo, Long actorId);

    /**
     * 列出草稿的提案（最新在前）。
     *
     * @param draftId 草稿ID
     * @return 提案列表
     */
    List<AigStudioChangeProposalVo> list(Long draftId);

    /**
     * 采纳提案：按提案内容产生一条 {@code source=COPILOT_ACCEPTED} 的新修订。
     *
     * @param proposalId 提案ID
     * @param bo         入参（{@code expectedRevision} 必填，CAS）
     * @param actorId    操作用户ID（必须是草稿责任人）
     * @return 落库后的草稿详情
     */
    AigStudioDraftDetailVo apply(Long proposalId, AigStudioProposalDecideBo bo, Long actorId);

    /**
     * 拒绝提案（内容不变）。
     *
     * @param proposalId 提案ID
     * @param bo         入参（{@code reason} 可空）
     * @param actorId    操作用户ID（必须是草稿责任人）
     * @return 拒绝后的提案视图
     */
    AigStudioChangeProposalVo reject(Long proposalId, AigStudioProposalDecideBo bo, Long actorId);

}
