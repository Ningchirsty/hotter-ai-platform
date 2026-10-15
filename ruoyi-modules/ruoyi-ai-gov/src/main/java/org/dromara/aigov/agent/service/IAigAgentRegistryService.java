package org.dromara.aigov.agent.service;

import org.dromara.aigov.agent.domain.bo.AigAgentBindingBo;
import org.dromara.aigov.agent.domain.bo.AigAgentResourceBindingBo;
import org.dromara.aigov.agent.domain.bo.AigReleaseAdvanceBo;
import org.dromara.aigov.agent.enums.AigReleaseGateEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.manifest.AigManifestScanResult;

import java.util.Set;

/**
 * Agent/Skill/Package 注册与发布服务（设计 §5.4、§6.3）。
 *
 * <p><b>本接口只有一条发布写入口</b>：{@link #advanceRelease}。没有「直接置某个状态」的方法，
 * 因此跳步在类型层面做不到——这正是状态机注释里「服务层只能用 nextIfAllGatesPassed 推进」
 * 那句话的落地方式。</p>
 *
 * @author ai-gov
 */
public interface IAigAgentRegistryService {

    /**
     * 推进发布状态（<b>唯一写入口</b>）。
     *
     * <p>顺序与判据：</p>
     * <ol>
     *     <li>对象与目标状态必须合法；</li>
     *     <li>{@code expectedStatus} 与库中当前状态必须一致，否则报「视图已过期」
     *         （比"更新影响 0 行"更早也更可读）；</li>
     *     <li>目标是门槛驱动的状态时，必须正好等于
     *         {@code nextIfAllGatesPassed(当前, passedGates)}——缺门槛或跳步都拒绝；</li>
     *     <li>{@code DISABLED → STABLE} 必须能证明该版本<b>曾经 STABLE 过</b>
     *         （证据来自发布事件账本），否则就是绕道发布；</li>
     *     <li>发布到 CANDIDATE/STABLE 且通道受限时，必须有启用中的绑定，
     *         否则「已发布」与「谁都看不见」会同时成立；</li>
     *     <li>写库用「按当前状态做条件更新」，影响 0 行即报并发冲突；</li>
     *     <li>无论成败，推进成功即向发布事件账本追加一行。</li>
     * </ol>
     *
     * @param bo 推进入参
     * @return 推进后的状态
     */
    AigReleaseStatusEnum advanceRelease(AigReleaseAdvanceBo bo);

    /**
     * 取当前状态前进所缺的门槛（供页面显示「还差：沙箱运行、人工批准」）。
     *
     * @param targetType      对象类型（{@code AigReleaseTargetTypeEnum}）
     * @param targetVersionId 对象版本ID
     * @param passedGates     已通过的门槛（可为空）
     * @return 尚缺的门槛；对象不接受门槛推进时返回空集合
     */
    Set<AigReleaseGateEnum> missingGates(String targetType, Long targetVersionId,
                                         Set<AigReleaseGateEnum> passedGates);

    /**
     * 该版本是否曾经达到过 STABLE（发布事件账本为准）。
     *
     * @param targetType      对象类型
     * @param targetVersionId 对象版本ID
     * @return 曾经 STABLE 过返回 true
     */
    boolean wasEverStable(String targetType, Long targetVersionId);

    /**
     * 扫描库中 Package 版本的 Manifest，并把结论落到该版本上（设计 §6.1、§6.2）。
     *
     * <p>这是「Manifest 校验」这道门槛的<b>证据来源</b>：{@link #advanceRelease} 不接受调用方
     * 单方面声明「已通过 Manifest 校验」，只接受库里 {@code scan_result=PASS} 这一事实。</p>
     *
     * <p>三条约束：</p>
     * <ol>
     *     <li>只对 <b>DRAFT</b> 版本扫描并落库。版本一旦离开 DRAFT，库中的扫描结论就是
     *         「当时凭什么放行」的历史记录，重算覆盖会把审计痕迹改掉；</li>
     *     <li>只记录结论，<b>不覆盖</b> {@code manifest_hash}。原文哈希与重算值不一致时，
     *         结论是拒绝（命中 §6.2-4），而不是「顺手把哈希改成新的」——后者恰好会抹掉
     *         「原文被动过」这件事；</li>
     *     <li>落库带「当前状态必须是 DRAFT」的条件，影响 0 行即报并发冲突，不覆盖他人结论。</li>
     * </ol>
     *
     * @param packageVersionId Package 版本ID
     * @return 扫描结论（同时已落库）
     */
    AigManifestScanResult scanStoredManifest(Long packageVersionId);

    /**
     * 新增一条 Agent 版本绑定。
     *
     * <p>会校验：目标版本存在；绑定的发布通道与版本当前通道一致（留空则取版本的通道）——
     * 「通道说 A、绑定说 B」会让按通道查询的结果自相矛盾。</p>
     *
     * @param bo 绑定入参
     * @return 绑定ID
     */
    Long addBinding(AigAgentBindingBo bo);

