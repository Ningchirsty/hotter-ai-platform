<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    :size="drawerSize"
    :with-header="false"
    append-to-body
    class="studio-drawer"
    @open="load"
  >
    <div class="drawer" data-panel="QaPanel">
      <div class="drawer-head">
        <span class="drawer-title">质检与交付</span>
        <span class="drawer-sub">{{ projectName || '当前项目' }}</span>
        <span class="spacer" />
        <el-button size="small" text :loading="loading" @click="load">刷新</el-button>
        <el-button size="small" text @click="visible = false">关闭</el-button>
      </div>

      <!-- 口径：写在最上面，避免"同一个 null 每页一个叫法" -->
      <p v-for="(line, i) in QA_SEMANTICS" :key="i" class="drawer-note">{{ line }}</p>

      <el-alert
        v-if="loadError"
        type="error"
        show-icon
        :closable="false"
        class="gate-alert"
        :title="loadError"
      />

      <!-- 交付产物（项目级：一条证据线，不按屏重复） -->
      <div class="section">
        <div class="sec-head">交付产物</div>
        <ul class="kv">
          <li>
            <span class="k">{{ deliveryLine.label }}</span>
            <span class="v">
              <el-tag size="small" :type="qaStatusType(deliveryLine.status)">{{ deliveryLine.statusLabel }}</el-tag>
              {{ deliveryLine.detail }}
            </span>
          </li>
          <li>
            <span class="k">处置</span>
            <span class="v">{{ deliveryLine.effect }}</span>
          </li>
        </ul>
      </div>

      <!-- 逐屏四条证据线（规则体检按屏；参考图/产品基准按候选） -->
      <div class="section">
        <div class="sec-head">
          逐屏结论
          <span class="muted small">（{{ rows.length }} 行）</span>
        </div>
        <table v-if="rows.length" class="qa-table">
          <thead>
            <tr>
              <th>屏</th>
              <th>候选</th>
              <th>参考图基准</th>
              <th>产品基准</th>
              <th>规则体检</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in rows" :key="i">
              <td>
                <div class="cell-main">{{ row.screenNo }}</div>
                <div class="muted small">{{ row.screenTypeDesc }}</div>
              </td>
              <td class="muted small">#{{ row.candidateNo ?? '—' }}<br />{{ row.status }}</td>
              <td v-for="item in row.evidence" :key="item.key">
                <el-tag size="small" :type="qaStatusType(item.status)">{{ item.statusLabel }}</el-tag>
                <div class="muted small">{{ item.detail }}</div>
                <div class="muted small effect">{{ item.effect }}</div>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted small">这个项目还没有出图候选：先在项目页或 AI 生产中心逐屏出图并选定。</p>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { listGenerations, getDelivery, getStoryboard } from '@/api/creative';
import type { CreativeProjectVO, DeliveryVO, DpGenerationVO, DpStoryboardVO } from '@/api/creative/types';
import {
  QA_SEMANTICS,
  buildQaRows,
  deliveryEvidence,
  qaStatusType,
  type QaScreenRow
} from '../composables/qaVerdicts';

/**
 * 质检与交付面板（V0.2 R31，文档 §23 的 QaPanel + §30 双基准 + R29/R30 新结论）。
 *
 * <p><b>它为什么值得单独做一个组件</b>：到 R30 为止，"这张图行不行"的结论散在三个页面：
 * 参考图/产品基准在出图页、规则体检在生产页、交付产物在终审页。做交付决策的人得来回切换，
 * 而且三个页面各写各的措辞。这里把四条证据线并排放在一起，口径来自
 * {@code composables/qaVerdicts}（纯函数、有单测）。</p>
 *
 * <p><b>只读</b>：不改候选状态、不发起质检、不生成交付产物——那是各页面的动作，
 * 这里只回答"现在的结论是什么"。要动作请去对应页面（或由工作台的按钮触发）。</p>
 *
 * @author creative
 */
const props = defineProps<{
  /** 当前项目ID */
  taskId?: string | number;
  /** 项目名（标题副行） */
  projectName?: string;
  /** 项目（用于取交付类型；可为空） */
  project?: CreativeProjectVO | null;
}>();

/** 抽屉宽度：结论要并排看，窄了就成了"又要来回滚" */
const drawerSize = '58%';

const visible = defineModel<boolean>('visible', { default: false });

const loading = ref(false);
const loadError = ref('');
const rows = ref<QaScreenRow[]>([]);
const delivery = ref<DeliveryVO | null>(null);
/** 是否已经加载过（打开时加载一次，之后靠「刷新」；避免每次开关都打三个接口） */
const loaded = ref(false);

/** 交付产物证据线（项目级） */
const deliveryLine = computed(() => deliveryEvidence(delivery.value));

/**
 * 加载：候选 + 分镜 + 交付视图。
 *
 * <p>三个接口里**任何一个失败都不吞**：整块显示错误原因（页面不会用空表糊过去，
 * 那正是"没数据"与"没读到"最容易被混为一谈的地方）。</p>
 */
async function load() {
  if (!props.taskId) {
    return;
  }
  if (loaded.value && !loadError.value) {
    return;
  }
  loading.value = true;
  loadError.value = '';
  try {
    const [genRes, sbRes, deliveryRes] = await Promise.all([
      listGenerations(props.taskId),
      getStoryboard(props.taskId),
      getDelivery(props.taskId)
    ]);
    const generations = (genRes.data as DpGenerationVO[]) || [];
    const storyboard = (sbRes.data as DpStoryboardVO | null) || null;
    delivery.value = (deliveryRes.data as DeliveryVO | null) || null;
    rows.value = buildQaRows(generations, storyboard?.screens || []);
    loaded.value = true;
  } catch (error) {
    loadError.value = `质检数据没读全：${(error as Error)?.message || '未知原因'}（点右上「刷新」重试；不显示空表糊过去）`;
    rows.value = [];
    delivery.value = null;
  } finally {
    loading.value = false;
  }
}

// 项目切换：重新加载（同一个面板会被复用到不同项目上）
watch(
  () => props.taskId,
  () => {
    loaded.value = false;
    if (visible.value) {
      void load();
    }
  }
);
</script>

<style scoped lang="scss">
.drawer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 2px 20px;
}

.drawer-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.drawer-title {
  font-size: 15px;
  font-weight: 600;
}

.drawer-sub {
  color: var(--muted, #9aa0a6);
  font-size: 12px;
}

.spacer {
  flex: 1;
}

.drawer-note {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--muted, #9aa0a6);
}

.section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sec-head {
  font-size: 13px;
  font-weight: 600;
}

.kv {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kv li {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 8px;
  font-size: 13px;
}

.kv .k {
  color: var(--muted, #9aa0a6);
}

.qa-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.qa-table th,
.qa-table td {
  border-bottom: 1px solid var(--line);
  padding: 8px 6px;
  vertical-align: top;
  text-align: left;
}

.qa-table th {
  font-weight: 600;
  color: var(--muted, #9aa0a6);
}

.cell-main {
  font-size: 13px;
}

.small {
  font-size: 11px;
}

.effect {
  opacity: 0.75;
}
</style>
