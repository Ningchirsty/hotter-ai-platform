-- ----------------------------------------------------------------------------
-- AI 治理：权限边界正名（设计 §5.4 / 走读 §4.2、§5.4）
--
-- 前置：ry_vue.sql（sys_menu/sys_role_menu）、aig_ai_gov_menu.sql（三个 aig_* 角色）
--
-- 【这次改了什么（4 处拆分 + 2 处更名 + 菜单名对齐）】
--   ① 调用与查询分权
--        aig:capability:invoke  ← 真实调用（POST /aigov/invoke/{capabilityCode}）
--        aig:capability:trial   ← dryRun 试调（只做路由决策，不调模型）
--        aig:capability:query   从此只表示"看详情"
--      原先两者都用 query ⇒ **能查能力就等于能发起计费调用**。
--   ② 密钥查看/写入分权
--        aig:model:secret:view（看脱敏引用/端点）  aig:model:secret:edit（写/清明文）
--      原先共用一个 aig:model:secret，菜单名却叫「查看密钥引用」——名字说只读、实际能改。
--   ③ 外呼测试独立
--        aig:model:probe  ← 连通性测试 / 批量健康探测（会带密钥**真的对外发一次请求**）
--      原先挂在 aig:model:edit 上：能改治理属性就顺带能拿密钥外呼。
--   ④ 能力↔模型候选归位
--        aig:capability:candidate:{list,add,edit,remove}
--      原先挂 aig:route:{list,add,edit,remove}——候选是"给能力挂哪个模型"，归属能力域。
--   ⑤ 场景指定更名：aig:route:binding → aig:route:scenario（"binding"与本仓另外三种"绑定"撞名）
--   ⑥ 菜单名与动作对齐：见文件尾部 UPDATE。
--
-- 【⚠️ 最重要的口径：本脚本**不让任何人掉权限**】
--   新增权限点的授权对象 = **原本拥有被拆分/更名前那个权限点的全部角色**（按 perms 反查 sys_role_menu）。
--   于是：
--     · 原先能调用的，现在仍有 invoke + trial；
--     · 原先能看密钥引用的，现在仍有 view；原先能写密钥的，现在仍有 edit；
--     · 原先能测连通性的，现在仍有 probe；
--     · 原先能管候选的，现在仍有 candidate:*；
--     · 原先能维护场景指定的，改名后仍能维护（menu_id 不变，只在原行上改 perms）。
--   **唯一例外是"收紧"**：`aig_viewer`（只读角色）**不再授予 invoke**——这正是本次修复的靶子
--   （只读账号不该能发起计费调用）。它仍保留 trial，可以预演链路。
--   如果你们的 aig_viewer 实际上承担了业务调用，请删掉下面那句 `and rm.role_id <> 1763100000000000003`
--   （或在页面上给对应角色补授 invoke）。
--
-- 【部署顺序】本脚本必须在**新镜像切换之前**执行：改名的那两条（secret→secret:edit、binding→scenario）
--   若不执行，持有旧权限的账号在新代码下会被 403（只有超管能过）。已列入 doc24 §5 的"本版必做"清单。
--
-- 幂等：权限行 insert ... where not exists（menu_id 与 perms 双条件）；改名用 update ... where perms=旧值；
--   授权用 insert ignore。可安全重复执行。
-- ID 段：1768800000000000001..（1763 菜单段、17681 配额、17682 审批、17684 训练台、17685 岗位包、17686 高风险、17687 门户）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 一、新增权限行（menu_type='F'）
-- ----------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768800000000000001, '能力真实调用', 1763000000000000101, 91, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:capability:invoke', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '真实调用会调模型并产生费用与审计；与「能力查询」分开——能查不等于能发起计费调用'
  where not exists (select 1 from (select menu_id from sys_menu) t where t.menu_id = 1768800000000000001)
    and not exists (select 1 from (select perms from sys_menu) p where p.perms = 'aig:capability:invoke');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768800000000000002, '能力试调（dryRun）', 1763000000000000101, 92, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:capability:trial', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '只做路由决策：不调模型、不花钱、不写审计。只读岗位可以用它核对链路'
  where not exists (select 1 from (select menu_id from sys_menu) t where t.menu_id = 1768800000000000002)
    and not exists (select 1 from (select perms from sys_menu) p where p.perms = 'aig:capability:trial');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768800000000000003, '能力候选-列表', 1763000000000000101, 93, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:capability:candidate:list', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '能力×模型候选的读取（从 aig:route:list 迁出：候选归属能力域）'
  where not exists (select 1 from (select menu_id from sys_menu) t where t.menu_id = 1768800000000000003)
    and not exists (select 1 from (select perms from sys_menu) p where p.perms = 'aig:capability:candidate:list');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768800000000000004, '能力候选-新增', 1763000000000000101, 94, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:capability:candidate:add', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '给能力挂模型候选（从 aig:route:add 迁出）'
  where not exists (select 1 from (select menu_id from sys_menu) t where t.menu_id = 1768800000000000004)
    and not exists (select 1 from (select perms from sys_menu) p where p.perms = 'aig:capability:candidate:add');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768800000000000005, '能力候选-修改', 1763000000000000101, 95, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:capability:candidate:edit', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '改候选的用途/优先级/启停（从 aig:route:edit 迁出）'
  where not exists (select 1 from (select menu_id from sys_menu) t where t.menu_id = 1768800000000000005)
    and not exists (select 1 from (select perms from sys_menu) p where p.perms = 'aig:capability:candidate:edit');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768800000000000006, '能力候选-删除', 1763000000000000101, 96, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:capability:candidate:remove', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '删除能力×模型候选（从 aig:route:remove 迁出）'
  where not exists (select 1 from (select menu_id from sys_menu) t where t.menu_id = 1768800000000000006)
    and not exists (select 1 from (select perms from sys_menu) p where p.perms = 'aig:capability:candidate:remove');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768800000000000007, '模型密钥引用查看', 1763000000000000102, 91, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:model:secret:view', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '只看脱敏引用与端点（原 aig:model:secret 的读语义）'
  where not exists (select 1 from (select menu_id from sys_menu) t where t.menu_id = 1768800000000000007)
    and not exists (select 1 from (select perms from sys_menu) p where p.perms = 'aig:model:secret:view');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
