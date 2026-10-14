<template>
  <div class="p-2">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span class="title">运营指标</span>
          <el-button size="small" @click="loadMetrics">刷新</el-button>
        </div>
      </template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="完成任务数">{{ metrics.completedTasks ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="累计成果">{{ metrics.confirmedResults ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="结果采纳率">{{ rateText(metrics.adoptionRate) }}</el-descriptions-item>
        <el-descriptions-item label="首次通过率">{{ rateText(metrics.firstPassRate) }}</el-descriptions-item>
        <el-descriptions-item label="返工率">{{ rateText(metrics.reworkRate) }}</el-descriptions-item>
        <el-descriptions-item label="引用有效率">{{ rateText(metrics.citationValidRate) }}</el-descriptions-item>
        <el-descriptions-item label="策略阻断">{{ metrics.policyBlocked ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="模型成本">{{ metrics.modelCost ?? '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        class="mt-2"
        type="info"
        :closable="false"
        title="口径：累计成果 = 经确认的结果记录（不是模型输出条数）；引用有效率与模型成本暂无可聚合数据源，如实为 0。"
      />
    </el-card>

    <el-card shadow="never" class="mt-2">
      <template #header>
        <div class="header">
          <span class="title">反馈问题队列</span>
          <div>
            <el-select v-model="verdict" style="width: 160px" @change="loadQueue">
              <el-option
                v-for="item in QUEUE_FILTERS"
                :key="item.code"
                :label="item.label"
                :value="item.code"
              />
            </el-select>
            <el-select v-model="actionCode" clearable style="width: 150px" class="ml-2" @change="loadQueue">
              <el-option
                v-for="item in QUEUE_ACTION_FILTERS"
                :key="item.code"
                :label="item.label"
                :value="item.code"
              />
            </el-select>
            <el-select v-model="reasonCode" clearable style="width: 150px" class="ml-2" @change="loadQueue">
              <el-option
                v-for="item in QUEUE_REASON_FILTERS"
                :key="item.code"
                :label="item.label"
                :value="item.code"
              />
            </el-select>
            <el-button size="small" class="ml-2" @click="loadQueue">刷新</el-button>
          </div>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon class="mb-2" :title="queueActionHint(actionCode)" />
      <el-table :data="rows" border>
        <el-table-column label="反馈ID" prop="feedbackId" width="180" />
        <el-table-column label="任务" prop="taskId" width="180" />
        <el-table-column label="结果" prop="resultId" width="180" />
        <el-table-column label="Agent 版本" prop="agentVersionId" width="180" show-overflow-tooltip />
        <el-table-column label="知识版本" prop="knowledgeVersionId" width="180" show-overflow-tooltip />
        <el-table-column label="原因" width="120">
          <template #default="{ row }">{{ reasonLabel(row.reasonCode) }}</template>
        </el-table-column>
        <el-table-column label="结论" width="130">
          <template #default="{ row }">
            <el-tag :type="verdictTagType(row.verdict)">{{ verdictLabel(row.verdict) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="动作" width="120">
          <template #default="{ row }">{{ row.actionCode ? actionLabel(row.actionCode) : '—' }}</template>
        </el-table-column>
        <el-table-column label="处理人" prop="handledBy" width="160" show-overflow-tooltip />
        <el-table-column label="处理时间" prop="handledAt" width="180" />
        <el-table-column label="提交时间" prop="createTime" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              v-hasPermi="['aig:feedback:handle']"
              link
              type="primary"
              :disabled="row.verdict !== 'PENDING'"
              @click="openHandle(row)"
            >
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="handleVisible" title="处理反馈" width="480px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="结论" required>
          <el-radio-group v-model="handleForm.verdict">
            <el-radio value="CONFIRMED">确认问题</el-radio>
            <el-radio value="REJECTED">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="handleForm.verdict === 'CONFIRMED'" label="动作" required>
          <el-select v-model="handleForm.actionCode" style="width: 100%">
            <el-option
              v-for="item in ACTION_OPTIONS"
              :key="item.code"
              :label="item.label"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="handleForm.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="handleForm.verdict === 'CONFIRMED'"
        type="warning"
        :closable="false"
        title="动作只是把问题送进改进流程（补知识/改规则/加入用例），不会直接改线上版本。"
      />
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doHandle">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { getOperationsMetrics, handleFeedback, listFeedbackQueue } from '@/api/aigov/feedback';
import type {
  AigFeedbackHandleForm,
  AigFeedbackVO,
  AigOperationsMetricsVO
} from '@/api/aigov/feedback/types';
import modal from '@/plugins/modal';
import {
  ACTION_OPTIONS,
  QUEUE_ACTION_FILTERS,
  QUEUE_FILTERS,
  QUEUE_REASON_FILTERS,
  actionLabel,
  handleValidation,
  queueActionHint,
  rateText,
  reasonLabel,
  verdictLabel,
  verdictTagType
} from './presentation';

defineOptions({ name: 'AigFeedback' });

const metrics = ref<AigOperationsMetricsVO>({});
const rows = ref<AigFeedbackVO[]>([]);
const verdict = ref('');
const actionCode = ref('');
const reasonCode = ref('');
const handleVisible = ref(false);
const saving = ref(false);
const handling = ref<AigFeedbackVO>();
const handleForm = ref<AigFeedbackHandleForm>({ verdict: 'CONFIRMED' });

const loadMetrics = async () => {
  const res = await getOperationsMetrics();
  metrics.value = res.data || {};
};

const loadQueue = async () => {
  const res = await listFeedbackQueue(verdict.value, actionCode.value, reasonCode.value);
  rows.value = res.data || [];
};

const openHandle = (row: AigFeedbackVO) => {
  handling.value = row;
  handleForm.value = { verdict: 'CONFIRMED' };
  handleVisible.value = true;
};

const doHandle = async () => {
  // 前端先按后端同一口径校验一次：避免"界面允许提交、后端拒绝"
  const error = handleValidation(handleForm.value);
  if (error) {
    modal.msgWarning(error);
    return;
  }
  if (!handling.value?.feedbackId) {
    return;
  }
  saving.value = true;
  try {
    await handleFeedback(handling.value.feedbackId, handleForm.value);
    modal.msgSuccess('已处理');
    handleVisible.value = false;
    await loadQueue();
  } catch (e: any) {
    modal.alertError(e?.message || '处理失败');
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  loadMetrics();
  loadQueue();
});
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-weight: 600;
}

.mt-2 {
  margin-top: 12px;
}

.ml-2 {
  margin-left: 8px;
}
</style>
