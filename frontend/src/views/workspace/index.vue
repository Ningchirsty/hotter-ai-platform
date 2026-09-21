<template>
  <div class="p-2 app-container workspace-page">
    <PageHeading title="我的工作台" subtitle="连接岗位能力，开启今天的工作" />

    <!-- 花卉铺满整个 hero 面板；文案与页脚浮在其上，靠左侧珍珠白蒙版保证可读性。 -->
    <section class="ws-hero" aria-label="工作台引导">
      <div ref="flowerRoot" class="ws-hero-stage" aria-hidden="true">
        <img v-if="sceneFallback" class="ws-hero-fallback" :src="fallbackImage" alt="" />
        <canvas ref="flowerCanvas" class="ws-hero-canvas"></canvas>
      </div>
      <div class="ws-hero-scrim" aria-hidden="true"></div>

      <div class="ws-hero-copy">
        <p class="ws-eyebrow">A SPACE FOR POSSIBILITY</p>
        <h2 class="ws-hero-title">{{ HERO_TITLE }}</h2>
        <p class="ws-hero-desc">属于你的工具、协作与业务，在这里连接。</p>
        <el-button type="primary" @click="openTool(VIDEO_CREATION)">进入 AI工具</el-button>
      </div>

      <div class="ws-hero-foot">
        <span class="ws-hero-foot-text">左侧是你每天会用到的入口，右侧是今天继续的线索。</span>
      </div>
    </section>

    <!-- 按权限呈现「二级菜单」能力；含下级的二级菜单点击后向下展开三级菜单 -->
    <section class="ws-section">
      <div class="ws-section-head">
        <h2>为你的岗位准备</h2>
      </div>
      <div class="ws-tools">
        <div v-for="group in capabilityGroups" :key="group.key" class="ws-tool-wrap">
          <button
            type="button"
            class="ws-tool"
            :class="{ 'is-open': openKey === group.key }"
            :aria-expanded="group.children.length ? openKey === group.key : undefined"
            @click="activateGroup(group)"
          >
            <span class="ws-tool-icon">
              <svg-icon :icon-class="group.icon" />
            </span>
            <el-icon class="ws-tool-arrow">
              <ArrowDown v-if="group.children.length" />
              <TopRight v-else />
            </el-icon>
            <h3>{{ group.title }}</h3>
            <p>
              {{ group.kicker }}
              <template v-if="group.children.length"> · {{ group.children.length }} 个功能</template>
            </p>
          </button>

          <div v-if="group.children.length && openKey === group.key" class="ws-sub">
            <button v-for="kid in group.children" :key="kid.path" type="button" class="ws-sub-item" @click="openTool(kid.path)">
              <svg-icon :icon-class="kid.icon" />
              <span>{{ kid.title }}</span>
            </button>
          </div>
        </div>
      </div>
    </section>

    <div class="ws-bottom">
      <section class="ws-panel">
        <div class="ws-panel-head">
          <h2>待我处理</h2>
          <span v-if="pendingHint" class="ws-panel-hint">{{ pendingHint }}</span>
        </div>

        <div v-if="pendingLoading" class="ws-loading" role="status">
          <span class="ws-spinner"></span>
          <span>正在载入待办任务</span>
        </div>

        <div v-else-if="pendingError" class="ws-fallback" role="alert">
          <el-icon class="ws-fallback-icon"><WarningFilled /></el-icon>
          <p>{{ pendingError }}</p>
          <el-button plain size="small" @click="loadPending()">重新加载</el-button>
          <el-button link type="primary" @click="openTool(TASK_WAITING)">前往审批协同</el-button>
        </div>

        <div v-else-if="!pendingTasks.length" class="ws-empty">
          <p>当前没有等待你处理的审批任务。</p>
          <el-button link type="primary" @click="openTool(TASK_WAITING)">前往审批协同处理</el-button>
        </div>

        <ul v-else class="ws-list">
          <li v-for="(task, index) in pendingTasks" :key="task.id" class="ws-row">
            <span class="ws-row-icon">
              <el-icon><component :is="pendingIcons[index % pendingIcons.length]" /></el-icon>
            </span>
            <div class="ws-row-copy">
              <h3>{{ task.title }}</h3>
              <p>{{ task.subtitle }}</p>
            </div>
            <span class="ws-badge">待审批</span>
            <el-button link type="primary" size="small" @click="openTool(TASK_WAITING)">查看</el-button>
          </li>
        </ul>

        <el-button v-if="pendingTasks.length" class="ws-panel-more" link type="primary" @click="openTool(TASK_WAITING)">
          查看全部
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </section>

      <section class="ws-panel">
        <div class="ws-panel-head">
          <h2>最近使用</h2>
          <span class="ws-panel-hint">继续你的工作</span>
        </div>

        <ul v-if="recentViews.length" class="ws-list">
          <li v-for="view in recentViews" :key="view.key" class="ws-row">
            <span class="ws-row-icon">
              <svg-icon :icon-class="view.icon" />
            </span>
            <div class="ws-row-copy">
              <h3>{{ view.title }}</h3>
              <p>{{ view.path }}</p>
            </div>
            <el-button link type="primary" size="small" :aria-label="`打开${view.title}`" @click="openTool(view.path)">
              <el-icon><TopRight /></el-icon>
            </el-button>
          </li>
        </ul>

        <div v-else class="ws-empty">
          <p>还没有打开过其他页面，从上方工具卡开始吧。</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts" name="Index">
