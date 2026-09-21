-- 图像创作菜单（PostgreSQL）
-- 菜单 ID 920002 / 920003：920000 / 920001 已被视频创作占用。
-- 幂等：ON CONFLICT DO NOTHING；授权从「视频创作」已有授权推导。

INSERT INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
 visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
(920002, '图像创作', 0, 7, 'image-creation', 'image/index', '', 'N', 'N', 'C', '0', '0',
 'image:creation:view', 'picture', '', '', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '图像创作工作台'),
(920003, '提交图像任务', 920002, 1, '', '', '', 'N', 'Y', 'F', '0', '0',
 'image:creation:submit', '#', '', '', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '')
ON CONFLICT (menu_id) DO NOTHING;

-- 可选：若已执行过工作空间归组脚本，把图像创作挂到「AI工具」分类下（父分类不存在时影响 0 行）
UPDATE sys_menu
   SET parent_id = 1764000000000000002, order_num = 4
 WHERE menu_id = 920002
   AND parent_id <> 1764000000000000002
   AND EXISTS (SELECT 1 FROM sys_menu p WHERE p.menu_id = 1764000000000000002);

-- 授权：从「视频创作」已有角色授权推导（幂等）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 920002 FROM sys_role_menu rm WHERE rm.menu_id = 920000
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 920003 FROM sys_role_menu rm WHERE rm.menu_id = 920002
ON CONFLICT (role_id, menu_id) DO NOTHING;

SELECT m.menu_id, m.menu_name, m.parent_id, m.order_num, m.path, m.component, m.menu_type, m.perms
  FROM sys_menu m WHERE m.menu_id IN (920002, 920003) OR m.parent_id = 920002
 ORDER BY m.menu_id;
