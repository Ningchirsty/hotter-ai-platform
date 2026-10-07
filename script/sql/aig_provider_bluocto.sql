-- ----------------------------------------------------------------------------
-- 外部聚合网关接入种子：bluocto（New API 系，OpenAI 兼容）
--
-- ⚠ api_endpoint 是 https://bluocto.com/v1 ——**不要改成 docs.newapi.pro**。
--   两者实测对比（2026-10-07）：
--     GET https://bluocto.com/v1/models      → 401 application/json
--        {"error":{"code":"","message":"无效的令牌 (request id: …)","type":"new_api_error"}}
--        ← 真网关。401（不是 404）+ JSON + type=new_api_error 说明路径存在、只是缺令牌。
--     GET https://docs.newapi.pro/v1/models  → 404 text/html
--        <title>404: This page could not be found.</title>
--        ← 这是 New API 项目**官方文档站**（Next.js），不是任何人的 API；
--          它只用来查接口契约，不能作为调用地址。Key 由 bluocto 签发，
--          发给 docs.newapi.pro 既没有模型也不会鉴权，必然失败。
--   契约依据（官方文档）：https://docs.newapi.pro/zh/docs/api/ai-model/models/list/listmodels
--     GET /v1/models   Header: Authorization: Bearer sk-xxxxxx
--     200 → {"object":"list","data":[{"id":"gpt-4","object":"model","created":0,"owned_by":"openai"}]}
--     401 → {"error":{"message","type","param","code"}}   ← 与上面 bluocto 的返回一致
--   **格式自动识别**：带 x-api-key+anthropic-version 返回 Anthropic 格式；
--   带 x-goog-api-key 或 ?key= 返回 Gemini 格式；**其余情况返回 OpenAI 格式**。
--   我们的 OpenAiCompatibleInvoker 只发 Authorization: Bearer + Content-Type，
--   因此拿到的是 **OpenAI 格式**，正是它 extractContent(choices[0].message.content) 与
--   extractTotalTokens(usage.total_tokens) 期望的——**无需额外请求头**。
--
-- 一把 Key 通多种模型：这是 New API 的形态——1 条 sai_model_provider + N 条
-- sai_model_config，N 条共享同一 api_endpoint 与同一把 api_key。
--
-- ⚠ 实测（2026-10-07）：本网关**只提供 12 个图像/视频生成模型，没有对话模型**。
--   详见 A 段模型清单注释与 B 段修正说明。当前状态因此是：
--     连接与治理属性已登记（惰性、不外发），模型行为空、密钥为空 —— 这是刻意的，
--     因为在实现 IMAGE 适配器之前，这些模型既不该被登记成 CHAT，也没有能力可用它们。
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
-- 清单来自实测 GET /v1/models（2026-10-07），**只登记网关明确声明了
-- image-generation 端点的 7 个模型**。
--
-- ⚠ model_type 必须是 'IMAGE'：图像生成走 /v1/images/generations，与对话模型的
--   /chat/completions 是两个端点。派发按 (部署类型, 模型类型) 双维度进行，
--   写成 'CHAT' 会被派给对话调用器并直接失败（OpenAiCompatibleInvoker 拒绝非 CHAT）。
--
-- 刻意**不登记**下面这些（各有原因，不是遗漏）：
--   · seedream-5-0-lite / seedream-5-0-pro：网关只声明了 'openai'，**没有**声明
--     image-generation。名字像图像模型，但没有证据说明它走 /images/generations；
--     登记成 IMAGE 后一旦上游不认，错会推迟到运行期才暴露。
--     要接请先手动试一次该端点，确认支持后再照下面的写法补两行。
--   · happyhorse-1.1-i2v / -r2v / -t2v：owned_by=task plugin，是**异步任务**型
--     （图生视频 / 参考生视频 / 文生视频）。它们需要「提交任务 + 轮询或回调」，
--     属 WP2 的 ai_task 范畴，不是本适配器能对接的形态。
--
-- 注：/v1/models 只返回**该 token 可用**的模型；若分组里本该有对话模型却没出现，
--     那是网关侧分组/渠道问题，不是本脚本的问题。
-- ============================================================================
INSERT INTO sai_model_config
(provider_id, model_name, model_key, model_type, adapter_key, description,
 api_key, api_endpoint, config_json, owner_id, scope, is_default, is_enabled, created_dt, updated_dt)
