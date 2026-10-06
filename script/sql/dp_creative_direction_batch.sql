-- ============================================================================
-- 视觉方向：「第几轮生成」与「可复现的差异种子」
-- ----------------------------------------------------------------------------
-- 为什么加这两列（v1 人工测试反馈裁定，2026-10-06）：
--   ⑨ 方向卡要加「第几轮」标记 —— 表里此前**没有任何轮次概念**，
--      "重新生成方向"只是又插 3 行，页面上于是出现两组同名的 A/B/C，
--      只能靠名字与状态猜哪组是哪次生成的。
--   ⑤ 「重新生成可以复现，但每次生成都要有差异化」——要同时做到这两件事，
--      只有一条路：**把这一次生成的"差异来源"存下来**。
--      `variant_seed` 就是那个来源（同种子必然产出同一份内容；换种子必然换一份内容）。
--
-- batch_no：同一个任务内，一次「生成方向」插入的 3 行共用同一个 batch_no，从 1 开始递增。
-- variant_seed：这一次生成用的差异种子（可空＝历史数据，按 0 处理）。
--
-- 存量数据的回填：老数据没有这两个字段，只能按"同一秒插入的一组 = 同一轮"来切
-- （实测两组分别是 2026-09-28 14:43:35 与 2026-10-05 13:24:49，各 3 行、时刻完全相同）。
-- **这是回填用的推断，不是新数据的写入口径**：新数据由服务层显式写入（见 CreativeDirectionServiceImpl）。
--
-- 幂等：ALTER 前先查列是否存在（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）。
-- 回滚见文件末尾。
-- ============================================================================

-- 1) 加列（只在列不存在时执行）
SET @has_batch := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_visual_direction'
                      AND COLUMN_NAME = 'batch_no');
SET @sql := IF(@has_batch = 0,
  'ALTER TABLE dp_visual_direction ADD COLUMN batch_no INT NOT NULL DEFAULT 1 COMMENT ''第几轮生成（同一任务内从 1 递增；一次生成的方向共用）''',
  'SELECT ''batch_no 已存在，跳过'' AS note');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_seed := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_visual_direction'
                     AND COLUMN_NAME = 'variant_seed');
SET @sql := IF(@has_seed = 0,
  'ALTER TABLE dp_visual_direction ADD COLUMN variant_seed BIGINT NULL COMMENT ''本次生成的差异种子（可复现：同种子同输出；空＝历史数据按 0 处理）''',
  'SELECT ''variant_seed 已存在，跳过'' AS note');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 存量回填：按 (task_id, create_time) 分组，组内按时间升序给 1..N
UPDATE dp_visual_direction d
  JOIN (
    SELECT task_id, create_time, ROW_NUMBER() OVER (PARTITION BY task_id ORDER BY create_time) AS rn
      FROM (SELECT DISTINCT task_id, create_time FROM dp_visual_direction WHERE del_flag = '0') t
  ) g ON g.task_id = d.task_id AND g.create_time = d.create_time
   SET d.batch_no = g.rn
 WHERE d.del_flag = '0';

-- 2b) variant_seed 保持 NULL：它要等 ⑤ 的差异化逻辑上线后才由服务层写入。
--     现在填 0 会让人以为"已经有种子了"，而其实没有任何代码消费它——不假装。

-- 3) 核对：每个任务的轮次与行数（期望：本机那个任务 2 轮 × 3 行）
SELECT '=== 回填结果（按任务 × 轮次）===' AS s;
SELECT task_id, batch_no, COUNT(*) AS rows_in_batch, MIN(create_time) AS created_at,
       GROUP_CONCAT(direction_code ORDER BY direction_code) AS codes
  FROM dp_visual_direction WHERE del_flag = '0'
 GROUP BY task_id, batch_no ORDER BY task_id, batch_no;

SELECT '=== 列是否都在（期望 2 行）===' AS s;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_visual_direction'
   AND COLUMN_NAME IN ('batch_no', 'variant_seed');

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------
-- ALTER TABLE dp_visual_direction DROP COLUMN batch_no;
-- ALTER TABLE dp_visual_direction DROP COLUMN variant_seed;
