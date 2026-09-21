<template>
  <div class="p-2 app-container hrtalent-job-page">
    <PageHeading title="招聘管理" subtitle="岗位需求发布、招聘负责人与面试官分配" module="hrtalent" />

    <!-- 岗位检索 -->
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>岗位检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="岗位编号" prop="jobNo">
            <el-input v-model="queryParams.jobNo" placeholder="请输入岗位编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="岗位名称" prop="jobName">
            <el-input v-model="queryParams.jobName" placeholder="请输入岗位名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="公司ID" prop="companyDeptId">
            <el-input
              v-model="queryParams.companyDeptId"
              placeholder="公司（部门）ID"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="用工部门ID" prop="useDeptId">
            <el-input v-model="queryParams.useDeptId" placeholder="用工部门ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="岗位状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
              <el-option v-for="item in JOB_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="招聘形式" prop="recruitMode">
            <el-select v-model="queryParams.recruitMode" placeholder="请选择招聘形式" clearable style="width: 150px">
              <el-option v-for="dict in recruit_mode" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="工作城市" prop="workCity">
            <el-input v-model="queryParams.workCity" placeholder="请输入工作城市" clearable @keyup.enter="handleQuery" />
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
            <span class="panel-kicker">Recruit Job</span>
            <h3>岗位需求</h3>
            <p>
              共 {{ total }} 条记录；岗位状态使用 draft/open/paused/closed 稳定编码（P1 字典中暂无岗位状态字典类型，页面本地渲染）。
              负责人与面试官分配走「分配」入口。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['recruit:job:add']" type="primary" icon="Plus" @click="handleAdd">新增岗位</el-button>
            <el-button icon="Refresh" plain @click="getList">刷新</el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="jobList">
        <el-table-column label="岗位编号" align="center" prop="jobNo" width="170" show-overflow-tooltip />
        <el-table-column label="岗位名称" align="center" prop="jobName" width="160" show-overflow-tooltip />
        <el-table-column label="公司" align="center" width="150" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.companyName || scope.row.companyDeptId || '-' }}</template>
        </el-table-column>
        <el-table-column label="用工部门" align="center" width="140" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.useDeptName || scope.row.useDeptId || '-' }}</template>
        </el-table-column>
        <el-table-column label="工作城市" align="center" prop="workCity" width="110" show-overflow-tooltip />
        <el-table-column label="招聘人数" align="center" prop="recruitCount" width="90" />
        <el-table-column label="薪资范围" align="center" width="150">
          <template #default="scope">{{ salaryText(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="招聘形式" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="recruit_mode" :value="scope.row.recruitMode" />
          </template>
        </el-table-column>
        <el-table-column label="招聘期限(天)" align="center" prop="standardDays" width="110">
          <template #default="scope">{{ scope.row.standardDays ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="负责人" align="center" width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.ownerName || scope.row.ownerId || '-' }}</template>
        </el-table-column>
        <el-table-column label="协助人 / 面试官" align="center" width="200" show-overflow-tooltip>
          <template #default="scope">{{ assigneeText(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="发布日期" align="center" prop="publishDate" width="110">
          <template #default="scope">{{ scope.row.publishDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="岗位状态" align="center" width="100">
          <template #default="scope">
            <el-tag :type="jobStatusTagType(scope.row.status)">{{ jobStatusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="编辑" placement="top">
              <el-button
                v-hasPermi="['recruit:job:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="分配负责人/面试官" placement="top">
              <el-button
                v-hasPermi="['recruit:job:assign']"
                link
                type="primary"
                icon="User"
                @click="openAssign(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip v-if="scope.row.status !== 'closed'" content="关闭岗位" placement="top">
              <el-button
                v-hasPermi="['recruit:job:close']"
                link
                type="danger"
                icon="CircleClose"
                @click="handleJobAction(scope.row, 'close')"
              ></el-button>
            </el-tooltip>
            <el-tooltip v-else content="复开岗位" placement="top">
              <el-button
                v-hasPermi="['recruit:job:close']"
                link
                type="success"
                icon="RefreshLeft"
                @click="handleJobAction(scope.row, 'reopen')"
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

    <!-- 新增/编辑岗位 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="900px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="岗位职责与任职要求为长文本，保存时由后端执行安全清理；薪资低值不得大于高值。"
      />
      <el-form ref="jobFormRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="岗位名称" prop="jobName">
              <el-input v-model="form.jobName" placeholder="请输入岗位名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="招聘人数" prop="recruitCount">
              <el-input-number v-model="form.recruitCount" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="公司部门ID" prop="companyDeptId">
              <el-input v-model="form.companyDeptId" placeholder="平台部门ID（bigint，按字符串提交）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公司名称" prop="companyName">
              <el-input v-model="form.companyName" placeholder="公司名称快照" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用工部门ID" prop="useDeptId">
              <el-input v-model="form.useDeptId" placeholder="可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用工部门名称" prop="useDeptName">
              <el-input v-model="form.useDeptName" placeholder="可为空" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="来源需求ID" prop="demandId">
              <el-input v-model="form.demandId" placeholder="可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联计划任务ID" prop="planItemId">
              <el-input v-model="form.planItemId" placeholder="可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="岗位负责人ID" prop="ownerId">
              <el-input v-model="form.ownerId" placeholder="可为空" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="岗位职级" prop="jobLevel">
              <el-input v-model="form.jobLevel" placeholder="字典编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="工作城市" prop="workCity">
              <el-input v-model="form.workCity" placeholder="请输入工作城市" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="招聘形式" prop="recruitMode">
              <el-select v-model="form.recruitMode" placeholder="请选择招聘形式" clearable style="width: 100%">
                <el-option v-for="dict in recruit_mode" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="薪资低值" prop="salaryMin">
              <el-input-number v-model="form.salaryMin" :min="0" :precision="2" :step="500" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="薪资高值" prop="salaryMax">
              <el-input-number v-model="form.salaryMax" :min="0" :precision="2" :step="500" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="薪资周期" prop="salaryPeriod">
              <el-select v-model="form.salaryPeriod" placeholder="请选择薪资周期" clearable style="width: 100%">
                <el-option v-for="item in SALARY_PERIODS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="招聘期限标准天数" prop="standardDays">
              <el-input-number v-model="form.standardDays" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发布日期" prop="publishDate">
              <el-date-picker
                v-model="form.publishDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择发布日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="岗位职责" prop="responsibility">
          <el-input v-model="form.responsibility" type="textarea" :rows="4" placeholder="请输入岗位职责" />
        </el-form-item>
        <el-form-item label="任职要求" prop="qualification">
          <el-input v-model="form.qualification" type="textarea" :rows="4" placeholder="请输入任职要求" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 分配负责人/面试官 -->
    <el-dialog v-model="assignDialog.visible" title="分配负责人 / 面试官" width="620px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="负责人必填；协助人可添加多个（输入用户ID后回车即可）。协助人读回与提交均为 ID 数组，收发对称。"
      />
      <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-width="120px">
        <el-form-item label="岗位编号">
          <el-input :model-value="assignForm.jobNo" disabled />
        </el-form-item>
        <el-form-item label="招聘负责人ID" prop="ownerId">
          <el-input v-model="assignForm.ownerId" placeholder="请输入负责人用户ID" />
        </el-form-item>
        <el-form-item label="协助人ID" prop="assistantIds">
          <el-select
            v-model="assignForm.assistantIds"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="输入用户ID后回车添加，可添加多个"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="一面面试官ID" prop="firstInterviewerId">
          <el-input v-model="assignForm.firstInterviewerId" placeholder="可为空" />
        </el-form-item>
        <el-form-item label="二面面试官ID" prop="secondInterviewerId">
          <el-input v-model="assignForm.secondInterviewerId" placeholder="可为空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="assignSubmitting" @click="submitAssign">确 定</el-button>
          <el-button @click="assignDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { addJob, assignJob, actionJob, getJob, listJob, updateJob } from '@/api/hrtalent/job';
import type { HrJobForm, HrJobQuery, HrJobVO, JobAction } from '@/api/hrtalent/job/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';

defineOptions({ name: 'HrJob' });

const { recruit_mode } = toRefs<any>(useDict('recruit_mode'));

/**
 * 岗位状态本地映射：hr_recruit_job.status 为 draft/open/paused/closed 稳定编码，
 * P1 未提供 recruit_job_status 字典类型，故此处本地渲染（待 P1 补字典后改回 dict-tag）。
 */
const JOB_STATUS_OPTIONS = [
  { value: 'draft', label: '草稿' },
  { value: 'open', label: '招聘中' },
  { value: 'paused', label: '暂停' },
  { value: 'closed', label: '已关闭' }
];

/** 薪资周期编码（DDL 注释：month/year/day 等稳定编码） */
const SALARY_PERIODS = [
  { value: 'month', label: '月薪' },
  { value: 'year', label: '年薪' },
  { value: 'day', label: '日薪' }
];

/** 岗位状态中文 */
const jobStatusLabel = (status?: string) =>
  JOB_STATUS_OPTIONS.find(item => item.value === status)?.label || status || '-';

/** 岗位状态标签类型 */
const jobStatusTagType = (status?: string): 'primary' | 'success' | 'info' | 'warning' | 'danger' => {
  switch (status) {
    case 'open':
      return 'primary';
    case 'paused':
      return 'warning';
    case 'closed':
      return 'info';
    default:
      return 'info';
  }
};

/** 协助人/面试官展示：优先中文昵称（@Translation 回填），缺失时回退 ID 数组 */
const assigneeText = (row: Partial<HrJobVO>) => {
  const parts: string[] = [];
  if (row.assistantNames || row.assistantIds?.length) {
    parts.push(`协助：${row.assistantNames || (row.assistantIds || []).join(',')}`);
  }
  if (row.firstInterviewerName || row.firstInterviewerId) {
    parts.push(`一面：${row.firstInterviewerName || row.firstInterviewerId}`);
  }
  if (row.secondInterviewerName || row.secondInterviewerId) {
    parts.push(`二面：${row.secondInterviewerName || row.secondInterviewerId}`);
  }
  return parts.length ? parts.join('；') : '-';
};

/** 薪资范围展示 */
const salaryText = (row: Partial<HrJobVO>) => {
  if (row.salaryMin === null || row.salaryMin === undefined) {
    if (row.salaryMax === null || row.salaryMax === undefined) {
      return '-';
    }
    return `≤ ${row.salaryMax}`;
  }
  if (row.salaryMax === null || row.salaryMax === undefined) {
    return `≥ ${row.salaryMin}`;
  }
  return `${row.salaryMin} ~ ${row.salaryMax}`;
};

const jobList = ref<HrJobVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const submitting = ref(false);
const jobFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();

const initFormData: HrJobForm = {
  jobId: undefined,
  demandId: undefined,
  planItemId: undefined,
  jobName: '',
  companyDeptId: undefined,
  companyName: '',
  useDeptId: undefined,
  useDeptName: '',
  jobLevel: '',
  workCity: '',
  responsibility: '',
  qualification: '',
  salaryMin: undefined,
  salaryMax: undefined,
  salaryPeriod: undefined,
  recruitMode: undefined,
  standardDays: undefined,
  recruitCount: 1,
  ownerId: undefined,
  publishDate: undefined,
  version: undefined,
  remark: ''
};

/** 薪资低值不得大于高值 */
const validateSalary = (_rule: any, value: any, callback: any) => {
  const min = value;
  const max = form.value.salaryMax;
  if (min === undefined || min === null || max === undefined || max === null) {
    callback();
    return;
  }
  if (Number(min) > Number(max)) {
    callback(new Error('薪资低值不得大于高值'));
    return;
  }
  callback();
};

const data = reactive<PageData<HrJobForm, HrJobQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    jobNo: undefined,
    jobName: undefined,
    companyDeptId: undefined,
    useDeptId: undefined,
    status: undefined,
    recruitMode: undefined,
    workCity: undefined,
    params: {}
  },
  rules: {
    jobName: [{ required: true, message: '岗位名称不能为空', trigger: 'blur' }],
    companyDeptId: [{ required: true, message: '公司（部门）ID 不能为空', trigger: 'blur' }],
    recruitCount: [{ required: true, message: '招聘人数不能为空', trigger: 'change' }],
    salaryMin: [{ validator: validateSalary, trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<HrJobForm, HrJobQuery>>(data);
const { dialog, resetForm, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: jobFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 查询岗位列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listJob(queryParams.value);
    jobList.value = res.data?.rows || [];
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

/** 新增岗位 */
const handleAdd = () => {
  resetForm();
  form.value.jobId = undefined;
  form.value.version = undefined;
  showDialog('新增岗位需求');
};

/** 编辑岗位：取详情回显，避免列表字段被裁剪 */
const handleUpdate = async (row: Partial<HrJobVO>) => {
  resetForm();
  const res = await getJob(row.jobId!);
  const detailRow = res.data || row;
  Object.assign(form.value, initFormData);
  Object.assign(form.value, {
    jobId: detailRow.jobId,
    demandId: detailRow.demandId,
    planItemId: detailRow.planItemId,
    jobName: detailRow.jobName,
    companyDeptId: detailRow.companyDeptId,
    companyName: detailRow.companyName,
    useDeptId: detailRow.useDeptId,
    useDeptName: detailRow.useDeptName,
    jobLevel: detailRow.jobLevel,
    workCity: detailRow.workCity,
    responsibility: detailRow.responsibility,
    qualification: detailRow.qualification,
    salaryMin: detailRow.salaryMin,
    salaryMax: detailRow.salaryMax,
    salaryPeriod: detailRow.salaryPeriod,
    recruitMode: detailRow.recruitMode,
    standardDays: detailRow.standardDays,
    recruitCount: detailRow.recruitCount,
    ownerId: detailRow.ownerId,
    publishDate: detailRow.publishDate,
    version: detailRow.version,
    remark: detailRow.remark
  });
  showDialog('修改岗位需求');
};

/** 提交新增/修改 */
const submitForm = () => {
  jobFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    submitting.value = true;
    try {
      if (form.value.jobId) {
        await updateJob({ ...form.value });
        modal.msgSuccess('修改成功');
      } else {
        await addJob({ ...form.value });
        modal.msgSuccess('新增成功');
      }
      closeDialog();
      await getList();
    } finally {
      submitting.value = false;
    }
  });
};

/** 关闭 / 复开岗位 */
const handleJobAction = async (row: HrJobVO, action: JobAction) => {
  const label = action === 'close' ? '关闭' : '复开';
  try {
    await modal.confirm(`是否确认${label}岗位「${row.jobName || row.jobNo}」？`);
  } catch {
    return;
  }
  await actionJob(row.jobId!, action);
  modal.msgSuccess(`${label}成功`);
  await getList();
};

// ------------------------------------------------------------------ 分配

const assignDialog = reactive({ visible: false });
const assignSubmitting = ref(false);
const assignFormRef = ref<ElFormInstance>();
/** 协助人控件直接用 ID 数组，与后端读回/提交形态一致（收发对称） */
const assignForm = ref<{
  jobId: string | number;
  jobNo?: string;
  ownerId?: string | number;
  assistantIds?: (string | number)[];
  firstInterviewerId?: string | number;
  secondInterviewerId?: string | number;
}>({
  jobId: '',
  jobNo: '',
  ownerId: undefined,
  assistantIds: [],
  firstInterviewerId: undefined,
  secondInterviewerId: undefined
});
const assignRules = {
  ownerId: [{ required: true, message: '招聘负责人不能为空', trigger: 'blur' }]
};

/** 打开分配弹窗：assistantIds 读回即数组，直接回显（空值 → 空数组） */
const openAssign = (row: HrJobVO) => {
  assignForm.value = {
    jobId: row.jobId!,
    jobNo: row.jobNo,
    ownerId: row.ownerId,
    assistantIds: Array.isArray(row.assistantIds) ? [...row.assistantIds] : [],
    firstInterviewerId: row.firstInterviewerId,
    secondInterviewerId: row.secondInterviewerId
  };
  assignDialog.visible = true;
};

/** 提交分配：assistantIds 按后端 RecruitJobAssignBo 传数组 Long[] */
const submitAssign = () => {
  assignFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    assignSubmitting.value = true;
    try {
      await assignJob({
        jobId: assignForm.value.jobId,
        ownerId: assignForm.value.ownerId,
        assistantIds: assignForm.value.assistantIds ?? [],
        firstInterviewerId: assignForm.value.firstInterviewerId,
        secondInterviewerId: assignForm.value.secondInterviewerId
      });
      modal.msgSuccess('分配成功');
      assignDialog.visible = false;
      await getList();
    } finally {
      assignSubmitting.value = false;
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
</style>
