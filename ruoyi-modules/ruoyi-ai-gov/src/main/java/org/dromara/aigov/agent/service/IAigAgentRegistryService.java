package org.dromara.aigov.agent.service;

import org.dromara.aigov.agent.domain.bo.AigAgentBindingBo;
import org.dromara.aigov.agent.domain.bo.AigReleaseAdvanceBo;
import org.dromara.aigov.agent.enums.AigReleaseGateEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;

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
