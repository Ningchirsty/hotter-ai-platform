<template>
  <div class="p-2 app-container hrtalent-background-page">
    <PageHeading
      title="背调与报到"
      subtitle="背调登记与授权、核查项与结论、敏感明细受控查看（必填用途并记审计）、报到与邀约信息登记"
      module="hrtalent"
    />

    <!-- 背调检索 -->
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>背调检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="应聘记录ID" prop="applicationId">
            <el-input v-model="queryParams.applicationId" placeholder="应聘记录ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="背调负责人ID" prop="checkerId">
            <el-input v-model="queryParams.checkerId" placeholder="负责人用户ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="背调结论" prop="result">
            <el-select v-model="queryParams.result" placeholder="请选择结论" clearable style="width: 150px">
              <el-option v-for="dict in recruit_background_result" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="背调状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 150px">
              <el-option v-for="dict in recruit_background_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否授权" prop="authorizedFlag">
            <el-select v-model="queryParams.authorizedFlag" placeholder="请选择" clearable style="width: 120px">
              <el-option label="已授权" value="1" />
              <el-option label="未授权" value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="未通过原因" prop="failureReasonCode">
            <el-select v-model="queryParams.failureReasonCode" placeholder="请选择原因分类" clearable style="width: 170px">
              <el-option
                v-for="dict in recruit_background_failure_reason"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="完成时间">
            <el-date-picker
              v-model="checkTimeRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              range-separator="-"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 340px"
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
            <span class="panel-kicker">Background Check</span>
            <h3>背调记录（高敏感）</h3>
            <p>
              共 {{ total }} 条记录。列表与普通详情<strong>不含</strong>背调敏感明细；
              明细只能经「敏感明细」按钮（独立权限 recruit:background:view-sensitive）填写用途后查看，系统会写审计。
              结论为「已豁免」必须填免背调原因，结论为「不通过」必须填未通过原因分类。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['recruit:background:add']" type="primary" icon="Plus" @click="handleAdd">
              新增背调
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="backgroundList">
        <el-table-column label="背调ID" align="center" prop="backgroundId" width="110" />
        <el-table-column label="应聘记录ID" align="center" prop="applicationId" width="120" />
        <el-table-column label="是否授权" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.authorizedFlag === '1' ? 'success' : 'info'" size="small">
              {{ scope.row.authorizedFlag === '1' ? '已授权' : '未授权' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="背调负责人" align="center" width="130" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.checkerName || scope.row.checkerId || '-' }}</template>
        </el-table-column>
        <el-table-column label="开始日期" align="center" prop="checkStartDate" width="110" />
        <el-table-column label="结束日期" align="center" prop="checkEndDate" width="110" />
        <el-table-column label="完成时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.checkTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="结论" align="center" width="110">
          <template #default="scope">
            <dict-tag v-if="scope.row.result" :options="recruit_background_result" :value="scope.row.result" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="未通过原因" align="center" width="150">
          <template #default="scope">
            <dict-tag
              v-if="scope.row.failureReasonCode"
              :options="recruit_background_failure_reason"
              :value="scope.row.failureReasonCode"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="核查项" align="center" prop="checkItems" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="110">
          <template #default="scope">
            <dict-tag v-if="scope.row.status" :options="recruit_background_status" :value="scope.row.status" />
            <span v-else>{{ scope.row.statusLabel || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="免背调原因" align="center" prop="waiveReason" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="210" align="center" class-name="small-padding fixed-width" fixed="right">
          <template #default="scope">
            <el-tooltip content="详情" placement="top">
              <el-button
                v-hasPermi="['recruit:background:list']"
                link
                type="primary"
                icon="Search"
                @click="handleDetail(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="编辑" placement="top">
              <el-button
                v-hasPermi="['recruit:background:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="敏感明细（需填用途，记录审计）" placement="top">
              <el-button
                v-hasPermi="['recruit:background:view-sensitive']"
                link
                type="danger"
                icon="View"
                @click="openSensitive(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-dropdown trigger="click" @command="cmd => handleMore(scope.row, cmd)">
              <el-button link type="primary" icon="More"></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-hasPermi="['recruit:candidate:stage']" command="offer">邀约登记</el-dropdown-item>
                  <el-dropdown-item v-hasPermi="['recruit:candidate:stage']" command="arrival">报到登记</el-dropdown-item>
                  <el-dropdown-item v-hasPermi="['recruit:candidate:stage']" command="noArrival" divided>
                    未报到登记
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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

    <!-- 新增 / 编辑背调 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="860px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="背调明细为密文存储：此处填写的「敏感说明」将以密文落库，列表与普通详情不会返回；留空表示不修改已有明细。"
      />
      <el-form ref="backgroundFormRef" :model="form" :rules="rules" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="应聘记录ID" prop="applicationId">
              <el-input v-model="form.applicationId" placeholder="应聘记录ID（必填）" :disabled="!!form.backgroundId" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否已获授权" prop="authorizedFlag">
              <el-select v-model="form.authorizedFlag" placeholder="请选择" clearable style="width: 100%">
                <el-option label="已授权" value="1" />
                <el-option label="未授权" value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="授权时间" prop="authorizeTime">
              <el-date-picker
                v-model="form.authorizeTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="可为空"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="背调负责人" prop="checkerId">
              <el-input v-model="form.checkerId" placeholder="用户ID" clearable @click="openCheckerSelect">
                <template #append>
                  <el-button icon="User" @click="openCheckerSelect" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="背调开始日期" prop="checkStartDate">
              <el-date-picker
                v-model="form.checkStartDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="背调结束日期" prop="checkEndDate">
              <el-date-picker
                v-model="form.checkEndDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="背调完成时间" prop="checkTime">
              <el-date-picker
                v-model="form.checkTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="背调状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option
                  v-for="dict in recruit_background_status"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="背调结论" prop="result">
              <el-select v-model="form.result" placeholder="请选择结论" clearable style="width: 100%">
                <el-option
                  v-for="dict in recruit_background_result"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="未通过原因分类" prop="failureReasonCode">
              <el-select
                v-model="form.failureReasonCode"
                placeholder="仅结论为不通过时填写"
                clearable
                :disabled="form.result !== 'fail'"
                style="width: 100%"
              >
                <el-option
                  v-for="dict in recruit_background_failure_reason"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="核查项清单" prop="checkItems">
          <el-input
            v-model="form.checkItems"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="如：身份核验、学历核验、工作经历、离职原因、竞业限制等"
          />
        </el-form-item>
        <el-form-item label="免背调原因" prop="waiveReason">
          <el-input
            v-model="form.waiveReason"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="结论为「已豁免」时必填（§7.2 第 4 条依赖该字段）"
          />
        </el-form-item>
        <el-form-item label="敏感说明" prop="detailCipher">
          <el-input
            v-model="form.detailCipher"
            type="textarea"
            :rows="4"
            placeholder="以密文落库；留空表示不修改已有明细。查看时需独立权限 + 填写用途 + 记录审计"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 背调详情（不含敏感明细）+ 受控附件 -->
    <el-dialog v-model="detail.visible" title="背调详情" width="900px" append-to-body>
      <el-descriptions :column="2" border size="small" class="detail-panel">
        <el-descriptions-item label="背调ID">{{ detail.row.backgroundId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="应聘记录ID">{{ detail.row.applicationId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="是否已获授权">
          <el-tag :type="detail.row.authorizedFlag === '1' ? 'success' : 'info'" size="small">
            {{ detail.row.authorizedFlag === '1' ? '已授权' : '未授权' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="授权时间">{{ parseTime(detail.row.authorizeTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="背调负责人">
          {{ detail.row.checkerName || detail.row.checkerId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="背调状态">
          <dict-tag v-if="detail.row.status" :options="recruit_background_status" :value="detail.row.status" />
          <span v-else>{{ detail.row.statusLabel || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="开始日期">{{ detail.row.checkStartDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束日期">{{ detail.row.checkEndDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ parseTime(detail.row.checkTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="背调结论">
          <dict-tag v-if="detail.row.result" :options="recruit_background_result" :value="detail.row.result" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="未通过原因">
          <dict-tag
            v-if="detail.row.failureReasonCode"
            :options="recruit_background_failure_reason"
            :value="detail.row.failureReasonCode"
          />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="核查项清单" :span="2">{{ detail.row.checkItems || '-' }}</el-descriptions-item>
        <el-descriptions-item label="免背调原因" :span="2">{{ detail.row.waiveReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.row.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="敏感说明" :span="2">
          <span class="sensitive-mask">已加密存储：请使用「敏感明细」按钮填写用途后查看（会记录审计）</span>
        </el-descriptions-item>
      </el-descriptions>

      <template v-if="attachmentVisible">
        <el-divider content-position="left">背调附件（受控预览 / 下载 / 逻辑删除，均需填写用途并记录审计）</el-divider>
        <el-table v-loading="attachmentLoading" border size="small" :data="attachmentList">
          <el-table-column label="文件名" align="center" prop="originalName" show-overflow-tooltip />
          <el-table-column label="类型" align="center" width="120">
            <template #default="scope">{{ scope.row.fileTypeLabel || scope.row.fileType || '-' }}</template>
          </el-table-column>
          <el-table-column label="版本" align="center" prop="versionNo" width="80" />
          <el-table-column label="安全级别" align="center" width="120">
            <template #default="scope">{{ scope.row.securityLevelLabel || scope.row.securityLevel || '-' }}</template>
          </el-table-column>
          <el-table-column label="上传人" align="center" width="120">
            <template #default="scope">{{ scope.row.uploadedByName || scope.row.uploadedBy || '-' }}</template>
          </el-table-column>
          <el-table-column label="上传时间" align="center" width="170">
            <template #default="scope">{{ parseTime(scope.row.uploadedTime) || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="190">
            <template #default="scope">
              <el-tooltip content="预览（需填用途）" placement="top">
                <el-button
                  v-hasPermi="['recruit:attachment:preview']"
                  link
                  type="primary"
                  icon="View"
                  @click="openAttachmentAction(scope.row, 'preview')"
                ></el-button>
              </el-tooltip>
              <el-tooltip content="下载（需填用途）" placement="top">
                <el-button
                  v-hasPermi="['recruit:attachment:download']"
                  link
                  type="primary"
                  icon="Download"
                  @click="openAttachmentAction(scope.row, 'download')"
                ></el-button>
              </el-tooltip>
              <el-tooltip content="删除（逻辑删除，可追溯）" placement="top">
                <el-button
                  v-hasPermi="['recruit:attachment:delete']"
                  link
                  type="danger"
                  icon="Delete"
                  @click="handleAttachmentDelete(scope.row)"
                ></el-button>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detail.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 敏感明细查看：先填用途 -->
    <el-dialog v-model="sensitiveDialog.visible" title="查看背调敏感明细" width="720px" append-to-body>
      <template v-if="sensitiveDialog.step === 'purpose'">
        <el-alert
          class="dialog-alert"
          type="error"
          :closable="false"
          show-icon
          title="高敏感数据：查看背调明细需要独立权限 recruit:background:view-sensitive；必须填写用途，服务端会先写入 background_view 审计再返回明文。"
        />
        <el-form ref="sensitiveFormRef" :model="sensitiveForm" :rules="sensitiveRules" label-width="80px">
          <el-form-item label="背调ID">
            <el-input :model-value="sensitiveDialog.row.backgroundId" disabled />
          </el-form-item>
          <el-form-item label="用途" prop="purpose">
            <el-input
              v-model="sensitiveForm.purpose"
              type="textarea"
              :rows="3"
              maxlength="255"
              show-word-limit
              placeholder="请填写查看用途（必填，最长 255 字）"
            />
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <el-alert
          class="dialog-alert"
          type="success"
          :closable="false"
          show-icon
          title="本次查看已记录敏感操作审计（事件类型 background_view，包含操作人、用途、IP 与时间）。"
        />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="用途">{{ sensitiveForm.purpose }}</el-descriptions-item>
          <el-descriptions-item label="背调结论">
            {{ sensitiveDialog.detail.resultLabel || sensitiveDialog.detail.result || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="核查项">{{ sensitiveDialog.detail.checkItems || '-' }}</el-descriptions-item>
          <el-descriptions-item label="未通过原因分类">{{ sensitiveDialog.detail.failureReasonCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="免背调原因">{{ sensitiveDialog.detail.waiveReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="敏感说明（明文）">
            <pre class="sensitive-text">{{ sensitiveDialog.detail.detailCipher || '-' }}</pre>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="sensitiveDialog.step === 'purpose'" type="primary" :loading="sensitiveDialog.loading" @click="submitSensitive">
            确认查看
          </el-button>
          <el-button v-else @click="sensitiveDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 附件预览/下载用途 -->
    <el-dialog v-model="attachmentDialog.visible" :title="attachmentDialog.action === 'preview' ? '预览附件' : '下载附件'" width="520px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="附件预览与下载均需填写用途，服务端会校验业务记录权限并写入审计。"
      />
      <el-form ref="attachmentFormRef" :model="attachmentForm" :rules="attachmentRules" label-width="80px">
        <el-form-item label="附件">
          <el-input :model-value="attachmentDialog.row.originalName" disabled />
        </el-form-item>
        <el-form-item label="用途" prop="purpose">
          <el-input
            v-model="attachmentForm.purpose"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="请填写用途（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="attachmentDialog.loading" @click="submitAttachmentAction">确 定</el-button>
          <el-button @click="attachmentDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 邀约 / 报到 / 未报到登记（按背调关联的应聘记录展示与登记） -->
    <el-dialog v-model="registryDialog.visible" :title="registryTitle" width="640px" append-to-body>
      <el-alert class="dialog-alert" type="warning" :closable="false" show-icon :title="registryTip" />
      <el-descriptions :column="2" border size="small" class="detail-panel">
        <el-descriptions-item label="应聘编号">{{ registryDialog.application.applicationNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前阶段">
          {{ registryDialog.application.currentStageLabel || registryDialog.application.currentStage || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="邀约日期">{{ registryDialog.application.offerDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邀约结果">{{ registryDialog.application.offerResult || '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划报到">{{ registryDialog.application.planArrivalDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际到岗">{{ registryDialog.application.arrivalDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="未报到原因" :span="2">
          {{ registryDialog.application.noArrivalReason || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <el-form ref="registryFormRef" :model="registryForm" :rules="registryRules" label-width="120px">
        <template v-if="registryDialog.mode === 'offer'">
          <el-form-item label="邀约日期" prop="offerDate">
            <el-date-picker
              v-model="registryForm.offerDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择邀约日期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="邀约结果" prop="offerResult">
            <el-select
              v-model="registryForm.offerResult"
              placeholder="请选择或输入邀约结果编码"
              filterable
              allow-create
              default-first-option
              style="width: 100%"
            >
              <el-option v-for="item in OFFER_RESULT_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="计划报到日期" prop="planArrivalDate">
            <el-date-picker
              v-model="registryForm.planArrivalDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择计划报到日期"
              style="width: 100%"
            />
          </el-form-item>
        </template>
        <template v-else-if="registryDialog.mode === 'arrival'">
          <el-form-item label="实际报到日期" prop="arrivalDate">
            <el-date-picker
              v-model="registryForm.arrivalDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择实际报到日期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="计入计划任务" prop="planItemId">
            <el-input v-model="registryForm.planItemId" placeholder="月度计划任务ID，可为空（由后端判定）" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="未报到原因" prop="noArrivalReason">
            <el-input
              v-model="registryForm.noArrivalReason"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="必填，最长 500 字"
            />
          </el-form-item>
          <el-form-item label="下次跟进时间" prop="nextFollowTime">
            <el-date-picker
              v-model="registryForm.nextFollowTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="可为空"
              style="width: 100%"
            />
          </el-form-item>
        </template>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="registryForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="registryDialog.submitting" @click="submitRegistry">确 定</el-button>
          <el-button @click="registryDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 背调负责人选择 -->
    <UserSelect ref="checkerSelectRef" :multiple="false" @confirm-call-back="handleCheckerSelected" />
  </div>
</template>

<script setup lang="ts">
import {
  getApplication,
  registerArrival,
  registerNoArrival,
  registerOffer
} from '@/api/hrtalent/application';
import type { HrApplicationVO } from '@/api/hrtalent/application/types';
import {
  addBackground,
  delAttachment,
  downloadAttachment,
  getBackground,
  getBackgroundDetail,
  listAttachment,
  listBackground,
  previewAttachment,
  updateBackground
} from '@/api/hrtalent/background';
import type {
  HrAttachmentVO,
  HrBackgroundDetailVO,
  HrBackgroundForm,
  HrBackgroundQuery,
  HrBackgroundVO
} from '@/api/hrtalent/background/types';
import UserSelect from '@/components/UserSelect/index.vue';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { checkPermi } from '@/utils/permission';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'HrBackground' });

const { recruit_background_result, recruit_background_status, recruit_background_failure_reason } = toRefs<any>(
  useDict('recruit_background_result', 'recruit_background_status', 'recruit_background_failure_reason')
);

/** 邀约结果可选编码（DDL 注释给出 accepted/rejected，无对应字典，允许自定义输入） */
const OFFER_RESULT_OPTIONS = [
  { label: '已接受（accepted）', value: 'accepted' },
  { label: '已拒绝（rejected）', value: 'rejected' }
];

const backgroundList = ref<HrBackgroundVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const submitting = ref(false);
const backgroundFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();
const sensitiveFormRef = ref<ElFormInstance>();
const attachmentFormRef = ref<ElFormInstance>();
const registryFormRef = ref<ElFormInstance>();
const checkerSelectRef = ref<InstanceType<typeof UserSelect>>();
const checkTimeRange = ref<[string, string] | null>(null);

/** 是否具备受控附件读取权限（避免无权限时发起必然 403 的请求） */
const attachmentVisible = computed(() => checkPermi(['recruit:attachment:preview']));

const initFormData: HrBackgroundForm = {
  backgroundId: undefined,
  applicationId: undefined,
  authorizedFlag: '0',
  authorizeTime: undefined,
  checkerId: undefined,
  checkTime: undefined,
  checkStartDate: undefined,
  checkEndDate: undefined,
  result: undefined,
  failureReasonCode: undefined,
  checkItems: '',
  detailCipher: '',
  waiveReason: '',
  status: 'draft',
  remark: ''
};

/** 结论为已豁免时免背调原因必填；结论为不通过时未通过原因分类必填（对齐后端校验） */
const validateWaiveReason = (_rule: any, _value: any, callback: any) => {
  if (form.value.result === 'waived' && !form.value.waiveReason) {
    callback(new Error('背调结论为已豁免时必须填写免背调原因'));
    return;
  }
  callback();
};

const validateFailureReason = (_rule: any, _value: any, callback: any) => {
  if (form.value.result === 'fail' && !form.value.failureReasonCode) {
    callback(new Error('背调结论为不通过时必须填写未通过原因分类'));
    return;
  }
  callback();
};

const data = reactive<PageData<HrBackgroundForm, HrBackgroundQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    applicationId: undefined,
    checkerId: undefined,
    result: undefined,
    status: undefined,
    authorizedFlag: undefined,
    failureReasonCode: undefined
  },
  rules: {
    applicationId: [{ required: true, message: '应聘记录ID不能为空', trigger: 'blur' }],
    authorizedFlag: [{ required: true, message: '请选择是否已获得候选人授权', trigger: 'change' }],
    waiveReason: [{ validator: validateWaiveReason, trigger: 'blur' }],
    failureReasonCode: [{ validator: validateFailureReason, trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<HrBackgroundForm, HrBackgroundQuery>>(data);
const { dialog, resetForm, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: backgroundFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  pageSizeKey: 'pageSize',
  initialPageSize: 10,
  resetExtras: () => {
    checkTimeRange.value = null;
  },
  afterReset: () => handleQuery()
});

const getList = async () => {
  await withLoading(async () => {
    queryParams.value.checkTimeBegin = checkTimeRange.value?.[0] || undefined;
    queryParams.value.checkTimeEnd = checkTimeRange.value?.[1] || undefined;
    const res = await listBackground(queryParams.value);
    backgroundList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
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
  resetForm();
  showDialog('新增背调记录');
};

const handleUpdate = async (row: HrBackgroundVO) => {
  resetForm();
  const res = await getBackground(row.backgroundId!);
  const detailRow = res.data || row;
  Object.assign(form.value, initFormData);
  Object.assign(form.value, {
    backgroundId: detailRow.backgroundId,
    applicationId: detailRow.applicationId,
    authorizedFlag: detailRow.authorizedFlag ?? '0',
    authorizeTime: detailRow.authorizeTime,
    checkerId: detailRow.checkerId,
    checkTime: detailRow.checkTime,
    checkStartDate: detailRow.checkStartDate,
    checkEndDate: detailRow.checkEndDate,
    result: detailRow.result,
    failureReasonCode: detailRow.failureReasonCode,
    checkItems: detailRow.checkItems,
    // 明细不随普通详情返回，留空表示不修改
    detailCipher: '',
    waiveReason: detailRow.waiveReason,
    status: detailRow.status,
    remark: detailRow.remark
  });
  showDialog('修改背调记录');
};

const submitForm = () => {
  backgroundFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    submitting.value = true;
    try {
      const payload: HrBackgroundForm = { ...form.value };
      // 明细留空表示保持原值（后端 null 不覆盖）
      if (!payload.detailCipher) {
        payload.detailCipher = undefined;
      }
      if (payload.result !== 'fail') {
        payload.failureReasonCode = undefined;
      }
      if (payload.result !== 'waived') {
        payload.waiveReason = undefined;
      }
      if (payload.backgroundId) {
        await updateBackground(payload);
        modal.msgSuccess('修改成功');
      } else {
        await addBackground(payload);
        modal.msgSuccess('新增成功');
      }
      closeDialog();
      await getList();
    } finally {
      submitting.value = false;
    }
  });
};

const openCheckerSelect = () => checkerSelectRef.value?.open();

const handleCheckerSelected = (users: any[]) => {
  const user = users?.[0];
  if (user) {
    form.value.checkerId = user.userId;
  }
};

/* ------------------------------ 详情与受控附件 ------------------------------ */

const detail = reactive<{ visible: boolean; row: Partial<HrBackgroundVO> }>({ visible: false, row: {} });
const attachmentLoading = ref(false);
const attachmentList = ref<HrAttachmentVO[]>([]);

const loadAttachments = async (bizId: string | number) => {
  if (!attachmentVisible.value) {
    return;
  }
  attachmentLoading.value = true;
  attachmentList.value = [];
  try {
    const res = await listAttachment({ bizType: 'background', bizId, currentFlag: '1', pageNum: 1, pageSize: 50 });
    attachmentList.value = res.data?.rows || [];
  } catch {
    // 无权限或接口异常时保持空列表，拦截器已提示
    attachmentList.value = [];
  } finally {
    attachmentLoading.value = false;
  }
};

const handleDetail = async (row: HrBackgroundVO) => {
  const res = await getBackground(row.backgroundId!);
  detail.row = res.data || row;
  detail.visible = true;
  await loadAttachments(detail.row.backgroundId!);
};

/* ------------------------------ 敏感明细 ------------------------------ */

const sensitiveDialog = reactive<{
  visible: boolean;
  loading: boolean;
  step: 'purpose' | 'result';
  row: Partial<HrBackgroundVO>;
  detail: Partial<HrBackgroundDetailVO>;
}>({ visible: false, loading: false, step: 'purpose', row: {}, detail: {} });

const sensitiveForm = ref<{ purpose: string }>({ purpose: '' });
const sensitiveRules: ElFormRules = {
  purpose: [{ required: true, message: '查看用途不能为空', trigger: 'blur' }]
};

const openSensitive = (row: HrBackgroundVO) => {
  sensitiveDialog.row = row;
  sensitiveDialog.detail = {};
  sensitiveDialog.step = 'purpose';
  sensitiveForm.value.purpose = '';
  sensitiveDialog.visible = true;
};

const submitSensitive = () => {
  sensitiveFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    sensitiveDialog.loading = true;
    try {
      const res = await getBackgroundDetail(sensitiveDialog.row.backgroundId!, { purpose: sensitiveForm.value.purpose });
      sensitiveDialog.detail = res.data || {};
      sensitiveDialog.step = 'result';
      modal.msgSuccess('已获取背调明细，本次查看已记录审计');
    } finally {
      sensitiveDialog.loading = false;
    }
  });
};

/* ------------------------------ 附件预览 / 下载 ------------------------------ */

/** 二进制响应在拦截器中原样透传 AxiosResponse，这里统一取出 Blob */
const toBlob = (resp: any): Blob => {
  const data = resp && resp.data !== undefined ? resp.data : resp;
  return data instanceof Blob ? data : new Blob([data]);
};

/** 触发浏览器保存文件 */
const saveBlobAsFile = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

/** 新窗口内联预览 */
const openBlobInNewTab = (blob: Blob) => {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank');
  window.setTimeout(() => URL.revokeObjectURL(url), 60000);
};

const attachmentDialog = reactive<{
  visible: boolean;
  loading: boolean;
  action: 'preview' | 'download';
  row: Partial<HrAttachmentVO>;
}>({ visible: false, loading: false, action: 'preview', row: {} });

const attachmentForm = ref<{ purpose: string }>({ purpose: '' });
const attachmentRules: ElFormRules = {
  purpose: [{ required: true, message: '用途不能为空', trigger: 'blur' }]
};

const openAttachmentAction = (row: HrAttachmentVO, action: 'preview' | 'download') => {
  attachmentDialog.row = row;
  attachmentDialog.action = action;
  attachmentForm.value.purpose = '';
  attachmentDialog.visible = true;
};

const submitAttachmentAction = () => {
  attachmentFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    attachmentDialog.loading = true;
    try {
      const attachmentId = attachmentDialog.row.attachmentId!;
      const fileName = attachmentDialog.row.originalName || `attachment-${attachmentId}`;
      if (attachmentDialog.action === 'preview') {
        const resp = await previewAttachment(attachmentId, attachmentForm.value.purpose);
        openBlobInNewTab(toBlob(resp));
        modal.msgSuccess('已打开预览，本次操作已记录审计');
      } else {
        const resp = await downloadAttachment(attachmentId, attachmentForm.value.purpose);
        saveBlobAsFile(toBlob(resp), fileName);
        modal.msgSuccess('已开始下载，本次操作已记录审计');
      }
      attachmentDialog.visible = false;
    } finally {
      attachmentDialog.loading = false;
    }
  });
};

/**
 * 逻辑删除附件
 *
 * 后端只置删除标志：**保留数据行与对象存储文件**，可追溯；菜单注释明确
 * 「集团招聘管理员亦不得物理删除」，因此确认文案不使用「永久删除」类措辞。
 */
const handleAttachmentDelete = async (row: HrAttachmentVO) => {
  try {
    await modal.confirm(
      `是否确认删除附件「${row.originalName || row.attachmentId}」？该操作为逻辑删除（仅置删除标志，保留数据行与对象存储文件，可追溯），不是物理删除。`
    );
  } catch {
    return;
  }
  await delAttachment(row.attachmentId!);
  modal.msgSuccess('已逻辑删除该附件（数据与文件均保留，可追溯）');
  if (detail.row.backgroundId) {
    await loadAttachments(detail.row.backgroundId);
  }
};

/* ------------------------------ 邀约 / 报到 / 未报到 ------------------------------ */

const registryDialog = reactive<{
  visible: boolean;
  submitting: boolean;
  mode: 'offer' | 'arrival' | 'noArrival';
  application: Partial<HrApplicationVO>;
}>({ visible: false, submitting: false, mode: 'offer', application: {} });

const registryForm = ref<{
  offerDate?: string;
  offerResult?: string;
  planArrivalDate?: string;
  arrivalDate?: string;
  planItemId?: string | number;
  noArrivalReason?: string;
  nextFollowTime?: string;
  remark?: string;
}>({});

const registryTitle = computed(() => {
  if (registryDialog.mode === 'offer') return '邀约登记';
  if (registryDialog.mode === 'arrival') return '报到登记';
  return '未报到登记';
});

const registryTip = computed(() => {
  if (registryDialog.mode === 'offer') {
    return '邀约登记会同时写入邀约结果与计划报到日期，是进入「待报到」的前置条件。';
  }
  if (registryDialog.mode === 'arrival') {
    return '登记实际报到后，后端会在同一事务内把到岗计入月度计划任务并刷新任务状态。';
  }
  return '未报到必须填写原因；已登记实际报到的应聘记录不能再次登记未报到。';
});

const registryRules = computed<ElFormRules>(() => {
  if (registryDialog.mode === 'offer') {
    return {
      offerResult: [{ required: true, message: '邀约结果不能为空', trigger: 'change' }],
      planArrivalDate: [{ required: true, message: '计划报到日期不能为空', trigger: 'change' }]
    };
  }
  if (registryDialog.mode === 'arrival') {
    return {
      arrivalDate: [{ required: true, message: '实际报到日期不能为空', trigger: 'change' }]
    };
  }
  return {
    noArrivalReason: [{ required: true, message: '未报到原因不能为空', trigger: 'blur' }]
  };
});

const openRegistry = async (row: HrBackgroundVO, mode: 'offer' | 'arrival' | 'noArrival') => {
  if (!row.applicationId) {
    modal.msgWarning('该背调记录未关联应聘记录');
    return;
  }
  registryDialog.application = {};
  registryDialog.mode = mode;
  registryForm.value = {};
  const res = await getApplication(row.applicationId);
  const application = res.data;
  if (!application) {
    modal.msgError('应聘记录不存在或已删除');
    return;
  }
  registryDialog.application = application;
  registryForm.value.offerDate = application.offerDate;
  registryForm.value.offerResult = application.offerResult;
  registryForm.value.planArrivalDate = application.planArrivalDate;
  registryForm.value.arrivalDate = application.arrivalDate;
  registryDialog.visible = true;
};

const submitRegistry = () => {
  registryFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    const application = registryDialog.application;
    registryDialog.submitting = true;
    try {
      if (registryDialog.mode === 'offer') {
        await registerOffer(application.applicationId!, {
          offerDate: registryForm.value.offerDate,
          offerResult: registryForm.value.offerResult,
          planArrivalDate: registryForm.value.planArrivalDate,
          remark: registryForm.value.remark,
          version: application.version
        });
        modal.msgSuccess('邀约登记成功');
      } else if (registryDialog.mode === 'arrival') {
        await registerArrival(application.applicationId!, {
          arrivalDate: registryForm.value.arrivalDate,
          planItemId: registryForm.value.planItemId,
          remark: registryForm.value.remark,
          version: application.version
        });
        modal.msgSuccess('报到登记成功');
      } else {
        await registerNoArrival(application.applicationId!, {
          noArrivalReason: registryForm.value.noArrivalReason,
          nextFollowTime: registryForm.value.nextFollowTime,
          remark: registryForm.value.remark,
          version: application.version
        });
        modal.msgSuccess('未报到登记成功');
      }
      registryDialog.visible = false;
      await getList();
    } finally {
      registryDialog.submitting = false;
    }
  });
};

const handleMore = (row: HrBackgroundVO, command: string) => {
  if (command === 'offer') {
    openRegistry(row, 'offer');
  } else if (command === 'arrival') {
    openRegistry(row, 'arrival');
  } else if (command === 'noArrival') {
    openRegistry(row, 'noArrival');
  }
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.dialog-alert {
  margin-bottom: 12px;
}

.detail-panel {
  margin-bottom: 12px;
}

.sensitive-mask {
  color: var(--app-text-muted);
  font-size: 12px;
}

.sensitive-text {
  margin: 0;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.6;
}
</style>
