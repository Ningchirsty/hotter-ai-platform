<template>
  <section class="block" data-block="PROJECT_COPY">
    <div class="block-head">
      <h4>3. 文案与要点</h4>
      <div class="block-actions">
        <span class="muted">共 {{ blocks.length }} 条</span>
        <el-button size="small" plain :loading="busy === 'load'" @click="$emit('refresh')">刷新</el-button>
      </div>
    </div>
    <p class="hint">
      这块是详情页要说的「字」。去向按后端实际接线如实写：<b>卖点 / 正文 / 参数</b>按这里的顺序进
      <b>详情页长图</b>（卖点还会进分镜草稿的卖点屏）。
      「必显信息」不在这里录入——它是<b>品牌方的要求</b>，统一在「2. 品牌要求（Brief）」里（品牌部在内容任务里填），由出图提示词与闸门引用，
      避免同一件事有两个真相源。
      与分镜的分工：分镜屏文案是「这一屏这张图配什么字」，<b>R7 起屏文案也会进图像提示词</b>（画面独白优先）；
      而这里整页的文字不进出图提示词——出图提示词用的是视觉基因 + 「2. 品牌要求（Brief）」的必显 / 主推 / 禁用词。
    </p>
    <p v-if="error" class="fact-error">
      {{ error }}（点右上「刷新」重试，页面不会用空表糊过去）
    </p>
    <el-tabs
      :model-value="tab"
      @update:model-value="(v: string | number) => $emit('update:tab', String(v))"
    >
      <el-tab-pane v-for="item in tabs" :key="item.value" :label="item.label" :name="item.value">
        <p class="hint">{{ item.hint }}</p>
        <p class="copy-usedat">{{ item.usedAt }}</p>
        <div class="block-actions copy-toolbar">
          <el-button size="small" type="primary" plain @click="$emit('add', item.value)">
            ＋ 新增{{ item.label }}
          </el-button>
          <el-button
            v-if="item.value === 'SPEC_ROW'"
            size="small"
            plain
            :loading="busy === 'seed'"
            @click="$emit('seed-from-facts')"
          >
            从已确认事实派生
          </el-button>
          <span v-if="item.value === 'SPEC_ROW'" class="hint">
            派生出来的行标为「事实派生」；改过事实后请重新派生，以免两处不一致。
          </span>
          <span v-else-if="item.value === 'SELLING_POINT'" class="hint">
            用「↑ / ↓」调整优先级，顺序即详情页从上到下的顺序，点一下立即保存。
          </span>
        </div>
        <!-- 无框行列表：与「事实确认」同一套写法（发丝分隔线 + 悬停高亮），
             不用白色表格容器；排序号显示"第几条"而不是后端 sortNo（步长 10 看着莫名） -->
        <div v-if="activeBlocks.length" class="copy-list">
          <div
            v-for="(row, idx) in activeBlocks"
            :key="String(asBlock(row).id)"
            class="copy-row"
          >
            <div class="copy-order">
              <span class="ord">{{ idx + 1 }}</span>
              <template v-if="item.value === 'SELLING_POINT'">
                <button
                  type="button"
                  class="ord-btn"
                  title="上移（提高优先级）"
                  :disabled="isFirst(item.value, asBlock(row)) || busy === 'reorder'"
                  @click="$emit('move', asBlock(row), -1)"
                >
                  ↑
                </button>
                <button
                  type="button"
                  class="ord-btn"
                  title="下移（降低优先级）"
                  :disabled="isLast(item.value, asBlock(row)) || busy === 'reorder'"
                  @click="$emit('move', asBlock(row), 1)"
                >
                  ↓
                </button>
              </template>
            </div>
            <div class="copy-body">
              <div class="copy-line1">
                <span class="copy-title">{{ asBlock(row).title || '—' }}</span>
                <span class="copy-src" :class="'is-' + sourceType(asBlock(row).source)">
                  {{ sourceLabel(asBlock(row).source) }}<span
                    v-if="asBlock(row).sourceRef"
                    class="muted"
                  > · {{ asBlock(row).sourceRef }}</span>
                </span>
                <span class="fact-status" :class="'is-' + statusType(asBlock(row).status)">
                  {{ statusLabel(asBlock(row).status) }}
                </span>
              </div>
              <p class="copy-text">{{ asBlock(row).content || '—' }}</p>
            </div>
            <div class="copy-ops">
              <el-button
                link
                size="small"
                type="primary"
                @click="$emit('edit', item.value, asBlock(row))"
              >
                编辑
              </el-button>
              <el-button
                link
                size="small"
                type="danger"
                :loading="busy === 'block-' + String(asBlock(row).id)"
                @click="$emit('delete', asBlock(row))"
              >
                删除
              </el-button>
            </div>
          </div>
        </div>
        <p v-else class="empty">这一组还没有内容。点上面的「新增」录入。</p>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import type { CopyBlockVO, TagType } from '@/api/creative/types';

/** 文案分组页签（页面按块类型生成的元数据，组件只负责显示） */
export interface CopyTabMeta {
  value: string;
  label: string;
  hint: string;
  usedAt: string;
}

/**
 * 项目页区块③「文案与要点」（V0.2 R32）。
 *
 * <p>这一块与"事实确认"共用一套无框行列表的写法；拆出来以后，
 * 「哪些行属于当前页签、能不能上移下移」这类判断仍由页面给（`activeBlocks`/`isFirst`/`isLast`），
 * 组件不自己算——避免同一套顺序规则在两处实现（R7 起这条规则已经改过两次口径）。</p>
 *
 * @author creative
 */
const props = defineProps<{
  /** 全部文案块（用于标题上的计数） */
  blocks: CopyBlockVO[];
  /** 当前页签下的行（页面已按页签过滤并排好序） */
  activeBlocks: CopyBlockVO[];
  /** 页签元数据 */
  tabs: CopyTabMeta[];
  /** 当前页签 */
  tab: string;
  /** 正在进行的动作（load / seed / reorder / block-<id>） */
  busy: string;
  /** 读取失败原因（不静默） */
  error: string;
  /** 行类型收窄（表格插槽行是 unknown，页面统一收窄） */
  asBlock: (row: unknown) => CopyBlockVO;
  /** 来源与状态的文案/样式（口径在页面，避免两处维护） */
  sourceLabel: (source?: string) => string;
  sourceType: (source?: string) => TagType;
  statusLabel: (status?: string) => string;
  statusType: (status?: string) => TagType;
  /** 排序边界判断 */
  isFirst: (tab: string, row: CopyBlockVO) => boolean;
  isLast: (tab: string, row: CopyBlockVO) => boolean;
}>();

defineEmits<{
  (e: 'update:tab', value: string): void;
  (e: 'refresh'): void;
  (e: 'add', tab: string): void;
  (e: 'edit', tab: string, row: CopyBlockVO): void;
  (e: 'delete', row: CopyBlockVO): void;
  (e: 'move', row: CopyBlockVO, delta: number): void;
  (e: 'seed-from-facts'): void;
}>();

// props 仅用于类型收窄（模板里直接用）
void props;
</script>
