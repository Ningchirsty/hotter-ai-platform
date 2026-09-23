<template>
  <div class="studio">
    <div v-if="showGuide" class="guide-bar">
      <span>
        R0 接线版：项目复用「内容生产协同」的电商详情页任务，出图复用图像创作内核（已发布工作流）。
        本轮只做「上传产品图 → 出一张 HERO 主图 → 可预览 → 全程可追溯」这条闭环，视觉基因/分镜/视觉门/排版在 R1–R3 交付。
      </span>
      <button type="button" title="关闭提示" @click="dismissGuide">✕</button>
    </div>

    <div class="workbench">
      <!-- 左：项目列表 -->
      <aside class="panel project-panel">
        <header class="panel-head">
          <h3>视觉项目</h3>
          <button type="button" class="ghost-btn" :disabled="loadingProjects" @click="loadProjects">刷新</button>
        </header>

        <div class="filter-row">
          <el-input
            v-model="queryTaskName"
            placeholder="按项目名称搜索"
            clearable
            @keyup.enter="loadProjects"
            @clear="loadProjects"
          />
          <el-button type="primary" plain @click="loadProjects">查询</el-button>
        </div>

        <button type="button" class="create-btn" @click="openCreateDialog">＋ 新建视觉项目</button>

        <div v-loading="loadingProjects" class="project-list">
          <p v-if="!projects.length && !loadingProjects" class="empty">
            还没有电商详情页项目。新建一个，或在「业务应用 → 内容生产协同 → 内容任务」里把交付类型选为电商详情图。
          </p>
          <button
            v-for="project in projects"
            :key="String(project.taskId)"
            type="button"
            class="project-item"
            :class="{ active: String(project.taskId) === String(currentProjectId) }"
            @click="selectProject(project)"
          >
            <span class="project-name">{{ project.taskName || '未命名项目' }}</span>
            <span class="project-meta">
              <span class="stage-tag" :class="'is-' + stageType(project.visualStage)">
                {{ stageLabel(project.visualStage) }}
              </span>
              <span class="task-no">{{ project.taskNo }}</span>
            </span>
            <span v-if="project.productName" class="project-sub">{{ project.productName }}</span>
            <span v-if="project.blockingCardCount" class="project-warn">
              有 {{ project.blockingCardCount }} 张阻断卡待处理
            </span>
          </button>
        </div>
      </aside>

      <!-- 右：项目工作台 -->
      <section class="panel detail-panel">
        <div v-if="!currentProject" class="placeholder">
          <p>从左侧选择一个视觉项目开始。</p>
          <p class="hint">选好项目后：上传产品参考图 → 描述你想要的画面 → 生成 HERO 主图候选。</p>
        </div>

        <template v-else>
          <header class="detail-head">
            <div class="detail-title">
              <h2>{{ currentProject.taskName }}</h2>
              <div class="detail-tags">
                <span class="stage-tag" :class="'is-' + stageType(currentProject.visualStage)">
                  {{ stageLabel(currentProject.visualStage) }}
                </span>
                <span class="muted">{{ currentProject.taskNo }}</span>
                <span class="muted">内容协同状态：{{ currentProject.status }}</span>
                <span v-if="currentProject.productName" class="muted">
                  产品：{{ currentProject.productName }}
                </span>
              </div>
            </div>
            <div class="detail-actions">
              <el-button size="small" @click="openDna">视觉基因</el-button>
              <el-button size="small" @click="loadDetail">刷新</el-button>
              <el-button size="small" @click="timelineVisible = true">操作日志</el-button>
            </div>
          </header>

          <div class="detail-body">
            <!-- 参考图 -->
            <section class="block">
              <div class="block-head">
                <h4>1. 产品参考图</h4>
                <span class="muted">{{ imageFiles.length }} 张</span>
              </div>
              <div class="ref-row">
                <div
                  v-for="file in imageFiles"
                  :key="String(file.fileId)"
                  class="ref-card"
                  :class="{ active: String(file.fileId) === String(selectedFileId) }"
                  @click="selectedFileId = file.fileId"
                >
                  <img v-if="urlOf('file-' + file.fileId)" :src="urlOf('file-' + file.fileId)" :alt="file.fileName" />
                  <span v-else class="ref-loading">读取中…</span>
                  <span class="ref-name">{{ file.fileName }}</span>
                  <span v-if="String(file.fileId) === String(selectedFileId)" class="ref-badge">当前参考图</span>
                </div>
                <el-upload
                  class="ref-upload"
                  :show-file-list="false"
                  accept="image/png,image/jpeg,image/webp"
                  :http-request="doUpload"
                >
                  <div class="upload-slot">
                    <span class="plus">＋</span>
                    <span>上传产品图</span>
                    <span class="hint">PNG/JPG/WEBP，≤20MB</span>
                  </div>
                </el-upload>
              </div>
            </section>

            <!-- 出图 -->
            <section class="block">
              <div class="block-head">
                <h4>2. 生成 HERO 主图</h4>
                <span class="muted">R0 每次出 1 张候选；重试=新增一次候选</span>
              </div>
              <div class="form-row">
                <label>出图工作流</label>
                <el-select v-model="heroForm.workflowCode" placeholder="使用默认已发布工作流" style="width: 320px">
                  <el-option
                    v-for="wf in workflows"
                    :key="wf.workflowCode"
                    :label="`${wf.workflowCode}（${wf.capabilityCode} · ${wf.published ? '已发布' : wf.status}）`"
                    :value="wf.workflowCode"
                  />
                </el-select>
              </div>
              <div class="form-row">
                <label>画面描述</label>
                <el-input
                  v-model="heroForm.prompt"
                  type="textarea"
                  :rows="3"
                  maxlength="1000"
                  show-word-limit
                  placeholder="留空则用默认主图提示词（产品居中、纯净背景、影棚光、保留原有结构与配色）"
                />
              </div>
              <p v-if="promptFromDna" class="dna-hint">
                已按<b>视觉基因</b>预填提示词（用到的维度：{{ promptApplied.join('、') }}）。可以改；改了就以你写的为准。
              </p>
              <p v-else-if="dnaLocked" class="dna-hint">
                检测到已锁定的视觉基因，但派生提示词尚未载入——点
                <el-button link type="primary" size="small" @click="prefillPromptFromDna">这里</el-button>
                载入。
              </p>
              <p v-else class="dna-hint muted">
                这个项目还没有锁定视觉基因，提示词按默认模板生成。建议先到
                <el-button link type="primary" size="small" @click="openDna">视觉基因</el-button>
                定义配色与光线。
              </p>
              <div class="form-row">
                <label>负向提示</label>
                <el-input
                  v-model="heroForm.negativePrompt"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  placeholder="留空则用默认（文字、水印、产品变形、结构缺失…）"
                />
              </div>
              <div class="submit-row">
                <el-button
                  type="primary"
                  :loading="submitting"
                  :disabled="!imageFiles.length"
                  @click="doGenerate"
                >
                  {{ submitting ? '提交中…' : '生成 HERO 主图候选' }}
                </el-button>
                <span v-if="!imageFiles.length" class="hint">请先上传产品参考图</span>
              </div>
            </section>

            <!-- 候选 -->
            <section class="block">
              <div class="block-head">
                <h4>3. 出图候选</h4>
                <span class="muted">
                  {{ generations.length }} 条
                  <template v-if="polling">· 状态跟踪中…</template>
                </span>
              </div>
              <p v-if="!generations.length" class="empty">还没有候选。填好描述后点上面的生成按钮。</p>
              <div v-else class="candidate-grid">
                <div v-for="gen in generations" :key="String(gen.id)" class="candidate-card">
                  <div class="candidate-cover" @click="gen.previewable && openPreview(gen)">
                    <img
                      v-if="urlOf('gen-' + gen.id)"
                      :src="urlOf('gen-' + gen.id)"
                      :alt="`候选 ${gen.candidateNo}`"
                    />
                    <span v-else class="cover-placeholder">
                      {{ gen.status === 'RUNNING' || gen.status === 'QUEUED' ? '出图中…' : '暂无产出' }}
                    </span>
                  </div>
                  <div class="candidate-meta">
                    <span class="gen-status" :class="'is-' + genStatusType(gen.status)">
                      #{{ gen.candidateNo }} · {{ gen.statusDesc || genStatusLabel(gen.status) }}
                    </span>
                    <span v-if="gen.outputWidth" class="muted">{{ gen.outputWidth }}×{{ gen.outputHeight }}</span>
                    <span v-if="gen.durationMs" class="muted">{{ (gen.durationMs / 1000).toFixed(1) }}s</span>
                  </div>
                  <p v-if="gen.errorMessage" class="gen-error" :title="gen.errorMessage">{{ gen.errorMessage }}</p>
                  <div class="candidate-actions">
                    <el-button v-if="gen.previewable" size="small" text type="primary" @click="openPreview(gen)">
                      预览
                    </el-button>
                    <el-button
                      v-if="gen.retryable"
                      size="small"
                      text
                      type="warning"
                      :loading="retryingId === String(gen.id)"
                      @click="doRetry(gen)"
                    >
                      重试
                    </el-button>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </template>
      </section>
    </div>

    <!-- 新建项目 -->
    <el-dialog v-model="createVisible" title="新建视觉项目" width="520px">
      <el-form label-width="90px">
        <el-form-item label="项目名称">
          <el-input v-model="createForm.taskName" maxlength="255" placeholder="如：趣往单枝花-详情页视觉" />
        </el-form-item>
        <el-form-item label="产品">
          <el-select
            v-model="createForm.productId"
            placeholder="可不选；选中后会带出产品名（影响默认提示词）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="product in products"
              :key="String(product.productId)"
              :label="product.productName + (product.skuCode ? ' · ' + product.skuCode : '')"
              :value="product.productId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" maxlength="500" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 候选预览 -->
    <el-dialog v-model="previewVisible" title="候选预览" width="720px" @closed="closePreview">
      <div class="preview-wrap">
        <img v-if="previewUrl" :src="previewUrl" alt="候选原图" />
        <p v-else class="empty">加载中…</p>
      </div>
    </el-dialog>

    <!-- 操作日志（全链路可追溯） -->
    <el-dialog v-model="timelineVisible" title="操作日志（阶段事件）" width="760px">
      <el-timeline v-if="timeline.length">
        <el-timeline-item
          v-for="event in timeline"
          :key="String(event.id)"
          :timestamp="formatTime(event.createTime)"
          placement="top"
        >
          <div class="tl-title">
            <strong>{{ event.action || event.eventType }}</strong>
            <span v-if="event.fromStage !== event.toStage" class="tl-stage">
              {{ stageLabel(event.fromStage) }} → {{ stageLabel(event.toStage) }}
            </span>
          </div>
          <div class="muted">{{ event.actorName || '系统' }}</div>
          <div v-if="event.detailJson" class="tl-detail">{{ event.detailJson }}</div>
        </el-timeline-item>
      </el-timeline>
      <p v-else class="empty">暂无事件。</p>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { UploadRequestOptions } from 'element-plus';
