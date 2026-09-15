<template>
  <div class="studio video-tasks">
    <!-- 页头 -->
    <div class="vt-head">
      <div>
        <h3 class="vt-title">视频任务</h3>
        <p class="vt-sub">AI 视频生成任务全生命周期：排队 · 生成 · 完成 · 失败重试（演示数据，后端接口待接入）</p>
      </div>
      <el-button class="vt-create" type="primary" @click="goCreate">
        <span class="vt-create-star">✦</span>
        去创作
      </el-button>
    </div>

    <!-- 搜索 + 状态筛选 -->
    <div class="vt-toolbar">
      <input v-model="keyword" class="vt-search" placeholder="🔍 搜索任务ID / 标题 / 描述" @input="page = 1" />
      <div class="vt-filters">
        <button
          v-for="f in filters"
          :key="f.key"
          class="vt-filter"
          :class="{ on: activeFilter === f.key }"
          @click="
            activeFilter = f.key;
            page = 1;
          "
        >
          {{ f.label }}
          <em>{{ f.count }}</em>
        </button>
      </div>
    </div>

    <!-- 任务表 -->
    <div class="vt-table-wrap">
      <table class="vt-table">
        <thead>
          <tr>
            <th>任务</th>
            <th>能力 / 模型</th>
            <th>状态</th>
            <th>提交时间</th>
            <th>耗时 / 计费</th>
            <th class="vt-ops-col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in pagedTasks" :key="t.id">
            <td>
              <div class="vt-task-cell">
                <div
                  v-hasPermi="['ai:video:query']"
                  class="vt-thumb"
                  role="button"
                  tabindex="0"
                  aria-label="查看任务详情"
                  @click="openPreview(t)"
                  @keydown.enter.space.prevent="openPreview(t)"
                >
                  <span v-if="t.status === 'DONE'" class="vt-thumb-play">▶</span>
                </div>
                <div class="vt-task-meta">
                  <b class="vt-task-title">{{ t.title }}</b>
                  <i class="vt-task-id">{{ t.id }}</i>
                  <div v-if="t.status === 'RUNNING'" class="vt-prog" :data-prog="t.id">
                    <i :style="{ width: progressOf(t) + '%' }"></i>
                  </div>
                </div>
              </div>
            </td>
            <td>
              <div class="vt-cap">{{ t.cap }}</div>
              <i class="vt-model">{{ t.model }}</i>
            </td>
            <td>
              <span class="vt-pill" :class="pillClass(t.status)">
                <span v-if="t.status === 'RUNNING'" class="vt-pulse"></span>
                {{ statusText(t.status) }}
              </span>
            </td>
            <td>{{ t.t }}</td>
            <td>
              <div>{{ t.dur }}</div>
              <i class="vt-bill">{{ t.bill }}</i>
            </td>
            <td>
              <div class="vt-ops">
                <button v-hasPermi="['ai:video:query']" @click="openPreview(t)">详情</button>
                <button v-if="t.status === 'DONE'" v-hasPermi="['ai:video:query']" @click="openPreview(t)">预览</button>
                <button
                  v-if="t.status === 'QUEUED'"
                  v-hasPermi="['ai:video:cancel']"
                  class="warn"
                  @click="cancelTask(t)"
                >
                  取消
                </button>
                <button v-if="t.status === 'FAILED'" v-hasPermi="['ai:video:retry']" @click="retryTask(t)">
                  ↻ 重试
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="pagedTasks.length === 0">
            <td colspan="6" class="vt-empty">暂无该状态任务</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="vt-pager">
      <span>共 {{ filteredTasks.length }} 条</span>
      <button :disabled="page === 1" @click="page--">‹</button>
      <button v-for="n in totalPages" :key="n" :class="{ cur: n === page }" @click="page = n">{{ n }}</button>
      <button :disabled="page === totalPages" @click="page++">›</button>
    </div>

    <!-- 预览 / 详情弹层 -->
    <div v-if="previewTask" class="vt-mask" @click.self="closePreview">
      <div class="vt-modal" role="dialog" aria-modal="true" aria-labelledby="video-task-dialog-title">
        <div class="vt-player">
          <div class="vt-player-badges">
            <span class="vt-badge hl">成片预览</span>
            <span class="vt-badge">{{ previewTask.tier }}</span>
            <span class="vt-badge">{{ previewTask.len }}</span>
          </div>
          <div class="vt-player-center">
            <button class="vt-playbtn" aria-label="播放成片" @click="hint('播放成片（演示）')"></button>
          </div>
          <div class="vt-player-bar">
            <div>
              <div class="vt-player-name">{{ previewTask.title }}</div>
              <i>{{ previewTask.id }} · {{ previewTask.cap }} · {{ previewTask.model }}</i>
            </div>
            <div class="vt-player-acts">
              <button
                v-hasPermi="['ai:video:download']"
                title="下载"
                aria-label="下载成片"
                @click="hint('开始下载（演示）')"
              >
                ⤓
              </button>
              <button title="分享" @click="hint('分享链接已复制（演示）')">⤴</button>
              <button class="pri" @click="goCreate">✦ 再创作</button>
            </div>
          </div>
        </div>
        <div class="vt-params">
          <div id="video-task-dialog-title" class="vt-params-title">输入参数</div>
          <div class="vt-kv">
            <span>任务ID</span>
            <b>{{ previewTask.id }}</b>
            <span>能力</span>
            <b>{{ previewTask.cap }}</b>
            <span>生成模型</span>
            <b>{{ previewTask.model }}</b>
            <span>计费方式</span>
            <b>{{ previewTask.bill }}</b>
            <span>描述</span>
            <b>{{ previewTask.desc || '—' }}</b>
            <span>输出</span>
            <b>{{ previewTask.tier }} · {{ previewTask.len }}</b>
            <span>耗时</span>
            <b>{{ previewTask.dur }}</b>
          </div>
        </div>
        <button class="vt-modal-close" aria-label="关闭详情" @click="closePreview">✕</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import '@/assets/styles/tokens-studio.scss';

