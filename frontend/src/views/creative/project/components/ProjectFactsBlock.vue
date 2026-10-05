<template>
  <section class="block" data-block="PROJECT_FACTS">
    <div class="block-head">
      <h4>{{ stepHeading }}事实确认</h4>
      <div class="block-actions">
        <span class="muted">已确认 {{ confirmedFacts.length }} 条 / 共 {{ facts.length }} 行</span>
        <span class="hint">只读（品牌部在内容任务里确认）</span>
      </div>
    </div>
    <p class="hint">
      只有<b>已确认</b>的事实才会进入基因 / 方向 / 分镜文案的推导；<b>待确认</b>、<b>冲突</b>与<b>已否决</b>都不算。
    </p>
    <p class="hint">
      本区块<b>只读</b>：事实由<b>品牌部</b>在「内容生产协同 → 内容任务 → 任务详情」确认。
      设计侧不提供新增 / 确认 / 驳回入口——参考图与 AI 结论都不得反向成为产品事实。
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
          <!-- v1 反馈：这里原来直接打印闸门等级码（BLOCK / CONDITION），
               而评审页同一概念写的是「硬性 / 建议」——中文口径统一由 gateLevelLabel 给。 -->
          <em class="gate-level">{{ gateLevelLabel(option.gateLevel) || '—' }}</em>
        </span>
        <span v-if="!requiredOptions.length" class="muted">该交付类型没有声明必填事实项。</span>
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
      </div>
      <p v-if="!visibleFacts.length" class="empty">该筛选下没有事实行。</p>
    </div>
    <p v-else class="empty">
      还没有事实候选。资料上传后由品牌部在内容任务里解析并确认，这里只做展示。
    </p>
  </section>
</template>

<script setup lang="ts">
import type { CpFactFieldOptionVO, CpFactSnapshotVO } from '@/api/content/fact/types';
import type { TagType } from '@/api/creative/types';
import { gateLevelLabel } from '../../composables/gateLabels';
import { useStepHeading } from '../../composables/stepNumbering';

/** 标题编号：本页步骤号（v1 反馈；没有工作台上下文时不显示编号） */
const stepHeading = useStepHeading('ProjectFactsBlock');

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
  /** 字段选项是否取到（没取到要如实说明"无法判断齐备"） */
  fieldOptionsLoaded: boolean;
  /** 闸门必填项的 hover 说明 */
  optionLabel: (option: CpFactFieldOptionVO) => string;
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
  (e: 'toggle-excerpt', row: CpFactSnapshotVO): void;
}>();
</script>
