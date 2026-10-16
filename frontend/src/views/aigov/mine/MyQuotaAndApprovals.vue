<template>
  <el-dialog
    v-model="visible"
    title="我的额度与申请"
    width="820px"
    append-to-body
    @open="handleOpen"
  >
    <el-tabs v-model="activeTab">
      <!-- 我的额度：员工自查「我还能调几次」 -->
      <el-tab-pane label="我的额度" name="quota">
        <div class="mine__quota-head">
          <el-tag :type="quotaMeta.type" size="small">{{ quotaMeta.label }}</el-tag>
          <span class="mine__quota-headline">{{ quota.headline }}</span>
          <el-button link type="primary" size="small" :loading="quotaLoading" @click="loadQuota">刷新</el-button>
        </div>
        <ul v-if="quota.lines.length" class="mine__quota-lines">
          <li v-for="line in quota.lines" :key="line">{{ line }}</li>
        </ul>
        <div class="mine__note">
          统计口径：调用<b>次数</b>（含失败——调用发生过就算），自然日 / 自然月。无配额行 = 不限。
          这里是<b>你自己</b>的额度，看不到别人的。
        </div>
      </el-tab-pane>

      <!-- 我的申请：状态 / 有效期 / 续期入口 -->
      <el-tab-pane label="我的申请" name="approvals">
        <div class="mine__filter">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 160px" @change="loadApprovals">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button icon="Refresh" @click="loadApprovals">刷新</el-button>
        </div>
        <el-table v-loading="approvalLoading" :data="approvals" border size="small">
          <el-table-column label="能力" prop="capabilityCode" min-width="150" />
          <el-table-column label="数据等级" prop="dataLevel" width="120" />
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag size="small" :type="approvalStatusMeta(row).type">{{ approvalStatusMeta(row).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="授权有效期止" prop="validUntil" width="170" />
          <el-table-column label="审批意见" prop="decisionRemark" min-width="150" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="canApply && renewable(row)"
                link
                type="primary"
                size="small"
                @click="openRenew(row)"
              >
                重新申请
              </el-button>
              <span v-else class="mine__muted">—</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="renewHint" class="mine__note">{{ renewHint }}</div>
        <pagination
          v-if="total > 0"
          v-model:page="query.pageNum"
          v-model:limit="query.pageSize"
          :total="total"
          @pagination="loadApprovals"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 重新申请（续期 = 新单，旧单不会被延长） -->
    <el-dialog v-model="renewVisible" title="重新提交调用授权申请" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="能力">
          <el-input v-model="renewForm.capabilityCode" disabled />
        </el-form-item>
        <el-form-item label="数据等级">
          <el-input v-model="renewForm.dataLevel" disabled />
        </el-form-item>
        <el-form-item label="申请理由">
          <el-input v-model="renewForm.reason" type="textarea" :rows="3" placeholder="说清用途与必要性；上次被驳回的请一并说明已如何补正" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renewVisible = false">取消</el-button>
        <el-button type="primary" :loading="renewing" @click="doRenew">提交申请</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { myApproval, applyApproval } from '@/api/aigov/approval';
import type { AigCallApprovalForm, AigCallApprovalVO } from '@/api/aigov/approval/types';
import { myQuotaUsage } from '@/api/aigov/quota';
import type { AigUserQuotaUsageVO } from '@/api/aigov/quota/types';
import modal from '@/plugins/modal';
import { checkPermi } from '@/utils/permission';
import { approvalStatusMeta, quotaSummary, renewable as renewableRow, renewalHint } from './presentation';

/**
 * 员工自助：我的额度 / 我的申请（Q5）。
 *
 * <p><b>为什么放在门户而不是治理台</b>：这是"员工看自己的"两屏——治理台是治理者看全体的地方，
 * 把自助页塞进治理台导航会让治理台从 6 个模块继续膨胀，也会让人误以为需要治理权限。
 * 因此做成门户工具栏里的一个弹窗（两页签），并沿用后端"只看自己、不需要权限点"的
 * {@code /aigov/quota/my-usage} 与 {@code /aigov/approval/my}。</p>
 *
 * <p><b>唯一需要权限的动作是「重新申请」</b>：提交申请要 {@code aig:approval:apply}，
 * 所以没有该权限时按钮不出现，并在提示里说清"找谁"——而不是让人点了才收到 403。</p>
 */
const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void }>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
});

const activeTab = ref('quota');
const quotaLoading = ref(false);
const quota = ref(quotaSummary(undefined));
const usage = ref<AigUserQuotaUsageVO>();

const approvalLoading = ref(false);
const approvals = ref<AigCallApprovalVO[]>([]);
const total = ref(0);
const query = ref<{ pageNum: number; pageSize: number; status?: string }>({ pageNum: 1, pageSize: 10 });
const statusOptions = [
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'EXPIRED', label: '已超时未批' },
  { value: 'CANCELLED', label: '已撤回' }
];

const canApply = computed(() => checkPermi(['aig:approval:apply']));

const quotaMeta = computed(() => {
  switch (quota.value.tone) {
    case 'ok':
      return { label: '正常', type: 'success' as const };
    case 'exceeded':
      return { label: '已超限', type: 'danger' as const };
    case 'unlimited':
      return { label: '不限', type: 'info' as const };
    default:
      return { label: '读不到', type: 'warning' as const };
  }
});

/** 这一行是否可以重新申请（续期只能靠新单） */
const renewable = (row: AigCallApprovalVO) => renewableRow(row);

/** 当前列表的续期提示：取第一行即可（同一批单子的处置口径一致） */
const renewHint = computed(() => (approvals.value.length ? renewalHint(approvals.value[0], canApply.value) : ''));

const loadQuota = async () => {
  quotaLoading.value = true;
  try {
    const res: any = await myQuotaUsage();
    usage.value = res.data;
    quota.value = quotaSummary(res.data);
  } catch {
    // 读不到就如实说"读不到"：显示 0 会让人以为"没有额度"
    usage.value = undefined;
    quota.value = quotaSummary(undefined);
  } finally {
    quotaLoading.value = false;
  }
};

const loadApprovals = async () => {
  approvalLoading.value = true;
  try {
    const res: any = await myApproval(query.value as any);
    approvals.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  } catch (error: any) {
    modal.alertError(error?.message || '读取我的申请失败');
    approvals.value = [];
    total.value = 0;
  } finally {
    approvalLoading.value = false;
  }
};

const handleOpen = () => {
  loadQuota();
  loadApprovals();
};

const renewVisible = ref(false);
const renewing = ref(false);
const renewForm = ref<AigCallApprovalForm>({});

const openRenew = (row: AigCallApprovalVO) => {
  renewForm.value = { capabilityCode: row.capabilityCode, dataLevel: row.dataLevel, reason: '' };
  renewVisible.value = true;
};

const doRenew = async () => {
  if (!renewForm.value.reason || !renewForm.value.reason.trim()) {
    modal.msgWarning('请填写申请理由：审批人需要据此判断是否放行');
    return;
  }
  renewing.value = true;
  try {
    await applyApproval(renewForm.value);
    modal.msgSuccess('已提交申请：可在「我的申请」里跟踪状态');
    renewVisible.value = false;
    await loadApprovals();
  } catch (error: any) {
    modal.alertError(error?.message || '提交申请失败');
  } finally {
    renewing.value = false;
  }
};
</script>

<style scoped>
.mine__quota-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mine__quota-headline {
  flex: 1;
  font-size: 13px;
}

.mine__quota-lines {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
}

.mine__filter {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}

.mine__note {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.mine__muted {
  color: var(--el-text-color-placeholder);
}
</style>
