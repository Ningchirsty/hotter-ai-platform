import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
const here = (relative: string) => fileURLToPath(new URL(relative, import.meta.url));
export default defineConfig({
  root: here('./'),
  plugins: [vue()],
  resolve: {
    alias: [
      { find: /^@\/api\/video$/, replacement: here('./fixture-api.ts') },
      { find: /^@\/api\/image$/, replacement: here('./fixture-api.ts') },
      { find: /^@\/utils\/request$/, replacement: here('./fixture-api.ts') },
      { find: '@', replacement: here('../src') }
    ]
  },
  server: { host: '127.0.0.1', port: 5178, strictPort: true, fs: { allow: [here('../')] } },
  build: { outDir: here('../.preview-dist'), emptyOutDir: true }
});
