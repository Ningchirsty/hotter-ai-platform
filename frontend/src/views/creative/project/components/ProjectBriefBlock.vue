<template>
  <section class="block" data-block="PROJECT_BRIEF">
    <div class="block-head">
      <h4>2. 品牌要求（Brief）（由品牌部在内容任务里录入）</h4>
      <div class="block-actions">
        <el-tag :type="briefStatusType" size="small" effect="dark">{{ briefStatusText }}</el-tag>
        <el-button size="small" type="warning" plain @click="$emit('apply-change')">
          申请修改品牌要求
        </el-button>
        <el-button size="small" plain :loading="briefBusy === 'load'" @click="$emit('refresh')">
          刷新
        </el-button>
        <el-button size="small" type="primary" plain @click="$emit('go-content-task')">
          去内容任务里录入
        </el-button>
      </div>
    </div>
    <p class="hint">
      <b>本页只读</b>：品牌要求由品牌部在<b>内容生产协同 → 内容任务 → 任务详情 →「品牌要求（Brief）」</b>
      里录入与确认，平面设计按此创作（那边确认后本页即可见）。要改要求请点右上「申请修改品牌要求」，
      提交后进入品牌部的待办（互动确认卡），处理完这里会跟着更新。
      产品事实同样只读（下面「4. 事实确认」），由品牌部在内容侧确认；品牌调性与事实里的 brand_tone
      <b>并存</b>——一个是品牌方自己提的要求，一个是从资料里解析确认的，两者冲突时同时展示、由人裁定，不自动合并。
      这些要求的去向（<b>按实际接线如实写</b>）：<b>必显信息</b>与<b>主推卖点</b>进出图的正向提示词，
      <b>禁用词</b>进出图的负向提示词；<b>品牌调性 / 目标人群 / 尺寸规范 / 参考风格</b>
      当前版本<b>没有任何下游消费</b>——不进出图提示词、不参与渲染，仅在此展示备查。
    </p>
    <p v-if="error" class="fact-error">
      {{ error }}（点右上「刷新」重试，页面不会用默认值糊过去）
    </p>
    <!-- 修改申请状态：有就明说在等品牌部处理，读不到也说一句（不静默） -->
    <p v-if="changeRequest" class="brief-change-pending">
      已提交修改申请：{{ changeRequest.question || '（无说明）' }}
      <span class="muted">（等待品牌部处理 · {{ formatTime(changeRequest.createTime) || '—' }}）</span>
    </p>
    <p v-else-if="changeError" class="muted brief-change-pending">
      {{ changeError }}
    </p>
    <div class="brief-grid brief-grid-readonly">
      <div v-for="field in fields" :key="field.key" class="brief-row">
        <label>{{ field.label }}</label>
        <div class="brief-control">
          <div class="brief-value" :class="{ 'is-empty': !valueOf(field.key) }">
            {{ valueOf(field.key) || '—' }}
          </div>
          <!-- 参考风格：品牌方给的参考图（只读展示；它们同时也是任务附件） -->
          <div v-if="field.key === 'styleRef'" class="brief-style-images">
            <BriefStyleImages
              :task-id="taskId"
              :images="brief?.styleRefImages || []"
              :editable="false"
              source="creative"
            />
            <span v-if="(brief?.styleRefImages || []).length" class="hint">
              这些图也是任务附件，可以在上面「1. 产品图与参考图」里被选作出图参考图。
            </span>
          </div>
          <span class="hint">{{ field.hint }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import BriefStyleImages from '@/components/BriefStyleImages/index.vue';
import type { BrandBriefFieldKey, BrandBriefVO } from '@/api/content/brief/types';
import { BRAND_BRIEF_FIELDS } from '@/api/content/brief/types';
import type { CpInteractionCardVO } from '@/api/content/card/types';
import type { TagType } from '@/api/creative/types';

/**
 * 项目页区块②「品牌要求（Brief）」（V0.2 R32）。
 *
 * <p>这一块整体是**只读**的：品牌要求由品牌部在内容协同里录入确认，这里只展示与"申请修改"。
 * 拆成组件后，"只读"这件事在代码结构上也成立了——组件里没有任何写接口，
 * 三个动作（申请修改 / 刷新 / 去内容任务）都是 emit 给页面。</p>
 *
 * @author creative
 */
defineProps<{
  /** 当前项目ID（参考风格图片条要它） */
  taskId: string | number;
  /** 品牌要求（未配置时为 null） */
  brief: BrandBriefVO | null;
  /** 状态徽标样式 */
  briefStatusType: TagType;
  /** 状态徽标文案 */
  briefStatusText: string;
  /** 正在进行的动作（'load' 时刷新按钮转圈） */
  briefBusy: string;
  /** 读取失败原因（不静默） */
  error: string;
  /** 待处理的"申请修改"互动卡 */
  changeRequest: CpInteractionCardVO | null;
  /** 申请状态读取失败原因 */
  changeError: string;
  /** 字段取值（与页面的 briefValueOf 同一口径） */
  valueOf: (key: BrandBriefFieldKey) => string;
  /** 时间格式化 */
  formatTime: (value?: string) => string;
}>();

/** 字段清单来自内容域的同一份契约（页面与这里都用它，不各写一份） */
const fields = BRAND_BRIEF_FIELDS;

defineEmits<{
  (e: 'apply-change'): void;
  (e: 'refresh'): void;
  (e: 'go-content-task'): void;
}>();
</script>
