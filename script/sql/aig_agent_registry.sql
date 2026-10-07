-- ----------------------------------------------------------------------------
-- AI 治理层 · Agent / Skill / Package 注册中心（设计 §5、§6、§10.2、§13.2）—— WP3
--
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）
-- 表前缀：aig_（按 Q5 决定并入 ruoyi-ai-gov 单模块；与既有 aig_ 表同前缀）
--
-- 【为什么要有这张注册中心】设计 §5.1 的对象链是：
--   Agent Package → Agent Definition → Agent Version →（Prompt/Workflow/Output Schema、
--   Skill 绑定、Tool Policy、Provider Policy、Knowledge Scope、Evaluation Set）
-- 也就是说「Agent」不是一段代码，而是**一条被版本化、被审批、可回滚的配置记录**。
-- 仓库现状里没有这个模型（snail-ai 的 sai_agent/sai_skill 是聊天 Agent，不是受控注册中心），
-- 因此本组表是 WP3 的地基：Package、评测、灰度、回退都挂在这些表上。
--
-- 【三条状态口径，刻意分开，不要混用】
--   1. status          char(1)  —— **记录状态**（0正常 1停用）。与既有 aig_capability /
--                                  aig_model_governance 等表一致，只管「这条记录还能不能用」。
--   2. release_status  varchar  —— **发布状态机**（设计 §5.4 七态：
--                                  DRAFT→VALIDATED→SANDBOX_TESTED→CANDIDATE→STABLE→DISABLED→ARCHIVED）。
--                                  它落在 **version** 表上，因为「通过 Manifest 校验/沙箱/黄金用例/
--                                  人工批准/灰度」这些门槛都是**逐版本**过的，不是逐 Agent 过的。
--                                  父表（aig_agent/aig_skill/aig_package）刻意不带这个状态：
--                                  「这个 Agent 有没有可用版本」应由 version 表回答，
--                                  在父表再存一份必然与版本状态不一致。
--   3. release_channel varchar  —— **发布通道 / 可见范围**（谁能看到这个版本）。
--
--   ⚠️ release_channel 的取值口径**设计文档没给**（§5.3 与 §10.2 都只出现列名）。
--   本实现按 §6.3 的语义「通过后发布 Candidate，仅绑定指定品牌、部门或测试项目」定为：
--       TESTING（仅测试项目）/ BRAND（指定品牌）/ DEPT（指定部门）/ GENERAL（通用）
--   列是 varchar，若这与你的预期不符，**只改注释与 Java 枚举即可，不需要回填数据**。
--   这里不静默发明一套「看起来像标准」的枚举，而是把「设计未定义」写出来。
--
-- 【其他刻意口径】
--   · **不加外键**：与既有 aig_ 表一致（避免与平台/业务表结构耦合，跨模块搬迁时不受约束）。
--   · **声明式 Package**（Q9）：manifest_json 只承载声明（Prompt/Workflow/输出 Schema/Skill 绑定/
--     权限声明），**不含可执行代码**，因此天然不触犯 §1.3「不做任意代码执行」与 §6.2 的拒绝项；
--     §6.2 的拒绝规则（DB 直连 / Shell / SSH / Docker Socket / 未声明外网或数据等级 / 来源或校验和不明）
--     由代码在 scan_result 上落结论，本表只如实记录扫描结果与说明。
--   · **安装日志是追加型账本**（与 aig_callback 同类）：一行一个动作，不做逻辑删除、不带 del_flag，
--     否则「谁在什么时候把哪个版本放出去」这件事就可以被抹掉。
--   · manifest_hash 对**原样入库的那串字节**计算，理由同 aig_task_snapshot.snapshot_hash：
--     重算要与存储永远一致，否则「Manifest 被改过吗」这个判断会时好时坏。
--   · 幂等：全部 create table if not exists，可安全重复执行（**本文件不含任何 drop**）。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、Package 主定义（设计 §6.1 身份与来源）
-- ----------------------------
create table if not exists aig_package (
    package_id         bigint(20)      not null                   comment 'Package ID',
    package_code       varchar(64)     not null                   comment 'Package 编码（唯一，跨版本稳定）',
    package_name       varchar(128)    not null                   comment 'Package 名称',
    package_type       varchar(32)     not null                   comment '类型（AGENT/SKILL/MIXED；首期只支持声明式）',
    publisher          varchar(128)    not null                   comment '发布方（§6.1 身份）',
    license_code       varchar(64)     default null               comment '许可证（§6.2 许可证不明确即拒绝）',
    checksum           char(64)        default null               comment '包体 SHA-256（§6.2 校验和不明确即拒绝；Manifest 自身的哈希见 aig_package_version.manifest_hash——Manifest 无法声明自己的哈希，那是自指）',
    source_type        varchar(32)     default null               comment '来源类型（UPLOAD上传 / TRUSTED_SOURCE登记可信来源，§6.3-1）',
    source_ref         varchar(500)    default null               comment '来源引用（上传存储键或可信来源地址）',
    description        varchar(500)    default null               comment '说明',
    status             char(1)         default '0'                comment '记录状态（0正常 1停用）',
    del_flag           char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept        bigint(20)      default null               comment '创建部门',
    create_by          bigint(20)      default null               comment '创建者',
    create_time        datetime                                   comment '创建时间',
    update_by          bigint(20)      default null               comment '更新者',
    update_time        datetime                                   comment '更新时间',
    remark             varchar(500)    default null               comment '备注',
    primary key (package_id),
    unique key uk_aig_package_code (package_code),
    key idx_aig_package_publisher (publisher, status, del_flag)
) engine=innodb comment = 'AI Package 主定义（设计 §6.1）';

