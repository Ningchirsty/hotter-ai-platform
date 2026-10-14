<template>
  <div class="p-2">
    <el-alert
      type="info"
      :closable="false"
      class="mb-2"
      title="工具只有注册了执行器才可执行；未接入的一律显示「已登记 待接入」，不得当作可用。变更业务数据/外部发送的工具调用前必须人工确认。"
    />

    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span class="title">可绑定工具</span>
          <div>
            <el-button v-hasPermi="['aig:tool:manage']" type="primary" size="small" @click="openDefinition">
              新建工具定义
            </el-button>
            <el-button v-hasPermi="['aig:tool:manage']" size="small" class="ml-2" @click="openVersion">
              新增版本
            </el-button>
            <el-button size="small" class="ml-2" @click="load">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tools" border>
        <el-table-column label="工具编码" prop="toolCode" width="200" />
        <el-table-column label="名称" prop="toolName" min-width="160" />
        <el-table-column label="版本" prop="version" width="100" />
        <el-table-column label="操作类型" width="140">
          <template #default="{ row }">{{ operationTypeLabel(row.operationType) }}</template>
        </el-table-column>
        <el-table-column label="需人工确认" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.requiresConfirmation" type="warning">需要</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="接入状态" width="140">
          <template #default="{ row }">
            <el-tag :type="executableTagType(row.executable)">{{ executableText(row.executable) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所需权限点" width="200">
          <template #default="{ row }">{{ permissionPointText(row.permissionPoint) }}</template>
        </el-table-column>
        <el-table-column label="等级上限" prop="dataLevelMax" width="120" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 工具契约详情（设计 §6.5 工具卡片：用途 / 输入输出 / 数据范围 / 责任人 / 接入状态） -->
    <el-dialog v-model="detailVisible" title="工具契约详情" width="720px" append-to-body>
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="工具">{{ detail.toolName || '—' }}（{{ detail.toolCode }}）</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">
          {{ operationTypeLabel(detail.operationType) }}
          <el-tag v-if="detail.requiresConfirmation" type="warning" size="small">需人工确认</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="接入状态">
          <el-tag :type="executableTagType(detail.executable)">{{ executableText(detail.executable) }}</el-tag>
          <span class="text-gray-500"> 执行器：{{ executorText(detail.executorRef) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="用途" :span="2">{{ detail.description || '（未声明用途）' }}</el-descriptions-item>
        <el-descriptions-item label="数据范围" :span="2">{{ dataPolicyText(detail.dataPolicy) }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ detail.ownerId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="等级上限">{{ detail.dataLevelMax || '不限' }}</el-descriptions-item>
        <el-descriptions-item label="所需权限点">
          {{ permissionPointText(detail.permissionPoint) }}
        </el-descriptions-item>
        <el-descriptions-item label="超时/重试">
          {{ timeoutText(detail.timeoutMs, detail.maxAttempts) }}
        </el-descriptions-item>
        <el-descriptions-item label="输入 Schema" :span="2">
          <pre class="schema-block">{{ schemaText(detail.inputSchema) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="输出 Schema" :span="2">
          <pre class="schema-block">{{ schemaText(detail.outputSchema) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新建工具定义 -->
    <el-dialog v-model="definitionVisible" title="新建工具定义" width="520px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="工具编码" required>
          <el-input v-model="definitionForm.toolCode" placeholder="如 product_fact_query" />
        </el-form-item>
        <el-form-item label="工具名称" required>
          <el-input v-model="definitionForm.toolName" />
        </el-form-item>
        <el-form-item label="用途说明">
          <el-input v-model="definitionForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="数据策略">
          <el-input v-model="definitionForm.dataPolicy" placeholder="不含密钥；密钥由平台配置与执行器管理" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="definitionVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doCreateDefinition">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新增版本 -->
    <el-dialog v-model="versionVisible" title="新增工具版本（执行契约）" width="600px" append-to-body>
      <el-form label-width="120px">
        <el-form-item label="工具编码" required>
          <el-select v-model="versionForm.toolCode" filterable allow-create style="width: 100%">
            <el-option v-for="code in toolCodes" :key="code" :label="code" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号" required>
          <el-input v-model="versionForm.version" placeholder="如 1.0.0" />
        </el-form-item>
        <el-form-item label="执行器引用">
          <el-input v-model="versionForm.executorRef" placeholder="可空=仅登记、待接入（不可执行）" />
        </el-form-item>
        <el-form-item label="操作类型" required>
          <el-select v-model="versionForm.operationType" style="width: 100%">
            <el-option
              v-for="item in OPERATION_TYPE_OPTIONS"
              :key="item.code"
              :label="item.label"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="超时(ms)">
          <el-input-number v-model="versionForm.timeoutMs" :min="1000" :step="1000" />
        </el-form-item>
        <el-form-item label="最大尝试次数">
          <el-input-number v-model="versionForm.maxAttempts" :min="1" :max="5" />
        </el-form-item>
        <el-form-item label="所需权限点">
          <el-input v-model="versionForm.permissionPoint" placeholder="可空=不额外要求权限" />
        </el-form-item>
        <el-form-item label="等级上限">
          <el-input v-model="versionForm.dataLevelMax" placeholder="可空=不限" />
        </el-form-item>
      </el-form>
      <el-alert v-if="versionHint" type="warning" :closable="false" :title="versionHint" />
      <template #footer>
        <el-button @click="versionVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doAddVersion">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { addToolVersion, createToolDefinition, getToolVersionDetail, listTools } from '@/api/aigov/tool';
import type { AigToolDefinitionForm, AigToolDetailVO, AigToolVersionForm, AigToolVersionVO } from '@/api/aigov/tool/types';
import modal from '@/plugins/modal';
import {
  OPERATION_TYPE_OPTIONS,
  confirmationHint,
  dataPolicyText,
  definitionValidation,
  executableTagType,
  executableText,
  executorText,
  operationTypeLabel,
  permissionPointHint,
  permissionPointText,
  schemaText,
  timeoutText,
  versionValidation
} from './presentation';

defineOptions({ name: 'AigTool' });

const tools = ref<AigToolVersionVO[]>([]);
const saving = ref(false);
const definitionVisible = ref(false);
const versionVisible = ref(false);
const definitionForm = ref<AigToolDefinitionForm>({});
const versionForm = ref<AigToolVersionForm>({ operationType: 'READ_ONLY_QUERY', timeoutMs: 30000, maxAttempts: 1 });

const toolCodes = computed(() => {
  const set = new Set<string>();
  for (const item of tools.value) {
    if (item.toolCode) {
      set.add(item.toolCode);
    }
  }
  return Array.from(set);
});

const versionHint = computed(() =>
  [confirmationHint(versionForm.value.operationType), permissionPointHint(versionForm.value.permissionPoint)]
    .filter(Boolean)
    .join(' ')
);

const load = async () => {
  const res = await listTools();
  tools.value = res.data || [];
};

/** 契约详情：列表不带 Schema 全文，点开才取（否则"看清单"等于把每份契约都拉一遍） */
const detailVisible = ref(false);
const detail = ref<AigToolDetailVO>();

const openDetail = async (row: AigToolVersionVO) => {
  if (!row.toolCode || !row.version) {
    modal.msgWarning('这一行缺少工具编码或版本，无法取详情');
    return;
  }
  try {
    const res = await getToolVersionDetail(row.toolCode, row.version);
    detail.value = res.data;
    detailVisible.value = true;
  } catch (e: any) {
    modal.alertError(e?.message || '读取工具契约详情失败');
  }
};

const openDefinition = () => {
  definitionForm.value = {};
  definitionVisible.value = true;
};

const openVersion = () => {
  versionForm.value = { operationType: 'READ_ONLY_QUERY', timeoutMs: 30000, maxAttempts: 1 };
  versionVisible.value = true;
};

const doCreateDefinition = async () => {
  const error = definitionValidation(definitionForm.value);
  if (error) {
    modal.msgWarning(error);
    return;
  }
  saving.value = true;
  try {
    await createToolDefinition(definitionForm.value);
    modal.msgSuccess('已登记工具定义');
    definitionVisible.value = false;
    await load();
  } catch (e: any) {
    modal.alertError(e?.message || '登记失败');
  } finally {
    saving.value = false;
  }
};

const doAddVersion = async () => {
  const error = versionValidation(versionForm.value);
  if (error) {
    modal.msgWarning(error);
    return;
  }
  saving.value = true;
  try {
    await addToolVersion(versionForm.value);
    modal.msgSuccess('已新增版本');
    versionVisible.value = false;
    await load();
  } catch (e: any) {
    modal.alertError(e?.message || '新增失败');
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  load();
});
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-weight: 600;
}

.mb-2 {
  margin-bottom: 12px;
}

.ml-2 {
  margin-left: 8px;
}

.schema-block {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: inherit;
  font-size: 12px;
}
</style>
