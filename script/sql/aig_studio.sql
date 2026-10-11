-- ------------------------------------------------------------------
-- 岗位 Agent Studio（训练台）——草稿 / 修订 / 测试证据 三张表
--
-- 依据：《岗位 AI 工作台与 Agent 训练台集成详细设计 V1.1》专题 C（C8 建议的数据模型增量），
--   用户 2026-10-10 拍板：**先做专题 C（Agent Studio）**，Scenario 建轻量表、Execution/Artifact
--   复用既有（本脚本只落 Studio 自己的三张表）。
--
-- 为什么必须有这三张表（而不是直接改 aig_agent_version）：
--   现有发布语义是 **版本不可变**：aig_agent_version 一旦发布（STABLE）就不允许原地改。
--   而训练台的本质是"反复编辑、反复试、随时回退"，把它写成对已发布版本的 UPDATE，
--   等于让"线上正在跑的能力"随一次试改而漂移——没有审计、也回不去。
--   所以草稿(Draft)/修订(Revision)必须与正式版本分离，二者之间只经"提交(submit)"单向转换。
--
-- 为什么 hash 要单独存列：
--   页面上的「未发布改动」判据必须是 `draft.content_hash != draft.last_published_hash`
--   （服务端事实），不能是前端一个布尔量——否则刷新页面就丢、或误报"没有改动"。
--
-- 为什么测试证据要钉到 revision 而不是 draft：
--   测试的对象必须是**某一刻的不可变内容**。只记 draft_id 的话，测完又改了草稿，
--   证据就指向了一段从未被测过的内容。所以 aig_studio_execution_link 记 revision_id + content_hash。
--
-- 本仓约定（照现有 DDL）：
--   * 主键 BIGINT 由 MyBatis-Plus 雪花生成，**没有 AUTO_INCREMENT**——裸 SQL 插入必须显式给 ID；
--   * 复杂 JSON 一律 longtext（与 aig_agent_version.input_schema 等同口径），不用 MySQL JSON 类型；
--   * 审计列 create_dept/create_by/create_time/update_by/update_time 由 BaseEntity 自动填充；
--   * 逻辑删除用 del_flag。
--
-- 幂等：create table if not exists（本文件只建新表，不含 ALTER，可在存量库直接重放）。
-- ------------------------------------------------------------------

-- ----------------------------
-- 1、训练草稿（可编辑工作区，独立于 aig_agent_version）
-- ----------------------------
create table if not exists aig_studio_draft (
    draft_id            bigint(20)    not null                   comment '训练草稿ID',
    agent_id            bigint(20)    default null               comment '关联的 Agent 定义（aig_agent.agent_id）；从零创建时为空',
    agent_code          varchar(64)   not null                   comment '训练对象编码（跨版本稳定；与 aig_agent.agent_code 对齐，从零创建时由建设人员指定）',
    org_id              bigint(20)    default null               comment '归属组织（部门ID；空=集团级）',
    owner_id            bigint(20)    not null                   comment '责任人（授权建设人员；多组织下同一对象各自持有草稿）',
    latest_revision     int(11)       not null default 0         comment '最新修订号（0=还没保存过内容）；编辑必须带 expectedRevision 做 CAS',
    content_json        longtext      not null                   comment '草稿内容（结构化：角色定位/Prompt 分节/输入输出引用/工具与知识声明；Schema 见 AigStudioDraftContent）',
    content_hash        char(64)      not null                   comment '内容哈希（对规范化 JSON 算 sha256；「未发布改动」判据的一半）',
    last_published_hash char(64)      default null               comment '最近一次已提交/发布时的内容哈希（与 content_hash 不等即有未发布改动；为空=从未提交）',
    agent_version_id    bigint(20)    default null               comment '最近一次由本草稿提交产生的 aig_agent_version（release_status=DRAFT）',
    status              varchar(24)   not null default 'EDITING' comment '草稿状态（EDITING编辑中 / SUBMITTED已提交 / ARCHIVED已归档）',
    del_flag            char(1)       default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept         bigint(20)    default null               comment '创建部门',
    create_by           bigint(20)    default null               comment '创建者',
    create_time         datetime                                 comment '创建时间',
    update_by           bigint(20)    default null               comment '更新者',
    update_time         datetime                                 comment '更新时间',
    remark              varchar(500)  default null               comment '备注',
    primary key (draft_id),
    key idx_aig_studio_draft_owner (org_id, owner_id, update_time),
    key idx_aig_studio_draft_agent (agent_code, status, del_flag)
) engine=innodb comment = '岗位 Agent Studio 训练草稿（专题 C，独立于已发布版本）';

