-- ----------------------------------------------------------------------------
-- AI 统一任务编排（设计文档 §9）—— WP2
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）
-- 表前缀：aig_（按 Q5 决定并入 ruoyi-ai-gov 单模块，表名不再另立前缀）
--
-- 与既有三套机制的关系（刻意不合并）：
--   image_task / video_task（ruoyi-ai 内核） = 执行真相
--   cp_async_job（ruoyi-content）            = 内容域作业
--   dp_generation（ruoyi-ai-creative）       = 视觉候选
--   本组表 = 【跨域编排层】：统一状态机、输入快照、事件、回调、幂等、成本。
--   新能力走本层；既有三套保持可用，dp_generation 的编排后续逐步委托过来，
--   一次性迁移会打断在跑的视觉工厂。
--
-- 关键口径：
--   1. 状态迁移唯一入口 + 乐观锁（version）；每次迁移写 aig_task_event（含 from/to）。
--   2. 输入快照不可变：创建时冻结，执行与审核只引用快照；snapshot_hash 用于校验未被改写。
--   3. 幂等键 = task_id + attempt_no（重试不重复计费）；外部重复提交另用 idempotency_key。
--   4. 回调幂等：aig_callback 对 (provider_code, event_id) 唯一。
--   5. 业务表只存资产 ID / 对象引用，不存服务器本地路径。
--   6. 幂等建表（create table if not exists），可安全重复执行。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、统一 AI 任务（设计 §9.1 任务类型 + §9.2 状态机）
-- ----------------------------
create table if not exists aig_task (
    task_id            bigint(20)      not null                   comment '任务ID',
    task_no            varchar(32)     not null                   comment '任务号（不可猜测的业务编号，对外不暴露连续ID）',
    task_type          varchar(32)     not null                   comment '任务类型（PLAN_GENERATION/VISUAL_DNA_ANALYSIS/TEXT_GENERATION/IMAGE_GENERATION/IMAGE_EDIT/VIDEO_GENERATION/DESIGN_SESSION_CREATE/VISUAL_QA/AGENT_EVALUATION）',
    capability_code    varchar(64)     default null               comment '业务能力编码（业务侧只传能力，不传厂商参数）',
    scenario_code      varchar(32)     default null               comment '业务场景（LONG_PAGE/POSTER/MULTI_IMAGE/VIDEO…）',
    project_type       varchar(32)     default null               comment '所属业务域（CONTENT/CREATIVE/TALENT…，用于跨域统计与权限）',
    project_id         bigint(20)      default null               comment '业务对象ID（如 cp_task.task_id；刻意不加外键）',
    agent_version_id   bigint(20)      default null               comment '发起该任务的 Agent 版本ID（人工直接发起时为空）',
    data_level         varchar(16)     not null default 'INTERNAL' comment '数据等级（PUBLIC/INTERNAL/RESTRICTED/STRICT）',
    allow_external     char(1)         not null default 'N'       comment '业务侧是否允许外发（Y/N）；与路由策略取与，两者都允许才可能外发',
    status             varchar(24)     not null default 'DRAFT'   comment '状态（DRAFT/POLICY_CHECKING/QUEUED/DISPATCHED/RUNNING/SUCCEEDED/REVIEW_PENDING/APPROVED/REJECTED/FAILED/RETRY_WAIT/CANCELLED/NEED_HUMAN）',
    attempt_no         int(11)         not null default 0         comment '已尝试次数（幂等键的一半）',
    max_attempt        int(11)         not null default 3         comment '最大自动重试次数（默认3，是否重试由错误分类决定）',
    idempotency_key    varchar(128)    default null               comment '外部提交幂等键（同一提交人+键只建一个任务）',
    input_snapshot_id  bigint(20)      default null               comment '当前使用的不可变输入快照ID',
    route_snapshot     text                                       comment '路由快照（最终 Provider/模型/调用器/策略版本，执行与排障的唯一依据）',
    policy_result      varchar(16)     default null               comment '策略判定结果（PASS/REJECT/MANUAL）',
    policy_reason      varchar(500)    default null               comment '策略判定原因（拒绝/转人工的可读说明，不允许为空泛文案）',
    provider_code      varchar(64)     default null               comment '实际执行的外部 Provider 编码',
    provider_job_id    varchar(128)    default null               comment '外部 Provider 异步作业ID（回调据此定位任务）',
    progress           int(11)         not null default 0         comment '进度（0-100）',
    result_type        varchar(32)     default null               comment '结果类型（STRUCTURED/ASSET/SESSION）',
    external_call      char(1)         not null default 'N'       comment '本次是否发生外部调用（Y/N）',
    cost_amount        decimal(12,4)   default null               comment '成本金额（算不出留空，禁止填0冒充）',
    latency_ms         bigint(20)      default null               comment '端到端耗时（毫秒）',
    trace_id           varchar(64)     default null               comment '调用链追踪ID（关联 aig_invocation_audit.trace_id）',
    error_code         varchar(64)     default null               comment '结构化错误码（驱动重试与转人工）',
    error_message      varchar(500)    default null               comment '可读失败原因',
    review_status      varchar(24)     default null               comment '人工复核状态（PENDING/APPROVED/REJECTED）',
    reviewed_by        bigint(20)      default null               comment '复核人',
    reviewed_at        datetime        default null               comment '复核时间',
    review_comment     varchar(500)    default null               comment '复核意见',
    started_at         datetime        default null               comment '开始执行时间',
    finished_at        datetime        default null               comment '结束时间',
    version            int(11)         not null default 0         comment '乐观锁版本号（状态迁移防并发覆盖）',
    del_flag           char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept        bigint(20)      default null               comment '创建部门',
    create_by          bigint(20)      default null               comment '创建者',
    create_time        datetime                                   comment '创建时间',
    update_by          bigint(20)      default null               comment '更新者',
    update_time        datetime                                   comment '更新时间',
    remark             varchar(500)    default null               comment '备注',
    primary key (task_id),
    unique key uk_aig_task_no (task_no),
    unique key uk_aig_task_idem (project_type, create_by, idempotency_key),
    key idx_aig_task_status (status, del_flag, create_time),
    key idx_aig_task_biz (project_type, project_id, del_flag),
    key idx_aig_task_trace (trace_id),
    key idx_aig_task_status_created (status, create_time)
) engine=innodb comment = 'AI统一任务表';

