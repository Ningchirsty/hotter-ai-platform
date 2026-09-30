-- =====================================================================
-- R46：新增交付类型 BRAND_POSTER（品牌海报）——**只落配置，先停用**
--
-- 依据：开发指导 §3.1（一级分类 03_BRAND_PROMOTION）、§3.2（交付类型清单里有 BRAND_POSTER，
-- 且明确"一个交付类型可以有多个输出规格，例如 1080×1440 / 1080×1920 / 1920×1080"）、
-- §9.4（品牌海报的流程）、§10（工作台用 workspace_schema 装配，**禁止再按场景加页面**）。
--
-- 与文档 §9.4 的一处**有意偏离**（代码约束，不是口味）：
--   文档画的是 INPUT → DNA → POSTER_CONCEPT → GENERATION → POSTER_LAYOUT → REVIEW → EXPORT，
--   没有视觉门；但代码里"未过视觉门不放行"是**出图前的硬前置**
--   （CreativeGenerationServiceImpl：「视觉门前置：未过门不放行」），
--   且视觉门的判据来自分镜 + 已锁定基因 + 已确认事实。
--   所以本流程在 GENERATION 之前插入一步 GATE（与 ECOM_DETAIL 同位置），
--   否则这条流程在第一次出图时必然被后端拒绝。
--
-- 为什么先 enabled='1'（停用）：配置先落地、但**先不让它被选中**。
-- 前端还缺两样东西（下一步做）：STEP_CODE_TO_PAGE 里新步骤的页面归属、以及海报专用组件。
-- 现在就把类型打开，用户新建项目时会选到一条半成品流程——那比"晚一步上线"更糟。
-- 前端补齐后，把这一行的 enabled 改成 '0' 即可（本文件末尾给了那条 UPDATE）。
--
-- 执行方式（生产）：
--   docker exec -i ai-video-poc-mysql-1 mysql --default-character-set=utf8mb4 \
--     -uroot -p"$MYSQL_ROOT_PASSWORD" ai_video_poc < dp_creative_r46_brand_poster.sql
-- =====================================================================

-- 0) 先看清参照物（ECOM_DETAIL 的步骤词汇：step_type / capability_code）
SELECT s.step_code, s.step_type, IFNULL(s.capability_code, '') AS capability, s.sort_no,
       s.required, IFNULL(s.gate_type, '') AS gate_type, s.stage_codes
  FROM dp_scenario_step s JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE p.delivery_type = 'ECOM_DETAIL' ORDER BY s.sort_no;

-- 1) 交付类型（停用状态；media/render 沿用"图片 + 多图打包"）
INSERT INTO dp_delivery_type
  (id, category_code, delivery_type, alias_codes, delivery_name, media_type, render_mode,
   default_profile_id, enabled, sort_no, remark, create_dept, create_by, create_time, del_flag)
VALUES
  (1769200000000000001, '03_BRAND_PROMOTION', 'BRAND_POSTER', 'POSTER', '品牌海报', 'IMAGE',
   'MULTI_IMAGE', 1769200000000000002, '1', 30,
   'R46：品牌海报（开发指导 §9.4）。先停用——前端页面归属与海报组件补齐后改 enabled=0',
   1761000000000000103, 1761100000000000001, NOW(), '0');

-- 2) 场景档案（工作台引用 WS_POSTER；输入/输出 schema 与 ECOM_DETAIL 同构，便于同一套校验）
INSERT INTO dp_scenario_profile
  (id, profile_code, profile_name, delivery_type, version, input_schema_json, workflow_schema_json,
   output_schema_json, workspace_schema_json, status, remark, create_dept, create_by, create_time, del_flag)
