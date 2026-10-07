-- ----------------------------------------------------------------------------
-- AI 治理层 · WP3 治理台页面菜单（5 个页面）
--
-- 目标库：平台库（依赖 ry_vue.sql / aig_ai_gov_menu.sql / aig_agent_registry_menu.sql）
--
-- 【为什么单独一份、而不是并进 aig_agent_registry_menu.sql】
-- 那份脚本只种**权限行**（menu_type='F'）——因为当时页面还不存在，种页面菜单点进去就是 404。
-- 现在五个页面（agent / binding / skill / package / evaluation）有了，页面菜单才补在这里。
-- 两份脚本互不依赖，都可重复执行。
--
-- 【菜单的 perms 用「列表权限」】沿用既有约定（如 AI能力目录 用 aig:capability:list）：
-- 看得到页面 ⇒ 至少能查列表。按钮级权限（发布推进/扫描/安装/评测/复核）是独立的权限行，
-- 已在 aig_agent_registry_menu.sql 里种好并按角色授权。
--
-- ⚠️ 【必须同时授权父菜单】RuoYi 按「用户已授权的菜单」建树：只授权子页面、不给父菜单
--    （AI平台治理 1763000000000000001）授权时，子节点没有挂载点，**菜单树里根本看不到这个页面**。
--    本脚本因此显式补一次父菜单授权（幂等；既有脚本本已授权，这里只是不依赖它）。
--
-- ID 段：页面菜单 1768*（权限行同段，后缀不冲突：权限行用 001-035，页面菜单用 041-045）
-- 幂等：insert ... select ... where not exists（**只按 menu_id 判断**）；授权用 insert ignore。
--
-- ⚠️ 【这里不能照抄 aig_agent_registry_menu.sql 的 perms 守卫】那份脚本的守卫是
--    「menu_id 不存在 **且** 该 perms 不存在」，用来避免同权限种两遍。页面的 perms 故意与
--    列表权限行重名（沿用 RuoYi 约定：页面用列表权限），带上 perms 守卫会让页面菜单**永远插不进去**。
--
-- 【与既有授权脚本的先后】aig_agent_registry_menu.sql 的授权是按 perms 反查 sys_menu，
--    本脚本在其后执行时那批授权已跑完、不会包含新页面；所以本脚本自己再授权一次（幂等）。
--    反过来若日后重放 menu 脚本，它会顺带把页面菜单也按 perms 授上——两条路径都不冲突。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 一、五个页面菜单（menu_type='C'）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000041, 'Agent注册中心', 1763000000000000001, 106, 'agent', 'aigov/agent/index',
       'N', 'Y', 'C', '0', '0', 'aig:agent:list', 'user',
       1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §5：Agent 清单/版本与发布推进（唯一写入口；门槛证据由服务层核对）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000041);

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000042, '版本绑定', 1763000000000000001, 107, 'binding', 'aigov/binding/index',
       'N', 'Y', 'C', '0', '0', 'aig:agent:list', 'link',
       1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §10.2：绑定决定受限通道发布后谁能看见（受限通道发 CANDIDATE/STABLE 必须有启用中绑定）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000042);

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000043, 'Skill注册', 1763000000000000001, 108, 'skill', 'aigov/skill/index',
       'N', 'Y', 'C', '0', '0', 'aig:skill:list', 'star',
       1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §5：Skill 清单与版本（第三方 Package 带入的版本能看到来源 Package 版本）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000043);

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000044, 'Package管理', 1763000000000000001, 109, 'package', 'aigov/package/index',
       'N', 'Y', 'C', '0', '0', 'aig:package:list', 'box',
       1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §6：上传（携包体核对校验和）→ 扫描（五类拒绝规则）→ 安装（按声明建 DRAFT 版本）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000044);

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000045, '黄金用例与评测', 1763000000000000001, 110, 'evaluation',
       'aigov/evaluation/index', 'N', 'Y', 'C', '0', '0', 'aig:evaluation:list', 'documentation',
       1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §13.2：用例定义（判据在定义期校验）→ 跑整组 → 人工复核 → 证据（发布门槛 GOLDEN_CASE 用）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000045);

-- ----------------------------
-- 二、授权：三个角色都给（页面权限 = 列表权限，读语义）
--   按钮级写权限（推进/扫描/上传/安装/跑/复核）不在这里给，见 aig_agent_registry_menu.sql
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu
 where menu_id in (1768000000000000041, 1768000000000000042, 1768000000000000043,
                   1768000000000000044, 1768000000000000045);

insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000002, menu_id from sys_menu
 where menu_id in (1768000000000000041, 1768000000000000042, 1768000000000000043,
                   1768000000000000044, 1768000000000000045);

insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000003, menu_id from sys_menu
 where menu_id in (1768000000000000041, 1768000000000000042, 1768000000000000043,
                   1768000000000000044, 1768000000000000045);

-- ⚠️ 父菜单（AI平台治理）也必须对这三个角色授权，否则子页面在菜单树里挂不上（看不见）
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, 1763000000000000001 from (select 1763100000000000001 as role_id
      union all select 1763100000000000002
      union all select 1763100000000000003) t;
