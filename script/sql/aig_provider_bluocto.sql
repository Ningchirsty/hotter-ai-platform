-- ----------------------------------------------------------------------------
-- 外部聚合网关接入种子：bluocto（New API 系，OpenAI 兼容）
--
-- 端点已实测确认：
--   GET https://bluocto.com/v1/models → 401
--   {"error":{"message":"无效的令牌…","type":"new_api_error"}}
--   401 而不是 404，且 type=new_api_error —— 说明 /v1 这条 OpenAI 兼容路径存在，
--   平台是 New API（one-api 系）。因此 api_endpoint 登记为 https://bluocto.com/v1，
--   由 OpenAiCompatibleInvoker 拼成 …/v1/chat/completions
--   （buildChatUrl：配到 /v1 或直接配到 /chat/completions 都能工作）。
--
-- 一把 Key 通多种模型：这是 New API 的形态——1 条 sai_model_provider + N 条
-- sai_model_config，N 条共享同一 api_endpoint 与同一把 api_key。
--
-- ============================================================================
-- 本脚本默认**【只登记连接与治理属性】，不含任何数据外发**：
--   A 段（默认执行）：供应商 + 模型 + 治理属性 → 全部为「已登记但无能力指向」的惰性状态，
--                     没有任何能力会路由到它，因此不会发出任何外部请求。
--   B 段（默认整段注释）：绑定能力 + 放宽路由策略 → 这才是真正让数据出去的开关，
--                     需要你逐条确认后手动取消注释。理由见 B 段头部。
--
-- 为什么不直接改现有 creative/content 的策略：
--   dp_creative_r8_dna_gov.sql 明确把 visual_dna_extract 的三个数据等级都设成
--   allow_external='N'，理由是「基因抽取涉及产品图，一律本地私有部署，禁止外发」；
--   那是团队已经做过的一个安全决定，不该被一次「接入配置」顺手覆盖。
--
-- 密钥怎么进（本脚本**不写** api_key）：
--   api_key 是 SM4 密文列。**不要**在这里填明文，也不要用其它工具随便加密——必须与
--   snail-ai 的 CryptoHelper 逐字节一致（SM4/CBC/PKCS5Padding，hex 16 字节 key/iv）。
--   推荐路径：部署环境设 AIGOV_MODEL_CRYPTO_ENABLED=true + …_SECRET_KEY/…_IV，
--   然后调 PUT /aigov/model/secret 录入明文，由服务端自行加密落库。
--
-- 幂等：供应商/模型/治理属性/绑定 全部 NOT EXISTS 或 INSERT IGNORE 守卫；
--   aig_model_governance 有 uk(model_id)、aig_capability_model 有 uk(capability_code,model_id)、
--   aig_route_policy 有 uk(capability_code,data_level)，可安全重复执行。
-- ----------------------------------------------------------------------------

-- ============================================================================
-- A 段：登记供应商
-- ============================================================================
INSERT INTO sai_model_provider (provider_name, provider_key, description, icon_url, is_enabled)
SELECT 'bluocto 聚合网关', 'bluocto',
       'New API 系 OpenAI 兼容聚合网关：一把 Key 通多种模型（https://bluocto.com/v1）',
       NULL, 1
  FROM (SELECT 1) dummy
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT provider_key FROM sai_model_provider) t
                    WHERE t.provider_key = 'bluocto');

-- ============================================================================
-- A 段：登记模型（一把 Key 通多种模型 → 每个要用的 model_key 一行）
--
-- ⚠ 下面的列表**默认是空的**（WHERE 1 = 0），不会插入任何模型，因为编造 model_key
--   会让上游报「model not found」，那种失败最难查。
--   请把实际可用的模型标识填进来，或等 Key 到位后由我调 /v1/models 枚举生成。
--
-- ⚠ 所有行都是 model_type='CHAT'：OpenAiCompatibleInvoker 只对接 /chat/completions，
--   embedding / rerank / 语音走的是别的端点，不能混用（代码会直接拒绝非 CHAT）。
--   **带视觉能力的多模态模型在这里同样是 CHAT**——「能不能看图」不是这一列表达的。
-- ============================================================================
INSERT INTO sai_model_config
(provider_id, model_name, model_key, model_type, adapter_key, description,
 api_key, api_endpoint, config_json, owner_id, scope, is_default, is_enabled, created_dt, updated_dt)
