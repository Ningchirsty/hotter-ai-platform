<template>
  <aside class="discovery" aria-label="AI 灵感推荐">
    <header class="discovery-heading">
      <div>
        <span class="eyebrow">
          <el-icon><MagicStick /></el-icon>
          AI 灵感推荐
        </span>
        <h2>灵感发现</h2>
        <p>先选喜欢的作品，再找到适合的模型</p>
      </div>
      <span class="media-pill">{{ media === 'video' ? '参考视频' : '参考图像' }}</span>
    </header>
    <div class="pool-actions">
      <button type="button" :disabled="loading || media !== 'image'" @click="refreshPool">刷新作品</button>
      <button type="button" :disabled="loading || pagination.pageCount < 2" @click="changeBatch">换一批</button>
      <span role="status">{{ loading ? '正在更新…' : updatedAt ? '更新于 ' + updatedAt : '' }}</span>
    </div>
    <div class="discovery-toolbar">
      <el-input v-model="keyword" placeholder="搜索风格、构图或主题" clearable aria-label="搜索灵感">
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <button
        type="button"
        :class="['saved-filter', { active: savedOnly }]"
        :aria-pressed="savedOnly"
        @click="savedOnly = !savedOnly"
      >
        <el-icon><CollectionTag /></el-icon>
        收藏 {{ favorites.length || '' }}
      </button>
    </div>
    <div class="model-filters">
      <label>
        来源模型
        <select v-model="selectedModel" aria-label="推荐模型筛选">
          <option value="">全部可用模型</option>
          <option v-for="model in connectedModels" :key="model" :value="model">{{ model }}</option>
        </select>
      </label>
      <label>
        创作能力
        <select v-model="selectedCapability" aria-label="推荐能力筛选">
          <option value="">全部可用能力</option>
          <option v-for="code in connectedCapabilities" :key="code" :value="code">{{ capabilityLabel(code) }}</option>
        </select>
      </label>
    </div>
    <p class="recommendation-context">
      {{ media === 'video' ? '视频参考：镜头、运动与叙事' : '图像参考：构图、材质与风格' }} · 根据已发布的模型能力推荐
    </p>
    <nav class="categories" aria-label="灵感类别">
      <button
        v-for="item in categories"
        :key="item"
        type="button"
        :class="{ active: category === item }"
        :aria-pressed="category === item"
        @click="category = item"
      >
        {{ item }}
      </button>
    </nav>
    <div v-if="appliedTitle" class="applied-note" role="status">
      <el-icon><Check /></el-icon>
      已带入「{{ appliedTitle }}」，请在左侧完善素材与参数。
    </div>
    <div v-if="visibleWorks.length" ref="galleryElement" class="masonry">
      <article v-for="work in visibleWorks" :key="work.id" class="work-card">
        <button type="button" class="work-open" :aria-label="`查看${work.title}，推荐模型`" @click="selected = work">
          <GeneratedCover v-if="work.assetId" :asset-id="work.assetId" :title="work.title" />
          <ReferenceCover v-else :cover="work.cover" :title="work.title">
            <span class="cover-label">{{ work.category }}</span>
            <span class="cover-action">
              查看创作方向
              <el-icon><ArrowRight /></el-icon>
            </span>
          </ReferenceCover>
        </button>
        <div class="work-meta">
          <button type="button" class="work-title" @click="selected = work">{{ work.title }}</button>
          <button
            type="button"
            :class="['save-work', { active: favorites.includes(work.id) }]"
            :aria-label="`${favorites.includes(work.id) ? '取消收藏' : '收藏'}${work.title}`"
            :aria-pressed="favorites.includes(work.id)"
            @click="toggleFavorite(work.id)"
          >
            <el-icon><CollectionTag /></el-icon>
          </button>
        </div>
        <div class="work-models">
          <span v-for="route in recommendedRoutes(work).slice(0, 2)" :key="route.model + route.capability + route.workflowCode">
            {{ route.model }} · {{ capabilityLabel(route.capability) }}
          </span>
        </div>
        <p class="work-tags">{{ work.tags.join(' · ') }}</p>
      </article>
    </div>
    <div v-else class="gallery-empty">
      <el-icon><Picture /></el-icon>
      <b>{{ error || (media === 'video' ? '视频灵感更新源尚未接入' : savedOnly ? '还没有匹配的收藏' : '暂无匹配的真实作品') }}</b>
      <p>
        {{
          media === 'video' ? '蓝章鱼图像作品不会混入视频推荐。' : '成功完成的云端图像任务会自动加入当前账号的作品池；也可更换筛选条件。'
        }}
      </p>
      <button type="button" @click="resetFilters">查看全部灵感</button>
    </div>
    <footer class="discovery-footer">
      <nav v-if="pagination.total" class="discovery-pagination" aria-label="灵感推荐分页">
        <button
          type="button"
          aria-label="上一页灵感"
          :disabled="pagination.currentPage === 1"
          @click="goToPage(pagination.currentPage - 1)"
        >
          上一页
        </button>
        <button
          v-for="number in pagination.pageCount"
          :key="number"
          type="button"
          :class="{ active: number === pagination.currentPage }"
          :aria-label="'第 ' + number + ' 页灵感'"
          :aria-current="number === pagination.currentPage ? 'page' : undefined"
          @click="goToPage(number)"
        >
          {{ number }}
        </button>
        <button
          type="button"
          aria-label="下一页灵感"
          :disabled="pagination.currentPage === pagination.pageCount"
          @click="goToPage(pagination.currentPage + 1)"
        >
          下一页
        </button>
      </nav>
      <span v-if="pagination.total" class="page-summary" role="status">
        第 {{ pagination.currentPage }} / {{ pagination.pageCount }} 页 · 共 {{ pagination.total }} 个{{
          media === 'video' ? '视频' : '图像'
        }}参考
      </span>
      <p>
        {{
          media === 'video'
            ? '视频需单独接入真实视频更新源。'
            : '图像来自当前账号已完成的蓝章鱼任务；刷新仅更新作品池，不产生生成费用。'
        }}
        先选作品，再按可用模型带入创作。
      </p>
    </footer>

    <el-dialog
      class="creative-dialog"
      :model-value="!!selected"
      :title="selected?.title"
      width="850px"
      style="max-width: calc(100vw - 32px)"
      align-center
      destroy-on-close
      @closed="selected = undefined"
      @update:model-value="
        value => {
          if (!value) selected = undefined;
        }
      "
    >
      <div v-if="selected" class="inspiration-detail">
        <div class="detail-art">
          <InspirationVideo
            v-if="selected.media === 'video'"
            :reference="selected.video"
            :title="selected.title"
            playable
          />
          <GeneratedCover v-else-if="selected.assetId" :asset-id="selected.assetId" :title="selected.title" />
          <ReferenceCover v-else :cover="selected.cover" :title="selected.title" />
          <a
            v-if="selected.media === 'video'"
            class="reference-source"
            :href="selected.video.sourceUrl"
            target="_blank"
            rel="noopener noreferrer"
          >
            来源：{{ selected.video.sourceName }} ↗
          </a>
          <p>参考作品 · {{ selected.category }}</p>
        </div>
        <div class="detail-directions">
          <span class="detail-eyebrow">从这件作品开始</span>
          <h3>推荐创作方向</h3>
          <p class="detail-tags">{{ selected.tags.join(' / ') }}</p>
          <article v-for="route in recommendedRoutes(selected)" :key="route.model + route.capability + route.workflowCode" class="model-recommendation">
            <div class="model-heading">
              <b>{{ route.model }}</b>
              <span>{{ route.source === 'cloud' ? '云端 · 蓝章鱼' : '本地 · ComfyUI' }}</span>
            </div>
            <strong>{{ capabilityLabel(route.capability) }}</strong>
            <p>{{ route.reason }}</p>
            <div class="reference-hint">{{ route.referenceHint }}</div>
            <details v-if="route.prompt">
              <summary>查看创作描述</summary>
              <p>{{ route.prompt }}</p>
            </details>
            <button type="button" :disabled="busy || !isRouteAvailable(route, workflows, cloudStatus)" @click="applyRoute(route)">
              {{
                busy
                  ? '请等待当前操作完成'
                  : isRouteAvailable(route, workflows, cloudStatus)
                    ? '使用此模型，带入创作'
                    : '工作流尚未开放'
              }}
              <el-icon><ArrowRight /></el-icon>
            </button>
            <small v-if="!isRouteAvailable(route, workflows, cloudStatus)">
              可用性以服务端发布状态为准；读取失败或尚未发布时不开放。
            </small>
          </article>
          <p v-if="selected.media === 'image' && selected.provenance" class="detail-tags">
            来源：蓝章鱼真实生成 · {{ selected.provenance.model }}<br />
            原任务：{{ selected.provenance.taskNo }}
          </p>
        </div>
      </div>
    </el-dialog>
  </aside>
