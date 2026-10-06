/**
 * 视觉门准入项「去哪儿补」的对照（v1 人工测试反馈：未满足项只说了不满足，没说去哪儿补）。
 *
 * <p><b>为什么要有这张表</b>：视觉门会给出一串未满足项（`BRAND_BRIEF_CONFIRMED` 之类），
 * 但"这一项该在哪个页面补"原先只存在于人的记忆里。最典型的是
 * 「品牌 Brief 已填写并确认」——它<b>不是设计侧能做的事</b>（要品牌部在内容任务里录），
 * 页面上却只显示"未满足"，于是点哪儿都像没反应。</p>
 *
 * <p><b>为什么不复用后端的 item_label</b>：label 说的是"什么没满足"，
 * 这里要说的是"去哪儿补"，两者不是一回事；而且这条口径要能被单测钉住。</p>
 *
 * <p><b>认不出的 item_code 返回 null</b>：配置里新加一项时页面不给假链接，
 * 只显示"—"（宁可不给按钮，也不要指错路）。</p>
 *
 * @author creative
 */

/** 一个准入项的补充去处 */
export interface GateFixTarget {
  /** 按钮文案（说清去哪个页面做什么） */
  label: string;
  /** 页面路由 */
  path: string;
  /**
   * 该路由是否支持 `?taskId=` 深链。
   *
   * <p><b>内容任务列表页原先不支持</b>（带上去只会落到列表，看着像"点了没反应"——
   * 这正是 v1 反馈里"也没有跳转到相应要确认的地方"）。本轮给它补了深链支持：
   * `?taskId=…[&section=brief|facts]` 会直接打开那条任务的详情抽屉并滚到对应卡片，
   * 所以品牌部的三项现在也带 taskId 了。</p>
   */
  carriesTaskId: boolean;
  /**
   * 要钉住的那一步（工作台的步骤编码，形如 `INPUT` / `STORYBOARD`）。
   *
   * <p><b>为什么必须带</b>：工作台默认显示"当前步"，而"缺参考图"要补的那一步常常不是当前步——
   * 真机验过：只跳到 `/creative/project` 会停在「出图」，上传框根本不在那一屏。
   * 页面没有这一步时会被忽略（工作台只认它自己托管的那几步）。</p>
   */
  step?: string;
  /**
   * 落在目标页的哪张卡片（内容任务详情的锚点，`brief` / `facts`）。
   *
   * <p>与 `step` 是两种页面的两种"钉住"：工作台按步骤钉，内容任务详情按卡片锚点钉。</p>
   */
  section?: string;
}

/** item code → 补充去处（码与 `dp_gate_item.item_code` 一致） */
export const GATE_FIX_TARGETS: Record<string, GateFixTarget> = {
  DNA_LOCKED: { label: '去视觉基因页生成并锁定', path: '/creative/dna', carriesTaskId: true, step: 'DNA' },
  REFERENCE_IMAGE: {
    label: '去视觉项目页传参考图', path: '/creative/project', carriesTaskId: true, step: 'INPUT'
  },
  DIRECTION_SELECTED: {
    label: '去视觉方向与分镜页选方向', path: '/creative/storyboard', carriesTaskId: true, step: 'DIRECTION'
  },
  STORYBOARD_LOCKED: {
    label: '去视觉方向与分镜页锁定分镜', path: '/creative/storyboard', carriesTaskId: true, step: 'STORYBOARD'
  },
  // 品牌部这三项都在任务详情里补，但**不在同一张卡上**——第 24 轮核对权威来源时发现：
  //   · 品牌 Brief 已填写并确认 → 判的是 Brief（`CreativeGateServiceImpl#brandBriefItem`）→ brief 卡
  //   · 已声明禁用词与合规红线 → 判的也是 Brief 的 forbiddenWords → brief 卡
  //   · **品牌调性已确认 → 判的是「事实」里的 brand_tone，不是 Brief 里的品牌调性**
  //     （`#brandToneItem` 遍历 `detail.getFacts()` 找 fieldCode=brand_tone）
  // 我原先三项都写成 section=brief，于是"品牌调性"这一项会把人送到一张**改不动它**的卡上——
  // 正是 v1 反馈那句"没有跳转到相应要确认的地方"。现在按各自的权威来源分开指。
  BRAND_TONE_CONFIRMED: {
    label: '去内容任务详情确认「品牌调性」事实',
    path: '/business/content/task',
    carriesTaskId: true,
    section: 'facts'
  },
  BRAND_BRIEF_CONFIRMED: {
    label: '去内容任务详情填并确认品牌要求',
    path: '/business/content/task',
    carriesTaskId: true,
    section: 'brief'
  },
  FORBIDDEN_WORDS_DECLARED: {
    label: '去内容任务详情声明禁用词',
    path: '/business/content/task',
    carriesTaskId: true,
    section: 'brief'
  }
};

/**
 * 「识别到 N 条待确认」的去处（v1 反馈 详情页与审核 1.3 的收尾）。
 *
 * <p>第 37 轮在视觉门里加了「上传资料并识别」：识别走内容侧那条链，
 * 结果**一律以待确认落库**。这个目标就是"去把识别出来的事实确认掉"——
 * 与「品牌调性」那一项同一个落点（内容任务详情的事实卡），所以路径口径只写在这里一处。</p>
 */
export const CONFIRM_FACTS_TARGET: GateFixTarget = {
  label: '去内容任务详情确认事实',
  path: '/business/content/task',
  carriesTaskId: true,
  section: 'facts'
};

/**
 * 取一个准入项的补充去处。
 *
 * @param itemCode 准入项编码（`GateItem.code`）
 * @returns 去处；认不出返回 null（页面显示"—"，不给假链接）
 */
export function gateFixTarget(itemCode?: string | null): GateFixTarget | null {
  const code = (itemCode || '').trim();
  return code ? GATE_FIX_TARGETS[code] || null : null;
}

/**
 * 拼出可跳转的地址（需要 `taskId` 的页面自动带上；`step` / `section` 各自带成查询参数）。
 *
 * @param target 去处
 * @param taskId 当前项目ID
 * @returns 路由地址
 */
export function gateFixRoute(target: GateFixTarget, taskId?: string | number | null): string {
  const params: string[] = [];
  if (target.carriesTaskId && taskId != null && String(taskId).trim() !== '') {
    params.push(`taskId=${encodeURIComponent(String(taskId))}`);
  }
  if (target.step) {
    params.push(`step=${encodeURIComponent(target.step)}`);
  }
  if (target.section) {
    params.push(`section=${encodeURIComponent(target.section)}`);
  }
  return params.length ? `${target.path}?${params.join('&')}` : target.path;
}
