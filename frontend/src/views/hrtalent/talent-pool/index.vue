<template>
  <div class="p-2 app-container hrtalent-talent-pool-page">
    <PageHeading
      title="人才池与分组"
      subtitle="人才池 CRUD 与成员管理（加入记录原因/推荐岗位/适配等级/下次联系时间，重复加入幂等，移出只结束关系）、公共分组与个人收藏维护、标签字典维护与人才标签挂载"
      module="hrtalent"
    />

    <el-card shadow="hover" class="table-panel pool-shell">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- ------------------------------ 人才池 ------------------------------ -->
        <el-tab-pane label="人才池" name="pool">
          <el-alert
            class="dialog-alert"
            type="info"
            :closable="false"
            show-icon
            title="移出人才池只结束成员关系，不删除人才主档（设计 §8.15）；重复加入按幂等处理并返回已有关系，不会报错。"
          />
          <el-form :model="poolQuery" :inline="true" class="query-form">
            <el-form-item label="人才池名称">
              <el-input v-model="poolQuery.poolName" placeholder="模糊匹配" clearable @keyup.enter="queryPool" />
            </el-form-item>
            <el-form-item label="人才池编号">
              <el-input v-model="poolQuery.poolCode" placeholder="精确匹配" clearable @keyup.enter="queryPool" />
            </el-form-item>
            <el-form-item label="人才池类型">
              <el-input v-model="poolQuery.poolType" placeholder="如 reserve/position/talent" clearable @keyup.enter="queryPool" />
            </el-form-item>
            <el-form-item label="池管理员ID">
              <el-input v-model="poolQuery.managerId" placeholder="用户ID" clearable @keyup.enter="queryPool" />
            </el-form-item>
            <el-form-item label="归属部门ID">
              <el-input v-model="poolQuery.ownerDeptId" placeholder="部门ID" clearable @keyup.enter="queryPool" />
            </el-form-item>
            <el-form-item label="可见范围">
              <el-select v-model="poolQuery.visibilityType" placeholder="请选择" clearable style="width: 150px">
                <el-option
                  v-for="dict in talent_visibility_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-input v-model="poolQuery.status" placeholder="如 active/archived" clearable @keyup.enter="queryPool" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="queryPool">搜索</el-button>
              <el-button icon="Refresh" @click="resetPoolQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <div class="tab-toolbar">
            <el-button v-hasPermi="['talent:pool:add']" type="primary" icon="Plus" @click="openPoolDialog()">
              新增人才池
            </el-button>
          </div>

          <el-table v-loading="poolLoading" border size="small" :data="poolList">
            <el-table-column label="人才池编号" align="center" prop="poolCode" width="160" show-overflow-tooltip />
            <el-table-column label="人才池名称" align="center" prop="poolName" width="180" show-overflow-tooltip />
            <el-table-column label="类型" align="center" prop="poolType" width="110" />
            <el-table-column label="池管理员" align="center" width="120">
              <template #default="scope">{{ scope.row.managerName || scope.row.managerId || '-' }}</template>
            </el-table-column>
            <el-table-column label="归属部门" align="center" width="150" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.ownerDeptName || scope.row.ownerDeptId || '-' }}</template>
            </el-table-column>
            <el-table-column label="可见范围" align="center" width="100">
              <template #default="scope">
                <dict-tag v-if="scope.row.visibilityType" :options="talent_visibility_type" :value="scope.row.visibilityType" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="成员数" align="center" prop="memberCount" width="80" />
            <el-table-column label="状态" align="center" prop="status" width="100" />
            <el-table-column label="说明" prop="poolDesc" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" align="center" width="220" fixed="right">
              <template #default="scope">
                <el-tooltip content="成员管理" placement="top">
                  <el-button
                    v-hasPermi="['talent:pool:list']"
                    link
                    type="primary"
                    icon="User"
                    @click="openPoolMembers(scope.row)"
                  ></el-button>
                </el-tooltip>
                <el-tooltip content="编辑" placement="top">
                  <el-button
                    v-hasPermi="['talent:pool:edit']"
                    link
                    type="primary"
                    icon="Edit"
                    @click="openPoolDialog(scope.row)"
                  ></el-button>
                </el-tooltip>
                <el-tooltip content="删除（只结束成员关系）" placement="top">
                  <el-button
                    v-hasPermi="['talent:pool:edit']"
                    link
                    type="danger"
                    icon="Delete"
                    @click="removePool(scope.row)"
                  ></el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="poolTotal > 0"
            v-model:page="poolQuery.pageNum"
            v-model:limit="poolQuery.pageSize"
            :total="poolTotal"
            @pagination="loadPool"
          />
        </el-tab-pane>

        <!-- ------------------------------ 分组 ------------------------------ -->
        <el-tab-pane label="人才分组" name="group">
          <el-alert
            class="dialog-alert"
            type="warning"
            :closable="false"
            show-icon
            title="公共分组 public：由人才池管理员维护，团队共同整理；个人收藏 personal：仅本人可见可维护，且不改变人才数据权限（设计 §8.15）。"
          />
          <el-form :model="groupQuery" :inline="true" class="query-form">
            <el-form-item label="分组名称">
              <el-input v-model="groupQuery.groupName" placeholder="模糊匹配" clearable @keyup.enter="queryGroup" />
            </el-form-item>
            <el-form-item label="分组类型">
              <el-select v-model="groupQuery.groupType" placeholder="请选择" clearable style="width: 150px">
                <el-option v-for="dict in talent_group_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="负责人ID">
              <el-input v-model="groupQuery.ownerId" placeholder="用户ID" clearable @keyup.enter="queryGroup" />
            </el-form-item>
            <el-form-item label="归属部门ID">
              <el-input v-model="groupQuery.ownerDeptId" placeholder="部门ID" clearable @keyup.enter="queryGroup" />
            </el-form-item>
            <el-form-item label="可见范围">
              <el-select v-model="groupQuery.visibilityType" placeholder="请选择" clearable style="width: 150px">
                <el-option
                  v-for="dict in talent_visibility_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-input v-model="groupQuery.status" placeholder="如 active/inactive" clearable @keyup.enter="queryGroup" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="queryGroup">搜索</el-button>
              <el-button icon="Refresh" @click="resetGroupQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <div class="tab-toolbar">
            <el-button v-hasPermi="['talent:pool:member']" type="primary" icon="Plus" @click="openGroupDialog()">
              新增分组
            </el-button>
            <el-button v-hasPermi="['talent:profile:list']" plain icon="Plus" @click="openGroupDialog(undefined, 'personal')">
              新增个人收藏
            </el-button>
          </div>

          <el-table v-loading="groupLoading" border size="small" :data="groupList">
            <el-table-column label="分组编码" align="center" prop="groupCode" width="150" show-overflow-tooltip />
            <el-table-column label="分组名称" align="center" prop="groupName" min-width="160" show-overflow-tooltip />
            <el-table-column label="分组类型" align="center" width="110">
              <template #default="scope">
                <dict-tag v-if="scope.row.groupType" :options="talent_group_type" :value="scope.row.groupType" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="负责人" align="center" width="120">
              <template #default="scope">{{ scope.row.ownerName || scope.row.ownerId || '-' }}</template>
            </el-table-column>
            <el-table-column label="归属部门ID" align="center" prop="ownerDeptId" width="110" />
            <el-table-column label="可见范围" align="center" width="100">
              <template #default="scope">
                <dict-tag v-if="scope.row.visibilityType" :options="talent_visibility_type" :value="scope.row.visibilityType" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="成员数" align="center" prop="talentCount" width="80" />
            <el-table-column label="状态" align="center" prop="status" width="100" />
            <el-table-column label="操作" align="center" width="220" fixed="right">
              <template #default="scope">
                <el-tooltip content="成员管理" placement="top">
                  <el-button
                    v-hasPermi="['talent:pool:member', 'talent:profile:list']"
                    link
                    type="primary"
                    icon="User"
                    @click="openGroupMembers(scope.row)"
                  ></el-button>
                </el-tooltip>
                <el-tooltip content="编辑" placement="top">
                  <el-button
                    v-hasPermi="['talent:pool:member', 'talent:profile:list']"
                    link
                    type="primary"
                    icon="Edit"
                    @click="openGroupDialog(scope.row)"
                  ></el-button>
                </el-tooltip>
                <el-tooltip content="删除（只删分组与成员关系）" placement="top">
                  <el-button
                    v-hasPermi="['talent:pool:member', 'talent:profile:list']"
                    link
                    type="danger"
                    icon="Delete"
                    @click="removeGroup(scope.row)"
                  ></el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="groupTotal > 0"
            v-model:page="groupQuery.pageNum"
            v-model:limit="groupQuery.pageSize"
            :total="groupTotal"
            @pagination="loadGroup"
          />
        </el-tab-pane>

        <!-- ------------------------------ 标签字典 ------------------------------ -->
        <el-tab-pane label="标签字典" name="tag">
          <el-alert
            class="dialog-alert"
            type="warning"
            :closable="false"
            show-icon
            title="禁止随意创建敏感或歧视性标签：背调失败、健康、家庭、年龄等敏感内容不得自动生成可被普通用户检索的标签（设计 §7.6.4、§8.15），服务层会校验名称与敏感标记。"
          />
          <el-form :model="tagQuery" :inline="true" class="query-form">
            <el-form-item label="标签名称">
              <el-input v-model="tagQuery.tagName" placeholder="模糊匹配" clearable @keyup.enter="queryTag" />
            </el-form-item>
            <el-form-item label="标签编码">
              <el-input v-model="tagQuery.tagCode" placeholder="精确匹配" clearable @keyup.enter="queryTag" />
            </el-form-item>
            <el-form-item label="标签类别">
              <el-select v-model="tagQuery.tagCategory" placeholder="请选择" clearable style="width: 150px">
                <el-option v-for="dict in talent_tag_category" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="是否敏感">
              <el-select v-model="tagQuery.sensitiveFlag" placeholder="不过滤" clearable style="width: 120px">
                <el-option label="敏感" value="1" />
                <el-option label="非敏感" value="0" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-input v-model="tagQuery.status" placeholder="如 active/disabled" clearable @keyup.enter="queryTag" />
            </el-form-item>
            <el-form-item label="仅启用">
              <el-switch v-model="tagQuery.onlyEnabled" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="queryTag">搜索</el-button>
              <el-button icon="Refresh" @click="resetTagQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <div class="tab-toolbar">
            <el-button v-hasPermi="['talent:profile:edit']" type="primary" icon="Plus" @click="openTagDialog()">
              新增标签
            </el-button>
            <el-button v-hasPermi="['talent:profile:edit']" plain icon="Connection" @click="openTagMount()">
              人才标签挂载
            </el-button>
          </div>

          <el-table v-loading="tagLoading" border size="small" :data="tagList">
            <el-table-column label="标签编码" align="center" prop="tagCode" width="160" show-overflow-tooltip />
            <el-table-column label="标签名称" align="center" prop="tagName" min-width="150" show-overflow-tooltip />
            <el-table-column label="标签类别" align="center" width="120">
              <template #default="scope">
                <dict-tag v-if="scope.row.tagCategory" :options="talent_tag_category" :value="scope.row.tagCategory" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="是否敏感" align="center" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.sensitiveFlag === '1' ? 'danger' : 'info'" size="small">
                  {{ scope.row.sensitiveFlag === '1' ? '敏感' : '普通' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="排序号" align="center" prop="sortNo" width="80" />
            <el-table-column label="状态" align="center" prop="status" width="100" />
            <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" align="center" width="130" fixed="right">
              <template #default="scope">
                <el-button
                  v-hasPermi="['talent:profile:edit']"
                  link
                  type="primary"
                  icon="Edit"
                  @click="openTagDialog(scope.row)"
                ></el-button>
                <el-button
                  v-hasPermi="['talent:profile:edit']"
                  link
                  type="danger"
                  icon="Delete"
                  @click="removeTag(scope.row)"
                ></el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="tagTotal > 0"
            v-model:page="tagQuery.pageNum"
            v-model:limit="tagQuery.pageSize"
            :total="tagTotal"
            @pagination="loadTag"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 人才池 新增 / 编辑 -->
    <el-dialog v-model="poolDialog.visible" :title="poolDialog.title" width="720px" append-to-body>
      <el-form ref="poolFormRef" :model="poolForm" :rules="poolRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="人才池名称" prop="poolName">
              <el-input v-model="poolForm.poolName" placeholder="请输入（必填，最长 100）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="人才池类型" prop="poolType">
              <el-input v-model="poolForm.poolType" placeholder="如 reserve/position/talent" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="池管理员" prop="managerId">
              <el-input v-model="poolForm.managerId" placeholder="用户ID" clearable>
                <template #append>
                  <el-button icon="User" @click="poolManagerSelectRef?.open()" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="归属部门ID" prop="ownerDeptId">
              <el-input v-model="poolForm.ownerDeptId" placeholder="部门ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="归属部门名称" prop="ownerDeptName">
              <el-input v-model="poolForm.ownerDeptName" placeholder="部门名称快照" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="可见范围" prop="visibilityType">
              <el-select v-model="poolForm.visibilityType" placeholder="请选择" clearable style="width: 100%">
                <el-option
                  v-for="dict in talent_visibility_type"
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
            <el-form-item label="状态" prop="status">
              <el-input v-model="poolForm.status" placeholder="如 active/archived" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="人才池说明" prop="poolDesc">
          <el-input v-model="poolForm.poolDesc" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="poolForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="poolDialog.submitting" @click="submitPool">确 定</el-button>
          <el-button @click="poolDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 池成员管理 -->
    <el-dialog v-model="poolMemberDialog.visible" title="人才池成员" width="960px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="移出人才池只结束成员关系，不删除人才主档；重复加入会返回已有关系（幂等），页面据此提示「该人才已在池中」。"
      />
      <el-form :inline="true">
        <el-form-item label="成员状态">
          <el-select v-model="poolMemberQuery.memberStatus" placeholder="全部" clearable style="width: 160px" @change="loadPoolMembers">
            <el-option
              v-for="dict in talent_pool_member_status"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['talent:pool:member']" type="primary" icon="Plus" @click="openAddPoolMember">
            加入人才
          </el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="poolMemberDialog.loading" border size="small" :data="poolMemberList">
        <el-table-column label="人才编号" align="center" prop="talentNo" width="150" show-overflow-tooltip />
        <el-table-column label="姓名" align="center" prop="talentName" width="100" />
        <el-table-column label="适配等级" align="center" prop="fitLevel" width="100" />
        <el-table-column label="推荐岗位" prop="recommendedJob" width="150" show-overflow-tooltip />
        <el-table-column label="加入原因" prop="joinReason" min-width="160" show-overflow-tooltip />
        <el-table-column label="成员状态" align="center" width="100">
          <template #default="scope">
            <dict-tag
              v-if="scope.row.memberStatus"
              :options="talent_pool_member_status"
              :value="scope.row.memberStatus"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="下次联系" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.nextContactTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="加入人" align="center" width="110">
          <template #default="scope">{{ scope.row.joinedByName || scope.row.joinedBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="加入时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.joinedTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="90" fixed="right">
          <template #default="scope">
            <el-tooltip content="移出（只结束成员关系）" placement="top">
              <el-button
                v-hasPermi="['talent:pool:member']"
                link
                type="danger"
                icon="Delete"
                @click="removePoolMemberRow(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="poolMemberDialog.total > 0"
        v-model:page="poolMemberQuery.pageNum"
        v-model:limit="poolMemberQuery.pageSize"
        :total="poolMemberDialog.total"
        @pagination="loadPoolMembers"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="poolMemberDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 加入人才池 -->
    <el-dialog v-model="poolMemberFormDialog.visible" title="加入人才池" width="720px" append-to-body>
      <el-form ref="poolMemberFormRef" :model="poolMemberForm" :rules="poolMemberRules" label-width="110px">
        <el-form-item label="人才" prop="talentId">
          <el-input v-model="poolMemberForm.talentId" placeholder="人才主档ID（可点右侧选择）" readonly>
            <template #append>
              <el-button icon="Search" @click="openTalentPicker('poolMember')" />
            </template>
          </el-input>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="适配等级" prop="fitLevel">
              <el-input v-model="poolMemberForm.fitLevel" placeholder="如 high/medium/low" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成员状态" prop="memberStatus">
              <el-select v-model="poolMemberForm.memberStatus" placeholder="缺省在池" clearable style="width: 100%">
                <el-option
                  v-for="dict in talent_pool_member_status"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="推荐岗位" prop="recommendedJob">
          <el-input v-model="poolMemberForm.recommendedJob" maxlength="200" show-word-limit placeholder="可为空" />
        </el-form-item>
        <el-form-item label="下次联系时间" prop="nextContactTime">
          <el-date-picker
            v-model="poolMemberForm.nextContactTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="可为空"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="加入原因" prop="joinReason">
          <el-input v-model="poolMemberForm.joinReason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="poolMemberForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="poolMemberFormDialog.submitting" @click="submitPoolMember">确 定</el-button>
          <el-button @click="poolMemberFormDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 分组 新增 / 编辑 -->
    <el-dialog v-model="groupDialog.visible" :title="groupDialog.title" width="720px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="公共分组需要人才池管理员权限维护；个人收藏仅本人可见，且不改变人才数据权限。"
      />
      <el-form ref="groupFormRef" :model="groupForm" :rules="groupRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分组名称" prop="groupName">
              <el-input v-model="groupForm.groupName" placeholder="请输入（必填，最长 128）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分组类型" prop="groupType">
              <el-select v-model="groupForm.groupType" placeholder="请选择" style="width: 100%">
                <el-option v-for="dict in talent_group_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="负责人/收藏人" prop="ownerId">
              <el-input v-model="groupForm.ownerId" placeholder="留空由服务端取登录用户" clearable>
                <template #append>
                  <el-button icon="User" @click="groupOwnerSelectRef?.open()" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="归属部门ID" prop="ownerDeptId">
              <el-input v-model="groupForm.ownerDeptId" placeholder="公共分组维护部门" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="可见范围" prop="visibilityType">
              <el-select v-model="groupForm.visibilityType" placeholder="请选择" clearable style="width: 100%">
                <el-option
                  v-for="dict in talent_visibility_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-input v-model="groupForm.status" placeholder="如 active/inactive" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="groupForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="groupDialog.submitting" @click="submitGroup">确 定</el-button>
          <el-button @click="groupDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 分组成员管理 -->
    <el-dialog v-model="groupMemberDialog.visible" title="分组成员" width="900px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="info"
        :closable="false"
        show-icon
        title="移出分组只结束关系，不删除人才主档；重复加入返回已有关系。"
      />
      <div class="tab-toolbar">
        <el-button type="primary" size="small" icon="Plus" @click="openAddGroupMember">加入人才</el-button>
      </div>
      <el-table v-loading="groupMemberDialog.loading" border size="small" :data="groupMemberList">
        <el-table-column label="人才编号" align="center" prop="talentNo" width="150" show-overflow-tooltip />
        <el-table-column label="姓名" align="center" prop="talentName" width="110" />
        <el-table-column label="加入人" align="center" width="120">
          <template #default="scope">{{ scope.row.addedByName || scope.row.addedBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="加入时间" align="center" width="170">
          <template #default="scope">{{ parseTime(scope.row.addedTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="90" fixed="right">
          <template #default="scope">
            <el-tooltip content="移出（只结束关系）" placement="top">
              <el-button
                v-hasPermi="['talent:pool:member']"
                link
                type="danger"
                icon="Delete"
                @click="removeGroupMemberRow(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="groupMemberDialog.total > 0"
        v-model:page="groupMemberQuery.pageNum"
        v-model:limit="groupMemberQuery.pageSize"
        :total="groupMemberDialog.total"
        @pagination="loadGroupMembers"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="groupMemberDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 标签字典 新增 / 编辑 -->
    <el-dialog v-model="tagDialog.visible" :title="tagDialog.title" width="640px" append-to-body>
      <el-form ref="tagFormRef" :model="tagForm" :rules="tagRules" label-width="110px">
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="tagForm.tagName" placeholder="请输入（禁止敏感或歧视性名称）" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="标签类别" prop="tagCategory">
              <el-select v-model="tagForm.tagCategory" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="dict in talent_tag_category" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否敏感" prop="sensitiveFlag">
              <el-select v-model="tagForm.sensitiveFlag" placeholder="请选择" clearable style="width: 100%">
                <el-option label="敏感（普通用户不可检索）" value="1" />
                <el-option label="普通" value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序号" prop="sortNo">
              <el-input-number v-model="tagForm.sortNo" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-input v-model="tagForm.status" placeholder="如 active/disabled" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="tagForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="tagDialog.submitting" @click="submitTag">确 定</el-button>
          <el-button @click="tagDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 人才标签挂载（全量覆盖语义） -->
    <el-dialog v-model="tagMountDialog.visible" title="人才标签挂载" width="720px" append-to-body>
      <el-alert
        class="dialog-alert"
        type="warning"
        :closable="false"
        show-icon
        title="保存为全量覆盖语义：未勾选的标签会从该人才身上移除。敏感标签（背调失败/健康/家庭/年龄等）不得作为普通可检索标签挂载（设计 §7.6.4、§8.15）。"
      />
      <el-form label-width="110px">
        <el-form-item label="人才">
          <el-input v-model="tagMountDialog.talentId" placeholder="人才主档ID" readonly>
            <template #append>
              <el-button icon="Search" @click="openTalentPicker('tagMount')" />
            </template>
          </el-input>
          <span v-if="tagMountDialog.talentLabel" class="form-tip">已选择：{{ tagMountDialog.talentLabel }}</span>
        </el-form-item>
        <el-form-item label="标签">
          <el-checkbox-group v-model="tagMountDialog.tagIds" class="tag-selector">
            <el-checkbox v-for="tag in tagMountDialog.options" :key="String(tag.tagId)" :value="String(tag.tagId)">
              {{ tag.tagName }}
              <el-tag v-if="tag.sensitiveFlag === '1'" size="small" type="danger" class="tag-flag">敏感</el-tag>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button
            v-hasPermi="['talent:profile:edit']"
            type="primary"
            :loading="tagMountDialog.loading"
            :disabled="!tagMountDialog.talentId"
            @click="submitTagMount"
          >
            确 定
          </el-button>
          <el-button @click="tagMountDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 人才选择（加入池成员 / 加入分组成员 / 标签挂载共用） -->
    <el-dialog v-model="talentPicker.visible" title="选择人才" width="860px" append-to-body>
      <el-form :inline="true">
        <el-form-item label="姓名">
          <el-input v-model="talentPicker.name" placeholder="模糊匹配" clearable @keyup.enter="searchTalent" />
        </el-form-item>
        <el-form-item label="人才编号">
          <el-input v-model="talentPicker.talentNo" placeholder="模糊匹配" clearable @keyup.enter="searchTalent" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="searchTalent">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="talentPicker.loading" border size="small" :data="talentPicker.rows">
        <el-table-column label="人才编号" align="center" prop="talentNo" width="150" show-overflow-tooltip />
        <el-table-column label="姓名" align="center" prop="name" width="100" />
        <el-table-column label="电话（脱敏）" align="center" prop="phoneMasked" width="130" />
        <el-table-column label="当前公司" prop="currentCompany" min-width="150" show-overflow-tooltip />
        <el-table-column label="意向岗位" prop="expectedPosition" min-width="130" show-overflow-tooltip />
        <el-table-column label="人才状态" align="center" width="100">
          <template #default="scope">
            <dict-tag v-if="scope.row.talentStatus" :options="talent_status" :value="scope.row.talentStatus" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="80" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="pickTalent(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="talentPicker.total > 0"
        v-model:page="talentPicker.pageNum"
        v-model:limit="talentPicker.pageSize"
        :total="talentPicker.total"
        @pagination="searchTalent"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="talentPicker.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <UserSelect ref="poolManagerSelectRef" :multiple="false" @confirm-call-back="handlePoolManagerSelected" />
    <UserSelect ref="groupOwnerSelectRef" :multiple="false" @confirm-call-back="handleGroupOwnerSelected" />
  </div>
</template>

<script setup lang="ts">
import {
  addGroup,
  addGroupMember,
  addPool,
  addPoolMember,
  addTag,
  delGroup,
  delPool,
  delTag,
  getGroup,
  getPool,
  listGroup,
  listGroupMember,
  listPool,
  listPoolMember,
  listTag,
  removeGroupMember,
  removePoolMember,
  updateGroup,
  updatePool,
  updateTag
} from '@/api/hrtalent/pool';
import type {
  HrTalentGroupForm,
  HrTalentGroupMemberVO,
  HrTalentGroupQuery,
  HrTalentGroupVO,
  HrTalentPoolForm,
  HrTalentPoolMemberForm,
  HrTalentPoolMemberVO,
  HrTalentPoolQuery,
  HrTalentPoolVO,
  HrTalentTagForm,
  HrTalentTagQuery,
  HrTalentTagVO
} from '@/api/hrtalent/pool/types';
import { listProfile, listProfileTags, updateProfileTags } from '@/api/hrtalent/profile';
import type { HrTalentProfileVO } from '@/api/hrtalent/profile/types';
import UserSelect from '@/components/UserSelect/index.vue';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'HrTalentPool' });