defineOptions({ name: 'AiVideoTasks' });

/** 任务对象形状（后端就绪后由 /ai/tasks 接口替换 mock） */
interface VideoTask {
  id: string;
  title: string;
  cap: string;
  model: string;
  bill: string;
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED';
  t: string;
  dur: string;
  tier: string;
  len: string;
  desc: string;
  startTs?: number;
}

const STATUS_TEXT: Record<VideoTask['status'], string> = {
  QUEUED: '排队中',
  RUNNING: '生成中',
  DONE: '已完成',
  FAILED: '失败'
};
const PILL_CLASS: Record<VideoTask['status'], string> = {
  QUEUED: 'q',
  RUNNING: 'run',
  DONE: 'ok',
  FAILED: 'fail'
};

const nowStr = (): string => {
  const n = new Date();
  const p = (x: number): string => String(x).padStart(2, '0');
  return `今天 ${p(n.getHours())}:${p(n.getMinutes())}`;
};

/** mock 数据（对齐原型 design/ui/app-prototype.html #/studio/tasks） */
const tasks = ref<VideoTask[]>([
  {
    id: 'VIDEO-20260915-018',
    title: '春季宣传片 · 包装特写',
    cap: '首尾帧生视频',
    model: 'MiniMax H3',
    bill: '10 积分/次',
    status: 'RUNNING',
    t: '今天 14:02',
    dur: '—',
    tier: '1080p',
    len: '5 秒',
    desc: '从产品特写缓缓拉远，光影流动。'
  },
  {
    id: 'VIDEO-20260915-017',
    title: '新品发布主视频',
    cap: '首尾帧生视频',
    model: 'H3 Pro',
    bill: '15 积分/次',
    status: 'DONE',
    t: '今天 13:40',
    dur: '4 分 12 秒',
    tier: '1080p',
    len: '5 秒',
    desc: '新品包装 360 度展示，节奏明快。'
  },
  {
    id: 'VIDEO-20260915-016',
    title: '品牌 LOGO 动效',
    cap: '文生视频',
    model: 'WAN 2.1',
    bill: '开源 · 本地 GPU',
    status: 'DONE',
    t: '今天 11:22',
    dur: '3 分 05 秒',
    tier: '720p',
    len: '5 秒',
    desc: '霓虹汇聚成 LOGO。'
  },
  {
    id: 'VIDEO-20260915-015',
    title: '门店氛围短片',
    cap: '首尾帧生视频',
    model: 'MiniMax H3',
    bill: '10 积分/次',
    status: 'DONE',
    t: '今天 10:05',
    dur: '3 分 48 秒',
    tier: '1080p',
    len: '10 秒',
    desc: '门店灯会氛围。'
  },
  {
    id: 'VIDEO-20260914-009',
    title: '门店氛围短片 · 元宵',
    cap: '首尾帧生视频',
    model: 'MiniMax H3',
    bill: '10 积分/次',
    status: 'DONE',
    t: '昨天 18:42',
    dur: '3 分 48 秒',
    tier: '1080p',
    len: '5 秒',
    desc: '元宵灯会。'
  },
  {
    id: 'VIDEO-20260914-008',
    title: '节日礼盒特写',
    cap: '首尾帧生视频',
    model: 'H3 Pro',
    bill: '15 积分/次',
    status: 'DONE',
    t: '昨天 16:20',
    dur: '4 分 02 秒',
    tier: '1080p',
    len: '5 秒',
    desc: '礼盒开箱特写。'
  },
  {
    id: 'VIDEO-20260914-007',
    title: '包装生产线演示',
    cap: '文生视频',
    model: 'CogVideoX',
    bill: '开源 · 本地 GPU',
    status: 'FAILED',
    t: '昨天 15:11',
    dur: '1 分 47 秒',
    tier: '1080p',
    len: '5 秒',
    desc: '产线演示。'
  },
  {
    id: 'VIDEO-20260914-006',
    title: '门店开业短片',
    cap: '运镜视频',
    model: 'LTX-Video',
    bill: '开源 · 本地 GPU',
    status: 'DONE',
    t: '昨天 11:36',
    dur: '3 分 33 秒',
    tier: '720p',
    len: '5 秒',
    desc: '开业现场。'
  }
]);

