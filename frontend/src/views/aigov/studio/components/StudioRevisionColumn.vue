<template>
  <el-card shadow="never">
    <!-- 三栏之右：辅助区（设计 §6.3：右栏可在「培训助手 / 测试预览 / 修订差异」之间切换）。
         这里兑现「测试预览」与「修订差异」两支；「培训助手」（变更提案面板）刻意仍留在三栏下方——
         它要并排显示「分节原文 / 改动 / 理由」，塞进 7/24 的右栏后每列只剩几个字，审阅会变成误点。
         注释必须写在根元素内部：根级注释也算一个子节点，会让「模板必须只有一个根节点」规则失败。 -->
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <b>辅助区</b>
        <span class="text-gray-500">当前修订 #{{ currentRevision }}</span>
      </div>
    </template>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="修订差异" name="diff">
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
      </el-tab-pane>

      <el-tab-pane label="测试预览" name="preview">
        <el-alert type="info" :closable="false" show-icon class="mb-2" :title="testPreviewSummary(lastTest)" />
        <el-descriptions v-if="lastTest" :column="1" border size="small">
          <el-descriptions-item label="版本哈希（测的是哪一版内容）">
            {{ hashShort(lastTest.contentHash) }}
          </el-descriptions-item>
          <el-descriptions-item label="结果摘要">
            {{ hashShort(lastTest.resultDigest) }}
          </el-descriptions-item>
          <el-descriptions-item label="Schema 结论">
            {{ schemaVerdictText(lastTest) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="lastTest.modelKey || lastTest.deploymentType" label="模型/部署">
            {{ lastTest.modelKey || '-' }} / {{ lastTest.deploymentType || '-' }}
            <span class="text-gray-500">{{ lastTest.externalCall ? '（发生了外发）' : '（未外发）' }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="lastTest.reason || lastTest.errorCode" label="原因">
            {{ lastTest.reason || '-' }} <span v-if="lastTest.errorCode">（{{ lastTest.errorCode }}）</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="lastTest.output" label="输出（预览）">
            <pre class="test-output">{{ lastTest.output }}</pre>
            <span v-if="lastTest.outputTruncated" class="text-orange-500">（输出已截断，完整内容见调用审计）</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="lastTest.traceId" label="traceId">
            {{ lastTest.traceId }}
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import type { AigStudioRevisionVO, AigStudioTestRunVO } from '@/api/aigov/studio/types';
import type { StudioSectionDiff } from '../diff';
import { hashShort, schemaVerdictText, testPreviewSummary } from '../testPreview';

defineOptions({ name: 'StudioRevisionColumn' });

/**
 * 右栏（辅助区：修订差异 / 测试预览）。
 *
 * <p><b>为什么只收 props、回抛事件</b>：选中的修订号、差异计算、回滚动作都留在编辑器里——
 * 它们和"当前内容/期望修订号"是同一份状态，复制进子组件就会有两份真相。
 * 这里只负责"把它显示出来"，因此没有任何自己的业务状态。</p>
 */
const props = defineProps<{
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
  /** 最近一次测试结果（可空=还没跑过） */
  lastTest?: AigStudioTestRunVO;
}>();

const emit = defineEmits<{
  (event: 'select', row: AigStudioRevisionVO | undefined): void;
  (event: 'rollback'): void;
  (event: 'clear'): void;
}>();

/**
 * 当前页签。
 *
 * <p>收到新的测试结果时自动切到「测试预览」：刚跑完测试的人想看的就是结果，
 * 让他再点一次页签没有意义（这也是设计 §6.3「右栏可切换」想要的行为）。</p>
 */
const activeTab = ref(props.lastTest ? 'preview' : 'diff');

watch(
  () => props.lastTest?.linkId,
  (linkId, previous) => {
    if (linkId !== undefined && linkId !== null && linkId !== previous) {
      activeTab.value = 'preview';
    }
  }
);
</script>

<style scoped>
.mb-2 {
  margin-bottom: 10px;
}

.diff-block {
  border-bottom: 1px dashed var(--app-border-color, #e4e7ed);
  padding: 6px 0;
}

.diff-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.diff-label {
  font-size: 13px;
  font-weight: 600;
}

.diff-side {
  font-size: 12px;
  color: var(--app-text-color-secondary, #606266);
  white-space: pre-wrap;
  word-break: break-word;
  margin-top: 2px;
}

.test-output {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 12px;
}
</style>
