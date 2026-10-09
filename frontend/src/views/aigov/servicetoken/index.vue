<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Service Identity</span>
            <h3>服务令牌（机器身份）</h3>
            <p>
              给<b>机器调用方</b>（定时任务、外部 Agent、脚本）用的身份。授权范围就是<b>平台的权限码</b>本身，
              与人的角色同一套口径：令牌能做什么 = 这些权限码允许什么，审计里也能直接对齐。
              <b>明文只在签发那一刻显示一次</b>，平台不留底；<b>不授予任何权限 = 空范围</b>（默认拒绝，不是默认全给）。
              调用方需在请求头携带 <code>X-Service-Token</code>，并按平台既有约定携带 <code>clientid</code>。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['aig:service-token:issue']" type="primary" plain icon="Plus" @click="openIssue">
              签发令牌
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="tokens">
        <el-table-column label="服务名" prop="name" min-width="160" show-overflow-tooltip />
        <el-table-column label="令牌前缀" prop="tokenPrefix" width="170" />
        <el-table-column label="授权范围（权限码）" min-width="240" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="!scope.row.scopes" class="quota-danger">（空 = 不授予任何操作）</span>
            <span v-else>{{ scope.row.scopes }}</span>
          </template>
        </el-table-column>
        <el-table-column label="到期" width="170" align="center">
          <template #default="scope">{{ scope.row.expiresAt || '不过期' }}</template>
        </el-table-column>
        <el-table-column label="最近使用" width="190">
          <template #default="scope">
            <template v-if="scope.row.lastUsedAt">
              {{ scope.row.lastUsedAt }}
              <div class="text-gray-500">{{ scope.row.lastUsedIp || '来源IP未知' }}</div>
            </template>
            <span v-else class="text-gray-500">从未使用</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === '1' ? 'info' : 'success'">
              {{ scope.row.status === '1' ? '已停用' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="scope">
            <el-button
              v-hasPermi="['aig:service-token:revoke']"
              link
              type="danger"
              icon="CircleClose"
              :disabled="scope.row.status === '1'"
              @click="handleRevoke(scope.row)"
            >
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 签发 -->
    <el-dialog v-model="issueVisible" title="签发服务令牌" width="640px" append-to-body>
      <el-alert type="warning" :closable="false" class="mb-2">
        明文令牌<b>只在签发成功后显示一次</b>，平台不留底、之后无法找回。丢了只能重新签发一把
        （所以请当场复制并交给使用方）。
      </el-alert>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="服务名" prop="name">
          <el-input v-model="form.name" placeholder="例如 vibeposter-worker（只用字母数字点下划线中划线）" />
        </el-form-item>
        <el-form-item label="授权范围" prop="scopes">
          <el-input
            v-model="form.scopes"
            type="textarea"
            :rows="3"
            placeholder="逗号分隔的权限码，例如 aig:capability:query；留空 = 不授予任何操作"
          />
        </el-form-item>
        <el-form-item label="到期时间" prop="expiresAt">
          <el-date-picker
            v-model="form.expiresAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空 = 不过期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="给谁用、为什么需要这些 scope（便于事后复核）" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false">
        服务端会拒绝两类范围，别白填：含通配符（<code>*</code>）的、以及令牌管理权限
        （<code>aig:service-token:*</code>）——前者会匹配上远宽于字面的权限，后者等于让机器自我提权。
      </el-alert>
      <template #footer>
        <el-button @click="issueVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="submitIssue">签 发</el-button>
      </template>
    </el-dialog>

    <!-- 明文（只此一次） -->
    <el-dialog
      v-model="issuedVisible"
      title="令牌已签发：明文只显示这一次"
      width="700px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <el-alert type="error" :closable="false" class="mb-2">
        平台<b>不留底</b>：关闭本窗口后既看不到明文，也找不回来。请现在复制并交付给使用方。
      </el-alert>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="服务名">{{ issued?.name }}</el-descriptions-item>
        <el-descriptions-item label="令牌前缀">{{ issued?.tokenPrefix }}</el-descriptions-item>
        <el-descriptions-item label="授权范围">{{ issued?.scopes || '（空：不授予任何操作）' }}</el-descriptions-item>
        <el-descriptions-item label="到期">{{ issued?.expiresAt || '不过期' }}</el-descriptions-item>
        <el-descriptions-item label="明文令牌">
          <div class="token-box">{{ issued?.token }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="使用方式">
          {{ issued?.usageHint || '请把该令牌放进请求头 X-Service-Token' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" plain icon="DocumentCopy" @click="copyToken">复制明文令牌</el-button>
        <el-button type="primary" @click="closeIssued">我已保存好，关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { issueServiceToken, listServiceToken, revokeServiceToken } from '@/api/aigov/serviceToken';
import type { AigServiceTokenIssuedVO, AigServiceTokenIssueForm, AigServiceTokenVO } from '@/api/aigov/serviceToken/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';

defineOptions({ name: 'AigServiceToken' });

const tokens = ref<AigServiceTokenVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();

const issueVisible = ref(false);
const issuedVisible = ref(false);
const saving = ref(false);
const copied = ref(false);
const formRef = ref<ElFormInstance>();
const form = ref<AigServiceTokenIssueForm>({});
const issued = ref<AigServiceTokenIssuedVO>();

const { copy } = useClipboard();

const rules = {
  name: [
    { required: true, message: '服务名不能为空', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9][A-Za-z0-9._-]*$/, message: '只能用字母数字点下划线中划线，且以字母或数字开头', trigger: 'blur' }
  ]
};

const getList = async () => {
  await withLoading(async () => {
    const res = await listServiceToken();
    tokens.value = res.data || [];
  });
};

const openIssue = () => {
  form.value = {};
  issueVisible.value = true;
};

const submitIssue = async () => {
  if (!formRef.value) return;
  await formRef.value.validate();
  saving.value = true;
  try {
    const res = await issueServiceToken(form.value);
    issued.value = res.data || {};
    copied.value = false;
    issueVisible.value = false;
    issuedVisible.value = true;
    getList();
  } finally {
    saving.value = false;
  }
};

const copyToken = async () => {
  await copy(issued.value?.token || '');
  copied.value = true;
  modal.msgSuccess('已复制到剪贴板');
};

const closeIssued = async () => {
  if (!copied.value) {
    // 明文错过就找不回来，所以"没复制就关"必须挡一下（挡的是误触，不是防呆到底）
    await modal.confirm('还没有复制明文令牌。关闭后平台无法再显示它，只能重新签发一把。确认关闭？');
  }
  issuedVisible.value = false;
  issued.value = undefined;
};

const handleRevoke = async (row: AigServiceTokenVO) => {
  await modal.confirm(
    '确认停用「' + row.name + '」？该调用方的所有调用会**立刻**变成 401（不物理删除，记录会保留）。' +
      '如果只是想换一把，先签发新令牌、调用方切换完成后再停用旧的。'
  );
  await revokeServiceToken(row.tokenId as string | number);
  modal.msgSuccess('已停用（该调用方需要改用新令牌）');
  getList();
};

onMounted(() => {
  getList();
});
</script>

<style scoped>
.token-box {
  font-family: var(--el-font-family-mono, monospace);
  word-break: break-all;
  background: var(--el-fill-color-light);
  padding: 6px 8px;
  border-radius: 4px;
}

.quota-danger {
  color: var(--el-color-danger);
}
</style>