const { talent_visibility_type, talent_pool_member_status, talent_tag_category, talent_group_type, talent_status } =
  toRefs<any>(
    useDict(
      'talent_visibility_type',
      'talent_pool_member_status',
      'talent_tag_category',
      'talent_group_type',
      'talent_status'
    )
  );

const activeTab = ref('pool');
const poolManagerSelectRef = ref<InstanceType<typeof UserSelect>>();
const groupOwnerSelectRef = ref<InstanceType<typeof UserSelect>>();

/* ------------------------------ 人才池 ------------------------------ */

const poolList = ref<HrTalentPoolVO[]>([]);
const poolLoading = ref(false);
const poolTotal = ref(0);
const poolFormRef = ref<ElFormInstance>();
const poolQuery = reactive<HrTalentPoolQuery>({
  pageNum: 1,
  pageSize: 10,
  poolName: undefined,
  poolCode: undefined,
  poolType: undefined,
  managerId: undefined,
  ownerDeptId: undefined,
  visibilityType: undefined,
  status: undefined
});
const poolDialog = reactive<{ visible: boolean; title: string; submitting: boolean }>({
  visible: false,
  title: '',
  submitting: false
});
const initPoolForm: HrTalentPoolForm = {
  poolId: undefined,
  poolName: '',
  poolType: '',
  ownerDeptId: undefined,
  ownerDeptName: '',
  managerId: undefined,
  visibilityType: undefined,
  poolDesc: '',
  status: 'active',
  remark: ''
};
const poolForm = ref<HrTalentPoolForm>({ ...initPoolForm });
const poolRules: ElFormRules = {
  poolName: [{ required: true, message: '人才池名称不能为空', trigger: 'blur' }]
};

