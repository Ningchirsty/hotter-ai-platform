-- ============================================================================
-- 闸门项完整性巡检 + 软删行复活（R38-4 / P1-1）
-- ----------------------------------------------------------------------------
-- 修的是什么（一个**静默 fail-open**）：
--   `dp_gate_brand_requirement_uniform.sql` 用
--     INSERT IGNORE INTO dp_gate_item … WHERE NOT EXISTS (… del_flag='0')
--   补「品牌 Brief 已填写并确认」与「已声明禁用词与合规红线」两项。
--   但 dp_gate_item 上有 UNIQUE KEY uk_dp_gate_item (profile_id, item_code)：
--   如果某档案**已经有同名项、只是被软删了**（del_flag='1'），那么
--     · NOT EXISTS 只看 del_flag='0' 的行 → 判定「不存在」→ 走 INSERT
--     · INSERT IGNORE 撞唯一键 → **静默跳过**
--   结果是这个档案最终**没有这一项**；而代码侧 @TableLogic 会过滤软删行、
--   mergeItems 又只遍历档案里真实存在的项 → **这一交付类型上的品牌要求根本不检查**，
--   且全过程没有任何报错（不是"报错但被忽略"，是"悄无声息地 fail-open"）。
--
-- 本脚本做两件事：
--   §1 只读巡检：每个已发布档案是否齐 7 项、有没有软删残留、有没有缺项。
--      **这一段可以直接当日常巡检跑**（不改任何数据）。
--   §2 修复：把软删残留**复活**（而不是 INSERT IGNORE 跳过），并把 7 项的
--      等级/顺序/展示名同步成代码期望的值（与 uniform 脚本同一口径：
--      BRAND_BRIEF_CONFIRMED=BLOCK，其余细项=CONDITION）。
--
-- 幂等：§2 的复活与同步都是**条件 UPDATE**（只改不一致的行），重复执行第 2 次起
--       影响 0 行；不会有任何 INSERT，因此不会再制造唯一键冲突。
-- 回滚：见文件末尾（把本脚本改动过的行按备份表写回）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- §1 执行前巡检（只读；日常巡检也跑这一段）
-- ---------------------------------------------------------------------------
SELECT '=== 1.1 每个已发布档案的项数（期望每个都是 7）===' AS s;
SELECT p.id AS profile_id, p.profile_code, p.delivery_type, p.status,
       SUM(i.del_flag = '0') AS live_items,
       SUM(i.del_flag <> '0') AS soft_deleted_items
  FROM dp_gate_profile p
  LEFT JOIN dp_gate_item i ON i.profile_id = p.id
 WHERE p.del_flag = '0' AND p.status = 'PUBLISHED'
 GROUP BY p.id, p.profile_code, p.delivery_type, p.status
 ORDER BY p.delivery_type;

SELECT '=== 1.2 【危险信号】软删的闸门项（非空即说明有档案会 fail-open）===' AS s;
SELECT p.profile_code, p.delivery_type, i.id, i.item_code, i.level, i.del_flag
  FROM dp_gate_item i JOIN dp_gate_profile p ON p.id = i.profile_id
 WHERE i.del_flag <> '0'
 ORDER BY p.delivery_type, i.item_code;

SELECT '=== 1.3 【危险信号】已发布档案缺哪几项（期望为空）===' AS s;
SELECT p.profile_code, p.delivery_type, want.item_code AS missing_item
  FROM dp_gate_profile p
  JOIN (SELECT 'DNA_LOCKED' AS item_code UNION ALL SELECT 'REFERENCE_IMAGE'
        UNION ALL SELECT 'DIRECTION_SELECTED' UNION ALL SELECT 'STORYBOARD_LOCKED'
        UNION ALL SELECT 'BRAND_TONE_CONFIRMED' UNION ALL SELECT 'BRAND_BRIEF_CONFIRMED'
        UNION ALL SELECT 'FORBIDDEN_WORDS_DECLARED') want
  LEFT JOIN dp_gate_item i
    ON i.profile_id = p.id AND i.item_code = want.item_code AND i.del_flag = '0'
 WHERE p.del_flag = '0' AND p.status = 'PUBLISHED' AND i.id IS NULL
 ORDER BY p.delivery_type, want.item_code;

