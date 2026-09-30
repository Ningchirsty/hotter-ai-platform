<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    :size="drawerSize"
    :with-header="false"
    append-to-body
    class="studio-drawer"
    @open="onOpen"
    @closed="onClosed"
  >
    <div class="drawer">
      <div class="drawer-head">
        <span class="drawer-title">资产抽屉</span>
        <span class="drawer-sub">{{ projectName || '当前项目' }}</span>
        <span class="spacer" />
        <el-button size="small" text @click="reload" :loading="loading">刷新</el-button>
        <el-button size="small" text @click="visible = false">关闭</el-button>
      </div>

      <p class="drawer-note">
        只读：这里只把项目里已有的资产（参考图/产品图、出图候选、排版版本）集中列出来，不做任何修改。
        附件缩略图是**原图直读**（附件目前没有缩略图接口），首次打开会慢一点，所以自动加载有上限。
      </p>

      <!-- 参考图与产品图 -->
      <div class="section">
        <div class="sec-head">
          <span>参考图与产品图</span>
          <span class="sec-count">
            {{ imageFiles.length }} 张附件{{ productImage ? ' · 产品图 1 张' : '' }}
            <template v-if="thumbPending"> · 缩略图 {{ thumbDone }}/{{ thumbTotal }} 读取中…</template>
          </span>
        </div>
        <p v-if="!imageFiles.length && !productImage" class="empty">项目里还没有图片附件（在项目页上传参考图/产品图）</p>
        <div v-else class="thumbs">
          <div v-if="productImage" class="thumb">
            <img v-if="productThumb" :src="productThumb" alt="产品图" />
            <div v-else class="thumb-ph">{{ productThumbFailed ? '读取失败' : '读取中…' }}</div>
            <span class="thumb-name">{{ productImage.fileName || '产品图' }}</span>
            <span class="thumb-tag is-product">产品图</span>
          </div>
          <div v-for="img in shownImages" :key="String(img.fileId)" class="thumb">
            <img v-if="thumbs[thumbKey(img)]" :src="thumbs[thumbKey(img)]" alt="参考图" />
            <div v-else class="thumb-ph">{{ failedThumbs[thumbKey(img)] ? '读取失败' : '读取中…' }}</div>
            <span class="thumb-name">{{ img.fileName || img.fileId }}</span>
            <span class="thumb-tag">参考图</span>
          </div>
        </div>
        <p v-if="failedCount" class="hint">
          有 {{ failedCount }} 张缩略图读取失败（原图仍在对象存储里，可在项目页重试）——没有用占位图假装成功。
          <template v-if="firstFailReason"><br />首个原因：{{ firstFailReason }}</template>
        </p>
        <el-button
          v-if="imageFiles.length > THUMB_LIMIT"
          size="small"
          text
          class="more"
          @click="thumbLimit = imageFiles.length"
        >
          加载其余 {{ imageFiles.length - thumbLimit }} 张缩略图
        </el-button>
      </div>

      <!-- 出图候选 -->
      <div class="section">
        <div class="sec-head">
          <span>出图候选</span>
          <span class="sec-count">{{ generations.length }} 张</span>
        </div>
        <p v-if="!generations.length" class="empty">还没有候选图（在出图页发起出图）</p>
        <ul v-else class="rows">
          <li v-for="gen in generations" :key="String(gen.id)" class="row">
            <span class="row-main">
              {{ screenLabel(gen.screenId) }} · 候选 {{ gen.candidateNo ?? '-' }}
            </span>
            <span class="badge" :class="statusClass(gen.status)">{{ gen.statusDesc || gen.status }}</span>
            <span class="badge" :class="qaClass(gen.qaVerdict)">质检 {{ qaText(gen.qaVerdict) }}</span>
            <span class="spacer" />
            <span class="row-time">{{ gen.createTime || '' }}</span>
            <el-button
              v-if="gen.previewable"
              size="small"
              text
              type="primary"
              @click="openGeneration(gen)"
            >
              看大图
            </el-button>
          </li>
        </ul>
      </div>

      <!-- 排版版本 -->
      <div class="section">
        <div class="sec-head">
          <span>排版版本</span>
          <span class="sec-count">{{ versions.length }} 个</span>
        </div>
        <p v-if="!versions.length" class="empty">还没有渲染过机排版版本（在评审页渲染）</p>
        <ul v-else class="rows">
          <li v-for="ver in versions" :key="String(ver.id)" class="row">
            <span class="row-main">v{{ ver.version ?? '-' }} · {{ ver.kind || '版本' }}</span>
            <span class="badge">{{ versionStatusText(ver.status) }}</span>
            <span class="spacer" />
            <span class="row-time">{{ ver.createTime || '' }}</span>
            <el-button size="small" text type="primary" @click="openVersion(ver)">看长图</el-button>
          </li>
        </ul>
      </div>

      <p v-if="error" class="err">{{ error }}</p>
      <!-- 取数失败必须说出来：不然界面会显示成"这个项目没有附件/候选/版本"——假的空态（R19 验收撞到过） -->
      <p v-if="loadErrors.length" class="err">
        {{ loadErrors.join('、') }}读取失败，点「刷新」重试——<strong>不是「没有数据」</strong>。
      </p>
    </div>

    <!-- 大图预览：与既有页面一致，用 blob URL + 本地遮罩，不引入新的查看器组件 -->
    <div v-if="preview.url" class="preview-mask" @click="closePreview">
      <img :src="preview.url" :alt="preview.title" />
      <p class="preview-title">{{ preview.title }}</p>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  fetchCreativeFileBlobUrl,
  fetchCreativeFileThumbnailBlobUrl,
  fetchDetailPreviewBlobUrl,
  fetchGenerationPreviewBlobUrl,
  fetchProductImageBlobUrl,
  getDetailPage,
  getStoryboard,
  listCreativeFiles,
  listGenerations
} from '@/api/creative';
import { getProjectProductImage } from '@/api/creative';
import type {
  CreativeProjectVO,
  DpDetailPageVersionVO,
  DpDetailPageVO,
  DpGenerationVO,
  DpStoryboardVO
} from '@/api/creative/types';
import type { CpTaskFileVO } from '@/api/content/task/types';