const loadPool = async () => {
  poolLoading.value = true;
  try {
    const res = await listPool(poolQuery);
    poolList.value = res.data?.rows || [];
    poolTotal.value = res.data?.total || 0;
  } finally {
    poolLoading.value = false;
  }
};
const queryPool = () => {
  poolQuery.pageNum = 1;
  loadPool();
};
const resetPoolQuery = () => {
  Object.assign(poolQuery, {
    pageNum: 1,
    pageSize: 10,
    poolName: undefined,
    poolCode: undefined,
    poolType: undefined,
    managerId: undefined,
    ownerDeptId: undefined,
    visibilityType: undefined,
    status: undefined
  });
  loadPool();
};
const openPoolDialog = async (row?: HrTalentPoolVO) => {
  poolForm.value = { ...initPoolForm };
  if (row?.poolId) {
    const res = await getPool(row.poolId);
    Object.assign(poolForm.value, res.data || row);
    poolDialog.title = '修改人才池';
  } else {
    poolDialog.title = '新增人才池';
  }
  poolDialog.visible = true;
};
const submitPool = () => {
  poolFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    poolDialog.submitting = true;
    try {
      if (poolForm.value.poolId) {
        await updatePool(poolForm.value);
        modal.msgSuccess('修改成功');
      } else {
        await addPool(poolForm.value);
        modal.msgSuccess('新增成功');
      }
      poolDialog.visible = false;
      await loadPool();
    } finally {
      poolDialog.submitting = false;
    }
  });
};
const removePool = async (row: HrTalentPoolVO) => {
  try {
    await modal.confirm('是否确认删除该人才池？只会结束成员关系，不会删除任何人才主档。');
  } catch {
    return;
  }
  await delPool(row.poolId!);
  modal.msgSuccess('删除成功');
  await loadPool();
};
const handlePoolManagerSelected = (users: any[]) => {
  const user = users?.[0];
  if (user) {
    poolForm.value.managerId = user.userId;
  }
};

