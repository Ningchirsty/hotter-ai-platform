import { describe, expect, it } from 'vitest';
import {
  QA_SEMANTICS,
  QA_STATUS_LABELS,
  buildQaRows,
  deliveryEvidence,
  parseRuleReport,
  productEvidence,
  qaStatusType,
  referenceEvidence,
  rulesEvidence
} from './qaVerdicts';
import type { DeliveryVO, DpGenerationVO, DpStoryboardScreenVO } from '@/api/creative/types';

/**
 * 质检与交付最终口径的单元测试（V0.2 R31）。
 *
 * <p>这个文件钉的是**措辞与处置**，不是渲染：到 R30 为止同一个 null 在三个页面上有三种叫法，
 * 其中一种（最坏的那种）会被当成通过。所以这里的断言集中在两件事上：</p>
 * <ol>
 *   <li><b>没检查 ≠ 通过</b>：空值一律 NOT_CHECKED/NOT_CONFIGURED，绝不出现 OK；</li>
 *   <li><b>处置不许串味</b>：只有参考图基准说"筛除"，产品基准与规则体检都必须说"只提示/只报告"。</li>
 * </ol>
 */
describe('qaVerdicts：参考图基准（唯一会筛除的一条）', () => {
  it('三种结论 + 空值四种状态各自正确', () => {
    expect(referenceEvidence('CONSISTENT').status).toBe('OK');
    expect(referenceEvidence('INCONSISTENT').status).toBe('FAIL');
    expect(referenceEvidence('UNCERTAIN').status).toBe('UNCERTAIN');
    expect(referenceEvidence(undefined).status).toBe('NOT_CHECKED');
    expect(referenceEvidence('').status).toBe('NOT_CHECKED');
  });

  it('只有参考图基准的处置里写「筛除」', () => {
    expect(referenceEvidence('INCONSISTENT').effect).toContain('筛除');
    expect(referenceEvidence(undefined).detail).toContain('不是通过');
  });
});

describe('qaVerdicts：产品基准（只提示，不筛除）', () => {
  it('不一致时处置写明"只提示、不自动筛除"，并要求人工确认', () => {
    const bad = productEvidence('INCONSISTENT');
    expect(bad.status).toBe('FAIL');
    expect(bad.effect).toContain('只提示');
    // 关键是否定式措辞：这里必须明确"不自动筛除"，否则容易被读成"和参考图基准一样会筛除"
    expect(bad.effect).toContain('不自动筛除');
    expect(bad.detail).toContain('人工确认');
  });

  it('空值 = 未质检，不是通过', () => {
    expect(productEvidence(undefined).status).toBe('NOT_CHECKED');
    expect(productEvidence(undefined).detail).toContain('不是通过');
  });
});

describe('qaVerdicts：四条证据线的处置不许串味', () => {
  it('只有参考图基准的处置是"筛除"，其余三条都是否定式（不筛除/只报告/现拼核对）', () => {
    const effects = {
      reference: referenceEvidence('INCONSISTENT').effect,
      product: productEvidence('INCONSISTENT').effect,
      rules: rulesEvidence('{}').effect,
      delivery: deliveryEvidence(null).effect
    };
    expect(effects.reference).toContain('筛除');
    expect(effects.reference).not.toContain('不自动筛除');
    expect(effects.product).toContain('不自动筛除');
    expect(effects.rules).toContain('不会自动筛除');
    expect(effects.delivery).toContain('现拼');
  });
});

describe('qaVerdicts：规则体检（只报告不判决）', () => {
  const pass = JSON.stringify({ configured: true, verdict: 'PASS', hardFailed: 0, softFailed: 0, findings: [] });
  const hard = JSON.stringify({
    configured: true, verdict: 'HARD_FAILED', hardFailed: 1, softFailed: 0,
    findings: [{ label: '最短边不小于 2048px', ok: false, level: 'HARD' }]
  });
  const soft = JSON.stringify({
    configured: true, verdict: 'SOFT_ONLY', hardFailed: 0, softFailed: 1,
    findings: [{ label: '主体未贴边（未被画布裁切）', ok: false, level: 'SOFT' }]
  });

  it('没配规则 → 未配置（不是通过）；有结论时解析出未过项', () => {
    expect(rulesEvidence(null).status).toBe('NOT_CONFIGURED');
    expect(rulesEvidence('').status).toBe('NOT_CONFIGURED');
    expect(rulesEvidence('not-json').status).toBe('NOT_CONFIGURED');
    expect(rulesEvidence(pass).status).toBe('OK');
    expect(rulesEvidence(hard).status).toBe('FAIL');
    expect(rulesEvidence(hard).detail).toContain('硬性项');
    expect(rulesEvidence(hard).detail).toContain('最短边不小于 2048px');
    expect(rulesEvidence(soft).detail).toContain('参考项');
  });

  it('体检的处置永远是"只报告不判决"', () => {
    for (const raw of [null, pass, hard, soft]) {
      expect(rulesEvidence(raw).effect).toContain('只报告');
    }
  });

  it('读不出图的结论单独处理（不是通过，也不是未配置）', () => {
    const unreadable = JSON.stringify({ configured: true, verdict: 'UNREADABLE', hardFailed: 1, softFailed: 0, findings: [] });
    expect(rulesEvidence(unreadable).status).toBe('UNCERTAIN');
    expect(rulesEvidence(unreadable).detail).toContain('不是通过');
  });

  it('parseRuleReport 容错：坏 JSON / 缺字段都按未配置', () => {
    expect(parseRuleReport('{bad').configured).toBe(false);
    expect(parseRuleReport('{}').verdict).toBe('NOT_CONFIGURED');
    expect(parseRuleReport(hard).failedLabels).toEqual(['最短边不小于 2048px']);
  });
});

