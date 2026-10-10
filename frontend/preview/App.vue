<template>
  <div class="preview-notice" role="status">
    <b>{{ live ? "本机云端真实联调" : "源码界面预览" }}</b>
    <span v-if="live">云端图像提交会调用蓝章鱼 API 并可能计费 · 任务与素材保存在本机 · 本地 ComfyUI 与视频仍为样例展示</span>
    <span v-else>31 个新增工作流 · 图像 11 / 视频 20 · 只读预览，任务／素材／GPU 为界面样例</span>
  </div>
  <header class="platform-header">
    <a class="brand" href="#/ai-tools/video-creation">
      <i>H</i>
      <span>
        纵享 AI 创作平台
        <small>CREATIVE STUDIO</small>
      </span>
    </a>
    <nav>
      <router-link to="/ai-tools/video-creation">视频创作</router-link>
      <router-link to="/ai-tools/image-creation">图像创作</router-link>
    </nav>
    <span class="baseline">本地分支 · 待确认</span>
  </header>
  <main><router-view /></main>
</template>
<script setup lang="ts">
const live = import.meta.env.VITE_CLOUD_LOCAL_LIVE === "true";
</script>
<style>
* {
  box-sizing: border-box;
}
body {
  margin: 0;
  font-family: 'Microsoft YaHei', Inter, system-ui, sans-serif;
  background: #f5f6fb;
  color: #29364f;
}
button,
input,
textarea {
  font: inherit;
}
.preview-notice {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding: 9px 20px;
  background: #f0eafd;
  color: #6e54bc;
  font-size: 12px;
  line-height: 1.6;
  flex-wrap: wrap;
}
.platform-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 36px;
  background: white;
  border-bottom: 1px solid #e3e7f0;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
  color: #29364f;
  font-size: 16px;
}
.brand i {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  background: #29364f;
  border-radius: 11px;
  color: white;
  font-size: 25px;
  font-style: normal;
}
.brand small {
  display: block;
  margin-top: 5px;
  letter-spacing: 2px;
  font-size: 10px;
  color: #8b98ae;
}
.platform-header nav {
  display: flex;
  gap: 18px;
}
.platform-header nav a {
  text-decoration: none;
  color: #8390a5;
  font-size: 13px;
}
.platform-header nav a.router-link-active {
  color: #7961ca;
}
.baseline {
  font-size: 11px;
  color: #8b98ae;
}
main {
  max-width: 1660px;
  margin: auto;
}
@media (max-width: 700px) {
  .platform-header {
    padding: 14px;
    gap: 10px;
  }
  .baseline {
    display: none;
  }
  .brand {
    font-size: 13px;
  }
}
</style>
