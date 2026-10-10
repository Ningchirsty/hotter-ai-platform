-- ------------------------------------------------------------------
-- 岗位工作台（主文档线）：场景包 + 岗位包 六张表（增量 1 的第一部分：数据层）
--
-- 依据：附件《岗位 AI 工作台…V1.1》§4（核心对象）、§5（Role Package 协议）、§6.1（场景最小契约）、§13（库设计增量）。
-- 用户拍板：**Scenario 建轻量表、不做引擎**（真实流程仍由创作域阶段机与视频链路执行）。
--
-- 【与既有冻结决定的对齐（不得冲突）】
--   · F-10（已冻）：Scenario/岗位的**审批与发布复用既有**——因此 scenario_version/role_version 的
--     release_status 沿用同一台发布状态机的取值（DRAFT/VALIDATED/SANDBOX_TESTED/CANDIDATE/STABLE/
--     DISABLED/ARCHIVED），**不另立一套状态、也不新造审批表**。
--   · F-05（已冻）：岗位绑定只做**可见性过滤**——只存 sys_dept 的组织ID 与 brand_id，
--     **不建第三套授权体系**；真实数据访问仍由 RuoYi 数据权限 + 治理层 org_scope 决定。
--   · F-06（未冻）：`aig_agent_binding` 今天只是发布许可证、无运行时消费方 ⇒ 岗位可见性必须自己实现。
--
-- 【为什么 action 拆表、而 category 留在 manifest_json】
--   action 要支持「按分类查卡片 / 排序 / 单独启停」，每次解析 JSON 不现实，所以拆表；
--   分类数量少、与 action 同版本、总是整份读取，留在角色版本清单（manifest_json）里即可——
--   多一张表就多一处要与版本对齐的地方。action.category_code 必须能在该版本清单里找到（服务层校验）。
--
-- 本仓约定：主键 BIGINT 由 MyBatis-Plus 雪花生成（**无 AUTO_INCREMENT**，裸 SQL 必须显式给 ID）；
--   复杂 JSON 用 longtext；审计列由 BaseEntity 自动填充；逻辑删除用 del_flag。
-- 幂等：create table if not exists（只建新表，不含 ALTER，存量库可直接重放）。
-- ------------------------------------------------------------------

-- ----------------------------
-- 一、场景包（轻量：只锁版本 + 引用；不做流程引擎）
-- ----------------------------
create table if not exists aig_scenario (
    scenario_id      bigint(20)   not null                  comment '场景ID',
    scenario_code    varchar(64)  not null                  comment '场景编码（跨版本稳定，如 COMMERCE_DETAIL_PAGE）',
    scenario_name    varchar(160) not null                  comment '场景名称',
    owner_org_code   varchar(80)  default null              comment '归属组织编码（业务归属，F-05：组织树只有 sys_dept 前两级）',
    description      varchar(500) default null              comment '说明（做什么、不做什么）',
    status           char(1)      default '0'               comment '记录状态（0正常 1停用）',
    del_flag         char(1)      default '0'               comment '删除标志（0代表存在 1代表删除）',
    create_dept      bigint(20)   default null              comment '创建部门',
    create_by        bigint(20)   default null              comment '创建者',
    create_time      datetime                               comment '创建时间',
    update_by        bigint(20)   default null              comment '更新者',
    update_time      datetime                               comment '更新时间',
    remark           varchar(500) default null              comment '备注',
    primary key (scenario_id),
    unique key uk_aig_scenario_code (scenario_code),
    key idx_aig_scenario_status (status, del_flag)
) engine=innodb comment = 'AI 场景包（轻量：只锁版本与引用，流程仍由各业务链路执行）';

