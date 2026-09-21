<template>
  <div class="p-2 app-container hrtalent-duplicate-page">
    <PageHeading
      title="重复人才治理"
      subtitle="疑似重复案件分级展示（强/中/弱）、差异对比预览、确认（置为已确认待合并）、忽略与事务合并（人工选择保留主档与冲突字段）"
      module="hrtalent"
    />

    <div v-show="activeTab === 'case'" class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>疑似重复检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="处理状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 160px">
              <el-option v-for="dict in talent_duplicate_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="匹配级别" prop="matchLevel">
            <el-select v-model="queryParams.matchLevel" placeholder="请选择" clearable style="width: 140px">
              <el-option label="强匹配" value="strong" />
              <el-option label="中匹配" value="medium" />
              <el-option label="弱匹配" value="weak" />
            </el-select>
          </el-form-item>
          <el-form-item label="姓名" prop="name">
            <el-input v-model="queryParams.name" placeholder="来源或目标命中" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="人才编号" prop="talentNo">
            <el-input v-model="queryParams.talentNo" placeholder="来源或目标命中" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="创建日期">
            <el-date-picker
              v-model="createDateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 260px"
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
            <span class="panel-kicker">Duplicate Governance</span>
            <h3>{{ activeTab === 'case' ? '疑似重复人才案件' : '人才合并日志（合并快照）' }}</h3>
            <p v-if="activeTab === 'case'">
              共 {{ total }} 条记录。三级匹配（§8.18）：强 = 标准化手机号/邮箱哈希相同（阻止静默新增）；
              中 = 姓名 + 公司/学校/简历哈希；弱 = 姓名 + 岗位方向（仅提示）。
              <strong>合并只允许集团人才管理员执行</strong>，且会在单个事务内转移全部关系并写入合并快照。
            </p>
            <p v-else>
              合并日志是**不可撤销操作的完整快照**（§8.18 第 6 点）：记录冲突字段决策与关系迁移统计
              （迁移前 / 实际迁移 / 迁移后剩余 / 是否全部迁移），用于事后对账「跳过了多少冲突关系」。
              后端要求<strong>至少指定保留主档或被合并主档</strong>后才可查询。
            </p>
          </div>
          <div class="toolbar-actions">
            <right-toolbar
              v-show="activeTab === 'case'"
              v-model:show-search="showSearch"
              :search="false"
              @query-table="getList"
            ></right-toolbar>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="疑似重复案件" name="case">
          <el-table v-loading="loading" border class="data-table" :data="caseList">
        <el-table-column label="案件ID" align="center" prop="caseId" width="90" />
        <el-table-column label="匹配级别" align="center" width="100">
          <template #default="scope">
            <el-tag :type="matchLevelTagType(scope.row.matchLevel)" size="small">
              {{ scope.row.matchLevelLabel || matchLevelText(scope.row.matchLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="匹配得分" align="center" prop="matchScore" width="90" />
        <el-table-column label="来源人才" align="center" width="170" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.sourceTalentName || '-' }}
            <span class="sub-text">（{{ scope.row.sourceTalentNo || '-' }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="来源公司/岗位" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.sourceCurrentCompany || '-' }} / {{ scope.row.sourceExpectedPosition || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="目标人才" align="center" width="170" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.targetTalentName || '-' }}
            <span class="sub-text">（{{ scope.row.targetTalentNo || '-' }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="目标公司/岗位" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.targetCurrentCompany || '-' }} / {{ scope.row.targetExpectedPosition || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="匹配原因" prop="matchReason" min-width="160" show-overflow-tooltip />
        <el-table-column label="处理状态" align="center" width="120">
          <template #default="scope">
            <dict-tag v-if="scope.row.status" :options="talent_duplicate_status" :value="scope.row.status" />
            <span v-else>{{ scope.row.statusLabel || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="处理人" align="center" width="110">
          <template #default="scope">{{ scope.row.handledByName || scope.row.handledBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="处理时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.handledTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center" class-name="small-padding fixed-width" fixed="right">
          <template #default="scope">
            <el-tooltip content="差异对比预览" placement="top">
              <el-button
                v-hasPermi="['talent:duplicate:list']"
                link
                type="primary"
                icon="View"
                @click="openPreview(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="确认（同一人/非同一人）" placement="top">
              <el-button
                v-hasPermi="['talent:duplicate:confirm']"
                link
                type="primary"
                icon="Select"
                :disabled="scope.row.status !== 'pending'"
                @click="openConfirm(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="忽略" placement="top">
              <el-button
                v-hasPermi="['talent:duplicate:ignore']"
                link
                type="info"
                icon="CloseBold"
                :disabled="scope.row.status !== 'pending'"
                @click="openIgnore(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="合并（仅集团人才管理员）" placement="top">
              <el-button
                v-hasPermi="['talent:duplicate:merge']"
                link
                type="danger"
                icon="Connection"
                :disabled="scope.row.status === 'merged' || scope.row.status === 'not_same' || scope.row.status === 'ignored'"
                @click="openMerge(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="查看该案件的合并日志（快照）" placement="top">
              <el-button
                v-hasPermi="['talent:duplicate:merge']"
                link
                type="info"
                icon="Tickets"
                :disabled="scope.row.status !== 'merged'"
                @click="openMergeLogOfCase(scope.row)"
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
        </el-tab-pane>

        <el-tab-pane label="合并日志" name="mergeLog">
          <el-form :inline="true" class="query-form">
            <el-form-item label="保留主档ID">
              <el-input
                v-model="mergeLogQuery.keepTalentId"
                placeholder="与下方至少填一项"
                clearable
                @keyup.enter="queryMergeLog"
              />
            </el-form-item>
            <el-form-item label="被合并主档ID">
              <el-input
                v-model="mergeLogQuery.mergedTalentId"
                placeholder="与上方至少填一项"
                clearable
                @keyup.enter="queryMergeLog"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="queryMergeLog">查询</el-button>
              <el-button icon="Refresh" @click="resetMergeLogQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="mergeLogLoading" border size="small" :data="mergeLogList">
            <el-table-column type="expand">
              <template #default="scope">
                <div class="merge-snapshot">
                  <div class="snapshot-summary">
                    <el-tag :type="scope.row.complete ? 'success' : 'warning'" size="small">
                      {{ scope.row.complete ? '关系全部迁移（无残留）' : '存在未迁移的残留关系' }}
                    </el-tag>
                    <span class="snapshot-summary-text">
                      迁移后仍留在被合并主档的关系总数（即跳过/未迁移的冲突关系数）：
                      <strong>{{ scope.row.residualTotal ?? '-' }}</strong>
                    </span>
                  </div>

                  <el-divider content-position="left">关系迁移统计（迁移前 / 实际迁移 / 迁移后剩余）</el-divider>
                  <el-table v-if="scope.row.relationRows.length" border size="small" :data="scope.row.relationRows">
                    <el-table-column label="关系" prop="label" width="130" />
                    <el-table-column label="关系表" prop="table" min-width="200" show-overflow-tooltip />
                    <el-table-column label="迁移前数量" align="center" prop="before" width="110" />
                    <el-table-column label="实际迁移" align="center" prop="moved" width="100" />
                    <el-table-column label="迁移后剩余" align="center" prop="after" width="110" />
                    <el-table-column label="迁移方式" align="center" prop="migrateMode" width="130" />
                  </el-table>
                  <span v-else class="empty-text">关系迁移统计快照为空</span>

                  <el-divider content-position="left">冲突字段取值决策</el-divider>
                  <el-table v-if="scope.row.fieldDecisions.length" border size="small" :data="scope.row.fieldDecisions">
                    <el-table-column label="字段" prop="label" width="140" />
                    <el-table-column label="取值来源" align="center" width="130">
                      <template #default="inner">
                        {{ inner.row.from === 'merged' ? '被合并主档' : '保留主档' }}
                      </template>
                    </el-table-column>
                    <el-table-column label="采用值" min-width="150" show-overflow-tooltip>
                      <template #default="inner">{{ inner.row.value ?? '（空）' }}</template>
                    </el-table-column>
                    <el-table-column label="落选值" min-width="150" show-overflow-tooltip>
                      <template #default="inner">{{ inner.row.rejectedValue ?? '（空）' }}</template>
                    </el-table-column>
                    <el-table-column label="是否冲突字段" align="center" width="120">
                      <template #default="inner">{{ inner.row.conflict ? '是' : '否（补全空值）' }}</template>
                    </el-table-column>
                  </el-table>
                  <span v-else class="empty-text">无字段决策记录（合并时所有冲突字段均保留保留主档现值）</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="合并日志ID" align="center" prop="mergeId" width="110" />
            <el-table-column label="保留主档ID" align="center" prop="keepTalentId" width="120" />
            <el-table-column label="被合并主档ID" align="center" prop="mergedTalentId" width="130" />
            <el-table-column label="合并原因" prop="mergeReason" min-width="200" show-overflow-tooltip />
            <el-table-column label="操作人" align="center" width="120">
              <template #default="scope">{{ scope.row.operatorName || scope.row.operatorId || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作时间" align="center" width="170">
              <template #default="scope">{{ parseTime(scope.row.operateTime) || '-' }}</template>
            </el-table-column>
            <el-table-column label="关系迁移结果" align="center" width="200">
              <template #default="scope">
                <el-tag :type="scope.row.complete ? 'success' : 'warning'" size="small">
                  剩余 {{ scope.row.residualTotal ?? '-' }} 条{{ scope.row.complete ? '（已全部迁移）' : '（有跳过）' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <pagination
            v-show="mergeLogTotal > 0"
            v-model:page="mergeLogQuery.pageNum"
            v-model:limit="mergeLogQuery.pageSize"
            :total="mergeLogTotal"
            @pagination="loadMergeLog"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 差异对比预览 -->
    <el-dialog v-model="previewDialog.visible" title="合并差异对比预览" width="1080px" append-to-body>
      <el-alert
        v-for="(warning, index) in previewDialog.data.warnings || []"
        :key="index"
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="warning"
      />
      <el-alert
        v-if="previewDialog.data.mergeAllowed === false"
        class="dialog-alert"
        type="error"
        :closable="false"
        show-icon
        title="当前登录人不具备合并权限：人才合并只允许集团人才管理员执行（设计 §8.18）。"
      />
      <el-descriptions :column="2" border size="small" class="detail-panel">
        <el-descriptions-item label="服务端建议保留主档（来源）">
          {{ previewDialog.data.keepTalent?.name || '-' }}（{{ previewDialog.data.keepTalent?.talentNo || '-' }}）
          版本号 {{ previewDialog.data.keepTalent?.version ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="服务端建议被合并主档（目标）">
          {{ previewDialog.data.mergedTalent?.name || '-' }}（{{ previewDialog.data.mergedTalent?.talentNo || '-' }}）
          版本号 {{ previewDialog.data.mergedTalent?.version ?? '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">冲突字段差异（可人工选择保留哪一侧取值）</el-divider>
      <el-table :data="previewDialog.data.fieldDiffs || []" border size="small">
        <el-table-column label="字段" prop="label" width="140" />
        <el-table-column label="保留主档（来源）取值" min-width="200" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.keepValue || '（空）' }}</template>
        </el-table-column>
        <el-table-column label="被合并主档（目标）取值" min-width="200" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.mergedValue || '（空）' }}</template>
        </el-table-column>
        <el-table-column label="可人工选择" align="center" width="110">
          <template #default="scope">{{ scope.row.selectable ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="服务端默认取值" align="center" width="130">
          <template #default="scope">{{ scope.row.defaultFrom === 'merged' ? '被合并主档' : '保留主档' }}</template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">关系迁移统计</el-divider>
      <el-table :data="previewDialog.data.relations || []" border size="small">
        <el-table-column label="关系" prop="label" width="180" show-overflow-tooltip />
        <el-table-column label="所属表" prop="table" min-width="200" show-overflow-tooltip />
        <el-table-column label="被合并侧数量" align="center" prop="mergedCount" width="120" />
        <el-table-column label="保留侧数量" align="center" prop="keepCount" width="110" />
        <el-table-column label="迁移方式" prop="migrateMode" min-width="180" show-overflow-tooltip />
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :disabled="!previewDialog.caseRow.caseId" @click="openMergeFromPreview">
            前往合并
          </el-button>
          <el-button @click="previewDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 确认：同一人 / 非同一人 -->
    <el-dialog v-model="confirmDialog.visible" title="确认疑似重复" width="620px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="确认「是同一人」会把案件置为「已确认待合并」，不会改动任何人才资料；确认「不是同一人」会直接置为非同一人并终结案件。确认依据必填，用于审计追溯。"
      />
      <el-form ref="confirmFormRef" :model="confirmForm" :rules="confirmRules" label-width="110px">
        <el-form-item label="案件ID">
          <el-input :model-value="confirmDialog.row.caseId" disabled />
        </el-form-item>
        <el-form-item label="确认结论" prop="samePerson">
          <el-radio-group v-model="confirmForm.samePerson">
            <el-radio :value="true">是同一人（置为已确认待合并）</el-radio>
            <el-radio :value="false">不是同一人（终结案件）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="确认依据" prop="reason">
          <el-input v-model="confirmForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="下次复核日期" prop="reviewDate">
          <el-date-picker
            v-model="confirmForm.reviewDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="可为空"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="confirmDialog.loading" @click="submitConfirm">确 定</el-button>
          <el-button @click="confirmDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 忽略 -->
    <el-dialog v-model="ignoreDialog.visible" title="忽略疑似重复" width="600px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="忽略不会改动任何人才资料。弱匹配仅提示，忽略需要留痕（原因必填）。"
      />
      <el-form ref="ignoreFormRef" :model="ignoreForm" :rules="ignoreRules" label-width="110px">
        <el-form-item label="忽略结论" prop="notSamePerson">
          <el-switch v-model="ignoreForm.notSamePerson" active-text="判定为非同一人" inactive-text="仅本次忽略" />
        </el-form-item>
        <el-form-item label="忽略原因" prop="reason">
          <el-input v-model="ignoreForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="ignoreDialog.loading" @click="submitIgnore">确 定</el-button>
          <el-button @click="ignoreDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 合并：选择保留主档、冲突字段取值与合并原因 -->
    <el-dialog v-model="mergeDialog.visible" title="人才合并（高风险，事务执行）" width="1020px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="error"
        :closable="false"
        show-icon
        title="合并会在单个数据库事务内转移应聘记录、简历、经历、附件、人才池成员、标签、跟进记录与授权范围，并写入合并快照与审计；被合并主档标记为「已合并」，不物理删除任何资料。合并后不可普通撤销。"
      />
      <el-alert
        v-if="mergeDialog.preview?.mergeAllowed === false"
        class="dialog-alert"
        type="error"
        :closable="false"
        show-icon
        title="当前登录人不是集团人才管理员，后端会拒绝本次合并（仅集团人才管理员可执行，设计 §8.18）。"
      />
      <el-form ref="mergeFormRef" :model="mergeForm" :rules="mergeRules" label-width="130px">
        <el-form-item label="保留主档" prop="keepSide">
          <el-radio-group v-model="mergeForm.keepSide">
            <el-radio value="source">
              来源：{{ mergeDialog.preview?.keepTalent?.name }}（{{ mergeDialog.preview?.keepTalent?.talentNo }}）
            </el-radio>
            <el-radio value="target">
              目标：{{ mergeDialog.preview?.mergedTalent?.name }}（{{ mergeDialog.preview?.mergedTalent?.talentNo }}）
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="被合并主档">
          <el-input :model-value="mergedTalentText" disabled />
        </el-form-item>

        <el-divider content-position="left">冲突字段取值（人工选择）</el-divider>
        <el-table :data="mergeDialog.selectableDiffs" border size="small">
          <el-table-column label="字段" prop="label" width="140" />
          <el-table-column label="来源人才取值" min-width="200" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.keepValue || '（空）' }}</template>
          </el-table-column>
          <el-table-column label="目标人才取值" min-width="200" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.mergedValue || '（空）' }}</template>
          </el-table-column>
          <el-table-column label="采用" align="center" width="220">
            <template #default="scope">
              <el-radio-group v-model="mergeDecisions[scope.row.field!]">
                <el-radio value="source">来源</el-radio>
                <el-radio value="target">目标</el-radio>
              </el-radio-group>
            </template>
          </el-table-column>
        </el-table>
        <p class="form-tip">未选择或不可选择的冲突字段一律保留「保留主档」的现值（保守策略：不因未选择而丢数据）。</p>

        <el-divider content-position="left">关系迁移统计（迁移前快照）</el-divider>
        <el-table :data="mergeDialog.preview?.relations || []" border size="small">
          <el-table-column label="关系" prop="label" width="180" show-overflow-tooltip />
          <el-table-column label="被合并侧数量" align="center" prop="mergedCount" width="120" />
          <el-table-column label="保留侧数量" align="center" prop="keepCount" width="110" />
          <el-table-column label="迁移方式" prop="migrateMode" min-width="180" show-overflow-tooltip />
        </el-table>

        <el-form-item label="合并原因" prop="mergeReason" class="merge-reason">
          <el-input v-model="mergeForm.mergeReason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button
            type="danger"
            :loading="mergeDialog.loading"
            :disabled="mergeDialog.preview?.mergeAllowed === false"
            @click="submitMerge"
          >
            确认合并
          </el-button>
          <el-button @click="mergeDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  confirmDuplicate,
  ignoreDuplicate,
  listDuplicate,
  listMergeLogs,
  mergeDuplicate,
  previewMerge
} from '@/api/hrtalent/duplicate';
import type {
  HrTalentDuplicateCaseVO,
  HrTalentDuplicateQuery,
  HrTalentMergeFieldDecision,
  HrTalentMergeLogVO,
  HrTalentMergePreviewVO
} from '@/api/hrtalent/duplicate/types';
import { useLoading } from '@/hooks/async/useLoading';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'HrTalentDuplicate' });

