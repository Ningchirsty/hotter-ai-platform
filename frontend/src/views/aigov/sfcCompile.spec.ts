import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc';
import { describe, expect, it } from 'vitest';
import { viteCheckTransitionPlugin } from '../../../vite/plugins/check-transition';

/**
 * 治理层页面（`views/aigov/**`）的 SFC 编译守卫。
 *
 * <p><b>为什么值得单独守一条</b>：本仓前端没有 DOM 测试环境，组件既不能渲染也不能挂载，
 * 于是"模板写错一个标签/绑定"这类错误在 CI 里<b>没有任何信号</b>——只有真正构建或人眼打开页面
 * 才会暴露。而本轮起这些页面是手改的（三栏布局、流程面板、分段预览），
 * 一个没闭合的标签就足以让整页白屏。</p>
 *
 * <p>这条守卫用 {@code @vue/compiler-sfc} 真编译每个 SFC 的模板与 {@code <script setup>}：
 * 能抓到语法与模板编译错误，抓不到"逻辑对不对"（那要靠纯函数单测与请求级测试）。</p>
 */
const ROOT = fileURLToPath(new URL('.', import.meta.url));

/** `src/views` 根：本仓所有"页面"都在它下面（构建期单根节点规则的作用范围）。 */
const VIEWS_ROOT = fileURLToPath(new URL('..', import.meta.url));

function walkVue(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...walkVue(full));
    } else if (entry.endsWith('.vue')) {
      out.push(full);
    }
  }
  return out;
}

const FILES = walkVue(ROOT).toSorted();
const VIEW_FILES = walkVue(VIEWS_ROOT).toSorted();

describe('aigov views SFC compile guard', () => {
  it('扫描本身不能空转（否则这条守卫会静默通过）', () => {
    expect(FILES.length).toBeGreaterThan(10);
    expect(FILES.some((file) => file.includes('studio'))).toBe(true);
  });

  it.each(FILES.map((file) => [file.slice(ROOT.length).replaceAll('\\', '/'), file]))(
    '%s 模板与 <script setup> 能编译',
    (_label, file) => {
      const source = readFileSync(file, 'utf8');
      const { descriptor, errors } = parse(source, { filename: file });
      expect(errors.map(String)).toEqual([]);
      if (descriptor.template) {
        const compiled = compileTemplate({
          id: 'guard',
          filename: file,
          source: descriptor.template.content
        });
        expect(compiled.errors.map(String)).toEqual([]);
      }
      if (descriptor.scriptSetup) {
        const script = compileScript(descriptor, { id: 'guard' });
        expect(script.content.length).toBeGreaterThan(0);
      }
    }
  );
});

describe('src/views 页面「transition 单根节点」守卫', () => {
  /**
   * 直接调用**构建插件自己的 transform**，不复制一份判定逻辑。
   *
   * <p>为什么要在单测里再跑一遍：这条规则原本只在 `vite build` 时才报错，而"跑生产构建"在 CI 里是
   * 前端镜像构建步骤、在本地通常不会跑（本轮就吃过：`ProposalPanel.vue` 的 `el-card` + `el-dialog`
   * 双根节点，单测 + lint + 全部守卫全绿，直到 CI 构建镜像才红）。把同一份判定挪到 `vitest` 里，
   * 这类问题在本地几十秒内就能看见，而且口径与构建完全一致（同一个插件对象）。</p>
   */
  const plugin = viteCheckTransitionPlugin() as unknown as {
    transform: ((code: string, id: string) => unknown) | { handler: (code: string, id: string) => unknown };
  };
  const transformFn =
    typeof plugin.transform === 'function' ? plugin.transform : plugin.transform.handler;

  it('全部页面都只允许一个根节点（违规一次性列全，避免逐个文件重跑）', () => {
    expect(VIEW_FILES.length).toBeGreaterThan(100);

    const violations: string[] = [];
    for (const file of VIEW_FILES) {
      const id = file.replaceAll('\\', '/');
      try {
        transformFn.call({}, readFileSync(file, 'utf8'), id);
      } catch (error: unknown) {
        violations.push(`${id.slice(id.indexOf('/src/') + 1)}: ${String(error)}`);
      }
    }
    expect(violations).toEqual([]);
  });
});
