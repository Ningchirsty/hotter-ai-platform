<template>
  <div class="p-2">
    <el-alert
      type="info"
      :closable="false"
      class="mb-2"
      title="知识资料按企业禁区处理：集合默认 STRICT；「上传完成」不等于「知识可用」——只有处理到「已就绪」的版本才会被检索。"
    />

    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span class="title">知识集合</span>
          <div>
            <el-button v-hasPermi="['aig:knowledge:collection:create']" type="primary" size="small" @click="openCollection">
              新建集合
            </el-button>
            <el-button size="small" class="ml-2" @click="loadCollections">刷新</el-button>
            <el-button size="small" class="ml-2" @click="openVersion">版本处理</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="collectionsLoading" :data="collections" border>
        <template #empty>
          <AigEmptyState
            icon="Collection"
            title="还没有知识集合"
            hint="知识集合是数据等级与组织范围的边界：先建集合，再往里登记文档。"
          >
            <el-button type="primary" size="small" @click="createVisible = true">新建知识集合</el-button>
            <el-button size="small" @click="loadCollections">刷新</el-button>
          </AigEmptyState>
        </template>
        <el-table-column label="编码" prop="collectionCode" width="180" />
        <el-table-column label="名称" prop="collectionName" min-width="160" />
        <el-table-column label="数据等级" prop="dataLevel" width="120" />
        <el-table-column label="组织范围" prop="orgScope" min-width="140" />
        <el-table-column label="责任人" prop="ownerId" width="180" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="selectCollection(row)">看文档</el-button>
            <el-button link type="primary" @click="openRetrieval(row)">检索测试</el-button>
            <el-button v-hasPermi="['aig:knowledge:document:list']" link type="primary" @click="openReferences(row)">
              看引用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="mt-2">
      <template #header>
        <div class="header">
          <span class="title">知识文档{{ currentCollection ? '：' + currentCollection.collectionName : '' }}</span>
          <div>
            <el-button
              v-hasPermi="['aig:knowledge:document:upload']"
              type="primary"
              size="small"
              :disabled="!currentCollection"
              @click="openDocument"
            >
              登记文档
            </el-button>
            <el-button size="small" class="ml-2" @click="loadDocuments">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="documentsLoading" :data="documents" border>
        <template #empty>
          <AigEmptyState
            icon="Document"
            title="这个集合里还没有文档"
            hint="登记文档后，还要点「上传并解析索引」才会进入检索；只登记不解析是查不到的。"
          >
            <el-button
              v-hasPermi="['aig:knowledge:document:upload']"
              type="primary"
              size="small"
              :disabled="!currentCollection"
              @click="openDocument"
            >
              登记文档
            </el-button>
          </AigEmptyState>
        </template>
        <el-table-column label="文档ID" width="200">
          <template #default="{ row }"><AigLongId :id="row.documentId" :keep="6" /></template>
        </el-table-column>
        <el-table-column label="文档名" prop="sourceName" min-width="200" />
        <el-table-column label="来源类型" width="120">
          <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="责任人" prop="ownerId" width="180" />
      </el-table>
    </el-card>

    <!-- 新建集合 -->
    <el-dialog v-model="collectionVisible" title="新建知识集合" width="520px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="集合编码" required>
          <el-input v-model="collectionForm.collectionCode" placeholder="如 product_docs（租户内唯一）" />
        </el-form-item>
        <el-form-item label="集合名称" required>
          <el-input v-model="collectionForm.collectionName" />
        </el-form-item>
        <el-form-item label="数据等级">
          <el-select v-model="collectionForm.dataLevel" style="width: 100%">
            <el-option v-for="item in DATA_LEVEL_OPTIONS" :key="item.code" :label="item.label" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="组织范围">
          <el-input v-model="collectionForm.orgScope" placeholder="可空=不限" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="collectionVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doCreateCollection">确定</el-button>
      </template>
    </el-dialog>

    <!-- 登记文档 -->
    <el-dialog v-model="documentVisible" title="登记知识文档" width="560px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="文档名" required>
          <el-input v-model="documentForm.sourceName" placeholder="如 产品手册.pdf（按扩展名识别类型）" />
        </el-form-item>
        <el-form-item label="原文 SHA-256" required>
          <el-input v-model="documentForm.sha256" placeholder="64 位十六进制（上传链路计算）" />
        </el-form-item>
        <el-form-item label="存储引用">
          <el-input v-model="documentForm.sourceRef" placeholder="对象键（正文不落库）" />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="documentForm.version" placeholder="可空=1" />
        </el-form-item>
      </el-form>
      <el-alert
        type="warning"
        :closable="false"
        title="登记只产生「已上传」；要能被检索，还需处理到「已就绪」。同一文件在不同业务范围不会自动共享授权。"
      />
      <template #footer>
        <el-button @click="documentVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doRegisterDocument">确定</el-button>
      </template>
    </el-dialog>

    <!-- 版本处理 -->
    <el-dialog v-model="versionVisible" title="版本处理" width="560px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="版本ID" required>
          <el-input v-model="versionId" @blur="loadVersion" />
        </el-form-item>
      </el-form>
      <el-descriptions v-if="version" :column="1" border>
        <el-descriptions-item label="版本">{{ version.version }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(version.status)">{{ statusLabel(version.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="生效开始">{{ version.effectiveFrom || '—' }}</el-descriptions-item>
        <el-descriptions-item label="生效结束">{{ version.effectiveTo || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="indexingResult"
        :type="indexingResult.status === 'FAILED' ? 'error' : 'success'"
        :closable="false"
        class="mb-2"
        show-icon
        :title="indexingSummary(indexingResult)"
      />
      <el-alert
        v-if="chunksLoaded"
        type="info"
        :closable="false"
        class="mb-2"
        show-icon
        :title="chunkSummary(chunks)"
      />
      <el-table v-if="chunks.length" :data="chunks" border size="small" max-height="260">
        <el-table-column label="段序" prop="ordinal" width="70" />
        <el-table-column label="定位" prop="locator" width="140" show-overflow-tooltip />
        <el-table-column label="预览（受限长度）" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ chunkExcerptText(row) }}</template>
        </el-table-column>
        <el-table-column label="等级" prop="dataLevel" width="110" />
        <el-table-column label="索引" width="100">
          <template #default="{ row }">
            <el-tag :type="row.indexed ? 'success' : 'info'" size="small">{{ chunkIndexText(row) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="versionVisible = false">关闭</el-button>
        <el-button
          v-hasPermi="['aig:knowledge:retrieval:test']"
          :disabled="!version"
          :loading="chunksLoading"
          @click="loadChunks"
        >
          看分段（预览）
        </el-button>
        <el-button
          v-hasPermi="['aig:knowledge:document:manage']"
          :disabled="!version || !canAdvance(version.status)"
          :loading="saving"
          @click="doAdvance"
        >
          推进一阶段
        </el-button>
        <el-upload
          v-hasPermi="['aig:knowledge:document:manage']"
          :show-file-list="false"
          :http-request="(option: any) => doIndexVersion(option)"
        >
          <el-button
            type="primary"
            :disabled="!version"
            :loading="indexing"
          >
            上传并解析索引
          </el-button>
        </el-upload>
        <el-button
          v-hasPermi="['aig:knowledge:document:manage']"
          type="danger"
          :disabled="!version || !canRetire(version.status)"
          :loading="saving"
          @click="doRetire"
        >
          退役
        </el-button>
      </template>
    </el-dialog>

    <!-- 谁在引用这个集合（设计 §6.4「引用该版本的 Agent」）：退役前先看影响面 -->
    <el-dialog
      v-model="referencesVisible"
      :title="'谁在引用：' + (referencesCollection?.collectionName || '')"
      width="760px"
      append-to-body
    >
      <el-alert type="warning" :closable="false" class="mb-2" show-icon :title="referenceSummary(references)" />
      <el-table :data="references" border size="small" max-height="320">
        <el-table-column label="Agent" min-width="200">
          <template #default="{ row }">{{ referenceAgentText(row) }}</template>
        </el-table-column>
        <el-table-column label="版本状态" prop="releaseStatus" width="130" show-overflow-tooltip />
        <el-table-column label="收窄范围" min-width="200">
          <template #default="{ row }">{{ referenceScopeText(row) }}</template>
        </el-table-column>
        <el-table-column label="绑定" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'Y' ? 'success' : 'info'" size="small">
              {{ row.enabled === 'Y' ? '启用中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="referencesVisible = false">关闭</el-button>
        <el-button :loading="referencesLoading" @click="loadReferences">刷新</el-button>
      </template>
    </el-dialog>

    <!-- 检索测试 -->
    <el-dialog v-model="retrievalVisible" title="知识检索测试（只检索、不生成）" width="720px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="集合编码">
          <el-select v-model="retrievalForm.collectionCodes" multiple style="width: 100%">
            <el-option
              v-for="item in collections"
              :key="item.collectionCode"
              :label="item.collectionName + '（' + item.collectionCode + '）'"
              :value="item.collectionCode!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="查询" required>
          <el-input v-model="retrievalForm.query" placeholder="如：3-6 岁适用材质" />
        </el-form-item>
        <el-form-item label="检索模式">
          <el-radio-group v-model="retrievalForm.mode">
            <el-radio-button
              v-for="item in RETRIEVAL_MODE_OPTIONS"
              :key="item.code"
              :value="item.code"
            >
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
          <div class="mode-hint">{{ retrievalModeHint(retrievalForm.mode) }}</div>
        </el-form-item>
        <el-form-item label="最大片段数">
          <el-input-number v-model="retrievalForm.maxChunks" :min="1" :max="20" />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="retrievalResult"
        :type="retrievalResult.noSupport ? 'warning' : 'success'"
        :closable="false"
        class="mb-2"
        :title="retrievalModeLabel(retrievalResult.mode) + ' · ' + retrievalSummary(retrievalResult)"
      />
      <el-table v-if="retrievalResult?.citations?.length" :data="retrievalResult.citations" border>
        <el-table-column label="片段" prop="chunkId" width="180" />
        <el-table-column label="来源" prop="sourceName" min-width="160" />
        <el-table-column label="定位" prop="locator" width="120" />
        <el-table-column label="预览" prop="excerptRef" min-width="200" />
      </el-table>
      <template #footer>
        <el-button @click="retrievalVisible = false">关闭</el-button>
        <el-button
          v-hasPermi="['aig:knowledge:retrieval:test']"
          type="primary"
          :loading="saving"
          @click="doRetrievalTest"
        >
          检索
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  advanceVersion,
  createCollection,
  getDocumentVersion,
  indexVersion,
  listCollectionReferences,
  listCollections,
  listDocuments,
  listVersionChunks,
  registerDocument,
  retrievalTest,
  retireVersion
} from '@/api/aigov/knowledge';
import type {
  AigAgentResourceReferenceVO,
  AigKnowledgeChunkVO,
  AigKnowledgeCollectionForm,
  AigKnowledgeCollectionVO,
  AigKnowledgeDocumentForm,
  AigKnowledgeDocumentVO,
  AigKnowledgeDocumentVersionVO,
  AigKnowledgeIndexingVO,
  AigKnowledgeRetrievalTestForm,
  AigKnowledgeRetrievalTestVO
} from '@/api/aigov/knowledge/types';
import modal from '@/plugins/modal';
import AigEmptyState from '../components/AigEmptyState.vue';
import AigLongId from '../components/AigLongId.vue';
import {
  DATA_LEVEL_OPTIONS,
  RETRIEVAL_MODE_OPTIONS,
  canAdvance,
  canRetire,
  chunkExcerptText,
  chunkIndexText,
  chunkSummary,
  indexingSummary,
  referenceAgentText,
  referenceScopeText,
  referenceSummary,
  registerValidation,
  retrievalModeHint,
  retrievalModeLabel,
  retrievalSummary,
  sourceTypeLabel,
  statusLabel,
  statusTagType
} from './presentation';

defineOptions({ name: 'AigKnowledge' });

const collections = ref<AigKnowledgeCollectionVO[]>([]);
const documents = ref<AigKnowledgeDocumentVO[]>([]);
const currentCollection = ref<AigKnowledgeCollectionVO>();
/** 两个列表各自 loading（走读 §5.5：慢网不能只看到空表） */
const collectionsLoading = ref(false);
const documentsLoading = ref(false);

const saving = ref(false);
const collectionVisible = ref(false);
const documentVisible = ref(false);
const versionVisible = ref(false);
const retrievalVisible = ref(false);

const collectionForm = ref<AigKnowledgeCollectionForm>({ dataLevel: 'STRICT' });
const documentForm = ref<AigKnowledgeDocumentForm>({});
const versionId = ref<string | number>('');
const version = ref<AigKnowledgeDocumentVersionVO>();
const retrievalForm = ref<AigKnowledgeRetrievalTestForm>({ collectionCodes: [], maxChunks: 5, mode: 'KEYWORD' });
const retrievalResult = ref<AigKnowledgeRetrievalTestVO>();

/** 解析并索引（本轮新增的完整管道入口）：结果如实展示用了哪个引擎、多少段 */
const indexing = ref(false);
const indexingResult = ref<AigKnowledgeIndexingVO>();

/** 分段预览（设计 §6.4「解析预览、分段」）：按版本加载，只显示受限长度的预览 */
const chunks = ref<AigKnowledgeChunkVO[]>([]);
const chunksLoaded = ref(false);
const chunksLoading = ref(false);

/** 谁在引用这个集合（退役前的影响面） */
const referencesVisible = ref(false);
const referencesLoading = ref(false);
const referencesCollection = ref<AigKnowledgeCollectionVO>();
const references = ref<AigAgentResourceReferenceVO[]>([]);

const loadReferences = async () => {
  if (!referencesCollection.value?.collectionId) {
    return;
  }
  referencesLoading.value = true;
  try {
    const res = await listCollectionReferences(referencesCollection.value.collectionId);
    references.value = res.data || [];
  } catch (e: any) {
    modal.alertError(e?.message || '读取引用清单失败');
  } finally {
    referencesLoading.value = false;
  }
};

const openReferences = (row: AigKnowledgeCollectionVO) => {
  referencesCollection.value = row;
  references.value = [];
  referencesVisible.value = true;
  loadReferences();
};

const loadChunks = async () => {
  if (!version.value?.versionId) {
    modal.msgWarning('请先填一个存在的版本ID');
    return;
  }
  chunksLoading.value = true;
  try {
    const res = await listVersionChunks(version.value.versionId);
    chunks.value = res.data || [];
    chunksLoaded.value = true;
  } catch (e: any) {
    modal.alertError(e?.message || '读取分段预览失败');
  } finally {
    chunksLoading.value = false;
  }
};

const loadCollections = async () => {
  collectionsLoading.value = true;
  try {
    const res = await listCollections();
    collections.value = res.data || [];
  } finally {
    collectionsLoading.value = false;
  }
};

const loadDocuments = async () => {
  if (!currentCollection.value?.collectionId) {
    documents.value = [];
    return;
  }
  documentsLoading.value = true;
  try {
    const res = await listDocuments(currentCollection.value.collectionId);
    documents.value = res.data || [];
  } finally {
    documentsLoading.value = false;
  }
};

const selectCollection = (row: AigKnowledgeCollectionVO) => {
  currentCollection.value = row;
  loadDocuments();
};

const openCollection = () => {
  collectionForm.value = { dataLevel: 'STRICT' };
  collectionVisible.value = true;
};

const doCreateCollection = async () => {
  if (!collectionForm.value.collectionCode || !collectionForm.value.collectionName) {
    modal.msgWarning('集合编码与名称必填');
    return;
  }
  saving.value = true;
  try {
    await createCollection(collectionForm.value);
    modal.msgSuccess('已创建');
    collectionVisible.value = false;
    await loadCollections();
  } catch (e: any) {
    modal.alertError(e?.message || '创建失败');
  } finally {
    saving.value = false;
  }
};

const openDocument = () => {
  documentForm.value = { collectionId: currentCollection.value?.collectionId };
  documentVisible.value = true;
};

const doRegisterDocument = async () => {
  const error = registerValidation(documentForm.value);
  if (error) {
    modal.msgWarning(error);
    return;
  }
  saving.value = true;
  try {
    const res = await registerDocument({
      ...documentForm.value,
      collectionId: currentCollection.value?.collectionId
    });
    if (res.data?.duplicateHint) {
      modal.msgWarning(
        '已登记（版本 ' + res.data.version + '）。注意：命中同哈希的既有版本 ' +
          (res.data.duplicateVersionId || '') +
          '——登记只提示不共享授权，是否复用请人工决定。'
      );
    } else {
      modal.msgSuccess('已登记（版本 ' + (res.data?.version || '') + '），版本ID ' + (res.data?.versionId || ''));
    }
    documentVisible.value = false;
    await loadDocuments();
  } catch (e: any) {
    modal.alertError(e?.message || '登记失败');
  } finally {
    saving.value = false;
  }
};

const openVersion = () => {
  versionId.value = '';
  version.value = undefined;
  // 换版本必须清掉上一版的分段：留在屏幕上会让"预览"跟"当前版本ID"对不上，
  // 而这种不一致读起来像是这一版的分段内容
  resetChunks();
  versionVisible.value = true;
};

const resetChunks = () => {
  chunks.value = [];
  chunksLoaded.value = false;
};

const loadVersion = async () => {
  resetChunks();
  if (!versionId.value) {
    version.value = undefined;
    return;
  }
  try {
    const res = await getDocumentVersion(versionId.value);
    version.value = res.data;
  } catch (e: any) {
    version.value = undefined;
    modal.alertError(e?.message || '版本不存在');
  }
};

const newKey = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);

const doAdvance = async () => {
  if (!versionId.value) return;
  saving.value = true;
  try {
    // 幂等键每次推进换新；服务端据此判"这一步是否已经做过"
    const res = await advanceVersion(versionId.value, newKey());
    modal.msgSuccess('已推进到：' + statusLabel(res.data as string));
    await loadVersion();
  } catch (e: any) {
    modal.alertError(e?.message || '推进失败');
  } finally {
    saving.value = false;
  }
};

const doRetire = async () => {
  if (!versionId.value) return;
  saving.value = true;
  try {
    await retireVersion(versionId.value);
    modal.msgSuccess('已退役（不再被新任务检索，历史结果仍可追溯）');
    await loadVersion();
  } catch (e: any) {
    modal.alertError(e?.message || '退役失败');
  } finally {
    saving.value = false;
  }
};

const openRetrieval = (row: AigKnowledgeCollectionVO) => {
  retrievalForm.value = {
    collectionCodes: row.collectionCode ? [row.collectionCode] : [],
    maxChunks: 5,
    // 默认关键字：与正式执行路径同口径，避免"随手一测"得到与线上不同的结论
    mode: 'KEYWORD'
  };
  retrievalResult.value = undefined;
  retrievalVisible.value = true;
};

const doRetrievalTest = async () => {
  if (!retrievalForm.value.query) {
    modal.msgWarning('请填写查询');
    return;
  }
  saving.value = true;
  try {
    const res = await retrievalTest(retrievalForm.value);
    retrievalResult.value = res.data;
  } catch (e: any) {
    modal.alertError(e?.message || '检索失败');
  } finally {
    saving.value = false;
  }
};

/**
 * 解析并索引（上传原文）。
 *
 * 幂等键**每次点击换新**：这是"我现在要处理这一版"的一次请求；
 * 重试同一个请求才会复用同一个键，而用户再点一次属于新的处理意图。
 */
const doIndexVersion = async (option: any) => {
  const file: File | undefined = option?.file;
  if (!version.value?.versionId && !versionId.value) {
    modal.msgWarning('请先填一个存在的版本ID');
    return;
  }
  if (!file) {
    modal.msgWarning('请选择要解析的原文文件');
    return;
  }
  // 危险操作统一确认结构（走读 §5.5）：做哪件事 + 影响什么 + 能不能恢复
  try {
    await modal.confirm(
      `解析并索引「${file.name}」？做的是：解析原文、切段、写入分段与向量索引。`
        + '影响：这一版的分段与索引会被这次结果覆盖（检索立刻按新结果走）。'
        + '不能恢复的点：如果该版本已经是「就绪」，服务端会拒绝原地重建（版本不可变），'
        + '需要先新建一个版本再处理。'
    );
  } catch {
    return;
  }
  indexing.value = true;
  indexingResult.value = undefined;
  try {
    const res = await indexVersion(version.value?.versionId ?? versionId.value, newKey(), file);
    indexingResult.value = res.data;
    if (res.data?.status === 'FAILED') {
      modal.alertError(indexingSummary(res.data));
    } else {
      modal.msgSuccess(indexingSummary(res.data));
    }
    await loadVersion();
  } catch (e: any) {
    modal.alertError(e?.message || '解析并索引失败');
  } finally {
    indexing.value = false;
  }
};

onMounted(() => {
  loadCollections();
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

.mt-2 {
  margin-top: 12px;
}

.mb-2 {
  margin-bottom: 12px;
}

.ml-2 {
  margin-left: 8px;
}

.mode-hint {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}
</style>
