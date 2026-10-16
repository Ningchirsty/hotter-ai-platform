import { describe, expect, it } from 'vitest';
import { highRiskConfirmText, policyRowRisk, policyUpdateRisk } from './highRisk';

/**
 * 路由策略高危判定与确认文案（Q6 + Q1C 前端口径）。
 *
 * <p>重点钉两件：①判定不能宽到"改备注也要高风险权限"（那会让人绕开界面），
 * 也不能窄到放过放开外发；②确认文案必须把**影响面**与**权限点**说清——
 * 这两句正是"机器只提议、人必须知道自己在做什么"的落点。</p>
 */
describe('route high risk', () => {
  it('★ 放开外发 / 限制级·严格级 / 外部部署 = 高风险；内部级禁止外发 = 不高风险', () => {
    expect(policyRowRisk({ dataLevel: 'INTERNAL', allowExternal: 'N', preferredDeployment: 'LOCAL' }).highRisk).toBe(false);
    expect(policyRowRisk({ dataLevel: 'INTERNAL', allowExternal: 'Y' }).highRisk).toBe(true);
    expect(policyRowRisk({ dataLevel: 'RESTRICTED', allowExternal: 'N' }).highRisk).toBe(true);
    expect(policyRowRisk({ dataLevel: 'STRICT' }).highRisk).toBe(true);
    expect(policyRowRisk({ dataLevel: 'PUBLIC', allowExternal: 'N', preferredDeployment: 'EXTERNAL_API' }).highRisk).toBe(true);
    expect(policyRowRisk({ dataLevel: 'PUBLIC', allowExternal: 'N', preferredDeployment: 'GROUP' }).highRisk).toBe(false);
    expect(policyRowRisk(undefined).highRisk).toBe(false);
  });

  it('原因是人话，且能说清"哪一条命中了"', () => {
    const risk = policyRowRisk({ dataLevel: 'STRICT', allowExternal: 'Y', preferredDeployment: 'EXTERNAL_API' });
    expect(risk.reasons).toHaveLength(3);
    expect(risk.reasons.join(' ')).toContain('放开外发');
    expect(risk.reasons.join(' ')).toContain('严格级数据');
    expect(risk.reasons.join(' ')).toContain('外部部署');
    expect(policyRowRisk({ dataLevel: 'RESTRICTED' }).reasons.join(' ')).toContain('限制级数据');
  });

  it('★ 编辑：新旧任一命中算高风险；改之前命中要标出来（否则人不知道自己动了什么）', () => {
    const shrink = policyUpdateRisk(
      { dataLevel: 'INTERNAL', allowExternal: 'N', preferredDeployment: 'LOCAL' },
      { dataLevel: 'INTERNAL', allowExternal: 'Y', preferredDeployment: 'LOCAL' }
    );
    expect(shrink.highRisk).toBe(true);
    expect(shrink.reasons[0]).toContain('改之前就命中');

    const widen = policyUpdateRisk(
      { dataLevel: 'INTERNAL', allowExternal: 'Y' },
      { dataLevel: 'INTERNAL', allowExternal: 'N' }
    );
    expect(widen.highRisk).toBe(true);
    expect(widen.reasons.join(' ')).not.toContain('改之前就命中');

    const harmless = policyUpdateRisk(
      { dataLevel: 'INTERNAL', allowExternal: 'N', preferredDeployment: 'LOCAL' },
      { dataLevel: 'INTERNAL', allowExternal: 'N', preferredDeployment: 'LOCAL' }
    );
    expect(harmless.highRisk).toBe(false);
    expect(harmless.reasons).toEqual([]);
  });

  it('★ 确认文案：说清动作、原因、权限点与影响面；无引用也要如实说', () => {
    const risk = policyRowRisk({ dataLevel: 'INTERNAL', allowExternal: 'Y' });
    const withRefs = highRiskConfirmText('保存策略', '图片生成', 'image_generation', risk, [
      { refType: 'AGENT_VERSION', refLabel: 'Agent「图片助手」版本 1.0.0（已发布）' },
      { refType: 'TASK', count: 12, refLabel: '历史任务 12 条' }
    ]);
    expect(withRefs).toContain('保存策略「图片生成」？');
    expect(withRefs).toContain('放开外发');
    expect(withRefs).toContain('aig:capability:high-risk');
    expect(withRefs).toContain('影响面（谁在用它）');
    expect(withRefs).toContain('图片助手');
    expect(withRefs).toContain('历史任务 12 条');
    expect(withRefs).toContain('停用');

    const noRefs = highRiskConfirmText('删除策略', undefined, 'code_x', risk, []);
    expect(noRefs).toContain('删除策略「code_x」？');
    expect(noRefs).toContain('没有任何 Agent 版本');
  });

  it('引用超过 5 条折叠，避免确认框长到没人看', () => {
    const risk = policyRowRisk({ dataLevel: 'STRICT' });
    const many = highRiskConfirmText('保存策略', undefined, 'code_y', risk,
      Array.from({ length: 7 }, (_, index) => ({ refType: 'AGENT_VERSION', refLabel: `Agent#${index}` })));
    expect(many).toContain('另有 2 类引用');
  });
});