-- ----------------------------
-- 2、草稿修订（每次有意义修改的不可变快照）
-- ----------------------------
create table if not exists aig_studio_revision (
    revision_id           bigint(20)   not null                 comment '修订ID',
    draft_id              bigint(20)   not null                 comment '所属草稿',
    revision_no           int(11)      not null                 comment '修订号（草稿内自增，从 1 开始；与 draft.latest_revision 对齐）',
    content_snapshot_json longtext     not null                 comment '该修订的完整内容快照（不可变；Hash 与内容一并保存，改动即新修订）',
    content_hash          char(64)     not null                 comment '内容哈希（sha256 规范化 JSON）',
    author_id             bigint(20)   not null                 comment '修订人',
    source                varchar(24)  not null default 'MANUAL' comment '修订来源（MANUAL人工编辑 / COPILOT_ACCEPTED采纳培训助手提案 / IMPORT_PACKAGE从包导入 / ROLLBACK回滚到旧修订）',
    summary               varchar(200) default null             comment '修订说明（页面上那行"改了什么"）',
    del_flag              char(1)      default '0'              comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)   default null             comment '创建部门',
    create_by             bigint(20)   default null             comment '创建者',
    create_time           datetime                              comment '创建时间',
    update_by             bigint(20)   default null             comment '更新者',
    update_time           datetime                              comment '更新时间',
    primary key (revision_id),
    unique key uk_aig_studio_revision (draft_id, revision_no),
    key idx_aig_studio_revision_hash (content_hash)
) engine=innodb comment = '岗位 Agent Studio 草稿修订（不可变快照，支持 Diff 与回滚）';

-- ----------------------------
-- 3、测试证据链（把"测过"钉到精确的不可变内容上）
-- ----------------------------
create table if not exists aig_studio_execution_link (
    link_id          bigint(20)   not null                  comment '关联ID',
    draft_id         bigint(20)   not null                  comment '所属草稿（便于按草稿聚合测试记录）',
    revision_id      bigint(20)   not null                  comment '被测试的修订（测试对象是不可变快照，不是"当前草稿"）',
    content_hash     char(64)     not null                  comment '测试时该修订的内容哈希（冗余留档：即使修订被逻辑删除也能核对"当时测的就是这份内容"）',
    agent_version_id bigint(20)   default null              comment '若本次测试用的是已提交的 Agent 版本，记下它；用草稿快照测试时为空',
    execution_id     bigint(20)   default null              comment '真实一次执行的任务ID（aig_task.task_id）；未接线时为空',
    eval_run_id      bigint(20)   default null              comment '黄金用例运行ID（aig_evaluation_run.eval_run_id）；未跑评测时为空',
    trace_id         varchar(64)  default null              comment '调用链追踪ID（关联 aig_invocation_audit.trace_id）',
    test_status      varchar(24)  not null default 'PENDING' comment '测试状态（PENDING待执行 / RUNNING执行中 / SUCCEEDED通过 / FAILED失败）',
    result_digest    char(64)     default null              comment '结果摘要哈希（对输出算 sha256；用于"同一输入不同输出"的可核对性）',
    error_message    varchar(500) default null              comment '失败原因（用户可读；不得含密钥与受限原文）',
    del_flag         char(1)      default '0'               comment '删除标志（0代表存在 1代表删除）',
    create_dept      bigint(20)   default null              comment '创建部门',
    create_by        bigint(20)   default null              comment '创建者',
    create_time      datetime                              comment '创建时间',
    update_by        bigint(20)   default null              comment '更新者',
    update_time      datetime                              comment '更新时间',
    primary key (link_id),
    key idx_aig_studio_link_revision (revision_id),
    key idx_aig_studio_link_draft (draft_id, test_status),
    key idx_aig_studio_link_exec (execution_id)
) engine=innodb comment = '岗位 Agent Studio 测试证据链（修订快照 ↔ 真实执行/评测）';

