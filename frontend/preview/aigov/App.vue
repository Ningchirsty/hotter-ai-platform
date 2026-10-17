<template>
  <div class="preview-shell">
    <div class="preview-banner" role="status">
      <b>治理台界面预览</b>
      <span>假数据 · 不连后端 · 不需要登录 · 所有写操作已被拦截</span>
      <span v-if="errorText" class="preview-error">页面渲染出错：{{ errorText }}</span>
      <span v-else-if="unmocked.length" class="preview-warn">
        有 {{ unmocked.length }} 个接口没被 mock（已按空数据渲染）：{{ unmocked.slice(0, 3).map(item => item.method + ' ' + item.url).join('；') }}
      </span>
    </div>
    <div class="preview-body">
      <aside class="preview-nav">
        <button
          v-for="item in pages"
          :key="item.key"
          type="button"
          :class="['nav-item', { 'nav-item--active': current === item.key }]"
          @click="current = item.key"
        >
          <span>{{ item.title }}</span>
          <small>{{ item.hint }}</small>
        </button>
      </aside>
      <main class="preview-main">
        <component :is="activePage" :key="current" />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onErrorCaptured } from 'vue';
import { unmockedRequests } from './mock-request';
import CapabilityPage from '@/views/aigov/capability/index.vue';
import RoutePage from '@/views/aigov/route/index.vue';
import AuditPage from '@/views/aigov/audit/index.vue';
import ModelPage from '@/views/aigov/model/index.vue';
import ToolPage from '@/views/aigov/tool/index.vue';
import RolePackagePage from '@/views/aigov/rolePackage/index.vue';
import EvaluationPage from '@/views/aigov/evaluation/index.vue';
import PortalPage from '@/views/aigov/portal/index.vue';
import KnowledgePage from '@/views/aigov/knowledge/index.vue';
import FeedbackPage from '@/views/aigov/feedback/index.vue';

const pages = [
  { key: 'capability', title: '能力清单', hint: '链路通不通', page: CapabilityPage },
  { key: 'route', title: '路由策略', hint: '哪些在放行外发', page: RoutePage },
  { key: 'audit', title: '调用审计', hint: '这一页有没有出事', page: AuditPage },
  { key: 'model', title: '模型注册', hint: '能不能用', page: ModelPage },
  { key: 'tool', title: '工具注册', hint: '能不能真执行', page: ToolPage },
  { key: 'rolePackage', title: '岗位包', hint: '员工能看到什么', page: RolePackagePage },
  { key: 'evaluation', title: '评测', hint: '跑得过吗', page: EvaluationPage },
  { key: 'portal', title: '员工工作台', hint: '我的任务到哪了', page: PortalPage },
  { key: 'knowledge', title: '知识中心', hint: '资料能不能被检索', page: KnowledgePage },
  { key: 'feedback', title: '反馈与运营', hint: '问题队列与指标', page: FeedbackPage }
];

const current = ref(new URLSearchParams(window.location.search).get('page') || 'capability');
const activePage = computed(() => pages.find(item => item.key === current.value)?.page ?? CapabilityPage);
const unmocked = computed(() => unmockedRequests.slice(0, 6));
const errorText = ref('');
const seen = new Set<string>();

// 渲染错误必须在屏幕上可见：否则"某页白屏"会被误当成"这页没数据"
onErrorCaptured((error: any) => {
  const text = String(error?.message || error || '未知错误');
  if (!seen.has(text)) {
    seen.add(text);
    errorText.value = text;
    // eslint-disable-next-line no-console
    console.error('[界面预览] 渲染错误', error);
  }
  return false;
});
</script>

<style scoped>
.preview-shell {
  min-height: 100vh;
  background: #f2f3f5;
}

.preview-banner {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 8px 16px;
  background: #eef4ff;
  border-bottom: 1px solid #d6e4ff;
  color: #35507a;
  font-size: 12px;
}

.preview-banner b {
  font-size: 13px;
}

.preview-warn {
  color: #a86400;
}

.preview-error {
  color: #c45656;
  font-weight: 600;
}

.preview-body {
  display: flex;
  align-items: stretch;
  min-height: calc(100vh - 37px);
}

.preview-nav {
  width: 168px;
  flex: 0 0 168px;
  padding: 8px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
}

.nav-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 8px 10px;
  margin-bottom: 4px;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  color: #303133;
  font-size: 13px;
}

.nav-item small {
  color: #909399;
  font-size: 11px;
}

.nav-item:hover {
  background: #f5f7fa;
}

.nav-item--active {
  background: #ecf5ff;
  color: #409eff;
}

.nav-item--active small {
  color: #79bbff;
}

.preview-main {
  flex: 1;
  min-width: 0;
  padding: 4px;
}
</style>
