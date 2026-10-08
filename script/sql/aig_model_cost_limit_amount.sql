-- ------------------------------------------------------------------
-- AI 治理：单次成本上限 cost_limit_amount（aig_model_governance）
--
-- 背景（为什么另开一列，而不是把 cost_limit 改成数字）：
--   设计 §4.4 第 3 步要求「过滤…超过预算…的 Provider」。仓库里对应的字段是
--   aig_model_governance.cost_limit，但它的语义从上线起就是**人读描述**
--   （varchar(255)，前端占位「单次/单项目/单日预算与限流规则」），已有数据是文字。
--   直接把它当数字解析 = 重新定义这个字段，并让既有配置一次性失效；而且
--   「单项目/单日」这类规则本来就塞不进一个数字。
--
--   因此两列并存、各司其职：
--     · cost_limit        —— 规则说明（人读），不参与任何判定；
--     · cost_limit_amount —— 单次成本上限（decimal），路由在调用前与本次预算比对。
--
--   为什么比对的是「声明的单次上限」而不是实际花费：路由发生在调用**之前**，
--   此时没有实际费用可依；而多数外部供应商不回执费用（审计 cost 列常为空，
--   为空=未知≠免费）。「声明上限 vs 本次预算」是唯一在调用前可判定的口径，
--   且它是保守的：声明上限高于预算就直接不选，而不是「先花了再说」。
--   累计预算（单项目/单日）刻意不在这里做——它需要真实费用回执做账，
--   没有账本只会做出一个对不上的假预算。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema
--   判断后动态执行 DDL（写法与 aig_model_capability_tags.sql、
--   aig_route_scenario_binding.sql、aig_audit_provider_usage.sql 一致）。
--
-- 兼容性：只加一列可空、不改既有列、不动索引；既有行该列为 NULL，含义是
--   **未声明** —— 默认放行并写入可见提示（配 aigov.route.require-model-cost=true
--   后改为严格排除）。这与能力标签同一取舍：新列一上线就默认严格，
--   会把所有既有模型同时排除干净，看起来更安全，实际是把功能一次掐死。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_model_governance'
        AND COLUMN_NAME = 'cost_limit_amount'),
    'SELECT ''aig_model_governance.cost_limit_amount already exists'' AS note',
    'ALTER TABLE aig_model_governance ADD COLUMN cost_limit_amount DECIMAL(18,8) NULL COMMENT ''单次成本上限（机器可判定）：路由在调用前与本次预算比对；单位=美元USD；NULL=未声明（默认放行并提示，aigov.route.require-model-cost=true 时严格排除）'' AFTER cost_limit')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：列存在，且既有行全为 NULL（未声明）
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_model_governance'
   AND COLUMN_NAME = 'cost_limit_amount';

SELECT COUNT(*)                                                          AS total_models,
       SUM(CASE WHEN cost_limit_amount IS NULL THEN 1 ELSE 0 END)         AS undeclared_cost_cap,
       SUM(CASE WHEN cost_limit_amount IS NOT NULL THEN 1 ELSE 0 END)     AS declared_cost_cap
  FROM aig_model_governance
 WHERE del_flag = '0';

SELECT 'AIG_MODEL_COST_LIMIT_AMOUNT_DONE' AS marker;
