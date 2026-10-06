<template>
  <div class="generation-board">
    <!--
      模式一（分镜页）：**按屏**看这一轮生产与质检——"哪一屏还没出、哪一屏质检没过"。
      与模式二共用同一批口径（generationText），但回答的是不同的问题，所以是两种呈现而不是两套逻辑。
    -->
    <section v-if="mode === 'SCREENS'" class="panel" data-generation-section="SCREENS">
      <div class="sub-head">
        <h3>
          逐屏出图与质检
          <span class="muted">
            本次提交 {{ production?.submitted ?? 0 }} 屏、跳过 {{ production?.skipped ?? 0 }} 屏
          </span>
        </h3>
        <div class="head-actions">
          <el-button size="small" plain :loading="refreshing" @click="$emit('refresh')">刷新状态</el-button>
          <el-button
            size="small"
            type="primary"
            :disabled="!storyboard || storyboard.status !== 'LOCKED'"
            :loading="producing"
            @click="$emit('produce')"
          >
            按分镜批量出图
          </el-button>
        </div>
      </div>
      <p class="muted">
        提示词由已锁定基因按屏派生；失败候选每屏最多自动重试到 3 次尝试（到顶转人工）。
        质检结论只用于筛选：<b>不一致的候选会被筛除，一致的也不会自动选定</b>。
      </p>
      <el-table v-if="production" :data="production.screens" size="small" class="prod-table">
        <el-table-column prop="screenNo" label="屏" width="70" />
        <el-table-column prop="screenTypeDesc" label="类型" width="90" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="screenStatusType(asScreen(row).status)">
              {{ screenStatusText(asScreen(row).status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="candidateCount" label="候选数" width="80" />
        <el-table-column label="最新候选" width="110">
          <template #default="{ row }">{{ latestStatusText(asScreen(row).latestStatus) }}</template>
        </el-table-column>
        <el-table-column label="质检" width="160">
          <template #default="{ row }">
            <span :class="qaClass(asScreen(row).qaVerdict)">{{ qaVerdictText(asScreen(row).qaVerdict) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="150">
          <template #default="{ row }">{{ asScreen(row).note || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              text
              type="primary"
              :loading="busy === 'regen-screen-' + asScreen(row).screenId"
              @click="$emit('regenerate-screen', asScreen(row))"
            >
              重出这一屏
            </el-button>
            <el-button
              size="small"
              text
              type="success"
              :disabled="!latestGenerationOf(asScreen(row))"
              :loading="busy === 'select-gen-' + latestGenerationOf(asScreen(row))"
              @click="$emit('select-screen', asScreen(row))"
            >
              选定候选
            </el-button>
            <el-button
              size="small"
              text
              :disabled="!latestGenerationOf(asScreen(row))"
              :loading="busy === 'qa-gen-' + latestGenerationOf(asScreen(row))"
              @click="$emit('qa-screen', asScreen(row))"
            >
              质检
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <p v-else class="empty">还没有生产记录。分镜锁定后点「按分镜批量出图」。</p>
    </section>

    <!-- 模式二（生产页）：**按候选**看——缩略图、质检/产品基准、规则体检、选定/重出/对比 -->
    <section v-else class="panel" data-generation-section="CANDIDATES">
      <div class="sub-head">
        <h3>
          项目候选
          <span class="muted">
            {{ rows.length }} 个候选
            <template v-if="storyboard">· 分镜 {{ storyboard.screenCount ?? (storyboard.screens || []).length }} 屏</template>
          </span>
        </h3>
        <div class="head-actions">
          <el-button size="small" plain :loading="refreshing" @click="$emit('refresh')">刷新状态</el-button>
        </div>
      </div>

      <p class="muted">
        质检只做减法：<b>参考图基准不一致的候选不参与选定</b>（只筛除不放行）；产品基准不一致
        <b>只提示、不自动筛除</b>——换背景、换景别可能正是设计意图，这个判断留给人。
        「产品基准」比的是<b>产品图</b>，「质检」比的是本次出图喂进模型的输入图；两者都可能为 null，
        为 null 时本页如实显示「未质检」，绝不当成通过。
      </p>

      <p class="muted">
        <b>规则体检</b>是按这一屏所属模块在模块库里配的 <code>qaRules</code> 对<b>交付图</b>做的
        <b>确定性像素度量</b>（是否 1:1、最短边、透明通道、边缘白度、主体占比、是否贴边），
        <b>不调用模型、可复算</b>；它只看客观度量，不看画面好不好看。
        体检<b>只报告不判决</b>：硬性项没过也不会自动筛除候选。这一屏没配规则时显示「未配置规则」——
        「没检查」与「检查通过」是两回事。
      </p>

      <el-table v-loading="loading" :data="rows" class="prod-table" empty-text="该项目还没有出图候选">
        <el-table-column label="预览" width="90">
          <template #default="{ row }">
            <div
              :ref="(el) => setThumbRef(el)"
              class="thumb"
              @click="asGen(row).previewable && $emit('preview', asGen(row))"
            >
              <img v-if="thumbUrl(asGen(row))" :src="thumbUrl(asGen(row))" :alt="`候选 ${asGen(row).candidateNo}`" />
              <span v-else class="thumb-empty">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="屏" min-width="150">
          <template #default="{ row }">
            <div class="cell-main">{{ screenLabel(asGen(row), screenMap) }}</div>
            <div class="cell-sub">{{ screenTypeDesc(asGen(row), screenMap) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="候选" width="80">
          <template #default="{ row }">#{{ asGen(row).candidateNo }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <span class="gen-status" :class="'is-' + statusType(asGen(row).status)">
              {{ asGen(row).statusDesc || statusLabel(asGen(row).status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="质检 / 产品基准" min-width="170">
          <template #default="{ row }">
            <div><span :class="qaClass(asGen(row).qaVerdict)">质检：{{ qaVerdictLabel(asGen(row).qaVerdict) }}</span></div>
            <div>
              <span :class="qaClass(asGen(row).productVerdict)">
                产品基准：{{ productVerdictLabel(asGen(row).productVerdict) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="尺寸" width="110">
          <template #default="{ row }">{{ sizeText(asGen(row)) }}</template>
        </el-table-column>
        <el-table-column label="规则体检" min-width="200">
          <template #default="{ row }">
            <template v-if="ruleCheck(asGen(row))">
              <div>
                <span :class="ruleClass(ruleCheck(asGen(row))!.verdict)">
                  规则：{{ ruleLabel(ruleCheck(asGen(row))!.verdict) }}
                </span>
              </div>
              <div v-if="ruleCheck(asGen(row))!.failed.length" class="cell-sub">
                {{ ruleCheck(asGen(row))!.failed.map((f) => f.label).join('、') }}
              </div>
            </template>
            <span v-else class="muted small">未配置规则</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ durationText(asGen(row).durationMs) }}</template>
        </el-table-column>
        <el-table-column label="失败/提示" min-width="170">
          <template #default="{ row }">
            <span :class="asGen(row).errorMessage ? 'gen-error' : 'cell-sub'">{{ asGen(row).errorMessage || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatTime(asGen(row).createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-if="asGen(row).previewable" size="small" text type="primary" @click="$emit('preview', asGen(row))">
              预览
            </el-button>
            <el-button
              v-if="canSelect(asGen(row))"
              size="small"
              text
              type="success"
              :loading="busy === 'select-' + asGen(row).id"
              @click="$emit('select', asGen(row))"
            >
              选定
            </el-button>
            <el-tag v-else-if="asGen(row).status === 'APPROVED'" size="small" type="success">已选定</el-tag>
            <el-button
              v-if="asGen(row).previewable"
              size="small"
              text
              :loading="busy === 'qa-' + asGen(row).id"
              @click="$emit('qa', asGen(row))"
            >
              质检
            </el-button>
            <el-button
              v-if="asGen(row).screenId != null"
              size="small"
              text
              type="warning"
              :loading="busy === 'regen-' + asGen(row).screenId"
              @click="$emit('regenerate', asGen(row))"
            >
              重出这一屏
            </el-button>
            <el-button
              v-if="asGen(row).previewable"
              size="small"
              text
              type="primary"
              @click="$emit('compare', asGen(row))"
            >
              对比产品图
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 产品图 | 生成图 并排对比（双基准各自如实显示，null 就是「未质检」） -->
      <div v-if="compareGen" class="compare-box">
        <div class="compare-head">
          <b>产品图 | 生成图</b>
          <span class="muted">
            {{ compareGen.typeDesc }} 候选 #{{ compareGen.gen.candidateNo }}
            <template v-if="compareScreenLabel">· {{ compareScreenLabel }}</template>
            · 质检：{{ qaVerdictLabel(compareGen.gen.qaVerdict) }}
            · 产品基准：{{ productVerdictLabel(compareGen.gen.productVerdict) }}
          </span>
          <span class="spacer" />
          <el-button link size="small" @click="$emit('close-compare')">关闭对比</el-button>
        </div>
        <div class="compare-grid">
          <figure>
            <img v-if="urlOf('product')" :src="urlOf('product')" alt="产品图" />
            <span v-else class="img-placeholder">产品图不可用（未配置或读取失败）</span>
            <figcaption>产品图（基准）</figcaption>
          </figure>
          <figure>
            <img
              v-if="urlOf('genpreview-' + compareGen.gen.id)"
              :src="urlOf('genpreview-' + compareGen.gen.id)"
              alt="生成图"
            />
            <span v-else class="img-placeholder">生成图读取中…</span>
            <figcaption>生成图（候选 #{{ compareGen.gen.candidateNo }}）</figcaption>
          </figure>
        </div>
        <p v-if="!(productImage && productImage.configured)" class="muted">
          该产品还没有产品图，左图没有基准可显示——请先在「视觉项目」页上传产品照片，并把它设为该产品的产品图。
        </p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, type ComponentPublicInstance } from 'vue';
import type {
  DpGenerationVO,
  DpStoryboardScreenVO,
  DpStoryboardVO,
  ProductionRunVO,
  ScreenProductionVO
} from '@/api/creative/types';
import {
  asGen,
  asScreen,
  canSelect,
  durationText,
  formatTime,
  latestGenerationOf,
  latestStatusText,
  productVerdictLabel,
  qaClass,
  qaVerdictLabel,
  qaVerdictText,
  ruleCheck,
  ruleClass,
  ruleLabel,
  screenLabel,
  screenStatusText,
  screenStatusType,
  screenTypeDesc,
  sizeText,
  statusLabel,
  statusType
} from '../generationText';

/**
 * 「出图」这一步的内容（V0.2 R41 起装配组件名 `GenerationBoard`；R43 起**两种模式**）。
 *
 * <p><b>为什么一个步骤会有两种呈现</b>：同一个步骤在不同页面上回答不同的问题——</p>
 * <ul>
 *   <li>{@code SCREENS}（分镜页）：**按屏**看"哪一屏还没出、哪一屏质检没过"，并批量出图；</li>
 *   <li>{@code CANDIDATES}（生产页）：**按候选**看缩略图、质检/产品基准、规则体检，选定或重出。</li>
 * </ul>
 * <p>两者共用同一批口径（{@code generationText.ts}：状态 / 质检 / 产品基准 / 规则体检 / 尺寸 / 耗时），
 * 因此"同一个 null 两个叫法"这类漂移不会发生；差别只在**呈现与动作**，用一个 `mode` 表达即可。
 * 配置侧用 `pages` 把两块分别标给分镜页与生产页（见 dp_creative_r41/r43 迁移脚本）。</p>
 *
 * <p><b>纯展示</b>：所有动作（批量出图 / 刷新 / 重出 / 选定 / 质检 / 预览 / 对比）都发事件回页面——
 * 它们要刷新候选、分镜与生产状态并推进流程指引线，只有页面知道该刷什么。
 * 对比框里的图片是 blob URL（由页面按 key 管理并在切页时释放），所以这里只通过 `urlOf` 读。</p>
 *
 * @author creative
 */
const props = withDefaults(
  defineProps<{
    /**
     * 呈现模式：`SCREENS`=按屏（分镜页） / `CANDIDATES`=按候选（生产页）。
     *
     * <p>默认按候选：生产页是"候选"的主场，老调用方（没传 mode）行为不变。</p>
     */
    mode?: 'SCREENS' | 'CANDIDATES';
    /** 该项目的候选（逐屏）——CANDIDATES 模式用 */
    rows: DpGenerationVO[];
    /** 最新分镜（用来显示"分镜 N 屏"；SCREENS 模式下"按分镜批量出图"也看它的锁定状态） */
    storyboard: DpStoryboardVO | null;
    /** 屏 id → 屏（候选归属与屏号展示）——CANDIDATES 模式用 */
    screenMap: Record<string, DpStoryboardScreenVO>;
    /** 这一轮的生产状态（按屏）——SCREENS 模式用（其它模式不需要传） */
    production?: ProductionRunVO | null;
    /** 表格 loading */
    loading: boolean;
    /** 刷新状态中 */
    refreshing: boolean;
    /** 批量出图中——SCREENS 模式用 */
    producing?: boolean;
    /**
     * 正在进行的动作（按钮 loading）：
     * CANDIDATES 用 `select-<id>` / `qa-<id>` / `regen-<screenId>`；
     * SCREENS 用 `regen-screen-<screenId>` / `select-gen-<id>` / `qa-gen-<id>`。
     */
    busy: string;
    /** 缩略图 URL（按候选取；没有就是空串）——CANDIDATES 模式用 */
    thumbUrl: (row: DpGenerationVO) => string;
    /** blob URL 台账读取（产品图与生成图对比）——CANDIDATES 模式用 */
    urlOf: (key: string) => string;
    /** 产品图（对比框里判断"有没有基准可显示"）——CANDIDATES 模式用 */
    productImage: { configured?: boolean } | null;
    /** 当前展开的对比（null = 不显示）——CANDIDATES 模式用 */
    compareGen: { gen: DpGenerationVO; typeDesc: string } | null;
    /** 对比头部显示的屏号——CANDIDATES 模式用 */
    compareScreenLabel: string;
  }>(),
  { mode: 'CANDIDATES', production: null, producing: false }
);

const emit = defineEmits<{
  /** 刷新生产状态 */
  (e: 'refresh'): void;
  /** 按分镜批量出图（SCREENS） */
  (e: 'produce'): void;
  /** 重出这一屏（SCREENS） */
  (e: 'regenerate-screen', row: ScreenProductionVO): void;
  /** 选定这一屏的最新候选（SCREENS） */
  (e: 'select-screen', row: ScreenProductionVO): void;
  /** 对这一屏的最新候选发起质检（SCREENS） */
  (e: 'qa-screen', row: ScreenProductionVO): void;
  /** 预览候选（CANDIDATES） */
  (e: 'preview', row: DpGenerationVO): void;
  /** 选定候选（CANDIDATES） */
  (e: 'select', row: DpGenerationVO): void;
  /** 对候选发起质检（CANDIDATES） */
  (e: 'qa', row: DpGenerationVO): void;
  /** 重出这一屏（CANDIDATES：从候选行发起） */
  (e: 'regenerate', row: DpGenerationVO): void;
  /** 打开"产品图 | 生成图"对比（CANDIDATES） */
  (e: 'compare', row: DpGenerationVO): void;
  /** 关闭对比（CANDIDATES） */
  (e: 'close-compare'): void;
  /**
   * 这个候选行的缩略图**进入视口附近**了，请页面去取（R45）。
   *
   * <p>为什么由组件发：只有组件知道行在 DOM 里的位置。页面收到后决定"取哪张、怎么去重"，
   * 组件依旧不发请求。</p>
   */
  (e: 'need-thumb', row: DpGenerationVO): void;
}>();

// 模板里统一用 `$emit(...)` 发事件；这个引用只是把事件类型显式声明出来
void emit;

// ---------------------------------------------------------------------------
// R45：候选缩略图"进视口才取"（一个项目 19 行候选不再是 19 个请求一起发）
// ---------------------------------------------------------------------------

/** 缩略图单元格（用来判断可见性） */
const thumbEls = ref<HTMLElement[]>([]);
/** 已经通知过的行（DOM 里同一个元素只通知一次） */
const thumbAnnounced = new WeakSet<HTMLElement>();
let thumbObserver: IntersectionObserver | null = null;

/**
 * 收集缩略图单元格（`:ref` 回调；Vue 对每个单元格各调一次）。
 *
 * @param el 元素（卸载时为 null）
 */
function setThumbRef(el: Element | ComponentPublicInstance | null) {
  if (!(el instanceof HTMLElement)) return;
  if (!thumbEls.value.includes(el)) {
    thumbEls.value.push(el);
  }
  observeThumb(el);
}

/**
 * 观察一个缩略图单元格：进入视口附近就通知页面，并停止观察它。
 *
 * @param el 单元格元素
 */
function observeThumb(el: HTMLElement) {
  if (thumbAnnounced.has(el)) return;
  if (typeof IntersectionObserver === 'undefined') {
    thumbAnnounced.add(el);
    announceThumbOf(el);
    return;
  }
  if (!thumbObserver) {
    thumbObserver = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          thumbAnnounced.add(entry.target as HTMLElement);
          announceThumbOf(entry.target as HTMLElement);
          thumbObserver?.unobserve(entry.target);
        }
      },
      { rootMargin: '200px' }
    );
  }
  thumbObserver.observe(el);
}

/**
 * 找到这个单元格对应的候选并通知页面。
 *
 * <p>表格行不给我们行对象，所以从 DOM 顺序反推：单元格顺序与 `rows` 一一对应
 * （el-table 渲染顺序即数据顺序）。对不上时不猜——直接不发（宁可少取一张，也不取错一张）。</p>
 *
 * @param el 单元格元素
 */
function announceThumbOf(el: HTMLElement) {
  const index = thumbEls.value.indexOf(el);
  const row = index >= 0 ? props.rows[index] : undefined;
  if (row) {
    emit('need-thumb', row);
  }
}

onBeforeUnmount(() => {
  thumbObserver?.disconnect();
  thumbObserver = null;
  thumbEls.value = [];
});
</script>

<style scoped lang="scss">
/* 这一步的内容样式（R41 从 production/index.vue 搬过来，一字未改） */
.generation-board {
  display: block;
}

/* .panel 的外观统一在全局 creative-studio.scss（第 33 轮收口：这里原有一份逐字相同的副本） */
.panel h3 {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 0;
  font-size: 15px;
}

.sub-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.prod-table {
  margin-top: 6px;
}

.thumb {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  overflow: hidden;
  cursor: pointer;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb-empty {
  color: var(--t3);
}

.cell-main {
  font-size: 13px;
}
.cell-sub {
  font-size: 12px;
  color: var(--t3);
}

.gen-status {
  font-size: 12px;
}
.gen-status.is-success {
  color: #a7f3d0;
}
.gen-status.is-warning {
  color: #fde68a;
}
.gen-status.is-danger {
  color: #fca5a5;
}
.gen-error {
  color: #fca5a5;
  font-size: 12px;
}

.good {
  color: #a7f3d0;
}
.bad {
  color: #fca5a5;
}
.warn {
  color: #fde68a;
}
.muted {
  color: var(--t2);
  font-size: 13px;
  line-height: 1.9;
}
.small {
  font-size: 12px;
}

.compare-box {
  padding: 12px;
  margin-top: 14px;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.compare-head {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}
.compare-head .spacer {
  flex: 1;
}
.compare-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}
.compare-grid figure {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
}
.compare-grid img {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 6px;
}
.compare-grid .img-placeholder {
  display: grid;
  place-items: center;
  min-height: 160px;
  color: var(--t3);
  font-size: 12px;
  background: var(--sunken);
  border: 1px dashed var(--line2);
  border-radius: 6px;
}
.compare-grid figcaption {
  font-size: 12px;
  color: var(--t3);
}
</style>
