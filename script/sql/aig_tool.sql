-- ------------------------------------------------------------------
-- AI 治理：工具注册与执行账本（设计 §6.5 工具与流程、§7.5 工具执行与确认、§8.2）
--
-- 背景（D05）：工具与知识已可绑定，但没有对应通用执行引擎 ⇒ 本脚本建立工具注册
--   （定义 + 版本）与执行账本（call），让"可绑定"与"可执行"分开表达：
--   没有注册执行器（executor_ref 无对应实现）的工具只能标"已登记、待接入"，
--   不能显示为可用（设计 §4.1）。
--
-- 三条纪律：
--   · 密钥只存引用：本表不存任何密钥/凭证，执行器自行从平台配置取；
--   · 执行账本按幂等键唯一：写工具重试必须命中同一条 call，而不是重复变更业务数据；
--   · 人工确认绑定输入摘要：参数一变摘要就变，原批准自动失效（设计 §7.5）。
--
-- 幂等：create table if not exists。
-- ------------------------------------------------------------------

create table if not exists aig_tool_definition (
    tool_id      bigint(20)   not null                  comment '工具ID',
    tool_code    varchar(64)  not null                  comment '工具编码（唯一）',
    tool_name    varchar(160) not null                  comment '工具名称',
    description  varchar(500) default null              comment '用途说明',
    owner_id     bigint(20)   default null              comment '责任人',
    data_policy  varchar(500) default null              comment '数据策略说明（不含密钥）',
    status       char(1)      not null default '0'       comment '记录状态（0正常 1停用）',
    del_flag     char(1)      default '0'               comment '删除标志（0存在 1删除）',
    create_dept  bigint(20)   default null              comment '创建部门',
    create_by    bigint(20)   default null              comment '创建者',
    create_time  datetime                               comment '创建时间',
    update_by    bigint(20)   default null              comment '更新者',
    update_time  datetime                               comment '更新时间',
    remark       varchar(500) default null              comment '备注',
    primary key (tool_id),
    unique key uk_aig_tool_code (tool_code)
) engine=innodb comment = '工具定义（设计 §8.2）';

create table if not exists aig_tool_definition_version (
    tool_version_id bigint(20)   not null                  comment '工具版本ID',
    tool_id         bigint(20)   not null                  comment '所属工具',
    version         varchar(32)  not null                  comment '版本号（同工具内唯一）',
    executor_ref    varchar(128) default null              comment '执行器引用（已注册的执行器键；空=仅登记、待接入）',
    operation_type  varchar(32)  not null                  comment '操作类型（AigToolOperationTypeEnum：READ_ONLY_QUERY/GENERATE_DRAFT/MUTATE_BUSINESS_DATA/EXTERNAL_SEND）',
    input_schema    varchar(2000) default null             comment '输入 Schema（JSON 文本）',
    output_schema   varchar(2000) default null             comment '输出 Schema（JSON 文本）',
    timeout_ms      int(11)      not null default 30000    comment '超时（毫秒）',
    max_attempts    int(11)      not null default 1        comment '最大尝试次数（写操作应保持 1）',
    permission_point varchar(128) default null             comment '调用该工具所需权限点（可空）',
    data_level_max  varchar(32)  default null              comment '允许处理的数据等级上限（可空=不限）',
    status          char(1)      not null default '0'       comment '记录状态（0正常 1停用）',
    del_flag        char(1)      default '0'               comment '删除标志（0存在 1删除）',
    create_dept     bigint(20)   default null              comment '创建部门',
    create_by       bigint(20)   default null              comment '创建者',
    create_time     datetime                               comment '创建时间',
    update_by       bigint(20)   default null              comment '更新者',
    update_time     datetime                               comment '更新时间',
    remark          varchar(500) default null              comment '备注',
    primary key (tool_version_id),
    unique key uk_aig_tool_version (tool_id, version),
    key idx_aig_tool_version_op (operation_type, status)
) engine=innodb comment = '工具版本（执行契约；密钥只存引用，设计 §8.2）';

create table if not exists aig_tool_call (
    call_id        bigint(20)   not null                  comment '调用ID',
    tool_code      varchar(64)  not null                  comment '工具编码',
    tool_version   varchar(32)  not null                  comment '工具版本',
    task_id        bigint(20)   default null              comment '归属任务（可空）',
    idempotency_key varchar(128) not null                 comment '幂等键（工具+版本+键唯一）',
    status         varchar(16)  not null                  comment '调用状态（AigToolCallStatusEnum：RUNNING/SUCCEEDED/FAILED/NEEDS_CONFIRMATION/OUTCOME_UNKNOWN）',
    input_digest   char(64)     default null              comment '输入摘要（人工确认绑定它；参数变则失效）',
    input_ref      varchar(500) default null              comment '输入引用（对象键；不存原文）',
    approval_ref   varchar(128) default null              comment '批准引用（人工确认/业务审批的凭证ID）',
    approval_digest char(64)    default null              comment '批准时绑定的输入摘要',
    output_ref     varchar(500) default null              comment '输出引用（大结果走存储引用）',
    receipt_ref    varchar(255) default null              comment '操作凭证引用（写操作的回执ID）',
    error_code     varchar(64)  default null              comment '失败分类',
    error_message  varchar(500) default null              comment '失败原因',
    del_flag       char(1)      default '0'               comment '删除标志（0存在 1删除）',
    create_dept    bigint(20)   default null              comment '创建部门',
    create_by      bigint(20)   default null              comment '创建者',
    create_time    datetime                               comment '创建时间',
    update_by      bigint(20)   default null              comment '更新者',
    update_time    datetime                               comment '更新时间',
    remark         varchar(500) default null              comment '备注',
    primary key (call_id),
    unique key uk_aig_tool_call (tool_code, tool_version, idempotency_key),
    key idx_aig_tool_call_task (task_id, status)
) engine=innodb comment = '工具调用账本（幂等 + 确认绑定，设计 §7.5）';
