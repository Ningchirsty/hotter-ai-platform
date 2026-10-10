<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Agent Studio</span>
            <h3>岗位 Agent 训练台</h3>
            <p>
              草稿是<b>可编辑工作区</b>，独立于已发布的 Agent 版本：改草稿不会动到线上正在跑的能力。
              「提交」只把当前内容固化成一条 <b>DRAFT</b> 版本——之后的沙箱、黄金用例、人工批准与灰度，
              仍走既有发布门槛，这里<b>没有</b>一步到 STABLE 的按钮。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['aig:studio:draft:create']" type="primary" plain icon="Plus" @click="openCreate">
              新建草稿
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-form v-show="showSearch" :inline="true" @submit.prevent>
        <el-form-item label="训练对象编码">
          <el-input v-model="queryParams.agentCode" clearable placeholder="如 DETAIL_COPYWRITER" style="width: 220px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 160px">
            <el-option label="编辑中" value="EDITING" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" border class="data-table" :data="draftList">
        <el-table-column label="草稿ID" align="center" prop="draftId" width="180" show-overflow-tooltip />
        <el-table-column label="训练对象编码" align="center" prop="agentCode" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ARCHIVED' ? 'info' : 'success'">
              {{ scope.row.statusLabel || scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="修订号" align="center" prop="latestRevision" width="90" />
        <el-table-column label="未发布改动" align="center" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.unpublishedChanges ? 'warning' : 'info'">
              {{ scope.row.unpublishedChanges ? '有改动' : '与已提交一致' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已提交版本" align="center" width="180" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.agentVersionId || '-' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" align="center" prop="updateTime" width="170" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="260" fixed="right">
          <template #default="scope">
            <el-button v-hasPermi="['aig:studio:draft:query']" link type="primary" icon="Edit" @click="openEditor(scope.row)">
              打开
            </el-button>
            <el-button
              v-hasPermi="['aig:studio:draft:validate']"
              link
              type="primary"
              icon="CircleCheck"
              @click="handleValidateRow(scope.row)"
            >
              预检
            </el-button>
            <el-button
              v-hasPermi="['aig:studio:draft:submit']"
              link
              type="primary"
              icon="Upload"
              :disabled="scope.row.status === 'ARCHIVED'"
              @click="openSubmitRow(scope.row)"
            >
              提交
            </el-button>
            <el-button
              v-hasPermi="['aig:studio:draft:edit']"
              link
              type="danger"
              icon="FolderDelete"
              :disabled="scope.row.status === 'ARCHIVED'"
              @click="handleArchive(scope.row)"
            >
              归档
            </el-button>
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

    <!-- 新建草稿 -->
    <el-dialog v-model="createVisible" title="新建训练草稿" width="560px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        服务端会填一份含八个标准分节的骨架，而不是让你从一个空对象开始。
        「训练对象编码」跨版本稳定，建成后不再变。
      </el-alert>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="120px">
        <el-form-item label="训练对象编码" prop="agentCode">
          <el-input v-model="createForm.agentCode" placeholder="如 DETAIL_COPYWRITER（新建 Agent 时它就是 Agent 编码）" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="为什么建这份草稿（便于事后复核）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取 消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑器（左配置 / 右修订与差异） -->
    <el-dialog
      v-model="editorVisible"
      :title="'训练草稿 · ' + (detail.agentCode || '')"
      width="92%"
      top="5vh"
      append-to-body
    >
      <el-alert v-if="localDirty" type="warning" :closable="false" class="mb-2">
        有<b>未保存</b>的改动。预检与提交都针对「已保存的内容」——请先保存。
      </el-alert>
      <el-alert v-if="problems.length" type="error" :closable="false" class="mb-2">
        <div>预检未通过（{{ problems.length }} 项）：</div>
        <ul>
          <li v-for="(item, index) in problems" :key="index">{{ item }}</li>
        </ul>
      </el-alert>

      <el-row :gutter="16">
        <el-col :span="14">
          <el-card shadow="never">
            <template #header><b>配置与 Prompt</b></template>
            <el-form label-width="130px">
              <el-form-item label="Agent 名称">
                <el-input v-model="content.agentName" placeholder="展示名；正式名以 Agent 定义为准" />
              </el-form-item>
              <el-form-item label="类别">
                <el-select v-model="content.agentCategory" style="width: 100%" placeholder="从零创建 Agent 时必须选">
                  <el-option v-for="item in categoryOptions" :key="item.code" :label="item.label" :value="item.code" />
                </el-select>
                <div class="text-gray-500" style="font-size: 12px; line-height: 1.5">
                  当前只有四个与创作工厂实现绑定的类别；非创作类 Agent 需要的新类别属独立变更。
                </div>
              </el-form-item>
              <el-form-item label="角色定位">
                <el-input v-model="content.roleDescription" type="textarea" :rows="2" />
              </el-form-item>
              <el-form-item label="主要责任">
                <el-input v-model="content.objective" type="textarea" :rows="2" />
              </el-form-item>
              <el-form-item label="禁止事项">
                <el-input v-model="content.prohibitions" type="textarea" :rows="2" />
              </el-form-item>
              <el-form-item label="所需能力">
                <el-input v-model="content.providerCapability" placeholder="能力编码，如 text_generation（没有它无法路由）" />
              </el-form-item>
              <el-form-item label="允许外发">
                <el-select v-model="content.allowExternal" style="width: 100%">
                  <el-option label="否（N）" value="N" />
                  <el-option label="是（Y）" value="Y" />
                </el-select>
              </el-form-item>
              <el-divider content-position="left">主 Prompt（八分节）</el-divider>
              <el-form-item v-for="key in sectionKeys" :key="key" :label="sectionLabel(key)">
                <el-input v-model="content.promptSections[key]" type="textarea" :rows="3" />
              </el-form-item>
              <el-divider content-position="left">输入 / 输出 Schema（JSON 文本）</el-divider>
              <el-form-item label="输入 Schema">
                <el-input v-model="content.inputSchema" type="textarea" :rows="3" placeholder='{"type":"object"}' />
              </el-form-item>
              <el-form-item label="输出 Schema">
                <el-input v-model="content.outputSchema" type="textarea" :rows="3" placeholder='{"type":"object"}' />
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>

        <el-col :span="10">
          <el-card shadow="never">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <b>修订历史与差异</b>
                <span class="text-gray-500">当前修订 #{{ expectedRevision }}</span>
              </div>
            </template>
            <el-table
              :data="revisions"
              size="small"
              border
              highlight-current-row
              @current-change="selectRevision"
              style="max-height: 240px; overflow: auto"
            >
              <el-table-column label="修订" prop="revisionNo" width="70" />
              <el-table-column label="来源" prop="source" width="140" show-overflow-tooltip />
              <el-table-column label="说明" prop="summary" show-overflow-tooltip />
              <el-table-column label="时间" prop="createTime" width="160" show-overflow-tooltip />
            </el-table>

            <div class="mt-2">
              <el-button
                v-hasPermi="['aig:studio:draft:edit']"
                size="small"
                :disabled="selectedRevisionNo === undefined"
                @click="handleRollback"
              >
                回滚到所选修订
              </el-button>
              <el-button size="small" :disabled="selectedRevisionNo === undefined" @click="clearSelection">清除选择</el-button>
            </div>

            <el-divider content-position="left">差异（当前 ↔ 所选修订）</el-divider>
            <p v-if="selectedRevisionNo === undefined" class="text-gray-500">
              从上面点一条修订，这里会逐节显示与当前内容的差异（改动过的节会标出来）。
            </p>
            <template v-else>
              <p :class="selectedDiffChanged.length ? 'text-orange-500' : 'text-gray-500'">
                {{ selectedSummary }}
              </p>
              <div v-for="item in selectedDiff" :key="item.key" class="diff-block">
                <div class="diff-head">
                  <el-tag v-if="item.changed" type="warning" size="small">改动</el-tag>
                  <el-tag v-else type="info" size="small">一致</el-tag>
                  <span class="diff-label">{{ item.label }}</span>
                </div>
                <template v-if="item.changed">
                  <div class="diff-side"><b>修订 #{{ selectedRevisionNo }}：</b>{{ item.revision || '（空）' }}</div>
                  <div class="diff-side"><b>当前：</b>{{ item.current || '（空）' }}</div>
                </template>
              </div>
            </template>
          </el-card>
        </el-col>
      </el-row>

      <template #footer>
        <el-button @click="editorVisible = false">关 闭</el-button>
        <el-button v-hasPermi="['aig:studio:draft:validate']" @click="handleValidate">预 检</el-button>
        <el-button v-hasPermi="['aig:studio:draft:edit']" type="primary" :loading="saving" @click="handleSave">保 存</el-button>
        <el-button
          v-hasPermi="['aig:studio:draft:submit']"
          type="success"
          :disabled="detail.status === 'ARCHIVED'"
          @click="openSubmit()"
        >
          提 交（产出 DRAFT 版本）
        </el-button>
      </template>
    </el-dialog>

    <!-- 提交 -->
    <el-dialog v-model="submitVisible" title="提交为 DRAFT 版本" width="560px" append-to-body>
      <el-alert type="warning" :closable="false" class="mb-2">
        提交只会把当前内容固化成一条 <b>DRAFT</b> 版本，<b>不会</b>发布、不会跑沙箱、不会批准。
        之后的门槛仍走既有发布流程。
      </el-alert>
      <el-form label-width="110px">
        <el-form-item label="版本号">
          <el-input v-model="submitForm.version" placeholder="留空则按 0.1.<修订号> 生成；被占用会报错让你显式指定" />
        </el-form-item>
        <el-form-item label="版本说明">
          <el-input v-model="submitForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="doSubmit">确 定提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { listDraft, getDraft, getRevision, createDraft, saveDraft, listRevisions, validateDraft, rollbackDraft, archiveDraft, submitDraft } from '@/api/aigov/studio';
import type {
  AigStudioDraftCreateForm,
  AigStudioDraftContentForm,
  AigStudioDraftDetailVO,
  AigStudioDraftQuery,
  AigStudioDraftSubmitForm,
  AigStudioDraftVO,
  AigStudioRevisionVO
} from '@/api/aigov/studio/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { PROMPT_SECTION_LABELS, diffSummary, parseDraftContent, sectionDiff } from './diff';

defineOptions({ name: 'AigStudio' });

/** 当前后端只支持这四个类别（与创作工厂的具体实现绑定） */
const categoryOptions = [
  { code: 'PLANNING', label: 'PLANNING（详情页策划）' },
  { code: 'VISUAL_DNA', label: 'VISUAL_DNA（视觉 DNA）' },
  { code: 'GENERATION', label: 'GENERATION（生成任务构建）' },
  { code: 'QA', label: 'QA（视觉 QA）' }
];

const sectionKeys = Object.keys(PROMPT_SECTION_LABELS);
const sectionLabel = (key: string) => PROMPT_SECTION_LABELS[key] ?? key;

const draftList = ref<AigStudioDraftVO[]>([]);
const total = ref(0);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const queryParams = ref<AigStudioDraftQuery>({ pageNum: 1, pageSize: 10 });

const createVisible = ref(false);
const creating = ref(false);
const createFormRef = ref<ElFormInstance>();
const createForm = ref<AigStudioDraftCreateForm>({ agentCode: '' });
const createRules = { agentCode: [{ required: true, message: '训练对象编码不能为空', trigger: 'blur' }] };

const editorVisible = ref(false);
const saving = ref(false);
const detail = ref<AigStudioDraftDetailVO>({ draftId: '', agentCode: '' });
const content = ref<AigStudioDraftContentForm>({ promptSections: {} });
const expectedRevision = ref(0);
const localDirty = ref(false);
const problems = ref<string[]>([]);

const revisions = ref<AigStudioRevisionVO[]>([]);
const selectedRevisionNo = ref<number | undefined>(undefined);
const selectedRevisionJson = ref<string | undefined>(undefined);

/**
 * 「正在载入内容」标记：载入本身不是用户的改动。
 * 少了它，深度 watcher 会在载入后把 localDirty 又置回 true，
 * 于是页面一打开就喊"有未保存的改动"并挡住预检/提交。
 */
const contentLoaded = ref(false);

const submitVisible = ref(false);
const submitting = ref(false);
const submitForm = ref<AigStudioDraftSubmitForm>({});

/** 当前内容序列化（用于与所选修订比较） */
const currentJson = computed(() => JSON.stringify(content.value));
const selectedDiff = computed(() => sectionDiff(currentJson.value, selectedRevisionJson.value));
const selectedDiffChanged = computed(() => selectedDiff.value.filter(item => item.changed));
const selectedSummary = computed(() => diffSummary(currentJson.value, selectedRevisionJson.value));

watch(
  content,
  () => {
    if (contentLoaded.value) localDirty.value = true;
  },
  { deep: true }
);

const getList = async () => {
  await withLoading(async () => {
    const res = await listDraft(queryParams.value);
    draftList.value = res.data.rows || [];
    total.value = res.data.total || 0;
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.value = { pageNum: 1, pageSize: 10 };
  getList();
};

const openCreate = () => {
  createForm.value = { agentCode: '' };
  createVisible.value = true;
};

const doCreate = async () => {
  if (!createFormRef.value) return;
  await createFormRef.value.validate();
  creating.value = true;
  try {
    const res = await createDraft(createForm.value);
    createVisible.value = false;
    modal.msgSuccess('草稿已创建（含初始修订）');
    await getList();
    await openEditor({ draftId: res.data });
  } finally {
    creating.value = false;
  }
};

/** 把库里的内容 JSON 变成可编辑对象（拿不到就给八节空骨架，不让人从空对象开始） */
const toForm = (json?: string | null): AigStudioDraftContentForm => {
  const parsed = parseDraftContent(json);
  const base = parsed ?? {};
  const sections: Record<string, string> = {};
  for (const key of sectionKeys) {
    sections[key] = '';
  }
  if (parsed?.promptSections && typeof parsed.promptSections === 'object') {
    for (const [key, value] of Object.entries(parsed.promptSections as Record<string, unknown>)) {
      sections[key] = value === null || value === undefined ? '' : String(value);
    }
  }
  return { ...base, promptSections: sections } as AigStudioDraftContentForm;
};

/**
 * 载入一份内容到编辑器（并把"载入"排除在改动之外）。
 *
 * @param json 库里的内容 JSON
 */
const applyContent = async (json?: string | null) => {
  contentLoaded.value = false;
  content.value = toForm(json);
  await nextTick();
  contentLoaded.value = true;
  localDirty.value = false;
};

/**
 * Element Plus 的表格插槽把行类型暴露为 `DefaultRow`，不能直接断言成业务 VO；
 * 因此这几个"从行进入"的入口按宽松类型接收，内部再取需要的字段。
 *
 * @param row 表格行
 */
const openEditor = async (row: any) => {
  const res = await getDraft(row.draftId);
  detail.value = res.data;
  expectedRevision.value = res.data.latestRevision ?? 0;
  problems.value = [];
  selectedRevisionNo.value = undefined;
  selectedRevisionJson.value = undefined;
  await applyContent(res.data.contentJson);
  await loadRevisions();
  editorVisible.value = true;
};

const loadRevisions = async () => {
  if (!detail.value.draftId) return;
  const res = await listRevisions(detail.value.draftId);
  revisions.value = res.data || [];
};

const selectRevision = async (row?: AigStudioRevisionVO) => {
  if (!row) return;
  selectedRevisionNo.value = row.revisionNo;
  // 列表接口可能不下发快照，取详情拿完整内容
  const res = await getRevision(row.revisionId);
  selectedRevisionJson.value = res.data?.contentSnapshotJson ?? row.contentSnapshotJson;
};

const clearSelection = () => {
  selectedRevisionNo.value = undefined;
  selectedRevisionJson.value = undefined;
};

const handleValidate = async (): Promise<boolean> => {
  if (localDirty.value) {
    modal.msgWarning('有未保存的改动：预检针对「已保存的内容」，请先保存');
    return false;
  }
  const res = await validateDraft(detail.value.draftId);
  problems.value = res.data.problems || [];
  if (res.data.passed) {
    modal.msgSuccess('预检通过');
    return true;
  }
  modal.alertError('预检未通过（' + problems.value.length + ' 项）：\n' + problems.value.join('\n'));
  return false;
};

const handleSave = async () => {
  saving.value = true;
  try {
    const res = await saveDraft(detail.value.draftId, {
      expectedRevision: expectedRevision.value,
      contentJson: JSON.stringify(content.value)
    });
    localDirty.value = false;
    problems.value = [];
    detail.value = res.data;
    expectedRevision.value = res.data.latestRevision ?? expectedRevision.value;
    if (res.data.revisionCreated) {
      modal.msgSuccess('已保存，产生新修订 #' + expectedRevision.value);
    } else {
      modal.msgSuccess('内容与当前一致，未产生新修订');
    }
    await loadRevisions();
    await getList();
  } catch (e: any) {
    // 冲突消息里给出了"你手上的修订号 / 库中当前"，原样展示，人才能决定刷新还是重改
    modal.alertError(e?.message || '保存失败');
  } finally {
    saving.value = false;
  }
};

const handleRollback = async () => {
  if (localDirty.value) {
    modal.msgWarning('有未保存的改动：请先保存或放弃后再回滚');
    return;
  }
  await modal.confirm(
    '回滚到修订 #' +
      selectedRevisionNo.value +
      '？这会**新增一条内容相同的新修订**，历史不会被改掉（要再回到新内容，回滚那次也查得到）。'
  );
  const res = await rollbackDraft(detail.value.draftId, {
    targetRevisionNo: selectedRevisionNo.value as number,
    expectedRevision: expectedRevision.value
  });
  const updated = await getDraft(detail.value.draftId);
  detail.value = updated.data;
  expectedRevision.value = updated.data.latestRevision ?? 0;
  await applyContent(updated.data.contentJson);
  await loadRevisions();
  await getList();
  modal.msgSuccess(res.data.revisionCreated ? '已回滚（产生新修订）' : '当前内容已与所选修订一致，未产生新修订');
};

const openSubmit = async () => {
  if (!(await handleValidate())) return;
  submitForm.value = {};
  submitVisible.value = true;
};

const doSubmit = async () => {
  submitting.value = true;
  try {
    const res = await submitDraft(detail.value.draftId, submitForm.value);
    const data = res.data;
    submitVisible.value = false;
    editorVisible.value = false;
    await getList();
    modal.alertSuccess(
      '已提交为 DRAFT 版本：\n' +
        'agentVersionId=' +
        data.agentVersionId +
        '，version=' +
        data.version +
        (data.agentCreated ? '\n（本次新建了 Agent 定义）' : '') +
        '\n\n注意：这只是草稿版本，正式发布仍走既有发布门槛（沙箱/黄金用例/人工批准/灰度）。'
    );
  } finally {
    submitting.value = false;
  }
};

const handleValidateRow = async (row: any) => {
  const res = await validateDraft(row.draftId);
  if (res.data.passed) {
    modal.alertSuccess('预检通过：' + (res.data.draftId as string) + ' 修订 #' + res.data.revision);
  } else {
    modal.alertError('预检未通过（' + res.data.problems.length + ' 项）：\n' + res.data.problems.join('\n'));
  }
};

const openSubmitRow = async (row: any) => {
  await openEditor(row);
  await openSubmit();
};

const handleArchive = async (row: any) => {
  await modal.confirm('归档草稿「' + row.agentCode + '」？归档是终态，之后不能再编辑（要再训练请新建草稿）。');
  await archiveDraft(row.draftId);
  modal.msgSuccess('已归档');
  getList();
};

onMounted(() => {
  getList();
});
</script>

<style scoped>
.diff-block {
  border-bottom: 1px dashed var(--app-border-color, #e4e7ed);
  padding: 6px 0;
}

.diff-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.diff-label {
  font-size: 13px;
  font-weight: 600;
}

.diff-side {
  font-size: 12px;
  color: var(--app-text-color-secondary, #606266);
  white-space: pre-wrap;
  word-break: break-word;
  margin-top: 2px;
}
</style>
