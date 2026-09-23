<template>
  <div class="studio">
    <header class="page-head">
      <div>
        <h2>视觉方向与分镜</h2>
        <p class="muted">
          先在同一个锁定基因下选一个方向（只在场景/光线/构图/情绪上分叉），再拆成逐屏规格。
          分镜锁定后不可修改，重新生成会出新版本。
        </p>
      </div>
      <div class="head-actions">
        <el-select v-model="taskId" placeholder="选择视觉项目" filterable style="width: 260px" @change="loadAll">
          <el-option
            v-for="project in projects"
            :key="String(project.taskId)"
            :label="project.taskName || String(project.taskId)"
            :value="String(project.taskId)"
          />
        </el-select>
        <el-button plain :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </header>

    <p v-if="!projects.length" class="empty">还没有视觉项目。先到「视觉项目」页新建一个。</p>
    <template v-else>
      <!-- 方向 -->
      <section class="panel">
        <div class="block-head">
          <h3>1. 视觉方向（A/B/C）</h3>
          <div class="head-actions">
            <span class="muted">来源：{{ DIRECTION_SOURCE_LABELS[templateSource] || templateSource || '—' }}</span>
            <el-button type="primary" :loading="generatingDir" @click="doGenerateDirections">
              {{ directions.length ? '重新生成方向' : '生成方向' }}
            </el-button>
          </div>
        </div>

        <p v-if="!directions.length" class="empty">
          还没有方向。生成后会得到三套「同一基因下的不同取舍」，选定其一即可继续拆分镜。
          （须先有<b>已锁定</b>的视觉基因）
        </p>
        <div v-else class="direction-grid">
          <div
            v-for="item in directions"
            :key="String(item.id)"
            class="direction-card"
            :class="{ selected: item.status === 'SELECTED', rejected: item.status === 'REJECTED' }"
          >
            <div class="direction-head">
              <span class="code">{{ item.directionCode }}</span>
              <span class="name">{{ item.directionName }}</span>
              <el-tag v-if="item.status === 'SELECTED'" type="success" size="small">已选定</el-tag>
              <el-tag v-else-if="item.status === 'REJECTED'" type="info" size="small">已弃用</el-tag>
            </div>
            <p class="concept">{{ item.concept }}</p>
            <ul class="strategy-list">
              <li v-for="key in strategyKeys(item)" :key="key">
                <span class="key" :class="{ diff: (item.differences || []).includes(key) }">{{ key }}</span>
                <span class="value">{{ item.strategy?.[key] }}</span>
              </li>
            </ul>
            <div class="direction-actions">
              <el-button
                size="small"
                type="primary"
                :disabled="item.status === 'SELECTED'"
                :loading="selectingId === String(item.id)"
                @click="doSelect(item)"
              >
                {{ item.status === 'SELECTED' ? '当前方向' : '选定这个方向' }}
              </el-button>
              <el-button size="small" text @click="openDirectionEdit(item)">编辑文案</el-button>
            </div>
          </div>
        </div>
      </section>

      <!-- 分镜 -->
      <section class="panel">
        <div class="block-head">
          <h3>2. 分镜</h3>
          <div class="head-actions">
            <template v-if="storyboard">
              <span class="muted">
                {{ storyboard.storyboardNo }} · v{{ storyboard.version }} ·
                {{ storyboard.statusDesc }} · {{ storyboard.screenCount }} 屏
              </span>
              <el-button size="small" :loading="generatingSb" @click="doGenerateStoryboard">重新生成分镜</el-button>
              <el-button
                size="small"
                type="primary"
                :disabled="storyboard.status === 'LOCKED'"
                :loading="lockingSb"
                @click="doLockStoryboard"
              >
                {{ storyboard.status === 'LOCKED' ? '已锁定' : '锁定分镜' }}
              </el-button>
            </template>
            <el-button v-else type="primary" :loading="generatingSb" @click="doGenerateStoryboard">
              生成分镜
            </el-button>
          </div>
        </div>

        <p v-if="storyboard && storyboard.sourceDesc" class="muted source-note">
          来源：{{ storyboard.sourceDesc }}
        </p>

        <p v-if="!storyboard" class="empty">
          还没有分镜。生成后会得到逐屏规格（屏号 / 类型 / 文案 / 画面独白 / 视觉规格）。
          每屏必须写清「这张图不讲文案时自己要说清什么」——没有独白的屏不能锁定。
        </p>
        <div v-else class="screen-list">
          <div v-for="screen in storyboard.screens || []" :key="String(screen.id)" class="screen-card">
            <div class="screen-head">
              <span class="screen-no">{{ screen.screenNo }}</span>
              <span class="screen-type">{{ screen.screenTypeDesc }}</span>
              <el-tag size="small" :type="screen.productLockLevel === 'STRICT' ? 'warning' : 'info'">
                {{ screen.productLockLevel === 'STRICT' ? '产品严格保真' : '允许艺术化' }}
              </el-tag>
              <span class="spacer" />
              <el-button size="small" text type="primary" @click="openScreenEdit(screen)">编辑</el-button>
            </div>
            <div class="screen-body">
              <h4>{{ screen.title || '（未填标题）' }}</h4>
              <p v-if="screen.subtitle" class="muted">{{ screen.subtitle }}</p>
              <p v-if="screen.bodyText" class="body-text">{{ screen.bodyText }}</p>
              <p class="solo">
                <b>画面独白：</b>{{ screen.pictureSoloStatement || '（缺失——锁定前必须补齐）' }}
              </p>
              <div class="spec-row">
                <span v-for="(value, key) in screen.spec" :key="key" class="spec-item">
                  <b>{{ specLabel(String(key)) }}</b>{{ value }}
                </span>
              </div>
              <p class="muted small">出图能力：{{ screen.workflowCode }}</p>
            </div>
          </div>
        </div>
      </section>
    </template>

    <!-- 编辑方向 -->
    <el-dialog v-model="directionEditVisible" title="编辑方向文案" width="520px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="directionForm.directionName" maxlength="64" /></el-form-item>
        <el-form-item label="概念">
          <el-input v-model="directionForm.concept" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="directionEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingDirection" @click="doSaveDirection">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑单屏 -->
    <el-dialog v-model="screenEditVisible" title="编辑分镜单屏" width="720px">
      <el-form label-width="92px">
        <el-form-item label="标题"><el-input v-model="screenForm.title" maxlength="255" /></el-form-item>
        <el-form-item label="副标题"><el-input v-model="screenForm.subtitle" maxlength="255" /></el-form-item>
        <el-form-item label="正文">
          <el-input v-model="screenForm.bodyText" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
        <el-form-item label="画面独白">
          <el-input
            v-model="screenForm.pictureSoloStatement"
            type="textarea"
            :rows="2"
            maxlength="1000"
            placeholder="这张图不讲文案时，自己要说清什么"
          />
        </el-form-item>
        <el-form-item label="镜头"><el-input v-model="screenForm.shot" maxlength="64" /></el-form-item>
        <el-form-item label="构图"><el-input v-model="screenForm.composition" maxlength="128" /></el-form-item>
        <el-form-item label="光线"><el-input v-model="screenForm.lighting" maxlength="128" /></el-form-item>
        <el-form-item label="背景"><el-input v-model="screenForm.background" maxlength="128" /></el-form-item>
        <el-form-item label="产品保真">
          <el-select v-model="screenForm.productLockLevel" style="width: 100%">
            <el-option label="严格保真（结构与配色不得变）" value="STRICT" />
            <el-option label="允许艺术化（可换场景与角度）" value="LOOSE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="screenEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingScreen" @click="doSaveScreen">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  generateDirections,
  generateStoryboard,
  getStoryboard,
  listCreativeProject,
  listDirections,
  lockStoryboard,
  selectDirection,
  updateDirection,
  updateScreen
} from '@/api/creative';
import type {
  CreativeDirectionForm,
  CreativeProjectVO,
  CreativeScreenForm,
  DpStoryboardScreenVO,
  DpStoryboardVO,
  DpVisualDirectionVO
} from '@/api/creative/types';
import { DIRECTION_SOURCE_LABELS } from '@/api/creative/types';

