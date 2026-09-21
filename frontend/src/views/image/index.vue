<template>
  <div class="image-studio">
    <header class="studio-head">
      <div class="head-main">
        <h2>图像创作</h2>
        <p class="sub">Qwen-Image-2.1 · 文生图 / 图生图 / 指令改图 / 抠图去背景</p>
      </div>
      <div class="head-meta">
        <span class="pill">{{ activeModule.name }}</span>
        <span v-if="currentWorkflow" class="pill ghost">{{ currentWorkflow.version }} · {{ currentWorkflow.status }}</span>
      </div>
    </header>

    <nav class="studio-nav" aria-label="图像创作功能">
      <button
        v-for="view in studioViews"
        :key="view.key"
        type="button"
        :class="['nav-item', { active: activeView === view.key }]"
        @click="activeView = view.key"
      >
        <el-icon><component :is="view.icon" /></el-icon>
        <span>{{ view.label }}</span>
      </button>
    </nav>

    <!-- ================= 创建 ================= -->
    <section v-if="activeView === 'create'" class="create-view">
      <div class="col-left">
        <div class="capability-grid">
          <button
            v-for="item in IMAGE_MODULES"
            :key="item.code"
            type="button"
            :class="['capability-card', { active: item.code === activeModule.code }]"
            @click="selectModule(item)"
          >
            <strong>{{ item.name }}</strong>
            <span>{{ item.desc }}</span>
          </button>
        </div>

        <div class="field-block">
          <label class="field-label">{{ activeModule.promptLabel || '提示词' }}</label>
          <el-input
            v-model="values.prompt"
            type="textarea"
            :rows="5"
            :maxlength="1000"
            show-word-limit
            :placeholder="activeModule.placeholder || '描述你想要的画面'"
          />
          <p v-for="tip in activeModule.tips" :key="tip" class="hint">· {{ tip }}</p>
        </div>

        <div v-if="activeModule.fields.includes('negative_prompt')" class="field-block">
          <label class="field-label">负向提示词（可选）</label>
          <el-input v-model="values.negative_prompt" :maxlength="500" placeholder="cfg 固定为 1，通常留空" />
        </div>

        <div v-if="activeModule.fields.includes('size')" class="field-row">
          <div class="field-block">
            <label class="field-label">输出尺寸</label>
            <el-select v-model="values.size" placeholder="选择尺寸" style="width: 100%">
              <el-option v-for="opt in sizeOptions" :key="opt.label" :label="opt.label" :value="opt.label" />
            </el-select>
          </div>
          <div v-if="activeModule.fields.includes('strength')" class="field-block">
            <label class="field-label">重绘幅度</label>
            <el-select v-model="values.strength" placeholder="选择幅度" style="width: 100%">
              <el-option v-for="opt in strengthOptions" :key="opt" :label="opt" :value="opt" />
            </el-select>
          </div>
        </div>

        <div v-else-if="activeModule.fields.includes('strength')" class="field-block">
          <label class="field-label">重绘幅度</label>
          <el-select v-model="values.strength" placeholder="选择幅度" style="width: 100%">
            <el-option v-for="opt in strengthOptions" :key="opt" :label="opt" :value="opt" />
          </el-select>
        </div>

        <div v-if="activeModule.imageFields || activeModule.imageField" class="field-block">
          <label class="field-label">
            {{ activeModule.code === 'EDIT' ? '参考图（第一张是编辑目标，必填）' : '输入图片' }}
          </label>
          <input
            ref="fileInput"
            class="hidden-input"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            :multiple="activeModule.code === 'EDIT'"
            @change="handleFiles"
          />
          <div class="upload-row">
            <el-button :loading="uploading" @click="pickFiles">选择图片</el-button>
            <span v-if="uploading" class="hint">上传中 {{ uploadPercent }}%</span>
          </div>
          <div v-if="previewUrls.length" class="thumbs">
            <div v-for="(url, index) in previewUrls" :key="url" class="thumb">
              <img :src="url" :alt="'参考图 ' + (index + 1)" />
              <button type="button" class="thumb-remove" @click="removeImage(index)">移除</button>
              <span class="thumb-tag">{{ slotLabels[index] }}</span>
            </div>
          </div>
        </div>

        <div class="submit-row">
          <el-button
            v-hasPermi="['image:creation:submit']"
            type="primary"
            size="large"
            :disabled="!canSubmit || submitting || uploading"
            :loading="submitting"
            @click="submitTask"
          >
            {{ submitting ? '提交中…' : canSubmit ? '开始生成' : '暂不可提交' }}
          </el-button>
          <span v-if="submitBlockReason" class="block-reason">{{ submitBlockReason }}</span>
        </div>
      </div>

      <div class="col-right">
        <section class="panel">
          <h3>灵感</h3>
          <button
            v-for="item in INSPIRATIONS"
            :key="item.title"
            type="button"
            class="inspiration"
            @click="applyInspiration(item)"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ moduleOf(item.capability)?.name }}</span>
            <p>{{ item.prompt }}</p>
          </button>
        </section>

        <section class="panel">
          <h3>最近任务</h3>
          <p v-if="!recentTasks.length" class="empty">暂无任务</p>
          <div v-for="task in recentTasks" :key="task.id" class="task-line" @click="openDetail(task.id)">
            <span :class="['status-dot', statusClass(task.status)]"></span>
            <div class="task-line-main">
              <strong>{{ task.taskName || task.taskNo }}</strong>
              <small>{{ statusText(task.status) }} · {{ task.createTime || '' }}</small>
            </div>
          </div>
        </section>
      </div>
    </section>

    <!-- ================= 我的任务 ================= -->
    <section v-else-if="activeView === 'tasks'" class="content-view">
      <div class="view-head">
        <h3>我的任务</h3>
        <el-button :loading="loadingTasks" @click="loadTasks">刷新</el-button>
      </div>
      <el-table :data="tasks" style="width: 100%" empty-text="暂无任务">
        <el-table-column prop="taskNo" label="任务编号" min-width="200" />
        <el-table-column prop="taskName" label="名称" min-width="160" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="输出" width="150">
          <template #default="{ row }">
            <span v-if="row.outputWidth">{{ row.outputWidth }}×{{ row.outputHeight }}</span>
            <span v-else class="hint">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button v-if="row.status === 'QUEUED'" link type="danger" @click="cancelTask(row.id)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ================= 素材库 ================= -->
    <section v-else class="content-view">
      <div class="view-head">
        <h3>素材库</h3>
        <div class="upload-row">
          <input
            ref="assetInput"
            class="hidden-input"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            @change="handleAssetFiles"
          />
          <el-button :loading="uploading" @click="assetInput?.click()">上传素材</el-button>
          <el-button :loading="loadingAssets" @click="loadAssets">刷新</el-button>
        </div>
      </div>
      <p v-if="!assets.length" class="empty">暂无素材</p>
      <div class="asset-grid">
        <div v-for="asset in assets" :key="asset.id" class="asset-card">
          <img v-if="assetThumbs[asset.id]" :src="assetThumbs[asset.id]" :alt="asset.originalName || '素材'" @click="openPreview(asset.id)" />
          <div v-else class="asset-placeholder">无预览</div>
          <div class="asset-meta">
            <strong>{{ asset.originalName || '素材 ' + asset.id }}</strong>
            <small>
              {{ asset.sourceKind === 'UPLOAD' ? '上传' : '产出' }}
              <template v-if="asset.sizeBytes"> · {{ formatSize(asset.sizeBytes) }}</template>
            </small>
          </div>
          <el-button link type="danger" size="small" @click="removeAsset(asset.id)">删除</el-button>
        </div>
      </div>
    </section>

    <!-- ================= 详情弹窗 ================= -->
    <el-dialog v-model="detailVisible" title="任务详情" width="720px">
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="任务编号">{{ detail.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="能力">{{ moduleOf(detail.capabilityCode)?.name || detail.capabilityCode }}</el-descriptions-item>
          <el-descriptions-item label="工作流">{{ detail.workflowCode }}</el-descriptions-item>
          <el-descriptions-item label="输出尺寸">
            <span v-if="detail.outputWidth">{{ detail.outputWidth }}×{{ detail.outputHeight }}</span>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="透明通道">{{ detail.outputHasAlpha ? '有' : '无' }}</el-descriptions-item>
          <el-descriptions-item label="提示词" :span="2">{{ detail.prompt || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.errorMessage" label="失败原因" :span="2">
            <span class="error-text">{{ detail.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="detailPreviewUrl" class="detail-preview">
          <img :src="detailPreviewUrl" alt="产出预览" />
        </div>

        <h4>执行时间线</h4>
        <el-timeline>
          <el-timeline-item
            v-for="event in detail.events || []"
            :key="event.sequence"
            :timestamp="event.createTime || ''"
            :type="event.eventType === 'FAILED' ? 'danger' : event.eventType === 'SUCCEEDED' ? 'success' : 'primary'"
          >
            {{ event.eventType }}<span v-if="event.detail"> · {{ event.detail }}</span>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-dialog>

    <!-- ================= 预览弹窗 ================= -->
    <el-dialog v-model="previewVisible" title="素材预览" width="720px">
      <img v-if="previewUrl" :src="previewUrl" style="width: 100%" alt="素材预览" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Document, FolderOpened, MagicStick } from '@element-plus/icons-vue';
import {
  cancelImageTask,
  createImageTask,
  deleteImageAsset,
  executeImageTask,
  fetchImageAssetBlobUrl,
  fetchImageAssetThumbnailBlobUrl,
  getImageTask,
  listImageAssets,
  listImageTasks,
  listImageWorkflows,
  uploadImageAsset
} from '@/api/image';
import type {
  ImageAssetVO,
  ImageCapabilityCode,
  ImageTaskDetailVO,
  ImageTaskStatus,
  ImageTaskVO,
  ImageWorkflowVO
} from '@/api/image/types';
import { extractErrorMessage } from '@/utils/request';
import {
  IMAGE_MODULES,
  INSPIRATIONS,
  moduleOf,
  type ImageCapabilityModule,
  type ImageFieldKey,
  type ImageInspiration
} from './modules';

type StudioView = 'create' | 'tasks' | 'assets';

const activeView = ref<StudioView>('create');
const studioViews: Array<{ key: StudioView; label: string; icon: unknown }> = [
  { key: 'create', label: '创建图像', icon: MagicStick },
  { key: 'tasks', label: '我的任务', icon: Document },
  { key: 'assets', label: '素材库', icon: FolderOpened }
];

const activeModule = ref<ImageCapabilityModule>(IMAGE_MODULES[0]);
const values = reactive<Partial<Record<ImageFieldKey, string>>>({});
const workflows = ref<ImageWorkflowVO[]>([]);
const tasks = ref<ImageTaskVO[]>([]);
const assets = ref<ImageAssetVO[]>([]);
const assetThumbs = reactive<Record<string, string>>({});
const uploadAssetIds = reactive<Partial<Record<ImageFieldKey, Array<number | string>>>>({});
const previewUrls = ref<string[]>([]);
const uploading = ref(false);
const uploadPercent = ref(0);
const submitting = ref(false);
const loadingTasks = ref(false);
const loadingAssets = ref(false);
const fileInput = ref<HTMLInputElement>();
const assetInput = ref<HTMLInputElement>();

const detailVisible = ref(false);
const detail = ref<ImageTaskDetailVO>();
const detailPreviewUrl = ref('');
const previewVisible = ref(false);
const previewUrl = ref('');

const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
const TERMINAL_STATUSES: ImageTaskStatus[] = ['SUCCEEDED', 'FAILED', 'CANCELED', 'TIMEOUT'];
const POLL_INTERVAL_MS = 3000;
const pollingTaskIds = new Set<string>();
let pollTimer: ReturnType<typeof setInterval> | undefined;

const currentWorkflow = computed(() => workflows.value.find((w) => w.workflowCode === activeModule.value.workflowCode));
const sizeOptions = computed(() => currentWorkflow.value?.sizes || []);
const strengthOptions = computed(() => currentWorkflow.value?.strengths || []);
const slotLabels = computed(() =>
  activeModule.value.imageFields ? activeModule.value.imageFields.map((f, i) => (i === 0 ? '目标图' : '参考图 ' + i)) : ['输入图']
);
const recentTasks = computed(() => tasks.value.slice(0, 5));

/** 提交可用性完全由服务端状态决定，不靠前端猜测。 */
const canSubmit = computed(() => currentWorkflow.value?.submittable === true);

const submitBlockReason = computed(() => {
  if (!workflows.value.length) return '正在读取工作流状态…';
  const workflow = currentWorkflow.value;
  if (!workflow) return activeModule.value.workflowCode + ' 尚未在服务端注册';
  if (workflow.status === 'DRAFT') return '工作流为 DRAFT，完成实机验收并发布后方可提交';
  if (workflow.status === 'TESTING') return '工作流处于 TESTING，仅隔离联调环境可提交';
  if (workflow.status === 'RETIRED') return '工作流已停用';
  return '';
});

function selectModule(item: ImageCapabilityModule) {
  activeModule.value = item;
  values.prompt = '';
  values.negative_prompt = '';
  values.size = '';
  values.strength = '';
  clearImages();
  applyDefaults();
}

/** 档位默认值来自契约（后端下发 defaultSize / defaultStrength）。 */
function applyDefaults() {
  const workflow = currentWorkflow.value;
  if (!workflow) return;
  if (activeModule.value.fields.includes('size')) {
    values.size = workflow.defaultSize || workflow.sizes[0]?.label || '';
  }
  if (activeModule.value.fields.includes('strength')) {
    values.strength = workflow.defaultStrength || workflow.strengths[0] || '';
  }
}

function applyInspiration(item: ImageInspiration) {
  const module = moduleOf(item.capability);
  if (!module) return;
  activeModule.value = module;
  clearImages();
  applyDefaults();
  values.prompt = item.prompt;
  activeView.value = 'create';
}

function pickFiles() {
  fileInput.value?.click();
}

function clearImages() {
  previewUrls.value.forEach((url) => URL.revokeObjectURL(url));
  previewUrls.value = [];
  Object.keys(uploadAssetIds).forEach((key) => delete uploadAssetIds[key as ImageFieldKey]);
}

function removeImage(index: number) {
  const fields = activeModule.value.imageFields || (activeModule.value.imageField ? [activeModule.value.imageField] : []);
  const field = fields[index];
  if (field) {
    delete uploadAssetIds[field];
  }
  const url = previewUrls.value[index];
  if (url) URL.revokeObjectURL(url);
  previewUrls.value.splice(index, 1);
  // 删除后按槽位顺序重新排列素材 ID，避免出现「第 2 张图跑到 image1」
  const remaining = fields.map((key) => uploadAssetIds[key]).filter(Boolean) as Array<Array<number | string>>;
  fields.forEach((key) => delete uploadAssetIds[key]);
  remaining.forEach((ids, i) => {
    uploadAssetIds[fields[i]] = ids;
  });
}

async function handleFiles(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = '';
  if (!files.length) return;

  const fields = activeModule.value.imageFields || (activeModule.value.imageField ? [activeModule.value.imageField] : []);
  const maxCount = fields.length || 1;
  const room = maxCount - previewUrls.value.length;
  if (room <= 0) {
    ElMessage.warning('最多上传 ' + maxCount + ' 张图片');
    return;
  }
  const accepted = files.slice(0, room);
  for (const file of accepted) {
    if (file.size > MAX_UPLOAD_BYTES) {
      ElMessage.error(file.name + ' 超过 ' + MAX_UPLOAD_BYTES / 1024 / 1024 + 'MB');
      return;
    }
  }

  uploading.value = true;
  try {
    for (const file of accepted) {
      const res = await uploadImageAsset(file, (percent) => {
        uploadPercent.value = percent;
      });
      const assetId = res.data?.assetId;
      if (assetId === undefined) continue;
      const nextIndex = previewUrls.value.length;
      const field = fields[nextIndex];
      if (!field) break;
      uploadAssetIds[field] = [assetId];
      previewUrls.value.push(URL.createObjectURL(file));
    }
  } catch (error) {
    ElMessage.error(extractErrorMessage(error) || '上传失败');
  } finally {
    uploading.value = false;
    uploadPercent.value = 0;
  }
}

async function submitTask() {
  const module = activeModule.value;
  const workflowCode = module.workflowCode;
  if (module.fields.includes('prompt') && !values.prompt?.trim()) {
    ElMessage.warning('请先填写提示词');
    return;
  }
  if (module.imageFields && !uploadAssetIds[module.imageFields[0]]) {
    ElMessage.warning('请先上传编辑目标图');
    return;
  }
  if (module.imageField && !uploadAssetIds[module.imageField]) {
    ElMessage.warning('请先上传输入图片');
    return;
  }

  const fields: Record<string, unknown> = {};
  if (module.fields.includes('prompt')) fields.prompt = values.prompt || '';
  if (module.fields.includes('negative_prompt')) fields.negative_prompt = values.negative_prompt || '';
  if (module.fields.includes('size')) fields.size = values.size || currentWorkflow.value?.defaultSize || '';
  if (module.fields.includes('strength')) fields.strength = values.strength || currentWorkflow.value?.defaultStrength || '';
  if (module.imageField) fields[module.imageField] = uploadAssetIds[module.imageField]?.[0];
  if (module.imageFields) {
    module.imageFields.forEach((key) => {
      const id = uploadAssetIds[key]?.[0];
      if (id !== undefined) fields[key] = id;
    });
  }

  submitting.value = true;
  try {
    const created = await createImageTask({
      capabilityCode: module.code as ImageCapabilityCode,
      workflowCode,
      taskName: module.name + ' · ' + workflowCode,
      fields,
      idempotencyKey: workflowCode + '-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
    });
    const taskId = created.data?.taskId;
    if (taskId === undefined) {
      ElMessage.error('创建任务失败：服务端未返回 taskId');
      return;
    }
    ElMessage.success('任务已创建，正在提交生成');
    await loadTasks();
    const executed = await executeImageTask(taskId);
    if (executed.data?.accepted === false || executed.data?.status === 'QUEUED') {
      ElMessage.warning('执行队列已满，任务已排队，稍后可在「我的任务」重试执行');
    } else {
      startTaskPolling(taskId);
    }
    activeView.value = 'tasks';
  } catch (error) {
    ElMessage.error(extractErrorMessage(error) || '提交失败');
  } finally {
    submitting.value = false;
  }
}

async function loadWorkflows() {
  try {
    const res = await listImageWorkflows();
    workflows.value = res.data || [];
    applyDefaults();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error) || '读取工作流状态失败');
  }
}

async function loadTasks() {
  loadingTasks.value = true;
  try {
    const res = await listImageTasks({ pageNum: 1, pageSize: 50 });
    tasks.value = res.data?.rows || [];
    tasks.value
      .filter((task) => !TERMINAL_STATUSES.includes(task.status))
      .forEach((task) => pollingTaskIds.add(String(task.id)));
    if (pollingTaskIds.size > 0) ensurePolling();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error) || '读取任务失败');
  } finally {
    loadingTasks.value = false;
  }
}

function ensurePolling() {
  if (pollTimer !== undefined) return;
  pollTimer = setInterval(() => void pollPendingTasks(), POLL_INTERVAL_MS);
}

function startTaskPolling(taskId: number | string) {
  pollingTaskIds.add(String(taskId));
  ensurePolling();
}

function stopTaskPolling(taskId: number | string) {
  pollingTaskIds.delete(String(taskId));
  if (pollingTaskIds.size === 0 && pollTimer !== undefined) {
    clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

async function pollPendingTasks() {
  const finished: string[] = [];
  for (const id of Array.from(pollingTaskIds)) {
    try {
      const res = await getImageTask(id);
      const task = res.data;
      if (!task) continue;
      if (TERMINAL_STATUSES.includes(task.status)) {
        finished.push(id);
        if (task.status === 'SUCCEEDED') {
          ElMessage.success('任务 ' + task.taskNo + ' 已完成');
        } else if (task.status === 'FAILED') {
          ElMessage.error('任务 ' + task.taskNo + ' 失败：' + (task.errorMessage || task.errorCode || '未知原因'));
        } else {
          ElMessage.warning('任务 ' + task.taskNo + ' 已' + statusText(task.status));
        }
      }
    } catch {
      finished.push(id);
    }
  }
  finished.forEach((id) => stopTaskPolling(id));
  if (finished.length) {
    await loadTasks();
    await loadAssets();
  }
}

async function openDetail(taskId: number | string) {
  try {
    const res = await getImageTask(taskId);
    detail.value = res.data;
    detailVisible.value = true;
    if (detailPreviewUrl.value) {
      URL.revokeObjectURL(detailPreviewUrl.value);
      detailPreviewUrl.value = '';
    }
    const outputAssetId = res.data?.outputAssetId;
    if (outputAssetId) {
      detailPreviewUrl.value = await fetchImageAssetBlobUrl(outputAssetId);
    }
  } catch (error) {
    ElMessage.error(extractErrorMessage(error) || '读取任务详情失败');
  }
}

async function cancelTask(taskId: number | string) {
  try {
    await ElMessageBox.confirm('确认取消该任务？', '取消任务', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await cancelImageTask(taskId);
    ElMessage.success('已取消');
    await loadTasks();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error) || '取消失败');
  }
}

async function loadAssets() {
  loadingAssets.value = true;
  try {
    const res = await listImageAssets({ pageNum: 1, pageSize: 60 });
    assets.value = res.data?.rows || [];
    await loadAssetThumbs();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error) || '读取素材失败');
  } finally {
    loadingAssets.value = false;
  }
}