import { productOptions } from '@/api/content/product';
import type { CpProductVO } from '@/api/content/product/types';
import type { CpTaskFileVO } from '@/api/content/task/types';
import {
  addCreativeProject,
  fetchCreativeFileBlobUrl,
  fetchGenerationPreviewBlobUrl,
  fetchGenerationThumbnailBlobUrl,
  getCreativeProject,
  getDna,
  getDnaPrompt,
  listCreativeFiles,
  listCreativeProject,
  listCreativeTimeline,
  listCreativeWorkflows,
  listGenerations,
  retryGeneration,
  submitHero,
  uploadCreativeReference
} from '@/api/creative';
import type {
  CreativeProjectVO,
  CreativeWorkflowVO,
  DpGenerationVO,
  DpStageEventVO
} from '@/api/creative/types';
import {
  CREATIVE_STAGE_LABELS,
  CREATIVE_STAGE_TYPES,
  GENERATION_STATUS_LABELS,
  GENERATION_STATUS_TYPES
} from '@/api/creative/types';

const GUIDE_KEY = 'hotter.creative.guide.dismissed';

const showGuide = ref(localStorage.getItem(GUIDE_KEY) !== '1');
const projects = ref<CreativeProjectVO[]>([]);
const loadingProjects = ref(false);
const queryTaskName = ref('');
const currentProjectId = ref<string | number>('');
const currentProject = ref<CreativeProjectVO | null>(null);
const files = ref<CpTaskFileVO[]>([]);
const generations = ref<DpGenerationVO[]>([]);
const timeline = ref<DpStageEventVO[]>([]);
const workflows = ref<CreativeWorkflowVO[]>([]);
const products = ref<CpProductVO[]>([]);
const selectedFileId = ref<string | number>('');

