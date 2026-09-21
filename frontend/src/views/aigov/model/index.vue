<template>
  <div class="p-2 app-container aigov-model-page">
    <PageHeading title="AI平台治理" subtitle="管理AI能力、模型接入与调用策略" admin module="aigov" />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>模型检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="模型键" prop="modelKey">
            <el-input v-model="queryParams.modelKey" placeholder="请输入模型键" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="模型名称" prop="modelName">
            <el-input
              v-model="queryParams.modelName"
              placeholder="请输入模型名称"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="部署类型" prop="deploymentType">
            <el-select v-model="queryParams.deploymentType" placeholder="请选择部署类型" clearable style="width: 180px">
              <el-option
                v-for="dict in aig_deployment_type"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="数据等级上限" prop="dataLevelMax">
            <el-select v-model="queryParams.dataLevelMax" placeholder="请选择数据等级" clearable style="width: 160px">
              <el-option v-for="dict in aig_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="生命周期" prop="lifecycleStatus">
            <el-select
              v-model="queryParams.lifecycleStatus"
              placeholder="请选择可用状态"
              clearable
              style="width: 160px"
            >
              <el-option
                v-for="dict in aig_lifecycle_status"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
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
            <span class="panel-kicker">Model Registry</span>
            <h3>模型注册中心</h3>
            <p>
              共 {{ total }} 条记录；模型主数据存放于 sai_model_config，可在此新增模型并同时登记首份治理属性。
              模型密钥可在此直接录入（提交后加密落库），列表只显示「已配置／未配置」，永不回显密钥内容。
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['aig:model:add']"
              type="primary"
              icon="Plus"
              @click="handleCreateModel"
            >
              新增模型
            </el-button>
            <el-button
              v-hasPermi="['aig:model:add']"
              icon="Connection"
              @click="openProviderManage"
            >
              供应商管理
            </el-button>
            <el-button
              v-hasPermi="['aig:model:edit']"
              type="success"
              plain
              icon="Edit"
              :disabled="single"
              @click="handleGovernance()"
            >
              登记治理属性
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        border
        class="data-table"
        :data="modelList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="模型键" align="center" prop="modelKey" width="160" show-overflow-tooltip />
        <el-table-column label="模型名称" align="center" prop="modelName" width="160" show-overflow-tooltip />
        <el-table-column label="模型类型" align="center" prop="modelType" width="120" />
        <el-table-column label="默认" align="center" width="90">
          <template #default="scope">
            <el-tag :type="isOn(scope.row.isDefault) ? 'success' : 'info'">
              {{ isOn(scope.row.isDefault) ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" align="center" width="90">
          <template #default="scope">
            <el-tag :type="isOn(scope.row.isEnabled) ? 'success' : 'danger'">
              {{ isOn(scope.row.isEnabled) ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部署类型" align="center" width="140">
          <template #default="scope">
            <dict-tag :options="aig_deployment_type" :value="scope.row.deploymentType" />
          </template>
        </el-table-column>
        <el-table-column label="数据等级上限" align="center" width="130">
          <template #default="scope">
            <dict-tag :options="aig_data_level" :value="scope.row.dataLevelMax" />
          </template>
        </el-table-column>
        <el-table-column label="可用状态" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="aig_lifecycle_status" :value="scope.row.lifecycleStatus" />
          </template>
        </el-table-column>
        <!-- 健康状态：由「测试连接」写入治理表 health_status / health_time -->
        <el-table-column label="连通性" align="center" width="150">
          <template #default="scope">
            <div class="health-cell">
              <el-tag v-if="scope.row.healthStatus" :type="scope.row.healthStatus === 'HEALTHY' ? 'success' : 'danger'">
                {{ scope.row.healthStatus === 'HEALTHY' ? '连通' : '不通' }}
              </el-tag>
              <el-tag v-else type="info">未测试</el-tag>
              <span v-if="scope.row.healthTime" class="health-time">{{ parseTime(scope.row.healthTime, '{m}-{d} {h}:{i}') }}</span>
            </div>
          </template>
        </el-table-column>
        <!--
          模型密钥状态：后端只回布尔位（在 SQL 内算好），密钥原值永不进入前端。
          不挂 v-hasPermi —— 它是「治理属性齐全但密钥为空」这类静默失败的告警，
          有列表权限的人本就该看见；能否「配置」密钥另由 aig:model:secret 控制。
        -->
        <el-table-column label="模型密钥" align="center" width="110">
          <template #default="scope">
            <el-tag v-if="scope.row.keyConfigured === true" type="success">已配置</el-tag>
            <el-tag v-else-if="scope.row.keyConfigured === false" type="warning">未配置</el-tag>
            <el-tag v-else type="info">未知</el-tag>
          </template>
        </el-table-column>
        <!-- 端点与密钥引用：仅 aig:model:secret 授权可见，无权限时该列不渲染 -->
        <el-table-column
          v-hasPermi="['aig:model:secret']"
          label="API 端点"
          align="center"
          prop="apiEndpoint"
          width="200"
          show-overflow-tooltip
        />
        <el-table-column v-hasPermi="['aig:model:secret']" label="密钥引用" align="center" width="200">
          <template #default="scope">
            <span>{{ scope.row.secretRef || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成本限额" align="center" prop="costLimit" show-overflow-tooltip />
        <el-table-column label="责任人" align="center" width="200">
          <template #default="scope">
            <div class="owner-cell">
              <span>技术：{{ scope.row.ownerTech || '-' }}</span>
              <span>业务：{{ scope.row.ownerBiz || '-' }}</span>
              <span>安全：{{ scope.row.ownerSecurity || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="有效期" align="center" width="180">
          <template #default="scope">
            <span>{{ scope.row.validFrom || '-' }} ~ {{ scope.row.validTo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="测试连接" placement="top">
              <el-button
                v-hasPermi="['aig:model:edit']"
                link
                type="primary"
                icon="Connection"
                :loading="testingId === scope.row.modelId"
                @click="handleTestConnection(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="登记治理属性" placement="top">
              <el-button
                v-hasPermi="['aig:model:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleGovernance(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="配置模型密钥" placement="top">
              <el-button
                v-hasPermi="['aig:model:secret']"
                link
                type="primary"
                icon="Key"
                @click="handleSecret(scope.row)"
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

    <!-- 登记/修改治理属性对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="820px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="模型主数据由 snail-ai 维护，此处只补齐治理属性；密钥引用只登记引用地址，明文密钥请在「配置模型密钥」中录入。"
      />
      <el-form ref="modelFormRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="模型键">
          <el-input :model-value="currentModel.modelKey" disabled placeholder="来自 snail-ai" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部署类型" prop="deploymentType">
              <el-select v-model="form.deploymentType" placeholder="请选择部署类型" style="width: 100%">
                <el-option
                  v-for="dict in aig_deployment_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据等级上限" prop="dataLevelMax">
              <el-select v-model="form.dataLevelMax" placeholder="请选择数据等级上限" style="width: 100%">
                <el-option v-for="dict in aig_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="可用状态" prop="lifecycleStatus">
              <el-select v-model="form.lifecycleStatus" placeholder="请选择可用状态" style="width: 100%">
                <el-option
                  v-for="dict in aig_lifecycle_status"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">
                  {{ dict.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 密钥引用：仅 aig:model:secret 授权可见；无权限时字段不渲染且不参与提交 -->
        <el-form-item v-hasPermi="['aig:model:secret']" label="密钥引用" prop="secretRef">
          <el-input v-model="form.secretRef" placeholder="如 kms://ai/qwen，只填引用不填明文" />
          <div class="form-tip">仅登记引用地址（受支持 scheme：kms:// vault:// env:// sm:// secret://）；真正的密钥请用「配置模型密钥」录入。</div>
        </el-form-item>
        <el-form-item label="输入限制" prop="inputLimits">
          <el-input v-model="form.inputLimits" placeholder="文本长度/文件类型/图片视频大小/并发" />
        </el-form-item>
        <el-form-item label="输出限制" prop="outputLimits">
          <el-input v-model="form.outputLimits" placeholder="格式/时长/分辨率/结构化输出能力" />
        </el-form-item>
        <el-form-item label="成本限额" prop="costLimit">
          <el-input v-model="form.costLimit" placeholder="单次/单项目/单日预算与限流规则" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="技术负责人" prop="ownerTech">
              <el-input v-model="form.ownerTech" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务负责人" prop="ownerBiz">
              <el-input v-model="form.ownerBiz" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="安全审批人" prop="ownerSecurity">
              <el-input v-model="form.ownerSecurity" placeholder="请输入" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="有效期起" prop="validFrom">
              <el-date-picker
                v-model="form.validFrom"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期止" prop="validTo">
              <el-date-picker
                v-model="form.validTo"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
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

    <!-- 新增模型对话框：模型主数据 + 首份治理属性一次提交 -->
    <el-dialog v-model="createVisible" title="新增模型" width="860px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="此处会把模型写入 sai_model_config。部署类型、数据等级上限、可用状态必须同时登记——缺治理属性的模型会被路由引擎排除，永远不会被任何策略选中。密钥引用只填引用地址；明文密钥填「API 密钥」，后端加密后落库。"
      />
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商" prop="providerId">
              <div class="provider-picker">
                <el-select v-model="createForm.providerId" placeholder="请选择供应商" style="width: 100%">
                  <el-option
                    v-for="item in providerOptions"
                    :key="item.providerId"
                    :label="item.providerName"
                    :value="item.providerId!"
                  />
                </el-select>
                <el-button
                  v-hasPermi="['aig:model:add']"
                  link
                  type="primary"
                  icon="Plus"
                  @click="openProviderForm()"
                >
                  新增供应商
                </el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型类型" prop="modelType">
              <el-select
                v-model="createForm.modelType"
                placeholder="如 CHAT / EMBEDDING"
                filterable
                allow-create
                default-first-option
                style="width: 100%"
              >
                <el-option v-for="t in modelTypeOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型标识" prop="modelKey">
              <el-input v-model="createForm.modelKey" placeholder="全局唯一，如 qwen-plus" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型名称" prop="modelName">
              <el-input v-model="createForm.modelName" placeholder="展示用名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="适配器标识" prop="adapterKey">
              <el-input v-model="createForm.adapterKey" placeholder="如 openai-compatible、local-rule" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作用域" prop="scope">
              <el-select v-model="createForm.scope" style="width: 100%">
                <el-option label="全局 GLOBAL" value="GLOBAL" />
                <el-option label="本地 LOCAL" value="LOCAL" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="接口地址" prop="apiEndpoint">
          <el-input v-model="createForm.apiEndpoint" placeholder="本地部署可留空；非本地部署建议填写" />
        </el-form-item>
        <!-- 模型密钥：仅 aig:model:secret 授权可见；明文只提交一次，后端加密落库 -->
        <el-form-item v-hasPermi="['aig:model:secret']" label="API 密钥" prop="apiKey">
          <el-input
            v-model="createForm.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="选填；供应商签发的 API Key"
          />
          <div class="form-tip">
            明文只提交一次，界面永不回显；留空可稍后在列表用「配置模型密钥」单独补录。
            后端按 snail-ai 的 SM4 口径加密后写入 sai_model_config.api_key。
          </div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部署类型" prop="deploymentType">
              <el-select v-model="createForm.deploymentType" placeholder="请选择部署类型" style="width: 100%">
                <el-option
                  v-for="dict in aig_deployment_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据等级上限" prop="dataLevelMax">
              <el-select v-model="createForm.dataLevelMax" placeholder="请选择数据等级上限" style="width: 100%">
                <el-option v-for="dict in aig_data_level" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="可用状态" prop="lifecycleStatus">
              <el-select v-model="createForm.lifecycleStatus" placeholder="请选择可用状态" style="width: 100%">
                <el-option
                  v-for="dict in aig_lifecycle_status"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否默认">
              <el-switch v-model="createForm.isDefault" />
              <span class="form-tip">同一模型类型建议只设一个默认</span>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 密钥引用：仅 aig:model:secret 授权可见；无权限时字段不渲染且不参与提交 -->
        <el-form-item v-hasPermi="['aig:model:secret']" label="密钥引用" prop="secretRef">
          <el-input v-model="createForm.secretRef" placeholder="如 kms://ai/qwen，只填引用不填明文" />
          <div class="form-tip">仅登记引用地址（受支持 scheme：kms:// vault:// env:// sm:// secret://）；真正的密钥请用「配置模型密钥」录入。</div>
        </el-form-item>
        <el-form-item label="成本限额" prop="costLimit">
          <el-input v-model="createForm.costLimit" placeholder="单次/单项目/单日预算与限流规则" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="技术负责人" prop="ownerTech">
              <el-input v-model="createForm.ownerTech" placeholder="技术负责人" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务负责人" prop="ownerBiz">
              <el-input v-model="createForm.ownerBiz" placeholder="业务负责人" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="安全审批人" prop="ownerSecurity">
              <el-input v-model="createForm.ownerSecurity" placeholder="安全审批人" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="有效期" prop="validRange">
          <el-date-picker
            v-model="validRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="生效日期"
            end-placeholder="失效日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="2" placeholder="模型用途与来源说明" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="createSubmitting" @click="submitCreate">确 定</el-button>
          <el-button @click="createVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 模型密钥：明文只提交一次，后端加密落库；任何界面都不回显密钥 -->
    <el-dialog v-model="secretDialog.visible" title="配置模型密钥" width="640px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="密钥提交后由后端加密写入，界面永远不会回显；如需更换请直接填新值，如需作废请用「清除密钥」。"
      />
      <el-form ref="secretFormRef" :model="secretForm" :rules="secretRules" label-width="110px">
        <el-form-item label="模型键">
          <el-input :model-value="currentSecretModel.modelKey" disabled placeholder="来自模型清单" />
        </el-form-item>
        <el-form-item label="当前状态">
          <el-tag v-if="currentSecretModel.keyConfigured === true" type="success">已配置</el-tag>
          <el-tag v-else-if="currentSecretModel.keyConfigured === false" type="warning">未配置</el-tag>
          <el-tag v-else type="info">未知</el-tag>
          <span class="form-tip">（只显示有无，不回显密钥内容）</span>
        </el-form-item>
        <el-form-item label="API 密钥" prop="apiKey">
          <el-input
            v-model="secretForm.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="currentSecretModel.keyConfigured ? '留空则保持不变；填入新值即覆盖' : '请输入供应商签发的 API Key'"
          />
          <div class="form-tip">
            明文仅提交一次，后端按 snail-ai 的 SM4 口径加密后写入 sai_model_config.api_key。
            未开启 aigov.model-crypto.enabled 时会被拒绝。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="secretSubmitting" @click="submitSecret">确 定</el-button>
          <el-button
            v-if="currentSecretModel.keyConfigured"
            type="danger"
            plain
            :loading="secretSubmitting"
            @click="submitClearSecret"
          >
            清除密钥
          </el-button>
          <el-button @click="secretDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 供应商管理：内置 7 家之外可自行新增（接入自建推理服务/内部网关/新云厂商） -->
    <el-dialog v-model="providerManageVisible" title="模型供应商管理" width="900px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="供应商只登记名称/标识/说明/启停，表里没有密钥列——密钥属于模型配置，治理层只登记引用。停用后该供应商不再出现在「新增模型」下拉里。"
      />
      <div class="provider-toolbar">
        <el-button v-hasPermi="['aig:model:add']" type="primary" icon="Plus" @click="openProviderForm()">
          新增供应商
        </el-button>
        <el-button icon="Refresh" @click="loadAllProviders">刷新</el-button>
      </div>
      <el-table v-loading="providerLoading" border :data="allProviders">
        <el-table-column label="供应商名称" align="center" prop="providerName" width="160" show-overflow-tooltip />
        <el-table-column label="标识" align="center" prop="providerKey" width="130" />
        <el-table-column label="说明" align="center" prop="description" show-overflow-tooltip />
        <el-table-column label="已有模型" align="center" width="100">
          <template #default="scope">{{ scope.row.modelCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.isEnabled ? 'success' : 'info'">{{ scope.row.isEnabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="170" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['aig:model:add']" link type="primary" icon="Edit" @click="openProviderForm(scope.row)">
              编辑
            </el-button>
            <el-button
              v-hasPermi="['aig:model:add']"
              link
              :type="scope.row.isEnabled ? 'danger' : 'success'"
              @click="toggleProvider(scope.row)"
            >
              {{ scope.row.isEnabled ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 供应商新增/编辑 -->
    <el-dialog v-model="providerFormVisible" :title="providerFormTitle" width="620px" append-to-body>
      <el-form ref="providerFormRef" :model="providerForm" :rules="providerRules" label-width="110px">
        <el-form-item label="供应商名称" prop="providerName">
          <el-input v-model="providerForm.providerName" placeholder="展示用名称，如 自建推理网关" />
        </el-form-item>
        <el-form-item label="供应商标识" prop="providerKey">
          <el-input
            v-model="providerForm.providerKey"
            :disabled="!!providerForm.id"
            placeholder="小写字母/数字/下划线/中划线，如 internal-gateway"
          />
          <div class="form-tip">标识是模型的归属键，创建后不可修改。</div>
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="providerForm.description" type="textarea" :rows="2" placeholder="例如：自建 OpenAI 兼容网关，仅内网可达" />
        </el-form-item>
        <el-form-item label="图标地址" prop="iconUrl">
          <el-input v-model="providerForm.iconUrl" placeholder="可选，图标 URL" />
        </el-form-item>
        <el-form-item label="启用" prop="isEnabled">
          <el-switch v-model="providerForm.isEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="providerSubmitting" @click="submitProvider">确 定</el-button>
          <el-button @click="providerFormVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 连通性测试结果 -->
    <el-dialog v-model="testDialog.visible" title="模型连通性测试" width="640px" append-to-body>
      <el-result
        :icon="testDialog.result?.ok ? 'success' : 'error'"
        :title="testDialog.result?.ok ? '连接成功' : '连接失败'"
        :sub-title="testDialog.result?.message || ''"
      >
        <template #extra>
          <el-descriptions :column="1" border size="small" class="test-detail">
            <el-descriptions-item label="模型">{{ testDialog.modelLabel }}</el-descriptions-item>
            <el-descriptions-item label="探测方式">{{ probeLabel(testDialog.result?.probe) }}</el-descriptions-item>
            <el-descriptions-item v-if="testDialog.result?.endpointHost" label="目标主机">
              {{ testDialog.result?.endpointHost }}
            </el-descriptions-item>
            <el-descriptions-item label="耗时">{{ testDialog.result?.latencyMs ?? '-' }} ms</el-descriptions-item>
            <el-descriptions-item v-if="testDialog.result?.detail" label="上游返回">
              <span class="test-detail-text">{{ testDialog.result?.detail }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <div class="test-tips">
            <p v-if="!testDialog.result?.ok">排查建议：核对访问地址是否需要 /v1 前缀、模型标识是否为厂商侧真实模型名、密钥是否有效且在治理层登记了引用。</p>
            <p v-else>健康状态已写入治理属性，列表「连通性」列可见。</p>
          </div>
        </template>
      </el-result>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="testDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type {
  AigModelCreateForm,
  AigModelGovernanceForm,
  AigModelGovernanceVO,
  AigModelProviderForm,
  AigModelProviderOption,
  AigModelQuery,
  AigModelSecretForm,
  AigModelTestResult
} from '@/api/aigov/model/types';
import {
  createModel,
  createModelProvider,
  getModel,
  listAllModelProviders,
  listModel,
  listModelProviders,
  testModelConnection,
  updateModelGovernance,
  updateModelProvider,
  updateModelSecret
} from '@/api/aigov/model';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { checkPermi } from '@/utils/permission';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'AigModel' });

const { aig_deployment_type, aig_data_level, aig_lifecycle_status, sys_normal_disable } = toRefs<any>(
  useDict('aig_deployment_type', 'aig_data_level', 'aig_lifecycle_status', 'sys_normal_disable')
);

const modelList = ref<AigModelGovernanceVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const { ids, single, handleSelectionChange } = useTableSelection<AigModelGovernanceVO>(item => item.modelId!);
const total = ref(0);
const modelFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();
/** 当前正在登记治理属性的模型（只读主数据回显） */
const currentModel = ref<AigModelGovernanceVO>({});

const initFormData: AigModelGovernanceForm = {
  governanceId: undefined,
  modelId: undefined,
  deploymentType: 'EXTERNAL_API',
  dataLevelMax: 'PUBLIC',
  lifecycleStatus: 'CANDIDATE',
  secretRef: '',
  inputLimits: '',
  outputLimits: '',
  costLimit: '',
  ownerTech: '',
  ownerBiz: '',
  ownerSecurity: '',
  validFrom: undefined,
  validTo: undefined,
  status: '0',
  remark: ''
};

const data = reactive<PageData<AigModelGovernanceForm, AigModelQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    modelKey: '',
    modelName: '',
    deploymentType: undefined,
    dataLevelMax: undefined,
    lifecycleStatus: undefined,
    params: {}
  },
  rules: {
    deploymentType: [{ required: true, message: '部署类型不能为空', trigger: 'change' }],
    dataLevelMax: [{ required: true, message: '数据等级上限不能为空', trigger: 'change' }],
    lifecycleStatus: [{ required: true, message: '可用状态不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<AigModelGovernanceForm, AigModelQuery>>(data);
const { dialog, resetForm, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: modelFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** sai_model_config 的 is_default / is_enabled 可能以 1 或 '1' 下发 */
const isOn = (value?: number | string) => value === 1 || value === '1';

/** 查询模型清单 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listModel(queryParams.value);
    modelList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 取消按钮 */
const cancel = () => {
  closeDialog();
  resetForm();
  currentModel.value = {};
};

/** 登记/修改治理属性 */
const handleGovernance = async (row?: Partial<AigModelGovernanceVO>) => {
  resetForm();
  const modelId = row?.modelId || ids.value[0];
  const res = await getModel(modelId!);
  currentModel.value = res.data || {};
  Object.assign(form.value, initFormData);
  form.value.modelId = modelId;
  form.value.governanceId = res.data?.governanceId;
  form.value.deploymentType = res.data?.deploymentType || initFormData.deploymentType;
  form.value.dataLevelMax = res.data?.dataLevelMax || initFormData.dataLevelMax;
  form.value.lifecycleStatus = res.data?.lifecycleStatus || initFormData.lifecycleStatus;
  form.value.secretRef = res.data?.secretRef || '';
  form.value.inputLimits = res.data?.inputLimits || '';
  form.value.outputLimits = res.data?.outputLimits || '';
  form.value.costLimit = res.data?.costLimit || '';
  form.value.ownerTech = res.data?.ownerTech || '';
  form.value.ownerBiz = res.data?.ownerBiz || '';
  form.value.ownerSecurity = res.data?.ownerSecurity || '';
  form.value.validFrom = res.data?.validFrom;
  form.value.validTo = res.data?.validTo;
  form.value.status = res.data?.status || '0';
  form.value.remark = res.data?.remark || '';
  showDialog('登记治理属性');
};

/** 提交按钮 */
const submitForm = () => {
  modelFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      const payload: AigModelGovernanceForm = { ...form.value };
      // 无 aig:model:secret 权限时不下发密钥引用字段，避免覆盖已有引用
      if (!checkPermi(['aig:model:secret'])) {
        delete payload.secretRef;
      }
      await updateModelGovernance(payload);
      modal.msgSuccess('操作成功');
      closeDialog();
      await getList();
    }
  });
};

// ---------------------------------------------------------------- 新增模型

/** 新增模型弹窗可见性 */
const createVisible = ref(false);
const createFormRef = ref<ElFormInstance>();
const createSubmitting = ref(false);
/** 供应商下拉选项（仅启用项，不含凭据） */
const providerOptions = ref<AigModelProviderOption[]>([]);
/** 模型类型建议值；允许自由输入，故不写死为字典 */
const modelTypeOptions = ['CHAT', 'EMBEDDING', 'RERANK', 'VISION'];
/** 有效期区间，提交时拆成 validFrom / validTo */
const validRange = ref<string[]>([]);

const initCreateForm = (): AigModelCreateForm => ({
  providerId: undefined,
  modelName: '',
  modelKey: '',
  modelType: 'CHAT',
  adapterKey: '',
  apiEndpoint: '',
  // 明文密钥：只出现在表单里，提交后由后端加密落库，界面永不回显
  apiKey: '',
  description: '',
  scope: 'GLOBAL',
  isDefault: false,
  isEnabled: true,
  deploymentType: 'EXTERNAL_API',
  dataLevelMax: 'PUBLIC',
  lifecycleStatus: 'CANDIDATE',
  secretRef: '',
  costLimit: '',
  ownerTech: '',
  ownerBiz: '',
  ownerSecurity: '',
  remark: ''
});

const createForm = ref<AigModelCreateForm>(initCreateForm());

const createRules = {
  providerId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  modelKey: [
    { required: true, message: '模型标识不能为空', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9][A-Za-z0-9._-]*$/,
      message: '只能由字母、数字、点、下划线、中划线组成，且以字母或数字开头',
      trigger: 'blur'
    }
  ],
  modelName: [{ required: true, message: '模型名称不能为空', trigger: 'blur' }],
  modelType: [{ required: true, message: '模型类型不能为空', trigger: 'change' }],
  deploymentType: [{ required: true, message: '部署类型不能为空', trigger: 'change' }],
  dataLevelMax: [{ required: true, message: '数据等级上限不能为空', trigger: 'change' }],
  lifecycleStatus: [{ required: true, message: '可用状态不能为空', trigger: 'change' }]
};

/** 打开新增模型弹窗：重置表单并加载供应商选项 */
const handleCreateModel = async () => {
  createForm.value = initCreateForm();
  validRange.value = [];
  createVisible.value = true;
  try {
    const res = await listModelProviders();
    providerOptions.value = res.data || [];
  } catch {
    // 拦截器已提示错误；这里降级为空选项，避免弹窗整体不可用
    providerOptions.value = [];
  }
};

/** 提交新增模型 */
const submitCreate = () => {
  createFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    const payload: AigModelCreateForm = { ...createForm.value };
    payload.validFrom = validRange.value?.[0];
    payload.validTo = validRange.value?.[1];
    // 无 aig:model:secret 权限时不下发密钥引用与明文密钥（后端亦会拒绝，双保险）
    if (!checkPermi(['aig:model:secret'])) {
      delete payload.secretRef;
      delete payload.apiKey;
    }
    createSubmitting.value = true;
    try {
      await createModel(payload);
      modal.msgSuccess('新增成功');
      createVisible.value = false;
      await getList();
    } finally {
      createSubmitting.value = false;
    }
  });
};

// ---------------------------------------------------------------- 模型密钥

/** 密钥弹窗可见性 */
const secretDialog = ref({ visible: false });
const secretFormRef = ref<ElFormInstance>();
const secretSubmitting = ref(false);
/** 当前正在配置密钥的模型（只读回显，含 keyConfigured 布尔位） */
const currentSecretModel = ref<AigModelGovernanceVO>({});

const secretForm = ref<AigModelSecretForm>({ modelId: '', apiKey: '' });

const secretRules = {
  apiKey: [
    { max: 500, message: 'API 密钥长度不能超过 500', trigger: 'blur' }
  ]
};

/** 打开密钥配置弹窗：明文输入框始终清空，绝不回显已有密钥 */
const handleSecret = (row: AigModelGovernanceVO) => {
  currentSecretModel.value = row;
  secretForm.value = { modelId: row.modelId!, apiKey: '' };
  secretDialog.value.visible = true;
};

/** 提交密钥：明文只在这一次请求体里出现，之后后端加密落库 */
const submitSecret = () => {
  secretFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    if (!secretForm.value.apiKey) {
      modal.msgWarning('请输入 API 密钥；如需作废已有密钥请用「清除密钥」');
      return;
    }
    secretSubmitting.value = true;
    try {
      await updateModelSecret({ modelId: secretForm.value.modelId, apiKey: secretForm.value.apiKey });
      modal.msgSuccess('密钥已保存（加密落库，界面不再回显）');
      // 用后即焚：避免明文停留在内存表单里
      secretForm.value.apiKey = '';
      secretDialog.value.visible = false;
      await getList();
    } finally {
      secretSubmitting.value = false;
    }
  });
};

/** 清除密钥：显式二次确认，避免误清导致模型立刻不可用 */
const submitClearSecret = async () => {
  try {
    await modal.confirm('清除后该模型将无法再向供应商发起调用，直到重新配置密钥。确定清除？');
  } catch {
    return;
  }
  secretSubmitting.value = true;
  try {
    await updateModelSecret({ modelId: secretForm.value.modelId, clearKey: true });
    modal.msgSuccess('密钥已清除');
    secretForm.value.apiKey = '';
    secretDialog.value.visible = false;
    await getList();
  } finally {
    secretSubmitting.value = false;
  }
};

// ---------------------------------------------------------------- 供应商管理

/** 供应商管理弹窗 */
const providerManageVisible = ref(false);
const providerLoading = ref(false);
const allProviders = ref<AigModelProviderOption[]>([]);
/** 供应商新增/编辑弹窗 */
const providerFormVisible = ref(false);
const providerFormRef = ref<ElFormInstance>();
const providerSubmitting = ref(false);
const providerFormTitle = ref('新增供应商');

const initProviderForm = (): AigModelProviderForm => ({
  id: undefined,
  providerName: '',
  providerKey: '',
  description: '',
  iconUrl: '',
  isEnabled: true
});

const providerForm = ref<AigModelProviderForm>(initProviderForm());

const providerRules = {
  providerName: [{ required: true, message: '供应商名称不能为空', trigger: 'blur' }],
  providerKey: [
    { required: true, message: '供应商标识不能为空', trigger: 'blur' },
    {
      pattern: /^[a-z0-9][a-z0-9_-]{1,49}$/,
      message: '只能用小写字母、数字、下划线或中划线，且以字母或数字开头',
      trigger: 'blur'
    }
  ]
};

/** 打开供应商管理弹窗 */
const openProviderManage = async () => {
  providerManageVisible.value = true;
  await loadAllProviders();
};

/** 加载供应商全量列表（含停用） */
const loadAllProviders = async () => {
  providerLoading.value = true;
  try {
    const res = await listAllModelProviders();
    allProviders.value = res.data || [];
  } catch {
    allProviders.value = [];
  } finally {
    providerLoading.value = false;
  }
};

/** 打开供应商新增/编辑弹窗 */
const openProviderForm = (row?: AigModelProviderOption) => {
  providerForm.value = row
    ? {
        id: row.providerId,
        providerName: row.providerName,
        providerKey: row.providerKey,
        description: row.description || '',
        iconUrl: row.iconUrl || '',
        isEnabled: !!row.isEnabled
      }
    : initProviderForm();
  providerFormTitle.value = row ? '编辑供应商' : '新增供应商';
  providerFormVisible.value = true;
};

/** 提交供应商新增/编辑 */
const submitProvider = () => {
  providerFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    providerSubmitting.value = true;
    try {
      if (providerForm.value.id) {
        await updateModelProvider({ ...providerForm.value });
        modal.msgSuccess('修改成功');
      } else {
        const res = await createModelProvider({ ...providerForm.value });
        modal.msgSuccess('新增成功');
        // 新增后自动选中：用户点「新增供应商」的意图就是马上用它建模型
        const newId = res.data as unknown as string | number;
        if (newId != null && createVisible.value) {
          createForm.value.providerId = newId;
        }
      }
      providerFormVisible.value = false;
      await loadAllProviders();
      await refreshProviderOptions();
    } finally {
      providerSubmitting.value = false;
    }
  });
};

/** 启用/停用供应商 */
const toggleProvider = async (row: AigModelProviderOption) => {
  await updateModelProvider({
    id: row.providerId,
    providerName: row.providerName,
    isEnabled: !row.isEnabled
  });
  modal.msgSuccess(row.isEnabled ? '已停用' : '已启用');
  await loadAllProviders();
  await refreshProviderOptions();
};

/** 刷新「新增模型」下拉的供应商选项（仅启用项） */
const refreshProviderOptions = async () => {
  try {
    const res = await listModelProviders();
    providerOptions.value = res.data || [];
  } catch {
    providerOptions.value = [];
  }
};

// ---------------------------------------------------------------- 连通性测试

/** 正在测试的模型ID（按钮 loading） */
const testingId = ref<string | number | undefined>(undefined);
const testDialog = reactive<{ visible: boolean; result?: AigModelTestResult; modelLabel: string }>({
  visible: false,
  result: undefined,
  modelLabel: ''
});

/** 探测方式的中文说明 */
const probeLabel = (probe?: string) => {
  const map: Record<string, string> = {
    LOCAL_ENGINE: '本地执行者自检（不出网）',
    SNAIL_AI: 'snail-ai 链路调用',
    OPENAI_COMPATIBLE: 'OpenAI 兼容端点探测',
    UNSUPPORTED: '不支持的适配器'
  };
  return probe ? map[probe] || probe : '-';
};

/** 测试连接：结果写入治理表健康状态，并在弹窗里给出结论与排查建议 */
const handleTestConnection = async (row: AigModelGovernanceVO) => {
  testingId.value = row.modelId;
  try {
    const res = await testModelConnection(row.modelId!);
    testDialog.result = res.data || {};
    testDialog.modelLabel = [row.modelName || row.modelKey, row.modelKey && row.modelName ? `(${row.modelKey})` : '']
      .filter(Boolean)
      .join(' ');
    testDialog.visible = true;
    if (res.data?.ok) {
      modal.msgSuccess('连接成功');
    } else {
      modal.msgError('连接失败：' + (res.data?.message || '未知原因'));
    }
    await getList();
  } finally {
    testingId.value = undefined;
  }
};

onMounted(() => {
  getList();
});</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.dialog-alert {
  margin-bottom: 12px;
}

.owner-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  font-size: 12px;
  line-height: 1.5;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}

/* 供应商选择 + 「新增供应商」入口同一行 */
.provider-picker {
  width: 100%;
}

.provider-picker .el-button {
  padding: 0;
  height: auto;
}

.provider-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

/* 连通性列：状态标签 + 最近测试时间 */
.health-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.health-time {
  font-size: 11px;
  color: var(--app-text-muted);
}

.test-detail {
  margin-top: 8px;
  text-align: left;
}

.test-detail-text {
  word-break: break-all;
}

.test-tips {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--app-text-muted);
  text-align: left;
}
</style>