create table if not exists aig_scenario_version (
    scenario_version_id  bigint(20)   not null              comment '场景版本ID',
    scenario_id          bigint(20)   not null              comment '所属场景',
    version              varchar(32)  not null              comment '版本号（同场景内唯一，SemVer 风格）',
    release_status       varchar(24)  not null default 'DRAFT' comment '发布状态（沿用既有发布状态机取值，F-10 不另立一套）',
    input_schema_ref     varchar(255) default null          comment '输入 Schema 引用（附件 §6.1 inputSchemaRef）',
    output_schema_ref    varchar(255) default null          comment '输出 Schema 引用（附件 §6.1 outputSchemaRef）',
    workflow_adapter     varchar(64)  not null              comment '流程适配器（AigScenarioAdapterEnum：指向既有业务链路，不是自建引擎）',
    route_key            varchar(64)  default null          comment '结果页跳转键（必须命中 routeKey 白名单，否则门户会给出"点进去空白"的入口）',
    ui_json              longtext     default null          comment '输入/结果 UI 描述（附件 §6.1 ui；页面定制只接受安全组件白名单）',
    required_capabilities varchar(500) default null         comment '所需能力编码（逗号分隔；用于可用性预检与发布校验）',
    quality_approvals    varchar(255) default null          comment '质量审批清单（附件 §6.1 quality.approvals，如 PRODUCT_FACTS,COPY,VISUAL,FINAL）',
    body_ref             varchar(255) default null          comment '版本内容引用（不可变制品引用，便于事后复现该版本定义）',
    published_at         datetime     default null          comment '发布时间',
    status               char(1)      default '0'           comment '记录状态（0正常 1停用）',
    del_flag             char(1)      default '0'           comment '删除标志（0代表存在 1代表删除）',
    create_dept          bigint(20)   default null          comment '创建部门',
    create_by            bigint(20)   default null          comment '创建者',
    create_time          datetime                           comment '创建时间',
    update_by            bigint(20)   default null          comment '更新者',
    update_time          datetime                           comment '更新时间',
    remark               varchar(500) default null          comment '版本说明',
    primary key (scenario_version_id),
    unique key uk_aig_scenario_ver (scenario_id, version),
    key idx_aig_scenario_ver_release (release_status, del_flag)
) engine=innodb comment = 'AI 场景包版本（锁定版本与引用；发布沿用既有状态机）';

-- ----------------------------
-- 二、岗位包（Role Package）
-- ----------------------------
create table if not exists aig_role_profile (
    role_id          bigint(20)   not null                  comment '岗位定义ID',
    role_code        varchar(80)  not null                  comment '岗位编码（唯一，跨版本稳定；**不是** sys_role 的角色）',
    role_name        varchar(160) not null                  comment '岗位名称（如"平面设计 AI 工作台"）',
    owner_org_code   varchar(80)  default null              comment '负责人组织编码',
    description      varchar(500) default null              comment '岗位简介（员工看到的说明）',
    status           char(1)      default '0'               comment '记录状态（0正常 1停用）',
    del_flag         char(1)      default '0'               comment '删除标志（0代表存在 1代表删除）',
    create_dept      bigint(20)   default null              comment '创建部门',
    create_by        bigint(20)   default null              comment '创建者',
    create_time      datetime                               comment '创建时间',
    update_by        bigint(20)   default null              comment '更新者',
    update_time      datetime                               comment '更新时间',
    remark           varchar(500) default null              comment '备注',
    primary key (role_id),
    unique key uk_aig_role_code (role_code),
    key idx_aig_role_status (status, del_flag)
) engine=innodb comment = '岗位定义（业务层可版本化对象；不替换 sys_role/aig_agent/aig_skill）';

create table if not exists aig_role_version (
    role_version_id  bigint(20)   not null                  comment '岗位版本ID',
    role_id          bigint(20)   not null                  comment '所属岗位',
    version          varchar(32)  not null                  comment '版本号（同岗位内唯一；发布后不可改，改动出新版本）',
    release_status   varchar(24)  not null default 'DRAFT'  comment '岗位发布状态（AigRoleReleaseStatusEnum：DRAFT/TESTING/PUBLISHED/DISABLED）',
    rollout_channel  varchar(16)  default 'TESTING'         comment '灰度通道（AigReleaseChannelEnum：TESTING/BRAND/DEPT/GENERAL）',
    manifest_json    longtext     not null                  comment '岗位包清单（分类/presentation/policy 等；含 category 列表，action.category_code 必须能在这里找到）',
    manifest_sha256  char(64)     not null                  comment '清单哈希（规范化 JSON 的 sha256；"未发布改动"与完整性判据）',
    published_at     datetime     default null              comment '发布时间',
    disabled_at      datetime     default null              comment '停用时间',
    status           char(1)      default '0'               comment '记录状态（0正常 1停用）',
    del_flag         char(1)      default '0'               comment '删除标志（0代表存在 1代表删除）',
    create_dept      bigint(20)   default null              comment '创建部门',
    create_by        bigint(20)   default null              comment '创建者',
    create_time      datetime                               comment '创建时间',
    update_by        bigint(20)   default null              comment '更新者',
    update_time      datetime                               comment '更新时间',
    remark           varchar(500) default null              comment '版本说明',
    primary key (role_version_id),
    unique key uk_aig_role_ver (role_id, version),
    key idx_aig_role_ver_release (role_id, release_status, del_flag)
) engine=innodb comment = '岗位版本（发布后不可变；发布状态与"能力是否 STABLE"是两回事）';

