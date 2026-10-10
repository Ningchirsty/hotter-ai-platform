<template>
  <div class="p-2 app-container aigov-agent-page">
    <PageHeading title="AI平台治理" subtitle="管理AI能力、模型接入与调用策略" admin module="aigov" />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>Agent 检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="Agent编码" prop="agentCode">
            <el-input v-model="queryParams.agentCode" placeholder="精确匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="Agent名称" prop="agentName">
            <el-input v-model="queryParams.agentName" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="类别" prop="category">
            <el-select v-model="queryParams.category" placeholder="请选择类别" clearable style="width: 160px">
              <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源" prop="builtin">
            <el-select v-model="queryParams.builtin" placeholder="请选择来源" clearable style="width: 160px">
              <el-option label="平台内置" value="Y" />
              <el-option label="Package 带入" value="N" />
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
            <span class="panel-kicker">Agent Registry</span>
            <h3>Agent 注册中心</h3>
            <p>
              共 {{ total }} 条记录；Agent 不是一段代码，而是一条被版本化、被审批、可回滚的配置。
              点「版本」看待发布版本，点「推进」走发布门槛（服务层会核对库里的证据）。
            </p>
          </div>
          <div class="toolbar-actions">
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="agentList">
        <el-table-column label="Agent编码" align="center" prop="agentCode" width="200" show-overflow-tooltip />
        <el-table-column label="名称" align="center" prop="agentName" width="160" show-overflow-tooltip />
        <el-table-column label="类别" align="center" width="120">
          <template #default="scope">
            <el-tag type="info">{{ categoryLabel(scope.row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" align="center" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.builtin === 'Y' ? 'success' : 'warning'">
              {{ scope.row.builtin === 'Y' ? '平台内置' : 'Package 带入' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" align="center" prop="description" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'">
              {{ scope.row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="160" fixed="right">
          <template #default="scope">
            <el-button v-hasPermi="['aig:agent:list']" link type="primary" icon="View" @click="openVersions(scope.row)">
              版本
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

    <!-- 版本列表 + 发布推进 -->
    <el-dialog v-model="versionVisible" :title="'版本 · ' + (currentAgent?.agentName || '')" width="1000px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        发布状态机：DRAFT → VALIDATED → SANDBOX_TESTED → CANDIDATE → STABLE（另有 DISABLED/ARCHIVED）。
        推进时服务层会核对门槛证据：Manifest 校验看 scan_result、黄金用例看评测账本。
      </el-alert>
      <el-table v-loading="versionLoading" border :data="versionList">
        <el-table-column label="版本" align="center" prop="version" width="90" />
        <el-table-column label="发布状态" align="center" width="130">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.releaseStatus)">{{ scope.row.releaseStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="通道" align="center" prop="releaseChannel" width="100" />
        <el-table-column label="外部调用" align="center" width="100">
          <template #default="scope">{{ scope.row.allowExternal === 'Y' ? '允许' : '禁止' }}</template>
        </el-table-column>
        <el-table-column label="来源Package版本" align="center" prop="packageVersionId" width="170" show-overflow-tooltip />
        <el-table-column label="最近评测" align="center" prop="evaluationRunId" width="150" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="140" fixed="right">
          <template #default="scope">
            <el-button
              v-hasPermi="['aig:agent:release']"
              link
              type="primary"
              icon="Promotion"
              :disabled="isTerminal(scope.row.releaseStatus)"
              @click="openAdvance(scope.row)"
            >
              推进
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="advanceVisible" title="推进发布状态" width="560px" append-to-body>
      <el-form ref="advanceFormRef" :model="advanceForm" label-width="130px">
        <el-form-item label="对象">
          <el-input :model-value="advanceForm.targetType + ' #' + advanceForm.targetVersionId" disabled />
        </el-form-item>
        <el-form-item label="我以为当前是" prop="expectedStatus">
          <el-input v-model="advanceForm.expectedStatus" disabled />
        </el-form-item>
        <el-form-item label="目标状态" prop="toStatus">
          <el-select v-model="advanceForm.toStatus" placeholder="请选择目标状态" style="width: 100%">
            <el-option v-for="item in nextStatusOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="已通过门槛">
          <el-checkbox-group v-model="advanceForm.passedGates">
            <el-checkbox v-for="gate in gateOptions" :key="gate.value" :value="gate.value">{{ gate.label }}</el-checkbox>
          </el-checkbox-group>
          <div class="gate-hint">
            服务层不会只看这里勾了什么：Manifest 校验、沙箱运行、黄金用例、灰度达标都会去库里核对证据，
            没有证据的声明一律拒绝。缺哪些门槛可点下面的「查还差什么」。
          </div>
        </el-form-item>
        <el-form-item label="沙箱证据">
          <div style="width: 100%">
            <el-alert
              v-if="sandboxEvidence"
              :type="sandboxEvidence.satisfied ? 'success' : 'warning'"
              :closable="false"
              class="mb-2"
              show-icon
            >
              <template #title>
                {{ sandboxEvidence.satisfied ? '沙箱证据成立（可勾选「沙箱运行」推进）' : '沙箱证据不成立' }}
              </template>
              <div v-if="sandboxEvidence.satisfied" class="gate-hint">
                作业 {{ sandboxEvidence.jobId }}、镜像 {{ sandboxEvidence.imageRef }}、退出码
                {{ sandboxEvidence.exitCode }}、产物 {{ sandboxEvidence.artifactCount }} 个、网络
                {{ sandboxEvidence.network }}、登记于 {{ sandboxEvidence.recordedAt }}
              </div>
              <div v-else class="gate-hint">
                {{ sandboxEvidence.reason }}
                <template v-if="sandboxEvidence.runId">
                  <br />最近一次运行：作业 {{ sandboxEvidence.jobId }}、退出码 {{ sandboxEvidence.exitCode }}、超时
                  {{ sandboxEvidence.timedOut }}、网络 {{ sandboxEvidence.network }}
                </template>
              </div>
            </el-alert>
            <!--
              「未验签」必须显式说出来：这条证据能证明"有人提交了这份 result.json、提交后没被改过"，
              不能证明"真的跑过"（提交方自述 image/exitCode/network）。把它写成小字提示，
              而不是让它藏在文档里——否则界面上的绿灯会被读成比它实际更强的结论。
            -->
            <el-alert
              v-if="sandboxEvidence && sandboxEvidence.attestation !== 'SIGNED'"
              type="warning"
              :closable="false"
              class="mb-2"
            >
              <template #title>未验签证据（可信度：{{ sandboxEvidence.attestation || 'UNATTESTED' }}）</template>
              <div class="gate-hint">
                只证明「有人提交了这份 result.json、提交后未被改过」，**不证明它来自一次真实运行**——
                镜像/退出码/网络模式都是提交方自述。闭环需要执行器私钥签名 + 平台公钥验签，尚未实现。
              </div>
            </el-alert>
            <el-button
              v-hasPermi="['aig:sandbox:record']"
              link
              type="primary"
              icon="Upload"
              @click="openRecord"
            >
              登记沙箱运行
            </el-button>
            <el-button link icon="Refresh" @click="loadSandboxEvidence">刷新证据</el-button>
            <!--
              这条口径必须出现在门槛旁边，而不是只写在文档里：训练台的"测试调用"是一次受治理的
              模型调用（记录在 aig_studio_execution_link），**不参与**这道门槛判定。把它悄悄接进来
              会把"不可信代码真的在隔离环境跑过"这唯一一道证据换成"模型说这次没问题"。
            -->
            <div class="gate-hint">
              训练台的「测试调用」只作参考，不参与这条门槛判定：它是一次模型调用，
              不等于不可信代码在隔离容器里跑通（门槛只认宿主机沙箱作业登记进账本的结果）。
            </div>
          </div>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="advanceForm.detail" type="textarea" :rows="2" placeholder="可空；会写进发布事件账本" />
        </el-form-item>
      </el-form>
      <el-alert v-if="missingGateText" type="warning" :closable="false" class="mb-2">{{ missingGateText }}</el-alert>
      <template #footer>
        <el-button @click="advanceVisible = false">取消</el-button>
        <el-button @click="handleMissingGates">查还差什么</el-button>
        <el-button type="primary" @click="submitAdvance">确定推进</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recordVisible" title="登记沙箱运行" width="640px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        这一步只做「把执行器吐出来的结果记进账本」。作业本身在宿主机上跑：
        <code>/opt/hotter-sandbox/sandbox-worker.sh --once</code>，随后把作业目录里的
        <code>result.json</code> **原样**粘到下面（不要手改、不要只挑几个字段填——手改过的结果
        与库里的哈希对不上，事后也说不清是谁改的）。
      </el-alert>
      <el-form ref="recordFormRef" :model="recordForm" label-width="110px">
        <el-form-item label="对象">
          <el-input :model-value="recordForm.targetType + ' #' + recordForm.targetVersionId" disabled />
        </el-form-item>
        <el-form-item label="作业ID" prop="jobId">
          <el-input v-model="recordForm.jobId" placeholder="作业目录名，例如 vibeposter-20261010-01" />
          <div class="gate-hint">必须与 result.json 里的 jobId 一致；一个作业只能登记一次。</div>
        </el-form-item>
        <el-form-item label="Agent 编码">
          <el-input v-model="recordForm.agentCode" placeholder="可空；便于事后按 Agent 查" />
        </el-form-item>
        <el-form-item label="result.json" prop="resultJson">
          <el-input
            v-model="recordForm.resultJson"
            type="textarea"
            :rows="10"
            placeholder='{"jobId":"...","image":"...","exitCode":0,"timedOut":false,"network":"none",...}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recordVisible = false">取消</el-button>
        <el-button type="primary" :loading="recordSubmitting" @click="submitRecord">提交登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { getAgentVersion, listAgent, listAgentVersion, advanceRelease, missingGates } from '@/api/aigov/agent';
import type { AigAgentQuery, AigAgentVO, AigAgentVersionVO, AigReleaseAdvanceForm } from '@/api/aigov/agent/types';
import { sandboxRunEvidence, recordSandboxRun } from '@/api/aigov/sandbox';
import type { AigSandboxRunEvidence, AigSandboxRunRecordForm } from '@/api/aigov/sandbox/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';

defineOptions({ name: 'AigAgentRegistry' });

/** Agent 类别（与后端 AigAgentCategoryEnum 一致） */
const categoryOptions = [
  { value: 'PLANNING', label: '详情页策划' },
  { value: 'VISUAL_DNA', label: '视觉 DNA' },
  { value: 'GENERATION', label: '生成任务构建' },
  { value: 'QA', label: '视觉 QA' }
];

/** 五道门槛（与后端 AigReleaseGateEnum 一致） */
const gateOptions = [
  { value: 'MANIFEST_VALIDATION', label: 'Manifest 校验' },
  { value: 'SANDBOX_RUN', label: '沙箱运行' },
  { value: 'GOLDEN_CASE', label: '黄金用例' },
  { value: 'HUMAN_APPROVAL', label: '人工批准' },
  { value: 'CANARY', label: '灰度达标' }
];

const agentList = ref<AigAgentVO[]>([]);
const versionList = ref<AigAgentVersionVO[]>([]);
const currentAgent = ref<AigAgentVO>();
const { loading, withLoading } = useLoading(true);
const { loading: versionLoading, withLoading: withVersionLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const versionVisible = ref(false);
const advanceVisible = ref(false);
const missingGateText = ref('');
const advanceFormRef = ref<ElFormInstance>();
const sandboxEvidence = ref<AigSandboxRunEvidence>();
const recordVisible = ref(false);
const recordSubmitting = ref(false);
const recordFormRef = ref<ElFormInstance>();

const recordForm = ref<AigSandboxRunRecordForm>({
  targetType: 'AGENT_VERSION',
  targetVersionId: '',
  jobId: '',
  agentCode: '',
  resultJson: ''
});

const queryParams = ref<AigAgentQuery>({
  pageNum: 1,
  pageSize: 10,
  agentCode: undefined,
  agentName: undefined,
  category: undefined,
  builtin: undefined,
  params: {}
});

const advanceForm = ref<AigReleaseAdvanceForm>({
  targetType: 'AGENT_VERSION',
  targetVersionId: '',
  expectedStatus: '',
  toStatus: '',
  passedGates: [],
  detail: ''
});

/** 目标状态候选：门槛驱动的下一步 + 运维动作（停用/归档） */
const nextStatusOptions = computed(() => {
  const current = advanceForm.value.expectedStatus;
  const map: Record<string, string[]> = {
    DRAFT: ['VALIDATED', 'DISABLED', 'ARCHIVED'],
    VALIDATED: ['SANDBOX_TESTED', 'DISABLED', 'ARCHIVED'],
    SANDBOX_TESTED: ['CANDIDATE', 'DISABLED', 'ARCHIVED'],
    CANDIDATE: ['STABLE', 'DISABLED', 'ARCHIVED'],
    STABLE: ['DISABLED', 'ARCHIVED'],
    DISABLED: ['STABLE', 'ARCHIVED'],
    ARCHIVED: []
  };
  return map[current] || [];
});

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 类别展示名 */
const categoryLabel = (value?: string) => categoryOptions.find((item) => item.value === value)?.label || value || '-';

/** 状态标签颜色：终态/停用给不同色，避免"看起来都一样" */
const statusTagType = (status?: string): 'primary' | 'success' | 'info' | 'warning' | 'danger' | undefined => {
  if (status === 'STABLE') return 'success';
  if (status === 'DISABLED' || status === 'ARCHIVED') return 'info';
  if (status === 'CANDIDATE') return 'warning';
  return undefined;
};

const isTerminal = (status?: string) => status === 'ARCHIVED';

/** 查询 Agent 清单 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listAgent(queryParams.value);
    agentList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 搜索 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 打开版本列表 */
const openVersions = async (row: AigAgentVO) => {
  currentAgent.value = row;
  versionVisible.value = true;
  await withVersionLoading(async () => {
    const res = await listAgentVersion({ pageNum: 1, pageSize: 100, agentId: row.agentId });
    versionList.value = res.data?.rows || [];
  });
};

/** 打开推进对话框（expectedStatus 用当前状态，服务层会比对） */
const openAdvance = async (row: AigAgentVersionVO) => {
  const res = await getAgentVersion(row.agentVersionId as string | number);
  advanceForm.value = {
    targetType: 'AGENT_VERSION',
    targetVersionId: row.agentVersionId as string | number,
    expectedStatus: (res.data?.releaseStatus || row.releaseStatus) as string,
    toStatus: '',
    passedGates: [],
    detail: ''
  };
  missingGateText.value = '';
  advanceVisible.value = true;
  // 打开就查一次沙箱证据：管理员在这里最需要知道的就是"为什么还推不动"
  await loadSandboxEvidence();
};

/**
 * 查当前版本的沙箱运行证据（门槛 SANDBOX_RUN）。
 *
 * 后端对"没有证据"也是 200 + satisfied:false + reason，所以这里不做错误分支，
 * 只把结论与原因如实显示——把失败当"没跑过"来猜会掩盖真正的故障。
 */
const loadSandboxEvidence = async () => {
  if (!advanceForm.value.targetVersionId) {
    sandboxEvidence.value = undefined;
    return;
  }
  try {
    const res = await sandboxRunEvidence(advanceForm.value.targetType, advanceForm.value.targetVersionId);
    sandboxEvidence.value = res.data;
  } catch {
    sandboxEvidence.value = undefined;
  }
};

/** 打开登记对话框（对象沿用当前版本，避免登记到别的版本上） */
const openRecord = () => {
  recordForm.value = {
    targetType: advanceForm.value.targetType,
    targetVersionId: advanceForm.value.targetVersionId,
    jobId: '',
    agentCode: '',
    resultJson: ''
  };
  recordVisible.value = true;
};

/** 提交登记：把执行器输出的 result.json 原文交上去 */
const submitRecord = async () => {
  if (!recordForm.value.jobId) {
    modal.msgError('请填作业ID（与 result.json 里的 jobId 一致）');
    return;
  }
  if (!recordForm.value.resultJson) {
    modal.msgError('请粘贴 result.json 原文');
    return;
  }
  await modal.confirm('确认登记这条沙箱运行？一个作业只能登记一次，登记后不能修改。');
  recordSubmitting.value = true;
  try {
    await recordSandboxRun(recordForm.value);
    modal.msgSuccess('已登记');
    recordVisible.value = false;
    await loadSandboxEvidence();
  } finally {
    recordSubmitting.value = false;
  }
};

/** 查「还差哪些门槛」 */
const handleMissingGates = async () => {
  const res = await missingGates(advanceForm.value.targetType, advanceForm.value.targetVersionId, advanceForm.value.passedGates);
  const codes: string[] = res.data || [];
  missingGateText.value = codes.length
    ? '还差：' + codes.map((code) => gateOptions.find((g) => g.value === code)?.label || code).join('、')
    : '当前状态下不需要再补门槛（或当前状态不接受门槛推进）。';
};

/** 提交推进 */
const submitAdvance = async () => {
  if (!advanceForm.value.toStatus) {
    modal.msgError('请选择目标状态');
    return;
  }
  await modal.confirm('确认把该版本推进到「' + advanceForm.value.toStatus + '」？服务层会核对门槛证据。');
  const res = await advanceRelease(advanceForm.value);
  modal.msgSuccess('已推进到 ' + res.data);
  advanceVisible.value = false;
  await openVersions(currentAgent.value as AigAgentVO);
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.gate-hint {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
