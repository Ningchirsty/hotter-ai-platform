-- ------------------------------------------------------------------
-- AI 治理：服务令牌（机器身份）——权限点 + 角色授权
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）、
--       aig_service_token.sql（aig_service_token 表，本功能的数据表）
--
-- 【为什么只有 F 行，没有页面菜单（C 行）】
--   本阶段只交付接口（/aigov/service-token/*），前端页面还没做。
--   此时建 'C' 行会让治理台出现一个**点开是空白**的菜单项——
--   那是"看起来有功能、实际没有"，比暂时没有入口更糟。
--   等页面真做出来，再单独补 C 行（参照 aig_user_quota_menu.sql 的写法，
--   注意页面行守卫只能按 menu_id，不能带 perms 条件）。
--
-- 【本文件绝不能被扩展成"顺手给机器也授一个"】
--   aig:service-token:* 这三个权限是给**人**的。机器身份一旦拿到
--   aig:service-token:issue，就可以自我提权/自我续期。因此：
--     · 签发接口在服务端拒绝任何以 aig:service-token: 开头的 scope；
--     · 令牌管理接口在运行期拒绝一切带服务身份的请求（403）。
--   两条都在代码里，且有测试守着。往 sys_role_menu 里加机器角色不会绕过它们。
--
-- 授权范围（保守；如需收紧，删掉对应那一行即可）：
--   · aig:service-token:list   → aig_admin / aig_security / aig_viewer（治理信息，读）
--   · aig:service-token:issue  → aig_admin / aig_security（发凭据；安全需能在事故中轮换）
--   · aig:service-token:revoke → aig_admin / aig_security（叫停一把泄露的令牌必须快）
--
-- 幂等：权限行 insert ... where not exists（menu_id 与 perms 双条件）；授权 insert ignore。
-- ID 段：1768300000000000001..（17682 段已被 aig_call_approval_menu.sql 占到 011）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 一、权限行（menu_type='F'）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768300000000000001, '服务令牌清单', 1763000000000000001, 130, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:service-token:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '有哪些机器身份、各自能做什么、最近一次谁在用（不含明文、不含哈希）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768300000000000001)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:service-token:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768300000000000002, '签发服务令牌', 1763000000000000001, 131, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:service-token:issue', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '发凭据：明文只在签发那一刻返回一次，之后平台无法找回；机器身份不得拥有本权限'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768300000000000002)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:service-token:issue');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768300000000000003, '停用服务令牌', 1763000000000000001, 132, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:service-token:revoke', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '叫停一把令牌：该调用方的所有调用立刻 401（不物理删除，保留审计痕迹）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768300000000000003)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:service-token:revoke');

-- ----------------------------
-- 二、角色授权
-- ----------------------------
-- 读：三个角色都给（谁在用机器身份属于治理信息）
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, menu_id from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002 union all
    select 1763100000000000003) r
  join sys_menu m on m.perms = 'aig:service-token:list';

-- 签发：AI 管理员 + 安全（安全需能在"令牌疑似泄露"时立刻轮换）
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, menu_id from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002) r
  join sys_menu m on m.perms = 'aig:service-token:issue';

-- 停用：AI 管理员 + 安全
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, menu_id from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002) r
  join sys_menu m on m.perms = 'aig:service-token:revoke';

-- ⚠️ 父菜单（AI平台治理 1763000000000000001）本来已授权给这三个角色
--    （见 aig_ai_gov_menu.sql）。这里不重复补：本文件没有页面行，
--    不新增子菜单节点，F 行不参与菜单树渲染。

-- ----------------------------
-- 三、执行后核对（人工看一眼）
-- ----------------------------
-- 期望：3 行 perms，且每行只授权给 2~3 个 aig_* 角色
select m.menu_id, m.menu_name, m.perms, m.menu_type,
       (select group_concat(rm.role_id order by rm.role_id)
          from sys_role_menu rm where rm.menu_id = m.menu_id) as roles
  from sys_menu m
 where m.perms like 'aig:service-token:%'
 order by m.menu_id;
