import type { DeliveryVO, DpGenerationVO, DpStoryboardScreenVO } from '@/api/creative/types';

/**
 * 质检与交付的**最终口径**（V0.2 R31，文档 §23 的 QaPanel 落地 + §30 的双基准 + R29/R30 的新结论）。
 *
 * <p><b>为什么要有这个纯函数模块</b>：到 R30 为止，"这张图行不行"的结论散在三个页面、四个来源上——
 * 参考图基准与产品基准在出图页、规则体检在生产页、交付产物在终审页。
 * 页面各写各的措辞，就会出现"同一个 null 在 A 页叫未质检、在 B 页被当成通过"。
 * 口径集中在这里，页面只负责显示；也因此它能被单测钉死（不需要浏览器）。</p>
 *
 * <p><b>四条证据线，各有各的处置，不许互相顶替</b>：</p>
 * <ol>
 *   <li>{@code REFERENCE} 参考图基准：模型判定"成品相对本次输入图走样多少"。
 *       不一致 → **筛除**（这是唯一会剥夺候选资格的一条）；空 → **未质检**（不是通过）。</li>
 *   <li>{@code PRODUCT} 产品基准：模型判定"产品还是不是那个产品"。
 *       不一致 → **只提示**（换背景/换景别可能正是设计意图，判断留给人）。</li>
 *   <li>{@code RULES} 屏级规则体检：像素度量（1:1/最短边/透明/白底度/主体占比/贴边）。
 *       未过 → **只报告不判决**；没配规则 → **未配置**（与"通过"必须区分）。</li>
 *   <li>{@code DELIVERY} 交付产物：把已选定的交付图打包成可核对的交付包（清单 + sha256）。
 *       它回答的是"交付了什么、能不能核对"，不回答"好不好看"。</li>
 * </ol>
 *
 * @author creative
 */

/** 一条证据线的状态 */
export type QaEvidenceStatus =
  /** 通过 */
  | 'OK'
  /** 未通过（处置见 effect） */
  | 'FAIL'
  /** 结论不确定（模型给了 UNCERTAIN，或质检失败） */
  | 'UNCERTAIN'
  /** 没做过检查（绝不等于通过） */
  | 'NOT_CHECKED'
  /** 这一屏没配规则/没配基准 */
  | 'NOT_CONFIGURED'
  /** 交付侧：有产物 */
  | 'READY';

/** 一条证据线 */
export interface QaEvidence {
  key: 'REFERENCE' | 'PRODUCT' | 'RULES' | 'DELIVERY';
  label: string;
  status: QaEvidenceStatus;
  statusLabel: string;
  detail: string;
  /** 这一条的处置（页面直接显示，避免"结论一样、处置不一样"被混为一谈） */
  effect: string;
}

/** 状态的中文标签 */
export const QA_STATUS_LABELS: Record<QaEvidenceStatus, string> = {
  OK: '通过',
  FAIL: '未通过',
  UNCERTAIN: '无法判定',
  NOT_CHECKED: '未质检',
  NOT_CONFIGURED: '未配置',
  READY: '已生成'
};

/** 口径说明（页面顶端整段显示；单测钉住关键措辞） */
export const QA_SEMANTICS: string[] = [
  '四条证据线各自独立，不许互相顶替：参考图基准筛除、产品基准提示、规则体检报告、交付产物核对。',
  '「未质检」「未配置」都不是通过——页面永远如实显示，绝不把"没检查"画成"通过"。',
  '只有参考图基准不一致会剥夺候选资格（只筛除不放行）；其余三条都只把结论摆出来，决定权在人。'
];

/**
 * 参考图基准结论。
 *
 * @param verdict 后端镜像的 qaVerdict（CONSISTENT/INCONSISTENT/UNCERTAIN/空）
 * @returns 证据线
 */
