package org.dromara.aigov.workspace.helper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 岗位工作台的 routeKey 白名单（**后端这一侧的唯一真相**）。
 *
 * <p><b>为什么要有这份白名单</b>：岗位包的 Action 带一个 {@code routeKey}，
 * 门户据此跳转。若直接按数据库里的字符串跳，就等于<b>让一行配置决定浏览器打开哪个组件</b>——
 * 写坏一行就能指向任意路径。所以只允许这里列出的键，且每个键的目标必须真实存在。</p>
 *
 * <p><b>与前端锁死</b>：前端的 {@code frontend/src/config/aiWorkspaceRouteRegistry.ts} 是同一份名单，
 * 由 {@code AigRouteKeyRegistryContractTest} 在构建期比对<b>键集合完全一致</b>；
 * 该测试还要求每个目标路径能在菜单脚本里找到对应组件（{@code <path 去掉斜杠>/index}），
 * 否则会出现"入口能点、点进去空白"。</p>
 *
 * @author ai-gov
 */
public final class AigRouteKeyRegistry {

    /**
     * 一个可被下发的跳转目标。
     *
     * @param routeKey             白名单键
     * @param frontendRouteName    前端路由名
     * @param frontendPath         前端路径（与菜单组件目录一致）
     * @param requireProjectAccess 是否需要先校验项目访问权
     */
    public record RouteTarget(String routeKey, String frontendRouteName, String frontendPath,
                              boolean requireProjectAccess) {
    }

    /**
     * 白名单（插入顺序即展示顺序）
     */
    private static final Map<String, RouteTarget> TARGETS;

    static {
        Map<String, RouteTarget> targets = new LinkedHashMap<>();
        register(targets, "CREATIVE_PROJECT", "CreativeProject", "/creative/project", true);
        register(targets, "CREATIVE_PRODUCTION", "CreativeProduction", "/creative/production", true);
        register(targets, "CREATIVE_REVIEW", "CreativeReview", "/creative/review", true);
        register(targets, "VIDEO_STUDIO", "VideoStudio", "/video", false);
        register(targets, "CONTENT_TASK", "ContentTask", "/content/task", false);
        register(targets, "AIGOV_TASK", "AigovTask", "/aigov/task", false);
        TARGETS = Collections.unmodifiableMap(targets);
    }

    private AigRouteKeyRegistry() {
    }

    /**
     * 登记一个目标。
     *
     * @param targets     目标表
     * @param key         routeKey
     * @param routeName   前端路由名
     * @param path        前端路径
     * @param needProject 是否需要项目访问权
     */
    private static void register(Map<String, RouteTarget> targets, String key, String routeName,
                                 String path, boolean needProject) {
        targets.put(key, new RouteTarget(key, routeName, path, needProject));
    }

    /**
     * 该 routeKey 是否在白名单里。
     *
     * @param routeKey 键
     * @return 在白名单里返回 true
     */
    public static boolean contains(String routeKey) {
        return routeKey != null && TARGETS.containsKey(routeKey.trim());
    }

    /**
     * 查目标。
     *
     * @param routeKey 键
     * @return 目标；不在白名单返回 null（调用方据此拒绝，不要猜路径）
     */
    public static RouteTarget find(String routeKey) {
        return routeKey == null ? null : TARGETS.get(routeKey.trim());
    }

    /**
     * 全部允许的键。
     *
     * @return 键集合（不可变）
     */
    public static Set<String> keys() {
        return TARGETS.keySet();
    }

    /**
     * 全部目标（按登记顺序）。
     *
     * @return 目标集合（不可变）
     */
    public static Set<RouteTarget> all() {
        return Set.copyOf(TARGETS.values());
    }

}
