<template>
  <div class="app-container">
    <el-card shadow="never" class="workspace-shell">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">AI 工作台</span>
            <h3>{{ roleHome?.roleName || '我的 AI 工作台' }}</h3>
            <p>
              这里只有<b>对你开放</b>的岗位与卡片（由岗位的发布状态与组织/品牌绑定决定，服务端过滤）。
              只读门户：卡片可以查看，页面类卡片可以直接打开；需要启动凭证的能力在下一增量接入。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-if="defaultRoleCode" plain icon="Close" @click="handleClearDefault">清空默认岗位</el-button>
            <el-button icon="Refresh" @click="reload">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="!loadingRoles && roles.length === 0"
        type="info"
        :closable="false"
        title="当前没有对你开放的岗位"
        description="岗位由管理员发布并按组织/品牌授权；若你预期应该看到某个岗位，请联系岗位负责人确认绑定范围。"
      />

      <div v-else class="role-strip">
        <article
          v-for="role in roles"
          :key="role.roleCode"
          class="role-card"
          :class="{ 'role-card-active': role.roleCode === selectedRoleCode }"
          @click="selectRole(role.roleCode)"
        >
          <div class="role-card-top">
            <h4>{{ role.roleName || role.roleCode }}</h4>
            <div class="role-card-actions">
              <el-tag v-if="role.version" effect="plain" size="small">{{ role.version }}</el-tag>
              <el-button
                link
                :type="isFavorite(favorites, role.roleCode) ? 'warning' : 'info'"
                :icon="isFavorite(favorites, role.roleCode) ? 'StarFilled' : 'Star'"
                :title="isFavorite(favorites, role.roleCode) ? '取消收藏' : '收藏（收藏的岗位会排在前面）'"
                @click.stop="handleToggleFavorite(role.roleCode)"
              />
              <el-tag v-if="defaultRoleCode === role.roleCode" size="small" type="success" effect="plain">默认</el-tag>
              <el-button
                v-else
                link
                type="info"
                title="设为默认打开的岗位"
                @click.stop="handleSetDefault(role.roleCode)"
              >
                设为默认
              </el-button>
            </div>
          </div>
          <p class="role-desc">{{ role.description || '暂无岗位说明' }}</p>
          <div class="role-meta">
            <el-tag v-for="category in role.categories" :key="category.code" size="small" type="info" effect="plain">
              {{ category.name || category.code }}{{ (category.actionCount || 0) > 0 ? ' · ' + category.actionCount : '' }}
            </el-tag>
            <span class="role-count">{{ role.actionCount || 0 }} 张可用卡片</span>
          </div>
        </article>
      </div>

      <template v-if="selectedRoleCode">
        <el-divider content-position="left">能力卡片</el-divider>
        <el-alert
          v-if="!loadingHome && actionGroups.length === 0"
          type="warning"
          :closable="false"
          title="这个岗位当前没有启用中的卡片"
        />
        <section v-for="group in actionGroups" :key="group.code" class="action-group">
          <h4 class="group-title">{{ group.name }}（{{ group.actions.length }}）</h4>
          <div class="action-grid">
            <article v-for="action in group.actions" :key="action.actionCode" class="action-card">
              <div class="action-top">
                <h5>{{ action.title || action.actionCode }}</h5>
                <el-tag size="small" :type="isNavigable(action) ? 'success' : 'info'" effect="plain">
                  {{ launchModeLabel(action.launchMode) }}
                </el-tag>
              </div>
              <p class="action-desc">{{ action.description || '暂无卡片说明' }}</p>
              <div class="action-foot">
                <span class="action-target">{{ action.targetRef || '—' }}</span>
                <el-button v-if="isNavigable(action)" type="primary" plain size="small" @click="openPage(action)">
                  打开
                </el-button>
                <el-button v-else type="primary" size="small" @click="openLaunch(action)">启动</el-button>
              </div>
            </article>
          </div>
        </section>
      </template>
    </el-card>

    <el-card shadow="never" class="mt-3">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <h3>我的任务</h3>
            <p>只显示<b>你自己</b>发起的任务（服务端固定按登录用户过滤）；成本、供应商与排查线索不在这里展示。</p>
          </div>
          <div class="toolbar-actions">
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getTasks"></right-toolbar>
          </div>
        </div>
      </template>

      <el-form v-show="showSearch" :inline="true" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="taskQuery.status" clearable placeholder="全部" style="width: 160px">
            <el-option v-for="item in taskStatusOptions" :key="item.code" :label="item.label" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务类型">
          <el-input v-model="taskQuery.taskType" clearable placeholder="如 CREATIVE" style="width: 180px" @keyup.enter="handleTaskQuery" />
        </el-form-item>
        <el-form-item label="场景编码">
          <el-input v-model="taskQuery.scenarioCode" clearable placeholder="talent_match" style="width: 180px" @keyup.enter="handleTaskQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleTaskQuery">查询</el-button>
          <el-button icon="Refresh" @click="resetTaskQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loadingTasks" border class="data-table" :data="tasks">
        <el-table-column label="任务编号" prop="taskNo" min-width="170" show-overflow-tooltip />
        <el-table-column label="任务类型" prop="taskType" align="center" width="130" />
        <el-table-column label="场景" prop="scenarioCode" min-width="150" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.scenarioCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="110">
          <template #default="scope">
            <el-tag :type="taskStatusMeta(scope.row.status, scope.row.statusLabel).tag">
              {{ taskStatusMeta(scope.row.status, scope.row.statusLabel).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" align="center" width="90">
          <template #default="scope">{{ scope.row.progress ?? 0 }}%</template>
        </el-table-column>
        <el-table-column label="数据等级" prop="dataLevel" align="center" width="110" />
        <el-table-column label="开始时间" prop="startedAt" align="center" width="170" show-overflow-tooltip />
        <el-table-column label="结束时间" prop="finishedAt" align="center" width="170" show-overflow-tooltip />
      </el-table>

      <pagination
        v-show="taskTotal > 0"
        v-model:page="taskQuery.pageNum"
        v-model:limit="taskQuery.pageSize"
        :total="taskTotal"
        @pagination="getTasks"
      />
    </el-card>

    <el-card shadow="never" class="mt-3">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <h3>我的产物</h3>
            <p>
              来自<b>平台产物台账</b>：只列出<b>我提交的任务</b>下<b>已通过校验</b>的产物，且只给元数据。
              下载请到对应专业台/任务页——那里的权限仍然生效。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button icon="Refresh" @click="getArtifacts">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loadingArtifacts" border class="data-table" :data="artifacts">
        <el-table-column label="产物ID" prop="artifactId" align="center" width="180" show-overflow-tooltip />
        <el-table-column label="来源任务" align="center" min-width="170" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.taskNo || scope.row.taskId }}</template>
        </el-table-column>
        <el-table-column label="类型" prop="artifactType" align="center" width="150" />
        <el-table-column label="MIME" prop="mimeType" align="center" min-width="140" show-overflow-tooltip />
        <el-table-column label="大小" align="center" width="110">
          <template #default="scope">{{ formatBytes(scope.row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column label="入库时间" prop="createTime" align="center" width="170" show-overflow-tooltip />
      </el-table>

      <pagination
        v-show="artifactTotal > 0"
        v-model:page="artifactQuery.pageNum"
        v-model:limit="artifactQuery.pageSize"
        :total="artifactTotal"
        @pagination="getArtifacts"
      />
    </el-card>

    <LaunchFormDrawer
      v-model="launchVisible"
      :role-code="selectedRoleCode"
      :action="launchAction"
      @launched="handleLaunched"
    />
  </div>
