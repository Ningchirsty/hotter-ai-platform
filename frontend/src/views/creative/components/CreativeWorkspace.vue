<template>
  <div
    class="ws"
    :data-workspace="workspaceName"
    :data-panels="assembledCodes.join(',')"
    :data-step-visible="visibleStep || ''"
    :data-step-hosted="hostedCodes.join(',')"
    :data-step-components="visibleComponents.join(',')"
  >
    <!--
      任务来自岗位卡片时显示"返回 AI 工作台"（R44 / 增量 4）。
      放在工作台容器里而不是各页各写一遍：五个创作页都用它，一处接线就全都有。
      不是岗位发起的任务（或不是自己发起的）时组件自己隐藏，不影响本页。
    -->
    <BackToWorkspace :task-id="taskId" />
    <!-- 按配置顺序装配：GUIDE / MAIN / COMPONENT / SKIP -->
    <template v-for="slot in plan" :key="slot.code">
      <!-- 步骤导航：指引线（R16 起配置驱动；这里由工作台托管面板入口） -->
      <CreativeFlowGuide
        v-if="slot.target === 'GUIDE'"
        :task-id="taskId"
        :refresh-token="refreshToken"
        :deliverable-type="deliverableType"
        :flow="flow"
        :panels-hosted-by-parent="true"
        @open-panel="onOpenPanel"
      />

      <!-- 主舞台：页面自身内容 -->
      <main v-else-if="slot.target === 'MAIN'" class="ws-main">
        <!--
          ⓪ R41：没选项目时**没有步骤可言**（指引线也显示"未选择项目"）。这时若页面提供了
             `#main`（例如生产页的"跨项目候选总览"），就渲染它——但仍先渲染页头，
             否则用户连"选项目"的下拉框都看不到，等于把自己关在门外。
        -->
        <template v-if="projectLess">
          <slot name="page-head" />
          <slot name="main" />
        </template>

        <!--
          ① R37：页面把**每一步**的内容以同名插槽递进来（如 `#ProjectAssetsBlock`）→
             工作台按配置里的「步骤 → 组件」只装配**当前那一步**，其余步骤用上面的步骤条切换。
             为什么这样定（用户已确认）：一屏只回答"这一步该做什么"，
             六个区块全堆在一起的页面没人读得完。
        -->
        <template v-else-if="hosted.length">
          <!-- 页头（R38）：这一步所属页面的框架（项目选择器 / 页面动作）。
               它不属于"某一步的内容"，所以放在步骤条与内容区之上，由页面用 #page-head 提供；
               没提供的页面（项目页的头部是配置里的 PROJECT_HEADER）这里就是空的。 -->
          <slot name="page-head" />

          <div v-if="hosted.length > 1" class="ws-step-bar">
            <span class="bar-label">本页步骤</span>
            <button
              v-for="item in hosted"
              :key="item.code"
              type="button"
              class="step-tab"
              :class="['is-' + item.status, { active: item.code === visibleStep }]"
              :title="item.hint"
              @click="selectStep(item.code)"
            >
              {{ item.no }}. {{ item.name }}
              <span class="tab-status">{{ item.statusLabel }}</span>
            </button>
          </div>

          <!--
            辅助入口（v1 反馈的连带影响）：流程指引里原来带着「检查器 / 资产 / 模块规划」三个入口，
            而 R44 把流程指引从基因 / 分镜 / 生产 / 审核四页裁掉之后，那几个面板**没人能打开了**
            （装配里还在、只是没有触发点）。这里在**没有指引线**的页面上补一条最小入口，
            文案与指引线、项目页头部保持一致。
            有指引线时不渲染——避免同一页出现两排一样的按钮。
          -->
          <div v-if="!hasGuide" class="ws-panel-bar">
            <span class="bar-label">辅助</span>
            <button type="button" class="panel-tab" :disabled="!taskId" @click="panels.inspector = true">
              检查器
            </button>
            <button type="button" class="panel-tab" :disabled="!taskId" @click="panels.assets = true">
              资产
            </button>
            <button type="button" class="panel-tab" :disabled="!taskId" @click="panels.qa = true">
              质检与交付
            </button>
            <button type="button" class="panel-tab" :disabled="!taskId" @click="openModulePlan">
              模块规划
            </button>
          </div>

          <section class="ws-stage">
            <div class="ws-stage-body">
              <template v-for="component in visibleComponents" :key="component">
                <slot :name="component" />
              </template>
              <!-- 配置里声明了、页面却没提供插槽的组件：如实说出来（不渲染空白，也不假装装上了） -->
              <p v-if="visibleMissing.length" class="ws-gap">
                这一步还声明了 {{ visibleMissing.join('、') }}，但页面上没有提供对应的插槽——
                配置与代码对不上，需要补插槽或在配置里去掉它。
              </p>
            </div>
          </section>

          <p class="ws-step-note">
            只显示当前这一步（{{ visibleName }}）：{{ visibleComponents.length }} 块内容。
            切换步骤用上面的「本页步骤」，跳整个流程用流程指引线。
            <span v-if="!visibleStepIsCurrent" class="note-follow">
              （这一步不是流程当前步，流程当前在「{{ currentStepName || '未知' }}」——用指引线可跳回去）
            </span>
          </p>
        </template>

        <!--
          ② 页面没有按步骤提供插槽（评审 / 生产两页还是整页一个主区）：
             保持 R19 的行为——主舞台渲染页面自己的内容。装配是**逐步接入**的，
             没接入的页面行为一个字不变。
        -->
        <slot v-else name="main" />

        <!--
          ③ 页面按步骤给了插槽，但配置里没有可用的装配定义（读失败 / 交付类型为空）：
             这时既不能白屏、也不该把锅甩给"名字对不上"——配置根本没来，说清楚是哪一种。
             注意只在配置**收工之后**才这么提示：首屏那一小段"正在读配置"不该报警。
        -->
        <p v-if="!hosted.length && !hasMainSlot && configMissing" class="ws-gap">
          工作台装配配置没读到（交付类型为空或配置接口不可用），所以本页按步骤装配的内容暂时显示不出来。
          页面顶部的流程指引线仍可用；刷新一次通常就能恢复。
        </p>
        <!-- 配置在、但一个组件名都没对上页面提供的插槽：两边各是什么，如实列出来 -->
        <p v-else-if="!hosted.length && !hasMainSlot && providedComponents.length" class="ws-gap">
          配置里的步骤组件（{{ declaredComponents.join('、') || '无' }}）与页面提供的插槽
          （{{ providedComponents.join('、') }}）对不上，所以这一步没有可装配的内容。
          请在配置里改用真实组件名，或让页面提供同名插槽。
        </p>
      </main>

      <!-- 项目头部（文档 §23 的 PROJECT_HEADER；R31 起它是真组件） -->
      <component
        :is="resolve(slot.code)"
        v-else-if="slot.target === 'COMPONENT' && slot.code === 'PROJECT_HEADER'"
        :project="headerProject"
        :spec="headerSpec"
        :stage-label="flow.stageLabel.value"
        :stage-type="stageType"
        :loading="loading"
        @refresh="$emit('refresh')"
        @open-qa="panels.qa = true"
        @open-assets="panels.assets = true"
        @open-logs="$emit('open-logs')"
      >
        <template #actions><slot name="header-actions" /></template>
      </component>

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

    <!--
      QaPanel 是**步骤组件**（场景里的 QA 步），不在面板清单里，所以它不由上面的计划渲染；
      工作台把它当"可开合抽屉"托管，入口在项目头部（质检与交付）。
      这样它既是真注册表里的组件（装配对照 11/18），又能在任意工作台里被打开一次看到全部结论。
    -->
    <CreativeQaPanel
      v-model:visible="panels.qa"
      :task-id="taskId"
      :project-name="project?.taskName || flow.project.value?.taskName || ''"
      :project="project"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, provide, reactive, ref, watch, useSlots, type Component } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import CreativeFlowGuide from './CreativeFlowGuide.vue';
