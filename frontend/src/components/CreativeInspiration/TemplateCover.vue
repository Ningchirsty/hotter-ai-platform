<template>
  <div class="template-cover"><img v-if="url" :src="url" :alt="title" loading="lazy" /><span v-else>{{ failed ? '封面暂不可用' : '读取模板…' }}</span></div>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { templateCover } from '@/api/image/templates';
const props = defineProps<{ id: string; revision: number; title: string }>();
const url = ref(''), failed = ref(false);
let sequence = 0;
watch(() => [props.id, props.revision], async () => {
  const current = ++sequence;
  if (url.value) URL.revokeObjectURL(url.value);
  url.value = ''; failed.value = false;
  try { const next = await templateCover(props.id, props.revision); if (sequence !== current) URL.revokeObjectURL(next); else url.value = next; }
  catch { if (sequence === current) failed.value = true; }
}, { immediate: true });
onBeforeUnmount(() => { sequence++; if (url.value) URL.revokeObjectURL(url.value); });
</script>
<style scoped>
.template-cover { min-height: 120px; background: #f3f1f8; overflow: hidden; border-radius: 10px; }
img { display: block; width: 100%; height: auto; }
span { display: block; padding: 50px 12px; text-align: center; color: #8c94a8; font-size: 12px; }
</style>
