import type { AigCapabilityOverviewVO } from '@/api/aigov/capability/overview-types';
import type { AigInvokeVO } from '@/api/aigov/invoke';

/**
 * 能力域展示口径（P0）。
 *
 * <p>这里放的是"界面怎么把服务端数据讲成人话"的纯函数：问题码 → 标签与原因、
 * 候选健康 → 状态、以及**试调助手把 dryRun 结果渲染成六段式对话**的逻辑。
 * 做成纯函数的理由与本仓其它域一致：本仓前端没有 DOM 测试环境，
 * 只有纯函数才能被单测钉住，而"助手回答得对不对"恰恰是这次改造的核心体验。</p>
 */

/** 语义胶囊（与全站 5 类一致） */
export type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary' | 'gray';

export interface TagMeta {
  label: string;
  type: TagType;
  /** 为什么是这个状态（用于 hover 提示与详情说明） */
  hint?: string;
}

/**
 * 问题码 → 标签/原因/建议。
 *
 * <p><b>为什么 hint 要写进代码</b>：这些原因码就是"卡片为什么红"的唯一解释；
 * 如果只给一个红标签，使用者还得回来问我们"缺模型候选是什么意思"。</p>
 */
export const ISSUE_META: Record<string, TagMeta> = {
  NO_MODEL_CANDIDATE: {
    label: '缺模型候选',
    type: 'danger',
    hint: '这个能力没有挂任何模型 ⇒ 调用一定失败（路由会返回「没有可用模型候选」）'
  },
  CANDIDATE_UNHEALTHY: {
    label: '候选健康异常',
    type: 'warning',
    hint: '有候选被标记为 DOWN / UNHEALTHY，路由会跳过它（若全部候选都不健康，调用会失败）'
  },
  NO_ROUTE_POLICY: {
    label: '缺策略',
    type: 'danger',
    hint: '没有任何数据等级策略 ⇒ 外发、审批、无模型时的兜底都只能走默认口径'
  },
  POLICY_INCOMPLETE: {
    label: '策略不全',
    type: 'warning',
    hint: '只有部分数据等级配了策略；缺失等级下的调用走默认兜底，容易与预期不一致'
  }
};

/** 认不出的问题码按"提示"处理并如实回显，不静默丢掉 */
export function issueMeta(code?: string): TagMeta {
  if (!code) {
    return { label: '链路完整', type: 'success', hint: '候选、策略都齐' };
  }
  return ISSUE_META[code] ?? { label: code, type: 'info', hint: '未登记的问题码（服务端新增了原因码，前端还没跟上）' };
}

/** 问题码集合 → 标签集合；空集合表示链路完整 */
export function issueTags(issueCodes?: string[]): TagMeta[] {
  if (!issueCodes || issueCodes.length === 0) {
    return [issueMeta()];
  }
  return issueCodes.map((code) => issueMeta(code));
}

/** 把问题码的提示拼成一段话（卡片 tooltip / 详情提示用） */
export function riskHint(issueCodes?: string[]): string {
  if (!issueCodes || issueCodes.length === 0) {
    return '链路完整：候选与策略都已配置。';
  }
  return issueCodes.map((code) => issueMeta(code).hint ?? code).join('；');
}

/**
 * 候选健康汇总 → 标签。
 *
 * <p>{@code UNKNOWN} 不是"未知的小问题"：它表示"候选从未被探测过"。
 * 把它显示成绿色会让运维以为一切正常 —— 所以给提示色并写明"尚未探测"。</p>
 */
export function healthMeta(agg?: string): TagMeta {
  switch (agg) {
    case 'HEALTHY':
      return { label: '候选健康', type: 'success' };
    case 'UNHEALTHY':
      return { label: '候选健康异常', type: 'warning', hint: '有候选被明确标记为不可用，路由会跳过' };
    case 'UNKNOWN':
      return { label: '尚未探测', type: 'info', hint: '候选模型还没有健康检查结果；路由此时不会排除它们' };
    case 'NONE':
      return { label: '无候选', type: 'danger', hint: '没有候选可用' };
    default:
      return { label: agg || '未知', type: 'gray' };
  }
}

/** 策略覆盖展示：0 → 明确写"未配置"，而不是显示 0/4 让人猜 */
export function coverageText(covered?: number, total?: number): string {
  const need = total ?? 4;
  if (!covered) {
    return `0/${need}（未配置）`;
  }
  return `${covered}/${need}`;
}

