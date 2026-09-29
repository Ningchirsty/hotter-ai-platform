-- ------------------------------------------------------------------
-- V0.2 / R21：第二个交付类型 MAIN_IMAGE（商品主图，文档 §9.2）最小种子
--
-- 为什么用 MAIN_IMAGE 而不是 XHS_MULTI_IMAGE：
--   内容模块的 `ContentDeliverableTypeEnum` 里**已经有** MAIN_IMAGE（"主图/SKU图"），
--   而 XHS_MULTI_IMAGE 不在枚举里 —— 用后者就得改内容模块的枚举（跨模块、且会让"交付类型字典"
--   出现第三个词表）。文档 §9.2 本来就把"商品主图"列为 P1 场景，所以用它既最小、又不越界。
--
-- 这一套种子用来做"按交付类型匹配分镜"的真机验收：
--   * 流程更短（9 步：没有 LAYOUT 长图排版）→ 指引线会显示 9 步，与详情页的 10 步可见不同；
--   * 屏集合不同（5 个模块各 1 屏 = 5 屏，类型是 MAIN_*）→ 与详情页的 7 屏可见不同；
--   * 输出规格 1:1 800×800（主图不是长图）；
--   * 闸门档案只留 5 项（主图不需要"分镜锁定"以外的品牌 Brief 项？——这里保留 DNA/参考图/方向/分镜/调性）。
--
-- 幂等：全部带 NOT EXISTS 守卫。
-- ------------------------------------------------------------------

-- 1) 交付类型
INSERT INTO dp_delivery_type
(id, category_code, delivery_type, alias_codes, delivery_name, media_type, render_mode,
 default_profile_id, enabled, sort_no, remark, create_dept, create_by, create_time)
SELECT 1769100000000000201, 'MAIN', 'MAIN_IMAGE', 'ECOM_MAIN_IMAGE', '商品主图', 'IMAGE', 'MULTI_IMAGE',
       1769100000000000202, '0', 20, 'R21 种子：第二个交付类型（用于验证"按交付类型匹配分镜"）',
       1761000000000000103, 1761100000000000001, NOW()
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT delivery_type FROM dp_delivery_type) t
                    WHERE t.delivery_type = 'MAIN_IMAGE');

-- 2) 场景档案
INSERT INTO dp_scenario_profile
(id, profile_code, profile_name, delivery_type, version, status,
 input_schema_json, workflow_schema_json, output_schema_json, workspace_schema_json, remark,
 create_dept, create_by, create_time)
SELECT 1769100000000000202, 'PROFILE_MAIN_IMAGE_V1', '商品主图生产流程（V1）', 'MAIN_IMAGE', '1.0.0', 'PUBLISHED',
       '{"fields":[{"code":"product_name","required":true},{"code":"product_images","required":true}]}',
       '{"steps":["INPUT","FACT","DNA","DIRECTION","STORYBOARD","GATE","GENERATION","QA","FINAL"]}',
       '{"artifacts":[{"code":"MAIN_IMAGE","format":"PNG","from":"dp_generation"}]}',
       '{"schemaCode":"WS_MAIN_IMAGE"}',
       'R21 种子：主图流程比详情页短一步（无长图排版）',
       1761000000000000103, 1761100000000000001, NOW()
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT profile_code FROM dp_scenario_profile) t
                    WHERE t.profile_code = 'PROFILE_MAIN_IMAGE_V1');

-- 3) 步骤（9 步；stage_codes 仍映射到既有阶段机，不新增阶段）
INSERT INTO dp_scenario_step
(id, profile_id, step_code, step_name, step_type, stage_codes, sort_no, required, gate_type,
 capability_code, workspace_component, entry_condition_json, completion_rule_json, config_json,
 create_dept, create_by, create_time)
