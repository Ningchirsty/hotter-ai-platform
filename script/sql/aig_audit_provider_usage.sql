-- ------------------------------------------------------------------
-- AI 治理：调用审计补三列 —— provider_id / usage_json / input_snapshot_ref
--
-- 背景（为什么必须补）：
--   审计表此前只记 model_id，不记**当时那一次**属于哪个供应商。而模型会改归属
--   （同一家网关把模型迁到另一个供应商、模型下架后重建）。于是「这家供应商这个月
--   花了多少、外发了多少次」只能靠 model_id join 现查，而 join 出来的是**今天**的
--   归属，不是当时那次的——费用与合规口径必须按「当时是谁」算。
--
--   用量同理：文本调用器其实已经把响应里的 token 数解析出来了
--   （OpenAiCompatibleInvoker#extractTotalTokens），但它只活在 ModelInvokeResult 里，
--   出了那次方法调用就丢了。审计里没有承载它的列，等于**解析了却扔掉**。
--   没有用量，「预算」只剩一个说法：既无法对账，也无法据此限流。
--
--   input_snapshot_ref 是设计 §4.3 标准请求对象的字段。它只存**引用**
--   （对象键/业务ID），不存快照副本——审计表是逐次追加的，塞副本会迅速膨胀，
--   也与「输入原文不落库」的原则冲突。没有它，同一个 traceId 只能看到
--   「用了什么模型」，看不到「当时喂进去的是什么」，无法判断当时的输出是否合理。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema
--   判断后动态执行 DDL（写法与 aig_model_capability_tags.sql、
--   aig_route_scenario_binding.sql 一致），可安全重复执行。
--
-- 兼容性：只加可空列、不改既有列、不动索引；既有审计行的三列均为 NULL，
--   含义是「补列之前发生的调用，当时没有采集」——不是「没有用量」。
--   读审计时不要把这两种情况混为一谈。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_invocation_audit'
        AND COLUMN_NAME = 'provider_id'),
    'SELECT ''aig_invocation_audit.provider_id already exists'' AS note',
    'ALTER TABLE aig_invocation_audit ADD COLUMN provider_id BIGINT NULL COMMENT ''实际使用的供应商ID（当时那一次的归属，不随模型改归属而变）'' AFTER model_id')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_invocation_audit'
        AND COLUMN_NAME = 'usage_json'),
    'SELECT ''aig_invocation_audit.usage_json already exists'' AS note',
    'ALTER TABLE aig_invocation_audit ADD COLUMN usage_json VARCHAR(1000) NULL COMMENT ''模型用量回执（JSON，如 {"tokensUsed":123}）；为空表示未拿到用量'' AFTER cost')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_invocation_audit'
        AND COLUMN_NAME = 'input_snapshot_ref'),
    'SELECT ''aig_invocation_audit.input_snapshot_ref already exists'' AS note',
    'ALTER TABLE aig_invocation_audit ADD COLUMN input_snapshot_ref VARCHAR(500) NULL COMMENT ''不可变输入快照引用（只存引用不存副本，事后复现的唯一入口）'' AFTER input_hash')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：三列都在
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_invocation_audit'
   AND COLUMN_NAME IN ('provider_id', 'usage_json', 'input_snapshot_ref')
 ORDER BY ORDINAL_POSITION;

-- 核对：按供应商对账的口径可查（旧行 provider_id 为 NULL，新行有值）
SELECT COUNT(*)                                                        AS total_rows,
       SUM(CASE WHEN provider_id IS NULL THEN 1 ELSE 0 END)            AS rows_without_provider,
       SUM(CASE WHEN usage_json IS NOT NULL THEN 1 ELSE 0 END)         AS rows_with_usage
  FROM aig_invocation_audit;

SELECT 'AIG_AUDIT_PROVIDER_USAGE_DONE' AS marker;
