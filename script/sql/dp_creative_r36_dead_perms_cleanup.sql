-- =====================================================================
-- R36（V0.2）：清理三个"菜单里有、代码里没有"的死权限
--
-- 背景：这三条按钮权限自 R7 建菜单时就写进了 sys_menu，但全仓（后端接口 + 前端按钮）
-- 从来没有用到它们。菜单里留着会让人以为平台具备该能力（"为什么我不能取消生成？"），
-- 而权限矩阵静态对照里它们永远躺在"菜单里有但代码没用到"那一栏——噪声会淹没真问题。
--
-- 判据（本轮实测，不是推测）：
--   * 后端 81 个 creative 接口的 @SaCheckPermission 里没有这三个 perms；
--   * 前端 frontend/src 里没有这三个字符串，也没有对应按钮；
--   * 业务上对应的能力由别的动作承担：
--       - 取消生成 → 没有取消语义（生成任务一旦提交就跑到结束，失败走"重试"）
--       - 视觉门锁定 → 锁定由"审核通过"推进阶段完成（creative:gate:review）
--       - 模板删除 → 模板只做启用/停用与退役（creative:template:edit + 退役接口）
--
-- 生产环境已于 R36 手工执行过（结果：3 行菜单 + 6 行角色映射删除，0 悬挂映射，
-- 页面菜单数不变，静态对照的"菜单里有但代码没用到"变为空）。本脚本是**幂等的收口**：
-- 已经删过的环境再执行一次不会有任何变化；新环境不应该再产生这些行
-- （dp_creative_menu.sql 里的对应行已同步删除）。
--
-- 执行方式（生产）：
--   docker exec -i ai-video-poc-mysql-1 mysql --default-character-set=utf8mb4 \
--     -uroot -p"$MYSQL_ROOT_PASSWORD" ai_video_poc < dp_creative_r36_dead_perms_cleanup.sql
--
-- 回滚：见文件末尾"回滚 SQL"（被删的整行原样留在本文件里，含 menu_id / 父菜单 / 顺序 /
-- 授权角色，照着插回去即可）。
-- =====================================================================

-- 1) 先看要删什么（幂等：删过之后这里返回 0 行）
SELECT menu_id, menu_name, parent_id, order_num, menu_type, perms, status
  FROM sys_menu
 WHERE perms IN ('creative:production:cancel', 'creative:gate:lock', 'creative:template:remove');

-- 2) 删角色映射（先删映射再删菜单，避免出现指向不存在菜单的悬挂行）
DELETE rm FROM sys_role_menu rm
  JOIN sys_menu m ON m.menu_id = rm.menu_id
 WHERE m.perms IN ('creative:production:cancel', 'creative:gate:lock', 'creative:template:remove');

-- 3) 删菜单行
DELETE FROM sys_menu
 WHERE perms IN ('creative:production:cancel', 'creative:gate:lock', 'creative:template:remove');

-- 4) 核对（三项都应如注释所示）
--    剩余菜单行 = 0
SELECT COUNT(*) AS left_menu_rows
  FROM sys_menu
 WHERE perms IN ('creative:production:cancel', 'creative:gate:lock', 'creative:template:remove');
--    悬挂的角色-菜单映射 = 0
SELECT COUNT(*) AS dangling_role_menu
  FROM sys_role_menu rm
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_id = rm.menu_id);
--    页面菜单数不变（生产为 8；只删按钮，不动页面）
SELECT COUNT(*) AS creative_page_menus
  FROM sys_menu
 WHERE menu_type = 'C' AND (path LIKE 'creative%' OR component LIKE 'creative%');
--    creative 按钮数（生产 35 → 32）
SELECT COUNT(*) AS creative_button_perms
  FROM sys_menu
 WHERE perms LIKE 'creative:%';

-- =====================================================================
-- 回滚 SQL（把这三个死权限恢复原样；仅在确需回退 R36 时执行）
--
-- 这三行原本来自 dp_creative_menu.sql（R7 建菜单），字段值与那份种子里的**逐字一致**
-- （列清单也照抄种子，含 active_menu / ext 两列，漏了会与线上表结构对不上）。
-- 父菜单：104=AI生产中心 / 105=详情页与审核 / 106=视觉模板库。
-- 授权角色：1765100000000000001（content_admin）、1765100000000000002（content_member）
-- ——这 6 行映射是 R7 用"把 1767 段全部菜单授予已拥有内容任务菜单的角色"批量授的。
--
-- INSERT IGNORE INTO sys_menu
-- (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
--  visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
-- VALUES
-- (1767000000000001403, '取消生成',   1767000000000000104, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'creative:production:cancel', '#', '', '', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL, ''),
-- (1767000000000001503, '视觉门锁定', 1767000000000000105, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'creative:gate:lock',         '#', '', '', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL, ''),
-- (1767000000000001603, '模板删除',   1767000000000000106, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'creative:template:remove',   '#', '', '', 1761000000000000103, 1761100000000000001, NOW(), NULL, NULL, '');
--
-- INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
--   (1765100000000000001, 1767000000000001403),
--   (1765100000000000001, 1767000000000001503),
--   (1765100000000000001, 1767000000000001603),
--   (1765100000000000002, 1767000000000001403),
--   (1765100000000000002, 1767000000000001503),
--   (1765100000000000002, 1767000000000001603);
-- =====================================================================
