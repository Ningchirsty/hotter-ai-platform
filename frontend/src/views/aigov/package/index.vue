<template>
  <div class="p-2 app-container aigov-package-page">
    <PageHeading title="AI平台治理" subtitle="管理AI能力、模型接入与调用策略" admin module="aigov" />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>Package 检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="Package编码" prop="packageCode">
            <el-input v-model="queryParams.packageCode" placeholder="精确匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="名称" prop="packageName">
            <el-input v-model="queryParams.packageName" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="类型" prop="packageType">
            <el-select v-model="queryParams.packageType" placeholder="请选择类型" clearable style="width: 150px">
              <el-option label="AGENT" value="AGENT" />
              <el-option label="SKILL" value="SKILL" />
              <el-option label="MIXED" value="MIXED" />
            </el-select>
          </el-form-item>
          <el-form-item label="发布方" prop="publisher">
            <el-input v-model="queryParams.publisher" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
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
            <span class="panel-kicker">Package Registry</span>
            <h3>第三方 Package</h3>
            <p>
              共 {{ total }} 条记录；链路是「上传（携包体，服务端核对 checksum）→ 扫描（§6.2 五类拒绝规则）
              → 安装（按 Manifest 声明的 agents/skills 建出 DRAFT 版本）」。安装不等于发布。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['aig:package:upload']" type="primary" plain icon="Upload" @click="openUpload">
              上传 Package
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="packageList">
        <el-table-column label="Package编码" align="center" prop="packageCode" width="200" show-overflow-tooltip />
        <el-table-column label="名称" align="center" prop="packageName" width="160" show-overflow-tooltip />
        <el-table-column label="类型" align="center" prop="packageType" width="100" />
        <el-table-column label="发布方" align="center" prop="publisher" width="140" show-overflow-tooltip />
        <el-table-column label="许可证" align="center" prop="licenseCode" width="120" />
        <el-table-column label="包体校验和" align="center" width="160">
          <template #default="scope">
            <span class="mono" :title="scope.row.checksum">{{ shortHash(scope.row.checksum) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" align="center" prop="sourceType" width="120" />
        <el-table-column label="操作" align="center" width="140" fixed="right">
          <template #default="scope">
            <el-button v-hasPermi="['aig:package:list']" link type="primary" icon="View" @click="openVersions(scope.row)">
              版本
            </el-button>
          </template>
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

    <!-- 版本：扫描 / 安装 / 账本 -->
    <el-dialog v-model="versionVisible" :title="'版本 · ' + (currentPackage?.packageName || '')" width="1080px" append-to-body>
      <el-table v-loading="versionLoading" border :data="versionList">
        <el-table-column label="版本" align="center" prop="version" width="90" />
        <el-table-column label="发布状态" align="center" prop="releaseStatus" width="120" />
        <el-table-column label="扫描结论" align="center" width="110">
          <template #default="scope">
            <el-tag :type="scanTagType(scope.row.scanResult)">{{ scope.row.scanResult || '未扫描' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="扫描说明" align="center" prop="scanDetail" show-overflow-tooltip />
        <el-table-column label="包体" align="center" width="110">
          <template #default="scope">
            <el-tooltip v-if="scope.row.bodyRef" :content="scope.row.bodyRef" placement="top">
              <el-tag type="success">已留存</el-tag>
            </el-tooltip>
            <el-tooltip v-else content="未留存包体（aigov.package.store-body 未开启，或该版本在本开关上线前登记）" placement="top">
              <el-tag type="info">未留存</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="250" fixed="right">
          <template #default="scope">
            <el-button v-hasPermi="['aig:package:scan']" link type="primary" icon="Search" @click="handleScan(scope.row)">
              扫描
            </el-button>
            <el-button v-hasPermi="['aig:package:install']" link type="success" icon="Download"
              :disabled="scope.row.scanResult !== 'PASS'" @click="handleInstall(scope.row)">
              安装
            </el-button>
            <el-button v-hasPermi="['aig:package:disable']" link type="danger" icon="CircleClose" @click="handleDisable(scope.row)">
              停用
            </el-button>
            <el-button v-hasPermi="['aig:package:query']" link icon="List" @click="openLog(scope.row)">账本</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-alert type="warning" :closable="false" class="mt-2">
        「安装」只对 <b>scan_result=PASS</b> 的版本开放（服务层查库里的证据，不看页面按钮状态）。
        安装出来的 Agent/Skill 版本一律是 DRAFT：安装不等于发布，发布门槛照旧要逐道过。
        <br />
        「停用」停的是<b>这个包带进来的</b>版本（按版本行的来源包精确判定，同一 Agent 的其它版本不动）：
        如果其中已有 <b>STABLE</b> 版本正在被业务使用，停用会立即影响线上；停用只改发布状态、不删版本内容。
      </el-alert>
    </el-dialog>

    <!-- 上传 -->
    <el-dialog v-model="uploadVisible" title="上传 Package（携包体）" width="720px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        服务端会对上传字节算 SHA-256 并与 Manifest 声明的 <b>checksum</b> 比对：不一致整笔拒绝、不落库。
        包身份与版本号以 Manifest 为准（入参不重复传，避免两处不一致）。包体本身不入库——
        声明式 Package 的安装只读 Manifest。
      </el-alert>
      <el-form label-width="110px">
        <el-form-item label="包体文件" required>
          <input type="file" @change="onFileChange" />
        </el-form-item>
        <el-form-item label="Manifest" required>
          <el-input
            v-model="uploadForm.manifestJson"
            type="textarea"
            :rows="10"
            placeholder='{"package_code":"...","version":"1.0.0","checksum":"<包体sha256>", ... ,"skills":[{...}]}'
          />
        </el-form-item>
        <el-form-item label="来源引用">
          <el-input v-model="uploadForm.sourceRef" placeholder="可空；上传存储键或可信来源地址" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="uploadForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <el-alert v-if="uploadResultText" :type="uploadResultOk ? 'success' : 'error'" :closable="false">
        {{ uploadResultText }}
      </el-alert>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUpload">上传登记</el-button>
      </template>
    </el-dialog>

    <!-- 安装账本 -->
    <el-dialog v-model="logVisible" title="安装账本（追加型）" width="820px" append-to-body>
      <el-table v-loading="logLoading" border :data="logList">
        <el-table-column label="动作" align="center" prop="action" width="110" />
        <el-table-column label="结果" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.result === 'PASS' ? 'success' : 'danger'">{{ scope.row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作人" align="center" prop="operatorId" width="120" show-overflow-tooltip />
        <el-table-column label="说明" align="center" prop="detail" show-overflow-tooltip />
        <el-table-column label="时间" align="center" prop="operateTime" width="180" />
      </el-table>
    </el-dialog>

    <!-- 停用结果 -->
    <el-dialog v-model="disableVisible" title="停用结果" width="880px" append-to-body>
      <el-alert :type="disableResult?.alreadyDisabled ? 'info' : 'success'" :closable="false" class="mb-2">
        {{ disableResult?.note }}
      </el-alert>

      <div v-if="disableResult?.disabled?.length">
        <div class="mb-1"><b>已停用</b>（{{ disableResult.disabled.length }} 个）</div>
        <el-table border :data="disableResult.disabled" size="small">
          <el-table-column label="类型" align="center" prop="targetType" width="150" />
          <el-table-column label="编码" align="center" prop="code" show-overflow-tooltip />
          <el-table-column label="版本" align="center" prop="version" width="120" />
          <el-table-column label="停用前状态" align="center" width="140">
            <template #default="scope">
              <el-tag :type="scope.row.fromStatus === 'STABLE' ? 'danger' : 'info'">
                {{ scope.row.fromStatus }}
              </el-tag>
              → DISABLED
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="disableResult?.skipped?.length" class="mt-3">
        <div class="mb-1"><b>未改动</b>（{{ disableResult.skipped.length }} 个，原因如下）</div>
        <el-table border :data="disableResult.skipped" size="small">
          <el-table-column label="类型" align="center" prop="targetType" width="150" />
          <el-table-column label="编码" align="center" prop="code" show-overflow-tooltip />
          <el-table-column label="当前状态" align="center" prop="fromStatus" width="120" />
          <el-table-column label="原因" align="center" prop="reason" show-overflow-tooltip />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  disablePackage,
  installPackage,
  listInstallLog,
  listPackage,
  listPackageVersion,
  scanManifest,
  uploadPackage
} from '@/api/aigov/package';
import type {
  AigPackageDisableVO,
  AigPackageInstallLogVO,
  AigPackageQuery,
  AigPackageRegisterVO,
  AigPackageUploadForm,
  AigPackageVO,
  AigPackageVersionVO
} from '@/api/aigov/package/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';

defineOptions({ name: 'AigPackageRegistry' });

const packageList = ref<AigPackageVO[]>([]);
const versionList = ref<AigPackageVersionVO[]>([]);
const logList = ref<AigPackageInstallLogVO[]>([]);
const currentPackage = ref<AigPackageVO>();
const { loading, withLoading } = useLoading(true);
const { loading: versionLoading, withLoading: withVersionLoading } = useLoading(true);
const { loading: logLoading, withLoading: withLogLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const versionVisible = ref(false);
const uploadVisible = ref(false);
const disableVisible = ref(false);
const disableResult = ref<AigPackageDisableVO>();
const logVisible = ref(false);
const bodyFile = ref<File>();
const uploadResultText = ref('');
const uploadResultOk = ref(false);

const queryParams = ref<AigPackageQuery>({
  pageNum: 1,
  pageSize: 10,
  params: {}
});

const uploadForm = ref<AigPackageUploadForm>({ manifestJson: '', sourceRef: '', remark: '' });

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 校验和只显示前 12 位（悬停看全量） */
const shortHash = (value?: string) => (value ? value.substring(0, 12) + '…' : '-');

/** 扫描结论标签色 */
const scanTagType = (value?: string): 'success' | 'danger' | 'info' | undefined => {
  if (value === 'PASS') return 'success';
  if (value === 'REJECT') return 'danger';
  return 'info';
};

/** 查询 Package 清单 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listPackage(queryParams.value);
    packageList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 搜索 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 打开版本列表 */
const openVersions = async (row: AigPackageVO) => {
  currentPackage.value = row;
  versionVisible.value = true;
  await withVersionLoading(async () => {
    const res = await listPackageVersion({ pageNum: 1, pageSize: 100, packageId: row.packageId });
    versionList.value = res.data?.rows || [];
  });
};

/** 扫描（结论落库，DRAFT 版本才可扫） */
const handleScan = async (row: AigPackageVersionVO) => {
  const res = await scanManifest(row.packageVersionId as string | number);
  const result = res.data;
  if (result?.pass) {
    modal.msgSuccess('扫描通过');
  } else {
    modal.msgError('扫描被拒：' + (result?.detail || ''));
  }
  await openVersions(currentPackage.value as AigPackageVO);
};

/** 安装 */
const handleInstall = async (row: AigPackageVersionVO) => {
  await modal.confirm('确认安装该 Package 版本？会按 Manifest 声明建出 Agent/Skill 版本（均为 DRAFT）。');
  const res = await installPackage(row.packageVersionId as string | number);
  const result = res.data;
  modal.msgSuccess(
    (result?.alreadyInstalled ? '此前已安装（本次未重复创建）：' : '安装完成：') +
      'Agent ' +
      (result?.agents?.length || 0) +
      ' 个、Skill ' +
      (result?.skills?.length || 0) +
      ' 个'
  );
  await openVersions(currentPackage.value as AigPackageVO);
};

/**
 * 停用：把该 Package 版本带进来的版本批量下线。
 *
 * 确认框必须把影响面说清楚——这里面可能有正在被业务使用的 STABLE 版本。
 * 结果里成功项与跳过项分开显示：跳过项都带原因，页面要能回答「为什么这个没停掉」。
 */
const handleDisable = async (row: AigPackageVersionVO) => {
  await modal.confirm(
    '确认停用该 Package 版本带进来的 Agent/Skill 版本？' +
      '会把这些版本批量下线：如果其中已有 STABLE 版本正在被业务使用，会立即影响线上使用。' +
      '停用只改发布状态、不删版本内容（重新启用走发布推进，且需证明该版本曾 STABLE 过）。'
  );
  const res = await disablePackage(row.packageVersionId as string | number);
  // 先刷新版本表（状态已经变了），再弹结果说明
  await openVersions(currentPackage.value as AigPackageVO);
  disableResult.value = res.data || {};
  disableVisible.value = true;
};

/** 打开安装账本 */
const openLog = async (row: AigPackageVersionVO) => {
  logVisible.value = true;
  await withLogLoading(async () => {
    const res = await listInstallLog(row.packageVersionId as string | number);
    logList.value = res.data || [];
  });
};

/** 打开上传 */
const openUpload = () => {
  uploadForm.value = { manifestJson: '', sourceRef: '', remark: '' };
  bodyFile.value = undefined;
  uploadResultText.value = '';
  uploadVisible.value = true;
};

/** 选择包体 */
const onFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement;
  bodyFile.value = input.files?.[0];
};

/** 提交上传 */
const submitUpload = async () => {
  if (!bodyFile.value) {
    modal.msgError('请选择包体文件（服务端要用它核对 checksum）');
    return;
  }
  if (!uploadForm.value.manifestJson) {
    modal.msgError('请填写 Manifest');
    return;
  }
  const res = await uploadPackage(bodyFile.value, uploadForm.value);
  const result: AigPackageRegisterVO = res.data || {};
  uploadResultOk.value = !!result.scanPass;
  uploadResultText.value =
    '已登记 ' +
    result.packageCode +
    '@' +
    result.version +
    '（版本ID ' +
    result.packageVersionId +
    '）：' +
    (result.scanPass ? 'Manifest 校验通过，可安装' : '被拒绝 —— ' + (result.scanDetail || '')) +
    (result.bodyStored
      ? '；包体已留存（' + result.bodyRef + '）'
      : '；包体未留存（aigov.package.store-body 未开启，这是默认行为）');
  getList();
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.mono {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
}
</style>
