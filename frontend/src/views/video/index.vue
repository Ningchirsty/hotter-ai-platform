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
              <!--
                已选图片的预览。没有它，用户只能看到一行「素材 1234567890」，
                传错图要等生成完才发现。
              -->
              <div v-if="uploadPreviews[field]?.length" class="upload-previews">
                <img
                  v-for="(src, index) in uploadPreviews[field]"
                  :key="index"
                  :src="src"
                  :alt="`已选素材 ${index + 1}`"
                />
                <span v-if="uploadAssetIds[field]?.length" class="upload-previews-badge">
                  已上传 {{ uploadAssetIds[field]!.length }} 张
                </span>
              </div>
              <template v-else>
                <el-icon>
                  <Check v-if="uploadAssetIds[field]?.length" />
                  <UploadFilled v-else />
                </el-icon>
                <b>{{ uploadSummary(field) }}</b>
              </template>
              <small>
                {{
                  uploading && uploadPercent > 0
                    ? `上传中 ${uploadPercent}%…`
                    : field === 'frames'
                      ? '支持 2-10 张关键帧'
                      : field === 'audio'
                        ? '支持 MP3、WAV、M4A'
                        : '支持 JPG、PNG、WEBP，单张不超过 20MB（大图会自动压缩）'
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
              maxlength="1000"
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
            <!--
              画面比例：只决定下面清晰度档位的分组，本身不是提交字段——
              档位才是唯一的输出旋钮（契约声明、服务端校验、随任务落库、成片尺寸断言都用它）。
              只有服务端确实下发了多个比例时才渲染这一组按钮。
            -->
            <template v-if="field === 'tier' && supportedRatios.length > 1">
              <label>
                画面比例
                <em>*</em>
              </label>
              <div class="choice-grid ratio-choices" aria-label="画面比例">
                <button
                  v-for="item in supportedRatios"
                  :key="item"
                  type="button"
                  :class="{ active: selectedRatio === item }"
                  :aria-pressed="selectedRatio === item"
                  @click="selectRatio(item)"
                >
                  {{ item }}
                </button>
              </div>
              <label>
                {{ fieldLabels[field] }}
                <em>*</em>
              </label>
            </template>
            <label v-else>
              {{ fieldLabels[field] }}
              <em>*</em>
            </label>
            <div :class="['choice-grid', { 'tier-choices': field === 'tier' }]">
              <button
                v-for="item in optionsFor(field)"
                :key="item"
                type="button"
                :class="{ active: values[field] === item }"
                :disabled="field === 'tier' && !ratioTiers.includes(item)"
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

      <!--
        GPU 队列状态：生成一次要 2~12 分钟，双卡也只有两个并发位。
        如实显示「几张卡在跑、前面还排着几个」，用户才分得清「在排队」和「卡住了」。
      -->
      <div v-if="workers" class="gpu-status">
        <span class="gpu-status-dot" :class="{ busy: busyWorkerCount > 0 }"></span>
        <span>GPU 运行中 {{ busyWorkerCount }}/{{ workers.concurrency }}</span>
        <span v-if="workers.queued > 0">· 排队 {{ workers.queued }} 个</span>
        <span v-if="unavailableWorkers.length" class="gpu-status-warn">
          · {{ unavailableWorkers.length }} 张卡暂不可用（{{ unavailableWorkers[0]!.reason || '显存被占用' }}）
        </span>
      </div>

      <div v-if="loadingTasks" class="empty-state">
        <el-icon><Document /></el-icon>
        <b>正在加载任务…</b>
      </div>
      <div v-else-if="filteredTasks.length" class="task-list">
        <article v-for="task in filteredTasks" :key="task.id" class="task-card">
          <div :class="['task-cover', taskStatusClass(task.status)]">
            <!--
              成片封面：成功任务的输出素材首帧。
              图片与视频都用 <img> 显示（mp4 的首帧大多数浏览器可直接渲染），
              取不到时回退成原来的图标，避免空白。
            -->
            <img
              v-if="coverFor(task)"
              class="task-cover-img"
              :src="coverFor(task)"
              :alt="task.taskName || task.taskNo"
            />
            <template v-else>
              <el-icon><VideoCamera /></el-icon>
              <span>{{ task.tier.replace(' · ', ' ') }}</span>
            </template>
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
            <small>
              {{ task.taskNo }} · {{ task.createTime || '—' }}
              <!-- 多卡后同一个 prompt 只在提交它的那台 ComfyUI 上可查，排障时要知道去问哪台实例。 -->
              <span v-if="task.comfyWorker" class="task-worker">GPU {{ task.comfyWorker }}</span>
            </small>
            <small v-if="task.errorMessage" class="task-error">{{ task.errorMessage }}</small>
          </div>
          <div class="task-actions">
            <button
              type="button"
              :title="task.status === 'SUCCEEDED' ? '预览成片' : '查看任务'"
              :aria-label="task.status === 'SUCCEEDED' ? '预览成片' : '查看任务'"
              @click="previewTask(task)"
            >
              <el-icon><component :is="task.status === 'SUCCEEDED' ? VideoPlay : View" /></el-icon>
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
            <!--
              终态失败/超时/被取消的任务可以一键重新执行。
              提示语一直写着「请重新执行该任务」，但过去没有这个入口，用户只能手动重建一条。
            -->
            <button
              v-if="retryable(task)"
              type="button"
              class="recreate"
              :disabled="retryingId === String(task.id)"
              title="按原参数重新执行"
              aria-label="重新执行"
              @click="retryTask(task)"
            >
              <el-icon><RefreshRight /></el-icon>
              {{ retryingId === String(task.id) ? '重新执行中…' : '重新执行' }}
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
            <!-- 真实缩略图；取不到时回退成图标，不让卡片出现空白 -->
            <img
              v-if="imageFor(asset)"
              class="asset-thumb"
              :src="imageFor(asset)"
              :alt="asset.originalName || ''"
            />
            <template v-else>
              <el-icon><component :is="assetIcon(assetKind(asset))" /></el-icon>
              <span>{{ assetKindLabel(asset) }}</span>
            </template>
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

    <!--
      成片预览弹窗。
      能真正播放的依据是后端 /video/assets/{id}/content：它按属主校验后才返回内容。
      这里用带鉴权取回的 blob URL 交给 <video>，因为 <video src> 不会携带 Authorization 头。
    -->
    <el-dialog
      v-model="previewVisible"
      :title="previewTarget?.taskName || previewTarget?.taskNo || '成片预览'"
      width="min(920px, 92vw)"
      top="6vh"
      destroy-on-close
      @closed="closePreview"
    >
      <div class="preview-body">
        <div v-if="previewLoading" class="empty-state">
          <el-icon><VideoPlay /></el-icon>
          <b>正在加载成片…</b>
        </div>
        <div v-else-if="previewError" class="empty-state">
          <el-icon><Close /></el-icon>
          <b>{{ previewError }}</b>
        </div>
        <video
          v-else-if="previewUrl"
          class="preview-video"
          :src="previewUrl"
          controls
          autoplay
          playsinline
          preload="metadata"
        ></video>
        <div v-else class="empty-state">
          <el-icon><VideoPlay /></el-icon>
          <b>该任务暂无成片</b>
        </div>

        <dl v-if="previewMeta.length" class="preview-meta">
          <div v-for="row in previewMeta" :key="row.label">
            <dt>{{ row.label }}</dt>
            <dd>{{ row.value }}</dd>
          </div>
        </dl>
      </div>
      <template #footer>
        <el-button :disabled="!previewUrl" @click="downloadPreview">
          <el-icon><Download /></el-icon>
          下载成片
        </el-button>
        <el-button type="primary" @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
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
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import {
  cancelVideoTask,
  createVideoTask,
  deleteVideoAsset,
  executeVideoTask,
  fetchVideoAssetBlobUrl,
  fetchVideoAssetThumbnailBlobUrl,
  getVideoTask,
  getVideoWorkers,
  listVideoAssets,
  listVideoTasks,
  listVideoWorkflows,
  retryVideoTask,
  uploadVideoAsset
} from '@/api/video';
import type {
  VideoAssetVO,
  VideoCapabilityCode,
  VideoTaskVO,
  VideoTaskStatus,
  VideoWorkersVO,
  VideoWorkflowVO
} from '@/api/video/types';
import { extractErrorMessage } from '@/utils/request';
import {
  COMPLETED_VIDEOS,
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
 * 每个上传字段的本地预览图（blob URL）。
 *
 * <p>为什么要有：用户选完图之后，界面上原来只有一行「已上传 · 素材 1234567890」，
 * 根本无法确认选的是哪张图——传错图只能等生成完才发现。这里在选图后立刻用本地
 * 文件生成预览，不用等后端返回，也不产生额外请求。</p>
 */
const uploadPreviews = reactive<Partial<Record<FieldKey, string[]>>>({});

/** 释放某个字段的本地预览，避免一直占着内存。 */
function releaseUploadPreviews(field: FieldKey) {
  for (const url of uploadPreviews[field] ?? []) {
    URL.revokeObjectURL(url);
  }
  uploadPreviews[field] = [];
}

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

/** 上传进度（0-100）。慢链路下一次上传要几十秒，没有它用户只会觉得卡住了。 */
const uploadPercent = ref(0);

/**
 * 上传前把过大的图片压到合理尺寸。
 *
 * <p><b>为什么必须这么做。</b>源站是 cloudflared tunnel，Cloudflare 免费版对源站响应
 * 有 100 秒上限：慢链路（手机 4G、弱 Wi-Fi、国际链路）上传一张 12MB 的相机原图，
 * 光上传就要一两分钟，结果就是 <b>HTTP 524</b>，用户看到「系统未知异常」——
 * 文件完全合法却传不上去。而 H3 只需要一张条件图，工作流内部还会缩到 1280~1920，
 * 2048px 早已足够。</p>
 *
 * <p><b>为什么是按目标体积逐级压，而不是固定一档。</b>照片压到 2048px/JPG 0.92 通常
 * 只有几百 KB；但截图、噪点图、扫描件这类难压的图可能仍有 3~4MB，在慢上行下依旧
 * 会撞 100 秒。所以给一个目标体积，压不到就依次降尺寸与质量，最多四级；</p>
 *
 * <p>只对大图动手：小图（≤3MB）原样上传，不做任何有损处理；带透明通道的用 WebP
 * （JPEG 会把透明压成黑块），其余用 JPEG。任何一步失败都退回原文件——
 * 压缩只是优化，不能成为新的失败点。</p>
 */
const SHRINK_THRESHOLD_BYTES = 3 * 1024 * 1024;
/** 压缩目标：一次上传应在一分钟内完成（慢上行 ~35KB/s 也能压进 100 秒的 CF 上限）。 */
const SHRINK_TARGET_BYTES = 1.5 * 1024 * 1024;
/** [最长边, JPEG/WebP 质量] 逐级降档。 */
const SHRINK_LADDER: Array<[number, number]> = [
  [2048, 0.92],
  [1920, 0.85],
  [1600, 0.8],
  [1280, 0.72]
];

async function shrinkForUpload(file: File): Promise<File> {
  if (!file.type.startsWith('image/') || file.size <= SHRINK_THRESHOLD_BYTES) return file;
  try {
    const bitmap = await createImageBitmap(file);
    const alpha = file.type === 'image/png' || file.type === 'image/webp';
    const outType = alpha ? 'image/webp' : 'image/jpeg';
    let best: Blob | null = null;
    for (const [maxEdge, quality] of SHRINK_LADDER) {
      const longEdge = Math.max(bitmap.width, bitmap.height);
      const scale = Math.min(1, maxEdge / longEdge);
      const width = Math.max(1, Math.round(bitmap.width * scale));
      const height = Math.max(1, Math.round(bitmap.height * scale));
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext('2d');
      if (!ctx) return file;
      if (!alpha) {
        // JPEG 无透明通道：先铺白底，避免透明区域变黑
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, width, height);
      }
      ctx.drawImage(bitmap, 0, 0, width, height);
      const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, outType, quality));
      if (!blob) return file;
      // 这一档已经够小，或者已经压到最低一档，就收手
      if (!best || blob.size < best.size) best = blob;
      if (blob.size <= SHRINK_TARGET_BYTES) break;
    }
    if (!best || best.size >= file.size) return file;
    const name = file.name.replace(/\.[^.]+$/, '') + (alpha ? '.webp' : '.jpg');
    return new File([best], name, { type: outType });
  } catch {
    return file;
  }
}
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

