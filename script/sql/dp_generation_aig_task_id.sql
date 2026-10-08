-- ------------------------------------------------------------------
-- 创作域：生成记录补「治理层统一任务ID」列（dp_generation.aig_task_id）
--
-- 背景（设计 §9「新任务走 aig_task，dp_generation 只做只读镜像」的起步形态，
--   用户 2026-10-08 拍定＝「登记 + 状态回写」，粒度＝一候选一任务）：
--   创作域发起出图时，把这次工作**登记**成一条 aig_task（execution_mode=EXTERNAL：
--   编排留在图像内核，平台不执行也不扫描），好让治理台看到的是**活任务**而不是只读镜像。
--   登记之后还要**回写**（内核状态变化、人工选定/否决），回写必须能定位到那条任务。
--
-- 为什么把任务号记在候选行上，而不是每次按幂等键去查：
--   aig_task 的创建幂等键是 (project_type, create_by, idempotency_key)，其中 create_by
--   取**当次登录态**。而回写发生在**刷新线程**（可能是定时/系统上下文，没有登录用户）
--   → 同一个幂等键在查询时会落到 SYSTEM 哨兵上，**查不到**当初那个人登记的那条。
--   把任务号写在候选行上就没有这个问题：它是事实，不是靠复算幂等键猜出来的。
--   而且查询侧也只支持按 projectType/projectId 过滤——同一项目下有多个候选、定位不到唯一一条。
--
-- 语义：为空 = 该候选未登记治理任务（本列上线前的历史候选，或登记失败时如实为空）。
--
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema 判断后动态执行
--   DDL（写法与 aig_task_execution_mode.sql / aig_invoke_agent_version.sql 一致）。
--
-- 兼容性：只加一列可空、不改既有列、不动索引、不动既有数据。
-- 注意：script/sql/dp_creative.sql（建表脚本）里也已加上这一列，全新库跑建表脚本即可；
--   本脚本是给**存量库**补齐的（可重复执行）。
-- ------------------------------------------------------------------

SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dp_generation'
        AND COLUMN_NAME = 'aig_task_id'),
    'SELECT ''dp_generation.aig_task_id already exists'' AS note',
    'ALTER TABLE dp_generation ADD COLUMN aig_task_id BIGINT NULL COMMENT ''治理层统一任务ID（aig_task.task_id）：本次出图登记的那条任务，执行方=业务域（EXTERNAL）'' AFTER image_task_id')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 核对：列存在
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'dp_generation'
   AND COLUMN_NAME = 'aig_task_id';
