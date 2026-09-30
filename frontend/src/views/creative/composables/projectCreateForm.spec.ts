import { describe, expect, it } from 'vitest';
import {
  DEFAULT_DELIVERABLE_TYPE,
  labelOfDeliveryType,
  pickDefaultDeliveryType
} from './projectCreateForm';
import type { ScenarioDeliveryType } from '@/api/creative/scenario';

/**
 * 新建项目表单的交付类型纯逻辑（V0.2 R51）。
 *
 * <p>钉住的是**一个口径**：不选交付类型时默认选哪个。这个口径必须与后端一致
 * （后端不传时默认商品详情页）；错位的表现很隐蔽——界面默认 A、接口默认 B，
 * 谁也不会立刻发现，直到某天有人换了默认值、老用户建出来的项目类型变了。</p>
 */
const TYPES: ScenarioDeliveryType[] = [
  { deliveryType: 'ECOM_DETAIL', deliveryName: '商品详情页' },
  { deliveryType: 'MAIN_IMAGE', deliveryName: '商品主图' },
  { deliveryType: 'BRAND_POSTER', deliveryName: '品牌海报' }
];

describe('新建项目的交付类型', () => {
  it('默认仍然选商品详情页（与后端默认值同口径）', () => {
    expect(DEFAULT_DELIVERABLE_TYPE).toBe('ECOM_DETAIL');
    expect(pickDefaultDeliveryType(TYPES)).toBe('ECOM_DETAIL');
  });

  it('默认类型没启用时选列表第一个（不摆空下拉）', () => {
    const types = TYPES.filter((t) => t.deliveryType !== 'ECOM_DETAIL');
    expect(pickDefaultDeliveryType(types)).toBe('MAIN_IMAGE');
  });

  it('配置读不到时返回空串，交给后端兜底（不编造类型）', () => {
    expect(pickDefaultDeliveryType([])).toBe('');
    expect(pickDefaultDeliveryType(null)).toBe('');
    expect(pickDefaultDeliveryType([{ deliveryType: '' }])).toBe('');
  });

  it('下拉文案带编码，配置没填名称时只显示编码', () => {
    expect(labelOfDeliveryType(TYPES[2])).toBe('品牌海报（BRAND_POSTER）');
    expect(labelOfDeliveryType({ deliveryType: 'X_ONLY' })).toBe('X_ONLY');
    expect(labelOfDeliveryType({ deliveryType: 'SAME', deliveryName: 'SAME' })).toBe('SAME');
    expect(labelOfDeliveryType(null)).toBe('');
  });
});
