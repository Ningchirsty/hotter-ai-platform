<template>
  <div class="studio">
    <div v-if="showGuide" class="guide-bar">
      <el-icon><MagicStick /></el-icon>
      <span>创建任务：选择图像能力，上传素材并描述画面，确认输出档位。四个能力均走 Qwen-Image-2.1 本地 GPU 工作流。</span>
      <button type="button" title="关闭引导" aria-label="关闭引导" @click="showGuide = false">
        <el-icon><Close /></el-icon>
      </button>
    </div>

    <nav class="studio-nav" aria-label="图像创作功能">
      <button
        v-for="view in studioViews"
        :key="view.key"
        type="button"
        :class="{ active: activeView === view.key }"
        :aria-current="activeView === view.key ? 'page' : undefined"
        @click="activeView = view.key"
      >
        <el-icon><component :is="view.icon" /></el-icon>
        {{ view.label }}
      </button>
    </nav>

    <!-- ================= 创建 ================= -->
    <div v-if="activeView === 'create'" class="workbench-grid">
      <section class="studio-card create-card">
        <div class="section-heading">
          <div>
            <span>图像创作 · Qwen-Image-2.1</span>
            <h2>{{ activeModule.name }}</h2>
          </div>
          <span class="version-pill">{{ versionPill }}</span>
        </div>

        <div class="capability-grid" aria-label="图像能力">
          <button
            v-for="item in IMAGE_MODULES"
            :key="item.code"
            type="button"
            :class="['capability', { active: item.code === activeModule.code }]"
            :aria-pressed="item.code === activeModule.code"
            @click="selectModule(item)"
          >
            <el-icon><component :is="moduleIcon(item.code)" /></el-icon>
            <strong>{{ item.name }}</strong>
            <small>{{ item.desc }}</small>
          </button>
        </div>

        <div class="form-divider" />

        <!--
          先上传、后写文字：参考图决定输出画布（image1 定尺寸）与主体，
          用户的心智顺序是「给图 → 说要求」，所以上传区放在提示词上方。
        -->
        <div v-if="activeModule.imageFields || activeModule.imageField" class="field-block">
          <label>
            {{ activeModule.code === 'EDIT' ? '参考图（第一张是编辑目标，必填）' : '输入图片' }}
            <em>*</em>
          </label>
          <label class="upload-zone" :class="{ complete: previewUrls.length > 0 }">
            <input
              ref="fileInput"
              type="file"
              accept="image/png,image/jpeg,image/webp"
              :multiple="activeModule.code === 'EDIT'"
              @change="handleFiles"
            />
            <!--
              已选图片的预览：没有它用户只能看到一行素材 ID，传错图要等生成完才发现。
              每张右上角可单独移除，移除后槽位顺序会重排（见 removeImage）。
            -->
            <div v-if="previewUrls.length" class="upload-previews">
              <figure v-for="(url, index) in previewUrls" :key="url">
                <img :src="url" :alt="slotLabels[index] || '参考图'" />
                <button
                  type="button"
                  :title="'移除' + (slotLabels[index] || '参考图')"
                  aria-label="移除该图片"
                  @click.prevent.stop="removeImage(index)"
                >
                  <el-icon><Close /></el-icon>
                </button>
              </figure>
              <span class="upload-previews-badge">已上传 {{ previewUrls.length }} 张</span>
            </div>
            <template v-else>
              <el-icon><UploadFilled /></el-icon>
              <b>{{ uploadHint }}</b>
              <small>
                {{ uploading && uploadPercent > 0 ? `上传中 ${uploadPercent}%` : '支持 PNG / JPG / WEBP，单张不超过 20MB' }}
              </small>
            </template>
          </label>
        </div>

        <div v-if="activeModule.fields.includes('prompt')" class="field-block">
          <label>
            {{ activeModule.promptLabel || '提示词' }}
            <em>*</em>
          </label>
          <el-input
            v-model="values.prompt"
            type="textarea"
            :rows="5"
            :maxlength="1000"
            show-word-limit
            :placeholder="activeModule.placeholder || '描述你想要的画面'"
          />
          <p v-for="tip in activeModule.tips" :key="tip" class="field-hint">· {{ tip }}</p>
        </div>

        <!-- 固定提示词的能力（抠图/白底图）：没有可填的提示词，但说明必须照常展示，
             否则用户只会看到「怎么没有输入框」，不知道提示词是由服务端固定的 -->
        <div v-else class="field-block">
          <p v-for="tip in activeModule.tips" :key="tip" class="field-hint">· {{ tip }}</p>
        </div>

        <div v-if="activeModule.fields.includes('negative_prompt')" class="field-block">
          <label>负向提示词（可选）</label>
          <el-input v-model="values.negative_prompt" :maxlength="500" placeholder="cfg 固定为 1，通常留空" />
        </div>

        <div v-if="activeModule.fields.includes('size')" class="field-block">
          <label>
            输出尺寸
            <em>*</em>
          </label>
          <p class="model-group-label">
            <el-icon><Grid /></el-icon>
            {{ sizeOptions.length }} 个档位可选
          </p>
          <div class="choice-grid size-choices">
            <button
              v-for="opt in sizeOptions"
              :key="opt.label"
              type="button"
              :class="{ active: values.size === opt.label }"
              @click="values.size = opt.label"
            >
              {{ opt.label }}
              <small>{{ opt.width }}×{{ opt.height }}</small>
            </button>
          </div>
        </div>

        <div v-if="activeModule.fields.includes('strength')" class="field-block">
          <label>
            重绘幅度
            <em>*</em>
          </label>
          <p class="model-group-label">
            <el-icon><Grid /></el-icon>
            数值越大越偏离原图
          </p>
          <div class="choice-grid strength-choices">
            <button
              v-for="opt in strengthOptions"
              :key="opt"
              type="button"
              :class="{ active: values.strength === opt }"
              @click="values.strength = opt"
            >
              {{ opt }}
            </button>
          </div>
        </div>

        <div class="submit-row">
          <button
            v-hasPermi="['image:creation:submit']"
            type="button"
            class="submit-button"
            :disabled="!canSubmit || submitting || uploading"
            :title="canSubmit ? '提交并生成图片' : submitBlockReason"
            @click="submitTask"
          >
            <el-icon><MagicStick /></el-icon>
            {{ submitting ? '提交中…' : canSubmit ? '提交生成' : '暂不可提交' }}
          </button>
          <span>{{ submitBlockReason || '提交后将经服务端填充模板并交由 ComfyUI 执行' }}</span>
        </div>
      </section>

      <aside class="right-column">
        <section class="studio-card inspiration-card">
          <div class="section-heading compact">
            <div>
              <h2>灵感 · 一键同款</h2>
              <span>自动带入能力与描述</span>
            </div>
          </div>
          <div class="inspiration-list">
            <article v-for="item in INSPIRATIONS" :key="item.title">
              <div :class="['inspiration-poster', inspirationTone(item.capability)]">
                <el-icon><PictureFilled /></el-icon>
                <span>{{ moduleOf(item.capability)?.name }}</span>
              </div>
              <div>
                <b>{{ item.title }}</b>
                <p>{{ item.prompt }}</p>
              </div>
              <button type="button" @click="applyInspiration(item)">
                用同款
                <el-icon><ArrowRight /></el-icon>
              </button>
            </article>
          </div>
        </section>

        <section class="studio-card recent-card">
          <div class="section-heading compact">
            <div>
              <h2>最近任务</h2>
              <span>当前创作队列</span>
            </div>
          </div>
          <div v-for="task in recentTasks" :key="task.id" class="recent-task" @click="openDetail(task.id)">
            <span :class="toneOf(task.status)"><i /></span>
            <div>
              <b>{{ task.taskName || task.taskNo }}</b>
              <small>{{ moduleOf(task.capabilityCode)?.name || task.capabilityCode }} · {{ task.sizeLabel || task.workflowCode }}</small>
            </div>
            <em>{{ statusText(task.status) }}</em>
          </div>
          <div v-if="!recentTasks.length" class="recent-task">
            <span class="muted"><i /></span>
            <div>
              <b>暂无任务</b>
              <small>创建后可在此查看进度</small>
            </div>
            <em>—</em>
          </div>
        </section>
      </aside>
    </div>

    <!-- ================= 我的任务 ================= -->
    <section v-else-if="activeView === 'tasks'" class="content-view">
      <div class="view-heading">
        <div>
          <span>图像创作</span>
          <h2>我的任务</h2>
          <p>显示服务端真实任务记录，仅本人可见。</p>
        </div>
        <button type="button" class="primary-action" @click="activeView = 'create'">
          <el-icon><MagicStick /></el-icon>
          创建任务
        </button>
      </div>

      <div class="task-toolbar">
        <el-input v-model="taskKeyword" placeholder="搜索任务名称或编号" clearable>
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="task-filters" aria-label="任务状态筛选">
          <button
            v-for="item in taskFilters"
            :key="item.key"
            type="button"
            :class="{ active: taskFilter === item.key }"
            @click="taskFilter = item.key"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <div v-if="loadingTasks" class="empty-state">
        <el-icon><Document /></el-icon>
        <b>正在加载任务…</b>
      </div>
      <div v-else-if="filteredTasks.length" class="task-list">
        <article v-for="task in filteredTasks" :key="task.id" class="task-card">
          <!--
            成片封面：成功任务的产出缩略图（走后端 thumbnail 接口，约几十 KB）。
            取不到时回退成「能力图标 + 档位」，不留空白；点封面直接开预览。
          -->
          <div :class="['task-cover', toneOf(task.status)]" @click="previewTask(task)">
            <img
              v-if="coverFor(task)"
              class="task-cover-img"
              :src="coverFor(task)"
              :alt="task.taskName || task.taskNo"
            />
            <template v-else>
              <el-icon><component :is="moduleIcon(task.capabilityCode)" /></el-icon>
              <span>{{ task.sizeLabel || task.strengthLabel || moduleOf(task.capabilityCode)?.name }}</span>
            </template>
            <span v-if="coverFor(task)" class="task-cover-zoom"><el-icon><ZoomIn /></el-icon></span>
          </div>
          <div class="task-main">
            <div class="task-title-row">
              <b>{{ task.taskName || task.taskNo }}</b>
              <span :class="['task-status', toneOf(task.status)]">{{ statusText(task.status) }}</span>
            </div>
            <p>
              {{ moduleOf(task.capabilityCode)?.name || task.capabilityCode }} · {{ task.workflowCode }}
              <template v-if="task.outputWidth"> · {{ task.outputWidth }}×{{ task.outputHeight }}</template>
            </p>
            <small>{{ task.taskNo }} · {{ task.createTime || '—' }}</small>
            <small v-if="task.errorMessage" class="task-error">{{ task.errorMessage }}</small>
          </div>
          <div class="task-actions">
            <button
              type="button"
              :title="previewable(task) ? '预览产出' : '该任务暂无可预览产出'"
              :aria-label="previewable(task) ? '预览产出' : '该任务暂无可预览产出'"
              :disabled="!previewable(task)"
              @click="previewTask(task)"
            >
              <el-icon><ZoomIn /></el-icon>
            </button>
            <button type="button" title="查看任务详情" aria-label="查看任务详情" @click="openDetail(task.id)">
              <el-icon><View /></el-icon>
            </button>
            <button
              v-if="task.status === 'QUEUED'"
              type="button"
              title="取消排队"
              aria-label="取消排队"
              @click="cancelTask(task.id)"
            >
              <el-icon><Close /></el-icon>
            </button>
          </div>
        </article>
      </div>
      <div v-else class="empty-state">
        <el-icon><Document /></el-icon>
        <b>没有匹配的任务</b>
        <span>调整搜索条件，或创建一个新的图像任务。</span>
      </div>
    </section>

    <!-- ================= 素材库 ================= -->
    <section v-else class="content-view">
      <div class="view-heading">
        <div>
          <span>图像创作</span>
          <h2>素材库</h2>
          <p>素材保存在服务端；任务提交时使用素材 ID，不使用浏览器本地文件名。</p>
        </div>
        <label class="primary-action asset-upload">
          <input
            ref="assetInput"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            multiple
            @change="handleAssetFiles"
          />
          <el-icon><UploadFilled /></el-icon>
          {{ uploading ? '上传中…' : '添加素材' }}
        </label>
      </div>

      <div v-if="loadingAssets" class="empty-state">
        <el-icon><UploadFilled /></el-icon>
        <b>正在加载素材…</b>
      </div>
      <div v-else-if="assets.length" class="asset-grid">
        <article v-for="asset in assets" :key="asset.id" class="asset-card">
          <div class="asset-preview">
            <!-- 真实缩略图；取不到时回退成图标，不让卡片出现空白 -->
            <img
              v-if="assetThumbs[asset.id]"
              class="asset-thumb"
              :src="assetThumbs[asset.id]"
              :alt="asset.originalName || ''"
              @click="openPreview(asset.id)"
            />
            <template v-else>
              <el-icon><Picture /></el-icon>
              <span>{{ asset.sourceKind === 'UPLOAD' ? '上传' : '产出' }}</span>
            </template>
          </div>
          <div class="asset-info">
            <b>{{ asset.originalName || '素材 ' + asset.id }}</b>
            <small>
              {{ asset.sourceKind === 'UPLOAD' ? '上传' : '产出' }}
              <template v-if="asset.sizeBytes"> · {{ formatSize(asset.sizeBytes) }}</template>
              · {{ asset.createTime || '—' }}
            </small>
          </div>
          <button type="button" title="移除素材" aria-label="移除素材" @click="removeAsset(asset.id)">
            <el-icon><Delete /></el-icon>
          </button>
        </article>
      </div>
      <div v-else class="empty-state">
        <el-icon><FolderOpened /></el-icon>
        <b>素材库还是空的</b>
        <span>上传图片后，即可在创建任务时使用。</span>
      </div>
    </section>

    <!-- ================= 详情弹窗 ================= -->
    <el-dialog v-model="detailVisible" title="任务详情" width="min(920px, 92vw)" top="6vh">
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
    <el-dialog v-model="previewVisible" title="素材预览" width="min(920px, 92vw)" top="6vh">
      <img v-if="previewUrl" :src="previewUrl" style="width: 100%" alt="素材预览" />
    </el-dialog>

    <!--
      产出预览：任务界面必须能直接看图。
      内容经鉴权接口取回后转成 blob URL（<img src> 不会带 Authorization 头），
      关闭时统一 revoke，避免内存里的 base64/blob 越堆越多。
    -->
    <el-dialog
      v-model="taskPreviewVisible"
      :title="taskPreviewTask?.taskName || taskPreviewTask?.taskNo || '产出预览'"
      width="min(920px, 92vw)"
      top="6vh"
      destroy-on-close
      @closed="closeTaskPreview"
    >
      <div class="preview-body">
        <div v-if="taskPreviewLoading" class="empty-state">
          <el-icon><ZoomIn /></el-icon>
          <b>正在加载产出…</b>
        </div>
        <div v-else-if="taskPreviewError" class="empty-state">
          <el-icon><Close /></el-icon>
          <b>{{ taskPreviewError }}</b>
          <span>可能是该任务还没有产出，或素材已被清理。</span>
        </div>
        <img v-else-if="taskPreviewUrl" class="preview-image" :src="taskPreviewUrl" :alt="'产出预览'" />
        <div v-else class="empty-state">
          <el-icon><ZoomIn /></el-icon>
          <b>暂无可预览的产出</b>
        </div>

        <dl v-if="taskPreviewMeta.length" class="preview-meta">
          <div v-for="row in taskPreviewMeta" :key="row.label">
            <dt>{{ row.label }}</dt>
            <dd>{{ row.value }}</dd>
          </div>
        </dl>
      </div>
      <template #footer>
        <el-button :disabled="!taskPreviewUrl" @click="downloadTaskOutput">
          <el-icon><Download /></el-icon>
          下载原图
        </el-button>
        <el-button type="primary" @click="taskPreviewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue';
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  ArrowRight,
  Close,
  Delete,
  Document,
  Download,
  FolderOpened,
  Grid,
  MagicStick,
  Picture,
  PictureFilled,
  Scissor,
  Search,
  UploadFilled,
  View,
  ZoomIn
} from '@element-plus/icons-vue';
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
const showGuide = ref(true);
const studioViews: Array<{ key: StudioView; label: string; icon: unknown }> = [
  { key: 'create', label: '创建图像', icon: MagicStick },
  { key: 'tasks', label: '我的任务', icon: Document },
  { key: 'assets', label: '素材库', icon: FolderOpened }
];

