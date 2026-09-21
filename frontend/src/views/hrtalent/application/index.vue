<template>
  <div class="p-2 app-container hrtalent-application-page">
    <PageHeading
      title="候选人跟进"
      subtitle="候选人检索与查重新增、阶段流转、邀约与报到登记（一人一档：候选人列表 = 有应聘记录的人才视图）"
      module="hrtalent"
    />

    <!-- 候选人检索 -->
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>候选人检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="queryParams.name" placeholder="请输入姓名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="人才编号" prop="talentNo">
            <el-input v-model="queryParams.talentNo" placeholder="请输入人才编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="电话" prop="phone">
            <el-input v-model="queryParams.phone" placeholder="按标准化哈希匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="queryParams.email" placeholder="按标准化哈希匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="当前公司" prop="currentCompany">
            <el-input v-model="queryParams.currentCompany" placeholder="请输入当前公司" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="期望岗位" prop="expectedPosition">
            <el-input v-model="queryParams.expectedPosition" placeholder="请输入期望岗位" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="当前阶段" prop="stage">
            <el-select v-model="queryParams.stage" placeholder="请选择阶段" clearable style="width: 150px">
              <el-option v-for="dict in recruit_candidate_stage" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="应聘结果" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择结果" clearable style="width: 150px">
              <el-option
                v-for="dict in recruit_application_result"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="人才状态" prop="talentStatus">
            <el-select v-model="queryParams.talentStatus" placeholder="请选择状态" clearable style="width: 150px">
              <el-option v-for="dict in talent_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="数据分级" prop="dataLevel">
            <el-select v-model="queryParams.dataLevel" placeholder="请选择分级" clearable style="width: 150px">
              <el-option v-for="dict in recruit_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="岗位ID" prop="jobId">
            <el-input v-model="queryParams.jobId" placeholder="岗位执行项ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="招聘负责人" prop="recruiterId">
            <el-input v-model="queryParams.recruiterId" placeholder="负责人用户ID" clearable @keyup.enter="handleQuery" />
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
            <span class="panel-kicker">Recruit Application</span>
            <h3>候选人 / 应聘记录</h3>
            <p>
              共 {{ total }} 条记录。列表电话与邮箱一律脱敏；电话明文需在弹窗中填写用途后获取，系统会记录审计。
              阶段流转需先满足 SPEC-P3 §3.2 的六条前置校验。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['recruit:candidate:add']" type="primary" icon="Plus" @click="handleAdd">新增候选人</el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="candidateList">
        <el-table-column label="人才编号" align="center" prop="talentNo" width="150" show-overflow-tooltip />
        <el-table-column label="姓名" align="center" prop="name" width="110" show-overflow-tooltip />
        <el-table-column label="性别" align="center" width="80">
          <template #default="scope">{{ scope.row.genderText || genderText(scope.row.gender) }}</template>
        </el-table-column>
        <el-table-column label="电话（脱敏）" align="center" width="140">
          <template #default="scope">{{ scope.row.phoneMasked || '-' }}</template>
        </el-table-column>
        <el-table-column label="邮箱（脱敏）" align="center" width="170">
          <template #default="scope">{{ scope.row.emailMasked || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前公司" align="center" prop="currentCompany" width="160" show-overflow-tooltip />
        <el-table-column label="期望岗位" align="center" prop="expectedPosition" width="140" show-overflow-tooltip />
        <el-table-column label="当前阶段" align="center" width="110">
          <template #default="scope">
            {{ scope.row.latestStageLabel || scope.row.latestStageText || stageText(scope.row.latestStage) }}
          </template>
        </el-table-column>
        <el-table-column label="应聘结果" align="center" width="110">
          <template #default="scope">
            <dict-tag
              v-if="scope.row.latestStatus"
              :options="recruit_application_result"
              :value="scope.row.latestStatus"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="应聘编号" align="center" prop="latestApplicationNo" width="170" show-overflow-tooltip />
        <el-table-column label="招聘负责人" align="center" width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.latestRecruiterName || scope.row.latestRecruiterId || '-' }}</template>
        </el-table-column>
        <el-table-column label="最近投递" align="center" width="160">
          <template #default="scope">{{ parseTime(scope.row.latestApplyTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="计划报到" align="center" prop="latestPlanArrivalDate" width="110" />
        <el-table-column label="实际到岗" align="center" prop="latestArrivalDate" width="110" />
        <el-table-column label="应聘次数" align="center" prop="applicationCount" width="90" />
        <el-table-column label="操作" width="230" align="center" class-name="small-padding fixed-width" fixed="right">
          <template #default="scope">
            <el-tooltip content="详情" placement="top">
              <el-button
                v-hasPermi="['recruit:candidate:query']"
                link
                type="primary"
                icon="Search"
                @click="handleDetail(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="电话明文（需填用途，记录审计）" placement="top">
              <el-button
                v-hasPermi="['recruit:candidate:phone-view']"
                link
                type="warning"
                icon="Phone"
                @click="openPhoneDialog(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="阶段流转" placement="top">
              <el-button
                v-hasPermi="['recruit:candidate:stage']"
                link
                type="primary"
                icon="Right"
                @click="openTransition(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-dropdown trigger="click" @command="cmd => handleMore(scope.row, cmd)">
              <el-button link type="primary" icon="More"></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-hasPermi="['recruit:candidate:query']" command="logs">阶段历史</el-dropdown-item>
                  <el-dropdown-item v-hasPermi="['recruit:candidate:transfer']" command="transfer">
                    转移负责人/岗位
                  </el-dropdown-item>
                  <el-dropdown-item v-hasPermi="['recruit:candidate:stage']" command="offer" divided>
                    邀约登记
                  </el-dropdown-item>
                  <el-dropdown-item v-hasPermi="['recruit:candidate:stage']" command="arrival">报到登记</el-dropdown-item>
                  <el-dropdown-item v-hasPermi="['recruit:candidate:stage']" command="noArrival">未报到登记</el-dropdown-item>
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

    <!-- 新增候选人（查重预检 → 复用 / 确认新建） -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="900px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="入库前必须查重（设计 §7.6.3）：请先执行「查重预检」。命中强/中匹配时必须选择「复用已有主档」或「确认不是同一人」，系统不允许静默创建重复人员。"
      />
      <el-alert
        v-if="form.talentId"
        class="dialog-alert"
        type="success"
        :closable="false"
        show-icon
        :title="`已选择复用已有主档（talentId=${form.talentId}）：本次只会追加应聘记录，人才主档字段以已有档案为准，不会被覆盖。`"
      />
      <el-form ref="candidateFormRef" :model="form" :rules="rules" label-width="120px">
        <el-divider content-position="left">人才主档</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="请输入姓名（必填）" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="曾用名/英文名" prop="formerName">
              <el-input v-model="form.formerName" placeholder="可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="性别" prop="gender">
              <el-select v-model="form.gender" placeholder="请选择" clearable style="width: 100%">
                <el-option label="男" value="male" />
                <el-option label="女" value="female" />
                <el-option label="未知" value="unknown" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="电话" prop="phone">
              <el-input v-model="form.phone" placeholder="明文提交，服务端加密存储" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="明文提交，服务端加密存储" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最高学历" prop="highestEducation">
              <el-select v-model="form.highestEducation" placeholder="请选择" clearable style="width: 100%">
                <el-option label="高中及以下" value="high_school" />
                <el-option label="大专" value="junior_college" />
                <el-option label="本科" value="bachelor" />
                <el-option label="硕士" value="master" />
                <el-option label="博士" value="doctor" />
                <el-option label="其他" value="other" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="当前公司" prop="currentCompany">
              <el-input v-model="form.currentCompany" placeholder="请输入当前公司" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="当前职位" prop="currentPosition">
              <el-input v-model="form.currentPosition" placeholder="请输入当前职位" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="期望岗位" prop="expectedPosition">
              <el-input v-model="form.expectedPosition" placeholder="请输入期望岗位" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="当前城市" prop="currentCity">
              <el-input v-model="form.currentCity" placeholder="请输入当前城市" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="期望城市" prop="expectedCity">
              <el-input v-model="form.expectedCity" placeholder="请输入期望城市" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="工作年限" prop="workYears">
              <el-input-number v-model="form.workYears" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="人才归属负责人" prop="ownerId">
              <el-input v-model="form.ownerId" placeholder="用户ID" clearable @click="openOwnerSelect">
                <template #append>
                  <el-button icon="User" @click="openOwnerSelect" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="归属部门ID" prop="ownerDeptId">
              <el-input v-model="form.ownerDeptId" placeholder="部门ID，可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="归属部门名称" prop="ownerDeptName">
              <el-input v-model="form.ownerDeptName" placeholder="部门名称快照，可为空" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="可见范围" prop="visibilityType">
              <el-select v-model="form.visibilityType" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="dict in talent_visibility_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="数据分级" prop="dataLevel">
              <el-select v-model="form.dataLevel" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="dict in recruit_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="主档来源" prop="sourceType">
              <el-input v-model="form.sourceType" placeholder="manual/channel 等稳定编码" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">应聘信息</el-divider>
        <el-form-item label="同时创建应聘记录" prop="createApplication">
          <el-switch v-model="form.createApplication" />
          <span class="form-tip">关闭时仅创建人才主档，不产生应聘记录。</span>
        </el-form-item>
        <template v-if="form.createApplication">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="应聘岗位ID" prop="jobId">
                <el-input v-model="form.jobId" placeholder="岗位执行项ID（必填）" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="招聘负责人ID" prop="recruiterId">
                <el-input v-model="form.recruiterId" placeholder="用户ID" clearable @click="openRecruiterSelect">
                  <template #append>
                    <el-button icon="User" @click="openRecruiterSelect" />
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="联系日期" prop="contactDate">
                <el-date-picker
                  v-model="form.contactDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="请选择日期"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="期望薪资下限" prop="expectedSalaryMin">
                <el-input-number v-model="form.expectedSalaryMin" :min="0" :precision="2" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="期望薪资上限" prop="expectedSalaryMax">
                <el-input-number v-model="form.expectedSalaryMax" :min="0" :precision="2" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="来源渠道ID" prop="sourceChannelId">
                <el-input v-model="form.sourceChannelId" placeholder="渠道ID，可为空" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="应聘备注" prop="applicationRemark">
            <el-input v-model="form.applicationRemark" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </template>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button :loading="prechecking" @click="runPrecheck">查重预检</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查重预检结果 -->
    <el-dialog v-model="precheckDialog.visible" title="查重预检结果" width="900px" append-to-body>
      <el-alert
        class="dialog-alert"
        :type="precheckDialog.result?.duplicated ? 'warning' : 'success'"
        :closable="false"
        show-icon
        :title="precheckDialog.result?.message || '未发现重复人员，可直接创建。'"
      />
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="匹配分级（设计 §21.15）：强匹配 = 电话/邮箱哈希命中；中匹配 = 姓名 +（公司 / 学校 / 简历哈希）；弱匹配 = 姓名 + 期望岗位。姓名单独命中不构成任何级别。"
      />
      <el-radio-group v-model="precheckDialog.reuseTalentId" class="precheck-radio">
        <div v-for="group in precheckGroups" :key="group.level" class="precheck-group">
          <div class="precheck-group-title">{{ group.title }}（{{ group.items.length }}）</div>
          <el-table v-if="group.items.length" border size="small" :data="group.items">
            <el-table-column label="选择复用" width="90" align="center">
              <template #default="scope">
                <el-radio :value="String(scope.row.talentId)" />
              </template>
            </el-table-column>
            <el-table-column label="人才编号" align="center" prop="talentNo" width="150" show-overflow-tooltip />
            <el-table-column label="姓名" align="center" prop="name" width="100" />
            <el-table-column label="电话（脱敏）" align="center" prop="phoneMasked" width="130" />
            <el-table-column label="邮箱（脱敏）" align="center" prop="emailMasked" width="160" show-overflow-tooltip />
            <el-table-column label="当前公司" align="center" prop="currentCompany" show-overflow-tooltip />
            <el-table-column label="期望岗位" align="center" prop="expectedPosition" show-overflow-tooltip />
            <el-table-column label="命中原因" align="center" prop="reason" width="150" show-overflow-tooltip />
            <el-table-column label="创建日期" align="center" prop="createDate" width="110" />
          </el-table>
          <div v-else class="precheck-empty">无</div>
        </div>
      </el-radio-group>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :disabled="!precheckDialog.reuseTalentId" @click="confirmReuse">复用选中主档</el-button>
          <el-button type="warning" plain @click="confirmDistinct">确认不是同一人，继续新建</el-button>
          <el-button @click="precheckDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 候选人详情（候选人不是独立实体，摘要来自列表行） -->
    <el-dialog v-model="detail.visible" title="候选人详情" width="900px" append-to-body>
      <el-descriptions :column="2" border size="small" class="detail-panel">
        <el-descriptions-item label="人才编号">{{ detail.row.talentNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detail.row.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="性别">
          {{ detail.row.genderText || genderText(detail.row.gender) }}
        </el-descriptions-item>
        <el-descriptions-item label="最高学历">
          {{ detail.row.highestEducationText || detail.row.highestEducation || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="电话（脱敏）">{{ detail.row.phoneMasked || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱（脱敏）">{{ detail.row.emailMasked || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前公司">{{ detail.row.currentCompany || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前职位">{{ detail.row.currentPosition || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望岗位">{{ detail.row.expectedPosition || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望城市">{{ detail.row.expectedCity || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前城市">{{ detail.row.currentCity || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工作年限">{{ detail.row.workYears ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属行业">{{ detail.row.industry || '-' }}</el-descriptions-item>
        <el-descriptions-item label="人才归属负责人">
          {{ detail.row.ownerName || detail.row.ownerId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="归属部门">
          {{ detail.row.ownerDeptName || detail.row.ownerDeptId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="协助人">{{ detail.row.assistantNames || '-' }}</el-descriptions-item>
        <el-descriptions-item label="人才状态">
          <dict-tag v-if="detail.row.talentStatus" :options="talent_status" :value="detail.row.talentStatus" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="可见范围">
          <dict-tag v-if="detail.row.visibilityType" :options="talent_visibility_type" :value="detail.row.visibilityType" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="数据分级">
          <dict-tag v-if="detail.row.dataLevel" :options="recruit_data_level" :value="detail.row.dataLevel" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="主档来源">{{ detail.row.sourceType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="应聘次数">{{ detail.row.applicationCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近应聘编号">{{ detail.row.latestApplicationNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前阶段">
          {{ detail.row.latestStageLabel || detail.row.latestStageText || stageText(detail.row.latestStage) }}
        </el-descriptions-item>
        <el-descriptions-item label="应聘结果">
          <dict-tag v-if="detail.row.latestStatus" :options="recruit_application_result" :value="detail.row.latestStatus" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="招聘负责人">
          {{ detail.row.latestRecruiterName || detail.row.latestRecruiterId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="最近投递">{{ parseTime(detail.row.latestApplyTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="进入阶段时间">
          {{ parseTime(detail.row.latestStageEnterTime) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="计划报到日期">{{ detail.row.latestPlanArrivalDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际到岗日期">{{ detail.row.latestArrivalDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望薪资">
          {{ salaryText(detail.row.latestExpectedSalaryMin, detail.row.latestExpectedSalaryMax) }}
        </el-descriptions-item>
        <el-descriptions-item label="应聘版本号">{{ detail.row.latestVersion ?? '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detail.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 电话明文查看：必须先填用途，查看后即时提示已记录审计 -->
    <el-dialog v-model="phoneDialog.visible" title="查看电话明文" width="520px" append-to-body>
      <template v-if="phoneDialog.step === 'purpose'">
        <el-alert
          class="dialog-alert"
          type="warning"
          :closable="false"
          show-icon
          title="电话明文属敏感数据：必须填写查看用途，服务端会记录操作人、IP、时间与用途的审计，且用途会写库留痕。"
        />
        <el-form ref="phoneFormRef" :model="phoneForm" :rules="phoneRules" label-width="80px">
          <el-form-item label="候选人">
            <el-input :model-value="phoneDialog.row.name" disabled />
          </el-form-item>
          <el-form-item label="用途" prop="purpose">
            <el-input
              v-model="phoneForm.purpose"
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
          title="本次查看已记录敏感操作审计（事件类型 phone_view，包含操作人、用途、IP 与时间）。"
        />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="姓名">{{ phoneDialog.row.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用途">{{ phoneForm.purpose }}</el-descriptions-item>
          <el-descriptions-item label="电话明文">
            <span class="phone-plain">{{ phoneDialog.phone || '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="phoneDialog.step === 'purpose'" type="primary" :loading="phoneDialog.loading" @click="submitPhoneView">
            确认查看
          </el-button>
          <el-button v-else @click="phoneDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 阶段流转：含 6 条前置校验提示 -->
    <el-dialog v-model="transitionDialog.visible" title="应聘阶段流转" width="820px" append-to-body>
      <el-descriptions :column="2" border size="small" class="detail-panel">
        <el-descriptions-item label="候选人">{{ transitionDialog.row.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="应聘编号">{{ transitionDialog.application.applicationNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前阶段">
          {{ stageText(transitionDialog.application.currentStage) }}
        </el-descriptions-item>
        <el-descriptions-item label="当前结果">
          {{ resultText(transitionDialog.application.currentStatus) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-alert class="dialog-alert" type="warning" :closable="false" show-icon>
        <template #title>跳转前置校验（SPEC-P3 §3.2 / 设计 §7.2，由后端逐条强制）</template>
        <ol class="rule-list">
          <li v-for="rule in PREREQUISITE_RULES" :key="rule.key" :class="{ 'is-active': rule.key === activePrerequisite }">
            {{ rule.text }}
          </li>
        </ol>
      </el-alert>

      <el-form ref="transitionFormRef" :model="transitionForm" :rules="transitionRules" label-width="120px">
        <el-form-item label="流转方式" prop="mode">
          <el-radio-group v-model="transitionForm.mode">
            <el-radio value="stage">阶段前进</el-radio>
            <el-radio value="result">转入结果（淘汰/放弃/暂缓/人才保留）</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="transitionForm.mode === 'stage'">
          <el-form-item label="目标阶段" prop="toStage">
            <el-select v-model="transitionForm.toStage" placeholder="请选择目标阶段" clearable style="width: 100%">
              <el-option v-for="item in targetStageOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="transitionForm.toStage === 'pending_arrival'" label="邀约结果" prop="offerResult">
            <el-select
              v-model="transitionForm.offerResult"
              placeholder="进入待报到前必须记录邀约结果"
              filterable
              allow-create
              default-first-option
              style="width: 100%"
            >
              <el-option v-for="item in OFFER_RESULT_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="transitionForm.toStage === 'pending_arrival'" label="计划报到日期" prop="planArrivalDate">
            <el-date-picker
              v-model="transitionForm.planArrivalDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="进入待报到前必须记录计划报到日期"
              style="width: 100%"
            />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="目标结果" prop="result">
            <el-select v-model="transitionForm.result" placeholder="请选择目标结果" clearable style="width: 100%">
              <el-option label="未通过（淘汰）" value="rejected" />
              <el-option label="已撤回（候选人放弃）" value="withdrawn" />
              <el-option label="已暂停（暂缓）" value="paused" />
              <el-option label="转入人才池（人才保留）" value="talent_pool" />
            </el-select>
          </el-form-item>
        </template>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="原因编码" prop="reasonCode">
              <el-input v-model="transitionForm.reasonCode" placeholder="字典编码，不存中文，可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下一步跟进时间" prop="nextFollowTime">
              <el-date-picker
                v-model="transitionForm.nextFollowTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="可为空"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="阶段说明" prop="comment">
          <el-input
            v-model="transitionForm.comment"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="淘汰与候选人放弃必须填写原因（原因编码或阶段说明至少一项）"
          />
        </el-form-item>
        <el-form-item label="管理员例外跳转" prop="override">
          <el-switch v-model="transitionForm.override" />
          <span class="form-tip">开启后允许跳级或回退，必须填写原因并写入敏感操作审计。</span>
        </el-form-item>
        <el-form-item v-if="transitionForm.override" label="例外跳转原因" prop="overrideReason">
          <el-input v-model="transitionForm.overrideReason" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="transitionDialog.submitting" @click="submitTransition">确 定</el-button>
          <el-button @click="transitionDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 阶段历史时间线 -->
    <el-dialog v-model="logDialog.visible" title="阶段历史（只追加，不覆盖）" width="760px" append-to-body>
      <div v-loading="logDialog.loading" class="timeline-wrap">
        <el-timeline v-if="logList.length">
          <el-timeline-item
            v-for="log in logList"
            :key="String(log.logId)"
            :timestamp="parseTime(log.operateTime) || ''"
            placement="top"
            :type="log.result === 'rejected' || log.result === 'withdrawn' ? 'danger' : 'primary'"
          >
            <div class="timeline-title">
              {{ log.fromStageLabel || stageText(log.fromStage) }} →
              {{ log.toStageLabel || stageText(log.toStage) }}
              <el-tag v-if="log.actionType" size="small" type="info" class="timeline-tag">{{ actionText(log.actionType) }}</el-tag>
              <el-tag v-if="log.result" size="small" :type="resultTagType(log.result)" class="timeline-tag">
                {{ log.resultLabel || resultText(log.result) }}
              </el-tag>
            </div>
            <div v-if="log.comment" class="timeline-comment">说明：{{ log.comment }}</div>
            <div v-if="log.reasonCode" class="timeline-comment">原因编码：{{ log.reasonCode }}</div>
            <div v-if="log.nextFollowTime" class="timeline-comment">下一步：{{ parseTime(log.nextFollowTime) }}</div>
            <div class="timeline-meta">操作人：{{ log.operatorName || log.operatorId || '-' }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else-if="!logDialog.loading" description="暂无阶段历史" />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="logDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 转移招聘负责人 / 岗位 -->
    <el-dialog v-model="transferDialog.visible" title="转移招聘负责人 / 应聘岗位" width="560px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="请至少指定新的招聘负责人或新的应聘岗位；已计入到岗统计的应聘记录不能更换岗位。"
      />
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules" label-width="120px">
        <el-form-item label="应聘记录">
          <el-input :model-value="transferDialog.row.name" disabled />
        </el-form-item>
        <el-form-item label="新招聘负责人" prop="recruiterId">
          <el-input v-model="transferForm.recruiterId" placeholder="用户ID" clearable @click="openTransferRecruiterSelect">
            <template #append>
              <el-button icon="User" @click="openTransferRecruiterSelect" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="新岗位ID" prop="jobId">
          <el-input v-model="transferForm.jobId" placeholder="岗位执行项ID，可为空" />
        </el-form-item>
        <el-form-item label="转移原因" prop="reason">
          <el-input v-model="transferForm.reason" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="transferDialog.submitting" @click="submitTransfer">确 定</el-button>
          <el-button @click="transferDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 邀约 / 报到 / 未报到登记 -->
    <el-dialog v-model="registryDialog.visible" :title="registryTitle" width="640px" append-to-body>
      <el-alert class="dialog-alert" type="warning" :closable="false" show-icon :title="registryTip" />
      <el-descriptions :column="2" border size="small" class="detail-panel">
        <el-descriptions-item label="候选人">{{ registryDialog.row.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="应聘编号">{{ registryDialog.application.applicationNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前阶段">{{ stageText(registryDialog.application.currentStage) }}</el-descriptions-item>
        <el-descriptions-item label="邀约结果">{{ registryDialog.application.offerResult || '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划报到">{{ registryDialog.application.planArrivalDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际到岗">{{ registryDialog.application.arrivalDate || '-' }}</el-descriptions-item>
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

    <!-- 用户选择：人才归属负责人 / 招聘负责人 / 转移负责人 -->
    <UserSelect ref="ownerSelectRef" :multiple="false" @confirm-call-back="handleOwnerSelected" />
    <UserSelect ref="recruiterSelectRef" :multiple="false" @confirm-call-back="handleRecruiterSelected" />
    <UserSelect ref="transferRecruiterSelectRef" :multiple="false" @confirm-call-back="handleTransferRecruiterSelected" />
  </div>
</template>

<script setup lang="ts">
import {
  addCandidate,
  getApplication,
  listCandidate,
  listStageLogs,
  precheckCandidate,
  registerArrival,
  registerNoArrival,
  registerOffer,
  transferApplication,
  transitionApplication,
  viewCandidatePhone
} from '@/api/hrtalent/application';
import type {
  HrApplicationTransitionForm,
  HrApplicationVO,
  HrCandidateCreateForm,
  HrCandidateQuery,
  HrCandidateVO,
  HrStageLogVO,
  HrTalentPrecheckVO
} from '@/api/hrtalent/application/types';
import UserSelect from '@/components/UserSelect/index.vue';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'HrApplication' });

const { recruit_candidate_stage, recruit_application_result, talent_status, talent_visibility_type, recruit_data_level } =
  toRefs<any>(
    useDict(
      'recruit_candidate_stage',
      'recruit_application_result',
      'talent_status',
      'talent_visibility_type',
      'recruit_data_level'
    )
  );

/** 阶段顺序（对齐后端 enums/CandidateStageEnum，用于计算「只能向前流转」的可选阶段） */
const STAGE_ORDER: string[] = [
  'new',
  'resume_review',
  'invite',
  'first_interview',
  'second_interview',
  'background',
  'offer',
  'pending_arrival',
  'arrived'
];

/** 阶段中文兜底（字典未加载时仍可读） */
const STAGE_TEXT: Record<string, string> = {
  new: '新简历',
  resume_review: '简历筛选',
  invite: '已邀约',
  first_interview: '初试',
  second_interview: '复试',
  background: '背调',
  offer: 'Offer',
  pending_arrival: '待到岗',
  arrived: '已到岗'
};

/** 应聘结果中文兜底（对齐 ApplicationResultEnum） */
const RESULT_TEXT: Record<string, string> = {
  processing: '处理中',
  passed: '已通过',
  rejected: '未通过',
  withdrawn: '已撤回',
  paused: '已暂停',
  talent_pool: '转入人才池'
};

/** 阶段历史动作中文（hr_recruit_stage_log.action_type 稳定编码） */
const ACTION_TEXT: Record<string, string> = {
  move: '阶段流转',
  reject: '淘汰',
  withdraw: '候选人放弃',
  pause: '暂缓',
  talent_pool: '人才保留',
  arrive: '报到'
};

/** 邀约结果可选编码（DDL 注释给出 accepted/rejected，无对应字典，允许自定义输入） */
const OFFER_RESULT_OPTIONS = [
  { label: '已接受（accepted）', value: 'accepted' },
  { label: '已拒绝（rejected）', value: 'rejected' }
];

/** 六条跳转前置校验（SPEC-P3 §3.2 / 设计 §7.2） */
const PREREQUISITE_RULES = [
  { key: 'first_interview', text: '① 进入「一面」前必须已存在面试安排' },
  { key: 'second_interview', text: '② 进入「二面」前必须已录入一面结论' },
  { key: 'background', text: '③ 进入「待背调」前必须已录入最终面试结论' },
  { key: 'offer', text: '④ 进入「待录用」前必须有背调结论，或经授权的免背调原因' },
  { key: 'pending_arrival', text: '⑤ 进入「待报到」前必须记录邀约结果与计划报到日期' },
  { key: 'override', text: '⑥ 管理员例外跳转（跳级/回退）必须填写原因，并写入敏感操作审计' }
];

const candidateList = ref<HrCandidateVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const submitting = ref(false);
const prechecking = ref(false);
const candidateFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();
const phoneFormRef = ref<ElFormInstance>();
const transitionFormRef = ref<ElFormInstance>();
const transferFormRef = ref<ElFormInstance>();
const registryFormRef = ref<ElFormInstance>();
const ownerSelectRef = ref<InstanceType<typeof UserSelect>>();
const recruiterSelectRef = ref<InstanceType<typeof UserSelect>>();
const transferRecruiterSelectRef = ref<InstanceType<typeof UserSelect>>();

/** 是否已完成重复处置（复用主档或确认非同一人） */
const duplicateConfirmed = ref(false);

const initFormData: HrCandidateCreateForm = {
  name: '',
  formerName: '',
  gender: undefined,
  highestEducation: undefined,
  phone: '',
  email: '',
  currentCity: '',
  expectedCity: '',
  currentCompany: '',
  currentPosition: '',
  expectedPosition: '',
  expectedSalaryMin: undefined,
  expectedSalaryMax: undefined,
  workYears: undefined,
  ownerId: undefined,
  ownerDeptId: undefined,
  ownerDeptName: '',
  visibilityType: undefined,
  dataLevel: undefined,
  sourceType: undefined,
  createApplication: true,
  jobId: undefined,
  recruiterId: undefined,
  contactDate: undefined,
  sourceChannelId: undefined,
  applicationRemark: '',
  remark: ''
};

/** 期望薪资上下限校验 */
const validateSalaryRange = (_rule: any, _value: any, callback: any) => {
  const min = form.value.expectedSalaryMin;
  const max = form.value.expectedSalaryMax;
  if (min !== undefined && min !== null && max !== undefined && max !== null && Number(min) > Number(max)) {
    callback(new Error('期望薪资下限不能大于上限'));
    return;
  }
  callback();
};

const data = reactive<PageData<HrCandidateCreateForm, HrCandidateQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    talentNo: undefined,
    phone: undefined,
    email: undefined,
    currentCompany: undefined,
    expectedPosition: undefined,
    stage: undefined,
    status: undefined,
    talentStatus: undefined,
    dataLevel: undefined,
    jobId: undefined,
    recruiterId: undefined
  },
  rules: {
    name: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
    email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
    expectedSalaryMax: [{ validator: validateSalaryRange, trigger: 'change' }],
    jobId: [
      {
        validator: (_rule: any, value: any, callback: any) => {
          if (form.value.createApplication && !value) {
            callback(new Error('创建应聘记录时，应聘岗位ID不能为空'));
            return;
          }
          callback();
        },
        trigger: 'blur'
      }
    ]
  }
});

const { queryParams, form, rules } = toRefs<PageData<HrCandidateCreateForm, HrCandidateQuery>>(data);
const { dialog, resetForm, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: candidateFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  pageSizeKey: 'pageSize',
  initialPageSize: 10,
  afterReset: () => handleQuery()
});

/** 阶段中文 */
const stageText = (code?: string) => (code ? STAGE_TEXT[code] || code : '-');
/** 结果中文 */
const resultText = (code?: string) => (code ? RESULT_TEXT[code] || code : '-');
/** 动作中文 */
const actionText = (code?: string) => (code ? ACTION_TEXT[code] || code : '-');
/** 性别中文兜底 */
const genderText = (code?: string) => {
  if (code === 'male') return '男';
  if (code === 'female') return '女';
  if (code === 'unknown') return '未知';
  return '-';
};
/** 结果标签颜色 */
const resultTagType = (code?: string) => {
  if (code === 'rejected') return 'danger';
  if (code === 'withdrawn') return 'info';
  if (code === 'paused') return 'warning';
  if (code === 'passed') return 'success';
  return 'primary';
};
/** 期望薪资展示 */
const salaryText = (min?: number, max?: number) => {
  if (min === undefined && max === undefined) {
    return '-';
  }
  return `${min ?? '-'} ~ ${max ?? '-'}`;
};

const getList = async () => {
  await withLoading(async () => {
    const res = await listCandidate(queryParams.value);
    candidateList.value = res.data?.rows || [];
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
  duplicateConfirmed.value = false;
};

const handleAdd = () => {
  resetForm();
  duplicateConfirmed.value = false;
  form.value.createApplication = true;
  showDialog('新增候选人（先查重）');
};

/* ------------------------------ 查重预检 ------------------------------ */

const precheckDialog = reactive<{
  visible: boolean;
  loading: boolean;
  reuseTalentId: string;
  result: HrTalentPrecheckVO | null;
}>({ visible: false, loading: false, reuseTalentId: '', result: null });

/** 预检结果按级别分组展示 */
const precheckGroups = computed(() => {
  const result = precheckDialog.result;
  return [
    { level: 'strong', title: '强匹配（电话/邮箱哈希命中，禁止静默创建）', items: result?.strongMatches || [] },
    { level: 'medium', title: '中匹配（姓名 + 公司/学校/简历哈希）', items: result?.mediumMatches || [] },
    { level: 'weak', title: '弱匹配（姓名 + 期望岗位，仅提示）', items: result?.weakMatches || [] }
  ];
});

/** 执行查重预检；返回是否命中强/中匹配 */
const doPrecheck = async (): Promise<HrTalentPrecheckVO | null> => {
  if (!form.value.name) {
    modal.msgWarning('请先填写姓名，姓名是查重的前置条件');
    return null;
  }
  prechecking.value = true;
  try {
    const res = await precheckCandidate({
      name: form.value.name,
      phone: form.value.phone,
      email: form.value.email,
      currentCompany: form.value.currentCompany,
      expectedPosition: form.value.expectedPosition
    });
    return res.data || null;
  } finally {
    prechecking.value = false;
  }
};

/** 按钮：查重预检 */
const runPrecheck = async () => {
  const result = await doPrecheck();
  if (!result) {
    return;
  }
  precheckDialog.result = result;
  precheckDialog.reuseTalentId = '';
  precheckDialog.visible = true;
};

/** 复用选中的已有主档 */
const confirmReuse = () => {
  if (!precheckDialog.reuseTalentId) {
    return;
  }
  form.value.talentId = precheckDialog.reuseTalentId;
  duplicateConfirmed.value = true;
  precheckDialog.visible = false;
  modal.msgSuccess('已选择复用已有主档，可在提交时继续追加应聘记录');
};

/** 确认不是同一人，继续新建 */
const confirmDistinct = () => {
  form.value.talentId = undefined;
  duplicateConfirmed.value = true;
  precheckDialog.visible = false;
  modal.msgSuccess('已确认非同一人，将创建新的人才主档');
};

/* ------------------------------ 新增候选人 ------------------------------ */

const submitForm = () => {
  candidateFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    // 未做重复处置时先自动预检：命中强/中匹配则强制人工确认（§7.6.3）
    if (!duplicateConfirmed.value && !form.value.talentId) {
      const result = await doPrecheck();
      if (!result) {
        return;
      }
      if (result.strongDuplicated || result.mediumDuplicated) {
        precheckDialog.result = result;
        precheckDialog.reuseTalentId = '';
        precheckDialog.visible = true;
        modal.msgWarning('发现疑似重复人员，请先选择「复用已有主档」或「确认不是同一人」');
        return;
      }
      duplicateConfirmed.value = true;
    }
    // 复用已有主档时后端只追加应聘记录、忽略主档字段，因此必须同时创建应聘记录
    if (form.value.talentId && !form.value.createApplication) {
      modal.msgWarning('复用已有主档时必须同时创建应聘记录，否则不会产生任何变更');
      return;
    }
    submitting.value = true;
    try {
      const payload: HrCandidateCreateForm = { ...form.value };
      // 人工已处置重复（复用或确认非同一人）时必须回传 duplicateAck，否则后端拒绝创建新主档
      if (duplicateConfirmed.value) {
        payload.duplicateAck = true;
      }
      // 不创建应聘记录时清空应聘侧字段，避免后端误建
      if (!payload.createApplication) {
        payload.jobId = undefined;
        payload.recruiterId = undefined;
        payload.contactDate = undefined;
        payload.applicationRemark = undefined;
      }
      await addCandidate(payload);
      modal.msgSuccess(form.value.talentId ? '已复用已有主档并新增应聘记录' : '新增成功');
      closeDialog();
      await getList();
    } finally {
      submitting.value = false;
    }
  });
};

const openOwnerSelect = () => ownerSelectRef.value?.open();
const openRecruiterSelect = () => recruiterSelectRef.value?.open();
const openTransferRecruiterSelect = () => transferRecruiterSelectRef.value?.open();

const handleOwnerSelected = (users: any[]) => {
  const user = users?.[0];
  if (user) {
    form.value.ownerId = user.userId;
  }
};
const handleRecruiterSelected = (users: any[]) => {
  const user = users?.[0];
  if (user) {
    form.value.recruiterId = user.userId;
  }
};
const handleTransferRecruiterSelected = (users: any[]) => {
  const user = users?.[0];
  if (user) {
    transferForm.value.recruiterId = user.userId;
  }
};

/* ------------------------------ 详情 ------------------------------ */

const detail = reactive<{ visible: boolean; row: Partial<HrCandidateVO> }>({ visible: false, row: {} });

const handleDetail = (row: HrCandidateVO) => {
  detail.row = row;
  detail.visible = true;
};

/** 加载应聘记录详情（流转/转移/报到都需要最新 version） */
const loadApplication = async (row: Partial<HrCandidateVO>): Promise<HrApplicationVO | null> => {
  if (!row.latestApplicationId) {
    modal.msgWarning('该候选人暂无应聘记录，请先创建应聘记录');
    return null;
  }
  const res = await getApplication(row.latestApplicationId);
  return res.data || null;
};

/* ------------------------------ 电话明文 ------------------------------ */

const phoneDialog = reactive<{
  visible: boolean;
  loading: boolean;
  step: 'purpose' | 'result';
  row: Partial<HrCandidateVO>;
  phone: string;
}>({ visible: false, loading: false, step: 'purpose', row: {}, phone: '' });

const phoneForm = ref<{ purpose: string }>({ purpose: '' });
const phoneRules: ElFormRules = {
  purpose: [{ required: true, message: '查看用途不能为空', trigger: 'blur' }]
};

const openPhoneDialog = (row: HrCandidateVO) => {
  phoneDialog.row = row;
  phoneDialog.phone = '';
  phoneDialog.step = 'purpose';
  phoneForm.value.purpose = '';
  phoneDialog.visible = true;
};

const submitPhoneView = () => {
  phoneFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    phoneDialog.loading = true;
    try {
      const res = await viewCandidatePhone(phoneDialog.row.talentId!, { purpose: phoneForm.value.purpose });
      phoneDialog.phone = (res.data as unknown as string) || '';
      phoneDialog.step = 'result';
      modal.msgSuccess('已获取电话明文，本次查看已记录审计');
    } finally {
      phoneDialog.loading = false;
    }
  });
};

/* ------------------------------ 阶段流转 ------------------------------ */

const transitionDialog = reactive<{
  visible: boolean;
  submitting: boolean;
  row: Partial<HrCandidateVO>;
  application: Partial<HrApplicationVO>;
}>({ visible: false, submitting: false, row: {}, application: {} });

const transitionForm = ref<HrApplicationTransitionForm & { mode: 'stage' | 'result' }>({
  mode: 'stage',
  fromStage: undefined,
  toStage: undefined,
  result: undefined,
  reasonCode: undefined,
  comment: undefined,
  nextFollowTime: undefined,
  override: false,
  overrideReason: undefined,
  offerResult: undefined,
  planArrivalDate: undefined
});

/** 目标阶段候选项：默认只允许向前流转（已报到必须走报到登记接口） */
const targetStageOptions = computed(() => {
  const current = transitionDialog.application.currentStage || 'new';
  const currentIndex = STAGE_ORDER.indexOf(current);
  return STAGE_ORDER.filter((code, index) => {
    if (code === 'arrived' || code === current) {
      return false;
    }
    return transitionForm.value.override ? true : index > currentIndex;
  }).map(code => ({ value: code, label: stageText(code) }));
});

/** 当前目标阶段命中的前置校验条目（用于高亮提示） */
const activePrerequisite = computed(() => {
  if (transitionForm.value.override) {
    return 'override';
  }
  if (transitionForm.value.mode !== 'stage') {
    return '';
  }
  return transitionForm.value.toStage || '';
});

const transitionRules = computed<ElFormRules>(() => ({
  toStage:
    transitionForm.value.mode === 'stage' ? [{ required: true, message: '目标阶段不能为空', trigger: 'change' }] : [],
  result:
    transitionForm.value.mode === 'result' ? [{ required: true, message: '目标结果不能为空', trigger: 'change' }] : [],
  offerResult:
    transitionForm.value.mode === 'stage' && transitionForm.value.toStage === 'pending_arrival'
      ? [{ required: true, message: '进入待报到前必须记录邀约结果', trigger: 'change' }]
      : [],
  planArrivalDate:
    transitionForm.value.mode === 'stage' && transitionForm.value.toStage === 'pending_arrival'
      ? [{ required: true, message: '进入待报到前必须记录计划报到日期', trigger: 'change' }]
      : [],
  overrideReason: transitionForm.value.override
    ? [{ required: true, message: '管理员例外跳转必须填写原因', trigger: 'blur' }]
    : [],
  comment: [
    {
      validator: (_rule: any, value: any, callback: any) => {
        const needReason = transitionForm.value.mode === 'result'
          && (transitionForm.value.result === 'rejected' || transitionForm.value.result === 'withdrawn');
        if (needReason && !value && !transitionForm.value.reasonCode) {
          callback(new Error('淘汰与候选人放弃必须填写原因（原因编码或阶段说明至少一项）'));
          return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ]
}));

const openTransition = async (row: HrCandidateVO) => {
  transitionDialog.row = row;
  transitionDialog.application = {};
  transitionForm.value = {
    mode: 'stage',
    fromStage: row.latestStage,
    toStage: undefined,
    result: undefined,
    reasonCode: undefined,
    comment: undefined,
    nextFollowTime: undefined,
    override: false,
    overrideReason: undefined,
    offerResult: undefined,
    planArrivalDate: undefined
  } as HrApplicationTransitionForm & { mode: 'stage' | 'result' };
  const application = await loadApplication(row);
  if (!application) {
    return;
  }
  transitionDialog.application = application;
  transitionForm.value.fromStage = application.currentStage;
  transitionForm.value.version = application.version;
  transitionDialog.visible = true;
};

const submitTransition = () => {
  transitionFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    const applicationId = transitionDialog.application.applicationId;
    if (!applicationId) {
      modal.msgError('应聘记录不存在，请刷新列表后重试');
      return;
    }
    transitionDialog.submitting = true;
    try {
      const payload: HrApplicationTransitionForm = {
        fromStage: transitionForm.value.fromStage,
        // toStage 与 result 二选一（后端强制）
        toStage: transitionForm.value.mode === 'stage' ? transitionForm.value.toStage : undefined,
        result: transitionForm.value.mode === 'result' ? transitionForm.value.result : undefined,
        reasonCode: transitionForm.value.reasonCode,
        comment: transitionForm.value.comment,
        nextFollowTime: transitionForm.value.nextFollowTime,
        override: transitionForm.value.override,
        overrideReason: transitionForm.value.override ? transitionForm.value.overrideReason : undefined,
        offerResult: transitionForm.value.toStage === 'pending_arrival' ? transitionForm.value.offerResult : undefined,
        planArrivalDate:
          transitionForm.value.toStage === 'pending_arrival' ? transitionForm.value.planArrivalDate : undefined,
        version: transitionForm.value.version
      };
      await transitionApplication(applicationId, payload);
      modal.msgSuccess('阶段流转成功');
      transitionDialog.visible = false;
      await getList();
    } finally {
      transitionDialog.submitting = false;
    }
  });
};

/* ------------------------------ 阶段历史 ------------------------------ */

const logDialog = reactive<{ visible: boolean; loading: boolean }>({ visible: false, loading: false });
const logList = ref<HrStageLogVO[]>([]);

const handleLogs = async (row: HrCandidateVO) => {
  if (!row.latestApplicationId) {
    modal.msgWarning('该候选人暂无应聘记录');
    return;
  }
  logDialog.visible = true;
  logDialog.loading = true;
  logList.value = [];
  try {
    const res = await listStageLogs(row.latestApplicationId!);
    logList.value = Array.isArray(res.data) ? res.data : [];
  } finally {
    logDialog.loading = false;
  }
};

/* ------------------------------ 转移 ------------------------------ */

const transferDialog = reactive<{
  visible: boolean;
  submitting: boolean;
  row: Partial<HrCandidateVO>;
  application: Partial<HrApplicationVO>;
}>({
  visible: false,
  submitting: false,
  row: {},
  application: {}
});
const transferForm = ref<{ recruiterId?: string | number; jobId?: string | number; reason?: string; version?: number }>({
  recruiterId: undefined,
  jobId: undefined,
  reason: undefined,
  version: undefined
});
const transferRules: ElFormRules = {
  jobId: [
    {
      validator: (_rule: any, value: any, callback: any) => {
        if (!value && !transferForm.value.recruiterId) {
          callback(new Error('请至少指定新的招聘负责人或新的应聘岗位'));
          return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ]
};

const openTransfer = async (row: HrCandidateVO) => {
  transferDialog.row = row;
  transferForm.value = { recruiterId: undefined, jobId: undefined, reason: undefined, version: undefined };
  const application = await loadApplication(row);
  if (!application) {
    return;
  }
  transferDialog.application = application;
  transferForm.value.version = application.version;
  transferDialog.visible = true;
};

const submitTransfer = () => {
  transferFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    transferDialog.submitting = true;
    try {
      await transferApplication(transferDialog.application.applicationId!, { ...transferForm.value });
      modal.msgSuccess('转移成功');
      transferDialog.visible = false;
      await getList();
    } finally {
      transferDialog.submitting = false;
    }
  });
};

/* ------------------------------ 邀约 / 报到 / 未报到 ------------------------------ */

const registryDialog = reactive<{
  visible: boolean;
  submitting: boolean;
  mode: 'offer' | 'arrival' | 'noArrival';
  row: Partial<HrCandidateVO>;
  application: Partial<HrApplicationVO>;
}>({ visible: false, submitting: false, mode: 'offer', row: {}, application: {} });

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

const openRegistry = async (row: HrCandidateVO, mode: 'offer' | 'arrival' | 'noArrival') => {
  registryDialog.row = row;
  registryDialog.application = {};
  registryDialog.mode = mode;
  registryForm.value = {};
  const application = await loadApplication(row);
  if (!application) {
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

/* ------------------------------ 操作下拉 ------------------------------ */

const handleMore = (row: HrCandidateVO, command: string) => {
  if (command === 'logs') {
    handleLogs(row);
  } else if (command === 'transfer') {
    openTransfer(row);
  } else if (command === 'offer') {
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

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--app-text-muted);
}

/* 六条前置校验提示 */
.rule-list {
  margin: 4px 0 0;
  padding-left: 18px;
  line-height: 1.9;
  font-size: 12px;
}

.rule-list li.is-active {
  font-weight: 600;
  color: var(--el-color-primary);
}

/* 查重预检结果分组 */
.precheck-radio {
  display: block;
  width: 100%;
}

.precheck-group {
  margin-bottom: 12px;
}

.precheck-group-title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
}

.precheck-empty {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--app-text-muted);
}

/* 阶段历史时间线 */
.timeline-wrap {
  max-height: 460px;
  overflow: auto;
  padding: 4px 8px;
}

.timeline-title {
  font-size: 13px;
  font-weight: 600;
}

.timeline-tag {
  margin-left: 6px;
}

.timeline-comment {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
  word-break: break-all;
}

.timeline-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.phone-plain {
  font-family: var(--el-font-family-mono, monospace);
  font-weight: 600;
  letter-spacing: 1px;
}
</style>