-- ----------------------------
-- 4、变更提案（培训助手只"提案"，人工采纳才产生新修订；附件 07 增量 6）
-- ----------------------------
create table if not exists aig_studio_change_proposal (
    proposal_id         bigint(20)    not null                   comment '提案ID',
    draft_id            bigint(20)    not null                   comment '所属草稿（aig_studio_draft.draft_id）',
    base_revision       int(11)       not null                   comment '提案基于的修订号（CAS：采纳时必须仍是当前修订）',
    source              varchar(24)   not null default 'COPILOT' comment '提案来源（COPILOT培训助手 / MANUAL人工草拟）',
    operations_json     longtext      not null                   comment '规范化后的操作清单（白名单操作 + JSON Pointer）',
    preview_json        longtext      default null               comment '改动预览（path/before/after；供人工审阅）',
    content_hash_before char(64)      not null                   comment '提案时的草稿内容哈希',
    content_hash_after  char(64)      not null                   comment '应用后预计的内容哈希（采纳时重算比对）',
    status              varchar(16)   not null default 'PENDING' comment '状态（PENDING待人工处理 / APPLIED已采纳 / REJECTED已拒绝）',
    decided_by          bigint(20)    default null               comment '处理人（采纳/拒绝）',
    decided_at          datetime      default null               comment '处理时间',
    decided_reason      varchar(200)  default null               comment '处理说明（拒绝原因；采纳时可为空）',
    summary             varchar(200)  default null               comment '提案说明',
    del_flag            char(1)       default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept         bigint(20)    default null               comment '创建部门',
    create_by           bigint(20)    default null               comment '创建者',
    create_time         datetime                                 comment '创建时间',
    update_by           bigint(20)    default null               comment '更新者',
    update_time         datetime                                 comment '更新时间',
    primary key (proposal_id),
    key idx_aig_studio_proposal_draft (draft_id, status, create_time)
) engine=innodb comment = '岗位 Agent Studio 变更提案（培训助手只提案，人工采纳才产生新修订）';

-- ----------------------------
-- 5、草案生成器三张表（Skill / 子 Agent / 快捷指令草案；附件 07 增量 7）
--
--    三类各自落表：字段确实不同（技能编码 vs 父 Agent vs 启动方式/目标），
--    合成一张表就得给每类留一半空列，而空列会让人以为"这个字段本来该填"。
--    详见 aig_studio_artifact_draft.sql 的头部说明。
-- ----------------------------
create table if not exists aig_studio_skill_draft (
    skill_draft_id      bigint(20)    not null                   comment '草案ID',
    draft_code          varchar(64)   not null                   comment '草案编码',
    draft_name          varchar(128)  default null               comment '草案名称',
    skill_code          varchar(64)   not null                   comment '技能编码（生成后落到 allowedSkills）',
    skill_name          varchar(128)  default null               comment '技能名称',
    capabilities        varchar(500)  default null               comment '技能声明的能力编码（逗号分隔；仅人读清单）',
    description         varchar(1000) default null               comment '技能做什么（进 tools_and_skills 分节）',
    usage_notes         varchar(1000) default null               comment '使用说明（进 workflow_rules 分节）',
    agent_category      varchar(32)   not null                   comment 'Agent 类别（必填）',
    provider_capability varchar(64)   not null                   comment '所需能力（必填）',
    allowed_tools       varchar(500)  default null               comment '允许工具（逗号分隔）',
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
    primary key (skill_draft_id),
    unique key uk_aig_studio_skill_draft_code (draft_code, del_flag),
    key idx_aig_studio_skill_draft_owner (owner_id, enabled, del_flag)
) engine=innodb comment = 'Agent Studio 技能草案（只产可提交内容，不等同可执行）';

