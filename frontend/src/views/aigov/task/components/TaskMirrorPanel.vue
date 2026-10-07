<template>
  <el-card shadow="hover" class="table-panel mirror-panel">
    <template #header>
      <div class="toolbar-shell">
        <div class="table-heading">
          <span class="panel-kicker">Legacy Task Mirror</span>
          <h3>存量任务镜像（只读）</h3>
          <p>
            新任务走统一任务层；存量任务在这里<b>只读呈现</b>——看得见，
            但重试/选定/质检仍回到各业务域自己的页面与权限上。
          </p>
        </div>
        <div class="toolbar-actions">
          <!-- 来源必选：后端刻意不做跨来源合并分页（各来源分页语义不同，合成一页会让页码与总数失真） -->
          <el-select
            v-model="queryParams.source"
            placeholder="选择来源"
            style="width: 200px"
            @change="handleQuery"
          >
            <el-option
              v-for="item in sources"
              :key="item.source"
              :label="item.label || item.source"
              :value="item.source"
            />
          </el-select>
          <el-input
            v-model="queryParams.status"
            placeholder="来源状态码，如 SUCCEEDED"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
          <el-input
            v-model="queryParams.projectId"
            placeholder="业务对象ID"
            clearable
            style="width: 150px"
            @keyup.enter="handleQuery"
          />
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </div>
      </div>
    </template>

    <!-- 状态口径要说清楚，否则会被当成「统一任务状态」来读 -->
    <el-alert
      v-if="currentSource"
      class="mirror-alert"
      type="info"
      :closable="false"
      show-icon
      :title="currentSource.description || ''"
    />
    <el-alert
      class="mirror-alert"
      type="warning"
      :closable="false"
      show-icon
      title="只读：这里的状态是来源自己的状态原值，刻意没有映射成统一任务的状态；重试/选定/质检请到来源页面操作。"
    />

    <el-table v-loading="loading" border class="data-table" :data="rows">
      <el-table-column label="来源" align="center" width="150" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.sourceLabel || scope.row.source }}</template>
      </el-table-column>
      <el-table-column label="行ID" align="center" prop="refId" width="130" show-overflow-tooltip />
      <el-table-column label="业务对象" align="center" width="180" show-overflow-tooltip>
        <template #default="scope">
          <span v-if="scope.row.projectName">{{ scope.row.projectName }}</span>
          <span v-else>{{ scope.row.projectId ?? '-' }}</span>
          <span v-if="scope.row.projectName && scope.row.projectId"> / {{ scope.row.projectId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="行" align="center" prop="title" width="130" show-overflow-tooltip />
      <el-table-column label="来源状态" align="center" width="170">
        <template #default="scope">
          <!-- 状态原值 + 来源状态机提示：不映射成统一任务状态，是刻意的 -->
          <el-tooltip :content="scope.row.stateMachine || '来源自有状态机'" placement="top">
            <el-tag :type="mirrorStatusTagType(scope.row)">{{ scope.row.statusLabel || scope.row.status }}</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="终态" align="center" width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.terminal" type="info">已终态</el-tag>
          <el-tag v-else type="warning">进行中</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已选定" align="center" width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.selected" type="success">已选定</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="产出资产" align="center" prop="outputAssetId" width="110" show-overflow-tooltip />
      <el-table-column label="错误" align="center" width="220" show-overflow-tooltip>
        <template #default="scope">
          <span v-if="scope.row.errorCode || scope.row.errorMessage">
            <el-tag type="danger" size="small">{{ scope.row.errorCode || 'ERROR' }}</el-tag>
            {{ scope.row.errorMessage || '' }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="耗时" align="center" width="110">
        <template #default="scope">
          {{ scope.row.durationMs ? scope.row.durationMs + 'ms' : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" width="170">
        <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
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
</template>

<script setup lang="ts">
import { listAigTaskMirror, listAigTaskMirrorSources } from '@/api/aigov/task';
import type { AigTaskMirrorQuery, AigTaskMirrorSourceVO, AigTaskMirrorVO } from '@/api/aigov/task/types';
import { useLoading } from '@/hooks/async/useLoading';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'AigTaskMirrorPanel' });

const sources = ref<AigTaskMirrorSourceVO[]>([]);
const rows = ref<AigTaskMirrorVO[]>([]);
const total = ref(0);
const { loading, withLoading } = useLoading(false);
const queryParams = ref<AigTaskMirrorQuery>({
  pageNum: 1,
  pageSize: 10,
  source: '',
  status: '',
  projectId: ''
});

const currentSource = computed(() => sources.value.find((item) => item.source === queryParams.value.source));

/**
 * 镜像行的标签色只表达「还要不要盯」这一件事：
 * 已选定（来源侧采用）用 success，终态用 info，进行中/未终态用 warning。
 * 刻意不做「状态码 → 颜色」的映射——那等于在前端另建一份跨来源状态字典，
 * 而各来源的状态含义本就不同。
 */
const mirrorStatusTagType = (row: AigTaskMirrorVO) => {
  if (row.selected) return 'success';
  return row.terminal ? 'info' : 'warning';
};

const getList = async () => {
  if (!queryParams.value.source) {
    rows.value = [];
    total.value = 0;
    return;
  }
  await withLoading(async () => {
    const res = await listAigTaskMirror(queryParams.value);
    rows.value = res.data?.rows ?? [];
    total.value = Number(res.data?.total ?? 0);
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const handleReset = () => {
  queryParams.value.status = '';
  queryParams.value.projectId = '';
  handleQuery();
};

onMounted(async () => {
  const res = await listAigTaskMirrorSources();
  sources.value = res.data ?? [];
  // 默认选中第一个来源：来源是必填项，空着会让页面看起来「没有数据」
  if (!queryParams.value.source && sources.value.length > 0) {
    queryParams.value.source = sources.value[0].source;
  }
  await getList();
});
</script>

<style lang="scss" scoped>
.mirror-panel {
  margin-top: 12px;
}

.mirror-alert {
  margin-bottom: 8px;
}
</style>
