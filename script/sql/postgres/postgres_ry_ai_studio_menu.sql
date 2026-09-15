-- HOTTER AI 视频创作菜单（PostgreSQL）

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
  (920000, 'AI 创作', 0, 5, 'ai-creation', NULL, '', 'N', 'Y', 'M', '0', '0', '', 'skill', '', '', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, 'AI 创作目录'),
  (920001, '视频创作', 920000, 1, 'video', 'ai/studio/index', '', 'N', 'N', 'C', '0', '0', 'ai:studio:view', 'caret-forward', '', '', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, 'ZCode 视频创作工作台');

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
  (920002, '提交视频任务', 920001, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'ai:studio:submit', '#', '', '', 1761000000000000103, 1761100000000000001, CURRENT_TIMESTAMP, NULL, NULL, '');
