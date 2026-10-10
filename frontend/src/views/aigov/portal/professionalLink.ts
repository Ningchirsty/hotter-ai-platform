/**
 * 岗位工作台 ↔ 专业台 的跳转与回跳（主文档线增量 4；附件 §11 回跳约定）。
 *
 * <h3>为什么必须把 taskId 写进 **URL query**，而不是路由 query</h3>
 * <p>创作域的几个页面（project / production / review / dna / storyboard）读的是
 * {@code new URLSearchParams(location.search).get('taskId')}，**不是** {@code route.query}。
 * 本项目用的是 history 路由，所以 {@code /video?taskId=123} 里的确会出现在 {@code location.search} 上；
 * 但如果有人用"先 push 到页面、再设置 query"或改用 hash 路由，那几个页面就会**静默拿不到 taskId**——
 * 表现是"从岗位工作台点进去，专业台没有带上这次启动"，而没有任何报错。</p>
 * <p>因此这里把"构造带 query 的 URL"做成唯一入口，并由用例钉住形态。</p>
 */

import type { AigPortalActionVO } from '@/api/aigov/portal/types';
import { resolveAiWorkspaceRoute } from '@/config/aiWorkspaceRouteRegistry';

/** 岗位工作台路径（回跳目标） */
export const WORKSPACE_PATH = '/ai-workspace';

/**
 * 专业页路径解析（只认白名单）。
 *
 * <ul>
 *     <li>{@code NAVIGATION}：目标引用本身就是 routeKey；</li>
 *     <li>{@code STUDIO}：专业页跳转键是 {@code studioRouteKey}。</li>
 * </ul>
 * <p>两种都要过白名单（`resolveAiWorkspaceRoute` 解析不到返回 null），**不许猜路径**——
 * 猜路径的表现是"点了跳到一个不存在的页面"。</p>
 *
 * @param action 卡片
 * @returns 专业页路径；非专业页卡片或不在白名单时返回 null
 */
export function resolveProfessionalPath(action?: AigPortalActionVO): string | null {
  if (!action) {
    return null;
  }
  if (action.launchMode === 'STUDIO') {
    return resolveAiWorkspaceRoute(action.studioRouteKey)?.path ?? null;
  }
  if (action.targetType === 'NAVIGATION' || action.launchMode === 'NAVIGATION') {
    return resolveAiWorkspaceRoute(action.targetRef)?.path ?? null;
  }
  return null;
}

/** 标记"这次是从岗位工作台来的"（专业页据此决定要不要显示返回入口） */
export const FROM_WORKSPACE_FLAG = 'ai-workspace';

/**
 * 构造专业台 URL（带 query）。
 *
 * @param path 专业页路径（必须以内置白名单解析出来的路径为准，不要拼字符串）
 * @param params 参数（空值会被忽略）
 * @returns 形如 `/video?taskId=1&from=ai-workspace`
 */
export function buildProfessionalUrl(
  path: string,
  params: { taskId?: string | number; roleCode?: string; actionCode?: string }
): string {
  const query = new URLSearchParams();
  if (params.taskId !== undefined && params.taskId !== null && String(params.taskId) !== '') {
    query.set('taskId', String(params.taskId));
  }
  if (params.roleCode) {
    query.set('roleCode', params.roleCode);
  }
  if (params.actionCode) {
    query.set('actionCode', params.actionCode);
  }
  query.set('from', FROM_WORKSPACE_FLAG);
  return path + '?' + query.toString();
}

/**
 * 从专业页的 URL query 读出回跳所需的上下文。
 *
 * @param search {@code location.search}（可空）
 * @returns 上下文；没有 taskId 时 taskId 为空字符串
 */
export function readLaunchContext(search?: string): {
  taskId: string;
  roleCode: string;
  actionCode: string;
  fromWorkspace: boolean;
} {
  const query = new URLSearchParams(search || '');
  return {
    taskId: query.get('taskId') || '',
    roleCode: query.get('roleCode') || '',
    actionCode: query.get('actionCode') || '',
    fromWorkspace: query.get('from') === FROM_WORKSPACE_FLAG
  };
}

/**
 * 构造"返回岗位工作台"的地址。
 *
 * @param context 回跳上下文
 * @returns 形如 `/ai-workspace?role=GRAPHIC_DESIGNER_AI&action=A1`
 */
export function buildWorkspaceBackUrl(context: { roleCode?: string; actionCode?: string }): string {
  const query = new URLSearchParams();
  if (context.roleCode) {
    query.set('role', context.roleCode);
  }
  if (context.actionCode) {
    query.set('action', context.actionCode);
  }
  const queryString = query.toString();
  return queryString ? WORKSPACE_PATH + '?' + queryString : WORKSPACE_PATH;
}
