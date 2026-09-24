-- ------------------------------------------------------------------
-- AI 视觉工厂：R5 增量菜单（下线「流程向导」）
--
-- 背景（用户确认的 R5 交互调整）：向导页把 8 个环节收进一个独立页面，结果是
-- 「做完一步要退回菜单再进下一页」，而且在 6 个页面之间来回跳。
-- R5 改为：**每个环节页面顶部一条流程指引线**（由 cp_task.visual_stage 定位当前步），
-- 因此向导页不再需要。
--
-- 处理方式：**菜单停用（status='1'）而不是物理删除**。
--   1. 停用后 sys_menu/getRouters 不会再下发该路由，侧边栏与直达 URL 都进不去；
--   2. 保留行便于回退（把 status 改回 '0' 即可恢复），也留下「它曾经存在过」的痕迹；
--   3. order_num=0 的默认入口位置随之失效，「视觉项目」（order_num=1）重新成为进入
--      视觉工厂后的第一个入口——这正是 R5 想要的效果，无需改其它行。
--
-- 说明：幂等（UPDATE 天然幂等），可重复执行。
--
-- 全新环境注意：`dp_creative_r4_menu.sql` 里仍然包含向导菜单的 INSERT（那是 R4 的历史迁移，不改写）。
-- 因此新环境按顺序执行到 R4 菜单脚本之后，**必须再执行本脚本**把向导停用；
-- 或者直接把 R4 脚本里 menu_id=1767000000000000107 那一行去掉（R5 之后它已不是系统的一部分）。
-- ------------------------------------------------------------------

-- 1) 停用向导菜单
UPDATE sys_menu
   SET status = '1',
       remark = CONCAT(COALESCE(remark, ''), '；R5 已下线：改为各环节页面顶部的流程指引线'),
       update_time = NOW()
 WHERE menu_id = 1767000000000000107
   AND menu_type = 'C'
   AND status <> '1';

-- 2) 核对：子菜单现状（向导应为停用，视觉项目 order_num 最小且启用）
SELECT menu_id, menu_name, path, component, order_num, status
  FROM sys_menu
 WHERE parent_id = 1767000000000000001
 ORDER BY order_num, menu_id;

SELECT COUNT(*) AS enabled_wizard_rows
  FROM sys_menu
 WHERE menu_id = 1767000000000000107 AND status = '0';

SELECT 'DP_CREATIVE_R5_MENU_DONE' AS marker;
