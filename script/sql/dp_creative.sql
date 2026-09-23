-- ------------------------------------------------------------------
-- AI 视觉工厂（dp_*）：R0 建表与最小增量迁移
--
-- 依据：《AI视觉工厂-V0.1修订版规格（复用路线）》§3
--
-- 设计前提（五项决策）：
--   1. 复用既有内核：项目=cp_task、事实=cp_fact_snapshot、闸门=cp_gate_rule、
--      产出校验=cp_output_check、异步作业=cp_async_job、出图=ruoyi-ai 图像内核。
--   2. 本文件只建「视觉领域自己」的表：视觉基因/方向/分镜/生成记录/模板/详情页/事件。
--      **不新建**项目表、资产表、门禁表、QA 表。
--   3. 项目 = cp_task(deliverable_type='ECOM_DETAIL')，故本文件只给 cp_task 加 1 列
--      visual_stage（视觉阶段）；cp_task.status 沿用既有 14 态不动。
--
-- 说明：
--   A. 幂等：建表用 CREATE TABLE IF NOT EXISTS；加列用 information_schema 判断后
--      动态执行 DDL（写法与 script/sql/ry_video_task_migration.sql 一致），可安全重复执行。
--   B. 只加列、不改既有数据；既有 ECOM_DETAIL 任务的 visual_stage 为 NULL，
--      由服务层在首次进入视觉工厂时补为 MATERIAL_READY。
--   C. 执行顺序：必须在部署「带 visual_stage / dp_* 读写逻辑的后端镜像」**之前**执行，
--      否则新代码会因 Unknown column / Unknown table 直接失败。
--   D. 主键：应用侧雪花 ID（mybatis-plus idType=ASSIGN_ID），故 BIGINT NOT NULL 且非自增。
--      曾经踩过的坑：表若写成自增而代码不写主键，会报 Column 'id' cannot be null。
--   E. 逻辑删除：与 ruoyi-content 同口径，del_flag 由 MyBatis-Plus 裸 @TableLogic 管理，
--      默认值 0=存在 / 1=删除。
--   F. 唯一索引一律不加在「会被软删的行」上：软删后重建会撞唯一索引
--      （content 模块的「删过检查后无法再发起检查」就是这一类），
--      故 dp_* 的 (task_id, version) 只建普通索引，由服务层保证版本不重用。
--   G. 门禁规则无需新增：cp_gate_rule 已有 ECOM_DETAIL 的 9 条（sku_code/product_name/
--      main_version/color/quantity/spec_params/package_version 为 BLOCK，
--      reference_image 为 CONDITION，brand_tone 为 NOTICE），视觉工厂直接复用。
--      「视觉质量」类人工确认走 cp_interaction_card（APPROVAL 卡），不进闸门规则表——
--      因为闸门引擎只判「事实字段是否已确认」，不认识构图/光线这类非事实项。
--
-- 回滚：本文件只新增对象。如需回滚，先确认无 dp_* 数据，再 drop 这 10 张表并
--       ALTER TABLE cp_task DROP COLUMN visual_stage（脚本末尾附核对查询）。
-- ------------------------------------------------------------------

-- ==================================================================
-- 一、cp_task 增量：视觉阶段（仅 ECOM_DETAIL 使用）
-- ==================================================================

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cp_task'
        AND COLUMN_NAME = 'visual_stage'
    ),
    'SELECT ''cp_task.visual_stage already exists'' AS note',
    'ALTER TABLE cp_task ADD COLUMN visual_stage VARCHAR(40) NULL COMMENT ''视觉工厂阶段（仅 deliverable_type=ECOM_DETAIL 使用，见 DpVisualStageEnum）'' AFTER status'
  )
);
PREPARE hotter_ddl FROM @ddl;
EXECUTE hotter_ddl;
DEALLOCATE PREPARE hotter_ddl;

