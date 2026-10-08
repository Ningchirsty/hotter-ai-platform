-- ------------------------------------------------------------------
-- AI 治理：任务表补「执行方」列（aig_task.execution_mode）
--
-- 背景（为什么需要它、为什么不能靠任务类型猜）：
--   任务表原先隐含假设「谁建任务，平台就负责执行它」——调度器扫 RETRY_WAIT 会把它
--   **重新入队**，扫 DISPATCHED/RUNNING 超时会把它判**失败**。
--   但业务域也可以自己执行任务：创作域的图像生成把编排留在**图像内核**
--   （ImageTaskSubmissionService），只把这次工作登记成一条 aig_task 以便在统一任务视图里
--   可见（设计 §9「新任务走 aig_task，dp_generation 只做只读镜像」的起步形态）。
--   这类任务若被平台当成自己的扫到，会出现两种都是真事故的结果：
--     · RETRY_WAIT 被重新入队 → 平台**再执行一遍**（两份产出、两次计费）；
--     · 在途被超时判失败 → 内核还在出图，账上已经 FAILED（甚至转人工重排，又一次计费）。
--
--   「谁执行」必须是数据里的显式事实：靠任务类型去猜，猜错的表现是
--   「平台把别人正在跑的任务又跑了一遍」，从日志上看不出来。
--
-- 语义：PLATFORM = 平台执行（走统一调用入口，调度器负责重试与超时判定）；
--   EXTERNAL = 业务域执行（平台只登记与展示，不执行、不扫描）。
--   **默认 PLATFORM**：存量行取默认值，因此本列上线**不改变任何既有任务的调度行为**。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema 判断后
--   动态执行 DDL（写法与 aig_invoke_agent_version.sql / aig_call_approval.sql 一致）。
--
-- 兼容性：只加一列且有默认值、not null，不改既有列、不动索引、不动既有数据。
--
-- 注意：script/sql/aig_ai_task.sql（建表脚本）里也已经加上了这一列，
--   所以全新库跑建表脚本即可；本脚本是给**存量库**补齐的（可重复执行）。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_task'
        AND COLUMN_NAME = 'execution_mode'),
    'SELECT ''aig_task.execution_mode already exists'' AS note',
    'ALTER TABLE aig_task ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT ''PLATFORM'' COMMENT ''执行方（PLATFORM=平台执行，走统一调用入口；EXTERNAL=业务域执行，平台只登记与展示、不执行也不扫描）'' AFTER status')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：列存在、默认值正确（存量行应当全是 PLATFORM）
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_task'
   AND COLUMN_NAME = 'execution_mode';

SELECT COALESCE(execution_mode, '(null)') AS mode, COUNT(*) AS rows_count
  FROM aig_task GROUP BY execution_mode;