/* ------------------------------ 池成员 ------------------------------ */

const poolMemberList = ref<HrTalentPoolMemberVO[]>([]);
const poolMemberDialog = reactive<{ visible: boolean; loading: boolean; total: number; poolId?: string | number }>({
  visible: false,
  loading: false,
  total: 0,
  poolId: undefined
});
const poolMemberQuery = reactive<{ pageNum: number; pageSize: number; memberStatus?: string }>({
  pageNum: 1,
  pageSize: 10,
  memberStatus: undefined
});
const poolMemberFormRef = ref<ElFormInstance>();
const poolMemberFormDialog = reactive<{ visible: boolean; submitting: boolean }>({ visible: false, submitting: false });
const initPoolMemberForm: HrTalentPoolMemberForm = {
  talentId: undefined,
  fitLevel: '',
  recommendedJob: '',
  joinReason: '',
  nextContactTime: undefined,
  memberStatus: 'active',
  remark: ''
};
const poolMemberForm = ref<HrTalentPoolMemberForm>({ ...initPoolMemberForm });
const poolMemberRules: ElFormRules = {
  talentId: [{ required: true, message: '请选择要加入人才池的人才', trigger: 'change' }]
};

const openPoolMembers = async (row: HrTalentPoolVO) => {
  poolMemberDialog.poolId = row.poolId;
  poolMemberDialog.visible = true;
  poolMemberQuery.pageNum = 1;
  poolMemberQuery.memberStatus = undefined;
  await loadPoolMembers();
};
const loadPoolMembers = async () => {
  if (!poolMemberDialog.poolId) {
    return;
  }
  poolMemberDialog.loading = true;
  try {
    const res = await listPoolMember(poolMemberDialog.poolId, { ...poolMemberQuery });
    poolMemberList.value = res.data?.rows || [];
    poolMemberDialog.total = res.data?.total || 0;
  } finally {
    poolMemberDialog.loading = false;
  }
};
const openAddPoolMember = () => {
  poolMemberForm.value = { ...initPoolMemberForm };
  poolMemberFormDialog.visible = true;
};
const submitPoolMember = () => {
  poolMemberFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    poolMemberFormDialog.submitting = true;
    try {
      // 后端幂等处理：该人才已在池中时返回**已有成员关系**而不报错，页面不做真伪判断，只如实提示
      const res = await addPoolMember(poolMemberDialog.poolId!, poolMemberForm.value);
      modal.msgSuccess(
        res.data?.memberId
          ? `加入成功（成员关系ID ${res.data.memberId}；若原本已在池中，后端会返回已有关系）`
          : '加入成功'
      );
      poolMemberFormDialog.visible = false;
      await loadPoolMembers();
      await loadPool();
    } finally {
      poolMemberFormDialog.submitting = false;
    }
  });
};
const removePoolMemberRow = async (row: HrTalentPoolMemberVO) => {
  let reason = '';
  try {
    const res: any = await modal.prompt('移出人才池只结束成员关系，请填写移出原因（可留空）');
    reason = res?.value || '';
  } catch {
    return;
  }
  await removePoolMember(poolMemberDialog.poolId!, row.memberId!, reason || undefined);
  modal.msgSuccess('已移出人才池（人才主档不受影响）');
  await loadPoolMembers();
  await loadPool();
};

