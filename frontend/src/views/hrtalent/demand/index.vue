<template>
  <div class="p-2 app-container hrtalent-demand-page">
    <PageHeading title="招聘管理" subtitle="招聘需求来源、状态流转与关键变更留痕" module="hrtalent" />

    <!-- 需求检索 -->
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>需求检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="需求编号" prop="demandNo">
            <el-input v-model="queryParams.demandNo" placeholder="请输入需求编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="需求标题" prop="demandTitle">
            <el-input
              v-model="queryParams.demandTitle"
              placeholder="请输入需求标题"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="需求岗位" prop="jobName">
            <el-input v-model="queryParams.jobName" placeholder="请输入岗位名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="公司ID" prop="companyDeptId">
            <el-input
              v-model="queryParams.companyDeptId"
              placeholder="请输入公司（部门）ID"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="需求状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 160px">
              <el-option v-for="dict in recruit_demand_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="紧急程度" prop="urgency">
            <el-select v-model="queryParams.urgency" placeholder="请选择紧急程度" clearable style="width: 150px">
              <el-option v-for="dict in recruit_urgency" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="招聘形式" prop="recruitMode">
            <el-select v-model="queryParams.recruitMode" placeholder="请选择招聘形式" clearable style="width: 150px">
              <el-option v-for="dict in recruit_mode" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="申请日期">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 260px"
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
            <span class="panel-kicker">Recruit Demand</span>
            <h3>招聘需求</h3>
            <p>
              共 {{ total }} 条记录；待招聘人数 = max(需求人数 - 已到岗人数, 0)（前端按此口径展示）。
              暂停、复开、关闭、完成必须填写原因并写入变更历史。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['recruit:demand:add']" type="primary" icon="Plus" @click="handleAdd">新增需求</el-button>
            <el-button
              v-hasPermi="['recruit:demand:edit']"
              type="success"
              plain
              icon="Edit"
              :disabled="single"
              @click="handleUpdate()"
            >
              修改
            </el-button>
            <el-button
              v-hasPermi="['recruit:demand:edit']"
              type="danger"
              plain
              icon="Delete"
              :disabled="!canDeleteSelected"
              @click="handleDelete()"
            >
              删除
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="demandList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="需求编号" align="center" prop="demandNo" width="170" show-overflow-tooltip />
        <el-table-column label="需求标题" align="center" prop="demandTitle" width="180" show-overflow-tooltip />
        <el-table-column label="公司" align="center" width="150" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.companyName || scope.row.companyDeptId || '-' }}</template>
        </el-table-column>
        <el-table-column label="用人部门" align="center" width="150" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.useDeptName || scope.row.useDeptId || '-' }}</template>
        </el-table-column>
        <el-table-column label="需求岗位" align="center" prop="jobName" width="150" show-overflow-tooltip />
        <el-table-column label="需求人数" align="center" prop="demandCount" width="90" />
        <el-table-column label="已到岗" align="center" prop="hiredCount" width="80" />
        <el-table-column label="待招聘" align="center" width="80">
          <template #default="scope">{{ pendingCount(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="紧急程度" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="recruit_urgency" :value="scope.row.urgency" />
          </template>
        </el-table-column>
        <el-table-column label="招聘负责人" align="center" width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.recruiterName || scope.row.recruiterId || '-' }}</template>
        </el-table-column>
        <el-table-column label="申请日期" align="center" prop="applyDate" width="110" />
        <el-table-column label="期望到岗" align="center" prop="expectArrivalDate" width="110" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="recruit_demand_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="详情" placement="top">
              <el-button
                v-hasPermi="['recruit:demand:query']"
                link
                type="primary"
                icon="Search"
                @click="handleDetail(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip v-if="canEdit(scope.row)" content="编辑" placement="top">
              <el-button
                v-hasPermi="['recruit:demand:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="变更历史" placement="top">
              <el-button
                v-hasPermi="['recruit:demand:query']"
                link
                type="primary"
                icon="Clock"
                @click="handleChanges(scope.row)"
              ></el-button>
            </el-tooltip>
            <!-- 状态动作：按状态机与权限过滤后展示，动作一律需填原因 -->
            <el-dropdown v-if="actionOptions(scope.row).length" trigger="click" @command="cmd => handleAction(scope.row, cmd)">
              <el-button link type="primary" icon="More"></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="item in actionOptions(scope.row)"
                    :key="item.action"
                    v-hasPermi="[item.permission]"
                    :command="item.action"
                    :divided="item.action === 'close'"
                  >
                    {{ item.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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

    <!-- 新增/编辑需求 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="860px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="需求人数必须大于 0；期望到岗日期不得早于申请日期。需求进入「招聘中」后，关键字段修改将由后端写入变更历史。"
      />
      <el-form ref="demandFormRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="需求标题" prop="demandTitle">
              <el-input v-model="form.demandTitle" placeholder="请输入需求标题" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需求岗位" prop="jobName">
              <el-input v-model="form.jobName" placeholder="请输入需求岗位名称" />
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
              <el-input v-model="form.companyName" placeholder="公司名称快照，可为空" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用工部门ID" prop="useDeptId">
              <el-input v-model="form.useDeptId" placeholder="用工部门ID，可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用工部门名称" prop="useDeptName">
              <el-input v-model="form.useDeptName" placeholder="用工部门名称快照，可为空" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="招聘负责人ID" prop="recruiterId">
              <el-input v-model="form.recruiterId" placeholder="招聘负责人用户ID，可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需求人数" prop="demandCount">
              <el-input-number v-model="form.demandCount" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="紧急程度" prop="urgency">
              <el-select v-model="form.urgency" placeholder="请选择紧急程度" style="width: 100%">
                <el-option v-for="dict in recruit_urgency" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="招聘形式" prop="recruitMode">
              <el-select v-model="form.recruitMode" placeholder="请选择招聘形式" clearable style="width: 100%">
                <el-option v-for="dict in recruit_mode" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="申请日期" prop="applyDate">
              <el-date-picker
                v-model="form.applyDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="期望到岗日期" prop="expectArrivalDate">
              <el-date-picker
                v-model="form.expectArrivalDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="不得早于申请日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="岗位职级" prop="jobLevel">
              <el-input v-model="form.jobLevel" placeholder="字典编码，可为空" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="工作城市" prop="workCity">
          <el-input v-model="form.workCity" placeholder="请输入工作城市" />
        </el-form-item>
        <el-form-item label="需求原因" prop="demandReason">
          <el-input v-model="form.demandReason" type="textarea" :rows="3" placeholder="请输入需求原因说明" />
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

    <!-- 需求详情 -->
    <el-dialog v-model="detail.visible" title="招聘需求详情" width="860px" append-to-body>
      <el-descriptions :column="2" border size="small" class="detail-panel">
        <el-descriptions-item label="需求编号">{{ detail.row.demandNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="需求标题">{{ detail.row.demandTitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="公司">{{ detail.row.companyName || detail.row.companyDeptId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用人部门">
          {{ detail.row.useDeptName || detail.row.useDeptId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="需求岗位">{{ detail.row.jobName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位职级">{{ detail.row.jobLevel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工作城市">{{ detail.row.workCity || '-' }}</el-descriptions-item>
        <el-descriptions-item label="招聘负责人">
          {{ detail.row.recruiterName || detail.row.recruiterId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="需求人数">{{ detail.row.demandCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="已到岗人数">{{ detail.row.hiredCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="待招聘人数">{{ pendingCount(detail.row) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="recruit_demand_status" :value="detail.row.status" />
        </el-descriptions-item>
        <el-descriptions-item label="紧急程度">
          <dict-tag :options="recruit_urgency" :value="detail.row.urgency" />
        </el-descriptions-item>
        <el-descriptions-item label="招聘形式">
          <dict-tag :options="recruit_mode" :value="detail.row.recruitMode" />
        </el-descriptions-item>
        <el-descriptions-item label="申请日期">{{ detail.row.applyDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望到岗日期">{{ detail.row.expectArrivalDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.row.submittedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关闭时间">{{ detail.row.closedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本号">{{ detail.row.version ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detail.row.updateTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="需求原因" :span="2">{{ detail.row.demandReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.row.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detail.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 状态动作：暂停/复开/完成/关闭必填原因，提交与确认可不填 -->
    <el-dialog v-model="actionDialog.visible" :title="actionDialog.title" width="560px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="actionDialog.tip"
      />
      <el-form ref="actionFormRef" :model="actionForm" :rules="actionRules" label-width="90px">
        <el-form-item label="需求编号">
          <el-input :model-value="actionDialog.row.demandNo" disabled />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input
            v-model="actionForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="actionReasonRequired ? '请填写操作原因（必填，最长 500 字）' : '选填，最长 500 字'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="actionDialog.submitting" @click="submitAction">确 定</el-button>
          <el-button @click="actionDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 变更历史 -->
    <el-dialog v-model="changeDialog.visible" title="需求变更历史" width="900px" append-to-body>
      <el-table v-loading="changeDialog.loading" border size="small" :data="changeList">
        <el-table-column type="expand">
          <template #default="scope">
            <div class="snapshot-block">
              <div class="snapshot-col">
                <span class="snapshot-title">变更前</span>
                <pre class="snapshot-text">{{ formatSnapshot(scope.row.beforeJson) }}</pre>
              </div>
              <div class="snapshot-col">
                <span class="snapshot-title">变更后</span>
                <pre class="snapshot-text">{{ formatSnapshot(scope.row.afterJson) }}</pre>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="变更类型" align="center" prop="changeType" width="120" />
        <el-table-column label="变更原因" align="center" prop="reason" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作人" align="center" width="130">
          <template #default="scope">{{ scope.row.operatorName || scope.row.operatorId || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.operateTime) || '-' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="changeDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { PageResult } from '@/api/types';
import {
  actionDemand,
  addDemand,
  delDemand,
  getDemand,
  listDemand,
  listDemandChanges,
  updateDemand
} from '@/api/hrtalent/demand';
import type {
  DemandAction,
  HrDemandChangeVO,
  HrDemandForm,
  HrDemandQuery,
  HrDemandVO
} from '@/api/hrtalent/demand/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useDateRangeQuery } from '@/hooks/form/useDateRangeQuery';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'HrDemand' });

const { recruit_demand_status, recruit_urgency, recruit_mode } = toRefs<any>(
  useDict('recruit_demand_status', 'recruit_urgency', 'recruit_mode')
);

/** 状态动作的中文名与说明（submit/confirm 的「是否必填原因」由 REASON_REQUIRED_ACTIONS 决定） */
const ACTION_META: Record<DemandAction, { label: string; tip: string }> = {
  submit: { label: '提交', tip: '提交后需求进入「已提交」，等待确认进入招聘中。提交原因可选。' },
  confirm: { label: '确认', tip: '确认后需求由「已提交」进入「招聘中」，开始计入招聘执行。确认原因可选。' },
  pause: { label: '暂停', tip: '暂停后需求冻结，必须填写原因并记入变更历史。' },
  resume: { label: '复开', tip: '复开后需求回到招聘中，必须填写原因并记入变更历史。' },
  complete: { label: '完成', tip: '完成后需求结束招聘，必须填写原因并记入变更历史。' },
  close: { label: '关闭', tip: '关闭为终态，必须填写原因并记入变更历史。' }
};

/** 必须填写原因的动作（设计 §8.2：暂停、关闭、复开必填；提交与确认不需要） */
const REASON_REQUIRED_ACTIONS: DemandAction[] = ['pause', 'resume', 'complete', 'close'];

/** 各状态下允许的动作（与 SPEC §4.1 状态机一致：draft→submitted→recruiting→(paused⇄recruiting)→completed/closed） */
const STATUS_ACTIONS: Record<string, DemandAction[]> = {
  draft: ['submit', 'close'],
  submitted: ['confirm', 'pause', 'close'],
  recruiting: ['pause', 'complete', 'close'],
  paused: ['resume', 'close']
};

/** 动作所需权限串（confirm/submit 复用 submit 权限，complete 复用 close 权限） */
const ACTION_PERMISSION: Record<DemandAction, string> = {
  submit: 'recruit:demand:submit',
  confirm: 'recruit:demand:submit',
  pause: 'recruit:demand:pause',
  resume: 'recruit:demand:pause',
  complete: 'recruit:demand:close',
  close: 'recruit:demand:close'
};

const demandList = ref<HrDemandVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const { ids, selectedRows, single, handleSelectionChange } = useTableSelection<HrDemandVO>(item => item.demandId!);
const total = ref(0);
const submitting = ref(false);
const demandFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();
const actionFormRef = ref<ElFormInstance>();
const { dateRange, applyDateRange, resetDateRange } = useDateRangeQuery('ApplyDate');

const initFormData: HrDemandForm = {
  demandId: undefined,
  demandNo: undefined,
  demandTitle: '',
  companyDeptId: undefined,
  companyName: '',
  useDeptId: undefined,
  useDeptName: '',
  recruiterId: undefined,
  applyDate: undefined,
  expectArrivalDate: undefined,
  demandCount: 1,
  urgency: 'normal',
  recruitMode: undefined,
  jobName: '',
  jobLevel: '',
  workCity: '',
  demandReason: '',
  version: undefined,
  remark: ''
};

/** 期望到岗日期不得早于申请日期 */
const validateArrivalDate = (_rule: any, value: any, callback: any) => {
  if (!value || !form.value.applyDate) {
    callback();
    return;
  }
  if (value < form.value.applyDate) {
    callback(new Error('期望到岗日期不得早于申请日期'));
    return;
  }
  callback();
};

const data = reactive<PageData<HrDemandForm, HrDemandQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    demandNo: undefined,
    demandTitle: undefined,
    jobName: undefined,
    companyDeptId: undefined,
    status: undefined,
    urgency: undefined,
    recruitMode: undefined,
    params: {}
  },
  rules: {
    demandTitle: [{ required: true, message: '需求标题不能为空', trigger: 'blur' }],
    companyDeptId: [{ required: true, message: '公司（部门）ID 不能为空', trigger: 'blur' }],
    jobName: [{ required: true, message: '需求岗位不能为空', trigger: 'blur' }],
    demandCount: [{ required: true, message: '需求人数不能为空', trigger: 'change' }],
    applyDate: [{ required: true, message: '申请日期不能为空', trigger: 'change' }],
    expectArrivalDate: [{ validator: validateArrivalDate, trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<HrDemandForm, HrDemandQuery>>(data);
const { dialog, resetForm, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: demandFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  resetExtras: () => resetDateRange(),
  afterReset: () => handleQuery()
});

/** 待招聘人数 = max(需求人数 - 已到岗人数, 0)，与后端 §4.1 口径一致 */
const pendingCount = (row: Partial<HrDemandVO>) => Math.max((row.demandCount || 0) - (row.hiredCount || 0), 0);

/** 是否允许编辑：终态（完成/关闭）不可编辑 */
const canEdit = (row: Partial<HrDemandVO>) => row.status !== 'completed' && row.status !== 'closed';

/** 仅草稿允许逻辑删除 */
const canDeleteSelected = computed(
  () => !single.value && selectedRows.value.length > 0 && selectedRows.value.every(row => row.status === 'draft')
);

/** 当前行可执行的动作（按状态机过滤，权限由 v-hasPermi 在菜单项上控制） */
const actionOptions = (row: Partial<HrDemandVO>) =>
  (STATUS_ACTIONS[row.status || ''] || []).map(action => ({
    action,
    label: ACTION_META[action].label,
    permission: ACTION_PERMISSION[action]
  }));

/** 查询需求列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listDemand(applyDateRange(queryParams.value));
    demandList.value = res.data?.rows || [];
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
  resetForm();
  form.value.demandId = undefined;
  form.value.version = undefined;
  form.value.demandNo = undefined;
  showDialog('新增招聘需求');
};

/** 修改按钮操作 */
const handleUpdate = async (row?: Partial<HrDemandVO>) => {
  resetForm();
  const demandId = row?.demandId || ids.value[0];
  const res = await getDemand(demandId!);
  const detailRow = res.data || {};
  Object.assign(form.value, initFormData);
  Object.assign(form.value, {
    demandId: detailRow.demandId,
    demandNo: detailRow.demandNo,
    demandTitle: detailRow.demandTitle,
    companyDeptId: detailRow.companyDeptId,
    companyName: detailRow.companyName,
    useDeptId: detailRow.useDeptId,
    useDeptName: detailRow.useDeptName,
    recruiterId: detailRow.recruiterId,
    applyDate: detailRow.applyDate,
    expectArrivalDate: detailRow.expectArrivalDate,
    demandCount: detailRow.demandCount,
    urgency: detailRow.urgency || 'normal',
    recruitMode: detailRow.recruitMode,
    jobName: detailRow.jobName,
    jobLevel: detailRow.jobLevel,
    workCity: detailRow.workCity,
    demandReason: detailRow.demandReason,
    version: detailRow.version,
    remark: detailRow.remark
  });
  showDialog('修改招聘需求');
};

/** 提交新增/修改 */
const submitForm = () => {
  demandFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    submitting.value = true;
    try {
      if (form.value.demandId) {
        await updateDemand({ ...form.value });
        modal.msgSuccess('修改成功');
      } else {
        await addDemand({ ...form.value });
        modal.msgSuccess('新增成功');
      }
      closeDialog();
      await getList();
    } finally {
      submitting.value = false;
    }
  });
};

/** 删除（仅草稿） */
const handleDelete = async (row?: Partial<HrDemandVO>) => {
  const targets = row?.demandId ? [row.demandId] : ids.value;
  if (!targets.length) {
    return;
  }
  try {
    await modal.confirm('是否确认删除选中的招聘需求？仅草稿状态可删除。');
  } catch {
    return;
  }
  await delDemand(targets.join(','));
  modal.msgSuccess('删除成功');
  await getList();
};

/** 详情 */
const detail = reactive<{ visible: boolean; row: Partial<HrDemandVO> }>({ visible: false, row: {} });

const handleDetail = async (row: Partial<HrDemandVO>) => {
  const res = await getDemand(row.demandId!);
  detail.row = res.data || row;
  detail.visible = true;
};

/** 状态动作弹窗 */
const actionDialog = reactive<{
  visible: boolean;
  title: string;
  tip: string;
  action: DemandAction;
  row: Partial<HrDemandVO>;
  submitting: boolean;
}>({
  visible: false,
  title: '',
  tip: '',
  action: 'close',
  row: {},
  submitting: false
});

const actionForm = ref<{ reason: string }>({ reason: '' });

/** 当前动作是否必须填写原因（暂停/复开/完成/关闭必填，提交/确认可不填） */
const actionReasonRequired = computed(() => REASON_REQUIRED_ACTIONS.includes(actionDialog.action));

/** 原因校验规则随动作动态变化 */
const actionRules = computed<ElFormRules>(() => ({
  reason: actionReasonRequired.value ? [{ required: true, message: '操作原因不能为空', trigger: 'blur' }] : []
}));

/** 打开动作弹窗 */
const handleAction = (row: Partial<HrDemandVO>, action: DemandAction) => {
  actionDialog.row = row;
  actionDialog.action = action;
  actionDialog.title = `需求${ACTION_META[action].label}`;
  actionDialog.tip = ACTION_META[action].tip;
  actionForm.value.reason = '';
  actionDialog.visible = true;
};

/** 提交动作（暂停/复开/完成/关闭必填原因；带 version 乐观锁） */
const submitAction = () => {
  actionFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    actionDialog.submitting = true;
    try {
      await actionDemand(actionDialog.row.demandId!, actionDialog.action, {
        reason: actionForm.value.reason || undefined,
        version: actionDialog.row.version
      });
      modal.msgSuccess(`${ACTION_META[actionDialog.action].label}成功`);
      actionDialog.visible = false;
      await getList();
    } finally {
      actionDialog.submitting = false;
    }
  });
};

/** 变更历史 */
const changeDialog = reactive<{ visible: boolean; loading: boolean }>({ visible: false, loading: false });
const changeList = ref<HrDemandChangeVO[]>([]);

const handleChanges = async (row: Partial<HrDemandVO>) => {
  changeDialog.visible = true;
  changeDialog.loading = true;
  changeList.value = [];
  try {
    const res = await listDemandChanges(row.demandId!);
    const payload = res.data as unknown;
    // 兼容「直接返回数组」与「分页 rows」两种出参
    changeList.value = Array.isArray(payload)
      ? (payload as HrDemandChangeVO[])
      : ((payload as PageResult<HrDemandChangeVO>)?.rows ?? []);
  } catch {
    // 拦截器已提示错误，这里保持空列表
    changeList.value = [];
  } finally {
    changeDialog.loading = false;
  }
};

/** 快照渲染：后端可能下发 JSON 字符串或对象 */
const formatSnapshot = (value?: Record<string, any> | string | null) => {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  if (typeof value === 'string') {
    return value;
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
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
  margin-bottom: 4px;
}

/* 变更快照：左右两栏对照展示 */
.snapshot-block {
  display: flex;
  gap: 12px;
  padding: 4px 8px;
}

.snapshot-col {
  flex: 1 1 50%;
  min-width: 0;
}

.snapshot-title {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.snapshot-text {
  margin: 0;
  max-height: 220px;
  overflow: auto;
  padding: 8px;
  border-radius: 6px;
  background: var(--app-surface-bg);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
