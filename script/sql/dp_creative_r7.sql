-- ==================================================================
-- R7：「文案与要点」+「品牌 Brief」两张表
--
-- 为什么单独建表而不是塞进 cp_fact_snapshot / dp_storyboard_screen：
--   1) 事实（cp_fact_snapshot）是「产品客观事实」，字段词表硬编码 12 个编码、
--      单条 ≤500 字、带 confirm_status 与证据来源；把「卖点排序 / 正文分段 / 参数明细」
--      硬塞成一条事实，等于把结构化信息压扁成字符串，排序与分段立刻丢失。
--   2) 分镜屏文案是「这一屏这张图配什么字」，按屏组织、锁定后不可改；
--      详情页正文与参数表是「整页要说的话」，与屏不是一对一关系。
--   3) 品牌 Brief 是「委托方的要求」，必须可判定（必显/禁用词）才能做闸门，
--      事实表里没有承载「要求」的语义位置（brand_tone 只是其中一项且是 NOTICE）。
--
-- 与既有数据的关系（不覆盖、只补充）：
--   - dp_brand_brief.brand_tone 与事实 brand_tone 并存：事实是「从资料里解析确认」的，
--     Brief 是「品牌方自己填的要求」，两者冲突时页面同时展示、由人裁定，不自动合并。
--   - dp_copy_block.source='FACT' 的块由已确认事实派生（只读展示），
--     source='MANUAL' 的块由人录入，source='MODEL' 的块由模型起草。
--
-- 【归属变更（R7 上线后追加）】两张表的**模块归属不同**，别再按前缀猜：
--   · dp_copy_block  → 创作域（ruoyi-ai-creative）：平面设计自己的产出（卖点/正文/参数）。
--   · dp_brand_brief → **内容域（ruoyi-content）**：品牌方的"委托要求"，在内容任务里录入与确认
--     （`/content/task/{taskId}/brand-brief`，服务 IContentBrandBriefService）。
--     创作域只读它（派生提示词 + 视觉门判定）。表名保留 dp_ 前缀是为了不动生产数据；
--     这是「dp_ 前缀但归内容域」的唯一例外，如实记在这里。
-- ==================================================================

CREATE TABLE IF NOT EXISTS dp_brand_brief (
  id             BIGINT        NOT NULL COMMENT '主键（雪花ID）',
  task_id        BIGINT        NOT NULL COMMENT '视觉项目（cp_task.task_id），一个项目一行',
  brand_tone     VARCHAR(500)  NULL     COMMENT '品牌调性（品牌方填写的要求，与 brand_tone 事实并存）',
  must_show      VARCHAR(2000) NULL     COMMENT '必显信息，一行一条（品牌名/logo/口号/资质等，必须出现在成品里）',
  forbidden_words VARCHAR(1000) NULL    COMMENT '禁用词与合规红线，一行一条（出图负向词与文案校验都用它）',
  target_audience VARCHAR(500) NULL     COMMENT '目标人群',
  main_push      VARCHAR(2000) NULL     COMMENT '主推卖点与优先级，一行一条，行首数字即优先级（1 最高）',
  size_spec_req  VARCHAR(1000) NULL     COMMENT '尺寸/规范要求（画布比例、留白、字号、平台规范）',
  style_ref      VARCHAR(1000) NULL     COMMENT '参考风格（可写参考图/参考品牌/风格描述）',
  status         VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT草稿/CONFIRMED品牌方已确认）',
  confirmed_by   BIGINT        NULL     COMMENT '确认人',
  confirmed_at   DATETIME      NULL     COMMENT '确认时间',
  remark         VARCHAR(500)  NULL,
  create_dept    BIGINT        NULL,
  create_by      BIGINT        NULL,
  create_time    DATETIME      NULL,
  update_by      BIGINT        NULL,
  update_time    DATETIME      NULL,
  del_flag       CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dp_brief_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·品牌Brief（委托方的要求）';

CREATE TABLE IF NOT EXISTS dp_copy_block (
  id           BIGINT        NOT NULL COMMENT '主键（雪花ID）',
  task_id      BIGINT        NOT NULL COMMENT '视觉项目（cp_task.task_id）',
  block_type   VARCHAR(24)   NOT NULL COMMENT '块类型（SELLING_POINT卖点/BODY_SECTION正文分段/SPEC_ROW参数行/MUST_SHOW必显信息）',
  sort_no      INT           NOT NULL DEFAULT 0 COMMENT '排序（详情页上从上到下的顺序，也是卖点优先级）',
  title        VARCHAR(255)  NULL     COMMENT '卖点标题/段落小标题/参数名',
  content      VARCHAR(2000) NULL     COMMENT '卖点说明/段落正文/参数值',
  source       VARCHAR(16)   NOT NULL DEFAULT 'MANUAL' COMMENT '来源（MANUAL人工录入/MODEL模型起草/FACT由已确认事实派生）',
  source_ref   VARCHAR(255)  NULL     COMMENT '来源引用（事实编码，或分镜屏号 S03），便于回看它从哪来',
  status       VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT草稿/CONFIRMED已确认）',
  remark       VARCHAR(500)  NULL,
  create_dept  BIGINT        NULL,
  create_by    BIGINT        NULL,
  create_time  DATETIME      NULL,
  update_by    BIGINT        NULL,
  update_time  DATETIME      NULL,
  del_flag     CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  KEY idx_dp_copy_task (task_id, block_type, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·文案与要点块';