create table if not exists aig_studio_child_agent_draft (
    child_agent_draft_id bigint(20)    not null                   comment '草案ID',
    draft_code           varchar(64)   not null                   comment '草案编码',
    draft_name           varchar(128)  default null               comment '草案名称',
    agent_code           varchar(64)   default null               comment 'Agent 编码（不在生成物里）',
    agent_name           varchar(128)  default null               comment 'Agent 展示名',
    agent_category       varchar(32)   not null                   comment 'Agent 类别（必填）',
    parent_agent_code    varchar(64)   default null               comment '父 Agent 编码（草稿内容无此槽位）',
    role_description     varchar(1000) default null               comment '角色定位',
    objective            varchar(1000) default null               comment '主要责任',
    prohibitions         varchar(1000) default null               comment '禁止事项',
    output_summary       varchar(1000) default null               comment '输出成果说明',
    prompt_sections_json longtext      default null               comment '主 Prompt 八分节（JSON 对象）',
    provider_capability  varchar(64)   not null                   comment '所需能力（必填）',
    allow_external       char(1)       default null               comment '是否允许外发（Y/N；缺省 N）',
    allowed_tools        varchar(500)  default null               comment '允许工具（逗号分隔）',
    knowledge_scope      varchar(500)  default null               comment '知识范围（逗号分隔）',
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

create table if not exists aig_studio_action_draft (
    action_draft_id     bigint(20)    not null                   comment '草案ID',
    draft_code          varchar(64)   not null                   comment '草案编码',
    draft_name          varchar(128)  default null               comment '草案名称',
    action_code         varchar(64)   default null               comment '指令编码',
    action_name         varchar(128)  default null               comment '指令名称',
    launch_mode         varchar(16)   default null               comment '启动方式（QUICK/FORM/STUDIO/NAVIGATION）',
    target_type         varchar(32)   default null               comment '启动目标类型',
    target_ref          varchar(128)  default null               comment '启动目标引用',
    agent_category      varchar(32)   not null                   comment 'Agent 类别（必填）',
    objective           varchar(1000) default null               comment '指令目标',
    output_contract     varchar(1000) default null               comment '产出要求',
    provider_capability varchar(64)   not null                   comment '所需能力（必填）',
    allowed_tools       varchar(500)  default null               comment '允许工具（逗号分隔）',
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

-- ----------------------------
-- 6、对话式 Copilot 对话账本（逐轮：谁说了什么、模型回了什么、产出了哪条提案；
--    追加型，无 del_flag；拒绝/失败原因必须留痕——见 aig_studio_copilot_turn.sql 头部）
-- ----------------------------
create table if not exists aig_studio_copilot_turn (
    turn_id         bigint(20)    not null                   comment '对话轮次ID',
    draft_id        bigint(20)    not null                   comment '所属训练草稿',
    revision        int(11)       default null               comment '对话基于的修订号',
    content_hash    char(64)      default null               comment '对话基于的草稿内容哈希',
    user_message    varchar(2000) not null                   comment '用户消息',
    data_level      varchar(16)   not null                   comment '本次数据等级',
    model_output    longtext      default null               comment '模型原文（截断）',
    status          varchar(16)   not null                   comment '结论（RUNNING/SUCCEEDED/REJECTED/FAILED）',
    failure_reason  varchar(500)  default null               comment '拒绝/失败原因',
    proposal_id     bigint(20)    default null               comment '产出的提案ID',
    trace_id        varchar(64)   default null               comment '网关调用链ID',
    model_key       varchar(128)  default null               comment '命中的模型键',
    deployment_type varchar(32)   default null               comment '部署类型',
    external_call   char(1)       default null               comment '是否发生外发（Y/N）',
    latency_ms      bigint(20)    default null               comment '耗时（毫秒）',
    create_dept     bigint(20)    default null               comment '创建部门',
    create_by       bigint(20)    default null               comment '创建者',
    create_time     datetime                                 comment '创建时间',
    update_by       bigint(20)    default null               comment '更新者',
    update_time     datetime                                 comment '更新时间',
    primary key (turn_id),
    key idx_aig_studio_copilot_draft (draft_id, turn_id),
    key idx_aig_studio_copilot_trace (trace_id)
) engine=innodb comment = 'Agent Studio 对话式 Copilot 对话账本（只产提案，不改草稿）';

-- 核对：八张表都在
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name in ('aig_studio_draft', 'aig_studio_revision', 'aig_studio_execution_link',
                      'aig_studio_change_proposal', 'aig_studio_skill_draft',
                      'aig_studio_child_agent_draft', 'aig_studio_action_draft',
                      'aig_studio_copilot_turn');