/** 能力卡图标：与视频页同一套视觉语言（能力 → 图标一一对应）。 */
const moduleIcons: Record<ImageCapabilityCode, Component> = {
  T2I: Picture,
  I2I: PictureFilled,
  EDIT: MagicStick,
  BGREMOVE: Scissor
};

function moduleIcon(code: string): Component {
  return moduleIcons[code as ImageCapabilityCode] ?? Picture;
}

/** 灵感卡海报色：与视频页一致的三种色调。 */
function inspirationTone(code: string) {
  if (code === 'I2I' || code === 'BGREMOVE') return 'cyan';
  if (code === 'EDIT') return 'rose';
  return '';
}

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

const taskKeyword = ref('');
const taskFilter = ref<'ALL' | ImageTaskStatus>('ALL');
const taskFilters: Array<{ key: 'ALL' | ImageTaskStatus; label: string }> = [
  { key: 'ALL', label: '全部' },
  { key: 'QUEUED', label: '排队中' },
  { key: 'RUNNING', label: '生成中' },
  { key: 'SUCCEEDED', label: '已完成' },
  { key: 'FAILED', label: '失败' }
];

const detailVisible = ref(false);
const detail = ref<ImageTaskDetailVO>();
const detailPreviewUrl = ref('');
const previewVisible = ref(false);
const previewUrl = ref('');

