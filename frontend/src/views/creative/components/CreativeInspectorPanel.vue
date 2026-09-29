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
        <span class="drawer-title">细节检查器</span>
        <span class="drawer-sub">{{ projectName || '当前项目' }}</span>
        <span class="spacer" />
        <el-button size="small" text @click="load" :loading="loading">刷新</el-button>
        <el-button size="small" text @click="visible = false">关闭</el-button>
      </div>

      <p class="drawer-note">
        只读：这里只回答「现在在哪一步、这一步凭什么、最近发生了什么」，不改任何状态。
      </p>

      <!-- 当前环节 -->
      <div class="section">
        <div class="sec-head">当前环节</div>
        <ul class="kv">
          <li>
            <span class="k">阶段</span>
            <span class="v">{{ stageLabel || '未选择项目' }}<em v-if="stage">（{{ stage }}）</em></span>
          </li>
          <li>
            <span class="k">当前步</span>
            <span class="v">
              {{ currentStep ? `第 ${currentStep.no} 步 · ${currentStep.name}` : '（没有进行中的步骤）' }}
              <span v-if="currentStep" class="badge" :class="'is-' + currentStep.status">
                {{ currentStep.statusLabel }}
              </span>
            </span>
          </li>
          <li>
            <span class="k">状态来源</span>
            <span class="v">{{ sourceText }}</span>
          </li>
        </ul>
      </div>

      <!-- 这一步的判断依据 -->
      <div class="section">
        <div class="sec-head">这一步的判断依据</div>
        <template v-if="currentStep">
          <p class="reason">{{ currentStep.reason }}</p>
          <ul v-if="currentStep.missing.length" class="missing">
            <li v-for="(m, i) in currentStep.missing" :key="i">{{ m }}</li>
          </ul>
          <el-button
            v-if="!currentStep.detailLoaded"
            size="small"
            text
            type="primary"
            @click="loadStepDetail"
          >
            读取这一步的明细
          </el-button>
        </template>
        <p v-else class="empty">流程已经走完（没有未完成的步骤），或还没读到步骤状态。</p>
      </div>

      <!-- 最近事件（真实时间线，不是前端拼的） -->
      <div class="section">
        <div class="sec-head">
          最近发生了什么
          <span class="sec-count">{{ events.length ? `最近 ${shownEvents.length} / ${events.length} 条` : '' }}</span>
        </div>
        <p v-if="!events.length" class="empty">还没有阶段事件（新项目刚建时就是这样）。</p>
        <ul v-else class="events">
          <li v-for="(ev, i) in shownEvents" :key="i">
            <span class="ev-time">{{ shortTime(ev.createTime) }}</span>
            <span class="ev-action">{{ ev.action || ev.eventType }}</span>
            <span class="ev-stage">{{ stageDiff(ev) }}</span>
            <span class="ev-actor">{{ ev.actorName || '' }}</span>
          </li>
        </ul>
      </div>

      <p v-if="error" class="err">{{ error }}</p>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { listCreativeTimeline } from '@/api/creative';
import { CREATIVE_STAGE_LABELS } from '@/api/creative/types';
import type { DpStageEventVO } from '@/api/creative/types';
import type { ProjectStepState } from '@/api/creative/scenario';
import type { FlowStep } from '../composables/useCreativeFlow';

/**
 * 细节检查器（INSPECTOR，R18 真做）。
 *
 * <p><b>为什么需要它</b>：以前"现在在哪一步、为什么卡住、最近谁动了什么"散在三处——
 * 步骤浮层（要说清缺什么）、阶段标签（只有阶段名）、时间线（藏在别处）。
 * 装配定义里 `INSPECTOR` 本来就是"选中对象后看它的属性与证据"的面板，这里把它落成真面板。</p>
 *
 * <p><b>数据都是真的、且不从父组件重复取</b>：阶段/当前步/判据由指引线（父组件）已经算好并传进来，
 * 本面板自己只多取一样——<b>阶段事件时间线</b>（`GET /creative/projects/{taskId}/timeline`）。
 * 面板不写库、不触发任何流程动作。</p>
 */
const props = defineProps<{
  /** 当前项目ID */
  taskId?: string | number;
  /** 项目名（标题展示） */
  projectName?: string;
  /** 当前阶段编码 */
  stage?: string | null;
  /** 当前阶段的可读名 */
  stageLabel?: string;
  /** 当前步（指引线里"进行中"的那一步；没有则不传） */
  currentStep?: FlowStep | null;
  /** 项目步骤状态（用来显示"状态来源：已落库/按阶段推导"） */
  projectSteps?: ProjectStepState[];
  /**
   * 让父组件去读当前步的明细（父组件持有明细加载逻辑，避免这里再实现一遍判据）。
   */
  onLoadDetail?: () => void;
}>();

const visible = defineModel<boolean>('visible', { required: true });

