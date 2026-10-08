<template>
  <div class="p-2 app-container aigov-evaluation-page">
    <PageHeading title="AI平台治理" subtitle="管理AI能力、模型接入与调用策略" admin module="aigov" />
    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Golden Cases</span>
            <h3>黄金用例</h3>
            <p>
              共 {{ caseList.length }} 条用例；判据（expected_json）在<b>定义期</b>即校验：未知判据名、类型写错、
              空判据对象都会当场被拒——否则一条写错的判据会被当成「跑不过」，于是大家去改 Prompt，而错的是用例。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['aig:evaluation:define']" type="primary" plain icon="Plus" @click="openDefine">
              定义用例
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="caseList">
        <el-table-column label="用例编码" align="center" prop="caseCode" width="240" show-overflow-tooltip />
        <el-table-column label="名称" align="center" prop="caseName" width="200" show-overflow-tooltip />
        <el-table-column label="类型" align="center" prop="caseType" width="110" />
        <el-table-column label="输入快照引用" align="center" prop="inputSnapshotRef" show-overflow-tooltip />
        <el-table-column label="成本范围（USD）" align="center" width="150">
          <template #default="scope">{{ scope.row.costMin ?? '-' }} ~ {{ scope.row.costMax ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="数据等级" align="center" prop="dataLevel" width="110" />
        <el-table-column label="操作" align="center" width="100" fixed="right">
          <template #default="scope">
            <el-button v-hasPermi="['aig:evaluation:query']" link type="primary" icon="View" @click="openCase(scope.row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="hover" class="table-panel mt-2">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Evaluation Runs</span>
            <h3>评测运行与证据</h3>
            <p>
              评测只对 <b>SANDBOX_TESTED</b> 的版本开放；<b>用例集合必须等于版本声明的集合</b>
              （只跑自选的一两条不能当作整组通过）。发布门槛 GOLDEN_CASE 查的是这里的账本，
              而且要求每条用例最近一次运行 PASS、人工复核不是 FAIL/待复核。
            </p>
          </div>
        </div>
      </template>

      <el-form :inline="true">
        <el-form-item label="对象类型">
          <el-select v-model="targetType" style="width: 190px">
            <el-option label="Agent 版本" value="AGENT_VERSION" />
            <el-option label="Skill 版本" value="SKILL_VERSION" />
            <el-option label="Package 版本" value="PACKAGE_VERSION" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象版本ID">
          <el-input v-model="targetVersionId" placeholder="版本ID" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button icon="Search" @click="loadRuns">列出运行</el-button>
          <el-button v-hasPermi="['aig:evaluation:query']" icon="DocumentChecked" @click="loadEvidence">查证据</el-button>
          <el-button v-hasPermi="['aig:evaluation:run']" type="primary" icon="VideoPlay" @click="openRun">
            跑评测
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="evidenceText" :type="evidenceOk ? 'success' : 'warning'" :closable="false" class="mb-2">
        {{ evidenceText }}
      </el-alert>

      <el-table v-loading="runLoading" border :data="runList">
        <el-table-column label="运行编号" align="center" prop="runNo" width="230" show-overflow-tooltip />
        <el-table-column label="用例" align="center" prop="caseId" width="180" show-overflow-tooltip />
        <el-table-column label="结论" align="center" width="110">
          <template #default="scope">
            <el-tag :type="runTagType(scope.row.resultStatus)">{{ scope.row.resultStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="人工复核" align="center" width="120">
          <template #default="scope">
            <el-tag :type="reviewTagType(scope.row.reviewResult)">{{ scope.row.reviewResult || '不需要' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成本（USD）" align="center" prop="costAmount" width="120" />
        <el-table-column label="耗时(ms)" align="center" prop="latencyMs" width="110" />
        <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="110" fixed="right">
          <template #default="scope">
            <el-button
              v-hasPermi="['aig:evaluation:review']"
              link
              type="primary"
              icon="EditPen"
              :disabled="scope.row.resultStatus === 'RUNNING'"
              @click="openReview(scope.row)"
            >
              复核
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 用例详情 -->
    <el-dialog v-model="caseVisible" :title="'用例 · ' + (currentCase?.caseName || '')" width="760px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用例编码">{{ currentCase?.caseCode }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentCase?.caseType }}</el-descriptions-item>
        <el-descriptions-item label="输入快照引用">{{ currentCase?.inputSnapshotRef }}</el-descriptions-item>
        <el-descriptions-item label="机器判据">{{ currentCase?.expectedJson || '（无：由人工 Rubric 判定）' }}</el-descriptions-item>
        <el-descriptions-item label="人工 Rubric">{{ currentCase?.rubricJson || '（无）' }}</el-descriptions-item>
        <el-descriptions-item label="成本范围（USD）">
          {{ currentCase?.costMin ?? '-' }} ~ {{ currentCase?.costMax ?? '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 定义用例 -->
    <el-dialog v-model="defineVisible" title="定义黄金用例" width="760px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        判据写法（五条，全部可判定）：required_paths / equals / min_items / must_contain / forbidden_contains。
        不用机器判据时可以留空并填 Rubric（该用例转人工复核）。判不了的不要硬写成判据——那只会让人去改 Prompt。
      </el-alert>
      <el-form ref="defineFormRef" :model="defineForm" :rules="defineRules" label-width="120px">
        <el-form-item label="用例编码" prop="caseCode">
          <el-input v-model="defineForm.caseCode" placeholder="全局唯一" />
        </el-form-item>
        <el-form-item label="名称" prop="caseName">
          <el-input v-model="defineForm.caseName" />
        </el-form-item>
        <el-form-item label="类型" prop="caseType">
          <el-select v-model="defineForm.caseType" style="width: 220px">
            <el-option label="PLAN 详情页策划" value="PLAN" />
            <el-option label="VISUAL_DNA 参考图分析" value="VISUAL_DNA" />
            <el-option label="IMAGE_QA 图像生成与 QA" value="IMAGE_QA" />
          </el-select>
        </el-form-item>
        <el-form-item label="输入快照引用" prop="inputSnapshotRef">
          <el-input v-model="defineForm.inputSnapshotRef" placeholder="inline:<json> 或 classpath:creative/eval/xxx.png" />
        </el-form-item>
        <el-form-item label="机器判据" prop="expectedJson">
          <el-input
            v-model="defineForm.expectedJson"
            type="textarea"
            :rows="6"
            placeholder='{"required_paths":["a.b"],"equals":{"c":1},"min_items":{"d":3}}'
          />
        </el-form-item>
        <el-form-item label="人工Rubric" prop="rubricJson">
          <el-input v-model="defineForm.rubricJson" type="textarea" :rows="3" placeholder="填了就意味着这条用例需要人工复核" />
        </el-form-item>
        <el-form-item label="成本范围（USD）" prop="costMin">
          <el-input v-model.number="defineForm.costMin" placeholder="下限" style="width: 140px" />
          <span class="mx-2">~</span>
          <el-input v-model.number="defineForm.costMax" placeholder="上限" style="width: 140px" />
          <span class="hint-inline">确定性执行器写 0：一旦接上模型调用，用例就会失败</span>
        </el-form-item>
        <el-form-item label="数据等级" prop="dataLevel">
          <el-select v-model="defineForm.dataLevel" style="width: 200px">
            <el-option label="PUBLIC" value="PUBLIC" />
            <el-option label="INTERNAL" value="INTERNAL" />
            <el-option label="RESTRICTED" value="RESTRICTED" />
            <el-option label="STRICT" value="STRICT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="defineVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDefine">确定</el-button>
      </template>
    </el-dialog>

    <!-- 跑评测 -->
    <el-dialog v-model="runVisible" title="跑评测" width="680px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        用例集合必须与版本声明的集合<b>完全一致</b>。下面已按版本声明的集合预填（服务端校验）；
        若为空，说明该版本还没声明黄金用例集合，请先在版本 config_json 里声明。
      </el-alert>
      <el-form label-width="110px">
        <el-form-item label="对象">
          <el-input :model-value="targetType + ' #' + targetVersionId" disabled />
        </el-form-item>
        <el-form-item label="用例集合">
          <el-checkbox-group v-model="runForm.caseCodes">
            <el-checkbox v-for="item in declaredCodes" :key="item" :value="item">{{ item }}</el-checkbox>
          </el-checkbox-group>
          <div v-if="!declaredCodes.length" class="hint-inline">该版本没有声明黄金用例集合</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRun">开始跑</el-button>
      </template>
    </el-dialog>

    <!-- 人工复核 -->
    <el-dialog v-model="reviewVisible" title="人工复核" width="520px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="运行">
          <el-input :model-value="reviewForm.runId" disabled />
        </el-form-item>
        <el-form-item label="复核结论" required>
          <el-radio-group v-model="reviewForm.reviewResult">
            <el-radio value="PASS">通过</el-radio>
            <el-radio value="FAIL">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="人工总分">
          <el-input v-model.number="reviewForm.totalScore" placeholder="可空；量纲由 Rubric 定义" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="reviewForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false">
        复核不能把机器判据的「不通过」改成通过：报告要求「最近一次运行 PASS 且人工复核不是 FAIL」，
        两者缺一不可。待复核（MANUAL）也不等于通过。
      </el-alert>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { caseEvidence, defineCase, getCase, listCase, listRun, reviewRun, runEvaluation, declaredCases } from '@/api/aigov/evaluation';
import type {
  AigEvaluationCaseDetail,
  AigEvaluationCaseForm,
  AigEvaluationCaseVO,
  AigEvaluationReviewForm,
  AigEvaluationRunForm,
  AigEvaluationRunVO
} from '@/api/aigov/evaluation/types';
import { useLoading } from '@/hooks/async/useLoading';
import modal from '@/plugins/modal';

defineOptions({ name: 'AigEvaluationConsole' });

const caseList = ref<AigEvaluationCaseVO[]>([]);
const runList = ref<AigEvaluationRunVO[]>([]);
const declaredCodes = ref<string[]>([]);
const currentCase = ref<AigEvaluationCaseDetail>();
const { loading, withLoading } = useLoading(true);
const { loading: runLoading, withLoading: withRunLoading } = useLoading(true);
const caseVisible = ref(false);
const defineVisible = ref(false);
const runVisible = ref(false);
const reviewVisible = ref(false);
const evidenceText = ref('');
const evidenceOk = ref(false);
const defineFormRef = ref<ElFormInstance>();
const targetType = ref('AGENT_VERSION');
const targetVersionId = ref('');

const defineForm = ref<AigEvaluationCaseForm>({
  caseCode: '',
  caseName: '',
  caseType: 'PLAN',
  inputSnapshotRef: '',
  expectedJson: '',
  rubricJson: '',
  costMin: undefined,
  costMax: undefined,
  dataLevel: 'INTERNAL'
});

const defineRules = {
  caseCode: [{ required: true, message: '用例编码不能为空', trigger: 'blur' }],
  caseName: [{ required: true, message: '用例名称不能为空', trigger: 'blur' }],
  caseType: [{ required: true, message: '请选择用例类型', trigger: 'change' }]
};

const runForm = ref<AigEvaluationRunForm>({ targetType: 'AGENT_VERSION', targetVersionId: '', caseCodes: [] });
const reviewForm = ref<AigEvaluationReviewForm>({ runId: '', reviewResult: 'PASS', remark: '' });

/** 运行结论标签色（FAIL 与 ERROR 分开：前者是被测对象的问题，后者是环境/用例的问题） */
const runTagType = (value?: string): 'success' | 'danger' | 'warning' | 'info' | undefined => {
  if (value === 'PASS') return 'success';
  if (value === 'FAIL') return 'danger';
  if (value === 'ERROR') return 'warning';
  return 'info';
};

/** 复核标签色（MANUAL=待复核，与「通过」不是一回事） */
const reviewTagType = (value?: string): 'success' | 'danger' | 'warning' | 'info' | undefined => {
  if (value === 'PASS') return 'success';
  if (value === 'FAIL') return 'danger';
  if (value === 'MANUAL') return 'warning';
  return 'info';
};

/** 加载用例清单 */
const getCases = async () => {
  await withLoading(async () => {
    const res = await listCase();
    caseList.value = res.data || [];
  });
};

/** 查用例详情（含判据原文） */
const openCase = async (row: AigEvaluationCaseVO) => {
  const res = await getCase(row.caseId as string | number);
  currentCase.value = res.data;
  caseVisible.value = true;
};

/** 打开定义用例 */
const openDefine = () => {
  defineForm.value = {
    caseCode: '',
    caseName: '',
    caseType: 'PLAN',
    inputSnapshotRef: '',
    expectedJson: '',
    rubricJson: '',
    costMin: undefined,
    costMax: undefined,
    dataLevel: 'INTERNAL'
  };
  defineVisible.value = true;
};

/** 提交定义 */
const submitDefine = async () => {
  await defineFormRef.value?.validate();
  await defineCase(defineForm.value);
  modal.msgSuccess('定义成功');
  defineVisible.value = false;
  getCases();
};

/** 列出运行 */
const loadRuns = async () => {
  if (!targetVersionId.value) {
    modal.msgError('请先填对象版本ID');
    return;
  }
  await withRunLoading(async () => {
    const res = await listRun(targetType.value, targetVersionId.value);
    runList.value = res.data || [];
  });
};

/** 查证据 */
const loadEvidence = async () => {
  if (!targetVersionId.value) {
    modal.msgError('请先填对象版本ID');
    return;
  }
  const res = await caseEvidence(targetType.value, targetVersionId.value);
  const data = res.data;
  const verdicts = Object.entries(data?.caseVerdicts || {})
    .map(([code, verdict]) => code + '=' + verdict)
    .join('、');
  evidenceOk.value = !!data?.satisfied;
  evidenceText.value = data?.satisfied
    ? '黄金用例已通过：' + verdicts
    : '未通过：' + (data?.reason || '') + (verdicts ? '（逐用例：' + verdicts + '）' : '');
};

/** 打开跑评测（按版本声明的集合预填） */
const openRun = async () => {
  if (!targetVersionId.value) {
    modal.msgError('请先填对象版本ID');
    return;
  }
  const res = await declaredCases(targetType.value, targetVersionId.value);
  declaredCodes.value = res.data || [];
  runForm.value = {
    targetType: targetType.value,
    targetVersionId: targetVersionId.value,
    caseCodes: [...declaredCodes.value]
  };
  runVisible.value = true;
};

/** 提交跑评测 */
const submitRun = async () => {
  if (!runForm.value.caseCodes.length) {
    modal.msgError('用例集合不能为空');
    return;
  }
  await modal.confirm('开始跑评测？用例集合必须与版本声明的集合完全一致，否则服务层会拒绝。');
  const res = await runEvaluation(runForm.value);
  const runs = res.data || [];
  modal.msgSuccess('本次产生 ' + runs.length + ' 条运行记录');
  runVisible.value = false;
  await loadRuns();
};

/** 打开复核 */
const openReview = (row: AigEvaluationRunVO) => {
  reviewForm.value = {
    runId: row.runId as string | number,
    reviewResult: 'PASS',
    totalScore: undefined,
    remark: ''
  };
  reviewVisible.value = true;
};

/** 提交复核 */
const submitReview = async () => {
  await reviewRun(reviewForm.value);
  modal.msgSuccess('复核已提交');
  reviewVisible.value = false;
  await loadRuns();
};

onMounted(() => {
  getCases();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.hint-inline {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
