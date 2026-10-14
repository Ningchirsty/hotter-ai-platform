import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc';
import { describe, expect, it } from 'vitest';

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
