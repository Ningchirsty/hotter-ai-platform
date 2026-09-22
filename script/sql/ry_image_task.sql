-- ------------------------------------------------------------------
-- 图像创作模块：任务 / 素材 / 事件三张表（MySQL）
--
-- 与视频模块同构（见 ry_video_task.sql），差异：
--   1. 没有时长/帧率概念，改为记录输出宽高与是否带 alpha（抠图能力必须透明）；
--   2. size_label / strength_label 取代 tier / duration_seconds；
--   3. id 一律由应用侧雪花 ID 生成，因此 NOT NULL 且非自增（曾经的缺陷：
--      代码传 null 期望自增，报 Column 'id' cannot be null）。
--
-- 脚本为 CREATE TABLE IF NOT EXISTS，可安全重复执行。
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS image_asset (
  id            BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
  tenant_id     VARCHAR(20)  NOT NULL COMMENT '租户编号',
  user_id       BIGINT       NOT NULL COMMENT '所属用户',
  task_id       BIGINT       NULL COMMENT '产出该素材的任务（上传素材为 NULL）',
  asset_type    VARCHAR(16)  NOT NULL COMMENT 'IMAGE',
  source_kind   VARCHAR(16)  NOT NULL COMMENT 'UPLOAD / OUTPUT',
  original_name VARCHAR(255) NULL COMMENT '原始文件名（仅展示，不作为存储键）',
  storage_key   VARCHAR(512) NOT NULL COMMENT '存储键（按租户/用户分目录）',
  content_type  VARCHAR(128) NULL COMMENT 'MIME',
  size_bytes    BIGINT       NULL COMMENT '字节数',
  checksum      VARCHAR(128) NULL COMMENT 'SHA-256',
  width         INT          NULL COMMENT '实测宽度',
  height        INT          NULL COMMENT '实测高度',
  has_alpha     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否带透明通道',
  create_dept   BIGINT       NULL,
  create_by     BIGINT       NULL,
  create_time   DATETIME     NULL,
  update_by     BIGINT       NULL,
  update_time   DATETIME     NULL,
  del_flag      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0 存在 2 删除',
  PRIMARY KEY (id),
  KEY idx_image_asset_owner (tenant_id, user_id, del_flag),
  KEY idx_image_asset_task (task_id),
  KEY idx_image_asset_checksum (checksum)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '图像创作 · 素材';

CREATE TABLE IF NOT EXISTS image_task (
  id                  BIGINT        NOT NULL COMMENT '主键（雪花 ID）',
  tenant_id           VARCHAR(20)   NOT NULL COMMENT '租户编号',
  user_id             BIGINT        NOT NULL COMMENT '所属用户',
  task_no             VARCHAR(64)   NOT NULL COMMENT '业务编号 IMAGE-yyyyMMdd-xxxxxx',
  task_name           VARCHAR(255)  NULL,
  capability_code     VARCHAR(32)   NOT NULL COMMENT 'T2I/I2I/EDIT/BGREMOVE/WHITEBG',
  workflow_code       VARCHAR(64)   NOT NULL COMMENT '契约 workflowCode',
  workflow_version    VARCHAR(32)   NOT NULL,
  model_code          VARCHAR(32)   NULL,
  status              VARCHAR(16)   NOT NULL COMMENT 'QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELED/TIMEOUT',
  size_label          VARCHAR(64)   NULL COMMENT 'size 档位标签（文生图）',
  strength_label      VARCHAR(64)   NULL COMMENT 'strength 档位标签（图生图）',
  prompt              VARCHAR(1000) NULL,
  negative_prompt     VARCHAR(500)  NULL,
  input_json          JSON          NULL COMMENT '字段快照（含素材 ID）',
  comfy_prompt_id     VARCHAR(64)   NULL COMMENT 'ComfyUI prompt_id',
  comfy_worker        VARCHAR(64)   NULL COMMENT '执行的 ComfyUI 实例名',
  output_asset_id     BIGINT        NULL,
  cover_asset_id      BIGINT        NULL,
  progress            INT           NOT NULL DEFAULT 0,
  error_code          VARCHAR(64)   NULL,
  error_message       VARCHAR(1000) NULL,
  attempt_count       INT           NOT NULL DEFAULT 0,
  output_width        INT           NULL COMMENT '实测宽度（ffprobe 的等价物：ImageIO 实测）',
  output_height       INT           NULL COMMENT '实测高度',
  output_has_alpha    TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '实测是否带 alpha',
  output_size_bytes   BIGINT        NULL COMMENT '实测字节数',
  idempotency_key     VARCHAR(128)  NULL,
  submitted_time      DATETIME      NULL,
  started_time        DATETIME      NULL,
  finished_time       DATETIME      NULL,
  create_dept         BIGINT        NULL,
  create_by           BIGINT        NULL,
  create_time         DATETIME      NULL,
  update_by           BIGINT        NULL,
  update_time         DATETIME      NULL,
  del_flag            CHAR(1)       NOT NULL DEFAULT '0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_image_task_no (task_no),
  UNIQUE KEY uk_image_task_idempotency (tenant_id, user_id, idempotency_key),
  KEY idx_image_task_owner (tenant_id, user_id, status, del_flag),
  KEY idx_image_task_comfy (comfy_prompt_id),
  KEY idx_image_task_status (status, update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '图像创作 · 任务';

CREATE TABLE IF NOT EXISTS image_task_event (
  id          BIGINT        NOT NULL,
  task_id     BIGINT        NOT NULL,
  tenant_id   VARCHAR(20)   NOT NULL,
  sequence    INT           NOT NULL COMMENT '任务内自增序号',
  event_type  VARCHAR(32)   NOT NULL COMMENT 'CREATED/SUBMITTED/SUCCEEDED/FAILED/TIMEOUT',
  detail      VARCHAR(1000) NULL,
  create_time DATETIME      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_image_event_seq (task_id, sequence),
  KEY idx_image_event_task (task_id, sequence)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '图像创作 · 任务事件';
