<template>
  <div class="p-2 app-container talent-profile-page">
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

    <user-select ref="userSelectRef" :multiple="false" @confirm-call-back="handleUserSelected" />
    <role-select ref="roleSelectRef" :multiple="false" @confirm-call-back="handleRoleSelected" />
  </div>
</template>

<script setup lang="ts">
import type { TalentGrantVO, TalentContactVO, TalentDetailVO, TalentForm, TalentQuery, TalentVO } from '@/api/talent/profile/types';
import type { TalentDuplicateVO } from '@/api/talent/duplicate/types';
import {
  addContact,
  addGrant,
  addTalent,
  archiveTalent,
  getFullPhone,
  getTalent,
  listContact,
  listGrant,
  listTalent,
  preCheckTalent,
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

.mr-2 {
  margin-right: 8px;
}
</style>