VALUES
  (1769200000000000002, 'PROFILE_BRAND_POSTER_V1', '品牌海报生产流程（V1）', 'BRAND_POSTER', '1.0.0',
   '{"fields":[{"code":"product_name","required":true},{"code":"brand_guide","required":false}]}',
   '{"steps":["INPUT","DNA","POSTER_CONCEPT","GATE","GENERATION","POSTER_LAYOUT","REVIEW","EXPORT"]}',
   '{"artifacts":[{"code":"POSTER","format":"PNG","from":"dp_generation"}]}',
   '{"schemaCode":"WS_POSTER"}',
   'PUBLISHED', 'R46：品牌海报流程；GATE 是按代码约束插入的（未过视觉门不放行出图）',
   1761000000000000103, 1761100000000000001, NOW(), '0');

-- 3) 八个步骤。step_type 从 ECOM_DETAIL 的对应步骤**照抄**（不猜词汇）；
--    stage_codes 复用现有 19 个阶段（阶段来自代码 DpVisualStageEnum，配置只能组合、不能新造）。
--    workspace_component 先留 NULL（海报专用组件还没做，不装作已经有了）。
INSERT INTO dp_scenario_step
  (id, profile_id, step_code, step_name, step_type, stage_codes, sort_no, required, gate_type,
   capability_code, workspace_component, entry_condition_json, completion_rule_json, config_json,
   create_dept, create_by, create_time, del_flag)
SELECT 1769200000000000011, 1769200000000000002, 'INPUT', '产品资料与参考图', s.step_type,
       'MATERIAL_READY', 10, '1', NULL, NULL, NULL, '{"requireProject":true}', NULL,
       '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM dp_scenario_step s JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE p.delivery_type = 'ECOM_DETAIL' AND s.step_code = 'INPUT' LIMIT 1;

INSERT INTO dp_scenario_step
  (id, profile_id, step_code, step_name, step_type, stage_codes, sort_no, required, gate_type,
   capability_code, workspace_component, entry_condition_json, completion_rule_json, config_json,
   create_dept, create_by, create_time, del_flag)
SELECT 1769200000000000012, 1769200000000000002, 'DNA', '视觉基因', s.step_type,
       'DNA_GENERATING,DNA_REVIEW,DNA_LOCKED', 20, '1', 'DNA_LOCKED', NULL, NULL,
       '{"requireInput":true}', '{"status":"LOCKED"}', '{"impl":"existing"}',
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM dp_scenario_step s JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE p.delivery_type = 'ECOM_DETAIL' AND s.step_code = 'DNA' LIMIT 1;

INSERT INTO dp_scenario_step
  (id, profile_id, step_code, step_name, step_type, stage_codes, sort_no, required, gate_type,
   capability_code, workspace_component, entry_condition_json, completion_rule_json, config_json,
   create_dept, create_by, create_time, del_flag)
SELECT 1769200000000000013, 1769200000000000002, 'POSTER_CONCEPT', '海报概念与主视觉', s.step_type,
       'STORYBOARD_GENERATING,STORYBOARD_REVIEW,STORYBOARD_LOCKED', 30, '1', 'STORYBOARD_LOCKED',
       NULL, NULL, '{"requireDna":true}', '{"status":"LOCKED"}',
       '{"impl":"existing","note":"海报的"屏"=主视觉与配套版式，登记在分镜链路里（阶段复用 STORYBOARD_*）"}',
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM dp_scenario_step s JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE p.delivery_type = 'ECOM_DETAIL' AND s.step_code = 'STORYBOARD' LIMIT 1;

INSERT INTO dp_scenario_step
  (id, profile_id, step_code, step_name, step_type, stage_codes, sort_no, required, gate_type,
   capability_code, workspace_component, entry_condition_json, completion_rule_json, config_json,
   create_dept, create_by, create_time, del_flag)
SELECT 1769200000000000014, 1769200000000000002, 'GATE', '视觉门', s.step_type,
       'VISUAL_GATE,VISUAL_LOCKED', 40, '1', 'VISUAL_GATE_PASS', NULL, NULL,
       '{"requireStoryboard":true}', '{"action":"VISUAL_GATE_PASS"}',
       '{"impl":"existing","note":"代码约束：未过视觉门不放行出图，故必须先门后出图"}',
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM dp_scenario_step s JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE p.delivery_type = 'ECOM_DETAIL' AND s.step_code = 'GATE' LIMIT 1;

