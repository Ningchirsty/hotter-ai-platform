<template>
  <!-- 单根容器：`src/views` 下的组件会被 `vite:check-transition` 检查（外层路由用 <transition> 包裹）。 -->
  <div class="final-panel">
    <!-- 交付最终版（V1.0）：LAYOUT 渲染完之后的人工精修产物 -->
    <section v-if="(detailPage?.currentVersion ?? 0) > 0" class="panel" data-final-section="FINAL">
      <div class="block-head">
        <h3>终审与交付</h3>
        <span class="muted">
          当前版本 v{{ detailPage?.currentVersion ?? 0 }}（{{ detailPage?.statusDesc || '未排版' }}）
        </span>
      </div>
      <div class="final-row">
        <div class="final-hint">
          <b>交付最终版（V1.0）</b>
          <span class="muted">
            设计师在 V0.8 基础上精修后上传长图；上传即登记为新版本并标记交付完成，历史版本全部保留。
          </span>
        </div>
        <el-upload
          :show-file-list="false"
          accept="image/png,image/jpeg"
          :http-request="onUpload"
        >
          <el-button :loading="uploadingFinal">上传精修最终版</el-button>
        </el-upload>
      </div>
    </section>

    <!-- 交付产物（R30，文档 §26 Renderer Hub） -->
    <section class="panel" data-final-section="DELIVERY">
      <div class="block-head">
        <h3>交付产物</h3>
        <div class="head-actions">
          <span class="muted">
            渲染器 {{ delivery?.rendererName || '—' }}（模式 {{ delivery?.renderMode || '未配置' }}）
            · 当前版本 v{{ delivery?.currentVersion ?? 0 }}
          </span>
          <!--
            R51：多图交付类型的收尾动作。以前全平台只有「上传精修最终版」能推到「已完成」，
            而多图类没有长图可上传——项目会卡在终审（R50 干跑实测）。
          -->
          <el-button
            v-if="canConfirmDelivery"
            v-hasPermi="['creative:final:review']"
            size="small"
            type="primary"
            :loading="confirming"
            @click="$emit('confirm-delivery')"
          >
            确认交付（标记已完成）
          </el-button>
          <!-- R35：同「渲染机排版」，交付产物生成也走 creative:layout:render，按权限显示 -->
          <el-button
            v-hasPermi="['creative:layout:render']"
            size="small"
            plain
            :loading="delivering"
            @click="$emit('render-delivery')"
          >
            生成交付产物
          </el-button>
        </div>
      </div>

      <p class="muted">
        渲染器由交付类型的<b>渲染模式</b>决定，不由页面猜：详情页（LONGPAGE）走长图排版，
        商品主图（MULTI_IMAGE）走<b>多图打包</b>——把各屏已选定的交付图按屏序打成一个 ZIP，
        包里第一项是 <code>manifest.json</code>（每张图是什么屏、多大、sha256 多少）。
        交付包在下载时<b>现拼</b>，不再复制一份存储。
      </p>

      <div v-if="(delivery?.renderers || []).length" class="renderer-row">
        <el-tag
          v-for="r in delivery?.renderers || []"
          :key="r.code"
          size="small"
          :type="r.implemented ? (r.selected ? 'success' : 'info') : 'warning'"
          :effect="r.selected ? 'dark' : 'plain'"
        >
          {{ r.name }}（{{ r.code }}）{{ r.implemented ? (r.selected ? '· 本次使用' : '') : '· 未实现' }}
        </el-tag>
      </div>
      <p class="muted small">
        未实现的渲染器只登记、不执行（点了会明确报错，不会跑个空壳还报成功）。
      </p>

      <el-table
        v-if="(delivery?.artifacts || []).length"
        :data="delivery?.artifacts || []"
        size="small"
        class="version-table"
      >
        <el-table-column label="版本" width="170">
          <template #default="{ row }">
            <div class="cell-main">v{{ asArtifact(row).version }} · {{ asArtifact(row).rendererName }}</div>
            <div class="muted small">{{ asArtifact(row).createTime }}</div>
          </template>
        </el-table-column>
        <el-table-column label="产物" width="130">
          <template #default="{ row }">
            {{ asArtifact(row).imageCount }} 张 ·
            {{ formatBytes(asArtifact(row).totalBytes) }}
          </template>
        </el-table-column>
        <el-table-column label="清单校验和" width="160">
          <template #default="{ row }">
            <span class="mono small">{{ shortSha(asArtifact(row).checksum) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="说明 / 产物明细" min-width="320">
          <template #default="{ row }">
            <div class="muted small">{{ asArtifact(row).remark || '—' }}</div>
            <div class="muted small">
              {{ (asArtifact(row).products || []).map((p) => productText(p)).join('、') }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              text
              type="primary"
              :loading="downloadingId === String(asArtifact(row).id)"
              @click="$emit('download', asArtifact(row))"
            >
              下载交付包
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <p v-else class="empty">
        还没有交付产物。逐屏出图并选定候选后，点上面的「生成交付产物」。
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { UploadRequestOptions } from 'element-plus';
import type {
  DeliveryArtifactVO,
  DeliveryProductVO,
  DeliveryVO,
  DpDetailPageVO
} from '@/api/creative/types';

/**
 * 「终审交付」这一步的内容（V0.2 R40，装配组件名 `FinalReviewPanel`）。
 *
 * <p>两块：① 交付最终版（V1.0）上传——LAYOUT 渲染完之后的人工精修产物；
 * ② 交付产物（R30 的 Renderer Hub：渲染器能力 + 历史交付包 + 下载）。
 * 原注释说这两块"与长图画布同区块，装配时要拆开"，本轮就按 LAYOUT / FINAL 两步拆开了。</p>
 *
 * <p>纯展示：上传（把 el-upload 的请求对象原样转给页面）、生成交付产物、下载都发事件——
 * 它们要刷新多份数据并推进流程指引线，只有页面知道该刷什么。</p>
 *
 * @author creative
 */
defineProps<{
  /** 详情页状态（这里只用"当前版本"判断要不要显示上传入口） */
  detailPage: DpDetailPageVO | null;
  /** 交付产物视图（渲染器能力 + 历史版本） */
  delivery: DeliveryVO | null;
  /** 正在上传最终版 */
  uploadingFinal: boolean;
  /** 正在生成交付产物 */
  delivering: boolean;
  /** 正在下载的产物 id（按钮 loading） */
  downloadingId: string;
  /**
   * 能不能「确认交付」（R51）。
   *
   * <p>由页面按三件事算出：是多图交付、已经有交付产物、项目还没到「已完成」。
   * 组件不自己判断——它拿不到阶段，也不该猜。</p>
   */
  canConfirmDelivery?: boolean;
  /** 正在确认交付 */
  confirming?: boolean;
}>();

const emit = defineEmits<{
  /** 上传精修最终版（el-upload 的请求对象原样转给页面） */
  (e: 'upload-final', options: UploadRequestOptions): void;
  /** 生成交付产物 */
  (e: 'render-delivery'): void;
  /** 确认交付（多图交付类型的收尾动作） */
  (e: 'confirm-delivery'): void;
  /** 下载某个交付包 */
  (e: 'download', artifact: DeliveryArtifactVO): void;
}>();

/**
 * el-upload 的自定义上传：把请求对象转给页面（上传逻辑只留在页面一处）。
 *
 * <p>el-upload 要求这个处理器返回 `Promise | XMLHttpRequest`，所以这里立刻 resolve：
 * 真正的等待与进度用页面传下来的 `uploadingFinal`（按钮自己的 loading）表达——
 * 组件不该假装知道上传什么时候结束。</p>
 *
 * @param options el-upload 的请求参数（含 file）
 * @returns 已 resolve 的 Promise（进度由 uploadingFinal 反映）
 */
function onUpload(options: UploadRequestOptions): Promise<void> {
  emit('upload-final', options);
  return Promise.resolve();
}

function asArtifact(row: unknown): DeliveryArtifactVO {
  return row as DeliveryArtifactVO;
}

/** 产物一行文字：屏号 + 模块 + 像素（页面表格里直接可读） */
function productText(product: DeliveryProductVO): string {
  const parts = [product.screenNo, product.moduleCode, `${product.width}×${product.height}`]
    .filter((v) => v !== undefined && v !== null && String(v) !== '');
  return parts.join(' ');
}

function formatBytes(bytes?: number): string {
  if (!bytes) return '0B';
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)}KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}

function shortSha(sha?: string): string {
  return sha ? `${sha.slice(0, 12)}…` : '—';
}
</script>

<style scoped lang="scss">
/* 这一步的内容样式（R40 从 review/index.vue 搬过来，一字未改） */
.final-panel {
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

.block-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.cell-main {
  font-size: 13px;
}
.small {
  font-size: 12px;
}

.version-table {
  margin-top: 12px;
}

/* R30：渲染器能力行（已实现/未实现一眼看出） */
.renderer-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0 4px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  letter-spacing: 0.02em;
}

.final-row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--line);
}
.final-hint {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}
.final-hint .muted {
  margin: 0;
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
