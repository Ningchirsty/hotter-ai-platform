<template>
  <div class="studio">
    <CreativeFlowGuide :task-id="currentProjectId" :refresh-token="flowToken" />

    <div v-if="showGuide" class="guide-bar">
      <span>
        R0 接线版：项目复用「内容生产协同」的电商详情页任务，出图复用图像创作内核（已发布工作流）。
        本轮只做「上传参考图（可同时登记为产品图）→ 出一张 HERO 主图 → 可预览 → 全程可追溯」这条闭环，视觉基因/分镜/视觉门/排版在 R1–R3 交付。
      </span>
      <button type="button" title="关闭提示" @click="dismissGuide">✕</button>
    </div>

    <div class="workbench">
      <!-- 左：项目列表 -->
      <aside class="panel project-panel">
        <header class="panel-head">
          <h3>视觉项目</h3>
          <button type="button" class="ghost-btn" :disabled="loadingProjects" @click="loadProjects">刷新</button>
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
      <section class="panel detail-panel">
        <div v-if="!currentProject" class="placeholder">
          <p>从左侧选择一个视觉项目开始。</p>
          <p class="hint">选好项目后：上传参考图 → 填品牌 Brief 与文案要点 → 描述你想要的画面 → 生成 HERO 主图候选。</p>
        </div>

        <template v-else>
          <header class="detail-head">
            <div class="detail-title">
              <h2>{{ currentProject.taskName }}</h2>
              <div class="detail-tags">
                <span class="stage-tag" :class="'is-' + stageType(currentProject.visualStage)">
                  {{ stageLabel(currentProject.visualStage) }}
                </span>
                <span class="muted">{{ currentProject.taskNo }}</span>
                <span class="muted">内容协同状态：{{ currentProject.status }}</span>
                <span v-if="currentProject.productName" class="muted">
                  产品：{{ currentProject.productName }}
                </span>
              </div>
            </div>
            <div class="detail-actions">
              <el-button size="small" @click="openDna">视觉基因</el-button>
              <el-button size="small" @click="loadDetail">刷新</el-button>
              <el-button size="small" @click="timelineVisible = true">操作日志</el-button>
            </div>
          </header>

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

            <!-- 品牌 Brief：委托方的要求 -->
            <section class="block">
              <div class="block-head">
                <h4>2. 品牌 Brief</h4>
                <div class="block-actions">
                  <el-tag :type="briefStatusType" size="small" effect="dark">{{ briefStatusText }}</el-tag>
                  <span v-if="briefDirty" class="brief-dirty">已修改未保存</span>
                  <el-button size="small" plain :loading="briefBusy === 'load'" @click="onRefreshBrief">刷新</el-button>
                  <el-button size="small" type="primary" plain :loading="briefBusy === 'save'" @click="doSaveBrandBrief">
                    保存
                  </el-button>
                  <el-button size="small" type="primary" :loading="briefBusy === 'confirm'" @click="doConfirmBrandBrief">
                    确认品牌要求
                  </el-button>
                </div>
              </div>
              <p class="hint">
                这里填的是<b>委托方（品牌）的要求</b>——「必须怎么做」，不是产品客观事实。
                产品事实在下面「4. 事实确认」里逐条确认。品牌调性与事实里的 brand_tone
                <b>并存</b>：一个是品牌方自己填的要求，一个是从资料里解析确认的，两者冲突时同时展示、由人裁定，不自动合并。
                已确认的 Brief 会被出图提示词与文案校验引用（必显信息进正向词、禁用词进负向词）。
              </p>
              <p v-if="brandBriefError" class="fact-error">
                {{ brandBriefError }}（点右上「刷新」重试，页面不会用默认值糊过去）
              </p>
              <div class="brief-grid">
                <div v-for="field in briefFields" :key="field.key" class="brief-row">
                  <label :for="'brief-' + field.key">{{ field.label }}</label>
                  <div class="brief-control">
                    <el-input
                      :id="'brief-' + field.key"
                      v-model="briefForm[field.key]"
                      type="textarea"
                      :rows="field.rows"
                      :maxlength="field.max"
                      show-word-limit
                      :placeholder="field.placeholder"
                    />
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
                「必显信息」不在这里录入——它是<b>品牌方的要求</b>，统一在「2. 品牌 Brief」里填，由出图提示词与闸门引用，
                避免同一件事有两个真相源。
                与分镜的分工：分镜屏文案是「这一屏这张图配什么字」，<b>R7 起屏文案也会进图像提示词</b>（画面独白优先）；
                而这里整页的文字不进出图提示词——出图提示词用的是视觉基因 + 「2. 品牌 Brief」的必显 / 主推 / 禁用词。
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
                  <el-table v-if="blocksOf(tab.value).length" :data="blocksOf(tab.value)" size="small">
                    <el-table-column label="排序" width="126">
                      <template #default="{ row }">
                        <span class="muted">{{ asBlock(row).sortNo ?? '—' }}</span>
                        <template v-if="tab.value === 'SELLING_POINT'">
                          <el-button
                            size="small"
                            text
                            type="primary"
                            :disabled="isFirstBlock(tab.value, asBlock(row)) || copyBusy === 'reorder'"
                            @click="moveBlock(asBlock(row), -1)"
                          >
                            ↑
                          </el-button>
                          <el-button
                            size="small"
                            text
                            type="primary"
                            :disabled="isLastBlock(tab.value, asBlock(row)) || copyBusy === 'reorder'"
                            @click="moveBlock(asBlock(row), 1)"
                          >
                            ↓
                          </el-button>
                        </template>
                      </template>
                    </el-table-column>
                    <el-table-column label="标题" width="170" show-overflow-tooltip>
                      <template #default="{ row }">{{ asBlock(row).title || '—' }}</template>
                    </el-table-column>
                    <el-table-column label="内容" min-width="240" show-overflow-tooltip>
                      <template #default="{ row }">{{ asBlock(row).content || '—' }}</template>
                    </el-table-column>
                    <el-table-column label="来源" width="170">
                      <template #default="{ row }">
                        <el-tag size="small" effect="plain" :type="copySourceType(asBlock(row).source)">
                          {{ copySourceLabel(asBlock(row).source) }}
                        </el-tag>
                        <span v-if="asBlock(row).sourceRef" class="muted">· {{ asBlock(row).sourceRef }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="状态" width="90">
                      <template #default="{ row }">
                        <el-tag size="small" :type="copyStatusType(asBlock(row).status)">
                          {{ copyStatusLabel(asBlock(row).status) }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="130" fixed="right">
                      <template #default="{ row }">
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
                      </template>
                    </el-table-column>
                  </el-table>
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
                <ul class="fact-check">
                  <li v-for="option in requiredFieldOptions" :key="String(option.fieldCode)" :class="{ ok: option.satisfied }">
                    <span class="mark">{{ option.satisfied ? '✓' : '✗' }}</span>
                    <span class="check-name">{{ option.fieldName || option.fieldCode }}</span>
                    <span class="muted">{{ option.fieldCode }} · {{ option.gateLevel || '—' }}</span>
                  </li>
                  <li v-if="!requiredFieldOptions.length" class="muted">该交付类型没有声明必填事实项。</li>
                </ul>
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

              <el-table v-if="facts.length" :data="facts" size="small" class="fact-table">
                <el-table-column label="字段" width="150" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ asFact(row).fieldName || asFact(row).fieldCode }}
                  </template>
                </el-table-column>
                <el-table-column label="值" width="150" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ asFact(row).fieldValue }}<span v-if="asFact(row).unit"> {{ asFact(row).unit }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="来源" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ asFact(row).sourceFileName || '—' }}
                    <span v-if="asFact(row).sourceLocator" class="muted">· {{ asFact(row).sourceLocator }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="原文摘录" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">{{ asFact(row).sourceExcerpt || '—' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="factStatusType(asFact(row).confirmStatus)">
                      {{ factStatusLabel(asFact(row).confirmStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right">
                  <template #default="{ row }">
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
                  </template>
                </el-table-column>
              </el-table>
              <p v-else class="empty">
                还没有事实候选。资料解析后会自动落成待确认行；也可以点「人工录入」补齐。
              </p>
            </section>

            <!-- 出图 -->
            <section class="block">
              <div class="block-head">
                <h4>5. 生成 HERO 主图</h4>
                <span class="muted">R0 每次出 1 张候选；重试=新增一次候选</span>
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
        </template>
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
import {
  addCopyBlock,
  addCreativeProject,
  bindProjectProductImage,
  confirmBrandBrief,
  deleteCopyBlock,
  fetchCreativeFileBlobUrl,
  fetchGenerationPreviewBlobUrl,
  fetchGenerationThumbnailBlobUrl,
  getBrandBrief,
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
  reorderCopyBlocks,
  retryGeneration,
  saveBrandBrief,
  seedCopyBlocksFromFacts,
  submitHero,
  updateCopyBlock,
  uploadCreativeReference
} from '@/api/creative';
import type {
  BrandBriefForm,
  BrandBriefVO,
  CopyBlockForm,
  CopyBlockVO,
  CreativeProjectVO,
  CreativeWorkflowVO,
  DpGenerationVO,
  DpStageEventVO,
  ProjectProductImageVO,
  TagType
} from '@/api/creative/types';
import {
  BRAND_BRIEF_STATUS_LABELS,
  BRAND_BRIEF_STATUS_TYPES,
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
import CreativeFlowGuide from '../components/CreativeFlowGuide.vue';

const GUIDE_KEY = 'hotter.creative.guide.dismissed';

const showGuide = ref(localStorage.getItem(GUIDE_KEY) !== '1');
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
// 品牌 Brief（委托方的要求）
// ------------------------------------------------------------------

/** 一个 Brief 字段的元信息：标签、行数、长度上限（与 dp_brand_brief 列宽一致）、填什么 */
interface BriefField {
  key: BriefFieldKey;
  label: string;
  rows: number;
  max: number;
  placeholder: string;
  hint: string;
}

type BriefFieldKey =
  | 'brandTone'
  | 'mustShow'
  | 'forbiddenWords'
  | 'targetAudience'
  | 'mainPush'
  | 'sizeSpecReq'
  | 'styleRef'
  | 'remark';

/**
 * 品牌 Brief 的字段定义（顺序即页面顺序）。
 * 长度上限照抄 dp_brand_brief 的列宽，页面先挡住超长，不让人填完才被后端拒。
 */
const briefFields: BriefField[] = [
  {
    key: 'brandTone',
    label: '品牌调性',
    rows: 2,
    max: 500,
    placeholder: '如：清新、治愈、自然；克制不喧哗',
    hint: '品牌方希望的调性。与事实里的 brand_tone 并存，冲突时以人裁定（页面不会自动合并两处）。'
  },
  {
    key: 'mustShow',
    label: '必显信息',
    rows: 3,
    max: 2000,
    placeholder: '一行一条，如：\n品牌名「趣往」\n「每日一枝，治愈生活」\n有机认证标志',
    hint: '必须出现在成品里的内容（品牌名 / logo / 口号 / 资质），一行一条；出图与文案都会校验它有没有落地。'
  },
  {
    key: 'forbiddenWords',
    label: '禁用词与红线',
    rows: 3,
    max: 1000,
    placeholder: '一行一条，如：\n最\n第一\n治疗失眠',
    hint: '合规红线与禁用词，一行一条；它同时作为出图负向词与文案校验依据。'
  },
  {
    key: 'mainPush',
    label: '主推卖点与优先级',
    rows: 3,
    max: 2000,
    placeholder: '一行一条，行首写优先级，如：\n1 单枝直发，48小时新鲜到家\n2 花苞大，开瓶率高',
    hint: '行首的 1/2/3 就是优先级（1 最高）。'
  },
  {
    key: 'targetAudience',
    label: '目标人群',
    rows: 2,
    max: 500,
    placeholder: '如：25-35 岁都市女性，悦己消费',
    hint: '卖给谁。影响文案口吻与画面调性。'
  },
  {
    key: 'sizeSpecReq',
    label: '尺寸与规范',
    rows: 2,
    max: 1000,
    placeholder: '如：详情页宽 750px；主图 1:1；正文不小于 14px',
    hint: '画布比例、留白、字号、平台规范等硬要求。'
  },
  {
    key: 'styleRef',
    label: '参考风格',
    rows: 2,
    max: 1000,
    placeholder: '如：参考图 2 的柔和自然光；无印良品式的留白',
    hint: '参考图 / 参考品牌 / 风格描述，帮助统一画面取向。'
  },
  {
    key: 'remark',
    label: '其它说明',
    rows: 2,
    max: 500,
    placeholder: '其它要交代的要求（可空）',
    hint: '上面没覆盖到的要求写这里。'
  }
];

/** Brief 表单（全部按字符串处理：后端列都是 varchar，空串与 null 语义相同） */
const briefForm = reactive<Record<BriefFieldKey, string>>({
  brandTone: '',
  mustShow: '',
  forbiddenWords: '',
  targetAudience: '',
  mainPush: '',
  sizeSpecReq: '',
  styleRef: '',
  remark: ''
});

/** 服务端当前的 Brief（含状态与确认人/时间）；未填写时后端也返回对象 */
const brandBrief = ref<BrandBriefVO | null>(null);
/** 是否成功从服务端取过：没取到就不下「未填写」的结论 */
const briefLoaded = ref(false);
/** 当前这份 Brief 属于哪个项目：切项目后必须重新取，否则会拿上一个项目的结果冒充（竞态） */
const briefTaskId = ref('');
const brandBriefError = ref('');
const briefBusy = ref('');
/** 上次保存/加载时的表单快照，用来判断「已修改未保存」 */
const briefSaved = ref('');

/** 表单快照：按字段定义顺序拼，保证同一内容得到同一字符串 */
function briefSnapshot(): string {
  return JSON.stringify(briefFields.map((field) => briefForm[field.key] ?? ''));
}

// 初始快照 = 空表单：否则刚进页面什么都没动就会显示「已修改未保存」
briefSaved.value = briefSnapshot();

/**
 * 是否「已修改未保存」。
 *
 * 刻意只比内容、不看是否加载成功：读接口失败时（表单是空的）人照样可以填写，
 * 这时也必须如实提示「未保存」，不能因为「没取到」就假装没有改动。
 */
const briefDirty = computed(() => briefSnapshot() !== briefSaved.value);

const briefStatusText = computed(() => {
  if (!briefLoaded.value) return '未加载';
  if (!brandBrief.value?.configured) return '未填写';
  if (brandBrief.value.status === 'CONFIRMED') {
    const who = brandBrief.value.confirmedByName || brandBrief.value.confirmedBy || '—';
    const when = formatTime(brandBrief.value.confirmedAt) || '—';
    return `已确认（${who} · ${when}）`;
  }
  return BRAND_BRIEF_STATUS_LABELS[brandBrief.value.status || 'DRAFT'] || '草稿';
});

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
 * <p>为什么没有「必显信息」这一组：品牌方的必显要求统一在「品牌 Brief」的 mustShow 里录入，
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

function dismissGuide() {
  showGuide.value = false;
  localStorage.setItem(GUIDE_KEY, '1');
}

async function loadProjects() {
  loadingProjects.value = true;
  try {
    const res = await listCreativeProject({
      pageNum: 1,
      pageSize: 50,
      queryTaskName: queryTaskName.value || undefined
    });
    projects.value = res.data?.rows || [];
    if (!projects.value.length) {
      currentProjectId.value = '';
      currentProject.value = null;
      return;
    }
    const stillThere = projects.value.some((p) => String(p.taskId) === String(currentProjectId.value));
    if (!stillThere) {
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
  // 换项目就换 Brief 与文案块：另一个项目的未保存输入不能留在这一页上
  brandBrief.value = null;
  briefLoaded.value = false;
  // 关键：把「这份 Brief 属于哪个项目」也清掉。否则上一个项目迟到的响应会把
  // briefLoaded 置成 true，新项目就再也不发请求（R7 浏览器验收复现的竞态）。
  briefTaskId.value = '';
  briefBusy.value = '';
  brandBriefError.value = '';
  applyBriefToForm(null);
  copyBlocks.value = [];
  copyLoadError.value = '';
  copyBusy.value = '';
  stopPolling();
  await loadDetail();
}

async function loadDetail() {
  if (!currentProjectId.value) return;
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
    // 品牌 Brief 与文案块跟着详情一起取：各自失败各自如实报，不影响整页
    void loadBrandBrief();
    void loadCopyBlocks();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '加载项目详情失败');
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
// 品牌 Brief：读 / 存 / 确认
// ------------------------------------------------------------------

/** 把服务端的 Brief 灌进表单，并把当前内容记为「已保存」快照 */
function applyBriefToForm(data: BrandBriefVO | null) {
  briefFields.forEach((field) => {
    briefForm[field.key] = (data?.[field.key] as string | undefined) ?? '';
  });
  briefSaved.value = briefSnapshot();
}

/**
 * 读品牌 Brief。
 *
 * <p>两个刻意的行为：</p>
 * <ol>
 *   <li>读失败不静默：置 brandBriefError，页面照实说「没取到」，空表不代表项目里没有要求；</li>
 *   <li>已经取到过就不再自动重取（{@code force=false}）：跟随详情刷新时绝不覆盖人正在输入的内容，
 *       只有「刷新」按钮才会用服务端内容替换当前表单。</li>
 * </ol>
 */
async function loadBrandBrief(force = false) {
  const taskId = String(currentProjectId.value || '');
  if (!taskId) return;
  // 已经取到过就不再自动覆盖输入：只有「刷新」按钮（force）才会用服务端内容替换当前表单。
  // 但「取到过」必须**按项目**判断：briefTaskId 是这份 Brief 属于哪个项目。
  // 只认 briefLoaded 会踩竞态——上一个项目迟到的响应把 briefLoaded 置成 true，
  // 于是新项目一个请求都不发，表单停在「未填写」（R7 浏览器验收 3/3 复现）。
  if (!force && briefLoaded.value && briefTaskId.value === taskId) return;
  briefBusy.value = 'load';
  brandBriefError.value = '';
  try {
    const res = await getBrandBrief(taskId);
    // 迟到的响应不能写进表单：期间人可能已经切到别的项目了
    if (String(currentProjectId.value || '') !== taskId) return;
    brandBrief.value = res.data ?? null;
    applyBriefToForm(brandBrief.value);
    briefTaskId.value = taskId;
    briefLoaded.value = true;
  } catch (error) {
    if (String(currentProjectId.value || '') !== taskId) return;
    brandBrief.value = null;
    briefLoaded.value = false;
    briefTaskId.value = '';
    brandBriefError.value =
      '品牌 Brief 没取到（' + ((await extractErrorMessage(error)) ?? '接口失败') + '），下面的空表不代表该项目没有品牌要求';
  } finally {
    if (String(currentProjectId.value || '') === taskId) briefBusy.value = '';
  }
}

function onRefreshBrief() {
  if (briefDirty.value) {
    ElMessage.warning('有未保存的修改，刷新会用服务端内容覆盖当前输入；请先点「保存」');
    return;
  }
  void loadBrandBrief(true);
}

/** 提交体：只带 8 个要求字段（状态由 confirm 接口推进，这里不传 status） */
function briefPayload(): BrandBriefForm {
  return {
    brandTone: briefForm.brandTone,
    mustShow: briefForm.mustShow,
    forbiddenWords: briefForm.forbiddenWords,
    targetAudience: briefForm.targetAudience,
    mainPush: briefForm.mainPush,
    sizeSpecReq: briefForm.sizeSpecReq,
    styleRef: briefForm.styleRef,
    remark: briefForm.remark
  };
}

/** 8 项里是否至少有一项写了内容（空白不算） */
function briefPayloadHasContent(): boolean {
  const payload = briefPayload();
  return briefFields.some((field) => (payload[field.key] ?? '').trim().length > 0);
}

async function doSaveBrandBrief() {
  if (!currentProjectId.value || briefBusy.value) return;
  briefBusy.value = 'save';
  try {
    const res = await saveBrandBrief(currentProjectId.value, briefPayload());
    if (res.data) {
      brandBrief.value = res.data;
    }
    applyBriefToForm(brandBrief.value);
    briefTaskId.value = String(currentProjectId.value || '');
    briefLoaded.value = true;
    brandBriefError.value = '';
    // 后端规则：保存草稿不会把已确认打回草稿（确认权在品牌方，不在保存表单）。
    // 所以「已确认」的 Brief 被改过并保存后，状态仍是已确认——这必须说清楚，不能让页面假装还是那条被确认的内容。
    if (brandBrief.value?.status === 'CONFIRMED') {
      ElMessage.warning('已保存；状态仍是「已确认」。内容有改动，建议重新点「确认品牌要求」，让确认动作对得上最新内容。');
    } else {
      ElMessage.success('品牌 Brief 已保存（状态：草稿）');
    }
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '保存品牌 Brief 失败');
  } finally {
    briefBusy.value = '';
  }
}

async function doConfirmBrandBrief() {
  if (!currentProjectId.value || briefBusy.value) return;
  if (briefDirty.value) {
    ElMessage.warning('有未保存的修改，请先点「保存」再确认');
    return;
  }
  if (!brandBrief.value?.configured) {
    ElMessage.warning('品牌 Brief 还没有内容：请先填写并保存，再确认');
    return;
  }
  if (!briefPayloadHasContent()) {
    ElMessage.warning('品牌 Brief 8 项全空：至少填一项再确认（确认后的要求会被出图与文案校验引用）');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '确认后这条 Brief 就是「品牌方已确认的要求」，出图提示词与文案校验会引用它。是否继续？',
      '确认品牌要求',
      { type: 'warning' }
    );
  } catch {
    return;
  }
  briefBusy.value = 'confirm';
  try {
    const res = await confirmBrandBrief(currentProjectId.value);
    if (res.data) {
      brandBrief.value = res.data;
      applyBriefToForm(brandBrief.value);
    }
    ElMessage.success('品牌要求已确认');
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '确认品牌要求失败');
  } finally {
    briefBusy.value = '';
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

.guide-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  margin-bottom: 22px;
  color: #ddd6fe;
  font-size: 13px;
  line-height: 1.7;
  background: rgba(148, 163, 184, 0.1);
  border: 1px solid rgba(186, 197, 209, 0.24);
  border-radius: 6px;
}
.guide-bar > span {
  flex: 1;
}
.guide-bar button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: var(--t2);
  cursor: pointer;
  background: transparent;
  border: 0;
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
.fact-check {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0;
  margin: 0 0 12px;
  list-style: none;
}
.fact-check li {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  color: var(--t2);
}
.fact-check .mark {
  color: #ef4444;
  font-weight: 700;
}
.fact-check li.ok .mark {
  color: #10b981;
}
.fact-check .check-name {
  color: var(--t1);
}
.fact-table {
  margin-top: 4px;
  background: transparent;
}
.fact-error {
  margin: 0 0 10px;
  font-size: 12.5px;
  line-height: 1.8;
  color: #fca5a5;
}

/* 品牌 Brief：一行一个要求，左侧标签固定宽，右侧输入 + 一句「填什么」 */
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
.brief-dirty {
  font-size: 12px;
  color: #fbbf24;
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
