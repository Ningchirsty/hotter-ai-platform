<template>
  <div class="studio">
    <!-- 已删项目素材清单 + 批量清理（R26）：只有还留着东西的项目才会出现在这里 -->
    <el-dialog v-model="deletedMaterialsVisible" title="清理已删项目的素材" width="760px" append-to-body>
      <p class="hint">
        删项目只软删项目本身，<b>素材按策略保留着</b>。这里列出"还留着素材的已删项目"，
        勾选后批量清理（对象存储里的文件会真删，附件行软删留痕，生成记录与质检记录删除）。
        <b>分镜、文案、模块计划、视觉基因与操作日志都会保留。</b>
      </p>
      <el-table
        v-loading="deletedMaterialsLoading"
        :data="deletedMaterials"
        size="small"
        empty-text="没有需要清理的已删项目（历史遗留已经打扫干净了）"
        @selection-change="onDeletedSelectionChange"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column label="项目" min-width="220">
          <template #default="{ row }">
            <div>{{ row.taskName }}</div>
            <div class="muted small">taskId {{ row.taskId }} · 删除于 {{ row.deletedAt || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="附件" width="120">
          <template #default="{ row }">{{ row.fileCount }} 个 / {{ mb(row.fileBytes) }} MB</template>
        </el-table-column>
        <el-table-column label="生成记录" width="100">
          <template #default="{ row }">{{ row.generationCount }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span class="muted small">已选 {{ deletedSelection.length }} 个，合计 {{ mb(deletedSelectionBytes) }} MB</span>
        <el-button @click="deletedMaterialsVisible = false">取消</el-button>
        <el-button type="danger" :disabled="!deletedSelection.length" :loading="purgeBusy" @click="doPurgeDeleted">
          清理选中项目的素材
        </el-button>
      </template>
    </el-dialog>

    <div class="workbench">
      <!-- 左：项目列表 -->
      <aside class="panel project-panel">
        <header class="panel-head">
          <h3>视觉项目</h3>
          <div class="panel-head-actions">
            <!-- R26：已删项目的素材默认保留着（删项目只软删），清理是显式动作。
                 入口放这里而不是藏进菜单：历史遗留要能被周期性打扫。 -->
            <button
              v-hasPermi="['creative:project:remove']"
              type="button"
              class="ghost-btn"
              @click="openDeletedMaterials"
            >
              清理已删项目素材
            </button>
            <button type="button" class="ghost-btn" :disabled="loadingProjects" @click="loadProjects">刷新</button>
          </div>
        </header>

        <div class="filter-row">
          <el-input
            v-model="queryTaskName"
            placeholder="按项目名称搜索"
            clearable
            @keyup.enter="loadProjects"
            @clear="loadProjects"
          />
          <el-button type="primary" plain @click="loadProjects">查询</el-button>
        </div>

        <button type="button" class="create-btn" @click="openCreateDialog">＋ 新建视觉项目</button>

        <div v-loading="loadingProjects" class="project-list">
          <p v-if="!projects.length && !loadingProjects" class="empty">
            还没有电商详情页项目。新建一个，或在「业务应用 → 内容生产协同 → 内容任务」里把交付类型选为电商详情图。
          </p>
          <button
            v-for="project in projects"
            :key="String(project.taskId)"
            type="button"
            class="project-item"
            :class="{ active: String(project.taskId) === String(currentProjectId) }"
            @click="selectProject(project)"
          >
            <span class="project-name">{{ project.taskName || '未命名项目' }}</span>
            <span class="project-meta">
              <span class="stage-tag" :class="'is-' + stageType(project.visualStage)">
                {{ stageLabel(project.visualStage) }}
              </span>
              <span class="task-no">{{ project.taskNo }}</span>
            </span>
            <span v-if="project.productName" class="project-sub">{{ project.productName }}</span>
            <span v-if="project.blockingCardCount" class="project-warn">
              有 {{ project.blockingCardCount }} 张阻断卡待处理
            </span>
          </button>
        </div>
      </aside>

      <!-- 右：项目工作台 -->
      <!-- 右：项目工作台（R31：走 §23 的槽位装配——头部/指引线/主舞台/检查器/资产抽屉由配置决定） -->
      <CreativeWorkspace
        v-if="currentProject"
        :task-id="currentProjectId"
        :deliverable-type="currentProject.deliverableType"
        :refresh-token="flowToken"
        :project="currentProject"
        :output-spec="currentOutputSpec"
        :stage-type="stageType(currentProject.visualStage)"
        :loading="loadingDetail"
        @refresh="loadDetail"
        @open-logs="timelineVisible = true"
      >
        <template #header-actions>
          <el-button size="small" @click="openDna">视觉基因</el-button>
          <!-- R25：清理素材是**显式动作**（删项目默认保留素材）。放在项目头部而不是藏进菜单里：
               它要能被人看见，但要经过"先看代价 → 输项目名"两道确认才能生效。 -->
          <el-button
            v-hasPermi="['creative:project:remove']"
            size="small"
            type="danger"
            plain
            :loading="purgeBusy"
            @click="doPurgeMaterials"
          >
            清理素材
          </el-button>
        </template>

        <template #main>
          <section class="panel detail-panel">
            <div class="detail-body">
            <!-- 参考图 / 产品图 -->
            <section class="block">
              <div class="block-head">
                <h4>1. 产品图与参考图</h4>
                <div class="block-actions">
                  <span class="muted">{{ imageFiles.length }} 张</span>
                  <el-tag :type="productImage?.configured ? 'success' : 'warning'" size="small" effect="dark">
                    产品图{{ productImage?.configured ? '：' + (productImage.fileName || '已配置') : '未配置' }}
                  </el-tag>
                </div>
              </div>
              <p class="hint">
                这里有两样不同的东西，别混：<b>产品图</b> = 产品主数据里唯一的那张照片，是
                <b>产品保真基准</b>（质检拿它比对生成图里的产品有没有走形；只用提示词约束，不自动判死）；
                <b>参考图</b> = 本次任务喂给模型的输入图，可以有好多张，是
                <b>一致性基准</b>（质检拿它比对画面是否走样，不一致会被筛除）。
                角色徽标按后端记录的来源如实展示，不靠推测。
              </p>
              <p class="hint">
                上传参考图<b>不会改变项目阶段</b>（已完成的项目也能补图）；只有下面的勾选框会把某张图登记成产品图。
              </p>
              <p class="hint">
                <template v-if="!canBindProductImage">
                  <span class="fact-error">该项目没有关联产品，无法登记产品图（后端会直接拒绝）——请先在项目里选择产品。</span>
                </template>
                <template v-else-if="!productImage?.configured">
                  把上传的产品照片登记为产品图：勾选下面的「同时设为该产品的产品图」，或在某张图片上点「设为产品图」。
                </template>
                <template v-else>
                  {{ productImageOrigin || '产品图已配置' }}
                  <span v-if="productImage.setAt" class="muted">· 设定于 {{ formatTime(productImage.setAt) }}</span>
                </template>
              </p>
              <div class="ref-row">
                <div
                  v-for="file in imageFiles"
                  :key="String(file.fileId)"
                  class="ref-card"
                  :class="{ active: String(file.fileId) === String(selectedFileId) }"
                  @click="selectedFileId = file.fileId"
                >
                  <img v-if="urlOf('file-' + file.fileId)" :src="urlOf('file-' + file.fileId)" :alt="file.fileName" />
                  <span v-else class="ref-loading">读取中…</span>
                  <span class="ref-name">{{ file.fileName }}</span>
                  <el-tag
                    v-if="file.sourceType"
                    size="small"
                    effect="plain"
                    :type="fileSourceType(file.sourceType)"
                  >
                    {{ fileSourceLabel(file.sourceType) }}
                  </el-tag>
                  <span v-if="isProductImageFile(file)" class="ref-badge ok">产品保真基准</span>
                  <span v-else-if="String(file.fileId) === String(selectedFileId)" class="ref-badge">当前参考图</span>
                  <el-button
                    v-if="canBindProductImage && file.fileId != null && !isProductImageFile(file)"
                    size="small"
                    text
                    type="primary"
                    :loading="bindingProductImage === String(file.fileId)"
                    @click.stop="doBindProductImage(file)"
                  >
                    设为产品图
                  </el-button>
                </div>
                <div class="ref-upload-wrap">
                  <el-checkbox v-model="asProductImage" :disabled="!canBindProductImage" class="as-product-image">
                    同时设为该产品的产品图
                  </el-checkbox>
                  <el-upload
                    class="ref-upload"
                    :show-file-list="false"
                    accept="image/png,image/jpeg,image/webp"
                    :http-request="doUpload"
                  >
                    <div class="upload-slot">
                      <span class="plus">＋</span>
                      <span>{{ asProductImage ? '上传并设为产品图' : '上传参考图' }}</span>
                      <span class="hint">PNG/JPG/WEBP，≤20MB</span>
                    </div>
                  </el-upload>
                </div>
              </div>
            </section>

            <!-- 品牌要求（Brief）：品牌部在内容协同录入并确认，本页只读 -->
            <section class="block">
              <div class="block-head">
                <h4>2. 品牌要求（Brief）（由品牌部在内容任务里录入）</h4>
                <div class="block-actions">
                  <el-tag :type="briefStatusType" size="small" effect="dark">{{ briefStatusText }}</el-tag>
                  <el-button
                    size="small"
                    type="warning"
                    plain
                    @click="openBriefChangeDialog"
                  >
                    申请修改品牌要求
                  </el-button>
                  <el-button size="small" plain :loading="briefBusy === 'load'" @click="refreshBrief">
                    刷新
                  </el-button>
                  <el-button size="small" type="primary" plain @click="goContentTask">
                    去内容任务里录入
                  </el-button>
                </div>
              </div>
              <p class="hint">
                <b>本页只读</b>：品牌要求由品牌部在<b>内容生产协同 → 内容任务 → 任务详情 →「品牌要求（Brief）」</b>
                里录入与确认，平面设计按此创作（那边确认后本页即可见）。要改要求请点右上「申请修改品牌要求」，
                提交后进入品牌部的待办（互动确认卡），处理完这里会跟着更新。
                产品事实仍在下面「4. 事实确认」里逐条确认；品牌调性与事实里的 brand_tone
                <b>并存</b>——一个是品牌方自己提的要求，一个是从资料里解析确认的，两者冲突时同时展示、由人裁定，不自动合并。
                这些要求的去向：<b>必显信息</b>与<b>主推卖点</b>进出图的正向提示词，<b>禁用词</b>进出图的负向提示词，
                品牌调性 / 目标人群 / 尺寸规范 / 参考风格作为创作依据。
              </p>
              <p v-if="brandBriefError" class="fact-error">
                {{ brandBriefError }}（点右上「刷新」重试，页面不会用默认值糊过去）
              </p>
              <!-- 修改申请状态：有就明说在等品牌部处理，读不到也说一句（不静默） -->
              <p v-if="briefChangeRequest" class="brief-change-pending">
                已提交修改申请：{{ briefChangeRequest.question || '（无说明）' }}
                <span class="muted">（等待品牌部处理 · {{ formatTime(briefChangeRequest.createTime) || '—' }}）</span>
              </p>
              <p v-else-if="briefChangeError" class="muted brief-change-pending">
                {{ briefChangeError }}
              </p>
              <div class="brief-grid brief-grid-readonly">
                <div v-for="field in briefFields" :key="field.key" class="brief-row">
                  <label>{{ field.label }}</label>
                  <div class="brief-control">
                    <div class="brief-value" :class="{ 'is-empty': !briefValueOf(field.key) }">
                      {{ briefValueOf(field.key) || '—' }}
                    </div>
                    <!-- 参考风格：品牌方给的参考图（只读展示；它们同时也是任务附件） -->
                    <div v-if="field.key === 'styleRef'" class="brief-style-images">
                      <BriefStyleImages
                        :task-id="currentProjectId"
                        :images="brandBrief?.styleRefImages || []"
                        :editable="false"
                        source="creative"
                      />
                      <span v-if="(brandBrief?.styleRefImages || []).length" class="hint">
                        这些图也是任务附件，可以在上面「1. 产品图与参考图」里被选作出图参考图。
                      </span>
                    </div>
                    <span class="hint">{{ field.hint }}</span>
                  </div>
                </div>
              </div>
            </section>

            <!-- 文案与要点：详情页的「字」 -->
            <section class="block">
              <div class="block-head">
                <h4>3. 文案与要点</h4>
                <div class="block-actions">
                  <span class="muted">共 {{ copyBlocks.length }} 条</span>
                  <el-button size="small" plain :loading="copyBusy === 'load'" @click="loadCopyBlocks">刷新</el-button>
                </div>
              </div>
              <p class="hint">
                这块是详情页要说的「字」。去向按后端实际接线如实写：<b>卖点 / 正文 / 参数</b>按这里的顺序进
                <b>详情页长图</b>（卖点还会进分镜草稿的卖点屏）。
                「必显信息」不在这里录入——它是<b>品牌方的要求</b>，统一在「2. 品牌要求（Brief）」里（品牌部在内容任务里填），由出图提示词与闸门引用，
                避免同一件事有两个真相源。
                与分镜的分工：分镜屏文案是「这一屏这张图配什么字」，<b>R7 起屏文案也会进图像提示词</b>（画面独白优先）；
                而这里整页的文字不进出图提示词——出图提示词用的是视觉基因 + 「2. 品牌要求（Brief）」的必显 / 主推 / 禁用词。
              </p>
              <p v-if="copyLoadError" class="fact-error">
                {{ copyLoadError }}（点右上「刷新」重试，页面不会用空表糊过去）
              </p>
              <el-tabs v-model="copyTab">
                <el-tab-pane v-for="tab in copyTabs" :key="tab.value" :label="tab.label" :name="tab.value">
                  <p class="hint">{{ tab.hint }}</p>
                  <p class="copy-usedat">{{ tab.usedAt }}</p>
                  <div class="block-actions copy-toolbar">
                    <el-button size="small" type="primary" plain @click="openCopyBlockDialog(tab.value)">
                      ＋ 新增{{ tab.label }}
                    </el-button>
                    <el-button
                      v-if="tab.value === 'SPEC_ROW'"
                      size="small"
                      plain
                      :loading="copyBusy === 'seed'"
                      @click="doSeedFromFacts"
                    >
                      从已确认事实派生
                    </el-button>
                    <span v-if="tab.value === 'SPEC_ROW'" class="hint">
                      派生出来的行标为「事实派生」；改过事实后请重新派生，以免两处不一致。
                    </span>
                    <span v-else-if="tab.value === 'SELLING_POINT'" class="hint">
                      用「↑ / ↓」调整优先级，顺序即详情页从上到下的顺序，点一下立即保存。
                    </span>
                  </div>
                  <!-- 无框行列表：与「事实确认」同一套写法（发丝分隔线 + 悬停高亮），
                       不用白色表格容器；排序号显示"第几条"而不是后端 sortNo（步长 10 看着莫名） -->
                  <div v-if="activeCopyBlocks.length" class="copy-list">
                    <div
                      v-for="(row, idx) in activeCopyBlocks"
                      :key="String(asBlock(row).id)"
                      class="copy-row"
                    >
                      <div class="copy-order">
                        <span class="ord">{{ idx + 1 }}</span>
                        <template v-if="tab.value === 'SELLING_POINT'">
                          <button
                            type="button"
                            class="ord-btn"
                            title="上移（提高优先级）"
                            :disabled="isFirstBlock(tab.value, asBlock(row)) || copyBusy === 'reorder'"
                            @click="moveBlock(asBlock(row), -1)"
                          >
                            ↑
                          </button>
                          <button
                            type="button"
                            class="ord-btn"
                            title="下移（降低优先级）"
                            :disabled="isLastBlock(tab.value, asBlock(row)) || copyBusy === 'reorder'"
                            @click="moveBlock(asBlock(row), 1)"
                          >
                            ↓
                          </button>
                        </template>
                      </div>
                      <div class="copy-body">
                        <div class="copy-line1">
                          <span class="copy-title">{{ asBlock(row).title || '—' }}</span>
                          <span class="copy-src" :class="'is-' + copySourceType(asBlock(row).source)">
                            {{ copySourceLabel(asBlock(row).source) }}<span
                              v-if="asBlock(row).sourceRef"
                              class="muted"
                            > · {{ asBlock(row).sourceRef }}</span>
                          </span>
                          <span class="fact-status" :class="'is-' + copyStatusType(asBlock(row).status)">
                            {{ copyStatusLabel(asBlock(row).status) }}
                          </span>
                        </div>
                        <p class="copy-text">{{ asBlock(row).content || '—' }}</p>
                      </div>
                      <div class="copy-ops">
                        <el-button
                          link
                          size="small"
                          type="primary"
                          @click="openCopyBlockDialog(tab.value, asBlock(row))"
                        >
                          编辑
                        </el-button>
                        <el-button
                          link
                          size="small"
                          type="danger"
                          :loading="copyBusy === 'block-' + String(asBlock(row).id)"
                          @click="doDeleteCopyBlock(asBlock(row))"
                        >
                          删除
                        </el-button>
                      </div>
                    </div>
                  </div>
                  <p v-else class="empty">这一组还没有内容。点上面的「新增」录入。</p>
                </el-tab-pane>
              </el-tabs>
            </section>

            <!-- 事实确认 -->
            <section class="block">
              <div class="block-head">
                <h4>4. 事实确认</h4>
                <div class="block-actions">
                  <span class="muted">已确认 {{ confirmedFacts.length }} 条 / 共 {{ facts.length }} 行</span>
                  <el-button
                    size="small"
                    plain
                    :loading="factBusy === 'confirmUnambiguous'"
                    @click="doConfirmUnambiguousFacts"
                  >
                    一键确认无歧义项
                  </el-button>
                  <el-button size="small" type="primary" plain @click="openManualFact()">人工录入</el-button>
                </div>
              </div>
              <p class="hint">
                只有 <b>CONFIRMED</b> 的事实才会进入基因 / 方向 / 分镜文案的推导；PENDING 与已否决都不算。
              </p>
              <p v-if="factLoadError" class="fact-error">
                {{ factLoadError }}（点右上「刷新」重试，页面不会用默认值糊过去）
              </p>

              <p v-if="!fieldOptionsLoaded && !factLoadError" class="fact-error">
                字段选项接口没取到，无法判断闸门必填项是否齐备——不猜，请在下方事实表里逐条确认。
              </p>
              <template v-else>
                <!-- 闸门必填项：紧凑状态带。原先是竖排一行一项（9 项就吃掉大半屏），
                     信息量一样但更省高度，也与页面其它「状态条」写法一致。 -->
                <div class="fact-gate">
                  <span
                    v-for="option in requiredFieldOptions"
                    :key="String(option.fieldCode)"
                    class="gate-chip"
                    :class="{ ok: option.satisfied }"
                    :title="optionLabel(option)"
                  >
                    <span class="mark">{{ option.satisfied ? '✓' : '✗' }}</span>
                    {{ option.fieldName || option.fieldCode }}
                    <em class="gate-level">{{ option.gateLevel || '—' }}</em>
                  </span>
                  <span v-if="!requiredFieldOptions.length" class="muted">该交付类型没有声明必填事实项。</span>
                </div>
                <div v-if="unsatisfiedRequiredOptions.length" class="block-actions">
                  <el-button
                    v-for="option in unsatisfiedRequiredOptions"
                    :key="'fill-' + option.fieldCode"
                    size="small"
                    @click="openManualFact(option.fieldCode)"
                  >
                    ＋ 录入「{{ option.fieldName || option.fieldCode }}」
                  </el-button>
                </div>
              </template>

              <!-- 事实清单：无框行列表（发丝分隔线 + 左侧状态色条），不用白色表格容器。
                   为什么改：白底表格在暗色工作室里是一块突兀的亮面，且列宽固定会把
                   「值/来源/原文摘录」挤成省略号；改成两行一行之后，值与来源都能直接读。 -->
              <div v-if="facts.length" class="fact-list">
                <div class="fact-tools">
                  <button
                    v-for="item in factFilters"
                    :key="item.value"
                    type="button"
                    class="fact-filter"
                    :class="{ active: factFilter === item.value }"
                    @click="factFilter = item.value"
                  >
                    {{ item.label }}<span class="count">{{ item.count }}</span>
                  </button>
                  <span class="muted fact-sort-note">待确认 / 冲突排在前面</span>
                </div>
                <div
                  v-for="row in visibleFacts"
                  :key="String(asFact(row).snapshotId)"
                  class="fact-row"
                  :class="'st-' + String(asFact(row).confirmStatus || 'PENDING').toLowerCase()"
                >
                  <div class="fact-main">
                    <div class="fact-line1">
                      <span class="fact-name">{{ asFact(row).fieldName || asFact(row).fieldCode }}</span>
                      <span class="fact-value">
                        {{ asFact(row).fieldValue }}<span v-if="asFact(row).unit" class="muted"> {{ asFact(row).unit }}</span>
                      </span>
                      <span class="fact-status" :class="'is-' + factStatusType(asFact(row).confirmStatus)">
                        {{ factStatusLabel(asFact(row).confirmStatus) }}
                      </span>
                    </div>
                    <div class="fact-line2">
                      <span class="fact-src">
                        {{ asFact(row).sourceFileName || '—'
                        }}<span v-if="asFact(row).sourceLocator" class="muted"> · {{ asFact(row).sourceLocator }}</span>
                      </span>
                      <span
                        v-if="asFact(row).sourceExcerpt"
                        class="fact-excerpt"
                        :class="{ open: !!expandedFacts[String(asFact(row).snapshotId)] }"
                        :title="asFact(row).sourceExcerpt"
                        @click="toggleFactExcerpt(asFact(row))"
                      >
                        原文：{{ asFact(row).sourceExcerpt }}
                      </span>
                      <span v-else class="muted">原文：—</span>
                    </div>
                  </div>
                  <div class="fact-ops">
                    <el-button
                      link
                      size="small"
                      type="primary"
                      :disabled="asFact(row).confirmStatus === 'CONFIRMED'"
                      :loading="factBusy === 'fact-' + asFact(row).snapshotId"
                      @click="doConfirmFact(asFact(row))"
                    >
                      确认
                    </el-button>
                    <el-button
                      link
                      size="small"
                      type="danger"
                      :disabled="asFact(row).confirmStatus === 'REJECTED'"
                      :loading="factBusy === 'fact-' + asFact(row).snapshotId"
                      @click="doRejectFact(asFact(row))"
                    >
                      驳回
                    </el-button>
                  </div>
                </div>
                <p v-if="!visibleFacts.length" class="empty">该筛选下没有事实行。</p>
              </div>
              <p v-else class="empty">
                还没有事实候选。资料解析后会自动落成待确认行；也可以点「人工录入」补齐。
              </p>
            </section>

            <!-- 出图 -->
            <section class="block">
              <div class="block-head">
                <h4>5. 生成 HERO 主图</h4>
                <span class="muted">每次出 1 张候选；重试=新增一次候选</span>
              </div>
              <div class="form-row">
                <label>出图工作流</label>
                <el-select v-model="heroForm.workflowCode" placeholder="使用默认已发布工作流" style="width: 320px">
                  <el-option
                    v-for="wf in workflows"
                    :key="wf.workflowCode"
                    :label="`${wf.workflowCode}（${wf.capabilityCode} · ${wf.published ? '已发布' : wf.status}）`"
                    :value="wf.workflowCode"
                  />
                </el-select>
              </div>
              <div class="form-row">
                <label>画面描述</label>
                <el-input
                  v-model="heroForm.prompt"
                  type="textarea"
                  :rows="3"
                  maxlength="1000"
                  show-word-limit
                  placeholder="留空则用默认主图提示词（产品居中、纯净背景、影棚光、保留原有结构与配色）"
                />
              </div>
              <template v-if="dnaStateLoaded">
                <p v-if="promptFromDna" class="dna-hint">
                  已按<b>视觉基因</b>预填提示词（用到的维度：{{ promptApplied.join('、') }}）。可以改；改了就以你写的为准。
                </p>
                <p v-else-if="dnaLocked" class="dna-hint">
                  将按<b>已锁定的视觉基因 {{ dnaLockedVersion }}</b>出图，但派生提示词尚未载入——点
                  <el-button link type="primary" size="small" @click="prefillPromptFromDna">这里</el-button>
                  载入。
                </p>
                <p v-else class="dna-hint muted">
                  这个项目还没有锁定视觉基因，提示词按默认模板生成。建议先到
                  <el-button link type="primary" size="small" @click="openDna">视觉基因</el-button>
                  定义配色与光线并锁定。
                </p>
              </template>
              <p v-else class="dna-hint muted">正在检查该项目的视觉基因…</p>
              <div class="form-row">
                <label>负向提示</label>
                <el-input
                  v-model="heroForm.negativePrompt"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  placeholder="留空则用默认（文字、水印、产品变形、结构缺失…）"
                />
              </div>
              <div class="submit-row">
                <el-button
                  type="primary"
                  :loading="submitting"
                  :disabled="!imageFiles.length"
                  @click="doGenerate"
                >
                  {{ submitting ? '提交中…' : '生成 HERO 主图候选' }}
                </el-button>
                <span v-if="!imageFiles.length" class="hint">请先上传参考图</span>
              </div>
            </section>

            <!-- 候选 -->
            <section class="block">
              <div class="block-head">
                <h4>6. 出图候选</h4>
                <span class="muted">
                  {{ generations.length }} 条
                  <template v-if="polling">· 状态跟踪中…</template>
                </span>
              </div>
              <p v-if="!generations.length" class="empty">还没有候选。填好描述后点上面的生成按钮。</p>
              <div v-else class="candidate-grid">
                <div v-for="gen in generations" :key="String(gen.id)" class="candidate-card">
                  <div class="candidate-cover" @click="gen.previewable && openPreview(gen)">
                    <img
                      v-if="urlOf('gen-' + gen.id)"
                      :src="urlOf('gen-' + gen.id)"
                      :alt="`候选 ${gen.candidateNo}`"
                    />
                    <span v-else class="cover-placeholder">
                      {{ gen.status === 'RUNNING' || gen.status === 'QUEUED' ? '出图中…' : '暂无产出' }}
                    </span>
                  </div>
                  <div class="candidate-meta">
                    <span class="gen-status" :class="'is-' + genStatusType(gen.status)">
                      #{{ gen.candidateNo }} · {{ gen.statusDesc || genStatusLabel(gen.status) }}
                    </span>
                    <span v-if="gen.outputWidth" class="muted">{{ gen.outputWidth }}×{{ gen.outputHeight }}</span>
                    <span v-if="gen.durationMs" class="muted">{{ (gen.durationMs / 1000).toFixed(1) }}s</span>
                  </div>
                  <p v-if="gen.errorMessage" class="gen-error" :title="gen.errorMessage">{{ gen.errorMessage }}</p>
                  <div class="candidate-actions">
                    <el-button v-if="gen.previewable" size="small" text type="primary" @click="openPreview(gen)">
                      预览
                    </el-button>
                    <el-button
                      v-if="gen.retryable"
                      size="small"
                      text
                      type="warning"
                      :loading="retryingId === String(gen.id)"
                      @click="doRetry(gen)"
                    >
                      重试
                    </el-button>
                  </div>
                </div>
              </div>
            </section>
          </div>
          </section>
        </template>
      </CreativeWorkspace>

      <!-- 没有选中项目时的占位（工作台只在有项目时渲染） -->
      <section v-else class="panel detail-panel">
        <div class="placeholder">
          <p>从左侧选择一个视觉项目开始。</p>
          <p class="hint">选好项目后：上传参考图 → 看品牌要求（品牌部在内容任务里录入）与文案要点 → 描述你想要的画面 → 生成 HERO 主图候选。</p>
        </div>
      </section>
    </div>

    <!-- 人工录入事实 -->
    <el-dialog v-model="manualFactVisible" title="人工录入事实" width="520px">
      <el-form label-width="90px">
        <el-form-item label="字段">
          <el-select
            v-model="manualForm.fieldCode"
            filterable
            allow-create
            default-first-option
            placeholder="从闸门字段里选（不支持时可直接输入编码）"
            style="width: 100%"
          >
            <el-option
              v-for="option in fieldOptions"
              :key="String(option.fieldCode)"
              :label="optionLabel(option)"
              :value="option.fieldCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="字段值">
          <el-input v-model="manualForm.value" placeholder="字段值" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="manualForm.remark" placeholder="备注（可空）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualFactVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="factBusy === 'manualFact'"
          :disabled="!manualForm.fieldCode || !manualForm.value"
          @click="doAddManualFact"
        >
          录入（落为待确认）
        </el-button>
      </template>
    </el-dialog>

    <!-- 申请修改品牌要求：设计不能直接改（品牌要求归品牌部），但需求要能到品牌部手里 -->
    <el-dialog v-model="briefChangeVisible" title="申请修改品牌要求" width="560px">
      <el-form label-width="96px">
        <el-form-item label="申请内容">
          <el-input
            v-model="briefChangeMessage"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="希望品牌方改什么，例如：必显信息里请补上「包装上的有机认证标志」；禁用词请删掉「最好」"
          />
        </el-form-item>
      </el-form>
      <p class="hint">
        提交后由<b>品牌部</b>在内容生产协同 → 内容任务 → 任务详情 →「品牌要求（Brief）」里处理
        （落到互动确认卡，可在内容任务页看到）。本页只读，改完这里会自动跟着更新。
      </p>
      <template #footer>
        <el-button @click="briefChangeVisible = false">取消</el-button>
        <el-button type="primary" :loading="briefBusy === 'change'" @click="submitBriefChangeRequest">
          提交申请
        </el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑文案与要点块 -->
    <el-dialog v-model="copyDialogVisible" :title="copyDialogTitle" width="560px">
      <el-form label-width="96px">
        <el-form-item label="类型">
          <span>{{ COPY_BLOCK_TYPE_LABELS[copyForm.blockType] || copyForm.blockType }}</span>
        </el-form-item>
        <el-form-item :label="copyFieldLabels.title">
          <el-input
            v-model="copyForm.title"
            maxlength="255"
            show-word-limit
            :placeholder="copyFieldLabels.titlePlaceholder"
          />
        </el-form-item>
        <el-form-item :label="copyFieldLabels.content">
          <el-input
            v-model="copyForm.content"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            :placeholder="copyFieldLabels.contentPlaceholder"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="copyForm.remark" maxlength="500" show-word-limit placeholder="备注（可空）" />
        </el-form-item>
      </el-form>
      <p v-if="copyDialogSourceHint" class="hint">{{ copyDialogSourceHint }}</p>
      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="copyBusy === 'saveBlock'"
          :disabled="!copyForm.title.trim() && !copyForm.content.trim()"
          @click="doSaveCopyBlock"
        >
          {{ copyEditingId == null ? '新增' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 新建项目 -->
    <el-dialog v-model="createVisible" title="新建视觉项目" width="520px">
      <el-form label-width="90px">
        <el-form-item label="项目名称">
          <el-input v-model="createForm.taskName" maxlength="255" placeholder="如：趣往单枝花-详情页视觉" />
        </el-form-item>
        <el-form-item label="产品">
          <el-select
            v-model="createForm.productId"
            placeholder="可不选；选中后会带出产品名（影响默认提示词）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="product in products"
              :key="String(product.productId)"
              :label="product.productName + (product.skuCode ? ' · ' + product.skuCode : '')"
              :value="product.productId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" maxlength="500" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 候选预览 -->
    <el-dialog v-model="previewVisible" title="候选预览" width="720px" @closed="closePreview">
      <div class="preview-wrap">
        <img v-if="previewUrl" :src="previewUrl" alt="候选原图" />
        <p v-else class="empty">加载中…</p>
      </div>
    </el-dialog>

    <!-- 操作日志（全链路可追溯） -->
    <el-dialog v-model="timelineVisible" title="操作日志（阶段事件）" width="760px">
      <el-timeline v-if="timeline.length">
        <el-timeline-item
          v-for="event in timeline"
          :key="String(event.id)"
          :timestamp="formatTime(event.createTime)"
          placement="top"
        >
          <div class="tl-title">
            <strong>{{ event.action || event.eventType }}</strong>
            <span v-if="event.fromStage !== event.toStage" class="tl-stage">
              {{ stageLabel(event.fromStage) }} → {{ stageLabel(event.toStage) }}
            </span>
          </div>
          <div class="muted">{{ event.actorName || '系统' }}</div>
          <div v-if="event.detailJson" class="tl-detail">{{ event.detailJson }}</div>
        </el-timeline-item>
      </el-timeline>
      <p v-else class="empty">暂无事件。</p>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { UploadRequestOptions } from 'element-plus';
import { productOptions } from '@/api/content/product';
import type { CpProductVO } from '@/api/content/product/types';
import {
  addManualFact,
  confirmFact,
  confirmUnambiguousFacts,
  factFieldOptions,
  listFact,
  rejectFact
} from '@/api/content/fact';
import type { CpFactFieldOptionVO, CpFactSnapshotVO } from '@/api/content/fact/types';
import type { CpTaskFileVO } from '@/api/content/task/types';
// 品牌要求（Brief）已归属内容生产协同：读的是内容域接口，本页只读展示
import { getBrandBrief } from '@/api/content/brief';
import type { BrandBriefFieldKey, BrandBriefVO } from '@/api/content/brief/types';
import {
  BRAND_BRIEF_CHANGE_FIELD_CODE,
  BRAND_BRIEF_FIELDS,
  BRAND_BRIEF_STATUS_TYPES,
  briefStatusText as briefStatusTextOf
} from '@/api/content/brief/types';
// 待处理申请读的是内容域的互动确认卡（品牌部在那张卡上处理）
import { listCard } from '@/api/content/card';
import type { CpInteractionCardVO } from '@/api/content/card/types';
// 参考风格图片条：与内容任务页共用同一个组件（blob 加载与回收只维护一处）
import BriefStyleImages from '@/components/BriefStyleImages/index.vue';
import {
  addCopyBlock,
  addCreativeProject,
  bindProjectProductImage,
  deleteCopyBlock,
  fetchCreativeFileBlobUrl,
  fetchGenerationPreviewBlobUrl,
  fetchGenerationThumbnailBlobUrl,
  getCreativeProject,
  getDna,
  getDnaPrompt,
  getProjectProductImage,
  listCopyBlocks,
  listCreativeFiles,
  listCreativeProject,
  listCreativeTimeline,
  listCreativeWorkflows,
  listDnaVersions,
  listGenerations,
  raiseBriefChangeRequest,
  reorderCopyBlocks,
  retryGeneration,
  seedCopyBlocksFromFacts,
  submitHero,
  updateCopyBlock,
  uploadCreativeReference,
  getProjectMaterials,
  purgeProjectMaterials,
  listDeletedProjectMaterials,
  purgeDeletedProjectMaterials
} from '@/api/creative';
import type {
  CopyBlockForm,
  CopyBlockVO,
  CreativeProjectVO,
  CreativeWorkflowVO,
  DpGenerationVO,
  DpStageEventVO,
  ProjectMaterialsVO,
  ProjectProductImageVO,
  TagType
} from '@/api/creative/types';
import {
  COPY_BLOCK_SOURCE_LABELS,
  COPY_BLOCK_SOURCE_TYPES,
  COPY_BLOCK_STATUS_LABELS,
  COPY_BLOCK_STATUS_TYPES,
  COPY_BLOCK_TYPE_LABELS,
  CREATIVE_STAGE_LABELS,
  CREATIVE_STAGE_TYPES,
  FILE_SOURCE_LABELS,
  FILE_SOURCE_TYPES,
  GENERATION_STATUS_LABELS,
  GENERATION_STATUS_TYPES
} from '@/api/creative/types';
import CreativeWorkspace from '../components/CreativeWorkspace.vue';
import { listOutputSpecs } from '@/api/creative/scenario';
import type { ScenarioOutputSpec } from '@/api/creative/scenario';

// R0 期的"接线版"说明横幅已在 V0.2 FIX-005 删除：它写的是"视觉基因/分镜/视觉门/排版在 R1–R3 交付"，
// 而这四块早已上线，留着只会误导使用的人（连同 GUIDE_KEY/showGuide/dismissGuide 一起清理）。

const projects = ref<CreativeProjectVO[]>([]);
const loadingProjects = ref(false);
const queryTaskName = ref('');
const currentProjectId = ref<string | number>('');
const currentProject = ref<CreativeProjectVO | null>(null);
const files = ref<CpTaskFileVO[]>([]);
const generations = ref<DpGenerationVO[]>([]);
const timeline = ref<DpStageEventVO[]>([]);
const workflows = ref<CreativeWorkflowVO[]>([]);
const products = ref<CpProductVO[]>([]);
const selectedFileId = ref<string | number>('');

/**
 * 产品图（产品主数据里的那张）。
 *
 * <p>它是「产品保真基准」：出图与质检会拿产品图与生成图比对（另一个基准是本次喂给模型的参考图）。
 * 所以这里必须让人看得见、并且能显式登记——否则产品主数据永远是空的，产品基准质检就没有基准图。</p>
 */
const productImage = ref<ProjectProductImageVO | null>(null);
/** 上传参考图时是否同时登记为该产品的产品图 */
const asProductImage = ref(false);
/** 正在登记产品图的附件ID（按行 loading） */
const bindingProductImage = ref('');

/** 事实确认（闸门必填项 + 事实清单） */
const facts = ref<CpFactSnapshotVO[]>([]);
const fieldOptions = ref<CpFactFieldOptionVO[]>([]);
/** 字段选项接口是否取到：取不到就不下「必填项齐了没」的结论 */
const fieldOptionsLoaded = ref(false);
const factLoadError = ref('');
const factBusy = ref('');
const manualFactVisible = ref(false);
const manualForm = reactive({ fieldCode: '', value: '', remark: '' });

// ------------------------------------------------------------------
// 品牌要求（Brief）：**只读**展示（品牌部在内容任务里录入与确认）
// ------------------------------------------------------------------

/**
 * 字段定义直接复用 `@/api/content/brief` 的那一份（与内容任务页共用）。
 * 为什么不在这里再定义一遍：两处各写一份字段表，迟早出现"这边有必显、那边没有"的偏差，
 * 长度上限与列宽也只需要维护一处。
 */
const briefFields = BRAND_BRIEF_FIELDS;

/** 服务端当前的品牌要求（含状态与确认人/时间）；未填写时后端也返回对象 */
const brandBrief = ref<BrandBriefVO | null>(null);
/** 是否成功从服务端取过：没取到就不下「未填写」的结论 */
const briefLoaded = ref(false);
/** 当前这份要求属于哪个项目：切项目后必须重新取，否则会拿上一个项目的结果冒充（竞态） */
const briefTaskId = ref('');
const brandBriefError = ref('');
const briefBusy = ref('');

/** 待处理的"申请修改品牌要求"（内容域的互动确认卡），有就提示在等品牌部处理 */
const briefChangeRequest = ref<CpInteractionCardVO | null>(null);
const briefChangeError = ref('');
const briefChangeVisible = ref(false);
const briefChangeMessage = ref('');

/** 某个字段的展示值（空值由模板显示成 —，这里不改写数据本身） */
function briefValueOf(key: BrandBriefFieldKey): string {
  return ((brandBrief.value?.[key] as string | undefined) ?? '').trim();
}

/** 状态徽标文案：与内容任务页共用同一个函数，避免两处口径不一致（谁 · 何时） */
const briefStatusText = computed(() => briefStatusTextOf(brandBrief.value, briefLoaded.value, formatTime));

/**
 * 读"申请修改品牌要求"的待处理状态（内容域互动确认卡）。
 *
 * <p>读不到不阻塞只读展示，但也不能静默：显示一句说明，让人知道"没看到申请状态"
 * 不等于"没有申请"。按 `cardType=SUPPLEMENT + fieldCode=brand_brief` 筛，
 * 与后端 {@code ContentCardServiceImpl.BRIEF_FIELD_CODE} 一致。</p>
 */
async function loadBriefChangeRequest() {
  const taskId = String(currentProjectId.value || '');
  if (!taskId) return;
  briefChangeError.value = '';
  try {
    const res = await listCard({
      taskId: taskId,
      cardType: 'SUPPLEMENT',
      status: 'PENDING',
      pageNum: 1,
      pageSize: 10
    });
    if (String(currentProjectId.value || '') !== taskId) return;
    const rows = res.data?.rows || [];
    briefChangeRequest.value =
      rows.find((row) => (row.fieldCode || '') === BRAND_BRIEF_CHANGE_FIELD_CODE) || null;
  } catch (error) {
    if (String(currentProjectId.value || '') !== taskId) return;
    briefChangeRequest.value = null;
    briefChangeError.value =
      '修改申请状态没取到（' + ((await extractErrorMessage(error)) ?? '接口失败') + '），不影响上面的只读展示。';
  }
}

/** 刷新品牌要求：内容与"修改申请状态"一起刷（两个都点一次才叫刷新） */
function refreshBrief() {
  void loadBrandBrief(true);
  void loadBriefChangeRequest();
}

/** 打开"申请修改品牌要求"弹窗 */
function openBriefChangeDialog() {
  if (!currentProjectId.value) {
    ElMessage.warning('还没选中项目，请先在左侧选择视觉项目');
    return;
  }
  briefChangeMessage.value = '';
  briefChangeVisible.value = true;
}

/** 提交修改申请：进品牌部待办（内容域互动确认卡），本页只读所以改完要来这边看 */
async function submitBriefChangeRequest() {
  if (!currentProjectId.value || briefBusy.value) return;
  const message = briefChangeMessage.value.trim();
  if (!message) {
    ElMessage.warning('请写清希望品牌方修改什么');
    return;
  }
  briefBusy.value = 'change';
  try {
    const res = await raiseBriefChangeRequest(currentProjectId.value, message);
    const created = res.data?.created !== false;
    if (created) {
      ElMessage.success('已提交给品牌部（内容任务的「互动确认卡」），处理后会在这里看到最新的品牌要求');
    } else {
      ElMessage.warning(
        `已有待处理的修改申请，没有重复提交（卡片ID ${res.data?.cardId ?? '—'}）；品牌部处理后这里会更新`
      );
    }
    briefChangeVisible.value = false;
    await loadBriefChangeRequest();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '提交修改申请失败');
  } finally {
    briefBusy.value = '';
  }
}

/** 去内容任务页录入：路由是从菜单表实查出来的（业务应用 → 内容生产协同 → 内容任务 = /business/content/task） */
function goContentTask() {
  window.open('/business/content/task', '_self');
}

const briefStatusType = computed<TagType>(() => {
  if (!briefLoaded.value || !brandBrief.value?.configured) return 'info';
  return BRAND_BRIEF_STATUS_TYPES[brandBrief.value.status || 'DRAFT'] || 'warning';
});

// ------------------------------------------------------------------
// 文案与要点（详情页的「字」）
// ------------------------------------------------------------------

/**
 * 三个分组：标签、这块字用在哪（usedAt，照后端实际接线如实写）、字段名（标题/内容）随类型变化。
 *
 * <p>{@code usedAt} 不是文案装饰：它按后端 R7 的真实读取点写，写错就等于骗人。
 * 依据（改动前请先看代码）：卖点=详情页排版 + 分镜草稿前 2 条（CreativeLayoutServiceImpl#copyBlocksNode、
 * CreativeStoryboardServiceImpl#sellingPointHints）；正文/参数=详情页排版。
 *
 * <p>为什么没有「必显信息」这一组：品牌方的必显要求统一在「品牌要求（Brief）」里（品牌部在内容任务里填），
 * 提示词与闸门读的也是它；文案块里再放一个同义类型会出现两个真相源（后端 MUST_SHOW 枚举值
 * 仅为兼容保留、已无录入入口）。</p>
 */
const copyTabs: Array<{
  value: string;
  label: string;
  hint: string;
  usedAt: string;
  titleField: string;
  contentField: string;
}> = [
  {
    value: 'SELLING_POINT',
    label: COPY_BLOCK_TYPE_LABELS.SELLING_POINT,
    hint: '卖给人的理由，顺序从上到下就是优先级顺序。',
    usedAt: '去向：详情页长图；另外生成分镜草稿时，按顺序取前 2 条写进两个卖点屏的文案。',
    titleField: '卖点标题',
    contentField: '卖点说明'
  },
  {
    value: 'BODY_SECTION',
    label: COPY_BLOCK_TYPE_LABELS.BODY_SECTION,
    hint: '详情页正文的分段，一段一块。',
    usedAt: '去向：详情页长图（按顺序从上到下排版）。',
    titleField: '段落小标题',
    contentField: '段落正文'
  },
  {
    value: 'SPEC_ROW',
    label: COPY_BLOCK_TYPE_LABELS.SPEC_ROW,
    hint: '参数表的一行，可以从已确认事实派生，避免手抄。',
    usedAt: '去向：详情页长图的参数表。',
    titleField: '参数名',
    contentField: '参数值'
  }
];

const copyTab = ref('SELLING_POINT');
const copyBlocks = ref<CopyBlockVO[]>([]);
const copyLoadError = ref('');
const copyBusy = ref('');
const deletedSelectionBytes = computed(() =>
  deletedSelection.value.reduce((sum, r) => sum + (r.fileBytes || 0), 0)
);
const purgeBusy = ref(false);
const deletedMaterialsVisible = ref(false);
const deletedMaterialsLoading = ref(false);
const deletedMaterials = ref<ProjectMaterialsVO[]>([]);
const deletedSelection = ref<ProjectMaterialsVO[]>([]);
const copyDialogVisible = ref(false);
/** 正在编辑的块ID；null 表示新增 */
const copyEditingId = ref<string | number | null>(null);
/** 正在编辑的块来源（用于在弹窗里如实说明「事实派生」的语义） */
const copyEditingSource = ref('');
const copyForm = reactive<CopyBlockForm>({ blockType: 'SELLING_POINT', title: '', content: '', remark: '' });

const copyDialogTypeLabel = computed(() => COPY_BLOCK_TYPE_LABELS[copyForm.blockType] || copyForm.blockType);

const copyDialogTitle = computed(() => (copyEditingId.value == null ? '新增' : '编辑') + copyDialogTypeLabel.value);

const copyFieldLabels = computed(() => {
  const tab = copyTabs.find((item) => item.value === copyForm.blockType) || copyTabs[0];
  return {
    title: tab.titleField,
    content: tab.contentField,
    titlePlaceholder: tab.value === 'SPEC_ROW' ? '如：主体版本' : '一句话说清（可空）',
    contentPlaceholder:
      tab.value === 'SPEC_ROW' ? '如：单枝 50cm' : '要出现在详情页上的文字'
  };
});

const copyDialogSourceHint = computed(() => {
  if (copyEditingSource.value === 'FACT') {
    return '这条是「事实派生」的：内容来自已确认事实，人工改动只影响这一条，不会写回事实；改过事实后请重新派生，以免两处不一致。';
  }
  if (copyEditingSource.value === 'MODEL') {
    return '这条是「模型起草」的：来源徽标会一直保留，方便你分得清哪些是模型写的、哪些是自己写的。';
  }
  return '';
});

/** 流程指引线刷新令牌：事实动作成功后 +1，指引线会重新读一次阶段 */
const flowToken = ref(0);
/** 项目详情加载中（R31：工作台头部的刷新按钮用） */
const loadingDetail = ref(false);
/** 该交付类型的默认输出规格（R31：工作台头部显示渠道与尺寸，取不到就如实说未配置） */
const currentOutputSpec = ref<ScenarioOutputSpec | null>(null);

const submitting = ref(false);
const creating = ref(false);
const retryingId = ref('');
const polling = ref(false);
const createVisible = ref(false);
const previewVisible = ref(false);
const timelineVisible = ref(false);
const previewUrl = ref('');

const createForm = reactive({ taskName: '', productId: '' as string | number, remark: '' });
const heroForm = reactive({ workflowCode: '', prompt: '', negativePrompt: '' });

/** 提示词是否来自视觉基因（页面如实说明，不让人以为是自己写的） */
const promptFromDna = ref(false);
const promptApplied = ref<string[]>([]);
const dnaLocked = ref(false);
const dnaLockedVersion = ref('');
/** 基因判定是否已完成：完成前不显示任何结论，避免闪一下「还没有锁定基因」这种错信息 */
const dnaStateLoaded = ref(false);

/**
 * blob URL 台账：key → URL。
 *
 * 必须是响应式（ref + 展开赋值），不能用普通 Map——普通 Map 的增删不会触发重渲染，
 * 表现为「接口全 200、页面上的 <img> 永远不出现」（这个坑实际踩过一次）。
 * 切换/卸载时统一 revoke，避免内存泄漏。
 */
const objectUrls = ref<Record<string, string>>({});
let pollTimer: number | undefined;
let pollTicks = 0;
const POLL_INTERVAL_MS = 5000;
const POLL_MAX_TICKS = 120;

const imageFiles = computed(() => files.value.filter((f) => (f.fileKind || '').toUpperCase() === 'IMAGE'));

/** 能不能登记产品图：后端要求项目必须关联产品，否则会直接拒绝 */
const canBindProductImage = computed(() => currentProject.value?.productId != null);

/** 某张附件是不是当前的产品图（按后端返回的产品图附件ID判定，不靠猜） */
function isProductImageFile(file: CpTaskFileVO): boolean {
  return Boolean(
    productImage.value?.configured &&
      productImage.value.fileId != null &&
      String(file.fileId) === String(productImage.value.fileId)
  );
}

/** 图角色文案：产品图 / 参考图 / 生成图 / 上传图（后端 cp_task_file.source_type） */
function fileSourceLabel(type?: string): string {
  return (type && FILE_SOURCE_LABELS[type]) || type || '';
}

function fileSourceType(type?: string): TagType {
  return (type && FILE_SOURCE_TYPES[type]) || 'info';
}

/** 产品图来源说明：来自本项目 / 来自别的项目（如实标注，不让人以为是本项目传的） */
const productImageOrigin = computed(() => {
  const info = productImage.value;
  if (!info?.configured) return '';
  if (info.sourceTaskId == null) return '';
  return String(info.sourceTaskId) === String(currentProjectId.value)
    ? '产品图来自本项目'
    : `产品图来自其它项目（taskId=${info.sourceTaskId}）`;
});

const confirmedFacts = computed(() => facts.value.filter((f) => f.confirmStatus === 'CONFIRMED'));
const requiredFieldOptions = computed(() => fieldOptions.value.filter((o) => o.requiredByGate));
const unsatisfiedRequiredOptions = computed(() => requiredFieldOptions.value.filter((o) => !o.satisfied));

/**
 * 事实清单的筛选与排序（无框列表用）。
 *
 * <p>为什么加筛选：一个项目的事实行会到十几条，而真正要动手的只有待确认/冲突那几条；
 * 原先是"一整张表从头看到尾"。默认仍然是**全部**（不藏数据），只是把待确认/冲突排到前面。</p>
 */
type FactFilterValue = 'ALL' | 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CONFLICT';
const factFilter = ref<FactFilterValue>('ALL');

/** 展开的原文摘录：用普通对象而不是 Set，保证 Vue 能追到变化（改用 Map/Set 得整体换新对象） */
const expandedFacts = ref<Record<string, boolean>>({});

/** 各状态下的事实条数（筛选按钮上的数字就是它，避免"点进去才发现是空的"） */
const factFilters = computed(() => {
  const by = (status: string) => facts.value.filter((f) => (f.confirmStatus || 'PENDING') === status).length;
  return [
    { value: 'ALL' as FactFilterValue, label: '全部', count: facts.value.length },
    { value: 'PENDING' as FactFilterValue, label: '待确认', count: by('PENDING') },
    { value: 'CONFLICT' as FactFilterValue, label: '冲突', count: by('CONFLICT') },
    { value: 'CONFIRMED' as FactFilterValue, label: '已确认', count: by('CONFIRMED') },
    { value: 'REJECTED' as FactFilterValue, label: '已否决', count: by('REJECTED') }
  ].filter((item) => item.value === 'ALL' || item.count > 0);
});

/** 需要人动手的排前面；同组内保持后端返回顺序（不重排，避免"顺序莫名其妙变了"） */
const FACT_STATUS_WEIGHT: Record<string, number> = { CONFLICT: 0, PENDING: 1, CONFIRMED: 2, REJECTED: 3 };

const visibleFacts = computed(() => {
  const list = factFilter.value === 'ALL'
    ? facts.value.slice()
    : facts.value.filter((f) => (f.confirmStatus || 'PENDING') === factFilter.value);
  return list
    .map((row, index) => ({ row, index }))
    .toSorted((a, b) => {
      const wa = FACT_STATUS_WEIGHT[a.row.confirmStatus || 'PENDING'] ?? 9;
      const wb = FACT_STATUS_WEIGHT[b.row.confirmStatus || 'PENDING'] ?? 9;
      return wa - wb || a.index - b.index;
    })
    .map((item) => item.row);
});

function toggleFactExcerpt(row: CpFactSnapshotVO) {
  const key = String(row.snapshotId);
  expandedFacts.value = { ...expandedFacts.value, [key]: !expandedFacts.value[key] };
}

function urlOf(key: string): string {
  return objectUrls.value[key] || '';
}

function setUrl(key: string, url: string) {
  const old = objectUrls.value[key];
  if (old) URL.revokeObjectURL(old);
  objectUrls.value = { ...objectUrls.value, [key]: url };
}

function releaseUrl(key: string) {
  const old = objectUrls.value[key];
  if (!old) return;
  URL.revokeObjectURL(old);
  const next = { ...objectUrls.value };
  delete next[key];
  objectUrls.value = next;
}

function stageLabel(stage?: string): string {
  if (!stage) return '未开始';
  return CREATIVE_STAGE_LABELS[stage] || stage;
}

function stageType(stage?: string): string {
  return (stage && CREATIVE_STAGE_TYPES[stage]) || 'info';
}

function genStatusLabel(status?: string): string {
  return (status && GENERATION_STATUS_LABELS[status]) || status || '';
}

function genStatusType(status?: string): string {
  return (status && GENERATION_STATUS_TYPES[status]) || 'info';
}

function formatTime(value?: string): string {
  if (!value) return '';
  return value.replace('T', ' ').slice(0, 19);
}

async function loadProjects() {
  loadingProjects.value = true;
  // 深链优先：`?taskId=` 指到哪个项目就打开哪个。
  // 原先这一页完全不看这个参数——直接开 `/creative/project?taskId=X` 总是落到列表第一个项目，
  // 看起来就像"链接没生效/页面没变化"（R19 排查"为什么界面没变化"时量到的真问题）。
  const queryTaskId = new URLSearchParams(location.search).get('taskId') || '';
  if (queryTaskId && !currentProjectId.value) {
    currentProjectId.value = queryTaskId;
  }
  try {
    const res = await listCreativeProject({
      pageNum: 1,
      pageSize: 50,
      queryTaskName: queryTaskName.value || undefined
    });
    projects.value = res.data?.rows || [];
    if (!projects.value.length) {
      if (currentProjectId.value) {
        // 深链项目不在这一页列表里（超出 50 条 / 被筛选掉 / 已删除）：仍按 id 打开，让页面自己如实报错
        await selectProject({ taskId: currentProjectId.value } as CreativeProjectVO);
      } else {
        currentProject.value = null;
      }
      return;
    }
    const target = projects.value.find((p) => String(p.taskId) === String(currentProjectId.value));
    if (target) {
      if (String(currentProject.value?.taskId ?? '') !== String(target.taskId)) {
        await selectProject(target);
      }
    } else if (currentProjectId.value) {
      // 深链项目不在这一页列表里（超出 50 条 / 被筛选掉 / 已不存在）：仍按这个 id 打开，
      // 让详情接口如实报错——**不偷偷换成列表第一个项目**（那会让人以为链接没生效）
      await selectProject({ taskId: currentProjectId.value } as CreativeProjectVO);
    } else {
      await selectProject(projects.value[0]);
    }
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载视觉项目失败');
  } finally {
    loadingProjects.value = false;
  }
}

async function selectProject(project: CreativeProjectVO) {
  currentProjectId.value = project.taskId ?? '';
  currentProject.value = project;
  selectedFileId.value = '';
  facts.value = [];
  fieldOptions.value = [];
  fieldOptionsLoaded.value = false;
  factLoadError.value = '';
  // 换项目就换品牌要求与文案块：另一个项目的内容不能留在这一页上
  brandBrief.value = null;
  briefLoaded.value = false;
  // 关键：把「这份要求属于哪个项目」也清掉。否则上一个项目迟到的响应会把
  // briefLoaded 置成 true，新项目就再也不发请求（R7 浏览器验收复现的竞态）。
  briefTaskId.value = '';
  briefBusy.value = '';
  brandBriefError.value = '';
  // 换项目也要清掉"修改申请"的状态：那是上一个项目的待办，留着会张冠李戴
  briefChangeRequest.value = null;
  briefChangeError.value = '';
  copyBlocks.value = [];
  copyLoadError.value = '';
  copyBusy.value = '';
  stopPolling();
  await loadDetail();
}

async function loadDetail() {
  if (!currentProjectId.value) return;
  loadingDetail.value = true;
  try {
    // 事实/字段选项跟着项目详情一起取：失败时不让整页详情跟着失败，
    // 但也不能静默——置 factLoadError，页面上照实写出「没取到」。
    const [detail, fileRes, genRes, timelineRes, factRes, optionRes, productImageRes] = await Promise.all([
      getCreativeProject(currentProjectId.value),
      listCreativeFiles(currentProjectId.value),
      listGenerations(currentProjectId.value),
      listCreativeTimeline(currentProjectId.value),
      listFact(currentProjectId.value).catch(() => null),
      factFieldOptions(currentProjectId.value).catch(() => null),
      // 产品图信息失败不影响整页：下面按「未配置」展示，并给出补齐入口
      getProjectProductImage(currentProjectId.value).catch(() => null)
    ]);
    currentProject.value = detail.data;
    files.value = fileRes.data || [];
    generations.value = genRes.data || [];
    timeline.value = timelineRes.data || [];
    facts.value = factRes?.data || [];
    fieldOptions.value = optionRes?.data || [];
    fieldOptionsLoaded.value = optionRes != null;
    productImage.value = productImageRes?.data ?? null;
    // 后端在读取产品图信息时会把「产品图」补登记成该项目的一张附件（复用同一对象键）。
    // 那条登记可能发生在本次 files 请求之后，所以发现列表里还没有它时补取一次。
    if (
      productImage.value?.configured &&
      productImage.value.fileId != null &&
      !files.value.some((f) => String(f.fileId) === String(productImage.value?.fileId))
    ) {
      const refreshed = await listCreativeFiles(currentProjectId.value).catch(() => null);
      if (refreshed?.data) files.value = refreshed.data;
    }
    factLoadError.value =
      factRes == null || optionRes == null
        ? '该项目的事实数据没取到（事实清单或字段选项接口失败），下面显示的内容可能不完整'
        : '';
    if (!selectedFileId.value && imageFiles.value.length) {
      selectedFileId.value = imageFiles.value[0].fileId ?? '';
    }
    void loadFileThumbs();
    void loadGenerationThumbs();
    syncPolling();
    void loadDnaState();
    // 品牌要求与文案块跟着详情一起取：各自失败各自如实报，不影响整页
    void loadBrandBrief();
    void loadBriefChangeRequest();
    void loadCopyBlocks();
    void loadOutputSpec();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载项目详情失败');
  } finally {
    loadingDetail.value = false;
  }
}

/**
 * 取该交付类型的默认输出规格（R31）。
 *
 * <p>为什么放在页面而不是头部组件里：规格是**配置数据**（`dp_output_spec`），
 * 项目页已经有一整套"取配置/取数据"的加载流程；头部只负责显示。
 * 取不到不报错——头部会如实显示"未配置"，而不是编一个 750/800 出来。</p>
 */
async function loadOutputSpec() {
  const deliveryType = currentProject.value?.deliverableType;
  if (!deliveryType) {
    currentOutputSpec.value = null;
    return;
  }
  try {
    const res = await listOutputSpecs(deliveryType);
    currentOutputSpec.value = (res.data || [])[0] || null;
  } catch (error) {
    currentOutputSpec.value = null;
  }
}

// ------------------------------------------------------------------
// 事实确认（闸门必填项 / 事实清单 / 人工录入）
// ------------------------------------------------------------------

/** 重新取事实与字段选项（事实动作成功后调用，不重跑整页） */
async function loadFacts() {
  if (!currentProjectId.value) return;
  const [factRes, optionRes] = await Promise.all([
    listFact(currentProjectId.value).catch(() => null),
    factFieldOptions(currentProjectId.value).catch(() => null)
  ]);
  facts.value = factRes?.data || [];
  fieldOptions.value = optionRes?.data || [];
  fieldOptionsLoaded.value = optionRes != null;
  factLoadError.value =
    factRes == null || optionRes == null
      ? '该项目的事实数据没取到（事实清单或字段选项接口失败），下面显示的内容可能不完整'
      : '';
}

async function doConfirmFact(row: CpFactSnapshotVO) {
  if (row.snapshotId == null) return;
  factBusy.value = 'fact-' + row.snapshotId;
  try {
    await confirmFact(row.snapshotId);
    ElMessage.success('已确认该值');
    await loadFacts();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '确认失败');
  } finally {
    factBusy.value = '';
  }
}

async function doRejectFact(row: CpFactSnapshotVO) {
  if (row.snapshotId == null) return;
  try {
    await ElMessageBox.confirm('驳回后该候选值不会被采用，是否继续？', '驳回候选值', { type: 'warning' });
  } catch {
    return;
  }
  factBusy.value = 'fact-' + row.snapshotId;
  try {
    await rejectFact(row.snapshotId);
    ElMessage.success('已驳回该候选值');
    await loadFacts();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '驳回失败');
  } finally {
    factBusy.value = '';
  }
}

async function doConfirmUnambiguousFacts() {
  if (!currentProjectId.value) return;
  factBusy.value = 'confirmUnambiguous';
  try {
    const res = await confirmUnambiguousFacts(currentProjectId.value);
    ElMessage.success(`已确认 ${res.data ?? 0} 条无争议项`);
    await loadFacts();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '一键确认失败');
  } finally {
    factBusy.value = '';
  }
}

