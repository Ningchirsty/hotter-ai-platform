<template>
  <TemplateDiscovery :media="media" :busy="busy" :cloud-status="cloudStatus" @apply="(route, title) => emit('apply', route, title)" @create="(route, title) => emit('create', route, title)" @task-created="emit('task-created')" />
</template>
<script setup lang="ts">
import TemplateDiscovery from './TemplateDiscovery.vue';
import type { CloudImageModelsVO } from '@/api/image/types';
import type { CreativeMedia, InspirationRoute, InspirationWorkflow } from './types';
defineProps<{ media: CreativeMedia; workflows: InspirationWorkflow[]; cloudStatus?: CloudImageModelsVO; busy?: boolean; appliedTitle?: string; revision?: number }>();
const emit = defineEmits<{ apply: [route: InspirationRoute, title: string]; create: [route: InspirationRoute, title: string]; 'task-created': [] }>();
</script>
