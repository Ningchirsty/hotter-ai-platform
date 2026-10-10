-- ------------------------------------------------------------------
-- 岗位工作台（主文档线增量 1b）——岗位包管理的权限点 + 角色授权
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）、
--       aig_role_workspace.sql（场景/岗位六张表）
--
-- 【为什么本脚本只种权限行、不种页面菜单行】
--   岗位包管理台（前端页面）在同一个增量的后半段才出现。先种页面行会得到一个
--   "点进去是空白"的入口——那比"暂时没有入口"更糟（用户会以为坏了）。
--   页面行与前端页面一起再出一份脚本补上。
--   而权限行必须现在种：`@SaCheckPermission` 的权限串只有在 sys_menu.perms 里存在
--   才有意义，少一个就是**该接口对所有人 403**（含管理员），编译期与单测都发现不了
--   （守卫见 AigPermissionSeedCoverageTest）。
--
-- 【授权范围：只给 AI 管理员；但把"发布"与"停用"分成两个权限点】
--   本仓当前只有 aig_admin / aig_security / aig_viewer 三个角色，默认只授 aig_admin。
--   分开的理由不是"现在给不同的人"，而是让运维**将来可以**把停用权给值班的人，
--   而不必连发布权一起给：发布是上架，停用是叫停；把叫停绑在发布上，
--   会在"发布的人休假了"时没人能撤下问题版本。
--
-- 幂等：insert ... where not exists（menu_id 与 perms 双条件）；授权用 insert ignore。
-- ID 段：1768500000000000001..（1768 段此前用到 8400 段，8500 未占用）
-- ------------------------------------------------------------------

-- ----------------------------
-- 一、权限行（menu_type='F'）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768500000000000001, '岗位包清单', 1763000000000000001, 140, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:role:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '岗位包：有哪些岗位、各自有哪些版本、当前发布状态'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768500000000000001)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:role:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768500000000000002, '岗位包详情', 1763000000000000001, 141, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:role:query', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '岗位包：某版本的卡片清单、清单哈希与校验结论（只读）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768500000000000002)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:role:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768500000000000003, '编辑岗位包草稿', 1763000000000000001, 142, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:role:edit', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '岗位包：新建岗位/版本、保存 DRAFT 版本的清单与卡片（已发布版本不可改）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768500000000000003)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:role:edit');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768500000000000004, '岗位包预检', 1763000000000000001, 143, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:role:validate', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '岗位包：静态预检（引用/分类/routeKey 白名单/数据等级只能收紧；只读不落库）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768500000000000004)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:role:validate');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768500000000000005, '发布岗位包', 1763000000000000001, 144, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:role:publish', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '岗位包：DRAFT→TESTING→PUBLISHED（离开 DRAFT 必须先校验通过；边表由代码写死）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768500000000000005)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:role:publish');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768500000000000006, '停用岗位包版本', 1763000000000000001, 145, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:role:disable', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '岗位包：→DISABLED（可撤销；与发布分开授权，免得"发布的人休假了"就没人能叫停）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768500000000000006)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:role:disable');

-- ----------------------------
-- 二、角色授权（只给 AI 管理员）
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu
 where perms in ('aig:role:list', 'aig:role:query', 'aig:role:edit',
                 'aig:role:validate', 'aig:role:publish', 'aig:role:disable');

-- 父菜单也必须授权，否则菜单树里看不到这一组（RuoYi 按「用户已授权的菜单」建树）
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1763000000000000001;

-- 核对：六个权限行都在，且 aig_admin 都有
select m.perms, count(rm.role_id) as granted_roles
  from sys_menu m
  left join sys_role_menu rm on rm.menu_id = m.menu_id
 where m.perms like 'aig:role:%'
 group by m.perms
 order by m.perms;