const keyword = ref('');
const activeFilter = ref<'ALL' | VideoTask['status']>('ALL');
const page = ref(1);
const pageSize = 5;
const previewTask = ref<VideoTask | null>(null);
const nowTick = ref(Date.now());

const filters = computed(() => [
  { key: 'ALL' as const, label: '全部', count: tasks.value.length },
  { key: 'QUEUED' as const, label: '排队中', count: tasks.value.filter(t => t.status === 'QUEUED').length },
  { key: 'RUNNING' as const, label: '生成中', count: tasks.value.filter(t => t.status === 'RUNNING').length },
  { key: 'DONE' as const, label: '已完成', count: tasks.value.filter(t => t.status === 'DONE').length },
  { key: 'FAILED' as const, label: '失败', count: tasks.value.filter(t => t.status === 'FAILED').length }
]);

const filteredTasks = computed(() => {
  const kw = keyword.value.trim();
  return tasks.value.filter(
    t =>
      (activeFilter.value === 'ALL' || t.status === activeFilter.value) &&
      (!kw || t.id.includes(kw) || t.title.includes(kw) || (t.desc || '').includes(kw))
  );
});

const totalPages = computed(() => Math.max(1, Math.ceil(filteredTasks.value.length / pageSize)));
const pagedTasks = computed(() => {
  return filteredTasks.value.slice((page.value - 1) * pageSize, page.value * pageSize);
});
watch(totalPages, total => {
  if (page.value > total) page.value = total;
});

const statusText = (s: VideoTask['status']): string => STATUS_TEXT[s];
const pillClass = (s: VideoTask['status']): string => PILL_CLASS[s];
const QUEUE_DELAY = 3000;
const RUN_DURATION = 12000;

