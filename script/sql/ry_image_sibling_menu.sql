-- ============================================================================
-- 图像创作：改为「视频创作」的同级菜单，并补齐图标
--
-- 背景：上一版迁移（ry_image_under_video_menu.sql，已删除）把 920000 视频创作 改成了目录（M），
-- 把 920002 图像创作 挂到它下面，形成「视频创作 → 视频创作 / 图像创作」的重复层级。
-- 验收反馈要求：图像创作与视频创作**同级**（都挂在 AI工具 下），且图像创作要有图标。
--
-- 结构（目标）：
--   AI工具(1764000000000000002)
--     ├─ 视频创作(920000)   C  video-creation   video/index   video:creation:view
--     │    └─ 提交视频任务(920001) F
--     ├─ 图像创作(920002)   C  image-creation   image/index   image:creation:view   icon=image
--     │    └─ 提交图像任务(920003) F
--     ├─ AI会话 / 工作流（顺序后移一位）
--
-- 图标：侧栏用 assets/icons/svg/*.svg 的**文件名**（<svg-icon :icon-class>），
--       原先写的 "picture" 不在图标集里 → 渲染为空白；本仓库新增 image.svg，故 icon='image'。
--
-- 幂等：可重复执行；重复执行不会再移动 order_num。
-- ============================================================================

SET @ai_tools     := 1764000000000000002;
SET @video        := 920000;
SET @video_page   := 920010;   -- 上一版新增的嵌套页面，本版删除
SET @image        := 920002;
SET @video_submit := 920001;
SET @image_submit := 920003;

-- 1) 视频创作恢复为页面（C），沿用原始 path/component/perms
UPDATE sys_menu
   SET menu_type = 'C',
       parent_id = @ai_tools,
       order_num = 1,
       path      = 'video-creation',
       component = 'video/index',
       perms     = 'video:creation:view',
       visible   = '0',
       status    = '0'
 WHERE menu_id = @video;

-- 2) 嵌套页面 920010 的下属按钮先回到 920000，再删除 920010 及其角色授权
UPDATE sys_menu SET parent_id = @video WHERE parent_id = @video_page;
DELETE FROM sys_role_menu WHERE menu_id = @video_page;
DELETE FROM sys_menu       WHERE menu_id = @video_page;

-- 3) 图像创作改为 AI工具 下的同级页面，并给图标
SET @already := (SELECT COUNT(*) FROM sys_menu WHERE menu_id = @image AND parent_id = @ai_tools);
UPDATE sys_menu
   SET order_num = order_num + 1
 WHERE parent_id = @ai_tools AND order_num >= 2 AND @already = 0;

UPDATE sys_menu
   SET parent_id = @ai_tools,
       order_num = 2,
       path      = 'image-creation',
       component = 'image/index',
       perms     = 'image:creation:view',
       menu_type = 'C',
       icon      = 'image',
       visible   = '0',
       status    = '0'
 WHERE menu_id = @image;

-- 4) 按钮归属校正
UPDATE sys_menu SET parent_id = @video WHERE menu_id = @video_submit;
UPDATE sys_menu SET parent_id = @image WHERE menu_id = @image_submit;

-- 5) 授权对齐：把图像创作及其按钮补授予「视频创作已有的那些角色」（缺则补，幂等）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @image
  FROM sys_role_menu rm
 WHERE rm.menu_id = @video
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = @image);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @image_submit
  FROM sys_role_menu rm
 WHERE rm.menu_id = @video
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = @image_submit);

-- 6) 结果核对
SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, icon, perms
  FROM sys_menu
 WHERE parent_id = @ai_tools OR menu_id IN (@video, @image, @video_submit, @image_submit)
 ORDER BY parent_id, order_num;

SELECT rm.menu_id, m.menu_name, COUNT(*) AS roles
  FROM sys_role_menu rm LEFT JOIN sys_menu m ON m.menu_id = rm.menu_id
 WHERE rm.menu_id IN (@video, @video_submit, @image, @image_submit)
 GROUP BY rm.menu_id, m.menu_name;