const submitting = ref(false);
const creating = ref(false);
const retryingId = ref('');
const polling = ref(false);
const createVisible = ref(false);
const previewVisible = ref(false);
const timelineVisible = ref(false);
const previewUrl = ref('');

const createForm = reactive({ taskName: '', productId: '' as string | number, remark: '' });
const heroForm = reactive({ workflowCode: '', prompt: '', negativePrompt: '' });

/** 提示词是否来自视觉基因（页面如实说明，不让人以为是自己写的） */
const promptFromDna = ref(false);
const promptApplied = ref<string[]>([]);
const dnaLocked = ref(false);

/**
 * blob URL 台账：key → URL。
 *
 * 必须是响应式（ref + 展开赋值），不能用普通 Map——普通 Map 的增删不会触发重渲染，
 * 表现为「接口全 200、页面上的 <img> 永远不出现」（这个坑实际踩过一次）。
 * 切换/卸载时统一 revoke，避免内存泄漏。
 */
const objectUrls = ref<Record<string, string>>({});
let pollTimer: number | undefined;
let pollTicks = 0;
const POLL_INTERVAL_MS = 5000;
const POLL_MAX_TICKS = 120;

const imageFiles = computed(() => files.value.filter((f) => (f.fileKind || '').toUpperCase() === 'IMAGE'));