/**
 * 「画面比例 → 档位」分组。
 *
 * <p>比例不是提交字段：档位才是这套模块里唯一的输出旋钮（契约声明、服务端校验、随任务落库、
 * 成片尺寸断言都用它）。服务端把分组下发过来，前端只做展示：先选比例，再在组内选清晰度，
 * 提交的仍是档位名。这样横竖屏并存而无需改任务表与提交流程。</p>
 *
 * <p>旧后端只下发扁平的 `supportedTiers` 时按档位名前缀兜底分组，保证向前兼容。</p>
 */
const tiersByRatio = computed<Record<string, string[]>>(() => {
  const fromServer = currentWorkflow.value?.supportedTiersByRatio;
  if (fromServer && Object.keys(fromServer).length) return fromServer;
  const groups: Record<string, string[]> = { '16:9 横屏': [], '9:16 竖屏': [] };
  for (const tier of supportedTiers.value) {
    if (tier.startsWith('竖屏')) groups['9:16 竖屏'].push(tier);
    else groups['16:9 横屏'].push(tier);
  }
  return Object.fromEntries(Object.entries(groups).filter(([, list]) => list.length));
});

/** 可选的画面比例（服务端下发；退回分组表的键）。 */
const supportedRatios = computed<string[]>(() => {
  const list = currentWorkflow.value?.supportedRatios;
  if (Array.isArray(list) && list.length) return list;
  return Object.keys(tiersByRatio.value);
});

