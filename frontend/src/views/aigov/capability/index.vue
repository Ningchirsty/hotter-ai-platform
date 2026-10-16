<template>
  <div class="p-2 app-container aigov-capability-page">
    <PageHeading
      title="能力"
      subtitle="每个能力是一张卡片：点开看档案与试调；链路是否完整一眼可见"
      admin
      module="aigov"
    />

    <!-- 详情（整页，模块内切换；不新增菜单行，避免再污染左侧导航） -->
    <CapabilityDetail
      v-if="detailCode"
      :capability-id="detailId!"
      :capability-code="detailCode"
      @back="closeDetail"
      @edit="openEdit"
    />

    <template v-else>
      <div class="cap-list__toolbar">
        <el-radio-group v-model="view" size="small">
          <el-radio-button value="list">卡片清单</el-radio-button>
          <el-radio-button value="board">看板</el-radio-button>
        </el-radio-group>
        <span class="cap-list__toolbar-hint">
          卡片＝给人看的（点开档案 + 试调）；看板＝给运营看的（指标 / 排行 / 表格）
        </span>
      </div>

      <!-- 筛选条：全部下发到服务端（否则筛选只看当前页，翻页后结论就错了） -->
      <el-card v-if="view === 'list'" shadow="never" class="cap-list__filter">
        <div class="cap-list__filter-row">
          <el-input
            v-model="query.capabilityName"
            placeholder="能力名称 / 编码"
            clearable
            style="width: 200px"
            @keyup.enter="load"
          />
          <el-select v-model="query.issueCode" style="width: 190px">
            <el-option v-for="item in issueFilterOptions()" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.candidateState" style="width: 180px">
            <el-option
              v-for="item in candidateStateOptions()"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-select v-model="query.status" style="width: 130px">
            <el-option
              v-for="dict in sys_normal_disable"
              :key="dict.value"
              :label="'状态：' + dict.label"
              :value="dict.value"
            />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="clearFilter">清除筛选</el-button>
          <span class="cap-list__count">显示 {{ rows.length }} 个能力（有问题的在前）</span>
        </div>
      </el-card>

      <el-alert
        v-if="view === 'list' && !loading && problemCount > 0"
        class="cap-list__alert"
        type="info"
        :closable="false"
        show-icon
        :title="`其中 ${problemCount} 个能力链路不完整（缺模型候选 / 候选健康异常 / 缺策略）：点卡片上的问题标签即可筛出它们。`"
      />

      <div v-if="view === 'list'" v-loading="loading" class="cap-list__grid">
        <!-- 创建卡（固定第一格） -->
        <el-card shadow="never" class="cap-list__create">
          <div class="cap-list__create-title">创建能力</div>
          <el-button class="cap-list__create-line" @click="openCreate">➕ 直接创建</el-button>
          <el-button class="cap-list__create-line" disabled>⬆️ 快速导入（P1）</el-button>
          <el-button class="cap-list__create-line" disabled>🗂 从模板创建（P1）</el-button>
          <div class="cap-list__create-hint">创建后到「模型候选」挂模型、到「策略」定外发，链路才算通。</div>
        </el-card>

        <!-- 能力卡片 -->
        <el-card v-for="row in rows" :key="row.capabilityCode" shadow="hover" class="cap-list__card">
          <div class="cap-list__card-top" @click="openDetail(row)">
            <div class="cap-list__icon" :class="iconClass(parseTags(row.requiredTags)[0])">
              {{ iconText(row.requiredTags) }}
            </div>
            <div class="cap-list__card-main">
              <div class="cap-list__card-name">
                {{ row.capabilityName || row.capabilityCode }}
                <el-tag v-for="tag in issueTags(row.issueCodes)" :key="tag.label" size="small" :type="tag.type">
                  {{ tag.label }}
                </el-tag>
              </div>
              <div class="cap-list__card-code">{{ row.capabilityCode }}</div>
            </div>
          </div>

          <div class="cap-list__card-desc" @click="openDetail(row)">{{ riskHint(row.issueCodes) }}</div>

          <div class="cap-list__card-tags">
            <el-tag v-for="tag in parseTags(row.requiredTags)" :key="tag" size="small" type="info">{{ tag }}</el-tag>
            <span v-if="parseTags(row.requiredTags).length === 0" class="cap-list__muted">未声明标签</span>
          </div>

          <div class="cap-list__card-foot" @click="openDetail(row)">
            <span>{{ cardSummary(row) }}</span>
            <el-tag size="small" :type="healthMeta(row.candidateHealthAgg).type">
              {{ healthMeta(row.candidateHealthAgg).label }}
            </el-tag>
          </div>

          <div class="cap-list__card-actions">
            <el-button link size="small" type="primary" @click="openDetail(row)">试调</el-button>
            <el-button link size="small" type="primary" @click="openEditById(row)">编辑基本信息</el-button>
            <el-button link size="small" type="danger" @click="remove(row)">删除</el-button>
          </div>
        </el-card>
      </div>

      <el-empty v-if="view === 'list' && !loading && rows.length === 0" description="没有符合条件的能力">
        <el-button type="primary" @click="openCreate">+ 创建能力</el-button>
        <el-button @click="clearFilter">清除筛选</el-button>
      </el-empty>

      <CapabilityBoard v-if="view === 'board'" :rows="rows" @open="openDetail" />
    </template>

    <!-- 新增 / 编辑：默认只填基本信息，高级字段收进「高级配置」 -->
    <el-dialog
      v-model="formVisible"
      :title="form.capabilityId ? '编辑能力' : '新建能力'"
      width="760px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="能力编码" prop="capabilityCode">
          <el-input
            v-model="form.capabilityCode"
            :disabled="!!form.capabilityId"
            placeholder="如 talent_match（对外稳定契约，创建后不可改）"
          />
        </el-form-item>
        <el-form-item label="能力名称" prop="capabilityName">
          <el-input v-model="form.capabilityName" maxlength="20" show-word-limit placeholder="如 人才匹配" />
        </el-form-item>
        <el-form-item label="需要的能力标签">
          <el-select v-model="tagSelection" multiple style="width: 100%" placeholder="决定哪些模型能服务这个能力">
            <el-option v-for="tag in TAG_OPTIONS" :key="tag" :label="tag" :value="tag" />
          </el-select>
          <div class="cap-list__hint">
            已下线的 RERANK 不再提供（生产上它已移除）——避免配出「永远没有候选」的能力。
          </div>
        </el-form-item>
        <el-form-item label="人设 / 业务目标" prop="bizGoal">
          <el-input
            v-model="form.bizGoal"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="一句话说清这个能力做什么、边界在哪（会进入系统提示）"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-divider content-position="left">
          <el-button link size="small" @click="advanced = !advanced">
            {{ advanced ? '收起高级配置' : '展开高级配置（Schema / 数据策略 / 审计等级…）' }}
          </el-button>
        </el-divider>

        <template v-if="advanced">
          <el-form-item label="输入 Schema">
            <el-input v-model="form.inputSchema" type="textarea" :rows="3" placeholder='{"type":"object"}' />
          </el-form-item>
          <el-form-item label="输出 Schema">
            <el-input v-model="form.outputSchema" type="textarea" :rows="3" placeholder='{"type":"object"}' />
          </el-form-item>
          <el-form-item label="数据策略">
            <el-select v-model="form.dataPolicy" style="width: 100%">
              <el-option
                v-for="dict in aig_data_policy"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
            <div class="cap-list__hint">
              这是登记项，<b>不参与路由判定</b>；真正决定外发的是「策略」（数据等级 × 允许外发）。
            </div>
          </el-form-item>
          <el-form-item label="审计等级">
            <el-select v-model="form.auditLevel" style="width: 100%">
              <el-option
                v-for="dict in aig_audit_level"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="人工确认点">
            <el-input v-model="form.humanConfirmPoints" placeholder="如：对外发送前" />
          </el-form-item>
          <el-form-item label="质量阈值">
            <el-input v-model="form.qualityThreshold" placeholder="如：完整率>=0.9；超时=60s" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, toRefs } from 'vue';
