package org.dromara.aigov.workspace.portal.domain;

import java.util.List;

/**
 * 启动链路需要的"确切的卡片"（主文档线增量 3）。
 *
 * <p><b>为什么由门户服务给出，而不是启动服务自己再解析一遍</b>：可见性与"哪一张卡片算可用"
 * 只有一处判定。两份实现的失效方式是"门户里看得到、启动说不可用"（或反过来）——
 * 而这类不一致只有用户能发现。所以启动链路问门户服务要这个值对象，
 * 而不是自己写一遍过滤。</p>
 *
 * @param roleVersionId      岗位版本（写进启动记录：用户确认的是这一版配置）
 * @param roleId             岗位定义ID
 * @param roleCode           岗位编码
 * @param version            版本号
 * @param actionCode         卡片编码
 * @param title              卡片标题
 * @param launchMode         启动方式
 * @param targetType         目标类型
 * @param targetRef          目标引用
 * @param studioRouteKey     STUDIO 的专业页跳转键
 * @param requiredContextKeys 卡片声明的上下文键
 * @author ai-gov
 */
public record AigPortalActionContext(
    Long roleVersionId,
    Long roleId,
    String roleCode,
    String version,
    String actionCode,
    String title,
    String launchMode,
    String targetType,
    String targetRef,
    String studioRouteKey,
    List<String> requiredContextKeys) {
}