/** 当前选中的画面比例（仅展示用状态，不随任务提交）。 */
const selectedRatio = ref<string>('');

/** 当前比例下的档位；分组缺失时退回全部受支持档位。 */
const ratioTiers = computed<string[]>(() => {
  const grouped = tiersByRatio.value[selectedRatio.value];
  if (Array.isArray(grouped) && grouped.length) return grouped;
  return supportedTiers.value;
});

/** 切换比例：把档位拉回该比例下的第一档，并同步时长档位。 */
function selectRatio(ratio: string) {
  selectedRatio.value = ratio;
  const tiers = tiersByRatio.value[ratio] ?? [];
  if (tiers.length && !tiers.includes(values.tier ?? '')) {
    values.tier = tiers[0];
  }
  if (!optionsFor('dur').includes(values.dur ?? '')) {
    values.dur = optionsFor('dur')[0];
  }
}

/**
 * 比例与服务端档位变化时，把「比例 + 档位」拉回一个必然可提交的组合。
 *
 * <p>两条 watch 各管一段：前者保证选中的比例仍在服务端允许范围内（并优先用服务端给的默认比例），
 * 后者保证当前档位属于已选比例。合并成一条会因为互相触发而难以推理。</p>
 */
watch(
  supportedRatios,
  names => {
    const preferred = currentWorkflow.value?.defaultRatio;
    if (!names.length || names.includes(selectedRatio.value)) return;
    selectRatio(preferred && names.includes(preferred) ? preferred : names[0]);
  },
  { immediate: true }
);

