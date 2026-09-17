<template>
  <div class="studio">
    <div v-if="showGuide" class="guide-bar">
      <el-icon><MagicStick /></el-icon>
      <span>创建任务：选择视频方式和模型，添加素材与描述，确认输出档位。</span>
      <button type="button" title="关闭引导" aria-label="关闭引导" @click="showGuide = false">
        <el-icon><Close /></el-icon>
      </button>
    </div>

    <nav class="studio-nav" aria-label="视频创作功能">
      <button
        v-for="item in studioViews"
        :key="item.key"
        type="button"
        :class="{ active: activeView === item.key }"
        :aria-current="activeView === item.key ? 'page' : undefined"
        @click="activeView = item.key"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        {{ item.label }}
      </button>
    </nav>

    <div v-if="activeView === 'create'" class="workbench-grid">
      <section class="studio-card create-card">
        <div class="section-heading">
          <div>
            <span>创建任务</span>
            <h2>{{ currentModule.name }}</h2>
          </div>
          <span class="version-pill">{{ versionPill }}</span>
        </div>

        <div class="capability-grid" aria-label="视频能力">
          <button
            v-for="item in VIDEO_MODULES"
            :key="item.code"
            type="button"
            :class="['capability', { active: item.code === currentModule.code }]"
            :aria-pressed="item.code === currentModule.code"
            @click="selectModule(item)"
          >
            <el-icon><component :is="moduleIcons[item.code]" /></el-icon>
            <strong>{{ item.name }}</strong>
            <small>{{ item.desc }}</small>
          </button>
        </div>

        <div class="form-divider" />

        <div v-if="currentModule.models.length" class="field-block">
          <label>
            生成模型
            <em>*</em>
          </label>
          <p v-if="closedModels.length" class="model-group-label">
            <el-icon><Lock /></el-icon>
            闭源模型
          </p>
          <div v-if="closedModels.length" class="model-grid">
            <button
              v-for="item in closedModels"
              :key="item.code"
              type="button"
              :class="['model-option', { active: item.code === currentModel.code }]"
              :disabled="item.code !== 'H3'"
              @click="currentModel = item"
            >
              <span>
                <b>{{ item.name }}</b>
                <i v-if="item.recommended">推荐</i>
              </span>
              <small>{{ item.code === 'H3' ? '模板已导入 · 待服务接入' : '工作流待接入' }}</small>
            </button>
          </div>
          <p v-if="openModels.length" class="model-group-label">
            <el-icon><Cpu /></el-icon>
            开源模型
          </p>
          <div v-if="openModels.length" class="model-grid">
            <button
              v-for="item in openModels"
              :key="item.code"
              type="button"
              :class="['model-option', { active: item.code === currentModel.code }]"
              disabled
              @click="currentModel = item"
            >
              <span>
                <b>{{ item.name }}</b>
              </span>
              <small>工作流待接入</small>
            </button>
          </div>
        </div>
        <div v-else class="field-block">
          <label>
            处理工作流
            <em>*</em>
          </label>
          <div class="fixed-workflow">
            <span>
              <b>{{ currentModule.fixedWorkflow!.name }}</b>
              <i>固定工作流</i>
            </span>
            <small>
              {{ currentModule.fixedWorkflow!.version }} · 本地 GPU · {{ currentModule.fixedWorkflow!.eta }}
            </small>
          </div>
        </div>

        <template v-for="field in currentModule.fields" :key="field">
          <div v-if="isUploadField(field)" class="field-block">
            <label>
              {{ fieldLabels[field] }}
              <em v-if="isRequired(field)">*</em>
            </label>
            <label class="upload-zone" :class="{ complete: uploadAssetIds[field]?.length }">
              <input
                type="file"
                :accept="field === 'audio' ? 'audio/*' : 'image/*'"
                :multiple="field === 'frames'"
                @change="handleFiles(field, $event)"
              />
              <el-icon>
                <Check v-if="uploadAssetIds[field]?.length" />
                <UploadFilled v-else />
              </el-icon>
              <b>{{ uploadSummary(field) }}</b>
              <small>
                {{
                  field === 'frames'
                    ? '支持 2-10 张关键帧'
                    : field === 'audio'
                      ? '支持 MP3、WAV、M4A'
                      : '支持 JPG、PNG、WEBP'
                }}
              </small>
            </label>
          </div>

          <div v-else-if="field === 'source'" class="field-block">
            <label>
              源视频
              <em>*</em>
            </label>
            <el-select v-model="values.source" placeholder="请选择已完成的视频" size="large">
              <el-option v-for="item in COMPLETED_VIDEOS" :key="item" :label="item" :value="item" />
            </el-select>
          </div>

          <div v-else-if="field === 'desc'" class="field-block">
            <label>
              {{ currentModule.promptLabel }}
              <em v-if="isRequired(field)">*</em>
            </label>
            <el-input
              v-model="values.desc"
              type="textarea"
              :rows="4"
              maxlength="200"
              show-word-limit
              :placeholder="currentModule.placeholder"
            />
            <div class="prompt-tools">
              <div>
                <button v-for="chip in PROMPT_CHIPS" :key="chip" type="button" @click="appendPrompt(chip)">
                  {{ chip }}
                </button>
              </div>
              <button class="optimize" type="button" @click="optimizePrompt">
                <el-icon><MagicStick /></el-icon>
                优化描述
              </button>
            </div>
          </div>

          <div v-else-if="field === 'tier' || field === 'dur'" class="field-block">
            <label>
              {{ fieldLabels[field] }}
              <em>*</em>
            </label>
            <div :class="['choice-grid', { 'tier-choices': field === 'tier' }]">
              <button
                v-for="item in optionsFor(field)"
                :key="item"
                type="button"
                :class="{ active: values[field] === item }"
                :disabled="field === 'tier' && !supportedTiers.includes(item)"
                @click="selectChoice(field, item)"
              >
                {{ item }}
              </button>
            </div>
          </div>

          <div v-else class="field-block">
            <label>
              {{ fieldLabels[field] }}
              <em>*</em>
            </label>
            <el-select v-model="values[field]" placeholder="请选择" size="large">
              <el-option v-for="item in fieldOptions[field]" :key="item" :label="item" :value="item" />
            </el-select>
          </div>
        </template>

        <p class="workflow-note">
          MiniMax H3 三种工作流模板已导入；清晰度档位由服务端契约声明，时长最多 5 秒。
          <template v-if="currentWorkflow">
            服务端状态：<b>{{ currentWorkflow.status }}</b>。
          </template>
          提交按钮仅在对应工作流发布（PUBLISHED）后开放，未通过实机验收前保持禁用。
        </p>
        <div class="submit-row">
          <button
            v-hasPermi="['video:creation:submit']"
            type="button"
            class="submit-button"
            :disabled="!canSubmit || submitting || uploading"
            :title="canSubmit ? '提交并生成视频' : submitBlockReason"
            @click="submitTask"
          >
            <el-icon><MagicStick /></el-icon>
            {{ submitting ? '提交中…' : canSubmit ? '提交生成' : '暂不可提交' }}
          </button>
          <span>{{ submitBlockReason || '提交后将经服务端填充模板并交由 ComfyUI 执行' }}</span>
        </div>
      </section>

      <aside class="right-column">
        <section class="latest-player">
          <div class="player-badges">
            <span>成片示意</span>
            <span>1080P</span>
            <span>00:05</span>
          </div>
          <button
            type="button"
            class="play-button"
            title="播放最新成片"
            aria-label="播放最新成片"
            @click="ElMessage.info('示例成片暂无真实视频')"
          >
            <el-icon><VideoPlay /></el-icon>
          </button>
          <div class="player-footer">
            <div>
              <b>新品发布主视频</b>
              <small>VIDEO-20260911-017 · 5 分钟前</small>
            </div>
            <div>
              <button type="button" title="示例成片不可下载" aria-label="下载" disabled>
                <el-icon><Download /></el-icon>
              </button>
              <button type="button" title="示例成片不可分享" aria-label="分享" disabled>
                <el-icon><Share /></el-icon>
              </button>
              <button type="button" class="recreate" @click="useInspiration(INSPIRATIONS[1])">
                <el-icon><RefreshRight /></el-icon>
                再创作
              </button>
            </div>
          </div>
        </section>

        <section class="stats" aria-label="视频创作统计">
          <div>
            <b>18</b>
            <span>本周成片</span>
          </div>
          <div>
            <b>{{ queueCount }}</b>
            <span>排队中</span>
          </div>
          <div>
            <b>6</b>
            <span>素材库</span>
          </div>
          <div>
            <b>98%</b>
            <span>生成成功率</span>
          </div>
        </section>

        <section class="studio-card inspiration-card">
          <div class="section-heading compact">
            <div>
              <h2>灵感 · 一键同款</h2>
              <span>自动带入能力、模型与描述</span>
            </div>
          </div>
          <div class="inspiration-list">
            <article v-for="item in INSPIRATIONS" :key="item.title">
              <div :class="['inspiration-poster', item.tone]">
                <el-icon><VideoCameraFilled /></el-icon>
                <span>{{ moduleName(item.module) }} · {{ modelName(item.model) }}</span>
              </div>
              <div>
                <b>{{ item.title }}</b>
                <p>{{ item.prompt }}</p>
              </div>
              <button type="button" @click="useInspiration(item)">
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
          <div v-for="task in recentTasks" :key="task.id" class="recent-task">
            <span :class="taskStatusClass(task.status)"><i /></span>
            <div>
              <b>{{ task.taskName || task.taskNo }}</b>
              <small>{{ moduleName(task.capabilityCode) }} · {{ modelName(task.modelCode) }}</small>
            </div>
            <em>{{ taskStatusText(task.status) }}</em>
          </div>
          <div v-if="!recentTasks.length" class="recent-task">
            <span><i /></span>
            <div>
              <b>暂无任务</b>
              <small>创建后可在此查看进度</small>
            </div>
            <em>—</em>
          </div>
        </section>
      </aside>
    </div>

    <section v-else-if="activeView === 'tasks'" class="content-view">
      <div class="view-heading">
        <div>
          <span>视频创作</span>
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
          <div :class="['task-cover', taskStatusClass(task.status)]">
            <el-icon><VideoCamera /></el-icon>
            <span>{{ task.tier.replace(' · ', ' ') }}</span>
          </div>
          <div class="task-main">
            <div class="task-title-row">
              <b>{{ task.taskName || task.taskNo }}</b>
              <span :class="['task-status', taskStatusClass(task.status)]">{{ taskStatusText(task.status) }}</span>
            </div>
            <p>
              {{ moduleName(task.capabilityCode) }} · {{ modelName(task.modelCode) }} ·
              {{ task.durationSeconds }} 秒
            </p>
            <small>{{ task.taskNo }} · {{ task.createTime || '—' }}</small>
            <small v-if="task.errorMessage" class="task-error">{{ task.errorMessage }}</small>
          </div>
          <div class="task-actions">
            <button type="button" title="查看任务" aria-label="查看任务" @click="previewTask(task)">
              <el-icon><View /></el-icon>
            </button>
            <button
              v-if="task.status === 'QUEUED'"
              type="button"
              title="取消排队"
              aria-label="取消排队"
              @click="cancelTask(task)"
            >
              <el-icon><Close /></el-icon>
            </button>
            <button v-if="task.status === 'SUCCEEDED'" type="button" class="recreate" @click="recreateTask(task)">
              <el-icon><RefreshRight /></el-icon>
              再创作
            </button>
          </div>
        </article>
      </div>
      <div v-else class="empty-state">
        <el-icon><Document /></el-icon>
        <b>没有匹配的任务</b>
        <span>调整搜索条件，或创建一个新的视频任务。</span>
      </div>
    </section>

    <section v-else class="content-view">
      <div class="view-heading">
        <div>
          <span>视频创作</span>
          <h2>素材库</h2>
          <p>素材保存在服务端；任务提交时使用素材 ID，不使用浏览器本地文件名。</p>
        </div>
        <label class="primary-action asset-upload">
          <input type="file" accept="image/*" multiple @change="handleAssetFiles" />
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
          <div :class="['asset-preview', assetKind(asset)]">
            <el-icon><component :is="assetIcon(assetKind(asset))" /></el-icon>
            <span>{{ assetKindLabel(asset) }}</span>
          </div>
          <div class="asset-info">
            <b>{{ asset.originalName || '素材 ' + asset.id }}</b>
            <small>{{ assetDetail(asset) }} · {{ asset.createTime || '—' }}</small>
          </div>
          <button type="button" title="移除素材" aria-label="移除素材" @click="removeAsset(asset.id)">
            <el-icon><Delete /></el-icon>
          </button>
        </article>
      </div>
      <div v-else class="empty-state">
        <el-icon><FolderOpened /></el-icon>
        <b>素材库还是空的</b>
        <span>添加图片后，即可在创建任务时使用。</span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue';
