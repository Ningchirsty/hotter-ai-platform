<template>
  <el-drawer v-model="visible" title="高风险变更单" size="900px" :destroy-on-close="false">
    <!--
      高风险变更单抽屉（Q1C 完整版）：申请 → 批准 → 生效记录。
      刻意不做成菜单页：它挂在「能力清单」上（改动都是针对能力的），不新增菜单行/权限点。
      （注释写在根元素内部——根级注释会被算成第二个根节点，构建期守卫会红。）
    -->
    <div class="cr__bar">
      <el-radio-group v-model="scope" @change="reload">
        <el-radio-button value="PENDING">待批准</el-radio-button>
        <el-radio-button value="MINE">我提交的</el-radio-button>
        <el-radio-button value="ALL">全部</el-radio-button>
      </el-radio-group>
      <el-input v-model="capabilityCode" clearable placeholder="按能力编码过滤" style="width: 220px" @keyup.enter="reload" />
      <el-button :loading="loading" @click="reload">刷新</el-button>
      <el-button v-if="canEdit" type="primary" @click="openSubmit">提交变更单</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-2"
      title="批准即执行：批准人会当场把改动应用到线上配置；执行前会与现场比对，不一致则置为「冲突未生效」，不会覆盖别人的改动。申请人不得自批。"
    />

    <el-table v-loading="loading" :data="rows" border size="small">
      <template #empty>
        <AigEmptyState
          icon="DocumentChecked"
          title="没有符合条件的变更单"
          :hint="scope === 'PENDING' ? '没有待批准的变更，说明当前没有高风险改动在等审批。' : '换个范围看看，或先提交一张变更单。'"
        >
          <el-button size="small" @click="reload">刷新</el-button>
        </AigEmptyState>
      </template>
      <el-table-column label="能力" prop="capabilityCode" min-width="150" show-overflow-tooltip />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="typeMeta(row.changeType).type">{{ typeMeta(row.changeType).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="改动" min-width="240">
        <template #default="{ row }">
          <div v-for="line in rowLines(row)" :key="line" class="cr__line">{{ line }}</div>
          <div v-if="!rowLines(row).length" class="cr__muted">（无目标值：删除策略）</div>
        </template>
      </el-table-column>
      <el-table-column label="申请人" prop="requesterName" width="110" show-overflow-tooltip />
      <el-table-column label="结论" min-width="200">
        <template #default="{ row }">{{ outcomeText(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button v-if="canApprove" link type="primary" size="small" @click="doApprove(row)">批准并生效</el-button>
            <el-button v-if="canApprove" link type="danger" size="small" @click="doReject(row)">驳回</el-button>
            <el-button v-if="canEdit" link size="small" @click="doCancel(row)">撤回</el-button>
          </template>
          <span v-else class="cr__muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-if="total > 0"
      v-model:page="query.pageNum"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="load"
    />

    <!-- 提交：目标值用显式字段，审批人才能逐项对比 -->
    <el-dialog v-model="submitVisible" title="提交高风险变更单" width="560px" append-to-body>
      <el-form label-width="120px">
        <el-form-item label="能力编码" required>
          <el-input v-model="form.capabilityCode" placeholder="如 image_generation" />
        </el-form-item>
        <el-form-item label="变更类型" required>
          <el-select v-model="form.changeType" style="width: 100%">
            <el-option label="新增路由策略" value="POLICY_CREATE" />
            <el-option label="修改路由策略" value="POLICY_UPDATE" />
            <el-option label="删除路由策略" value="POLICY_DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.changeType !== 'POLICY_CREATE'" label="目标策略ID" required>
          <el-input v-model="form.targetRef" placeholder="策略行的 policyId（在路由策略页可复制）" />
        </el-form-item>
        <template v-if="form.changeType !== 'POLICY_DELETE'">
          <el-form-item label="数据等级" required>
            <el-input v-model="form.dataLevel" placeholder="PUBLIC / INTERNAL / RESTRICTED / STRICT" />
          </el-form-item>
          <el-form-item label="允许外发">
            <el-select v-model="form.allowExternal" clearable style="width: 100%" placeholder="不改就留空">
              <el-option label="允许（Y）" value="Y" />
              <el-option label="禁止（N）" value="N" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先部署类型">
            <el-input v-model="form.preferredDeployment" placeholder="可空=不做优先排序" />
          </el-form-item>
          <el-form-item label="调用前审批">
            <el-select v-model="form.requireApproval" clearable style="width: 100%" placeholder="不改就留空">
              <el-option label="需要（Y）" value="Y" />
              <el-option label="不需要（N）" value="N" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="申请理由" required>
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="说清为什么必须这么改；批准人要据此判断" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doSubmit">提交</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  approveChangeRequest,
  cancelChangeRequest,
  listChangeRequest,
  listMyChangeRequest,
  rejectChangeRequest,
  submitChangeRequest
} from '@/api/aigov/changeRequest';
import type { AigChangeRequestForm, AigChangeRequestVO } from '@/api/aigov/changeRequest/types';
import modal from '@/plugins/modal';
import { checkPermi } from '@/utils/permission';
import AigEmptyState from '../components/AigEmptyState.vue';
import { outcomeText, snapshotLines, statusMeta, typeMeta } from './presentation';

/**
 * 高风险变更单（Q1C 完整版）：申请 → 批准 → 生效记录。
 *
 * <p><b>为什么挂在能力清单而不是新开一个菜单页</b>：改动都是针对能力的（本轮覆盖路由策略三件套），
 * 放进已有页面既不新增菜单行也不改授权；审批人照样能在这里看到待批的单子。</p>
 *
 * <p><b>批准即执行</b>：批准会当场应用改动，所以这里必须把结果分三种说清——
 * 已生效 / **冲突未生效** / 已驳回。把冲突说成"成功"是这个功能里最糟的一种错。</p>
 */
const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void }>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
});

