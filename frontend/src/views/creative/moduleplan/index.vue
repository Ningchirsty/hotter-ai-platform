<template>
  <div class="studio">
    <header class="page-head">
      <div>
        <h2>模块规划</h2>
        <p class="muted">
          这一页决定<b>这个项目的分镜有哪些屏</b>：左边是模块库，中间是顺序（一个模块可以占多屏，
          也可以停用而不删除），右边是单个模块的目标/卖点/文案/事实/视觉/参考图/Workflow/模板。
          顺序、屏数、启停就是屏集合本身——保存后要<b>重新拆分镜</b>才会用上新计划。
        </p>
      </div>
      <div class="head-actions">
        <el-select
          v-if="!taskId"
          v-model="pickedTaskId"
          filterable
          remote
          placeholder="选择视觉项目"
          :remote-method="searchProjects"
          :loading="projectLoading"
          class="project-picker"
          @change="onPickProject"
        >
          <el-option
            v-for="p in projects"
            :key="String(p.taskId)"
            :label="`${p.taskName}（${p.deliverableType || 'ECOM_DETAIL'}）`"
            :value="String(p.taskId)"
          />
        </el-select>
        <el-tag v-if="plan" effect="dark" type="info">{{ plan.deliveryName || plan.deliveryType }}</el-tag>
        <el-tag v-if="plan" effect="dark" :type="plan.editable ? 'success' : 'warning'">
          {{ plan.editable ? '可编辑' : '已锁定状态' }}
        </el-tag>
        <!-- R28（§25 第 1 步）：计划确认状态。改了计划会自动回到"待确认"，所以这个标签
             回答的是"当前这一版有没有人确认过"。 -->
        <el-tag v-if="plan" effect="dark" :type="plan.confirmed ? 'success' : 'info'">
          {{ plan.confirmed ? '计划已确认' : '计划待确认' }}
        </el-tag>
        <el-button
          v-if="plan && !plan.confirmed"
          type="primary"
          plain
          :disabled="!canEdit"
          :loading="confirming"
          @click="confirmPlan"
        >
          确认本计划
        </el-button>
        <el-button plain :loading="loading" @click="load">刷新</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!canEdit || !dirty"
          @click="save"
        >
          保存计划
        </el-button>
      </div>
    </header>

    <el-alert v-if="!taskId" type="info" :closable="false" class="notice" show-icon
      title="先选一个视觉项目：模块计划是挂在项目上的。">
      <span class="muted">也可以从项目页顶部的「模块规划」按钮直接进来（会带上 taskId）。</span>
    </el-alert>

    <el-alert v-else-if="loadError" type="error" :closable="false" class="notice" show-icon :title="loadError">
      <span class="muted">加载失败时页面不会显示"没有模块"这种假空态——请先解决上面的原因再刷新。</span>
    </el-alert>

    <el-alert v-else-if="plan && !plan.editable" type="warning" :closable="false" class="notice" show-icon
      title="现在不能改模块计划">
      <span class="muted">{{ plan.editBlockReason }}</span>
    </el-alert>

    <!-- 模块定义编辑（R27）：改的是"交付类型级的模块库"，不是这个项目的计划 -->
    <el-dialog
      v-model="definitionVisible"
      :title="definitionForm.id ? '编辑模块定义' : '新建模块定义'"
      width="720px"
      append-to-body
    >
      <p class="hint">
        这是<b>交付类型级</b>的模块库（所有该类型的项目共享）。改它只影响以后生成的分镜；
        已经生成的分镜不动。
      </p>
      <el-form label-width="120px" size="small">
        <el-form-item label="模块编码">
          <el-input v-model="definitionForm.moduleCode" :disabled="!!definitionForm.id"
            placeholder="HERO / SELLING_POINT 这类唯一编码（项目计划按它关联）" />
        </el-form-item>
        <el-form-item label="模块名">
          <el-input v-model="definitionForm.moduleName" placeholder="多屏时会作为前缀：卖点 → 卖点一/卖点二" />
        </el-form-item>
        <el-form-item label="屏类型">
          <el-input v-model="definitionForm.screenType" placeholder="HERO / SELLING_POINT / SCENE …（决定文案策略）" />
        </el-form-item>
        <el-form-item label="保真等级">
          <el-select v-model="definitionForm.productLockLevel" class="pick">
            <el-option label="STRICT（产品必须一致）" value="STRICT" />
            <el-option label="LOOSE（允许场景化演绎）" value="LOOSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="取景">
          <el-input v-model="definitionForm.shot" placeholder="可用 {ratio} 占位产品占比区间" />
        </el-form-item>
        <el-form-item label="模块目标">
          <el-input v-model="definitionForm.objective" placeholder="这一屏要达成什么" />
        </el-form-item>
        <el-form-item label="屏数区间">
          <el-input-number v-model="definitionForm.minScreens" :min="1" :max="10" />
          <span class="muted"> ～ </span>
          <el-input-number v-model="definitionForm.maxScreens" :min="1" :max="10" />
          <span class="muted small">（默认骨架取最少屏数）</span>
        </el-form-item>
        <el-form-item label="进默认骨架">
          <el-switch
            :model-value="definitionForm.defaultSelected !== '1'"
            inline-prompt
            active-text="进"
            inactive-text="不进"
            @update:model-value="(v: string | number | boolean) => (definitionForm.defaultSelected = v ? '0' : '1')"
          />
          <span class="muted small">（不进 = 可选模块，规划页里手动添加）</span>
        </el-form-item>
        <el-form-item label="默认顺序">
          <el-input-number v-model="definitionForm.defaultSortNo" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="允许的 Workflow">
          <el-input v-model="definitionForm.allowedWorkflows" placeholder="逗号分隔，例如 wf-i2i-qwen21,wf-whitebg-qwen21" />
        </el-form-item>
        <el-form-item label="允许的模板">
          <el-input v-model="definitionForm.allowedTemplates" placeholder="逗号分隔，例如 longpage@1.0.2" />
        </el-form-item>
        <el-form-item label="所需事实">
          <el-input v-model="definitionForm.requiredFacts" placeholder="事实字段码，逗号分隔" />
        </el-form-item>
        <el-form-item label="视觉表达">
          <el-input v-model="definitionForm.visualRulesJson" type="textarea" :rows="2"
            placeholder='JSON 或人话文本，例如 {"tone":"暖光"}' />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="definitionForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="definitionVisible = false">取消</el-button>
        <el-button type="primary" :loading="definitionSaving" @click="saveDefinition">保存</el-button>
      </template>
    </el-dialog>

    <template v-if="plan">
      <div class="cols">
        <!-- 左：模块库 -->
        <section class="panel col-library">
          <div class="block-head">
            <h3>可用模块库</h3>
            <span class="muted">{{ library.length }} 个</span>
            <el-button size="small" text :disabled="!canEdit" @click="openDefinitionDialog(null)">
              ＋ 新建模块
            </el-button>
          </div>
          <p class="muted small">
            来自交付类型「{{ plan.deliveryType }}」的模块库。点「添加」放到中间的计划里；
            同一个模块可以添加多次（复制）。
            <b>改模块库只影响以后生成的分镜</b>——已生成的分镜屏上冻着当时的屏类型与取景，不回溯改动。
          </p>
          <ul class="library">
            <li v-for="d in library" :key="String(d.moduleCode)" class="lib-item">
              <div class="lib-main">
                <div class="lib-name">
                  {{ d.moduleName }}
                  <el-tag v-if="d.defaultSelected === '1'" size="small" effect="plain">默认骨架</el-tag>
                </div>
                <div class="muted small">
                  {{ d.moduleCode }} · 屏类型 {{ d.screenType }} · {{ screenRangeText(d) }}
                </div>
                <div v-if="d.objective" class="muted small">{{ d.objective }}</div>
              </div>
              <div class="lib-actions">
                <el-button size="small" text :disabled="!canEdit" @click="addModule(d)">添加</el-button>
                <el-button size="small" text :disabled="!canEdit" @click="openDefinitionDialog(d)">
                  编辑
                </el-button>
                <el-button size="small" text :disabled="!canEdit" @click="toggleDefinition(d)">
                  {{ d.enabled === '1' ? '启用' : '停用' }}
                </el-button>
                <el-button size="small" text type="danger" :disabled="!canEdit" @click="removeDefinition(d)">
                  删除
                </el-button>
              </div>
            </li>
          </ul>
        </section>

        <!-- 中：当前顺序 -->
        <section class="panel col-order">
          <div class="block-head">
            <h3>当前顺序</h3>
            <span class="muted">
              {{ modules.length }} 个模块 → {{ plan.screenCount }} 屏
            </span>
          </div>
          <p class="muted small">
            拖动或用 ↑↓ 调顺序；「屏数」决定这个模块占几屏；关掉开关是<b>停用</b>（留在计划里、不出屏）。
            <b>确认本计划</b>只表示"人看过并认可当前这一版"——<b>保存改动会自动回到待确认</b>。
          </p>
          <ul class="modules">
            <li
              v-for="(m, index) in modules"
              :key="m.uid"
              class="module-item"
              :class="{ 'is-current': index === currentIndex, 'is-off': m.enabled === '1' }"
              draggable="true"
              @dragstart="onDragStart(index)"
              @dragover.prevent
              @drop="onDrop(index)"
              @click="currentIndex = index"
            >
              <span class="drag-handle" title="按住拖动排序">⠿</span>
              <div class="module-main">
                <div class="module-name">
                  {{ m.moduleName || m.moduleCode }}
                  <el-tag v-if="m.enabled === '1'" size="small" type="info" effect="dark">已停用</el-tag>
                  <el-tag v-if="m.copyText" size="small" type="success" effect="plain">有人工文案</el-tag>
                </div>
                <div class="muted small">
                  {{ m.moduleCode }} · {{ m.screenType }} · 计划第 {{ index + 1 }} 位
                </div>
              </div>
              <el-input-number
                v-model="m.screenCount"
                size="small"
                :min="1"
                :max="maxScreensOf(m)"
                :disabled="!canEdit"
                class="count"
                @change="markDirty"
              />
              <el-switch
                :model-value="m.enabled !== '1'"
                :disabled="!canEdit"
                inline-prompt
                active-text="启"
                inactive-text="停"
                @update:model-value="(v: string | number | boolean) => toggleEnabled(m, Boolean(v))"
              />
              <el-button size="small" text :disabled="!canEdit" @click.stop="move(index, -1)">↑</el-button>
              <el-button size="small" text :disabled="!canEdit" @click.stop="move(index, 1)">↓</el-button>
              <el-button size="small" text :disabled="!canEdit" @click.stop="duplicate(index)">复制</el-button>
              <el-button size="small" text type="danger" :disabled="!canEdit" @click.stop="remove(index)">
                删除
              </el-button>
            </li>
          </ul>
          <p v-if="!modules.length" class="muted small">
            计划是空的：从左边「添加」至少一个模块，否则分镜没有任何屏可出。
          </p>

          <div class="block-head sub">
            <h3>屏预览</h3>
            <span class="muted">{{ plan.screenCount }} 屏（保存前的预览，与真正出屏用同一段逻辑）</span>
          </div>
          <el-alert
            v-if="plan.previewSource && plan.previewSource !== 'PLAN'"
            type="info"
            :closable="false"
            class="preview-note"
            show-icon
            :title="plan.previewNote || '预览来自默认骨架（尚未保存）'"
          />
          <ol class="screens">
            <li v-for="s in plan.screens" :key="String(s.screenNo)" class="screen-item">
              <span class="screen-no mono">{{ s.screenNo }}</span>
              <span class="screen-label">{{ s.label }}</span>
              <span class="muted small">{{ s.screenType }} · {{ s.productLockLevel }}</span>
              <el-tag v-if="s.missingFacts && s.missingFacts.length" size="small" type="warning" effect="dark">
                缺事实：{{ s.missingFacts.join('、') }}
              </el-tag>
            </li>
          </ol>
          <p v-if="plan.storyboard" class="muted small storyboard-note" :class="{ warn: plan.storyboard.stale }">
            {{ plan.storyboard.note }}
          </p>
        </section>

        <!-- 右：当前模块的字段 -->
        <section class="panel col-fields">
          <div class="block-head">
            <h3>模块字段</h3>
            <span class="muted">{{ current ? current.moduleName || current.moduleCode : '未选中' }}</span>
          </div>
          <template v-if="current">
            <label class="field">
              <span>模块目标</span>
              <el-input v-model="current.objective" :disabled="!canEdit" placeholder="这一屏要达成什么"
                @input="markDirty" />
            </label>
            <label class="field">
              <span>对应卖点</span>
              <el-select
                v-model="currentSellingPointIds"
                multiple
                filterable
                :disabled="!canEdit"
                placeholder="从项目卖点块里选（不选则按顺序兜底）"
                @change="onSellingPointsChange"
              >
                <el-option
                  v-for="b in sellingPoints"
                  :key="String(b.id)"
                  :label="b.title || b.content || String(b.id)"
                  :value="String(b.id)"
                />
              </el-select>
            </label>
            <label class="field">
              <span>文案</span>
              <el-input
                v-model="current.copyText"
                type="textarea"
                :rows="3"
                :disabled="!canEdit"
                placeholder="写了就用它（人工优先于模型与模板）"
                @input="markDirty"
              />
            </label>
            <label class="field">
              <span>所需事实</span>
              <el-input v-model="current.requiredFactCodes" :disabled="!canEdit"
                placeholder="事实字段码，逗号分隔（缺哪个会在屏预览里标出来）" @input="markDirty" />
            </label>
            <label class="field">
              <span>视觉表达</span>
              <el-input v-model="current.visualRulesJson" type="textarea" :rows="2" :disabled="!canEdit"
                placeholder='JSON 文本，例如 {"tone":"暖光","props":["木桌"]}' @input="markDirty" />
            </label>
            <label class="field">
              <span>参考图</span>
              <el-input v-model="current.referenceCodes" :disabled="!canEdit"
                placeholder="附件文件ID，逗号分隔" @input="markDirty" />
            </label>
            <label class="field">
              <span>Workflow</span>
              <el-input v-model="current.workflowCodes" :disabled="!canEdit"
                placeholder="逗号分隔，第一个用于该模块出图" @input="markDirty" />
            </label>
            <label class="field">
              <span>模板</span>
              <el-input v-model="current.templateCodes" :disabled="!canEdit"
                placeholder="模板码，逗号分隔" @input="markDirty" />
            </label>
            <label class="field">
              <span>备注</span>
              <el-input v-model="current.remark" :disabled="!canEdit" @input="markDirty" />
            </label>
            <p class="muted small">
              已生效：顺序 / 屏数 / 启停（决定屏集合）、<b>文案</b>（覆盖该屏正文）、
              <b>Workflow</b>（该模块出图用第一个）、<b>对应卖点</b>（该屏取哪个卖点块）。
              其余字段（视觉表达 / 参考图 / 模板）本轮只落库并展示，尚未参与生成——不假装它们已经在起作用。
            </p>
          </template>
          <p v-else class="muted small">在中间点一行模块，这里编辑它的字段。</p>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { ElMessage } from 'element-plus';
  import {
    getProjectModulePlan,
    saveProjectModulePlan,
    type ModuleDefinition,
    type ProjectModule,
    type ProjectModulePlan,
    createModuleDefinition,
    updateModuleDefinition,
    setModuleDefinitionEnabled,
    deleteModuleDefinition,
    type ModuleDefinitionForm,
    confirmProjectModulePlan} from '@/api/creative/scenario';
  import { listCopyBlocks, listCreativeProject } from '@/api/creative';
  import type { CopyBlockVO } from '@/api/creative/types';
  import { extractErrorMessage } from '@/utils/request';
  import { checkPermi } from '@/utils/permission';

  /**
   * 模块规划页（V0.2 R22，文档 §24）。
   *
   * 三栏：左模块库 / 中顺序 / 右字段。为什么把"屏预览"放在中间栏底部而不是右边：
   * 用户改的是顺序与屏数，预览要跟改动**同屏可见**，改一下就看到"现在会出几屏"。
   *
   * 页面不自己判断"能不能改"：后端 `editable/editBlockReason` 说了算（分镜锁定、已出图、已渲染都不可逆）。
   * 前端重算一遍"能不能改"，迟早会与后端不一致，那时的表现就是"按钮能点、保存被拒"。
   */

  /** 带本地 uid 的模块行（复制的两份 module_code 相同，必须靠 uid 做 key 与选中） */
  interface EditableModule extends ProjectModule {
    uid: string;
  }

  const route = useRoute();
  const router = useRouter();

  const taskId = ref<string>(String(route.query.taskId || ''));
  const pickedTaskId = ref<string>('');
  const projects = ref<{ taskId?: string | number; taskName?: string; deliverableType?: string }[]>([]);
  const projectLoading = ref(false);

  const plan = ref<ProjectModulePlan | null>(null);
  const modules = ref<EditableModule[]>([]);
  const sellingPoints = ref<CopyBlockVO[]>([]);
  const currentIndex = ref(0);
  const loading = ref(false);
  const saving = ref(false);
  const dirty = ref(false);
  const confirming = ref(false);
  const definitionVisible = ref(false);
  const definitionSaving = ref(false);
  const definitionForm = ref<ModuleDefinitionForm & { id?: string | number }>({});
  const loadError = ref('');
  let uidSeed = 0;
  let dragFrom = -1;

  const library = computed<ModuleDefinition[]>(() => plan.value?.library || []);
  const current = computed<EditableModule | null>(() => modules.value[currentIndex.value] || null);
  /**
   * 这个账号能不能改这份计划 = 后端说可改（不是锁定/已出图/已渲染）**且**有 `creative:project:edit`。
   *
   * <p>为什么两个都要：后端只回答"这个项目现在能不能改"（业务状态），不回答"你有没有权限"。
   * R22 只用了前者，于是"能看不能改"的角色会看到一个可点的保存按钮，点下去才 403——
   * 这是 R23 用临时只读账号真机验出来的缺口，这里补上。</p>
   */
  const canEdit = computed(() => Boolean(plan.value?.editable) && checkPermi(['creative:project:edit']));

  const currentSellingPointIds = computed<string[]>(() =>
    (current.value?.sellingPointCodes || '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
  );

  function nextUid(): string {
    uidSeed += 1;
    return `m${uidSeed}`;
  }

  function toEditable(rows: ProjectModule[]): EditableModule[] {
    return rows.map((row) => ({ ...row, uid: nextUid() }));
  }

  function definitionOf(code?: string): ModuleDefinition | undefined {
    return library.value.find((d) => d.moduleCode === code);
  }

  function maxScreensOf(m: EditableModule): number {
    const max = definitionOf(m.moduleCode)?.maxScreens;
    return max && max > 0 ? Math.min(10, max) : 10;
  }

  function screenRangeText(d: ModuleDefinition): string {
    const min = d.minScreens ?? 1;
    const max = d.maxScreens ?? 1;
    return min === max ? `${min} 屏` : `${min}~${max} 屏`;
  }

  function markDirty() {
    dirty.value = true;
  }

  async function load() {
    if (!taskId.value) {
      return;
    }
    loading.value = true;
    loadError.value = '';
    try {
      const res = await getProjectModulePlan(taskId.value);
      const data = res.data || null;
      plan.value = data;
      modules.value = toEditable(data?.modules || []);
      currentIndex.value = 0;
      dirty.value = false;
      // 卖点块用于「对应卖点」选择器：取不到只是这个下拉是空的，不影响其余编辑
      try {
        const blocks = await listCopyBlocks(taskId.value, 'SELLING_POINT');
        sellingPoints.value = blocks.data || [];
      } catch {
        sellingPoints.value = [];
      }
    } catch (error) {
      plan.value = null;
      modules.value = [];
      loadError.value = (await extractErrorMessage(error)) ?? '加载模块规划失败';
    } finally {
      loading.value = false;
    }
  }

  /** 确认本计划（R28）：确认后若再保存任何改动，会自动回到"待确认" */
  async function confirmPlan() {
    confirming.value = true;
    try {
      const res = await confirmProjectModulePlan(taskId.value);
      plan.value = res.data || null;
      ElMessage.success('已确认当前模块计划（改动后需要重新确认）');
    } catch (error) {
      ElMessage.error((await extractErrorMessage(error)) ?? '确认失败');
    } finally {
      confirming.value = false;
    }
  }

  async function save() {
    saving.value = true;
    try {
      const res = await saveProjectModulePlan(
        taskId.value,
        modules.value.map(({ uid: _uid, ...rest }) => rest)
      );
      plan.value = res.data || null;
      modules.value = toEditable(res.data?.modules || []);
      currentIndex.value = 0;
      dirty.value = false;
      ElMessage.success('模块计划已保存');
    } catch (error) {
      ElMessage.error((await extractErrorMessage(error)) ?? '保存失败');
    } finally {
      saving.value = false;
    }
  }

  /** 打开模块定义编辑框（传 null = 新建） */
  function openDefinitionDialog(d: ModuleDefinition | null) {
    definitionForm.value = d
      ? {
          id: d.id,
          deliveryType: d.deliveryType,
          moduleCode: d.moduleCode,
          moduleName: d.moduleName,
          objective: d.objective,
          screenType: d.screenType,
          productLockLevel: d.productLockLevel || 'LOOSE',
          shot: d.shot,
          required: d.required || '0',
          minScreens: d.minScreens ?? 1,
          maxScreens: d.maxScreens ?? 1,
          defaultSelected: d.defaultSelected || '0',
          defaultSortNo: d.defaultSortNo ?? 0,
          allowedTemplates: d.allowedTemplates,
          allowedWorkflows: d.allowedWorkflows,
          requiredFacts: d.requiredFacts,
          visualRulesJson: d.visualRulesJson,
          qaRulesJson: d.qaRulesJson,
          enabled: d.enabled || '0',
          remark: d.remark
        }
      : {
          deliveryType: plan.value?.deliveryType,
          productLockLevel: 'LOOSE',
          required: '0',
          minScreens: 1,
          maxScreens: 1,
          defaultSelected: '0',
          defaultSortNo: 0,
          enabled: '0'
        };
    definitionVisible.value = true;
  }

  /** 保存模块定义（新建 / 编辑） */
  async function saveDefinition() {
    const form = definitionForm.value as ModuleDefinitionForm & { id?: string | number };
    if (!form.moduleCode || !form.moduleName || !form.screenType) {
      ElMessage.warning('模块编码、模块名、屏类型都是必填的');
      return;
    }
    definitionSaving.value = true;
    try {
      if (form.id) {
        await updateModuleDefinition(form.id, form);
      } else {
        await createModuleDefinition({ ...form, deliveryType: plan.value?.deliveryType });
      }
      ElMessage.success('模块库已保存（只影响以后生成的分镜）');
      definitionVisible.value = false;
      await load();
    } catch (error) {
      ElMessage.error((await extractErrorMessage(error)) ?? '保存失败');
    } finally {
      definitionSaving.value = false;
    }
  }

  /** 启用/停用模块定义 */
  async function toggleDefinition(d: ModuleDefinition) {
    if (!d.id) return;
    try {
      await setModuleDefinitionEnabled(d.id, d.enabled === '1' ? '0' : '1');
      ElMessage.success(d.enabled === '1' ? '已启用' : '已停用（新项目默认骨架不再包含它）');
      await load();
    } catch (error) {
      ElMessage.error((await extractErrorMessage(error)) ?? '操作失败');
    }
  }

  /** 删除模块定义（有项目计划在用会被后端拒绝并说明原因） */
  async function removeDefinition(d: ModuleDefinition) {
    if (!d.id) return;
    try {
      await ElMessageBox.confirm(
        `删除模块「${d.moduleName}」（${d.moduleCode}）？\n\n` +
          `已在项目计划里使用它的项目会受影响，所以后端会先检查：有项目在用就直接拒绝，` +
          `并告诉你还有几个项目在用。要下线建议先「停用」。`,
        '删除模块定义',
        { type: 'warning' }
      );
    } catch {
      return;
    }
    try {
      const res = await deleteModuleDefinition(d.id);
      ElMessage.success(String(res.data || '已删除'));
      await load();
    } catch (error) {
      ElMessage.error((await extractErrorMessage(error)) ?? '删除失败');
    }
  }

  function addModule(d: ModuleDefinition) {
    const count = d.minScreens && d.minScreens > 0 ? d.minScreens : 1;
    modules.value.push({
      uid: nextUid(),
      moduleCode: d.moduleCode,
      moduleName: d.moduleName,
      screenType: d.screenType,
      screenCount: count,
      enabled: '0',
      objective: d.objective,
      requiredFactCodes: d.requiredFacts,
      workflowCodes: d.allowedWorkflows,
      templateCodes: d.allowedTemplates,
      visualRulesJson: d.visualRulesJson
    });
    currentIndex.value = modules.value.length - 1;
    markDirty();
  }

  function duplicate(index: number) {
    const source = modules.value[index];
    if (!source) {
      return;
    }
    modules.value.splice(index + 1, 0, { ...source, uid: nextUid() });
    currentIndex.value = index + 1;
    markDirty();
  }

  function remove(index: number) {
    modules.value.splice(index, 1);
    currentIndex.value = Math.min(currentIndex.value, Math.max(0, modules.value.length - 1));
    markDirty();
  }

  function move(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= modules.value.length) {
      return;
    }
    const [row] = modules.value.splice(index, 1);
    modules.value.splice(target, 0, row);
    currentIndex.value = target;
    markDirty();
  }

  function toggleEnabled(m: EditableModule, on: boolean) {
    m.enabled = on ? '0' : '1';
    markDirty();
  }

  function onSellingPointsChange(ids: string[]) {
    if (current.value) {
      current.value.sellingPointCodes = ids.join(',');
    }
    markDirty();
  }

  function onDragStart(index: number) {
    dragFrom = index;
  }

  function onDrop(index: number) {
    if (dragFrom < 0 || dragFrom === index) {
      return;
    }
    const [row] = modules.value.splice(dragFrom, 1);
    modules.value.splice(index, 0, row);
    currentIndex.value = index;
    dragFrom = -1;
    markDirty();
  }

  function onPickProject(value: string) {
    taskId.value = value;
    void router.replace({ path: route.path, query: { taskId: value } });
    void load();
  }

  async function searchProjects(keyword: string) {
    projectLoading.value = true;
    try {
      const res = await listCreativeProject({ pageNum: 1, pageSize: 20, queryTaskName: keyword });
      projects.value = res.data?.rows || [];
    } catch {
      projects.value = [];
    } finally {
      projectLoading.value = false;
    }
  }

  onMounted(() => {
    if (taskId.value) {
      void load();
    } else {
      void searchProjects('');
    }
  });
