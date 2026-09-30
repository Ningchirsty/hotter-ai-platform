-- ------------------------------------------------------------------
-- V0.2 / R11：Sprint B1 —— 场景只读配置层（Scenario Foundation）
--
-- 依据文档 §40（Sprint B 的 5 张表 + ECOM_DETAIL 种子）与 §36（/creative/v2 只读配置接口）。
-- 本轮**只建配置层**：新表 + 种子 + 只读查询接口；既有 creative/content 流程一行判定都不改
-- （阶段机、闸门、模板校验、出图链路全部原样）。
--
-- 【权威顺序（文档 §51 红线 / 对照文档 D1）】
--   本表是"场景怎么生产"的**权威**：交付类型、步骤、输出规格、工作台装配都读它。
--   但**不复制**已有的权威：模板可用性仍在 dp_layout_template（含校验和与发布门）、
--   能力/模型仍在 aig_*（契约与治理）、阶段合法性仍在 DpVisualStageEnum#canMoveTo（代码，
--   故意不配置化——见 D3）。
--
-- 【命名决定：delivery_type 用 ECOM_DETAIL，不用文档里的 ECOM_DETAIL_PAGE】
--   生产库 cp_task.deliverable_type 与内容域枚举 `ContentDeliverableTypeEnum` 用的都是 `ECOM_DETAIL`
--   （15 个存量项目都是这个值）。文档 §40 写的是 `ECOM_DETAIL_PAGE`——若照抄就会生出**第三套词表**。
--   所以：delivery_type 取现有编码 `ECOM_DETAIL`，把文档里的名字放进 `alias_codes`，
--   接口按 code 查询时两者都能命中（见 CreativeScenarioConfigServiceImpl#resolveDeliveryType）。
--
-- 幂等：全部 CREATE TABLE IF NOT EXISTS + NOT EXISTS 守卫，可重复执行。
-- ------------------------------------------------------------------

