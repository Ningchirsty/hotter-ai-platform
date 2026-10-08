-- =====================================================================
-- M-005：策略决策账本 aig_policy_decision_log
-- =====================================================================
-- 为什么需要它（设计 ADR-006 要求「外发管控在执行点强制且**可举证**」）：
--   decide() 的结论此前只进内存 + policyHits 文本。调用被拒时，除了应用日志
--   **没有可查询的结构化证据**，无法回答"上周三那次外发是谁批准的、命中了哪条策略"。
--
-- 口径（与 aig_invocation_audit 对齐）：
--   * **追加型账本，不做逻辑删除**（与审计表同口径，见 aig_ai_gov.sql 第 13 行的约定）；
--     => **没有 del_flag / create_by / update_* 等 BaseEntity 列**，只有 operate_time；
--   * 只增不改：本脚本用 create table if not exists，可反复执行。
--
-- 与审计表的分工（不要混）：
--   aig_invocation_audit  = 「调用发生了什么」（含耗时/成本/结果/重试）
--   aig_policy_decision_log = 「为什么放行/为什么拒绝」（决策与授权证据）
--   一次调用通常各一行；被拒的调用只有决策行（本来就没发生调用）。
--
-- 回滚：drop table if exists aig_policy_decision_log（新表、无历史依赖）
-- =====================================================================

create table if not exists aig_policy_decision_log (
    decision_id       bigint(20)      not null                   comment '决策ID',
    trace_id          varchar(64)     default null               comment '调用链ID（与 aig_invocation_audit.trace_id 对齐，用于把「为什么」接到「发生了什么」）',
    capability_code   varchar(64)     not null                   comment '业务能力编码',
    data_level        varchar(16)     not null                   comment '本次数据等级（PUBLIC/INTERNAL/RESTRICTED/STRICT）',
    decision          varchar(16)     not null                   comment '决策结论（MODEL 命中模型 / MANUAL 转人工 / DENIED 策略拒绝）',
    model_id          bigint(20)      default null               comment '选中的模型ID（DENIED/MANUAL 时为空）',
    model_key         varchar(100)    default null               comment '选中的模型键（冗余，便于离线审计）',
    deployment_type   varchar(24)     default null               comment '选中模型的部署类型',
    policy_id         bigint(20)      default null               comment '命中的路由策略ID（为空=未配置该能力×数据等级的策略）',
    allow_external    char(1)         default null               comment '本次**实际生效**的 allow_external（已含 STRICT 级强制置 N 之后的值，不是策略表原值）',
    approval_required char(1)         default 'N'                comment '策略是否要求调用授权审批（Y/N）',
    external_call     char(1)         default 'N'                comment '本次是否真的外发（Y/N）。DENIED/MANUAL 恒为 N；MODEL 按选中模型部署类型判定',
    reason_code       varchar(64)     default null               comment '细因（见 contract/error-codes.json 的 reasonCodes）',
    reason            varchar(500)    default null               comment '结论说明（decision 的一句话原因）',
    excluded_json     varchar(2000)   default null               comment '被排除候选与原因摘要（来自 policyHits，截断保存）',
    caller_id         bigint(20)      default null               comment '调用人用户ID（调度/系统发起为空）',
    agent_version_id  bigint(20)      default null               comment '本次归属的 Agent 版本ID（为空=本次未绑定版本，非「不知道」）',
    task_id           bigint(20)      default null               comment '关联任务（一次业务动作一个）',
    request_digest    char(64)        default null               comment '请求摘要哈希（同幂等键不同摘要要能看出来）',
    operate_time      datetime                                   comment '决策时间',
    primary key (decision_id),
    key idx_policy_decision_time (operate_time),
    key idx_policy_decision_cap (capability_code, data_level, operate_time),
    key idx_policy_decision_trace (trace_id),
    key idx_policy_decision_decision (decision, operate_time),
    key idx_policy_decision_external (external_call, operate_time)
) engine=innodb comment = 'AI调用策略决策账本（可举证：为什么放行/为什么拒绝）';
