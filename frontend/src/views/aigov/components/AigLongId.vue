<template>
  <span class="aig-id" :title="full" @click.stop="copy">
    <!--
      长 ID：默认折叠成"头 6 位…尾 6 位"，悬停看全量，点一下复制完整值。
      为什么不用 el-text/直接显示：19 位雪花 ID 直出会把列表挤成一堆数字，且复制容易少一位。
      （注释写在根元素内部——根级注释会被计成第二个根节点。）
    -->
    <slot>{{ folded }}</slot>
    <el-icon v-if="fold" class="aig-id__icon"><DocumentCopy /></el-icon>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { ElMessage } from 'element-plus';
import { foldId, needsFold } from './id';

/**
 * 长 ID 展示（折叠 + 悬停全量 + 点击复制）。
 *
 * <p>用 {@code @click.stop}：这些 ID 常出现在可点行（表格行点击进详情）里，
 * 复制不该顺带把页面跳走。</p>
 */
const props = withDefaults(
  defineProps<{
    /** 原始 ID。⚠️ 19 位雪花 ID 请传**字符串**（数字字面量会丢精度） */
    id?: string | number | null;
    /** 头尾各保留几位 */
    keep?: number;
  }>(),
  { keep: 6 }
);

const full = computed(() => (props.id === null || props.id === undefined ? '' : String(props.id)));
const folded = computed(() => foldId(full.value, props.keep));
const fold = computed(() => needsFold(full.value, props.keep));

/**
 * 复制完整 ID。剪贴板不可用时（http 环境 / 无权限）如实提示，
 * 而不是静默失败——那会让人以为"复制了"。
 */
const copy = async () => {
  if (!full.value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(full.value);
    ElMessage.success('完整 ID 已复制');
  } catch {
    ElMessage.warning('浏览器不允许自动复制，请手动选择文本复制');
  }
};
</script>

<style scoped>
.aig-id {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  cursor: pointer;
}

.aig-id__icon {
  margin-left: 4px;
  color: var(--el-text-color-placeholder);
  vertical-align: -2px;
}

.aig-id:hover .aig-id__icon {
  color: var(--el-color-primary);
}
</style>
