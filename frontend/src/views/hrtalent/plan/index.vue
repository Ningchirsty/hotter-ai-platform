<template>
  <div class="p-2 app-container hrtalent-plan-page">
    <PageHeading title="招聘管理" subtitle="公司月度计划表头、月度任务与跨月结转链" module="hrtalent" />

    <!-- 计划表头检索 -->
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>计划检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="计划编号" prop="planNo">
            <el-input v-model="queryParams.planNo" placeholder="请输入计划编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="公司ID" prop="companyDeptId">
            <el-input
              v-model="queryParams.companyDeptId"
              placeholder="公司（部门）ID"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="计划月份" prop="planMonth">
            <el-date-picker
              v-model="queryParams.planMonth"
              type="month"
              value-format="YYYY-MM"
              placeholder="请选择月份"
              clearable
              style="width: 160px"
            />
          </el-form-item>
          <el-form-item label="计划状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 150px">
              <el-option v-for="dict in recruit_plan_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
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
            <span class="panel-kicker">Monthly Plan</span>
            <h3>公司月度计划</h3>
            <p>
              共 {{ total }} 条记录；一个公司 + 一个自然月只有一张计划表头。
              当月每次新增招聘任务都创建新记录，相同公司/部门/岗位只用于相似提示，绝不合并。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['recruit:plan:add']" type="primary" icon="Plus" @click="handleAddPlan">
              新增计划
            </el-button>
            <el-button icon="Refresh" plain @click="getList">刷新</el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="planList">
        <el-table-column label="计划编号" align="center" prop="planNo" width="180" show-overflow-tooltip />
        <el-table-column label="公司" align="center" width="150" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.companyName || scope.row.companyDeptId || '-' }}</template>
        </el-table-column>
        <el-table-column label="计划月份" align="center" prop="planMonth" width="110" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="recruit_plan_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="计划总人数" align="center" prop="totalPlanQty" width="110" />
        <el-table-column label="累计到岗" align="center" prop="totalCreditedQty" width="100" />
        <el-table-column label="累计剩余" align="center" prop="totalRemainingQty" width="100" />
        <el-table-column label="已生成任务" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.generatedFlag === '1' ? 'success' : 'info'">
              {{ scope.row.generatedFlag === '1' ? '已生成' : '未生成' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.confirmedTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="任务列表" placement="top">
              <el-button
                v-hasPermi="['recruit:plan:query']"
                link
                type="primary"
                icon="List"
                @click="openTasks(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip v-if="scope.row.status === 'draft'" content="确认计划" placement="top">
              <el-button
                v-hasPermi="['recruit:plan:confirm']"
                link
                type="success"
                icon="Check"
                @click="handlePlanAction(scope.row, 'confirm')"
              ></el-button>
            </el-tooltip>
            <el-tooltip v-if="scope.row.status === 'executing'" content="关闭计划" placement="top">
              <el-button
                v-hasPermi="['recruit:plan:close']"
                link
                type="danger"
                icon="CircleClose"
                @click="handlePlanAction(scope.row, 'close')"
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

    <!-- 计划任务抽屉：表头下的任务列表 -->
    <el-drawer v-model="taskDrawer.visible" size="76%" :title="taskDrawerTitle" append-to-body>
      <div class="drawer-shell" v-loading="taskDrawer.loading">
        <el-card shadow="hover" class="inner-card">
          <template #header>
            <div class="toolbar-shell">
              <div class="table-heading">
                <h3>月度计划任务</h3>
                <p>
                  计划人数 {{ taskDrawer.plan.totalPlanQty ?? 0 }} 人，累计到岗
                  {{ taskDrawer.plan.totalCreditedQty ?? 0 }} 人，累计剩余
                  {{ taskDrawer.plan.totalRemainingQty ?? 0 }} 人。
                  新增任务始终新建记录，不因公司/部门/岗位相同而合并。
                </p>
              </div>
              <div class="toolbar-actions">
                <el-button
                  v-hasPermi="['recruit:plan:add']"
                  type="primary"
                  icon="Plus"
                  @click="openItemForm"
                >
                  新增独立任务
                </el-button>
                <el-button icon="Refresh" plain @click="getItemList">刷新</el-button>
              </div>
            </div>
          </template>

          <el-table v-loading="itemLoading" border class="data-table" :data="itemList">
            <el-table-column label="任务编号" align="center" prop="itemNo" width="170" show-overflow-tooltip />
            <el-table-column label="来源类型" align="center" width="100">
              <template #default="scope">
                <dict-tag :options="recruit_plan_source_type" :value="scope.row.sourceType" />
              </template>
            </el-table-column>
            <el-table-column label="岗位" align="center" prop="jobName" width="140" show-overflow-tooltip />
            <el-table-column label="用工部门" align="center" width="130" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.useDeptName || scope.row.useDeptId || '-' }}</template>
            </el-table-column>
            <el-table-column label="计划人数" align="center" prop="planQty" width="90" />
            <el-table-column label="已到岗" align="center" prop="creditedArrivalQty" width="80" />
            <el-table-column label="剩余" align="center" prop="remainingQty" width="70" />
            <el-table-column label="主状态" align="center" width="120">
              <template #default="scope">
                <el-tag :type="displayStatus(scope.row).type">{{ displayStatus(scope.row).label }}</el-tag>
                <!-- 部分完成：不覆盖执行阶段，仅追加标签 -->
                <el-tag
                  v-if="isPartial(scope.row)"
                  class="partial-tag"
                  type="warning"
                  size="small"
                >
                  部分完成
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="控制状态" align="center" width="100">
              <template #default="scope">
                <dict-tag :options="recruit_plan_control_status" :value="scope.row.controlStatus" />
              </template>
            </el-table-column>
            <el-table-column label="执行阶段" align="center" width="110">
              <template #default="scope">
                <dict-tag :options="recruit_plan_execution_status" :value="scope.row.executionStatus" />
              </template>
            </el-table-column>
            <el-table-column label="完成状态" align="center" width="120">
              <template #default="scope">
                <dict-tag :options="recruit_plan_completion_status" :value="scope.row.completionStatus" />
              </template>
            </el-table-column>
            <el-table-column label="可结转" align="center" width="80">
              <template #default="scope">
                <el-tag :type="scope.row.carryoverEnabled === '1' ? 'success' : 'info'">
                  {{ scope.row.carryoverEnabled === '1' ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="负责人" align="center" width="110" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.ownerName || scope.row.ownerId || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="250" align="center" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-tooltip content="编辑任务" placement="top">
                  <el-button
                    v-hasPermi="['recruit:plan:edit']"
                    link
                    type="primary"
                    icon="Edit"
                    @click="openItemEdit(scope.row)"
                  ></el-button>
                </el-tooltip>
                <el-tooltip content="结转链" placement="top">
                  <el-button
                    v-hasPermi="['recruit:plan:query']"
                    link
                    type="primary"
                    icon="Link"
                    @click="openRolloverChain(scope.row)"
                  ></el-button>
                </el-tooltip>
                <el-tooltip content="状态变更日志（只追加，可追溯）" placement="top">
                  <el-button
                    v-hasPermi="['recruit:plan:query']"
                    link
                    type="primary"
                    icon="Clock"
                    @click="openStatusLogs(scope.row)"
                  ></el-button>
                </el-tooltip>
                <el-tooltip content="重算状态" placement="top">
                  <el-button
                    v-hasPermi="['recruit:plan:edit']"
                    link
                    type="primary"
                    icon="Refresh"
                    @click="handleRefreshStatus(scope.row)"
                  ></el-button>
                </el-tooltip>
                <el-dropdown
                  v-if="itemActionOptions(scope.row).length"
                  trigger="click"
                  @command="cmd => openItemAction(scope.row, cmd)"
                >
                  <el-button link type="primary" icon="More"></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                        v-for="item in itemActionOptions(scope.row)"
                        :key="item.action"
                        :command="item.action"
                      >
                        {{ item.label }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>

          <pagination
            v-show="itemTotal > 0"
            v-model:page="itemQuery.pageNum"
            v-model:limit="itemQuery.pageSize"
            :total="itemTotal"
            @pagination="getItemList"
          />
        </el-card>
      </div>
    </el-drawer>

    <!-- 新增计划表头 -->
    <el-dialog v-model="planDialog.visible" title="新增公司月度计划" width="620px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="一个公司 + 一个自然月只有一张计划表头；若已存在，后端会复用或给出中文提示，前端不做合并。"
      />
      <el-form ref="planFormRef" :model="planForm" :rules="planRules" label-width="110px">
        <el-form-item label="公司部门ID" prop="companyDeptId">
          <el-input v-model="planForm.companyDeptId" placeholder="平台部门ID（bigint，按字符串提交）" />
        </el-form-item>
        <el-form-item label="公司名称" prop="companyName">
          <el-input v-model="planForm.companyName" placeholder="公司名称快照，可为空" />
        </el-form-item>
        <el-form-item label="计划月份" prop="planMonth">
          <el-date-picker
            v-model="planForm.planMonth"
            type="month"
            value-format="YYYY-MM"
            placeholder="请选择计划月份"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="planForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="planSubmitting" @click="submitPlan">确 定</el-button>
          <el-button @click="planDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 新增独立任务 -->
    <el-dialog v-model="itemDialog.visible" title="新增月度计划任务" width="760px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="新增任务永远创建新记录：即使公司、部门、岗位、人数完全相同也不会合并、覆盖或复用；相似计划仅用于提示。"
      />
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="120px">
        <el-form-item label="所属计划">
          <el-input :model-value="taskDrawer.plan.planNo" disabled />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="岗位名称" prop="jobName">
              <el-input v-model="itemForm.jobName" placeholder="请输入岗位名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划人数" prop="planQty">
              <el-input-number v-model="itemForm.planQty" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="公司部门ID" prop="companyDeptId">
              <el-input v-model="itemForm.companyDeptId" placeholder="默认取所属计划表头" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公司名称" prop="companyName">
              <el-input v-model="itemForm.companyName" placeholder="公司名称快照" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用工部门ID" prop="useDeptId">
              <el-input v-model="itemForm.useDeptId" placeholder="用工部门ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用工部门名称" prop="useDeptName">
              <el-input v-model="itemForm.useDeptName" placeholder="用工部门名称快照" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="来源需求ID" prop="demandId">
              <el-input v-model="itemForm.demandId" placeholder="可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联岗位ID" prop="jobId">
              <el-input v-model="itemForm.jobId" placeholder="可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="任务负责人ID" prop="ownerId">
              <el-input v-model="itemForm.ownerId" placeholder="可为空" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="允许下月结转" prop="carryoverEnabled">
          <el-switch v-model="itemForm.carryoverEnabled" active-value="1" inactive-value="0" />
          <span class="form-tip">关闭后该任务不会在月末自动结转到下月。</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="itemForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="itemSubmitting" @click="submitItem">确 定</el-button>
          <el-button @click="itemDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 相似计划提示：只提示，不合并 -->
    <el-dialog v-model="similarDialog.visible" title="存在相似计划，请确认" width="760px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="以下任务与本次填写属于同一公司、部门与岗位。系统不会合并或复用它们，请确认后仍将新建一条独立任务。"
      />
      <el-table border size="small" :data="similarList">
        <el-table-column label="任务编号" align="center" prop="itemNo" width="170" show-overflow-tooltip />
        <el-table-column label="计划月份" align="center" prop="planMonth" width="110" />
        <el-table-column label="岗位" align="center" prop="jobName" show-overflow-tooltip />
        <el-table-column label="计划人数" align="center" prop="planQty" width="90" />
        <el-table-column label="剩余人数" align="center" prop="remainingQty" width="90" />
        <el-table-column label="来源类型" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="recruit_plan_source_type" :value="scope.row.sourceType" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="itemSubmitting" @click="confirmAddItem">仍然新增</el-button>
          <el-button @click="similarDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 编辑任务 -->
    <el-dialog v-model="itemEditDialog.visible" title="编辑月度计划任务" width="640px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="计划月份与来源任务不可修改：原月任务结转后保留，禁止通过修改 plan_month 把任务挪到下月。"
      />
      <el-form ref="itemEditFormRef" :model="itemEditForm" :rules="itemEditRules" label-width="120px">
        <el-form-item label="任务编号">
          <el-input :model-value="itemEditForm.itemNo" disabled />
        </el-form-item>
        <el-form-item label="计划月份">
          <el-input :model-value="itemEditForm.planMonth" disabled />
        </el-form-item>
        <el-form-item label="计划人数" prop="planQty">
          <el-input-number v-model="itemEditForm.planQty" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="任务负责人ID" prop="ownerId">
          <el-input v-model="itemEditForm.ownerId" placeholder="可为空" />
        </el-form-item>
        <el-form-item label="用工部门ID" prop="useDeptId">
          <el-input v-model="itemEditForm.useDeptId" placeholder="可为空" />
        </el-form-item>
        <el-form-item label="用工部门名称" prop="useDeptName">
          <el-input v-model="itemEditForm.useDeptName" placeholder="可为空" />
        </el-form-item>
        <el-form-item label="允许下月结转" prop="carryoverEnabled">
          <el-switch v-model="itemEditForm.carryoverEnabled" active-value="1" inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="itemEditForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="itemEditSubmitting" @click="submitItemEdit">确 定</el-button>
          <el-button @click="itemEditDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 任务动作：暂停/恢复/取消（填写原因） -->
    <el-dialog v-model="itemActionDialog.visible" :title="itemActionDialog.title" width="560px" append-to-body>
      <el-alert class="dialog-alert" type="warning" :closable="false" show-icon :title="itemActionDialog.tip" />
      <el-form ref="itemActionFormRef" :model="itemActionForm" :rules="itemActionRules" label-width="90px">
        <el-form-item label="任务编号">
          <el-input :model-value="itemActionDialog.row.itemNo" disabled />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input
            v-model="itemActionForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请填写操作原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="itemActionDialog.submitting" @click="submitItemAction">确 定</el-button>
          <el-button @click="itemActionDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 跨月结转链 -->
    <el-dialog v-model="chainDialog.visible" title="跨月结转链" width="900px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="结转链按月份先后展示同一条业务从原任务到下月任务的传递过程；原任务不会被修改，也不会被移动到下月。"
      />
      <el-table v-loading="chainDialog.loading" border size="small" :data="chainList">
        <el-table-column label="计划月份" align="center" prop="planMonth" width="110" />
        <el-table-column label="任务编号" align="center" prop="itemNo" width="170" show-overflow-tooltip />
        <el-table-column label="来源类型" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="recruit_plan_source_type" :value="scope.row.sourceType" />
          </template>
        </el-table-column>
        <el-table-column label="前置任务" align="center" width="170" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.previousItemNo || scope.row.previousPlanItemId || '-' }}</template>
        </el-table-column>
        <el-table-column label="计划人数" align="center" prop="planQty" width="90" />
        <el-table-column label="已到岗" align="center" prop="creditedArrivalQty" width="80" />
        <el-table-column label="剩余" align="center" prop="remainingQty" width="70" />
        <el-table-column label="主状态" align="center" width="110">
          <template #default="scope">
            <el-tag :type="displayStatus(scope.row).type">{{ displayStatus(scope.row).label }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="chainDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 计划任务状态变更日志（追加型，只读，用于追溯状态为何变化） -->
    <el-dialog v-model="statusLogDialog.visible" title="计划任务状态变更日志" width="1020px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="状态变更日志为追加型记录（只插入、不修改）：每次自动刷新或管理员手工重算都会留痕，展示「原状态 → 新状态」、触发事件、刷新时间与操作人，用于回答「这个任务的状态为什么变了」。"
      />
      <el-descriptions :column="3" border size="small" class="detail-panel">
        <el-descriptions-item label="任务编号">{{ statusLogDialog.row.itemNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ statusLogDialog.row.jobName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划月份">{{ statusLogDialog.row.planMonth || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-loading="statusLogDialog.loading" border size="small" :data="statusLogList">
        <el-table-column label="刷新时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.refreshTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="触发事件" align="center" width="130">
          <template #default="scope">
            {{ triggerEventText(scope.row.triggerEvent) }}
          </template>
        </el-table-column>
        <el-table-column label="自动执行阶段变化" align="center" min-width="200">
          <template #default="scope">
            <dict-tag
              v-if="scope.row.fromExecutionStatus"
              :options="recruit_plan_execution_status"
              :value="scope.row.fromExecutionStatus"
            />
            <span v-else>-</span>
            <span class="log-arrow">→</span>
            <dict-tag
              v-if="scope.row.toExecutionStatus"
              :options="recruit_plan_execution_status"
              :value="scope.row.toExecutionStatus"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="完成状态变化" align="center" min-width="200">
          <template #default="scope">
            <dict-tag
              v-if="scope.row.fromCompletionStatus"
              :options="recruit_plan_completion_status"
              :value="scope.row.fromCompletionStatus"
            />
            <span v-else>-</span>
            <span class="log-arrow">→</span>
            <dict-tag
              v-if="scope.row.toCompletionStatus"
              :options="recruit_plan_completion_status"
              :value="scope.row.toCompletionStatus"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" align="center" width="110">
          <template #default="scope">
            {{ scope.row.operatorName || scope.row.operatorId || '自动刷新' }}
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
      </el-table>
      <span v-if="!statusLogDialog.loading && !statusLogList.length" class="empty-text">暂无状态变更日志</span>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="statusLogDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  actionPlan,
  actionPlanItem,
  addPlan,
  addPlanItem,
  getPlan,
  listPlan,
  listPlanItemByPlan,
  listPlanItemRolloverChain,
  listPlanItemStatusLogs,
  listSimilarPlanItem,
  refreshPlanItemStatus,
  updatePlanItem
} from '@/api/hrtalent/plan';
import type {
  HrPlanForm,
  HrPlanItemEditForm,
  HrPlanItemForm,
  HrPlanItemQuery,
  HrPlanItemStatusLogVO,
  HrPlanItemVO,
  HrPlanQuery,
  HrPlanVO,
  PlanAction,
  PlanItemAction
} from '@/api/hrtalent/plan/types';
import { PLAN_ITEM_TRIGGER_EVENT_TEXT } from '@/api/hrtalent/plan/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'HrPlan' });

const {
  recruit_plan_status,
  recruit_plan_source_type,
  recruit_plan_control_status,
  recruit_plan_execution_status,
  recruit_plan_completion_status
} = toRefs<any>(
  useDict(
    'recruit_plan_status',
    'recruit_plan_source_type',
    'recruit_plan_control_status',
    'recruit_plan_execution_status',
    'recruit_plan_completion_status'
  )
);

/** 任务动作中文名与说明 */
const ITEM_ACTION_META: Record<PlanItemAction, { label: string; tip: string }> = {
  pause: { label: '暂停', tip: '暂停为人工控制状态，任务保留但不再计入推进，请填写原因。' },
  resume: { label: '恢复', tip: '恢复后任务回到正常推进状态，请填写原因。' },
  cancel: { label: '取消', tip: '取消后任务不再参与结转与完成统计，请填写原因。' }
};

const planList = ref<HrPlanVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();

const queryParams = ref<HrPlanQuery>({
  pageNum: 1,
  pageSize: 10,
  planNo: undefined,
  companyDeptId: undefined,
  planMonth: undefined,
  status: undefined,
  params: {}
});

const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

// ------------------------------------------------------------------ 计划表头

/** 查询计划表头列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listPlan(queryParams.value);
    planList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const planDialog = reactive({ visible: false });
const planSubmitting = ref(false);
const planFormRef = ref<ElFormInstance>();
const planForm = ref<HrPlanForm>({ companyDeptId: undefined, companyName: '', planMonth: undefined, remark: '' });
const planRules = {
  companyDeptId: [{ required: true, message: '公司（部门）ID 不能为空', trigger: 'blur' }],
  planMonth: [{ required: true, message: '计划月份不能为空', trigger: 'change' }]
};

/** 新增计划表头 */
const handleAddPlan = () => {
  planForm.value = { companyDeptId: undefined, companyName: '', planMonth: undefined, remark: '' };
  planDialog.visible = true;
};

/** 提交新增计划表头 */
const submitPlan = () => {
  planFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    planSubmitting.value = true;
    try {
      await addPlan({ ...planForm.value });
      modal.msgSuccess('新增成功');
      planDialog.visible = false;
      await getList();
    } finally {
      planSubmitting.value = false;
    }
  });
};

/** 计划表头动作：确认 / 关闭 */
const handlePlanAction = async (row: HrPlanVO, action: PlanAction) => {
  const label = action === 'confirm' ? '确认' : '关闭';
  try {
    await modal.confirm(`是否确认${label}计划「${row.planNo || row.planId}」？`);
  } catch {
    return;
  }
  await actionPlan(row.planId!, action);
  modal.msgSuccess(`${label}成功`);
  await getList();
};

// ------------------------------------------------------------------ 计划任务

const taskDrawer = reactive<{ visible: boolean; loading: boolean; plan: Partial<HrPlanVO> }>({
  visible: false,
  loading: false,
  plan: {}
});
const itemList = ref<HrPlanItemVO[]>([]);
const itemLoading = ref(false);
const itemTotal = ref(0);
const itemQuery = ref<HrPlanItemQuery>({
  pageNum: 1,
  pageSize: 10,
  planId: undefined,
  params: {}
});

const taskDrawerTitle = computed(() =>
  taskDrawer.plan.planNo ? `计划任务 · ${taskDrawer.plan.planNo}` : '计划任务'
);

/** 打开任务抽屉：同时刷新表头汇总信息 */
const openTasks = async (row: HrPlanVO) => {
  taskDrawer.plan = row;
  taskDrawer.visible = true;
  itemQuery.value = { ...itemQuery.value, pageNum: 1, planId: row.planId };
  await getItemList();
  await refreshPlanHeader(row.planId!);
};

/** 刷新抽屉顶部的表头汇总（累计计划/到岗/剩余） */
const refreshPlanHeader = async (planId: string | number) => {
  try {
    const res = await getPlan(planId);
    if (res.data) {
      taskDrawer.plan = res.data;
    }
  } catch {
    // 拦截器已提示错误，保留列表行数据
  }
};

/** 查询表头下的任务 */
const getItemList = async () => {
  if (!itemQuery.value.planId) {
    itemList.value = [];
    itemTotal.value = 0;
    return;
  }
  itemLoading.value = true;
  taskDrawer.loading = true;
  try {
    const res = await listPlanItemByPlan(itemQuery.value.planId, itemQuery.value);
    itemList.value = res.data?.rows || [];
    itemTotal.value = res.data?.total || 0;
  } catch {
    itemList.value = [];
    itemTotal.value = 0;
  } finally {
    itemLoading.value = false;
    taskDrawer.loading = false;
  }
};

/**
 * 主展示状态优先级（SPEC §4.2，与设计 §7.1.5 一致）：
 * 已取消 > 暂停 > 已完成(remaining_qty=0) > 已结转 > 待报到 > 待录用 > 面试中 > 招聘中 > 待启动
 */
const displayStatus = (row: Partial<HrPlanItemVO>): { label: string; type: 'primary' | 'success' | 'info' | 'warning' | 'danger' } => {
  if (row.controlStatus === 'cancelled') {
    return { label: '已取消', type: 'info' };
  }
  if (row.controlStatus === 'paused') {
    return { label: '暂停', type: 'warning' };
  }
  if ((row.remainingQty ?? 0) <= 0) {
    return { label: '已完成', type: 'success' };
  }
  if (row.completionStatus === 'rolled_over') {
    return { label: '已结转', type: 'info' };
  }
  switch (row.executionStatus) {
    case 'pending_arrival':
      return { label: '待报到', type: 'success' };
    case 'offer':
      return { label: '待录用', type: 'success' };
    case 'interviewing':
      return { label: '面试中', type: 'primary' };
    case 'recruiting':
      return { label: '招聘中', type: 'primary' };
    default:
      return { label: '待启动', type: 'info' };
  }
};

/** 部分完成：已计入到岗 > 0 且仍有剩余，追加标签而不覆盖执行阶段 */
const isPartial = (row: Partial<HrPlanItemVO>) => (row.creditedArrivalQty ?? 0) > 0 && (row.remainingQty ?? 0) > 0;

/** 任务可执行动作（按控制状态过滤） */
const itemActionOptions = (row: Partial<HrPlanItemVO>) => {
  if (row.controlStatus === 'cancelled') {
    return [];
  }
  if (row.controlStatus === 'paused') {
    return [
      { action: 'resume' as PlanItemAction, label: ITEM_ACTION_META.resume.label },
      { action: 'cancel' as PlanItemAction, label: ITEM_ACTION_META.cancel.label }
    ];
  }
  return [
    { action: 'pause' as PlanItemAction, label: ITEM_ACTION_META.pause.label },
    { action: 'cancel' as PlanItemAction, label: ITEM_ACTION_META.cancel.label }
  ];
};

// ------------------------------------------------------------------ 新增任务

const itemDialog = reactive({ visible: false });
const itemSubmitting = ref(false);
const itemFormRef = ref<ElFormInstance>();
const similarDialog = reactive({ visible: false });
const similarList = ref<HrPlanItemVO[]>([]);

const initItemForm = (): HrPlanItemForm => ({
  planId: undefined,
  demandId: undefined,
  jobId: undefined,
  companyDeptId: undefined,
  companyName: '',
  useDeptId: undefined,
  useDeptName: '',
  jobName: '',
  planMonth: undefined,
  planQty: 1,
  ownerId: undefined,
  carryoverEnabled: '1',
  remark: ''
});

const itemForm = ref<HrPlanItemForm>(initItemForm());
const itemRules = {
  jobName: [{ required: true, message: '岗位名称不能为空', trigger: 'blur' }],
  planQty: [{ required: true, message: '计划人数不能为空', trigger: 'change' }],
  companyDeptId: [{ required: true, message: '公司（部门）ID 不能为空', trigger: 'blur' }]
};

/** 打开「新增独立任务」：默认带出所属计划表头信息 */
const openItemForm = () => {
  itemForm.value = {
    ...initItemForm(),
    planId: taskDrawer.plan.planId,
    companyDeptId: taskDrawer.plan.companyDeptId,
    companyName: taskDrawer.plan.companyName,
    planMonth: taskDrawer.plan.planMonth
  };
  itemDialog.visible = true;
};

/** 提交：先做相似提示（仅提示，不合并） */
const submitItem = () => {
  itemFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    const similar = await loadSimilarItems();
    if (similar.length) {
      similarDialog.visible = true;
      return;
    }
    await doAddItem();
  });
};

