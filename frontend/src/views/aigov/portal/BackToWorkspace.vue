<template>
  <div v-if="record" class="back-bar">
    <el-icon><Back /></el-icon>
    <span class="back-text">
      这次任务来自岗位
      <b>{{ record.roleName || record.roleCode }}</b>
      的卡片「{{ record.actionCode }}」
    </span>
    <el-button link type="primary" icon="Guide" @click="backToWorkspace">返回 AI 工作台</el-button>
  </div>
</template>

<script setup lang="ts">
import { getLaunchRecordByTask } from '@/api/aigov/portal';
import type { AigLaunchRecordVO } from '@/api/aigov/portal/types';
import { buildWorkspaceBackUrl, readLaunchContext } from './professionalLink';

/**
 * "返回 AI 工作台"入口（主文档线增量 4）。
 *
 * <h3>它为什么不是每页各写一遍</h3>
 * <p>创作域有五个页面，各自都会读 URL query 里的 {@code taskId}。把入口做成一个组件，
 * 专业页只挂一个标签——否则五处"找回跳上下文"的代码迟早出现四份和一份不一样。</p>
 *
 * <h3>为什么查不到时就什么都不显示</h3>
 * <p>这个入口是**附加信息**：任务不是由岗位卡片发起（或不是自己发起的）时，专业页本来就该照常工作。
 * 在这里弹一个错误只会让员工以为页面坏了。所以失败即静默隐藏。</p>
 *
 * @author ai-gov
 */
const props = defineProps<{
  /** 任务ID；不传则从 location.search 读（专业页读的就是它） */
  taskId?: string | number;
}>();

const router = useRouter();
const record = ref<AigLaunchRecordVO>();
const context = ref(readLaunchContext(typeof location === 'undefined' ? '' : location.search));

const resolveTaskId = () => {
  if (props.taskId !== undefined && props.taskId !== null && String(props.taskId) !== '') {
    return String(props.taskId);
  }
  return context.value.taskId;
};

onMounted(async () => {
  const taskId = resolveTaskId();
  if (!taskId) {
    return;
  }
  try {
    const res = await getLaunchRecordByTask(taskId);
    record.value = res.data;
  } catch {
    // 不是由岗位卡片发起（或不是自己发起的）：静默隐藏，不影响专业页
    record.value = undefined;
  }
});

const backToWorkspace = () => {
  const roleCode = record.value?.roleCode || context.value.roleCode;
  const actionCode = record.value?.actionCode || context.value.actionCode;
  router.push(buildWorkspaceBackUrl({ roleCode, actionCode }));
};
</script>

<style scoped>
.back-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  font-size: 13px;
}

.back-text {
  color: var(--el-text-color-regular);
}
</style>