async function loadAssetThumbs() {
  for (const asset of assets.value) {
    const key = String(asset.id);
    if (assetThumbs[key]) continue;
    if (asset.sourceKind === 'OUTPUT') continue; // 产出图不在缩略图接口范围内，按需预览
    try {
      assetThumbs[key] = await fetchImageAssetThumbnailBlobUrl(asset.id);
    } catch {
      try {
        assetThumbs[key] = await fetchImageAssetBlobUrl(asset.id);
      } catch {
        /* 无预览 */
      }
    }
  }
}

async function handleAssetFiles(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = '';
  if (!files.length) return;
  uploading.value = true;
  try {
    for (const file of files) {
      if (file.size > MAX_UPLOAD_BYTES) {
        ElMessage.error(file.name + ' 超过 ' + MAX_UPLOAD_BYTES / 1024 / 1024 + 'MB');
        continue;
      }
      await uploadImageAsset(file);
    }
    ElMessage.success('上传完成');
    await loadAssets();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error) || '上传失败');
  } finally {
    uploading.value = false;
  }
}

async function removeAsset(assetId: number | string) {
  try {
    await ElMessageBox.confirm('确认删除该素材？', '删除素材', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await deleteImageAsset(assetId);
    const key = String(assetId);
    if (assetThumbs[key]) {
      URL.revokeObjectURL(assetThumbs[key]);
      delete assetThumbs[key];
    }
    await loadAssets();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error) || '删除失败');
  }
}

