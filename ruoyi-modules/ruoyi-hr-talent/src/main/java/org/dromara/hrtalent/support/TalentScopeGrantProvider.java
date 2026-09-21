package org.dromara.hrtalent.support;

import org.dromara.hrtalent.enums.TalentPermissionLevelEnum;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 人才共享授权查询扩展点（SPI）。
 * <p>由领域层定义、数据访问层实现：P1 只落地领域服务骨架，P3 接入
 * {@code hr_talent_scope_grant} 后提供基于 Mapper 的实现（需过滤 {@code del_flag = '0'}、
 * {@code valid_from <= now} 且 {@code valid_to} 为空或未过期）。</p>
 *
 * <p>未提供实现时，领域服务按「无任何显式授权」处理，即 fail-safe 拒绝，
 * 不得反向放行。</p>
 *
 * @author hr-talent
 */
public interface TalentScopeGrantProvider {

    /**
     * 是否命中该人才的有效授权，且授权级别不低于要求级别。
     *
     * @param talentId      人才ID
     * @param subjects      当前用户可匹配的授权主体集合（类型 + ID 成对）
     * @param requiredLevel 要求的最低授权级别（摘要 &lt; 明细 &lt; 附件）
     * @param now           当前时间，用于授权有效期判断
     * @return 是否命中足够级别的有效授权
     */
    boolean hasActiveGrant(Long talentId, Collection<GrantSubject> subjects,
                           TalentPermissionLevelEnum requiredLevel, LocalDateTime now);

}
