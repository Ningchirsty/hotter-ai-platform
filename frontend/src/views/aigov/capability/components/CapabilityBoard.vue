<template>
  <div class="cap-board">
    <div class="cap-board__metrics">
      <div class="cap-board__metric cap-board__metric--blue">
        <div class="k">业务能力</div>
        <div class="v">{{ rows.length }} <span>个</span></div>
        <div class="d">{{ rows.filter((row) => (row.issueCodes || []).length === 0).length }} 个链路完整</div>
      </div>
      <div class="cap-board__metric cap-board__metric--teal">
        <div class="k">模型候选</div>
        <div class="v">{{ totalCandidates }} <span>条</span></div>
        <div class="d">{{ rows.filter((row) => (row.candidateCount ?? 0) === 0).length }} 个能力无候选</div>
      </div>
      <div class="cap-board__metric cap-board__metric--amber">
        <div class="k">近 24h 调用</div>
        <div class="v">{{ totalCalls }} <span>次</span></div>
        <div class="d">外发与失败见下表</div>
      </div>
      <div class="cap-board__metric cap-board__metric--yellow">
        <div class="k">近 24h 失败率</div>
        <div class="v">{{ failRate }} <span>%</span></div>
        <div class="d">失败 {{ totalFails }} 次</div>
      </div>
    </div>

    <div class="cap-board__panels">
      <el-card shadow="never">
        <template #header>
          <div class="cap-board__panel-head">
            <b>调用最多</b>
            <span>按近 24h 调用次数</span>
          </div>
        </template>
        <ul v-if="topCalls.length > 0" class="cap-board__rank">
          <li v-for="(row, index) in topCalls" :key="row.capabilityCode">
            <span class="no">{{ index + 1 }}</span>
            <div class="body">
              <div class="line">{{ row.capabilityName || row.capabilityCode }}</div>
              <div class="bar"><i :style="{ width: barWidth(row.calls24h, topCalls[0].calls24h) }" /></div>
            </div>
            <span class="n">{{ row.calls24h ?? 0 }}</span>
          </li>
        </ul>
        <el-empty v-else description="近 24h 没有任何调用" :image-size="60" />
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="cap-board__panel-head">
            <b>失败最多</b>
            <span>按近 24h 失败次数（排查优先级）</span>
          </div>
        </template>
        <ul v-if="topFails.length > 0" class="cap-board__rank cap-board__rank--danger">
          <li v-for="(row, index) in topFails" :key="row.capabilityCode">
            <span class="no">{{ index + 1 }}</span>
            <div class="body">
              <div class="line">{{ row.capabilityName || row.capabilityCode }}</div>
              <div class="bar"><i :style="{ width: barWidth(row.fails24h, topFails[0].fails24h) }" /></div>
            </div>
            <span class="n">{{ row.fails24h ?? 0 }}</span>
          </li>
        </ul>
        <el-empty v-else description="近 24h 没有失败" :image-size="60" />
      </el-card>
    </div>

    <el-card shadow="never" class="cap-board__table">
      <template #header>
        <div class="cap-board__panel-head">
          <b>全部能力（表格视图）</b>
          <span>同一份数据：给人看用卡片，排障用这张表</span>
        </div>
      </template>
      <el-table :data="rows" border size="small">
        <el-table-column label="能力" min-width="200">
          <template #default="{ row }">
            <div>{{ row.capabilityName || row.capabilityCode }}</div>
            <div class="cap-board__mono">{{ row.capabilityCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="链路" width="200">
          <template #default="{ row }">
            <el-tag v-for="tag in issueTags(row.issueCodes)" :key="tag.label" size="small" :type="tag.type">
              {{ tag.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="候选" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="healthMeta(row.candidateHealthAgg).type">
              {{ row.candidateCount ?? 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="策略覆盖" width="120">
          <template #default="{ row }">{{ coverageText(row.policyCoveredLevels, row.policyTotalLevels) }}</template>
        </el-table-column>
        <el-table-column prop="calls24h" label="近 24h" width="90" />
        <el-table-column prop="fails24h" label="失败" width="80" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="emit('open', row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { AigCapabilityOverviewVO } from '@/api/aigov/capability/overview-types';
import { coverageText, healthMeta, issueTags } from '../presentation';

/**
 * 能力看板（P0）。
 *
 * <p><b>数据来源与诚实边界</b>：四个指标、两个排行榜与表格视图都来自**同一份**
 * {@code GET /aigov/capability/overview}（近 24h 口径由服务端算好）。
 * 这里刻意<b>不画"按天趋势"</b>：那需要一个新的按天聚合接口，拿分页的审计列表在客户端拼趋势
 * 会得出错的曲线 —— 宁可先不做，也不给一个看起来专业的错图。</p>
 */
const props = defineProps<{
  rows: AigCapabilityOverviewVO[];
}>();

const emit = defineEmits<{
  (event: 'open', row: AigCapabilityOverviewVO): void;
}>();

const totalCandidates = computed(() =>
  (props.rows || []).reduce((sum, row) => sum + (row.candidateCount ?? 0), 0)
);
const totalCalls = computed(() => (props.rows || []).reduce((sum, row) => sum + (row.calls24h ?? 0), 0));
const totalFails = computed(() => (props.rows || []).reduce((sum, row) => sum + (row.fails24h ?? 0), 0));
const failRate = computed(() =>
  totalCalls.value === 0 ? '0.0' : ((totalFails.value / totalCalls.value) * 100).toFixed(1)
);

const topCalls = computed(() =>
  [...(props.rows || [])]
    .filter((row) => (row.calls24h ?? 0) > 0)
    .toSorted((a, b) => (b.calls24h ?? 0) - (a.calls24h ?? 0))
    .slice(0, 5)
);
const topFails = computed(() =>
  [...(props.rows || [])]
    .filter((row) => (row.fails24h ?? 0) > 0)
    .toSorted((a, b) => (b.fails24h ?? 0) - (a.fails24h ?? 0))
    .slice(0, 5)
);

const barWidth = (value?: number, max?: number) => {
  const top = max ?? 0;
  if (!value || top <= 0) {
    return '4%';
  }
  return `${Math.max(6, Math.round((value / top) * 100))}%`;
};
</script>

<style scoped>
.cap-board {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cap-board__metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.cap-board__metric {
  border-radius: 12px;
  padding: 14px 16px;
  color: #fff;
}

.cap-board__metric--blue {
  background: linear-gradient(135deg, #2b63f0, #4c8dff);
}

.cap-board__metric--teal {
  background: linear-gradient(135deg, #12a8a8, #3fc9c9);
}

.cap-board__metric--amber {
  background: linear-gradient(135deg, #e08700, #ffae33);
}

.cap-board__metric--yellow {
  background: linear-gradient(135deg, #e0a800, #ffc93c);
}

.cap-board__metric .k {
  font-size: 13px;
  opacity: 0.94;
}

.cap-board__metric .v {
  font-size: 28px;
  font-weight: 700;
  margin: 6px 0 2px;
}

.cap-board__metric .v span {
  font-size: 13px;
  font-weight: 500;
}

.cap-board__metric .d {
  font-size: 12px;
  opacity: 0.94;
}

.cap-board__panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.cap-board__panel-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.cap-board__panel-head span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cap-board__rank {
  list-style: none;
  margin: 0;
  padding: 0;
}

.cap-board__rank li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}

.cap-board__rank .no {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cap-board__rank--danger .no {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.cap-board__rank .body {
  flex: 1;
}

.cap-board__rank .line {
  font-size: 13px;
}

.cap-board__rank .bar {
  height: 6px;
  border-radius: 3px;
  background: var(--el-fill-color-light);
  margin-top: 5px;
  overflow: hidden;
}

.cap-board__rank .bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #2b63f0, #4c8dff);
}

.cap-board__rank--danger .bar i {
  background: linear-gradient(90deg, #d93025, #ef6b5f);
}

.cap-board__rank .n {
  width: 54px;
  text-align: right;
  font-weight: 600;
}

.cap-board__mono {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1200px) {
  .cap-board__panels {
    grid-template-columns: 1fr;
  }
}
</style>
