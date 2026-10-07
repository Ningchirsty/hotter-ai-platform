-- ------------------------------------------------------------------
-- AI 治理：Package 版本补「包体对象键」列（aig_package_version.body_ref）
--
-- 背景（为什么补、为什么补在版本表而不是包表）：
--   第二期 B2：把上传的包体留在对象存储，并把对象键记下来，以便事后复核
--   （重新读回对象、重算哈希，确认留存的那份与当初登记的 checksum 是同一份）。
--
--   列必须落在 **aig_package_version**（逐版本）而不是 aig_package（逐包）：
--   每次上传都可能带**不同的包体**（这正是 checksum 随每次上传更新的原因），
--   包表只放得下一个包体键，第二个版本就会把第一个覆盖掉——那时「这个版本对应哪份包体」
--   就永久查不到了。source_ref 保持原义（调用方给的可信来源引用），不挪作他用。
--
--   为什么**不登记 sys_oss**：包体属于治理链路的输入证据，若登记进 sys_oss，
--   任何持有 system:oss:download 的账号都能绕过治理模块授权直接下载第三方包体。
--   因此对象写在私有前缀 aig-private/ 下、只由本模块按键访问（与内容域同口径）。
--
-- 语义：为空 = 该版本未留存包体（未开启 aigov.package.store-body，或本列上线前的历史版本）。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema
--   判断后动态执行 DDL（写法与 aig_skill_version_package_ref.sql 一致）。
--
-- 兼容性：只加一列可空、不改既有列、不动索引。
--
-- 注意：script/sql/aig_agent_registry.sql（建表脚本）里也已经加上了这一列，
--   所以全新库跑建表脚本即可；本脚本是给**存量库**补齐的（可重复执行）。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_package_version'
        AND COLUMN_NAME = 'body_ref'),
    'SELECT ''aig_package_version.body_ref already exists'' AS note',
    'ALTER TABLE aig_package_version ADD COLUMN body_ref VARCHAR(500) NULL COMMENT ''包体对象键（aigov.package.store-body=true 时写入；私有前缀且不登记 sys_oss）'' AFTER manifest_hash')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：列存在
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_package_version'
   AND COLUMN_NAME = 'body_ref';
