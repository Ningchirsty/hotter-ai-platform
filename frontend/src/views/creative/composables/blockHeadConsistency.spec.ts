import { readdirSync, readFileSync, statSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

/**
 * 「区块头只有一套写法」的静态守卫（第 35 轮）。
 *
 * <p><b>为什么要有它</b>：v1 反馈 ① 说的是「排版没有设计和逻辑、整体看起来比较混乱」。
 * 第 33 轮把 `.panel` / `.block-head` 那些**逐字相同的副本**收成了一处，但第 35 轮量的时候
 * 又发现两种"同类不同名/不同参数"的漏网写法：</p>
 * <ul>
 *   <li>生产页的区块头叫 `.sub-head`（间距 10px，标准是 12px）——**同类不同名**，
 *       所以第 33 轮按 `.block-head` 搜的时候扫不到它；</li>
 *   <li>分镜页「生成方向」那颗按钮没写 `size="small"`——**同一行里比旁边两颗大一号**
 *       （真机量出来的：同一 `.head-actions` 里 `small=false` 只有这一颗）。</li>
 * </ul>
 *
 * <p>这两种都不会报错、类型检查也抓不到，只能像这样静态钉住。</p>
 */

const CREATIVE = new URL('../', import.meta.url);

/** 递归列出 views/creative 下的所有 .vue（用于"整片都不许出现"的断言） */
function allVueFiles(dir = CREATIVE, acc: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const url = new URL(entry + (statSync(new URL(entry, dir)).isDirectory() ? '/' : ''), dir);
    if (statSync(url).isDirectory()) {
      allVueFiles(url, acc);
    } else if (entry.endsWith('.vue')) {
      acc.push(readFileSync(url, 'utf-8'));
    }
  }
  return acc;
}

/**
 * 取出一个 `<div class="block-head">…</div>` 区块（按 div 配对，不是遇 `</div>` 就断）。
 *
 * <p>注意只扫**区块头**：工作台里还有一层**页面头**（`.page-head`）——那一层的按钮
 * 是页面级动作，尺寸本来就更醒目（默认尺寸），两类不能混为一谈。</p>
 *
 * @param text 组件源码
 * @returns 每个 block-head 区块的源码
 */
function tagBlocks(text: string, marker: string): string[] {
  const blocks: string[] = [];
  let from = 0;
  for (;;) {
    const at = text.indexOf(marker, from);
    if (at < 0) {
      break;
    }
    const open = text.lastIndexOf('<div', at);
    let depth = 0;
    let i = open;
    while (i < text.length) {
      if (text.startsWith('<div', i)) {
        depth += 1;
        i += 4;
        continue;
      }
      if (text.startsWith('</div>', i)) {
        depth -= 1;
        i += 6;
        if (depth === 0) {
          break;
        }
        continue;
      }
      i += 1;
    }
    blocks.push(text.slice(open, i));
    from = i;
  }
  return blocks;
}

describe('区块头（.block-head + .head-actions）只有一套写法（第 35 轮）', () => {
  const files = allVueFiles();

  it('全站不再有 .sub-head 这类"同类不同名"的区块头副本', () => {
    for (const text of files) {
      expect(text).not.toContain('class="sub-head"');
      expect(text).not.toMatch(/^\s*\.sub-head\s*\{/m);
    }
  });

  it('区块头里的按钮一律 size="small"（同一行不能混两种尺寸；页面头不在此列）', () => {
    const offenders: string[] = [];
    for (const text of files) {
      for (const block of tagBlocks(text, 'class="block-head"')) {
        const actions = tagBlocks(block, 'class="head-actions"').join('\n');
        for (const button of actions.match(/<el-button[^>]*>/g) || []) {
          if (!button.includes('size="small"')) {
            offenders.push(button.replace(/\s+/g, ' ').slice(0, 90));
          }
        }
      }
    }
    expect(offenders, `这些区块头按钮没写 size="small"：\n${offenders.join('\n')}`).toEqual([]);
  });
});