const { talent_duplicate_status } = toRefs<any>(useDict('talent_duplicate_status'));

/** 页签：疑似重复案件 / 合并日志 */
const activeTab = ref('case');

const caseList = ref<HrTalentDuplicateCaseVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const confirmFormRef = ref<ElFormInstance>();
const ignoreFormRef = ref<ElFormInstance>();
const mergeFormRef = ref<ElFormInstance>();
const createDateRange = ref<[string, string] | null>(null);

const data = reactive<{ queryParams: HrTalentDuplicateQuery }>({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    status: undefined,
    matchLevel: undefined,
    name: undefined,
    talentNo: undefined,
    createDateBegin: undefined,
    createDateEnd: undefined
  }
});
const { queryParams } = toRefs(data);
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  pageSizeKey: 'pageSize',
  initialPageSize: 10,
  resetExtras: () => {
    createDateRange.value = null;
  },
  afterReset: () => handleQuery()
});

/** 匹配级别中文兜底（后端另有 matchLevelLabel） */
const matchLevelText = (code?: string) => {
  if (code === 'strong') return '强匹配';
  if (code === 'medium') return '中匹配';
  if (code === 'weak') return '弱匹配';
  return '-';
};
/** 匹配级别标签颜色 */
const matchLevelTagType = (code?: string) => {
  if (code === 'strong') return 'danger';
  if (code === 'medium') return 'warning';
  return 'info';
};

