<template>
  <div class="generated-cover">
    <img v-if="url" :src="url" :alt="title" loading="lazy" />
    <span v-else role="status">{{ error ? '作品读取失败' : '读取真实作品…' }}</span>
  </div>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue';
import { fetchImageAssetThumbnailBlobUrl } from '@/api/image';
const props = defineProps<{ assetId: string; title: string }>();
const url = ref(''), error = ref(false);
let version = 0;
async function load() {
  const current = ++version; error.value = false;
  if (url.value) URL.revokeObjectURL(url.value); url.value = '';
  try {
    const next = await fetchImageAssetThumbnailBlobUrl(props.assetId);
    if (current !== version) URL.revokeObjectURL(next); else url.value = next;
  } catch { if (current === version) error.value = true; }
}
watch(() => props.assetId, load, { immediate: true });
onBeforeUnmount(() => { version++; if (url.value) URL.revokeObjectURL(url.value); });
</script>
<style scoped>
.generated-cover { position: relative; min-height: 120px; background: var(--el-fill-color-light); border-radius: 10px; overflow: hidden; }
img { display: block; width: 100%; height: auto; }
span { display: block; padding: 48px 12px; text-align: center; font-size: 12px; color: var(--el-text-color-secondary); }
button { padding: 8px; border: 0; background: transparent; color: #7961ca; cursor: pointer; }
</style>
