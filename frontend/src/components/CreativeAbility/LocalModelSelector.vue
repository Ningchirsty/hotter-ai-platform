<template>
  <div class="local-model-editor">
    <section class="model-section" :aria-label="media === 'image' ? '本地图像模型选择' : '本地视频模型选择'">
      <div class="field-heading"><h3>{{ media === 'image' ? '选择图像模型' : '选择视频模型' }}</h3><span>{{ visibleModels.length }} / {{ models.length }}</span></div>
      <input v-model="keyword" class="model-search" type="search" :aria-label="media === 'image' ? '搜索本地图像模型' : '搜索本地视频模型'"
        placeholder="搜索模型名称…" :disabled="busy" />
      <div class="family-tabs" aria-label="本地模型系列">
        <button v-for="item in families" :key="item" type="button" :class="{ active: family === item }"
          :aria-pressed="family === item" :disabled="busy" @click="family = item">{{ item }}</button>
      </div>
      <div class="model-grid" :aria-label="media === 'image' ? '本地图像模型' : '本地视频模型'">
        <button v-for="item in visibleModels" :key="item.code" type="button" :class="['model-card', { active: selectedModel?.code === item.code }]"
          :aria-pressed="selectedModel?.code === item.code" :disabled="busy" :aria-label="'选择模型 ' + item.name" @click="chooseModel(item.code)">
          <span class="model-card-top"><span class="model-symbol">{{ item.family.slice(0, 1) }}</span><span class="model-family">{{ item.family }}</span>
            <span class="selection-dot" aria-hidden="true">{{ selectedModel?.code === item.code ? '✓' : '' }}</span></span>
          <strong>{{ item.name }}</strong>
          <span class="model-card-bottom"><b>本地生成</b><em>{{ item.choices.length }} 项创作能力</em></span>
        </button>
      </div>
      <div v-if="!visibleModels.length" class="empty-models" role="status">没有匹配的模型。<button type="button" :disabled="busy" @click="keyword = ''; family = '全部'">重置筛选</button></div>
    </section>

    <section v-if="selectedModel" class="selected-model" aria-label="当前选择的本地模型" aria-live="polite">
      <div><small>当前选择</small><strong>{{ selectedModel.name }}</strong></div><span>{{ selectedChoice?.name || '请选择创作能力' }}</span>
    </section>
    <section v-if="selectedModel" class="ability-section">
      <div class="field-heading"><h3>{{ media === 'image' ? '图像创作能力' : '视频创作能力' }}</h3><span>{{ selectedModel.choices.length }} 项可选</span></div>
      <div class="capability-grid" :aria-label="media === 'image' ? '本地图像创作能力' : '本地视频创作能力'">
        <button v-for="item in selectedModel.choices" :key="item.key" type="button" :class="{ active: selectedChoice?.key === item.key }"
          :aria-pressed="selectedChoice?.key === item.key" :disabled="busy" :aria-label="'选择能力 ' + item.name" @click="chooseChoice(item)">
          <strong>{{ item.name }}</strong><small>{{ item.desc }}</small><em>{{ choiceStatus(item) }}</em>
        </button>
      </div>
      <details v-if="selectedChoice && !hideWorkflowSettings" class="workflow-settings">
        <summary>流程设置{{ selectedChoice.workflows.length > 1 ? ' · ' + selectedChoice.workflows.length + ' 个版本' : '' }}</summary>
        <label>工作流版本<select :value="modelValue" :disabled="busy" aria-label="本地工作流版本" @change="chooseWorkflow(($event.target as HTMLSelectElement).value)">
          <option v-for="item in selectedChoice.workflows" :key="item.workflowCode" :value="item.workflowCode">{{ item.name }}{{ item.version ? ' · ' + item.version : '' }}</option>
        </select></label>
        <p>{{ choiceStatus(selectedChoice) }}{{ selectedWorkflow?.hasAudio ? ' · 含音频' : '' }}</p>
      </details>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { matchRegisteredWorkflow, type LocalWorkflowOption, type RegisteredWorkflow } from '../LocalWorkflowPicker/types';
import type { CreativeAbility } from './types';
import { localCreationModels, localModelWorkflow, type LocalCreationChoice } from './model-selection';
const props = defineProps<{ media: 'image' | 'video'; abilities: CreativeAbility[]; catalog: LocalWorkflowOption[];
  general: Array<{ code: string; name: string; desc: string }>; registry: RegisteredWorkflow[]; modelValue: string; busy?: boolean; hideWorkflowSettings?: boolean }>();
const emit = defineEmits<{ choose: [workflowCode: string] }>();
const keyword = ref('');
const family = ref('全部');
const models = computed(() => localCreationModels(props.media, props.abilities, props.catalog, props.general));
const families = computed(() => ['全部', ...new Set(models.value.map(item => item.family))]);
const visibleModels = computed(() => models.value.filter(item => (family.value === '全部' || item.family === family.value)
  && (item.name + ' ' + item.family).toLowerCase().includes(keyword.value.trim().toLowerCase())));
const selectedModel = computed(() => models.value.find(item => item.choices.some(choice => choice.workflows.some(w => w.workflowCode === props.modelValue))));
const selectedChoice = computed(() => selectedModel.value?.choices.find(item => item.workflows.some(w => w.workflowCode === props.modelValue)));
const selectedWorkflow = computed(() => selectedChoice.value?.workflows.find(w => w.workflowCode === props.modelValue));
const publishedCodes = computed(() => models.value.flatMap(model => model.choices.flatMap(choice => choice.workflows))
  .filter(workflow => { const registered = matchRegisteredWorkflow(props.media, workflow, props.registry); return registered?.status === 'PUBLISHED' && registered.submittable; })
  .map(workflow => workflow.workflowCode));
