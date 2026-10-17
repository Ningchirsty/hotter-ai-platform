import { describe, expect, it } from 'vitest';
import { collapsedPreview, looksLikeJson, prettyOrRaw } from './raw';

/**
 * 原始字段展示口径（走读 §5.5）。
 *
 * <p>重点钉一件：**非法 JSON 也要能看到内容**——那种值恰恰最需要查，
 * 不能因为"格式化失败"就显示一句错误、把原文丢掉。</p>
 */
describe('aig raw text', () => {
  it('识别 JSON 对象/数组；普通文本与非法 JSON 都不算', () => {
    expect(looksLikeJson('{"a":1}')).toBe(true);
    expect(looksLikeJson(' [1,2] ')).toBe(true);
    expect(looksLikeJson('{not json')).toBe(false);
    expect(looksLikeJson('hello')).toBe(false);
    expect(looksLikeJson('')).toBe(false);
    expect(looksLikeJson(undefined)).toBe(false);
  });

  it('★ 展开：JSON 缩进美化；非法 JSON / 普通文本原样交出（不丢内容）', () => {
    expect(prettyOrRaw('{"a":1}')).toContain('\n  "a": 1');
    expect(prettyOrRaw('{not json')).toBe('{not json');
    expect(prettyOrRaw('一个人读的说明')).toBe('一个人读的说明');
    expect(prettyOrRaw('')).toBe('（空）');
    expect(prettyOrRaw(undefined)).toBe('（空）');
  });

  it('★ 收起：单行、限长、带总长度（列表不能被一段 JSON 撑开）', () => {
    const long = 'x'.repeat(100);
    const preview = collapsedPreview(long, 20);
    expect(preview.startsWith('x'.repeat(20))).toBe(true);
    expect(preview).toContain('共 100 字符');
    expect(collapsedPreview('{"a":\n 1}')).toBe('{"a": 1}');
    expect(collapsedPreview('   ')).toBe('（空）');
    expect(collapsedPreview(undefined)).toBe('（空）');
  });
});
