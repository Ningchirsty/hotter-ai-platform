<template>
  <!-- 单根容器：`src/views` 下的组件会被 `vite:check-transition` 检查（外层路由用 <transition> 包裹）。 -->
  <div class="storyboard-board">
    <p v-if="!hasProjects" class="empty">还没有视觉项目。先到「视觉项目」页新建一个。</p>
    <section v-else class="panel" data-board-section="SCREENS">
      <div class="block-head">
        <h3>{{ stepHeading }}分镜</h3>
        <div class="head-actions">
          <template v-if="storyboard">
            <span class="muted">
              {{ storyboard.storyboardNo }} · v{{ storyboard.version }} ·
              {{ storyboard.statusDesc }} · {{ storyboard.screenCount }} 屏
            </span>
            <el-button size="small" :loading="generating" @click="$emit('generate')">重新生成分镜</el-button>
            <el-button
              size="small"
              type="primary"
              :disabled="storyboard.status === 'LOCKED'"
              :loading="locking"
              @click="$emit('lock')"
            >
              {{ storyboard.status === 'LOCKED' ? '已锁定' : '锁定分镜' }}
            </el-button>
          </template>
          <el-button v-else type="primary" :loading="generating" @click="$emit('generate')">
            生成分镜
          </el-button>
        </div>
      </div>

      <p v-if="storyboard && storyboard.sourceDesc" class="muted source-note">
        来源：{{ storyboard.sourceDesc }}
      </p>

      <p v-if="!storyboard" class="empty">
        还没有分镜。生成后会得到逐屏规格（屏号 / 类型 / 文案 / 画面独白 / 视觉规格）。
        每屏必须写清「这张图不讲文案时自己要说清什么」——没有独白的屏不能锁定。
      </p>
      <div v-else class="screen-list">
        <div v-for="screen in storyboard.screens || []" :key="String(screen.id)" class="screen-card">
          <div class="screen-head">
            <span class="screen-no">{{ screen.screenNo }}</span>
            <span class="screen-type">{{ screen.screenTypeDesc }}</span>
            <el-tag size="small" :type="screen.productLockLevel === 'STRICT' ? 'warning' : 'info'">
              {{ screen.productLockLevel === 'STRICT' ? '产品严格保真' : '允许艺术化' }}
            </el-tag>
            <span class="spacer" />
            <el-button size="small" text type="primary" @click="$emit('edit-screen', screen)">编辑</el-button>
          </div>
          <div class="screen-body">
            <h4>{{ screen.title || '（未填标题）' }}</h4>
            <p v-if="screen.subtitle" class="muted">{{ screen.subtitle }}</p>
            <p v-if="screen.bodyText" class="body-text">{{ screen.bodyText }}</p>
            <p class="solo">
              <b>画面独白：</b>{{ screen.pictureSoloStatement || '（缺失——锁定前必须补齐）' }}
            </p>
            <div class="spec-row">
              <span v-for="(value, key) in screen.spec" :key="key" class="spec-item">
                <b>{{ specLabel(String(key)) }}</b>{{ value }}
              </span>
            </div>
            <p class="muted small">出图能力：{{ screen.workflowCode }}</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { DpStoryboardScreenVO, DpStoryboardVO } from '@/api/creative/types';
import { useStepHeading } from '../../composables/stepNumbering';

/** 标题编号：本页步骤号（v1 反馈；没有工作台上下文时不显示编号） */
const stepHeading = useStepHeading('StoryboardBoard');

/**
 * 「分镜」这一步的内容（V0.2 R39，装配组件名 `StoryboardBoard`）。
 *
 * <p><b>纯展示</b>：逐屏规格卡片 + 生成/锁定/编辑单屏三个动作发事件回页面。
 * 编辑单屏的弹窗留在页面（与方向那边同一套做法：弹窗是页面级浮层，
 * "保存成功 → 关弹窗 → 刷新 → 推进指引线"必须在一起）。</p>
 *
 * @author creative
 */
defineProps<{
  /** 当前分镜（没有表示还没生成） */
  storyboard: DpStoryboardVO | null;
  /** 正在生成分镜 */
  generating: boolean;
  /** 正在锁定分镜 */
  locking: boolean;
  /** 项目列表是否非空（空列表要引导去新建项目，而不是显示"还没有分镜"） */
  hasProjects: boolean;
}>();

defineEmits<{
  /** 生成 / 重新生成分镜 */
  (e: 'generate'): void;
  /** 锁定这一版分镜 */
  (e: 'lock'): void;
  /** 打开编辑单屏弹窗（弹窗与落库都在页面） */
  (e: 'edit-screen', screen: DpStoryboardScreenVO): void;
}>();

/**
 * 视觉规格字段的中文标签。
 *
 * @param key 字段键
 * @returns 标签
 */
function specLabel(key: string): string {
  const map: Record<string, string> = {
    shot: '镜头',
    composition: '构图',
    lighting: '光线',
    background: '背景',
    productRatio: '产品占比',
    whitespace: '留白'
  };
  return map[key] || key;
}
</script>

<style scoped lang="scss">
/* 这一步的内容样式（R39 从 storyboard/index.vue 搬过来，一字未改） */
.storyboard-board {
  display: block;
}

.panel {
  padding: 16px;
  margin-bottom: 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.panel h3 {
  margin: 0;
  font-size: 15px;
}
.block-head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.source-note {
  margin: 0 0 10px;
}

.screen-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 12px;
}
.screen-card {
  display: flex;
  flex-direction: column;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.screen-head {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}
.screen-head .spacer {
  flex: 1;
}
.screen-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: #c7d2fe;
}
.screen-type {
  font-size: 13px;
  font-weight: 600;
}
.screen-body {
  padding: 12px;
}
.screen-body h4 {
  margin: 0 0 6px;
  font-size: 14px;
}
.body-text {
  margin: 6px 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--t1);
}
.solo {
  margin: 8px 0;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.8;
  color: #ddd6fe;
  background: rgba(124, 58, 237, 0.12);
  border-left: 2px solid rgba(124, 58, 237, 0.6);
  border-radius: 0 4px 4px 0;
}
.spec-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--t2);
}
.spec-item b {
  margin-right: 4px;
  color: var(--t3);
  font-weight: 500;
}
.small {
  font-size: 12px;
}

.muted {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--t2);
}
.empty {
  padding: 12px 0;
  font-size: 13px;
  line-height: 1.9;
  color: var(--t2);
}
</style>
