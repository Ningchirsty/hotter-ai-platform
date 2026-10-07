package org.dromara.aigov.agent.state;

import org.dromara.aigov.agent.enums.AigReleaseGateEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Agent / Skill / Package 版本的发布状态机（设计 §5.4、§6.3）。
 *
 * <p>与 {@code AigTaskStateMachine} 同样的理由抽成纯类：发布这件事「错了不会报错」——
 * 一个没跑过沙箱的版本被直接置为 STABLE，没有任何异常，只是安静地放出去了。
 * 做成不依赖 Spring / 数据库的纯函数，才能把「哪些边合法」「哪一步缺哪个门槛」完整测出来。</p>
 *
 * <h3>三条刻意做对的判断</h3>
 *
 * <p><b>① 不设「退回」边。</b>设计只写了向前的链路与「失败则停用或回滚」，没有写回退。
 * 这里也刻意不补：<b>版本内容不可变</b>（{@code manifest_hash} 就是对原样字节算的），
 * 一旦允许 {@code VALIDATED → DRAFT}、{@code CANDIDATE → SANDBOX_TESTED} 这类边，
 * 同一个版本就可以「带着旧结论重新过门槛」，审核结论变成可以重置的东西。
 * 正确做法是<b>失败就停在原地或停用，要改就出新版本</b>（版本号递增，历史可追溯）。</p>
 *
 * <p><b>② 任何非归档状态都能停用或归档。</b>否则一个校验没过的版本只能永远躺在 DRAFT 列表里，
 * 界面上既没有失败原因也没有可操作项。停用是可撤销的（回滚后重新启用），
 * 归档是终态。</p>
 *
 * <p><b>③ 门槛是「与」关系，且只能按门槛推进。</b>{@code SANDBOX_TESTED → CANDIDATE}
 * 需要<b>黄金用例与人工批准两把都过</b>；{@code CANDIDATE → STABLE} 需要灰度达标。
 * 服务层因此没有「直接置 STABLE」的入口——只能提交「已通过的门槛集合」，
 * 由本类判定能否前进。跳步在类型层面就做不到。</p>
 *
 * <h3>本类做不到、必须由服务层兜住的一件事</h3>
 * <p>{@code DISABLED → STABLE} 是合法的（停用后重新启用 / 回滚目标复位），但
 * <b>「这个版本历史上是否真的 STABLE 过」状态机看不到</b>——它只看当前状态，不查历史。
 * 若不核对就允许这条边，任何人都能借「先停用再启用」把从未发布过的版本推成 STABLE。
 * 因此服务层必须用发布记录（{@code aig_package_install_log} 与版本状态迁移事件）
 * 证明该版本曾达到过 STABLE，才能走这条边。</p>
 *
 * <h3>另一条边界</h3>
 * <p>状态机只保证「<b>不缺门槛、不跳步</b>」。它<b>不保证</b>上报的门槛集合与历史一致
 * （例如在 DRAFT 就声称沙箱已跑过）——那属于审计与流程完整性的范畴，
 * 由服务层记录实际提交的门槛集合并留痕，而不是在这里猜。</p>
 *
 * @author ai-gov
 */
public final class AigReleaseStateMachine {

    /**
     * 状态迁移图：key = 源状态，value = 允许到达的状态集合。
     */
    private static final Map<AigReleaseStatusEnum, Set<AigReleaseStatusEnum>> EDGES = buildEdges();

    /**
     * 每个状态前进到下一状态必须<b>全部</b>通过的门槛；空集合表示该状态不接受门槛推进。
     */
    private static final Map<AigReleaseStatusEnum, Set<AigReleaseGateEnum>> ADVANCE_GATES = buildAdvanceGates();

    /**
     * 门槛全部通过后的落点。
     */
    private static final Map<AigReleaseStatusEnum, AigReleaseStatusEnum> ADVANCE_TO = buildAdvanceTo();

    private AigReleaseStateMachine() {
    }

