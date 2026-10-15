<template>
  <el-card shadow="never" class="proposal-panel">
    <template #header>
      <div class="proposal-head">
        <b>变更提案（培训助手）</b>
        <el-button size="small" plain :loading="loading" @click="load">刷 新</el-button>
      </div>
    </template>

    <el-alert type="info" :closable="false" class="mb-2">
      培训助手只能<b>提案</b>：<b>采纳</b>才会产生一条新修订（来源 COPILOT_ACCEPTED），
      创建提案本身不改草稿。提案基于旧修订、或提出后内容已被改过，都会被服务端拒绝——
      而不是"按老提案覆盖新内容"。采纳也<b>不会</b>发布、不会提交版本。
    </el-alert>

    <el-table
      :data="proposals"
      size="small"
      border
      highlight-current-row
      style="max-height: 220px; overflow: auto"
      @current-change="select"
    >
      <el-table-column label="提案" prop="proposalId" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="proposalStatusTag(row.status)" size="small">{{ proposalStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="90">
        <template #default="{ row }">{{ proposalSourceLabel(row.source) }}</template>
      </el-table-column>
      <el-table-column label="基准修订" prop="baseRevision" width="80" />
      <el-table-column label="想改什么" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.summary || proposalSummary(row) }}</template>
      </el-table-column>
      <el-table-column label="时间" prop="createTime" width="150" show-overflow-tooltip />
    </el-table>

    <template v-if="selected">
      <el-divider content-position="left">改动预览（提案 #{{ selected.proposalId }}）</el-divider>
      <p :class="previewItems(selected).length ? 'proposal-hint' : 'proposal-hint-warn'">
        {{ proposalSummary(selected) }}
        <span class="proposal-muted">
          · 基准修订 #{{ selected.baseRevision }} · 当前草稿修订 #{{ props.currentRevision }}
        </span>
      </p>
      <div v-for="(item, index) in previewItems(selected)" :key="index" class="proposal-diff">
        <div class="proposal-path">{{ item.path }}</div>
        <div class="proposal-side"><b>改前：</b>{{ formatDiffValue(item.before) }}</div>
        <div class="proposal-side"><b>改后：</b>{{ formatDiffValue(item.after) }}</div>
      </div>

      <div class="mt-2">
        <el-button
          v-hasPermi="['aig:studio:copilot:use']"
          type="primary"
          size="small"
          :disabled="!canDecide"
          :loading="deciding"
          @click="handleApply"
        >
          采 纳
        </el-button>
        <el-button
          v-hasPermi="['aig:studio:copilot:use']"
          size="small"
          :disabled="!canDecide"
          :loading="deciding"
          @click="openReject"
        >
          拒 绝
        </el-button>
        <span v-if="!isPending(selected)" class="proposal-muted">
          已处理（{{ proposalStatusLabel(selected.status) }}），不能重复处理
        </span>
        <span v-else-if="props.archived" class="proposal-muted">草稿已归档，不能再采纳</span>
        <span v-else-if="decidedReasonOf(selected)" class="proposal-muted">处理说明：{{ selected.decidedReason }}</span>
      </div>
    </template>
    <p v-else-if="proposals.length === 0" class="proposal-muted">
      这个草稿还没有变更提案。可以用下面的「与培训助手对话」让它提一份，或由外部流程通过接口提案；
      本面板负责审阅与处理。
    </p>
    <p v-else class="proposal-muted">从上面点一条提案，这里会显示它逐处的改动预览。</p>

    <!-- 对话式 Copilot：只产提案，模型输出必须先过白名单 -->
    <el-divider />
    <div class="copilot-chat">
      <div class="copilot-chat-head">
        <el-button size="small" text type="primary" @click="toggleChat">
          {{ chatOpen ? '收起对话' : '与培训助手对话（会花一次调用）' }}
        </el-button>
        <span class="proposal-muted">
          对话只产<b>提案</b>：模型输出的改动先过白名单，越权的一律被拒并记在下面。
        </span>
      </div>

      <template v-if="chatOpen">
        <el-alert type="warning" :closable="false" class="mb-2">
          发送会发起<b>一次真实计费的模型调用</b>（走后端网关的策略校验与配额）。
          后端开关 <code>aigov.studio.copilot.enabled</code> 默认为关，关着时会被明确拒绝。
        </el-alert>
        <el-form label-width="90px">
          <el-form-item label="数据等级" required>
            <el-select v-model="chatDataLevel" style="width: 100%">
              <el-option
                v-for="item in DATA_LEVEL_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <div class="text-gray-500" style="font-size: 12px; line-height: 1.5">
              必须按<b>上线时要用的等级</b>说：按 INTERNAL 聊通、按 RESTRICTED 上线 = 聊了错的那件事。
            </div>
          </el-form-item>
          <el-form-item label="要求">
            <el-input
              v-model="chatMessage"
              type="textarea"
              :rows="3"
              placeholder="例如：把主要责任改成「先把事实查清再写卖点」"
            />
          </el-form-item>
          <el-form-item label="成本上限">
            <el-input-number
              v-model="chatMaxCost"
              :min="0"
              :controls="false"
              placeholder="可空；单位=美元 USD"
            />
          </el-form-item>
        </el-form>
        <div class="mb-2">
          <el-button
            v-hasPermi="['aig:studio:copilot:chat']"
            type="primary"
            size="small"
            :disabled="!canChat(chatMessage, chatDataLevel)"
            :loading="sending"
            @click="send"
          >
            发 送
          </el-button>
          <el-button size="small" :loading="turnsLoading" @click="loadTurns">刷新会话</el-button>
        </div>

        <p v-if="!turns.length" class="proposal-muted">还没有对话记录。</p>
        <div v-for="turn in turns" :key="turn.turnId" class="copilot-turn">
          <div class="copilot-turn-head">
            <el-tag :type="copilotStatusTag(turn.status)" size="small">
              {{ copilotStatusLabel(turn.status) }}
            </el-tag>
            <span class="copilot-turn-summary">{{ copilotTurnSummary(turn) }}</span>
            <span class="proposal-muted">{{ turn.createTime || '' }}</span>
          </div>
          <div class="copilot-turn-side"><b>我：</b>{{ turn.userMessage || '（空）' }}</div>
          <div v-if="turn.failureReason" class="copilot-turn-warn">{{ turn.failureReason }}</div>
          <el-collapse v-if="turn.modelOutput">
            <el-collapse-item title="模型原文（可能被截断）">
              <pre class="copilot-output">{{ turn.modelOutput }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>
      </template>
    </div>

    <el-dialog v-model="rejectVisible" title="拒绝变更提案" width="520px" append-to-body>
      <el-alert type="warning" :closable="false" class="mb-2">
        拒绝<b>不会</b>改动草稿内容；它只是把这条提案置为终态，避免它一直挂在"待处理"里。
      </el-alert>
      <el-form label-width="90px">
        <el-form-item label="拒绝原因">
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="可空；写清楚为什么不要这次改动，便于提案方改进" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取 消</el-button>
        <el-button type="primary" :loading="deciding" @click="doReject">确 定 拒 绝</el-button>
      </template>
    </el-dialog>
    <!-- D06：本地有未保存改动时的三选一。采纳以「服务端已保存的修订」为基准并重载编辑器，
         因此绝不能静默覆盖本地改动。
         刻意放在 el-card 内部：本组件由页面用 <transition> 包裹，模板必须只有一个根节点；
         而 el-dialog 带 append-to-body，插在哪里都不影响它的渲染位置与样式。 -->
    <el-dialog v-model="applyDirtyVisible" title="有未保存的改动" width="480px" append-to-body>
      <div>{{ adoptUnsavedMessage() }}</div>
      <template #footer>
        <el-button
          v-for="choice in DIRTY_CHOICES"
          :key="choice.key"
          :type="choice.type"
          @click="chooseApplyDirty(choice.key)"
        >
          {{ choice.label }}
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { applyProposal, chatCopilot, listCopilotTurns, listProposals, rejectProposal } from '@/api/aigov/studio';
import type {
  AigStudioChangeProposalVO,
  AigStudioCopilotTurnVO
} from '@/api/aigov/studio/types';
import modal from '@/plugins/modal';
import { adoptUnsavedMessage, DIRTY_CHOICES, type DirtyChoice } from '../dirtyGuard';
import {
  canChat,
  copilotStatusLabel,
  copilotStatusTag,
  copilotTurnSummary,
  DATA_LEVEL_OPTIONS,
  formatDiffValue,
  isPending,
  previewItems,
  proposalSourceLabel,
  proposalStatusLabel,
  proposalStatusTag,
  proposalSummary
} from '../proposal';

/**
 * 训练台：变更提案审阅面板（增量 22／附件 07 增量 6）。
 *
 * <p><b>为什么它只做"审阅与处理"</b>：创建提案的入口是给培训助手（Copilot）与外部流程用的
 * 接口；本页面——由草稿责任人使用——负责看清"这条提案会改哪几处"并决定采纳/拒绝。
 * 创建提案绝不改草稿，采纳才产生一条 source=COPILOT_ACCEPTED 的新修订。</p>
 *
 * <p><b>为什么采纳必须带 expectedRevision</b>：采纳是写草稿，写草稿必须带 CAS。
 * 页面带上"我看到的当前修订号"，服务端才知道我审的是哪一版；对不上就拒绝，
 * 而不是把一份针对旧内容的提案盖到新内容上。</p>
 *
 * @author ai-gov
 */
const props = defineProps<{
  /** 草稿ID（为空时不取数） */
  draftId?: string | number | null;
  /** 草稿当前修订号（采纳时作 CAS） */
  currentRevision: number;
  /** 草稿是否已归档（归档后不能再采纳/拒绝） */
  archived?: boolean;
  /** 编辑器是否有未保存改动（D06：采纳前必须处理本地改动） */
  dirty?: boolean;
  /** 由父组件提供的保存动作（"保存并继续"用）；返回是否保存成功 */
  save?: () => Promise<boolean>;
}>();

const emit = defineEmits<{ (e: 'applied'): void }>();

const loading = ref(false);
const deciding = ref(false);
const proposals = ref<AigStudioChangeProposalVO[]>([]);
const selected = ref<AigStudioChangeProposalVO>();

const rejectVisible = ref(false);
const rejectReason = ref('');
/** D06：本地有未保存改动时，"采纳"先弹三选一 */
const applyDirtyVisible = ref(false);

/* ------------------------------------------------------------------ 对话式 Copilot */

/** 对话区默认收起：它是花钱的动作，不该在打开面板时就摆出来 */
const chatOpen = ref(false);
const sending = ref(false);
const turnsLoading = ref(false);
const turns = ref<AigStudioCopilotTurnVO[]>([]);
const chatMessage = ref('');
const chatDataLevel = ref('INTERNAL');
const chatMaxCost = ref<number>();

/** 只有"待处理 + 草稿未归档"的提案可以被处理 */
const canDecide = computed(() => isPending(selected.value) && !props.archived);

/**
 * 读取提案列表。
 *
 * 每次刷新都丢弃当前选中项：列表变了以后，"选中"很可能指向一条已经不在列表里的提案，
 * 留着它只会让人对着一份过期预览做决定。
 */
async function load(): Promise<void> {
  const id = props.draftId;
  selected.value = undefined;
  if (!id) {
    proposals.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await listProposals(id);
    // 取数期间切了草稿：丢弃这次结果，避免把上一个草稿的提案显示成本草稿的
    if (String(props.draftId) !== String(id)) return;
    proposals.value = res.data || [];
  } catch (e) {
    if (String(props.draftId) === String(id)) {
      modal.alertError('提案列表没取到：' + ((e as { message?: string })?.message || '未知原因'));
    }
  } finally {
    if (String(props.draftId) === String(id)) {
      loading.value = false;
    }
  }
}

/** 表格行选择 */
function select(row?: AigStudioChangeProposalVO): void {
  if (row) selected.value = row;
}

/** 有处理说明时才展示（避免采纳时留一个空标签） */
function decidedReasonOf(proposal: AigStudioChangeProposalVO): boolean {
  return !!proposal.decidedReason;
}

/** 采纳入口（D06）：本地有未保存改动时，先让人选择 保存并继续 / 放弃并继续 / 返回 */
async function handleApply(): Promise<void> {
  const target = selected.value;
  if (!target) return;
  if (props.archived) {
    modal.msgWarning('草稿已归档，不能再采纳提案');
    return;
  }
  if (props.dirty) {
    applyDirtyVisible.value = true;
    return;
  }
  await doApply();
}

/** 三选一的收口：CANCEL 不动；SAVE 先保存（失败则不继续）；随后照常采纳 */
async function chooseApplyDirty(choice: DirtyChoice): Promise<void> {
  applyDirtyVisible.value = false;
  if (choice === 'CANCEL') return;
  if (choice === 'SAVE') {
    const saved = props.save ? await props.save() : false;
    if (!saved) return;
  }
  await doApply();
}

/** 真正采纳：带 CAS 落一条新修订，然后让父组件重新载入内容与修订历史 */
async function doApply(): Promise<void> {
  const target = selected.value;
  if (!target) return;
  if (props.archived) {
    modal.msgWarning('草稿已归档，不能再采纳提案');
    return;
  }
  await modal.confirm(
    '采纳提案 #' +
      target.proposalId +
      '？这会立即按提案内容产生一条新修订（来源 COPILOT_ACCEPTED）。' +
      '不会发布、不会提交版本。'
  );
  deciding.value = true;
  try {
    await applyProposal(target.proposalId, { expectedRevision: props.currentRevision });
    modal.msgSuccess('已采纳，产生新修订');
    await load();
    emit('applied');
  } catch (e) {
    // 失效（基于旧修订）/ 内容漂移 / 并发被处理，服务端都有明确文案，原样展示
    modal.alertError((e as { message?: string })?.message || '采纳失败');
  } finally {
    deciding.value = false;
  }
}

/** 打开拒绝弹窗（原因可空） */
function openReject(): void {
  if (!selected.value) return;
  rejectReason.value = '';
  rejectVisible.value = true;
}

/** 拒绝：把提案置为终态，草稿内容不变 */
async function doReject(): Promise<void> {
  const target = selected.value;
  if (!target) return;
  deciding.value = true;
  try {
    await rejectProposal(target.proposalId, { reason: rejectReason.value || undefined });
    rejectVisible.value = false;
    modal.msgSuccess('已拒绝（草稿内容未改变）');
    await load();
  } catch (e) {
    modal.alertError((e as { message?: string })?.message || '拒绝失败');
  } finally {
    deciding.value = false;
  }
}

/* ------------------------------------------------------------------ 对话式 Copilot */

/** 展开/收起对话区；展开时取一次会话历史（收起时不取：只读面板不该无谓往返） */
async function toggleChat(): Promise<void> {
  chatOpen.value = !chatOpen.value;
  if (chatOpen.value) {
    await loadTurns();
  }
}

/** 读取会话历史（含被拒绝/失败的那些轮次——它们才是最有信息量的） */
async function loadTurns(): Promise<void> {
  const id = props.draftId;
  if (!id) {
    turns.value = [];
    return;
  }
  turnsLoading.value = true;
  try {
    const res = await listCopilotTurns(id);
    // 取数期间切了草稿：丢弃这次结果，避免把上一个草稿的对话显示成本草稿的
    if (String(props.draftId) !== String(id)) return;
    turns.value = res.data || [];
  } catch (e) {
    if (String(props.draftId) === String(id)) {
      modal.alertError('会话历史没取到：' + ((e as { message?: string })?.message || '未知原因'));
    }
  } finally {
    if (String(props.draftId) === String(id)) {
      turnsLoading.value = false;
    }
  }
}

/**
 * 发送一轮对话。
 *
 * 三种结论分开处理：产出了提案要刷新提案列表（否则人会以为没反应）；被拒绝/失败要把
 * 服务端原因原样展示——尤其"模型越界"这条，它是这条通道最有价值的信号。
 */
async function send(): Promise<void> {
  const id = props.draftId;
  if (!id) return;
  if (!canChat(chatMessage.value, chatDataLevel.value)) {
    modal.msgWarning('请填写要求并选择数据等级');
    return;
  }
  await modal.confirm(
    '发送会发起一次真实计费的模型调用（数据等级 ' + chatDataLevel.value + '）。继续？'
  );
  sending.value = true;
  try {
    const res = await chatCopilot(id, {
      message: chatMessage.value,
      dataLevel: chatDataLevel.value,
      maxCost: chatMaxCost.value
    });
    const turn = res.data;
    chatMessage.value = '';
    await loadTurns();
    if (turn.status === 'SUCCEEDED') {
      modal.msgSuccess('已产出提案 #' + turn.proposalId + '（待你审阅）');
      // 让新提案出现在上面的列表里——不刷新等于让人以为没反应
      await load();
    } else {
      modal.alertError(copilotStatusLabel(turn.status) + '：\n' + (turn.failureReason || '未知原因'));
    }
  } catch (e) {
    // 开关关闭（aigov.studio.copilot.enabled）、超预算、无权限等都有明确文案
    modal.alertError((e as { message?: string })?.message || '对话失败');
  } finally {
    sending.value = false;
  }
}

// 编辑器打开/切换草稿时取一次（immediate 让首次挂载就取）；切草稿时收起对话并清空输入
watch(
  () => props.draftId,
  async () => {
    chatOpen.value = false;
    turns.value = [];
    chatMessage.value = '';
    chatMaxCost.value = undefined;
    await load();
  },
  { immediate: true }
);
</script>

<style scoped>
.proposal-panel {
  margin-top: 12px;
}

.proposal-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.proposal-hint {
  font-size: 12.5px;
  color: var(--app-text-color-secondary, #606266);
  margin: 0 0 6px;
}

.proposal-hint-warn {
  font-size: 12.5px;
  color: var(--el-color-warning, #e6a23c);
  margin: 0 0 6px;
}

.proposal-muted {
  font-size: 12px;
  color: var(--app-text-color-secondary, #909399);
  margin-left: 8px;
}

.proposal-diff {
  border-bottom: 1px dashed var(--app-border-color, #e4e7ed);
  padding: 6px 0;
}

.proposal-path {
  font-size: 12.5px;
  font-weight: 600;
  word-break: break-all;
}

.proposal-side {
  font-size: 12px;
  color: var(--app-text-color-secondary, #606266);
  white-space: pre-wrap;
  word-break: break-word;
  margin-top: 2px;
}

.copilot-chat-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.copilot-turn {
  border-bottom: 1px dashed var(--app-border-color, #e4e7ed);
  padding: 6px 0;
}

.copilot-turn-head {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.copilot-turn-summary {
  font-size: 12.5px;
  font-weight: 600;
}

.copilot-turn-side {
  margin-top: 2px;
  font-size: 12px;
  color: var(--app-text-color-secondary, #606266);
  white-space: pre-wrap;
  word-break: break-word;
}

.copilot-turn-warn {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-color-warning, #e6a23c);
  white-space: pre-wrap;
  word-break: break-word;
}

.copilot-output {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 12px;
}
</style>