-- ==================================================================
-- 二、阶段事件（全链路可追溯：谁在什么时候把项目从哪个阶段推到哪个阶段）
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_stage_event (
  id          BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  task_id     BIGINT       NOT NULL COMMENT '视觉项目（cp_task.task_id）',
  event_type  VARCHAR(32)  NOT NULL COMMENT '事件类型（MATERIAL/DNA/DIRECTION/STORYBOARD/VISUAL_GATE/GENERATION/QA/LAYOUT/FINAL）',
  from_stage  VARCHAR(40)  NULL     COMMENT '变更前阶段',
  to_stage    VARCHAR(40)  NULL     COMMENT '变更后阶段',
  action      VARCHAR(64)  NULL     COMMENT '动作（如 DNA_LOCK、GENERATION_RETRY）',
  detail_json TEXT         NULL     COMMENT '事件明细（结构化，便于复盘）',
  actor_id    BIGINT       NULL     COMMENT '操作人',
  actor_name  VARCHAR(64)  NULL     COMMENT '操作人姓名（冗余，便于展示）',
  create_dept BIGINT       NULL     COMMENT '创建部门',
  create_by   BIGINT       NULL     COMMENT '创建者',
  create_time DATETIME     NULL     COMMENT '创建时间',
  update_by   BIGINT       NULL     COMMENT '更新者',
  update_time DATETIME     NULL     COMMENT '更新时间',
  del_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  KEY idx_dp_event_task (task_id, event_type, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·阶段事件';

-- ==================================================================
-- 三、Visual DNA（视觉基因：结构化视觉规范）
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_visual_dna (
  id                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  task_id           BIGINT       NOT NULL COMMENT '视觉项目（cp_task.task_id）',
  dna_no            VARCHAR(32)  NOT NULL COMMENT '编号 DNA-yyyyMMdd-xxxxxx',
  version           INT          NOT NULL DEFAULT 1 COMMENT '版本（每次重生成/编辑递增）',
  status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT草稿/REVIEW待审/LOCKED已锁定）',
  style_keywords    VARCHAR(500) NULL     COMMENT '风格关键词（逗号分隔）',
  avoid_keywords    VARCHAR(500) NULL     COMMENT '禁忌关键词（不该出现的东西）',
  color_primary     VARCHAR(32)  NULL     COMMENT '主色 #RRGGBB',
  color_secondary   VARCHAR(32)  NULL     COMMENT '辅色 #RRGGBB',
  color_accent      VARCHAR(32)  NULL     COMMENT '点缀色 #RRGGBB',
  color_bg          VARCHAR(32)  NULL     COMMENT '背景色 #RRGGBB',
  saturation        VARCHAR(16)  NULL     COMMENT '饱和度档（LOW/MEDIUM/HIGH）',
  contrast_level    VARCHAR(16)  NULL     COMMENT '对比度档（LOW/MEDIUM/HIGH）',
  lighting_type     VARCHAR(32)  NULL     COMMENT '光线类型（SOFT/HARD/STUDIO/NATURAL）',
  lighting_dir      VARCHAR(32)  NULL     COMMENT '光位（FRONT/SIDE/TOP/BACK）',
  product_ratio_min DECIMAL(5,2) NULL     COMMENT '产品占画面最小比例（%）',
  product_ratio_max DECIMAL(5,2) NULL     COMMENT '产品占画面最大比例（%）',
  whitespace_level  VARCHAR(16)  NULL     COMMENT '留白程度（LOW/MEDIUM/HIGH）',
  typography_style  VARCHAR(64)  NULL     COMMENT '字体风格描述（字体授权另议，渲染层不猜）',
  scene_type        VARCHAR(64)  NULL     COMMENT '场景类型（纯色底/生活场景/主题场景）',
  dna_json          LONGTEXT     NULL     COMMENT '完整视觉基因（权威内容；其余列是索引/展示用镜像）',
  source            VARCHAR(16)  NOT NULL DEFAULT 'AI' COMMENT '来源（AI生成/MANUAL人工/AI人工混合）',
  model_key         VARCHAR(128) NULL     COMMENT '生成所用模型标识（治理台 aig_*）',
  trace_id          VARCHAR(64)  NULL     COMMENT '治理层调用链ID（aig_invocation_audit.trace_id）',
  approved_by       BIGINT       NULL     COMMENT '锁定人',
  approved_at       DATETIME     NULL     COMMENT '锁定时间',
  remark            VARCHAR(500) NULL     COMMENT '备注',
  create_dept       BIGINT       NULL,
  create_by         BIGINT       NULL,
  create_time       DATETIME     NULL,
  update_by         BIGINT       NULL,
  update_time       DATETIME     NULL,
  del_flag          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  KEY idx_dp_dna_task (task_id, status),
  KEY idx_dp_dna_no (dna_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·Visual DNA';

-- ==================================================================
-- 四、Visual Direction（A/B/C 视觉方向）
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_visual_direction (
  id               BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  task_id          BIGINT       NOT NULL COMMENT '视觉项目（cp_task.task_id）',
  direction_code   VARCHAR(2)   NOT NULL COMMENT '方向代号（A/B/C）',
  direction_name   VARCHAR(64)  NULL     COMMENT '方向名称',
  concept          VARCHAR(500) NULL     COMMENT '一句话概念',
  strategy_json    TEXT         NULL     COMMENT '策略明细（配色/光线/场景/构图差异点）',
  preview_file_ids VARCHAR(500) NULL     COMMENT '预览图附件ID（cp_task_file.file_id，逗号分隔）',
  status           VARCHAR(16)  NOT NULL DEFAULT 'GENERATED' COMMENT '状态（GENERATED待选/SELECTED已选/REJECTED已弃）',
  sort_no          INT          NOT NULL DEFAULT 0 COMMENT '排序',
  source           VARCHAR(16)  NOT NULL DEFAULT 'AI' COMMENT '来源（AI生成/MANUAL人工）',
  model_key        VARCHAR(128) NULL     COMMENT '生成所用模型标识',
  trace_id         VARCHAR(64)  NULL     COMMENT '治理层调用链ID',
  selected_by      BIGINT       NULL     COMMENT '选定人',
  selected_at      DATETIME     NULL     COMMENT '选定时间',
  remark           VARCHAR(500) NULL,
  create_dept      BIGINT       NULL,
  create_by        BIGINT       NULL,
  create_time      DATETIME     NULL,
  update_by        BIGINT       NULL,
  update_time      DATETIME     NULL,
  del_flag         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  KEY idx_dp_dir_task (task_id, status),
  KEY idx_dp_dir_code (task_id, direction_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·视觉方向';

-- ==================================================================
-- 五、Storyboard（分镜）与单屏
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_storyboard (
  id                  BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  task_id             BIGINT       NOT NULL COMMENT '视觉项目（cp_task.task_id）',
  storyboard_no       VARCHAR(32)  NOT NULL COMMENT '编号 SB-yyyyMMdd-xxxxxx',
  version             INT          NOT NULL DEFAULT 1 COMMENT '版本',
  visual_direction_id BIGINT       NULL     COMMENT '采用的方向（dp_visual_direction.id）',
  visual_dna_id       BIGINT       NULL     COMMENT '采用的基因（dp_visual_dna.id，锁定版）',
  screen_count        INT          NOT NULL DEFAULT 0 COMMENT '屏数',
  rhythm_json         TEXT         NULL     COMMENT '节奏编排（信息密度/情绪曲线）',
  status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT草稿/REVIEW待审/LOCKED已锁定）',
  source              VARCHAR(16)  NOT NULL DEFAULT 'AI' COMMENT '来源（AI生成/MANUAL人工）',
  model_key           VARCHAR(128) NULL     COMMENT '生成所用模型标识',
  trace_id            VARCHAR(64)  NULL     COMMENT '治理层调用链ID',
  approved_by         BIGINT       NULL     COMMENT '锁定人',
  approved_at         DATETIME     NULL     COMMENT '锁定时间',
  remark              VARCHAR(500) NULL,
  create_dept         BIGINT       NULL,
  create_by           BIGINT       NULL,
  create_time         DATETIME     NULL,
  update_by           BIGINT       NULL,
  update_time         DATETIME     NULL,
  del_flag            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  KEY idx_dp_sb_task (task_id, status),
  KEY idx_dp_sb_no (storyboard_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·分镜';

CREATE TABLE IF NOT EXISTS dp_storyboard_screen (
  id                     BIGINT        NOT NULL COMMENT '主键（雪花ID）',
  storyboard_id          BIGINT        NOT NULL COMMENT '分镜（dp_storyboard.id）',
  task_id                BIGINT        NOT NULL COMMENT '视觉项目（冗余，便于按项目直查）',
  screen_no              VARCHAR(16)   NOT NULL COMMENT '屏号 S01/S02…',
  sort_no                INT           NOT NULL DEFAULT 0 COMMENT '排序',
  screen_type            VARCHAR(24)   NOT NULL COMMENT '屏类型（HERO/SELLING_POINT/SCENE/DETAIL/SIZE/PACKAGE/BRAND）',
  title                  VARCHAR(255)  NULL     COMMENT '标题文案',
  subtitle               VARCHAR(255)  NULL     COMMENT '副标题文案',
  body_text              VARCHAR(1000) NULL     COMMENT '正文文案',
  picture_solo_statement VARCHAR(1000) NULL     COMMENT '画面独白（这张图不讲文案时自己要说清什么）',
  spec_json              TEXT          NULL     COMMENT '视觉规格（镜头/构图/光线/背景）',
  workflow_code          VARCHAR(64)   NULL     COMMENT '指定出图能力编码（如 WF-HERO-001）',
  product_lock_level     VARCHAR(16)   NULL     COMMENT '产品保真等级（STRICT严格一致/LOOSE允许艺术化）',
  status                 VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/READY待生成/GENERATING生成中/GENERATED已出图/APPROVED已通过/REJECTED已否决）',
  remark                 VARCHAR(500)  NULL,
  create_dept            BIGINT        NULL,
  create_by              BIGINT        NULL,
  create_time            DATETIME      NULL,
  update_by              BIGINT        NULL,
  update_time            DATETIME      NULL,
  del_flag               CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  KEY idx_dp_screen_sb (storyboard_id, sort_no),
  KEY idx_dp_screen_task (task_id, status),
  KEY idx_dp_screen_type (task_id, screen_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·分镜单屏';

-- ==================================================================
-- 六、生成记录（哪一屏、第几次候选、什么提示词、产出哪个文件、QA 结论）
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_generation (
  id              BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  task_id         BIGINT       NOT NULL COMMENT '视觉项目（cp_task.task_id）',
  screen_id       BIGINT       NULL     COMMENT '分镜单屏（dp_storyboard_screen.id；方向预览图为 NULL）',
  candidate_no    INT          NOT NULL DEFAULT 1 COMMENT '候选序号（同屏第几次）',
  workflow_code   VARCHAR(64)  NOT NULL COMMENT '出图能力编码（图像契约，如 WF-HERO-001）',
  workflow_version VARCHAR(32) NULL     COMMENT '契约版本（发布态）',
  prompt          TEXT         NULL     COMMENT '正向提示词（实际下发）',
  negative_prompt TEXT         NULL     COMMENT '负向提示词（实际下发）',
  seed            BIGINT       NULL     COMMENT '随机种子（可复现）',
  input_json      TEXT         NULL     COMMENT '输入明细（参考图/参数/尺寸，结构化）',
  input_file_id   BIGINT       NULL     COMMENT '输入参考图附件ID（cp_task_file.file_id，业务留痕）',
  input_asset_id  BIGINT       NULL     COMMENT '输入参考图内核素材ID（image_asset.id，执行用）',
  image_task_id   BIGINT       NULL     COMMENT '执行内核任务ID（image_task.id，出图真相在这张表）',
  exec_tenant_id  VARCHAR(20)  NULL     COMMENT '执行者租户（内核按 tenant+user 校验素材归属，回读状态必须带）',
  exec_user_id    BIGINT       NULL     COMMENT '执行者用户（同上；可为任务负责人而非创建人）',
  output_file_id  BIGINT       NULL     COMMENT '产出附件ID（cp_task_file.file_id，业务留痕）',
  output_asset_id BIGINT       NULL     COMMENT '产出内核素材ID（image_asset.id，预览代理走它）',
  output_width    INT          NULL     COMMENT '产出实际宽度',
  output_height   INT          NULL     COMMENT '产出实际高度',
  status          VARCHAR(16)  NOT NULL DEFAULT 'QUEUED' COMMENT '状态（QUEUED/RUNNING/SUCCEEDED/FAILED/REJECTED筛选掉/APPROVED选定）',
  error_code      VARCHAR(64)  NULL     COMMENT '失败码（内核错误码原样保留）',
  error_message   VARCHAR(500) NULL     COMMENT '失败原因（用户可读）',
  qa_check_id     BIGINT       NULL     COMMENT 'QA 检查ID（cp_output_check.check_id；复用成品一致性检查）',
  qa_verdict      VARCHAR(16)  NULL     COMMENT 'QA 结论镜像（CONSISTENT/INCONSISTENT/UNCERTAIN；权威在 cp_output_check）',
  duration_ms     BIGINT       NULL     COMMENT '耗时（毫秒）',
  gpu_node        VARCHAR(64)  NULL     COMMENT '执行的 GPU 节点名（多卡区分）',
  remark          VARCHAR(500) NULL,
  create_dept     BIGINT       NULL,
  create_by       BIGINT       NULL,
  create_time     DATETIME     NULL,
  update_by       BIGINT       NULL,
  update_time     DATETIME     NULL,
  del_flag        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  KEY idx_dp_gen_task (task_id, screen_id, status),
  KEY idx_dp_gen_image_task (image_task_id),
  KEY idx_dp_gen_qa (qa_check_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·生成记录';

-- ==================================================================
-- 七、视觉模板库（HTML/CSS 模板，渲染层用）
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_layout_template (
  id                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  template_code     VARCHAR(64)  NOT NULL COMMENT '模板编码（如 HERO-H02）',
  template_name     VARCHAR(128) NOT NULL COMMENT '模板名称',
  template_type     VARCHAR(32)  NOT NULL COMMENT '模板类型（COVER/SELLING_POINT/SCENE/DETAIL/SIZE/PACKAGE/BRAND/TAIL）',
  version           VARCHAR(32)  NOT NULL DEFAULT '1.0.0' COMMENT '模板版本',
  schema_json       TEXT         NULL     COMMENT '模板可填字段约束（结构化）',
  html_template_key VARCHAR(512) NULL     COMMENT 'HTML 模板存储键（对象存储）',
  css_key           VARCHAR(512) NULL     COMMENT 'CSS 存储键（对象存储）',
  preview_file_id   BIGINT       NULL     COMMENT '预览图附件ID（cp_task_file.file_id）',
  status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/PUBLISHED/RETIRED）',
  enabled           CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否启用（0启用 1停用，与 sys_menu.status 同口径）',
  remark            VARCHAR(500) NULL,
  create_dept       BIGINT       NULL,
  create_by         BIGINT       NULL,
  create_time       DATETIME     NULL,
  update_by         BIGINT       NULL,
  update_time       DATETIME     NULL,
  del_flag          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  KEY idx_dp_tpl_code (template_code, version),
  KEY idx_dp_tpl_type (template_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·视觉模板';

-- ==================================================================
-- 八、详情页与版本（V0.8 机排版 / 最终版）
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_detail_page (
  id              BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  task_id         BIGINT       NOT NULL COMMENT '视觉项目（cp_task.task_id，一项目一详情页）',
  current_version INT          NOT NULL DEFAULT 0 COMMENT '当前版本号（0=尚无版本）',
  status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/V08_READY机排完成/REFINING人工精修/FINAL最终/ARCHIVED归档）',
  remark          VARCHAR(500) NULL,
  create_dept     BIGINT       NULL,
  create_by       BIGINT       NULL,
  create_time     DATETIME     NULL,
  update_by       BIGINT       NULL,
  update_time     DATETIME     NULL,
  del_flag        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  KEY idx_dp_page_task (task_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·详情页';

CREATE TABLE IF NOT EXISTS dp_detail_page_version (
  id               BIGINT        NOT NULL COMMENT '主键（雪花ID）',
  detail_page_id   BIGINT        NOT NULL COMMENT '详情页（dp_detail_page.id）',
  task_id          BIGINT        NOT NULL COMMENT '视觉项目（冗余，便于按项目直查）',
  version          INT           NOT NULL COMMENT '版本号',
  kind             VARCHAR(16)   NOT NULL COMMENT '版本类型（V08机排版/V10_FINAL最终版）',
  layout_json      LONGTEXT      NULL     COMMENT '排版数据（权威内容，可重渲染）',
  rendered_file_id BIGINT        NULL     COMMENT '渲染长图附件ID（cp_task_file.file_id）',
  page_width       INT           NULL     COMMENT '页宽（px）',
  page_height      INT           NULL     COMMENT '页高（px，长图可达数千）',
  screen_count     INT           NULL     COMMENT '屏数',
  qa_check_id      BIGINT        NULL     COMMENT '整页 QA 检查ID（cp_output_check.check_id）',
  status           VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/RENDERED已渲染/REVIEWING待审/APPROVED已通过/REJECTED已否决）',
  review_by        BIGINT        NULL     COMMENT '审核人',
  review_at        DATETIME      NULL     COMMENT '审核时间',
  review_comment   VARCHAR(1000) NULL     COMMENT '审核意见',
  remark           VARCHAR(500)  NULL,
  create_dept      BIGINT        NULL,
  create_by        BIGINT        NULL,
  create_time      DATETIME      NULL,
  update_by        BIGINT        NULL,
  update_time      DATETIME      NULL,
  del_flag         CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  KEY idx_dp_pagever_page (detail_page_id, version),
  KEY idx_dp_pagever_task (task_id, kind, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·详情页版本';

-- ==================================================================
-- 九、QA 校准样本（决策③：自动 QA 只筛除+记录；样本由 AI 生成）
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_qa_sample (
  id                     BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  sample_no              VARCHAR(32)  NOT NULL COMMENT '样本编号 QA-yyyyMMdd-xxxx',
  split                  VARCHAR(16)  NOT NULL DEFAULT 'CALIB' COMMENT '用途（CALIB校准集/TEST留出集）',
  kind                   VARCHAR(32)  NOT NULL COMMENT '样本类型（OK/DEFECT_PRODUCT_TRUTH/DEFECT_ARTIFACT/DEFECT_DNA/DEFECT_COMPOSITION）',
  defect_desc            VARCHAR(500) NULL     COMMENT '缺陷说明（告诉人这张图错在哪）',
  reference_storage_key  VARCHAR(512) NOT NULL COMMENT '参考图存储键（对象存储）',
  candidate_storage_key  VARCHAR(512) NOT NULL COMMENT '待检图存储键（对象存储）',
  width                  INT          NULL     COMMENT '待检图宽度',
  height                 INT          NULL     COMMENT '待检图高度',
  ground_truth_verdict   VARCHAR(16)  NOT NULL COMMENT '标注结论（CONSISTENT/INCONSISTENT，人工认定）',
  qa_verdict             VARCHAR(16)  NULL     COMMENT '最近一次机器结论（cp_output_check.verdict）',
  qa_score               DECIMAL(5,2) NULL     COMMENT '最近一次机器得分',
  qa_check_json          TEXT         NULL     COMMENT '最近一次检查明细（findings/metrics）',
  last_run_at            DATETIME     NULL     COMMENT '最近一次校准时间',
  remark                 VARCHAR(500) NULL,
  create_dept            BIGINT       NULL,
  create_by              BIGINT       NULL,
  create_time            DATETIME     NULL,
  update_by              BIGINT       NULL,
  update_time            DATETIME     NULL,
  del_flag               CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  KEY idx_dp_qas_kind (kind, split),
  KEY idx_dp_qas_no (sample_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·QA校准样本';

-- ==================================================================
-- 十、执行结果核对
-- ==================================================================

SELECT 'cp_task.visual_stage' AS obj,
       COUNT(*) AS present
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cp_task' AND COLUMN_NAME = 'visual_stage'
UNION ALL
SELECT CONCAT('table:', table_name), 1
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND table_name LIKE 'dp\_%'
 ORDER BY obj;

SELECT 'DP_CREATIVE_MIGRATION_DONE' AS marker;