/** 卡片一行摘要（候选 / 策略覆盖 / 近 24h 调用） */
export function cardSummary(vo?: AigCapabilityOverviewVO): string {
  if (!vo) {
    return '';
  }
  const candidates = vo.candidateCount ?? 0;
  const calls = vo.calls24h ?? 0;
  const fails = vo.fails24h ?? 0;
  const callText = calls === 0 ? '近24h 无调用' : `近24h ${calls}${fails > 0 ? `（失败 ${fails}）` : ''}`;
  return `候选 ${candidates} · 策略 ${coverageText(vo.policyCoveredLevels, vo.policyTotalLevels)} · ${callText}`;
}

/** 能力标签（逗号分隔）→ 数组（去空白、去空项） */
export function parseTags(requiredTags?: string): string[] {
  if (!requiredTags) {
    return [];
  }
  return requiredTags
    .split(',')
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0);
}

/** 状态（0正常 1停用） */
export function statusMeta(status?: string): TagMeta {
  return status === '1'
    ? { label: '已停用', type: 'gray' }
    : { label: '启用', type: 'success' };
}

/** 问题码筛选项（筛选条用；与服务端 issueCode 取值一一对应） */
export function issueFilterOptions(): { value: string; label: string }[] {
  return [
    { value: '', label: '问题：全部' },
    ...Object.entries(ISSUE_META).map(([value, meta]) => ({ value, label: `问题：${meta.label}` }))
  ];
}

/** 候选有无筛选项 */
export function candidateStateOptions(): { value: string; label: string }[] {
  return [
    { value: '', label: '模型候选：全部' },
    { value: 'NO', label: '模型候选：无候选' },
    { value: 'HAS', label: '模型候选：已有候选' }
  ];
}

/** 候选用途（写库口径：规范大写；服务端会归一化） */
export const USAGE_OPTIONS: { value: string; label: string }[] = [
  { value: 'PRIMARY', label: '主选 PRIMARY' },
  { value: 'FALLBACK', label: '备选 FALLBACK' },
  { value: 'GRAY', label: '灰度 GRAY' }
];

/** 用途 → 标签 */
export function usageMeta(usage?: string): TagMeta {
  switch ((usage || '').toUpperCase()) {
    case 'PRIMARY':
      return { label: '主选', type: 'primary' };
    case 'FALLBACK':
      return { label: '备选', type: 'info' };
    case 'GRAY':
      return { label: '灰度', type: 'gray' };
    default:
      return { label: usage || '未设置', type: 'gray' };
  }
}

/** 数据等级（试调假设参数用） */
export const DATA_LEVEL_OPTIONS: { value: string; label: string }[] = [
  { value: 'PUBLIC', label: '公开' },
  { value: 'INTERNAL', label: '内部' },
  { value: 'RESTRICTED', label: '限制' },
  { value: 'STRICT', label: '严格（禁止外发）' }
];

/** 路由结论 → 标签 */
export function decisionMeta(decision?: string): TagMeta {
  switch (decision) {
    case 'MODEL':
      return { label: '命中模型', type: 'success' };
    case 'MANUAL':
      return { label: '转人工', type: 'warning', hint: '没有可用候选或策略要求转人工，需要人来处理' };
    case 'DENIED':
      return { label: '策略拒绝', type: 'danger', hint: '被策略拦下（外发/等级/绑定等），不会调用模型' };
    default:
      return { label: decision || '未返回结论', type: 'gray' };
  }
}

/** 从 policyHits 里挑出"排除"类判定（引擎写的是「排除 …」开头的行） */
export function excludedHits(policyHits?: string[]): string[] {
  if (!policyHits || policyHits.length === 0) {
    return [];
  }
  return policyHits.filter((hit) => hit.trim().startsWith('排除'));
}

/** 从 policyHits 里挑出非排除的判定（策略命中、场景收窄、预算提示等） */
export function otherHits(policyHits?: string[]): string[] {
  if (!policyHits || policyHits.length === 0) {
    return [];
  }
  return policyHits.filter((hit) => !hit.trim().startsWith('排除'));
}

/**
 * 找出与某个模型相关的判定（回答"为什么排除了 X"）。
 *
 * <p>引擎写的排除行有两种形态：{@code 排除 modelId=123：…} 与
 * {@code 排除 模型名（modelKey=abc）：…}。这里两种都匹配；**匹配不到就返回空数组**，
 * 由调用方如实回答"本次决策没有排除它"——而不是硬凑一条解释。</p>
 */
