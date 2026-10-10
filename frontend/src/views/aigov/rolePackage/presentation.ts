import type { AigRoleActionVO } from '@/api/aigov/rolePackage/types';

/**
 * 岗位包管理台的**展示层**纯函数。
 *
 * 这里只做"怎么显示"，**不做状态判定**：允许的流转、是否对员工可见都由服务端算好
 * （`allowedTransitions` / `visibleToEmployees`）。前端各写一份判断，
 * 迟早会出现"界面说能点、后端拒绝"，或者更糟——"界面看不到但门户里能看见"。
 */

/** 发布状态 → 展示元数据（未知取值 fail-safe 成原样显示，而不是假装正常） */
const RELEASE_STATUS_META: Record<string, { label: string; tag: 'info' | 'warning' | 'success' | 'danger' }> = {
  DRAFT: { label: '草稿', tag: 'info' },
  TESTING: { label: '测试中（仅预览）', tag: 'warning' },
  PUBLISHED: { label: '已发布', tag: 'success' },
  DISABLED: { label: '已停用', tag: 'danger' }
};

/**
 * 状态的显示文案与标签色。
 *
 * @param code 发布状态编码
 * @returns 文案与标签类型；未知状态原样显示并标灰（不是默认成"已发布"）
 */
export function releaseStatusMeta(code?: string): { label: string; tag: 'info' | 'warning' | 'success' | 'danger' } {
  if (!code) {
    return { label: '未知', tag: 'info' };
  }
  return RELEASE_STATUS_META[code] ?? { label: code, tag: 'info' };
}

/**
 * 可用于「发布」按钮的目标（服务端允许的流转里滤出 TESTING/PUBLISHED）。
 *
 * 刻意滤掉 DISABLED：停用是另一个端点、另一个权限点。把 DISABLED 混进这个列表，
 * 会让"能发布"等于"能叫停"，权限拆分就白做了。
 *
 * @param allowed 服务端给出的允许流转
 * @returns 目标数组（按状态机的推进顺序：先 TESTING 后 PUBLISHED）
 */
export function publishTargets(allowed?: string[]): { code: string; label: string }[] {
  const order = ['TESTING', 'PUBLISHED'];
  const set = new Set(allowed ?? []);
  return order.filter(code => set.has(code)).map(code => ({ code, label: releaseStatusMeta(code).label }));
}

/**
 * 是否应该显示「停用」按钮。
 *
 * @param releaseStatus 当前发布状态
 * @returns 已停用的版本不再显示（它已经停了；重新启用走发布按钮）
 */
export function canDisable(releaseStatus?: string): boolean {
  return releaseStatus !== 'DISABLED';
}

/**
 * 按分类归拢卡片（列表展示用）。
 *
 * 分类顺序按**清单里声明的顺序**（那是配置者排的），分类内按 `sortOrder` 再按编码。
 * 没有任何卡片的分类也会返回一条空组——"这个分类下还没有卡片"是配置者要知道的事实，
 * 直接把它从界面上抹掉，会让人以为分类根本没配上。
 *
 * @param categories 清单里的分类
 * @param actions 卡片
 * @returns 分组结果
 */
export function groupActionsByCategory(
  categories: { code: string; name?: string }[] | undefined,
  actions: AigRoleActionVO[] | undefined
): { code: string; name: string; actions: AigRoleActionVO[] }[] {
  const actionList = actions ?? [];
  const groups: { code: string; name: string; actions: AigRoleActionVO[] }[] = [];
  const seen = new Set<string>();
  for (const category of categories ?? []) {
    if (!category?.code) {
      continue;
    }
    seen.add(category.code);
    groups.push({
      code: category.code,
      name: category.name || category.code,
      actions: sortActions(actionList.filter(item => item.categoryCode === category.code))
    });
  }
  // 卡片挂在清单里没有的分类上（服务端校验会拦，但界面上也要看得见，而不是凭空消失）
  const orphans = actionList.filter(item => item.categoryCode && !seen.has(item.categoryCode));
  if (orphans.length > 0) {
    groups.push({ code: '（未在清单中声明的分类）', name: '（未在清单中声明的分类）', actions: sortActions(orphans) });
  }
  return groups;
}

/**
 * 分类内排序：先 sortOrder，再编码。
 *
 * @param actions 卡片
 * @returns 新数组
 */
function sortActions(actions: AigRoleActionVO[]): AigRoleActionVO[] {
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
 * 解析清单 JSON（编辑草稿时把"非卡片部分"填回表单）。
 *
 * 清单里的键在前端会被重新序列化，因此这里**只读不写**：保存时由服务端重新组装清单，
 * 前端不生成清单文本（否则前端与服务端会各写一份，迟早不一致）。
 *
 * 解析失败时返回 `ok: false` 与空值——**不抛异常**，让界面照常打开并把
 * 校验问题（服务端重新校验时会报）展示给用户，而不是弹一个白屏。
 *
 * @param manifestJson 清单原文
 * @returns 解析结果
 */
export function parseManifest(manifestJson?: string): {
  ok: boolean;
  categories: { code: string; name: string }[];
  defaultCategory?: string;
  audienceScope?: string;
  defaultDataLevel?: string;
  maxDataLevel?: string;
} {
  const empty = { ok: false, categories: [] as { code: string; name: string }[] };
  if (!manifestJson) {
    return empty;
  }
  try {
    const parsed = JSON.parse(manifestJson);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return empty;
    }
    const categories = Array.isArray(parsed.categories)
      ? parsed.categories
          .filter((item: any) => item && typeof item === 'object')
          .map((item: any) => ({ code: String(item.code ?? ''), name: String(item.name ?? '') }))
      : [];
    return {
      ok: true,
      categories,
      defaultCategory: parsed.defaultCategory ?? undefined,
      audienceScope: parsed.presentation?.audienceScope ?? undefined,
      defaultDataLevel: parsed.policy?.defaultDataLevel ?? undefined,
      maxDataLevel: parsed.policy?.maxDataLevel ?? undefined
    };
  } catch {
    return empty;
  }
}

/** 启动方式 → 显示文案 */
const LAUNCH_MODE_LABELS: Record<string, string> = {
  QUICK: '快速能力',
  FORM: '表单',
  STUDIO: '专业台',
  NAVIGATION: '页面跳转'
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
