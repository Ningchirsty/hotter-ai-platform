<template>
  <div class="studio">
    <CreativeFlowGuide :task-id="taskId" :refresh-token="flowToken" />
    <header class="dna-head">
      <div>
        <h2>视觉基因 DNA</h2>
        <p class="muted">
          把「这条详情页长什么样」写成结构化规范：配色、光线、留白、产品占比、场景与禁忌。
          <strong>锁定后不可修改</strong>，再改会新建版本——这样后面每一屏、每一张图都能回答「是按哪版基因做的」。
        </p>
      </div>
      <div class="head-actions">
        <el-select v-model="taskId" placeholder="选择视觉项目" filterable style="width: 260px" @change="onProjectChange">
          <el-option
            v-for="project in projects"
            :key="String(project.taskId)"
            :label="project.taskName || String(project.taskId)"
            :value="String(project.taskId)"
          />
        </el-select>
        <el-button v-if="dna" plain @click="loadAll">刷新</el-button>
      </div>
    </header>

    <p v-if="!projects.length" class="empty">还没有视觉项目。先到「视觉项目」页新建一个。</p>
    <p v-else-if="loading" class="empty">加载中…</p>

    <!-- 尚未生成 -->
    <section v-else-if="!dna" class="panel generate-panel">
      <h3>这个项目还没有视觉基因</h3>
      <p class="muted">
        生成时会用到：<strong>已确认的产品事实</strong>（颜色、品牌调性、主体版本…）、
        <strong>项目里的参考图</strong>，以及一组<strong>明确标注来源的默认规范</strong>。
      </p>
      <p class="muted">
        若治理台已为视觉分析注册了可用模型，模型结论会用于补全并逐项校验后采纳；
        没有可用模型时，基因来源会如实标为「事实推导」，<strong>不会把默认值包装成 AI 结论</strong>。
      </p>
      <el-button type="primary" :loading="generating" @click="doGenerate">生成视觉基因</el-button>
    </section>

    <template v-else>
      <!-- 概览 -->
      <section class="panel">
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
            <el-button size="small" :loading="generating" @click="doGenerate">重新生成</el-button>
            <el-button size="small" type="primary" :disabled="dna.locked" :loading="locking" @click="doLock">
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
      <section class="panel">
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
          <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
          <el-button @click="resetForm">还原为当前版本</el-button>
        </div>
      </section>

      <!-- 派生提示词 -->
      <section class="panel">
        <div class="block-head">
          <h3>按这版基因派生的出图提示词</h3>
          <el-button size="small" text type="primary" @click="loadPrompt">重新派生</el-button>
        </div>
        <p class="muted">
          用到的维度：{{ prompt?.applied?.join('、') || '—' }}。
          这是<strong>预填</strong>到出图框的内容，你可以改；改完照原样下发。
        </p>
        <el-input v-model="promptText" type="textarea" :rows="4" readonly />
        <el-input v-model="negativeText" type="textarea" :rows="2" readonly class="mt8" />
      </section>

      <!-- 证据链 -->
      <section class="panel">
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
      <section class="panel">
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
              <el-button size="small" text type="primary" @click="viewVersion(asDna(row))">查看</el-button>
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
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listCreativeProject } from '@/api/creative';
import { generateDna, getDna, getDnaPrompt, listDnaVersions, lockDna, recommendDna, saveDna } from '@/api/creative';
import type {
  CreativeDnaForm,
  CreativeProjectVO,
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
import CreativeFlowGuide from '../components/CreativeFlowGuide.vue';

const projects = ref<CreativeProjectVO[]>([]);
const taskId = ref('');
// 流程指引线的刷新令牌：只在动作成功后 +1，避免把它塞进加载函数导致每次进页面重复读阶段
const flowToken = ref(0);
const dna = ref<DpVisualDnaVO | null>(null);
const versions = ref<DpVisualDnaVO[]>([]);
const prompt = ref<DnaPromptVO | null>(null);
const promptText = ref('');
const negativeText = ref('');
const loading = ref(false);
const generating = ref(false);
const saving = ref(false);
const locking = ref(false);
const recommending = ref(false);
const recommendVisible = ref(false);
const recommendation = ref<DnaRecommendationVO | null>(null);

const form = reactive<CreativeDnaForm>({});

function sourceType(source?: string): TagType {
  return (source && DNA_SOURCE_TYPES[source]) || 'info';
}

function sourceLabel(source?: string): string {
  return (source && DNA_SOURCE_LABELS[source]) || source || '';
}

function asDna(row: unknown): DpVisualDnaVO {
  return row as DpVisualDnaVO;
}

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
  fillForm(dna.value);
}

/**
 * 按参考图推荐：把实测值填进表单并展示逐字段依据。
 *
 * 刻意**只填表单、不自动保存**：推荐值是给眼睛过一遍的，落库仍是人的动作。
 * 与已确认事实冲突时，事实优先——冲突由后端在 conflicts 里说明。
 */
async function doRecommend() {
  if (!taskId.value) return;
  recommending.value = true;
  try {
    const res = await recommendDna(taskId.value);
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
    ElMessage.error((await extractErrorMessage(error)) ?? '按参考图推荐失败');
  } finally {
    recommending.value = false;
  }
}

