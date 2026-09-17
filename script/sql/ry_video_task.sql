-- ------------------------------------------------------------------
-- 视频创作模块：任务、素材、事件持久化（实现 §5 最小闭环）
--
-- 设计依据：
--   script/video/workflows/README.md（契约边界硬约束）
--   script/video/workflows/video-workflow-contracts.json（唯一权威契约）
--   script/sql/ry_video_workflow.sql（工作流版本表，已单独建表）
--
-- 说明：
--   1. 本文件只建表，不写入任何工作流种子数据。工作流版本由契约 JSON 导入程序写入。
--   2. 三个 H3 工作流在实机验收前必须保持 DRAFT；本表不改变其状态。
--   3. 所有查询必须同时约束 tenant_id 与 user_id，避免跨租户/跨用户越权。
--   4. 素材只保存后端对象存储路径，不保存浏览器本地文件名。
--   5. 幂等：全部使用 CREATE TABLE IF NOT EXISTS，可安全重复执行。
-- ------------------------------------------------------------------

-- 素材：上传后的输入素材（图片/音频/视频）或任务产出的成片
CREATE TABLE IF NOT EXISTS video_asset (
  id             BIGINT       NOT NULL                COMMENT '主键（雪花）',
  tenant_id      VARCHAR(20)  NOT NULL                COMMENT '租户编号',
  user_id        BIGINT       NOT NULL                COMMENT '归属用户',
  task_id        BIGINT       NULL                    COMMENT '产出该素材的任务；上传素材为 NULL',
  asset_type     VARCHAR(16)  NOT NULL                COMMENT 'IMAGE/AUDIO/VIDEO',
  source_kind    VARCHAR(16)  NOT NULL                COMMENT 'UPLOAD 用户上传 / OUTPUT 任务产出',
  original_name  VARCHAR(255) NULL                    COMMENT '原始文件名（仅展示用）',
  storage_key    VARCHAR(512) NOT NULL                COMMENT '对象存储键或后端受控相对路径',
  content_type   VARCHAR(128) NULL                    COMMENT 'MIME 类型',
  size_bytes     BIGINT       NULL                    COMMENT '字节数',
  checksum       VARCHAR(128) NULL                    COMMENT 'SHA-256，用于去重与完整性校验',
  width          INT          NULL                    COMMENT '图片/视频宽度',
  height         INT          NULL                    COMMENT '图片/视频高度',
  duration_ms    BIGINT       NULL                    COMMENT '音视频时长（毫秒），实测值',
  create_dept    BIGINT       NULL,
  create_by      BIGINT       NULL,
  create_time    DATETIME     NULL,
  update_by      BIGINT       NULL,
  update_time    DATETIME     NULL,
  del_flag       CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '删除标志（0 存在 2 删除）',
  PRIMARY KEY (id),
  KEY idx_asset_owner (tenant_id, user_id, del_flag),
  KEY idx_asset_task (task_id),
  KEY idx_asset_checksum (checksum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频素材（上传素材与任务成片）';

-- 任务：一次视频生成请求及其生命周期
CREATE TABLE IF NOT EXISTS video_task (
  id                BIGINT       NOT NULL             COMMENT '主键（雪花）',
  tenant_id         VARCHAR(20)  NOT NULL             COMMENT '租户编号',
  user_id           BIGINT       NOT NULL             COMMENT '归属用户',
  task_no           VARCHAR(64)  NOT NULL             COMMENT '业务编号 VIDEO-YYYYMMDD-xxx',
  task_name         VARCHAR(255) NULL                 COMMENT '任务名称',
  capability_code   VARCHAR(32)  NOT NULL             COMMENT 'I2V/T2V/FL2V',
  workflow_code     VARCHAR(64)  NOT NULL             COMMENT '如 wf-t2v-h3',
  workflow_version  VARCHAR(32)  NOT NULL             COMMENT '提交时锁定的工作流版本',
  model_code        VARCHAR(32)  NULL                 COMMENT '底模编码',
  status            VARCHAR(16)  NOT NULL             COMMENT 'QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELED/TIMEOUT',
  tier              VARCHAR(32)  NOT NULL             COMMENT '输出档位（当前仅 高清 · 1080P）',
  duration_seconds  INT          NOT NULL             COMMENT '目标时长（秒），当前仅 5',
  prompt            VARCHAR(1000) NULL                COMMENT '视频描述',
  input_json        JSON         NULL                 COMMENT '字段→素材ID 白名单，已服务端校验',
  comfy_prompt_id   VARCHAR(64)  NULL                 COMMENT 'ComfyUI prompt_id',
  output_asset_id   BIGINT       NULL                 COMMENT '成片素材 ID',
  cover_asset_id    BIGINT       NULL                 COMMENT '封面素材 ID',
  progress          INT          NOT NULL DEFAULT 0   COMMENT '进度 0-100',
  error_code        VARCHAR(64)  NULL                 COMMENT '失败分类（契约/网络/超时/输出不合规）',
  error_message     VARCHAR(1000) NULL                COMMENT '脱敏后的失败原因',
  attempt_count     INT          NOT NULL DEFAULT 0   COMMENT '已尝试次数',
  output_width      INT          NULL                 COMMENT '成片实测宽度',
  output_height     INT          NULL                 COMMENT '成片实测高度',
  output_fps        DOUBLE       NULL                 COMMENT '成片实测帧率',
  output_duration_ms BIGINT      NULL                 COMMENT '成片实测时长（毫秒）',
  truncation_applied TINYINT(1)  NOT NULL DEFAULT 0   COMMENT '是否因超过 5 秒被截断',
  idempotency_key   VARCHAR(128) NULL                 COMMENT '客户端幂等键',
  submitted_time    DATETIME     NULL                 COMMENT '提交到 ComfyUI 的时间',
  started_time      DATETIME     NULL                 COMMENT '开始执行时间',
  finished_time     DATETIME     NULL                 COMMENT '终态时间',
  create_dept       BIGINT       NULL,
  create_by         BIGINT       NULL,
  create_time       DATETIME     NULL,
  update_by         BIGINT       NULL,
  update_time       DATETIME     NULL,
  del_flag          CHAR(1)      NOT NULL DEFAULT '0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_no (task_no),
  UNIQUE KEY uk_task_idempotency (tenant_id, user_id, idempotency_key),
  KEY idx_task_owner (tenant_id, user_id, status, del_flag),
  KEY idx_task_comfy (comfy_prompt_id),
  KEY idx_task_status (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频生成任务';

-- 事件：任务状态流转审计与断点恢复
CREATE TABLE IF NOT EXISTS video_task_event (
  id           BIGINT       NOT NULL                  COMMENT '主键（雪花）',
  task_id      BIGINT       NOT NULL                  COMMENT '任务 ID',
  tenant_id    VARCHAR(20)  NOT NULL                  COMMENT '冗余租户，便于按租户审计',
  sequence     INT          NOT NULL                  COMMENT '任务内自增序号，保证事件可重放',
  event_type   VARCHAR(32)  NOT NULL                  COMMENT 'CREATED/VALIDATED/SUBMITTED/QUEUED/RUNNING/OUTPUT_READY/TRUNCATED/SUCCEEDED/FAILED/CANCELED/TIMEOUT',
  detail       VARCHAR(1000) NULL                     COMMENT '脱敏详情',
  create_time  DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_seq (task_id, sequence),
  KEY idx_event_task (task_id, sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频任务事件（状态流转与重放）';
