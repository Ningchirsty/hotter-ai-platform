<template>
  <div class="p-2 app-container workspace-page">
    <PageHeading title="我的工作台" subtitle="连接岗位能力，开启今天的工作" />

    <!-- 花卉只出现在 hero；其余面板保持克制、不做动画。 -->
    <section class="ws-hero" aria-label="工作台引导">
      <div class="ws-hero-copy">
        <p class="ws-eyebrow">A SPACE FOR POSSIBILITY</p>
        <!-- 标题按设计稿在“让灵感绽放，”后换行；用 v-html 避免模板换行被折叠成空格。 -->
        <h2 class="ws-hero-title" v-html="HERO_TITLE"></h2>
        <p class="ws-hero-desc">属于你的工具、协作与业务，在这里连接。</p>
        <el-button type="primary" @click="openTool(VIDEO_CREATION)">进入 AI工具</el-button>
      </div>

      <div ref="flowerRoot" class="ws-hero-stage" aria-hidden="true">
        <img v-if="sceneFallback" class="ws-hero-fallback" :src="fallbackImage" alt="" />
        <canvas ref="flowerCanvas" class="ws-hero-canvas"></canvas>
      </div>

      <div class="ws-hero-foot">
        <span class="ws-hero-foot-text">左侧是你每天会用到的入口，右侧是今天继续的线索。</span>
        <button
          v-if="!sceneFallback"
          class="ws-motion"
          type="button"
          :aria-pressed="flowerPlaying"
          @click="toggleMotion"
        >
          <el-icon>
            <VideoPause v-if="flowerPlaying" />
            <VideoPlay v-else />
          </el-icon>
          {{ flowerPlaying ? '暂停花卉动态' : '播放花卉动态' }}
        </button>
      </div>
    </section>

    <section class="ws-section">
      <div class="ws-section-head">
        <h2>为你的岗位准备</h2>
      </div>
      <div class="ws-tools">
        <button v-for="tool in toolCards" :key="tool.path" type="button" class="ws-tool" @click="openTool(tool.path)">
          <span class="ws-tool-icon">
            <el-icon><component :is="tool.icon" /></el-icon>
          </span>
          <el-icon class="ws-tool-arrow"><TopRight /></el-icon>
          <h3>{{ tool.name }}</h3>
          <p>{{ tool.desc }}</p>
        </button>
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
              <el-icon><component :is="view.icon" /></el-icon>
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
import type { RouteLocationNormalized } from 'vue-router';
import {
  ArrowRight,
  Connection,
  DataAnalysis,
  Download,
  Files,
  Film,
  Grid,
  MagicStick,
  User,
  VideoPause,
  VideoPlay,
  View,
  WarningFilled
} from '@element-plus/icons-vue';
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue';
import { pageByTaskWait } from '@/api/workflow/task';
import { createFloralScene } from '@/components/FloralLogin/floral-scene.js';
import fallbackImage from '@/components/FloralLogin/flower-fallback.png';
import { useTagsViewStore } from '@/store/modules/tagsView';

type ToolCard = {
  name: string;
  desc: string;
  path: string;
  icon: Component;
};

type PendingTask = {
  id: string | number;
  title: string;
  subtitle: string;
};

const VIDEO_CREATION = '/ai-tools/video-creation';
const TASK_WAITING = '/approval/task/taskWaiting';
// hero 标题固定文案（设计稿要求在此处换行），非业务数据。
const HERO_TITLE = '让灵感绽放，<br>让工作从容发生。';

// 生产真实路径；权限由路由守卫处理，本页不做写死的权限判断。
const toolCards: ToolCard[] = [
  { name: '视频创作', desc: '让创意成为动态画面', path: VIDEO_CREATION, icon: Film },
  { name: 'AI助手', desc: '构思、写作与日常协助', path: '/ai-tools/aichat', icon: MagicStick },
  { name: '工作流', desc: '把重复步骤编排成流程', path: '/ai-tools/workflow', icon: Connection },
  { name: '人才档案', desc: '检索、筛选与档案维护', path: '/business/talent/profile', icon: User },
  { name: '简历与附件', desc: '查看人才关联文件', path: '/business/talent/attachment', icon: Files },
  { name: '重复人才预警', desc: '发现重复记录', path: '/business/talent/duplicate', icon: DataAnalysis },
  { name: 'Excel导出中心', desc: '管理人才导出任务', path: '/business/talent/export', icon: Download },
  { name: '敏感操作审计', desc: '追溯授权范围内的操作', path: '/business/talent/audit', icon: View },
  { name: 'AI能力目录', desc: '查看平台已登记的能力', path: '/admin-center/ai-gov/capability', icon: Grid }
];

const router = useRouter();
const tagsViewStore = useTagsViewStore();