SELECT * FROM (
  SELECT 1769100000000000210 AS id, 1769100000000000202 AS profile_id, 'INPUT' AS step_code,
         '产品资料与参考图' AS step_name, 'INPUT' AS step_type, 'MATERIAL_READY' AS stage_codes,
         10 AS sort_no, '1' AS required, NULL AS gate_type, NULL AS capability_code,
         'ProjectInputPanel' AS workspace_component, '{"requireProject":true}' AS entry_condition_json,
         '{"minReferenceImages":1}' AS completion_rule_json, '{"impl":"existing"}' AS config_json,
         1761000000000000103 AS create_dept, 1761100000000000001 AS create_by, NOW() AS create_time
  UNION ALL SELECT 1769100000000000211, 1769100000000000202, 'FACT', '事实确认', 'FACT', 'MATERIAL_READY',
         20, '1', 'FACT_CONFIRMED', 'document_parse', 'FactPanel', '{"requireInput":true}',
         '{"confirmedFields":["product_name"]}', '{"impl":"content-domain"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000212, 1769100000000000202, 'DNA', '视觉基因', 'DNA',
         'DNA_GENERATING,DNA_REVIEW,DNA_LOCKED', 30, '1', 'DNA_LOCKED', 'visual_dna_extract', 'VisualDnaPanel',
         '{"requireFact":true}', '{"status":"LOCKED"}', '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000213, 1769100000000000202, 'DIRECTION', '视觉方向', 'DIRECTION',
         'DIRECTION_GENERATING,DIRECTION_REVIEW,DIRECTION_LOCKED', 40, '1', 'DIRECTION_SELECTED',
         'creative_direction_draft', 'DirectionBoard', '{"requireDna":true}', '{"selectedCount":1}',
         '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000214, 1769100000000000202, 'STORYBOARD', '主图分镜', 'STORYBOARD',
         'STORYBOARD_GENERATING,STORYBOARD_REVIEW,STORYBOARD_LOCKED', 50, '1', 'STORYBOARD_LOCKED',
         'creative_storyboard_draft', 'StoryboardBoard', '{"requireDirection":true}', '{"status":"LOCKED"}',
         '{"impl":"existing","skeletonFrom":"dp_project_module"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000215, 1769100000000000202, 'GATE', '视觉门', 'GATE', 'VISUAL_GATE,VISUAL_LOCKED',
         60, '1', 'VISUAL_GATE_PASS', NULL, 'GatePanel', '{"requireStoryboard":true}', '{"action":"VISUAL_GATE_PASS"}',
         '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000216, 1769100000000000202, 'GENERATION', '出图', 'GENERATION', 'PRODUCING',
         70, '1', NULL, NULL, 'GenerationBoard', '{"requireGate":true}', '{"candidatePerScreen":1}',
         '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000217, 1769100000000000202, 'QA', '质检', 'QA', 'QA_PROCESSING',
         80, '0', NULL, 'deliverable_consistency', 'QaPanel', '{"requireGeneration":true}',
         '{"hardFailBlocking":true}', '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000219, 1769100000000000202, 'FINAL', '终审交付', 'FINAL_REVIEW',
         'FINAL_REVIEW,COMPLETED', 90, '1', 'FINAL_APPROVED', NULL, 'FinalReviewPanel', '{"requireGeneration":true}',
         '{"status":"COMPLETED"}', '{"impl":"existing","note":"主图直接交付成品图，无长图排版"}',
         1761000000000000103, 1761100000000000001, NOW()
) seed
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT profile_id FROM dp_scenario_step) t
                    WHERE t.profile_id = 1769100000000000202);

-- 4) 输出规格（主图 1:1；800 为默认）
-- 注意列名以**实际表结构**为准（dp_output_spec 没有 enabled 列，有 file_format_json/source_scale）：
-- 第一版按记忆写了 enabled → MySQL 报 Unknown column 并**在这一句就停住**，
-- 后面的闸门/工作台/模块种子全都没执行（而 zxlib.sql 会吞掉 stderr，看起来像"执行了但没输出"）。
INSERT INTO dp_output_spec
(id, spec_code, delivery_type, channel, width, height, height_mode, ratio, unit, dpi, color_mode,
 file_format_json, source_scale, is_default, sort_no, remark, create_dept, create_by, create_time)
