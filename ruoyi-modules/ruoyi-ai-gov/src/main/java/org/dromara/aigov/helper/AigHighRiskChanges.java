package org.dromara.aigov.helper;

import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.bo.AigRoutePolicyBo;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.common.core.utils.StringUtils;

/**
 * 「这次变更算不算高风险」（Q1C 轻量门槛的规则本身，纯函数）。
 *
 * <p><b>为什么要单独抽一个纯函数</b>：判据必须能被逐条钉住，而"服务里夹着权限判定"没法这么测。
 * 更实际的理由是：同一条规则要在**新增 / 编辑 / 删除**三个入口用，而且编辑时要看"改之前那一行"
 * （{@code N→Y} 只有对比新旧才看得出来）。写在三个地方迟早分叉。</p>
 *
 * <h3>规则（对应联动方案 §8.1，本轮落地的部分）</h3>
 * <ul>
 *     <li><b>放开外发</b>：目标行 {@code allow_external='Y'}（新增就是放开；编辑时 {@code N→Y} 同理）；</li>
 *     <li><b>限制级 / 严格级</b>：目标行的数据等级 ∈ {RESTRICTED, STRICT}
 *         （这两个等级哪怕不放开外发，改策略也会改变"这些数据能不能被处理"）；</li>
 *     <li><b>钉到外部部署</b>：目标行的 {@code preferred_deployment} 是外部部署类型；</li>
 *     <li><b>删除</b>：被删行命中上面任一条 —— 删掉一条"仅本地"的限制，效果与放开外发一样。</li>
 * </ul>
 *
 * <p><b>明确不在本轮规则里的</b>（如实标注，避免以为已经全防住）：
 * 联动方案 §8.1 第 4 条里"限制级/严格级**能力**的候选变更（新增/改用途/启停）"依赖能力的
 * 声明等级列（Q2C 的 {@code min_data_level}），该列尚不存在，所以候选变更本轮不拦——
 * 留到 Q2C 一起做，届时能按能力声明等级判定。</p>
 *
 * @author ai-gov
 */
public final class AigHighRiskChanges {

    /**
     * 数据等级：限制级。
     */
    private static final String LEVEL_RESTRICTED = "RESTRICTED";

    /**
     * 外发允许值。
     */
    private static final String ALLOW_EXTERNAL_YES = "Y";

    private AigHighRiskChanges() {
    }

    /**
     * 一行策略（按它自身的取值）是否属于高风险。
     *
     * @param dataLevel           数据等级（可空）
     * @param allowExternal       是否允许外发（可空）
     * @param preferredDeployment 首选部署类型（可空）
     * @return 高风险返回 true
     */
    public static boolean policyRow(String dataLevel, String allowExternal, String preferredDeployment) {
        if (ALLOW_EXTERNAL_YES.equalsIgnoreCase(trim(allowExternal))) {
            return true;
        }
        AigDataLevelEnum level = AigDataLevelEnum.find(upper(dataLevel));
        if (level != null && level.getRank() >= AigDataLevelEnum.RESTRICTED.getRank()) {
            return true;
        }
        return isExternalDeployment(preferredDeployment);
    }

    /**
     * 新增策略是否高风险（只有目标行可看）。
     *
     * @param bo 入参
     * @return 高风险返回 true
     */
    public static boolean forNewPolicy(AigRoutePolicyBo bo) {
        if (bo == null) {
            return false;
        }
        return policyRow(bo.getDataLevel(), bo.getAllowExternal(), bo.getPreferredDeployment());
    }

    /**
     * 编辑策略是否高风险：<b>改之前那一行与改之后的目标行，任一命中就算</b>。
     *
     * <p>为什么要看"改之前"：把 {@code allow_external} 从 {@code N} 改成 {@code Y} 时，
     * 只看新值（Y）已能命中；但把一整行"仅本地"的限制改成别的、或把 {@code preferred_deployment}
     * 从外部改回内部，属于收紧（不是高风险）——所以用"新旧任一命中"这条最保守的口径，
     * 既不放过放宽，也不因为改个备注就要求高风险权限（备注不影响这两列）。</p>
     *
     * @param existing 改之前的行（可空）
     * @param bo       入参
     * @return 高风险返回 true
     */
    public static boolean forUpdatedPolicy(AigRoutePolicy existing, AigRoutePolicyBo bo) {
        if (existing != null && policyRow(existing.getDataLevel(), existing.getAllowExternal(),
            existing.getPreferredDeployment())) {
            return true;
        }
        if (bo == null) {
            return false;
        }
        // 目标行的取值：bo 里没给就沿用现有（PATCH 式更新）
        String dataLevel = StringUtils.isBlank(bo.getDataLevel())
            ? (existing == null ? null : existing.getDataLevel()) : bo.getDataLevel();
        String allowExternal = StringUtils.isBlank(bo.getAllowExternal())
            ? (existing == null ? null : existing.getAllowExternal()) : bo.getAllowExternal();
        String preferred = StringUtils.isBlank(bo.getPreferredDeployment())
            ? (existing == null ? null : existing.getPreferredDeployment()) : bo.getPreferredDeployment();
        return policyRow(dataLevel, allowExternal, preferred);
    }

