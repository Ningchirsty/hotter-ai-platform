/**
 * 「测试预览」的展示口径（纯函数，可单测）。
 *
 * <p>设计 §6.6：单次测试结果包含**输出、引用、Schema 结论、运行时间、错误和版本哈希**。
 * 其中"版本哈希"与"结果摘要"服务端一直在返回（{@code contentHash} / {@code resultDigest}），
 * 但界面从没显示过——于是"这一测到底测的是哪一版内容"在界面上无从核对。
 * 这里把它们与 Schema 结论一起做成可单测的口径。</p>
 *
 * @author ai-gov
 */

import type { AigStudioTestRunVO } from '@/api/aigov/studio/types';

/** 测试状态文案（认不出的码原样展示，不冒充成功） */
export function testStatusText(status?: string): string {
  switch (status) {
    case 'SUCCEEDED':
      return '调用成功（不等于业务通过）';
    case 'FAILED':
      return '调用失败';
    default:
      return status || '未知';
  }
}

/**
 * 哈希短显：`sha256:abcd1234…`（保留前 8 位）。
 *
 * <p>完整哈希在界面上没人读得动，但"两次测试是不是同一版内容"要靠它比对——留前 8 位够辨认，
 * 需要逐字核对时去审计/调用记录里取原文。</p>
 */
export function hashShort(hash?: string): string {
  const text = hash?.trim();
  if (!text) {
    return '—';
  }
  return text.length <= 12 ? text : `${text.slice(0, 8)}…${text.slice(-4)}`;
}

/**
 * Schema 结论（设计 §6.6）。
 *
 * <p>判据来自调用层：输出不符合能力输出模板时，网关会把错误分类落成
 * {@code OUTPUT_UNPARSABLE}（错误摘要"输出不符合Schema"）。因此：
 * 成功 = 已过 Schema 校验；{@code OUTPUT_UNPARSABLE} = 没过；
 * 其它失败 = **未判定**（没走到校验那一步），不能反过来说它"通过"。</p>
 */
export function schemaVerdictText(test?: { testStatus?: string; errorCode?: string }): string {
  if (!test) {
    return '未判定';
  }
  if (test.errorCode === 'OUTPUT_UNPARSABLE') {
    return '不通过（输出不符合 Schema）';
  }
  if (test.testStatus === 'SUCCEEDED') {
    return '通过（输出符合 Schema）';
  }
  return '未判定（调用未成功）';
}

/** 一次测试的一句话摘要；没有测试结果时给出"怎么跑一次"的提示 */
export function testPreviewSummary(test?: AigStudioTestRunVO): string {
  if (!test) {
    return '还没跑过测试：点底部「测试（会花一次调用）」跑一次，结果会留在这里。';
  }
  const parts = [testStatusText(test.testStatus)];
  if (test.revision !== undefined && test.revision !== null) {
    parts.push(`修订 #${test.revision}`);
  }
  if (test.latencyMs !== undefined && test.latencyMs !== null) {
    parts.push(`耗时 ${test.latencyMs}ms`);
  }
  parts.push(`Schema ${schemaVerdictText(test)}`);
  return parts.join(' · ');
}
