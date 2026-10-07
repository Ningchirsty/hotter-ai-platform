-- ----------------------------------------------------------------------------
-- AI 治理层 · Agent/Skill/Package 注册中心与黄金评测 —— 权限种子（WP3）
--
-- 目标库：平台库（与 ry_vue.sql / aig_ai_gov_menu.sql 同库）
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）
--       以及 script/sql/aig_agent_registry.sql（WP3 的表）
--
-- 【为什么只有权限行（menu_type='F'），没有页面菜单（'C'）】
-- 这次交付的是 API（Controller），治理台页面还没做。若现在就种『页面菜单』，用户点进去会 404 ——
-- 那比"菜单里暂时看不到"更糟。权限行是接口鉴权真正需要的东西（Sa-Token 按 perms 判定），
-- 因此先种权限、把页面菜单留到前端落地时一起加（届时只需补 menu_type='C' 的父级行并让它们指向
-- 本文件的权限行作为下级）。
--
-- 【为什么三个写权限独立于读权限】
--   aig:agent:release —— 推进发布状态（唯一写入口，能把版本放给业务用）；
--   aig:package:scan  —— Manifest 扫描（结论是「Manifest 校验」门槛的唯一证据）；
--   aig:agent:binding —— 版本绑定范围（决定受限通道发布后谁能看见）；
--   aig:evaluation:run / aig:evaluation:review —— 跑评测（可能产生外部成本）与人工复核（放行链条上的一环）。
-- 它们都会改变别人能看到什么，或产生费用，因此不与「查看清单」共用一个权限。
--
-- 【幂等】全部 insert ... select ... where not exists（按 menu_id 与 perms 双条件），
-- 授权用 insert ignore（sys_role_menu 是 (role_id, menu_id) 复合主键）。
-- 与 aig_ai_gov_menu.sql 的『97 条纯 insert』不同：那个脚本重放会 exit=1 且卡在第一条重复键。
--
-- ID 段：权限行 1768*（1761/1763/1765/1766/1767 已被占用）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 一、注册中心权限（读）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000001, '注册中心清单', 1763000000000000001, 90, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:agent:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §5：Agent/Skill/Package 清单（读）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000001)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:agent:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000002, '注册中心详情', 1763000000000000001, 91, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:agent:query', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §5：Agent/Skill/Package 详情（读）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000002)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:agent:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000011, 'Skill清单', 1763000000000000001, 92, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:skill:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §5：Skill 清单（读）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000011)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:skill:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000012, 'Skill详情', 1763000000000000001, 93, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:skill:query', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §5：Skill 详情（读）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000012)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:skill:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000021, 'Package清单', 1763000000000000001, 94, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:package:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §6：Package 清单（读）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000021)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:package:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000022, 'Package详情', 1763000000000000001, 95, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:package:query', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §6：Package 详情（读）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000022)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:package:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000031, '评测清单', 1763000000000000001, 96, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:evaluation:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §13.2：黄金用例与评测运行清单（读）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000031)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:evaluation:list');

-- ----------------------------
-- 二、注册中心权限（写：改状态 / 改证据 / 改可见范围）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000003, '发布状态推进', 1763000000000000001, 97, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:agent:release', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §5.4：唯一写入口；能把版本放给业务用，因此独立授权'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000003)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:agent:release');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000004, '版本绑定管理', 1763000000000000001, 98, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:agent:binding', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §10.2：绑定范围决定受限通道发布后谁能看见'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000004)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:agent:binding');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000023, 'Manifest扫描', 1763000000000000001, 99, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:package:scan', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §6.2：扫描结论是「Manifest 校验」门槛的唯一证据，能改证据的人不该只是查看者'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000023)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:package:scan');

-- ----------------------------
-- 三、评测权限（详情 / 定义 / 跑 / 复核）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000032, '评测详情与证据', 1763000000000000001, 100, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:evaluation:query', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §13.2：判据原文、打分明细与「黄金用例是否通过」的证据'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000032)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:evaluation:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000033, '定义黄金用例', 1763000000000000001, 101, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:evaluation:define', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §13.2：定义用例（判据写法在定义期即校验）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000033)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:evaluation:define');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000034, '跑评测', 1763000000000000001, 102, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:evaluation:run', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §13.2：跑评测会产生评测账本，被测对象可能产生外部调用成本'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000034)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:evaluation:run');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000035, '评测人工复核', 1763000000000000001, 103, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:evaluation:review', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §13.2/§6.3-5：Rubric 用例的最后一道判断，是放行链条上的一环'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000035)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:evaluation:review');

-- ----------------------------
-- 三点五、Package 上传与安装（本轮新增）
--   上传：携包体登记包与版本（包体哈希由服务端算并与 Manifest 声明比对）；
--   安装：按 Manifest 声明的 agents/skills 建出 DRAFT 版本。
--   两个都是写动作——尤其安装会真的建出 Agent/Skill 版本行，因此独立授权。
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000024, 'Package上传', 1763000000000000001, 104, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:package:upload', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §6.1/§6.3：上传登记（包体校验和由服务端核对，包体不入库）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000024)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:package:upload');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768000000000000025, 'Package安装', 1763000000000000001, 105, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:package:install', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       'WP3 §6.3：按 Manifest 声明建出 Agent/Skill 版本（均为 DRAFT，不跳发布门槛）'
  where not exists (select 1 from (select menu_id from sys_menu) t
                     where t.menu_id = 1768000000000000025)
    and not exists (select 1 from (select perms from sys_menu) p
                     where p.perms = 'aig:package:install');

-- ----------------------------
-- 四、角色授权
--   · aig_admin（AI数智化管理员）：全部（含推进发布、定义用例、跑评测、复核）
--   · aig_security（信息安全授权人）：读 + Manifest 扫描（扫描是安全关口），
--     但不给发布推进/跑评测/复核——那些是会改变业务可见性或产生费用的动作
--   · aig_viewer（AI能力查看者）：只读
-- 用 insert ignore：sys_role_menu 是 (role_id, menu_id) 复合主键，重复授权是幂等的；
-- 而 menu_id 取自 sys_menu 的 perms 反查，避免写死 ID 与权限行不同步。
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, menu_id from sys_menu
 where perms in ('aig:agent:list', 'aig:agent:query', 'aig:agent:release', 'aig:agent:binding',
                 'aig:skill:list', 'aig:skill:query',
                 'aig:package:list', 'aig:package:query', 'aig:package:scan',
                 'aig:package:upload', 'aig:package:install',
                 'aig:evaluation:list', 'aig:evaluation:query', 'aig:evaluation:define',
                 'aig:evaluation:run', 'aig:evaluation:review');

insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000002, menu_id from sys_menu
 where perms in ('aig:agent:list', 'aig:agent:query',
                 'aig:skill:list', 'aig:skill:query',
                 'aig:package:list', 'aig:package:query', 'aig:package:scan',
                 'aig:evaluation:list', 'aig:evaluation:query');

insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000003, menu_id from sys_menu
 where perms in ('aig:agent:list', 'aig:agent:query',
                 'aig:skill:list', 'aig:skill:query',
                 'aig:package:list', 'aig:package:query',
                 'aig:evaluation:list', 'aig:evaluation:query');