select 1768800000000000008, '模型连通性测试/探测', 1763000000000000102, 92, '', '', 'N', 'Y', 'F', '0', '0',
       'aig:model:probe', '#', 1761000000000000103, 1761100000000000001, sysdate(),
       '会带密钥真的对外发一次请求（单模型测试 + 批量健康探测）；从 aig:model:edit 拆出'
  where not exists (select 1 from (select menu_id from sys_menu) t where t.menu_id = 1768800000000000008)
    and not exists (select 1 from (select perms from sys_menu) p where p.perms = 'aig:model:probe');

-- ----------------------------
-- 二、原权限行**原地改名**（menu_id 不变 ⇒ 已有角色授权自动保留）
-- ----------------------------
-- aig:model:secret → aig:model:secret:edit（写/清密钥）
update sys_menu set perms = 'aig:model:secret:edit', menu_name = '模型密钥写入/清除',
       remark = '写入或清除模型密钥（含供应商维度批量）；与 secret:view 分开——能看脱敏引用不等于能改凭据'
 where perms = 'aig:model:secret';

-- aig:route:binding → aig:route:scenario（场景指定）
update sys_menu set perms = 'aig:route:scenario', menu_name = '场景指定',
       remark = '维护「场景 × 能力 → 指定供应商」的场景指定（只收窄候选，不放宽治理口径）'
 where perms = 'aig:route:binding';

-- ----------------------------
-- 三、授权：新增权限点继承"被拆分/更名前那个权限点"的受众
-- ----------------------------
-- invoke：继承 aig:capability:query 的受众，但**排除只读角色 aig_viewer**
--（这正是本次修复要挡的事：只读账号不该能发起计费调用）
insert ignore into sys_role_menu (role_id, menu_id)
select rm.role_id, newm.menu_id
  from sys_role_menu rm
  join sys_menu oldm on oldm.menu_id = rm.menu_id and oldm.perms = 'aig:capability:query'
  join sys_menu newm on newm.perms = 'aig:capability:invoke'
 where rm.role_id <> 1763100000000000003;

-- trial：继承 aig:capability:query 的**全部**受众（试调不花钱、不外发）
insert ignore into sys_role_menu (role_id, menu_id)
select rm.role_id, newm.menu_id
  from sys_role_menu rm
  join sys_menu oldm on oldm.menu_id = rm.menu_id and oldm.perms = 'aig:capability:query'
  join sys_menu newm on newm.perms = 'aig:capability:trial';