describe('qaVerdicts：交付产物（核对，不评判好坏）', () => {
  const delivery: DeliveryVO = {
    renderer: 'MULTI_IMAGE',
    rendererName: '多图交付渲染器',
    currentVersion: 1,
    artifacts: [
      { id: 1, renderer: 'MULTI_IMAGE', rendererName: '多图交付渲染器', version: 1, imageCount: 2, checksum: 'abc123def456' }
    ]
  };

  it('有产物 → 已生成，并显示张数与校验和', () => {
    const ok = deliveryEvidence(delivery);
    expect(ok.status).toBe('READY');
    expect(ok.detail).toContain('2 张');
    expect(ok.detail).toContain('abc123def456');
  });

  it('没有产物 → 未生成（不是通过），且说明包是现拼的', () => {
    const none = deliveryEvidence(null);
    expect(none.status).toBe('NOT_CHECKED');
    expect(none.detail).toContain('还没有生成');
    expect(none.effect).toContain('现拼');
  });
});

describe('qaVerdicts：逐屏行', () => {
  const screens = [
    { id: 11, screenNo: 'S01', screenTypeDesc: '白底主图' },
    { id: 12, screenNo: 'S02', screenTypeDesc: '场景图' }
  ] as DpStoryboardScreenVO[];
  const generations = [
    { id: 101, screenId: 11, candidateNo: 1, status: 'APPROVED', qaVerdict: 'CONSISTENT', productVerdict: 'INCONSISTENT' },
    { id: 102, screenId: 12, candidateNo: 1, status: 'SUCCEEDED' }
  ] as DpGenerationVO[];

  it('按分镜屏顺序出行，未质检的候选如实标未质检', () => {
    const rows = buildQaRows(generations, screens);
    expect(rows.map((r) => r.screenNo)).toEqual(['S01', 'S02']);
    expect(rows[0].evidence.map((e) => e.status)).toEqual(['OK', 'FAIL', 'NOT_CONFIGURED']);
    expect(rows[1].evidence.map((e) => e.status)).toEqual(['NOT_CHECKED', 'NOT_CHECKED', 'NOT_CONFIGURED']);
    // 只筛除不放行：产品基准 FAIL 也照样出一行（页面把它摆出来，由人判断）
    expect(rows[0].evidence[1].effect).toContain('只提示');
  });

  it('分镜里找不到的候选放最后并标「未归属屏」（不丢数据、也不编屏号）', () => {
    const rows = buildQaRows([...generations, { id: 103, screenId: 999, candidateNo: 2, status: 'SUCCEEDED' } as DpGenerationVO], screens);
    expect(rows.map((r) => r.screenNo)).toEqual(['S01', 'S02', '未归属屏']);
  });

  it('取不到分镜也能出表（按候选列，不编屏号）', () => {
    const rows = buildQaRows(generations, []);
    expect(rows).toHaveLength(2);
    expect(rows.every((r) => r.screenNo === '未归属屏')).toBe(true);
  });
});

describe('qaVerdicts：口径文字与状态样式', () => {
  it('口径三句写全（各自独立 / 未检查不是通过 / 只有参考图基准筛除）', () => {
    expect(QA_SEMANTICS).toHaveLength(3);
    expect(QA_SEMANTICS[1]).toContain('不是通过');
    expect(QA_SEMANTICS[2]).toContain('只筛除不放行');
  });

  it('六种状态都有中文标签，样式映射不越界', () => {
    expect(Object.keys(QA_STATUS_LABELS).toSorted()).toEqual(
      ['FAIL', 'NOT_CHECKED', 'NOT_CONFIGURED', 'OK', 'READY', 'UNCERTAIN'].toSorted()
    );
    expect(qaStatusType('OK')).toBe('success');
    expect(qaStatusType('FAIL')).toBe('danger');
    expect(qaStatusType('UNCERTAIN')).toBe('warning');
    expect(qaStatusType('NOT_CHECKED')).toBe('info');
    expect(qaStatusType('NOT_CONFIGURED')).toBe('info');
  });
});
