<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="960px"
    append-to-body
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @closed="reset"
  >
    <!-- 第一步：选文件 -->
    <div v-if="step === 'pick'">
      <el-alert
        class="tip"
        type="info"
        :closable="false"
        show-icon
        :title="`先下载模板、按列填写，再上传预检；预检不会写入任何数据，确认后才导入。`"
      />
      <div class="pick-bar">
        <el-button icon="Download" @click="downloadTemplate">下载导入模板</el-button>
        <span class="muted">模板第二个工作表「填写说明」列出了每列是否必填与填写示例</span>
      </div>
      <el-upload
        drag
        :limit="1"
        :auto-upload="false"
        :file-list="fileList"
        :on-change="handleFileChange"
        :on-remove="() => handleFileChange(null)"
        :on-exceed="handleExceed"
        accept=".xlsx,.xls"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx / .xls，单次最多 2000 行、不超过 5MB</div>
        </template>
      </el-upload>
    </div>

    <!-- 第二步：预检结果 -->
    <div v-else-if="step === 'preview'">
      <el-alert
        class="tip"
        :type="preview && preview.errorCount ? 'warning' : 'success'"
        :closable="false"
        show-icon
        :title="preview?.message || ''"
      />
      <el-descriptions class="mt" :column="4" border size="small">
        <el-descriptions-item label="批次编号">{{ preview?.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="文件">{{ preview?.sourceFileName }}</el-descriptions-item>
        <el-descriptions-item label="数据行数">{{ preview?.totalCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="可导入 / 错误">
          <span class="ok">{{ preview?.validCount ?? 0 }}</span> /
          <span class="err">{{ preview?.errorCount ?? 0 }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <template v-if="(preview?.issues || []).length">
        <el-divider content-position="left">问题清单（错误行不会导入，提示行仍会导入）</el-divider>
        <el-table border size="small" max-height="280" :data="preview?.issues">
          <el-table-column label="行号" align="center" prop="rowNo" width="80" />
          <el-table-column label="级别" align="center" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.severity === 'error' ? 'danger' : 'warning'" size="small">
                {{ scope.row.severity === 'error' ? '错误' : '提示' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="字段" align="center" prop="fieldName" width="150" show-overflow-tooltip />
          <el-table-column label="原始值" align="center" prop="rawValue" width="160" show-overflow-tooltip />
          <el-table-column label="说明" align="left" prop="errorMessage" show-overflow-tooltip />
        </el-table>
      </template>

      <template v-if="(preview?.preview || []).length">
        <el-divider content-position="left">解析预览（前 {{ preview?.preview?.length }} 行，确认列有没有读串）</el-divider>
        <el-table border size="small" max-height="240" :data="preview?.preview">
          <el-table-column label="行号" align="center" prop="rowNo" width="80" />
          <el-table-column
            v-for="col in preview?.columns || []"
            :key="col.header"
            :label="col.header"
            align="center"
            :prop="col.header"
            min-width="120"
            show-overflow-tooltip
          />
        </el-table>
      </template>
    </div>

    <!-- 第三步：导入结果 -->
    <div v-else>
      <el-alert
        class="tip"
        :type="result?.status === 'success' ? 'success' : result?.status === 'failed' ? 'error' : 'warning'"
        :closable="false"
        show-icon
        :title="`批次 ${result?.batchNo}：${result?.statusLabel}`"
      />
      <el-descriptions class="mt" :column="3" border size="small">
        <el-descriptions-item label="数据行数">{{ result?.totalCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="成功">
          <span class="ok">{{ result?.successCount ?? 0 }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="失败">
          <span class="err">{{ result?.failureCount ?? 0 }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <ul v-if="(result?.messages || []).length" class="msg-list">
        <li v-for="(m, i) in result?.messages" :key="i">{{ m }}</li>
      </ul>
      <template v-if="(result?.issues || []).length">
        <el-divider content-position="left">未导入的行</el-divider>
        <el-table border size="small" max-height="240" :data="result?.issues">
          <el-table-column label="行号" align="center" prop="rowNo" width="80" />
          <el-table-column label="字段" align="center" prop="fieldName" width="150" show-overflow-tooltip />
          <el-table-column label="说明" align="left" prop="errorMessage" show-overflow-tooltip />
        </el-table>
      </template>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button v-if="step === 'pick'" type="primary" :loading="previewing" :disabled="!file" @click="doPreview">
          开始预检
        </el-button>
        <template v-else-if="step === 'preview'">
          <el-button :loading="confirming" :disabled="!preview?.validCount" @click="doConfirm">
            确认导入{{ preview?.validCount ? `（${preview.validCount} 行）` : '' }}
          </el-button>
          <el-button @click="backToPick">重新上传</el-button>
          <el-button type="danger" plain @click="doCancel">取消批次</el-button>
        </template>
        <el-button v-else type="primary" @click="close">完 成</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { createRecruitImportApi } from '@/api/hrtalent/import';
import type { RecruitImportPreviewVO, RecruitImportResultVO } from '@/api/hrtalent/import/types';
import modal from '@/plugins/modal';
import { download } from '@/utils/request';

defineOptions({ name: 'RecruitImportDialog' });

const props = defineProps<{
  /** 是否显示 */
  modelValue: boolean;
  /** 弹窗标题 */
  title: string;
  /** 业务接口前缀，如 /recruit/standards */
  basePath: string;
  /** 模板文件名 */
  templateName: string;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  /** 导入完成，父页面据此刷新列表 */
  (e: 'done'): void;
}>();

const api = createRecruitImportApi(props.basePath);

type Step = 'pick' | 'preview' | 'result';
const step = ref<Step>('pick');
const file = ref<File | null>(null);
const fileList = ref<any[]>([]);
const previewing = ref(false);
const confirming = ref(false);
const preview = ref<RecruitImportPreviewVO | null>(null);
const result = ref<RecruitImportResultVO | null>(null);

/** 选择文件（auto-upload=false，这里只抓住原始 File） */
const handleFileChange = (uploadFile: any) => {
  const raw: File | undefined = uploadFile?.raw;
  file.value = raw || null;
  fileList.value = raw ? [uploadFile] : [];
};

/** 超出 limit=1 时直接替换，避免用户以为没选上 */
const handleExceed = (files: File[]) => {
  fileList.value = [];
  file.value = files[0] || null;
  if (file.value) {
    fileList.value = [{ name: file.value.name, raw: file.value }];
  }
};

/** 下载模板 */
const downloadTemplate = () => {
  download(`${props.basePath}/importTemplate`, {}, props.templateName);
};

/** 上传预检 */
const doPreview = async () => {
  if (!file.value) {
    modal.msgWarning('请先选择要导入的文件');
    return;
  }
  previewing.value = true;
  try {
    const res = await api.importPreview(file.value);
    preview.value = res.data || null;
    step.value = 'preview';
    if (!preview.value?.totalCount) {
      modal.msgWarning('文件里没有解析到数据行，请检查是否只填了表头');
    }
  } finally {
    previewing.value = false;
  }
};

/** 确认导入 */
const doConfirm = async () => {
  if (!preview.value?.batchId) {
    return;
  }
  confirming.value = true;
  try {
    const res = await api.importConfirm(preview.value.batchId);
    result.value = res.data || null;
    step.value = 'result';
    emit('done');
  } finally {
    confirming.value = false;
  }
};

/** 取消批次并回到选文件 */
const doCancel = async () => {
  if (!preview.value?.batchId) {
    return;
  }
  await modal.confirm('取消后该批次不会导入任何数据，是否继续？');
  await api.importCancel(preview.value.batchId);
  modal.msgSuccess('已取消该批次');
  backToPick();
};

/** 回到选文件 */
const backToPick = () => {
  step.value = 'pick';
  preview.value = null;
  result.value = null;
  file.value = null;
  fileList.value = [];
};

/** 关闭 */
const close = () => {
  emit('update:modelValue', false);
};

/** 关闭后复位，避免下次打开残留上一次的结果 */
const reset = () => {
  backToPick();
};
</script>

<style lang="scss" scoped>
.pick-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.mt {
  margin-top: 12px;
}

.ok {
  color: var(--el-color-success);
  font-weight: 600;
}

.err {
  color: var(--el-color-danger);
  font-weight: 600;
}

.msg-list {
  margin: 10px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}

.dialog-footer {
  text-align: right;
}
</style>
