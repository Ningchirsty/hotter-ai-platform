<template>
  <div class="aig-empty">
    <!--
      统一空态（走读 §5.5「空态三行式」）：
      ①图标 ②一句人话（**为什么这里是空的**）③下一步能做什么（slot 里放按钮）。
      为什么要有这个组件：原先 20+ 处列表"没数据就只剩表头"，人看不出是"没有"还是"没加载出来"，
      也不知道下一步该点哪——于是最常见的反应是反复刷新。
      （注：注释必须写在根元素**内部**——根级注释会被计成第二个根节点，构建期守卫会红。）
    -->
    <el-icon class="aig-empty__icon" :size="40"><component :is="icon" /></el-icon>
    <div class="aig-empty__title">{{ title }}</div>
    <div v-if="hint" class="aig-empty__hint">{{ hint }}</div>
    <div v-if="$slots.default" class="aig-empty__actions">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 统一空态（图标 + 一句人话 + 下一步动作）。
 *
 * <p><b>title 与 hint 的分工</b>：title 回答"为什么这里是空的"（例如"没有符合当前筛选的能力"），
 * hint 回答"下一步做什么"（例如"清掉筛选，或直接创建一个"）。只写其中一句都会让人卡住。</p>
 */
withDefaults(
  defineProps<{
    /** 图标名（Element Plus 图标组件名，如 Box / Search / Warning） */
    icon?: string;
    /** 一句人话：为什么这里是空的 */
    title: string;
    /** 下一步动作说明（可空；若给了按钮插槽通常配一句） */
    hint?: string;
  }>(),
  { icon: 'Box', hint: '' }
);
</script>

<style scoped>
.aig-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 28px 16px;
  color: var(--el-text-color-secondary);
}

.aig-empty__icon {
  color: var(--el-text-color-placeholder);
}

.aig-empty__title {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.aig-empty__hint {
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}

.aig-empty__actions {
  margin-top: 6px;
  display: flex;
  gap: 8px;
}
</style>
