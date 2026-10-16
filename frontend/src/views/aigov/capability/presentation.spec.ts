import { describe, expect, it } from 'vitest';
import type { AigCapabilityOverviewVO } from '@/api/aigov/capability/overview-types';
import type { AigInvokeVO } from '@/api/aigov/invoke';
import {
  assistantBoundaryNote,
  candidateStateOptions,
  capabilityReadiness,
  cardSummary,
  coverageText,
  decisionMeta,
  deleteConfirmText,
  excludedHits,
  extractModelToken,
  healthMeta,
  hitsForModel,
  issueFilterOptions,
  issueMeta,
  issueTags,
  matchPresetQuestion,
  otherHits,
  parseTags,
  policySuggestions,
  presetQuestions,
  referenceSummary,
  referenceTypeMeta,
  riskHint,
  statusMeta,
  usageMeta,
  validateJsonText,
  walkthroughSections
} from './presentation';

/**
 * 能力域展示口径测试（P0）。
 *
 * <p>重点钉两类：①"卡片为什么红"的口径（问题码/健康/覆盖度）②**试调助手的六段式渲染**
 * ——它是本次改造的核心体验，而本仓没有 DOM 测试环境，只能靠纯函数单测把它钉住。</p>
 */
describe('capability presentation', () => {
  it('问题码 → 标签与原因；空集合表示链路完整；认不出的码如实回显', () => {
    expect(issueMeta().label).toBe('链路完整');
    expect(issueMeta('NO_MODEL_CANDIDATE').type).toBe('danger');
    expect(issueMeta('POLICY_INCOMPLETE').type).toBe('warning');
    expect(issueMeta('SOMETHING_NEW').label).toBe('SOMETHING_NEW');

    expect(issueTags(undefined).map((tag) => tag.label)).toEqual(['链路完整']);
    expect(issueTags(['NO_MODEL_CANDIDATE', 'NO_ROUTE_POLICY']).map((tag) => tag.type)).toEqual([
      'danger',
      'danger'
    ]);
    expect(riskHint([])).toContain('链路完整');
    expect(riskHint(['NO_MODEL_CANDIDATE'])).toContain('调用一定失败');
  });

  it('★ 候选健康：UNKNOWN 不是绿色（从未探测过不能被当成健康）', () => {
    expect(healthMeta('HEALTHY').type).toBe('success');
    expect(healthMeta('UNHEALTHY').type).toBe('warning');
    expect(healthMeta('UNKNOWN').type).toBe('info');
    expect(healthMeta('UNKNOWN').label).toContain('尚未探测');
    expect(healthMeta('NONE').type).toBe('danger');
    expect(healthMeta(undefined).type).toBe('gray');
  });

  it('策略覆盖：0 要写成"未配置"而不是 0/4', () => {
    expect(coverageText(0, 4)).toBe('0/4（未配置）');
    expect(coverageText(3, 4)).toBe('3/4');
    expect(coverageText(undefined, undefined)).toBe('0/4（未配置）');
  });

  it('卡片摘要把候选/策略/调用讲成人话（无调用时不显示"失败 0"）', () => {
    const vo: AigCapabilityOverviewVO = {
      candidateCount: 2,
      policyCoveredLevels: 4,
      policyTotalLevels: 4,
      calls24h: 812,
      fails24h: 9
    };
    expect(cardSummary(vo)).toBe('候选 2 · 策略 4/4 · 近24h 812（失败 9）');
    expect(cardSummary({ candidateCount: 0, policyCoveredLevels: 0, calls24h: 0 })).toContain(
      '近24h 无调用'
    );
    expect(cardSummary(undefined)).toBe('');
  });

  it('标签解析、状态与用途口径', () => {
    expect(parseTags(' OCR , TEXT ,, ')).toEqual(['OCR', 'TEXT']);
    expect(parseTags(undefined)).toEqual([]);
    expect(statusMeta('0').label).toBe('启用');
    expect(statusMeta('1').type).toBe('gray');
    expect(usageMeta('primary').label).toBe('主选');
    expect(usageMeta('GRAY').label).toBe('灰度');
    expect(usageMeta(undefined).label).toBe('未设置');
  });

  it('筛选项与服务端取值一一对应（问题码筛选项来自同一份元数据）', () => {
    expect(issueFilterOptions()[0].value).toBe('');
    expect(issueFilterOptions().map((item) => item.value)).toContain('NO_MODEL_CANDIDATE');
    expect(candidateStateOptions().map((item) => item.value)).toEqual(['', 'NO', 'HAS']);
  });

  it('★ policyHits 拆分：排除类 vs 其他；按模型定位"为什么排除它"', () => {
    const hits = [
      "策略 allowExternal='N'：本次调用仅允许本地/集团共享部署模型，外部部署模型将被排除",
      '排除 local-sdxl（modelKey=local-sdxl）：健康状态=UNHEALTHY',
      '排除 modelId=88：数据等级上限=INTERNAL 低于本次等级'
    ];
    expect(otherHits(hits)).toHaveLength(1);
    expect(excludedHits(hits)).toHaveLength(2);
    expect(hitsForModel(hits, 'local-sdxl')).toHaveLength(1);
    expect(hitsForModel(hits, '88')).toHaveLength(1);
    // 没有排除它 → 空数组，由调用方如实回答"本次没有排除它"
    expect(hitsForModel(hits, 'qwen-plus')).toEqual([]);
    expect(hitsForModel(undefined, 'x')).toEqual([]);
    expect(hitsForModel(hits, '')).toEqual([]);
  });

  it('★ 六段式：命中模型时给出假设、排除原因、最终选择与边界说明', () => {
    const vo: AigInvokeVO = {
      decision: 'MODEL',
      modelKey: 'bluocto-image-v2',
      deploymentType: 'EXTERNAL_ENTERPRISE',
      invoker: 'OpenAiCompatibleInvoker',
      externalCall: true,
      policyHits: ['排除 local-sdxl（modelKey=local-sdxl）：健康状态=UNHEALTHY'],
      pendingConfirm: ['对外发送前']
    };
    const sections = walkthroughSections(vo, { dataLevel: 'INTERNAL' });

    expect(sections).toHaveLength(6);
    expect(sections[0].lines.join(' ')).toContain('数据等级=INTERNAL');
    expect(sections[0].lines.join(' ')).toContain('命中模型');
    expect(sections[2].lines[0]).toContain('local-sdxl');
    expect(sections[3].lines).toContain('模型：bluocto-image-v2');
    expect(sections[3].lines.join(' ')).toContain('是否外发：是');
    expect(sections[4].lines).toEqual(['对外发送前']);
    expect(sections[5].lines[0]).toBe(assistantBoundaryNote());
  });

  it('★ 六段式：转人工/被拒绝时不谎报"选中了谁"；没有排除项就明说', () => {
    const denied: AigInvokeVO = { decision: 'DENIED', reason: '严格级不允许外发', policyHits: [] };
    const sections = walkthroughSections(denied, { dataLevel: 'STRICT', allowExternal: false });

    expect(sections[0].lines.join(' ')).toContain('策略拒绝');
    expect(sections[0].lines.join(' ')).toContain('禁止外发');
    expect(sections[2].lines[0]).toContain('没有候选被排除');
    expect(sections[3].lines[0]).toContain('没有选中任何模型');
    expect(sections[4].lines).toEqual(['无']);
  });

  it('边界说明必须同时提到"不写审计/不扣配额"与"不校验授权与配额"', () => {
    const note = assistantBoundaryNote();
    expect(note).toContain('不写审计');
    expect(note).toContain('不扣配额');
    expect(note).toContain('不校验调用授权');
    expect(note).toContain('试调通过');
  });

  it('自由输入按关键词路由到固定 4 类；认不出返回 null（由界面给可用问题）', () => {
    expect(presetQuestions()).toHaveLength(4);
    expect(matchPresetQuestion('为什么排除了 local-sdxl？')).toBe('EXCLUDE');
    expect(matchPresetQuestion('如果等级是严格会怎样')).toBe('LEVEL');
    expect(matchPresetQuestion('帮我补一条策略建议')).toBe('SUGGEST');
    expect(matchPresetQuestion('这条链路会走哪条路？')).toBe('WALKTHROUGH');
    expect(matchPresetQuestion('今天天气怎么样')).toBeNull();
    expect(matchPresetQuestion('   ')).toBeNull();
  });

  it('策略建议：无候选/策略不全都有对应建议，且末尾必须声明"不会自动写入"', () => {
    const suggestions = policySuggestions(undefined, {
      candidateCount: 0,
      policyCoveredLevels: 2,
      policyTotalLevels: 4
    });
    expect(suggestions.join(' ')).toContain('挂一个模型候选');
    expect(suggestions.join(' ')).toContain('还有 2 个数据等级没有策略');
    expect(suggestions[suggestions.length - 1]).toContain('不会自动写入');

    const reasonSuggestions = policySuggestions({ reasonCode: 'NO_MODEL_BOUND' }, {
      candidateCount: 1,
      policyCoveredLevels: 4,
      policyTotalLevels: 4
    });
    expect(reasonSuggestions.join(' ')).toContain('没有绑定模型');
  });

  it('路由结论标签：三类 + 未知', () => {
    expect(decisionMeta('MODEL').type).toBe('success');
    expect(decisionMeta('MANUAL').type).toBe('warning');
    expect(decisionMeta('DENIED').type).toBe('danger');
    expect(decisionMeta(undefined).label).toContain('未返回结论');
  });

  it('JSON 文本校验：空值合法；非法给出可读原因（填了才必须合法）', () => {
    expect(validateJsonText(undefined)).toBeNull();
    expect(validateJsonText('   ')).toBeNull();
    expect(validateJsonText('{"a":1}')).toBeNull();
    expect(validateJsonText('{a:1}')).toContain('JSON 格式不合法');
  });

  it('从自然语言里抽模型 token：抽不到就返回空串（不硬凑解释）', () => {
    expect(extractModelToken('为什么排除了 local-sdxl？')).toBe('local-sdxl');
    expect(extractModelToken('为什么排除 modelId=88')).toBe('modelId');
    expect(extractModelToken('数据等级是严格会怎样')).toBe('');
    expect(extractModelToken('   ')).toBe('');
  });

  it('★ 影响面：无引用要明说"没有任何引用"（这句话正是"可安全删除"的依据）', () => {
    expect(referenceSummary([])).toContain('没有任何 Agent 版本');
    expect(referenceSummary(undefined)).toContain('没有任何');
    const summary = referenceSummary([
      { refType: 'AGENT_VERSION', count: 1 },
      { refType: 'TASK', count: 12 },
      { refType: 'DRAFT', count: 2 }
    ]);
    expect(summary).toContain('共 3 类引用');
    expect(summary).toContain('Agent 版本 1 个');
    expect(summary).toContain('历史任务 12 条');
    expect(summary).toContain('训练台草稿 2 条');
  });

  it('★ 删除确认框：把引用方逐条写出来（超过 5 条折叠），并给出"停用可恢复"的替代', () => {
    const withRefs = deleteConfirmText('图片生成', 'image_generation', [
      { refType: 'AGENT_VERSION', refLabel: 'Agent「图片助手」版本 1.0.0（已发布）' },
      { refType: 'TASK', count: 12, refLabel: '历史任务 12 条' }
    ]);
    expect(withRefs).toContain('删除能力「图片生成」？');
    expect(withRefs).toContain('图片助手');
    expect(withRefs).toContain('历史任务 12 条');
    expect(withRefs).toContain('停用');
    expect(withRefs).toContain('孤儿配置');

    const many = deleteConfirmText(undefined, 'code_x', Array.from({ length: 7 }, (_, index) => ({
      refType: 'AGENT_VERSION',
      refLabel: `Agent#${index}`
    })));
    expect(many).toContain('另有 2 类引用');
    expect(many).toContain('删除能力「code_x」？');

    const none = deleteConfirmText('某能力', 'code_y', []);
    expect(none).toContain('没有任何 Agent 版本');
  });

  it('★ 就绪度：能力不存在/已停用是阻断项；缺候选缺策略只是提示项（草稿允许存半成品）', () => {
    const missing = capabilityReadiness(undefined);
    expect(missing.blockers.join(' ')).toContain('能力不存在');
    expect(missing.warnings).toEqual([]);

    const disabled = capabilityReadiness({ status: '1', candidateCount: 1, policyCoveredLevels: 4 });
    expect(disabled.blockers.join(' ')).toContain('已停用');
    expect(disabled.warnings).toEqual([]);

    const halfBaked = capabilityReadiness({ status: '0', candidateCount: 0, policyCoveredLevels: 0 });
    expect(halfBaked.blockers).toEqual([]);
    expect(halfBaked.warnings.join(' ')).toContain('缺模型候选');
    expect(halfBaked.warnings.join(' ')).toContain('缺路由策略（0/4）');

    // 就绪：没有任何问题 → 空文案（界面据此不显示任何提示）
    const ready = capabilityReadiness({
      status: '0',
      candidateCount: 2,
      candidateHealthAgg: 'HEALTHY',
      policyCoveredLevels: 4,
      policyTotalLevels: 4
    });
    expect(ready.blockers).toEqual([]);
    expect(ready.warnings).toEqual([]);
    expect(ready.text).toBe('');
  });

  it('候选状态：全部不健康 / 从未探测 要分开说（不能把"没探测过"当健康）', () => {
    const unhealthy = capabilityReadiness({
      status: '0',
      candidateCount: 3,
      candidateHealthAgg: 'UNHEALTHY',
      policyCoveredLevels: 4
    });
    expect(unhealthy.warnings.join(' ')).toContain('全部不健康');

    const unknown = capabilityReadiness({
      status: '0',
      candidateCount: 3,
      candidateHealthAgg: 'UNKNOWN',
      policyCoveredLevels: 4
    });
    expect(unknown.warnings.join(' ')).toContain('尚未探测');

    const partial = capabilityReadiness({
      status: '0',
      candidateCount: 1,
      candidateHealthAgg: 'HEALTHY',
      policyCoveredLevels: 2,
      policyTotalLevels: 4
    });
    expect(partial.warnings.join(' ')).toContain('路由策略不全（2/4）');
  });
});
