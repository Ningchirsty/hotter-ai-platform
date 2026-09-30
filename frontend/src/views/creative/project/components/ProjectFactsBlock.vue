<template>
  <section class="block" data-block="PROJECT_FACTS">
    <div class="block-head">
      <h4>4. 事实确认</h4>
      <div class="block-actions">
        <span class="muted">已确认 {{ confirmedFacts.length }} 条 / 共 {{ facts.length }} 行</span>
        <el-button
          size="small"
          plain
          :loading="busy === 'confirmUnambiguous'"
          @click="$emit('confirm-unambiguous')"
        >
          一键确认无歧义项
        </el-button>
        <el-button size="small" type="primary" plain @click="$emit('manual-entry')">人工录入</el-button>
      </div>
    </div>
    <p class="hint">
      只有 <b>CONFIRMED</b> 的事实才会进入基因 / 方向 / 分镜文案的推导；PENDING 与已否决都不算。
    </p>
    <p v-if="error" class="fact-error">
      {{ error }}（点右上「刷新」重试，页面不会用默认值糊过去）
    </p>

    <p v-if="!fieldOptionsLoaded && !error" class="fact-error">
      字段选项接口没取到，无法判断闸门必填项是否齐备——不猜，请在下方事实表里逐条确认。
    </p>
    <template v-else>
      <!-- 闸门必填项：紧凑状态带。原先是竖排一行一项（9 项就吃掉大半屏），
           信息量一样但更省高度，也与页面其它「状态条」写法一致。 -->
      <div class="fact-gate">
        <span
          v-for="option in requiredOptions"
          :key="String(option.fieldCode)"
          class="gate-chip"
          :class="{ ok: option.satisfied }"
          :title="optionLabel(option)"
        >
          <span class="mark">{{ option.satisfied ? '✓' : '✗' }}</span>
          {{ option.fieldName || option.fieldCode }}
          <em class="gate-level">{{ option.gateLevel || '—' }}</em>
        </span>
        <span v-if="!requiredOptions.length" class="muted">该交付类型没有声明必填事实项。</span>
      </div>
      <div v-if="unsatisfiedOptions.length" class="block-actions">
        <el-button
          v-for="option in unsatisfiedOptions"
          :key="'fill-' + option.fieldCode"
          size="small"
          @click="$emit('manual-entry', option.fieldCode)"
        >
          ＋ 录入「{{ option.fieldName || option.fieldCode }}」
        </el-button>
      </div>
    </template>

    <!-- 事实清单：无框行列表（发丝分隔线 + 左侧状态色条），不用白色表格容器。
         为什么改：白底表格在暗色工作室里是一块突兀的亮面，且列宽固定会把
         「值/来源/原文摘录」挤成省略号；改成两行一行之后，值与来源都能直接读。 -->
    <div v-if="facts.length" class="fact-list">
      <div class="fact-tools">
        <button
          v-for="item in filters"
          :key="item.value"
          type="button"
          class="fact-filter"
          :class="{ active: filter === item.value }"
          @click="$emit('update:filter', item.value)"
        >
          {{ item.label }}<span class="count">{{ item.count }}</span>
        </button>
        <span class="muted fact-sort-note">待确认 / 冲突排在前面</span>
      </div>
      <div
        v-for="row in visibleFacts"
        :key="String(asFact(row).snapshotId)"
        class="fact-row"
        :class="'st-' + String(asFact(row).confirmStatus || 'PENDING').toLowerCase()"
      >
        <div class="fact-main">
          <div class="fact-line1">
            <span class="fact-name">{{ asFact(row).fieldName || asFact(row).fieldCode }}</span>
            <span class="fact-value">
              {{ asFact(row).fieldValue }}<span v-if="asFact(row).unit" class="muted"> {{ asFact(row).unit }}</span>
            </span>
            <span class="fact-status" :class="'is-' + statusType(asFact(row).confirmStatus)">
              {{ statusLabel(asFact(row).confirmStatus) }}
            </span>
          </div>
          <div class="fact-line2">
            <span class="fact-src">
              {{ asFact(row).sourceFileName || '—'
              }}<span v-if="asFact(row).sourceLocator" class="muted"> · {{ asFact(row).sourceLocator }}</span>
            </span>
            <span
              v-if="asFact(row).sourceExcerpt"
              class="fact-excerpt"
              :class="{ open: !!expanded[String(asFact(row).snapshotId)] }"
              :title="asFact(row).sourceExcerpt"
              @click="$emit('toggle-excerpt', asFact(row))"
            >
              原文：{{ asFact(row).sourceExcerpt }}
            </span>
            <span v-else class="muted">原文：—</span>
          </div>
        </div>
        <div class="fact-ops">
          <el-button
            link
            size="small"
            type="primary"
            :disabled="asFact(row).confirmStatus === 'CONFIRMED'"
            :loading="busy === 'fact-' + asFact(row).snapshotId"
            @click="$emit('confirm', asFact(row))"
          >
            确认
          </el-button>
          <el-button
            link
            size="small"
            type="danger"
            :disabled="asFact(row).confirmStatus === 'REJECTED'"
            :loading="busy === 'fact-' + asFact(row).snapshotId"
            @click="$emit('reject', asFact(row))"
          >
            驳回
          </el-button>
        </div>
      </div>
      <p v-if="!visibleFacts.length" class="empty">该筛选下没有事实行。</p>
    </div>
    <p v-else class="empty">
      还没有事实候选。资料解析后会自动落成待确认行；也可以点「人工录入」补齐。
    </p>
  </section>
