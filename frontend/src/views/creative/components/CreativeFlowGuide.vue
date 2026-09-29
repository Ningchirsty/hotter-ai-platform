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
      <div class="progress" :title="`已完成 ${doneCount} / ${steps.length} 步`">
        <i :style="{ width: progressPct + '%' }" />
      </div>
      <span class="progress-text">{{ doneCount }} / {{ steps.length }} 已完成</span>
    </div>

    <p v-if="!taskId" class="flow-empty">选择一个视觉项目后，这里会显示它在流程里的位置。</p>
    <p v-else-if="!steps.length" class="flow-empty">正在读取流程配置…</p>

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
          @show="ensureStep(step.key)"
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
    <!-- 步骤来源（只读补充信息）：告诉人这条线是按场景配置（十步）还是按代码八步回落渲染的 -->
    <p v-if="taskId && steps.length" class="flow-source">
      <span class="source-tag">{{ configDriven ? '按场景配置' : '按代码八步（配置未取到）' }}</span>
      <span v-if="configDriven">共 {{ steps.length }} 步：{{ steps.map((s) => s.name).join(' → ') }}</span>
    </p>
    <!-- 场景配置（只读补充信息）：读不到就整行不渲染，绝不影响上面的指引线 -->
    <p v-if="scenarioLine" class="flow-scenario" :title="scenarioSteps.join(' → ')">
      <span class="scenario-tag">场景配置</span>{{ scenarioLine }}
    </p>
    <!-- 步序对照（只读，V0.2 D 阶段第二刀第一步）：
         把「配置 N 步 ↔ 代码八步」的差异如实摆出来供人核对，**指引线一行不动**——
         真的把导航切到配置驱动之前，先让"哪一步对哪一步、哪一步没有对应"是可见的。
         R17 再加一枚「工作台装配」：配置声明的面板/组件 vs 代码里的真实落点。 -->
    <div v-if="mappingChip || assemblyChip" class="flow-mapping">
      <el-popover
        v-if="mappingChip"
        placement="top-start"
        :width="440"
        trigger="click"
        popper-class="flow-map-popover"
      >
        <template #reference>
          <span class="map-chip" :title="mapping.verdict">{{ mappingChip }}</span>
        </template>
        <div class="map">
          <div class="map-head">步序对照 · 配置步骤 → 代码八步</div>
          <ul class="map-rows">
            <li
              v-for="row in mapping.rows"
              :key="row.stepCode || row.sortNo"
              :class="{ 'is-unmapped': !row.codeNo }"
            >
              <span class="map-code">{{ row.stepCode || '未命名' }}</span>
              <span class="map-name">{{ row.stepName }}</span>
              <span class="map-arrow">→</span>
              <span class="map-target">
                {{ row.codeNo ? `第 ${row.codeNo} 步 · ${row.codeName}` : '代码八步里没有对应' }}
              </span>
            </li>
          </ul>
          <p class="map-note">{{ mapping.verdict }}</p>
        </div>
      </el-popover>
      <el-popover
        v-if="assemblyChip && assembly"
        placement="top-start"
        :width="480"
        trigger="click"
        popper-class="flow-map-popover"
      >
        <template #reference>
          <span class="asm-chip" :title="assembly.verdict">{{ assemblyChip }}</span>
        </template>
        <div class="map">
          <div class="map-head">工作台装配 · {{ assembly.workspace || '未声明' }}（配置 → 代码落点）</div>
          <p class="asm-sec">面板</p>
          <ul class="map-rows">
            <li v-for="row in assembly.panelRows" :key="row.code" :class="'kind-' + row.kind.toLowerCase()">
              <span class="asm-code">{{ row.code }}</span>
              <span class="asm-kind">{{ kindLabel(row.kind) }}</span>
              <span class="asm-loc">{{ row.location }}</span>
            </li>
          </ul>
          <p class="asm-sec">步骤组件</p>
          <ul class="map-rows">
            <li v-for="row in assembly.stepRows" :key="row.code" :class="'kind-' + row.kind.toLowerCase()">
              <span class="asm-code">{{ row.code }} → {{ row.component || '（没给组件）' }}</span>
              <span class="asm-kind">{{ kindLabel(row.kind) }}</span>
              <span class="asm-loc">{{ row.location }}</span>
            </li>
          </ul>
          <p class="map-note">{{ assembly.verdict }}</p>
        </div>
      </el-popover>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { CREATIVE_STAGE_TYPES } from '@/api/creative/types';
import { useCreativeFlow, type FlowStep } from '../composables/useCreativeFlow';
import { ASSEMBLY_KIND_LABELS, type AssemblyKind } from '../composables/workspaceAssembly';

