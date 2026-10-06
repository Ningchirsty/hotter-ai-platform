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
            <p class="title-note">
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
          :workflows="workflows"
          :busy-screen-id="screenActionId"
          @generate="doGenerateStoryboard"
          @lock="doLockStoryboard"
          @edit-screen="openScreenEdit"
          @lock-screen="doLockScreen"
          @add-screen="doAddScreen"
          @delete-screen="doDeleteScreen"
          @open-module-plan="openModulePlan"
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
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  addStoryboardScreen,
  deleteStoryboardScreen,
  generateDirections,
  generateStoryboard,
  getStoryboard,
  listCreativeProject,
  listCreativeWorkflows,
  listDirections,
  lockStoryboard,
  lockStoryboardScreen,
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
  CreativeWorkflowVO,
  DpStoryboardScreenVO,
  DpStoryboardVO,
  DpVisualDirectionVO,
  ProductionRunVO,
  ScreenProductionVO
} from '@/api/creative/types';
import CreativeWorkspace from '../components/CreativeWorkspace.vue';
import CreativeBriefStrip from '../components/CreativeBriefStrip.vue';
import { notifyNoProject } from '../composables/noProject';
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
const router = useRouter();
// 流程指引线的刷新令牌：只在动作成功后 +1，加载/刷新函数里不动它
const flowToken = ref(0);
const directions = ref<DpVisualDirectionVO[]>([]);
const storyboard = ref<DpStoryboardVO | null>(null);
const production = ref<ProductionRunVO | null>(null);
/** 出图工作流清单（只用于把 `wf-i2i-qwen21` 这种代号翻成「图生图」） */
const workflows = ref<CreativeWorkflowVO[]>([]);
const loading = ref(false);
const generatingDir = ref(false);
const generatingSb = ref(false);
const selectingId = ref('');
const lockingSb = ref(false);
const savingDirection = ref(false);
const savingScreen = ref(false);
/**
 * 正在处理的屏（逐屏锁定/加屏/删屏共用）——v1 裁定 ④。
 * 一次只动一屏，所以一个 id 就够，比三个布尔量更难出现"两个按钮同时转圈"。
 */
const screenActionId = ref('');
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
  if (!taskId.value) {
    // 第 34 轮：没选项目时点「刷新」原先什么都不发生（"点了没反应"那一类），现在有回话
    notifyNoProject();
    return;
  }
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

/**
 * 拉出图工作流清单（v1 反馈：分镜卡片上原本打印 `wf-i2i-qwen21` 这种代号）。
 *
 * <p>与 `loadAll` 分开、且**失败不提示**：它只服务于"把代号翻成中文"这一件事，
 * 拉不到时组件如实回落成显示代号，不该因为一个装饰性请求打断整页。</p>
 */
async function loadWorkflows() {
  try {
    const res = await listCreativeWorkflows();
    workflows.value = res.data || [];
  } catch {
    workflows.value = [];
  }
}