/** 加载相似任务（同公司 + 部门 + 岗位） */
const loadSimilarItems = async () => {
  try {
    const res = await listSimilarPlanItem({
      companyDeptId: itemForm.value.companyDeptId,
      useDeptId: itemForm.value.useDeptId,
      jobName: itemForm.value.jobName,
      planMonth: itemForm.value.planMonth
    });
    similarList.value = res.data || [];
  } catch {
    // 相似提示失败不阻断新增
    similarList.value = [];
  }
  return similarList.value;
};

/** 用户确认「仍然新增」后落库 */
const confirmAddItem = async () => {
  similarDialog.visible = false;
  await doAddItem();
};

/** 真正新增任务：始终新建记录 */
const doAddItem = async () => {
  itemSubmitting.value = true;
  try {
    await addPlanItem(itemForm.value.planId!, { ...itemForm.value });
    modal.msgSuccess('新增成功（已创建独立任务，未与相似计划合并）');
    itemDialog.visible = false;
    await getItemList();
    await refreshPlanHeader(itemForm.value.planId!);
  } finally {
    itemSubmitting.value = false;
  }
};

// ------------------------------------------------------------------ 编辑任务

const itemEditDialog = reactive({ visible: false });
const itemEditSubmitting = ref(false);
const itemEditFormRef = ref<ElFormInstance>();
const itemEditForm = ref<HrPlanItemEditForm & { itemNo?: string; planMonth?: string }>({
  itemId: '',
  planQty: 0,
  ownerId: undefined,
  useDeptId: undefined,
  useDeptName: '',
  carryoverEnabled: '1',
  remark: ''
});
const itemEditRules = {
  planQty: [{ required: true, message: '计划人数不能为空', trigger: 'change' }]
};

