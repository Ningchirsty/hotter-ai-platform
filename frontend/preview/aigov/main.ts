import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { createRouter, createWebHashHistory } from 'vue-router';
import 'element-plus/dist/index.css';
import 'virtual:uno.css';
import App from './App.vue';

/**
 * 治理台界面预览入口（**只读 demo，不连后端**）。
 *
 * 与生产入口的差别仅三处：API 换成 `mock-request.ts`、字典换成静态表、
 * 权限指令一律放行（`v-hasPermi` 空实现）——这样不需要登录也能看见全部界面。
 * 生产构建不含本文件（`aigov-preview.vite.config.ts` 是独立入口）。
 */
const app = createApp(App);

// portal 页里有「返回 AI 工作台」用了 useRouter；给一个能用的实例（路由本身不参与预览渲染）
const router = createRouter({
  history: createWebHashHistory(),
  routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }]
});

app.use(createPinia());
app.use(router);
// 中文语言包：否则 Element Plus 的分页/占位符会是 "Total / Go to / Select"，看不出真实观感
app.use(ElementPlus, { locale: zhCn });

// 权限指令：预览里放行（不是放宽权限——预览没有登录态，也没有真实数据）
app.directive('hasPermi', {});
app.directive('hasRoles', {});
app.directive('copyText', {});

app.mount('#app');