import type { Component } from 'vue';
import type { RouteLocationNormalized, RouteRecordRaw } from 'vue-router';
import { ArrowDown, ArrowRight, DataAnalysis, Files, TopRight, View, WarningFilled } from '@element-plus/icons-vue';
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue';
import { pageByTaskWait } from '@/api/workflow/task';
import { createFloralScene } from '@/components/FloralLogin/floral-scene.js';
import fallbackImage from '@/components/FloralLogin/flower-fallback.png';
import { usePermissionStore } from '@/store/modules/permission';
import { useTagsViewStore } from '@/store/modules/tagsView';

type CapabilityItem = {
  title: string;
  path: string;
  icon: string;
};

type CapabilityGroup = {
  key: string;
  title: string;
  kicker: string;
  path: string;
  icon: string;
  children: CapabilityItem[];
};

type PendingTask = {
  id: string | number;
  title: string;
  subtitle: string;
};

// 视频创作页面已下沉到「视频创作」目录之下（图像创作也挂在该目录下），路径随之变为 /video-creation/video
const VIDEO_CREATION = '/ai-tools/video-creation/video';
const TASK_WAITING = '/approval/task/taskWaiting';
// hero 标题固定文案（设计稿要求一行呈现，不换行），非业务数据。
const HERO_TITLE = '让灵感绽放，让工作从容发生。';

const router = useRouter();
const tagsViewStore = useTagsViewStore();
const permissionStore = usePermissionStore();

const openTool = (path: string) => {
  router.push(path);
};

/* ---------------- 为你的岗位准备：直接来自该用户真实菜单路由的「二级菜单」 ----------------
 * 二级菜单 = 一级分类（AI工具/业务应用/审批协同/管理中心）的直接子项；
 * 若二级菜单本身还是目录，则它下面的是三级菜单，点击二级卡片向下展开，不预先铺在首页。
 * 全部取自 permissionStore 的路由树，所以天然按权限过滤，无需在本页写死清单。 */
const normalizeIcon = (icon?: unknown) => {
  const name = typeof icon === 'string' ? icon : '';
  return !name || name === '#' ? 'list' : name;
};

const joinPath = (base: string, path: string) => {
  if (!path) return base;
  if (path.startsWith('/')) return path;
  return `${base.replace(/\/+$/, '')}/${path}`;
};

