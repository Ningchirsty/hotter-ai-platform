<template>
  <div class="local-workflows">
    <div class="picker-heading">
      <label>生成模型与工作流</label>
      <span>{{ total }} 个新增{{ media === 'image' ? '图像' : '视频' }}工作流</span>
    </div>
    <button type="button" class="browse-button" :disabled="busy" @click="browserOpen = true">
      查看全部{{ media === 'image' ? '图像' : '视频' }}工作流 →
    </button>
    <div class="family-grid" :aria-label="media === 'image' ? '图像模型' : '视频模型'">
      <button v-for="model in models" :key="model.code" type="button"
        :disabled="busy" :class="{ active: selected?.modelCode === model.code }"
        :aria-pressed="selected?.modelCode === model.code" @click="chooseModel(model.code)">
        <b>{{ model.name }}</b>
        <small>{{ model.count }} 个适用工作流</small>
      </button>
    </div>
    <label class="variant-label">工作流版本</label>
    <el-select :model-value="modelValue" :disabled="busy" filterable class="variant-select"
      aria-label="工作流版本" @update:model-value="emit('update:modelValue', $event)">
      <el-option v-for="item in variants" :key="item.workflowCode" :value="item.workflowCode"
        :label="item.name" />
    </el-select>
    <div v-if="selected" class="workflow-summary" role="status">
      <span :class="['state', { available: registered?.status === 'PUBLISHED' && registered?.submittable }]">
        {{ registered?.status === 'PUBLISHED' && registered?.submittable ? '平台可提交' : selected.verifiedAt ? 'ComfyUI 已验证 · 平台待接入' : '平台工作流' }}
      </span>
      <span v-if="selected.hasAudio">含音频</span>
      <span v-if="selected.fps">{{ selected.fps }} FPS</span>
      <small>{{ selected.version }}</small>
    </div>
    <el-drawer v-model="browserOpen" :title="media === 'image' ? '图像工作流 · 11 个新增' : '视频工作流 · 20 个新增'"
      size="min(620px, 100vw)" append-to-body>
      <p class="browse-intro">按创作方式选择已验证的模型版本，带入左侧创作表单。</p>
      <div v-for="item in catalogOptions" :key="item.workflowCode" class="browse-row">
        <div><b>{{ item.modelName }}</b><p>{{ item.name }}</p>
          <small>{{ capabilityNames[item.capabilityCode] }} · {{ item.sizes?.[0]?.label ?? item.supportedTiers?.[0] }}{{ item.hasAudio ? ' · 含音频' : '' }}</small>
        </div>
        <button :disabled="busy" type="button" @click="pickFromBrowser(item.workflowCode)">带入创作</button>
      </div>
    </el-drawer>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import type { LocalWorkflowOption, RegisteredWorkflow } from './types';
const props = defineProps<{
  media: 'image' | 'video';
  items: LocalWorkflowOption[];
  catalog: LocalWorkflowOption[];
  modelValue: string;
  total: number;
  busy?: boolean;
  registered?: RegisteredWorkflow | null;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: string]; choose: [value: string] }>();
const browserOpen = ref(false);
const catalogOptions = computed(() => props.catalog.filter(item => item.media === props.media));
const capabilityNames: Record<string, string> = { T2I: '文生图', EDIT: '指令改图', CONTROL: '结构控制', T2V: '文生视频', I2V: '图生视频', FL2V: '首尾帧生视频', R2V: '参考图生视频' };
function pickFromBrowser(code: string) {
  if (!props.busy && catalogOptions.value.some(item => item.workflowCode === code)) { emit('choose', code); browserOpen.value = false; }
}
// Defense in depth: even a mixed caller list cannot display the other media's catalog.
const options = computed(() => props.items.filter(item => item.media === props.media));
const selected = computed(() => options.value.find(item => item.workflowCode === props.modelValue));
const models = computed(() => Array.from(new Set(options.value.map(item => item.modelCode))).map(code => {
  const rows = options.value.filter(item => item.modelCode === code);
  return { code, name: rows[0]!.modelName, count: rows.length };
}));
const variants = computed(() => options.value.filter(item => item.modelCode === selected.value?.modelCode));
function chooseModel(code: string) {
  const first = options.value.find(item => item.modelCode === code);
  if (first && selected.value?.modelCode !== code) emit('update:modelValue', first.workflowCode);
}
</script>
<style scoped>
.local-workflows { margin: 22px 0; }
.picker-heading { display: flex; flex-wrap: wrap; gap: 8px; justify-content: space-between; margin-bottom: 12px; }
label { font-size: 13px; font-weight: 600; color: #35425c; }
.picker-heading > span { color: #8a79b3; font-size: 11px; }
.browse-button { margin: 0 0 14px; padding: 0; border: 0; background: none; color: #8066b6; cursor: pointer; font-size: 11px; }
.browse-intro { font-size: 12px; color: #8b96ac; margin: 0 0 18px; }
.browse-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 17px 0; border-bottom: 1px solid #e9ecf2; }
.browse-row b { font-size: 13px; color: #35425c; }
.browse-row p { font-size: 12px; margin: 7px 0; color: #66748d; }
.browse-row small { color: #8b96ac; font-size: 11px; }
.browse-row button { flex-shrink: 0; padding: 8px 12px; border: 1px solid #dcd2f2; color: #8066b6; background: #f5f1fc; border-radius: 7px; cursor: pointer; font-size: 12px; }
.family-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }
.family-grid button { text-align: left; min-width: 0; padding: 13px 14px; border: 1px solid #e4e7ef; background: #fff; border-radius: 10px; color: #44516c; cursor: pointer; }
.family-grid button.active { border-color: #9279d8; background: #f5f1fc; box-shadow: inset 0 0 0 1px #9279d8; }
.family-grid button:focus-visible { outline: 2px solid #9279d8; outline-offset: 2px; }
.family-grid button:disabled { cursor: wait; opacity: .6; }
.family-grid b { display: block; font-size: 12px; overflow-wrap: anywhere; }
.family-grid small { display: block; color: #939db0; margin-top: 6px; font-size: 10px; }
.variant-label { display: block; margin: 17px 0 9px; }
.variant-select { width: 100%; }
.workflow-summary { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 11px; font-size: 10px; color: #8b96ac; }
.state { background: #f3f0fa; color: #8066b6; border-radius: 5px; padding: 5px 8px; }
.state.available { color: #418068; background: #eaf7f1; }
@media (max-width: 400px) { .family-grid { grid-template-columns: 1fr; } }
</style>
