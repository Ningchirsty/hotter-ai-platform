<template>
  <div class="p-2 app-container hrtalent-talent-profile-page">
    <PageHeading
      title="人才档案"
      subtitle="一人一档的人才主档：§8.17 组合检索、详情（教育/工作/项目/标签/跟进/简历/应聘摘要）、查重预检新增、编辑、归档与电话明文受控查看"
      module="hrtalent"
    />

    <!-- 人才组合检索（§8.17） -->
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>人才组合检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="queryParams.name" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="手机号后四位" prop="phoneTail4">
            <el-input
              v-model="queryParams.phoneTail4"
              placeholder="精确匹配，如 8888"
              maxlength="4"
              clearable
              style="width: 150px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="人才编号" prop="talentNo">
            <el-input v-model="queryParams.talentNo" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="当前岗位" prop="currentPosition">
            <el-input v-model="queryParams.currentPosition" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="历史岗位" prop="historyPosition">
            <el-input
              v-model="queryParams.historyPosition"
              placeholder="命中任一工作经历"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="意向岗位" prop="expectedPosition">
            <el-input v-model="queryParams.expectedPosition" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="人才标签" prop="tagIds">
            <el-select
              v-model="queryParams.tagIds"
              multiple
              collapse-tags
              collapse-tags-tooltip
              placeholder="命中任一标签"
              clearable
              style="width: 220px"
            >
              <el-option v-for="tag in tagOptions" :key="String(tag.tagId)" :label="tag.tagName" :value="tag.tagId!" />
            </el-select>
          </el-form-item>
          <el-form-item label="最高学历" prop="highestEducation">
            <el-select v-model="queryParams.highestEducation" placeholder="请选择" clearable style="width: 150px">
              <el-option v-for="dict in talent_education" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="专业" prop="major">
            <el-input v-model="queryParams.major" placeholder="教育经历专业" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="毕业院校" prop="schoolName">
            <el-input v-model="queryParams.schoolName" placeholder="教育经历院校" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="当前城市" prop="currentCity">
            <el-input v-model="queryParams.currentCity" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="意向城市" prop="expectedCity">
            <el-input v-model="queryParams.expectedCity" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="工作年限" prop="workYearsBegin">
            <el-input-number
              v-model="queryParams.workYearsBegin"
              :min="0"
              :controls="false"
              placeholder="下限"
              style="width: 90px"
            />
            <span class="range-sep">-</span>
            <el-input-number
              v-model="queryParams.workYearsEnd"
              :min="0"
              :controls="false"
              placeholder="上限"
              style="width: 90px"
            />
          </el-form-item>
          <el-form-item label="所属行业" prop="industry">
            <el-input v-model="queryParams.industry" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="当前公司" prop="currentCompany">
            <el-input v-model="queryParams.currentCompany" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="来源渠道ID" prop="sourceChannelId">
            <el-input v-model="queryParams.sourceChannelId" placeholder="渠道ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="归属公司部门" prop="ownerDeptId">
            <el-input v-model="queryParams.ownerDeptId" placeholder="部门ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="负责人ID" prop="ownerId">
            <el-input v-model="queryParams.ownerId" placeholder="用户ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="人才池" prop="poolId">
            <el-select v-model="queryParams.poolId" placeholder="请选择人才池" clearable filterable style="width: 200px">
              <el-option v-for="pool in poolOptions" :key="String(pool.poolId)" :label="pool.poolName" :value="pool.poolId!" />
            </el-select>
          </el-form-item>
          <el-form-item label="人才状态" prop="talentStatus">
            <el-select v-model="queryParams.talentStatus" placeholder="请选择" clearable style="width: 150px">
              <el-option v-for="dict in talent_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="最近联系时间">
            <el-date-picker
              v-model="lastFollowRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              range-separator="-"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 340px"
            />
          </el-form-item>
          <el-form-item label="是否有当前简历" prop="hasCurrentResume">
            <el-select v-model="queryParams.hasCurrentResume" placeholder="不过滤" clearable style="width: 120px">
              <el-option label="有当前简历" :value="true" />
              <el-option label="无当前简历" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item label="简历解析状态" prop="resumeParseStatus">
            <el-select v-model="queryParams.resumeParseStatus" placeholder="当前版本" clearable style="width: 150px">
              <el-option
                v-for="dict in talent_resume_parse_status"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="包含已归档" prop="includeArchived">
            <el-switch v-model="queryParams.includeArchived" />
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
            <span class="panel-kicker">Talent Profile</span>
            <h3>人才主档</h3>
            <p>
              共 {{ total }} 条记录。列表电话与邮箱一律<strong>脱敏</strong>；电话明文需在弹窗中必填用途后获取，系统会写入敏感操作审计。
              新增人才前必须执行查重预检（强/中匹配时不得静默创建）。<strong>资料完整度仅作展示</strong>：后端对完整度筛选条件 fail-fast 拒绝，页面不提供该筛选。
              「导出」按<strong>当前检索条件</strong>创建异步导出任务（普通台账 / 敏感台账，敏感台账需独立权限与用途），文件存私有对象存储，仅提供系统内受控下载地址。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['talent:profile:add']" type="primary" icon="Plus" @click="handleAdd">
              新增人才
            </el-button>
            <el-button v-hasPermi="['talent:profile:export']" type="success" plain icon="Download" @click="openExportDialog">
              导出
            </el-button>
            <el-button v-hasPermi="['talent:profile:export']" plain icon="Tickets" @click="openExportTaskDialog">
              导出任务
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="profileList">
        <el-table-column label="人才编号" align="center" prop="talentNo" width="150" show-overflow-tooltip />
        <el-table-column label="姓名" align="center" prop="name" width="100" show-overflow-tooltip />
        <el-table-column label="性别" align="center" width="70">
          <template #default="scope">{{ scope.row.genderText || genderText(scope.row.gender) }}</template>
        </el-table-column>
        <el-table-column label="电话（脱敏）" align="center" width="140">
          <template #default="scope">{{ scope.row.phoneMasked || '-' }}</template>
        </el-table-column>
        <el-table-column label="最高学历" align="center" width="100">
          <template #default="scope">
            <dict-tag v-if="scope.row.highestEducation" :options="talent_education" :value="scope.row.highestEducation" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="当前公司" align="center" prop="currentCompany" width="170" show-overflow-tooltip />
        <el-table-column label="当前职位" align="center" prop="currentPosition" width="130" show-overflow-tooltip />
        <el-table-column label="意向岗位" align="center" prop="expectedPosition" width="130" show-overflow-tooltip />
        <el-table-column label="城市" align="center" prop="currentCity" width="100" show-overflow-tooltip />
        <el-table-column label="工作年限" align="center" prop="workYears" width="90" />
        <el-table-column label="人才状态" align="center" width="100">
          <template #default="scope">
            <dict-tag v-if="scope.row.talentStatus" :options="talent_status" :value="scope.row.talentStatus" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="资料完整度" align="center" width="150">
          <template #default="scope">
            <el-progress
              :percentage="scope.row.completeness ?? 0"
              :stroke-width="10"
              :status="scope.row.completeness >= 80 ? 'success' : undefined"
            />
            <span class="completeness-text">
              {{ scope.row.completenessLevelText || completenessText(scope.row.completenessLevel) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="负责人" align="center" width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.ownerName || scope.row.ownerId || '-' }}</template>
        </el-table-column>
        <el-table-column label="归属部门" align="center" width="140" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.ownerDeptName || scope.row.ownerDeptId || '-' }}</template>
        </el-table-column>
        <el-table-column label="可见范围" align="center" width="100">
          <template #default="scope">
            <dict-tag v-if="scope.row.visibilityType" :options="talent_visibility_type" :value="scope.row.visibilityType" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="最近简历更新" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.resumeUpdateTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" class-name="small-padding fixed-width" fixed="right">
          <template #default="scope">
            <el-tooltip content="详情" placement="top">
              <el-button
                v-hasPermi="['talent:profile:query']"
                link
                type="primary"
                icon="Search"
                @click="handleDetail(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="编辑" placement="top">
              <el-button
                v-hasPermi="['talent:profile:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="电话明文（需填用途，记录审计）" placement="top">
              <el-button
                v-hasPermi="['talent:profile:phone-view']"
                link
                type="warning"
                icon="Phone"
                @click="openPhoneDialog(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-dropdown trigger="click" @command="cmd => handleMore(scope.row, cmd)">
              <el-button link type="primary" icon="More"></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-hasPermi="['talent:profile:query']" command="tags">标签挂载</el-dropdown-item>
                  <el-dropdown-item v-hasPermi="['talent:profile:archive']" command="archive" divided>归档</el-dropdown-item>
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

    <!-- 新增 / 编辑人才主档（新增必先查重预检） -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="980px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="入库前必须查重（设计 §7.6.3）：请先执行「查重预检」。命中强/中匹配时必须选择「复用已有主档」或「确认不是同一人」，系统不允许静默创建重复人员。"
      />
      <el-form ref="profileFormRef" :model="form" :rules="rules" label-width="120px">
        <el-divider content-position="left">基础信息</el-divider>
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
            <el-form-item label="出生日期" prop="birthDate">
              <el-date-picker
                v-model="form.birthDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="年龄快照" prop="ageSnapshot">
              <el-input-number v-model="form.ageSnapshot" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最高学历" prop="highestEducation">
              <el-select v-model="form.highestEducation" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="dict in talent_education" :key="dict.value" :label="dict.label" :value="dict.value" />
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
            <el-form-item label="备用手机号" prop="backupPhone">
              <el-input v-model="form.backupPhone" placeholder="明文提交，可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="明文提交，服务端加密存储" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="其他联系方式" prop="otherContact">
              <el-input v-model="form.otherContact" placeholder="如微信/QQ，可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="当前城市" prop="currentCity">
              <el-input v-model="form.currentCity" placeholder="请输入当前城市" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="意向城市" prop="expectedCity">
              <el-input v-model="form.expectedCity" placeholder="请输入期望工作城市" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">职业信息</el-divider>
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
            <el-form-item label="意向岗位" prop="expectedPosition">
              <el-input v-model="form.expectedPosition" placeholder="弱匹配依赖字段" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="工作年限" prop="workYears">
              <el-input-number v-model="form.workYears" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="所属行业" prop="industry">
              <el-input v-model="form.industry" placeholder="请输入所属行业" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="主档来源" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="dict in talent_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
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

        <el-divider content-position="left">归属与权限</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="人才负责人" prop="ownerId">
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
            <el-form-item label="协助人" prop="assistantIds">
              <el-input :model-value="assistantText" placeholder="最多 20 人" readonly @click="openAssistantSelect">
                <template #append>
                  <el-button icon="User" @click="openAssistantSelect" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="可见范围" prop="visibilityType">
              <el-select v-model="form.visibilityType" placeholder="请选择" clearable style="width: 100%">
                <el-option
                  v-for="dict in talent_visibility_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
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
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="状态原因" prop="statusReason">
              <el-input
                v-model="form.statusReason"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                placeholder="限制/禁止联系状态必填（§7.6.2）"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态到期日" prop="statusExpireDate">
              <el-date-picker
                v-model="form.statusExpireDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="可为空"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item v-if="form.talentId" label="版本号">
          <el-input :model-value="form.version" disabled />
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

    <!-- 查重预检结果：强 / 中 / 弱分级展示 -->
    <el-dialog v-model="precheckDialog.visible" title="查重预检结果" width="940px" append-to-body>
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
        title="匹配分级（设计 §21.15）：强匹配 = 电话/邮箱哈希命中（禁止静默新增）；中匹配 = 姓名 +（公司 / 学校 / 简历哈希）；弱匹配 = 姓名 + 期望岗位（仅提示）。姓名单独命中不构成任何级别。"
      />
      <el-alert
        v-if="blockingDuplicated"
        class="dialog-alert"
        type="error"
        :closable="false"
        show-icon
        title="后端契约限制：POST /talent/profiles 固定以 duplicateAck=false 做入库前查重，因此命中强/中匹配时本页无法强制新建（会返回「疑似重复人员」错误）。请选择「复用选中主档」查看/维护已有档案；若确为新人，请到「候选人跟进」页面新增候选人（该接口支持 duplicateAck 显式确认非同一人）。"
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
            <el-table-column label="姓名" align="center" prop="name" width="90" />
            <el-table-column label="电话（脱敏）" align="center" prop="phoneMasked" width="130" />
            <el-table-column label="当前公司" align="center" prop="currentCompany" show-overflow-tooltip />
            <el-table-column label="意向岗位" align="center" prop="expectedPosition" show-overflow-tooltip />
            <el-table-column label="命中原因" align="center" prop="reason" width="150" show-overflow-tooltip />
            <el-table-column label="创建日期" align="center" prop="createDate" width="110" />
          </el-table>
          <div v-else class="precheck-empty">无</div>
        </div>
      </el-radio-group>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :disabled="!precheckDialog.reuseTalentId" @click="confirmReuse">
            复用选中主档（查看档案）
          </el-button>
          <el-button type="warning" plain :disabled="blockingDuplicated" @click="confirmDistinct">
            确认不是同一人，继续新建
          </el-button>
          <el-button @click="precheckDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 人才详情：教育 / 工作 / 项目 / 标签 / 跟进 / 简历 / 应聘记录摘要 -->
    <el-dialog v-model="detail.visible" title="人才档案详情" width="1080px" append-to-body>
      <el-tabs v-model="detail.tab" class="detail-tabs">
        <el-tab-pane label="基础信息" name="base">
          <el-descriptions :column="2" border size="small" class="detail-panel">
            <el-descriptions-item label="人才编号">{{ detail.row.talentNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ detail.row.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="性别">
              {{ detail.row.genderText || genderText(detail.row.gender) }}
            </el-descriptions-item>
            <el-descriptions-item label="曾用名/英文名">{{ detail.row.formerName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="出生日期">{{ detail.row.birthDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="年龄快照">{{ detail.row.ageSnapshot ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="电话（脱敏）">
              {{ detail.row.phoneMasked || '-' }}
              <el-button
                v-hasPermi="['talent:profile:phone-view']"
                link
                type="warning"
                icon="Phone"
                @click="openPhoneDialog(detail.row)"
              >
                明文
              </el-button>
            </el-descriptions-item>
            <el-descriptions-item label="邮箱（脱敏）">{{ detail.row.emailMasked || '-' }}</el-descriptions-item>
            <el-descriptions-item label="最高学历">
              <dict-tag
                v-if="detail.row.highestEducation"
                :options="talent_education"
                :value="detail.row.highestEducation"
              />
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="资料完整度">
              {{ detail.row.completeness ?? '-' }}%（{{
                detail.row.completenessLevelText || completenessText(detail.row.completenessLevel)
              }}）
            </el-descriptions-item>
            <el-descriptions-item label="当前城市">{{ detail.row.currentCity || '-' }}</el-descriptions-item>
            <el-descriptions-item label="意向城市">{{ detail.row.expectedCity || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前公司">{{ detail.row.currentCompany || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前职位">{{ detail.row.currentPosition || '-' }}</el-descriptions-item>
            <el-descriptions-item label="意向岗位">{{ detail.row.expectedPosition || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工作年限">{{ detail.row.workYears ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="所属行业">{{ detail.row.industry || '-' }}</el-descriptions-item>
            <el-descriptions-item label="期望薪资">
              {{ salaryText(detail.row.expectedSalaryMin, detail.row.expectedSalaryMax) }}
            </el-descriptions-item>
            <el-descriptions-item label="人才负责人">
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
              <dict-tag
                v-if="detail.row.visibilityType"
                :options="talent_visibility_type"
                :value="detail.row.visibilityType"
              />
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="数据分级">
              <dict-tag v-if="detail.row.dataLevel" :options="recruit_data_level" :value="detail.row.dataLevel" />
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="主档来源">{{ detail.row.sourceType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态原因">{{ detail.row.statusReason || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态到期日">{{ detail.row.statusExpireDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="最近跟进时间">
              {{ parseTime(detail.row.lastFollowTime) || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="下次联系时间">
              {{ parseTime(detail.row.nextFollowTime) || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="是否已有应聘记录">
              {{ detail.row.hasApplication ? '是' : '否' }}
            </el-descriptions-item>
            <el-descriptions-item label="当前简历ID">{{ detail.row.currentResumeId ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="被合并到">{{ detail.row.mergedToId ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.row.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="教育经历" name="education">
          <div class="tab-toolbar">
            <el-button
              v-hasPermi="['talent:profile:edit']"
              type="primary"
              size="small"
              icon="Plus"
              @click="openExperience('education')"
            >
              新增教育经历
            </el-button>
          </div>
          <el-table v-loading="detail.loading" border size="small" :data="detail.educations">
            <el-table-column label="学校" prop="schoolName" min-width="160" show-overflow-tooltip />
            <el-table-column label="专业" prop="major" min-width="140" show-overflow-tooltip />
            <el-table-column label="学历" align="center" width="100">
              <template #default="scope">
                <dict-tag v-if="scope.row.education" :options="talent_education" :value="scope.row.education" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="学位" align="center" width="100">
              <template #default="scope">
                <dict-tag v-if="scope.row.degree" :options="talent_degree" :value="scope.row.degree" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="起止" align="center" width="200">
              <template #default="scope">{{ scope.row.startDate || '-' }} ~ {{ scope.row.endDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="全日制" align="center" width="90">
              <template #default="scope">{{ flagText(scope.row.fullTimeFlag) }}</template>
            </el-table-column>
            <el-table-column label="来源" align="center" width="110">
              <template #default="scope">
                <dict-tag v-if="scope.row.sourceType" :options="talent_source_type" :value="scope.row.sourceType" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="130" fixed="right">
              <template #default="scope">
                <el-button
                  v-hasPermi="['talent:profile:edit']"
                  link
                  type="primary"
                  icon="Edit"
                  @click="openExperience('education', scope.row)"
                ></el-button>
                <el-button
                  v-hasPermi="['talent:profile:edit']"
                  link
                  type="danger"
                  icon="Delete"
                  @click="removeExperience('education', scope.row)"
                ></el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="工作经历" name="work">
          <div class="tab-toolbar">
            <el-button
              v-hasPermi="['talent:profile:edit']"
              type="primary"
              size="small"
              icon="Plus"
              @click="openExperience('work')"
            >
              新增工作经历
            </el-button>
          </div>
          <el-table v-loading="detail.loading" border size="small" :data="detail.works">
            <el-table-column label="公司" prop="companyName" min-width="160" show-overflow-tooltip />
            <el-table-column label="部门" prop="departmentName" min-width="120" show-overflow-tooltip />
            <el-table-column label="职位" prop="positionName" min-width="130" show-overflow-tooltip />
            <el-table-column label="行业" prop="industry" min-width="110" show-overflow-tooltip />
            <el-table-column label="起止" align="center" width="200">
              <template #default="scope">{{ scope.row.startDate || '-' }} ~ {{ scope.row.endDate || '至今' }}</template>
            </el-table-column>
            <el-table-column label="当前任职" align="center" width="90">
              <template #default="scope">{{ flagText(scope.row.currentFlag) }}</template>
            </el-table-column>
            <el-table-column label="来源" align="center" width="110">
              <template #default="scope">
                <dict-tag v-if="scope.row.sourceType" :options="talent_source_type" :value="scope.row.sourceType" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="130" fixed="right">
              <template #default="scope">
                <el-button
                  v-hasPermi="['talent:profile:edit']"
                  link
                  type="primary"
                  icon="Edit"
                  @click="openExperience('work', scope.row)"
                ></el-button>
                <el-button
                  v-hasPermi="['talent:profile:edit']"
                  link
                  type="danger"
                  icon="Delete"
                  @click="removeExperience('work', scope.row)"
                ></el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="项目经历" name="project">
          <div class="tab-toolbar">
            <el-button
              v-hasPermi="['talent:profile:edit']"
              type="primary"
              size="small"
              icon="Plus"
              @click="openExperience('project')"
            >
              新增项目经历
            </el-button>
          </div>
          <el-table v-loading="detail.loading" border size="small" :data="detail.projects">
            <el-table-column label="项目名称" prop="projectName" min-width="170" show-overflow-tooltip />
            <el-table-column label="角色" prop="projectRole" width="120" show-overflow-tooltip />
            <el-table-column label="起止" align="center" width="200">
              <template #default="scope">{{ scope.row.startDate || '-' }} ~ {{ scope.row.endDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="职责" prop="responsibility" min-width="180" show-overflow-tooltip />
            <el-table-column label="成果" prop="achievement" min-width="180" show-overflow-tooltip />
            <el-table-column label="来源" align="center" width="110">
              <template #default="scope">
                <dict-tag v-if="scope.row.sourceType" :options="talent_source_type" :value="scope.row.sourceType" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="130" fixed="right">
              <template #default="scope">
                <el-button
                  v-hasPermi="['talent:profile:edit']"
                  link
                  type="primary"
                  icon="Edit"
                  @click="openExperience('project', scope.row)"
                ></el-button>
                <el-button
                  v-hasPermi="['talent:profile:edit']"
                  link
                  type="danger"
                  icon="Delete"
                  @click="removeExperience('project', scope.row)"
                ></el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="标签" name="tags">
          <div class="tab-toolbar">
            <el-button v-hasPermi="['talent:profile:edit']" type="primary" size="small" icon="Edit" @click="openTagDialog">
              维护标签
            </el-button>
          </div>
          <div class="tag-wall">
            <el-tag v-for="tag in detail.tags" :key="String(tag.tagId)" class="tag-item" :type="tag.sensitiveFlag === '1' ? 'danger' : 'primary'">
              {{ tag.tagName }}{{ tag.sensitiveFlag === '1' ? '（敏感）' : '' }}
            </el-tag>
            <span v-if="!detail.tags.length" class="empty-text">暂无标签</span>
          </div>
        </el-tab-pane>

        <el-tab-pane label="跟进记录" name="follow">
          <div class="tab-toolbar">
            <el-button
              v-hasPermi="['talent:profile:edit']"
              type="primary"
              size="small"
              icon="Plus"
              @click="openFollowUp()"
            >
              新增跟进
            </el-button>
          </div>
          <el-table v-loading="detail.loading" border size="small" :data="detail.followUps">
            <el-table-column label="联系时间" align="center" width="170">
              <template #default="scope">{{ parseTime(scope.row.contactTime) || '-' }}</template>
            </el-table-column>
            <el-table-column label="联系方式" align="center" prop="contactMethod" width="110" />
            <el-table-column label="联系结果" align="center" width="110">
              <template #default="scope">
                <dict-tag
                  v-if="scope.row.contactResult"
                  :options="talent_contact_result"
                  :value="scope.row.contactResult"
                />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="意向变化" prop="intentChange" width="140" show-overflow-tooltip />
            <el-table-column label="沟通摘要" prop="summary" min-width="200" show-overflow-tooltip />
            <el-table-column label="下次联系" align="center" width="170">
              <template #default="scope">{{ parseTime(scope.row.nextContactTime) || '-' }}</template>
            </el-table-column>
            <el-table-column label="跟进人" align="center" width="110">
              <template #default="scope">{{ scope.row.followerName || scope.row.followerId || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="130" fixed="right">
              <template #default="scope">
                <el-button
                  v-hasPermi="['talent:profile:edit']"
                  link
                  type="primary"
                  icon="Edit"
                  @click="openFollowUp(scope.row)"
                ></el-button>
                <el-button
                  v-hasPermi="['talent:profile:edit']"
                  link
                  type="danger"
                  icon="Delete"
                  @click="removeFollowUp(scope.row)"
                ></el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="detail.followTotal > 0"
            v-model:page="followQuery.pageNum"
            v-model:limit="followQuery.pageSize"
            :total="detail.followTotal"
            @pagination="loadFollowUps"
          />
        </el-tab-pane>

        <el-tab-pane label="简历版本" name="resume">
          <el-alert
            class="dialog-alert"
            type="info"
            :closable="false"
            show-icon
            title="简历的上传、指定当前版本、受控下载与解析复核统一在「简历中心」页面操作；此处只读展示版本与解析状态。"
          />
          <el-table v-loading="detail.loading" border size="small" :data="detail.resumes">
            <el-table-column label="版本号" align="center" prop="versionNo" width="80" />
            <el-table-column label="文件名" prop="originalName" min-width="200" show-overflow-tooltip />
            <el-table-column label="当前版本" align="center" width="90">
              <template #default="scope">{{ scope.row.currentFlag === '1' ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="解析状态" align="center" width="110">
              <template #default="scope">
                <dict-tag
                  v-if="scope.row.parseStatus"
                  :options="talent_resume_parse_status"
                  :value="scope.row.parseStatus"
                />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="复核状态" align="center" width="110">
              <template #default="scope">
                <dict-tag
                  v-if="scope.row.reviewStatus"
                  :options="talent_resume_review_status"
                  :value="scope.row.reviewStatus"
                />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="上传人" align="center" width="110">
              <template #default="scope">{{ scope.row.uploadedByName || scope.row.uploadedBy || '-' }}</template>
            </el-table-column>
            <el-table-column label="上传时间" align="center" width="170">
              <template #default="scope">{{ parseTime(scope.row.uploadedTime) || '-' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="应聘记录摘要" name="application">
          <el-alert
            class="dialog-alert"
            type="info"
            :closable="false"
            show-icon
            title="应聘记录摘要只读展示；阶段流转与邀约/报到登记请在「候选人跟进」页面操作。"
          />
          <el-table v-loading="detail.loading" border size="small" :data="detail.applications">
            <el-table-column label="应聘编号" prop="applicationNo" width="170" show-overflow-tooltip />
            <el-table-column label="岗位执行项ID" align="center" prop="jobId" width="110" />
            <el-table-column label="当前阶段" align="center" width="110">
              <template #default="scope">
                <dict-tag v-if="scope.row.currentStage" :options="recruit_candidate_stage" :value="scope.row.currentStage" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="应聘结果" align="center" width="110">
              <template #default="scope">
                <dict-tag
                  v-if="scope.row.currentStatus"
                  :options="recruit_application_result"
                  :value="scope.row.currentStatus"
                />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="招聘负责人" align="center" width="120">
              <template #default="scope">{{ scope.row.recruiterName || scope.row.recruiterId || '-' }}</template>
            </el-table-column>
            <el-table-column label="投递时间" align="center" width="170">
              <template #default="scope">{{ parseTime(scope.row.applyTime) || '-' }}</template>
            </el-table-column>
            <el-table-column label="进入阶段时间" align="center" width="170">
              <template #default="scope">{{ parseTime(scope.row.stageEnterTime) || '-' }}</template>
            </el-table-column>
            <el-table-column label="计划报到" align="center" prop="planArrivalDate" width="110" />
            <el-table-column label="实际到岗" align="center" prop="arrivalDate" width="110" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="变更历史" name="changes">
          <el-alert
            class="dialog-alert"
            type="info"
            :closable="false"
            show-icon
            title="关键字段变更留痕（设计 §8.12 / §8.5）：姓名、联系方式、状态、负责人等关键字段的变更都会写入明细，只追加不覆盖。"
          />
          <div v-loading="detail.changeLoading" class="timeline-wrap">
            <el-timeline v-if="detail.changes.length">
              <el-timeline-item
                v-for="change in detail.changes"
                :key="String(change.changeId)"
                :timestamp="parseTime(change.operateTime) || ''"
                placement="top"
                type="primary"
              >
                <div class="timeline-title">
                  {{ change.changeTypeLabel || change.changeType || '字段变更' }}
                  <el-tag size="small" type="info" class="timeline-tag">
                    操作人 {{ change.operatorId ?? '-' }}
                  </el-tag>
                </div>
                <div v-if="change.remark" class="timeline-comment">说明：{{ change.remark }}</div>
                <div class="snapshot-block">
                  <div class="snapshot-col">
                    <span class="snapshot-title">变更前</span>
                    <pre class="snapshot-text">{{ formatSnapshot(change.beforeJson) }}</pre>
                  </div>
                  <div class="snapshot-col">
                    <span class="snapshot-title">变更后</span>
                    <pre class="snapshot-text">{{ formatSnapshot(change.afterJson) }}</pre>
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
            <span v-else-if="!detail.changeLoading" class="empty-text">暂无关键字段变更记录</span>
          </div>
          <pagination
            v-show="detail.changeTotal > 0"
            v-model:page="changeQuery.pageNum"
            v-model:limit="changeQuery.pageSize"
            :total="detail.changeTotal"
            @pagination="loadChanges"
          />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detail.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 教育 / 工作 / 项目经历 新增编辑 -->
    <el-dialog v-model="expDialog.visible" :title="expDialog.title" width="720px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="来源类型为「简历解析」的内容在人工确认前不得直接覆盖正式经历；日期区间必须满足开始 ≤ 结束。"
      />
      <el-form ref="expFormRef" :model="expForm" :rules="expRules" label-width="110px">
        <template v-if="expDialog.type === 'education'">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="学校名称" prop="schoolName">
                <el-input v-model="expForm.schoolName" placeholder="请输入学校名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="专业" prop="major">
                <el-input v-model="expForm.major" placeholder="请输入专业" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="学历" prop="education">
                <el-select v-model="expForm.education" placeholder="请选择" clearable style="width: 100%">
                  <el-option v-for="dict in talent_education" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="学位" prop="degree">
                <el-select v-model="expForm.degree" placeholder="请选择" clearable style="width: 100%">
                  <el-option v-for="dict in talent_degree" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="是否全日制" prop="fullTimeFlag">
                <el-select v-model="expForm.fullTimeFlag" placeholder="请选择" clearable style="width: 100%">
                  <el-option label="是" value="1" />
                  <el-option label="否" value="0" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <template v-else-if="expDialog.type === 'work'">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="公司名称" prop="companyName">
                <el-input v-model="expForm.companyName" placeholder="请输入公司名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="部门名称" prop="departmentName">
                <el-input v-model="expForm.departmentName" placeholder="请输入部门名称" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="职位名称" prop="positionName">
                <el-input v-model="expForm.positionName" placeholder="请输入职位名称" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="所属行业" prop="industry">
                <el-input v-model="expForm.industry" placeholder="请输入行业" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="是否当前任职" prop="currentFlag">
                <el-select v-model="expForm.currentFlag" placeholder="请选择" clearable style="width: 100%">
                  <el-option label="是" value="1" />
                  <el-option label="否" value="0" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="职责描述" prop="responsibility">
            <el-input v-model="expForm.responsibility" type="textarea" :rows="2" maxlength="1000" show-word-limit />
          </el-form-item>
          <el-form-item label="业绩描述" prop="achievement">
            <el-input v-model="expForm.achievement" type="textarea" :rows="2" maxlength="1000" show-word-limit />
          </el-form-item>
          <el-form-item label="离职原因" prop="leaveReason">
            <el-input v-model="expForm.leaveReason" maxlength="200" show-word-limit placeholder="可为空" />
          </el-form-item>
        </template>

        <template v-else>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="项目名称" prop="projectName">
                <el-input v-model="expForm.projectName" placeholder="请输入项目名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="项目角色" prop="projectRole">
                <el-input v-model="expForm.projectRole" placeholder="请输入角色" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="项目说明" prop="description">
            <el-input v-model="expForm.description" type="textarea" :rows="2" maxlength="1000" show-word-limit />
          </el-form-item>
          <el-form-item label="职责" prop="responsibility">
            <el-input v-model="expForm.responsibility" type="textarea" :rows="2" maxlength="1000" show-word-limit />
          </el-form-item>
          <el-form-item label="成果" prop="achievement">
            <el-input v-model="expForm.achievement" type="textarea" :rows="2" maxlength="1000" show-word-limit />
          </el-form-item>
        </template>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="开始日期" prop="startDate">
              <el-date-picker
                v-model="expForm.startDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker
                v-model="expForm.endDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="当前任职可留空"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="expForm.sourceType" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="dict in talent_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="排序号" prop="sortNo">
          <el-input-number v-model="expForm.sortNo" :min="0" :step="1" style="width: 200px" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="expForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="expDialog.submitting" @click="submitExperience">确 定</el-button>
          <el-button @click="expDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 标签挂载（全量覆盖语义） -->
    <el-dialog v-model="tagDialog.visible" title="维护人才标签" width="680px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="保存为全量覆盖语义：取消勾选的标签会被移除。敏感标签（背调失败/健康/家庭/年龄等）不得作为普通可检索标签挂载（设计 §7.6.4、§8.15）。"
      />
      <el-checkbox-group v-model="tagDialog.tagIds" class="tag-selector">
        <el-checkbox v-for="tag in tagOptions" :key="String(tag.tagId)" :value="String(tag.tagId)">
          {{ tag.tagName }}
          <el-tag v-if="tag.sensitiveFlag === '1'" size="small" type="danger" class="tag-flag">敏感</el-tag>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="tagDialog.loading" @click="submitTags">确 定</el-button>
          <el-button @click="tagDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 跟进记录新增 / 编辑 -->
    <el-dialog v-model="followDialog.visible" :title="followDialog.title" width="680px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="跟进记录与招聘阶段历史分开；沟通摘要不得保存与招聘无关的高度敏感个人信息（服务端会校验提示）。"
      />
      <el-form ref="followFormRef" :model="followForm" :rules="followRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系时间" prop="contactTime">
              <el-date-picker
                v-model="followForm.contactTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系方式" prop="contactMethod">
              <el-input v-model="followForm.contactMethod" placeholder="如 phone/wechat/email" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系结果" prop="contactResult">
              <el-select v-model="followForm.contactResult" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="dict in talent_contact_result" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下次联系时间" prop="nextContactTime">
              <el-date-picker
                v-model="followForm.nextContactTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="可为空"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="意向变化" prop="intentChange">
          <el-input v-model="followForm.intentChange" maxlength="200" show-word-limit placeholder="可为空" />
        </el-form-item>
        <el-form-item label="沟通摘要" prop="summary">
          <el-input v-model="followForm.summary" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="跟进人ID" prop="followerId">
          <el-input v-model="followForm.followerId" placeholder="用户ID，留空由服务端取登录人" clearable>
            <template #append>
              <el-button icon="User" @click="openFollowerSelect" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="followForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="followDialog.submitting" @click="submitFollowUp">确 定</el-button>
          <el-button @click="followDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 归档：必填原因（后端 reason 为可选 query 参数，页面按业务要求必填） -->
    <el-dialog v-model="archiveDialog.visible" title="归档人才主档" width="560px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="归档后该人才默认不参与日常检索（检索时可勾选「包含已归档」）。归档是状态变更，不删除任何资料。"
      />
      <el-form ref="archiveFormRef" :model="archiveForm" :rules="archiveRules" label-width="90px">
        <el-form-item label="人才">
          <el-input :model-value="archiveDialog.row.name" disabled />
        </el-form-item>
        <el-form-item label="归档原因" prop="reason">
          <el-input
            v-model="archiveForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请填写归档原因（最长 500 字）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="archiveDialog.submitting" @click="submitArchive">确 定</el-button>
          <el-button @click="archiveDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 电话明文：必须先填用途，查看后提示已记录审计 -->
    <el-dialog v-model="phoneDialog.visible" title="查看电话明文" width="540px" append-to-body>
      <template v-if="phoneDialog.step === 'purpose'">
        <el-alert
          class="dialog-alert"
          type="warning"
          :closable="false"
          show-icon
          title="电话明文属敏感数据：必须填写查看用途，服务端会先写入审计（操作人、IP、时间与用途）再返发明文。"
        />
        <el-form ref="phoneFormRef" :model="phoneForm" :rules="phoneRules" label-width="80px">
          <el-form-item label="人才">
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
          <el-button
            v-if="phoneDialog.step === 'purpose'"
            type="primary"
            :loading="phoneDialog.loading"
            @click="submitPhoneView"
          >
            确认查看
          </el-button>
          <el-button v-else @click="phoneDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 用户选择：负责人 / 协助人 / 跟进人 -->
    <UserSelect ref="ownerSelectRef" :multiple="false" @confirm-call-back="handleOwnerSelected" />
    <UserSelect ref="assistantSelectRef" :multiple="true" @confirm-call-back="handleAssistantSelected" />
    <UserSelect ref="followerSelectRef" :multiple="false" @confirm-call-back="handleFollowerSelected" />

    <!-- 创建人才导出任务（普通台账 / 敏感台账） -->
    <el-dialog v-model="exportDialog.visible" title="创建人才导出" width="780px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="导出条件 = 当前页面检索条件（保证「搜得到的」与「导出的」口径一致）。导出为异步任务：创建后请在「导出任务」中按任务状态下载；导出文件存私有对象存储，系统只提供受控下载地址，不提供任何对象存储永久地址。"
      />
      <el-alert
        v-if="exportForm.exportType === 'sensitive'"
        class="dialog-alert"
        type="error"
        :closable="false"
        show-icon
        title="敏感台账（联系方式/薪资/附件访问地址）需要独立权限 talent:profile:phone-view，且用途必填；服务端会写审计，权限不足或用途为空会被拒绝。"
      />
      <el-form ref="exportFormRef" :model="exportForm" :rules="exportRules" label-width="110px">
        <el-form-item label="导出类型" prop="exportType">
          <el-radio-group v-model="exportForm.exportType" @change="handleExportTypeChange">
            <el-radio value="normal">普通台账（人才编号/脱敏姓名/岗位方向/学历/区域/状态/标签/负责人）</el-radio>
            <el-radio value="sensitive">敏感台账（在普通台账基础上追加联系方式/薪资等）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="导出字段">
          <el-checkbox-group v-model="exportForm.fields" class="field-selector">
            <el-checkbox
              v-for="item in exportFieldOptions"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
          <span class="form-tip">
            全部不勾选 = 使用该类型默认字段集（普通台账 8 列；敏感台账 = 普通字段 + 联系方式/邮箱/薪资/档案与简历受控访问地址）。
            字段清单为服务端白名单，非白名单字段会被拒绝。
          </span>
        </el-form-item>
        <el-form-item label="导出用途" prop="purpose">
          <el-input
            v-model="exportForm.purpose"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            :placeholder="exportForm.exportType === 'sensitive' ? '敏感台账必填（最长 255 字）' : '普通台账可选（最长 255 字）'"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="exportForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="exportDialog.loading" @click="submitExport">创建导出任务</el-button>
          <el-button @click="exportDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 导出任务列表（受控下载） -->
    <el-dialog v-model="exportTaskDialog.visible" title="导出任务" width="1080px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="导出为异步任务，请按任务状态判断是否可下载：仅「已完成」且未过期的任务可下载，过期任务会被服务端拒绝。下载必须填写用途并记录审计；敏感台账还需独立权限 talent:profile:phone-view。"
      />
      <el-form :inline="true" class="query-form">
        <el-form-item label="导出类型">
          <el-select v-model="exportTaskQuery.exportType" placeholder="全部" clearable style="width: 140px">
            <el-option label="普通台账" value="normal" />
            <el-option label="敏感台账" value="sensitive" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="exportTaskQuery.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="待处理" value="pending" />
            <el-option label="生成中" value="running" />
            <el-option label="已完成" value="success" />
            <el-option label="失败" value="failed" />
            <el-option label="已过期" value="expired" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建日期">
          <el-date-picker
            v-model="exportTaskDateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryExportTask">查询</el-button>
          <el-button icon="Refresh" @click="resetExportTaskQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="exportTaskDialog.loading" border size="small" :data="exportTaskList">
        <el-table-column label="任务编号" align="center" prop="taskNo" width="170" show-overflow-tooltip />
        <el-table-column label="导出类型" align="center" width="100">
          <template #default="scope">{{ scope.row.exportTypeText || exportTypeText(scope.row.exportType) }}</template>
        </el-table-column>
        <el-table-column label="任务状态" align="center" width="100">
          <template #default="scope">
            <el-tag :type="exportStatusTagType(scope.row.status)" size="small">
              {{ scope.row.statusText || exportStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="记录数" align="center" prop="recordCount" width="90" />
        <el-table-column label="用途" prop="purpose" min-width="150" show-overflow-tooltip />
        <el-table-column label="导出人" align="center" width="110">
          <template #default="scope">{{ scope.row.exportedByName || scope.row.exportedBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" width="165">
          <template #default="scope">{{ parseTime(scope.row.createTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="过期时间" align="center" width="165">
          <template #default="scope">{{ parseTime(scope.row.expireTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="失败原因" prop="failureReason" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="90" fixed="right">
          <template #default="scope">
            <el-tooltip content="受控下载（需填用途，记录审计）" placement="top">
              <el-button
                v-hasPermi="['talent:profile:export']"
                link
                type="primary"
                icon="Download"
                :disabled="scope.row.status !== 'success' || !scope.row.downloadApi"
                @click="openExportDownload(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="exportTaskTotal > 0"
        v-model:page="exportTaskQuery.pageNum"
        v-model:limit="exportTaskQuery.pageSize"
        :total="exportTaskTotal"
        @pagination="loadExportTask"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="exportTaskDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 导出文件受控下载：必填用途 -->
    <el-dialog v-model="exportDownloadDialog.visible" title="下载导出文件" width="560px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="导出文件属受控资料：服务端会校验用途非空、任务已完成且未过期，并写入审计后再流式返回；用途为空或任务过期会被拒绝并记录 denied 审计。"
      />
      <el-form ref="exportDownloadFormRef" :model="exportDownloadForm" :rules="exportDownloadRules" label-width="90px">
        <el-form-item label="任务编号">
          <el-input :model-value="exportDownloadDialog.row.taskNo" disabled />
        </el-form-item>
        <el-form-item label="导出类型">
          <el-input :model-value="exportDownloadDialog.row.exportTypeText || exportTypeText(exportDownloadDialog.row.exportType)" disabled />
        </el-form-item>
        <el-form-item label="文件名">
          <el-input :model-value="exportDownloadDialog.row.fileName" disabled />
        </el-form-item>
        <el-form-item label="用途" prop="purpose">
          <el-input
            v-model="exportDownloadForm.purpose"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="请填写下载用途（必填，最长 255 字）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="exportDownloadDialog.loading" @click="submitExportDownload">
            确认下载
          </el-button>
          <el-button @click="exportDownloadDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { listApplication } from '@/api/hrtalent/application';
import type { HrApplicationVO } from '@/api/hrtalent/application/types';
import { createTalentExport, downloadTalentExport, listTalentExport } from '@/api/hrtalent/export';
import { EXPORT_FIELD_OPTIONS } from '@/api/hrtalent/export/types';
import type {
  HrTalentExportCreateForm,
  HrTalentExportQuery,
  HrTalentExportTaskVO
} from '@/api/hrtalent/export/types';
import { listTag, listPool } from '@/api/hrtalent/pool';
import type { HrTalentPoolVO, HrTalentTagVO } from '@/api/hrtalent/pool/types';
import {
  addEducation,
  addFollowUp,
  addProfile,
  addProject,
  addWork,
  archiveProfile,
  delEducation,
  delFollowUp,
  delProject,
  delWork,
  getProfile,
  listEducation,
  listFollowUp,
  listProfile,
  listProfileChanges,
  listProfileTags,
  listProject,
  listWork,
  precheckProfile,
  updateEducation,
  updateFollowUp,
  updateProfile,
  updateProfileTags,
  updateProject,
  updateWork,
  viewTalentPhone
} from '@/api/hrtalent/profile';
import type {
  HrPhoneViewForm,
  HrTalentEducationForm,
  HrTalentEducationVO,
  HrTalentFollowUpForm,
  HrTalentFollowUpQuery,
  HrTalentFollowUpVO,
  HrTalentPrecheckVO,
  HrTalentProfileChangeVO,
  HrTalentProfileDetailVO,
  HrTalentProfileForm,
  HrTalentProfileQuery,
  HrTalentProfileVO,
  HrTalentProjectForm,
  HrTalentProjectVO,
  HrTalentWorkForm,
  HrTalentWorkVO
} from '@/api/hrtalent/profile/types';
import { listResume } from '@/api/hrtalent/resume';
import type { HrTalentResumeVO } from '@/api/hrtalent/resume/types';
import UserSelect from '@/components/UserSelect/index.vue';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';
import { saveBlob } from '@/utils/save';

defineOptions({ name: 'HrTalentProfile' });

const {
  talent_status,
  talent_visibility_type,
  talent_education,
  talent_degree,
  talent_source_type,
  talent_contact_result,
  talent_resume_parse_status,
  talent_resume_review_status,
  recruit_data_level,
  recruit_candidate_stage,
  recruit_application_result
} = toRefs<any>(
  useDict(
    'talent_status',
    'talent_visibility_type',
    'talent_education',
    'talent_degree',
    'talent_source_type',
    'talent_contact_result',
    'talent_resume_parse_status',
    'talent_resume_review_status',
    'recruit_data_level',
    'recruit_candidate_stage',
    'recruit_application_result'
  )
);

type ExperienceType = 'education' | 'work' | 'project';

const profileList = ref<HrTalentProfileVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const submitting = ref(false);
const prechecking = ref(false);
const profileFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();
const phoneFormRef = ref<ElFormInstance>();
const archiveFormRef = ref<ElFormInstance>();
const expFormRef = ref<ElFormInstance>();
const followFormRef = ref<ElFormInstance>();
const ownerSelectRef = ref<InstanceType<typeof UserSelect>>();
const assistantSelectRef = ref<InstanceType<typeof UserSelect>>();
const followerSelectRef = ref<InstanceType<typeof UserSelect>>();
const lastFollowRange = ref<[string, string] | null>(null);
const archiveTarget = ref<HrTalentProfileVO | null>(null);
const tagOptions = ref<HrTalentTagVO[]>([]);
const poolOptions = ref<HrTalentPoolVO[]>([]);
const selectedAssistants = ref<any[]>([]);
/** 是否已完成重复处置（复用主档或确认非同一人） */
const duplicateConfirmed = ref(false);

const initFormData: HrTalentProfileForm = {
  talentId: undefined,
  name: '',
  formerName: '',
  gender: undefined,
  birthDate: undefined,
  ageSnapshot: undefined,
  highestEducation: undefined,
  phone: '',
  backupPhone: '',
  email: '',
  otherContact: '',
  currentCity: '',
  expectedCity: '',
  currentCompany: '',
  currentPosition: '',
  expectedPosition: '',
  expectedSalaryMin: undefined,
  expectedSalaryMax: undefined,
  workYears: undefined,
  industry: '',
  ownerId: undefined,
  ownerDeptId: undefined,
  ownerDeptName: '',
  assistantIds: [],
  visibilityType: undefined,
  dataLevel: undefined,
  sourceType: undefined,
  sourceChannelId: undefined,
  statusReason: '',
  statusExpireDate: undefined,
  version: undefined,
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

const data = reactive<PageData<HrTalentProfileForm, HrTalentProfileQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    phoneTail4: undefined,
    talentNo: undefined,
    currentPosition: undefined,
    historyPosition: undefined,
    expectedPosition: undefined,
    tagIds: undefined,
    highestEducation: undefined,
    major: undefined,
    schoolName: undefined,
    currentCity: undefined,
    expectedCity: undefined,
    workYearsBegin: undefined,
    workYearsEnd: undefined,
    industry: undefined,
    currentCompany: undefined,
    sourceChannelId: undefined,
    ownerDeptId: undefined,
    ownerId: undefined,
    poolId: undefined,
    talentStatus: undefined,
    hasCurrentResume: undefined,
    resumeParseStatus: undefined,
    includeArchived: false
  },
  rules: {
    name: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
    email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
    expectedSalaryMax: [{ validator: validateSalaryRange, trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<HrTalentProfileForm, HrTalentProfileQuery>>(data);
const { dialog, resetForm, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: profileFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  pageSizeKey: 'pageSize',
  initialPageSize: 10,
  resetExtras: () => {
    lastFollowRange.value = null;
  },
  afterReset: () => handleQuery()
});

/** 性别中文兜底（后端未定义性别字典） */
const genderText = (code?: string) => {
  if (code === 'male') return '男';
  if (code === 'female') return '女';
  if (code === 'unknown') return '未知';
  return '-';
};
/** 完整度分档中文兜底 */
const completenessText = (level?: string) => {
  if (level === 'high') return '高';
  if (level === 'medium') return '中';
  if (level === 'low') return '低';
  return '-';
};
/** 是否标志展示（1/0） */
const flagText = (code?: string) => (code === '1' ? '是' : code === '0' ? '否' : '-');
/** 期望薪资展示 */
const salaryText = (min?: number, max?: number) => {
  if (min === undefined && max === undefined) {
    return '-';
  }
  return `${min ?? '-'} ~ ${max ?? '-'}`;
};
/** 协助人回显 */
const assistantText = computed(() => {
  if (!selectedAssistants.value.length) {
    return '';
  }
  return selectedAssistants.value.map(item => item.nickName || item.userName || item.userId).join('，');
});

const getList = async () => {
  await withLoading(async () => {
    queryParams.value.lastFollowTimeBegin = lastFollowRange.value?.[0] || undefined;
    queryParams.value.lastFollowTimeEnd = lastFollowRange.value?.[1] || undefined;
    const res = await listProfile(queryParams.value);
    profileList.value = res.data?.rows || [];
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
  selectedAssistants.value = [];
  duplicateConfirmed.value = false;
};

/** 加载标签字典与人才池下拉（检索与标签挂载共用） */
const loadOptions = async () => {
  try {
    const [tagRes, poolRes] = await Promise.all([
      listTag({ pageNum: 1, pageSize: 200, onlyEnabled: true }),
      listPool({ pageNum: 1, pageSize: 200 })
    ]);
    tagOptions.value = tagRes.data?.rows || [];
    poolOptions.value = poolRes.data?.rows || [];
  } catch {
    // 无权限或接口异常时保持空列表，拦截器已提示
    tagOptions.value = [];
    poolOptions.value = [];
  }
};

const handleAdd = () => {
  resetForm();
  selectedAssistants.value = [];
  duplicateConfirmed.value = false;
  showDialog('新增人才主档（先查重）');
};

const handleUpdate = async (row: Partial<HrTalentProfileVO>) => {
  resetForm();
  const res = await getProfile(row.talentId!);
  const detailRow = res.data || {};
  Object.assign(form.value, initFormData);
  Object.assign(form.value, {
    talentId: detailRow.talentId,
    name: detailRow.name,
    formerName: detailRow.formerName,
    gender: detailRow.gender,
    birthDate: detailRow.birthDate,
    ageSnapshot: detailRow.ageSnapshot,
    highestEducation: detailRow.highestEducation,
    // 明文不随列表/详情返回，编辑时留空表示不修改联系方式
    phone: '',
    backupPhone: '',
    email: '',
    otherContact: '',
    currentCity: detailRow.currentCity,
    expectedCity: detailRow.expectedCity,
    currentCompany: detailRow.currentCompany,
    currentPosition: detailRow.currentPosition,
    expectedPosition: detailRow.expectedPosition,
    expectedSalaryMin: detailRow.expectedSalaryMin,
    expectedSalaryMax: detailRow.expectedSalaryMax,
    workYears: detailRow.workYears,
    industry: detailRow.industry,
    ownerId: detailRow.ownerId,
    ownerDeptId: detailRow.ownerDeptId,
    ownerDeptName: detailRow.ownerDeptName,
    assistantIds: detailRow.assistantIds || [],
    visibilityType: detailRow.visibilityType,
    dataLevel: detailRow.dataLevel,
    sourceType: detailRow.sourceType,
    sourceChannelId: detailRow.sourceChannelId,
    statusReason: detailRow.statusReason,
    statusExpireDate: detailRow.statusExpireDate,
    version: detailRow.version,
    remark: detailRow.remark
  });
  selectedAssistants.value = (detailRow.assistantIds || []).map(id => ({ userId: id, nickName: String(id) }));
  showDialog('修改人才主档');
};

/* ------------------------------ 查重预检 ------------------------------ */

const precheckDialog = reactive<{
  visible: boolean;
  reuseTalentId: string;
  result: HrTalentPrecheckVO | null;
}>({ visible: false, reuseTalentId: '', result: null });

/** 预检结果按级别分组展示（强 / 中 / 弱） */
const precheckGroups = computed(() => {
  const result = precheckDialog.result;
  return [
    { level: 'strong', title: '强匹配（电话/邮箱哈希命中，禁止静默创建）', items: result?.strongMatches || [] },
    { level: 'medium', title: '中匹配（姓名 + 公司/学校/简历哈希）', items: result?.mediumMatches || [] },
    { level: 'weak', title: '弱匹配（姓名 + 期望岗位，仅提示）', items: result?.weakMatches || [] }
  ];
});

/**
 * 是否存在阻断创建（强/中匹配）的命中
 *
 * `POST /talent/profiles` 后端固定以 `duplicateAck=false` 调用入库前查重
 * （TalentProfileServiceImpl#create(bo, true, false, null)），因此命中强/中匹配时
 * 本页**无法**强制新建，只能复用已有主档或改走候选人接口。
 */
const blockingDuplicated = computed(
  () => precheckDialog.result?.strongDuplicated === true || precheckDialog.result?.mediumDuplicated === true
);

/** 执行查重预检 */
const doPrecheck = async (): Promise<HrTalentPrecheckVO | null> => {
  if (!form.value.name) {
    modal.msgWarning('请先填写姓名，姓名是查重的前置条件');
    return null;
  }
  prechecking.value = true;
  try {
    const res = await precheckProfile({
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

const runPrecheck = async () => {
  const result = await doPrecheck();
  if (!result) {
    return;
  }
  precheckDialog.result = result;
  precheckDialog.reuseTalentId = '';
  precheckDialog.visible = true;
};

/** 复用已有主档：本页不创建应聘记录，直接打开该主档详情 */
const confirmReuse = async () => {
  const talentId = precheckDialog.reuseTalentId;
  if (!talentId) {
    return;
  }
  precheckDialog.visible = false;
  cancel();
  await handleDetail({ talentId });
  modal.msgSuccess('已复用已有主档，未创建新档案');
};

/** 确认不是同一人，继续新建 */
const confirmDistinct = () => {
  duplicateConfirmed.value = true;
  precheckDialog.visible = false;
  modal.msgSuccess('已确认非同一人，可继续创建新档案');
};

/* ------------------------------ 新增 / 编辑提交 ------------------------------ */

const submitForm = () => {
  profileFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    // 新增前未做重复处置时自动预检：命中强/中匹配强制人工确认（§7.6.3）
    if (!form.value.talentId && !duplicateConfirmed.value) {
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
    submitting.value = true;
    try {
      const payload: HrTalentProfileForm = { ...form.value };
      // 编辑时空联系方式表示不修改（后端 null 不覆盖）
      if (payload.talentId && !payload.phone) {
        payload.phone = undefined;
      }
      if (payload.talentId && !payload.email) {
        payload.email = undefined;
      }
      if (payload.talentId) {
        await updateProfile(payload);
        modal.msgSuccess('修改成功');
      } else {
        await addProfile(payload);
        modal.msgSuccess('新增成功');
      }
      closeDialog();
      await getList();
    } finally {
      submitting.value = false;
    }
  });
};

/* ------------------------------ 详情（含经历 / 标签 / 跟进 / 简历 / 应聘摘要） ------------------------------ */

const detail = reactive<{
  visible: boolean;
  loading: boolean;
  tab: string;
  row: Partial<HrTalentProfileDetailVO>;
  educations: HrTalentEducationVO[];
  works: HrTalentWorkVO[];
  projects: HrTalentProjectVO[];
  tags: HrTalentTagVO[];
  followUps: HrTalentFollowUpVO[];
  followTotal: number;
  resumes: HrTalentResumeVO[];
  applications: HrApplicationVO[];
  changes: HrTalentProfileChangeVO[];
  changeTotal: number;
  changeLoading: boolean;
  /** 变更历史是否已加载（页签懒加载，避免每次打开详情都多打一次请求） */
  changeLoaded: boolean;
}>({
  visible: false,
  loading: false,
  tab: 'base',
  row: {},
  educations: [],
  works: [],
  projects: [],
  tags: [],
  followUps: [],
  followTotal: 0,
  resumes: [],
  applications: [],
  changes: [],
  changeTotal: 0,
  changeLoading: false,
  changeLoaded: false
});

const followQuery = reactive<HrTalentFollowUpQuery>({ pageNum: 1, pageSize: 5 });
/** 变更历史分页参数 */
const changeQuery = reactive({ pageNum: 1, pageSize: 10 });

const handleDetail = async (row: Partial<HrTalentProfileVO>) => {
  const talentId = row.talentId!;
  const res = await getProfile(talentId);
  detail.row = res.data || { ...row };
  detail.tab = 'base';
  detail.changes = [];
  detail.changeTotal = 0;
  detail.changeLoaded = false;
  changeQuery.pageNum = 1;
  detail.visible = true;
  await loadDetailSections(talentId);
};

/** 详情页签切换：变更历史按需加载 */
const handleDetailTabChange = (name: string | number) => {
  if (name === 'changes' && !detail.changeLoaded) {
    loadChanges();
  }
};

/** 查询人才关键字段变更历史（GET /talent/profiles/{id}/changes） */
const loadChanges = async () => {
  if (!detail.row.talentId) {
    return;
  }
  detail.changeLoading = true;
  try {
    const res = await listProfileChanges(detail.row.talentId, { ...changeQuery });
    detail.changes = res.data?.rows || [];
    detail.changeTotal = res.data?.total || 0;
    detail.changeLoaded = true;
  } catch {
    // 拦截器已提示错误，保持空列表
    detail.changes = [];
    detail.changeTotal = 0;
  } finally {
    detail.changeLoading = false;
  }
};

/** 快照渲染：后端下发 JSON 字符串，这里格式化展示；非 JSON 时原样输出 */
const formatSnapshot = (value?: string | null) => {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
};

/** 并行加载详情的各分区数据，单个分区失败不影响其他分区 */
const loadDetailSections = async (talentId: string | number) => {
  detail.loading = true;
  try {
    const [eduRes, workRes, projRes, tagRes, followRes, resumeRes, appRes] = await Promise.allSettled([
      listEducation(talentId),
      listWork(talentId),
      listProject(talentId),
      listProfileTags(talentId),
      listFollowUp(talentId, { ...followQuery }),
      listResume(talentId),
      listApplication({ pageNum: 1, pageSize: 20, talentId })
    ]);
    detail.educations = eduRes.status === 'fulfilled' ? eduRes.value.data || [] : [];
    detail.works = workRes.status === 'fulfilled' ? workRes.value.data || [] : [];
    detail.projects = projRes.status === 'fulfilled' ? projRes.value.data || [] : [];
    detail.tags = tagRes.status === 'fulfilled' ? tagRes.value.data || [] : [];
    if (followRes.status === 'fulfilled') {
      detail.followUps = followRes.value.data?.rows || [];
      detail.followTotal = followRes.value.data?.total || 0;
    } else {
      detail.followUps = [];
      detail.followTotal = 0;
    }
    detail.resumes = resumeRes.status === 'fulfilled' ? resumeRes.value.data || [] : [];
    detail.applications = appRes.status === 'fulfilled' ? appRes.value.data?.rows || [] : [];
  } finally {
    detail.loading = false;
  }
};

/** 按当前分页参数重新查询该人才的跟进记录 */
const loadFollowUps = async () => {
  if (!detail.row.talentId) {
    return;
  }
  const res = await listFollowUp(detail.row.talentId, { ...followQuery });
  detail.followUps = res.data?.rows || [];
  detail.followTotal = res.data?.total || 0;
};


/* ------------------------------ 经历 CRUD ------------------------------ */

const expDialog = reactive<{
  visible: boolean;
  title: string;
  type: ExperienceType;
  submitting: boolean;
}>({ visible: false, title: '', type: 'education', submitting: false });

/** 三类经历共用一个表单对象（字段并集，按类型只提交相关字段） */
const expForm = ref<Record<string, any>>({});

const expRules = computed<ElFormRules>(() => {
  const rules: ElFormRules = {};
  if (expDialog.type === 'education') {
    rules.schoolName = [{ required: true, message: '学校名称不能为空', trigger: 'blur' }];
  } else if (expDialog.type === 'work') {
    rules.companyName = [{ required: true, message: '公司名称不能为空', trigger: 'blur' }];
    rules.positionName = [{ required: true, message: '职位名称不能为空', trigger: 'blur' }];
  } else {
    rules.projectName = [{ required: true, message: '项目名称不能为空', trigger: 'blur' }];
  }
  rules.endDate = [
    {
      validator: (_rule: any, value: any, callback: any) => {
        if (value && expForm.value.startDate && value < expForm.value.startDate) {
          callback(new Error('结束日期不得早于开始日期'));
          return;
        }
        callback();
      },
      trigger: 'change'
    }
  ];
  return rules;
});

const EXP_TITLE: Record<ExperienceType, string> = {
  education: '教育经历',
  work: '工作经历',
  project: '项目经历'
};

const openExperience = (type: ExperienceType, row?: Record<string, any>) => {
  expDialog.type = type;
  expDialog.title = `${row ? '修改' : '新增'}${EXP_TITLE[type]}`;
  expForm.value = { ...row, sourceType: row?.sourceType || 'manual' };
  expDialog.visible = true;
};

const submitExperience = () => {
  expFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    const talentId = detail.row.talentId!;
    expDialog.submitting = true;
    try {
      if (expDialog.type === 'education') {
        const payload = expForm.value as HrTalentEducationForm;
        if (payload.educationId) {
          await updateEducation(talentId, payload);
        } else {
          await addEducation(talentId, payload);
        }
      } else if (expDialog.type === 'work') {
        const payload = expForm.value as HrTalentWorkForm;
        if (payload.workId) {
          await updateWork(talentId, payload);
        } else {
          await addWork(talentId, payload);
        }
      } else {
        const payload = expForm.value as HrTalentProjectForm;
        if (payload.projectId) {
          await updateProject(talentId, payload);
        } else {
          await addProject(talentId, payload);
        }
      }
      modal.msgSuccess('保存成功');
      expDialog.visible = false;
      await loadDetailSections(talentId);
      await getList();
    } finally {
      expDialog.submitting = false;
    }
  });
};

const removeExperience = async (type: ExperienceType, row: Record<string, any>) => {
  const talentId = detail.row.talentId!;
  try {
    await modal.confirm(`是否确认删除该${EXP_TITLE[type]}记录？删除为逻辑删除。`);
  } catch {
    return;
  }
  if (type === 'education') {
    await delEducation(talentId, row.educationId);
  } else if (type === 'work') {
    await delWork(talentId, row.workId);
  } else {
    await delProject(talentId, row.projectId);
  }
  modal.msgSuccess('删除成功');
  await loadDetailSections(talentId);
  await getList();
};

/* ------------------------------ 标签挂载 ------------------------------ */

const tagDialog = reactive<{ visible: boolean; loading: boolean; tagIds: string[] }>({
  visible: false,
  loading: false,
  tagIds: []
});

const openTagDialog = () => {
  tagDialog.tagIds = detail.tags.map(tag => String(tag.tagId));
  tagDialog.visible = true;
};

const submitTags = () => {
  tagDialog.loading = true;
  updateProfileTags(detail.row.talentId!, { tagIds: tagDialog.tagIds, sourceType: 'manual' })
    .then(async () => {
      modal.msgSuccess('标签已更新');
      tagDialog.visible = false;
      await loadDetailSections(detail.row.talentId!);
    })
    .finally(() => {
      tagDialog.loading = false;
    });
};

/* ------------------------------ 跟进 ------------------------------ */

const followDialog = reactive<{ visible: boolean; title: string; submitting: boolean }>({
  visible: false,
  title: '',
  submitting: false
});

const initFollowData: HrTalentFollowUpForm = {
  followId: undefined,
  talentId: undefined,
  contactTime: undefined,
  contactMethod: '',
  contactResult: undefined,
  intentChange: '',
  summary: '',
  nextContactTime: undefined,
  followerId: undefined,
  remark: ''
};

const followForm = ref<HrTalentFollowUpForm>({ ...initFollowData });
const followRules: ElFormRules = {
  contactTime: [{ required: true, message: '联系时间不能为空', trigger: 'change' }],
  summary: [{ required: true, message: '沟通摘要不能为空', trigger: 'blur' }]
};

const openFollowUp = (row?: Partial<HrTalentFollowUpVO>) => {
  followForm.value = { ...initFollowData, ...row };
  followDialog.title = row?.followId ? '修改跟进记录' : '新增跟进记录';
  followDialog.visible = true;
};

const submitFollowUp = () => {
  followFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    const talentId = detail.row.talentId!;
    followDialog.submitting = true;
    try {
      if (followForm.value.followId) {
        await updateFollowUp(talentId, followForm.value);
      } else {
        await addFollowUp(talentId, followForm.value);
      }
      modal.msgSuccess('保存成功');
      followDialog.visible = false;
      await loadFollowUps();
      await getList();
    } finally {
      followDialog.submitting = false;
    }
  });
};

const removeFollowUp = async (row: Partial<HrTalentFollowUpVO>) => {
  const talentId = detail.row.talentId!;
  try {
    await modal.confirm('是否确认删除该跟进记录？删除为逻辑删除，招聘阶段历史不受影响。');
  } catch {
    return;
  }
  await delFollowUp(talentId, row.followId!);
  modal.msgSuccess('删除成功');
  await loadFollowUps();
};

/* ------------------------------ 归档 ------------------------------ */

const archiveDialog = reactive<{ visible: boolean; submitting: boolean; row: Partial<HrTalentProfileVO> }>({
  visible: false,
  submitting: false,
  row: {}
});
const archiveForm = ref<{ reason: string }>({ reason: '' });
const archiveRules: ElFormRules = {
  reason: [{ required: true, message: '归档原因不能为空', trigger: 'blur' }]
};

const openArchive = (row?: Partial<HrTalentProfileVO>) => {
  const target = row || archiveTarget.value;
  if (!target?.talentId) {
    modal.msgWarning('请先选择一条人才记录');
    return;
  }
  archiveDialog.row = target;
  archiveForm.value.reason = '';
  archiveDialog.visible = true;
};

const submitArchive = () => {
  archiveFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    archiveDialog.submitting = true;
    try {
      await archiveProfile(archiveDialog.row.talentId!, archiveForm.value.reason);
      modal.msgSuccess('归档成功');
      archiveDialog.visible = false;
      archiveTarget.value = null;
      await getList();
    } finally {
      archiveDialog.submitting = false;
    }
  });
};

/* ------------------------------ 电话明文 ------------------------------ */

const phoneDialog = reactive<{
  visible: boolean;
  loading: boolean;
  step: 'purpose' | 'result';
  row: Partial<HrTalentProfileVO>;
  phone: string;
}>({ visible: false, loading: false, step: 'purpose', row: {}, phone: '' });

const phoneForm = ref<HrPhoneViewForm>({ purpose: '' });
const phoneRules: ElFormRules = {
  purpose: [{ required: true, message: '查看用途不能为空', trigger: 'blur' }]
};

const openPhoneDialog = (row: Partial<HrTalentProfileVO>) => {
  if (!row.talentId) {
    return;
  }
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
      const res = await viewTalentPhone(phoneDialog.row.talentId!, { purpose: phoneForm.value.purpose });
      phoneDialog.phone = res.data || '';
      phoneDialog.step = 'result';
      modal.msgSuccess('已获取电话明文，本次查看已记录审计');
    } finally {
      phoneDialog.loading = false;
    }
  });
};

/* ------------------------------ 表格行操作与用户选择 ------------------------------ */

const handleMore = (row: HrTalentProfileVO, command: string) => {
  archiveTarget.value = row;
  if (command === 'tags') {
    handleDetail(row).then(() => openTagDialog());
  } else if (command === 'archive') {
    openArchive(row);
  }
};

const openOwnerSelect = () => ownerSelectRef.value?.open();
const openAssistantSelect = () => assistantSelectRef.value?.open();
const openFollowerSelect = () => followerSelectRef.value?.open();

const handleOwnerSelected = (users: any[]) => {
  const user = users?.[0];
  if (user) {
    form.value.ownerId = user.userId;
  }
};

const handleAssistantSelected = (users: any[]) => {
  selectedAssistants.value = users || [];
  form.value.assistantIds = (users || []).map(item => item.userId);
};

const handleFollowerSelected = (users: any[]) => {
  const user = users?.[0];
  if (user) {
    followForm.value.followerId = user.userId;
  }
};

/* ------------------------------ 人才导出（§8.20，权限 talent:profile:export） ------------------------------ */

const exportFormRef = ref<ElFormInstance>();
const exportDownloadFormRef = ref<ElFormInstance>();

const exportDialog = reactive<{ visible: boolean; loading: boolean }>({ visible: false, loading: false });

const initExportForm: HrTalentExportCreateForm = {
  exportType: 'normal',
  filters: undefined,
  fields: [],
  purpose: '',
  remark: ''
};
const exportForm = ref<HrTalentExportCreateForm>({ ...initExportForm });

/** 当前导出类型下可选的字段白名单（普通台账不含敏感追加字段） */
const exportFieldOptions = computed(() =>
  exportForm.value.exportType === 'sensitive'
    ? [...EXPORT_FIELD_OPTIONS.normal, ...EXPORT_FIELD_OPTIONS.sensitiveExtra]
    : [...EXPORT_FIELD_OPTIONS.normal]
);

const exportRules: ElFormRules = {
  exportType: [{ required: true, message: '导出类型不能为空', trigger: 'change' }],
  purpose: [
    {
      validator: (_rule: any, value: any, callback: any) => {
        // 敏感台账用途由服务端强制；前端做非空校验以避免无谓请求
        if (exportForm.value.exportType === 'sensitive' && !value) {
          callback(new Error('敏感台账导出必须填写用途'));
          return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ]
};

/** 切换导出类型：清掉不属于该类型的字段选择，避免后端「不支持的导出字段」 */
const handleExportTypeChange = () => {
  const allowed = exportFieldOptions.value.map(item => item.value) as string[];
  exportForm.value.fields = (exportForm.value.fields || []).filter(field => allowed.includes(field));
};

/** 导出条件 = 当前检索条件（剔除分页参数，与 GET /talent/profiles 的 TalentProfileQueryBo 同构） */
const buildExportFilters = (): Record<string, any> => {
  const { pageNum, pageSize, ...filters } = queryParams.value as Record<string, any>;
  return filters;
};

const openExportDialog = () => {
  exportForm.value = { ...initExportForm, fields: [] };
  exportDialog.visible = true;
};

const submitExport = () => {
  exportFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    exportDialog.loading = true;
    try {
      const res = await createTalentExport({
        exportType: exportForm.value.exportType,
        filters: buildExportFilters(),
        fields: exportForm.value.fields && exportForm.value.fields.length ? exportForm.value.fields : undefined,
        purpose: exportForm.value.purpose || undefined,
        remark: exportForm.value.remark || undefined
      });
      modal.msgSuccess(`导出任务已创建（任务ID ${res.data}），可在「导出任务」中按状态下载`);
      exportDialog.visible = false;
      // 打开任务列表便于按状态下载（导出为异步任务，不假设可立即下载）
      await openExportTaskDialog();
    } finally {
      exportDialog.loading = false;
    }
  });
};

/* ---------------- 导出任务列表与受控下载 ---------------- */

const exportTaskDialog = reactive<{ visible: boolean; loading: boolean }>({ visible: false, loading: false });
const exportTaskList = ref<HrTalentExportTaskVO[]>([]);
const exportTaskTotal = ref(0);
const exportTaskDateRange = ref<[string, string] | null>(null);
const exportTaskQuery = reactive<HrTalentExportQuery>({
  pageNum: 1,
  pageSize: 10,
  exportType: undefined,
  status: undefined,
  exportedBy: undefined,
  createDateBegin: undefined,
  createDateEnd: undefined
});

/** 导出类型中文兜底（后端另有 exportTypeText） */
const exportTypeText = (code?: string) => {
  if (code === 'normal') return '普通台账';
  if (code === 'sensitive') return '敏感台账';
  return '-';
};
/** 导出状态中文兜底（后端另有 statusText） */
const exportStatusText = (code?: string) => {
  if (code === 'pending') return '待处理';
  if (code === 'running') return '生成中';
  if (code === 'success') return '已完成';
  if (code === 'failed') return '失败';
  if (code === 'expired') return '已过期';
  return '-';
};
/** 导出状态标签颜色 */
const exportStatusTagType = (code?: string) => {
  if (code === 'success') return 'success';
  if (code === 'failed') return 'danger';
  if (code === 'expired') return 'info';
  return 'warning';
};

const openExportTaskDialog = async () => {
  exportTaskDialog.visible = true;
  exportTaskDateRange.value = null;
  exportTaskQuery.pageNum = 1;
  await loadExportTask();
};

const loadExportTask = async () => {
  exportTaskDialog.loading = true;
  try {
    exportTaskQuery.createDateBegin = exportTaskDateRange.value?.[0] || undefined;
    exportTaskQuery.createDateEnd = exportTaskDateRange.value?.[1] || undefined;
    const res = await listTalentExport(exportTaskQuery);
    exportTaskList.value = res.data?.rows || [];
    exportTaskTotal.value = res.data?.total || 0;
  } catch {
    // 拦截器已提示错误，保持空列表
    exportTaskList.value = [];
    exportTaskTotal.value = 0;
  } finally {
    exportTaskDialog.loading = false;
  }
};

const queryExportTask = () => {
  exportTaskQuery.pageNum = 1;
  loadExportTask();
};

const resetExportTaskQuery = () => {
  exportTaskQuery.exportType = undefined;
  exportTaskQuery.status = undefined;
  exportTaskQuery.pageNum = 1;
  exportTaskDateRange.value = null;
  loadExportTask();
};

const exportDownloadDialog = reactive<{
  visible: boolean;
  loading: boolean;
  row: Partial<HrTalentExportTaskVO>;
}>({ visible: false, loading: false, row: {} });
const exportDownloadForm = ref<{ purpose: string }>({ purpose: '' });
const exportDownloadRules: ElFormRules = {
  purpose: [{ required: true, message: '下载用途不能为空', trigger: 'blur' }]
};

const openExportDownload = (row: HrTalentExportTaskVO) => {
  exportDownloadDialog.row = row;
  exportDownloadForm.value.purpose = '';
  exportDownloadDialog.visible = true;
};

/**
 * 解包二进制响应，并识别后端「HTTP 200 + JSON 体」的业务错误。
 *
 * 导出下载在 responseType=blob 下，业务异常（用途为空 / 无敏感权限 / 任务未完成或过期）
 * 会以 `application/json` 类型的 Blob 返回，必须解析出 msg 提示，**不能**当成文件保存。
 */
const resolveBlobResponse = async (resp: any): Promise<{ blob: Blob | null; errorMsg?: string }> => {
  const data = resp && resp.data !== undefined ? resp.data : resp;
  const blob = data instanceof Blob ? data : new Blob([data]);
  const contentType = blob.type || '';
  if (contentType.includes('application/json')) {
    const text = await blob.text();
    try {
      const payload = JSON.parse(text);
      return { blob: null, errorMsg: payload?.msg || '服务端拒绝了本次下载请求' };
    } catch {
      return { blob: null, errorMsg: text || '服务端拒绝了本次下载请求' };
    }
  }
  return { blob };
};

const submitExportDownload = () => {
  exportDownloadFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    exportDownloadDialog.loading = true;
    try {
      const row = exportDownloadDialog.row;
      const resp = await downloadTalentExport(row.taskId!, exportDownloadForm.value.purpose);
      const { blob, errorMsg } = await resolveBlobResponse(resp);
      if (!blob) {
        modal.msgError(errorMsg || '导出文件下载失败');
        return;
      }
      saveBlob(blob, row.fileName || `talent-ledger-${row.taskId}.xlsx`);
      modal.msgSuccess('已开始下载，本次操作已记录审计');
      exportDownloadDialog.visible = false;
    } finally {
      exportDownloadDialog.loading = false;
    }
  });
};

onMounted(() => {
  loadOptions();
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

.detail-tabs {
  :deep(.el-tabs__content) {
    max-height: 60vh;
    overflow: auto;
  }
}

.tab-toolbar {
  margin-bottom: 8px;
}

.range-sep {
  margin: 0 6px;
  color: var(--app-text-muted);
}

.completeness-text {
  font-size: 12px;
  color: var(--app-text-muted);
}

.precheck-radio {
  display: block;
}

.precheck-group {
  margin-bottom: 14px;
}

.precheck-group-title {
  margin-bottom: 6px;
  font-weight: 600;
}

.precheck-empty {
  padding: 6px 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.tag-wall {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-item {
  margin: 0;
}

.tag-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.tag-flag {
  margin-left: 4px;
}

.empty-text {
  font-size: 12px;
  color: var(--app-text-muted);
}

.phone-plain {
  font-weight: 600;
  letter-spacing: 1px;
}

/* 导出字段清单 */
.field-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
}

.form-tip {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-muted);
}

/* 变更历史时间线 */
.timeline-wrap {
  min-height: 80px;
  padding: 4px 8px;
}

.timeline-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.timeline-tag {
  font-weight: 400;
}

.timeline-comment {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

/* 变更快照：左右两栏对照展示 */
.snapshot-block {
  display: flex;
  gap: 12px;
  margin-top: 6px;
}

.snapshot-col {
  flex: 1 1 50%;
  min-width: 0;
}

.snapshot-title {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.snapshot-text {
  margin: 0;
  max-height: 200px;
  overflow: auto;
  padding: 8px;
  border-radius: 6px;
  background: var(--app-surface-bg);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