const isVisibleRoute = (route: RouteRecordRaw) => !route.hidden && !!route.meta?.title && !route.meta?.link;

const capabilityGroups = computed<CapabilityGroup[]>(() => {
  const groups: CapabilityGroup[] = [];
  for (const top of permissionStore.getSidebarRoutes() as RouteRecordRaw[]) {
    // 跳过静态 Layout 包装（path 为空的空壳路由）
    if (!top.path || top.hidden) continue;
    for (const child of (top.children || []) as RouteRecordRaw[]) {
      if (!isVisibleRoute(child)) continue;
      const childPath = joinPath(top.path, child.path);
      if (childPath === '/index' || childPath === '/') continue;
      const grandChildren = ((child.children || []) as RouteRecordRaw[]).filter(isVisibleRoute);
      groups.push({
        key: childPath,
        title: String(child.meta?.title),
        kicker: String(top.meta?.title || ''),
        path: childPath,
        icon: normalizeIcon(child.meta?.icon),
        children: grandChildren.map(kid => ({
          title: String(kid.meta?.title),
          path: joinPath(childPath, kid.path),
          icon: normalizeIcon(kid.meta?.icon)
        }))
      });
    }
  }
  return groups;
});

// 展开状态：只有一个二级菜单保持展开，避免首页被铺满
const openKey = ref('');
const activateGroup = (group: CapabilityGroup) => {
  if (!group.children.length) {
    openTool(group.path);
    return;
  }
  openKey.value = openKey.value === group.key ? '' : group.key;
};

/* ---------------- 待我处理：复用现有审批待办接口 /workflow/task/pageByTaskWait ---------------- */
const pendingLoading = ref(true);
const pendingError = ref('');
const pendingTotal = ref(0);
const pendingTasks = ref<PendingTask[]>([]);
const pendingIcons: Component[] = [Files, DataAnalysis, View];

const pendingHint = computed(() => {
  if (pendingLoading.value) return '正在载入';
  if (pendingError.value) return '接口不可用';
  return `共 ${pendingTotal.value} 条`;
});

const loadPending = async () => {
  pendingLoading.value = true;
  pendingError.value = '';
  try {
    const resp = await pageByTaskWait({ pageNum: 1, pageSize: 5 });
    const rows = resp.data?.rows ?? [];
    pendingTotal.value = resp.data?.total ?? rows.length;
    pendingTasks.value = rows.map(row => ({
      id: row.id,
      title: row.businessTitle || row.flowName || '未命名业务',
      subtitle: [row.flowName, row.nodeName].filter(Boolean).join(' · ')
    }));
  } catch {
    pendingTasks.value = [];
    pendingTotal.value = 0;
    pendingError.value = '待办任务暂时无法获取，请稍后重试。';
  } finally {
    pendingLoading.value = false;
  }
};

/* ---------------- 最近使用：读取多标签页的真实访问记录 ---------------- */
const RECENT_LIMIT = 5;

const recentViews = computed(() => {
  // visitedViews 按“打开顺序”追加，尾部即最近访问；这里取出窗口后按索引倒序读取，
  // 不用 Array#reverse/toReversed，保持与项目 Chromium 87+ 目标一致。
  const views = (tagsViewStore.visitedViews as RouteLocationNormalized[]).filter(
    view => view.path && view.path !== '/index' && !view.meta?.affix
  );
  const start = Math.max(0, views.length - RECENT_LIMIT);
  const recent: { key: string; path: string; title: string; icon: string }[] = [];
  for (let index = views.length - 1; index >= start; index--) {
    const view = views[index];
    recent.push({
      key: view.fullPath || view.path,
      path: view.fullPath || view.path,
      title: (view.meta?.title as string) || (view.name as string) || view.path,
      // 图标沿用该页面菜单里的图标，和左侧导航保持一致
      icon: normalizeIcon(view.meta?.icon)
    });
  }
  return recent;
});

