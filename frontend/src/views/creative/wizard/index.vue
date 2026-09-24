<template>
  <div class="studio">
    <!-- 顶部：项目 + 总体进度 -->
    <header class="page-head">
      <div>
        <h2>AI 视觉工厂 · 流程向导</h2>
        <p class="muted">
          八个步骤串在一页里：<b>每一步都在本页完成</b>，不用回菜单换页面。每个步骤卡都会说清
          「现在什么状态」「还差什么」「在哪补」，缺数据时如实显示「未开始/被阻塞」，不假装完成。
        </p>
      </div>
      <div class="head-actions">
        <el-select
          v-model="taskId"
          placeholder="选择视觉项目"
          filterable
          style="width: 280px"
          @change="onProjectChange"
        >
          <el-option
            v-for="item in projects"
            :key="String(item.taskId)"
            :label="item.taskName || String(item.taskId)"
            :value="String(item.taskId)"
          />
        </el-select>
        <el-button plain :loading="loading" @click="reload">刷新全部</el-button>
        <el-button
          v-if="nextStep"
          type="primary"
          plain
          @click="focusStep(nextStep.key)"
        >
          跳到「{{ nextStep.name }}」
        </el-button>
      </div>
    </header>

    <p v-if="!projects.length && !loading" class="empty">
      还没有视觉项目。请先到「视觉项目」页新建一个，或在「内容生产协同 → 内容任务」里把交付类型选为电商详情图。
    </p>
    <p v-else-if="!taskId" class="empty">请先在上方选择一个视觉项目，向导会一次性把八个步骤的状态都算出来。</p>

    <div v-else class="wizard">
      <!-- 左：步骤导航 -->
      <aside class="rail">
        <div class="rail-head">
          <strong>{{ doneCount }} / 8 已完成</strong>
          <span class="muted">{{ projectStageLabel }}</span>
        </div>
        <button
          v-for="step in stepCards"
          :key="step.key"
          type="button"
          class="rail-item"
          :class="['is-' + step.status, { active: step.key === currentKey }]"
          @click="focusStep(step.key)"
        >
          <span class="rail-no">{{ step.no }}</span>
          <span class="rail-name">{{ step.name }}</span>
          <span class="rail-status">{{ step.statusLabel }}</span>
        </button>
        <p v-if="project && project.blockReason" class="rail-warn">
          内容协同当前阻断原因：{{ project.blockReason }}
        </p>
      </aside>

      <!-- 右：步骤卡 -->
      <div class="steps">
        <section v-if="loading" class="panel loading-panel">
          正在读取该项目八个步骤的数据…（数据没到齐之前不显示任何「还没有/未配置」的结论，避免误报）
        </section>
        <section
          v-for="step in stepCards"
          v-else
          :id="'wizard-step-' + step.key"
          :key="step.key"
          class="panel step-card"
          :class="'is-' + step.status"
        >
          <header class="step-head" @click="toggleStep(step.key)">
            <span class="step-no">{{ step.no }}</span>
            <h3>{{ step.name }}</h3>
            <span class="status-tag" :class="'is-' + step.status">{{ step.statusLabel }}</span>
            <span class="spacer" />
            <span class="muted small">{{ step.summary }}</span>
            <el-button link size="small" type="primary">{{ isOpen(step.key) ? '收起' : '展开' }}</el-button>
          </header>

          <p class="why" :class="'is-' + step.status">
            <b>{{ step.status === 'done' ? '进入下一步的条件：' : '为什么还不能进入下一步：' }}</b>{{ step.reason }}
          </p>

          <p v-if="stepError(step.key)" class="load-error">
            本步数据没取到：{{ stepError(step.key) }}（点右上「刷新全部」重试；页面不会用旧数据或默认值糊过去）
          </p>

          <div v-show="isOpen(step.key)" class="step-body">
            <!-- ============ 1. 项目与资料 ============ -->
            <template v-if="step.key === 'material'">
              <div class="grid-2">
                <div class="sub-panel">
                  <div class="sub-head">
                    <h4>产品图（出图与质检的保真基准）</h4>
                    <el-tag size="small" :type="productImage && productImage.configured ? 'success' : 'warning'">
                      {{ productImage && productImage.configured ? '已配置' : '未配置' }}
                    </el-tag>
                  </div>
                  <div v-if="productImage && productImage.configured" class="product-image-box">
                    <img v-if="urlOf('product')" :src="urlOf('product')" alt="产品图" />
                    <span v-else class="img-placeholder">产品图读取中…</span>
                    <div class="product-meta">
                      <div><b>产品：</b>{{ productImage.productName || '—' }}</div>
                      <div><b>文件：</b>{{ productImage.fileName || '—' }}</div>
                      <div>
                        <b>来源：</b>
                        {{ String(productImage.sourceTaskId) === String(taskId)
                          ? '本项目'
                          : '其它项目（taskId=' + productImage.sourceTaskId + '）' }}
                      </div>
                      <div v-if="productImage.setAt"><b>设定时间：</b>{{ formatTime(productImage.setAt) }}</div>
                    </div>
                  </div>
                  <div v-else class="hint-block">
                    <p>{{ productImage?.note || '该产品还没有产品图。' }}</p>
                    <p class="muted small">
                      判定口径：只有后端返回 <code>configured === true</code> 才算有产品图；本页不把「随便一张参考图」当成产品图。
                    </p>
                  </div>
                </div>

                <div class="sub-panel">
                  <div class="sub-head">
                    <h4>上传产品照片 / 参考图</h4>
                    <el-tag size="small" type="info">{{ imageFiles.length }} 张图片</el-tag>
                  </div>
                  <el-checkbox v-model="asProductImage" :disabled="!canBindProductImage">
                    同时设为该产品的产品图
                  </el-checkbox>
                  <p v-if="!canBindProductImage" class="muted small">
                    该项目没有关联产品，无法登记产品图（后端会直接拒绝）——请先在「视觉项目」里给项目选产品。
                  </p>
                  <p v-else-if="asProductImage" class="dna-hint">
                    勾选后：本次上传会同时写回该产品的产品图，并成为第 7 步「产品图 | 生成图」对比的基准。
                  </p>
                  <el-upload
                    class="mt10"
                    :show-file-list="false"
                    accept="image/png,image/jpeg,image/webp"
                    :http-request="doUpload"
                  >
                    <el-button :loading="busy === 'upload'" type="primary">
                      {{ asProductImage ? '上传并设为产品图' : '上传参考图' }}
                    </el-button>
                  </el-upload>
                  <p class="muted small">PNG / JPG / WEBP，单张 ≤20MB。</p>
                </div>
              </div>

              <div class="sub-panel">
                <div class="sub-head">
                  <h4>项目附件（按角色标注）</h4>
                  <span class="muted small">产品图 / 参考图 / 生成图 三种角色必须分得清，否则出图基准会拿错</span>
                </div>
                <p v-if="!files.length" class="empty">还没有任何附件。先上传产品照片。</p>
                <div v-else class="file-grid">
                  <div v-for="file in files" :key="String(file.fileId)" class="file-card">
                    <div class="file-cover">
                      <img v-if="urlOf('file-' + file.fileId)" :src="urlOf('file-' + file.fileId)" :alt="file.fileName" />
                      <span v-else class="img-placeholder">读取中…</span>
                    </div>
                    <div class="file-meta">
                      <span class="file-name" :title="file.fileName">{{ file.fileName || '未命名' }}</span>
                      <el-tag size="small" :type="fileSourceType(file.sourceType)">
                        {{ fileSourceLabel(file.sourceType) }}
                      </el-tag>
                    </div>
                    <el-button
                      v-if="isBindableImage(file)"
                      link
                      size="small"
                      type="primary"
                      :loading="busy === 'bind-' + file.fileId"
                      @click="doBindProductImage(file)"
                    >
                      设为产品图
                    </el-button>
                    <span v-else-if="file.sourceType === 'PRODUCT'" class="muted small">已是产品图</span>
                  </div>
                </div>
              </div>
            </template>

            <!-- ============ 2. 事实确认 ============ -->
            <template v-else-if="step.key === 'fact'">
              <div class="sub-panel">
                <div class="sub-head">
                  <h4>必填事实（本条交付类型的闸门要求项）</h4>
                  <el-button
                    size="small"
                    plain
                    :loading="busy === 'confirmUnambiguous'"
                    @click="doConfirmUnambiguous"
                  >
                    一键确认无争议项
                  </el-button>
                </div>
                <p class="muted small">
                  只有 <b>CONFIRMED</b> 的事实才会进入基因 / 方向 / 分镜文案的推导；PENDING 与已否决都不算。
                </p>
                <p v-if="!fieldOptionsLoaded" class="load-error">
                  字段选项接口没取到，无法判断闸门必填项是否齐备——不猜，请在下方事实表里逐条确认。
                </p>
                <ul v-else class="check-list">
                  <li v-for="option in requiredFieldOptions" :key="option.fieldCode" :class="{ ok: option.satisfied }">
                    <span class="mark">{{ option.satisfied ? '✓' : '✗' }}</span>
                    <span class="check-name">{{ option.fieldName || option.fieldCode }}</span>
                    <span class="muted small">{{ option.fieldCode }} · {{ option.gateLevel || '—' }}</span>
                  </li>
                  <li v-if="!requiredFieldOptions.length" class="muted">该交付类型没有声明必填事实项。</li>
                </ul>
                <el-button
                  v-for="option in unsatisfiedRequiredOptions"
                  :key="'fill-' + option.fieldCode"
                  class="fill-btn"
                  size="small"
                  @click="openManualFor(option.fieldCode)"
                >
                  ＋ 录入「{{ option.fieldName || option.fieldCode }}」
                </el-button>
              </div>

              <div class="sub-panel">
                <div class="sub-head">
                  <h4>事实清单</h4>
                  <span class="muted small">已确认 {{ confirmedFacts.length }} 条 / 共 {{ facts.length }} 行</span>
                </div>
                <el-table v-if="facts.length" :data="facts" size="small">
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
                      <span v-if="asFact(row).sourceLocator" class="muted small">· {{ asFact(row).sourceLocator }}</span>
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
                        :loading="busy === 'fact-' + asFact(row).snapshotId"
                        @click="doConfirmFact(asFact(row))"
                      >
                        确认
                      </el-button>
                      <el-button
                        link
                        size="small"
                        type="danger"
                        :disabled="asFact(row).confirmStatus === 'REJECTED'"
                        @click="doRejectFact(asFact(row))"
                      >
                        否决
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <p v-else class="empty">
                  还没有事实候选。资料解析后会自动落成待确认行；也可以直接用下面的「人工录入事实」补齐。
                </p>
              </div>

              <div class="sub-panel">
                <div class="sub-head"><h4>人工录入事实</h4></div>
                <div class="form-row">
                  <label>字段</label>
                  <el-select
                    v-model="manualForm.fieldCode"
                    filterable
                    allow-create
                    default-first-option
                    placeholder="从闸门字段里选（不支持时可直接输入编码）"
                    style="width: 320px"
                  >
                    <el-option
                      v-for="option in fieldOptions"
                      :key="String(option.fieldCode)"
                      :label="optionLabel(option)"
                      :value="option.fieldCode"
                    />
                  </el-select>
                  <el-input v-model="manualForm.value" placeholder="字段值" style="width: 220px" />
                  <el-input v-model="manualForm.remark" placeholder="备注（可空）" style="width: 220px" />
                  <el-button
                    type="primary"
                    :loading="busy === 'manualFact'"
                    :disabled="!manualForm.fieldCode || !manualForm.value"
                    @click="doAddManualFact"
                  >
                    录入（落为待确认）
                  </el-button>
                </div>
              </div>
            </template>

            <!-- ============ 3. 视觉基因 ============ -->
            <template v-else-if="step.key === 'dna'">
              <div class="sub-panel">
                <div class="sub-head">
                  <h4>当前基因版本</h4>
                  <div class="head-actions">
                    <el-button
                      size="small"
                      :loading="busy === 'recommend'"
                      :disabled="!imageFiles.length"
                      @click="doRecommend"
                    >
                      按参考图推荐
                    </el-button>
                    <el-button size="small" :loading="busy === 'generateDna'" @click="doGenerateDna">
                      {{ dna ? '重新生成基因' : '生成视觉基因' }}
                    </el-button>
                    <el-button
                      size="small"
                      type="primary"
                      :disabled="!dna || dna.locked"
                      :loading="busy === 'lockDna'"
                      @click="doLockDna"
                    >
                      {{ dna && dna.locked ? '已锁定' : '锁定这一版' }}
                    </el-button>
                  </div>
                </div>
                <p v-if="!imageFiles.length" class="muted small">
                  项目里还没有参考图，「按参考图推荐」按钮不可用——推荐值的依据就是这张图的实测像素，没有图就没有依据。
                </p>
                <template v-if="dna">
                  <div class="dna-title">
                    <h4>{{ dna.dnaNo }} <span class="muted">v{{ dna.version }}</span></h4>
                    <el-tag size="small" :type="dnaSourceType(dna.source)">{{ dnaSourceLabel(dna.source) }}</el-tag>
                    <el-tag size="small" :type="dna.locked ? 'success' : 'warning'">{{ dna.statusDesc }}</el-tag>
                    <span class="muted small">{{ dna.sourceDesc }}</span>
                  </div>
                  <el-alert
                    v-if="(dna.issues || []).length"
                    type="warning"
                    show-icon
                    :closable="false"
                    class="mt10"
                    title="这一版还不能锁定，请先补齐："
                  >
                    <ul class="issue-list">
                      <li v-for="(issue, index) in dna.issues" :key="index">{{ issue }}</li>
                    </ul>
                  </el-alert>
                  <el-alert
                    v-else
                    type="success"
                    show-icon
                    :closable="false"
                    class="mt10"
                    title="规范自洽，可以锁定"
                  />
                </template>
                <p v-else class="empty">
                  这个项目还没有视觉基因。生成会用到「已确认的产品事实 + 项目参考图 + 一组明确标注来源的默认规范」；
                  没有可用模型时来源会如实标为「事实推导」，不会把默认值包装成 AI 结论。
                </p>
                <div v-if="lockedDna" class="locked-note">
                  已锁定版本：<b>{{ lockedDna.dnaNo }} v{{ lockedDna.version }}</b>——出图与分镜都以它为依据（最新版未必是出图依据）。
                </div>
              </div>

              <div v-if="dna" class="sub-panel">
                <div class="sub-head">
                  <h4>规范内容（可改，保存即生效）</h4>
                  <span class="muted small">已锁定版本再改会自动新建一版</span>
                </div>
                <div class="form-grid">
                  <div class="form-item span2">
                    <label>风格关键词</label>
                    <el-select
                      v-model="dnaForm.styleKeywords"
                      multiple
                      filterable
                      allow-create
                      default-first-option
                      style="width: 100%"
                      placeholder="如：现代简约 / 治愈 / 自然"
                    />
                  </div>
                  <div class="form-item span2">
                    <label>禁忌关键词</label>
                    <el-select
                      v-model="dnaForm.avoidKeywords"
                      multiple
                      filterable
                      allow-create
                      default-first-option
                      style="width: 100%"
                      placeholder="不该出现在画面里的东西"
                    />
                  </div>
                  <div class="form-item"><label>主色</label><el-color-picker v-model="dnaForm.colorPrimary" /></div>
                  <div class="form-item"><label>辅色</label><el-color-picker v-model="dnaForm.colorSecondary" /></div>
                  <div class="form-item"><label>点缀色</label><el-color-picker v-model="dnaForm.colorAccent" /></div>
                  <div class="form-item"><label>背景色</label><el-color-picker v-model="dnaForm.colorBg" /></div>
                  <div class="form-item">
                    <label>饱和度</label>
                    <el-select v-model="dnaForm.saturation" clearable placeholder="未设置">
                      <el-option v-for="o in DNA_LEVEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </div>
                  <div class="form-item">
                    <label>对比度</label>
                    <el-select v-model="dnaForm.contrastLevel" clearable placeholder="未设置">
                      <el-option v-for="o in DNA_LEVEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </div>
                  <div class="form-item">
                    <label>留白</label>
                    <el-select v-model="dnaForm.whitespaceLevel" clearable placeholder="未设置">
                      <el-option v-for="o in DNA_LEVEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </div>
                  <div class="form-item">
                    <label>场景类型</label>
                    <el-input v-model="dnaForm.sceneType" placeholder="如：纯色底 / 生活场景" />
                  </div>
                  <div class="form-item">
                    <label>光线类型</label>
                    <el-select v-model="dnaForm.lightingType" clearable placeholder="未设置">
                      <el-option v-for="o in DNA_LIGHTING_TYPES" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </div>
                  <div class="form-item">
                    <label>光位</label>
                    <el-select v-model="dnaForm.lightingDir" clearable placeholder="未设置">
                      <el-option v-for="o in DNA_LIGHTING_DIRS" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </div>
                  <div class="form-item">
                    <label>产品占比下限 (%)</label>
                    <el-input-number v-model="dnaForm.productRatioMin" :min="0" :max="100" controls-position="right" />
                  </div>
                  <div class="form-item">
                    <label>产品占比上限 (%)</label>
                    <el-input-number v-model="dnaForm.productRatioMax" :min="0" :max="100" controls-position="right" />
                  </div>
                  <div class="form-item span2">
                    <label>字体风格</label>
                    <el-input v-model="dnaForm.typographyStyle" placeholder="如：无衬线、字号层级分明" />
                  </div>
                </div>
                <div class="save-row">
                  <el-button type="primary" :loading="busy === 'saveDna'" @click="doSaveDna">保存基因</el-button>
                  <el-button @click="fillDnaForm(dna)">还原为当前版本</el-button>
                  <el-button :loading="busy === 'dnaPrompt'" @click="loadDnaPrompt">派生提示词</el-button>
                </div>
                <div v-if="dnaPrompt" class="prompt-box">
                  <p class="muted small">用到的维度：{{ (dnaPrompt.applied || []).join('、') || '—' }}（这是预填内容，可改）</p>
                  <el-input :model-value="dnaPrompt.prompt" type="textarea" :rows="3" readonly />
                  <el-input :model-value="dnaPrompt.negativePrompt" type="textarea" :rows="2" readonly class="mt8" />
                </div>
              </div>

              <div v-if="recommendation" class="sub-panel">
                <div class="sub-head">
                  <h4>参考图推荐的依据</h4>
                  <span class="muted small">
                    {{ recommendation.imageName || '—' }}
                    <template v-if="recommendation.imageWidth">
                      （{{ recommendation.imageWidth }}×{{ recommendation.imageHeight }}）
                    </template>
                    <template v-if="recommendation.observedProductRatio != null">
                      　实测产品占画面 {{ recommendation.observedProductRatio.toFixed(0) }}%
                    </template>
                  </span>
                </div>
                <el-alert
                  v-for="(item, index) in recommendation.conflicts || []"
                  :key="'cf' + index"
                  type="warning"
                  show-icon
                  :closable="false"
                  class="mt6"
                  :title="item"
                />
                <el-table :data="recommendation.evidence || []" size="small" class="mt6" empty-text="没有可用依据">
                  <el-table-column prop="field" label="字段" width="150" />
                  <el-table-column prop="value" label="推荐值" width="130" />
                  <el-table-column label="可信度" width="100">
                    <template #default="{ row }">
                      <el-tag size="small" :type="row.reliability === 'HIGH' ? 'success' : 'warning'">
                        {{ row.reliability === 'HIGH' ? '实测' : '弱启发' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="basis" label="依据" min-width="300" show-overflow-tooltip />
                </el-table>
                <div v-if="(recommendation.skipped || []).length" class="mt10">
                  <h4 class="mini-title">测不出来、明确不猜的字段</h4>
                  <ul class="issue-list">
                    <li v-for="(item, index) in recommendation.skipped" :key="'sk' + index">{{ item }}</li>
                  </ul>
                </div>
                <div v-if="(recommendation.notes || []).length" class="mt10">
                  <h4 class="mini-title">说明</h4>
                  <ul class="issue-list">
                    <li v-for="(item, index) in recommendation.notes" :key="'nt' + index">{{ item }}</li>
                  </ul>
                </div>
                <p class="muted small">推荐只填表单，<b>点「保存基因」才落库</b>。</p>
              </div>
            </template>

            <!-- ============ 4. 视觉方向 ============ -->
            <template v-else-if="step.key === 'direction'">
              <div class="sub-head">
                <h4>视觉方向（同一锁定基因下的不同取舍）</h4>
                <el-button
                  type="primary"
                  size="small"
                  :loading="busy === 'generateDirections'"
                  :disabled="!lockedDna"
                  @click="doGenerateDirections"
                >
                  {{ directions.length ? '重新生成方向' : '生成方向' }}
                </el-button>
              </div>
              <p v-if="!lockedDna" class="muted small">
                还没有已锁定的视觉基因，后端会直接拒绝生成方向——请先在第 3 步锁定基因。
              </p>
              <p v-if="!directions.length" class="empty">
                还没有方向。生成后会得到三套「同一基因下的不同取舍」，选定其一即可继续拆分镜。
              </p>
              <div v-else class="direction-grid">
                <div
                  v-for="item in directions"
                  :key="String(item.id)"
                  class="direction-card"
                  :class="{ selected: item.status === 'SELECTED', rejected: item.status === 'REJECTED' }"
                >
                  <div class="direction-head">
                    <span class="code">{{ item.directionCode }}</span>
                    <span class="name">{{ item.directionName }}</span>
                    <el-tag v-if="item.status === 'SELECTED'" type="success" size="small">已选定</el-tag>
                    <el-tag v-else-if="item.status === 'REJECTED'" type="info" size="small">已弃用</el-tag>
                    <el-tag v-else size="small">待选定</el-tag>
                  </div>
                  <p class="concept">{{ item.concept }}</p>
                  <ul class="strategy-list">
                    <li v-for="key in strategyKeys(item)" :key="key">
                      <span class="key" :class="{ diff: (item.differences || []).includes(key) }">{{ key }}</span>
                      <span class="value">{{ item.strategy?.[key] }}</span>
                    </li>
                  </ul>
                  <p v-if="(item.differences || []).length" class="muted small">
                    与其它方向的差异字段：{{ (item.differences || []).join('、') }}
                  </p>
                  <div class="direction-actions">
                    <el-button
                      size="small"
                      type="primary"
                      :disabled="item.status === 'SELECTED'"
                      :loading="busy === 'selectDirection-' + item.id"
                      @click="doSelectDirection(item)"
                    >
                      {{ item.status === 'SELECTED' ? '当前方向' : '选定这个方向' }}
                    </el-button>
                    <el-button size="small" text @click="openDirectionEdit(item)">编辑文案</el-button>
                  </div>
                </div>
              </div>
            </template>

            <!-- ============ 5. 分镜 ============ -->
            <template v-else-if="step.key === 'storyboard'">
              <div class="sub-head">
                <h4>
                  分镜
                  <template v-if="storyboard">
                    {{ storyboard.storyboardNo }} · v{{ storyboard.version }} · {{ storyboard.statusDesc }} ·
                    {{ storyboard.screenCount }} 屏
                  </template>
                </h4>
                <div class="head-actions">
                  <el-button
                    size="small"
                    :loading="busy === 'generateStoryboard'"
                    :disabled="!lockedDna"
                    @click="doGenerateStoryboard"
                  >
                    {{ storyboard ? '重新生成分镜' : '生成分镜' }}
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :disabled="!storyboard || storyboard.status === 'LOCKED'"
                    :loading="busy === 'lockStoryboard'"
                    @click="doLockStoryboard"
                  >
                    {{ storyboard && storyboard.status === 'LOCKED' ? '已锁定' : '锁定分镜' }}
                  </el-button>
                </div>
              </div>
              <p v-if="!lockedDna" class="muted small">还没有已锁定的视觉基因，后端会拒绝生成分镜——请先在第 3 步锁定基因。</p>
              <p v-if="storyboard && storyboard.sourceDesc" class="muted small">来源：{{ storyboard.sourceDesc }}</p>
              <p v-if="!storyboard" class="empty">还没有分镜。生成后会得到逐屏规格（屏号 / 类型 / 文案 / 画面独白 / 视觉规格）。</p>
              <template v-else>
                <p v-if="storyboard.status === 'LOCKED'" class="locked-note">
                  这一版已锁定，<b>锁定版不可修改</b>（后端会拒绝保存）。要改文案请先「重新生成分镜」得到新版本。
                </p>
                <div class="screen-list">
                  <div v-for="screen in storyboard.screens || []" :key="String(screen.id)" class="screen-card">
                    <div class="screen-head">
                      <span class="screen-no">{{ screen.screenNo }}</span>
                      <span class="screen-type">{{ screen.screenTypeDesc }}</span>
                      <el-tag size="small" :type="screen.productLockLevel === 'STRICT' ? 'warning' : 'info'">
                        {{ screen.productLockLevel === 'STRICT' ? '产品严格保真' : '允许艺术化' }}
                      </el-tag>
                      <span class="spacer" />
                      <el-button
                        size="small"
                        text
                        type="primary"
                        :disabled="storyboard.status === 'LOCKED'"
                        @click="openScreenEdit(screen)"
                      >
                        编辑
                      </el-button>
                    </div>
                    <div class="screen-body">
                      <h4>{{ screen.title || '（未填标题）' }}</h4>
                      <p v-if="screen.subtitle" class="muted">{{ screen.subtitle }}</p>
                      <p v-if="screen.bodyText" class="body-text">{{ screen.bodyText }}</p>
                      <p class="solo">
                        <b>画面独白：</b>
                        {{ screen.pictureSoloStatement || '（缺失——锁定前必须补齐）' }}
                      </p>
                      <div class="spec-row">
                        <span v-for="(value, key) in screen.spec" :key="key" class="spec-item">
                          <b>{{ specLabel(String(key)) }}</b>{{ value }}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
            </template>

            <!-- ============ 6. 视觉门 ============ -->
            <template v-else-if="step.key === 'gate'">
              <div class="sub-head">
                <h4>
                  视觉门
                  <el-tag v-if="gate && gate.passed" type="success" effect="dark" size="small">已通过</el-tag>
                  <el-tag v-else-if="gate && gate.cardStatus === 'PENDING'" type="warning" effect="dark" size="small">
                    待人工确认
                  </el-tag>
                  <el-tag v-else-if="gate && gate.cardStatus === 'BLOCKED'" type="danger" effect="dark" size="small">
                    已打回
                  </el-tag>
                  <el-tag v-else type="info" effect="dark" size="small">未提交</el-tag>
                </h4>
                <el-button
                  type="primary"
                  size="small"
                  :disabled="!gate || !gate.submittable || gate.passed"
                  :loading="busy === 'submitGate'"
                  @click="doSubmitGate"
                >
                  {{ gate && gate.cardStatus === 'PENDING' ? '重新提交（已有待确认项）' : '提交视觉门审核' }}
                </el-button>
              </div>
              <p v-if="gate" class="muted small">
                当前视觉阶段：{{ gate.stageDesc }}（{{ gate.stage }}）
                <template v-if="gate.cardId">　确认项卡号：{{ gate.cardId }}</template>
              </p>
              <el-alert
                v-if="gate && !gate.submittable"
                type="warning"
                show-icon
                :closable="false"
                class="mt6"
                title="硬性项未满足，提交会被后端拒绝"
              >
                <ul class="issue-list">
                  <li v-for="(item, index) in gate.blocked" :key="index">{{ item }}</li>
                </ul>
              </el-alert>
              <el-alert
                v-else-if="gate && !gate.passed"
                type="info"
                show-icon
                :closable="false"
                class="mt6"
                title="硬性项已满足，可提交人工确认"
              />
              <el-table v-if="gate" :data="gate.items" size="small" class="mt10">
                <el-table-column label="等级" width="90">
                  <template #default="{ row }">
                    <el-tag :type="asGateItem(row).level === 'BLOCK' ? 'danger' : 'info'" size="small">
                      {{ asGateItem(row).level === 'BLOCK' ? '硬性' : '建议' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="label" label="准入项" width="180" />
                <el-table-column label="结果" width="90">
                  <template #default="{ row }">
                    <span :class="asGateItem(row).passed ? 'good' : 'bad'">
                      {{ asGateItem(row).passed ? '已满足' : '未满足' }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="detail" label="依据 / 说明" min-width="320" show-overflow-tooltip />
              </el-table>
              <div class="review-row mt10">
                <el-input
                  v-model="gateComment"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  placeholder="意见（打回时建议写明要改什么）"
                />
                <div class="review-actions">
                  <el-button
                    type="success"
                    :disabled="!gate || gate.cardStatus !== 'PENDING'"
                    :loading="busy === 'gateConfirm'"
                    @click="doReviewGate('CONFIRM')"
                  >
                    确认方案，允许出图
                  </el-button>
                  <el-button
                    type="danger"
                    plain
                    :disabled="!gate || gate.cardStatus !== 'PENDING'"
                    :loading="busy === 'gateBlock'"
                    @click="doReviewGate('BLOCK')"
                  >
                    打回
                  </el-button>
                </div>
              </div>
              <p v-if="gate && gate.cardStatus !== 'PENDING'" class="muted small">
                当前没有待确认项：{{
                  gate.cardStatus === 'RESOLVED'
                    ? '已确认通过'
                    : gate.cardStatus === 'BLOCKED'
                      ? '已被打回，请修改方案后重新提交'
                      : '请先提交视觉门审核'
                }}
              </p>
            </template>

            <!-- ============ 7. 出图 ============ -->
            <template v-else-if="step.key === 'production'">
              <div class="sub-head">
                <h4>逐屏出图 · 候选 · 双基准质检</h4>
                <div class="head-actions">
                  <el-button size="small" plain :loading="busy === 'refreshProduction'" @click="doRefreshProduction">
                    刷新状态
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :disabled="!storyboard || storyboard.status !== 'LOCKED' || !(gate && gate.passed)"
                    :loading="busy === 'startProduction'"
                    @click="doStartProduction"
                  >
                    按分镜批量出图
                  </el-button>
                </div>
              </div>
              <p class="muted small">
                提示词由已锁定基因按屏派生；失败候选每屏最多自动重试到 3 次尝试（到顶转人工）。
                质检只做减法：<b>不一致的候选会被筛除，一致的也不会自动选定</b>——选定永远是人的动作。
              </p>
              <p class="muted small">
                「产品基准」比的是<b>产品图</b>，「质检」比的是本次出图喂进模型的输入图；两者都可能为 null，
                为 null 时本页如实显示「未质检」，绝不当成通过。
                <b>注：</b>候选接口（DpGenerationVo）当前未回传 product_verdict 列，因此这里目前会稳定显示「未质检」——
                结论存在 dp_generation.product_verdict 里，等后端把该列加进 VO 即可显示，本页不猜、不伪造。
              </p>

              <p v-if="!storyboard" class="empty">还没有分镜，无法出图。先在第 5 步生成并锁定分镜。</p>
              <p v-else-if="storyboard.status !== 'LOCKED'" class="empty">
                分镜还没锁定（{{ storyboard.statusDesc }}）。批量出图以最新分镜为准，建议先在第 5 步锁定。
              </p>
              <p v-else-if="!(gate && gate.passed)" class="empty">
                视觉门还没通过，后端会直接拒绝出图请求。请先在第 6 步提交并由人确认。
              </p>
              <p v-else-if="!screenSlots.length" class="empty">分镜里没有屏，无法批量出图。</p>

              <div v-else class="screen-prod-list">
                <div v-for="slot in screenSlots" :key="String(slot.screenId)" class="prod-card">
                  <div class="prod-head">
                    <span class="screen-no">{{ slot.screenNo }}</span>
                    <span class="screen-type">{{ slot.screenTypeDesc }}</span>
                    <span class="cell-main">{{ slot.title }}</span>
                    <span class="spacer" />
                    <el-tag size="small" :type="slot.selectedId != null ? 'success' : 'warning'">
                      {{ slot.selectedId != null ? '已选定候选' : '待选定' }}
                    </el-tag>
                    <el-button
                      size="small"
                      text
                      type="primary"
                      :loading="busy === 'regen-' + slot.screenId"
                      @click="doRegenerateScreen(slot)"
                    >
                      重出这一屏
                    </el-button>
                  </div>
                  <p v-if="!slot.candidates.length" class="empty small">
                    这一屏还没有候选。点「重出这一屏」单独出，或点上方「按分镜批量出图」。
                  </p>
                  <div v-else class="candidate-grid">
                    <div
                      v-for="gen in slot.candidates"
                      :key="String(gen.id)"
                      class="candidate-card"
                      :class="{ selected: gen.status === 'APPROVED', rejected: gen.status === 'REJECTED' }"
                    >
                      <div class="candidate-cover">
                        <img v-if="urlOf('gen-' + gen.id)" :src="urlOf('gen-' + gen.id)" :alt="`候选 ${gen.candidateNo}`" />
                        <span v-else class="img-placeholder">
                          {{ gen.status === 'RUNNING' || gen.status === 'QUEUED' ? '出图中…' : '暂无产出' }}
                        </span>
                      </div>
                      <div class="candidate-meta">
                        <span class="gen-status" :class="'is-' + genStatusType(gen.status)">
                          #{{ gen.candidateNo }} · {{ gen.statusDesc || genStatusLabel(gen.status) }}
                        </span>
                        <span v-if="gen.outputWidth" class="muted small">
                          {{ gen.outputWidth }}×{{ gen.outputHeight }}
                        </span>
                      </div>
                      <div class="verdict-row">
                        <span :class="qaClass(gen.qaVerdict)">
                          质检：{{ qaVerdictLabel(gen.qaVerdict) }}
                        </span>
                      </div>
                      <div class="verdict-row">
                        <span :class="qaClass(gen.productVerdict)">
                          产品基准：{{ productVerdictLabel(gen.productVerdict) }}
                        </span>
                      </div>
                      <p v-if="gen.errorMessage" class="gen-error" :title="gen.errorMessage">{{ gen.errorMessage }}</p>
                      <div class="candidate-actions">
                        <el-button
                          link
                          size="small"
                          type="success"
                          :disabled="gen.status !== 'SUCCEEDED' && gen.status !== 'APPROVED'"
                          :loading="busy === 'select-' + gen.id"
                          @click="doSelectCandidate(gen)"
                        >
                          选定
                        </el-button>
                        <el-button
                          link
                          size="small"
                          :loading="busy === 'qa-' + gen.id"
                          :disabled="!gen.previewable"
                          @click="doRunQa(gen)"
                        >
                          质检
                        </el-button>
                        <el-button
                          link
                          size="small"
                          type="primary"
                          :disabled="!gen.previewable"
                          @click="openCompare(slot, gen)"
                        >
                          对比产品图
                        </el-button>
                      </div>
                    </div>
                  </div>

                  <div v-if="compareScreenKey === String(slot.screenId) && compareGen" class="compare-box">
                    <div class="compare-head">
                      <b>产品图 | 生成图</b>
                      <span class="muted small">
                        {{ compareGen.typeDesc }} 候选 #{{ compareGen.gen.candidateNo }} ·
                        产品基准：{{ productVerdictLabel(compareGen.gen.productVerdict) }}
                      </span>
                      <span class="spacer" />
                      <el-button link size="small" @click="closeCompare">关闭对比</el-button>
                    </div>
                    <div class="compare-grid">
                      <figure>
                        <img v-if="urlOf('product')" :src="urlOf('product')" alt="产品图" />
                        <span v-else class="img-placeholder">产品图不可用（未配置或读取失败）</span>
                        <figcaption>产品图（基准）</figcaption>
                      </figure>
                      <figure>
                        <img
                          v-if="urlOf('genpreview-' + compareGen.gen.id)"
                          :src="urlOf('genpreview-' + compareGen.gen.id)"
                          alt="生成图"
                        />
                        <span v-else class="img-placeholder">生成图读取中…</span>
                        <figcaption>生成图（候选 #{{ compareGen.gen.candidateNo }}）</figcaption>
                      </figure>
                    </div>
                    <p v-if="!(productImage && productImage.configured)" class="muted small">
                      该产品还没有产品图，左图没有基准可显示——请回第 1 步上传产品照片并勾选「同时设为该产品的产品图」。
                    </p>
                  </div>
                </div>
              </div>
            </template>

            <!-- ============ 8. 详情页排版与终审 ============ -->
            <template v-else-if="step.key === 'layout'">
              <div class="sub-head">
                <h4>详情页排版与终审</h4>
                <div class="head-actions">
                  <el-tag size="small" :type="detailPage && detailPage.rendererAvailable ? 'success' : 'danger'">
                    {{ detailPage && detailPage.rendererAvailable ? '渲染服务可达' : '渲染服务不可达' }}
                  </el-tag>
                  <span class="muted small">
                    模板 {{ detailPage?.templateKey || '—' }} · 当前版本 v{{ detailPage?.currentVersion ?? 0 }}
                    （{{ detailPage?.statusDesc || '未排版' }}）
                  </span>
                  <el-button
                    size="small"
                    plain
                    :disabled="!(gate && gate.passed)"
                    :loading="busy === 'renderDetail'"
                    @click="doRenderDetail"
                  >
                    {{ (detailPage?.currentVersion ?? 0) > 0 ? '重新渲染 V0.8' : '渲染机排版 V0.8' }}
                  </el-button>
                </div>
              </div>
              <p v-if="!(gate && gate.passed)" class="muted small">视觉门还没通过，后端会拒绝渲染——请先完成第 6 步。</p>
              <p class="muted small">
                渲染把每屏<b>已选定</b>的产出与分镜文案排成 750×N 长图；没有已选定产出的屏会在图上明确写出
                「这一屏还没有产出」，不留白糊弄。
              </p>
              <el-alert
                v-if="(detailPage?.screensWithoutSelection || []).length"
                type="warning"
                show-icon
                :closable="false"
                class="mt6"
                :title="`以下屏还没有已选定的产出：${(detailPage?.screensWithoutSelection || []).join('、')}（渲染会在图上标注，但成品不完整）`"
              />
              <el-alert
                v-if="detailPage && detailPage.rendererAvailable === false"
                type="error"
                show-icon
                :closable="false"
                class="mt6"
                title="渲染服务不可达，无法排版（请确认 creative-renderer 容器已启动）"
              />
              <el-table v-if="(detailPage?.versions || []).length" :data="detailPage?.versions || []" size="small" class="mt10">
                <el-table-column label="版本" width="180">
                  <template #default="{ row }">
                    <div class="cell-main">v{{ asVersion(row).version }} · {{ asVersion(row).kindDesc }}</div>
                    <div class="muted small">{{ formatTime(asVersion(row).createTime) }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="长图" width="120">
                  <template #default="{ row }">
                    {{ asVersion(row).pageWidth }}×{{ asVersion(row).pageHeight }}
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="130">
                  <template #default="{ row }">
                    <el-tag size="small" :type="versionStatusType(asVersion(row).status)">
                      {{ LAYOUT_VERSION_STATUS_LABELS[asVersion(row).status || ''] || asVersion(row).status }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="渲染证据 / 审核意见" min-width="240">
                  <template #default="{ row }">
                    <div class="muted small">{{ asVersion(row).remark || '—' }}</div>
                    <div v-if="asVersion(row).reviewComment" class="review-line">
                      审核：{{ asVersion(row).reviewComment }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="230" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      link
                      size="small"
                      type="primary"
                      :disabled="!asVersion(row).previewable"
                      :loading="busy === 'preview-' + asVersion(row).id"
                      @click="doPreviewVersion(asVersion(row))"
                    >
                      预览长图
                    </el-button>
                    <el-button
                      link
                      size="small"
                      type="success"
                      :disabled="asVersion(row).status !== 'RENDERED'"
                      :loading="busy === 'review-' + asVersion(row).id"
                      @click="doReviewVersion(asVersion(row), true)"
                    >
                      通过
                    </el-button>
                    <el-button
                      link
                      size="small"
                      type="danger"
                      :disabled="asVersion(row).status !== 'RENDERED'"
                      :loading="busy === 'review-' + asVersion(row).id"
                      @click="doReviewVersion(asVersion(row), false)"
                    >
                      打回
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <p v-else class="empty">
                还没有排版版本。先通过视觉门、在出图步骤逐屏选定候选，再回到这里渲染。
              </p>
              <div v-if="(detailPage?.currentVersion ?? 0) > 0" class="final-row">
                <div class="final-hint">
                  <b>交付最终版（V1.0）</b>
                  <span class="muted small">
                    设计师在 V0.8 基础上精修后上传长图；上传即登记为新版本并标记交付完成，历史版本全部保留。
                  </span>
                </div>
                <el-upload
                  :show-file-list="false"
                  accept="image/png,image/jpeg"
                  :http-request="doUploadFinal"
                >
                  <el-button :loading="busy === 'uploadFinal'">上传精修最终版</el-button>
                </el-upload>
              </div>
            </template>
          </div>
        </section>
      </div>
    </div>

    <!-- 方向文案编辑 -->
    <el-dialog v-model="directionEditVisible" title="编辑方向文案" width="520px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="directionForm.directionName" maxlength="64" /></el-form-item>
        <el-form-item label="概念">
          <el-input v-model="directionForm.concept" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="directionEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'saveDirection'" @click="doSaveDirection">保存</el-button>
      </template>
    </el-dialog>

    <!-- 单屏编辑 -->
    <el-dialog v-model="screenEditVisible" title="编辑分镜单屏" width="720px">
      <el-form label-width="92px">
        <el-form-item label="标题"><el-input v-model="screenForm.title" maxlength="255" /></el-form-item>
        <el-form-item label="副标题"><el-input v-model="screenForm.subtitle" maxlength="255" /></el-form-item>
        <el-form-item label="正文">
          <el-input v-model="screenForm.bodyText" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
        <el-form-item label="画面独白">
          <el-input
            v-model="screenForm.pictureSoloStatement"
            type="textarea"
            :rows="2"
            maxlength="1000"
            placeholder="这张图不讲文案时，自己要说清什么"
          />
        </el-form-item>
        <el-form-item label="镜头"><el-input v-model="screenForm.shot" maxlength="64" /></el-form-item>
        <el-form-item label="构图"><el-input v-model="screenForm.composition" maxlength="128" /></el-form-item>
        <el-form-item label="光线"><el-input v-model="screenForm.lighting" maxlength="128" /></el-form-item>
        <el-form-item label="背景"><el-input v-model="screenForm.background" maxlength="128" /></el-form-item>
        <el-form-item label="产品保真">
          <el-select v-model="screenForm.productLockLevel" style="width: 100%">
            <el-option label="严格保真（结构与配色不得变）" value="STRICT" />
            <el-option label="允许艺术化（可换场景与角度）" value="LOOSE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="screenEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'saveScreen'" @click="doSaveScreen">保存</el-button>
      </template>
    </el-dialog>

    <!-- 长图预览 -->
    <el-dialog v-model="longPreviewVisible" title="详情页长图预览" width="820px" @closed="closeLongPreview">
      <div class="long-preview">
        <img v-if="longPreviewUrl" :src="longPreviewUrl" alt="详情页长图" />
        <p v-else class="muted">加载中…</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { UploadRequestOptions } from 'element-plus';
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
  bindProjectProductImage,
  fetchCreativeFileBlobUrl,
  fetchDetailPreviewBlobUrl,
  fetchGenerationPreviewBlobUrl,
  fetchGenerationThumbnailBlobUrl,
  fetchProductImageBlobUrl,
  generateDirections,
  generateDna,
  generateStoryboard,
  getCreativeProject,
  getDetailPage,
  getDna,
  getDnaPrompt,
  getProjectProductImage,
  getStoryboard,
  getVisualGate,
  listCreativeFiles,
  listCreativeProject,
  listDirections,
  listDnaVersions,
  listGenerations,
  lockDna,
  lockStoryboard,
  recommendDna,
  refreshProduction,
  regenerateScreen,
  renderDetailPage,
  reviewDetailVersion,
  reviewVisualGate,
  runCandidateQa,
  saveDna,
  selectCandidate,
  selectDirection,
  startProduction,
  submitVisualGate,
  updateDirection,
  updateScreen,
  uploadCreativeReference,
  uploadDetailFinal
} from '@/api/creative';
import type {
  CreativeDnaForm,
  CreativeDirectionForm,
  CreativeProjectVO,
  CreativeScreenForm,
  DnaPromptVO,
  DnaRecommendationVO,
  DpDetailPageVO,
  DpDetailPageVersionVO,
  DpGenerationVO,
  DpStoryboardScreenVO,
  DpStoryboardVO,
  DpVisualDirectionVO,
  DpVisualDnaVO,
  GateEvaluationVO,
  GateItem,
  ProjectProductImageVO,
  TagType
} from '@/api/creative/types';
import {
  CREATIVE_STAGE_LABELS,
  DNA_LIGHTING_DIRS,
  DNA_LIGHTING_TYPES,
  DNA_LEVEL_OPTIONS,
  DNA_SOURCE_LABELS,
  DNA_SOURCE_TYPES,
  FILE_SOURCE_LABELS,
  FILE_SOURCE_TYPES,
  GENERATION_STATUS_LABELS,
  GENERATION_STATUS_TYPES,
  LAYOUT_VERSION_STATUS_LABELS,
  PRODUCT_VERDICT_LABELS,
  QA_VERDICT_LABELS
} from '@/api/creative/types';

// ------------------------------------------------------------------
// 步骤模型
// ------------------------------------------------------------------

type StepStatus = 'done' | 'doing' | 'todo' | 'blocked';

interface StepCard {
  key: string;
  no: number;
  /** 卡片标题（名称） */
  name: string;
  /** 状态 */
  status: StepStatus;
  statusLabel: string;
  /** 一句话概括本步当前的数据情况 */
  summary: string;
  /** 一句「为什么还不能进入下一步」（已完成时说明进入下一步的条件已满足） */
  reason: string;
}

interface ScreenSlot {
  screenId: string | number;
  screenNo: string;
  screenTypeDesc: string;
  title: string;
  candidates: DpGenerationVO[];
  selectedId: string | number | null;
}

/** 步骤推导的中间结果：先算事实（缺什么/是否被阻塞），再折算成展示用的卡片 */
interface StepDraft {
  key: string;
  no: number;
  name: string;
  summary: string;
  /** 现在还缺什么（每条都写清在哪补） */
  missing: string[];
  started: boolean;
  /** 后端会直接拒绝本步动作，或完成条件现在不可能满足 */
  blocked: boolean;
  done: boolean;
}

interface CompareTarget {
  gen: DpGenerationVO;
  typeDesc: string;
}

const STATUS_LABELS: Record<StepStatus, string> = {
  done: '已完成',
  doing: '进行中',
  todo: '未开始',
  blocked: '被阻塞'
};

// ------------------------------------------------------------------
// 状态
// ------------------------------------------------------------------

const projects = ref<CreativeProjectVO[]>([]);
const taskId = ref('');
const project = ref<CreativeProjectVO | null>(null);
const files = ref<CpTaskFileVO[]>([]);
const productImage = ref<ProjectProductImageVO | null>(null);
const facts = ref<CpFactSnapshotVO[]>([]);
const fieldOptions = ref<CpFactFieldOptionVO[]>([]);
const fieldOptionsLoaded = ref(false);
const dna = ref<DpVisualDnaVO | null>(null);
const dnaVersions = ref<DpVisualDnaVO[]>([]);
const dnaPrompt = ref<DnaPromptVO | null>(null);
const recommendation = ref<DnaRecommendationVO | null>(null);
const directions = ref<DpVisualDirectionVO[]>([]);
const storyboard = ref<DpStoryboardVO | null>(null);
const gate = ref<GateEvaluationVO | null>(null);
const generations = ref<DpGenerationVO[]>([]);
const detailPage = ref<DpDetailPageVO | null>(null);

const loading = ref(false);
/** 正在跑的动作（按钮 loading 与互斥用），同时只有一个 */
const busy = ref('');
/** 每步的加载失败原因：取不到就说取不到，不静默留空 */
const stepErrors = ref<Record<string, string>>({});

const asProductImage = ref(false);
const gateComment = ref('');

const dnaForm = reactive<CreativeDnaForm>({});
const directionForm = reactive<CreativeDirectionForm>({ id: '' });
const screenForm = reactive<CreativeScreenForm>({ id: '' });
const manualForm = reactive({ fieldCode: '', value: '', remark: '' });

const directionEditVisible = ref(false);
const screenEditVisible = ref(false);
const longPreviewVisible = ref(false);
const longPreviewUrl = ref('');

const compareScreenKey = ref('');
const compareGen = ref<CompareTarget | null>(null);

/**
 * blob URL 台账：key → URL。
 *
 * 必须是响应式（ref + 展开赋值），普通 Map 的增删不会触发重渲染
 * （表现为「接口全 200、页面上的 <img> 永远不出现」）。卸载时统一 revoke。
 */
const objectUrls = ref<Record<string, string>>({});

// ------------------------------------------------------------------
// 派生数据
// ------------------------------------------------------------------

const imageFiles = computed(() => files.value.filter((f) => (f.fileKind || '').toUpperCase() === 'IMAGE'));

const lockedDna = computed<DpVisualDnaVO | null>(() => {
  const fromVersions = dnaVersions.value.find((item) => item.locked);
  if (fromVersions) return fromVersions;
  return dna.value && dna.value.locked ? dna.value : null;
});

const requiredFieldOptions = computed(() => fieldOptions.value.filter((o) => o.requiredByGate));
const unsatisfiedRequiredOptions = computed(() => requiredFieldOptions.value.filter((o) => !o.satisfied));
const confirmedFacts = computed(() => facts.value.filter((f) => f.confirmStatus === 'CONFIRMED'));

const selectedDirection = computed<DpVisualDirectionVO | null>(
  () => directions.value.find((d) => d.status === 'SELECTED') || null
);

const storyboardLocked = computed(() => storyboard.value?.status === 'LOCKED');
const gatePassed = computed(() => gate.value?.passed === true);

const canBindProductImage = computed(() => project.value?.productId != null && project.value.productId !== '');

/** 屏槽位：把「最新分镜的屏」和「候选」拼在一起（候选来自 listGenerations） */
const screenSlots = computed<ScreenSlot[]>(() => {
  const screens = storyboard.value?.screens || [];
  const grouped = new Map<string, DpGenerationVO[]>();
  for (const gen of generations.value) {
    if (gen.screenId == null) continue;
    const key = String(gen.screenId);
    const list = grouped.get(key) || [];
    list.push(gen);
    grouped.set(key, list);
  }
  return screens.map((screen) => {
    const list = (grouped.get(String(screen.id)) || [])
      .slice()
      .sort((a, b) => (b.candidateNo || 0) - (a.candidateNo || 0));
    const selected = list.find((gen) => gen.status === 'APPROVED');
    return {
      screenId: screen.id,
      screenNo: screen.screenNo || '',
      screenTypeDesc: screen.screenTypeDesc || screen.screenType || '',
      title: screen.title || '',
      candidates: list,
      selectedId: selected ? selected.id : null
    };
  });
});

const allScreensSelected = computed(
  () => screenSlots.value.length > 0 && screenSlots.value.every((slot) => slot.selectedId != null)
);

const unselectedScreenNos = computed(() =>
  screenSlots.value.filter((slot) => slot.selectedId == null).map((slot) => slot.screenNo || String(slot.screenId))
);

const finalVersion = computed<DpDetailPageVersionVO | null>(
  () => (detailPage.value?.versions || []).find((v) => v.kind === 'V10_FINAL') || null
);

const approvedVisionVersion = computed<DpDetailPageVersionVO | null>(
  () => (detailPage.value?.versions || []).find((v) => v.status === 'APPROVED' && v.kind !== 'V10_FINAL') || null
);

const projectStageLabel = computed(() => {
  const stage = project.value?.visualStage;
  if (!stage) return '阶段未知';
  return `${CREATIVE_STAGE_LABELS[stage] || stage}`;
});

/**
 * 八步卡片：状态 + 「为什么还不能进入下一步」全部由真实数据推导。
 *
 * blocked 的口径从严：只有「后端会直接拒绝该动作」或「完成条件现在不可能满足」才算被阻塞，
 * 其余情况说清缺什么即可——不夸大、也不把可做的事说成不能做。
 */
const stepCards = computed<StepCard[]>(() => {
  const cards: StepDraft[] = [];

  // 1. 项目与资料
  const materialMissing: string[] = [];
  if (!taskId.value) materialMissing.push('还没有选择视觉项目');
  else if (!imageFiles.value.length) materialMissing.push('项目里还没有图片附件（产品照片 / 参考图）');
  if (project.value && project.value.productId == null) {
    materialMissing.push('项目没有关联产品，照片无法登记为产品图（产品基准质检会没有基准图）');
  }
  const materialDone = Boolean(taskId.value) && imageFiles.value.length > 0;
  cards.push({
    key: 'material',
    no: 1,
    name: '项目与资料',
    summary: `${files.value.length} 个附件 / ${imageFiles.value.length} 张图片`,
    missing: materialMissing,
    started: Boolean(taskId.value),
    blocked: !taskId.value,
    done: materialDone
  });

  // 2. 事实确认
  const factMissing: string[] = [];
  if (!taskId.value) factMissing.push('还没有选择视觉项目');
  if (fieldOptionsLoaded.value) {
    if (unsatisfiedRequiredOptions.value.length) {
      factMissing.push(
        `必填事实还差 ${unsatisfiedRequiredOptions.value.length} 项：` +
          unsatisfiedRequiredOptions.value.map((o) => o.fieldName || o.fieldCode).join('、')
      );
    }
    if (!requiredFieldOptions.value.length && !confirmedFacts.value.length) {
      factMissing.push('该交付类型没有声明必填事实项，同时也没有任何已确认事实');
    }
  } else if (taskId.value) {
    factMissing.push('字段选项接口没取到，无法判断闸门必填项是否齐备');
  }
  if (!confirmedFacts.value.length && taskId.value && requiredFieldOptions.value.length) {
    factMissing.push('目前 0 条已确认事实');
  }
  // 完成判据：闸门要求项全部已确认；该类型没有要求项时退化为「至少有一条已确认事实」
  const factDone = fieldOptionsLoaded.value
    ? requiredFieldOptions.value.length
      ? unsatisfiedRequiredOptions.value.length === 0
      : confirmedFacts.value.length > 0
    : false;
  cards.push({
    key: 'fact',
    no: 2,
    name: '事实确认',
    summary: `已确认 ${confirmedFacts.value.length} 条 / 共 ${facts.value.length} 行`,
    missing: factMissing,
    started: facts.value.length > 0,
    blocked: !taskId.value,
    done: factDone
  });

  // 3. 视觉基因：完成判据 = 存在已锁定版本
  const dnaMissing: string[] = [];
  if (!taskId.value) dnaMissing.push('还没有选择视觉项目');
  if (!imageFiles.value.length && taskId.value) dnaMissing.push('没有参考图，「按参考图推荐」没有实测依据');
  if (!confirmedFacts.value.length && taskId.value) dnaMissing.push('没有已确认事实，基因与文案只能靠默认规范推导');
  if (dna.value && (dna.value.issues || []).length) {
    dnaMissing.push(`当前版本自洽校验未过：${(dna.value.issues || []).join('；')}`);
  }
  if (dna.value && !dna.value.locked && !(dna.value.issues || []).length) {
    dnaMissing.push('当前版本还没有锁定');
  }
  if (!dna.value && taskId.value) dnaMissing.push('还没有生成视觉基因');
  cards.push({
    key: 'dna',
    no: 3,
    name: '视觉基因',
    summary: dna.value ? `${dna.value.dnaNo} v${dna.value.version} · ${dna.value.statusDesc}` : '还没有基因版本',
    missing: dnaMissing,
    started: dna.value != null,
    blocked: !taskId.value,
    done: lockedDna.value != null
  });

  // 4. 视觉方向：后端要求已锁定基因，否则直接拒绝生成
  const directionMissing: string[] = [];
  if (!lockedDna.value) directionMissing.push('视觉基因还没有锁定版本（后端会拒绝生成方向）');
  if (directions.value.length && !selectedDirection.value) directionMissing.push('还没有在 A/B/C 中选定方向');
  if (!directions.value.length && lockedDna.value) directionMissing.push('还没有生成方向');
  cards.push({
    key: 'direction',
    no: 4,
    name: '视觉方向',
    summary: `${directions.value.length} 个方向${selectedDirection.value ? ' · 已选定 ' + selectedDirection.value.directionCode : ''}`,
    missing: directionMissing,
    started: directions.value.length > 0,
    blocked: !lockedDna.value,
    done: selectedDirection.value != null
  });

  // 5. 分镜：后端要求已锁定基因
  const storyboardMissing: string[] = [];
  if (!lockedDna.value) storyboardMissing.push('视觉基因还没有锁定版本（后端会拒绝生成分镜）');
  if (!storyboard.value && lockedDna.value) storyboardMissing.push('还没有生成分镜');
  if (storyboard.value && !storyboardLocked.value) storyboardMissing.push('当前分镜还是草稿，没有锁定');
  if (!selectedDirection.value) storyboardMissing.push('方向未选定（不影响生成，但分镜不会带上方向的取舍）');
  const screenWithoutSolo = (storyboard.value?.screens || []).filter((s) => !s.pictureSoloStatement);
  if (screenWithoutSolo.length) {
    storyboardMissing.push(
      `${screenWithoutSolo.length} 屏缺少「画面独白」：${screenWithoutSolo.map((s) => s.screenNo).join('、')}`
    );
  }
  cards.push({
    key: 'storyboard',
    no: 5,
    name: '分镜',
    summary: storyboard.value
      ? `${storyboard.value.storyboardNo} v${storyboard.value.version} · ${storyboard.value.statusDesc} · ${storyboard.value.screenCount ?? 0} 屏`
      : '还没有分镜',
    missing: storyboardMissing,
    started: storyboard.value != null,
    blocked: !lockedDna.value,
    done: storyboardLocked.value
  });

  // 6. 视觉门：blocked 直接用后端给出的硬性项，不自己编规则
  const gateMissing: string[] = gate.value ? [...(gate.value.blocked || [])] : ['视觉门评估还没取到'];
  if (gate.value && gate.value.submittable && !gate.value.passed) {
    if (gate.value.cardStatus === 'PENDING') gateMissing.push('已提交，等待人工确认（本页可直接确认/打回）');
    else if (gate.value.cardStatus === 'BLOCKED') gateMissing.push('已被打回，请修改方案后重新提交');
    else gateMissing.push('硬性项已满足，但还没有提交审核');
  }
  cards.push({
    key: 'gate',
    no: 6,
    name: '视觉门',
    summary: gate.value
      ? gate.value.passed
        ? '已通过'
        : `未通过（${gate.value.cardStatus || '未提交'}）`
      : '评估未取到',
    missing: gateMissing,
    started: Boolean(gate.value && (gate.value.cardStatus || gate.value.passed)),
    blocked: Boolean(gate.value) && gate.value.submittable === false,
    done: gatePassed.value
  });

  // 7. 出图：后端要求视觉门已通过
  const prodMissing: string[] = [];
  if (!storyboard.value) prodMissing.push('还没有分镜，无法出图');
  else if (!storyboardLocked.value) prodMissing.push('分镜还没锁定（批量出图以最新分镜为准）');
  if (!gatePassed.value) prodMissing.push('视觉门还没通过（后端会拒绝出图请求）');
  if (storyboard.value && !screenSlots.value.length) prodMissing.push('分镜里没有屏');
  if (unselectedScreenNos.value.length) {
    prodMissing.push(`${unselectedScreenNos.value.length} 屏还没有选定候选：${unselectedScreenNos.value.join('、')}`);
  }
  cards.push({
    key: 'production',
    no: 7,
    name: '出图',
    summary: `${generations.value.length} 个候选 / ${screenSlots.value.length} 屏，已选定 ${screenSlots.value.filter((s) => s.selectedId != null).length} 屏`,
    missing: prodMissing,
    started: generations.value.length > 0,
    blocked: !storyboard.value || !gatePassed.value,
    done: allScreensSelected.value
  });

  // 8. 详情页排版与终审：后端要求视觉门已通过
  const layoutMissing: string[] = [];
  if (!gatePassed.value) layoutMissing.push('视觉门还没通过（后端会拒绝渲染）');
  if (storyboard.value && !screenSlots.value.length) layoutMissing.push('没有分镜屏可排版');
  if (!(detailPage.value?.versions || []).length) layoutMissing.push('还没有渲染过机排版版本');
  else if (unselectedScreenNos.value.length) {
    layoutMissing.push(`渲染前还有 ${unselectedScreenNos.value.length} 屏没有已选定产出（图上会标注为缺失）`);
  }
  if ((detailPage.value?.versions || []).length && !finalVersion.value && !approvedVisionVersion.value) {
    layoutMissing.push('已有版本但还没有终审通过，也没有上传精修最终版');
  }
  if (approvedVisionVersion.value && !finalVersion.value) {
    layoutMissing.push('机排版已终审通过，还差上传精修最终版（V1.0）');
  }
  cards.push({
    key: 'layout',
    no: 8,
    name: '详情页排版与终审',
    summary: detailPage.value
      ? `v${detailPage.value.currentVersion ?? 0} · ${detailPage.value.statusDesc || '未排版'} · ${(detailPage.value.versions || []).length} 个版本`
      : '详情页未取到',
    missing: layoutMissing,
    started: (detailPage.value?.versions || []).length > 0,
    blocked: !gatePassed.value,
    done: finalVersion.value != null
  });

  return cards.map((card, index) => {
    const next = cards[index + 1];
    const status: StepStatus = card.done ? 'done' : card.blocked ? 'blocked' : card.started ? 'doing' : 'todo';
    let reason: string;
    if (card.done) {
      reason = next
        ? `已满足进入第 ${next.no} 步「${next.name}」的条件。`
        : '八个步骤已完成，交付最终版（V1.0）已登记。';
    } else if (card.missing.length) {
      reason = card.missing.join('；') + '。';
    } else {
      reason = '本步还没有可用的数据，请先完成本页上面的动作。';
    }
    return {
      key: card.key,
      no: card.no,
      name: card.name,
      summary: card.summary,
      status,
      statusLabel: STATUS_LABELS[status],
      reason
    };
  });
});

const doneCount = computed(() => stepCards.value.filter((s) => s.status === 'done').length);
const nextStep = computed(() => stepCards.value.find((s) => s.status !== 'done') || null);
const currentKey = ref('material');

// ------------------------------------------------------------------
// 展开 / 定位
// ------------------------------------------------------------------

const expandedKeys = ref<Record<string, boolean>>({});
const expandTouched = ref(false);

function isOpen(key: string): boolean {
  return expandedKeys.value[key] === true;
}

function toggleStep(key: string) {
  expandTouched.value = true;
  expandedKeys.value = { ...expandedKeys.value, [key]: !isOpen(key) };
}

function focusStep(key: string) {
  expandTouched.value = true;
  currentKey.value = key;
  expandedKeys.value = { ...expandedKeys.value, [key]: true };
  const el = document.getElementById('wizard-step-' + key);
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/**
 * 首次进入把「第一个未完成的步骤」展开；此后每次刷新保证它的卡片是打开的。
 *
 * 只增不减：用户自己展开过的卡片不会被刷新动作收起来（否则点一次上传就跳走，很难用）。
 */
function applyAutoExpand() {
  if (expandTouched.value) return;
  const target = nextStep.value;
  if (!target) return;
  if (isOpen(target.key)) {
    currentKey.value = target.key;
    return;
  }
  currentKey.value = target.key;
  expandedKeys.value = { ...expandedKeys.value, [target.key]: true };
}

// ------------------------------------------------------------------
// blob URL 台账
// ------------------------------------------------------------------

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

// ------------------------------------------------------------------
// 展示助手
// ------------------------------------------------------------------

function formatTime(value?: string): string {
  if (!value) return '';
  return value.replace('T', ' ').slice(0, 19);
}

function fileSourceLabel(source?: string): string {
  return (source && FILE_SOURCE_LABELS[source]) || source || '未标注角色';
}

function fileSourceType(source?: string): TagType {
  return (source && FILE_SOURCE_TYPES[source]) || 'info';
}

function genStatusLabel(status?: string): string {
  return (status && GENERATION_STATUS_LABELS[status]) || status || '';
}

function genStatusType(status?: string): string {
  return (status && GENERATION_STATUS_TYPES[status]) || 'info';
}

function qaVerdictLabel(verdict?: string): string {
  if (!verdict) return '未质检';
  return QA_VERDICT_LABELS[verdict] || verdict;
}

/** 产品基准结论：null/空一律显示「未质检」，绝不当成通过 */
function productVerdictLabel(verdict?: string): string {
  if (!verdict) return '未质检';
  return PRODUCT_VERDICT_LABELS[verdict] || verdict;
}

function qaClass(verdict?: string): string {
  if (verdict === 'CONSISTENT') return 'good';
  if (verdict === 'INCONSISTENT') return 'bad';
  if (verdict === 'UNCERTAIN') return 'warn';
  return 'muted';
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

function dnaSourceLabel(source?: string): string {
  return (source && DNA_SOURCE_LABELS[source]) || source || '—';
}

function dnaSourceType(source?: string): TagType {
  return (source && DNA_SOURCE_TYPES[source]) || 'info';
}

function versionStatusType(status?: string): TagType {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'RENDERED') return 'warning';
  return 'info';
}

function strategyKeys(item: DpVisualDirectionVO): string[] {
  return Object.keys(item.strategy || {}).filter((key) => key !== 'schema' && key !== 'differences');
}

function specLabel(key: string): string {
  const map: Record<string, string> = {
    shot: '镜头',
    composition: '构图',
    lighting: '光线',
    background: '背景',
    productRatio: '产品占比',
    whitespace: '留白'
  };
  return map[key] || key;
}

function optionLabel(option: CpFactFieldOptionVO): string {
  const parts = [`${option.fieldName || option.fieldCode}（${option.fieldCode}）`];
  if (option.gateLevel) parts.push(option.gateLevel);
  if (option.satisfied) parts.push('已确认');
  return parts.join(' · ');
}

/** 只有图片附件才能登记成产品图（后端会按图片校验，前端先按住明显不成立的） */
function isBindableImage(file: CpTaskFileVO): boolean {
  const isImage = (file.fileKind || '').toUpperCase() === 'IMAGE';
  const isProduct = file.sourceType === 'PRODUCT';
  return Boolean(canBindProductImage.value && isImage && !isProduct && file.fileId != null);
}

/**
 * el-table 插槽行类型是 DefaultRow，数据其实是我们的 VO；
 * 在模板里显式收窄，而不是把函数参数放宽成 any。
 */
function asFact(row: unknown): CpFactSnapshotVO {
  return row as CpFactSnapshotVO;
}
function asGateItem(row: unknown): GateItem {
  return row as GateItem;
}
function asVersion(row: unknown): DpDetailPageVersionVO {
  return row as DpDetailPageVersionVO;
}

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

/**
 * 失败不抛断整页：记下这一步的错误，页面照实显示。
 *
 * key 用「步骤/接口」两段式：同一步有多个请求时，任何一个失败都不会被另一个的成功覆盖掉。
 */
async function safe<T>(key: string, fn: () => Promise<T>, fallback: T): Promise<T> {
  try {
    const result = await fn();
    if (stepErrors.value[key]) {
      const next = { ...stepErrors.value };
      delete next[key];
      stepErrors.value = next;
    }
    return result;
  } catch (error) {
    const message = (await extractErrorMessage(error)) || '加载失败';
    stepErrors.value = { ...stepErrors.value, [key]: message };
    return fallback;
  }
}

/** 某一步所有请求的失败原因（合并展示，不静默留空） */
function stepError(stepKey: string): string {
  return Object.entries(stepErrors.value)
    .filter(([key]) => key.startsWith(stepKey + '/'))
    .map(([, message]) => message)
    .join('；');
}

// ------------------------------------------------------------------
// 加载
// ------------------------------------------------------------------

async function loadProjects() {
  const res = await listCreativeProject({ pageNum: 1, pageSize: 50 });
  projects.value = res.data?.rows || [];
  const queryTaskId = new URLSearchParams(location.search).get('taskId');
  if (queryTaskId && projects.value.some((p) => String(p.taskId) === queryTaskId)) {
    taskId.value = queryTaskId;
  } else if (!taskId.value && projects.value.length) {
    taskId.value = String(projects.value[0].taskId);
  }
  if (!projects.value.some((p) => String(p.taskId) === String(taskId.value))) {
    taskId.value = projects.value.length ? String(projects.value[0].taskId) : '';
  }
}

async function reload() {
  if (!taskId.value) {
    project.value = null;
    return;
  }
  loading.value = true;
  try {
    const id = taskId.value;
    const [projectRes, fileRes, productRes, factRes, optionRes, dnaRes, dnaVerRes, dirRes, sbRes, gateRes, genRes, detailRes] =
      await Promise.all([
        safe('material/project', () => getCreativeProject(id), null),
        safe('material/files', () => listCreativeFiles(id), null),
        safe('material/productImage', () => getProjectProductImage(id), null),
        safe('fact/list', () => listFact(id), null),
        safe('fact/options', () => factFieldOptions(id), null),
        safe('dna/latest', () => getDna(id), null),
        safe('dna/versions', () => listDnaVersions(id), null),
        safe('direction/list', () => listDirections(id), null),
        safe('storyboard/latest', () => getStoryboard(id), null),
        safe('gate/evaluate', () => getVisualGate(id), null),
        safe('production/generations', () => listGenerations(id), null),
        safe('layout/detailPage', () => getDetailPage(id), null)
      ]);

    project.value = projectRes?.data ?? null;
    files.value = fileRes?.data || [];
    productImage.value = productRes?.data ?? null;
    facts.value = factRes?.data || [];
    fieldOptions.value = optionRes?.data || [];
    fieldOptionsLoaded.value = optionRes != null;
    dna.value = dnaRes?.data ?? null;
    dnaVersions.value = dnaVerRes?.data || [];
    directions.value = dirRes?.data || [];
    storyboard.value = sbRes?.data ?? null;
    gate.value = gateRes?.data ?? null;
    generations.value = genRes?.data || [];
    detailPage.value = detailRes?.data ?? null;

    dnaPrompt.value = null;
    fillDnaForm(dna.value);

    void loadProductImageUrl();
    void loadFileThumbs();
    void loadGenThumbs();
    applyAutoExpand();
  } finally {
    loading.value = false;
  }
}

async function onProjectChange() {
  expandTouched.value = false;
  expandedKeys.value = {};
  compareScreenKey.value = '';
  compareGen.value = null;
  recommendation.value = null;
  asProductImage.value = false;
  Object.keys(objectUrls.value).forEach((key) => releaseUrl(key));
  await reload();
}

async function loadProductImageUrl() {
  if (!taskId.value || !productImage.value?.configured) {
    releaseUrl('product');
    return;
  }
  try {
    setUrl('product', await fetchProductImageBlobUrl(taskId.value));
  } catch (error) {
    releaseUrl('product');
    ElMessage.warning((await extractErrorMessage(error)) ?? '产品图读取失败');
  }
}

async function loadFileThumbs() {
  for (const file of imageFiles.value) {
    if (file.fileId == null) continue;
    const key = 'file-' + file.fileId;
    if (objectUrls.value[key]) continue;
    try {
      setUrl(key, await fetchCreativeFileBlobUrl(taskId.value, file.fileId));
    } catch {
      /* 单张读失败不影响其它 */
    }
  }
}

async function loadGenThumbs() {
  for (const gen of generations.value) {
    const key = 'gen-' + gen.id;
    if (!gen.previewable) {
      releaseUrl(key);
      continue;
    }
    if (objectUrls.value[key]) continue;
    try {
      setUrl(key, await fetchGenerationThumbnailBlobUrl(gen.id));
    } catch {
      /* 缩略图失败留空，点「对比产品图」仍可取原图 */
    }
  }
}

async function loadDnaPrompt() {
  if (!taskId.value) return;
  busy.value = 'dnaPrompt';
  try {
    const res = await getDnaPrompt(taskId.value, 'HERO 主图');
    dnaPrompt.value = res.data || null;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '派生提示词失败');
  } finally {
    busy.value = '';
  }
}

// ------------------------------------------------------------------
// 步骤 1：资料 / 产品图
// ------------------------------------------------------------------

async function doUpload(options: UploadRequestOptions) {
  if (!taskId.value) return;
  busy.value = 'upload';
  try {
    await uploadCreativeReference(taskId.value, options.file as File, asProductImage.value);
    ElMessage.success(asProductImage.value ? '已上传，并已登记为该产品的产品图' : '参考图已上传');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '上传失败');
  } finally {
    busy.value = '';
  }
}

async function doBindProductImage(file: CpTaskFileVO) {
  if (!taskId.value || file.fileId == null) return;
  busy.value = 'bind-' + file.fileId;
  try {
    await bindProjectProductImage(taskId.value, file.fileId);
    ElMessage.success('已设为该产品的产品图');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '设置产品图失败');
  } finally {
    busy.value = '';
  }
}

// ------------------------------------------------------------------
// 步骤 2：事实
// ------------------------------------------------------------------

async function doConfirmFact(row: CpFactSnapshotVO) {
  if (row.snapshotId == null) return;
  busy.value = 'fact-' + row.snapshotId;
  try {
    await confirmFact(row.snapshotId);
    ElMessage.success('已确认该值');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '确认失败');
  } finally {
    busy.value = '';
  }
}

async function doRejectFact(row: CpFactSnapshotVO) {
  if (row.snapshotId == null) return;
  try {
    await ElMessageBox.confirm('否决后该候选值不会被采用，是否继续？', '否决候选值', { type: 'warning' });
  } catch {
    return;
  }
  busy.value = 'fact-' + row.snapshotId;
  try {
    await rejectFact(row.snapshotId);
    ElMessage.success('已否决该候选值');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '否决失败');
  } finally {
    busy.value = '';
  }
}

async function doConfirmUnambiguous() {
  if (!taskId.value) return;
  busy.value = 'confirmUnambiguous';
  try {
    const res = await confirmUnambiguousFacts(taskId.value);
    ElMessage.success(`已确认 ${res.data ?? 0} 条无争议项`);
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '一键确认失败');
  } finally {
    busy.value = '';
  }
}

function openManualFor(fieldCode?: string) {
  if (fieldCode) manualForm.fieldCode = fieldCode;
  focusStep('fact');
}

async function doAddManualFact() {
  if (!taskId.value || !manualForm.fieldCode || !manualForm.value) return;
  busy.value = 'manualFact';
  try {
    await addManualFact({
      taskId: taskId.value,
      fieldCode: manualForm.fieldCode,
      value: manualForm.value,
      remark: manualForm.remark || undefined
    });
    ElMessage.success('已录入，状态为「待确认」——请在事实表里确认后才算数');
    manualForm.value = '';
    manualForm.remark = '';
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '录入失败');
  } finally {
    busy.value = '';
  }
}

// ------------------------------------------------------------------
// 步骤 3：视觉基因
// ------------------------------------------------------------------

function fillDnaForm(source: DpVisualDnaVO | null) {
  dnaForm.id = source?.id;
  dnaForm.styleKeywords = [...(source?.styleKeywords || [])];
  dnaForm.avoidKeywords = [...(source?.avoidKeywords || [])];
  dnaForm.colorPrimary = source?.colors?.primary || '';
  dnaForm.colorSecondary = source?.colors?.secondary || '';
  dnaForm.colorAccent = source?.colors?.accent || '';
  dnaForm.colorBg = source?.colors?.background || '';
  dnaForm.saturation = source?.saturation || '';
  dnaForm.contrastLevel = source?.contrastLevel || '';
  dnaForm.whitespaceLevel = source?.whitespaceLevel || '';
  dnaForm.lightingType = source?.lighting?.type || '';
  dnaForm.lightingDir = source?.lighting?.direction || '';
  dnaForm.productRatioMin = source?.productRatio?.min;
  dnaForm.productRatioMax = source?.productRatio?.max;
  dnaForm.typographyStyle = source?.typographyStyle || '';
  dnaForm.sceneType = source?.sceneType || '';
}

async function doGenerateDna() {
  if (!taskId.value) return;
  busy.value = 'generateDna';
  try {
    const res = await generateDna(taskId.value);
    ElMessage.success(`已生成 v${res.data?.version}，来源：${dnaSourceLabel(res.data?.source)}`);
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '生成视觉基因失败');
  } finally {
    busy.value = '';
  }
}