import { addCapability, delCapability, getCapability, updateCapability } from '@/api/aigov/capability';
import { capabilityOverview } from '@/api/aigov/capability/overview';
import { listCapabilityReferences } from '@/api/aigov/capability/references';
import type { AigCapabilityReferenceVO } from '@/api/aigov/capability/references';
import type { AigCapabilityOverviewQuery, AigCapabilityOverviewVO } from '@/api/aigov/capability/overview-types';
import type { AigCapabilityForm, AigCapabilityVO } from '@/api/aigov/capability/types';
import CapabilityBoard from './components/CapabilityBoard.vue';
import CapabilityDetail from './components/CapabilityDetail.vue';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import {
  candidateStateOptions,
  cardSummary,
  deleteConfirmText,
  disableConfirmText,
  healthMeta,
  issueFilterOptions,
  issueTags,
  parseTags,
  riskHint,
  validateJsonText
} from './presentation';

defineOptions({ name: 'AigCapability' });

/**
 * 能力清单（卡片视图）。
 *
 * <h3>为什么从表格改成卡片</h3>
 * <p>一屏卡片能同时回答「这个能力是什么、链路通不通、最近有没有被调用」，而十几列宽表只能回答
 * 「有哪些字段」。链路完整度（候选/策略/健康）用卡片角标 + 服务端筛选直接暴露 ——
 * 这正是治好「配了以为生效」最需要的那个信号。</p>
 *
 * <h3>两个刻意的边界</h3>
 * <ul>
 *     <li><b>详情不新增菜单行</b>：从卡片进入、返回即回清单。20 项平铺的导航本身就是本次要治的问题；</li>
 *     <li><b>高级字段默认收起</b>：Schema / 数据策略 / 审计等级收进「展开高级配置」，
 *         默认只让人填编码、名称、标签、人设 —— 少一屏原始 JSON。</li>
 * </ul>
 */
