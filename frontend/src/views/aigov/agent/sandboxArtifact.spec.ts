import { describe, expect, it } from 'vitest';
import {
  artifactSummary,
  downloadSummary,
  downloaderLabel,
  fileNameOf,
  pendingPaths,
  shortSha,
  uploadedCount
} from './sandboxArtifact';
import type {
  AigSandboxArtifactDownloadVO,
  AigSandboxArtifactManifestVO
} from '@/api/aigov/sandbox/types';

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

  describe('下载留痕（ADR-016 ⑤）', () => {
    const row = (
      id: number,
      downloadedBy: number | null,
      clientIp: string | null,
      createTime?: string
    ): AigSandboxArtifactDownloadVO => ({
      downloadId: id,
      path: 'out/a.txt',
      downloadedBy,
      clientIp,
      createTime
    });

    it('★ 摘要要说清「共几次 + 最近一次是谁、从哪来」——安全事件的第一个问题', () => {
      const rows = [row(3, 200, '10.12.34.56', '2026-10-11 16:00:00'), row(2, 201, '10.0.0.1')];
      expect(downloadSummary(rows)).toBe('共 2 次下载；最近一次：用户 200 / 10.12.34.56（2026-10-11 16:00:00）');
    });

    it('没有记录时说清「还没有」，不是空字符串（空字符串会被读成加载失败）', () => {
      expect(downloadSummary([])).toBe('还没有下载记录');
      expect(downloadSummary(null)).toBe('还没有下载记录');
      expect(downloadSummary(undefined)).toBe('还没有下载记录');
    });

    it('缺失字段不炸：没有用户/没有 IP/没有时间都照常给出一句话', () => {
      expect(downloadSummary([row(1, null, null)])).toBe('共 1 次下载；最近一次：（未记录用户）');
      expect(downloaderLabel(row(1, null, null))).toBe('（未记录用户）');
      expect(downloaderLabel(row(1, 200, 'x'))).toBe('200');
      expect(downloaderLabel(undefined)).toBe('（未记录用户）');
    });
  });
});