INSERT INTO dp_scenario_step
  (id, profile_id, step_code, step_name, step_type, stage_codes, sort_no, required, gate_type,
   capability_code, workspace_component, entry_condition_json, completion_rule_json, config_json,
   create_dept, create_by, create_time, del_flag)
SELECT 1769200000000000015, 1769200000000000002, 'GENERATION', '主视觉出图', s.step_type,
       'PRODUCING', 50, '1', NULL, NULL, NULL, '{"requireGate":true}', NULL,
       '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM dp_scenario_step s JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE p.delivery_type = 'ECOM_DETAIL' AND s.step_code = 'GENERATION' LIMIT 1;

INSERT INTO dp_scenario_step
  (id, profile_id, step_code, step_name, step_type, stage_codes, sort_no, required, gate_type,
   capability_code, workspace_component, entry_condition_json, completion_rule_json, config_json,
   create_dept, create_by, create_time, del_flag)
SELECT 1769200000000000016, 1769200000000000002, 'POSTER_LAYOUT', '海报版式与多尺寸适配', s.step_type,
       'LAYOUT_PROCESSING,DESIGN_REFINING', 60, '1', NULL, NULL, NULL,
       '{"requireGeneration":true}', NULL,
       '{"impl":"existing","pageWidthFrom":"dp_output_spec","note":"一个交付类型多档尺寸，按 dp_output_spec 逐个出图"}',
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM dp_scenario_step s JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE p.delivery_type = 'ECOM_DETAIL' AND s.step_code = 'LAYOUT' LIMIT 1;

INSERT INTO dp_scenario_step
  (id, profile_id, step_code, step_name, step_type, stage_codes, sort_no, required, gate_type,
   capability_code, workspace_component, entry_condition_json, completion_rule_json, config_json,
   create_dept, create_by, create_time, del_flag)
SELECT 1769200000000000017, 1769200000000000002, 'REVIEW', '终审', s.step_type,
       'V08_READY,FINAL_REVIEW', 70, '1', 'FINAL_APPROVED', NULL, NULL,
       '{"requireLayout":true}', '{"action":"FINAL_APPROVED"}', '{"impl":"existing"}',
       1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM dp_scenario_step s JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE p.delivery_type = 'ECOM_DETAIL' AND s.step_code = 'FINAL' LIMIT 1;

INSERT INTO dp_scenario_step
  (id, profile_id, step_code, step_name, step_type, stage_codes, sort_no, required, gate_type,
   capability_code, workspace_component, entry_condition_json, completion_rule_json, config_json,
   create_dept, create_by, create_time, del_flag)
SELECT 1769200000000000018, 1769200000000000002, 'EXPORT', '导出交付', s.step_type,
       'COMPLETED', 80, '1', NULL, NULL, NULL, '{"requireLayout":true}', NULL,
       '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW(), '0'
  FROM dp_scenario_step s JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE p.delivery_type = 'ECOM_DETAIL' AND s.step_code = 'FINAL' LIMIT 1;

-- 4) 输出规格：一个交付类型，多档尺寸（文档 §3.2 明说 BRAND_POSTER 可以同时有这三档）
INSERT INTO dp_output_spec
  (id, spec_code, delivery_type, channel, width, height, height_mode, ratio, unit, dpi, color_mode,
   source_scale, is_default, sort_no, remark, create_dept, create_by, create_time, del_flag)