/** 任务产出封面缓存：assetId -> blob URL（走缩略图接口，几十 KB，不拉原图）。 */
const taskCoverUrls = ref<Record<string, string>>({});
const taskCoverLoading = new Set<string>();

/** 产出预览弹窗状态。 */
const taskPreviewVisible = ref(false);
const taskPreviewTask = ref<ImageTaskVO>();
const taskPreviewUrl = ref('');
const taskPreviewLoading = ref(false);
const taskPreviewError = ref('');
const taskPreviewMeta = ref<Array<{ label: string; value: string }>>([]);

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
const versionPill = computed(() => {
  const workflow = currentWorkflow.value;
  return workflow ? workflow.version + ' · ' + workflow.status : '工作流读取中';
});
const uploadHint = computed(() => {
  const count = activeModule.value.imageFields?.length || 1;
  return count > 1 ? `点击上传参考图（最多 ${count} 张）` : '点击上传输入图片';
});
const filteredTasks = computed(() => {
  const keyword = taskKeyword.value.trim().toLowerCase();
  return tasks.value.filter((task) => {
    if (taskFilter.value !== 'ALL' && task.status !== taskFilter.value) return false;
    if (!keyword) return true;
    return (task.taskName || '').toLowerCase().includes(keyword) || task.taskNo.toLowerCase().includes(keyword);
  });
});

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

