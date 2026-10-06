<template>
  <div class="studio">
    <!-- R19：本页由工作台容器按配置装配。R40：三个步骤各拆成装配组件——
         GATE → GatePanel、LAYOUT → LongPageCanvas、FINAL → FinalReviewPanel；
         长图预览弹窗是页面级浮层（要负责 blob URL 释放），留在页面。 -->
    <CreativeWorkspace
      :task-id="taskId"
      :refresh-token="flowToken"
      :loading="loading"
      @refresh="loadAll"
    >
      <template #page-head>
        <header class="page-head">
          <div>
            <h2>详情页与审核</h2>
            <p class="title-note">
              视觉门是进入批量出图的<b>唯一入口</b>：硬性项不满足连提交都不允许；提交后由人确认或打回。
              <b>未通过视觉门，出图接口会直接拒绝</b>——门禁在后端强制，不是前端按钮变灰。
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
      </template>

      <template #GatePanel>
        <GatePanel
          :gate="gate"
          :has-projects="projects.length > 0"
          :submitting="submitting"
          :reviewing="reviewing"
          :task-id="taskId"
          @submit="doSubmit"
          @review="doReview"
          @go-fix="doGoFix"
        />
      </template>

      <template #LongPageCanvas>
        <LongPageCanvas
          :detail-page="detailPage"
          :rendering="rendering"
          :previewing-id="previewingId"
          :reviewing-id="reviewingId"
          :mode="canvasMode"
          :delivery="delivery"
          :delivering="delivering"
          @render="doRender"
          @render-delivery="doRenderDelivery"
          @preview="doPreview"
          @review-version="doVersionReview"
        />
      </template>

      <template #FinalReviewPanel>
        <FinalReviewPanel
          :detail-page="detailPage"
          :delivery="delivery"
          :uploading-final="uploadingFinal"
          :delivering="delivering"
          :downloading-id="downloadingId"
          :can-confirm-delivery="canConfirmDelivery"
          :confirming="confirming"
          @upload-final="doUploadFinal"
          @render-delivery="doRenderDelivery"
          @confirm-delivery="doConfirmDelivery"
          @download="doDownloadArtifact"
        />
      </template>
    </CreativeWorkspace>

    <!-- 长图预览（页面级浮层：blob URL 由页面取、由页面释放） -->
    <el-dialog v-model="previewVisible" title="详情页长图预览" width="820px" @closed="closePreview">
      <div class="long-preview">
        <img v-if="previewUrl" :src="previewUrl" alt="详情页长图" />
        <p v-else class="muted">加载中…</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { UploadRequestOptions } from 'element-plus';
import {
  confirmDelivery,
  downloadDeliveryArtifact,
  fetchDetailPreviewBlobUrl,
  getDelivery,
  getDetailPage,
  getVisualGate,
  listCreativeProject,
  renderDelivery,
  renderDetailPage,
  reviewDetailVersion,
  reviewVisualGate,
  submitVisualGate,
  uploadDetailFinal
} from '@/api/creative';
import type {
  CreativeProjectVO,
  DeliveryArtifactVO,
  DeliveryVO,
  DpDetailPageVO,
  DpDetailPageVersionVO,
  GateEvaluationVO
} from '@/api/creative/types';
import CreativeWorkspace from '../components/CreativeWorkspace.vue';
import GatePanel from './components/GatePanel.vue';
import LongPageCanvas from './components/LongPageCanvas.vue';
import FinalReviewPanel from './components/FinalReviewPanel.vue';
import { canConfirmDelivery as canConfirmDeliveryOf, canvasModeOf } from '../composables/deliveryActions';
import { notifyNoProject } from '../composables/noProject';

/**
 * 详情页与审核页（R19 起由工作台装配；R40 起三个步骤各自是装配组件）。
 *
 * <p><b>页面留下什么</b>：项目选择（页头）、拉数据（视觉门 / 详情页 / 交付产物）、调接口、
 * "成功后做什么"（提示 / 刷新 / 推进指引线），以及长图预览弹窗（blob URL 生命周期）。</p>
 *
 * <p><b>为什么长图预览弹窗留在页面</b>：它要取 blob URL 并在关闭时释放——这是页面级的资源生命周期，
 * 拆进组件就会出现"组件关了、URL 没释放"的泄漏（或反过来）。</p>
 *
 * @author creative
 */
const projects = ref<CreativeProjectVO[]>([]);
const taskId = ref('');
// 流程指引线的刷新令牌：只在动作成功后 +1，加载函数里不动它
const flowToken = ref(0);
const gate = ref<GateEvaluationVO | null>(null);
const detailPage = ref<DpDetailPageVO | null>(null);
// R30：交付产物（Renderer Hub）——渲染器能力 + 历史交付版本
const delivery = ref<DeliveryVO | null>(null);
const delivering = ref(false);
const confirming = ref(false);
const downloadingId = ref('');
const loading = ref(false);

