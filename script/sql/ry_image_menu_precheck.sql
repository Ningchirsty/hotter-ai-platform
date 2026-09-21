-- ------------------------------------------------------------------
-- 图像创作菜单 · 执行前预检（MySQL，只读，不改任何数据）
--
-- 用法：先在目标库跑本脚本，逐条核对「期望」列，再执行 ry_image_menu.sql。
-- 依据：docs/video-module-implementation-handoff.md §3 记录过视频模块的同类预检
-- （menu_id 是否空闲、sys_menu 列清单是否一致、create_dept/create_by 是否存在），
-- 当时因为没预检过一次，脚本选用还纠结了一番（menu vs menu_migration）。
--
-- 期望结果都写在每段注释里，任何一条不符就先停下来，不要盲目执行建菜单脚本。
-- ------------------------------------------------------------------

-- 1) 菜单 ID 是否空闲：期望 0 行（有行说明 920002/920003 已被占用，需换号）
SELECT '1-菜单ID占用检查(期望0行)' AS check_item;
SELECT menu_id, menu_name, component, perms
  FROM sys_menu
 WHERE menu_id IN (920002, 920003);

-- 2) 「视频创作」菜单是否存在：期望 1 行（=920000）
--    角色授权是用它推导的（INSERT ... SELECT FROM sys_role_menu WHERE menu_id = 920000），
--    不存在则新菜单不会授给任何角色，页面上看不到。
SELECT '2-视频创作菜单(期望1行,920000)' AS check_item;
SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id = 920000;

-- 3) 授权推导的数据源：期望至少 1 行（谁有视频创作，图像创作就授给谁）
SELECT '3-视频创作的角色授权(期望>=1行)' AS check_item;
SELECT rm.role_id, r.role_name, r.role_key
  FROM sys_role_menu rm
  LEFT JOIN sys_role r ON r.role_id = rm.role_id
 WHERE rm.menu_id = 920000;

-- 4) 权限标识冲突：期望 0 行（image:creation:view / submit 不应已被别的菜单占用）
SELECT '4-权限标识冲突(期望0行)' AS check_item;
SELECT menu_id, menu_name, perms
  FROM sys_menu
 WHERE perms LIKE 'image:creation%';

-- 5) 「AI工具」一级分类是否存在：决定 ry_image_menu.sql 末尾那条归组 UPDATE 是否生效
--    1 行 = 会挂到 AI工具 下（order 4）；0 行 = 保持顶级菜单（不影响功能，只影响分组）
SELECT '5-AI工具分类(1行=会归组,0行=保持顶级)' AS check_item;
SELECT menu_id, menu_name, parent_id, order_num, menu_type
  FROM sys_menu
 WHERE menu_id = 1764000000000000002;

-- 6) 同级排序占用情况：确认归组后 order 4 是否与现有子菜单冲突（冲突只影响显示顺序）
SELECT '6-AI工具现有子菜单(看order占用)' AS check_item;
SELECT menu_id, menu_name, order_num FROM sys_menu
 WHERE parent_id = 1764000000000000002
 ORDER BY order_num;

-- 7) 审计字段依赖：期望 2 行（create_dept / create_by 必须在 sys_dept / sys_user 里存在，
--    否则外键或审计字段会指向不存在的记录）
SELECT '7-审计字段(期望2行)' AS check_item;
SELECT 1761000000000000103 AS id, 'sys_dept' AS source FROM sys_dept WHERE dept_id = 1761000000000000103
UNION ALL
SELECT 1761100000000000001 AS id, 'sys_user' AS source FROM sys_user WHERE user_id = 1761100000000000001;

-- 8) sys_menu 列清单：确认待插入表的列与本脚本 INSERT 的列一一对应
--    （视频模块正是因为核对了列清单才确定可以用 menu.sql 而不是 menu_migration.sql）
SELECT '8-sys_menu列清单' AS check_item;
SELECT column_name, column_type, is_nullable, column_default
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'sys_menu'
 ORDER BY ordinal_position;

-- 9) 图像业务表是否已存在：期望 0 行（不存在则由 ry_image_task.sql / ry_image_workflow.sql 建）
SELECT '9-图像业务表(期望0行)' AS check_item;
SELECT table_name FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('image_task', 'image_asset', 'image_task_event', 'image_workflow_version');

-- 10) 菜单总数基线：记录执行前的行数，执行后应恰好 +2
SELECT '10-菜单总数基线' AS check_item;
SELECT COUNT(*) AS menu_count_before FROM sys_menu;