const scope = ref<'PENDING' | 'MINE' | 'ALL'>('PENDING');
const capabilityCode = ref('');
const rows = ref<AigChangeRequestVO[]>([]);
const total = ref(0);
const loading = ref(false);
const query = ref<{ pageNum: number; pageSize: number }>({ pageNum: 1, pageSize: 10 });

const canEdit = computed(() => checkPermi(['aig:capability:edit']));
const canApprove = computed(() => checkPermi(['aig:capability:high-risk']));

/** 改动对比（只列真正变化的键，没有旧值时全列） */
const rowLines = (row: AigChangeRequestVO): string[] =>
  snapshotLines(row.afterJson, row.beforeJson);

const reload = () => {
  query.value.pageNum = 1;
  load();
};

const load = async () => {
  loading.value = true;
  try {
    const params = {
      capabilityCode: capabilityCode.value || undefined,
      filterStatus: scope.value === 'PENDING' ? 'PENDING' : undefined,
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize
    };
    const res: any = scope.value === 'MINE'
      ? await listMyChangeRequest(params)
      : await listChangeRequest(params);
    rows.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  } catch (error: any) {
    modal.alertError(error?.message || '读取变更单失败');
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

watch(() => props.modelValue, (open) => {
  if (open) {
    reload();
  }
});

const submitVisible = ref(false);
const submitting = ref(false);
const form = ref<AigChangeRequestForm>({ changeType: 'POLICY_UPDATE' });

const openSubmit = () => {
  form.value = { changeType: 'POLICY_UPDATE', capabilityCode: capabilityCode.value || '' };
  submitVisible.value = true;
};

const doSubmit = async () => {
  if (!form.value.capabilityCode || !form.value.changeType) {
    modal.msgWarning('能力编码与变更类型必填');
    return;
  }
  if (form.value.changeType !== 'POLICY_CREATE' && !form.value.targetRef) {
    modal.msgWarning('修改/删除策略必须填目标策略ID');
    return;
  }
  if (form.value.changeType !== 'POLICY_DELETE' && !form.value.dataLevel) {
    modal.msgWarning('新增/修改策略必须填数据等级');
    return;
  }
  if (!form.value.reason || !form.value.reason.trim()) {
    modal.msgWarning('请写申请理由：批准人要据此判断');
    return;
  }
  submitting.value = true;
  try {
    await submitChangeRequest(form.value);
    modal.msgSuccess('已提交：等另一位持有「能力高风险变更」权限的同事批准（批准即生效）');
    submitVisible.value = false;
    scope.value = 'MINE';
    reload();
  } catch (error: any) {
    modal.alertError(error?.message || '提交失败');
  } finally {
    submitting.value = false;
  }
};

const doApprove = async (row: AigChangeRequestVO) => {
  let remark = '';
  try {
    const result = await modal.prompt('批准意见（可留空）。注意：批准会**当场执行**这次改动。');
    remark = result?.value ?? '';
  } catch {
    return;
  }
  try {
    const res: any = await approveChangeRequest(row.changeRequestId!, remark);
    const applied = res?.data?.status === 'APPLIED';
    if (applied) {
      modal.msgSuccess('已批准并生效');
    } else {
      // 冲突/执行失败：必须如实说"没生效"，别显示成功
      modal.alertError(outcomeText(res?.data) || '变更未生效');
    }
    await load();
  } catch (error: any) {
    modal.alertError(error?.message || '批准失败');
  }
};

const doReject = async (row: AigChangeRequestVO) => {
  let remark = '';
  try {
    const result = await modal.prompt('驳回理由（必填）：申请人需要知道该怎么改。');
    remark = result?.value ?? '';
  } catch {
    return;
  }
  if (!remark.trim()) {
    modal.msgWarning('驳回必须写明理由');
    return;
  }
  try {
    await rejectChangeRequest(row.changeRequestId!, remark);
    modal.msgSuccess('已驳回');
    await load();
  } catch (error: any) {
    modal.alertError(error?.message || '驳回失败');
  }
};

const doCancel = async (row: AigChangeRequestVO) => {
  try {
    await modal.confirm('撤回这张变更单？撤回后不会生效，需要时可重新提交。');
  } catch {
    return;
  }
  try {
    await cancelChangeRequest(row.changeRequestId!);
    modal.msgSuccess('已撤回');
    await load();
  } catch (error: any) {
    modal.alertError(error?.message || '撤回失败');
  }
};
</script>

<style scoped>
.cr__bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.cr__line {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  line-height: 1.6;
}

.cr__muted {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