-- view：继承原 aig:model:secret 的受众（现已是 :edit，所以按 :edit 反查）
insert ignore into sys_role_menu (role_id, menu_id)
select rm.role_id, newm.menu_id
  from sys_role_menu rm
  join sys_menu oldm on oldm.menu_id = rm.menu_id and oldm.perms = 'aig:model:secret:edit'
  join sys_menu newm on newm.perms = 'aig:model:secret:view';

-- probe：继承 aig:model:edit 的受众（原先测连通性就是它）
insert ignore into sys_role_menu (role_id, menu_id)
select rm.role_id, newm.menu_id
  from sys_role_menu rm
  join sys_menu oldm on oldm.menu_id = rm.menu_id and oldm.perms = 'aig:model:edit'
  join sys_menu newm on newm.perms = 'aig:model:probe';

-- candidate:*：分别继承原 aig:route:{list,add,edit,remove} 的受众
insert ignore into sys_role_menu (role_id, menu_id)
select rm.role_id, newm.menu_id
  from sys_role_menu rm
  join sys_menu oldm on oldm.menu_id = rm.menu_id and oldm.perms = 'aig:route:list'
  join sys_menu newm on newm.perms = 'aig:capability:candidate:list';

insert ignore into sys_role_menu (role_id, menu_id)
select rm.role_id, newm.menu_id
  from sys_role_menu rm
  join sys_menu oldm on oldm.menu_id = rm.menu_id and oldm.perms = 'aig:route:add'
  join sys_menu newm on newm.perms = 'aig:capability:candidate:add';

insert ignore into sys_role_menu (role_id, menu_id)
select rm.role_id, newm.menu_id
  from sys_role_menu rm
  join sys_menu oldm on oldm.menu_id = rm.menu_id and oldm.perms = 'aig:route:edit'
  join sys_menu newm on newm.perms = 'aig:capability:candidate:edit';

insert ignore into sys_role_menu (role_id, menu_id)
select rm.role_id, newm.menu_id
  from sys_role_menu rm
  join sys_menu oldm on oldm.menu_id = rm.menu_id and oldm.perms = 'aig:route:remove'
  join sys_menu newm on newm.perms = 'aig:capability:candidate:remove';

-- 兜底：AI 管理员始终拥有全部新增权限点（避免"反查不到受众"时管理员自己也被挡住）
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, m.menu_id from sys_menu m
 where m.perms in ('aig:capability:invoke', 'aig:capability:trial',
                   'aig:capability:candidate:list', 'aig:capability:candidate:add',
                   'aig:capability:candidate:edit', 'aig:capability:candidate:remove',
                   'aig:model:secret:view', 'aig:model:probe');

-- ⚠️ 父菜单必须已授权，否则菜单树里看不到这些权限行
insert ignore into sys_role_menu (role_id, menu_id)
select 1763100000000000001, 1763000000000000001
 where not exists (select 1 from (select 1 from sys_role_menu
                                   where role_id = 1763100000000000001
                                     and menu_id = 1763000000000000001) x);

-- ----------------------------
-- 四、菜单名与实际动作对齐（走读 §5.4 的 6 处）
-- ----------------------------
update sys_menu set menu_name = '能力删除'          where perms = 'aig:capability:remove';
update sys_menu set menu_name = '路由策略-新增'      where perms = 'aig:route:add';
update sys_menu set menu_name = '路由策略-删除'      where perms = 'aig:route:remove';
update sys_menu set menu_name = '模型治理属性编辑'    where perms = 'aig:model:edit';
update sys_menu set menu_name = '调用授权审批'        where perms = 'aig:approval:approve';
update sys_menu set menu_name = '能力候选-列表'      where perms = 'aig:capability:candidate:list';

-- 核对：新权限点都已存在
SELECT perms, menu_name
  FROM sys_menu
 WHERE perms IN ('aig:capability:invoke', 'aig:capability:trial',
                 'aig:capability:candidate:list', 'aig:capability:candidate:add',
                 'aig:capability:candidate:edit', 'aig:capability:candidate:remove',
                 'aig:model:secret:view', 'aig:model:secret:edit', 'aig:model:probe',
                 'aig:route:scenario')
 ORDER BY perms;

SELECT 'AIG_PERMISSION_BOUNDARY_DONE' AS marker;
