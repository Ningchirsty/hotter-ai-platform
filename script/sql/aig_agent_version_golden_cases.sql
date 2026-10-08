-- ------------------------------------------------------------------
-- AI 治理：把「内置 Agent 版本声明的黄金用例集合」在**存量库**上补齐
--
-- 为什么需要它（这不是可有可无的补丁）：
--   aig_agent_registry_seed.sql 是 `insert ... select ... where not exists`——**只插不改**。
--   所以当某个内置 Agent 版本需要**多声明一条黄金用例**时，改种子脚本里那一行
--   在存量库上是**不会生效**的：版本行已存在，整条 INSERT 被 `where not exists` 跳过。
--
--   后果是「覆盖面看起来补了、实际空转」：
--     新用例进了 aig_evaluation_case，却没有任何版本声明它；而
--     AigEvaluationServiceImpl 要求「本次请求的用例集合 == 版本声明的集合」，
--     于是这条新用例**永远跑不到**。
--   （实测：生产上用例库 8 条含 case-plan-brand-brief，但 creative_planning 版本仍只声明 2 条。）
--
-- 幂等：只有数组里还没有该用例码时才追加（JSON_CONTAINS 守卫），可安全重复执行。
--
-- 兼容性说明：
--   config_json 是 longtext，不是 JSON 类型；JSON 函数接受合法 JSON 文本，与书写格式无关
--   （同仓库既有做法，见 dp_creative_level_words_in_stored_text.sql 的注释）。
--   唯一副作用：MySQL 的 JSON 函数会**规范化**该字段的文本排版（如 {"a":1} -> {"a": 1}）。
--   读取方一律走 JSON_EXTRACT，功能不受影响；此处只动被点名的那一行。
-- ------------------------------------------------------------------

UPDATE aig_agent_version
   SET config_json = JSON_ARRAY_APPEND(config_json, '$.golden_cases', 'case-plan-brand-brief')
 WHERE agent_id = 1765000000000000001
   AND version = '0.1.0'
   AND JSON_VALID(config_json) = 1
   AND JSON_EXTRACT(config_json, '$.golden_cases') IS NOT NULL
   AND JSON_CONTAINS(config_json, '"case-plan-brand-brief"', '$.golden_cases') = 0;

-- 核对：四个内置 Agent 版本各自声明了几条（creative_planning 期望 3 条且含新用例）
SELECT a.agent_code, av.version,
       JSON_UNQUOTE(JSON_EXTRACT(av.config_json, '$.golden_cases')) AS declared,
       JSON_LENGTH(av.config_json, '$.golden_cases') AS declared_n,
       JSON_CONTAINS(av.config_json, '"case-plan-brand-brief"',
                     '$.golden_cases') AS has_new_case
  FROM aig_agent_version av
  JOIN aig_agent a ON a.agent_id = av.agent_id
 ORDER BY a.agent_code;
