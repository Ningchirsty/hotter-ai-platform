import { describe, expect, it } from 'vitest';
import { artifactSummary, fileNameOf, pendingPaths, shortSha, uploadedCount } from './sandboxArtifact';
import type { AigSandboxArtifactManifestVO } from '@/api/aigov/sandbox/types';

/**
 * 沙箱产物展示助手测试（ADR-016）。
 *
 * 这些错都不报错：摘要截取错只是不好读；而"还差哪个"算错会让人以为产物已收齐——
 * 那恰恰是这条通道唯一要回答的问题。
 */
describe('沙箱产物展示助手', () => {
  const item = (path: string, uploaded: boolean): AigSandboxArtifactManifestVO => ({ path, uploaded });

  it('下载文件名：只取最后一段；空则兜底', () => {
    expect(fileNameOf('out/a.txt')).toBe('a.txt');
    expect(fileNameOf('a.txt')).toBe('a.txt');
    expect(fileNameOf('out/文件.txt')).toBe('文件.txt');
    expect(fileNameOf('out/')).toBe('artifact');
    expect(fileNameOf('')).toBe('artifact');
    expect(fileNameOf(null)).toBe('artifact');
    expect(fileNameOf(undefined)).toBe('artifact');
  });

  it('摘要展示：只显示前 12 位；缺失显示占位', () => {
    expect(shortSha('abcdef0123456789')).toBe('abcdef012345…');
    expect(shortSha('abc')).toBe('abc');
    expect(shortSha('')).toBe('—');
    expect(shortSha(null)).toBe('—');
    expect(shortSha(undefined)).toBe('—');
  });

  it('★ 结论要点名「还差哪个」——只说 2/3 没人知道该补哪一个', () => {
    expect(artifactSummary([item('out/a.txt', true), item('out/b.bin', false)])).toBe(
      '声明 2 个，已入库 1 个；还差：out/b.bin'
    );
    expect(artifactSummary([item('a', false), item('b', false)])).toBe('声明 2 个，已入库 0 个；还差：a、b');
  });

  it('收齐了就说清楚收齐了；没声明产物也要说清是「没声明」而不是「空列表」', () => {
    expect(artifactSummary([item('a', true), item('b', true)])).toBe('声明 2 个，已全部入库');
    expect(artifactSummary([])).toBe('这次作业的 result.json 没有声明产物');
    expect(artifactSummary(null)).toBe('这次作业的 result.json 没有声明产物');
    expect(artifactSummary(undefined)).toBe('这次作业的 result.json 没有声明产物');
  });

  it('已入库计数与待补清单：非布尔 true 一律算未入库（后端可能不给这个字段）', () => {
    expect(uploadedCount([item('a', true), item('b', false)])).toBe(1);
    expect(uploadedCount(undefined)).toBe(0);
    // @ts-expect-error 故意给缺 uploaded 的项：按未入库处理（宁可让人多看一眼）
    expect(uploadedCount([{ path: 'a' }])).toBe(0);
    expect(pendingPaths([item('a', true), item('b', false)])).toEqual(['b']);
    expect(pendingPaths(undefined)).toEqual([]);
  });
});