const projects = ref<CreativeProjectVO[]>([]);
const taskId = ref('');
const directions = ref<DpVisualDirectionVO[]>([]);
const storyboard = ref<DpStoryboardVO | null>(null);
const loading = ref(false);
const generatingDir = ref(false);
const generatingSb = ref(false);
const selectingId = ref('');
const lockingSb = ref(false);
const savingDirection = ref(false);
const savingScreen = ref(false);
const directionEditVisible = ref(false);
const screenEditVisible = ref(false);
const directionForm = reactive<CreativeDirectionForm>({ id: '' });
const screenForm = reactive<CreativeScreenForm>({ id: '' });
const templateSource = ref('');

function strategyKeys(item: DpVisualDirectionVO): string[] {
  return Object.keys(item.strategy || {}).filter((key) => key !== 'schema' && key !== 'differences');
}

function specLabel(key: string): string {
  const map: Record<string, string> = {
    shot: '镜头',
    composition: '构图',
    lighting: '光线',
    background: '背景',
    productRatio: '产品占比',
    whitespace: '留白'
  };
  return map[key] || key;
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
    const [dirRes, sbRes] = await Promise.all([listDirections(taskId.value), getStoryboard(taskId.value)]);
    directions.value = dirRes.data || [];
    storyboard.value = sbRes.data || null;
    templateSource.value = directions.value[0]?.source || '';
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载失败');
  } finally {
    loading.value = false;
  }
}

async function doGenerateDirections() {
  generatingDir.value = true;
  try {
    await generateDirections(taskId.value);
    ElMessage.success('已生成 A/B/C 三个方向');
    await loadAll();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '生成方向失败');
  } finally {
    generatingDir.value = false;
  }
}

