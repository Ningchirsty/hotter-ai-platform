<template>
  <div class="studio">
    <header class="stage-head">
      <div>
        <h2>{{ title }}</h2>
        <p class="muted">{{ summary }}</p>
      </div>
      <span class="round-tag">{{ round }} 交付</span>
    </header>

    <section class="stage-card">
      <h3>这一版为什么先不做它</h3>
      <p class="muted">
        R0 的目标是把「项目 → 上传参考图 → 出图 → 预览 → 可追溯」这条闭环在真实生产环境跑通，
        并且把复用的边界钉死。视觉基因、方向、分镜、视觉门、排版模板属于<strong>新建的视觉领域</strong>，
        在闭环稳定、口径确认之后再逐个交付，避免一次做十件事、每件都半成品。
      </p>
    </section>

    <section class="stage-card">
      <h3>计划做什么（{{ round }}）</h3>
      <ul class="plan-list">
        <li v-for="(item, index) in items" :key="index">{{ item }}</li>
      </ul>
    </section>

    <section class="stage-card">
      <h3>现在可以先用的能力</h3>
      <ul class="plan-list">
        <li>
          出图与候选管理已可用：去
          <router-link class="link" to="/creative/project">视觉项目</router-link>
          上传产品图并生成 HERO 主图候选。
        </li>
        <li>
          生产总览见
          <router-link class="link" to="/creative/production">AI 生产中心</router-link>。
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  summary: string;
  round: string;
  items: string[];
}>();
</script>

<style scoped lang="scss">
@use '@/assets/styles/tokens-studio.scss';

.studio {
  min-height: calc(100vh - 135px);
  padding: 24px;
  color: var(--t1);
  background: var(--bg);
  background-image: radial-gradient(900px 460px at 84% -10%, rgba(148, 163, 184, 0.16), transparent 68%);
  border: 1px solid var(--line);
  border-radius: 8px;
}

.stage-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--line);
}
.stage-head h2 {
  margin: 0 0 6px;
  font-size: 18px;
}

.round-tag {
  padding: 3px 10px;
  font-size: 12px;
  color: #c7d2fe;
  background: rgba(99, 102, 241, 0.16);
  border: 1px solid rgba(99, 102, 241, 0.4);
  border-radius: 10px;
}

.stage-card {
  padding: 16px;
  margin-bottom: 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.stage-card h3 {
  margin: 0 0 10px;
  font-size: 14px;
}

.plan-list {
  padding-left: 20px;
  margin: 0;
  font-size: 13px;
  line-height: 2;
  color: var(--t1);
}

.muted {
  margin: 0;
  font-size: 13px;
  line-height: 1.9;
  color: var(--t2);
}

.link {
  color: #a5b4fc;
  text-decoration: none;
}
.link:hover {
  text-decoration: underline;
}
</style>