-- ---------------------------------------------------------------------------
-- §2 修复
-- ---------------------------------------------------------------------------
-- 2.1 复活软删残留：把 del_flag 写回 '0'，同时把等级/顺序/展示名同步成期望值。
--     为什么不是 UPDATE 完再 INSERT：复活之后该 (profile_id, item_code) 已经是活行，
--     再 INSERT 就是重复；一行 UPDATE 同时解决"缺失"与"等级不对"两件事。
UPDATE dp_gate_item i
  JOIN dp_gate_profile p ON p.id = i.profile_id
   SET i.del_flag = '0',
       i.level = CASE i.item_code
                   WHEN 'BRAND_BRIEF_CONFIRMED' THEN 'BLOCK'
                   ELSE 'CONDITION' END,
       i.sort_no = CASE i.item_code
                     WHEN 'DNA_LOCKED' THEN 10
                     WHEN 'REFERENCE_IMAGE' THEN 20
                     WHEN 'DIRECTION_SELECTED' THEN 30
                     WHEN 'STORYBOARD_LOCKED' THEN 40
                     WHEN 'BRAND_TONE_CONFIRMED' THEN 50
                     WHEN 'BRAND_BRIEF_CONFIRMED' THEN 60
                     WHEN 'FORBIDDEN_WORDS_DECLARED' THEN 70
                     ELSE i.sort_no END,
       i.item_label = CASE i.item_code
                        WHEN 'DNA_LOCKED' THEN '视觉基因已锁定'
                        WHEN 'REFERENCE_IMAGE' THEN '产品参考图已上传'
                        WHEN 'DIRECTION_SELECTED' THEN '视觉方向已选定'
                        WHEN 'STORYBOARD_LOCKED' THEN '分镜已锁定'
                        WHEN 'BRAND_TONE_CONFIRMED' THEN '品牌调性已确认'
                        WHEN 'BRAND_BRIEF_CONFIRMED' THEN '品牌 Brief 已填写并确认'
                        WHEN 'FORBIDDEN_WORDS_DECLARED' THEN '已声明禁用词与合规红线'
                        ELSE i.item_label END,
       i.remark = CONCAT(COALESCE(i.remark, ''),
                   ' ｜R38-4：软删残留已复活（原先 INSERT IGNORE 会静默跳过，导致该档案 fail-open）'),
       i.update_time = sysdate()
 WHERE i.del_flag <> '0'
   AND i.item_code IN ('DNA_LOCKED','REFERENCE_IMAGE','DIRECTION_SELECTED','STORYBOARD_LOCKED',
                       'BRAND_TONE_CONFIRMED','BRAND_BRIEF_CONFIRMED','FORBIDDEN_WORDS_DECLARED');

-- 2.2 同步活行的等级/顺序（不改 del_flag；只修"等级不是期望值"的行）
--     与 dp_gate_brand_requirement_uniform.sql 同口径，重复执行影响 0 行。
UPDATE dp_gate_item
   SET level = 'BLOCK', update_time = sysdate()
 WHERE del_flag = '0' AND item_code = 'BRAND_BRIEF_CONFIRMED' AND level <> 'BLOCK';

UPDATE dp_gate_item
   SET level = 'CONDITION', update_time = sysdate()
 WHERE del_flag = '0'
   AND item_code IN ('FORBIDDEN_WORDS_DECLARED','BRAND_TONE_CONFIRMED')
   AND level <> 'CONDITION';

-- ---------------------------------------------------------------------------
-- §3 执行后核对（期望：每个已发布档案 live_items=7、soft_deleted_items=0、缺项为空）
-- ---------------------------------------------------------------------------
SELECT '=== 3.1 修复后：项数 ===' AS s;
SELECT p.profile_code, p.delivery_type,
       SUM(i.del_flag = '0') AS live_items,
       SUM(i.del_flag <> '0') AS soft_deleted_items
  FROM dp_gate_profile p
  LEFT JOIN dp_gate_item i ON i.profile_id = p.id
 WHERE p.del_flag = '0' AND p.status = 'PUBLISHED'
 GROUP BY p.profile_code, p.delivery_type
 ORDER BY p.delivery_type;

