<template>
  <!-- 三栏之右：修订历史与差异（设计 §6.3：右栏可承载「修订差异」） -->
  <el-card shadow="never">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <b>修订历史与差异</b>
        <span class="text-gray-500">当前修订 #{{ currentRevision }}</span>
      </div>
    </template>
    <el-table
      :data="revisions"
      size="small"
      border
      highlight-current-row
      style="max-height: 240px; overflow: auto"
      @current-change="(row: AigStudioRevisionVO | undefined) => emit('select', row)"
    >
      <el-table-column label="修订" prop="revisionNo" width="70" />
      <el-table-column label="来源" prop="source" width="140" show-overflow-tooltip />
      <el-table-column label="说明" prop="summary" show-overflow-tooltip />
      <el-table-column label="时间" prop="createTime" width="160" show-overflow-tooltip />
    </el-table>

    <div class="mt-2">
      <el-button
        v-hasPermi="['aig:studio:draft:edit']"
        size="small"
        :disabled="selectedRevisionNo === undefined"
        @click="emit('rollback')"
      >
        回滚到所选修订
      </el-button>
      <el-button size="small" :disabled="selectedRevisionNo === undefined" @click="emit('clear')">
        清除选择
      </el-button>
    </div>

    <el-divider content-position="left">差异（当前 ↔ 所选修订）</el-divider>
    <p v-if="selectedRevisionNo === undefined" class="text-gray-500">
      从上面点一条修订，这里会逐节显示与当前内容的差异（改动过的节会标出来）。
    </p>
    <template v-else>
      <p :class="selectedDiffChanged.length ? 'text-orange-500' : 'text-gray-500'">
        {{ selectedSummary }}
      </p>
      <div v-for="item in selectedDiff" :key="item.key" class="diff-block">
        <div class="diff-head">
          <el-tag v-if="item.changed" type="warning" size="small">改动</el-tag>
          <el-tag v-else type="info" size="small">一致</el-tag>
          <span class="diff-label">{{ item.label }}</span>
        </div>
        <template v-if="item.changed">
          <div class="diff-side"><b>修订 #{{ selectedRevisionNo }}：</b>{{ item.revision || '（空）' }}</div>
          <div class="diff-side"><b>当前：</b>{{ item.current || '（空）' }}</div>
        </template>
      </div>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import type { AigStudioRevisionVO } from '@/api/aigov/studio/types';
import type { StudioSectionDiff } from '../diff';

defineOptions({ name: 'StudioRevisionColumn' });

/**
 * 右栏（修订历史与差异）。
 *
 * <p><b>为什么只收 props、回抛事件</b>：选中的修订号、差异计算、回滚动作都留在编辑器里——
 * 它们和"当前内容/期望修订号"是同一份状态，复制进子组件就会有两份真相。
 * 这里只负责"把它显示出来"，因此没有任何自己的业务状态。</p>
 */
defineProps<{
  /** 当前修订号（页头与差异标题都展示它） */
  currentRevision: number;
  /** 修订清单 */
  revisions: AigStudioRevisionVO[];
  /** 当前选中的修订号（undefined=未选） */
  selectedRevisionNo?: number;
  /** 逐节差异 */
  selectedDiff: StudioSectionDiff[];
  /** 差异摘要 */
  selectedSummary: string;
  /** 改动过的节 */
  selectedDiffChanged: StudioSectionDiff[];
}>();

const emit = defineEmits<{
  (event: 'select', row: AigStudioRevisionVO | undefined): void;
  (event: 'rollback'): void;
  (event: 'clear'): void;
}>();
</script>
