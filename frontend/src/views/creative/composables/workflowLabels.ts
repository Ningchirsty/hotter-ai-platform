import type { CreativeWorkflowVO } from '@/api/creative/types';

/**
 * 出图能力/工作流的中文口径（v1 人工测试反馈：分镜卡片与出图框里全是英文代号）。
 *
 * <p><b>问题</b>：分镜卡片底部写着「出图能力：wf-i2i-qwen21」，项目页的「出图工作流」下拉
 * 写的是「wf-i2i-qwen21（I2I · 已发布）」——两处都是**给代码看的标识符**。
 * 设计同事要读的是「图生图」；代号是排障时才需要的东西，放 tooltip 里就够。</p>
 *
 * <p><b>中文名从哪来</b>：工作流清单接口（`GET /creative/v1/workflows`，即
 * `image_workflow_version` 的镜像）每行都带 `capabilityCode`，而能力的中文名在
 * `script/image/workflows/image-workflow-contracts.json` 的 `capabilities[].name` 里
 * （文生图 / 图生图 / 指令改图 / 商品去背景 / 白底图）。所以这里只维护
 * "能力编码 → 中文"，**不从 `wf-i2i-*` 这种命名前缀去猜**——前缀是巧合，不是契约。</p>
 *
 * <p><b>认不出就原样显示编码</b>：新加一个能力时页面会露出英文代号（难看但可发现），
 * 不会显示成空白或"未知能力"那种把信息抹掉的样子。</p>
 *
 * @author creative
 */

/** 能力编码 → 中文名（与 contracts 的 `capabilities[].name` 保持一致） */
export const CAPABILITY_LABELS: Record<string, string> = {
  T2I: '文生图',
  I2I: '图生图',
  EDIT: '指令改图',
  BGREMOVE: '商品去背景',
  WHITEBG: '白底图'
};

/**
 * 能力编码的中文名。
 *
 * @param capabilityCode 能力编码（如 `I2I`）
 * @returns 中文名；认不出时原样返回编码；空值返回空串
 */
export function capabilityLabel(capabilityCode?: string | null): string {
  const code = (capabilityCode || '').trim();
  if (!code) {
    return '';
  }
  return CAPABILITY_LABELS[code] || code;
}

/**
 * 按工作流编码查它属于哪个能力。
 *
 * @param workflowCode 工作流编码（如 `wf-i2i-qwen21`）
 * @param workflows    工作流清单（页面从接口拿到的）
 * @returns 能力编码；查不到返回空串
 */
export function capabilityOf(
  workflowCode?: string | null,
  workflows?: CreativeWorkflowVO[] | null
): string {
  const code = (workflowCode || '').trim();
  if (!code) {
    return '';
  }
  const hit = (workflows || []).find((wf) => wf.workflowCode === code);
  return hit?.capabilityCode || '';
}

/**
 * 分镜卡片上「出图能力」的显示文案。
 *
 * @param workflowCode 工作流编码
 * @param workflows    工作流清单（拿不到时回落成显示编码本身）
 * @returns 中文能力名；查不到就返回编码；编码为空返回「—」
 */
export function workflowDisplayName(
  workflowCode?: string | null,
  workflows?: CreativeWorkflowVO[] | null
): string {
  const code = (workflowCode || '').trim();
  if (!code) {
    return '—';
  }
  const label = capabilityLabel(capabilityOf(code, workflows));
  return label || code;
}

/**
 * 「出图工作流」下拉项的文案：中文能力名在前，代号与发布状态放在括号里。
 *
 * @param wf 工作流
 * @returns 形如 `图生图（wf-i2i-qwen21 · 已发布）`
 */
export function workflowOptionLabel(wf: CreativeWorkflowVO): string {
  const name = capabilityLabel(wf.capabilityCode) || wf.workflowCode;
  const state = wf.published ? '已发布' : wf.status || '未知状态';
  return `${name}（${wf.workflowCode} · ${state}）`;
}
