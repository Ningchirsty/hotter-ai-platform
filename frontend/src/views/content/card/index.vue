<template>
  <div class="p-2 app-container content-card-page">
    <PageHeading
      title="内容生产协同"
      subtitle="互动确认卡：把发现的问题交到对的人手上——证据摆全，由人裁定"
      module="content"
    />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>卡片检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="任务号" prop="taskNo">
            <el-input
              v-model="queryParams.taskNo"
              placeholder="请输入任务号"
              clearable
              style="width: 180px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="卡片类型" prop="cardType">
            <el-select v-model="queryParams.cardType" placeholder="请选择卡片类型" clearable style="width: 150px">
              <el-option v-for="dict in cp_card_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 150px">
              <el-option v-for="dict in cp_card_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="闸门等级" prop="gateLevel">
            <el-select v-model="queryParams.gateLevel" placeholder="请选择闸门等级" clearable style="width: 150px">
              <el-option v-for="dict in cp_gate_level" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="范围">
            <el-checkbox v-model="queryParams.mineOnly">待我确认</el-checkbox>
            <el-checkbox v-model="queryParams.blockingOnly">仅看阻断项</el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Interaction Cards</span>
            <h3>互动确认卡</h3>
            <p>
              共 {{ total }} 条记录；系统只呈现证据、不判断谁对谁错。展开卡片可看到全部来源与处理选项。
            </p>
          </div>
          <div class="toolbar-actions">
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table
        ref="tableRef"
        v-loading="loading"
        border
        row-key="cardId"
        class="data-table"
        :data="cardList"
      >
        <el-table-column type="expand">
          <template #default="scope">
            <div class="card-detail">
              <div class="detail-block">
                <div class="block-title">问题描述</div>
                <p class="detail-text">{{ scope.row.question || scope.row.title || '-' }}</p>
              </div>

              <div class="detail-block">
                <div class="block-title">来源证据</div>
                <template v-if="parseEvidence(scope.row.evidenceJson).length">
                  <div
                    v-for="(ev, idx) in parseEvidence(scope.row.evidenceJson)"
                    :key="idx"
                    class="evidence-item"
                  >
                    <div class="evidence-head">
                      <el-tag size="small" type="info">{{ ev.sourceFileName || '未知文件' }}</el-tag>
                      <span class="evidence-locator">{{ ev.locator || '未标注定位' }}</span>
                      <span v-if="ev.value" class="evidence-value">取值：{{ ev.value }}</span>
                    </div>
                    <div v-if="ev.excerpt" class="evidence-excerpt">摘录：{{ ev.excerpt }}</div>
                  </div>
                </template>
                <p v-else class="detail-text muted">
                  该卡暂无来源摘录（缺料类卡片由资料缺失产生）。
                </p>
              </div>

              <div class="detail-block">
                <div class="block-title">影响对象</div>
                <template v-if="parseImpact(scope.row.impactJson).length">
                  <div v-for="(im, idx) in parseImpact(scope.row.impactJson)" :key="idx" class="impact-item">
                    <el-tag size="small" type="warning">{{ im.deliverableTypeName || im.deliverableType }}</el-tag>
                    <span class="impact-field">{{ im.fieldName }}</span>
                    <span class="impact-note">{{ im.note }}</span>
                  </div>
                </template>
                <p v-else class="detail-text muted">未标注影响对象。</p>
              </div>

              <div class="detail-block">
                <div class="block-title">责任与时限</div>
                <div class="meta-row">
                  <span>责任人：{{ scope.row.assigneeName || '-' }}</span>
                  <span>截止：{{ parseTime(scope.row.dueAt) || '-' }}</span>
                  <span>闸门等级：{{ gateLevelLabel(scope.row.gateLevel) }}</span>
                  <span>是否阻断：{{ scope.row.blocking === 'Y' ? '是' : '否' }}</span>
                </div>
              </div>

              <div v-if="scope.row.status === 'PENDING'" class="detail-block">
                <div class="block-title">处理</div>
                <div class="option-bar">
                  <el-button
                    v-for="(opt, idx) in parseOptions(scope.row.optionsJson)"
                    :key="idx"
                    v-hasPermi="['content:card:handle']"
                    :type="optionButtonType(opt.option)"
                    :disabled="resolving"
                    @click="handleOption(scope.row, opt)"
                  >
                    {{ opt.label || opt.option }}
                  </el-button>
                </div>
                <div v-if="otherCardId === String(scope.row.cardId)" class="other-input">
                  <el-input
                    v-model="otherValue"
                    placeholder="请填写确认值（该值将作为已确认事实录入）"
                    style="max-width: 420px"
                  />
                  <el-button
                    v-hasPermi="['content:card:handle']"
                    type="primary"
                    :disabled="resolving"
                    @click="submitOther(scope.row)"
                  >
                    提交其他值
                  </el-button>
                  <el-button @click="cancelOther">取消</el-button>
                </div>
                <div class="form-tip">系统不会替你选值；「暂不确认并阻断」会让任务停在待确认状态。</div>
              </div>
              <div v-else class="detail-block">
                <div class="block-title">处理结果</div>
                <div class="meta-row">
                  <span>状态：{{ cardStatusLabel(scope.row.status) }}</span>
                  <span>所选选项：{{ scope.row.resolvedOption || '-' }}</span>
                  <span>确认值：{{ scope.row.resolvedValue || '-' }}</span>
                  <span>处理时间：{{ parseTime(scope.row.resolvedAt) || '-' }}</span>
                </div>
                <p v-if="scope.row.remark" class="detail-text">处理说明：{{ scope.row.remark }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="任务号" align="center" prop="taskNo" width="160" show-overflow-tooltip />
        <el-table-column label="任务名称" align="center" prop="taskName" min-width="160" show-overflow-tooltip />
        <el-table-column label="卡片类型" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="cp_card_type" :value="scope.row.cardType" />
          </template>
        </el-table-column>
        <el-table-column label="问题" align="center" prop="title" min-width="220" show-overflow-tooltip />
        <el-table-column label="闸门等级" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="cp_gate_level" :value="scope.row.gateLevel" />
          </template>
        </el-table-column>
        <el-table-column label="是否阻断" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.blocking === 'Y' ? 'danger' : 'info'" size="small">
              {{ scope.row.blocking === 'Y' ? '阻断' : '非阻断' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="责任人" align="center" prop="assigneeName" width="110" show-overflow-tooltip />
        <el-table-column label="截止时间" align="center" prop="dueAt" width="170">
          <template #default="scope">
            <span>{{ parseTime(scope.row.dueAt) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="cp_card_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="展开处理" placement="top">
              <el-button
                v-hasPermi="['content:card:handle']"
                link
                type="primary"
                icon="Edit"
                :disabled="scope.row.status !== 'PENDING'"
                @click="toggleRow(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import type { CardEvidenceItem, CardImpactItem, CardOptionItem, CpCardQuery, CpInteractionCardVO } from '@/api/content/card/types';
import { listCard, resolveCard } from '@/api/content/card';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'ContentCard' });

const { cp_card_type, cp_card_status, cp_gate_level } = toRefs<any>(
  useDict('cp_card_type', 'cp_card_status', 'cp_gate_level')
);

const cardList = ref<CpInteractionCardVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const tableRef = ref<ElTableInstance>();
const queryFormRef = ref<ElFormInstance>();

/** 处理中标记：避免同一次处理被重复提交 */
const resolving = ref(false);
/** 当前展开「填写其他值」输入框的卡片ID */
const otherCardId = ref<string>('');
const otherValue = ref('');

const data = reactive<PageData<Record<string, never>, CpCardQuery>>({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    taskId: undefined,
    taskNo: '',
    cardType: undefined,
    status: undefined,
    gateLevel: undefined,
    assigneeId: undefined,
    mineOnly: false,
    blockingOnly: false,
    params: {}
  },
  rules: {}
});

const { queryParams } = toRefs<PageData<Record<string, never>, CpCardQuery>>(data);
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** JSON 文本安全解析为数组（后端以文本落库，解析失败按空数组处理） */
function parseJsonList<T>(text?: string): T[] {
  if (!text) return [];
  try {
    const parsed = JSON.parse(text);
    return Array.isArray(parsed) ? (parsed as T[]) : [];
  } catch {
    return [];
  }
}

const parseEvidence = (text?: string) => parseJsonList<CardEvidenceItem>(text);
const parseImpact = (text?: string) => parseJsonList<CardImpactItem>(text);
const parseOptions = (text?: string) => parseJsonList<CardOptionItem>(text);

/** 闸门等级中文（用于展开区文案） */
const gateLevelLabel = (level?: string) => {
  if (level === 'BLOCK') return '强制阻断';
  if (level === 'CONDITION') return '条件流转';
  if (level === 'NOTICE') return '非阻断提醒';
  return '-';
};

/** 卡片状态中文 */
const cardStatusLabel = (status?: string) => {
  if (status === 'PENDING') return '待处理';
  if (status === 'RESOLVED') return '已处理';
  if (status === 'BLOCKED') return '暂不确认并阻断';
  if (status === 'CLOSED') return '已关闭';
  return status || '-';
};

/** 选项按钮样式：阻断类给出危险色 */
const optionButtonType = (option?: string) => {
  if (option === 'BLOCK') return 'danger';
  if (option === 'CONFIRM') return 'primary';
  return '';
};

const cancelOther = () => {
  otherCardId.value = '';
  otherValue.value = '';
};

/** 展开指定行，露出证据与处理区 */
const toggleRow = (row: CpInteractionCardVO) => {
  tableRef.value?.toggleRowExpansion(row, true);
};

/** 提交处理请求，成功后刷新列表（后端会同步重算闸门） */
const doResolve = async (
  row: CpInteractionCardVO,
  payload: { option: string; value?: string; snapshotId?: string | number; comment?: string }
) => {
  if (resolving.value) return;
  resolving.value = true;
  try {
    await resolveCard({ cardId: row.cardId!, ...payload });
    modal.msgSuccess('处理成功');
    cancelOther();
    await getList();
  } finally {
    resolving.value = false;
  }
};

/** 按 optionsJson 渲染出的选项按钮的处理入口 */
const handleOption = async (row: CpInteractionCardVO, opt: CardOptionItem) => {
  const option = opt.option || '';
  if (option === 'CONFIRM') {
    await doResolve(row, { option: 'CONFIRM', value: opt.value ?? undefined, snapshotId: opt.snapshotId });
    return;
  }
  if (option === 'OTHER') {
    otherCardId.value = String(row.cardId);
    otherValue.value = '';
    return;
  }
  // SUPPLEMENT / BLOCK：要求给出可读说明，作为人的决策记录
  const tip =
    option === 'BLOCK'
      ? '选择「暂不确认并阻断」后任务将停在待确认状态，请填写阻断原因（可选）'
      : '请说明将补充哪些资料（可选），补料后重新解析会重新生成卡片';
  let comment = '';
  try {
    const res: any = await modal.prompt(tip);
    comment = res?.value || '';
  } catch {
    return;
  }
  await doResolve(row, { option, comment });
};

/** 提交「填写其他值」 */
const submitOther = async (row: CpInteractionCardVO) => {
  const value = otherValue.value.trim();
  if (!value) {
    modal.msgError('请填写确认值');
    return;
  }
  await doResolve(row, { option: 'OTHER', value });
};

/** 查询卡片列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listCard(queryParams.value);
    cardList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.card-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 16px 12px;
}

.detail-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.block-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-title);
}

.detail-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-text-body);
  white-space: pre-wrap;
}

.detail-text.muted {
  color: var(--app-text-muted);
}

.evidence-item {
  padding: 6px 10px;
  border-left: 3px solid #d9e2ea;
  background: #f7fafc;
  border-radius: 4px;
}

.evidence-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--app-text-body);
}

.evidence-locator {
  color: var(--app-text-muted);
}

.evidence-value {
  font-weight: 600;
}

.evidence-excerpt {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-muted);
}

.impact-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--app-text-body);
}

.impact-note {
  color: var(--app-text-muted);
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: var(--app-text-body);
}

.option-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.other-input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-tip {
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}
</style>
