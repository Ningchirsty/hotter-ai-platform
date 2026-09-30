-- =====================================================================
-- R43：把「逐屏出图与质检」收敛成「出图」步的组件（分镜页也提供 GenerationBoard）
--
-- 背景：分镜页底部那块「3. 逐屏出图与质检」自 R19 起一直是**页面级**内容（R39 时放在 #page-foot），
-- 但它其实是「出图」这一步的另一种呈现（按屏看：哪一屏还没出、哪一屏质检没过）。
-- 用户已确认收敛：与生产页的逐屏候选管理共用同一个组件 GenerationBoard，
-- 用 mode 区分两种呈现（SCREENS=按屏 / CANDIDATES=按候选）。
--
-- 于是配置里把一个组件名标给两个页面（R41 引入的 pages 机制正好表达这件事）：
--   {"component":"GenerationBoard","pages":["/creative/production","/creative/storyboard"]}
-- 分镜页因此多出一个「出图」步骤页签（方向 → 分镜 → 出图），把批量出图放在它该在的那一步里。
--
-- 执行方式（生产）：
--   docker exec -i ai-video-poc-mysql-1 mysql --default-character-set=utf8mb4 \
--     -uroot -p"$MYSQL_ROOT_PASSWORD" ai_video_poc < dp_creative_r43_generation_board_storyboard.sql
-- =====================================================================

-- 1) 改前留档
SELECT schema_code, version, JSON_EXTRACT(layout_json, '$.steps[6]') AS generation_step
  FROM dp_workspace_schema WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE');

-- 2) 给 GenerationBoard 的 pages 补上分镜页
UPDATE dp_workspace_schema
   SET layout_json = REPLACE(
         layout_json,
         '{"component":"GenerationBoard","pages":["/creative/production"]}',
         '{"component":"GenerationBoard","pages":["/creative/production","/creative/storyboard"]}'
       ),
       version = '1.3.0',
       remark = CONCAT(IFNULL(remark, ''), '｜R43：逐屏出图与质检收敛为出图步组件（分镜页也提供）')
 WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE')
   AND layout_json LIKE '%"component":"GenerationBoard","pages":["/creative/production"]%';

-- 3) 核对
SELECT schema_code, version, JSON_VALID(layout_json) AS json_ok,
       JSON_EXTRACT(layout_json, '$.steps[6]') AS generation_step
  FROM dp_workspace_schema WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE');

-- =====================================================================
-- 回滚（分镜页收回 GenerationBoard；前端也要一起回滚到 R42 版）
--
-- UPDATE dp_workspace_schema SET version = '1.2.0',
--   layout_json = REPLACE(layout_json,
--     '{"component":"GenerationBoard","pages":["/creative/production","/creative/storyboard"]}',
--     '{"component":"GenerationBoard","pages":["/creative/production"]}')
--  WHERE schema_code IN ('WS_LONG_PAGE', 'WS_MAIN_IMAGE');
-- =====================================================================
