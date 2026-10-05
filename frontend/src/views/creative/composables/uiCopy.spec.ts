import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

/**
 * 「说明文案里不许出现界面根本不显示的枚举码」的静态守卫（v1 人工测试反馈：注释口径统一）。
 *
 * <p><b>为什么值得一条测试</b>：说明文案是**手写的**，而枚举码是后端/配置里的事实。
 * 两者会各自漂移，而且漂移的方向很隐蔽——页面上明明写着「待确认 / 已确认」，
 * 说明里却写 `PENDING / CONFIRMED`；更糟的一种是说明里写「标注 MEDIUM」，
 * 而界面早就把那两个标签改成了「实测 / 弱启发」——**读者按说明去找 MEDIUM，找不到**。
 * 这类文案错误不会报错、不会被类型检查抓住，只会让人读不懂。</p>
 *
 * <p>断言按"具体那句话"钉，而不是"整个文件不许出现 CONFIRMED"——
 * 组件代码里当然会有 `=== 'CONFIRMED'` 这种比较（那是逻辑，不是文案）。</p>
 *
 * @author creative
 */

/** 读一个组件源码（相对 views/creative） */
function source(relative: string): string {
  return readFileSync(new URL('../' + relative, import.meta.url), 'utf-8');
}

describe('界面说明文案与界面实际显示保持一致', () => {
  it('事实区块：用「已确认 / 待确认 / 冲突 / 已否决」，不用 CONFIRMED/PENDING', () => {
    const text = source('project/components/ProjectFactsBlock.vue');
    expect(text).toContain('只有<b>已确认</b>的事实才会进入');
    expect(text).toContain('<b>待确认</b>');
    expect(text).toContain('<b>冲突</b>');
    // 界面上的状态标签由 factStatusLabel 给中文，说明里不该再回退成枚举
    expect(text).not.toContain('<b>CONFIRMED</b>');
    expect(text).not.toContain('PENDING 与已否决');
  });

  it('基因页推荐说明：说界面真的会显示的两个标签（实测 / 弱启发），不写「标注 MEDIUM」', () => {
    const text = source('dna/components/VisualDnaPanel.vue');
    expect(text).not.toContain('标注 MEDIUM');
    expect(text).toContain('<b>实测</b>与<b>弱启发</b>');
  });

  it('视觉门准入项说明：不写 BLOCK/CONDITION 这种等级码（等级以下表「等级」列为准）', () => {
    const text = source('review/components/GatePanel.vue');
    expect(text).not.toContain('硬性项（BLOCK）');
    expect(text).not.toContain('建议项（CONDITION）');
    expect(text).toContain('以下表「等级」列为准');
  });

  it('保真等级下拉：两个页面用同一套中文口径（不再一处 STRICT 一处中文）', () => {
    const modulePlan = source('moduleplan/index.vue');
    const storyboard = source('storyboard/index.vue');
    for (const text of [modulePlan, storyboard]) {
      expect(text).toContain('严格保真（结构与配色不得变）');
      expect(text).toContain('允许艺术化（可换场景与角度）');
    }
    expect(modulePlan).not.toContain('STRICT（产品必须一致）');
  });

  it('辅助入口：流程指引被裁掉的页面仍要能打开检查器/资产/质检/模块规划', () => {
    // R44 把流程指引从四页裁掉后，这几个面板**没有触发点了**（装配里还在、就是打不开）。
    // 这条静态守卫钉住"工作台补齐了这四个入口"，以及"有指引线时不重复渲染"。
    const ws = source('components/CreativeWorkspace.vue');
    for (const label of ['检查器', '资产', '质检与交付', '模块规划']) {
      expect(ws, `辅助入口缺少「${label}」`).toContain(`>\n              ${label}\n            </button>`);
    }
    expect(ws).toContain('v-if="!hasGuide"');
    expect(ws).toContain("const hasGuide = computed(() => assembledCodes.value.includes('STEP_NAVIGATOR'))");
  });

  it('基因页的提示词框：不许变成"能改"（那是第三个真相源），但必须给一条跳去出图框的路', () => {
    const panel = source('dna/components/VisualDnaPanel.vue');
    const page = source('dna/index.vue');
    const box = source('dna/components/PromptWithSwatches.vue');
    // 两个框保持只读（本页没有可落库的提示词存储）。
    // 守卫口径：断言"意图"而不是某个具体标签——原先这里钉的是 `<el-input ... readonly />` 的字面量，
    // 于是本轮把色号渲染成色块（v1 反馈 1.2）就把它碰红了，而"只读"这件事其实没变。
    expect(panel).toContain('label="正向提示词（要什么）" :text="promptText"');
    expect(panel).toContain('label="负向提示词（不要什么）" :text="negativeText"');
    // 展示组件内部：原文框只读；色块只读渲染；**不许出现双向绑定**（那才是"能改"）
    expect(box).toContain(':model-value="text"');
    expect(box).toContain('readonly');
    expect(box).not.toContain('v-model="text"');
    expect(box).not.toContain('@update:model-value');
    expect(panel).not.toContain('v-model="promptText"');
    // 但要说清"本页改不了"，并给按钮一跳就到真正生效的地方
    expect(panel).toContain('本页改不了');
    expect(panel).toContain('去出图框改提示词');
    expect(panel).toContain("(e: 'open-generation'): void;");
    expect(page).toContain('@open-generation="openGeneration"');
    expect(page).toContain("window.open(`/creative/project?taskId=${taskId.value}`, '_self')");
  });
});
