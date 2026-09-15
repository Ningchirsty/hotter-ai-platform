-- HOTTER AI 视频任务中心菜单（SQL Server）
-- 对应前端：frontend/src/views/ai/video-tasks/index.vue

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (920000, N'视频任务', 0, 5, N'video-tasks', N'ai/video-tasks/index', N'', N'N', N'N', N'C', N'0', N'0', N'ai:video:list', N'my-task', 1761000000000000103, 1761100000000000001, GETDATE(), NULL, NULL, N'AI 视频生成任务列表（我的任务）');

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
(920001, N'视频任务查询', 920000, 1, N'', N'', N'', N'N', N'Y', N'F', N'0', N'0', N'ai:video:query', N'#', 1761000000000000103, 1761100000000000001, GETDATE(), NULL, NULL, N''),
(920002, N'视频任务取消', 920000, 2, N'', N'', N'', N'N', N'Y', N'F', N'0', N'0', N'ai:video:cancel', N'#', 1761000000000000103, 1761100000000000001, GETDATE(), NULL, NULL, N''),
(920003, N'视频任务重试', 920000, 3, N'', N'', N'', N'N', N'Y', N'F', N'0', N'0', N'ai:video:retry', N'#', 1761000000000000103, 1761100000000000001, GETDATE(), NULL, NULL, N''),
(920004, N'视频任务下载', 920000, 4, N'', N'', N'', N'N', N'Y', N'F', N'0', N'0', N'ai:video:download', N'#', 1761000000000000103, 1761100000000000001, GETDATE(), NULL, NULL, N'');
