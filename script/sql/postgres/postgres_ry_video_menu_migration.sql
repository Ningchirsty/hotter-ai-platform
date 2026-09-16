-- PostgreSQL: only for databases that already imported postgres_ry_ai_studio_menu.sql.
-- Execute in one session and review the selected rows before committing.
BEGIN;

UPDATE sys_menu
SET component = 'video/index', perms = 'video:creation:view', remark = '视频创作工作台'
WHERE menu_id = 920000 AND component = 'ai/studio/index' AND perms = 'ai:studio:view';

UPDATE sys_menu
SET perms = 'video:creation:submit'
WHERE menu_id = 920001 AND parent_id = 920000 AND perms = 'ai:studio:submit';

SELECT menu_id, parent_id, path, component, perms FROM sys_menu WHERE menu_id IN (920000, 920001);
-- COMMIT; execute only after verifying the selected rows.
-- ROLLBACK; use if the rows are unexpected.