/* ---------------- hero 花卉场景：复用登录页的 canvas 场景 ----------------
 * 按需求：进入工作台即无限播放，不提供暂停按钮，也不受系统「减少动态效果」影响
 * （场景自身的帧循环是循环调度的，只要保持 playing=true 就持续动）。
 * WebGL 上下文丢失会退化成静态图，这里自动重建实例（最多 3 次），避免动画永久停住。 */
const flowerRoot = ref<HTMLElement | null>(null);
const flowerCanvas = ref<HTMLCanvasElement | null>(null);
const sceneFallback = ref(false);

const MAX_SCENE_RECOVERY = 3;
let scene: ReturnType<typeof createFloralScene> | null = null;
let recoveryAttempts = 0;
let recoveryTimer = 0;

const mountScene = () => {
  if (!flowerRoot.value || !flowerCanvas.value) return;
  // 上下文丢失时场景会把 canvas 隐藏，重建前先恢复显示
  flowerCanvas.value.style.visibility = '';
  sceneFallback.value = false;
  scene = createFloralScene(flowerRoot.value, flowerCanvas.value, {
    playing: true,
    onFallback: () => {
      sceneFallback.value = true;
      scene?.destroy();
      scene = null;
      if (recoveryAttempts < MAX_SCENE_RECOVERY) {
        recoveryAttempts += 1;
        window.clearTimeout(recoveryTimer);
        recoveryTimer = window.setTimeout(mountScene, 1500);
      }
    }
  });
};

onMounted(async () => {
  await nextTick();
  loadPending();
  mountScene();
});

// 页面被 keep-alive 缓存到后台时停帧（用户看不到），回到前台立刻继续无限播放。
onDeactivated(() => scene?.setPlaying(false));
onActivated(() => {
  if (scene) {
    scene.setPlaying(true);
  } else {
    mountScene();
  }
});

onBeforeUnmount(() => {
  window.clearTimeout(recoveryTimer);
  scene?.destroy();
  scene = null;
});
</script>

<style lang="scss" scoped>
.workspace-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
  min-width: 0;
}

/* ---------------- hero ---------------- */
.ws-hero {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  min-height: 198px;
  padding: 30px 28px 44px;
  border: 1px solid var(--app-surface-border);
  border-radius: var(--app-radius-base);
  background:
    radial-gradient(120% 150% at 88% 12%, var(--app-accent-soft), transparent 62%),
    linear-gradient(115deg, var(--app-surface-bg), var(--app-surface-bg) 55%, var(--app-accent-soft));
  box-shadow: var(--app-shadow-sm);
}

.ws-hero-copy {
  position: relative;
  z-index: 2;
  width: 62%;
  min-width: 0;
}

.ws-eyebrow {
  margin: 0 0 7px;
  font-size: 10px;
  letter-spacing: 2px;
  color: var(--app-text-muted);
}

.ws-hero-title {
  margin: 0 0 8px;
  /* 设计稿要求整句一行呈现；窄屏用 clamp 缩字号，避免换行或横向溢出 */
  font-size: clamp(15px, 2.05vw, 25px);
  font-weight: 500;
  line-height: 1.55;
  letter-spacing: 0.7px;
  white-space: nowrap;
  color: var(--app-text-title);
}

.ws-hero-desc {
  margin: 0 0 20px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text-muted);
}

/* 花卉画布铺满整个 hero 面板；两朵花分别落在左下与右上，横向分布在整个动态区 */
.ws-hero-stage {
  position: absolute;
  inset: 0;
  width: 100%;
  z-index: 0;
}

/* 文案区蒙版：左侧偏实保证可读，同时向下渐隐——左下那朵花因此仍能露出来，
 * 两朵花分列左下与右上，分布在整个 hero 动态区。
 * 用 rgba 而不是 color-mix，避免低版本浏览器不支持导致蒙版失效。 */