-- ----------------------------
-- 2、Package 版本（Manifest + 扫描结论 + 发布状态机）
-- ----------------------------
create table if not exists aig_package_version (
    package_version_id     bigint(20)   not null                  comment 'Package 版本ID',
    package_id             bigint(20)   not null                  comment '所属 Package',
    version                varchar(32)  not null                  comment '版本号（同 Package 内唯一）',
    manifest_json          longtext     not null                  comment 'Manifest 原文（声明式：身份/能力/依赖/权限/质量；不含可执行代码）',
    manifest_hash          char(64)     not null                  comment 'Manifest 原文 SHA-256（对原样入库字节计算，校验未被改写）',
    body_ref               varchar(500) default null              comment '包体对象键（aigov.package.store-body=true 时写入；私有前缀且不登记 sys_oss，避免持有 system:oss:download 的账号绕过模块授权取到包体；关闭开关时为空=包体未留存）',
    scan_result            varchar(16)  default null              comment '拒绝规则扫描结论（PASS/REJECT/PENDING，§6.2）',
    scan_detail            varchar(1000) default null             comment '扫描说明（拒绝时必须写明命中哪一条，不允许空泛文案）',
    release_status         varchar(24)  not null default 'DRAFT'  comment '发布状态机（DRAFT/VALIDATED/SANDBOX_TESTED/CANDIDATE/STABLE/DISABLED/ARCHIVED，§5.4）',
    release_channel        varchar(16)  not null default 'TESTING' comment '发布通道/可见范围（TESTING/BRAND/DEPT/GENERAL；设计未定义取值，见文件头）',
    sandbox_project_id     bigint(20)   default null              comment '沙箱运行所用测试项目（§6.3-4 禁止写正式资产）',
    evaluation_run_id      bigint(20)   default null              comment '最近一次评测运行ID（关联 aig_evaluation_run）',
    rollback_target_version_id bigint(20) default null            comment '回滚目标版本（§6.3-7 失败则停用或回滚）',
    approved_by            bigint(20)   default null              comment '审批人（业务Owner/AI管理员/平台管理员三方，§6.3-5）',
    approved_at            datetime     default null              comment '审批时间',
    status                 char(1)      default '0'               comment '记录状态（0正常 1停用）',
    del_flag               char(1)      default '0'               comment '删除标志（0代表存在 1代表删除）',
    create_dept            bigint(20)   default null              comment '创建部门',
    create_by              bigint(20)   default null              comment '创建者',
    create_time            datetime                               comment '创建时间',
    update_by              bigint(20)   default null              comment '更新者',
    update_time            datetime                               comment '更新时间',
    remark                 varchar(500) default null              comment '版本说明',
    primary key (package_version_id),
    unique key uk_aig_pkg_ver (package_id, version),
    key idx_aig_pkg_ver_release (release_status, release_channel, del_flag),
    key idx_aig_pkg_ver_scan (scan_result)
) engine=innodb comment = 'AI Package 版本（Manifest 与发布状态）';