/** 打开编辑弹窗（仅允许字段可改） */
const openItemEdit = (row: HrPlanItemVO) => {
  itemEditForm.value = {
    itemId: row.itemId!,
    itemNo: row.itemNo,
    planMonth: row.planMonth,
    planQty: row.planQty,
    ownerId: row.ownerId,
    useDeptId: row.useDeptId,
    useDeptName: row.useDeptName,
    carryoverEnabled: row.carryoverEnabled ?? '1',
    remark: row.remark
  };
  itemEditDialog.visible = true;
};

/** 提交编辑 */
const submitItemEdit = () => {
  itemEditFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    itemEditSubmitting.value = true;
    try {
      await updatePlanItem({
        itemId: itemEditForm.value.itemId,
        planQty: itemEditForm.value.planQty,
        ownerId: itemEditForm.value.ownerId,
        useDeptId: itemEditForm.value.useDeptId,
        useDeptName: itemEditForm.value.useDeptName,
        carryoverEnabled: itemEditForm.value.carryoverEnabled,
        remark: itemEditForm.value.remark
      });
      modal.msgSuccess('修改成功');
      itemEditDialog.visible = false;
      await getItemList();
    } finally {
      itemEditSubmitting.value = false;
    }
  });
};

// ------------------------------------------------------------------ 任务动作

