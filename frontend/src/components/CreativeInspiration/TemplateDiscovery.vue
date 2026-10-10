<template>
  <aside class="discovery" aria-label="AI 灵感推荐">
    <header><span class="eyebrow">✧ AI 灵感推荐</span><h2>灵感发现</h2><p>选择喜欢的模板，填入内容，开始创作</p></header>
    <div class="actions"><button :disabled="loading" @click="refresh">刷新模板</button><button :disabled="loading || pages < 2" @click="nextBatch">换一批</button><span>{{ feed?.online ? '在线模板 · 第 ' + feed.version + ' 版' : '缓存预览' }}</span></div>
    <el-input v-model="keyword" placeholder="搜索主题、风格或构图" clearable aria-label="搜索模板" />
    <nav class="categories" aria-label="模板分类"><button :class="{ active: !category }" @click="category = ''">全部</button><button v-for="c in categories" :key="c.id" :class="{ active: category === c.id }" @click="category = c.id">{{ c.zh }}</button></nav>
    <div v-if="items.length" class="masonry">
      <article v-for="item in items" :key="item.id + ':' + item.revision">
        <button class="open" :aria-label="'查看模板 ' + item.title.zh" @click="open(item)"><TemplateCover :id="item.id" :revision="item.revision" :title="item.title.zh" /></button>
        <button class="title" @click="open(item)">{{ item.title.zh || item.title.en }}</button>
        <p>{{ item.category }} · {{ item.outputSize.replace('x', ' × ') }}</p><span class="tag">{{ item.status === 'maintenance' ? '维护中' : '可复用模板' }}</span>
      </article>
    </div>
    <div v-else class="empty" role="status">{{ error || (loading ? '正在同步模板…' : '暂无匹配模板') }}</div>
    <footer><el-pagination v-if="total" v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="load" /><p>共 {{ total }} 个图像模板 · 每 10 分钟检查更新</p></footer>
    <section v-for="record in deferred" :key="record.requestId" class="request-status" role="status">
      <b>先前请求待核对</b><p>{{ creationMessage(record.result.error?.message) }}</p><span>任务 {{ record.result.task_id }}</span>
      <button :disabled="sending" @click="queryDeferred(record)">查询已保留请求</button>
    </section>
    <section v-if="pending" class="request-status" role="status">
      <b>{{ stateLabel(pending.status) }}</b><p>{{ creationMessage(pending.error?.message) }}</p>
      <button :disabled="sending" @click="recover">查询原请求</button>
      <button v-if="['queue_full', 'dispatch_not_submitted'].includes(pending.error?.code || '')" :disabled="sending" @click="retryQueue">恢复排队</button>
      <button v-if="pending.status === 'unknown' && pending.request_id && savedAction?.requestId === pending.request_id" :disabled="sending" @click="deferPending">保留原请求，使用其它模板</button>
      <button v-if="['succeeded', 'failed', 'archived'].includes(pending.status)" @click="clearPending">关闭</button>
      <span v-if="pending.task_id">任务已同步到下方任务列表</span>
    </section>
    <el-dialog v-model="dialog" :title="selected?.title.zh || selected?.title.en" width="760px" style="max-width: calc(100vw - 32px)" align-center destroy-on-close>
      <div v-if="selected" class="detail">
        <TemplateCover :id="selected.id" :revision="selected.revision" :title="selected.title.zh" />
        <div><span class="tag">{{ selected.category }}</span><h3>复用此模板</h3><p>图像创作 · {{ selected.outputSize.replace('x', ' × ') }} · 1 张</p>
          <el-form label-position="top"><el-form-item v-for="v in selected.variables" :key="v.key" :label="v.key === 'subject' ? '画面主体' : v.label.zh || v.label.en" :required="v.required">
            <el-select v-if="v.type === 'select'" v-model="variables[v.key]"><el-option v-for="option in v.options" :key="option" :label="option" :value="option" /></el-select>
            <el-input v-else v-model="variables[v.key]" :maxlength="v.max_len || 200" show-word-limit />
          </el-form-item></el-form>
          <p v-if="!selected.variables.length">模板已包含完整创作描述，带入表单后可继续编辑。</p>
          <p v-if="formError" class="error" role="alert">{{ formError }}</p>
          <p>文生图模板，无需参考素材。带入后可调整描述与输出设置，确认后再生成。</p>
          <el-button v-hasPermi="['image:creation:submit']" type="primary" :loading="preparing" :disabled="busy || preparing || selected.status === 'maintenance'" @click="applyTemplate">{{ selected.status === 'maintenance' ? '模板维护中' : '带入创作表单' }}</el-button>
        </div>
      </div>
    </el-dialog>
  </aside>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useUserStore } from '@/store/modules/user';