</template>

<script setup lang="ts">
import { ArrowRight, Check, CollectionTag, MagicStick, Picture, Search } from '@element-plus/icons-vue';
import { useStorage } from '@vueuse/core';
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue';
import { listImageInspirations, type GeneratedInspiration } from '@/api/image/inspiration';
import type { CloudImageModelsVO } from '@/api/image/types';
import { categoriesFor } from './catalog';
import { generatedWork, generatedRoutes } from './generated-pool';
import GeneratedCover from './GeneratedCover.vue';
import InspirationVideo from './InspirationVideo.vue';
import ReferenceCover from './ReferenceCover.vue';
import { isRouteAvailable, type CreativeMedia, type InspirationRoute, type InspirationWork, type InspirationWorkflow } from './types';

const props = defineProps<{
  media: CreativeMedia; workflows: InspirationWorkflow[]; cloudStatus?: CloudImageModelsVO;
  busy?: boolean; appliedTitle?: string; revision?: number;
}>();
const emit = defineEmits<{ apply: [route: InspirationRoute, title: string] }>();
const category = ref('全部'), keyword = ref(''), savedOnly = ref(false);
const favorites = useStorage<string[]>('hotter-creative-inspiration-favorites-v1', []);
const currentPage = ref(1), selectedModel = ref(''), selectedCapability = ref('');
const selected = ref<InspirationWork>(), galleryElement = ref<HTMLElement>();
const works = ref<GeneratedInspiration[]>([]), total = ref(0), loading = ref(false), error = ref(''), updatedAt = ref('');
const pageSize = 6;
let sequence = 0, timer: ReturnType<typeof setTimeout> | undefined, refreshTimer: ReturnType<typeof setInterval> | undefined;
const categories = computed(() => [...categoriesFor(props.media), ...(props.media === 'image' ? ['风格与构图'] : [])]);
const connectedModels = computed(() => props.cloudStatus?.configured ? props.cloudStatus.models.filter(model => model !== 'flux-2-pro') : []);
const connectedCapabilities = computed(() => [...new Set((props.cloudStatus?.profiles ?? [])
  .filter(profile => !selectedModel.value || profile.model === selectedModel.value)
  .flatMap(profile => profile.capabilities.filter(cap => cap.verified).map(cap => cap.code)))]);
