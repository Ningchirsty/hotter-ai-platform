-- ------------------------------------------------------------------
-- AI 治理：调用人均配额（C3）——权限点 + 治理台页面菜单 + 角色授权
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）、
--       aig_user_quota.sql（配额表，本功能的数据表）
--
-- 【权限点与页面为什么分成 F 与 C 两种行】
--   aig:quota:list（读清单/用量）、aig:quota:edit（改额度：新增/修改/删除）
--   是**接口鉴权**用的（Sa-Token 按 perms 判定）；
--   页面菜单（'C'）只是让治理台能看到这一页，它的 perms 用列表权限——
--   沿用 RuoYi 约定（见 aig_agent_registry_pages.sql 的同一说明）。
--
-- ⚠️ 【页面行的守卫只按 menu_id，不能带 perms 条件】
--   aig:quota:list 故意与权限行重名，若照抄权限行脚本的「menu_id 不存在且 perms 不存在」守卫，
--   页面菜单将**永远插不进去**。
--
-- ⚠️ 【父菜单也必须授权】
--   只给页面授权、不给父菜单（AI平台治理 1763000000000000001）授权时，
--   菜单树里根本看不到这一页（RuoYi 按「用户已授权的菜单」建树）。本脚本显式补一次父菜单授权。
--
-- 授权范围：
--   · aig_admin：全部（读 + 改额度）
--   · aig_security / aig_viewer：只读（能看谁有多少额度、当前用了多少，但不能改）
--
-- 幂等：权限行 insert ... where not exists（menu_id 与 perms 双条件）；
--   页面行与授权用 insert ... where not exists / insert ignore，可安全重复执行。
-- ID 段：1768100000000000001..（1768 段已被 WP3 的权限行与页面行占用到 045）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 一、权限行（menu_type='F'）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768100000000000001, '调用配额清单', 1763000000000000001, 120, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:quota:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'C3：看谁有多少调用额度、当前用了多少（单位是「调用次数」，不是钱）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768100000000000001)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:quota:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768100000000000002, '调用配额编辑', 1763000000000000001, 121, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:quota:edit', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'C3：新增/修改/删除某个人的额度（删除=回到「不限」，同样是放宽额度）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768100000000000002)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:quota:edit');

-- ----------------------------
-- 二、治理台页面菜单（menu_type='C'；守卫只按 menu_id）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768100000000000011, '调用配额', 1763000000000000001, 112, 'quota', 'aigov/quota/index',
       'N', 'Y', 'C', '0', '0', 'aig:quota:list', 'peoples',
       1761000000000000103, 1761100000000000001, sysdate(),
       'C3：按人配置「每日/每月调用次数」上限，并可查当前用量（无配额行=不限）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768100000000000011);

-- ----------------------------
-- 三、角色授权
-- ----------------------------
-- 读：三个角色都给（知道自己还剩多少额度，也需要能看一眼）
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, menu_id from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002 union all
    select 1763100000000000003) r
  join sys_menu m on m.perms in ('aig:quota:list') ;

-- 写：只给 AI 管理员
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu where perms = 'aig:quota:edit';

-- 页面菜单：三个角色都给（读语义）
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, 1768100000000000011 from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002 union all
    select 1763100000000000003) r;

-- ⚠️ 父菜单必须授权，否则菜单树里看不到这一页
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, 1763000000000000001 from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002 union all
    select 1763100000000000003) r;