/**
 * 资产抽屉（ASSET_DRAWER，R18 真做）。
 *
 * <p><b>为什么需要它</b>：资产现在散在三处——参考图在项目页、候选在生产页、排版版本在评审页。
 * 做精修或对账时要来回跳页；装配定义（`dp_workspace_schema`）里本来就把它列为一个面板。</p>
 *
 * <p><b>只读</b>：只列与预览，不写任何数据。图片一律走既有 blob 通道
 * （`fetchCreativeFileBlobUrl` 等），URL 生命周期由本组件管：关抽屉、切项目时统一 revoke，
 * 避免"图片还在渲染就被回收"或"越攒越多不回收"。</p>
 *
 * <p><b>缩略图是有上限的</b>：附件可能有几十张（生产上有过 34 张的项目），一次性拉几十个 blob
 * 会把慢网拖死。所以默认只自动加载前 {@link THUMB_LIMIT} 张，其余的由人点一下再加载——
 * 不做"悄悄只显示 12 张"那种假完整。</p>
 */
const props = defineProps<{
  /** 当前项目ID */
  taskId?: string | number;
  /** 项目名（仅用于标题展示） */
  projectName?: string;
}>();

const visible = defineModel<boolean>('visible', { required: true });

/** 自动加载缩略图的上限 */
const THUMB_LIMIT = 12;
/** 缩略图并发路数（原图直读，串行太慢；并发太高会打满带宽） */
const THUMB_CONCURRENCY = 4;