async function doRecommend() {
  if (!taskId.value) return;
  busy.value = 'recommend';
  try {
    const res = await recommendDna(taskId.value);
    const data = res.data || null;
    recommendation.value = data;
    if (!data?.analyzed) {
      ElMessage.warning((data?.notes || ['没有可用于分析的参考图'])[0]);
      return;
    }
    if (data.colorPrimary) dnaForm.colorPrimary = data.colorPrimary;
    if (data.colorSecondary) dnaForm.colorSecondary = data.colorSecondary;
    if (data.colorAccent) dnaForm.colorAccent = data.colorAccent;
    if (data.colorBg) dnaForm.colorBg = data.colorBg;
    if (data.saturation) dnaForm.saturation = data.saturation;
    if (data.contrastLevel) dnaForm.contrastLevel = data.contrastLevel;
    if (data.whitespaceLevel) dnaForm.whitespaceLevel = data.whitespaceLevel;
    if (data.sceneType) dnaForm.sceneType = data.sceneType;
    if (data.lightingType) dnaForm.lightingType = data.lightingType;
    if (data.lightingDir) dnaForm.lightingDir = data.lightingDir;
    if (data.productRatioMin != null) dnaForm.productRatioMin = data.productRatioMin;
    if (data.productRatioMax != null) dnaForm.productRatioMax = data.productRatioMax;
    ElMessage.success(`已按参考图填好 ${data.evidence?.length ?? 0} 项，确认后点「保存基因」`);
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '按参考图推荐失败');
  } finally {
    busy.value = '';
  }
}

