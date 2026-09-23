<template>
  <div class="studio">
    <header class="page-head">
      <div>
        <h2>详情页与审核</h2>
        <p class="muted">
          视觉门是进入批量出图的<b>唯一入口</b>：硬性项不满足连提交都不允许；提交后由人确认或打回。
          <b>未通过视觉门，出图接口会直接拒绝</b>——门禁在后端强制，不是前端按钮变灰。
        </p>
      </div>
      <div class="head-actions">
        <el-select v-model="taskId" placeholder="选择视觉项目" filterable style="width: 260px" @change="loadAll">
          <el-option
            v-for="project in projects"
            :key="String(project.taskId)"
            :label="project.taskName || String(project.taskId)"
            :value="String(project.taskId)"
          />
        </el-select>
        <el-button plain :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </header>

    <p v-if="!projects.length" class="empty">还没有视觉项目。先到「视觉项目」页新建一个。</p>

    <template v-else-if="gate">
      <!-- 门禁状态 -->
      <section class="panel">
        <div class="gate-head">
          <div>
            <h3>
              视觉门
              <el-tag v-if="gate.passed" type="success" effect="dark">已通过</el-tag>
              <el-tag v-else-if="gate.cardStatus === 'PENDING'" type="warning" effect="dark">待人工确认</el-tag>
              <el-tag v-else-if="gate.cardStatus === 'BLOCKED'" type="danger" effect="dark">已打回</el-tag>
              <el-tag v-else type="info" effect="dark">未提交</el-tag>
            </h3>
            <p class="muted">
              当前视觉阶段：{{ gate.stageDesc }}（{{ gate.stage }}）
              <template v-if="gate.cardId">　确认项卡号：{{ gate.cardId }}</template>
            </p>
          </div>
          <div class="gate-actions">
            <el-button
              type="primary"
              :disabled="!gate.submittable"
              :loading="submitting"
              @click="doSubmit"
            >
              {{ gate.cardStatus === 'PENDING' ? '重新提交（已有待确认项）' : '提交视觉门审核' }}
            </el-button>
          </div>
        </div>

        <el-alert
          v-if="!gate.submittable"
          type="warning"
          show-icon
          :closable="false"
          class="gate-alert"
          title="硬性项未满足，暂不能提交"
        >
          <ul class="issue-list">
            <li v-for="(item, index) in gate.blocked" :key="index">{{ item }}</li>
          </ul>
        </el-alert>
        <el-alert
          v-else-if="!gate.passed"
          type="info"
          show-icon
          :closable="false"
          class="gate-alert"
          title="硬性项已满足，可提交人工确认"
        />
      </section>

      <!-- 准入项 -->
      <section class="panel">
        <div class="block-head">
          <h3>准入项</h3>
          <span class="muted">硬性项（BLOCK）不满足时不能提交；建议项（CONDITION）只提示</span>
        </div>
        <el-table :data="gate.items" size="small">
          <el-table-column label="等级" width="110">
            <template #default="{ row }">
              <el-tag :type="asItem(row).level === 'BLOCK' ? 'danger' : 'info'" size="small">
                {{ asItem(row).level === 'BLOCK' ? '硬性' : '建议' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="label" label="准入项" width="200" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <span :class="asItem(row).passed ? 'good' : 'bad'">{{ asItem(row).passed ? '已满足' : '未满足' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="依据 / 说明" min-width="360" show-overflow-tooltip />
        </el-table>
      </section>

      <!-- 人工确认 -->
      <section class="panel">
        <div class="block-head">
          <h3>人工确认</h3>
          <span class="muted">确认后出图放行；打回会阻断内容任务流转并回到视觉门</span>
        </div>
        <div class="review-row">
          <el-input
            v-model="comment"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="意见（打回时建议写明要改什么）"
          />
          <div class="review-actions">
            <el-button
              type="success"
              :disabled="gate.cardStatus !== 'PENDING'"
              :loading="reviewing === 'CONFIRM'"
              @click="doReview('CONFIRM')"
            >
              确认方案，允许出图
            </el-button>
            <el-button
              type="danger"
              plain
              :disabled="gate.cardStatus !== 'PENDING'"
              :loading="reviewing === 'BLOCK'"
              @click="doReview('BLOCK')"
            >
              打回
            </el-button>
          </div>
        </div>
        <p v-if="gate.cardStatus !== 'PENDING'" class="muted">
          当前没有待确认项：{{ gate.cardStatus === 'RESOLVED' ? '已确认通过' : (gate.cardStatus === 'BLOCKED' ? '已被打回，请修改方案后重新提交' : '请先提交视觉门审核') }}
        </p>
      </section>

      <!-- 机排版与终审（R3） -->
      <section class="panel">
        <div class="block-head">
          <h3>机排版与终审</h3>
          <div class="head-actions">
            <el-tag :type="detailPage?.rendererAvailable ? 'success' : 'danger'" size="small">
              {{ detailPage?.rendererAvailable ? '渲染服务可达' : '渲染服务不可达' }}
            </el-tag>
            <span class="muted">
              模板 {{ detailPage?.templateKey || '—' }} · 当前版本 v{{ detailPage?.currentVersion ?? 0 }}
              （{{ detailPage?.statusDesc || '未排版' }}）
            </span>
            <el-button size="small" plain :loading="rendering" @click="doRender">
              {{ (detailPage?.currentVersion ?? 0) > 0 ? '重新渲染 V0.8' : '渲染机排版 V0.8' }}
            </el-button>
          </div>
        </div>

        <p class="muted">
          渲染会把每屏<b>已选定</b>的产出与分镜文案排成 750×N 长图；没有已选定产出的屏会在图上明确画出
          「这一屏还没有产出」，不会留白糊弄。
        </p>
        <el-alert
          v-if="(detailPage?.screensWithoutSelection || []).length"
          type="warning"
          show-icon
          :closable="false"
          class="gate-alert"
          :title="`以下屏还没有已选定的产出：${(detailPage?.screensWithoutSelection || []).join('、')}`"
        />
        <el-alert
          v-if="detailPage && detailPage.rendererAvailable === false"
          type="error"
          show-icon
          :closable="false"
          class="gate-alert"
          title="渲染服务不可达，无法排版（请确认 creative-renderer 容器已启动）"
        />

        <el-table
          v-if="(detailPage?.versions || []).length"
          :data="detailPage?.versions || []"
          size="small"
          class="version-table"
        >
          <el-table-column label="版本" width="150">
            <template #default="{ row }">
              <div class="cell-main">v{{ asVersion(row).version }} · {{ asVersion(row).kindDesc }}</div>
              <div class="muted small">{{ asVersion(row).createTime }}</div>
            </template>
          </el-table-column>
          <el-table-column label="长图" width="130">
            <template #default="{ row }">
              {{ asVersion(row).pageWidth }}×{{ asVersion(row).pageHeight }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag size="small" :type="versionStatusType(asVersion(row).status)">
                {{ LAYOUT_VERSION_STATUS_LABELS[asVersion(row).status || ''] || asVersion(row).status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="渲染证据 / 审核意见" min-width="280">
            <template #default="{ row }">
              <div class="muted small">{{ asVersion(row).remark || '—' }}</div>
              <div v-if="asVersion(row).reviewComment" class="review-line">
                审核：{{ asVersion(row).reviewComment }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                text
                type="primary"
                :disabled="!asVersion(row).previewable"
                :loading="previewingId === String(asVersion(row).id)"
                @click="doPreview(asVersion(row))"
              >
                预览长图
              </el-button>
              <el-button
                size="small"
                text
                type="success"
                :disabled="asVersion(row).status !== 'RENDERED'"
                :loading="reviewingId === String(asVersion(row).id)"
                @click="doVersionReview(asVersion(row), true)"
              >
                通过
              </el-button>
              <el-button
                size="small"
                text
                type="danger"
                :disabled="asVersion(row).status !== 'RENDERED'"
                :loading="reviewingId === String(asVersion(row).id)"
                @click="doVersionReview(asVersion(row), false)"
              >
                打回
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <p v-else class="empty">
          还没有排版版本。先在上面通过视觉门、到「视觉方向与分镜」页逐屏出图并选定候选，再回来渲染。
        </p>

        <div v-if="(detailPage?.currentVersion ?? 0) > 0" class="final-row">
          <div class="final-hint">
            <b>交付最终版（V1.0）</b>
            <span class="muted">
              设计师在 V0.8 基础上精修后上传长图；上传即登记为新版本并标记交付完成，历史版本全部保留。
            </span>
          </div>
          <el-upload
            :show-file-list="false"
            accept="image/png,image/jpeg"
            :http-request="doUploadFinal"
          >
            <el-button :loading="uploadingFinal">上传精修最终版</el-button>
          </el-upload>
        </div>
      </section>

      <el-dialog v-model="previewVisible" title="详情页长图预览" width="820px" @closed="closePreview">
        <div class="long-preview">
          <img v-if="previewUrl" :src="previewUrl" alt="详情页长图" />
          <p v-else class="muted">加载中…</p>
        </div>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { UploadRequestOptions } from 'element-plus';
import {
  fetchDetailPreviewBlobUrl,
  getDetailPage,
  getVisualGate,
  listCreativeProject,
  renderDetailPage,
  reviewDetailVersion,
  reviewVisualGate,
  submitVisualGate,
  uploadDetailFinal
} from '@/api/creative';
import type {
  CreativeProjectVO,
  DpDetailPageVO,
  DpDetailPageVersionVO,
  GateEvaluationVO,
  GateItem,
  TagType
} from '@/api/creative/types';
import { LAYOUT_VERSION_STATUS_LABELS } from '@/api/creative/types';

const projects = ref<CreativeProjectVO[]>([]);
const taskId = ref('');
const gate = ref<GateEvaluationVO | null>(null);
const detailPage = ref<DpDetailPageVO | null>(null);
const loading = ref(false);
const submitting = ref(false);
const reviewing = ref('');
const comment = ref('');
const rendering = ref(false);
const reviewingId = ref('');
const previewingId = ref('');
const uploadingFinal = ref(false);
const previewVisible = ref(false);
const previewUrl = ref('');

function asItem(row: unknown): GateItem {
  return row as GateItem;
}

function asVersion(row: unknown): DpDetailPageVersionVO {
  return row as DpDetailPageVersionVO;
}

function versionStatusType(status?: string): TagType {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'RENDERED') return 'warning';
  return 'info';
}

async function loadProjects() {
  const res = await listCreativeProject({ pageNum: 1, pageSize: 50 });
  projects.value = res.data?.rows || [];
  const queryTaskId = new URLSearchParams(location.search).get('taskId');
  if (queryTaskId && projects.value.some((p) => String(p.taskId) === queryTaskId)) {
    taskId.value = queryTaskId;
  } else if (projects.value.length) {
    taskId.value = String(projects.value[0].taskId);
  }
}

async function loadAll() {
  if (!taskId.value) return;
  loading.value = true;
  try {
    const [gateRes, detailRes] = await Promise.all([
      getVisualGate(taskId.value),
      getDetailPage(taskId.value)
    ]);
    gate.value = gateRes.data || null;
    detailPage.value = detailRes.data || null;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载视觉门失败');
  } finally {
    loading.value = false;
  }
}

async function doRender() {
  rendering.value = true;
  try {
    const res = await renderDetailPage(taskId.value);
    detailPage.value = res.data || null;
    const latest = (detailPage.value?.versions || [])[0];
    ElMessage.success(latest
      ? `已渲染 v${latest.version}（${latest.pageWidth}×${latest.pageHeight}）`
      : '已渲染');
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '渲染失败');
  } finally {
    rendering.value = false;
  }
}

async function doPreview(row: DpDetailPageVersionVO) {
  previewingId.value = String(row.id);
  previewVisible.value = true;
  try {
    const url = await fetchDetailPreviewBlobUrl(taskId.value, row.id);
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = url;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取长图失败');
  } finally {
    previewingId.value = '';
  }
}

function closePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = '';
}

async function doVersionReview(row: DpDetailPageVersionVO, approve: boolean) {
  if (!approve) {
    try {
      await ElMessageBox.confirm('打回后这一版需要重新渲染或修改。确认打回？', '终审打回', { type: 'warning' });
    } catch {
      return;
    }
  }
  reviewingId.value = String(row.id);
  try {
    await reviewDetailVersion(taskId.value, row.id, approve, approve ? '终审通过' : '终审打回');
    ElMessage.success(approve ? '已通过，可进入人工精修' : '已打回');
    await loadAll();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '终审失败');
  } finally {
    reviewingId.value = '';
  }
}

async function doUploadFinal(options: UploadRequestOptions) {
  uploadingFinal.value = true;
  try {
    await uploadDetailFinal(taskId.value, options.file as File, '人工精修最终版');
    ElMessage.success('最终版已上传并登记为 V1.0');
    await loadAll();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '上传失败');
  } finally {
    uploadingFinal.value = false;
  }
}

async function doSubmit() {
  submitting.value = true;
  try {
    const res = await submitVisualGate(taskId.value);
    gate.value = res.data || null;
    ElMessage.success('已提交视觉门，等待人工确认');
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '提交失败');
  } finally {
    submitting.value = false;
  }
}

async function doReview(option: 'CONFIRM' | 'BLOCK') {
  if (option === 'BLOCK') {
    try {
      await ElMessageBox.confirm(
        '打回会把视觉方案退回，并阻断内容任务的流转（内容侧会出现一张阻断卡）。确认打回？',
        '打回视觉方案',
        { type: 'warning' }
      );
    } catch {
      return;
    }
  }
  reviewing.value = option;
  try {
    const res = await reviewVisualGate(taskId.value, option, comment.value || undefined);
    gate.value = res.data || null;
    ElMessage.success(option === 'CONFIRM' ? '已确认，出图已放行' : '已打回');
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '处理失败');
  } finally {
    reviewing.value = '';
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

onMounted(async () => {
  try {
    await loadProjects();
    await loadAll();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '初始化失败');
  }
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

.page-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--line);
}
.page-head h2 {
  margin: 0 0 6px;
  font-size: 18px;
}
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.panel {
  padding: 16px;
  margin-bottom: 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.panel h3 {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 0 0 6px;
  font-size: 15px;
}

.gate-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}
.gate-actions {
  display: flex;
  gap: 8px;
}

.block-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.gate-alert {
  margin-top: 12px;
}
.issue-list {
  padding-left: 18px;
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.9;
}

.review-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.review-row .el-textarea {
  flex: 1;
}
.review-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.good {
  color: #a7f3d0;
}
.bad {
  color: #fde68a;
}

.review-line {
  margin-top: 4px;
  font-size: 12px;
  color: #a5b4fc;
}

.cell-main {
  font-size: 13px;
}
.small {
  font-size: 12px;
}

.version-table {
  margin-top: 12px;
}

.final-row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--line);
}
.final-hint {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}
.final-hint .muted {
  margin: 0;
}

.long-preview {
  display: grid;
  place-items: center;
  max-height: 70vh;
  overflow-y: auto;
}
.long-preview img {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.muted {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--t2);
}
.empty {
  padding: 12px 0;
  font-size: 13px;
  color: var(--t2);
}

.studio :deep(.el-input__wrapper),
.studio :deep(.el-textarea__inner),
.studio :deep(.el-select__wrapper) {
  background: var(--sunken);
  box-shadow: 0 0 0 1px var(--line) inset;
}
.studio :deep(.el-input__inner),
.studio :deep(.el-textarea__inner) {
  color: var(--t1);
}
.studio :deep(th.el-table__cell) {
  color: var(--t2);
  background: var(--elevated);
  border-bottom-color: var(--line);
}
.studio :deep(td.el-table__cell) {
  color: var(--t1);
  background: transparent;
  border-bottom-color: var(--line);
}
.studio :deep(.el-table),
.studio :deep(.el-table tr) {
  background: transparent;
}
</style>