export function hitsForModel(policyHits: string[] | undefined, keyword?: string): string[] {
  if (!policyHits || policyHits.length === 0 || !keyword) {
    return [];
  }
  const needle = keyword.trim();
  if (!needle) {
    return [];
  }
  return policyHits.filter((hit) => hit.includes(needle) || hit.includes(`modelId=${needle}`));
}

/** 试调助手的固定边界说明（Q3：P0 只用文案讲清，P1 再补只读字段） */
export function assistantBoundaryNote(): string {
  return 'dryRun 只预演路由：不发起模型调用、不写审计、不扣配额；'
    + '也**不校验调用授权（审批）与人均配额**——那两道在真实调用时才会拦。所以「试调通过」不等于「一定能调」。';
}

/** 助手快捷提问（P0 固定的 4 类） */
export function presetQuestions(): string[] {
  return ['这条链路会走哪条路？', '为什么排除了某个模型？', '数据等级是严格会怎样？', '给我策略配置建议'];
}

/**
 * 把自由输入路由到 4 类固定问题之一（P0 不接大模型，避免为一次问答产生外部调用）。
 *
 * @param input 用户输入
 * @returns 命中的问题类型；识别不出返回 null（由界面提示可用问题）
 */
export function matchPresetQuestion(input: string): string | null {
  const text = (input || '').trim();
  if (!text) {
    return null;
  }
  if (/(排除|为什么没选|为什么不用|没被选中|未选中)/.test(text)) {
    return 'EXCLUDE';
  }
  if (/(严格|STRICT|等级|外发|审批)/.test(text)) {
    return 'LEVEL';
  }
  if (/(建议|怎么配|如何配|优化|补齐)/.test(text)) {
    return 'SUGGEST';
  }
  if (/(哪条路|走哪|选哪个|命中|会怎样|链路)/.test(text)) {
    return 'WALKTHROUGH';
  }
  return null;
}

export interface WalkthroughOptions {
  /** 本次假设的数据等级 */
  dataLevel?: string;
  /** 本次假设是否禁止外发 */
  allowExternal?: boolean;
  /** 本次假设的场景编码 */
  scenarioCode?: string;
}

/**
 * 把 dryRun 结果渲染成**六段式**（试调助手的核心输出）。
 *
 * @param vo      dryRun 返回
 * @param options 本次假设参数（写进第 ② 段，让人知道"这是在什么假设下得出的结论"）
 * @returns 段落数组（title + lines）
 */
export function walkthroughSections(vo?: AigInvokeVO, options: WalkthroughOptions = {}) {
  const decision = decisionMeta(vo?.decision);
  const excluded = excludedHits(vo?.policyHits);
  const others = otherHits(vo?.policyHits);

  const assumption = [
    `数据等级=${options.dataLevel || '未指定'}`,
    options.allowExternal === true ? '本次允许外发' : options.allowExternal === false ? '本次禁止外发（硬约束）' : '外发沿用策略',
    options.scenarioCode ? `场景=${options.scenarioCode}` : '场景=不限'
  ].join(' · ');

  return [
    {
      title: '① 能力与假设',
      lines: [assumption, `结论：${decision.label}${vo?.reason ? `（${vo.reason}）` : ''}`]
    },
    {
      title: '② 策略与其他判定',
      lines: others.length > 0 ? others : ['本次没有额外的策略/收窄说明（策略那一行按默认口径生效）']
    },
    {
      title: '③ 被排除的候选',
      lines:
        excluded.length > 0
          ? excluded
          : ['本次没有候选被排除' + (vo?.modelKey ? `（最终选了 ${vo.modelKey}）` : '')]
    },
    {
      title: '④ 最终选择',
      lines: vo?.modelKey
        ? [
            `模型：${vo.modelKey}`,
            `部署类型：${vo.deploymentType || '未知'}`,
            `调用器：${vo.invoker || '未知'}`,
            `是否外发：${vo.externalCall ? '是（数据离开本地环境）' : '否'}`
          ]
        : ['没有选中任何模型（转人工或被拒绝）']
    },
    {
      title: '⑤ 需要人工确认的点',
      lines:
        vo?.pendingConfirm && vo.pendingConfirm.length > 0 ? vo.pendingConfirm : ['无']
    },
    {
      title: '⑥ 边界（必读）',
      lines: [assistantBoundaryNote()]
    }
  ];
}

