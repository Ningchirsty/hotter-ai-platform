<template>
  <StagePlaceholder
    title="视觉基因 DNA"
    summary="把「这条详情页长什么样」写成结构化的视觉规范：配色、光线、留白、产品占比、场景与禁忌。"
    round="R1"
    :items="items"
  />
</template>

<script setup lang="ts">
import StagePlaceholder from '../components/StagePlaceholder.vue';

const items = [
  '参考图分析：从上传的产品图与竞品参考里抽取可复用的视觉特征（不是让 AI 自由发挥）',
  '结构化视觉基因：主色/辅色/点缀色/背景色、饱和度、对比度、光线类型与光位、留白档、产品占比区间',
  '人工可改：每一项都能改，改完版本递增（视觉基因是权威内容，其余列只是索引）',
  '锁定机制：只有「已锁定」的基因才能进入分镜与出图，锁定动作留痕（谁在什么时候锁的）',
  '与出图的衔接：锁定的基因会翻译成提示词片段与负向词，避免每屏各写一套'
];
</script>