const getList = async () => {
  await withLoading(async () => {
    queryParams.value.createDateBegin = createDateRange.value?.[0] || undefined;
    queryParams.value.createDateEnd = createDateRange.value?.[1] || undefined;
    const res = await listDuplicate(queryParams.value);
    caseList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/* ------------------------------ 差异对比预览 ------------------------------ */

const previewDialog = reactive<{
  visible: boolean;
  loading: boolean;
  caseRow: Partial<HrTalentDuplicateCaseVO>;
  data: HrTalentMergePreviewVO;
}>({ visible: false, loading: false, caseRow: {}, data: {} });

const openPreview = async (row: HrTalentDuplicateCaseVO) => {
  previewDialog.caseRow = row;
  previewDialog.data = {};
  previewDialog.visible = true;
  try {
    const res = await previewMerge(row.caseId!);
    previewDialog.data = res.data || {};
  } catch {
    // 拦截器已提示错误（如无权限），保持空数据
    previewDialog.data = {};
  }
};

const openMergeFromPreview = () => {
  previewDialog.visible = false;
  openMerge(previewDialog.caseRow as HrTalentDuplicateCaseVO);
};

/* ------------------------------ 确认 ------------------------------ */

const confirmDialog = reactive<{
  visible: boolean;
  loading: boolean;
  row: Partial<HrTalentDuplicateCaseVO>;
}>({ visible: false, loading: false, row: {} });
const confirmForm = ref<{ samePerson: boolean; reason: string; reviewDate?: string }>({
  samePerson: true,
  reason: '',
  reviewDate: undefined
});
const confirmRules: ElFormRules = {
  reason: [{ required: true, message: '确认依据不能为空', trigger: 'blur' }]
};

const openConfirm = (row: HrTalentDuplicateCaseVO) => {
  confirmDialog.row = row;
  confirmForm.value = { samePerson: true, reason: '', reviewDate: undefined };
  confirmDialog.visible = true;
};

const submitConfirm = () => {
  confirmFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    confirmDialog.loading = true;
    try {
      await confirmDuplicate(confirmDialog.row.caseId!, {
        samePerson: confirmForm.value.samePerson,
        reason: confirmForm.value.reason,
        reviewDate: confirmForm.value.reviewDate
      });
      modal.msgSuccess(confirmForm.value.samePerson ? '已确认是同一人，案件置为「已确认待合并」' : '已确认为非同一人');
      confirmDialog.visible = false;
      await getList();
    } finally {
      confirmDialog.loading = false;
    }
  });
};

/* ------------------------------ 忽略 ------------------------------ */

const ignoreDialog = reactive<{
  visible: boolean;
  loading: boolean;
  row: Partial<HrTalentDuplicateCaseVO>;
}>({ visible: false, loading: false, row: {} });
const ignoreForm = ref<{ notSamePerson: boolean; reason: string }>({ notSamePerson: false, reason: '' });
const ignoreRules: ElFormRules = {
  reason: [{ required: true, message: '忽略原因不能为空', trigger: 'blur' }]
};

const openIgnore = (row: HrTalentDuplicateCaseVO) => {
  ignoreDialog.row = row;
  ignoreForm.value = { notSamePerson: false, reason: '' };
  ignoreDialog.visible = true;
};

const submitIgnore = () => {
  ignoreFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    ignoreDialog.loading = true;
    try {
      await ignoreDuplicate(ignoreDialog.row.caseId!, {
        reason: ignoreForm.value.reason,
        notSamePerson: ignoreForm.value.notSamePerson
      });
      modal.msgSuccess(ignoreForm.value.notSamePerson ? '已判定为非同一人' : '已忽略该疑似重复案件');
      ignoreDialog.visible = false;
      await getList();
    } finally {
      ignoreDialog.loading = false;
    }
  });
};

