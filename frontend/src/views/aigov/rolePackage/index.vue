<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">岗位工作台</span>
            <h3>岗位包管理</h3>
            <p>
              岗位包是<b>给人配的</b>：配错了不会报错，只会表现为「员工点了没反应」或「某个入口点进去空白」。
              所以这里把能判定的都判定掉：先<b>预检</b>，再<b>发布</b>；发布后<b>不可改</b>（要改请出新版本）。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['aig:role:edit']" type="primary" plain icon="Plus" @click="openCreate">
              新建岗位包
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-form v-show="showSearch" :inline="true" @submit.prevent>
        <el-form-item label="岗位编码">
          <el-input v-model="queryParams.roleCode" clearable placeholder="如 GRAPHIC_DESIGNER_AI" style="width: 220px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="岗位名称">
          <el-input v-model="queryParams.roleName" clearable placeholder="模糊匹配" style="width: 200px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="发布状态">
          <el-select v-model="queryParams.releaseStatus" clearable placeholder="全部" style="width: 190px">
            <el-option v-for="item in statusOptions" :key="item.code" :label="item.label" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" border class="data-table" :data="versionList">
        <el-table-column label="岗位编码" align="center" prop="roleCode" min-width="180" show-overflow-tooltip />
        <el-table-column label="岗位名称" align="center" prop="roleName" min-width="180" show-overflow-tooltip />
        <el-table-column label="版本" align="center" prop="version" width="110" />
        <el-table-column label="发布状态" align="center" width="150">
          <template #default="scope">
            <el-tag :type="releaseStatusMeta(scope.row.releaseStatus).tag">
              {{ releaseStatusMeta(scope.row.releaseStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="对员工可见" align="center" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.visibleToEmployees ? 'success' : 'info'">
              {{ scope.row.visibleToEmployees ? '可见' : '不可见' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="清单哈希" align="center" width="150" show-overflow-tooltip>
          <template #default="scope">{{ shortHash(scope.row.manifestSha256) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" align="center" prop="updateTime" width="170" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="330" fixed="right">
          <template #default="scope">
            <el-button v-hasPermi="['aig:role:query']" link type="primary" icon="View" @click="openDetail(scope.row)">
              详情
            </el-button>
            <el-button
              v-hasPermi="['aig:role:edit']"
              link
              type="primary"
              icon="Edit"
              :disabled="scope.row.releaseStatus !== 'DRAFT'"
              @click="openEdit(scope.row)"
            >
              编辑
            </el-button>
            <el-button
              v-hasPermi="['aig:role:validate']"
              link
              type="primary"
              icon="CircleCheck"
              @click="handleValidateStored(scope.row)"
            >
              预检
            </el-button>
            <el-button
              v-hasPermi="['aig:role:publish']"
              link
              type="success"
              icon="Upload"
              :disabled="(scope.row.allowedTransitions || []).length === 0"
              @click="openPublish(scope.row)"
            >
              发布
            </el-button>
            <el-button
              v-hasPermi="['aig:role:disable']"
              link
              type="danger"
              icon="CircleClose"
              :disabled="!canDisable(scope.row.releaseStatus)"
              @click="openDisable(scope.row)"
            >
              停用
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

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="岗位包版本详情" width="900px" append-to-body>
      <el-alert v-if="(detail.problems || []).length" type="warning" :closable="false" class="mb-2">
        <template #title>校验未通过（{{ detail.problems?.length }} 项）——发布前必须修完</template>
        <ul class="problem-list">
          <li v-for="(problem, index) in detail.problems" :key="index">{{ problem }}</li>
        </ul>
      </el-alert>
      <el-alert v-else type="success" :closable="false" class="mb-2" title="校验通过：这份配置可以被发布" />
      <el-descriptions :column="2" border>
        <el-descriptions-item label="岗位编码">{{ detail.roleCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位名称">{{ detail.roleName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布状态">
          {{ releaseStatusMeta(detail.releaseStatus).label }}
        </el-descriptions-item>
        <el-descriptions-item label="对员工可见">{{ detail.visibleToEmployees ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="灰度通道">{{ detail.rolloutChannel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ detail.publishedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="停用时间">{{ detail.disabledAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="清单哈希" :span="2">{{ detail.manifestSha256 || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位简介" :span="2">{{ detail.roleDescription || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本说明" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">能力卡片（按分类）</el-divider>
      <template v-for="group in detailGroups" :key="group.code">
        <div class="group-title">{{ group.name }}（{{ group.actions.length }} 张）</div>
        <el-table :data="group.actions" border size="small" class="data-table">
          <el-table-column label="卡片编码" prop="actionCode" min-width="170" show-overflow-tooltip />
          <el-table-column label="标题" prop="title" min-width="150" show-overflow-tooltip />
          <el-table-column label="启动方式" align="center" width="100">
            <template #default="scope">{{ launchModeLabel(scope.row.launchMode) }}</template>
          </el-table-column>
          <el-table-column label="目标类型" prop="targetType" align="center" width="140" />
          <el-table-column label="目标引用" prop="targetRef" min-width="200" show-overflow-tooltip />
          <el-table-column label="启用" align="center" width="70">
            <template #default="scope">{{ scope.row.enabled === 'Y' ? '是' : '否' }}</template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-if="!detailGroups.length" description="这个版本还没有卡片" />
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 编辑（新建 / 覆盖草稿） -->
    <el-dialog v-model="editVisible" :title="editTitle" width="1020px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        保存的是<b>草稿</b>：改了不会动到员工正在用的那一份（已发布版本不可改）。
        「预检」只是看结论，不落库；发布时服务端会<b>重新校验一次</b>。
      </el-alert>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="130px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="岗位编码" prop="roleCode">
              <el-input v-model="editForm.roleCode" :disabled="!!editForm.roleVersionId" placeholder="如 GRAPHIC_DESIGNER_AI" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位名称" prop="roleName">
              <el-input v-model="editForm.roleName" placeholder="如 平面设计 AI 工作台" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号" prop="version">
              <el-input v-model="editForm.version" :disabled="!!editForm.roleVersionId" placeholder="SemVer，如 1.0.0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="可见范围" prop="audienceScope">
              <el-select v-model="editForm.audienceScope" style="width: 100%">
                <el-option label="按组织范围（ASSIGNED_ORG）" value="ASSIGNED_ORG" />
                <el-option label="全员（ALL）" value="ALL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认数据等级" prop="defaultDataLevel">
              <el-select v-model="editForm.defaultDataLevel" style="width: 100%">
                <el-option v-for="item in dataLevelOptions" :key="item.code" :label="item.label" :value="item.code" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最高数据等级" prop="maxDataLevel">
              <el-select v-model="editForm.maxDataLevel" style="width: 100%">
                <el-option v-for="item in dataLevelOptions" :key="item.code" :label="item.label" :value="item.code" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="岗位简介" prop="description">
              <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="员工看到的说明：这个岗位是做什么的" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="默认分类" prop="defaultCategory">
              <el-select v-model="editForm.defaultCategory" clearable placeholder="未选则使用第一张卡片所在分类" style="width: 100%">
                <el-option v-for="item in editForm.categories" :key="item.code" :label="item.code + '（' + item.name + '）'" :value="item.code" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">分类（至少一个）</el-divider>
        <el-table :data="editForm.categories" border size="small" class="data-table">
          <el-table-column label="分类编码" min-width="200">
            <template #default="scope">
              <el-input v-model="scope.row.code" placeholder="如 DETAIL_PAGE" />
            </template>
          </el-table-column>
          <el-table-column label="分类名称" min-width="200">
            <template #default="scope">
              <el-input v-model="scope.row.name" placeholder="如 详情页设计" />
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="80">
            <template #default="scope">
              <el-button link type="danger" icon="Delete" @click="editForm.categories.splice(scope.$index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button link type="primary" icon="Plus" @click="editForm.categories.push({ code: '', name: '' })">添加分类</el-button>

        <el-divider content-position="left">能力卡片（至少一张）</el-divider>
        <el-table :data="editForm.actions" border size="small" class="data-table">
          <el-table-column label="卡片编码" min-width="170">
            <template #default="scope"><el-input v-model="scope.row.code" /></template>
          </el-table-column>
          <el-table-column label="分类" min-width="150">
            <template #default="scope">
              <el-select v-model="scope.row.categoryCode" style="width: 100%">
                <el-option v-for="item in editForm.categories" :key="item.code" :label="item.code" :value="item.code" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="标题" min-width="150">
            <template #default="scope"><el-input v-model="scope.row.title" /></template>
          </el-table-column>
          <el-table-column label="启动方式" min-width="130">
            <template #default="scope">
              <el-select v-model="scope.row.launchMode" style="width: 100%">
                <el-option v-for="item in launchModeOptions" :key="item.code" :label="item.label" :value="item.code" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="目标类型" min-width="150">
            <template #default="scope">
              <el-select v-model="scope.row.targetType" style="width: 100%">
                <el-option v-for="item in targetTypeOptions" :key="item.code" :label="item.label" :value="item.code" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="目标引用" min-width="200">
            <template #default="scope">
              <el-input v-model="scope.row.targetRef" placeholder="scenario://CODE@1.0.0 / routeKey / 能力编码" />
            </template>
          </el-table-column>
          <el-table-column label="专业台跳转键" min-width="170">
            <template #default="scope">
              <el-input v-model="scope.row.studioRouteKey" placeholder="STUDIO 时必填（白名单 routeKey）" />
            </template>
          </el-table-column>
          <el-table-column label="上下文键" min-width="160">
            <template #default="scope">
              <el-input v-model="scope.row.requiredContext" placeholder="逗号分隔，如 productId,brandId" />
            </template>
          </el-table-column>
          <el-table-column label="启用" align="center" width="70">
            <template #default="scope"><el-switch v-model="scope.row.enabled" /></template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="70">
            <template #default="scope">
              <el-button link type="danger" icon="Delete" @click="editForm.actions.splice(scope.$index, 1)" />
            </template>
          </el-table-column>
        </el-table>
        <el-button link type="primary" icon="Plus" @click="addAction">添加卡片</el-button>

        <el-form-item label="版本说明" prop="remark" class="mt-2">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="这一版改了什么（发布后仍会保留）" />
        </el-form-item>
      </el-form>

      <el-alert v-if="editProblems.length" type="warning" :closable="false" class="mt-2">
        <template #title>预检未通过（{{ editProblems.length }} 项）</template>
        <ul class="problem-list">
          <li v-for="(problem, index) in editProblems" :key="index">{{ problem }}</li>
        </ul>
      </el-alert>

      <template #footer>
        <el-button v-hasPermi="['aig:role:validate']" :loading="validating" @click="handleValidateForm">
          预检
        </el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存草稿</el-button>
        <el-button @click="editVisible = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 发布 -->
    <el-dialog v-model="publishVisible" title="发布岗位包版本" width="560px" append-to-body>
      <el-alert type="warning" :closable="false" class="mb-2">
        发布前服务端会<b>重新校验一次</b>库里的那份配置；不通过会被拒。
        发布后<b>不能退回草稿</b>（要改请出新版本）。
      </el-alert>
      <el-form label-width="110px">
        <el-form-item label="目标状态">
          <el-select v-model="publishForm.targetStatus" style="width: 100%">
            <el-option v-for="item in publishTargetList" :key="item.code" :label="item.label" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="流转说明">
          <el-input v-model="publishForm.remark" type="textarea" :rows="2" placeholder="为什么现在发布（会写进版本说明）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="publishing" @click="handlePublish">确认发布</el-button>
        <el-button @click="publishVisible = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 停用 -->
    <el-dialog v-model="disableVisible" title="停用岗位包版本" width="560px" append-to-body>
      <el-alert type="error" :closable="false" class="mb-2">
        停用后<b>新用户不会获得该版本</b>（历史任务不受影响）。停用是<b>可撤销</b>的：
        之后还能重新启用到测试或已发布。
      </el-alert>
      <el-form label-width="110px">
        <el-form-item label="停用原因">
          <el-input v-model="disableForm.remark" type="textarea" :rows="2" placeholder="为什么叫停（会写进版本说明，事后要能回答）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="danger" :loading="disabling" @click="handleDisable">确认停用</el-button>
        <el-button @click="disableVisible = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  disableRoleVersion,
  getRoleVersion,
  listRoleVersions,
  publishRoleVersion,
  saveRolePackage,
  updateRoleVersion,
  validateRolePackage,
  validateStoredRoleVersion
} from '@/api/aigov/rolePackage';
import type {
  AigRolePackageQuery,
  AigRolePackageSaveForm,
  AigRoleVersionDetailVO,
  AigRoleVersionVO
} from '@/api/aigov/rolePackage/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import {
  canDisable,
  groupActionsByCategory,
  launchModeLabel,
  parseManifest,
  publishTargets,
  releaseStatusMeta
} from './presentation';

defineOptions({ name: 'AigRolePackage' });

/** 发布状态筛选（与后端 AigRoleReleaseStatusEnum 一致） */
const statusOptions = ['DRAFT', 'TESTING', 'PUBLISHED', 'DISABLED'].map(code => ({
  code,
  label: releaseStatusMeta(code).label
}));

/** 数据等级（与后端 AigDataLevelEnum 一致；rank 越大越严） */
const dataLevelOptions = [
  { code: 'PUBLIC', label: 'PUBLIC（公开）' },
  { code: 'INTERNAL', label: 'INTERNAL（内部）' },
  { code: 'RESTRICTED', label: 'RESTRICTED（限制）' },
  { code: 'STRICT', label: 'STRICT（严格）' }
];

/** 启动方式（与后端 AigActionLaunchModeEnum 一致） */
const launchModeOptions = [
  { code: 'QUICK', label: launchModeLabel('QUICK') },
  { code: 'FORM', label: launchModeLabel('FORM') },
  { code: 'STUDIO', label: launchModeLabel('STUDIO') },
  { code: 'NAVIGATION', label: launchModeLabel('NAVIGATION') }
];

/** 目标类型（与后端 AigLaunchTargetTypeEnum 一致） */
const targetTypeOptions = [
  { code: 'SCENARIO', label: 'SCENARIO（场景）' },
  { code: 'QUICK_CAPABILITY', label: 'QUICK_CAPABILITY（轻量能力）' },
  { code: 'NAVIGATION', label: 'NAVIGATION（页面）' }
];

const versionList = ref<AigRoleVersionVO[]>([]);
const total = ref(0);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const queryParams = ref<AigRolePackageQuery>({ pageNum: 1, pageSize: 10 });

const detailVisible = ref(false);
const detail = ref<AigRoleVersionDetailVO>({ roleVersionId: '', roleId: '' });

const editVisible = ref(false);
const editTitle = ref('新建岗位包');
const saving = ref(false);
const validating = ref(false);
const editProblems = ref<string[]>([]);
const editFormRef = ref<ElFormInstance>();
const editForm = ref<AigRolePackageSaveForm>(emptyForm());
const editRules = {
  roleCode: [{ required: true, message: '岗位编码不能为空', trigger: 'blur' }],
  roleName: [{ required: true, message: '岗位名称不能为空', trigger: 'blur' }],
  version: [{ required: true, message: '版本号不能为空', trigger: 'blur' }],
  audienceScope: [{ required: true, message: '可见范围不能为空', trigger: 'change' }],
  defaultDataLevel: [{ required: true, message: '默认数据等级不能为空', trigger: 'change' }],
  maxDataLevel: [{ required: true, message: '最高数据等级不能为空', trigger: 'change' }]
};

const publishVisible = ref(false);
const publishing = ref(false);
const publishForm = ref<{ targetStatus: string; remark: string }>({ targetStatus: '', remark: '' });
const publishRow = ref<AigRoleVersionVO>();
const publishTargetList = computed(() => publishTargets(publishRow.value?.allowedTransitions));

const disableVisible = ref(false);
const disabling = ref(false);
const disableForm = ref<{ remark: string }>({ remark: '' });
const disableRow = ref<AigRoleVersionVO>();

/** 详情里的卡片按清单声明的分类归组（清单坏掉时按"未声明分类"显示，而不是凭空消失） */
const detailGroups = computed(() => {
  const manifest = parseManifest(detail.value.manifestJson);
  return groupActionsByCategory(manifest.categories, detail.value.actions);
});

/** 新表单骨架：默认 INTERNAL/INTERNAL（只能收紧，默认就按最保守的共同点给） */
function emptyForm(): AigRolePackageSaveForm {
  return {
    roleCode: '',
    roleName: '',
    version: '1.0.0',
    categories: [{ code: '', name: '' }],
    actions: [],
    audienceScope: 'ASSIGNED_ORG',
    defaultDataLevel: 'INTERNAL',
    maxDataLevel: 'INTERNAL'
  };
}

const getList = async () => {
  await withLoading(async () => {
    const res = await listRoleVersions(queryParams.value);
    versionList.value = res.data.rows || [];
    total.value = res.data.total || 0;
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.value = { pageNum: 1, pageSize: queryParams.value.pageSize };
  getList();
};

const shortHash = (hash?: string) => (hash ? hash.slice(0, 12) + '…' : '-');

const openDetail = async (row: any) => {
  const res = await getRoleVersion(row.roleVersionId);
  detail.value = res.data;
  detailVisible.value = true;
};

const openCreate = () => {
  editForm.value = emptyForm();
  editProblems.value = [];
  editTitle.value = '新建岗位包';
  editVisible.value = true;
};

const openEdit = async (row: any) => {
  const res = await getRoleVersion(row.roleVersionId);
  const data = res.data;
  const manifest = parseManifest(data.manifestJson);
  if (!manifest.ok) {
    // 清单坏了不阻止编辑：服务端校验会给出问题清单，先让人看到问题
    modal.msgWarning('这份版本的清单无法解析：请先预检，按问题清单修正后再保存');
  }
  editForm.value = {
    roleId: data.roleId,
    roleVersionId: data.roleVersionId,
    // CAS：保存前必须带"我读到的是哪一版"，服务端会核对，避免并发编辑被静默覆盖
    expectedManifestSha256: data.manifestSha256,
    roleCode: data.roleCode || '',
    roleName: data.roleName || '',
    version: data.version || '',
    description: data.roleDescription,
    categories: manifest.categories.length ? manifest.categories : [{ code: '', name: '' }],
    defaultCategory: manifest.defaultCategory,
    actions: (data.actions || []).map(item => ({
      code: item.actionCode || '',
      categoryCode: item.categoryCode || '',
      title: item.title || '',
      description: item.description,
      launchMode: item.launchMode || 'QUICK',
      targetType: item.targetType || 'QUICK_CAPABILITY',
      targetRef: item.targetRef,
      studioRouteKey: item.studioRouteKey,
      requiredContext: item.requiredContext,
      enabled: item.enabled !== 'N'
    })),
    audienceScope: manifest.audienceScope || 'ASSIGNED_ORG',
    defaultDataLevel: manifest.defaultDataLevel || 'INTERNAL',
    maxDataLevel: manifest.maxDataLevel || 'INTERNAL',
    remark: data.remark
  };
  editProblems.value = [];
  editTitle.value = '编辑岗位包草稿（' + (data.roleName || data.roleCode) + ' ' + (data.version || '') + '）';
  editVisible.value = true;
};

const addAction = () => {
  editForm.value.actions.push({
    code: '',
    categoryCode: editForm.value.categories[0]?.code || '',
    title: '',
    launchMode: 'QUICK',
    targetType: 'QUICK_CAPABILITY',
    targetRef: '',
    enabled: true
  });
};

const handleValidateForm = async () => {
  validating.value = true;
  try {
    const res = await validateRolePackage(editForm.value);
    editProblems.value = res.data.problems || [];
    if (res.data.passed) {
      modal.msgSuccess('预检通过');
    } else {
      modal.msgWarning('预检未通过（' + editProblems.value.length + ' 项）：请看下方问题清单');
    }
  } finally {
    validating.value = false;
  }
};

const handleSave = async () => {
  const valid = await editFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  saving.value = true;
  try {
    if (editForm.value.roleVersionId) {
      const res = await updateRoleVersion(editForm.value.roleVersionId, editForm.value);
      editForm.value.expectedManifestSha256 = res.data.manifestSha256;
      editProblems.value = res.data.problems || [];
      modal.msgSuccess('草稿已保存');
    } else {
      const res = await saveRolePackage(editForm.value);
      // 落库后转为"覆盖该 DRAFT 版本"，后续保存走 PUT + CAS
      editForm.value.roleId = res.data.roleId;
      editForm.value.roleVersionId = res.data.roleVersionId;
      editForm.value.expectedManifestSha256 = res.data.manifestSha256;
      editProblems.value = res.data.problems || [];
      modal.msgSuccess('草稿已创建（当前处于 DRAFT，发布前请先预检）');
    }
    editTitle.value = '编辑岗位包草稿';
    getList();
  } catch (e: any) {
    modal.alertError(e?.message || '保存失败');
  } finally {
    saving.value = false;
  }
};

const handleValidateStored = async (row: any) => {
  const res = await validateStoredRoleVersion(row.roleVersionId);
  if (res.data.passed) {
    modal.alertSuccess('校验通过：' + (row.roleCode || '') + ' ' + (row.version || ''));
  } else {
    modal.alertError(
      '校验未通过（' + (res.data.problems?.length || 0) + ' 项）：\n' + (res.data.problems || []).join('\n')
    );
  }
};

const openPublish = (row: any) => {
  publishRow.value = row;
  publishForm.value = { targetStatus: publishTargets(row.allowedTransitions)[0]?.code || '', remark: '' };
  publishVisible.value = true;
};

const handlePublish = async () => {
  if (!publishRow.value || !publishForm.value.targetStatus) {
    modal.msgWarning('请选择目标状态');
    return;
  }
  publishing.value = true;
  try {
    await publishRoleVersion(publishRow.value.roleVersionId, {
      targetStatus: publishForm.value.targetStatus,
      remark: publishForm.value.remark
    });
    modal.msgSuccess('已流转到 ' + releaseStatusMeta(publishForm.value.targetStatus).label);
    publishVisible.value = false;
    getList();
  } catch (e: any) {
    // 服务端会给出"为什么不行"（没校验通过 / 不允许的流转），照原样显示
    modal.alertError(e?.message || '发布失败');
  } finally {
    publishing.value = false;
  }
};

const openDisable = (row: any) => {
  disableRow.value = row;
  disableForm.value = { remark: '' };
  disableVisible.value = true;
};

const handleDisable = async () => {
  if (!disableRow.value) {
    return;
  }
  disabling.value = true;
  try {
    await disableRoleVersion(disableRow.value.roleVersionId, { remark: disableForm.value.remark });
    modal.msgSuccess('已停用（可重新启用到测试或已发布）');
    disableVisible.value = false;
    getList();
  } catch (e: any) {
    modal.alertError(e?.message || '停用失败');
  } finally {
    disabling.value = false;
  }
};

onMounted(() => {
  getList();
});
</script>

<style scoped>
.problem-list {
  margin: 4px 0 0 0;
  padding-left: 18px;
}

.group-title {
  margin: 10px 0 6px 0;
  font-weight: 600;
}

.mb-2 {
  margin-bottom: 12px;
}

.mt-2 {
  margin-top: 12px;
}
</style>