async function doSaveDna() {
  if (!taskId.value) return;
  busy.value = 'saveDna';
  try {
    const wasLocked = dna.value?.locked;
    await saveDna(taskId.value, { ...dnaForm });
    ElMessage.success(wasLocked ? '已基于锁定版新建一版' : '已保存基因');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '保存失败');
  } finally {
    busy.value = '';
  }
}

async function doLockDna() {
  if (!taskId.value || !dna.value) return;
  try {
    await ElMessageBox.confirm(
      '锁定后这一版不可修改（再改会新建版本），并成为后续出图与分镜的依据。确认锁定？',
      '锁定视觉基因',
      { type: 'warning' }
    );
  } catch {
    return;
  }
  busy.value = 'lockDna';
  try {
    await lockDna(taskId.value, dna.value.id);
    ElMessage.success('视觉基因已锁定');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '锁定失败');
  } finally {
    busy.value = '';
  }
}

// ------------------------------------------------------------------
// 步骤 4：视觉方向
// ------------------------------------------------------------------

async function doGenerateDirections() {
  if (!taskId.value) return;
  busy.value = 'generateDirections';
  try {
    await generateDirections(taskId.value);
    ElMessage.success('已生成 A/B/C 三个方向');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '生成方向失败');
  } finally {
    busy.value = '';
  }
}

