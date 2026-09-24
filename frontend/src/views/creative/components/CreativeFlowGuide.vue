<template>
  <div class="flow-guide" :class="{ 'is-running': stageRunning, 'is-empty': !taskId }">
    <div class="flow-head">
      <span class="flow-title">流程指引</span>
      <span class="stage-tag" :class="'is-' + stageType">
        {{ stageLabel }}
      </span>
      <span v-if="stageRunning" class="running-hint">
        <span class="spinner" aria-hidden="true" />这一步正在执行
      </span>
      <span class="spacer" />
      <div class="progress" :title="`已完成 ${doneCount} / 8 步`">
        <i :style="{ width: progressPct + '%' }" />
      </div>
      <span class="progress-text">{{ doneCount }} / 8 已完成</span>
    </div>

    <p v-if="!taskId" class="flow-empty">选择一个视觉项目后，这里会显示它在八个环节里的位置。</p>

    <ol v-else class="flow-steps">
      <li
        v-for="step in steps"
        :key="step.key"
        :class="['flow-step', 'is-' + step.status, { active: step.no === activeNo }]"
        @click="go(step)"
      >
        <el-popover
          placement="bottom-start"
          :width="340"
          trigger="hover"
          popper-class="flow-popover"
          @show="ensureStep(step.no)"
        >
          <template #reference>
            <!-- 热区是整个步骤（圆圈 + 名称 + 状态），不是只有圆圈：
                 只把圆圈当触发点的话，鼠标停在步骤名称上不会出浮层，等于「缺什么」看不见 -->
            <span class="step-inner">
              <span class="dot">
                <span v-if="step.status === 'done'" class="tick">✓</span>
                <span v-else-if="step.no === activeNo && stageRunning" class="spin" />
                <span v-else>{{ step.no }}</span>
              </span>
              <span class="lbl">{{ step.name }}</span>
              <span class="st">{{ step.statusLabel }}</span>
            </span>
          </template>
          <div class="pop">
            <div class="pop-head">
              第 {{ step.no }} 步 · {{ step.name }}
              <span class="pop-status" :class="'is-' + step.status">{{ step.statusLabel }}</span>
            </div>
            <p class="pop-sum">{{ step.summary }}</p>
            <p v-if="!step.detailLoaded" class="pop-muted">正在读取这一步的明细…</p>
            <template v-else-if="step.missing.length">
              <p class="pop-label">为什么还不能进入下一步：</p>
              <ul class="pop-missing">
                <li v-for="(m, i) in step.missing" :key="i">{{ m }}</li>
              </ul>
            </template>
            <p v-else class="pop-ok">这一步的条件都已满足。</p>
            <div class="pop-actions">
              <el-button size="small" type="primary" @click="go(step)">去这一步</el-button>
            </div>
          </div>
        </el-popover>
      </li>
    </ol>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { CREATIVE_STAGE_TYPES } from '@/api/creative/types';
import { useCreativeFlow, type FlowStep } from '../composables/useCreativeFlow';

/**
 * 流程指引线：把八个环节横排在每个环节页面的顶部，当前环节由项目阶段定位。
 *
 * <p>交互（按确认过的口径）：<b>悬停</b>看「为什么还不能进入下一步」（明细按需加载），
 * <b>点击</b>跳到该步对应的页面并带上 taskId（纯导航，不写数据）。</p>
 */
const props = defineProps<{
  /** 当前项目ID；为空时只显示一句引导语 */
  taskId?: string | number;
  /**
   * 刷新令牌：页面完成某个动作后把它 +1，指引线会重新读一次阶段。
   * 这样页面不必把内部的加载函数暴露出来，也不会出现「页面状态变了、指引线还停在旧步骤」。
   */
  refreshToken?: number;
}>();

const router = useRouter();
// 解构出来的 ref/computed 在模板里会自动解包；留着 flow.* 访问则必须写 .value，容易漏
const { steps, activeNo, doneCount, stageRunning, stage, stageLabel, ensureStep, stepHref, reload } =
  useCreativeFlow(computed(() => props.taskId));

const stageType = computed(() => CREATIVE_STAGE_TYPES[stage.value || ''] || 'info');
const progressPct = computed(() => Math.round((doneCount.value / 8) * 100));

function go(step: FlowStep) {
  void router.push(stepHref(step.no));
}

