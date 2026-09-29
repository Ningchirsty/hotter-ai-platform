import { STEP_META, STAGE_ORDER } from './creativeFlowSteps';
import type { ScenarioStep } from '@/api/creative/scenario';

/**
 * 「配置步骤（`dp_scenario_step`）↔ 代码八步」的**只读对照**（V0.2 D 阶段第二刀，第一步）。
 *
 * <p><b>为什么先做对照、而不是直接把指引线切到配置驱动</b>：配置里是 10 步（INPUT/FACT/DNA/
 * DIRECTION/STORYBOARD/GATE/GENERATION/QA/LAYOUT/FINAL），代码里是 8 步，两者**不是一一对应**：
 * <ul>
 *   <li>代码第 1 步「项目资料与品牌要求」在配置里被拆成 INPUT + FACT（一条配置 → 两步合一）；</li>
 *   <li>代码第 8 步「详情页排版与终审」在配置里被拆成 LAYOUT + FINAL（两条配置 → 一步合一）；</li>
 *   <li>配置里的 QA（质检）在代码八步里**没有独立一步**（代码把质检并进第 7 步「出图」）。</li>
 * </ul>
 * 直接把指引线换成 10 步会立刻改变"当前在第几步、点了跳到哪一页"的判定，而每个步骤的
 * 明细判据（`useCreativeFlow#ensureStep`）目前是按 1~8 的步号写死的。所以这一步只做**对照**：
 * 把差异如实算出来展示给人看，指引线一行不动（点错了不会有事，但看错了会指错路）。</p>
 *
 * <p><b>纯函数</b>：不 import 任何运行时依赖（`@/api/creative/scenario` 只 import type，编译后擦除），
 * 因此能被 vitest 在 node 环境直接断言——与 `scenarioText.ts`、`creativeFlowSteps.ts` 同一做法。</p>
 *
 * @author creative
 */

/**
 * 配置步骤编码 → 代码八步的 key。
 *
 * <p>这张表是**人的判断**，不是推导出来的：改它等于改"配置里的哪一步对应界面上的哪一步"，
 * 必须有意识地改，并且单测会跟着红。刻意不给 QA 映射——代码八步里没有它。</p>
 */
export const STEP_CODE_TO_CODE_KEY: Record<string, string> = {
  INPUT: 'material',
  FACT: 'fact',
  DNA: 'dna',
  DIRECTION: 'direction',
  STORYBOARD: 'storyboard',
  GATE: 'gate',
  GENERATION: 'production',
  LAYOUT: 'layout',
  FINAL: 'layout'
};

/** 对照表里的一行 */
export interface MappingRow {
  stepCode: string;
  stepName: string;
  sortNo: number;
  /** 对映到的代码步号（1~8）；没有对应为 null */
  codeNo: number | null;
  /** 对映到的代码步骤名（没有对应为 null） */
  codeName: string | null;
  /** 该配置步骤覆盖的阶段（原样，便于核对） */
  stageCodes: string;
  /** 覆盖了但代码不认识的阶段码（配置写错时在这里暴露） */
  unknownStages: string[];
}

/** 对照结果 */
export interface FlowStepMapping {
  /** 逐条对照（按 sortNo） */
  rows: MappingRow[];
  /** 配置里有、代码八步里没有对应的（预期：QA） */
  unmapped: MappingRow[];
  /** 代码八步里没有任何配置步骤覆盖的步号 */
  uncoveredCodeNos: number[];
  /** 多条配置步骤覆盖同一个代码步（预期：LAYOUT/FINAL → 第 8 步） */
  fanIn: Array<{ codeNo: number; codeName: string; stepCodes: string[] }>;
  /** 18 个阶段里没有被任何配置步骤覆盖的（预期：V08_READY） */
  uncoveredStages: string[];
  /** 配置里引用了代码不认识的阶段（预期：空） */
  unknownStages: string[];
  /** 已对映条数 / 配置总条数，形如 `9/10` */
  coverageText: string;
  /** 一句话结论（界面上的 hover 提示用它，测试也断言它） */
  verdict: string;
}

/** 取代码八步的步号（按 STEP_META 顺序，key 不存在返回 null） */
function codeNoOfKey(key: string): number | null {
  const meta = STEP_META.find((m) => m.key === key);
  return meta ? meta.no : null;
}

/** 取代码八步的名字 */
function codeNameOfKey(key: string): string | null {
  const meta = STEP_META.find((m) => m.key === key);
  return meta ? meta.name : null;
}

