<template>
  <div class="cloud-image-editor editor-body" aria-label="云端图像创建任务">
    <section class="cloud-field" aria-labelledby="cloud-image-model-label">
      <div class="field-heading">
        <h3 id="cloud-image-model-label">云端生成模型</h3>
        <span>{{ visibleModels.length }} / {{ models.length }}</span>
      </div>
      <input
        v-model="keyword"
        class="model-search"
        type="search"
        aria-label="搜索云端图像模型"
        placeholder="搜索模型名称…"
        :disabled="busy || materialProcessing"
      />
      <div class="family-tabs" aria-label="模型系列">
        <button
          v-for="item in families"
          :key="item"
          type="button"
          :class="{ active: family === item }"
          :aria-pressed="family === item"
          :disabled="busy || materialProcessing"
          @click="family = item"
        >
          {{ item }}
        </button>
      </div>
      <div class="cloud-models" aria-label="云端图像模型">
        <button
          v-for="item in visibleModels"
          :key="item.id"
          type="button"
          :class="['model-card', { active: modelId === item.id }]"
          :aria-pressed="modelId === item.id"
          :disabled="busy || materialProcessing"
          @click="modelId = item.id"
        >
          <span class="model-card-top">
            <span class="model-symbol">{{ item.family === 'GPT Image' ? 'G' : item.family?.slice(0, 1) }}</span>
            <span class="model-family">{{ item.family }}</span>
            <span class="selection-dot" aria-hidden="true">{{ modelId === item.id ? '✓' : '' }}</span>
          </span>
          <strong>{{ item.name }}</strong>
          <span class="model-card-bottom">
            <span class="model-price">
              {{ formatPrice(item.priceUsd) }}
              <small>/ 请求</small>
            </span>
            <em>{{ modelBadge(item.id) }}</em>
          </span>
        </button>
      </div>
      <div v-if="!visibleModels.length" class="empty-models" role="status">
        没有匹配的模型。
        <button
          type="button"
          :disabled="busy || materialProcessing"
          @click="
            keyword = '';
            family = '全部';
          "
        >
          重置筛选
        </button>
      </div>
    </section>

    <section class="selected-model" aria-label="当前选择的模型" aria-live="polite">
      <div>
        <small>当前选择</small>
        <strong>{{ model.name }}</strong>
      </div>
      <span>{{ capability.name }}</span>
    </section>

    <section class="cloud-field" aria-labelledby="cloud-image-capability-label">
      <h3 id="cloud-image-capability-label">创作能力</h3>
      <div class="capability-options ability-grid">
        <button v-for="item in capabilities" :key="item.code" type="button" :class="{active: mode === item.code}" :aria-pressed="mode === item.code" :disabled="busy || materialProcessing" @click="mode = item.code">
          <strong>{{ item.name }}</strong><small>{{ item.description }}</small><em>{{ imageCapabilityStatusLabel(capabilityStatus(item.code)) }}</em>
        </button>
      </div>
      <p v-if="!verified(mode)" class="field-help capability-status" role="status">{{ imageCapabilityStatusLabel(capabilityStatus(mode)) }}，此项能力暂不可提交。</p>
      <CloudImageMaterials v-if="needsReference" :key="modelId + mode" :mode="mode" :busy="busy" :enabled="availableModel(modelId) && verified(mode)" :max-references="referenceLimit" :max-file-bytes="referenceBytes" @change="materials = $event; materialProcessing = $event.processing" />
    </section>

    <section class="cloud-field">
      <label for="cloud-image-prompt">
        创作描述
        <em>*</em>
      </label>
      <textarea
        id="cloud-image-prompt"
        v-model="draft.prompt"
        :disabled="busy || materialProcessing"
        maxlength="1000"
        rows="5"
        :placeholder="capability.prompt"
      />
      <div class="prompt-footer">
        <span>切换模型与能力保留各自描述草稿，离开页面后清空</span>
        <span>{{ draft.prompt.length }} 字符</span>
      </div>
    </section>

    <section class="cloud-field" aria-labelledby="cloud-image-output-label">
      <div class="field-heading">
        <h3 id="cloud-image-output-label">输出设置</h3>
        <span>{{ selectedOutputLabel }}</span>
      </div>
      <div class="parameter-grid">
        <label v-for="item in outputFields" :key="item.key">
          {{ item.label }}
          <select v-if="outputValues(item.key).length > 1" :value="outputValue(selectedOutput,item.key)" :aria-label="item.label" :disabled="busy || materialProcessing" @change="selectOutput(item.key,($event.target as HTMLSelectElement).value)">
            <option v-for="value in outputValues(item.key)" :key="value" :value="value">{{ outputLabel(item.key,value) }}{{ outputParameterStatusLabel(props.cloudStatus,modelId,mode,item.key,value) }}</option>
          </select>
          <span v-else class="output-fixed">{{ outputLabel(item.key,outputValue(selectedOutput,item.key)) }}</span>
        </label>
      </div>
      <p class="field-help">参数按型号和创作能力分别验收，已通过的选项可提交。</p>
    </section>

    <section class="cloud-summary" aria-label="模型与费用信息">
      <div>
        <span>{{ model.family }} · {{ model.name }}</span>
        <b>
          {{ formatPrice(model.priceUsd) }}
          <small>/ 请求（参考）</small>
        </b>
      </div>
      <p>提交后会调用云端 API 并产生费用，结果归入「我的任务」和「素材库」。</p>
      <a :href="model.docs" target="_blank" rel="noopener noreferrer">平台接口说明 ↗</a>
    </section>
    <p class="field-help inspiration-hint">右侧作品可匹配已验收的云端模型与本地工作流；带入后请确认素材和参数。</p>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { cloudModelsFor } from './cloud-models';