-- ----------------------------
-- 3、Agent 定义（Agent Definition，设计 §5.1）
-- ----------------------------
create table if not exists aig_agent (
    agent_id           bigint(20)      not null                   comment 'Agent ID',
    agent_code         varchar(64)     not null                   comment 'Agent 编码（唯一，跨版本稳定）',
    agent_name         varchar(128)    not null                   comment 'Agent 名称',
    category           varchar(32)     not null                   comment '类别（PLANNING详情页策划 / VISUAL_DNA视觉DNA / GENERATION生成任务构建 / QA视觉QA，对应 §5.2 四个内置 Agent）',
    owner_id           bigint(20)      default null               comment '负责人（业务 Owner）',
    builtin            char(1)         not null default 'N'       comment '是否平台内置（Y=随平台发布，N=第三方 Package 带入）',
    description        varchar(500)    default null               comment '说明（做什么、不做什么）',
    status             char(1)         default '0'                comment '记录状态（0正常 1停用）',
    del_flag           char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept        bigint(20)      default null               comment '创建部门',
    create_by          bigint(20)      default null               comment '创建者',
    create_time        datetime                                   comment '创建时间',
    update_by          bigint(20)      default null               comment '更新者',
    update_time        datetime                                   comment '更新时间',
    remark             varchar(500)    default null               comment '备注',
    primary key (agent_id),
    unique key uk_aig_agent_code (agent_code),
    key idx_aig_agent_category (category, status, del_flag)
) engine=innodb comment = 'AI Agent 定义（设计 §5.1）';