/** 生成中进度：演示节奏 12s 走满，完成 100% */
const progressOf = (t: VideoTask): number =>
  t.status === 'DONE'
    ? 100
    : Math.min(96, Math.round(((nowTick.value - (t.startTs || nowTick.value)) / RUN_DURATION) * 100));

const hint = (m: string): void => {
  ElMessage.info(m);
};

const goCreate = (): void => {
  hint('创作工作台页面在下一任务接入（见适配包 H3）');
};

interface TaskTimers {
  queue?: number;
  complete?: number;
}

const lifecycleTimers = new Map<string, TaskTimers>();

const clearTaskTimers = (taskId: string): void => {
  const timers = lifecycleTimers.get(taskId);
  if (!timers) return;
  if (timers.queue) window.clearTimeout(timers.queue);
  if (timers.complete) window.clearTimeout(timers.complete);
  lifecycleTimers.delete(taskId);
};

const finishTask = (t: VideoTask): void => {
  if (t.status !== 'RUNNING') return;
  t.status = 'DONE';
  t.dur = '2 分 05 秒';
  lifecycleTimers.delete(t.id);
  ElMessage.success(`${t.title} 生成完成 · 成片可下载`);
};

const cancelTask = (t: VideoTask): void => {
  ElMessageBox.confirm(`确认取消 ${t.id}？仅排队中任务可取消。`, '取消任务', {
    confirmButtonText: '确认取消',
    cancelButtonText: '再想想',
    type: 'warning'
  })
    .then(() => {
      clearTaskTimers(t.id);
      t.status = 'FAILED';
      t.dur = '—';
      ElMessage.success('任务已取消');
    })
    .catch(() => undefined);
};

const retryTask = (t: VideoTask): void => {
  ElMessageBox.confirm(`将重新执行 ${t.id} · ${t.cap} · ${t.model}，计费方式不变。`, '重新提交', {
    confirmButtonText: '重新提交',
    cancelButtonText: '取消',
    type: 'info'
  })
    .then(() => {
      t.status = 'QUEUED';
      t.dur = '—';
      t.startTs = undefined;
      scheduleLife(t);
      ElMessage.success('已重新入队 · 完成后通知');
    })
    .catch(() => undefined);
};

/** 演示生命周期：排队 3s → 生成中 → 12s 完成（后端接入后由轮询/SSE 替换） */
const scheduleLife = (t: VideoTask): void => {
  clearTaskTimers(t.id);
  const timers: TaskTimers = {};
  lifecycleTimers.set(t.id, timers);

  if (t.status === 'RUNNING') {
    t.startTs = t.startTs || Date.now();
    const remaining = Math.max(0, RUN_DURATION - (Date.now() - t.startTs));
    timers.complete = window.setTimeout(() => finishTask(t), remaining);
    return;
  }

  if (t.status !== 'QUEUED') return;
  timers.queue = window.setTimeout(() => {
    if (t.status !== 'QUEUED') return;
    t.status = 'RUNNING';
    t.startTs = Date.now();
    timers.complete = window.setTimeout(() => finishTask(t), RUN_DURATION);
  }, QUEUE_DELAY);
};

const openPreview = (t: VideoTask): void => {
  previewTask.value = t;
};
const closePreview = (): void => {
  previewTask.value = null;
};

const handleKeydown = (event: KeyboardEvent): void => {
  if (event.key === 'Escape') closePreview();
};

/** 进度条秒级刷新 */
let ticker = 0;
onMounted(() => {
  tasks.value
    .filter(t => t.status === 'RUNNING')
    .forEach(t => {
      t.startTs = t.startTs || Date.now() - 4000;
      scheduleLife(t);
    });
  ticker = window.setInterval(() => {
    nowTick.value = Date.now();
  }, 1000);
  window.addEventListener('keydown', handleKeydown);
});
onBeforeUnmount(() => {
  window.clearInterval(ticker);
  window.removeEventListener('keydown', handleKeydown);
  lifecycleTimers.forEach(timers => {
    if (timers.queue) window.clearTimeout(timers.queue);
    if (timers.complete) window.clearTimeout(timers.complete);
  });
  lifecycleTimers.clear();
});
</script>

