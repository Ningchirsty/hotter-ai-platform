-- ------------------------------------------------------------------
-- AI 视觉工厂：R1 增量迁移（视觉基因 → 出图的留痕列）
--
-- 背景：R1 起出图不再是「一张图随便说说」，而是「按某一版视觉基因做的」。
-- dp_generation 已经记了 workflow/候选/执行身份，但没记「用的是哪版基因、哪个方向、哪版分镜」，
-- 于是事后无法回答「这批图是按哪版基因生成的」。本迁移只补这三个可空外键列。
--
-- 说明：
--   1. 幂等：用 information_schema 判断后再动态执行 DDL（写法与 dp_creative.sql 一致）；
--   2. 只加列、不改既有数据；R0 期间产生的历史候选这三列为 NULL，含义是「早于基因功能」，
--      不是「没有基因」——页面按「历史候选」展示，不做反推；
--   3. 必须在部署带基因留痕逻辑的后端镜像之前执行。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'dp_generation'
        AND COLUMN_NAME = 'dna_id'
    ),
    'SELECT ''dp_generation.dna_id already exists'' AS note',
    'ALTER TABLE dp_generation ADD COLUMN dna_id BIGINT NULL COMMENT ''生成时采用的视觉基因版本（dp_visual_dna.id；R0 历史候选为 NULL）'' AFTER screen_id'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'dp_generation'
        AND COLUMN_NAME = 'direction_id'
    ),
    'SELECT ''dp_generation.direction_id already exists'' AS note',
    'ALTER TABLE dp_generation ADD COLUMN direction_id BIGINT NULL COMMENT ''生成时采用的视觉方向（dp_visual_direction.id）'' AFTER dna_id'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'dp_generation'
        AND COLUMN_NAME = 'storyboard_id'
    ),
    'SELECT ''dp_generation.storyboard_id already exists'' AS note',
    'ALTER TABLE dp_generation ADD COLUMN storyboard_id BIGINT NULL COMMENT ''生成时采用的分镜版本（dp_storyboard.id）'' AFTER direction_id'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

-- 执行结果核对
SELECT column_name, column_type, is_nullable, column_comment
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'dp_generation'
   AND column_name IN ('dna_id', 'direction_id', 'storyboard_id')
 ORDER BY ordinal_position;

SELECT 'DP_CREATIVE_R1_MIGRATION_DONE' AS marker;
