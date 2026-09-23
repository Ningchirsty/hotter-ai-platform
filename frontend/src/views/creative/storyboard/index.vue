<template>
  <StagePlaceholder
    title="视觉方向与分镜"
    summary="先给三个可选的视觉方向，选定之后拆成逐屏分镜：每屏讲什么、画面单独要说什么、用哪套出图能力。"
    round="R1"
    :items="items"
  />
</template>

<script setup lang="ts">
import StagePlaceholder from '../components/StagePlaceholder.vue';

const items = [
  'A/B/C 三套视觉方向：各自的配色、光线、场景与构图差异点，可各出一张预览图后再选',
  '分镜脚本：屏号、屏类型（HERO/卖点/场景/细节/尺寸/包装/品牌）、标题与正文文案',
  '画面独白：每屏必须写清「这张图不讲文案时自己要说清什么」，避免出一堆好看但没信息量的图',
  '逐屏视觉规格：镜头、构图、光线、背景、产品保真等级（严格一致 / 允许艺术化）',
  '单屏重生成：某一屏不满意只重跑那一屏，不重跑整个分镜'
];
</script>