function urlOf(key: string): string {
  return objectUrls.value[key] || '';
}

function setUrl(key: string, url: string) {
  const old = objectUrls.value[key];
  if (old) URL.revokeObjectURL(old);
  objectUrls.value = { ...objectUrls.value, [key]: url };
}

function releaseUrl(key: string) {
  const old = objectUrls.value[key];
  if (!old) return;
  URL.revokeObjectURL(old);
  const next = { ...objectUrls.value };
  delete next[key];
  objectUrls.value = next;
}

function stageLabel(stage?: string): string {
  if (!stage) return '未开始';
  return CREATIVE_STAGE_LABELS[stage] || stage;
}

function stageType(stage?: string): string {
  return (stage && CREATIVE_STAGE_TYPES[stage]) || 'info';
}

function genStatusLabel(status?: string): string {
  return (status && GENERATION_STATUS_LABELS[status]) || status || '';
}

function genStatusType(status?: string): string {
  return (status && GENERATION_STATUS_TYPES[status]) || 'info';
}

function formatTime(value?: string): string {
  if (!value) return '';
  return value.replace('T', ' ').slice(0, 19);
}

function dismissGuide() {
  showGuide.value = false;
  localStorage.setItem(GUIDE_KEY, '1');
}

async function loadProjects() {
  loadingProjects.value = true;
  try {
    const res = await listCreativeProject({
      pageNum: 1,
      pageSize: 50,
      queryTaskName: queryTaskName.value || undefined
    });
    projects.value = res.data?.rows || [];
    if (!projects.value.length) {
      currentProjectId.value = '';
      currentProject.value = null;
      return;
    }
    const stillThere = projects.value.some((p) => String(p.taskId) === String(currentProjectId.value));
    if (!stillThere) {
      await selectProject(projects.value[0]);
    }
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载视觉项目失败');
  } finally {
    loadingProjects.value = false;
  }
}

