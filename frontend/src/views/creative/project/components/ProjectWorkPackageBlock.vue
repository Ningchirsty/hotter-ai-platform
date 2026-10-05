<template>
  <section class="block" data-block="PROJECT_WORK_PACKAGE">
    <div class="block-head">
      <h4>{{ stepHeading }}开工包（品牌部签发的交接凭证）</h4>
      <div class="block-actions">
        <el-tag v-if="loaded && view?.available" size="small" effect="dark" :type="statusType">
          {{ view?.statusDesc || view?.status }}
        </el-tag>
        <el-tag v-else-if="loaded" size="small" type="info">未生成</el-tag>
        <span v-if="view?.issuedAt" class="muted">
          签发：{{ parseTime(view?.issuedAt) }} · {{ view?.issuedByName || view?.issuedBy }}
        </span>
        <span v-if="view?.snapshotVersion" class="muted">冻结事实版本 v{{ view?.snapshotVersion }}</span>
        <el-button size="small" plain :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <p v-if="view?.note" class="hint">{{ view?.note }}</p>

    <p v-if="error" class="wp-error">
      {{ error }}
    </p>
    <p v-else-if="!loaded" class="muted">读取中…</p>
    <p v-else-if="!view?.available" class="wp-empty">
      品牌部还没有生成开工包。这不阻塞你做——但<b>缺口与品牌红线</b>只能从这份包里读到，
      没有它时请把"哪些信息还不确定"当成待确认项，别默认按自己的理解补全。
    </p>

    <template v-else>
      <p v-if="parseWarning" class="wp-error">{{ parseWarning }}</p>

      <!-- 品牌红线（C8 起取自 Brief，不再是空数组） -->
      <div class="wp-grid">
        <div class="wp-card">
          <div class="wp-card-head"><b>品牌红线（禁用词）</b><span class="muted">出图时逐条进负向提示词</span></div>
          <ul v-if="(content?.copy?.forbidden || []).length" class="wp-list">
            <li v-for="(t, i) in content?.copy?.forbidden || []" :key="'f' + i">{{ t }}</li>
          </ul>
          <p v-else class="muted">品牌部没有声明禁用词（或生成这一包时还没填）。</p>
          <p v-if="content?.copy?.forbiddenSource" class="wp-src">{{ content?.copy?.forbiddenSource }}</p>
        </div>
        <div class="wp-card">
          <div class="wp-card-head"><b>必显信息</b><span class="muted">出图时进正向提示词</span></div>
          <ul v-if="(content?.copy?.confirmed || []).length" class="wp-list">
            <li v-for="(t, i) in content?.copy?.confirmed || []" :key="'c' + i">{{ t }}</li>
          </ul>
          <p v-else class="muted">品牌部没有声明必显信息（或生成这一包时还没填）。</p>
          <p v-if="content?.copy?.mustShowSource" class="wp-src">{{ content?.copy?.mustShowSource }}</p>
        </div>
      </div>

      <!-- 尺寸：两个权威并列，不合成一个 -->
      <div class="wp-card">
        <div class="wp-card-head"><b>尺寸</b></div>
        <dl class="wp-kv">
          <div>
            <dt>品牌部确认的要求</dt>
            <dd>{{ content?.spec?.outputSize || '未确认（包里为空）' }}</dd>
          </div>
          <div>
            <dt>排版实际使用的规格</dt>
            <dd>{{ view?.renderOutputSize || '未配置输出规格' }}</dd>
          </div>
        </dl>
        <p v-if="content?.spec?.outputSizeNote" class="wp-src">{{ content?.spec?.outputSizeNote }}</p>
      </div>

      <!-- 缺口 -->
      <div class="wp-card">
        <div class="wp-card-head">
          <b>缺口与替代</b>
          <span class="muted">可按替代方案开工，但必须确定替代方案或补齐</span>
        </div>
        <ul v-if="(content?.gaps || []).length" class="wp-list">
          <li v-for="(g, i) in content?.gaps || []" :key="'g' + i">
            {{ g.fieldName || g.fieldCode }}：{{ g.note || '未说明' }}
          </li>
        </ul>
        <p v-else class="muted">没有缺口：该交付类型的条件项都已满足。</p>
      </div>

      <!-- 已确认事实 -->
      <div class="wp-card">
        <div class="wp-card-head">
          <b>已确认事实</b>
          <span class="muted">事实的权威在内容域；这里显示的是签发时冻结的那一版</span>
        </div>
        <el-table v-if="(content?.confirmedFacts || []).length" :data="content?.confirmedFacts || []" size="small">
          <el-table-column prop="fieldName" label="字段" width="160" show-overflow-tooltip />
          <el-table-column prop="value" label="值" min-width="180" show-overflow-tooltip />
          <el-table-column prop="sourceLocator" label="出处" min-width="180" show-overflow-tooltip />
        </el-table>
        <p v-else class="muted">包里没有已确认事实。</p>
      </div>

      <!-- 不可修改项 -->
      <div class="wp-card">
        <div class="wp-card-head"><b>不可修改项</b><span class="muted">AI 生产不得自由重绘</span></div>
        <div class="wp-tags">
          <el-tag v-for="(t, i) in content?.immutableItems || []" :key="'i' + i" size="small" type="warning" effect="plain">
            {{ t }}
          </el-tag>
        </div>
        <p v-if="!(content?.immutableItems || []).length" class="muted">包里没有声明不可修改项。</p>
      </div>

      <!-- 全文兜底：结构对不上时不静默（宁可让人看到原文） -->
      <details v-if="parseWarning" class="wp-raw">
        <summary>查看开工包原文</summary>
        <pre>{{ view?.contentJson }}</pre>
      </details>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { getProjectWorkPackage } from '@/api/creative';
