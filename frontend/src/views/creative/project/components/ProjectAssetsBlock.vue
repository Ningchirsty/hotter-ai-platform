<template>
  <section class="block" data-block="PROJECT_ASSETS">
    <div class="block-head">
      <h4>1. 产品图与参考图</h4>
      <div class="block-actions">
        <span class="muted">{{ files.length }} 张</span>
        <el-tag :type="productImage?.configured ? 'success' : 'warning'" size="small" effect="dark">
          产品图{{ productImage?.configured ? '：' + (productImage.fileName || '已配置') : '未配置' }}
        </el-tag>
      </div>
    </div>
    <p class="hint">
      这里有两样不同的东西，别混：<b>产品图</b> = 产品主数据里唯一的那张照片，是
      <b>产品保真基准</b>（质检拿它比对生成图里的产品有没有走形；只用提示词约束，不自动判死）；
      <b>参考图</b> = 本次任务喂给模型的输入图，可以有好多张，是
      <b>一致性基准</b>（质检拿它比对画面是否走样，不一致会被筛除）。
      角色徽标按后端记录的来源如实展示，不靠推测。
    </p>
    <p class="hint">
      上传参考图<b>不会改变项目阶段</b>（已完成的项目也能补图）；只有下面的勾选框会把某张图登记成产品图。
    </p>
    <p class="hint">
      <template v-if="!canBindProductImage">
        <span class="fact-error">该项目没有关联产品，无法登记产品图（后端会直接拒绝）——请先在项目里选择产品。</span>
      </template>
      <template v-else-if="!productImage?.configured">
        把上传的产品照片登记为产品图：勾选下面的「同时设为该产品的产品图」，或在某张图片上点「设为产品图」。
      </template>
      <template v-else>
        {{ productImageOrigin || '产品图已配置' }}
        <span v-if="productImage.setAt" class="muted">· 设定于 {{ formatTime(productImage.setAt) }}</span>
      </template>
    </p>
    <div class="ref-row">
      <div
        v-for="file in files"
        :key="String(file.fileId)"
        class="ref-card"
        :class="{ active: String(file.fileId) === String(selectedFileId) }"
        @click="$emit('update:selectedFileId', file.fileId ?? '')"
      >
        <img v-if="urlOf('file-' + file.fileId)" :src="urlOf('file-' + file.fileId)" :alt="file.fileName" />
        <span v-else class="ref-loading">读取中…</span>
        <span class="ref-name">{{ file.fileName }}</span>
        <el-tag
          v-if="file.sourceType"
          size="small"
          effect="plain"
          :type="fileSourceType(file.sourceType)"
        >
          {{ fileSourceLabel(file.sourceType) }}
        </el-tag>
        <span v-if="isProductImageFile(file)" class="ref-badge ok">产品保真基准</span>
        <span v-else-if="String(file.fileId) === String(selectedFileId)" class="ref-badge">当前参考图</span>
        <el-button
          v-if="canBindProductImage && file.fileId != null && !isProductImageFile(file)"
          size="small"
          text
          type="primary"
          :loading="bindingProductImage === String(file.fileId)"
          @click.stop="$emit('bind-product-image', file)"
        >
          设为产品图
        </el-button>
      </div>
      <div class="ref-upload-wrap">
        <el-checkbox
          :model-value="asProductImage"
          :disabled="!canBindProductImage"
          class="as-product-image"
          @update:model-value="(v: string | number | boolean) => $emit('update:asProductImage', Boolean(v))"
        >
          同时设为该产品的产品图
        </el-checkbox>
        <el-upload
          class="ref-upload"
          :show-file-list="false"
          accept="image/png,image/jpeg,image/webp"
          :http-request="doUpload"
        >
          <div class="upload-slot">
            <span class="plus">＋</span>
            <span>{{ asProductImage ? '上传并设为产品图' : '上传参考图' }}</span>
            <span class="hint">PNG/JPG/WEBP，≤20MB</span>
          </div>
        </el-upload>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { UploadRequestOptions } from 'element-plus';
import type { CpTaskFileVO } from '@/api/content/task/types';
import type { ProjectProductImageVO, TagType } from '@/api/creative/types';

/**
 * 项目页区块①「产品图与参考图」（V0.2 R32，文档 §23 的 ProjectInputPanel 一部分）。
 *
 * <p><b>为什么要拆</b>：项目页是一个 3000 行、11 万字节的 `.vue`，六个区块全在里面；
 * 装配对照里它们只能标成"页面内区块"（装配时被跳过）。拆成组件之后，每一块有明确的
 * 输入输出边界，装配运行时才有可能真的按配置把某一步渲染成这个组件。</p>
 *
 * <p><b>状态仍在页面（唯一状态宿主）</b>：本组件只拿"它显示的数据 + 它触发的动作"，
 * 不持有任何数据、不发任何请求——这样拆分不会产生两个真相源，
 * 页面切项目时的清理逻辑也完全不用改（R7 那次的竞态就是这么来的）。</p>
 *
 * @author creative
 */
defineProps<{
  /** 图片类附件（页面已按 fileKind 过滤） */
  files: CpTaskFileVO[];
  /** 产品图信息（未配置时 configured=false） */
  productImage: ProjectProductImageVO | null;
  /** 产品图来源说明（来自本项目/其它项目） */
  productImageOrigin: string;
  /** 当前选中的参考图附件ID */
  selectedFileId: string | number;
  /** 是否允许登记产品图（项目必须关联产品） */
  canBindProductImage: boolean;
  /** 是否"同时设为产品图" */
  asProductImage: boolean;
  /** 正在登记产品图的附件ID（按钮 loading） */
  bindingProductImage: string;
  /** blob URL 台账读取（页面维护对象URL生命周期） */
  urlOf: (key: string) => string;
  /** 角色徽标文案 */
  fileSourceLabel: (type?: string) => string;
  /** 角色徽标样式 */
  fileSourceType: (type?: string) => TagType;
  /** 某张附件是不是当前产品图 */
  isProductImageFile: (file: CpTaskFileVO) => boolean;
  /** 时间格式化 */
  formatTime: (value?: string) => string;
  /**
   * 上传处理（走页面的上传逻辑：对象键规则、登记产品图、错误提示只维护一处）。
   *
   * <p>为什么这个用函数 prop 而不是 emit：`el-upload` 的 `http-request` 有返回值契约
   * （必须返回 Promise 或 XHR，用来驱动自身的上传态），emit 是同步无返回值的，
   * 包一层反而丢语义。其余纯动作仍走 emit。</p>
   */
  doUpload: (options: UploadRequestOptions) => Promise<unknown> | XMLHttpRequest;
}>();

defineEmits<{
  (e: 'update:selectedFileId', value: string | number): void;
  (e: 'update:asProductImage', value: boolean): void;
  (e: 'bind-product-image', file: CpTaskFileVO): void;
}>();
</script>
