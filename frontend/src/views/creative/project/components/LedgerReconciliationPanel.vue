<template>
  <div class="ledger-reconcile">
    <div class="reconcile-head">
      <el-button size="small" text type="primary" @click="toggle">
        {{ open ? '收起父子对账' : '父子对账' }}
      </el-button>
      <span v-if="loaded && summary" class="muted">
        共 {{ summary.total }} 候选 · 一致 {{ summary.consistent }} · 漂移 {{ summary.drifted }} ·
        未登记 {{ summary.unregistered }}
      </span>
      <el-tag v-if="loaded && summary && summary.drifted > 0" size="small" type="danger" effect="dark">
        有漂移
      </el-tag>
      <el-button v-if="open" size="small" plain :loading="loading" @click="load">刷新</el-button>
    </div>

    <p v-if="!open" class="hint">
      父＝场景派发出这条项目的平台任务，子＝每个候选登记的治理任务。两套账由不同代码推进，
      这里把两边并排摆出来，看有没有对不上。
    </p>

    <template v-else>
      <p v-if="error" class="reconcile-error">{{ error }}</p>
      <p v-else-if="!loaded" class="muted">读取中…</p>

      <template v-else>
        <p v-if="summary?.parentMissing" class="reconcile-error">
          项目标记了来源平台任务，但那条任务读不到——数据缺失（可能已被清理）。请查平台任务是否还在。
        </p>
        <p v-else-if="parent" class="hint">
          父任务：<b>{{ parent.taskNo || parent.taskId }}</b>
          <el-tag size="small" effect="plain">{{ parent.status || '—' }}</el-tag>
          · 场景 {{ parent.scenarioCode || '—' }}
          · 执行方 {{ parent.executionMode || '—' }}
          <template v-if="parent.externalRef">· 项目引用 {{ parent.externalRef }}</template>
        </p>
        <p v-else class="hint">本项目不是由岗位场景派发创建，没有父平台任务。</p>

        <p v-if="!children.length" class="muted">还没有候选——先出图，再回来看对账。</p>
        <el-table v-else :data="children" size="small">
          <el-table-column label="候选" width="70">
            <template #default="{ row }">#{{ row.candidateNo ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="候选状态" prop="candidateStatus" width="110" />
          <el-table-column label="应记任务状态" width="130">
            <template #default="{ row }">{{ row.expectedLedgerStatus || '—' }}</template>
          </el-table-column>
          <el-table-column label="治理任务" width="180">
            <template #default="{ row }">
              {{ row.ledgerTaskNo || (row.ledgerTaskId ? String(row.ledgerTaskId) : '未登记') }}
            </template>
          </el-table-column>
          <el-table-column label="任务状态" width="120">
            <template #default="{ row }">{{ row.ledgerStatus || '—' }}</template>
          </el-table-column>
          <el-table-column label="对账" min-width="240">
            <template #default="{ row }">
              <el-tag v-if="row.consistent === true" size="small" type="success">一致</el-tag>
              <el-tag v-else-if="row.consistent === false" size="small" type="danger">漂移</el-tag>
              <el-tag v-else size="small" type="info">判不了</el-tag>
              <span v-if="row.drift" class="muted drift">{{ row.drift }}</span>
            </template>
          </el-table-column>
        </el-table>

        <p class="hint">
          只读：本视图不刷新内核状态、不回写任务——候选的重试/选定/质检仍在各自入口做。
        </p>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { getLedgerReconciliation } from '@/api/creative';
import type { CreativeLedgerReconciliationVO } from '@/api/creative/types';

/**
 * 项目页候选区块里的「父子对账」抽屉（增量 19 前端）。
 *
 * <p><b>为什么折叠且按需取数</b>：这是观测面。项目页每次打开都去拉一次对账没有意义，
 * 还会给"看项目"这个最常用的动作加一次往返；展开时才取、可手动刷新。</p>
 *
 * <p><b>为什么只读</b>：后端这条接口本身就不刷新内核状态（浏览对账不该把出图跑起来）；
 * 前端也不提供任何改状态的入口——候选的重试/选定/质检仍在它们自己的入口上。</p>
 *
 * @author creative
 */
const props = defineProps<{
  /** 当前项目ID（为空时不取数） */
  taskId?: string | number | null;
}>();

const open = ref(false);
const loading = ref(false);
const loaded = ref(false);
const error = ref('');
const data = ref<CreativeLedgerReconciliationVO | null>(null);

const parent = computed(() => data.value?.parent ?? null);
const children = computed(() => data.value?.children ?? []);
const summary = computed(() => data.value?.summary ?? null);

/** 取对账；切项目时丢弃旧值，避免把上一个项目的对账冒充成本项目的 */
async function load(): Promise<void> {
  const id = props.taskId;
  data.value = null;
  loaded.value = false;
  error.value = '';
  if (!id) {
    return;
  }
  loading.value = true;
  try {
    const res = await getLedgerReconciliation(id);
    if (String(props.taskId) !== String(id)) {
      return;
    }
    data.value = res.data ?? null;
  } catch (e) {
    if (String(props.taskId) === String(id)) {
      error.value = '父子对账没取到（接口失败）：' + ((e as { message?: string })?.message || '未知原因');
    }
  } finally {
    if (String(props.taskId) === String(id)) {
      loaded.value = true;
      loading.value = false;
    }
  }
}

/** 展开/收起；展开时按需取一次 */
async function toggle(): Promise<void> {
  open.value = !open.value;
  if (open.value && !loaded.value) {
    await load();
  }
}

// 切项目：收起并清空（下一次展开重新取），避免残留上一个项目的结论
watch(
  () => props.taskId,
  () => {
    open.value = false;
    data.value = null;
    loaded.value = false;
    error.value = '';
  }
);
</script>

<style scoped lang="scss">
.ledger-reconcile {
  padding-top: 10px;
  margin-top: 12px;
  border-top: 1px dashed var(--line);
}

.reconcile-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.reconcile-error {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: #fbbf24;
}

.drift {
  margin-left: 8px;
  font-size: 12px;
}
</style>
