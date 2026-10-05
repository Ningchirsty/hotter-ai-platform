<template>
  <div class="prompt-box">
    <div class="prompt-label">
      <label>{{ label }}</label>
      <el-button size="small" text type="primary" @click="showRaw = !showRaw">
        {{ showRaw ? '看色块版' : '查看原文' }}
      </el-button>
    </div>
    <!-- 原文：与下发时逐字相同，可选中复制 -->
    <el-input v-if="showRaw" :model-value="text" type="textarea" :rows="rows" readonly />
    <!-- 默认视图：色号渲染成色块，正文不再印 #RRGGBB（色值在 title 里，悬停可见） -->
    <div v-else class="prompt-render">
      <template v-for="(seg, index) in segments" :key="index">
        <span v-if="seg.kind === 'text'">{{ seg.text }}</span>
        <span v-else class="swatch" :style="{ background: seg.text }" :title="seg.text" />
      </template>
    </div>
    <p v-if="!showRaw && tokens.length" class="swatch-note">
      色块＝提示词里的色号（悬停看色值）。这段文本本身一个字没改，点「查看原文」能看到并复制原样。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import { promptColorTokens, splitPromptColors } from '../../composables/promptSwatch';

/**
 * 提示词展示框（V0.2；v1 人工测试反馈 视觉基因 1.2「具体的编号对应的是相应的颜色。不要展示编号最好。」）。
 *
 * <p><b>为什么不是直接放一个只读 textarea</b>：色号对人没有意义（`#C8443C` 是什么颜色看不出来），
 * 但这段文本又是出图真正下发的内容、也是"这版基因派生出什么"的证据，**不能改**。
 * 所以这里只换显示方式：默认把色号画成色块，另给一个「查看原文」把逐字原文放出来（可复制）。</p>
 *
 * <p><b>为什么不做成可编辑</b>：提示词能不能在本页改、改完算不算新一版基因，是产品口径问题
 * （见对照表 §4-2），没有定之前这里保持只读——不假装配了编辑。</p>
 *
 * @author creative
 */
const props = withDefaults(
  defineProps<{
    /** 框上方的标题（正向/负向） */
    label: string;
    /** 提示词原文 */
    text: string;
    /** 「查看原文」时文本框的行数 */
    rows?: number;
  }>(),
  { rows: 4 }
);

/** 当前是否显示逐字原文 */
const showRaw = ref(false);

const segments = computed(() => splitPromptColors(props.text));
const tokens = computed(() => promptColorTokens(props.text));
</script>

<style scoped lang="scss">
.prompt-box {
  margin-top: 10px;
}

.prompt-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;

  label {
    font-size: 12px;
    color: var(--t2);
  }
}

.prompt-render {
  min-height: 60px;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--t1);
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 4px;
  user-select: text;
}

/* 色块本身就代表那个色号：稍大一点，保证深色/浅色都看得见 */
.swatch {
  display: inline-block;
  width: 14px;
  height: 14px;
  margin: 0 2px;
  vertical-align: -2px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 3px;
  cursor: help;
}

.swatch-note {
  margin: 6px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--t3);
}
</style>
