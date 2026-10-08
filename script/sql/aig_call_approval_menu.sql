-- ------------------------------------------------------------------
-- AI 治理：调用授权审批（C3）——权限点 + 治理台页面菜单 + 角色授权
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）、
--       aig_call_approval.sql（审批表，本功能的数据表）
--
-- 【三个权限点，为什么这么分】
--   aig:approval:list    看清单：谁申请了什么、批没批、哪张授权还在有效期内
--   aig:approval:apply   提交/撤回**自己的**申请
--   aig:approval:approve 批准/驳回（＝替别人担这个责任）
--   申请与审批必须分开：申请是"我需要"，审批是"我同意放行"。
--
--   apply 给三个角色：谁都可能撞上「这个能力需要审批」，提交与撤回自己那张单子
--   不该需要治理权限（「看自己的」还有一个不要权限的 /my 接口）。
--   approve 只给 aig_admin 与 aig_security：服务层还会强制**申请人不得自审**（分离职责），
--   这里少给一个角色只是把风险面收窄，不是唯一约束。
--
-- 【页面行的守卫只按 menu_id，不能带 perms 条件】
--   aig:approval:list 故意与权限行重名，若照抄权限行脚本的「menu_id 不存在且 perms 不存在」
--   守卫，页面菜单将**永远插不进去**。
--
-- 【父菜单也必须授权】
--   只给页面授权、不给父菜单（AI平台治理 1763000000000000001）授权时，菜单树里根本看不到
--   这一页（RuoYi 按「用户已授权的菜单」建树）。本脚本显式补一次父菜单授权。
--
-- 幂等：权限行 insert ... where not exists（menu_id 与 perms 双条件）；
--   页面行与授权用 insert ... where not exists / insert ignore，可安全重复执行。
-- ID 段：1768200000000000xxx（**新开一段**：1768000… 是 WP3 的权限行/页面行，
--   1768100… 是 C3 配额那一批，互不重叠）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 一、权限行（menu_type='F'）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768200000000000001, '调用授权清单', 1763000000000000001, 130, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:approval:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'C3：看谁申请了什么、批没批、哪张授权还在有效期内'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768200000000000001)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:approval:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768200000000000002, '提交调用授权申请', 1763000000000000001, 131, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:approval:apply', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'C3：提交/撤回**自己的**申请（只看自己的另有不要权限的 /my 接口）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768200000000000002)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:approval:apply');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768200000000000003, '调用授权审批', 1763000000000000001, 132, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:approval:approve', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'C3：批准/驳回（申请人不得自审，服务层强制）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768200000000000003)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:approval:approve');

-- ----------------------------
-- 二、治理台页面菜单（menu_type='C'；守卫只按 menu_id）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768200000000000011, '调用授权', 1763000000000000001, 113, 'approval', 'aigov/approval/index',
       'N', 'Y', 'C', '0', '0', 'aig:approval:list', 'lock',
       1761000000000000103, 1761100000000000001, sysdate(),
       'C3：提交「能力×数据等级」的调用授权申请、审批、查看有效期（批准后在有效期内免再审）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768200000000000011);

-- ----------------------------
-- 三、角色授权
-- ----------------------------
-- 读清单：三个角色都给（申请要知道自己交的单子走到哪了）
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, menu_id from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002 union all
    select 1763100000000000003) r
  join sys_menu m on m.perms in ('aig:approval:list');

-- 提交/撤回自己的申请：三个角色都给
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, menu_id from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002 union all
    select 1763100000000000003) r
  join sys_menu m on m.perms in ('aig:approval:apply');

-- 审批：只给 AI 管理员与安全
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, menu_id from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002) r
  join sys_menu m on m.perms in ('aig:approval:approve');

-- 页面菜单：三个角色都给（页面本身是读语义，能申请就能进这一页）
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, 1768200000000000011 from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002 union all
    select 1763100000000000003) r;

-- ⚠️ 父菜单必须授权，否则菜单树里看不到这一页
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, 1763000000000000001 from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002 union all
    select 1763100000000000003) r;
