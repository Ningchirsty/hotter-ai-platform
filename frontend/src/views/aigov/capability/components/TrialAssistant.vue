<template>
  <section class="assistant" :class="{ 'assistant--collapsed': collapsed }">
    <header class="assistant__head">
      <span class="assistant__dot" />
      <b>能力试调助手</b>
      <span class="assistant__spacer" />
      <el-button size="small" @click="emit('update:collapsed', !collapsed)">
        {{ collapsed ? '展开' : '收起' }}
      </el-button>
    </header>

    <template v-if="!collapsed">
      <div class="assistant__scope">
        当前作用域：<b>{{ capabilityName || capabilityCode }}</b>
        <span class="assistant__mono">（{{ capabilityCode }}）</span>
      </div>

      <div class="assistant__assume">
        <el-select v-model="dataLevel" size="small" style="width: 120px">
          <el-option v-for="item in DATA_LEVEL_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="scenarioCode" size="small" style="width: 130px" placeholder="场景（可空）">
          <el-option label="不限定场景" value="" />
        </el-select>
        <el-checkbox v-model="forbidExternal" size="small">禁止外发</el-checkbox>
      </div>

      <div class="assistant__chips">
        <el-tag
          v-for="question in presetQuestions()"
          :key="question"
          class="assistant__chip"
          @click="ask(question)"
        >
          {{ question }}
        </el-tag>
      </div>

      <div ref="messageBox" class="assistant__msgs">
        <div class="assistant__hello">
          我是这个能力的试调助手。我不会真的发起调用；问我"会走哪条路 / 为什么排除了某个模型"即可。
        </div>
        <div v-for="(message, index) in messages" :key="index" class="assistant__msg" :class="`assistant__msg--${message.role}`">
          <div class="assistant__bubble">
            <template v-if="message.sections">
              <div v-for="section in message.sections" :key="section.title" class="assistant__section">
                <div class="assistant__section-title">{{ section.title }}</div>
                <ul class="assistant__lines">
                  <li v-for="line in section.lines" :key="line">{{ line }}</li>
                </ul>
              </div>
            </template>
            <template v-else>{{ message.text }}</template>
          </div>
        </div>
      </div>

      <div class="assistant__input">
        <el-input
          v-model="draft"
          size="small"
          placeholder="输入消息开始对话（回车发送）"
          @keyup.enter="ask(draft)"
        />
        <el-button type="primary" size="small" :loading="loading" @click="ask(draft)">发送</el-button>
      </div>
      <div class="assistant__foot">
        dryRun 不产生费用、不写审计；也**不校验授权与配额**——真实调用才会拦（详见 ⑥ 边界）。
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import { dryRun } from '@/api/aigov/invoke';
import type { AigInvokeVO } from '@/api/aigov/invoke';
import {
  DATA_LEVEL_OPTIONS,
  assistantBoundaryNote,
  extractModelToken,
  hitsForModel,
  matchPresetQuestion,
  policySuggestions,
  presetQuestions,
  walkthroughSections
} from '../presentation';
import type { AigCapabilityOverviewVO } from '@/api/aigov/capability/overview-types';

/**
 * 能力试调助手（右侧常驻对话面板，用户已确认此交互形态）。
 *
 * <p><b>它解决什么</b>：新增能力或改完策略后，"这条链路到底会怎么走"此前只能靠真实调用 + 事后翻审计。
 * 后端 `POST /aigov/invoke/dryRun` 只做路由决策（不调用、不写审计、不扣配额），
 * 本组件把它渲染成对话：四类固定问题 + 假设参数（数据等级/禁止外发/场景）。</p>
 *
 * <p><b>三类回答的来源</b>：①"会走哪条路"＝`walkthroughSections`（六段式）②"为什么排除了 X"
 * ＝从 `policyHits` 里精确匹配，匹配不到就如实说"本次没有排除它"③"等级=严格会怎样"
 * ＝换 `dataLevel` 再跑一次 dryRun 并对比。合起来的价值是：**任选一个假设直接跑**，
 * 而不是对着表单猜。</p>
 *
 * <p><b>刻意不接大模型</b>：治理台的一次问答不应产生外部调用与费用；自由输入先走关键词路由。</p>
 */
const props = defineProps<{
  capabilityCode: string;
  capabilityName?: string;
  /** 能力概览（用于"策略建议"那类回答） */
  overview?: AigCapabilityOverviewVO;
  collapsed?: boolean;
}>();

const emit = defineEmits<{
  (event: 'update:collapsed', value: boolean): void;
  (event: 'raw', content: string): void;
}>();

interface AssistantMessage {
  role: 'me' | 'bot';
  text?: string;
  sections?: { title: string; lines: string[] }[];
}

const dataLevel = ref('INTERNAL');
const scenarioCode = ref('');
const forbidExternal = ref(false);
const draft = ref('');
const messages = ref<AssistantMessage[]>([]);
const loading = ref(false);
const messageBox = ref<HTMLElement>();