/* ------------------------------ 合并 ------------------------------ */

const mergeDialog = reactive<{
  visible: boolean;
  loading: boolean;
  caseRow: Partial<HrTalentDuplicateCaseVO>;
  preview: HrTalentMergePreviewVO | null;
  selectableDiffs: NonNullable<HrTalentMergePreviewVO['fieldDiffs']>;
}>({ visible: false, loading: false, caseRow: {}, preview: null, selectableDiffs: [] });

const mergeForm = ref<{ keepSide: 'source' | 'target'; mergeReason: string }>({
  keepSide: 'source',
  mergeReason: ''
});
const mergeRules: ElFormRules = {
  mergeReason: [{ required: true, message: '合并原因不能为空', trigger: 'blur' }]
};
/** 冲突字段选择：field -> 采用哪一侧的值 */
const mergeDecisions = reactive<Record<string, 'source' | 'target'>>({});

/** 被合并主档提示文本（随保留侧选择动态变化） */
const mergedTalentText = computed(() => {
  const preview = mergeDialog.preview;
  if (!preview) {
    return '';
  }
  const mergedSide = mergeForm.value.keepSide === 'source' ? preview.mergedTalent : preview.keepTalent;
  return `${mergedSide?.name || '-'}（${mergedSide?.talentNo || '-'}）`;
});