export function referenceEvidence(verdict?: string): QaEvidence {
  const effect = '不一致的候选不参与选定（只筛除不放行）';
  if (verdict === 'CONSISTENT') {
    return { key: 'REFERENCE', label: '参考图基准', status: 'OK', statusLabel: '通过', detail: '成品与本次输入图一致', effect };
  }
  if (verdict === 'INCONSISTENT') {
    return { key: 'REFERENCE', label: '参考图基准', status: 'FAIL', statusLabel: '未通过', detail: '成品与本次输入图不一致，已筛除', effect };
  }
  if (verdict === 'UNCERTAIN') {
    return { key: 'REFERENCE', label: '参考图基准', status: 'UNCERTAIN', statusLabel: '无法判定', detail: '模型未能给出确定结论，转人工看', effect };
  }
  return {
    key: 'REFERENCE', label: '参考图基准', status: 'NOT_CHECKED', statusLabel: '未质检',
    detail: '还没有跑过一致性比对（不是通过）', effect
  };
}

/**
 * 产品基准结论。
 *
 * @param verdict 后端镜像的 productVerdict
 * @returns 证据线
 */
export function productEvidence(verdict?: string): QaEvidence {
  const effect = '只提示、不自动筛除（换背景/换景别可能正是设计意图）';
  if (verdict === 'CONSISTENT') {
    return { key: 'PRODUCT', label: '产品基准', status: 'OK', statusLabel: '通过', detail: '与产品图一致', effect };
  }
  if (verdict === 'INCONSISTENT') {
    return { key: 'PRODUCT', label: '产品基准', status: 'FAIL', statusLabel: '未通过', detail: '与产品图有差异，请人工确认是否是设计意图', effect };
  }
  if (verdict === 'UNCERTAIN') {
    return { key: 'PRODUCT', label: '产品基准', status: 'UNCERTAIN', statusLabel: '无法判定', detail: '产品基准比对没有给出结论', effect };
  }
  return {
    key: 'PRODUCT', label: '产品基准', status: 'NOT_CHECKED', statusLabel: '未质检',
    detail: '还没有以产品图为基准比对过（不是通过）', effect
  };
}

/** 规则体检结论的解析结果（与后端 screen-qa-report/1 对齐） */
export interface QaRuleReport {
  configured: boolean;
  verdict: string;
  hardFailed: number;
  softFailed: number;
  failedLabels: string[];
}

/**
 * 解析屏级规则体检结论（`dp_generation.qa_findings_json`）。
 *
 * @param raw 体检 JSON 文本（可空）
 * @returns 解析结果；解析不出按"未配置"处理（不编造结论）
 */
export function parseRuleReport(raw?: string | null): QaRuleReport {
  const empty: QaRuleReport = { configured: false, verdict: 'NOT_CONFIGURED', hardFailed: 0, softFailed: 0, failedLabels: [] };
  if (!raw) {
    return empty;
  }
  try {
    const parsed = JSON.parse(raw) as {
      configured?: boolean;
      verdict?: string;
      hardFailed?: number;
      softFailed?: number;
      findings?: { label?: string; ok?: boolean }[];
    };
    if (!parsed || !parsed.verdict) {
      return empty;
    }
    const failedLabels = (parsed.findings || [])
      .filter((item) => item && item.ok === false)
      .map((item) => item.label || '未命名检查项');
    return {
      configured: parsed.configured === true,
      verdict: parsed.verdict,
      hardFailed: parsed.hardFailed || 0,
      softFailed: parsed.softFailed || 0,
      failedLabels
    };
  } catch {
    return empty;
  }
}

/**
 * 规则体检证据线。
 *
 * @param raw 体检 JSON
 * @returns 证据线
 */
export function rulesEvidence(raw?: string | null): QaEvidence {
  const report = parseRuleReport(raw);
  const effect = '只报告不判决（不会自动筛除候选）';
  if (!raw) {
    return {
      key: 'RULES', label: '规则体检', status: 'NOT_CONFIGURED', statusLabel: '未配置',
      detail: '这一屏的模块没有配质检规则，没有做像素体检', effect
    };
  }
  if (!report.configured || report.verdict === 'NOT_CONFIGURED') {
    return {
      key: 'RULES', label: '规则体检', status: 'NOT_CONFIGURED', statusLabel: '未配置',
      detail: '这一屏的模块没有配质检规则，没有做像素体检', effect
    };
  }
  if (report.verdict === 'PASS') {
    return { key: 'RULES', label: '规则体检', status: 'OK', statusLabel: '通过', detail: '客观度量项全部满足', effect };
  }
  if (report.verdict === 'UNREADABLE') {
    return { key: 'RULES', label: '规则体检', status: 'UNCERTAIN', statusLabel: '读不出图', detail: '交付图读不出来，没能体检（不是通过）', effect };
  }
  const level = report.hardFailed > 0 ? '硬性项' : '参考项';
  return {
    key: 'RULES', label: '规则体检', status: 'FAIL', statusLabel: '未通过',
    detail: `${level}未过：${report.failedLabels.join('、') || '（未给明细）'}`, effect
  };
}

