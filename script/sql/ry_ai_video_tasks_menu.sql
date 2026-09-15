-- ----------------------------
-- HOTTER AI 视频任务中心菜单（My Tasks 视频功能界面）
-- 对应前端：frontend/src/views/ai/video-tasks/index.vue（component = ai/video-tasks/index）
-- 适配包任务 H4 · 规格见 hotter-adaptation-pack/SPEC.md
-- 注意：menu_id 使用 920000+ 段，避免与现有菜单冲突；三库适配时同步 postgres/oracle/sqlserver 目录
-- ----------------------------

-- 一级菜单：视频任务（挂在主菜单，目录型不需要；直接页面型）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (920000, '视频任务', 0, 5, 'video-tasks', 'ai/video-tasks/index', '', 1, 0, 'C', '0', '0', 'ai:video:list', 'video', 103, 1, NOW(), NULL, NULL, 'AI 视频生成任务列表（我的任务）');

-- 按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
(920001, '视频任务查询', 920000, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:video:query',  '#', 103, 1, NOW(), NULL, NULL, ''),
(920002, '视频任务取消', 920000, 2, '', '', '', 1, 0, 'F', '0', '0', 'ai:video:cancel', '#', 103, 1, NOW(), NULL, NULL, ''),
(920003, '视频任务重试', 920000, 3, '', '', '', 1, 0, 'F', '0', '0', 'ai:video:retry',  '#', 103, 1, NOW(), NULL, NULL, ''),
(920004, '视频任务下载', 920000, 4, '', '', '', 1, 0, 'F', '0', '0', 'ai:video:download', '#', 103, 1, NOW(), NULL, NULL, '');

-- 给管理员角色授权（role_id=1 超级管理员通常默认全权限，可按需执行）
-- INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 920000),(1,920001),(1,920002),(1,920003),(1,920004);
