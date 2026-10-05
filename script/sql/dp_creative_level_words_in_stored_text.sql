-- ============================================================================
-- 存量数据：把已落库文本里的档位枚举改成中文（v1 人工测试反馈「去英文/去编号」的存量部分）
-- ----------------------------------------------------------------------------
-- 背景：`CreativeDraftFactory` / `CreativeStoryboardServiceImpl` 现在**新生成**的文本
-- 已经写中文档位（LOW/MEDIUM/HIGH → 低/中/高，见 `CreativeLevelText`），
-- 但**已经落库**的行还是老文案：
--   · `dp_visual_direction.strategy_json` 的 `composition`：
--       「产品居中、正投影，四周留白均等（留白 HIGH）」
--     `dnaBasis`：「主色 未测、背景 #F5F5F3、饱和 MEDIUM、对比 MEDIUM、留白 HIGH、产品占比 45%~65%」
--   · `dp_storyboard_screen.spec_json`：
--       `"whitespace":"HIGH"`（结构化字段，卡片上直接显示成「留白HIGH」）
--       以及从方向复制过来的 `composition` 散文
-- 只改代码不迁移数据的话，**已有项目的卡片上仍然印着 MEDIUM/HIGH**——
-- 复验的人会以为这次改动没生效。
--
-- 口径：这些是**可再生的展示文案**（不是审计日志），normalization 不改变含义，
-- 只把"给代码看的词"换成"给人读的词"，并且把 饱和/对比 的旧措辞统一成
-- 饱和度/对比度（与新增文案同一口径）。`dp_stage_event` 之类审计表**不动**。
--
-- 幂等：替换的都是"旧写法"，跑第二遍不会再命中。
-- 回滚：文件末尾给出反向替换（一般不需要——这是措辞归一，不丢信息）。
-- ============================================================================

-- 1) 执行前：还有多少行带着旧写法
SELECT '=== 1) 执行前 ===' AS s;
SELECT 'direction.strategy_json' AS t, COUNT(*) AS n FROM dp_visual_direction
 WHERE REPLACE(strategy_json, ' ', '') REGEXP '留白(HIGH|MEDIUM|LOW)|饱和(HIGH|MEDIUM|LOW)|对比(HIGH|MEDIUM|LOW)'
UNION ALL
SELECT 'screen.spec_json', COUNT(*) FROM dp_storyboard_screen
 WHERE REPLACE(spec_json, ' ', '') REGEXP '留白(HIGH|MEDIUM|LOW)|饱和(HIGH|MEDIUM|LOW)|对比(HIGH|MEDIUM|LOW)'
UNION ALL
SELECT 'screen.whitespace', COUNT(*) FROM dp_storyboard_screen
 WHERE JSON_VALID(spec_json)
   AND JSON_UNQUOTE(JSON_EXTRACT(spec_json, '$.whitespace')) IN ('HIGH', 'MEDIUM', 'LOW');

-- 2) 方向的策略明细（composition 与 dnaBasis 两处带档位）
UPDATE dp_visual_direction
   SET strategy_json = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
         strategy_json,
         '留白 HIGH', '留白 高'), '留白 MEDIUM', '留白 中'), '留白 LOW', '留白 低'),
         '饱和 HIGH', '饱和度 高'), '饱和 MEDIUM', '饱和度 中'), '饱和 LOW', '饱和度 低'),
         '对比 HIGH', '对比度 高'), '对比 MEDIUM', '对比度 中'), '对比 LOW', '对比度 低'),
       update_time = sysdate()
 WHERE strategy_json IS NOT NULL
   AND (strategy_json LIKE '%留白 HIGH%' OR strategy_json LIKE '%留白 MEDIUM%' OR strategy_json LIKE '%留白 LOW%'
     OR strategy_json LIKE '%饱和 HIGH%' OR strategy_json LIKE '%饱和 MEDIUM%' OR strategy_json LIKE '%饱和 LOW%'
     OR strategy_json LIKE '%对比 HIGH%' OR strategy_json LIKE '%对比 MEDIUM%' OR strategy_json LIKE '%对比 LOW%');

-- 3) 分镜屏规格：结构化字段用 JSON_SET（与 JSON 的书写格式无关）
UPDATE dp_storyboard_screen
   SET spec_json = JSON_SET(spec_json, '$.whitespace', '高'), update_time = sysdate()
 WHERE JSON_VALID(spec_json) AND JSON_UNQUOTE(JSON_EXTRACT(spec_json, '$.whitespace')) = 'HIGH';
UPDATE dp_storyboard_screen
   SET spec_json = JSON_SET(spec_json, '$.whitespace', '中'), update_time = sysdate()
 WHERE JSON_VALID(spec_json) AND JSON_UNQUOTE(JSON_EXTRACT(spec_json, '$.whitespace')) = 'MEDIUM';
UPDATE dp_storyboard_screen
   SET spec_json = JSON_SET(spec_json, '$.whitespace', '低'), update_time = sysdate()
 WHERE JSON_VALID(spec_json) AND JSON_UNQUOTE(JSON_EXTRACT(spec_json, '$.whitespace')) = 'LOW';

