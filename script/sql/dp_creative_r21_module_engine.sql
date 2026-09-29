-- ------------------------------------------------------------------
-- V0.2 / R21：模块引擎（文档 §18/§19/§20/§21）
--
-- 目的：把"分镜有哪些屏"从"一份进程级契约文件"变成"**每个交付类型一套模块定义 + 每个项目一份模块计划**"。
--   文档 §18 给了两张表；§19 给了固定骨架规则（HERO → [可选 N] → PRODUCT_INFO → BRAND_END）；
--   §20 给了模块定义字段（objective / allowedTemplates / allowedWorkflows / requiredFacts /
--   visualRules / qaRules / minScreens / maxScreens）；§21 明确 "Module Plan → Screen Plan"，
--   一个模块可以占 1..N 屏，screen_no 保留但不再假设总数是 7。
--
-- 与"契约文件"的关系（**权威顺序**，改之前先读这里）：
--   * `dp_module_definition` / `dp_project_module` 决定**屏集合**：哪些类型、各几屏、顺序；
--   * 契约文件（creative/screen-skeleton.json）保留"每类屏怎么拍"的知识（shot / 保真等级 / 展示名），
--     并在**项目还没有模块计划时**作为兜底整份生效（fail-safe，不静默改成空骨架）；
--   * 本轮把 ECOM_DETAIL 的 7 屏逐字镜像成 6 个模块（HERO 1 屏 + SELLING_POINT 2 屏 + 场景/细节/尺寸/品牌各 1 屏），
--     因此**切换后行为与今天逐屏一致**——这是切驱动的前提，由单测等价性断言钉住。
--
-- 幂等：都带 NOT EXISTS 守卫（守卫的派生表必须 SELECT 出所有被用到的列，否则 MySQL 会 Unknown column
--       并在第一条语句就停住——R11 踩过）。
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS dp_module_definition (
  id                 BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  delivery_type      VARCHAR(64)  NOT NULL COMMENT '交付类型（不同场景不同模块库）',
  module_code        VARCHAR(64)  NOT NULL COMMENT '模块编码（HERO/SELLING_POINT/…）',
  module_name         VARCHAR(128) NOT NULL COMMENT '模块名（多屏时作为前缀：卖点 → 卖点一/卖点二）',
  objective          VARCHAR(500) NULL     COMMENT '模块目标（文档 §20 objective）',
  screen_type        VARCHAR(32)  NOT NULL COMMENT '生成的屏类型（进提示词与草稿工厂）',
  product_lock_level VARCHAR(16)  NOT NULL DEFAULT 'LOOSE' COMMENT '产品保真等级（STRICT/LOOSE）',
  shot               VARCHAR(200) NULL     COMMENT '取景（可用 {ratio} 占位产品占比区间）',
  required           CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否必需（1必需 0可选）',
  min_screens        INT          NOT NULL DEFAULT 1 COMMENT '最少屏数',
  max_screens        INT          NOT NULL DEFAULT 1 COMMENT '最多屏数',
  default_selected   CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否进入默认骨架（1是 0否=可选模块库）',
  default_sort_no    INT          NOT NULL DEFAULT 0 COMMENT '默认骨架里的顺序（仅 default_selected=1 有意义）',
  allowed_templates  VARCHAR(500) NULL     COMMENT '允许的模板码（逗号分隔）',
  allowed_workflows  VARCHAR(500) NULL     COMMENT '允许的工作流/能力码（逗号分隔）',
  required_facts     VARCHAR(500) NULL     COMMENT '需要的事实字段（逗号分隔）',
  visual_rules_json  TEXT         NULL     COMMENT '视觉规则（文档 §20 visualRules）',
  qa_rules_json      TEXT         NULL     COMMENT '质检规则（文档 §20 qaRules）',
  enabled            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否启用（0启用 1停用）',
  remark             VARCHAR(500) NULL,
  create_dept        BIGINT       NULL,
  create_by          BIGINT       NULL,
  create_time        DATETIME     NULL,
  update_by          BIGINT       NULL,
  update_time        DATETIME     NULL,
  del_flag           CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dp_module_def (delivery_type, module_code),
  KEY idx_dp_module_def_default (delivery_type, default_selected, default_sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·模块定义（文档 §18/§20）';

CREATE TABLE IF NOT EXISTS dp_project_module (
  id           BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  task_id      BIGINT       NOT NULL COMMENT '视觉项目（cp_task.task_id）',
  module_code  VARCHAR(64)  NOT NULL COMMENT '模块编码（dp_module_definition.module_code）',
  module_name  VARCHAR(128) NOT NULL COMMENT '模块名（快照，改名不影响历史）',
  screen_type  VARCHAR(32)  NOT NULL COMMENT '屏类型（快照）',
  screen_count INT          NOT NULL DEFAULT 1 COMMENT '本模块占几屏',
  sort_no      INT          NOT NULL DEFAULT 0 COMMENT '项目里的顺序',
  status       VARCHAR(16)  NOT NULL DEFAULT 'PLANNED' COMMENT '状态（PLANNED已计划/CONFIRMED已确认）',
  source       VARCHAR(16)  NOT NULL DEFAULT 'DEFAULT' COMMENT '来源（DEFAULT按交付类型初始化 / MANUAL人工调整）',
  remark       VARCHAR(500) NULL,
  create_dept  BIGINT       NULL,
  create_by    BIGINT       NULL,
  create_time  DATETIME     NULL,
  update_by    BIGINT       NULL,
  update_time  DATETIME     NULL,
  del_flag     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dp_project_module (task_id, module_code),
  KEY idx_dp_project_module_task (task_id, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·项目模块计划（文档 §18/§21）';

-- ------------------------------------------------------------------
-- ECOM_DETAIL 的模块库（文档 §18 的 15 个模块名；其中 6 个进入默认骨架）
-- 前 6 行 = 今天契约文件里的 7 屏逐字镜像：
--   HERO 1 + SELLING_POINT 2 + SCENE 1 + DETAIL 1 + SIZE 1 + BRAND 1 = 7 屏
-- ------------------------------------------------------------------
INSERT INTO dp_module_definition
(id, delivery_type, module_code, module_name, objective, screen_type, product_lock_level, shot,
 required, min_screens, max_screens, default_selected, default_sort_no,
 allowed_templates, allowed_workflows, required_facts, visual_rules_json, qa_rules_json,
 enabled, remark, create_dept, create_by, create_time)
SELECT * FROM (
  SELECT 1769000000000000001 AS id, 'ECOM_DETAIL' AS delivery_type, 'HERO' AS module_code,
         '主图' AS module_name, '第一眼说清"这是什么"' AS objective, 'HERO' AS screen_type,
         'STRICT' AS product_lock_level, '产品全貌，正视角（或 15° 微侧）' AS shot,
         '1' AS required, 1 AS min_screens, 1 AS max_screens, '1' AS default_selected, 10 AS default_sort_no,
         NULL AS allowed_templates, NULL AS allowed_workflows, NULL AS required_facts,
         NULL AS visual_rules_json, NULL AS qa_rules_json, '0' AS enabled,
         'R21 默认骨架第 1 屏（与契约文件逐字一致）' AS remark,
         1761000000000000103 AS create_dept, 1761100000000000001 AS create_by, NOW() AS create_time
  UNION ALL SELECT 1769000000000000002, 'ECOM_DETAIL', 'SELLING_POINT', '卖点', '把核心卖点讲透（可占多屏）',
         'SELLING_POINT', 'LOOSE', '功能/卖点相关的中近景', '1', 2, 2, '1', 20,
         NULL, NULL, NULL, NULL, NULL, '0', 'R21 默认骨架：默认 2 屏 → 卖点一/卖点二（与契约文件一致）',
         1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000003, 'ECOM_DETAIL', 'SCENE_DISPLAY', '使用场景', '代入使用：空间关系 + 家居价值',
         'SCENE', 'LOOSE', '环境全景，产品占画面 {ratio}', '1', 1, 2, '1', 30,
         NULL, NULL, NULL, NULL, NULL, '0', 'R21 默认骨架第 4 屏',
         1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000004, 'ECOM_DETAIL', 'CRAFT_DETAIL', '细节工艺', '材质/结构/接口的证据',
         'DETAIL', 'STRICT', '局部大特写（材质/结构/接口）', '1', 1, 2, '1', 40,
         NULL, NULL, NULL, NULL, NULL, '0', 'R21 默认骨架第 5 屏',
         1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000005, 'ECOM_DETAIL', 'SIZE_ADVANTAGE', '尺寸参数', '尺寸与规格（可含参照物）',
         'SIZE', 'STRICT', '含参照物的平视构图', '0', 1, 1, '1', 50,
         NULL, NULL, NULL, NULL, NULL, '0', 'R21 默认骨架第 6 屏',
         1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000006, 'ECOM_DETAIL', 'BRAND_END', '品牌收尾', '把品牌与产品收在一起',
         'BRAND', 'LOOSE', '产品与品牌元素的合影', '1', 1, 1, '1', 60,
         NULL, NULL, NULL, NULL, NULL, '0', 'R21 默认骨架第 7 屏',
         1761000000000000103, 1761100000000000001, NOW()
  -- 以下为文档 §18 的可选模块库（不进默认骨架：default_selected=0），供"模块规划"页面装配
  UNION ALL SELECT 1769000000000000007, 'ECOM_DETAIL', 'SERIES_THEME', '系列主题', '系列/主题叙事',
         'SELLING_POINT', 'LOOSE', '主题相关的氛围中景', '0', 1, 2, '0', 0,
         NULL, NULL, NULL, NULL, NULL, '0', '文档 §18 模块库（可选）', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000008, 'ECOM_DETAIL', 'GIFT_EXPRESSION', '礼赠表达', '礼赠场景与心意',
         'SCENE', 'LOOSE', '礼赠场景全景', '0', 1, 1, '0', 0,
         NULL, NULL, NULL, NULL, NULL, '0', '文档 §18 模块库（可选）', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000009, 'ECOM_DETAIL', 'ASSEMBLY_EXPERIENCE', '组装体验', '开箱/组装过程',
         'DETAIL', 'LOOSE', '组装动作中近景', '0', 1, 2, '0', 0,
         NULL, NULL, NULL, NULL, NULL, '0', '文档 §18 模块库（可选）', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000010, 'ECOM_DETAIL', 'FREE_CREATION', '自由创作', 'DIY/自由搭配',
         'SCENE', 'LOOSE', '创作过程全景', '0', 1, 2, '0', 0,
         NULL, NULL, NULL, NULL, NULL, '0', '文档 §18 模块库（可选）', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000011, 'ECOM_DETAIL', 'FLOWER_STRUCTURE', '花艺结构', '花材与结构说明',
         'DETAIL', 'STRICT', '花材结构特写', '0', 1, 2, '0', 0,
         NULL, NULL, NULL, NULL, NULL, '0', '文档 §18 模块库（可选）', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000012, 'ECOM_DETAIL', 'FUNCTION_FEATURE', '功能特性', '功能点逐条说明',
         'SELLING_POINT', 'LOOSE', '功能演示中景', '0', 1, 3, '0', 0,
         NULL, NULL, NULL, NULL, NULL, '0', '文档 §18 模块库（可选）', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000013, 'ECOM_DETAIL', 'PLAY_DEMO', '玩法演示', '玩法/使用演示',
         'SCENE', 'LOOSE', '使用动作全景', '0', 1, 2, '0', 0,
         NULL, NULL, NULL, NULL, NULL, '0', '文档 §18 模块库（可选）', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000014, 'ECOM_DETAIL', 'COMPARE_SELLING', '对比卖点', '与替代方案的对比',
         'SELLING_POINT', 'LOOSE', '左右对比构图', '0', 1, 2, '0', 0,
         NULL, NULL, NULL, NULL, NULL, '0', '文档 §18 模块库（可选）', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000015, 'ECOM_DETAIL', 'WARM_NOTICE', '温馨提示', '使用与养护提醒',
         'DETAIL', 'LOOSE', '图文说明版式', '0', 1, 1, '0', 0,
         NULL, NULL, NULL, NULL, NULL, '0', '文档 §18 模块库（可选）', 1761000000000000103, 1761100000000000001, NOW()
  UNION ALL SELECT 1769000000000000016, 'ECOM_DETAIL', 'PRODUCT_INFO', '产品参数', '规格/参数表',
         'SIZE', 'STRICT', '参数表版式', '0', 1, 1, '0', 0,
         NULL, NULL, NULL, NULL, NULL, '0', '文档 §18 模块库（可选；本场景用 SIZE_ADVANTAGE 承担）',
         1761000000000000103, 1761100000000000001, NOW()
) seed
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT delivery_type, module_code FROM dp_module_definition) t
                    WHERE t.delivery_type = 'ECOM_DETAIL');

-- ------------------------------------------------------------------
-- 修正段（幂等）：本文件第一次执行时种子里有两处写错，已在上面的 INSERT 里改对；
-- 这里把**已经种进去的行**也纠正过来，因为守卫是 NOT EXISTS(delivery_type='ECOM_DETAIL')，
-- 重跑不会覆盖既有行。两处错误与后果：
--   1) SELLING_POINT.min_screens 写成 1 → 默认骨架只有 6 屏（应为 7 屏，与契约文件一致）；
--   2) CRAFT_DETAIL.required 写成 '2' → required 是 CHAR(1)，只能是 0/1。
-- 这类错误正是"等价性单测 + 真机核对"要挡住的：单测用的是手写夹具，只有核对**库里的种子**
-- 才能发现"夹具对、种子错"。
-- ------------------------------------------------------------------
UPDATE dp_module_definition SET min_screens = 2, max_screens = 2, remark = 'R21 默认骨架：默认 2 屏 → 卖点一/卖点二（与契约文件一致）'
 WHERE delivery_type = 'ECOM_DETAIL' AND module_code = 'SELLING_POINT' AND min_screens <> 2;
UPDATE dp_module_definition SET required = '1'
 WHERE delivery_type = 'ECOM_DETAIL' AND module_code = 'CRAFT_DETAIL' AND required NOT IN ('0', '1');

SELECT 'DP_CREATIVE_R21_MODULE_ENGINE_DONE' AS marker;