SELECT p.id, m.model_name, m.model_key, 'CHAT', 'openai-compatible', m.description,
       NULL, 'https://bluocto.com/v1', NULL, NULL, 'GLOBAL', 0, m.is_enabled, NOW(), NOW()
  FROM sai_model_provider p
  JOIN (
        SELECT 'REPLACE_ME' AS model_key, '待填：模型显示名' AS model_name,
               '待填：用途说明' AS description, 1 AS is_enabled
         WHERE 1 = 0
        -- 实际形态示例（把 WHERE 1 = 0 去掉并替换成真实标识）：
        -- UNION ALL SELECT 'gpt-4o-mini',      'GPT-4o mini',   '文本/轻量', 1
        -- UNION ALL SELECT 'gemini-2.5-flash', 'Gemini 2.5 Flash', '文本+视觉', 1
       ) m
 WHERE p.provider_key = 'bluocto'
   AND NOT EXISTS (SELECT 1 FROM (SELECT provider_id, model_key FROM sai_model_config) c
                    WHERE c.provider_id = p.id AND c.model_key = m.model_key);

-- ============================================================================
-- A 段：治理属性（aig_model_governance）——两条安全默认，都由代码强制
--
--   1) data_level_max = 'INTERNAL'
--      路由的可用性判定是「模型允许的最高等级 rank ≥ 本次数据等级 rank」，
--      所以 RESTRICTED / STRICT 的数据**在代码层就不可能**路由到本供应商，
--      不依赖任何策略行是否写对。外部聚合网关不给限制级数据，这是有意的。
--   2) lifecycle_status = 'GRAY'
--      GRAY 属于 callable()，可用但表示「灰度、未充分验证」；
--      连通性测试与真实调用都正常后再提升为 PRODUCTION。
--
-- health_status 留 NULL（尚未做过连通性测试）。注意：NULL **不会**被路由排除
--   （路由只在明确 DOWN 时排除），因此登记完即可调用；建议登记后立刻跑一次
--   POST /aigov/model/{modelId}/test 把健康状态落下来。
--
-- secret_ref 留 NULL：它只是「引用登记」，运行时**没有任何组件解析它**——
--   真正生效的凭据是 sai_model_config.api_key。填个假的反而误导排障。
-- ============================================================================
INSERT IGNORE INTO aig_model_governance
(governance_id, model_id, deployment_type, data_level_max, lifecycle_status, secret_ref,
 input_limits, output_limits, cost_limit, owner_tech, owner_biz, owner_security,
 valid_from, valid_to, health_status, health_time, status, del_flag,
 create_dept, create_by, create_time, remark)
SELECT 1764100000000000000 + c.id, c.id, 'EXTERNAL_API', 'INTERNAL', 'GRAY', NULL,
       NULL, NULL, NULL, NULL, NULL, NULL,
       NULL, NULL, NULL, NULL, '0', '0',
       1761000000000000103, 1761100000000000001, NOW(),
       CONCAT('bluocto 聚合网关（外部API）：最高 INTERNAL，外部不接触限制级数据')
  FROM sai_model_config c
  JOIN sai_model_provider p ON p.id = c.provider_id
 WHERE p.provider_key = 'bluocto';