/**
 * 校验 JSON 文本（输入/输出 Schema）。
 *
 * <p>返回 null 表示合法；返回字符串表示**给人看的**错误原因。空值视为"未填写"，也算合法
 * （Schema 不是必填，但填了就必须是合法 JSON —— 垃圾值会让预览/生成阶段才失败）。</p>
 *
 * @param text JSON 文本（可空）
 * @returns 错误原因；合法返回 null
 */
export function validateJsonText(text?: string): string | null {
  if (!text || !text.trim()) {
    return null;
  }
  try {
    JSON.parse(text);
    return null;
  } catch (error) {
    return `JSON 格式不合法：${String((error as Error)?.message || error)}`;
  }
}

/**
 * 从一句自然语言里猜出"用户问的是哪个模型"。
 *
 * <p>试调助手的自由输入要走关键词路由（P0 不接大模型）：用户问"为什么排除了 local-sdxl？"
 * 时，把其中的模型键/ID 抽出来去 {@code policyHits} 里精确匹配。抽不到就返回空串，
 * 由调用方如实回答"本次没有排除它"，而不是硬凑一条解释。</p>
 *
 * @param text 用户输入
 * @returns 候选 token（可能是模型键或 modelId）；抽不到返回空串
 */
export function extractModelToken(text?: string): string {
  const input = (text || '').trim();
  if (!input) {
    return '';
  }
  // 先剥掉常见疑问词，再取第一个"像标识符"的片段（含 . _ : - 的英文数字组合）
  const cleaned = input.replace(/[?？。，,、；;！!（）()【】[\]“”"']/g, ' ');
  const matched = cleaned.match(/[A-Za-z0-9][A-Za-z0-9._:-]{1,}/g);
  if (!matched || matched.length === 0) {
    return '';
  }
  const stopWords = new Set([
    'why',
    'the',
    'and',
    'not',
    'model',
    'dryRun',
    'STRICT',
    'PUBLIC',
    'INTERNAL',
    'RESTRICTED'
  ]);
  for (const token of matched) {
    if (!stopWords.has(token)) {
      return token;
    }
  }
  return '';
}

/**
 * 引用类型 → 标签（影响面表格用）。
 *
 * @param refType AGENT_VERSION / TASK / DRAFT
 * @returns 标签
 */
export function referenceTypeMeta(refType?: string): TagMeta {
  switch (refType) {
    case 'AGENT_VERSION':
      return { label: 'Agent 版本', type: 'primary' };
    case 'TASK':
      return { label: '历史任务', type: 'info' };
    case 'DRAFT':
      return { label: '训练台草稿', type: 'gray' };
    default:
      return { label: refType || '未知引用', type: 'gray' };
  }
}

/**
 * 影响面摘要（一句话；用于详情区块与删除确认框标题）。
 *
 * <p>空引用要**明说"没有任何引用"**：那句话正是"可以安全删除"的唯一依据。</p>
 *
 * @param refs 引用清单（可空）
 * @returns 摘要文案
 */
export function referenceSummary(refs?: { refType?: string; count?: number }[]): string {
  const list = refs || [];
  if (list.length === 0) {
    return '没有任何 Agent 版本 / 历史任务 / 训练台草稿引用它。';
  }
  const parts = list.map((ref) => {
    const label = referenceTypeMeta(ref.refType).label;
    return ref.refType === 'AGENT_VERSION' ? `Agent 版本 ${ref.count ?? 1} 个` : `${label} ${ref.count ?? 0} 条`;
  });
  return `共 ${list.length} 类引用：${parts.join(' · ')}。`;
}

/**
 * 删除/停用前的确认文案（把影响面写进确认框，而不是只说"确定删除吗"）。
 *
 * @param capabilityName 能力名（可空则用编码）
 * @param capabilityCode 能力编码
 * @param refs           引用清单
 * @returns 确认文案
 */
export function deleteConfirmText(
  capabilityName: string | undefined,
  capabilityCode: string,
  refs?: { refLabel?: string; refType?: string; count?: number }[]
): string {
  const title = capabilityName || capabilityCode;
  const list = refs || [];
  const head = `删除能力「${title}」？`;
  const impact = ['删除后，它的模型候选、策略、调用授权会变成指向已删能力的孤儿配置（永远不生效）。'];
  if (list.length === 0) {
    impact.push(referenceSummary(list) + ' 仍建议先确认没有正在使用它的任务。');
  } else {
    impact.push('当前引用：');
    list.slice(0, 5).forEach((ref) => {
      impact.push(`· ${ref.refLabel || referenceTypeMeta(ref.refType).label}${ref.count ? `（${ref.count}）` : ''}`);
    });
    if (list.length > 5) {
      impact.push(`· …另有 ${list.length - 5} 类引用`);
    }
  }
  impact.push('如果只是暂时不用，建议改为「停用」——停用可以恢复。');
  return [head, '', ...impact].join('\n');
}

/** 能力就绪度的两类问题：阻断项（现在引用它一定失败）与提示项（能存草稿但不能真正路由） */
export interface CapabilityReadiness {
  /** 阻断项：不存在 / 已停用 */
  blockers: string[];
  /** 提示项：缺候选 / 候选不健康 / 缺策略 */
  warnings: string[];
  /** 两类合起来的一句话（下拉项与预检提示直接用；就绪时为空串） */
  text: string;
}

/**
 * 能力就绪度（训练台下拉与预检共用的唯一口径）。
 *
 * <p><b>为什么阻断项与提示项要分开</b>：按 D07 的既定语义，训练台草稿允许先存半成品，
 * 所以「缺候选 / 缺策略」只能是提示（让人知道现在跑不通），不能拦保存；
 * 而「能力不存在 / 已停用」是编码层面就错了，必须报阻断。</p>
 *
 * @param vo 能力概览行；为空表示能力清单里没有这个编码
 * @returns 就绪度（不改任何配置，纯判定）
 */
export function capabilityReadiness(vo?: AigCapabilityOverviewVO): CapabilityReadiness {
  const blockers: string[] = [];
  const warnings: string[] = [];
  if (!vo) {
    blockers.push('能力不存在（不在能力清单里，编码可能写错或能力已被删除）');
  } else {
    if ((vo.status ?? '0') !== '0') {
      blockers.push('能力已停用（停用后调用一律拒绝）');
    }
    if ((vo.candidateCount ?? 0) === 0) {
      warnings.push('缺模型候选（无法路由）');
    } else if (vo.candidateHealthAgg === 'UNHEALTHY') {
      warnings.push('模型候选全部不健康');
    } else if (vo.candidateHealthAgg === 'NONE' || vo.candidateHealthAgg === 'UNKNOWN') {
      warnings.push('模型候选健康状态未知（尚未探测）');
    }
    const covered = vo.policyCoveredLevels ?? 0;
    const total = vo.policyTotalLevels ?? 4;
    if (covered === 0) {
      warnings.push(`缺路由策略（0/${total}）`);
    } else if (covered < total) {
      warnings.push(`路由策略不全（${covered}/${total}）`);
    }
  }
  return { blockers, warnings, text: [...blockers, ...warnings].join('；') };
}

/**
 * 策略配置建议（P0 用规则模板；明确标注"建议，不会自动写入"）。
 *
 * @param vo       dryRun 结果（用来判断当前被什么挡住）
 * @param overview 能力概览（用来判断候选/策略覆盖度）
 * @returns 建议条目
 */
export function policySuggestions(vo?: AigInvokeVO, overview?: AigCapabilityOverviewVO): string[] {
  const suggestions: string[] = [];
  if (!overview || (overview.candidateCount ?? 0) === 0) {
    suggestions.push('先给这个能力挂一个模型候选，否则任何调用都会在路由第 3 步失败。');
  }
  const covered = overview?.policyCoveredLevels ?? 0;
  const total = overview?.policyTotalLevels ?? 4;
  if (covered === 0) {
    suggestions.push(`补 4 个数据等级的策略（当前 ${covered}/${total}）：至少明确「严格」等级禁止外发。`);
  } else if (covered < total) {
    suggestions.push(`还有 ${total - covered} 个数据等级没有策略，建议补齐（缺失等级走默认兜底）。`);
  }
  if (vo?.reasonCode === 'NO_ROUTE_POLICY') {
    suggestions.push('本次结论是「没有路由策略」：请在该能力 × 该数据等级下新增一条策略。');
  }
  if (vo?.reasonCode === 'NO_MODEL_BOUND') {
    suggestions.push('本次结论是「没有绑定模型」：请在详情 → 模型候选里添加候选。');
  }
  if (suggestions.length === 0) {
    suggestions.push('当前配置看不出明显缺口；如需收紧，可从「限制」等级开始补审批。');
  }
  suggestions.push('以上只是建议，不会自动写入任何配置；请到对应页签手动确认后保存。');
  return suggestions;
}
