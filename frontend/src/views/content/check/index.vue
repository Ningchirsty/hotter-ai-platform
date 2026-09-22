<template>
  <div class="p-2 app-container content-check-page">
    <PageHeading
      title="内容生产协同"
      subtitle="出稿验收：把生成结果与原参考图并排比对，输出一致 / 不一致 / 无法判定与差异清单"
      module="content"
    />

    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>检查记录检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="任务号" prop="taskNo">
            <el-input
              v-model="queryParams.taskNo"
              placeholder="任务号，如 CT202601010001"
              clearable
              style="width: 220px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="检查状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
              <el-option v-for="dict in cp_check_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="检查结论" prop="verdict">
            <el-select v-model="queryParams.verdict" placeholder="全部" clearable style="width: 140px">
              <el-option v-for="dict in cp_check_verdict" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="只看未通过">
            <el-switch v-model="queryParams.onlyFailed" />
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
            <span class="panel-kicker">Output Consistency</span>
            <h3>成品一致性检查</h3>
            <p>共 {{ total }} 条记录；结论仅供验收参考，不作为产品事实依据。</p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['content:check:run']"
              type="primary"
              plain
              icon="Plus"
              @click="openRunDialog"
            >
              发起检查
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border :data="checkList">
        <el-table-column label="检查单号" align="center" prop="checkNo" width="150" show-overflow-tooltip />
        <el-table-column label="任务号" align="center" prop="taskNo" width="150" show-overflow-tooltip />
        <el-table-column label="任务名称" align="center" prop="taskName" min-width="160" show-overflow-tooltip />
        <el-table-column label="产品" align="center" prop="productName" min-width="130" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="cp_check_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="结论" align="center" width="110">
          <template #default="scope">
            <dict-tag v-if="scope.row.verdict" :options="cp_check_verdict" :value="scope.row.verdict" />
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="得分" align="center" width="90">
          <template #default="scope">
            <span v-if="scope.row.score !== null && scope.row.score !== undefined">{{ scope.row.score }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="执行者" align="center" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.modelKey">{{ scope.row.modelKey }}</span>
            <span v-else class="muted">—</span>
            <span v-if="scope.row.invokerName" class="muted">（{{ scope.row.invokerName }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="检查时间" align="center" width="170">
          <template #default="scope">
            <span v-if="scope.row.checkedAt">{{ parseTime(scope.row.checkedAt) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="查看比对结果" placement="top">
              <el-button
                v-hasPermi="['content:check:query']"
                link
                type="primary"
                icon="View"
                @click="openDetail(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                v-hasPermi="['content:check:remove']"
                link
                type="primary"
                icon="Delete"
                @click="handleDelete(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <!-- 发起检查 -->
    <el-dialog v-model="runDialog.visible" title="发起成品一致性检查" width="720px" append-to-body>
      <el-form ref="runFormRef" :model="runForm" :rules="runRules" label-width="120px">
        <el-form-item label="所属任务" prop="taskId">
          <el-select
            v-model="runForm.taskId"
            filterable
            placeholder="请选择内容生产任务"
            style="width: 100%"
            @change="handleTaskChange"
          >
            <el-option
              v-for="item in taskOptions"
              :key="item.taskId"
              :label="`${item.taskNo} · ${item.taskName}`"
              :value="item.taskId!"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="原参考图">
          <el-radio-group v-model="referenceMode">
            <el-radio value="EXISTING">引用任务中已有图片</el-radio>
            <el-radio value="UPLOAD">上传新参考图</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="referenceMode === 'EXISTING'" label="参考图附件">
          <el-select
            v-model="runForm.referenceFileId"
            placeholder="选择该任务下的图片附件"
            clearable
            style="width: 100%"
            :loading="fileLoading"
          >
            <el-option
              v-for="item in imageFiles"
              :key="item.fileId"
              :label="item.fileName"
              :value="item.fileId!"
            />
          </el-select>
          <div class="tip">仅列出该任务下已上传的图片附件；没有合适的就改用「上传新参考图」。</div>
        </el-form-item>

        <el-form-item v-else label="上传参考图">
          <el-upload
            :limit="1"
            :auto-upload="false"
            :file-list="referenceFileList"
            :on-change="(file: any) => handlePick('reference', file)"
            :on-remove="() => handleClear('reference')"
          >
            <el-button type="primary" plain icon="Upload">选择图片</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 PNG / JPEG / GIF / BMP / WebP，单张不超过 8MB。上传前会自动压缩，无需自行处理体积。</div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="成品图" prop="resultFile">
          <el-upload
            :limit="1"
            :auto-upload="false"
            :file-list="resultFileList"
            :on-change="(file: any) => handlePick('result', file)"
            :on-remove="() => handleClear('result')"
          >
            <el-button type="primary" plain icon="Upload">选择生成结果</el-button>
            <template #tip>
              <div class="el-upload__tip">出稿的成品图，单张不超过 8MB，上传前会自动压缩。与参考图一起提交后异步比对。</div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="runForm.remark" type="textarea" :rows="2" placeholder="可填写本次检查的背景，如版本号、改动点" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="running" @click="submitRun">确 定</el-button>
          <el-button @click="runDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 比对结果 -->
    <el-dialog v-model="detailDialog.visible" title="成品一致性比对结果" width="980px" append-to-body>
      <div v-if="detail" v-loading="detailLoading">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="检查单号">{{ detail.checkNo }}</el-descriptions-item>
          <el-descriptions-item label="任务">{{ detail.taskNo }} · {{ detail.taskName }}</el-descriptions-item>
          <el-descriptions-item label="产品">{{ detail.productName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <dict-tag :options="cp_check_status" :value="detail.status" />
          </el-descriptions-item>
          <el-descriptions-item label="结论">
            <dict-tag v-if="detail.verdict" :options="cp_check_verdict" :value="detail.verdict" />
            <span v-else class="muted">—</span>
          </el-descriptions-item>
          <el-descriptions-item label="得分">
            <span v-if="detail.score !== null && detail.score !== undefined">{{ detail.score }}</span>
            <span v-else class="muted">未给出（不编造）</span>
          </el-descriptions-item>
          <el-descriptions-item label="执行模型">{{ detail.modelKey || '—' }}</el-descriptions-item>
          <el-descriptions-item label="调用器">{{ detail.invokerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="部署类型">{{ detail.deploymentType || '—' }}</el-descriptions-item>
          <el-descriptions-item label="调用链" :span="3">{{ detail.traceId || '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.failureReason"
          class="mt-2"
          type="error"
          :closable="false"
          show-icon
          :title="'未取得结论：' + detail.failureReason"
        />
        <el-alert
          v-else
          class="mt-2"
          :type="verdictAlertType"
          :closable="false"
          show-icon
          :title="detail.summary || '暂无结论摘要'"
        />

        <div class="compare-grid mt-2">
          <div class="compare-cell">
            <div class="compare-title">原参考图</div>
            <div class="compare-body">
              <img v-if="referenceUrl" :src="referenceUrl" alt="参考图" />
              <div v-else class="muted">加载中或不可用</div>
            </div>
            <div class="compare-file">{{ detail.referenceFileName || '—' }}</div>
          </div>
          <div class="compare-cell">
            <div class="compare-title">生成结果</div>
            <div class="compare-body">
              <img v-if="resultUrl" :src="resultUrl" alt="成品图" />
              <div v-else class="muted">加载中或不可用</div>
            </div>
            <div class="compare-file">{{ detail.resultFileName || '—' }}</div>
          </div>
        </div>

        <el-divider content-position="left">差异清单</el-divider>
        <el-table v-if="findings.length" border size="small" :data="findings">
          <el-table-column label="类别" align="center" prop="category" width="140" />
          <el-table-column label="严重度" align="center" width="100">
            <template #default="scope">
              <el-tag :type="severityTag(scope.row.severity)" size="small">{{ scope.row.severity || 'INFO' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" align="left" prop="description" show-overflow-tooltip />
        </el-table>
        <el-empty v-else description="本次未给出差异项" :image-size="60" />

        <el-divider content-position="left">本地确定性度量（不依赖模型，可复算）</el-divider>
        <el-descriptions v-if="metrics" :column="3" border size="small">
          <el-descriptions-item label="参考图尺寸">
            {{ metrics.referenceWidth ?? '—' }} × {{ metrics.referenceHeight ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="成品图尺寸">
            {{ metrics.resultWidth ?? '—' }} × {{ metrics.resultHeight ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="画布可比">
            {{ metrics.comparable ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="网格相似度">
            {{ metrics.gridSimilarity === null || metrics.gridSimilarity === undefined ? '—' : metrics.gridSimilarity }}
          </el-descriptions-item>
          <el-descriptions-item label="平均亮度差">
            {{ metrics.meanLumaDiff === null || metrics.meanLumaDiff === undefined ? '—' : metrics.meanLumaDiff }}
          </el-descriptions-item>
          <el-descriptions-item label="网格">{{ metrics.gridSize || '—' }} × {{ metrics.gridSize || '—' }}</el-descriptions-item>
        </el-descriptions>
        <ul v-if="metrics?.notes?.length" class="note-list">
          <li v-for="(note, idx) in metrics!.notes" :key="idx">{{ note }}</li>
        </ul>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { compressAccurately } from 'image-conversion';
import type { CheckFinding, CheckMetrics, CpOutputCheckQuery, CpOutputCheckVO } from '@/api/content/check/types';
import { delCheck, fetchCheckImageBlobUrl, getCheck, listCheck, runCheck } from '@/api/content/check';
import { listTask, listTaskFiles } from '@/api/content/task';
import type { CpTaskFileVO, CpTaskVO } from '@/api/content/task/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'ContentCheck' });

const { cp_check_status, cp_check_verdict } = toRefs<any>(useDict('cp_check_status', 'cp_check_verdict'));

const checkList = ref<CpOutputCheckVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();

const data = reactive<PageData<any, CpOutputCheckQuery>>({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    taskNo: '',
    status: undefined,
    verdict: undefined,
    onlyFailed: false
  },
  rules: {}
});
const { queryParams } = toRefs<PageData<any, CpOutputCheckQuery>>(data);
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 查询检查记录 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listCheck(queryParams.value);
    checkList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 搜索 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

// ---------------- 发起检查 ----------------

const runDialog = reactive({ visible: false });
const runFormRef = ref<ElFormInstance>();
const running = ref(false);
const taskOptions = ref<CpTaskVO[]>([]);
const imageFiles = ref<CpTaskFileVO[]>([]);
const fileLoading = ref(false);
const referenceMode = ref<'EXISTING' | 'UPLOAD'>('EXISTING');
const referenceFile = ref<File | null>(null);
const resultFile = ref<File | null>(null);
const referenceFileList = ref<any[]>([]);
const resultFileList = ref<any[]>([]);

const initRunForm = () => ({
  taskId: undefined as string | number | undefined,
  referenceFileId: undefined as string | number | undefined,
  remark: ''
});
const runForm = ref(initRunForm());
const runRules = {
  taskId: [{ required: true, message: '请选择任务', trigger: 'change' }],
  resultFile: [{ required: true, message: '请上传成品图', trigger: 'change' }]
};

/** 打开发起检查弹窗并加载任务下拉 */
const openRunDialog = async () => {
  runForm.value = initRunForm();
  referenceMode.value = 'EXISTING';
  handleClear('reference');
  handleClear('result');
  imageFiles.value = [];
  runDialog.visible = true;
  try {
    const res = await listTask({ pageNum: 1, pageSize: 200 });
    taskOptions.value = res.data?.rows || [];
  } catch {
    modal.msgError('任务列表加载失败，请稍后重试');
  }
};

/** 切换任务时刷新其图片附件 */
const handleTaskChange = async (taskId: string | number) => {
  runForm.value.referenceFileId = undefined;
  imageFiles.value = [];
  if (!taskId) {
    return;
  }
  fileLoading.value = true;
  try {
    const res = await listTaskFiles(taskId);
    imageFiles.value = (res.data || []).filter((f: CpTaskFileVO) => f.fileKind === 'IMAGE');
  } catch {
    modal.msgError('任务附件加载失败');
  } finally {
    fileLoading.value = false;
  }
};

/** 选择文件（auto-upload=false，这里只抓住原始 File） */
const handlePick = (side: 'reference' | 'result', file: any) => {
  const raw: File | undefined = file?.raw;
  if (side === 'reference') {
    referenceFile.value = raw || null;
    referenceFileList.value = [file];
  } else {
    resultFile.value = raw || null;
    resultFileList.value = [file];
  }
};

/** 清空已选文件 */
const handleClear = (side: 'reference' | 'result') => {
  if (side === 'reference') {
    referenceFile.value = null;
    referenceFileList.value = [];
  } else {
    resultFile.value = null;
    resultFileList.value = [];
  }
};

// ---------------- 上传前压缩 ----------------

/**
 * 压缩目标体积（KB）。比对本身只需要 16×16 的亮度网格（本地确定性度量）
 * 或一张看得清的图（视觉模型），全分辨率从来不是必需的。
 * 取 200KB：生产 Tunnel 上行实测只有 10–20KB/s，两张图合计要落在几十秒内。
 */
const UPLOAD_TARGET_KB = 200;

/** 小于该体积就原样上传，避免对本来就很小的图再做无意义的再编码 */
const UPLOAD_PASSTHROUGH_KB = 300;

/** 与服务端 ContentFileKindEnum 的图片集合保持一致 */
const ALLOWED_IMAGE_EXT = ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'];

/** 单张原图硬上限（与服务端 MAX_CHECK_IMAGE_SIZE 一致） */
const MAX_UPLOAD_BYTES = 8 * 1024 * 1024;

/**
 * 上传前把图片压到目标体积。
 *
 * 为什么必须压：浏览器经 Cloudflare Tunnel 的上行实测只有 ~20KB/s（同机直连内网是 300MB/s）。
 * 一次检查要传两张图，原图动辄 3–8MB，光传输就要 150–800 秒——必然先撞前端 50s 请求超时，
 * 再撞 Cloudflare 的 100s 上限（HTTP 524）。压到几百 KB 后传输降到十几秒以内，比对精度不受影响。
 *
 * @param file 用户选择的原图
 * @returns 可直接上传的文件（压缩后，或原样返回）
 */
const prepareUploadImage = async (file: File): Promise<File> => {
  const ext = (file.name.split('.').pop() || '').toLowerCase();
  if (!ALLOWED_IMAGE_EXT.includes(ext)) {
    throw new Error(`「${file.name}」不是图片，仅支持 PNG / JPEG / GIF / BMP / WebP`);
  }
  if (file.size <= UPLOAD_PASSTHROUGH_KB * 1024) {
    return file;
  }
  try {
    const compressed = await compressAccurately(file, UPLOAD_TARGET_KB);
    // 统一按 JPEG 上传：体积可控，且服务端按扩展名判类型，.jpg 必然通过。
    // 代价是丢透明通道——本功能比对的是实物产品照片，可以接受。
    const name = file.name.replace(/\.[^.]+$/, '') + '.jpg';
    return new File([compressed], name, { type: 'image/jpeg' });
  } catch {
    // 压缩失败不直接拦死：原图若在服务端上限内仍可提交，超限时由服务端如实拒绝
    if (file.size > MAX_UPLOAD_BYTES) {
      throw new Error(`「${file.name}」压缩失败且原图超过 8MB，请先自行裁剪后再上传`);
    }
    return file;
  }
};

/** 轮询定时器句柄 */
let pollTimer: ReturnType<typeof setInterval> | null = null;

/** 停止轮询 */
const stopPolling = () => {
  if (pollTimer !== null) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
};

/**
 * 轮询检查结论。
 *
 * 比对跑在单线程异步执行器里：上传慢时请求本身就要十几秒，走视觉模型最坏一分钟以上。
 * 只刷新一次会让用户一直看到「检查中」，误以为卡死。这里 3 秒一轮、最多 40 轮（约 2 分钟），
 * 该条记录进入 DONE / FAILED 即停。
 *
 * @param checkId 本次提交的检查ID
 */
const startPolling = (checkId?: string | number) => {
  stopPolling();
  const target = checkId === undefined || checkId === null ? undefined : String(checkId);
  let ticks = 0;
  pollTimer = setInterval(async () => {
    ticks += 1;
    await getList();
    const row = target ? checkList.value.find((item) => String(item.checkId) === target) : undefined;
    const status = row ? String(row.status) : '';
    if ((target && (status === 'DONE' || status === 'FAILED')) || ticks >= 40) {
      stopPolling();
    }
  }, 3000);
};

onBeforeUnmount(() => stopPolling());

/** 提交检查 */
const submitRun = async () => {
  if (!runForm.value.taskId) {
    modal.msgWarning('请选择任务');
    return;
  }
  if (!resultFile.value) {
    modal.msgWarning('请上传成品图');
    return;
  }
  if (referenceMode.value === 'EXISTING' && !runForm.value.referenceFileId) {
    modal.msgWarning('请选择任务中已有的参考图，或改用「上传新参考图」');
    return;
  }
  if (referenceMode.value === 'UPLOAD' && !referenceFile.value) {
    modal.msgWarning('请上传参考图');
    return;
  }
  running.value = true;
  try {
    // 先压缩再上传：上行经 Cloudflare 只有 ~20KB/s，原图直传必然超时（见 prepareUploadImage）
    let preparedResult: File;
    let preparedReference: File | undefined;
    try {
      preparedResult = await prepareUploadImage(resultFile.value);
      preparedReference =
        referenceMode.value === 'UPLOAD' && referenceFile.value ? await prepareUploadImage(referenceFile.value) : undefined;
    } catch (e: any) {
      modal.msgError(e?.message || '图片处理失败，请检查文件后重试');
      return;
    }
    const checkId = await runCheck({
      taskId: runForm.value.taskId,
      referenceFileId: referenceMode.value === 'EXISTING' ? runForm.value.referenceFileId : undefined,
      referenceFile: preparedReference,
      resultFile: preparedResult,
      remark: runForm.value.remark
    });
    runDialog.visible = false;
    modal.msgSuccess('已提交检查，正在比对，完成后会自动刷新列表');
    await getList();
    // 上传本身就要十几秒，走视觉模型最坏一分钟以上；只刷一次会让用户一直看到「检查中」
    startPolling(checkId);
    if (checkId) {
      // 保持 checkId 引用，便于排障时定位（不弹窗打扰）
      console.debug('checkId =', checkId);
    }
  } finally {
    running.value = false;
  }
};

// ---------------- 比对结果 ----------------

const detailDialog = reactive({ visible: false });
const detailLoading = ref(false);
const detail = ref<CpOutputCheckVO | null>(null);
const findings = ref<CheckFinding[]>([]);
const metrics = ref<CheckMetrics | null>(null);
const referenceUrl = ref('');
const resultUrl = ref('');

/** 释放已创建的 blob URL，避免反复查看时内存堆积 */
const revokeUrls = () => {
  if (referenceUrl.value) {
    URL.revokeObjectURL(referenceUrl.value);
    referenceUrl.value = '';
  }
  if (resultUrl.value) {
    URL.revokeObjectURL(resultUrl.value);
    resultUrl.value = '';
  }
};

/** 查看比对结果 */
const openDetail = async (row: CpOutputCheckVO) => {
  detailDialog.visible = true;
  detailLoading.value = true;
  detail.value = null;
  findings.value = [];
  metrics.value = null;
  revokeUrls();
  try {
    const res = await getCheck(row.checkId!);
    detail.value = res.data || null;
    findings.value = safeParse<CheckFinding[]>(detail.value?.findingsJson, []);
    metrics.value = safeParse<CheckMetrics | null>(detail.value?.metricsJson, null);
    const checkId = detail.value?.checkId;
    if (checkId) {
      // 图片单独取：一张图可能几 MB，失败不该让整个详情打不开
      fetchCheckImageBlobUrl(checkId, 'reference')
        .then(url => (referenceUrl.value = url))
        .catch(() => (referenceUrl.value = ''));
      fetchCheckImageBlobUrl(checkId, 'result')
        .then(url => (resultUrl.value = url))
        .catch(() => (resultUrl.value = ''));
    }
  } finally {
    detailLoading.value = false;
  }
};

/** 关闭详情时释放 blob URL */
watch(
  () => detailDialog.visible,
  visible => {
    if (!visible) {
      revokeUrls();
    }
  }
);

/** 解析后端下发的 JSON 字符串；解析失败返回兜底值而不是抛错 */
const safeParse = <T,>(raw: string | undefined, fallback: T): T => {
  if (!raw) {
    return fallback;
  }
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
};

/** 结论对应的提示样式 */
const verdictAlertType = computed(() => {
  switch (detail.value?.verdict) {
    case 'CONSISTENT':
      return 'success';
    case 'INCONSISTENT':
      return 'error';
    default:
      return 'warning';
  }
});

/** 严重度对应的标签样式 */
const severityTag = (severity?: string) => {
  switch ((severity || '').toUpperCase()) {
    case 'ERROR':
      return 'danger';
    case 'WARN':
      return 'warning';
    default:
      return 'info';
  }
};

/** 删除检查记录 */
const handleDelete = async (row: CpOutputCheckVO) => {
  await modal.confirm('是否确认删除检查单「' + row.checkNo + '」？删除后不再出现在验收列表中。');
  await delCheck(row.checkId!);
  await getList();
  modal.msgSuccess('删除成功');
};

onMounted(() => {
  getList();
});

onBeforeUnmount(() => {
  revokeUrls();
});
</script>

<style lang="scss" scoped>
.content-check-page {
  .muted {
    color: var(--el-text-color-secondary);
  }

  .tip {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.6;
  }

  .mt-2 {
    margin-top: 12px;
  }
}

/*
 * 弹窗相关样式刻意放在顶层、不嵌套进 .content-check-page：
 * el-dialog 用了 append-to-body，DOM 挂在 body 下，已不是 .content-check-page 的后代。
 * 嵌套写法会让「.content-check-page .compare-grid」这个祖先选择器匹配不到，
 * 表现为并排比对退化成上下堆叠、图片尺寸约束失效——而且不报任何错。
 * scoped 的作用域属性仍会打在 teleport 出去的元素上，故顶层规则依然生效。
 */
.compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-items: start;
}

.compare-cell {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px;
  background: var(--el-fill-color-blank);
  min-width: 0;
}

.compare-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.compare-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  max-height: 420px;
  overflow: auto;
  background: var(--el-fill-color-light);
  border-radius: 4px;

  img {
    max-width: 100%;
    max-height: 400px;
    object-fit: contain;
  }
}

.compare-file {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.note-list {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
}

.dialog-footer {
  text-align: right;
}
</style>
