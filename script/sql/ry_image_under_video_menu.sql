-- ============================================================================
-- 把「图像创作」挂到「视频创作」目录下
--
-- 目标结构（AI工具 1764000000000000002 之下）：
--   视频创作（920000，目录 M，path=video-creation）
--     ├── 视频创作（920010，页面 C，path=video，component=video/index）  ← 原 920000 的页面
--     │     └── 提交视频任务（920001，按钮 F，video:creation:submit）
--     └── 图像创作（920002，页面 C，path=image，component=image/index）
--           └── 提交图像任务（920003，按钮 F，image:creation:submit）
--
-- 路由变化（嵌套是 RuoYi 的必然结果：菜单要挂到某目录下，目录本身不可点）：
--   视频创作：/ai-tools/video-creation         → /ai-tools/video-creation/video
--   图像创作：/ai-tools/image-creation         → /ai-tools/video-creation/image
--
-- 幂等：所有语句都带条件或 insert ignore，可重复执行；不删除任何菜单。
-- 用法：mysql -u<user> -p -D ai_video_poc < ry_image_under_video_menu.sql
-- 前置：先备份 sys_menu / sys_role_menu
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、视频创作(920000) 由页面改为目录：清掉 component 与 perms，路径/名称/图标不变
-- ---------------------------------------------------------------------------
update sys_menu
   set menu_type = 'M', component = null, perms = '', order_num = 1
 where menu_id = 920000
   and (menu_type <> 'M' or component is not null or perms <> '');

-- ---------------------------------------------------------------------------
-- 二、新增「视频创作」页面子菜单（承接原 920000 的组件与权限码）
-- ---------------------------------------------------------------------------
insert ignore into sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
values
  (920010, '视频创作', 920000, 1, 'video', 'video/index', '', 'N', 'N', 'C', '0', '0',
   'video:creation:view', 'caret-forward', '', '', 1761000000000000103, 1761100000000000001, sysdate(),
   '视频创作工作台（原 920000；因图像创作要挂到该目录下，页面下沉为子菜单）');

-- ---------------------------------------------------------------------------
-- 三、按钮归位：提交视频任务 → 视频创作页面之下
-- ---------------------------------------------------------------------------
update sys_menu set parent_id = 920010, order_num = 1
 where menu_id = 920001 and parent_id <> 920010;

-- ---------------------------------------------------------------------------
-- 四、图像创作挂到视频创作目录下（保持自身组件与权限码不变，只改父级与相对路径）
-- ---------------------------------------------------------------------------
update sys_menu set parent_id = 920000, order_num = 2, path = 'image'
 where menu_id = 920002 and (parent_id <> 920000 or path <> 'image');

-- ---------------------------------------------------------------------------
-- 五、授权：新菜单 920010 继承 920000 现有授权（谁原本能看到视频创作，就还能看到）
-- ---------------------------------------------------------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select distinct role_id, 920010 from sys_role_menu where menu_id = 920000;

-- ---------------------------------------------------------------------------
-- 六、自检
-- ---------------------------------------------------------------------------
select '视频创作目录下' as label, menu_id, menu_name, menu_type, path, component, perms, order_num
  from sys_menu where parent_id = 920000 order by order_num;

select '按钮归属' as label, menu_id, menu_name, parent_id, perms
  from sys_menu where menu_id in (920001, 920003);

select '授权' as label, r.role_key, m.menu_id, m.menu_name
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.menu_id in (920000, 920001, 920002, 920003, 920010)
 order by r.role_key, m.menu_id;
