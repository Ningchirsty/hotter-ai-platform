<template>
  <!-- 单根容器：`src/views` 下的组件会被 `vite:check-transition` 检查（外层路由用 <transition> 包裹）。 -->
  <div class="generation-board">
    <section class="panel" data-generation-section="CANDIDATES">
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
            <div class="thumb" @click="asGen(row).previewable && $emit('preview', asGen(row))">
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
import type { DpGenerationVO, DpStoryboardScreenVO, DpStoryboardVO } from '@/api/creative/types';
import {
  asGen,
  canSelect,
  durationText,
  formatTime,
  productVerdictLabel,
  qaClass,
  qaVerdictLabel,
  ruleCheck,
  ruleClass,
  ruleLabel,
  screenLabel,
  screenTypeDesc,
  sizeText,
  statusLabel,
  statusType
} from '../generationText';

/**
 * 「出图」这一步在生产页上的内容（V0.2 R41，装配组件名 `GenerationBoard`）。
 *
 * <p><b>为什么这一步有两个组件</b>：同一个步骤在不同页面上呈现不同——
 * 项目页是"发起出图 + 候选"（`ProjectHeroBlock` + `ProjectGenerationsBlock`），
 * 生产页是"逐屏候选管理"（本组件）。装配配置用 `pages` 标明每个组件属于哪个页面，
 * 于是两个页面各自只显示自己那一份，也不会互相收到"配置与插槽对不上"的假警报。</p>
 *
 * <p><b>纯展示</b>：预览 / 选定 / 质检 / 重出 / 对比 / 关闭对比都发事件回页面——
 * 它们要刷新候选、分镜与生产状态并推进流程指引线，只有页面知道该刷什么。
 * 对比框里的图片是 blob URL（由页面按 key 管理并在切页时释放），所以这里只通过 `urlOf` 读。</p>
 *
 * @author creative
 */
defineProps<{
  /** 该项目的候选（逐屏） */
  rows: DpGenerationVO[];
  /** 最新分镜（用来显示"分镜 N 屏"） */
  storyboard: DpStoryboardVO | null;
  /** 屏 id → 屏（候选归属与屏号展示） */
  screenMap: Record<string, DpStoryboardScreenVO>;
  /** 表格 loading */
  loading: boolean;
  /** 刷新状态中 */
  refreshing: boolean;
  /** 正在进行的动作（`select-<id>` / `qa-<id>` / `regen-<screenId>`） */
  busy: string;
  /** 缩略图 URL（按候选取；没有就是空串） */
  thumbUrl: (row: DpGenerationVO) => string;
  /** blob URL 台账读取（产品图与生成图对比） */
  urlOf: (key: string) => string;
  /** 产品图（对比框里判断"有没有基准可显示"） */
  productImage: { configured?: boolean } | null;
  /** 当前展开的对比（null = 不显示） */
  compareGen: { gen: DpGenerationVO; typeDesc: string } | null;
  /** 对比头部显示的屏号 */
  compareScreenLabel: string;
}>();

defineEmits<{
  /** 刷新生产状态 */
  (e: 'refresh'): void;
  /** 预览候选 */
  (e: 'preview', row: DpGenerationVO): void;
  /** 选定候选 */
  (e: 'select', row: DpGenerationVO): void;
  /** 对候选发起质检 */
  (e: 'qa', row: DpGenerationVO): void;
  /** 重出这一屏 */
  (e: 'regenerate', row: DpGenerationVO): void;
  /** 打开"产品图 | 生成图"对比 */
  (e: 'compare', row: DpGenerationVO): void;
  /** 关闭对比 */
  (e: 'close-compare'): void;
}>();
</script>

<style scoped lang="scss">
/* 这一步的内容样式（R41 从 production/index.vue 搬过来，一字未改） */
.generation-board {
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
