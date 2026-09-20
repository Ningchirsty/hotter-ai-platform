<template>
  <div class="p-2 app-container aigov-capability-page">
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>能力检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="能力编码" prop="capabilityCode">
            <el-input
              v-model="queryParams.capabilityCode"
              placeholder="请输入能力编码"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="能力名称" prop="capabilityName">
            <el-input
              v-model="queryParams.capabilityName"
              placeholder="请输入能力名称"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="数据策略" prop="dataPolicy">
            <el-select v-model="queryParams.dataPolicy" placeholder="请选择数据策略" clearable style="width: 180px">
              <el-option v-for="dict in aig_data_policy" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="审计等级" prop="auditLevel">
            <el-select v-model="queryParams.auditLevel" placeholder="请选择审计等级" clearable style="width: 180px">
              <el-option v-for="dict in aig_audit_level" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
              <el-option
                v-for="dict in sys_normal_disable"
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
            <span class="panel-kicker">Capability Catalog</span>
            <h3>AI 能力目录</h3>
            <p>
              共 {{ total }} 条记录；能力模板只定义「做什么」，具体模型由能力-模型绑定与路由策略决定。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['aig:capability:add']" type="primary" plain icon="Plus" @click="handleAdd">
              新增
            </el-button>
            <el-button
              v-hasPermi="['aig:capability:edit']"
              type="success"
              plain
              icon="Edit"
              :disabled="single"
              @click="handleUpdate()"
            >
              修改
            </el-button>
            <el-button
              v-hasPermi="['aig:capability:remove']"
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
        :data="capabilityList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="能力编码" align="center" prop="capabilityCode" width="160" show-overflow-tooltip />
        <el-table-column label="能力名称" align="center" prop="capabilityName" width="160" show-overflow-tooltip />
        <el-table-column label="业务目标" align="center" prop="bizGoal" show-overflow-tooltip />
        <el-table-column label="需要的能力标签" align="center" width="220">
          <template #default="scope">
            <template v-if="splitTags(scope.row.requiredTags).length">
              <el-tag v-for="tag in splitTags(scope.row.requiredTags)" :key="tag" class="tag-item" type="info">
                {{ tag }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="数据策略" align="center" width="120">
          <template #default="scope">
            <dict-tag :options="aig_data_policy" :value="scope.row.dataPolicy" />
          </template>
        </el-table-column>
        <el-table-column label="审计等级" align="center" width="120">
          <template #default="scope">
            <dict-tag :options="aig_audit_level" :value="scope.row.auditLevel" />
          </template>
        </el-table-column>
        <el-table-column label="人工确认点" align="center" prop="humanConfirmPoints" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button
                v-hasPermi="['aig:capability:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                v-hasPermi="['aig:capability:remove']"
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

    <!-- 新增或修改能力对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="820px" append-to-body>
      <el-form ref="capabilityFormRef" :model="form" :rules="rules" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="能力编码" prop="capabilityCode">
              <el-input v-model="form.capabilityCode" placeholder="如 talent_match（对外稳定契约）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="能力名称" prop="capabilityName">
              <el-input v-model="form.capabilityName" placeholder="请输入能力名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="业务目标" prop="bizGoal">
          <el-input v-model="form.bizGoal" type="textarea" :rows="2" placeholder="该能力服务的业务结果" />
        </el-form-item>
        <el-form-item label="需要的能力标签" prop="requiredTags">
          <el-select v-model="requiredTagList" multiple placeholder="请选择模型能力标签" style="width: 100%">
            <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
        <el-form-item label="输入 Schema" prop="inputSchema">
          <el-input
            v-model="form.inputSchema"
            type="textarea"
            :rows="4"
            placeholder='JSON 文本，如 {"fields":[{"name":"demandText","type":"string","dataLevel":"INTERNAL"}]}'
          />
          <div class="form-tip">约定允许的字段与数据等级；受限字段（手机号/证件/简历正文）禁止写入。</div>
        </el-form-item>
        <el-form-item label="输出 Schema" prop="outputSchema">
          <el-input
            v-model="form.outputSchema"
            type="textarea"
            :rows="4"
            placeholder='JSON 文本，如 {"fields":[{"name":"candidates","type":"array"}]}'
          />
          <div class="form-tip">要求结构化字段、置信度、证据与待确认项；输出不符模板时该次调用不计成功。</div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="数据策略" prop="dataPolicy">
              <el-select v-model="form.dataPolicy" placeholder="请选择数据策略" style="width: 100%">
                <el-option
                  v-for="dict in aig_data_policy"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="审计等级" prop="auditLevel">
              <el-select v-model="form.auditLevel" placeholder="请选择审计等级" style="width: 100%">
                <el-option
                  v-for="dict in aig_audit_level"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="人工确认点" prop="humanConfirmPoints">
          <el-input
            v-model="form.humanConfirmPoints"
            type="textarea"
            :rows="2"
            placeholder="必须由人工确认后才能流转的结论点"
          />
        </el-form-item>
        <el-form-item label="质量阈值" prop="qualityThreshold">
          <el-input
            v-model="form.qualityThreshold"
            type="textarea"
            :rows="2"
            placeholder="格式/完整性/可信度/超时与失败处理"
          />
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
import type { AigCapabilityForm, AigCapabilityQuery, AigCapabilityVO } from '@/api/aigov/capability/types';
import {
  addCapability,
  delCapability,
  getCapability,
  listCapability,
  updateCapability
} from '@/api/aigov/capability';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'AigCapability' });