/** 打开人工录入对话框（可从「缺某项」的按钮带出字段编码） */
function openManualFact(fieldCode?: string) {
  if (fieldCode) manualForm.fieldCode = fieldCode;
  manualFactVisible.value = true;
}

async function doAddManualFact() {
  if (!currentProjectId.value || !manualForm.fieldCode || !manualForm.value) return;
  factBusy.value = 'manualFact';
  try {
    await addManualFact({
      taskId: currentProjectId.value,
      fieldCode: manualForm.fieldCode,
      value: manualForm.value,
      remark: manualForm.remark || undefined
    });
    ElMessage.success('已录入，状态为「待确认」——请在事实表里确认后才算数');
    manualForm.value = '';
    manualForm.remark = '';
    manualFactVisible.value = false;
    await loadFacts();
    flowToken.value += 1;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '录入失败');
  } finally {
    factBusy.value = '';
  }
}

function factStatusLabel(status?: string): string {
  if (status === 'PENDING') return '待确认';
  if (status === 'CONFIRMED') return '已确认';
  if (status === 'CONFLICT') return '冲突';
  if (status === 'REJECTED') return '已否决';
  return status || '—';
}

function factStatusType(status?: string): TagType {
  if (status === 'CONFIRMED') return 'success';
  if (status === 'CONFLICT') return 'danger';
  if (status === 'REJECTED') return 'info';
  return 'warning';
}