async function selectProject(project: CreativeProjectVO) {
  currentProjectId.value = project.taskId ?? '';
  currentProject.value = project;
  selectedFileId.value = '';
  stopPolling();
  await loadDetail();
}

async function loadDetail() {
  if (!currentProjectId.value) return;
  try {
    const [detail, fileRes, genRes, timelineRes] = await Promise.all([
      getCreativeProject(currentProjectId.value),
      listCreativeFiles(currentProjectId.value),
      listGenerations(currentProjectId.value),
      listCreativeTimeline(currentProjectId.value)
    ]);
    currentProject.value = detail.data;
    files.value = fileRes.data || [];
    generations.value = genRes.data || [];
    timeline.value = timelineRes.data || [];
    if (!selectedFileId.value && imageFiles.value.length) {
      selectedFileId.value = imageFiles.value[0].fileId ?? '';
    }
    void loadFileThumbs();
    void loadGenerationThumbs();
    syncPolling();
    void loadDnaState();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载项目详情失败');
  }
}

/** 看这个项目有没有锁定基因，并决定是否预填提示词（只在用户没写过提示词时预填） */
async function loadDnaState() {
  promptFromDna.value = false;
  try {
    const dnaRes = await getDna(currentProjectId.value);
    dnaLocked.value = dnaRes.data?.locked === true;
    if (dnaLocked.value && !heroForm.prompt.trim()) {
      await prefillPromptFromDna();
    }
  } catch {
    dnaLocked.value = false;
  }
}

/** 用视觉基因派生提示词预填出图框 */
async function prefillPromptFromDna() {
  try {
    const res = await getDnaPrompt(currentProjectId.value, 'HERO 主图');
    heroForm.prompt = res.data?.prompt || '';
    heroForm.negativePrompt = res.data?.negativePrompt || '';
    promptApplied.value = res.data?.applied || [];
    promptFromDna.value = true;
    ElMessage.success('已按视觉基因预填提示词');
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '派生提示词失败');
  }
}

/** 跳到视觉基因页（带上当前项目） */
function openDna() {
  if (!currentProjectId.value) return;
  window.open(`/creative/dna?taskId=${currentProjectId.value}`, '_self');
}

async function loadFileThumbs() {
  for (const file of imageFiles.value) {
    const key = 'file-' + file.fileId;
    if (objectUrls.value[key]) continue;
    try {
      const url = await fetchCreativeFileBlobUrl(currentProjectId.value, file.fileId as string | number);
      setUrl(key, url);
    } catch {
      /* 单张读失败不影响其它 */
    }
  }
}

async function loadGenerationThumbs() {
  for (const gen of generations.value) {
    const key = 'gen-' + gen.id;
    if (!gen.previewable) {
      releaseUrl(key);
      continue;
    }
    if (objectUrls.value[key]) continue;
    try {
      const url = await fetchGenerationThumbnailBlobUrl(gen.id);
      setUrl(key, url);
    } catch {
      /* 缩略图失败就留空，点击预览仍可取原图 */
    }
  }
}

async function doUpload(options: UploadRequestOptions) {
  if (!currentProjectId.value) return;
  try {
    await uploadCreativeReference(currentProjectId.value, options.file as File);
    ElMessage.success('参考图已上传');
    await loadDetail();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '上传失败');
  }
}

async function doGenerate() {
  if (!currentProjectId.value) return;
  submitting.value = true;
  try {
    await submitHero(currentProjectId.value, {
      fileId: selectedFileId.value || undefined,
      prompt: heroForm.prompt || undefined,
      negativePrompt: heroForm.negativePrompt || undefined,
      workflowCode: heroForm.workflowCode || undefined
    });
    ElMessage.success('已提交出图，正在跟踪状态');
    await loadDetail();
    startPolling();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '提交出图失败');
  } finally {
    submitting.value = false;
  }
}

async function doRetry(gen: DpGenerationVO) {
  retryingId.value = String(gen.id);
  try {
    await retryGeneration(gen.id);
    ElMessage.success('已新建一次候选');
    await loadDetail();
    startPolling();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '重试失败');
  } finally {
    retryingId.value = '';
  }
}

