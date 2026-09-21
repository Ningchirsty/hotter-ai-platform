<template>
  <div class="p-2 app-container hrtalent-grant-page">
    <PageHeading
      title="人才共享授权"
      subtitle="某人才的授权列表与新增（用户/角色/公司部门/部门，权限级别 summary/detail/attachment，有效起止与授权原因）、撤销"
      module="hrtalent"
    />

    <!-- §8.19 安全边界必须显式提示 -->
    <el-alert
      class="page-alert"
      type="warning"
      :closable="false"
      show-icon
      title="共享只扩大查看范围，不自动授予电话明文、附件下载、背调和导出权限（设计 §8.19）。上述动作各有独立权限（recruit:candidate:phone-view、talent:resume:download / recruit:attachment:download、recruit:background:view-sensitive、talent:profile:export），授权级别不改变它们。"
    />

    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>授权检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="人才ID" prop="talentId">
            <el-input v-model="queryParams.talentId" placeholder="人才主档ID" clearable @keyup.enter="handleQuery">
              <template #append>
                <el-button icon="Search" @click="openTalentPicker" />
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="授权对象类型" prop="granteeType">
            <el-select v-model="queryParams.granteeType" placeholder="请选择" clearable style="width: 150px">
              <el-option v-for="item in GRANTEE_TYPES" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权对象ID" prop="granteeId">
            <el-input v-model="queryParams.granteeId" placeholder="与类型成对" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="授权级别" prop="permissionLevel">
            <el-select v-model="queryParams.permissionLevel" placeholder="请选择" clearable style="width: 150px">
              <el-option
                v-for="dict in talent_permission_level"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="是否已撤销" prop="revokeFlag">
            <el-select v-model="queryParams.revokeFlag" placeholder="全部" clearable style="width: 120px">
              <el-option label="未撤销" value="0" />
              <el-option label="已撤销" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item label="仅有效授权" prop="onlyActive">
            <el-switch v-model="queryParams.onlyActive" />
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
            <span class="panel-kicker">Scope Grant</span>
            <h3>人才共享授权</h3>
            <p>
              共 {{ total }} 条记录。授权级别 summary &lt; detail &lt; attachment 只表达「可查看该人才的资料级别」；
              授权过期立即失效，撤销保留审计轨迹。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['talent:grant:add']" type="primary" icon="Plus" @click="openAddDialog">
              新增授权
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="grantList">
        <el-table-column label="授权ID" align="center" prop="grantId" width="90" />
        <el-table-column label="人才主档ID" align="center" prop="talentId" width="120" />
        <el-table-column label="授权对象类型" align="center" width="130">
          <template #default="scope">{{ scope.row.granteeTypeLabel || granteeTypeText(scope.row.granteeType) }}</template>
        </el-table-column>
        <el-table-column label="授权对象" align="center" width="170" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.granteeName || scope.row.granteeId || '-' }}
            <span class="sub-text">（ID {{ scope.row.granteeId ?? '-' }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="授权级别" align="center" width="110">
          <template #default="scope">
            <dict-tag
              v-if="scope.row.permissionLevel"
              :options="talent_permission_level"
              :value="scope.row.permissionLevel"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="有效期起" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.validFrom) || '立即生效' }}</template>
        </el-table-column>
        <el-table-column label="有效期止" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.validTo) || '长期有效' }}</template>
        </el-table-column>
        <el-table-column label="授权事由" prop="grantReason" min-width="180" show-overflow-tooltip />
        <el-table-column label="授权人" align="center" width="110">
          <template #default="scope">{{ scope.row.grantedByName || scope.row.grantedBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="授权时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.grantedTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="是否已撤销" align="center" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.revokeFlag === '1' ? 'danger' : 'success'" size="small">
              {{ scope.row.revokeFlag === '1' ? '已撤销' : '有效' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="撤销时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.revokedTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" class-name="small-padding fixed-width" fixed="right">
          <template #default="scope">
            <el-tooltip content="撤销授权" placement="top">
              <el-button
                v-hasPermi="['talent:grant:revoke']"
                link
                type="danger"
                icon="CircleClose"
                :disabled="scope.row.revokeFlag === '1'"
                @click="revoke(scope.row)"
              ></el-button>
            </el-tooltip>
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

    <!-- 新增授权 -->
    <el-dialog v-model="addDialog.visible" title="新增共享授权" width="720px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="共享只扩大查看范围，不自动授予电话明文、附件下载、背调和导出权限（§8.19）。授权过期立即失效；有效期止必须晚于有效期起。"
      />
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="120px">
        <el-form-item label="人才主档ID" prop="talentId">
          <el-input v-model="addForm.talentId" placeholder="人才主档ID" readonly>
            <template #append>
              <el-button icon="Search" @click="openTalentPicker" />
            </template>
          </el-input>
          <span v-if="talentLabel" class="form-tip">已选择：{{ talentLabel }}</span>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="授权对象类型" prop="granteeType">
              <el-select v-model="addForm.granteeType" placeholder="请选择" style="width: 100%" @change="handleGranteeTypeChange">
                <el-option v-for="item in GRANTEE_TYPES" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="授权对象ID" prop="granteeId">
              <el-input v-model="addForm.granteeId" :placeholder="granteePlaceholder" clearable @click="openGranteeSelect">
                <template v-if="addForm.granteeType === 'user'" #append>
                  <el-button icon="User" @click="openGranteeSelect" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="授权级别" prop="permissionLevel">
              <el-select v-model="addForm.permissionLevel" placeholder="请选择" style="width: 100%">
                <el-option
                  v-for="dict in talent_permission_level"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="有效期起" prop="validFrom">
              <el-date-picker
                v-model="addForm.validFrom"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="留空表示立即生效"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期止" prop="validTo">
              <el-date-picker
                v-model="addForm.validTo"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="留空表示长期有效"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="授权事由" prop="grantReason">
          <el-input
            v-model="addForm.grantReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="必填，用于事后审计追溯"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="addDialog.loading" @click="submitAdd">确 定</el-button>
          <el-button @click="addDialog.visible = false">取 消</el-button>
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

    <UserSelect ref="granteeSelectRef" :multiple="false" @confirm-call-back="handleGranteeSelected" />
  </div>
</template>

<script setup lang="ts">
import { addGrant, listGrant, revokeGrant } from '@/api/hrtalent/grant';
import type { HrTalentScopeGrantForm, HrTalentScopeGrantQuery, HrTalentScopeGrantVO } from '@/api/hrtalent/grant/types';
import { listProfile } from '@/api/hrtalent/profile';
import type { HrTalentProfileVO } from '@/api/hrtalent/profile/types';
import UserSelect from '@/components/UserSelect/index.vue';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'HrTalentGrant' });

