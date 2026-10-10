package org.dromara.aigov.workspace.domain;

import java.util.List;

/**
 * 岗位包草稿的**校验输入**（附件 §5.2 岗位包示例的强类型化）。
 *
 * <p><b>为什么用 record 而不是实体</b>：校验的输入是"一份还没入库的岗位包描述"，
 * 它没有主键、没有审计字段，也不该被持久化框架注解污染。用不可变值对象，
 * 校验就成了纯函数——这正是它需要被逐条单测的原因（配错了不会报错，只会跑出错误的行为）。</p>
 *
 * @param roleCode        岗位编码（跨版本稳定）
 * @param roleName        岗位名称
 * @param version         版本号（SemVer 风格）
 * @param categories      分类（随版本）
 * @param defaultCategory 默认分类（可空；非空时必须在 categories 里）
 * @param actions         能力卡片
 * @param audienceScope   可见范围（如 ASSIGNED_ORG）
 * @param defaultDataLevel 默认数据等级
 * @param maxDataLevel     允许的最高数据等级（**只能收紧**：不得比默认等级还宽）
 * @author ai-gov
 */
public record AigRolePackageDraft(
    String roleCode,
    String roleName,
    String version,
    List<CategoryDraft> categories,
    String defaultCategory,
    List<ActionDraft> actions,
    String audienceScope,
    String defaultDataLevel,
    String maxDataLevel) {

    /**
     * 分类。
     *
     * @param code 分类编码
     * @param name 分类名称
     */
    public record CategoryDraft(String code, String name) {
    }

    /**
     * 能力卡片（一个 Action 只引用一个确定的启动目标）。
     *
     * @param code            卡片编码（同版本内唯一）
     * @param categoryCode    所属分类
     * @param title           标题（业务名）
     * @param launchMode      启动方式（{@code AigActionLaunchModeEnum} 的 code）
     * @param targetType      目标类型（{@code AigLaunchTargetTypeEnum} 的 code）
     * @param targetRef       目标引用（SCENARIO 时是 {@code scenario://code@version}；NAVIGATION 时是 routeKey）
     * @param studioRouteKey  STUDIO 模式的专业页跳转键
     * @param requiredContext 所需上下文键（逗号分隔，白名单）
     * @param enabled         是否启用
     */
    public record ActionDraft(String code, String categoryCode, String title, String launchMode,
                              String targetType, String targetRef, String studioRouteKey,
                              String requiredContext, boolean enabled) {
    }

}
