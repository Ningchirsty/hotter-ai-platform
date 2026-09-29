-- ------------------------------------------------------------------
-- AI 视觉工厂：R22 增量菜单（模块规划页，文档 §24）
--
-- 背景：模块引擎（R21）把"分镜有哪些屏"变成了 `dp_project_module`，但改它只能动库；
-- §24 要求一个真正的模块规划界面（左模块库 / 中顺序 / 右字段）。本脚本把它挂进菜单。
--
-- 权限：
--   页面（菜单 perms）= creative:project:list —— 能看项目的人就能进页面看计划；
--   **保存**走的是后端 `PUT /creative/v2/projects/{taskId}/module-plan`，
--   它的注解校验的是 creative:project:edit。两者刻意分开：
--   "看计划"是只读能力，"改计划"是编辑能力，前端按钮也据此禁用（不可编辑时按钮直接灰掉）。
--
-- 幂等：INSERT IGNORE（重复执行不会重复插入）；授权沿用 dp_creative_menu.sql 的同一套规则。
-- order_num = 7 → 排在既有 6 个环节页面之后（模块规划是"配置"性质，不是必经环节）。
-- ------------------------------------------------------------------

INSERT IGNORE INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
 visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
(1767000000000000108, '模块规划', 1767000000000000001, 7, 'module-plan', 'creative/moduleplan/index', '', 'N', 'N', 'C',
 '0', '0', 'creative:project:list', 'tree-table', '', '', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL,
 '文档 §24：模块库 / 顺序（拖动排序、添加、删除、复制、启停）/ 模块目标·卖点·文案·事实·视觉·参考图·Workflow·模板');

-- 授权：把 1767 段全部菜单（含新增的模块规划）授予「已拥有内容任务菜单」的角色
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, m.menu_id
  FROM sys_role_menu rm
  JOIN sys_menu m
    ON m.menu_id BETWEEN 1767000000000000001 AND 1767000000000001699
 WHERE rm.menu_id = 1765000000000000101;

-- 执行后核对
SELECT menu_id, parent_id, menu_name, menu_type, path, component, perms, order_num, status
  FROM sys_menu
  WHERE menu_id = 1767000000000000108;

SELECT r.role_key, COUNT(*) AS granted
  FROM sys_role_menu rm
  JOIN sys_role r ON r.role_id = rm.role_id
 WHERE rm.menu_id BETWEEN 1767000000000000001 AND 1767000000000001699
 GROUP BY r.role_key
 ORDER BY r.role_key;

SELECT 'DP_CREATIVE_R22_MENU_DONE' AS marker;
