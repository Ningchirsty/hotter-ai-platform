-- ------------------------------------------------------------------
-- AI 治理：结果反馈账本 aig_result_feedback（设计 §6.9 反馈与运营、§8.2）
--
-- 背景（D12）：统计以调用/成本/任务为基础，缺少业务反馈与有效成果口径。本表把
--   "员工对结果的反馈"落成一条可被训练负责人处理的记录，并明确：
--     · 反馈关联 taskId / resultId / Agent 版本 / 知识版本（缺一条就无法定位到具体哪次运行）；
--     · 采纳反馈**不直接改线上版本**——它进入问题队列，由负责人决定补知识/改规则/加用例/关闭；
--     · "累计成果"必须定义为**经确认的结果记录**，不能把所有模型输出都算成成果。
--
-- 幂等：create table if not exists。
-- ID 段：无（业务自增由框架分配）。
-- ------------------------------------------------------------------

create table if not exists aig_result_feedback (
    feedback_id        bigint(20)   not null               comment '反馈ID',
    task_id            bigint(20)   default null           comment '关联任务（结果来自哪次运行）',
    result_id          bigint(20)   default null           comment '关联结果（候选资产ID）',
    agent_version_id   bigint(20)   default null           comment '关联 Agent 版本',
    knowledge_version_id bigint(20) default null           comment '关联知识版本（可空）',
    reason_code        varchar(32)  not null               comment '反馈原因（AigFeedbackReasonEnum）',
    verdict            varchar(16)  not null default 'PENDING' comment '处理结论（AigFeedbackVerdictEnum：PENDING/CONFIRMED/REJECTED）',
    action_code        varchar(32)  default null           comment '处理动作（AigFeedbackActionEnum：ADD_KNOWLEDGE/CHANGE_RULE/ADD_CASE/CLOSE）',
    detail_ref         varchar(500) default null           comment '补充说明引用（受限原文走受控存储）',
    reporter_id        bigint(20)   default null           comment '反馈人',
    handled_by         bigint(20)   default null           comment '处理人',
    handled_at         datetime     default null           comment '处理时间',
    status             char(1)      not null default '0'    comment '记录状态（0正常 1停用）',
    del_flag           char(1)      default '0'            comment '删除标志（0存在 1删除）',
    create_dept        bigint(20)   default null           comment '创建部门',
    create_by          bigint(20)   default null           comment '创建者',
    create_time        datetime                            comment '创建时间',
    update_by          bigint(20)   default null           comment '更新者',
    update_time        datetime                            comment '更新时间',
    remark             varchar(500) default null           comment '备注',
    primary key (feedback_id),
    key idx_aig_feedback_result (result_id),
    key idx_aig_feedback_task (task_id),
    key idx_aig_feedback_queue (verdict, create_time)
) engine=innodb comment = '结果反馈（设计 §8.2、§6.9）';
