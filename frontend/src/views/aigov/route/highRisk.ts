/**
 * 路由策略的「高风险变更」判定与确认文案（Q6 + Q1C 的前端口径）。
 *
 * <p><b>为什么要有一份前端判定</b>：服务端已经会拦（没有 {@code aig:capability:high-risk} 就 403），
 * 但"点了保存才被拒"是很差的体验——用的人会以为是系统坏了。前端先算出"这次算高风险"，
 * 于是能提前做两件事：①弹确认框列出**谁在用它**（Q6）②提醒"这需要能力高风险变更权限"。
 * <b>它不承担安全职责</b>：真正的闸门在服务端，这里只负责把话说在前面。</p>
 *
 * <p>口径必须与服务端 {@code AigHighRiskChanges} 一致（同一份规则写两遍是刻意接受的重复：
 * 前端不能执行 Java；但两边都有单测钉住同样的用例，任一边改了规则，另一边会红）。</p>
 */

/** 一次策略变更的高风险判定 */
export interface PolicyHighRisk {
  /** 是否属于高风险变更 */
  highRisk: boolean;
  /** 命中的原因（逐条可读，用于确认框与提示） */
  reasons: string[];
}

/** 策略的关键列（判定只看这三列） */
export interface PolicyRiskInput {
  dataLevel?: string;
  allowExternal?: string;
  preferredDeployment?: string;
}

const EXTERNAL_DEPLOYMENTS = ['EXTERNAL_ENTERPRISE', 'EXTERNAL_API'];
const HIGH_LEVELS = ['RESTRICTED', 'STRICT'];

/**
 * 判定一行策略是否高风险（与服务端 `AigHighRiskChanges#policyRow` 同规则）。
 *
 * @param row 目标行的关键列
 * @returns 判定结果与原因
 */
export function policyRowRisk(row?: PolicyRiskInput): PolicyHighRisk {
  const reasons: string[] = [];
  const allowExternal = (row?.allowExternal || '').trim().toUpperCase();
  const level = (row?.dataLevel || '').trim().toUpperCase();
  const preferred = (row?.preferredDeployment || '').trim().toUpperCase();

  if (allowExternal === 'Y') {
    reasons.push('放开外发：数据会离开公司可控环境，且可能无法撤回');
  }
  if (HIGH_LEVELS.includes(level)) {
    reasons.push(`作用于${level === 'STRICT' ? '严格' : '限制'}级数据：改动会直接影响这类数据能不能被处理`);
  }
  if (EXTERNAL_DEPLOYMENTS.includes(preferred)) {
    reasons.push('首选外部部署：等于把这条链路钉在外部服务上');
  }
  return { highRisk: reasons.length > 0, reasons };
}

/**
 * 编辑场景的判定：**改之前那一行与改之后的目标行，任一命中就算**。
 *
 * @param target   目标行（表单里的值）
 * @param previous 改之前那一行（可空）
 * @returns 判定结果与原因
 */
export function policyUpdateRisk(target?: PolicyRiskInput, previous?: PolicyRiskInput): PolicyHighRisk {
  const next = policyRowRisk(target);
  if (next.highRisk) {
    return next;
  }
  const before = policyRowRisk(previous);
  if (before.highRisk) {
    return { highRisk: true, reasons: before.reasons.map((reason) => `改之前就命中：${reason}`) };
  }
  return { highRisk: false, reasons: [] };
}

/**
 * 高风险变更的确认文案（把"改什么、影响谁、需要什么权限、怎么退出"一次说完）。
 *
 * @param title          动作标题（如「保存策略」「删除策略」）
 * @param capabilityLabel 能力展示名（可空则用编码）
 * @param capabilityCode 能力编码
 * @param risk           高风险判定
 * @param refs           影响面（谁在用它；可空）
 * @returns 确认框文案
 */
export function highRiskConfirmText(
  title: string,
  capabilityLabel: string | undefined,
  capabilityCode: string,
  risk: PolicyHighRisk,
  refs?: { refLabel?: string; refType?: string; count?: number }[]
): string {
  const lines = [`${title}「${capabilityLabel || capabilityCode}」？`, ''];
  if (risk.highRisk) {
    lines.push('这是一次高风险变更：');
    risk.reasons.forEach((reason) => lines.push(`· ${reason}`));
    lines.push('', '需要「能力高风险变更」权限点（aig:capability:high-risk）；没有该权限时保存会被服务端拒绝。');
  } else {
    lines.push('本次变更不属于高风险变更（不需要额外权限点），但仍请先确认影响面：');
  }
  const list = refs || [];
  lines.push('', '影响面（谁在用它）：');
  if (list.length === 0) {
    lines.push('· 当前没有任何 Agent 版本 / 历史任务 / 训练台草稿引用它（仍请确认这不是"还没来得及用"）');
  } else {
    list.slice(0, 5).forEach((ref) => {
      lines.push(`· ${ref.refLabel || ref.refType || '引用'}${ref.count ? `（${ref.count}）` : ''}`);
    });
    if (list.length > 5) {
      lines.push(`· …另有 ${list.length - 5} 类引用`);
    }
  }
  lines.push('', '如只是暂时不用，建议改为「停用」——停用可以恢复。');
  return lines.join('\n');
}
