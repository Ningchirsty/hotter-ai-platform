-- ============================================================================
-- 视觉基因：「这一版提示词用哪套措辞」的种子（v1 人工测试反馈裁定 ⑤）
-- ----------------------------------------------------------------------------
-- 裁定原文：「可以复现，但是每次生成都要有差异化」，对到基因页那条原文就是
-- 「不满足可『重新生成』，点了要出现新提示词」。
--
-- 为什么需要存一颗种子，而不是每次现算：
--   提示词是**基因的派生结果**（`DnaPromptBuilder`）。改造前它是纯函数，于是
--   『重新生成』若补出来的字段一样（模板回落、或模型返回同样的值），提示词就逐字一样——
--   人看到的就是"点了没变化"。而直接塞随机数又会毁掉"可复现"（出问题时要能回到当时那一版）。
--   折中与视觉方向一致：**把差异来源存下来**。种子决定用哪一套措辞（块顺序 × 引导语，共 6 套），
--   同一颗种子逐字相同（可复现、可回归测试），『重新生成』得到的新版本必然换一套（下标每次移一位）。
--
-- **种子只换措辞、绝不换事实**：色号、光线、留白、占比、场景、各档位的值一个字都不改，
-- 变的只是"先说什么后先说"与引导语（整体风格↔风格基调、光线↔布光…）。有单测逐条钉住。
--
-- 存量数据：**保持 NULL**，表示"按改造前的原文案派生"——历史版本与已下发过的出图记录因此逐字不变
-- （反过来说：如果给历史行硬填一个种子，页面就会用另一套措辞显示"当时的提示词"，那是假的）。
--
-- 幂等：ALTER 前先查列是否存在。回滚见文件末尾。
-- ============================================================================

SET @has_seed := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_visual_dna'
                     AND COLUMN_NAME = 'prompt_seed');
SET @sql := IF(@has_seed = 0,
  'ALTER TABLE dp_visual_dna ADD COLUMN prompt_seed BIGINT NULL COMMENT ''这一版提示词的措辞种子（NULL＝改造前的原文案；只换说法不换值）''',
  'SELECT ''prompt_seed 已存在，跳过'' AS note');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT '=== 列是否建好（期望 1 行）===' AS s;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_visual_dna'
   AND COLUMN_NAME = 'prompt_seed';

SELECT '=== 存量基因的种子（期望全部为 NULL）===' AS s;
SELECT id, `version`, status, prompt_seed FROM dp_visual_dna WHERE del_flag = '0' ORDER BY task_id, `version`;

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------
-- ALTER TABLE dp_visual_dna DROP COLUMN prompt_seed;