-- ----------------------------
-- 4、Agent 版本（设计 §5.3 逐版本配置 + §5.4 状态机）
-- ----------------------------
create table if not exists aig_agent_version (
    agent_version_id       bigint(20)   not null                  comment 'Agent 版本ID',
    agent_id               bigint(20)   not null                  comment '所属 Agent',
    version                varchar(32)  not null                  comment '版本号（同 Agent 内唯一）',
    release_status         varchar(24)  not null default 'DRAFT'  comment '发布状态机（DRAFT/VALIDATED/SANDBOX_TESTED/CANDIDATE/STABLE/DISABLED/ARCHIVED，§5.4）',
    release_channel        varchar(16)  not null default 'TESTING' comment '发布通道/可见范围（TESTING/BRAND/DEPT/GENERAL；设计未定义取值，见文件头）',
    scenario_code          varchar(32)  default null              comment '适用业务场景（§5.3 适用 scenario_code；为空表示不限场景）',
    input_schema           longtext     default null              comment '输入 Schema（§5.3）',
    output_schema          longtext     default null              comment '输出 Schema（§5.3；结构化输出的字段定义）',
    prompt_template        longtext     default null              comment 'Prompt 模板（§5.3）',
    prompt_variables_json  varchar(2000) default null             comment 'Prompt 变量定义（§5.3）',
    config_json            longtext     default null              comment '版本配置汇总（§5.3：Workflow/知识范围/品牌范围/部门范围/角色范围/人工Gate/黄金用例/回滚目标的结构化留档）',
    allowed_tools          varchar(1000) default null             comment '允许工具清单（§5.3；逗号分隔，空前缀表示不额外声明）',
    forbidden_tools        varchar(1000) default null             comment '禁止工具清单（§5.3；§5.1 强调 Agent 调工具始终受 Tool Policy 限制）',
    provider_capability    varchar(64)  default null              comment '需要的 Provider 能力编码（§5.3；路由只认能力，不认厂商）',
    allow_external         char(1)      not null default 'N'      comment '是否允许外部调用（Y/N）；与路由策略取与，两者都允许才可能外发',
    knowledge_scope_json   varchar(2000) default null             comment '知识范围（§5.3；WP4 的知识库表落地后据此限定检索范围）',
    gate_json              varchar(1000) default null             comment '人工 Gate 配置（§5.3；哪些步骤必须人工确认）',
    package_version_id     bigint(20)   default null              comment '来源 Package 版本（内置 Agent 为空；第三方带入时指向 aig_package_version）',
    evaluation_run_id      bigint(20)   default null              comment '最近一次评测运行ID（§5.4 进 STABLE 前必须通过黄金用例）',
    rollback_target_version_id bigint(20) default null            comment '回滚目标版本（§5.3）',
    validated_at           datetime     default null              comment 'Manifest/Schema 校验通过时间（§5.4 VALIDATED）',
    sandbox_tested_at      datetime     default null              comment '沙箱运行通过时间（§5.4 SANDBOX_TESTED）',
    approved_by            bigint(20)   default null              comment '人工批准人（§5.4 只有通过人工批准才能进 STABLE）',
    approved_at            datetime     default null              comment '人工批准时间',
    status                 char(1)      default '0'               comment '记录状态（0正常 1停用）',
    del_flag               char(1)      default '0'               comment '删除标志（0代表存在 1代表删除）',
    create_dept            bigint(20)   default null              comment '创建部门',
    create_by              bigint(20)   default null              comment '创建者',
    create_time            datetime                               comment '创建时间',
    update_by              bigint(20)   default null              comment '更新者',
    update_time            datetime                               comment '更新时间',
    remark                 varchar(500) default null              comment '版本说明（§6.1 版本说明）',
    primary key (agent_version_id),
    unique key uk_aig_agent_ver (agent_id, version),
    key idx_aig_agent_ver_release (release_status, release_channel, del_flag),
    key idx_aig_agent_ver_scenario (scenario_code, release_status),
    key idx_aig_agent_ver_capability (provider_capability, allow_external)
) engine=innodb comment = 'AI Agent 版本（设计 §5.3/§5.4）';

-- ----------------------------
-- 5、Skill 定义（可复用原子能力，设计 §5.1）
-- ----------------------------
create table if not exists aig_skill (
    skill_id           bigint(20)      not null                   comment 'Skill ID',
    skill_code         varchar(64)     not null                   comment 'Skill 编码（唯一，跨版本稳定）',
    skill_name         varchar(128)    not null                   comment 'Skill 名称',
    capabilities       varchar(500)    default null               comment '能力清单（§10.2 capabilities；逗号分隔的 Provider 能力编码）',
    builtin            char(1)         not null default 'N'       comment '是否平台内置（Y/N）',
    description        varchar(500)    default null               comment '说明',
    status             char(1)         default '0'                comment '记录状态（0正常 1停用）',
    del_flag           char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept        bigint(20)      default null               comment '创建部门',
    create_by          bigint(20)      default null               comment '创建者',
    create_time        datetime                                   comment '创建时间',
    update_by          bigint(20)      default null               comment '更新者',
    update_time        datetime                                   comment '更新时间',
    remark             varchar(500)    default null               comment '备注',
    primary key (skill_id),
    unique key uk_aig_skill_code (skill_code),
    key idx_aig_skill_status (status, del_flag)
) engine=innodb comment = 'AI Skill 定义（设计 §5.1）';