const loading = ref(false);
const error = ref('');
/**
 * 哪几块取数失败了。
 *
 * <p>为什么单独记：`Promise.all` 里每项都 `.catch(() => null)`，失败与"真的没有数据"
 * 在界面上长得一模一样（都显示"还没有…"）。R19 的批量验收就撞到过：接口明明有 11/9/1，
 * 抽屉却显示 0——被当成"产品没数据"排查了一轮。现在失败会明确写在下面。</p>
 */
const loadErrors = ref<string[]>([]);
const drawerSize = ref('46%');

const project = ref<CreativeProjectVO | null>(null);
const files = ref<CpTaskFileVO[]>([]);
const generations = ref<DpGenerationVO[]>([]);
const storyboard = ref<DpStoryboardVO | null>(null);
const versions = ref<DpDetailPageVersionVO[]>([]);
const productImage = ref<{ fileName?: string; configured?: boolean } | null>(null);

/** 缩略图：key → blob URL（key 用 `file-<id>` / `product`） */
const thumbs = ref<Record<string, string>>({});
/** 读取失败的缩略图（如实标"读取失败"，不永远停在"读取中"） */
const failedThumbs = ref<Record<string, boolean>>({});
/**
 * 失败原因（每个 key 一条）。
 *
 * <p>为什么要记：R20 排查"有些缩略图读取失败"时，服务端日志全是 200、nginx 也没有非 200，
 * 但界面只知道"失败"——**不知道是超时、鉴权、还是响应体不是图片**，只能靠猜。
 * 现在把原因带出来（提示里显示首条），下次一眼能定位。</p>
 */
const failedReasons = ref<Record<string, string>>({});
const productThumb = ref('');
const productThumbFailed = ref(false);
/** 缩略图进度（给标题栏显示"8/12 读取中…"） */
const thumbTotal = ref(0);
const thumbDone = ref(0);
const thumbPending = computed(() => thumbTotal.value > 0 && thumbDone.value < thumbTotal.value);
const failedCount = computed(() => Object.keys(failedThumbs.value).length + (productThumbFailed.value ? 1 : 0));
/** 失败原因里的首条（提示里显示，便于一眼定位是超时还是响应体不对） */
const firstFailReason = computed(() => Object.values(failedReasons.value)[0] || '');
const thumbLimit = ref(THUMB_LIMIT);
const preview = ref<{ url: string; title: string }>({ url: '', title: '' });

const imageFiles = computed(() =>
  files.value.filter((f) => (f.fileKind || '').toUpperCase() === 'IMAGE')
);
const shownImages = computed(() => imageFiles.value.slice(0, thumbLimit.value));

/** 缩略图的 key */
function thumbKey(file: CpTaskFileVO): string {
  return 'file-' + String(file.fileId);
}

/** 屏号显示：storyboard 里的 screenId → screenNo */
function screenLabel(screenId?: string | number): string {
  const screens = storyboard.value?.screens || [];
  const hit = screens.find((s) => String(s.id) === String(screenId));
  return hit?.screenNo ? '屏 ' + hit.screenNo : '未归属屏';
}

/** 候选状态 → 样式类 */
function statusClass(status?: string): string {
  if (status === 'APPROVED') return 'is-ok';
  if (status === 'REJECTED') return 'is-bad';
  return '';
}

/** 质检结论 → 样式类（只筛除不放行：INCONSISTENT 是"已被筛除"） */
function qaClass(verdict?: string): string {
  if (verdict === 'CONSISTENT') return 'is-ok';
  if (verdict === 'INCONSISTENT') return 'is-bad';
  if (verdict === 'UNCERTAIN') return 'is-warn';
  return '';
}

/** 质检结论 → 文本（没有结论就说"未质检"，不当成通过） */
function qaText(verdict?: string): string {
  if (verdict === 'CONSISTENT') return '一致';
  if (verdict === 'INCONSISTENT') return '不一致（已筛除）';
  if (verdict === 'UNCERTAIN') return '无结论';
  return '未质检';
}