import CreativeInspectorPanel from './CreativeInspectorPanel.vue';
import CreativeAssetDrawer from './CreativeAssetDrawer.vue';
import CreativeQaPanel from './CreativeQaPanel.vue';
import BackToWorkspace from '@/views/aigov/portal/BackToWorkspace.vue';
import { useCreativeFlow } from '../composables/useCreativeFlow';
import { provideStepNumbering } from '../composables/stepNumbering';
import {
  assembledSlots,
  buildAssemblyPlan,
  hostedSteps,
  pickVisibleStep,
  stepComponentsForPage
} from '../composables/workspaceAssembly';
import { resolveWorkspaceComponent } from './workspace/registry';
import type { CreativeProjectVO } from '@/api/creative/types';
import type { ScenarioOutputSpec } from '@/api/creative/scenario';

/**
 * 工作台装配运行时（V0.2 D 阶段，R19；R37 起按步骤装配）。
 *
 * <p><b>它解决什么</b>：在此之前"一个页面长什么样"是各页 `.vue` 里写死的；
 * 装配定义（`dp_workspace_schema.layout_json`）里的面板 + 步骤组件只是**对照展示**。
 * 这个容器让页面按配置装配：顺序、有哪些槽位、**当前该显示哪一步的哪几块**都来自配置。</p>
 *
 * <p><b>四件不做的事</b>：</p>
 * <ul>
 *   <li>不渲染空白：跳过（SKIP）的槽位不占位；步骤声明了组件而页面没给插槽时，明说对不上；</li>
 *   <li>不假装实现：只有真注册过的组件才会被解析出来；</li>
 *   <li>不因配置层挂了而变空：配置读不到时给兜底计划（指引线 + 主舞台），页面内容照常；</li>
 *   <li>不一次堆六块：R37 起主舞台只装配**当前这一步**的组件，其余步骤用页面内的步骤条切换
 *       （用户已确认的口径）。</li>
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
  /** 当前项目（PROJECT_HEADER 与 QaPanel 要显示项目/SKU/负责人） */
  project?: CreativeProjectVO | null;
  /** 该交付类型的默认输出规格（头部显示渠道与尺寸；取不到就如实说未配置） */
  outputSpec?: ScenarioOutputSpec | null;
  /** 页面是否正在加载（头部按钮的 loading） */
  loading?: boolean;
  /** 阶段样式类型（页面各自的 is-* 约定） */
  stageType?: string;
}>();

