package org.dromara.aigov.agent.domain.vo;

import java.util.List;

/**
 * Package 停用结果（把该 Package 版本带进来的 Agent/Skill 版本批量下线）。
 *
 * <p><b>为什么成功项与跳过项要分成两个清单</b>：停用是一次<b>批量</b>动作，而批量动作最容易
 * 变成「报了个成功、其实有一半没做」。这里把「真的改了状态的」与「没动的」分开列，
 * 且每个跳过项都必须带 {@code reason}——页面上因此能回答「为什么这个没停掉」，
 * 而不是只显示一句笼统的「部分成功」。</p>
 *
 * @param packageId        Package ID
 * @param packageVersionId Package 版本ID
 * @param alreadyDisabled  本次是否为重复调用（幂等命中：带进来的版本全都已在停用状态）
 * @param disabled         本次真正被停用的版本
 * @param skipped          本次未改动的版本（已在停用中、或已是终态）
 * @param note             可读说明
 * @author ai-gov
 */
public record AigPackageDisableVo(
    Long packageId,
    Long packageVersionId,
    boolean alreadyDisabled,
    List<DisabledItem> disabled,
    List<SkippedItem> skipped,
    String note
) {

    /**
     * 一个被停用的版本。
     *
     * @param targetType 对象类型（AGENT_VERSION / SKILL_VERSION）
     * @param code       编码（Agent/Skill 编码）
     * @param parentId   aig_agent.agent_id / aig_skill.skill_id
     * @param versionId  aig_agent_version.agent_version_id / aig_skill_version.skill_version_id
     * @param version    版本号
     * @param fromStatus 停用前的状态（<b>必须留下</b>：把一个 STABLE 版本下线与把一个 DRAFT 版本
     *                   下线，影响面完全不同，只记「已停用」会把这件事抹平）
     */
    public record DisabledItem(String targetType, String code, Long parentId, Long versionId,
                               String version, String fromStatus) {
    }

    /**
     * 一个未改动的版本。
     *
     * @param targetType 对象类型
     * @param code       编码
     * @param parentId   aig_agent.agent_id / aig_skill.skill_id
     * @param versionId  版本ID
     * @param version    版本号
     * @param fromStatus 当前状态
     * @param reason     未改动的原因（已停用 / 终态不可停用 / 状态为空）
     */
    public record SkippedItem(String targetType, String code, Long parentId, Long versionId,
                              String version, String fromStatus, String reason) {
    }

}