/**
 * 流程指引线：把流程环节横排在每个环节页面的顶部，当前环节由**后端步骤状态**定位。
 *
 * <p>交互（按确认过的口径）：<b>悬停</b>看「为什么还不能进入下一步」（明细按需加载，判据按 step_code 注册），
 * <b>点击</b>跳到该步对应的页面并带上 taskId（纯导航，不写数据）。</p>
 *
 * <p>V0.2 D 阶段第一刀（R13）：底部多一行「场景配置」（交付类型 / 默认输出规格 / 配置步骤数）。
 * V0.2 D 阶段第二刀第一步（R15）：再加一枚「步序对照」胶囊，把配置步骤与代码八步的差异摆出来。
 * <b>第二刀（R16，本轮）</b>：指引线本身切成配置驱动——十步各占一格（QA 复用出图页），
 * 状态取自 `GET /creative/v2/projects/{taskId}/steps`，前置条件取自配置 `entry_condition_json`；
 * 配置读不到时静默回落到代码八步（见底部那一行"按代码八步"提示）。</p>
 */
const props = defineProps<{
  /** 当前项目ID；为空时只显示一句引导语 */
  taskId?: string | number;
  /**
   * 刷新令牌：页面完成某个动作后把它 +1，指引线会重新读一次阶段。
   * 这样页面不必把内部的加载函数暴露出来，也不会出现「页面状态变了、指引线还停在旧步骤」。
   */
  refreshToken?: number;
  /**
   * 交付类型提示：**可选**。R16 起指引线自己会从项目数据里取交付类型，
   * 五个页面因此拿到同一份配置；这个 prop 保留给"页面已经知道类型"的场景（早一拍，少一次等待）。
   */
  deliverableType?: string;
}>();

const router = useRouter();
// 单一来源：指引线、场景行、步序对照都从 useCreativeFlow 出（它内部复用同一份场景配置缓存）
const {
  steps,
  activeNo,
  doneCount,
  stageRunning,
  stage,
  stageLabel,
  configDriven,
  scenarioLine,
  scenarioSteps,
  mapping,
  mappingChip,
  assembly,
  assemblyChip,
  ensureStep,
  stepHref,
  reload
} = useCreativeFlow(
  computed(() => props.taskId),
  computed(() => props.deliverableType)
);

const stageType = computed(() => CREATIVE_STAGE_TYPES[stage.value || ''] || 'info');
const progressPct = computed(() =>
  steps.value.length ? Math.round((doneCount.value / steps.value.length) * 100) : 0
);

/**
 * 装配分类的中文标签。
 *
 * @param kind 分类
 * @returns 标签文本
 */
function kindLabel(kind: AssemblyKind): string {
  return ASSEMBLY_KIND_LABELS[kind] || kind;
}

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

/* 场景配置（只读补充信息，V0.2 D 阶段第一刀）：与指引线同一套暗色 token，
   用一条细分隔线与上面的步骤隔开，不做成"白色卡片"以免破坏工作台观感 */
.flow-scenario {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0 0;
  padding-top: 9px;
  border-top: 1px dashed var(--line);
  color: var(--t2);
  font-size: 12px;
  line-height: 1.6;
}

.scenario-tag {
  flex: none;
  padding: 1px 7px;
  color: var(--t3);
  font-size: 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
}

/* 步骤来源（只读）：这条线是"按场景配置"还是"按代码八步回落"渲染的，一眼可辨 */
.flow-source {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 8px 0 0;
  color: var(--t3);
  font-size: 11px;
  line-height: 1.7;
}

.source-tag {
  flex: none;
  padding: 1px 7px;
  color: var(--t3);
  font-size: 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
}

/* 步序对照（只读）：一枚小胶囊，点开才是明细——默认不占地方，也不改变指引线观感 */
.flow-mapping {
  margin-top: 6px;
  line-height: 1.6;
}

.map-chip,
.asm-chip {
  display: inline-block;
  padding: 1px 8px;
  color: var(--t3);
  font-size: 11px;
  border: 1px dashed var(--line);
  border-radius: 999px;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease;

  &:hover {
    color: var(--t2);
    border-color: var(--t3);
  }
}

.asm-chip {
  margin-left: 8px;
}

