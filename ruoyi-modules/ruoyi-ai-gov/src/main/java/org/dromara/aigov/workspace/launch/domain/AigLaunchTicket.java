package org.dromara.aigov.workspace.launch.domain;

import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

/**
 * 启动票据（主文档线增量 3：prepare 的产物，存 Redis、**不落库**）。
 *
 * <h3>它承载什么</h3>
 * <p>prepare 阶段已经把"这次启动是什么"（岗位版本、卡片、目标、业务项目、请求摘要）都算清楚了；
 * 票据把这些结论带过 commit 那一步，避免 commit 时重新解析一遍而**得出与用户看到的不同的目标**
 * （用户在确认界面上看到的是 A，提交后执行 B，这类问题只有用户会发现）。</p>
 *
 * <h3>为什么票在 Redis 而不落库</h3>
 * <p>它是**短期凭证**（附件 §12：TTL 5–10 分钟），过期即无意义；落库会带来一批只用来过期的行，
 * 还要自己写清理。落库的只有 commit 之后的 {@link AigLaunchRecord}——那才是要长期回答的事实。</p>
 *
 * <h3>为什么序列化放在这个类里</h3>
 * <p>票据是一个整体：字段名一改，若编解码在两处各写一遍，就会出现"能存进去、读不出来"，
 * 而表现是**所有启动都报票据过期**。所以编解码只在这里，且有往返用例。</p>
 *
 * @param ticketId      票据ID（不透明，前端原样回传）
 * @param userId        启动人（提交者必须与 prepare 的人一致）
 * @param orgId         启动时的组织
 * @param roleCode      岗位编码
 * @param roleVersionId 岗位版本
 * @param actionCode    卡片编码
 * @param launchMode    启动方式
 * @param targetType    目标类型
 * @param targetRef     目标引用
 * @param projectType   业务域（可空）
 * @param projectId     业务项目ID（可空）
 * @param requestDigest 请求内容摘要（用于幂等冲突判定）
 * @param expiresAt     过期时刻
 * @author ai-gov
 */
public record AigLaunchTicket(
    String ticketId,
    Long userId,
    Long orgId,
    String roleCode,
    Long roleVersionId,
    String actionCode,
    String launchMode,
    String targetType,
    String targetRef,
    String projectType,
    Long projectId,
    String requestDigest,
    LocalDateTime expiresAt) {

    /**
     * 票据序列化（Jackson 3）
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * 票据是否已过期。
     *
     * @param now 判定时刻
     * @return 已过期返回 true（过期时刻为空时按"永不过期"处理由调用方决定，这里视为不过期）
     */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && now != null && now.isAfter(expiresAt);
    }

    /**
     * 序列化成 JSON。
     *
     * @return JSON
     */
    public String toJson() {
        return MAPPER.writeValueAsString(this);
    }

    /**
     * 从 JSON 还原。
     *
     * @param json JSON（可空）
     * @return 票据；空串返回 null
     * @throws IllegalArgumentException JSON 非法（不能"当作没有票据"静默处理——那会表现为票据过期）
     */
    public static AigLaunchTicket fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, AigLaunchTicket.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("启动票据无法解析：" + e.getMessage(), e);
        }
    }

}
