<template>
  <div class="dna-panel">
    <p v-if="!hasProjects" class="empty">还没有视觉项目。先到「视觉项目」页新建一个。</p>
    <p v-else-if="loading" class="empty">加载中…</p>

  <!-- 尚未生成 -->
  <section v-else-if="!dna" class="panel generate-panel" data-dna-section="EMPTY">
    <h3>这个项目还没有视觉基因</h3>
    <p class="muted">
      生成时会用到：<strong>已确认的产品事实</strong>（颜色、品牌调性、主体版本…）、
      <strong>项目里的参考图</strong>，以及一组<strong>明确标注来源的默认规范</strong>。
    </p>
    <p class="muted">
      若治理台已为视觉分析注册了可用模型，模型结论会用于补全并逐项校验后采纳；
      没有可用模型时，基因来源会如实标为「事实推导」，<strong>不会把默认值包装成 AI 结论</strong>。
    </p>
    <el-button type="primary" :loading="generating" @click="$emit('generate')">生成视觉基因</el-button>
  </section>

  <template v-else>
    <!-- 概览 -->
    <section class="panel" data-dna-section="OVERVIEW">
      <div class="dna-title">
        <h3>
          {{ dna.dnaNo }}
          <span class="version">v{{ dna.version }}</span>
        </h3>
        <div class="dna-tags">
          <el-tag :type="sourceType(dna.source)" effect="dark" size="small">
            {{ sourceLabel(dna.source) }}
          </el-tag>
          <el-tag :type="dna.locked ? 'success' : 'warning'" size="small">
            {{ dna.statusDesc }}
          </el-tag>
          <span class="muted">{{ dna.sourceDesc }}</span>
        </div>
        <div class="dna-actions">
          <el-button size="small" :loading="generating" @click="$emit('generate')">重新生成</el-button>
          <el-button size="small" type="primary" :disabled="dna.locked" :loading="locking" @click="$emit('lock')">
            {{ dna.locked ? '已锁定' : '锁定这一版' }}
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="dna.issues && dna.issues.length"
        type="warning"
        show-icon
        :closable="false"
        class="issues"
        title="这一版还不能锁定，请先补齐："
      >
        <ul class="issue-list">
          <li v-for="(issue, index) in dna.issues" :key="index">{{ issue }}</li>
        </ul>
      </el-alert>
      <el-alert
        v-else
        type="success"
        show-icon
        :closable="false"
        class="issues"
        title="规范自洽，可以锁定"
      />

      <el-alert
        v-if="dna.remark"
        type="info"
        :closable="false"
        class="issues"
        :title="dna.remark"
      />
    </section>

    <!-- 编辑 -->
    <section class="panel" data-dna-section="FORM">
      <div class="block-head">
        <h3>规范内容</h3>
        <div class="head-actions">
          <span class="muted">改动保存即生效；已锁定版本保存会自动新建一版</span>
          <el-button size="small" :loading="recommending" @click="doRecommend">按参考图推荐</el-button>
        </div>
      </div>
      <p class="muted recommend-hint">
        配色、饱和度、对比度、留白、产品占比由参考图<b>实测</b>得出；光线与场景是弱启发（标注 MEDIUM）。
        风格关键词、禁忌词、字体风格像素层面推不出来，<b>不会编</b>——需要人工填或由品牌调性事实带入。
        推荐只填表单，<b>点「保存」才落库</b>。
      </p>

      <div class="form-grid">
        <div class="form-item span2">
          <label>风格关键词</label>
          <el-select
            v-model="form.styleKeywords"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="如：现代简约 / 治愈 / 自然"
            style="width: 100%"
          />
        </div>
        <div class="form-item span2">
          <label>禁忌关键词</label>
          <el-select
            v-model="form.avoidKeywords"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="不该出现在画面里的东西"
            style="width: 100%"
          />
        </div>

        <div class="form-item">
          <label>主色</label>
          <el-color-picker v-model="form.colorPrimary" show-alpha />
        </div>
        <div class="form-item">
          <label>辅色</label>
          <el-color-picker v-model="form.colorSecondary" show-alpha />
        </div>
        <div class="form-item">
          <label>点缀色</label>
          <el-color-picker v-model="form.colorAccent" show-alpha />
        </div>
        <div class="form-item">
          <label>背景色</label>
          <el-color-picker v-model="form.colorBg" show-alpha />
        </div>

        <div class="form-item">
          <label>饱和度</label>
          <el-select v-model="form.saturation" clearable placeholder="未设置">
            <el-option v-for="item in DNA_LEVEL_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="form-item">
          <label>对比度</label>
          <el-select v-model="form.contrastLevel" clearable placeholder="未设置">
            <el-option v-for="item in DNA_LEVEL_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="form-item">
          <label>留白</label>
          <el-select v-model="form.whitespaceLevel" clearable placeholder="未设置">
            <el-option v-for="item in DNA_LEVEL_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="form-item">
          <label>场景类型</label>
          <el-input v-model="form.sceneType" placeholder="如：纯色底 / 生活场景" />
        </div>

        <div class="form-item">
          <label>光线类型</label>
          <el-select v-model="form.lightingType" clearable placeholder="未设置">
            <el-option v-for="item in DNA_LIGHTING_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="form-item">
          <label>光位</label>
          <el-select v-model="form.lightingDir" clearable placeholder="未设置">
            <el-option v-for="item in DNA_LIGHTING_DIRS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="form-item">
          <label>产品占比下限 (%)</label>
          <el-input-number v-model="form.productRatioMin" :min="0" :max="100" controls-position="right" />
        </div>
        <div class="form-item">
          <label>产品占比上限 (%)</label>
          <el-input-number v-model="form.productRatioMax" :min="0" :max="100" controls-position="right" />
        </div>
        <div class="form-item span2">
          <label>字体风格</label>
          <el-input v-model="form.typographyStyle" placeholder="如：无衬线、字号层级分明" />
        </div>
      </div>

      <div class="save-row">
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
        <el-button @click="resetForm">还原为当前版本</el-button>
      </div>
    </section>

    <!-- 派生提示词 -->
    <section class="panel" data-dna-section="PROMPT">
      <div class="block-head">
        <h3>按这版基因派生的出图提示词</h3>
        <el-button size="small" text type="primary" @click="$emit('load-prompt')">重新派生</el-button>
      </div>
      <p class="muted">
        用到的维度：{{ prompt?.applied?.join('、') || '—' }}。
        这是<strong>预填</strong>到出图框的内容，你可以改；改完照原样下发。
      </p>
      <el-input :model-value="promptText" type="textarea" :rows="4" readonly />
      <el-input :model-value="negativeText" type="textarea" :rows="2" readonly class="mt8" />
    </section>

    <!-- 证据链 -->
    <section class="panel" data-dna-section="EVIDENCE">
      <div class="block-head">
        <h3>这一版是怎么来的（证据链）</h3>
        <span class="muted">{{ (dna.evidence || []).length }} 条</span>
      </div>
      <el-table :data="dna.evidence || []" size="small" empty-text="没有证据记录">
        <el-table-column prop="kind" label="类型" width="100" />
        <el-table-column prop="label" label="项目" width="200" />
        <el-table-column prop="value" label="值" min-width="220" show-overflow-tooltip />
        <el-table-column prop="source" label="来源" min-width="240" show-overflow-tooltip />
      </el-table>
    </section>

    <!-- 版本历史 -->
    <section class="panel" data-dna-section="VERSIONS">
      <div class="block-head">
        <h3>版本历史</h3>
        <span class="muted">{{ versions.length }} 版</span>
      </div>
      <el-table :data="versions" size="small">
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="dnaNo" label="编号" width="180" />
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <el-tag :type="sourceType(asDna(row).source)" size="small">{{ sourceLabel(asDna(row).source) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="statusDesc" label="状态" width="100" />
        <el-table-column label="校验" width="90">
          <template #default="{ row }">
            <span :class="(asDna(row).issues || []).length ? 'bad' : 'good'">
              {{ (asDna(row).issues || []).length ? '待补齐' : 'OK' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column prop="remark" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="$emit('view-version', asDna(row))">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </template>

  <!-- 推荐依据 -->
  <el-dialog v-model="recommendVisible" title="参考图推荐依据" width="820px">
    <template v-if="recommendation">
      <p class="muted">
        参考图：{{ recommendation.imageName }}
        <template v-if="recommendation.imageWidth">
          （{{ recommendation.imageWidth }}×{{ recommendation.imageHeight }}）
        </template>
        <template v-if="recommendation.observedProductRatio != null">
          　实测产品占画面 {{ recommendation.observedProductRatio.toFixed(0) }}%
        </template>
      </p>

      <el-alert
        v-for="(item, index) in recommendation.conflicts || []"
        :key="'c' + index"
        type="warning"
        show-icon
        :closable="false"
        class="notice"
        :title="item"
      />

      <el-table :data="recommendation.evidence || []" size="small" class="ev-table">
        <el-table-column prop="field" label="字段" width="150" />
        <el-table-column prop="value" label="推荐值" width="130" />
        <el-table-column label="可信度" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.reliability === 'HIGH' ? 'success' : 'warning'">
              {{ row.reliability === 'HIGH' ? '实测' : '弱启发' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="basis" label="依据" min-width="300" show-overflow-tooltip />
      </el-table>

      <div v-if="(recommendation.skipped || []).length" class="skip-block">
        <h4>测不出来、明确不猜的字段</h4>
        <ul class="note-list">
          <li v-for="(item, index) in recommendation.skipped" :key="'s' + index">{{ item }}</li>
        </ul>
      </div>

      <div v-if="(recommendation.notes || []).length" class="skip-block">
        <h4>说明</h4>
        <ul class="note-list">
          <li v-for="(item, index) in recommendation.notes" :key="'n' + index">{{ item }}</li>
        </ul>
      </div>
    </template>
    <template #footer>
      <el-button type="primary" @click="recommendVisible = false">知道了（已填入表单，保存后生效）</el-button>
    </template>
  </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { recommendDna } from '@/api/creative';
import type {
  CreativeDnaForm,
  DnaPromptVO,
  DnaRecommendationVO,
  DpVisualDnaVO,
  TagType
} from '@/api/creative/types';
import {
  DNA_LIGHTING_DIRS,
  DNA_LIGHTING_TYPES,
  DNA_LEVEL_OPTIONS,
  DNA_SOURCE_LABELS,
  DNA_SOURCE_TYPES
} from '@/api/creative/types';

/**
 * 「视觉基因」这一步的内容（V0.2 R38，装配组件名 `VisualDnaPanel`）。
 *
 * <p><b>为什么把表单搬进组件里</b>：原先表单状态（`form`）在页面上，页面既管"取哪一版基因"
 * 又管"这一版怎么编辑"。拆开之后职责清楚：</p>
 * <ul>
 *   <li>页面（`dna/index.vue`）：项目选择、拉数据、调接口、把成功写回 `dna`/`versions`；</li>
 *   <li>本组件：**当前这一版的编辑态**（表单）与"按参考图推荐"（它只填表单，不落库）；</li>
 * </ul>
 * <p>表单从 `dna` 派生（watch 覆盖填充），保存时把整份 payload 交给页面去落库——
 * 组件不直接调保存接口，这样"保存后要做什么"（刷新、推进指引线、提示文案）仍然只有页面一处。</p>
 *
 * <p>装配口径（R37）：页面以同名插槽 `#VisualDnaPanel` 把它交给工作台，
 * 由装配配置里的 DNA 步声明。组件名与 `dp_workspace_schema.layout_json` 里的一致。</p>
 *
 * @author creative
 */
const props = defineProps<{
  /** 当前项目ID（"按参考图推荐"要用） */
  taskId?: string;
  /** 当前这一版的基因（没有表示还没生成） */
  dna: DpVisualDnaVO | null;
  /** 版本历史 */
  versions: DpVisualDnaVO[];
  /** 派生提示词（只读展示） */
  prompt: DnaPromptVO | null;
  promptText: string;
  negativeText: string;
  /** 项目列表是否非空（空列表要引导去新建项目，而不是显示"还没有基因"） */
  hasProjects: boolean;
  loading: boolean;
  generating: boolean;
  saving: boolean;
  locking: boolean;
}>();

const emit = defineEmits<{
  /** 生成 / 重新生成 */
  (e: 'generate'): void;
  /** 保存（把编辑好的表单交回页面落库） */
  (e: 'save', payload: CreativeDnaForm): void;
  /** 锁定这一版 */
  (e: 'lock'): void;
  /** 重新派生提示词 */
  (e: 'load-prompt'): void;
  /** 查看历史版本（页面负责切换当前版） */
  (e: 'view-version', row: DpVisualDnaVO): void;
}>();

/** 编辑态：由 `dna` 覆盖填充（保存后页面刷新 dna，这里跟着回到最新版） */
const form = reactive<CreativeDnaForm>({});
const recommending = ref(false);
const recommendVisible = ref(false);
const recommendation = ref<DnaRecommendationVO | null>(null);

function sourceType(source?: string): TagType {
  return (source && DNA_SOURCE_TYPES[source]) || 'info';
}

function sourceLabel(source?: string): string {
  return (source && DNA_SOURCE_LABELS[source]) || source || '';
}

function asDna(row: unknown): DpVisualDnaVO {
  return row as DpVisualDnaVO;
}

/**
 * 用某一版基因覆盖表单。
 *
 * @param source 基因版本；为空时表单回到全空
 */
function fillForm(source: DpVisualDnaVO | null) {
  form.id = source?.id;
  form.styleKeywords = [...(source?.styleKeywords || [])];
  form.avoidKeywords = [...(source?.avoidKeywords || [])];
  form.colorPrimary = source?.colors?.primary || '';
  form.colorSecondary = source?.colors?.secondary || '';
  form.colorAccent = source?.colors?.accent || '';
  form.colorBg = source?.colors?.background || '';
  form.saturation = source?.saturation || '';
  form.contrastLevel = source?.contrastLevel || '';
  form.whitespaceLevel = source?.whitespaceLevel || '';
  form.lightingType = source?.lighting?.type || '';
  form.lightingDir = source?.lighting?.direction || '';
  form.productRatioMin = source?.productRatio?.min;
  form.productRatioMax = source?.productRatio?.max;
  form.typographyStyle = source?.typographyStyle || '';
  form.sceneType = source?.sceneType || '';
}

function resetForm() {
  fillForm(props.dna);
}

/** 保存：把表单整份交给页面（页面负责调接口、刷新与提示） */
function submit() {
  emit('save', { ...form });
}

/**
 * 按参考图推荐：把实测值填进表单并展示逐字段依据。
 *
 * 刻意**只填表单、不自动保存**：推荐值是给眼睛过一遍的，落库仍是人的动作。
 * 与已确认事实冲突时，事实优先——冲突由后端在 conflicts 里说明。
 */
async function doRecommend() {
  if (!props.taskId) return;
  recommending.value = true;
  try {
    const res = await recommendDna(props.taskId);
    const data = res.data || null;
    recommendation.value = data;
    if (!data?.analyzed) {
      ElMessage.warning((data?.notes || ['没有可用于分析的参考图'])[0]);
      recommendVisible.value = true;
      return;
    }
    // 只覆盖推荐有值的字段，其余保留用户已填内容
    if (data.colorPrimary) form.colorPrimary = data.colorPrimary;
    if (data.colorSecondary) form.colorSecondary = data.colorSecondary;
    if (data.colorAccent) form.colorAccent = data.colorAccent;
    if (data.colorBg) form.colorBg = data.colorBg;
    if (data.saturation) form.saturation = data.saturation;
    if (data.contrastLevel) form.contrastLevel = data.contrastLevel;
    if (data.whitespaceLevel) form.whitespaceLevel = data.whitespaceLevel;
    if (data.sceneType) form.sceneType = data.sceneType;
    if (data.lightingType) form.lightingType = data.lightingType;
    if (data.lightingDir) form.lightingDir = data.lightingDir;
    if (data.productRatioMin != null) form.productRatioMin = data.productRatioMin;
    if (data.productRatioMax != null) form.productRatioMax = data.productRatioMax;
    recommendVisible.value = true;
    ElMessage.success(`已按参考图填好 ${data.evidence?.length ?? 0} 项，确认后点「保存」`);
  } catch (error) {
    const anyError = error as { message?: string; response?: { data?: { msg?: string } } };
    ElMessage.error(anyError?.response?.data?.msg || anyError?.message || '按参考图推荐失败');
  } finally {
    recommending.value = false;
  }
}

// 换版 / 保存后重新拉取都会替换 dna 对象：表单跟着覆盖填充（这是"当前版"的编辑态）
watch(() => props.dna, (value) => fillForm(value), { immediate: true });
</script>

<style scoped lang="scss">
/* 这一步的内容区样式（R38 从 dna/index.vue 搬过来，一字未改） */
.dna-panel {
  display: block;
}

.panel {
  padding: 16px;
  margin-bottom: 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.panel h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.generate-panel .el-button {
  margin-top: 10px;
}

.dna-title {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}
.version {
  margin-left: 6px;
  font-size: 13px;
  color: var(--t2);
}
.dna-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  font-size: 13px;
}
.dna-actions {
  display: flex;
  gap: 8px;
}

.issues {
  margin-top: 12px;
}
.issue-list {
  padding-left: 18px;
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.9;
}

.block-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.recommend-hint {
  margin-bottom: 14px;
}
.notice {
  margin-bottom: 10px;
}
.ev-table {
  margin-top: 6px;
}
.skip-block {
  margin-top: 14px;
}
.skip-block h4 {
  margin: 0 0 6px;
  font-size: 13px;
}
.note-list {
  padding-left: 18px;
  margin: 0;
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--t2);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px 16px;
}
.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-item.span2 {
  grid-column: span 2;
}
.form-item > label {
  font-size: 12px;
  color: var(--t2);
}

.save-row {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.mt8 {
  margin-top: 8px;
}

.good {
  color: #a7f3d0;
}
.bad {
  color: #fde68a;
}

.muted {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--t2);
}
.empty {
  padding: 16px 0;
  font-size: 13px;
  color: var(--t2);
}
</style>