-- ----------------------------
-- 6、Skill 版本（工具策略与发布状态）
-- ----------------------------
create table if not exists aig_skill_version (
    skill_version_id   bigint(20)      not null                   comment 'Skill 版本ID',
    skill_id           bigint(20)      not null                   comment '所属 Skill',
    version            varchar(32)     not null                   comment '版本号（同 Skill 内唯一）',
    release_status     varchar(24)     not null default 'DRAFT'   comment '发布状态机（DRAFT/VALIDATED/SANDBOX_TESTED/CANDIDATE/STABLE/DISABLED/ARCHIVED，§5.4）',
    release_channel    varchar(16)     not null default 'TESTING' comment '发布通道/可见范围（TESTING/BRAND/DEPT/GENERAL；设计未定义取值，见文件头）',
    config_json        longtext        default null               comment 'Skill 配置（§10.2 config_json）',
    tool_policy_json   varchar(2000)   default null               comment '工具策略（§10.2 tool_policy_json；允许/禁止工具与调用前置条件）',
    provider_capability varchar(64)    default null               comment '需要的 Provider 能力编码',
    allow_external     char(1)         not null default 'N'       comment '是否允许外部调用（Y/N）',
    package_version_id bigint(20)      default null               comment '来源 Package 版本（第三方 Package 带入时指向 aig_package_version；内置 Skill 为空；安装幂等判据用它）',
    input_schema       longtext        default null               comment '输入 Schema',
    output_schema      longtext        default null               comment '输出 Schema',
    approved_by        bigint(20)      default null               comment '人工批准人',
    approved_at        datetime        default null               comment '人工批准时间',
    status             char(1)         default '0'                comment '记录状态（0正常 1停用）',
    del_flag           char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept        bigint(20)      default null               comment '创建部门',
    create_by          bigint(20)      default null               comment '创建者',
    create_time        datetime                                   comment '创建时间',
    update_by          bigint(20)      default null               comment '更新者',
    update_time        datetime                                   comment '更新时间',
    remark             varchar(500)    default null               comment '版本说明',
    primary key (skill_version_id),
    unique key uk_aig_skill_ver (skill_id, version),
    key idx_aig_skill_ver_release (release_status, release_channel, del_flag)
) engine=innodb comment = 'AI Skill 版本（工具策略与发布状态）';

-- ----------------------------
-- 7、Agent 版本绑定（设计 §10.2 + §6.3-6 灰度范围：只绑定指定品牌/部门/测试项目）
-- ----------------------------
create table if not exists aig_agent_binding (
    binding_id         bigint(20)      not null                   comment '绑定ID',
    agent_version_id   bigint(20)      not null                   comment '绑定的 Agent 版本ID',
    company_id         bigint(20)      default null               comment '限定公司（空=不限）',
    brand_id           bigint(20)      default null               comment '限定品牌（空=不限；§6.3-6 候选只绑定指定品牌）',
    scenario_code      varchar(32)     default null               comment '限定业务场景（空=不限）',
    role_scope         varchar(200)    default null               comment '限定角色（§10.2 role_scope；逗号分隔的 role_key，空=不限）',
    knowledge_scope    varchar(500)    default null               comment '知识范围限定（§10.2 knowledge_scope；空=不做额外收窄）',
    release_channel    varchar(16)     not null default 'TESTING' comment '绑定所属发布通道（须与版本通道一致，便于按通道查询）',
    enabled            char(1)         not null default 'Y'       comment '是否启用（Y/N）',
    effective_from     datetime        default null               comment '生效开始',
    effective_to       datetime        default null               comment '生效结束',
    del_flag           char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept        bigint(20)      default null               comment '创建部门',
    create_by          bigint(20)      default null               comment '创建者',
    create_time        datetime                                   comment '创建时间',
    update_by          bigint(20)      default null               comment '更新者',
    update_time        datetime                                   comment '更新时间',
    remark             varchar(500)    default null               comment '备注',
    primary key (binding_id),
    key idx_aig_agent_bind_ver (agent_version_id, enabled, del_flag),
    key idx_aig_agent_bind_scope (company_id, brand_id, scenario_code)
) engine=innodb comment = 'AI Agent 版本绑定（公司/品牌/场景/角色范围）';

