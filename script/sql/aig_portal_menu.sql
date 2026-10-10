-- ------------------------------------------------------------------
-- 员工 AI 工作台（门户）——页面菜单行 + 角色授权
--
-- 主文档线增量 2（Q5：**新增独立路由 `/ai-workspace`**，不改造现有 `/index`）。
-- 前置：ry_vue.sql、aig_ai_gov_menu.sql（三个 aig_* 角色）
--
-- 【为什么这一页是**顶层菜单**（parent_id = 0）】
--   门户是给员工用的入口，不是治理台的一个子页。放在治理模块下会让"员工要用它"
--   变成"要先能进治理台"——那正好与它的定位相反。所以它是独立顶层路由，
--   与 `video-creation`、`creative` 这些业务工作台同级。
--
-- 【为什么**没有权限点**（perms 留空）】
--   页面接口是 `@SaCheckLogin`：它只返回"当前用户自己"能看到的东西，
--   范围由岗位绑定与登录身份决定。给它编一个 `aig:portal:view` 权限点，
--   等于发明一个"谁能使用员工工作台"的授权面——而答案永远是"所有员工"，
--   于是每建一个角色都要记得勾上，漏了就表现为"某人打开是空白"。
--   真正的边界是**数据范围**，不是权限点。
--   因此本脚本也不需要为任何 `PERM_` 常量供种（守卫只管 `PERM_` 开头的常量）。
--
-- 【授权】默认授予现有三个 aig_* 角色；**上线时请按面向员工的身份角色调整**——
--   哪些角色能进这个门户是运维决定，不是代码决定。
--
-- 幂等：页面行 insert ... where not exists（只按 menu_id）；授权 insert ignore。
-- ID：1768700000000000001（1768 段 8400/8500 已用，8700 未占用）
-- ------------------------------------------------------------------

-- ----------------------------
-- 一、页面菜单（menu_type='C'，顶层路由）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768700000000000001, 'AI 工作台', 0, 8, 'ai-workspace', 'aigov/portal/index',
       'N', 'N', 'C', '0', '0', '', 'guide',
       1761000000000000103, 1761100000000000001, sysdate(),
       '员工 AI 工作台：我只看到对我开放的岗位与卡片（服务端按发布状态与组织/品牌绑定过滤）+ 我的任务'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768700000000000001);

-- ----------------------------
-- 二、角色授权
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, 1768700000000000001
  from sys_role
 where role_id in (1763100000000000001, 1763100000000000002, 1763100000000000003);

-- 核对：页面行在、组件路径正确、perms 为空（说明它不靠权限点）、且已授权
select m.menu_id, m.menu_name, m.parent_id, m.path, m.component, m.perms,
       (select count(*) from sys_role_menu rm where rm.menu_id = m.menu_id) as granted_roles
  from sys_menu m
 where m.menu_id = 1768700000000000001;
