-- ============================================================================
-- 纵享工作空间：管理中心分组对齐设计规范 + 工作台/审批协同归位
--
-- 设计规范（交付包 IMPLEMENTATION.md §3 / preview 截图）要求：
--   一级导航：工作台 / AI工具 / 业务应用 / 审批协同 / 管理中心
--   管理中心二级：组织与权限 / 平台治理 / 平台配置 / 运维监控 / 开发工具
--
-- 做法（幂等、可重复执行、不删除任何菜单）：
--   1) 复用既有菜单行做「重命名」，路由 path 不变，因此已有角色授权与外部链接不受影响：
--        系统管理   -> 组织与权限
--        系统监控   -> 运维监控
--        系统工具   -> 开发工具
--        AI平台治理 -> 平台治理（页面标题仍是 AI平台治理，见前端 PageHeading）
--   2) 新建「平台配置」分组，把字典/参数/通知/文件/客户端归进去
--   3) 测试菜单、PLUS官网 移到「开发工具」下
--   4) 「我的任务」移到「审批协同」下；空掉的「工作台」一级目录置为隐藏
--      （静态路由 /index 才是设计稿里的「工作台」首页）
--   5) 「平台配置」的授权按“已有其子菜单的角色”派生，与其它分组口径一致
--
-- 用法：mysql -u<user> -p -D ai_video_poc < zongxiang_admin_grouping.sql
-- 前置：先备份 sys_menu / sys_role_menu
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、重命名既有目录（path 不变，避免破坏已授权路由）
-- ---------------------------------------------------------------------------
update sys_menu set menu_name = '组织与权限', order_num = 2, icon = 'user'
  where menu_id = 1761400000000000001 and menu_name <> '组织与权限';

update sys_menu set menu_name = '运维监控', order_num = 4, icon = 'monitor'
  where menu_id = 1761400000000000002 and menu_name <> '运维监控';

update sys_menu set menu_name = '开发工具', order_num = 5, icon = 'tool'
  where menu_id = 1761400000000000003 and menu_name <> '开发工具';

update sys_menu set menu_name = '平台治理', order_num = 1, icon = 'robot'
  where menu_id = 1763000000000000001 and menu_name <> '平台治理';

-- ---------------------------------------------------------------------------
-- 二、新建「平台配置」分组（管理中心第 3 项）
-- ---------------------------------------------------------------------------
insert ignore into sys_menu values(1764000000000000006, '平台配置', 1764000000000000005, 3, 'platform-config', null, '', 'N', 'Y', 'M', '0', '0', '', 'set-up', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '工作空间管理中心二级分组：平台配置');

-- ---------------------------------------------------------------------------
-- 三、把配置类菜单移入「平台配置」（只改 parent/order，不动 perms）
-- ---------------------------------------------------------------------------
update sys_menu set parent_id = 1764000000000000006, order_num = 1
  where menu_id = 1761400000000000105 and parent_id <> 1764000000000000006; -- 字典管理
update sys_menu set parent_id = 1764000000000000006, order_num = 2
  where menu_id = 1761400000000000106 and parent_id <> 1764000000000000006; -- 参数设置
update sys_menu set parent_id = 1764000000000000006, order_num = 3
  where menu_id = 1761400000000000107 and parent_id <> 1764000000000000006; -- 通知公告
update sys_menu set parent_id = 1764000000000000006, order_num = 4
  where menu_id = 1761400000000000118 and parent_id <> 1764000000000000006; -- 文件管理
update sys_menu set parent_id = 1764000000000000006, order_num = 5
  where menu_id = 1761400000000000123 and parent_id <> 1764000000000000006; -- 客户端管理

-- ---------------------------------------------------------------------------
-- 四、开发辅助类菜单归入「开发工具」
-- ---------------------------------------------------------------------------
update sys_menu set parent_id = 1761400000000000003, order_num = 3
  where menu_id = 1761400000000000005 and parent_id <> 1761400000000000003; -- 测试菜单（目录）
update sys_menu set parent_id = 1761400000000000003, order_num = 4
  where menu_id = 1761400000000000004 and parent_id <> 1761400000000000003; -- PLUS官网

-- ---------------------------------------------------------------------------
-- 五、「我的任务」归入「审批协同」，空掉的「工作台」目录隐藏
-- ---------------------------------------------------------------------------
update sys_menu set parent_id = 1764000000000000004, order_num = 1
  where menu_id = 1761400000000011618 and parent_id <> 1764000000000000004; -- 我的任务（目录）

update sys_menu set visible = '1'
  where menu_id = 1764000000000000001
    and visible <> '1'
    and not exists (select 1 from (select 1 from sys_menu where parent_id = 1764000000000000001) t);

-- ---------------------------------------------------------------------------
-- 六、「平台配置」授权：按已有其子菜单的角色派生（与其它分组一致）
-- ---------------------------------------------------------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select distinct rm.role_id, 1764000000000000006 from sys_role_menu rm
 where rm.menu_id in (1761400000000000105, 1761400000000000106, 1761400000000000107,
                      1761400000000000118, 1761400000000000123);

-- 新建目录若其子菜单已授权但目录本身漏授权，这里补齐（幂等）
insert ignore into sys_role_menu (role_id, menu_id)
select distinct rm.role_id, 1761400000000000003 from sys_role_menu rm
 where rm.menu_id in (1761400000000000115, 1761400000000000005, 1761400000000000004);
insert ignore into sys_role_menu (role_id, menu_id)
select distinct rm.role_id, 1764000000000000004 from sys_role_menu rm
 where rm.menu_id in (1761400000000011618, 1761400000000011629, 1761400000000011619, 1761400000000011632, 1761400000000011633);

-- ---------------------------------------------------------------------------
-- 七、自检
-- ---------------------------------------------------------------------------
select '管理中心分组' as label, m.menu_id, m.menu_name, m.order_num, m.path, m.visible
  from sys_menu m
 where m.parent_id = 1764000000000000005
 order by m.order_num;

select '平台配置子项' as label, m.menu_id, m.menu_name, m.order_num, m.path
  from sys_menu m
 where m.parent_id = 1764000000000000006
 order by m.order_num;

select '审批协同子项' as label, m.menu_id, m.menu_name, m.order_num, m.path
  from sys_menu m
 where m.parent_id = 1764000000000000004
 order by m.order_num;
