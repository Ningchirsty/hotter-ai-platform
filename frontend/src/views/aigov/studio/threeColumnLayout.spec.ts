import { readFileSync } from 'node:fs';
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc';
import { describe, expect, it } from 'vitest';

/**
 * 训练台三栏结构与组件拆分的源码守卫（设计 §6.3、§5.4 图一）。
 *
 * <p><b>为什么是"读源码"而不是"渲染断言"</b>：本仓前端没有 DOM 测试环境
 * （无 jsdom / happy-dom / @vue/test-utils），组件渲染无法在 CI 里验证。
 * 但"三栏是不是还在"这件事有实打实的回归风险——它曾经是一个 800 多行的单文件组件，
 * 后人删掉左栏、把步骤导航挪回顶部、或把左右两栏又塞回主文件，不会有任何信号。
 * 所以这里钉住**结构**（三栏列宽合计 24、左右两栏由子组件承载、辅助区仍在下方），
 * 渲染与观感仍需要人看一眼（这一点写在文档里，不在这里假装已验证）。</p>
 *
 * <p>本轮把左右两栏拆成子组件后：主文件的职责变成"三栏怎么摆"，
 * 各栏自己的结构由各自的文件负责——所以断言也随之分成三份。</p>
 */
const INDEX = readFileSync(new URL('./index.vue', import.meta.url), 'utf8');
const STEPS = readFileSync(new URL('./components/StudioStepsColumn.vue', import.meta.url), 'utf8');
const REVISIONS = readFileSync(
  new URL('./components/StudioRevisionColumn.vue', import.meta.url),
  'utf8'
);

/** 三栏那一行的模板片段（从第一个 el-row 开始） */
const rowBlock = (() => {
  const start = INDEX.indexOf('<el-row :gutter="16">');
  return start < 0 ? '' : INDEX.slice(start, INDEX.indexOf('</el-row>', start) + '</el-row>'.length);
})();

describe('studio three-column layout (source guard)', () => {
  it('★ 主文件只负责"三栏怎么摆"：左=子组件步骤栏、中=编辑区、右=子组件差异栏', () => {
    expect(rowBlock).toContain('<StudioStepsColumn');
    expect(rowBlock).toContain('配置与 Prompt');
    expect(rowBlock).toContain('<StudioRevisionColumn');
    // 左栏在编辑区之前、右栏在编辑区之后
    expect(rowBlock.indexOf('StudioStepsColumn')).toBeLessThan(rowBlock.indexOf('配置与 Prompt'));
    expect(rowBlock.indexOf('StudioRevisionColumn')).toBeGreaterThan(
      rowBlock.indexOf('配置与 Prompt')
    );
  });

  it('★ 三栏列宽合计 24（4 + 13 + 7）', () => {
    expect(INDEX).toContain('<el-col :span="4">');
    expect(INDEX).toContain('<el-col :span="13">');
    expect(INDEX).toContain('<el-col :span="7">');
  });

  it('★ 左栏：步骤清单来自 layout.ts 的唯一口径，且高亮不做过滤', () => {
    expect(STEPS).toContain('class="step-card"');
    expect(STEPS).toContain('class="step-list"');
    expect(STEPS).toContain('step-item-active');
    expect(STEPS).toContain('STUDIO_STEPS');

    // 只允许在 class 绑定里用 activeStep；不得出现 v-if/v-show 与它的组合
    // （过滤会让"别的步骤的内容"看起来丢了）
    const withVIf = STEPS.match(/v-(if|show)="[^"]*activeStep[^"]*"/g) ?? [];
    expect(withVIf).toEqual([]);
  });

  it('★ 右栏：修订历史与差异在子组件里，动作通过事件回抛（不复制状态）', () => {
    expect(REVISIONS).toContain('修订历史与差异');
    expect(REVISIONS).toContain('回滚到所选修订');
    expect(REVISIONS).toContain('差异（当前 ↔ 所选修订）');
    expect(REVISIONS).toContain("emit('select'");
    expect(REVISIONS).toContain("emit('rollback')");
    expect(REVISIONS).toContain("emit('clear')");
  });

  it('★ 不再有顶部横向 step（改版后残留会让步骤出现两次）', () => {
    expect(INDEX).not.toContain('<el-steps');
    expect(INDEX).not.toContain(':active="1"');
  });

  it('★ 变更提案仍在三栏下方（刻意的偏离：塞进 7/24 右栏会挤到没法审阅）', () => {
    expect(INDEX).toContain('<ProposalPanel');
    const proposal = INDEX.indexOf('<ProposalPanel');
    expect(proposal).toBeGreaterThan(INDEX.indexOf('</el-row>'));
  });

  it('★ 主文件 SFC 仍能编译（无 DOM 环境下能拿到的最低保证：模板/绑定写错会在这里红）', () => {
    const { descriptor, errors } = parse(INDEX, { filename: 'studio/index.vue' });
    expect(errors.map(String)).toEqual([]);
    expect(descriptor.template).not.toBeNull();

    const compiled = compileTemplate({
      id: 'studio-layout-guard',
      filename: 'studio/index.vue',
      source: descriptor.template!.content
    });
    expect(compiled.errors.map(String)).toEqual([]);
    expect(compiled.code).toContain('StudioRevisionColumn');

    const script = compileScript(descriptor, { id: 'studio-layout-guard' });
    expect(script.content.length).toBeGreaterThan(0);
  });
});