    /**
     * 构造状态迁移图。
     *
     * @return 不可变的迁移图
     */
    private static Map<AigReleaseStatusEnum, Set<AigReleaseStatusEnum>> buildEdges() {
        Map<AigReleaseStatusEnum, Set<AigReleaseStatusEnum>> edges =
            new EnumMap<>(AigReleaseStatusEnum.class);
        edges.put(AigReleaseStatusEnum.DRAFT, EnumSet.of(
            // 设计 §5.4：DRAFT → VALIDATED（Manifest/Schema 校验 + 拒绝规则扫描通过）
            AigReleaseStatusEnum.VALIDATED,
            // 停用/归档出口：校验不过、或作者放弃
            AigReleaseStatusEnum.DISABLED,
            AigReleaseStatusEnum.ARCHIVED));
        edges.put(AigReleaseStatusEnum.VALIDATED, EnumSet.of(
            // 设计 §5.4/§6.3-4：VALIDATED → SANDBOX_TESTED
            AigReleaseStatusEnum.SANDBOX_TESTED,
            AigReleaseStatusEnum.DISABLED,
            AigReleaseStatusEnum.ARCHIVED));
        edges.put(AigReleaseStatusEnum.SANDBOX_TESTED, EnumSet.of(
            // 设计 §5.4/§6.3-5：SANDBOX_TESTED → CANDIDATE（黄金用例 + 三方审批）
            AigReleaseStatusEnum.CANDIDATE,
            AigReleaseStatusEnum.DISABLED,
            AigReleaseStatusEnum.ARCHIVED));
        edges.put(AigReleaseStatusEnum.CANDIDATE, EnumSet.of(
            // 设计 §5.4/§6.3-7：CANDIDATE → STABLE（灰度达标）
            AigReleaseStatusEnum.STABLE,
            // 灰度不达标 → 停用（不是退回沙箱：版本不可变，要改就出新版本）
            AigReleaseStatusEnum.DISABLED,
            AigReleaseStatusEnum.ARCHIVED));
        edges.put(AigReleaseStatusEnum.STABLE, EnumSet.of(
            // 停用：出问题或主动下线（§6.3-7「失败则停用或回滚」）
            AigReleaseStatusEnum.DISABLED,
            // 归档：彻底退役
            AigReleaseStatusEnum.ARCHIVED));
        edges.put(AigReleaseStatusEnum.DISABLED, EnumSet.of(
            // 重新启用 / 回滚目标复位（服务层必须证明它曾达到过 STABLE，见类注释）
            AigReleaseStatusEnum.STABLE,
            AigReleaseStatusEnum.ARCHIVED));
        // 终态：没有任何出边
        edges.put(AigReleaseStatusEnum.ARCHIVED, EnumSet.noneOf(AigReleaseStatusEnum.class));
        return Collections.unmodifiableMap(edges);
    }

    /**
     * 构造「前进所需门槛」表。
     *
     * @return 不可变的门槛表
     */
    private static Map<AigReleaseStatusEnum, Set<AigReleaseGateEnum>> buildAdvanceGates() {
        Map<AigReleaseStatusEnum, Set<AigReleaseGateEnum>> gates =
            new EnumMap<>(AigReleaseStatusEnum.class);
        // 设计 §5.4 的顺序：Manifest 校验 → 沙箱运行 → 黄金用例 + 人工批准 → 灰度
        gates.put(AigReleaseStatusEnum.DRAFT,
            EnumSet.of(AigReleaseGateEnum.MANIFEST_VALIDATION));
        gates.put(AigReleaseStatusEnum.VALIDATED,
            EnumSet.of(AigReleaseGateEnum.SANDBOX_RUN));
        // 注意这里是「两把都过」：黄金用例（§13.2）与三方人工批准（§6.3-5）缺一不可
        gates.put(AigReleaseStatusEnum.SANDBOX_TESTED,
            EnumSet.of(AigReleaseGateEnum.GOLDEN_CASE, AigReleaseGateEnum.HUMAN_APPROVAL));
        gates.put(AigReleaseStatusEnum.CANDIDATE,
            EnumSet.of(AigReleaseGateEnum.CANARY));
        // 以下状态不接受「门槛推进」：STABLE 只能停用/归档；
        // DISABLED → STABLE 是运维动作（重新启用），不是过门槛，故不在此表内
        gates.put(AigReleaseStatusEnum.STABLE, EnumSet.noneOf(AigReleaseGateEnum.class));
        gates.put(AigReleaseStatusEnum.DISABLED, EnumSet.noneOf(AigReleaseGateEnum.class));
        gates.put(AigReleaseStatusEnum.ARCHIVED, EnumSet.noneOf(AigReleaseGateEnum.class));
        return Collections.unmodifiableMap(gates);
    }

    /**
     * 构造「门槛全过之后的落点」。
     *
     * @return 不可变的落点表
     */
    private static Map<AigReleaseStatusEnum, AigReleaseStatusEnum> buildAdvanceTo() {
        Map<AigReleaseStatusEnum, AigReleaseStatusEnum> next =
            new EnumMap<>(AigReleaseStatusEnum.class);
        next.put(AigReleaseStatusEnum.DRAFT, AigReleaseStatusEnum.VALIDATED);
        next.put(AigReleaseStatusEnum.VALIDATED, AigReleaseStatusEnum.SANDBOX_TESTED);
        next.put(AigReleaseStatusEnum.SANDBOX_TESTED, AigReleaseStatusEnum.CANDIDATE);
        next.put(AigReleaseStatusEnum.CANDIDATE, AigReleaseStatusEnum.STABLE);
        return Collections.unmodifiableMap(next);
    }

    /**
     * 判断一次迁移是否合法。
     *
     * @param from 源状态（为 null 视为非法）
     * @param to   目标状态（为 null 视为非法）
     * @return 合法返回 true
     */
    public static boolean canTransition(AigReleaseStatusEnum from, AigReleaseStatusEnum to) {
        if (from == null || to == null) {
            return false;
        }
        return outgoing(from).contains(to);
    }

    /**
     * 取某状态的允许目标集合。
     *
     * @param from 源状态（可为 null）
     * @return 允许的目标集合；未知状态返回空集合（不返回 null）
     */
    public static Set<AigReleaseStatusEnum> outgoing(AigReleaseStatusEnum from) {
        if (from == null) {
            return Collections.emptySet();
        }
        Set<AigReleaseStatusEnum> targets = EDGES.get(from);
        return targets == null ? Collections.emptySet() : targets;
    }

