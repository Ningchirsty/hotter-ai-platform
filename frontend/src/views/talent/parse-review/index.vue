<template>
  <div class="p-2 app-container talent-parse-page">
    <PageHeading title="人才管理" subtitle="档案、附件与业务记录，在同一个工作空间有序连接" module="talent" />
    <el-card shadow="hover" class="search-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Parse Review</span>
            <h3>简历解析复核</h3>
            <p>解析结果仅展示字段原值、置信度与人工确认值；不展示简历正文与存储地址。</p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['talent:parse:retry']"
              type="warning"
              plain
              icon="RefreshRight"
              :disabled="!task || task.status === 'PROCESSING'"
              @click="handleRetry"
            >
              重试解析
            </el-button>
            <el-button
              v-hasPermi="['talent:parse:confirm']"
              type="primary"
              plain
              icon="Select"
              :disabled="!fields.length"
              :loading="saving"
              @click="handleConfirm"
            >
              保存确认
            </el-button>
          </div>
        </div>
      </template>
      <el-form :inline="true" class="query-form" @submit.prevent>
        <el-form-item label="解析任务ID">
          <el-input v-model="taskIdInput" placeholder="请输入解析任务ID" clearable style="width: 260px" @keyup.enter="loadTask" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadTask">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="table-heading">
          <h3>任务信息</h3>
        </div>
      </template>
      <el-descriptions v-if="task" v-loading="loading" :column="3" border>
        <el-descriptions-item label="任务ID">{{ task.taskId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="人才ID">{{ task.talentId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="附件">{{ task.attachmentName || task.attachmentId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(task.status)">{{ task.statusLabel || parseStatusLabel(task.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="解析服务版本">{{ task.parserVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ task.retryCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ parseTime(task.startTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ parseTime(task.finishTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误摘要">{{ task.errorSummary || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="请输入解析任务ID后查询" />
    </el-card>

    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="table-heading">
          <h3>字段原值 / 置信度 / 确认值</h3>
        </div>
      </template>
      <el-table v-loading="loading" border class="data-table" :data="fields">
        <el-table-column label="字段名" align="center" prop="fieldName" width="180" />
        <el-table-column label="解析原值" align="center" prop="parsedValue" show-overflow-tooltip />
        <el-table-column label="置信度" align="center" width="110">
          <template #default="scope">
            <el-tag :type="confidenceTagType(scope.row.confidence)">
              {{ formatConfidence(scope.row.confidence) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认值" align="center" width="240">
          <template #default="scope">
            <el-input v-model="scope.row.confirmedValue" placeholder="请输入确认值" clearable />
          </template>
        </el-table-column>
        <el-table-column label="确认状态" align="center" width="150">
          <template #default="scope">
            <el-select v-model="scope.row.confirmStatus" placeholder="请选择确认状态">
              <el-option v-for="item in confirmStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="确认人" align="center" prop="confirmByName" width="120" />
        <el-table-column label="确认时间" align="center" width="180">
          <template #default="scope">{{ parseTime(scope.row.confirmTime) || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router';
import type { ParseFieldVO, ParseTaskVO } from '@/api/talent/parse/types';
import { confirmParseFields, getParseTask, retryParseTask } from '@/api/talent/parse';
import modal from '@/plugins/modal';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'TalentParseReview' });

const route = useRoute();

const parseStatusOptions = [
  { value: 'DISABLED', label: '未启用' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' }
];
const parseStatusLabel = (value?: string) => parseStatusOptions.find(item => item.value === value)?.label || value || '-';
const statusTagType = (value?: string) => {
  if (value === 'SUCCESS') return 'success';
  if (value === 'FAILED') return 'danger';
  if (value === 'PROCESSING') return 'warning';
  return 'info';
};

const confirmStatusOptions = [
  { value: '0', label: '待确认' },
  { value: '1', label: '已确认' },
  { value: '2', label: '已忽略' }
];

const taskIdInput = ref<string>('');
const task = ref<ParseTaskVO | null>(null);
const fields = ref<ParseFieldVO[]>([]);
const loading = ref(false);
const saving = ref(false);

const formatConfidence = (value?: number) => (value === undefined || value === null ? '-' : `${(Number(value) * 100).toFixed(1)}%`);
const confidenceTagType = (value?: number) => {
  const num = Number(value ?? 0);
  if (num >= 0.9) return 'success';
  if (num >= 0.6) return 'warning';
  return 'danger';
};

/** 查询解析任务（含字段清单） */
const loadTask = async () => {
  if (!taskIdInput.value) {
    modal.msgWarning('请输入解析任务ID');
    return;
  }
  loading.value = true;
  try {
    const res = await getParseTask(taskIdInput.value);
    task.value = res.data || null;
    fields.value = (res.data?.fields || []).map(item => ({
      ...item,
      confirmedValue: item.confirmedValue ?? item.parsedValue ?? '',
      confirmStatus: item.confirmStatus ?? '0'
    }));
  } finally {
    loading.value = false;
  }
};

/** 保存字段人工确认结果 */
const handleConfirm = async () => {
  if (!task.value?.taskId) return;
  saving.value = true;
  try {
    await confirmParseFields({
      taskId: task.value.taskId,
      fields: fields.value
        .filter(item => item.fieldId !== undefined && item.fieldId !== null)
        .map(item => ({
          fieldId: item.fieldId as string | number,
          confirmedValue: item.confirmedValue,
          confirmStatus: item.confirmStatus
        }))
    });
    modal.msgSuccess('确认结果已保存');
    await loadTask();
  } finally {
    saving.value = false;
  }
};

/** 重试解析 */
const handleRetry = async () => {
  if (!task.value?.taskId) return;
  await modal.confirm('是否确认重新提交该解析任务？');
  await retryParseTask(task.value.taskId);
  modal.msgSuccess('已提交重试');
  await loadTask();
};

onMounted(() => {
  const queryTaskId = route.query.taskId;
  if (queryTaskId) {
    taskIdInput.value = String(queryTaskId);
    loadTask();
  }
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;
</style>
