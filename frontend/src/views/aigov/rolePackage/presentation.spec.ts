import { describe, expect, it } from 'vitest';
import {
  canDisable,
  groupActionsByCategory,
  launchModeLabel,
  parseManifest,
  publishTargets,
  releaseStatusMeta
} from './presentation';

/**
 * 岗位包管理台的展示层测试。
 *
 * 这里断言的都是"界面上会显示成什么"——显示错了不会报错，只会让人做出错误决定：
 * 比如把 DISABLED 混进"发布"按钮，或者把未知状态显示成"已发布"。
 */
describe('岗位包管理台展示层', () => {
  it('未知状态原样显示并标灰，不冒充已发布', () => {
    expect(releaseStatusMeta('PUBLISHED').tag).toBe('success');
    expect(releaseStatusMeta('WEIRD_STATE')).toEqual({ label: 'WEIRD_STATE', tag: 'info' });
    expect(releaseStatusMeta(undefined)).toEqual({ label: '未知', tag: 'info' });
  });

  it('发布按钮的目标只取 TESTING/PUBLISHED，不混入停用', () => {
    expect(publishTargets(['DISABLED', 'TESTING', 'PUBLISHED'])).toEqual([
      { code: 'TESTING', label: '测试中（仅预览）' },
      { code: 'PUBLISHED', label: '已发布' }
    ]);
    // DRAFT→PUBLISHED 不是允许的边，服务端不会给；这里给空数组而不是自己补上
    expect(publishTargets(['DISABLED'])).toEqual([]);
    expect(publishTargets(undefined)).toEqual([]);
  });

  it('已停用的版本不再显示停用按钮', () => {
    expect(canDisable('PUBLISHED')).toBe(true);
    expect(canDisable('DRAFT')).toBe(true);
    expect(canDisable('DISABLED')).toBe(false);
  });

  it('按清单声明的分类顺序归组，分类内按 sortOrder 排', () => {
    const groups = groupActionsByCategory(
      [
        { code: 'BANNER', name: '横幅' },
        { code: 'DETAIL_PAGE', name: '详情页' }
      ],
      [
        { actionCode: 'B', categoryCode: 'DETAIL_PAGE', sortOrder: 2 },
        { actionCode: 'A', categoryCode: 'DETAIL_PAGE', sortOrder: 1 },
        { actionCode: 'C', categoryCode: 'BANNER', sortOrder: 0 }
      ]
    );
    expect(groups.map(item => item.code)).toEqual(['BANNER', 'DETAIL_PAGE']);
    expect(groups[0].actions.map(item => item.actionCode)).toEqual(['C']);
    expect(groups[1].actions.map(item => item.actionCode)).toEqual(['A', 'B']);
  });

  it('没有卡片的分类也要出现，并且卡片挂在未声明分类上时不能凭空消失', () => {
    const groups = groupActionsByCategory(
      [{ code: 'EMPTY_CATEGORY', name: '空分类' }],
      [{ actionCode: 'X', categoryCode: 'GONE' }]
    );
    expect(groups.map(item => item.code)).toEqual(['EMPTY_CATEGORY', '（未在清单中声明的分类）']);
    expect(groups[1].actions[0].actionCode).toBe('X');
  });

  it('启动方式显示中文，未知原样显示', () => {
    expect(launchModeLabel('STUDIO')).toBe('专业台');
    expect(launchModeLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
    expect(launchModeLabel(undefined)).toBe('未指定');
  });

  it('清单解析：把分类与策略读回表单', () => {
    const manifest = JSON.stringify({
      schemaVersion: 1,
      roleCode: 'GRAPHIC_DESIGNER_AI',
      version: '1.0.0',
      categories: [
        { code: 'DETAIL_PAGE', name: '详情页设计' },
        { code: 'BANNER', name: '横幅' }
      ],
      defaultCategory: 'DETAIL_PAGE',
      presentation: { audienceScope: 'ASSIGNED_ORG' },
      policy: { defaultDataLevel: 'INTERNAL', maxDataLevel: 'STRICT' }
    });
    const parsed = parseManifest(manifest);
    expect(parsed.ok).toBe(true);
    expect(parsed.categories.map(item => item.code)).toEqual(['DETAIL_PAGE', 'BANNER']);
    expect(parsed.defaultCategory).toBe('DETAIL_PAGE');
    expect(parsed.audienceScope).toBe('ASSIGNED_ORG');
    expect(parsed.maxDataLevel).toBe('STRICT');
  });

  it('清单坏掉时不抛异常：交回空值，让服务端校验去说明问题', () => {
    expect(parseManifest('{不是 JSON').ok).toBe(false);
    expect(parseManifest('{不是 JSON').categories).toEqual([]);
    expect(parseManifest('[1,2]').ok).toBe(false);
    expect(parseManifest(undefined).ok).toBe(false);
    // 结构合法但没有 policy/presentation：照旧 ok，缺的就是 undefined（不编造默认值）
    const partial = parseManifest('{"categories":[]}');
    expect(partial.ok).toBe(true);
    expect(partial.audienceScope).toBeUndefined();
    expect(partial.defaultDataLevel).toBeUndefined();
  });
});
