<template>
  <StagePlaceholder
    title="视觉模板库"
    summary="把详情页版式沉淀成可复用模板：模板编码、可填字段约束、预览图与发布状态。"
    round="R3"
    :items="items"
  />
</template>

<script setup lang="ts">
import StagePlaceholder from '../components/StagePlaceholder.vue';

const items = [
  '模板登记：编码（如 HERO-H02）、类型、版本、可填字段约束、HTML/CSS 与预览图',
  '发布门：模板走 DRAFT → PUBLISHED，只有已发布模板能被排版选用（与图像工作流同一套纪律）',
  '字体前提：渲染服务需要中文字体的商用授权，这是业务侧的前置输入，不是技术能替代的',
  '首批先做一个模板（HERO-H02 单屏）跑通渲染与像素一致性验收，再按 20~30 个规模铺开',
  '像素级一致性：同一输入两次渲染结果必须逐像素一致，否则长图无法用于审核比对'
];
</script>
