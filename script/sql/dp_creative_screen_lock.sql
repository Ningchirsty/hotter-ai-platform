-- ============================================================================
-- 分镜「逐屏锁定」（v1 人工测试反馈裁定 ④，2026-10-06）
-- ----------------------------------------------------------------------------
-- 裁定原文：「由使用人说了算，可自定义不同的屏数，**可以原地锁定一个屏幕**，但其余可以自定义」。
--
-- 改造前：锁定是**整版**的（`dp_storyboard.status = LOCKED`），一锁全锁，
--         `updateScreen` 直接拒绝；屏集合在生成那一刻由模块计划定死，生成之后没有加/删屏的口子。
--
-- 这一列是"逐屏锁定"的落点：`lock_status` = DRAFT（未锁，可改）/ LOCKED（已锁，冻结这一屏）。
--
-- **为什么用 DRAFT/LOCKED 而不是 0/1**：分镜表自己用的就是 `status = DRAFT/LOCKED`，
-- 同一套词在这里读起来不用查文档；而 0/1 会撞上 RuoYi 的"0＝正常/启用"惯例，
-- 让 `locked=0` 到底是"没锁"还是"锁了"变成一个必须翻代码的问题。
--
-- 边界（本轮同时定的两条，写在这里便于以后查）：
--   * 加屏/删屏只允许发生在**整版锁定之前**（锁定即冻结屏集合）；
--   * 出图闸门仍是**整版锁定**——单屏锁定的用途是"草稿期防误改"，不是"逐屏放行出图"。
--
-- 存量回填：整版已 LOCKED 的分镜，它的屏今天事实上就是被冻结的，所以按事实标成 LOCKED；
--          其余屏保持 DRAFT（不假装它们被锁过）。
--
-- 幂等：ALTER 前先查列是否存在（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）。
-- 回滚见文件末尾。
-- ============================================================================

-- 1) 加列（只在列不存在时执行）
SET @has_lock := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_storyboard_screen'
                     AND COLUMN_NAME = 'lock_status');
SET @sql := IF(@has_lock = 0,
  'ALTER TABLE dp_storyboard_screen ADD COLUMN lock_status VARCHAR(20) NOT NULL DEFAULT ''DRAFT'' COMMENT ''逐屏锁定（DRAFT 未锁可改 / LOCKED 已锁冻结这一屏）''',
  'SELECT ''lock_status 已存在，跳过'' AS note');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 存量回填：跟着分镜版本的事实走
UPDATE dp_storyboard_screen s
  JOIN dp_storyboard b ON b.id = s.storyboard_id
   SET s.lock_status = 'LOCKED'
 WHERE s.del_flag = '0' AND b.status = 'LOCKED' AND s.lock_status <> 'LOCKED';

-- 3) 核对：逐屏锁定状态 × 分镜版本状态（期望：LOCKED 版本的屏全是 LOCKED；DRAFT 版本全是 DRAFT）
SELECT '=== 逐屏锁定 vs 版本状态 ===' AS s;
SELECT b.status AS storyboard_status, s.lock_status, COUNT(*) AS screens
  FROM dp_storyboard_screen s
  JOIN dp_storyboard b ON b.id = s.storyboard_id
 WHERE s.del_flag = '0'
 GROUP BY b.status, s.lock_status
 ORDER BY b.status, s.lock_status;

SELECT '=== 列是否建好（期望 1 行）===' AS s;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_storyboard_screen'
   AND COLUMN_NAME = 'lock_status';

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------
-- ALTER TABLE dp_storyboard_screen DROP COLUMN lock_status;