watch(
  ratioTiers,
  tiers => {
    if (tiers.length && !tiers.includes(values.tier ?? '')) {
      values.tier = tiers[0];
      if (!optionsFor('dur').includes(values.dur ?? '')) {
        values.dur = optionsFor('dur')[0];
      }
    }
  },
  { immediate: true }
);

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
    // 页面刷新/重新进来时，之前提交的任务仍在后台跑（执行在服务端，和这个页面无关）。
    // 必须把它们纳入轮询，否则任务状态和 GPU 队列行会一直停在打开页面那一刻的值——
    // 用户刷新一次就会看到「明明在生成却显示 0/2、任务一直排队中」。
    for (const task of tasks.value) {
      if (!TERMINAL_STATUSES.includes(task.status)) {
        startTaskPolling(task.id);
      }
    }
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取任务列表失败');
  } finally {
    loadingTasks.value = false;
    // 顺带刷新 GPU 队列状态：任务列表是用户唯一能看到"还要等多久"的地方。
    void loadWorkerStatus();
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
  uploadFields.forEach(field => releaseUploadPreviews(field));
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
  // 档位选项按已选画面比例过滤：竖屏比例下只给竖屏档位，避免选出一个必然被拒的组合。
  if (field === 'tier') return ratioTiers.value.length ? ratioTiers.value : fieldOptions.tier ?? [];
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
    const previews: string[] = [];

    // 先把这一批文件全部校验完，再动界面。
    // 反例（曾经的写法）：边校验边清空预览 —— 用户误选了一个 24MB 的文件时，
    // 原来选好的那张图会被擦掉（素材 ID 还在），界面与真实状态对不上，
    // 而且撤销 blob URL 还会在控制台留下 ERR_FILE_NOT_FOUND。
    for (const file of files) {
      if (!file.size) {
        ElMessage.error(`「${file.name}」是空文件，请重新选择`);
        return;
      }
      if (file.size > MAX_UPLOAD_BYTES) {
        ElMessage.error(
          `「${file.name}」${(file.size / 1024 / 1024).toFixed(1)}MB，超过 ${MAX_UPLOAD_BYTES / 1024 / 1024}MB 上限，请压缩后再传`
        );
        return;
      }
      if (file.type && !ALLOWED_UPLOAD_TYPES.includes(file.type.toLowerCase())) {
        ElMessage.error(`「${file.name}」格式不支持，请上传 PNG/JPEG/WEBP 图片`);
        return;
      }
    }

    releaseUploadPreviews(field);
    for (const file of files) {
      if (file.type.startsWith('image/')) {
        // 创建对象 URL 后立刻挂上去：预览不能等网络——慢链路下一张 12MB 的图
        // 要十几秒，用户得在这之前就确认自己选对了图。
        previews.push(URL.createObjectURL(file));
        uploadPreviews[field] = [...previews];
      }
      // 大图先压缩：经 Cloudflare 慢链路上传 10MB+ 会撞上 100 秒源站超时（524）。
      const prepared = await shrinkForUpload(file);
      if (prepared !== file) {
        ElMessage.info(
          `「${file.name}」${(file.size / 1048576).toFixed(1)}MB 已压缩为 ${(prepared.size / 1048576).toFixed(1)}MB 上传`
        );
      }
      uploadPercent.value = 0;
      const res = await uploadVideoAsset(prepared, percent => {
        uploadPercent.value = percent;
      });
      if (res.data?.assetId !== undefined) ids.push(res.data.assetId);
    }
    uploadAssetIds[field] = ids;
    uploadPreviews[field] = previews;
    ElMessage.success(`已上传 ${ids.length} 个素材`);
    void loadAssets();
  } catch (error) {
    // 失败时必须给出可定位的信息：带上文件名与网络层面的原因，
    // 避免出现「点了没反应」而无法排查的情况。
    const detail = (await extractErrorMessage(error)) ?? '素材上传失败';
    const name = files.map(f => f.name).join('、');
    ElMessage.error(`${detail}（文件：${name}）`);
    releaseUploadPreviews(field);
  } finally {
    uploading.value = false;
    uploadPercent.value = 0;
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
    const exec = executed.data;
    if (exec?.accepted === false) {
      // 任务已经在跑（多半是重复点击），不重复执行，接着轮询即可。
      ElMessage.info(`任务已在${taskStatusText(exec.status)}，将自动刷新结果`);
      startTaskPolling(taskId);
    } else if (exec?.status === 'RUNNING') {
      // 生成要 130 秒到 11 分钟，远超 Cloudflare 对源站响应的等待上限（约 100 秒），
      // 所以后端改为提交后台执行，这里轮询结果——同步等响应会被 Cloudflare 断开，
      // 用户只会看到「点了没反应」（实测 nginx 记 499）。
      ElMessage.success('已提交生成，完成后会自动显示，可以离开这个页面');
      startTaskPolling(taskId);
    } else if (exec?.status === 'FAILED') {
      ElMessage.warning(exec.errorMessage ?? '任务未完成，请查看任务详情');
      await loadTasks();
      await loadAssets();
    } else {
      // 兼容同步返回的旧形态（理论上不会再走到）。
      ElMessage.success('成片已生成，可在「我的任务」查看');
      await loadTasks();
      await loadAssets();
    }
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '任务提交失败');
    await loadTasks();
  } finally {
    submitting.value = false;
  }
}

