<template>
  <div class="studio">
    <!-- R19/R41：本页由工作台装配。R41 把"逐屏候选管理"拆成装配组件 GenerationBoard
         （配置里 GENERATION 步用 pages 指明它只属于本页）；未选项目时的跨项目总览走 #main。 -->
    <CreativeWorkspace
      :task-id="taskId"
      :refresh-token="flowToken"
      :loading="loading"
      @refresh="reloadCurrent"
    >
      <template #page-head>
        <header class="prod-head">
          <div>
            <h2>AI 生产中心</h2>
            <p class="muted">
              选定一个视觉项目后，这里按<b>屏</b>看该项目自己的候选：预览、质检、选定、重出这一屏都在本页完成。
              不选项目时，下面是跨项目的候选总览——未结束的候选在刷新时会向出图内核要一次真实状态，
              因此显示的状态就是内核里的状态；重试＝新增一次候选（保留历史，不覆盖）。
            </p>
          </div>
          <div class="head-actions">
            <el-select
              v-model="taskId"
              placeholder="选择视觉项目"
              filterable
              clearable
              style="width: 260px"
              @change="onProjectChange"
            >
              <el-option
                v-for="project in projects"
                :key="String(project.taskId)"
                :label="project.taskName || String(project.taskId)"
                :value="String(project.taskId)"
              />
            </el-select>
            <el-select v-if="!taskId" v-model="status" placeholder="全部状态" clearable style="width: 150px" @change="load">
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-button type="primary" plain :loading="loading" @click="reloadCurrent">刷新</el-button>
          </div>
        </header>
      </template>

      <template #GenerationBoard>
        <GenerationBoard
          :rows="rows"
          :storyboard="storyboard"
          :screen-map="screenMap"
          :loading="loading"
          :refreshing="refreshing"
          :busy="busy"
          :thumb-url="thumbUrl"
          :url-of="urlOf"
          :product-image="productImage"
          :compare-gen="compareGen"
          :compare-screen-label="compareScreenLabel"
          @refresh="doRefreshProduction"
          @preview="openPreview"
          @select="doSelectCandidate"
          @qa="doRunQa"
          @regenerate="doRegenerateScreen"
          @compare="openCompare"
          @close-compare="closeCompare"
          @need-thumb="loadThumb"
        />
      </template>

      <template #main>
        <!-- 未选项目：跨项目候选总览（保持原样） -->
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
              {{ row.statusDesc || statusLabel(row.status) }}
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
      </template>
    </CreativeWorkspace>

    <el-dialog v-model="previewVisible" title="候选预览" width="720px" @closed="closePreview">
      <div class="preview-wrap">
        <img v-if="previewUrl" :src="previewUrl" alt="候选原图" />
        <p v-else class="muted">加载中…</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  fetchGenerationPreviewBlobUrl,
  fetchGenerationThumbnailBlobUrl,
  fetchProductImageBlobUrl,
  getProjectProductImage,
  getStoryboard,
  listCreativeProject,
  listGenerations,
  listProductions,
  refreshProduction,
  regenerateScreen,
  retryGeneration,
  runCandidateQa,
  selectCandidate
} from '@/api/creative';
import type {
  CreativeProjectVO,
  DpGenerationVO,
  DpStoryboardScreenVO,
  DpStoryboardVO,
  ProjectProductImageVO
} from '@/api/creative/types';
import {
  GENERATION_STATUS_LABELS
} from '@/api/creative/types';
import {
  asGen,
  formatTime,
  screenLabel,
  screenTypeDesc,
  statusLabel,
  statusType
} from './generationText';
import CreativeWorkspace from '../components/CreativeWorkspace.vue';
import GenerationBoard from './components/GenerationBoard.vue';

/** 并排对比的目标：候选 + 它所属屏的类型描述 */
interface CompareTarget {
  gen: DpGenerationVO;
  typeDesc: string;
}

const statusOptions = Object.entries(GENERATION_STATUS_LABELS).map(([value, label]) => ({ value, label }));

// 项目选择
const projects = ref<CreativeProjectVO[]>([]);
const taskId = ref('');

// 跨项目总览（未选项目时）
const rows = ref<DpGenerationVO[]>([]);
const loading = ref(false);
const status = ref('');
const pageNum = ref(1);
const pageSize = ref(20);
const total = ref(0);
const retryingId = ref('');
const previewVisible = ref(false);
const previewUrl = ref('');