defineEmits<{
  (e: 'refresh'): void;
  (e: 'open-logs'): void;
}>();

/** 唯一的流程状态实例（指引线、检查器共用） */
const flow = useCreativeFlow(
  computed(() => props.taskId),
  computed(() => props.deliverableType)
);

/**
 * 刷新令牌：页面做完一个动作（锁定分镜、选定方向、提交视觉门…）就把它 +1。
 *
 * <p><b>为什么必须由工作台消化，而不是交给指引线</b>（R44，本地真机验出来的回归）：
 * 这个令牌原先只有「流程指引」面板在听，而面板从 R44 起可以按页面裁剪
 * （基因 / 分镜 / 生产 / 审核四页已经把流程指引去掉）。那四页于是**没人再读流程状态**——
 * 真机复现：锁定分镜之后分镜确实变成「已锁定」，但页面上的步骤条仍然写着
 * 「2. 分镜 **进行中**」「3. 出图 **前置未完成**」，页面也不跳到出图，
 * 看起来就像"锁定没生效"。工作台自己既持有这份状态、又渲染「本页步骤」，
 * 所以令牌该由它消化；指引线只在**自持**状态时才继续自己听（见该组件的 watch）。</p>
 */
watch(() => props.refreshToken, () => void flow.reload());

/**
 * 首屏加载流程数据（项目 / 附件 / 事实 / 字段选项 + 步骤状态）。
 *
 * <p><b>为什么工作台要自己加载</b>：这份活原先也挂在「流程指引」的
 * `watch(taskId, reload, { immediate: true })` 上——指引线一被裁掉，
 * 那四页的 `flow.project` / `flow.files` / `flow.facts` 就永远是空的
 * （步骤状态另有一条内部通道，所以步骤条看着是对的，掩盖了这件事）。
 * 后果是检查器与资产抽屉拿不到 `<b>项目名</b>`（标题空白）。
 * 工作台持有这个实例，就该由它保证首屏有数据。</p>
 */
