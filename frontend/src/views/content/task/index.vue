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
              <el-select v-model="form.productId" placeholder="请选择产品" clearable filterable style="width: 100%">
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
              <el-input v-model="form.skuCode" placeholder="可留空，默认取产品的SKU" />
            </el-form-item>
          </el-col>
        </el-row>
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
        </el-descriptions>

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
        <el-card shadow="never" class="detail-card">
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
    <el-dialog v-model="manualDialog.visible" title="手工录入事实" width="560px" append-to-body>
      <el-form ref="manualFormRef" :model="manualForm" :rules="manualRules" label-width="100px">
        <el-form-item label="字段编码" prop="fieldCode">
          <el-input v-model="manualForm.fieldCode" placeholder="如 product_height" />
        </el-form-item>
        <el-form-item label="字段值" prop="value">
          <el-input v-model="manualForm.value" placeholder="请输入经责任人确认的值" />
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
              <el-button
                v-hasPermi="['content:card:handle']"
                type="primary"
                :disabled="cardResolving"
                @click="submitCardOther"
              >
                提交其他值
              </el-button>
              <el-button @click="cardOtherActive = false">取消</el-button>
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
import type { CpFactSnapshotVO } from '@/api/content/fact/types';
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
import { resolveCard } from '@/api/content/card';
import { addManualFact, confirmFact, confirmUnambiguousFacts, rejectFact } from '@/api/content/fact';
import { productOptions } from '@/api/content/product';
import {
  addTask,
  delTask,
  getTask,
  listTask,
  recheckGate,
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
};

const handleAdd = () => {
  openDialog('新增内容任务');
};

const handleUpdate = async (row?: Partial<CpTaskVO>) => {
  resetForm();
  const taskId = row?.taskId || ids.value[0];
  const res = await getTask(taskId!);
  Object.assign(form.value, res.data?.task || {});
  showDialog('修改内容任务');
};

const submitForm = () => {
  taskFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      form.value.taskId ? await updateTask(form.value) : await addTask(form.value);
      modal.msgSuccess('操作成功');
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
  } finally {
    if (withLoading) detailLoading.value = false;
  }
};

const openDetail = async (row: CpTaskVO) => {
  detailTaskId.value = row.taskId;
  detailVisible.value = true;
  await loadDetail(row.taskId);
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
  if (!row.snapshotId) return;
  await confirmFact(row.snapshotId);
  modal.msgSuccess('已确认该值');
  await loadDetail();
};

const handleRejectFact = async (row: CpFactSnapshotVO) => {
  if (!row.snapshotId) return;
  await modal.confirm('否决后该候选值不会被采用，是否继续？');
  await rejectFact(row.snapshotId);
  modal.msgSuccess('已否决该候选值');
  await loadDetail();
};

const handleConfirmUnambiguous = async () => {
  if (!detailTaskId.value) return;
  const res = await confirmUnambiguousFacts(detailTaskId.value);
  modal.msgSuccess('已确认 ' + (res.data ?? 0) + ' 条无争议项');
  await loadDetail();
  await getList();
};

const manualDialog = reactive<DialogOption>({ visible: false, title: '手工录入事实' });
const manualFormRef = ref<ElFormInstance>();
const manualSaving = ref(false);
const manualForm = reactive({ fieldCode: '', value: '', remark: '' });
const manualRules = {
  fieldCode: [{ required: true, message: '字段编码不能为空', trigger: 'blur' }],
  value: [{ required: true, message: '字段值不能为空', trigger: 'blur' }]
};

const openManualDialog = () => {
  manualForm.fieldCode = '';
  manualForm.value = '';
  manualForm.remark = '';
  manualDialog.visible = true;
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
        remark: manualForm.remark
      });
      modal.msgSuccess('录入成功');
      manualDialog.visible = false;
      await loadDetail();
    } finally {
      manualSaving.value = false;
    }
  });
};

// ---------------------------------------------------------------- 互动卡处理

const cardDialog = reactive<DialogOption>({ visible: false, title: '处理互动确认卡' });
const currentCard = ref<CpInteractionCardVO | null>(null);
const cardOtherActive = ref(false);
const cardOtherValue = ref('');
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
};

const openCardDialog = (row: CpInteractionCardVO) => {
  currentCard.value = row;
  cancelOther();
  cardDialog.visible = true;
};

/** 提交卡片处理；后端会同步重算闸门 */
const doResolveCard = async (payload: { option: string; value?: string; snapshotId?: string | number; comment?: string }) => {
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
  await doResolveCard({ option: 'OTHER', value });
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

const taskStatusLabel = (status?: string) => {
  if (status === 'DRAFT') return '草稿';
  if (status === 'PARSING') return '解析中';
  if (status === 'PENDING_CONFIRM') return '待确认/待补料';
  if (status === 'CONDITIONAL_READY') return '条件开工';
  if (status === 'READY') return '可开工';
  return status || '-';
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
}

.gate-reason {
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
