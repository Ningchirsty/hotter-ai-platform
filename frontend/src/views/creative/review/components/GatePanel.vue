<template>
  <div class="gate-panel">
    <p v-if="!hasProjects" class="empty">还没有视觉项目。先到「视觉项目」页新建一个。</p>
    <p v-else-if="!gate" class="empty">视觉门评估还没取到（可能还在加载）。</p>

    <template v-else>
      <!-- 门禁状态 -->
      <section class="panel" data-gate-section="STATUS">
        <div class="gate-head">
          <div>
            <h3>
              视觉门
              <el-tag v-if="gate.passed" type="success" effect="dark">已通过</el-tag>
              <el-tag v-else-if="gate.cardStatus === 'PENDING'" type="warning" effect="dark">待人工确认</el-tag>
              <el-tag v-else-if="gate.cardStatus === 'BLOCKED'" type="danger" effect="dark">已打回</el-tag>
              <el-tag v-else type="info" effect="dark">未提交</el-tag>
            </h3>
            <p v-if="gate.cardId" class="muted">
              确认项卡号：{{ gate.cardId }}
            </p>
          </div>
          <div class="gate-actions">
            <el-button
              type="primary"
              :disabled="!gate.submittable"
              :loading="submitting"
              @click="$emit('submit')"
            >
              {{ gate.cardStatus === 'PENDING' ? '重新提交（已有待确认项）' : '提交视觉门审核' }}
            </el-button>
          </div>
        </div>

        <el-alert
          v-if="!gate.submittable"
          type="warning"
          show-icon
          :closable="false"
          class="gate-alert"
          title="硬性项未满足，暂不能提交"
        >
          <ul class="issue-list">
            <li v-for="(item, index) in gate.blocked" :key="index">{{ item }}</li>
          </ul>
          <!-- v1 反馈：这里原先是纯文字清单——说了"哪儿不行"，没说"去哪儿补"，
               于是点哪儿都像没反应。补法在下面「准入项」表的「去哪儿补」列。 -->
          <p class="issue-next">每一项该去哪儿补，见下方「准入项」表的<b>去哪儿补</b>列。</p>
        </el-alert>
        <el-alert
          v-else-if="!gate.passed"
          type="info"
          show-icon
          :closable="false"
          class="gate-alert"
          title="硬性项已满足，可提交人工确认"
        />
      </section>

      <!-- 准入项 -->
      <section class="panel" data-gate-section="ITEMS">
        <div class="block-head">
          <h3>准入项</h3>
          <span class="muted">
            硬性项不满足时不能提交；建议项只提示。
            「品牌 Brief 已填写并确认」「已声明禁用词与合规红线」是品牌方的要求，
            <b>等级以下表「等级」列为准</b>——文案里不写死等级，避免配置改了文案还在说旧话。
          </span>
        </div>
        <el-table :data="gate.items" size="small">
          <el-table-column label="等级" width="110">
            <template #default="{ row }">
              <el-tag :type="asItem(row).level === 'BLOCK' ? 'danger' : 'info'" size="small">
                {{ gateLevelLabel(asItem(row).level) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="label" label="准入项" width="200" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <span :class="asItem(row).passed ? 'good' : 'bad'">{{ asItem(row).passed ? '已满足' : '未满足' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="依据 / 说明" min-width="320" show-overflow-tooltip />
          <el-table-column label="去哪儿补" width="200">
            <template #default="{ row }">
              <el-button
                v-if="fixLabel(asItem(row))"
                size="small"
                text
                type="primary"
                @click="goFix(asItem(row))"
              >
                {{ fixLabel(asItem(row)) }}
              </el-button>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <!-- 人工确认 -->
      <section class="panel" data-gate-section="REVIEW">
        <div class="block-head">
          <h3>人工确认</h3>
          <span class="muted">确认后出图放行；打回会阻断内容任务流转并回到视觉门</span>
        </div>
        <div class="review-row">
          <el-input
            v-model="comment"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="意见（打回时建议写明要改什么）"
          />
          <div class="review-actions">
            <el-button
              type="success"
              :disabled="gate.cardStatus !== 'PENDING'"
              :loading="reviewing === 'CONFIRM'"
              @click="review('CONFIRM')"
            >
              确认方案，允许出图
            </el-button>
            <el-button
              type="danger"
              plain
              :disabled="gate.cardStatus !== 'PENDING'"
              :loading="reviewing === 'BLOCK'"
              @click="review('BLOCK')"
            >
              打回
            </el-button>
          </div>
        </div>
        <p v-if="gate.cardStatus !== 'PENDING'" class="muted">
          当前没有待确认项：{{ gate.cardStatus === 'RESOLVED' ? '已确认通过' : (gate.cardStatus === 'BLOCKED' ? '已被打回，请修改方案后重新提交' : '请先提交视觉门审核') }}
        </p>
      </section>

      <!--
        上传资料并识别（v1 人工测试反馈 详情页与审核 1.3）。
        原文：「闸门结论版块除了手动录入信息之外还应该有上传信息自动识别录入的功能」；
        裁定：「在闸门里做上传+识别」。
        **识别不在这里做**：上传落内容侧同一张附件表，识别走内容侧那条「以待确认落库」的链，
        这一块只给入口、触发与状态——两套识别一定会给出两个答案。
      -->
      <section class="panel" data-gate-section="MATERIALS">
        <div class="block-head">
          <h3>
            上传资料并识别
            <span class="muted">
              {{ materialSummary }}
            </span>
          </h3>
          <div class="head-actions">
            <el-button
              size="small"
              plain
              :loading="materialsLoading"
              @click="loadMaterials"
            >
              刷新状态
            </el-button>
          </div>
        </div>
        <p class="hint">
          除了手动录入，也可以把资料（pdf / doc / docx / txt / md / csv / xlsx / pptx / 图片，≤ 20MB）
          传到这里点「识别」。识别由<b>内容侧的文档解析能力</b>完成，结果一律<b>以待确认落库</b>——
          只有人工确认的值才会进入开工包，所以识别完要去确认一遍。
        </p>
        <div class="material-row">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            :limit="1"
            accept=".pdf,.doc,.docx,.txt,.md,.csv,.xls,.xlsx,.ppt,.pptx,.png,.jpg,.jpeg,.webp"
            :on-change="onMaterialPick"
            :on-exceed="() => ElMessage.warning('一次传一份：先识别完这一份，再传下一份')"
          >
            <el-button size="small" plain :loading="uploading">
              {{ pickedFile ? `已选：${pickedFile.name}` : '选择资料文件' }}
            </el-button>
          </el-upload>
          <el-button
            size="small"
            type="primary"
            :loading="uploading"
            :disabled="!pickedFile"
            @click="doUploadMaterial"
          >
            上传
          </el-button>
          <el-button
            size="small"
            type="primary"
            plain
            :loading="parsing"
            :disabled="!materials.files?.length"
            @click="doParseMaterials"
          >
            识别
          </el-button>
        </div>
        <ul v-if="materials.files?.length" class="material-list">
          <li v-for="item in materials.files" :key="String(item.fileId)">
            <span class="name">{{ item.fileName }}</span>
            <el-tag
              size="small"
              :type="item.parseStatus === 'DONE' ? 'success'
                : (item.parseStatus === 'FAILED' ? 'danger' : 'info')"
            >
              {{ item.parseStatusDesc || item.parseStatus }}
            </el-tag>
            <!-- 失败/跳过要带原因：只显示"失败"等于让人去猜 -->
            <span v-if="item.parseMessage" class="reason">{{ item.parseMessage }}</span>
          </li>
        </ul>
        <p v-else class="empty">
          还没有上传资料。传一份产品资料/品牌规范，点「识别」就能把里面的信息识别成待确认事实。
        </p>
        <p v-if="materials.pendingFacts" class="issue-next">
          识别到 <b>{{ materials.pendingFacts }}</b> 条<b>待确认</b>事实（已确认 {{ materials.confirmedFacts }} 条）——
          这些还没进开工包。
          <el-button size="small" text type="primary" @click="gotoConfirmFacts">
            去内容任务详情确认事实
          </el-button>
        </p>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { GateEvaluationVO, GateItem, GateMaterialsVO } from '@/api/creative/types';
import { getGateMaterials, parseGateMaterials, uploadGateMaterial } from '@/api/creative';
import { CONFIRM_FACTS_TARGET, gateFixRoute, gateFixTarget } from '../../composables/gateFixTarget';
import { gateLevelLabel } from '../../composables/gateLabels';
import { extractErrorMessage } from '@/utils/request';

/**
 * 「视觉门」这一步的内容（V0.2 R40，装配组件名 `GatePanel`）。
 *
 * <p><b>纯展示 + 一个自带输入</b>：门禁状态、准入项、人工确认三块。审核意见是**这一步自己的输入**，
 * 所以由组件持有并随事件一起交回页面（`review(action, comment)`）——页面负责调接口、
 * 提示、刷新与推进指引线。</p>
 *
 * <p><b>V1 反馈补的「去哪儿补」</b>：未满足项原先只说"未满足"。现在每一项（未满足且认识该码的）
 * 给一个跳转到"补它的地方"的按钮；跳转由页面执行（组件不自己导航，与本仓其它组件一致）。
 * 认不出的码不给按钮——宁可不给，也不指错路。</p>
 *
 * @author creative
 */
const props = defineProps<{
  /** 视觉门评估（没取到时为 null） */
  gate: GateEvaluationVO | null;
  /** 项目列表是否非空（空列表要引导去新建项目） */
  hasProjects: boolean;
  /** 正在提交视觉门 */
  submitting: boolean;
  /** 正在处理的审核动作（'CONFIRM' / 'BLOCK'，用于按钮 loading） */
  reviewing: string;
  /** 当前项目ID（拼"去哪儿补"的深链用） */
  taskId?: string | number | null;
}>();

const emit = defineEmits<{
  /** 提交视觉门审核 */
  (e: 'submit'): void;
  /** 人工确认或打回（意见随事件交回页面） */
  (e: 'review', action: 'CONFIRM' | 'BLOCK', comment: string): void;
  /** 去补某个未满足的准入项（地址已拼好，页面负责跳转） */
  (e: 'go-fix', route: string): void;
}>();

/** 审核意见（这一步自己的输入；只有本次操作有效，不落库直到点了确认/打回） */
const comment = ref('');

function asItem(row: unknown): GateItem {
  return row as GateItem;
}

/**
 * 一个准入项的「去哪儿补」按钮文案。
 *
 * 已满足的项不给按钮（不需要补）；认不出的码也不给（不指错路）。
 *
 * @param item 准入项
 * @returns 按钮文案；不需要/不认识时为空串
 */
function fixLabel(item: GateItem): string {
  if (item.passed) {
    return '';
  }
  return gateFixTarget(item.code)?.label || '';
}

/**
 * 去补这一项：把地址算好交给页面（组件不自己导航）。
 *
 * @param item 准入项
 */
function goFix(item: GateItem) {
  const target = gateFixTarget(item.code);
  if (!target) {
    return;
  }
  emit('go-fix', gateFixRoute(target, props.taskId));
}

/**
 * 发审核动作（页面负责确认弹窗与落库）。
 *
 * @param action CONFIRM=确认放行 / BLOCK=打回
 */
function review(action: 'CONFIRM' | 'BLOCK') {
  emit('review', action, comment.value);
}

// ------------------------------------------------------------------
// 上传资料并识别（v1 反馈 详情页与审核 1.3）
//
// 这一块自己读状态（组件已经有 taskId），页面不必再传一遍数据；
// 识别本身在内容侧跑，这里只负责：选文件 → 上传 → 触发 → 如实显示状态与待确认条数。
// ------------------------------------------------------------------

/** 这一块的现状（资料列表 + 待确认/已确认 + 最近解析时间） */
const materials = ref<GateMaterialsVO>({ files: [], pendingFacts: 0, confirmedFacts: 0 });
const materialsLoading = ref(false);
const uploading = ref(false);
const parsing = ref(false);
/** 已经选中但还没上传的文件（`:auto-upload="false"`，上传要人点一下） */
const pickedFile = ref<File | null>(null);

/** 标题右侧那句状态：几份资料、识别到几条待确认 */
const materialSummary = computed(() => {
  const total = materials.value.files?.length || 0;
  if (!total) {
    return '还没有资料';
  }
  return `${total} 份资料 · 待确认 ${materials.value.pendingFacts || 0} 条`;
});

/**
 * 读这一块的现状。
 *
 * <p>读不到不弹错：这块是"补充信息"，失败时把状态清空并如实显示"还没有资料"，
 * 不挡住视觉门本来的提交/审核动作。</p>
 */
async function loadMaterials(): Promise<void> {
  if (!props.taskId) {
    materials.value = { files: [], pendingFacts: 0, confirmedFacts: 0 };
    return;
  }
  materialsLoading.value = true;
  try {
    const res = await getGateMaterials(props.taskId);
    materials.value = res.data || { files: [], pendingFacts: 0, confirmedFacts: 0 };
  } catch {
    materials.value = { files: [], pendingFacts: 0, confirmedFacts: 0 };
  } finally {
    materialsLoading.value = false;
  }
}

/**
 * 选中文件（`:auto-upload="false"`：先记住，点「上传」才发）。
 *
 * @param file el-upload 的文件项
 */
function onMaterialPick(file: { raw?: File }): void {
  pickedFile.value = file?.raw ?? null;
}

/** 上传选中的资料（上传后再读一次状态，页面上看到的就是库里的真实状态） */
async function doUploadMaterial(): Promise<void> {
  if (!props.taskId || !pickedFile.value) {
    return;
  }
  uploading.value = true;
  try {
    await uploadGateMaterial(props.taskId, pickedFile.value);
    ElMessage.success(`已上传「${pickedFile.value.name}」：接着点「识别」把它识别成待确认事实`);
    pickedFile.value = null;
    await loadMaterials();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '上传资料失败');
  } finally {
    uploading.value = false;
  }
}

/** 触发识别（异步）：识别在内容侧跑，跑完刷新状态就能看到待确认条数 */
async function doParseMaterials(): Promise<void> {
  if (!props.taskId) {
    return;
  }
  parsing.value = true;
  try {
    await parseGateMaterials(props.taskId);
    ElMessage.success('已发起识别：结果会以「待确认事实」落库，去内容任务详情确认后才进开工包');
    // 解析是异步的：先读一次（能立刻看到"解析中"），过几秒再读一次拿结果
    await loadMaterials();
    window.setTimeout(() => void loadMaterials(), 6000);
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '发起识别失败');
  } finally {
    parsing.value = false;
  }
}

/** 去内容任务详情确认识别出来的事实（落点与「品牌调性」那一项同一个：事实卡） */
function gotoConfirmFacts(): void {
  emit('go-fix', gateFixRoute(CONFIRM_FACTS_TARGET, props.taskId));
}

onMounted(() => {
  void loadMaterials();
});
watch(
  () => props.taskId,
  () => {
    pickedFile.value = null;
    void loadMaterials();
  }
);
</script>

<style scoped lang="scss">
/* 这一步的内容样式（R40 从 review/index.vue 搬过来，一字未改） */
.gate-panel {
  display: block;
}

/* .panel 的外观统一在全局 creative-studio.scss（第 33 轮收口：这里原有一份逐字相同的副本） */
.panel h3 {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 0 0 6px;
  font-size: 15px;
}

.gate-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}
.gate-actions {
  display: flex;
  gap: 8px;
}

/* .block-head 统一在全局 creative-studio.scss（第 33 轮收口：原副本与它权重相同、只靠注入顺序取胜） */

.gate-alert {
  margin-top: 12px;
}
.issue-list {
  padding-left: 18px;
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.9;
}
.issue-next {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.8;
}

.review-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.review-row .el-textarea {
  flex: 1;
}
.review-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.good {
  color: #a7f3d0;
}
.bad {
  color: #fde68a;
}

.muted {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--t2);
}
.empty {
  padding: 12px 0;
  font-size: 13px;
  color: var(--t2);
}

/* 上传资料并识别（第 37 轮）：一行三个控件 + 一份资料清单 */
.material-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.material-list {
  padding: 0;
  margin: 10px 0 0;
  list-style: none;
  font-size: 12.5px;
  line-height: 1.9;
}
.material-list li {
  display: flex;
  gap: 8px;
  align-items: center;
}
.material-list .name {
  color: var(--t1);
}
.material-list .reason {
  color: var(--t3);
}
</style>