async function loadProjects() {
  const res = await listCreativeProject({ pageNum: 1, pageSize: 50 });
  projects.value = res.data?.rows || [];
  const queryTaskId = new URLSearchParams(location.search).get('taskId');
  if (queryTaskId && projects.value.some((p) => String(p.taskId) === queryTaskId)) {
    taskId.value = queryTaskId;
  } else if (projects.value.length) {
    taskId.value = String(projects.value[0].taskId);
  }
}

async function loadAll() {
  if (!taskId.value) return;
  loading.value = true;
  try {
    const [latest, versionList] = await Promise.all([getDna(taskId.value), listDnaVersions(taskId.value)]);
    dna.value = latest.data || null;
    versions.value = versionList.data || [];
    fillForm(dna.value);
    if (dna.value) {
      prompt.value = null;
      await loadPrompt();
    }
  } catch (error) {
    ElMessage.error(await extractErrorMessage(error) ?? '加载视觉基因失败');
  } finally {
    loading.value = false;
  }
}

async function loadPrompt() {
  if (!taskId.value) return;
  try {
    const res = await getDnaPrompt(taskId.value, 'HERO 主图');
    prompt.value = res.data || null;
    promptText.value = res.data?.prompt || '';
    negativeText.value = res.data?.negativePrompt || '';
  } catch {
    /* 提示词派生失败不影响基因查看 */
  }
}

function onProjectChange() {
  dna.value = null;
  versions.value = [];
  prompt.value = null;
  void loadAll();
}

async function doGenerate() {
  if (!taskId.value) return;
  generating.value = true;
  try {
    const res = await generateDna(taskId.value);
    dna.value = res.data || null;
    ElMessage.success(`已生成 v${dna.value?.version}，来源：${sourceLabel(dna.value?.source)}`);
    await loadAll();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error(await extractErrorMessage(error) ?? '生成视觉基因失败');
  } finally {
    generating.value = false;
  }
}

async function doSave() {
  if (!taskId.value) return;
  saving.value = true;
  try {
    const payload: CreativeDnaForm = { ...form };
    // 空串表示「清掉这一项」，与后端约定一致
    const res = await saveDna(taskId.value, payload);
    const wasLocked = dna.value?.locked;
    dna.value = res.data || null;
    ElMessage.success(wasLocked ? '已基于锁定版新建一版' : '已保存');
    await loadAll();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error(await extractErrorMessage(error) ?? '保存失败');
  } finally {
    saving.value = false;
  }
}

async function doLock() {
  if (!taskId.value || !dna.value) return;
  try {
    await ElMessageBox.confirm(
      '锁定后这一版不可修改（再改会新建版本），并且会成为后续出图与分镜的依据。确认锁定？',
      '锁定视觉基因',
      { type: 'warning' }
    );
  } catch {
    return;
  }
  locking.value = true;
  try {
    const res = await lockDna(taskId.value, dna.value.id);
    dna.value = res.data || null;
    ElMessage.success('已锁定');
    await loadAll();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error(await extractErrorMessage(error) ?? '锁定失败');
  } finally {
    locking.value = false;
  }
}

function viewVersion(target: DpVisualDnaVO) {
  dna.value = target;
  fillForm(target);
  if (target.locked) {
    ElMessage.info('这是已锁定版本，页面已切换到该版');
  }
}

async function extractErrorMessage(error: unknown): Promise<string | undefined> {
  const anyError = error as { message?: string; response?: { data?: unknown } };
  const data = anyError?.response?.data;
  if (data instanceof Blob) {
    try {
      const text = await data.text();
      const parsed = JSON.parse(text) as { msg?: string; message?: string };
      return parsed.msg || parsed.message || text;
    } catch {
      return anyError?.message;
    }
  }
  if (data && typeof data === 'object') {
    const parsed = data as { msg?: string; message?: string };
    return parsed.msg || parsed.message || anyError?.message;
  }
  return anyError?.message;
}

onMounted(async () => {
  try {
    await loadProjects();
    await loadAll();
  } catch (error) {
    ElMessage.error(await extractErrorMessage(error) ?? '初始化失败');
  }
});
</script>

<style scoped lang="scss">
@use '@/assets/styles/tokens-studio.scss';

.studio {
  min-height: calc(100vh - 135px);
  padding: 24px;
  color: var(--t1);
  background: var(--bg);
  background-image: radial-gradient(900px 460px at 84% -10%, rgba(148, 163, 184, 0.16), transparent 68%);
  border: 1px solid var(--line);
  border-radius: 8px;
}

.dna-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--line);
}
.dna-head h2 {
  margin: 0 0 6px;
  font-size: 18px;
}
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
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

.studio :deep(.el-input__wrapper),
.studio :deep(.el-textarea__inner),
.studio :deep(.el-select__wrapper) {
  background: var(--sunken);
  box-shadow: 0 0 0 1px var(--line) inset;
}
.studio :deep(.el-input__inner),
.studio :deep(.el-textarea__inner) {
  color: var(--t1);
}
.studio :deep(th.el-table__cell) {
  color: var(--t2);
  background: var(--elevated);
  border-bottom-color: var(--line);
}
.studio :deep(td.el-table__cell) {
  color: var(--t1);
  background: transparent;
  border-bottom-color: var(--line);
}
.studio :deep(.el-table),
.studio :deep(.el-table tr) {
  background: transparent;
}
</style>
