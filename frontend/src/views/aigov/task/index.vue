<template>
  <div class="p-2 app-container aigov-task-page">
    <PageHeading title="AI平台治理" subtitle="统一任务编排：状态机、输入快照、事件流与候选结果" admin module="aigov" />

    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>任务检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 170px">
              <el-option v-for="item in statusOptions" :key="item.code" :label="item.label" :value="item.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="任务类型" prop="taskType">
            <el-select v-model="queryParams.taskType" placeholder="全部" clearable style="width: 190px">
              <el-option v-for="item in typeOptions" :key="item.code" :label="item.label" :value="item.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务域" prop="projectType">
            <el-input v-model="queryParams.projectType" placeholder="如 CREATIVE" clearable style="width: 150px"
                      @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="业务对象" prop="projectId">
            <el-input v-model="queryParams.projectId" placeholder="业务ID" clearable style="width: 150px"
                      @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="任务号" prop="taskNo">
            <el-input v-model="queryParams.taskNo" placeholder="精确匹配" clearable style="width: 200px"
                      @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="traceId" prop="traceId">
            <el-input v-model="queryParams.traceId" placeholder="调用链ID" clearable style="width: 200px"
                      @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="数据等级" prop="dataLevel">
            <el-select v-model="queryParams.dataLevel" placeholder="全部" clearable style="width: 150px">
              <el-option v-for="dict in aig_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否外发" prop="externalCall">
            <el-select v-model="queryParams.externalCall" placeholder="全部" clearable style="width: 130px">
              <el-option label="外发" value="Y" />
              <el-option label="未外发" value="N" />
            </el-select>
          </el-form-item>
          <el-form-item label="创建时间">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 260px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Unified AI Task</span>
            <h3>AI任务</h3>
            <p>
              共 {{ total }} 条记录。「执行成功」只代表 Provider 返回成功，候选仍需人工选定；
              <strong>自动流程只筛除、不放行</strong>。
            </p>
          </div>
          <div class="toolbar-actions">
            <!-- 手动扫描：aigov.task.scheduler.enabled=false 时唯一的触发路径 -->
            <el-button
              v-hasPermi="['aig:task:operate']"
              type="warning"
              plain
              icon="Refresh"
              :loading="sweeping"
              @click="handleSweep"
            >
              手动触发调度
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="taskList">
        <el-table-column label="任务号" align="center" prop="taskNo" width="200" show-overflow-tooltip />
        <el-table-column label="类型" align="center" width="150" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.taskTypeLabel || scope.row.taskType }}</template>
        </el-table-column>
        <el-table-column label="业务对象" align="center" width="140" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.projectType">{{ scope.row.projectType }}</span>
            <span v-if="scope.row.projectId"> / {{ scope.row.projectId }}</span>
            <span v-if="!scope.row.projectType && !scope.row.projectId">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="160">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{ scope.row.statusLabel || scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="尝试" align="center" width="90">
          <template #default="scope">{{ scope.row.attemptNo ?? 0 }} / {{ scope.row.maxAttempt ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="数据等级" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="aig_data_level" :value="scope.row.dataLevel" />
          </template>
        </el-table-column>
        <el-table-column label="是否外发" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.externalCall === 'Y' ? 'danger' : 'success'">
              {{ scope.row.externalCall === 'Y' ? '已外发' : '未外发' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Provider" align="center" width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.providerCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="失败原因" align="center" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.errorCode" class="error-text">{{ scope.row.errorCode }}</span>
            <span v-if="scope.row.errorMessage"> {{ scope.row.errorMessage }}</span>
            <span v-if="!scope.row.errorCode && !scope.row.errorMessage">-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" width="180">
          <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
            <el-button
              v-if="scope.row.status === 'QUEUED'"
              v-hasPermi="['aig:task:operate']"
              link
              type="warning"
              :loading="executingId === scope.row.taskId"
              @click="handleExecute(scope.row)"
            >
              执行
            </el-button>
            <el-button
              v-if="canCancel(scope.row)"
              v-hasPermi="['aig:task:operate']"
              link
              type="danger"
              @click="handleCancel(scope.row)"
            >
              取消
            </el-button>
            <el-button
              v-if="canRequeue(scope.row)"
              v-hasPermi="['aig:task:operate']"
              link
              type="success"
              @click="handleRequeue(scope.row)"
            >
              重新入队
            </el-button>
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

    <!-- 任务详情：任务 + 快照 + 事件流 + 候选结果 -->
    <el-drawer v-model="detailVisible" title="任务详情" size="72%" append-to-body>
      <div v-loading="detailLoading" class="detail-body">
        <el-descriptions v-if="detail.task" :column="2" border size="small">
          <el-descriptions-item label="任务号">{{ detail.task.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.task.status)">
              {{ detail.task.statusLabel || detail.task.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.task.taskTypeLabel }}</el-descriptions-item>
          <el-descriptions-item label="能力">{{ detail.task.capabilityCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务对象">
            {{ detail.task.projectType }} / {{ detail.task.projectId }}
          </el-descriptions-item>
          <el-descriptions-item label="尝试次数">
            {{ detail.task.attemptNo ?? 0 }} / {{ detail.task.maxAttempt }}
          </el-descriptions-item>
          <el-descriptions-item label="数据等级">
            <dict-tag :options="aig_data_level" :value="detail.task.dataLevel" />
          </el-descriptions-item>
          <el-descriptions-item label="允许外发">{{ detail.task.allowExternal }}</el-descriptions-item>
          <el-descriptions-item label="Provider">{{ detail.task.providerCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="外部作业ID">{{ detail.task.providerJobId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="traceId">{{ detail.task.traceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ detail.task.latencyMs != null ? detail.task.latencyMs + ' ms' : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="策略结论">
            {{ detail.task.policyResult || '-' }} {{ detail.task.policyReason || '' }}
          </el-descriptions-item>
          <el-descriptions-item label="失败原因">
            {{ detail.task.errorCode || '-' }} {{ detail.task.errorMessage || '' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">输入快照（不可变）</el-divider>
        <template v-if="detail.snapshot">
          <el-alert
            class="detail-alert"
            type="info"
            :closable="false"
            show-icon
            :title="'内容 SHA-256：' + detail.snapshot.snapshotHash + '（执行与审核只引用快照，避免配置变更导致结果不可复现）'"
          />
          <pre class="json-block">{{ detail.snapshot.snapshotJson }}</pre>
        </template>
        <el-empty v-else description="无快照" :image-size="60" />

        <el-divider content-position="left">事件流（按序号升序）</el-divider>
        <el-table :data="detail.events" border size="small">
          <el-table-column label="#" align="center" prop="sequence" width="60" />
          <el-table-column label="事件" align="center" width="150" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.eventTypeLabel || scope.row.eventType }}</template>
          </el-table-column>
          <el-table-column label="迁移" align="center" width="230">
            <template #default="scope">
              <span v-if="scope.row.fromStatus || scope.row.toStatus">
                {{ scope.row.fromStatus || '-' }} → {{ scope.row.toStatus || '-' }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="尝试" align="center" prop="attemptNo" width="70" />
          <el-table-column label="说明" align="center" prop="detail" show-overflow-tooltip />
          <el-table-column label="时间" align="center" width="170">
            <template #default="scope">{{ parseTime(scope.row.operateTime) }}</template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">候选结果</el-divider>
        <el-table :data="detail.results" border size="small">
          <el-table-column label="类型" align="center" prop="resultType" width="110" />
          <el-table-column label="资产" align="center" prop="assetId" width="120" />
          <el-table-column label="结构校验" align="center" width="120">
            <template #default="scope">
              <el-tag :type="scope.row.validationResult === 'PASS' ? 'success' : 'danger'">
                {{ scope.row.validationResult || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="质检" align="center" prop="qaVerdict" width="120" />
          <el-table-column label="候选状态" align="center" width="160">
            <template #default="scope">{{ scope.row.candidateStatusLabel || scope.row.candidateStatus }}</template>
          </el-table-column>
          <el-table-column label="修订意见" align="center" prop="qaDetail" show-overflow-tooltip />
        </el-table>
        <el-alert
          class="detail-alert"
          type="warning"
          :closable="false"
          show-icon
          title="自动 QA 只筛除、不放行：候选的「已选定」必须由人工操作产生，系统不会代选。"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { getAigTask, cancelAigTask, executeAigTask, listAigTask, requeueAigTask, sweepAigTask } from '@/api/aigov/task';
import type { AigTaskDetailVO, AigTaskQuery, AigTaskVO } from '@/api/aigov/task/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useDateRangeQuery } from '@/hooks/form/useDateRangeQuery';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'AigTask' });

const { aig_data_level } = toRefs<any>(useDict('aig_data_level'));

/** 状态码 → 中文由后端下发；这里只为下拉提供「有哪些状态可选」，与后端枚举同口径 */
const statusOptions = [
  { code: 'DRAFT', label: '草稿' },
  { code: 'POLICY_CHECKING', label: '策略校验中' },
  { code: 'QUEUED', label: '已入队' },
  { code: 'DISPATCHED', label: '已派发' },
  { code: 'RUNNING', label: '执行中' },
  { code: 'SUCCEEDED', label: '执行成功（待复核）' },
  { code: 'REVIEW_PENDING', label: '待人工复核' },
  { code: 'APPROVED', label: '已通过' },
  { code: 'REJECTED', label: '已拒绝' },
  { code: 'FAILED', label: '失败' },
  { code: 'RETRY_WAIT', label: '等待重试' },
  { code: 'CANCEL_REQUESTED', label: '已请求取消' },
  { code: 'CANCELLED', label: '已取消' },
  { code: 'NEED_HUMAN', label: '待人工处理' }
];

const typeOptions = [
  { code: 'PLAN_GENERATION', label: '生成规划' },
  { code: 'VISUAL_DNA_ANALYSIS', label: '视觉基因分析' },
  { code: 'TEXT_GENERATION', label: '文本生成' },
  { code: 'IMAGE_GENERATION', label: '图像生成' },
  { code: 'IMAGE_EDIT', label: '图像编辑' },
  { code: 'VIDEO_GENERATION', label: '视频生成' },
  { code: 'DESIGN_SESSION_CREATE', label: '创建设计会话' },
  { code: 'VISUAL_QA', label: '视觉质量检查' },
  { code: 'AGENT_EVALUATION', label: 'Agent 评测' }
];

const taskList = ref<AigTaskVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const { dateRange, applyDateRange, resetDateRange } = useDateRangeQuery();
const sweeping = ref(false);
/** 正在执行的任务ID（用于按钮 loading，避免重复点触发两次真实调用） */
const executingId = ref<string | number | null>(null);

const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<AigTaskDetailVO>({});

const queryParams = ref<AigTaskQuery>({
  pageNum: 1,
  pageSize: 10,
  taskNo: undefined,
  taskType: undefined,
  projectType: undefined,
  status: undefined,
  dataLevel: undefined,
  externalCall: undefined,
  traceId: undefined,
  params: {}
});

/** 状态标签颜色：终态用信息色，失败/取消用危险色，成功可循 */
const statusTagType = (status?: string) => {
  switch (status) {
    case 'APPROVED':
    case 'SUCCEEDED':
      return 'success';
    case 'REJECTED':
    case 'FAILED':
    case 'CANCELLED':
      return 'danger';
    case 'CANCEL_REQUESTED':
    case 'RETRY_WAIT':
      return 'warning';
    case 'NEED_HUMAN':
    case 'REVIEW_PENDING':
      return 'warning';
    default:
      return 'info';
  }
};

/** 只有在途状态才谈得上取消（终态取消没有意义） */
const canCancel = (row: AigTaskVO) =>
  ['DISPATCHED', 'RUNNING'].includes(row.status || '') === true;

/** 待人工处理的任务可以由人重新入队；等待重试交给调度器，不在这里抢 */
const canRequeue = (row: AigTaskVO) => row.status === 'NEED_HUMAN';

const getList = async () => {
  await withLoading(async () => {
    const res = await listAigTask(applyDateRange(queryParams.value));
    taskList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  resetExtras: () => resetDateRange(),
  afterReset: () => handleQuery()
});

const handleDetail = async (row: AigTaskVO) => {
  detailVisible.value = true;
  detailLoading.value = true;
  detail.value = {};
  try {
    const res = await getAigTask(row.taskId!);
    detail.value = res.data || {};
  } finally {
    detailLoading.value = false;
  }
};

const handleCancel = async (row: AigTaskVO) => {
  let reason = '';
  try {
    const result = await modal.prompt('请求取消的原因（写入事件流，便于事后复盘）');
    reason = result?.value ?? '';
  } catch {
    return;
  }
  await cancelAigTask(row.taskId!, row.version!, reason);
  modal.msgSuccess('已请求取消：等 Provider 确认后才会落为「已取消」');
  getList();
};

const handleRequeue = async (row: AigTaskVO) => {
  try {
    await modal.confirm('即将把该任务重新入队。若输入或配置未修好，它会再次失败并消耗预算。确定继续？');
  } catch {
    return;
  }
  await requeueAigTask(row.taskId!, row.version!, '人工重新入队');
  modal.msgSuccess('已重新入队');
  getList();
};

/**
 * 手动执行一次任务（联调/运维用）。
 *
 * 正常路径是业务域建任务后直接调用执行器——前端调这个只是为了联调期手动触发与运维重跑。
 * 提示词必须手填：治理层不知道业务快照的字段含义，无法从快照推导出真实请求。
 */
const handleExecute = async (row: AigTaskVO) => {
  let prompt = '';
  try {
    const result = await modal.prompt('本次执行的提示词（写入调用请求；快照仍作为不可变记录）');
    prompt = result?.value ?? '';
  } catch {
    return;
  }
  if (!prompt) {
    modal.msgWarning('提示词不能为空');
    return;
  }
  executingId.value = row.taskId!;
  try {
    const res = await executeAigTask(row.taskId!, prompt);
    const data = res.data;
    if (data?.success) {
      modal.msgSuccess(
        `执行成功：模型 ${data.modelKey ?? '-'}，调用器 ${data.invoker ?? '-'}，` +
          `外发 ${data.externalCall ? '是' : '否'}，耗时 ${data.latencyMs ?? '-'} ms，traceId ${data.traceId ?? '-'}`
      );
      // 输出不落库（落资产是业务域的事），联调时把预览放进详情抽屉
      if (data.output) {
        detail.value = { task: { ...row, status: data.status } };
        detailVisible.value = true;
      }
    } else {
      modal.msgError(
        `执行失败：${data?.errorCode ?? 'UNKNOWN'} ${data?.reason ?? ''}（任务已落到 ${data?.status ?? '-'}）`
      );
    }
    getList();
  } finally {
    executingId.value = null;
  }
};

const handleSweep = async () => {  sweeping.value = true;
  try {
    const res = await sweepAigTask();
    const data = res.data || {};
    const failures = data.failures?.length
      ? `；另有 ${data.failures.length} 条处理失败（详见服务日志）`
      : '';
    modal.msgSuccess(
      `扫描完成：重排 ${data.retried ?? 0} 条（候选 ${data.retryCandidate ?? 0}），` +
        `超时 ${data.timedOut ?? 0} 条（候选 ${data.timeoutCandidate ?? 0}），跳过 ${data.skipped ?? 0} 条` +
        failures
    );
    getList();
  } finally {
    sweeping.value = false;
  }
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.detail-body {
  padding: 0 4px;
}

.detail-alert {
  margin: 8px 0;
}

.json-block {
  max-height: 220px;
  overflow: auto;
  padding: 8px 10px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.error-text {
  color: var(--el-color-danger);
}
</style>