.ws-hero-scrim {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background: linear-gradient(
    100deg,
    rgba(255, 255, 255, 1) 0%,
    rgba(255, 255, 255, 0.88) 30%,
    rgba(255, 255, 255, 0.42) 50%,
    rgba(255, 255, 255, 0) 66%
  );
  mask-image: linear-gradient(180deg, #000 0%, #000 52%, rgba(0, 0, 0, 0) 92%);
  -webkit-mask-image: linear-gradient(180deg, #000 0%, #000 52%, rgba(0, 0, 0, 0) 92%);
}

html.dark .ws-hero-scrim {
  background: linear-gradient(
    100deg,
    rgba(17, 24, 39, 1) 0%,
    rgba(17, 24, 39, 0.88) 30%,
    rgba(17, 24, 39, 0.42) 50%,
    rgba(17, 24, 39, 0) 66%
  );
}

.ws-hero-canvas,
.ws-hero-fallback {
  display: block;
  width: 100%;
  height: 100%;
}

.ws-hero-fallback {
  position: absolute;
  inset: 0;
  object-fit: cover;
  opacity: 0.6;
}

.ws-hero-foot {
  position: absolute;
  right: 14px;
  bottom: 10px;
  left: 28px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.ws-hero-foot-text {
  font-size: 11px;
  line-height: 1.6;
  color: var(--app-text-muted);
}

/* ---------------- 区块标题 ---------------- */
.ws-section-head,
.ws-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 13px;

  h2 {
    margin: 0;
    font-size: 14px;
    font-weight: 500;
    color: var(--app-text-title);
  }
}

.ws-panel-hint {
  font-size: 11px;
  color: var(--app-text-muted);
}

/* ---------------- 岗位能力卡（二级菜单 + 下拉三级） ---------------- */
.ws-tools {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 12px;
  min-width: 0;
}

.ws-tool-wrap {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.ws-tool {
  position: relative;
  display: block;
  width: 100%;
  min-width: 0;
  padding: 17px 15px 16px;
  border: 1px solid var(--app-surface-border);
  border-radius: var(--app-radius-base);
  background: var(--app-surface-bg);
  box-shadow: var(--app-shadow-sm);
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;

  &:hover {
    transform: translateY(-3px);
    border-color: var(--app-accent-strong);
    box-shadow: var(--app-shadow-md);
  }

  &.is-open {
    border-color: var(--app-accent-strong);
    border-bottom-left-radius: 0;
    border-bottom-right-radius: 0;
  }

  h3 {
    margin: 0 0 4px;
    font-size: 13px;
    font-weight: 500;
    color: var(--app-text-title);
  }

  p {
    margin: 0;
    font-size: 11px;
    line-height: 1.7;
    color: var(--app-text-muted);
  }
}

/* 三级菜单：点开二级卡片后向下展开 */
.ws-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 12px 12px;
  border: 1px solid var(--app-accent-strong);
  border-top: 0;
  border-radius: 0 0 var(--app-radius-base) var(--app-radius-base);
  background: var(--app-surface-bg);
  box-shadow: var(--app-shadow-sm);
}

.ws-sub-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border: 1px solid var(--app-surface-border);
  border-radius: 6px;
  background: var(--app-accent-soft);
  color: var(--app-text-title);
  font-size: 11px;
  line-height: 1.6;
  cursor: pointer;
  transition:
    color 0.16s ease,
    border-color 0.16s ease;

  &:hover {
    color: var(--app-accent-strong);
    border-color: var(--app-accent-strong);
  }
}

.ws-tool-icon {
  display: grid;
  place-items: center;
  width: 35px;
  height: 35px;
  margin-bottom: 12px;
  border-radius: 10px;
  background: var(--app-accent-soft);
  color: var(--app-accent-strong);
  font-size: 18px;
}

.ws-tool-arrow {
  position: absolute;
  top: 20px;
  right: 14px;
  color: var(--app-text-muted);
  font-size: 13px;
}

/* ---------------- 两栏面板 ---------------- */
.ws-bottom {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 17px;
  min-width: 0;
}

.ws-panel {
  position: relative;
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--app-surface-border);
  border-radius: var(--app-radius-base);
  background: var(--app-surface-bg);
  box-shadow: var(--app-shadow-sm);
}