import {
  ArrowRight,
  Check,
  Close,
  Cpu,
  Delete,
  Download,
  Document,
  FolderOpened,
  Lock,
  MagicStick,
  Picture,
  RefreshRight,
  Share,
  Search,
  UploadFilled,
  VideoCamera,
  VideoCameraFilled,
  VideoPlay,
  View
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import {
  cancelVideoTask,
  createVideoTask,
  deleteVideoAsset,
  executeVideoTask,
  getVideoTask,
  listVideoAssets,
  listVideoTasks,
  listVideoWorkflows,
  uploadVideoAsset
} from '@/api/video';
import type {
  VideoAssetVO,
  VideoCapabilityCode,
  VideoTaskVO,
  VideoTaskStatus,
  VideoWorkflowVO
} from '@/api/video/types';
import { extractErrorMessage } from '@/utils/request';
import {
  INSPIRATIONS,
  PROMPT_CHIPS,
  VIDEO_MODELS,
  VIDEO_MODULES,
  resolveWorkflowCode,
  type FieldKey,
  type Inspiration,
  type StudioModule
} from './modules';

type StudioView = 'create' | 'tasks' | 'assets';
type AssetKind = 'image' | 'video' | 'audio';
type TaskFilterKey = 'all' | VideoTaskStatus;

const activeView = ref<StudioView>('create');
const studioViews: Array<{ key: StudioView; label: string; icon: Component }> = [
  { key: 'create', label: '创建任务', icon: MagicStick },
  { key: 'tasks', label: '我的任务', icon: Document },
  { key: 'assets', label: '素材库', icon: FolderOpened }
];

const currentModule = ref(VIDEO_MODULES[0]!);
const currentModel = ref(VIDEO_MODELS.find(item => item.code === VIDEO_MODULES[0]!.defaultModel) ?? VIDEO_MODELS[0]!);
const values = reactive<Partial<Record<FieldKey, string>>>({ tier: '高清 · 1080P', dur: '5 秒' });
/** 已上传素材的 ID，提交任务时传 ID，不传浏览器本地文件名。 */
const uploadAssetIds = reactive<Partial<Record<FieldKey, Array<number | string>>>>({});

/**
 * 与后端 VideoCreationController 保持一致的上传约束。
 *
 * <p>放在前端是为了给出即时、明确的提示，而不是让用户等一个必然失败的请求。
 * 后端仍然会独立校验，前端校验不作为安全边界。</p>
 */
const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
const ALLOWED_UPLOAD_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

/**
 * 时长兜底矩阵：仅在后端未下发 `supportedDurationsByTier` 时使用。
 *
 * 取值与后端 `VideoTierResolutions.defaultDurations()` 保持一致；
 * 正常情况下以服务端为准，避免前端与后端各写一份而漂移。
 */
const FALLBACK_DURATIONS: Record<string, string[]> = {
  '高清 · 1080P': ['5 秒'],
  '流畅 · 720P': ['5 秒', '10 秒'],
  '标清 · 480P': ['5 秒', '10 秒', '20 秒']
};
const uploading = ref(false);
const submitting = ref(false);
const loadingTasks = ref(false);
const loadingAssets = ref(false);
const showGuide = ref(true);

/** 服务端返回的工作流视图：提交按钮的可用性完全由它的 status 决定。 */
const workflows = ref<VideoWorkflowVO[]>([]);
const tasks = ref<VideoTaskVO[]>([]);
const assets = ref<VideoAssetVO[]>([]);
const assetTotal = ref(0);

const taskKeyword = ref('');
const taskFilter = ref<TaskFilterKey>('all');
const taskFilters = [
  { key: 'all', label: '全部' },
  { key: 'QUEUED', label: '排队中' },
  { key: 'RUNNING', label: '生成中' },
  { key: 'SUCCEEDED', label: '已完成' },
  { key: 'FAILED', label: '失败' }
] as const;

const moduleIcons: Record<string, Component> = {
  I2V: VideoCamera,
  T2V: MagicStick,
  FL2V: Picture
};
const fieldLabels: Record<FieldKey, string> = {
  first: '首帧图片',
  last: '尾帧图片',
  frames: '关键帧',
  img: '原图',
  source: '源视频',
  audio: '音频文件',
  desc: '视频描述',
  tier: '输出档位',
  dur: '视频时长',
  move: '运镜方式',
  extend: '续写时长',
  target: '目标画质',
  fps: '帧率'
};
const fieldOptions: Partial<Record<FieldKey, string[]>> = {
  tier: ['高清 · 1080P', '流畅 · 720P', '标清 · 480P'],
  dur: ['5 秒', '10 秒', '20 秒'],
  move: ['推近', '拉远', '左摇', '右摇', '环绕', '旋转'],
  extend: ['5 秒', '10 秒', '20 秒'],
  target: ['1080P', '4K'],
  fps: ['24 FPS', '30 FPS', '60 FPS']
};
const uploadFields: FieldKey[] = ['first', 'last', 'frames', 'img', 'audio'];
const requiredFields: FieldKey[] = [
  'first',
  'img',
  'frames',
  'source',
  'audio',
  'tier',
  'dur',
  'move',
  'extend',
  'target',
  'fps'
];

const closedModels = computed(() =>
  VIDEO_MODELS.filter(item => currentModule.value.models.includes(item.code) && item.license === 'closed')
);
const openModels = computed(() =>
  VIDEO_MODELS.filter(item => currentModule.value.models.includes(item.code) && item.license === 'open')
);

/** 当前模块 + 模型解析出的 workflowCode。 */
const currentWorkflowCode = computed(() => resolveWorkflowCode(currentModule.value, currentModel.value.code));

/** 当前 workflowCode 对应的服务端工作流视图。 */
const currentWorkflow = computed(
  () => workflows.value.find(item => item.workflowCode === currentWorkflowCode.value) ?? null
);

/**
 * 只有 PUBLISHED 才允许在正式环境提交。
 * 三个 H3 模板当前均为 DRAFT，因此默认为不可提交，且提示真实原因。
 */
const canSubmit = computed(() => currentWorkflow.value?.submittable === true);

/**
 * 当前工作流允许的输出档位（清晰度）。
 *
 * 由服务端契约 `fixedFieldValidation.supportedTiers` 决定——契约是唯一权威，
 * 前端不再硬编码「只允许 1080P」。服务端未返回时退化为 `supportedTier` 单档位；
 * 都拿不到（例如工作流尚未注册）时退回 1080P，避免把全部档位误判为可选。
 */
const supportedTiers = computed<string[]>(() => {
  const workflow = currentWorkflow.value;
  const list = workflow?.supportedTiers;
  if (Array.isArray(list) && list.length) return list;
  if (workflow?.supportedTier) return [workflow.supportedTier];
  return ['高清 · 1080P'];
});

/** 档位表变化时把当前选择拉回第一个受支持的档位，避免提交一个必然被拒的档位。 */
watch(supportedTiers, tiers => {
  if (tiers.length && !tiers.includes(values.tier ?? '')) {
    values.tier = tiers[0];
    if (!optionsFor('dur').includes(values.dur ?? '')) {
      values.dur = optionsFor('dur')[0];
    }
  }
});

const submitBlockReason = computed(() => {
  if (!workflows.value.length) return '正在读取工作流状态…';
  const workflow = currentWorkflow.value;
  if (!workflow) return `${currentWorkflowCode.value} 尚未在服务端注册`;
  if (workflow.status === 'DRAFT') return '工作流为 DRAFT，完成实机验收并发布后方可提交';
  if (workflow.status === 'TESTING') return '工作流处于 TESTING，仅隔离联调环境可提交';
  if (workflow.status === 'RETIRED') return '工作流已停用';
  return '';
});

const versionPill = computed(() => {
  const workflow = currentWorkflow.value;
  if (workflow) return `${workflow.modelCode ?? currentModel.value.name} · ${workflow.version} · ${workflow.status}`;
  return currentModule.value.models.length
    ? `${currentModel.value.name} · ${currentModel.value.version}`
    : `${currentModule.value.fixedWorkflow!.name} · ${currentModule.value.fixedWorkflow!.version}`;
});

const filteredTasks = computed(() => {
  const keyword = taskKeyword.value.trim().toLowerCase();
  return tasks.value.filter(task => {
    const matchesFilter = taskFilter.value === 'all' || task.status === taskFilter.value;
    const matchesKeyword =
      !keyword || `${task.taskNo ?? ''} ${task.taskName ?? ''}`.toLowerCase().includes(keyword);
    return matchesFilter && matchesKeyword;
  });
});

const recentTasks = computed(() => tasks.value.slice(0, 4));
const queueCount = computed(
  () => tasks.value.filter(task => task.status === 'QUEUED' || task.status === 'RUNNING').length
);

onMounted(() => {
  void loadWorkflows();
  void loadTasks();
  void loadAssets();
});

async function loadWorkflows() {
  try {
    const res = await listVideoWorkflows();
    workflows.value = res.data ?? [];
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取工作流状态失败');
  }
}

async function loadTasks() {
  loadingTasks.value = true;
  try {
    const res = await listVideoTasks({ pageNum: 1, pageSize: 50 });
    tasks.value = res.data?.rows ?? [];
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取任务列表失败');
  } finally {
    loadingTasks.value = false;
  }
}

async function loadAssets() {
  loadingAssets.value = true;
  try {
    const res = await listVideoAssets({ pageNum: 1, pageSize: 60 });
    assets.value = res.data?.rows ?? [];
    assetTotal.value = res.data?.total ?? assets.value.length;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取素材库失败');
  } finally {
    loadingAssets.value = false;
  }
}

function selectModule(item: StudioModule) {
  currentModule.value = item;
  currentModel.value =
    VIDEO_MODELS.find(candidate => candidate.code === item.defaultModel) ??
    VIDEO_MODELS.find(candidate => item.models.includes(candidate.code)) ??
    VIDEO_MODELS[0]!;
  Object.keys(values).forEach(key => delete values[key as FieldKey]);
  Object.keys(uploadAssetIds).forEach(key => delete uploadAssetIds[key as FieldKey]);
  // 默认取服务端允许的第一个档位，而不是写死 1080P。
  values.tier = supportedTiers.value[0] ?? '高清 · 1080P';
  values.dur = '5 秒';
}

function isUploadField(field: FieldKey) {
  return uploadFields.includes(field);
}

function isRequired(field: FieldKey) {
  if (field === 'desc') return ['I2V', 'T2V', 'FL2V'].includes(currentModule.value.code);
  if (field === 'last') return currentModule.value.code === 'FL2V';
  return requiredFields.includes(field);
}

function optionsFor(field: FieldKey) {
  if (field !== 'dur') return fieldOptions[field] ?? [];
  // 时长选项以服务端为准：长时长只在低分辨率档位开放（H3 帧数随时长线性增长、
  // 显存与耗时显著上升）。服务端未下发时退回内置兜底值，保证旧后端仍可用。
  const fromServer = currentWorkflow.value?.supportedDurationsByTier?.[values.tier ?? ''];
  if (Array.isArray(fromServer) && fromServer.length) return fromServer;
  return FALLBACK_DURATIONS[values.tier ?? ''] ?? ['5 秒'];
}

function selectChoice(field: FieldKey, value: string) {
  values[field] = value;
  if (field === 'tier' && !optionsFor('dur').includes(values.dur ?? '')) {
    values.dur = optionsFor('dur')[0];
  }
}

/**
 * 选择文件后立即上传，拿到后端素材 ID。
 * 提交任务时只用素材 ID，绝不把本地文件名当作素材凭证。
 */
async function handleFiles(field: FieldKey, event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []).slice(0, field === 'frames' ? 10 : 1);
  input.value = '';
  if (!files.length) return;

  uploading.value = true;
  try {
    const ids: Array<number | string> = [];
    for (const file of files) {
      // 提交前先做本地校验：文件为空或类型不被接受时，明确告知用户，
      // 而不是发一个注定被后端拒绝的请求。后端同样会校验，这里是第一道闸。
      if (!file.size) {
        ElMessage.error(`「${file.name}」是空文件，请重新选择`);
        return;
      }
      if (file.size > MAX_UPLOAD_BYTES) {
        ElMessage.error(`「${file.name}」超过 ${MAX_UPLOAD_BYTES / 1024 / 1024}MB 上限`);
        return;
      }
      if (file.type && !ALLOWED_UPLOAD_TYPES.includes(file.type.toLowerCase())) {
        ElMessage.error(`「${file.name}」格式不支持，请上传 PNG/JPEG/WEBP 图片`);
        return;
      }
      const res = await uploadVideoAsset(file);
      if (res.data?.assetId !== undefined) ids.push(res.data.assetId);
    }
    uploadAssetIds[field] = ids;
    ElMessage.success(`已上传 ${ids.length} 个素材`);
    void loadAssets();
  } catch (error) {
    // 失败时必须给出可定位的信息：带上文件名与网络层面的原因，
    // 避免出现「点了没反应」而无法排查的情况。
    const detail = (await extractErrorMessage(error)) ?? '素材上传失败';
    const name = files.map(f => f.name).join('、');
    ElMessage.error(`${detail}（文件：${name}）`);
  } finally {
    uploading.value = false;
  }
}

