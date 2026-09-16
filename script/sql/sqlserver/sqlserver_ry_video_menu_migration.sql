-- SQL Server: only for databases that already imported sqlserver_ry_ai_studio_menu.sql.
BEGIN TRANSACTION;

UPDATE sys_menu
SET component = N'video/index', perms = N'video:creation:view', remark = N'视频创作工作台'
WHERE menu_id = 920000 AND component = N'ai/studio/index' AND perms = N'ai:studio:view';

UPDATE sys_menu
SET perms = N'video:creation:submit'
WHERE menu_id = 920001 AND parent_id = 920000 AND perms = N'ai:studio:submit';

SELECT menu_id, parent_id, path, component, perms FROM sys_menu WHERE menu_id IN (920000, 920001);
-- COMMIT TRANSACTION; execute only after verifying the selected rows.
-- ROLLBACK TRANSACTION; use if the rows are unexpected.
