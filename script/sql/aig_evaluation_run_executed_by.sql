-- ------------------------------------------------------------------
-- AI 治理：评测运行补「结论产出方」列（aig_evaluation_run.executed_by）
--
-- 背景（为什么需要它）：
--   发布门槛「黄金用例通过」只认 aig_evaluation_run.result_status=PASS，
--   但 PASS 有两种来源：
--     · PLATFORM —— 平台评测执行器（IAigEvaluationSubject）跑出来、判据由平台求值；
--     · ADMIN    —— 平台没有该对象的执行器时，管理员人工评测后录入（2026-10-09 裁定）。
--   两者都合法，但可信度来源不同，评审必须能分开看。没有这一列时，
--   人工结论与机器结论在库里长得一模一样 ⇒ 人工结论会伪装成机器结论。
--
--   为什么不写进 remark：remark 是自由文本，且会被人工复核「追加」
--   （AigEvaluationServiceImpl#reviewRun），性质放在会变的地方等于没放。
--
-- 语义：存量行一律回填 'PLATFORM'——本列上线前，所有运行都是平台执行器跑的，这是事实而非猜测。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema 判断后
--   动态执行 DDL（写法与 aig_package_version_body_ref.sql / aig_skill_version_package_ref.sql 一致）。
--
-- 兼容性：只加一列（非空带默认值）、不改既有列、不动索引。
--
-- 注意：script/sql/aig_agent_registry.sql（建表脚本）里也已加上了这一列，
--   所以全新库跑建表脚本即可；本脚本是给**存量库**补齐的（可重复执行）。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_evaluation_run'
        AND COLUMN_NAME = 'executed_by'),
    'SELECT ''aig_evaluation_run.executed_by already exists'' AS note',
    'ALTER TABLE aig_evaluation_run ADD COLUMN executed_by VARCHAR(16) NOT NULL DEFAULT ''PLATFORM'' COMMENT ''结论产出方（PLATFORM=平台执行器跑的；ADMIN=管理员人工评测后录入）'' AFTER model_code')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：列存在且取值域只有两种
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_evaluation_run'
   AND COLUMN_NAME = 'executed_by';

SELECT executed_by, COUNT(*) AS rows_count
  FROM aig_evaluation_run
 GROUP BY executed_by;

SELECT 'AIG_EVALUATION_RUN_EXECUTED_BY_DDL_DONE' AS marker;