const openMerge = async (row: HrTalentDuplicateCaseVO) => {
  mergeDialog.caseRow = row;
  mergeDialog.preview = null;
  mergeDialog.selectableDiffs = [];
  mergeForm.value = { keepSide: 'source', mergeReason: '' };
  Object.keys(mergeDecisions).forEach(key => delete mergeDecisions[key]);
  mergeDialog.visible = true;
  try {
    const res = await previewMerge(row.caseId!);
    mergeDialog.preview = res.data || null;
    const diffs = (res.data?.fieldDiffs || []).filter(item => item.selectable && item.field);
    mergeDialog.selectableDiffs = diffs;
    // 默认按服务端建议取值：defaultFrom = merged 表示采用「被合并主档（目标）」的值
    diffs.forEach(item => {
      if (item.field) {
        mergeDecisions[item.field] = item.defaultFrom === 'merged' ? 'target' : 'source';
      }
    });
  } catch {
    mergeDialog.preview = null;
  }
};

const submitMerge = () => {
  const preview = mergeDialog.preview;
  if (!preview) {
    modal.msgWarning('缺少合并预览数据，请先执行差异对比预览');
    return;
  }
  mergeFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    const source = preview.keepTalent;
    const target = preview.mergedTalent;
    const keepIsSource = mergeForm.value.keepSide === 'source';
    const keep = keepIsSource ? source : target;
    const merged = keepIsSource ? target : source;
    if (!keep?.talentId || !merged?.talentId) {
      modal.msgWarning('主档ID缺失，请刷新后重试');
      return;
    }
    // 字段决策以「保留主档 / 被合并主档」为语义，因此需要按保留侧方向做映射
    const fieldDecisions: HrTalentMergeFieldDecision[] = mergeDialog.selectableDiffs
      .filter(item => item.field && mergeDecisions[item.field])
      .map(item => {
        const choice = mergeDecisions[item.field!];
        const chosenIsSource = choice === 'source';
        const sameSide = chosenIsSource === keepIsSource;
        return { field: item.field!, from: sameSide ? 'keep' : 'merged' };
      });
    try {
      await modal.confirm('合并后不可普通撤销，是否确认执行本次合并？');
    } catch {
      return;
    }
    mergeDialog.loading = true;
    try {
      const res = await mergeDuplicate(mergeDialog.caseRow.caseId!, {
        keepTalentId: keep.talentId,
        keepVersion: keep.version!,
        mergedTalentId: merged.talentId,
        mergedVersion: merged.version!,
        mergeReason: mergeForm.value.mergeReason,
        fieldDecisions
      });
      modal.msgSuccess(`合并完成（合并日志ID ${res.data}），关系已迁移并写入合并快照`);
      mergeDialog.visible = false;
      await getList();
      // 合并后直接跳到合并日志页签，便于立即核对迁移快照
      if (mergeDialog.caseRow.sourceTalentId && mergeDialog.caseRow.targetTalentId) {
        await openMergeLogOfCase(mergeDialog.caseRow as HrTalentDuplicateCaseVO);
      }
    } finally {
      mergeDialog.loading = false;
    }
  });
};

