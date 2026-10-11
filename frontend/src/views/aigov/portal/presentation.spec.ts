import { describe, expect, it } from 'vitest';
import {
  START_PENDING_HINT,
  TASK_TYPE_OPTIONS,
  assetDomainLabel,
  buildIdempotencyKey,
  formatBytes,
  groupPortalActions,
  hasProblem,
  isFavorite,
  isNavigable,
  launchModeLabel,
  problemTexts,
  sortRolesByFavorite,
  suggestionKey,
  taskStatusMeta
} from './presentation';

/**
 * 员工 AI 工作台展示层测试（增量 2/3b/5）。
 *
 * 断言的都是"员工看到什么、点了会怎样"：空分类要显示出来、未知状态不冒充成功、
 * 收藏只影响排序、（路径解析本身在 `professionalLink.spec.ts` 里钉——只留一处实现）。
 */
describe('员工 AI 工作台展示层', () => {
  it('导航类卡片才走"直接打开"，其余走启动表单', () => {
    expect(isNavigable({ actionCode: 'A', targetType: 'NAVIGATION', targetRef: 'VIDEO_STUDIO' })).toBe(true);
    expect(isNavigable({ actionCode: 'B', launchMode: 'NAVIGATION', targetType: 'QUICK_CAPABILITY' })).toBe(true);
    expect(isNavigable({ actionCode: 'C', launchMode: 'STUDIO', targetType: 'QUICK_CAPABILITY' })).toBe(false);
    expect(isNavigable({ actionCode: 'D', launchMode: 'QUICK', targetType: 'QUICK_CAPABILITY' })).toBe(false);
    expect(isNavigable(undefined)).toBe(false);
  });

  it('启动文案如实说明"先预检、不通不发票"', () => {
    expect(START_PENDING_HINT).toContain('预检');
  });

  it('幂等键：同一轮表单里只生成一次，且不重复', () => {
    const a = buildIdempotencyKey();
    const b = buildIdempotencyKey();
    expect(a.length).toBeGreaterThan(8);
    expect(a).not.toEqual(b);
  });

  it('按错误码分支，不靠匹配文案（后端改措辞不该让界面逻辑失效）', () => {
    const problems = [{ code: 'REQUIRED_INPUT_MISSING', message: '还有必填内容没有填写' }];
    expect(hasProblem(problems, 'REQUIRED_INPUT_MISSING')).toBe(true);
    expect(hasProblem(problems, 'LAUNCH_TICKET_EXPIRED')).toBe(false);
    expect(hasProblem(undefined, 'ANY')).toBe(false);
  });

  it('问题文案优先用后端给的，缺文案时退回码', () => {
    expect(
      problemTexts([
        { code: 'A', message: '后端文案' },
        { code: 'B' }
      ])
    ).toEqual(['后端文案', 'B']);
    expect(problemTexts(undefined)).toEqual([]);
  });

  it('任务类型选项是封闭集合（权威校验仍在服务端）', () => {
    expect(TASK_TYPE_OPTIONS.map(item => item.code)).toContain('TEXT_GENERATION');
    expect(TASK_TYPE_OPTIONS.length).toBeGreaterThanOrEqual(9);
  });

  it('按清单声明的分类顺序归组，空分类也要出现', () => {
    const groups = groupPortalActions(
      [
        { code: 'BANNER', name: '横幅' },
        { code: 'EMPTY', name: '空栏' },
        { code: 'DETAIL_PAGE', name: '详情页' }
      ],
      [
        { actionCode: 'B', categoryCode: 'DETAIL_PAGE', sortOrder: 2 },
        { actionCode: 'A', categoryCode: 'DETAIL_PAGE', sortOrder: 1 },
        { actionCode: 'C', categoryCode: 'BANNER', sortOrder: 0 }
      ]
    );
    expect(groups.map(item => item.code)).toEqual(['BANNER', 'EMPTY', 'DETAIL_PAGE']);
    expect(groups[1].actions).toEqual([]);
    expect(groups[2].actions.map(item => item.actionCode)).toEqual(['A', 'B']);
  });

  it('挂在未声明分类上的卡片不能凭空消失', () => {
    const groups = groupPortalActions([{ code: 'X' }], [{ actionCode: 'ORPHAN', categoryCode: 'GONE' }]);
    expect(groups.map(item => item.code)).toEqual(['X', '（未在清单中声明的分类）']);
    expect(groups[1].actions[0].actionCode).toBe('ORPHAN');
  });

  it('任务状态：服务端给了中文就用它；未知状态不冒充成功', () => {
    expect(taskStatusMeta('SUCCEEDED', '成功')).toEqual({ label: '成功', tag: 'success' });
    expect(taskStatusMeta('FAILED', '失败').tag).toBe('danger');
    expect(taskStatusMeta('RUNNING', '进行中').tag).toBe('warning');
    expect(taskStatusMeta('SOMETHING_NEW')).toEqual({ label: 'SOMETHING_NEW', tag: 'info' });
    expect(taskStatusMeta(undefined)).toEqual({ label: '未知', tag: 'info' });
  });

  it('启动方式显示中文，未知原样显示', () => {
    expect(launchModeLabel('STUDIO')).toBe('专业台');
    expect(launchModeLabel('WEIRD')).toBe('WEIRD');
    expect(launchModeLabel(undefined)).toBe('未指定');
  });

  it('收藏判定与排序：收藏在前，其余保持服务端顺序', () => {
    expect(isFavorite(['A'], 'A')).toBe(true);
    expect(isFavorite(['A'], 'B')).toBe(false);
    expect(isFavorite(undefined, 'A')).toBe(false);
    expect(isFavorite(['A'], undefined)).toBe(false);

    const roles = [{ roleCode: 'A' }, { roleCode: 'B' }, { roleCode: 'C' }];
    expect(sortRolesByFavorite(roles, ['C']).map(r => r.roleCode)).toEqual(['C', 'A', 'B']);
    // 收藏里含"已不可见"的岗位时，只把可见的排前面，不凭空补位
    expect(sortRolesByFavorite(roles, ['GONE', 'B']).map(r => r.roleCode)).toEqual(['B', 'A', 'C']);
    expect(sortRolesByFavorite(undefined, ['A'])).toEqual([]);
    // 不修改入参（调用方可能还在用原顺序）
    const original = [...roles];
    sortRolesByFavorite(roles, ['C']);
    expect(roles).toEqual(original);
  });

  it('产物大小：缺值与 0 分得开，异常值显示原样', () => {
    expect(formatBytes(undefined)).toBe('—');
    expect(formatBytes(null)).toBe('—');
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(1023)).toBe('1023 B');
    expect(formatBytes(1024)).toBe('1.0 KB');
    expect(formatBytes(1024 * 1024)).toBe('1.0 MB');
    expect(formatBytes(15 * 1024 * 1024)).toBe('15.0 MB');
    // 异常值不硬编成某个大小，显示原值便于发现上游给了什么
    expect(formatBytes(-1)).toBe('-1');
    expect(formatBytes(Number.NaN)).toBe('NaN');
  });

  it('推荐结果的列表键带上岗位：两个岗位的同名卡片不能互相复用', () => {
    expect(suggestionKey({ roleCode: 'R1', action: { actionCode: 'A' } })).toBe('R1#A');
    expect(suggestionKey({ roleCode: 'R2', action: { actionCode: 'A' } })).toBe('R2#A');
    // 同码不同岗位必须产生不同的键（否则列表里会少一条、或点哪条都开同一个岗位）
    expect(suggestionKey({ roleCode: 'R1', action: { actionCode: 'A' } })).not.toEqual(
      suggestionKey({ roleCode: 'R2', action: { actionCode: 'A' } })
    );
    // 缺字段不抛错（界面容错，但不会因此把两条不同推荐合成一条）
    expect(suggestionKey({})).toBe('#');
  });

  it('资产域文案：已知域给中文，未知域原样显示（不并到「其他」里藏起来）', () => {
    expect(assetDomainLabel('IMAGE')).toBe('图片素材');
    expect(assetDomainLabel('VIDEO')).toBe('视频素材');
    expect(assetDomainLabel('CONTENT')).toBe('内容附件');
    expect(assetDomainLabel('NEW_DOMAIN')).toBe('NEW_DOMAIN');
    expect(assetDomainLabel(undefined)).toBe('资产');
  });
});
