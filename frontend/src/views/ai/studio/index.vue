<template>
  <div class="studio">
    <div v-if="showGuide" class="guide-bar">
      <el-icon><MagicStick /></el-icon>
      <span>三步出片：① 选模块与模型 → ② 上传素材、写描述 → ③ 提交后即可离开，完成时通知你</span>
      <button type="button" title="关闭引导" aria-label="关闭引导" @click="showGuide = false">
        <el-icon><Close /></el-icon>
      </button>
    </div>

    <div class="workbench-grid">
      <section class="studio-card create-card">
        <div class="section-heading">
          <div>
            <span>创建任务</span>
            <h2>{{ currentModule.name }}</h2>
          </div>
          <span class="version-pill">{{ versionPill }}</span>
        </div>

        <div class="capability-grid" aria-label="视频能力">
          <button
            v-for="item in VIDEO_MODULES"
            :key="item.code"
            type="button"
            :class="['capability', { active: item.code === currentModule.code }]"
            :aria-pressed="item.code === currentModule.code"
            @click="selectModule(item)"
          >
            <el-icon><component :is="moduleIcons[item.code]" /></el-icon>
            <strong>{{ item.name }}</strong>
            <small>{{ item.desc }}</small>
          </button>
        </div>

        <div class="form-divider" />

        <div v-if="currentModule.models.length" class="field-block">
          <label>
            生成模型
            <em>*</em>
          </label>
          <p v-if="closedModels.length" class="model-group-label">
            <el-icon><Lock /></el-icon>
            闭源商用 · 按次消耗积分
          </p>
          <div v-if="closedModels.length" class="model-grid">
            <button
              v-for="item in closedModels"
              :key="item.code"
              type="button"
              :class="['model-option', { active: item.code === currentModel.code }]"
              @click="currentModel = item"
            >
              <span>
                <b>{{ item.name }}</b>
                <i v-if="item.recommended">推荐</i>
              </span>
              <small>{{ item.desc }} · {{ MODEL_COSTS[item.code] }} 积分/次</small>
            </button>
          </div>
          <p v-if="openModels.length" class="model-group-label">
            <el-icon><Cpu /></el-icon>
            开源模型 · 本地 GPU · 显示时间进展
          </p>
          <div v-if="openModels.length" class="model-grid">
            <button
              v-for="item in openModels"
              :key="item.code"
              type="button"
              :class="['model-option', { active: item.code === currentModel.code }]"
              @click="currentModel = item"
            >
              <span>
                <b>{{ item.name }}</b>
              </span>
              <small>{{ item.desc }} · {{ MODEL_ETAS[item.code] }}</small>
            </button>
          </div>
        </div>
        <div v-else class="field-block">
          <label>
            处理工作流
            <em>*</em>
          </label>
          <div class="fixed-workflow">
            <span>
              <b>{{ currentModule.fixedWorkflow!.name }}</b>
              <i>固定工作流</i>
            </span>
            <small>
              {{ currentModule.fixedWorkflow!.version }} · 本地 GPU · {{ currentModule.fixedWorkflow!.eta }}
            </small>
          </div>
        </div>

        <template v-for="field in currentModule.fields" :key="field">
          <div v-if="isUploadField(field)" class="field-block">
            <label>
              {{ fieldLabels[field] }}
              <em v-if="isRequired(field)">*</em>
            </label>
            <label class="upload-zone" :class="{ complete: uploads[field]?.length }">
              <input
                type="file"
                :accept="field === 'audio' ? 'audio/*' : 'image/*'"
                :multiple="field === 'frames'"
                @change="handleFiles(field, $event)"
              />
              <el-icon>
                <Check v-if="uploads[field]?.length" />
                <UploadFilled v-else />
              </el-icon>
              <b>{{ uploadSummary(field) }}</b>
              <small>
                {{
                  field === 'frames'
                    ? '支持 2-10 张关键帧'
                    : field === 'audio'
                      ? '支持 MP3、WAV、M4A'
                      : '支持 JPG、PNG、WEBP'
                }}
              </small>
            </label>
          </div>

          <div v-else-if="field === 'source'" class="field-block">
            <label>
              源视频
              <em>*</em>
            </label>
            <el-select v-model="values.source" placeholder="请选择已完成的视频" size="large">
              <el-option v-for="item in COMPLETED_VIDEOS" :key="item" :label="item" :value="item" />
            </el-select>
          </div>

          <div v-else-if="field === 'desc'" class="field-block">
            <label>
              {{ currentModule.promptLabel }}
              <em v-if="isRequired(field)">*</em>
            </label>
            <el-input
              v-model="values.desc"
              type="textarea"
              :rows="4"
              maxlength="200"
              show-word-limit
              :placeholder="currentModule.placeholder"
            />
            <div class="prompt-tools">
              <div>
                <button v-for="chip in PROMPT_CHIPS" :key="chip" type="button" @click="appendPrompt(chip)">
                  {{ chip }}
                </button>
              </div>
              <button class="optimize" type="button" @click="optimizePrompt">
                <el-icon><MagicStick /></el-icon>
                优化描述
              </button>
            </div>
          </div>

          <div v-else-if="field === 'tier' || field === 'dur'" class="field-block">
            <label>
              {{ fieldLabels[field] }}
              <em>*</em>
            </label>
            <div class="choice-grid">
              <button
                v-for="item in optionsFor(field)"
                :key="item"
                type="button"
                :class="{ active: values[field] === item }"
                @click="selectChoice(field, item)"
              >
                {{ item }}
              </button>
            </div>
          </div>

          <div v-else class="field-block">
            <label>
              {{ fieldLabels[field] }}
              <em>*</em>
            </label>
            <el-select v-model="values[field]" placeholder="请选择" size="large">
              <el-option v-for="item in fieldOptions[field]" :key="item" :label="item" :value="item" />
            </el-select>
          </div>
        </template>

        <div class="submit-row">
          <button
            v-hasPermi="['ai:studio:submit']"
            type="button"
            class="submit-button"
            :disabled="submitting"
            @click="submitTask"
          >
            <el-icon>
              <Loading v-if="submitting" class="is-loading" />
              <MagicStick v-else />
            </el-icon>
            {{ submitting ? '正在提交' : submitButtonText }}
          </button>
          <span>当前排队 {{ queueCount }} 个任务</span>
        </div>
      </section>

      <aside class="right-column">
        <section class="latest-player">
          <div class="player-badges">
            <span>最新成片</span>
            <span>1080P</span>
            <span>00:05</span>
          </div>
          <button
            type="button"
            class="play-button"
            title="播放最新成片"
            aria-label="播放最新成片"
            @click="ElMessage.info('成片预览准备中')"
          >
            <el-icon><VideoPlay /></el-icon>
          </button>
          <div class="player-footer">
            <div>
              <b>新品发布主视频</b>
              <small>VIDEO-20260911-017 · 5 分钟前</small>
            </div>
            <div>
              <button type="button" title="下载" aria-label="下载" @click="ElMessage.success('开始下载成片')">
                <el-icon><Download /></el-icon>
              </button>
              <button type="button" title="分享" aria-label="分享" @click="ElMessage.success('分享链接已复制')">
                <el-icon><Share /></el-icon>
              </button>
              <button type="button" class="recreate" @click="useInspiration(INSPIRATIONS[1])">
                <el-icon><RefreshRight /></el-icon>
                再创作
              </button>
            </div>
          </div>
        </section>

        <section class="stats" aria-label="视频创作统计">
          <div>
            <b>18</b>
            <span>本周成片</span>
          </div>
          <div>
            <b>{{ queueCount }}</b>
            <span>排队中</span>
          </div>
          <div>
            <b>6</b>
            <span>素材库</span>
          </div>
          <div>
            <b>98%</b>
            <span>生成成功率</span>
          </div>
        </section>

        <section class="studio-card inspiration-card">
          <div class="section-heading compact">
            <div>
              <h2>灵感 · 一键同款</h2>
              <span>自动带入能力、模型与描述</span>
            </div>
          </div>
          <div class="inspiration-list">
            <article v-for="item in INSPIRATIONS" :key="item.title">
              <div :class="['inspiration-poster', item.tone]">
                <el-icon><VideoCameraFilled /></el-icon>
                <span>{{ moduleName(item.module) }} · {{ modelName(item.model) }}</span>
              </div>
              <div>
                <b>{{ item.title }}</b>
                <p>{{ item.prompt }}</p>
              </div>
              <button type="button" @click="useInspiration(item)">
                用同款
                <el-icon><ArrowRight /></el-icon>
              </button>
            </article>
          </div>
        </section>

        <section class="studio-card recent-card">
          <div class="section-heading compact">
            <div>
              <h2>最近任务</h2>
              <span>当前创作队列</span>
            </div>
          </div>
          <div v-for="task in recentTasks" :key="task.id" class="recent-task">
            <span :class="task.status"><i /></span>
            <div>
              <b>{{ task.name }}</b>
              <small>{{ task.module }} · {{ task.model }}</small>
            </div>
            <em>{{ task.status === 'running' ? '生成中' : task.status === 'queued' ? '排队中' : '已完成' }}</em>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue';
