-- ------------------------------------------------------------------
-- AI 治理：沙箱运行证据补「可信度来源」列（aig_sandbox_run.attestation）
--
-- 背景（为什么需要它，以及它**不**解决什么）：
--   发布门槛 SANDBOX_RUN 的证据是"管理员把执行器输出的 result.json 登记进来"。
--   账本能证明：有人**提交了**这份结果、提交后**没被改过**（原文 + 服务端实算 SHA-256）、
--   字段自洽。它**不能**证明：这份结果来自一次真实运行——image/exitCode/network 全是提交方自述。
--   任何持有 aig:sandbox:record 的人可以在文本框里编一条通过的记录，沙箱一次都不用跑。
--
--   真正闭环需要"平台能验、不能造"的签名（执行器用只有 root 能读的私钥签名，平台用公钥验签），
--   本轮**没有做**（决定见 ADR-015「已知边界」）。但"没做"不能靠文档里一句话兜着：
--   在库里、在接口里、在界面上都必须是显式的，否则这条证据会被读成比它实际更强的东西。
--
-- 语义：本列记录"这条证据是怎么来的"，取值：
--   UNATTESTED —— 人工登记，**无密码学保证**（当前所有行都是这个值，也是列默认值）；
--   SIGNED     —— 执行器私钥签名且平台公钥验签通过（**尚未实现**，服务端只有在验签实现后
--                才可能写入这个值；提交方无法自称，接口里根本没有这个入参）。
--
-- 存量行回填 UNATTESTED：本列上线前所有证据都是人工登记的，这是事实而非猜测。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后动态执行 DDL
--   （写法与 aig_evaluation_run_executed_by.sql 一致）。
-- 兼容性：只加一列（非空带默认值）、不改既有列、不动索引。
--
-- 注意：script/sql/aig_sandbox_run.sql（建表脚本）里也已含这一列；本脚本是给**存量库**补齐的。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_sandbox_run'
        AND COLUMN_NAME = 'attestation'),
    'SELECT ''aig_sandbox_run.attestation already exists'' AS note',
    'ALTER TABLE aig_sandbox_run ADD COLUMN attestation VARCHAR(32) NOT NULL DEFAULT ''UNATTESTED'' COMMENT ''证据可信度来源（UNATTESTED=人工登记、无密码学保证；SIGNED=执行器私钥签名+平台公钥验签，未实现）'' AFTER result_json')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：列存在、默认值正确
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_sandbox_run'
   AND COLUMN_NAME = 'attestation';

-- 核对：按来源分组看现状（期望：要么 0 行，要么全部 UNATTESTED）
SELECT attestation, COUNT(*) AS rows_count
  FROM aig_sandbox_run
 GROUP BY attestation;

SELECT 'AIG_SANDBOX_RUN_ATTESTATION_DDL_DONE' AS marker;