    /**
     * 描述某状态允许的去处（用于报错与页面提示，不能只说「不行」）。
     *
     * @param from 源状态（可为 null）
     * @return 可读描述
     */
    public static String describeAllowed(AigReleaseStatusEnum from) {
        if (from == null) {
            return "状态为空，不允许任何迁移";
        }
        Set<AigReleaseStatusEnum> targets = outgoing(from);
        if (targets.isEmpty()) {
            return "该状态（" + from.getCode() + "：" + from.getDesc() + "）是终态，没有允许的后续状态";
        }
        StringBuilder sb = new StringBuilder("允许迁移到：");
        boolean first = true;
        for (AigReleaseStatusEnum target : targets) {
            if (!first) {
                sb.append('、');
            }
            sb.append(target.getCode()).append('（').append(target.getDesc()).append('）');
            first = false;
        }
        return sb.toString();
    }

    /**
     * 某状态是否为终态。
     *
     * @param status 状态（可为 null）
     * @return 终态返回 true；null 视为 true（空状态无可迁移）
     */
    public static boolean isTerminal(AigReleaseStatusEnum status) {
        if (status == null) {
            return true;
        }
        return status.isTerminal();
    }

    /**
     * 取全部非终态。
     *
     * @return 非终态集合（保持枚举声明顺序）
     */
    public static Set<AigReleaseStatusEnum> nonTerminalStates() {
        Set<AigReleaseStatusEnum> result = new LinkedHashSet<>();
        for (AigReleaseStatusEnum item : AigReleaseStatusEnum.values()) {
            if (!item.isTerminal()) {
                result.add(item);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * 取某状态前进到下一状态所需的<b>全部</b>门槛。
     *
     * <p>调用方据此渲染「还差哪几步」，而不是在页面上各自写一份顺序。</p>
     *
     * @param from 源状态（可为 null）
     * @return 门槛集合；该状态不接受门槛推进时返回空集合（不返回 null）
     */
    public static Set<AigReleaseGateEnum> requiredGates(AigReleaseStatusEnum from) {
        if (from == null) {
            return Collections.emptySet();
        }
        Set<AigReleaseGateEnum> gates = ADVANCE_GATES.get(from);
        return gates == null ? Collections.emptySet() : gates;
    }

    /**
     * 计算还缺哪些门槛。
     *
     * @param from        源状态
     * @param passedGates 已通过的门槛（可为 null，视为未通过任何门槛）
     * @return 尚缺的门槛集合（保持声明顺序）；无需门槛时返回空集合
     */
    public static Set<AigReleaseGateEnum> missingGates(AigReleaseStatusEnum from,
                                                       Collection<AigReleaseGateEnum> passedGates) {
        Set<AigReleaseGateEnum> required = requiredGates(from);
        if (required.isEmpty()) {
            return Collections.emptySet();
        }
        Set<AigReleaseGateEnum> passed;
        if (passedGates == null || passedGates.isEmpty()) {
            // 注意：不能写 EnumSet.copyOf(passedGates)。它对「非 EnumSet 且为空」的集合会抛
            // IllegalArgumentException，而「一个门槛都还没过」正是最常见的入参——
            // 那会在最该给出「还差哪几步」的时候抛异常。
            passed = Collections.emptySet();
        } else {
            passed = EnumSet.copyOf(passedGates);
        }
        Set<AigReleaseGateEnum> missing = new LinkedHashSet<>();
        for (AigReleaseGateEnum gate : required) {
            if (!passed.contains(gate)) {
                missing.add(gate);
            }
        }
        return Collections.unmodifiableSet(missing);
    }

    /**
     * 门槛全过则给出下一状态，否则返回 null。
     *
     * <p><b>服务层只能用这个方法推进发布状态</b>——没有「直接置 STABLE」的入口，
     * 因此跳步在结构上就做不到。返回 null 时调用方应结合
     * {@link #missingGates} 给出可读原因（还差哪几步），而不是笼统报「不满足条件」。</p>
     *
     * <p>语义边界：只要求<b>必需门槛全部满足</b>，上报集合里多出的门槛不作障碍
     * （例如调用方把历史通过的集合一并传来）。「上报集合是否与历史一致」由服务层与审计负责。</p>
     *
     * @param from        源状态（可为 null）
     * @param passedGates 已通过的门槛（可为 null）
     * @return 全过则返回下一状态；否则返回 null
     */
    public static AigReleaseStatusEnum nextIfAllGatesPassed(AigReleaseStatusEnum from,
                                                           Collection<AigReleaseGateEnum> passedGates) {
        if (from == null || from.isTerminal()) {
            return null;
        }
        Set<AigReleaseGateEnum> required = requiredGates(from);
        if (required.isEmpty()) {
            // 该状态不接受门槛推进（STABLE / DISABLED / ARCHIVED）
            return null;
        }
        if (!missingGates(from, passedGates).isEmpty()) {
            return null;
        }
        return ADVANCE_TO.get(from);
    }

}
