<template>
  <div class="studio">
    <header class="page-head">
      <div>
        <h2>视觉模板库</h2>
        <p class="muted">
          模板本体随渲染服务镜像分发，这里只登记「哪个编码版本可用、校验和是多少、是否已发布」。
          排版只会选用<b>已发布且校验和与渲染服务一致</b>的模板——模板被换过就必须重新对账发布，
          否则「同一版本渲染出不同结果」会变成一笔糊涂账。
        </p>
      </div>
      <div class="head-actions">
        <el-tag :type="rendererAvailable ? 'success' : 'danger'" effect="dark">
          {{ rendererAvailable ? '渲染服务可达' : '渲染服务不可达' }}
        </el-tag>
        <el-button plain :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :loading="syncing" @click="doSync">与渲染服务对账</el-button>
      </div>
    </header>

    <el-alert
      v-if="!rendererAvailable"
      type="error"
      show-icon
      :closable="false"
      class="notice"
      title="渲染服务不可达，无法对账或发布模板"
    >
      <span class="muted">
        请确认容器 creative-renderer 已启动且后端与它同处内网。此时不会显示任何「实时校验和」，
        也不会允许发布——宁可拦住，也不要拿一份不知道是否一致的模板去渲染。
      </span>
    </el-alert>

    <section class="panel">
      <div class="block-head">
        <h3>模板清单</h3>
        <span class="muted">{{ templates.length }} 个</span>
      </div>
      <el-table v-loading="loading" :data="templates" size="small" empty-text="还没有登记任何模板，先点「与渲染服务对账」">
        <el-table-column label="模板" min-width="200">
          <template #default="{ row }">
            <div class="tpl-code">{{ asTpl(row).templateCode }}@{{ asTpl(row).version }}</div>
            <div class="muted small">{{ asTpl(row).templateName }} · {{ asTpl(row).htmlTemplateKey }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ asTpl(row).templateType }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(asTpl(row).status)">
              {{ TEMPLATE_STATUS_LABELS[asTpl(row).status || ''] || asTpl(row).status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="库内校验和" width="130">
          <template #default="{ row }">
            <span class="mono">{{ short(asTpl(row).registeredChecksum) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="渲染服务校验和" width="150">
          <template #default="{ row }">
            <span v-if="asTpl(row).rendererChecksum" class="mono">{{ short(asTpl(row).rendererChecksum) }}</span>
            <span v-else class="muted">（不可达）</span>
          </template>
        </el-table-column>
        <el-table-column label="一致性" width="110">
          <template #default="{ row }">
            <span :class="asTpl(row).checksumMatches ? 'good' : 'bad'">
              {{ asTpl(row).checksumMatches === true ? '一致' : (asTpl(row).checksumMatches === false ? '不一致' : '未知') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="文件" width="100">
          <template #default="{ row }">
            {{ asTpl(row).templateBytes ? `${asTpl(row).templateBytes} B` : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              text
              type="primary"
              :disabled="asTpl(row).status === 'PUBLISHED' || !asTpl(row).checksumMatches"
              :loading="busyId === String(asTpl(row).id)"
              @click="doPublish(asTpl(row))"
            >
              发布
            </el-button>
            <el-button
              size="small"
              text
              type="danger"
              :disabled="asTpl(row).status === 'RETIRED'"
              :loading="busyId === String(asTpl(row).id)"
              @click="doRetire(asTpl(row))"
            >
              退役
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="block-head"><h3>说明（为什么要有校验和）</h3></div>
      <ul class="note-list">
        <li>模板文件不在业务库里，而在渲染服务镜像里；库里只记它的 sha256。</li>
        <li>对账时若发现库内与渲染服务的校验和不一致，该模板会被<b>自动退回草稿</b>并提示重新发布。</li>
        <li>每次排版前还会再对账一次；发布后被换过的模板会被<b>拒绝使用</b>。</li>
        <li>当前 POC 阶段所有屏都用同一个长图模板；铺开更多模板后再按屏类型选择。</li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listLayoutTemplates, publishLayoutTemplate, retireLayoutTemplate, syncLayoutTemplates } from '@/api/creative';
import type { DpLayoutTemplateVO, TagType } from '@/api/creative/types';
import { TEMPLATE_STATUS_LABELS } from '@/api/creative/types';

const templates = ref<DpLayoutTemplateVO[]>([]);
const loading = ref(false);
const syncing = ref(false);
const busyId = ref('');

const rendererAvailable = computed(() =>
  templates.value.length === 0 ? true : templates.value.every((t) => t.rendererAvailable !== false)
);

function asTpl(row: unknown): DpLayoutTemplateVO {
  return row as DpLayoutTemplateVO;
}

function short(checksum?: string): string {
  return checksum ? checksum.slice(0, 12) : '—';
}

function statusType(status?: string): TagType {
  if (status === 'PUBLISHED') return 'success';
  if (status === 'RETIRED') return 'info';
  return 'warning';
}

async function load() {
  loading.value = true;
  try {
    const res = await listLayoutTemplates();
    templates.value = res.data || [];
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载模板失败');
  } finally {
    loading.value = false;
  }
}

async function doSync() {
  syncing.value = true;
  try {
    const res = await syncLayoutTemplates();
    templates.value = res.data || [];
    ElMessage.success('已与渲染服务对账');
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '对账失败');
  } finally {
    syncing.value = false;
  }
}

async function doPublish(row: DpLayoutTemplateVO) {
  busyId.value = String(row.id);
  try {
    await publishLayoutTemplate(row.id);
    ElMessage.success(`${row.templateCode}@${row.version} 已发布`);
    await load();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '发布失败');
  } finally {
    busyId.value = '';
  }
}

async function doRetire(row: DpLayoutTemplateVO) {
  try {
    await ElMessageBox.confirm(
      `退役后 ${row.templateCode}@${row.version} 不能再被排版选用（已渲染的版本不受影响）。确认退役？`,
      '退役模板',
      { type: 'warning' }
    );
  } catch {
    return;
  }
  busyId.value = String(row.id);
  try {
    await retireLayoutTemplate(row.id);
    ElMessage.success('已退役');
    await load();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '退役失败');
  } finally {
    busyId.value = '';
  }
}

async function extractErrorMessage(error: unknown): Promise<string | undefined> {
  const anyError = error as { message?: string; response?: { data?: unknown } };
  const data = anyError?.response?.data;
  if (data instanceof Blob) {
    try {
      const text = await data.text();
      const parsed = JSON.parse(text) as { msg?: string; message?: string };
      return parsed.msg || parsed.message || text;
    } catch {
      return anyError?.message;
    }
  }
  if (data && typeof data === 'object') {
    const parsed = data as { msg?: string; message?: string };
    return parsed.msg || parsed.message || anyError?.message;
  }
  return anyError?.message;
}

onMounted(load);
</script>

<style scoped lang="scss">
@use '@/assets/styles/tokens-studio.scss';

.studio {
  min-height: calc(100vh - 135px);
  padding: 24px;
  color: var(--t1);
  background: var(--bg);
  background-image: radial-gradient(900px 460px at 84% -10%, rgba(148, 163, 184, 0.16), transparent 68%);
  border: 1px solid var(--line);
  border-radius: 8px;
}

.page-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--line);
}
.page-head h2 {
  margin: 0 0 6px;
  font-size: 18px;
}
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.notice {
  margin-bottom: 14px;
}

.panel {
  padding: 16px;
  margin-bottom: 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.panel h3 {
  margin: 0;
  font-size: 15px;
}
.block-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.tpl-code {
  font-size: 13px;
  font-weight: 600;
}
.small {
  font-size: 12px;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.good {
  color: #a7f3d0;
}
.bad {
  color: #fca5a5;
}
.note-list {
  padding-left: 18px;
  margin: 0;
  font-size: 13px;
  line-height: 2;
  color: var(--t2);
}

.muted {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--t2);
}

.studio :deep(th.el-table__cell) {
  color: var(--t2);
  background: var(--elevated);
  border-bottom-color: var(--line);
}
.studio :deep(td.el-table__cell) {
  color: var(--t1);
  background: transparent;
  border-bottom-color: var(--line);
}
.studio :deep(.el-table),
.studio :deep(.el-table tr) {
  background: transparent;
}
</style>
