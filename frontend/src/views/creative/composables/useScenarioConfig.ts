import { computed, ref, watch, type Ref } from 'vue';
import {
  getDeliveryType,
  getScenario,
  getWorkspace,
  listOutputSpecs,
  listScenarioSteps
} from '@/api/creative/scenario';
import type { ScenarioOutputSpec, ScenarioStep } from '@/api/creative/scenario';
import { formatDefaultSpec, formatScenarioLine } from './scenarioText';
import { formatMappingChip, mapFlowSteps } from './flowStepMapping';
import {
  diffWorkspaceAssembly,
  formatAssemblyChip,
  layoutOfWorkspace,
  referencedSchemaCode,
  type AssemblyDiff,
  type WorkspaceLayout
} from './workspaceAssembly';

/**
 * 场景配置（V0.2 D 阶段）：把 B1/R11 建好的 `/creative/v2/*` 配置**读进来展示**。
 *
 * <p>提供四样东西：① 一行场景摘要；② 配置里的步骤名（hover 用）；③ 配置步骤与代码八步的
 * <b>步序对照</b>（R15：先把差异暴露出来再切驱动）；④ <b>工作台装配对照</b>
 * （R17：配置声明的面板/组件 vs 代码里的真实落点，让"切装配要做什么"可核对）。</p>
 *
 * <p><b>失败必须静默</b>：配置层读不到（未部署/无权限/网络问题）时把 <code>line</code> 置空、
 * 标记 <code>failed</code>，**不抛异常、不弹提示**——一个只读的补充信息不该影响任何既有交互。
 * 交付类型为空（例如老项目没这个字段）时直接不加载。</p>
 */

/** 缓存里存的东西（同一个交付类型只读一次配置） */
interface ScenarioCacheEntry {
  line: string;
  names: string[];
  steps: ScenarioStep[];
  /** 工作台装配对照（R17） */
  assembly: AssemblyDiff | null;
  /** 工作台装配定义（R41：工作台运行时要用它——步骤、组件、页面限定都在这里） */
  layout: WorkspaceLayout | null;
  /** 默认输出规格（R31：项目头部显示渠道/尺寸用，避免页面再发一次同样的请求） */
  defaultSpec: ScenarioOutputSpec | null;
}

/**
 * 已加载过的交付类型 → 结果缓存。
 *
 * <p><b>刻意放在模块级</b>：R13 时它是 composable 内的实例缓存，注释写着"避免五个页面来回切时重复请求"，
 * 但实际上每次切页面都会重新 mount、实例缓存跟着丢——那句注释从来没成立过。放到模块级才真的省下请求
 * （同一个交付类型在同一会话内只读一次配置；配置改动靠刷新页面生效，这与 R11 以来"配置只读"的定位一致）。</p>
 */
const scenarioCache = new Map<string, ScenarioCacheEntry>();

/**
 * 已经重试过的交付类型（R31）。
 *
 * <p>同样放模块级：配置首载失败只重试一次，避免网络抖动时把五个页面都变成重试风暴。</p>
 */
const retried = new Set<string>();

