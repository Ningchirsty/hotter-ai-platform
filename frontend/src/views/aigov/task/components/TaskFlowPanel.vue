<template>
  <el-dialog v-model="visible" title="流程节点与人工确认" width="760px" append-to-body>
    <el-alert type="info" :closable="false" class="mb-2" show-icon :title="summary" />
    <el-alert
      v-if="awaiting.length"
      type="warning"
      :closable="false"
      class="mb-2"
      show-icon
      :title="`有 ${awaiting.length} 个节点在等待人工确认：确认只是「批准了这个节点」，不会自动重跑任务。`"
    />

    <el-alert
      v-if="conditionHint"
      type="info"
      :closable="false"
      class="mb-2"
      show-icon
      :title="conditionHint"
    />

    <el-table v-loading="loading" border size="small" :data="nodes">
      <el-table-column label="#" align="center" width="55">
        <template #default="scope">{{ scope.row.nodeIndex }}</template>
      </el-table-column>
      <el-table-column label="节点类型" align="center" width="120">
        <template #default="scope">{{ flowNodeTypeLabel(scope.row.nodeType) }}</template>
      </el-table-column>
      <el-table-column label="引用" align="center" prop="nodeRef" width="130" show-overflow-tooltip />
      <el-table-column label="状态" align="center" width="170">
        <template #default="scope">
          <el-tag :type="flowNodeTagType(scope.row)">{{ flowNodeStatusText(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="批准引用" align="center" prop="approvalRef" min-width="140" show-overflow-tooltip />
      <el-table-column label="结果引用" align="center" prop="outputRef" min-width="140" show-overflow-tooltip />
      <el-table-column label="失败原因" align="center" min-width="180" show-overflow-tooltip>
        <template #default="scope">
          <span v-if="scope.row.errorCode || scope.row.errorMessage">
            <el-tag v-if="scope.row.errorCode" type="danger" size="small">{{ scope.row.errorCode }}</el-tag>
            {{ scope.row.errorMessage || '' }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">记录人工确认</el-divider>
    <p class="tip">
      批准写在任务步骤账本上（状态 + 批准引用），<b>不覆盖已经批过的那次凭据</b>。
      完整操作序列：这里确认 → 回任务列表点「重新入队」让流程继续。
    </p>
    <el-form label-width="90px">
      <el-form-item label="节点">
        <el-select v-model="confirmIndex" placeholder="选择等待确认的节点" style="width: 100%">
          <el-option
            v-for="item in awaiting"
            :key="item.nodeIndex"
            :label="`#${item.nodeIndex} ${flowNodeTypeLabel(item.nodeType)}${item.nodeRef ? ' · ' + item.nodeRef : ''}`"
            :value="item.nodeIndex"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="批准引用">
        <el-input
          v-model="approvalRef"
          placeholder="审批单号 / 工单号（必填：没有凭据的确认事后无法追溯）"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="saving" @click="load">刷新</el-button>
      <el-button
        v-hasPermi="['aig:task:operate']"
        type="primary"
        :disabled="!awaiting.length"
        :loading="saving"
        @click="doConfirm"
      >
        记录确认
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { confirmAigTaskFlowNode, listAigTaskFlowNodes } from '@/api/aigov/task';
import type { AigTaskFlowNodeVO } from '@/api/aigov/task/types';
import modal from '@/plugins/modal';
import {
  awaitingNodes,
  conditionSourceHint,
  confirmValidation,
  flowNodeStatusText,
  flowNodeTagType,
  flowNodeTypeLabel,
  flowSummary
} from './flow';

defineOptions({ name: 'AigTaskFlowPanel' });

const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const taskId = ref<string | number>();
const nodes = ref<AigTaskFlowNodeVO[]>([]);
const confirmIndex = ref<number>();
const approvalRef = ref('');

const awaiting = computed(() => awaitingNodes(nodes.value));
const summary = computed(() => flowSummary(nodes.value));
const conditionHint = computed(() => conditionSourceHint(nodes.value));

const load = async () => {
  if (taskId.value === undefined || taskId.value === null) {
    return;
  }
  loading.value = true;
  try {
    const res = await listAigTaskFlowNodes(taskId.value);
    nodes.value = res.data ?? [];
    // 只剩一个待确认节点时直接选中它：多一步选择只是让人多一次点错的机会
    confirmIndex.value = awaiting.value.length === 1 ? awaiting.value[0].nodeIndex : undefined;
    approvalRef.value = '';
  } catch (e: any) {
    modal.alertError(e?.message || '读取流程节点失败');
  } finally {
    loading.value = false;
  }
};

/** 供列表页调用：打开某个任务的流程面板 */
const open = (id: string | number) => {
  taskId.value = id;
  nodes.value = [];
  confirmIndex.value = undefined;
  approvalRef.value = '';
  visible.value = true;
  load();
};

const doConfirm = async () => {
  const error = confirmValidation(nodes.value, confirmIndex.value, approvalRef.value);
  if (error) {
    modal.msgWarning(error);
    return;
  }
  const confirmed = await modal
    .confirm('确认后该节点记为「已批准」，并留下批准引用。是否继续执行由你在任务列表点「重新入队」决定。确定记录？')
    .catch(() => false);
  if (!confirmed) {
    return;
  }
  saving.value = true;
  try {
    await confirmAigTaskFlowNode(taskId.value!, confirmIndex.value!, approvalRef.value.trim());
    modal.msgSuccess('已记录人工确认；流程继续请回列表点「重新入队」');
    await load();
  } catch (e: any) {
    modal.alertError(e?.message || '记录确认失败');
  } finally {
    saving.value = false;
  }
};

defineExpose({ open });
</script>

<style scoped>
.tip {
  margin: 0 0 10px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.mb-2 {
  margin-bottom: 10px;
}
</style>
