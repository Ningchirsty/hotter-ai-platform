<template>
  <section v-if="visible" class="raw-console">
    <header class="raw-console__head">
      <span class="raw-console__title">&lt;/&gt; {{ title || '原始与日志' }}</span>
      <span class="raw-console__spacer" />
      <el-button size="small" @click="copy">复制全部</el-button>
      <el-button size="small" @click="emit('close')">关闭</el-button>
    </header>
    <pre class="raw-console__body">{{ content || '（没有可展示的原始返回）' }}</pre>
    <footer class="raw-console__foot">
      原始字段与决策链路只在这里展示：列表里只给人读的值。
    </footer>
  </section>
</template>

<script setup lang="ts">
/**
 * 深色「原始与日志」控制台（P0 组件）。
 *
 * <p><b>为什么单独做成深色区</b>：治理台的其它区域都是浅色业务页面，而原始 JSON / traceId /
 * 决策链路是排障时才看的东西。把它放进唯一一块深色区域，既让排障的人一眼找到，
 * 也避免"看日志"和"配能力"两种注意力互相干扰。</p>
 *
 * <p>不做成弹窗（`el-dialog`）是刻意的：弹窗会盖住它上面那个正在被排障的页面，
 * 而排障最常见的动作是"对照着看"。</p>
 */
const props = defineProps<{
  /** 是否可见（由父组件用 v-if 之外的开关控制，便于保留滚动位置） */
  visible: boolean;
  /** 标题（默认"原始与日志"） */
  title?: string;
  /** 原始内容 */
  content?: string;
}>();

const emit = defineEmits<{
  (event: 'close'): void;
}>();

const copy = async () => {
  try {
    await navigator.clipboard?.writeText(props.content || '');
  } catch {
    // 复制失败不阻断：用户可以手动选中（无 DOM 测试环境时 clipboard 也可能不可用）
  }
};
</script>

<style scoped>
.raw-console {
  margin-top: 12px;
  background: #232323;
  border-radius: 12px;
  overflow: hidden;
}

.raw-console__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #2b2b2b;
  border-bottom: 1px solid #3a3a3a;
}

.raw-console__title {
  color: #e5e7eb;
  font-size: 13px;
}

.raw-console__spacer {
  flex: 1;
}

.raw-console__body {
  margin: 0;
  padding: 12px 14px;
  color: #c9d6a0;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  line-height: 1.65;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
}

.raw-console__foot {
  padding: 8px 14px;
  background: #2b2b2b;
  border-top: 1px solid #3a3a3a;
  color: #9ca3af;
  font-size: 12px;
}
</style>
