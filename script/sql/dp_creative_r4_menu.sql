-- ------------------------------------------------------------------
-- AI 视觉工厂：R4 增量菜单（「流程向导」作为默认入口）
--
-- 背景（问题 2）：视觉工厂的 8 个环节散在 6 个独立页面里，每完成一步都要退回菜单再进下一页；
-- 前置不足时页面只给一句话，既不告诉你缺什么，也没有「去完成」的入口。
-- 修复：新增一个单页向导，把 8 步串成一列并在页内直接完成，排在子菜单第一位作为默认入口；
-- 原有 6 个页面全部保留，作为「直达/高级」入口（老用户的操作习惯不被破坏）。
--
-- 说明：
--   1. 幂等：INSERT IGNORE，重复执行不会重复插入；授权沿用 dp_creative_menu.sql 的同一套规则；
--   2. 权限沿用 creative:project:query（向导是聚合视图，任何能看项目的人都能看向导）；
--   3. order_num = 0 → 排在「视觉项目」之前，成为进入 AI视觉工厂后的第一个入口。
-- ------------------------------------------------------------------

INSERT IGNORE INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
 visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
(1767000000000000107, '流程向导', 1767000000000000001, 0, 'wizard', 'creative/wizard/index', '', 'N', 'N', 'C',
 '0', '0', 'creative:project:query', 'guide', '', '', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL,
 '8 步串行向导：每步可在页内直接完成，无需来回跳页');

-- 授权：把 1767 段全部菜单（含新增的向导）授予「已拥有内容任务菜单」的角色
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, m.menu_id
  FROM sys_role_menu rm
  JOIN sys_menu m
    ON m.menu_id BETWEEN 1767000000000000001 AND 1767000000000001699
 WHERE rm.menu_id = 1765000000000000101;

-- 执行后核对
SELECT menu_id, parent_id, menu_name, menu_type, path, component, perms, order_num, status
  FROM sys_menu
 WHERE menu_id BETWEEN 1767000000000000101 AND 1767000000000000107
 ORDER BY order_num, menu_id;

SELECT r.role_key, COUNT(*) AS granted
  FROM sys_role_menu rm
  JOIN sys_role r ON r.role_id = rm.role_id
 WHERE rm.menu_id BETWEEN 1767000000000000001 AND 1767000000000001699
 GROUP BY r.role_key
 ORDER BY r.role_key;

SELECT 'DP_CREATIVE_R4_MENU_DONE' AS marker;