function chooseModel(code: string) {
  const workflow = localModelWorkflow(models.value, code, props.modelValue, publishedCodes.value);
  if (workflow) chooseWorkflow(workflow);
}
function chooseChoice(choice: LocalCreationChoice) {
  const workflow = choice.workflows.find(item => item.workflowCode === props.modelValue)
    ?? choice.workflows.find(item => publishedCodes.value.includes(item.workflowCode)) ?? choice.workflows[0];
  if (workflow) chooseWorkflow(workflow.workflowCode);
}
function chooseWorkflow(code: string) {
  if (!props.busy && code !== props.modelValue && models.value.some(model => model.choices.some(choice => choice.workflows.some(w => w.workflowCode === code)))) emit('choose', code);
}
function choiceStatus(choice: LocalCreationChoice) {
  const selected = choice.workflows.find(item => item.workflowCode === props.modelValue);
  const versions = selected ? [selected] : choice.workflows;
  if (versions.some(item => publishedCodes.value.includes(item.workflowCode))) return '已发布';
  if (choice.kind === 'purpose') return '用途已接入 · 待发布';
  return versions.some(item => item.verifiedAt) ? 'ComfyUI 已验证 · 待发布' : '待发布';
}
</script>
<style scoped>
.local-model-editor { min-width:0; color:var(--t1); }
.field-heading,.model-card-top,.model-card-bottom,.selected-model { display:flex; align-items:center; justify-content:space-between; gap:10px; }
.field-heading { margin-bottom:10px; } h3 { margin:0; font-size:13px; font-weight:600; }
.field-heading > span { color:var(--t2); font-size:11px; }
.model-search,select { box-sizing:border-box; width:100%; min-width:0; border:1px solid var(--line2); background:var(--sunken); color:var(--t1); border-radius:8px; padding:10px; font:inherit; font-size:12px; }
.model-search::placeholder { color:var(--t3); }
.family-tabs { display:flex; flex-wrap:wrap; gap:6px; margin:10px 0 12px; }
.family-tabs button { border:1px solid transparent; background:var(--sunken); color:var(--t2); border-radius:6px; padding:6px 9px; font-size:11px; cursor:pointer; }
.family-tabs button.active { color:var(--p); background:var(--tint); border-color:var(--p); }
.model-grid,.capability-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; }
.model-card { min-width:0; display:flex; flex-direction:column; gap:12px; padding:13px; text-align:left; color:var(--t1); background:var(--surface); border:1px solid var(--line2); border-radius:10px; cursor:pointer; }
.model-card.active { background:var(--tint); border-color:var(--p); }
.model-card-top,.model-card-bottom { width:100%; } .model-card-bottom { margin-top:auto; flex-wrap:wrap; gap:5px; }
.model-symbol { display:grid; place-items:center; width:27px; height:27px; flex-shrink:0; border-radius:8px; color:var(--p); background:var(--sunken); font-size:12px; font-weight:700; }
.model-family { flex:1; color:var(--t2); font-size:11px; }
.selection-dot { width:16px; height:16px; display:grid; place-items:center; border:1px solid var(--line2); border-radius:50%; font-size:10px; flex-shrink:0; }
.active .selection-dot { background:var(--p); color:white; border-color:var(--p); }
.model-card strong { font-size:12px; font-weight:600; line-height:1.6; overflow-wrap:anywhere; }
.model-card-bottom b { font-size:12px; font-weight:600; } em { color:var(--p); font-size:10px; font-style:normal; }
.selected-model { padding:12px; margin:22px 0; border:1px solid var(--line2); border-radius:9px; background:var(--sunken); }
.selected-model small { display:block; color:var(--t2); font-size:10px; margin-bottom:5px; }
.selected-model strong { font-size:13px; overflow-wrap:anywhere; }
.selected-model > span { color:var(--p); background:var(--tint); border-radius:6px; padding:5px 9px; font-size:11px; }
.capability-grid { gap:8px; } .capability-grid button { text-align:left; cursor:pointer; min-width:0; border:1px solid var(--line2); border-radius:7px; background:var(--surface); color:var(--t2); padding:10px 12px; }
.capability-grid button.active { border-color:var(--p); color:var(--p); background:var(--tint); }
.capability-grid strong,.capability-grid small,.capability-grid em { display:block; line-height:1.7; }
.capability-grid strong { font-size:12px; } .capability-grid small { font-size:10px; color:var(--t2); margin:5px 0; }
.workflow-settings { margin-top:14px; border:1px solid var(--line2); border-radius:8px; padding:10px 12px; }
.workflow-settings summary { color:var(--p); font-size:11px; cursor:pointer; }
.workflow-settings label { display:block; font-size:11px; color:var(--t2); margin-top:12px; } select { margin-top:7px; }
.workflow-settings p { font-size:10px; color:var(--t2); line-height:1.7; margin:8px 0 0; }
.empty-models { padding:24px 10px; text-align:center; background:var(--sunken); border-radius:9px; font-size:12px; color:var(--t2); }
.empty-models button { background:none; border:0; color:var(--p); cursor:pointer; }
:is(button,input,select):focus-visible { outline:2px solid var(--p); outline-offset:2px; }
:disabled { opacity:.6; cursor:not-allowed; }
@media(max-width:420px) { .model-grid { grid-template-columns:1fr; } }
</style>
