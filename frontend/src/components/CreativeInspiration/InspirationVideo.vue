<template>
  <div class="video-reference">
    <template v-if="playable">
      <video
        v-if="!failed"
        :src="reference.src"
        :poster="reference.poster"
        controls
        playsinline
        preload="metadata"
        :aria-label="title + '参考视频'"
        @error="failed = true"
      />
      <div v-else class="video-failure" role="status">参考视频暂时无法加载，请通过下方来源链接查看。</div>
    </template>
    <template v-else>
      <img v-if="!failed" :src="reference.poster" :alt="title + ' · 视频封面'" loading="lazy" @error="failed = true" />
      <div v-else class="poster-failure">参考视频</div>
      <span class="play-icon">
        <el-icon><VideoPlay /></el-icon>
      </span>
      <span class="video-label">参考视频</span>
    </template>
  </div>
</template>
<script setup lang="ts">
import { VideoPlay } from '@element-plus/icons-vue';
import { ref, watch } from 'vue';
import type { VideoReference } from './types';
const props = defineProps<{ reference: VideoReference; title: string; playable?: boolean }>();
const failed = ref(false);
watch(
  () => props.reference.src,
  () => {
    failed.value = false;
  }
);
</script>
<style scoped>
.video-reference {
  position: relative;
  width: 100%;
  aspect-ratio: 16/9;
  overflow: hidden;
  border-radius: 10px;
  background: #eef0f7;
}
video,
img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
img {
  object-fit: cover;
}
.play-icon {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: white;
  font-size: 40px;
  background: linear-gradient(transparent, rgba(25, 34, 51, 0.25));
}
.play-icon .el-icon {
  filter: drop-shadow(0 2px 5px rgba(0, 0, 0, 0.25));
}
.video-label {
  position: absolute;
  left: 9px;
  bottom: 9px;
  padding: 5px 7px;
  color: #29364f;
  background: rgba(255, 255, 255, 0.94);
  border-radius: 5px;
  font-size: 10px;
}
.video-failure,
.poster-failure {
  display: grid;
  place-items: center;
  height: 100%;
  padding: 24px;
  color: #728097;
  font-size: 12px;
  text-align: center;
}
</style>
