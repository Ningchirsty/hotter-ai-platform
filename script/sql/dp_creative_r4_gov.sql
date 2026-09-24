-- ------------------------------------------------------------------
-- AI 视觉工厂：R4 治理台注册（本地 LLM 创作能力）
--
-- 背景（问题 3 的答案收尾）：R4 之前方向/分镜只能由固定模板派生，根因是**没有任何可用的创作模型能力**：
--   - dryRun creative_direction_draft → DENIED（能力不存在）
--   - 四个已登记模型实测：3 local-content 是进程内规则引擎（1ms、LocalRuleModelInvoker）、
--     1 glm-5.1 401 无密钥、6 nvidia 404、7 openrouter/free 可用但属外部且 dataLevelMax=PUBLIC
-- 现在 192.168.2.223（公司 A100 机器）上部署了 Ollama + qwen2.5:7b-instruct，
-- 按「本地私有」登记：数据不出公司，因此 data_level_max=RESTRICTED，路由策略一律 allow_external='N'。
--
-- 说明：
--   1. 幂等：全部 NOT EXISTS 守卫，可重复执行；
--   2. 模型登记为 LOCAL（不是 EXTERNAL_API）——Ollama 在公司内网，标成外部会污染审计口径，
--      并让 INTERNAL 任务的合法调用被 allow_external 闸门拒掉；
--      对应的调用器是 ruoyi-ai-creative 的 CreativeLocalChatInvoker（按能力声明认领）；
--   3. output_schema 刻意只要求「合法 JSON」：逐字段验收由 CreativeDraftBrain 做（缺失字段回落参数化草稿），
--      比整体 schema 校验更适合「模型润色 + 逐项采纳」的用法；
--   4. 必须在部署带 CreativeLocalChatInvoker 的后端镜像之后才能生效（否则路由会选到没有调用器的能力）。
-- ------------------------------------------------------------------

-- 1) 模型主数据（sai_model_config）------------------------------------

INSERT INTO sai_model_config
(id, provider_id, model_name, model_key, model_type, adapter_key, description,
 api_endpoint, scope, is_default, is_enabled, created_dt, updated_dt)
SELECT 1764000000000000001, p.id, '本地 Qwen2.5-7B-Instruct（Ollama）', 'qwen2.5:7b-instruct', 'CHAT',
       'openai-compatible', '公司内网 A100 上的 Ollama 端点，供视觉工厂生成方向/分镜草稿；数据不出公司',
       'http://192.168.2.223:11434/v1', 'GLOBAL', 0, 1, NOW(), NOW()
  FROM sai_model_provider p
 WHERE p.provider_name = 'Ollama'
   AND NOT EXISTS (SELECT 1 FROM (SELECT model_key FROM sai_model_config) c
                    WHERE c.model_key = 'qwen2.5:7b-instruct');

-- 2) 模型治理属性（aig_model_governance）-----------------------------

INSERT INTO aig_model_governance
(governance_id, model_id, deployment_type, data_level_max, lifecycle_status, input_limits, output_limits,
 status, del_flag, create_dept, create_by, create_time, remark)
SELECT 1764000000000000011, c.id, 'LOCAL', 'RESTRICTED', 'GRAY',
       '提示词上限 8k 字符；单次请求不含图片', '只输出 JSON 文本；单字段长度由业务侧逐字段验收',
       '0', '0', 1761000000000000103, 1761100000000000001, NOW(),
       '视觉工厂本地大模型：内网 HTTP 端点，数据不出公司'
  FROM sai_model_config c
 WHERE c.model_key = 'qwen2.5:7b-instruct'
   AND NOT EXISTS (SELECT 1 FROM (SELECT model_id FROM aig_model_governance) g
                    WHERE g.model_id = c.id);

-- 3) 能力登记（aig_capability）---------------------------------------

INSERT INTO aig_capability
(capability_id, capability_code, capability_name, biz_goal, required_tags, input_schema, output_schema,
 data_policy, human_confirm_points, quality_threshold, audit_level, status, del_flag,
 create_dept, create_by, create_time, remark)
SELECT 1764000000000000021, 'creative_direction_draft', '视觉方向草稿',
       '为电商详情页给出 3 条视觉方向的名称与取舍说明，供人选一条',
       'TEXT',
       '{"fields":[{"name":"facts","type":"object"},{"name":"dna","type":"string"},{"name":"baseline","type":"array"}]}',
       '{"fields":[],"note":"只要求合法 JSON；具体字段（directions[].code/name/concept）由业务侧逐字段验收，不合格回落参数化草稿"}',
       'LOCAL_FIRST',
       '方向必须由人选定，模型不得代替人做取舍',
       '输出必须是合法 JSON；单字段 name≤40 字、concept≤400 字；超长或缺失即丢弃该字段',
       'SUMMARY', '0', '0', 1761000000000000103, 1761100000000000001, NOW(),
       'R4：方向文案由本地模型润色，无模型时回落参数化模板（来源如实标注）'
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT capability_code FROM aig_capability) t
                    WHERE t.capability_code = 'creative_direction_draft');

