<template>
  <div class="p-2 app-container hrtalent-audit-page">
    <PageHeading
      title="敏感操作审计"
      subtitle="敏感操作审计记录（只读、追加型）：电话明文查看、背调明细查看、附件预览/下载/删除、数据导出与管理员例外跳转都会自动留痕"
      module="hrtalent"
    />

    <!-- §8.7 / §15.3：审计记录本身不含任何敏感明文 -->
    <el-alert
      class="page-alert"
      type="info"
      :closable="false"
      show-icon
      title="审计记录为脱敏数据：不含电话明文、简历正文与背调明细，只记录操作人、用途、结果、IP 与时间。审计表为追加型，本页只提供查询与导出，不提供任何修改或删除操作。"
    />

    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>审计检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="事件类型" prop="eventType">
            <el-select v-model="queryParams.eventType" placeholder="请选择" clearable filterable style="width: 170px">
              <el-option v-for="item in eventTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务类型" prop="bizType">
            <el-select v-model="queryParams.bizType" placeholder="请选择" clearable filterable style="width: 150px">
              <el-option v-for="item in bizTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务对象ID" prop="bizId">
            <el-input v-model="queryParams.bizId" placeholder="业务对象ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="操作人ID" prop="operatorId">
            <el-input v-model="queryParams.operatorId" placeholder="用户ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="操作结果" prop="result">
            <el-select v-model="queryParams.result" placeholder="请选择" clearable style="width: 140px">
              <el-option v-for="item in resultOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="事件时间">
            <el-date-picker
              v-model="eventTimeRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              range-separator="-"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 340px"
            />
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
            <span class="panel-kicker">Sensitive Audit</span>
            <h3>审计记录（只读）</h3>
            <p>
              共 {{ total }} 条记录。导出会按<strong>当前筛选条件</strong>生成文件（保证「看到什么就导出什么」），
              且导出动作本身也会写入一条 <code>export</code> 审计；文件名取响应头 <code>Content-Disposition</code> /
              <code>download-filename</code>。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['recruit:audit:export']"
              type="warning"
              plain
              icon="Download"
              :loading="exporting"
              @click="handleExport"
            >
              导出
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="auditList">
        <el-table-column type="expand">
          <template #default="scope">
            <div class="audit-detail">
              <div class="audit-detail-row">
                <span class="audit-detail-title">附加明细快照（脱敏 JSON）</span>
                <el-tag size="small" type="info">不含联系方式与背调明细</el-tag>
              </div>
              <pre class="snapshot-text">{{ formatSnapshot(scope.row.detailJson) }}</pre>
              <div class="audit-detail-row">
                <span class="audit-detail-title">User-Agent</span>
                <span class="audit-detail-value">{{ scope.row.userAgent || '-' }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="审计ID" align="center" prop="auditId" width="100" />
        <el-table-column label="事件类型" align="center" width="140">
          <template #default="scope">{{ eventTypeText(scope.row.eventType) }}</template>
        </el-table-column>
        <el-table-column label="业务类型" align="center" width="110">
          <template #default="scope">{{ bizTypeText(scope.row.bizType) }}</template>
        </el-table-column>
        <el-table-column label="业务对象ID" align="center" prop="bizId" width="120" />
        <el-table-column label="操作人" align="center" width="130" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.operatorName || scope.row.operatorId || '-' }}</template>
        </el-table-column>
        <el-table-column label="用途 / 事由" prop="purpose" min-width="180" show-overflow-tooltip />
        <el-table-column label="结果" align="center" width="100">
          <template #default="scope">
            <el-tag :type="resultTagType(scope.row.result)" size="small">{{ resultText(scope.row.result) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="IP" align="center" prop="ip" width="140" show-overflow-tooltip />
        <el-table-column label="事件时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.eventTime) || scope.row.eventTimeText || '-' }}</template>
        </el-table-column>
        <el-table-column label="记录人" align="center" width="110">
          <template #default="scope">{{ scope.row.createByName || scope.row.createBy || '-' }}</template>
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
  </div>
</template>

<script setup lang="ts">
import { exportAudit, listAudit } from '@/api/hrtalent/audit';
import {
  AUDIT_BIZ_TYPE_TEXT,
  AUDIT_EVENT_TYPE_TEXT,
  AUDIT_RESULT_TEXT
} from '@/api/hrtalent/audit/types';
import type { HrSensitiveAuditQuery, HrSensitiveAuditVO } from '@/api/hrtalent/audit/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { parseTime } from '@/utils/ruoyi';
import { saveBlob } from '@/utils/save';

defineOptions({ name: 'HrTalentAudit' });

const auditList = ref<HrSensitiveAuditVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const exporting = ref(false);
const queryFormRef = ref<ElFormInstance>();
const eventTimeRange = ref<[string, string] | null>(null);

const data = reactive<{ queryParams: HrSensitiveAuditQuery }>({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    eventType: undefined,
    bizType: undefined,
    bizId: undefined,
    operatorId: undefined,
    result: undefined,
    eventTimeBegin: undefined,
    eventTimeEnd: undefined
  }
});
const { queryParams } = toRefs(data);
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  pageSizeKey: 'pageSize',
  initialPageSize: 10,
  resetExtras: () => {
    eventTimeRange.value = null;
  },
  afterReset: () => handleQuery()
});

