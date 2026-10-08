<template>
  <CloudImageGenerationForm
    v-if="media === 'image'"
    :busy="busy"
    :cloud-status="cloudStatus"
    :inspiration="inspiration"
    @change="emit('change', $event)"
  />
  <CloudVideoGenerationForm v-else media="video" :busy="busy" />
</template>

<script setup lang="ts">
import type { CreativeMedia } from './types';
import CloudImageGenerationForm from './CloudImageGenerationForm.vue';
import CloudVideoGenerationForm from './CloudVideoGenerationForm.vue';
defineProps<{ media: CreativeMedia; busy?: boolean; cloudStatus?: import('@/api/image/types').CloudImageModelsVO; inspiration?: { route: import('./types').InspirationRoute; stamp: number } }>();
const emit = defineEmits<{ change: [draft: import('@/api/image/types').CloudImageDraft] }>();
</script>
