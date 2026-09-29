<template>
  <div class="ws" :data-workspace="workspaceName" :data-panels="assembledCodes.join(',')">
    <!-- 按配置顺序装配：GUIDE / MAIN / COMPONENT / SKIP -->
    <template v-for="slot in plan" :key="slot.code">
      <!-- 步骤导航：指引线（R16 起配置驱动；这里由工作台托管面板入口） -->
      <CreativeFlowGuide
        v-if="slot.target === 'GUIDE'"
        :task-id="taskId"
        :refresh-token="refreshToken"
        :deliverable-type="deliverableType"
        :flow="flow"
        :host-panels="false"
        @open-panel="onOpenPanel"
      />

      <!-- 主舞台：页面自身内容 -->
      <main v-else-if="slot.target === 'MAIN'" class="ws-main">
        <slot name="main" />
      </main>

      <!-- 已是独立组件的面板：按名字从真实注册表解析（INSPECTOR / ASSET_DRAWER 等） -->
      <component
        :is="resolve(slot.code)"
        v-else-if="slot.target === 'COMPONENT' && slot.code === 'INSPECTOR'"
        v-model:visible="panels.inspector"
        :task-id="taskId"
        :project-name="flow.project.value?.taskName || ''"
        :stage="flow.stage.value"
        :stage-label="flow.stageLabel.value"
        :current-step="currentStep"
        :project-steps="flow.projectSteps.value"
        :on-load-detail="loadCurrentStepDetail"
      />
      <component
        :is="resolve(slot.code)"
        v-else-if="slot.target === 'COMPONENT' && slot.code === 'ASSET_DRAWER'"
        v-model:visible="panels.assets"
        :task-id="taskId"
        :project-name="flow.project.value?.taskName || ''"
      />
      <!-- 其它已实现组件：目前只有上面两个需要额外入参，其余按需再接 -->
      <component
        :is="resolve(slot.code)"
        v-else-if="slot.target === 'COMPONENT'"
        v-bind="{ taskId }"
      />
      <!-- SKIP 的槽位什么都不渲染：为什么跳过由装配对照胶囊说明（不在这里堆提示） -->
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, type Component } from 'vue';
import CreativeFlowGuide from './CreativeFlowGuide.vue';
import CreativeInspectorPanel from './CreativeInspectorPanel.vue';
import CreativeAssetDrawer from './CreativeAssetDrawer.vue';
import { useCreativeFlow } from '../composables/useCreativeFlow';
import { assembledSlots, buildAssemblyPlan } from '../composables/workspaceAssembly';
import { resolveWorkspaceComponent } from './workspace/registry';

/**
 * 工作台装配运行时（V0.2 D 阶段，R19）。
 *
 * <p><b>它解决什么</b>：在此之前"一个页面长什么样"是各页 `.vue` 里写死的；
 * 装配定义（`dp_workspace_schema.layout_json`）里的 5 个面板 + 十步组件只是**对照展示**。
 * 这个容器让页面按配置装配：顺序、有哪些槽位都来自配置，组件从真实注册表解析。</p>
 *
 * <p><b>三件不做的事</b>：</p>
 * <ul>
 *   <li>不渲染空白：跳过（SKIP）的槽位不占位，原因由「工作台装配」胶囊说明；</li>
 *   <li>不假装实现：只有真注册过的组件才会被解析出来；</li>
 *   <li>不因配置层挂了而变空：配置读不到时给兜底计划（指引线 + 主舞台），页面内容照常。</li>
 * </ul>
 *
 * <p><b>它是一个"状态宿主"</b>：`useCreativeFlow` 只在这里创建一次，把同一个实例交给指引线
 * （步骤导航）与检查器——避免"页面里两份流程状态、各自发一遍请求"。
 * 指引线因此在工作台里不再自己托管面板入口（`host-panels=false`），改由这里统一开合。</p>
 *
 * @author creative
 */
const props = defineProps<{
  /** 当前项目ID */
  taskId?: string | number;
  /** 交付类型提示（没有就用项目数据里的） */
  deliverableType?: string;
  /** 刷新令牌：页面完成某个动作后 +1，指引线重读阶段 */
  refreshToken?: number;
}>();

/** 唯一的流程状态实例（指引线、检查器共用） */
const flow = useCreativeFlow(
  computed(() => props.taskId),
  computed(() => props.deliverableType)
);

/** 面板开合（工作台统一托管） */
const panels = reactive({ inspector: false, assets: false });

/** 装配计划：来自配置里的面板清单；配置读不到时是兜底计划 */
const plan = computed(() => buildAssemblyPlan(flow.assembly.value?.panelRows));
/** 真的渲染出来的槽位编码（挂到 DOM 上供验收与排障） */
const assembledCodes = computed(() => assembledSlots(plan.value).map((s) => s.code));
const workspaceName = computed(() => flow.assembly.value?.workspace || 'FALLBACK');

/** 当前步（进行中的那一步）：检查器要它 */
const currentStep = computed(() => flow.steps.value.find((s) => s.no === flow.activeNo.value) || null);

/** 按名字解析组件（没实现返回 null，模板里那一支就不会渲染） */
function resolve(name: string): Component | null {
  return resolveWorkspaceComponent(name);
}

/** 指引线请求开面板（工作台托管时它只发事件，不自己开） */
function onOpenPanel(code: string) {
  if (code === 'INSPECTOR') {
    panels.inspector = true;
    return;
  }
  if (code === 'ASSET_DRAWER') {
    panels.assets = true;
  }
}

/** 让检查器能请求"读取当前步明细"（判据仍在 useCreativeFlow 里，不重复实现） */
function loadCurrentStepDetail() {
  if (currentStep.value) {
    void flow.ensureStep(currentStep.value.key);
  }
}
</script>

<style scoped lang="scss">
/* 工作台只是装配容器，不引入任何视觉：页面观感与改造前一致（原先就是"指引线 + 内容"） */
.ws {
  display: block;
}

.ws-main {
  display: block;
}
</style>