const filteredWorks = computed(() => works.value.map(generatedWork).filter(work =>
  (category.value === '全部' || work.category === category.value) && (!savedOnly.value || favorites.value.includes(work.id))));
const pagination = computed(() => ({
  currentPage: currentPage.value, pageCount: Math.max(1, Math.ceil(total.value / pageSize)), total: total.value
}));
const visibleWorks = computed(() => filteredWorks.value);
async function loadPool() {
  const current = ++sequence;
  if (props.media !== 'image') { works.value = []; total.value = 0; loading.value = false; return; }
  loading.value = true; error.value = '';
  try {
    const response = await listImageInspirations({ pageNum: currentPage.value, pageSize,
      keyword: keyword.value.trim() || undefined, model: selectedModel.value || undefined,
      capability: selectedCapability.value || undefined, category: category.value,
      savedIds: savedOnly.value ? favorites.value.map(id => id.replace('generated-', '')).join(',') : undefined });
    if (current !== sequence) return;
    works.value = response.data.rows ?? []; total.value = response.data.total ?? 0;
    currentPage.value = Math.min(currentPage.value, Math.max(1, Math.ceil(total.value / pageSize)));
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  } catch { if (current === sequence) { error.value = '真实作品读取失败，请刷新重试'; works.value = []; total.value = 0; } }
  finally { if (current === sequence) loading.value = false; }
}
function refreshPool() { currentPage.value = 1; void loadPool(); }
function changeBatch() { goToPage(currentPage.value < pagination.value.pageCount ? currentPage.value + 1 : 1); }
function goToPage(page: number) {
  currentPage.value = Math.max(1, Math.min(pagination.value.pageCount, page));
  void loadPool(); galleryElement.value?.scrollIntoView({ behavior: 'auto', block: 'start' });
}
function recommendedRoutes(work: InspirationWork) {
  return work.media === 'image' ? generatedRoutes(work, props.cloudStatus, props.workflows) : [];
}
function toggleFavorite(id: string) { favorites.value = favorites.value.includes(id) ? favorites.value.filter(value => value !== id) : [...favorites.value, id]; }
function resetFilters() { category.value = '全部'; keyword.value = ''; savedOnly.value = false; selectedModel.value = ''; selectedCapability.value = ''; refreshPool(); }
function capabilityLabel(code: string) {
  return ({ T2I:'文生图', I2I:'图生图', EDIT:'参考图编辑', MULTI:'多图融合', MASK:'局部重绘', OUTPAINT:'画面扩展', TRANSPARENT:'透明背景', BGREMOVE:'抠图去背景', WHITEBG:'白底图', T2V:'文生视频', I2V:'图生视频', FL2V:'首尾帧生视频' } as Record<string,string>)[code] || code;
}
function applyRoute(route: InspirationRoute) {
  if (!selected.value || props.busy || !isRouteAvailable(route, props.workflows, props.cloudStatus)) return;
  emit('apply', route, selected.value.title); selected.value = undefined;
}
watch([keyword, category, savedOnly, favorites, selectedModel, selectedCapability, () => props.media], () => {
  if (timer) clearTimeout(timer); currentPage.value = 1;
  timer = setTimeout(loadPool, 300);
});
watch(() => props.media, () => { selected.value = undefined; });
watch(() => props.revision, () => { if (!loading.value) refreshPool(); });
onMounted(() => { void loadPool(); refreshTimer = setInterval(() => { if (!document.hidden && !loading.value && !selected.value) void loadPool(); }, 60000); });
onBeforeUnmount(() => { sequence++; if (timer) clearTimeout(timer); if (refreshTimer) clearInterval(refreshTimer); });
</script>