import {
  ArrowRight,
  Check,
  Close,
  Cpu,
  Download,
  Loading,
  Lock,
  MagicStick,
  Picture,
  RefreshRight,
  Share,
  UploadFilled,
  VideoCamera,
  VideoPlay
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import {
  COMPLETED_VIDEOS,
  INSPIRATIONS,
  MODEL_COSTS,
  MODEL_ETAS,
  PROMPT_CHIPS,
  VIDEO_MODELS,
  VIDEO_MODULES,
  resolveWorkflowCode,
  type FieldKey,
  type Inspiration,
  type StudioModule
} from './modules';

const currentModule = ref(VIDEO_MODULES[0]!);
const currentModel = ref(VIDEO_MODELS.find(item => item.code === VIDEO_MODULES[0]!.defaultModel) ?? VIDEO_MODELS[0]!);
const values = reactive<Partial<Record<FieldKey, string>>>({ tier: '高清 · 1080P', dur: '5 秒' });
const uploads = reactive<Partial<Record<FieldKey, string[]>>>({});
const showGuide = ref(true);
const submitting = ref(false);
const queueCount = ref(1);
const submitTimer = ref<number>();
const recentTasks = ref([
  { id: '018', name: '春季宣传片 · 包装特写', module: '首尾帧生视频', model: 'MiniMax H3', status: 'running' },
  { id: '017', name: '新品发布主视频', module: '首尾帧生视频', model: 'H3 Pro', status: 'done' },
  { id: '016', name: '品牌 LOGO 动效', module: '文生视频', model: 'WAN 2.1', status: 'done' }
]);

const moduleIcons: Record<string, Component> = {
  I2V: VideoCamera,
  T2V: MagicStick,
  F2V: Picture
};
const fieldLabels: Record<FieldKey, string> = {
  first: '首帧图片',
  last: '尾帧图片',
  frames: '关键帧',
  img: '原图',
  source: '源视频',
  audio: '音频文件',
  desc: '视频描述',
  tier: '输出档位',
  dur: '视频时长',
  move: '运镜方式',
  extend: '续写时长',
  target: '目标画质',
  fps: '帧率'
};
const fieldOptions: Partial<Record<FieldKey, string[]>> = {
  tier: ['高清 · 1080P', '流畅 · 720P', '标清 · 480P'],
  dur: ['5 秒', '10 秒', '20 秒'],
  move: ['推近', '拉远', '左摇', '右摇', '环绕', '旋转'],
  extend: ['5 秒', '10 秒', '20 秒'],
  target: ['1080P', '4K'],
  fps: ['24 FPS', '30 FPS', '60 FPS']
};
const uploadFields: FieldKey[] = ['first', 'last', 'frames', 'img', 'audio'];
const requiredFields: FieldKey[] = [
  'first',
  'img',
  'frames',
  'source',
  'audio',
  'tier',
  'dur',
  'move',
  'extend',
  'target',
  'fps'
];

const closedModels = computed(() =>
  VIDEO_MODELS.filter(item => currentModule.value.models.includes(item.code) && item.license === 'closed')
);
const openModels = computed(() =>
  VIDEO_MODELS.filter(item => currentModule.value.models.includes(item.code) && item.license === 'open')
);
const versionPill = computed(() =>
  currentModule.value.models.length
    ? `${currentModel.value.name} · ${currentModel.value.version}`
    : `${currentModule.value.fixedWorkflow!.name} · ${currentModule.value.fixedWorkflow!.version}`
);
const submitButtonText = computed(() => {
  if (!currentModule.value.models.length) {
    return `提交生成 · ${currentModule.value.fixedWorkflow!.eta}`;
  }
  return currentModel.value.license === 'closed'
    ? `提交生成 · 消耗 ${MODEL_COSTS[currentModel.value.code]} 积分`
    : `提交生成 · ${MODEL_ETAS[currentModel.value.code]}`;
});

function selectModule(item: StudioModule) {
  currentModule.value = item;
  currentModel.value =
    VIDEO_MODELS.find(candidate => candidate.code === item.defaultModel) ??
    VIDEO_MODELS.find(candidate => item.models.includes(candidate.code)) ??
    VIDEO_MODELS[0]!;
  Object.keys(values).forEach(key => delete values[key as FieldKey]);
  Object.keys(uploads).forEach(key => delete uploads[key as FieldKey]);
  values.tier = '高清 · 1080P';
  values.dur = '5 秒';
}

function isUploadField(field: FieldKey) {
  return uploadFields.includes(field);
}

function isRequired(field: FieldKey) {
  if (field === 'desc') return ['I2V', 'T2V', 'F2V'].includes(currentModule.value.code);
  if (field === 'last') return currentModule.value.code === 'F2V';
  return requiredFields.includes(field);
}

function optionsFor(field: FieldKey) {
  if (field !== 'dur') return fieldOptions[field] ?? [];
  switch (values.tier) {
    case '标清 · 480P':
      return ['5 秒', '10 秒', '20 秒'];
    case '流畅 · 720P':
      return ['5 秒', '10 秒'];
    default:
      return ['5 秒'];
  }
}

function selectChoice(field: FieldKey, value: string) {
  values[field] = value;
  if (field === 'tier' && !optionsFor('dur').includes(values.dur ?? '')) {
    values.dur = optionsFor('dur')[0];
  }
}

function handleFiles(field: FieldKey, event: Event) {
  const input = event.target as HTMLInputElement;
  const names = Array.from(input.files ?? [])
    .slice(0, field === 'frames' ? 10 : 1)
    .map(file => file.name);
  uploads[field] = names;
}

function uploadSummary(field: FieldKey) {
  const files = uploads[field] ?? [];
  if (!files.length) return field === 'frames' ? '选择关键帧' : '点击上传素材';
  return field === 'frames' ? `已选择 ${files.length} 张关键帧` : files[0];
}

function appendPrompt(text: string) {
  values.desc = values.desc ? `${values.desc}，${text}` : text;
}

function optimizePrompt() {
  if (!values.desc?.trim()) {
    ElMessage.warning('请先填写视频描述');
    return;
  }
  values.desc = `${values.desc.replace(/[，。]+$/, '')}，主体清晰，运镜平滑，光影层次自然，电影级质感。`;
  ElMessage.success('描述已优化');
}

function validate() {
  for (const field of currentModule.value.fields) {
    if (!isRequired(field)) continue;
    if (isUploadField(field)) {
      const count = uploads[field]?.length ?? 0;
      if (!count || (field === 'frames' && count < 2)) return `请完成${fieldLabels[field]}上传`;
    } else if (!values[field]?.trim()) {
      return `请填写或选择${fieldLabels[field]}`;
    }
  }
  return '';
}

function submitTask() {
  const error = validate();
  if (error) {
    ElMessage.error(error);
    return;
  }
  // 契约 payload：后端按 capabilityCode+workflowCode 深拷贝工作流模板，
  // 仅覆写 mapping_json 白名单内的节点输入键（上传文件在真实对接时替换为 fileIds）
  const payload = {
    capabilityCode: currentModule.value.workflowCapabilityCode ?? currentModule.value.code,
    workflowCode: resolveWorkflowCode(currentModule.value, currentModel.value.code),
    modelCode: currentModule.value.models.length ? currentModel.value.code : undefined,
    fields: {
      ...values,
      ...Object.fromEntries(Object.entries(uploads).filter(([, files]) => files?.length))
    }
  };
  console.debug('[ai-studio] submit payload', payload);
  submitting.value = true;
  window.clearTimeout(submitTimer.value);
  submitTimer.value = window.setTimeout(() => {
    submitting.value = false;
    queueCount.value += 1;
    recentTasks.value.unshift({
      id: String(Date.now()),
      name: values.desc?.slice(0, 18) || currentModule.value.name,
      module: currentModule.value.name,
      model: currentModel.value.name,
      status: 'queued'
    });
    recentTasks.value = recentTasks.value.slice(0, 4);
    ElMessage.success('任务已提交，完成后将通知你');
  }, 700);
}

function useInspiration(item: Inspiration) {
  const module = VIDEO_MODULES.find(candidate => candidate.code === item.module);
  const model = VIDEO_MODELS.find(candidate => candidate.code === item.model);
  if (module) selectModule(module);
  if (module && model && module.models.includes(item.model)) currentModel.value = model;
  values.desc = item.prompt;
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function moduleName(code: string) {
  return VIDEO_MODULES.find(item => item.code === code)?.name ?? code;
}

function modelName(code: string) {
  return VIDEO_MODELS.find(item => item.code === code)?.name ?? code;
}

onBeforeUnmount(() => window.clearTimeout(submitTimer.value));
</script>

<style scoped lang="scss">
@use '@/assets/styles/tokens-studio.scss';

.studio {
  min-height: calc(100vh - 135px);
  padding: 24px;
  overflow: hidden;
  color: var(--t1);
  background: var(--bg);
  background-image: radial-gradient(900px 460px at 84% -10%, rgba(139, 92, 246, 0.16), transparent 68%);
  border: 1px solid var(--line);
  border-radius: 8px;
}

button {
  font: inherit;
}

.guide-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  margin-bottom: 22px;
  color: #ddd6fe;
  font-size: 13px;
  background: rgba(139, 92, 246, 0.1);
  border: 1px solid rgba(167, 139, 250, 0.24);
  border-radius: 6px;
}
.guide-bar > span {
  flex: 1;
}
.guide-bar button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: var(--t2);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.studio-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  margin-bottom: 22px;
}
.studio-heading p {
  margin: 0 0 8px;
  color: var(--p-h);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
}
.studio-heading h1 {
  margin: 0 0 7px;
  font-size: 30px;
  line-height: 1.15;
  letter-spacing: 0;
}
.studio-heading span {
  color: var(--t2);
  font-size: 13px;
}
.heading-status {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 10px;
  color: var(--t2);
  font-size: 12px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.heading-status i {
  width: 7px;
  height: 7px;
  background: var(--ok);
  border-radius: 50%;
  box-shadow: 0 0 10px var(--ok);
}

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 390px);
  gap: 18px;
  max-width: 1480px;
  margin: 0 auto;
}
.studio-card {
  padding: 20px;
  background: rgba(18, 21, 28, 0.94);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}
