<template>
  <div class="p-2 app-container content-workPackage-page">
    <PageHeading
      title="内容生产协同"
      subtitle="设计开工包：已确认事实 + 缺口与替代 + 允许的 AI 动作 + 不可修改项"
      module="content"
    />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>开工包检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="任务号" prop="queryTaskNo">
            <el-input
              v-model="queryParams.queryTaskNo"
              placeholder="请输入任务号"
              clearable
              style="width: 180px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="任务名称" prop="queryTaskName">
            <el-input
              v-model="queryParams.queryTaskName"
              placeholder="请输入任务名称"
              clearable
              style="width: 200px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="交付类型" prop="queryDeliverableType">
            <el-select
              v-model="queryParams.queryDeliverableType"
              placeholder="请选择交付类型"
              clearable
              style="width: 170px"
            >
              <el-option
                v-for="dict in cp_deliverable_type"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
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
            <span class="panel-kicker">Work Packages</span>
            <h3>按任务查看开工包</h3>
            <p>共 {{ total }} 个任务；开工包按任务生成，签发时冻结事实版本。选择任务后可查看全文并签发。</p>
          </div>
          <div class="toolbar-actions">
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="taskList" highlight-current-row>
        <el-table-column label="任务号" align="center" prop="taskNo" width="170" show-overflow-tooltip />
        <el-table-column label="任务名称" align="center" prop="taskName" min-width="180" show-overflow-tooltip />
        <el-table-column label="交付类型" align="center" width="130">
          <template #default="scope">
            <dict-tag :options="cp_deliverable_type" :value="scope.row.deliverableType" />
          </template>
        </el-table-column>
        <el-table-column label="任务状态" align="center" width="130">
          <template #default="scope">
            <dict-tag :options="cp_task_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="负责人" align="center" prop="ownerName" width="110" show-overflow-tooltip />
        <el-table-column label="截止时间" align="center" prop="deadline" width="170">
          <template #default="scope">
            <span>{{ parseTime(scope.row.deadline) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="待处理卡" align="center" prop="pendingCardCount" width="100" />
        <el-table-column label="阻断卡" align="center" prop="blockingCardCount" width="90" />
        <el-table-column label="操作" width="230" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" @click="handleLoad(scope.row)">查看开工包</el-button>
            <el-button
              v-hasPermi="['content:package:generate']"
              link
              type="primary"
              @click="handleGenerate(scope.row)"
            >
              生成开工包
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

    <!-- 开工包全文 -->
    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Package Detail</span>
            <h3>{{ currentTask ? `开工包 · ${currentTask.taskNo || ''}` : '开工包详情' }}</h3>
            <p v-if="!currentTask">请在上方列表中选择一个任务以查看其开工包。</p>
            <p v-else-if="!workPackage">该任务尚未生成开工包；可点击“生成开工包”生成草稿。</p>
            <p v-else>
              包状态：<b>{{ workPackage.status === 'ISSUED' ? '已签发' : '草稿' }}</b>；冻结事实版本：V{{
                workPackage.snapshotVersion ?? '-'
              }}；生成时间：{{ parseTime(workPackage.generatedAt) || '-' }}；签发时间：{{
                parseTime(workPackage.issuedAt) || '-'
              }}
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-if="currentTask"
              v-hasPermi="['content:package:generate']"
              type="primary"
              plain
              icon="Refresh"
              :loading="generating"
              @click="handleGenerate(currentTask)"
            >
              重新生成
            </el-button>
            <el-button
              v-if="workPackage && workPackage.status !== 'ISSUED'"
              v-hasPermi="['content:package:issue']"
              type="success"
              plain
              icon="Select"
              :loading="issuing"
              @click="handleIssue"
            >
              签发开工包
            </el-button>
          </div>
        </div>
      </template>

      <div v-if="content" v-loading="loadingPackage" class="package-body">
        <div class="package-grid">
          <div class="package-block">
            <div class="block-title">产品与交付</div>
            <div class="kv-row">
              <span>产品：{{ content.product?.productName || '-' }}</span>
              <span>产品编码：{{ content.product?.productCode || '-' }}</span>
              <span>SKU：{{ content.product?.skuCode || content.product?.skuCodeFromTask || '-' }}</span>
              <span>产品版本：{{ content.product?.version || '-' }}</span>
              <span>交付类型：{{ content.deliverableTypeName || content.deliverableType || '-' }}</span>
            </div>
          </div>
          <div class="package-block">
            <div class="block-title">负责人与截止</div>
            <div class="kv-row">
              <span>负责人：{{ content.owner?.ownerName || '-' }}</span>
              <span>截止时间：{{ parseTime(content.deadline) || content.deadline || '-' }}</span>
              <span>快照版本：V{{ content.snapshotVersion ?? '-' }}</span>
            </div>
          </div>
        </div>

        <div class="package-block">
          <div class="block-title">已确认事实（{{ content.confirmedFacts?.length || 0 }}）</div>
          <el-table
            v-if="content.confirmedFacts?.length"
            border
            size="small"
            :data="content.confirmedFacts"
            class="inner-table"
          >
            <el-table-column label="字段" align="center" prop="fieldName" width="160" show-overflow-tooltip />
            <el-table-column label="值" align="center" prop="value" width="180" show-overflow-tooltip />
            <el-table-column label="来源定位" align="center" prop="sourceLocator" show-overflow-tooltip />
            <el-table-column label="原文摘录" align="center" prop="sourceExcerpt" show-overflow-tooltip />
            <el-table-column label="确认时间" align="center" width="170">
              <template #default="scope">
                <span>{{ parseTime(scope.row.confirmedAt) || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
          <p v-else class="muted">尚无已确认事实；未经人工确认的解析值不会进入开工包。</p>
        </div>

        <div class="package-block">
          <div class="block-title">缺口与替代（{{ content.gaps?.length || 0 }}）</div>
          <el-table v-if="content.gaps?.length" border size="small" :data="content.gaps" class="inner-table">
            <el-table-column label="字段" align="center" prop="fieldName" width="160" show-overflow-tooltip />
            <el-table-column label="字段编码" align="center" prop="fieldCode" width="170" show-overflow-tooltip />
            <el-table-column label="说明" align="center" prop="note" show-overflow-tooltip />
          </el-table>
          <p v-else class="muted">无缺口（条件项均已满足）。</p>
        </div>

        <div class="package-block">
          <div class="block-title">允许的 AI 动作</div>
          <el-table v-if="content.allowedAiActions?.length" border size="small" :data="content.allowedAiActions" class="inner-table">
            <el-table-column label="能力编码" align="center" prop="capability" width="180" show-overflow-tooltip />
            <el-table-column label="名称" align="center" prop="name" width="140" show-overflow-tooltip />
            <el-table-column label="说明" align="center" prop="desc" show-overflow-tooltip />
            <el-table-column label="范围" align="center" prop="scope" width="120" />
          </el-table>
          <p v-else class="muted">未声明允许的 AI 动作。</p>
        </div>

        <div class="package-block">
          <div class="block-title">不可修改项</div>
          <div class="tag-bar">
            <el-tag v-for="item in content.immutableItems || []" :key="item" type="danger" effect="plain">
              {{ item }}
            </el-tag>
            <span v-if="!(content.immutableItems || []).length" class="muted">未声明不可修改项。</span>
          </div>
        </div>

        <div class="package-grid">
          <div class="package-block">
            <div class="block-title">输出规格</div>
            <div class="kv-row">
              <span>输出尺寸：{{ content.spec?.outputSize || '-' }}</span>
              <span>分辨率：{{ content.spec?.resolution || '-' }}</span>
              <span>验收口径：{{ content.spec?.acceptance || '-' }}</span>
            </div>
          </div>
          <div class="package-block">
            <div class="block-title">资料依据素材</div>
            <div class="tag-bar">
              <el-tag
                v-for="asset in content.assets?.productBasis || []"
                :key="String(asset.fileId)"
                type="info"
                effect="plain"
              >
                {{ asset.fileName }}
              </el-tag>
              <span v-if="!(content.assets?.productBasis || []).length" class="muted">暂无资料依据。</span>
            </div>
          </div>
        </div>

        <div class="package-block">
          <div class="block-title">原始内容（contentJson）</div>
          <el-input v-model="contentJsonText" type="textarea" :rows="8" readonly />
        </div>
      </div>
      <p v-else v-loading="loadingPackage" class="muted package-empty">
        {{ currentTask ? '（该任务暂无开工包）' : '（未选择任务）' }}
      </p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import type { CpTaskQuery, CpTaskVO } from '@/api/content/task/types';
import type { CpWorkPackageVO, WorkPackageContent } from '@/api/content/workPackage/types';
import { listTask } from '@/api/content/task';
import { generateWorkPackage, getWorkPackageByTask, issueWorkPackage } from '@/api/content/workPackage';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'ContentWorkPackage' });

const { cp_deliverable_type, cp_task_status } = toRefs<any>(useDict('cp_deliverable_type', 'cp_task_status'));

const taskList = ref<CpTaskVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();

/** 当前选中的任务与其开工包 */
const currentTask = ref<CpTaskVO | null>(null);
const workPackage = ref<CpWorkPackageVO | null>(null);
const content = ref<WorkPackageContent | null>(null);
const contentJsonText = ref('');
const loadingPackage = ref(false);
const generating = ref(false);
const issuing = ref(false);

const data = reactive<PageData<Record<string, never>, CpTaskQuery>>({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    queryTaskNo: '',
    queryTaskName: '',
    queryDeliverableType: undefined,
    params: {}
  },
  rules: {}
});

const { queryParams } = toRefs<PageData<Record<string, never>, CpTaskQuery>>(data);
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** contentJson 安全解析（解析失败不阻断页面，仅提示） */
const parseContent = (text?: string): WorkPackageContent | null => {
  if (!text) return null;
  try {
    return JSON.parse(text) as WorkPackageContent;
  } catch {
    return null;
  }
};

/** 查询任务列表（开工包按任务查询，故列表即任务列表） */
const getList = async () => {
  await withLoading(async () => {
    const res = await listTask(queryParams.value);
    taskList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 加载某任务的开工包 */
const loadPackage = async (task: CpTaskVO) => {
  currentTask.value = task;
  loadingPackage.value = true;
  try {
    const res = await getWorkPackageByTask(task.taskId!);
    workPackage.value = res.data || null;
    contentJsonText.value = res.data?.contentJson || '';
    content.value = parseContent(res.data?.contentJson);
    if (res.data?.contentJson && !content.value) {
      modal.msgWarning('开工包内容不是合法 JSON，已原样展示');
    }
  } finally {
    loadingPackage.value = false;
  }
};

/** 查看开工包 */
const handleLoad = (row: CpTaskVO) => {
  loadPackage(row);
};

/** 生成开工包（草稿），然后刷新展示 */
const handleGenerate = async (row: CpTaskVO) => {
  if (generating.value) return;
  generating.value = true;
  try {
    await generateWorkPackage(row.taskId!);
    modal.msgSuccess('开工包已生成');
    await loadPackage(row);
  } finally {
    generating.value = false;
  }
};

/** 签发开工包：签发时冻结当前事实版本 */
const handleIssue = async () => {
  if (!workPackage.value?.packageId || issuing.value) return;
  await modal.confirm('签发后开工包内容与事实版本将被冻结，是否继续？');
  issuing.value = true;
  try {
    await issueWorkPackage(workPackage.value.packageId);
    modal.msgSuccess('签发成功');
    if (currentTask.value) {
      await loadPackage(currentTask.value);
    }
  } finally {
    issuing.value = false;
  }
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.package-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.package-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}

.package-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.block-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-title);
}

.kv-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: var(--app-text-body);
}

.tag-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.inner-table {
  width: 100%;
}

.muted {
  margin: 0;
  font-size: 12px;
  color: var(--app-text-muted);
}

.package-empty {
  padding: 8px 0;
}
</style>
