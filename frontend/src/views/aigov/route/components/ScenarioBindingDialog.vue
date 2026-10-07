<template>
  <el-dialog
    :model-value="modelValue"
    title="场景强制绑定"
    width="980px"
    append-to-body
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
    @open="open"
  >
    <el-alert
      class="dialog-alert"
      type="info"
      :closable="false"
      show-icon
      title="命中绑定后，路由的候选集合会被收窄为「只保留指定供应商下的模型」。它只收紧、不放宽：被数据等级、生命周期、外发禁令或健康状态排除的模型，不会因为绑定了就变得可用。"
    />

    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="binding-query">
      <el-form-item label="场景编码" prop="scenarioCodeLike">
        <el-input
          v-model="queryParams.scenarioCodeLike"
          placeholder="如 LONG_PAGE"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="能力" prop="capabilityCode">
        <el-select v-model="queryParams.capabilityCode" placeholder="全部" clearable style="width: 200px">
          <el-option
            v-for="item in capabilityOptions"
            :key="item.capabilityCode"
            :label="capabilityLabel(item)"
            :value="item.capabilityCode!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="正常" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="binding-toolbar">
      <el-button v-hasPermi="['aig:route:binding']" type="primary" icon="Plus" @click="handleAdd">新增绑定</el-button>
      <el-button
        v-hasPermi="['aig:route:binding']"
        type="danger"
        plain
        icon="Delete"
        :disabled="multiple"
        @click="handleDelete()"
      >
        删除
      </el-button>
      <el-button icon="Refresh" @click="getList">刷新</el-button>
    </div>

    <el-table v-loading="loading" border :data="bindingList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="场景编码" align="center" prop="scenarioCode" width="150" />
      <el-table-column label="能力" align="center" width="200" show-overflow-tooltip>
        <template #default="scope">{{ capabilityText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="强制供应商" align="center" width="180" show-overflow-tooltip>
        <template #default="scope">
          <span v-if="scope.row.providerName">{{ scope.row.providerName }}</span>
          <span v-else class="binding-warn">供应商 {{ scope.row.providerId }}（已不存在）</span>
        </template>
      </el-table-column>
      <el-table-column label="优先序" align="center" prop="priority" width="90" />
      <el-table-column label="状态" align="center" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'">
            {{ scope.row.status === '0' ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
      <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button v-hasPermi="['aig:route:binding']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)">
            修改
          </el-button>
          <el-button v-hasPermi="['aig:route:binding']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">
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

    <el-alert
      v-if="!bindingList.length && !loading"
      class="dialog-alert binding-empty"
      type="warning"
      :closable="false"
      show-icon
      title="尚未配置任何场景强制绑定：带场景的调用不会因此被拒绝，但也不会被收窄——路由仍按优先级自由选取供应商。"
    />

    <!-- 新增/编辑绑定 -->
    <el-dialog v-model="formVisible" :title="formTitle" width="620px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="场景编码" prop="scenarioCode">
          <el-input
            v-model="form.scenarioCode"
            placeholder="如 LONG_PAGE（大写字母/数字/下划线/中划线）"
            @input="form.scenarioCode = (form.scenarioCode || '').toUpperCase()"
          />
          <div class="form-tip">
            比对时忽略大小写，这里统一按大写保存；改了它等于换一个场景，原场景的钉死随之失效。
          </div>
        </el-form-item>
        <el-form-item label="能力" prop="capabilityCode">
          <el-select v-model="form.capabilityCode" placeholder="选择能力" filterable style="width: 100%">
            <el-option
              v-for="item in capabilityOptions"
              :key="item.capabilityCode"
              :label="capabilityLabel(item)"
              :value="item.capabilityCode!"
            />
          </el-select>
          <div class="form-tip">绑定与能力一一对应：同一场景下的不同能力要各自配置。</div>
        </el-form-item>
        <el-form-item label="强制供应商" prop="providerId">
          <el-select v-model="form.providerId" placeholder="选择供应商" filterable style="width: 100%">
            <el-option
              v-for="item in providerOptions"
              :key="item.providerId"
              :label="providerLabel(item)"
              :value="item.providerId!"
            />
          </el-select>
          <div class="form-tip">只有属于这家供应商的候选会被保留。若它名下没有可用模型，本次调用会转人工或按策略拒绝。</div>
        </el-form-item>
        <el-form-item label="优先序" prop="priority">
          <el-input-number v-model="form.priority" :min="0" :max="9999" controls-position="right" />
          <div class="form-tip">同一「场景 × 能力」绑定多家供应商时的展示/比对顺序；不改变候选之间原有的优先级。</div>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" active-value="0" inactive-value="1" active-text="正常" inactive-text="停用" />
          <div class="form-tip">停用后该绑定不参与路由，效果等同于删除但保留记录。</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="说明为什么钉死这家，便于事后复核" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
          <el-button @click="formVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import type { AigCapabilityVO } from '@/api/aigov/capability/types';
import { listCapability } from '@/api/aigov/capability';
import type { AigModelProviderOption } from '@/api/aigov/model/types';
import { listAllModelProviders } from '@/api/aigov/model';
import type {
  AigRouteScenarioBindingForm,
  AigRouteScenarioBindingQuery,
  AigRouteScenarioBindingVO
} from '@/api/aigov/route/types';
import {
  addScenarioBinding,
  delScenarioBinding,
  getScenarioBinding,
  listScenarioBinding,
  updateScenarioBinding
} from '@/api/aigov/route';
import { useLoading } from '@/hooks/async/useLoading';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import modal from '@/plugins/modal';

defineOptions({ name: 'AigRouteScenarioBinding' });

const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();

const { loading, withLoading } = useLoading(false);
const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();
const bindingList = ref<AigRouteScenarioBindingVO[]>([]);
const capabilityOptions = ref<AigCapabilityVO[]>([]);
const providerOptions = ref<AigModelProviderOption[]>([]);
const { ids, multiple, handleSelectionChange } = useTableSelection<AigRouteScenarioBindingVO>(
  item => item.bindId!
);
const total = ref(0);
const formVisible = ref(false);
const formTitle = ref('新增场景强制绑定');
const submitting = ref(false);

const queryParams = ref<AigRouteScenarioBindingQuery>({
  pageNum: 1,
  pageSize: 10,
  scenarioCodeLike: undefined,
  capabilityCode: undefined,
  status: undefined
});

const initForm = (): AigRouteScenarioBindingForm => ({
  bindId: undefined,
  scenarioCode: '',
  capabilityCode: undefined,
  providerId: undefined,
  priority: 0,
  status: '0',
  remark: ''
});
const form = ref<AigRouteScenarioBindingForm>(initForm());

const rules = {
  scenarioCode: [
    { required: true, message: '场景编码不能为空', trigger: 'blur' },
    { max: 64, message: '场景编码长度不能超过 64', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '只能包含字母、数字、下划线、中划线', trigger: 'blur' }
  ],
  capabilityCode: [{ required: true, message: '请选择能力', trigger: 'change' }],
  providerId: [{ required: true, message: '请选择供应商', trigger: 'change' }]
};

const capabilityLabel = (item: Partial<AigCapabilityVO>) =>
  item.capabilityName ? `${item.capabilityName}（${item.capabilityCode}）` : (item.capabilityCode ?? '');

const capabilityText = (row: AigRouteScenarioBindingVO) => {
  const matched = capabilityOptions.value.find(item => item.capabilityCode === row.capabilityCode);
  return matched?.capabilityName
    ? `${matched.capabilityName}（${row.capabilityCode}）`
    : (row.capabilityName ? `${row.capabilityName}（${row.capabilityCode}）` : (row.capabilityCode ?? ''));
};

const providerLabel = (item: AigModelProviderOption) =>
  item.providerName ? `${item.providerName}（${item.providerKey}）` : String(item.providerId ?? '');

const getList = () => {
  withLoading(async () => {
    const res = await listScenarioBinding(queryParams.value);
    bindingList.value = res.data?.rows ?? [];
    total.value = res.data?.total ?? 0;
  });
};

/** 首次打开时加载下拉项；已加载过就不重复请求 */
const loadOptions = async () => {
  if (!capabilityOptions.value.length) {
    const res = await listCapability({ pageNum: 1, pageSize: 200 });
    capabilityOptions.value = res.data?.rows ?? [];
  }
  if (!providerOptions.value.length) {
    const res = await listAllModelProviders();
    providerOptions.value = res.data ?? [];
  }
};

const open = async () => {
  await loadOptions();
  getList();
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.value.scenarioCodeLike = undefined;
  queryParams.value.capabilityCode = undefined;
  queryParams.value.status = undefined;
  queryFormRef.value?.resetFields();
  handleQuery();
};

const handleAdd = () => {
  form.value = initForm();
  formTitle.value = '新增场景强制绑定';
  formVisible.value = true;
};

const handleUpdate = async (row: AigRouteScenarioBindingVO) => {
  const bindId = row.bindId ?? ids.value[0];
  const res = await getScenarioBinding(bindId!);
  form.value = { ...initForm(), ...res.data };
  formTitle.value = '修改场景强制绑定';
  formVisible.value = true;
};

const submitForm = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    submitting.value = true;
    try {
      if (form.value.bindId) {
        await updateScenarioBinding(form.value);
        modal.msgSuccess('绑定已更新');
      } else {
        await addScenarioBinding(form.value);
        modal.msgSuccess('绑定已新增');
      }
      formVisible.value = false;
      getList();
    } finally {
      submitting.value = false;
    }
  });
};

const handleDelete = async (row?: Partial<AigRouteScenarioBindingVO>) => {
  const bindIds = row?.bindId ? [row.bindId] : ids.value;
  if (!bindIds.length) {
    return;
  }
  const label = row?.scenarioCode ?? `${bindIds.length} 条绑定`;
  try {
    await modal.confirm(
      `删除后「${label}」不再限制供应商，路由将恢复为按策略与优先级自由选取。确定删除？`
    );
  } catch {
    return;
  }
  await delScenarioBinding(bindIds.join(','));
  modal.msgSuccess('已删除，该场景恢复为自由选取供应商');
  getList();
};

// 关闭时让下次打开重新拉取，避免看到改过权限前后的陈旧列表
watch(
  () => props.modelValue,
  value => {
    if (!value) {
      bindingList.value = [];
      total.value = 0;
    }
  }
);
</script>

<style lang="scss" scoped>
.dialog-alert {
  margin-bottom: 12px;
}

.binding-query {
  margin-bottom: 4px;
}

.binding-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.binding-empty {
  margin-top: 12px;
}

.binding-warn {
  color: var(--el-color-danger);
}

.form-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}
</style>
