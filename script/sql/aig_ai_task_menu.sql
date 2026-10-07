-- ------------------------------------------------------------------
-- AI 任务（设计 §9 统一任务编排）：菜单与权限增量
--
-- 背景：aig_ai_gov_menu.sql 是**安装脚本**（直接 insert、不幂等）。
--   目标库若已经跑过它，本轮新增的「AI任务」菜单不会自动出现——
--   功能做出来了却在菜单里找不到入口，而报错只有一句 404。
--   因此这里单独给一份可重复执行的增量：全部用 INSERT ... SELECT ... WHERE NOT EXISTS。
--
-- 权限切分：
--   aig:task:list     任务列表
--   aig:task:query    任务详情（任务+输入快照+事件流+候选结果）
--   aig:task:operate  人工取消 / 重新入队 / 手动触发调度扫描
--   与「只看」分开的理由：这些动作会改变任务状态或触发重试与计费，
--   而查看任务只是读。合并的话，一个只该看进度的人就能替所有人取消在跑的任务。
--
-- 幂等：可安全重复执行。
-- ------------------------------------------------------------------

-- 1) 菜单：页面 + 两个功能权限
INSERT INTO sys_menu
SELECT 1763000000000000105, 'AI任务', 1763000000000000001, 5, 'task', 'aigov/task/index', '', 'N', 'Y', 'C', '0', '0',
       'aig:task:list', 'job', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null,
       '统一任务编排：状态机、输入快照、事件流、候选结果、失败原因与重试'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1763000000000000105);

INSERT INTO sys_menu
SELECT 1763000000000001401, '任务详情', 1763000000000000105, 1, '', '', '', 'N', 'Y', 'F', '0', '0',
       'aig:task:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, ''
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1763000000000001401);

INSERT INTO sys_menu
SELECT 1763000000000001402, '任务操作', 1763000000000000105, 2, '', '', '', 'N', 'Y', 'F', '0', '0',
       'aig:task:operate', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, ''
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1763000000000001402);

INSERT INTO sys_menu
SELECT 1763000000000001403, '选定交付物', 1763000000000000105, 3, '', '', '', 'N', 'Y', 'F', '0', '0',
       'aig:task:select', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null,
       '人工选定候选资产（自动流程只筛除、不放行，选定必须人工）'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1763000000000001403);

-- 2) 角色-菜单：管理员全量；安全授权人与查看者只读（不给 operate，也不给 select——
--    「决定交付哪一张」是业务决定，不该由安全/查看角色代做）
INSERT INTO sys_role_menu
SELECT 1763100000000000001, 1763000000000000105
 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1763100000000000001 AND menu_id = 1763000000000000105);
INSERT INTO sys_role_menu
SELECT 1763100000000000001, 1763000000000001401
 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1763100000000000001 AND menu_id = 1763000000000001401);
INSERT INTO sys_role_menu
SELECT 1763100000000000001, 1763000000000001402
 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1763100000000000001 AND menu_id = 1763000000000001402);
INSERT INTO sys_role_menu
SELECT 1763100000000000001, 1763000000000001403
 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1763100000000000001 AND menu_id = 1763000000000001403);

INSERT INTO sys_role_menu
SELECT 1763100000000000002, 1763000000000000105
 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1763100000000000002 AND menu_id = 1763000000000000105);
INSERT INTO sys_role_menu
SELECT 1763100000000000002, 1763000000000001401
 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1763100000000000002 AND menu_id = 1763000000000001401);

INSERT INTO sys_role_menu
SELECT 1763100000000000003, 1763000000000000105
 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1763100000000000003 AND menu_id = 1763000000000000105);
INSERT INTO sys_role_menu
SELECT 1763100000000000003, 1763000000000001401
 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1763100000000000003 AND menu_id = 1763000000000001401);

-- 3) 核对
SELECT menu_id, menu_name, parent_id, perms
  FROM sys_menu
 WHERE menu_id IN (1763000000000000105, 1763000000000001401, 1763000000000001402, 1763000000000001403)
 ORDER BY menu_id;

SELECT role_id, COUNT(*) AS task_menus
  FROM sys_role_menu
 WHERE menu_id IN (1763000000000000105, 1763000000000001401, 1763000000000001402, 1763000000000001403)
 GROUP BY role_id
 ORDER BY role_id;

SELECT 'AIG_AI_TASK_MENU_DONE' AS marker;