/* ------------------------------ 合并日志（合并快照） ------------------------------ */

/**
 * 关系表 → 中文名（与后端 TalentDuplicateServiceImpl#relationLabels 逐字一致，避免自造标签）
 */
const RELATION_LABELS: Record<string, string> = {
  hr_recruit_application: '应聘记录',
  hr_talent_resume: '简历版本',
  hr_talent_education: '教育经历',
  hr_talent_work: '工作经历',
  hr_talent_project: '项目经历',
  hr_recruit_attachment: '应聘附件',
  hr_recruit_attachment_talent: '人才附件',
  hr_talent_pool_member: '人才池成员',
  hr_talent_profile_tag: '标签关系',
  hr_talent_follow_up: '跟进记录',
  hr_talent_group_member: '分组成员',
  hr_talent_scope_grant: '共享授权',
  hr_talent_profile_change: '字段变更历史'
};

/** 关系迁移方式（与后端 TalentDuplicateServiceImpl#migrateMode 逐字一致） */
const migrateModeText = (table: string) => {
  if (
    table === 'hr_talent_pool_member' ||
    table === 'hr_talent_profile_tag' ||
    table === 'hr_talent_group_member'
  ) {
    return 'skip_conflict（跳过冲突）';
  }
  if (table === 'hr_recruit_attachment') {
    return 'follow（跟随应聘记录）';
  }
  return 'move（直接改归属）';
};