async function doSelectDirection(item: DpVisualDirectionVO) {
  if (!taskId.value) return;
  busy.value = 'selectDirection-' + item.id;
  try {
    await selectDirection(taskId.value, item.id);
    ElMessage.success(`已选定方向 ${item.directionCode}`);
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '选定失败');
  } finally {
    busy.value = '';
  }
}

function openDirectionEdit(item: DpVisualDirectionVO) {
  directionForm.id = item.id;
  directionForm.directionName = item.directionName;
  directionForm.concept = item.concept;
  directionForm.remark = item.remark;
  directionEditVisible.value = true;
}

async function doSaveDirection() {
  if (!taskId.value) return;
  busy.value = 'saveDirection';
  try {
    await updateDirection(taskId.value, { ...directionForm });
    ElMessage.success('已保存方向文案');
    directionEditVisible.value = false;
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '保存失败');
  } finally {
    busy.value = '';
  }
}

// ------------------------------------------------------------------
// 步骤 5：分镜
// ------------------------------------------------------------------

async function doGenerateStoryboard() {
  if (!taskId.value) return;
  busy.value = 'generateStoryboard';
  try {
    await generateStoryboard(taskId.value);
    ElMessage.success('已生成分镜');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '生成分镜失败');
  } finally {
    busy.value = '';
  }
}

