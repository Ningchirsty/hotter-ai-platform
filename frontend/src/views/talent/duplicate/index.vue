<template>
  <div class="p-2 app-container talent-duplicate-page">
    <PageHeading title="人才管理" subtitle="档案、附件与业务记录，在同一个工作空间有序连接" />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>筛选条件</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="来源人才姓名" prop="sourceName">
            <el-input v-model="queryParams.sourceName" placeholder="请输入来源人才姓名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="命中人才姓名" prop="matchedName">
            <el-input v-model="queryParams.matchedName" placeholder="请输入命中人才姓名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="匹配规则" prop="matchRule">
            <el-select v-model="queryParams.matchRule" placeholder="请选择匹配规则" clearable style="width: 200px">
              <el-option v-for="item in matchRuleOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="确认结论" prop="conclusion">
            <el-select v-model="queryParams.conclusion" placeholder="请选择确认结论" clearable style="width: 180px">
              <el-option v-for="dict in tl_duplicate_conclusion" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
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
            <span class="panel-kicker">Duplicate Alerts</span>
            <h3>重复人才预警</h3>
            <p>共 {{ total }} 条记录；系统只预警不自动合并，需人工确认为「不同人」或「同一人」。</p>
          </div>
          <div class="toolbar-actions">
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="duplicateList">
        <el-table-column label="来源人才编号" align="center" prop="sourceTalentNo" width="150" />
        <el-table-column label="来源姓名" align="center" prop="sourceName" width="110" show-overflow-tooltip />
        <el-table-column label="命中人才编号" align="center" prop="matchedTalentNo" width="150" />
        <el-table-column label="命中姓名" align="center" prop="matchedName" width="110" show-overflow-tooltip />
        <el-table-column label="命中区域" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="tl_region" :value="scope.row.matchedRegionCode" />
          </template>
        </el-table-column>
        <el-table-column label="匹配规则" align="center" width="170">
          <template #default="scope">{{ matchRuleLabel(scope.row.matchRule) }}</template>
        </el-table-column>
        <el-table-column label="匹配分数" align="center" prop="matchScore" width="100" />
        <el-table-column label="确认结论" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="tl_duplicate_conclusion" :value="scope.row.conclusion" />
          </template>
        </el-table-column>
        <el-table-column label="确认人" align="center" prop="confirmByName" width="110" />
        <el-table-column label="确认时间" align="center" width="180">
          <template #default="scope">{{ parseTime(scope.row.confirmTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="确认说明" align="center" prop="confirmRemark" show-overflow-tooltip />
        <el-table-column label="操作" width="110" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              v-hasPermi="['talent:duplicate:confirm']"
              link
              type="primary"
              icon="Select"
              :disabled="scope.row.conclusion !== 'PENDING'"
              @click="handleConfirm(scope.row)"
            ></el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="520px" append-to-body>
      <el-form ref="confirmFormRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="来源人才">
          <span>{{ currentRow?.sourceName || '-' }}（{{ currentRow?.sourceTalentNo || '-' }}）</span>
        </el-form-item>
        <el-form-item label="命中人才">
          <span>{{ currentRow?.matchedName || '-' }}（{{ currentRow?.matchedTalentNo || '-' }}）</span>
        </el-form-item>
        <el-form-item label="确认结论" prop="conclusion">
          <el-radio-group v-model="form.conclusion">
            <el-radio value="DIFFERENT">不同人（可正常入库）</el-radio>
            <el-radio value="SAME">同一人（转入已有档案，不自动合并）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="确认说明" prop="confirmRemark">
          <el-input v-model="form.confirmRemark" type="textarea" :rows="3" placeholder="请输入确认说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { TalentDuplicateQuery, TalentDuplicateVO } from '@/api/talent/duplicate/types';
import { confirmDuplicate, listDuplicate } from '@/api/talent/duplicate';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'TalentDuplicate' });

const { tl_duplicate_conclusion, tl_region } = toRefs<any>(useDict('tl_duplicate_conclusion', 'tl_region'));

const matchRuleOptions = [
  { value: 'PHONE_HASH', label: '手机号完全一致' },
  { value: 'NAME_PHONE_TAIL4', label: '姓名 + 手机后四位' },
  { value: 'NAME_REGION', label: '姓名 + 同区域' }
];
const matchRuleLabel = (value?: string) => matchRuleOptions.find(item => item.value === value)?.label || value || '-';

const duplicateList = ref<TalentDuplicateVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const confirmFormRef = ref<ElFormInstance>();
const currentRow = ref<TalentDuplicateVO | null>(null);

const dialog = reactive<DialogOption>({ visible: false, title: '重复人才确认' });

const data = reactive<{ form: { duplicateId: string | number | undefined; conclusion: string; confirmRemark: string }; queryParams: TalentDuplicateQuery; rules: ElFormRules }>({
  form: { duplicateId: undefined, conclusion: 'DIFFERENT', confirmRemark: '' },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    sourceName: '',
    matchedName: '',
    matchRule: undefined,
    conclusion: undefined
  },
  rules: {
    conclusion: [{ required: true, message: '确认结论不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 查询重复预警列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listDuplicate(queryParams.value);
    duplicateList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const handleConfirm = (row: TalentDuplicateVO) => {
  currentRow.value = row;
  form.value = { duplicateId: row.duplicateId, conclusion: 'DIFFERENT', confirmRemark: '' };
  dialog.title = '重复人才确认';
  dialog.visible = true;
};

const submitForm = () => {
  confirmFormRef.value?.validate(async (valid: boolean) => {
    if (!valid || !form.value.duplicateId) return;
    await confirmDuplicate({
      duplicateId: form.value.duplicateId,
      conclusion: form.value.conclusion,
      confirmRemark: form.value.confirmRemark
    });
    modal.msgSuccess('确认成功');
    dialog.visible = false;
    await getList();
  });
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;
</style>