/** 下拉选项：编码来自后端 SensitiveAuditRecorder 常量（无字典组，中文为镜像映射） */
const eventTypeOptions = Object.entries(AUDIT_EVENT_TYPE_TEXT).map(([value, label]) => ({ value, label }));
const bizTypeOptions = Object.entries(AUDIT_BIZ_TYPE_TEXT).map(([value, label]) => ({ value, label }));
const resultOptions = Object.entries(AUDIT_RESULT_TEXT).map(([value, label]) => ({ value, label }));

const eventTypeText = (code?: string) => (code ? AUDIT_EVENT_TYPE_TEXT[code] || code : '-');
const bizTypeText = (code?: string) => (code ? AUDIT_BIZ_TYPE_TEXT[code] || code : '-');
const resultText = (code?: string) => (code ? AUDIT_RESULT_TEXT[code] || code : '-');
const resultTagType = (code?: string) => {
  if (code === 'success') return 'success';
  if (code === 'denied') return 'warning';
  if (code === 'failed') return 'danger';
  return 'info';
};

/** 明细快照渲染：后端下发 JSON 字符串，这里格式化展示；非 JSON 原样输出 */
const formatSnapshot = (value?: string | null) => {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
};

/** 带当前筛选条件的查询（列表与导出共用，保证口径一致；导出接口不接受分页参数） */
const buildQuery = (): Omit<HrSensitiveAuditQuery, 'pageNum' | 'pageSize'> => {
  const { pageNum, pageSize, ...filters } = queryParams.value;
  return filters;
};

const getList = async () => {
  await withLoading(async () => {
    queryParams.value.eventTimeBegin = eventTimeRange.value?.[0] || undefined;
    queryParams.value.eventTimeEnd = eventTimeRange.value?.[1] || undefined;
    const res = await listAudit(queryParams.value);
    auditList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/**
 * 解包二进制响应，并识别后端「HTTP 200 + JSON 体」的业务错误
 * （无导出权限 / 超出导出上限等情况会以 application/json 的 Blob 返回）。
 */
const resolveBlobResponse = async (
  resp: any
): Promise<{ blob: Blob | null; errorMsg?: string; fileName?: string }> => {
  const data = resp && resp.data !== undefined ? resp.data : resp;
  const headers = (resp && resp.headers) || {};
  // 后端 FileUtils.setAttachmentResponseHeader 会同时写入 download-filename 与 Content-Disposition
  const headerFileName = headers['download-filename']
    ? decodeURIComponent(String(headers['download-filename']))
    : parseContentDispositionFileName(headers['content-disposition']);
  const blob = data instanceof Blob ? data : new Blob([data]);
  const contentType = blob.type || '';
  if (contentType.includes('application/json')) {
    const text = await blob.text();
    try {
      const payload = JSON.parse(text);
      return { blob: null, errorMsg: payload?.msg || '服务端拒绝了本次导出请求' };
    } catch {
      return { blob: null, errorMsg: text || '服务端拒绝了本次导出请求' };
    }
  }
  return { blob, fileName: headerFileName };
};

/** 从 Content-Disposition 解析文件名（优先 filename*=utf-8''，其次 filename=） */
function parseContentDispositionFileName(disposition?: string): string | undefined {
  if (!disposition) {
    return undefined;
  }
  const utf8Match = /filename\*=utf-8''([^;]+)/i.exec(disposition);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1].trim().replace(/^"|"$/g, ''));
    } catch {
      return utf8Match[1].trim();
    }
  }
  const plainMatch = /filename=([^;]+)/i.exec(disposition);
  if (plainMatch?.[1]) {
    return plainMatch[1].trim().replace(/^"|"$/g, '');
  }
  return undefined;
}

/** 导出当前筛选条件下的审计记录（导出动作本身也会写入 export 审计） */
const handleExport = async () => {
  exporting.value = true;
  try {
    const resp = await exportAudit(buildQuery());
    const { blob, errorMsg, fileName } = await resolveBlobResponse(resp);
    if (!blob) {
      modal.msgError(errorMsg || '审计导出失败');
      return;
    }
    saveBlob(blob, fileName || `audit_${new Date().getTime()}.xlsx`);
    modal.msgSuccess('已开始导出，本次导出已记录审计');
  } finally {
    exporting.value = false;
  }
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.page-alert {
  margin-bottom: 12px;
}

.audit-detail {
  padding: 4px 8px;
}

.audit-detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.audit-detail-title {
  font-size: 12px;
  font-weight: 600;
}

.audit-detail-value {
  font-size: 12px;
  color: var(--app-text-muted);
  word-break: break-all;
}

.snapshot-text {
  margin: 0 0 8px;
  max-height: 220px;
  overflow: auto;
  padding: 8px;
  border-radius: 6px;
  background: var(--app-surface-bg);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
