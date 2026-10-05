/**
 * 内容任务状态的中文标签（唯一口径）。
 *
 * <p><b>为什么从页面里搬出来</b>：这套映射原先只写在「内容任务」页的一个局部函数里，
 * 而**视觉项目页的头部**也在显示内容协同状态——它拿不到那份映射，于是直接打印枚举码
 * （真机上是「内容协同状态：CONDITIONAL_READY」，v1 人工测试反馈的"去英文"里就有它）。
 * 同一件事两处实现，必然有一处漏翻；这里给两处共用。</p>
 *
 * <p>放在内容域的 api 目录下：状态属于内容域（`cp_task.status`），
 * 创作域只是**读**它——与 `@/api/content/brief`、`@/api/content/fact` 同一方向。</p>
 *
 * <p>认不出的状态原样返回（新状态上线时页面上会露出代号，而不是被抹成空白或"未知"）。</p>
 *
 * @author content
 */

/** 状态码 → 中文 */
const TASK_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  PARSING: '解析中',
  PENDING_CONFIRM: '待确认/待补料',
  CONDITIONAL_READY: '条件开工',
  READY: '可开工'
};

/**
 * 内容任务状态的中文名。
 *
 * @param status 状态码
 * @returns 中文名；认不出原样返回；空值返回 `-`（与内容任务页原来的写法保持一致）
 */
export function taskStatusLabel(status?: string): string {
  if (!status) {
    return '-';
  }
  return TASK_STATUS_LABELS[status] || status;
}
