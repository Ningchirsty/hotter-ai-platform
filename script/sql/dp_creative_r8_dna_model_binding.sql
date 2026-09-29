-- ------------------------------------------------------------------
-- R8 续：为 visual_dna_extract 绑定**本地视觉模型**，让 DNA 抽取真正可调用
--
-- 前置（已完成）：在 A100 机器（项目自己的推理机 ai-center / 192.168.2.223）上
--   `ollama pull qwen2.5vl:3b`（约 3.2GB；该机 2026-09-29 实测 53GB 可用，Ollama 0.34.3）。
--
-- 【为什么必须是本地模型，而不是台账里已有的那些】
--   本能力配的路由策略是三个数据等级一律 LOCAL、allow_external='N'：产品参考图不该出网。
--   台账里其余模型（glm-5.1 / nvidia / openrouter-free）都是外发端点，即使支持视觉也不能用于产品图。
--   本机原有一个本地模型 qwen2.5:7b-instruct，但它是**纯文本**（capabilities 里没有 vision），
--   绑上去会得到一份"没看过图"的基因却以模型结论的样子出现——比诚实回落更糟，这正是 R8 首轮
--   刻意不绑的原因。现在补上真正的看图模型。
--
-- 【生命周期取 GRAY 而不是 PRODUCTION】
--   模型刚拉下来、还没有经过人工验收。GRAY 在 AigLifecycleStatusEnum 里 isCallable()=true，
--   路由可用；等逐字段验收一段时间后再由人决定是否转 PRODUCTION。这与既有的本地文本模型
--   （aig_model_governance 1764000000000000011 也是 GRAY）口径一致。
--
-- 幂等：全部 NOT EXISTS 守卫，可重复执行。
-- 回滚：见文件末尾。
-- ------------------------------------------------------------------

-- 1) 模型登记（sai_model_config）--------------------------------------
-- 注意本表**没有 status 列**，启用与否看 is_enabled；model_key 必须与 Ollama 里的模型名完全一致，
-- 因为调用器把 model_key 直接当作请求体里的 "model" 字段发出去。

INSERT INTO sai_model_config
(id, provider_id, model_name, model_key, model_type, adapter_key, description, api_key, api_endpoint,
 config_json, owner_id, scope, is_default, is_enabled)
SELECT 1764000000000000024, 3, 'Qwen2.5-VL-3B（本地 Ollama，看图）', 'qwen2.5vl:3b', 'CHAT',
       'openai-compatible',
       '视觉基因抽取专用：本地看图模型，数据不出公司（R8 续）', NULL,
       'http://192.168.2.223:11434/v1', NULL, NULL, 'GLOBAL', 0, 1
  WHERE NOT EXISTS (SELECT 1 FROM (SELECT model_key FROM sai_model_config) t
                     WHERE t.model_key = 'qwen2.5vl:3b');

-- 2) 治理属性（aig_model_governance）---------------------------------
-- data_level_max 取最高档：基因抽取可能涉及未上市产品的参考图。
-- 路由的四道硬条件（治理行存在 / 生命周期可调用 / 等级够 / is_enabled=1）在这里一次配齐。

INSERT INTO aig_model_governance
(governance_id, model_id, deployment_type, data_level_max, lifecycle_status, secret_ref, input_limits,
 output_limits, cost_limit, owner_tech, owner_biz, owner_security, valid_from, valid_to, health_status,
 health_time, status, del_flag, create_dept, create_by, create_time, remark)
SELECT 1764000000000000012, 1764000000000000024, 'LOCAL', 'RESTRICTED', 'GRAY', NULL,
       '单次最多 6 张图、单张解码后 ≤8MB（治理层 ModelImagePayload 限制）',
       '只接受合法 JSON；色值 #RRGGBB；枚举须在集合内', '本地 GPU，无按次计费',
       'creative', 'creative', 'creative', NULL, NULL, 'UNKNOWN', NULL, '0', '0',
       1761000000000000103, 1761100000000000001, NOW(),
       'R8 续：视觉基因抽取的本地看图模型；先灰度，逐字段验收后再考虑转 PRODUCTION'
  WHERE NOT EXISTS (SELECT 1 FROM (SELECT model_id FROM aig_model_governance) g
                     WHERE g.model_id = 1764000000000000024);

-- 3) 能力↔模型绑定（aig_capability_model）----------------------------

INSERT INTO aig_capability_model
(bind_id, capability_code, model_id, usage_type, priority, status, del_flag,
 create_dept, create_by, create_time, remark)
SELECT 1764000000000000043, 'visual_dna_extract', 1764000000000000024, 'PRIMARY', 10, '0', '0',
       1761000000000000103, 1761100000000000001, NOW(),
       'R8 续：DNA 抽取从"只登记能力与路由、无模型"改为真正绑定本地视觉模型'
  WHERE NOT EXISTS (SELECT 1 FROM (SELECT capability_code FROM aig_capability_model) b
                     WHERE b.capability_code = 'visual_dna_extract');

-- 4) 核对 -------------------------------------------------------------

SELECT c.capability_code, m.model_key, m.api_endpoint, m.is_enabled,
       g.deployment_type, g.data_level_max, g.lifecycle_status,
       b.usage_type, b.priority, b.status
  FROM aig_capability c
  JOIN aig_capability_model b ON b.capability_code = c.capability_code
  JOIN sai_model_config m ON m.id = b.model_id
  JOIN aig_model_governance g ON g.model_id = b.model_id
 WHERE c.capability_code = 'visual_dna_extract';

SELECT 'DP_CREATIVE_R8_DNA_MODEL_BINDING_DONE' AS marker;

-- ------------------------------------------------------------------
-- 回滚（如需）：
--   DELETE FROM aig_capability_model WHERE capability_code='visual_dna_extract';
--   DELETE FROM aig_model_governance WHERE model_id=1764000000000000024;
--   DELETE FROM sai_model_config WHERE model_key='qwen2.5vl:3b';
-- 并在 223 上 `ollama rm qwen2.5vl:3b` 释放约 3.2GB。
-- ------------------------------------------------------------------
