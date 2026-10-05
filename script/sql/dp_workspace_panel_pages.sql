-- ============================================================================
-- 工作台装配：面板也能写 `pages`（v1 人工测试反馈「各页面板裁剪」）
-- ----------------------------------------------------------------------------
-- 背景：v1 人工测试发现，视觉基因 / 视觉方向与分镜 / AI生产中心 / 详情页与审核
-- 四页**各自已经有页头**（页面用 `#page-head` 提供：页面标题 + 项目选择 + 刷新），
-- 却仍然叠着配置里的「产品信息」（PROJECT_HEADER）与「流程指引」（STEP_NAVIGATOR），
-- 于是同一个页面上出现两层标题，本页模块被挤到下面。
--
-- 为什么不是把这两个面板从配置里删掉：视觉项目页**没有**自己的页头
-- （它的头部就是 PROJECT_HEADER）。所以这不是"要不要"，而是"在哪些页面上要"——
-- 前端 R44 起面板与步骤组件一样支持 `pages`（`frontend/src/views/creative/composables/
-- workspaceAssembly.ts` 的 `PanelRef` / `panelsForPage`）。
--
-- 本脚本把它们限定到 `/creative/project`：
--   ["PROJECT_HEADER","STEP_NAVIGATOR","MAIN_STAGE","INSPECTOR","ASSET_DRAWER"]
--   →
--   [{"code":"PROJECT_HEADER","pages":["/creative/project"]},
--    {"code":"STEP_NAVIGATOR","pages":["/creative/project"]},
--    "MAIN_STAGE","INSPECTOR","ASSET_DRAWER"]
--
-- ⚠️ 上线顺序：**前端先上、配置后改**。老前端只认纯字符串面板，
--    遇到对象项会过滤掉 → 面板整块消失（比"多一层标题"严重得多）。
--    前端解析同时认两种写法（纯字符串归一成"不限页面"），所以前端先上是安全的。
-- ⚠️ 幂等：只在面板[0]仍是字符串 `PROJECT_HEADER`、面板[1]仍是字符串 `STEP_NAVIGATOR` 时改。
-- ============================================================================

-- 1) 执行前：各已发布装配的面板清单
SELECT '=== 1) 执行前：panels ===' AS s;
SELECT delivery_type, schema_code, status,
       JSON_EXTRACT(layout_json, '$.panels') AS panels,
       JSON_TYPE(JSON_EXTRACT(layout_json, '$.panels[0]')) AS panels0_type
  FROM dp_workspace_schema
 WHERE del_flag = '0' AND status = 'PUBLISHED'
 ORDER BY delivery_type;

-- 2) 把这两个面板限定到视觉项目页（其余面板不动、顺序不动）
UPDATE dp_workspace_schema
   SET layout_json = JSON_SET(layout_json, '$.panels', JSON_ARRAY(
         JSON_OBJECT('code', 'PROJECT_HEADER',  'pages', JSON_ARRAY('/creative/project')),
         JSON_OBJECT('code', 'STEP_NAVIGATOR',  'pages', JSON_ARRAY('/creative/project')),
         'MAIN_STAGE', 'INSPECTOR', 'ASSET_DRAWER'
       )),
       remark = CONCAT(COALESCE(remark, ''),
           ' ｜v1反馈：产品信息与流程指引只在视觉项目页出现（面板 pages 限定）'),
       update_time = sysdate()
 WHERE del_flag = '0' AND status = 'PUBLISHED'
   AND JSON_TYPE(JSON_EXTRACT(layout_json, '$.panels[0]')) = 'STRING'
   AND JSON_UNQUOTE(JSON_EXTRACT(layout_json, '$.panels[0]')) = 'PROJECT_HEADER'
   AND JSON_UNQUOTE(JSON_EXTRACT(layout_json, '$.panels[1]')) = 'STEP_NAVIGATOR'
   AND JSON_VALID(layout_json);

-- 3) 执行后核对
SELECT '=== 3) 执行后：panels ===' AS s;
SELECT delivery_type, schema_code,
       JSON_EXTRACT(layout_json, '$.panels') AS panels
  FROM dp_workspace_schema
 WHERE del_flag = '0' AND status = 'PUBLISHED'
 ORDER BY delivery_type;

SELECT '=== 3.1 仍是老写法（纯字符串）的已发布装配（期望为空）===' AS s;
SELECT delivery_type, schema_code
  FROM dp_workspace_schema
 WHERE del_flag = '0' AND status = 'PUBLISHED'
   AND JSON_UNQUOTE(JSON_EXTRACT(layout_json, '$.panels[0]')) = 'PROJECT_HEADER'
   AND JSON_TYPE(JSON_EXTRACT(layout_json, '$.panels[0]')) = 'STRING';

-- ---------------------------------------------------------------------------
-- 回滚（把面板写回纯字符串，= 所有页面都出现产品信息与流程指引）
-- ---------------------------------------------------------------------------
-- UPDATE dp_workspace_schema
--    SET layout_json = JSON_SET(layout_json, '$.panels',
--          JSON_ARRAY('PROJECT_HEADER','STEP_NAVIGATOR','MAIN_STAGE','INSPECTOR','ASSET_DRAWER')),
--        update_time = sysdate()
--  WHERE del_flag = '0' AND status = 'PUBLISHED'
--    AND JSON_UNQUOTE(JSON_EXTRACT(layout_json, '$.panels[0].code')) = 'PROJECT_HEADER';