const itemActionDialog = reactive<{
  visible: boolean;
  title: string;
  tip: string;
  action: PlanItemAction;
  row: Partial<HrPlanItemVO>;
  submitting: boolean;
}>({
  visible: false,
  title: '',
  tip: '',
  action: 'pause',
  row: {},
  submitting: false
});
const itemActionFormRef = ref<ElFormInstance>();
const itemActionForm = ref<{ reason: string }>({ reason: '' });
const itemActionRules = {
  reason: [{ required: true, message: '操作原因不能为空', trigger: 'blur' }]
};

/** 打开任务动作弹窗 */
const openItemAction = (row: HrPlanItemVO, action: PlanItemAction) => {
  itemActionDialog.row = row;
  itemActionDialog.action = action;
  itemActionDialog.title = `任务${ITEM_ACTION_META[action].label}`;
  itemActionDialog.tip = ITEM_ACTION_META[action].tip;
  itemActionForm.value.reason = '';
  itemActionDialog.visible = true;
};

/** 提交任务动作 */
const submitItemAction = () => {
  itemActionFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    itemActionDialog.submitting = true;
    try {
      await actionPlanItem(itemActionDialog.row.itemId!, itemActionDialog.action, {
        reason: itemActionForm.value.reason
      });
      modal.msgSuccess(`${ITEM_ACTION_META[itemActionDialog.action].label}成功`);
      itemActionDialog.visible = false;
      await getItemList();
    } finally {
      itemActionDialog.submitting = false;
    }
  });
};