</template>

<script setup lang="ts">
import {
  getRoleHome,
  getWorkspacePref,
  listMyArtifacts,
  listMyRoles,
  listMyTasks,
  setDefaultRole,
  toggleFavorite
} from '@/api/aigov/portal';
import type {
  AigLaunchCommitVO,
  AigPortalActionVO,
  AigPortalArtifactQuery,
  AigPortalArtifactVO,
  AigPortalRoleHomeVO,
  AigPortalRoleVO,
  AigPortalTaskQuery,
  AigPortalTaskVO
} from '@/api/aigov/portal/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import LaunchFormDrawer from './LaunchFormDrawer.vue';
import {
  formatBytes,
  groupPortalActions,
  isFavorite,
  isNavigable,
  launchModeLabel,
  sortRolesByFavorite,
  taskStatusMeta
} from './presentation';
import { buildProfessionalUrl, resolveProfessionalPath } from './professionalLink';

defineOptions({ name: 'AiWorkspace' });

const router = useRouter();

const roles = ref<AigPortalRoleVO[]>([]);
const selectedRoleCode = ref('');
const roleHome = ref<AigPortalRoleHomeVO>();
const { loading: loadingRoles, withLoading: withRolesLoading } = useLoading(true);
const { loading: loadingHome, withLoading: withHomeLoading } = useLoading(false);