/** 最近一次 dryRun 结果：用于"为什么排除 X"与"策略建议" */
let lastVo: AigInvokeVO | undefined;

const scrollToBottom = async () => {
  await nextTick();
  if (messageBox.value) {
    messageBox.value.scrollTop = messageBox.value.scrollHeight;
  }
};

// 换能力时清空对话：否则上一段结论会被误读成这个能力的结论
watch(
  () => props.capabilityCode,
  () => {
    messages.value = [];
    lastVo = undefined;
  }
);

const runDryRun = async (): Promise<AigInvokeVO | undefined> => {
  const res = await dryRun({
    capabilityCode: props.capabilityCode,
    dataLevel: dataLevel.value,
    scenarioCode: scenarioCode.value || undefined,
    allowExternal: forbidExternal.value ? false : undefined
  });
  lastVo = res.data;
  return lastVo;
};

const ask = async (question: string) => {
  const text = (question || '').trim();
  if (!text || loading.value) {
    return;
  }
  draft.value = '';
  messages.value.push({ role: 'me', text });
  loading.value = true;
  try {
    const kind = matchPresetQuestion(text) || 'WALKTHROUGH';
    const vo = await runDryRun();
    if (!vo) {
      messages.value.push({ role: 'bot', text: '试调没有返回结果（接口返回空）。可稍后重试，或查看「原始与日志」。' });
    } else if (kind === 'EXCLUDE') {
      const token = extractModelToken(text);
      const matched = hitsForModel(vo.policyHits, token);
      if (matched.length > 0) {
        messages.value.push({ role: 'bot', sections: [{ title: `被排除的原因（${token}）`, lines: matched }] });
      } else {
        const all = (vo.policyHits || []).filter((hit) => hit.trim().startsWith('排除'));
        messages.value.push({
          role: 'bot',
          sections: [
            {
              title: '本次决策没有排除它',
              lines:
                token.length > 0
                  ? [`没有找到与「${token}」相关的排除判定。`, ...(all.length ? ['本次被排除的是：', ...all] : ['本次没有任何候选被排除。'])]
                  : ['请把模型键（如 local-sdxl）写进问题里，我再精确回答。', ...all]
            }
          ]
        });
      }
    } else if (kind === 'LEVEL') {
      messages.value.push({
        role: 'bot',
        sections: walkthroughSections(vo, {
          dataLevel: dataLevel.value,
          allowExternal: forbidExternal.value ? false : undefined,
          scenarioCode: scenarioCode.value || undefined
        })
      });
    } else if (kind === 'SUGGEST') {
      messages.value.push({
        role: 'bot',
        sections: [{ title: '策略配置建议（不会自动写入）', lines: policySuggestions(vo, props.overview) }]
      });
    } else {
      messages.value.push({
        role: 'bot',
        sections: walkthroughSections(vo, {
          dataLevel: dataLevel.value,
          allowExternal: forbidExternal.value ? false : undefined,
          scenarioCode: scenarioCode.value || undefined
        })
      });
    }
    emit('raw', JSON.stringify(vo ?? {}, null, 2));
  } catch (error: any) {
    messages.value.push({ role: 'bot', text: `试调失败：${error?.message || '未知错误'}` });
  } finally {
    loading.value = false;
    await scrollToBottom();
  }
};

defineExpose({ boundaryNote: assistantBoundaryNote });
</script>

<style scoped>
.assistant {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  overflow: hidden;
}

.assistant--collapsed .assistant__head {
  border-bottom: 0;
}

.assistant__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}

.assistant__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary);
}

.assistant__spacer {
  flex: 1;
}

.assistant__scope {
  padding: 8px 12px 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.assistant__mono {
  font-family: ui-monospace, Consolas, monospace;
}

.assistant__assume {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  flex-wrap: wrap;
}

.assistant__chips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding: 0 12px 8px;
}

.assistant__chip {
  cursor: pointer;
}

.assistant__msgs {
  flex: 1;
  min-height: 180px;
  max-height: 42vh;
  overflow: auto;
  padding: 8px 12px;
  background: var(--el-fill-color-blank);
}

.assistant__hello {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 8px 10px;
}

.assistant__msg {
  display: flex;
  margin-top: 8px;
}

.assistant__msg--me {
  justify-content: flex-end;
}

.assistant__bubble {
  max-width: 92%;
  padding: 8px 10px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.6;
  background: var(--el-fill-color-light);
}

.assistant__msg--me .assistant__bubble {
  background: var(--el-color-primary);
  color: #fff;
}

.assistant__section + .assistant__section {
  margin-top: 8px;
}

.assistant__section-title {
  font-weight: 600;
}

.assistant__lines {
  margin: 4px 0 0;
  padding-left: 16px;
}

.assistant__input {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.assistant__foot {
  padding: 6px 12px 10px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
