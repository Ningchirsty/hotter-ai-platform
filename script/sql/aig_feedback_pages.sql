-- ------------------------------------------------------------------
-- 结果反馈与运营指标 —— 页面菜单行 + 角色授权
--
-- 前置：aig_feedback_menu.sql（两个权限行）、aig_ops_menu.sql（运营指标权限行）
--
-- 【为什么页面行单独一个脚本】权限行必须与接口同时上线（少了就是 403）；
--   页面行只在页面组件交付后才种——先种会得到"点进去空白"的入口。
--   本次前端页面（`aigov/feedback/index`）已交付，故在此补页面行。
--
-- 幂等：insert ... where not exists（menu_id 与 component 双条件）；授权用 insert ignore。
-- ID 段：1769400000000000001..（未占用）。
-- ------------------------------------------------------------------

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769400000000000001, '反馈与运营', 1763000000000000001, 190, 'feedback',
       'aigov/feedback/index', 'N', 'Y', 'C', '0', '0', 'aig:feedback:queue', 'chart', 
       1761000000000000103, 1761100000000000001, sysdate(),
       '反馈问题队列 + 运营指标（累计成果=经确认结果；引用有效率与模型成本暂无可聚合数据源，如实为 0）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769400000000000001)
    and not exists (select 1 from (select component from sys_menu) c
                     where c.component = 'aigov/feedback/index');

insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu
 where component = 'aigov/feedback/index';

-- ⚠️ 父菜单也必须授权，否则菜单树里看不到这一组
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1763000000000000001;

-- 核对
select menu_id, menu_name, path, component, perms
  from sys_menu
 where component = 'aigov/feedback/index';
