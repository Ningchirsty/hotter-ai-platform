<template>
  <div class="aig-raw">
    <div class="aig-raw__head">
      <el-button link type="primary" size="small" @click="open = !open">
        {{ open ? '收起原始字段' : '显示原始字段' }}
      </el-button>
      <span v-if="!open" class="aig-raw__preview">{{ preview }}</span>
      <el-button link size="small" @click="copy">复制</el-button>
    </div>
    <pre v-if="open" class="aig-raw__body">{{ expanded }}</pre>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { collapsedPreview, prettyOrRaw } from './raw';

/**
 * 原始字段（默认折叠，展开可复制）。
 *
 * <p><b>为什么默认收起</b>：它该在"需要查证"时才出现，而不是把每一行的注意力从结论拉走。
 * 收起时保留一行预览（限长），这样至少能看出"这里有没有东西"。</p>
 *
 * <p>非 JSON（含非法 JSON）也照原样显示——恰恰是这种值最需要能查到，
 * 不能因为"格式化失败"就把内容藏起来。</p>
 */
const props = withDefaults(
  defineProps<{
    /** 原始文本（JSON 或任意长文本） */
    text?: string;
    /** 收起时的预览长度 */
    previewLength?: number;
  }>(),
  { previewLength: 60 }
);

const open = ref(false);
const preview = computed(() => collapsedPreview(props.text, props.previewLength));
const expanded = computed(() => prettyOrRaw(props.text));

/**
 * 复制原始文本。剪贴板不可用时如实提示（http 环境 / 无权限）。
 */
const copy = async () => {
  if (!props.text) {
    ElMessage.warning('没有可复制的内容');
    return;
  }
  try {
    await navigator.clipboard.writeText(props.text);
    ElMessage.success('原始字段已复制');
  } catch {
    ElMessage.warning('浏览器不允许自动复制，请展开后手动选择文本');
  }
};
</script>

<style scoped>
.aig-raw__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.aig-raw__preview {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-family: var(--el-font-family-mono, monospace);
}

.aig-raw__body {
  margin: 6px 0 0;
  padding: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