/** 状态 → 卡片色调（与视频页同一套 running / queued / done / failed）。 */
function toneOf(status: string) {
  if (status === 'SUCCEEDED') return 'done';
  if (status === 'FAILED' || status === 'TIMEOUT') return 'failed';
  if (status === 'RUNNING') return 'running';
  if (status === 'QUEUED') return 'queued';
  return 'muted';
}

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
    ElMessage.error((await extractErrorMessage(error)) ?? '上传失败');
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
    ElMessage.error((await extractErrorMessage(error)) ?? '提交失败');
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
    ElMessage.error((await extractErrorMessage(error)) ?? '读取工作流状态失败');
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
    ElMessage.error((await extractErrorMessage(error)) ?? '读取任务失败');
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
    ElMessage.error((await extractErrorMessage(error)) ?? '读取任务详情失败');
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
    ElMessage.error((await extractErrorMessage(error)) ?? '取消失败');
  }
}

async function loadAssets() {
  loadingAssets.value = true;
  try {
    const res = await listImageAssets({ pageNum: 1, pageSize: 60 });
    assets.value = res.data?.rows || [];
    await loadAssetThumbs();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取素材失败');
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
    ElMessage.error((await extractErrorMessage(error)) ?? '上传失败');
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
    ElMessage.error((await extractErrorMessage(error)) ?? '删除失败');
  }
}

