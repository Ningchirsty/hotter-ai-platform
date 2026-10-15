-- ------------------------------------------------------------------
-- 工具注册 —— 页面菜单行 + 角色授权
--
-- 前置：aig_tool.sql（三张工具表）、aig_tool_menu.sql（两个权限行）
--
-- 页面行只在页面组件交付后才种——先种会得到"点进去空白"的入口。
-- 本次前端页面（`aigov/tool/index`）已交付，故在此补页面行。
--
-- 幂等：insert ... where not exists（menu_id 与 component 双条件）；授权用 insert ignore。
-- ID 段：1769600000000000001..（未占用）。
-- ------------------------------------------------------------------

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769600000000000001, '工具注册', 1763000000000000001, 210, 'tool',
       'aigov/tool/index', 'N', 'Y', 'C', '0', '0', 'aig:tool:list', 'set-up',
       1761000000000000103, 1761100000000000001, sysdate(),
       '工具注册：版本/操作类型/接入状态（未接入显示「已登记 待接入」）；写操作需人工确认'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769600000000000001)
    and not exists (select 1 from (select component from sys_menu) c
                     where c.component = 'aigov/tool/index');

insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu where component = 'aigov/tool/index';

-- ⚠️ 父菜单也必须授权，否则菜单树里看不到这一组
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1763000000000000001;

-- 核对
select menu_id, menu_name, path, component, perms
  from sys_menu
 where component = 'aigov/tool/index';