.ws-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.ws-row {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
  padding: 13px 6px;
  border-bottom: 1px solid var(--app-surface-border);
  border-radius: var(--app-radius-sm);
  transition: background-color 0.18s ease;

  &:hover {
    background: var(--app-accent-soft);
  }

  &:last-child {
    border-bottom: 0;
    padding-bottom: 1px;
  }
}

.ws-row-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 30px;
  height: 34px;
  border-radius: 8px;
  background: var(--app-accent-soft);
  color: var(--app-accent-strong);
  font-size: 15px;
}

.ws-row-copy {
  flex: 1;
  min-width: 0;

  h3 {
    margin: 0;
    font-size: 12px;
    font-weight: 400;
    color: var(--app-text-title);
    overflow-wrap: anywhere;
  }

  p {
    margin: 3px 0 0;
    font-size: 11px;
    line-height: 1.6;
    color: var(--app-text-muted);
    overflow-wrap: anywhere;
  }
}

.ws-badge {
  flex-shrink: 0;
  padding: 2px 7px;
  border-radius: 5px;
  background: var(--app-accent-soft);
  color: var(--app-text-muted);
  font-size: 10px;
  line-height: 1.6;
  white-space: nowrap;
}

.ws-panel-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
}

/* ---------------- 加载 / 空 / 失败 三态 ---------------- */
.ws-loading {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 28px 6px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.ws-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--app-accent-soft);
  border-top-color: var(--app-accent-strong);
  border-radius: 50%;
  animation: ws-spin 0.9s linear infinite;
}

@keyframes ws-spin {
  to {
    transform: rotate(360deg);
  }
}

.ws-empty,
.ws-fallback {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 22px 6px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.7;

  p {
    margin: 0;
  }
}

.ws-fallback-icon {
  color: var(--app-text-muted);
  font-size: 18px;
}

/* ---------------- 响应式：1024 / 736 / 375 / 320 ---------------- */
@media (max-width: 900px) {
  .ws-bottom {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 1024px) {
  .ws-tools {
    grid-template-columns: repeat(auto-fit, minmax(196px, 1fr));
  }
}

@media (max-width: 768px) {
  .ws-hero {
    padding: 24px 22px 46px;
  }

  .ws-hero-copy {
    width: 78%;
  }

  .ws-hero-stage {
    opacity: 0.82;
  }

  .ws-hero-foot {
    left: 22px;
  }
}

@media (max-width: 640px) {
  .workspace-page {
    gap: 18px;
  }

  .ws-hero {
    min-height: 210px;
    padding: 24px 18px 52px;
  }

  .ws-hero-copy {
    width: 88%;
  }

  .ws-hero-desc {
    max-width: 190px;
    font-size: 12px;
  }

  .ws-hero-stage {
    opacity: 0.5;
  }

  .ws-hero-foot {
    left: 18px;
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }

  .ws-panel {
    padding: 17px 15px;
  }

  /* 能力卡在窄屏变一列 */
  .ws-tools {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }

  .ws-row {
    flex-wrap: wrap;
  }
}

@media (max-width: 375px) {
  .ws-hero-stage {
    opacity: 0.38;
  }

  .ws-hero-foot-text {
    display: none;
  }

  .ws-panel-head {
    align-items: flex-start;
  }
}

/* 系统「减少动态效果」下收敛卡片位移与加载转圈；
 * hero 花卉按需求始终无限播放，这里不再干预 canvas 动画。 */
@media (prefers-reduced-motion: reduce) {
  .ws-tool,
  .ws-row {
    transition: none;
  }

  .ws-tool:hover {
    transform: none;
  }

  .ws-spinner {
    animation: none;
  }
}
</style>
