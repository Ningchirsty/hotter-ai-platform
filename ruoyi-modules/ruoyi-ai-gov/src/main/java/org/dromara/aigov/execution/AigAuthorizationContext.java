package org.dromara.aigov.execution;

import java.io.Serializable;

/**
 * 冻结进执行计划的授权上下文（设计 §7.1「身份授权 + 版本 + 输入 + 有效策略」、§7.2「逐层相交」）。
 *
 * <p>它回答的是"这次运行<b>以谁的身份、在哪个岗位卡片下</b>发生"。把身份与卡片冻结进计划，
 * 才能让下游（知识检索、工具执行）把"身份/卡片范围"也纳入相交，而不是只按版本范围放行——
 * 只在版本层收窄，等于把"谁在用"这一层整个丢掉。</p>
 *
 * <p>取不到（任务不是由岗位卡片启动、或启动记录已不存在）时为 {@code null}，
 * 表示"本次没有岗位/身份上下文"，而不是"身份未知可以放宽"。</p>
 *
 * @param callerId      启动人（来自启动记录，不由请求体自报）
 * @param orgId         组织（启动记录里的 orgId）
 * @param roleCode      岗位编码
 * @param roleVersionId 岗位版本（用户确认的是这一版配置）
 * @param actionCode    卡片编码
 * @author ai-gov
 */
public record AigAuthorizationContext(Long callerId, Long orgId, String roleCode,
                                      Long roleVersionId, String actionCode) implements Serializable {
}
