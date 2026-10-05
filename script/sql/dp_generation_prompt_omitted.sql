-- ============================================================================
-- 出图候选：把「未进提示词的条目」落成可查字段（内测 S13）
-- ----------------------------------------------------------------------------
-- 背景：`DnaPromptBuilder` 一直在如实计算 `omitted`（必显信息/主推卖点/禁用词因长度上限
-- 未放入提示词，以及屏文案被截断），但这份留痕**只写进了阶段事件的 detail JSON**。
-- 于是用户看到的是："我明明填了必显信息、出图却没体现"，而唯一能解释这件事的记录
-- 埋在原始 JSON 里，得去翻事件才知道。
--
-- 本脚本给 `dp_generation` 加一列，让它在候选列表上直接可见（"失败/提示"那一列）。
--
-- 存的是**可读文案**（分号连接）而不是 JSON：它的用途是给人看的一句话提示，
-- 不是给程序查询的结构化数据；真要用 JSON 时旁边已经有 `qa_findings_json` 的先例，
-- 但那一条确实是要被程序读的。
--
-- ⚠️ 上线顺序：本脚本必须先于（或同时于）代码上线——代码会写这一列。
-- 幂等：先判断列是否存在。
-- ============================================================================

-- 1) 加列（可重复执行）
SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_generation' AND COLUMN_NAME = 'prompt_omitted'
);
SET @sql := IF(@exists = 0,
    'ALTER TABLE dp_generation ADD COLUMN prompt_omitted varchar(2000) NULL COMMENT ''未进提示词的条目与原因（内测S13；给人看的可读提示）'' AFTER negative_prompt',
    'SELECT ''prompt_omitted already exists'' AS note');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 核对
SELECT '=== dp_generation.prompt_omitted ===' AS s;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_generation' AND COLUMN_NAME = 'prompt_omitted';

-- 3) 现状：已有候选里有多少条能解释"填了没进提示词"（加列前必然全为 NULL）
SELECT '=== 现在有提示词留痕的候选 ===' AS s;
SELECT COUNT(*) AS total,
       SUM(prompt_omitted IS NOT NULL AND prompt_omitted <> '') AS with_note
  FROM dp_generation;

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------
-- ALTER TABLE dp_generation DROP COLUMN prompt_omitted;
