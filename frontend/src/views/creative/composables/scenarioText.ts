import type { ScenarioOutputSpec } from '@/api/creative/scenario';

/**
 * 场景配置的**纯展示格式化**（V0.2 D 阶段第一刀）。
 *
 * <p>刻意独立成模块、不 import 任何运行时依赖（只 import type，编译后被擦除）：
 * 这样它可以被 vitest 在 node 环境下直接断言，不需要 jsdom——
 * 与 `creativeFlowSteps.ts` 同样的做法。规则本身（取哪条规格、AUTO/FIXED 怎么写）
 * 拼错不会报错，只会让人看到错的场景口径，所以必须能被单测钉住。</p>
 */

/**
 * 把输出规格列表格式化成"默认规格"一句话。
 *
 * @param specs 输出规格（后端已把默认排最前，这里再兜一层）
 * @returns 形如 `TAOBAO_DETAIL 750×AUTO`；没有可用规格返回空串（不编造）
 */
export function formatDefaultSpec(specs?: ScenarioOutputSpec[] | null): string {
  if (!specs || specs.length === 0) {
    return '';
  }
  const def = specs.find((s) => s?.isDefault === '1') || specs[0];
  if (!def?.specCode || !def?.width) {
    return '';
  }
  const height = def.heightMode === 'FIXED' && def.height ? `${def.height}` : 'AUTO';
  return `${def.specCode} ${def.width}×${height}`;
}

/**
 * 组装展示行：场景名（编码） · 默认规格 · 配置步骤数。
 *
 * @param deliveryName 交付类型展示名（空则用编码）
 * @param deliveryType 交付类型编码
 * @param specText     默认规格文本（空则省略这一段）
 * @param stepCount    配置步骤数（<=0 则省略这一段）
 * @returns 展示文本；什么信息都没有时返回空串（调用方据此不显示这一行）
 */
export function formatScenarioLine(deliveryName: string | undefined, deliveryType: string,
                                   specText: string, stepCount: number): string {
  const parts: string[] = [];
  if (deliveryName) {
    parts.push(`${deliveryName}（${deliveryType}）`);
  } else if (deliveryType) {
    parts.push(deliveryType);
  }
  if (specText) {
    parts.push(`默认规格 ${specText}`);
  }
  if (stepCount > 0) {
    parts.push(`配置 ${stepCount} 步`);
  }
  return parts.join(' · ');
}
