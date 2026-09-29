-- ------------------------------------------------------------------
-- V0.2 / R14：D2 —— 项目步骤状态 dp_project_step_state
--
-- 依据：文档 §33（阶段模型改造）与已确认的状态纪律（对照文档 D2）。三条纪律写在这里，改代码时不要破：
--   1) **单一写入点**：本表只由 CreativeProjectServiceImpl#moveStage 经 CreativeStepStateWriter 写；
--      查询路径（GET /creative/v2/projects/{taskId}/steps）**永不写库**，没持久化就按当前阶段推导投影。
--   2) **visual_stage 是兼容投影**：cp_task.visual_stage 仍是阶段与合法性的权威（canMoveTo 在代码里），
--      本表是它的派生视图，用于"这一步做到哪了"的可读展示与后续工作台。
--   3) **同时写事件**：每次阶段变更仍写 dp_stage_event（moveStage 里原有那一行），
--      本表行上的 stage_code 记录"是哪次阶段变更把它推到这个状态的"，便于与事件对账。
--
-- 为什么派生而非独立事实：本表可由 dp_stage_event + dp_scenario_step 重算出来；
-- 一旦与事件对不上，以事件为准重建即可（不制造第二个真相源）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS dp_project_step_state (
  id           BIGINT       NOT NULL COMMENT '主键（雪花ID）',
  task_id      BIGINT       NOT NULL COMMENT '视觉项目（cp_task.task_id）',
  profile_id   BIGINT       NULL     COMMENT '当时的场景档案（dp_scenario_profile.id，便于换档案后溯源）',
  step_code    VARCHAR(64)  NOT NULL COMMENT '步骤编码（对应 dp_scenario_step.step_code）',
  step_name    VARCHAR(128) NULL     COMMENT '步骤名称（快照，便于档案改名后仍可读）',
  status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING待办/ACTIVE进行中/DONE已完成/SKIPPED已跳过）',
  sort_no      INT          NOT NULL DEFAULT 0 COMMENT '顺序（快照）',
  stage_code   VARCHAR(40)  NULL     COMMENT '触发本次状态变化的阶段（对应 dp_stage_event.to_stage）',
  started_at   DATETIME     NULL     COMMENT '进入进行中的时间',
  completed_at DATETIME     NULL     COMMENT '完成时间',
  remark       VARCHAR(500) NULL,
  create_dept  BIGINT       NULL,
  create_by    BIGINT       NULL,
  create_time  DATETIME     NULL,
  update_by    BIGINT       NULL,
  update_time  DATETIME     NULL,
  del_flag     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dp_project_step (task_id, step_code),
  KEY idx_dp_project_step_task (task_id, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI视觉工厂·项目步骤状态（moveStage 唯一写入）';

SELECT 'DP_CREATIVE_R14_STEP_STATE_DONE' AS marker;