INSERT INTO aig_capability
(capability_id, capability_code, capability_name, biz_goal, required_tags, input_schema, output_schema,
 data_policy, human_confirm_points, quality_threshold, audit_level, status, del_flag,
 create_dept, create_by, create_time, remark)
SELECT 1764000000000000022, 'creative_storyboard_draft', '分镜草稿',
       '为电商详情页 7 屏分镜给出标题/正文/画面独白，供人编辑并锁定',
       'TEXT',
       '{"fields":[{"name":"facts","type":"object"},{"name":"dna","type":"string"},{"name":"screenTypes","type":"array"}]}',
       '{"fields":[],"note":"只要求合法 JSON；screens[].type/title/subtitle/bodyText/soloStatement 由业务侧逐字段验收，缺失则保留参数化草稿"}',
       'LOCAL_FIRST',
       '分镜必须由人锁定；锁定版不可修改',
       '输出必须是合法 JSON；type 必须与固定 7 屏一致；单字段 title≤60、soloStatement≤400 字',
       'SUMMARY', '0', '0', 1761000000000000103, 1761100000000000001, NOW(),
       'R4：分镜文案由本地模型润色，屏骨架固定 7 屏不变'
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT capability_code FROM aig_capability) t
                    WHERE t.capability_code = 'creative_storyboard_draft');

-- 4) 路由策略（aig_route_policy）：三个数据等级都禁外发 ---------------

INSERT INTO aig_route_policy
(policy_id, capability_code, data_level, preferred_deployment, allow_external, require_approval,
 fallback_to_manual, status, del_flag, create_dept, create_by, create_time, remark)
SELECT
       -- 每个 (能力, 数据等级) 必须有自己的主键：方向用 31~33，分镜用 34~36
       CASE cc.code WHEN 'creative_direction_draft' THEN 1764000000000000030
                    ELSE 1764000000000000033 END
         + CASE lv.level WHEN 'PUBLIC' THEN 1 WHEN 'INTERNAL' THEN 2 ELSE 3 END AS policy_id,
       cc.code, lv.level, 'LOCAL', 'N', 'N', 'Y', '0', '0',
       1761000000000000103, 1761100000000000001, NOW(),
       '视觉工厂文案能力一律本地私有部署，禁止外发（任务多为 INTERNAL）'
  FROM (SELECT 'creative_direction_draft' AS code UNION ALL SELECT 'creative_storyboard_draft') cc
  JOIN (SELECT 'PUBLIC' AS level UNION ALL SELECT 'INTERNAL' UNION ALL SELECT 'RESTRICTED') lv
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT capability_code, data_level FROM aig_route_policy) p
                    WHERE p.capability_code = cc.code AND p.data_level = lv.level);

-- 5) 能力→模型绑定（aig_capability_model）----------------------------

INSERT INTO aig_capability_model
(bind_id, capability_code, model_id, usage_type, priority, status, del_flag,
 create_dept, create_by, create_time, remark)
SELECT 1764000000000000041, 'creative_direction_draft', c.id, 'PRIMARY', 10, '0', '0',
       1761000000000000103, 1761100000000000001, NOW(), 'R4：本地 Qwen2.5-7B-Instruct'
  FROM sai_model_config c
 WHERE c.model_key = 'qwen2.5:7b-instruct'
   AND NOT EXISTS (SELECT 1 FROM (SELECT capability_code FROM aig_capability_model) b
                    WHERE b.capability_code = 'creative_direction_draft');

INSERT INTO aig_capability_model
(bind_id, capability_code, model_id, usage_type, priority, status, del_flag,
 create_dept, create_by, create_time, remark)
SELECT 1764000000000000042, 'creative_storyboard_draft', c.id, 'PRIMARY', 10, '0', '0',
       1761000000000000103, 1761100000000000001, NOW(), 'R4：本地 Qwen2.5-7B-Instruct'
  FROM sai_model_config c
 WHERE c.model_key = 'qwen2.5:7b-instruct'
   AND NOT EXISTS (SELECT 1 FROM (SELECT capability_code FROM aig_capability_model) b
                    WHERE b.capability_code = 'creative_storyboard_draft');

-- 6) 核对 ------------------------------------------------------------

SELECT c.id AS model_id, c.model_key, g.deployment_type, g.data_level_max, g.lifecycle_status
  FROM sai_model_config c LEFT JOIN aig_model_governance g ON g.model_id = c.id
 WHERE c.model_key = 'qwen2.5:7b-instruct';

SELECT cap.capability_code, cap.capability_name, r.data_level, r.preferred_deployment, r.allow_external,
       b.model_id, b.usage_type
  FROM aig_capability cap
  LEFT JOIN aig_route_policy r ON r.capability_code = cap.capability_code
  LEFT JOIN aig_capability_model b ON b.capability_code = cap.capability_code
 WHERE cap.capability_code IN ('creative_direction_draft', 'creative_storyboard_draft')
 ORDER BY cap.capability_code, r.data_level;

SELECT 'DP_CREATIVE_R4_GOV_DONE' AS marker;