<style lang="scss" scoped>
/* ===== HOTTER AI 视频任务控制台（星云暗色 · 自包含 .studio 作用域） ===== */
.studio.video-tasks {
  margin: 12px;
  padding: 22px;
  border-radius: 16px;
  background: var(--bg);
  color: var(--t1);
  border: 1px solid var(--line);
  min-height: calc(100vh - 130px);
  font-size: 13px;
}

.vt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
.vt-title {
  font-size: 18px;
  margin: 0;
  color: var(--t1);
}
.vt-sub {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--t3);
}
.vt-create {
  background: var(--grad);
  border: 0;
  font-weight: 700;
  border-radius: 10px;
  box-shadow: 0 8px 24px var(--glow);
}
.vt-create-star {
  margin-right: 4px;
}

.vt-toolbar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.vt-search {
  flex: 1;
  min-width: 260px;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--t1);
  padding: 10px 14px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
  &:focus {
    border-color: var(--p);
    box-shadow: 0 0 0 3px var(--tint);
  }
}
.vt-filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.vt-filter {
  padding: 8px 14px;
  border-radius: 99px;
  font-size: 12.5px;
  color: var(--t2);
  border: 1px solid var(--line);
  background: var(--surface);
  cursor: pointer;
  transition: 0.2s;
  em {
    font-style: normal;
    font-size: 10.5px;
    opacity: 0.7;
    margin-left: 4px;
  }
  &:hover {
    color: var(--t1);
  }
  &.on {
    color: #fff;
    background: var(--tint);
    border-color: transparent;
    font-weight: 600;
  }
}

.vt-table-wrap {
  overflow-x: auto;
}
.vt-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.vt-table th {
  text-align: left;
  font-weight: 600;
  font-size: 12px;
  color: var(--t3);
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.04);
  border-bottom: 1px solid var(--line);
  white-space: nowrap;
}
.vt-table td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--line);
  color: var(--t2);
  vertical-align: top;
}
.vt-table tbody tr:hover td {
  background: var(--elevated);
}
.vt-empty {
  text-align: center;
  color: var(--t3);
  padding: 48px 0;
}

.vt-task-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.vt-thumb {
  width: 52px;
  height: 32px;
  border-radius: 7px;
  background: var(--poster);
  border: 1px solid var(--line2);
  flex: none;
  cursor: pointer;
  position: relative;
}
.vt-thumb-play {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 10px;
}
.vt-task-meta {
  min-width: 0;
}
.vt-task-title {
  color: var(--t1);
  font-size: 13px;
  display: block;
}
.vt-task-id {
  font-style: normal;
  font-size: 11px;
  color: var(--t3);
}
.vt-prog {
  height: 4px;
  border-radius: 99px;
  background: rgba(139, 92, 246, 0.15);
  margin-top: 8px;
  overflow: hidden;
  width: 160px;
  i {
    display: block;
    height: 100%;
    border-radius: 99px;
    background: var(--grad);
    box-shadow: 0 0 8px var(--glow);
    transition: width 1s linear;
  }
}
.vt-cap {
  color: var(--t1);
}
.vt-model,
.vt-bill {
  font-style: normal;
  font-size: 11px;
  color: var(--t3);
}

.vt-pill {
  font-size: 10.5px;
  font-weight: 700;
  padding: 3px 9px;
  border-radius: 99px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
  &.q {
    color: #8db9ff;
    background: rgba(96, 165, 250, 0.16);
  }
  &.run {
    color: var(--acc);
    background: var(--tint);
  }
  &.ok {
    color: var(--ok);
    background: rgba(52, 211, 153, 0.1);
  }
  &.fail {
    color: var(--danger);
    background: rgba(248, 113, 113, 0.1);
  }
}
.vt-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: vt-pu 1.4s infinite;
}
@keyframes vt-pu {
  0% {
    box-shadow: 0 0 0 0 var(--glow);
  }
  70% {
    box-shadow: 0 0 0 7px transparent;
  }
  100% {
    box-shadow: 0 0 0 0 transparent;
  }
}

