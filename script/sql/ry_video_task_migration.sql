-- ------------------------------------------------------------------
-- 视频创作模块：video_task 增量迁移
--
-- 背景：
--   双 GPU 后同一个 prompt_id 只在「提交它的那台 ComfyUI 进程」里可查，
--   排障时必须知道任务跑在哪张卡上，因此补一列 comfy_worker。
--
-- 说明：
--   1. 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用
--      information_schema 判断后再动态执行 DDL，可安全重复执行。
--   2. 只加列，不改既有数据；老任务该列为 NULL。
--   3. 必须在部署带 comfy_worker 写库逻辑的后端镜像「之前」执行，
--      否则 markSubmitted 会因 Unknown column 直接失败。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'video_task'
        AND COLUMN_NAME = 'comfy_worker'
    ),
    'SELECT ''video_task.comfy_worker already exists'' AS note',
    'ALTER TABLE video_task ADD COLUMN comfy_worker VARCHAR(64) NULL COMMENT ''承担本次生成的 ComfyUI 工作节点名（多 GPU 区分）'' AFTER comfy_prompt_id'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;
