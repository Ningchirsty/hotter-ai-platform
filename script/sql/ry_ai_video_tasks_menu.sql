-- ----------------------------
-- HOTTER AI 视频任务中心菜单（My Tasks 视频功能界面）
-- 对应前端：frontend/src/views/ai/video-tasks/index.vue（component = ai/video-tasks/index）
-- 适配包任务 H4 · 规格见 hotter-adaptation-pack/SPEC.md
-- 注意：menu_id 使用 920000+ 段，避免与现有菜单冲突；其他数据库脚本位于 postgres/oracle/sqlserver 目录
-- ----------------------------

-- 一级菜单：视频任务（挂在主菜单，目录型不需要；直接页面型）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (920000, '视频任务', 0, 5, 'video-tasks', 'ai/video-tasks/index', '', 'N', 'N', 'C', '0', '0', 'ai:video:list', 'my-task', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL, 'AI 视频生成任务列表（我的任务）');

-- 按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
(920001, '视频任务查询', 920000, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'ai:video:query',  '#', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL, ''),
(920002, '视频任务取消', 920000, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'ai:video:cancel', '#', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL, ''),
(920003, '视频任务重试', 920000, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'ai:video:retry',  '#', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL, ''),
(920004, '视频任务下载', 920000, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'ai:video:download', '#', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL, '');

-- 超级管理员通常默认拥有全部权限；如需显式授权，可使用 role_id=1761300000000000001
-- INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1761300000000000001, 920000),(1761300000000000001,920001),(1761300000000000001,920002),(1761300000000000001,920003),(1761300000000000001,920004);