/** 终态：到了这些状态就不会再变，轮询可以停。 */
const TERMINAL_STATUSES: VideoTaskStatus[] = ['SUCCEEDED', 'FAILED', 'CANCELED', 'TIMEOUT'];

/** 轮询间隔。生成动辄几分钟，3 秒足够又不至于把后端压垮。 */
const POLL_INTERVAL_MS = 3000;

/** GPU 工作节点与队列状态。取不到就不显示，绝不因为运维信息缺失而影响主流程。 */
const workers = ref<VideoWorkersVO | null>(null);

const busyWorkerCount = computed(() => workers.value?.workers.filter(item => item.busy).length ?? 0);
const unavailableWorkers = computed(() => workers.value?.workers.filter(item => item.unavailable) ?? []);

async function loadWorkerStatus() {
  try {
    const res = await getVideoWorkers();
    workers.value = res.data ?? null;
  } catch {
    workers.value = null;
  }
}

/** 正在轮询的任务 id。后台执行 + 轮询是生成结果的唯一回传通道。 */
const pollingTaskIds = new Set<string>();
let pollTimer: ReturnType<typeof setInterval> | undefined;

/** 开始轮询某个任务，直到它进入终态。 */
function startTaskPolling(taskId: number | string) {
  pollingTaskIds.add(String(taskId));
  if (pollTimer !== undefined) return;
  pollTimer = setInterval(() => void pollPendingTasks(), POLL_INTERVAL_MS);
}