-- ==================================================================
-- 一、交付类型
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_delivery_type (
  id                 BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  category_code      VARCHAR(64)  NOT NULL COMMENT '业务大类（如 02_ECOMMERCE）',
  delivery_type      VARCHAR(64)  NOT NULL COMMENT '交付类型编码（与 cp_task.deliverable_type 同词表）',
  alias_codes        VARCHAR(200) NULL     COMMENT '别名编码（逗号分隔，兼容文档/历史叫法，如 ECOM_DETAIL_PAGE）',
  delivery_name      VARCHAR(128) NOT NULL COMMENT '展示名',
  media_type         VARCHAR(32)  NOT NULL COMMENT '媒介类型（LONG_PAGE/POSTER/CAROUSEL/ARTICLE/VIDEO/PRINT）',
  render_mode        VARCHAR(32)  NOT NULL COMMENT '渲染模式（LONGPAGE/POSTER/ARTICLE/PRINT）',
  default_profile_id BIGINT       NULL     COMMENT '默认场景档案（dp_scenario_profile.id）',
  enabled            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否启用（0启用 1停用，与 dp_layout_template.enabled 同口径）',
  sort_no            INT          NOT NULL DEFAULT 0 COMMENT '排序',
  remark             VARCHAR(500) NULL,
  create_dept        BIGINT       NULL,
  create_by          BIGINT       NULL,
  create_time        DATETIME     NULL,
  update_by          BIGINT       NULL,
  update_time        DATETIME     NULL,
  del_flag           CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dp_delivery_type (delivery_type),
  KEY idx_dp_delivery_cat (category_code, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·交付类型（场景配置层）';

-- ==================================================================
-- 二、场景档案（一种业务"怎么生产"）
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_scenario_profile (
  id                    BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  profile_code          VARCHAR(64)  NOT NULL COMMENT '档案编码（如 PROFILE_ECOM_DETAIL_V1）',
  profile_name          VARCHAR(128) NOT NULL COMMENT '档案名称',
  delivery_type         VARCHAR(64)  NOT NULL COMMENT '交付类型编码',
  version               VARCHAR(32)  NOT NULL DEFAULT '1.0.0' COMMENT '档案版本',
  input_schema_json     TEXT         NULL     COMMENT '输入要求（需要哪些资料/字段）',
  workflow_schema_json  TEXT         NULL     COMMENT '流程总览（步骤编码顺序，冗余自 dp_scenario_step 便于一次读全）',
  output_schema_json    TEXT         NULL     COMMENT '交付物要求（形态/组件/合规）',
  workspace_schema_json TEXT         NULL     COMMENT '工作台装配（引用 dp_workspace_schema.schema_code）',
  status                VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/PUBLISHED/RETIRED）',
  remark                VARCHAR(500) NULL,
  create_dept           BIGINT       NULL,
  create_by             BIGINT       NULL,
  create_time           DATETIME     NULL,
  update_by             BIGINT       NULL,
  update_time           DATETIME     NULL,
  del_flag              CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dp_profile_code (profile_code, version),
  KEY idx_dp_profile_delivery (delivery_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·场景档案（Scenario Profile）';

-- ==================================================================
-- 三、场景步骤（流程可配置）
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_scenario_step (
  id                   BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  profile_id           BIGINT       NOT NULL COMMENT '所属场景档案（dp_scenario_profile.id）',
  step_code            VARCHAR(64)  NOT NULL COMMENT '步骤编码（INPUT/FACT/DNA/DIRECTION/STORYBOARD/GATE/GENERATION/QA/LAYOUT/FINAL）',
  step_name            VARCHAR(128) NOT NULL COMMENT '步骤名称',
  step_type            VARCHAR(32)  NOT NULL COMMENT '步骤类型（文档 §8：INPUT/FACT/DNA/DIRECTION/STORYBOARD/GATE/GENERATION/QA/LAYOUT/HUMAN_REFINE/FINAL_REVIEW 等）',
  stage_codes          VARCHAR(200) NULL     COMMENT '映射到现有阶段机的阶段编码（逗号分隔，投影用；阶段合法性仍在代码里）',
  sort_no              INT          NOT NULL DEFAULT 0 COMMENT '顺序',
  required             CHAR(1)      NOT NULL DEFAULT '1' COMMENT '是否必需（1必需 0可选）',
  gate_type            VARCHAR(64)  NULL     COMMENT '闸门类型（如 VISUAL_GATE / FACT_CONFIRMED；空=无闸门）',
  capability_code      VARCHAR(64)  NULL     COMMENT '依赖的治理能力编码（如 visual_dna_extract，空=不调模型）',
  workspace_component  VARCHAR(128) NULL     COMMENT '工作台组件名（前端注册表里的键，如 VisualDnaPanel）',
  entry_condition_json TEXT         NULL     COMMENT '进入条件（结构化，供工作台/流程引擎判断）',
  completion_rule_json TEXT         NULL     COMMENT '完成判定（结构化）',
  config_json          TEXT         NULL     COMMENT '其它配置（实现状态、备注等）',
  create_dept          BIGINT       NULL,
  create_by            BIGINT       NULL,
  create_time          DATETIME     NULL,
  update_by            BIGINT       NULL,
  update_time          DATETIME     NULL,
  del_flag             CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dp_step (profile_id, step_code),
  KEY idx_dp_step_profile (profile_id, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·场景步骤（可配置流程）';

-- ==================================================================
-- 四、输出规格（把"尺寸"配置化；B2 起页宽由它决定）
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_output_spec (
  id               BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  spec_code        VARCHAR(64)  NOT NULL COMMENT '规格编码（如 TAOBAO_DETAIL）',
  delivery_type    VARCHAR(64)  NOT NULL COMMENT '所属交付类型',
  channel          VARCHAR(32)  NULL     COMMENT '渠道（TAOBAO/TMALL/XHS/WECHAT/OFFLINE…）',
  width            INT          NOT NULL COMMENT '宽（单位见 unit）',
  height           INT          NULL     COMMENT '高（height_mode=FIXED 时必填；AUTO 表示按内容）',
  height_mode      VARCHAR(16)  NOT NULL DEFAULT 'AUTO' COMMENT '高度模式（FIXED/AUTO）',
  ratio            VARCHAR(16)  NULL     COMMENT '比例（如 3:4；与宽高二选一表达）',
  unit             VARCHAR(8)   NOT NULL DEFAULT 'px' COMMENT '单位（px/mm/cm）',
  dpi              INT          NULL     COMMENT '印刷 DPI（屏幕规格为空）',
  color_mode       VARCHAR(16)  NULL     COMMENT '色彩模式（RGB/CMYK）',
  safe_area_json   TEXT         NULL     COMMENT '安全区（结构化：上/下/左/右）',
  bleed_json       TEXT         NULL     COMMENT '出血（结构化）',
  file_format_json TEXT         NULL     COMMENT '文件格式要求（结构化：format/mime/maxSizeMB/alpha）',
  source_scale     INT          NOT NULL DEFAULT 1 COMMENT '源图倍率（导出时按此放大，1=按规格原尺寸）',
  is_default       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否该交付类型的默认规格（1是 0否）',
  sort_no          INT          NOT NULL DEFAULT 0 COMMENT '排序',
  remark           VARCHAR(500) NULL,
  create_dept      BIGINT       NULL,
  create_by        BIGINT       NULL,
  create_time      DATETIME     NULL,
  update_by        BIGINT       NULL,
  update_time      DATETIME     NULL,
  del_flag         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dp_output_spec (spec_code),
  KEY idx_dp_output_delivery (delivery_type, is_default)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·输出规格（尺寸配置化）';

-- ==================================================================
-- 五、工作台 Schema（不同场景展示不同页面）
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_workspace_schema (
  id            BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  schema_code   VARCHAR(64)  NOT NULL COMMENT '工作台编码（如 WS_LONG_PAGE）',
  delivery_type VARCHAR(64)  NOT NULL COMMENT '所属交付类型',
  profile_id    BIGINT       NULL     COMMENT '所属场景档案（可空=交付类型级默认）',
  version       VARCHAR(32)  NOT NULL DEFAULT '1.0.0' COMMENT '版本',
  layout_json   TEXT         NULL     COMMENT '装配定义（panels 列表 + steps→component 映射，见文档 §49）',
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/PUBLISHED/RETIRED）',
  remark        VARCHAR(500) NULL,
  create_dept   BIGINT       NULL,
  create_by     BIGINT       NULL,
  create_time   DATETIME     NULL,
  update_by     BIGINT       NULL,
  update_time   DATETIME     NULL,
  del_flag      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dp_workspace_code (schema_code, version),
  KEY idx_dp_workspace_delivery (delivery_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·工作台装配（Workspace Schema）';

-- ==================================================================
-- 六、种子：ECOM_DETAIL（电商详情页）
-- ==================================================================

-- 6.1 交付类型 ------------------------------------------------------
INSERT INTO dp_delivery_type
(id, category_code, delivery_type, alias_codes, delivery_name, media_type, render_mode,
 default_profile_id, enabled, sort_no, remark, create_dept, create_by, create_time)
SELECT 1765000000000000001, '02_ECOMMERCE', 'ECOM_DETAIL', 'ECOM_DETAIL_PAGE', '商品详情页',
       'LONG_PAGE', 'LONGPAGE', 1765000000000000002, '0', 10,
       'B1 种子：文档 §40 里写的是 ECOM_DETAIL_PAGE，这里用生产库/内容域枚举已有的 ECOM_DETAIL，别名放 alias_codes，避免第三套词表',
       1761000000000000103, 1761100000000000001, NOW()
  WHERE NOT EXISTS (SELECT 1 FROM (SELECT delivery_type FROM dp_delivery_type) t
                     WHERE t.delivery_type = 'ECOM_DETAIL');

-- 6.2 场景档案 ------------------------------------------------------
INSERT INTO dp_scenario_profile
(id, profile_code, profile_name, delivery_type, version, input_schema_json, workflow_schema_json,
 output_schema_json, workspace_schema_json, status, remark, create_dept, create_by, create_time)
SELECT 1765000000000000002, 'PROFILE_ECOM_DETAIL_V1', '电商详情页生产流程（V1）', 'ECOM_DETAIL', '1.0.0',
       '{"fields":[{"code":"product_name","required":true},{"code":"reference_images","required":true,"note":"参考图决定视觉基因"},{"code":"facts","required":true,"note":"事实在内容域确认"}],"note":"输入要求只是配置声明，校验仍在既有链路上"}',
       '{"steps":["INPUT","FACT","DNA","DIRECTION","STORYBOARD","GATE","GENERATION","QA","LAYOUT","FINAL"]}',
       '{"artifacts":[{"code":"LONG_PAGE","format":"PNG","from":"dp_detail_page_version"},{"code":"SCREEN_CANDIDATES","format":"PNG","from":"dp_generation"}],"note":"交付物由现有表承载，这里只声明口径"}',
       '{"schemaCode":"WS_LONG_PAGE"}',
       'PUBLISHED',
       'B1 种子：步骤见 dp_scenario_step；输出规格见 dp_output_spec；工作台见 dp_workspace_schema',
       1761000000000000103, 1761100000000000001, NOW()
  WHERE NOT EXISTS (SELECT 1 FROM (SELECT profile_code FROM dp_scenario_profile) t
                     WHERE t.profile_code = 'PROFILE_ECOM_DETAIL_V1');

-- 6.3 步骤（与现有阶段机投影对齐；capability_code 用治理台已登记的能力）-----
INSERT INTO dp_scenario_step
(id, profile_id, step_code, step_name, step_type, stage_codes, sort_no, required, gate_type,
 capability_code, workspace_component, entry_condition_json, completion_rule_json, config_json,
 create_dept, create_by, create_time)
SELECT * FROM (
  SELECT 1765000000000000010 AS id, 1765000000000000002 AS profile_id, 'INPUT' AS step_code,
         '产品资料与参考图' AS step_name, 'INPUT' AS step_type,
         'MATERIAL_READY' AS stage_codes, 10 AS sort_no, '1' AS required, NULL AS gate_type,
         NULL AS capability_code, 'ProjectInputPanel' AS workspace_component,
         '{"requireProject":true}' AS entry_condition_json,
         '{"minReferenceImages":1}' AS completion_rule_json,
         '{"impl":"existing"}' AS config_json,
         1761000000000000103 AS create_dept, 1761100000000000001 AS create_by, NOW() AS create_time
  UNION ALL SELECT 1765000000000000011, 1765000000000000002, 'FACT', '事实确认', 'FACT',
         'MATERIAL_READY', 20, '1', 'FACT_CONFIRMED', 'document_parse', 'FactPanel',
         '{"requireInput":true}', '{"confirmedFields":["product_name","spec_params"]}',
         '{"impl":"content-domain","note":"事实在内容域确认，视觉阶段不单列阶段码"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1765000000000000012, 1765000000000000002, 'DNA', '视觉基因', 'DNA',
         'DNA_GENERATING,DNA_REVIEW,DNA_LOCKED', 30, '1', 'DNA_LOCKED', 'visual_dna_extract', 'VisualDnaPanel',
         '{"requireFact":true}', '{"status":"LOCKED"}', '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1765000000000000013, 1765000000000000002, 'DIRECTION', '视觉方向', 'DIRECTION',
         'DIRECTION_GENERATING,DIRECTION_REVIEW,DIRECTION_LOCKED', 40, '1', 'DIRECTION_SELECTED',
         'creative_direction_draft', 'DirectionBoard', '{"requireDna":true}', '{"selectedCount":1}',
         '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1765000000000000014, 1765000000000000002, 'STORYBOARD', '分镜', 'STORYBOARD',
         'STORYBOARD_GENERATING,STORYBOARD_REVIEW,STORYBOARD_LOCKED', 50, '1', 'STORYBOARD_LOCKED',
         'creative_storyboard_draft', 'StoryboardBoard', '{"requireDirection":true}', '{"status":"LOCKED"}',
         '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1765000000000000015, 1765000000000000002, 'GATE', '视觉门', 'GATE',
         'VISUAL_GATE,VISUAL_LOCKED', 60, '1', 'VISUAL_GATE_PASS', NULL, 'GatePanel',
         '{"requireStoryboard":true}', '{"action":"VISUAL_GATE_PASS"}',
         '{"impl":"existing","note":"门禁项目前仍写死在 CreativeGateServiceImpl，B2 才配置化"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1765000000000000016, 1765000000000000002, 'GENERATION', '出图', 'GENERATION',
         'PRODUCING', 70, '1', NULL, NULL, 'GenerationBoard', '{"requireGate":true}', '{"candidatePerScreen":1}',
         '{"impl":"existing","note":"复用图像内核 image_task"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1765000000000000017, 1765000000000000002, 'QA', '质检', 'QA',
         'QA_PROCESSING', 80, '0', NULL, 'deliverable_consistency', 'QaPanel', '{"requireGeneration":true}',
         '{"hardFailBlocking":true}', '{"impl":"existing"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1765000000000000018, 1765000000000000002, 'LAYOUT', '长图排版', 'LAYOUT',
         'LAYOUT_PROCESSING,DESIGN_REFINING', 90, '1', NULL, NULL, 'LongPageCanvas',
         '{"requireGate":true}', '{"renderTemplate":"longpage"}',
         '{"impl":"existing","pageWidthFrom":"dp_output_spec"}', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1765000000000000019, 1765000000000000002, 'FINAL', '终审交付', 'FINAL_REVIEW',
         'FINAL_REVIEW,COMPLETED', 100, '1', 'FINAL_APPROVED', NULL, 'FinalReviewPanel',
         '{"requireLayout":true}', '{"status":"COMPLETED"}', '{"impl":"existing"}',
         1761000000000000103, 1761100000000000001, NOW()
) seed
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT profile_id FROM dp_scenario_step) t
                    WHERE t.profile_id = 1765000000000000002);

-- 6.4 输出规格（TAOBAO_DETAIL 750 = 当前写死的页宽，B2 起由这里驱动）---------
INSERT INTO dp_output_spec
(id, spec_code, delivery_type, channel, width, height, height_mode, ratio, unit, dpi, color_mode,
 safe_area_json, bleed_json, file_format_json, source_scale, is_default, sort_no, remark,
 create_dept, create_by, create_time)
SELECT * FROM (
  SELECT 1765000000000000021 AS id, 'TAOBAO_DETAIL' AS spec_code, 'ECOM_DETAIL' AS delivery_type,
         'TAOBAO' AS channel, 750 AS width, NULL AS height, 'AUTO' AS height_mode, NULL AS ratio,
         'px' AS unit, NULL AS dpi, 'RGB' AS color_mode, NULL AS safe_area_json, NULL AS bleed_json,
         '{"format":"PNG","mime":"image/png","maxSizeMB":20}' AS file_format_json, 1 AS source_scale,
         '1' AS is_default, 10 AS sort_no,
         '淘宝详情页：宽 750 = R10 之前写死的页宽，现在成为可配置规格' AS remark,
         1761000000000000103 AS create_dept, 1761100000000000001 AS create_by, NOW() AS create_time
  UNION ALL SELECT 1765000000000000022, 'TMALL_DETAIL', 'ECOM_DETAIL', 'TMALL', 790, NULL, 'AUTO', NULL,
         'px', NULL, 'RGB', NULL, NULL, '{"format":"PNG","mime":"image/png","maxSizeMB":20}', 1, '0', 20,
         '天猫详情页（宽 790）', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1765000000000000023, 'ECOM_MAIN_IMAGE', 'ECOM_DETAIL', 'TAOBAO', 800, 800, 'FIXED', '1:1',
         'px', NULL, 'RGB', NULL, NULL, '{"format":"PNG","mime":"image/png","maxSizeMB":10,"alpha":false}', 1, '0', 30,
         '电商主图 800×800（白底要求由 QA/合规声明，不在本行）', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1765000000000000024, 'ECOM_WHITE_BG', 'ECOM_DETAIL', 'TAOBAO', 800, 800, 'FIXED', '1:1',
         'px', NULL, 'RGB', NULL, NULL, '{"format":"PNG","mime":"image/png","maxSizeMB":10,"alpha":false}', 1, '0', 40,
         '电商白底图 800×800', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1765000000000000025, 'ECOM_PRODUCT_PNG', 'ECOM_DETAIL', 'TAOBAO', 800, 800, 'FIXED', '1:1',
         'px', NULL, 'RGB', NULL, NULL, '{"format":"PNG","mime":"image/png","maxSizeMB":10,"alpha":true}', 1, '0', 50,
         '电商透明底产品图 800×800', 1761000000000000103, 1761100000000000001, NOW()
) seed
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT spec_code, delivery_type FROM dp_output_spec) t
                    WHERE t.delivery_type = 'ECOM_DETAIL');

-- 6.5 工作台装配（文档 §49 的详情页示例，组件名与 §11 注册表一致）----------
INSERT INTO dp_workspace_schema
(id, schema_code, delivery_type, profile_id, version, layout_json, status, remark,
 create_dept, create_by, create_time)
SELECT 1765000000000000030, 'WS_LONG_PAGE', 'ECOM_DETAIL', 1765000000000000002, '1.0.0',
       '{"workspace":"LONG_PAGE","panels":["PROJECT_HEADER","STEP_NAVIGATOR","MAIN_STAGE","INSPECTOR","ASSET_DRAWER"],"steps":[{"code":"INPUT","component":"ProjectInputPanel"},{"code":"FACT","component":"FactPanel"},{"code":"DNA","component":"VisualDnaPanel"},{"code":"DIRECTION","component":"DirectionBoard"},{"code":"STORYBOARD","component":"StoryboardBoard"},{"code":"GATE","component":"GatePanel"},{"code":"GENERATION","component":"GenerationBoard"},{"code":"QA","component":"QaPanel"},{"code":"LAYOUT","component":"LongPageCanvas"},{"code":"FINAL","component":"FinalReviewPanel"}]}',
       'PUBLISHED',
       'B1 种子：装配定义照文档 §49；前端组件注册表（§11）在 D 阶段落地，本轮只登记配置',
       1761000000000000103, 1761100000000000001, NOW()
  WHERE NOT EXISTS (SELECT 1 FROM (SELECT schema_code FROM dp_workspace_schema) t
                     WHERE t.schema_code = 'WS_LONG_PAGE');

-- ==================================================================
-- 七、核对（跑完应看到 1 交付类型 / 1 档案 / 10 步骤 / 5 规格 / 1 工作台）
-- ==================================================================

SELECT 'delivery_type' AS t, COUNT(*) AS n FROM dp_delivery_type WHERE delivery_type = 'ECOM_DETAIL'
UNION ALL SELECT 'profile', COUNT(*) FROM dp_scenario_profile WHERE delivery_type = 'ECOM_DETAIL'
UNION ALL SELECT 'steps', COUNT(*) FROM dp_scenario_step WHERE profile_id = 1765000000000000002
UNION ALL SELECT 'output_spec', COUNT(*) FROM dp_output_spec WHERE delivery_type = 'ECOM_DETAIL'
UNION ALL SELECT 'workspace', COUNT(*) FROM dp_workspace_schema WHERE delivery_type = 'ECOM_DETAIL';

SELECT 'DP_CREATIVE_R11_SCENARIO_FOUNDATION_DONE' AS marker;