function optionLabel(option: CpFactFieldOptionVO): string {
  const parts = [`${option.fieldName || option.fieldCode}（${option.fieldCode}）`];
  if (option.gateLevel) parts.push(option.gateLevel);
  if (option.satisfied) parts.push('已确认');
  return parts.join(' · ');
}

/**
 * el-table 插槽行类型是 DefaultRow，数据其实是我们的 VO；
 * 在模板里显式收窄，而不是把函数参数放宽成 any。
 */
function asFact(row: unknown): CpFactSnapshotVO {
  return row as CpFactSnapshotVO;
}

// ------------------------------------------------------------------
// 品牌要求（Brief）：只读读取
// ------------------------------------------------------------------

/**
 * 读品牌要求（走内容域接口 `/content/task/{taskId}/brand-brief`）。
 *
 * <p>三个刻意的行为：</p>
 * <ol>
 *   <li>读失败不静默：置 brandBriefError，页面照实说「没取到」，空值不代表品牌部没有提要求；</li>
 *   <li>已经取到过就不再自动重取（{@code force=false}）：跟随详情刷新时不重复打接口，
 *       只有「刷新」按钮才会强制重取；</li>
 *   <li>按项目判断"取到过"（{@code briefTaskId}），并丢弃迟到的响应——只认 briefLoaded
 *       会踩竞态：上一个项目迟到的响应把它置成 true，新项目一个请求都不发（R7 验收复现过）。</li>
 * </ol>
 */
