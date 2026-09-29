-- ------------------------------------------------------------------
-- V0.2 / R22：模块规划（文档 §24）
--
-- §24 要求一个真正的"模块规划"界面：左侧可用模块库 / 中间当前顺序（拖动排序、添加、删除、复制、
-- 启用停用）/ 右侧模块目标、对应卖点、文案、所需事实、视觉表达、参考图、Workflow、模板。
-- R21 的 `dp_project_module` 只有"快照 + 屏数 + 顺序 + 状态"，右侧那一列字段没有地方落，
-- 所以本轮补列；同时**放开唯一键**，因为「复制」要求同一个模块能在一个项目里出现多次。
--
-- 幂等：加列/删索引都用 information_schema 守卫 + PREPARE 动态执行（MySQL 8 没有
--       `ADD COLUMN IF NOT EXISTS`）。守卫必须能重复跑——这个脚本会被 apply 脚本整体重放。
--
-- 与 R21 的关系（改之前先读）：
--   * 屏集合的**权威**仍是 `dp_project_module`（顺序 + screen_count + enabled），
--     本文件只加"右侧字段"与"允许重复"；
--   * `enabled='0'` 表示启用（本项目约定：0启用 1停用），停用的模块**不参与出屏**，
--     但它留在计划里（可见、可再启用），不是删除。
-- ------------------------------------------------------------------

-- 1) 右侧字段（§24）
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE dp_project_module ADD COLUMN objective VARCHAR(500) NULL COMMENT ''模块目标（可覆盖模块定义，§24 右栏）''',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module' AND COLUMN_NAME = 'objective');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE dp_project_module ADD COLUMN selling_point_codes VARCHAR(500) NULL COMMENT ''对应卖点（文案块编码，逗号分隔，§24 右栏）''',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module' AND COLUMN_NAME = 'selling_point_codes');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE dp_project_module ADD COLUMN copy_text VARCHAR(1000) NULL COMMENT ''人工文案（最高优先级，覆盖草稿与模型，§24 右栏）''',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module' AND COLUMN_NAME = 'copy_text');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE dp_project_module ADD COLUMN required_fact_codes VARCHAR(500) NULL COMMENT ''所需事实（事实字段码，逗号分隔，§24 右栏）''',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module' AND COLUMN_NAME = 'required_fact_codes');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE dp_project_module ADD COLUMN visual_rules_json TEXT NULL COMMENT ''视觉表达（JSON 文本，§24 右栏）''',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module' AND COLUMN_NAME = 'visual_rules_json');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE dp_project_module ADD COLUMN reference_codes VARCHAR(500) NULL COMMENT ''参考图（附件文件ID或编码，逗号分隔，§24 右栏）''',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module' AND COLUMN_NAME = 'reference_codes');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE dp_project_module ADD COLUMN workflow_codes VARCHAR(500) NULL COMMENT ''Workflow/能力编码（逗号分隔，取第一个用于该模块出图）''',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module' AND COLUMN_NAME = 'workflow_codes');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE dp_project_module ADD COLUMN template_codes VARCHAR(500) NULL COMMENT ''模板码（逗号分隔，§24 右栏）''',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module' AND COLUMN_NAME = 'template_codes');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 启用/停用（§24 中间栏）：0启用 1停用，与 dp_module_definition.enabled 同约定。
-- 已有行默认 '0'（启用）——R21 建的计划本来每一行都参与出屏，不能因为加列就改变既有行为。
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE dp_project_module ADD COLUMN enabled CHAR(1) NOT NULL DEFAULT ''0'' COMMENT ''是否启用（0启用 1停用；停用不出屏但留在计划里）''',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module' AND COLUMN_NAME = 'enabled');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) 放开唯一键：(task_id, module_code) 会让"复制"直接撞唯一键。
--    换成普通索引，保留按模块编码回查的能力。
SET @ddl = (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE dp_project_module DROP INDEX uk_dp_project_module', 'DO 0')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module' AND INDEX_NAME = 'uk_dp_project_module');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE dp_project_module ADD INDEX idx_dp_project_module_code (task_id, module_code)', 'DO 0')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module' AND INDEX_NAME = 'idx_dp_project_module_code');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SELECT 'DP_CREATIVE_R22_MODULE_PLAN_DONE' AS marker;

-- 执行后核对
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module'
 ORDER BY ORDINAL_POSITION;

SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols, NON_UNIQUE
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_project_module'
 GROUP BY INDEX_NAME, NON_UNIQUE;