/* ------------------------------ 分组 ------------------------------ */

const groupList = ref<HrTalentGroupVO[]>([]);
const groupLoading = ref(false);
const groupTotal = ref(0);
const groupFormRef = ref<ElFormInstance>();
const groupQuery = reactive<HrTalentGroupQuery>({
  pageNum: 1,
  pageSize: 10,
  groupName: undefined,
  groupType: undefined,
  ownerId: undefined,
  ownerDeptId: undefined,
  visibilityType: undefined,
  status: undefined
});
const groupDialog = reactive<{ visible: boolean; title: string; submitting: boolean }>({
  visible: false,
  title: '',
  submitting: false
});
const initGroupForm: HrTalentGroupForm = {
  groupId: undefined,
  groupName: '',
  groupType: 'personal',
  ownerDeptId: undefined,
  ownerId: undefined,
  visibilityType: undefined,
  status: 'active',
  remark: ''
};
const groupForm = ref<HrTalentGroupForm>({ ...initGroupForm });
const groupRules: ElFormRules = {
  groupName: [{ required: true, message: '分组名称不能为空', trigger: 'blur' }],
  groupType: [{ required: true, message: '分组类型不能为空', trigger: 'change' }]
};

const loadGroup = async () => {
  groupLoading.value = true;
  try {
    const res = await listGroup(groupQuery);
    groupList.value = res.data?.rows || [];
    groupTotal.value = res.data?.total || 0;
  } finally {
    groupLoading.value = false;
  }
};
const queryGroup = () => {
  groupQuery.pageNum = 1;
  loadGroup();
};
const resetGroupQuery = () => {
  Object.assign(groupQuery, {
    pageNum: 1,
    pageSize: 10,
    groupName: undefined,
    groupType: undefined,
    ownerId: undefined,
    ownerDeptId: undefined,
    visibilityType: undefined,
    status: undefined
  });
  loadGroup();
};
const openGroupDialog = async (row?: HrTalentGroupVO, forceType?: string) => {
  groupForm.value = { ...initGroupForm };
  if (row?.groupId) {
    const res = await getGroup(row.groupId);
    Object.assign(groupForm.value, res.data || row);
    groupDialog.title = '修改分组';
  } else {
    groupForm.value.groupType = forceType || 'public';
    groupDialog.title = forceType === 'personal' ? '新增个人收藏' : '新增分组';
  }
  groupDialog.visible = true;
};
const submitGroup = () => {
  groupFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    groupDialog.submitting = true;
    try {
      if (groupForm.value.groupId) {
        await updateGroup(groupForm.value);
        modal.msgSuccess('修改成功');
      } else {
        await addGroup(groupForm.value);
        modal.msgSuccess('新增成功');
      }
      groupDialog.visible = false;
      await loadGroup();
    } finally {
      groupDialog.submitting = false;
    }
  });
};
const removeGroup = async (row: HrTalentGroupVO) => {
  try {
    await modal.confirm('是否确认删除该分组？只会删除分组与成员关系，不会删除任何人才主档。');
  } catch {
    return;
  }
  await delGroup(row.groupId!);
  modal.msgSuccess('删除成功');
  await loadGroup();
};
const handleGroupOwnerSelected = (users: any[]) => {
  const user = users?.[0];
  if (user) {
    groupForm.value.ownerId = user.userId;
  }
};

