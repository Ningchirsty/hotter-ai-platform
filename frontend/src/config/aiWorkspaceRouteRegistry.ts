/**
 * 岗位工作台的 routeKey 白名单（**前端这一侧的唯一真相**）。
 *
 * 为什么必须是"白名单"而不是"数据库下发什么就打开什么"：
 * 岗位包（RolePackage）里的 Action 会带一个 `studio.routeKey`。如果直接按它跳转，
 * 就等于**让数据库里的一行配置决定浏览器去打开哪个组件**——一条被写坏的配置可以指向任意路径。
 * 所以这里列出**允许被下发的键**，运行时用 `resolveAiWorkspaceRoute` 解析；
 * 解析不到就明确报"这个入口不可用"，而不是猜一个路径。
 *
 * 与后端的契约：后端 `AigRouteKeyRegistry` 的键集合必须与本文件**完全一致**，
 * 由 `AigRouteKeyRegistryContractTest` 在构建期锁死（跑 CI 就会红）。
 * 每个 `path` 还必须能在菜单脚本里找到对应的组件（`<path 去掉斜杠>/index`），
 * 否则"入口能点、点进去空白"。
 *
 * @author ai-gov
 */

/** 一个可被下发的跳转目标 */
export interface AiWorkspaceRouteTarget {
  /** 目标路由名（前端内置路由） */
  routeName: string;
  /** 目标路径（与菜单组件的目录一致） */
  path: string;
  /** 是否需要先校验项目访问权（进入前由服务端再校验一次） */
  requireProjectAccess: boolean;
}

/** routeKey → 跳转目标 */
export const aiWorkspaceRouteRegistry: Record<string, AiWorkspaceRouteTarget> = {
  CREATIVE_PROJECT: { routeName: 'CreativeProject', path: '/creative/project', requireProjectAccess: true },
  CREATIVE_PRODUCTION: {
    routeName: 'CreativeProduction',
    path: '/creative/production',
    requireProjectAccess: true
  },
  CREATIVE_REVIEW: { routeName: 'CreativeReview', path: '/creative/review', requireProjectAccess: true },
  VIDEO_STUDIO: { routeName: 'VideoStudio', path: '/video', requireProjectAccess: false },
  CONTENT_TASK: { routeName: 'ContentTask', path: '/content/task', requireProjectAccess: false },
  AIGOV_TASK: { routeName: 'AigovTask', path: '/aigov/task', requireProjectAccess: false }
};

/**
 * 解析 routeKey；不在白名单里返回 null（调用方据此显示"入口不可用"，不要猜路径）。
 *
 * @param key routeKey
 * @returns 目标或 null
 */
export function resolveAiWorkspaceRoute(key?: string | null): AiWorkspaceRouteTarget | null {
  if (!key) return null;
  return aiWorkspaceRouteRegistry[key] ?? null;
}

/**
 * 所有允许的 routeKey（供测试与页面提示使用）。
 *
 * @returns 键列表
 */
export function aiWorkspaceRouteKeys(): string[] {
  return Object.keys(aiWorkspaceRouteRegistry);
}
