<template>
  <div class="cap-detail">
    <!-- 顶部条：返回 + 身份 + 危险/发布动作 -->
    <div class="cap-detail__bar">
      <el-button link @click="emit('back')">← 返回清单</el-button>
      <span class="cap-detail__name">{{ capability?.capabilityName || capabilityCode }}</span>
      <el-tag v-if="capability" size="small" :type="statusMeta(capability.status).type">
        {{ statusMeta(capability.status).label }}
      </el-tag>
      <span class="cap-detail__spacer" />
      <el-button size="small" @click="rawVisible = !rawVisible">&lt;/&gt; 原始与日志</el-button>
      <el-button size="small" @click="emit('edit', capability)">编辑</el-button>
      <el-button
        size="small"
        type="primary"
        @click="
          modal.msgWarning('高风险变更需要批准（P1 落地）：外发放开、等级≥限制的改动会先走批准，再生效。')
        "
      >
        发布
      </el-button>
    </div>

    <!-- 状态头图：一眼看链路是否通 -->
    <div class="cap-detail__hero" :class="`cap-detail__hero--${heroTone}`">
      <div class="cap-detail__hero-main">
        <div class="cap-detail__hero-title">{{ issueTags(overview?.issueCodes).map((tag) => tag.label).join(' · ') }}</div>
        <div class="cap-detail__hero-hint">{{ riskHint(overview?.issueCodes) }}</div>
      </div>
      <div class="cap-detail__hero-stats">
        <div><span class="k">模型候选</span><b>{{ overview?.candidateCount ?? 0 }}</b></div>
        <div><span class="k">策略覆盖</span><b>{{ coverageText(overview?.policyCoveredLevels, overview?.policyTotalLevels) }}</b></div>
        <div><span class="k">近 24h 调用</span><b>{{ overview?.calls24h ?? 0 }}</b></div>
        <div><span class="k">失败</span><b>{{ overview?.fails24h ?? 0 }}</b></div>
      </div>
    </div>

    <div class="cap-detail__body">
      <div class="cap-detail__left">
        <!-- 渐进披露：默认只看给人读的描述 -->
        <div class="cap-detail__expander" @click="expanded = !expanded">
          <span>{{ expanded ? '▾' : '▸' }}</span> 展开配置详情
          <span class="cap-detail__expander-hint">（基本信息 · 模型候选 · 策略 · 场景指定 · 最近调用）</span>
        </div>

        <el-tabs v-if="expanded" v-model="activeTab" class="cap-detail__tabs">
          <el-tab-pane label="基本信息" name="basic">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="能力编码">{{ capability?.capabilityCode }}</el-descriptions-item>
              <el-descriptions-item label="能力标签">{{ parseTags(capability?.requiredTags).join(' / ') || '未声明' }}</el-descriptions-item>
              <el-descriptions-item label="最低数据等级">
                {{ minDataLevelLabel(capability?.minDataLevel) }}
                <span v-if="candidateChangeNeedsHighRisk(capability?.minDataLevel)" class="cap-detail__warn">
                  （该能力的候选变更属高风险：需要「能力高风险变更」权限）
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="数据策略">
                {{ capability?.dataPolicyLabel || capability?.dataPolicy || '未设置' }}
                <span class="cap-detail__warn">（登记项，判定以外发策略为准）</span>
              </el-descriptions-item>
              <el-descriptions-item label="审计等级">{{ capability?.auditLevelLabel || capability?.auditLevel || '未设置' }}</el-descriptions-item>
              <el-descriptions-item label="人工确认点">{{ capability?.humanConfirmPoints || '无' }}</el-descriptions-item>
              <el-descriptions-item label="质量阈值">{{ capability?.qualityThreshold || '未声明' }}</el-descriptions-item>
              <el-descriptions-item label="业务目标" :span="2">{{ capability?.bizGoal || '未填写' }}</el-descriptions-item>
            </el-descriptions>
            <div class="cap-detail__note">
              输入/输出 Schema 与其它高级字段走「编辑」弹窗的高级配置：默认不摊在详情里，
              避免一屏原始 JSON。
            </div>

            <!-- Q6 影响面：改策略/换候选/停用/删除之前先看清"谁在用它" -->
            <div class="cap-detail__refs">
              <div class="cap-detail__refs-head">
                <b>影响面（谁在用它）</b>
                <span class="cap-detail__sub">{{ referenceSummary(references) }}</span>
                <el-button link size="small" type="primary" @click="loadReferences">
                  刷新
                </el-button>
              </div>
              <el-table v-if="(references || []).length" :data="references" border size="small">
                <el-table-column label="类型" width="120">
                  <template #default="{ row }">
                    <el-tag size="small" :type="referenceTypeMeta(row.refType).type">
                      {{ referenceTypeMeta(row.refType).label }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="refLabel" label="引用" min-width="280" />
                <el-table-column prop="detail" label="说明" min-width="180" />
              </el-table>
              <div v-else class="cap-detail__sub">
                没有任何 Agent 版本 / 历史任务 / 训练台草稿引用它。
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="模型候选" name="candidates">
            <el-table v-if="(candidates || []).length" :data="candidates" border size="small">
              <el-table-column label="模型" min-width="200">
                <template #default="{ row }">
                  <span class="cap-detail__mono">{{ row.modelKey || row.modelId }}</span>
                  <div class="cap-detail__sub">{{ row.modelName || '' }}</div>
                </template>
              </el-table-column>
              <el-table-column label="用途" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="usageMeta(row.usageType).type">{{ usageMeta(row.usageType).label }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="priority" label="优先级" width="90" />
              <el-table-column label="部署" width="130">
                <template #default="{ row }">{{ row.deploymentTypeLabel || row.deploymentType || '—' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button link size="small" type="primary" @click="openCandidate(row)">编辑</el-button>
                  <el-button link size="small" type="danger" @click="removeCandidate(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="这个能力还没有模型候选：没有候选 ⇒ 调用一定失败">
              <el-button size="small" type="primary" @click="modal.msgWarning('新增候选在 P1 随『模型候选』页一起做；当前可先到路由页配置或直接调用接口。')">
                + 添加候选
              </el-button>
            </el-empty>
          </el-tab-pane>

          <el-tab-pane label="策略" name="policies">
            <el-table v-if="(policies || []).length" :data="policies" border size="small">
              <el-table-column prop="dataLevel" label="数据等级" width="120" />
              <el-table-column label="允许外发" width="100">
                <template #default="{ row }">{{ row.allowExternal === 'Y' ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="调用前审批" width="110">
                <template #default="{ row }">{{ row.requireApproval === 'Y' ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="无模型时" width="120">
                <template #default="{ row }">{{ row.fallbackToManual === 'Y' ? '转人工' : '失败' }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="160" />
            </el-table>
            <el-empty v-else description="没有任何数据等级策略：外发/审批/兜底都只能走默认口径">
              <el-button size="small" type="primary" @click="modal.msgWarning('策略编辑在「路由与策略」模块；P1 会收进本页签。')">
                去配策略
              </el-button>
            </el-empty>
            <div v-if="capability && capability.dataPolicy" class="cap-detail__note">
              提示：能力模板里的「数据策略」是登记项、不参与判定；真正决定外发的是这里的策略行。
            </div>
          </el-tab-pane>

          <el-tab-pane label="场景指定" name="scenes">
            <el-table v-if="(scenes || []).length" :data="scenes" border size="small">
              <el-table-column prop="scenarioCode" label="场景" width="160" />
              <el-table-column prop="providerId" label="指定供应商" width="160" />
              <el-table-column prop="priority" label="优先级" width="90" />
            </el-table>
            <el-empty v-else description="没有场景指定：该能力对所有场景一视同仁（不按场景收窄）" />
          </el-tab-pane>

          <el-tab-pane label="最近调用" name="audits">
            <el-table v-if="(audits || []).length" :data="audits" border size="small">
              <el-table-column prop="operateTime" label="时间" width="170" />
              <el-table-column prop="modelKey" label="命中模型" min-width="160" />
              <el-table-column label="外发" width="80">
                <template #default="{ row }">{{ row.externalCall === 'Y' ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="结果" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.result === '1' ? 'danger' : 'success'">
                    {{ row.result === '1' ? '失败' : '成功' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="errorSummary" label="说明" min-width="200" />
            </el-table>
            <el-empty v-else description="近 24h 没有调用记录" />
          </el-tab-pane>
        </el-tabs>

        <div class="cap-detail__persona">
          <h4>[角色]</h4>
          <p>{{ capability?.bizGoal || '（未填写业务目标；可在「编辑」里补充。这一段会作为人设摘要显示给使用者。）' }}</p>
          <h4>[人工确认点]</h4>
          <p>{{ capability?.humanConfirmPoints || '无（该能力不需要人工确认）' }}</p>
          <h4>[高级字段]</h4>
          <p>
            输入 Schema / 输出 Schema / 数据策略 / 审计等级 / 质量阈值：
            在「编辑」弹窗的<b>高级配置</b>里查看与修改（默认不展开，避免原始 JSON 糊一屏）。
          </p>
        </div>
      </div>

      <div class="cap-detail__right">
        <TrialAssistant
          v-model:collapsed="assistantCollapsed"
          :capability-code="capabilityCode"
          :capability-name="capability?.capabilityName"
          :overview="overview"
          @raw="rawContent = $event"
        />
      </div>
    </div>

    <RawConsole :visible="rawVisible" :title="`原始与日志 · ${capabilityCode}`" :content="rawContent" @close="rawVisible = false" />

    <el-dialog v-model="candidateVisible" title="编辑候选（用途 / 优先级）" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="模型">
          <span class="cap-detail__mono">{{ candidateForm.modelKey || candidateForm.modelId }}</span>
          <div class="cap-detail__sub">换模型请新增候选 + 删除旧候选：换模型等于换了一条候选的身份。</div>
        </el-form-item>
        <el-form-item label="用途">
          <el-select v-model="candidateForm.usageType" style="width: 100%">
            <el-option v-for="item in USAGE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="candidateForm.priority" :min="1" :max="999" />
          <span class="cap-detail__sub">数值越小越优先</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="candidateVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCandidate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { getCapability } from '@/api/aigov/capability';
import { capabilityOverview } from '@/api/aigov/capability/overview';
import { listCapabilityReferences } from '@/api/aigov/capability/references';
import type { AigCapabilityReferenceVO } from '@/api/aigov/capability/references';
import { deleteCandidate, listCandidates, updateCandidate } from '@/api/aigov/candidate';
import { listRoutePolicy, listScenarioBinding } from '@/api/aigov/route';
import { listInvocationAudit } from '@/api/aigov/audit';
import type { AigCapabilityOverviewVO } from '@/api/aigov/capability/overview-types';
import type { AigCapabilityVO } from '@/api/aigov/capability/types';
import modal from '@/plugins/modal';
import RawConsole from './RawConsole.vue';
import TrialAssistant from './TrialAssistant.vue';
import {
  USAGE_OPTIONS,
  candidateChangeNeedsHighRisk,
  coverageText,
  issueTags,
  minDataLevelLabel,
  parseTags,
  referenceSummary,
  referenceTypeMeta,
  riskHint,
  statusMeta,
  usageMeta
} from '../presentation';

/**
 * 能力详情整页（用户已确认此交互形态：整页 + 「展开配置详情」）。
 *
 * <p><b>为什么是整页而不是弹窗/抽屉</b>：这一屏要同时装下"档案 + 五个配置页签 + 右侧试调助手"，
 * 弹窗会把它压成一个表单，抽屉装不下助手。而默认只显示人设摘要、配置藏在「展开配置详情」后面
 * （渐进披露），所以整页也不会一上来就是一屏字段。</p>
 */
const props = defineProps<{
  capabilityId: string | number;
  capabilityCode: string;
}>();

const emit = defineEmits<{
  (event: 'back'): void;
  (event: 'edit', capability?: AigCapabilityVO): void;
}>();

const capability = ref<AigCapabilityVO>();
const overview = ref<AigCapabilityOverviewVO>();
const candidates = ref<any[]>([]);
const policies = ref<any[]>([]);
const scenes = ref<any[]>([]);
const audits = ref<any[]>([]);
/** 影响面（Q6）：Agent 版本 / 历史任务 / 训练台草稿 */
const references = ref<AigCapabilityReferenceVO[]>([]);
const expanded = ref(false);
const activeTab = ref('basic');
const rawVisible = ref(false);
const rawContent = ref('');
const assistantCollapsed = ref(false);

const candidateVisible = ref(false);
const saving = ref(false);
const candidateForm = ref<any>({});

const heroTone = computed(() => {
  const codes = overview.value?.issueCodes || [];
  if (codes.some((code) => code === 'NO_MODEL_CANDIDATE' || code === 'NO_ROUTE_POLICY')) {
    return 'danger';
  }
  if (codes.length > 0) {
    return 'warning';
  }
  return 'ok';
});

const loadOverview = async () => {
  try {
    const res = await capabilityOverview({ capabilityCode: props.capabilityCode });
    overview.value = (res.data || [])[0];
  } catch {
    // 概览失败不阻断详情：头图退化为"未取到完整度"，其它页签照常可看
    overview.value = undefined;
  }
};

const loadDetail = async () => {
  try {
    const res = await getCapability(props.capabilityId);
    capability.value = res.data;
  } catch (error: any) {
    modal.alertError(error?.message || '读取能力详情失败');
  }
};

const loadCandidates = async () => {
  try {
    const res: any = await listCandidates(props.capabilityCode);
    candidates.value = res.data?.rows || res.data || [];
  } catch {
    candidates.value = [];
  }
};

const loadPolicies = async () => {
  try {
    const res: any = await listRoutePolicy({ capabilityCode: props.capabilityCode, pageNum: 1, pageSize: 50 } as any);
    policies.value = res.data?.rows || res.data || [];
  } catch {
    policies.value = [];
  }
};

const loadScenes = async () => {
  try {
    const res: any = await listScenarioBinding({ capabilityCode: props.capabilityCode, pageNum: 1, pageSize: 50 } as any);
    scenes.value = res.data?.rows || res.data || [];
  } catch {
    scenes.value = [];
  }
};

const loadAudits = async () => {
  try {
    const res: any = await listInvocationAudit({ capabilityCode: props.capabilityCode, pageNum: 1, pageSize: 20 } as any);
    audits.value = res.data?.rows || res.data || [];
  } catch {
    audits.value = [];
  }
};

/** 影响面：删除/停用/改策略前必须先看清引用方（Q6） */
const loadReferences = async () => {
  try {
    const res: any = await listCapabilityReferences(props.capabilityCode);
    references.value = res.data || [];
  } catch {
    references.value = [];
  }
};

const loadAll = async () => {
  await Promise.all([
    loadOverview(),
    loadDetail(),
    loadCandidates(),
    loadPolicies(),
    loadScenes(),
    loadAudits(),
    loadReferences()
  ]);
};

const openCandidate = (row: any) => {
  candidateForm.value = { ...row };
  candidateVisible.value = true;
};

/** 该能力（按声明等级）的候选变更属高风险时，先把话说清（Q1C/§8.1 第 4 条） */
const confirmCandidateHighRisk = async (action: string) => {
  if (!candidateChangeNeedsHighRisk(capability.value?.minDataLevel)) {
    return true;
  }
  try {
    await modal.confirm(
      `这是一次高风险变更：${action}限制级/严格级能力「${props.capabilityName || props.capabilityCode}」的模型候选`
        + `（声明的数据等级=${capability.value?.minDataLevel}），它会直接改变这类数据能落到哪个模型。`
        + `需要「能力高风险变更」权限点（aig:capability:high-risk）；没有该权限时保存会被服务端拒绝。`
    );
    return true;
  } catch {
    return false;
  }
};

const saveCandidate = async () => {
  if (!candidateForm.value?.bindId) {
    return;
  }
  if (!(await confirmCandidateHighRisk('修改'))) {
    return;
  }
  saving.value = true;
  try {
    await updateCandidate(candidateForm.value.bindId, {
      usageType: candidateForm.value.usageType,
      priority: candidateForm.value.priority
    });
    modal.msgSuccess('候选已更新（用途/优先级会立即影响路由排序）');
    candidateVisible.value = false;
    await Promise.all([loadCandidates(), loadOverview()]);
  } catch (error: any) {
    modal.alertError(error?.message || '保存候选失败');
  } finally {
    saving.value = false;
  }
};

const removeCandidate = async (row: any) => {
  if (!(await confirmCandidateHighRisk('删除'))) {
    return;
  }
  try {
    await modal.confirm(
      `删除这条候选？删除后该模型不再参与「${props.capabilityCode}」的路由；若这是唯一候选，调用会立即失败。`
    );
  } catch {
    return;
  }
  try {
    await deleteCandidate(row.bindId);
    modal.msgSuccess('候选已删除');
    await Promise.all([loadCandidates(), loadOverview()]);
  } catch (error: any) {
    modal.alertError(error?.message || '删除候选失败');
  }
};

onMounted(loadAll);
watch(() => props.capabilityId, loadAll);
</script>

<style scoped>
.cap-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cap-detail__bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cap-detail__name {
  font-weight: 600;
}

.cap-detail__spacer {
  flex: 1;
}

.cap-detail__hero {
  border-radius: 12px;
  padding: 14px 16px;
  color: #fff;
  display: flex;
  gap: 16px;
  align-items: center;
  background: linear-gradient(135deg, #2b63f0, #4c8dff);
}

.cap-detail__hero--ok {
  background: linear-gradient(135deg, #0f9d58, #35be7b);
}

.cap-detail__hero--warning {
  background: linear-gradient(135deg, #e08700, #ffae33);
}

.cap-detail__hero--danger {
  background: linear-gradient(135deg, #d93025, #ef6b5f);
}

.cap-detail__hero-main {
  flex: 1;
}

.cap-detail__hero-title {
  font-size: 16px;
  font-weight: 700;
}

.cap-detail__hero-hint {
  font-size: 12px;
  opacity: 0.92;
  margin-top: 4px;
}

.cap-detail__hero-stats {
  display: flex;
  gap: 18px;
}

.cap-detail__hero-stats .k {
  display: block;
  font-size: 11px;
  opacity: 0.9;
}

.cap-detail__hero-stats b {
  font-size: 18px;
}

.cap-detail__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 14px;
  align-items: start;
}

.cap-detail__expander {
  cursor: pointer;
  font-size: 13px;
  color: var(--el-color-primary);
}

.cap-detail__expander-hint {
  color: var(--el-text-color-secondary);
}

.cap-detail__tabs {
  margin-top: 8px;
}

.cap-detail__persona h4 {
  margin: 14px 0 6px;
  font-size: 14px;
}

.cap-detail__persona p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.cap-detail__note {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 8px 10px;
}

.cap-detail__warn {
  color: var(--el-color-warning);
  font-size: 12px;
}

.cap-detail__mono {
  font-family: ui-monospace, Consolas, monospace;
}

.cap-detail__sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1280px) {
  .cap-detail__body {
    grid-template-columns: 1fr;
  }
}
</style>
