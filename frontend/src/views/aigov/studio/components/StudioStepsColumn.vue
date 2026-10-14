<template>
  <!-- 三栏之左：步骤导航（设计 §6.3、§5.4 图一） -->
  <el-card shadow="never" class="step-card">
    <template #header><b>步骤</b></template>
    <ul class="step-list">
      <li
        v-for="(step, index) in STUDIO_STEPS"
        :key="step.key"
        :class="['step-item', index === activeStep ? 'step-item-active' : '']"
        @click="activeStep = index"
      >
        <span class="step-no">{{ index + 1 }}</span>
        <span class="step-label">{{ step.label }}</span>
      </li>
    </ul>
    <p class="step-hint">顺序即编辑顺序；表单暂未按步骤拆分，这里用于定位当前在填哪一段。</p>
  </el-card>
</template>

<script setup lang="ts">
import { STUDIO_STEPS } from '../layout';

defineOptions({ name: 'StudioStepsColumn' });

/**
 * 左栏当前高亮的步骤。
 *
 * <p>状态刻意留在本组件内：父组件（编辑器）不需要知道"正高亮哪一步"——
 * 它只做定位与高亮，**不按步骤过滤中间的表单**：表单目前是一整块（八个 Prompt 分节共享一个
 * 内容对象），过滤会让人以为"别的步骤的内容丢了"。等表单真按步骤拆开后再让它联动。</p>
 */
const activeStep = ref(0);
</script>

<style scoped>
.step-card {
  height: 100%;
}

.step-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.step-item {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  line-height: 1.4;
  color: var(--app-text-color-regular, #606266);
}

.step-item:hover {
  background: var(--app-hover-bg, #f5f7fa);
}

.step-item-active {
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
  font-weight: 600;
}

.step-no {
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  margin-top: 1px;
  border-radius: 50%;
  background: var(--el-color-info-light-8, #ebeef5);
  color: #606266;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
}

.step-item-active .step-no {
  background: var(--el-color-primary, #409eff);
  color: #fff;
}

.step-label {
  word-break: break-all;
}

.step-hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-color-secondary, #909399);
}
</style>