async function openPreview(assetId: number | string) {
  try {
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = await fetchImageAssetBlobUrl(assetId);
    previewVisible.value = true;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取素材失败');
  }
}

/** 只有成功且有产出素材的任务才谈得上预览。 */
function previewable(task: ImageTaskVO) {
  return task.status === 'SUCCEEDED' && task.outputAssetId !== null && task.outputAssetId !== undefined;
}

function coverFor(task: ImageTaskVO) {
  const id = task.outputAssetId;
  return id === null || id === undefined ? '' : (taskCoverUrls.value[String(id)] ?? '');
}

/**
 * 为成功任务加载产出封面。
 *
 * 走缩略图接口而不是原图：列表一次可能十几条，原图每张几百 KB~1MB，
 * 经公网拉原图会让列表迟迟出不来；缩略图只有几十 KB。取不到就静默失败，
 * 卡片回退成「能力图标 + 档位」，封面不该成为「能不能看」的开关。
 */
async function loadTaskCovers() {
  for (const task of filteredTasks.value) {
    if (!previewable(task)) continue;
    const key = String(task.outputAssetId);
    if (taskCoverUrls.value[key] || taskCoverLoading.has(key)) continue;
    taskCoverLoading.add(key);
    try {
      const url = await fetchImageAssetThumbnailBlobUrl(task.outputAssetId as number | string);
      taskCoverUrls.value = { ...taskCoverUrls.value, [key]: url };
    } catch {
      /* 忽略：封面只是锦上添花 */
    } finally {
      taskCoverLoading.delete(key);
    }
  }
}

/**
 * 打开产出预览。
 *
 * 产出内容要走鉴权接口取回再转 blob URL（<img src> 不带 Authorization 头），
 * 关闭时 revoke，避免 blob 越堆越多。
 */
async function previewTask(task: ImageTaskVO) {
  taskPreviewTask.value = task;
  taskPreviewVisible.value = true;
  taskPreviewError.value = '';
  if (taskPreviewUrl.value) {
    URL.revokeObjectURL(taskPreviewUrl.value);
    taskPreviewUrl.value = '';
  }
  taskPreviewMeta.value = [
    { label: '任务编号', value: task.taskNo },
    { label: '能力', value: moduleOf(task.capabilityCode)?.name || task.capabilityCode },
    { label: '输出尺寸', value: task.outputWidth ? task.outputWidth + '×' + task.outputHeight : '—' },
    { label: '透明通道', value: task.outputHasAlpha ? '有' : '无' },
    { label: '完成时间', value: task.finishedTime || task.createTime || '—' }
  ];
  if (!previewable(task)) {
    taskPreviewError.value = '该任务没有产出';
    return;
  }
  taskPreviewLoading.value = true;
  try {
    taskPreviewUrl.value = await fetchImageAssetBlobUrl(task.outputAssetId as number | string);
  } catch (error) {
    taskPreviewError.value = (await extractErrorMessage(error)) ?? '读取产出失败';
  } finally {
    taskPreviewLoading.value = false;
  }
}

function closeTaskPreview() {
  if (taskPreviewUrl.value) {
    URL.revokeObjectURL(taskPreviewUrl.value);
    taskPreviewUrl.value = '';
  }
  taskPreviewError.value = '';
  taskPreviewTask.value = undefined;
}

/** 下载原图：文件名用任务号，避免浏览器存成随机名。 */
function downloadTaskOutput() {
  if (!taskPreviewUrl.value || !taskPreviewTask.value) return;
  const link = document.createElement('a');
  link.href = taskPreviewUrl.value;
  link.download = (taskPreviewTask.value.taskNo || 'image-task') + '.png';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

/** 任务列表变化（含轮询出新产出）时补齐封面。 */
watch(filteredTasks, () => void loadTaskCovers(), { immediate: true });

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
  Object.values(taskCoverUrls.value).forEach((url) => URL.revokeObjectURL(url));
  if (detailPreviewUrl.value) URL.revokeObjectURL(detailPreviewUrl.value);
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  if (taskPreviewUrl.value) URL.revokeObjectURL(taskPreviewUrl.value);
});
</script>

<!--
  视觉规范与视频创作页（views/video/index.vue）共用同一套暗色 studio 外壳与 token：
  底色 / 卡片 / 档位选择 / 上传区 / 任务卡 / 素材卡 全部按视频页同款数值实现，
  仅把「视频」语义替换为图像能力。改这里前先确认视频页有没有同步变更。
-->
<style scoped lang="scss">
@use '@/assets/styles/tokens-studio.scss';

.studio {
  min-height: calc(100vh - 135px);
  padding: 24px;
  overflow: hidden;
  color: var(--t1);
  background: var(--bg);
  background-image: radial-gradient(900px 460px at 84% -10%, rgba(148, 163, 184, 0.16), transparent 68%);
  border: 1px solid var(--line);
  border-radius: 8px;
}

