<template>
  <div class="business-form">
    <div v-for="field in ability.inputs" :key="field.key" class="business-field">
      <label :for="`${ability.media}-${ability.code}-${field.key}`">{{ field.label }}<em v-if="field.required">*</em></label>
      <el-select v-if="field.options" :id="`${ability.media}-${ability.code}-${field.key}`" :model-value="values[field.key]" :disabled="busy"
        :aria-label="field.label" @update:model-value="update(field.key, $event)">
        <el-option v-for="option in field.options" :key="option" :value="option" :label="option" />
      </el-select>
      <el-input v-else :id="`${ability.media}-${ability.code}-${field.key}`" :model-value="values[field.key]" :disabled="busy"
        :type="field.key === 'title' ? 'text' : 'textarea'" :rows="2" :maxlength="field.maxLength" show-word-limit
        :placeholder="field.placeholder" @update:model-value="update(field.key, $event)" />
    </div>
    <p class="ability-note">{{ ability.note }}</p>
    <details class="plan-review">
      <summary>查看创作方案</summary>
      <p><b>用途：</b>{{ ability.name }} · {{ workflow?.modelName }}</p>
      <p class="workflow-code">{{ workflow?.workflowCode }}</p>
      <p v-if="plan.errors.length" class="missing">{{ plan.errors.join('；') }}</p>
      <pre v-else>{{ plan.prompt || '固定抠图提示词；白底用途还包含后端合成步骤。' }}</pre>
      <b class="check-title">成品检查</b>
      <ul><li v-for="check in ability.acceptance" :key="check">{{ check }}</li></ul>
      <small>用途表单由后端校验并编译；生成效果需要对应场景实测。</small>
    </details>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { compileAbility, type CreativeAbility } from './types';
import type { LocalWorkflowOption } from '../LocalWorkflowPicker/types';
const props = defineProps<{ ability: CreativeAbility; values: Record<string,string>; workflow?: LocalWorkflowOption; busy?: boolean }>();
const emit = defineEmits<{ 'update:values': [value: Record<string,string>] }>();
const plan = computed(() => compileAbility(props.ability, props.values));
function update(key: string, value: string) { emit('update:values', { ...props.values, [key]: value }); }
</script>
<style scoped>
.business-field { margin:20px 0; } label { display:block; margin-bottom:10px; font-size:13px; color:#35425c; font-weight:600; }
em { font-style:normal; color:#a783c6; margin-left:5px; } .el-select { width:100%; }
.ability-note { font-size:12px; line-height:1.8; color:#8893a8; }
.plan-review { border:1px solid #e5e1ef; border-radius:10px; padding:14px; background:#faf9fd; margin:18px 0; color:#66738b; font-size:12px; line-height:1.8; }
summary { cursor:pointer; color:#7b61b4; font-weight:600; } pre { white-space:pre-wrap; overflow-wrap:anywhere; font:inherit; }
.workflow-code { overflow-wrap:anywhere; color:#929aad; font-size:10px; } .missing { color:#9b7952; }
.check-title { display:block; margin-top:15px; } ul { padding-left:18px; } small { font-size:10px; color:#929aad; }
</style>