SELECT * FROM (
  SELECT 1769100000000000221 AS id, 'MAIN_IMAGE_800' AS spec_code, 'MAIN_IMAGE' AS delivery_type,
         'TAOBAO' AS channel, 800 AS width, 800 AS height, 'FIXED' AS height_mode, '1:1' AS ratio,
         'px' AS unit, NULL AS dpi, 'RGB' AS color_mode,
         '{"format":"PNG","mime":"image/png","maxSizeMB":10,"alpha":false}' AS file_format_json,
         1 AS source_scale, '1' AS is_default, 10 AS sort_no,
         'R21 种子：主图默认 800×800' AS remark,
         1761000000000000103 AS create_dept, 1761100000000000001 AS create_by, NOW() AS create_time
  UNION ALL SELECT 1769100000000000222, 'MAIN_IMAGE_1200', 'MAIN_IMAGE', 'TAOBAO', 1200, 1200, 'FIXED', '1:1',
         'px', NULL, 'RGB', '{"format":"PNG","mime":"image/png","maxSizeMB":10,"alpha":false}', 1, '0', 20,
         'R21 种子：高清主图 1200×1200',
         1761000000000000103, 1761100000000000001, NOW()
) seed
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT delivery_type, spec_code FROM dp_output_spec) t
                    WHERE t.delivery_type = 'MAIN_IMAGE');

-- 5) 闸门档案（主图只留 5 项：参考图/DNA/方向/分镜/调性）
-- 列名同样以实际表结构为准：dp_gate_profile **没有 profile_name**（只有 profile_code/delivery_type/version/status/remark）。
-- 这一轮第三次踩"猜列名"：mysql 在出错那一句就停住，后面的种子全没执行——而 zxlib.sql 吞 stderr，
-- 现象是"执行了但没输出"。教训：写种子的第一步永远是 SHOW COLUMNS，不是凭记忆。
INSERT INTO dp_gate_profile
(id, profile_code, delivery_type, version, status, remark, create_dept, create_by, create_time)
SELECT 1769100000000000230, 'GATE_MAIN_IMAGE_V1', 'MAIN_IMAGE', '1.0.0', 'PUBLISHED',
       'R21 种子：主图闸门项比详情页少（不做品牌 Brief 与禁用词两项）',
       1761000000000000103, 1761100000000000001, NOW()
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT profile_code FROM dp_gate_profile) t
                    WHERE t.profile_code = 'GATE_MAIN_IMAGE_V1');

INSERT INTO dp_gate_item
(id, profile_id, item_code, item_label, level, sort_no, remark, create_dept, create_by, create_time)
SELECT * FROM (
  SELECT 1769100000000000231 AS id, 1769100000000000230 AS profile_id, 'REFERENCE_IMAGE' AS item_code,
         '产品参考图已上传' AS item_label, 'BLOCK' AS level, 10 AS sort_no, NULL AS remark,
         1761000000000000103 AS create_dept, 1761100000000000001 AS create_by, NOW() AS create_time
  UNION ALL SELECT 1769100000000000232, 1769100000000000230, 'DNA_LOCKED', '视觉基因已锁定', 'BLOCK', 20, NULL,
         1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000233, 1769100000000000230, 'DIRECTION_SELECTED', '视觉方向已选定', 'CONDITION', 30, NULL,
         1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000234, 1769100000000000230, 'STORYBOARD_LOCKED', '分镜已锁定', 'CONDITION', 40, NULL,
         1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000235, 1769100000000000230, 'BRAND_TONE_CONFIRMED', '品牌调性已确认', 'CONDITION', 50, NULL,
         1761000000000000103, 1761100000000000001, NOW()
) seed
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT profile_id FROM dp_gate_item) t
                    WHERE t.profile_id = 1769100000000000230);

-- 6) 工作台装配（主图工作台：面板与详情页一致，步骤映射不同——没有长图排版）
INSERT INTO dp_workspace_schema
(id, schema_code, delivery_type, profile_id, version, layout_json, status, remark,
 create_dept, create_by, create_time)
