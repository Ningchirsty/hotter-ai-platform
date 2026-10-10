<template>
  <TemplateDiscovery v-if="media === 'image'" :busy="busy" @apply="(route, title) => emit('apply', route, title)" @task-created="emit('task-created')" />
  <VideoDiscovery v-else v-bind="$props" @apply="(route, title) => emit('apply', route, title)" />
</template>
<script setup lang="ts">
import TemplateDiscovery from './TemplateDiscovery.vue';
import VideoDiscovery from './VideoDiscovery.vue';
import type { CloudImageModelsVO } from '@/api/image/types';
import type { CreativeMedia, InspirationRoute, InspirationWorkflow } from './types';
defineProps<{ media: CreativeMedia; workflows: InspirationWorkflow[]; cloudStatus?: CloudImageModelsVO; busy?: boolean; appliedTitle?: string; revision?: number }>();
const emit = defineEmits<{ apply: [route: InspirationRoute, title: string]; 'task-created': [] }>();
</script>
