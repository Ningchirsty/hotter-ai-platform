-- ------------------------------------------------------------------ 
-- AI 治理：Skill 版本补「来源 Package 版本」列（aig_skill_version.package_version_id）
--
-- 背景（为什么必须补）：
--   Agent 版本表一早就带 package_version_id（第三方 Package 带入时指向 aig_package_version），
--   Skill 版本表却没有——这是本组表自身的不一致：**同一次安装带进来的两类对象，
--   一类能回溯来源包、另一类不能**。影响两件事：
--     1) 安装的幂等判据：应当能直接查「这个 Package 版本装过没有」。Skill 版本没有这一列时，
--        只能去翻 aig_package_install_log（审计账本），而日志是「发生过什么」，
--        不是「现在是什么状态」；
--     2) 复盘：要回答「这个 Skill 版本是哪个包带进来的」，原先只能靠 config_json 里的字符串。
--
--   本轮新增 Package 上传/安装链路时暴露出来，因此在这里补齐（Agent 版本已有，不动）。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema
--   判断后动态执行 DDL（写法与 aig_model_capability_tags.sql / aig_model_cost_limit_amount.sql 一致）。
--
-- 兼容性：只加一列可空、不改既有列、不动索引；既有行该列为 NULL，含义是
--   **不是第三方 Package 带入的**（内置 Skill 或历史数据），与 Agent 版本的口径一致。
--
-- 注意：script/sql/aig_agent_registry.sql（建表脚本）里也已经加上了这一列，
--   所以全新库跑建表脚本即可；本脚本是给**存量库**补齐的（可重复执行）。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_skill_version'
        AND COLUMN_NAME = 'package_version_id'),
    'SELECT ''aig_skill_version.package_version_id already exists'' AS note',
    'ALTER TABLE aig_skill_version ADD COLUMN package_version_id BIGINT(20) NULL COMMENT ''来源 Package 版本（第三方 Package 带入时指向 aig_package_version；内置 Skill 为空）'' AFTER allow_external')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：列存在
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_skill_version'
   AND COLUMN_NAME = 'package_version_id';
