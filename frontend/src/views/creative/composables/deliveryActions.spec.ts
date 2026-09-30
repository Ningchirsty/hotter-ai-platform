import { describe, expect, it } from 'vitest';
import { CONFIRMABLE_RENDERERS, canConfirmDelivery, canvasModeOf } from './deliveryActions';
import type { DeliveryVO } from '@/api/creative/types';

/**
 * 交付形态的前端判定（V0.2 R52）。
 *
 * <p>钉住两件事：① 海报这类"多档成品图"的版式步不能显示"渲染机排版 V0.8"（后端会拒）；
 * ② 「确认交付」只对后端白名单里的渲染器开放，且要有产物、项目未完成。</p>
 */
const delivery = (patch: Partial<DeliveryVO>): DeliveryVO => ({
  taskId: 1,
  deliveryType: 'BRAND_POSTER',
  currentVersion: 1,
  artifacts: [],
  ...patch
});

describe('版式画布形态', () => {
  it('长图走长图排版画布', () => {
    expect(canvasModeOf(delivery({ renderMode: 'LONGPAGE', renderer: 'LONG_PAGE' }))).toBe('LONG_PAGE');
  });

  it('海报与多图包走「成品图」画布', () => {
    expect(canvasModeOf(delivery({ renderMode: 'POSTER', renderer: 'POSTER' }))).toBe('DELIVERY');
    expect(canvasModeOf(delivery({ renderMode: 'MULTI_IMAGE', renderer: 'MULTI_IMAGE' }))).toBe('DELIVERY');
  });

  it('读不到交付视图时按长图处理（与 R40 之前的行为一致，不突然多出按钮）', () => {
    expect(canvasModeOf(null)).toBe('LONG_PAGE');
    expect(canvasModeOf(delivery({}))).toBe('LONG_PAGE');
  });
});

describe('确认交付的可见条件', () => {
  it('多图包与海报可确认；长图不可（走上传精修最终版）', () => {
    expect(CONFIRMABLE_RENDERERS).toEqual(['MULTI_IMAGE', 'POSTER']);
    expect(canConfirmDelivery(delivery({ renderer: 'MULTI_IMAGE' }), 'FINAL_REVIEW')).toBe(true);
    expect(canConfirmDelivery(delivery({ renderer: 'POSTER' }), 'V08_READY')).toBe(true);
    expect(canConfirmDelivery(delivery({ renderer: 'LONG_PAGE' }), 'FINAL_REVIEW')).toBe(false);
    expect(canConfirmDelivery(delivery({ renderer: null }), 'FINAL_REVIEW')).toBe(false);
  });

  it('还没有交付产物时不显示', () => {
    expect(canConfirmDelivery(delivery({ renderer: 'POSTER', currentVersion: 0 }), 'V08_READY')).toBe(false);
  });

  it('已完成的项目不再显示（避免重复收尾）', () => {
    expect(canConfirmDelivery(delivery({ renderer: 'POSTER' }), 'COMPLETED')).toBe(false);
  });
});
