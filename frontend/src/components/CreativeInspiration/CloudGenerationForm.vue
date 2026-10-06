<template>
  <div class="cloud-editor editor-body" :aria-label="media === 'video' ? '云端视频创建任务' : '云端图像创建任务'">
    <div class="cloud-heading">
      <div>
        <span>创建任务 · 云端</span>
        <h2>{{ capabilityInfo.name }}</h2>
      </div>
      <b>待接入</b>
    </div>
    <p class="cloud-notice">可选择模型并配置草稿；外部 API 接入后开放提交。</p>
    <div class="cloud-field">
      <label>云端生成模型</label>
      <div class="cloud-models" aria-label="云端模型">
        <button
          v-for="item in models"
          :key="item.id"
          type="button"
          :class="{ active: modelId === item.id }"
          :aria-pressed="modelId === item.id"
          :disabled="busy"
          @click="selectModel(item)"
        >
          <span>
            <strong>{{ item.name }}</strong>
            <em>待接入</em>
          </span>
          <small>{{ item.provider }}</small>
          <p>{{ item.description }}</p>
        </button>
      </div>
      <div class="model-documentation">
        <span>候选模型 · 未配置服务</span>
        <a :href="model.docs" target="_blank" rel="noopener noreferrer">官方能力说明 ↗</a>
      </div>
    </div>
    <div class="cloud-field">
      <label>创作能力</label>
      <div class="cloud-capabilities" aria-label="云端创作能力">
        <button
          v-for="code in model.capabilities"
          :key="code"
          type="button"
          :class="{ active: capability === code }"
          :aria-pressed="capability === code"
          :disabled="busy"
          @click="capability = code"
        >
          {{ CLOUD_CAPABILITIES[code].name }}
        </button>
      </div>
      <p class="field-help">{{ capabilityInfo.description }} · 仅展示所选模型支持的能力</p>
    </div>
    <div v-if="slots.length" class="cloud-field">
      <label>创作素材</label>
      <p class="field-help">仅在本机预览，当前不会上传至外部服务。单张不超过 10 MB。</p>
      <div class="cloud-materials">
        <div v-for="slot in slots" :key="slot.key" class="material-slot">
          <label :for="`${media}-cloud-${slot.key}`">
            {{ slot.label }}
            <em v-if="slot.required">*</em>
            <small v-else>可选</small>
          </label>
          <div v-if="material(slot.key)" class="material-preview">
            <img :src="material(slot.key)?.url" :alt="slot.label" />
            <span>{{ material(slot.key)?.file.name }}</span>
            <button type="button" :disabled="busy" :aria-label="`移除${slot.label}`" @click="removeMaterial(slot.key)">
              移除
            </button>
          </div>
          <input
            :id="`${media}-cloud-${slot.key}`"
            type="file"
            accept="image/jpeg,image/png,image/webp,image/bmp,image/gif"
            :disabled="busy"
            @change="chooseMaterial($event, slot.key)"
          />
        </div>
      </div>
      <p v-if="materialError" class="field-error" role="alert">{{ materialError }}</p>
    </div>
    <div v-if="capability === 'EXTEND'" class="cloud-field">
      <label :for="`${media}-cloud-source-task`">原视频任务</label>
      <input :id="`${media}-cloud-source-task`" type="text" disabled placeholder="接入后选择 Veo 已完成任务" />
      <p class="field-help">仅支持该模型此前生成的视频；正式接入后从我的任务选择，不能使用任意参考视频。</p>
    </div>
    <div class="cloud-field">
      <label :for="`${media}-cloud-prompt`">
        {{ media === 'video' ? '视频描述' : '创作描述' }}
        <em>*</em>
      </label>
      <textarea
        :id="`${media}-cloud-prompt`"
        v-model="draft.prompt"
        :disabled="busy"
        :maxlength="promptLimit"
        rows="4"
        :placeholder="
          media === 'video'
            ? '描述主体、场景、镜头运动，以及希望呈现的氛围…'
            : '描述画面、构图、风格，或希望修改的部分…'
        "
      />
      <div class="text-count">{{ draft.prompt.length }} / {{ promptLimit }} 字符</div>
    </div>
    <div class="cloud-field">
      <label>输出参数</label>
      <div class="parameter-grid">
        <label :for="`${media}-cloud-output`">
          {{ media === 'video' ? '清晰度' : '图像尺寸' }}
          <select :id="`${media}-cloud-output`" v-model="draft.output" :disabled="busy">
            <option v-for="option in outputs" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label v-if="media === 'video'" :for="`${media}-cloud-duration`">
          视频时长
          <select :id="`${media}-cloud-duration`" v-model="draft.duration" :disabled="busy">
            <option v-for="seconds in durations" :key="seconds" :value="seconds">{{ seconds }} 秒</option>
          </select>
        </label>
        <label v-if="model.id === 'veo-3.1-generate-preview' && capability !== 'EXTEND'" :for="`${media}-cloud-ratio`">
          画面比例
          <select :id="`${media}-cloud-ratio`" v-model="draft.ratio" :disabled="busy">
            <option>16:9</option>
            <option>9:16</option>
          </select>
        </label>
        <label v-if="media === 'image'" :for="`${media}-cloud-count`">
          生成数量
          <select :id="`${media}-cloud-count`" v-model="draft.count" :disabled="busy">
            <option v-for="count in model.id === 'qwen-image-edit-plus' ? 6 : 1" :key="count" :value="count">
              {{ count }} 张
            </option>
          </select>
        </label>
      </div>
      <p v-if="media === 'video'" class="field-help">{{ outputHint }}</p>
      <p v-if="model.id === 'veo-3.1-generate-preview'" class="field-help">
        原生生成音频，可在视频描述中加入对白、音乐或环境声。
      </p>
    </div>
    <details class="cloud-advanced">
      <summary>高级参数</summary>
      <div v-if="media === 'image' || model.id === 'veo-3.1-generate-preview'" class="cloud-field">
        <label :for="`${media}-cloud-negative`">
          反向描述
          <small>可选</small>
        </label>
        <textarea
          :id="`${media}-cloud-negative`"
          v-model="draft.negativePrompt"
          :disabled="busy"
          maxlength="500"
          rows="2"
          placeholder="不希望出现在画面中的内容…"
        />
      </div>
      <div v-if="media === 'image'" class="cloud-field">
        <label :for="`${media}-cloud-seed`">
          随机种子
          <small>留空为随机</small>
        </label>
        <input
          :id="`${media}-cloud-seed`"
          v-model="draft.seed"
          type="number"
          min="0"
          max="2147483647"
          :disabled="busy"
          placeholder="0 – 2147483647"
        />
      </div>
      <label v-if="model.id !== 'veo-3.1-generate-preview'" class="check-field">
        <input v-model="draft.optimize" type="checkbox" :disabled="busy" />
        智能优化提示词
      </label>
      <label v-if="media === 'image'" class="check-field">
        <input v-model="draft.watermark" type="checkbox" :disabled="busy" />
        添加模型水印
      </label>
    </details>
    <div class="cloud-summary">
      <span>{{ model.provider }} · {{ model.name }}</span>
      <strong>费用待配置</strong>
      <p>接入后根据所选参数显示预估费用。</p>
    </div>
    <p class="field-help">右侧灵感目前匹配已接入的本地模型；应用其创作方向会切换回本地生成。</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import type { CreativeMedia } from './types';
