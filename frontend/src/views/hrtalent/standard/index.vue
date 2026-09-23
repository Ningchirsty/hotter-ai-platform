<template>
  <div class="p-2 app-container hrtalent-standard-page">
    <PageHeading
      title="招聘期限标准"
      subtitle="岗位的招聘周期基线：某公司某岗位从启动招聘到人到岗应当用多少天。公司留空表示集团通用"
      module="hrtalent"
    />

    <el-alert
      class="page-alert"
      type="info"
      :closable="false"
      show-icon
      title="批量录入请用「批量导入」：模板按「公司名称」填写，系统自动匹配组织架构里的公司；单条录入需自行填公司部门ID。业务键为「岗位名称 + 公司 + 生效日期」，同键重复时导入会覆盖其天数并给出提示。"
    />

    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>标准检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="岗位名称" prop="jobName">
            <el-input
              v-model="queryParams.jobName"
              placeholder="岗位名称，支持模糊"
              clearable
              style="width: 200px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="公司部门ID" prop="companyDeptId">
            <el-input
              v-model="queryParams.companyDeptId"
              placeholder="平台部门ID"
              clearable
              style="width: 160px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
              <el-option label="生效" value="active" />
              <el-option label="停用" value="inactive" />
            </el-select>
          </el-form-item>
          <el-form-item label="仅集团通用">
            <el-switch v-model="queryParams.groupOnly" />
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
            <span class="panel-kicker">Recruit Standard</span>
            <h3>招聘期限标准</h3>
            <p>共 {{ total }} 条；同「岗位 + 公司 + 生效日期」只应有一条。</p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['recruit:import:template']"
              plain
              icon="Download"
              @click="handleDownloadTemplate"
            >
              下载模板
            </el-button>
            <el-button
              v-hasPermi="['recruit:import:upload']"
              type="success"
              plain
              icon="Upload"
              @click="importVisible = true"
            >
              批量导入
            </el-button>
            <el-button v-hasPermi="['recruit:standard:add']" type="primary" plain icon="Plus" @click="handleAdd">
              新增
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border :data="list">
        <el-table-column label="岗位名称" align="center" prop="jobName" min-width="160" show-overflow-tooltip />
        <el-table-column label="适用公司" align="center" min-width="150" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.groupWide" class="muted">集团通用</span>
            <span v-else>{{ scope.row.companyName || scope.row.companyDeptId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="期限（天）" align="center" prop="standardDays" width="110" />
        <el-table-column label="生效日期" align="center" prop="effectiveDate" width="120">
          <template #default="scope">{{ scope.row.effectiveDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="失效日期" align="center" prop="expiryDate" width="120">
          <template #default="scope">{{ scope.row.expiryDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'" size="small">
              {{ scope.row.statusLabel || scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button
                v-hasPermi="['recruit:standard:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                v-hasPermi="['recruit:standard:edit']"
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
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <!-- 新增/修改 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="620px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="岗位名称" prop="jobName">
          <el-input v-model="form.jobName" placeholder="如 前端开发工程师" />
        </el-form-item>
        <el-form-item label="公司部门ID" prop="companyDeptId">
          <el-input v-model="form.companyDeptId" placeholder="留空表示集团通用（所有公司适用）" />
        </el-form-item>
        <el-form-item label="公司名称" prop="companyName">
          <el-input v-model="form.companyName" placeholder="公司名称快照，可留空" />
        </el-form-item>
        <el-form-item label="期限标准天数" prop="standardDays">
          <el-input-number v-model="form.standardDays" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker
            v-model="form.effectiveDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="可留空"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="失效日期" prop="expiryDate">
          <el-date-picker
            v-model="form.expiryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="可留空；不得早于生效日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="active">生效</el-radio>
            <el-radio value="inactive">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可留空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="saving" @click="submitForm">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 批量导入（模板 → 预检 → 确认） -->
    <RecruitImportDialog
      v-model="importVisible"
      title="批量导入招聘期限标准"
      base-path="/recruit/standards"
      template-name="招聘期限标准导入模板.xlsx"
      @done="getList"
    />
  </div>
</template>

<script setup lang="ts">
import type { RecruitStandardForm, RecruitStandardQuery, RecruitStandardVO } from '@/api/hrtalent/standard/types';
import { addStandard, delStandard, getStandard, listStandard, updateStandard } from '@/api/hrtalent/standard';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { download } from '@/utils/request';
import RecruitImportDialog from '../components/RecruitImportDialog.vue';

defineOptions({ name: 'HrRecruitStandard' });

const list = ref<RecruitStandardVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();
const saving = ref(false);
const importVisible = ref(false);

const dialog = reactive<{ visible: boolean; title: string }>({ visible: false, title: '新增招聘期限标准' });

const initForm = (): RecruitStandardForm => ({
  standardId: undefined,
  jobName: '',
  companyDeptId: undefined,
  companyName: '',
  standardDays: 30,
  effectiveDate: undefined,
  expiryDate: undefined,
  status: 'active',
  remark: ''
});
const form = ref<RecruitStandardForm>(initForm());

const rules = {
  jobName: [{ required: true, message: '岗位名称不能为空', trigger: 'blur' }],
  standardDays: [{ required: true, message: '期限标准天数不能为空', trigger: 'blur' }]
};

const data = reactive<{ queryParams: RecruitStandardQuery }>({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    jobName: '',
    companyDeptId: undefined,
    status: undefined,
    groupOnly: false
  }
});
const { queryParams } = toRefs(data);
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 查询列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listStandard(queryParams.value);
    list.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 搜索 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 下载模板 */
const handleDownloadTemplate = () => {
  download('/recruit/standards/importTemplate', {}, '招聘期限标准导入模板.xlsx');
};

/** 新增 */
const handleAdd = () => {
  form.value = initForm();
  dialog.title = '新增招聘期限标准';
  dialog.visible = true;
};

/** 修改 */
const handleUpdate = async (row: RecruitStandardVO) => {
  const res = await getStandard(row.standardId!);
  form.value = { ...(res.data as RecruitStandardForm), status: res.data?.status || 'active' };
  dialog.title = '修改招聘期限标准';
  dialog.visible = true;
};

/** 提交 */
const submitForm = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    saving.value = true;
    try {
      if (form.value.standardId) {
        await updateStandard(form.value);
      } else {
        await addStandard(form.value);
      }
      modal.msgSuccess('操作成功');
      dialog.visible = false;
      await getList();
    } finally {
      saving.value = false;
    }
  });
};

/** 删除 */
const handleDelete = async (row: RecruitStandardVO) => {
  await modal.confirm(`是否确认删除「${row.jobName}」的招聘期限标准？`);
  await delStandard(row.standardId!);
  await getList();
  modal.msgSuccess('删除成功');
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
.hrtalent-standard-page {
  .muted {
    color: var(--el-text-color-secondary);
  }

  .dialog-footer {
    text-align: right;
  }
}
</style>