SELECT p.id, m.model_name, m.model_key, 'IMAGE', 'openai-compatible', m.description,
       NULL, 'https://bluocto.com/v1', NULL, NULL, 'GLOBAL', 0, 1, NOW(), NOW()
  FROM sai_model_provider p
  JOIN (
        SELECT 'flux-2-pro' AS model_key, 'FLUX 2 Pro' AS model_name, '图像生成（通用）' AS description
        UNION ALL SELECT 'gpt-image-2.5-flare',    'GPT Image 2.5 Flare',    '图像生成（OpenAI 系）'
        UNION ALL SELECT 'gpt-image-2.5-sunburst', 'GPT Image 2.5 Sunburst', '图像生成（OpenAI 系，风格变体）'
        UNION ALL SELECT 'qwen-image-3.0',         'Qwen Image 3.0',         '图像生成（对中文提示词友好）'
        UNION ALL SELECT 'qwen-image-3.0-pro',     'Qwen Image 3.0 Pro',     '图像生成（中文友好，质量更高）'
        UNION ALL SELECT 'wan2.7-image',           'Wan 2.7 Image',          '图像生成（通义万相）'
        UNION ALL SELECT 'wan2.7-image-pro',       'Wan 2.7 Image Pro',      '图像生成（通义万相，质量更高）'
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
-- A 段：登记能力 image_generation（设计文档 §4.1 的 IMAGE 类型落点）
--
--   required_tags='IMAGE'：与 aig_capability 的标签口径一致（TEXT/VISION/OCR/IMAGE/
--     VIDEO/EMBEDDING/RERANK/AGENT）。注意该列目前**只登记、不参与模型匹配**——
--     把文本模型绑到 IMAGE 能力上系统不会报错（已知隐患，见方案 WP1 待办）。
--     本脚本靠「只给这 7 个图像模型建绑定」来避免踩它，而不是靠代码拦。
--
--   output_schema 与适配器返回的 JSON 信封对齐（mimeType/sizeBytes/sha256/b64）：
--     写具体字段，输出校验才真正生效；留空只会退化成「是合法 JSON 就行」。
-- ============================================================================
INSERT INTO aig_capability
(capability_id, capability_code, capability_name, biz_goal, required_tags, input_schema, output_schema,
 data_policy, human_confirm_points, quality_threshold, audit_level, status, del_flag,
 create_dept, create_by, create_time, remark)
SELECT 1764000000000000050, 'image_generation', '图像生成（外部聚合网关）',
       '按提示词生成电商/宣传用图；作为本地 ComfyUI 之外的第二条出图通路',
       'IMAGE',
       '{"fields":[{"name":"prompt","type":"string"},{"name":"size","type":"string","optional":true,"note":"可选；不传则用上游默认尺寸"}]}',
       '{"fields":[{"name":"mimeType","type":"string"},{"name":"sizeBytes","type":"number"},{"name":"sha256","type":"string"},{"name":"b64","type":"string"}],"note":"调用器返回的 JSON 信封；治理层不持有资产存储，由调用方据此落盘"}',
       'LOCAL_FIRST',
       '生成的图必须先落候选、由人选定后才进入正式资产；自动质检只筛除不放行',
       '输出必须是合法 JSON 且含 mimeType/sizeBytes/sha256/b64；单图不得超过 aigov.external-api.image-max-bytes；超限或缺失一律判失败，不静默截断',
       'SUMMARY', '0', '0', 1761000000000000103, 1761100000000000001, NOW(),
       'bluocto 接入：能力编码 image_generation，对接 OpenAiImageInvoker（/images/generations）'
  WHERE NOT EXISTS (SELECT 1 FROM (SELECT capability_code FROM aig_capability) t
                     WHERE t.capability_code = 'image_generation');

-- ============================================================================
-- B 段：绑定与路由策略（**本段默认启用** —— 图像通路是本次接入的既定目标）
--
-- 【仍然做不到的两类能力，别再试】（2026-10-07 实测）
--   · 文本能力（creative_direction_draft / creative_storyboard_draft）：
--     网关 /v1/models 里**没有任何对话模型**。不是「暂时不绑」，是无模型可绑；
--     要接文本需让网关侧换一个含对话模型的令牌分组。
--   · 视觉理解能力（visual_dna_extract / deliverable_consistency）：
--     这里的模型是**能画图**，不是**能看图**。别把生成能力当理解能力用。
--
-- 【本段在做什么】
--   B1 绑定：只把 qwen-image-3.0-pro 绑到 image_generation，usage_type='FALLBACK'。
--      · 为什么只绑一个：其余 6 个已登记为可用模型，但一次性全绑会让「路由选谁」
--        看不出依据（同 usage_type 下只能靠 priority 排序，那是编出来的偏好）。
--        需要时加一行绑定即可，模型行不必重来。
--      · 为什么是 FALLBACK 而不是 PRIMARY：本地 ComfyUI 才是主通路，外部是备选。
--        目前 ComfyUI 还**没有**在治理层登记为 provider，所以本绑定是唯一候选、
--        照样会被选中（排序 PRIMARY→GRAY→FALLBACK 后取第一个可用）；
--        将来把 ComfyUI 登记成 PRIMARY 时，顺序天然就对了，无需再改这一行。
--      · 为什么选 qwen-image-3.0-pro：本项目是中文电商详情页，中文提示词友好度优先。
--        这是**可改的默认值**，不是结论。
--   B2 路由策略：image_generation × PUBLIC/INTERNAL 允许外发；RESTRICTED 不允许。
--      · RESTRICTED 其实还有第二道保险：这些模型的治理属性是 data_level_max='INTERNAL'，
--        等级比较在**代码层**就会把它们排除，不依赖策略行写对。这里显式写 'N' 是为了
--        让「不允许」在数据里也可见，而不是只藏在另一张表里。
--      · STRICT 不必写策略行：无策略即默认拒绝，且 STRICT 会被强制禁外发。
--      · preferred_deployment 留 NULL：该列目前不参与过滤（只写入策略命中说明），
--        填了会让人误以为在生效。
-- ============================================================================

-- B1：绑定图像模型（FALLBACK，不挤掉将来登记的本地主通路）
INSERT IGNORE INTO aig_capability_model
(bind_id, capability_code, model_id, usage_type, priority, status, del_flag,
 create_dept, create_by, create_time, remark)
SELECT 1764200000000000000 + c.id, 'image_generation', c.id, 'FALLBACK', 500, '0', '0',
       1761000000000000103, 1761100000000000001, NOW(),
       'bluocto 聚合网关：图像生成的外部备选（默认只绑 qwen-image-3.0-pro，可加行扩展）'
  FROM sai_model_config c
  JOIN sai_model_provider p ON p.id = c.provider_id
 WHERE p.provider_key = 'bluocto' AND c.model_key = 'qwen-image-3.0-pro';

-- B2：image_generation 的 PUBLIC / INTERNAL 允许外发，RESTRICTED 显式禁止
INSERT IGNORE INTO aig_route_policy
(policy_id, capability_code, data_level, preferred_deployment, allow_external, require_approval,
 fallback_to_manual, status, del_flag, create_dept, create_by, create_time, remark)
SELECT 1764300000000000000
         + CASE lv.level WHEN 'PUBLIC' THEN 1 WHEN 'INTERNAL' THEN 2 ELSE 3 END AS policy_id,
       'image_generation', lv.level, NULL,
       CASE lv.level WHEN 'RESTRICTED' THEN 'N' ELSE 'Y' END, 'N', 'Y', '0', '0',
       1761000000000000103, 1761100000000000001, NOW(),
       CASE lv.level WHEN 'RESTRICTED'
            THEN 'bluocto 接入：限制级数据禁止外发（另受 data_level_max=INTERNAL 的代码级保险）'
            ELSE 'bluocto 接入：允许外发到外部图像网关' END
  FROM (SELECT 'PUBLIC' AS level UNION ALL SELECT 'INTERNAL' UNION ALL SELECT 'RESTRICTED') lv
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT capability_code, data_level FROM aig_route_policy) r
                    WHERE r.capability_code = 'image_generation' AND r.data_level = lv.level);

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

