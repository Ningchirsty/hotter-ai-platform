-- ------------------------------------------------------------------
-- V0.2 / R29：屏级质检规则（模块库 qaRules 真正被消费）+ 主图规则体检留痕
--
-- 背景（文档 §20 的 qaRules 从 R21 起就只是一个字段）：
--   `dp_module_definition.qa_rules_json` 建表时就存在，模块库界面也能编辑，
--   但**从来没有被任何代码读过**——存着而已。本轮把它接上：
--     1) 分镜生成时，把模块库的 qaRules **烙进屏的 spec_json**（与 visualRules 同一时刻、
--        同一语义：屏上冻着"这一屏当时按什么规则产出/验收"，改模块库不回溯）；
--     2) 选定候选、交付图规格化之后，按这套规则对**交付图本身**做一次确定性体检
--        （正方形/最小边/白底度/主体占比/透明通道/边缘裁切），结论落 dp_generation.qa_findings_json；
--     3) 体检**只报告不判决**：HARD 项不自动筛除候选（是否让平台硬性项自动筛除是需要你拍板的事，
--        与 §25 第 3 步同类），页面如实显示。
--
-- 本轮种子只给 MAIN_IMAGE 的 5 个模块配规则（主图有客观的平台硬性要求：1:1、≥800、白底、无透明）。
-- 详情页（ECOM_DETAIL）**不配**：它的交付物是 750×AUTO 的长图，方图/最小边那类规则不适用；
-- 没配规则的屏，体检结果如实写"本屏未配置质检规则，未做规则体检"，不会伪造通过。
--
-- 幂等：加列用 information_schema 守卫；种子用"当前为空才写"的守卫（重放不会覆盖人工改动）。
-- ------------------------------------------------------------------

