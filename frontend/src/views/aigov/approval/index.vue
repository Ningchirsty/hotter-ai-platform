<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Call Approval</span>
            <h3>调用授权</h3>
            <p>
              路由策略上勾了「调用前审批」的<b>能力 × 数据等级</b>，调用前必须有一张<b>已批准且未过期</b>的授权，
              否则调用会被拒（错误码 <code>APPROVAL_REQUIRED</code>）。
              批准的不是一次请求、也不是当日无限，而是<b>「这个人 × 这个能力 × 这个数据等级」在授权有效期内的后续调用</b>。
              <b>申请人不得自审</b>（服务端强制）。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['aig:approval:apply']" type="primary" plain icon="Plus" @click="openApply()">
              提交申请
            </el-button>
            <el-button v-hasPermi="['aig:approval:approve']" plain icon="Refresh" @click="handleExpireScan()">
              超时扫描
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true">
        <el-form-item label="能力编码" prop="capabilityCode">
          <el-input v-model="queryParams.capabilityCode" placeholder="如 image_generation" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="数据等级" prop="dataLevel">
          <el-select v-model="queryParams.dataLevel" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="dict in aig_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请人ID" prop="requesterId">
          <el-input v-model="queryParams.requesterId" placeholder="sys_user.user_id" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" border class="data-table" :data="approvalList">
        <el-table-column label="单号" align="center" prop="approvalId" width="170" show-overflow-tooltip />
        <el-table-column label="申请人" align="center" width="140" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.requesterName || '-' }}</span>
            <span class="text-muted">#{{ scope.row.requesterId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="能力" align="center" prop="capabilityCode" width="180" show-overflow-tooltip />
        <el-table-column label="数据等级" align="center" width="120">
          <template #default="scope">
            <dict-tag :options="aig_data_level" :value="scope.row.dataLevel" />
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="170">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row)">{{ statusText(scope.row) }}</el-tag>
            <!-- 关键是「现在还有效吗」：已批准但过了授权有效期必须一眼看出来 -->
            <div v-if="scope.row.status === 'APPROVED'" class="text-muted">
              {{ grantActive(scope.row) ? '授权在用' : '授权已过期' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="授权有效期止" align="center" width="170">
          <template #default="scope">
            {{ scope.row.validUntil ? parseTime(scope.row.validUntil) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="审批时限" align="center" width="170">
          <template #default="scope">
            {{ scope.row.expireTime ? parseTime(scope.row.expireTime) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="申请理由" align="center" prop="reason" show-overflow-tooltip />
        <el-table-column label="审批" align="center" width="230" show-overflow-tooltip>
          <template #default="scope">
            <template v-if="scope.row.decidedAt">
              <div>{{ scope.row.approverName || '-' }} · {{ parseTime(scope.row.decidedAt) }}</div>
              <div class="text-muted">{{ scope.row.decisionRemark || '（未填意见）' }}</div>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="scope">
            <template v-if="scope.row.status === 'PENDING'">
              <el-button v-hasPermi="['aig:approval:approve']" link type="primary" icon="Check" @click="openDecide(scope.row, true)">
                批准
              </el-button>
              <el-button v-hasPermi="['aig:approval:approve']" link type="warning" icon="Close" @click="openDecide(scope.row, false)">
                驳回
              </el-button>
              <el-button v-hasPermi="['aig:approval:apply']" link type="danger" icon="RefreshLeft" @click="handleCancel(scope.row)">
                撤回
              </el-button>
            </template>
            <span v-else class="text-muted">已出结论</span>
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

    <!-- 提交申请 -->
    <el-dialog v-model="applyVisible" title="提交调用授权申请" width="600px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        只有<b>策略要求审批</b>的「能力 × 数据等级」才需要申请；其它组合会被服务端直接拒绝并说明原因。
        批准后你会拿到一张<b>带有效期</b>的授权（默认 30 分钟），有效期内该组合的调用免再审。
      </el-alert>
      <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" label-width="110px">
        <el-form-item label="能力" prop="capabilityCode">
          <el-select v-model="applyForm.capabilityCode" placeholder="请选择能力" filterable style="width: 100%">
            <el-option v-for="item in capabilityOptions" :key="item.capabilityCode" :label="item.capabilityCode" :value="item.capabilityCode as string" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据等级" prop="dataLevel">
          <el-select v-model="applyForm.dataLevel" placeholder="请选择数据等级" style="width: 100%">
            <el-option v-for="dict in aig_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请理由" prop="reason">
          <el-input v-model="applyForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="审批人据此判断：这批调用是做什么、为什么必须走这个能力与等级" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitApply">提交</el-button>
      </template>
    </el-dialog>

    <!-- 批准 / 驳回 -->
    <el-dialog v-model="decideVisible" :title="decideForm.approved ? '批准申请' : '驳回申请'" width="560px" append-to-body>
      <el-alert v-if="decideForm.approved" type="info" :closable="false" class="mb-2">
        批准后，该申请人可在<b>授权有效期内</b>对「{{ decideRow?.capabilityCode }} × {{ decideRow?.dataLevel }}」免再审调用。
      </el-alert>
      <el-alert v-else type="warning" :closable="false" class="mb-2">
        驳回<b>必须写明原因</b>：申请人要据此修改后重新提交。
      </el-alert>
      <el-form ref="decideFormRef" :model="decideForm" :rules="decideRules" label-width="90px">
        <el-form-item label="审批意见" prop="remark">
          <el-input v-model="decideForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="批准可留空；驳回必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="decideVisible = false">取消</el-button>
        <el-button :type="decideForm.approved ? 'primary' : 'warning'" :loading="saving" @click="submitDecide">
          {{ decideForm.approved ? '确认批准' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { applyApproval, cancelApproval, decideApproval, expireScanApproval, listApproval } from '@/api/aigov/approval';
import type { AigCallApprovalDecideForm, AigCallApprovalForm, AigCallApprovalQuery, AigCallApprovalVO } from '@/api/aigov/approval/types';
import { listCapability } from '@/api/aigov/capability';
import type { AigCapabilityVO } from '@/api/aigov/capability/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'AigCallApproval' });

const { aig_data_level } = toRefs<any>(useDict('aig_data_level'));

const approvalList = ref<AigCallApprovalVO[]>([]);
const capabilityOptions = ref<AigCapabilityVO[]>([]);
const total = ref(0);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const queryParams = ref<AigCallApprovalQuery>({ pageNum: 1, pageSize: 10 });

const applyVisible = ref(false);
const decideVisible = ref(false);
const saving = ref(false);
const applyFormRef = ref<ElFormInstance>();
const decideFormRef = ref<ElFormInstance>();
const applyForm = ref<AigCallApprovalForm>({});
const decideForm = ref<AigCallApprovalDecideForm>({});
const decideRow = ref<AigCallApprovalVO>();

const statusOptions = [
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'EXPIRED', label: '已超时' },
  { value: 'CANCELLED', label: '已撤回' }
];

const applyRules = {
  capabilityCode: [{ required: true, message: '请选择能力', trigger: 'change' }],
  dataLevel: [{ required: true, message: '请选择数据等级', trigger: 'change' }]
};
const decideRules = {
  remark: [
    {
      // 驳回必须有原因——与服务端的判据一致，页面先拦一道给出即时反馈
      validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
        if (decideForm.value.approved === false && !String(value || '').trim()) {
          callback(new Error('驳回必须写明原因'));
          return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ]
};

/** 授权是否仍在有效期内（「批过」与「现在还管用」是两件事） */
const grantActive = (row: AigCallApprovalVO) =>
  row.status === 'APPROVED' && !!row.validUntil && new Date(row.validUntil).getTime() > Date.now();

const statusText = (row: AigCallApprovalVO) => {
  const label = statusOptions.find(item => item.value === row.status)?.label || row.status || '-';
  return label;
};

const statusTagType = (row: AigCallApprovalVO) => {
  if (row.status === 'PENDING') return 'warning';
  if (row.status === 'APPROVED') return grantActive(row) ? 'success' : 'info';
  if (row.status === 'REJECTED') return 'danger';
  return 'info';
};

const getList = async () => {
  await withLoading(async () => {
    const res = await listApproval(queryParams.value);
    approvalList.value = res.data.rows || [];
    total.value = res.data.total || 0;
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.value = { pageNum: 1, pageSize: queryParams.value.pageSize };
  getList();
};

const openApply = async () => {
  applyForm.value = {};
  applyVisible.value = true;
  if (capabilityOptions.value.length === 0) {
    try {
      const res = await listCapability({ pageNum: 1, pageSize: 200 } as any);
      capabilityOptions.value = (res.data?.rows || res.data || []) as AigCapabilityVO[];
    } catch {
      // 拉不到能力清单不阻塞：后端仍会校验能力是否存在
      capabilityOptions.value = [];
    }
  }
};

const submitApply = async () => {
  if (!applyFormRef.value) return;
  await applyFormRef.value.validate();
  saving.value = true;
  try {
    await applyApproval(applyForm.value);
    modal.msgSuccess('已提交，等待审批');
    applyVisible.value = false;
    getList();
  } finally {
    saving.value = false;
  }
};

const openDecide = (row: AigCallApprovalVO, approved: boolean) => {
  decideRow.value = row;
  decideForm.value = { approved, remark: '' };
  decideVisible.value = true;
};

const submitDecide = async () => {
  if (!decideFormRef.value || !decideRow.value) return;
  await decideFormRef.value.validate();
  saving.value = true;
  try {
    await decideApproval(decideRow.value.approvalId as string | number, decideForm.value);
    modal.msgSuccess(decideForm.value.approved ? '已批准' : '已驳回');
    decideVisible.value = false;
    getList();
  } finally {
    saving.value = false;
  }
};

const handleCancel = async (row: AigCallApprovalVO) => {
  await modal.confirm('确认撤回这张申请单？撤回后可以重新提交（只能撤回自己提交的、尚未出结论的单子）。');
  await cancelApproval(row.approvalId as string | number);
  modal.msgSuccess('已撤回');
  getList();
};

const handleExpireScan = async () => {
  const res = await expireScanApproval();
  modal.msgSuccess('超时扫描完成：本次置为「已超时」' + (res.data?.expired ?? 0) + ' 张');
  getList();
};

onMounted(() => {
  getList();
});
</script>

<style scoped>
.text-muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.toolbar-shell {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.panel-kicker {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.table-heading h3 {
  margin: 4px 0 6px;
}
.table-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mb-2 {
  margin-bottom: 12px;
}
</style>
