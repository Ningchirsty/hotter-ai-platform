<template>
  <div class="page-heading-wrap">
    <div class="page-heading">
      <div class="page-heading-main">
        <h1 class="page-heading-title">{{ title }}</h1>
        <p v-if="subtitle" class="page-heading-subtitle">{{ subtitle }}</p>
      </div>
      <div class="page-heading-side">
        <span v-if="admin" class="page-heading-badge">
          <el-icon><Lock /></el-icon>
          {{ adminLabel }}
        </span>
        <slot name="actions"></slot>
      </div>
    </div>

    <!-- 模块内标签页：同一模块的兄弟页在这条切换（设计规范：12px、激活项墨蓝下划线） -->
    <nav v-if="tabs.length" class="page-heading-tabs" aria-label="模块内页面切换">
      <button
        v-for="tab in tabs"
        :key="tab.path"
        type="button"
        class="page-heading-tab"
        :class="{ 'is-active': tab.path === route.path }"
        :aria-current="tab.path === route.path ? 'page' : undefined"
        @click="go(tab.path)"
      >
        {{ tab.label }}
      </button>
    </nav>
  </div>
</template>

<script setup lang="ts" name="PageHeading">
/**
 * 工作空间页面标题行 + 模块内标签页。
 * 设计规范：页面标题约 22px、副标题 12px、面板标题 14px；管理类页面用角标标明访问范围。
 * 颜色/圆角取工程既有 token，不引入新变量。
 */
const props = withDefaults(
  defineProps<{
    title: string;
    subtitle?: string;
    admin?: boolean;
    adminLabel?: string;
    /** 所属模块，决定标题行下方展示哪组模块内标签页 */
    module?: '' | 'hrtalent' | 'aigov' | 'content';
  }>(),
  {
    subtitle: '',
    admin: false,
    adminLabel: '管理员专属',
    module: ''
  }
);

const route = useRoute();
const router = useRouter();

/** 模块内页面顺序与真实路由（与数据库菜单 path 保持一致；隐藏页不列入） */
const MODULE_TABS: Record<string, { label: string; path: string }[]> = {
  // 招聘与人才管理一体化系统：一级目录 招聘管理 path=recruit，
  // 人才管理为二级目录 path=talent（无 component，后端下发 ParentView），
  // 故人才相关页面路由为 /recruit/talent/*。路径与 sys_menu.path 逐字一致。
  hrtalent: [
    { label: '管理驾驶舱', path: '/recruit/dashboard' },
    { label: '招聘需求', path: '/recruit/demand' },
    { label: '公司月度计划', path: '/recruit/plan' },
    { label: '候选人跟进', path: '/recruit/application' },
    { label: '面试管理', path: '/recruit/interview' },
    { label: '人才档案', path: '/recruit/talent/profile' },
    { label: '人才池与分组', path: '/recruit/talent/pool' },
    { label: '重复人才治理', path: '/recruit/talent/duplicate' }
  ],
  aigov: [
    { label: 'AI能力目录', path: '/admin-center/ai-gov/capability' },
    { label: '模型注册中心', path: '/admin-center/ai-gov/model' },
    { label: '路由策略', path: '/admin-center/ai-gov/route' },
    { label: '调用审计', path: '/admin-center/ai-gov/audit' }
  ],
  // 内容生产协同挂在「业务应用」下，故路由前缀为 /business/content
  content: [
    { label: '内容任务', path: '/business/content/task' },
    { label: '互动确认卡', path: '/business/content/card' },
    { label: '设计开工包', path: '/business/content/workPackage' },
    { label: '产品与SKU', path: '/business/content/product' },
    { label: '闸门规则', path: '/business/content/gateRule' }
  ]
};

const tabs = computed(() => MODULE_TABS[props.module as string] || []);

const go = (path: string) => {
  if (path !== route.path) {
    router.push(path);
  }
};
</script>

<style lang="scss" scoped>
.page-heading-wrap {
  min-width: 0;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.page-heading-main {
  min-width: 0;
}

.page-heading-title {
  margin: 0;
  font-size: 22px;
  font-weight: 500;
  line-height: 1.4;
  letter-spacing: 0.6px;
  color: var(--app-text-title);
}

.page-heading-subtitle {
  margin: 5px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}

.page-heading-side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding-top: 4px;
}

.page-heading-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.6;
  white-space: nowrap;
  color: #6d8398;
  background: #edf2f6;
}

.page-heading-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 14px;
  border-bottom: 1px solid #dee5eb;
}

.page-heading-tab {
  padding: 0 0 11px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  font-size: 12px;
  line-height: 1.4;
  color: #8090a0;
  cursor: pointer;
  transition: color 0.18s ease;
}

.page-heading-tab:hover {
  color: #2f4b66;
}

.page-heading-tab.is-active {
  color: #2f4b66;
  border-bottom-color: #708da7;
  cursor: default;
}

@media (max-width: 600px) {
  .page-heading {
    flex-wrap: wrap;
  }

  .page-heading-title {
    font-size: 20px;
    letter-spacing: 0.3px;
  }

  .page-heading-tabs {
    gap: 16px;
    overflow-x: auto;
    flex-wrap: nowrap;
    scrollbar-width: none;
  }

  .page-heading-tabs::-webkit-scrollbar {
    height: 0;
  }

  .page-heading-tab {
    white-space: nowrap;
  }
}
</style>
