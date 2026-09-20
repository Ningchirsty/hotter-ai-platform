<template>
  <div class="sidebar-shell" :class="{ 'has-logo': showLogo }" :style="menuStyle">
    <logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar :class="sideTheme" wrap-class="scrollbar-wrapper">
      <transition :enter-active-class="animateConfig.menuSearchAnimate.enter" mode="out-in">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :unique-opened="true"
          :collapse-transition="false"
          :popper-offset="12"
          mode="vertical"
        >
          <sidebar-item v-for="(r, index) in sidebarRouters" :key="r.path + index" :item="r" :base-path="r.path" />
        </el-menu>
      </transition>
    </el-scrollbar>
    <!-- 设计规范：侧栏底部一行安静的品牌短句；收起侧栏时隐藏，避免挤压菜单 -->
    <div v-if="!isCollapse" class="sidebar-footer">
      <p>每一份灵感，都值得绽放。</p>
      <span>IMAGINATION IN BLOOM</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { RouteRecordRaw } from 'vue-router';
import animateConfig from '@/animate';
import { useAppStore } from '@/store/modules/app';
import { usePermissionStore } from '@/store/modules/permission';
import { useSettingsStore } from '@/store/modules/settings';
import Logo from './Logo.vue';
import SidebarItem from './SidebarItem.vue';

const route = useRoute();
const appStore = useAppStore();
const settingsStore = useSettingsStore();
const permissionStore = usePermissionStore();
const sidebarRouters = computed<RouteRecordRaw[]>(() => permissionStore.getSidebarRoutes());
const showLogo = computed(() => settingsStore.sidebarLogo);
const sideTheme = computed(() => settingsStore.sideTheme);
const theme = computed(() => settingsStore.theme);
const isCollapse = computed(() => !appStore.sidebar.opened);

const activeMenu = computed(() => {
  const { meta, path } = route;
  // if set path, the sidebar will highlight the path you set
  if (meta.activeMenu) {
    return meta.activeMenu;
  }
  return path;
});

const bgColor = computed(() => (sideTheme.value === 'theme-dark' ? '#111827' : '#ffffff'));
// 浅色侧栏文字沿用 design-tokens 的 $zx-text（#475c70），与 _shell.scss 的 --side-menu-text 保持一致
const textColor = computed(() => (sideTheme.value === 'theme-dark' ? '#e5edf8' : '#475c70'));
const menuStyle = computed(() => ({
  backgroundColor: bgColor.value,
  '--el-menu-bg-color': bgColor.value,
  '--el-menu-text-color': textColor.value,
  '--el-menu-active-color': theme.value
}));
</script>

<style lang="scss" scoped>
.sidebar-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 8px 12px;
  border: 1px solid var(--app-sidebar-border);
  border-radius: var(--app-radius-base);
  box-shadow: var(--app-shadow-sm);
  background: v-bind(bgColor) !important;
  overflow: hidden;
}

:deep(.el-scrollbar__view) {
  min-height: 0;
  padding-bottom: 12px;
}

:deep(.el-scrollbar) {
  flex: 1;
  min-height: 0;
  height: auto !important;
}

:deep(.el-scrollbar__wrap) {
  height: 100%;
  overflow-x: hidden;
}

.sidebar-footer {
  flex-shrink: 0;
  padding: 10px 10px 2px;
  border-top: 1px solid var(--app-sidebar-border);
  line-height: 1.5;
}

.sidebar-footer p {
  margin: 0;
  font-size: 11px;
  color: var(--app-text-muted);
}

.sidebar-footer span {
  font-size: 9px;
  letter-spacing: 0.12em;
  color: var(--app-text-muted);
  opacity: 0.75;
}
</style>
