-- ------------------------------------------------------------------
-- AI 治理：高风险「变更单」（aig_change_request）—— Q1C 完整版
--
-- 背景（为什么需要它）：
--   Q1C 轻量版已经把「放开外发 / 动限制级策略 / 停用删除能力」挡在 aig:capability:high-risk 后面。
--   但它解决不了两件事：
--     ① **没有该权限的人**提不出这类改动——他只能去 IM 上找管理员，改动无据可查；
--     ② 只有"谁改的"（操作日志），没有"**谁批的**"与"**批的到底是什么值**"。
--   本表把高风险变更变成一张单子：**申请人**（有 aig:capability:edit 即可）填目标值 → **批准人**
--   （需 aig:capability:high-risk）批准 → 批准即执行 → 落"生效记录"（before/after + 执行时间）。
--
-- 语义（几条都是刻意的，与 aig_call_approval 同规矩）：
--   · **申请人不得自批**：aig_call_approval 已经这么做（"这单子要两个人才成立"），本表沿用；
--     服务端强制，不靠页面藏按钮。
--   · **批准即执行**：审批的意义就是"批了就生效"；不做"批准后还要人再点一次应用"——
--     那样单子容易烂尾，而且没人知道到底生效了没有。
--     ⇒ 执行前用 before_json 与**当前行**逐字段比对（乐观并发）：
--       不一致 → 置 CONFLICTED 并写明"谁先改了"，请重新提交，**绝不覆盖别人的改动**。
--   · **执行走既有服务方法**（routePolicyService.create/update/remove），因此 Q1C 那道闸门
--     仍然生效——批准人持有 high-risk，闸门放行；本表**不提供任何绕过闸门的通道**。
--   · **零新增权限点**：申请用 aig:capability:edit，批准用 aig:capability:high-risk（已存在）。
--   · 不做物理删除：变更单是留痕事实，del_flag 只是常规逻辑删除位。
--
-- 幂等：create table if not exists，可安全重复执行。
-- 兼容性：只新增表，不改任何既有表与列；老库跑本脚本即可。
-- 注意：本脚本已加进 .github/workflows/deploy-poc.yml 的 additive 重放清单
--   （create table if not exists 幂等）。
-- ------------------------------------------------------------------

create table if not exists aig_change_request (
    change_request_id bigint(20)    not null                  comment '变更单ID',
    capability_code   varchar(64)   not null                  comment '能力编码',
    change_type       varchar(24)   not null                  comment '变更类型（POLICY_CREATE 新增策略 / POLICY_UPDATE 修改策略 / POLICY_DELETE 删除策略）',
    target_ref        bigint(20)    default null              comment '目标对象ID（策略行 policyId；新增时为空）',
    before_json       varchar(1000) default null              comment '变更前快照（JSON；新增时为空）',
    after_json        varchar(1000) default null              comment '变更后目标值（JSON；删除时为空——删除不需要目标值）',
    reason            varchar(500)  default null              comment '申请理由（为什么必须这么改）',
    status            varchar(16)   not null default 'PENDING' comment '状态（PENDING 待批准 / APPLIED 已批准并生效 / REJECTED 已驳回 / CANCELLED 已撤回 / CONFLICTED 执行时与现场冲突未生效）',
    requester_id      bigint(20)    not null                  comment '申请人用户ID',
    requester_name    varchar(64)   default null              comment '申请人账号（冗余，便于离线核对）',
    approver_id       bigint(20)    default null              comment '批准人用户ID（申请人不得自批）',
    approver_name     varchar(64)   default null              comment '批准人账号（冗余）',
    decided_at        datetime      default null              comment '批准/驳回时间',
    decision_remark   varchar(500)  default null              comment '审批意见（驳回必填）',
    applied_at        datetime      default null              comment '生效时间（即批准时执行成功的时间）',
    result_remark     varchar(500)  default null              comment '执行结果说明（冲突/失败原因，成功时为空）',
    del_flag          char(1)       default '0'               comment '删除标志（0代表存在 1代表删除）',
    create_dept       bigint(20)    default null              comment '创建部门',
    create_by         bigint(20)    default null              comment '创建者',
    create_time       datetime      default null              comment '创建时间',
    update_by         bigint(20)    default null              comment '更新者',
    update_time       datetime      default null              comment '更新时间',
    remark            varchar(500)  default null              comment '备注',
    primary key (change_request_id),
    key idx_aig_change_request_status (status, capability_code),
    key idx_aig_change_request_requester (requester_id, status)
) engine=innodb comment = 'AI 高风险变更单（申请-批准-生效记录；批准即执行，执行前做乐观并发校验）';

-- 核对：表存在
SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_change_request';

SELECT 'AIG_CHANGE_REQUEST_DONE' AS marker;