const { showSearch } = useSearchToggle();

const tasks = ref<AigPortalTaskVO[]>([]);
const taskTotal = ref(0);
const { loading: loadingTasks, withLoading: withTasksLoading } = useLoading(true);
const taskQuery = ref<AigPortalTaskQuery>({ pageNum: 1, pageSize: 10 });

/** 启动表单（非导航类卡片走这里：预检 → 确认） */
const launchVisible = ref(false);
const launchAction = ref<AigPortalActionVO>();

/** 偏好（收藏 + 默认岗位）；只是"先看哪个"，不参与可见性判定 */
const favorites = ref<string[]>([]);
const defaultRoleCode = ref('');

/** 我的产物（平台产物台账；只给我的任务下已通过校验的） */
const artifacts = ref<AigPortalArtifactVO[]>([]);
const artifactTotal = ref(0);
const { loading: loadingArtifacts, withLoading: withArtifactsLoading } = useLoading(true);
const artifactQuery = ref<AigPortalArtifactQuery>({ pageNum: 1, pageSize: 10 });

/** 任务状态选项（与后端 AigTaskStatusEnum 一致） */
const taskStatusOptions = [
  { code: 'PENDING', label: '待处理' },
  { code: 'RUNNING', label: '进行中' },
  { code: 'SUCCEEDED', label: '成功' },
  { code: 'FAILED', label: '失败' },
  { code: 'CANCELED', label: '已取消' }
];

const actionGroups = computed(() => groupPortalActions(roleHome.value?.categories, roleHome.value?.actions));

/**
 * 读偏好（收藏 + 默认岗位）。
 *
 * 失败不影响门户主流程：偏好看不到时按"没有收藏、没有默认"处理——它是附加信息，
 * 不该让员工打不开工作台。
 */
const getPref = async () => {
  try {
    const res = await getWorkspacePref();
    favorites.value = res.data.favorites || [];
    defaultRoleCode.value = res.data.defaultRoleCode || '';
  } catch {
    favorites.value = [];
    defaultRoleCode.value = '';
  }
};

const getRoles = async () => {
  await withRolesLoading(async () => {
    const res = await listMyRoles();
    // 展示哪些岗位只认服务端返回的列表；收藏只用来排序（收藏里可能有已不可见的岗位）
    roles.value = sortRolesByFavorite(res.data || [], favorites.value);
    if (roles.value.length === 0) {
      selectedRoleCode.value = '';
      roleHome.value = undefined;
      return;
    }
    // 优先打开默认岗位，其次保留当前选择，最后取第一个：
    // 员工打开工作台应当立刻看到卡片，而不是一个空的选择器
    const preferred = roles.value.find(role => role.roleCode === defaultRoleCode.value);
    const stillVisible = roles.value.some(role => role.roleCode === selectedRoleCode.value);
    const target = stillVisible ? selectedRoleCode.value : preferred?.roleCode || roles.value[0].roleCode;
    await selectRole(target);
  });
};

const selectRole = async (roleCode: string) => {
  selectedRoleCode.value = roleCode;
  await withHomeLoading(async () => {
    const res = await getRoleHome(roleCode);
    roleHome.value = res.data;
  });
};

/**
 * 收藏/取消收藏。
 *
 * 服务端会校验"这个岗位对本人可见"；失败时把提示原样显示，**不改本地状态**
 * （本地先改再回滚会让人以为改成功了）。
 */
const handleToggleFavorite = async (roleCode: string) => {
  try {
    const res = await toggleFavorite(roleCode, !isFavorite(favorites.value, roleCode));
    favorites.value = res.data.favorites || [];
    roles.value = sortRolesByFavorite(roles.value, favorites.value);
  } catch (e: any) {
    modal.alertError(e?.message || '操作失败');
  }
};

