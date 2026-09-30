import type { ScenarioDeliveryType } from '@/api/creative/scenario';

/**
 * 「新建视觉项目」表单里与交付类型有关的**纯逻辑**（V0.2 R51）。
 *
 * <p><b>为什么单独一个文件</b>：这里有一个必须与后端对齐的口径——不选交付类型时选哪个。
 * 它写在页面里就测不了，而口径错位的表现很隐蔽（界面默认 A、接口默认 B，换默认值时没人发现）。
 * 抽成纯函数后，`projectCreateForm.spec.ts` 能直接钉住它。</p>
 *
 * @author creative
 */

/**
 * 默认交付类型：与后端 `CreativeProjectServiceImpl#createProject` 不传交付类型时的默认值一致
 * （商品详情页）。两边必须同口径——所以这里只有一处定义，页面与测试都引用它。
 */
export const DEFAULT_DELIVERABLE_TYPE = 'ECOM_DETAIL';

/**
 * 选默认交付类型。
 *
 * <p>口径：优先 {@link DEFAULT_DELIVERABLE_TYPE}（保持老用户手感）；它不在启用列表里时选第一个；
 * 一个都没有（配置读不到）时返回空串——**不编造**，留给后端按默认值处理。</p>
 *
 * @param types 已启用的交付类型（配置接口返回，顺序即展示顺序）
 * @returns 交付类型编码；无可用时为空串
 */
export function pickDefaultDeliveryType(types?: ScenarioDeliveryType[] | null): string {
  const rows = (types || []).filter((t) => Boolean(t?.deliveryType));
  if (!rows.length) {
    return '';
  }
  const preferred = rows.find((t) => t.deliveryType === DEFAULT_DELIVERABLE_TYPE);
  return preferred?.deliveryType || rows[0].deliveryType || '';
}

/**
 * 交付类型下拉的文案（形如 `品牌海报（BRAND_POSTER）`）。
 *
 * <p>编码与名称相同时不重复显示（配置没填名称时只显示编码）。</p>
 *
 * @param type 交付类型
 * @returns 展示文案
 */
export function labelOfDeliveryType(type?: ScenarioDeliveryType | null): string {
  const code = (type?.deliveryType || '').trim();
  const name = (type?.deliveryName || '').trim();
  if (name && code && name !== code) {
    return `${name}（${code}）`;
  }
  return name || code;
}