-- 1) 质检结论留痕列（主图规则体检的 findings）
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE dp_generation ADD COLUMN qa_findings_json TEXT NULL COMMENT ''屏级规则体检结论（JSON：metrics + findings；只报告不判决）''',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_generation' AND COLUMN_NAME = 'qa_findings_json');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) MAIN_IMAGE 模块库的质检规则（只在为空时写入）
--    规则 schema 见 CreativeQaRules：levels 决定每一项是 HARD（平台硬性、报告为主）还是 SOFT（参考项）。
UPDATE dp_module_definition SET qa_rules_json = '{
  "schema": "screen-qa/1",
  "square": true,
  "minSide": 800,
  "alphaForbidden": true,
  "whiteBackground": {"enabled": true, "minEdgeWhiteness": 0.90},
  "subjectRatio": {"enabled": true, "min": 0.10},
  "edgeBleed": {"enabled": true, "maxRatio": 0.05},
  "levels": {"CANVAS_SQUARE": "HARD", "MIN_SIDE": "HARD", "NO_ALPHA": "HARD",
             "WHITE_BACKGROUND": "SOFT", "SUBJECT_RATIO": "SOFT", "EDGE_BLEED": "SOFT"}
}'
 WHERE delivery_type = 'MAIN_IMAGE' AND module_code = 'MAIN_WHITE_BG'
   AND (qa_rules_json IS NULL OR qa_rules_json = '');

UPDATE dp_module_definition SET qa_rules_json = '{
  "schema": "screen-qa/1",
  "square": true,
  "minSide": 800,
  "alphaForbidden": true,
  "whiteBackground": {"enabled": false},
  "subjectRatio": {"enabled": true, "min": 0.08},
  "edgeBleed": {"enabled": true, "maxRatio": 0.05},
  "levels": {"CANVAS_SQUARE": "HARD", "MIN_SIDE": "HARD", "NO_ALPHA": "HARD",
             "SUBJECT_RATIO": "SOFT", "EDGE_BLEED": "SOFT"}
}'
 WHERE delivery_type = 'MAIN_IMAGE' AND module_code = 'MAIN_SELLING'
   AND (qa_rules_json IS NULL OR qa_rules_json = '');

UPDATE dp_module_definition SET qa_rules_json = '{
  "schema": "screen-qa/1",
  "square": true,
  "minSide": 800,
  "alphaForbidden": true,
  "whiteBackground": {"enabled": false},
  "subjectRatio": {"enabled": true, "min": 0.05},
  "edgeBleed": {"enabled": true, "maxRatio": 0.05},
  "levels": {"CANVAS_SQUARE": "HARD", "MIN_SIDE": "HARD", "NO_ALPHA": "HARD",
             "SUBJECT_RATIO": "SOFT", "EDGE_BLEED": "SOFT"}
}'
 WHERE delivery_type = 'MAIN_IMAGE' AND module_code = 'MAIN_SCENE'
   AND (qa_rules_json IS NULL OR qa_rules_json = '');

UPDATE dp_module_definition SET qa_rules_json = '{
  "schema": "screen-qa/1",
  "square": true,
  "minSide": 800,
  "alphaForbidden": true,
  "whiteBackground": {"enabled": false},
  "subjectRatio": {"enabled": true, "min": 0.10},
  "edgeBleed": {"enabled": true, "maxRatio": 0.05},
  "levels": {"CANVAS_SQUARE": "HARD", "MIN_SIDE": "HARD", "NO_ALPHA": "HARD",
             "SUBJECT_RATIO": "SOFT", "EDGE_BLEED": "SOFT"}
}'
 WHERE delivery_type = 'MAIN_IMAGE' AND module_code = 'MAIN_DETAIL'
   AND (qa_rules_json IS NULL OR qa_rules_json = '');

UPDATE dp_module_definition SET qa_rules_json = '{
  "schema": "screen-qa/1",
  "square": true,
  "minSide": 800,
  "alphaForbidden": true,
  "whiteBackground": {"enabled": false},
  "subjectRatio": {"enabled": true, "min": 0.05},
  "edgeBleed": {"enabled": true, "maxRatio": 0.05},
  "levels": {"CANVAS_SQUARE": "HARD", "MIN_SIDE": "HARD", "NO_ALPHA": "HARD",
             "SUBJECT_RATIO": "SOFT", "EDGE_BLEED": "SOFT"}
}'
 WHERE delivery_type = 'MAIN_IMAGE' AND module_code = 'MAIN_SIZE'
   AND (qa_rules_json IS NULL OR qa_rules_json = '');

-- 3) 修正已经落库的贴边率阈值（幂等：只在值还是旧的 0.01 时改成 0.05）
--
-- 为什么改：贴边率的**度量口径**在真机第一次验收后修正了——
--   旧："主体像素里落在 2% 带内的比例"（量的是"边缘有没有非白像素"，柔和投影/渐变也会触发）；
--   新："最外 0.5% 环上非背景像素占该环的比例"（量的是"画布边界被主体占了多少"，被裁才显著升高）。
-- 真机证据：一张 800×800 的合格白底主图，旧口径量出 3.5%（阈值 1% → 判不过），
-- 那明显是度量本身的问题，不是图的问题。口径改了，阈值也要跟着改成 5%（仍是 SOFT 参考项）。
-- 另外：贴边判定改用**更严的距离阈值**（80，而不是主体占比的 30）——柔和投影与渐变暗角
-- 与背景的距离通常在 30~60，用 30 会把"边缘有点灰"当成"产品被切了"。
UPDATE dp_module_definition
   SET qa_rules_json = JSON_SET(qa_rules_json, '$.edgeBleed.maxRatio', 0.05)
 WHERE delivery_type = 'MAIN_IMAGE'
   AND JSON_VALID(qa_rules_json)
   AND JSON_EXTRACT(qa_rules_json, '$.edgeBleed.maxRatio') = 0.01;

-- 4) 按真机实测重新标定「主体占比」下限（幂等：只改老种子值，人工改过的不动）
--
-- 为什么标定：主体占比是**以四角均色为背景基准的非背景像素占比**（面积口径）。
-- 真机实测：800×800 的合格白底主图量到 0.156~0.161。第一版种子按"基因里的
-- productRatio 60%~80%"直接抄成 0.50，但那是**线性占比**（产品占画面宽度的比例），
-- 与"像素面积占比"不是一个口径：线性 70% 对应面积约 49%，再叠加"产品自身浅色像素
-- 与白底接近会被少算"，0.50 就成了"每张真图都不过"的坏规则。
-- 所以下限整体下调到"只兜住'画面几乎是空的'这一种情况"（0.05~0.10），
-- 实测值仍在 detail 里如实给出，供人对照基因目标；把它做成硬判据需要先积累标注数据（见待办）。
UPDATE dp_module_definition SET qa_rules_json = JSON_SET(qa_rules_json, '$.subjectRatio.min', 0.10)
 WHERE delivery_type='MAIN_IMAGE' AND JSON_VALID(qa_rules_json)
   AND JSON_EXTRACT(qa_rules_json, '$.subjectRatio.min') IN (0.50, 0.35);
UPDATE dp_module_definition SET qa_rules_json = JSON_SET(qa_rules_json, '$.subjectRatio.min', 0.08)
 WHERE delivery_type='MAIN_IMAGE' AND JSON_VALID(qa_rules_json)
   AND JSON_EXTRACT(qa_rules_json, '$.subjectRatio.min') = 0.25;
UPDATE dp_module_definition SET qa_rules_json = JSON_SET(qa_rules_json, '$.subjectRatio.min', 0.05)
 WHERE delivery_type='MAIN_IMAGE' AND JSON_VALID(qa_rules_json)
   AND JSON_EXTRACT(qa_rules_json, '$.subjectRatio.min') = 0.15;

SELECT 'DP_CREATIVE_R29_SCREEN_QA_DONE' AS marker;

-- 执行后核对
SELECT delivery_type, module_code, LEFT(COALESCE(qa_rules_json, '(空)'), 40) AS qa_rules
  FROM dp_module_definition WHERE del_flag = '0' ORDER BY delivery_type, default_sort_no;

SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_generation' AND COLUMN_NAME = 'qa_findings_json';
