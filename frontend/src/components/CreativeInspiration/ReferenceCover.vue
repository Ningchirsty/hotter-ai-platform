<template>
  <div class="reference-cover" :style="{ aspectRatio: `${cover.width} / ${cover.height}` }">
    <img :src="referenceImage" :alt="title" loading="lazy" :style="imageStyle" />
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import referenceImage from '@/assets/images/inspiration-reference.png';
import type { CoverRegion } from './types';
const props = defineProps<{ cover: CoverRegion; title: string }>();
const imageStyle = computed(() => ({
  width: `${(1920 / props.cover.width) * 100}%`,
  left: `${(-props.cover.x / props.cover.width) * 100}%`,
  top: `${(-props.cover.y / props.cover.height) * 100}%`
}));
</script>

<style scoped>
.reference-cover {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: #f1eee9;
  border-radius: 10px;
}
.reference-cover img {
  position: absolute;
  max-width: none;
  height: auto;
  pointer-events: none;
}
</style>
