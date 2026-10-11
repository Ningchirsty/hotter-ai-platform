-- ----------------------------------------------------------------------------
-- cp_task 增加 platform_task_id（岗位场景派发 → 内容链路；增量 15）
--
-- 为什么需要它：
--   门户的"场景任务派发"把平台任务交给内容链路执行（AigScenarioFlowPort）。
--   平台任务可能被人工重新入队 → 执行器会**再派发一次**；没有来源标记的话，
--   内容侧会建出第二条 cp_task（重复建任务、重复占产线）。
--   记下"这条内容任务来自哪个平台任务"，并在列上建**唯一索引**，
--   就同时得到两件事：可追溯、以及"同一平台任务只建一条内容任务"。
--
-- 为什么唯一索引能容忍历史数据：
--   MySQL/MariaDB 的**唯一索引不约束 NULL**——存量 cp_task 的 platform_task_id 都是 NULL，
--   只要一条 cp_task 不重复引用同一个非 NULL 平台任务即可。（注意：正因为 NULL 不被约束，
--   "一个平台任务只建一条"这条约束**只对派发创建的行成立**，这正是我们要的。）
--
-- 幂等：列/索引都用 information_schema 反查后再 ALTER（where not exists 无法用在 ALTER 上）；
--   重复执行只会打印"already exists"，不报错、不改数据。
-- 前置：cp_content.sql 已执行（本脚本只做增量）。
-- ----------------------------------------------------------------------------

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_task'
                       AND COLUMN_NAME = 'platform_task_id');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE cp_task ADD COLUMN platform_task_id BIGINT NULL COMMENT ''来源平台任务ID（岗位场景派发时写入；唯一，NULL=非派发创建）'' AFTER product_id',
    'SELECT ''cp_task.platform_task_id already exists'' AS note');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_task'
                       AND INDEX_NAME = 'uk_cp_task_platform');
SET @sql2 := IF(@idx_exists = 0,
    'ALTER TABLE cp_task ADD UNIQUE KEY uk_cp_task_platform (platform_task_id)',
    'SELECT ''uk_cp_task_platform already exists'' AS note');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 核对：列与唯一索引都在
select c.column_name, c.is_nullable, c.column_comment
  from information_schema.columns c
 where c.table_schema = database() and c.table_name = 'cp_task' and c.column_name = 'platform_task_id';

select s.index_name, s.column_name, s.non_unique
  from information_schema.statistics s
 where s.table_schema = database() and s.table_name = 'cp_task' and s.index_name = 'uk_cp_task_platform';

select 'CP_TASK_PLATFORM_REF_DONE' as marker;