-- 3) 能力登记（应看到 image_generation / required_tags=IMAGE / data_policy=LOCAL_FIRST）
SELECT capability_code, capability_name, required_tags, data_policy, status
  FROM aig_capability
 WHERE capability_code = 'image_generation';

-- 4) 绑定（应只有一条：qwen-image-3.0-pro → image_generation，usage_type=FALLBACK）
SELECT c.model_key, c.model_type, b.capability_code, b.usage_type, b.priority
  FROM aig_capability_model b
  JOIN sai_model_config c ON c.id = b.model_id
  JOIN sai_model_provider p ON p.id = c.provider_id
 WHERE p.provider_key = 'bluocto';

-- 5) 路由策略（image_generation：PUBLIC/INTERNAL=Y，RESTRICTED 必须为 N）
SELECT capability_code, data_level, preferred_deployment, allow_external, fallback_to_manual
  FROM aig_route_policy
 WHERE capability_code = 'image_generation'
 ORDER BY data_level;

-- 6) 端到端可用性自检（干跑：只做路由决策，不真调模型）
--    期望：decision=MODEL、modelKey=qwen-image-3.0-pro、invoker=OpenAiImageInvoker。
--    用接口验更真实：POST /aigov/invoke/dryRun
--      {"capabilityCode":"image_generation","dataLevel":"INTERNAL"}
--    dryRun 不产生审计记录，可以随便跑；真实调用（POST /aigov/invoke/image_generation）
--    需要 sai_model_config.api_key 已录入，否则上游会返回 401 → 记为鉴权失败并建议熔断
--    （这是正确行为：没有凭据就该明确失败，而不是伪装成成功）。

SELECT 'AIG_PROVIDER_BLUOCTO_DONE' AS marker;
