-- ------------------------------------------------------------------
-- 结果反馈（Phase E）—— 权限点 + 角色授权
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）、
--       aig_result_feedback.sql（反馈账本）
--
-- 【为什么只种两个权限行】员工**提交**反馈走 @SaCheckLogin（反馈是员工动作，
--   加权限点只会抬高入口）；需要权限点的是"看问题队列"与"处理反馈"——那是训练负责人的职责。
--
-- 【授权范围：只给 AI 管理员】与 studio/knowledge/tool 三个脚本同一口径；
--   设计 §4.3 的「业务训练负责人」应由运维按需新建并单独授权。
--
-- 幂等：insert ... where not exists（menu_id 与 perms 双条件）；授权用 insert ignore。
-- ID 段：1769200000000000001..（未占用）。
-- ------------------------------------------------------------------

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769200000000000001, '反馈问题队列', 1763000000000000001, 170, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:feedback:queue', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '反馈：待处理/已确认/已驳回的问题清单（训练负责人）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769200000000000001)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:feedback:queue');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1769200000000000002, '处理反馈', 1763000000000000001, 171, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:feedback:handle', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '反馈：确认问题（补知识/改规则/加入用例）或驳回；只送进改进流程，不直接改线上版本'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1769200000000000002)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:feedback:handle');

insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu
 where perms in ('aig:feedback:queue', 'aig:feedback:handle');

-- ⚠️ 父菜单也必须授权，否则菜单树里看不到这一组
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1763000000000000001;

-- 核对
select m.perms, count(rm.role_id) as granted_roles
  from sys_menu m
  left join sys_role_menu rm on rm.menu_id = m.menu_id
 where m.perms like 'aig:feedback:%'
 group by m.perms
 order by m.perms;
