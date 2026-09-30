import { defineAsyncComponent, type Component } from 'vue';

/**
 * 工作台组件的**真实注册表**（V0.2 D 阶段，R18；R31 起含 PROJECT_HEADER 与 QaPanel）。
 *
 * <p>与 `workspaceAssembly.ts` 里那张"描述用"的注册表（{@link CODE_COMPONENT_REGISTRY}）不同：
 * 这张表是**装配运行时真正要用的**——`dp_workspace_schema.layout_json` 里的组件名 → 组件实现。
 * 两张表必须一致：`workspaceAssembly.spec.ts` 里有一条测试钉住
 * "描述表里分类为 `COMPONENT` 的每一项，这里都要有实现"（反之亦然）。</p>
 *
 * <p>为什么用 `defineAsyncComponent`：装配后一个工作台可能挂十几个面板，
 * 同步全量引入会让首屏包体跟着组件数线性增长；按需加载才能让"加一个组件"不拖慢所有人。</p>
 *
 * @author creative
 */
export const WORKSPACE_COMPONENTS: Record<string, Component> = {
  PROJECT_HEADER: defineAsyncComponent(() => import('../CreativeProjectHeader.vue')),
  STEP_NAVIGATOR: defineAsyncComponent(() => import('../CreativeFlowGuide.vue')),
  INSPECTOR: defineAsyncComponent(() => import('../CreativeInspectorPanel.vue')),
  ASSET_DRAWER: defineAsyncComponent(() => import('../CreativeAssetDrawer.vue')),
  QaPanel: defineAsyncComponent(() => import('../CreativeQaPanel.vue'))
};

/** 已实现的组件名 */
export const IMPLEMENTED_COMPONENT_NAMES = Object.keys(WORKSPACE_COMPONENTS);

/**
 * 取组件实现。
 *
 * @param name 配置里的组件名（`layout_json` 的 steps[].component 或 panels[]）
 * @returns 组件；没实现返回 null（调用方应如实显示"未实现"，而不是渲染空白）
 */
export function resolveWorkspaceComponent(name?: string | null): Component | null {
  if (!name) {
    return null;
  }
  return WORKSPACE_COMPONENTS[name] ?? null;
}
