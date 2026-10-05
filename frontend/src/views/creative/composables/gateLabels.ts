/**
 * 闸门等级 / DNA 证据类型的中文标签（v1 人工测试反馈：去英文/去编号）。
 *
 * <p><b>为什么抽出来</b>：同一个概念在两个地方各写各的——
 * 评审页的「等级」列写死「硬性 / 建议」，而项目页「事实确认」的闸门状态带
 * 直接打印 `BLOCK`，「事实字段」下拉也把 `BLOCK` 拼进选项文案；
 * DNA 证据链的「类型」列则直接打印 `FACT / DEFAULT / MANUAL`。
 * 一页中文一页英文，读的人要自己建立对照关系。</p>
 *
 * <p><b>认不出的值原样返回</b>：配置里加了新等级/新证据类型时页面上会露出英文代号
 * （难看但可发现），不会变成空白或"未知"。</p>
 *
 * @author creative
 */

/**
 * 闸门等级 → 中文（与内容域 `ContentGateLevelEnum` / 内容任务页同一套措辞）。
 *
 * <p>三档都要在：`BLOCK` 与 `CONDITION` 是闸门项的等级，而**事实字段的闸门等级还会出现
 * `NOTICE`**（真机上「品牌调性说明」那一条就是它）——只映射前两档的话，
 * 第三档会原样露出 `NOTICE`（本地实测踩到过）。</p>
 */
const GATE_LEVEL_LABELS: Record<string, string> = {
  BLOCK: '硬性',
  CONDITION: '建议',
  NOTICE: '非阻断提醒'
};

/** DNA 证据类型 → 中文（与 `VisualDnaSchema.addEvidence` 的 kind 取值一致） */
const EVIDENCE_KIND_LABELS: Record<string, string> = {
  FACT: '事实',
  DEFAULT: '默认值',
  MANUAL: '人工编辑',
  MODEL: '模型',
  REFERENCE: '参考图'
};

/**
 * 闸门等级的中文名。
 *
 * @param level 等级码（BLOCK / CONDITION / NOTICE）
 * @returns 硬性 / 建议 / 非阻断提醒；认不出原样返回；空值空串
 */
export function gateLevelLabel(level?: string | null): string {
  const code = (level || '').trim();
  return code ? GATE_LEVEL_LABELS[code] || code : '';
}

/**
 * DNA 证据类型的中文名。
 *
 * @param kind 类型码（FACT / DEFAULT / MANUAL / MODEL / REFERENCE）
 * @returns 中文名；认不出原样返回；空值空串
 */
export function dnaEvidenceKindLabel(kind?: string | null): string {
  const code = (kind || '').trim();
  return code ? EVIDENCE_KIND_LABELS[code] || code : '';
}
