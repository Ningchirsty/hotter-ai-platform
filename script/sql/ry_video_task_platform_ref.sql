-- ----------------------------------------------------------------------------
-- video_task 增加 platform_task_id（岗位场景派发 → 视频链路；增量 17）
--
-- 为什么需要它：
--   门户的"场景任务派发"把平台任务交给视频链路执行（AigScenarioFlowPort）。
--   平台任务可能被人工重新入队 → 执行器会**再派发一次**；没有来源标记的话，
--   视频侧会建出第二条 video_task（重复占 GPU、重复计费）。
--   记下"这条视频任务来自哪个平台任务"并在列上建**唯一索引**，
--   同时得到两件事：可追溯、以及"同一平台任务只建一条视频任务"。
--
-- 为什么唯一索引能容忍历史数据：
--   MySQL/MariaDB 的**唯一索引不约束 NULL**——存量 video_task 的 platform_task_id 都是 NULL，
--   只要一条视频任务不重复引用同一个非 NULL 平台任务即可。
--
-- 幂等：列/索引都先查 information_schema 再 ALTER；重复执行只打印 already exists。
-- 前置：ry_video_task.sql 已执行（本脚本只做增量）。
-- ----------------------------------------------------------------------------

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'video_task'
                       AND COLUMN_NAME = 'platform_task_id');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE video_task ADD COLUMN platform_task_id BIGINT NULL COMMENT ''来源平台任务ID（岗位场景派发时写入；唯一，NULL=非派发创建）'' AFTER idempotency_key',
    'SELECT ''video_task.platform_task_id already exists'' AS note');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'video_task'
                       AND INDEX_NAME = 'uk_task_platform');
SET @sql2 := IF(@idx_exists = 0,
    'ALTER TABLE video_task ADD UNIQUE KEY uk_task_platform (platform_task_id)',
    'SELECT ''uk_task_platform already exists'' AS note');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 核对：列与唯一索引都在
select c.column_name, c.is_nullable, c.column_comment
  from information_schema.columns c
 where c.table_schema = database() and c.table_name = 'video_task' and c.column_name = 'platform_task_id';

select s.index_name, s.column_name, s.non_unique
  from information_schema.statistics s
 where s.table_schema = database() and s.table_name = 'video_task' and s.index_name = 'uk_task_platform';

select 'VIDEO_TASK_PLATFORM_REF_DONE' as marker;