/** 拆 stage_codes（逗号分隔）并去掉空项 */
function splitStages(stageCodes?: string | null): string[] {
  return (stageCodes || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

/**
 * 把配置步骤与代码八步做逐条对照。
 *
 * @param steps 配置步骤（`GET /creative/v2/scenarios/{type}/steps` 的返回）
 * @returns 对照结果；入参为空时所有数组为空、coverageText 为空串（调用方据此不显示）
 */
export function mapFlowSteps(steps?: ScenarioStep[] | null): FlowStepMapping {
  const rows: MappingRow[] = (steps || [])
    .filter(Boolean)
    .toSorted((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
    .map((step) => {
      const stepCode = step.stepCode || '';
      const stages = splitStages(step.stageCodes);
      const unknown = stages.filter((s) => !STAGE_ORDER.includes(s));
      const key = STEP_CODE_TO_CODE_KEY[stepCode];
      const codeNo = key ? codeNoOfKey(key) : null;
      return {
        stepCode,
        stepName: step.stepName || stepCode,
        sortNo: step.sortNo ?? 0,
        codeNo,
        codeName: key ? codeNameOfKey(key) : null,
        stageCodes: step.stageCodes || '',
        unknownStages: unknown
      };
    });

  const unmapped = rows.filter((r) => r.codeNo == null);

  // 代码八步里没有任何配置步骤覆盖的
  const coveredNos = new Set(rows.map((r) => r.codeNo).filter((n): n is number => n != null));
  const uncoveredCodeNos = STEP_META.map((m) => m.no).filter((no) => !coveredNos.has(no));

  // 多条配置 → 同一个代码步
  const fanIn: FlowStepMapping['fanIn'] = [];
  for (const meta of STEP_META) {
    const hit = rows.filter((r) => r.codeNo === meta.no);
    if (hit.length > 1) {
      fanIn.push({ codeNo: meta.no, codeName: meta.name, stepCodes: hit.map((r) => r.stepCode) });
    }
  }

  // 阶段覆盖：哪些阶段没有被任何配置步骤覆盖
  const coveredStages = new Set(rows.flatMap((r) => splitStages(r.stageCodes)));
  const uncoveredStages = STAGE_ORDER.filter((s) => !coveredStages.has(s));
  const unknownStages = Array.from(new Set(rows.flatMap((r) => r.unknownStages)));

  const covered = rows.length - unmapped.length;
  return {
    rows,
    unmapped,
    uncoveredCodeNos,
    fanIn,
    uncoveredStages,
    unknownStages,
    coverageText: rows.length ? `${covered}/${rows.length}` : '',
    verdict: buildVerdict(rows, unmapped, uncoveredCodeNos, fanIn, uncoveredStages, unknownStages)
  };
}

/**
 * 组装一句话结论（**只用算出来的事实拼**，不写死"应该是几比几"——配置改了结论自己会变）。
 *
 * @param rows               逐条对照
 * @param unmapped           无对应的配置步骤
 * @param uncoveredCodeNos   无配置对应的代码步号
 * @param fanIn              多条配置覆盖同一步
 * @param uncoveredStages    无配置覆盖的阶段
 * @param unknownStages      代码不认识的阶段码
 * @returns 结论文本；没有配置步骤时返回空串
 */
function buildVerdict(
  rows: MappingRow[],
  unmapped: MappingRow[],
  uncoveredCodeNos: number[],
  fanIn: FlowStepMapping['fanIn'],
  uncoveredStages: string[],
  unknownStages: string[]
): string {
  if (rows.length === 0) {
    return '';
  }
  const parts: string[] = [`配置 ${rows.length} 步 ↔ 代码 ${STEP_META.length} 步`];
  parts.push(
    unmapped.length
      ? `${rows.length - unmapped.length} 条已对映，${unmapped.length} 条代码八步里没有对应（${unmapped
          .map((r) => r.stepCode || '未命名')
          .join('、')}）`
      : '全部有对应'
  );
  for (const item of fanIn) {
    parts.push(`代码第 ${item.codeNo} 步由 ${item.stepCodes.join('、')} 两步覆盖`);
  }
  if (uncoveredCodeNos.length) {
    parts.push(`代码第 ${uncoveredCodeNos.join('、')} 步没有配置步骤对应`);
  }
  if (uncoveredStages.length) {
    parts.push(`${uncoveredStages.length} 个阶段未被任何配置步骤覆盖（${uncoveredStages.join('、')}）`);
  }
  if (unknownStages.length) {
    parts.push(`配置里引用了代码不认识的阶段：${unknownStages.join('、')}`);
  }
  return parts.join('；');
}

/**
 * 界面上那枚小胶囊的文字（形如 `步序对照 9/10`）。
 *
 * @param mapping 对照结果
 * @returns 胶囊文本；没有对照数据返回空串
 */
export function formatMappingChip(mapping: FlowStepMapping): string {
  if (!mapping.rows.length || !mapping.coverageText) {
    return '';
  }
  return `步序对照 ${mapping.coverageText}`;
}
