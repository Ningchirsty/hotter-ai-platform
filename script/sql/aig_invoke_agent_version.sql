-- ------------------------------------------------------------------
-- AI 治理：逐次调用审计补「Agent 版本」列（aig_invocation_audit.agent_version_id）
--
-- 背景（为什么补、为什么这一列填得上）：
--   C3 第二块「评测灰度」的达标判据是按 **Agent 版本** 统计调用次数 / 失败率 /
--   是否出现严重错误，然后才允许 CANDIDATE → STABLE 的 CANARY 门槛通过。
--   而审计表原先只有 model_id / model_key / model_version —— 那是**模型**版本，
--   不是治理层发布的 **Agent** 版本：同一个模型可能挂着多个 Agent 版本，
--   按模型维度统计根本无法回答「这个版本灰度期间跑了多少次、失败几次」。
--   在此之前 CANARY 门槛**没有任何证据校验**（只有 MANIFEST_VALIDATION 与
--   GOLDEN_CASE 两个断言），即「灰度达标」完全由调用方声明——这一列是补上该证据的前提。
--
--   这一列**填得上，不是空列**：`aig_task.agent_version_id` 在任务域本来就存在
--   （AigTaskCreateBo / AigTaskVo 都有），只是构造调用入参 AigInvokeBo 时没带下来，
--   传到统一调用入口就丢了。随本列一起打通：AigInvokeBo → AigTaskExecutorImpl →
--   AigInvokeServiceImpl#buildAuditContext → AigAuditContext → AigAuditRecorder → 本表。
--
-- 语义：为空 = 本次调用**没有绑定到某个 Agent 版本**（例如直接调能力、不经任务），
--   不是「不知道」。因此本列**必须可空**，不能给默认值或设为 NOT NULL。
--
-- 为什么建索引：灰度的达标证据是按版本 + 时间窗口数行
--   （count(*) where agent_version_id = ? and operate_time >= ?），
--   而审计表是逐次追加、只增不减的；没有索引就是全表扫描，越跑越慢。
--   与人均配额沿用的 idx_aig_audit_caller (caller_id, operate_time) 同形。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN / ADD INDEX IF NOT EXISTS，这里用
--   information_schema 判断后动态执行 DDL（写法与 aig_package_version_body_ref.sql 一致）。
--
-- 兼容性：只加一列可空 + 一个索引，不改既有列、不动既有数据。
--
-- 注意：script/sql/aig_ai_gov.sql（建表脚本）里也已经加上了这一列与索引，
--   所以全新库跑建表脚本即可；本脚本是给**存量库**补齐的（可重复执行）。
-- ------------------------------------------------------------------

-- 1) 补列
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_invocation_audit'
        AND COLUMN_NAME = 'agent_version_id'),
    'SELECT ''aig_invocation_audit.agent_version_id already exists'' AS note',
    'ALTER TABLE aig_invocation_audit ADD COLUMN agent_version_id BIGINT NULL COMMENT ''本次调用所属的 Agent 版本ID（治理层发布的 aig_agent_version.id；为空=本次未绑定 Agent 版本，非「不知道」）'' AFTER model_version')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 2) 补索引（灰度按版本+时间窗口统计调用次数/失败率时用）
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_invocation_audit'
        AND INDEX_NAME = 'idx_aig_audit_agent_version'),
    'SELECT ''idx_aig_audit_agent_version already exists'' AS note',
    'ALTER TABLE aig_invocation_audit ADD INDEX idx_aig_audit_agent_version (agent_version_id, operate_time)')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 3) 核对：列存在且可空
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_invocation_audit'
   AND COLUMN_NAME = 'agent_version_id';

-- 4) 核对：索引存在
SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_invocation_audit'
   AND INDEX_NAME = 'idx_aig_audit_agent_version'
 ORDER BY SEQ_IN_INDEX;
