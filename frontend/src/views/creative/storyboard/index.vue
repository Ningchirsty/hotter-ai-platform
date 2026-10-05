<template>
  <div class="studio">
    <!-- R19：本页由工作台容器按配置装配。R39：两个步骤各拆成装配组件——
         DIRECTION → DirectionBoard、STORYBOARD → StoryboardBoard；
         「逐屏出图与质检」是「出图」步的另一种呈现（按屏），R43 起与生产页共用同一个组件 GenerationBoard。 -->
    <CreativeWorkspace
      :task-id="taskId"
      :refresh-token="flowToken"
      :loading="loading"
      @refresh="loadAll"
    >
      <template #page-head>
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
        <!--
          S12：品牌要求摘要条。本页是设计师真正干活的地方（选方向、拆分镜），
          而品牌要求原先只在「视觉项目」页可见——人不会为了看一眼必显信息来回切页面。
        -->
        <CreativeBriefStrip :task-id="taskId" />
      </template>

      <template #DirectionBoard>
        <DirectionBoard
          :directions="directions"
          :template-source="templateSource"
          :generating="generatingDir"
          :selecting-id="selectingId"
          :has-projects="projects.length > 0"
          @generate="doGenerateDirections"
          @select="doSelect"
          @edit="openDirectionEdit"
        />
      </template>

      <template #StoryboardBoard>
        <StoryboardBoard
          :storyboard="storyboard"
          :generating="generatingSb"
          :locking="lockingSb"
          :has-projects="projects.length > 0"
          @generate="doGenerateStoryboard"
          @lock="doLockStoryboard"
          @edit-screen="openScreenEdit"
        />
      </template>

      <template #GenerationBoard>
        <!-- 逐屏出图与质检（R43 起收敛）：与生产页共用同一个装配组件 GenerationBoard，
             这里用 SCREENS 模式（按屏看"哪一屏还没出、哪一屏质检没过"并批量出图），
             生产页用 CANDIDATES 模式（按候选看缩略图与质检明细）。 -->
        <GenerationBoard
          mode="SCREENS"
          :rows="[]"
          :storyboard="storyboard"
          :screen-map="{}"
          :production="production"
          :loading="loading"
          :refreshing="refreshing"
          :producing="producing"
          :busy="screenBusyKey"
          :thumb-url="() => ''"
          :url-of="() => ''"
          :product-image="null"
          :compare-gen="null"
          compare-screen-label=""
          @refresh="doRefreshProduction"
          @produce="doStartProduction"
          @regenerate-screen="doRegenerate"
          @select-screen="doSelectCandidate"
          @qa-screen="doQa"
        />
      </template>
    </CreativeWorkspace>

    <!-- 编辑弹窗（页面级浮层）：保存成功后才关，并且要顺带刷新数据与推进指引线——
         这三件事必须在一起，所以弹窗与落库都留在页面（与 R32/R33 的项目页区块同一套做法）。 -->
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
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  generateDirections,
  generateStoryboard,
  getStoryboard,
  listCreativeProject,
  listDirections,
  lockStoryboard,
  refreshProduction,
  regenerateScreen,
  runCandidateQa,
  selectCandidate,
  selectDirection,
  startProduction,
  updateDirection,
  updateScreen
} from '@/api/creative';
import type {
  CreativeDirectionForm,
  CreativeProjectVO,
  CreativeScreenForm,
  DpStoryboardScreenVO,
  DpStoryboardVO,
  DpVisualDirectionVO,
  ProductionRunVO,
  ScreenProductionVO
} from '@/api/creative/types';
import CreativeWorkspace from '../components/CreativeWorkspace.vue';
import CreativeBriefStrip from '../components/CreativeBriefStrip.vue';
import GenerationBoard from '../production/components/GenerationBoard.vue';
import { latestGenerationOf } from '../production/generationText';
import DirectionBoard from './components/DirectionBoard.vue';
import StoryboardBoard from './components/StoryboardBoard.vue';

/**
 * 视觉方向与分镜页（R19 起由工作台装配；R39 起两个步骤各自是装配组件）。
 *
 * <p><b>页面留下什么</b>：项目选择（页头）、拉数据、调接口、"成功后做什么"（提示 / 刷新 / 推进指引线），
 * 以及两个编辑弹窗；「逐屏出图与质检」是「出图」步的一个装配组件（SCREENS 模式），不再挂在页面级。</p>
 *
 * <p><b>为什么弹窗留在页面</b>：它同时牵动"关闭弹窗 + 刷新列表 + 推进流程指引线"三件事，
 * 拆到组件里就会出现半个状态（组件关了弹窗但页面没刷新）。方向/分镜两块内容则是纯展示。</p>
 *
 * @author creative
 */
