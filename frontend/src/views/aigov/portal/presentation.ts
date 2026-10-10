import type { AigPortalActionVO, AigPortalCategoryVO } from '@/api/aigov/portal/types';
import { resolveAiWorkspaceRoute } from '@/config/aiWorkspaceRouteRegistry';

/**
 * 员工 AI 工作台的展示层纯函数（主文档线增量 2）。
 *
 * 这里只做"怎么显示"与"哪个入口现在真的能走"：
 * - 可见性（哪些岗位、哪些卡片）由**服务端**决定，前端不再判断；
 * - "哪些卡片现在能打开"是前端的事，但它必须<b>只认白名单</b>（{@link resolveAiWorkspaceRoute}
 *   解析不到就返回 null），不许猜路径——猜路径的表现是"点了跳到一个不存在的页面"。
 */

/** 启动方式 → 显示文案 */
const LAUNCH_MODE_LABELS: Record<string, string> = {
  QUICK: '快速能力',
  FORM: '表单',
  STUDIO: '专业台',
  NAVIGATION: '页面'
};

/**
 * 启动方式的显示文案。
 *
 * @param code 启动方式
 * @returns 文案；未知原样显示
 */
export function launchModeLabel(code?: string): string {
  if (!code) {
    return '未指定';
  }
  return LAUNCH_MODE_LABELS[code] ?? code;
}

/**
 * 非导航类卡片现在的说明文案。
 *
 * <p>增量 2 是**只读门户**：真正启动能力（QUICK/FORM/STUDIO/SCENARIO）要走启动凭证，
 * 那是增量 3 的 Launch Resolver。界面必须<b>如实说明</b>，而不是给一个点了没反应的按钮——
 * "按钮在但没反应"对员工来说就是"系统坏了"。</p>
 */
export const START_PENDING_HINT = '启动将在接入启动凭证后开放（当前为只读门户）';

/**
 * 这张卡片现在能否直接打开页面。
 *
 * @param action 卡片
 * @returns 可直接打开返回 true
 */
export function isNavigable(action?: AigPortalActionVO): boolean {
  return action?.targetType === 'NAVIGATION' || action?.launchMode === 'NAVIGATION';
}

/**
 * 导航类卡片的前端路径（只认白名单）。
 *
 * @param action 卡片
 * @returns 路径；非导航、或 routeKey 不在白名单时返回 null（调用方据此禁用入口）
 */
export function navigationPath(action?: AigPortalActionVO): string | null {
  if (!isNavigable(action)) {
    return null;
  }
  const target = resolveAiWorkspaceRoute(action?.targetRef);
  return target?.path ?? null;
}

/**
 * 按分类归拢卡片。
 *
 * 分类顺序按**清单里声明的顺序**（那是配置者排的），分类内按 sortOrder 再按编码。
 * 没有卡片的分类也会返回一条空组——"这栏暂时是空的"是员工该知道的事实；
 * 直接抹掉会让人以为岗位配错了。
 *
 * @param categories 分类
 * @param actions 卡片
 * @returns 分组结果
 */
export function groupPortalActions(
  categories: AigPortalCategoryVO[] | undefined,
  actions: AigPortalActionVO[] | undefined
): { code: string; name: string; actions: AigPortalActionVO[] }[] {
  const list = actions ?? [];
  const groups: { code: string; name: string; actions: AigPortalActionVO[] }[] = [];
  const seen = new Set<string>();
  for (const category of categories ?? []) {
    if (!category?.code) {
      continue;
    }
    seen.add(category.code);
    groups.push({
      code: category.code,
      name: category.name || category.code,
      actions: sortActions(list.filter(item => item.categoryCode === category.code))
    });
  }
  // 卡片挂在清单里没有的分类上（服务端会过滤掉，但万一还是显示出来，不能凭空消失）
  const orphans = list.filter(item => item.categoryCode && !seen.has(item.categoryCode));
  if (orphans.length > 0) {
    groups.push({
      code: '（未在清单中声明的分类）',
      name: '（未在清单中声明的分类）',
      actions: sortActions(orphans)
    });
  }
  return groups;
}

/**
 * 分类内排序：先 sortOrder，再编码。
 *
 * @param actions 卡片
 * @returns 新数组
 */
function sortActions(actions: AigPortalActionVO[]): AigPortalActionVO[] {
  return actions.toSorted((a, b) => {
    const left = a.sortOrder ?? 0;
    const right = b.sortOrder ?? 0;
    if (left !== right) {
      return left - right;
    }
    return (a.actionCode || '').localeCompare(b.actionCode || '');
  });
}

/**
 * 任务状态的展示（服务端给了中文就直接用，避免前端另写一份映射）。
 *
 * @param status 状态码
 * @param statusLabel 服务端给的中文
 * @returns 文案与标签色；未知状态原样显示并标灰（不是默认成"成功"）
 */
export function taskStatusMeta(
  status?: string,
  statusLabel?: string
): { label: string; tag: 'info' | 'warning' | 'success' | 'danger' } {
  if (statusLabel) {
    return { label: statusLabel, tag: tagOf(status) };
  }
  if (!status) {
    return { label: '未知', tag: 'info' };
  }
  return { label: status, tag: tagOf(status) };
}

/**
 * 状态 → 标签色（只影响颜色；文案认服务端的）。
 *
 * @param status 状态码
 * @returns 标签色
 */
function tagOf(status?: string): 'info' | 'warning' | 'success' | 'danger' {
  switch (status) {
    case 'SUCCEEDED':
      return 'success';
    case 'FAILED':
    case 'CANCELED':
      return 'danger';
    case 'RUNNING':
    case 'PENDING':
    case 'DISPATCHED':
      return 'warning';
    default:
      return 'info';
  }
}
