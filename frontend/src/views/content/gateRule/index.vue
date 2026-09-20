<template>
  <div class="p-2 app-container content-gateRule-page">
    <PageHeading
      title="内容生产协同"
      subtitle="闸门规则表驱动：强制阻断 / 条件流转 / 非阻断提醒，运营可改，不发版"
      module="content"
    />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>规则检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="交付类型" prop="queryDeliverableType">
            <el-select
              v-model="queryParams.queryDeliverableType"
              placeholder="请选择交付类型"
              clearable
              style="width: 180px"
            >
              <el-option
                v-for="dict in cp_deliverable_type"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="字段编码" prop="fieldCode">
            <el-input
              v-model="queryParams.fieldCode"
              placeholder="如 product_height"
              clearable
              style="width: 200px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="闸门等级" prop="gateLevel">
            <el-select v-model="queryParams.gateLevel" placeholder="请选择闸门等级" clearable style="width: 160px">
              <el-option v-for="dict in cp_gate_level" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否启用" prop="enabled">
            <el-select v-model="queryParams.enabled" placeholder="请选择是否启用" clearable style="width: 140px">
              <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
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
            <span class="panel-kicker">Gate Rules</span>
            <h3>闸门规则</h3>
            <p>
              共 {{ total }} 条记录；强制项未确认时任务不可开工，条件项未满足则只能条件开工。启用口径与平台一致：
              <b>0 启用 / 1 停用</b>。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['content:gateRule:add']" type="primary" plain icon="Plus" @click="handleAdd">
              新增
            </el-button>
            <el-button
              v-hasPermi="['content:gateRule:edit']"
              type="success"
              plain
              icon="Edit"
              :disabled="single"
              @click="handleUpdate()"
            >
              修改
            </el-button>
            <el-button
              v-hasPermi="['content:gateRule:remove']"
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

      <el-table
        v-loading="loading"
        border
        class="data-table"
        :data="ruleList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="交付类型" align="center" width="130">
          <template #default="scope">
            <dict-tag :options="cp_deliverable_type" :value="scope.row.deliverableType" />
          </template>
        </el-table-column>
        <el-table-column label="字段编码" align="center" prop="fieldCode" width="170" show-overflow-tooltip />
        <el-table-column label="字段名称" align="center" prop="fieldName" width="160" show-overflow-tooltip />
        <el-table-column label="闸门等级" align="center" width="120">
          <template #default="scope">
            <dict-tag :options="cp_gate_level" :value="scope.row.gateLevel" />
          </template>
        </el-table-column>
        <el-table-column label="是否必须存在" align="center" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.requirePresent === 'Y' ? 'danger' : 'info'" size="small">
              {{ scope.row.requirePresent === 'Y' ? '必须存在' : '可缺失' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否启用" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.enabled" />
          </template>
        </el-table-column>
        <el-table-column label="排序" align="center" prop="sortNo" width="80" />
        <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button
                v-hasPermi="['content:gateRule:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                v-hasPermi="['content:gateRule:remove']"
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

    <!-- 新增或修改闸门规则对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="720px" append-to-body>
      <el-form ref="ruleFormRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="交付类型" prop="deliverableType">
              <el-select v-model="form.deliverableType" placeholder="请选择交付类型" style="width: 100%">
                <el-option
                  v-for="dict in cp_deliverable_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="闸门等级" prop="gateLevel">
              <el-select v-model="form.gateLevel" placeholder="请选择闸门等级" style="width: 100%">
                <el-option v-for="dict in cp_gate_level" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="字段编码" prop="fieldCode">
              <el-input v-model="form.fieldCode" placeholder="如 product_height" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="字段名称" prop="fieldName">
              <el-input v-model="form.fieldName" placeholder="如 产品高度" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="是否必须存在" prop="requirePresent">
              <el-radio-group v-model="form.requirePresent">
                <el-radio v-for="opt in requirePresentOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortNo">
              <el-input-number v-model="form.sortNo" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="是否启用" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">
              {{ dict.label }}
            </el-radio>
          </el-radio-group>
          <div class="form-tip">与平台口径一致：0 启用、1 停用；停用后该字段不参与闸门判定。</div>
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
import type { CpGateRuleForm, CpGateRuleQuery, CpGateRuleVO } from '@/api/content/gateRule/types';
import { addGateRule, delGateRule, getGateRule, listGateRule, updateGateRule } from '@/api/content/gateRule';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';

defineOptions({ name: 'ContentGateRule' });

const { cp_deliverable_type, cp_gate_level, sys_normal_disable } = toRefs<any>(
  useDict('cp_deliverable_type', 'cp_gate_level', 'sys_normal_disable')
);

/** require_present 无字典，按 SPEC 语义本地给出两项 */
const requirePresentOptions = [
  { label: '必须存在', value: 'Y' },
  { label: '可缺失', value: 'N' }
];

const ruleList = ref<CpGateRuleVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const { ids, single, multiple, handleSelectionChange } = useTableSelection<CpGateRuleVO>(item => item.ruleId!);
const total = ref(0);
const ruleFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();

const initFormData: CpGateRuleForm = {
  ruleId: undefined,
  deliverableType: '',
  fieldCode: '',
  fieldName: '',
  gateLevel: 'BLOCK',
  requirePresent: 'Y',
  enabled: '0',
  sortNo: 0,
  remark: ''
};

const data = reactive<PageData<CpGateRuleForm, CpGateRuleQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    queryDeliverableType: undefined,
    fieldCode: '',
    gateLevel: undefined,
    enabled: undefined,
    params: {}
  },
  rules: {
    deliverableType: [{ required: true, message: '交付类型不能为空', trigger: 'change' }],
    fieldCode: [{ required: true, message: '事实字段编码不能为空', trigger: 'blur' }],
    gateLevel: [{ required: true, message: '闸门等级不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<CpGateRuleForm, CpGateRuleQuery>>(data);
const { dialog, resetForm, openDialog, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: ruleFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 查询闸门规则列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listGateRule(queryParams.value);
    ruleList.value = res.data?.rows || [];
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
};

/** 新增按钮操作 */
const handleAdd = () => {
  openDialog('新增闸门规则');
};

/** 修改按钮操作 */
const handleUpdate = async (row?: Partial<CpGateRuleVO>) => {
  resetForm();
  const ruleId = row?.ruleId || ids.value[0];
  const res = await getGateRule(ruleId!);
  Object.assign(form.value, res.data);
  showDialog('修改闸门规则');
};

/** 提交按钮 */
const submitForm = () => {
  ruleFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      form.value.ruleId ? await updateGateRule(form.value) : await addGateRule(form.value);
      modal.msgSuccess('操作成功');
      closeDialog();
      await getList();
    }
  });
};

/** 删除按钮操作 */
const handleDelete = async (row?: Partial<CpGateRuleVO>) => {
  const ruleIds = row?.ruleId || ids.value;
  await modal.confirm('是否确认删除规则编号为"' + ruleIds + '"的数据项？');
  await delGateRule(ruleIds);
  await getList();
  modal.msgSuccess('删除成功');
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}
</style>