VALUES
  (1769200000000000021, 'BRAND_POSTER_3_4',  'BRAND_POSTER', 'SOCIAL', 1080, 1440, 'FIXED', '3:4',  'px', 72,  'RGB', 1, '1', 10, 'R46：竖版主用规格（小红书/公众号封面）', 1761000000000000103, 1761100000000000001, NOW(), '0'),
  (1769200000000000022, 'BRAND_POSTER_9_16', 'BRAND_POSTER', 'SOCIAL', 1080, 1920, 'FIXED', '9:16', 'px', 72,  'RGB', 1, '0', 20, 'R46：竖版长图规格（故事封面/信息流）',   1761000000000000103, 1761100000000000001, NOW(), '0'),
  (1769200000000000023, 'BRAND_POSTER_16_9', 'BRAND_POSTER', 'SOCIAL', 1920, 1080, 'FIXED', '16:9', 'px', 72,  'RGB', 1, '0', 30, 'R46：横版规格（Banner/头图）',          1761000000000000103, 1761100000000000001, NOW(), '0');

-- 5) 工作台装配：与 §10 一致——五类面板 + 该交付类型自己的步骤组件
INSERT INTO dp_workspace_schema
  (id, schema_code, delivery_type, profile_id, version, layout_json, status, remark,
   create_dept, create_by, create_time, del_flag)
VALUES
  (1769200000000000031, 'WS_POSTER', 'BRAND_POSTER', 1769200000000000002, '1.0.0',
   '{"workspace":"POSTER","panels":["PROJECT_HEADER","STEP_NAVIGATOR","MAIN_STAGE","INSPECTOR","ASSET_DRAWER"],"steps":[{"code":"INPUT","components":["ProjectAssetsBlock","ProjectBriefBlock"]},{"code":"DNA","components":["VisualDnaPanel"]},{"code":"POSTER_CONCEPT","components":["StoryboardBoard"]},{"code":"GATE","components":["GatePanel"]},{"code":"GENERATION","components":["GenerationBoard"]},{"code":"POSTER_LAYOUT","components":["LongPageCanvas"]},{"code":"REVIEW","components":["FinalReviewPanel"]},{"code":"EXPORT","components":["FinalReviewPanel"]}]}',
   'PUBLISHED',
   'R46：品牌海报工作台。先复用现有组件（海报专用组件 PosterCanvas 等下一步再做）；页面归属补进前端 STEP_CODE_TO_PAGE 后才启用交付类型',
   1761000000000000103, 1761100000000000001, NOW(), '0');

-- 6) 核对（交付类型 / 步骤数 / 规格档数 / 工作台 JSON 是否合法）
SELECT d.delivery_type, d.delivery_name, d.enabled,
       (SELECT COUNT(*) FROM dp_scenario_step s WHERE s.profile_id = 1769200000000000002) AS steps,
       (SELECT COUNT(*) FROM dp_output_spec o WHERE o.delivery_type = 'BRAND_POSTER') AS specs,
       (SELECT JSON_VALID(layout_json) FROM dp_workspace_schema WHERE schema_code = 'WS_POSTER') AS ws_json_ok
  FROM dp_delivery_type d WHERE d.delivery_type = 'BRAND_POSTER';

-- 7) 前端补齐后，用这条把它打开（**先别执行**）
-- UPDATE dp_delivery_type SET enabled = '0'
--  WHERE delivery_type = 'BRAND_POSTER'
--    AND EXISTS (SELECT 1 FROM dp_workspace_schema WHERE schema_code = 'WS_POSTER');

-- =====================================================================
-- 回滚（整条交付类型撤掉；步骤/规格/工作台都按 id 段 17692 清）
--
-- DELETE FROM dp_scenario_step     WHERE profile_id = 1769200000000000002;
-- DELETE FROM dp_output_spec       WHERE delivery_type = 'BRAND_POSTER';
-- DELETE FROM dp_workspace_schema  WHERE schema_code = 'WS_POSTER';
-- DELETE FROM dp_scenario_profile  WHERE id = 1769200000000000002;
-- DELETE FROM dp_delivery_type     WHERE delivery_type = 'BRAND_POSTER';
-- =====================================================================