async function loadBrandBrief(force = false) {
  const taskId = String(currentProjectId.value || '');
  if (!taskId) return;
  if (!force && briefLoaded.value && briefTaskId.value === taskId) return;
  briefBusy.value = 'load';
  brandBriefError.value = '';
  try {
    const res = await getBrandBrief(taskId);
    // 迟到的响应不能写进页面：期间人可能已经切到别的项目了
    if (String(currentProjectId.value || '') !== taskId) return;
    brandBrief.value = res.data ?? null;
    briefTaskId.value = taskId;
    briefLoaded.value = true;
  } catch (error) {
    if (String(currentProjectId.value || '') !== taskId) return;
    brandBrief.value = null;
    briefLoaded.value = false;
    briefTaskId.value = '';
    brandBriefError.value =
      '品牌要求没取到（' + ((await extractErrorMessage(error)) ?? '接口失败') + '），上面的空值不代表品牌部没有提要求';
  } finally {
    if (String(currentProjectId.value || '') === taskId) briefBusy.value = '';
  }
}

// ------------------------------------------------------------------
// 文案与要点：读 / 增 / 改 / 删 / 排序 / 从事实派生
// ------------------------------------------------------------------

/**
 * 读全部文案块（不按类型过滤：一次取回，分组在页面本地做，切 Tab 不再打请求）。
 *
 * <p>读失败不静默：置 copyLoadError，页面照实说「没取到」。</p>
 */
