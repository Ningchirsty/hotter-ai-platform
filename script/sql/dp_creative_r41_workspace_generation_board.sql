-- =====================================================================
-- R41（V0.2）：装配配置支持「同一个步骤在不同页面上的不同视图」
--
-- 问题：装配定义是按**交付类型**给的（每个交付类型一份 layout_json），
-- 但一个步骤在不同页面上的呈现本来就不同：
--   · 「出图」步在项目页 = 发起出图 + 候选（ProjectHeroBlock / ProjectGenerationsBlock）
--   · 「出图」步在生产页 = 逐屏候选管理（GenerationBoard）
-- 不表达这件事，生产页就没法按步骤装配（要么显示不出自己那一块，
-- 要么每页都收到"配置与插槽对不上"的假警报）。
--
-- 做法：step.components 里的一项可以是字符串（处处生效），也可以是
--   {"component":"GenerationBoard","pages":["/creative/production"]}
-- 前端 parseWorkspaceLayout 归一成 {name, pages}；pages 为空 = 不限页面。
--
-- 执行方式（生产）：
--   docker exec -i ai-video-poc-mysql-1 mysql --default-character-set=utf8mb4 \
--     -uroot -p"$MYSQL_ROOT_PASSWORD" ai_video_poc < dp_creative_r41_workspace_generation_board.sql
-- =====================================================================

-- 1) 改前留档
SELECT schema_code, version, layout_json FROM dp_workspace_schema
 WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE');

-- 2) GENERATION 步补上生产页那一块（其余步骤不变）
UPDATE dp_workspace_schema
   SET layout_json = REPLACE(
         layout_json,
         '{"code":"GENERATION","components":["ProjectHeroBlock","ProjectGenerationsBlock"]}',
         '{"code":"GENERATION","components":["ProjectHeroBlock","ProjectGenerationsBlock",{"component":"GenerationBoard","pages":["/creative/production"]}]}'
       ),
       version = '1.2.0',
       remark = CONCAT(IFNULL(remark, ''), '｜R41：出图步区分项目页/生产页两种视图')
 WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE')
   AND layout_json LIKE '%"code":"GENERATION","components":["ProjectHeroBlock","ProjectGenerationsBlock"]%';

-- 3) 核对：两行的 GENERATION 都应带上 GenerationBoard，且 JSON 仍然合法
SELECT schema_code, version,
       JSON_VALID(layout_json) AS json_ok,
       JSON_EXTRACT(layout_json, '$.steps[6]') AS generation_step
  FROM dp_workspace_schema
 WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE');

-- 4) 项目页那两块也标明"只属于项目页"。
--    为什么必须标：装配运行时按"这一步在本页应当有的组件"来提示"配置与插槽对不上"，
--    不标的话生产页会收到「声明了 ProjectHeroBlock/ProjectGenerationsBlock 却没提供插槽」的**假警报**
--    （这两块本来就只属于项目页）。真机验收第一次就抓到了这条。
UPDATE dp_workspace_schema
   SET layout_json = REPLACE(
         REPLACE(layout_json,
                 '"ProjectHeroBlock"', '{"component":"ProjectHeroBlock","pages":["/creative/project"]}'),
         '"ProjectGenerationsBlock"', '{"component":"ProjectGenerationsBlock","pages":["/creative/project"]}')
 WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE')
   AND layout_json LIKE '%"ProjectHeroBlock"%'
   AND layout_json NOT LIKE '%"component":"ProjectHeroBlock"%';

-- 5) 再核对一次 GENERATION 步（两块应带 pages，生产页那块也应带 pages）
SELECT schema_code, version, JSON_VALID(layout_json) AS json_ok,
       JSON_EXTRACT(layout_json, '$.steps[6]') AS generation_step
  FROM dp_workspace_schema
 WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE');

-- =====================================================================
-- 回滚（把 GENERATION 改回只有项目页那两块）
--
-- UPDATE dp_workspace_schema SET version = '1.1.0',
--   layout_json = REPLACE(layout_json,
--     '{"code":"GENERATION","components":["ProjectHeroBlock","ProjectGenerationsBlock",{"component":"GenerationBoard","pages":["/creative/production"]}]}',
--     '{"code":"GENERATION","components":["ProjectHeroBlock","ProjectGenerationsBlock"]}')
--  WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE');
-- =====================================================================