function stopTaskPolling() {
  if (pollTimer !== undefined) {
    clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

/**
 * 拉取所有在途任务的状态。
 *
 * <p>任务在服务端后台线程里跑，HTTP 连接、页面刷新都不影响它——所以这里只需要
 * 定期问「好了没」，失败一次也不该打断整个轮询。</p>
 */
async function pollPendingTasks() {
  if (pollingTaskIds.size === 0) {
    stopTaskPolling();
    return;
  }
  // 顺手刷新 GPU 队列状态：这两件事的节奏完全一致（都在等同一批任务）。
  void loadWorkerStatus();
  // 先收集、循环结束后再删：避免在遍历 Set 的过程中改它。
  const finished: Array<{ id: string; status: VideoTaskStatus; message?: string }> = [];
  for (const id of pollingTaskIds) {
    try {
      const detail = await getVideoTask(id);
      const status = detail.data?.status;
      if (!status || !TERMINAL_STATUSES.includes(status)) continue;
      finished.push({ id, status, message: detail.data?.errorMessage });
    } catch {
      // 单次查询失败不影响后续轮询（网络抖动、页面切后台都可能发生）。
    }
  }

  for (const item of finished) {
    pollingTaskIds.delete(item.id);
    if (item.status === 'SUCCEEDED') {
      ElMessage.success('成片已生成，可在「我的任务」查看');
    } else {
      ElMessage.warning(item.message ?? `任务${taskStatusText(item.status)}，请查看任务详情`);
    }
  }

  await loadTasks();
  if (finished.length) {
    await loadAssets();
    if (finished.some((item) => item.status === 'SUCCEEDED')) void loadTaskCovers();
  }
  if (pollingTaskIds.size === 0) stopTaskPolling();
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

/** 预览弹窗状态。 */
const previewVisible = ref(false);
const previewTarget = ref<VideoTaskVO | null>(null);
const previewUrl = ref('');
const previewLoading = ref(false);
const previewError = ref('');
const previewMeta = ref<Array<{ label: string; value: string }>>([]);

/** 成片封面缓存：assetId -> blob URL，避免同一素材反复请求。 */
const coverUrls = ref<Record<string, string>>({});
const coverLoading = new Set<string>();

function coverFor(task: VideoTaskVO) {
  const id = task.outputAssetId;
  return id === null || id === undefined ? '' : coverUrls.value[String(id)] ?? '';
}

/**
 * 为成功任务加载成片封面。
 *
 * <p><b>为什么要走缩略图接口。</b>这里曾经直接拉成片本体当封面：9 个成片合计约 13 MB，
 * 而经 Cloudflare 实测吞吐只有 258 KB/s ~ 790 KB/s，等于把带宽占满，
 * 同一时刻发起的预览请求就会排队甚至超时——表现就是"预览时好时坏"。
 * 现在后端用 ffmpeg 抽一帧（约几十 KB），封面的代价从 13 MB 降到约 0.3 MB。</p>
 *
 * <p>按需加载且去重：同一素材只请求一次；失败静默（封面只是锦上添花，
 * 不该因为取图失败而打扰用户，模板会回退成图标）。</p>
 */
async function loadTaskCovers() {
  for (const task of filteredTasks.value) {
    if (task.status !== 'SUCCEEDED' || task.outputAssetId === null || task.outputAssetId === undefined) {
      continue;
    }
    const key = String(task.outputAssetId);
    if (coverUrls.value[key] || coverLoading.has(key)) continue;
    coverLoading.add(key);
    try {
      coverUrls.value = { ...coverUrls.value, [key]: await fetchVideoAssetThumbnailBlobUrl(task.outputAssetId) };
    } catch {
      // 忽略：封面失败不影响功能
    } finally {
      coverLoading.delete(key);
    }
  }
}

watch(filteredTasks, () => void loadTaskCovers());

/** 素材缩略图缓存：assetId -> blob URL。 */
const imageUrls = ref<Record<string, string>>({});
const imageLoading = new Set<string>();

function imageFor(asset: VideoAssetVO) {
  return imageUrls.value[String(asset.id)] ?? '';
}

/**
 * 为图片类素材加载缩略图。
 *
 * <p>优先走后端的缩略图接口（ffmpeg 生成、最长边 480px、带缓存），
 * 拿不到再退回原图，最后才让卡片显示图标——缩略图只是为了让列表快点出来，
 * 不该成为「能不能看到」的开关。视频/音频素材仍用图标，避免为一个小格子去拉整段视频。</p>
 */
async function loadAssetThumbnails() {
  for (const asset of assets.value) {
    if (asset.assetType !== 'IMAGE') continue;
    const key = String(asset.id);
    if (imageUrls.value[key] || imageLoading.has(key)) continue;
    imageLoading.add(key);
    try {
      let url: string;
      try {
        url = await fetchVideoAssetThumbnailBlobUrl(asset.id);
      } catch {
        url = await fetchVideoAssetBlobUrl(asset.id);
      }
      imageUrls.value = { ...imageUrls.value, [key]: url };
    } catch {
      // 忽略：缩略图失败不影响素材本身的使用
    } finally {
      imageLoading.delete(key);
    }
  }
}

watch(assets, () => void loadAssetThumbnails());

/** 组件卸载时释放所有 blob URL，避免内存泄漏。 */
onBeforeUnmount(() => {
  stopTaskPolling();
  pollingTaskIds.clear();
  releasePreviewUrl();
  Object.values(coverUrls.value).forEach(URL.revokeObjectURL);
  Object.values(imageUrls.value).forEach(URL.revokeObjectURL);
  Object.values(uploadPreviews).forEach(urls => urls?.forEach(URL.revokeObjectURL));
});

function releasePreviewUrl() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = '';
  }
}

function closePreview() {
  releasePreviewUrl();
  previewTarget.value = null;
  previewError.value = '';
  previewMeta.value = [];
}

/**
 * 打开成片预览。
 *
 * <p>只有 SUCCEEDED 且拿到 outputAssetId 才取内容；其余状态沿用原来的提示，
 * 不制造「有预览」的假象。</p>
 */
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

    previewTarget.value = task;
    previewMeta.value = [
      detail.outputWidth && detail.outputHeight
        ? { label: '分辨率', value: `${detail.outputWidth}×${detail.outputHeight}` }
        : null,
      detail.outputDurationMs
        ? { label: '时长', value: `${(detail.outputDurationMs / 1000).toFixed(3)} 秒` }
        : null,
      detail.outputFps ? { label: '帧率', value: `${detail.outputFps} fps` } : null,
      detail.truncationApplied ? { label: '截断', value: '已按目标时长精确截断' } : null
    ].filter(Boolean) as Array<{ label: string; value: string }>;

    previewVisible.value = true;
    previewError.value = '';
    releasePreviewUrl();

    if (detail.outputAssetId === null || detail.outputAssetId === undefined) {
      // 成功但没有成片素材：如实说明，而不是显示一个空播放器。
      previewError.value = '该任务没有可预览的成片素材';
      return;
    }
    previewLoading.value = true;
    try {
      previewUrl.value = await fetchVideoAssetBlobUrl(detail.outputAssetId);
    } catch (error) {
      // 网络抖动重试一次：经 Cloudflare 的链路偶发失败是真实存在的，
      // 但重试前必须确认上一次没有留下半个 blob（releasePreviewUrl 已经处理）。
      try {
        await new Promise((resolve) => setTimeout(resolve, 600));
        previewUrl.value = await fetchVideoAssetBlobUrl(detail.outputAssetId);
      } catch {
        previewError.value = (await extractErrorMessage(error)) ?? '成片加载失败';
      }
    } finally {
      previewLoading.value = false;
    }
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取任务详情失败');
  }
}

