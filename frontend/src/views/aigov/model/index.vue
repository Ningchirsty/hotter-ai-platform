<template>
  <div class="p-2 app-container aigov-model-page">
    <PageHeading title="AI平台治理" subtitle="管理AI能力、模型接入与调用策略" admin />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>模型检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="模型键" prop="modelKey">
            <el-input v-model="queryParams.modelKey" placeholder="请输入模型键" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="模型名称" prop="modelName">
            <el-input
              v-model="queryParams.modelName"
              placeholder="请输入模型名称"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="部署类型" prop="deploymentType">
            <el-select v-model="queryParams.deploymentType" placeholder="请选择部署类型" clearable style="width: 180px">
              <el-option
                v-for="dict in aig_deployment_type"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="数据等级上限" prop="dataLevelMax">
            <el-select v-model="queryParams.dataLevelMax" placeholder="请选择数据等级" clearable style="width: 160px">
              <el-option v-for="dict in aig_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="生命周期" prop="lifecycleStatus">
            <el-select
              v-model="queryParams.lifecycleStatus"
              placeholder="请选择可用状态"
              clearable
              style="width: 160px"
            >
              <el-option
                v-for="dict in aig_lifecycle_status"
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
            <span class="panel-kicker">Model Registry</span>
            <h3>模型注册中心</h3>
            <p>
              共 {{ total }} 条记录；模型主数据来自 snail-ai（sai_model_config），本页只登记治理属性。
              密钥只存引用，本页不展示任何明文密钥。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['aig:model:edit']"
              type="success"
              plain
              icon="Edit"
              :disabled="single"
              @click="handleGovernance()"
            >
              登记治理属性
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        border
        class="data-table"
        :data="modelList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="模型键" align="center" prop="modelKey" width="160" show-overflow-tooltip />
        <el-table-column label="模型名称" align="center" prop="modelName" width="160" show-overflow-tooltip />
        <el-table-column label="模型类型" align="center" prop="modelType" width="120" />
        <el-table-column label="默认" align="center" width="90">
          <template #default="scope">
            <el-tag :type="isOn(scope.row.isDefault) ? 'success' : 'info'">
              {{ isOn(scope.row.isDefault) ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" align="center" width="90">
          <template #default="scope">
            <el-tag :type="isOn(scope.row.isEnabled) ? 'success' : 'danger'">
              {{ isOn(scope.row.isEnabled) ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部署类型" align="center" width="140">
          <template #default="scope">
            <dict-tag :options="aig_deployment_type" :value="scope.row.deploymentType" />
          </template>
        </el-table-column>
        <el-table-column label="数据等级上限" align="center" width="130">
          <template #default="scope">
            <dict-tag :options="aig_data_level" :value="scope.row.dataLevelMax" />
          </template>
        </el-table-column>
        <el-table-column label="可用状态" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="aig_lifecycle_status" :value="scope.row.lifecycleStatus" />
          </template>
        </el-table-column>
        <!-- 端点与密钥引用：仅 aig:model:secret 授权可见，无权限时该列不渲染 -->
        <el-table-column
          v-hasPermi="['aig:model:secret']"
          label="API 端点"
          align="center"
          prop="apiEndpoint"
          width="200"
          show-overflow-tooltip
        />
        <el-table-column v-hasPermi="['aig:model:secret']" label="密钥引用" align="center" width="200">
          <template #default="scope">
            <span>{{ scope.row.secretRef || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成本限额" align="center" prop="costLimit" show-overflow-tooltip />
        <el-table-column label="责任人" align="center" width="200">
          <template #default="scope">
            <div class="owner-cell">
              <span>技术：{{ scope.row.ownerTech || '-' }}</span>
              <span>业务：{{ scope.row.ownerBiz || '-' }}</span>
              <span>安全：{{ scope.row.ownerSecurity || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="有效期" align="center" width="180">
          <template #default="scope">
            <span>{{ scope.row.validFrom || '-' }} ~ {{ scope.row.validTo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="登记治理属性" placement="top">
              <el-button
                v-hasPermi="['aig:model:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleGovernance(scope.row)"
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

    <!-- 登记/修改治理属性对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="820px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="模型主数据由 snail-ai 维护，此处只补齐治理属性；密钥一律只登记引用，禁止粘贴明文密钥。"
      />
      <el-form ref="modelFormRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="模型键">
          <el-input :model-value="currentModel.modelKey" disabled placeholder="来自 snail-ai" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部署类型" prop="deploymentType">
              <el-select v-model="form.deploymentType" placeholder="请选择部署类型" style="width: 100%">
                <el-option
                  v-for="dict in aig_deployment_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据等级上限" prop="dataLevelMax">
              <el-select v-model="form.dataLevelMax" placeholder="请选择数据等级上限" style="width: 100%">
                <el-option v-for="dict in aig_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="可用状态" prop="lifecycleStatus">
              <el-select v-model="form.lifecycleStatus" placeholder="请选择可用状态" style="width: 100%">
                <el-option
                  v-for="dict in aig_lifecycle_status"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">
                  {{ dict.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 密钥引用：仅 aig:model:secret 授权可见；无权限时字段不渲染且不参与提交 -->
        <el-form-item v-hasPermi="['aig:model:secret']" label="密钥引用" prop="secretRef">
          <el-input v-model="form.secretRef" placeholder="如 kms://ai/qwen，只填引用不填明文" />
          <div class="form-tip">仅登记引用地址，后端不会读取 sai_model_config.api_key。</div>
        </el-form-item>
        <el-form-item label="输入限制" prop="inputLimits">
          <el-input v-model="form.inputLimits" placeholder="文本长度/文件类型/图片视频大小/并发" />
        </el-form-item>
        <el-form-item label="输出限制" prop="outputLimits">
          <el-input v-model="form.outputLimits" placeholder="格式/时长/分辨率/结构化输出能力" />
        </el-form-item>
        <el-form-item label="成本限额" prop="costLimit">
          <el-input v-model="form.costLimit" placeholder="单次/单项目/单日预算与限流规则" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="技术负责人" prop="ownerTech">
              <el-input v-model="form.ownerTech" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务负责人" prop="ownerBiz">
              <el-input v-model="form.ownerBiz" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="安全审批人" prop="ownerSecurity">
              <el-input v-model="form.ownerSecurity" placeholder="请输入" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="有效期起" prop="validFrom">
              <el-date-picker
                v-model="form.validFrom"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期止" prop="validTo">
              <el-date-picker
                v-model="form.validTo"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
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
import type { AigModelGovernanceForm, AigModelGovernanceVO, AigModelQuery } from '@/api/aigov/model/types';
import { getModel, listModel, updateModelGovernance } from '@/api/aigov/model';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { checkPermi } from '@/utils/permission';

defineOptions({ name: 'AigModel' });

const { aig_deployment_type, aig_data_level, aig_lifecycle_status, sys_normal_disable } = toRefs<any>(
  useDict('aig_deployment_type', 'aig_data_level', 'aig_lifecycle_status', 'sys_normal_disable')
);

const modelList = ref<AigModelGovernanceVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const { ids, single, handleSelectionChange } = useTableSelection<AigModelGovernanceVO>(item => item.modelId!);
const total = ref(0);
const modelFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();
/** 当前正在登记治理属性的模型（只读主数据回显） */
const currentModel = ref<AigModelGovernanceVO>({});

const initFormData: AigModelGovernanceForm = {
  governanceId: undefined,
  modelId: undefined,
  deploymentType: 'EXTERNAL_API',
  dataLevelMax: 'PUBLIC',
  lifecycleStatus: 'CANDIDATE',
  secretRef: '',
  inputLimits: '',
  outputLimits: '',
  costLimit: '',
  ownerTech: '',
  ownerBiz: '',
  ownerSecurity: '',
  validFrom: undefined,
  validTo: undefined,
  status: '0',
  remark: ''
};

const data = reactive<PageData<AigModelGovernanceForm, AigModelQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    modelKey: '',
    modelName: '',
    deploymentType: undefined,
    dataLevelMax: undefined,
    lifecycleStatus: undefined,
    params: {}
  },
  rules: {
    deploymentType: [{ required: true, message: '部署类型不能为空', trigger: 'change' }],
    dataLevelMax: [{ required: true, message: '数据等级上限不能为空', trigger: 'change' }],
    lifecycleStatus: [{ required: true, message: '可用状态不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<AigModelGovernanceForm, AigModelQuery>>(data);
const { dialog, resetForm, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: modelFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** sai_model_config 的 is_default / is_enabled 可能以 1 或 '1' 下发 */
const isOn = (value?: number | string) => value === 1 || value === '1';

/** 查询模型清单 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listModel(queryParams.value);
    modelList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
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
  currentModel.value = {};
};

/** 登记/修改治理属性 */
const handleGovernance = async (row?: Partial<AigModelGovernanceVO>) => {
  resetForm();
  const modelId = row?.modelId || ids.value[0];
  const res = await getModel(modelId!);
  currentModel.value = res.data || {};
  Object.assign(form.value, initFormData);
  form.value.modelId = modelId;
  form.value.governanceId = res.data?.governanceId;
  form.value.deploymentType = res.data?.deploymentType || initFormData.deploymentType;
  form.value.dataLevelMax = res.data?.dataLevelMax || initFormData.dataLevelMax;
  form.value.lifecycleStatus = res.data?.lifecycleStatus || initFormData.lifecycleStatus;
  form.value.secretRef = res.data?.secretRef || '';
  form.value.inputLimits = res.data?.inputLimits || '';
  form.value.outputLimits = res.data?.outputLimits || '';
  form.value.costLimit = res.data?.costLimit || '';
  form.value.ownerTech = res.data?.ownerTech || '';
  form.value.ownerBiz = res.data?.ownerBiz || '';
  form.value.ownerSecurity = res.data?.ownerSecurity || '';
  form.value.validFrom = res.data?.validFrom;
  form.value.validTo = res.data?.validTo;
  form.value.status = res.data?.status || '0';
  form.value.remark = res.data?.remark || '';
  showDialog('登记治理属性');
};

/** 提交按钮 */
const submitForm = () => {
  modelFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      const payload: AigModelGovernanceForm = { ...form.value };
      // 无 aig:model:secret 权限时不下发密钥引用字段，避免覆盖已有引用
      if (!checkPermi(['aig:model:secret'])) {
        delete payload.secretRef;
      }
      await updateModelGovernance(payload);
      modal.msgSuccess('操作成功');
      closeDialog();
      await getList();
    }
  });
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

.owner-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  font-size: 12px;
  line-height: 1.5;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}
</style>
