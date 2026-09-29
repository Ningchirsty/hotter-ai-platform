import { describe, expect, it } from 'vitest';
import { formatDefaultSpec, formatScenarioLine } from './scenarioText';
import type { ScenarioOutputSpec } from '@/api/creative/scenario';

/**
 * 场景配置展示的纯函数测试（V0.2 D 阶段第一刀）。
 *
 * <p>为什么钉住这些：这一行是"给人看的场景口径"（交付类型 / 默认规格 / 配置步骤），
 * 拼错不会报错，只会让人看到错误的信息——例如把非默认规格显示成默认，
 * 或把 AUTO 的高度显示成数字。所以格式化规则必须是纯函数并被断言。</p>
 */
describe('场景配置展示行', () => {
  const specs: ScenarioOutputSpec[] = [
    { specCode: 'TAOBAO_DETAIL', width: 750, heightMode: 'AUTO', isDefault: '1' },
    { specCode: 'TMALL_DETAIL', width: 790, heightMode: 'AUTO', isDefault: '0' },
    { specCode: 'ECOM_MAIN_IMAGE', width: 800, height: 800, heightMode: 'FIXED', isDefault: '0' },
  ];

  it('默认规格：取 isDefault=1 的那条，AUTO 高度显示为 AUTO', () => {
    expect(formatDefaultSpec(specs)).toBe('TAOBAO_DETAIL 750×AUTO');
  });

  it('默认规格不在第一位时也要取默认那条（不依赖后端顺序）', () => {
    const shuffled = [specs[1], specs[0], specs[2]];
    expect(formatDefaultSpec(shuffled)).toBe('TAOBAO_DETAIL 750×AUTO');
  });

  it('固定高度显示真实高度；没有规格/缺字段返回空串', () => {
    expect(formatDefaultSpec([specs[2]])).toBe('ECOM_MAIN_IMAGE 800×800');
    expect(formatDefaultSpec([])).toBe('');
    expect(formatDefaultSpec(undefined)).toBe('');
    expect(formatDefaultSpec([{ specCode: 'X' }])).toBe('');   // 没有宽度不编造
  });

  it('展示行包含场景名（编码）/ 默认规格 / 配置步骤数', () => {
    expect(formatScenarioLine('商品详情页', 'ECOM_DETAIL', 'TAOBAO_DETAIL 750×AUTO', 10))
      .toBe('商品详情页（ECOM_DETAIL） · 默认规格 TAOBAO_DETAIL 750×AUTO · 配置 10 步');
  });

  it('缺少名字时用编码；缺少规格或步骤时省略对应片段；全空则返回空串', () => {
    expect(formatScenarioLine(undefined, 'ECOM_DETAIL', '', 0)).toBe('ECOM_DETAIL');
    expect(formatScenarioLine('商品详情页', 'ECOM_DETAIL', '', 10)).toBe('商品详情页（ECOM_DETAIL） · 配置 10 步');
    expect(formatScenarioLine(undefined, '', '', 0)).toBe('');
  });
});