async function loadCopyBlocks() {
  if (!currentProjectId.value) return;
  if (copyDialogVisible.value) return;
  copyBusy.value = 'load';
  copyLoadError.value = '';
  try {
    const res = await listCopyBlocks(currentProjectId.value);
    copyBlocks.value = res.data || [];
  } catch (error) {
    copyBlocks.value = [];
    copyLoadError.value =
      '文案与要点没取到（' + ((await extractErrorMessage(error)) ?? '接口失败') + '），空表不代表项目里没有内容';
  } finally {
    copyBusy.value = '';
  }
}

/** 某一类文案块，按 sortNo 升序（sortNo 相同退回按ID，保证顺序稳定） */
function blocksOf(blockType: string): CopyBlockVO[] {
  // 用 toSorted 而不是 sort：filter 出来的虽是新数组，但就地排序的写法在读代码时无法一眼看出
  // 「没有改动共享状态」，且前端 lint 明确禁止就地排序（unicorn/no-array-sort）。
  return copyBlocks.value
    .filter((block) => (block.blockType || '') === blockType)
    .toSorted((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0) || String(a.id).localeCompare(String(b.id)));
}

/** 当前 tab 的文案块（模板里 v-for 用；避免在同一个渲染里反复 filter 同一份数据） */
const activeCopyBlocks = computed(() => blocksOf(copyTab.value));