SELECT '=== 3.2 修复后：仍软删的项（期望空）===' AS s;
SELECT p.profile_code, i.item_code FROM dp_gate_item i
  JOIN dp_gate_profile p ON p.id = i.profile_id
 WHERE i.del_flag <> '0';

SELECT '=== 3.3 修复后：仍缺项的已发布档案（期望空）===' AS s;
SELECT p.profile_code, want.item_code AS missing_item
  FROM dp_gate_profile p
  JOIN (SELECT 'DNA_LOCKED' AS item_code UNION ALL SELECT 'REFERENCE_IMAGE'
        UNION ALL SELECT 'DIRECTION_SELECTED' UNION ALL SELECT 'STORYBOARD_LOCKED'
        UNION ALL SELECT 'BRAND_TONE_CONFIRMED' UNION ALL SELECT 'BRAND_BRIEF_CONFIRMED'
        UNION ALL SELECT 'FORBIDDEN_WORDS_DECLARED') want
  LEFT JOIN dp_gate_item i
    ON i.profile_id = p.id AND i.item_code = want.item_code AND i.del_flag = '0'
 WHERE p.del_flag = '0' AND p.status = 'PUBLISHED' AND i.id IS NULL;

SELECT '=== 3.4 修复后：两个已发布档案的品牌项等级（期望 brief=BLOCK / forbidden=CONDITION）===' AS s;
SELECT p.delivery_type, p.profile_code,
       MAX(CASE WHEN i.item_code = 'BRAND_BRIEF_CONFIRMED' THEN i.level END) AS brief_level,
       MAX(CASE WHEN i.item_code = 'FORBIDDEN_WORDS_DECLARED' THEN i.level END) AS forbidden_level
  FROM dp_gate_profile p LEFT JOIN dp_gate_item i ON i.profile_id = p.id AND i.del_flag = '0'
 WHERE p.del_flag = '0' AND p.status = 'PUBLISHED'
 GROUP BY p.delivery_type, p.profile_code;

SELECT '=== 3.5 草稿档案也要齐（明天会被发布；期望空）===' AS s;
SELECT p.profile_code, p.status, want.item_code AS missing_item
  FROM dp_gate_profile p
  JOIN (SELECT 'DNA_LOCKED' AS item_code UNION ALL SELECT 'REFERENCE_IMAGE'
        UNION ALL SELECT 'DIRECTION_SELECTED' UNION ALL SELECT 'STORYBOARD_LOCKED'
        UNION ALL SELECT 'BRAND_TONE_CONFIRMED' UNION ALL SELECT 'BRAND_BRIEF_CONFIRMED'
        UNION ALL SELECT 'FORBIDDEN_WORDS_DECLARED') want
  LEFT JOIN dp_gate_item i
    ON i.profile_id = p.id AND i.item_code = want.item_code AND i.del_flag = '0'
 WHERE p.del_flag = '0' AND p.status <> 'PUBLISHED' AND i.id IS NULL;

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------
-- 本脚本只改 dp_gate_item 的 del_flag / level / sort_no / item_label / remark。
-- 执行前请先留档（本案已留 bak_20261006_dp_gate_item）：
--   CREATE TABLE bak_gateitem_YYYYMMDD AS SELECT * FROM dp_gate_item;
-- 回滚：把复活过的行重新软删（按备份表里 del_flag='1' 的那批 id）
--   UPDATE dp_gate_item i JOIN bak_gateitem_YYYYMMDD b ON b.id = i.id
--      SET i.del_flag = '1', i.update_time = sysdate()
--    WHERE b.del_flag = '1';
-- 注意：等级回滚要连代码一起回（CreativeGateServiceImpl.DEFAULT_ITEMS 里的 BLOCK），
--       否则没有档案的交付类型仍会硬拦。