/* ------------------------------ 分组成员 ------------------------------ */

const groupMemberList = ref<HrTalentGroupMemberVO[]>([]);
const groupMemberDialog = reactive<{ visible: boolean; loading: boolean; total: number; groupId?: string | number }>({
  visible: false,
  loading: false,
  total: 0,
  groupId: undefined
});
const groupMemberQuery = reactive<{ pageNum: number; pageSize: number }>({ pageNum: 1, pageSize: 10 });

const openGroupMembers = async (row: HrTalentGroupVO) => {
  groupMemberDialog.groupId = row.groupId;
  groupMemberDialog.visible = true;
  groupMemberQuery.pageNum = 1;
  await loadGroupMembers();
};
const loadGroupMembers = async () => {
  if (!groupMemberDialog.groupId) {
    return;
  }
  groupMemberDialog.loading = true;
  try {
    const res = await listGroupMember(groupMemberDialog.groupId, { ...groupMemberQuery });
    groupMemberList.value = res.data?.rows || [];
    groupMemberDialog.total = res.data?.total || 0;
  } finally {
    groupMemberDialog.loading = false;
  }
};
const openAddGroupMember = () => {
  openTalentPicker('groupMember');
};
const removeGroupMemberRow = async (row: HrTalentGroupMemberVO) => {
  try {
    await modal.confirm('移出分组只结束关系，不删除人才主档。是否继续？');
  } catch {
    return;
  }
  await removeGroupMember(groupMemberDialog.groupId!, row.memberId!);
  modal.msgSuccess('已移出分组');
  await loadGroupMembers();
  await loadGroup();
};

