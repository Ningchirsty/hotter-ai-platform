-- ============================================================================
-- 品牌要求在**所有交付类型**上一致 —— 内测冲突 A + 冲突 F 的落地
-- ----------------------------------------------------------------------------
-- 定案（来自内测决策）：
--
--   A) 同一条产线的同一个根因，**只留一道硬拦**。本产线只有两个根因：
--      ① 设计侧准备没做完 → DNA_LOCKED + REFERENCE_IMAGE（本来就是硬拦，出图的输入，
--         缺了根本渲不出来，本质是一件事的两半）；
--      ② 品牌部功课没做完 → **只有** BRAND_BRIEF_CONFIRMED 一道硬拦。
--         FORBIDDEN_WORDS_DECLARED / BRAND_TONE_CONFIRMED 保持建议级：
--         它们是「品牌 Brief 已确认」的细项，各自再设一道硬拦＝对同一根因拦第二次，
--         使用者会在同一个门上被同一件事拒两次。
--
--   F) 这道硬拦的适用性**不得取决于交付类型**。原种子 GATE_MAIN_IMAGE_V1 的备注明写
--      「R21 种子：主图闸门项比详情页少（不做品牌 Brief 与禁用词两项）」，
--      于是把任务改成主图就能绕开品牌要求（不是"改需求"，是同一套工作换个名字过门）。
--      本脚本把所有已发布档案补齐并统一等级。
--
-- ⚠️ 本脚本会**改变现有项目的门禁结论**：品牌 Brief 未确认的项目将不能提交视觉门。
--    执行前必须先看 §1.2 的存量影响清单——这是本脚本唯一的"有感知"后果。
--    若业务上不能立刻接受，先让品牌部补齐 Brief，再执行 §2/§3。
--
-- ⚠️ 代码侧 DEFAULT_ITEMS 已同步为 BLOCK（CreativeGateServiceImpl），两处必须一致：
--    有档案的交付类型以**档案里的 level 为准**，没有档案的回落代码清单。
--    只改一处 = 同一件要求在不同交付类型上时紧时松，那正是本次要修的形状。
--
-- 幂等：INSERT IGNORE + NOT EXISTS 守卫 + 条件 UPDATE，可重复执行。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 执行前核对（只读）
-- ---------------------------------------------------------------------------
SELECT '=== 1.1 各交付类型的品牌项现状 ===' AS s;
SELECT p.delivery_type, p.profile_code, p.version, p.status,
       MAX(CASE WHEN i.item_code = 'BRAND_BRIEF_CONFIRMED'
                THEN i.level END) AS brief_level,
       MAX(CASE WHEN i.item_code = 'FORBIDDEN_WORDS_DECLARED'
                THEN i.level END) AS forbidden_level,
       COUNT(i.id) AS item_count
  FROM dp_gate_profile p
  LEFT JOIN dp_gate_item i ON i.profile_id = p.id AND i.del_flag = '0'
 WHERE p.del_flag = '0' AND p.status = 'PUBLISHED'
 GROUP BY p.id, p.delivery_type, p.profile_code, p.version, p.status
 ORDER BY p.delivery_type;

-- 1.2 存量影响：执行后会被这道新硬拦挡住的现有项目（这是唯一的"有感知"后果）
SELECT '=== 1.2 品牌 Brief 未确认的项目（将不能提交视觉门）===' AS s;
SELECT t.task_id, t.task_no, t.task_name, t.deliverable_type, t.status,
       t.visual_stage,
       CASE WHEN b.id IS NULL THEN '(从未填写)' ELSE b.status END AS brief_status,
       b.confirmed_at
  FROM cp_task t
  LEFT JOIN dp_brand_brief b ON b.task_id = t.task_id AND b.del_flag = '0'
 WHERE b.id IS NULL OR b.status <> 'CONFIRMED'
 ORDER BY t.create_time DESC;

-- ---------------------------------------------------------------------------
-- 2) 补齐：给缺品牌项的已发布档案补上这两项
--    id 取 profile_id + 500 / 501（与种子里的 10~2xx 偏移量拉开，且**按档案确定**，
--    所以重复执行会命中同一个主键 → INSERT IGNORE 天然幂等）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO dp_gate_item
    (id, profile_id, item_code, item_label, level, sort_no, remark, create_dept, create_by, create_time)
SELECT p.id + 500, p.id, 'BRAND_BRIEF_CONFIRMED', '品牌 Brief 已填写并确认', 'BLOCK', 60,
       '内测冲突F：品牌要求不得随交付类型消失；冲突A：这是品牌部功课的唯一硬拦',
       1761000000000000103, 1761100000000000001, sysdate()
  FROM dp_gate_profile p
 WHERE p.del_flag = '0' AND p.status = 'PUBLISHED'
   AND NOT EXISTS (SELECT 1 FROM dp_gate_item i
                    WHERE i.profile_id = p.id AND i.item_code = 'BRAND_BRIEF_CONFIRMED'
                      AND i.del_flag = '0');

INSERT IGNORE INTO dp_gate_item
    (id, profile_id, item_code, item_label, level, sort_no, remark, create_dept, create_by, create_time)
