<template>
  <div class="studio">
    <!-- R19：本页由工作台容器按配置装配；R38：这一步的内容改成**装配组件** VisualDnaPanel，
         页面只留页头（项目选择）与状态/接口——"这一步长什么样"归装配配置。 -->
    <CreativeWorkspace :task-id="taskId" :refresh-token="flowToken">
      <template #page-head>
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
      </template>

      <template #VisualDnaPanel>
        <VisualDnaPanel
          :task-id="taskId"
          :dna="dna"
          :versions="versions"
          :prompt="prompt"
          :prompt-text="promptText"
          :negative-text="negativeText"
          :has-projects="projects.length > 0"
          :loading="loading"
          :generating="generating"
          :saving="saving"
          :locking="locking"
          @generate="doGenerate"
          @save="doSave"
          @lock="doLock"
          @load-prompt="loadPrompt"
          @open-generation="openGeneration"
          @view-version="viewVersion"
        />
      </template>
    </CreativeWorkspace>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listCreativeProject } from '@/api/creative';
import { generateDna, getDna, getDnaPrompt, listDnaVersions, lockDna, saveDna } from '@/api/creative';
import type {
  CreativeDnaForm,
  CreativeProjectVO,
  DnaPromptVO,
  DpVisualDnaVO
} from '@/api/creative/types';
import { DNA_SOURCE_LABELS } from '@/api/creative/types';
import CreativeWorkspace from '../components/CreativeWorkspace.vue';
import VisualDnaPanel from './components/VisualDnaPanel.vue';

/**
 * 视觉基因页（V0.2 D 阶段 R19 起由工作台装配；R38 起这一步的内容是装配组件 `VisualDnaPanel`）。
 *
 * <p><b>页面留下的三件事</b>：① 项目选择（页头）；② 拉数据（基因 / 版本 / 提示词）；
 * ③ 调接口与"成功后做什么"（提示文案、刷新、推进流程指引线 `flowToken`）。
 * 编辑态表单与"按参考图推荐"在组件里——见 `VisualDnaPanel` 的说明。</p>
 *
 * <p>为什么保存/生成/锁定留在页面：它们要动的是**跨步骤**的东西（当前版基因、流程推进），
 * 不是"这一步的编辑态"。放在组件里会让组件顺手改页面状态，两边都不知道谁是权威。</p>
 *
 * @author creative
 */
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

function sourceLabel(source?: string): string {
  return (source && DNA_SOURCE_LABELS[source]) || source || '';
}

async function loadProjects() {
  // 深链优先：`?taskId=` 先落地，列表慢/失败都不影响它。
  // 原先只在"该项目出现在前 50 条列表里"才采用，列表拿不到就静默丢弃深链——
  // 表现是"直接开这个链接看到的是另一个项目/什么都没有"，看起来像页面没生效。
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
    const [latest, versionList] = await Promise.all([getDna(taskId.value), listDnaVersions(taskId.value)]);
    dna.value = latest.data || null;
    versions.value = versionList.data || [];
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

/**
 * 去出图框改提示词（v1 反馈「可编辑」）。
 *
 * <p>本页只展示派生结果，真正可编辑、也真正会生效的地方是项目页的
 * 「生成 HERO 主图」提交框。带 `taskId` 跳过去，用户不用自己找。</p>
 */
function openGeneration() {
  if (!taskId.value) {
    return;
  }
  window.open(`/creative/project?taskId=${taskId.value}`, '_self');
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

/**
 * 保存编辑好的这一版（payload 由 `VisualDnaPanel` 递过来）。
 *
 * @param payload 表单内容（空串表示"清掉这一项"，与后端约定一致）
 */
async function doSave(payload: CreativeDnaForm) {
  if (!taskId.value) return;
  saving.value = true;
  try {
    const res = await saveDna(taskId.value, { ...payload });
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

/** 查看某个历史版本：把它切成"当前版"（表单跟着覆盖填充，由组件监听 dna 完成） */
function viewVersion(target: DpVisualDnaVO) {
  dna.value = target;
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

/* 页头（R38）：留在页面上——它是"这一页"的框架（项目选择 + 刷新），不属于某一步的内容。
   这一步的内容样式搬进了 VisualDnaPanel（那边 scoped 生效）。 */
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
.muted {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--t2);
}

/* Element Plus 控件的暗色适配：作用域在整个工作台上（这一步的内容也在其中） */
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
