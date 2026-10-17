import vue from '@vitejs/plugin-vue';
import UnoCSS from 'unocss/vite';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';

const here = (relative: string) => fileURLToPath(new URL(relative, import.meta.url));

/**
 * 治理台界面预览入口（`pnpm preview:aigov`）。
 *
 * <p>为什么单独一个入口：本仓前端没有 DOM 测试环境，"观感"只能靠人眼；
 * 而跑真实环境需要后端 + 库 + 登录。这里把**真实的页面组件**挂起来，
 * 只把三处依赖换成替身（API → 假数据、字典 → 静态表、权限指令 → 放行），
 * 于是打开浏览器就能看界面，且**生产入口与真实 API 一点都不受影响**。</p>
 */
export default defineConfig({
  root: here('./preview/aigov/'),
  plugins: [
    vue(),
    UnoCSS({ hmrTopLevelAwait: false }),
    AutoImport({
      imports: ['vue', 'vue-router', '@vueuse/core', 'pinia'],
      resolvers: [ElementPlusResolver({ importStyle: false })],
      vueTemplate: true,
      dts: false
    }),
    Components({
      // 生产入口默认扫 src/components（`<pagination>` / `<dict-tag>` 就是这么来的）
      dirs: [here('./src/components')],
      resolvers: [ElementPlusResolver({ importStyle: false })],
      dts: false
    })
  ],
  resolve: {
    alias: [
      { find: /^@\/utils\/request$/, replacement: here('./preview/aigov/mock-request.ts') },
      { find: /^@\/utils\/dict$/, replacement: here('./preview/aigov/stubs.ts') },
      { find: /^@\/utils\/permission$/, replacement: here('./preview/aigov/stubs.ts') },
      { find: '@', replacement: here('./src') }
    ]
  },
  server: {
    host: '127.0.0.1',
    port: 5190,
    strictPort: true,
    fs: { allow: [here('./')] }
  },
  build: {
    outDir: here('./.aigov-preview-dist'),
    emptyOutDir: true
  }
});