.section-heading span {
  color: var(--t3);
  font-size: 11px;
}
.section-heading h2 {
  margin: 3px 0 0;
  font-size: 18px;
  letter-spacing: 0;
}
.section-heading.compact {
  margin-bottom: 14px;
}
.section-heading.compact h2 {
  margin: 0 0 4px;
  font-size: 15px;
}
.version-pill {
  flex: 0 0 auto;
  padding: 6px 9px;
  color: #ddd6fe !important;
  background: var(--tint);
  border: 1px solid rgba(167, 139, 250, 0.2);
  border-radius: 999px;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.capability {
  min-height: 98px;
  padding: 12px;
  color: var(--t2);
  text-align: left;
  cursor: pointer;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 7px;
  transition:
    border-color 0.18s,
    background 0.18s,
    transform 0.18s;
}
.capability:hover {
  border-color: rgba(167, 139, 250, 0.42);
  transform: translateY(-1px);
}
.capability.active {
  background: var(--tint);
  border-color: var(--p);
  box-shadow: inset 0 0 0 1px rgba(139, 92, 246, 0.2);
}
.capability .el-icon {
  display: block;
  margin-bottom: 9px;
  color: var(--p-h);
  font-size: 21px;
}
.capability strong,
.capability small {
  display: block;
}
.capability strong {
  margin-bottom: 5px;
  color: var(--t1);
  font-size: 13px;
}
.capability small {
  min-height: 30px;
  color: var(--t3);
  font-size: 11px;
  line-height: 1.4;
}

.form-divider {
  height: 1px;
  margin: 20px 0;
  background: var(--line);
}
.field-block {
  margin-top: 18px;
}
.field-block > label {
  display: block;
  margin-bottom: 8px;
  color: var(--t2);
  font-size: 12px;
  font-weight: 600;
}
.field-block label em {
  color: var(--danger);
  font-style: normal;
}
.model-group-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 0 8px;
  color: var(--t3);
  font-size: 11px;
}
.model-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.model-option {
  padding: 11px 12px;
  color: var(--t2);
  text-align: left;
  cursor: pointer;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.model-option.active {
  background: var(--tint);
  border-color: var(--p);
}
.model-option span,
.model-option small {
  display: block;
}
.model-option b {
  color: var(--t1);
  font-size: 12px;
}
.model-option i {
  padding: 2px 5px;
  margin-left: 6px;
  color: #fef3c7;
  font-size: 9px;
  font-style: normal;
  background: rgba(245, 158, 11, 0.18);
  border-radius: 3px;
}
.model-option small {
  margin-top: 5px;
  color: var(--t3);
  font-size: 10px;
}
.fixed-workflow {
  padding: 11px 12px;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.fixed-workflow span,
.fixed-workflow small {
  display: flex;
  align-items: center;
  gap: 6px;
}
.fixed-workflow span {
  justify-content: space-between;
}
.fixed-workflow b {
  color: var(--t1);
  font-size: 12px;
}
.fixed-workflow i {
  padding: 2px 5px;
  color: #ddd6fe;
  font-size: 9px;
  font-style: normal;
  background: var(--tint);
  border-radius: 3px;
}
.fixed-workflow small {
  margin-top: 5px;
  color: var(--t3);
  font-size: 10px;
}

.upload-zone {
  display: grid !important;
  min-height: 112px;
  place-items: center;
  align-content: center;
  gap: 5px;
  padding: 16px;
  cursor: pointer;
  background: var(--sunken);
  border: 1px dashed var(--line2);
  border-radius: 7px;
}
.upload-zone:hover {
  border-color: var(--p-h);
}
.upload-zone.complete {
  color: var(--ok);
  border-color: rgba(52, 211, 153, 0.55);
}
.upload-zone input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}
.upload-zone .el-icon {
  font-size: 25px;
}
.upload-zone b {
  max-width: 100%;
  overflow: hidden;
  color: var(--t1);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.upload-zone small {
  color: var(--t3);
  font-size: 10px;
}

.studio :deep(.el-select) {
  width: 100%;
}
.studio :deep(.el-select__wrapper),
.studio :deep(.el-textarea__inner) {
  color: var(--t1);
  background: var(--sunken);
  border-color: var(--line2);
  box-shadow: 0 0 0 1px var(--line2) inset;
}
.studio :deep(.el-select__wrapper:hover),
.studio :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px var(--p-h) inset;
}
.studio :deep(.el-textarea__inner) {
  min-height: 112px;
  padding: 12px;
}
.studio :deep(.el-input__count) {
  color: var(--t3);
  background: transparent;
}
.choice-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.choice-grid button {
  min-height: 42px;
  color: var(--t2);
  cursor: pointer;
  background: var(--sunken);
  border: 1px solid var(--line2);
  border-radius: 6px;
}
.choice-grid button.active {
  color: #fff;
  background: var(--tint);
  border-color: var(--p);
}
.prompt-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
}
.prompt-tools > div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.prompt-tools button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  color: var(--t2);
  font-size: 10px;
  cursor: pointer;
  background: var(--tint);
  border: 0;
  border-radius: 999px;
}
.prompt-tools .optimize {
  flex: 0 0 auto;
  color: #ede9fe;
  border: 1px solid rgba(167, 139, 250, 0.24);
  border-radius: 6px;
}

