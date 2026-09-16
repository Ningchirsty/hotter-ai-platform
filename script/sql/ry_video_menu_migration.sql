-- MySQL: only for databases that already imported ry_ai_studio_menu.sql.
-- Keep menu IDs and role-menu assignments. Execute in one session and review
-- the selected rows before choosing COMMIT or ROLLBACK.
START TRANSACTION;

UPDATE sys_menu
SET component = 'video/index', perms = 'video:creation:view', remark = '视频创作工作台'
WHERE menu_id = 920000 AND component = 'ai/studio/index' AND perms = 'ai:studio:view';

UPDATE sys_menu
SET perms = 'video:creation:submit'
WHERE menu_id = 920001 AND parent_id = 920000 AND perms = 'ai:studio:submit';

SELECT menu_id, parent_id, path, component, perms FROM sys_menu WHERE menu_id IN (920000, 920001);
-- COMMIT; execute only after verifying the selected rows.
-- ROLLBACK; use if the rows are unexpected.

-- Optional only if ai_workflow_version exists, video_workflow_version does not,
-- and its data belongs to this video module. Back up and check dependent objects first.
-- SELECT table_name FROM information_schema.tables
--   WHERE table_schema = DATABASE() AND table_name IN ('ai_workflow_version', 'video_workflow_version');
-- RENAME TABLE ai_workflow_version TO video_workflow_version;
-- Existing workflow_path values beginning with workflows/api/ also need the
-- video/ prefix after checking that they point to these migrated templates.
