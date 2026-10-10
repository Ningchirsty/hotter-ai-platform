<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="720px"
    append-to-body
    @update:model-value="close"
  >
    <el-alert type="info" :closable="false" class="mb-2">
      {{ START_PENDING_HINT }}
    </el-alert>

    <el-form label-width="120px">
      <el-form-item label="卡片">
        <span class="card-title">{{ action?.title || action?.actionCode }}</span>
        <el-tag class="ml-2" size="small" type="info" effect="plain">
          {{ launchModeLabel(action?.launchMode) }}
        </el-tag>
        <span v-if="prepared?.willCreateTask === false" class="hint">这次启动只记录审计，不会创建任务</span>
      </el-form-item>

      <el-form-item v-if="needsTask" label="任务类型" required>
        <el-select v-model="form.taskType" style="width: 100%">
          <el-option v-for="item in TASK_TYPE_OPTIONS" :key="item.code" :label="item.label" :value="item.code" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="needsTask" label="业务域" required>
        <el-input v-model="form.projectType" placeholder="如 creative / content / video" />
      </el-form-item>

      <el-form-item label="项目ID">
        <el-input v-model="form.projectId" placeholder="可空；填了表示这次挂到该项目下" />
      </el-form-item>

      <el-form-item v-if="needsTask" label="数据等级" required>
        <el-select v-model="form.dataLevel" style="width: 100%">
          <el-option label="PUBLIC（公开）" value="PUBLIC" />
          <el-option label="INTERNAL（内部）" value="INTERNAL" />
          <el-option label="RESTRICTED（限制）" value="RESTRICTED" />
          <el-option label="STRICT（严格）" value="STRICT" />
        </el-select>
      </el-form-item>

      <el-form-item v-for="key in requiredContextKeys" :key="key" :label="key" required>
        <el-input v-model="form.context[key]" :placeholder="'卡片要求这个上下文键（' + key + '）'" />
      </el-form-item>

      <el-form-item v-if="needsTask" label="输入内容" required>
        <el-input
          v-model="form.snapshotJson"
          type="textarea"
          :rows="5"
          placeholder="这次要做什么（会作为任务的输入快照保存，结果可按它复现）"
        />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
      </el-form-item>
    </el-form>

    <el-alert v-if="problems.length" type="warning" :closable="false" class="mb-2">
      <template #title>启动未通过（{{ problems.length }} 项）</template>
      <ul class="problem-list">
        <li v-for="(problem, index) in problems" :key="index">{{ problem }}</li>
      </ul>
    </el-alert>

    <el-alert
      v-if="prepared?.ticketId"
      type="success"
      :closable="false"
      class="mb-2"
      :title="'预检通过：票据有效期至 ' + (prepared.expiresAt || '—')"
    />
    <el-alert
      v-if="prepared?.existingTaskId"
      type="info"
      :closable="false"
      class="mb-2"
      :title="'这次启动已经存在（任务 ' + (prepared.existingTaskNo || prepared.existingTaskId) + '），不需要重复提交'"
    />

    <template #footer>
      <el-button :loading="preparing" @click="handlePrepare">预检</el-button>
      <el-button
        type="primary"
        :loading="committing"
        :disabled="!prepared?.ticketId"
        @click="handleCommit"
      >
        确认启动
      </el-button>
      <el-button @click="close(false)">取消</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { commitLaunch, prepareLaunch } from '@/api/aigov/portal';
import type { AigLaunchCommitVO, AigLaunchRequestForm, AigLaunchPrepareVO, AigPortalActionVO } from '@/api/aigov/portal/types';
import modal from '@/plugins/modal';
import { START_PENDING_HINT, TASK_TYPE_OPTIONS, buildIdempotencyKey, launchModeLabel, problemTexts } from './presentation';

const props = defineProps<{
  modelValue: boolean;
  roleCode: string;
  action?: AigPortalActionVO;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'launched', result: AigLaunchCommitVO): void;
}>();

const dialogTitle = computed(() => '启动：' + (props.action?.title || props.action?.actionCode || ''));