/**
 * 版式步的画布形态（R52）：长图走长图排版，其余形态（海报/多图包）走"生成成品图"。
 * 判定是纯函数（composables/deliveryActions.ts，有单测）——后端对非长图调排版接口会明确拒绝。
 */
const canvasMode = computed(() => canvasModeOf(delivery.value));

/**
 * 能不能「确认交付」（R51 起；R52 起含海报）：形态允许 + 已有交付产物 + 项目还没完成。
 *
 * <p>为什么不问后端"能不能确认"：这三个条件在页面上都已经有数据（交付视图 + 视觉门返回的阶段），
 * 再发一次请求只会多一个可能过期的状态。真正会不会被拒由**后端**判（长图类会被明确拒绝）。</p>
 */
const canConfirmDelivery = computed(() => canConfirmDeliveryOf(delivery.value, gate.value?.stage));
const submitting = ref(false);
const reviewing = ref('');
const rendering = ref(false);
const reviewingId = ref('');
const previewingId = ref('');
const uploadingFinal = ref(false);
const previewVisible = ref(false);
const previewUrl = ref('');

async function doRenderDelivery() {
  if (!taskId.value) return;
  delivering.value = true;
  try {
    const res = await renderDelivery(taskId.value);
    delivery.value = (res.data as DeliveryVO) || null;
    ElMessage.success('交付产物已生成');
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '生成交付产物失败');
  } finally {
    delivering.value = false;
  }
}

async function doDownloadArtifact(artifact: DeliveryArtifactVO) {
  downloadingId.value = String(artifact.id);
  try {
    await downloadDeliveryArtifact(taskId.value, artifact.id, artifact.downloadName || 'delivery.zip');
    ElMessage.success('已开始下载');
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '下载交付产物失败');
  } finally {
    downloadingId.value = '';
  }
}

async function loadProjects() {
  // 深链优先：`?taskId=` 先落地，列表慢/失败都不影响它（理由同 dna/storyboard 页）
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
    // 第 34 轮：没选项目时点「刷新」原先什么都不发生，现在有回话
    notifyNoProject();
    return;
  }
  loading.value = true;
  try {
    const [gateRes, detailRes, deliveryRes] = await Promise.all([
      getVisualGate(taskId.value),
      getDetailPage(taskId.value),
      getDelivery(taskId.value)
    ]);
    gate.value = gateRes.data || null;
    detailPage.value = detailRes.data || null;
    delivery.value = deliveryRes.data || null;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载视觉门失败');
  } finally {
    loading.value = false;
  }
}

async function doRender() {
  rendering.value = true;
  try {
    const res = await renderDetailPage(taskId.value);
    detailPage.value = res.data || null;
    const latest = (detailPage.value?.versions || [])[0];
    ElMessage.success(latest
      ? `已渲染 v${latest.version}（${latest.pageWidth}×${latest.pageHeight}）`
      : '已渲染');
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '渲染失败');
  } finally {
    rendering.value = false;
  }
}

/**
 * 确认交付（R51）：把当前这一版交付产物定为最终交付物，项目置为「已完成」。
 *
 * <p>不可逆（会推进到终态），所以先让人确认一次——文案里写明是哪一版、多少张，
 * 不让人凭记忆点。</p>
 */
async function doConfirmDelivery() {
  const version = delivery.value?.currentVersion ?? 0;
  const artifact = (delivery.value?.artifacts || []).find((a) => a.version === version);
  // C9-c：多图交付同样可能"7 屏只出了 2 屏"。长图那条路已经有确认闸，这里是补齐另一条——
  // 缺屏这件事必须出现在**这一次确认**里（而不是页面上另找一条提示），否则还是一样的漏。
  const missing = detailPage.value?.screensWithoutSelection || [];
  const shortfall = missing.length
    ? `⚠ 还有 ${missing.length} 屏没有已选定的产出图（屏号：${missing.join('、')}）——`
      + '交付物里这几屏是空白的。\n\n'
    : '';
  try {
    await ElMessageBox.confirm(
      shortfall
      + `把交付产物 v${version}（${artifact?.imageCount ?? 0} 张）定为最终交付物，`
      + '项目将置为「已完成」。这一步不可撤销，之后要改需要走返工。',
      missing.length ? '带空屏交付确认' : '确认交付',
      {
        confirmButtonText: missing.length ? '确认交付（含空屏）' : '确认交付',
        cancelButtonText: missing.length ? '先去出图' : '再想想',
        type: 'warning'
      }
    );
  } catch {
    return; // 人取消
  }
  confirming.value = true;
  try {
    const res = await confirmDelivery(taskId.value, artifact?.id, '终审通过，确认交付', missing.length > 0);
    delivery.value = res.data || delivery.value;
    ElMessage.success('已确认交付，项目置为「已完成」');
    await loadAll();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '确认交付失败');
  } finally {
    confirming.value = false;
  }
}

