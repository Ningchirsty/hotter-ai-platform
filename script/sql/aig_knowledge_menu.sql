-- ------------------------------------------------------------------
-- 知识中心（Phase C）—— 权限点 + 角色授权
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）、
--       aig_knowledge.sql（五张知识表）
--
-- 【为什么本脚本只种权限行】
--   知识中心的页面组件（集合/文档/版本/检索测试）尚未交付；先种页面行会得到一个
--   "点进去是空白页"的入口。权限行则必须现在种：{@code @SaCheckPermission} 的权限串
--   只有在 sys_menu.perms 里存在才有意义，少一个就是**该接口对所有人 403**（含管理员），
--   而编译期与单测都发现不了（守卫见 {@code AigPermissionSeedCoverageTest}）。
--
-- 【授权范围：只给 AI 管理员】
--   设计 §4.3 的「知识维护人」是业务角色，应由运维按需新建并单独授权本组权限，
--   而不是默认放开。这里与 studio/role 两个脚本同一口径：只授 aig_admin。
--
-- 幂等：insert ... where not exists（menu_id 与 perms 双条件）；授权用 insert ignore。
-- ID 段：1769000000000000001..；1769 段此前未占用。
-- ------------------------------------------------------------------

-- ----------------------------
-- 一、权限行（menu_type='F'）
-- ----------------------------

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769000000000000001, '知识集合清单', 1763000000000000001, 150, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:knowledge:collection:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '知识中心：有哪些集合、责任人、数据等级'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769000000000000001)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:knowledge:collection:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769000000000000002, '新建知识集合', 1763000000000000001, 151, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:knowledge:collection:create', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '知识中心：集合是授权与责任的最小单位，与「看清单」分开授权'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769000000000000002)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:knowledge:collection:create');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769000000000000003, '知识文档清单', 1763000000000000001, 152, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:knowledge:document:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '知识中心：文档与版本的来源/处理状态/失败阶段（只读）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769000000000000003)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:knowledge:document:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769000000000000004, '登记知识文档', 1763000000000000001, 153, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:knowledge:document:upload', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '知识中心：登记文档与版本（不改「能不能被检索」）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769000000000000004)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:knowledge:document:upload');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769000000000000005, '知识处理与退役', 1763000000000000001, 154, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:knowledge:document:manage', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '知识中心：推进/失败/退役处理——直接改变「这份资料能不能被检索」'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769000000000000005)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:knowledge:document:manage');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769000000000000006, '知识检索测试', 1763000000000000001, 155, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:knowledge:retrieval:test', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '知识中心：只检索不生成，用于区分「知识没检索到」与「模型答不好」'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769000000000000006)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:knowledge:retrieval:test');

-- ----------------------------
-- 二、角色授权（只给 AI 管理员）
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu
 where perms in ('aig:knowledge:collection:list', 'aig:knowledge:collection:create',
                 'aig:knowledge:document:list', 'aig:knowledge:document:upload',
                 'aig:knowledge:document:manage', 'aig:knowledge:retrieval:test');

-- ⚠️ 父菜单也必须授权，否则菜单树里看不到这一组（RuoYi 按「用户已授权的菜单」建树）
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1763000000000000001;

-- 核对：六个权限行都在，且 aig_admin 都有
select m.perms, count(rm.role_id) as granted_roles
  from sys_menu m
  left join sys_role_menu rm on rm.menu_id = m.menu_id
 where m.perms like 'aig:knowledge:%'
 group by m.perms
 order by m.perms;