-- 4) 分镜屏规格里从方向复制过来的散文（composition）+ 没有方向时的光线兜底句
UPDATE dp_storyboard_screen
   SET spec_json = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
         spec_json,
         '留白 HIGH', '留白 高'), '留白 MEDIUM', '留白 中'), '留白 LOW', '留白 低'),
         '饱和 HIGH', '饱和度 高'), '饱和 MEDIUM', '饱和度 中'), '饱和 LOW', '饱和度 低'),
         '对比 HIGH', '对比度 高'), '对比 MEDIUM', '对比度 中'), '对比 LOW', '对比度 低'),
         '光线：SOFT', '光线：柔光'), '光线：HARD', '光线：硬光'),
         '光线：STUDIO', '光线：影棚均匀光'), '光线：NATURAL', '光线：自然光'),
       update_time = sysdate()
 WHERE spec_json IS NOT NULL
   AND (spec_json LIKE '%留白 HIGH%' OR spec_json LIKE '%留白 MEDIUM%' OR spec_json LIKE '%留白 LOW%'
     OR spec_json LIKE '%饱和 HIGH%' OR spec_json LIKE '%饱和 MEDIUM%' OR spec_json LIKE '%饱和 LOW%'
     OR spec_json LIKE '%对比 HIGH%' OR spec_json LIKE '%对比 MEDIUM%' OR spec_json LIKE '%对比 LOW%'
     OR spec_json LIKE '%光线：SOFT%' OR spec_json LIKE '%光线：HARD%'
     OR spec_json LIKE '%光线：STUDIO%' OR spec_json LIKE '%光线：NATURAL%');

-- 5) 基因证据链里「默认值」那条摘要（`dp_visual_dna.dna_json`）
--    老文案是 DnaSeedBuilder 写死的 `MEDIUM/MEDIUM/HIGH/纯色底/SOFT+FRONT/45~65%`，
--    基因页的「证据链」表会把 value 原样显示出来（v1 反馈：那一列印着枚举）。
--    这个串带斜杠、很独特，直接换成中文档位；比例区间保持原样。
UPDATE dp_visual_dna
   SET dna_json = REPLACE(dna_json,
         'MEDIUM/MEDIUM/HIGH/纯色底/SOFT+FRONT/',
         '中/中/高/纯色底/柔光+正面光/'),
       update_time = sysdate()
 WHERE dna_json IS NOT NULL
   AND dna_json LIKE '%MEDIUM/MEDIUM/HIGH/纯色底/SOFT+FRONT/%';

-- 6) 执行后核对（期望：两条计数都是 0）
SELECT '=== 5) 执行后（期望全 0）===' AS s;
SELECT 'direction.strategy_json' AS t, COUNT(*) AS n FROM dp_visual_direction
 WHERE REPLACE(strategy_json, ' ', '') REGEXP '留白(HIGH|MEDIUM|LOW)|饱和(HIGH|MEDIUM|LOW)|对比(HIGH|MEDIUM|LOW)'
UNION ALL
SELECT 'screen.spec_json', COUNT(*) FROM dp_storyboard_screen
 WHERE REPLACE(spec_json, ' ', '') REGEXP '留白(HIGH|MEDIUM|LOW)|饱和(HIGH|MEDIUM|LOW)|对比(HIGH|MEDIUM|LOW)'
UNION ALL
SELECT 'screen.whitespace', COUNT(*) FROM dp_storyboard_screen
 WHERE JSON_VALID(spec_json)
   AND JSON_UNQUOTE(JSON_EXTRACT(spec_json, '$.whitespace')) IN ('HIGH', 'MEDIUM', 'LOW')
UNION ALL
SELECT 'dna.evidence_default_summary', COUNT(*) FROM dp_visual_dna
 WHERE dna_json LIKE '%MEDIUM/MEDIUM/HIGH/纯色底/SOFT+FRONT/%';

SELECT '=== 6.1 迁移后 dna_json 必须仍是合法 JSON（期望空）===' AS s;
SELECT id FROM dp_visual_dna WHERE dna_json IS NOT NULL AND NOT JSON_VALID(dna_json);

-- 7) 抽样看一眼改完的样子
SELECT '=== 7) 抽样 ===' AS s;
SELECT id, JSON_UNQUOTE(JSON_EXTRACT(strategy_json, '$.composition')) AS composition,
       JSON_UNQUOTE(JSON_EXTRACT(strategy_json, '$.dnaBasis')) AS dna_basis
  FROM dp_visual_direction WHERE task_id = 2104582766641799169 AND status = 'SELECTED';
SELECT screen_no, JSON_UNQUOTE(JSON_EXTRACT(spec_json, '$.whitespace')) AS whitespace,
       JSON_UNQUOTE(JSON_EXTRACT(spec_json, '$.composition')) AS composition
  FROM dp_storyboard_screen WHERE task_id = 2104582766641799169 ORDER BY sort_no LIMIT 3;
SELECT id, JSON_UNQUOTE(JSON_EXTRACT(dna_json, '$.evidence[7].value')) AS default_summary
  FROM dp_visual_dna WHERE task_id = 2104582766641799169 ORDER BY id DESC LIMIT 1;

-- ---------------------------------------------------------------------------
-- 回滚（中文 → 旧枚举；一般不需要，这里只是把口径写全）
-- ---------------------------------------------------------------------------
-- UPDATE dp_visual_direction SET strategy_json = REPLACE(REPLACE(REPLACE(
--        strategy_json, '留白 高', '留白 HIGH'), '留白 中', '留白 MEDIUM'), '留白 低', '留白 LOW')
--  WHERE strategy_json LIKE '%留白 高%' OR strategy_json LIKE '%留白 中%' OR strategy_json LIKE '%留白 低%';
-- （饱和度/对比度同理；分镜的 whitespace 用 JSON_SET 写回枚举）