.submit-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
}
.submit-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-width: 140px;
  min-height: 42px;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  background: var(--grad);
  border: 0;
  border-radius: 6px;
  box-shadow: 0 8px 24px var(--glow);
}
.submit-button:disabled {
  cursor: wait;
  opacity: 0.65;
}
.submit-row > span {
  color: var(--t3);
  font-size: 11px;
}

.right-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.latest-player {
  position: relative;
  min-height: 250px;
  overflow: hidden;
  background: var(--poster);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.latest-player::before {
  position: absolute;
  inset: 0;
  content: '';
  background:
    radial-gradient(circle at 60% 38%, rgba(217, 70, 239, 0.35), transparent 24%),
    linear-gradient(140deg, transparent 15%, rgba(96, 165, 250, 0.2) 48%, transparent 70%);
}
.player-badges {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  gap: 6px;
}
.player-badges span {
  padding: 4px 7px;
  color: #e5e7eb;
  font-size: 9px;
  background: rgba(8, 10, 15, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
}
.player-badges span:first-child {
  color: #ede9fe;
  background: rgba(124, 58, 237, 0.5);
}
.play-button {
  position: absolute;
  top: 42%;
  left: 50%;
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  color: #fff;
  cursor: pointer;
  background: rgba(12, 14, 20, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.26);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  backdrop-filter: blur(8px);
}
.play-button .el-icon {
  font-size: 25px;
}
.player-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 13px;
  background: linear-gradient(transparent, rgba(7, 9, 13, 0.96));
}
.player-footer b,
.player-footer small {
  display: block;
}
.player-footer b {
  font-size: 12px;
}
.player-footer small {
  margin-top: 3px;
  color: var(--t3);
  font-size: 9px;
}
.player-footer > div:last-child {
  display: flex;
  gap: 5px;
}
.player-footer button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 30px;
  height: 30px;
  color: var(--t2);
  cursor: pointer;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--line2);
  border-radius: 5px;
}
.player-footer button.recreate {
  padding: 0 8px;
  color: #fff;
  background: var(--p);
  border-color: var(--p);
  font-size: 10px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.stats div {
  padding: 13px 6px;
  text-align: center;
  border-right: 1px solid var(--line);
}
.stats div:last-child {
  border-right: 0;
}
.stats b,
.stats span {
  display: block;
}
.stats b {
  margin-bottom: 4px;
  color: var(--t1);
  font-size: 17px;
}
.stats span {
  color: var(--t3);
  font-size: 9px;
}

.inspiration-list {
  display: grid;
  gap: 10px;
}
.inspiration-list article {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 7px;
}
.inspiration-poster {
  position: relative;
  display: grid;
  height: 58px;
  place-items: center;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.8);
  background: linear-gradient(135deg, #20103c, #6d28d9);
  border-radius: 5px;
}
.inspiration-poster.cyan {
  background: linear-gradient(135deg, #082f49, #0e7490);
}
.inspiration-poster.rose {
  background: linear-gradient(135deg, #4c0519, #be123c);
}
.inspiration-poster .el-icon {
  font-size: 20px;
}
.inspiration-poster span {
  position: absolute;
  right: 5px;
  bottom: 4px;
  left: 5px;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.72);
  font-size: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.inspiration-list b {
  color: var(--t1);
  font-size: 11px;
}
.inspiration-list p {
  display: -webkit-box;
  margin: 5px 0 0;
  overflow: hidden;
  color: var(--t3);
  font-size: 9px;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.inspiration-list article > button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 5px;
  color: var(--p-h);
  font-size: 10px;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.recent-task {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 9px 0;
  border-top: 1px solid var(--line);
}
.recent-task > span {
  display: grid;
  width: 9px;
  height: 9px;
  place-items: center;
  border: 1px solid var(--line2);
  border-radius: 50%;
}
.recent-task > span i {
  width: 5px;
  height: 5px;
  background: var(--t3);
  border-radius: 50%;
}
.recent-task > span.running i {
  background: var(--p-h);
  animation: pulse 1.5s infinite;
}
.recent-task > span.queued i {
  background: var(--warn);
}
.recent-task b,
.recent-task small {
  display: block;
}
.recent-task b {
  color: var(--t1);
  font-size: 11px;
}
.recent-task small {
  margin-top: 3px;
  color: var(--t3);
  font-size: 9px;
}
.recent-task em {
  color: var(--t3);
  font-size: 9px;
  font-style: normal;
}

@keyframes pulse {
  50% {
    box-shadow: 0 0 0 4px rgba(167, 139, 250, 0.12);
  }
}
@media (max-width: 1240px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }
  .right-column {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  .latest-player,
  .stats {
    grid-column: 1 / -1;
  }
}
@media (max-width: 820px) {
  .studio {
    padding: 16px;
  }
  .capability-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .model-grid {
    grid-template-columns: 1fr;
  }
  .right-column {
    display: flex;
  }
}
@media (max-width: 520px) {
  .studio-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
  .heading-status {
    align-self: stretch;
  }
  .capability-grid {
    grid-template-columns: 1fr;
  }
  .prompt-tools,
  .submit-row {
    align-items: flex-start;
    flex-direction: column;
  }
  .inspiration-list article {
    grid-template-columns: 74px minmax(0, 1fr);
  }
  .inspiration-list article > button {
    grid-column: 2;
    justify-self: start;
  }
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .stats div:nth-child(2) {
    border-right: 0;
  }
  .stats div:nth-child(-n + 2) {
    border-bottom: 1px solid var(--line);
  }
}
</style>
