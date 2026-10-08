<template>
  <section class="materials">
    <h3>{{ mode === 'MULTI' ? `参考图片（2–${maxReferences} 张）` : '参考图片' }}</h3>
    <label class="upload-box">
      <input type="file" accept="image/png,image/jpeg,image/webp" :multiple="mode === 'MULTI'" :disabled="busy || processing" @change="choose" />
      <span>＋ {{ mode === 'MULTI' ? '选择参考图片' : '选择原图' }}</span>
      <small>PNG / JPG / WEBP · 单张 ≤{{ Math.floor(maxFileBytes / 1024 / 1024) }}MB · 合计 ≤40MB</small>
    </label>
    <div v-if="references.length" class="reference-grid">
      <figure v-for="(item, index) in references" :key="item.url">
        <img :src="item.url" :alt="`参考图 ${index + 1}`" />
        <figcaption>{{ index + 1 }} · {{ item.file.name }}</figcaption>
      </figure>
    </div>
    <template v-if="mode === 'MASK' && references.length">
      <h3>标记修改区域</h3>
      <p>在原图上涂抹需要重绘的区域，紫色标记将导出为透明蒙版。</p>
      <div class="mask-stage" :style="{ backgroundImage: `url(${references[0].url})` }">
        <canvas ref="maskCanvas" @pointerdown="startPaint" @pointermove="paint" @pointerup="finishPaint" @pointercancel="finishPaint" />
      </div>
      <div class="tools"><label>画笔 <input v-model.number="brush" type="range" min="10" max="160" :disabled="busy || processing" /></label><button type="button" :disabled="busy || processing" @click="clearMask">清除标记</button></div>
    </template>
    <template v-if="mode === 'OUTPAINT' && references.length">
      <h3>扩展范围</h3>
      <div class="padding-grid"><label v-for="side in sides" :key="side.key">{{ side.label }}（px）<input v-model.number="padding[side.key]" type="number" min="0" max="512" step="64" :disabled="busy || processing" @change="prepare" /></label></div>
      <p>原图放在扩展画布中央，新增区域会自动生成透明蒙版。</p>
      <img v-if="expandedUrl" :src="expandedUrl" alt="扩展画布预览" class="expanded" />
    </template>
    <p v-if="processing" role="status">正在处理参考素材…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="!enabled">当前尚未满足云端提交条件，可预览素材与构图；素材不会发送给供应商。</p>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue';