button {
  font: inherit;
}

.guide-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  margin-bottom: 22px;
  color: #ddd6fe;
  font-size: 13px;
  background: rgba(148, 163, 184, 0.1);
  border: 1px solid rgba(186, 197, 209, 0.24);
  border-radius: 6px;
}
.guide-bar > span {
  flex: 1;
}
.guide-bar button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: var(--t2);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.studio-nav {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}
.studio-nav button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 108px;
  min-height: 38px;
  padding: 0 12px;
  color: var(--t2);
  font-size: 13px;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
}
.studio-nav button:hover {
  color: var(--t1);
  background: var(--sunken);
}
.studio-nav button.active {
  color: #fff;
  background: var(--tint);
  border-color: var(--p);
}

/* ================= 创建 ================= */
.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 390px);
  gap: 18px;
  max-width: 1480px;
  margin: 0 auto;
}
.studio-card {
  padding: 20px;
  background: rgba(18, 21, 28, 0.94);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}
.section-heading span {
  color: var(--t3);
  font-size: 11px;
}
.section-heading h2 {
  margin: 3px 0 0;
  font-size: 18px;
  letter-spacing: 0;
}
.section-heading.compact {
  margin-bottom: 14px;
}
.section-heading.compact h2 {
  margin: 0 0 4px;
  font-size: 15px;
}
.version-pill {
  flex: 0 0 auto;
  padding: 6px 9px;
  color: #ddd6fe !important;
  background: var(--tint);
  border: 1px solid rgba(186, 197, 209, 0.2);
  border-radius: 999px;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.capability {
  min-height: 98px;
  padding: 12px;
  color: var(--t2);
  text-align: left;
  cursor: pointer;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 7px;
  transition:
    border-color 0.18s,
    background 0.18s,
    transform 0.18s;
}
.capability:hover {
  border-color: rgba(186, 197, 209, 0.42);
  transform: translateY(-1px);
}
.capability.active {
  background: var(--tint);
  border-color: var(--p);
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.2);
}
.capability .el-icon {
  display: block;
  margin-bottom: 9px;
  color: var(--p-h);
  font-size: 21px;
}
.capability strong,
.capability small {
  display: block;
}
.capability strong {
  margin-bottom: 5px;
  color: var(--t1);
  font-size: 13px;
}
.capability small {
  min-height: 30px;
  color: var(--t3);
  font-size: 11px;
  line-height: 1.4;
}

.form-divider {
  height: 1px;
  margin: 20px 0;
  background: var(--line);
}
.field-block {
  margin-top: 18px;
}
.field-block > label {
  display: block;
  margin-bottom: 8px;
  color: var(--t2);
  font-size: 12px;
  font-weight: 600;
}
.field-block label em {
  color: var(--danger);
  font-style: normal;
}
.field-hint {
  margin: 8px 0 0;
  color: var(--t3);
  font-size: 11px;
  line-height: 1.6;
}
.field-hint + .field-hint {
  margin-top: 3px;
}
.model-group-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 0 8px;
  color: var(--t3);
  font-size: 11px;
}

/* 上传区：与视频页同款虚线大块，含已选图片预览 */
.upload-zone {
  display: grid !important;
  min-height: 112px;
  place-items: center;
  align-content: center;
  gap: 5px;
  padding: 16px;
  cursor: pointer;
  background: var(--sunken);
  border: 1px dashed var(--line2);
  border-radius: 7px;
}
.upload-zone:hover {
  border-color: var(--p-h);
}
.upload-zone.complete {
  color: var(--ok);
  border-color: rgba(52, 211, 153, 0.55);
}
.upload-zone input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}
.upload-zone .el-icon {
  font-size: 25px;
}
.upload-zone b {
  max-width: 100%;
  overflow: hidden;
  color: var(--t1);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.upload-zone small {
  color: var(--t3);
  font-size: 10px;
}
.upload-previews {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  justify-content: center;
  width: 100%;
}
.upload-previews figure {
  position: relative;
  margin: 0;
}
.upload-previews img {
  display: block;
  width: 64px;
  height: 64px;
  object-fit: cover;
  border: 1px solid var(--line2);
  border-radius: 6px;
}
.upload-previews figure > button {
  position: absolute;
  top: 3px;
  right: 3px;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  padding: 0;
  color: #fecaca;
  cursor: pointer;
  background: rgba(0, 0, 0, 0.66);
  border: 0;
  border-radius: 4px;
}
.upload-previews-badge {
  color: var(--t2);
  font-size: 11px;
}

.studio :deep(.el-select) {
  width: 100%;
}
.studio :deep(.el-select__wrapper),
.studio :deep(.el-textarea__inner),
.studio :deep(.el-input__wrapper) {
  color: var(--t1);
  background: var(--sunken);
  border-color: var(--line2);
  box-shadow: 0 0 0 1px var(--line2) inset;
}
.studio :deep(.el-select__wrapper:hover),
.studio :deep(.el-textarea__inner:hover),
.studio :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--p-h) inset;
}
.studio :deep(.el-textarea__inner) {
  min-height: 112px;
  padding: 12px;
}
.studio :deep(.el-input__inner) {
  color: var(--t1);
}
.studio :deep(.el-input__count) {
  color: var(--t3);
  background: transparent;
}

