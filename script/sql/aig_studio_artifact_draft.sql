-- ----------------------------------------------------------------------------
-- 岗位 Agent Studio：草案生成器三张表（附件 07 增量 7／ST-005/006/007）
--
-- 【这张/这三张表是什么】
--   附件 07 增量 7 要的是「草案生成器」：Skill / 子 Agent / 快捷指令三类草案。
--   草案的最外层价值是**只产草案、不等同可执行**（附件 §286）：生成物是一份
--   **可提交的训练台草稿内容**（aig_studio_draft 的内容模型），要不要提交、提交成什么版本
--   仍由人在训练台里决定，并走既有五道门槛（沙箱/黄金用例/人工批准/灰度）。
--
-- 【为什么三类各自落表（而不是一张表加 kind）】
--   字段确实不同：技能草案要"绑哪个技能编码、它声明了哪些能力"；子 Agent 草案要
--   "挂在哪个父 Agent 下、属于哪个类别"；快捷指令草案要"启动方式与启动目标"。
--   合成一张表就得给每类留一半空列——而空列会让人以为"这个字段本来该填"，
--   三类各自的必填项也会因此失守。
--
-- 【生成物为什么必须能过预检】
--   训练台预检按 containsKey 判"缺 Prompt 分节"，少一个键直接不通过。所以生成器一律把
--   八个分节键摆全（没内容的填空串），并校验 agentCategory 与 providerCapability
--   ——否则"可提交内容"只是一句自我感觉。
--
-- 【没进内容的字段必须说出来】
--   父 Agent（agentCode/parentAgentCode）、启动方式与启动目标（launchMode/targetType/targetRef）
--   在草稿内容模型里**没有槽位**（那是 aig_agent / aig_role_action 的列）。它们留在草案表里，
--   并由生成结果的 notes 明确告知"这一项要你去别处配"——静默丢掉比不写更糟。
--
-- 本仓约定：主键 BIGINT 由 MyBatis-Plus 雪花生成（无 AUTO_INCREMENT）；
--   长文本用 longtext；审计列由 BaseEntity 填充；逻辑删除用 del_flag。
-- 幂等：create table if not exists（只建新表，可在存量库直接重放）。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、技能草案（给 Agent 挂上一个技能编码与它的能力声明；生成物用 allowedSkills）
-- ----------------------------
create table if not exists aig_studio_skill_draft (
    skill_draft_id      bigint(20)    not null                   comment '草案ID',
    draft_code          varchar(64)   not null                   comment '草案编码（页面上认出草案的唯一方式）',
    draft_name          varchar(128)  default null               comment '草案名称',
    skill_code          varchar(64)   not null                   comment '技能编码（生成后落到草稿内容的 allowedSkills）',
    skill_name          varchar(128)  default null               comment '技能名称（展示用）',
    capabilities        varchar(500)  default null               comment '该技能声明的能力编码（逗号分隔；写进 coreCapabilities，不参与路由）',
    description         varchar(1000) default null               comment '技能做什么（进 tools_and_skills 分节）',
    usage_notes         varchar(1000) default null               comment '使用说明（进 workflow_rules 分节；可空）',
    agent_category      varchar(32)   not null                   comment 'Agent 类别（必填：决定绑定哪个实现，平台不替草案猜）',
    provider_capability varchar(64)   not null                   comment '生成的 Agent 所需能力（没有它无法路由）',
    allowed_tools       varchar(500)  default null               comment '生成的 Agent 允许使用的工具（逗号分隔；可空）',
    owner_id            bigint(20)    default null               comment '责任人',
    org_id              bigint(20)    default null               comment '归属组织',
    enabled             char(1)       not null default 'Y'       comment '是否启用（Y/N；停用的草案不能用于生成）',
    del_flag            char(1)       default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept         bigint(20)    default null               comment '创建部门',
    create_by           bigint(20)    default null               comment '创建者',
    create_time         datetime                                 comment '创建时间',
    update_by           bigint(20)    default null               comment '更新者',
    update_time         datetime                                 comment '更新时间',
    remark              varchar(500)  default null               comment '备注',
    primary key (skill_draft_id),
    unique key uk_aig_studio_skill_draft_code (draft_code, del_flag),
    key idx_aig_studio_skill_draft_owner (owner_id, enabled, del_flag)
) engine=innodb comment = 'Agent Studio 技能草案（只产可提交内容，不等同可执行）';