/** 设为默认打开的岗位（传空即清空，所以这里只做"设为默认"） */
const handleSetDefault = async (roleCode: string) => {
  try {
    const res = await setDefaultRole(roleCode);
    defaultRoleCode.value = res.data.defaultRoleCode || '';
    modal.msgSuccess('已设为默认岗位');
  } catch (e: any) {
    modal.alertError(e?.message || '操作失败');
  }
};

/**
 * 清空默认岗位。
 *
 * 单独给一个入口：只能"换一个默认"而没有"取消默认"时，用户没法表达"我不想有默认"。
 */
const handleClearDefault = async () => {
  try {
    const res = await setDefaultRole('');
    defaultRoleCode.value = res.data.defaultRoleCode || '';
    modal.msgSuccess('已清空默认岗位');
  } catch (e: any) {
    modal.alertError(e?.message || '操作失败');
  }
};

const openPage = (action: AigPortalActionVO) => {
  const path = resolveProfessionalPath(action);
  if (!path) {
    modal.msgWarning('这张卡片的目标不在白名单里，无法打开（请联系岗位负责人核对配置）');
    return;
  }
  // 带 query 跳转：专业页读的是 location.search，把 taskId 放别处等于没带（见 professionalLink 的说明）
  router.push(buildProfessionalUrl(path, { roleCode: selectedRoleCode.value, actionCode: action.actionCode }));
};

const getTasks = async () => {
  await withTasksLoading(async () => {
    const res = await listMyTasks(taskQuery.value);
    tasks.value = res.data.rows || [];
    taskTotal.value = res.data.total || 0;
  });
};

const getArtifacts = async () => {
  await withArtifactsLoading(async () => {
    const res = await listMyArtifacts(artifactQuery.value);
    artifacts.value = res.data.rows || [];
    artifactTotal.value = res.data.total || 0;
  });
};

/**
 * 打开启动表单（非导航类卡片）。
 *
 * 页面类卡片不在这里：它们直接打开页面（`openPage`），不需要启动凭证。
 */
const openLaunch = (action: AigPortalActionVO) => {
  launchAction.value = action;
  launchVisible.value = true;
};

/**
 * 启动成功后的收尾（增量 4 的桥接点）。
 *
 * 卡片指向专业页时（页面类、以及 STUDIO 类专业台）**必须把 taskId 带进 URL query**：
 * 专业页读的是 `location.search`，它据此知道自己是在处理哪一次启动，也据此显示"返回 AI 工作台"。
 * 不指向专业页的（轻量能力/表单类）就刷新"我的任务"，让员工立刻看到刚建出来的任务。
 */
const handleLaunched = async (result: AigLaunchCommitVO) => {
  const action = launchAction.value;
  const path = resolveProfessionalPath(action);
  if (action && path) {
    router.push(
      buildProfessionalUrl(path, {
        taskId: result.taskId,
        roleCode: selectedRoleCode.value,
        actionCode: action.actionCode
      })
    );
    return;
  }
  await getTasks();
};

const handleTaskQuery = () => {
  taskQuery.value.pageNum = 1;
  getTasks();
};

const resetTaskQuery = () => {
  taskQuery.value = { pageNum: 1, pageSize: taskQuery.value.pageSize };
  getTasks();
};

const reload = async () => {
  // 先读偏好再读岗位：收藏决定排序、默认岗位决定先打开哪一个
  await getPref();
  await getRoles();
  await getTasks();
  await getArtifacts();
};

onMounted(() => {
  reload();
});
</script>

<style scoped>
.role-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.role-card {
  flex: 1 1 280px;
  max-width: 360px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.role-card:hover {
  border-color: var(--el-color-primary);
}

.role-card-active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.role-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.role-card-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.role-card-top h4 {
  margin: 0;
}

.role-desc {
  margin: 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  min-height: 36px;
}

.role-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.role-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.action-group {
  margin-bottom: 14px;
}

.group-title {
  margin: 6px 0;
}

.action-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.action-card {
  flex: 1 1 300px;
  max-width: 380px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
}

.action-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.action-top h5 {
  margin: 0;
}

.action-desc {
  margin: 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  min-height: 36px;
}

.action-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.action-target {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mt-3 {
  margin-top: 12px;
}
</style>