-- ----------------------------
-- 8、Package 安装日志（追加型账本：一行一个动作）
-- ----------------------------
create table if not exists aig_package_install_log (
    log_id             bigint(20)      not null                   comment '日志ID',
    package_version_id bigint(20)      not null                   comment 'Package 版本ID',
    action             varchar(32)     not null                   comment '动作（UPLOAD/SCAN/SANDBOX/RUN_CASES/APPROVE/PUBLISH_CANDIDATE/PUBLISH_STABLE/DISABLE/ROLLBACK）',
    operator_id        bigint(20)      default null               comment '操作人（系统触发为空）',
    result             varchar(16)     not null                   comment '结果（PASS/FAIL/REJECT）',
    detail             varchar(1000)   default null               comment '说明（拒绝/失败时必须写明原因；安装链路是审计对象）',
    evidence_ref       varchar(500)    default null               comment '证据引用（评测报告/沙箱日志的对象键，不存副本）',
    operate_time       datetime        not null                   comment '操作时间',
    primary key (log_id),
    key idx_aig_pkg_log_ver (package_version_id, operate_time),
    key idx_aig_pkg_log_action (action, result)
) engine=innodb comment = 'AI Package 安装日志（追加型账本）';

-- ----------------------------
-- 9、黄金用例（设计 §13.2：三类首期用例）
-- ----------------------------
create table if not exists aig_evaluation_case (
    case_id            bigint(20)      not null                   comment '用例ID',
    case_code          varchar(64)     not null                   comment '用例编码（唯一）',
    case_name          varchar(128)    not null                   comment '用例名称',
    case_type          varchar(32)     not null                   comment '用例类型（PLAN详情页策划 / VISUAL_DNA参考图视觉分析 / IMAGE_QA图像生成与QA，§13.2 三类）',
    scenario_code      varchar(32)     default null               comment '所属业务场景',
    input_snapshot_ref varchar(500)    default null               comment '输入快照引用（对象键/业务ID；不存副本，§13.2「记录输入快照」）',
    expected_json      longtext        default null               comment '预期规则（§13.2；可判定的期望，避免只写「好/不好」）',
    rubric_json        longtext        default null               comment '人工评分 Rubric（§13.2）',
    cost_min           decimal(12,4)   default null               comment '成本范围下限（§13.2；算不出留空，禁止填0冒充）',
    cost_max           decimal(12,4)   default null               comment '成本范围上限',
    data_level         varchar(16)     not null default 'INTERNAL' comment '数据等级（沙箱运行须按此脱敏，§6.3-4）',
    classification     varchar(32)     default null               comment '用例分类（§10.3 classification）',
    status             char(1)         default '0'                comment '记录状态（0正常 1停用）',
    del_flag           char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept        bigint(20)      default null               comment '创建部门',
    create_by          bigint(20)      default null               comment '创建者',
    create_time        datetime                                   comment '创建时间',
    update_by          bigint(20)      default null               comment '更新者',
    update_time        datetime                                   comment '更新时间',
    remark             varchar(500)    default null               comment '备注',
    primary key (case_id),
    unique key uk_aig_eval_case_code (case_code),
    key idx_aig_eval_case_type (case_type, scenario_code, status)
) engine=innodb comment = 'AI 黄金用例（设计 §13.2）';