const { aig_data_policy, aig_audit_level, sys_normal_disable } = toRefs<any>(
  useDict('aig_data_policy', 'aig_audit_level', 'sys_normal_disable')
);

/** 模型能力标签字典（与后端 AigCapability.required_tags 约定一致） */
const tagOptions = ['TEXT', 'VISION', 'OCR', 'IMAGE', 'VIDEO', 'EMBEDDING', 'RERANK', 'AGENT'];

const capabilityList = ref<AigCapabilityVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const { ids, single, multiple, handleSelectionChange } = useTableSelection<AigCapabilityVO>(item => item.capabilityId!);
const total = ref(0);
const capabilityFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();

const initFormData: AigCapabilityForm = {
  capabilityId: undefined,
  capabilityCode: '',
  capabilityName: '',
  bizGoal: '',
  requiredTags: '',
  inputSchema: '',
  outputSchema: '',
  dataPolicy: 'LOCAL_FIRST',
  humanConfirmPoints: '',
  qualityThreshold: '',
  auditLevel: 'SUMMARY',
  status: '0',
  remark: ''
};

/** JSON 文本域校验：允许为空，非空时必须能解析为 JSON 对象 */
const validateJsonText = (_rule: any, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback();
    return;
  }
  try {
    const parsed = JSON.parse(value);
    if (parsed === null || typeof parsed !== 'object') {
      callback(new Error('必须是 JSON 对象或数组'));
      return;
    }
    callback();
  } catch {
    callback(new Error('不是合法的 JSON 文本'));
  }
};

const data = reactive<PageData<AigCapabilityForm, AigCapabilityQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    capabilityCode: '',
    capabilityName: '',
    dataPolicy: undefined,
    auditLevel: undefined,
    status: undefined,
    params: {}
  },
  rules: {
    capabilityCode: [{ required: true, message: '能力编码不能为空', trigger: 'blur' }],
    capabilityName: [{ required: true, message: '能力名称不能为空', trigger: 'blur' }],
    dataPolicy: [{ required: true, message: '数据策略不能为空', trigger: 'change' }],
    auditLevel: [{ required: true, message: '审计等级不能为空', trigger: 'change' }],
    inputSchema: [{ validator: validateJsonText, trigger: 'blur' }],
    outputSchema: [{ validator: validateJsonText, trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<AigCapabilityForm, AigCapabilityQuery>>(data);
const { dialog, resetForm, openDialog, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: capabilityFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 能力标签在表单中以数组呈现，落库时按逗号拼接 */
const requiredTagList = computed<string[]>({
  get: () => (form.value.requiredTags ? form.value.requiredTags.split(',').filter(Boolean) : []),
  set: val => {
    form.value.requiredTags = val.join(',');
  }
});

const splitTags = (tags?: string) => (tags ? tags.split(',').filter(Boolean) : []);

/** 查询能力目录列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listCapability(queryParams.value);
    capabilityList.value = res.data?.rows || [];
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
  openDialog('新增能力');
};

/** 修改按钮操作 */
const handleUpdate = async (row?: Partial<AigCapabilityVO>) => {
  resetForm();
  const capabilityId = row?.capabilityId || ids.value[0];
  const res = await getCapability(capabilityId!);
  Object.assign(form.value, res.data);
  showDialog('修改能力');
};

/** 提交按钮 */
const submitForm = () => {
  capabilityFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      form.value.capabilityId ? await updateCapability(form.value) : await addCapability(form.value);
      modal.msgSuccess('操作成功');
      closeDialog();
      await getList();
    }
  });
};

/** 删除按钮操作 */
const handleDelete = async (row?: Partial<AigCapabilityVO>) => {
  const capabilityIds = row?.capabilityId || ids.value;
  await modal.confirm('是否确认删除能力编号为"' + capabilityIds + '"的数据项？');
  await delCapability(capabilityIds);
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

.tag-item {
  margin: 2px 4px 2px 0;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}
</style>