/** 下载当前预览的成片。 */
function downloadPreview() {
  if (!previewUrl.value) return;
  const name = previewTarget.value?.taskNo ? `${previewTarget.value.taskNo}.mp4` : '成片.mp4';
  const a = document.createElement('a');
  a.href = previewUrl.value;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
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

/** 终态且非成功的任务可重新执行（与后端 retry 的状态门禁一致）。 */
function retryable(task: VideoTaskVO) {
  return ['FAILED', 'TIMEOUT', 'CANCELED'].includes(task.status);
}

/** 正在重新执行的任务 id，用于按钮 loading 态（同一条只允许点一次）。 */
const retryingId = ref('');

/**
 * 重新执行：后端把终态退回 QUEUED 再认领入队，成功后就地开始轮询。
 *
 * <p>为什么需要它：进程重启会把 RUNNING 收敛为 FAILED 并提示「请重新执行该任务」，
 * 但此前没有这个入口——用户按提示做却点不动，只能手动重建一条。</p>
 */
async function retryTask(task: VideoTaskVO) {
  const id = String(task.id);
  retryingId.value = id;
  try {
    const res = await retryVideoTask(task.id);
    if (res.data?.accepted === false) {
      ElMessage.info('该任务已经在执行队列中');
    } else {
      ElMessage.success('已重新提交执行');
    }
    await loadTasks();
    startTaskPolling(String(task.id));
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '重新执行失败');
  } finally {
    retryingId.value = '';
  }
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
.gpu-status {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin: 10px 0 0;
  color: var(--t2);
  font-size: 11px;
}
.gpu-status-dot {
  width: 7px;
  height: 7px;
  background: var(--t3, #8a8f98);
  border-radius: 50%;
}
.gpu-status-dot.busy {
  background: #3ddc97;
  box-shadow: 0 0 0 3px rgb(61 220 151 / 18%);
}
.gpu-status-warn {
  color: #e6a23c;
}
.upload-previews {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  justify-content: center;
  width: 100%;
}
.upload-previews img {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border: 1px solid var(--line2);
  border-radius: 6px;
}
.upload-previews-badge {
  color: var(--t2);
  font-size: 11px;
}
.task-worker {
  padding: 1px 5px;
  margin-left: 6px;
  color: var(--t2);
  font-size: 10px;
  border: 1px solid var(--line2);
  border-radius: 4px;
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
  border-color: rgba(186, 197, 209, 0.28);
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
/* 素材真实缩略图：填满预览位并保持比例，不拉伸变形 */
.asset-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 6px;
}
/* 任务卡成片封面 */
.task-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: inherit;
}
/* 成片预览弹窗 */
.preview-body {
  display: grid;
  gap: 14px;
}
.preview-video {
  width: 100%;
  max-height: 62vh;
  background: #000;
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
  color: var(--t2);
  font-size: 11px;
}
.preview-meta dd {
  margin: 0;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
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
  border: 1px solid rgba(186, 197, 209, 0.2);
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
/* 画面比例就两个按钮，与下面的清晰度档位留一点间距，视觉上成一组两层 */
.choice-grid.ratio-choices {
  margin-bottom: 14px;
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
  border: 1px solid rgba(186, 197, 209, 0.24);
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
