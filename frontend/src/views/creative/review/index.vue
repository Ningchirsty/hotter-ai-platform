<template>
  <StagePlaceholder
    title="详情页与审核"
    summary="机器排版出 V0.8 长图，人工精修后出最终版；视觉门与终审都在这里，AI 不自动放行。"
    round="R3"
    :items="items"
  />
</template>

<script setup lang="ts">
import StagePlaceholder from '../components/StagePlaceholder.vue';

const items = [
  '视觉门：分镜锁定后的人工审核关卡，确认项复用内容协同的互动确认卡（BLOCK/CONDITION 两级）',
  '排版引擎：编排 JSON + HTML/CSS 模板 → 独立渲染服务（Node + Playwright）→ 750×N 长图',
  '机排版 V0.8 与最终版 V1.0 双版本：版本可回溯，审核意见落在版本上',
  '整页质检：复用成品一致性检查做「原图 vs 成品图」比对，结论只用于筛除与提示',
  '终审：通过后写回内容协同任务状态并登记交付物'
];
</script>
