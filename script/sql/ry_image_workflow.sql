-- ------------------------------------------------------------------
-- 图像创作模块：工作流版本表（MySQL）
--
-- 定位与 video_workflow_version 完全一致：**契约文件的同步镜像**，
-- 供查询/审计/运维；运行时权威仍是 script/image/workflows/image-workflow-contracts.json。
--
-- 注意：JdbcImageWorkflowVersionRepository 的 ON DUPLICATE KEY UPDATE 子句
-- 刻意不含 status / published_by / published_time —— 一次同步不得把审核结果冲掉。
--
-- 其他数据库（oracle/postgres/sqlserver）变体在联调前补齐（与视频模块同样的做法）。
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS image_workflow_version (
  id               BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
  capability_code  VARCHAR(32)  NOT NULL COMMENT 'T2I/I2I/EDIT/BGREMOVE',
  workflow_code    VARCHAR(64)  NOT NULL,
  model_code       VARCHAR(32)  NULL,
  version          VARCHAR(32)  NOT NULL,
  workflow_path    VARCHAR(255) NOT NULL COMMENT '模板路径（相对 image.contract-root）',
  mapping_json     JSON         NULL COMMENT '字段 → 节点输入白名单',
  output_rule_json JSON         NULL,
  billing_json     JSON         NULL,
  perf_json        JSON         NULL,
  checksum         VARCHAR(128) NULL COMMENT '模板 SHA-256；模板未加载时为 NULL（不伪装成已校验）',
  status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/TESTING/PUBLISHED/RETIRED',
  test_report_json JSON         NULL,
  published_by     BIGINT       NULL,
  published_time   DATETIME     NULL,
  create_dept      BIGINT       NULL,
  create_by        BIGINT       NULL,
  create_time      DATETIME     NULL,
  update_by        BIGINT       NULL,
  update_time      DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_image_workflow_version (workflow_code, version),
  KEY idx_image_capability_status (capability_code, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '图像创作 · 工作流版本（契约镜像）';