    /**
     * 删除策略是否高风险：被删行命中规则即算（删掉"仅本地"的效果与放开外发相同）。
     *
     * @param existing 被删的行
     * @return 高风险返回 true
     */
    public static boolean forRemovedPolicy(AigRoutePolicy existing) {
        return existing != null && policyRow(existing.getDataLevel(), existing.getAllowExternal(),
            existing.getPreferredDeployment());
    }

    /**
     * 能力从「启用」改成「停用」是否高风险（停用会让所有调用被拒，且是在跑的能力）。
     *
     * <p>只判"启用 → 停用"这一个迁移：对已经停用的能力再存一次（改个描述）不该被要求高风险权限——
     * 那会让人以为"这个权限点是随机拦人的"。</p>
     *
     * @param existingStatus 改之前的状态（0 正常 / 1 停用）
     * @param targetStatus   目标状态（可空 = 不改）
     * @return 高风险返回 true
     */
    public static boolean forCapabilityDisable(String existingStatus, String targetStatus) {
        boolean wasDisabled = "1".equals(trim(existingStatus));
        boolean willBeDisabled = "1".equals(trim(targetStatus));
        return !wasDisabled && willBeDisabled;
    }

    /**
     * 限制级/严格级能力（按其声明的 {@code min_data_level}）的**候选变更**是否高风险。
     *
     * <p>对应联动方案 §8.1 第 4 条：这类能力的候选决定"限制级数据能落到哪个模型"，
     * 所以新增/改用途改优先级/启停/删除都必须有高风险权限。</p>
     *
     * <p>判据是 Q2C 新增的 {@code aig_capability.min_data_level}；没有该列之前只能拿策略行猜，
     * 会误报——这也是这条规则留到 Q2C 一起做的原因。</p>
     *
     * @param capabilityMinDataLevel 能力声明的最低数据等级（可空 = 不声明）
     * @return 需要高风险权限返回 true
     */
    public static boolean forCandidateChange(String capabilityMinDataLevel) {
        AigDataLevelEnum level = AigDataLevelEnum.find(upper(capabilityMinDataLevel));
        return level != null && level.getRank() >= AigDataLevelEnum.RESTRICTED.getRank();
    }

    /**
     * 能力声明的最低数据等级被**调低或取消**是否高风险。
     *
     * <p>声明等级是"防业务方标低等级"的那道锁。<b>调高</b>（甚至从无到有）是收紧，不需要额外权限；
     * <b>调低或清空</b>等于把这把锁松开（例如 RESTRICTED → INTERNAL、RESTRICTED → 空），
     * 与"放宽数据出界"同一类风险，因此同样要求高风险权限。</p>
     *
     * <p>本来就没声明（{@code existingLevel} 为空）时不算高风险：那时谈不上"调低"。</p>
     *
     * @param existingLevel 改之前的声明（可空）
     * @param targetLevel   目标声明（可空 = 取消声明）
     * @return 需要高风险权限返回 true
     */
    public static boolean forMinDataLevelChange(String existingLevel, String targetLevel) {
        AigDataLevelEnum before = AigDataLevelEnum.find(upper(existingLevel));
        if (before == null) {
            return false;
        }
        AigDataLevelEnum after = AigDataLevelEnum.find(upper(targetLevel));
        int afterRank = after == null ? -1 : after.getRank();
        return afterRank < before.getRank();
    }

    /**
     * 是否外部部署类型。
     *
     * @param deploymentType 部署类型（可空）
     * @return 外部返回 true
     */
    public static boolean isExternalDeployment(String deploymentType) {
        AigDeploymentTypeEnum deployment = AigDeploymentTypeEnum.find(upper(deploymentType));
        return deployment != null && deployment.isExternal();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(java.util.Locale.ROOT);
    }
}