<style scoped>
.pool-actions { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.pool-actions button { padding: 8px 12px; border: 1px solid #e1e6f0; border-radius: 7px; background: #f7f4fc; color: #7961ca; cursor: pointer; }
.pool-actions span { font-size: 11px; color: #8d98aa; }
.pool-actions button:disabled { opacity: .5; cursor: not-allowed; }

.discovery {
  display: flex;
  flex-direction: column;
  align-self: stretch;
  min-width: 0;
  padding: 24px;
  color: #29364f;
  background: #fff;
  border: 1px solid #e1e6f0;
  border-radius: 14px;
  container-type: inline-size;
}
.discovery button {
  font: inherit;
}
.discovery-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 22px;
}
.eyebrow {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #7961ca;
  font-size: 12px;
}
.discovery h2 {
  margin: 7px 0;
  font-size: 23px;
  font-weight: 600;
}
.discovery-heading p {
  margin: 0;
  color: #7e899f;
  font-size: 12px;
}
.media-pill {
  padding: 7px 10px;
  border-radius: 7px;
  color: #7961ca;
  background: #f2edfc;
  font-size: 11px;
  white-space: nowrap;
}
.discovery-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.discovery-toolbar :deep(.el-input) {
  flex: 1;
  min-width: 0;
  --el-input-text-color: #29364f;
  --el-input-bg-color: #f8f9fc;
  --el-input-border-color: #e1e6f0;
}
.saved-filter {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 0 10px;
  color: #7e899f;
  border: 1px solid #e1e6f0;
  border-radius: 7px;
  background: white;
  cursor: pointer;
}
.saved-filter.active {
  color: #7961ca;
  background: #f2edfc;
}
.categories {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  padding-bottom: 18px;
}
.categories button {
  padding: 8px 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #68758d;
  font-size: 12px;
  cursor: pointer;
}
.categories button:hover {
  color: #7961ca;
  background: #f7f4fc;
}
.categories button.active {
  color: #7961ca;
  background: #f2edfc;
  font-weight: 600;
}
.applied-note {
  display: flex;
  gap: 7px;
  margin-bottom: 16px;
  padding: 10px;
  color: #378071;
  font-size: 12px;
  background: #eef8f4;
  border-radius: 7px;
}
.masonry {
  columns: 3;
  column-gap: 16px;
}
.work-card {
  margin-bottom: 22px;
  break-inside: avoid;
}
.work-open {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  border-radius: 10px;
}
.cover-label {
  position: absolute;
  left: 9px;
  bottom: 9px;
  padding: 5px 7px;
  color: #29364f;
  background: rgba(255, 255, 255, 0.94);
  border-radius: 5px;
  font-size: 10px;
}
.cover-action {
  position: absolute;
  inset: auto 8px 8px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 7px;
  padding: 10px;
  background: rgba(41, 54, 79, 0.91);
  color: white;
  border-radius: 7px;
  opacity: 0;
  transition: opacity 0.15s;
  font-size: 12px;
}
.work-open:hover .cover-action,
.work-open:focus-visible .cover-action {
  opacity: 1;
}
.work-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: 10px;
}
.work-title {
  padding: 0;
  border: 0;
  color: #29364f;
  background: transparent;
  text-align: left;
  font-size: 13px !important;
  line-height: 1.7;
  cursor: pointer;
}
.save-work {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  padding: 4px;
  border: 0;
  background: transparent;
  color: #8d98aa;
  cursor: pointer;
}
.save-work.active {
  color: #7961ca;
  background: #f2edfc;
  border-radius: 4px;
}
.work-tags {
  margin: 4px 0 0;
  color: #8d98aa;
  font-size: 11px;
  line-height: 1.7;
}
.discovery-footer {
  margin-top: 0;
  padding-top: 16px;
  text-align: center;
}
.discovery-footer button {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  padding: 10px 18px;
  color: #7961ca;
  background: #f7f4fc;
  border: 1px solid #e9e1f7;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
}
.discovery-footer p {
  margin: 16px 0 0;
  color: #8d98aa;
  font-size: 11px;
  line-height: 1.8;
  text-align: left;
}
.gallery-empty {
  display: grid;
  place-items: center;
  align-content: center;
  flex: 1;
  min-height: 300px;
  color: #7e899f;
  font-size: 13px;
}
.gallery-empty .el-icon {
  font-size: 32px;
  margin-bottom: 16px;
}
.gallery-empty button {
  padding: 9px 12px;
  border: 1px solid #e1e6f0;
  border-radius: 7px;
  color: #7961ca;
  background: white;
  cursor: pointer;
}
.inspiration-detail {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 28px;
  color: var(--el-text-color-primary);
}
.detail-art p {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.detail-eyebrow {
  color: #7961ca;
  font-size: 12px;
}
.detail-directions h3 {
  margin: 8px 0;
  font-size: 21px;
}
.detail-tags {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.model-recommendation {
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
}
.model-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.model-heading span {
  padding: 4px 6px;
  color: #7961ca;
  background: #f2edfc;
  font-size: 11px;
  border-radius: 5px;
}
.model-recommendation strong {
  font-size: 13px;
}
.model-recommendation p {
  font-size: 12px;
  line-height: 1.8;
}
.reference-hint {
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.8;
}
.model-recommendation details {
  margin-top: 12px;
  font-size: 12px;
}
.model-recommendation summary {
  cursor: pointer;
  color: #7961ca;
}
.model-recommendation button {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  padding: 12px;
  border: 0;
  background: #7961ca;
  color: white;
  border-radius: 7px;
  cursor: pointer;
  font-size: 12px;
}
.model-recommendation button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.model-recommendation small {
  display: block;
  margin-top: 9px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.7;
}
.cloud-direction {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 16px 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.cloud-direction p {
  margin: 6px 0 0;
  line-height: 1.8;
}
button:focus-visible {
  outline: 2px solid #7961ca;
  outline-offset: 3px;
}
@container (max-width: 720px) {
  .masonry {
    columns: 2;
  }
}
@container (max-width: 380px) {
  .discovery-heading {
    flex-wrap: wrap;
  }
  .masonry {
    column-gap: 12px;
  }
  .discovery-toolbar {
    flex-wrap: wrap;
  }
  .saved-filter {
    min-height: 32px;
  }
}
@media (max-width: 620px) {
  .discovery {
    padding: 16px;
  }
  .inspiration-detail {
    grid-template-columns: 1fr;
  }
  .detail-art {
    max-width: 260px;
    margin: auto;
    width: 100%;
  }
}
.model-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.model-filters label {
  display: flex;
  flex: 1;
  min-width: 170px;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #728097;
}
.model-filters select {
  width: 100%;
  min-width: 0;
  padding: 8px;
  color: #29364f;
  border: 1px solid #e1e6f0;
  border-radius: 7px;
  background: #f8f9fc;
  font: inherit;
}
.recommendation-context {
  margin: 12px 0 15px;
  color: #728097;
  font-size: 11px;
  line-height: 1.8;
}
.work-models {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 6px;
}
.work-models span {
  padding: 3px 5px;
  color: #7961ca;
  background: #f2edfc;
  border-radius: 4px;
  font-size: 10px;
}
.discovery-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.discovery-pagination button {
  min-width: 32px;
  padding: 8px 10px;
  background: white;
  border-color: #e1e6f0;
}
.discovery-pagination button.active {
  background: #7961ca;
  color: white;
  border-color: #7961ca;
}
.discovery-pagination button:disabled {
  color: #a0a9b8;
  background: #f8f9fc;
  cursor: not-allowed;
}
.page-summary {
  display: block;
  margin-top: 12px;
  color: #728097;
  font-size: 11px;
}
.reference-source {
  display: inline-block;
  margin-top: 12px;
  color: #7961ca;
  font-size: 12px;
}
.masonry {
  scroll-margin-top: 20px;
}
.model-recommendation + .model-recommendation {
  margin-top: 12px;
}
select:focus-visible {
  outline: 2px solid #7961ca;
  outline-offset: 2px;
}
</style>
