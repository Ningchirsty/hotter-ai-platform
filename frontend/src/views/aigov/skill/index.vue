<template>
  <div class="p-2 app-container aigov-skill-page">
    <PageHeading title="AI平台治理" subtitle="管理AI能力、模型接入与调用策略" admin module="aigov" />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>Skill 检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="Skill编码" prop="skillCode">
            <el-input v-model="queryParams.skillCode" placeholder="精确匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="名称" prop="skillName">
            <el-input v-model="queryParams.skillName" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="能力编码" prop="capabilities">
            <el-input v-model="queryParams.capabilities" placeholder="如 image_generation" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="来源" prop="builtin">
            <el-select v-model="queryParams.builtin" placeholder="请选择来源" clearable style="width: 160px">
              <el-option label="平台内置" value="Y" />
              <el-option label="Package 带入" value="N" />
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
            <span class="panel-kicker">Skill Registry</span>
            <h3>Skill 注册（可复用原子能力）</h3>
            <p>共 {{ total }} 条记录；Skill 是「一个可复用的原子能力」，Agent 通过绑定使用它。</p>
          </div>
          <div class="toolbar-actions">
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="skillList">
        <el-table-column label="Skill编码" align="center" prop="skillCode" width="200" show-overflow-tooltip />
        <el-table-column label="名称" align="center" prop="skillName" width="160" show-overflow-tooltip />
        <el-table-column label="能力" align="center" width="240">
          <template #default="scope">
            <template v-if="splitTags(scope.row.capabilities).length">
              <el-tag v-for="tag in splitTags(scope.row.capabilities)" :key="tag" class="tag-item" type="info">
                {{ tag }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" align="center" width="130">
          <template #default="scope">
            <el-tag :type="scope.row.builtin === 'Y' ? 'success' : 'warning'">
              {{ scope.row.builtin === 'Y' ? '平台内置' : 'Package 带入' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" align="center" prop="description" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="120" fixed="right">
          <template #default="scope">
            <el-button v-hasPermi="['aig:skill:list']" link type="primary" icon="View" @click="openVersions(scope.row)">
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

    <el-dialog v-model="versionVisible" :title="'版本 · ' + (currentSkill?.skillName || '')" width="900px" append-to-body>
      <el-alert type="info" :closable="false" class="mb-2">
        第三方 Package 带入的 Skill 版本会在「来源Package版本」里显示指向的 Package 版本；
        发布门槛与 Agent 版本一致（DRAFT 起，逐道过）。
      </el-alert>
      <!--
        M-004：provider_capability 是**声明**字段，全仓库没有任何路由/调用代码读它
        （已核实）。它原本的表头只写「能力」，很容易被读成"这个 Skill 走哪个模型"——
        而真正决定选型的是「能力 × 模型绑定」（aig_capability_model）。
        表头用「声明能力」并在下面备一句说明，是为了让"它不参与路由"这件事在界面上可读，
        而不是只写在某份手册里。
      -->
      <div class="capability-declaration-note">
        下表「声明能力」只表示该版本<b>声明</b>具备哪类能力，<b>不参与路由选型</b>；
        实际调用哪个模型由「能力 → 模型绑定」（治理台「模型绑定」页）决定。
      </div>
      <el-table v-loading="versionLoading" border :data="versionList">
        <el-table-column label="版本" align="center" prop="version" width="90" />
        <el-table-column label="发布状态" align="center" width="140" prop="releaseStatus" />
        <el-table-column label="通道" align="center" prop="releaseChannel" width="110" />
        <el-table-column label="声明能力" align="center" prop="providerCapability" width="160" show-overflow-tooltip />
        <el-table-column label="外部调用" align="center" width="100">
          <template #default="scope">{{ scope.row.allowExternal === 'Y' ? '允许' : '禁止' }}</template>
        </el-table-column>
        <el-table-column label="来源Package版本" align="center" prop="packageVersionId" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { listSkill, listSkillVersion } from '@/api/aigov/skill';
import type { AigSkillQuery, AigSkillVO, AigSkillVersionVO } from '@/api/aigov/skill/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';

defineOptions({ name: 'AigSkillRegistry' });

const skillList = ref<AigSkillVO[]>([]);
const versionList = ref<AigSkillVersionVO[]>([]);
const currentSkill = ref<AigSkillVO>();
const { loading, withLoading } = useLoading(true);
const { loading: versionLoading, withLoading: withVersionLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const versionVisible = ref(false);

const queryParams = ref<AigSkillQuery>({
  pageNum: 1,
  pageSize: 10,
  params: {}
});

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 逗号分隔的能力清单 */
const splitTags = (value?: string) => (value || '').split(',').map((item) => item.trim()).filter(Boolean);

/** 查询 Skill 清单 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listSkill(queryParams.value);
    skillList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 搜索 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 打开版本列表 */
const openVersions = async (row: AigSkillVO) => {
  currentSkill.value = row;
  versionVisible.value = true;
  await withVersionLoading(async () => {
    const res = await listSkillVersion({ pageNum: 1, pageSize: 100, skillId: row.skillId });
    versionList.value = res.data?.rows || [];
  });
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

/* M-004：说明「声明能力」不参与路由。做成一条不抢眼的注释风格提示，
   而不是弹窗或红字——它是口径说明，不是故障。 */
.capability-declaration-note {
  margin-bottom: 8px;
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-left: 3px solid var(--el-border-color);
  border-radius: 2px;
}
</style>
