<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Call Quota</span>
            <h3>调用配额（按人）</h3>
            <p>
              单位是<b>调用次数</b>，不是钱：调用审计里的 cost 常为「未知而非免费」，用它做配额会算出一本对不上的账。
              统计口径是<b>该调用人在自然日/自然月内的调用审计行数（含失败）</b>——只算成功会让「反复失败重试」绕开配额。
              <b>没有配额行 = 不限</b>；日/月上限留空 = 该周期不限。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['aig:quota:edit']" type="primary" plain icon="Plus" @click="openEdit()">
              新增配额
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="quotaList">
        <el-table-column label="用户ID" align="center" prop="userId" width="140" />
        <el-table-column label="账号" align="center" prop="userName" width="160" show-overflow-tooltip />
        <el-table-column label="每日上限" align="center" width="120">
          <template #default="scope">{{ limitText(scope.row.dailyLimit) }}</template>
        </el-table-column>
        <el-table-column label="每月上限" align="center" width="120">
          <template #default="scope">{{ limitText(scope.row.monthlyLimit) }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === '1' ? 'info' : 'success'">
              {{ scope.row.status === '1' ? '停用（不判）' : '生效' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="210" fixed="right">
          <template #default="scope">
            <el-button v-hasPermi="['aig:quota:list']" link type="primary" icon="DataLine" @click="openUsage(scope.row)">
              用量
            </el-button>
            <el-button v-hasPermi="['aig:quota:edit']" link type="primary" icon="Edit" @click="openEdit(scope.row)">
              修改
            </el-button>
            <el-button v-hasPermi="['aig:quota:edit']" link type="danger" icon="Delete" @click="handleRemove(scope.row)">
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
    </el-card>

    <!-- 编辑 -->
    <el-dialog v-model="editVisible" :title="form.quotaId ? '修改配额' : '新增配额'" width="560px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        一人一行：同一个用户只保留一条配置（保存时自动覆盖），避免「改了日上限、月上限还是旧值」。
        上限留空表示该周期<b>不限</b>。
      </el-alert>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="用户ID" prop="userId">
          <el-input v-model="form.userId" :disabled="!!form.quotaId" placeholder="sys_user.user_id，例如 1001" />
        </el-form-item>
        <el-form-item label="账号" prop="userName">
          <el-input v-model="form.userName" placeholder="可空；冗余存一份便于列表显示与离线核对" />
        </el-form-item>
        <el-form-item label="每日上限" prop="dailyLimit">
          <el-input-number v-model="form.dailyLimit" :min="0" :controls="false" placeholder="留空 = 不限" />
        </el-form-item>
        <el-form-item label="每月上限" prop="monthlyLimit">
          <el-input-number v-model="form.monthlyLimit" :min="0" :controls="false" placeholder="留空 = 不限" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="生效（参与判定）" value="0" />
            <el-option label="停用（不参与判定，等同于不限）" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="为什么给他设这个额度（便于事后复核）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 用量 -->
    <el-dialog v-model="usageVisible" title="用量（已用 / 上限）" width="520px" append-to-body>
      <el-descriptions v-if="usage" :column="1" border>
        <el-descriptions-item label="用户">
          {{ usage.userName || '-' }}（{{ usage.userId }}）
        </el-descriptions-item>
        <el-descriptions-item label="今日">
          {{ usage.dailyUsed }} / {{ limitText(usage.dailyLimit) }}
          <span class="text-gray-500">（自 {{ usage.dailyFrom }} 起）</span>
        </el-descriptions-item>
        <el-descriptions-item label="本月">
          {{ usage.monthlyUsed }} / {{ limitText(usage.monthlyLimit) }}
          <span class="text-gray-500">（自 {{ usage.monthlyFrom }} 起）</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="usage.exceeded ? 'danger' : usage.limited ? 'warning' : 'info'">
            {{ usage.exceeded ? '已用尽（调用会被拒）' : usage.limited ? '生效中' : '不限' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { listQuota, quotaUsage, removeQuota, saveQuota } from '@/api/aigov/quota';
import type { AigUserQuotaForm, AigUserQuotaQuery, AigUserQuotaUsageVO, AigUserQuotaVO } from '@/api/aigov/quota/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';

defineOptions({ name: 'AigUserQuota' });

const quotaList = ref<AigUserQuotaVO[]>([]);
const total = ref(0);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const queryParams = ref<AigUserQuotaQuery>({ pageNum: 1, pageSize: 10 });

const editVisible = ref(false);
const saving = ref(false);
const formRef = ref<ElFormInstance>();
const form = ref<AigUserQuotaForm>({});

const usageVisible = ref(false);
const usage = ref<AigUserQuotaUsageVO>();

const rules = {
  userId: [{ required: true, message: '用户ID不能为空', trigger: 'blur' }]
};

/** 上限展示：空 = 不限（不能显示成 0，那会读成「一次都不许」） */
const limitText = (value?: number | null) => (value === null || value === undefined ? '不限' : String(value));

const getList = async () => {
  await withLoading(async () => {
    const res = await listQuota(queryParams.value);
    quotaList.value = res.data.rows || [];
    total.value = res.data.total || 0;
  });
};

const openEdit = (row?: AigUserQuotaVO) => {
  form.value = row
    ? {
        quotaId: row.quotaId,
        userId: row.userId,
        userName: row.userName,
        dailyLimit: row.dailyLimit ?? undefined,
        monthlyLimit: row.monthlyLimit ?? undefined,
        status: row.status || '0',
        remark: row.remark
      }
    : { status: '0' };
  editVisible.value = true;
};

const submit = async () => {
  if (!formRef.value) return;
  await formRef.value.validate();
  saving.value = true;
  try {
    // 上限留空 -> null：null 是「不限」的表示（后端用显式 set 写入，不会被跳过）
    const payload: AigUserQuotaForm = {
      ...form.value,
      dailyLimit: form.value.dailyLimit === undefined || form.value.dailyLimit === null
        ? null : form.value.dailyLimit,
      monthlyLimit: form.value.monthlyLimit === undefined || form.value.monthlyLimit === null
        ? null : form.value.monthlyLimit
    };
    await saveQuota(payload);
    modal.msgSuccess('保存成功');
    editVisible.value = false;
    getList();
  } finally {
    saving.value = false;
  }
};

const handleRemove = async (row: AigUserQuotaVO) => {
  await modal.confirm(
    '确认删除「' + (row.userName || row.userId) + '」的配额配置？' +
      '删除的含义是回到「不限」，不是禁止调用——如果目的是停用这个人，请改为把状态设为停用，或把上限设为 0。'
  );
  await removeQuota(row.quotaId as string | number);
  modal.msgSuccess('已删除（该用户回到「不限」）');
  getList();
};

const openUsage = async (row: AigUserQuotaVO) => {
  usageVisible.value = true;
  const res = await quotaUsage(row.userId as string | number);
  usage.value = res.data || {};
};

onMounted(() => {
  getList();
});
</script>
