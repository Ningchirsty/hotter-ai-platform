-- Oracle: only for databases that already imported oracle_ry_ai_studio_menu.sql.
-- Review the selected rows before committing the session.
UPDATE sys_menu
SET component = 'video/index', perms = 'video:creation:view', remark = '视频创作工作台'
WHERE menu_id = 920000 AND component = 'ai/studio/index' AND perms = 'ai:studio:view';

UPDATE sys_menu
SET perms = 'video:creation:submit'
WHERE menu_id = 920001 AND parent_id = 920000 AND perms = 'ai:studio:submit';

SELECT menu_id, parent_id, path, component, perms FROM sys_menu WHERE menu_id IN (920000, 920001);
-- COMMIT; execute only after verifying the selected rows.
