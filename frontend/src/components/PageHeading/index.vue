<template>
  <div class="page-heading">
    <div class="page-heading-main">
      <h1 class="page-heading-title">{{ title }}</h1>
      <p v-if="subtitle" class="page-heading-subtitle">{{ subtitle }}</p>
    </div>
    <div class="page-heading-side">
      <span v-if="admin" class="page-heading-badge">
        <el-icon><Lock /></el-icon>
        {{ adminLabel }}
      </span>
      <slot name="actions"></slot>
    </div>
  </div>
</template>

<script setup lang="ts" name="PageHeading">
/**
 * 工作空间页面标题行。
 * 设计规范：页面标题约 22px、副标题 12px；不使用营销式大标题，管理类页面用角标标明访问范围。
 * 圆角、颜色取自工程既有 token（--app-text-title / --app-text-muted / --app-radius-md）。
 */
withDefaults(
  defineProps<{
    title: string;
    subtitle?: string;
    admin?: boolean;
    adminLabel?: string;
  }>(),
  {
    subtitle: '',
    admin: false,
    adminLabel: '管理员专属'
  }
);
</script>

<style lang="scss" scoped>
.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.page-heading-main {
  min-width: 0;
}

.page-heading-title {
  margin: 0;
  font-size: 22px;
  font-weight: 500;
  line-height: 1.4;
  letter-spacing: 0.6px;
  color: var(--app-text-title);
}

.page-heading-subtitle {
  margin: 5px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}

.page-heading-side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding-top: 4px;
}

.page-heading-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.6;
  white-space: nowrap;
  color: #6d8398;
  background: #edf2f6;
}

@media (max-width: 600px) {
  .page-heading-title {
    font-size: 20px;
    letter-spacing: 0.3px;
  }

  .page-heading {
    flex-wrap: wrap;
  }
}
</style>
