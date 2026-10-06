-- ============================================================================
-- 历史候选的「耗时」补回来（v1 人工测试反馈：AI 生产中心 1.2「提交时间没按实际时间」）
-- ----------------------------------------------------------------------------
-- 背景（这条的根因与修法）：
--   `dp_generation.duration_ms` 是**从内核任务的起止时间算出来的**（`CreativeGenerationServiceImpl#durationOf`），
--   而内核状态是"列表刷新时"去问的（`refreshRows` → `applyKernelState`）。
--   改造前那次内核查询少选了 `started_time`，于是算不出耗时，页面上每个候选的「耗时」都是「—」
--   （这就是反馈里那条"没按实际时间"）。查询已修（那次修复后新出的候选能显示真实耗时），
--   **但修复之前产生的候选库里 `duration_ms` 一直是 NULL**——列表只刷新"未结束"的候选，
--   已结束的历史候选再也不会被问一次，于是它们的「—」永远不会变成数字。
--
-- 为什么可以补：内核任务还在（`image_task` 保存了 `started_time` / `finished_time`），
--   这是**当时真实的起止时间**，不是估算也不是编造。补法就是照代码里的口径算一遍：
--       duration_ms = (finished_time - started_time) 毫秒
--   （时间差，所以时区差异不影响结果。）
--
-- 为什么放在 SQL 里做而不是改代码：**新候选这条路已经是好的**（状态与起止时间是同一次内核查询拿回来的，
--   一起落库）。缺的只是"改动之前留下的历史行"，那是一次性的数据补齐，不是每次列表都要重算的东西
--   （放进读路径会让每次列表都多打一轮内核，而结果永远不变）。
--
-- 幂等：只补 `duration_ms IS NULL` 的行；重复执行不会改动已补好的值。
-- 回滚见文件末尾。
-- ============================================================================

-- 1) 补：内核有完整起止时间、而候选这一行没记上耗时的
UPDATE dp_generation g
  JOIN image_task t ON t.id = g.image_task_id
   SET g.duration_ms = TIMESTAMPDIFF(MICROSECOND, t.started_time, t.finished_time) DIV 1000
 WHERE g.del_flag = '0'
   AND g.duration_ms IS NULL
   AND g.image_task_id IS NOT NULL
   AND t.started_time IS NOT NULL
   AND t.finished_time IS NOT NULL;

-- 先把影响行数收进变量：ROW_COUNT() 会被下一条 SELECT 重置（第一版就是在这里显示了 -1）
SET @patched := ROW_COUNT();

SELECT '=== 本次补了多少行（重复执行应为 0）===' AS s;
SELECT @patched AS patched_rows;

-- 2) 核对：候选的耗时与内核起止时间是否对得上
SELECT '=== 候选耗时 vs 内核起止时间 ===' AS s;
SELECT g.id AS generation_id, g.status, g.duration_ms,
       t.started_time, t.finished_time,
       TIMESTAMPDIFF(MICROSECOND, t.started_time, t.finished_time) DIV 1000 AS kernel_ms,
       (g.duration_ms = TIMESTAMPDIFF(MICROSECOND, t.started_time, t.finished_time) DIV 1000) AS matches
  FROM dp_generation g
  LEFT JOIN image_task t ON t.id = g.image_task_id
 WHERE g.del_flag = '0'
 ORDER BY g.id;

-- 3) 剩下补不了的（内核没有起止时间 / 没有内核任务 id）：如实留着 NULL——
--    页面继续显示「—」，那是"不知道"，不是 0 秒。
SELECT '=== 仍然拿不到耗时的候选（期望 0 行，本机）===' AS s;
SELECT g.id, g.status, g.image_task_id,
       CASE WHEN g.image_task_id IS NULL THEN '没有内核任务ID'
            WHEN t.id IS NULL THEN '内核任务已不存在'
            ELSE '内核没记起止时间' END AS reason
  FROM dp_generation g
  LEFT JOIN image_task t ON t.id = g.image_task_id
 WHERE g.del_flag = '0' AND g.duration_ms IS NULL;

-- ---------------------------------------------------------------------------
-- 回滚（把这次补出来的值还原成 NULL —— 改动前的状态就是 NULL）
-- ---------------------------------------------------------------------------
-- UPDATE dp_generation SET duration_ms = NULL
--  WHERE id IN (2104583461734440961, 2104584202956038146);
