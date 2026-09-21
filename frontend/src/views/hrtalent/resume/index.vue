<template>
  <div class="p-2 app-container hrtalent-resume-page">
    <PageHeading
      title="简历中心"
      subtitle="某人才的简历版本列表、上传新版本（不覆盖旧文件）、指定当前版本、受控下载（必填用途并记审计）、异步解析任务与人工逐字段复核"
      module="hrtalent"
    />

    <!-- 人才选择 -->
    <el-card shadow="hover" class="table-panel resume-shell">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Resume Center</span>
            <h3>当前人才</h3>
            <p>
              简历挂靠在人才主档下：先选择人才，再管理其简历版本。上传新简历<strong>默认创建新版本，绝不覆盖旧文件</strong>；
              下载必须填写用途，服务端会先做资源级鉴权并写审计。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['talent:resume:list']" type="primary" icon="Search" @click="openTalentPicker">
              选择人才
            </el-button>
            <el-button v-hasPermi="['talent:resume:upload']" type="success" plain icon="Upload" :disabled="!talent.talentId" @click="openUpload">
              上传新版本
            </el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="3" border size="small" class="detail-panel">
        <el-descriptions-item label="人才编号">{{ talent.talentNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ talent.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电话（脱敏）">{{ talent.phoneMasked || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前公司">{{ talent.currentCompany || '-' }}</el-descriptions-item>
        <el-descriptions-item label="意向岗位">{{ talent.expectedPosition || '-' }}</el-descriptions-item>
        <el-descriptions-item label="人才状态">
          <dict-tag v-if="talent.talentStatus" :options="talent_status" :value="talent.talentStatus" />
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-form :inline="true" class="query-form">
        <el-form-item label="是否当前版本">
          <el-select v-model="resumeQuery.currentFlag" placeholder="全部" clearable style="width: 120px" @change="loadResume">
            <el-option label="当前版本" value="1" />
            <el-option label="历史版本" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="解析状态">
          <el-select v-model="resumeQuery.parseStatus" placeholder="全部" clearable style="width: 150px" @change="loadResume">
            <el-option v-for="dict in talent_resume_parse_status" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="复核状态">
          <el-select v-model="resumeQuery.reviewStatus" placeholder="全部" clearable style="width: 150px" @change="loadResume">
            <el-option
              v-for="dict in talent_resume_review_status"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="文件后缀">
          <el-input v-model="resumeQuery.fileSuffix" placeholder="如 pdf/docx" clearable style="width: 140px" @keyup.enter="loadResume" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadResume">刷新</el-button>
          <el-button icon="Refresh" @click="resetResumeQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="resumeLoading" border size="small" :data="resumeList">
        <el-table-column label="版本号" align="center" prop="versionNo" width="80" />
        <el-table-column label="文件名" prop="originalName" min-width="200" show-overflow-tooltip />
        <el-table-column label="后缀" align="center" prop="fileSuffix" width="80" />
        <el-table-column label="大小" align="center" width="100">
          <template #default="scope">{{ formatFileSize(scope.row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="当前版本" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.currentFlag === '1' ? 'success' : 'info'" size="small">
              {{ scope.row.currentFlag === '1' ? '当前' : '历史' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="解析状态" align="center" width="110">
          <template #default="scope">
            <dict-tag v-if="scope.row.parseStatus" :options="talent_resume_parse_status" :value="scope.row.parseStatus" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="复核状态" align="center" width="110">
          <template #default="scope">
            <dict-tag
              v-if="scope.row.reviewStatus"
              :options="talent_resume_review_status"
              :value="scope.row.reviewStatus"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="扫描状态" align="center" prop="scanStatus" width="100" />
        <el-table-column label="上传人" align="center" width="110">
          <template #default="scope">{{ scope.row.uploadedByName || scope.row.uploadedBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="上传时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.uploadedTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="250" fixed="right">
          <template #default="scope">
            <el-tooltip content="受控下载（需填用途，记录审计）" placement="top">
              <el-button
                v-hasPermi="['talent:resume:download']"
                link
                type="primary"
                icon="Download"
                @click="openDownload(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="指定为当前版本" placement="top">
              <el-button
                v-hasPermi="['talent:resume:version']"
                link
                type="primary"
                icon="Star"
                :disabled="scope.row.currentFlag === '1'"
                @click="setCurrent(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="创建解析任务" placement="top">
              <el-button
                v-hasPermi="['talent:resume:parse']"
                link
                type="warning"
                icon="MagicStick"
                @click="createParse(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="解析任务与人工复核" placement="top">
              <el-button
                v-hasPermi="['talent:resume:review']"
                link
                type="primary"
                icon="View"
                @click="openReviewDialog(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="版本列表" placement="top">
              <el-button
                v-hasPermi="['talent:resume:list']"
                link
                type="primary"
                icon="Clock"
                @click="openVersions(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 上传新版本 -->
    <el-dialog v-model="uploadDialog.visible" title="上传简历新版本" width="640px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="上传会创建新版本（版本号单调递增），绝不覆盖旧文件；上传后新版本自动成为当前版本。文件扩展名/MIME/大小由服务端校验。"
      />
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="90px">
        <el-form-item label="简历文件" prop="file">
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            :file-list="uploadFileList"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :on-exceed="handleFileExceed"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 pdf/doc/docx 等常见简历格式，单个文件请勿超过服务端上限。</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="来源类型" prop="sourceType">
          <el-select v-model="uploadForm.sourceType" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="dict in talent_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="uploadForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="uploadDialog.loading" @click="submitUpload">上 传</el-button>
          <el-button @click="uploadDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 受控下载：必填用途 -->
    <el-dialog v-model="downloadDialog.visible" title="受控下载简历" width="540px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="简历属敏感资料：服务端会先做人才可见范围鉴权、校验用途非空，再写入审计并返回内容；用途为空会被拒绝并记录 denied 审计。"
      />
      <el-form ref="downloadFormRef" :model="downloadForm" :rules="downloadRules" label-width="80px">
        <el-form-item label="文件">
          <el-input :model-value="downloadDialog.row.originalName" disabled />
        </el-form-item>
        <el-form-item label="用途" prop="purpose">
          <el-input
            v-model="downloadForm.purpose"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="请填写下载用途（必填，最长 255 字）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="downloadDialog.loading" @click="submitDownload">确认下载</el-button>
          <el-button @click="downloadDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 版本列表 -->
    <el-dialog v-model="versionDialog.visible" title="简历版本列表" width="860px" append-to-body>
      <el-table v-loading="versionDialog.loading" border size="small" :data="versionList">
        <el-table-column label="版本号" align="center" prop="versionNo" width="80" />
        <el-table-column label="文件名" prop="originalName" min-width="200" show-overflow-tooltip />
        <el-table-column label="当前版本" align="center" width="90">
          <template #default="scope">{{ scope.row.currentFlag === '1' ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="解析状态" align="center" width="110">
          <template #default="scope">
            <dict-tag v-if="scope.row.parseStatus" :options="talent_resume_parse_status" :value="scope.row.parseStatus" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="复核状态" align="center" width="110">
          <template #default="scope">
            <dict-tag
              v-if="scope.row.reviewStatus"
              :options="talent_resume_review_status"
              :value="scope.row.reviewStatus"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.uploadedTime) || '-' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="versionDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 解析任务与人工逐字段复核 -->
    <el-dialog v-model="reviewDialog.visible" title="解析任务与人工复核" width="1080px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="一期只创建异步解析任务并做人工复核：解析引擎未接入时任务会停在 pending 或置 failed 并给出明确提示。低置信度候选项默认不勾选，只有人工勾选确认的字段才会写入人才主档/经历。"
      />
      <el-form :inline="true">
        <el-form-item label="解析任务ID">
          <el-input v-model="reviewDialog.taskId" placeholder="输入或由「创建解析任务」得到" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadParseTask">查询任务</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="reviewDialog.task" :column="3" border size="small" class="detail-panel">
        <el-descriptions-item label="任务ID">{{ reviewDialog.task.taskId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="简历版本ID">{{ reviewDialog.task.resumeId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务状态">{{ reviewDialog.task.taskStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="解析器类型">{{ reviewDialog.task.parserType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="解析器版本">{{ reviewDialog.task.parserVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ reviewDialog.task.retryCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">
          {{ reviewDialog.task.createByName || reviewDialog.task.createBy || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(reviewDialog.task.createTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ parseTime(reviewDialog.task.finishedTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误码">{{ reviewDialog.task.errorCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2">{{ reviewDialog.task.errorMessage || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table v-loading="reviewDialog.loading" border size="small" :data="reviewDialog.results">
        <el-table-column label="确认写入" align="center" width="90">
          <template #default="scope">
            <el-checkbox v-model="scope.row.accepted" :disabled="!canReview" />
          </template>
        </el-table-column>
        <el-table-column label="字段路径" prop="fieldPath" width="180" show-overflow-tooltip />
        <el-table-column label="原始值" prop="rawValue" min-width="150" show-overflow-tooltip />
        <el-table-column label="标准化值（可修正）" min-width="180">
          <template #default="scope">
            <el-input v-model="scope.row.normalizedValue" size="small" :disabled="!canReview" placeholder="为空则使用解析值" />
          </template>
        </el-table-column>
        <el-table-column label="置信度" align="center" width="90">
          <template #default="scope">{{ scope.row.confidence ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="来源位置" prop="sourceLocation" width="120" show-overflow-tooltip />
        <el-table-column label="复核状态" align="center" width="110">
          <template #default="scope">
            <dict-tag
              v-if="scope.row.reviewStatus"
              :options="talent_resume_review_status"
              :value="scope.row.reviewStatus"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="默认勾选" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.defaultSelected ? 'success' : 'info'" size="small">
              {{ scope.row.defaultSelected ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-form label-width="90px" class="review-remark">
        <el-form-item label="复核备注">
          <el-input v-model="reviewDialog.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="toggleAllAccepted(true)">全选</el-button>
          <el-button @click="toggleAllAccepted(false)">全不选</el-button>
          <el-button
            v-hasPermi="['talent:resume:review']"
            type="primary"
            :loading="reviewDialog.submitting"
            :disabled="!canReview"
            @click="submitReview"
          >
            确认复核并写入正式字段
          </el-button>
          <el-button @click="reviewDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 人才选择 -->
    <el-dialog v-model="talentPicker.visible" title="选择人才" width="860px" append-to-body>
      <el-form :inline="true">
        <el-form-item label="姓名">
          <el-input v-model="talentPicker.name" placeholder="模糊匹配" clearable @keyup.enter="searchTalent" />
        </el-form-item>
        <el-form-item label="人才编号">
          <el-input v-model="talentPicker.talentNo" placeholder="模糊匹配" clearable @keyup.enter="searchTalent" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="searchTalent">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="talentPicker.loading" border size="small" :data="talentPicker.rows">
        <el-table-column label="人才编号" align="center" prop="talentNo" width="150" show-overflow-tooltip />
        <el-table-column label="姓名" align="center" prop="name" width="100" />
        <el-table-column label="电话（脱敏）" align="center" prop="phoneMasked" width="130" />
        <el-table-column label="当前公司" prop="currentCompany" min-width="150" show-overflow-tooltip />
        <el-table-column label="意向岗位" prop="expectedPosition" min-width="130" show-overflow-tooltip />
        <el-table-column label="人才状态" align="center" width="100">
          <template #default="scope">
            <dict-tag v-if="scope.row.talentStatus" :options="talent_status" :value="scope.row.talentStatus" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="80" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="pickTalent(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="talentPicker.total > 0"
        v-model:page="talentPicker.pageNum"
        v-model:limit="talentPicker.pageSize"
        :total="talentPicker.total"
        @pagination="searchTalent"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="talentPicker.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { listProfile } from '@/api/hrtalent/profile';
import type { HrTalentProfileVO } from '@/api/hrtalent/profile/types';
import {
  confirmParseTask,
  createParseTask,
  downloadResume,
  getParseTask,
  listResume,
  listResumeVersions,
  setCurrentResume,
  uploadResume
} from '@/api/hrtalent/resume';
import type {
  HrTalentParseResultVO,
  HrTalentParseTaskVO,
  HrTalentResumeQuery,
  HrTalentResumeVO
} from '@/api/hrtalent/resume/types';
import modal from '@/plugins/modal';
import { saveBlob } from '@/utils/save';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'HrTalentResume' });

const { talent_resume_parse_status, talent_resume_review_status, talent_source_type, talent_status } = toRefs<any>(
  useDict('talent_resume_parse_status', 'talent_resume_review_status', 'talent_source_type', 'talent_status')
);

/** 当前选中的服务端校验标记（能否复核：必须有已加载的解析任务与候选结果） */
const talent = ref<Partial<HrTalentProfileVO>>({});
const resumeList = ref<HrTalentResumeVO[]>([]);
const resumeLoading = ref(false);
const resumeQuery = reactive<HrTalentResumeQuery>({
  currentFlag: undefined,
  parseStatus: undefined,
  reviewStatus: undefined,
  fileSuffix: undefined
});

const uploadFormRef = ref<ElFormInstance>();
const downloadFormRef = ref<ElFormInstance>();

/** 文件大小展示 */
const formatFileSize = (size?: number) => {
  if (size === undefined || size === null) {
    return '-';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
};

/* ------------------------------ 简历列表 ------------------------------ */

const loadResume = async () => {
  if (!talent.value.talentId) {
    resumeList.value = [];
    return;
  }
  resumeLoading.value = true;
  try {
    const res = await listResume(talent.value.talentId, resumeQuery);
    resumeList.value = res.data || [];
  } finally {
    resumeLoading.value = false;
  }
};

const resetResumeQuery = () => {
  Object.assign(resumeQuery, {
    currentFlag: undefined,
    parseStatus: undefined,
    reviewStatus: undefined,
    fileSuffix: undefined
  });
  loadResume();
};

/* ------------------------------ 上传新版本 ------------------------------ */

const uploadDialog = reactive<{ visible: boolean; loading: boolean }>({ visible: false, loading: false });
const uploadFileList = ref<any[]>([]);
const uploadFile = ref<File | null>(null);
const uploadForm = ref<{ sourceType?: string; remark?: string }>({ sourceType: 'manual', remark: '' });
const uploadRules: ElFormRules = {
  sourceType: [{ required: true, message: '来源类型不能为空', trigger: 'change' }]
};

const openUpload = () => {
  uploadFileList.value = [];
  uploadFile.value = null;
  uploadForm.value = { sourceType: 'manual', remark: '' };
  uploadDialog.visible = true;
};

const handleFileChange = (file: any) => {
  uploadFile.value = file.raw || null;
  uploadFileList.value = [file];
};

const handleFileRemove = () => {
  uploadFile.value = null;
  uploadFileList.value = [];
};

const handleFileExceed = () => {
  modal.msgWarning('一次只能上传一个文件，请先移除已选择的文件');
};

const submitUpload = () => {
  if (!uploadFile.value) {
    modal.msgWarning('请先选择要上传的简历文件');
    return;
  }
  uploadFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    uploadDialog.loading = true;
    try {
      const res = await uploadResume({
        talentId: talent.value.talentId!,
        file: uploadFile.value as File,
        sourceType: uploadForm.value.sourceType,
        remark: uploadForm.value.remark
      });
      // 同哈希文件会返回重复提示，页面如实展示
      if (res.data?.duplicateFileHint) {
        modal.msgWarning(res.data.duplicateFileHint);
      } else {
        modal.msgSuccess('上传成功，已创建新的简历版本');
      }
      uploadDialog.visible = false;
      await loadResume();
    } finally {
      uploadDialog.loading = false;
    }
  });
};

/* ------------------------------ 指定当前版本 ------------------------------ */

const setCurrent = async (row: HrTalentResumeVO) => {
  try {
    await modal.confirm(`是否将版本 v${row.versionNo} 指定为当前简历？旧版本文件不会被删除。`);
  } catch {
    return;
  }
  await setCurrentResume(row.resumeId!);
  modal.msgSuccess('已指定为当前简历');
  await loadResume();
};

/* ------------------------------ 受控下载 ------------------------------ */

const downloadDialog = reactive<{ visible: boolean; loading: boolean; row: Partial<HrTalentResumeVO> }>({
  visible: false,
  loading: false,
  row: {}
});
const downloadForm = ref<{ purpose: string }>({ purpose: '' });
const downloadRules: ElFormRules = {
  purpose: [{ required: true, message: '下载用途不能为空', trigger: 'blur' }]
};

const openDownload = (row: HrTalentResumeVO) => {
  downloadDialog.row = row;
  downloadForm.value.purpose = '';
  downloadDialog.visible = true;
};

/**
 * 解包二进制响应（拦截器对 blob 原样透传 AxiosResponse），并识别后端
 * 「HTTP 200 + JSON 体」的业务错误。
 *
 * 简历下载在 responseType=blob 下，业务异常（用途为空 / 无下载权限 / 人才不可见）
 * 会以 `application/json` 类型的 Blob 返回，必须解析出 msg 提示，**不能**当成简历文件保存。
 */
const resolveBlobResponse = async (resp: any): Promise<{ blob: Blob | null; errorMsg?: string }> => {
  const data = resp && resp.data !== undefined ? resp.data : resp;
  const blob = data instanceof Blob ? data : new Blob([data]);
  const contentType = blob.type || '';
  if (contentType.includes('application/json')) {
    const text = await blob.text();
    try {
      const payload = JSON.parse(text);
      return { blob: null, errorMsg: payload?.msg || '服务端拒绝了本次下载请求' };
    } catch {
      return { blob: null, errorMsg: text || '服务端拒绝了本次下载请求' };
    }
  }
  return { blob };
};

const submitDownload = () => {
  downloadFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    downloadDialog.loading = true;
    try {
      const resp = await downloadResume(downloadDialog.row.resumeId!, downloadForm.value.purpose);
      const { blob, errorMsg } = await resolveBlobResponse(resp);
      if (!blob) {
        modal.msgError(errorMsg || '简历下载失败');
        return;
      }
      saveBlob(blob, downloadDialog.row.originalName || `resume-${downloadDialog.row.resumeId}`);
      modal.msgSuccess('已开始下载，本次操作已记录审计');
      downloadDialog.visible = false;
    } finally {
      downloadDialog.loading = false;
    }
  });
};

/* ------------------------------ 版本列表 ------------------------------ */

const versionDialog = reactive<{ visible: boolean; loading: boolean }>({ visible: false, loading: false });
const versionList = ref<HrTalentResumeVO[]>([]);

const openVersions = async (row: HrTalentResumeVO) => {
  versionDialog.visible = true;
  versionDialog.loading = true;
  versionList.value = [];
  try {
    const res = await listResumeVersions(row.resumeId!);
    versionList.value = res.data || [];
  } finally {
    versionDialog.loading = false;
  }
};

/* ------------------------------ 解析任务与人工复核 ------------------------------ */

const reviewDialog = reactive<{
  visible: boolean;
  loading: boolean;
  submitting: boolean;
  taskId: string;
  remark: string;
  task: HrTalentParseTaskVO | null;
  results: (HrTalentParseResultVO & { accepted?: boolean })[];
}>({
  visible: false,
  loading: false,
  submitting: false,
  taskId: '',
  remark: '',
  task: null,
  results: []
});

/** 是否允许复核：必须已加载到解析任务 */
const canReview = computed(() => !!reviewDialog.task?.taskId);

/** 创建解析任务后立即查询任务详情（一期引擎未接入时任务会停在 pending/failed） */
const createParse = async (row: HrTalentResumeVO) => {
  try {
    await modal.confirm(
      '是否为该简历版本创建异步解析任务？解析不在请求内同步执行；解析引擎未接入时任务会停在 pending 或置 failed 并给出提示。'
    );
  } catch {
    return;
  }
  const res = await createParseTask(row.resumeId!);
  modal.msgSuccess(`已创建解析任务（任务ID ${res.data}）`);
  await loadResume();
  await openReviewDialog(row, res.data ? String(res.data) : undefined);
};

const openReviewDialog = async (row: HrTalentResumeVO, taskId?: string) => {
  reviewDialog.task = null;
  reviewDialog.results = [];
  reviewDialog.remark = '';
  reviewDialog.taskId = taskId || '';
  reviewDialog.visible = true;
  if (reviewDialog.taskId) {
    await loadParseTask();
  }
};

/** 查询任务并把候选项按「低置信度默认不勾选」初始化 */
const loadParseTask = async () => {
  if (!reviewDialog.taskId) {
    modal.msgWarning('请输入解析任务ID');
    return;
  }
  reviewDialog.loading = true;
  try {
    const res = await getParseTask(reviewDialog.taskId);
    reviewDialog.task = res.data || null;
    reviewDialog.results = (res.data?.results || []).map(item => ({
      ...item,
      // 低置信度/未标记默认勾选的字段默认不勾选，只有人工确认后才写入正式数据
      accepted: item.defaultSelected === true,
      // 人工修正值以解析标准化值作为初始值
      normalizedValue: item.normalizedValue ?? ''
    }));
    if (reviewDialog.task?.errorMessage) {
      modal.msgWarning(reviewDialog.task.errorMessage);
    }
  } finally {
    reviewDialog.loading = false;
  }
};

const toggleAllAccepted = (accepted: boolean) => {
  if (!canReview.value) {
    return;
  }
  reviewDialog.results = reviewDialog.results.map(item => ({ ...item, accepted }));
};

const submitReview = () => {
  if (!reviewDialog.task?.taskId) {
    return;
  }
  if (!reviewDialog.results.length) {
    modal.msgWarning('当前任务没有可复核的解析字段');
    return;
  }
  reviewDialog.submitting = true;
  confirmParseTask(reviewDialog.task.taskId, {
    remark: reviewDialog.remark,
    // 未勾选的候选项也必须回传，用于记录 rejected 复核结论
    items: reviewDialog.results.map(item => ({
      resultId: item.resultId!,
      accepted: item.accepted === true,
      normalizedValue: item.normalizedValue || undefined
    }))
  })
    .then(async res => {
      reviewDialog.task = res.data || reviewDialog.task;
      reviewDialog.results = (res.data?.results || []).map(item => ({
        ...item,
        accepted: item.reviewStatus === 'confirmed',
        normalizedValue: item.normalizedValue ?? ''
      }));
      modal.msgSuccess('复核已提交，仅勾选确认的字段写入了正式数据');
      await loadResume();
    })
    .finally(() => {
      reviewDialog.submitting = false;
    });
};

/* ------------------------------ 人才选择 ------------------------------ */

const talentPicker = reactive<{
  visible: boolean;
  loading: boolean;
  total: number;
  pageNum: number;
  pageSize: number;
  name?: string;
  talentNo?: string;
  rows: HrTalentProfileVO[];
}>({
  visible: false,
  loading: false,
  total: 0,
  pageNum: 1,
  pageSize: 10,
  name: undefined,
  talentNo: undefined,
  rows: []
});

const openTalentPicker = () => {
  talentPicker.name = undefined;
  talentPicker.talentNo = undefined;
  talentPicker.pageNum = 1;
  talentPicker.visible = true;
  searchTalent();
};

const searchTalent = async () => {
  talentPicker.loading = true;
  try {
    const res = await listProfile({
      pageNum: talentPicker.pageNum,
      pageSize: talentPicker.pageSize,
      name: talentPicker.name,
      talentNo: talentPicker.talentNo
    });
    talentPicker.rows = res.data?.rows || [];
    talentPicker.total = res.data?.total || 0;
  } finally {
    talentPicker.loading = false;
  }
};

const pickTalent = async (row: HrTalentProfileVO) => {
  talent.value = row;
  talentPicker.visible = false;
  await loadResume();
};
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.resume-shell {
  :deep(.el-tabs__content) {
    overflow: visible;
  }
}

.dialog-alert {
  margin-bottom: 12px;
}

.detail-panel {
  margin-bottom: 12px;
}

.review-remark {
  margin-top: 12px;
}
</style>
