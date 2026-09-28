<template>
  <div class="style-ref">
    <div v-if="images.length" class="style-ref-list">
      <div v-for="img in images" :key="String(img.fileId)" class="style-ref-item">
        <div
          class="style-ref-thumb"
          :class="{ clickable: !!urlOf(img) }"
          :title="urlOf(img) ? '点击查看原图' : ''"
          @click="openFull(img)"
        >
          <img v-if="urlOf(img)" :src="urlOf(img)" :alt="img.fileName || '参考风格图片'" />
          <span v-else class="style-ref-fallback">{{ failedOf(img) ? '预览不可用' : '读取中…' }}</span>
        </div>
        <div class="style-ref-meta">
          <span class="style-ref-name" :title="img.fileName">{{ img.fileName || '未命名图片' }}</span>
          <el-button
            v-if="editable"
            link
            size="small"
            type="danger"
            :disabled="busy"
            @click="emit('remove', img)"
          >
            移除
          </el-button>
        </div>
      </div>
    </div>
    <p v-else class="style-ref-empty">
      {{ editable ? '还没有参考风格图片' : '品牌方没有上传参考风格图片' }}
    </p>

    <div v-if="editable" class="style-ref-actions">
      <el-upload
        :show-file-list="false"
        accept="image/*"
        :before-upload="beforePick"
        :disabled="busy || images.length >= max"
      >
        <el-button size="small" plain :loading="busy" :disabled="images.length >= max">
          ＋ 上传参考风格图片
        </el-button>
      </el-upload>
      <span class="hint">
        PNG/JPG，最多 {{ max }} 张；上传后立即保存，平面设计在 AI 视觉工厂能看到这些图（它们同时也是任务附件）。
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
  import type { UploadRawFile } from 'element-plus';
  import { ElMessage } from 'element-plus';
  import { fetchTaskFileBlobUrl } from '@/api/content/task';
  import { fetchCreativeFileBlobUrl } from '@/api/creative';
  import type { BrandBriefImage } from '@/api/content/brief/types';
  import { BRAND_BRIEF_STYLE_MAX } from '@/api/content/brief/types';

  /**
   * 参考风格图片条（品牌方给的"照这张图的风格做"）。
   *
   * <p><b>为什么做成共用组件</b>：内容任务页（品牌部，可上传/移除）与视觉项目页（平面设计，只读）
   * 展示的是同一批图，两处各写一遍必然长歪；而且 blob URL 的加载与回收**必须**跟着
   * 图片列表变化走，散在两个页面里极容易漏回收（内存泄漏）或在渲染前就被 revoke（图不出现）。</p>
   *
   * <p><b>为什么预览接口按 source 分</b>：内容域的附件取字节走 `/content/task/.../files/{id}/content`，
   * 创作域页面走自己的代理接口——两边鉴权通路不同，不能让后端替前端决定走哪条。</p>
   */
  const props = withDefaults(
    defineProps<{
      /** 任务/项目ID（取预览字节用） */
      taskId?: string | number;
      /** 图片明细（后端 styleRefImages） */
      images?: BrandBriefImage[];
      /** 是否可上传/移除（品牌部可，平面设计只读） */
      editable?: boolean;
      /** 上传中（父组件控制，避免连点重复上传） */
      busy?: boolean;
      /** 预览走哪条通路：内容任务页 content / 视觉项目页 creative */
      source?: 'content' | 'creative';
      /** 最多几张（默认与后端一致） */
      max?: number;
    }>(),
    {
      images: () => [],
      editable: false,
      busy: false,
      source: 'content',
      max: BRAND_BRIEF_STYLE_MAX
    }
  );

  const emit = defineEmits<{
    /** 选了文件（父组件负责上传 + 保存引用：上传通路属页面职责） */
    upload: [file: File];
    /** 点移除（父组件负责保存） */
    remove: [image: BrandBriefImage];
  }>();

  /** fileId → blob URL（响应式：普通 Map 的增删不触发重渲染，本仓库踩过这个坑） */
  const urls = ref<Record<string, string>>({});
  /** 读失败的 fileId：显示「预览不可用」，而不是一直「读取中…」 */
  const failed = ref<Record<string, boolean>>({});

  const keyOf = (img: BrandBriefImage) => String(img.fileId ?? '');
  const urlOf = (img: BrandBriefImage) => urls.value[keyOf(img)] || '';
  const failedOf = (img: BrandBriefImage) => !!failed.value[keyOf(img)];

  const revoke = (key: string) => {
    const old = urls.value[key];
    if (!old) return;
    URL.revokeObjectURL(old);
    const next = { ...urls.value };
    delete next[key];
    urls.value = next;
  };

  const fetchPreview = async (taskId: string | number, fileId: string | number, fileName?: string) =>
    props.source === 'creative'
      ? fetchCreativeFileBlobUrl(taskId, fileId)
      : fetchTaskFileBlobUrl(taskId, fileId, fileName);

  /**
   * 按当前 images 对齐预览：新增的去取、消失的回收。
   *
   * 逐个 await 而不是 Promise.all：取到一张显示一张，也避免一次打太多并发请求。
   */
  const syncPreviews = async () => {
    const alive = new Set(props.images.map((img) => keyOf(img)));
    Object.keys(urls.value).forEach((key) => {
      if (!alive.has(key)) revoke(key);
    });
    Object.keys(failed.value).forEach((key) => {
      if (!alive.has(key)) {
        const next = { ...failed.value };
        delete next[key];
        failed.value = next;
      }
    });
    if (!props.taskId) return;
    for (const img of props.images) {
      const key = keyOf(img);
      if (!key || urls.value[key] || failed.value[key]) continue;
      try {
        const url = await fetchPreview(props.taskId, img.fileId as string | number, img.fileName);
        // 取回期间列表可能已经变了（切任务/移除）：变了就立刻回收，不往 state 里塞
        if (!props.images.some((x) => keyOf(x) === key)) {
          URL.revokeObjectURL(url);
          continue;
        }
        urls.value = { ...urls.value, [key]: url };
      } catch {
        failed.value = { ...failed.value, [key]: true };
      }
    }
  };

  watch(() => [props.taskId, props.images.map(keyOf).join(',')], syncPreviews, { immediate: true });

  onBeforeUnmount(() => {
    Object.values(urls.value).forEach((url) => URL.revokeObjectURL(url));
    urls.value = {};
  });

  const openFull = (img: BrandBriefImage) => {
    const url = urlOf(img);
    if (url) window.open(url, '_blank');
  };

  /**
   * 选完文件就把原始 File 交给父组件，并返回 false 阻止 el-upload 自己发请求。
   *
   * 为什么不用 `http-request`：上传要带任务的数据等级、还要紧接着保存引用，属页面职责；
   * 组件只管"选文件"这一件事（before-upload 返回 false 即取消内置上传行为）。
   */
  const beforePick = (file: UploadRawFile) => {
    if (props.images.length >= props.max) {
      ElMessage.warning(`参考风格图片最多 ${props.max} 张，请先移除再上传`);
      return false;
    }
    emit('upload', file as unknown as File);
    return false;
  };
</script>

<style scoped lang="scss">
  .style-ref {
    width: 100%;
  }
  .style-ref-list {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }
  .style-ref-item {
    width: 116px;
  }
  .style-ref-thumb {
    display: grid;
    place-items: center;
    width: 116px;
    height: 86px;
    overflow: hidden;
    background: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
  }
  .style-ref-thumb.clickable {
    cursor: zoom-in;
  }
  .style-ref-thumb img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  .style-ref-fallback {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
  .style-ref-meta {
    display: flex;
    gap: 4px;
    align-items: center;
    justify-content: space-between;
    margin-top: 4px;
  }
  .style-ref-name {
    overflow: hidden;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .style-ref-empty {
    margin: 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
  .style-ref-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    margin-top: 8px;
  }
  .hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
</style>
