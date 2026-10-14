import { readFileSync } from 'node:fs';
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc';
import { describe, expect, it } from 'vitest';

/**
 * 训练台三栏结构的源码守卫（设计 §6.3、§5.4 图一）。
 *
 * <p><b>为什么是"读源码"而不是"渲染断言"</b>：本仓前端没有 DOM 测试环境
 * （无 jsdom / happy-dom / @vue/test-utils），组件渲染无法在 CI 里验证。
 * 但"三栏是不是还在"这件事有实打实的回归风险——它是一个 800 多行的单文件组件，
 * 后人删掉左栏或把步骤导航挪回顶部不会有任何信号。所以这里退一步：
 * 钉住**结构**（左栏在编辑区之前、三栏列宽合计 24、辅助区仍在下方），
 * 渲染与观感仍需要人看一眼（这一点写在文档里，不在这里假装已验证）。</p>
 */
const SOURCE = readFileSync(new URL('./index.vue', import.meta.url), 'utf8');

/** 三栏那一行的模板片段（从第一个 el-row 开始） */
const rowBlock = (() => {
  const start = SOURCE.indexOf('<el-row :gutter="16">');
  return start < 0 ? '' : SOURCE.slice(start, SOURCE.indexOf('</el-row>', start) + '</el-row>'.length);
})();

describe('studio three-column layout (source guard)', () => {
  it('★ 左栏是步骤导航，并且在编辑区之前（左→中）', () => {
    expect(SOURCE).toContain('class="step-card"');
    expect(SOURCE).toContain('class="step-list"');
    expect(SOURCE).toContain('step-item-active');
    // 步骤清单必须来自 layout.ts 的唯一口径，不得在页面里再抄一份
    expect(SOURCE).toContain('STUDIO_STEPS');
    expect(rowBlock.indexOf('step-card')).toBeGreaterThan(-1);
    expect(rowBlock.indexOf('step-card')).toBeLessThan(rowBlock.indexOf('配置与 Prompt'));
  });

  it('★ 三栏列宽合计 24（4 + 13 + 7），辅助区（修订差异）在编辑区之后', () => {
    expect(SOURCE).toContain('<el-col :span="4">');
    expect(SOURCE).toContain('<el-col :span="13">');
    expect(SOURCE).toContain('<el-col :span="7">');
    expect(rowBlock.indexOf('修订历史与差异')).toBeGreaterThan(rowBlock.indexOf('配置与 Prompt'));
  });

  it('★ 不再有顶部横向 step（改版后残留会让步骤出现两次）', () => {
    expect(SOURCE).not.toContain('<el-steps');
    expect(SOURCE).not.toContain(':active="1"');
  });

  it('★ 变更提案仍在三栏下方（刻意的偏离：塞进 7/24 右栏会挤到没法审阅）', () => {
    expect(SOURCE).toContain('<ProposalPanel');
    const proposal = SOURCE.indexOf('<ProposalPanel');
    const rowEnd = SOURCE.indexOf('</el-row>');
    expect(proposal).toBeGreaterThan(rowEnd);
  });

  it('步骤高亮是"定位"而不是过滤：不按 activeStep 隐藏任何表单区（否则会像内容丢了）', () => {
    // 只允许在 class 绑定里用 activeStep；不得出现 v-if/v-show 与 activeStep 的组合
    const withVIf = SOURCE.match(/v-(if|show)="[^"]*activeStep[^"]*"/g) ?? [];
    expect(withVIf).toEqual([]);
  });

  it('★ SFC 仍能编译（无 DOM 环境下能拿到的最低保证：模板语法/绑定写错会在这里红）', () => {
    const { descriptor, errors } = parse(SOURCE, { filename: 'studio/index.vue' });
    expect(errors.map(String)).toEqual([]);
    expect(descriptor.template).not.toBeNull();

    const compiled = compileTemplate({
      id: 'studio-layout-guard',
      filename: 'studio/index.vue',
      source: descriptor.template!.content
    });
    expect(compiled.errors.map(String)).toEqual([]);
    expect(compiled.code).toContain('step-card');

    const script = compileScript(descriptor, { id: 'studio-layout-guard' });
    expect(script.content.length).toBeGreaterThan(0);
  });
});