/** 触发单任务状态重算 */
const handleRefreshStatus = async (row: HrPlanItemVO) => {
  try {
    await modal.confirm(`是否触发任务「${row.itemNo || row.itemId}」的状态重算？`);
  } catch {
    return;
  }
  await refreshPlanItemStatus(row.itemId!);
  modal.msgSuccess('已触发状态重算');
  await getItemList();
};

// ------------------------------------------------------------------ 计划任务状态变更日志

const statusLogDialog = reactive<{ visible: boolean; loading: boolean; row: Partial<HrPlanItemVO> }>({
  visible: false,
  loading: false,
  row: {}
});
const statusLogList = ref<HrPlanItemStatusLogVO[]>([]);

/** 触发事件中文（编码与后端 IPlanItemStatusService.TRIGGER_* 逐字一致，后端无对应字典） */
const triggerEventText = (code?: string) => (code ? PLAN_ITEM_TRIGGER_EVENT_TEXT[code] || code : '-');

/** 查询计划任务状态变更日志（追加型，只读；权限沿用 recruit:plan:query） */
const openStatusLogs = async (row: HrPlanItemVO) => {
  statusLogDialog.row = row;
  statusLogDialog.visible = true;
  statusLogDialog.loading = true;
  statusLogList.value = [];
  try {
    const res = await listPlanItemStatusLogs(row.itemId!);
    const payload = res.data as unknown;
    statusLogList.value = Array.isArray(payload) ? (payload as HrPlanItemStatusLogVO[]) : [];
  } catch {
    // 拦截器已提示错误，保持空列表
    statusLogList.value = [];
  } finally {
    statusLogDialog.loading = false;
  }
};

// ------------------------------------------------------------------ 结转链

const chainDialog = reactive({ visible: false, loading: false });
const chainList = ref<HrPlanItemVO[]>([]);

/** 查询跨月结转链 */
const openRolloverChain = async (row: HrPlanItemVO) => {
  chainDialog.visible = true;
  chainDialog.loading = true;
  chainList.value = [];
  try {
    const res = await listPlanItemRolloverChain(row.itemId!);
    const payload = res.data as unknown;
    chainList.value = Array.isArray(payload) ? (payload as HrPlanItemVO[]) : [];
  } catch {
    chainList.value = [];
  } finally {
    chainDialog.loading = false;
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

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}

/* 抽屉内层卡片：去掉重复留白 */
.drawer-shell {
  padding: 0 12px 12px;
}

.inner-card {
  margin-bottom: 0;
}

.partial-tag {
  margin-top: 4px;
}

/* 状态变更日志：原状态 → 新状态 */
.log-arrow {
  margin: 0 6px;
  color: var(--app-text-muted);
}

.empty-text {
  font-size: 12px;
  color: var(--app-text-muted);
}
</style>
