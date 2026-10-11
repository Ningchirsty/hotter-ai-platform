<template>
  <section class="block" data-block="PROJECT_GENERATIONS">
    <div class="block-head">
      <h4>{{ stepHeading }}出图候选</h4>
      <span class="muted">
        {{ generations.length }} 条
        <template v-if="polling">· 状态跟踪中…</template>
      </span>
    </div>
    <p v-if="!generations.length" class="empty">还没有候选。填好描述后点上面的生成按钮。</p>
    <div v-else class="candidate-grid">
      <div v-for="gen in generations" :key="String(gen.id)" class="candidate-card">
        <div class="candidate-cover" @click="gen.previewable && $emit('preview', gen)">
          <img
            v-if="urlOf('gen-' + gen.id)"
            :src="urlOf('gen-' + gen.id)"
            :alt="`候选 ${gen.candidateNo}`"
          />
          <span v-else class="cover-placeholder">
            {{ gen.status === 'RUNNING' || gen.status === 'QUEUED' ? '出图中…' : '暂无产出' }}
          </span>
        </div>
        <div class="candidate-meta">
          <span class="gen-status" :class="'is-' + statusType(gen.status)">
            #{{ gen.candidateNo }} · {{ gen.statusDesc || statusLabel(gen.status) }}
          </span>
          <span v-if="gen.outputWidth" class="muted">{{ gen.outputWidth }}×{{ gen.outputHeight }}</span>
          <span v-if="gen.durationMs" class="muted">{{ (gen.durationMs / 1000).toFixed(1) }}s</span>
        </div>
        <p v-if="gen.errorMessage" class="gen-error" :title="gen.errorMessage">{{ gen.errorMessage }}</p>
        <div class="candidate-actions">
          <el-button v-if="gen.previewable" size="small" text type="primary" @click="$emit('preview', gen)">
            预览
          </el-button>
          <el-button
            v-if="gen.retryable"
            size="small"
            text
            type="warning"
            :loading="retryingId === String(gen.id)"
            @click="$emit('retry', gen)"
          >
            重试
          </el-button>
        </div>
      </div>
    </div>

    <!-- 父子对账（增量 19）：父＝场景派发出的平台任务，子＝逐候选登记的治理任务。
         折叠、按需取数、纯只读——它是观测面，不该给"看项目"这个常用动作加一次往返。 -->
    <LedgerReconciliationPanel :task-id="taskId" />
  </section>
</template>

<script setup lang="ts">
import type { DpGenerationVO } from '@/api/creative/types';
import { useStepHeading } from '../../composables/stepNumbering';
import LedgerReconciliationPanel from './LedgerReconciliationPanel.vue';

/** 标题编号：本页步骤号（v1 反馈；没有工作台上下文时不显示编号） */
const stepHeading = useStepHeading('ProjectGenerationsBlock');

/**
 * 项目页区块⑥「出图候选」（V0.2 R33）。
 *
 * <p>缩略图走页面的 blob URL 台账（`urlOf('gen-<id>')`）：台账与 revoke 时机在页面统一管，
 * 组件只读——否则每个组件各自 createObjectURL 就会出现"切项目后旧 URL 没释放"的内存泄漏，
 * 这类问题在暗色工作台里不容易被发现。</p>
 *
 * <p>只出「预览 / 重试」两个动作：选定与质检在生产页（那里有逐屏上下文），
 * 这里不做重复入口，避免同一个候选两个地方都能改状态。</p>
 *
 * @author creative
 */
defineProps<{
  /** 候选列表（页面按项目取，含运行中/失败/已产出） */
  generations: DpGenerationVO[];
  /** 是否在跟踪状态（有运行中的候选时页面会轮询） */
  polling: boolean;
  /** blob URL 台账读取（生命周期由页面管） */
  urlOf: (key: string) => string;
  /** 状态文案与样式（口径在页面，避免两处维护） */
  statusLabel: (status?: string) => string;
  statusType: (status?: string) => string;
  /** 正在重试的候选ID（按钮 loading） */
  retryingId: string;
  /** 当前项目ID（父子对账面板按需取数；为空时不取） */
  taskId?: string | number | null;
}>();

defineEmits<{
  (e: 'preview', gen: DpGenerationVO): void;
  (e: 'retry', gen: DpGenerationVO): void;
}>();
</script>
