import { readFileSync } from 'node:fs';
import { describe, expect, it, vi } from 'vitest';

/**
 * 「点了没反应」这一类问题的守卫（第 34 轮）。
 *
 * <p><b>为什么值得单测</b>：v1 人工测试反馈里这是一整类问题——按钮在、状态不对、
 * 点下去什么都不发生。它不会报错、类型检查也抓不到，只有"真的点一下"才会发现。
 * 第 23 轮手工清过内容任务页；第 34 轮做了一次**可重复的静态审计**
 * （`_local/audit-r34-silent-clicks.py`）又找出 7 处「刷新/加载」按钮：
 * 没选项目时直接 `return`，没有任何回话。</p>
 *
 * <p>这里钉两件事：① 回话的口径只有一处（`NO_PROJECT_HINT`）；
 * ② 那 7 处调用点确实"有回话再返回"，而不是又变回静默 `return`。</p>
 */

const mockInfo = vi.fn();
vi.mock('element-plus', () => ({
  ElMessage: { info: (...args: unknown[]) => mockInfo(...args) }
}));

const { NO_PROJECT_HINT, notifyNoProject } = await import('./noProject');

/** 读一个组件/页面的源码（相对 views/creative） */
function source(relative: string): string {
  return readFileSync(new URL('../' + relative, import.meta.url), 'utf-8');
}

describe('没选项目时的回话（第 34 轮）', () => {
  it('口径只有一处，而且说清了"现在做不了 + 去哪儿做"', () => {
    expect(NO_PROJECT_HINT).toContain('还没有选项目');
    expect(NO_PROJECT_HINT).toContain('「视觉项目」');
    notifyNoProject();
    expect(mockInfo).toHaveBeenCalledWith(NO_PROJECT_HINT);
  });

  it('7 处曾经静默返回的地方，现在都是"先回话再返回"', () => {
    const sites: Array<[string, string]> = [
      ['storyboard/index.vue', 'async function loadAll()'],
      ['review/index.vue', 'async function loadAll()'],
      ['moduleplan/index.vue', 'async function load()'],
      ['components/CreativeQaPanel.vue', 'async function load()'],
      ['components/CreativeInspectorPanel.vue', 'async function load()'],
      ['components/CreativeAssetDrawer.vue', 'async function load()'],
      ['project/components/ProjectWorkPackageBlock.vue', 'async function load()']
    ];
    for (const [file, fn] of sites) {
      const text = source(file);
      expect(text, `${file} 没有引入统一的回话`).toContain("import { notifyNoProject }");
      const start = text.indexOf(fn);
      expect(start, `${file} 里找不到 ${fn}`).toBeGreaterThan(-1);
      const body = text.slice(start, start + 400);
      // 守卫分支里必须紧跟着一句回话（顺序也钉住：先回话、再 return）
      expect(body, `${file} 的 ${fn} 守卫里没有回话`).toMatch(/if \(![\w.]+\) \{\s*(\/\/[^\n]*\n\s*)?notifyNoProject\(\);/);
      expect(body.indexOf('notifyNoProject()')).toBeLessThan(body.indexOf('return;'));
    }
  });
});
