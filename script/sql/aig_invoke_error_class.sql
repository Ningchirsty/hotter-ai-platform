-- ------------------------------------------------------------------
-- AI 治理：逐次调用审计补「错误分类」列（aig_invocation_audit.error_class）
--
-- 背景（为什么需要它）：
--   C3 第二块「评测灰度」的达标判据是 a+b+c：调用次数 ≥ N、失败率 ≤ X%、
--   **无严重错误**。其中 (a)(b) 直接来自审计行的 result（0/1），
--   但 (c)「严重错误」需要一个能区分「哪里错了」的机器可读字段——
--   「策略拒绝（版本/配置不允许）」与「上游临时超时」是两件完全不同的事：
--   前者说明这个版本不该被放行，后者只说明当时网络不好（已由失败率覆盖）。
--
--   分类其实**早就被算出来了**（invoke 侧用 AigErrorClassEnum 决定
--   重试/换候选/转人工/熔断），但它此前只被写进 policy_hit 的文本里
--   （「错误分类=POLICY_DENIED（…）」），而 policy_hit 是 varchar(255)、
--   且该片段是**最后追加**的——策略命中多时它最先被截掉。
--   把「有没有严重错误」压在一段会被截断的文本上，就会静默漏判
--   （漏判 = 把不达标的版本放成 STABLE）。
--
-- 语义：为空 = 本次调用成功，或旧数据（本列上线前）。只在**最终失败**时写入；
--   主候选失败、备选成功的那次调用是成功的，不算严重错误。
--   因此本列**必须可空**、不给默认值。
--
-- 为什么不为 error_class 单独建索引：灰度的统计总是先按
--   agent_version_id + operate_time 收窄（已有 idx_aig_audit_agent_version），
--   error_class 只在这批行内做条件计数，属于后置过滤，建单列索引没有意义。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema
--   判断后动态执行 DDL（写法与 aig_invoke_agent_version.sql 一致）。
--
-- 兼容性：只加一列可空，不改既有列、不动索引、不改既有数据。
--
-- 注意：script/sql/aig_ai_gov.sql（建表脚本）里也已经加上了这一列，
--   所以全新库跑建表脚本即可；本脚本是给**存量库**补齐的（可重复执行）。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_invocation_audit'
        AND COLUMN_NAME = 'error_class'),
    'SELECT ''aig_invocation_audit.error_class already exists'' AS note',
    'ALTER TABLE aig_invocation_audit ADD COLUMN error_class VARCHAR(32) NULL COMMENT ''错误分类编码（AigErrorClassEnum），成功时为空；灰度的「无严重错误」按它统计'' AFTER error_summary')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：列存在且可空
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_invocation_audit'
   AND COLUMN_NAME = 'error_class';
