-- ------------------------------------------------------------------
-- AI 治理：调用人均配额（C3 的第一块：用量配额按人）
--
-- 背景（为什么需要它）：
--   治理台此前只有「单次成本上限」（aig_model_governance.cost_limit_amount，按模型声明），
--   它是**单次**约束，不是**累计**约束：一个人一天调一千次、每次都在单次上限内，
--   没有任何地方会拦。而设计 §13.2 把「用量配额」列进了治理能力。
--
-- 为什么单独一张表，而不是给现有表加列：
--   配额是「谁（人）」的维度，现有表分别是「能力×数据等级」（策略）、「模型」（单次上限）、
--   「场景×能力→供应商」（强制绑定）。塞进任一张都会破坏它既有的唯一键与语义。
--
-- ★ 计量单位是「调用次数」，不是钱（这是刻意的，别改）：
--   aig_invocation_audit.cost 的注释写明「多数外部供应商不回执费用，为空表示未知而非免费」，
--   而经 snail-ai 的链路根本不返回 tokens/cost（cost 恒为 null）。
--   用一列经常是「未知」的数字做配额，会算出一本对不上的账——那正是当初
--   「累计预算刻意不做」的理由（见 07 手册 §十）。**次数**是每条调用都有的、可核对的事实。
--   等费用回执可靠了（各家都回执、币种口径定了）再谈按钱的配额。
--
-- 语义边界：
--   · 无配额行 = **不限**（默认放行）：新表上线不改变任何既有调用行为，这与
--     「新功能默认不收紧」的一贯取舍一致；
--   · 状态为停用 = 不参与判定，等同于不限；
--   · 日/月两条上限都可空，为空的那条不限（可以只限日、只限月或两者都限）；
--   · 计数按**调用人**统计 aig_invocation_audit 的行数（含失败：调用发生过就算），
--     周期是**自然日/自然月**（服务器时区）；
--   · 无调用人（调度/系统发起，审计里 caller_id 为空）时**不判**：没有「人」可以归属。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可安全重复执行。
-- 兼容性：只新增表，不改任何既有表与列。
--
-- 注意：建表脚本 aig_ai_gov.sql 里也已加上本表，因此全新库跑建表脚本即可；
--   本脚本是给**存量库**补齐的。
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS aig_user_quota (
    quota_id       bigint(20)   not null                   comment '配额ID',
    user_id        bigint(20)   not null                   comment '用户ID（sys_user.user_id）：按「调用人」计',
    user_name      varchar(64)  default null               comment '调用人账号（冗余，便于离线核对；以 user_id 为准，与 aig_invocation_audit.caller_name 同口径）',
    daily_limit    int(11)      default null               comment '每自然日调用次数上限（NULL=不限）',
    monthly_limit  int(11)      default null               comment '每自然月调用次数上限（NULL=不限）',
    status         char(1)      default '0'                comment '状态（0正常 1停用；停用=不参与判定，等同于不限）',
    del_flag       char(1)      default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept    bigint(20)   default null               comment '创建部门',
    create_by      bigint(20)   default null               comment '创建者',
    create_time    datetime     default null               comment '创建时间',
    update_by      bigint(20)   default null               comment '更新者',
    update_time    datetime     default null               comment '更新时间',
    remark         varchar(500) default null               comment '备注（为什么给他设这个额度）',
    primary key (quota_id),
    unique key uk_aig_user_quota_user (user_id)
) engine = innodb comment = 'AI 调用人均配额（按人、自然日/自然月、计「调用次数」）';

-- 核对：表存在
SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_user_quota';
