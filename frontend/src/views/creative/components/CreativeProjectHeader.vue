<template>
  <header class="proj-header" :data-panel="'PROJECT_HEADER'">
    <div class="ph-title">
      <h2>{{ project?.taskName || '未选择项目' }}</h2>
      <div class="ph-tags">
        <span v-if="stageLabel" class="stage-tag" :class="'is-' + stageType">{{ stageLabel }}</span>
        <span v-if="project?.taskNo" class="muted">{{ project.taskNo }}</span>
        <span v-if="project?.status" class="muted">内容协同状态：{{ project.status }}</span>
      </div>
    </div>

    <!-- 文档 §23：项目 / SKU / 渠道 / 当前阶段 / 负责人 / 输出规格。
         每一项都来自配置或数据：输出规格与渠道取该交付类型的 dp_output_spec，取不到就如实说"未配置"，
         不写死 750/800（那正是"配置化了却还写死"的老毛病）。 -->
    <dl class="ph-meta">
      <div>
        <dt>SKU / 产品</dt>
        <dd>{{ skuText }}</dd>
      </div>
      <div>
        <dt>渠道</dt>
        <dd>{{ spec.channel || '未配置' }}</dd>
      </div>
      <div>
        <dt>输出规格</dt>
        <dd>
          <template v-if="spec.specCode">
            {{ spec.specCode }} · {{ sizeText }}
          </template>
          <template v-else>未配置（该交付类型还没有输出规格）</template>
        </dd>
      </div>
      <div>
        <dt>负责人</dt>
        <dd>{{ project?.ownerName || '未指定' }}</dd>
      </div>
      <div>
        <dt>交付类型</dt>
        <!-- 不直接漏编码：配置里有权威名（deliveryName），拿到就用它；只有编码时用前端兜底表（内测 S11） -->
        <dd>{{ deliveryTypeText }}</dd>
      </div>
      <div>
        <dt>截止</dt>
        <dd>{{ project?.deadline || '未设置' }}</dd>
      </div>
    </dl>

    <div class="ph-actions">
      <el-button size="small" plain :loading="loading" @click="$emit('refresh')">刷新</el-button>
      <el-button size="small" plain @click="$emit('open-qa')">质检与交付</el-button>
      <el-button size="small" plain @click="$emit('open-assets')">资产</el-button>
      <el-button size="small" plain @click="$emit('open-logs')">操作日志</el-button>
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { CreativeProjectVO } from '@/api/creative/types';
import { DELIVERY_TYPE_LABELS } from '@/api/creative/types';
import type { ScenarioOutputSpec } from '@/api/creative/scenario';

/**
 * 项目工作台头部（V0.2 R31，文档 §23 的 PROJECT_HEADER 槽位）。
 *
 * <p><b>它把"配置声明了却没人实现"的那一块补上</b>：R17 的装配对照里
 * PROJECT_HEADER 一直分类为"页面内区块"，装配运行时因此把它跳过
 * （页面上那 4/5 个槽位就是这么来的）。现在它是真组件，配置里的 5 个面板全部可装配。</p>
 *
 * <p><b>为什么输出规格/渠道要读配置</b>：它们本来就在 `dp_output_spec` 里（主图 800×800、
 * 详情页 750×AUTO）。头部把它们显示出来，用户一眼能看到"这次交付要出的尺寸"；
 * 写死在组件里就会与配置漂移，而漂移在出图那一刻才暴露。</p>
 *
 * @author creative
 */
const props = defineProps<{
  /** 当前项目 */
  project?: CreativeProjectVO | null;
  /** 该交付类型的默认输出规格（取不到就不显示尺寸，如实说未配置） */
  spec?: ScenarioOutputSpec | null;
  /** 阶段中文标签 */
  stageLabel?: string;
  /** 阶段样式用的类型（对齐既有 stage-tag 的 is-* 约定） */
  stageType?: string;
  /** 刷新中 */
  loading?: boolean;
}>();

defineEmits<{
  (e: 'refresh'): void;
  (e: 'open-qa'): void;
  (e: 'open-assets'): void;
  (e: 'open-logs'): void;
}>();

const spec = computed(() => props.spec || {});

const skuText = computed(() => {
  const parts = [props.project?.productName, props.project?.skuCode].filter((v) => v);
  return parts.length ? parts.join(' / ') : '未关联产品';
});

const sizeText = computed(() => {
  const width = spec.value.width;
  const height = spec.value.height;
  if (!width) {
    return '—';
  }
  if ((spec.value.heightMode || '').toUpperCase() === 'AUTO') {
    return `${width}×自动高度`;
  }
  return `${width}×${height || '—'}`;
});

/**
 * 交付类型的展示文案（内测 S11：以前这里直接漏 `ECOM_DETAIL`）。
 *
 * <p>顺序：后端按配置填好的 `deliverableTypeDesc`（权威名）→ 前端兜底表 → 编码本身。
 * 最后那档是刻意的：**显示看不懂的编码，也强过留白**——编码至少能拿去对配置，空白没人能查。</p>
 */
const deliveryTypeText = computed(() => {
  const code = props.project?.deliverableType;
  if (!code) {
    return '—';
  }
  return props.project?.deliverableTypeDesc || DELIVERY_TYPE_LABELS[code] || code;
});
</script>

<style scoped lang="scss">
.proj-header {
  display: grid;
  grid-template-columns: minmax(280px, 1.2fr) 2fr auto;
  gap: 16px;
  align-items: start;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--panel, transparent);
  margin-bottom: 12px;
}

.ph-title h2 {
  margin: 0 0 6px;
  font-size: 18px;
}

.ph-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}

.ph-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 8px 16px;
  margin: 0;
}

.ph-meta div {
  min-width: 0;
}

.ph-meta dt {
  font-size: 11px;
  letter-spacing: 0.04em;
  color: var(--muted, #9aa0a6);
  text-transform: uppercase;
}

.ph-meta dd {
  margin: 2px 0 0;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.ph-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

@media (max-width: 1200px) {
  .proj-header {
    grid-template-columns: 1fr;
  }
  .ph-actions {
    justify-content: flex-start;
  }
}
</style>