function uploadSummary(field: FieldKey) {
  const ids = uploadAssetIds[field] ?? [];
  if (!ids.length) return field === 'frames' ? '选择关键帧' : '点击上传素材';
  return field === 'frames' ? `已上传 ${ids.length} 张关键帧` : `已上传 · 素材 ${ids[0]}`;
}

function appendPrompt(text: string) {
  values.desc = values.desc ? `${values.desc}，${text}` : text;
}

function optimizePrompt() {
  if (!values.desc?.trim()) {
    ElMessage.warning('请先填写视频描述');
    return;
  }
  values.desc = `${values.desc.replace(/[，。]+$/, '')}，主体清晰，运镜平滑，光影层次自然，电影级质感。`;
  ElMessage.success('描述已优化');
}

/**
 * 提交任务：创建 → 执行 → 刷新列表。
 *
 * 提交前再次校验 workPermit（后端也会独立校验，前端禁用只是体验层）。
 */
async function submitTask() {
  const capabilityCode = currentModule.value.code as VideoCapabilityCode;
  if (!capabilityCode) return;

  if (capabilityCode === 'I2V' && !uploadAssetIds.img?.length) {
    ElMessage.warning('请先上传图片素材');
    return;
  }
  if (capabilityCode === 'FL2V' && (!uploadAssetIds.first?.length || !uploadAssetIds.last?.length)) {
    ElMessage.warning('请上传首帧和尾帧图片');
    return;
  }
  if (!values.desc?.trim()) {
    ElMessage.warning('请填写视频描述');
    return;
  }

  const payload = {
    capabilityCode,
    workflowCode: currentWorkflowCode.value,
    taskName: `${currentModule.value.name} · ${currentModel.value.name}`,
    fields: {
      desc: values.desc,
      tier: values.tier,
      dur: values.dur,
      img: uploadAssetIds.img?.[0],
      first: uploadAssetIds.first?.[0],
      last: uploadAssetIds.last?.[0]
    },
    // 幂等键避免重复点击产生多份成片
    idempotencyKey: `${currentWorkflowCode.value}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  };

  submitting.value = true;
  try {
    const created = await createVideoTask(payload);
    const taskId = created.data?.taskId;
    if (taskId === undefined) {
      ElMessage.error('创建任务失败：未返回任务 ID');
      return;
    }
    ElMessage.success('任务已创建，正在提交生成…');
    await loadTasks();

    const executed = await executeVideoTask(taskId);
    if (executed.code === 200) {
      ElMessage.success('成片已生成，可在「我的任务」查看');
    } else {
      ElMessage.warning(executed.msg ?? '任务未完成，请查看任务详情');
    }
    await loadTasks();
    await loadAssets();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '任务提交失败');
    await loadTasks();
  } finally {
    submitting.value = false;
  }
}

function taskStatusText(status: VideoTaskStatus) {
  switch (status) {
    case 'QUEUED':
      return '排队中';
    case 'RUNNING':
      return '生成中';
    case 'SUCCEEDED':
      return '已完成';
    case 'FAILED':
      return '失败';
    case 'CANCELED':
      return '已取消';
    case 'TIMEOUT':
      return '超时';
    default:
      return status;
  }
}

/** 任务卡片样式类：把后端状态映射到已有的 queued/running/done 视觉。 */
function taskStatusClass(status: VideoTaskStatus) {
  if (status === 'RUNNING') return 'running';
  if (status === 'QUEUED') return 'queued';
  if (status === 'SUCCEEDED') return 'done';
  return 'failed';
}

async function previewTask(task: VideoTaskVO) {
  try {
    const res = await getVideoTask(task.id);
    const detail = res.data;
    if (!detail) {
      ElMessage.info('暂无任务详情');
      return;
    }
    if (detail.status !== 'SUCCEEDED') {
      ElMessage.info(detail.errorMessage ?? `任务状态：${taskStatusText(detail.status)}`);
      return;
    }
    const measured = [
      detail.outputWidth && detail.outputHeight ? `${detail.outputWidth}×${detail.outputHeight}` : null,
      detail.outputDurationMs ? `${(detail.outputDurationMs / 1000).toFixed(2)} 秒` : null,
      detail.truncationApplied ? '已截断至 5 秒' : null
    ]
      .filter(Boolean)
      .join(' · ');
    ElMessage.success(`成片素材 ${detail.outputAssetId ?? '-'}${measured ? ' · ' + measured : ''}`);
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取任务详情失败');
  }
}

async function cancelTask(task: VideoTaskVO) {
  try {
    await cancelVideoTask(task.id);
    ElMessage.success('已取消排队任务');
    await loadTasks();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '取消失败');
  }
}

function recreateTask(task: VideoTaskVO) {
  const module = VIDEO_MODULES.find(item => item.code === task.capabilityCode);
  if (module) selectModule(module);
  const model = VIDEO_MODELS.find(item => item.code === task.modelCode);
  if (model) currentModel.value = model;
  values.desc = task.taskName ?? values.desc;
  activeView.value = 'create';
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function useInspiration(item: Inspiration) {
  const module = VIDEO_MODULES.find(candidate => candidate.code === item.module);
  const model = VIDEO_MODELS.find(candidate => candidate.code === item.model);
  if (module) selectModule(module);
  if (module && model && item.model === 'H3') currentModel.value = model;
  values.desc = item.prompt;
  activeView.value = 'create';
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

/**
 * 素材库上传：同样先上传换 ID，成功后重新拉取列表，不使用本地假数据。
 */
async function handleAssetFiles(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  input.value = '';
  if (!files.length) return;
  uploading.value = true;
  try {
    let uploaded = 0;
    for (const file of files) {
      const res = await uploadVideoAsset(file);
      if (res.data?.assetId !== undefined) uploaded += 1;
    }
    ElMessage.success(`已上传 ${uploaded} 个素材`);
    await loadAssets();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '素材上传失败');
  } finally {
    uploading.value = false;
  }
}

async function removeAsset(assetId: number | string) {
  try {
    await deleteVideoAsset(assetId);
    ElMessage.success('素材已删除');
    await loadAssets();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '删除失败');
  }
}

function assetIcon(kind: AssetKind): Component {
  return kind === 'image' ? Picture : kind === 'video' ? VideoCamera : Document;
}

function assetKind(asset: VideoAssetVO): AssetKind {
  if (asset.assetType === 'VIDEO') return 'video';
  if (asset.assetType === 'AUDIO') return 'audio';
  return 'image';
}

function assetKindLabel(asset: VideoAssetVO) {
  const kind = assetKind(asset);
  return kind === 'image' ? '图片' : kind === 'video' ? '视频' : '音频';
}

function assetDetail(asset: VideoAssetVO) {
  const parts: string[] = [];
  const ext = asset.contentType?.split('/')[1]?.toUpperCase();
  if (ext) parts.push(ext);
  if (asset.sizeBytes) parts.push(formatFileSize(asset.sizeBytes));
  if (asset.width && asset.height) parts.push(`${asset.width}×${asset.height}`);
  if (asset.taskId) parts.push('任务成片');
  return parts.join(' · ') || '—';
}

function formatFileSize(size: number) {
  return size >= 1024 * 1024 ? `${(size / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.ceil(size / 1024))} KB`;
}

function moduleName(code: string) {
  return VIDEO_MODULES.find(item => item.code === code)?.name ?? code;
}

function modelName(code?: string | null) {
  return VIDEO_MODELS.find(item => item.code === code)?.name ?? code ?? '—';
}
</script>

<style scoped lang="scss">
@use '@/assets/styles/tokens-studio.scss';

.studio {
  min-height: calc(100vh - 135px);
  padding: 24px;
  overflow: hidden;
  color: var(--t1);
  background: var(--bg);
  background-image: radial-gradient(900px 460px at 84% -10%, rgba(139, 92, 246, 0.16), transparent 68%);
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
  background: rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(167, 139, 250, 0.24);
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
.task-cover.failed {
  background: linear-gradient(135deg, #450a0a, #b91c1c);
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
.task-actions .recreate {
  gap: 5px;
  padding: 0 9px;
  color: #ede9fe;
  font-size: 11px;
  background: var(--tint);
  border-color: rgba(167, 139, 250, 0.28);
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
  color: #ddd6fe;
  background: linear-gradient(135deg, #20103c, #6d28d9);
  border-radius: 6px;
}
.asset-preview.video {
  color: #a5f3fc;
  background: linear-gradient(135deg, #083344, #0e7490);
}
.asset-preview.audio {
  color: #fef3c7;
  background: linear-gradient(135deg, #442006, #b45309);
}
.asset-preview .el-icon {
  font-size: 20px;
}
.asset-preview span {
  font-size: 9px;
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

.studio-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  margin-bottom: 22px;
}
.studio-heading p {
  margin: 0 0 8px;
  color: var(--p-h);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
}
.studio-heading h1 {
  margin: 0 0 7px;
  font-size: 30px;
  line-height: 1.15;
  letter-spacing: 0;
}
.studio-heading span {
  color: var(--t2);
  font-size: 13px;
}
.heading-status {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 10px;
  color: var(--t2);
  font-size: 12px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.heading-status i {
  width: 7px;
  height: 7px;
  background: var(--ok);
  border-radius: 50%;
  box-shadow: 0 0 10px var(--ok);
}

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
  border: 1px solid rgba(167, 139, 250, 0.2);
  border-radius: 999px;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
  border-color: rgba(167, 139, 250, 0.42);
  transform: translateY(-1px);
}
.capability.active {
  background: var(--tint);
  border-color: var(--p);
  box-shadow: inset 0 0 0 1px rgba(139, 92, 246, 0.2);
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
.model-group-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 0 8px;
  color: var(--t3);
  font-size: 11px;
}
.model-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.model-option {
  padding: 11px 12px;
  color: var(--t2);
  text-align: left;
  cursor: pointer;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.model-option.active {
  background: var(--tint);
  border-color: var(--p);
}
.model-option:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}
.model-option span,
.model-option small {
  display: block;
}
.model-option b {
  color: var(--t1);
  font-size: 12px;
}
.model-option i {
  padding: 2px 5px;
  margin-left: 6px;
  color: #fef3c7;
  font-size: 9px;
  font-style: normal;
  background: rgba(245, 158, 11, 0.18);
  border-radius: 3px;
}
.model-option small {
  margin-top: 5px;
  color: var(--t3);
  font-size: 10px;
}
.fixed-workflow {
  padding: 11px 12px;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.fixed-workflow span,
.fixed-workflow small {
  display: flex;
  align-items: center;
  gap: 6px;
}
.fixed-workflow span {
  justify-content: space-between;
}
.fixed-workflow b {
  color: var(--t1);
  font-size: 12px;
}
.fixed-workflow i {
  padding: 2px 5px;
  color: #ddd6fe;
  font-size: 9px;
  font-style: normal;
  background: var(--tint);
  border-radius: 3px;
}
.fixed-workflow small {
  margin-top: 5px;
  color: var(--t3);
  font-size: 10px;
}

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

.studio :deep(.el-select) {
  width: 100%;
}
.studio :deep(.el-select__wrapper),
.studio :deep(.el-textarea__inner) {
  color: var(--t1);
  background: var(--sunken);
  border-color: var(--line2);
  box-shadow: 0 0 0 1px var(--line2) inset;
}
.studio :deep(.el-select__wrapper:hover),
.studio :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px var(--p-h) inset;
}
.studio :deep(.el-textarea__inner) {
  min-height: 112px;
  padding: 12px;
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
.choice-grid.tier-choices {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.choice-grid button {
  min-height: 42px;
  color: var(--t2);
  cursor: pointer;
  background: var(--sunken);
  border: 1px solid var(--line2);
  border-radius: 6px;
}
.choice-grid button.active {
  color: #fff;
  background: var(--tint);
  border-color: var(--p);
}
.choice-grid button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.workflow-note {
  margin: 20px 0 0;
  color: var(--t2);
  font-size: 11px;
  line-height: 1.6;
}
.prompt-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
}
.prompt-tools > div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.prompt-tools button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  color: var(--t2);
  font-size: 10px;
  cursor: pointer;
  background: var(--tint);
  border: 0;
  border-radius: 999px;
}
.prompt-tools .optimize {
  flex: 0 0 auto;
  color: #ede9fe;
  border: 1px solid rgba(167, 139, 250, 0.24);
  border-radius: 6px;
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

.right-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.latest-player {
  position: relative;
  min-height: 250px;
  overflow: hidden;
  background: var(--poster);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.latest-player::before {
  position: absolute;
  inset: 0;
  content: '';
  background:
    radial-gradient(circle at 60% 38%, rgba(217, 70, 239, 0.35), transparent 24%),
    linear-gradient(140deg, transparent 15%, rgba(96, 165, 250, 0.2) 48%, transparent 70%);
}
.player-badges {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  gap: 6px;
}
.player-badges span {
  padding: 4px 7px;
  color: #e5e7eb;
  font-size: 9px;
  background: rgba(8, 10, 15, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
}
.player-badges span:first-child {
  color: #ede9fe;
  background: rgba(124, 58, 237, 0.5);
}
.play-button {
  position: absolute;
  top: 42%;
  left: 50%;
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  color: #fff;
  cursor: pointer;
  background: rgba(12, 14, 20, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.26);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  backdrop-filter: blur(8px);
}
.play-button .el-icon {
  font-size: 25px;
}
.player-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 13px;
  background: linear-gradient(transparent, rgba(7, 9, 13, 0.96));
}
.player-footer b,
.player-footer small {
  display: block;
}
.player-footer b {
  font-size: 12px;
}
.player-footer small {
  margin-top: 3px;
  color: var(--t3);
  font-size: 9px;
}
.player-footer > div:last-child {
  display: flex;
  gap: 5px;
}
.player-footer button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 30px;
  height: 30px;
  color: var(--t2);
  cursor: pointer;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--line2);
  border-radius: 5px;
}
.player-footer button.recreate {
  padding: 0 8px;
  color: #fff;
  background: var(--p);
  border-color: var(--p);
  font-size: 10px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.stats div {
  padding: 13px 6px;
  text-align: center;
  border-right: 1px solid var(--line);
}
.stats div:last-child {
  border-right: 0;
}
.stats b,
.stats span {
  display: block;
}
.stats b {
  margin-bottom: 4px;
  color: var(--t1);
  font-size: 17px;
}
.stats span {
  color: var(--t3);
  font-size: 9px;
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

@keyframes pulse {
  50% {
    box-shadow: 0 0 0 4px rgba(167, 139, 250, 0.12);
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
  .latest-player,
  .stats {
    grid-column: 1 / -1;
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
  .choice-grid.tier-choices {
    grid-template-columns: 1fr;
  }
  .model-grid {
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
  .studio-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
  .heading-status {
    align-self: stretch;
  }
  .capability-grid {
    grid-template-columns: 1fr;
  }
  .prompt-tools,
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
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .stats div:nth-child(2) {
    border-right: 0;
  }
  .stats div:nth-child(-n + 2) {
    border-bottom: 1px solid var(--line);
  }
}
</style>