/** 版本状态 → 文本 */
function versionStatusText(status?: string): string {
  if (status === 'RENDERED') return '渲染待终审';
  if (status === 'APPROVED') return '已通过';
  if (status === 'REJECTED') return '已打回';
  return status || '未知';
}

/** 记一个 blob URL（同 key 旧的先回收，避免泄漏） */
function putUrl(key: string, url: string) {
  const old = thumbs.value[key];
  if (old) URL.revokeObjectURL(old);
  thumbs.value = { ...thumbs.value, [key]: url };
}

/** 回收全部 blob URL */
function releaseAll() {
  Object.values(thumbs.value).forEach((u) => URL.revokeObjectURL(u));
  thumbs.value = {};
  failedThumbs.value = {};
  failedReasons.value = {};
  thumbTotal.value = 0;
  thumbDone.value = 0;
  if (productThumb.value) {
    URL.revokeObjectURL(productThumb.value);
    productThumb.value = '';
  }
  productThumbFailed.value = false;
  closePreview();
}

/** 关闭大图预览并回收它的 URL */
function closePreview() {
  if (preview.value.url) {
    URL.revokeObjectURL(preview.value.url);
  }
  preview.value = { url: '', title: '' };
}

/**
 * 加载缩略图：**有界并发**（默认 4 路）。
 *
 * <p>为什么不是串行 <code>for await</code>：附件缩略图是原图直读，单张可能 1~3MB，
 * 串行 12 张在慢网下要十几秒（实测"打开抽屉后一直显示读取中"，看着像坏了）。
 * 4 路并发能把等待压到 1/3 左右，同时不至于把带宽打满。</p>
 *
 * <p>失败的格子标"读取失败"，**不会永远停在"读取中…"**——那是假状态。</p>
 *
 * @param list 要加载的附件
 */
async function loadThumbs(list: CpTaskFileVO[]) {
  const queue = list.filter((f) => !thumbs.value[thumbKey(f)] && !failedThumbs.value[thumbKey(f)]);
  thumbTotal.value = list.length;
  thumbDone.value = list.length - queue.length;
  const workers = Array.from({ length: Math.min(THUMB_CONCURRENCY, queue.length) }, async () => {
    for (;;) {
      const file = queue.shift();
      if (!file) {
        return;
      }
      const key = thumbKey(file);
      try {
        // R24：网格用缩略图（几十 KB），点开大图才取原图。理由见 api 里的注释：
        // 原图 6.7MB/张、且此前没有缓存头，抽屉一开就是几十 MB。
        const url = await fetchCreativeFileThumbnailBlobUrl(
          props.taskId as string | number,
          file.fileId as string | number
        );
        putUrl(key, url);
      } catch (e) {
        failedThumbs.value = { ...failedThumbs.value, [key]: true };
        failedReasons.value = {
          ...failedReasons.value,
          [key]: e instanceof Error ? e.message : String(e)
        };
      } finally {
        thumbDone.value += 1;
      }
    }
  });
  await Promise.all(workers);
}

