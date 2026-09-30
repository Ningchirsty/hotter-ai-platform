-- =====================================================================
-- R48：BRAND_POSTER 的模块库（海报要出哪些"屏"）
--
-- 依据：开发指导 §9.4 的品牌海报流程 + §18/§21 的模块引擎口径
--        （模块库 = 交付类型级的"这张海报由哪几屏组成"，项目里再按需勾选）。
--
-- 事实依据（不是猜）：先看了 ECOM_DETAIL 的模块写法——
--   · allowed_workflows / allowed_templates / required_facts **都是 NULL**（NULL = 不设白名单）；
--   · screen_type 的可用值来自屏骨架契约：HERO / SELLING_POINT / SCENE / DETAIL / SIZE / BRAND；
--   · qa_rules_json 留空 = 界面上如实显示「未配置规则」（"没检查"与"检查通过"是两回事）。
-- 所以本文件照同样的写法落四个海报模块。
--
-- 执行方式（生产）：
--   docker exec -i ai-video-poc-mysql-1 mysql --default-character-set=utf8mb4 \
--     -uroot -p"$MYSQL_ROOT_PASSWORD" ai_video_poc < dp_creative_r48_brand_poster_modules.sql
-- =====================================================================

-- 1) 改前留档（应为 0 行）
SELECT COUNT(*) AS poster_modules_before FROM dp_module_definition WHERE delivery_type = 'BRAND_POSTER';

-- 2) 四个海报模块
INSERT INTO dp_module_definition
  (id, delivery_type, module_code, module_name, objective, screen_type, product_lock_level, shot,
   required, min_screens, max_screens, default_selected, default_sort_no,
   allowed_templates, allowed_workflows, required_facts, visual_rules_json, qa_rules_json,
   enabled, remark, create_dept, create_by, create_time, del_flag)
VALUES
  (1769200000000000041, 'BRAND_POSTER', 'MAIN_VISUAL', '主视觉', '一眼说清这张海报在讲什么（产品/主体 + 场景氛围）',
   'HERO', 'LOOSE', '主体居中或三分构图，留出文案位', '1', 1, 1, '1', 10,
   NULL, NULL, NULL, NULL, NULL, '0',
   'R48：海报第一屏。qaRules 留空＝界面如实显示「未配置规则」，不假装体检过',
   1761000000000000103, 1761100000000000001, NOW(), '0'),
  (1769200000000000042, 'BRAND_POSTER', 'BRAND_LOCKUP', '品牌标识', '品牌名/Logo 与主张的分层排布，必须清晰可辨',
   'BRAND', 'STRICT', 'Logo 与品牌名成组出现，不与主体抢注意力', '1', 1, 1, '1', 20,
   NULL, NULL, NULL, NULL, NULL, '0',
   'R48：品牌标识屏。product_lock_level=STRICT＝结构与配色不得变形',
   1761000000000000103, 1761100000000000001, NOW(), '0'),
  (1769200000000000043, 'BRAND_POSTER', 'CAMPAIGN_LINE', '主张与卖点', '一条主张或两三条卖点，短句、可一眼读完',
   'SELLING_POINT', 'LOOSE', '文字块与主视觉分区，不压主体', '0', 1, 2, '0', 30,
   NULL, NULL, NULL, NULL, NULL, '0',
   'R48：可选模块（default_selected=0）——海报不一定都要文案条',
   1761000000000000103, 1761100000000000001, NOW(), '0'),
  (1769200000000000044, 'BRAND_POSTER', 'ATMOSPHERE_SCENE', '氛围场景', '补一层情绪与场景，让海报不只是产品图',
   'SCENE', 'LOOSE', '环境光与材质呼应品牌调性', '0', 1, 2, '0', 40,
   NULL, NULL, NULL, NULL, NULL, '0',
   'R48：可选模块；与主视觉同一"屏集合"里按需勾选',
   1761000000000000103, 1761100000000000001, NOW(), '0');

-- 3) 核对
SELECT module_code, module_name, screen_type, product_lock_level, required, default_selected,
       min_screens, max_screens, default_sort_no, enabled
  FROM dp_module_definition WHERE delivery_type = 'BRAND_POSTER' ORDER BY default_sort_no;
SELECT COUNT(*) AS poster_modules_after,
       SUM(required = '1') AS required_modules,
       SUM(default_selected = '1') AS selected_by_default
  FROM dp_module_definition WHERE delivery_type = 'BRAND_POSTER' AND del_flag = '0';

-- =====================================================================
-- 回滚
-- DELETE FROM dp_module_definition WHERE delivery_type = 'BRAND_POSTER';
-- =====================================================================
