-- ------------------------------------------------------------------
-- 纵享集团AI创作平台 · 工作空间信息架构迁移（增量、幂等、不删任何现有菜单）
--
-- 目标（见集成包 ROUTES-PERMISSIONS.md）：
--   一级分类固定为：工作台 / AI工具 / 业务应用 / 审批协同 / 管理中心
--   业务应用 → 内容生产协同（见 cp_content_menu.sql）
--   管理中心 → AI平台治理 → AI能力目录 / 模型注册中心 / 路由策略 / 调用审计
--
-- *** 人才管理归属变更（重要，勿回退）***
--   招聘与人才管理（原「集团人才库」）已由独立模块 ruoyi-hr-talent 重建。业务要求其在 RuoYi 中
--   保持**独立一级菜单**：menu_id 1766000000000000001「招聘管理」、parent_id = 0，
--   由 script/sql/hr_talent_menu.sql 建立，权限前缀 recruit:* / talent:*，
--   前端目录 frontend/src/views/hrtalent/*，统一在 pm.hottter.cn 域名下访问，不设独立子域名。
--   故本文件**不再**把人才管理挂到「业务应用」之下。
--   旧模块 ruoyi-talent（1762… 段菜单、views/talent/*、talent:audit:* 权限）已整体退役并删除，
--   本文件已移除对它的全部引用。若目标库仍残留旧人才菜单，请按 docs/archive/talent-library/
--   的退役说明单独清理，不要在本文件里恢复。
--
-- 设计原则：
--   1. 只新增 5 个一级分类菜单；其余菜单只改 parent_id（可回滚）。
--   2. 新分类的授权从「其子菜单已有的授权」推导，避免写死角色，也避免新建分类后
--      普通用户看不到任何菜单。
--   3. 全程 insert ignore / 条件 update，可重复执行。
--   4. 不删除、不重建任何表，不改现有权限标识。
--
-- 执行顺序：先建分类 → 挂现有菜单 → 补授权。
-- ------------------------------------------------------------------

-- 1) 五个一级分类（固定 ID，位于 1764xxxxxxxxxxxxxxx 段）
insert ignore into sys_menu values(1764000000000000001, '工作台',   0, 1, 'workspace',    null, '', 'N', 'Y', 'M', '0', '0', '', 'dashboard', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '工作空间一级分类');
insert ignore into sys_menu values(1764000000000000002, 'AI工具',   0, 2, 'ai-tools',     null, '', 'N', 'Y', 'M', '0', '0', '', 'tool',      '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '工作空间一级分类');
insert ignore into sys_menu values(1764000000000000003, '业务应用', 0, 3, 'business',     null, '', 'N', 'Y', 'M', '0', '0', '', 'grid',      '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '工作空间一级分类');
insert ignore into sys_menu values(1764000000000000004, '审批协同', 0, 4, 'approval',     null, '', 'N', 'Y', 'M', '0', '0', '', 'form',      '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '工作空间一级分类');
insert ignore into sys_menu values(1764000000000000005, '管理中心', 0, 5, 'admin-center', null, '', 'N', 'Y', 'M', '0', '0', '', 'setting',   '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '工作空间一级分类');

-- 2) 现有菜单挂到新分类下（只改父级，不删除）
--    工作台：我的任务
update sys_menu set parent_id = 1764000000000000001, order_num = 1
  where menu_id = 1761400000000011618 and parent_id <> 1764000000000000001;
--    AI工具：视频创作、AI会话、工作流（工作流同时也可视作审批协同，这里归 AI工具下的创作链路）
update sys_menu set parent_id = 1764000000000000002, order_num = 1
  where menu_id = 920000 and parent_id <> 1764000000000000002;
update sys_menu set parent_id = 1764000000000000002, order_num = 2
  where menu_id = 1761400000000000008 and parent_id <> 1764000000000000002;
update sys_menu set parent_id = 1764000000000000002, order_num = 3
  where menu_id = 1761400000000011616 and parent_id <> 1764000000000000002;
--    业务应用：内容生产协同（1765000000000000001，由 cp_content_menu.sql 挂载，此处不动）
--    说明：原「集团人才库 → 人才管理」的挂载已移除，招聘与人才管理改为一级菜单
--          （见文件头「人才管理归属变更」）。「业务应用」现有子菜单为内容生产协同。
--    管理中心：AI平台治理、系统管理、系统监控、系统工具、测试菜单、PLUS官网
update sys_menu set parent_id = 1764000000000000005, order_num = 1
  where menu_id = 1763000000000000001 and parent_id <> 1764000000000000005;
update sys_menu set parent_id = 1764000000000000005, order_num = 2
  where menu_id = 1761400000000000001 and parent_id <> 1764000000000000005;
update sys_menu set parent_id = 1764000000000000005, order_num = 3
  where menu_id = 1761400000000000002 and parent_id <> 1764000000000000005;
update sys_menu set parent_id = 1764000000000000005, order_num = 4
  where menu_id = 1761400000000000003 and parent_id <> 1764000000000000005;
update sys_menu set parent_id = 1764000000000000005, order_num = 5
  where menu_id = 1761400000000000005 and parent_id <> 1764000000000000005;
update sys_menu set parent_id = 1764000000000000005, order_num = 6
  where menu_id = 1761400000000000004 and parent_id <> 1764000000000000005;
--    审批协同：暂留空分类（工作流已在 AI工具；后续审批类功能挂这里）

-- 3) 授权：新分类授给「其子菜单已有的角色」
--    说明：原「补齐敏感操作审计页面菜单」已移除。该页面现由 hr_talent_menu.sql 提供
--          （menu_id 1766000000000000113、component hrtalent/audit/index、权限 recruit:audit:list），
--          随「招聘管理」一级菜单一并授权，本文件不再重复建菜单。
insert ignore into sys_role_menu (role_id, menu_id)
select distinct rm.role_id, 1764000000000000001 from sys_role_menu rm
 where rm.menu_id in (1761400000000011618);
insert ignore into sys_role_menu (role_id, menu_id)
select distinct rm.role_id, 1764000000000000002 from sys_role_menu rm
 where rm.menu_id in (920000, 1761400000000000008, 1761400000000011616);
insert ignore into sys_role_menu (role_id, menu_id)
select distinct rm.role_id, 1764000000000000003 from sys_role_menu rm
 where rm.menu_id in (1765000000000000001);
insert ignore into sys_role_menu (role_id, menu_id)
select distinct rm.role_id, 1764000000000000005 from sys_role_menu rm
 where rm.menu_id in (1763000000000000001, 1761400000000000001, 1761400000000000002, 1761400000000000003, 1761400000000000005, 1761400000000000004);

-- 4) 结果核对（执行后人工看一眼）
--    注：parent_id = 0 会同时列出招聘管理（1766000000000000001，一级菜单，由 hr_talent_menu.sql 建立）。
select m.menu_id, m.menu_name, m.parent_id, m.order_num, m.path, m.component, m.menu_type
  from sys_menu m where m.parent_id in (0, 1764000000000000003, 1764000000000000005, 1763000000000000001)
 order by m.parent_id, m.order_num;