const loading = ref(false);
const error = ref('');
const drawerSize = ref('42%');
const events = ref<DpStageEventVO[]>([]);
const shownEvents = computed(() => events.value.slice(-8).reverse());

/** 状态来源文案：这一步的状态是落库的还是按阶段推导的 */
const sourceText = computed(() => {
  const step = props.currentStep;
  if (!step) {
    return '—';
  }
  const hit = (props.projectSteps || []).find((s) => s.stepCode === step.key);
  if (!hit) {
    return '接口没有返回这一步的状态行（界面按代码八步回落显示）';
  }
  if (hit.source === 'PERSISTED') {
    return `步骤状态表已落库（触发阶段 ${hit.stageCode || '未记录'}）`;
  }
  if (hit.source === 'DERIVED') {
    return `按当前阶段推导（还没有落库行；阶段 ${hit.stageCode || '未记录'}）`;
  }
  return hit.source || '未知';
});

/** 阶段变化 → `A → B`（原地事件显示"停在 A"） */
function stageDiff(ev: DpStageEventVO): string {
  const from = CREATIVE_STAGE_LABELS[ev.fromStage || ''] || ev.fromStage || '';
  const to = CREATIVE_STAGE_LABELS[ev.toStage || ''] || ev.toStage || '';
  if (from && to && from !== to) {
    return `${from} → ${to}`;
  }
  return from || to || '';
}

/** 时间只显示到分钟（面板窄，秒没意义） */
function shortTime(time?: string): string {
  if (!time) return '';
  return time.length >= 16 ? time.slice(5, 16) : time;
}

/** 读时间线 */
async function load() {
  if (!props.taskId) {
    events.value = [];
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const res = await listCreativeTimeline(props.taskId);
    events.value = res?.data || [];
  } catch (e) {
    error.value = '阶段事件读取失败（其余照常显示）';
  } finally {
    loading.value = false;
  }
}

/** 让父组件去读当前步明细 */
function loadStepDetail() {
  props.onLoadDetail?.();
}

function onOpen() {
  // 同样要挡重复：`@open` 与 `visible` 上的自愈监听会在同一拍各触发一次
  // （真机实测一开抽屉 timeline 等接口各被打了 2 次）。
  if (loading.value && !events.value.length) {
    return;
  }
  void load();
}

function onClosed() {
  events.value = [];
}

watch(
  () => props.taskId,
  () => {
    if (visible.value) {
      void load();
    }
  }
);

/**
 * 自愈：抽屉可能在 `taskId` 还没到位时就被打开（指引线挂载早于页面数据），
 * 那时 `load()` 直接返回、事件区停在"还没有阶段事件"——看起来像这个项目真的没有历史。
 * 这里在 `taskId` 到位且**还没读到过事件**时补一次。
 *
 * R21 补强（`visible` 一并入参 + `immediate`）：与 ASSET_DRAWER 同理——抽屉在已可见状态下
 * 被重新挂载时 `@open` 不再触发，新实例会显示成"没有历史"的假空态。
 */
watch(
  () => [visible.value, props.taskId] as const,
  () => {
    if (!visible.value || !props.taskId || loading.value) {
      return;
    }
    if (!events.value.length) {
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
}

.kv {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    align-items: baseline;
    gap: 8px;
    padding: 4px 0;
    font-size: 12px;
  }

  .k {
    flex: none;
    width: 62px;
    color: var(--t3);
  }

  .v {
    color: var(--t2);

    em {
      color: var(--t3);
      font-style: normal;
    }
  }
}

.badge {
  margin-left: 6px;
  padding: 0 6px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 10px;
  color: var(--t3);

  &.is-done {
    color: #67c23a;
    border-color: rgba(103, 194, 58, 0.35);
  }

  &.is-doing {
    color: #409eff;
    border-color: rgba(64, 158, 255, 0.35);
  }

  &.is-blocked {
    color: #e6a23c;
    border-color: rgba(230, 162, 60, 0.35);
  }
}

.reason {
  margin: 6px 0;
  color: var(--t2);
  font-size: 12px;
  line-height: 1.75;
}

.missing {
  margin: 4px 0 0;
  padding-left: 18px;
  color: var(--t2);
  font-size: 12px;
  line-height: 1.8;
}

.events {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    align-items: baseline;
    gap: 8px;
    padding: 4px 0;
    border-bottom: 1px dashed var(--line);
    font-size: 12px;

    &:last-child {
      border-bottom: none;
    }
  }

  .ev-time {
    flex: none;
    color: var(--t3);
    font-size: 11px;
  }

  .ev-action {
    flex: none;
    color: var(--t1);
  }

  .ev-stage {
    flex: 1;
    color: var(--t2);
  }

  .ev-actor {
    flex: none;
    color: var(--t3);
    font-size: 11px;
  }
}

.err {
  margin: 12px 0 0;
  color: #e6a23c;
  font-size: 12px;
}
</style>

<!-- 抽屉 teleport 到 body：token 显式 include（与资产抽屉同因同解） -->
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
