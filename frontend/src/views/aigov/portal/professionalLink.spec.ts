import { describe, expect, it } from 'vitest';
import {
  FROM_WORKSPACE_FLAG,
  WORKSPACE_PATH,
  buildProfessionalUrl,
  buildWorkspaceBackUrl,
  readLaunchContext,
  resolveProfessionalPath
} from './professionalLink';

/**
 * 专业台跳转/回跳链接测试（增量 4）。
 *
 * 这里钉住的是那个**安静的错误**：创作域页面读的是 `location.search` 里的 taskId，
 * 一旦跳转时把 taskId 放在别处（或干脆不带），页面不会报错，只是"没带上这次启动"。
 */
describe('岗位工作台 ↔ 专业台 链接', () => {
  it('跳转带 taskId 到 URL query（专业页读的是 location.search）', () => {
    const url = buildProfessionalUrl('/creative/project', {
      taskId: 123,
      roleCode: 'GRAPHIC_DESIGNER_AI',
      actionCode: 'A1'
    });
    const query = new URLSearchParams(url.split('?')[1]);
    expect(url.startsWith('/creative/project?')).toBe(true);
    expect(query.get('taskId')).toBe('123');
    expect(query.get('roleCode')).toBe('GRAPHIC_DESIGNER_AI');
    expect(query.get('actionCode')).toBe('A1');
    expect(query.get('from')).toBe(FROM_WORKSPACE_FLAG);
  });

  it('没有 taskId 时也标注来源（便于专业页显示返回入口）', () => {
    const url = buildProfessionalUrl('/video', {});
    expect(url).toBe('/video?from=ai-workspace');
    expect(new URLSearchParams(url.split('?')[1]).has('taskId')).toBe(false);
  });

  it('空值不写进 query（避免 taskId= 这种"看着有其实没有"的参数）', () => {
    const url = buildProfessionalUrl('/video', { taskId: '', roleCode: '', actionCode: undefined });
    const query = new URLSearchParams(url.split('?')[1]);
    expect(query.has('taskId')).toBe(false);
    expect(query.has('roleCode')).toBe(false);
    expect(query.has('actionCode')).toBe(false);
  });

  it('读回上下文：能拿到 taskId 并识别是否来自工作台', () => {
    expect(readLaunchContext('?taskId=9&roleCode=R&actionCode=A&from=ai-workspace')).toEqual({
      taskId: '9',
      roleCode: 'R',
      actionCode: 'A',
      fromWorkspace: true
    });
    expect(readLaunchContext('').taskId).toBe('');
    expect(readLaunchContext(undefined).fromWorkspace).toBe(false);
    // 只带 taskId（手工打开的老链接）也要能用
    expect(readLaunchContext('?taskId=9').taskId).toBe('9');
  });

  it('回跳地址带上岗位与卡片，便于工作台恢复上下文', () => {
    expect(buildWorkspaceBackUrl({ roleCode: 'R', actionCode: 'A' })).toBe('/ai-workspace?role=R&action=A');
    expect(buildWorkspaceBackUrl({})).toBe(WORKSPACE_PATH);
    expect(buildWorkspaceBackUrl({ roleCode: 'R' })).toBe('/ai-workspace?role=R');
  });

  it('跳转与回跳是一对：能原样往返上下文', () => {
    const url = buildProfessionalUrl('/creative/review', { taskId: '7', roleCode: 'R', actionCode: 'A' });
    const context = readLaunchContext(url.substring(url.indexOf('?')));
    expect(context.taskId).toBe('7');
    expect(buildWorkspaceBackUrl(context)).toBe('/ai-workspace?role=R&action=A');
  });

  it('专业页路径只认白名单：NAVIGATION 看 targetRef，STUDIO 看 studioRouteKey', () => {
    // 导航卡片：目标引用是 routeKey
    expect(resolveProfessionalPath({ actionCode: 'A', launchMode: 'NAVIGATION', targetType: 'NAVIGATION', targetRef: 'VIDEO_STUDIO' })).toBeTruthy();
    // 专业台卡片：看 studioRouteKey
    expect(
      resolveProfessionalPath({
        actionCode: 'B',
        launchMode: 'STUDIO',
        targetType: 'QUICK_CAPABILITY',
        targetRef: 'cap/x',
        studioRouteKey: 'CREATIVE_PRODUCTION'
      })
    ).toBeTruthy();
    // 不在白名单 / 非专业页卡片：null（宁可不给入口，也不猜路径）
    expect(resolveProfessionalPath({ actionCode: 'C', launchMode: 'STUDIO', targetType: 'QUICK_CAPABILITY', studioRouteKey: 'NOT_A_KEY' })).toBeNull();
    expect(resolveProfessionalPath({ actionCode: 'D', launchMode: 'QUICK', targetType: 'QUICK_CAPABILITY', targetRef: 'cap/x' })).toBeNull();
    expect(resolveProfessionalPath(undefined)).toBeNull();
  });
});
