<template>
  <div class="direction-board">
    <p v-if="!hasProjects" class="empty">还没有视觉项目。先到「视觉项目」页新建一个。</p>
    <section v-else class="panel" data-board-section="DIRECTIONS">
      <div class="block-head">
        <!-- v1 反馈 1.3「方向的每一个卡片都以中文展示，不要以英文或者编号去展示」：
             标题里原先带「（A/B/C）」，卡片头上还印一个 A/B/C 的 code。编号现在只在悬停时出现
             （口头指认还用得上），页面上不再展示。 -->
        <h3>{{ stepHeading }}视觉方向</h3>
        <div class="head-actions">
          <span class="muted">来源：{{ DIRECTION_SOURCE_LABELS[templateSource] || templateSource || '—' }}</span>
          <el-button type="primary" :loading="generating" @click="$emit('generate')">
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
          <div
            class="direction-head"
            :title="item.directionCode ? `方向 ${item.directionCode}（页面上不再展示编号，仅悬停可见）` : undefined"
          >
            <!-- v1 裁定 ⑨：重新生成会留下多组同名 A/B/C，必须一眼看出哪组是哪次生成的 -->
            <span v-if="item.batchNo" class="batch" :title="batchTitle(item)">
              第 {{ item.batchNo }} 轮
            </span>
            <span class="name">{{ item.directionName }}</span>
            <el-tag v-if="item.status === 'SELECTED'" type="success" size="small">已选定</el-tag>
            <el-tag v-else-if="item.status === 'REJECTED'" type="info" size="small">已弃用</el-tag>
          </div>
          <p class="concept">{{ item.concept }}</p>
          <ul class="strategy-list">
            <li v-for="key in strategyKeys(item)" :key="key">
              <span class="key" :class="{ diff: (item.differences || []).includes(key) }">
                {{ strategyLabel(key) }}
              </span>
              <span class="value">{{ strategyValue(item, key) }}</span>
            </li>
          </ul>
          <div class="direction-actions">
            <el-button
              size="small"
              type="primary"
              :disabled="item.status === 'SELECTED'"
              :loading="selectingId === String(item.id)"
              @click="$emit('select', item)"
            >
              {{ item.status === 'SELECTED' ? '当前方向' : '选定这个方向' }}
            </el-button>
            <!-- FIX-004：已选定的方向是后续 DNA/分镜/排版的基准，不允许原地改文案；
                 要改就「重新生成方向」得到新版本再重新选定（后端也会拒绝，这里只是提前说明） -->
            <el-tooltip
              :disabled="item.status !== 'SELECTED'"
              content="已选定的方向不能原地修改：它是后续分镜与排版的基准。要改请点上方「重新生成方向」得到新版本"
              placement="top"
            >
              <span>
                <el-button
                  size="small"
                  text
                  :disabled="item.status === 'SELECTED'"
                  @click="$emit('edit', item)"
                >
                  编辑文案
                </el-button>
              </span>
            </el-tooltip>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { DpVisualDirectionVO } from '@/api/creative/types';
import { DIRECTION_SOURCE_LABELS } from '@/api/creative/types';
import {
  directionStrategyKeys,
  directionStrategyLabel,
  directionStrategyValue
} from '../../composables/directionStrategy';
import { useStepHeading } from '../../composables/stepNumbering';

/** 标题编号：本页步骤号（v1 反馈；没有工作台上下文时不显示编号） */
const stepHeading = useStepHeading('DirectionBoard');

/**
 * 「视觉方向」这一步的内容（V0.2 R39，装配组件名 `DirectionBoard`）。
 *
 * <p><b>纯展示</b>：方向卡片的渲染 + 三个动作（生成 / 选定 / 编辑文案）发事件回页面。
 * 编辑弹窗**留在页面**（与 R32/R33 的项目页区块同一套做法）：弹窗是"页面级的浮层"，
 * 而且"保存成功后关弹窗 + 刷新 + 推进指引线"这三件事必须在一起，放在页面才不会有半个状态。</p>
 *
 * @author creative
 */
defineProps<{
  /** 三个方向（含已选定/已弃用状态） */
  directions: DpVisualDirectionVO[];
  /** 方向模板来源（展示用：内置模板 / 模型） */
  templateSource: string;
  /** 正在生成方向 */
  generating: boolean;
  /** 正在选定的方向 id（按钮 loading 用） */
  selectingId: string;
  /** 项目列表是否非空（空列表要引导去新建项目，而不是显示"还没有方向"） */
  hasProjects: boolean;
}>();

defineEmits<{
  /** 生成 / 重新生成方向 */
  (e: 'generate'): void;
  /** 选定某个方向 */
  (e: 'select', row: DpVisualDirectionVO): void;
  /** 打开编辑弹窗（弹窗与落库都在页面） */
  (e: 'edit', row: DpVisualDirectionVO): void;
}>();

/**
 * 策略维度（去掉 schema / differences 这类非"取舍"字段）。
 *
 * v1 反馈：原来这里直接 `Object.keys(strategy)` 并原样渲染键名，卡片上于是出现
 * `background / scene / lighting / composition / mood / productRatio / …` 一串英文标识符。
 * 中文标签与阅读顺序统一由 `composables/directionStrategy.ts` 给（纯函数，有单测）。
 *
 * @param item 方向
 * @returns 维度键（中文顺序）
 */
function strategyKeys(item: DpVisualDirectionVO): string[] {
  return directionStrategyKeys(item.strategy);
}

/** 维度的中文标签（认不出的键原样返回，不静默丢） */
function strategyLabel(key: string): string {
  return directionStrategyLabel(key);
}

/** 维度的展示值（缺值给空串，不显示 "undefined"） */
function strategyValue(item: DpVisualDirectionVO, key: string): string {
  return directionStrategyValue(item.strategy, key);
}

/**
 * 「第 N 轮」标记的悬停说明。
 *
 * v1 裁定 ⑤「可以复现，但每次生成都要有差异化」：每次「重新生成方向」都会换一版拍法
 * （机位、光比、场景处理、氛围），但同一颗种子必然产出逐字相同的一套。
 * 种子是一串数字，放在卡片正文里会成为噪音（正文全是设计同事要读的取舍内容），
 * 所以只在悬停时如实给出——想说清"这一轮是哪一版"的人随时能看到。
 *
 * 历史数据没有种子（那时还没有差异化逻辑），这里如实说"没有记录"，不编一个数字。
 *
 * @param item 方向
 * @returns 悬停提示文案
 */
function batchTitle(item: DpVisualDirectionVO): string {
  const head = `第 ${item.batchNo} 轮生成的方向`;
  if (item.variantSeed == null) {
    return `${head}（生成时还没有差异种子记录）`;
  }
  return `${head}；差异种子 ${item.variantSeed}——同一颗种子重新生成会得到完全相同的结果，换一轮则必然不同`;
}
</script>

<style scoped lang="scss">
/* 这一步的内容样式（R39 从 storyboard/index.vue 搬过来，一字未改） */
.direction-board {
  display: block;
}

/* .panel 的外观统一在全局 creative-studio.scss（第 33 轮收口：这里原有一份逐字相同的副本） */
.panel h3 {
  margin: 0;
  font-size: 15px;
}
/* .block-head 统一在全局 creative-studio.scss（第 33 轮收口：原副本与它权重相同、只靠注入顺序取胜） */
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
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
/* 「第几轮」标记：说明性的小标签，不抢方向名的位置 */
.direction-head .batch {
  flex: 0 0 auto;
  padding: 1px 7px;
  font-size: 11px;
  color: var(--t2);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--line);
  border-radius: 999px;
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
</style>
