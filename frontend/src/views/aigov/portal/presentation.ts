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
 * <p>增量 3 之后启动链路已接入：点「启动」会打开启动表单（预检 → 确认）。
 * 这段文案现在只出现在启动表单的说明里，不再表示"不能启动"。</p>
 */
export const START_PENDING_HINT = '启动会先预检：不通过不会发放票据，也就不会真的建任务';

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

/* ------------------------------------------------------------------ *
 * 启动表单（增量 3b）
 * ------------------------------------------------------------------ */

/**
 * 平台任务类型（与后端 `AigTaskTypeEnum` 一致）。
 *
 * <p><b>权威判定仍在服务端</b>：后端对任务类型做封闭集合校验。这里列出来只是为了让用户能选，
 * 而不是把一个不认识的类型提交上去等报错——但如果两边哪天不一致，**服务端会拒绝**，
 * 界面拿到的是一条明确的"必填输入缺失"。</p>
 */
export const TASK_TYPE_OPTIONS = [
  { code: 'PLAN_GENERATION', label: '生成规划（Page Spec/Brief/Prompt Plan）' },
  { code: 'VISUAL_DNA_ANALYSIS', label: '视觉基因分析' },
  { code: 'TEXT_GENERATION', label: '文本生成' },
  { code: 'IMAGE_GENERATION', label: '图像生成' },
  { code: 'IMAGE_EDIT', label: '图像编辑' },
  { code: 'VIDEO_GENERATION', label: '视频生成' },
  { code: 'DESIGN_SESSION_CREATE', label: '创建设计会话' },
  { code: 'VISUAL_QA', label: '视觉质量检查' },
  { code: 'AGENT_EVALUATION', label: 'Agent 评测' }
];

/**
 * 生成一次启动的幂等键。
 *
 * <p>**打开表单时生成一次，整轮（预检 + 重试）都用它**：服务端按它判"这是同一次启动"，
 * 每次都换新键等于关掉了幂等。用户改了输入再点预检仍然复用同一个键——
 * 服务端只在**已经存在启动记录**时才把"同键不同内容"判为冲突（那时确实需要换键或刷新）。</p>
 *
 * @returns 幂等键
 */
export function buildIdempotencyKey(): string {
  const cryptoObj = typeof globalThis !== 'undefined' ? (globalThis.crypto as Crypto | undefined) : undefined;
  if (cryptoObj?.randomUUID) {
    return cryptoObj.randomUUID().replace(/-/g, '');
  }
  // 老浏览器兜底：不追求密码学强度，只求"这一轮唯一"
  return 'k' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
}

/**
 * 问题里是否包含某个错误码。
 *
 * <p>界面按**码**分支（例如缺必填时高亮表单、票据过期时提示重新发起），
 * 不能匹配文案——文案由后端给，改措辞不该让界面逻辑失效。</p>
 *
 * @param problems 问题
 * @param code 错误码
 * @returns 包含返回 true
 */
export function hasProblem(problems: { code: string }[] | undefined, code: string): boolean {
  return (problems ?? []).some(item => item.code === code);
}

/**
 * 把问题拼成可直接显示的文案（后端给了文案就用它；没有则退回码）。
 *
 * @param problems 问题
 * @returns 文案数组
 */
export function problemTexts(problems: { code: string; message?: string }[] | undefined): string[] {
  return (problems ?? []).map(item => item.message || item.code);
}