.vt-ops-col {
  width: 190px;
}
.vt-ops {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.vt-ops button {
  border: 1px solid var(--line2);
  background: transparent;
  color: var(--t2);
  border-radius: 8px;
  padding: 5px 10px;
  font-size: 12px;
  cursor: pointer;
  transition: 0.2s;
  &:hover {
    color: #fff;
    border-color: var(--p);
    background: var(--tint);
  }
  &.warn:hover {
    color: #ffd3d3;
    border-color: var(--danger);
    background: rgba(248, 113, 113, 0.1);
  }
}

.vt-pager {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
  margin-top: 16px;
  font-size: 12.5px;
  color: var(--t3);
  button {
    min-width: 30px;
    height: 30px;
    border-radius: 8px;
    border: 1px solid var(--line);
    background: transparent;
    color: var(--t2);
    font-size: 12.5px;
    cursor: pointer;
    &:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
    &.cur {
      background: var(--tint);
      color: #fff;
      border-color: transparent;
      font-weight: 700;
    }
  }
}

/* ===== 预览弹层 ===== */
.vt-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: grid;
  place-items: center;
  background: rgba(10, 12, 16, 0.55);
  backdrop-filter: blur(4px);
}
.vt-modal {
  width: min(720px, 92vw);
  background: #151923;
  border: 1px solid var(--line2);
  border-radius: 16px;
  overflow: hidden;
  color: #f2f4f8;
  position: relative;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.5);
}
.vt-player {
  position: relative;
  aspect-ratio: 16/9;
  background: var(--poster);
}
.vt-player-badges {
  position: absolute;
  top: 16px;
  left: 16px;
  display: flex;
  gap: 8px;
  z-index: 2;
}
.vt-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 5px 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: #fff;
  &.hl {
    background: var(--tint);
    border-color: transparent;
    color: var(--acc);
  }
}
.vt-player-center {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  z-index: 2;
}
.vt-playbtn {
  width: 74px;
  height: 74px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.22);
  cursor: pointer;
  position: relative;
  transition: 0.25s;
  &::before {
    content: '';
    position: absolute;
    width: 0;
    height: 0;
    left: 29px;
    top: 25px;
    border-left: 20px solid #fff;
    border-top: 12px solid transparent;
    border-bottom: 12px solid transparent;
  }
  &:hover {
    background: var(--grad);
    box-shadow: 0 0 40px var(--glow);
  }
}
.vt-player-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.65));
}
.vt-player-name {
  font-size: 12.5px;
  color: #e8ecf3;
}
.vt-player-bar i {
  font-style: normal;
  color: rgba(255, 255, 255, 0.5);
  display: block;
  font-size: 11px;
  margin-top: 2px;
}
.vt-player-acts {
  margin-left: auto;
  display: flex;
  gap: 8px;
  button {
    width: 34px;
    height: 34px;
    border-radius: 9px;
    border: 1px solid rgba(255, 255, 255, 0.16);
    background: rgba(0, 0, 0, 0.4);
    color: #fff;
    font-size: 13px;
    cursor: pointer;
    &.pri {
      width: auto;
      padding: 0 14px;
      background: var(--grad);
      border-color: transparent;
      font-weight: 700;
    }
  }
}
.vt-params {
  padding: 18px 20px;
}
.vt-params-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #f2f4f8;
}
.vt-kv {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 8px 12px;
  font-size: 12.5px;
  span {
    color: #7b8598;
  }
  b {
    color: #9aa3b2;
    font-weight: 500;
  }
}
.vt-modal-close {
  position: absolute;
  top: 12px;
  right: 14px;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  cursor: pointer;
  z-index: 3;
}

@media (max-width: 760px) {
  .studio.video-tasks {
    margin: 0;
    border-radius: 0;
  }
  .vt-ops-col {
    width: auto;
  }
}
</style>
