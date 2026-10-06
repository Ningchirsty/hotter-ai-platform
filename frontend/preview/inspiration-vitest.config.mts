import { defineConfig } from 'vitest/config';
export default defineConfig({
  test: { include: ['src/components/CreativeInspiration/{catalog,cloud-models}.spec.ts'], environment: 'node' }
});