-- ----------------------------
-- 10、评测运行（打分与人工复核结论，§5.4 进 STABLE 的前置证据）
-- ----------------------------
create table if not exists aig_evaluation_run (
    run_id             bigint(20)      not null                   comment '评测运行ID',
    run_no             varchar(32)     not null                   comment '运行编号（不可猜测，对外引用用）',
    target_type        varchar(32)     not null                   comment '评测对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION，§10.3 target_type）',
    target_version_id  bigint(20)      not null                   comment '评测对象版本ID（配合 target_type 使用）',
    case_id            bigint(20)      not null                   comment '所用黄金用例ID',
    provider_id        bigint(20)      default null               comment '实际执行的 Provider（跟着实际候选走，便于按供应商复盘）',
    model_code         varchar(100)    default null               comment '实际使用的模型编码',
    external_call      char(1)         not null default 'N'       comment '本次是否发生外部调用（Y/N）',
    score_json         longtext        default null               comment '打分明细（逐项分数与依据）',
    total_score        decimal(6,2)    default null               comment '总分（算不出留空，禁止填0冒充）',
    review_result      varchar(24)     default null               comment '人工复核结论（PASS/FAIL/MANUAL）',
    reviewer_id        bigint(20)      default null               comment '复核人',
    reviewed_at        datetime        default null               comment '复核时间',
    cost_amount        decimal(12,4)   default null               comment '实际成本（算不出留空）',
    latency_ms         bigint(20)      default null               comment '端到端耗时（毫秒）',
    trace_id           varchar(64)     default null               comment '调用链ID（关联 aig_invocation_audit.trace_id）',
    result_status      varchar(16)     not null default 'RUNNING' comment '运行状态（RUNNING/PASS/FAIL/ERROR）',
    operate_time       datetime        not null                   comment '运行时间',
    del_flag           char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）；评测报告是证据，正常情况不应删除',
    create_dept        bigint(20)      default null               comment '创建部门',
    create_by          bigint(20)      default null               comment '创建者',
    create_time        datetime                                   comment '创建时间',
    update_by          bigint(20)      default null               comment '更新者',
    update_time        datetime                                   comment '更新时间',
    remark             varchar(500)    default null               comment '备注',
    primary key (run_id),
    unique key uk_aig_eval_run_no (run_no),
    key idx_aig_eval_run_target (target_type, target_version_id, result_status),
    key idx_aig_eval_run_case (case_id, operate_time)
) engine=innodb comment = 'AI 评测运行（打分与人工复核）';

-- ----------------------------
-- 11、版本发布事件账本（追加型；三类版本共用）
-- ----------------------------
-- 为什么需要它：状态机注释里写明「DISABLED → STABLE（重新启用/回滚复位）必须由服务层
-- 证明该版本**曾经 STABLE 过**」，否则「先停用再启用」就能把从未发布过的版本推成 STABLE。
-- 而 aig_package_install_log 只覆盖 Package 版本，Agent/Skill 版本没有任何发布记录 →
-- 那条不变式无从实现。本表是三类版本共用的发布账本：既作审计（谁在何时把哪个版本推到哪），
-- 也是「曾 STABLE 过」的**唯一**证据来源。
--
-- 追加型：一行一次迁移，不更新、不删除、不带 del_flag。
create table if not exists aig_release_event (
    event_id           bigint(20)      not null                   comment '发布事件ID',
    target_type        varchar(32)     not null                   comment '对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION）',
    target_version_id  bigint(20)      not null                   comment '对象版本ID',
    from_status        varchar(24)     default null               comment '源发布状态（首次记录可为空）',
    to_status          varchar(24)     not null                   comment '目标发布状态',
    passed_gates       varchar(500)    default null               comment '本次推进所依据的门槛（逗号分隔；停用/归档这类运维动作为空）',
    operator_id        bigint(20)      default null               comment '操作人（系统触发为空）',
    detail             varchar(500)    default null               comment '说明',
    operate_time       datetime        not null                   comment '操作时间',
    primary key (event_id),
    key idx_aig_release_target (target_type, target_version_id, operate_time),
    key idx_aig_release_to (to_status)
) engine=innodb comment = 'AI 版本发布事件账本（审计 + 曾 STABLE 过的证据）';

-- ----------------------------
-- 12、核对
-- ----------------------------
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name in ('aig_package', 'aig_package_version', 'aig_agent', 'aig_agent_version',
                      'aig_skill', 'aig_skill_version', 'aig_agent_binding',
                      'aig_package_install_log', 'aig_evaluation_case', 'aig_evaluation_run',
                      'aig_release_event')
 order by table_name;

select 'AIG_AGENT_REGISTRY_DDL_DONE' as marker;