async function doPreview(row: DpDetailPageVersionVO) {
  previewingId.value = String(row.id);
  previewVisible.value = true;
  try {
    const url = await fetchDetailPreviewBlobUrl(taskId.value, row.id);
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = url;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取长图失败');
  } finally {
    previewingId.value = '';
  }
}

function closePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = '';
}

async function doVersionReview(row: DpDetailPageVersionVO, approve: boolean) {
  if (!approve) {
    try {
      await ElMessageBox.confirm('打回后这一版需要重新渲染或修改。确认打回？', '终审打回', { type: 'warning' });
    } catch {
      return;
    }
  }
  reviewingId.value = String(row.id);
  try {
    await reviewDetailVersion(taskId.value, row.id, approve, approve ? '终审通过' : '终审打回');
    ElMessage.success(approve ? '已通过，可进入人工精修' : '已打回');
    await loadAll();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '终审失败');
  } finally {
    reviewingId.value = '';
  }
}

async function doUploadFinal(options: UploadRequestOptions) {
  // C9：空屏交付先让人明确表态。后端也会拦一次（此处只是把话说在点按钮之前），
  // 因为"排到一半先交"是真实业务，要拦的是"没注意到还有屏是空的"。
  const missing = detailPage.value?.screensWithoutSelection || [];
  let acknowledgeShortfall = false;
  if (missing.length) {
    try {
      await ElMessageBox.confirm(
        `还有 ${missing.length} 屏没有已选定的产出图（屏号：${missing.join('、')}）。` +
          '现在上传等于「带空屏交付」——排版稿上这几屏是空白的。确认仍要交付？',
        '带空屏交付确认',
        { type: 'warning', confirmButtonText: '确认交付', cancelButtonText: '先去出图' }
      );
      acknowledgeShortfall = true;
    } catch {
      return;
    }
  }
  uploadingFinal.value = true;
  try {
    const res = await uploadDetailFinal(
      taskId.value,
      options.file as File,
      '人工精修最终版',
      acknowledgeShortfall
    );
    // C10：尺寸不一致只警告不拦——但必须让人看见（后端同时写进版本备注与事件）
    const sizeWarning = res.data?.finalSizeWarning;
    if (sizeWarning) {
      await ElMessageBox.alert(sizeWarning, '终版尺寸与输出规格不一致', { type: 'warning' });
    } else {
      ElMessage.success('最终版已上传并登记为 V1.0');
    }
    await loadAll();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '上传失败');
  } finally {
    uploadingFinal.value = false;
  }
}

async function doSubmit() {
  submitting.value = true;
  try {
    const res = await submitVisualGate(taskId.value);
    gate.value = res.data || null;
    ElMessage.success('已提交视觉门，等待人工确认');
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '提交失败');
  } finally {
    submitting.value = false;
  }
}

/**
 * 去补某个未满足的门禁项（v1 反馈：未满足项原先只说不满足，没有去处）。
 *
 * <p>地址由组件按「未满足项 → 页面」对照拼好（`composables/gateFixTarget.ts`），
 * 这里只负责跳转——与项目页「去内容任务」同一套做法（`window.open(..., '_self')`）。</p>
 *
 * @param route 目标地址
 */
function doGoFix(route: string) {
  if (!route) {
    return;
  }
  window.open(route, '_self');
}

/**
 * 人工确认 / 打回视觉门（意见由 GatePanel 随事件交回）。
 *
 * @param option CONFIRM=确认放行 / BLOCK=打回
 * @param comment 审核意见（可空）
 */
async function doReview(option: 'CONFIRM' | 'BLOCK', comment?: string) {
  if (option === 'BLOCK') {
    try {
      await ElMessageBox.confirm(
        '打回会把视觉方案退回，并阻断内容任务的流转（内容侧会出现一张阻断卡）。确认打回？',
        '打回视觉方案',
        { type: 'warning' }
      );
    } catch {
      return;
    }
  }
  reviewing.value = option;
  try {
    const res = await reviewVisualGate(taskId.value, option, comment || undefined);
    gate.value = res.data || null;
    ElMessage.success(option === 'CONFIRM' ? '已确认，出图已放行' : '已打回');
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '处理失败');
  } finally {
    reviewing.value = '';
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

/* 页头（R40）：留在页面上；三步内容的样式搬进了各自组件 */
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

.long-preview {
  display: grid;
  place-items: center;
  max-height: 70vh;
  overflow-y: auto;
}
.long-preview img {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.muted {
  margin: 0 0 6px;
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