export function useScenarioConfig(deliveryType: Ref<string | undefined>) {
  /** 展示行（空串 = 不显示） */
  const line = ref('');
  /** 配置里的步骤名（按 sortNo，用于 hover 展开） */
  const stepNames = ref<string[]>([]);
  /** 配置里的步骤（原样保留，供步序对照与指引线计划用） */
  const scenarioSteps = ref<ScenarioStep[]>([]);
  /** 工作台装配定义（来自场景档案的 workspace_schema_json） */
  const assembly = ref<AssemblyDiff | null>(null);
  /** 工作台装配定义（R41：工作台运行时用它，不再从对照行反推） */
  const layout = ref<WorkspaceLayout | null>(null);
  /** 默认输出规格（R31：项目头部显示渠道与尺寸，取的是同一份缓存，不再单独发请求） */
  const defaultSpec = ref<ScenarioOutputSpec | null>(null);
  const loading = ref(false);
  const failed = ref(false);

  /** 步序对照（配置步骤 ↔ 代码八步）；没有配置时各项为空，界面据此不渲染 */
  const mapping = computed(() => mapFlowSteps(scenarioSteps.value));
  /** 对照胶囊文字（形如 `步序对照 9/10`） */
  const mappingChip = computed(() => formatMappingChip(mapping.value));
  /** 装配对照胶囊文字（形如 `工作台装配 1/15`） */
  const assemblyChip = computed(() => formatAssemblyChip(assembly.value));

  async function load() {
    const type = (deliveryType.value || '').trim();
    if (!type) {
      // 没有交付类型（老项目或字段为空）：不加载、不显示，也不算失败
      line.value = '';
      stepNames.value = [];
      scenarioSteps.value = [];
      assembly.value = null;
      layout.value = null;
      defaultSpec.value = null;
      failed.value = false;
      return;
    }
    const hit = scenarioCache.get(type);
    if (hit) {
      line.value = hit.line;
      stepNames.value = hit.names;
      scenarioSteps.value = hit.steps;
      assembly.value = hit.assembly;
      layout.value = hit.layout;
      defaultSpec.value = hit.defaultSpec;
      failed.value = false;
      return;
    }
    loading.value = true;
    try {
      const [deliveryRes, specRes, stepRes, profileRes, workspaceRes] = await Promise.all([
        getDeliveryType(type),
        listOutputSpecs(type),
        listScenarioSteps(type),
        getScenario(type),
        getWorkspace(type)
      ]);
      const delivery = deliveryRes.data;
      const specs = specRes.data || [];
      const steps = stepRes.data || [];
      const names = steps.map((s) => s.stepName || s.stepCode || '').filter(Boolean);
      const text = formatScenarioLine(delivery?.deliveryName, type, formatDefaultSpec(specs), names.length);
      // 装配对照：定义在 dp_workspace_schema.layout_json（档案里那个字段只是引用），
      // 顺手把"档案引用的工作台编码 vs 实际发布的编码"对一次账（不一致要看得见）
      const parsedLayout = layoutOfWorkspace(workspaceRes.data);
      const assemblyDiff = diffWorkspaceAssembly(parsedLayout, {
        referencedCode: referencedSchemaCode(profileRes.data),
        actualCode: workspaceRes.data?.schemaCode
      });
      line.value = text;
      stepNames.value = names;
      scenarioSteps.value = steps;
      assembly.value = assemblyDiff;
      layout.value = parsedLayout;
      defaultSpec.value = specs.length ? specs[0] : null;
      failed.value = false;
      scenarioCache.set(type, {
        line: text,
        names,
        steps,
        assembly: assemblyDiff,
        layout: parsedLayout,
        defaultSpec: defaultSpec.value
      });
    } catch (e) {
      // 静默降级：只读补充信息不该影响页面；不弹提示、不阻断。
      // 但**首载失败要多试一次**（R31 真机验收里出现过一次：同一项目第二次打开就正常，
      // 说明是冷启动/网络抖动）。重试仍失败就如实降级——那时页面还有兜底装配，不会变空。
      line.value = '';
      stepNames.value = [];
      scenarioSteps.value = [];
      assembly.value = null;
      layout.value = null;
      defaultSpec.value = null;
      failed.value = true;
      scenarioCache.delete(type);
      if (!retried.has(type)) {
        retried.add(type);
        // 模块级集合：同一个交付类型在一次会话里只重试一次，避免抖动时打成重试风暴
        setTimeout(() => void load(), 1500);
      }
    } finally {
      loading.value = false;
    }
  }

  watch(deliveryType, () => void load(), { immediate: true });

  return {
    line,
    stepNames,
    scenarioSteps,
    mapping,
    mappingChip,
    assembly,
    layout,
    assemblyChip,
    defaultSpec,
    loading,
    failed,
    reload: load
  };
}