watch(
  () => props.taskId,
  () => void reload(),
  { immediate: true }
);

watch(
  () => props.refreshToken,
  () => void reload()
);
</script>

<style scoped lang="scss">
@use '@/assets/styles/tokens-studio.scss';

.flow-guide {
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: 10px;
  padding: 10px 14px 12px;
  margin-bottom: 14px;
}

.flow-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;

  .flow-title {
    font-weight: 600;
    color: var(--t1);
  }

  .spacer {
    flex: 1;
  }

  .running-hint {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--t2);
    font-size: 12px;
  }

  .progress {
    width: 140px;
    height: 6px;
    border-radius: 3px;
    background: var(--sunken);
    overflow: hidden;

    i {
      display: block;
      height: 100%;
      background: linear-gradient(90deg, #409eff, #67c23a);
      transition: width 0.4s ease;
    }
  }

  .progress-text {
    font-size: 12px;
    color: var(--t2);
    min-width: 76px;
    text-align: right;
  }
}

.flow-empty {
  margin: 0;
  color: var(--t3);
  font-size: 13px;
}

.flow-steps {
  display: flex;
  align-items: flex-start;
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-x: auto;
}

.flow-step {
  position: relative;
  flex: 1 1 0;
  min-width: 96px;
  cursor: pointer;
  padding-top: 2px;

  /* 连接线：与左边相邻步骤连起来 */
  &::before {
    content: '';
    position: absolute;
    top: 14px;
    left: -50%;
    width: 100%;
    height: 2px;
    background: var(--line2);
    z-index: 0;
  }

  &:first-child::before {
    display: none;
  }

  /* 整个步骤都是悬停热区（浮层触发点）与点击区（跳转） */
  .step-inner {
    position: relative;
    z-index: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    width: 100%;
  }

  .dot {
    position: relative;
    z-index: 1;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    background: var(--elevated);
    border: 2px solid var(--line2);
    color: var(--t2);
    transition: all 0.3s ease;
  }

  .lbl {
    font-size: 12px;
    color: var(--t2);
  }

  .st {
    font-size: 11px;
    color: var(--t3);
  }

  &.is-done {
    &::before {
      background: #67c23a;
    }

    .dot {
      background: #67c23a;
      border-color: #67c23a;
      color: #fff;
    }
  }

  &.is-doing {
    .dot {
      border-color: #409eff;
      color: #409eff;
      animation: flow-pulse 1.8s infinite;
    }

    .lbl {
      color: #409eff;
      font-weight: 600;
    }
  }

  &.is-blocked {
    .dot {
      border-color: #f56c6c;
      color: #f56c6c;
    }

    .lbl {
      color: #f56c6c;
    }
  }

  &.active .lbl {
    font-weight: 600;
  }
}

/* 当前步的脉冲：让「现在在这里」一眼可见 */
@keyframes flow-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.45);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(64, 158, 255, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(64, 158, 255, 0);
  }
}

.spinner,
.spin {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(64, 158, 255, 0.3);
  border-top-color: #409eff;
  border-radius: 50%;
  display: inline-block;
  animation: flow-spin 0.8s linear infinite;
}

@keyframes flow-spin {
  to {
    transform: rotate(360deg);
  }
}

.pop {
  .pop-head {
    font-weight: 600;
    margin-bottom: 6px;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .pop-status {
    font-size: 11px;
    padding: 1px 6px;
    border-radius: 4px;
    background: var(--sunken);
    color: var(--t2);

    &.is-done {
      background: #f0f9eb;
      color: #67c23a;
    }

    &.is-doing {
      background: #ecf5ff;
      color: #409eff;
    }

    &.is-blocked {
      background: #fef0f0;
      color: #f56c6c;
    }
  }

  .pop-sum,
  .pop-muted {
    margin: 4px 0;
    color: var(--t2);
    font-size: 12px;
  }

  .pop-label {
    margin: 6px 0 2px;
    font-size: 12px;
    color: var(--t1);
  }

  .pop-missing {
    margin: 0;
    padding-left: 18px;
    font-size: 12px;
    color: var(--t2);
    line-height: 1.7;
  }

  .pop-ok {
    margin: 4px 0;
    font-size: 12px;
    color: #67c23a;
  }

  .pop-actions {
    margin-top: 8px;
    text-align: right;
  }
}
</style>