</script>

<style scoped lang="scss">
  @use '@/assets/styles/tokens-studio.scss';

  .studio {
    min-height: calc(100vh - 135px);
    padding: 24px;
    color: var(--t1);
    background: var(--bg);
    background-image: radial-gradient(900px 460px at 84% -10%, rgba(148, 163, 184, 0.16), transparent 68%);
    border: 1px solid var(--line);
    border-radius: 8px;
  }

  .page-head {
    display: flex;
    gap: 16px;
    align-items: flex-start;
    justify-content: space-between;
    padding-bottom: 16px;
    margin-bottom: 16px;
    border-bottom: 1px solid var(--line);
  }
  .page-head h2 {
    margin: 0 0 6px;
    font-size: 18px;
  }
  .head-actions {
    display: flex;
    gap: 10px;
    align-items: center;
  }
  .project-picker {
    width: 260px;
  }
  .notice {
    margin-bottom: 14px;
  }
  .muted {
    color: var(--t2);
  }
  .small {
    font-size: 12px;
  }

  .cols {
    display: grid;
    grid-template-columns: 300px minmax(420px, 1fr) 380px;
    gap: 14px;
  }
  @media (max-width: 1500px) {
    .cols {
      grid-template-columns: 260px minmax(320px, 1fr) 320px;
    }
  }

  .panel {
    padding: 16px;
    background: var(--surface);
    border: 1px solid var(--line);
    border-radius: 8px;
  }
  .block-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }
  .block-head.sub {
    margin-top: 18px;
    padding-top: 12px;
    border-top: 1px solid var(--line);
  }
  .block-head h3 {
    margin: 0;
    font-size: 14px;
  }

  ul.library,
  ul.modules,
  ol.screens {
    padding: 0;
    margin: 10px 0 0;
    list-style: none;
  }
  .lib-item {
    display: flex;
    gap: 8px;
    align-items: flex-start;
    justify-content: space-between;
    padding: 8px;
    margin-bottom: 6px;
    background: var(--elevated, #171b24);
    border: 1px solid var(--line);
    border-radius: 6px;
  }
  .lib-actions {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
  }
  .pick {
    width: 260px;
  }
  .lib-name {
    display: flex;
    gap: 6px;
    align-items: center;
    font-weight: 600;
  }

  .module-item {
    display: flex;
    gap: 8px;
    align-items: center;
    padding: 8px;
    margin-bottom: 6px;
    cursor: pointer;
    background: var(--elevated, #171b24);
    border: 1px solid var(--line);
    border-radius: 6px;
  }
  .module-item.is-current {
    border-color: var(--accent, rgba(103, 194, 58, 0.6));
  }
  .module-item.is-off {
    opacity: 0.55;
  }
  .drag-handle {
    color: var(--t2);
    cursor: grab;
    user-select: none;
  }
  .module-main {
    flex: 1;
    min-width: 0;
  }
  .module-name {
    display: flex;
    gap: 6px;
    align-items: center;
    font-weight: 600;
  }
  .count {
    width: 96px;
  }

  .screen-item {
    display: flex;
    gap: 10px;
    align-items: center;
    padding: 6px 8px;
    margin-bottom: 4px;
    background: var(--elevated, #171b24);
    border: 1px solid var(--line);
    border-radius: 6px;
  }
  .screen-no {
    color: var(--t2);
  }
  .screen-label {
    font-weight: 600;
  }
  .mono {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  }
  .storyboard-note.warn {
    color: rgb(230, 162, 60);
  }
  .preview-note {
    margin: 8px 0;
  }

  .field {
    display: block;
    margin-bottom: 10px;
  }
  .field > span {
    display: block;
    margin-bottom: 4px;
    font-size: 12px;
    color: var(--t2);
  }
</style>