import {
  CLOUD_CAPABILITIES,
  cloudDurations,
  cloudMaterialSlots,
  cloudModelsFor,
  cloudOutputOptions,
  createCloudDraft,
  type CloudCapability,
  type CloudDraft,
  type CloudModel
} from './cloud-models';
const props = defineProps<{ media: CreativeMedia; busy?: boolean }>();
const models = computed(() => cloudModelsFor(props.media));
const modelId = ref(models.value[0].id);
const model = computed(() => models.value.find(item => item.id === modelId.value)!);
const capability = ref<CloudCapability>(model.value.capabilities[0]);
const capabilityInfo = computed(() => CLOUD_CAPABILITIES[capability.value]);
const drafts = reactive<Record<string, CloudDraft>>({});
const draftKey = computed(() => `${modelId.value}:${capability.value}`);
watch(
  draftKey,
  key => {
    drafts[key] ??= createCloudDraft(model.value, capability.value);
  },
  { immediate: true, flush: 'sync' }
);
const draft = computed(() => drafts[draftKey.value]);
const outputs = computed(() => cloudOutputOptions(model.value, capability.value));
const durations = computed(() => cloudDurations(model.value, capability.value, draft.value.output));
const slots = computed(() => cloudMaterialSlots(capability.value));
const promptLimit = computed(() => (props.media === 'image' ? 800 : 2000));
const outputHint = computed(() =>
  modelId.value === 'MiniMax-Hailuo-2.3'
    ? '768P 支持 6 / 10 秒；1080P 仅支持 6 秒。'
    : capability.value === 'EXTEND'
      ? '续写仅支持 720P；时长参数固定为 8 秒。'
      : capability.value === 'R2V'
        ? '多图参考时长固定为 8 秒。'
        : '720P 支持 4 / 6 / 8 秒；1080P、4K 仅支持 8 秒。'
);
watch(
  durations,
  options => {
    if (options.length && !options.includes(draft.value.duration)) draft.value.duration = options[0];
  },
  { immediate: true }
);
function selectModel(next: CloudModel) {
  modelId.value = next.id;
  if (!next.capabilities.includes(capability.value)) capability.value = next.capabilities[0];
  materialError.value = '';
}
interface LocalMaterial {
  file: File;
  url: string;
}
const materials = reactive<Record<string, Record<string, LocalMaterial>>>({});
const materialError = ref('');
function material(key: string): LocalMaterial | undefined {
  return materials[draftKey.value]?.[key];
}
function removeMaterial(key: string) {
  const selected = material(key);
  if (selected) URL.revokeObjectURL(selected.url);
  if (materials[draftKey.value]) delete materials[draftKey.value][key];
}
function chooseMaterial(event: Event, key: string) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  if (
    !['image/jpeg', 'image/png', 'image/webp', 'image/bmp', 'image/gif'].includes(file.type) ||
    file.size > 10 * 1024 * 1024
  ) {
    materialError.value = '请选择不超过 10 MB 的 JPG、PNG、WEBP、BMP 或 GIF 图片。';
    return;
  }
  removeMaterial(key);
  (materials[draftKey.value] ??= {})[key] = { file, url: URL.createObjectURL(file) };
  materialError.value = '';
}
watch(capability, () => {
  materialError.value = '';
});
onBeforeUnmount(() =>
  Object.values(materials).forEach(group => Object.values(group).forEach(item => URL.revokeObjectURL(item.url)))
);
</script>

