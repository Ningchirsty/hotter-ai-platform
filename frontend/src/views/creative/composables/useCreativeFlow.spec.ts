import { describe, expect, it } from 'vitest';
import { resolveActiveNo, stageReached, stageStepOf } from './creativeFlowSteps';

/**
 * 流程指引线的核心判定测试。
 *
 * <p>为什么必须钉住：这条线要回答「我现在在八个环节的哪一步」，一旦映射错位，
 * 5 个页面会一起把人指到错误的环节上——而且看起来还挺像对的。所以顺序映射与
 * 「当前步」的规则都必须是纯函数并被断言，而不是只能靠人肉点页面发现。</p>
 */
describe('阶段 → 步骤映射', () => {
  it('基因区间落在第 3 步', () => {
    expect(stageStepOf('MATERIAL_READY')).toBe(3);
    expect(stageStepOf('DNA_GENERATING')).toBe(3);
    expect(stageStepOf('DNA_REVIEW')).toBe(3);
    expect(stageStepOf('DNA_LOCKED')).toBe(3);
  });

  it('方向 / 分镜 / 视觉门 / 出图 / 排版各归其步', () => {
    expect(stageStepOf('DIRECTION_REVIEW')).toBe(4);
    expect(stageStepOf('DIRECTION_LOCKED')).toBe(4);
    expect(stageStepOf('STORYBOARD_REVIEW')).toBe(5);
    expect(stageStepOf('STORYBOARD_LOCKED')).toBe(5);
    expect(stageStepOf('VISUAL_GATE')).toBe(6);
    expect(stageStepOf('VISUAL_LOCKED')).toBe(6);
    expect(stageStepOf('PRODUCING')).toBe(7);
    expect(stageStepOf('QA_PROCESSING')).toBe(7);
    expect(stageStepOf('LAYOUT_PROCESSING')).toBe(8);
    expect(stageStepOf('V08_READY')).toBe(8);
    expect(stageStepOf('DESIGN_REFINING')).toBe(8);
    expect(stageStepOf('FINAL_REVIEW')).toBe(8);
    expect(stageStepOf('COMPLETED')).toBe(8);
  });

  it('未知阶段按「基因」起步，不猜成已完成', () => {
    expect(stageStepOf(null)).toBe(3);
    expect(stageStepOf(undefined)).toBe(3);
    expect(stageStepOf('SOMETHING_NEW')).toBe(3);
  });

  it('阶段推进是单调的（跳步也算到达）', () => {
    expect(stageReached('STORYBOARD_LOCKED', 'DNA_LOCKED')).toBe(true);
    expect(stageReached('DNA_REVIEW', 'DNA_LOCKED')).toBe(false);
    expect(stageReached('COMPLETED', 'VISUAL_LOCKED')).toBe(true);
    expect(stageReached('SOMETHING_NEW', 'DNA_LOCKED')).toBe(false);
  });
});

describe('当前步解析', () => {
  const flags = (...done: number[]) => {
    // 用 Array.from 而不是 new Array(n)：后者语义含糊（长度还是唯一元素），lint 也会拦
    const arr = Array.from({ length: 8 }, () => false);
    done.forEach((no) => (arr[no - 1] = true));
    return arr;
  };

  it('资料或事实没做完时，当前步就是它们（阶段还停在 MATERIAL_READY）', () => {
    expect(resolveActiveNo(flags(), 'MATERIAL_READY')).toBe(1);
    expect(resolveActiveNo(flags(1), 'MATERIAL_READY')).toBe(2);
  });

  it('前置都做完后，当前步是「还没做完的第一步」', () => {
    expect(resolveActiveNo(flags(1, 2), 'MATERIAL_READY')).toBe(3);
    expect(resolveActiveNo(flags(1, 2, 3), 'DNA_LOCKED')).toBe(4);
    expect(resolveActiveNo(flags(1, 2, 3, 4), 'DIRECTION_LOCKED')).toBe(5);
    expect(resolveActiveNo(flags(1, 2, 3, 4, 5), 'STORYBOARD_LOCKED')).toBe(6);
  });

  it('视觉门已过时当前步是出图，而不是已完成的门（这是最容易错的一处）', () => {
    // 阶段 VISUAL_LOCKED 落在第 6 步区间，但第 6 步已完成 → 当前步应为第 7 步
    expect(stageStepOf('VISUAL_LOCKED')).toBe(6);
    expect(resolveActiveNo(flags(1, 2, 3, 4, 5, 6), 'VISUAL_LOCKED')).toBe(7);
  });

  it('走完出图后当前步是排版', () => {
    expect(resolveActiveNo(flags(1, 2, 3, 4, 5, 6, 7), 'LAYOUT_PROCESSING')).toBe(8);
  });

  it('返工（阶段被退回）时当前步跟着回到那一步', () => {
    // 曾经做到分镜，后来基因被退回重做：第 3 步未完成
    expect(resolveActiveNo(flags(1, 2), 'DNA_REVIEW')).toBe(3);
  });

  it('八步全完成时没有当前步（返回 0，线上不该有脉冲）', () => {
    expect(resolveActiveNo(flags(1, 2, 3, 4, 5, 6, 7, 8), 'COMPLETED')).toBe(0);
  });
});