/* ------------------------------ 标签字典 ------------------------------ */

const tagList = ref<HrTalentTagVO[]>([]);
const tagLoading = ref(false);
const tagTotal = ref(0);
const tagFormRef = ref<ElFormInstance>();
const tagQuery = reactive<HrTalentTagQuery>({
  pageNum: 1,
  pageSize: 10,
  tagName: undefined,
  tagCode: undefined,
  tagCategory: undefined,
  sensitiveFlag: undefined,
  status: undefined,
  onlyEnabled: undefined
});
const tagDialog = reactive<{ visible: boolean; title: string; submitting: boolean }>({
  visible: false,
  title: '',
  submitting: false
});
const initTagForm: HrTalentTagForm = {
  tagId: undefined,
  tagName: '',
  tagCategory: undefined,
  sensitiveFlag: '0',
  sortNo: 0,
  status: 'active',
  remark: ''
};
const tagForm = ref<HrTalentTagForm>({ ...initTagForm });
const tagRules: ElFormRules = {
  tagName: [{ required: true, message: '标签名称不能为空', trigger: 'blur' }]
};

const loadTag = async () => {
  tagLoading.value = true;
  try {
    const res = await listTag(tagQuery);
    tagList.value = res.data?.rows || [];
    tagTotal.value = res.data?.total || 0;
  } finally {
    tagLoading.value = false;
  }
};
const queryTag = () => {
  tagQuery.pageNum = 1;
  loadTag();
};
const resetTagQuery = () => {
  Object.assign(tagQuery, {
    pageNum: 1,
    pageSize: 10,
    tagName: undefined,
    tagCode: undefined,
    tagCategory: undefined,
    sensitiveFlag: undefined,
    status: undefined,
    onlyEnabled: undefined
  });
  loadTag();
};
const openTagDialog = (row?: HrTalentTagVO) => {
  tagForm.value = { ...initTagForm, ...row };
  tagDialog.title = row?.tagId ? '修改标签' : '新增标签';
  tagDialog.visible = true;
};
const submitTag = () => {
  tagFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    tagDialog.submitting = true;
    try {
      if (tagForm.value.tagId) {
        await updateTag(tagForm.value);
        modal.msgSuccess('修改成功');
      } else {
        await addTag(tagForm.value);
        modal.msgSuccess('新增成功');
      }
      tagDialog.visible = false;
      await loadTag();
    } finally {
      tagDialog.submitting = false;
    }
  });
};
const removeTag = async (row: HrTalentTagVO) => {
  try {
    await modal.confirm('是否确认删除该标签？删除为逻辑删除，已挂载的人才标签关系按后端规则失效。');
  } catch {
    return;
  }
  await delTag(row.tagId!);
  modal.msgSuccess('删除成功');
  await loadTag();
};

