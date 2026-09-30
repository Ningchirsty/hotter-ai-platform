import type { DeliveryVO } from '@/api/creative/types';

/**
 * 交付形态在前端的**纯判定**（V0.2 R52）。
 *
 * <p>为什么单独一个文件：这些判定是"页面上该显示哪个动作"的唯一依据，判错的表现是
 * <b>按钮点下去报错</b>（例如给海报显示"渲染机排版 V0.8"，而后端只为长图开了这条路），
 * 或者反过来把该有的动作藏起来。抽成纯函数就能直接单测，不必起浏览器。</p>
 *
 * <p><b>与后端同口径</b>：后端 {@code CreativeDeliveryServiceImpl} 用一张白名单
 * （{@code CONFIRMABLE_RENDERERS}）判"能不能确认交付"，判据是**解析出来的渲染器**；
 * 这里用的是交付视图里同一份解析结果（{@code delivery.renderer}）。加新形态时两边一起改。</p>
 *
 * @author creative
 */

/**
 * 允许用「确认交付」收尾的渲染器（与后端白名单一一对应）。
 *
 * <p>它们的共同点：交付物是**一组成品图**（多图包 / 多档海报），没有"长图精修版"这一步。</p>
 */
export const CONFIRMABLE_RENDERERS = ['MULTI_IMAGE', 'POSTER'];

/**
 * 「版式」这一步该显示哪种画布。
 *
 * <p>长图（LONGPAGE）走长图排版与逐版本审核；其余形态（海报、多图包）的版式产出是
 * **交付产物**，画布要给的是"生成成品图"，不是"渲染机排版 V0.8"——
 * 后端对非长图调用排版接口会明确拒绝。</p>
 *
 * @param delivery 交付视图（可为空）
 * @returns 画布形态
 */
export function canvasModeOf(delivery?: DeliveryVO | null): 'LONG_PAGE' | 'DELIVERY' {
  const mode = (delivery?.renderMode || '').trim().toUpperCase();
  return mode && mode !== 'LONGPAGE' ? 'DELIVERY' : 'LONG_PAGE';
}

/**
 * 能不能「确认交付」：形态允许 + 已经有交付产物 + 项目还没到「已完成」。
 *
 * @param delivery 交付视图（可为空）
 * @param stage    当前视觉阶段（可空）
 * @returns 可确认时返回 true
 */
export function canConfirmDelivery(delivery?: DeliveryVO | null, stage?: string | null): boolean {
  const renderer = (delivery?.renderer || '').trim().toUpperCase();
  if (!CONFIRMABLE_RENDERERS.includes(renderer)) {
    return false;
  }
  if ((delivery?.currentVersion ?? 0) <= 0) {
    return false;
  }
  return (stage || '').trim().toUpperCase() !== 'COMPLETED';
}