const { talent_permission_level, talent_status } = toRefs<any>(useDict('talent_permission_level', 'talent_status'));

/** 被授权主体类型（对齐后端 support/GrantSubject.TYPE_*，无字典组，取自后端常量） */
const GRANTEE_TYPES = [
  { label: '用户', value: 'user' },
  { label: '角色', value: 'role' },
  { label: '公司部门', value: 'company_dept' },
  { label: '部门', value: 'dept' }
];

const grantList = ref<HrTalentScopeGrantVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const addFormRef = ref<ElFormInstance>();
const granteeSelectRef = ref<InstanceType<typeof UserSelect>>();

const data = reactive<{ queryParams: HrTalentScopeGrantQuery }>({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    talentId: undefined,
    granteeType: undefined,
    granteeId: undefined,
    permissionLevel: undefined,
    revokeFlag: undefined,
    onlyActive: undefined
  }
});
const { queryParams } = toRefs(data);
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  pageSizeKey: 'pageSize',
  initialPageSize: 10,
  afterReset: () => handleQuery()
});

/** 授权对象类型中文兜底（后端另有 granteeTypeLabel） */
const granteeTypeText = (code?: string) => GRANTEE_TYPES.find(item => item.value === code)?.label || '-';

const getList = async () => {
  await withLoading(async () => {
    const res = await listGrant(queryParams.value);
    grantList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/* ------------------------------ 新增授权 ------------------------------ */

const addDialog = reactive<{ visible: boolean; loading: boolean }>({ visible: false, loading: false });
const initAddForm: HrTalentScopeGrantForm = {
  talentId: undefined,
  granteeType: 'user',
  granteeId: undefined,
  permissionLevel: 'summary',
  validFrom: undefined,
  validTo: undefined,
  grantReason: '',
  remark: ''
};
const addForm = ref<HrTalentScopeGrantForm>({ ...initAddForm });
const talentLabel = ref('');

/** 不同授权对象类型的ID提示 */
const granteePlaceholder = computed(() => {
  if (addForm.value.granteeType === 'user') {
    return '用户ID（可点右侧选择）';
  }
  if (addForm.value.granteeType === 'role') {
    return '角色ID';
  }
  if (addForm.value.granteeType === 'company_dept') {
    return '公司部门ID';
  }
  return '部门ID';
});

const validateValidRange = (_rule: any, value: any, callback: any) => {
  if (value && addForm.value.validFrom && value <= addForm.value.validFrom) {
    callback(new Error('有效期止必须晚于有效期起'));
    return;
  }
  callback();
};

const addRules: ElFormRules = {
  talentId: [{ required: true, message: '请先选择人才主档', trigger: 'change' }],
  granteeType: [{ required: true, message: '授权对象类型不能为空', trigger: 'change' }],
  granteeId: [{ required: true, message: '授权对象ID不能为空', trigger: 'blur' }],
  permissionLevel: [{ required: true, message: '授权级别不能为空', trigger: 'change' }],
  grantReason: [{ required: true, message: '授权事由不能为空', trigger: 'blur' }],
  validTo: [{ validator: validateValidRange, trigger: 'change' }]
};

const openAddDialog = () => {
  addForm.value = { ...initAddForm };
  talentLabel.value = queryParams.value.talentId ? `人才主档ID ${queryParams.value.talentId}` : '';
  if (queryParams.value.talentId) {
    addForm.value.talentId = queryParams.value.talentId;
  }
  addDialog.visible = true;
};

const handleGranteeTypeChange = () => {
  // 主体类型与主体ID 成对，切换类型时清空 ID，避免角色ID被当成用户ID 翻译
  addForm.value.granteeId = undefined;
};

const openGranteeSelect = () => {
  if (addForm.value.granteeType !== 'user') {
    return;
  }
  granteeSelectRef.value?.open();
};

const handleGranteeSelected = (users: any[]) => {
  const user = users?.[0];
  if (user) {
    addForm.value.granteeId = user.userId;
  }
};

const submitAdd = () => {
  addFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    addDialog.loading = true;
    try {
      await addGrant(addForm.value.talentId!, addForm.value);
      modal.msgSuccess('授权成功（共享只扩大查看范围，不授予电话明文/附件下载/背调/导出权限）');
      addDialog.visible = false;
      await getList();
    } finally {
      addDialog.loading = false;
    }
  });
};

/* ------------------------------ 撤销授权 ------------------------------ */

const revoke = async (row: HrTalentScopeGrantVO) => {
  let reason = '';
  try {
    const res: any = await modal.prompt('撤销后授权立即失效（保留审计轨迹），请填写撤销原因（可留空）');
    reason = res?.value || '';
  } catch {
    return;
  }
  await revokeGrant(row.grantId!, reason || undefined);
  modal.msgSuccess('已撤销该共享授权');
  await getList();
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

/** 选择人才：若新增弹窗已打开则回填表单，否则作为检索条件 */
const pickTalent = (row: HrTalentProfileVO) => {
  talentPicker.visible = false;
  if (addDialog.visible) {
    addForm.value.talentId = row.talentId;
    talentLabel.value = `${row.name || '-'}（${row.talentNo || '-'}）`;
  } else {
    queryParams.value.talentId = row.talentId;
    handleQuery();
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

.dialog-alert {
  margin-bottom: 12px;
}

.sub-text {
  font-size: 12px;
  color: var(--app-text-muted);
}

.form-tip {
  font-size: 12px;
  color: var(--app-text-muted);
}
</style>