-- ============================================================================
-- B 段（默认整段注释，需你确认后手动启用）
--
-- 这一段才会让数据真的出去。启用前请逐条读：
--
-- B1 绑定模型到能力：用 usage_type='GRAY'
--      排序口径是 PRIMARY → GRAY → FALLBACK，所以 GRAY **不会挤掉** 已有的本地
--      PRIMARY 模型，而是「本地不行时再走外部」——与这些能力的 data_policy
--      LOCAL_FIRST 一致。
--      ⚠ 视觉能力（required_tags='VISION'，如 visual_dna_extract /
--        deliverable_consistency）**绝不可以绑文本模型**。治理数据里没有
--        「模型是否支持视觉」这一列，代码也不会替你拦（required_tags 目前只登记、
--        未与模型能力标签做匹配），绑错了会得到一份「没看过图」的结论却以模型
--        产出的样子出现。绑视觉能力前请先确认该 model_key 真的支持图片输入。
--
-- B2 放宽路由策略：把 allow_external 从 'N' 改成 'Y'
--      只对 PUBLIC / INTERNAL 放宽；RESTRICTED 与 STRICT **永不**放宽
--      （STRICT 即便误写成 'Y'，路由层也会强制覆盖为不允许）。
--      preferred_deployment 留 NULL：该列目前不参与过滤（只写入策略命中说明），
--      填了会让人误以为在生效。
--
-- 需要放开的具体能力，请按你的判断增删：
--   creative_direction_draft   视觉方向草稿（文本）
--   creative_storyboard_draft  分镜草稿（文本）
--   visual_dna_extract         视觉基因抽取（**看图**，只能绑视觉模型）
--   deliverable_consistency    成品一致性检查（**看图**，只能绑视觉模型）
-- ----------------------------------------------------------------------------
-- -- B1：把 bluocto 的模型绑到「文本」能力上（GRAY 灰度，不挤掉本地主选）
-- INSERT IGNORE INTO aig_capability_model
-- (bind_id, capability_code, model_id, usage_type, priority, status, del_flag,
--  create_dept, create_by, create_time, remark)
-- SELECT 1764200000000000000 + c.id, 'creative_direction_draft', c.id, 'GRAY', 500, '0', '0',
--        1761000000000000103, 1761100000000000001, NOW(),
--        'bluocto 聚合网关：本地不可用时的灰度备选'
--   FROM sai_model_config c
--   JOIN sai_model_provider p ON p.id = c.provider_id
--  WHERE p.provider_key = 'bluocto';
--
-- -- B2：只对 PUBLIC / INTERNAL 放宽外发（RESTRICTED/STRICT 不动）
-- UPDATE aig_route_policy
--    SET allow_external = 'Y', preferred_deployment = NULL,
--        update_by = 1761100000000000001, update_time = NOW(),
--        remark = CONCAT(IFNULL(remark, ''), ' | 已允许外发至 bluocto（仅 PUBLIC/INTERNAL）')
--  WHERE capability_code IN ('creative_direction_draft', 'creative_storyboard_draft')
--    AND data_level IN ('PUBLIC', 'INTERNAL')
--    AND del_flag = '0';
--
-- -- B3：（仅当确认该 model_key 支持图片输入后才执行）
-- -- INSERT IGNORE INTO aig_capability_model
-- -- (bind_id, capability_code, model_id, usage_type, priority, status, del_flag,
-- --  create_dept, create_by, create_time, remark)
-- -- SELECT 1764200000000000000 + c.id, 'visual_dna_extract', c.id, 'GRAY', 500, '0', '0',
-- --        1761000000000000103, 1761100000000000001, NOW(),
-- --        'bluocto：视觉模型，需人工确认支持图片输入'
-- --   FROM sai_model_config c
-- --   JOIN sai_model_provider p ON p.id = c.provider_id
-- --  WHERE p.provider_key = 'bluocto' AND c.model_key = 'REPLACE_ME_vision_model';
-- ============================================================================

-- ============================================================================
-- 核对
-- ============================================================================
-- 1) 供应商与模型
SELECT p.provider_key, p.provider_name, p.is_enabled,
       c.id AS model_id, c.model_key, c.model_type, c.api_endpoint, c.is_enabled AS model_enabled,
       CASE WHEN c.api_key IS NULL THEN '未配置密钥（待录入）' ELSE '已配置（密文）' END AS key_state
  FROM sai_model_provider p
  LEFT JOIN sai_model_config c ON c.provider_id = p.id
 WHERE p.provider_key = 'bluocto'
 ORDER BY c.id;

-- 2) 治理属性（应看到 deployment_type=EXTERNAL_API、data_level_max=INTERNAL、lifecycle=GRAY）
SELECT g.governance_id, c.model_key, g.deployment_type, g.data_level_max,
       g.lifecycle_status, g.health_status, g.secret_ref, g.status
  FROM aig_model_governance g
  JOIN sai_model_config c ON c.id = g.model_id
  JOIN sai_model_provider p ON p.id = c.provider_id
 WHERE p.provider_key = 'bluocto'
 ORDER BY c.id;

-- 3) 绑定与策略现状（默认应全部为空/仍为 N —— 证明 A 段不产生任何外发路径）
SELECT c.model_key, b.capability_code, b.usage_type, b.priority
  FROM aig_capability_model b
  JOIN sai_model_config c ON c.id = b.model_id
  JOIN sai_model_provider p ON p.id = c.provider_id
 WHERE p.provider_key = 'bluocto';

SELECT capability_code, data_level, preferred_deployment, allow_external, fallback_to_manual
  FROM aig_route_policy
 WHERE capability_code IN ('creative_direction_draft', 'creative_storyboard_draft',
                           'visual_dna_extract', 'deliverable_consistency')
 ORDER BY capability_code, data_level;

SELECT 'AIG_PROVIDER_BLUOCTO_DONE' AS marker;