/** 合并日志行（在原 VO 上补充解析后的快照结构，便于展开行渲染） */
interface MergeLogRow extends HrTalentMergeLogVO {
  residualTotal?: number;
  complete?: boolean;
  relationRows: {
    table: string;
    label: string;
    before?: number;
    moved?: number;
    after?: number;
    migrateMode: string;
  }[];
  fieldDecisions: {
    field: string;
    label?: string;
    from?: string;
    value?: unknown;
    rejectedValue?: unknown;
    conflict?: boolean;
  }[];
}

const mergeLogList = ref<MergeLogRow[]>([]);
const mergeLogLoading = ref(false);
const mergeLogTotal = ref(0);
const mergeLogQuery = reactive<{
  pageNum: number;
  pageSize: number;
  keepTalentId?: string | number;
  mergedTalentId?: string | number;
}>({ pageNum: 1, pageSize: 10, keepTalentId: undefined, mergedTalentId: undefined });

/** 安全解析后端下发的 JSON 快照字符串 */
const parseSnapshot = <T>(json?: string | null): T | null => {
  if (!json) {
    return null;
  }
  try {
    return JSON.parse(json) as T;
  } catch {
    return null;
  }
};

/** 把合并日志 VO 的快照 JSON 展开为可渲染结构 */
const decorateMergeLog = (row: HrTalentMergeLogVO): MergeLogRow => {
  const relation = parseSnapshot<{
    before?: Record<string, number>;
    moved?: Record<string, number>;
    after?: Record<string, number>;
    residualTotal?: number;
    complete?: boolean;
  }>(row.relationCountJson);
  const before = relation?.before || {};
  const moved = relation?.moved || {};
  const after = relation?.after || {};
  const tables = Object.keys(RELATION_LABELS).filter(
    table => before[table] !== undefined || moved[table] !== undefined || after[table] !== undefined
  );
  const decisionSnapshot =
    parseSnapshot<Record<string, { label?: string; from?: string; value?: unknown; rejectedValue?: unknown; conflict?: boolean }>>(
      row.fieldDecisionJson
    ) || {};
  return {
    ...row,
    residualTotal: relation?.residualTotal,
    complete: relation?.complete,
    relationRows: tables.map(table => ({
      table,
      label: RELATION_LABELS[table] || table,
      before: before[table],
      moved: moved[table],
      after: after[table],
      migrateMode: migrateModeText(table)
    })),
    fieldDecisions: Object.entries(decisionSnapshot).map(([field, item]) => ({
      field,
      label: item?.label,
      from: item?.from,
      value: item?.value,
      rejectedValue: item?.rejectedValue,
      conflict: item?.conflict
    }))
  };
};