/** el-table 插槽行类型是 DefaultRow，数据其实是我们的 VO；在模板里显式收窄，而不是把参数放宽成 any */
function asBlock(row: unknown): CopyBlockVO {
  return row as CopyBlockVO;
}

function copySourceLabel(source?: string): string {
  return (source && COPY_BLOCK_SOURCE_LABELS[source]) || source || '';
}

function copySourceType(source?: string): TagType {
  return (source && COPY_BLOCK_SOURCE_TYPES[source]) || 'info';
}

function copyStatusLabel(status?: string): string {
  return (status && COPY_BLOCK_STATUS_LABELS[status]) || status || '—';
}

function copyStatusType(status?: string): TagType {
  return (status && COPY_BLOCK_STATUS_TYPES[status]) || 'warning';
}

function isFirstBlock(blockType: string, block: CopyBlockVO): boolean {
  const list = blocksOf(blockType);
  return list.length === 0 || String(list[0].id) === String(block.id);
}

function isLastBlock(blockType: string, block: CopyBlockVO): boolean {
  const list = blocksOf(blockType);
  return list.length === 0 || String(list[list.length - 1].id) === String(block.id);
}

/**
 * 上移 / 下移一条（只对卖点开放）。
 *
 * <p>为什么用按钮而不是拖拽：拖拽要额外依赖且容易在窄屏上误操作；按钮调序后立刻调 reorder 持久化，
 * 顺序就是详情页从上到下的顺序。提交的是该类型的<b>完整</b>顺序，后端据此重写 sort_no。</p>
 */
async function moveBlock(block: CopyBlockVO, delta: number) {
  if (!currentProjectId.value || copyBusy.value) return;
  const blockType = block.blockType || '';
  const list = blocksOf(blockType);
  const index = list.findIndex((item) => String(item.id) === String(block.id));
  const target = index + delta;
  if (index < 0 || target < 0 || target >= list.length) return;
  const ids: Array<string | number> = list.map((item) => item.id ?? '');
  const [moved] = ids.splice(index, 1);
  ids.splice(target, 0, moved);
  copyBusy.value = 'reorder';
  try {
    await reorderCopyBlocks(currentProjectId.value, blockType, ids);
    // 重新读一次，页面上显示的顺序以后端落库的 sort_no 为准（不做本地猜测）
    const res = await listCopyBlocks(currentProjectId.value);
    copyBlocks.value = res.data || [];
    ElMessage.success('顺序已保存');
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '调整顺序失败');
  } finally {
    copyBusy.value = '';
  }
}

/** 打开新增/编辑弹窗（传 block 即编辑） */
function openCopyBlockDialog(blockType: string, block?: CopyBlockVO) {
  copyForm.blockType = block?.blockType || blockType;
  copyForm.title = block?.title || '';
  copyForm.content = block?.content || '';
  copyForm.remark = block?.remark || '';
  copyEditingId.value = block?.id ?? null;
  copyEditingSource.value = block?.source || '';
  copyDialogVisible.value = true;
}

async function doSaveCopyBlock() {
  if (!currentProjectId.value || copyBusy.value) return;
  const title = copyForm.title.trim();
  const content = copyForm.content.trim();
  if (!title && !content) {
    ElMessage.warning('标题和内容至少填一项');
    return;
  }
  copyBusy.value = 'saveBlock';
  try {
    if (copyEditingId.value == null) {
      await addCopyBlock(currentProjectId.value, {
        blockType: copyForm.blockType,
        title,
        content,
        remark: copyForm.remark.trim() || undefined
      });
      ElMessage.success('已新增');
    } else {
      await updateCopyBlock(currentProjectId.value, copyEditingId.value, {
        blockType: copyForm.blockType,
        title,
        content,
        remark: copyForm.remark.trim() || undefined
      });
      ElMessage.success('已保存');
    }
    copyDialogVisible.value = false;
    copyTab.value = copyForm.blockType;
    await loadCopyBlocks();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '保存失败');
  } finally {
    copyBusy.value = '';
  }
}

/**
 * 清理项目素材（V0.2 R25）：**不可恢复**，所以两道确认。
 *
 * 第一道把代价摊开给用户看（多少附件、多少生成记录、多大体积），
 * 第二道要求逐字输入项目名——服务端还会再校验一次名字，前端这道只是别让人手滑点过去。
 * 清理完成后刷新详情：附件区会立刻变空，让人当场看到"确实删掉了什么"。
 */
async function doPurgeMaterials() {
  if (!currentProjectId.value || purgeBusy.value) return;
  let summary: ProjectMaterialsVO;
  try {
    const res = await getProjectMaterials(currentProjectId.value);
    summary = res.data || {};
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取素材概况失败');
    return;
  }
  const mb = ((summary.fileBytes || 0) / 1024 / 1024).toFixed(2);
  try {
    await ElMessageBox.confirm(
      `将永久删除：**${summary.fileCount || 0} 个附件**（约 ${mb} MB）、` +
        `**${summary.generationCount || 0} 条生成记录**，以及对象存储里的对应文件。` +
        `\n\n会保留：分镜、文案、模块计划、视觉基因与操作日志——清理后仍能看到当时怎么做的。` +
        `\n\n不可恢复。`,
      '清理素材（不可恢复）',
      { type: 'warning', dangerouslyUseHTMLString: false, confirmButtonText: '继续', cancelButtonText: '取消' }
    );
    await ElMessageBox.prompt(
      `请输入项目名以确认：${summary.taskName || ''}`,
      '二次确认',
      { type: 'warning', confirmButtonText: '清理', cancelButtonText: '取消', inputPlaceholder: '逐字输入项目名' }
    );
  } catch {
    return;
  }
  purgeBusy.value = true;
  try {
    const res = await purgeProjectMaterials(currentProjectId.value, summary.taskName || '', true);
    const r = res.data || {};
    ElMessage.success(
      `已清理 ${r.purgedObjects || 0} 个对象 / ${r.purgedFiles || 0} 条附件 / ` +
        `${r.purgedGenerations || 0} 条生成记录，释放约 ${Math.round((r.purgedBytes || 0) / 1024)} KB`
    );
    await loadDetail();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '清理失败');
  } finally {
    purgeBusy.value = false;
  }
}

/** 打开"已删项目素材清单"（R26） */
async function openDeletedMaterials() {
  deletedMaterialsVisible.value = true;
  deletedMaterialsLoading.value = true;
  deletedSelection.value = [];
  try {
    const res = await listDeletedProjectMaterials();
    deletedMaterials.value = res.data || [];
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取已删项目素材失败');
    deletedMaterials.value = [];
  } finally {
    deletedMaterialsLoading.value = false;
  }
}

function onDeletedSelectionChange(rows: ProjectMaterialsVO[]) {
  deletedSelection.value = rows;
}

function mb(bytes?: number) {
  return ((bytes || 0) / 1024 / 1024).toFixed(2);
}