async function doLockStoryboard() {
  if (!taskId.value) return;
  try {
    await ElMessageBox.confirm(
      '锁定后这一版分镜不可修改（重新生成会出新版本），并成为视觉门审核与批量出图的依据。确认锁定？',
      '锁定分镜',
      { type: 'warning' }
    );
  } catch {
    return;
  }
  busy.value = 'lockStoryboard';
  try {
    await lockStoryboard(taskId.value, storyboard.value?.id);
    ElMessage.success('分镜已锁定');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '锁定失败');
  } finally {
    busy.value = '';
  }
}

function openScreenEdit(screen: DpStoryboardScreenVO) {
  screenForm.id = screen.id;
  screenForm.title = screen.title;
  screenForm.subtitle = screen.subtitle;
  screenForm.bodyText = screen.bodyText;
  screenForm.pictureSoloStatement = screen.pictureSoloStatement;
  screenForm.shot = String(screen.spec?.shot ?? '');
  screenForm.composition = String(screen.spec?.composition ?? '');
  screenForm.lighting = String(screen.spec?.lighting ?? '');
  screenForm.background = String(screen.spec?.background ?? '');
  screenForm.workflowCode = screen.workflowCode;
  screenForm.productLockLevel = screen.productLockLevel;
  screenEditVisible.value = true;
}

