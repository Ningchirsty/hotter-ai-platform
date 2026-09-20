-- ----------------------------------------------------------------------------
-- AI 模型能力接入与配置管控 —— 阶段1 治理层数据表
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）
-- 表前缀：aig_（ai governance）
-- 设计依据：《AI 模型能力接入与配置管控设计》§4 §5 §6 §9 §10
--
-- 关键口径：
--   1. 不重建模型注册与供应商管理：模型主数据仍以 snail-ai 的 sai_model_config 为准，
--      本层只通过 aig_model_governance 关联补齐治理属性（数据等级、生命周期、密钥引用等），
--      **刻意不加外键**，避免与第三方模块的表结构耦合。
--   2. 能力模板定义「做什么」，不绑定具体模型；模型选择由 aig_capability_model + aig_route_policy 决定。
--   3. 密钥一律只存「引用」（secret_ref），禁止明文。
--   4. aig_invocation_audit 为追加型审计表，不做逻辑删除（与 tl_sensitive_audit 同口径）。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、业务能力模板（设计 §5）
-- ----------------------------
drop table if exists aig_capability;
create table aig_capability (
    capability_id        bigint(20)      not null                   comment '能力ID',
    capability_code      varchar(64)     not null                   comment '能力编码，如 talent_match / brief_precheck（对外稳定契约）',
    capability_name      varchar(100)    not null                   comment '能力名称',
    biz_goal             varchar(500)    default null               comment '业务目标：该能力服务的业务结果',
    required_tags        varchar(255)    default null               comment '需要的模型能力标签，逗号分隔（TEXT/VISION/OCR/IMAGE/VIDEO/EMBEDDING/RERANK/AGENT）',
    input_schema         text                                       comment '输入 Schema（JSON），约定允许的字段与数据等级',
    output_schema        text                                       comment '输出 Schema（JSON），强制结构化字段/置信度/证据/待确认项',
    data_policy          varchar(20)     not null default 'LOCAL_FIRST' comment '数据策略（LOCAL_ONLY仅本地 LOCAL_FIRST本地优先 EXTERNAL_ALLOWED允许外部）',
    human_confirm_points varchar(500)    default null               comment '必须人工确认的结论点',
    quality_threshold    varchar(500)    default null               comment '质量阈值：格式/完整性/可信度/超时与失败处理',
    audit_level          varchar(20)     not null default 'SUMMARY' comment '审计等级（SUMMARY摘要 FULL完整输出 HASH_ONLY仅哈希）',
    status               char(1)         default '0'                comment '状态（0正常 1停用）',
    del_flag             char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept          bigint(20)      default null               comment '创建部门',
    create_by            bigint(20)      default null               comment '创建者',
    create_time          datetime                                   comment '创建时间',
    update_by            bigint(20)      default null               comment '更新者',
    update_time          datetime                                   comment '更新时间',
    remark               varchar(500)    default null               comment '备注',
    primary key (capability_id),
    unique key uk_aig_capability_code (capability_code),
    key idx_aig_capability_status (status, del_flag)
) engine=innodb comment = 'AI业务能力模板表';