/* ------------------------------ 人才标签挂载 ------------------------------ */

const tagMountDialog = reactive<{
  visible: boolean;
  loading: boolean;
  talentId?: string | number;
  talentLabel: string;
  tagIds: string[];
  options: HrTalentTagVO[];
}>({ visible: false, loading: false, talentId: undefined, talentLabel: '', tagIds: [], options: [] });

const openTagMount = () => {
  tagMountDialog.talentId = undefined;
  tagMountDialog.talentLabel = '';
  tagMountDialog.tagIds = [];
  openTalentPicker('tagMount');
};

/** 载入该人才当前标签 + 全部启用标签字典 */
const loadTagMountData = async (talentId: string | number) => {
  tagMountDialog.loading = true;
  try {
    const [tagRes, mountedRes] = await Promise.all([
      listTag({ pageNum: 1, pageSize: 200, onlyEnabled: true }),
      listProfileTags(talentId)
    ]);
    tagMountDialog.options = tagRes.data?.rows || [];
    tagMountDialog.tagIds = (mountedRes.data || []).map(tag => String(tag.tagId));
    tagMountDialog.visible = true;
  } finally {
    tagMountDialog.loading = false;
  }
};

const submitTagMount = async () => {
  tagMountDialog.loading = true;
  updateProfileTags(tagMountDialog.talentId!, { tagIds: tagMountDialog.tagIds, sourceType: 'manual' })
    .then(() => {
      modal.msgSuccess('标签已更新');
      tagMountDialog.visible = false;
    })
    .finally(() => {
      tagMountDialog.loading = false;
    });
};

/* ------------------------------ 人才选择弹窗 ------------------------------ */

const talentPicker = reactive<{
  visible: boolean;
  loading: boolean;
  total: number;
  pageNum: number;
  pageSize: number;
  name?: string;
  talentNo?: string;
  /** 人才列表数据 */
  rows: HrTalentProfileVO[];
  /** 选择结果的用途：加入池成员 / 加入分组成员 / 人才标签挂载 */
  purpose: 'poolMember' | 'groupMember' | 'tagMount';
}>({
  visible: false,
  loading: false,
  total: 0,
  pageNum: 1,
  pageSize: 10,
  name: undefined,
  talentNo: undefined,
  rows: [],
  purpose: 'poolMember'
});

const openTalentPicker = (purpose: 'poolMember' | 'groupMember' | 'tagMount') => {
  talentPicker.purpose = purpose;
  talentPicker.name = undefined;
  talentPicker.talentNo = undefined;
  talentPicker.pageNum = 1;
  talentPicker.visible = true;
  searchTalent();
};
const searchTalent = async () => {
  talentPicker.loading = true;
  try {
    const res = await listProfile({
      pageNum: talentPicker.pageNum,
      pageSize: talentPicker.pageSize,
      name: talentPicker.name,
      talentNo: talentPicker.talentNo
    });
    talentPicker.rows = res.data?.rows || [];
    talentPicker.total = res.data?.total || 0;
  } finally {
    talentPicker.loading = false;
  }
};
const pickTalent = (row: HrTalentProfileVO) => {
  if (talentPicker.purpose === 'poolMember') {
    poolMemberForm.value.talentId = row.talentId;
    talentPicker.visible = false;
  } else if (talentPicker.purpose === 'groupMember') {
    talentPicker.visible = false;
    addGroupMemberWithTalent(row.talentId!);
  } else {
    talentPicker.visible = false;
    tagMountDialog.talentId = row.talentId;
    tagMountDialog.talentLabel = `${row.name || '-'}（${row.talentNo || '-'}）`;
    loadTagMountData(row.talentId!);
  }
};

/** 分组成员加入：后端 talentId 为 query 参数 */
const addGroupMemberWithTalent = async (talentId: string | number) => {
  const res = await addGroupMember(groupMemberDialog.groupId!, talentId);
  modal.msgSuccess(res.data?.memberId ? `已加入分组（成员关系ID ${res.data.memberId}）` : '加入成功');
  await loadGroupMembers();
  await loadGroup();
};

const handleTabChange = (name: string) => {
  if (name === 'pool') {
    loadPool();
  } else if (name === 'group') {
    loadGroup();
  } else {
    loadTag();
  }
};

onMounted(() => {
  loadPool();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.pool-shell {
  :deep(.el-tabs__content) {
    overflow: visible;
  }
}

.dialog-alert {
  margin-bottom: 12px;
}

.tab-toolbar {
  margin-bottom: 8px;
}

.form-tip {
  font-size: 12px;
  color: var(--app-text-muted);
}

.tag-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.tag-flag {
  margin-left: 4px;
}
</style>
