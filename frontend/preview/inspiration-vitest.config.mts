import { defineConfig } from 'vitest/config';
export default defineConfig({
  test: {
    include: [
      'src/components/CreativeAbility/abilities.spec.ts',
      'src/components/LocalWorkflowPicker/catalog.spec.ts',
      'src/components/CreativeInspiration/{catalog,cloud-models,cloud-image-capabilities,cloud-image-output}.spec.ts',
      'src/utils/task-polling.spec.ts',
      'src/api/video/cloud*.spec.ts'
    ],
    environment: 'node'
  }
});