-- ----------------------------
-- 2、不可变输入快照（设计 §9.3）
-- ----------------------------
create table if not exists aig_task_snapshot (
    snapshot_id        bigint(20)      not null                   comment '快照ID',
    task_id            bigint(20)      not null                   comment '任务ID',
    snapshot_version   int(11)         not null default 1         comment '快照版本（同任务内递增）',
    snapshot_json      longtext        not null                   comment '冻结内容：事实/品牌/文案版本 + Agent/Prompt/Workflow/Template/Provider 路由版本 + 参考资产版本 + 预算与负面约束',
    snapshot_hash      char(64)        not null                   comment '快照内容 SHA-256（校验执行期间未被改写）',
    data_level         varchar(16)     not null default 'INTERNAL' comment '数据等级',
    allow_external     char(1)         not null default 'N'       comment '外发许可（Y/N）',
    budget_amount      decimal(12,4)   default null               comment '预算上限（算不出留空）',
    negative_constraints text                                     comment '负面约束（禁止项/禁改项，执行时强制带入）',
    frozen_at          datetime        not null                   comment '冻结时间',
    create_by          bigint(20)      default null               comment '创建者',
    create_time        datetime                                   comment '创建时间',
    primary key (snapshot_id),
    unique key uk_aig_task_snapshot (task_id, snapshot_version),
    key idx_aig_task_snapshot_task (task_id)
) engine=innodb comment = 'AI任务不可变输入快照表';

-- ----------------------------
-- 3、任务事件（追加型，不做逻辑删除）
-- ----------------------------
create table if not exists aig_task_event (
    event_id           bigint(20)      not null                   comment '事件ID',
    task_id            bigint(20)      not null                   comment '任务ID',
    sequence           int(11)         not null                   comment '任务内事件序号（从1递增，与 task_id 组成唯一键）',
    event_type         varchar(48)     not null                   comment '事件类型（AiTaskCreated/AiTaskPolicyApproved/AiTaskDispatched/AiTaskProgressed/AiTaskSucceeded/AiTaskFailed/CandidateAssetCreated/VisualQaCompleted/AgentPlanConfirmed…）',
    from_status        varchar(24)     default null               comment '迁移前状态',
    to_status          varchar(24)     default null               comment '迁移后状态',
    attempt_no         int(11)         default null               comment '所属尝试次数',
    payload_json       text                                       comment '事件载荷（JSON；不得含密钥与受限原文）',
    detail             varchar(1000)   default null               comment '可读说明',
    actor_id           bigint(20)      default null               comment '操作者（系统事件为空）',
    actor_name         varchar(64)     default null               comment '操作者名称',
    trace_id           varchar(64)     default null               comment '调用链追踪ID',
    operate_time       datetime        not null                   comment '事件时间',
    primary key (event_id),
    unique key uk_aig_task_event_seq (task_id, sequence),
    key idx_aig_task_event_time (operate_time),
    key idx_aig_task_event_type (event_type, operate_time)
) engine=innodb comment = 'AI任务事件表';