import { IMAGE_CLOUD_CAPABILITIES, imageCapabilityVerified, imageCapabilitiesForModel, imageCapabilityStatusLabel, type ImageCloudCapability } from './cloud-image-capabilities';
import CloudImageMaterials from './CloudImageMaterials.vue';
import {outputFields,outputValue,outputCandidates,outputParameterStatusLabel,outputVerified,updateOutput,outputDefaults,observedOutputFormat,type OutputKey} from './cloud-image-output';
const props = defineProps<{ busy?: boolean; cloudStatus?: import('@/api/image/types').CloudImageModelsVO; inspiration?: { route: import('./types').InspirationRoute; stamp: number } }>();
const emit = defineEmits<{ change: [draft: import('@/api/image/types').CloudImageDraft] }>();
function availableModel(id: string) {
  return Boolean(props.cloudStatus?.configured && props.cloudStatus.models.includes(id));
}
const models = cloudModelsFor('image');
const families = ['全部', 'GPT Image', 'Qwen', 'Wan'] as const;
const modelId = ref(models.find(item => item.generationTestedAt)?.id ?? models[0].id);
const family = ref<string>('全部');
const keyword = ref('');
const model = computed(() => models.find(item => item.id === modelId.value)!);
const visibleModels = computed(() =>
  models.filter(
    item =>
      (family.value === '全部' || item.family === family.value) &&
      item.name.toLowerCase().includes(keyword.value.trim().toLowerCase())
  )
);
const mode = ref<ImageCloudCapability>('T2I');
const isGpt = computed(() => model.value.family === 'GPT Image');
const capabilities = computed(() => imageCapabilitiesForModel(modelId.value));
const capability = computed(() => IMAGE_CLOUD_CAPABILITIES.find(item => item.code === mode.value)!);
function verified(code: string) { return imageCapabilityVerified(props.cloudStatus, modelId.value, code); }
function capabilityStatus(code: string) { return props.cloudStatus?.profiles?.find(p => p.model === modelId.value)?.capabilities.find(c => c.code === code)?.status; }
function modelBadge(id: string) {
  const profile = props.cloudStatus?.profiles?.find(p => p.model === id);
  const count = profile?.capabilities.filter(c => c.verified).length ?? 0;
  return count ? `${count} 项已验证` : imageCapabilityStatusLabel(profile?.capabilities.find(c => c.code === 'T2I')?.status);
}
const referenceLimit = computed(() => props.cloudStatus?.profiles?.find(p => p.model === modelId.value)?.maxReferenceImages ?? (model.value.family === 'Qwen' ? 3 : model.value.family === 'Wan' ? 9 : 16));
const referenceBytes = computed(() => props.cloudStatus?.profiles?.find(p => p.model === modelId.value)?.maxReferenceBytes ?? (isGpt.value ? 20 : 10)*1024*1024);
const needsReference = computed(() => ['EDIT','MULTI','MASK','OUTPAINT'].includes(mode.value));
const materials = ref<{ ids: (number|string)[]; maskId?: number|string; ready: boolean; processing: boolean }>({ ids: [], ready: false, processing: false });
const materialProcessing = ref(false);
const drafts = reactive<Record<string, {prompt:string; output?: import('@/api/image/types').CloudImageOutputParams}>>(Object.fromEntries(models.flatMap(item => IMAGE_CLOUD_CAPABILITIES.map(cap => [item.id + ':' + cap.code, {prompt:''}]))));
const draft = computed(() => drafts[modelId.value + ':' + mode.value]);
watch([modelId, mode], () => { materials.value = {ids:[],ready:false,processing:false}; materialProcessing.value=false; if (!capabilities.value.some(item => item.code === mode.value)) mode.value='T2I'; });
let initialModelSelected = false;
watch(() => props.cloudStatus, status => {
  if (!status || initialModelSelected) return;
  initialModelSelected = true;
  const eligible = models.filter(item => status.models.includes(item.id) && imageCapabilityVerified(status, item.id, 'T2I'));
  const preferred = eligible.find(item => outputDefaults(status,item.id,'T2I').size) ?? eligible[0];
  if (preferred) modelId.value = preferred.id;
}, { immediate: true });
const selectedOutput = computed(() => draft.value.output ?? outputDefaults(props.cloudStatus,modelId.value,mode.value));
const selectedOutputLabel = computed(() => [selectedOutput.value.size?.replace('x',' × '), `${selectedOutput.value.n ?? 1} 张`].filter(Boolean).join(' · '));
function outputValues(key:OutputKey):string[] { return outputCandidates(modelId.value,mode.value,key); }
function outputLabel(key:OutputKey,value:string) {
  if(key === 'n') return value+' 张';
  if(!value) {
    if(key === 'size') return '未指定尺寸';
    if(key === 'quality') return isGpt.value ? '未指定画质档位' : '无画质档位参数';
    const format=observedOutputFormat(props.cloudStatus,modelId.value,mode.value);
    return format ? `${format}（实测；未指定格式）` : '保留原始格式';
  }
  if(key === 'quality') return ({low:'标准（low）',medium:'精细（medium）',high:'高质量（high）'} as Record<string,string>)[value] ?? value;
  if(key === 'size') return value.replace('x',' × ');
  return value.toUpperCase();
}
function selectOutput(key:OutputKey,value:string) { draft.value.output=updateOutput(selectedOutput.value,key,value); }
watch(() => [modelId.value, mode.value, draft.value.prompt, props.cloudStatus, materials.value, draft.value.output] as const, () => {
  const ready = availableModel(modelId.value) && verified(mode.value) && outputVerified(props.cloudStatus,modelId.value,mode.value,selectedOutput.value) && !materialProcessing.value && (!needsReference.value || materials.value.ready);
  const blockReason = !verified(mode.value) ? '此能力尚未通过供应商接口验证，暂不可提交' : !availableModel(modelId.value) ? '云端 API Key 尚未配置，暂不可提交' : needsReference.value && !materials.value.ready ? '请完成参考素材与蒙版设置' : !outputVerified(props.cloudStatus,modelId.value,mode.value,selectedOutput.value) ? '所选输出参数尚未验证通过，暂不可提交' : '';
  emit('change', { model: modelId.value, prompt: draft.value.prompt, capability: mode.value, referenceAssetIds: needsReference.value ? materials.value.ids : [], maskAssetId: needsReference.value ? materials.value.maskId : undefined, ready, blockReason, output: selectedOutput.value });
}, { immediate: true, deep: true });
watch(() => props.inspiration, value => {
  if (!value || props.busy) return;
  const route = value.route;
  if (!availableModel(route.model) || !imageCapabilityVerified(props.cloudStatus, route.model, route.capability)) return;
  modelId.value = route.model; mode.value = route.capability as ImageCloudCapability;
  family.value = '全部'; keyword.value = '';
  drafts[route.model + ':' + route.capability].prompt = route.prompt;
}, { immediate: true });
function formatPrice(price?: number): string {
  return price === undefined ? '待确认' : `$${price.toFixed(4).replace(/0+$/, '').replace(/\.$/, '')}`;
}
</script>

