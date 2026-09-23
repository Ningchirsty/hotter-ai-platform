<template>
  <div class="studio">
    <header class="prod-head">
      <div>
        <h2>AI 生产中心</h2>
        <p class="muted">
          跨项目的出图候选总览。未结束的候选在刷新时会向出图内核要一次真实状态，
          因此这里显示的状态就是内核里的状态；重试＝新增一次候选（保留历史，不覆盖）。
        </p>
      </div>
      <div class="head-actions">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px" @change="load">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <el-table v-loading="loading" :data="rows" class="prod-table" empty-text="还没有出图候选">
      <el-table-column label="预览" width="90">
        <template #default="{ row }">
          <div class="thumb" @click="asGen(row).previewable && openPreview(asGen(row))">
            <img v-if="thumbUrl(asGen(row))" :src="thumbUrl(asGen(row))" :alt="`候选 ${asGen(row).candidateNo}`" />
            <span v-else class="thumb-empty">—</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="项目" min-width="200">
        <template #default="{ row }">
          <div class="cell-main">{{ row.taskName || row.taskId }}</div>
          <div class="cell-sub">{{ row.workflowCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="候选" width="80">
        <template #default="{ row }">#{{ row.candidateNo }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <span class="gen-status" :class="'is-' + statusType(row.status)">
            {{ row.statusDesc || row.status }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="尺寸" width="120">
        <template #default="{ row }">
          {{ row.outputWidth ? `${row.outputWidth}×${row.outputHeight}` : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">
          {{ row.durationMs ? `${(row.durationMs / 1000).toFixed(1)}s` : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="失败/提示" min-width="200">
        <template #default="{ row }">
          <span :class="row.errorMessage ? 'gen-error' : 'cell-sub'">{{ row.errorMessage || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-if="asGen(row).previewable" size="small" text type="primary" @click="openPreview(asGen(row))">
            预览
          </el-button>
          <el-button
            v-if="asGen(row).retryable"
            size="small"
            text
            type="warning"
            :loading="retryingId === String(asGen(row).id)"
            @click="doRetry(asGen(row))"
          >
            重试
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="previewVisible" title="候选预览" width="720px" @closed="closePreview">
      <div class="preview-wrap">
        <img v-if="previewUrl" :src="previewUrl" alt="候选原图" />
        <p v-else class="muted">加载中…</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  fetchGenerationPreviewBlobUrl,
  fetchGenerationThumbnailBlobUrl,
  listProductions,
  retryGeneration
} from '@/api/creative';
import type { DpGenerationVO } from '@/api/creative/types';
import { GENERATION_STATUS_LABELS, GENERATION_STATUS_TYPES } from '@/api/creative/types';

const statusOptions = Object.entries(GENERATION_STATUS_LABELS).map(([value, label]) => ({ value, label }));

const rows = ref<DpGenerationVO[]>([]);
const loading = ref(false);
const status = ref('');
const pageNum = ref(1);
const pageSize = ref(20);
const total = ref(0);
const retryingId = ref('');
const previewVisible = ref(false);
const previewUrl = ref('');
const thumbs = ref<Record<string, string>>({});

function thumbUrl(row: DpGenerationVO): string {
  return thumbs.value[String(row.id)] || '';
}

/**
 * el-table 的插槽行类型是 DefaultRow（不含我们的字段），
 * 数据本身来自 DpGenerationVO，故在模板里做一次显式收窄，而不是把函数参数放宽成 any。
 */
function asGen(row: unknown): DpGenerationVO {
  return row as DpGenerationVO;
}

function statusType(value?: string): string {
  return (value && GENERATION_STATUS_TYPES[value]) || 'info';
}

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '';
}

async function load() {
  loading.value = true;
  try {
    const res = await listProductions({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: status.value || undefined
    });
    rows.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
    void loadThumbs();
  } catch (error) {
    ElMessage.error(await extractErrorMessage(error) ?? '加载生产列表失败');
  } finally {
    loading.value = false;
  }
}

async function loadThumbs() {
  for (const row of rows.value) {
    const key = String(row.id);
    if (!row.previewable) {
      releaseThumb(key);
      continue;
    }
    if (thumbs.value[key]) continue;
    try {
      const url = await fetchGenerationThumbnailBlobUrl(row.id);
      thumbs.value = { ...thumbs.value, [key]: url };
    } catch {
      /* 缩略图失败不阻断列表 */
    }
  }
}

function releaseThumb(key: string) {
  const url = thumbs.value[key];
  if (url) {
    URL.revokeObjectURL(url);
    const next = { ...thumbs.value };
    delete next[key];
    thumbs.value = next;
  }
}

async function openPreview(row: DpGenerationVO) {
  previewVisible.value = true;
  try {
    const url = await fetchGenerationPreviewBlobUrl(row.id);
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = url;
  } catch (error) {
    ElMessage.error(await extractErrorMessage(error) ?? '读取产出图失败');
  }
}

function closePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = '';
}

async function doRetry(row: DpGenerationVO) {
  retryingId.value = String(row.id);
  try {
    await retryGeneration(row.id);
    ElMessage.success('已新建一次候选');
    await load();
  } catch (error) {
    ElMessage.error(await extractErrorMessage(error) ?? '重试失败');
  } finally {
    retryingId.value = '';
  }
}

async function extractErrorMessage(error: unknown): Promise<string | undefined> {
  const anyError = error as { message?: string; response?: { data?: unknown } };
  const data = anyError?.response?.data;
  if (data instanceof Blob) {
    try {
      const text = await data.text();
      const parsed = JSON.parse(text) as { msg?: string; message?: string };
      return parsed.msg || parsed.message || text;
    } catch {
      return anyError?.message;
    }
  }
  if (data && typeof data === 'object') {
    const parsed = data as { msg?: string; message?: string };
    return parsed.msg || parsed.message || anyError?.message;
  }
  return anyError?.message;
}

onMounted(load);

onBeforeUnmount(() => {
  Object.values(thumbs.value).forEach((url) => URL.revokeObjectURL(url));
  thumbs.value = {};
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
});
</script>

<style scoped lang="scss">
@use '@/assets/styles/tokens-studio.scss';

.studio {
  min-height: calc(100vh - 135px);
  padding: 24px;
  color: var(--t1);
  background: var(--bg);
  background-image: radial-gradient(900px 460px at 84% -10%, rgba(148, 163, 184, 0.16), transparent 68%);
  border: 1px solid var(--line);
  border-radius: 8px;
}

.prod-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--line);
}
.prod-head h2 {
  margin: 0 0 6px;
  font-size: 18px;
}
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.prod-table {
  background: transparent;
}
.prod-table :deep(.el-table__inner-wrapper::before) {
  background-color: var(--line);
}
.prod-table :deep(th.el-table__cell) {
  color: var(--t2);
  font-weight: 500;
  background: var(--surface);
  border-bottom-color: var(--line);
}
.prod-table :deep(td.el-table__cell) {
  color: var(--t1);
  background: transparent;
  border-bottom-color: var(--line);
}
.prod-table :deep(tr:hover > td.el-table__cell) {
  background: rgba(148, 163, 184, 0.06);
}

.thumb {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  overflow: hidden;
  cursor: pointer;
  background: #05070a;
  border-radius: 4px;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb-empty {
  color: var(--t3);
}

.cell-main {
  font-size: 13px;
}
.cell-sub {
  font-size: 12px;
  color: var(--t3);
}

.gen-status {
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 8px;
}
.gen-status.is-primary {
  color: #c7d2fe;
  background: rgba(99, 102, 241, 0.18);
}
.gen-status.is-success {
  color: #a7f3d0;
  background: rgba(16, 185, 129, 0.18);
}
.gen-status.is-danger {
  color: #fecaca;
  background: rgba(239, 68, 68, 0.18);
}
.gen-status.is-warning {
  color: #fde68a;
  background: rgba(245, 158, 11, 0.18);
}
.gen-status.is-info {
  color: var(--t2);
  background: rgba(148, 163, 184, 0.16);
}

.gen-error {
  font-size: 12px;
  color: #fca5a5;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

.preview-wrap {
  display: grid;
  place-items: center;
  min-height: 200px;
}
.preview-wrap img {
  max-width: 100%;
  max-height: 62vh;
  border-radius: 6px;
}

.muted {
  margin: 0;
  font-size: 13px;
  line-height: 1.9;
  color: var(--t2);
}
</style>