SELECT p.id + 501, p.id, 'FORBIDDEN_WORDS_DECLARED', '已声明禁用词与合规红线', 'CONDITION', 70,
       '建议级：它是「品牌 Brief 已确认」的细项，不再单设硬拦（冲突A 只留一道）',
       1761000000000000103, 1761100000000000001, sysdate()
  FROM dp_gate_profile p
 WHERE p.del_flag = '0' AND p.status = 'PUBLISHED'
   AND NOT EXISTS (SELECT 1 FROM dp_gate_item i
                    WHERE i.profile_id = p.id AND i.item_code = 'FORBIDDEN_WORDS_DECLARED'
                      AND i.del_flag = '0');

-- ---------------------------------------------------------------------------
-- 3) 统一等级（含"已存在但等级不对"的档案）
--    备注采用**追加**而不是覆盖：原备注里记着判据（如"判据是 status=CONFIRMED，光填过不算"），
--    那是排查时的第一手说明，不该被这次变更冲掉。
-- ---------------------------------------------------------------------------
-- 3.1 品牌 Brief = 唯一硬拦
UPDATE dp_gate_item
   SET level = 'BLOCK',
       remark = CONCAT(COALESCE(remark, ''), ' ｜内测冲突A：本项是品牌部功课的唯一硬拦，失败即不能提交视觉门'),
       update_time = sysdate()
 WHERE item_code = 'BRAND_BRIEF_CONFIRMED' AND del_flag = '0' AND level <> 'BLOCK';

-- 3.2 细项一律建议级（防止有人已把某一项提成 BLOCK，那就成了第二道硬拦）
UPDATE dp_gate_item
   SET level = 'CONDITION',
       remark = CONCAT(COALESCE(remark, ''), ' ｜内测冲突A：本项是「品牌 Brief 已确认」的细项，不再单设硬拦'),
       update_time = sysdate()
 WHERE item_code IN ('FORBIDDEN_WORDS_DECLARED', 'BRAND_TONE_CONFIRMED')
   AND del_flag = '0' AND level <> 'CONDITION';

-- ---------------------------------------------------------------------------
-- 4) 执行后核对（期望：每个已发布档案的 brief_level 都是 BLOCK，forbidden 都是 CONDITION）
-- ---------------------------------------------------------------------------
SELECT '=== 4.1 执行后：品牌项等级 ===' AS s;
SELECT p.delivery_type, p.profile_code,
       MAX(CASE WHEN i.item_code = 'BRAND_BRIEF_CONFIRMED' THEN i.level END) AS brief_level,
       MAX(CASE WHEN i.item_code = 'FORBIDDEN_WORDS_DECLARED' THEN i.level END) AS forbidden_level
  FROM dp_gate_profile p
  LEFT JOIN dp_gate_item i ON i.profile_id = p.id AND i.del_flag = '0'
 WHERE p.del_flag = '0' AND p.status = 'PUBLISHED'
 GROUP BY p.id, p.delivery_type, p.profile_code
 ORDER BY p.delivery_type;

-- 4.2 仍缺品牌项的档案（**含未发布的草稿档案**，期望：空）
--    查全部档案而不是只查 PUBLISHED：草稿档案今天不影响门禁，但它**明天会被发布**——
--    等发布了才发现缺项，绕过路径就又回来了。把它当例行检查跑。
SELECT '=== 4.2 仍缺品牌项的档案（含草稿；期望为空）===' AS s;
SELECT p.delivery_type, p.profile_code, p.version, p.status
  FROM dp_gate_profile p
 WHERE p.del_flag = '0'
   AND NOT EXISTS (SELECT 1 FROM dp_gate_item i
                    WHERE i.profile_id = p.id AND i.item_code = 'BRAND_BRIEF_CONFIRMED'
                      AND i.del_flag = '0')
 ORDER BY p.status, p.delivery_type;

-- 4.3 例行核对：全部已发布档案的品牌项等级（期望：brief=BLOCK，forbidden=CONDITION）
SELECT '=== 4.3 全部档案的品牌项等级 ===' AS s;
SELECT p.delivery_type, p.profile_code, p.status,
       MAX(CASE WHEN i.item_code = 'BRAND_BRIEF_CONFIRMED' THEN i.level END) AS brief_level,
       MAX(CASE WHEN i.item_code = 'FORBIDDEN_WORDS_DECLARED' THEN i.level END) AS forbidden_level
  FROM dp_gate_profile p
  LEFT JOIN dp_gate_item i ON i.profile_id = p.id AND i.del_flag = '0'
 WHERE p.del_flag = '0'
 GROUP BY p.id, p.delivery_type, p.profile_code, p.status
 ORDER BY p.delivery_type, p.version;

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------
-- 回滚第二步（只删本次新增的行，id = profile_id + 500/501）
-- DELETE FROM dp_gate_item WHERE id IN (
--   SELECT id FROM (
--     SELECT p.id + 500 AS id FROM dp_gate_profile p
--      UNION ALL SELECT p.id + 501 FROM dp_gate_profile p) x);
-- 回滚第三步（等级退回建议级）
-- UPDATE dp_gate_item SET level = 'CONDITION', update_time = sysdate()
--  WHERE item_code = 'BRAND_BRIEF_CONFIRMED' AND del_flag = '0';
-- ⚠️ 回滚 SQL 之后**代码也要一起回**（DEFAULT_ITEMS 里的 BLOCK），否则没有档案的交付类型仍然硬拦。
