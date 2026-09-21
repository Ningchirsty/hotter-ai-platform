<template>
  <div class="p-2 app-container hrtalent-interview-page">
    <PageHeading
      title="面试管理"
      subtitle="面试安排、改期与取消（保留操作记录）、面试官分别提交个人反馈、我的待办"
      module="hrtalent"
    />

    <!-- 面试检索 -->
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>面试检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="应聘记录ID" prop="applicationId">
            <el-input v-model="queryParams.applicationId" placeholder="应聘记录ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="轮次" prop="roundNo">
            <el-input-number v-model="queryParams.roundNo" :min="1" :step="1" placeholder="轮次" style="width: 130px" />
          </el-form-item>
          <el-form-item label="面试状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 150px">
              <el-option v-for="dict in recruit_interview_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="面试结果" prop="result">
            <el-select v-model="queryParams.result" placeholder="请选择结果" clearable style="width: 150px">
              <el-option v-for="dict in recruit_interview_result" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="面试方式" prop="method">
            <el-select v-model="queryParams.method" placeholder="请选择方式" clearable style="width: 140px">
              <el-option v-for="dict in recruit_interview_method" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="面试官ID" prop="interviewerUserId">
            <el-input
              v-model="queryParams.interviewerUserId"
              placeholder="面试官用户ID"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="计划时间">
            <el-date-picker
              v-model="scheduleRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              range-separator="-"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 340px"
            />
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
            <span class="panel-kicker">Interview</span>
            <h3>{{ viewMode === 'list' ? '面试记录' : '我的面试待办' }}</h3>
            <p>
              多人面试分别保存个人意见（hr_recruit_interviewer.feedback），汇总结论保存在面试记录上。
              改期与取消均保留操作记录；面试结果由面试官反馈驱动。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-radio-group v-model="viewMode" @change="handleViewModeChange">
              <el-radio-button value="list">面试列表</el-radio-button>
              <el-radio-button value="todos">我的待办</el-radio-button>
            </el-radio-group>
            <el-button v-hasPermi="['recruit:interview:schedule']" type="primary" icon="Plus" @click="handleAdd">
              安排面试
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="tableData">
        <el-table-column label="面试ID" align="center" prop="interviewId" width="110" />
        <el-table-column label="应聘编号" align="center" prop="applicationNo" width="170" show-overflow-tooltip />
        <el-table-column label="候选人" align="center" prop="candidateName" width="110" show-overflow-tooltip />
        <el-table-column label="岗位" align="center" prop="jobName" width="150" show-overflow-tooltip />
        <el-table-column label="轮次" align="center" width="80">
          <template #default="scope">第 {{ scope.row.roundNo ?? '-' }} 轮</template>
        </el-table-column>
        <el-table-column label="计划面试时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.scheduleTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="方式" align="center" width="100">
          <template #default="scope">
            <dict-tag v-if="scope.row.method" :options="recruit_interview_method" :value="scope.row.method" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="地点/链接" align="center" prop="location" width="180" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="110">
          <template #default="scope">
            <dict-tag v-if="scope.row.status" :options="recruit_interview_status" :value="scope.row.status" />
            <span v-else>{{ scope.row.statusLabel || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" align="center" width="110">
          <template #default="scope">
            <dict-tag v-if="scope.row.result" :options="recruit_interview_result" :value="scope.row.result" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="汇总评分" align="center" prop="score" width="100" />
        <el-table-column label="面试官" align="center" width="180" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.interviewerNames || '-' }}</template>
        </el-table-column>
        <el-table-column label="反馈时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.feedbackTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="取消原因" align="center" prop="cancelReason" width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="210" align="center" class-name="small-padding fixed-width" fixed="right">
          <template #default="scope">
            <el-tooltip content="详情" placement="top">
              <el-button
                v-hasPermi="['recruit:interview:list']"
                link
                type="primary"
                icon="Search"
                @click="handleDetail(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="反馈" placement="top">
              <el-button
                v-hasPermi="['recruit:interview:feedback']"
                link
                type="primary"
                icon="Edit"
                @click="openFeedback(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-dropdown trigger="click" @command="cmd => handleMore(scope.row, cmd)">
              <el-button link type="primary" icon="More"></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-hasPermi="['recruit:interview:schedule']" command="reschedule">改期</el-dropdown-item>
                  <el-dropdown-item v-hasPermi="['recruit:interview:cancel']" command="cancel" divided>取消面试</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="viewMode === 'list' && total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <!-- 安排面试 / 改期 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="760px" append-to-body>
      <el-alert
        class="dialog-alert"
        :type="scheduleDialog.isEdit ? 'warning' : 'info'"
        :closable="false"
        show-icon
        :title="
          scheduleDialog.isEdit
            ? '改期会保留操作记录：原面试记录置为「已改期」，并新增一条有效面试记录。'
            : '同一应聘记录同一轮次只能有一条有效面试安排；进入「一面」前必须先存在面试安排。'
        "
      />
      <el-form ref="scheduleFormRef" :model="scheduleForm" :rules="scheduleRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="应聘记录ID" prop="applicationId">
              <el-input v-model="scheduleForm.applicationId" placeholder="应聘记录ID" :disabled="scheduleDialog.isEdit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面试轮次" prop="roundNo">
              <el-input-number v-model="scheduleForm.roundNo" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="计划面试时间" prop="scheduleTime">
              <el-date-picker
                v-model="scheduleForm.scheduleTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择计划面试时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实际结束时间" prop="endTime">
              <el-date-picker
                v-model="scheduleForm.endTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="可为空"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="面试方式" prop="method">
              <el-select v-model="scheduleForm.method" placeholder="请选择面试方式" clearable style="width: 100%">
                <el-option
                  v-for="dict in recruit_interview_method"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地点/线上链接" prop="location">
              <el-input v-model="scheduleForm.location" placeholder="现场地点或线上会议链接" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="面试官" prop="interviewerIds">
          <el-input
            :model-value="interviewerDisplay"
            placeholder="点击右侧按钮选择面试官（可多选，最多 20 人）"
            readonly
          />
          <el-button class="inline-btn" icon="User" @click="openInterviewerSelect">选择面试官</el-button>
        </el-form-item>
        <el-form-item v-if="scheduleDialog.isEdit" label="改期原因" prop="changeReason">
          <el-input v-model="scheduleForm.changeReason" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="scheduleForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitting" @click="submitSchedule">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 取消面试（必填原因） -->
    <el-dialog v-model="cancelDialog.visible" title="取消面试" width="520px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="取消原因必填，将写入面试记录留痕；已完成的面试不能取消。"
      />
      <el-form ref="cancelFormRef" :model="cancelForm" :rules="cancelRules" label-width="90px">
        <el-form-item label="面试ID">
          <el-input :model-value="cancelDialog.row.interviewId" disabled />
        </el-form-item>
        <el-form-item label="取消原因" prop="cancelReason">
          <el-input
            v-model="cancelForm.cancelReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请填写取消原因（必填，最长 500 字）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="cancelDialog.submitting" @click="submitCancel">确 定</el-button>
          <el-button @click="cancelDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 面试官提交反馈 -->
    <el-dialog v-model="feedbackDialog.visible" title="提交面试反馈" width="640px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="反馈按面试官分别保存：当前登录用户必须是该面试的面试官。个人意见为必填，可选同时提交汇总结论。"
      />
      <el-form ref="feedbackFormRef" :model="feedbackForm" :rules="feedbackRules" label-width="110px">
        <el-form-item label="面试记录ID">
          <el-input :model-value="feedbackDialog.row.interviewId" disabled />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="个人评分" prop="score">
              <el-input-number v-model="feedbackForm.score" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="个人结论" prop="result">
              <el-select v-model="feedbackForm.result" placeholder="请选择结论" clearable style="width: 100%">
                <el-option
                  v-for="dict in recruit_interview_result"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="个人意见" prop="feedback">
          <el-input
            v-model="feedbackForm.feedback"
            type="textarea"
            :rows="3"
            placeholder="请填写个人面试意见（必填）"
          />
        </el-form-item>
        <el-divider content-position="left">汇总结论（可选，填写后同时更新面试记录）</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="汇总评分" prop="overallScore">
              <el-input-number v-model="feedbackForm.overallScore" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="汇总结论" prop="conclusion">
          <el-input v-model="feedbackForm.conclusion" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="feedbackDialog.submitting" @click="submitFeedback">确 定</el-button>
          <el-button @click="feedbackDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 面试详情（含各面试官个人意见） -->
    <el-dialog v-model="detail.visible" title="面试详情" width="900px" append-to-body>
      <el-descriptions :column="2" border size="small" class="detail-panel">
        <el-descriptions-item label="面试ID">{{ detail.row.interviewId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="应聘编号">{{ detail.row.applicationNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="候选人">{{ detail.row.candidateName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ detail.row.jobName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="轮次">第 {{ detail.row.roundNo ?? '-' }} 轮</el-descriptions-item>
        <el-descriptions-item label="面试方式">
          <dict-tag v-if="detail.row.method" :options="recruit_interview_method" :value="detail.row.method" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="计划面试时间">{{ parseTime(detail.row.scheduleTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际结束时间">{{ parseTime(detail.row.endTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地点/链接" :span="2">{{ detail.row.location || '-' }}</el-descriptions-item>
        <el-descriptions-item label="面试状态">
          <dict-tag v-if="detail.row.status" :options="recruit_interview_status" :value="detail.row.status" />
          <span v-else>{{ detail.row.statusLabel || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="面试结果">
          <dict-tag v-if="detail.row.result" :options="recruit_interview_result" :value="detail.row.result" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="汇总评分">{{ detail.row.score ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="反馈时间">{{ parseTime(detail.row.feedbackTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="面试官" :span="2">
          {{ detail.row.interviewerNames || idsToText(detail.row.interviewerIds) }}
        </el-descriptions-item>
        <el-descriptions-item label="汇总意见" :span="2">{{ detail.row.feedback || '-' }}</el-descriptions-item>
        <el-descriptions-item label="取消原因" :span="2">{{ detail.row.cancelReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.row.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">面试官个人意见</el-divider>
      <el-table border size="small" :data="detail.row.interviewers || []">
        <el-table-column label="面试官" align="center" width="140">
          <template #default="scope">{{ scope.row.userNickName || scope.row.userName || scope.row.userId || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" align="center" prop="interviewerRole" width="100" />
        <el-table-column label="反馈状态" align="center" prop="feedbackStatus" width="110" />
        <el-table-column label="评分" align="center" prop="score" width="90" />
        <el-table-column label="反馈时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.feedbackTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="个人意见" align="center" prop="feedback" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detail.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 面试官多选 -->
    <UserSelect ref="interviewerSelectRef" :multiple="true" @confirm-call-back="handleInterviewerSelected" />
  </div>
</template>

<script setup lang="ts">
import {
  addInterview,
  cancelInterview,
  getInterview,
  listInterview,
  listMyInterviewTodos,
  rescheduleInterview,
  submitInterviewFeedback
} from '@/api/hrtalent/interview';
import type { HrInterviewFeedbackForm, HrInterviewForm, HrInterviewQuery, HrInterviewVO } from '@/api/hrtalent/interview/types';
import UserSelect from '@/components/UserSelect/index.vue';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'HrInterview' });

const { recruit_interview_status, recruit_interview_method, recruit_interview_result } = toRefs<any>(
  useDict('recruit_interview_status', 'recruit_interview_method', 'recruit_interview_result')
);

/** 视图模式：面试列表 / 我的待办 */
const viewMode = ref<'list' | 'todos'>('list');
const interviewList = ref<HrInterviewVO[]>([]);
const todoList = ref<HrInterviewVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const submitting = ref(false);
const scheduleFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();
const cancelFormRef = ref<ElFormInstance>();
const feedbackFormRef = ref<ElFormInstance>();
const interviewerSelectRef = ref<InstanceType<typeof UserSelect>>();
const scheduleRange = ref<[string, string] | null>(null);

/** 当前表格数据源 */
const tableData = computed(() => (viewMode.value === 'list' ? interviewList.value : todoList.value));

/** 用户ID数组转展示串 */
const idsToText = (ids?: (string | number)[]) => (ids && ids.length ? ids.join(', ') : '');

/** 面试官选择弹窗的临时状态（安排/改期共用） */
const scheduleDialog = reactive<{
  isEdit: boolean;
  names: string;
  row: Partial<HrInterviewVO>;
}>({ isEdit: false, names: '', row: {} });

/** 面试官昵称展示（优先用后端翻译的逗号串，其次用已选用户ID） */
const interviewerDisplay = computed(() => scheduleDialog.names || idsToText(scheduleForm.value.interviewerIds));

const initFormData: HrInterviewForm = {
  interviewId: undefined,
  applicationId: undefined,
  roundNo: 1,
  scheduleTime: undefined,
  endTime: undefined,
  method: undefined,
  location: '',
  interviewerIds: [],
  changeReason: undefined,
  remark: ''
};

/** 面试官至少一名 */
const validateInterviewers = (_rule: any, _value: any, callback: any) => {
  if (!scheduleForm.value.interviewerIds || !scheduleForm.value.interviewerIds.length) {
    callback(new Error('请至少指定一名面试官'));
    return;
  }
  callback();
};

const data = reactive<PageData<HrInterviewForm, HrInterviewQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    applicationId: undefined,
    roundNo: undefined,
    status: undefined,
    result: undefined,
    method: undefined,
    interviewerUserId: undefined
  },
  rules: {
    applicationId: [{ required: true, message: '应聘记录ID不能为空', trigger: 'blur' }],
    roundNo: [{ required: true, message: '面试轮次不能为空', trigger: 'change' }],
    scheduleTime: [{ required: true, message: '计划面试时间不能为空', trigger: 'change' }],
    interviewerIds: [{ validator: validateInterviewers, trigger: 'change' }]
  }
});

const { queryParams, form: scheduleForm, rules: scheduleRules } = toRefs<PageData<HrInterviewForm, HrInterviewQuery>>(data);
const { dialog, resetForm, showDialog, closeDialog } = useFormDialog({
  form: scheduleForm,
  formRef: scheduleFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  pageSizeKey: 'pageSize',
  initialPageSize: 10,
  resetExtras: () => {
    scheduleRange.value = null;
  },
  afterReset: () => handleQuery()
});

/** 合并计划时间区间条件（后端字段为 scheduleTimeBegin / scheduleTimeEnd） */
const applyRange = () => {
  queryParams.value.scheduleTimeBegin = scheduleRange.value?.[0] || undefined;
  queryParams.value.scheduleTimeEnd = scheduleRange.value?.[1] || undefined;
  return queryParams.value;
};

const getList = async () => {
  await withLoading(async () => {
    if (viewMode.value === 'todos') {
      const res = await listMyInterviewTodos();
      todoList.value = Array.isArray(res.data) ? res.data : [];
      total.value = todoList.value.length;
      return;
    }
    applyRange();
    const res = await listInterview(queryParams.value);
    interviewList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const handleViewModeChange = () => {
  getList();
};

const handleAdd = () => {
  resetForm();
  scheduleDialog.isEdit = false;
  scheduleDialog.row = {};
  scheduleDialog.names = '';
  scheduleForm.value.interviewerIds = [];
  showDialog('安排面试');
};

/* ------------------------------ 安排 / 改期 ------------------------------ */

const openInterviewerSelect = () => interviewerSelectRef.value?.open();

const handleInterviewerSelected = (users: any[]) => {
  const list = users || [];
  scheduleForm.value.interviewerIds = list.map(user => user.userId);
  scheduleDialog.names = list.map(user => user.nickName || user.userName || user.userId).join(', ');
};

const handleReschedule = async (row: HrInterviewVO) => {
  const res = await getInterview(row.interviewId!);
  const detailRow = res.data || row;
  resetForm();
  scheduleDialog.isEdit = true;
  scheduleDialog.row = detailRow;
  scheduleDialog.names = detailRow.interviewerNames || '';
  Object.assign(scheduleForm.value, {
    interviewId: detailRow.interviewId,
    applicationId: detailRow.applicationId,
    roundNo: detailRow.roundNo ?? 1,
    scheduleTime: detailRow.scheduleTime,
    endTime: detailRow.endTime,
    method: detailRow.method,
    location: detailRow.location,
    interviewerIds: (detailRow.interviewerIds || []).slice(),
    changeReason: undefined,
    remark: detailRow.remark
  });
  showDialog('面试改期');
};

const submitSchedule = () => {
  scheduleFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    submitting.value = true;
    try {
      if (scheduleDialog.isEdit && scheduleForm.value.interviewId) {
        await rescheduleInterview(scheduleForm.value.interviewId, { ...scheduleForm.value });
        modal.msgSuccess('改期成功，原面试记录已置为「已改期」');
      } else {
        await addInterview({ ...scheduleForm.value });
        modal.msgSuccess('面试安排成功');
      }
      closeDialog();
      await getList();
    } finally {
      submitting.value = false;
    }
  });
};

/* ------------------------------ 取消 ------------------------------ */

const cancelDialog = reactive<{ visible: boolean; submitting: boolean; row: Partial<HrInterviewVO> }>({
  visible: false,
  submitting: false,
  row: {}
});
const cancelForm = ref<{ cancelReason: string }>({ cancelReason: '' });
const cancelRules: ElFormRules = {
  cancelReason: [{ required: true, message: '取消原因不能为空', trigger: 'blur' }]
};

const openCancel = (row: HrInterviewVO) => {
  cancelDialog.row = row;
  cancelForm.value.cancelReason = '';
  cancelDialog.visible = true;
};

const submitCancel = () => {
  cancelFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    cancelDialog.submitting = true;
    try {
      await cancelInterview(cancelDialog.row.interviewId!, { ...cancelForm.value });
      modal.msgSuccess('取消成功');
      cancelDialog.visible = false;
      await getList();
    } finally {
      cancelDialog.submitting = false;
    }
  });
};

/* ------------------------------ 反馈 ------------------------------ */

const feedbackDialog = reactive<{ visible: boolean; submitting: boolean; row: Partial<HrInterviewVO> }>({
  visible: false,
  submitting: false,
  row: {}
});
const feedbackForm = ref<HrInterviewFeedbackForm>({
  score: undefined,
  feedback: '',
  result: undefined,
  overallScore: undefined,
  conclusion: ''
});
const feedbackRules: ElFormRules = {
  feedback: [{ required: true, message: '面试意见不能为空', trigger: 'blur' }]
};

const openFeedback = (row: HrInterviewVO) => {
  feedbackDialog.row = row;
  feedbackForm.value = {
    score: row.score,
    feedback: '',
    result: undefined,
    overallScore: undefined,
    conclusion: ''
  };
  feedbackDialog.visible = true;
};

const submitFeedback = () => {
  feedbackFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    feedbackDialog.submitting = true;
    try {
      await submitInterviewFeedback(feedbackDialog.row.interviewId!, { ...feedbackForm.value });
      modal.msgSuccess('反馈提交成功');
      feedbackDialog.visible = false;
      await getList();
    } finally {
      feedbackDialog.submitting = false;
    }
  });
};

/* ------------------------------ 详情 ------------------------------ */

const detail = reactive<{ visible: boolean; row: Partial<HrInterviewVO> }>({ visible: false, row: {} });

const handleDetail = async (row: HrInterviewVO) => {
  const res = await getInterview(row.interviewId!);
  detail.row = res.data || row;
  detail.visible = true;
};

/* ------------------------------ 操作下拉 ------------------------------ */

const handleMore = (row: HrInterviewVO, command: string) => {
  if (command === 'reschedule') {
    handleReschedule(row);
  } else if (command === 'cancel') {
    openCancel(row);
  }
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

.detail-panel {
  margin-bottom: 12px;
}

.inline-btn {
  margin-left: 8px;
}
</style>
