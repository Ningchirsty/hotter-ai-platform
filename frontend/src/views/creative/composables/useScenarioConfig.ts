import { ref, watch, type Ref } from 'vue';
import { getDeliveryType, listOutputSpecs, listScenarioSteps } from '@/api/creative/scenario';
import { formatDefaultSpec, formatScenarioLine } from './scenarioText';

/**
 * 场景配置（V0.2 D 阶段第一刀）：把 B1/R11 建好的 `/creative/v2/*` 配置**读进来展示**。
 *
 * <p><b>只读不驱动</b>：本 composable 不参与任何流程判定——流程仍由既有阶段机与八步指引决定。
 * 它的作用只有一个：让人在界面上看到"这个项目属于哪个场景、默认出什么规格、配置里有哪些步骤"。</p>
 *
 * <p><b>失败必须静默</b>：配置层读不到（未部署/无权限/网络问题）时把 <code>line</code> 置空、
 * 标记 <code>failed</code>，**不抛异常、不弹提示**——一个只读的补充信息不该影响任何既有交互。
 * 交付类型为空（例如老项目没这个字段）时直接不加载。</p>
 */

export function useScenarioConfig(deliveryType: Ref<string | undefined>) {
  /** 展示行（空串 = 不显示） */
  const line = ref('');
  /** 配置里的步骤名（按 sortNo，用于 hover 展开） */
  const stepNames = ref<string[]>([]);
  const loading = ref(false);
  const failed = ref(false);
  /** 已加载过的交付类型 → 结果缓存，避免五个页面来回切时重复请求 */
  const cache = new Map<string, { line: string; steps: string[] }>();

  async function load() {
    const type = (deliveryType.value || '').trim();
    if (!type) {
      // 没有交付类型（老项目或字段为空）：不加载、不显示，也不算失败
      line.value = '';
      stepNames.value = [];
      failed.value = false;
      return;
    }
    const hit = cache.get(type);
    if (hit) {
      line.value = hit.line;
      stepNames.value = hit.steps;
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
      failed.value = false;
      cache.set(type, { line: text, steps: names });
    } catch (e) {
      // 静默降级：只读补充信息不该影响页面；不弹提示、不阻断
      line.value = '';
      stepNames.value = [];
      failed.value = true;
      cache.delete(type);
    } finally {
      loading.value = false;
    }
  }

  watch(deliveryType, () => void load(), { immediate: true });

  return { line, stepNames, loading, failed, reload: load };
}
