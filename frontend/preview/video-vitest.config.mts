import { defineConfig } from 'vitest/config';
export default defineConfig({
  test: {
    environment: 'node',
    include: [
      'src/api/video/cloud-models.spec.ts',
      'src/api/video/cloud-pricing.spec.ts',
      'src/api/video/cloud.spec.ts'
    ]
  }
});