async function doSaveScreen() {
  if (!taskId.value) return;
  busy.value = 'saveScreen';
  try {
    await updateScreen(taskId.value, { ...screenForm });
    ElMessage.success('已保存该屏文案');
    screenEditVisible.value = false;
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '保存失败');
  } finally {
    busy.value = '';
  }
}

// ------------------------------------------------------------------
// 步骤 6：视觉门
// ------------------------------------------------------------------

async function doSubmitGate() {
  if (!taskId.value) return;
  busy.value = 'submitGate';
  try {
    const res = await submitVisualGate(taskId.value);
    gate.value = res.data || null;
    ElMessage.success('已提交视觉门，等待人工确认');
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '提交失败');
  } finally {
    busy.value = '';
  }
}

async function doReviewGate(option: 'CONFIRM' | 'BLOCK') {
  if (!taskId.value) return;
  if (option === 'BLOCK') {
    try {
      await ElMessageBox.confirm(
        '打回会把视觉方案退回，并阻断内容任务的流转（内容侧会出现一张阻断卡）。确认打回？',
        '打回视觉方案',
        { type: 'warning' }
      );
    } catch {
      return;
    }
  }
  busy.value = option === 'CONFIRM' ? 'gateConfirm' : 'gateBlock';
  try {
    const res = await reviewVisualGate(taskId.value, option, gateComment.value || undefined);
    gate.value = res.data || null;
    ElMessage.success(option === 'CONFIRM' ? '已确认，出图已放行' : '已打回');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '处理失败');
  } finally {
    busy.value = '';
  }
}

