-- ============================================================================
-- AI 视觉工厂 · 设计角色独立（内测 C1 · 职责分离）
-- ----------------------------------------------------------------------------
-- 背景：dp_creative_menu.sql 把整个 1767 段授予了「任何已拥有内容任务菜单的角色」，
--       也就是 content_admin / content_member。结果是同一个账号既是品牌部又是设计部：
--       设计账号能改品牌事实、能改品牌要求、能自己点「品牌方确认」（内测真机实测过）。
--
-- 本脚本分三段，**必须按 A → B → 核对 → C 的顺序**：
--   A) 建 creative_designer 角色并授予 1767 段（安全，幂等）
--   B) 【迁移】把"当前通过内容角色拿到视觉工厂访问权"的用户补挂 creative_designer
--      —— 这样执行 C 收回时，没人会突然看不到菜单
--   C) 【收回，默认注释掉】从内容角色摘掉 1767 段
--
-- ⚠️ A 与 B 可以先跑、随时跑；**C 是有感知的变更**，它会改变现有账号的可见范围，
--    请确认 B 的结果（下面第四节有核对 SQL）之后再手工执行。
--
-- 幂等：INSERT IGNORE + 条件 DELETE，可重复执行。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- A) 角色 + 授予 1767 段（页面与按钮全套）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_role
(role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
 status, del_flag, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
(1767100000000000001, '视觉设计师', 'creative_designer', 40, '1', 1, 1, '0', '0',
 1761000000000000103, 1761100000000000001, sysdate(), NULL, NULL,
 'AI 视觉工厂全部权限（平面设计部）。刻意不含内容域的 Brief 写入与确认——那是品牌方的动作');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1767100000000000001, m.menu_id
  FROM sys_menu m
 WHERE m.menu_id BETWEEN 1767000000000000001 AND 1767000000000001699;

-- ---------------------------------------------------------------------------
-- B) 迁移：把现在能访问视觉工厂的用户都补挂 creative_designer
--    （判定口径 = 该用户经由任一其它角色持有 1767 段菜单）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT DISTINCT ur.user_id, 1767100000000000001
  FROM sys_user_role ur
  JOIN sys_role_menu rm ON rm.role_id = ur.role_id
 WHERE rm.menu_id BETWEEN 1767000000000000001 AND 1767000000000001699
   AND ur.role_id <> 1767100000000000001;

-- ---------------------------------------------------------------------------
-- C) 【收回】从内容角色摘掉 1767 段。
--    确认第四节核对结果无误后，手工去掉下面的注释再执行。
-- ---------------------------------------------------------------------------
-- DELETE rm FROM sys_role_menu rm
--  WHERE rm.menu_id BETWEEN 1767000000000000001 AND 1767000000000001699
--    AND rm.role_id IN (1765100000000000001, 1765100000000000002);

-- ---------------------------------------------------------------------------
-- 四、核对
-- ---------------------------------------------------------------------------
-- 4.1 角色已建、拿到多少菜单（期望：1 行，menus ≈ 33）
SELECT r.role_key, r.role_name,
       (SELECT COUNT(*) FROM sys_role_menu rm WHERE rm.role_id = r.role_id) AS menus
  FROM sys_role r WHERE r.role_key = 'creative_designer';

-- 4.2 迁移后各角色的 1767 段数量（B 跑完：content_* 仍是 33，creative_designer 也是 33）
SELECT r.role_key,
       SUM(rm.menu_id BETWEEN 1767000000000000001 AND 1767000000000001699) AS creative_menus
  FROM sys_role_menu rm JOIN sys_role r ON r.role_id = rm.role_id
 GROUP BY r.role_key ORDER BY r.role_key;

-- 4.3 【执行 C 之前必看】哪些用户会因为 C 而失去视觉工厂访问权
--     （口径：有 1767 段、但**不是**通过 creative_designer 拿到的用户 → 期望为空）
SELECT u.user_name,
       GROUP_CONCAT(DISTINCT other.role_key) AS via_roles
  FROM sys_user_role ur
  JOIN sys_user u ON u.user_id = ur.user_id
  JOIN sys_role other ON other.role_id = ur.role_id AND other.role_id <> 1767100000000000001
  JOIN sys_role_menu rm ON rm.role_id = ur.role_id
 WHERE rm.menu_id BETWEEN 1767000000000000001 AND 1767000000000001699
   AND NOT EXISTS (SELECT 1 FROM sys_user_role x
                    WHERE x.user_id = ur.user_id AND x.role_id = 1767100000000000001)
 GROUP BY u.user_name;

-- 4.4 收回后复核（执行 C 之后跑）：期望只剩 creative_designer
-- SELECT r.role_key, COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.role_id = rm.role_id
--  WHERE rm.menu_id BETWEEN 1767000000000000001 AND 1767000000000001699
--  GROUP BY r.role_key;

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------
-- 回滚 C：把 1767 段重新授给内容角色
-- INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
-- SELECT r.role_id, m.menu_id FROM sys_role r, sys_menu m
--  WHERE r.role_key IN ('content_admin','content_member')
--    AND m.menu_id BETWEEN 1767000000000000001 AND 1767000000000001699;
-- 回滚 B + A：删角色（先删关联，再删角色）
-- DELETE FROM sys_user_role WHERE role_id = 1767100000000000001;
-- DELETE FROM sys_role_menu WHERE role_id = 1767100000000000001;
-- DELETE FROM sys_role      WHERE role_id = 1767100000000000001;