create table if not exists aig_role_action (
    action_id        bigint(20)   not null                  comment '能力卡片ID',
    role_version_id  bigint(20)   not null                  comment '所属岗位版本',
    action_code      varchar(64)  not null                  comment '卡片编码（同版本内唯一）',
    category_code    varchar(64)  not null                  comment '所属分类（必须存在于该版本 manifest_json 的 categories 里）',
    title            varchar(160) not null                  comment '卡片标题（业务名，不是 Agent 名）',
    description      varchar(500) default null              comment '卡片说明（适用事项/所需资料/输出内容）',
    launch_mode      varchar(16)  not null                  comment '启动方式（AigActionLaunchModeEnum：QUICK/FORM/STUDIO/NAVIGATION）',
    target_type      varchar(24)  not null                  comment '目标类型（AigLaunchTargetTypeEnum：SCENARIO/QUICK_CAPABILITY/NAVIGATION）',
    target_ref       varchar(255) not null                  comment '目标引用（SCENARIO 时是 scenario://code@version；NAVIGATION 时是 routeKey）',
    studio_route_key varchar(64)  default null              comment 'STUDIO 模式的专业页跳转键（必须命中 routeKey 白名单）',
    required_context varchar(255) default null              comment '所需上下文键（白名单：brandId/projectId/productId 等；不允许包定义敏感身份覆盖）',
    sort_order       int(11)      not null default 0        comment '排序（同分类内）',
    enabled          char(1)      not null default 'Y'      comment '是否启用（Y/N；单独启停一张卡片，不必改整个版本）',
    status           char(1)      default '0'               comment '记录状态（0正常 1停用）',
    del_flag         char(1)      default '0'               comment '删除标志（0代表存在 1代表删除）',
    create_dept      bigint(20)   default null              comment '创建部门',
    create_by        bigint(20)   default null              comment '创建者',
    create_time      datetime                               comment '创建时间',
    update_by        bigint(20)   default null              comment '更新者',
    update_time      datetime                               comment '更新时间',
    remark           varchar(500) default null              comment '备注',
    primary key (action_id),
    unique key uk_aig_role_action (role_version_id, action_code),
    key idx_aig_role_action_cat (role_version_id, category_code, sort_order),
    key idx_aig_role_action_enabled (role_version_id, enabled)
) engine=innodb comment = '岗位能力卡片（一个 Action 只引用一个确定的启动目标）';

create table if not exists aig_role_binding (
    binding_id       bigint(20)   not null                  comment '绑定ID',
    role_version_id  bigint(20)   not null                  comment '岗位版本',
    org_id           bigint(20)   default null              comment '组织（sys_dept.dept_id；F-05：集团/子公司=前两级）',
    brand_id         bigint(20)   default null              comment '品牌（业务主表 ID；F-05：治理层只存 ID，无外键）',
    channel          varchar(16)  not null default 'TESTING' comment '通道（AigReleaseChannelEnum；TESTING/BRAND/DEPT/GENERAL）',
    enabled          char(1)      not null default 'Y'      comment '是否生效（Y/N）',
    effective_from   datetime     default null              comment '生效起（空=立即）',
    effective_to     datetime     default null              comment '生效止（空=长期）',
    status           char(1)      default '0'               comment '记录状态（0正常 1停用）',
    del_flag         char(1)      default '0'               comment '删除标志（0代表存在 1代表删除）',
    create_dept      bigint(20)   default null              comment '创建部门',
    create_by        bigint(20)   default null              comment '创建者',
    create_time      datetime                               comment '创建时间',
    update_by        bigint(20)   default null              comment '更新者',
    update_time      datetime                               comment '更新时间',
    remark           varchar(500) default null              comment '备注',
    primary key (binding_id),
    key idx_aig_role_binding_lookup (role_version_id, org_id, enabled),
    key idx_aig_role_binding_org (org_id, brand_id, enabled)
) engine=innodb comment = '岗位可见性绑定（只是"谁能看到卡片"；真实数据权仍走既有数据权限）';

-- ----------------------------
-- 三、核对
-- ----------------------------
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name in ('aig_scenario', 'aig_scenario_version', 'aig_role_profile',
                      'aig_role_version', 'aig_role_action', 'aig_role_binding')
 order by table_name;
