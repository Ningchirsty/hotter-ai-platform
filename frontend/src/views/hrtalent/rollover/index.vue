<template>
  <div class="p-2 app-container hrtalent-rollover-page">
    <PageHeading title="招聘管理" subtitle="月末结转预览、幂等执行、批次明细与安全重试" module="hrtalent" />

    <!-- 结转预览 -->
    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Rollover Preview</span>
            <h3>结转预览</h3>
            <p>
              选择目标月份后列出待结转过任务与人数。只有「未取消、未完成、剩余人数 &gt; 0、允许结转、且尚未对该月份生成过结转任务」
              的任务才会被结转；重复执行不会重复生成（唯一索引保证幂等）。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-date-picker
              v-model="previewMonth"
              type="month"
              value-format="YYYY-MM"
              placeholder="选择目标月份"
              clearable
              style="width: 170px"
            />
            <el-button
              v-hasPermi="['recruit:rollover:preview']"
              type="primary"
              icon="Search"
              :loading="previewLoading"
              @click="handlePreview"
            >
              预览
            </el-button>
            <el-button
              v-hasPermi="['recruit:rollover:execute']"
              type="success"
              icon="Right"
              :disabled="!previewItems.length"
              :loading="executeLoading"
              @click="handleExecute"
            >
              执行结转
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="executeResult"
        class="dialog-alert"
        type="success"
        :closable="true"
        show-icon
        :title="executeResultTitle"
        @close="executeResult = undefined"
      />

      <el-table
        v-loading="previewLoading"
        border
        class="data-table"
        :data="previewItems"
        @selection-change="handlePreviewSelection"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="来源任务编号" align="center" prop="itemNo" width="180" show-overflow-tooltip />
        <el-table-column label="计划月份" align="center" prop="planMonth" width="110" />
        <el-table-column label="公司" align="center" width="150" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.companyName || scope.row.companyDeptId || '-' }}</template>
        </el-table-column>
        <el-table-column label="用工部门" align="center" width="140" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.useDeptName || scope.row.useDeptId || '-' }}</template>
        </el-table-column>
        <el-table-column label="岗位" align="center" prop="jobName" width="150" show-overflow-tooltip />
        <el-table-column label="来源类型" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="recruit_plan_source_type" :value="scope.row.sourceType" />
          </template>
        </el-table-column>
        <el-table-column label="计划人数" align="center" prop="planQty" width="90" />
        <el-table-column label="已到岗" align="center" prop="creditedArrivalQty" width="90" />
        <el-table-column label="待结转人数" align="center" width="110">
          <template #default="scope">
            <strong>{{ scope.row.remainingQty ?? 0 }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="负责人" align="center" width="110" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.ownerName || scope.row.ownerId || '-' }}</template>
        </el-table-column>
      </el-table>

      <div v-if="previewItems.length" class="preview-summary">
        共 {{ previewItems.length }} 条待结转任务，待结转人数合计
        <strong>{{ previewTotalQty }}</strong> 人；已选
        <strong>{{ selectedPreview.length }}</strong> 条（未选择时执行全部待结转任务）。
      </div>
    </el-card>

    <!-- 批次列表 -->
    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Rollover Batches</span>
            <h3>结转批次</h3>
            <p>共 {{ total }} 条批次；单条失败只记录原因并允许安全重试，不会整批回滚已成功条目。</p>
          </div>
          <div class="toolbar-actions">
            <el-button icon="Refresh" plain @click="getList">刷新</el-button>
          </div>
        </div>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="批次号" prop="batchNo">
            <el-input v-model="queryParams.batchNo" placeholder="请输入批次号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="来源月份" prop="sourceMonth">
            <el-date-picker
              v-model="queryParams.sourceMonth"
              type="month"
              value-format="YYYY-MM"
              placeholder="来源月份"
              clearable
              style="width: 150px"
            />
          </el-form-item>
          <el-form-item label="目标月份" prop="targetMonth">
            <el-date-picker
              v-model="queryParams.targetMonth"
              type="month"
              value-format="YYYY-MM"
              placeholder="目标月份"
              clearable
              style="width: 150px"
            />
          </el-form-item>
          <el-form-item label="执行结果" prop="result">
            <el-select v-model="queryParams.result" placeholder="请选择结果" clearable style="width: 140px">
              <el-option v-for="item in ROLLOVER_RESULTS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="batchList">
        <el-table-column label="批次号" align="center" prop="batchNo" width="200" show-overflow-tooltip />
        <el-table-column label="来源月份" align="center" prop="sourceMonth" width="110">
          <template #default="scope">{{ scope.row.sourceMonth || '-' }}</template>
        </el-table-column>
        <el-table-column label="目标月份" align="center" prop="targetMonth" width="110">
          <template #default="scope">{{ scope.row.targetMonth || '-' }}</template>
        </el-table-column>
        <el-table-column label="结转条数" align="center" width="100">
          <template #default="scope">{{ scope.row.totalCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="成功" align="center" width="80">
          <template #default="scope">{{ scope.row.successCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="失败" align="center" width="80">
          <template #default="scope">
            <span :class="{ 'text-danger': (scope.row.failedCount ?? 0) > 0 }">{{ scope.row.failedCount ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结转人数" align="center" width="100">
          <template #default="scope">{{ scope.row.carryoverQty ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="执行结果" align="center" width="110">
          <template #default="scope">
            <el-tag :type="resultTagType(scope.row.result)">{{ resultLabel(scope.row.result) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.executeTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="批次明细" placement="top">
              <el-button
                v-hasPermi="['recruit:rollover:detail']"
                link
                type="primary"
                icon="Search"
                @click="openBatchDetail(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="重试失败条目" placement="top">
              <el-button
                v-hasPermi="['recruit:rollover:retry']"
                link
                type="warning"
                icon="RefreshRight"
                @click="handleRetry(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <!-- 批次明细 -->
    <el-dialog v-model="detail.visible" :title="detailTitle" width="940px" append-to-body>
      <el-descriptions :column="3" border size="small" class="detail-panel">
        <el-descriptions-item label="批次号">{{ detail.batch.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次ID">{{ detail.batch.batchId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标月份">{{ detail.batch.targetMonth || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源月份">{{ detail.batch.sourceMonth || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结转人数">{{ detail.batch.carryoverQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行时间">{{ parseTime(detail.batch.executeTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="成功条数">{{ detail.batch.successCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="失败条数">{{ detail.batch.failedCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行结果">
          <el-tag :type="resultTagType(detail.batch.result)">{{ resultLabel(detail.batch.result) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-table v-loading="detail.loading" border size="small" :data="detail.records" class="detail-table">
        <el-table-column label="来源任务" align="center" width="170" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.sourceItemNo || scope.row.sourceItemId || '-' }}</template>
        </el-table-column>
        <el-table-column label="目标任务" align="center" width="170" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.targetItemNo || scope.row.targetItemId || '-' }}</template>
        </el-table-column>
        <el-table-column label="来源月份" align="center" prop="sourceMonth" width="100" />
        <el-table-column label="目标月份" align="center" prop="targetMonth" width="100" />
        <el-table-column label="结转人数" align="center" prop="carryoverQty" width="90" />
        <el-table-column label="结果" align="center" width="100">
          <template #default="scope">
            <el-tag :type="resultTagType(scope.row.result)">{{ resultLabel(scope.row.result) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重试次数" align="center" prop="retryCount" width="90" />
        <el-table-column label="失败原因" align="center" prop="failureReason" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.failureReason || '-' }}</template>
        </el-table-column>
        <el-table-column label="执行时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.executeTime) || '-' }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detail.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  executeRollover,
  getRolloverBatch,
  listRollover,
  previewRollover,
  retryRollover
} from '@/api/hrtalent/rollover';
import type {
  HrRolloverBatchDetailVO,
  HrRolloverBatchVO,
  HrRolloverExecuteVO,
  HrRolloverQuery,
  HrRolloverRecordVO
} from '@/api/hrtalent/rollover/types';
import type { HrPlanItemVO } from '@/api/hrtalent/plan/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'HrRollover' });

const { recruit_plan_source_type } = toRefs<any>(useDict('recruit_plan_source_type'));

/**
 * 结转结果编码本地映射：
 * hr_recruit_plan_rollover.result 存 success/failed/skipped 等稳定编码，
 * P1 字典中没有对应字典类型（无 recruit_rollover_result），故暂时本地渲染。
 */
const ROLLOVER_RESULTS = [
  { value: 'success', label: '成功' },
  { value: 'failed', label: '失败' },
  { value: 'skipped', label: '已跳过' },
  { value: 'partial', label: '部分成功' }
];

/** 结果编码 → 标签类型 */
const resultTagType = (result?: string): 'primary' | 'success' | 'info' | 'warning' | 'danger' => {
  switch (result) {
    case 'success':
      return 'success';
    case 'failed':
      return 'danger';
    case 'partial':
      return 'warning';
    case 'skipped':
      return 'info';
    default:
      return 'info';
  }
};

/** 结果编码 → 中文 */
const resultLabel = (result?: string) =>
  ROLLOVER_RESULTS.find(item => item.value === result)?.label || result || '-';

// ------------------------------------------------------------------ 结转预览

const previewMonth = ref<string | undefined>(undefined);
const previewLoading = ref(false);
const executeLoading = ref(false);
const previewItems = ref<HrPlanItemVO[]>([]);
const selectedPreview = ref<HrPlanItemVO[]>([]);
const executeResult = ref<HrRolloverExecuteVO | undefined>(undefined);

/** 待结转人数合计 */
const previewTotalQty = computed(() =>
  previewItems.value.reduce((sum, item) => sum + (item.remainingQty || 0), 0)
);

/** 执行结果提示文案 */
const executeResultTitle = computed(() => {
  const result = executeResult.value;
  if (!result) {
    return '';
  }
  return `批次 ${result.batchNo || '-'}：应结转 ${result.totalCount ?? '-'} 条，成功 ${result.successCount ?? '-'} 条，失败 ${
    result.failedCount ?? '-'
  } 条，结转人数 ${result.carryoverQty ?? '-'} 人。`;
});

/** 预览：列出目标月份待结转任务 */
const handlePreview = async () => {
  if (!previewMonth.value) {
    modal.msgWarning('请先选择目标月份');
    return;
  }
  previewLoading.value = true;
  executeResult.value = undefined;
  try {
    const res = await previewRollover({ targetMonth: previewMonth.value });
    const payload = res.data as unknown;
    // 兼容「直接返回数组」与「返回 { items, totalQty }」两种出参
    previewItems.value = Array.isArray(payload)
      ? (payload as HrPlanItemVO[])
      : ((payload as { items?: HrPlanItemVO[] })?.items ?? []);
  } catch {
    previewItems.value = [];
  } finally {
    previewLoading.value = false;
  }
};

const handlePreviewSelection = (selection: HrPlanItemVO[]) => {
  selectedPreview.value = selection;
};

/** 执行结转（幂等）：默认结转全部待结转任务，勾选后只结转选中项 */
const handleExecute = async () => {
  if (!previewMonth.value) {
    modal.msgWarning('请先选择目标月份');
    return;
  }
  const scope =
    selectedPreview.value.length > 0
      ? `选中的 ${selectedPreview.value.length} 条任务`
      : `全部 ${previewItems.value.length} 条待结转任务`;
  try {
    await modal.confirm(`将对目标月份「${previewMonth.value}」执行结转：${scope}。重复执行不会重复生成任务，是否继续？`);
  } catch {
    return;
  }
  executeLoading.value = true;
  try {
    const res = await executeRollover({
      targetMonth: previewMonth.value,
      sourceItemIds: selectedPreview.value.map(item => item.itemId!).filter(Boolean)
    });
    executeResult.value = res.data || {};
    modal.msgSuccess('结转执行完成');
    await handlePreview();
    await getList();
  } finally {
    executeLoading.value = false;
  }
};

// ------------------------------------------------------------------ 批次列表

const batchList = ref<HrRolloverBatchVO[]>([]);
const { loading, withLoading } = useLoading(true);
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();

const queryParams = ref<HrRolloverQuery>({
  pageNum: 1,
  pageSize: 10,
  batchNo: undefined,
  sourceMonth: undefined,
  targetMonth: undefined,
  result: undefined,
  params: {}
});

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 查询批次列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listRollover(queryParams.value);
    batchList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

// ------------------------------------------------------------------ 批次明细

const detail = reactive<{
  visible: boolean;
  loading: boolean;
  batch: Partial<HrRolloverBatchDetailVO>;
  records: HrRolloverRecordVO[];
}>({
  visible: false,
  loading: false,
  batch: {},
  records: []
});

const detailTitle = computed(() => (detail.batch.batchNo ? `结转批次明细 · ${detail.batch.batchNo}` : '结转批次明细'));

/** 打开批次明细 */
const openBatchDetail = async (row: HrRolloverBatchVO) => {
  detail.batch = row;
  detail.records = [];
  detail.visible = true;
  detail.loading = true;
  try {
    const res = await getRolloverBatch(row.batchNo!);
    const data = res.data || {};
    detail.batch = data;
    detail.records = data.records || data.items || [];
  } catch {
    detail.records = [];
  } finally {
    detail.loading = false;
  }
};

/** 重试批次中失败/跳过的条目 */
const handleRetry = async (row: HrRolloverBatchVO) => {
  try {
    await modal.confirm(`是否重试批次「${row.batchNo}」中失败或跳过的结转条目？已成功条目不会被回滚。`);
  } catch {
    return;
  }
  const res = await retryRollover(row.batchNo!);
  executeResult.value = res.data || undefined;
  modal.msgSuccess('重试已执行');
  await getList();
  if (detail.visible) {
    await openBatchDetail(row);
  }
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.dialog-alert {
  margin-bottom: 12px;
}

.preview-summary {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-muted);
}

.detail-panel {
  margin-bottom: 12px;
}

.detail-table {
  margin-top: 4px;
}

.text-danger {
  color: var(--el-color-danger);
}
</style>