async function openPreview(gen: DpGenerationVO) {
  previewVisible.value = true;
  try {
    const url = await fetchGenerationPreviewBlobUrl(gen.id);
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = url;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取产出图失败');
  }
}

function closePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = '';
}

function openCreateDialog() {
  createForm.taskName = '';
  createForm.productId = '';
  createForm.remark = '';
  createVisible.value = true;
  if (!products.value.length) {
    void loadProducts();
  }
}

async function loadProducts() {
  try {
    const res = await productOptions();
    products.value = res.data || [];
  } catch {
    /* 产品下拉失败不阻断建项目（产品是可选项） */
  }
}

async function doCreate() {
  if (!createForm.taskName.trim()) {
    ElMessage.warning('请填写项目名称');
    return;
  }
  creating.value = true;
  try {
    const res = await addCreativeProject({
      taskName: createForm.taskName.trim(),
      productId: createForm.productId || undefined,
      remark: createForm.remark || undefined
    });
    ElMessage.success('项目已创建');
    createVisible.value = false;
    await loadProjects();
    const created = projects.value.find((p) => String(p.taskId) === String(res.data));
    if (created) await selectProject(created);
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '创建项目失败');
  } finally {
    creating.value = false;
  }
}

/** 有候选在跑就轮询，跑完自动停（不做无脑定时器） */
function syncPolling() {
  const hasRunning = generations.value.some((g) => g.status === 'QUEUED' || g.status === 'RUNNING');
  if (hasRunning) {
    startPolling();
  } else {
    stopPolling();
  }
}

function startPolling() {
  if (pollTimer !== undefined) return;
  pollTicks = 0;
  polling.value = true;
  pollTimer = window.setInterval(async () => {
    pollTicks += 1;
    if (pollTicks > POLL_MAX_TICKS) {
      stopPolling();
      return;
    }
    try {
      const res = await listGenerations(currentProjectId.value);
      generations.value = res.data || [];
      void loadGenerationThumbs();
      const hasRunning = generations.value.some((g) => g.status === 'QUEUED' || g.status === 'RUNNING');
      if (!hasRunning) {
        stopPolling();
        void loadDetail();
      }
    } catch {
      stopPolling();
    }
  }, POLL_INTERVAL_MS);
}

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer);
    pollTimer = undefined;
  }
  polling.value = false;
}

/** 若依的错误体可能是 blob/字符串，统一取出可读信息 */
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
  await loadProjects();
  try {
    const res = await listCreativeWorkflows();
    workflows.value = res.data || [];
    const published = workflows.value.find((w) => w.published && w.capabilityCode === 'I2I');
    if (published) heroForm.workflowCode = published.workflowCode;
  } catch {
    /* 工作流列表失败时，后端仍会用默认已发布契约 */
  }
});

onBeforeUnmount(() => {
  stopPolling();
  Object.values(objectUrls.value).forEach((url) => URL.revokeObjectURL(url));
  objectUrls.value = {};
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
});
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
  line-height: 1.7;
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

.workbench {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
}
.panel-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.ghost-btn {
  padding: 4px 10px;
  color: var(--t2);
  font-size: 12px;
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--line2);
  border-radius: 4px;
}
.ghost-btn:hover {
  color: var(--t1);
}

.filter-row {
  display: flex;
  gap: 8px;
  padding: 0 16px 10px;
}

.create-btn {
  width: calc(100% - 32px);
  min-height: 36px;
  margin: 0 16px 12px;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border: 0;
  border-radius: 6px;
}
.create-btn:hover {
  filter: brightness(1.08);
}

.project-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 340px);
  padding: 0 12px 14px;
  overflow-y: auto;
}