async function doGenerateDirections() {
  generatingDir.value = true;
  try {
    const res = await generateDirections(taskId.value);
    // 提示里带上轮次（v1 裁定 ⑨）：重新生成会留下多组方向，说清这是第几轮
    const batchNo = res.data?.[0]?.batchNo;
    ElMessage.success(batchNo ? `已生成第 ${batchNo} 轮 3 个视觉方向` : '已生成 3 个视觉方向');
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
    // 用中文名而不是 A/B/C：页面上已经不展示编号了，提示里再印一个编号只会对不上
    ElMessage.success(`已选定方向「${item.directionName || '未命名'}」`);
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
  // v1 反馈 方向与分镜 1.4：「目前只能按照 7 个分镜头去锁定」——锁定的范围原先没写出来，
  // 人只看到一句"确认锁定？"。这里把**锁的是哪几屏、这个集合从哪来**说在点按钮之前，
  // 免得锁完才发现屏数不是自己要的（那时只能重新生成一版）。
  //
  // v1 裁定 ④ 之后，屏数在这一页就能改（整版锁定之前），所以这句话要跟着改：
  // 集合不再只"来自模块规划"，而是"你现在看到的这一版"。
  const count = storyboard.value?.screenCount ?? (storyboard.value?.screens || []).length;
  const scope = count
    ? `本次锁定的是当前这一版的 ${count} 屏（就是你现在看到的这些；锁定前可以加屏/删屏调整）。`
    : '';
  try {
    await ElMessageBox.confirm(
      scope +
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

/**
 * 去「模块规划」改屏集合与顺序（v1 反馈 方向与分镜 1.4：「应该是可配置的，
 * 目前只能按照 7 个分镜头去锁定」）。可配置这件事一直在（计划驱动屏集合），
 * 缺的是"在分镜这一步能看见并从这儿过去"，所以由分镜区块发一个事件过来。
 */
function openModulePlan() {
  if (!taskId.value) {
    return;
  }
  void router.push({ path: '/creative/module-plan', query: { taskId: String(taskId.value) } });
}

/**
 * 单独锁定 / 解锁一屏（v1 裁定 ④：「可以原地锁定一个屏幕，但其余可以自定义」）。
 *
 * 不解锁整版锁定：整版锁定后这一版就冻结了（要改就重新生成）——这条口径没变，
 * 单屏锁定只是草稿期"先冻住这一屏、其余继续改"。
 *
 * @param screen 屏
 * @param locked true＝锁定，false＝解锁
 */
async function doLockScreen(screen: DpStoryboardScreenVO, locked: boolean) {
  screenActionId.value = String(screen.id);
  try {
    await lockStoryboardScreen(taskId.value, screen.id, locked);
    ElMessage.success(locked
      ? `已锁定 ${screen.screenNo} 这一屏（其余屏仍可改）`
      : `已解锁 ${screen.screenNo}，这一屏现在可以改了`);
    await loadAll();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? (locked ? '锁定这一屏失败' : '解锁这一屏失败'));
  } finally {
    screenActionId.value = '';
  }
}

/**
 * 在某一屏之后插入一屏（v1 裁定 ④：屏数由使用人说了算）。
 *
 * 新屏是"人工新增的屏"：后端只沿用参照屏的类型/取景/保真等级/出图能力，
 * **文案与画面独白留空**（不替人编一句），所以这里必须把"接下来要干嘛"说出来。
 */
async function doAddScreen(screen: DpStoryboardScreenVO) {
  screenActionId.value = String(screen.id);
  try {
    const res = await addStoryboardScreen(taskId.value, screen.id);
    const added = res.data as DpStoryboardScreenVO | undefined;
    ElMessage.success(`已在 ${screen.screenNo} 之后加了 1 屏（${added?.screenNo ?? '新屏'}）；`
      + '它的文案与画面独白是空的，请点「编辑」补上——锁定整版前每屏都必须有画面独白');
    await loadAll();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加屏失败');
  } finally {
    screenActionId.value = '';
  }
}

/**
 * 删掉一屏（v1 裁定 ④）。删之前先说清是哪一屏、删完屏号会重排——
 * 屏号是给人指认用的（"第 3 屏"），不说清很容易删错。
 */
async function doDeleteScreen(screen: DpStoryboardScreenVO) {
  const label = `${screen.screenNo}（${screen.screenTypeDesc || screen.screenType || '未命名'}`
    + `${screen.title ? ' · ' + screen.title : ''}）`;
  try {
    await ElMessageBox.confirm(
      `确定删除 ${label} 这一屏？删掉之后后面的屏号会往前补（S01..S0N）。`,
      '删除这一屏',
      { type: 'warning' }
    );
  } catch {
    return;
  }
  screenActionId.value = String(screen.id);
  try {
    await deleteStoryboardScreen(taskId.value, screen.id);
    ElMessage.success(`已删除 ${screen.screenNo}（屏号已重排）`);
    await loadAll();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '删屏失败');
  } finally {
    screenActionId.value = '';
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
    // 工作流清单与主数据并行拉：它只影响"代号显示成什么"，失败也不该拖住页面
    void loadWorkflows();
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
