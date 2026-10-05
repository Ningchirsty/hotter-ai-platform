-- ============================================================================
-- 步骤配置：「锁定」＝这一步做完了（v1 人工测试反馈：锁定后仍显示「进行中」、页面也不推进）
-- ----------------------------------------------------------------------------
-- 现象（本地真机复现，task 2104582766641799169）：
--   锁定分镜成功后，分镜本身确实变成「已锁定」，但页面上的步骤条仍然是
--   「2. 分镜 **进行中**」「3. 出图 前置未完成」，页面也不转到下一步——
--   看起来就像"锁定没生效"。
--
-- 根因（不是刷新问题）：`dp_scenario_step.stage_codes` 把每个步骤**自己的锁定阶段**
-- 也算进了该步：
--   DNA        → DNA_GENERATING,DNA_REVIEW,**DNA_LOCKED**
--   DIRECTION  → DIRECTION_GENERATING,DIRECTION_REVIEW,**DIRECTION_LOCKED**
--   STORYBOARD → STORYBOARD_GENERATING,STORYBOARD_REVIEW,**STORYBOARD_LOCKED**
--   GATE       → VISUAL_GATE,**VISUAL_LOCKED**
-- 而 `CreativeStepProjection` 的规则是「当前阶段在该步的 stage_codes 里 → ACTIVE」，
-- 于是"锁定"这个动作**不会**把该步判成 DONE，页面自然也不推进。
--
-- 为什么现在改：这条规则本身没问题（"锁定了"确实还停在这一步上），
-- 但产品口径要的是「锁定/确认 ＝ 这一步做完」。改的是**配置**（哪些阶段属于哪一步），
-- 投影规则与代码一行不动 —— 与"等级只写在配置里"是同一种做法。
--
-- ⚠️ 影响面（照着说，不缩小）：
--   1. 4 个步骤的"完成时机"各提前一个阶段：基因 / 方向 / 分镜 / 视觉门；
--      所有页面的步骤条与流程指引的"当前步"判定都会跟着变（这正是目的）；
--   2. **副作用**：刚锁定完那一步、下一步还没开始时，**没有任何一步是 ACTIVE**
--      （流程指引的"当前步"高亮会空一拍，页面显示的是"第一个还没了结的步骤"）。
--      这一点已被 `CreativeStepProjectionTest` 显式钉住，属于有意行为；
--   3. 三种交付类型（ECOM_DETAIL / MAIN_IMAGE / BRAND_POSTER）一起改——步骤语义是共用的，
--      只改一种会让不同交付类型的同一步行为不一致。
--      （前两版只做成了"按步骤名匹配"，于是 11 行里漏了 1 行，见下。）
--
-- ⚠️ 覆盖范围的坑（复核时补上）：海报交付类型里"分镜那一步"叫 **POSTER_CONCEPT**（不是
--   STORYBOARD），于是只按 step_code='STORYBOARD' 匹配会漏掉它——海报项目锁定分镜后，
--   「海报概念与主视觉」这一步仍会显示"进行中"，正是本文件要修的那个现象（只是换了个步骤名）。
--   所以下面第 5 条单独补上；核对也改成**不按步骤名过滤**的通用断言——
--   "步骤改名"不该让断言失明（本次就是断言按名字过滤才漏掉的）。
--   执行前/执行后共用同一条查询，期望值从「11 行」变成「空」。
--
--   11 行是怎么来的：三份种子各插了自己交付类型的步骤
--   （`dp_creative_r11_scenario_foundation.sql` 的 ECOM_DETAIL 4 行
--    + `dp_creative_r21_main_image_scenario.sql` 的 MAIN_IMAGE 4 行
--    + `dp_creative_r46_brand_poster.sql` 的 BRAND_POSTER 3 行 = 11），
--   是按种子逐行数出来的，不是估的。本机实例上用"先按回滚段还原成种子状态 → 跑本文件 → 核对"
--   的往返方式验过（执行前 11 行、执行后空）。
--
-- 幂等：只按"旧的完整值"匹配；已经改过就不再动。
-- 回滚见文件末尾。
-- ============================================================================

-- 1) 执行前：所有仍把"自己的锁定阶段"算进本步的步骤（不按步骤名过滤，避免改名漏网）
SELECT '=== 1) 执行前：把 *_LOCKED 算进本步的步骤（期望 11 行）===' AS s;
SELECT p.delivery_type, s.step_code, s.sort_no, s.stage_codes
  FROM dp_scenario_step s
  JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE s.del_flag = '0'
   AND (s.stage_codes LIKE '%DNA_LOCKED%'
     OR s.stage_codes LIKE '%DIRECTION_LOCKED%'
     OR s.stage_codes LIKE '%STORYBOARD_LOCKED%'
     OR s.stage_codes LIKE '%VISUAL_LOCKED%')
 ORDER BY p.delivery_type, s.sort_no;