    /**
     * 撤销一条 Agent 版本绑定（D03，逻辑删除）。
     *
     * <h3>为什么必须有这个入口</h3>
     * <p>绑定决定「受限通道发布后谁能看见、哪个版本能被启动」。此前只有新增与查询接口，
     * 一旦绑错（品牌选错、有效期填错、灰度结束），没有任何办法让它失效——只能改库。
     * 撤销后：发布门槛的「启用中的绑定」计数立刻下降，执行侧的版本授权检查立刻拒绝。</p>
     *
     * <h3>为什么是逻辑删除</h3>
     * <p>{@code del_flag} 带 {@code @TableLogic}，删除只置标志：行还在，能回答
     * 「这条绑定曾经存在过、什么时候没的、为什么没的」；而所有查询（含发布门槛计数、
     * 执行期授权）都由框架自动追加 {@code del_flag='0'}，撤销即时生效。</p>
     *
     * <h3>与「停用（enabled）」的区别</h3>
     * <p>这里不提供"改 enabled 开关"：{@code enabled='N'} 的行仍占据该版本的一个绑定名额，
     * 外观上"有绑定"、实际"不可用"，是最容易误判的状态。撤销 = 移除，语义唯一。</p>
     *
     * @param bindingId 绑定ID
     * @param reason    撤销原因（可空；写入日志，便于事后回答"为什么没的"）
     */
    void revokeBinding(Long bindingId, String reason);

    /**
     * 执行期版本授权检查（D03：设计 §7.2「正式执行时重新检查授权、停用和资源退役」）。
     *
     * <p>受限通道（TESTING/BRAND/DEPT 等）的 Agent 版本，必须在<b>当前</b>存在一条
     * 启用中且在有效期内的绑定时才允许执行；否则拒绝并说明原因。
     * 不受限通道（如 PUBLIC）不依赖绑定，直接放行。裸能力调用（{@code agentVersionId} 为空）
     * 不属于"某个版本的执行"，本方法直接返回。</p>
     *
     * <p><b>为什么在这里再查一次</b>：发布时校验过不等于执行时仍成立——绑定可能被撤销、
     * 有效期可能到期。只在发布时查，等于把"当时能发布"错误地当成了"现在能执行"。</p>
     *
     * @param agentVersionId Agent 版本ID（可空）
     * @throws org.dromara.common.core.exception.ServiceException 版本不存在，或受限通道且无有效绑定
     */
    void assertExecutionAuthorized(Long agentVersionId);

    /**
     * 新增一条工具/知识绑定（附件 07 §5）。
     *
     * <p>会校验：版本存在；资源类型合法；<b>资源编码命中该版本已声明的项</b>
     * （TOOL 看 {@code allowed_tools} 并拒 {@code forbidden_tools}，KNOWLEDGE 看
     * {@code knowledge_scope_json}）；同一范围下不重复绑定。</p>
     *
     * <p><b>它只登记、不执行</b>：附件 07 §10 明确不建 Tool Gateway / Knowledge 引擎，
     * 本表没有任何执行语义。</p>
     *
     * @param bo 绑定入参
     * @return 绑定ID
     */
    Long addResourceBinding(AigAgentResourceBindingBo bo);

    /**
     * 删除一条工具/知识绑定（<b>逻辑删除</b>，附件 07 §5）。
     *
     * <h3>为什么必须有这个入口</h3>
     * <p>绑定的"同一范围只能绑一次"是服务层显式做的，而<b>范围列不可改</b>——
     * 于是绑错一次（比如范围填错）就没有出路：既不能再绑一条同范围的，
     * 也没有任何接口能改它。重复检查的拒绝文案自己写着「要改范围请删掉旧的那条再建」，
     * 所以这个入口不是"顺手加的 CRUD"，而是那句文案成立的前提。</p>
     *
     * <h3>为什么是逻辑删除而不是物理删除</h3>
     * <p>实体上 {@code del_flag} 带 {@code @TableLogic}，删除只会把 {@code del_flag} 置为已删除：
     * 行还在，能回答"这条绑定曾经存在过、什么时候没的"；而所有查询（含重复检查、
     * {@code declared-resources} 的 {@code bound} 标记）都由框架自动追加 {@code del_flag='0'}，
     * 所以删掉之后**同范围立刻可以重建**——这正是文案承诺的那个动作。</p>
     *
     * <h3>为什么不提供"停用"</h3>
     * <p>{@code enabled} 只在创建时能设，看起来像少了一个开关；但本表<b>没有执行语义</b>
     * （附件 07 §10：只登记、不执行，仓库里没有调用路径会读它），所以"停用"除了显示与筛选
     * 没有任何运行时效果；反而会留下一个<b>仍然占着"同一范围"名额</b>的行——
     * 重复检查不看 {@code enabled}，于是"停用"把用户卡在"既用不了、也绑不上"的状态。
     * 删掉+重建才是这个动作真正需要的语义。</p>
     *
     * @param bindingId 绑定ID
     */
    void removeResourceBinding(Long bindingId);

}
