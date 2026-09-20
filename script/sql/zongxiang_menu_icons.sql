-- ============================================================================
-- 纵享工作空间：让左侧菜单图标与所对应的能力匹配
--
-- 背景：以下 5 个可见菜单的 icon 名在工程 svg 图标库（frontend/src/assets/icons/svg）
--       里并不存在，侧栏会渲染成空白：
--         业务应用 grid / 管理中心 setting / 重复人才预警 warning /
--         平台治理 robot / 平台配置 set-up
--       另外 AI会话、我的公文、敏感操作审计、参数设置、组织与权限 的图标语义不够贴切。
--
-- 做法：只改 sys_menu.icon（幂等 UPDATE），不动结构/授权/路由。
--       只使用工程里确实存在的图标名（已逐个核对图标库清单）。
-- 用法：mysql -u<user> -p -D ai_video_poc < zongxiang_menu_icons.sql
-- 前置：先备份 sys_menu
-- ============================================================================

-- 一、修复库中不存在的图标（侧栏空白）
update sys_menu set icon = 'category'    where menu_id = 1764000000000000003 and icon <> 'category';    -- 业务应用（应用分类）
update sys_menu set icon = 'system'      where menu_id = 1764000000000000005 and icon <> 'system';      -- 管理中心（平台管理）
update sys_menu set icon = 'my-copy'     where menu_id = 1762000000000000103 and icon <> 'my-copy';     -- 重复人才预警（重复记录）
update sys_menu set icon = 'model'       where menu_id = 1763000000000000001 and icon <> 'model';       -- 平台治理（AI 能力与模型）
update sys_menu set icon = 'edit'        where menu_id = 1764000000000000006 and icon <> 'edit';        -- 平台配置（配置维护）

-- 二、语义更贴合的调整
update sys_menu set icon = 'company'      where menu_id = 1761400000000000001 and icon <> 'company';      -- 组织与权限（组织）
update sys_menu set icon = 'checkbox'     where menu_id = 1764000000000000004 and icon <> 'checkbox';     -- 审批协同（审批）
update sys_menu set icon = 'message'      where menu_id = 1761400000000000008 and icon <> 'message';      -- AI会话（对话）
update sys_menu set icon = 'documentation' where menu_id = 1761400000000011629 and icon <> 'documentation'; -- 我的公文（文档）
update sys_menu set icon = 'eye-open'     where menu_id = 1762000000000000106 and icon <> 'eye-open';     -- 敏感操作审计（审计查看）
update sys_menu set icon = 'slider'       where menu_id = 1761400000000000106 and icon <> 'slider';       -- 参数设置（参数）
update sys_menu set icon = 'eye-open'     where menu_id = 1763000000000000104 and icon <> 'eye-open';     -- 调用审计（审计查看）

-- 三、一级导航语义对齐
update sys_menu set icon = 'skill'        where menu_id = 1764000000000000002 and icon <> 'skill';        -- AI工具（AI 能力）
update sys_menu set icon = 'dashboard'    where menu_id = 1764000000000000001 and icon <> 'dashboard';    -- 工作台（隐藏项，保持一致）

-- 四、自检：列出这一批菜单的最终图标
select menu_id, menu_name, icon from sys_menu
 where menu_id in (1764000000000000001, 1764000000000000002, 1764000000000000003, 1764000000000000004,
                   1764000000000000005, 1764000000000000006, 1763000000000000001, 1761400000000000001,
                   1761400000000000008, 1761400000011629, 1762000000000000103, 1762000000000000106,
                   1761400000000000106, 1763000000000000104)
 order by menu_id;
