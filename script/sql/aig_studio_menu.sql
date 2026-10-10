-- ------------------------------------------------------------------
-- 岗位 Agent Studio（训练台）——权限点 + 角色授权
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）、
--       aig_studio.sql（草稿/修订/测试证据三张表）
--
-- 【为什么本脚本只种权限行、**不**种页面菜单行】
--   页面组件（`aigov/studio/index`）要到前端增量（S4）才存在。先种菜单行会得到
--   一个"点进去是空白页"的入口——那是一种比"暂时没有入口"更糟的缺陷（用户会以为坏了）。
--   因此 S4 会与前端页面一起再出一份 `aig_studio_pages.sql` 补上页面行与其父菜单授权。
--   而权限行必须现在种：{@code @SaCheckPermission} 的权限串只有在 sys_menu.perms 里存在
--   才有意义，少一个就是**该接口对所有人 403**（含管理员），且编译期/单测都发现不了
--   （守卫见 {@code AigPermissionSeedCoverageTest}）。
--
-- 【授权范围：只给 AI 管理员】
--   训练台面向"授权建设人员"，而本仓当前只有 aig_admin / aig_security / aig_viewer 三个角色。
--   治理审阅者（security/viewer）**不**默认获得草稿写权：能改草稿=能间接影响后续版本内容。
--   业务专家角色应由运维按需新建并单独授权本组权限，而不是默认放开。
--
-- 幂等：insert ... where not exists（menu_id 与 perms 双条件）；授权用 insert ignore。
-- ID 段：1768400000000000001..（1768 段此前已用到 8200，未占用 8400）
-- ------------------------------------------------------------------

-- ----------------------------
-- 一、权限行（menu_type='F'）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768400000000000001, '训练草稿清单', 1763000000000000001, 130, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:studio:draft:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '训练台：有哪些草稿、归谁、改到第几版、有没有未提交改动'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768400000000000001)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:studio:draft:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768400000000000002, '训练草稿详情', 1763000000000000001, 131, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:studio:draft:query', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '训练台：草稿内容与修订历史（只读）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768400000000000002)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:studio:draft:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768400000000000003, '新建训练草稿', 1763000000000000001, 132, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:studio:draft:create', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '训练台：新建草稿（同时产生第 1 个修订）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768400000000000003)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:studio:draft:create');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768400000000000004, '编辑训练草稿', 1763000000000000001, 133, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:studio:draft:edit', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '训练台：保存（带 CAS 版本）/回滚/归档草稿'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768400000000000004)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:studio:draft:edit');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768400000000000005, '训练草稿预检', 1763000000000000001, 134, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:studio:draft:validate', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '训练台：静态预检（只读校验，不产生版本、不发布）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768400000000000005)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:studio:draft:validate');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768400000000000006, '提交训练草稿', 1763000000000000001, 135, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:studio:draft:submit', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '训练台：把草稿固化成 DRAFT Agent 版本（此后进入既有发布门槛，训练台不推进发布状态）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768400000000000006)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:studio:draft:submit');

-- ----------------------------
-- 二、角色授权（只给 AI 管理员）
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu
 where perms in ('aig:studio:draft:list', 'aig:studio:draft:query', 'aig:studio:draft:create',
                 'aig:studio:draft:edit', 'aig:studio:draft:validate', 'aig:studio:draft:submit');

-- ⚠️ 父菜单也必须授权，否则菜单树里看不到这一组（RuoYi 按「用户已授权的菜单」建树）
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1763000000000000001;

-- 核对：六个权限行都在，且 aig_admin 都有
select m.perms, count(rm.role_id) as granted_roles
  from sys_menu m
  left join sys_role_menu rm on rm.menu_id = m.menu_id
 where m.perms like 'aig:studio:%'
 group by m.perms
 order by m.perms;
