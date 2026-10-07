package org.dromara.aigov.agent.service;

import org.dromara.aigov.agent.domain.bo.AigAgentBindingBo;
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

}