// 项目内视图
const storyboard = ref<DpStoryboardVO | null>(null);
const productImage = ref<ProjectProductImageVO | null>(null);
/** 正在跑的项目内动作（按钮 loading 与互斥用），同时只有一个 */
const busy = ref('');
const refreshing = ref(false);
/** 流程指引线刷新令牌：能改变阶段/候选的动作成功后 +1 */
const flowToken = ref(0);

const compareScreenKey = ref('');
const compareGen = ref<CompareTarget | null>(null);

/**
 * blob URL 台账：key → URL。
 *
 * 必须是响应式（ref + 展开赋值），普通 Map 的增删不会触发重渲染
 * （表现为「接口全 200、页面上的 <img> 永远不出现」）。切换项目/卸载时统一 revoke。
 */
const objectUrls = ref<Record<string, string>>({});

const screenMap = computed<Record<string, DpStoryboardScreenVO>>(() => {
  const map: Record<string, DpStoryboardScreenVO> = {};
  for (const screen of storyboard.value?.screens || []) {
    map[String(screen.id)] = screen;
  }
  return map;
});

const compareScreenLabel = computed(() => {
  const screen = screenMap.value[compareScreenKey.value];
  if (!screen) return '';
  return screen.screenNo || String(screen.id);
});

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

function releaseAll() {
  Object.keys(objectUrls.value).forEach((key) => releaseUrl(key));
}

function thumbUrl(row: DpGenerationVO): string {
  return urlOf('gen-' + row.id);
}

async function loadProjects() {
  // 深链优先：`?taskId=` 先落地（原先只在列表里找得到才采用；找不到就停在"未选项目"总览，
  // 直接开链接看起来就像这一页什么都没生效）
  const queryTaskId = new URLSearchParams(location.search).get('taskId') || '';
  if (queryTaskId) {
    taskId.value = queryTaskId;
  }
  try {
    const res = await listCreativeProject({ pageNum: 1, pageSize: 50 });
    projects.value = res.data?.rows || [];
  } catch (error) {
    // 列表失败不影响深链：提示照旧，taskId 已经落地
    ElMessage.error((await extractErrorMessage(error)) ?? '加载视觉项目失败');
  }
}

/** 未选项目：跨项目分页总览 */
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
    void loadOverviewThumbs();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载生产列表失败');
  } finally {
    loading.value = false;
  }
}

/** 已选项目：该项目自己的候选 + 分镜（屏号/类型）+ 产品图 */
async function loadProject() {
  if (!taskId.value) return;
  loading.value = true;
  try {
    const [genRes, sbRes, productRes] = await Promise.all([
      listGenerations(taskId.value),
      getStoryboard(taskId.value).catch(() => null),
      getProjectProductImage(taskId.value).catch(() => null)
    ]);
    rows.value = genRes.data || [];
    storyboard.value = sbRes?.data ?? null;
    productImage.value = productRes?.data ?? null;
    // R45：缩略图不再一次性全取——由 GenerationBoard 在候选行进入视口时发 need-thumb
    void loadProductImageUrl();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载项目候选失败');
  } finally {
    loading.value = false;
  }
}

async function onProjectChange() {
  closeCompare();
  releaseAll();
  rows.value = [];
  total.value = 0;
  pageNum.value = 1;
  storyboard.value = null;
  productImage.value = null;
  if (taskId.value) {
    await loadProject();
  } else {
    await load();
  }
}

async function reloadCurrent() {
  if (taskId.value) {
    await loadProject();
  } else {
    await load();
  }
}

/**
 * 按需取某个候选的缩略图（R45：由 GenerationBoard 在候选行进入视口时发 `need-thumb`）。
 *
 * <p>原先这里是把整页候选**一次性全取**（19 行就 19 个请求）；现在只取真正看得见的那几行，
 * 行的可见性由组件用 IntersectionObserver 判断（只有组件知道行在 DOM 里的位置）。</p>
 *
 * @param row 候选
 */
async function loadThumb(row: DpGenerationVO) {
  const key = 'gen-' + row.id;
  if (!row.previewable) {
    releaseUrl(key);
    return;
  }
  if (objectUrls.value[key]) return;
  try {
    setUrl(key, await fetchGenerationThumbnailBlobUrl(row.id));
  } catch {
    /* 缩略图失败不阻断列表（点开预览仍可取原图） */
  }
}

/**
 * 跨项目总览（`#main` 里的那张表）的缩略图：分页内 20 行，一次性取即可。
 *
 * <p>R45 只把"项目内"那一块（候选多、一屏看不完）改成按需；总览是分页视图、每页就 20 行，
 * 且没有复用的懒加载容器，保持原样更简单。</p>
 */