.project-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 11px 12px;
  text-align: left;
  cursor: pointer;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.project-item:hover {
  border-color: var(--line2);
}
.project-item.active {
  border-color: #7c3aed;
  box-shadow: inset 0 0 0 1px rgba(124, 58, 237, 0.4);
}
.project-name {
  font-size: 14px;
  font-weight: 600;
}
.project-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--t3);
}
.project-sub {
  font-size: 12px;
  color: var(--t2);
}
.project-warn {
  font-size: 12px;
  color: #fbbf24;
}
.task-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.stage-tag {
  padding: 2px 7px;
  font-size: 12px;
  border-radius: 10px;
  border: 1px solid transparent;
}
.stage-tag.is-primary {
  color: #c7d2fe;
  background: rgba(99, 102, 241, 0.16);
  border-color: rgba(99, 102, 241, 0.4);
}
.stage-tag.is-warning {
  color: #fde68a;
  background: rgba(245, 158, 11, 0.16);
  border-color: rgba(245, 158, 11, 0.4);
}
.stage-tag.is-success {
  color: #a7f3d0;
  background: rgba(16, 185, 129, 0.16);
  border-color: rgba(16, 185, 129, 0.4);
}
.stage-tag.is-info {
  color: var(--t2);
  background: rgba(148, 163, 184, 0.14);
  border-color: var(--line2);
}

.detail-panel {
  min-height: 420px;
  padding-bottom: 8px;
}

.placeholder {
  padding: 60px 24px;
  color: var(--t2);
  text-align: center;
}

.detail-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--line);
}
.detail-title h2 {
  margin: 0 0 8px;
  font-size: 17px;
}
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  font-size: 12px;
}
.detail-actions {
  display: flex;
  gap: 8px;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 16px;
}

.block {
  padding: 14px;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.block-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.block-head h4 {
  margin: 0;
  font-size: 14px;
}

.ref-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.ref-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 132px;
  padding: 6px;
  cursor: pointer;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.ref-card.active {
  border-color: #7c3aed;
}
.ref-card img {
  width: 100%;
  height: 96px;
  object-fit: contain;
  background: #05070a;
  border-radius: 4px;
}
.ref-loading {
  display: grid;
  place-items: center;
  height: 96px;
  font-size: 12px;
  color: var(--t3);
}
.ref-name {
  overflow: hidden;
  font-size: 11px;
  color: var(--t2);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ref-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 1px 6px;
  font-size: 10px;
  color: #fff;
  background: rgba(124, 58, 237, 0.9);
  border-radius: 8px;
}

.upload-slot {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  width: 132px;
  height: 138px;
  color: var(--t2);
  font-size: 12px;
  background: var(--sunken);
  border: 1px dashed var(--line2);
  border-radius: 6px;
}
.upload-slot .plus {
  font-size: 20px;
}
.upload-slot:hover {
  color: var(--t1);
  border-color: #7c3aed;
}

.form-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}
.form-row > label {
  flex: 0 0 76px;
  padding-top: 8px;
  font-size: 13px;
  color: var(--t2);
}
.form-row > :deep(.el-textarea),
.form-row > :deep(.el-select) {
  flex: 1;
}

.submit-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.candidate-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}
.candidate-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.candidate-cover {
  display: grid;
  place-items: center;
  height: 170px;
  overflow: hidden;
  cursor: pointer;
  background: #05070a;
  border-radius: 4px;
}
.candidate-cover img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.cover-placeholder {
  font-size: 12px;
  color: var(--t3);
}
.candidate-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}
.gen-status {
  padding: 1px 7px;
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
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin: 0;
  overflow: hidden;
  font-size: 12px;
  color: #fca5a5;
}
.candidate-actions {
  display: flex;
  gap: 4px;
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

.tl-title {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 13px;
}
.tl-stage {
  font-size: 12px;
  color: var(--t2);
}
.tl-detail {
  margin-top: 4px;
  font-size: 12px;
  color: var(--t3);
  word-break: break-all;
}

.muted {
  color: var(--t2);
}
.hint {
  font-size: 12px;
  color: var(--t3);
}

.dna-hint {
  margin: 0 0 10px;
  padding-left: 88px;
  font-size: 12px;
  line-height: 1.8;
  color: #a5b4fc;
}
.dna-hint.muted {
  color: var(--t3);
}
.empty {
  padding: 14px 0;
  font-size: 13px;
  line-height: 1.8;
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
.studio :deep(.el-input__count) {
  background: transparent;
}
</style>