async function openPreview(assetId: number | string) {
  try {
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = await fetchImageAssetBlobUrl(assetId);
    previewVisible.value = true;
  } catch (error) {
    ElMessage.error(extractErrorMessage(error) || '读取素材失败');
  }
}

function statusText(status: ImageTaskStatus | string) {
  const map: Record<string, string> = {
    QUEUED: '排队中',
    RUNNING: '生成中',
    SUCCEEDED: '已完成',
    FAILED: '失败',
    CANCELED: '已取消',
    TIMEOUT: '超时'
  };
  return map[status] || status;
}

function statusTagType(status: ImageTaskStatus | string) {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'TIMEOUT') return 'warning';
  if (status === 'RUNNING') return 'primary';
  return 'info';
}

function statusClass(status: ImageTaskStatus | string) {
  return 'dot-' + String(status).toLowerCase();
}

function formatSize(bytes?: number | null) {
  if (!bytes) return '0 B';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1024 / 1024).toFixed(2) + ' MB';
}

onMounted(async () => {
  await loadWorkflows();
  applyDefaults();
  await loadTasks();
  await loadAssets();
});

onBeforeUnmount(() => {
  if (pollTimer !== undefined) clearInterval(pollTimer);
  previewUrls.value.forEach((url) => URL.revokeObjectURL(url));
  Object.values(assetThumbs).forEach((url) => URL.revokeObjectURL(url));
  if (detailPreviewUrl.value) URL.revokeObjectURL(detailPreviewUrl.value);
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
});
</script>