async function loadOverviewThumbs() {
  for (const row of rows.value) {
    await loadThumb(row);
  }
}

/** 产品图是「产品基准」的左图；未配置时明确不留旧图 */
async function loadProductImageUrl() {
  if (!taskId.value || !productImage.value?.configured) {
    releaseUrl('product');
    return;
  }
  try {
    setUrl('product', await fetchProductImageBlobUrl(taskId.value));
  } catch (error) {
    releaseUrl('product');
    ElMessage.warning((await extractErrorMessage(error)) ?? '产品图读取失败');
  }
}

async function openPreview(row: DpGenerationVO) {
  previewVisible.value = true;
  try {
    const url = await fetchGenerationPreviewBlobUrl(row.id);
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

async function doRetry(row: DpGenerationVO) {
  retryingId.value = String(row.id);
  try {
    await retryGeneration(row.id);
    ElMessage.success('已新建一次候选');
    await load();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '重试失败');
  } finally {
    retryingId.value = '';
  }
}

async function doSelectCandidate(gen: DpGenerationVO) {
  if (!taskId.value) return;
  busy.value = 'select-' + gen.id;
  try {
    await selectCandidate(taskId.value, gen.id);
    ElMessage.success(`已选定候选 #${gen.candidateNo}，并已登记产出与发起质检`);
    await loadProject();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '选定失败');
  } finally {
    busy.value = '';
  }
}

async function doRunQa(gen: DpGenerationVO) {
  if (!taskId.value) return;
  busy.value = 'qa-' + gen.id;
  try {
    await runCandidateQa(taskId.value, gen.id);
    ElMessage.success('已发起质检（只筛除，不放行）');
    await loadProject();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '发起质检失败');
  } finally {
    busy.value = '';
  }
}

async function doRegenerateScreen(gen: DpGenerationVO) {
  if (!taskId.value || gen.screenId == null) return;
  busy.value = 'regen-' + gen.screenId;
  try {
    await regenerateScreen(taskId.value, gen.screenId);
    ElMessage.success(`${screenLabel(gen, screenMap.value)} 已重新提交出图`);
    await loadProject();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '重出失败');
  } finally {
    busy.value = '';
  }
}

/** 向出图内核要一次真实状态（质检回填 / 失败自动重试） */
async function doRefreshProduction() {
  if (!taskId.value) return;
  refreshing.value = true;
  try {
    const res = await refreshProduction(taskId.value);
    ElMessage.success(`已刷新：${res.data?.screens?.length ?? 0} 屏状态（含质检回填与失败自动重试）`);
    await loadProject();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '刷新失败');
  } finally {
    refreshing.value = false;
  }
}

/** 并排对比：左=产品图（产品基准），右=生成图原图；都走 blob，不出现对象存储键 */
async function openCompare(gen: DpGenerationVO) {
  compareScreenKey.value = gen.screenId == null ? '' : String(gen.screenId);
  compareGen.value = { gen, typeDesc: screenTypeDesc(gen, screenMap.value) };
  if (!productImage.value?.configured) {
    releaseUrl('product');
  } else if (!urlOf('product')) {
    void loadProductImageUrl();
  }
  const key = 'genpreview-' + gen.id;
  if (urlOf(key)) return;
  try {
    setUrl(key, await fetchGenerationPreviewBlobUrl(gen.id));
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取生成图失败');
  }
}

function closeCompare() {
  compareScreenKey.value = '';
  compareGen.value = null;
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
  await loadProjects();
  await reloadCurrent();
});

onBeforeUnmount(() => {
  releaseAll();
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

.sub-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.sub-head h3 {
  margin: 0;
  font-size: 15px;
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

.good {
  color: #a7f3d0;
}
.bad {
  color: #fca5a5;
}
.warn {
  color: #fde68a;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

/* 产品图 | 生成图 并排对比 */
.compare-box {
  padding: 12px;
  margin-top: 14px;
  background: var(--surface);
  border: 1px dashed var(--line2);
  border-radius: 6px;
}
.compare-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
}
.compare-head .spacer {
  flex: 1;
}
.compare-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}
.compare-grid figure {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  margin: 0;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.compare-grid img {
  width: 100%;
  height: 300px;
  object-fit: contain;
  background: #05070a;
  border-radius: 4px;
}
.compare-grid figcaption {
  font-size: 12px;
  color: var(--t2);
  text-align: center;
}
.img-placeholder {
  display: grid;
  place-items: center;
  padding: 8px;
  font-size: 12px;
  color: var(--t3);
  text-align: center;
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
