-- =====================================================================
-- R53：角色菜单绑定补齐祖先（通用修复）
-- =====================================================================
--
-- 现象：给人员授予「内容生产管理员 + 内容生产人员」后，点「内容生产协同」「AI视觉工厂」报
--       「没有访问权限，请联系管理员授权」。
-- 排查结论分两层：
--   ① 代码层（主因）：改**用户角色**那条路没有让在线会话失效——登录时权限集固化在会话里，
--      菜单路由却是每次实时查库，于是"菜单看得到、点进去 403"。修在
--      SysUserServiceImpl#insertUserRole（发 OnlineUserCleanEvent）。
--   ② 配置层（本次本文件修）：**角色绑了子菜单却没绑祖先**。菜单路由是从 parent_id=0 递归拼的，
--      父级不在集合里整棵子树会被丢掉：只授「内容生产人员」的人完全看不到「内容生产协同」。
--      另外还有 5 个角色有同类孤儿（talent_viewer / 4 个 HR 角色）。
--
-- 做法：一条**递归 CTE** 把所有角色的"已绑菜单 + 其全部祖先"补进 sys_role_menu。
--       sys_role_menu 主键是 (role_id, menu_id)，所以 INSERT IGNORE 天然幂等，
--       重复执行不会产生重复行，也不会多授任何"不是祖先"的菜单。
--
-- 影响面核对（执行前可先跑下面第 2 步的"执行前"查询）：
--   只增加"祖先菜单"这一类绑定；F 型按钮权限一个都不动，也不改任何角色的可见页面。
--
-- 回滚：本迁移只做补齐，语义上不该回滚（缺祖先本身就是错的）。
--       若确要回滚，只需删掉执行前那份查询列出来的 (role_id, menu_id) 组合。
-- =====================================================================

-- 1) 执行前：列出缺口（人工看一眼是哪些角色/菜单）
WITH RECURSIVE closing AS (
    SELECT rm.role_id AS role_id, m.menu_id AS menu_id, m.parent_id AS parent_id
      FROM sys_role_menu rm
      JOIN sys_menu m ON m.menu_id = rm.menu_id
    UNION ALL
    SELECT c.role_id, p.menu_id, p.parent_id
      FROM closing c
      JOIN sys_menu p ON p.menu_id = c.parent_id
     WHERE c.parent_id IS NOT NULL AND c.parent_id <> 0
)
SELECT r.role_key, t.menu_id AS 缺失祖先, m.menu_name
  FROM closing t
  JOIN sys_role r ON r.role_id = t.role_id
  JOIN sys_menu m ON m.menu_id = t.menu_id
 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x
                    WHERE x.role_id = t.role_id AND x.menu_id = t.menu_id)
 ORDER BY r.role_key, m.menu_name;

-- 2) 补齐：把"已绑菜单 + 其全部祖先"整体 INSERT IGNORE（已绑的自动跳过）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
WITH RECURSIVE closing AS (
    SELECT rm.role_id AS role_id, m.menu_id AS menu_id, m.parent_id AS parent_id
      FROM sys_role_menu rm
      JOIN sys_menu m ON m.menu_id = rm.menu_id
    UNION ALL
    SELECT c.role_id, p.menu_id, p.parent_id
      FROM closing c
      JOIN sys_menu p ON p.menu_id = c.parent_id
     WHERE c.parent_id IS NOT NULL AND c.parent_id <> 0
)
SELECT DISTINCT role_id, menu_id FROM closing;

-- 3) 执行后核对：应为 0 行
WITH RECURSIVE closing AS (
    SELECT rm.role_id AS role_id, m.menu_id AS menu_id, m.parent_id AS parent_id
      FROM sys_role_menu rm
      JOIN sys_menu m ON m.menu_id = rm.menu_id
    UNION ALL
    SELECT c.role_id, p.menu_id, p.parent_id
      FROM closing c
      JOIN sys_menu p ON p.menu_id = c.parent_id
     WHERE c.parent_id IS NOT NULL AND c.parent_id <> 0
)
SELECT COUNT(*) AS 仍缺祖先的行数
  FROM closing t
 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x
                    WHERE x.role_id = t.role_id AND x.menu_id = t.menu_id);

-- 4) 两个内容角色必须有父级「业务应用」（都应为 1）
SELECT r.role_key, COUNT(1) AS 有业务应用
  FROM sys_role_menu rm JOIN sys_role r ON r.role_id = rm.role_id
 WHERE r.role_key IN ('content_admin','content_member') AND rm.menu_id = 1764000000000000003
 GROUP BY r.role_key;
