-- ----------------------------------------------------------------------------
-- AI 治理：沙箱运行证据的两个权限（aig:sandbox:record / aig:sandbox:list）
--
-- 为什么登记要单独一个权限：
--   它直接决定版本能不能从 VALIDATED 走到 SANDBOX_TESTED（发布门槛 SANDBOX_RUN）。
--   与「看清单/看证据」不是同一件事——口径同 aig:evaluation:manual（人工评测录入）。
--   注意：这是**人工登记**，登记的人拿的是宿主侧执行器输出的 result.json 原文；
--   权限只决定"谁能记"，不决定"记的东西可不可信"——后者由服务端的字段校验与
--   原文 SHA-256 留存来保证（见 AigSandboxRunServiceImpl）。
--
-- 口径：两个权限都只授予 aig_admin（AI数智化管理员）。
--   aig_security / aig_viewer 本来就没有发布推进权，本次对它们的可见范围没有变化。
--
-- 幂等：菜单行按 menu_id 与 perms 双重反查（where not exists），
--   授权用 insert ignore（(role_id, menu_id) 是复合主键）。可安全重复执行；
--   本文件不含任何 update/delete/drop。
--
-- 注意：仓库有一条构建期检查（AigPermissionSeedCoverageTest）要求每个 PERM_* 常量
--   都出现在文件名含 menu/perm 的脚本里——本文件就是为它而存在的那个"perm 脚本"。
-- 前置：治理台父菜单 1763000000000000001 与角色 aig_admin 由 aig_ai_gov_menu.sql 建出。
-- ----------------------------------------------------------------------------

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000037, '沙箱运行登记', 1763000000000000001, 111, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:sandbox:record', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'ADR-015：把宿主侧沙箱作业的 result.json 原样登记为 SANDBOX_RUN 门槛的证据'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000037)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:sandbox:record');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000038, '沙箱运行证据查看', 1763000000000000001, 112, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:sandbox:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'ADR-015：查看某个版本是否已有沙箱运行证据、以及不满足的原因'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000038)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:sandbox:list');

-- 只授给 AI数智化管理员（aig_admin）：insert ignore，重复执行安全
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu
 where perms in ('aig:sandbox:record', 'aig:sandbox:list');

-- 核对：权限行在不在、各授予了几个角色（期望 2 行、各 1 个角色）
select m.perms, m.menu_name, m.menu_id,
       (select count(*) from sys_role_menu rm where rm.menu_id = m.menu_id) as granted_roles
  from sys_menu m
 where m.perms in ('aig:sandbox:record', 'aig:sandbox:list')
 order by m.perms;

-- 核对：admin 角色名（便于人工确认 1763100000000000001 到底是谁）
select role_id, role_key, role_name from sys_role
 where role_id = 1763100000000000001;

select 'AIG_SANDBOX_RUN_PERM_DONE' as marker;