watch(() => props.taskId, () => void flow.reload(), { immediate: true });

/** 面板开合（工作台统一托管）；qa 是"步骤组件抽屉"，不在面板清单里 */
const panels = reactive({ inspector: false, assets: false, qa: false });

/** 当前路由（R41 起装配按页面筛步骤组件；R44 起面板也按页面筛） */
const route = useRoute();
/** 路由跳转（辅助入口里的「模块规划」要带 taskId 深链过去，与指引线同一套做法） */
const router = useRouter();

/**
 * 装配计划：来自配置里的面板清单；配置读不到时是兜底计划。
 *
 * <p><b>R44（v1 反馈）</b>：面板也能写 `pages` 了。产品信息（`PROJECT_HEADER`）与
 * 流程指引（`STEP_NAVIGATOR`）只在视觉项目页出现——另外四页各自已经有页头
 * （页面用 `#page-head` 提供），再叠一层就是"两层标题、本页模块被挤到下面"。
 * 项目页没有自己的页头（它的头部就是 `PROJECT_HEADER`），所以这条路必须留着。</p>
 */
const plan = computed(() => buildAssemblyPlan(flow.assembly.value?.panelRows, undefined, route.path));
/** 真的渲染出来的槽位编码（挂到 DOM 上供验收与排障） */
const assembledCodes = computed(() => assembledSlots(plan.value).map((s) => s.code));
/** 本页装配里有没有流程指引：没有时工作台补一条最小的辅助入口（见模板） */
const hasGuide = computed(() => assembledCodes.value.includes('STEP_NAVIGATOR'));
const workspaceName = computed(() => flow.assembly.value?.workspace || 'FALLBACK');

/** 当前步（进行中的那一步）：检查器要它 */
const currentStep = computed(() => flow.steps.value.find((s) => s.no === flow.activeNo.value) || null);
/** 全局当前步的编码（指引线口径）；没有就空串 */
const activeStepCode = computed(() => currentStep.value?.key || '');

/**
 * 头部数据的工作台兜底（R39）。
 *
 * <p>页面显式传了就听页面的；没传（基因页 / 分镜页只传了 taskId）就从流程状态与场景配置里取——
 * 否则头部会显示"未选择项目 / 未配置"，而页面明明已经选好了项目（R31 的头部落到基因页时就是这样，
 * 只是当时没人从头部这一侧看）。</p>
 */
const headerProject = computed(() => props.project ?? flow.project.value);
const headerSpec = computed(() => props.outputSpec ?? flow.defaultSpec.value);

/**
 * 页面**按步骤**提供的插槽（= 组件名）。
 *
 * <p>`main` / `header-actions` / `page-head` 不是步骤组件，排除掉；
 * 页面没按步骤给插槽时这里是空数组，主舞台就回到 R19 的行为（渲染 `#main`）。</p>
 */
const slots = useSlots();
const providedComponents = computed(() =>
  Object.keys(slots).filter(
    (name) => !['main', 'header-actions', 'page-head', 'default'].includes(name)
  )
);
const hasMainSlot = computed(() => Boolean(slots.main));

/** 没有项目就没有步骤：这时若页面提供了 #main（跨项目总览之类），就渲染它 */
const projectLess = computed(() => !props.taskId && hasMainSlot.value);

