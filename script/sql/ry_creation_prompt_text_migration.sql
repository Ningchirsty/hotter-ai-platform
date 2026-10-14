-- 已有图像/视频任务表的提示词容量迁移；新库由对应建表脚本直接使用 TEXT。
-- API 已允许图像模板 8000 字符、云端视频 10000 字符，VARCHAR(1000) 会使保存失败。
-- utf8mb4 的 TEXT 可容纳这些完整提示词；保留 NULL，不截断、不改写旧记录。
-- 仅扩展 VARCHAR 字段；TEXT/MEDIUMTEXT/LONGTEXT 不收窄，可重复执行。
SET @ddl := (
  SELECT IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'image_task'
      AND COLUMN_NAME = 'prompt' AND DATA_TYPE = 'varchar'
  ), 'ALTER TABLE image_task MODIFY COLUMN prompt TEXT NULL COMMENT ''图像描述（支持完整模板提示词）''',
     'SELECT ''image_task.prompt needs no expansion'' AS note')
);
PREPARE hotter_prompt_ddl FROM @ddl;
EXECUTE hotter_prompt_ddl;
DEALLOCATE PREPARE hotter_prompt_ddl;

SET @ddl := (
  SELECT IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'video_task'
      AND COLUMN_NAME = 'prompt' AND DATA_TYPE = 'varchar'
  ), 'ALTER TABLE video_task MODIFY COLUMN prompt TEXT NULL COMMENT ''视频描述（支持完整模板提示词）''',
     'SELECT ''video_task.prompt needs no expansion'' AS note')
);
PREPARE hotter_prompt_ddl FROM @ddl;
EXECUTE hotter_prompt_ddl;
DEALLOCATE PREPARE hotter_prompt_ddl;