<style scoped lang="scss">
.image-studio {
  padding: 20px 24px 40px;
  color: #e6ebf2;

  .studio-head {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 16px;

    h2 {
      margin: 0;
      font-size: 22px;
      letter-spacing: 0.5px;
    }

    .sub {
      margin: 6px 0 0;
      color: #8b97a8;
      font-size: 13px;
    }

    .head-meta {
      display: flex;
      gap: 8px;
    }

    .pill {
      padding: 4px 10px;
      border-radius: 999px;
      background: rgba(94, 168, 255, 0.16);
      color: #9fc7ff;
      font-size: 12px;

      &.ghost {
        background: rgba(255, 255, 255, 0.06);
        color: #97a3b4;
      }
    }
  }

  .studio-nav {
    display: flex;
    gap: 8px;
    margin: 18px 0;

    .nav-item {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.03);
      color: #aab6c6;
      cursor: pointer;

      &.active {
        border-color: rgba(94, 168, 255, 0.6);
        background: rgba(94, 168, 255, 0.14);
        color: #e6f0ff;
      }
    }
  }

  .create-view {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 320px;
    gap: 20px;
  }

  .col-left,
  .col-right {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .capability-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
    gap: 12px;

    .capability-card {
      display: flex;
      flex-direction: column;
      gap: 6px;
      padding: 14px;
      text-align: left;
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.03);
      color: inherit;
      cursor: pointer;

      strong {
        font-size: 15px;
      }

      span {
        color: #8b97a8;
        font-size: 12px;
        line-height: 1.5;
      }

      &.active {
        border-color: rgba(94, 168, 255, 0.6);
        background: rgba(94, 168, 255, 0.12);
      }
    }
  }

  .field-block {
    display: flex;
    flex-direction: column;
    gap: 8px;

    .field-label {
      font-size: 13px;
      color: #b6c2d2;
    }
  }

  .field-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }

  .hint {
    margin: 0;
    color: #7f8b9c;
    font-size: 12px;
  }

  .hidden-input {
    display: none;
  }

  .upload-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .thumbs {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;

    .thumb {
      position: relative;
      width: 120px;
      border-radius: 10px;
      overflow: hidden;
      border: 1px solid rgba(255, 255, 255, 0.1);

      img {
        display: block;
        width: 100%;
        height: 120px;
        object-fit: cover;
      }

      .thumb-tag {
        position: absolute;
        left: 6px;
        bottom: 6px;
        padding: 2px 6px;
        border-radius: 6px;
        background: rgba(0, 0, 0, 0.6);
        font-size: 11px;
      }

      .thumb-remove {
        position: absolute;
        right: 6px;
        top: 6px;
        padding: 2px 8px;
        border: none;
        border-radius: 6px;
        background: rgba(0, 0, 0, 0.6);
        color: #ffb4b4;
        font-size: 11px;
        cursor: pointer;
      }
    }
  }

  .submit-row {
    display: flex;
    align-items: center;
    gap: 12px;

    .block-reason {
      color: #f0b866;
      font-size: 12px;
    }
  }

  .panel {
    padding: 14px;
    border: 1px solid rgba(255, 255, 255, 0.07);
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.025);

    h3 {
      margin: 0 0 10px;
      font-size: 14px;
      color: #cbd6e4;
    }

    .inspiration {
      display: block;
      width: 100%;
      margin-bottom: 8px;
      padding: 10px;
      text-align: left;
      border: 1px solid rgba(255, 255, 255, 0.07);
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.02);
      color: inherit;
      cursor: pointer;

      strong {
        display: block;
        font-size: 13px;
      }

      span {
        color: #8b97a8;
        font-size: 11px;
      }

      p {
        margin: 6px 0 0;
        color: #7f8b9c;
        font-size: 12px;
        line-height: 1.5;
      }
    }

    .task-line {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 4px;
      cursor: pointer;
      border-bottom: 1px dashed rgba(255, 255, 255, 0.06);

      .task-line-main {
        display: flex;
        flex-direction: column;

        strong {
          font-size: 12.5px;
        }

        small {
          color: #7f8b9c;
          font-size: 11px;
        }
      }
    }
  }

  .status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #6b7b8c;
  }

  .dot-succeeded {
    background: #4ec98a;
  }

  .dot-failed {
    background: #ff6b6b;
  }

  .dot-running {
    background: #5ea8ff;
  }

  .dot-queued {
    background: #b0b8c4;
  }

  .dot-timeout {
    background: #f0b866;
  }

  .dot-canceled {
    background: #8b97a8;
  }

  .content-view {
    .view-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 14px;
    }
  }

  .empty {
    color: #7f8b9c;
    font-size: 13px;
  }

  .asset-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 14px;

    .asset-card {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 10px;
      border: 1px solid rgba(255, 255, 255, 0.07);
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.025);

      img,
      .asset-placeholder {
        width: 100%;
        height: 140px;
        object-fit: cover;
        border-radius: 8px;
        background: rgba(255, 255, 255, 0.04);
        cursor: pointer;
      }

      .asset-placeholder {
        display: flex;
        align-items: center;
        justify-content: center;
        color: #6f7b8c;
        font-size: 12px;
      }

      .asset-meta {
        display: flex;
        flex-direction: column;

        strong {
          font-size: 12.5px;
          word-break: break-all;
        }

        small {
          color: #7f8b9c;
          font-size: 11px;
        }
      }
    }
  }

  .detail-preview {
    margin: 14px 0;

    img {
      width: 100%;
      border-radius: 10px;
    }
  }

  .error-text {
    color: #ff9b9b;
  }
}
</style>