import { listTemplates, getTemplate, prepareTemplate, generateTemplate, getTemplateGeneration, retryTemplateGeneration, type FeedTemplate, type FeedPage, type TemplateSubmission, type TemplateGeneration } from '@/api/image/templates';
import TemplateCover from './TemplateCover.vue';
import type { InspirationRoute } from './types';
import { creationMessage } from './template-reuse';
const props = defineProps<{ busy?: boolean }>();
const busy = computed(() => props.busy === true);
const emit = defineEmits<{ 'task-created': []; apply: [route: InspirationRoute, title: string] }>();
const preparing = ref(false);
const userStore = useUserStore();
const storageKey = computed(() => 'hotter-template-action-v2-' + String(userStore.userId));
const items = ref<FeedTemplate[]>([]), categories = ref<FeedPage['categories']>([]), feed = ref<FeedPage['feed']>();
const page = ref(1), total = ref(0), category = ref(''), keyword = ref(''), loading = ref(false), error = ref('');
const pageSize = 6, pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const selected = ref<FeedTemplate>(), dialog = ref(false), variables = ref<Record<string, string>>({}), sending = ref(false), formError = ref('');
const pending = ref<TemplateGeneration>(), savedAction = ref<{ body: TemplateSubmission; requestId?: string }>();
const deferred = ref<{ body: TemplateSubmission; requestId: string; result: TemplateGeneration }[]>([]);
let sequence = 0, debounce: ReturnType<typeof setTimeout> | undefined, interval: ReturnType<typeof setInterval> | undefined;
async function load() {
  const current = ++sequence; loading.value = true; error.value = '';
  try { const r = await listTemplates({ page: page.value, page_size: pageSize, category: category.value || undefined, keyword: keyword.value.trim() || undefined });
    if (current !== sequence) return; items.value = r.data.items; total.value = r.data.total; categories.value = r.data.categories; feed.value = r.data.feed;
    if (page.value > pages.value) { page.value = pages.value; void load(); }
  } catch { if (current === sequence) error.value = '模板读取失败，请刷新重试'; }
  finally { if (current === sequence) loading.value = false; }
}
function refresh() { page.value = 1; void load(); }
function nextBatch() { page.value = page.value < pages.value ? page.value + 1 : 1; void load(); }
async function open(item: FeedTemplate) {
  if (preparing.value) return;
  formError.value = ''; variables.value = {};
  try { const r = await getTemplate(item.id); selected.value = r.data; dialog.value = true; }
  catch { void load(); }
}
function persist() {
  localStorage.setItem(storageKey.value + '-pending', JSON.stringify(deferred.value));
  if (savedAction.value) localStorage.setItem(storageKey.value, JSON.stringify(savedAction.value));
  else localStorage.removeItem(storageKey.value);
}
function clearPending() { savedAction.value = undefined; pending.value = undefined; persist(); }
function deferPending() {
  if (sending.value || !savedAction.value || pending.value?.status !== 'unknown' || !pending.value.request_id || savedAction.value?.requestId !== pending.value.request_id) return;
  if (!deferred.value.some(record => record.requestId === pending.value!.request_id)) deferred.value.push({ body: savedAction.value.body, requestId: pending.value.request_id, result: pending.value });
  clearPending();
}
async function queryDeferred(record: (typeof deferred.value)[number]) {
  if (sending.value) return; sending.value = true;
  try {
    const r = await getTemplateGeneration(record.requestId); record.result = r.data;
    if (['succeeded', 'failed', 'archived'].includes(r.data.status)) deferred.value = deferred.value.filter(item => item.requestId !== record.requestId);
    persist();
  } finally { sending.value = false; }
}
async function applyTemplate() {
  if (!selected.value || busy.value || preparing.value || selected.value.status === 'maintenance') return;
  const template = selected.value;
  const title = template.title.zh || template.title.en;
  preparing.value = true; formError.value = '';
  try {
    const r = await prepareTemplate({ template_id: template.id, revision: template.revision, variables: { ...variables.value } });
    if (busy.value || !dialog.value || selected.value !== template) return;
    emit('apply', { media: 'image', source: 'cloud', capability: r.data.capability, workflowCode: 'cloud-bluocto-t2i', model: r.data.model, prompt: r.data.prompt, output: r.data.output, templateTitle: title, reason: '', referenceHint: '' }, title);
    dialog.value = false;
  } catch (e: unknown) {
    const response = (e as { response?: { data?: { msg?: string; data?: { errors?: { field: string; rule: string }[] } } } }).response?.data;
    const invalid = response?.data?.errors;
    formError.value = invalid?.length ? '请检查模板内容：' + invalid.map(item => item.rule === 'required' ? '请填写必填内容' : item.rule === 'max_length' ? '内容超过长度限制' : item.rule === 'select_option' ? '请选择有效选项' : '模板内容暂不可用，请修改后重试').filter((v,i,a) => a.indexOf(v) === i).join('；') : creationMessage(response?.msg) || '模板带入失败，请刷新后重试';
  } finally { preparing.value = false; }
}
async function recover() {
  if (!savedAction.value || sending.value) return;
  sending.value = true; formError.value = '';
  const newSubmission = !savedAction.value.requestId;
  try {
    const r = savedAction.value.requestId ? await getTemplateGeneration(savedAction.value.requestId) : await generateTemplate(savedAction.value.body);
    savedAction.value.requestId = r.data.request_id; persist(); pending.value = r.data; if (newSubmission) dialog.value = false;
    if (r.data.task_id) emit('task-created');
    if (['succeeded', 'failed', 'archived'].includes(r.data.status)) { savedAction.value = undefined; persist(); }
  } catch (e: unknown) {
    const http = e as { response?: { status?: number; data?: { msg?: string; data?: { errors?: { field: string; rule: string }[] } } } };
    const status = http.response?.status;
    formError.value = creationMessage(http.response?.data?.msg) || '响应未确认，请查询原请求，避免重复生成';
    if ([404, 409, 410, 422].includes(status || 0) && !savedAction.value.requestId) {
      const rules = http.response?.data?.data?.errors;
      if (rules?.length) formError.value += '：' + rules.map(rule => rule.field + ' (' + rule.rule + ')').join('；');
      savedAction.value = undefined; persist();
      if (status !== 422) void load();
    } else pending.value = { request_id: savedAction.value.requestId || '', status: 'unknown', error: { code: 'unknown', message: formError.value } };
  } finally { sending.value = false; }
}
async function retryQueue() {
  if (!pending.value?.request_id || sending.value) return; sending.value = true;
  try { const r = await retryTemplateGeneration(pending.value.request_id); pending.value = r.data; emit('task-created'); }
  finally { sending.value = false; }
}
function stateLabel(state: string) { return ({ pending: '请求已保存', submitted: '正在生成', succeeded: '生成完成', failed: '生成失败', unknown: '结果待确认', archived: '请求已归档' } as Record<string, string>)[state] || state; }
watch([category, keyword], () => { if (debounce) clearTimeout(debounce); page.value = 1; debounce = setTimeout(load, 300); });
onMounted(() => {
  try { const records = JSON.parse(localStorage.getItem(storageKey.value + '-pending') || '[]'); if (Array.isArray(records)) deferred.value = records.filter(record => typeof record?.requestId === 'string' && record?.body?.template_id && record?.result?.status === 'unknown'); } catch { deferred.value = []; }
  try { const saved = localStorage.getItem(storageKey.value); if (saved) { savedAction.value = JSON.parse(saved); void recover(); } } catch { localStorage.removeItem(storageKey.value); }
  void load(); interval = setInterval(() => { if (!document.hidden) { if (!loading.value) void load(); if (savedAction.value?.requestId && !sending.value) void recover(); } }, 15000);
});
onBeforeUnmount(() => { sequence++; if (debounce) clearTimeout(debounce); if (interval) clearInterval(interval); });
</script>
<style scoped>
.discovery { display: flex; flex-direction: column; align-self: stretch; padding: 24px; min-width: 0; border: 1px solid #e0e5f2; border-radius: 16px; background: #fff; color: #293751; }
.eyebrow { color: #7961ca; font-size: 13px; } h2 { margin: 10px 0 8px; font-size: 24px; } header p, footer p, article p, .detail p { color: #8391ab; font-size: 12px; line-height: 1.8; }
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 16px 0; } .actions span { color: #8793a9; font-size: 11px; }
button { cursor: pointer; color: #687b9b; border: 1px solid #e1e5f2; background: #fff; padding: 8px 11px; border-radius: 8px; font: inherit; font-size: 12px; } button:disabled { cursor: not-allowed; opacity: .5; }
.categories { display: flex; flex-wrap: wrap; gap: 8px; margin: 16px 0; } .active { border-color: #8566f5; background: #f0eafb; color: #7954d6; }
.masonry { columns: 2; column-gap: 14px; } article { break-inside: avoid; padding-bottom: 18px; } .open { display: block; width: 100%; padding: 0; border: 0; border-radius: 10px; } .title { border: 0; padding: 12px 0 0; background: transparent; color: #304161; text-align: left; line-height: 1.6; } article p { margin: 6px 0; } .tag { padding: 4px 7px; font-size: 10px; color: #7956cc; background: #f2edfb; border-radius: 5px; }
.empty { padding: 70px 15px; text-align: center; color: #8692a8; } footer { margin-top: auto; padding-top: 20px; } footer :deep(.el-pagination) { justify-content: center; }
.detail { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; align-items: start; } .detail :deep(.el-select) { width: 100%; } .error { color: #ba5561 !important; }
.request-status { margin-top: 16px; border-radius: 10px; padding: 14px; background: #f7f4fc; font-size: 12px; } .request-status span { display: block; margin-top: 10px; }
@media (max-width: 640px) { .detail { grid-template-columns: 1fr; } .discovery { padding: 16px; } }
</style>