const TAG_OPTIONS = ['TEXT', 'VISION', 'OCR', 'IMAGE', 'VIDEO', 'EMBEDDING', 'DECISION', 'AGENT'];

const { aig_data_policy, aig_audit_level, sys_normal_disable } = toRefs<any>(
  useDict('aig_data_policy', 'aig_audit_level', 'sys_normal_disable')
);

const query = reactive<AigCapabilityOverviewQuery>({
  capabilityName: '',
  issueCode: '',
  candidateState: '',
  status: ''
});
const rows = ref<AigCapabilityOverviewVO[]>([]);
const loading = ref(false);
/** 清单 / 看板：同一份数据，两种看法（不新增页面与菜单行） */
const view = ref<'list' | 'board'>('list');

const detailId = ref<string | number>();
const detailCode = ref<string>();

const formVisible = ref(false);
const saving = ref(false);
/** 编辑弹窗打开时的原状态（Q1C 只把「启用 → 停用」当高风险迁移） */
const originalStatus = ref('0');
const advanced = ref(false);
const formRef = ref();
const form = reactive<AigCapabilityForm>({ status: '0' });
const tagSelection = ref<string[]>([]);

const rules = {
  capabilityCode: [{ required: true, message: '能力编码不能为空', trigger: 'blur' }],
  capabilityName: [{ required: true, message: '能力名称不能为空', trigger: 'blur' }]
};

const problemCount = computed(() => rows.value.filter((row) => (row.issueCodes || []).length > 0).length);

const load = async () => {
  loading.value = true;
  try {
    const res = await capabilityOverview(query);
    rows.value = res.data || [];
  } catch (error: any) {
    modal.alertError(error?.message || '读取能力概览失败');
    rows.value = [];
  } finally {
    loading.value = false;
  }
};

const clearFilter = () => {
  query.capabilityName = '';
  query.issueCode = '';
  query.candidateState = '';
  query.status = '';
  load();
};

const iconText = (requiredTags?: string) => {
  const tags = parseTags(requiredTags);
  return tags.length > 0 ? tags[0].slice(0, 3).toUpperCase() : '?';
};

const iconClass = (tag?: string) => {
  switch ((tag || '').toUpperCase()) {
    case 'VISION':
    case 'OCR':
      return 'cap-list__icon--vision';
    case 'IMAGE':
      return 'cap-list__icon--image';
    case 'VIDEO':
    case 'EMBEDDING':
      return 'cap-list__icon--multi';
    default:
      return 'cap-list__icon--text';
  }
};

const openDetail = (row: AigCapabilityOverviewVO) => {
  detailId.value = row.capabilityId;
  detailCode.value = row.capabilityCode;
};

const closeDetail = () => {
  detailId.value = undefined;
  detailCode.value = undefined;
  load();
};

const emptyForm = (): AigCapabilityForm => ({
  capabilityId: undefined,
  capabilityCode: '',
  capabilityName: '',
  bizGoal: '',
  requiredTags: '',
  inputSchema: '',
  outputSchema: '',
  dataPolicy: 'LOCAL_FIRST',
  auditLevel: 'SUMMARY',
  humanConfirmPoints: '',
  qualityThreshold: '',
  status: '0',
  remark: ''
});

const openCreate = () => {
  Object.assign(form, emptyForm());
  tagSelection.value = [];
  advanced.value = false;
  formVisible.value = true;
};

const openEdit = (capability?: AigCapabilityVO) => {
  if (!capability) {
    return;
  }
  Object.assign(form, emptyForm(), capability);
  // 留一份"改之前的状态"：Q1C 只把「启用 → 停用」这一个迁移当高风险，
  // 对已停用的能力改个描述不该被要求高风险权限（否则人会觉得权限点是随机拦人的）
  originalStatus.value = capability.status ?? '0';
  tagSelection.value = parseTags(capability.requiredTags);
  advanced.value = false;
  formVisible.value = true;
};

