<template>
  <div class="p-2 app-container talent-audit-page">
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>筛选条件</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="操作人" prop="operatorName">
            <el-input v-model="queryParams.operatorName" placeholder="请输入操作人账号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="动作" prop="action">
            <el-select v-model="queryParams.action" placeholder="请选择动作" clearable style="width: 200px">
              <el-option v-for="item in auditActionOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="对象类型" prop="targetType">
            <el-select v-model="queryParams.targetType" placeholder="请选择对象类型" clearable style="width: 180px">
              <el-option v-for="item in auditTargetOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="人才ID" prop="talentId">
            <el-input v-model="queryParams.talentId" placeholder="请输入人才ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="结果" prop="result">
            <el-select v-model="queryParams.result" placeholder="请选择结果" clearable style="width: 140px">
              <el-option label="成功" value="0" />
              <el-option label="失败" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作时间">
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
            <span class="panel-kicker">Sensitive Audit</span>
            <h3>敏感操作审计</h3>
            <p>共 {{ total }} 条记录；IP 与 User-Agent 仅存摘要，完整手机号不会出现在本页。</p>
          </div>
          <div class="toolbar-actions">
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="auditList">
        <el-table-column label="审计ID" align="center" prop="auditId" width="180" show-overflow-tooltip />
        <el-table-column label="操作人" align="center" prop="operatorName" width="120" />
        <el-table-column label="动作" align="center" width="150">
          <template #default="scope">{{ scope.row.actionLabel || actionLabel(scope.row.action) }}</template>
        </el-table-column>
        <el-table-column label="对象类型" align="center" width="130">
          <template #default="scope">{{ targetLabel(scope.row.targetType) }}</template>
        </el-table-column>
        <el-table-column label="对象ID" align="center" prop="targetId" width="180" show-overflow-tooltip />
        <el-table-column label="人才ID" align="center" prop="talentId" width="180" show-overflow-tooltip />
        <el-table-column label="结果" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.result === '1' ? 'danger' : 'success'">
              {{ scope.row.result === '1' ? '失败' : '成功' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原因/摘要" align="center" prop="reason" show-overflow-tooltip />
        <el-table-column label="IP摘要" align="center" prop="ipDigest" width="200" show-overflow-tooltip />
        <el-table-column label="操作时间" align="center" width="180">
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
import type { SensitiveAuditQuery, SensitiveAuditVO } from '@/api/talent/audit/types';
import { listAudit } from '@/api/talent/audit';
import { useLoading } from '@/hooks/async/useLoading';
import { useDateRangeQuery } from '@/hooks/form/useDateRangeQuery';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'TalentAudit' });

const auditActionOptions = [
  { value: 'VIEW_DETAIL', label: '查看详情' },
  { value: 'VIEW_FULL_PHONE', label: '查看完整手机号' },
  { value: 'DOWNLOAD', label: '下载附件' },
  { value: 'EXPORT', label: '导出' },
  { value: 'CREATE_GRANT', label: '创建授权' },
  { value: 'DELETE', label: '删除' },
  { value: 'ARCHIVE', label: '归档' },
  { value: 'UPLOAD', label: '上传' }
];
const auditTargetOptions = [
  { value: 'TALENT', label: '人才档案' },
  { value: 'ATTACHMENT', label: '附件' },
  { value: 'EXPORT', label: '导出任务' },
  { value: 'GRANT', label: '授权' },
  { value: 'PARSE_TASK', label: '解析任务' }
];

const actionLabel = (value?: string) => auditActionOptions.find(item => item.value === value)?.label || value || '-';
const targetLabel = (value?: string) => auditTargetOptions.find(item => item.value === value)?.label || value || '-';

const auditList = ref<SensitiveAuditVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const { dateRange, applyDateRange, resetDateRange } = useDateRangeQuery();

const queryParams = ref<SensitiveAuditQuery>({
  pageNum: 1,
  pageSize: 10,
  operatorName: '',
  action: undefined,
  targetType: undefined,
  talentId: undefined,
  result: undefined,
  params: {}
});

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  resetExtras: () => resetDateRange(),
  afterReset: () => handleQuery()
});

/** 查询敏感操作审计列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listAudit(applyDateRange(queryParams.value));
    auditList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

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
</style>