// ------------------------------------------------------------------
// 步骤 7：出图
// ------------------------------------------------------------------

async function doStartProduction() {
  if (!taskId.value) return;
  busy.value = 'startProduction';
  try {
    const res = await startProduction(taskId.value, false);
    ElMessage.success(`已提交 ${res.data?.submitted ?? 0} 屏出图，跳过 ${res.data?.skipped ?? 0} 屏`);
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '批量出图失败');
  } finally {
    busy.value = '';
  }
}

async function doRefreshProduction() {
  if (!taskId.value) return;
  busy.value = 'refreshProduction';
  try {
    const res = await refreshProduction(taskId.value);
    ElMessage.success(
      `已刷新：${res.data?.screens?.length ?? 0} 屏状态（含质检回填与失败自动重试）`
    );
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '刷新失败');
  } finally {
    busy.value = '';
  }
}

async function doRegenerateScreen(slot: ScreenSlot) {
  if (!taskId.value) return;
  busy.value = 'regen-' + slot.screenId;
  try {
    await regenerateScreen(taskId.value, slot.screenId);
    ElMessage.success(`${slot.screenNo} 已重新提交出图`);
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '重出失败');
  } finally {
    busy.value = '';
  }
}

async function doSelectCandidate(gen: DpGenerationVO) {
  if (!taskId.value) return;
  busy.value = 'select-' + gen.id;
  try {
    await selectCandidate(taskId.value, gen.id);
    ElMessage.success(`已选定候选 #${gen.candidateNo}，并已登记产出与发起质检`);
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '选定失败');
  } finally {
    busy.value = '';
  }
}

async function doRunQa(gen: DpGenerationVO) {
  if (!taskId.value) return;
  busy.value = 'qa-' + gen.id;
  try {
    await runCandidateQa(taskId.value, gen.id);
    ElMessage.success('已发起质检（只筛除，不放行）');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '发起质检失败');
  } finally {
    busy.value = '';
  }
}

/** 并排对比：左=产品图（新接口），右=生成图原图；都走 blob，不出现对象存储键 */
async function openCompare(slot: ScreenSlot, gen: DpGenerationVO) {
  compareScreenKey.value = String(slot.screenId);
  compareGen.value = { gen, typeDesc: slot.screenTypeDesc };
  if (!productImage.value?.configured) {
    releaseUrl('product');
  } else if (!urlOf('product')) {
    void loadProductImageUrl();
  }
  const key = 'genpreview-' + gen.id;
  if (urlOf(key)) return;
  try {
    setUrl(key, await fetchGenerationPreviewBlobUrl(gen.id));
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取生成图失败');
  }
}

function closeCompare() {
  compareScreenKey.value = '';
  compareGen.value = null;
}

// ------------------------------------------------------------------
// 步骤 8：排版与终审
// ------------------------------------------------------------------

async function doRenderDetail() {
  if (!taskId.value) return;
  busy.value = 'renderDetail';
  try {
    const res = await renderDetailPage(taskId.value);
    detailPage.value = res.data || null;
    const latest = (detailPage.value?.versions || [])[0];
    ElMessage.success(
      latest ? `已渲染 v${latest.version}（${latest.pageWidth}×${latest.pageHeight}）` : '已渲染'
    );
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '渲染失败');
  } finally {
    busy.value = '';
  }
}

async function doPreviewVersion(row: DpDetailPageVersionVO) {
  if (!taskId.value) return;
  busy.value = 'preview-' + row.id;
  longPreviewVisible.value = true;
  try {
    const url = await fetchDetailPreviewBlobUrl(taskId.value, row.id);
    if (longPreviewUrl.value) URL.revokeObjectURL(longPreviewUrl.value);
    longPreviewUrl.value = url;
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '读取长图失败');
  } finally {
    busy.value = '';
  }
}

