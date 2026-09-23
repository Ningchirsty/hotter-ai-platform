<template>
  <div class="studio">
    <header class="page-head">
      <div>
        <h2>详情页与审核</h2>
        <p class="muted">
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

    <p v-if="!projects.length" class="empty">还没有视觉项目。先到「视觉项目」页新建一个。</p>

    <template v-else-if="gate">
      <!-- 门禁状态 -->
      <section class="panel">
        <div class="gate-head">
          <div>
            <h3>
              视觉门
              <el-tag v-if="gate.passed" type="success" effect="dark">已通过</el-tag>
              <el-tag v-else-if="gate.cardStatus === 'PENDING'" type="warning" effect="dark">待人工确认</el-tag>
              <el-tag v-else-if="gate.cardStatus === 'BLOCKED'" type="danger" effect="dark">已打回</el-tag>
              <el-tag v-else type="info" effect="dark">未提交</el-tag>
            </h3>
            <p class="muted">
              当前视觉阶段：{{ gate.stageDesc }}（{{ gate.stage }}）
              <template v-if="gate.cardId">　确认项卡号：{{ gate.cardId }}</template>
            </p>
          </div>
          <div class="gate-actions">
            <el-button
              type="primary"
              :disabled="!gate.submittable"
              :loading="submitting"
              @click="doSubmit"
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
      <section class="panel">
        <div class="block-head">
          <h3>准入项</h3>
          <span class="muted">硬性项（BLOCK）不满足时不能提交；建议项（CONDITION）只提示</span>
        </div>
        <el-table :data="gate.items" size="small">
          <el-table-column label="等级" width="110">
            <template #default="{ row }">
              <el-tag :type="asItem(row).level === 'BLOCK' ? 'danger' : 'info'" size="small">
                {{ asItem(row).level === 'BLOCK' ? '硬性' : '建议' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="label" label="准入项" width="200" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <span :class="asItem(row).passed ? 'good' : 'bad'">{{ asItem(row).passed ? '已满足' : '未满足' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="依据 / 说明" min-width="360" show-overflow-tooltip />
        </el-table>
      </section>

      <!-- 人工确认 -->
      <section class="panel">
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
              @click="doReview('CONFIRM')"
            >
              确认方案，允许出图
            </el-button>
            <el-button
              type="danger"
              plain
              :disabled="gate.cardStatus !== 'PENDING'"
              :loading="reviewing === 'BLOCK'"
              @click="doReview('BLOCK')"
            >
              打回
            </el-button>
          </div>
        </div>
        <p v-if="gate.cardStatus !== 'PENDING'" class="muted">
          当前没有待确认项：{{ gate.cardStatus === 'RESOLVED' ? '已确认通过' : (gate.cardStatus === 'BLOCKED' ? '已被打回，请修改方案后重新提交' : '请先提交视觉门审核') }}
        </p>
      </section>

      <!-- 后续阶段占位说明 -->
      <section class="panel">
        <div class="block-head">
          <h3>后续（R3）</h3>
        </div>
        <p class="muted">
          机排版 V0.8、人工精修与终审在 R3 交付：由独立渲染服务把逐屏结果编成 750×N 长图，
          再走整页质检与终审。当前页面只承载视觉门，避免把未交付的功能混进审核页。
        </p>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  getVisualGate,
  listCreativeProject,
  reviewVisualGate,
  submitVisualGate
} from '@/api/creative';
import type { CreativeProjectVO, GateEvaluationVO, GateItem } from '@/api/creative/types';

const projects = ref<CreativeProjectVO[]>([]);
const taskId = ref('');
const gate = ref<GateEvaluationVO | null>(null);
const loading = ref(false);
const submitting = ref(false);
const reviewing = ref('');
const comment = ref('');

function asItem(row: unknown): GateItem {
  return row as GateItem;
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
    const res = await getVisualGate(taskId.value);
    gate.value = res.data || null;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载视觉门失败');
  } finally {
    loading.value = false;
  }
}

async function doSubmit() {
  submitting.value = true;
  try {
    const res = await submitVisualGate(taskId.value);
    gate.value = res.data || null;
    ElMessage.success('已提交视觉门，等待人工确认');
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '提交失败');
  } finally {
    submitting.value = false;
  }
}

async function doReview(option: 'CONFIRM' | 'BLOCK') {
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
    const res = await reviewVisualGate(taskId.value, option, comment.value || undefined);
    gate.value = res.data || null;
    ElMessage.success(option === 'CONFIRM' ? '已确认，出图已放行' : '已打回');
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

.block-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.gate-alert {
  margin-top: 12px;
}
.issue-list {
  padding-left: 18px;
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.9;
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
