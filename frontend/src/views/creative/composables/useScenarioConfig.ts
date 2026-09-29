import { computed, ref, watch, type Ref } from 'vue';
import { getDeliveryType, listOutputSpecs, listScenarioSteps } from '@/api/creative/scenario';
import type { ScenarioStep } from '@/api/creative/scenario';
import { formatDefaultSpec, formatScenarioLine } from './scenarioText';
import { formatMappingChip, mapFlowSteps } from './flowStepMapping';

/**
 * 场景配置（V0.2 D 阶段）：把 B1/R11 建好的 `/creative/v2/*` 配置**读进来展示**。
 *
 * <p><b>只读不驱动</b>：本 composable 不参与任何流程判定——流程仍由既有阶段机与八步指引决定。
 * 它提供三样东西：① 一行场景摘要；② 配置里的步骤名（hover 用）；③ 配置步骤与代码八步的
 * <b>步序对照</b>（D 阶段第二刀第一步：先把差异暴露出来，指引线一行不动）。</p>
 *
 * <p><b>失败必须静默</b>：配置层读不到（未部署/无权限/网络问题）时把 <code>line</code> 置空、
 * 标记 <code>failed</code>，**不抛异常、不弹提示**——一个只读的补充信息不该影响任何既有交互。
 * 交付类型为空（例如老项目没这个字段）时直接不加载。</p>
 */

/**
 * 已加载过的交付类型 → 结果缓存。
 *
 * <p><b>刻意放在模块级</b>：R13 时它是 composable 内的实例缓存，注释写着"避免五个页面来回切时重复请求"，
 * 但实际上每次切页面都会重新 mount、实例缓存跟着丢——那句注释从来没成立过。放到模块级才真的省下请求
 * （同一个交付类型在同一会话内只读一次配置；配置改动靠刷新页面生效，这与 R11 以来"配置只读"的定位一致）。</p>
 */
const scenarioCache = new Map<string, { line: string; names: string[]; steps: ScenarioStep[] }>();

export function useScenarioConfig(deliveryType: Ref<string | undefined>) {
  /** 展示行（空串 = 不显示） */
  const line = ref('');
  /** 配置里的步骤名（按 sortNo，用于 hover 展开） */
  const stepNames = ref<string[]>([]);
  /** 配置里的步骤（原样保留，供步序对照与指引线计划用） */
  const scenarioSteps = ref<ScenarioStep[]>([]);
  const loading = ref(false);
  const failed = ref(false);

  /** 步序对照（配置步骤 ↔ 代码八步）；没有配置时各项为空，界面据此不渲染 */
  const mapping = computed(() => mapFlowSteps(scenarioSteps.value));
  /** 对照胶囊文字（形如 `步序对照 9/10`） */
  const mappingChip = computed(() => formatMappingChip(mapping.value));

  async function load() {
    const type = (deliveryType.value || '').trim();
    if (!type) {
      // 没有交付类型（老项目或字段为空）：不加载、不显示，也不算失败
      line.value = '';
      stepNames.value = [];
      scenarioSteps.value = [];
      failed.value = false;
      return;
    }
    const hit = scenarioCache.get(type);
    if (hit) {
      line.value = hit.line;
      stepNames.value = hit.names;
      scenarioSteps.value = hit.steps;
      failed.value = false;
      return;
    }
    loading.value = true;
    try {
      const [deliveryRes, specRes, stepRes] = await Promise.all([
        getDeliveryType(type),
        listOutputSpecs(type),
        listScenarioSteps(type),
      ]);
      const delivery = deliveryRes.data;
      const specs = specRes.data || [];
      const steps = stepRes.data || [];
      const names = steps.map((s) => s.stepName || s.stepCode || '').filter(Boolean);
      const text = formatScenarioLine(delivery?.deliveryName, type, formatDefaultSpec(specs), names.length);
      line.value = text;
      stepNames.value = names;
      scenarioSteps.value = steps;
      failed.value = false;
      scenarioCache.set(type, { line: text, names, steps });
    } catch (e) {
      // 静默降级：只读补充信息不该影响页面；不弹提示、不阻断
      line.value = '';
      stepNames.value = [];
      scenarioSteps.value = [];
      failed.value = true;
      scenarioCache.delete(type);
    } finally {
      loading.value = false;
    }
  }

  watch(deliveryType, () => void load(), { immediate: true });

  return { line, stepNames, scenarioSteps, mapping, mappingChip, loading, failed, reload: load };
}