-- ----------------------------
-- 2、子 Agent 草案（一份完整的 Agent 定义草稿；父 Agent 归属不在内容里）
-- ----------------------------
create table if not exists aig_studio_child_agent_draft (
    child_agent_draft_id bigint(20)    not null                   comment '草案ID',
    draft_code           varchar(64)   not null                   comment '草案编码',
    draft_name           varchar(128)  default null               comment '草案名称',
    agent_code           varchar(64)   default null               comment 'Agent 编码（不在生成物里：提交用训练台草稿自身的编码）',
    agent_name           varchar(128)  default null               comment 'Agent 展示名（进内容的 agentName）',
    agent_category       varchar(32)   not null                   comment 'Agent 类别（必填）',
    parent_agent_code    varchar(64)   default null               comment '父 Agent 编码（草稿内容无此槽位；生成时进 notes 提示）',
    role_description     varchar(1000) default null               comment '角色定位（进 roleDescription）',
    objective            varchar(1000) default null               comment '主要责任（进 objective）',
    prohibitions         varchar(1000) default null               comment '禁止事项（进 prohibitions）',
    output_summary       varchar(1000) default null               comment '输出成果说明（进 outputSummary）',
    prompt_sections_json longtext      default null               comment '主 Prompt 八分节（JSON 对象：分节键 → 正文）',
    provider_capability  varchar(64)   not null                   comment '所需能力（必填）',
    allow_external       char(1)       default null               comment '是否允许外发（Y/N；缺省按 N 生成）',
    allowed_tools        varchar(500)  default null               comment '允许工具（逗号分隔；可空）',
    knowledge_scope      varchar(500)  default null               comment '知识范围（逗号分隔；可空）',
    owner_id             bigint(20)    default null               comment '责任人',
    org_id               bigint(20)    default null               comment '归属组织',
    enabled              char(1)       not null default 'Y'       comment '是否启用（Y/N）',
    del_flag             char(1)       default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept          bigint(20)    default null               comment '创建部门',
    create_by            bigint(20)    default null               comment '创建者',
    create_time          datetime                                 comment '创建时间',
    update_by            bigint(20)    default null               comment '更新者',
    update_time          datetime                                 comment '更新时间',
    remark               varchar(500)  default null               comment '备注',
    primary key (child_agent_draft_id),
    unique key uk_aig_studio_child_agent_draft_code (draft_code, del_flag),
    key idx_aig_studio_child_agent_draft_owner (owner_id, enabled, del_flag)
) engine=innodb comment = 'Agent Studio 子 Agent 草案（只产可提交内容，不等同可执行）';

-- ----------------------------
-- 3、快捷指令草案（启动方式/目标是岗位卡片配置，不在草稿内容里）
-- ----------------------------
create table if not exists aig_studio_action_draft (
    action_draft_id     bigint(20)    not null                   comment '草案ID',
    draft_code          varchar(64)   not null                   comment '草案编码',
    draft_name          varchar(128)  default null               comment '草案名称',
    action_code         varchar(64)   default null               comment '指令编码（岗位卡片上的 actionCode）',
    action_name         varchar(128)  default null               comment '指令名称',
    launch_mode         varchar(16)   default null               comment '启动方式（QUICK/FORM/STUDIO/NAVIGATION）',
    target_type         varchar(32)   default null               comment '启动目标类型（SCENARIO/QUICK_CAPABILITY/AGENT_VERSION/NAVIGATION）',
    target_ref          varchar(128)  default null               comment '启动目标引用（版本ID / 能力编码 / routeKey）',
    agent_category      varchar(32)   not null                   comment 'Agent 类别（必填：生成物是一份 Agent 内容）',
    objective           varchar(1000) default null               comment '指令目标（进 objective）',
    output_contract     varchar(1000) default null               comment '产出要求（进 output_contract 分节）',
    provider_capability varchar(64)   not null                   comment '所需能力（必填）',
    allowed_tools       varchar(500)  default null               comment '允许工具（逗号分隔；可空）',
    owner_id            bigint(20)    default null               comment '责任人',
    org_id              bigint(20)    default null               comment '归属组织',
    enabled             char(1)       not null default 'Y'       comment '是否启用（Y/N）',
    del_flag            char(1)       default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept         bigint(20)    default null               comment '创建部门',
    create_by           bigint(20)    default null               comment '创建者',
    create_time         datetime                                 comment '创建时间',
    update_by           bigint(20)    default null               comment '更新者',
    update_time         datetime                                 comment '更新时间',
    remark              varchar(500)  default null               comment '备注',
    primary key (action_draft_id),
    unique key uk_aig_studio_action_draft_code (draft_code, del_flag),
    key idx_aig_studio_action_draft_owner (owner_id, enabled, del_flag)
) engine=innodb comment = 'Agent Studio 快捷指令草案（只产可提交内容，不等同可执行）';

-- 核对：三张表都在
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name in ('aig_studio_skill_draft', 'aig_studio_child_agent_draft',
                      'aig_studio_action_draft');

select 'AIG_STUDIO_ARTIFACT_DRAFT_DDL_DONE' as marker;