/** 加载数据（打开抽屉时调用；也可手动刷新） */
async function load() {
  if (!props.taskId) {
    return;
  }
  loading.value = true;
  error.value = '';
  loadErrors.value = [];
  try {
    const id = props.taskId;
    const failed: string[] = [];
    const [fileRes, genRes, sbRes, pageRes, imgRes] = await Promise.all([
      listCreativeFiles(id).catch(() => {
        failed.push('附件');
        return null;
      }),
      listGenerations(id).catch(() => {
        failed.push('出图候选');
        return null;
      }),
      // 分镜只用来把 screenId 翻成屏号，取不到不影响主数据（屏号会显示"未归属屏"）
      getStoryboard(id).catch(() => null),
      getDetailPage(id).catch(() => {
        failed.push('排版版本');
        return null;
      }),
      // 产品图元信息：取不到就按"未配置"处理（页面别的地方也一样）
      getProjectProductImage(id).catch(() => null)
    ]);
    loadErrors.value = failed;
    files.value = fileRes?.data || [];
    generations.value = (genRes?.data || []).toSorted((a, b) =>
      String(b.createTime || '').localeCompare(String(a.createTime || ''))
    );
    storyboard.value = sbRes?.data ?? null;
    const page: DpDetailPageVO | null = pageRes?.data ?? null;
    versions.value = page?.versions || [];
    const img = imgRes?.data as { fileName?: string; configured?: boolean } | undefined;
    productImage.value = img?.configured ? img : null;

    // 缩略图：产品图 1 张 + 附件前 12 张（其余点按钮再加载）
    thumbLimit.value = THUMB_LIMIT;
    if (productImage.value) {
      try {
        productThumb.value = await fetchProductImageBlobUrl(id);
      } catch (e) {
        productThumb.value = '';
        productThumbFailed.value = true;
      }
    }
    await loadThumbs(shownImages.value);
  } finally {
    loading.value = false;
  }
}

/** 点「看大图」取候选原图 */
async function openGeneration(gen: DpGenerationVO) {
  closePreview();
  try {
    const url = await fetchGenerationPreviewBlobUrl(gen.id as string | number);
    preview.value = { url, title: `${screenLabel(gen.screenId)} · 候选 ${gen.candidateNo ?? '-'}` };
  } catch (e) {
    error.value = '候选原图读取失败';
  }
}

/** 点「看长图」取版本长图 */
async function openVersion(ver: DpDetailPageVersionVO) {
  closePreview();
  try {
    const url = await fetchDetailPreviewBlobUrl(props.taskId as string | number, ver.id as string | number);
    preview.value = { url, title: `v${ver.version ?? '-'} · ${ver.kind || '版本'}` };
  } catch (e) {
    error.value = '版本长图读取失败';
  }
}

/** 手动刷新：先清干净再拉 */
async function reload() {
  releaseAll();
  await load();
}

function onOpen() {
  // 与上面 `visible` 的自愈监听可能在同一拍各触发一次（谁先谁后取决于 watcher 队列顺序：
  // onOpen 先跑则自愈被 `loading` 挡住，自愈先跑则这里会重复发一遍）。
  // 生产实测（2026-09-30，1680×1000 真机）：一开抽屉 5 个接口**各被打了 2 次**。
  // 附件是原图直读（850KB/张），重复一次就是双倍慢，所以这里也要挡。
  if (loading.value && !files.value.length && !generations.value.length) {
    return;
  }
  void load();
}

function onClosed() {
  releaseAll();
}

// 缩略图上限变化（点"加载其余"）时补拉
watch(thumbLimit, () => {
  void loadThumbs(shownImages.value);
});

// 切项目：先把上一个项目的资源全回收，避免串图；已打开时重新加载
watch(
  () => props.taskId,
  () => {
    if (visible.value) {
      void reload();
    }
  }
);

/**
 * 自愈：抽屉可能在 `taskId` 还没到位时就被打开（指引线挂载早于页面数据），
 * 那时 `load()` 会直接返回、界面停在"还没有图片附件"——看起来像项目真的没有附件。
 * 这里在 `taskId` 到位且**还没加载过任何数据**时补一次加载。
 *
 * R21 补强（`visible` 一并入参 + `immediate`）：抽屉**在已经可见的状态下被重新挂载**时
 * （父级重渲染换掉实例），`@open` 不会再触发，新实例手上没有任何数据 → 三个段全空、
 * 而且没有失败提示，就是一个**假的空态**。R21 回归验收里偶发过 6 项失败、单独重跑却是好的，
 * 根因就是它。把 `visible` 纳入监听并 `immediate` 一次，重挂载当刻就会补加载。
 */