-- 2) 把各自的锁定阶段摘掉（锁定＝该步完成）
UPDATE dp_scenario_step
   SET stage_codes = 'DNA_GENERATING,DNA_REVIEW', update_time = sysdate()
 WHERE del_flag = '0' AND step_code = 'DNA'
   AND stage_codes = 'DNA_GENERATING,DNA_REVIEW,DNA_LOCKED';

UPDATE dp_scenario_step
   SET stage_codes = 'DIRECTION_GENERATING,DIRECTION_REVIEW', update_time = sysdate()
 WHERE del_flag = '0' AND step_code = 'DIRECTION'
   AND stage_codes = 'DIRECTION_GENERATING,DIRECTION_REVIEW,DIRECTION_LOCKED';

UPDATE dp_scenario_step
   SET stage_codes = 'STORYBOARD_GENERATING,STORYBOARD_REVIEW', update_time = sysdate()
 WHERE del_flag = '0' AND step_code = 'STORYBOARD'
   AND stage_codes = 'STORYBOARD_GENERATING,STORYBOARD_REVIEW,STORYBOARD_LOCKED';

UPDATE dp_scenario_step
   SET stage_codes = 'VISUAL_GATE', update_time = sysdate()
 WHERE del_flag = '0' AND step_code = 'GATE'
   AND stage_codes = 'VISUAL_GATE,VISUAL_LOCKED';

-- 5) 海报交付类型：这一步叫 POSTER_CONCEPT（同一语义、不同步骤名），必须与 STORYBOARD 同改
UPDATE dp_scenario_step
   SET stage_codes = 'STORYBOARD_GENERATING,STORYBOARD_REVIEW', update_time = sysdate()
 WHERE del_flag = '0' AND step_code = 'POSTER_CONCEPT'
   AND stage_codes = 'STORYBOARD_GENERATING,STORYBOARD_REVIEW,STORYBOARD_LOCKED';

-- 3) 执行后核对（期望：一行都不剩）
SELECT '=== 3) 执行后：把 *_LOCKED 算进本步的步骤（期望空）===' AS s;
SELECT p.delivery_type, s.step_code, s.stage_codes
  FROM dp_scenario_step s
  JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE s.del_flag = '0'
   AND (s.stage_codes LIKE '%DNA_LOCKED%'
     OR s.stage_codes LIKE '%DIRECTION_LOCKED%'
     OR s.stage_codes LIKE '%STORYBOARD_LOCKED%'
     OR s.stage_codes LIKE '%VISUAL_LOCKED%');

-- ---------------------------------------------------------------------------
-- 回滚（把锁定阶段写回各自步骤 = 恢复"锁定后仍显示进行中"）
-- ---------------------------------------------------------------------------
-- UPDATE dp_scenario_step SET stage_codes='DNA_GENERATING,DNA_REVIEW,DNA_LOCKED'
--  WHERE del_flag='0' AND step_code='DNA' AND stage_codes='DNA_GENERATING,DNA_REVIEW';
-- UPDATE dp_scenario_step SET stage_codes='DIRECTION_GENERATING,DIRECTION_REVIEW,DIRECTION_LOCKED'
--  WHERE del_flag='0' AND step_code='DIRECTION' AND stage_codes='DIRECTION_GENERATING,DIRECTION_REVIEW';
-- UPDATE dp_scenario_step SET stage_codes='STORYBOARD_GENERATING,STORYBOARD_REVIEW,STORYBOARD_LOCKED'
--  WHERE del_flag='0' AND step_code='STORYBOARD' AND stage_codes='STORYBOARD_GENERATING,STORYBOARD_REVIEW';
-- UPDATE dp_scenario_step SET stage_codes='VISUAL_GATE,VISUAL_LOCKED'
--  WHERE del_flag='0' AND step_code='GATE' AND stage_codes='VISUAL_GATE';
-- UPDATE dp_scenario_step SET stage_codes='STORYBOARD_GENERATING,STORYBOARD_REVIEW,STORYBOARD_LOCKED'
--  WHERE del_flag='0' AND step_code='POSTER_CONCEPT' AND stage_codes='STORYBOARD_GENERATING,STORYBOARD_REVIEW';