/** 配置里的步骤（R41：直接用解析出来的装配定义，不再从对照行反推） */
const layoutSteps = computed(() => flow.workspaceLayout.value?.steps || []);
/** 这一步在本页面上应当有的组件（页面限定的组件只在它声明的页面上算） */
function componentsHere(stepCode: string): string[] {
  const step = layoutSteps.value.find((s) => s.code === stepCode);
  return step ? stepComponentsForPage(step, route.path).map((c) => c.name) : [];
}
/** 本页面声明到的全部组件名（用于"对不上"的提示） */
const declaredComponents = computed(() =>
  layoutSteps.value.flatMap((s) => stepComponentsForPage(s, route.path).map((c) => c.name))
);
/** 本页面托管的步骤：配置里有（且对本页生效）、页面也提供了插槽的那些 */
const hosted = computed(() => {
  const status = new Map(flow.steps.value.map((s) => [s.key, s]));
  return hostedSteps(layoutSteps.value, providedComponents.value, route.path).map((step, index) => {
    const hit = status.get(step.code);
    return {
      code: step.code,
      components: step.components,
      // v1 反馈：步骤条原来用流程里的全局步号（出图＝第 7 步），于是本页读起来是"1、2、7"。
      // 这里改成**本页序号**：它回答的是"在这个页面上按什么顺序看"。
      // 整条流程的顺序由「流程指引线」负责（那边仍用全局步号），两者分工不同。
      no: index + 1,
      name: hit?.name || step.code,
      status: hit?.status || 'todo',
      statusLabel: hit?.statusLabel || '未开始',
      hint: `只显示「${hit?.name || step.code}」这一步：${step.components.join(' + ')}`
        + (hit?.no ? `（流程第 ${hit.no} 步）` : '')
    };
  });
});
const hostedCodes = computed(() => hosted.value.map((s) => s.code));

/**
 * 「钉住某一步」：来自地址栏的 `?step=<步骤编码>`（例如视觉门那枚「去哪儿补」按钮
 * 跳到项目页的「产品资料与参考图」上传参考图——只跳到页面是不够的，页面默认显示的是
 * **当前步**，而"没上传参考图"这件事要补的那一步往往不是当前步；真机验过：
 * 跳到 `/creative/project` 会停在「出图」，上传框根本不在那一屏）。
 *
 * <p>与 `manualStep` 的区别：manualStep 是"用户点了步骤条"，只要流程推进就清掉；
 * 这个是**带着意图进来的**，要一直钉住，直到用户自己点别的步骤。</p>
 */
const pinnedStep = ref(typeof route.query.step === 'string' ? route.query.step : '');

/** 这一步显示哪一步：钉住的 → 手工选的 → 按"全局当前步 → 进行中 → 没了结 → 最后一步" */
const manualStep = ref('');
const visibleStep = computed(() => {
  if (pinnedStep.value && hostedCodes.value.includes(pinnedStep.value)) {
    return pinnedStep.value;
  }
  if (manualStep.value && hostedCodes.value.includes(manualStep.value)) {
    return manualStep.value;
  }
  return pickVisibleStep(hosted.value, activeStepCode.value) || '';
});
/** 步骤变了（流程推进 / 用户点了指引线）就回到"跟随当前步"，别把人按在旧的那一步上 */
watch(activeStepCode, () => {
  manualStep.value = '';
});

/** 这一步真的要渲染的组件（页面提供了插槽的）+ 声明了却没有的 */
const visible = computed(() => hosted.value.find((s) => s.code === visibleStep.value) || null);
const visibleComponents = computed(() => visible.value?.components || []);

/**
 * 本页步骤编号（v1 反馈）：区块标题里的号必须与"本页步骤"一致，
 * 否则会出现"事实确认是 4、文案与要点是 3，而 4 排在 3 前面"这种读不通的顺序。
 *
 * 只提供、不强制：区块自己不取（例如被用在别处）时就不显示编号——
 * 编一个可能错的号比没有号更难发现。
 */
