<template>
  <div class="p-2 app-container aigov-route-page">
    <PageHeading title="AI平台治理" subtitle="管理AI能力、模型接入与调用策略" admin />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>策略检索</h3>
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
          <el-form-item label="是否外发" prop="allowExternal">
            <el-select v-model="queryParams.allowExternal" placeholder="请选择" clearable style="width: 140px">
              <el-option label="允许外发" value="Y" />
              <el-option label="禁止外发" value="N" />
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
            <span class="panel-kicker">Route Policy</span>
            <h3>路由策略</h3>
            <p>
              共 {{ total }} 条记录；按「能力 × 数据等级」唯一确定一条策略，<strong>未配置即默认拒绝</strong>，
              不会默认放行。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['aig:route:add']" type="primary" plain icon="Plus" @click="handleAdd">
              新增
            </el-button>
            <el-button
              v-hasPermi="['aig:route:edit']"
              type="success"
              plain
              icon="Edit"
              :disabled="single"
              @click="handleUpdate()"
            >
              修改
            </el-button>
            <el-button
              v-hasPermi="['aig:route:remove']"
              type="danger"
              plain
              icon="Delete"
              :disabled="multiple"
              @click="handleDelete()"
            >
              删除
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-alert
        class="risk-banner"
        type="error"
        :closable="false"
        show-icon
        title="数据出域风险提示"
        description="开启「允许外发」意味着该能力在该数据等级下可路由到外部模型，数据将离开公司可控环境。限制级资料与人才个人资料默认禁止外发，请勿随意开启。"
      />

      <el-table
        v-loading="loading"
        border
        class="data-table"
        :data="policyList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="能力" align="center" width="200" show-overflow-tooltip>
          <template #default="scope">{{ capabilityLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="数据等级" align="center" width="120">
          <template #default="scope">
            <dict-tag :options="aig_data_level" :value="scope.row.dataLevel" />
          </template>
        </el-table-column>
        <el-table-column label="优先部署类型" align="center" width="150">
          <template #default="scope">
            <dict-tag :options="aig_deployment_type" :value="scope.row.preferredDeployment" />
          </template>
        </el-table-column>
        <el-table-column label="允许外发" align="center" width="150">
          <template #default="scope">
            <el-switch
              :model-value="scope.row.allowExternal === 'Y'"
              :disabled="!checkPermi(['aig:route:edit'])"
              :before-change="() => beforeAllowExternalChange(scope.row)"
              inline-prompt
              active-text="外发"
              inactive-text="禁止"
            />
          </template>
        </el-table-column>
        <el-table-column label="调用前审批" align="center" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.requireApproval === 'Y' ? 'warning' : 'info'">
              {{ scope.row.requireApproval === 'Y' ? '需要' : '不需要' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="无模型时" align="center" width="140">
          <template #default="scope">
            <el-tag :type="scope.row.fallbackToManual === 'Y' ? 'success' : 'danger'">
              {{ scope.row.fallbackToManual === 'Y' ? '转人工待办' : '拒绝调用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button
                v-hasPermi="['aig:route:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                v-hasPermi="['aig:route:remove']"
                link
                type="primary"
                icon="Delete"
                @click="handleDelete(scope.row)"
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

    <!-- 新增或修改路由策略对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="680px" append-to-body>
      <el-form ref="policyFormRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="能力" prop="capabilityCode">
          <el-select v-model="form.capabilityCode" placeholder="请选择能力" filterable style="width: 100%">
            <el-option
              v-for="item in capabilityOptions"
              :key="item.capabilityCode"
              :label="capabilityLabel(item)"
              :value="item.capabilityCode!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数据等级" prop="dataLevel">
          <el-select v-model="form.dataLevel" placeholder="请选择数据等级" style="width: 100%">
            <el-option v-for="dict in aig_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先部署类型" prop="preferredDeployment">
          <el-select v-model="form.preferredDeployment" placeholder="请选择优先部署类型" clearable style="width: 100%">
            <el-option
              v-for="dict in aig_deployment_type"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="允许外发" prop="allowExternal">
          <el-switch v-model="allowExternalSwitch" inline-prompt active-text="允许" inactive-text="禁止" />
          <div class="form-tip">关闭时，该能力在该数据等级下只能路由到本地私有 / 集团共享模型。</div>
        </el-form-item>
        <el-alert
          v-if="allowExternalSwitch"
          class="risk-alert"
          type="error"
          :closable="false"
          show-icon
          title="风险提示：外部调用意味着数据出域"
          description="开启后数据将发送到公司可控环境之外（外部企业服务 / 外部API），可能无法撤回。限制级资料、客户资料与人才个人资料禁止开启。"
        />
        <el-form-item label="调用前审批" prop="requireApproval">
          <el-switch v-model="approvalSwitch" inline-prompt active-text="需要" inactive-text="不需要" />
          <div class="form-tip">阶段1仅作为路由判定记录，审批流在二期实现。</div>
        </el-form-item>
        <el-form-item label="无模型时转人工" prop="fallbackToManual">
          <el-switch v-model="fallbackSwitch" inline-prompt active-text="转人工" inactive-text="拒绝" />
          <div class="form-tip">关闭时无可用模型直接判定为 DENIED（拒绝调用）。</div>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">
              {{ dict.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { listCapability } from '@/api/aigov/capability';
import type { AigCapabilityVO } from '@/api/aigov/capability/types';
import type { AigRoutePolicyForm, AigRoutePolicyQuery, AigRoutePolicyVO } from '@/api/aigov/route/types';
import {
  addRoutePolicy,
  delRoutePolicy,
  getRoutePolicy,
  listRoutePolicy,
  updateRoutePolicy
} from '@/api/aigov/route';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { checkPermi } from '@/utils/permission';

defineOptions({ name: 'AigRoutePolicy' });

const { aig_data_level, aig_deployment_type, sys_normal_disable } = toRefs<any>(
  useDict('aig_data_level', 'aig_deployment_type', 'sys_normal_disable')
);

const policyList = ref<AigRoutePolicyVO[]>([]);
const capabilityOptions = ref<AigCapabilityVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const { ids, single, multiple, handleSelectionChange } = useTableSelection<AigRoutePolicyVO>(item => item.policyId!);
const total = ref(0);
const policyFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();

const initFormData: AigRoutePolicyForm = {
  policyId: undefined,
  capabilityCode: '',
  dataLevel: '',
  preferredDeployment: undefined,
  allowExternal: 'N',
  requireApproval: 'N',
  fallbackToManual: 'Y',
  status: '0',
  remark: ''
};

const data = reactive<PageData<AigRoutePolicyForm, AigRoutePolicyQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    capabilityCode: undefined,
    dataLevel: undefined,
    allowExternal: undefined,
    params: {}
  },
  rules: {
    capabilityCode: [{ required: true, message: '能力不能为空', trigger: 'change' }],
    dataLevel: [{ required: true, message: '数据等级不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<AigRoutePolicyForm, AigRoutePolicyQuery>>(data);
const { dialog, resetForm, openDialog, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: policyFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** Y/N 与开关布尔值的双向映射 */
const toSwitch = (getter: () => string | undefined, setter: (value: string) => void) =>
  computed<boolean>({
    get: () => getter() === 'Y',
    set: val => setter(val ? 'Y' : 'N')
  });

const allowExternalSwitch = toSwitch(
  () => form.value.allowExternal,
  val => (form.value.allowExternal = val)
);
const approvalSwitch = toSwitch(
  () => form.value.requireApproval,
  val => (form.value.requireApproval = val)
);
const fallbackSwitch = toSwitch(
  () => form.value.fallbackToManual,
  val => (form.value.fallbackToManual = val)
);

/** 能力下拉展示：编码 + 名称 */
const capabilityLabel = (item: Partial<AigCapabilityVO>) =>
  item.capabilityName ? `${item.capabilityName}（${item.capabilityCode}）` : item.capabilityCode || '-';

/** 查询路由策略列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listRoutePolicy(queryParams.value);
    policyList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 加载能力下拉（策略必须挂在已登记的能力上） */
const getCapabilityOptions = async () => {
  const res = await listCapability({ pageNum: 1, pageSize: 200 });
  capabilityOptions.value = res.data?.rows || [];
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 取消按钮 */
const cancel = () => {
  closeDialog();
  resetForm();
};

/** 新增按钮操作 */
const handleAdd = () => {
  openDialog('新增路由策略');
};

/** 修改按钮操作 */
const handleUpdate = async (row?: Partial<AigRoutePolicyVO>) => {
  resetForm();
  const policyId = row?.policyId || ids.value[0];
  const res = await getRoutePolicy(policyId!);
  Object.assign(form.value, initFormData, {
    policyId: res.data?.policyId,
    capabilityCode: res.data?.capabilityCode,
    dataLevel: res.data?.dataLevel,
    preferredDeployment: res.data?.preferredDeployment,
    allowExternal: res.data?.allowExternal || 'N',
    requireApproval: res.data?.requireApproval || 'N',
    fallbackToManual: res.data?.fallbackToManual || 'Y',
    status: res.data?.status || '0',
    remark: res.data?.remark || ''
  });
  showDialog('修改路由策略');
};

/** 提交按钮 */
const submitForm = () => {
  policyFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      form.value.policyId ? await updateRoutePolicy(form.value) : await addRoutePolicy(form.value);
      modal.msgSuccess('操作成功');
      closeDialog();
      await getList();
    }
  });
};

/**
 * 列表内快捷切换「允许外发」：开启前必须二次确认（数据出域），
 * 确认后回写策略并重新拉取列表。
 */
const beforeAllowExternalChange = async (row: AigRoutePolicyVO): Promise<boolean> => {
  const next = row.allowExternal === 'Y' ? 'N' : 'Y';
  if (next === 'Y') {
    try {
      await modal.confirm('开启「允许外发」意味着数据将离开公司可控环境并可能无法撤回，是否确认开启？');
    } catch {
      // 用户取消：开关保持原值
      return false;
    }
  }
  try {
    await updateRoutePolicy({
      policyId: row.policyId,
      capabilityCode: row.capabilityCode,
      dataLevel: row.dataLevel,
      preferredDeployment: row.preferredDeployment,
      allowExternal: next,
      requireApproval: row.requireApproval,
      fallbackToManual: row.fallbackToManual,
      status: row.status,
      remark: row.remark
    });
    modal.msgSuccess('操作成功');
    await getList();
    return true;
  } catch {
    return false;
  }
};

/** 删除按钮操作 */
const handleDelete = async (row?: Partial<AigRoutePolicyVO>) => {
  const policyIds = row?.policyId || ids.value;
  await modal.confirm('是否确认删除策略编号为"' + policyIds + '"的数据项？');
  await delRoutePolicy(policyIds);
  await getList();
  modal.msgSuccess('删除成功');
};

onMounted(() => {
  getCapabilityOptions();
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.risk-banner {
  margin-bottom: 12px;
}

.risk-alert {
  margin: -8px 0 16px 130px;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}
</style>