watch(
  () => [visible.value, props.taskId] as const,
  () => {
    if (!visible.value || !props.taskId || loading.value) {
      return;
    }
    if (!files.value.length && !generations.value.length) {
      void load();
    }
  },
  { immediate: true, flush: 'post' }
);
</script>

<style scoped lang="scss">
.drawer {
  padding: 0 4px 20px;
}

.drawer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--line);

  .drawer-title {
    color: var(--t1);
    font-size: 14px;
    font-weight: 600;
  }

  .drawer-sub {
    color: var(--t3);
    font-size: 12px;
  }

  .spacer {
    flex: 1;
  }
}

.drawer-note {
  margin: 8px 0 12px;
  color: var(--t3);
  font-size: 11px;
  line-height: 1.7;
}

.section {
  margin-top: 14px;

  .sec-head {
    display: flex;
    align-items: baseline;
    gap: 8px;
    color: var(--t1);
    font-size: 13px;
    font-weight: 600;
  }

  .sec-count {
    color: var(--t3);
    font-size: 11px;
    font-weight: 400;
  }

  .empty {
    margin: 6px 0;
    color: var(--t3);
    font-size: 12px;
  }

  .more {
    margin-top: 6px;
  }
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.thumb {
  position: relative;
  width: 92px;

  img,
  .thumb-ph {
    width: 92px;
    height: 92px;
    object-fit: cover;
    border: 1px solid var(--line);
    border-radius: 6px;
    background: var(--sunken);
  }

  .thumb-ph {
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--t3);
    font-size: 11px;
  }

  .thumb-name {
    display: block;
    margin-top: 3px;
    color: var(--t3);
    font-size: 10px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .thumb-tag {
    position: absolute;
    top: 4px;
    left: 4px;
    padding: 0 5px;
    border-radius: 999px;
    background: rgba(10, 12, 16, 0.72);
    border: 1px solid var(--line);
    color: var(--t2);
    font-size: 10px;

    &.is-product {
      color: #67c23a;
      border-color: rgba(103, 194, 58, 0.4);
    }
  }
}

.rows {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px dashed var(--line);
  color: var(--t2);
  font-size: 12px;

  &:last-child {
    border-bottom: none;
  }

  .row-main {
    color: var(--t1);
  }

  .row-time {
    color: var(--t3);
    font-size: 11px;
  }

  .spacer {
    flex: 1;
  }
}

.badge {
  flex: none;
  padding: 0 6px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--t3);
  font-size: 10px;

  &.is-ok {
    color: #67c23a;
    border-color: rgba(103, 194, 58, 0.35);
  }

  &.is-bad {
    color: #f56c6c;
    border-color: rgba(245, 108, 108, 0.35);
  }

  &.is-warn {
    color: #e6a23c;
    border-color: rgba(230, 162, 60, 0.35);
  }
}

.err {
  margin: 12px 0 0;
  color: #e6a23c;
  font-size: 12px;
}

.hint {
  margin: 6px 0 0;
  color: #e6a23c;
  font-size: 11px;
  line-height: 1.7;
}

.preview-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(5, 6, 9, 0.88);
  cursor: zoom-out;

  img {
    max-width: 86vw;
    max-height: 82vh;
    border: 1px solid var(--line);
    border-radius: 8px;
  }

  .preview-title {
    margin: 0;
    color: var(--t2);
    font-size: 12px;
  }
}
</style>

<!-- 抽屉是 teleport 到 body 的，token 必须显式 include（R15 踩过的坑：.studio 只在页面组件作用域里）。
     这里复用 tokens-studio.scss 的 mixin，不抄字面量。 -->
<style lang="scss">
@use '@/assets/styles/tokens-studio.scss' as studio;

.studio-drawer.el-drawer {
  @include studio.studio-tokens;

  background: var(--elevated);
  color: var(--t2);
  border-left: 1px solid var(--line);

  .el-drawer__body {
    padding: 14px 16px;
    overflow-y: auto;
  }
}
</style>