.choice-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.choice-grid.size-choices {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.choice-grid.strength-choices {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.choice-grid button {
  display: grid;
  gap: 3px;
  place-items: center;
  min-height: 42px;
  color: var(--t2);
  cursor: pointer;
  background: var(--sunken);
  border: 1px solid var(--line2);
  border-radius: 6px;
}
.choice-grid button small {
  color: var(--t3);
  font-size: 10px;
}
.choice-grid button.active {
  color: #fff;
  background: var(--tint);
  border-color: var(--p);
}
.choice-grid button.active small {
  color: #ddd6fe;
}
.choice-grid button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.submit-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
}
.submit-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-width: 140px;
  min-height: 42px;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  background: var(--grad);
  border: 0;
  border-radius: 6px;
  box-shadow: 0 8px 24px var(--glow);
}
.submit-button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}
.submit-row > span {
  color: var(--t3);
  font-size: 11px;
}

/* ================= 右栏 ================= */
.right-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.inspiration-list {
  display: grid;
  gap: 10px;
}
.inspiration-list article {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 7px;
}
.inspiration-poster {
  position: relative;
  display: grid;
  height: 58px;
  place-items: center;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.8);
  background: linear-gradient(135deg, #20103c, #6d28d9);
  border-radius: 5px;
}
.inspiration-poster.cyan {
  background: linear-gradient(135deg, #082f49, #0e7490);
}
.inspiration-poster.rose {
  background: linear-gradient(135deg, #4c0519, #be123c);
}
.inspiration-poster .el-icon {
  font-size: 20px;
}
.inspiration-poster span {
  position: absolute;
  right: 5px;
  bottom: 4px;
  left: 5px;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.72);
  font-size: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.inspiration-list b {
  color: var(--t1);
  font-size: 11px;
}
.inspiration-list p {
  display: -webkit-box;
  margin: 5px 0 0;
  overflow: hidden;
  color: var(--t3);
  font-size: 9px;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.inspiration-list article > button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 5px;
  color: var(--p-h);
  font-size: 10px;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.recent-task {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 9px 0;
  cursor: pointer;
  border-top: 1px solid var(--line);
}
.recent-task > span {
  display: grid;
  width: 9px;
  height: 9px;
  place-items: center;
  border: 1px solid var(--line2);
  border-radius: 50%;
}
.recent-task > span i {
  width: 5px;
  height: 5px;
  background: var(--t3);
  border-radius: 50%;
}
.recent-task > span.running i {
  background: var(--p-h);
  animation: pulse 1.5s infinite;
}
.recent-task > span.queued i {
  background: var(--warn);
}
.recent-task > span.done i {
  background: var(--ok);
}
.recent-task > span.failed i {
  background: var(--danger);
}
.recent-task b,
.recent-task small {
  display: block;
}
.recent-task b {
  color: var(--t1);
  font-size: 11px;
}
.recent-task small {
  margin-top: 3px;
  color: var(--t3);
  font-size: 9px;
}
.recent-task em {
  color: var(--t3);
  font-size: 9px;
  font-style: normal;
}

/* ================= 任务 / 素材 ================= */
.content-view {
  max-width: 1180px;
  margin: 0 auto;
}
.view-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin: 6px 0 18px;
}
.view-heading > div > span {
  color: var(--p-h);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.5px;
}
.view-heading h2 {
  margin: 5px 0 6px;
  color: var(--t1);
  font-size: 22px;
  letter-spacing: 0;
}
.view-heading p {
  margin: 0;
  color: var(--t3);
  font-size: 12px;
}
.primary-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 12px;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  background: var(--grad);
  border: 1px solid var(--p);
  border-radius: 6px;
}
.task-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px;
  margin-bottom: 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.task-toolbar :deep(.el-input) {
  width: min(340px, 100%);
}
.task-toolbar :deep(.el-input__wrapper) {
  background: var(--sunken);
  box-shadow: 0 0 0 1px var(--line2) inset;
}
.task-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.task-filters button {
  min-height: 32px;
  padding: 0 10px;
  color: var(--t2);
  font-size: 11px;
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--line2);
  border-radius: 5px;
}
.task-filters button.active {
  color: #fff;
  background: var(--tint);
  border-color: var(--p);
}

