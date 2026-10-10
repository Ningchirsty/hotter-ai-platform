import ElementPlus from 'element-plus';
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { createRouter, createWebHashHistory } from 'vue-router';
import 'element-plus/dist/index.css';
import ImageCreation from '@/views/image/index.vue';
import VideoCreation from '@/views/video/index.vue';
import App from './App.vue';
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/ai-tools/video-creation' },
    { path: '/ai-tools/video-creation', component: VideoCreation },
    { path: '/ai-tools/image-creation', component: ImageCreation }
  ]
});
// Preview only: show permission-controlled controls, while all writes reject in fixture-api.
createApp(App).use(createPinia()).use(router).use(ElementPlus).directive('hasPermi', {}).mount('#app');
