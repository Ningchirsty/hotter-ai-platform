-- ------------------------------------------------------------------
-- 运营指标（Phase E）—— 权限点 + 角色授权
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）
--
-- 【只种一个权限行】运营指标是管理员视图；员工不需要它。
-- 权限行必须现在种：缺了就是该接口对所有人 403（含管理员），守卫见 AigPermissionSeedCoverageTest。
--
-- 幂等：insert ... where not exists（menu_id 与 perms 双条件）；授权用 insert ignore。
-- ID 段：1769300000000000001..（未占用）。
-- ------------------------------------------------------------------

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769300000000000001, '运营指标', 1763000000000000001, 180, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:ops:metrics', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '运营指标：完成任务数、结果采纳率（累计成果=经确认结果）、首次通过率、返工率、策略阻断'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769300000000000001)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:ops:metrics');

insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu where perms = 'aig:ops:metrics';

insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1763000000000000001;

-- 核对
select m.perms, count(rm.role_id) as granted_roles
  from sys_menu m
  left join sys_role_menu rm on rm.menu_id = m.menu_id
 where m.perms = 'aig:ops:metrics'
 group by m.perms;
