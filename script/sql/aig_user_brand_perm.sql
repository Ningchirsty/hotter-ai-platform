-- ----------------------------------------------------------------------------
-- 用户↔品牌归属的两个权限（aig:user-brand:list / aig:user-brand:edit）
--
-- 为什么编辑要单独一个权限：
--   它直接决定「按品牌定向的岗位」对谁生效——登记某人属于某品牌，等于让他看到该品牌的岗位。
--   与「查看谁属于哪些品牌」不是同一件事，口径同 aig:sandbox:record 之于 aig:sandbox:list。
--   注意：本表只是**可见性判定的输入之一**，不是授权体系（不提高任何其它权限）。
--
-- 口径：两个权限都只授予 aig_admin（AI数智化管理员）。
--   aig_security / aig_viewer 的可见范围没有变化。
--
-- 幂等：菜单行按 menu_id 与 perms 双重反查（where not exists），
--   授权用 insert ignore（(role_id, menu_id) 是复合主键）。可安全重复执行；
--   本文件不含任何 update/delete/drop。
--
-- 前置：治理台父菜单 1763000000000000001 与角色 aig_admin 由 aig_ai_gov_menu.sql 建出。
-- 关联：数据源默认关闭（aigov.user-brand.enabled=false）；打开前先执行 aig_user_brand.sql。
-- ----------------------------------------------------------------------------

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000039, '用户品牌归属查看', 1763000000000000001, 113, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:user-brand:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '④：查看谁属于哪些品牌（岗位按品牌可见性判定的输入之一）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000039)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:user-brand:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000040, '用户品牌归属编辑', 1763000000000000001, 114, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:user-brand:edit', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '④：登记/停用某人的品牌归属——它改变「按品牌定向的岗位对谁生效」，故与查看分开授权'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000040)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:user-brand:edit');

-- 只授给 AI数智化管理员（aig_admin）：insert ignore，重复执行安全
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu
 where perms in ('aig:user-brand:list', 'aig:user-brand:edit');

-- 核对：权限行在不在、各授予了几个角色（期望 2 行、各 1 个角色）
select m.perms, m.menu_name, m.menu_id,
       (select count(*) from sys_role_menu rm where rm.menu_id = m.menu_id) as granted_roles
  from sys_menu m
 where m.perms in ('aig:user-brand:list', 'aig:user-brand:edit')
 order by m.perms;

select 'AIG_USER_BRAND_PERM_DONE' as marker;
