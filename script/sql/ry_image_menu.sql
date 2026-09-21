-- ------------------------------------------------------------------
-- 图像创作菜单（MySQL）
--
-- 列清单与 ry_video_menu.sql 完全一致（sys_menu 的 22 列），保证与既有迁移脚本同构。
-- 菜单 ID 920002 / 920003：920000 / 920001 已被视频创作占用（见 ry_video_menu.sql）。
-- 权限标识遵循 ${module}:${business}:${action}：image:creation:view / image:creation:submit。
--
-- 与视频模块的一处改进：视频的角色授权当时是**在生产库手工补的**（docs/video-module-implementation-handoff.md §3），
-- 脚本里没有记录，导致换环境要重新问一遍。这里把授权一并写进脚本，并从「视频创作」已有的授权推导，
-- 保证两个创作模块的可见范围一致；全部为幂等语句，可重复执行。
-- ------------------------------------------------------------------

INSERT INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
 visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
(920002, '图像创作', 0, 7, 'image-creation', 'image/index', '', 'N', 'N', 'C', '0', '0',
 'image:creation:view', 'picture', '', '', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL, '图像创作工作台'),
(920003, '提交图像任务', 920002, 1, '', '', '', 'N', 'Y', 'F', '0', '0',
 'image:creation:submit', '#', '', '', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL, '');

-- 可选：若已执行过 zongxiang_workspace_menu.sql（把「视频创作」挂到「AI工具」分类下），
-- 则把图像创作也挂到同一分类。父分类不存在时影响 0 行，不会把菜单挂到不存在的父级上。
UPDATE sys_menu
   SET parent_id = 1764000000000000002, order_num = 4
 WHERE menu_id = 920002
   AND parent_id <> 1764000000000000002
   AND EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE menu_id = 1764000000000000002) t);

-- 授权：从「视频创作」已有的角色授权推导，保证两个创作模块可见范围一致（幂等）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 920002 FROM sys_role_menu rm WHERE rm.menu_id = 920000;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 920003 FROM sys_role_menu rm WHERE rm.menu_id = 920002;

-- 执行后核对（人工看一眼）
SELECT m.menu_id, m.menu_name, m.parent_id, m.order_num, m.path, m.component, m.menu_type, m.perms
  FROM sys_menu m WHERE m.menu_id IN (920002, 920003) OR m.parent_id = 920002
 ORDER BY m.menu_id;
