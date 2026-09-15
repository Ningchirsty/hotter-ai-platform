-- HOTTER AI 视频创作菜单（SQL Server）

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
  (920000, N'视频创作', 0, 6, N'video-creation', N'ai/studio/index', N'', N'N', N'N', N'C', N'0', N'0', N'ai:studio:view', N'caret-forward', N'', N'', 1761000000000000103, 1761100000000000001, GETDATE(), NULL, NULL, N'ZCode 视频创作工作台');

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
  (920001, N'提交视频任务', 920000, 1, N'', N'', N'', N'N', N'Y', N'F', N'0', N'0', N'ai:studio:submit', N'#', N'', N'', 1761000000000000103, 1761100000000000001, GETDATE(), NULL, NULL, N'');