/**
 * 交付产物证据线（项目级，不是单张候选）。
 *
 * @param delivery 交付视图（可空）
 * @returns 证据线
 */
export function deliveryEvidence(delivery?: DeliveryVO | null): QaEvidence {
  const effect = '交付包按清单现拼（不重复占存储），可下载核对';
  const artifacts = delivery?.artifacts || [];
  if (!artifacts.length) {
    return {
      key: 'DELIVERY', label: '交付产物', status: 'NOT_CHECKED', statusLabel: '未生成',
      detail: '还没有生成交付产物（逐屏选定后生成）', effect
    };
  }
  const latest = artifacts[0];
  const renderer = latest.rendererName || latest.renderer || '未知渲染器';
  return {
    key: 'DELIVERY', label: '交付产物', status: 'READY', statusLabel: '已生成',
    detail: `${renderer} v${latest.version}：${latest.imageCount} 张，清单校验和 ${(latest.checksum || '').slice(0, 12) || '—'}…`,
    effect
  };
}

/** 一屏的质检行 */
export interface QaScreenRow {
  screenNo: string;
  screenTypeDesc: string;
  candidateNo?: number;
  status: string;
  evidence: QaEvidence[];
}

/**
 * 把候选与交付数据拼成逐屏的质检行（页面表格直接用）。
 *
 * <p>屏顺序取分镜；分镜里没有的候选（老数据）放在最后并如实标"未归属屏"。</p>
 *
 * @param generations 候选列表（项目内）
 * @param screens     分镜屏（可取不到：那就只按候选列）
 * @param delivery    交付视图（用于交付证据线，挂到每一行？不——交付是项目级，单独显示）
 * @returns 逐屏行
 */
export function buildQaRows(
  generations: DpGenerationVO[],
  screens: DpStoryboardScreenVO[] = []
): QaScreenRow[] {
  const rows: QaScreenRow[] = [];
  const screenMap = new Map<string, DpStoryboardScreenVO>();
  for (const screen of screens) {
    screenMap.set(String(screen.id), screen);
  }
  const used = new Set<string>();
  for (const screen of screens) {
    const gen = generations.find((g) => String(g.screenId) === String(screen.id));
    if (!gen) {
      continue;
    }
    used.add(String(gen.id));
    rows.push({
      screenNo: screen.screenNo || String(screen.id),
      screenTypeDesc: screen.screenTypeDesc || '—',
      candidateNo: gen.candidateNo,
      status: gen.statusDesc || gen.status || '',
      evidence: [referenceEvidence(gen.qaVerdict), productEvidence(gen.productVerdict), rulesEvidence(gen.qaFindingsJson)]
    });
  }
  for (const gen of generations) {
    if (used.has(String(gen.id))) {
      continue;
    }
    rows.push({
      screenNo: '未归属屏',
      screenTypeDesc: '—',
      candidateNo: gen.candidateNo,
      status: gen.statusDesc || gen.status || '',
      evidence: [referenceEvidence(gen.qaVerdict), productEvidence(gen.productVerdict), rulesEvidence(gen.qaFindingsJson)]
    });
  }
  return rows;
}

/**
 * 某一状态的显示类型（Element Plus 的 tag type）。
 *
 * @param status 状态
 * @returns info / success / warning / danger
 */
export function qaStatusType(status: QaEvidenceStatus): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'OK' || status === 'READY') return 'success';
  if (status === 'FAIL') return 'danger';
  if (status === 'UNCERTAIN') return 'warning';
  return 'info';
}
