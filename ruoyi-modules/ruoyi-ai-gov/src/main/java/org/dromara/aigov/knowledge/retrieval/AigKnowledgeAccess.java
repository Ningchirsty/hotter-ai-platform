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

}
