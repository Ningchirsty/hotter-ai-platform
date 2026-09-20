<template>
  <div class="p-2 app-container talent-export-page">
    <PageHeading title="人才管理" subtitle="档案、附件与业务记录，在同一个工作空间有序连接" />
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
          <el-form-item label="任务名称" prop="taskName">
            <el-input v-model="queryParams.taskName" placeholder="请输入任务名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="任务状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择任务状态" clearable style="width: 180px">
              <el-option v-for="item in exportStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
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
            <span class="panel-kicker">Export Center</span>
            <h3>Excel 导出任务</h3>
            <p>共 {{ total }} 条记录；任务异步生成，文件 24 小时后过期，仅可下载本人或授权范围内的数据。</p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['talent:export:create']" type="primary" plain icon="Plus" @click="handleCreate">
              新建导出任务
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="exportList">
        <el-table-column label="任务名称" align="center" prop="taskName" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="110">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">
              {{ scope.row.statusLabel || statusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件名" align="center" prop="fileName" show-overflow-tooltip />
        <el-table-column label="行数" align="center" prop="rowCount" width="90" />
        <el-table-column label="创建人" align="center" prop="createByName" width="110" />
        <el-table-column label="创建时间" align="center" width="180">
          <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" align="center" width="180">
          <template #default="scope">{{ parseTime(scope.row.finishTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="过期时间" align="center" width="180">
          <template #default="scope">{{ parseTime(scope.row.expireTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="错误摘要" align="center" prop="errorSummary" show-overflow-tooltip />
        <el-table-column label="操作" width="110" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              v-hasPermi="['talent:export:download']"
              link
              type="primary"
              icon="Download"
              :disabled="scope.row.status !== 'SUCCESS' || scope.row.expired === true"
              @click="handleDownload(scope.row)"
            ></el-button>
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

    <el-dialog v-model="dialog.visible" title="新建导出任务" width="720px" append-to-body>
      <el-form ref="createFormRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="任务名称" prop="taskName">
              <el-input v-model="form.taskName" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="请输入姓名" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="人才编号" prop="talentNo">
              <el-input v-model="form.talentNo" placeholder="请输入人才编号" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="归属区域" prop="regionCode">
              <el-select v-model="form.regionCode" placeholder="可选，收窄筛选" clearable style="width: 100%">
                <el-option v-for="dict in tl_region" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="人才状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option v-for="dict in tl_talent_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学历" prop="education">
              <el-select v-model="form.education" placeholder="请选择学历" clearable style="width: 100%">
                <el-option v-for="dict in tl_education" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源" prop="source">
              <el-select v-model="form.source" placeholder="请选择来源" clearable style="width: 100%">
                <el-option v-for="dict in tl_source" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="意向岗位" prop="position">
              <el-input v-model="form.position" placeholder="请输入意向岗位" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机后四位" prop="phoneTail4">
              <el-input v-model="form.phoneTail4" maxlength="4" placeholder="请输入手机号后四位" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系日期起" prop="contactDateStart">
              <el-date-picker
                v-model="form.contactDateStart"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择开始日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系日期止" prop="contactDateEnd">
              <el-date-picker
                v-model="form.contactDateEnd"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择结束日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitting" @click="submitForm">创 建</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { ExportCreateForm, ExportTaskQuery, ExportTaskVO } from '@/api/talent/export/types';
import { createExport, downloadExport, listExport } from '@/api/talent/export';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { blobValidate, parseTime } from '@/utils/ruoyi';
import { saveBlob } from '@/utils/save';

defineOptions({ name: 'TalentExport' });

const { tl_region, tl_education, tl_talent_status, tl_source } = toRefs<any>(
  useDict('tl_region', 'tl_education', 'tl_talent_status', 'tl_source')
);

const exportStatusOptions = [
  { value: 'PENDING', label: '待处理' },
  { value: 'RUNNING', label: '生成中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'EXPIRED', label: '已过期' }
];
const statusLabel = (value?: string) => exportStatusOptions.find(item => item.value === value)?.label || value || '-';
const statusTagType = (value?: string) => {
  if (value === 'SUCCESS') return 'success';
  if (value === 'FAILED') return 'danger';
  if (value === 'RUNNING') return 'warning';
  return 'info';
};

const exportList = ref<ExportTaskVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const submitting = ref(false);
const queryFormRef = ref<ElFormInstance>();
const createFormRef = ref<ElFormInstance>();

const initFormData: ExportCreateForm = {
  taskName: '',
  name: '',
  talentNo: '',
  phoneTail4: '',
  regionCode: undefined,
  status: undefined,
  education: undefined,
  position: '',
  source: undefined,
  contactDateStart: undefined,
  contactDateEnd: undefined
};

const data = reactive<PageData<ExportCreateForm, ExportTaskQuery>>({
  form: { ...initFormData },
  queryParams: { pageNum: 1, pageSize: 10, taskName: '', status: undefined },
  rules: {
    taskName: [{ required: true, message: '任务名称不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<ExportCreateForm, ExportTaskQuery>>(data);
const { dialog, resetForm, openDialog, closeDialog } = useFormDialog({
  form,
  formRef: createFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 查询导出任务列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listExport(queryParams.value);
    exportList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const handleCreate = () => {
  openDialog('新建导出任务');
};

const submitForm = () => {
  createFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    submitting.value = true;
    try {
      await createExport(form.value);
      modal.msgSuccess('导出任务已创建，请稍后刷新查看');
      closeDialog();
      resetForm();
      await getList();
    } finally {
      submitting.value = false;
    }
  });
};

/** 下载导出文件：后端返回二进制流 */
const handleDownload = async (row: ExportTaskVO) => {
  if (!row.exportId) return;
  const data: any = await downloadExport(row.exportId);
  const blob = data instanceof Blob ? data : new Blob([data]);
  if (!blobValidate(blob)) {
    const text = await blob.text();
    let msg = '下载失败';
    try {
      msg = JSON.parse(text).msg || msg;
    } catch {
      msg = text || msg;
    }
    modal.msgError(msg);
    return;
  }
  saveBlob(blob, row.fileName || `talent-export-${row.exportId}.xlsx`);
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;
</style>
