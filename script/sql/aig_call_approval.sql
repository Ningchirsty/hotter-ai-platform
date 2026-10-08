-- ------------------------------------------------------------------
-- AI 治理：调用授权审批表（aig_call_approval）——把 require_approval 做实（C3）
--
-- 背景（为什么需要它）：
--   aig_route_policy.require_approval（「调用前是否需要审批」）**早就存在**——建表、
--   实体、VO/BO 都有，治理台「路由策略」页有开关、列表也显示「需要/不需要」。
--   但在本次之前，全仓库**唯一**读它的地方是把它拼进 policyHits 的说明文本：
--   管理员把开关打开、页面显示「需要」，调用却照跑不误。建表注释也自认这是
--   「阶段1仅作为路由判定，审批流二期」。本表与配套逻辑把「二期」补上。
--
--   好消息：现有种子数据的 require_approval **全是 'N'**，所以本表上线
--   **不会改变任何既有部署的行为**，直到管理员主动把某个策略改成 'Y'。
--
-- 语义（几条都是刻意的）：
--   · 授权粒度＝**人 × 能力 × 数据等级**。审批本身就是按 (能力,数据等级) 的策略触发的，
--     批准 INTERNAL 不该顺带放行 RESTRICTED/STRICT——数据等级是安全维度，越高越严。
--   · 一张单子有两个时间：expire_time（审批时限：PENDING 超时即为 EXPIRED，
--     过期之后不得再批准）与 valid_until（授权有效期止，批准时＝审批时间+有效时长）。
--   · 只有 status='APPROVED' 且 valid_until > now 才是「有效授权」；
--     PENDING / REJECTED / EXPIRED / CANCELLED 一律不放行。
--   · **申请人不得自审**（分离职责）——这条在服务层强制，不靠页面藏按钮。
--   · 不做物理删除：审批是留痕事实，`del_flag` 只是常规逻辑删除位。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可安全重复执行。
-- 兼容性：只新增表，不改任何既有表与列。
--
-- 注意：建表脚本 aig_ai_gov.sql 里也已加上本表，因此全新库跑建表脚本即可；
--   本脚本是给**存量库**补齐的。
-- ------------------------------------------------------------------

create table if not exists aig_call_approval (
    approval_id     bigint(20)   not null                   comment '审批单ID',
    requester_id    bigint(20)   not null                   comment '申请人用户ID（sys_user.user_id）',
    requester_name  varchar(64)  default null               comment '申请人账号（冗余，便于离线核对）',
    capability_code varchar(64)  not null                   comment '能力编码（授权粒度：能力）',
    data_level      varchar(16)  not null                   comment '数据等级（授权粒度：数据等级）',
    reason          varchar(500) default null               comment '申请理由（为什么需要这次授权）',
    status          varchar(16)  not null default 'PENDING' comment '状态（PENDING 待审批 / APPROVED 已批准 / REJECTED 已驳回 / EXPIRED 已超时 / CANCELLED 已撤回）',
    expire_time     datetime     not null                   comment '审批时限：PENDING 超过此时间不得再批准，由扫描置 EXPIRED',
    approver_id     bigint(20)   default null               comment '审批人用户ID（申请人不得自审）',
    approver_name   varchar(64)  default null               comment '审批人账号（冗余）',
    decided_at      datetime     default null               comment '审批时间',
    decision_remark varchar(500) default null               comment '审批意见（批准/驳回时填；驳回必填）',
    valid_until     datetime     default null               comment '授权有效期止（批准时=审批时间+有效时长；为空表示这张单从未获批）',
    del_flag        char(1)      default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept     bigint(20)   default null               comment '创建部门',
    create_by       bigint(20)   default null               comment '创建者',
    create_time     datetime     default null               comment '创建时间',
    update_by       bigint(20)   default null               comment '更新者',
    update_time     datetime     default null               comment '更新时间',
    remark          varchar(500) default null               comment '备注',
    primary key (approval_id),
    key idx_aig_call_approval_grant (requester_id, capability_code, data_level, status, valid_until),
    key idx_aig_call_approval_status (status, expire_time)
) engine=innodb comment = 'AI 调用授权审批（人×能力×数据等级，带时效；批准后有效期内免再审）';

-- 核对：表存在
SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_call_approval';
