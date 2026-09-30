<template>
  <!-- 单根容器：`src/views` 下的组件会被 `vite:check-transition` 检查（外层路由用 <transition> 包裹）。 -->
  <div class="layout-panel">
    <section class="panel" data-layout-section="LAYOUT">
      <div class="block-head">
        <h3>长图排版</h3>
        <div class="head-actions">
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
        </div>
      </div>

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
    </section>
  </div>
</template>

<script setup lang="ts">
import type { DpDetailPageVO, DpDetailPageVersionVO, TagType } from '@/api/creative/types';
import { LAYOUT_VERSION_STATUS_LABELS } from '@/api/creative/types';

/**
 * 「长图排版」这一步的内容（V0.2 R40，装配组件名 `LongPageCanvas`）。
 *
 * <p>R40 之前它与"终审交付"挤在同一个面板里（原注释就写着"装配时要拆开"）：
 * 渲染与逐版本通过/打回属于 **LAYOUT**，而上传精修最终版与交付产物属于 **FINAL**。</p>
 *
 * <p>纯展示：渲染 / 预览 / 逐版本审核都发事件回页面（确认弹窗、接口调用、刷新与指引线都在页面）。
 * 长图预览弹窗也留在页面——它是页面级浮层，且要负责 blob URL 的释放。</p>
 *
 * @author creative
 */
defineProps<{
  /** 详情页状态（含版本列表、渲染服务可达性） */
  detailPage: DpDetailPageVO | null;
  /** 正在渲染 */
  rendering: boolean;
  /** 正在取预览的版本 id（按钮 loading） */
  previewingId: string;
  /** 正在审核的版本 id（按钮 loading） */
  reviewingId: string;
}>();

defineEmits<{
  /** 渲染机排版 */
  (e: 'render'): void;
  /** 预览某个版本的长图 */
  (e: 'preview', row: DpDetailPageVersionVO): void;
  /** 通过 / 打回某个版本 */
  (e: 'review-version', row: DpDetailPageVersionVO, approve: boolean): void;
}>();

function asVersion(row: unknown): DpDetailPageVersionVO {
  return row as DpDetailPageVersionVO;
}

function versionStatusType(status?: string): TagType {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'RENDERED') return 'warning';
  return 'info';
}
</script>

<style scoped lang="scss">
/* 这一步的内容样式（R40 从 review/index.vue 搬过来，一字未改） */
.layout-panel {
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
