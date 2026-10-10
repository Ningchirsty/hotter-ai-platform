-- ------------------------------------------------------------------
-- 岗位工作台（主文档线增量 1b）——岗位包管理台页面菜单行 + 角色授权
--
-- 前置：ry_vue.sql、aig_ai_gov_menu.sql（三个 aig_* 角色）、aig_role_workspace_menu.sql（六个权限点）
--
-- 【为什么页面行单独一份、晚于权限行】
--   权限行（aig_role_workspace_menu.sql）必须早种：权限串只有进了 sys_menu.perms 才有意义，
--   否则接口对所有人 403（守卫 AigPermissionSeedCoverageTest 会在构建期抓住）。
--   而页面行的 component 指向前端页面 `aigov/rolePackage/index`——它此前还不存在。
--   先种菜单会得到一个"点进去空白"的入口，比"暂时没有入口"更糟。
--
-- ⚠️ 页面行的守卫只按 menu_id，不能带 perms 条件
--   `aig:role:list` 故意与权限行重名，若照抄权限行的「menu_id 不存在且 perms 不存在」守卫，
--   页面菜单将**永远插不进去**（同一坑见 aig_studio_pages.sql / aig_user_quota_menu.sql）。
--
-- ⚠️ 父菜单也必须授权，否则菜单树里看不到这一页（RuoYi 按「用户已授权的菜单」建树）。
--
-- 幂等：页面行 insert ... where not exists（只按 menu_id）；授权 insert ignore。
-- ID：1768500000000000011（权限行占 0001~0006，页面行用 0011）
-- ------------------------------------------------------------------

-- ----------------------------
-- 一、页面菜单（menu_type='C'）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768500000000000011, '岗位包管理', 1763000000000000001, 112, 'role-package', 'aigov/rolePackage/index',
       'N', 'Y', 'C', '0', '0', 'aig:role:list', 'tree',
       1761000000000000103, 1761100000000000001, sysdate(),
       '岗位包管理台：草稿（可改）→ 预检 → 发布（TESTING/PUBLISHED）→ 停用（可撤销）；发布后不可改'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768500000000000011);

-- ----------------------------
-- 二、角色授权（只给 AI 管理员）
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1768500000000000011;

-- ⚠️ 父菜单必须授权，否则菜单树里看不到这一页
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1763000000000000001;

-- 核对：页面行在、组件路径正确、且已授权给 AI 管理员
select m.menu_id, m.menu_name, m.component, m.perms,
       (select count(*) from sys_role_menu rm where rm.menu_id = m.menu_id) as granted_roles
  from sys_menu m
 where m.menu_id = 1768500000000000011;