-- ----------------------------
-- 4、任务结果（候选资产回写；SUCCEEDED 不等于审核通过）
-- ----------------------------
create table if not exists aig_task_result (
    result_id              bigint(20)      not null               comment '结果ID',
    task_id                bigint(20)      not null               comment '任务ID',
    attempt_no             int(11)         not null default 1     comment '所属尝试次数',
    result_type            varchar(32)     not null               comment '结果类型（STRUCTURED结构化输出 ASSET资产 SESSION会话）',
    asset_id               bigint(20)      default null           comment '资产ID/对象引用（禁止存服务器本地路径）',
    structured_output_json longtext                               comment '结构化输出（已通过 Schema 校验才入库）',
    validation_result      varchar(16)     default null           comment '结构校验结果（PASS/FAIL）',
    validation_detail      varchar(1000)   default null           comment '校验明细（失败时给字段级原因）',
    candidate_status       varchar(24)     not null default 'CANDIDATE' comment '候选状态（CANDIDATE候选 APPROVED已选定 REJECTED已筛除）；自动QA只筛除不放行',
    qa_verdict             varchar(16)     default null           comment '质检结论（CONSISTENT/INCONSISTENT/UNCERTAIN）',
    qa_detail              varchar(1000)   default null           comment '质检说明（含本地确定性度量的边界说明）',
    selected_by            bigint(20)      default null           comment '选定人（必须人工）',
    selected_at            datetime        default null           comment '选定时间',
    del_flag               char(1)         default '0'            comment '删除标志（0代表存在 1代表删除）',
    create_dept            bigint(20)      default null           comment '创建部门',
    create_by              bigint(20)      default null           comment '创建者',
    create_time            datetime                               comment '创建时间',
    update_by              bigint(20)      default null           comment '更新者',
    update_time            datetime                               comment '更新时间',
    remark                 varchar(500)    default null           comment '备注',
    primary key (result_id),
    key idx_aig_task_result_task (task_id, attempt_no),
    key idx_aig_task_result_asset (asset_id),
    key idx_aig_task_result_candidate (task_id, candidate_status)
) engine=innodb comment = 'AI任务结果表';

-- ----------------------------
-- 5、回调账本（验签 + 幂等去重；追加型）
-- ----------------------------
create table if not exists aig_callback (
    callback_id        bigint(20)      not null                   comment '回调记录ID',
    task_id            bigint(20)      default null               comment '关联任务ID（按 provider_job_id 定位，定位不到也留痕）',
    provider_code      varchar(64)     not null                   comment 'Provider 编码',
    provider_job_id    varchar(128)    default null               comment '外部作业ID',
    event_id           varchar(128)    default null               comment '外部事件ID（与 provider_code 组成幂等键）',
    signature_verified char(1)         not null default 'N'       comment '验签是否通过（Y/N）；未通过一律不推进状态',
    sign_algorithm     varchar(32)     default null               comment '签名算法（如 HMAC-SHA256）',
    payload_hash       char(64)        default null               comment '载荷 SHA-256',
    idempotent_hit     char(1)         not null default 'N'       comment '是否命中重复（Y=重复回调，已幂等忽略）',
    process_result     varchar(32)     default null               comment '处理结果（ACCEPTED/DUPLICATE/REJECTED_UNSIGNED/TASK_NOT_FOUND/ORDER_STALE）',
    detail             varchar(1000)   default null               comment '处理说明',
    received_at        datetime        not null                   comment '接收时间',
    primary key (callback_id),
    unique key uk_aig_callback_event (provider_code, event_id),
    key idx_aig_callback_job (provider_code, provider_job_id),
    key idx_aig_callback_task (task_id)
) engine=innodb comment = 'AI任务回调账本（验签与幂等）';

-- ----------------------------
-- 6、核对
-- ----------------------------
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name in ('aig_task', 'aig_task_snapshot', 'aig_task_event', 'aig_task_result', 'aig_callback')
 order by table_name;

select 'AIG_AI_TASK_DDL_DONE' as marker;
