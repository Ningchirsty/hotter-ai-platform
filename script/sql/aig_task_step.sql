-- ------------------------------------------------------------------
-- AI 治理：任务步骤账本 aig_task_step（设计 §8.2、§6.5）
--
-- 背景（D05）：子 Agent 顺序调用与顺序流程节点目前只"校验并冻结进执行计划"，
--   父任务还没有一个可追溯的"这次编排有哪些步骤、交给谁、结果引用是什么"的落点。
--   本表提供该落点：步骤声明、输入摘要、结果引用与批准引用。
--
-- 三条纪律：
--   · 步骤按 (task_id, sequence, attempt) 唯一：重试是新的 attempt，而不是覆盖历史；
--   · 只记引用（output_ref / approval_ref），不把大结果或受限原文落进本表；
--   · 状态独立于任务状态：任务 SUCCEEDED 而某步骤 FAILED 是可能的（部分成功），
--     合成一个状态会掩盖"哪一步没成"。
--
-- 幂等：create table if not exists。
-- ------------------------------------------------------------------

create table if not exists aig_task_step (
    step_id             bigint(20)   not null               comment '步骤ID',
    task_id             bigint(20)   not null               comment '所属任务',
    sequence            int(11)      not null               comment '步骤序号（同任务内从 1 开始）',
    kind                varchar(16)  not null               comment '步骤类型（AigTaskStepKindEnum：SUB_AGENT/FLOW_NODE/TOOL_CALL）',
    node_type           varchar(24)  default null            comment '流程节点类型（AigFlowNodeTypeEnum：STEP/CONDITION/HUMAN_CONFIRM/OUTPUT；非 FLOW_NODE 步骤为空）',
    node_ref            varchar(128) default null            comment '流程节点引用（条件表达式的键/步骤标识/输出契约键；非 FLOW_NODE 步骤为空）',
    resource_version_id bigint(20)   default null            comment '引用的资源版本（子 Agent 版本 / 工具版本；可空）',
    status              varchar(16)  not null default 'PLANNED' comment '步骤状态（AigTaskStepStatusEnum）',
    input_hash          char(64)     default null            comment '输入摘要（与父任务输入快照对应）',
    output_ref          varchar(500) default null            comment '结果引用（对象键/业务ID）',
    approval_ref        varchar(128) default null            comment '批准引用（人工确认/业务审批凭证ID）',
    attempt             int(11)      not null default 1       comment '第几次尝试（重试是新 attempt，不覆盖历史）',
    error_code          varchar(64)  default null            comment '失败分类',
    error_message       varchar(500) default null            comment '失败原因',
    del_flag            char(1)      default '0'             comment '删除标志（0存在 1删除）',
    create_dept         bigint(20)   default null            comment '创建部门',
    create_by           bigint(20)   default null            comment '创建者',
    create_time         datetime                             comment '创建时间',
    update_by           bigint(20)   default null            comment '更新者',
    update_time         datetime                             comment '更新时间',
    remark              varchar(500) default null            comment '备注',
    primary key (step_id),
    unique key uk_aig_task_step (task_id, sequence, attempt),
    key idx_aig_task_step_status (task_id, status)
) engine=innodb comment = '任务步骤账本（设计 §8.2、§6.5）';
