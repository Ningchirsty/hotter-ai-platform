-- 视频创作菜单（Oracle）

INSERT ALL
  INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
  VALUES (920000, '视频创作', 0, 6, 'video-creation', 'video/index', '', 'N', 'N', 'C', '0', '0', 'video:creation:view', 'caret-forward', '', '', 1761000000000000103, 1761100000000000001, SYSDATE, NULL, NULL, '视频创作工作台')
  INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
  VALUES (920001, '提交视频任务', 920000, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'video:creation:submit', '#', '', '', 1761000000000000103, 1761100000000000001, SYSDATE, NULL, NULL, '')
SELECT 1 FROM DUAL;
