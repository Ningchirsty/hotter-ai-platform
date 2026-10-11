import type { AigSandboxArtifactManifestVO } from '@/api/aigov/sandbox/types';

/**
 * 沙箱产物展示助手（纯函数；ADR-016）。
 *
 * <p>为什么抽出来单测：这几句错了都不报错——摘要截取错了只是显示得看不清；
 * 而"还差哪个"算错会让人以为产物已经收齐，那正是这条通道唯一要回答的问题。</p>
 *
 * @author ai-gov
 */

/**
 * 由路径推下载文件名（只取最后一段；空则兜底）。
 *
 * <p>真正的响应头安全处理在服务端（那里才能防 header injection）；
 * 这里只负责给 `saveBlob` 一个像样的名字。</p>
 *
 * @param path 产物路径
 * @returns 文件名
 */
export function fileNameOf(path?: string | null): string {
  if (!path) return 'artifact';
  const name = path.split('/').pop() ?? '';
  return name.trim() === '' ? 'artifact' : name;
}

/**
 * 摘要展示（只显示前 12 位 + 省略号）：完整 64 位在表格里既挤又没人会逐位读。
 *
 * @param sha 摘要
 * @returns 展示文本
 */
export function shortSha(sha?: string | null): string {
  if (!sha) return '—';
  const text = sha.trim();
  if (text === '') return '—';
  return text.length <= 12 ? text : text.slice(0, 12) + '…';
}

/**
 * 已入库的产物数。
 *
 * @param items 清单
 * @returns 数量
 */
export function uploadedCount(items?: AigSandboxArtifactManifestVO[] | null): number {
  if (!Array.isArray(items)) return 0;
  return items.filter(item => item?.uploaded === true).length;
}

/**
 * 一句话结论：声明了几个、收了几个、还差哪个。
 *
 * <p><b>"还差哪个"必须点名</b>：只说"2/3"没人知道该去补哪一个；而漏传的产物
 * 在"只列已入库"的列表里根本不会出现，所以这份清单一向以<b>声明的产物</b>为准。</p>
 *
 * @param items 清单
 * @returns 摘要
 */
export function artifactSummary(items?: AigSandboxArtifactManifestVO[] | null): string {
  const list = Array.isArray(items) ? items : [];
  if (list.length === 0) {
    return '这次作业的 result.json 没有声明产物';
  }
  const uploaded = uploadedCount(list);
  if (uploaded === list.length) {
    return `声明 ${list.length} 个，已全部入库`;
  }
  const missing = list.filter(item => item?.uploaded !== true).map(item => item?.path ?? '（未知路径）');
  return `声明 ${list.length} 个，已入库 ${uploaded} 个；还差：${missing.join('、')}`;
}

/**
 * 未入库的产物路径清单（上传按钮的可用性判断用得上）。
 *
 * @param items 清单
 * @returns 路径清单
 */
export function pendingPaths(items?: AigSandboxArtifactManifestVO[] | null): string[] {
  if (!Array.isArray(items)) return [];
  return items.filter(item => item?.uploaded !== true).map(item => item?.path ?? '');
}
