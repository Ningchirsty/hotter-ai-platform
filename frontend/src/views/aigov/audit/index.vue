<template>
  <div class="p-2 app-container aigov-audit-page">
    <PageHeading title="AI平台治理" subtitle="管理AI能力、模型接入与调用策略" admin module="aigov" />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>审计检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="能力" prop="capabilityCode">
            <el-select
              v-model="queryParams.capabilityCode"
              placeholder="请选择能力"
              clearable
              filterable
              style="width: 220px"
            >
              <el-option
                v-for="item in capabilityOptions"
                :key="item.capabilityCode"
                :label="capabilityLabel(item)"
                :value="item.capabilityCode!"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="数据等级" prop="dataLevel">
            <el-select v-model="queryParams.dataLevel" placeholder="请选择数据等级" clearable style="width: 160px">
              <el-option v-for="dict in aig_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否外发" prop="externalCall">
            <el-select v-model="queryParams.externalCall" placeholder="请选择" clearable style="width: 140px">
              <el-option label="外发" value="Y" />
              <el-option label="未外发" value="N" />
            </el-select>
          </el-form-item>
          <el-form-item label="调用人" prop="callerName">
            <el-input
              v-model="queryParams.callerName"
              placeholder="请输入调用人账号"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="traceId" prop="traceId">
            <el-input v-model="queryParams.traceId" placeholder="请输入 traceId" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="调用时间">
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
            <span class="panel-kicker">Invocation Audit</span>
            <h3>AI 调用审计</h3>
            <p>
              共 {{ total }} 条记录；逐次调用审计为追加型记录，<strong>只读、不可修改与删除</strong>。
              输入原文不入库，此处只展示哈希与摘要。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button icon="Refresh" plain @click="getList">刷新</el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="auditList">
        <el-table-column label="traceId" align="center" prop="traceId" width="220" show-overflow-tooltip />
        <el-table-column label="能力" align="center" width="180" show-overflow-tooltip>
          <template #default="scope">{{ capabilityLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="调用人" align="center" prop="callerName" width="120" />
        <el-table-column label="模型" align="center" width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.modelKey || '-' }}</template>
        </el-table-column>
        <el-table-column label="部署类型" align="center" width="130">
          <template #default="scope">
            <dict-tag :options="aig_deployment_type" :value="scope.row.deploymentType" />
          </template>
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
        <el-table-column label="策略命中" align="center" prop="policyHit" show-overflow-tooltip />
        <el-table-column label="耗时" align="center" width="100">
          <template #default="scope">{{ scope.row.latencyMs ?? '-' }} ms</template>
        </el-table-column>
        <el-table-column label="成本" align="center" width="110">
          <template #default="scope">{{ scope.row.cost ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" align="center" width="180">
          <template #default="scope">
            <el-tag :type="scope.row.result === '1' ? 'danger' : 'success'">
              {{ scope.row.result === '1' ? '失败' : '成功' }}
            </el-tag>
            <span v-if="scope.row.errorSummary" class="error-summary">{{ scope.row.errorSummary }}</span>
          </template>
        </el-table-column>
        <el-table-column label="调用时间" align="center" width="180">
          <template #default="scope">{{ parseTime(scope.row.operateTime) }}</template>
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
  </div>
</template>

<script setup lang="ts">
import { listCapability } from '@/api/aigov/capability';
import type { AigCapabilityVO } from '@/api/aigov/capability/types';
import { listInvocationAudit } from '@/api/aigov/audit';
import type { AigInvocationAuditQuery, AigInvocationAuditVO } from '@/api/aigov/audit/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useDateRangeQuery } from '@/hooks/form/useDateRangeQuery';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'AigInvocationAudit' });

const { aig_data_level, aig_deployment_type } = toRefs<any>(useDict('aig_data_level', 'aig_deployment_type'));

const auditList = ref<AigInvocationAuditVO[]>([]);
const capabilityOptions = ref<AigCapabilityVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const { dateRange, applyDateRange, resetDateRange } = useDateRangeQuery();

const queryParams = ref<AigInvocationAuditQuery>({
  pageNum: 1,
  pageSize: 10,
  traceId: undefined,
  capabilityCode: undefined,
  dataLevel: undefined,
  externalCall: undefined,
  callerName: undefined,
  params: {}
});

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  resetExtras: () => resetDateRange(),
  afterReset: () => handleQuery()
});

/** 能力下拉展示：编码 + 名称 */
const capabilityLabel = (item: Partial<AigCapabilityVO>) =>
  item.capabilityName ? `${item.capabilityName}（${item.capabilityCode}）` : item.capabilityCode || '-';

/** 查询调用审计列表（只读） */
const getList = async () => {
  await withLoading(async () => {
    const res = await listInvocationAudit(applyDateRange(queryParams.value));
    auditList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 加载能力下拉用于检索 */
const getCapabilityOptions = async () => {
  const res = await listCapability({ pageNum: 1, pageSize: 200 });
  capabilityOptions.value = res.data?.rows || [];
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

onMounted(() => {
  getCapabilityOptions();
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.error-summary {
  display: block;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-color-danger);
}
</style>