.task-list {
  display: grid;
  gap: 10px;
}
.task-card {
  display: grid;
  grid-template-columns: 116px minmax(0, 1fr) auto;
  align-items: center;
  gap: 15px;
  padding: 11px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.task-cover {
  position: relative;
  display: grid;
  min-height: 70px;
  place-items: center;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.8);
  background: linear-gradient(135deg, #20103c, #6d28d9);
  border-radius: 6px;
}
.task-cover.done {
  background: linear-gradient(135deg, #083344, #0e7490);
}
.task-cover.queued {
  background: linear-gradient(135deg, #442006, #b45309);
}
.task-cover.failed {
  background: linear-gradient(135deg, #450a0a, #b91c1c);
}
.task-cover.muted {
  background: linear-gradient(135deg, #1f2937, #475569);
}
.task-cover .el-icon {
  font-size: 24px;
}
.task-cover span {
  position: absolute;
  right: 6px;
  bottom: 5px;
  padding: 3px 5px;
  color: #e5e7eb;
  font-size: 9px;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 3px;
}
/* 有产出时封面放真实缩略图，点击/按钮都能开预览 */
.task-cover {
  cursor: pointer;
}
.task-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: inherit;
}
.task-cover .task-cover-zoom {
  position: absolute;
  right: 6px;
  bottom: 5px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  padding: 0;
  color: #e5e7eb;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 4px;
}
.task-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.task-title-row b {
  overflow: hidden;
  color: var(--t1);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-main p,
.task-main small {
  display: block;
  margin: 5px 0 0;
  color: var(--t3);
  font-size: 11px;
}
.task-main small {
  font-size: 10px;
}
.task-status {
  flex: 0 0 auto;
  padding: 3px 6px;
  color: var(--t3);
  font-size: 10px;
  background: var(--sunken);
  border-radius: 3px;
}
.task-status.running {
  color: #ddd6fe;
  background: var(--tint);
}
.task-status.queued {
  color: #fef3c7;
  background: rgba(245, 158, 11, 0.15);
}
.task-status.done {
  color: #bbf7d0;
  background: rgba(52, 211, 153, 0.12);
}
.task-status.failed {
  color: #fecaca;
  background: rgba(248, 113, 113, 0.14);
}
.task-error {
  color: #fca5a5 !important;
}
.task-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
.task-actions button,
.asset-card > button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  color: var(--t2);
  cursor: pointer;
  background: var(--sunken);
  border: 1px solid var(--line2);
  border-radius: 5px;
}
.task-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.asset-upload input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}
.asset-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.asset-card {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.asset-preview {
  display: grid;
  height: 62px;
  place-items: center;
  align-content: center;
  gap: 4px;
  overflow: hidden;
  color: #ddd6fe;
  background: linear-gradient(135deg, #20103c, #6d28d9);
  border-radius: 6px;
}
.asset-preview .el-icon {
  font-size: 20px;
}
.asset-preview span {
  font-size: 9px;
}
/* 素材真实缩略图：填满预览位并保持比例，不拉伸变形 */
.asset-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: pointer;
  border-radius: 6px;
}
.asset-info {
  min-width: 0;
}
.asset-info b,
.asset-info small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.asset-info b {
  color: var(--t1);
  font-size: 12px;
}
.asset-info small {
  margin-top: 5px;
  color: var(--t3);
  font-size: 10px;
}
.asset-card > button:hover {
  color: #fecaca;
  border-color: rgba(248, 113, 113, 0.4);
}
.empty-state {
  display: grid;
  min-height: 230px;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: var(--t3);
  background: var(--surface);
  border: 1px dashed var(--line2);
  border-radius: 8px;
}
.empty-state .el-icon {
  color: var(--p-h);
  font-size: 30px;
}
.empty-state b {
  color: var(--t2);
  font-size: 13px;
}
.empty-state span {
  font-size: 11px;
}

/* ================= 弹窗 ================= */
.detail-preview {
  margin: 14px 0;
}
.detail-preview img {
  width: 100%;
  border-radius: 10px;
}

/* 产出预览弹窗：内容在浅色弹窗里，故这里用固定深色文字而不是 studio 的浅色 token */
.preview-body {
  display: grid;
  gap: 14px;
}
.preview-image {
  width: 100%;
  max-height: 62vh;
  object-fit: contain;
  background: #0d1016;
  border-radius: 8px;
}
.preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin: 0;
}
.preview-meta > div {
  display: grid;
  gap: 2px;
}
.preview-meta dt {
  color: #6b7280;
  font-size: 11px;
}
.preview-meta dd {
  margin: 0;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.error-text {
  color: #ff9b9b;
}

@keyframes pulse {
  50% {
    box-shadow: 0 0 0 4px rgba(186, 197, 209, 0.12);
  }
}
@media (max-width: 1360px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }
  .right-column {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 820px) {
  .studio {
    padding: 16px;
  }
  .asset-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .capability-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .choice-grid.size-choices,
  .choice-grid.strength-choices {
    grid-template-columns: 1fr;
  }
  .right-column {
    display: flex;
  }
}
@media (max-width: 520px) {
  .studio-nav {
    gap: 5px;
    overflow-x: auto;
  }
  .studio-nav button {
    min-width: auto;
    padding: 0 9px;
    white-space: nowrap;
  }
  .view-heading,
  .task-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .primary-action {
    width: 100%;
  }
  .task-toolbar :deep(.el-input) {
    width: 100%;
  }
  .task-card {
    grid-template-columns: 74px minmax(0, 1fr);
    gap: 10px;
  }
  .task-cover {
    min-height: 64px;
  }
  .task-actions {
    grid-column: 2;
  }
  .asset-grid {
    grid-template-columns: 1fr;
  }
  .capability-grid {
    grid-template-columns: 1fr;
  }
  .submit-row {
    align-items: flex-start;
    flex-direction: column;
  }
  .inspiration-list article {
    grid-template-columns: 74px minmax(0, 1fr);
  }
  .inspiration-list article > button {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