</template>

<script setup lang="ts">
import type { CpFactFieldOptionVO, CpFactSnapshotVO } from '@/api/content/fact/types';
import type { TagType } from '@/api/creative/types';

/** 事实筛选页签（页面按状态统计好数量，组件只显示） */
export interface FactFilterMeta {
  value: string;
  label: string;
  count: number;
}

/**
 * 项目页区块④「事实确认」（V0.2 R32）。
 *
 * <p><b>事实的权威在内容域</b>：这一块只做"展示 + 确认/驳回"，不产生事实值
 * （SPEC 红线：参考图与 AI 结论都不得反向成为产品事实）。拆成组件后这条边界更明显：
 * 组件没有任何写事实字段的入口。</p>
 *
 * <p>筛选计数、可见行、展开状态都由页面算好传进来——`visibleFacts` 的排序口径
 * （待确认/冲突优先）改过两次，只该有一处实现。</p>
 *
 * @author creative
 */
defineProps<{
  /** 全部事实（用于标题计数） */
  facts: CpFactSnapshotVO[];
  /** 已确认的事实（用于标题计数） */
  confirmedFacts: CpFactSnapshotVO[];
  /** 当前筛选下的行（页面已筛选并排序） */
  visibleFacts: CpFactSnapshotVO[];
  /** 筛选页签与计数 */
  filters: FactFilterMeta[];
  /** 当前筛选 */
  filter: string;
  /** 闸门必填项 */
  requiredOptions: CpFactFieldOptionVO[];
  /** 未满足的必填项 */
  unsatisfiedOptions: CpFactFieldOptionVO[];
  /** 字段选项是否取到（没取到要如实说明"无法判断齐备"） */
  fieldOptionsLoaded: boolean;
  /** 闸门必填项的 hover 说明 */
  optionLabel: (option: CpFactFieldOptionVO) => string;
  /** 正在进行的动作（'fact-<id>' / 'confirmUnambiguous'） */
  busy: string;
  /** 读取失败原因（不静默） */
  error: string;
  /** 行收窄 */
  asFact: (row: unknown) => CpFactSnapshotVO;
  /** 状态文案与样式 */
  statusLabel: (status?: string) => string;
  statusType: (status?: string) => TagType;
  /** 原文摘录是否展开（按快照ID） */
  expanded: Record<string, boolean>;
}>();

defineEmits<{
  (e: 'update:filter', value: string): void;
  (e: 'confirm-unambiguous'): void;
  (e: 'manual-entry', fieldCode?: string): void;
  (e: 'confirm', row: CpFactSnapshotVO): void;
  (e: 'reject', row: CpFactSnapshotVO): void;
  (e: 'toggle-excerpt', row: CpFactSnapshotVO): void;
}>();
</script>