-- ----------------------------
-- 2、模型治理扩展（关联 sai_model_config，不修改其表结构）
-- ----------------------------
drop table if exists aig_model_governance;
create table aig_model_governance (
    governance_id     bigint(20)      not null                   comment '治理记录ID',
    model_id          bigint(20)      not null                   comment '关联 sai_model_config.id（模型主数据仍在 snail-ai）',
    deployment_type   varchar(24)     not null default 'EXTERNAL_API' comment '部署类型（LOCAL本地私有 GROUP集团共享 EXTERNAL_ENTERPRISE外部企业服务 EXTERNAL_API外部API）',
    data_level_max    varchar(16)     not null default 'PUBLIC'  comment '允许处理的最高数据等级（PUBLIC公开 INTERNAL内部 RESTRICTED限制）',
    lifecycle_status  varchar(16)     not null default 'CANDIDATE' comment '可用状态（CANDIDATE候选 TRIAL试验 GRAY灰度 PRODUCTION生产 SUSPENDED暂停 RETIRED退役）',
    secret_ref        varchar(255)    default null               comment '密钥引用（如 kms://ai/qwen），**禁止存明文密钥**',
    input_limits      varchar(500)    default null               comment '输入限制：文本长度/文件类型/图片视频大小/并发',
    output_limits     varchar(500)    default null               comment '输出限制：格式/时长/分辨率/结构化输出能力',
    cost_limit        varchar(255)    default null               comment '成本与配额：单次/单项目/单日预算与限流规则',
    owner_tech        varchar(64)     default null               comment '技术负责人',
    owner_biz         varchar(64)     default null               comment '业务负责人',
    owner_security    varchar(64)     default null               comment '安全审批人',
    valid_from        date            default null               comment '有效期起',
    valid_to          date            default null               comment '有效期止',
    health_status     varchar(16)     default null               comment '最近健康检查结果（UP/DOWN/DEGRADED）',
    health_time       datetime        default null               comment '最近健康检查时间',
    status            char(1)         default '0'                comment '状态（0正常 1停用）',
    del_flag          char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept       bigint(20)      default null               comment '创建部门',
    create_by         bigint(20)      default null               comment '创建者',
    create_time       datetime                                   comment '创建时间',
    update_by         bigint(20)      default null               comment '更新者',
    update_time       datetime                                   comment '更新时间',
    remark            varchar(500)    default null               comment '备注',
    primary key (governance_id),
    unique key uk_aig_gov_model (model_id),
    key idx_aig_gov_lifecycle (lifecycle_status, del_flag),
    key idx_aig_gov_data_level (data_level_max)
) engine=innodb comment = 'AI模型治理扩展表';

-- ----------------------------
-- 3、能力-模型绑定（设计 §5.1 可用模型能力 + §6 优先级）
-- ----------------------------
drop table if exists aig_capability_model;
create table aig_capability_model (
    bind_id         bigint(20)      not null                   comment '绑定ID',
    capability_code varchar(64)     not null                   comment '能力编码',
    model_id        bigint(20)      not null                   comment '关联 sai_model_config.id',
    usage_type      varchar(16)     not null default 'PRIMARY' comment '用途（PRIMARY主选 FALLBACK备选 GRAY灰度）',
    priority        int(11)         not null default 100       comment '优先级，数值越小越优先',
    status          char(1)         default '0'                comment '状态（0正常 1停用）',
    del_flag        char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    remark          varchar(500)    default null               comment '备注',
    primary key (bind_id),
    unique key uk_aig_cap_model (capability_code, model_id),
    key idx_aig_cap_model_pick (capability_code, status, del_flag, priority)
) engine=innodb comment = 'AI能力与模型绑定表';

-- ----------------------------
-- 4、路由策略（设计 §6.2 数据等级路由矩阵）
-- ----------------------------
drop table if exists aig_route_policy;
create table aig_route_policy (
    policy_id            bigint(20)      not null                   comment '策略ID',
    capability_code      varchar(64)     not null                   comment '能力编码',
    data_level           varchar(16)     not null                   comment '数据等级（PUBLIC/INTERNAL/RESTRICTED）',
    preferred_deployment varchar(24)     default null               comment '优先部署类型（LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API）',
    allow_external       char(1)         not null default 'N'       comment '是否允许外发（Y允许 N禁止）',
    require_approval     char(1)         not null default 'N'       comment '调用前是否需要审批（Y是 N否；阶段1仅作为路由判定，审批流二期）',
    fallback_to_manual   char(1)         not null default 'Y'       comment '无可用模型时是否转人工待办（Y是 N否）',
    status               char(1)         default '0'                comment '状态（0正常 1停用）',
    del_flag             char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept          bigint(20)      default null               comment '创建部门',
    create_by            bigint(20)      default null               comment '创建者',
    create_time          datetime                                   comment '创建时间',
    update_by            bigint(20)      default null               comment '更新者',
    update_time          datetime                                   comment '更新时间',
    remark               varchar(500)    default null               comment '备注',
    primary key (policy_id),
    unique key uk_aig_route_cap_level (capability_code, data_level),
    key idx_aig_route_lookup (capability_code, data_level, status, del_flag)
) engine=innodb comment = 'AI路由策略表';

