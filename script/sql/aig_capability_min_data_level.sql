-- ------------------------------------------------------------------
-- AI 治理：能力声明的「最低数据等级」（aig_capability.min_data_level）增量迁移
--
-- 背景（为什么必须加这一列）：
--   数据等级此前**完全由调用方传入**。业务方（或一个图省事的集成）把一份客户名单
--   按 INTERNAL 发过来，路由就按 INTERNAL 找策略——可能落在一个允许外发的模型上。
--   等级标低拦不住，这是当前口径里最大的一个洞。
--
-- 口径（Q2C，已与业务确认）：
--   能力可**声明最低数据等级**；路由按 max(声明, 调用方传入) 生效。
--     · NULL / 空 = **不声明**（默认，等于现在的行为，存量能力完全不受影响）；
--     · 声明 RESTRICTED → 该能力的任何调用一律按 ≥ RESTRICTED 处理；
--     · 声明 STRICT → 该能力一律禁止外发（STRICT 由路由硬拒绝外发）。
--   **只抬不降**：声明只用于"把等级抬上去"，绝不允许用它把调用方标高的等级降下来。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema
--   判断后再动态执行 DDL，可安全重复执行（写法与 aig_model_capability_tags.sql 一致）。
--
-- 兼容性：只加列、不改既有数据、不动索引与唯一键；老数据该列为 NULL（=不声明）。
--   ⚠️ 本脚本已进 .github/workflows/deploy-poc.yml 的重放清单（由
--   AigDeploySchemaReplayCoverageTest 把关；漏了会"部署成功但接口 500"）。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_capability'
        AND COLUMN_NAME = 'min_data_level'),
    'SELECT ''aig_capability.min_data_level already exists'' AS note',
    'ALTER TABLE aig_capability ADD COLUMN min_data_level VARCHAR(16) NULL COMMENT ''能力声明的最低数据等级（PUBLIC/INTERNAL/RESTRICTED/STRICT）；路由按 max(声明, 调用方传入) 生效；NULL=不声明（等于调用方说了算）'' AFTER required_tags')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：列应存在，且既有行为 NULL（不声明）
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_capability'
   AND COLUMN_NAME = 'min_data_level';

SELECT COUNT(*) AS total_capabilities,
       SUM(CASE WHEN min_data_level IS NULL THEN 1 ELSE 0 END) AS undeclared_levels
  FROM aig_capability
 WHERE del_flag = '0';

SELECT 'AIG_CAPABILITY_MIN_DATA_LEVEL_DONE' AS marker;
