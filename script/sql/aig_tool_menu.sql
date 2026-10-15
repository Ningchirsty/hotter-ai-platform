-- ------------------------------------------------------------------
-- 工具注册（Phase D）—— 权限点 + 角色授权
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）、
--       aig_tool.sql（三张工具表）
--
-- 【为什么本脚本只种权限行】工具页面组件尚未交付；先种页面行会得到"点进去空白"的入口。
--   权限行必须现在种：{@code @SaCheckPermission} 的权限串只有在 sys_menu.perms 里存在才有意义，
--   少一个就是**该接口对所有人 403**（含管理员），编译期与单测都发现不了
--   （守卫见 {@code AigPermissionSeedCoverageTest}）。
--
-- 【授权范围：只给 AI 管理员】与 studio/knowledge 两个脚本同一口径。
--
-- 幂等：insert ... where not exists（menu_id 与 perms 双条件）；授权用 insert ignore。
-- ID 段：1769100000000000001..（1769 段已用于 knowledge 的 001..006，此处用 101.. 段，不撞）。
-- ------------------------------------------------------------------

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769100000000000001, '可绑定工具清单', 1763000000000000001, 160, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:tool:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '工具：版本、操作类型、是否已接入执行器（未接入显示「已登记 待接入」）、所需权限点'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769100000000000001)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:tool:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769100000000000002, '登记工具与版本', 1763000000000000001, 161, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:tool:manage', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '工具：新建定义与执行契约版本（改变 Agent 可申请的范围）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769100000000000002)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:tool:manage');

-- 角色授权（只给 AI 管理员）
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu
 where perms in ('aig:tool:list', 'aig:tool:manage');

-- ⚠️ 父菜单也必须授权，否则菜单树里看不到这一组（RuoYi 按「用户已授权的菜单」建树）
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1763000000000000001;

-- 核对
select m.perms, count(rm.role_id) as granted_roles
  from sys_menu m
  left join sys_role_menu rm on rm.menu_id = m.menu_id
 where m.perms like 'aig:tool:%'
 group by m.perms
 order by m.perms;
