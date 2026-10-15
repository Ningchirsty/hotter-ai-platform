package org.dromara.aigov.knowledge.retrieval;

import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.aigov.knowledge.enums.AigKnowledgeStatusEnum;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 知识检索的授权与有效期判据（设计 §7.4、§8.4）。
 *
 * <p>抽成纯函数的原因：授权是"越权片段会不会出现"的唯一开关，必须能逐条单测。
 * 它<b>不查库</b>——调用方把已经算好的授权范围传进来。</p>
 *
 * @author ai-gov
 */
public final class AigKnowledgeAccess {

    private AigKnowledgeAccess() {
    }

    /**
     * 该版本此刻是否可被检索。
     *
     * <p>三条同时满足：①版本状态为 {@code READY}；②在有效期内（闭区间，两端为空表示该侧不限）；
     * ③版本属于允许的集合（{@code allowedCollectionCodes} 含该版本的集合编码）。</p>
     *
     * @param version                版本
     * @param allowedVersionIds      允许的版本ID（可空：非空时也要求命中）
     * @param allowedCollectionCodes 允许的集合编码
     * @param collectionCode         该版本所属集合的编码
     * @param now                    判定时刻
     * @return 可检索返回 true
     */
    public static boolean retrievable(AigKnowledgeDocumentVersion version,
                                      Set<Long> allowedVersionIds,
                                      Set<String> allowedCollectionCodes,
                                      String collectionCode,
                                      LocalDateTime now) {
        if (version == null) {
            return false;
        }
        AigKnowledgeStatusEnum status = AigKnowledgeStatusEnum.find(version.getStatus());
        if (status == null || !status.retrievable()) {
            return false;
        }
        if (allowedCollectionCodes == null || collectionCode == null
            || !allowedCollectionCodes.contains(collectionCode)) {
            return false;
        }
        if (allowedVersionIds != null && !allowedVersionIds.isEmpty()
            && !allowedVersionIds.contains(version.getVersionId())) {
            return false;
        }
        LocalDateTime time = now == null ? LocalDateTime.now() : now;
        if (version.getEffectiveFrom() != null && time.isBefore(version.getEffectiveFrom())) {
            return false;
        }
        // 闭区间：截止时刻本身仍有效（与既有有效期口径一致，设计 §8.4）
        return version.getEffectiveTo() == null || !time.isAfter(version.getEffectiveTo());
    }

    /**
     * 组织范围是否允许（设计 §7.2「登录身份范围 ∩ …」）。
     *
     * <p>口径三条：</p>
     * <ul>
     *     <li>{@code orgId} 为空 → <b>不做该层收窄</b>（如知识维护人的检索测试入口，
     *         它本来就要按显式选择的集合测试；执行路径一定带身份，因此这不算放宽）；</li>
     *     <li>{@code orgScope} 为空 → 该集合不限组织，允许；</li>
     *     <li>否则按字符串等值匹配（当前 {@code org_scope} 是字符串列，尚无结构化组织字段）。</li>
     * </ul>
     *
     * @param orgScope 集合的组织范围（可空）
     * @param orgId    调用方组织ID（可空）
     * @return 允许返回 true
     */
    public static boolean orgAllowed(String orgScope, Long orgId) {
        if (orgId == null) {
            return true;
        }
        if (orgScope == null || orgScope.isBlank()) {
            return true;
        }
        return orgScope.trim().equalsIgnoreCase(String.valueOf(orgId));
    }

}