-- ----------------------------
-- 5、逐次调用审计（设计 §10.2；追加型，不做逻辑删除）
-- ----------------------------
drop table if exists aig_invocation_audit;
create table aig_invocation_audit (
    audit_id         bigint(20)      not null                   comment '审计ID',
    trace_id         varchar(64)     not null                   comment '调用链ID（一次业务动作一个）',
    capability_code  varchar(64)     not null                   comment '业务能力编码',
    caller_id        bigint(20)      default null               comment '调用人用户ID',
    caller_name      varchar(64)     default null               comment '调用人账号（冗余，便于离线审计）',
    data_level       varchar(16)     default null               comment '本次数据等级',
    model_id         bigint(20)      default null               comment '实际使用的模型ID（sai_model_config.id）',
    model_key        varchar(100)    default null               comment '模型键（内部标识）',
    model_version    varchar(64)     default null               comment '模型版本',
    deployment_type  varchar(24)     default null               comment '部署类型',
    external_call    char(1)         default 'N'                comment '是否外发（Y是 N否）',
    policy_hit       varchar(255)    default null               comment '命中的路由策略摘要',
    input_hash       varchar(64)     default null               comment '输入摘要哈希（不存原文）',
    input_summary    varchar(500)    default null               comment '输入摘要（仅在审计等级允许时写入）',
    output_ref       varchar(500)    default null               comment '输出引用（对象键/业务ID，不存完整输出副本）',
    result           char(1)         default '0'                comment '结果（0成功 1失败）',
    error_summary    varchar(500)    default null               comment '错误摘要',
    latency_ms       int(11)         default null               comment '耗时（毫秒）',
    cost             decimal(18,8)   default null               comment '本次成本',
    retry_count      int(11)         default 0                  comment '重试次数',
    manual_decision  varchar(16)     default 'PENDING'          comment '人工结论（PENDING/ACCEPTED/REJECTED/NOT_REQUIRED）',
    operate_time     datetime                                   comment '调用时间',
    primary key (audit_id),
    key idx_aig_audit_time (operate_time),
    key idx_aig_audit_cap (capability_code, operate_time),
    key idx_aig_audit_caller (caller_id, operate_time),
    key idx_aig_audit_trace (trace_id),
    key idx_aig_audit_external (external_call, operate_time)
) engine=innodb comment = 'AI调用逐次审计表';

-- ----------------------------
-- 6、阶段1 演示数据：首个能力模板 talent_match（设计 §5.2 §12.3）
--    人才匹配只允许本地模型/规则处理，禁止外发个人信息
-- ----------------------------
insert into aig_capability values(
    1763000000000001001, 'talent_match', '人才能力匹配',
    '按岗位/项目需求，从人才库中给出候选技能匹配建议，供人工确认后使用',
    'TEXT,RERANK',
    '{"fields":[{"name":"demandText","type":"string","dataLevel":"INTERNAL"},{"name":"skillTags","type":"string[]","dataLevel":"INTERNAL"}],"forbidden":["phone","idCard","resume_raw","name"]}',
    '{"fields":[{"name":"candidates","type":"array"},{"name":"score","type":"number"},{"name":"evidence","type":"string"},{"name":"pendingConfirm","type":"array"}]}',
    'LOCAL_ONLY',
    '最终候选与资源协调必须由项目负责人/人才库责任人确认；匹配结论不得自动流转',
    '置信度低于 0.7 的候选不得进入推荐位；输出结构不符时重试一次后转人工',
    'SUMMARY', '0', '0',
    1761000000000000103, 1761100000000000001, sysdate(), null, null,
    '阶段1 首个验证能力'
);

-- ----------------------------
-- 7、阶段1 演示数据：路由策略（设计 §6.2）
--    talent_match 三个数据等级均不外发
-- ----------------------------
insert into aig_route_policy values(1763000000000002001, 'talent_match', 'PUBLIC',     'LOCAL', 'N', 'N', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才数据即使标记公开也不外发');
insert into aig_route_policy values(1763000000000002002, 'talent_match', 'INTERNAL',   'LOCAL', 'N', 'N', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '内部资料本地优先，禁止外发');
insert into aig_route_policy values(1763000000000002003, 'talent_match', 'RESTRICTED', 'LOCAL', 'N', 'Y', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '限制级默认禁止外发，需审批');