const projects = ref<CreativeProjectVO[]>([]);
const taskId = ref('');
// 流程指引线的刷新令牌：只在动作成功后 +1，加载/刷新函数里不动它
const flowToken = ref(0);
const directions = ref<DpVisualDirectionVO[]>([]);
const storyboard = ref<DpStoryboardVO | null>(null);
const production = ref<ProductionRunVO | null>(null);
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
const producing = ref(false);
const refreshing = ref(false);
const busyScreen = ref('');
const selectingGen = ref('');
const qaGen = ref('');

function asScreen(row: unknown): ScreenProductionVO {
  return row as ScreenProductionVO;
}

/**
 * 当前"正在进行的动作"（喂给 GenerationBoard 的 `busy`，决定按钮 loading）。
 *
 * <p>页面有三个互斥的忙碌标记（重出/选定/质检），组件的约定是一个字符串键；
 * 这里做一次映射，避免为了一个 loading 把三个 ref 都透传进组件。</p>
 *
 * @returns 形如 `regen-screen-<screenId>` / `select-gen-<genId>` / `qa-gen-<genId>`；空闲为空串
 */
const screenBusyKey = computed(() => {
  if (busyScreen.value) return 'regen-screen-' + busyScreen.value;
  if (selectingGen.value) return 'select-gen-' + selectingGen.value;
  if (qaGen.value) return 'qa-gen-' + qaGen.value;
  return '';
});

async function doStartProduction() {
  producing.value = true;
  try {
    const res = await startProduction(taskId.value, false);
    production.value = res.data || null;
    ElMessage.success(`已提交 ${res.data?.submitted ?? 0} 屏出图，跳过 ${res.data?.skipped ?? 0} 屏`);
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '批量出图失败');
  } finally {
    producing.value = false;
  }
}

async function doRefreshProduction() {
  refreshing.value = true;
  try {
    const res = await refreshProduction(taskId.value);
    production.value = res.data || null;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '刷新失败');
  } finally {
    refreshing.value = false;
  }
}

async function doRegenerate(row: ScreenProductionVO) {
  busyScreen.value = String(row.screenId);
  try {
    await regenerateScreen(taskId.value, row.screenId);
    ElMessage.success(`${row.screenNo} 已重新提交出图`);
    await doRefreshProduction();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '重出失败');
  } finally {
    busyScreen.value = '';
  }
}

async function doSelectCandidate(row: ScreenProductionVO) {
  const generationId = latestGenerationOf(row);
  if (!generationId) return;
  selectingGen.value = String(generationId);
  try {
    await selectCandidate(taskId.value, generationId);
    ElMessage.success(`${row.screenNo} 已选定候选，并已登记产出与发起质检`);
    await doRefreshProduction();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '选定失败');
  } finally {
    selectingGen.value = '';
  }
}

async function doQa(row: ScreenProductionVO) {
  const generationId = latestGenerationOf(row);
  if (!generationId) return;
  qaGen.value = String(generationId);
  try {
    await runCandidateQa(taskId.value, generationId);
    ElMessage.success('已发起质检（只筛除，不放行）');
    await doRefreshProduction();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '发起质检失败');
  } finally {
    qaGen.value = '';
  }
}

async function loadProjects() {
  // 深链优先：`?taskId=` 先落地，列表慢/失败都不影响它（原先只在列表里找得到才采用，
  // 找不到就悄悄换成第一个项目——直接开链接看起来就像"没生效"）
  const queryTaskId = new URLSearchParams(location.search).get('taskId') || '';
  if (queryTaskId) {
    taskId.value = queryTaskId;
  }
  try {
    const res = await listCreativeProject({ pageNum: 1, pageSize: 50 });
    projects.value = res.data?.rows || [];
  } catch (error) {
    projects.value = [];
    ElMessage.error('加载视觉项目列表失败（深链项目仍按 id 打开，可刷新重试）');
    return;
  }
  if (!queryTaskId && projects.value.length) {
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
    // 顺带拉一次逐屏生产状态（含 QA 结论回填与自动重试结果）
    try {
      const prodRes = await refreshProduction(taskId.value);
      production.value = prodRes.data || null;
    } catch {
      /* 生产状态拉取失败不影响方向/分镜查看 */
    }
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
    flowToken.value += 1;
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
    flowToken.value += 1;
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
    flowToken.value += 1;
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
    flowToken.value += 1;
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
    flowToken.value += 1;
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
    flowToken.value += 1;
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

/* 页头（R39）：留在页面上；方向与分镜两块内容的样式搬进了各自组件 */
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

/* R43：逐屏表搬进了 GenerationBoard，「这一步」的容器样式（.panel / .block-head / 质检配色）
   随它一起走——这里只留页头与页脚自己的样式，避免两处各留一份慢慢漂移。 */

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