/** 卡片声明的上下文键（表单据此长字段，缺一个后端就会拒） */
const requiredContextKeys = computed(() => {
  const raw = props.action?.requiredContext;
  if (!raw) {
    return [] as string[];
  }
  return raw
    .split(',')
    .map(item => item.trim())
    .filter(Boolean);
});

/** 页面类卡片不建任务；其余都要把任务描述清楚（与后端同一判据） */
const needsTask = computed(() => props.action?.launchMode !== 'NAVIGATION');

const preparing = ref(false);
const committing = ref(false);
const problems = ref<string[]>([]);
const prepared = ref<AigLaunchPrepareVO>();
const form = ref<AigLaunchRequestForm>({ roleCode: '', actionCode: '', idempotencyKey: '', context: {} });

const emptyContext = () => {
  const map: Record<string, string> = {};
  for (const key of requiredContextKeys.value) {
    map[key] = '';
  }
  return map;
};

/**
 * 打开表单时重置一次。
 *
 * 幂等键**在这里生成一次**：整轮（预检 + 重试）都用它，服务端据此判"同一次启动"。
 * 每次点击都换新键等于关掉了幂等。
 */
const reset = () => {
  problems.value = [];
  prepared.value = undefined;
  form.value = {
    roleCode: props.roleCode,
    actionCode: props.action?.actionCode || '',
    idempotencyKey: buildIdempotencyKey(),
    taskType: 'TEXT_GENERATION',
    projectType: '',
    projectId: undefined,
    dataLevel: 'INTERNAL',
    snapshotJson: '',
    context: emptyContext(),
    remark: ''
  };
};

watch(
  () => props.modelValue,
  visible => {
    if (visible) {
      reset();
    }
  }
);

const close = (value = false) => {
  emit('update:modelValue', value);
};

/** 把表单里的空串去掉，避免把"空字符串"当成用户给了值 */
const buildPayload = (): AigLaunchRequestForm => {
  const context: Record<string, string> = {};
  for (const [key, value] of Object.entries(form.value.context || {})) {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      context[key] = String(value);
    }
  }
  return {
    ...form.value,
    context,
    projectId: form.value.projectId === '' || form.value.projectId === undefined ? undefined : form.value.projectId
  };
};

const handlePrepare = async () => {
  preparing.value = true;
  problems.value = [];
  prepared.value = undefined;
  try {
    const res = await prepareLaunch(buildPayload());
    prepared.value = res.data;
    problems.value = problemTexts(res.data.problems);
    if (res.data.passed) {
      modal.msgSuccess(res.data.existingTaskId ? '这次启动已经存在，不需要重复提交' : '预检通过，可以确认启动');
    } else {
      modal.msgWarning('预检未通过：请看下方问题清单');
    }
  } catch (e: any) {
    // 系统故障才走这里（业务拒绝是 passed=false + problems）
    problems.value = [e?.message || '预检失败'];
  } finally {
    preparing.value = false;
  }
};

const handleCommit = async () => {
  committing.value = true;
  problems.value = [];
  try {
    const res = await commitLaunch({ ...buildPayload(), ticket: prepared.value?.ticketId });
    if (!res.data.passed) {
      problems.value = problemTexts(res.data.problems);
      // 票据过期/内容变了都要重新预检，旧票已无效
      prepared.value = undefined;
      modal.msgWarning('启动未完成：请看下方问题清单，必要时重新预检');
      return;
    }
    modal.msgSuccess(res.data.replayed ? '这次启动此前已提交，返回同一次结果' : '已启动');
    emit('launched', res.data);
    close(false);
  } catch (e: any) {
    problems.value = [e?.message || '启动失败'];
  } finally {
    committing.value = false;
  }
};
</script>

<style scoped>
.problem-list {
  margin: 4px 0 0 0;
  padding-left: 18px;
}

.card-title {
  font-weight: 600;
}

.ml-2 {
  margin-left: 8px;
}

.mb-2 {
  margin-bottom: 12px;
}

.hint {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