import type { CreativeWorkPackageVO } from '@/api/creative/types';
// 包内容的结构定义**复用内容域那一份**，不在创作域另写一套——
// 另写一套的代价不是重复几十行，而是内容侧一改结构这边就悄悄错位。
import type { WorkPackageContent } from '@/api/content/workPackage/types';
import { parseTime } from '@/utils/ruoyi';
import { useStepHeading } from '../../composables/stepNumbering';

/** 标题编号：本页步骤号（v1 反馈；没有工作台上下文时不显示编号） */
const stepHeading = useStepHeading('ProjectWorkPackageBlock');

/**
 * 项目页区块③「开工包」（内测 C5①：开工包＝跨部门交接凭证）。
 *
 * <p><b>它解决什么</b>：开工包原先在内容侧签发、设计侧<b>零引用</b>（连字段都没有）——
 * "交接"只是内容侧的单方面动作（内测 S5）。定位改成跨部门交接凭证之后，
 * 设计侧必须能在干活的地方读到"品牌部交接了什么、缺什么、什么不能改"。</p>
 *
 * <p><b>为什么包内容原样透传而不是在创作域建结构</b>：结构属于内容域（SPEC §4.5）。
 * 创作域另建一份就是第二处定义，内容侧一改就错位——而那正是这一轮反复在修的东西。
 * 解析不了时不静默：明确说"结构对不上"并把原文放出来。</p>
 *
 * <p><b>尺寸为什么摆两个</b>：包里的 {@code spec.outputSize} 是"品牌部确认的要求"，
 * {@code renderOutputSize} 是"排版实际使用的规格"（创作域的场景配置是尺寸权威）。
 * 两者含义不同，合成一个字段就会变成"排版按 750、验收按别的"那种自相矛盾——
 * 所以并排列出，由人裁定。</p>
 *
 * @author creative
 */
const props = defineProps<{
  /** 当前项目ID（为空时不取数） */
  taskId?: string | number | null;
}>();

const view = ref<CreativeWorkPackageVO | null>(null);
const loaded = ref(false);
const loading = ref(false);
const error = ref('');
/** contentJson 解析失败时的说明（不静默：解析不了也要让人看到原文） */
const parseWarning = ref('');

const content = ref<WorkPackageContent | null>(null);

const statusType = computed(() => {
  const status = (view.value?.status || '').toUpperCase();
  if (status === 'ISSUED') {
    return 'success';
  }
  return 'warning';
});

/**
 * 取开工包。
 *
 * <p>切项目必须重新取（否则会拿上一个项目的包冒充）；失败不静默，
 * 也不把旧值留在页面上——那比报错更危险。</p>
 */
async function load(): Promise<void> {
  const id = props.taskId;
  view.value = null;
  content.value = null;
  loaded.value = false;
  error.value = '';
  parseWarning.value = '';
  if (!id) {
    return;
  }
  loading.value = true;
  try {
    const res = await getProjectWorkPackage(id);
    if (String(props.taskId) !== String(id)) {
      return;
    }
    view.value = res.data ?? null;
    parseContent();
  } catch (e) {
    if (String(props.taskId) === String(id)) {
      error.value = '开工包没取到（接口失败）——这不代表品牌部没生成，请刷新重试';
    }
  } finally {
    if (String(props.taskId) === String(id)) {
      loaded.value = true;
      loading.value = false;
    }
  }
}

/** 解析 contentJson：结构对不上就明确说出来，并保留原文入口 */
function parseContent(): void {
  const raw = view.value?.contentJson;
  if (!raw) {
    content.value = null;
    return;
  }
  try {
    content.value = JSON.parse(raw) as WorkPackageContent;
  } catch {
    content.value = null;
    parseWarning.value = '开工包内容不是合法 JSON，已按原文展示（结构可能版本不一致，请找内容侧确认）';
  }
}

watch(() => props.taskId, load, { immediate: true });
</script>

<style scoped lang="scss">
.block-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.wp-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 10px;
}

.wp-card {
  padding: 10px 12px;
  margin-top: 10px;
  background: var(--surface-2, rgb(255 255 255 / 3%));
  border: 1px solid var(--line);
  border-radius: 6px;
}
.wp-card-head {
  display: flex;
  gap: 8px;
  align-items: baseline;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.wp-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.8;
}

.wp-kv {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 4px 16px;
  margin: 0;
}
.wp-kv dt {
  font-size: 12px;
  color: var(--t2);
}
.wp-kv dd {
  margin: 2px 0 0;
  font-size: 12.5px;
}

.wp-src {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--t2);
}

.wp-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.wp-error {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: #fbbf24;
}
.wp-empty {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--t2);
}

.wp-raw pre {
  max-height: 320px;
  padding: 10px;
  overflow: auto;
  font-size: 12px;
  background: rgb(0 0 0 / 25%);
  border-radius: 6px;
}
</style>
