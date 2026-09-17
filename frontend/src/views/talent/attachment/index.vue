<template>
  <div class="p-2 app-container talent-attachment-page">
    <el-card shadow="hover" class="search-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Attachment Console</span>
            <h3>简历与附件</h3>
            <p>附件按人才与类型维护版本；下载走服务端受控流，页面不展示任何对象存储地址。</p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['talent:attachment:upload']"
              type="primary"
              plain
              icon="Upload"
              :disabled="!talentId"
              @click="handleUpload"
            >
              上传新版本
            </el-button>
            <el-button icon="Refresh" @click="loadAttachments">刷新</el-button>
          </div>
        </div>
      </template>
      <el-form :inline="true" class="query-form">
        <el-form-item label="人才">
          <el-select
            v-model="talentId"
            filterable
            remote
            reserve-keyword
            clearable
            placeholder="输入姓名或编号检索人才"
            :remote-method="remoteTalent"
            :loading="talentLoading"
            style="width: 320px"
            @change="loadAttachments"
          >
            <el-option
              v-for="item in talentOptions"
              :key="String(item.talentId)"
              :label="`${item.name || '-'}（${item.talentNo || item.talentId}）`"
              :value="item.talentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="附件类型">
          <el-select v-model="queryType" placeholder="全部" clearable style="width: 180px">
            <el-option v-for="dict in tl_attachment_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" class="table-panel">
      <el-table v-loading="loading" border class="data-table" :data="filteredList">
        <el-table-column label="附件类型" align="center" width="120">
          <template #default="scope">
            <dict-tag :options="tl_attachment_type" :value="scope.row.attachmentType" />
          </template>
        </el-table-column>
        <el-table-column label="文件名" align="center" prop="originalName" show-overflow-tooltip />
        <el-table-column label="格式" align="center" prop="fileExt" width="90" />
        <el-table-column label="大小" align="center" width="110">
          <template #default="scope">{{ scope.row.fileSizeText || '-' }}</template>
        </el-table-column>
        <el-table-column label="版本" align="center" prop="version" width="80" />
        <el-table-column label="当前版本" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.isCurrent === '1' ? 'success' : 'info'">
              {{ scope.row.isCurrent === '1' ? '当前' : '历史' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="扫描状态" align="center" width="120">
          <template #default="scope">
            <el-tag :type="scanTagType(scope.row.scanStatus)">{{ scope.row.scanStatusLabel || scope.row.scanStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传人" align="center" prop="createByName" width="110" />
        <el-table-column label="上传时间" align="center" width="180">
          <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="下载（受控）" placement="top">
              <el-button
                v-hasPermi="['talent:attachment:download']"
                link
                type="primary"
                icon="Download"
                :disabled="scope.row.downloadable === false"
                @click="handleDownload(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                v-hasPermi="['talent:attachment:manage']"
                link
                type="primary"
                icon="Delete"
                @click="handleDelete(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="uploadDialog.visible" title="上传附件新版本" width="520px" append-to-body>
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="90px">
        <el-form-item label="附件类型" prop="attachmentType">
          <el-select v-model="uploadForm.attachmentType" placeholder="请选择附件类型" style="width: 100%">
            <el-option v-for="dict in tl_attachment_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择文件" prop="fileName">
          <el-upload
            ref="uploadRef"
            drag
            :limit="1"
            :auto-upload="false"
            :accept="acceptExt"
            :file-list="fileList"
            :on-change="handleFileChange"
            :on-exceed="handleExceed"
            :on-remove="handleFileRemove"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">仅允许 pdf / doc / docx / jpg / jpeg / png，单文件不超过 25MB。</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="uploading" @click="submitUpload">上 传</el-button>
          <el-button @click="uploadDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { TalentAttachmentVO } from '@/api/talent/attachment/types';
import { delAttachment, downloadAttachment, listAttachment, uploadAttachment } from '@/api/talent/attachment';
import { listTalent } from '@/api/talent/profile';
import type { TalentVO } from '@/api/talent/profile/types';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { blobValidate, parseTime } from '@/utils/ruoyi';
import { saveBlob } from '@/utils/save';

defineOptions({ name: 'TalentAttachment' });

const { tl_attachment_type } = toRefs<any>(useDict('tl_attachment_type'));

const allowedExt = ['pdf', 'doc', 'docx', 'jpg', 'jpeg', 'png'];
const acceptExt = allowedExt.map(ext => '.' + ext).join(',');
const maxFileSize = 25 * 1024 * 1024;

const talentId = ref<string | number | undefined>(undefined);
const talentOptions = ref<TalentVO[]>([]);
const talentLoading = ref(false);
const queryType = ref<string | undefined>(undefined);
const attachmentList = ref<TalentAttachmentVO[]>([]);
const loading = ref(false);

const uploadDialog = reactive<DialogOption>({ visible: false, title: '上传附件新版本' });
const uploadFormRef = ref<ElFormInstance>();
const uploadRef = ref<ElUploadInstance>();
const uploadForm = reactive({ attachmentType: 'RESUME', fileName: '' });
const uploadRules = {
  attachmentType: [{ required: true, message: '附件类型不能为空', trigger: 'change' }],
  fileName: [{ required: true, message: '请选择要上传的文件', trigger: 'change' }]
};
const fileList = ref<any[]>([]);
const currentFile = ref<File | null>(null);
const uploading = ref(false);

const filteredList = computed(() =>
  queryType.value ? attachmentList.value.filter(item => item.attachmentType === queryType.value) : attachmentList.value
);

const scanTagType = (status?: string) => {
  if (status === 'CLEAN') return 'success';
  if (status === 'INFECTED') return 'danger';
  if (status === 'FAILED') return 'warning';
  return 'info';
};

/** 人才远程检索（按姓名/编号） */
const remoteTalent = async (query: string) => {
  talentLoading.value = true;
  try {
    const res = await listTalent({ pageNum: 1, pageSize: 20, name: query });
    talentOptions.value = res.data?.rows || [];
  } finally {
    talentLoading.value = false;
  }
};

/** 按人才加载附件版本列表 */
const loadAttachments = async () => {
  if (!talentId.value) {
    attachmentList.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await listAttachment(talentId.value);
    attachmentList.value = res.data || [];
  } finally {
    loading.value = false;
  }
};

/** 附件下载：后端返回二进制流，前端按 blob 保存 */
const handleDownload = async (row: TalentAttachmentVO) => {
  if (!row.attachmentId) return;
  const data: any = await downloadAttachment(row.attachmentId);
  const blob = data instanceof Blob ? data : new Blob([data]);
  if (!blobValidate(blob)) {
    const text = await blob.text();
    let msg = '下载失败';
    try {
      msg = JSON.parse(text).msg || msg;
    } catch {
      msg = text || msg;
    }
    modal.msgError(msg);
    return;
  }
  saveBlob(blob, row.originalName || `attachment-${row.attachmentId}`);
};

const handleUpload = () => {
  uploadForm.attachmentType = 'RESUME';
  uploadForm.fileName = '';
  fileList.value = [];
  currentFile.value = null;
  uploadDialog.visible = true;
};

const handleFileChange = (file: UploadFile) => {
  const raw = (file as any).raw as File | undefined;
  const ext = (raw?.name || file.name || '').split('.').pop()?.toLowerCase() || '';
  if (!allowedExt.includes(ext)) {
    modal.msgError(`文件格式不正确，仅允许 ${allowedExt.join('/')}！`);
    fileList.value = [];
    currentFile.value = null;
    uploadRef.value?.clearFiles();
    return;
  }
  if ((raw?.size || 0) > maxFileSize) {
    modal.msgError('文件大小不能超过 25MB！');
    fileList.value = [];
    currentFile.value = null;
    uploadRef.value?.clearFiles();
    return;
  }
  currentFile.value = raw || null;
  uploadForm.fileName = raw?.name || file.name || '';
  fileList.value = [file];
  uploadFormRef.value?.validateField('fileName');
};

const handleFileRemove = () => {
  currentFile.value = null;
  uploadForm.fileName = '';
  fileList.value = [];
};

const handleExceed = () => {
  uploadRef.value?.clearFiles();
  fileList.value = [];
  currentFile.value = null;
  modal.msgError('一次只能上传一个文件，请先移除已选文件');
};

const submitUpload = () => {
  uploadFormRef.value?.validate(async (valid: boolean) => {
    if (!valid || !currentFile.value || !talentId.value) return;
    uploading.value = true;
    try {
      await uploadAttachment({
        talentId: talentId.value,
        attachmentType: uploadForm.attachmentType,
        file: currentFile.value
      });
      modal.msgSuccess('上传成功');
      uploadDialog.visible = false;
      await loadAttachments();
    } finally {
      uploading.value = false;
    }
  });
};

const handleDelete = async (row: TalentAttachmentVO) => {
  if (!row.attachmentId) return;
  await modal.confirm(`是否确认删除附件「${row.originalName || row.attachmentId}」？删除后将无法下载该版本。`);
  await delAttachment(row.attachmentId);
  modal.msgSuccess('删除成功');
  await loadAttachments();
};

onMounted(async () => {
  await remoteTalent('');
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;
</style>