/* 弹层内容（这段 DOM 在本组件模板里编译，所以 scoped 属性照样生效） */
.map {
  .map-head {
    margin-bottom: 6px;
    color: var(--t1);
    font-size: 12px;
    font-weight: 600;
  }

  .map-rows {
    margin: 0;
    padding: 0;
    list-style: none;
    max-height: 320px;
    overflow-y: auto;

    li {
      display: flex;
      align-items: baseline;
      gap: 6px;
      padding: 3px 0;
      border-bottom: 1px dashed var(--line);
      font-size: 12px;
      color: var(--t2);

      &:last-child {
        border-bottom: none;
      }

      /* 配置里有、代码八步里没有对应的那一条：一眼能认出来，而不是混在列表里 */
      &.is-unmapped {
        .map-target {
          color: #e6a23c;
        }
      }
    }
  }

  .map-code {
    flex: none;
    min-width: 74px;
    color: var(--t1);
  }

  .map-name {
    flex: none;
    color: var(--t3);
  }

  .map-arrow {
    flex: none;
    color: var(--t3);
  }

  .map-target {
    flex: 1;
    text-align: right;
  }

  .map-note {
    margin: 8px 0 0;
    padding-top: 7px;
    border-top: 1px solid var(--line);
    color: var(--t3);
    font-size: 11px;
    line-height: 1.7;
  }

  /* 装配对照：面板/步骤两段，每行是「编码 → 落点 + 分类徽标」 */
  .asm-sec {
    margin: 8px 0 2px;
    color: var(--t3);
    font-size: 11px;
  }

  .asm-code {
    flex: none;
    color: var(--t1);
  }

  .asm-kind {
    flex: none;
    padding: 0 6px;
    font-size: 10px;
    border-radius: 999px;
    border: 1px solid var(--line);
    color: var(--t3);
  }

  .asm-loc {
    flex: 1;
    text-align: right;
    color: var(--t3);
  }

  /* 分类用颜色区分：已是组件=绿、页面内区块=蓝、未实现=橙（与"无对应"同一套观感） */
  .kind-component .asm-kind {
    color: #67c23a;
    border-color: rgba(103, 194, 58, 0.35);
  }

  .kind-section .asm-kind {
    color: #409eff;
    border-color: rgba(64, 158, 255, 0.35);
  }

  .kind-missing .asm-kind {
    color: #e6a23c;
    border-color: rgba(230, 162, 60, 0.35);
  }
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
    }

    .lbl {
      color: #409eff;
      font-weight: 600;
    }
  }

  /* 脉冲只给"当前步"：配置驱动后同一阶段可能有两步同时进行中（如资料与事实都覆盖 MATERIAL_READY），
     两个圆点一起闪会让人以为界面出错了——进行中的都标蓝，只有当前这一步在脉冲。 */
  &.is-doing.active .dot {
    animation: flow-pulse 1.8s infinite;
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

    /* 状态胶囊：底色用低透明度着色，才能在暗色弹层上既分得清状态又不刺眼
       （原来是给白底弹层配的浅色实底 #f0f9eb 这类，放到暗底上会像贴了三块白纸） */
    &.is-done {
      background: rgba(103, 194, 58, 0.16);
      color: #67c23a;
    }

    &.is-doing {
      background: rgba(64, 158, 255, 0.16);
      color: #409eff;
    }

    &.is-blocked {
      background: rgba(245, 108, 108, 0.16);
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

<!-- 弹层外壳：Element Plus 默认是白底，这里换成暗色 token。
     两个必须点（第一版就是没做这两点而静默失效的，实测底色 rgba(0,0,0,0) + 文字近黑）：
       1) token 要**显式 include** 到弹层根节点：`tokens-studio.scss` 编译出来是
          `.studio[data-v-xxx]`（每个页面组件各自一份），而弹层被 teleport 到 body，
          既不是页面根节点、也不带那个 data-v → 取不到任何 token，
          `background: var(--elevated)` 解析失败退化成透明（color 同理 → 继承近黑）。
          所以这里 `@include studio.studio-tokens`，用的是同一份定义，没有抄字面量。
       2) 规则要放非 scoped 块：popper-class 挂在弹层根节点上，scoped 选择器匹配不到。
     既有步骤浮层 `.flow-popover` 有同样的问题——它一直是"暗色工作台里的一块白卡"，
     与"不要白色框架"的口径不符，本轮一并用同一份 token 修正（只有颜色变化，无结构改动）。 -->
<style lang="scss">
@use '@/assets/styles/tokens-studio.scss' as studio;

.flow-popover.el-popover.el-popper,
.flow-map-popover.el-popover.el-popper {
  @include studio.studio-tokens;

  background: var(--elevated);
  border: 1px solid var(--line);
  color: var(--t2);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.45);

  .el-popper__arrow::before {
    background: var(--elevated);
    border-color: var(--line);
  }
}
</style>
