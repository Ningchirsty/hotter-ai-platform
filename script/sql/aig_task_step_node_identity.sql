-- ------------------------------------------------------------------
-- AI 治理：任务步骤账本补「流程节点身份」两列
--   aig_task_step.node_type / aig_task_step.node_ref
--
-- 背景（为什么必须落在账本上）：
--   流程节点此前只存在于"冻结的执行计划"里（版本 config_json -> AigExecutionPlan.flowNodes），
--   而计划**不落库**。于是"这个步骤是人工确认还是普通步骤"这件事，只有正在执行的那一次
--   进程内有；一旦任务因为"需要人工确认"停下来，重启/换一次请求就再也认不出它。
--   后果很具体：人工确认的**恢复入口做不出来**——确认接口必须能回答
--   "你批的到底是不是人工确认节点"，没有这份信息就只能盲批（那等于没有闸门）。
--
-- 语义：
--   node_type —— 节点类型（AigFlowNodeTypeEnum：STEP / CONDITION / HUMAN_CONFIRM / OUTPUT）；
--   node_ref  —— 节点引用（条件判断的键、步骤标识、输出契约键）。
--   两者对 kind != 'FLOW_NODE' 的步骤为空（子 Agent 步骤没有流程节点身份）。
--
-- 存量行不回填：本列上线前登记的 FLOW_NODE 步骤确实没有这份信息，
--   回填一个猜出来的类型会把"不知道"写成"知道"。驱动遇到没有身份的步骤时
--   退回执行计划（同一次执行内仍然可用），并在日志里留痕。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后动态执行 DDL
--   （写法与 aig_sandbox_run_attestation.sql / aig_evaluation_run_executed_by.sql 一致）。
-- 兼容性：只加两列（可空）、不改既有列、不动索引。
--
-- 注意：script/sql/aig_task_step.sql（建表脚本）里也已含这两列；本脚本是给**存量库**补齐的。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_task_step'
        AND COLUMN_NAME = 'node_type'),
    'SELECT ''aig_task_step.node_type already exists'' AS note',
    'ALTER TABLE aig_task_step ADD COLUMN node_type VARCHAR(24) NULL COMMENT ''流程节点类型（AigFlowNodeTypeEnum：STEP/CONDITION/HUMAN_CONFIRM/OUTPUT；非 FLOW_NODE 步骤为空）'' AFTER kind')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_task_step'
        AND COLUMN_NAME = 'node_ref'),
    'SELECT ''aig_task_step.node_ref already exists'' AS note',
    'ALTER TABLE aig_task_step ADD COLUMN node_ref VARCHAR(128) NULL COMMENT ''流程节点引用（条件判断的键/步骤标识/输出契约键；非 FLOW_NODE 步骤为空）'' AFTER node_type')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：两列存在、类型与可空性正确
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_task_step'
   AND COLUMN_NAME IN ('node_type', 'node_ref')
 ORDER BY ORDINAL_POSITION;

-- 核对：FLOW_NODE 步骤的身份覆盖情况（存量行会是 0 = 没回填，属预期）
SELECT kind,
       SUM(CASE WHEN node_type IS NULL THEN 1 ELSE 0 END) AS without_identity,
       SUM(CASE WHEN node_type IS NOT NULL THEN 1 ELSE 0 END) AS with_identity
  FROM aig_task_step
 GROUP BY kind;

-- 核对：按节点类型看分布（能一眼看出有没有人工确认节点被登记）
SELECT node_type, COUNT(*) AS rows_count
  FROM aig_task_step
 GROUP BY node_type;

SELECT 'AIG_TASK_STEP_NODE_IDENTITY_DDL_DONE' AS marker;