const openEditById = async (row: AigCapabilityOverviewVO) => {
  try {
    const res = await getCapability(row.capabilityId!);
    openEdit(res.data);
  } catch (error: any) {
    modal.alertError(error?.message || '读取能力详情失败');
  }
};

const submit = async () => {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  const schemaError = validateJsonText(form.inputSchema) || validateJsonText(form.outputSchema);
  if (schemaError) {
    modal.alertError(`Schema ${schemaError}`);
    return;
  }
  form.requiredTags = tagSelection.value.join(',');
  // Q6 + Q1C：停用是「在跑的能力立刻调不通」，保存前先把引用方列出来
  if (form.capabilityId && originalStatus.value !== '1' && form.status === '1') {
    let refs: AigCapabilityReferenceVO[] = [];
    try {
      const res = await listCapabilityReferences(form.capabilityCode!);
      refs = res.data || [];
    } catch (error: any) {
      modal.alertError(error?.message || '读取影响面失败：请稍后重试，或先确认没有引用方再停用');
      return;
    }
    try {
      await modal.confirm(disableConfirmText(form.capabilityName, form.capabilityCode!, refs));
    } catch {
      return;
    }
  }
  saving.value = true;
  try {
    if (form.capabilityId) {
      await updateCapability(form);
      modal.msgSuccess('已保存：改动会立即影响路由');
    } else {
      await addCapability(form);
      modal.msgSuccess('已创建：请到「模型候选」挂模型、到「策略」定外发，链路才算通');
    }
    formVisible.value = false;
    await load();
  } catch (error: any) {
    modal.alertError(error?.message || '保存失败');
  } finally {
    saving.value = false;
  }
};

const remove = async (row: AigCapabilityOverviewVO) => {
  // Q6（已拍板）：删除前强制先看影响面 —— 先把"谁在引用它"查出来写进确认框，
  // 而不是只问一句"确定删除吗"。查不到引用时也要明说"没有任何引用"（那是可安全删除的唯一依据）。
  let refs: AigCapabilityReferenceVO[] = [];
  try {
    const res = await listCapabilityReferences(row.capabilityCode!);
    refs = res.data || [];
  } catch (error: any) {
    // 影响面查不出来时**不静默继续**：宁可让使用者自己判断，也不要在没有影响面的情况下删掉
    modal.alertError(error?.message || '读取引用清单失败：请稍后重试，或在无引用确认后再删除');
    return;
  }
  try {
    await modal.confirm(deleteConfirmText(row.capabilityName, row.capabilityCode!, refs));
  } catch {
    return;
  }
  try {
    await delCapability(row.capabilityId!);
    modal.msgSuccess('已删除');
    await load();
  } catch (error: any) {
    modal.alertError(error?.message || '删除失败');
  }
};

onMounted(load);
</script>

<style scoped>
.cap-list__filter-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.cap-list__toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
}

.cap-list__toolbar-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cap-list__count {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cap-list__alert {
  margin-top: 10px;
}

.cap-list__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
  min-height: 120px;
  margin-top: 10px;
}

.cap-list__create {
  border-style: dashed;
}

.cap-list__create-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.cap-list__create-line {
  width: 100%;
  margin: 0 0 8px;
  justify-content: flex-start;
}

.cap-list__create-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cap-list__card-top {
  display: flex;
  gap: 10px;
  cursor: pointer;
}

.cap-list__icon {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  flex: 0 0 38px;
}

.cap-list__icon--text {
  background: linear-gradient(135deg, #2b63f0, #4c8dff);
}

.cap-list__icon--vision {
  background: linear-gradient(135deg, #6941c6, #8b6be0);
}

.cap-list__icon--image {
  background: linear-gradient(135deg, #e08700, #ffae33);
}

.cap-list__icon--multi {
  background: linear-gradient(135deg, #0e9f9f, #38c2c2);
}

.cap-list__card-main {
  min-width: 0;
  flex: 1;
}

.cap-list__card-name {
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.cap-list__card-code {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cap-list__card-desc {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
  min-height: 38px;
  cursor: pointer;
}

.cap-list__card-tags {
  margin-top: 6px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.cap-list__card-foot {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.cap-list__card-actions {
  margin-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 6px;
  display: flex;
  justify-content: flex-end;
}

.cap-list__muted {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.cap-list__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
