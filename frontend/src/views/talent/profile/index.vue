<template>
  <div class="p-2 app-container talent-profile-page">
    <PageHeading title="人才管理" subtitle="档案、附件与业务记录，在同一个工作空间有序连接" module="talent" />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>人才检索</h3>
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
          <el-form-item label="手机后四位" prop="phoneTail4">
            <el-input
              v-model="queryParams.phoneTail4"
              placeholder="请输入手机号后四位"
              maxlength="4"
              clearable
              style="width: 180px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="归属区域" prop="regionCode">
            <el-select v-model="queryParams.regionCode" placeholder="可选，收窄筛选" clearable style="width: 180px">
              <el-option v-for="dict in tl_region" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="人才状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 180px">
              <el-option v-for="dict in tl_talent_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="学历" prop="education">
            <el-select v-model="queryParams.education" placeholder="请选择学历" clearable style="width: 180px">
              <el-option v-for="dict in tl_education" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="意向岗位" prop="position">
            <el-input v-model="queryParams.position" placeholder="请输入意向岗位" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="来源" prop="source">
            <el-select v-model="queryParams.source" placeholder="请选择来源" clearable style="width: 180px">
              <el-option v-for="dict in tl_source" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="联系日期">
            <el-date-picker
              v-model="contactDateRange"
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
            <span class="panel-kicker">Talent Dataset</span>
            <h3>人才档案</h3>
            <p>共 {{ total }} 条记录；手机号默认脱敏展示，查询范围由服务端数据权限决定。</p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['talent:profile:add']" type="primary" plain icon="Plus" @click="handleAdd">
              新增
            </el-button>
            <el-button
              v-hasPermi="['talent:profile:import']"
              type="success"
              plain
              icon="UploadFilled"
              @click="handleImport"
            >
              导入简历
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border class="data-table" :data="talentList">
        <el-table-column label="人才编号" align="center" prop="talentNo" width="150" />
        <el-table-column label="姓名" align="center" prop="name" width="110" show-overflow-tooltip />
        <el-table-column label="性别" align="center" width="80">
          <template #default="scope">
            <dict-tag :options="tl_gender" :value="scope.row.gender" />
          </template>
        </el-table-column>
        <el-table-column label="年龄" align="center" width="70">
          <template #default="scope">{{ scope.row.age ?? scope.row.ageOnly ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="学历" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="tl_education" :value="scope.row.education" />
          </template>
        </el-table-column>
        <el-table-column label="意向岗位" align="center" prop="position" show-overflow-tooltip />
        <el-table-column label="薪资范围" align="center" width="110">
          <template #default="scope">{{ scope.row.expectSalaryText || '-' }}</template>
        </el-table-column>
        <el-table-column label="手机号" align="center" width="150">
          <template #default="scope">
            <span>{{ scope.row.phoneMasked || '****' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="归属区域" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="tl_region" :value="scope.row.regionCode" />
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="tl_talent_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="来源" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="tl_source" :value="scope.row.source" />
          </template>
        </el-table-column>
        <el-table-column label="联系日期" align="center" width="120">
          <template #default="scope">{{ scope.row.contactDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建人" align="center" prop="createByName" width="110" />
        <el-table-column label="操作" width="260" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="详情" placement="top">
              <el-button
                v-hasPermi="['talent:profile:query']"
                link
                type="primary"
                icon="View"
                @click="handleDetail(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="查看完整手机号" placement="top">
              <el-button
                v-hasPermi="['talent:profile:phone']"
                link
                type="primary"
                icon="Phone"
                @click="handleFullPhone(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="联系记录" placement="top">
              <el-button
                v-hasPermi="['talent:profile:query']"
                link
                type="primary"
                icon="ChatDotRound"
                @click="handleContact(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="授权配置" placement="top">
              <el-button
                v-hasPermi="['talent:profile:grant']"
                link
                type="primary"
                icon="Key"
                @click="handleGrant(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="修改" placement="top">
              <el-button
                v-hasPermi="['talent:profile:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="归档" placement="top">
              <el-button
                v-hasPermi="['talent:profile:archive']"
                link
                type="primary"
                icon="FolderDelete"
                @click="handleArchive(scope.row)"
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="760px" append-to-body>
      <el-form ref="talentFormRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="请输入姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <el-select v-model="form.gender" placeholder="请选择性别" style="width: 100%">
                <el-option v-for="dict in tl_gender" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入手机号（编辑时留空表示不修改）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出生日期" prop="birthDate">
              <el-date-picker
                v-model="form.birthDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择出生日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仅识别年龄" prop="ageOnly">
              <el-input-number v-model="form.ageOnly" :min="0" :max="120" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学历" prop="education">
              <el-select v-model="form.education" placeholder="请选择学历" clearable style="width: 100%">
                <el-option v-for="dict in tl_education" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期望薪资下限" prop="expectSalaryMin">
              <el-input-number v-model="form.expectSalaryMin" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期望薪资上限" prop="expectSalaryMax">
              <el-input-number v-model="form.expectSalaryMax" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="意向岗位" prop="position">
              <el-input v-model="form.position" placeholder="请输入意向岗位" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系日期" prop="contactDate">
              <el-date-picker
                v-model="form.contactDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择联系日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="归属区域" prop="regionCode">
              <el-select v-model="form.regionCode" placeholder="请选择归属区域" style="width: 100%">
                <el-option v-for="dict in tl_region" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="人才状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择人才状态" style="width: 100%">
                <el-option v-for="dict in tl_talent_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="共享范围" prop="shareScope">
              <el-select v-model="form.shareScope" placeholder="请选择共享范围" style="width: 100%">
                <el-option v-for="item in shareScopeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源" prop="source">
              <el-select v-model="form.source" placeholder="请选择来源" clearable style="width: 100%">
                <el-option v-for="dict in tl_source" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 重复预检结果 -->
    <el-dialog v-model="preCheckDialog.visible" title="疑似重复人才" width="820px" append-to-body>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="检测到疑似重复档案，系统不会自动合并，请确认后决定是否继续入库。"
      />
      <el-table :data="preCheckList" border class="data-table mt-2">
        <el-table-column label="命中规则" align="center" prop="matchRule" width="160" />
        <el-table-column label="匹配分数" align="center" prop="matchScore" width="100" />
        <el-table-column label="库中人才编号" align="center" prop="matchedTalentNo" width="150" />
        <el-table-column label="库中姓名" align="center" prop="matchedName" width="110" />
        <el-table-column label="归属区域" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="tl_region" :value="scope.row.matchedRegionCode" />
          </template>
        </el-table-column>
        <el-table-column label="当前结论" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="tl_duplicate_conclusion" :value="scope.row.conclusion || 'PENDING'" />
          </template>
        </el-table-column>
      </el-table>
      <el-form label-width="100px" class="mt-2">
        <el-form-item label="确认说明">
          <el-input v-model="form.duplicateRemark" type="textarea" :rows="2" placeholder="请说明为何仍要入库" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmDuplicateAndSave">确认仍要入库</el-button>
          <el-button @click="preCheckDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 完整手机号弹窗：仅在弹窗内展示，关闭即清空，不写入本地存储 -->
    <el-dialog v-model="phoneDialog.visible" title="完整手机号" width="360px" append-to-body @closed="fullPhone = ''">
      <div class="full-phone-box">{{ fullPhone || '-' }}</div>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="查看完整手机号会写入敏感操作审计，请勿记录或外传。"
        class="mt-2"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="phoneDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawer.visible" title="人才档案详情" :size="760" append-to-body @closed="detail = null">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="2" border>
          <el-descriptions-item label="人才编号">{{ detail.talentNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ detail.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ detail.genderLabel || dictLabel(tl_gender, detail.gender) }}</el-descriptions-item>
          <el-descriptions-item label="年龄">{{ detail.age ?? detail.ageOnly ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detail.phoneMasked || '****' }}</el-descriptions-item>
          <el-descriptions-item label="学历">{{ detail.educationLabel || dictLabel(tl_education, detail.education) }}</el-descriptions-item>
          <el-descriptions-item label="意向岗位">{{ detail.position || '-' }}</el-descriptions-item>
          <el-descriptions-item label="薪资范围">{{ detail.expectSalaryText || '-' }}</el-descriptions-item>
          <el-descriptions-item label="归属区域">{{ detail.regionLabel || dictLabel(tl_region, detail.regionCode) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.statusLabel || dictLabel(tl_talent_status, detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="共享范围">{{ shareScopeLabel(detail.shareScope) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.sourceLabel || dictLabel(tl_source, detail.source) }}</el-descriptions-item>
          <el-descriptions-item label="联系日期">{{ detail.contactDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.createByName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">当前用户权限</el-divider>
        <div v-if="detail">
          <el-tag :type="detail.permissions?.view ? 'success' : 'info'" class="mr-2">
            查看 {{ detail.permissions?.view ? '允许' : '不允许' }}
          </el-tag>
          <el-tag :type="detail.permissions?.download ? 'success' : 'info'" class="mr-2">
            下载 {{ detail.permissions?.download ? '允许' : '不允许' }}
          </el-tag>
          <el-tag :type="detail.permissions?.viewFullPhone ? 'success' : 'info'">
            完整手机号 {{ detail.permissions?.viewFullPhone ? '允许' : '不允许' }}
          </el-tag>
        </div>

        <el-divider content-position="left">附件（受控下载，不展示存储地址）</el-divider>
        <el-table v-if="detail" :data="detail.attachments || []" border size="small">
          <el-table-column label="类型" align="center" width="120">
            <template #default="scope">
              <dict-tag :options="tl_attachment_type" :value="scope.row.attachmentType" />
            </template>
          </el-table-column>
          <el-table-column label="文件名" align="center" prop="originalName" show-overflow-tooltip />
          <el-table-column label="版本" align="center" prop="version" width="80" />
          <el-table-column label="当前版本" align="center" width="90">
            <template #default="scope">{{ scope.row.isCurrent === '1' ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="扫描状态" align="center" prop="scanStatusLabel" width="110" />
        </el-table>

        <el-divider content-position="left">联系记录</el-divider>
        <el-table v-if="detail" :data="detail.contacts || []" border size="small">
          <el-table-column label="联系时间" align="center" prop="contactTime" width="180" />
          <el-table-column label="联系人" align="center" prop="contactorName" width="120" />
          <el-table-column label="结果" align="center" width="110">
            <template #default="scope">
              <dict-tag :options="tl_contact_result" :value="scope.row.contactResult" />
            </template>
          </el-table-column>
          <el-table-column label="内容" align="left" prop="content" show-overflow-tooltip />
        </el-table>
      </div>
    </el-drawer>

    <!-- 联系记录弹窗 -->
    <el-dialog v-model="contactDialog.visible" title="联系记录" width="720px" append-to-body>
      <el-table v-loading="contactLoading" :data="contactList" border size="small">
        <el-table-column label="联系时间" align="center" prop="contactTime" width="180" />
        <el-table-column label="联系人" align="center" prop="contactorName" width="120" />
        <el-table-column label="结果" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="tl_contact_result" :value="scope.row.contactResult" />
          </template>
        </el-table-column>
        <el-table-column label="内容" align="left" prop="content" show-overflow-tooltip />
      </el-table>

      <el-divider content-position="left">新增跟进</el-divider>
      <el-form ref="contactFormRef" :model="contactForm" :rules="contactRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系时间" prop="contactTime">
              <el-date-picker
                v-model="contactForm.contactTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择联系时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系结果" prop="contactResult">
              <el-select v-model="contactForm.contactResult" placeholder="请选择联系结果" style="width: 100%">
                <el-option v-for="dict in tl_contact_result" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="内容" prop="content">
              <el-input v-model="contactForm.content" type="textarea" :rows="2" placeholder="请输入联系内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitContact">提 交</el-button>
          <el-button @click="contactDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 授权配置弹窗 -->
    <el-dialog v-model="grantDialog.visible" title="授权配置" width="820px" append-to-body>
      <el-table v-loading="grantLoading" :data="grantList" border size="small">
        <el-table-column label="主体类型" align="center" width="100">
          <template #default="scope">{{ scope.row.granteeType === 'ROLE' ? '角色' : '用户' }}</template>
        </el-table-column>
        <el-table-column label="主体ID" align="center" prop="granteeId" width="180" />
        <el-table-column label="权限" align="center" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.permission || '-' }}</template>
        </el-table-column>
        <el-table-column label="生效时间" align="center" prop="startTime" width="170" />
        <el-table-column label="失效时间" align="center" prop="endTime" width="170" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'info'">
              {{ scope.row.status === '0' ? '正常' : '已撤销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="90">
          <template #default="scope">
            <el-button
              v-hasPermi="['talent:profile:grant']"
              link
              type="danger"
              icon="Delete"
              :disabled="scope.row.status !== '0'"
              @click="handleRevokeGrant(scope.row)"
            ></el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">新增授权</el-divider>
      <el-form ref="grantFormRef" :model="grantForm" :rules="grantRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="主体类型" prop="granteeType">
              <el-radio-group v-model="grantForm.granteeType" @change="handleGranteeTypeChange">
                <el-radio value="USER">用户</el-radio>
                <el-radio value="ROLE">角色</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="被授权主体" prop="granteeId">
              <el-input v-model="granteeLabel" readonly placeholder="请选择被授权主体">
                <template #append>
                  <el-button icon="Search" @click="openGranteeSelect">选择</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="授权动作" prop="permissions">
              <el-checkbox-group v-model="grantForm.permissions">
                <el-checkbox v-for="item in grantPermissionOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效时间" prop="startTime">
              <el-date-picker
                v-model="grantForm.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="留空表示立即生效"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效时间" prop="endTime">
              <el-date-picker
                v-model="grantForm.endTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="留空表示长期，需审批"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="授权原因" prop="reason">
              <el-input v-model="grantForm.reason" type="textarea" :rows="2" placeholder="请输入授权原因（审计用）" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitGrant">新增授权</el-button>
          <el-button @click="grantDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 归档弹窗 -->
    <el-dialog v-model="archiveDialog.visible" title="归档人才档案" width="480px" append-to-body>
      <el-form ref="archiveFormRef" :model="archiveForm" label-width="90px">
        <el-form-item label="归档说明">
          <el-input v-model="archiveForm.remark" type="textarea" :rows="3" placeholder="请输入归档说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitArchive">确 定</el-button>
          <el-button @click="archiveDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 导入简历：第 1 步上传解析 / 第 2 步逐字段预览确认（来源与置信度均展示，不展示任何存储地址） -->
    <el-dialog
      v-model="importDialog.visible"
      title="导入简历"
      width="960px"
      append-to-body
      :close-on-click-modal="false"
      @closed="handleImportClosed"
    >
      <el-steps :active="importStep" align-center finish-status="success" class="import-steps">
        <el-step title="上传与解析" description="选择简历文件并本地提取" />
        <el-step title="预览确认" description="逐字段核对后建档" />
      </el-steps>

      <!-- 第 1 步：上传与解析；关闭自动上传，点按钮才触发 preview -->
      <div v-if="importStep === 1" v-loading="importParsing" element-loading-text="正在本地解析简历，请稍候">
        <el-upload
          ref="importUploadRef"
          drag
          :limit="1"
          :auto-upload="false"
          :accept="importAcceptExt"
          :file-list="importFileList"
          :on-change="handleImportFileChange"
          :on-exceed="handleImportExceed"
          :on-remove="handleImportFileRemove"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将简历拖到此处，或<em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">
              支持 pdf / doc / docx，单文件不超过 25MB；jpg / jpeg / png 可上传，但无法提取文字，需人工补全。
            </div>
          </template>
        </el-upload>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mt-2"
          title="解析全部在服务端进程内完成，不调用任何外部 AI/OCR 服务，简历数据不会离开服务器。"
        />
      </div>

      <!-- 第 2 步：预览确认 -->
      <div v-else v-loading="importSubmitting" element-loading-text="正在建档，请稍候">
        <el-alert
          v-if="importPreview && importPreview.textExtracted === false"
          type="warning"
          :closable="false"
          show-icon
          title="未能提取到简历正文文字，仅按文件名解析，请手工补全各字段。"
          class="mb-2"
        >
          <div v-for="(item, index) in importPreview.warnings || []" :key="index" class="import-warning-line">
            {{ item }}
          </div>
        </el-alert>
        <el-alert
          v-else-if="(importPreview?.warnings || []).length > 0"
          type="warning"
          :closable="false"
          show-icon
          class="mb-2"
        >
          <div v-for="(item, index) in importPreview?.warnings || []" :key="index" class="import-warning-line">
            {{ item }}
          </div>
        </el-alert>

        <el-table
          :data="importReviewRows"
          :row-class-name="importRowClassName"
          border
          size="small"
          class="data-table import-review-table"
        >
          <el-table-column label="字段" width="110">
            <template #default="scope">
              <span>{{ scope.row.label }}</span>
              <el-tag v-if="scope.row.warning" type="warning" size="small" effect="dark" class="ml-1">请核对</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="识别值" min-width="220">
            <template #default="scope">
              <el-select
                v-if="scope.row.edit === 'gender' || scope.row.edit === 'region' || scope.row.edit === 'education'"
                v-model="importForm[scope.row.edit]"
                :placeholder="scope.row.edit === 'region' ? '请选择归属区域' : '请选择'"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="dict in importDictOptions(scope.row.edit)"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
              <el-date-picker
                v-else-if="scope.row.edit === 'birthDate'"
                v-model="importForm.birthDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择出生日期"
                style="width: 100%"
              />
              <el-input-number
                v-else-if="scope.row.edit === 'expectSalaryMin'"
                v-model="importForm.expectSalaryMin"
                :min="0"
                controls-position="right"
                placeholder="期望薪资下限"
                style="width: 100%"
              />
              <el-input-number
                v-else-if="scope.row.edit === 'expectSalaryMax'"
                v-model="importForm.expectSalaryMax"
                :min="0"
                controls-position="right"
                placeholder="期望薪资上限"
                style="width: 100%"
              />
              <el-input-number
                v-else-if="scope.row.edit === 'ageOnly'"
                v-model="importForm.ageOnly"
                :min="0"
                :max="120"
                controls-position="right"
                placeholder="仅识别到年龄时填写"
                style="width: 100%"
              />
              <el-input
                v-else-if="scope.row.edit"
                v-model="importForm[scope.row.edit]"
                :placeholder="scope.row.label + '未识别，请手工补全'"
                clearable
              />
              <span v-else class="text-placeholder">-</span>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="100" align="center">
            <template #default="scope">{{ candidateSourceLabel(scope.row.source) }}</template>
          </el-table-column>
          <el-table-column label="置信度" width="160" align="center">
            <template #default="scope">
              <el-tag v-if="scope.row.confidence == null" type="info" size="small">未识别</el-tag>
              <el-tag v-else-if="isLowConfidence(scope.row.confidence)" type="warning" size="small" effect="dark">
                {{ formatConfidence(scope.row.confidence) }} · 置信度偏低，请人工核对
              </el-tag>
              <el-tag v-else type="success" size="small">{{ formatConfidence(scope.row.confidence) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提示" min-width="180" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.hint || '-' }}</template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">备注与补充（邮箱 / 经验会按「邮箱：xxx；经验：xxx」拼入备注）</el-divider>
        <el-form ref="importFormRef" :model="importForm" :rules="importRules" label-width="90px">
          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="备注" prop="remark">
                <el-input v-model="importForm.remark" type="textarea" :rows="2" placeholder="可补充说明，不会覆盖已识别内容" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <template v-if="importStep === 1">
            <el-button type="primary" icon="MagicStick" :loading="importParsing" @click="submitImportPreview">
              解析并预览
            </el-button>
            <el-button @click="importDialog.visible = false">取 消</el-button>
          </template>
          <template v-else>
            <el-button type="primary" :loading="importSubmitting" @click="submitImportConfirm">确认建档</el-button>
            <el-button @click="backToImportUpload">上一步</el-button>
            <el-button @click="importDialog.visible = false">取 消</el-button>
          </template>
        </div>
      </template>
    </el-dialog>

    <!-- 导入建档前的重复预检结果：命中后可选择仍然入库 -->
    <el-dialog v-model="importDuplicateDialog.visible" title="疑似重复人才" width="820px" append-to-body>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="按姓名 / 手机号 / 区域命中疑似重复档案，系统不会自动合并，请确认后决定是否继续导入建档。"
      />
      <el-table :data="importDuplicateList" border class="data-table mt-2">
        <el-table-column label="命中规则" align="center" prop="matchRule" width="160" />
        <el-table-column label="匹配分数" align="center" prop="matchScore" width="100" />
        <el-table-column label="库中人才编号" align="center" prop="matchedTalentNo" width="150" />
        <el-table-column label="库中姓名" align="center" prop="matchedName" width="110" />
        <el-table-column label="归属区域" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="tl_region" :value="scope.row.matchedRegionCode" />
          </template>
        </el-table-column>
        <el-table-column label="当前结论" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="tl_duplicate_conclusion" :value="scope.row.conclusion || 'PENDING'" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="importSubmitting" @click="confirmImportDuplicate">仍然导入建档</el-button>
          <el-button @click="importDuplicateDialog.visible = false">返回核对</el-button>
        </div>
      </template>
    </el-dialog>

    <user-select ref="userSelectRef" :multiple="false" @confirm-call-back="handleUserSelected" />
    <role-select ref="roleSelectRef" :multiple="false" @confirm-call-back="handleRoleSelected" />
  </div>
</template>

<script setup lang="ts">
import type {
  ResumeFieldCandidateVO,
  ResumeImportPreviewVO,
  TalentGrantVO,
  TalentContactVO,
  TalentDetailVO,
  TalentForm,
  TalentQuery,
  TalentVO
} from '@/api/talent/profile/types';
import type { TalentDuplicateVO } from '@/api/talent/duplicate/types';
import {
  addContact,
  addGrant,
  addTalent,
  archiveTalent,
  confirmResumeImport,
  getFullPhone,
  getTalent,
  listContact,
  listGrant,
  listTalent,
  preCheckTalent,
  previewResumeImport,
  revokeGrant,
  updateTalent
} from '@/api/talent/profile';
import RoleSelect from '@/components/RoleSelect/index.vue';
import UserSelect from '@/components/UserSelect/index.vue';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { selectDictLabel } from '@/utils/ruoyi';

defineOptions({ name: 'TalentProfile' });

const { tl_gender, tl_education, tl_talent_status, tl_region, tl_source, tl_attachment_type, tl_contact_result, tl_duplicate_conclusion } =
  toRefs<any>(
    useDict(
      'tl_gender',
      'tl_education',
      'tl_talent_status',
      'tl_region',
      'tl_source',
      'tl_attachment_type',
      'tl_contact_result',
      'tl_duplicate_conclusion'
    )
  );

const shareScopeOptions = [
  { value: 'REGION', label: '区域共享' },
  { value: 'GROUP', label: '全集团共享' },
  { value: 'GRANT_ONLY', label: '仅授权可见' }
];

const grantPermissionOptions = [
  { value: 'VIEW', label: '查看' },
  { value: 'DOWNLOAD', label: '下载' },
  { value: 'VIEW_FULL_PHONE', label: '查看完整手机号' }
];

const dictLabel = (options: any, value?: string) => (value ? selectDictLabel(options || [], value) : '-');
const shareScopeLabel = (value?: string) => shareScopeOptions.find(item => item.value === value)?.label || value || '-';

const talentList = ref<TalentVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const total = ref(0);
const submitting = ref(false);
const fullPhone = ref('');
const contactDateRange = ref<string[]>([]);

const queryFormRef = ref<ElFormInstance>();
const talentFormRef = ref<ElFormInstance>();
const contactFormRef = ref<ElFormInstance>();
const grantFormRef = ref<ElFormInstance>();
const archiveFormRef = ref<ElFormInstance>();
const userSelectRef = ref<InstanceType<typeof UserSelect>>();
const roleSelectRef = ref<InstanceType<typeof RoleSelect>>();

const initFormData: TalentForm = {
  talentId: undefined,
  name: '',
  gender: '0',
  birthDate: undefined,
  ageOnly: undefined,
  phone: '',
  education: undefined,
  expectSalaryMin: undefined,
  expectSalaryMax: undefined,
  position: '',
  contactDate: undefined,
  regionCode: undefined,
  status: 'NEW',
  shareScope: 'REGION',
  source: undefined,
  remark: '',
  duplicateConfirmed: false,
  duplicateRemark: ''
};

const data = reactive<PageData<TalentForm, TalentQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: '',
    talentNo: '',
    phoneTail4: '',
    regionCode: undefined,
    status: undefined,
    education: undefined,
    position: '',
    source: undefined,
    contactDateStart: undefined,
    contactDateEnd: undefined
  },
  rules: {
    name: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
    regionCode: [{ required: true, message: '归属区域不能为空', trigger: 'change' }],
    status: [{ required: true, message: '人才状态不能为空', trigger: 'change' }],
    shareScope: [{ required: true, message: '共享范围不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<TalentForm, TalentQuery>>(data);
const { dialog, resetForm, openDialog, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: talentFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  resetExtras: () => {
    contactDateRange.value = [];
    queryParams.value.contactDateStart = undefined;
    queryParams.value.contactDateEnd = undefined;
  },
  afterReset: () => handleQuery()
});

/** 重复预检 & 手机号弹窗 */
const preCheckDialog = reactive<DialogOption>({ visible: false, title: '疑似重复人才' });
const preCheckList = ref<TalentDuplicateVO[]>([]);
const phoneDialog = reactive<DialogOption>({ visible: false, title: '完整手机号' });

/** 详情抽屉 */
const drawer = reactive<DialogOption>({ visible: false, title: '人才档案详情' });
const detail = ref<TalentDetailVO | null>(null);
const detailLoading = ref(false);

/** 联系记录 */
const contactDialog = reactive<DialogOption>({ visible: false, title: '联系记录' });
const contactLoading = ref(false);
const contactList = ref<TalentContactVO[]>([]);
const contactForm = ref({ contactId: undefined, talentId: undefined, contactTime: '', contactResult: undefined, content: '' });
const contactRules = {
  contactTime: [{ required: true, message: '联系时间不能为空', trigger: 'change' }],
  contactResult: [{ required: true, message: '联系结果不能为空', trigger: 'change' }]
};

/** 授权配置 */
const grantDialog = reactive<DialogOption>({ visible: false, title: '授权配置' });
const grantLoading = ref(false);
const grantList = ref<TalentGrantVO[]>([]);
const granteeLabel = ref('');
const grantForm = ref({
  grantId: undefined,
  talentId: undefined,
  granteeType: 'USER',
  granteeId: undefined,
  permissions: ['VIEW'] as string[],
  startTime: '',
  endTime: '',
  reason: ''
});
const grantRules = {
  granteeType: [{ required: true, message: '主体类型不能为空', trigger: 'change' }],
  granteeId: [{ required: true, message: '被授权主体不能为空', trigger: 'change' }],
  permissions: [{ required: true, message: '至少选择一个授权动作', trigger: 'change' }]
};

/** 归档 */
const archiveDialog = reactive<DialogOption>({ visible: false, title: '归档人才档案' });
const archiveForm = ref<{ talentId: string | number | undefined; remark: string }>({ talentId: undefined, remark: '' });

/** ==================== 导入简历（两步向导） ==================== */

/** 允许的扩展名，与后端 TalentConstants.ALLOWED_EXT 严格一致；图片类型可传但无正文可提取 */
const importAllowedExt = ['pdf', 'docx', 'doc', 'jpg', 'jpeg', 'png'];
const importAcceptExt = importAllowedExt.map(ext => '.' + ext).join(',');
const importMaxFileSize = 25 * 1024 * 1024;
/** 低于该置信度视为低置信度，必须提示人工核对 */
const importLowConfidence = 0.8;

/** 预览表格行：固定顺序，未识别字段也占一行，保证人工可见可补 */
interface ImportReviewRow {
  /** 与 candidates[].field 一致：name/gender/education/birthDate/phone/position/regionCode/expectSalaryMin/expectSalaryMax/ageOnly/email/experienceText */
  field: string;
  label: string;
  edit?: ImportEditableKey;
  source?: string;
  confidence?: number;
  hint?: string;
  warning?: boolean;
}

/** 弹窗内可直接编辑的主档字段（其余候选仅展示） */
type ImportEditableKey =
  | 'name'
  | 'gender'
  | 'phone'
  | 'birthDate'
  | 'education'
  | 'position'
  | 'regionCode'
  | 'expectSalaryMin'
  | 'expectSalaryMax'
  | 'ageOnly'
  | 'remark';

/** 表单字段 -> 后端候选 field 的映射 */
const importFieldToCandidate: Record<ImportEditableKey, string> = {
  name: 'name',
  gender: 'gender',
  phone: 'phone',
  birthDate: 'birthDate',
  education: 'education',
  position: 'position',
  regionCode: 'regionCode',
  expectSalaryMin: 'expectSalaryMin',
  expectSalaryMax: 'expectSalaryMax',
  ageOnly: 'ageOnly',
  remark: 'remark'
};

/** 预览表格行定义（顺序固定，便于人工逐条核对） */
const importRowDefs: ImportReviewRow[] = [
  { field: 'name', label: '姓名', edit: 'name' },
  { field: 'gender', label: '性别', edit: 'gender' },
  { field: 'education', label: '学历', edit: 'education' },
  { field: 'birthDate', label: '出生日期', edit: 'birthDate' },
  { field: 'ageOnly', label: '仅识别年龄', edit: 'ageOnly' },
  { field: 'phone', label: '手机号', edit: 'phone' },
  { field: 'position', label: '意向岗位', edit: 'position' },
  { field: 'regionCode', label: '归属区域', edit: 'regionCode' },
  { field: 'expectSalaryMin', label: '期望薪资下限', edit: 'expectSalaryMin' },
  { field: 'expectSalaryMax', label: '期望薪资上限', edit: 'expectSalaryMax' },
  { field: 'email', label: '邮箱（并入备注）' },
  { field: 'experienceText', label: '经验（并入备注）' },
  { field: 'contactDate', label: '联系日期（服务端）' }
];

/** 导入弹窗表单：出生日期由 date-picker 承载，解析值先落 importForm 再提交 */
const initImportForm = (): TalentForm => ({
  name: '',
  gender: '0',
  phone: '',
  birthDate: undefined,
  ageOnly: undefined,
  education: undefined,
  position: '',
  regionCode: undefined,
  expectSalaryMin: undefined,
  expectSalaryMax: undefined,
  status: 'NEW',
  shareScope: 'REGION',
  remark: ''
});

const importDialog = reactive<DialogOption>({ visible: false, title: '导入简历' });
const importStep = ref(1);
const importPreview = ref<ResumeImportPreviewVO | null>(null);
const importParsing = ref(false);
const importSubmitting = ref(false);
const importFormRef = ref<ElFormInstance>();
const importUploadRef = ref<ElUploadInstance>();
const importFileList = ref<any[]>([]);
const importForm = ref<TalentForm>(initImportForm());
/** 导入确认阶段的重复预检结果：命中时先让用户确认再继续建档 */
const importDuplicateDialog = reactive<DialogOption>({ visible: false, title: '疑似重复人才' });
const importDuplicateList = ref<TalentDuplicateVO[]>([]);
const importRules = {
  name: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
  gender: [{ required: true, message: '性别不能为空', trigger: 'change' }],
  regionCode: [{ required: true, message: '归属区域不能为空', trigger: 'change' }]
};

/** 候选字段索引：同名取第一条（后端已按优先级排序） */
const importCandidateMap = computed(() => {
  const map: Record<string, any> = {};
  (importPreview.value?.candidates || []).forEach(item => {
    if (item?.field && !map[item.field]) {
      map[item.field] = item;
    }
  });
  return map;
});

/** 候选字段 -> 弹窗表单字段反查，用于判断表单值是否回填失败 */
const importCandidateToControl = computed(() => {
  const map: Record<string, ImportEditableKey> = {};
  (Object.keys(importFieldToCandidate) as ImportEditableKey[]).forEach(key => {
    map[importFieldToCandidate[key]] = key;
  });
  return map;
});

/** 逐字段预览行：展示来源、置信度与低置信度提示 */
const importReviewRows = computed<ImportReviewRow[]>(() => {
  const map = importCandidateMap.value;
  return importRowDefs.map(def => {
    const candidate = map[def.field];
    let warning = candidate?.confidence != null && candidate.confidence < importLowConfidence;
    // 解析值未能回填到表单（例如日期格式异常）时同样提示人工核对
    if (def.edit && candidate?.value) {
      const control = importCandidateToControl.value[def.field];
      if (control && !importForm.value[control]) {
        warning = true;
      }
    }
    return {
      ...def,
      source: candidate?.source,
      confidence: candidate?.confidence,
      hint: candidate?.hint,
      warning
    };
  });
});

const candidateSourceLabel = (source?: string) => {
  if (source === 'FILENAME') return '文件名';
  if (source === 'TEXT') return '正文';
  if (source === 'DEFAULT') return '默认';
  return '-';
};

const isLowConfidence = (confidence?: number) => confidence != null && confidence < importLowConfidence;

/** 低置信度行整行高亮，便于人工快速定位 */
const importRowClassName = ({ row }: { row: ImportReviewRow }) => (row.warning ? 'is-low-confidence' : '');

const formatConfidence = (confidence?: number) => (confidence == null ? '-' : `${Math.round(confidence * 100)}%`);

/** 弹窗内下拉使用的字典（区域/性别/学历） */
const importDictOptions = (key: string): any[] => {
  if (key === 'region') return tl_region.value || [];
  if (key === 'gender') return tl_gender.value || [];
  if (key === 'education') return tl_education.value || [];
  return [];
};

/** 把候选值写入表单，作为提交时的真实值来源 */
const fillImportForm = (candidates: ResumeFieldCandidateVO[]) => {
  const map: Record<string, string> = {};
  candidates.forEach(item => {
    if (item?.field && map[item.field] === undefined) {
      map[item.field] = item.value ?? '';
    }
  });
  const ageOnly = Number(map.ageOnly);
  Object.assign(importForm.value, {
    ...initImportForm(),
    name: map.name || '',
    gender: map.gender || '0',
    phone: map.phone || '',
    birthDate: map.birthDate || undefined,
    ageOnly: Number.isFinite(ageOnly) && map.ageOnly ? ageOnly : undefined,
    education: map.education || undefined,
    position: map.position || '',
    regionCode: map.regionCode || undefined,
    expectSalaryMin: map.expectSalaryMin ? Number(map.expectSalaryMin) : undefined,
    expectSalaryMax: map.expectSalaryMax ? Number(map.expectSalaryMax) : undefined
  });
};

/** 重置导入弹窗 */
const resetImportDialog = () => {
  importStep.value = 1;
  importPreview.value = null;
  importForm.value = initImportForm();
  importFileList.value = [];
  importDuplicateList.value = [];
  importDuplicateDialog.visible = false;
  importUploadRef.value?.clearFiles();
  importFormRef.value?.clearValidate?.();
};

const handleImport = () => {
  resetImportDialog();
  importDialog.visible = true;
};

const handleImportClosed = () => {
  importParsing.value = false;
  importSubmitting.value = false;
  resetImportDialog();
};

const handleImportFileChange = (file: UploadFile) => {
  const raw = (file as any).raw as File | undefined;
  const name = raw?.name || file.name || '';
  const ext = name.split('.').pop()?.toLowerCase() || '';
  if (!importAllowedExt.includes(ext)) {
    modal.msgError(`文件格式不正确，仅允许 ${importAllowedExt.join('/')}！`);
    importFileList.value = [];
    importUploadRef.value?.clearFiles();
    return;
  }
  if ((raw?.size || 0) > importMaxFileSize) {
    modal.msgError('文件大小不能超过 25MB！');
    importFileList.value = [];
    importUploadRef.value?.clearFiles();
    return;
  }
  // 手动上传：只保留最后选择的文件
  importFileList.value = [file];
};

const handleImportFileRemove = () => {
  importFileList.value = [];
};

const handleImportExceed = () => {
  importFileList.value = [];
  importUploadRef.value?.clearFiles();
  modal.msgError('一次只能上传一个文件，请重新选择');
};

/** 第 1 步：上传并解析 */
const submitImportPreview = async () => {
  const file = importFileList.value[0]?.raw as File | undefined;
  if (!file) {
    modal.msgError('请先选择要导入的简历文件');
    return;
  }
  importParsing.value = true;
  try {
    const res = await previewResumeImport(file);
    const preview = res.data || {};
    importPreview.value = preview;
    fillImportForm(preview.candidates || []);
    importStep.value = 2;
  } catch (error: any) {
    // 请求拦截器已提示 HTTP 错误；业务错误兜底提示，避免静默失败
    if (!error?.isHandled && error?.message) {
      modal.msgError(error.message);
    }
  } finally {
    importParsing.value = false;
  }
};

const backToImportUpload = () => {
  importStep.value = 1;
};

/**
 * 提交前把邮箱 / 经验拼入备注（非主档字段）。
 * 备注中已有用户输入时用中文分号追加，不覆盖用户内容。
 */
const buildImportTalent = (): TalentForm => {
  const map = importCandidateMap.value;
  const extras: string[] = [];
  if (map.email?.value) extras.push(`邮箱：${map.email.value}`);
  if (map.experienceText?.value) extras.push(`经验：${map.experienceText.value}`);
  const userRemark = (importForm.value.remark || '').trim();
  let remark = userRemark;
  if (extras.length > 0) {
    remark = remark ? `${remark}；${extras.join('；')}` : extras.join('；');
  }
  const form = importForm.value;
  return {
    name: form.name,
    gender: form.gender,
    phone: form.phone,
    birthDate: form.birthDate,
    ageOnly: form.ageOnly,
    education: form.education,
    position: form.position,
    regionCode: form.regionCode,
    expectSalaryMin: form.expectSalaryMin,
    expectSalaryMax: form.expectSalaryMax,
    status: form.status,
    shareScope: form.shareScope,
    remark,
    duplicateConfirmed: true
  };
};

/** 第 2 步：以用户确认后的表单值确认建档 */
const submitImportConfirm = () => {
  importFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    const token = importPreview.value?.importToken;
    if (!token) {
      modal.msgError('导入凭证已失效，请重新上传简历');
      return;
    }
    // 与新增一致：先做重复预检，命中则让用户显式确认，避免后端建档被拦截后还要重新上传
    const preCheckRes = await preCheckTalent(buildImportTalent());
    const hits = preCheckRes.data || [];
    if (hits.length > 0) {
      importDuplicateList.value = hits;
      importDuplicateDialog.visible = true;
      return;
    }
    await doImportConfirm(false);
  });
};

/** 命中重复后由用户确认继续入库 */
const confirmImportDuplicate = async () => {
  importDuplicateDialog.visible = false;
  await doImportConfirm(true);
};

const doImportConfirm = async (duplicateConfirmed: boolean) => {
  const token = importPreview.value?.importToken;
  if (!token) {
    modal.msgError('导入凭证已失效，请重新上传简历');
    return;
  }
  importSubmitting.value = true;
  try {
    const res = await confirmResumeImport({
      importToken: token,
      talent: { ...buildImportTalent(), duplicateConfirmed }
    });
    const [talentId] = res.data || [];
    importDialog.visible = false;
    modal.msgSuccess(talentId ? `建档成功（人才ID：${talentId}）` : '建档成功');
    await getList();
  } finally {
    importSubmitting.value = false;
  }
};

/** 查询人才档案列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listTalent(queryParams.value);
    talentList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  queryParams.value.contactDateStart = contactDateRange.value?.[0];
  queryParams.value.contactDateEnd = contactDateRange.value?.[1];
  getList();
};

const cancel = () => {
  closeDialog();
  resetForm();
};

const handleAdd = () => {
  openDialog('新增人才档案');
};

const handleUpdate = async (row: TalentVO) => {
  resetForm();
  if (!row.talentId) return;
  const res = await getTalent(row.talentId);
  const d: TalentDetailVO = res.data || {};
  // 只回填可编辑字段；phone 留空表示不修改，避免把详情结构整体回传给编辑接口
  Object.assign(form.value, {
    talentId: d.talentId,
    name: d.name,
    gender: d.gender,
    birthDate: d.birthDate,
    ageOnly: d.ageOnly,
    ageSourceDate: d.ageSourceDate,
    phone: '',
    education: d.education,
    expectSalaryMin: d.expectSalaryMin,
    expectSalaryMax: d.expectSalaryMax,
    position: d.position,
    contactDate: d.contactDate,
    regionCode: d.regionCode,
    status: d.status,
    shareScope: d.shareScope,
    source: d.source,
    remark: d.remark,
    duplicateConfirmed: false,
    duplicateRemark: ''
  });
  showDialog('修改人才档案');
};

/** 提交：新增前做重复预检，命中则先弹确认 */
const submitForm = () => {
  talentFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    if (!form.value.talentId) {
      const res = await preCheckTalent(form.value);
      preCheckList.value = res.data || [];
      if (preCheckList.value.length > 0) {
        preCheckDialog.visible = true;
        return;
      }
    }
    await save();
  });
};

const confirmDuplicateAndSave = async () => {
  form.value.duplicateConfirmed = true;
  preCheckDialog.visible = false;
  await save();
};

const save = async () => {
  submitting.value = true;
  try {
    form.value.talentId ? await updateTalent(form.value) : await addTalent(form.value);
    modal.msgSuccess('操作成功');
    closeDialog();
    await getList();
  } finally {
    submitting.value = false;
  }
};

/** 完整手机号：仅弹窗展示，关闭即清空 */
const handleFullPhone = async (row: TalentVO) => {
  if (!row.talentId) return;
  const res = await getFullPhone(row.talentId);
  fullPhone.value = res.data || '';
  phoneDialog.visible = true;
};

/** 详情 */
const handleDetail = async (row: TalentVO) => {
  if (!row.talentId) return;
  drawer.visible = true;
  detailLoading.value = true;
  try {
    const res = await getTalent(row.talentId);
    detail.value = res.data;
  } finally {
    detailLoading.value = false;
  }
};

/** 联系记录 */
const handleContact = async (row: TalentVO) => {
  if (!row.talentId) return;
  contactDialog.visible = true;
  contactForm.value = { contactId: undefined, talentId: row.talentId, contactTime: '', contactResult: undefined, content: '' };
  await loadContacts(row.talentId);
};

const loadContacts = async (talentId: string | number) => {
  contactLoading.value = true;
  try {
    const res = await listContact(talentId);
    contactList.value = res.data || [];
  } finally {
    contactLoading.value = false;
  }
};

const submitContact = () => {
  contactFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    await addContact(contactForm.value);
    modal.msgSuccess('已记录联系跟进');
    if (contactForm.value.talentId) {
      await loadContacts(contactForm.value.talentId);
    }
    contactForm.value = {
      contactId: undefined,
      talentId: contactForm.value.talentId,
      contactTime: '',
      contactResult: undefined,
      content: ''
    };
  });
};

/** 授权配置 */
const handleGrant = async (row: TalentVO) => {
  if (!row.talentId) return;
  grantDialog.visible = true;
  resetGrantForm(row.talentId);
  await loadGrants(row.talentId);
};

const resetGrantForm = (talentId?: string | number) => {
  grantForm.value = {
    grantId: undefined,
    talentId,
    granteeType: 'USER',
    granteeId: undefined,
    permissions: ['VIEW'],
    startTime: '',
    endTime: '',
    reason: ''
  };
  granteeLabel.value = '';
};

const loadGrants = async (talentId: string | number) => {
  grantLoading.value = true;
  try {
    const res = await listGrant(talentId);
    grantList.value = res.data || [];
  } finally {
    grantLoading.value = false;
  }
};

const handleGranteeTypeChange = () => {
  grantForm.value.granteeId = undefined;
  granteeLabel.value = '';
};

const openGranteeSelect = () => {
  if (grantForm.value.granteeType === 'ROLE') {
    roleSelectRef.value?.open();
  } else {
    userSelectRef.value?.open();
  }
};

const handleUserSelected = (users: any[]) => {
  const user = users?.[0];
  if (!user) return;
  grantForm.value.granteeId = user.userId;
  granteeLabel.value = `${user.nickName || user.userName}（${user.userId}）`;
};

const handleRoleSelected = (roles: any[]) => {
  const role = roles?.[0];
  if (!role) return;
  grantForm.value.granteeId = role.roleId;
  granteeLabel.value = `${role.roleName}（${role.roleId}）`;
};

const submitGrant = () => {
  grantFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    await addGrant({
      talentId: grantForm.value.talentId,
      granteeType: grantForm.value.granteeType,
      granteeId: grantForm.value.granteeId,
      permissions: grantForm.value.permissions,
      startTime: grantForm.value.startTime || undefined,
      endTime: grantForm.value.endTime || undefined,
      reason: grantForm.value.reason
    });
    modal.msgSuccess('授权已创建');
    const talentId = grantForm.value.talentId;
    resetGrantForm(talentId);
    if (talentId) {
      await loadGrants(talentId);
    }
  });
};

const handleRevokeGrant = async (row: TalentGrantVO) => {
  await modal.confirm('是否确认撤销该条授权？');
  if (!row.grantId) return;
  await revokeGrant(row.grantId);
  modal.msgSuccess('已撤销');
  if (grantForm.value.talentId) {
    await loadGrants(grantForm.value.talentId);
  }
};

/** 归档 */
const handleArchive = (row: TalentVO) => {
  if (!row.talentId) return;
  archiveForm.value = { talentId: row.talentId, remark: '' };
  archiveDialog.visible = true;
};

const submitArchive = async () => {
  if (!archiveForm.value.talentId) return;
  await archiveTalent({ talentId: archiveForm.value.talentId, remark: archiveForm.value.remark });
  modal.msgSuccess('归档成功');
  archiveDialog.visible = false;
  await getList();
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.full-phone-box {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 1px;
  text-align: center;
}

.mt-2 {
  margin-top: 8px;
}

.mb-2 {
  margin-bottom: 8px;
}

.mr-2 {
  margin-right: 8px;
}

.ml-1 {
  margin-left: 4px;
}

.import-steps {
  margin-bottom: 16px;
}

.import-warning-line {
  line-height: 1.6;
}

.import-review-table {
  margin-bottom: 8px;

  /* 低置信度行整行浅橙底，配合「请核对」标签提示人工复核 */
  :deep(.el-table__row.is-low-confidence) {
    background-color: var(--el-color-warning-light-9);
  }
}

.text-placeholder {
  color: var(--el-text-color-placeholder);
}
</style>
