-- ----------------------------------------------------------------------------
-- AI 治理：把「人工评测录入」拆成独立权限（aig:evaluation:manual）—— 存量库补齐
--
-- 背景（为什么拆，而不是继续复用 aig:evaluation:run）：
--   ADR-014（2026-10-09）落地了「管理员来评测」：平台没有该对象执行器时，由管理员按用例给出
--   PASS/FAIL，平台登记为人工产出的黄金用例证据，从而让版本能推进到 CANDIDATE。
--   当时为了不过度设计，它复用了「跑评测」这个权限。但两者其实不等价：
--     · 机器评测的可信度来自「平台的判据在同样输入上判过了」；
--     · 人工录入的可信度只来自「一个人签了字」。
--   而它直接决定版本能不能进灰度 ⇒ 应当收紧为管理角色，否则"任何能跑评测的人顺手就能填"。
--
-- 口径：只授予 aig_admin（AI数智化管理员）。
--   aig_security（信息安全授权人）与 aig_viewer（只读）本来就**没有** aig:evaluation:run，
--   所以本次拆分对它们的可见范围没有变化；真正的影响是——**自定义角色**若只被授了
--   aig:evaluation:run，从此不再能录人工结论（这是本次收紧的目的）。
--
-- 幂等：菜单行按 perms 反查（`where not exists`），授权用 insert ignore（(role_id, menu_id) 是复合主键）。
-- 可安全重复执行；本文件不含任何 update/delete/drop。
--
-- 注意：script/sql/aig_agent_registry_menu.sql（全新库的菜单种子）里也已包含这一行，
--   所以全新库跑那支即可；本脚本是给**存量库**补齐的。
-- ----------------------------------------------------------------------------

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000036, '人工评测录入', 1763000000000000001, 104, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:evaluation:manual', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'ADR-014：平台没有该对象执行器时由管理员人工评测产出证据；比「跑评测」更严，故独立授权'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000036)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:evaluation:manual');

-- 只授给 AI数智化管理员（aig_admin）
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu where perms = 'aig:evaluation:manual';

-- 核对：权限行在不在、授给了哪些角色
select m.perms, m.menu_name, m.menu_id,
       (select count(*) from sys_role_menu rm where rm.menu_id = m.menu_id) as granted_roles
  from sys_menu m
 where m.perms = 'aig:evaluation:manual';

-- 核对：admin 角色名（便于人工确认 1763100000000000001 到底是谁）
select role_id, role_key, role_name from sys_role
 where role_id in (1763100000000000001, 1763100000000000002, 1763100000000000003);

select 'AIG_EVALUATION_MANUAL_PERM_DONE' as marker;
