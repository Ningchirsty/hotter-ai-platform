<template>
  <div class="p-2 app-container aigov-resource-binding-page">
    <PageHeading
      title="AI平台治理"
      subtitle="管理AI能力、模型接入与调用策略"
      admin
      module="aigov"
    />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>工具/知识绑定检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="Agent版本ID" prop="agentVersionId">
            <el-input v-model="queryParams.agentVersionId" placeholder="精确匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="资源类型" prop="resourceType">
            <el-select v-model="queryParams.resourceType" placeholder="请选择" clearable style="width: 170px">
              <el-option v-for="item in RESOURCE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="资源编码" prop="resourceCode">
            <el-input v-model="queryParams.resourceCode" placeholder="精确匹配" clearable @keyup.enter="handleQuery" />
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
            <span class="panel-kicker">Tool / Knowledge Bindings</span>
            <h3>工具/知识绑定</h3>
            <p>
              共 {{ total }} 条记录。绑定是版本声明的<b>收窄</b>：<code>allowed_tools</code> /
              <code>knowledge_scope_json</code> 说"这个版本可以碰哪些工具/知识"，这里说
              "在什么范围下真的给它用、并且启用"。<b>只登记，不执行</b>——本仓没有 Tool Gateway /
              Knowledge 引擎，绑定不会让任何工具真的能跑。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['aig:agent:resource-binding']"
              type="primary"
              plain
              icon="Plus"
              @click="handleAdd"
            >
              新增绑定
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="bindingList">
        <template #empty>
          <AigEmptyState
            icon="Link"
            title="还没有资源绑定"
            hint="绑定 = 「这个 Agent 版本在什么范围内可以用这些工具/知识」；解绑后用同一范围可以重新绑。"
          >
            <el-button size="small" @click="getList">刷新</el-button>
          </AigEmptyState>
        </template>
        <el-table-column label="绑定ID" align="center" prop="bindingId" width="150" show-overflow-tooltip />
        <el-table-column label="Agent版本ID" align="center" prop="agentVersionId" width="150" show-overflow-tooltip />
        <el-table-column label="类型" align="center" width="110">
          <template #default="{ row }">
            <el-tag :type="row.resourceType === 'TOOL' ? 'warning' : 'success'" size="small">
              {{ resourceTypeLabel(row.resourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="资源编码" align="center" prop="resourceCode" show-overflow-tooltip />
        <el-table-column label="公司" align="center" width="100">
          <template #default="{ row }">{{ scopeLabel(row.companyId) }}</template>
        </el-table-column>
        <el-table-column label="品牌" align="center" width="100">
          <template #default="{ row }">{{ scopeLabel(row.brandId) }}</template>
        </el-table-column>
        <el-table-column label="场景" align="center" width="120">
          <template #default="{ row }">{{ scopeLabel(row.scenarioCode) }}</template>
        </el-table-column>
        <el-table-column label="角色范围" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ scopeLabel(row.roleScope) }}</template>
        </el-table-column>
        <el-table-column label="启用" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'Y' ? 'success' : 'info'" size="small">
              {{ row.enabled === 'Y' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="160" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-hasPermi="['aig:agent:resource-binding']"
              link
              type="danger"
              icon="Delete"
              @click="handleDelete(row)"
            >
              删除
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

    <el-dialog v-model="dialogVisible" title="新增工具/知识绑定" width="620px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        只能绑定<b>该版本已声明</b>的项：工具看 <code>allowed_tools</code>、知识看
        <code>knowledge_scope_json</code>；<code>forbidden_tools</code> 里的工具会被明确拒绝。
        绑定表<b>不是第二条声明通道</b>——治理台上说"不能用"的，这里也绑不上。
      </el-alert>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="Agent版本ID" prop="agentVersionId">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="form.agentVersionId" placeholder="要绑定到哪个 Agent 版本" />
            <el-button :loading="declaredLoading" @click="loadDeclared">读取声明</el-button>
          </div>
        </el-form-item>

        <el-form-item v-if="declared" label="版本声明">
          <div class="declared-box">
            <p v-if="declared.version" class="declared-line">
              版本 <b>{{ declared.version }}</b>（声明读取成功）
            </p>
            <p v-if="declaredHint(declared, 'TOOL')" class="declared-warn">{{ declaredHint(declared, 'TOOL') }}</p>
            <p v-else class="declared-line">
              已声明工具：{{ declaredItems(declared, 'TOOL').map(item => declaredOptionLabel(item)).join('、') }}
            </p>
            <p v-if="declaredHint(declared, 'KNOWLEDGE')" class="declared-warn">
              {{ declaredHint(declared, 'KNOWLEDGE') }}
            </p>
            <p v-else class="declared-line">
              已声明知识范围：{{ declaredItems(declared, 'KNOWLEDGE').map(item => declaredOptionLabel(item)).join('、') }}
            </p>
            <p v-if="forbiddenNote(declared)" class="declared-warn">{{ forbiddenNote(declared) }}</p>
          </div>
        </el-form-item>

        <el-form-item label="资源类型" prop="resourceType">
          <el-select v-model="form.resourceType" placeholder="请选择" style="width: 100%" @change="onTypeChange">
            <el-option v-for="item in RESOURCE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源编码" prop="resourceCode">
          <el-select
            v-model="form.resourceCode"
            filterable
            allow-create
            default-first-option
            placeholder="从已声明的项里选；也可以直接填编码（未声明的会被服务端拒绝）"
            style="width: 100%"
          >
            <el-option
              v-for="item in codeOptions"
              :key="item.code"
              :label="declaredOptionLabel(item)"
              :value="item.code"
            />
          </el-select>
          <div v-if="!declared" class="text-gray-500" style="font-size: 12px; line-height: 1.5">
            还没读取声明：先点「读取声明」，下拉里就会出现该版本可绑的项（避免填一个必被拒绝的编码）。
          </div>
        </el-form-item>
        <el-form-item label="公司ID" prop="companyId">
          <el-input v-model="form.companyId" placeholder="留空=不限" />
        </el-form-item>
        <el-form-item label="品牌ID" prop="brandId">
          <el-input v-model="form.brandId" placeholder="留空=不限" />
        </el-form-item>
        <el-form-item label="场景" prop="scenarioCode">
          <el-input v-model="form.scenarioCode" placeholder="留空=不限场景" />
        </el-form-item>
        <el-form-item label="角色范围" prop="roleScope">
          <el-input v-model="form.roleScope" placeholder="逗号分隔的 role_key；留空=不限" />
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio value="Y">启用</el-radio>
            <el-radio value="N">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false">
        同一版本 + 同一类型 + 同一编码在<b>相同范围</b>下只能绑一次（服务端会拒绝重复）；
        换个范围再绑一条是允许的。
      </el-alert>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { addResourceBinding, delResourceBinding, getDeclaredResources, listResourceBinding } from '@/api/aigov/resourceBinding';
import type {
  AigAgentDeclaredResourceVO,
  AigAgentResourceBindingForm,
  AigAgentResourceBindingQuery,
  AigAgentResourceBindingVO
} from '@/api/aigov/resourceBinding/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import AigEmptyState from '../components/AigEmptyState.vue';
import {
  bindingDeleteConfirmText,
  declaredHint,
  declaredItems,
  declaredOptionLabel,
  forbiddenNote,
  hasDeletableId,
  RESOURCE_TYPE_OPTIONS,
  resourceTypeLabel,
  scopeLabel
} from './presentation';

defineOptions({ name: 'AigAgentResourceBinding' });

const bindingList = ref<AigAgentResourceBindingVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();
const dialogVisible = ref(false);

/** 声明视图：读不到就不显示（页面据此提示"先读取声明"） */
const declared = ref<AigAgentDeclaredResourceVO>();
const declaredLoading = ref(false);

const queryParams = ref<AigAgentResourceBindingQuery>({
  pageNum: 1,
  pageSize: 10,
  params: {}
});

const form = ref<AigAgentResourceBindingForm>({
  agentVersionId: undefined,
  resourceType: 'TOOL',
  resourceCode: undefined,
  enabled: 'Y',
  remark: ''
});

const rules = {
  agentVersionId: [{ required: true, message: 'Agent 版本ID不能为空', trigger: 'blur' }],
  resourceType: [{ required: true, message: '资源类型不能为空', trigger: 'change' }],
  resourceCode: [{ required: true, message: '资源编码不能为空', trigger: 'change' }]
};

/** 当前类型下可选的声明项（用于下拉；未读取声明时为空） */
const codeOptions = computed(() => declaredItems(declared.value, form.value.resourceType));

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 查询绑定清单 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listResourceBinding(queryParams.value);
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
  form.value = {
    agentVersionId: undefined,
    resourceType: 'TOOL',
    resourceCode: undefined,
    enabled: 'Y',
    remark: ''
  };
  declared.value = undefined;
  dialogVisible.value = true;
};

/**
 * 读取版本声明（页面据此让人从声明里挑，而不是手打一个必被拒绝的编码）。
 */
const loadDeclared = async () => {
  const versionId = form.value.agentVersionId;
  if (!versionId) {
    modal.msgWarning('先填 Agent 版本ID');
    return;
  }
  declaredLoading.value = true;
  try {
    const res = await getDeclaredResources(versionId);
    // 取数期间改了版本ID：丢弃这次结果，避免把上一个版本的声明显示成本版本的
    if (String(form.value.agentVersionId) !== String(versionId)) return;
    declared.value = res.data || undefined;
  } catch (e) {
    declared.value = undefined;
    modal.alertError('读取声明失败：' + ((e as { message?: string })?.message || '未知原因'));
  } finally {
    declaredLoading.value = false;
  }
};

/** 换类型时清掉编码：不clear会留下一个与当前类型不匹配的编码，提交时才报错 */
const onTypeChange = () => {
  form.value.resourceCode = undefined;
};

/** 提交 */
const submitForm = async () => {
  await formRef.value?.validate();
  await addResourceBinding(form.value);
  modal.msgSuccess('新增成功');
  dialogVisible.value = false;
  getList();
};

/**
 * 删除一条绑定。
 *
 * 为什么要有这个按钮：范围列不可改、同一范围又只能绑一次——绑错范围时没有出路，
 * 而服务端的重复检查文案自己写着「要改范围请删掉旧的那条再建」。
 * 逻辑删除，删掉之后同范围可以立刻重建（不必等 5 秒防重窗：这个接口刻意没加 @RepeatSubmit）。
 */
const handleDelete = async (row: AigAgentResourceBindingVO) => {
  // 先挡"没有可用ID"：空 ID 拼进 URL 会打到一个不存在的地址上，错误看着像"删除失败"而不是"列表数据异常"
  if (!hasDeletableId(row)) {
    modal.msgError('这条绑定没有可用的绑定ID，无法删除（列表数据异常，请刷新后重试）');
    return;
  }
  try {
    await modal.confirm(bindingDeleteConfirmText(row));
  } catch {
    // 用户取消：不是错误，静默返回（不要把「取消」弹成错误提示）
    return;
  }
  await delResourceBinding(row.bindingId as string | number);
  modal.msgSuccess('删除成功');
  getList();
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.declared-box {
  width: 100%;
  font-size: 12.5px;
  line-height: 1.7;
}

.declared-line {
  margin: 0;
  color: var(--app-text-color-secondary, #606266);
  word-break: break-word;
}

.declared-warn {
  margin: 0;
  color: var(--el-color-warning, #e6a23c);
  word-break: break-word;
}
</style>