provideStepNumbering(
  computed(() => ({
    no: visible.value?.no || 0,
    size: visibleComponents.value.length,
    orderOf: (component: string) => visibleComponents.value.indexOf(component) + 1
  }))
);
const visibleMissing = computed(() =>
  componentsHere(visibleStep.value).filter(
    (component) => !providedComponents.value.includes(component)
  )
);
const visibleName = computed(() => visible.value?.name || '');
const currentStepName = computed(() => currentStep.value?.name || '');
const visibleStepIsCurrent = computed(() => !activeStepCode.value || visibleStep.value === activeStepCode.value);
/** 装配配置**确定**没读到（不是"还在读"）：首屏竞态期间不报警，读完了还是没有才说 */
const configMissing = computed(() => !flow.configPending.value && !flow.assembly.value);

/** 点步骤条：手工选一步（下一次流程推进时自动回到跟随当前步） */
function selectStep(code: string) {
  manualStep.value = code;
  // 用户自己点了步骤条：放弃地址栏带来的钉住，之后按正常规则跟随
  pinnedStep.value = '';
}

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

/**
 * 打开「模块规划」页（辅助入口里的第四个按钮）。
 *
 * <p>与指引线的做法逐字一致：带 `taskId` 深链过去（模块计划挂在项目上，不带 ID 进去是空页），
 * 用 `push` 而不是 `replace`，这样改完能按返回回到刚才那一步。</p>
 */
function openModulePlan() {
  if (!props.taskId) {
    return;
  }
  void router.push({ path: '/creative/module-plan', query: { taskId: String(props.taskId) } });
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

/* 本页步骤条（R37）：一屏只显示一步，切步骤靠它——所以它必须在最显眼的位置，
   但不能抢指引线的位置：指引线回答"整个流程走到哪了"，这里回答"本页有哪几步" */
.ws-step-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;

  .bar-label {
    color: var(--t3);
    font-size: 11px;
  }
}

/* 辅助入口那一行的样式已抽到全局 creative-studio.scss（.ws-panel-bar / .bar-label / .panel-tab）：
   有指引线的页面（挂指引线卡片里）与没有指引线的页面（这里补的一行）必须长得一样，
   两处各写一份就会漂移——这是本轮"去掉重复定义"的一部分，选择器与外观都没变。 */

.step-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  color: var(--t2);
  font-size: 12px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 999px;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
  &:hover {
    color: var(--t1);
    border-color: var(--t3);
  }

  .tab-status {
    color: var(--t3);
    font-size: 11px;
  }

  &.is-doing {
    border-color: rgba(64, 158, 255, 0.45);

    .tab-status {
      color: #409eff;
    }
  }

  &.is-done .tab-status {
    color: #67c23a;
  }

  &.is-skipped .tab-status {
    color: var(--t3);
  }

  &.is-blocked .tab-status {
    color: #f56c6c;
  }

  &.active {
    color: var(--t1);
    background: var(--elevated);
    border-color: var(--t3);
    font-weight: 600;
  }
}

/* 这一步的内容区：观感与改造前一致（原来是页面自己的 .panel + .detail-body） */
.ws-stage {
  min-height: 420px;
  padding-bottom: 8px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}

.ws-stage-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 16px;
}

/* "配置与页面插槽对不上"的提示：这种时候绝不能白屏——必须说清两边各是什么 */
.ws-gap {
  margin: 0;
  padding: 10px 12px;
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.7;
  background: rgba(230, 162, 60, 0.08);
  border: 1px dashed rgba(230, 162, 60, 0.35);
  border-radius: 6px;
}

.ws-step-note {
  margin: 8px 0 0;
  color: var(--t3);
  font-size: 11px;
  line-height: 1.7;

  .note-follow {
    color: var(--t3);
  }
}
</style>
