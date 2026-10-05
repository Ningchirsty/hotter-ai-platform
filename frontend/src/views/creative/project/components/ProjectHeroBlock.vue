<template>
  <section class="block" data-block="PROJECT_HERO">
    <div class="block-head">
      <h4>{{ stepHeading }}生成 HERO 主图</h4>
      <span class="muted">每次出 1 张候选；重试=新增一次候选</span>
    </div>
    <div class="form-row">
      <label>出图工作流</label>
      <el-select
        :model-value="workflowCode"
        placeholder="使用默认已发布工作流"
        style="width: 320px"
        @update:model-value="(v: string) => (workflowCode = v)"
      >
        <el-option
          v-for="wf in workflows"
          :key="wf.workflowCode"
          :label="`${wf.workflowCode}（${wf.capabilityCode} · ${wf.published ? '已发布' : wf.status}）`"
          :value="wf.workflowCode"
        />
      </el-select>
    </div>
    <div class="form-row">
      <label>画面描述</label>
      <el-input
        :model-value="prompt"
        type="textarea"
        :rows="3"
        maxlength="1000"
        show-word-limit
        placeholder="留空则用默认主图提示词（产品居中、纯净背景、影棚光、保留原有结构与配色）"
        @update:model-value="(v: string) => (prompt = v)"
      />
    </div>
    <template v-if="dnaStateLoaded">
      <p v-if="promptFromDna" class="dna-hint">
        已按<b>视觉基因</b>预填提示词（用到的维度：{{ appliedText(promptApplied) || '—' }}）。可以改；改了就以你写的为准。
      </p>
      <p v-else-if="dnaLocked" class="dna-hint">
        将按<b>已锁定的视觉基因 {{ dnaLockedVersion }}</b>出图，但派生提示词尚未载入——点
        <el-button link type="primary" size="small" @click="$emit('prefill-from-dna')">这里</el-button>
        载入。
      </p>
      <p v-else class="dna-hint muted">
        这个项目还没有锁定视觉基因，提示词按默认模板生成。建议先到
        <el-button link type="primary" size="small" @click="$emit('open-dna')">视觉基因</el-button>
        定义配色与光线并锁定。
      </p>
    </template>
    <p v-else class="dna-hint muted">正在检查该项目的视觉基因…</p>
    <div class="form-row">
      <label>负向提示</label>
      <el-input
        :model-value="negativePrompt"
        type="textarea"
        :rows="2"
        maxlength="500"
        show-word-limit
        placeholder="留空则用默认（文字、水印、产品变形、结构缺失…）"
        @update:model-value="(v: string) => (negativePrompt = v)"
      />
    </div>
    <div class="submit-row">
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!referenceCount"
        @click="$emit('generate')"
      >
        {{ submitting ? '提交中…' : '生成 HERO 主图候选' }}
      </el-button>
      <span v-if="!referenceCount" class="hint">请先上传参考图</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { CreativeWorkflowVO } from '@/api/creative/types';
import { appliedText } from '../../composables/promptApplied';
import { useStepHeading } from '../../composables/stepNumbering';

/** 标题编号：本页步骤号（v1 反馈；没有工作台上下文时不显示编号） */
const stepHeading = useStepHeading('ProjectHeroBlock');

/**
 * 项目页区块⑤「生成 HERO 主图」（V0.2 R33）。
 *
 * <p><b>三个输入用三个 v-model</b>，而不是把页面的 `heroForm` 整个传进来：
 * 表单字段的写入路径要显式（`v-model:workflow-code` / `v-model:prompt` / `v-model:negative-prompt`），
 * 这样"谁改了提示词"在模板上就能读出来，也不会有"子组件直接改父对象属性"的隐式耦合。</p>
 *
 * <p><b>提示词来自视觉基因的三种状态照实显示</b>（已预填 / 基因已锁定但未载入 / 没有基因）：
 * 这是 R4 起的口径——提示词是谁给的必须让人看得见，改过就以人写的为准。</p>
 *
 * @author creative
 */
const workflowCode = defineModel<string>('workflowCode', { default: '' });
const prompt = defineModel<string>('prompt', { default: '' });
const negativePrompt = defineModel<string>('negativePrompt', { default: '' });

defineProps<{
  /** 可选工作流（来自出图内核的已发布清单） */
  workflows: CreativeWorkflowVO[];
  /** 提交中（按钮转圈） */
  submitting: boolean;
  /** 参考图数量：0 张时按钮禁用并说明原因（与页面既有口径一致） */
  referenceCount: number;
  /** 该项目视觉基因状态是否已读到（读到之前不显示提示词来源说明） */
  dnaStateLoaded: boolean;
  /** 提示词是否由基因预填 */
  promptFromDna: boolean;
  /** 预填用到的维度（给人核对） */
  promptApplied: string[];
  /** 是否有已锁定的基因 */
  dnaLocked: boolean;
  /** 已锁定的基因版本 */
  dnaLockedVersion: string;
}>();

defineEmits<{
  (e: 'generate'): void;
  (e: 'prefill-from-dna'): void;
  (e: 'open-dna'): void;
}>();
</script>
