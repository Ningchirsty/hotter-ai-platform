-- ----------------------------------------------------------------------------
-- AI 治理：能力域「高风险变更」权限点（Q1C 轻量门槛）
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）
--
-- 【它防的是什么】
--   aig:capability:edit / aig:route:edit 只表示「能改配置」。但把
--   aig_route_policy.allow_external 从 'N' 改成 'Y' 等于**改变数据出域边界**——
--   之前这两件事共用一个权限点，于是一个人就能悄悄放开外发。
--   本脚本给它单独一个权限点：aig:capability:high-risk。
--
-- 【谁该有】
--   只给 aig_admin（AI 数智化管理员）。aig_security 默认不给：安全角色应当是
--   「审计与把关」的手，不该顺手就能放宽边界；要让安全也能改，由运维显式授权即可
--   （授权语句是按 perms 反查的，加一行就行）。
--
-- 【覆盖范围（本轮）】服务端在以下入口判定（见 AigHighRiskGuard / AigHighRiskChanges）：
--   · 新增/编辑路由策略：命中 allow_external='Y'、数据等级 RESTRICTED/STRICT、首选外部部署；
--   · 删除路由策略：被删行命中上述任一条（删掉「仅本地」的效果与放开外发相同）；
--   · 停用能力（启用 → 停用）、删除能力。
--   本轮**不含**「限制级能力的候选变更」——那需要能力的声明等级列（Q2C 的 min_data_level），
--   该列尚不存在；否则只能按策略行猜，会误报。详见设计文档 §8.1 与本文件末尾备注。
--
-- 幂等：权限行 insert ... where not exists（menu_id 与 perms 双条件）；授权用 insert ignore。
-- ID 段：1768600000000000001..（1768 段已用：8100 配额、8200 审批、8400 训练台、8500 岗位包、8700 门户）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 一、权限行（menu_type='F'，挂在「AI能力目录」1763000000000000101 下）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768600000000000001, '能力高风险变更', 1763000000000000101, 90, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:capability:high-risk', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'Q1C 轻量门槛：放开外发 / 动限制级策略 / 停用或删除能力。与 aig:capability:edit 刻意分开——能改配置不等于能放宽数据出界'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768600000000000001)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:capability:high-risk');

-- ----------------------------
-- 二、角色授权（只给 AI 管理员）
-- ----------------------------
-- 幂等说明：用 perms 反查菜单，所以即使 menu_id 因环境不同而变化也能授上；
--   已经授过则忽略（insert ignore 依赖 uk(role_id, menu_id)）。
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu where perms = 'aig:capability:high-risk';

-- ⚠️ 父菜单（AI平台治理）必须已授权；这里补一次，避免「菜单树里看不到」的经典坑。
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1763000000000000001
  where not exists (select 1 from (select 1 from sys_role_menu
                                    where role_id = 1763100000000000001
                                      and menu_id = 1763000000000000001) x);
