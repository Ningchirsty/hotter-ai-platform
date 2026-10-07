<template>
  <div class="p-2 app-container aigov-binding-page">
    <PageHeading title="AI平台治理" subtitle="管理AI能力、模型接入与调用策略" admin module="aigov" />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>绑定检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="Agent版本ID" prop="agentVersionId">
            <el-input v-model="queryParams.agentVersionId" placeholder="精确匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="品牌ID" prop="brandId">
            <el-input v-model="queryParams.brandId" placeholder="精确匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="通道" prop="releaseChannel">
            <el-select v-model="queryParams.releaseChannel" placeholder="请选择通道" clearable style="width: 160px">
              <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="启用" prop="enabled">
            <el-select v-model="queryParams.enabled" placeholder="请选择" clearable style="width: 140px">
              <el-option label="启用" value="Y" />
              <el-option label="停用" value="N" />
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
            <span class="panel-kicker">Version Bindings</span>
            <h3>Agent 版本绑定</h3>
            <p>
              共 {{ total }} 条记录；绑定决定<b>受限通道</b>（TESTING/BRAND/DEPT）发布后谁能看见——
              受限通道发到 CANDIDATE/STABLE 时若没有任何启用中的绑定，服务层会拒绝发布。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['aig:agent:binding']" type="primary" plain icon="Plus" @click="handleAdd">
              新增绑定
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="bindingList">
        <el-table-column label="绑定ID" align="center" prop="bindingId" width="160" show-overflow-tooltip />
        <el-table-column label="Agent版本ID" align="center" prop="agentVersionId" width="160" show-overflow-tooltip />
        <el-table-column label="公司" align="center" prop="companyId" width="120" show-overflow-tooltip />
        <el-table-column label="品牌" align="center" prop="brandId" width="120" show-overflow-tooltip />
        <el-table-column label="场景" align="center" prop="scenarioCode" width="140" show-overflow-tooltip />
        <el-table-column label="角色范围" align="center" prop="roleScope" show-overflow-tooltip />
        <el-table-column label="通道" align="center" width="110">
          <template #default="scope">
            <el-tag type="warning">{{ channelLabel(scope.row.releaseChannel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.enabled === 'Y' ? 'success' : 'info'">
              {{ scope.row.enabled === 'Y' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效时间" align="center" width="180">
          <template #default="scope">{{ formatRange(scope.row.effectiveFrom, scope.row.effectiveTo) }}</template>
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

    <el-dialog v-model="dialogVisible" title="新增绑定" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="Agent版本ID" prop="agentVersionId">
          <el-input v-model="form.agentVersionId" placeholder="要绑定到哪个 Agent 版本" />
        </el-form-item>
        <el-form-item label="公司ID" prop="companyId">
          <el-input v-model="form.companyId" placeholder="留空=不限" />
        </el-form-item>
        <el-form-item label="品牌ID" prop="brandId">
          <el-input v-model="form.brandId" placeholder="留空=不限；候选只绑定指定品牌（§6.3-6）" />
        </el-form-item>
        <el-form-item label="场景" prop="scenarioCode">
          <el-input v-model="form.scenarioCode" placeholder="留空=不限场景" />
        </el-form-item>
        <el-form-item label="通道" prop="releaseChannel">
          <el-select v-model="form.releaseChannel" placeholder="留空=取版本当前通道" clearable style="width: 100%">
            <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio value="Y">启用</el-radio>
            <el-radio value="N">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生效时间" prop="effectiveFrom">
          <el-date-picker
            v-model="effectiveRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false">
        填了通道就必须与版本当前通道一致（否则按通道查询会自相矛盾，服务层会拒绝）。
      </el-alert>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { addBinding, listBinding } from '@/api/aigov/binding';
import type { AigAgentBindingForm, AigAgentBindingQuery, AigAgentBindingVO } from '@/api/aigov/binding/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';

defineOptions({ name: 'AigAgentBinding' });

/** 通道（与后端 AigReleaseChannelEnum 一致；除 GENERAL 外都是受限通道） */
const channelOptions = [
  { value: 'TESTING', label: 'TESTING 测试' },
  { value: 'BRAND', label: 'BRAND 品牌' },
  { value: 'DEPT', label: 'DEPT 部门' },
  { value: 'GENERAL', label: 'GENERAL 通用' }
];

const bindingList = ref<AigAgentBindingVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();
const dialogVisible = ref(false);
const effectiveRange = ref<string[]>([]);

const queryParams = ref<AigAgentBindingQuery>({
  pageNum: 1,
  pageSize: 10,
  params: {}
});

const form = ref<AigAgentBindingForm>({
  agentVersionId: undefined,
  companyId: undefined,
  brandId: undefined,
  scenarioCode: undefined,
  releaseChannel: undefined,
  enabled: 'Y',
  remark: ''
});

const rules = {
  agentVersionId: [{ required: true, message: 'Agent 版本ID不能为空', trigger: 'blur' }]
};

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 通道展示名 */
const channelLabel = (value?: string) => channelOptions.find((item) => item.value === value)?.label || value || '-';

/** 生效时间展示 */
const formatRange = (from?: string, to?: string) => {
  if (!from && !to) return '不限';
  return (from || '不限') + ' ~ ' + (to || '不限');
};

/** 查询绑定清单 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listBinding(queryParams.value);
    bindingList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 搜索 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 打开新增 */
const handleAdd = () => {
  form.value = { agentVersionId: undefined, enabled: 'Y', remark: '' };
  effectiveRange.value = [];
  dialogVisible.value = true;
};

/** 提交 */
const submitForm = async () => {
  await formRef.value?.validate();
  if (effectiveRange.value?.length === 2) {
    form.value.effectiveFrom = effectiveRange.value[0] + ' 00:00:00';
    form.value.effectiveTo = effectiveRange.value[1] + ' 23:59:59';
  }
  await addBinding(form.value);
  modal.msgSuccess('新增成功');
  dialogVisible.value = false;
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
