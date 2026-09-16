-- ------------------------------------------------------------------
-- 视频工作流版本表（目标结构，后端视频模块实现时启用）
-- 契约源文件：script/video/workflows/video-workflow-contracts.json（唯一权威）
-- 设计依据：comfyui搭建/docs/04-data-model-and-task-state.md（原稿使用 ai_workflow_version 命名）
-- 说明：
--   1. 种子数据由契约 JSON 导入（后端导入脚本随 H6 阶段实现），不在本文件手写；
--   2. API 工作流文件保存在后端受控目录，本表只存路径与 SHA-256 校验值；
--   3. 同一 workflow_code 同一时间只能有一个 PUBLISHED 版本；
--   4. 其他数据库（oracle/postgres/sqlserver）变体在联调前补齐；
--   5. 若 ai_workflow_version 已建且有数据，勿直接执行本文件创建空表，
--      先核对 script/sql/ry_video_menu_migration.sql 中的可选改名步骤。
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS video_workflow_version (
  id              BIGINT       NOT NULL                COMMENT '主键',
  capability_code VARCHAR(32)  NOT NULL                COMMENT '能力编码（I2V/T2V/MFRAME/CAMMOVE/VEXT/VHD/LIP）',
  workflow_code   VARCHAR(64)  NOT NULL                COMMENT '工作流编码（wf-{module}-{model} 或固定工作流编码）',
  model_code      VARCHAR(32)  NULL                    COMMENT '底模编码；固定工具工作流（VHD/LIP）为 NULL',
  version         VARCHAR(32)  NOT NULL                COMMENT '工作流版本',
  workflow_path   VARCHAR(255) NOT NULL                COMMENT 'API Format JSON 在后端受控目录的相对路径',
  mapping_json    JSON         NULL                    COMMENT '字段→节点输入白名单 [{field,nodeId,inputKey,note}]',
  output_rule_json JSON        NULL                    COMMENT '输出解析规则（输出节点/字段/格式/封面）',
  billing_json    JSON         NULL                    COMMENT '计费展示 {type:credits|eta, credits, eta}',
  perf_json       JSON         NULL                    COMMENT '性能 {avgSeconds,maxSeconds,timeoutSeconds,concurrency}',
  checksum        VARCHAR(128) NULL                    COMMENT '模板文件 SHA-256',
  status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/TESTING/PUBLISHED/RETIRED',
  test_report_json JSON        NULL                    COMMENT 'docs/07 清单 §7/§8 测试记录',
  published_by    BIGINT       NULL                    COMMENT '发布人',
  published_time  DATETIME     NULL                    COMMENT '发布时间',
  create_dept     BIGINT       NULL,
  create_by       BIGINT       NULL,
  create_time     DATETIME     NULL,
  update_by       BIGINT       NULL,
  update_time     DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_workflow_version (workflow_code, version),
  KEY idx_capability_status (capability_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频工作流版本（7 能力 × 底模绑定）';
