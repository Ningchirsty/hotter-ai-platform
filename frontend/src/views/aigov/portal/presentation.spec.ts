import { describe, expect, it } from 'vitest';
import {
  START_PENDING_HINT,
  groupPortalActions,
  isNavigable,
  launchModeLabel,
  navigationPath,
  taskStatusMeta
} from './presentation';

/**
 * 员工 AI 工作台展示层测试（增量 2）。
 *
 * 断言的都是"员工看到什么、点了会怎样"：导航入口只认白名单（不许猜路径）、
 * 空分类要显示出来、未知状态不冒充成功。
 */
describe('员工 AI 工作台展示层', () => {
  it('导航类卡片才可直接打开，且只认白名单 routeKey', () => {
    const nav = { actionCode: 'A', targetType: 'NAVIGATION', targetRef: 'VIDEO_STUDIO', launchMode: 'NAVIGATION' };
    expect(isNavigable(nav)).toBe(true);
    // 白名单里有这个键：给出真实路径
    const path = navigationPath(nav);
    expect(path).not.toBeNull();
    expect(path?.startsWith('/')).toBe(true);

    // 白名单里没有的键：解析不到就返回 null（不许猜路径）
    expect(navigationPath({ actionCode: 'B', targetType: 'NAVIGATION', targetRef: 'NOT_A_REAL_KEY' })).toBeNull();
    // 非导航卡片：即使 targetRef 恰好是个 routeKey 也不给路径（那是 NAVIGATION 的语义）
    expect(navigationPath({ actionCode: 'C', targetType: 'QUICK_CAPABILITY', targetRef: 'VIDEO_STUDIO' })).toBeNull();
    expect(navigationPath(undefined)).toBeNull();
  });

  it('非导航卡片的文案说明"现在还不能启动"，而不是给一个没反应的按钮', () => {
    expect(START_PENDING_HINT).toContain('只读');
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
});