/** 查询合并日志（后端要求至少指定保留主档或被合并主档） */
const loadMergeLog = async () => {
  if (!mergeLogQuery.keepTalentId && !mergeLogQuery.mergedTalentId) {
    modal.msgWarning('请至少指定保留主档或被合并主档');
    return;
  }
  mergeLogLoading.value = true;
  try {
    const res = await listMergeLogs({
      keepTalentId: mergeLogQuery.keepTalentId,
      mergedTalentId: mergeLogQuery.mergedTalentId,
      pageNum: mergeLogQuery.pageNum,
      pageSize: mergeLogQuery.pageSize
    });
    mergeLogList.value = (res.data?.rows || []).map(decorateMergeLog);
    mergeLogTotal.value = res.data?.total || 0;
  } catch {
    // 拦截器已提示错误，保持空列表
    mergeLogList.value = [];
    mergeLogTotal.value = 0;
  } finally {
    mergeLogLoading.value = false;
  }
};

const queryMergeLog = () => {
  mergeLogQuery.pageNum = 1;
  loadMergeLog();
};

const resetMergeLogQuery = () => {
  mergeLogQuery.keepTalentId = undefined;
  mergeLogQuery.mergedTalentId = undefined;
  mergeLogQuery.pageNum = 1;
  mergeLogList.value = [];
  mergeLogTotal.value = 0;
};

/** 从已合并的案件跳转到合并日志页签并按该案件的主档对查询 */
const openMergeLogOfCase = async (row: HrTalentDuplicateCaseVO) => {
  activeTab.value = 'mergeLog';
  mergeLogQuery.keepTalentId = row.sourceTalentId;
  mergeLogQuery.mergedTalentId = row.targetTalentId;
  mergeLogQuery.pageNum = 1;
  await loadMergeLog();
};

const handleTabChange = (name: string | number) => {
  if (name === 'mergeLog' && (mergeLogQuery.keepTalentId || mergeLogQuery.mergedTalentId)) {
    loadMergeLog();
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

.sub-text {
  font-size: 12px;
  color: var(--app-text-muted);
}

.form-tip {
  margin: 6px 0 12px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.merge-reason {
  margin-top: 12px;
}

/* 合并日志展开行：快照对照 */
.merge-snapshot {
  padding: 4px 8px;
}

.snapshot-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.snapshot-summary-text {
  font-size: 12px;
  color: var(--app-text-muted);
}

.empty-text {
  font-size: 12px;
  color: var(--app-text-muted);
}
</style>
