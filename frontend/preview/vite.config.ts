import vue from '@vitejs/plugin-vue';
import { readFileSync } from 'node:fs';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
const here = (relative: string) => fileURLToPath(new URL(relative, import.meta.url));
const live = process.env.HOTTER_LOCAL_CLOUD_LIVE === 'true';
const localSession = live ? readFileSync(process.env.HOTTER_LOCAL_CLOUD_SESSION_FILE!, 'utf8').trim() : '';
export default defineConfig({
  define: { 'import.meta.env.VITE_CLOUD_LOCAL_LIVE': JSON.stringify(live ? 'true' : 'false') },
  root: here('./'),
  plugins: [vue()],
  resolve: {
    alias: [
      { find: /^@\/api\/video$/, replacement: here('./fixture-api.ts') },
      { find: /^@\/api\/image$/, replacement: here(live ? './live-api.ts' : './fixture-api.ts') },
      { find: /^@\/utils\/request$/, replacement: here('./fixture-api.ts') },
      { find: '@', replacement: here('../src') }
    ]
  },
  server: { host: '127.0.0.1', port: live ? 5179 : 5178, strictPort: true, fs: { allow: [here('../')] },
    proxy: live ? { '/local-cloud': { target: 'http://127.0.0.1:5180', rewrite: path => path.replace(/^\/local-cloud/, ''),
      headers: { 'X-Hotter-Local-Session': localSession } } } : undefined },
  build: { outDir: here('../.preview-dist'), emptyOutDir: true }
});