/** 批量清理已删项目素材（R26）：口令逐字输入，服务端还会再校验一次"只处理已删项目" */
async function doPurgeDeleted() {
  const ids = deletedSelection.value.map((r) => r.taskId as string | number);
  if (!ids.length) return;
  try {
    await ElMessageBox.prompt(
      `将清理 ${ids.length} 个已删项目的素材（合计约 ${mb(deletedSelectionBytes.value)} MB），不可恢复。` +
        `请输入「清理素材」确认。`,
      '批量清理（不可恢复）',
      { type: 'warning', confirmButtonText: '清理', cancelButtonText: '取消', inputPlaceholder: '清理素材' }
    );
  } catch {
    return;
  }
  purgeBusy.value = true;
  try {
    const res = await purgeDeletedProjectMaterials('清理素材', ids);
    const r = (res.data || {}) as Record<string, number | string[]>;
    ElMessage.success(
      `已清理 ${r.projects || 0} 个项目：${r.objects || 0} 个对象 / ${r.files || 0} 条附件 / ` +
        `${r.generations || 0} 条生成记录，释放约 ${Math.round(Number(r.bytes || 0) / 1024)} KB`
    );
    const skipped = (r.skipped as string[]) || [];
    if (skipped.length) ElMessage.warning('有项目被跳过：' + skipped.join('；'));
    await openDeletedMaterials();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '批量清理失败');
  } finally {
    purgeBusy.value = false;
  }
}

async function doDeleteCopyBlock(block: CopyBlockVO) {
  if (!currentProjectId.value || block.id == null) return;
  try {
    await ElMessageBox.confirm(
      `删除「${block.title || block.content || '这一条'}」？删除后详情页排版不会再包含它。`,
      '删除文案块',
      { type: 'warning' }
    );
  } catch {
    return;
  }
  copyBusy.value = 'block-' + String(block.id);
  try {
    await deleteCopyBlock(currentProjectId.value, block.id);
    ElMessage.success('已删除');
    await loadCopyBlocks();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '删除失败');
  } finally {
    copyBusy.value = '';
  }
}

/** 从已确认事实派生参数行（幂等：已存在的不重复新增） */
async function doSeedFromFacts() {
  if (!currentProjectId.value || copyBusy.value) return;
  copyBusy.value = 'seed';
  try {
    const res = await seedCopyBlocksFromFacts(currentProjectId.value);
    const added = res.data ?? 0;
    ElMessage.success(
      added > 0
        ? `已从已确认事实派生 ${added} 条参数行`
        : '没有新增：可派生的参数行都已存在（派生是幂等的）'
    );
    await loadCopyBlocks();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '从事实派生失败');
  } finally {
    copyBusy.value = '';
  }
}

/** 看这个项目有没有锁定基因，并决定是否预填提示词（只在用户没写过提示词时预填） */
async function loadDnaState() {
  promptFromDna.value = false;
  dnaLockedVersion.value = '';
  dnaStateLoaded.value = false;
  try {
    // 口径必须与后端一致：出图用的是「已锁定版本」，不是「最新版本」。
    // 锁定 v1 之后又改出 v2 时，最新版是待确认的 v2，但实际出图依据仍是 v1。
    const [latest, versionRes] = await Promise.all([
      getDna(currentProjectId.value),
      listDnaVersions(currentProjectId.value)
    ]);
    const lockedVersion = (versionRes.data || []).find((item) => item.locked);
    dnaLocked.value = Boolean(lockedVersion);
    if (lockedVersion) {
      dnaLockedVersion.value = `v${lockedVersion.version}`;
    } else if (latest.data) {
      dnaLockedVersion.value = '';
    }
    if (dnaLocked.value && !heroForm.prompt.trim()) {
      await prefillPromptFromDna();
    }
  } catch {
    dnaLocked.value = false;
  } finally {
    dnaStateLoaded.value = true;
  }
}

/** 用视觉基因派生提示词预填出图框 */
async function prefillPromptFromDna() {
  try {
    const res = await getDnaPrompt(currentProjectId.value, 'HERO 主图');
    heroForm.prompt = res.data?.prompt || '';
    heroForm.negativePrompt = res.data?.negativePrompt || '';
    promptApplied.value = res.data?.applied || [];
    promptFromDna.value = true;
    ElMessage.success('已按视觉基因预填提示词');
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '派生提示词失败');
  }
}

/** 跳到视觉基因页（带上当前项目） */
function openDna() {
  if (!currentProjectId.value) return;
  window.open(`/creative/dna?taskId=${currentProjectId.value}`, '_self');
}

async function loadFileThumbs() {
  for (const file of imageFiles.value) {
    const key = 'file-' + file.fileId;
    if (objectUrls.value[key]) continue;
    try {
      const url = await fetchCreativeFileBlobUrl(currentProjectId.value, file.fileId as string | number);
      setUrl(key, url);
    } catch {
      /* 单张读失败不影响其它 */
    }
  }
}

async function loadGenerationThumbs() {
  for (const gen of generations.value) {
    const key = 'gen-' + gen.id;
    if (!gen.previewable) {
      releaseUrl(key);
      continue;
    }
    if (objectUrls.value[key]) continue;
    try {
      const url = await fetchGenerationThumbnailBlobUrl(gen.id);
      setUrl(key, url);
    } catch {
      /* 缩略图失败就留空，点击预览仍可取原图 */
    }
  }
}

async function doUpload(options: UploadRequestOptions) {
  // 这里两个早退都必须给话：静默 return 会让人以为「点了没反应」（实际踩过）
  if (!currentProjectId.value) {
    ElMessage.warning('还没选中项目，请先在左侧选择视觉项目');
    return;
  }
  if (!options.file) {
    ElMessage.warning('没有选中图片，请重新选择');
    return;
  }
  const alsoProductImage = asProductImage.value;
  try {
    // 上传参考图本身只是「追加一张输入图」，不会改项目阶段（页面提示按此如实写）；
    // 勾选时后端会在同一次请求里上传 + 回写产品主数据（先在服务端校验可行性再落对象存储）
    await uploadCreativeReference(currentProjectId.value, options.file as File, alsoProductImage);
    ElMessage.success(alsoProductImage ? '已上传，并登记为该产品的产品图' : '参考图已上传');
    asProductImage.value = false;
    await loadDetail();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '上传失败');
  }
}

/**
 * 把某个附件登记为该产品的产品图（写回 cp_product.product_image）。
 *
 * <p>后端会硬校验：附件必须是图片、且其所属任务的产品就是本项目的产品；不满足直接给可读原因。
 * 登记成功后，这张图成为「产品保真基准」，页面上的角色徽标也会随之变成「产品图」。</p>
 */
async function doBindProductImage(file: CpTaskFileVO) {
  if (!currentProjectId.value || file.fileId == null) return;
  bindingProductImage.value = String(file.fileId);
  try {
    const res = await bindProjectProductImage(currentProjectId.value, file.fileId);
    productImage.value = res.data ?? null;
    ElMessage.success(`已把「${file.fileName}」设为该产品的产品图`);
    flowToken.value += 1;
    await loadDetail();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '设为产品图失败');
  } finally {
    bindingProductImage.value = '';
  }
}

async function doGenerate() {
  if (!currentProjectId.value) return;
  submitting.value = true;
  try {
    await submitHero(currentProjectId.value, {
      fileId: selectedFileId.value || undefined,
      prompt: heroForm.prompt || undefined,
      negativePrompt: heroForm.negativePrompt || undefined,
      workflowCode: heroForm.workflowCode || undefined
    });
    ElMessage.success('已提交出图，正在跟踪状态');
    await loadDetail();
    startPolling();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '提交出图失败');
  } finally {
    submitting.value = false;
  }
}

async function doRetry(gen: DpGenerationVO) {
  retryingId.value = String(gen.id);
  try {
    await retryGeneration(gen.id);
    ElMessage.success('已新建一次候选');
    await loadDetail();
    startPolling();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '重试失败');
  } finally {
    retryingId.value = '';
  }
}

async function openPreview(gen: DpGenerationVO) {
  previewVisible.value = true;
  try {
    const url = await fetchGenerationPreviewBlobUrl(gen.id);
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = url;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取产出图失败');
  }
}

function closePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = '';
}

function openCreateDialog() {
  createForm.taskName = '';
  createForm.productId = '';
  createForm.remark = '';
  createVisible.value = true;
  if (!products.value.length) {
    void loadProducts();
  }
}

async function loadProducts() {
  try {
    const res = await productOptions();
    products.value = res.data || [];
  } catch {
    /* 产品下拉失败不阻断建项目（产品是可选项） */
  }
}

async function doCreate() {
  if (!createForm.taskName.trim()) {
    ElMessage.warning('请填写项目名称');
    return;
  }
  creating.value = true;
  try {
    const res = await addCreativeProject({
      taskName: createForm.taskName.trim(),
      productId: createForm.productId || undefined,
      remark: createForm.remark || undefined
    });
    ElMessage.success('项目已创建');
    createVisible.value = false;
    await loadProjects();
    const created = projects.value.find((p) => String(p.taskId) === String(res.data));
    if (created) await selectProject(created);
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '创建项目失败');
  } finally {
    creating.value = false;
  }
}

/** 有候选在跑就轮询，跑完自动停（不做无脑定时器） */
function syncPolling() {
  const hasRunning = generations.value.some((g) => g.status === 'QUEUED' || g.status === 'RUNNING');
  if (hasRunning) {
    startPolling();
  } else {
    stopPolling();
  }
}

function startPolling() {
  if (pollTimer !== undefined) return;
  pollTicks = 0;
  polling.value = true;
  pollTimer = window.setInterval(async () => {
    pollTicks += 1;
    if (pollTicks > POLL_MAX_TICKS) {
      stopPolling();
      return;
    }
    try {
      const res = await listGenerations(currentProjectId.value);
      generations.value = res.data || [];
      void loadGenerationThumbs();
      const hasRunning = generations.value.some((g) => g.status === 'QUEUED' || g.status === 'RUNNING');
      if (!hasRunning) {
        stopPolling();
        void loadDetail();
      }
    } catch {
      stopPolling();
    }
  }, POLL_INTERVAL_MS);
}

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer);
    pollTimer = undefined;
  }
  polling.value = false;
}

/** 若依的错误体可能是 blob/字符串，统一取出可读信息 */
async function extractErrorMessage(error: unknown): Promise<string | undefined> {
  const anyError = error as { message?: string; response?: { data?: unknown } };
  const data = anyError?.response?.data;
  if (data instanceof Blob) {
    try {
      const text = await data.text();
      const parsed = JSON.parse(text) as { msg?: string; message?: string };
      return parsed.msg || parsed.message || text;
    } catch {
      return anyError?.message;
    }
  }
  if (data && typeof data === 'object') {
    const parsed = data as { msg?: string; message?: string };
    return parsed.msg || parsed.message || anyError?.message;
  }
  return anyError?.message;
}

onMounted(async () => {
  await loadProjects();
  try {
    const res = await listCreativeWorkflows();
    workflows.value = res.data || [];
    const published = workflows.value.find((w) => w.published && w.capabilityCode === 'I2I');
    if (published) heroForm.workflowCode = published.workflowCode;
  } catch {
    /* 工作流列表失败时，后端仍会用默认已发布契约 */
  }
});

onBeforeUnmount(() => {
  stopPolling();
  Object.values(objectUrls.value).forEach((url) => URL.revokeObjectURL(url));
  objectUrls.value = {};
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
});
</script>

<style scoped lang="scss">
@use '@/assets/styles/tokens-studio.scss';

.studio {
  min-height: calc(100vh - 135px);
  padding: 24px;
  overflow: hidden;
  color: var(--t1);
  background: var(--bg);
  background-image: radial-gradient(900px 460px at 84% -10%, rgba(148, 163, 184, 0.16), transparent 68%);
  border: 1px solid var(--line);
  border-radius: 8px;
}

button {
  font: inherit;
}

.workbench {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
}
.panel-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.ghost-btn {
  padding: 4px 10px;
  color: var(--t2);
  font-size: 12px;
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--line2);
  border-radius: 4px;
}
.ghost-btn:hover {
  color: var(--t1);
}

.filter-row {
  display: flex;
  gap: 8px;
  padding: 0 16px 10px;
}

.create-btn {
  width: calc(100% - 32px);
  min-height: 36px;
  margin: 0 16px 12px;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border: 0;
  border-radius: 6px;
}
.create-btn:hover {
  filter: brightness(1.08);
}

.project-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 340px);
  padding: 0 12px 14px;
  overflow-y: auto;
}