function closeLongPreview() {
  if (longPreviewUrl.value) URL.revokeObjectURL(longPreviewUrl.value);
  longPreviewUrl.value = '';
}

async function doReviewVersion(row: DpDetailPageVersionVO, approve: boolean) {
  if (!taskId.value) return;
  if (!approve) {
    try {
      await ElMessageBox.confirm('打回后这一版需要重新渲染或修改。确认打回？', '终审打回', { type: 'warning' });
    } catch {
      return;
    }
  }
  busy.value = 'review-' + row.id;
  try {
    await reviewDetailVersion(taskId.value, row.id, approve, approve ? '终审通过' : '终审打回');
    ElMessage.success(approve ? '已通过，可进入人工精修' : '已打回');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '终审失败');
  } finally {
    busy.value = '';
  }
}

async function doUploadFinal(options: UploadRequestOptions) {
  if (!taskId.value) return;
  busy.value = 'uploadFinal';
  try {
    await uploadDetailFinal(taskId.value, options.file as File, '人工精修最终版');
    ElMessage.success('最终版已上传并登记为 V1.0');
    await reload();
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '上传失败');
  } finally {
    busy.value = '';
  }
}

// ------------------------------------------------------------------
// 生命周期
// ------------------------------------------------------------------

onMounted(async () => {
  try {
    await loadProjects();
    await reload();
    if (project.value) {
      ElMessage.info(`已载入「${project.value.taskName}」，当前阶段：${projectStageLabel.value}`);
    }
  } catch (error) {
    ElMessage.error((await extractErrorMessage(error)) ?? '初始化失败');
  }
});

onBeforeUnmount(() => {
  Object.values(objectUrls.value).forEach((url) => URL.revokeObjectURL(url));
  objectUrls.value = {};
  if (longPreviewUrl.value) URL.revokeObjectURL(longPreviewUrl.value);
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
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.wizard {
  display: grid;
  grid-template-columns: minmax(200px, 240px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

/* 左：步骤导航 */
.rail {
  position: sticky;
  top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.rail-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 8px;
  margin-bottom: 4px;
  font-size: 13px;
  border-bottom: 1px solid var(--line);
}
.rail-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 7px 8px;
  font: inherit;
  font-size: 13px;
  color: var(--t1);
  text-align: left;
  cursor: pointer;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.rail-item:hover {
  border-color: var(--line2);
}
.rail-item.active {
  border-color: #7c3aed;
}
.rail-item.is-done {
  border-left: 3px solid #10b981;
}
.rail-item.is-doing {
  border-left: 3px solid #6366f1;
}
.rail-item.is-blocked {
  border-left: 3px solid #ef4444;
}
.rail-item.is-todo {
  border-left: 3px solid rgba(148, 163, 184, 0.4);
}
.rail-no {
  display: grid;
  place-items: center;
  flex: 0 0 20px;
  width: 20px;
  height: 20px;
  font-size: 11px;
  color: #fff;
  background: rgba(124, 58, 237, 0.85);
  border-radius: 50%;
}
.rail-name {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.rail-status {
  font-size: 11px;
  color: var(--t3);
}
.rail-warn {
  padding: 8px;
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #fde68a;
  background: rgba(245, 158, 11, 0.12);
  border-radius: 6px;
}

/* 右：步骤卡 */
.steps {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.panel {
  padding: 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
}
.step-card.is-done {
  border-left: 3px solid #10b981;
}
.step-card.is-doing {
  border-left: 3px solid #6366f1;
}
.step-card.is-blocked {
  border-left: 3px solid #ef4444;
}

.step-head {
  display: flex;
  gap: 10px;
  align-items: center;
  cursor: pointer;
}
.step-head h3 {
  margin: 0;
  font-size: 15px;
}
.step-no {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-radius: 6px;
}
.step-head .spacer {
  flex: 1;
}

.status-tag {
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 10px;
  border: 1px solid transparent;
}
.status-tag.is-done {
  color: #a7f3d0;
  background: rgba(16, 185, 129, 0.16);
  border-color: rgba(16, 185, 129, 0.4);
}
.status-tag.is-doing {
  color: #c7d2fe;
  background: rgba(99, 102, 241, 0.16);
  border-color: rgba(99, 102, 241, 0.4);
}
.status-tag.is-blocked {
  color: #fecaca;
  background: rgba(239, 68, 68, 0.16);
  border-color: rgba(239, 68, 68, 0.4);
}
.status-tag.is-todo {
  color: var(--t2);
  background: rgba(148, 163, 184, 0.14);
  border-color: var(--line2);
}

.why {
  padding: 8px 10px;
  margin: 10px 0 0;
  font-size: 12.5px;
  line-height: 1.8;
  border-radius: 6px;
  background: var(--sunken);
  border-left: 2px solid var(--line2);
}
.why.is-done {
  color: #a7f3d0;
  border-left-color: #10b981;
}
.why.is-doing {
  color: #c7d2fe;
  border-left-color: #6366f1;
}
.why.is-blocked {
  color: #fecaca;
  border-left-color: #ef4444;
}
.why.is-todo {
  color: var(--t2);
}

.load-error {
  padding: 8px 10px;
  margin: 8px 0 0;
  font-size: 12.5px;
  line-height: 1.8;
  color: #fecaca;
  background: rgba(239, 68, 68, 0.12);
  border-left: 2px solid #ef4444;
  border-radius: 0 6px 6px 0;
}

.step-body {
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--line);
}

.loading-panel {
  font-size: 13px;
  line-height: 1.9;
  color: var(--t2);
}

.grid-2 {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.sub-panel {
  padding: 12px;
  margin-bottom: 12px;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.sub-panel:last-child {
  margin-bottom: 0;
}
.sub-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.sub-head h4 {
  margin: 0;
  font-size: 14px;
}

.product-image-box {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.product-image-box img {
  width: 160px;
  height: 160px;
  object-fit: contain;
  background: #05070a;
  border: 1px solid var(--line);
  border-radius: 6px;
}
.product-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12.5px;
  color: var(--t2);
}

.hint-block p {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.8;
  color: #fde68a;
}
.hint-block p.muted {
  color: var(--t3);
}

.file-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.file-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 148px;
  padding: 8px;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.file-cover {
  display: grid;
  place-items: center;
  height: 100px;
  overflow: hidden;
  background: #05070a;
  border-radius: 4px;
}
.file-cover img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.file-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.file-name {
  overflow: hidden;
  font-size: 11.5px;
  color: var(--t2);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.img-placeholder {
  display: grid;
  place-items: center;
  padding: 8px;
  font-size: 12px;
  color: var(--t3);
  text-align: center;
}

.check-list {
  padding: 0;
  margin: 0;
  list-style: none;
  font-size: 13px;
  line-height: 2;
}
.check-list li {
  display: flex;
  gap: 8px;
  align-items: center;
  color: #fde68a;
}
.check-list li.ok {
  color: #a7f3d0;
}
.check-list li .mark {
  width: 14px;
  font-weight: 700;
}
.check-list li .check-name {
  min-width: 110px;
}
.fill-btn {
  margin: 8px 8px 0 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px 16px;
}
.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-item.span2 {
  grid-column: span 2;
}
.form-item > label {
  font-size: 12px;
  color: var(--t2);
}

.form-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.form-row > label {
  font-size: 12.5px;
  color: var(--t2);
}

.save-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.prompt-box {
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--line);
}

.dna-title {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.dna-title h4 {
  margin: 0;
  font-size: 14px;
}

.locked-note {
  padding: 8px 10px;
  margin-top: 10px;
  font-size: 12.5px;
  line-height: 1.8;
  color: #a7f3d0;
  background: rgba(16, 185, 129, 0.1);
  border-left: 2px solid #10b981;
  border-radius: 0 6px 6px 0;
}
.dna-hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.8;
  color: #a5b4fc;
}

.issue-list {
  padding-left: 18px;
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.9;
}
.mini-title {
  margin: 0 0 4px;
  font-size: 13px;
}

.direction-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}
.direction-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.direction-card.selected {
  border-color: #10b981;
  box-shadow: inset 0 0 0 1px rgba(16, 185, 129, 0.35);
}
.direction-card.rejected {
  opacity: 0.62;
}
.direction-head {
  display: flex;
  gap: 8px;
  align-items: center;
}
.direction-head .code {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-radius: 6px;
}
.direction-head .name {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
}
.concept {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--t2);
}
.strategy-list {
  padding: 0;
  margin: 0;
  list-style: none;
  font-size: 12px;
  line-height: 1.9;
}
.strategy-list li {
  display: flex;
  gap: 8px;
}
.strategy-list .key {
  flex: 0 0 84px;
  color: var(--t3);
}
.strategy-list .key.diff {
  color: #fde68a;
}
.strategy-list .value {
  flex: 1;
  color: var(--t1);
}
.direction-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.screen-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 12px;
}
.screen-card {
  display: flex;
  flex-direction: column;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.screen-head {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}
.screen-head .spacer {
  flex: 1;
}
.screen-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: #c7d2fe;
}
.screen-type {
  font-size: 13px;
  font-weight: 600;
}
.screen-body {
  padding: 12px;
}
.screen-body h4 {
  margin: 0 0 6px;
  font-size: 14px;
}
.body-text {
  margin: 6px 0;
  font-size: 13px;
  line-height: 1.8;
}
.solo {
  padding: 8px 10px;
  margin: 8px 0;
  font-size: 12px;
  line-height: 1.8;
  color: #ddd6fe;
  background: rgba(124, 58, 237, 0.12);
  border-left: 2px solid rgba(124, 58, 237, 0.6);
  border-radius: 0 4px 4px 0;
}
.spec-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--t2);
}
.spec-item b {
  margin-right: 4px;
  font-weight: 500;
  color: var(--t3);
}

.review-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.review-row .el-textarea {
  flex: 1;
}
.review-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.screen-prod-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.prod-card {
  padding: 12px;
  background: var(--elevated);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.prod-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}
.prod-head .spacer {
  flex: 1;
}

.candidate-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
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
.candidate-card.selected {
  border-color: #10b981;
  box-shadow: inset 0 0 0 1px rgba(16, 185, 129, 0.35);
}
.candidate-card.rejected {
  opacity: 0.7;
}
.candidate-cover {
  display: grid;
  place-items: center;
  height: 170px;
  overflow: hidden;
  background: #05070a;
  border-radius: 4px;
}
.candidate-cover img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.candidate-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  font-size: 12px;
}
.verdict-row {
  font-size: 12px;
  line-height: 1.7;
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
  flex-wrap: wrap;
  gap: 4px;
}

.compare-box {
  padding: 12px;
  margin-top: 12px;
  background: var(--surface);
  border: 1px dashed var(--line2);
  border-radius: 6px;
}
.compare-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
}
.compare-head .spacer {
  flex: 1;
}
.compare-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}
.compare-grid figure {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  margin: 0;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.compare-grid img {
  width: 100%;
  height: 300px;
  object-fit: contain;
  background: #05070a;
  border-radius: 4px;
}
.compare-grid figcaption {
  font-size: 12px;
  color: var(--t2);
  text-align: center;
}

.final-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--line);
}
.final-hint {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}

.long-preview {
  display: grid;
  place-items: center;
  max-height: 70vh;
  overflow-y: auto;
}
.long-preview img {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.cell-main {
  font-size: 13px;
}
.review-line {
  margin-top: 4px;
  font-size: 12px;
  color: #a5b4fc;
}

.good {
  color: #a7f3d0;
}
.bad {
  color: #fca5a5;
}
.warn {
  color: #fde68a;
}

.muted {
  color: var(--t2);
}
.small {
  font-size: 12px;
}
.mt6 {
  margin-top: 6px;
}
.mt8 {
  margin-top: 8px;
}
.mt10 {
  margin-top: 10px;
}
code {
  padding: 1px 4px;
  font-size: 12px;
  background: var(--sunken);
  border-radius: 3px;
}

.empty {
  padding: 12px 0;
  margin: 0;
  font-size: 13px;
  line-height: 1.9;
  color: var(--t2);
}
.empty.small {
  font-size: 12.5px;
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
.studio :deep(th.el-table__cell) {
  color: var(--t2);
  background: var(--elevated);
  border-bottom-color: var(--line);
}
.studio :deep(td.el-table__cell) {
  color: var(--t1);
  background: transparent;
  border-bottom-color: var(--line);
}
.studio :deep(.el-table),
.studio :deep(.el-table tr) {
  background: transparent;
}
</style>