<style scoped>
.cloud-image-editor {
  min-width: 0;
  color: var(--t1);
}
.field-heading,
.model-card-top,
.model-card-bottom,
.prompt-footer,
.selected-model {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.selected-model > span {
  color: var(--p);
  background: var(--tint);
  border-radius: 6px;
  padding: 5px 9px;
  font-size: 11px;
  white-space: nowrap;
}
a {
  color: var(--p);
  text-decoration: none;
  font-size: 11px;
}
.cloud-field {
  margin: 22px 0;
}
.cloud-field:first-child {
  margin-top: 0;
}
h3,
.cloud-field > label {
  display: block;
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
}
.field-heading {
  margin-bottom: 10px;
}
.field-heading h3 {
  margin: 0;
}
.field-heading > span {
  font-size: 11px;
  color: var(--t2);
}
.model-search,
textarea,
select {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  border: 1px solid var(--line2);
  background: var(--sunken);
  color: var(--t1);
  border-radius: 8px;
  padding: 10px;
  font: inherit;
  font-size: 12px;
}
textarea {
  resize: vertical;
  line-height: 1.8;
}
textarea::placeholder,
input::placeholder {
  color: var(--t3);
}
.family-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0 12px;
}
.family-tabs button {
  border: 1px solid transparent;
  background: var(--sunken);
  color: var(--t2);
  border-radius: 6px;
  padding: 6px 9px;
  font-size: 11px;
  cursor: pointer;
}
.family-tabs button.active {
  color: var(--p);
  background: var(--tint);
  border-color: var(--p);
}
.cloud-models {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.model-card {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 13px;
  text-align: left;
  color: var(--t1);
  background: var(--surface);
  border: 1px solid var(--line2);
  border-radius: 10px;
  cursor: pointer;
}
.model-card.active {
  background: var(--tint);
  border-color: var(--p);
}
.model-card-top,
.model-card-bottom {
  width: 100%;
}
.model-symbol {
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  border-radius: 8px;
  color: var(--p);
  background: var(--sunken);
  font-size: 12px;
  font-weight: 700;
}
.model-family {
  flex: 1;
  color: var(--t2);
  font-size: 11px;
}
.selection-dot {
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line2);
  border-radius: 50%;
  font-size: 10px;
}
.active .selection-dot {
  background: var(--p);
  color: white;
  border-color: var(--p);
}
.model-card strong {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.model-card-bottom {
  margin-top: auto;
  flex-wrap: wrap;
  gap: 5px;
}
.model-price {
  font-size: 13px;
  font-weight: 700;
}
.model-price small,
.cloud-summary small {
  font-size: 10px;
  color: var(--t2);
  font-weight: 400;
}
em {
  color: var(--p);
  font-size: 10px;
  font-style: normal;
}
.field-help {
  margin: 9px 0;
  color: var(--t2);
  font-size: 11px;
  line-height: 1.7;
}
.selected-model {
  padding: 12px;
  border: 1px solid var(--line2);
  border-radius: 9px;
  background: var(--sunken);
}
.selected-model small {
  display: block;
  color: var(--t2);
  font-size: 10px;
  margin-bottom: 5px;
}
.selected-model strong {
  font-size: 13px;
  overflow-wrap: anywhere;
}
.ability-grid { display: grid !important; grid-template-columns: repeat(2,minmax(0,1fr)); }
.ability-grid button { text-align:left; cursor:pointer; min-width:0; }
.ability-grid strong,.ability-grid small,.ability-grid em { display:block; line-height:1.7; }
.ability-grid small { font-size:10px; color:var(--t2); margin:5px 0; }
.capability-status { padding:10px; background:var(--sunken); border-radius:8px; }
.capability-options {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
.capability-options button {
  border: 1px solid var(--line2);
  border-radius: 7px;
  background: var(--surface);
  color: var(--t2);
  padding: 8px 10px;
  font-size: 11px;
}
.capability-options button.active {
  border-color: var(--p);
  color: var(--p);
  background: var(--tint);
}
.prompt-footer {
  align-items: flex-start;
  margin-top: 6px;
  color: var(--t2);
  font-size: 10px;
  line-height: 1.6;
}
.prompt-footer > span:last-child {
  white-space: nowrap;
}
.parameter-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.parameter-grid label {
  font-size: 12px;
  color: var(--t2);
  min-width: 0;
}
.parameter-grid select {
  display: block;
  margin-top: 7px;
}
.cloud-summary {
  padding: 14px;
  border-radius: 10px;
  background: var(--sunken);
}
.cloud-summary span {
  display: block;
  color: var(--t2);
  font-size: 11px;
  overflow-wrap: anywhere;
}
.cloud-summary b {
  display: block;
  margin-top: 8px;
  color: var(--p);
  font-size: 17px;
}
.cloud-summary p {
  font-size: 11px;
  color: var(--t2);
  line-height: 1.7;
}
.inspiration-hint {
  margin-top: 16px;
}
.empty-models {
  padding: 24px 10px;
  text-align: center;
  background: var(--sunken);
  border-radius: 9px;
  font-size: 12px;
  color: var(--t2);
}
.empty-models button {
  background: none;
  border: 0;
  color: var(--p);
  cursor: pointer;
}
:is(button, input, textarea, select):focus-visible {
  outline: 2px solid var(--p);
  outline-offset: 2px;
}
:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
@media (max-width: 420px) {
  .cloud-models {
    grid-template-columns: 1fr;
  }
}
</style>

<style scoped>
.output-fixed { display:block; padding:10px; margin-top:6px; border-radius:6px; background:var(--sunken); color:var(--t2); font-size:12px; }
</style>
