<template>
  <div class="layout-panel">
    <!--
      R52：同一个「版式」步骤服务两种交付形态——
        · LONG_PAGE（长图）：渲染机排版 V0.8，产出是一张长图，按版本审核；
        · DELIVERY（海报 / 多图包）：产出是**交付产物**（一组成品图），按钮走「生成交付产物」。
      形态由页面按配置的渲染模式算出来传进来（纯函数在 composables/deliveryActions.ts）。
      后端对非长图调排版接口会明确拒绝，所以这里绝不能给海报显示"渲染机排版"。
    -->
    <section class="panel" data-layout-section="LAYOUT" :data-layout-mode="mode">
      <div class="block-head">
        <h3>{{ isDelivery ? '版式与成品图' : '长图排版' }}</h3>
        <div class="head-actions">
          <template v-if="!isDelivery">
            <el-tag :type="detailPage?.rendererAvailable ? 'success' : 'danger'" size="small">
              {{ detailPage?.rendererAvailable ? '渲染服务可达' : '渲染服务不可达' }}
            </el-tag>
            <span class="muted">
              模板 {{ detailPage?.templateKey || '—' }} · 当前版本 v{{ detailPage?.currentVersion ?? 0 }}
              （{{ detailPage?.statusDesc || '未排版' }}）
            </span>
            <!-- R35：按钮按权限显示。后端对这个接口强制 creative:layout:render，
                 前端不隐藏的话，没有该权限的角色点了只会拿到 403——"看得见但点不动"是最差的提示。 -->
            <el-button
              v-hasPermi="['creative:layout:render']"
              size="small"
              plain
              :loading="rendering"
              @click="$emit('render')"
            >
              {{ (detailPage?.currentVersion ?? 0) > 0 ? '重新渲染 V0.8' : '渲染机排版 V0.8' }}
            </el-button>
          </template>
          <template v-else>
            <span class="muted">
              渲染器 {{ delivery?.rendererName || '—' }} · 当前版本 v{{ delivery?.currentVersion ?? 0 }}
            </span>
            <el-button
              v-hasPermi="['creative:layout:render']"
              size="small"
              plain
              :loading="delivering"
              @click="$emit('render-delivery')"
            >
              {{ (delivery?.currentVersion ?? 0) > 0 ? '重新生成成品图' : '生成成品图' }}
            </el-button>
          </template>
        </div>
      </div>

      <!-- 长图形态：说明 + 逐版本表 -->
      <template v-if="!isDelivery">
        <p class="muted">
          渲染会把每屏<b>已选定</b>的产出与分镜文案排成 750×N 长图；没有已选定产出的屏会在图上明确画出
          「这一屏还没有产出」，不会留白糊弄。
        </p>
        <el-alert
          v-if="(detailPage?.screensWithoutSelection || []).length"
          type="warning"
          show-icon
          :closable="false"
          class="gate-alert"
          :title="`以下屏还没有已选定的产出：${(detailPage?.screensWithoutSelection || []).join('、')}`"
        />
        <el-alert
          v-if="detailPage && detailPage.rendererAvailable === false"
          type="error"
          show-icon
          :closable="false"
          class="gate-alert"
          title="渲染服务不可达，无法排版（请确认 creative-renderer 容器已启动）"
        />

        <el-table
          v-if="(detailPage?.versions || []).length"
          :data="detailPage?.versions || []"
          size="small"
          class="version-table"
        >
          <el-table-column label="版本" width="150">
            <template #default="{ row }">
              <div class="cell-main">v{{ asVersion(row).version }} · {{ asVersion(row).kindDesc }}</div>
              <div class="muted small">{{ asVersion(row).createTime }}</div>
            </template>
          </el-table-column>
          <el-table-column label="长图" width="130">
            <template #default="{ row }">
              {{ asVersion(row).pageWidth }}×{{ asVersion(row).pageHeight }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag size="small" :type="versionStatusType(asVersion(row).status)">
                {{ LAYOUT_VERSION_STATUS_LABELS[asVersion(row).status || ''] || asVersion(row).status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="渲染证据 / 审核意见" min-width="280">
            <template #default="{ row }">
              <div class="muted small">{{ asVersion(row).remark || '—' }}</div>
              <div v-if="asVersion(row).reviewComment" class="review-line">
                审核：{{ asVersion(row).reviewComment }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                text
                type="primary"
                :disabled="!asVersion(row).previewable"
                :loading="previewingId === String(asVersion(row).id)"
                @click="$emit('preview', asVersion(row))"
              >
                预览长图
              </el-button>
              <el-button
                size="small"
                text
                type="success"
                :disabled="asVersion(row).status !== 'RENDERED'"
                :loading="reviewingId === String(asVersion(row).id)"
                @click="$emit('review-version', asVersion(row), true)"
              >
                通过
              </el-button>
              <el-button
                size="small"
                text
                type="danger"
                :disabled="asVersion(row).status !== 'RENDERED'"
                :loading="reviewingId === String(asVersion(row).id)"
                @click="$emit('review-version', asVersion(row), false)"
              >
                打回
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <p v-else class="empty">
          还没有排版版本。先在上面通过视觉门、到「视觉方向与分镜」页逐屏出图并选定候选，再回来渲染。
        </p>
      </template>

      <!-- 成品图形态（海报 / 多图包）：按输出规格逐档出图，产出即交付产物 -->
      <template v-else>
        <p class="muted">
          按这个交付类型的<b>输出规格逐档</b>排版已选定的产出图：一档 = 一张成品图
          （品牌海报是 3:4 / 9:16 / 16:9 三档）。产出会登记成<b>交付产物</b>，可下载、可终审。
          还没有已选定产出图的屏会在图上明确写出缺什么，不会留白糊弄。
        </p>
        <el-table
          v-if="currentArtifact?.products?.length"
          :data="currentArtifact?.products || []"
          size="small"
          class="version-table"
        >
          <el-table-column label="规格" width="220">
            <template #default="{ row }">
              <div class="cell-main">{{ asProduct(row).screenNo || '—' }}</div>
              <div class="muted small">{{ asProduct(row).fileName }}</div>
            </template>
          </el-table-column>
          <el-table-column label="尺寸" width="140">
            <template #default="{ row }">
              {{ asProduct(row).width }}×{{ asProduct(row).height }}
            </template>
          </el-table-column>
          <el-table-column label="字节" width="120">
            <template #default="{ row }">{{ formatBytes(asProduct(row).bytes) }}</template>
          </el-table-column>
          <el-table-column label="内容校验和" min-width="200">
            <template #default="{ row }">
              <span class="mono small">{{ shortSha(asProduct(row).sha256) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <p v-else class="empty">
          还没有成品图。先在上面通过视觉门、到「海报概念与主视觉」页逐屏出图并选定候选，再回来生成。
        </p>
        <p v-if="currentArtifact?.remark" class="muted small">
          v{{ currentArtifact?.version }} · {{ currentArtifact?.remark }}
        </p>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type {
  DeliveryArtifactVO,
  DeliveryProductVO,
  DeliveryVO,
  DpDetailPageVO,
  DpDetailPageVersionVO,
  TagType
} from '@/api/creative/types';
import { LAYOUT_VERSION_STATUS_LABELS } from '@/api/creative/types';

/**
 * 「版式」这一步的内容（V0.2 R40 装配组件名 `LongPageCanvas`；R52 起服务两种形态）。
 *
 * <p>R40 之前它与"终审交付"挤在同一个面板里（原注释就写着"装配时要拆开"）：
 * 渲染与逐版本通过/打回属于 **LAYOUT**，而上传精修最终版与交付产物属于 **FINAL**。</p>
 *
 * <p>纯展示：渲染 / 预览 / 逐版本审核都发事件回页面（确认弹窗、接口调用、刷新与指引线都在页面）。
 * 长图预览弹窗也留在页面——它是页面级浮层，且要负责 blob URL 的释放。</p>
 *
 * @author creative
 */
const props = withDefaults(
  defineProps<{
    /** 详情页状态（含版本列表、渲染服务可达性）；长图形态用 */
    detailPage: DpDetailPageVO | null;
    /** 正在渲染长图 */
    rendering: boolean;
    /** 正在取预览的版本 id（按钮 loading） */
    previewingId: string;
    /** 正在审核的版本 id（按钮 loading） */
    reviewingId: string;
    /** 画布形态（页面按配置的渲染模式算；R52） */
    mode?: 'LONG_PAGE' | 'DELIVERY';
    /** 交付产物视图（成品图形态用） */
    delivery?: DeliveryVO | null;
    /** 正在生成交付产物 */
    delivering?: boolean;
  }>(),
  { mode: 'LONG_PAGE', delivery: null, delivering: false }
);

const isDelivery = computed(() => props.mode === 'DELIVERY');

/** 当前交付版本（历史按版本倒序，第一条就是当前） */
const currentArtifact = computed<DeliveryArtifactVO | null>(() => (props.delivery?.artifacts || [])[0] || null);

defineEmits<{
  /** 渲染机排版（长图） */
  (e: 'render'): void;
  /** 生成交付产物（成品图形态） */
  (e: 'render-delivery'): void;
  /** 预览某个版本的长图 */
  (e: 'preview', row: DpDetailPageVersionVO): void;
  /** 通过 / 打回某个版本 */
  (e: 'review-version', row: DpDetailPageVersionVO, approve: boolean): void;
}>();

function asVersion(row: unknown): DpDetailPageVersionVO {
  return row as DpDetailPageVersionVO;
}

function asProduct(row: unknown): DeliveryProductVO {
  return row as DeliveryProductVO;
}

function versionStatusType(status?: string): TagType {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'RENDERED') return 'warning';
  return 'info';
}

/** 字节数的可读写法（与终审面板同一口径，避免两处显示不一样） */
function formatBytes(bytes?: number | null): string {
  const value = Number(bytes || 0);
  if (!value) return '—';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

/** sha256 前 12 位（核对用，不占整行） */
function shortSha(sha?: string | null): string {
  const value = String(sha || '');
  return value ? value.slice(0, 12) : '—';
}
</script>

<style scoped lang="scss">
/* 这一步的内容样式（R40 从 review/index.vue 搬过来，一字未改） */
.layout-panel {
  display: block;
}

/* .panel 的外观统一在全局 creative-studio.scss（第 33 轮收口：这里原有一份逐字相同的副本） */
.panel h3 {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 0 0 6px;
  font-size: 15px;
}

/* .block-head 统一在全局 creative-studio.scss（第 33 轮收口：原副本与它权重相同、只靠注入顺序取胜） */
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.gate-alert {
  margin-top: 12px;
}

.review-line {
  margin-top: 4px;
  font-size: 12px;
  color: #a5b4fc;
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
