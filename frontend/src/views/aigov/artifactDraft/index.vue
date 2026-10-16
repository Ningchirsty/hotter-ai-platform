<template>
  <div class="p-2 app-container aigov-artifact-draft-page">
    <PageHeading
      title="AI平台治理"
      subtitle="管理AI能力、模型接入与调用策略"
      admin
      module="aigov"
    />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>草案检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="草案编码" prop="draftCode">
            <el-input v-model="queryParams.draftCode" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="草案名称" prop="draftName">
            <el-input v-model="queryParams.draftName" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="启用" prop="enabled">
            <el-select v-model="queryParams.enabled" placeholder="请选择" clearable style="width: 140px">
              <el-option label="启用" value="Y" />
              <el-option label="停用" value="N" />
            </el-select>
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
            <span class="panel-kicker">Artifact Drafts</span>
            <h3>草案生成器</h3>
            <p>
              三类草案<b>各自落表</b>，它们都<b>只产草案、不等同可执行</b>：生成物是一份
              <b>可提交的训练台草稿内容</b>（八分节齐全、类别与能力已校验），要不要提交、
              提交成什么版本，仍由人在训练台里决定并走既有五道门槛。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['aig:studio:artifact-draft']"
              type="primary"
              plain
              icon="Plus"
              @click="handleAdd"
            >
              新增{{ activeLabel }}
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeKind" @tab-change="getList">
        <el-tab-pane
          v-for="tab in ARTIFACT_KIND_TABS"
          :key="tab.value"
          :label="tab.label"
          :name="tab.value"
        />
      </el-tabs>

      <el-table v-if="activeKind === 'SKILL'" v-loading="loading" border class="data-table" :data="skillList">
        <template #empty>
          <AigEmptyState
            icon="Document"
            title="还没有草案"
            hint="草案生成器产出的内容先落成草案，人工确认后才可提交；没有草案说明还没生成过。"
          >
            <el-button size="small" @click="getList">刷新</el-button>
          </AigEmptyState>
        </template>
        <el-table-column label="草案编码" prop="draftCode" min-width="180" show-overflow-tooltip />
        <el-table-column label="草案名称" prop="draftName" min-width="150" show-overflow-tooltip />
        <el-table-column label="技能编码" prop="skillCode" min-width="140" show-overflow-tooltip />
        <el-table-column label="类别" prop="agentCategory" width="120" />
        <el-table-column label="所需能力" prop="providerCapability" min-width="140" show-overflow-tooltip />
        <el-table-column label="启用" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'Y' ? 'success' : 'info'" size="small">
              {{ row.enabled === 'Y' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openPreview(row)">预览/生成</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else-if="activeKind === 'CHILD_AGENT'"
        v-loading="loading"
        border
        class="data-table"
        :data="childList"
      >
        <template #empty>
          <AigEmptyState
            icon="Connection"
            title="还没有子 Agent 草案"
            hint="子 Agent 由编排草案拆分而来；先在编排里声明子 Agent，这里才会有可生成的行。"
          >
            <el-button size="small" @click="getList">刷新</el-button>
          </AigEmptyState>
        </template>
        <el-table-column label="草案编码" prop="draftCode" min-width="180" show-overflow-tooltip />
        <el-table-column label="草案名称" prop="draftName" min-width="150" show-overflow-tooltip />
        <el-table-column label="Agent 编码" prop="agentCode" min-width="150" show-overflow-tooltip />
        <el-table-column label="类别" prop="agentCategory" width="120" />
        <el-table-column label="父 Agent" prop="parentAgentCode" min-width="150" show-overflow-tooltip />
        <el-table-column label="启用" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'Y' ? 'success' : 'info'" size="small">
              {{ row.enabled === 'Y' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openPreview(row)">预览/生成</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else v-loading="loading" border class="data-table" :data="actionList">
        <template #empty>
          <AigEmptyState
            icon="Document"
            title="还没有指令草案"
            hint="指令草案是把「一次动作」落成可提交内容；没有说明还没生成过这一类。"
          >
            <el-button size="small" @click="getList">刷新</el-button>
          </AigEmptyState>
        </template>
        <el-table-column label="草案编码" prop="draftCode" min-width="180" show-overflow-tooltip />
        <el-table-column label="草案名称" prop="draftName" min-width="150" show-overflow-tooltip />
        <el-table-column label="指令编码" prop="actionCode" min-width="150" show-overflow-tooltip />
        <el-table-column label="启动方式" prop="launchMode" width="120" />
        <el-table-column label="启动目标" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.targetType ? row.targetType + ' / ' + (row.targetRef || '-') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="类别" prop="agentCategory" width="120" />
        <el-table-column label="启用" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'Y' ? 'success' : 'info'" size="small">
              {{ row.enabled === 'Y' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openPreview(row)">预览/生成</el-button>
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

    <!-- 新增草案（字段按类型切换） -->
    <el-dialog v-model="addVisible" :title="'新增' + activeLabel" width="640px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        草案只是<b>种子</b>：登记它不会改任何草稿。生成时才会把内容落到某个训练草稿的一条新修订上。
        类别与所需能力是<b>必填</b>——生成物是一份 Agent 内容，平台不替草案猜实现绑定。
      </el-alert>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="120px">
        <el-form-item label="草案编码" prop="draftCode">
          <el-input v-model="addForm.draftCode" placeholder="页面上认出草案的唯一方式，不能重复" />
        </el-form-item>
        <el-form-item label="草案名称" prop="draftName">
          <el-input v-model="addForm.draftName" placeholder="留空取草案编码" />
        </el-form-item>

        <template v-if="activeKind === 'SKILL'">
          <el-form-item label="技能编码" prop="skillCode">
            <el-input v-model="addForm.skillCode" placeholder="生成后落到草稿内容的 allowedSkills" />
          </el-form-item>
          <el-form-item label="技能名称" prop="skillName">
            <el-input v-model="addForm.skillName" />
          </el-form-item>
          <el-form-item label="技能能力" prop="capabilities">
            <el-input v-model="addForm.capabilities" placeholder="逗号分隔；仅人读清单，不参与路由" />
          </el-form-item>
          <el-form-item label="技能说明" prop="description">
            <el-input v-model="addForm.description" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="使用说明" prop="usageNotes">
            <el-input v-model="addForm.usageNotes" type="textarea" :rows="2" />
          </el-form-item>
        </template>

        <template v-else-if="activeKind === 'CHILD_AGENT'">
          <el-form-item label="Agent 编码" prop="agentCode">
            <el-input v-model="addForm.agentCode" placeholder="不在生成物里：提交用训练台草稿自身的编码" />
          </el-form-item>
          <el-form-item label="Agent 名称" prop="agentName">
            <el-input v-model="addForm.agentName" />
          </el-form-item>
          <el-form-item label="父 Agent" prop="parentAgentCode">
            <el-input v-model="addForm.parentAgentCode" placeholder="不在生成物里（草稿内容没有这个槽位）" />
          </el-form-item>
          <el-form-item label="角色定位" prop="roleDescription">
            <el-input v-model="addForm.roleDescription" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="主要责任" prop="objective">
            <el-input v-model="addForm.objective" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="禁止事项" prop="prohibitions">
            <el-input v-model="addForm.prohibitions" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="输出成果" prop="outputSummary">
            <el-input v-model="addForm.outputSummary" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="八分节 JSON" prop="promptSectionsJson">
            <el-input
              v-model="addForm.promptSectionsJson"
              type="textarea"
              :rows="4"
              placeholder='{"workflow_rules":"先查事实再写","inputs":"SKU 编号"}'
            />
          </el-form-item>
          <el-form-item label="允许外发" prop="allowExternal">
            <el-select v-model="addForm.allowExternal" style="width: 100%">
              <el-option label="否（N，缺省）" value="N" />
              <el-option label="是（Y）" value="Y" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="指令编码" prop="actionCode">
            <el-input v-model="addForm.actionCode" placeholder="岗位卡片上的 actionCode" />
          </el-form-item>
          <el-form-item label="指令名称" prop="actionName">
            <el-input v-model="addForm.actionName" />
          </el-form-item>
          <el-form-item label="启动方式" prop="launchMode">
            <el-select v-model="addForm.launchMode" style="width: 100%" clearable>
              <el-option label="QUICK 轻量任务" value="QUICK" />
              <el-option label="FORM 表单任务" value="FORM" />
              <el-option label="STUDIO 专业工作台" value="STUDIO" />
              <el-option label="NAVIGATION 纯导航" value="NAVIGATION" />
            </el-select>
          </el-form-item>
          <el-form-item label="启动目标类型" prop="targetType">
            <el-select v-model="addForm.targetType" style="width: 100%" clearable>
              <el-option label="SCENARIO 场景包" value="SCENARIO" />
              <el-option label="QUICK_CAPABILITY 轻量能力" value="QUICK_CAPABILITY" />
              <el-option label="AGENT_VERSION Agent 版本" value="AGENT_VERSION" />
              <el-option label="NAVIGATION 受控跳转" value="NAVIGATION" />
            </el-select>
          </el-form-item>
          <el-form-item label="启动目标引用" prop="targetRef">
            <el-input v-model="addForm.targetRef" placeholder="版本ID / 能力编码 / routeKey" />
          </el-form-item>
          <el-form-item label="指令目标" prop="objective">
            <el-input v-model="addForm.objective" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="产出要求" prop="outputContract">
            <el-input v-model="addForm.outputContract" type="textarea" :rows="2" />
          </el-form-item>
        </template>

        <el-form-item label="Agent 类别" prop="agentCategory">
          <el-select v-model="addForm.agentCategory" style="width: 100%" placeholder="必填：决定绑定哪个实现">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="所需能力" prop="providerCapability">
          <el-input v-model="addForm.providerCapability" placeholder="能力编码，如 text_generation" />
        </el-form-item>
        <el-form-item label="允许工具" prop="allowedTools">
          <el-input v-model="addForm.allowedTools" placeholder="逗号分隔；可空" />
        </el-form-item>
        <el-form-item v-if="activeKind === 'CHILD_AGENT'" label="知识范围" prop="knowledgeScope">
          <el-input v-model="addForm.knowledgeScope" placeholder="逗号分隔；可空" />
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-radio-group v-model="addForm.enabled">
            <el-radio value="Y">启用</el-radio>
            <el-radio value="N">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 预览 / 生成 -->
    <el-dialog v-model="previewVisible" title="预览生成物 → 生成到训练草稿" width="760px" append-to-body>
      <p class="preview-summary">{{ generateSummary(generated) }}</p>
      <el-alert type="warning" :closable="false" class="mb-2">
        生成会<b>立即写一条新修订</b>到目标训练草稿（来源 DRAFT_GENERATED），但<b>不会</b>提交、不会发布。
        下面的说明列的是"草案里没有写进内容"和"留空待补充"的项——生成器不替你编内容。
      </el-alert>

      <el-descriptions v-if="generated" :column="1" border size="small" class="mb-2">
        <el-descriptions-item v-for="row in declarationRows(generated.content)" :key="row.label" :label="row.label">
          {{ row.value }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="generated">
        <div v-for="row in sectionRows(generated.content)" :key="row.key" class="section-row">
          <div class="section-head">
            <el-tag :type="row.empty ? 'info' : 'success'" size="small">{{ row.empty ? '空' : '已填' }}</el-tag>
            <span class="section-label">{{ row.label }}</span>
          </div>
          <div v-if="!row.empty" class="section-text">{{ row.text }}</div>
        </div>
      </div>

      <el-divider content-position="left">生成说明</el-divider>
      <ul class="note-list">
        <li v-for="(note, index) in generated?.notes || []" :key="index">{{ note }}</li>
      </ul>

      <el-divider content-position="left">落到哪个训练草稿</el-divider>
      <el-form label-width="130px">
        <el-form-item label="目标草稿ID" required>
          <el-input v-model="generateForm.targetDraftId" placeholder="训练台里那份草稿的 draftId" />
        </el-form-item>
        <el-form-item label="期望修订号" required>
          <el-input-number v-model="generateForm.expectedRevision" :min="1" :controls="false" style="width: 100%" />
          <div class="text-gray-500" style="font-size: 12px; line-height: 1.5">
            必须是目标草稿<b>当前</b>的修订号（训练台「修订历史」里能看到）。带它是为了不被别人的并发修改悄悄覆盖。
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="previewVisible = false">关 闭</el-button>
        <el-button :loading="previewing" @click="doPreview">重新预览</el-button>
        <el-button
          v-hasPermi="['aig:studio:draft:edit']"
          type="primary"
          :loading="generating"
          @click="doGenerate"
        >
          生成到草稿
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  addActionDraft,
  addChildAgentDraft,
  addSkillDraft,
  generateArtifactDraft,
  listActionDrafts,
  listChildAgentDrafts,
  listSkillDrafts,
  previewArtifactDraft
} from '@/api/aigov/artifactDraft';
import type {
  AigStudioActionDraftForm,
  AigStudioActionDraftVO,
  AigStudioArtifactDraftQuery,
  AigStudioArtifactGenerateForm,
  AigStudioArtifactGenerateVO,
  AigStudioArtifactKind,
  AigStudioChildAgentDraftForm,
  AigStudioChildAgentDraftVO,
  AigStudioSkillDraftForm,
  AigStudioSkillDraftVO
} from '@/api/aigov/artifactDraft/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import AigEmptyState from '../components/AigEmptyState.vue';
import {
  ARTIFACT_KIND_TABS,
  declarationRows,
  generateSummary,
  kindPath,
  sectionRows
} from './presentation';

defineOptions({ name: 'AigStudioArtifactDraft' });

/** 与后端 AigAgentCategoryEnum 一致 */
const categoryOptions = [
  { value: 'PLANNING', label: 'PLANNING（详情页策划）' },
  { value: 'VISUAL_DNA', label: 'VISUAL_DNA（视觉 DNA）' },
  { value: 'GENERATION', label: 'GENERATION（生成任务构建）' },
  { value: 'QA', label: 'QA（视觉 QA）' },
  { value: 'ANALYSIS', label: 'ANALYSIS（行业分析，非创作类）' }
];

const activeKind = ref<AigStudioArtifactKind>('SKILL');
const activeLabel = computed(
  () => ARTIFACT_KIND_TABS.find(item => item.value === activeKind.value)?.label ?? '草案'
);

const skillList = ref<AigStudioSkillDraftVO[]>([]);
const childList = ref<AigStudioChildAgentDraftVO[]>([]);
const actionList = ref<AigStudioActionDraftVO[]>([]);
const total = ref(0);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const queryFormRef = ref<ElFormInstance>();

const queryParams = ref<AigStudioArtifactDraftQuery>({ pageNum: 1, pageSize: 10, params: {} });

const addVisible = ref(false);
const addFormRef = ref<ElFormInstance>();
const addForm = ref<Record<string, any>>({ enabled: 'Y' });
const addRules = {
  draftCode: [{ required: true, message: '草案编码不能为空', trigger: 'blur' }],
  agentCategory: [{ required: true, message: 'Agent 类别不能为空（生成物是一份 Agent 内容）', trigger: 'change' }],
  providerCapability: [{ required: true, message: '所需能力不能为空（没有它无法路由）', trigger: 'blur' }],
  skillCode: [{ required: true, message: '技能编码不能为空', trigger: 'blur' }]
};

const previewVisible = ref(false);
const previewing = ref(false);
const generating = ref(false);
const previewRow = ref<Record<string, any>>();
const generated = ref<AigStudioArtifactGenerateVO>();
const generateForm = ref<AigStudioArtifactGenerateForm>({});

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 取值按当前类型走各自的清单接口（三类各自落表） */
const getList = async () => {
  await withLoading(async () => {
    const query = queryParams.value;
    if (activeKind.value === 'SKILL') {
      const res = await listSkillDrafts(query);
      skillList.value = res.data?.rows || [];
      total.value = res.data?.total || 0;
    } else if (activeKind.value === 'CHILD_AGENT') {
      const res = await listChildAgentDrafts(query);
      childList.value = res.data?.rows || [];
      total.value = res.data?.total || 0;
    } else {
      const res = await listActionDrafts(query);
      actionList.value = res.data?.rows || [];
      total.value = res.data?.total || 0;
    }
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 打开新增（表单按类型重置，避免上一种类型的字段残留） */
const handleAdd = () => {
  addForm.value = { enabled: 'Y' };
  addVisible.value = true;
};

/** 提交新增：按类型走各自的端点与入参 */
const submitAdd = async () => {
  await addFormRef.value?.validate();
  const path = kindPath(activeKind.value);
  if (!path) {
    modal.msgError('草案类型未知，无法提交');
    return;
  }
  if (activeKind.value === 'SKILL') {
    await addSkillDraft(addForm.value as AigStudioSkillDraftForm);
  } else if (activeKind.value === 'CHILD_AGENT') {
    await addChildAgentDraft(addForm.value as AigStudioChildAgentDraftForm);
  } else {
    await addActionDraft(addForm.value as AigStudioActionDraftForm);
  }
  modal.msgSuccess('新增成功');
  addVisible.value = false;
  getList();
};

/** 取草案ID（三类字段名不同） */
const idOf = (row: Record<string, any>): string | number =>
  row.skillDraftId ?? row.childAgentDraftId ?? row.actionDraftId ?? '';

/** 打开预览：先取一次生成物 */
const openPreview = async (row: Record<string, any>) => {
  previewRow.value = row;
  generated.value = undefined;
  generateForm.value = { targetDraftId: undefined, expectedRevision: undefined };
  previewVisible.value = true;
  await doPreview();
};

/** 预览（不写任何表） */
const doPreview = async () => {
  const row = previewRow.value;
  if (!row) return;
  const path = kindPath(activeKind.value);
  const id = idOf(row);
  if (!path || !id) {
    modal.msgError('草案类型或ID未知，无法预览');
    return;
  }
  previewing.value = true;
  try {
    const res = await previewArtifactDraft(path, id);
    generated.value = res.data;
  } catch (e) {
    // 缺类别/能力、八分节 JSON 非法等都在这里如实展示
    modal.alertError('预览失败：' + ((e as { message?: string })?.message || '未知原因'));
  } finally {
    previewing.value = false;
  }
};

/** 生成：写目标草稿的一条新修订（带 CAS） */
const doGenerate = async () => {
  const row = previewRow.value;
  if (!row) return;
  const path = kindPath(activeKind.value);
  const id = idOf(row);
  if (!path || !id) {
    modal.msgError('草案类型或ID未知，无法生成');
    return;
  }
  if (!generateForm.value.targetDraftId || !generateForm.value.expectedRevision) {
    modal.msgWarning('请填目标草稿ID与期望修订号');
    return;
  }
  await modal.confirm(
    '把「' +
      (row.draftName || row.draftCode) +
      '」的生成结果落到草稿 #' +
      generateForm.value.targetDraftId +
      '？这会立即产生一条新修订（来源 DRAFT_GENERATED）。不会提交、不会发布。'
  );
  generating.value = true;
  try {
    const res = await generateArtifactDraft(path, id, generateForm.value);
    generated.value = res.data;
    modal.msgSuccess(generateSummary(res.data));
    getList();
  } catch (e) {
    // 失效（基于旧修订）/ 内容与目标草稿一致 / 无权限，服务端都有明确文案
    modal.alertError((e as { message?: string })?.message || '生成失败');
  } finally {
    generating.value = false;
  }
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.preview-summary {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
}

.section-row {
  border-bottom: 1px dashed var(--app-border-color, #e4e7ed);
  padding: 6px 0;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-label {
  font-size: 12.5px;
  font-weight: 600;
}

.section-text {
  margin-top: 2px;
  font-size: 12px;
  color: var(--app-text-color-secondary, #606266);
  white-space: pre-wrap;
  word-break: break-word;
}

.note-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--app-text-color-secondary, #606266);
}
</style>