SELECT 1769100000000000240, 'WS_MAIN_IMAGE', 'MAIN_IMAGE', 1769100000000000202, '1.0.0',
       '{"workspace":"MULTI_IMAGE","panels":["PROJECT_HEADER","STEP_NAVIGATOR","MAIN_STAGE","INSPECTOR","ASSET_DRAWER"],"steps":[{"code":"INPUT","component":"ProjectInputPanel"},{"code":"FACT","component":"FactPanel"},{"code":"DNA","component":"VisualDnaPanel"},{"code":"DIRECTION","component":"DirectionBoard"},{"code":"STORYBOARD","component":"StoryboardBoard"},{"code":"GATE","component":"GatePanel"},{"code":"GENERATION","component":"GenerationBoard"},{"code":"QA","component":"QaPanel"},{"code":"FINAL","component":"FinalReviewPanel"}]}',
       'PUBLISHED', 'R21 种子：主图工作台（MULTI_IMAGE），步骤比详情页少一步',
       1761000000000000103, 1761100000000000001, NOW()
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT schema_code FROM dp_workspace_schema) t
                    WHERE t.schema_code = 'WS_MAIN_IMAGE');

-- 7) 主图的模块库（5 个模块各 1 屏 = 5 屏；屏类型是 MAIN_*，与详情页的 7 屏明显不同）
INSERT INTO dp_module_definition
(id, delivery_type, module_code, module_name, objective, screen_type, product_lock_level, shot,
 required, min_screens, max_screens, default_selected, default_sort_no,
 allowed_templates, allowed_workflows, required_facts, visual_rules_json, qa_rules_json,
 enabled, remark, create_dept, create_by, create_time)
SELECT * FROM (
  SELECT 1769100000000000251 AS id, 'MAIN_IMAGE' AS delivery_type, 'MAIN_WHITE_BG' AS module_code,
         '白底主图' AS module_name, '干净白底，产品完整、无遮挡' AS objective, 'MAIN_WHITE_BG' AS screen_type,
         'STRICT' AS product_lock_level, '产品居中、纯白背景、正视角' AS shot,
         '1' AS required, 1 AS min_screens, 1 AS max_screens, '1' AS default_selected, 10 AS default_sort_no,
         NULL AS allowed_templates, 'wf-i2i-qwen21' AS allowed_workflows, NULL AS required_facts,
         NULL AS visual_rules_json, NULL AS qa_rules_json, '0' AS enabled,
         'R21 种子：主图第 1 屏（必需）' AS remark,
         1761000000000000103 AS create_dept, 1761100000000000001 AS create_by, NOW() AS create_time
  UNION ALL SELECT 1769100000000000252, 'MAIN_IMAGE', 'MAIN_SELLING', '卖点图', '一眼看出核心卖点',
         'MAIN_SELLING_POINT', 'LOOSE', '卖点相关中近景 + 留出文案位', '0', 1, 1, '1', 20,
         NULL, NULL, NULL, NULL, NULL, '0', 'R21 种子：主图第 2 屏',
         1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000253, 'MAIN_IMAGE', 'MAIN_SCENE', '场景图', '代入真实使用场景',
         'MAIN_SCENE', 'LOOSE', '环境全景，产品占画面 {ratio}', '0', 1, 1, '1', 30,
         NULL, NULL, NULL, NULL, NULL, '0', 'R21 种子：主图第 3 屏',
         1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000254, 'MAIN_IMAGE', 'MAIN_DETAIL', '细节图', '材质与工艺证据',
         'MAIN_DETAIL', 'STRICT', '局部大特写（材质/结构）', '0', 1, 1, '1', 40,
         NULL, NULL, NULL, NULL, NULL, '0', 'R21 种子：主图第 4 屏',
         1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769100000000000255, 'MAIN_IMAGE', 'MAIN_SIZE', '尺寸图', '尺寸与规格',
         'MAIN_SIZE', 'STRICT', '含参照物的平视构图', '0', 1, 1, '1', 50,
         NULL, NULL, NULL, NULL, NULL, '0', 'R21 种子：主图第 5 屏',
         1761000000000000103, 1761100000000000001, NOW()
) seed
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT delivery_type, module_code FROM dp_module_definition) t
                    WHERE t.delivery_type = 'MAIN_IMAGE');

SELECT 'DP_CREATIVE_R21_MAIN_IMAGE_DONE' AS marker;