async function doSelect(item: DpVisualDirectionVO) {
  selectingId.value = String(item.id);
  try {
    await selectDirection(taskId.value, item.id);
    ElMessage.success(`已选定方向 ${item.directionCode}`);
    await loadAll();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '选定失败');
  } finally {
    selectingId.value = '';
  }
}

function openDirectionEdit(item: DpVisualDirectionVO) {
  directionForm.id = item.id;
  directionForm.directionName = item.directionName;
  directionForm.concept = item.concept;
  directionForm.remark = item.remark;
  directionEditVisible.value = true;
}

async function doSaveDirection() {
  savingDirection.value = true;
  try {
    await updateDirection(taskId.value, { ...directionForm });
    ElMessage.success('已保存');
    directionEditVisible.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '保存失败');
  } finally {
    savingDirection.value = false;
  }
}

async function doGenerateStoryboard() {
  generatingSb.value = true;
  try {
    await generateStoryboard(taskId.value);
    ElMessage.success('已生成分镜');
    await loadAll();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '生成分镜失败');
  } finally {
    generatingSb.value = false;
  }
}

async function doLockStoryboard() {
  try {
    await ElMessageBox.confirm(
      '锁定后这一版分镜不可修改（重新生成会出新版本），并成为视觉门审核与批量出图的依据。确认锁定？',
      '锁定分镜',
      { type: 'warning' }
    );
  } catch {
    return;
  }
  lockingSb.value = true;
  try {
    await lockStoryboard(taskId.value, storyboard.value?.id);
    ElMessage.success('分镜已锁定');
    await loadAll();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '锁定失败');
  } finally {
    lockingSb.value = false;
  }
}

function openScreenEdit(screen: DpStoryboardScreenVO) {
  screenForm.id = screen.id;
  screenForm.title = screen.title;
  screenForm.subtitle = screen.subtitle;
  screenForm.bodyText = screen.bodyText;
  screenForm.pictureSoloStatement = screen.pictureSoloStatement;
  screenForm.shot = String(screen.spec?.shot ?? '');
  screenForm.composition = String(screen.spec?.composition ?? '');
  screenForm.lighting = String(screen.spec?.lighting ?? '');
  screenForm.background = String(screen.spec?.background ?? '');
  screenForm.workflowCode = screen.workflowCode;
  screenForm.productLockLevel = screen.productLockLevel;
  screenEditVisible.value = true;
}

async function doSaveScreen() {
  savingScreen.value = true;
  try {
    await updateScreen(taskId.value, { ...screenForm });
    ElMessage.success('已保存');
    screenEditVisible.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '保存失败');
  } finally {
    savingScreen.value = false;
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
    ElMessage.error((await extractErrorMessage(error)) ?? '初始化失败');
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

.page-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--line);
}
.page-head h2 {
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
  margin: 0;
  font-size: 15px;
}
.block-head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.direction-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}
.direction-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.direction-card.selected {
  border-color: #10b981;
  box-shadow: inset 0 0 0 1px rgba(16, 185, 129, 0.35);
}
.direction-card.rejected {
  opacity: 0.62;
}
.direction-head {
  display: flex;
  gap: 8px;
  align-items: center;
}
.direction-head .code {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-radius: 6px;
}
.direction-head .name {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
}
.concept {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--t2);
}
.strategy-list {
  padding: 0;
  margin: 0;
  list-style: none;
  font-size: 12px;
  line-height: 1.9;
}
.strategy-list li {
  display: flex;
  gap: 8px;
}
.strategy-list .key {
  flex: 0 0 84px;
  color: var(--t3);
}
.strategy-list .key.diff {
  color: #fde68a;
}
.strategy-list .value {
  flex: 1;
  color: var(--t1);
}
.direction-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.source-note {
  margin: 0 0 10px;
}

.screen-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 12px;
}
.screen-card {
  display: flex;
  flex-direction: column;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.screen-head {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}
.screen-head .spacer {
  flex: 1;
}
.screen-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: #c7d2fe;
}
.screen-type {
  font-size: 13px;
  font-weight: 600;
}
.screen-body {
  padding: 12px;
}
.screen-body h4 {
  margin: 0 0 6px;
  font-size: 14px;
}
.body-text {
  margin: 6px 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--t1);
}
.solo {
  margin: 8px 0;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.8;
  color: #ddd6fe;
  background: rgba(124, 58, 237, 0.12);
  border-left: 2px solid rgba(124, 58, 237, 0.6);
  border-radius: 0 4px 4px 0;
}
.spec-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--t2);
}
.spec-item b {
  margin-right: 4px;
  color: var(--t3);
  font-weight: 500;
}
.small {
  font-size: 12px;
}

.muted {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--t2);
}
.empty {
  padding: 12px 0;
  font-size: 13px;
  line-height: 1.9;
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
</style>
