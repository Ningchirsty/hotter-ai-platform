<template>
  <div class="p-2 app-container content-task-page">
    <PageHeading
      title="内容生产协同"
      subtitle="按任务组织：上传资料 → 解析预检 → 人确认事实 → 过闸门 → 出开工包"
      module="content"
    />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>任务检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="任务号" prop="queryTaskNo">
            <el-input
              v-model="queryParams.queryTaskNo"
              placeholder="请输入任务号"
              clearable
              style="width: 180px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="任务名称" prop="queryTaskName">
            <el-input
              v-model="queryParams.queryTaskName"
              placeholder="请输入任务名称"
              clearable
              style="width: 200px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="交付类型" prop="queryDeliverableType">
            <el-select
              v-model="queryParams.queryDeliverableType"
              placeholder="请选择交付类型"
              clearable
              style="width: 170px"
            >
              <el-option
                v-for="dict in cp_deliverable_type"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="queryStatus">
            <el-select v-model="queryParams.queryStatus" placeholder="请选择状态" clearable style="width: 160px">
              <el-option v-for="dict in cp_task_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="产品" prop="productId">
            <el-select v-model="queryParams.productId" placeholder="请选择产品" clearable filterable style="width: 200px">
              <el-option
                v-for="p in productList"
                :key="String(p.productId)"
                :label="p.productName || p.productCode"
                :value="p.productId!"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="负责人">
            <el-input
              v-model="queryOwnerLabel"
              placeholder="选择负责人"
              readonly
              clearable
              style="width: 180px"
              @click="openQueryOwnerSelect"
              @clear="handleClearQueryOwner"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Content Tasks</span>
            <h3>内容任务</h3>
            <p>共 {{ total }} 条记录；点击「详情」进入任务的资料、事实、卡片与开工包。</p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['content:task:add']" type="primary" plain icon="Plus" @click="handleAdd">
              新增
            </el-button>
            <el-button
              v-hasPermi="['content:task:edit']"
              type="success"
              plain
              icon="Edit"
              :disabled="single"
              @click="handleUpdate()"
            >
              修改
            </el-button>
            <el-button
              v-hasPermi="['content:task:remove']"
              type="danger"
              plain
              icon="Delete"
              :disabled="multiple"
              @click="handleDelete()"
            >
              删除
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        border
        class="data-table"
        :data="taskList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="任务号" align="center" prop="taskNo" width="170" show-overflow-tooltip />
        <el-table-column label="任务名称" align="center" prop="taskName" min-width="170" show-overflow-tooltip />
        <el-table-column label="交付类型" align="center" width="120">
          <template #default="scope">
            <dict-tag :options="cp_deliverable_type" :value="scope.row.deliverableType" />
          </template>
        </el-table-column>
        <el-table-column label="产品" align="center" prop="productName" width="150" show-overflow-tooltip />
        <el-table-column label="负责人" align="center" prop="ownerName" width="110" show-overflow-tooltip />
        <el-table-column label="截止时间" align="center" prop="deadline" width="170">
          <template #default="scope">
            <span>{{ parseTime(scope.row.deadline) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="130">
          <template #default="scope">
            <dict-tag :options="cp_task_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <!--
          视觉进度（内测 S6 / 冲突 B 的最佳建议）：品牌部需要"图做到哪了"。
          它是**只读**展示——内容侧的 status 回答的是"资料齐不齐"，两者正交，所以分两列。
        -->
        <el-table-column label="视觉进度" align="center" width="130">
          <template #default="scope">
            <el-tag v-if="scope.row.visualStage" :type="visualStageType(scope.row.visualStage)" size="small">
              {{ visualStageLabel(scope.row.visualStage) }}
            </el-tag>
            <span v-else class="muted">未进入</span>
          </template>
        </el-table-column>
        <el-table-column label="待处理卡数" align="center" width="110">
          <template #default="scope">
            <el-tag v-if="scope.row.pendingCardCount" type="warning" size="small">
              {{ scope.row.pendingCardCount }}
            </el-tag>
            <span v-else>0</span>
          </template>
        </el-table-column>
        <el-table-column label="阻断卡数" align="center" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.blockingCardCount" type="danger" size="small">
              {{ scope.row.blockingCardCount }}
            </el-tag>
            <span v-else>0</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['content:task:query']" link type="primary" @click="openDetail(scope.row)">
              详情
            </el-button>
            <el-tooltip content="修改" placement="top">
              <el-button
                v-hasPermi="['content:task:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                v-hasPermi="['content:task:remove']"
                link
                type="primary"
                icon="Delete"
                @click="handleDelete(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <!-- 新增/修改任务 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="780px" append-to-body>
      <el-form ref="taskFormRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="任务名称" prop="taskName">
              <el-input v-model="form.taskName" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交付类型" prop="deliverableType">
              <el-select v-model="form.deliverableType" placeholder="请选择交付类型" style="width: 100%">
                <el-option
                  v-for="dict in cp_deliverable_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="产品" prop="productId">
              <el-select
                v-model="form.productId"
                placeholder="请选择产品"
                clearable
                filterable
                style="width: 100%"
                @change="handleFormProductChange"
              >
                <el-option
                  v-for="p in productList"
                  :key="String(p.productId)"
                  :label="p.productName || p.productCode"
                  :value="p.productId!"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SKU编码" prop="skuCode">
              <el-input v-model="form.skuCode" placeholder="留空则取所选产品的SKU（保存时自动带出）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="selectedProduct"
          class="product-hint"
          type="info"
          :closable="false"
          show-icon
          :title="'已选产品：' + (selectedProduct.productName || '—') + '（' + (selectedProduct.productCode || '—') + '）'"
        >
          <div class="product-hint-line">
            品牌 {{ selectedProduct.brand || '—' }} · 二级分类 {{ selectedProduct.subCategory || '—' }} · 产品经理
            {{ selectedProduct.productManager || '—' }} · 产品SKU {{ selectedProduct.skuCode || '—' }}
          </div>
          <div class="product-hint-line muted">
            保存后会自动把「产品名称」「SKU」按产品与SKU主数据写入为已确认事实（来源可追溯）；
            主体版本、颜色、数量、参数、包装版本不在该模块中，仍需来自产品资料或手工录入。
          </div>
        </el-alert>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="截止时间" prop="deadline">
              <el-date-picker
                v-model="form.deadline"
                type="datetime"
                placeholder="请选择截止时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人" prop="ownerName">
              <el-input
                v-model="form.ownerName"
                placeholder="选择任务负责人（互动卡默认指派人）"
                readonly
                @click="openFormOwnerSelect"
              >
                <template #append>
                  <el-button icon="User" @click="openFormOwnerSelect"></el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="资料敏感级别" prop="dataLevel">
          <el-radio-group v-model="form.dataLevel">
            <el-radio v-for="dict in aig_data_level" :key="dict.value" :value="dict.value">
              {{ dict.label }}
            </el-radio>
          </el-radio-group>
          <div class="form-tip">默认内部级；限制级资料默认禁止外发，阶段1A 的解析一律本地执行。</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入内容" />
        </el-form-item>

        <!-- 品牌要求（Brief）：默认折叠，摘要显示当前状态；随任务一并保存 -->
        <el-collapse v-model="briefCollapse" class="form-collapse">
          <el-collapse-item name="brief">
            <template #title>
              <div class="collapse-title">
                <span>品牌要求（Brief）</span>
                <el-tag :type="formBriefStatusType" size="small" effect="plain">{{ formBriefStatusLabel }}</el-tag>
              </div>
            </template>
            <p class="form-tip collapse-tip">
              品牌方提的「必须怎么做」。必显信息与主推卖点会进 AI 视觉工厂出图的正向提示词、禁用词进负向提示词；
              品牌调性 / 目标人群 / 尺寸规范 / 参考风格<b>当前版本没有任何下游消费</b>（不进出图、不参与渲染），仅记录备查。
              此处留空则不提交品牌要求（任务仍会保存）。
            </p>
            <el-form-item v-for="field in briefFields" :key="field.key" :label="field.label">
              <el-input
                v-model="formBrief[field.key]"
                type="textarea"
                :rows="field.rows"
                :maxlength="field.max"
                show-word-limit
                :placeholder="field.placeholder"
              />
              <!-- 弹窗里不做上传：图片要挂在已存在的任务附件上，任务还没保存就没有 taskId -->
              <div v-if="field.key === 'styleRef'" class="form-tip">
                参考风格图片请保存任务后在<b>任务详情 →「品牌要求（Brief）」</b>里上传（那里能直接传图并即时保存）。
              </div>
              <div class="form-tip">{{ field.hint }}</div>
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 任务详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="drawerTitle"
      size="74%"
      append-to-body
      destroy-on-close
      @close="handleDrawerClosed"
    >
      <div v-loading="detailLoading" class="task-detail">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="任务号">{{ taskInfo.taskNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="交付类型">
            <dict-tag :options="cp_deliverable_type" :value="taskInfo.deliverableType" />
          </el-descriptions-item>
          <el-descriptions-item label="任务状态">
            <dict-tag :options="cp_task_status" :value="taskInfo.status" />
          </el-descriptions-item>
          <el-descriptions-item label="产品">{{ taskInfo.productName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ taskInfo.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="截止时间">{{ parseTime(taskInfo.deadline) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="资料敏感级别">
            <dict-tag :options="aig_data_level" :value="taskInfo.dataLevel" />
          </el-descriptions-item>
          <el-descriptions-item label="解析完成时间">
            {{ parseTime(taskInfo.parseDoneAt) || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="阻断原因">{{ taskInfo.blockReason || '无' }}</el-descriptions-item>
          <el-descriptions-item label="视觉进度">
            <el-tag v-if="taskInfo.visualStage" :type="visualStageType(taskInfo.visualStage)" size="small">
              {{ visualStageLabel(taskInfo.visualStage) }}
            </el-tag>
            <span v-else>未进入视觉工厂</span>
          </el-descriptions-item>
        </el-descriptions>
        <p class="muted small">
          「任务状态」说的是<b>资料齐不齐、能不能开工</b>；「视觉进度」说的是<b>图做到哪了</b>。
          两个问题正交，所以这里是<b>只读展示</b>——视觉侧的进度不会改回任务状态，
          否则"资料就绪度"会随着制作进度漂移，闸门判定与审计都会失真。
        </p>

        <div class="action-bar">
          <el-button
            v-hasPermi="['content:task:edit']"
            type="primary"
            plain
            icon="Upload"
            :loading="acting"
            @click="handleParse"
          >
            触发解析
          </el-button>
          <el-button
            v-hasPermi="['content:task:edit']"
            type="primary"
            plain
            icon="Search"
            :loading="acting"
            @click="handlePrecheck"
          >
            触发预检
          </el-button>
          <el-button
            v-hasPermi="['content:task:edit']"
            type="warning"
            plain
            icon="Refresh"
            :loading="acting"
            @click="handleRecheck"
          >
            重算闸门
          </el-button>
          <el-button icon="RefreshRight" :loading="detailLoading" @click="loadDetail()">刷新</el-button>
          <span v-if="polling" class="polling-tip">作业执行中，每 3 秒自动刷新…</span>
        </div>

        <!-- 闸门结论 -->
        <el-card shadow="never" class="detail-card">
          <template #header>
            <div class="card-head">
              <span class="panel-kicker">Gate Conclusion</span>
              <h3>闸门结论</h3>
              <div class="gate-head">
                <dict-tag :options="cp_task_status" :value="gate.status" />
                <span v-if="gate.blockReason" class="gate-reason">{{ gate.blockReason }}</span>
              </div>
            </div>
          </template>

          <!-- R38-5 / P1-3：「没配规则」不能长得跟「校验通过」一样。
               生产实测只有 ECOM_DETAIL 与 EXHIBITION 灌了规则；其余交付类型的任务直接到
               「可开工」，但那是**没人校验过**。这里如实把这件事说出来——
               文案里的去向（「闸门规则」页）由后端 hint 给出，不在前端拼路由（内容侧路由由菜单树生成，
               硬写路径一旦对不上就是"点了跳到空白页"）。 -->
          <el-alert
            v-if="gate.rulesConfigured === false"
            type="warning"
            show-icon
            :closable="false"
            class="gate-no-rules"
            title="该交付类型尚未配置闸门规则——下面的「可开工」是「没有规则可校验」，不是「校验通过」"
          >
            <p class="muted">{{ gate.rulesMissingHint }}</p>
          </el-alert>

          <div class="gate-block">
            <div class="block-title danger">
              未满足的强制项（{{ (gate.blockUnsatisfied || []).length }}）
            </div>
            <el-table
              v-if="(gate.blockUnsatisfied || []).length"
              border
              size="small"
              :data="gate.blockUnsatisfied"
              class="inner-table"
            >
              <el-table-column label="字段名称" align="center" prop="fieldName" width="180" show-overflow-tooltip />
              <el-table-column label="字段编码" align="center" prop="fieldCode" width="200" show-overflow-tooltip />
              <el-table-column label="闸门等级" align="center" width="120">
                <template #default="scope">
                  <dict-tag :options="cp_gate_level" :value="scope.row.gateLevel" />
                </template>
              </el-table-column>
              <el-table-column label="是否必须存在" align="center" width="120">
                <template #default="scope">{{ scope.row.requirePresent === 'N' ? '可缺失' : '必须存在' }}</template>
              </el-table-column>
              <el-table-column label="操作" align="center" width="130">
                <template #default="scope">
                  <el-button
                    v-hasPermi="['content:task:edit']"
                    link
                    type="primary"
                    icon="Plus"
                    @click="openManualDialogFor(scope.row)"
                  >
                    录入该字段
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <p v-else class="muted">无未满足的强制项。</p>
          </div>

          <div class="gate-block">
            <div class="block-title warning">
              未满足的条件项（{{ (gate.conditionUnsatisfied || []).length }}）
            </div>
            <el-table
              v-if="(gate.conditionUnsatisfied || []).length"
              border
              size="small"
              :data="gate.conditionUnsatisfied"
              class="inner-table"
            >
              <el-table-column label="字段名称" align="center" prop="fieldName" width="180" show-overflow-tooltip />
              <el-table-column label="字段编码" align="center" prop="fieldCode" width="200" show-overflow-tooltip />
              <el-table-column label="闸门等级" align="center" width="120">
                <template #default="scope">
                  <dict-tag :options="cp_gate_level" :value="scope.row.gateLevel" />
                </template>
              </el-table-column>
              <el-table-column label="说明" align="center">
                <template #default>当前可开工，但必须补齐或确定替代方案（会写入开工包缺口）</template>
              </el-table-column>
              <el-table-column label="操作" align="center" width="130">
                <template #default="scope">
                  <el-button
                    v-hasPermi="['content:task:edit']"
                    link
                    type="primary"
                    icon="Plus"
                    @click="openManualDialogFor(scope.row)"
                  >
                    录入该字段
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <p v-else class="muted">无未满足的条件项。</p>
          </div>

          <div class="gate-block">
            <div class="block-title">非阻断提醒项（{{ (gate.notices || []).length }}）</div>
            <div v-if="(gate.notices || []).length" class="tag-bar">
              <el-tag v-for="n in gate.notices" :key="String(n.ruleId)" type="info" effect="plain">
                {{ n.fieldName || n.fieldCode }}
              </el-tag>
            </div>
            <p v-else class="muted">无非阻断提醒项。</p>
          </div>
        </el-card>

        <!-- 品牌要求（Brief）：品牌部在这里录入并确认；AI 视觉工厂那边只读 -->
        <!-- id 是深链的落点：视觉门「去哪儿补」会带 ?taskId=…&section=brief 过来，直接滚到这张卡 -->
        <el-card id="detail-brief" shadow="never" class="detail-card">
          <template #header>
            <div class="card-head">
              <span class="panel-kicker">Brand Brief</span>
              <h3>品牌要求（Brief）</h3>
              <p>
                品牌方提的「必须怎么做」。去向（按实际接线如实写）：<b>必显信息</b>与<b>主推卖点</b>进 AI 视觉工厂出图的<b>正向提示词</b>，
                <b>禁用词</b>进<b>负向提示词</b>；<b>品牌调性 / 目标人群 / 尺寸规范 / 参考风格</b>当前版本
                <b>没有任何下游消费</b>，仅记录备查。
                平面设计部在 AI 视觉工厂按此创作，<b>那边只读</b>，要改就在这里改。
              </p>
            </div>
          </template>

          <div class="brief-bar">
            <el-tag :type="briefStatusTagType" size="small" effect="dark">{{ briefStatusLabel }}</el-tag>
            <span v-if="briefEditing && briefDirty" class="brief-dirty">已修改未保存</span>
            <div class="brief-bar-actions">
              <template v-if="!briefEditing">
                <el-button
                  v-hasPermi="['content:brief:edit']"
                  size="small"
                  type="primary"
                  plain
                  :loading="briefBusy === 'load'"
                  @click="startEditBrief"
                >
                  编辑
                </el-button>
                <el-button
                  v-hasPermi="['content:brief:confirm']"
                  size="small"
                  plain
                  :loading="briefBusy === 'confirm'"
                  @click="confirmBrief"
                >
                  品牌方确认
                </el-button>
                <el-button size="small" plain :loading="briefBusy === 'load'" @click="loadBrief(detailTaskId)">
                  刷新
                </el-button>
              </template>
              <template v-else>
                <el-button size="small" type="primary" :loading="briefBusy === 'save'" @click="saveBrief">
                  保存
                </el-button>
                <el-button size="small" @click="cancelEditBrief">取消</el-button>
              </template>
            </div>
          </div>

          <p v-if="briefError" class="brief-error">{{ briefError }}</p>

          <!-- 只读展示：有值按多行原样显示，空值显示 —（不编造默认值） -->
          <div v-if="!briefEditing" class="brief-grid">
            <div v-for="field in briefFields" :key="field.key" class="brief-row">
              <label>{{ field.label }}</label>
              <div class="brief-value" :class="{ 'is-empty': !briefValueOf(field.key) }">
                {{ briefValueOf(field.key) || '—' }}
              </div>
            </div>
            <!-- 参考风格图片：只读态也展示（品牌方给的图，设计要照着做） -->
            <div class="brief-row">
              <label>参考风格图片</label>
              <div class="brief-control">
                <BriefStyleImages
                  :task-id="detailTaskId"
                  :images="briefStyleImages"
                  :editable="false"
                />
              </div>
            </div>
          </div>

          <!-- 编辑态：8 个字段，长度上限与数据库列宽一致 -->
          <el-form v-else label-width="120px" class="brief-form">
            <el-form-item v-for="field in briefFields" :key="field.key" :label="field.label">
              <el-input
                v-model="briefForm[field.key]"
                type="textarea"
                :rows="field.rows"
                :maxlength="field.max"
                show-word-limit
                :placeholder="field.placeholder"
              />
              <!-- 参考风格：文字描述 + 直接传图（上传即保存，不用再点一次保存） -->
              <div v-if="field.key === 'styleRef'" class="brief-style-upload">
                <BriefStyleImages
                  :task-id="detailTaskId"
                  :images="briefStyleImages"
                  :editable="checkPermi(['content:brief:edit'])"
                  :busy="briefBusy === 'style'"
                  @upload="onStyleImagePick"
                  @remove="removeStyleImage"
                />
              </div>
              <div class="form-tip">{{ field.hint }}</div>
            </el-form-item>
          </el-form>

          <p v-if="!briefEditing && briefLoaded && !brief?.configured" class="muted">
            还没有录过品牌要求。点「编辑」填写后保存；确认无误再点「品牌方确认」，确认后视觉工厂那边即可见。
          </p>
        </el-card>

        <!-- 附件 -->
        <el-card shadow="never" class="detail-card">
          <template #header>
            <div class="card-head">
              <span class="panel-kicker">Task Files</span>
              <h3>资料附件</h3>
              <p>图片与旧版 .doc 本期不做 OCR，会以「已跳过」并给出可读原因。</p>
            </div>
          </template>

          <div v-hasPermi="['content:task:edit']" class="upload-bar">
            <el-upload
              ref="uploadRef"
              drag
              :limit="1"
              :auto-upload="false"
              :file-list="uploadFileList"
              :on-change="handleUploadChange"
              :on-exceed="handleUploadExceed"
              :on-remove="handleUploadRemove"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
              <template #tip>
                <div class="el-upload__tip">支持 Excel / Word(docx) / PDF；图片与旧版 .doc 仅归档。</div>
              </template>
            </el-upload>
            <div class="upload-side">
              <el-select v-model="uploadDataLevel" placeholder="文件敏感级别（默认随任务）" clearable style="width: 230px">
                <el-option v-for="dict in aig_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
              <el-button type="primary" :loading="uploading" @click="submitUpload">上传附件</el-button>
            </div>
          </div>

          <el-table v-if="(files || []).length" border size="small" :data="files" class="inner-table">
            <el-table-column label="文件名" align="center" prop="fileName" min-width="200" show-overflow-tooltip />
            <el-table-column label="类型" align="center" width="100">
              <template #default="scope">
                <dict-tag :options="cp_file_kind" :value="scope.row.fileKind" />
              </template>
            </el-table-column>
            <el-table-column label="大小" align="center" width="100">
              <template #default="scope">{{ formatSize(scope.row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="敏感级别" align="center" width="110">
              <template #default="scope">
                <dict-tag :options="aig_data_level" :value="scope.row.dataLevel" />
              </template>
            </el-table-column>
            <el-table-column label="解析状态" align="center" width="110">
              <template #default="scope">
                <dict-tag :options="cp_parse_status" :value="scope.row.parseStatus" />
              </template>
            </el-table-column>
            <el-table-column label="解析说明" align="center" prop="parseMessage" min-width="180" show-overflow-tooltip />
            <el-table-column label="上传时间" align="center" width="170">
              <template #default="scope">{{ parseTime(scope.row.createTime) || '-' }}</template>
            </el-table-column>
          </el-table>
          <p v-else class="muted">尚未上传资料。</p>
        </el-card>

        <!-- 异步作业 -->
        <el-card shadow="never" class="detail-card">
          <template #header>
            <div class="card-head">
              <span class="panel-kicker">Async Jobs</span>
              <h3>异步作业</h3>
              <p>解析与预检在应用内执行器串行执行，进度与失败原因在此展示。</p>
            </div>
          </template>
          <el-table v-if="(jobs || []).length" border size="small" :data="jobs" class="inner-table">
            <el-table-column label="作业ID" align="center" prop="jobId" width="100" />
            <el-table-column label="类型" align="center" width="110">
              <template #default="scope">
                <el-tag size="small" type="info">{{ jobTypeLabel(scope.row.jobType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" align="center" width="110">
              <template #default="scope">
                <el-tag size="small" :type="jobStatusType(scope.row.status)">
                  {{ jobStatusLabel(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" align="center" width="180">
              <template #default="scope">
                <el-progress :percentage="scope.row.progress || 0" :stroke-width="10" />
              </template>
            </el-table-column>
            <el-table-column label="说明" align="center" prop="message" min-width="180" show-overflow-tooltip />
            <el-table-column label="开始时间" align="center" width="170">
              <template #default="scope">{{ parseTime(scope.row.startedAt) || '-' }}</template>
            </el-table-column>
            <el-table-column label="结束时间" align="center" width="170">
              <template #default="scope">{{ parseTime(scope.row.finishedAt) || '-' }}</template>
            </el-table-column>
          </el-table>
          <p v-else class="muted">暂无作业记录。</p>
        </el-card>

        <!-- 事实清单 -->
        <!-- id 是深链的落点：视觉门「去哪儿补」会带 ?taskId=…&section=facts 过来 -->
        <el-card id="detail-facts" shadow="never" class="detail-card">
          <template #header>
            <div class="card-head-row">
              <div class="card-head">
                <span class="panel-kicker">Fact Snapshots</span>
                <h3>事实清单（{{ (facts || []).length }}）</h3>
                <p>解析结果一律以待确认落库；只有人工确认的值才能进入开工包。</p>
              </div>
              <div class="toolbar-actions">
                <el-button
                  v-hasPermi="['content:task:edit']"
                  type="primary"
                  plain
                  icon="Select"
                  :disabled="!detailTaskId"
                  @click="handleConfirmUnambiguous"
                >
                  一键确认无争议项
                </el-button>
                <el-button
                  v-hasPermi="['content:task:edit']"
                  plain
                  icon="Refresh"
                  :disabled="!detailTaskId"
                  :loading="syncingProductFacts"
                  @click="handleSyncProductFacts"
                >
                  同步产品信息
                </el-button>
                <el-button v-hasPermi="['content:task:edit']" plain icon="Plus" @click="openManualDialog">
                  手工录入事实
                </el-button>
              </div>
            </div>
          </template>

          <el-table v-if="(facts || []).length" border size="small" :data="facts" class="inner-table">
            <el-table-column label="字段" align="center" prop="fieldName" width="150" show-overflow-tooltip />
            <el-table-column label="值" align="center" prop="fieldValue" width="150" show-overflow-tooltip />
            <el-table-column label="单位" align="center" prop="unit" width="80" />
            <el-table-column label="来源文件" align="center" prop="sourceFileName" width="160" show-overflow-tooltip />
            <el-table-column label="来源定位" align="center" prop="sourceLocator" width="170" show-overflow-tooltip />
            <el-table-column label="原文摘录" align="center" prop="sourceExcerpt" min-width="180" show-overflow-tooltip />
            <el-table-column label="快照版本" align="center" prop="snapshotVersion" width="100" />
            <el-table-column label="确认状态" align="center" width="110">
              <template #default="scope">
                <el-tag size="small" :type="confirmStatusType(scope.row.confirmStatus)">
                  {{ confirmStatusLabel(scope.row.confirmStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-button
                  v-hasPermi="['content:task:edit']"
                  link
                  type="primary"
                  :disabled="scope.row.confirmStatus === 'CONFIRMED'"
                  @click="handleConfirmFact(scope.row)"
                >
                  确认
                </el-button>
                <el-button
                  v-hasPermi="['content:task:edit']"
                  link
                  type="danger"
                  :disabled="scope.row.confirmStatus === 'REJECTED'"
                  @click="handleRejectFact(scope.row)"
                >
                  否决
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <p v-else class="muted">暂无事实候选；请先上传资料并触发解析。</p>
        </el-card>

        <!-- 互动卡 -->
        <el-card shadow="never" class="detail-card">
          <template #header>
            <div class="card-head">
              <span class="panel-kicker">Interaction Cards</span>
              <h3>互动确认卡（{{ (cards || []).length }}）</h3>
              <p>证据摆全，由人裁定；系统不判断哪个取值正确。</p>
            </div>
          </template>
          <el-table v-if="(cards || []).length" border size="small" :data="cards" class="inner-table">
            <el-table-column label="类型" align="center" width="100">
              <template #default="scope">
                <dict-tag :options="cp_card_type" :value="scope.row.cardType" />
              </template>
            </el-table-column>
            <el-table-column label="问题" align="center" prop="title" min-width="200" show-overflow-tooltip />
            <el-table-column label="闸门等级" align="center" width="110">
              <template #default="scope">
                <dict-tag :options="cp_gate_level" :value="scope.row.gateLevel" />
              </template>
            </el-table-column>
            <el-table-column label="责任人" align="center" prop="assigneeName" width="100" show-overflow-tooltip />
            <el-table-column label="截止时间" align="center" width="170">
              <template #default="scope">{{ parseTime(scope.row.dueAt) || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" align="center" width="110">
              <template #default="scope">
                <dict-tag :options="cp_card_status" :value="scope.row.status" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-button v-hasPermi="['content:card:handle']" link type="primary" @click="openCardDialog(scope.row)">
                  查看处理
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <p v-else class="muted">暂无互动卡。</p>
        </el-card>

        <!-- 开工包 -->
        <el-card shadow="never" class="detail-card">
          <template #header>
            <div class="card-head-row">
              <div class="card-head">
                <span class="panel-kicker">Work Package</span>
                <h3>设计开工包</h3>
                <p v-if="workPackage">
                  包状态：<b>{{ workPackage.status === 'ISSUED' ? '已签发' : '草稿' }}</b>；冻结事实版本：V{{
                    workPackage.snapshotVersion ?? '-'
                  }}；生成时间：{{ parseTime(workPackage.generatedAt) || '-' }}；签发时间：{{
                    parseTime(workPackage.issuedAt) || '-'
                  }}
                </p>
                <p v-else>尚未生成开工包；需先满足强制项（可开工或条件开工）。</p>
                <!-- C5①：开工包已定位为**跨部门交接凭证**，设计侧在视觉项目页能读到它（只读区块） -->
                <p class="form-tip">
                  <b>这份包是跨部门交接凭证</b>：签发后，平面设计部在
                  <b>AI 视觉工厂 → 视觉项目页的「开工包」区块</b>能读到它（只读）。
                  包里的<b>品牌红线（禁用词）与必显信息取自「品牌要求（Brief）」</b>，
                  所以填 Brief 就是在给设计侧立红线；缺口与不可修改项也一并交接过去。
                  包一旦签发即冻结，后续改 Brief 不会自动改动它——需要以最新要求为准时请重新生成并签发。
                </p>
              </div>
              <div class="toolbar-actions">
                <el-button
                  v-hasPermi="['content:package:generate']"
                  type="primary"
                  plain
                  icon="Refresh"
                  :loading="generatingPackage"
                  @click="handleGeneratePackage"
                >
                  生成开工包
                </el-button>
                <el-button
                  v-if="workPackage && workPackage.status !== 'ISSUED'"
                  v-hasPermi="['content:package:issue']"
                  type="success"
                  plain
                  icon="Select"
                  :loading="issuingPackage"
                  @click="handleIssuePackage"
                >
                  签发开工包
                </el-button>
              </div>
            </div>
          </template>

          <div v-if="packageContent" class="package-summary">
            <div class="summary-item">
              <span class="summary-label">已确认事实</span>
              <span class="summary-value">{{ packageContent.confirmedFacts?.length || 0 }} 条</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">缺口</span>
              <span class="summary-value">{{ packageContent.gaps?.length || 0 }} 项</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">允许的 AI 动作</span>
              <span class="summary-value">{{ packageContent.allowedAiActions?.length || 0 }} 项</span>
            </div>
            <div class="summary-item wide">
              <span class="summary-label">不可修改项</span>
              <span class="summary-value">
                <el-tag v-for="item in packageContent.immutableItems || []" :key="item" type="danger" effect="plain" class="tag-gap">
                  {{ item }}
                </el-tag>
                <span v-if="!(packageContent.immutableItems || []).length">-</span>
              </span>
            </div>
            <div class="summary-item wide">
              <span class="summary-label">输出规格</span>
              <span class="summary-value">
                {{ packageContent.spec?.outputSize || '-' }} / {{ packageContent.spec?.resolution || '-' }} /
                {{ packageContent.spec?.acceptance || '-' }}
              </span>
            </div>
            <div class="summary-item wide">
              <span class="summary-label">负责人与截止</span>
              <span class="summary-value">
                {{ packageContent.owner?.ownerName || '-' }} / {{ packageContent.deadline || '-' }}
              </span>
            </div>
          </div>
          <p v-else-if="workPackage" class="muted">开工包内容不是合法 JSON，请到「设计开工包」页面查看原文。</p>
          <p v-else class="muted">暂无开工包内容。</p>
        </el-card>
      </div>
    </el-drawer>

    <!-- 手工录入事实 -->
    <el-dialog v-model="manualDialog.visible" title="手工录入事实" width="600px" append-to-body>
      <el-form ref="manualFormRef" :model="manualForm" :rules="manualRules" label-width="100px">
        <el-form-item label="事实字段" prop="fieldCode">
          <el-select
            v-if="!manualCustomMode"
            v-model="manualForm.fieldCode"
            placeholder="请选择要录入的事实字段"
            filterable
            style="width: 100%"
          >
            <el-option-group v-if="gateFieldOptions.length" label="本交付类型的闸门要求项">
              <el-option v-for="o in gateFieldOptions" :key="o.fieldCode" :label="optionLabel(o)" :value="o.fieldCode!" />
            </el-option-group>
            <el-option-group v-if="otherFieldOptions.length" label="其他已登记字段（不影响本任务闸门）">
              <el-option v-for="o in otherFieldOptions" :key="o.fieldCode" :label="optionLabel(o)" :value="o.fieldCode!" />
            </el-option-group>
          </el-select>
          <el-input v-else v-model="manualForm.fieldCode" placeholder="请输入已在「闸门规则」中登记过的字段编码" />
          <div class="manual-tip">
            <el-button link type="primary" @click="manualCustomMode = !manualCustomMode">
              {{ manualCustomMode ? '改为从列表选择（推荐）' : '改为自定义编码' }}
            </el-button>
            <span class="muted">闸门只认与闸门规则完全一致的编码；编码写错会出现「事实录进去了、闸门却不动」。</span>
          </div>
          <div v-if="manualSelectedOption?.description" class="manual-desc">
            {{ manualSelectedOption.description }}
          </div>
        </el-form-item>
        <el-form-item label="字段值" prop="value">
          <el-input v-model="manualForm.value" placeholder="请输入经责任人确认的值" />
        </el-form-item>
        <el-form-item label="事实出处" prop="sourceFileId">
          <el-select
            v-model="manualForm.sourceFileId"
            placeholder="必选：这条值是从哪份任务资料里看到的"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="f in sourceFileOptions"
              :key="String(f.fileId)"
              :label="sourceFileLabel(f)"
              :value="f.fileId!"
            />
          </el-select>
          <el-input
            v-model="manualForm.sourceLocator"
            class="source-locator-input"
            placeholder="可选：资料里的位置，如「第 3 行」「第 2 页参数表」"
          />
          <div class="form-tip">
            出处会随开工包交给下游。<b>必须选一份本任务的资料</b>（不能只写"见资料"）——
            手工录入的值直接标记为「已确认」，出处要能被点开核对。
          </div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="manualForm.remark" type="textarea" :rows="2" placeholder="为什么以此值为准（可选）" />
        </el-form-item>
        <div class="form-tip">手工录入的值直接标记为「已确认」，请确认它来自经确认的产品资料。</div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="manualSaving" @click="submitManual">确 定</el-button>
          <el-button @click="manualDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 互动卡处理 -->
    <el-dialog v-model="cardDialog.visible" title="处理互动确认卡" width="720px" append-to-body>
      <div v-if="currentCard" class="card-process">
        <div class="detail-block">
          <div class="block-title">问题</div>
          <p class="detail-text">{{ currentCard.question || currentCard.title || '-' }}</p>
        </div>
        <div class="detail-block">
          <div class="block-title">来源证据</div>
          <template v-if="cardEvidence.length">
            <div v-for="(ev, idx) in cardEvidence" :key="idx" class="evidence-item">
              <div class="evidence-head">
                <el-tag size="small" type="info">{{ ev.sourceFileName || '未知文件' }}</el-tag>
                <span class="evidence-locator">{{ ev.locator || '未标注定位' }}</span>
                <span v-if="ev.value" class="evidence-value">取值：{{ ev.value }}</span>
              </div>
              <div v-if="ev.excerpt" class="evidence-excerpt">摘录：{{ ev.excerpt }}</div>
            </div>
          </template>
          <p v-else class="muted">该卡暂无来源摘录。</p>
        </div>
        <div class="detail-block">
          <div class="block-title">影响对象</div>
          <div v-if="cardImpact.length" class="tag-bar">
            <el-tag v-for="(im, idx) in cardImpact" :key="idx" size="small" type="warning">
              {{ im.deliverableTypeName || im.deliverableType }} · {{ im.fieldName }}
            </el-tag>
            <span class="evidence-excerpt">{{ cardImpact[0]?.note }}</span>
          </div>
          <p v-else class="muted">未标注影响对象。</p>
        </div>
        <div class="detail-block">
          <div class="block-title">责任与时限</div>
          <div class="meta-row">
            <span>责任人：{{ currentCard.assigneeName || '-' }}</span>
            <span>截止：{{ parseTime(currentCard.dueAt) || '-' }}</span>
            <span>闸门等级：{{ gateLevelLabel(currentCard.gateLevel) }}</span>
            <span>是否阻断：{{ currentCard.blocking === 'Y' ? '是' : '否' }}</span>
          </div>
        </div>

        <template v-if="currentCard.status === 'PENDING'">
          <div class="detail-block">
            <div class="block-title">处理</div>
            <div class="option-bar">
              <el-button
                v-for="(opt, idx) in cardOptions"
                :key="idx"
                v-hasPermi="['content:card:handle']"
                :type="optionButtonType(opt.option)"
                :disabled="cardResolving"
                @click="submitCardOption(opt)"
              >
                {{ opt.label || opt.option }}
              </el-button>
            </div>
            <div v-if="cardOtherActive" class="other-input">
              <el-input v-model="cardOtherValue" placeholder="请填写确认值" style="max-width: 380px" />
              <el-select
                v-model="cardOtherFileId"
                placeholder="必选：事实出处（本任务的哪份资料）"
                filterable
                style="max-width: 380px"
              >
                <el-option
                  v-for="f in sourceFileOptions"
                  :key="String(f.fileId)"
                  :label="sourceFileLabel(f)"
                  :value="f.fileId!"
                />
              </el-select>
              <el-input
                v-model="cardOtherLocator"
                placeholder="可选：资料里的位置，如「第 3 行」"
                style="max-width: 380px"
              />
              <el-button
                v-hasPermi="['content:card:handle']"
                type="primary"
                :disabled="cardResolving"
                @click="submitCardOther"
              >
                提交其他值
              </el-button>
              <el-button @click="cancelOther">取消</el-button>
            </div>
            <div class="form-tip">确认或阻断后，任务闸门会立即重算。</div>
          </div>
        </template>
        <div v-else class="detail-block">
          <div class="block-title">处理结果</div>
          <div class="meta-row">
            <span>所选选项：{{ currentCard.resolvedOption || '-' }}</span>
            <span>确认值：{{ currentCard.resolvedValue || '-' }}</span>
            <span>处理时间：{{ parseTime(currentCard.resolvedAt) || '-' }}</span>
          </div>
          <p v-if="currentCard.remark" class="detail-text">处理说明：{{ currentCard.remark }}</p>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cardDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 用户选择器（查询负责人 / 表单负责人） -->
    <UserSelect ref="queryUserSelectRef" :multiple="false" @confirm-call-back="handleQueryOwnerSelected" />
    <UserSelect ref="userSelectRef" :multiple="false" @confirm-call-back="handleFormOwnerSelected" />
  </div>
</template>

<script setup lang="ts">
import type { CardEvidenceItem, CardImpactItem, CardOptionItem, CpInteractionCardVO } from '@/api/content/card/types';
import type { CpFactFieldOptionVO, CpFactSnapshotVO } from '@/api/content/fact/types';
import type { CpProductVO } from '@/api/content/product/types';
import type {
  CpAsyncJobVO,
  CpTaskFileVO,
  CpTaskForm,
  CpTaskQuery,
  CpTaskVO,
  ContentTaskDetailVO
} from '@/api/content/task/types';
import type { WorkPackageContent } from '@/api/content/workPackage/types';
import { taskStatusLabel } from '@/api/content/task/status';
import { resolveCard } from '@/api/content/card';
// 品牌要求（Brief）：归属内容生产协同，品牌部在这里录入与确认（AI 视觉工厂只读）
import { confirmBrandBrief, getBrandBrief, saveBrandBrief } from '@/api/content/brief';
import type { BrandBriefFieldKey, BrandBriefImage, BrandBriefVO } from '@/api/content/brief/types';
import {
  BRAND_BRIEF_FIELDS,
  BRAND_BRIEF_STATUS_TYPES,
  BRAND_BRIEF_STYLE_MAX,
  briefFormHasContent,
  briefStatusText,
  emptyBrandBriefForm,
  formToBriefPayload,
  joinStyleRefFileIds,
  parseStyleRefFileIds
} from '@/api/content/brief/types';
// 参考风格图片条：与视觉项目页共用同一个组件（展示逻辑与 blob 回收只维护一处）
import BriefStyleImages from '@/components/BriefStyleImages/index.vue';
// 视觉阶段的中文字典：**复用创作域那一份**，不在这里另抄一张表。
// 抄一份的代价不是多打几个字，而是两张表迟早对不上（内测 S11 就是英文枚举直接漏到界面）。
import { CREATIVE_STAGE_LABELS, CREATIVE_STAGE_TYPES } from '@/api/creative/types';
import type { TagType } from '@/api/creative/types';
import { checkPermi } from '@/utils/permission';
import { addManualFact, confirmFact, confirmUnambiguousFacts, factFieldOptions, rejectFact } from '@/api/content/fact';
import { productOptions } from '@/api/content/product';
import {
  addTask,
  delTask,
  getTask,
  listTask,
  recheckGate,
  syncProductFacts,
  triggerParse,
  triggerPrecheck,
  updateTask,
  uploadTaskFile
} from '@/api/content/task';
import { generateWorkPackage, issueWorkPackage } from '@/api/content/workPackage';
import UserSelect from '@/components/UserSelect/index.vue';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { extractErrorMessage } from '@/utils/request';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'ContentTask' });

const {
  cp_deliverable_type,
  cp_task_status,
  cp_card_type,
  cp_card_status,
  cp_gate_level,
  cp_file_kind,
  cp_parse_status,
  aig_data_level
} = toRefs<any>(
  useDict(
    'cp_deliverable_type',
    'cp_task_status',
    'cp_card_type',
    'cp_card_status',
    'cp_gate_level',
    'cp_file_kind',
    'cp_parse_status',
    'aig_data_level'
  )
);

// ---------------------------------------------------------------- 品牌要求（Brief）

/**
 * 品牌要求由**品牌部**在这里录入并确认，AI 视觉工厂那边只读。
 *
 * <p>状态语义（后端定的，页面如实照做）：保存草稿**不会**把「已确认」打回「草稿」——
 * 确认权在品牌方，改完内容要重新点「品牌方确认」，页面必须把这句说清楚，
 * 否则会出现"我改了但别人看到的还是已确认"的误解。</p>
 */
const briefFields = BRAND_BRIEF_FIELDS;
const briefCollapse = ref<string[]>([]);
const brief = ref<BrandBriefVO | null>(null);
const briefLoaded = ref(false);
const briefError = ref('');
/** '' | 'load' | 'save' | 'confirm'：一个字符串状态位，避免多个布尔互相打架 */
const briefBusy = ref('');
const briefEditing = ref(false);
const briefForm = reactive<Record<BrandBriefFieldKey, string>>(emptyBrandBriefForm());
/** 编辑前的内容快照：用来判断「已修改未保存」（只比内容，不看是否加载成功） */
const briefSavedSnapshot = ref('');
/** 表单弹窗里的品牌要求（与详情抽屉各自独立，避免一处编辑影响另一处） */
const formBrief = reactive<Record<BrandBriefFieldKey, string>>(emptyBrandBriefForm());
/** 打开弹窗时该任务是否已存在品牌要求记录：决定"8 项全空"时是否仍然提交 */
const formBriefExisted = ref(false);
/** 弹窗里的当前状态（未填写 / 草稿 / 已确认），折叠标题上用 */
const formBriefStatus = ref<BrandBriefVO | null>(null);

/**
 * 参考风格图片的当前状态：`briefStyleFiles` 是**页面认的真相**（保存时按它提交），
 * `briefStyleImages` 是服务端解析出来的明细（缩略图与文件名用）。
 *
 * <p>为什么两份都留：保存要的是 ID 串（唯一、可比），展示要的是文件名；
 * 只用明细会把"服务端解析不到的文件名"当成"这张图不存在"。
 * 每次保存后用服务端返回覆盖两份，页面不做本地猜测。</p>
 */
const briefStyleFiles = ref<string[]>([]);
const briefStyleImages = ref<BrandBriefImage[]>([]);

/** 用服务端返回同步"参考风格图片"两份状态 */
const applyBriefStyleFiles = (data?: BrandBriefVO | null) => {
  briefStyleFiles.value = parseStyleRefFileIds(data?.styleRefFiles);
  briefStyleImages.value = data?.styleRefImages || [];
};

const briefSnapshotOf = (form: Record<BrandBriefFieldKey, string>) =>
  JSON.stringify(briefFields.map(field => (form[field.key] ?? '').trim()));

const briefDirty = computed(() => briefSnapshotOf(briefForm) !== briefSavedSnapshot.value);

const briefStatusLabel = computed(() => briefStatusText(brief.value, briefLoaded.value, v => parseTime(v) || ''));

const briefStatusTagType = computed<ElTagType>(() => {
  if (!briefLoaded.value || !brief.value?.configured) return 'info';
  return BRAND_BRIEF_STATUS_TYPES[brief.value.status || 'DRAFT'] || 'warning';
});

const formBriefStatusLabel = computed(() =>
  briefStatusText(formBriefStatus.value, true, v => parseTime(v) || '')
);

const formBriefStatusType = computed<ElTagType>(() => {
  if (!formBriefStatus.value?.configured) return 'info';
  return BRAND_BRIEF_STATUS_TYPES[formBriefStatus.value.status || 'DRAFT'] || 'warning';
});

/** 详情里某个字段的展示值（空值由模板显示成 —） */
const briefValueOf = (key: BrandBriefFieldKey): string => ((brief.value?.[key] as string | undefined) ?? '').trim();

const fillBriefForm = (target: Record<BrandBriefFieldKey, string>, data?: BrandBriefVO | null) => {
  briefFields.forEach(field => {
    target[field.key] = (data?.[field.key] as string | undefined) ?? '';
  });
};

/**
 * 读品牌要求（内容域接口）。
 *
 * <p>读失败**不静默**：置 briefError 并在卡片里显示原因——空表不代表品牌部没有提要求。</p>
 */
const loadBrief = async (taskId?: string | number) => {
  if (!taskId) return;
  briefBusy.value = 'load';
  briefError.value = '';
  try {
    const res = await getBrandBrief(taskId);
    // 切任务后迟到的响应不写进页面
    if (String(detailTaskId.value || '') !== String(taskId)) return;
    brief.value = res.data ?? null;
    briefLoaded.value = true;
    briefEditing.value = false;
    fillBriefForm(briefForm, brief.value);
    applyBriefStyleFiles(brief.value);
    briefSavedSnapshot.value = briefSnapshotOf(briefForm);
  } catch (error) {
    if (String(detailTaskId.value || '') !== String(taskId)) return;
    brief.value = null;
    briefLoaded.value = false;
    briefError.value =
      '品牌要求没取到（' + ((await extractErrorMessage(error)) ?? '接口失败') + '），下面的空值不代表品牌部没有提要求';
  } finally {
    if (String(detailTaskId.value || '') === String(taskId)) briefBusy.value = '';
  }
};

const startEditBrief = () => {
  fillBriefForm(briefForm, brief.value);
  briefSavedSnapshot.value = briefSnapshotOf(briefForm);
  briefEditing.value = true;
};

const cancelEditBrief = async () => {
  if (briefDirty.value) {
    try {
      await modal.confirm('有未保存的改动，取消后这些改动会丢失。是否继续？');
    } catch {
      return;
    }
  }
  fillBriefForm(briefForm, brief.value);
  briefSavedSnapshot.value = briefSnapshotOf(briefForm);
  briefEditing.value = false;
};

const saveBrief = async () => {
  if (!detailTaskId.value || briefBusy.value) return;
  const wasConfirmed = brief.value?.status === 'CONFIRMED';
  briefBusy.value = 'save';
  try {
    const res = await saveBrandBrief(
      detailTaskId.value,
      formToBriefPayload(briefForm, joinStyleRefFileIds(briefStyleFiles.value))
    );
    brief.value = res.data ?? brief.value;
    briefLoaded.value = true;
    briefError.value = '';
    fillBriefForm(briefForm, brief.value);
    applyBriefStyleFiles(brief.value);
    briefSavedSnapshot.value = briefSnapshotOf(briefForm);
    briefEditing.value = false;
    // 后端语义：保存不把「已确认」打回「草稿」。改了内容却仍是已确认，必须提醒重新确认。
    if (wasConfirmed) {
      modal.msgWarning('已保存；状态仍是「已确认」。内容有改动，建议重新点「品牌方确认」，让确认动作对得上最新内容。');
    } else {
      modal.msgSuccess('品牌要求已保存（状态：草稿）');
    }
  } catch (error) {
    // 失败时**留在编辑态**：不能把没存上的内容当成已保存
    briefError.value = '保存品牌要求失败：' + ((await extractErrorMessage(error)) ?? '接口失败');
    modal.msgError(briefError.value);
  } finally {
    briefBusy.value = '';
  }
};

const confirmBrief = async () => {
  if (!detailTaskId.value || briefBusy.value) return;
  if (briefEditing.value && briefDirty.value) {
    modal.msgWarning('有未保存的修改，请先点「保存」再确认');
    return;
  }
  if (!brief.value?.configured) {
    modal.msgWarning('还没有品牌要求内容：请先点「编辑」填写并保存，再确认');
    return;
  }
  if (!briefFields.some(field => ((brief.value?.[field.key] as string | undefined) ?? '').trim().length > 0)) {
    modal.msgWarning('品牌要求 8 项全空：至少填一项再确认（确认后视觉工厂会按它创作）');
    return;
  }
  try {
    await modal.confirm('确认后这条品牌要求就是「品牌方已确认的要求」，AI 视觉工厂会按它出图；是否继续？');
  } catch {
    return;
  }
  briefBusy.value = 'confirm';
  try {
    const res = await confirmBrandBrief(detailTaskId.value);
    brief.value = res.data ?? brief.value;
    briefLoaded.value = true;
    fillBriefForm(briefForm, brief.value);
    applyBriefStyleFiles(brief.value);
    briefSavedSnapshot.value = briefSnapshotOf(briefForm);
    modal.msgSuccess('品牌要求已确认');
  } catch (error) {
    modal.msgError('确认品牌要求失败：' + ((await extractErrorMessage(error)) ?? '接口失败'));
  } finally {
    briefBusy.value = '';
  }
};

// ---------------------------------------------------------------- 参考风格图片（Brief 的一个字段）

/**
 * 保存「参考风格图片」引用并**核对结果**。
 *
 * @param nextIds       目标 ID 列表（已包含本次改动）
 * @param action        '上传' | '移除'（提示语用）
 * @param hadTextEdits  调用前是否有未保存的文字改动（保存会把它们一起存下来，提示要说清）
 * @return 是否完全按预期生效
 */
const saveStyleRefs = async (
  nextIds: string[],
  action: '上传' | '移除',
  hadTextEdits: boolean
): Promise<boolean> => {
  if (!detailTaskId.value) return false;
  const res = await saveBrandBrief(
    detailTaskId.value,
    formToBriefPayload(briefForm, joinStyleRefFileIds(nextIds))
  );
  brief.value = res.data ?? brief.value;
  briefLoaded.value = true;
  briefError.value = '';
  fillBriefForm(briefForm, brief.value);
  applyBriefStyleFiles(brief.value);
  briefSavedSnapshot.value = briefSnapshotOf(briefForm);
  const kept = joinStyleRefFileIds(briefStyleFiles.value);
  const wanted = joinStyleRefFileIds(nextIds);
  if (kept !== wanted) {
    // 真实存在的一个后端边界：引用被清空时后端会把该列归一成 null，而 MyBatis-Plus 的
    // updateById 默认**跳过 null 字段**，所以"把最后一张也移除"这一步存不下来
    // （列表非空时都能正常保存）。必须如实说出来：不说的话用户刷新后看到图还在，会以为页面坏了。
    briefError.value =
      `参考风格图片的「${action}」没有完全生效：服务端仍返回「${kept || '（空）'}」。` +
      '原因是引用清空后后端把该列当 null 跳过更新（本仓库既有的更新策略），需要后端改成对该列显式置空。' +
      '当前状态以刷新后的列表为准。';
    modal.msgWarning(briefError.value);
    return false;
  }
  if (action === '上传') {
    modal.msgSuccess(
      hadTextEdits
        ? '参考风格图片已上传并保存（同时保存了你正在编辑的文字内容）；平面设计在视觉工厂能看到这张图。'
        : '参考风格图片已上传并保存；平面设计在视觉工厂能看到这张图。'
    );
  } else {
    modal.msgSuccess('已移除该参考风格图片并保存');
  }
  return true;
};

/**
 * 选了参考风格图片：先传成任务附件，再把附件ID并进品牌要求并保存。
 *
 * <p>分两步并且**分别报错**：上传成功但引用没存上时，必须把附件ID告诉用户
 * （图已经在「资料附件」里，可以拿这个ID人工补救），不能笼统说"上传失败"——那会让人重复上传。</p>
 */
const onStyleImagePick = async (file: File) => {
  if (!detailTaskId.value) {
    modal.msgError('请先打开一个任务详情再上传参考风格图片');
    return;
  }
  if (briefStyleFiles.value.length >= BRAND_BRIEF_STYLE_MAX) {
    modal.msgWarning(`参考风格图片最多 ${BRAND_BRIEF_STYLE_MAX} 张，请先移除再上传`);
    return;
  }
  briefBusy.value = 'style';
  briefError.value = '';
  const hadTextEdits = briefDirty.value;
  let stage: '上传' | '保存引用' = '上传';
  try {
    const res = await uploadTaskFile({ taskId: detailTaskId.value, file: file });
    const fileId = res.data;
    if (!fileId) {
      briefError.value = '参考风格图片上传没有返回附件ID，无法登记到品牌要求里；请重试，或改用「资料附件」上传';
      modal.msgError(briefError.value);
      return;
    }
    stage = '保存引用';
    await saveStyleRefs([...briefStyleFiles.value, String(fileId)], '上传', hadTextEdits);
    // 这张图同时也会出现在「资料附件」里（同一份文件，不复制），刷新一下让列表跟上
    await loadDetail();
  } catch (error) {
    const reason = (await extractErrorMessage(error)) ?? '接口失败';
    briefError.value =
      stage === '上传' ? `参考风格图片上传失败：${reason}` : `图片已上传（在「资料附件」里），但引用没保存成功：${reason}`;
    modal.msgError(briefError.value);
  } finally {
    briefBusy.value = '';
  }
};

/** 移除一张参考风格图片（只解除引用；附件本身不删——它可能还被资料/参考图使用） */
const removeStyleImage = async (image: BrandBriefImage) => {
  const id = String(image.fileId ?? '');
  if (!id || !detailTaskId.value) return;
  try {
    await modal.confirm(
      `移除参考风格图片「${image.fileName || id}」？\n只解除品牌要求里的引用，附件本身仍留在「资料附件」里。`
    );
  } catch {
    return;
  }
  briefBusy.value = 'style';
  try {
    await saveStyleRefs(
      briefStyleFiles.value.filter((item) => item !== id),
      '移除',
      false
    );
  } catch (error) {
    briefError.value = '移除参考风格图片失败：' + ((await extractErrorMessage(error)) ?? '接口失败');
    modal.msgError(briefError.value);
  } finally {
    briefBusy.value = '';
  }
};

// ---------------------------------------------------------------- 列表与表单

const taskList = ref<CpTaskVO[]>([]);
const productList = ref<CpProductVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const { ids, single, multiple, handleSelectionChange } = useTableSelection<CpTaskVO>(item => item.taskId!);
const total = ref(0);
const taskFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();
const userSelectRef = ref<InstanceType<typeof UserSelect>>();
const queryUserSelectRef = ref<InstanceType<typeof UserSelect>>();
const queryOwnerLabel = ref('');

const initFormData: CpTaskForm = {
  taskId: undefined,
  taskName: '',
  deliverableType: '',
  productId: undefined,
  skuCode: '',
  deadline: undefined,
  ownerId: undefined,
  ownerName: '',
  dataLevel: 'INTERNAL',
  remark: ''
};

const data = reactive<PageData<CpTaskForm, CpTaskQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    queryTaskNo: '',
    queryTaskName: '',
    queryDeliverableType: undefined,
    queryStatus: undefined,
    queryOwnerId: undefined,
    productId: undefined,
    params: {}
  },
  rules: {
    taskName: [{ required: true, message: '任务名称不能为空', trigger: 'blur' }],
    deliverableType: [{ required: true, message: '交付类型不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<CpTaskForm, CpTaskQuery>>(data);
const { dialog, resetForm, openDialog, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: taskFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => {
    queryOwnerLabel.value = '';
    queryParams.value.queryOwnerId = undefined;
    handleQuery();
  }
});

/** 查询任务列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listTask(queryParams.value);
    taskList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 产品下拉选项 */
const loadProducts = async () => {
  const res = await productOptions();
  productList.value = res.data || [];
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const cancel = () => {
  closeDialog();
  resetForm();
  // 品牌要求是弹窗里的第二块表单，它不在 useFormDialog 的范围内，必须一起复位
  fillBriefForm(formBrief, null);
  formBriefExisted.value = false;
  formBriefStatus.value = null;
  briefCollapse.value = [];
};

const handleAdd = () => {
  fillBriefForm(formBrief, null);
  formBriefExisted.value = false;
  formBriefStatus.value = null;
  briefCollapse.value = [];
  openDialog('新增内容任务');
};

const handleUpdate = async (row?: Partial<CpTaskVO>) => {
  resetForm();
  const taskId = row?.taskId || ids.value[0];
  const res = await getTask(taskId!);
  Object.assign(form.value, res.data?.task || {});
  // 品牌要求：读出来填进折叠区，并记下"本来就存在记录"，决定 8 项全空时是否提交
  briefCollapse.value = [];
  fillBriefForm(formBrief, null);
  formBriefExisted.value = false;
  formBriefStatus.value = null;
  try {
    const briefRes = await getBrandBrief(taskId!);
    formBriefStatus.value = briefRes.data ?? null;
    formBriefExisted.value = briefRes.data?.configured === true;
    fillBriefForm(formBrief, briefRes.data);
  } catch (error) {
    // 任务本身能打开就够了：品牌要求读失败要说出来，但不能把人挡在编辑弹窗外
    formBriefStatus.value = null;
    modal.msgWarning('品牌要求没取到：' + ((await extractErrorMessage(error)) ?? '接口失败') + '；本次保存不会覆盖它（除非你在折叠区里填了内容）');
  }
  showDialog('修改内容任务');
};

/** 表单里当前选中的产品（用于展示产品与SKU信息，避免只显示一个产品名） */
const selectedProduct = computed(() =>
  productList.value.find(p => String(p.productId) === String(form.value.productId))
);

/**
 * 选中产品时把 SKU 带出来。
 *
 * 此前 placeholder 写着「可留空，默认取产品的SKU」，但前后端都没有实现这个默认值——
 * 实测选中产品后 cp_task.sku_code 仍是空串。这里补上：仅在用户没填过时自动带出，
 * 不覆盖用户已经手填的 SKU。
 */
const handleFormProductChange = (productId: string | number | undefined) => {
  const product = productList.value.find(p => String(p.productId) === String(productId));
  if (!product) {
    return;
  }
  if (!form.value.skuCode) {
    form.value.skuCode = product.skuCode || '';
  }
};

/**
 * 保存任务 = 两步：先任务本体，再品牌要求（Brief）。
 *
 * <p>为什么要分两步并分别报错：品牌要求是另一组接口（`/content/task/{id}/brand-brief`），
 * 任一步失败都必须说清"哪一步失败了"，否则用户看到"保存失败"会以为任务也没建成，
 * 重复提交出两条任务。所以这里逐段 try：任务失败就中止（品牌要求没意义），
 * 品牌要求失败时明确告知"任务已保存，品牌要求没存上，去详情里补一次即可"。</p>
 */
const submitForm = () => {
  taskFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    const isEdit = !!form.value.taskId;
    // 8 项全空、且本来就没有记录时跳过提交：不给每个任务都建一条空白的品牌要求
    const shouldSaveBrief = briefFormHasContent(formBrief) || formBriefExisted.value;
    let taskId: string | number | undefined = form.value.taskId;

    // 第一步：任务本体
    try {
      if (isEdit) {
        await updateTask(form.value);
      } else {
        const res = await addTask(form.value);
        taskId = res.data;
      }
    } catch (error) {
      const reason = (await extractErrorMessage(error)) ?? '接口失败';
      modal.msgError(
        (isEdit ? '任务更新失败：' : '任务创建失败：') + reason + '（品牌要求未提交，请修好后重试）'
      );
      return;
    }

    // 第二步：品牌要求
    if (!shouldSaveBrief || !taskId) {
      modal.msgSuccess('操作成功（品牌要求为空，未提交）');
      closeDialog();
      await getList();
      return;
    }
    const wasConfirmed = formBriefStatus.value?.status === 'CONFIRMED';
    try {
      const briefRes = await saveBrandBrief(taskId, formToBriefPayload(formBrief));
      formBriefStatus.value = briefRes.data ?? formBriefStatus.value;
      formBriefExisted.value = true;
      if (wasConfirmed) {
        modal.msgWarning('操作成功，品牌要求已一并保存；状态仍是「已确认」，内容有改动建议到任务详情里重新点「品牌方确认」。');
      } else {
        modal.msgSuccess('操作成功，品牌要求已一并保存');
      }
      closeDialog();
      await getList();
    } catch (error) {
      const reason = (await extractErrorMessage(error)) ?? '接口失败';
      // 任务已经保存成功了：不要再让用户重填任务，只需去详情补品牌要求
      modal.msgError(
        (isEdit ? '任务已保存' : '任务已创建') + '，但品牌要求保存失败：' + reason +
        '。任务不需要重填，请打开该任务详情在「品牌要求（Brief）」里补一次保存。'
      );
      closeDialog();
      await getList();
    }
  });
};

const handleDelete = async (row?: Partial<CpTaskVO>) => {
  const taskIds = row?.taskId || ids.value;
  await modal.confirm('是否确认删除任务编号为"' + taskIds + '"的数据项？');
  await delTask(taskIds);
  await getList();
  modal.msgSuccess('删除成功');
};

/** 打开查询区的负责人选择器 */
const openQueryOwnerSelect = () => {
  queryUserSelectRef.value?.open();
};

/** 打开表单里的负责人选择器 */
const openFormOwnerSelect = () => {
  userSelectRef.value?.open();
};

const handleQueryOwnerSelected = (users: any[]) => {
  const user = users?.[0];
  if (!user) return;
  queryParams.value.queryOwnerId = user.userId;
  queryOwnerLabel.value = user.nickName || user.userName || String(user.userId);
};

const handleClearQueryOwner = () => {
  queryParams.value.queryOwnerId = undefined;
  queryOwnerLabel.value = '';
};

const handleFormOwnerSelected = (users: any[]) => {
  const user = users?.[0];
  if (!user) return;
  form.value.ownerId = user.userId;
  form.value.ownerName = user.nickName || user.userName || String(user.userId);
};

// ---------------------------------------------------------------- 详情

const detailVisible = ref(false);
const detailTaskId = ref<string | number | undefined>(undefined);
const detail = ref<ContentTaskDetailVO>({});
const detailLoading = ref(false);
const acting = ref(false);
const polling = ref(false);

const taskInfo = computed<CpTaskVO>(() => detail.value.task || {});
const files = computed<CpTaskFileVO[]>(() => detail.value.files || []);
const facts = computed<CpFactSnapshotVO[]>(() => detail.value.facts || []);
const cards = computed<CpInteractionCardVO[]>(() => detail.value.cards || []);
const jobs = computed<CpAsyncJobVO[]>(() => detail.value.jobs || []);
const gate = computed(() => detail.value.gate || {});
const workPackage = computed(() => detail.value.workPackage || null);
const drawerTitle = computed(() => (taskInfo.value.taskNo ? '任务详情 · ' + taskInfo.value.taskNo : '任务详情'));

/**
 * 视觉阶段 → 中文。字典来自创作域（`CREATIVE_STAGE_LABELS`），本页只做兜底。
 *
 * 未知编码直接显示编码本身而不是空字符串：**看不懂的英文也比"什么都没有"有用**——
 * 内测 S11 的教训就是枚举直接漏到界面上，而静默留白更糟（看起来像没有进度）。
 *
 * @param stage 阶段编码（cp_task.visual_stage）
 * @returns 可读文案
 */
const visualStageLabel = (stage?: string) => (stage ? CREATIVE_STAGE_LABELS[stage] || stage : '未进入视觉工厂');

/** 视觉阶段的徽标色彩（与创作域同一张表，保证两个部门看到的颜色语义一致） */
const visualStageType = (stage?: string): TagType =>
  ((stage ? CREATIVE_STAGE_TYPES[stage] : undefined) as TagType) || 'info';

/**
 * 可作为「事实出处」的资料：本任务的附件。
 *
 * C7-b 起出处必须指到一份具体资料（而不是一句自由文本），所以这里直接给出可选清单。
 * 详情接口本来就把附件一起返回了（`files`），不必再打一次接口。
 */
const sourceFileOptions = computed<CpTaskFileVO[]>(() => files.value);

/** 资料选项文案：文件名（类型）——类型能帮人认出"参数表还是设计稿" */
const sourceFileLabel = (f: CpTaskFileVO) =>
  `${f.fileName || '未命名'}${f.fileKind ? `（${f.fileKind}）` : ''}`;

/** 作业中标记列表（用于轮询判定） */
const hasRunningJob = (list: CpAsyncJobVO[]) =>
  list.some(j => j.status === 'QUEUED' || j.status === 'RUNNING');

/** JSON 文本安全解析为数组 */
function parseJsonList<T>(text?: string): T[] {
  if (!text) return [];
  try {
    const parsed = JSON.parse(text);
    return Array.isArray(parsed) ? (parsed as T[]) : [];
  } catch {
    return [];
  }
}

/** 开工包内容解析 */
const parsePackageContent = (text?: string): WorkPackageContent | null => {
  if (!text) return null;
  try {
    return JSON.parse(text) as WorkPackageContent;
  } catch {
    return null;
  }
};

const packageContent = computed<WorkPackageContent | null>(() =>
  parsePackageContent(workPackage.value?.contentJson)
);

/** 加载任务详情；withLoading=false 时用于轮询静默刷新 */
const loadDetail = async (taskId: string | number | undefined = detailTaskId.value, withLoading = true) => {
  if (!taskId) return;
  if (withLoading) detailLoading.value = true;
  try {
    const res = await getTask(taskId);
    detail.value = res.data || {};
    if (hasRunningJob(detail.value.jobs || [])) {
      startPolling();
    } else {
      stopPolling();
    }
    // 品牌要求单独取：它归内容域的另一组接口，失败不影响任务详情本身，
    // 但必须在卡片里如实显示失败原因（loadBrief 内部处理）
    await loadBrief(taskId);
  } finally {
    if (withLoading) detailLoading.value = false;
  }
};

const openDetail = async (row: CpTaskVO) => {
  await openDetailById(row.taskId);
};

/**
 * 按 taskId 打开任务详情（抽屉）。
 *
 * <p><b>为什么单独抽出来</b>：深链只有一个 id、没有列表行对象，而列表里也未必有这一条
 * （`openDetail(row)` 不能直接用）。详情接口 `getTask(taskId)` 本来就能按 id 取，
 * 所以两条入口共用同一段加载逻辑，不需要"先找到那一行"。</p>
 *
 * @param taskId 任务ID
 * @param section 打开后滚动到的卡片（`brief` / `facts`；不带就停在顶部）
 */
const openDetailById = async (taskId: string | number, section?: string | null) => {
  detailTaskId.value = taskId;
  detailVisible.value = true;
  await loadDetail(taskId);
  await scrollToDetailSection(section);
};

/**
 * 把详情抽屉滚到指定卡片（深链用）。
 *
 * <p>抽屉是懒渲染的（`el-drawer` 的内容在打开后才挂载），所以必须等一帧再查 DOM；
 * 找不到就**什么都不做**——锚点不对不该让深链整体失败。</p>
 *
 * @param section 卡片标识（`brief` / `facts`）
 */
const scrollToDetailSection = async (section?: string | null) => {
  const key = (section || '').trim();
  if (!key) return;
  await nextTick();
  const el = document.getElementById(`detail-${key}`);
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
};

// 轮询：解析/预检为应用内异步作业，前端按作业状态自动刷新（SPEC §5）
let pollTimer: ReturnType<typeof setInterval> | null = null;
let pollTicks = 0;

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  polling.value = false;
};

const startPolling = () => {
  if (pollTimer) return;
  polling.value = true;
  pollTicks = 0;
  pollTimer = setInterval(async () => {
    pollTicks += 1;
    if (!detailTaskId.value || pollTicks > 100) {
      stopPolling();
      return;
    }
    await loadDetail(detailTaskId.value, false);
    await getList();
  }, 3000);
};

const handleDrawerClosed = () => {
  stopPolling();
  detailVisible.value = false;
  detailTaskId.value = undefined;
  detail.value = {};
  // 品牌要求的卡片状态一起清掉：下次打开的是另一个任务，不能沿用上一个的任务内容
  brief.value = null;
  briefLoaded.value = false;
  briefError.value = '';
  briefEditing.value = false;
  briefBusy.value = '';
  fillBriefForm(briefForm, null);
  applyBriefStyleFiles(null);
  briefSavedSnapshot.value = briefSnapshotOf(briefForm);
  cancelOther();
  cardDialog.visible = false;
};

/** 触发解析 */
const handleParse = async () => {
  if (!detailTaskId.value || acting.value) return;
  acting.value = true;
  try {
    await triggerParse(detailTaskId.value);
    modal.msgSuccess('解析已提交，正在后台执行');
    await loadDetail();
    startPolling();
  } finally {
    acting.value = false;
  }
};

/** 触发预检 */
const handlePrecheck = async () => {
  if (!detailTaskId.value || acting.value) return;
  acting.value = true;
  try {
    await triggerPrecheck(detailTaskId.value);
    modal.msgSuccess('预检已提交，正在后台执行');
    await loadDetail();
    startPolling();
  } finally {
    acting.value = false;
  }
};

/** 重算闸门并刷新 */
const handleRecheck = async () => {
  if (!detailTaskId.value || acting.value) return;
  acting.value = true;
  try {
    const res = await recheckGate(detailTaskId.value);
    modal.msgSuccess('闸门重算完成：' + taskStatusLabel(res.data?.status));
    await loadDetail();
    await getList();
  } finally {
    acting.value = false;
  }
};

// ---------------------------------------------------------------- 附件上传

const uploadRef = ref<ElUploadInstance>();
const uploadFileList = ref<any[]>([]);
const uploadFile = ref<File | null>(null);
const uploadDataLevel = ref<string | undefined>(undefined);
const uploading = ref(false);

const handleUploadChange = (file: UploadFile) => {
  const raw = (file as any).raw as File | undefined;
  uploadFile.value = raw || null;
  uploadFileList.value = [file];
};

const handleUploadRemove = () => {
  uploadFile.value = null;
  uploadFileList.value = [];
};

const handleUploadExceed = () => {
  uploadRef.value?.clearFiles();
  uploadFile.value = null;
  uploadFileList.value = [];
  modal.msgError('一次只能上传一个文件，请先移除已选文件');
};

const submitUpload = async () => {
  if (!detailTaskId.value) return;
  if (!uploadFile.value) {
    modal.msgError('请选择要上传的文件');
    return;
  }
  uploading.value = true;
  try {
    await uploadTaskFile({
      taskId: detailTaskId.value,
      dataLevel: uploadDataLevel.value,
      file: uploadFile.value
    });
    modal.msgSuccess('上传成功');
    uploadRef.value?.clearFiles();
    uploadFile.value = null;
    uploadFileList.value = [];
    await loadDetail();
  } finally {
    uploading.value = false;
  }
};

// ---------------------------------------------------------------- 事实清单

const confirmStatusLabel = (status?: string) => {
  if (status === 'PENDING') return '待确认';
  if (status === 'CONFIRMED') return '已确认';
  if (status === 'CONFLICT') return '冲突';
  if (status === 'REJECTED') return '已否决';
  return status || '-';
};

const confirmStatusType = (status?: string): ElTagType => {
  if (status === 'CONFIRMED') return 'success';
  if (status === 'CONFLICT') return 'danger';
  if (status === 'REJECTED') return 'info';
  return 'warning';
};

const handleConfirmFact = async (row: CpFactSnapshotVO) => {
  // v1 反馈（详情页与审核 1.3）：「点击确认事实无误选项之后**还是没有反应**」。
  // 这一条原来就是静默 return —— 点下去什么都不发生，人只能反复点。现在把原因说出来。
  if (!row.snapshotId) {
    modal.msgWarning(
      '这一行没有可确认的候选快照（snapshotId 缺失），无法确认。请先刷新任务详情；若一直如此，请把这条反馈给我们。'
    );
    return;
  }
  await confirmFact(row.snapshotId);
  modal.msgSuccess('已确认该值');
  await loadDetail();
};

const handleRejectFact = async (row: CpFactSnapshotVO) => {
  if (!row.snapshotId) {
    modal.msgWarning('这一行没有可否决的候选快照（snapshotId 缺失）。请先刷新任务详情；若一直如此，请反馈给我们。');
    return;
  }
  await modal.confirm('否决后该候选值不会被采用，是否继续？');
  await rejectFact(row.snapshotId);
  modal.msgSuccess('已否决该候选值');
  await loadDetail();
};

const handleConfirmUnambiguous = async () => {
  if (!detailTaskId.value) {
    modal.msgWarning('请先打开一个任务详情，再点「一键确认无争议项」');
    return;
  }
  const res = await confirmUnambiguousFacts(detailTaskId.value);
  const count = res.data ?? 0;
  if (!count) {
    // 「已确认 0 条无争议项」和"没反应"是同一件事（v1 反馈原话），所以 0 条要解释为什么、
    // 以及接下来去哪儿做——后端只把"没有待确认的无争议项"这件事告诉我们，
    // "为什么没有"（都已确认 / 剩下的都要逐条裁定）由页面按事实清单的现状说清楚。
    modal.msgWarning(
      '没有可一键确认的无争议项：要么这些事实都已经确认过了，要么剩下的都需要逐条裁定——' +
        '请在下面「事实清单」里逐行点「确认」或「否决」。'
    );
  } else {
    modal.msgSuccess('已确认 ' + count + ' 条无争议项');
  }
  await loadDetail();
  await getList();
};

const manualDialog = reactive<DialogOption>({ visible: false, title: '手工录入事实' });
const manualFormRef = ref<ElFormInstance>();
const manualSaving = ref(false);
const manualForm = reactive({ fieldCode: '', value: '', sourceFileId: undefined as string | number | undefined, sourceLocator: '', remark: '' });
/** 是否使用「自定义编码」输入（默认从下拉选，避免手打编码踩空） */
const manualCustomMode = ref(false);
/** 本任务可录入的字段选项（本交付类型的闸门要求项在前） */
const fieldOptionList = ref<CpFactFieldOptionVO[]>([]);
const manualRules = {
  fieldCode: [{ required: true, message: '事实字段不能为空', trigger: 'change' }],
  value: [{ required: true, message: '字段值不能为空', trigger: 'blur' }],
  // 出处必选（内测 S19 → C7-b）：值直接落 CONFIRMED 且会随开工包交给下游，
  // 所以出处必须指到一份本任务的资料，"填一句话"不够（自由文本可以被填成「-」）
  sourceFileId: [{ required: true, message: '请选择事实出处（这条值是从哪份任务资料里看到的）', trigger: 'change' }]
};

const gateFieldOptions = computed(() => fieldOptionList.value.filter(o => o.requiredByGate));
const otherFieldOptions = computed(() => fieldOptionList.value.filter(o => !o.requiredByGate));
const manualSelectedOption = computed(() =>
  fieldOptionList.value.find(o => o.fieldCode === manualForm.fieldCode)
);

/** 下拉项文案：中文名（编码）· 闸门等级 · 已确认标记 */
const optionLabel = (o: CpFactFieldOptionVO) => {
  const parts = [`${o.fieldName || o.fieldCode}（${o.fieldCode}）`];
  if (o.gateLevel) parts.push(o.gateLevel);
  if (o.satisfied) parts.push('已确认');
  return parts.join(' · ');
};

/** 拉取可录入字段选项；失败不阻断录入（退化为自定义编码） */
const loadFieldOptions = async () => {
  if (!detailTaskId.value) {
    fieldOptionList.value = [];
    return;
  }
  try {
    const res = await factFieldOptions(detailTaskId.value);
    fieldOptionList.value = res.data || [];
  } catch {
    fieldOptionList.value = [];
    manualCustomMode.value = true;
  }
};

const openManualDialog = async () => {
  manualForm.fieldCode = '';
  manualForm.value = '';
  manualForm.sourceFileId = undefined;
  manualForm.sourceLocator = '';
  manualForm.remark = '';
  manualCustomMode.value = false;
  manualDialog.visible = true;
  await loadFieldOptions();
};

/**
 * 从闸门表的「录入该字段」进入：编码与中文名已确定，直接预填，不用用户抄编码。
 *
 * @param rule 闸门规则行（含 fieldCode / fieldName）
 */
const openManualDialogFor = async (rule: any) => {
  await openManualDialog();
  if (rule?.fieldCode) {
    manualForm.fieldCode = rule.fieldCode;
    // 闸门规则里的编码未必在别名表里，若下拉里没有它则退回自定义模式，保证能填进去
    if (!fieldOptionList.value.some(o => o.fieldCode === rule.fieldCode)) {
      manualCustomMode.value = true;
    }
  }
};

const submitManual = () => {
  manualFormRef.value?.validate(async (valid: boolean) => {
    if (!valid || !detailTaskId.value) return;
    manualSaving.value = true;
    try {
      await addManualFact({
        taskId: detailTaskId.value,
        fieldCode: manualForm.fieldCode,
        value: manualForm.value,
        sourceFileId: manualForm.sourceFileId!,
        sourceLocator: manualForm.sourceLocator || undefined,
        remark: manualForm.remark
      });
      modal.msgSuccess('录入成功');
      manualDialog.visible = false;
      await loadDetail();
      await getList();
    } finally {
      manualSaving.value = false;
    }
  });
};

// ------------------------------------------------- 产品主数据 → 产品事实（同步）

const syncingProductFacts = ref(false);

/** 把所选产品在「产品与SKU」里的名称/SKU 同步为产品事实（冲突时只落待确认） */
const handleSyncProductFacts = async () => {
  if (!detailTaskId.value) return;
  syncingProductFacts.value = true;
  try {
    const res = await syncProductFacts(detailTaskId.value);
    const data = res.data || {};
    const msg = `同步完成：写入 ${data.synced ?? 0} 条、跳过 ${data.skipped ?? 0} 条、冲突待裁定 ${data.conflicts ?? 0} 条`;
    if (data.conflicts) {
      modal.msgWarning(msg + '。冲突项未自动确认，请在事实清单中裁定。');
    } else {
      modal.msgSuccess(msg);
    }
    await loadDetail();
    await getList();
  } finally {
    syncingProductFacts.value = false;
  }
};

// ---------------------------------------------------------------- 互动卡处理

const cardDialog = reactive<DialogOption>({ visible: false, title: '处理互动确认卡' });
const currentCard = ref<CpInteractionCardVO | null>(null);
const cardOtherActive = ref(false);
const cardOtherValue = ref('');
/** 「填写其他值」的出处：本任务的哪份资料（必选）+ 位置（可选）——内测 C7-b */
const cardOtherFileId = ref<string | number | undefined>(undefined);
const cardOtherLocator = ref('');
const cardResolving = ref(false);

const cardEvidence = computed<CardEvidenceItem[]>(() => parseJsonList<CardEvidenceItem>(currentCard.value?.evidenceJson));
const cardImpact = computed<CardImpactItem[]>(() => parseJsonList<CardImpactItem>(currentCard.value?.impactJson));
const cardOptions = computed<CardOptionItem[]>(() => parseJsonList<CardOptionItem>(currentCard.value?.optionsJson));

const gateLevelLabel = (level?: string) => {
  if (level === 'BLOCK') return '强制阻断';
  if (level === 'CONDITION') return '条件流转';
  if (level === 'NOTICE') return '非阻断提醒';
  return '-';
};

const optionButtonType = (option?: string) => {
  if (option === 'BLOCK') return 'danger';
  if (option === 'CONFIRM') return 'primary';
  return '';
};

const cancelOther = () => {
  cardOtherActive.value = false;
  cardOtherValue.value = '';
  cardOtherFileId.value = undefined;
  cardOtherLocator.value = '';
};

const openCardDialog = (row: CpInteractionCardVO) => {
  currentCard.value = row;
  cancelOther();
  cardDialog.visible = true;
};

/** 提交卡片处理；后端会同步重算闸门 */
const doResolveCard = async (payload: {
  option: string;
  value?: string;
  snapshotId?: string | number;
  sourceFileId?: string | number;
  sourceLocator?: string;
  comment?: string;
}) => {
  if (!currentCard.value?.cardId || cardResolving.value) return;
  cardResolving.value = true;
  try {
    await resolveCard({ cardId: currentCard.value.cardId, ...payload });
    modal.msgSuccess('处理成功');
    cardDialog.visible = false;
    cancelOther();
    await loadDetail();
    await getList();
  } finally {
    cardResolving.value = false;
  }
};

const submitCardOption = async (opt: CardOptionItem) => {
  const option = opt.option || '';
  if (option === 'CONFIRM') {
    await doResolveCard({ option: 'CONFIRM', value: opt.value ?? undefined, snapshotId: opt.snapshotId });
    return;
  }
  if (option === 'OTHER') {
    cardOtherActive.value = true;
    cardOtherValue.value = '';
    cardOtherFileId.value = undefined;
    cardOtherLocator.value = '';
    return;
  }
  const tip =
    option === 'BLOCK'
      ? '选择「暂不确认并阻断」后任务将停在待确认状态，请填写阻断原因（可选）'
      : '请说明将补充哪些资料（可选）';
  let comment = '';
  try {
    const res: any = await modal.prompt(tip);
    comment = res?.value || '';
  } catch {
    return;
  }
  await doResolveCard({ option, comment });
};

const submitCardOther = async () => {
  const value = cardOtherValue.value.trim();
  if (!value) {
    modal.msgError('请填写确认值');
    return;
  }
  if (!cardOtherFileId.value) {
    modal.msgError('请选择事实出处（这条值是从哪份任务资料里看到的）');
    return;
  }
  await doResolveCard({
    option: 'OTHER',
    value,
    sourceFileId: cardOtherFileId.value,
    sourceLocator: cardOtherLocator.value.trim() || undefined
  });
};

// ---------------------------------------------------------------- 开工包

const generatingPackage = ref(false);
const issuingPackage = ref(false);

const handleGeneratePackage = async () => {
  if (!detailTaskId.value || generatingPackage.value) return;
  generatingPackage.value = true;
  try {
    await generateWorkPackage(detailTaskId.value);
    modal.msgSuccess('开工包已生成');
    await loadDetail();
  } finally {
    generatingPackage.value = false;
  }
};

const handleIssuePackage = async () => {
  const pkg = workPackage.value;
  if (!pkg?.packageId || issuingPackage.value) return;
  await modal.confirm('签发后开工包内容与事实版本将被冻结，是否继续？');
  issuingPackage.value = true;
  try {
    await issueWorkPackage(pkg.packageId);
    modal.msgSuccess('签发成功');
    await loadDetail();
  } finally {
    issuingPackage.value = false;
  }
};

// ---------------------------------------------------------------- 展示辅助

const jobTypeLabel = (type?: string) => {
  if (type === 'PARSE') return '资料解析';
  if (type === 'PRECHECK') return '资料预检';
  if (type === 'PACKAGE') return '开工包生成';
  return type || '-';
};

const jobStatusLabel = (status?: string) => {
  if (status === 'QUEUED') return '排队中';
  if (status === 'RUNNING') return '执行中';
  if (status === 'SUCCESS') return '成功';
  if (status === 'FAILED') return '失败';
  return status || '-';
};

const jobStatusType = (status?: string): ElTagType => {
  if (status === 'SUCCESS') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'RUNNING') return 'primary';
  return 'info';
};

const formatSize = (size?: number) => {
  if (size === undefined || size === null) return '-';
  if (size < 1024) return size + ' B';
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB';
  return (size / 1024 / 1024).toFixed(2) + ' MB';
};

onMounted(async () => {
  await loadProducts();
  await getList();
  // 深链：`?taskId=…[&section=brief|facts]` 直接打开那条任务的详情。
  //
  // 为什么必须有：视觉门的「去哪儿补」把品牌部的项指到本页（品牌要求 / 事实只有品牌方能在
  // 任务详情里确认），而本页原先**不支持深链**，于是点了按钮只落到任务列表——
  // 用户看到的正是"也没有跳转到相应要确认的地方"（v1 人工测试反馈 详情页与审核 1.3）。
  const query = new URLSearchParams(location.search);
  const deepTaskId = (query.get('taskId') || '').trim();
  if (deepTaskId) {
    try {
      await openDetailById(deepTaskId, query.get('section'));
    } catch (error) {
      // 深链指到不存在的任务 / 没有权限 / id 不合法：不能留下"抽屉空着 + 控制台一条未捕获异常"。
      // 关掉抽屉并说清原因，人就还能正常用这个页面（列表已经加载好了）。
      detailVisible.value = false;
      detailTaskId.value = undefined;
      modal.msgError(
        '打开这个任务失败（可能已删除、id 不合法或没有权限）：' +
          ((await extractErrorMessage(error)) ?? '接口失败')
      );
    }
  }
});

onBeforeUnmount(() => {
  stopPolling();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}

/* 表单里选中产品后的信息提示（展示产品与SKU模块已有的标识信息） */
.product-hint {
  margin: 0 0 12px;

  .product-hint-line {
    font-size: 12px;
    line-height: 1.7;
  }
}

/* 手工录入事实：字段下拉的辅助说明 */
.manual-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
}

.manual-desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}

/* 出处：先选资料（必选），再补位置（可选）——两行摆在一起，别让人以为位置是主要入口 */
.source-locator-input {
  margin-top: 6px;
}

.task-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 12px;
}

.action-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.polling-tip {
  font-size: 12px;
  color: var(--app-text-muted);
}

/* 品牌要求（Brief）：只读值 + 编辑态表单。视觉工厂那边只读，录入与确认都在这里。 */
.brief-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.brief-bar-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.brief-dirty {
  font-size: 12px;
  color: #b45309;
}

/* 参考风格图片：上传位跟在「参考风格」文本框下面（同一个表单项里，就近可操作） */
.brief-style-upload {
  width: 100%;
  margin-top: 6px;
}

.brief-error {
  margin: 0 0 10px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #c0392b;
}

.brief-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.brief-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.brief-row > label {
  flex: 0 0 110px;
  padding-top: 2px;
  font-size: 13px;
  color: var(--app-text-muted);
}

/* 多行原样显示（必显信息/禁用词都是一行一条），空值用 — 而不是编造默认值 */
.brief-value {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--app-text-title);
  white-space: pre-line;
  word-break: break-word;
}

.brief-value.is-empty {
  color: var(--app-text-muted);
}

.brief-form {
  padding-top: 2px;
}

.form-collapse {
  margin-top: 4px;
}

.collapse-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}

.collapse-tip {
  margin-bottom: 8px;
}

.detail-card {
  --el-card-padding: 14px;
}

.card-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.card-head p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}

.card-head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.gate-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}.gate-reason {
  font-size: 12px;
  color: #b45309;
}

.gate-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.block-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-title);
}

.block-title.danger {
  color: #c0392b;
}

.block-title.warning {
  color: #b45309;
}

.inner-table {
  width: 100%;
}

.tag-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.muted {
  margin: 0;
  font-size: 12px;
  color: var(--app-text-muted);
}

.upload-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 12px;
}

.upload-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.package-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 10px 20px;
}

.summary-item {
  display: flex;
  gap: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-body);
}

.summary-item.wide {
  grid-column: 1 / -1;
}

.summary-label {
  flex-shrink: 0;
  color: var(--app-text-muted);
}

.summary-value {
  min-width: 0;
  word-break: break-all;
}

.tag-gap {
  margin-right: 6px;
}

.card-process {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-text-body);
  white-space: pre-wrap;
}

.evidence-item {
  padding: 6px 10px;
  border-left: 3px solid #d9e2ea;
  background: #f7fafc;
  border-radius: 4px;
}

.evidence-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--app-text-body);
}

.evidence-locator {
  color: var(--app-text-muted);
}

.evidence-value {
  font-weight: 600;
}

.evidence-excerpt {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-muted);
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: var(--app-text-body);
}

.option-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.other-input {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
