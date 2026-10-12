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
     * 只做 {@link #create} 的全部校验与规划，<b>一行都不落库</b>。
     *
     * <p><b>为什么需要它（一个真实的线上故障，2026-10-12 生产实测 3/3 复现）</b>：
     * {@link #create} 是 {@code @Transactional}，而对话式 Copilot 的
     * {@code AigStudioCopilotServiceImpl#chat} 也带 {@code @Transactional}，
     * 且其设计是"<b>捕获</b> create 抛出的『模型越界』业务异常 → 记 {@code REJECTED} 后继续写对话结论"。
     * 问题是：内层事务方法抛异常时，Spring 会把**两者共享的那个事务**标记为 rollback-only；
     * 外层即使 catch 住了、继续写完结论，**提交时仍会抛 {@code UnexpectedRollbackException}**。
     * 于是生产上的表现是：</p>
     * <ul>
     *     <li>用户看到 {@code code=500 发生未知异常，请联系管理员 [错误编号: …]}（RuoYi 把异常掩掉了）；</li>
     *     <li><b>整轮对话被回滚</b>——连"被拒绝"这条结论都没留下。</li>
     * </ul>
     * <p>而 {@code aig_studio_copilot_turn} 的建表注释恰好写着：模型越界记 {@code REJECTED} 是
     * <b>"这条通道最该被看见的信号"</b>（说明提示词与白名单没约束住模型）——修之前这个信号
     * **在生产上永远看不到**，只会变成一条没有信息量的 500。</p>
     *
     * <p><b>所以判定"模型越界"必须发生在调用 {@link #create} 之前。</b>调用方（Copilot）先用本方法
     * try/catch 判定 {@code REJECTED}，再调 {@code create}（此时它不会再抛）。</p>
     *
     * <p>⚠️ <b>本方法刻意不加 {@code @Transactional}</b>：它只读、不写；正因为"没有事务拦截器"，
     * 它抛出的业务异常**不会**参与共享事务的 rollback-only 标记。顺手给它加上注解会把上面这个故障原样带回来
     * （有构建期守卫 `AigStudioProposalTransactionBoundaryTest` 钉住这一点）。</p>
     *
     * @param draftId 草稿ID
     * @param bo      入参（同 {@link #create}）
     * @param actorId 操作用户ID
     * @throws ServiceException 与 {@code create} 完全相同的拒绝原因（越权路径/值类型不符/没有变化/非责任人…）
     */
    void validateForCreate(Long draftId, AigStudioProposalCreateBo bo, Long actorId);

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
