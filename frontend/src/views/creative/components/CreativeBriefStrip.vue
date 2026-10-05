<template>
  <section v-if="taskId" class="brief-strip" :class="{ 'is-empty': loaded && !hasContent }">
    <div class="bs-head">
      <span class="bs-title">品牌要求</span>
      <el-tag size="small" :type="statusType" effect="dark">{{ statusText }}</el-tag>
      <span v-if="hasContent" class="muted bs-note">
        出图会自动追加：<b>必显信息</b>与<b>主推卖点</b>进正向提示词、<b>禁用词</b>进负向提示词
      </span>
      <span class="bs-spacer" />
      <span class="muted bs-note">本页只读 · 要改请让品牌部在「内容生产协同 → 内容任务」改</span>
    </div>
    <p v-if="error" class="bs-error">{{ error }}</p>
    <p v-else-if="!loaded" class="muted bs-note">读取中…</p>
    <p v-else-if="!hasContent" class="bs-error">
      品牌部还没有填品牌要求——本次出图不会带上必显信息 / 禁用词 / 主推卖点。
      这不影响你继续做，但画面与文案是否满足品牌要求需要人工把关。
    </p>
    <dl v-else class="bs-grid">
      <div>
        <dt>必显信息</dt>
        <dd :title="valueOf('mustShow')">{{ inline('mustShow') }}</dd>
      </div>
      <div>
        <dt>禁用词</dt>
        <dd :title="valueOf('forbiddenWords')">{{ inline('forbiddenWords') }}</dd>
      </div>
      <div>
        <dt>主推卖点</dt>
        <dd :title="valueOf('mainPush')">{{ inline('mainPush') }}</dd>
      </div>
    </dl>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { getBrandBrief } from '@/api/content/brief';
import type { BrandBriefVO } from '@/api/content/brief/types';
import { briefStatusText } from '@/api/content/brief/types';
import { parseTime } from '@/utils/ruoyi';

/**
 * 「品牌要求」摘要条（内测 S12）。
 *
 * <p><b>为什么需要它</b>：品牌要求只在「视觉项目」页可见，而设计师真正干活的地方是
 * 分镜页与生产中心——于是"品牌部提的要求"和"我这一屏怎么出图"在空间上分开了：
 * 人不会为了看一眼必显信息来回切页面，切过去也容易漏掉。内测实测到的现象就是
 * "必显信息填了，出图里没体现，而且没人发现"。</p>
 *
 * <p><b>只读，且不重复渲染全部字段</b>：品牌要求的编辑入口在内容侧（品牌部的事），
 * 这里只展示**真正会影响出图的三项**（必显信息 / 禁用词 / 主推卖点）。
 * 目标人群、尺寸规范、参考风格当前不参与出图，把它们也摆上来会造成
 * "填了就会生效"的错觉——那是内测 S2/C6 的问题，不该在这个组件里复发。</p>
 *
 * <p><b>读不到就说读不到</b>：接口失败时不显示空白（空白会被读成"品牌部没提要求"），
 * 而是明确写"没取到"。同理，没填/没确认时也把后果说清楚（"本次出图不会带上…"），
 * 因为沉默在这里等于默许。</p>
 *
 * @author creative
 */
const props = defineProps<{
  /** 项目ID；为空时不渲染（这些页面都允许"未选项目"） */
  taskId?: string | number | null;
}>();

const brief = ref<BrandBriefVO | null>(null);
const loaded = ref(false);
const error = ref('');

/** 只有这三项会进提示词——组件只展示它们（见组件说明） */
const BRIEF_KEYS = ['mustShow', 'forbiddenWords', 'mainPush'] as const;

const valueOf = (key: string): string => String((brief.value as Record<string, unknown>)?.[key] ?? '').trim();

/** 多行字段在条里压成一行；完整内容在 title 与视觉项目页 */
const inline = (key: string): string => {
  const value = valueOf(key);
  if (!value) {
    return '—';
  }
  return value.split(/\r?\n/).filter((line) => line.trim()).join('、');
};

const hasContent = computed(() => BRIEF_KEYS.some((key) => valueOf(key)));

const statusText = computed(() => briefStatusText(brief.value, loaded.value, parseTime));

const statusType = computed(() => {
  if (!loaded.value) {
    return 'info';
  }
  if (!brief.value?.configured) {
    return 'info';
  }
  return brief.value.status === 'CONFIRMED' ? 'success' : 'warning';
});

/**
 * 按项目取品牌要求。
 *
 * <p>切项目必须重新取（否则会拿上一个项目的品牌要求冒充，是竞态）；失败不静默，
 * 也不把 brief 留在上一个项目的值上——那比报错更危险。</p>
 */
async function load(): Promise<void> {
  const id = props.taskId;
  brief.value = null;
  loaded.value = false;
  error.value = '';
  if (!id) {
    return;
  }
  try {
    const res = await getBrandBrief(id);
    // 期间又切了项目：这次结果作废（否则慢请求会覆盖新项目的展示）
    if (String(props.taskId) !== String(id)) {
      return;
    }
    brief.value = res.data ?? null;
  } catch (e) {
    if (String(props.taskId) !== String(id)) {
      return;
    }
    error.value = '品牌要求没取到（接口失败）——这不代表品牌部没提要求，请刷新重试或去「视觉项目」页确认';
  } finally {
    if (String(props.taskId) === String(id)) {
      loaded.value = true;
    }
  }
}

watch(() => props.taskId, load, { immediate: true });
</script>

<style scoped lang="scss">
.brief-strip {
  padding: 10px 12px;
  margin-bottom: 12px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-left: 3px solid var(--brand, #6ea8fe);
  border-radius: 6px;
}
.brief-strip.is-empty {
  border-left-color: #fbbf24;
}

.bs-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.bs-title {
  font-size: 13px;
  font-weight: 600;
}
.bs-spacer {
  flex: 1 1 auto;
}
.bs-note {
  font-size: 12px;
}

.bs-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px 16px;
  margin: 8px 0 0;
}
.bs-grid dt {
  font-size: 12px;
  color: var(--t2);
}
.bs-grid dd {
  margin: 2px 0 0;
  overflow: hidden;
  font-size: 12.5px;
  line-height: 1.6;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bs-error {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: #fbbf24;
}
</style>