import { uploadImageAsset } from '@/api/image';
import type { ImageCloudCapability } from './cloud-image-capabilities';
const props = defineProps<{ mode: ImageCloudCapability; enabled: boolean; busy?: boolean; maxReferences?: number; maxFileBytes?: number }>();
const maxReferences = computed(() => props.maxReferences ?? 16);
const maxFileBytes = computed(() => props.maxFileBytes ?? 20*1024*1024);
const emit = defineEmits<{ change: [value: { ids: (string | number)[]; maskId?: string | number; ready: boolean; processing: boolean }] }>();
const references = ref<{ file: File; url: string; image: HTMLImageElement }[]>([]);
const processing = ref(false), error = ref(''), expandedUrl = ref('');
const maskCanvas = ref<HTMLCanvasElement>(), brush = ref(50);
const padding = reactive({ left: 256, right: 256, top: 0, bottom: 0 });
const sides = [{ key: 'left', label: '左' }, { key: 'right', label: '右' }, { key: 'top', label: '上' }, { key: 'bottom', label: '下' }] as const;
let drawing = false, painted = false, requestVersion = 0;
function publish(ids: (string | number)[] = [], maskId?: string | number, ready = false) { emit('change', { ids, maskId, ready, processing: processing.value }); }
function release() { references.value.forEach(r => URL.revokeObjectURL(r.url)); if (expandedUrl.value) URL.revokeObjectURL(expandedUrl.value); expandedUrl.value = ''; }
async function choose(event: Event) {
  const input = event.target as HTMLInputElement, files = Array.from(input.files ?? []); input.value = '';
  if (!files.length) return;
  error.value = ''; requestVersion++; processing.value = true; publish();
  const loaded: typeof references.value = [];
  try {
    if (files.length > (props.mode === 'MULTI' ? maxReferences.value : 1) || files.some(f => f.size > maxFileBytes.value || !['image/png','image/jpeg','image/webp'].includes(f.type)) || files.reduce((n,f) => n+f.size,0) > 40 * 1024 * 1024) throw new Error('请检查文件类型、数量与大小限制');
    for (const file of files) {
      const url = URL.createObjectURL(file), image = new Image();
      try { image.src = url; await image.decode(); if (image.naturalWidth * image.naturalHeight > 16 * 1024 * 1024) throw new Error('单张参考图须小于 16MP'); }
      catch (e) { URL.revokeObjectURL(url); throw e; }
      loaded.push({ file, url, image });
    }
    release(); references.value = loaded; painted = false;
    await nextTick();
    if (maskCanvas.value) { maskCanvas.value.width = loaded[0].image.naturalWidth; maskCanvas.value.height = loaded[0].image.naturalHeight; }
    await prepare();
  } catch (e) { loaded.forEach(r => URL.revokeObjectURL(r.url)); error.value = e instanceof Error ? e.message : '素材处理失败'; processing.value = false; publish(); }
}
function startPaint(event: PointerEvent) {
  if (props.busy || processing.value) return;
  drawing = true; maskCanvas.value?.setPointerCapture(event.pointerId); paint(event);
}
function paint(event: PointerEvent) {
  const canvas = maskCanvas.value; if (!drawing || !canvas) return;
  const box = canvas.getBoundingClientRect(), ctx = canvas.getContext('2d')!;
  ctx.fillStyle = 'rgba(124,92,255,0.8)'; ctx.beginPath();
  ctx.arc((event.clientX-box.left)*canvas.width/box.width, (event.clientY-box.top)*canvas.height/box.height, brush.value*canvas.width/box.width/2, 0, Math.PI*2); ctx.fill(); painted = true; publish();
}
function finishPaint() { if (!drawing) return; drawing = false; void prepare(); }
function clearMask() { painted = false; maskCanvas.value?.getContext('2d')?.clearRect(0,0,maskCanvas.value.width,maskCanvas.value.height); publish(); }
function png(canvas: HTMLCanvasElement): Promise<File> { return new Promise((resolve,reject) => canvas.toBlob(blob => blob ? resolve(new File([blob], 'canvas.png', {type:'image/png'})) : reject(new Error('无法生成 PNG')), 'image/png')); }
async function prepare() {
  const version = ++requestVersion;
  processing.value = true; error.value = ''; publish();
  try {
    let files = references.value.map(r => r.file), mask: File | undefined;
    if (!files.length || (props.mode === 'MULTI' && files.length < 2)) { publish(); return; }
    if (props.mode === 'MASK') {
      if (!painted || !maskCanvas.value) { publish(); return; }
      const maskImage = document.createElement('canvas'); maskImage.width = maskCanvas.value.width; maskImage.height = maskCanvas.value.height;
      const ctx = maskImage.getContext('2d')!, marks = maskCanvas.value.getContext('2d')!.getImageData(0,0,maskImage.width,maskImage.height);
      const data = ctx.createImageData(maskImage.width,maskImage.height);
      for (let i=3;i<data.data.length;i+=4) data.data[i] = marks.data[i] ? 0 : 255;
      ctx.putImageData(data,0,0); mask = await png(maskImage);
    }
    if (props.mode === 'OUTPAINT') {
      if (Object.values(padding).some(n => !Number.isInteger(n) || n < 0 || n > 512) || !Object.values(padding).some(n => n > 0)) throw new Error('请选择 0–512px 的有效扩展范围');
      const source = references.value[0].image, canvas = document.createElement('canvas');
      canvas.width = source.naturalWidth + padding.left + padding.right; canvas.height = source.naturalHeight + padding.top + padding.bottom;
      if (canvas.width * canvas.height > 16*1024*1024) throw new Error('扩展画布超过 16MP，请缩小范围');
      canvas.getContext('2d')!.drawImage(source,padding.left,padding.top); const file = await png(canvas); files=[file];
      if (expandedUrl.value) URL.revokeObjectURL(expandedUrl.value); expandedUrl.value=URL.createObjectURL(file);
      const maskImage=document.createElement('canvas'); maskImage.width=canvas.width; maskImage.height=canvas.height;
      const ctx=maskImage.getContext('2d')!; ctx.fillStyle='#000'; ctx.fillRect(padding.left,padding.top,source.naturalWidth,source.naturalHeight); mask=await png(maskImage);
    }
    if (!props.enabled) return;
    if (files.some(f => f.size > maxFileBytes.value) || files.reduce((n,f)=>n+f.size,mask?.size??0) > 40*1024*1024 || (mask && mask.size >= 4*1024*1024)) throw new Error('处理后的素材超出上传限制，请缩小图片');
    const ids: (string|number)[]=[];
    for (const file of files) { const result=await uploadImageAsset(file); ids.push(result.data.assetId); }
    const maskId=mask ? (await uploadImageAsset(mask)).data.assetId : undefined;
    if (version===requestVersion) { processing.value=false; publish(ids,maskId,true); }
  } catch(e) { if (version===requestVersion) error.value=e instanceof Error?e.message:'素材处理或上传失败，请重新选择'; }
  finally { if (version===requestVersion && processing.value) { processing.value=false; publish(); } }
}
onBeforeUnmount(() => { requestVersion++; release(); });
</script>
<style scoped>
h3{font-size:13px;margin:0 0 10px}p,small{font-size:11px;color:var(--t2);line-height:1.7}.upload-box{display:flex;flex-direction:column;gap:6px;padding:20px;border:1px dashed var(--line2);border-radius:10px;cursor:pointer;text-align:center;color:var(--p)}.upload-box input{max-width:100%;font-size:11px}.reference-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin:12px 0}figure{margin:0;min-width:0}figure img{width:100%;height:120px;object-fit:contain;background:var(--sunken);border-radius:8px}figcaption{font-size:10px;color:var(--t2);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.mask-stage{background-size:100% 100%;line-height:0;border-radius:8px;overflow:hidden}.mask-stage canvas{width:100%;height:auto;touch-action:none;cursor:crosshair}.tools{display:flex;align-items:center;gap:10px;font-size:11px;margin:10px 0}.tools label{display:flex;align-items:center}.tools button{background:var(--tint);border:1px solid var(--line2);border-radius:6px;padding:6px;color:var(--p)}.padding-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.padding-grid label{font-size:11px;color:var(--t2)}.padding-grid input{display:block;width:100%;box-sizing:border-box;margin-top:6px;border:1px solid var(--line2);border-radius:6px;background:var(--sunken);color:var(--t1);padding:8px}.expanded{width:100%;max-height:300px;object-fit:contain;background:repeating-conic-gradient(var(--sunken) 0% 25%,var(--surface) 0% 50%) 0/16px 16px}.error{color:var(--danger,#cf4155)}
</style>
