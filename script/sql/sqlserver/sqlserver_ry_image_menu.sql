-- 图像创作菜单（SQL Server）
-- 菜单 ID 920002 / 920003：920000 / 920001 已被视频创作占用。

IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 920002)
INSERT INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
 visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
(920002, N'图像创作', 0, 7, N'image-creation', N'image/index', N'', N'N', N'N', N'C', N'0', N'0',
 N'image:creation:view', N'picture', N'', N'', 1761000000000000103, 1761100000000000001, GETDATE(), NULL, NULL, N'图像创作工作台');

IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 920003)
INSERT INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
 visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
(920003, N'提交图像任务', 920002, 1, N'', N'', N'', N'N', N'Y', N'F', N'0', N'0',
 N'image:creation:submit', N'#', N'', N'', 1761000000000000103, 1761100000000000001, GETDATE(), NULL, NULL, N'');

-- 可选：若已执行过工作空间归组脚本，把图像创作挂到「AI工具」分类下（父分类不存在时影响 0 行）
UPDATE sys_menu
   SET parent_id = 1764000000000000002, order_num = 4
 WHERE menu_id = 920002
   AND parent_id <> 1764000000000000002
   AND EXISTS (SELECT 1 FROM sys_menu p WHERE p.menu_id = 1764000000000000002);

-- 授权：从「视频创作」已有角色授权推导（幂等）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 920002 FROM sys_role_menu rm
 WHERE rm.menu_id = 920000
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu t WHERE t.role_id = rm.role_id AND t.menu_id = 920002);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 920003 FROM sys_role_menu rm
 WHERE rm.menu_id = 920002
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu t WHERE t.role_id = rm.role_id AND t.menu_id = 920003);

SELECT m.menu_id, m.menu_name, m.parent_id, m.order_num, m.path, m.component, m.menu_type, m.perms
  FROM sys_menu m WHERE m.menu_id IN (920002, 920003) OR m.parent_id = 920002
 ORDER BY m.menu_id;
