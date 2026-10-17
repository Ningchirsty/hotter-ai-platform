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
        <template #empty>
          <AigEmptyState
            icon="Connection"
            title="还没有 Agent"
            hint="Agent 通常从训练台（草稿→提交→发布）产生；这里只做检索与版本/绑定管理。"
          >
            <el-button size="small" @click="getList">刷新</el-button>
          </AigEmptyState>
        </template>
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
        推进时服务层会核对门槛证据：Manifest 校验看 scan_result、沙箱运行看沙箱账本、黄金用例看评测账本、
        灰度达标看调用统计；<b>「三方人工批准」是唯一例外——它是声明式的，平台不核对批准记录</b>。
      </el-alert>
      <el-table v-loading="versionLoading" border :data="versionList">
        <template #empty>
          <AigEmptyState
            icon="Files"
            title="这个 Agent 还没有版本"
            hint="版本从训练台提交产生；没有版本就无法被绑定/发布，也不能被工具知识绑定引用。"
          >
            <el-button size="small" @click="openVersions(currentAgent)">刷新</el-button>
          </AigEmptyState>
        </template>
        <el-table-column label="版本" align="center" prop="version" width="90" />
        <el-table-column label="发布状态" align="center" width="130">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.releaseStatus)">{{ releaseStatusLabel(scope.row.releaseStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="通道" align="center" prop="releaseChannel" width="100" />
        <el-table-column label="外部调用" align="center" width="100">
          <template #default="scope">{{ scope.row.allowExternal === 'Y' ? '允许' : '禁止' }}</template>
        </el-table-column>
        <el-table-column label="来源Package版本" align="center" width="170">
          <template #default="{ row }"><AigLongId :id="row.packageVersionId" :keep="6" /></template>
        </el-table-column>
        <el-table-column label="最近评测" align="center" width="150">
          <template #default="{ row }"><AigLongId :id="row.evaluationRunId" :keep="6" /></template>
        </el-table-column>
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
            服务层不会只看这里勾了什么。<b>四道门槛会去库里核对证据</b>：Manifest 校验看扫描结论、
            沙箱运行看沙箱账本、黄金用例看评测账本、灰度达标看调用统计——没有证据的声明一律拒绝。
            <br />
            <b>例外：「三方人工批准」是声明式的</b>——平台<b>不核对</b>批准记录（本仓没有"谁批的"这张表，
            且已冻「不新造审批表」；调用授权表是「人×能力×数据等级」，与"某版本该不该发布"不是一回事）。
            勾它就等于声明已线下完成三方批准（业务 Owner / AI 管理员 / 平台管理员）；
            平台会把这句话记进发布事件与版本行（approved_by / approved_at）作为留痕，
            但<b>不把它当成已验证的事实</b>。
            <br />
            缺哪些门槛可点下面的「查还差什么」。
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
              沙箱产物（ADR-016）：产物字节由运维导出后经这里交上来，平台不读宿主归档目录。
              清单以 result.json **声明的产物**为准（未入库的也会出现）——只列已入库的话，
              漏传的那个恰恰不会出现，而"都收齐了吗"正是这里唯一要回答的问题。
              下载一律按附件处理（响应头由后端保证），界面刻意不提供预览：产物是不可信内容。
            -->
            <div v-if="sandboxEvidence && sandboxEvidence.runId" class="artifact-block">
              <div class="artifact-head">
                <span>{{ artifactSummary(sandboxArtifacts) }}</span>
                <el-button link icon="Refresh" :loading="loadingArtifacts" @click="loadSandboxArtifacts">
                  刷新产物
                </el-button>
              </div>
              <el-table v-loading="loadingArtifacts" :data="sandboxArtifacts" size="small" border>
                <template #empty>
                  <AigEmptyState
                    icon="Folder"
                    title="这个沙箱运行还没有产物"
                    hint="产物由沙箱作业写出；没有说明这次运行还没产出文件，或产物已被清理。"
                  >
                    <el-button size="small" @click="loadSandboxArtifacts">刷新</el-button>
                  </AigEmptyState>
                </template>
                <el-table-column label="路径" prop="path" min-width="200" show-overflow-tooltip />
                <el-table-column label="字节" prop="bytes" align="center" width="100" />
                <el-table-column label="SHA-256" align="center" min-width="130" show-overflow-tooltip>
                  <template #default="{ row }">{{ shortSha(row.sha256) }}</template>
                </el-table-column>
                <el-table-column label="入库" align="center" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.uploaded ? 'success' : 'info'" size="small">
                      {{ row.uploaded ? '已入库' : '未入库' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" align="center" width="160">
                  <template #default="{ row }">
                    <el-upload
                      v-hasPermi="['aig:sandbox:record']"
                      :show-file-list="false"
                      :http-request="(option: any) => doUploadArtifact(row, option)"
                    >
                      <el-button
                        link
                        size="small"
                        type="primary"
                        :disabled="row.uploaded === true || uploadingPath === row.path"
                        :loading="uploadingPath === row.path"
                      >
                        上传
                      </el-button>
                    </el-upload>
                    <el-button
                      link
                      size="small"
                      type="primary"
                      :disabled="row.uploaded !== true"
                      @click="doDownloadArtifact(row)"
                    >
                      下载
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="gate-hint">
                上传时服务端按 result.json 声明的字节数与 SHA-256 逐个校验：不符即拒绝，不落对象存储。
                产物字节与发布门槛无关——门槛只看沙箱运行证据（上面那块）。
              </div>

              <!--
                下载留痕（ADR-016 安全硬要求 ⑤）：产物是不可信内容，"谁在何时从哪个 IP 取走了哪一份"
                是安全事件的第一个问题——所以它单独记账，而不是只写应用日志（日志会轮转、会随容器重建丢掉）。
                按需展开：它是审计面，不是每次打开都要拉的东西。
              -->
              <div class="download-block">
                <div class="artifact-head">
                  <el-button size="small" text type="primary" @click="toggleDownloads">
                    {{ downloadsOpen ? '收起下载记录' : '下载记录' }}
                  </el-button>
                  <span class="gate-hint">{{ downloadSummary(artifactDownloads) }}</span>
                  <el-button
                    v-if="downloadsOpen"
                    link
                    size="small"
                    icon="Refresh"
                    :loading="downloadsLoading"
                    @click="loadArtifactDownloads"
                  >
                    刷新
                  </el-button>
                </div>
                <el-table
                  v-if="downloadsOpen && artifactDownloads.length"
                  v-loading="downloadsLoading"
                  :data="artifactDownloads"
                  size="small"
                  border
                >
                  <el-table-column label="路径" prop="path" min-width="180" show-overflow-tooltip />
                  <el-table-column label="下载人" align="center" width="110">
                    <template #default="{ row }">{{ downloaderLabel(row) }}</template>
                  </el-table-column>
                  <el-table-column label="来源 IP" align="center" width="140">
                    <template #default="{ row }">{{ row.clientIp || '—' }}</template>
                  </el-table-column>
                  <el-table-column label="下发摘要" align="center" min-width="120" show-overflow-tooltip>
                    <template #default="{ row }">{{ shortSha(row.sha256) }}</template>
                  </el-table-column>
                  <el-table-column label="时间" prop="createTime" align="center" width="170" show-overflow-tooltip />
                </el-table>
              </div>
            </div>
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
import { sandboxRunEvidence, recordSandboxRun, listSandboxArtifacts, uploadSandboxArtifact, downloadSandboxArtifact, listArtifactDownloads } from '@/api/aigov/sandbox';
import type {
  AigSandboxArtifactDownloadVO,
  AigSandboxArtifactManifestVO,
  AigSandboxRunEvidence,
  AigSandboxRunRecordForm
} from '@/api/aigov/sandbox/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import AigEmptyState from '../components/AigEmptyState.vue';
import AigLongId from '../components/AigLongId.vue';
import { saveBlob } from '@/utils/save';
import { artifactSummary, downloadSummary, downloaderLabel, fileNameOf, shortSha } from './sandboxArtifact';

defineOptions({ name: 'AigAgentRegistry' });

/** Agent 类别（与后端 AigAgentCategoryEnum 一致） */
const categoryOptions = [
  { value: 'PLANNING', label: '详情页策划' },
  { value: 'VISUAL_DNA', label: '视觉 DNA' },
  { value: 'GENERATION', label: '生成任务构建' },
  { value: 'QA', label: '视觉 QA' },
  { value: 'ANALYSIS', label: '行业分析（非创作类）' }
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
/**
 * 沙箱产物清单（ADR-016）：以 result.json 声明的产物为准，未入库的也会出现。
 * 证据没有 runId（没跑过）时它为空——那时"没有产物"是结论，不是加载失败。
 */
const sandboxArtifacts = ref<AigSandboxArtifactManifestVO[]>([]);
const loadingArtifacts = ref(false);
/** 正在上传的产物路径（按行禁用按钮，避免同一行连点） */
const uploadingPath = ref('');

/** 下载留痕（按需展开才拉：它是审计面，不该在每次打开推进弹窗时都请求） */
const artifactDownloads = ref<AigSandboxArtifactDownloadVO[]>([]);
const downloadsLoading = ref(false);
const downloadsOpen = ref(false);
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

/**
 * 发布状态**人话**（走读 §5.5：不再直出 DRAFT/VALIDATED 这类裸枚举）。
 *
 * <p>取值与后端 {@code AigReleaseStatusEnum} 一一对应；认不出的原样回显
 * （宁可显示原始码，也不要把不认识的取值藏起来）。</p>
 */
const releaseStatusLabel = (status?: string): string => {
  switch (status) {
    case 'DRAFT':
      return '草稿';
    case 'VALIDATED':
      return '已校验';
    case 'SANDBOX_TESTED':
      return '沙箱已跑通';
    case 'CANDIDATE':
      return '候选（灰度）';
    case 'STABLE':
      return '稳定（已发布）';
    case 'DISABLED':
      return '已停用';
    case 'ARCHIVED':
      return '已归档';
    default:
      return status || '未知';
  }
};

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
  await loadSandboxArtifacts();
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

/**
 * 读产物清单（以 result.json 声明的产物为准）。
 *
 * 证据没有 runId 时清空并直接返回：没跑过就没有产物清单，这不是加载失败。
 */
const loadSandboxArtifacts = async () => {
  const runId = sandboxEvidence.value?.runId;
  if (!runId) {
    sandboxArtifacts.value = [];
    return;
  }
  loadingArtifacts.value = true;
  try {
    const res = await listSandboxArtifacts(runId);
    // 取数期间换了版本/关了对话框：丢弃这次结果，避免把上一条运行的产物显示成本条的
    if (sandboxEvidence.value?.runId !== runId) return;
    sandboxArtifacts.value = res.data || [];
  } catch (e: any) {
    sandboxArtifacts.value = [];
    modal.alertError(e?.message || '读取沙箱产物清单失败');
  } finally {
    loadingArtifacts.value = false;
  }
};

/**
 * 上传一只产物的字节。
 *
 * 本地不做字节数/摘要校验：校验基准是账本里声明的清单，只有服务端拿得到，
 * 本地"顺手校验一下"会得到两套口径——而两套口径不一致时，人只会看到互相矛盾的提示。
 */
const doUploadArtifact = async (row: AigSandboxArtifactManifestVO, option: any) => {
  const runId = sandboxEvidence.value?.runId;
  if (!runId) {
    option?.onError?.(new Error('没有沙箱运行记录，无法上传产物'));
    return;
  }
  uploadingPath.value = row.path;
  try {
    await uploadSandboxArtifact(runId, row.path, option.file as File,
      (option.file as File)?.type || undefined);
    modal.msgSuccess('已登记：' + row.path);
    await loadSandboxArtifacts();
  } catch (e: any) {
    modal.alertError(e?.message || '产物登记失败');
    option?.onError?.(e);
  } finally {
    uploadingPath.value = '';
  }
};

/** 下载一只已入库的产物（按附件保存；产物是不可信内容，不做预览） */
const doDownloadArtifact = async (row: AigSandboxArtifactManifestVO) => {
  const runId = sandboxEvidence.value?.runId;
  if (!runId) return;
  try {
    const blob = await downloadSandboxArtifact(runId, row.path);
    saveBlob(blob, fileNameOf(row.path));
    // 留痕是这次下发的副产物：已经展开的话立刻刷出来，别让人以为没记上
    if (downloadsOpen.value) {
      await loadArtifactDownloads();
    }
  } catch (e: any) {
    modal.alertError(e?.message || '产物下载失败');
  }
};

/** 展开/收起下载留痕；展开时取一次 */
const toggleDownloads = async () => {
  downloadsOpen.value = !downloadsOpen.value;
  if (downloadsOpen.value) {
    await loadArtifactDownloads();
  }
};

/** 读下载留痕（谁/何时/哪个 IP/哪份摘要） */
const loadArtifactDownloads = async () => {
  const runId = sandboxEvidence.value?.runId;
  if (!runId) {
    artifactDownloads.value = [];
    return;
  }
  downloadsLoading.value = true;
  try {
    const res = await listArtifactDownloads(runId);
    // 取数期间换了版本/关了对话框：丢弃这次结果
    if (sandboxEvidence.value?.runId !== runId) return;
    artifactDownloads.value = res.data || [];
  } catch (e: any) {
    artifactDownloads.value = [];
    modal.alertError(e?.message || '读取下载记录失败');
  } finally {
    downloadsLoading.value = false;
  }
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
  await modal.confirm('确认把该版本推进到「' + advanceForm.value.toStatus
    + '」？服务层会核对**有证据的那几道**门槛；「三方人工批准」是声明式的，不会被核对。');
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

.artifact-block {
  margin: 8px 0;
}

.artifact-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
  font-size: 12.5px;
}

.download-block {
  margin-top: 8px;
}
</style>