.project-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 11px 12px;
  text-align: left;
  cursor: pointer;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.project-item:hover {
  border-color: var(--line2);
}
.project-item.active {
  border-color: #7c3aed;
  box-shadow: inset 0 0 0 1px rgba(124, 58, 237, 0.4);
}
.project-name {
  font-size: 14px;
  font-weight: 600;
}
.project-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--t3);
}
.project-sub {
  font-size: 12px;
  color: var(--t2);
}
.project-warn {
  font-size: 12px;
  color: #fbbf24;
}
.task-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.stage-tag {
  padding: 2px 7px;
  font-size: 12px;
  border-radius: 10px;
  border: 1px solid transparent;
}
.stage-tag.is-primary {
  color: #c7d2fe;
  background: rgba(99, 102, 241, 0.16);
  border-color: rgba(99, 102, 241, 0.4);
}
.stage-tag.is-warning {
  color: #fde68a;
  background: rgba(245, 158, 11, 0.16);
  border-color: rgba(245, 158, 11, 0.4);
}
.stage-tag.is-success {
  color: #a7f3d0;
  background: rgba(16, 185, 129, 0.16);
  border-color: rgba(16, 185, 129, 0.4);
}
.stage-tag.is-info {
  color: var(--t2);
  background: rgba(148, 163, 184, 0.14);
  border-color: var(--line2);
}

.detail-panel {
  min-height: 420px;
  padding-bottom: 8px;
}

.placeholder {
  padding: 60px 24px;
  color: var(--t2);
  text-align: center;
}

.detail-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--line);
}
.detail-title h2 {
  margin: 0 0 8px;
  font-size: 17px;
}
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  font-size: 12px;
}
.detail-actions {
  display: flex;
  gap: 8px;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 16px;
}

.block {
  padding: 14px;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.block-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.block-head h4 {
  margin: 0;
  font-size: 14px;
}

.ref-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.ref-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 132px;
  padding: 6px;
  cursor: pointer;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.ref-card.active {
  border-color: #7c3aed;
}
.ref-card img {
  width: 100%;
  height: 96px;
  object-fit: contain;
  background: #05070a;
  border-radius: 4px;
}
.ref-loading {
  display: grid;
  place-items: center;
  height: 96px;
  font-size: 12px;
  color: var(--t3);
}
.ref-name {
  overflow: hidden;
  font-size: 11px;
  color: var(--t2);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ref-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 1px 6px;
  font-size: 10px;
  color: #fff;
  background: rgba(124, 58, 237, 0.9);
  border-radius: 8px;
}
/* 产品图徽标与「当前参考图」区分开：它标的是产品保真基准，不是本次参考图 */
.ref-badge.ok {
  background: rgba(16, 185, 129, 0.92);
}
.ref-upload-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
}
.as-product-image {
  max-width: 132px;
  height: auto;
  white-space: normal;
  font-size: 12px;
  line-height: 1.4;
}

.upload-slot {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  width: 132px;
  height: 138px;
  color: var(--t2);
  font-size: 12px;
  background: var(--sunken);
  border: 1px dashed var(--line2);
  border-radius: 6px;
}
.upload-slot .plus {
  font-size: 20px;
}
.upload-slot:hover {
  color: var(--t1);
  border-color: #7c3aed;
}

.form-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}
.form-row > label {
  flex: 0 0 76px;
  padding-top: 8px;
  font-size: 13px;
  color: var(--t2);
}
.form-row > :deep(.el-textarea),
.form-row > :deep(.el-select) {
  flex: 1;
}

.submit-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.candidate-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}
.candidate-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.candidate-cover {
  display: grid;
  place-items: center;
  height: 170px;
  overflow: hidden;
  cursor: pointer;
  background: #05070a;
  border-radius: 4px;
}
.candidate-cover img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.cover-placeholder {
  font-size: 12px;
  color: var(--t3);
}
.candidate-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}
.gen-status {
  padding: 1px 7px;
  border-radius: 8px;
}
.gen-status.is-primary {
  color: #c7d2fe;
  background: rgba(99, 102, 241, 0.18);
}
.gen-status.is-success {
  color: #a7f3d0;
  background: rgba(16, 185, 129, 0.18);
}
.gen-status.is-danger {
  color: #fecaca;
  background: rgba(239, 68, 68, 0.18);
}
.gen-status.is-warning {
  color: #fde68a;
  background: rgba(245, 158, 11, 0.18);
}
.gen-status.is-info {
  color: var(--t2);
  background: rgba(148, 163, 184, 0.16);
}
.gen-error {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin: 0;
  overflow: hidden;
  font-size: 12px;
  color: #fca5a5;
}
.candidate-actions {
  display: flex;
  gap: 4px;
}

.preview-wrap {
  display: grid;
  place-items: center;
  min-height: 200px;
}
.preview-wrap img {
  max-width: 100%;
  max-height: 62vh;
  border-radius: 6px;
}

.tl-title {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 13px;
}
.tl-stage {
  font-size: 12px;
  color: var(--t2);
}
.tl-detail {
  margin-top: 4px;
  font-size: 12px;
  color: var(--t3);
  word-break: break-all;
}

.muted {
  color: var(--t2);
}
.hint {
  font-size: 12px;
  color: var(--t3);
}

/* 事实确认 */
.block-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

/* 闸门必填项：紧凑状态带（chip 一行行排，代替原来的竖排清单） */
.fact-gate {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
  margin: 2px 0 12px;
}
.gate-chip {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 3px 9px;
  font-size: 12.5px;
  color: var(--t2);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--line);
  border-radius: 999px;
}
.gate-chip .mark {
  color: #f87171;
  font-weight: 700;
}
.gate-chip.ok .mark {
  color: #34d399;
}
.gate-chip .gate-level {
  padding-left: 6px;
  font-size: 11px;
  font-style: normal;
  color: var(--t3);
  border-left: 1px solid var(--line2);
}

/* 事实清单：无框行列表。左侧 2px 状态色条代替整块白底标签，
   行与行之间只有发丝分隔线，整块没有容器边框——这样在暗色工作室里不再是一块亮面。 */
.fact-list {
  display: flex;
  flex-direction: column;
}
.fact-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--line);
}
.fact-filter {
  padding: 3px 10px;
  font: inherit;
  font-size: 12.5px;
  color: var(--t2);
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 999px;
}
.fact-filter:hover {
  color: var(--t1);
  background: rgba(255, 255, 255, 0.04);
}
.fact-filter.active {
  color: var(--t1);
  background: rgba(148, 163, 184, 0.14);
  border-color: var(--line2);
}
.fact-filter .count {
  margin-left: 5px;
  font-size: 11.5px;
  color: var(--t3);
}
.fact-filter.active .count {
  color: var(--t2);
}
.fact-sort-note {
  margin-left: auto;
  font-size: 12px;
}
.fact-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 9px 4px 9px 10px;
  border-bottom: 1px solid var(--line);
  border-left: 2px solid transparent;
  transition: background 0.15s ease;
}
.fact-row:hover {
  background: rgba(255, 255, 255, 0.035);
}
.fact-row.st-pending {
  border-left-color: #f59e0b;
}
.fact-row.st-conflict {
  border-left-color: #a855f7;
}
.fact-row.st-confirmed {
  border-left-color: #10b981;
}
.fact-row.st-rejected {
  border-left-color: #ef4444;
}
.fact-main {
  flex: 1;
  min-width: 0;
}
.fact-line1 {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 10px;
  align-items: baseline;
}
.fact-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--t1);
}
.fact-value {
  font-size: 13px;
  color: var(--t1);
  word-break: break-all;
}
.fact-status {
  padding: 1px 7px;
  font-size: 11.5px;
  border-radius: 999px;
}
.fact-status.is-success {
  color: #a7f3d0;
  background: rgba(16, 185, 129, 0.16);
}
.fact-status.is-warning {
  color: #fde68a;
  background: rgba(245, 158, 11, 0.16);
}
.fact-status.is-danger {
  color: #fecaca;
  background: rgba(239, 68, 68, 0.16);
}
.fact-status.is-info {
  color: var(--t2);
  background: rgba(148, 163, 184, 0.14);
}
.fact-line2 {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 10px;
  margin-top: 3px;
  font-size: 12px;
  color: var(--t3);
}
.fact-excerpt {
  display: -webkit-box;
  max-width: 100%;
  overflow: hidden;
  color: var(--t3);
  cursor: pointer;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}
.fact-excerpt:hover {
  color: var(--t2);
}
.fact-excerpt.open {
  color: var(--t2);
  -webkit-line-clamp: unset;
}
.fact-ops {
  display: flex;
  flex: 0 0 auto;
  gap: 2px;
  align-items: center;
}
.fact-error {
  margin: 0 0 10px;
  font-size: 12.5px;
  line-height: 1.8;
  color: #fca5a5;
}

/* 文案与要点：与事实清单同一套无框行列表 */
.copy-list {
  display: flex;
  flex-direction: column;
}
.copy-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 9px 4px 9px 10px;
  border-bottom: 1px solid var(--line);
  border-left: 2px solid transparent;
  transition: background 0.15s ease;
}
.copy-row:hover {
  background: rgba(255, 255, 255, 0.035);
  border-left-color: var(--line2);
}
.copy-order {
  display: flex;
  flex: 0 0 auto;
  gap: 2px;
  align-items: center;
  width: 76px;
  padding-top: 1px;
}
.copy-order .ord {
  min-width: 16px;
  font-size: 12px;
  color: var(--t3);
  text-align: right;
}
.ord-btn {
  width: 20px;
  height: 20px;
  font-size: 12px;
  line-height: 1;
  color: var(--t2);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 4px;
}
.ord-btn:hover:not(:disabled) {
  color: var(--t1);
  background: rgba(255, 255, 255, 0.07);
}
.ord-btn:disabled {
  color: #4b5563;
  cursor: not-allowed;
}
.copy-body {
  flex: 1;
  min-width: 0;
}
.copy-line1 {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 10px;
  align-items: baseline;
}
.copy-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--t1);
}
.copy-src {
  padding: 1px 7px;
  font-size: 11.5px;
  color: var(--t2);
  background: rgba(148, 163, 184, 0.14);
  border-radius: 999px;
}
.copy-src.is-success {
  color: #a7f3d0;
  background: rgba(16, 185, 129, 0.16);
}
.copy-src.is-warning {
  color: #fde68a;
  background: rgba(245, 158, 11, 0.16);
}
.copy-src.is-primary {
  color: #c7d2fe;
  background: rgba(99, 102, 241, 0.18);
}
.copy-text {
  display: -webkit-box;
  margin: 3px 0 0;
  overflow: hidden;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--t2);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.copy-ops {
  display: flex;
  flex: 0 0 auto;
  gap: 2px;
  align-items: center;
}

/* 品牌要求（Brief）：**只读**展示——一行一个要求，左侧标签固定宽，右侧是值 */
.brief-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.brief-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.brief-row > label {
  flex: 0 0 112px;
  padding-top: 8px;
  font-size: 13px;
  color: var(--t2);
}
.brief-control {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
/*
 * 只读值：用普通文本而不是输入框——输入框在这个页面会暗示"可以改"，
 * 而这份要求由品牌部在内容任务里录入并确认，视觉工厂这一侧只按它创作。
 * white-space: pre-line 保留多行（必显信息/禁用词都是一行一条）。
 */
.brief-value {
  font-size: 13px;
  line-height: 1.8;
  color: var(--t1);
  white-space: pre-line;
  word-break: break-word;
}
.brief-value.is-empty {
  color: var(--t3);
}

/* 参考风格图片（只读）：缩略图条 + 一句"它们也是任务附件"的说明 */
.brief-style-images {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 2px;
}

/* 「申请修改品牌要求」的待处理提示：一行醒目的琥珀色，别和普通说明混在一起 */
.brief-change-pending {
  margin: 4px 0 8px;
  padding: 7px 10px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #fde68a;
  background: rgba(245, 158, 11, 0.12);
  border-left: 2px solid #f59e0b;
  border-radius: 4px;
}

/* 文案与要点：分组工具条 */
.copy-toolbar {
  margin-bottom: 10px;
}
.copy-usedat {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.7;
  color: #a5b4fc;
}
.studio :deep(.el-tabs__item) {
  color: var(--t2);
}
.studio :deep(.el-tabs__item.is-active) {
  color: var(--t1);
}
.studio :deep(.el-tabs__nav-wrap::after) {
  background-color: var(--line);
}

.dna-hint {
  margin: 0 0 10px;
  padding-left: 88px;
  font-size: 12px;
  line-height: 1.8;
  color: #a5b4fc;
}
.dna-hint.muted {
  color: var(--t3);
}
.empty {
  padding: 14px 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--t2);
}

.studio :deep(.el-input__wrapper),
.studio :deep(.el-textarea__inner),
.studio :deep(.el-select__wrapper) {
  background: var(--sunken);
  box-shadow: 0 0 0 1px var(--line) inset;
}
.studio :deep(.el-input__inner),
.studio :deep(.el-textarea__inner) {
  color: var(--t1);
}
.studio :deep(.el-input__count) {
  background: transparent;
}
</style>
