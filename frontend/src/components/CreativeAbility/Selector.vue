<template>
  <div class="ability-heading"><b>你想创作什么？</b><span>{{ items.length }} 个{{ media === 'image' ? '图像' : '视频' }}用途</span></div>
  <div class="ability-cards" :aria-label="media === 'image' ? '图像用途' : '视频用途'">
    <button v-for="item in visibleItems" :key="item.code" type="button" :disabled="busy"
      :class="{ active: modelValue === item.code }" :aria-pressed="modelValue === item.code" @click="$emit('choose', item)">
      <span class="ability-symbol">{{ media === 'image' ? '◈' : '▷' }}</span>
      <strong>{{ item.name }}</strong><small>{{ item.desc }}</small>
    </button>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { CreativeAbility } from './types';
const props = defineProps<{ media: 'image' | 'video'; items: CreativeAbility[]; modelValue?: string; busy?: boolean }>();
defineEmits<{ choose: [ability: CreativeAbility] }>();
const visibleItems = computed(() => props.items.filter(item => item.media === props.media));
</script>
<style scoped>
.ability-heading { display:flex; justify-content:space-between; gap:12px; margin:22px 0 13px; font-size:13px; color:#35425c; }
.ability-heading span { font-size:11px; color:#8994aa; }
.ability-cards { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; }
.ability-cards button { text-align:left; padding:16px; border:1px solid #e5e7ef; border-radius:11px; background:white; color:#35425c; cursor:pointer; min-width:0; }
.ability-cards button.active { border-color:#937ad5; background:#f5f1fc; box-shadow:inset 0 0 0 1px #937ad5; }
.ability-cards button:focus-visible { outline:2px solid #937ad5; outline-offset:3px; }
.ability-cards button:disabled { opacity:.6; cursor:wait; }
.ability-symbol { color:#8b70c8; margin-right:7px; }
strong { font-size:13px; } small { display:block; color:#8b96ab; font-size:11px; line-height:1.7; margin-top:7px; }
@media(max-width:380px) { .ability-cards { grid-template-columns:1fr; } }
</style>