const openTool = (path: string) => {
  router.push(path);
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
const RECENT_ICONS: Record<string, Component> = {
  '/ai-tools/video-creation': Film,
  '/ai-tools/aichat': MagicStick,
  '/ai-tools/workflow': Connection,
  '/business/talent/profile': User,
  '/business/talent/attachment': Files,
  '/business/talent/export': Download,
  '/admin-center/ai-gov/capability': Grid,
  '/approval/task/taskWaiting': DataAnalysis,
  '/approval/task/processInstance': DataAnalysis,
  '/approval/task/taskFinish': DataAnalysis,
  '/approval/task/taskCopyList': DataAnalysis
};

const recentViews = computed(() => {
  // visitedViews 按“打开顺序”追加，尾部即最近访问；这里取出窗口后按索引倒序读取，
  // 不用 Array#reverse/toReversed，保持与项目 Chromium 87+ 目标一致。
  const views = (tagsViewStore.visitedViews as RouteLocationNormalized[]).filter(
    view => view.path && view.path !== '/index' && !view.meta?.affix
  );
  const start = Math.max(0, views.length - RECENT_LIMIT);
  const recent: { key: string; path: string; title: string; icon: Component }[] = [];
  for (let index = views.length - 1; index >= start; index--) {
    const view = views[index];
    recent.push({
      key: view.fullPath || view.path,
      path: view.fullPath || view.path,
      title: (view.meta?.title as string) || (view.name as string) || view.path,
      icon: RECENT_ICONS[view.path] ?? View
    });
  }
  return recent;
});

/* ---------------- hero 花卉场景：复用登录页的 canvas 场景 ---------------- */
const flowerRoot = ref<HTMLElement | null>(null);
const flowerCanvas = ref<HTMLCanvasElement | null>(null);
const flowerPlaying = ref(true);
const sceneFallback = ref(false);

let scene: ReturnType<typeof createFloralScene> | null = null;
let motionMedia: MediaQueryList | null = null;

const toggleMotion = () => {
  flowerPlaying.value = !flowerPlaying.value;
  scene?.setPlaying(flowerPlaying.value);
};

const motionPreferenceChanged = (event: MediaQueryListEvent) => {
  flowerPlaying.value = !event.matches;
  scene?.setPlaying(flowerPlaying.value);
};

onMounted(async () => {
  await nextTick();
  loadPending();
  motionMedia = window.matchMedia('(prefers-reduced-motion: reduce)');
  flowerPlaying.value = !motionMedia.matches;
  motionMedia.addEventListener?.('change', motionPreferenceChanged);
  if (!flowerRoot.value || !flowerCanvas.value) return;
  scene = createFloralScene(flowerRoot.value, flowerCanvas.value, {
    playing: flowerPlaying.value,
    onFallback: () => {
      sceneFallback.value = true;
    }
  });
});

// 页面被 keep-alive 缓存时停掉动画，避免后台持续占用 GPU。
onDeactivated(() => scene?.setPlaying(false));
onActivated(() => scene?.setPlaying(flowerPlaying.value));

onBeforeUnmount(() => {
  motionMedia?.removeEventListener?.('change', motionPreferenceChanged);
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
  z-index: 1;
  width: 68%;
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
  font-size: 25px;
  font-weight: 500;
  line-height: 1.55;
  letter-spacing: 0.7px;
  color: var(--app-text-title);
}

.ws-hero-desc {
  margin: 0 0 20px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text-muted);
}

.ws-hero-stage {
  position: absolute;
  inset: 0 0 0 auto;
  width: 280px;
  z-index: 0;
  mask-image: linear-gradient(90deg, transparent, #000 12%);
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 12%);
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

.ws-motion {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  padding: 3px 10px;
  border: 1px solid var(--app-surface-border);
  border-radius: 999px;
  background: var(--app-accent-soft);
  color: var(--app-text-muted);
  font-size: 11px;
  line-height: 1.6;
  cursor: pointer;
  transition:
    color 0.18s ease,
    border-color 0.18s ease;

  &:hover {
    color: var(--app-accent-strong);
    border-color: var(--app-accent-strong);
  }
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

/* ---------------- 岗位工具卡 ---------------- */
.ws-tools {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 12px;
  min-width: 0;
}

.ws-tool {
  position: relative;
  display: block;
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

  .ws-hero-title {
    font-size: 23px;
  }

  .ws-hero-stage {
    width: 210px;
    opacity: 0.78;
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

  .ws-hero-title {
    font-size: 22px;
  }

  .ws-hero-desc {
    max-width: 190px;
    font-size: 12px;
  }

  .ws-hero-stage {
    width: 150px;
    opacity: 0.45;
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

  /* 工具卡在窄屏变一列 */
  .ws-tools {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }

  .ws-row {
    flex-wrap: wrap;
  }
}

@media (max-width: 375px) {
  .ws-hero-title {
    font-size: 20px;
    line-height: 1.5;
  }

  .ws-hero-stage {
    opacity: 0.35;
  }

  .ws-hero-foot-text {
    display: none;
  }

  .ws-panel-head {
    align-items: flex-start;
  }
}

/* 尊重系统的“减弱动态效果”设置：静态呈现，不做位移与循环动画 */
@media (prefers-reduced-motion: reduce) {
  .ws-tool,
  .ws-row,
  .ws-motion {
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
