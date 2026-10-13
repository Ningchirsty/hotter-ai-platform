<template>
  <div ref="element" class="template-video-preview" :class="{ 'is-detail': detail }">
    <TemplateCover v-if="!url || failed" :id="id" :revision="revision" :title="title" media="video" />
    <video v-if="url && !failed" ref="player" :src="url" :aria-label="title + '示例视频'" muted playsinline :loop="!detail" :controls="detail" preload="metadata" @loadeddata="syncPlayback" @error="failed = true" />
    <span v-if="!detail || !url || failed" class="preview-label">{{ failed ? '视频暂不可用' : !available ? '暂无示例视频' : !url ? '视频加载中…' : '动态预览' }}</span>
    <button v-if="detail && failed" class="retry" type="button" @click="retry">重新加载视频</button>
  </div>
</template>
<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import TemplateCover from './TemplateCover.vue';
import { videoPreviewPool, type PreviewLease } from './video-preview-pool';
const props = withDefaults(defineProps<{ id: string; revision: number; title: string; available?: boolean; detail?: boolean }>(), { available: true, detail: false });
const element = ref<HTMLElement>(), player = ref<HTMLVideoElement>();
const visible = ref(props.detail), url = ref(''), failed = ref(false);
let observer: IntersectionObserver | undefined, lease: PreviewLease | undefined, sequence = 0, loading = false;
async function syncPlayback() {
  await nextTick();
  if (!player.value) return;
  if (!visible.value || document.hidden) { player.value.pause(); return; }
  try { await player.value.play(); } catch { /* Detail controls remain available if autoplay is unavailable. */ }
}
async function load() {
  if (!props.available || loading || url.value || failed.value) return;
  loading = true; const current = sequence;
  try {
    const next = await videoPreviewPool.acquire(props.id, props.revision);
    if (current !== sequence) { next.release(); return; }
    lease = next; url.value = next.url; await syncPlayback();
  } catch { if (current === sequence) failed.value = true; }
  finally { if (current === sequence) loading = false; }
}
function reset() { sequence++; lease?.release(); lease = undefined; url.value = ''; failed.value = false; loading = false; if (visible.value) void load(); }
function retry() { failed.value = false; void load(); }
watch(() => [props.id, props.revision, props.available], reset);
watch(visible, value => { if (value) void load(); void syncPlayback(); });
onMounted(() => {
  document.addEventListener('visibilitychange', syncPlayback);
  if (props.detail || typeof IntersectionObserver === 'undefined') { visible.value = true; void load(); }
  else {
    observer = new IntersectionObserver(entries => { visible.value = entries.some(entry => entry.isIntersecting); }, { rootMargin: '40px', threshold: 0.05 });
    if (element.value) observer.observe(element.value);
  }
});
onBeforeUnmount(() => { sequence++; observer?.disconnect(); document.removeEventListener('visibilitychange', syncPlayback); player.value?.pause(); lease?.release(); });
</script>
<style scoped>
.template-video-preview { position: relative; min-height: 120px; overflow: hidden; border-radius: 10px; background: #edf0f7; }
video { display: block; width: 100%; max-height: 300px; object-fit: contain; background: #172035; }
.is-detail video { max-height: 520px; }
.preview-label { position: absolute; bottom: 9px; left: 9px; padding: 5px 8px; border-radius: 6px; background: rgba(25, 34, 51, .76); color: #fff; font-size: 11px; }
.retry { position: absolute; right: 9px; bottom: 9px; padding: 5px 8px; border: 0; border-radius: 6px; background: #fff; color: #6846bc; cursor: pointer; }
</style>
