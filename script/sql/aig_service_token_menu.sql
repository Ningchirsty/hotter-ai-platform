-- ------------------------------------------------------------------
-- AI 治理：服务令牌（机器身份）——权限点 + 治理台页面 + 角色授权
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）、
--       aig_service_token.sql（aig_service_token 表，本功能的数据表）
--
-- 【F 行与 C 行是两种东西，别照抄守卫】
--   F 行（menu_type='F'）= 接口鉴权用的权限点；C 行（menu_type='C'）= 治理台页面，
--   让这一页在菜单树里出现。C 行的 perms 沿用列表权限（RuoYi 约定）。
--   ⚠️ C 行的守卫**只能按 menu_id**：aig:service-token:list 这个串故意与 F 行重名，
--      若照抄 F 行"menu_id 不存在且 perms 不存在"的双条件，页面行将永远插不进去。
--   ⚠️ 父菜单（1763000000000000001，生产里叫「平台治理」）必须授权，否则菜单树里看不到这一页。
--      它本来就已授权给这三个 aig_* 角色（见 aig_ai_gov_menu.sql），本文件不重复补。
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
-- 幂等：F 行 insert ... where not exists（menu_id 与 perms 双条件）；C 行与授权 insert ignore。
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
-- 二、治理台页面菜单（menu_type='C'；守卫**只按 menu_id**，见文件头说明）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768300000000000011, '服务令牌', 1763000000000000001, 113, 'service-token', 'aigov/servicetoken/index',
       'N', 'Y', 'C', '0', '0', 'aig:service-token:list', 'lock',
       1761000000000000103, 1761100000000000001, sysdate(),
       '签发/停用机器身份令牌：明文只在签发时显示一次；停用会让该调用方的所有调用立刻 401'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768300000000000011);

-- ----------------------------
-- 三、角色授权
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

-- 页面菜单：三个角色都给（读语义）
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, 1768300000000000011 from (
    select 1763100000000000001 as role_id union all
    select 1763100000000000002 union all
    select 1763100000000000003) r;

-- ⚠️ 父菜单（1763000000000000001，生产里的名字是「平台治理」，parent = 1764000000000000005 管理中心）
--    本来就已授权给这三个角色（见 aig_ai_gov_menu.sql），所以菜单树能看到这一页；本文件不重复补。

-- ----------------------------
-- 四、执行后核对（人工看一眼）
-- ----------------------------
-- 期望：4 行（3 个 F + 1 个 C），且每行只授权给 2~3 个 aig_* 角色
select m.menu_id, m.menu_name, m.perms, m.menu_type,
       (select group_concat(rm.role_id order by rm.role_id)
          from sys_role_menu rm where rm.menu_id = m.menu_id) as roles
  from sys_menu m
 where m.perms like 'aig:service-token:%'
 order by m.menu_id;
