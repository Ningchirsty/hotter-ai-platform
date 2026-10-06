<template>
  <div class="generation-source">
    <span class="source-label">生成方式</span>
    <div class="source-options" aria-label="生成方式">
      <button
        type="button"
        :class="{ active: modelValue === 'local' }"
        :aria-pressed="modelValue === 'local'"
        :disabled="busy"
        @click="$emit('update:modelValue', 'local')"
      >
        <el-icon><Cpu /></el-icon>
        <span>
          <b>本地生成</b>
          <small>ComfyUI 工作流</small>
        </span>
      </button>
      <button
        type="button"
        :class="{ active: modelValue === 'cloud' }"
        :aria-pressed="modelValue === 'cloud'"
        :disabled="busy"
        @click="$emit('update:modelValue', 'cloud')"
      >
        <el-icon><Cloudy /></el-icon>
        <span>
          <b>
            云端生成
            <em>待接入</em>
          </b>
          <small>外部 API 服务</small>
        </span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Cloudy, Cpu } from '@element-plus/icons-vue';
import type { GenerationSource } from './types';
defineProps<{ modelValue: GenerationSource; busy?: boolean }>();
defineEmits<{ 'update:modelValue': [value: GenerationSource] }>();
</script>

<style scoped>
.generation-source {
  margin-bottom: 22px;
}
.source-label {
  display: block;
  margin-bottom: 10px;
  color: var(--t1);
  font-size: 13px;
  font-weight: 600;
}
.source-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.source-options button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 12px;
  text-align: left;
  color: var(--t2);
  background: var(--surface);
  border: 1px solid var(--line2);
  border-radius: 10px;
  cursor: pointer;
}
.source-options button.active {
  color: var(--p);
  background: var(--tint);
  border-color: var(--p);
}
.source-options button:disabled {
  cursor: wait;
  opacity: 0.6;
}
.source-options .el-icon {
  font-size: 20px;
  flex-shrink: 0;
}
.source-options b {
  display: block;
  font-size: 13px;
}
.source-options small {
  display: block;
  margin-top: 5px;
  font-size: 11px;
  color: var(--t2);
}
.source-options em {
  font-size: 10px;
  font-weight: 400;
  font-style: normal;
}
button:focus-visible {
  outline: 2px solid var(--p);
  outline-offset: 3px;
}
</style>
