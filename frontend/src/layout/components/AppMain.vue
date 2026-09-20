<template>
  <section class="app-main">
    <router-view v-slot="{ Component, route }">
      <transition :enter-active-class="animate" mode="out-in">
        <keep-alive :include="tagsViewStore.cachedViews">
          <component :is="Component" v-if="!route.meta.link" :key="route.path" />
        </keep-alive>
      </transition>
    </router-view>
    <iframe-toggle />
    <!-- 设计规范：内容区底部一行克制的版权条（页面短时贴底，长时跟随内容） -->
    <footer class="app-main-footer">
      <span>纵享集团AI创作平台</span>
      <span>让灵感，自然绽放</span>
    </footer>
  </section>
</template>

<script setup name="AppMain" lang="ts">
import animateConfig from '@/animate';
import { useFullHeightTable } from '@/hooks/table/useFullHeightTable';
import { useSettingsStore } from '@/store/modules/settings';
import { useTagsViewStore } from '@/store/modules/tagsView';
import IframeToggle from './IframeToggle/index.vue';

const route = useRoute();
const tagsViewStore = useTagsViewStore();
useFullHeightTable();

// 随机动画集合
const animate = ref<string>('');
watch(
  () => useSettingsStore().animationEnable,
  (val: boolean) => {
    if (val) {
      animate.value = animateConfig.animateList[Math.floor(Math.random() * animateConfig.animateList.length)] as string;
    } else {
      animate.value = animateConfig.defaultAnimate as string;
    }
  },
  { immediate: true }
);

watchEffect(() => {
  addIframe();
});

function addIframe() {
  if (route.meta.link) {
    useTagsViewStore().addIframeView(route);
  }
}
</script>

<style lang="scss" scoped>
.app-main {
  min-height: 100vh;
  width: 100%;
  position: relative;
  overflow: hidden;
  padding: 12px;
  // 让底部版权条在内容不足一屏时贴底
  display: flex;
  flex-direction: column;

  &:fullscreen,
  &:-webkit-full-screen,
  &:-moz-full-screen,
  &:-ms-fullscreen {
    background: var(--el-bg-color);
    overflow-y: auto;
  }
}

.app-main:not(.with-fixed-header) {
  min-height: calc(100vh - 64px);
}

.app-main.with-tags-view:not(.with-fixed-header) {
  min-height: calc(100vh - 105px);
}

.app-main.with-fixed-header {
  padding-top: 76px;
  min-height: calc(100vh - 76px);
}

.app-main.with-fixed-header.with-tags-view {
  min-height: calc(100vh - 111px);
  padding-top: 111px;
}

.app-main-footer {
  margin-top: auto;
  padding: 14px 4px 2px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 10px;
  letter-spacing: 0.3px;
  color: var(--app-text-muted);
  opacity: 0.8;
}
</style>
<style lang="scss">
// fix css style bug in open el-dialog
.el-popup-parent--hidden {
  .fixed-header {
    padding-right: 6px;
  }
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background-color: var(--el-fill-color-lighter);
}

::-webkit-scrollbar-thumb {
  background-color: var(--el-text-color-placeholder);
  border-radius: 999px;
}
</style>