<style scoped>
.cloud-editor {
  min-width: 0;
  color: var(--t1);
}
.cloud-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.cloud-heading span {
  color: var(--t2);
  font-size: 12px;
}
.cloud-heading h2 {
  margin: 6px 0 0;
  font-size: 22px;
}
.cloud-heading b {
  color: var(--p);
  background: var(--tint);
  padding: 5px 9px;
  border-radius: 6px;
  font-size: 11px;
  white-space: nowrap;
}
.cloud-notice {
  padding: 10px 12px;
  background: var(--tint);
  color: var(--p);
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.7;
}
.cloud-field {
  margin: 20px 0;
}
.cloud-field > label {
  display: block;
  margin-bottom: 9px;
  font-size: 13px;
  font-weight: 600;
}
.cloud-editor em {
  color: var(--p);
  font-style: normal;
}
.cloud-editor small {
  color: var(--t2);
  font-size: 11px;
  font-weight: 400;
}
.cloud-models {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}
.cloud-models button {
  padding: 12px 10px;
  background: var(--surface);
  color: var(--t1);
  border: 1px solid var(--line2);
  border-radius: 9px;
  text-align: left;
  cursor: pointer;
  min-width: 0;
}
.cloud-models button.active,
.cloud-capabilities button.active {
  border-color: var(--p);
  background: var(--tint);
  color: var(--p);
}
.cloud-models span {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
}
.cloud-models strong {
  font-size: 13px;
  overflow-wrap: anywhere;
}
.cloud-models em {
  font-size: 10px;
}
.cloud-models small {
  display: block;
  margin-top: 6px;
}
.cloud-models p {
  color: var(--t2);
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.6;
}
.model-documentation {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: var(--t2);
  margin-top: 10px;
}
.model-documentation a {
  color: var(--p);
  text-decoration: none;
}
.cloud-capabilities {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.cloud-capabilities button {
  border: 1px solid var(--line2);
  color: var(--t2);
  background: var(--surface);
  padding: 9px 5px;
  border-radius: 7px;
  cursor: pointer;
  font-size: 12px;
}
.field-help {
  font-size: 11px;
  color: var(--t2);
  line-height: 1.7;
  margin: 8px 0;
}
.cloud-materials {
  display: grid;
  gap: 9px;
}
.material-slot {
  min-width: 0;
  padding: 10px;
  border: 1px dashed var(--line2);
  border-radius: 8px;
  background: var(--sunken);
}
.material-slot > label {
  font-size: 12px;
  display: block;
  margin-bottom: 7px;
}
.cloud-editor input[type='file'] {
  width: 100%;
  font-size: 11px;
  color: var(--t2);
}
.cloud-editor input[type='file']::file-selector-button {
  border: 1px solid var(--line2);
  color: var(--p);
  background: var(--surface);
  border-radius: 5px;
  padding: 6px 8px;
  margin-right: 8px;
  cursor: pointer;
}
.material-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 11px;
}
.material-preview img {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 5px;
}
.material-preview span {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.material-preview button {
  border: 0;
  background: none;
  color: var(--p);
  cursor: pointer;
}
.cloud-editor textarea,
.cloud-editor select,
.cloud-editor input[type='text'],
.cloud-editor input[type='number'] {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  border: 1px solid var(--line2);
  background: var(--sunken);
  color: var(--t1);
  border-radius: 7px;
  padding: 9px;
  font: inherit;
  font-size: 12px;
}
.cloud-editor textarea {
  resize: vertical;
  line-height: 1.8;
}
.cloud-editor textarea::placeholder,
.cloud-editor input::placeholder {
  color: var(--t3);
}
.cloud-editor :is(button, input, textarea, select):focus-visible {
  outline: 2px solid var(--p);
  outline-offset: 2px;
}
.cloud-editor :disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.text-count {
  text-align: right;
  color: var(--t2);
  margin-top: 4px;
  font-size: 10px;
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
.cloud-advanced {
  border-top: 1px solid var(--line);
  padding-top: 14px;
  font-size: 12px;
}
.cloud-advanced summary {
  cursor: pointer;
  color: var(--t2);
}
.check-field {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 12px 0;
}
.check-field input {
  accent-color: var(--p);
}
.cloud-summary {
  background: var(--sunken);
  border-radius: 8px;
  padding: 12px;
  margin-top: 20px;
  font-size: 11px;
  line-height: 1.7;
}
.cloud-summary strong {
  display: block;
  color: var(--p);
  margin-top: 6px;
  font-size: 13px;
}
.cloud-summary p {
  margin: 5px 0 0;
  color: var(--t2);
}
.field-error {
  color: #b94358;
  font-size: 12px;
}
</style>
