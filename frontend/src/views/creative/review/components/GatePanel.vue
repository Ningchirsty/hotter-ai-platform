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
            硬性项（BLOCK）不满足时不能提交；建议项（CONDITION）只提示。
            「品牌 Brief 已填写并确认」「已声明禁用词与合规红线」是品牌方的要求，
            <b>等级以下表「等级」列为准</b>——文案里不写死等级，避免配置改了文案还在说旧话。
          </span>
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
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { GateEvaluationVO, GateItem } from '@/api/creative/types';

/**
 * 「视觉门」这一步的内容（V0.2 R40，装配组件名 `GatePanel`）。
 *
 * <p><b>纯展示 + 一个自带输入</b>：门禁状态、准入项、人工确认三块。审核意见是**这一步自己的输入**，
 * 所以由组件持有并随事件一起交回页面（`review(action, comment)`）——页面负责调接口、
 * 提示、刷新与推进指引线。</p>
 *
 * @author creative
 */
defineProps<{
  /** 视觉门评估（没取到时为 null） */
  gate: GateEvaluationVO | null;
  /** 项目列表是否非空（空列表要引导去新建项目） */
  hasProjects: boolean;
  /** 正在提交视觉门 */
  submitting: boolean;
  /** 正在处理的审核动作（'CONFIRM' / 'BLOCK'，用于按钮 loading） */
  reviewing: string;
}>();

const emit = defineEmits<{
  /** 提交视觉门审核 */
  (e: 'submit'): void;
  /** 人工确认或打回（意见随事件交回页面） */
  (e: 'review', action: 'CONFIRM' | 'BLOCK', comment: string): void;
}>();

/** 审核意见（这一步自己的输入；只有本次操作有效，不落库直到点了确认/打回） */
const comment = ref('');

function asItem(row: unknown): GateItem {
  return row as GateItem;
}

/**
 * 发审核动作（页面负责确认弹窗与落库）。
 *
 * @param action CONFIRM=确认放行 / BLOCK=打回
 */
function review(action: 'CONFIRM' | 'BLOCK') {
  emit('review', action, comment.value);
}
</script>

<style scoped lang="scss">
/* 这一步的内容样式（R40 从 review/index.vue 搬过来，一字未改） */
.gate-panel {
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
</style>
