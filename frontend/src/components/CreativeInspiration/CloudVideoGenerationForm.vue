<template>
  <div class="cloud-editor editor-body" aria-label="云端视频创建任务">
    <div class="cloud-heading">
      <div>
        <span>创建任务 · 云端视频</span>
        <h2>{{ selected.group }}</h2>
      </div>
      <b>蓝章鱼</b>
    </div>
    <div class="cloud-field">
      <label>视频生成模型</label>
      <div class="cloud-models">
        <button
          v-for="group in groups"
          :key="group"
          :class="{ active: selected.group === group }"
          :disabled="busy"
          type="button"
          @click="selectProfile(profiles.find(p => p.group === group)!)"
        >
          <strong>{{ group }}</strong>
          <small>
            {{ profiles.filter(p => p.group === group).length }} 个型号 ·
            {{ profiles.find(p => p.group === group)?.family }}
          </small>
          <small class="rate-caption">{{ groupRate(group) }} · {{ VIDEO_PRICING.groupRatio }} 倍</small>
        </button>
      </div>
    </div>
    <div class="cloud-field">
      <label>模型模式</label>
      <div class="cloud-capabilities">
        <button
          v-for="profile in variants"
          :key="profile.id"
          type="button"
          :class="{ active: profile.id === draft.model }"
          :disabled="busy"
          @click="selectProfile(profile)"
        >
          {{ profile.mode }}
          <small class="rate-caption">{{ videoRateLabel(profile, draft.resolution) }}</small>
          <small class="option-price">{{ optionFee(profile) }} · {{ VIDEO_PRICING.groupRatio }} 倍</small>
        </button>
      </div>
      <p class="field-help model-id">{{ draft.model }}</p>
    </div>
    <div class="cloud-field">
      <label>视频创作能力</label>
      <div class="cloud-capabilities">
        <button
          v-for="code in selected.capabilities"
          :key="code"
          type="button"
          :class="{ active: draft.capability === code }"
          :disabled="busy"
          @click="selectCapability(code)"
        >
          {{ labels[code] }}
          <small class="rate-caption">
            {{ videoRateLabel(selected, draft.resolution) }} · {{ VIDEO_PRICING.groupRatio }} 倍
          </small>
          <small class="option-price">{{ optionFee(selected, { capability: code }) }}</small>
        </button>
      </div>
    </div>
    <div v-if="draft.capability !== 'T2V'" class="cloud-field">
      <label>参考素材</label>
      <p class="field-help">
        {{
          draft.capability === 'FL2V'
            ? '分别添加首帧和尾帧'
            : draft.capability === 'I2V'
              ? '添加首帧图片'
              : '添加参考图片，以及模型支持的视频／音频素材'
        }}
      </p>
      <p v-if="!status?.referenceDeliveryConfigured" class="field-help">参考素材上传待云端视频服务接入后开放。</p>
      <div v-for="slot in slots" :key="slot.role" class="material-slot">
        <label>
          {{ slot.label }}
          <input
            type="file"
            :accept="slot.accept"
            :multiple="slot.multiple"
            :disabled="busy || uploading || !status?.referenceDeliveryConfigured"
            @change="choose($event, slot.role, slot.multiple)"
          />
        </label>
      </div>
      <div v-for="(item, i) in materials" :key="item.assetId" class="material-preview">
        <span>{{ item.name }} · {{ roleLabels[item.role] }}</span>
        <button type="button" :disabled="busy" @click="remove(i)">移除</button>
      </div>
      <p v-if="uploading" class="field-help">正在上传参考素材…</p>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    </div>
    <div class="cloud-field">
      <label for="video-cloud-prompt">视频描述</label>
      <textarea
        id="video-cloud-prompt"
        v-model="draft.prompt"
        rows="5"
        maxlength="10000"
        :disabled="busy"
        placeholder="描述主体、场景、镜头运动、风格和对白…"
      />
    </div>
    <div class="cloud-field">
      <label>输出设置</label>
      <div class="parameter-grid">
        <label>
          视频时长
          <select v-model.number="draft.seconds" :disabled="busy">
            <option v-for="n in selected.durations" :key="n" :value="n">
              {{ n }} 秒 · {{ optionFee(selected, { seconds: n }) }}
            </option>
          </select>
        </label>
        <label>
          分辨率
          <select v-model="draft.resolution" :disabled="busy">
            <option v-for="n in selected.resolutions" :key="n" :value="n">
              {{ n.toUpperCase() }} · {{ optionFee(selected, { resolution: n }) }}
            </option>
          </select>
        </label>
        <label>
          画面比例
          <select v-model="draft.ratio" :disabled="busy">
            <option v-for="n in availableRatios" :key="n" :value="n">{{ n === 'adaptive' ? '自适应' : n }}</option>
          </select>
        </label>
        <label>
          输出格式
          <select disabled>
            <option>MP4</option>
          </select>
        </label>
      </div>
    </div>
    <details class="cloud-advanced">
      <summary>高级设置</summary>
      <label v-if="selected.hasAudioOutput" class="check-field">
        <input v-model="draft.generateAudio" type="checkbox" :disabled="busy" />
        生成音频
      </label>
      <div v-if="selected.hasSeed" class="cloud-field">
        <label>随机种子（可选）</label>
        <input v-model.number="draft.seed" type="number" min="0" max="2147483647" :disabled="busy" />
      </div>
      <div v-if="selected.family === 'Wan'" class="cloud-field">
        <label>反向描述</label>
        <textarea v-model="draft.negativePrompt" maxlength="500" :disabled="busy" rows="2" />
      </div>
    </details>
    <section class="video-fee-panel" aria-label="云端视频计费信息" aria-live="polite">
      <div class="fee-heading">
        <strong>计费信息</strong>
        <span>{{ labels[draft.capability] }} · {{ draft.resolution.toUpperCase() }} · {{ draft.seconds }} 秒</span>
      </div>
      <div v-if="costQuote" class="fee-metrics">
        <div class="fee-metric">
          <span>消耗单价</span>
          <strong>{{ formatCnyFromUsd(costQuote.unitPrice) }}</strong>
          <small>{{ costQuote.unitLabel }}</small>
        </div>
        <div class="fee-metric">
          <span>计费倍率</span>
          <strong>
            {{ costQuote.multiplier }}
            <em>倍</em>
          </strong>
          <small>媒体分组公开倍率</small>
        </div>
        <div class="fee-metric fee-total">
          <span>{{ costQuote.referenceVideoExtra ? '预计基础费用 · 参考视频另计' : '当前视频预计费用' }}</span>
          <strong>
            {{ formatCnyFromUsd(costQuote.estimatedUsd) }}
            <em>人民币</em>
          </strong>
        </div>
      </div>
      <p v-if="costQuote" class="fee-formula">{{ costQuote.formula }}</p>
      <p class="fee-note">
        当前型号：{{ draft.model }} · {{ selected.mode }}。公开计费表达式未单列创作能力或音频加价，按该型号的秒数／Token
        规则计算。
      </p>
      <details class="fee-combinations">
        <summary>查看时长 × 分辨率组合费用（{{ selected.durations.length * selected.resolutions.length }} 组）</summary>
        <p class="fee-note">
          {{ labels[draft.capability] }} · 公开倍率 {{ VIDEO_PRICING.groupRatio }} 倍 · 人民币。点击报价可选择该组合。
        </p>
        <div class="fee-table-scroll" tabindex="0" aria-label="时长与分辨率组合报价表">
          <table class="fee-table">
            <caption class="sr-only">{{ draft.model }} {{ labels[draft.capability] }}组合预计费用，人民币</caption>
            <thead>
              <tr>
                <th scope="col">时长</th>
                <th v-for="resolution in selected.resolutions" :key="resolution" scope="col">
                  {{ resolution.toUpperCase() }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr class="fee-unit-row">
                <th scope="row">消耗单价</th>
                <td v-for="resolution in selected.resolutions" :key="resolution">
                  {{ videoRateLabel(selected, resolution) }}
                </td>
              </tr>
              <tr v-for="row in priceGrid" :key="row.seconds">
                <th scope="row">{{ row.seconds }} 秒</th>
                <td v-for="cell in row.cells" :key="cell.resolution">
                  <button
                    type="button"
                    :disabled="busy || !cell.quote"
                    :class="{ active: draft.seconds === row.seconds && draft.resolution === cell.resolution }"
                    :aria-pressed="draft.seconds === row.seconds && draft.resolution === cell.resolution"
                    :aria-label="`${row.seconds} 秒 ${cell.resolution} ${quoteFee(cell.quote)}`"
                    @click="selectPriceCombination(row.seconds, cell.resolution)"
                  >
                    {{ quoteFee(cell.quote) }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
      <p v-if="costQuote?.approximate" class="fee-note">按所选清晰度的公开 16:9 样例估算，实际按计费 Token 结算。</p>
      <p v-if="costQuote?.referenceVideoExtra" class="fee-note">以上不含参考视频产生的额外 Token 费用。</p>
      <p v-if="costQuote?.channelPriceMayVary" class="fee-note">该型号存在不同通道价格，当前显示公开模型报价。</p>
      <p v-if="!costQuote" class="fee-note">当前参数没有可用的公开报价。</p>
      <div class="fee-source">
        <a :href="VIDEO_PRICING.source" target="_blank" rel="noopener noreferrer">蓝章鱼公开价格 ↗</a>
        <time>{{ pricingDate }} 核对</time>
      </div>
      <p class="fee-note">
        人民币按蓝章鱼公开汇率快照换算：1 美元 = {{ VIDEO_PRICING.displayCurrency.usdToCny }} 元 （{{
          exchangeRateDate
        }}
        更新）。
      </p>
      <p class="fee-note fee-settlement">预计费用以公开媒体分组倍率计算，实际以账号通道结算为准。</p>
    </section>
    <div v-if="status?.validationVariants?.length" class="cloud-field">
      <label>本批次真实验收 · 剩余 {{ status.validationRemaining }} 次</label>
      <div class="cloud-capabilities">
        <button
          v-for="variant in status.validationVariants"
          :key="variant"
          type="button"
          :disabled="busy"
          @click="selectValidation(variant)"
        >
          {{ variant.split('|')[0] }} · {{ labels[variant.split('|')[1]] }}
          <small>{{ variant.split('|')[2] }} 秒 · {{ variant.split('|')[3] }} · {{ variant.split('|')[4] }}</small>
        </button>
      </div>
    </div>
    <div class="cloud-summary">
      <span>
        {{ selected.family }} · {{ selected.mode }} · {{ draft.resolution.toUpperCase() }} · {{ draft.seconds }} 秒
      </span>
      <p>
        {{ status?.configured ? '服务端已配置蓝章鱼' : '服务端尚未启用云端视频' }} ·
        {{
          videoCloudAccess(draft, status) === 'verified'
            ? '当前参数组合已完成真实验收'
            : videoCloudAccess(draft, status) === 'validation'
              ? '本批次一次性验收，可真实提交'
              : '当前参数组合待成片验收'
        }}
      </p>
      <p v-if="selected.protocol === 'unconfirmed'">该型号的参考视频协议尚未获得供应商接口依据。</p>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import {
  VIDEO_CLOUD_PROFILES,
  videoCloudAccess,
  normalizeVideoCloudDraft,
  uploadVideoCloudAsset,
  type VideoCloudDraft,
  type VideoCloudProfile,
  type VideoCloudStatus,
  type VideoCloudReference
} from '@/api/video/cloud';
import {
  calculateVideoCost,
  formatCnyFromUsd,
  videoRateLabel,
  videoStartingRate,
  videoOptionQuote,
  videoPriceGrid,
  type VideoCostQuote,
  VIDEO_PRICING
} from '@/api/video/cloud-pricing';
function selectValidation(variant: string) {
  const [model, capability, seconds, resolution, ratio, audio] = variant.split('|');
  const profile = profiles.find(p => p.id === model);
  if (!profile) return;
  selectProfile(profile);
  Object.assign(draft, {
    model,
    capability,
    seconds: Number(seconds),
    resolution,
    ratio,
    generateAudio: profile.hasAudioOutput ? audio === 'true' : undefined,
    seed: undefined,
    negativePrompt: undefined
  });
}
const props = defineProps<{ busy?: boolean; status?: VideoCloudStatus | null }>();
const emit = defineEmits<{ change: [draft: VideoCloudDraft]; uploading: [value: boolean] }>();
const profiles = VIDEO_CLOUD_PROFILES;
const labels: Record<string, string> = { T2V: '文生视频', I2V: '图生视频', FL2V: '首尾帧视频', R2V: '多素材参考视频' };
const roleLabels: Record<string, string> = {
  first_frame: '首帧',
  last_frame: '尾帧',
  reference_image: '参考图片',
  reference_video: '参考视频',
  reference_audio: '参考音频'
};
const first = profiles[0];
const draft = reactive<VideoCloudDraft>({
  model: first.id,
  capability: first.capabilities[0],
  prompt: '',
  seconds: first.durations[0],
  resolution: first.resolutions[0],
  ratio: first.ratios[0],
  references: [],
  generateAudio: first.hasAudioOutput ? true : undefined,
  negativePrompt: ''
});
const selected = computed(() => profiles.find(p => p.id === draft.model)!);
const availableRatios = computed(() =>
  selected.value.ratios.filter(
    r => !(selected.value.family === 'MiniMax' && draft.capability === 'T2V' && r === 'adaptive')
  )
);
const groups = [...new Set(profiles.map(p => p.group))];
const variants = computed(() => profiles.filter(p => p.group === selected.value.group));
const costQuote = computed(() => calculateVideoCost(draft));
const priceGrid = computed(() => videoPriceGrid(selected.value, draft));
function quoteFee(quote: VideoCostQuote | undefined) {
  if (!quote) return '暂无报价';
  return `${quote.approximate ? '约 ' : ''}${formatCnyFromUsd(quote.estimatedUsd)}${quote.referenceVideoExtra ? ' + 参考视频费' : ''}`;
}
function optionFee(
  profile: VideoCloudProfile,
  option: Partial<Pick<VideoCloudDraft, 'capability' | 'resolution' | 'seconds'>> = {}
) {
  return `预计 ${quoteFee(videoOptionQuote(profile, draft, option))}`;
}
function selectPriceCombination(seconds: number, resolution: string) {
  if (props.busy || !videoOptionQuote(selected.value, draft, { seconds, resolution })) return;
  draft.seconds = seconds;
  draft.resolution = resolution;
}
const exchangeRateDate = VIDEO_PRICING.displayCurrency.updatedAt.slice(0, 16).replace('T', ' ');
const pricingDate = VIDEO_PRICING.checkedAt.slice(0, 16).replace('T', ' ');
function groupRate(group: string) {
  return videoStartingRate(profiles.filter(p => p.group === group));
}
const materials = ref<(VideoCloudReference & { name: string })[]>([]);
const uploading = ref(false);
const error = ref('');
const slots = computed(() => {
  const image = (role: VideoCloudReference['role'], label: string, multiple = false) => ({
    role,
    label,
    accept: 'image/png,image/jpeg,image/webp',
    multiple
  });
  if (draft.capability === 'I2V') return [image('first_frame', '首帧图片')];
  if (draft.capability === 'FL2V') return [image('first_frame', '首帧图片'), image('last_frame', '尾帧图片')];
  if (draft.capability !== 'R2V') return [];
  return [
    image('reference_image', `参考图片 · 最多 ${selected.value.maxImages} 张`, true),
    ...(selected.value.hasVideoReference
      ? [{ role: 'reference_video' as const, label: '参考视频', accept: 'video/mp4', multiple: true }]
      : []),
    ...(selected.value.hasAudioReference
      ? [{ role: 'reference_audio' as const, label: '参考音频', accept: 'audio/mpeg,audio/wav', multiple: true }]
      : [])
  ];
});
function resetMaterials() {
  materials.value = [];
  draft.references = [];
  error.value = '';
}
function selectProfile(p: VideoCloudProfile) {
  Object.assign(draft, normalizeVideoCloudDraft(draft, p));
  resetMaterials();
}
function selectCapability(code: string) {
  draft.capability = code;
  Object.assign(draft, normalizeVideoCloudDraft(draft, selected.value));
  resetMaterials();
}
function remove(i: number) {
  materials.value.splice(i, 1);
  draft.references = materials.value.map(({ assetId, role }) => ({ assetId, role }));
}
async function choose(event: Event, role: VideoCloudReference['role'], multiple: boolean) {
  const input = event.target as HTMLInputElement;
  const files = [...(input.files ?? [])];
  input.value = '';
  error.value = '';
  const max = role.includes('frame')
    ? 1
    : role === 'reference_image'
      ? selected.value.maxImages
      : role === 'reference_video'
        ? selected.value.maxVideos
        : selected.value.maxAudios;
  if (files.length + (multiple ? materials.value.filter(m => m.role === role).length : 0) > max) {
    error.value = `该素材最多 ${max} 个`;
    return;
  }
  if (files.some(f => f.size > 64 * 1024 * 1024)) {
    error.value = '单个素材不能超过 64 MB';
    return;
  }
  uploading.value = true;
  emit('uploading', true);
  try {
    if (!multiple) materials.value = materials.value.filter(m => m.role !== role);
    for (const file of files) {
      const res = await uploadVideoCloudAsset(file);
      if (!res.data?.assetId) throw new Error('素材上传未返回 ID');
      materials.value.push({ assetId: res.data.assetId, role, name: file.name });
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '素材上传失败';
  } finally {
    draft.references = materials.value.map(({ assetId, role }) => ({ assetId, role }));
    uploading.value = false;
    emit('uploading', false);
  }
}
function applyDraft(input: VideoCloudDraft) {
  const p = profiles.find(p => p.id === input.model);
  if (!p) return;
  Object.assign(draft, normalizeVideoCloudDraft({ ...input, idempotencyKey: undefined }, p));
  materials.value = input.references.map(r => ({ ...r, name: `已上传素材 ${r.assetId}` }));
  error.value = '';
}
defineExpose({ applyDraft });
watch(
  draft,
  () =>
    emit('change', {
      ...draft,
      seed: typeof draft.seed === 'number' && Number.isFinite(draft.seed) ? draft.seed : undefined,
      references: draft.references.map(r => ({ ...r }))
    }),
  { deep: true, immediate: true }
);
</script>
<style scoped>
.cloud-editor {
  color: var(--t1);
  min-width: 0;
}
.cloud-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cloud-heading span,
.field-help,
.cloud-summary p {
  font-size: 11px;
  color: var(--t2);
  line-height: 1.7;
}
.cloud-heading h2 {
  font-size: 22px;
  margin: 6px 0;
}
.cloud-heading b {
  color: var(--p);
  background: var(--tint);
  padding: 5px 9px;
  border-radius: 6px;
  font-size: 11px;
}
.cloud-field {
  margin: 20px 0;
}
.cloud-field > label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 9px;
}
.cloud-models,
.cloud-capabilities,
.parameter-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}
.cloud-models button,
.cloud-capabilities button {
  border: 1px solid var(--line2);
  border-radius: 9px;
  background: var(--surface);
  color: var(--t1);
  padding: 12px;
  text-align: left;
  cursor: pointer;
}
.cloud-models .active,
.cloud-capabilities .active {
  border-color: var(--p);
  background: var(--tint);
  color: var(--p);
}
.cloud-models small {
  display: block;
  margin-top: 7px;
  color: var(--t2);
  font-size: 11px;
}
.cloud-editor :is(select, textarea) {
  box-sizing: border-box;
  width: 100%;
  border: 1px solid var(--line2);
  background: var(--sunken);
  color: var(--t1);
  padding: 9px;
  border-radius: 7px;
  font: inherit;
  font-size: 12px;
  margin-top: 7px;
}
.cloud-editor textarea {
  resize: vertical;
  line-height: 1.8;
}
.parameter-grid label {
  font-size: 12px;
  color: var(--t2);
}
.cloud-advanced {
  border-top: 1px solid var(--line);
  padding-top: 14px;
  font-size: 12px;
}
.check-field {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 14px 0;
}
.cloud-summary {
  background: var(--sunken);
  padding: 12px;
  border-radius: 8px;
  margin-top: 20px;
  font-size: 12px;
}
.material-slot {
  padding: 10px;
  border: 1px dashed var(--line2);
  border-radius: 8px;
  margin: 8px 0;
  font-size: 12px;
}
.material-slot input {
  display: block;
  margin-top: 8px;
  max-width: 100%;
}
.material-preview {
  display: flex;
  gap: 8px;
  margin: 8px 0;
  font-size: 11px;
  overflow-wrap: anywhere;
}
.material-preview button {
  color: var(--p);
  border: 0;
  background: none;
  cursor: pointer;
}
.field-error {
  color: #b94358;
  font-size: 12px;
}
.model-id {
  overflow-wrap: anywhere;
}
.cloud-editor :disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.cloud-editor :focus-visible {
  outline: 2px solid var(--p);
  outline-offset: 2px;
}

.rate-caption {
  display: block;
  margin-top: 7px;
  color: var(--t2);
  font-size: 10px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.video-fee-panel {
  margin-top: 20px;
  padding: 15px;
  border: 1px solid var(--line2);
  border-radius: 10px;
  background: var(--sunken);
}
.fee-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  margin-bottom: 12px;
}
.fee-heading > span {
  font-size: 11px;
  color: var(--t2);
}
.fee-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.fee-metric {
  padding: 12px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  min-width: 0;
}
.fee-metric > span,
.fee-metric > small {
  display: block;
  font-size: 11px;
  color: var(--t2);
  line-height: 1.6;
}
.fee-metric > strong {
  display: block;
  font-size: 21px;
  line-height: 1.6;
  color: var(--t1);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.fee-metric em {
  font-size: 11px;
  font-weight: 400;
  font-style: normal;
  color: var(--t2);
  margin-left: 6px;
}
.fee-total {
  grid-column: 1/-1;
  border-color: var(--p);
  background: var(--tint);
}
.fee-total > strong {
  color: var(--p);
  font-size: 25px;
}
.fee-formula {
  font-size: 11px;
  color: var(--t1);
  line-height: 1.7;
  margin: 12px 0 6px;
  overflow-wrap: anywhere;
}
.fee-note {
  font-size: 10px;
  color: var(--t2);
  line-height: 1.7;
  margin: 5px 0;
}
.fee-source {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 7px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  font-size: 10px;
  color: var(--t2);
}
.fee-source a {
  color: var(--p);
  text-decoration: none;
}
.fee-source a:hover {
  text-decoration: underline;
}
.fee-settlement {
  margin-bottom: 0;
}

.option-price {
  display: block;
  margin-top: 4px;
  color: var(--p);
  font-size: 11px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.fee-combinations {
  margin: 12px 0;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}
.fee-combinations > summary {
  font-size: 12px;
  color: var(--p);
  cursor: pointer;
  line-height: 1.7;
}
.fee-table-scroll {
  max-height: 285px;
  overflow: auto;
  margin-top: 8px;
  border: 1px solid var(--line);
  border-radius: 8px;
}
.fee-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 11px;
  background: var(--surface);
}
.fee-table th,
.fee-table td {
  padding: 7px;
  border-bottom: 1px solid var(--line);
  text-align: center;
  min-width: 70px;
}
.fee-table thead th {
  position: sticky;
  top: 0;
  background: var(--sunken);
  z-index: 1;
}
.fee-table tbody th {
  font-weight: 500;
  color: var(--t2);
  white-space: nowrap;
}
.fee-unit-row td {
  font-size: 10px;
  color: var(--t2);
  line-height: 1.6;
}
.fee-table button {
  width: 100%;
  padding: 6px 4px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 5px;
  color: var(--t1);
  font: inherit;
  cursor: pointer;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.fee-table button:hover,
.fee-table button.active {
  border-color: var(--p);
  background: var(--tint);
  color: var(--p);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
