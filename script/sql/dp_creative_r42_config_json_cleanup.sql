-- =====================================================================
-- R42：清掉 dp_scenario_step.config_json 里过时的一条指针（skeletonContract）
--
-- 背景（R21 的遗留待办）：R11 建场景配置时，ECOM_DETAIL 的分镜步写着
--   {"impl":"existing","skeletonContract":"creative/screen-skeleton.json"}
-- 意思是"屏集合按这个契约文件来"。模块引擎（R21）接管屏集合之后，屏来自
-- dp_project_module（项目模块计划），契约文件由**后端按 classpath 直接加载**
-- （启动日志：屏骨架契约加载完成，来源=classpath:creative/screen-skeleton.json，共 7 屏），
-- 配置里这条指针就再也没人读了——它指向的不是"当前真相"。
--
-- 本轮先查清了这一列的真实地位，避免误删：
--   * 全仓（Java + 前端）**没有任何代码读取 config_json 的任何键**（只有领域字段声明）；
--   * 它是"配置里的说明书"：impl（这一步现在靠什么实现）、note（为什么这样）、
--     skeletonFrom / pageWidthFrom（屏集合与页宽的来源）——这些对人有用，保留；
--   * 只有 skeletonContract 是**过时的指针**（指向已被模块引擎取代的机制），故只清它。
--
-- 执行方式（生产）：
--   docker exec -i ai-video-poc-mysql-1 mysql --default-character-set=utf8mb4 \
--     -uroot -p"$MYSQL_ROOT_PASSWORD" ai_video_poc < dp_creative_r42_config_json_cleanup.sql
-- =====================================================================

-- 1) 改前留档（只有 1 行）
SELECT p.delivery_type, s.step_code, s.config_json
  FROM dp_scenario_step s JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE s.config_json LIKE '%skeletonContract%';

-- 2) 只摘掉这一个键，其余键原样保留
UPDATE dp_scenario_step
   SET config_json = JSON_REMOVE(config_json, '$.skeletonContract')
 WHERE config_json LIKE '%skeletonContract%'
   AND JSON_VALID(config_json);

-- 3) 核对：skeletonContract 归零，其它键一个不少，JSON 仍然合法
SELECT COUNT(*) AS skeleton_contract_left
  FROM dp_scenario_step WHERE config_json LIKE '%skeletonContract%';
SELECT p.delivery_type, s.step_code, s.config_json, JSON_VALID(s.config_json) AS json_ok
  FROM dp_scenario_step s JOIN dp_scenario_profile p ON p.id = s.profile_id
 WHERE p.delivery_type = 'ECOM_DETAIL' AND s.step_code = 'STORYBOARD';
SELECT DISTINCT JSON_KEYS(config_json) AS keys_in_use
  FROM dp_scenario_step WHERE config_json IS NOT NULL AND JSON_VALID(config_json);

-- =====================================================================
-- 回滚（把那条指针写回去；仅当确需恢复 R11 的写法时执行）
--
-- UPDATE dp_scenario_step s
--   JOIN dp_scenario_profile p ON p.id = s.profile_id
--    SET s.config_json = JSON_SET(s.config_json, '$.skeletonContract',
--                                'creative/screen-skeleton.json')
--  WHERE p.delivery_type = 'ECOM_DETAIL' AND s.step_code = 'STORYBOARD';
-- =====================================================================
