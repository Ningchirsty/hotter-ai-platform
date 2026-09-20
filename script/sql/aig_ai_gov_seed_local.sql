-- ============================================================================
-- AI 治理层 · 本地验证夹具（SEED / FIXTURE）
-- ----------------------------------------------------------------------------
-- 用途：仅用于本地端到端验证 talent_match 的「本地优先」路由链路。
--
-- 为什么需要它：
--   aig_ai_gov.sql 只建表 + 灌入能力/策略，未灌「能力-模型绑定」与「模型治理属性」。
--   缺这两张表的数据时，路由判定会在「步骤3」找不到可用候选模型，进而走
--   fallback_to_manual='Y' 转 MANUAL。此时冒烟测试仍会写出审计行（externalCall=N），
--   表面通过，却完全没有证明 MODEL → LOCAL 的真实调用路径。
--
-- ⚠️ 禁止在生产环境执行本文件。
-- ⚠️ 本夹具会向 sai_model_config 插入一条「本地规则引擎」记录（供应商取 Ollama，
--    语义上最贴近本地推理）。它不代表任何真实模型部署，只是为了让治理层能选到
--    一个 deployment_type='LOCAL' 的候选模型。生产环境必须按真实部署形态登记模型
--    与治理属性（deployment_type / data_level_max / lifecycle_status）。
-- ============================================================================

-- 1) 夹具模型：本地规则引擎（幂等：按 model_key 判重）
INSERT INTO sai_model_config (provider_id, model_name, model_key, model_type, adapter_key,
                              description, scope, is_default, is_enabled)
SELECT 3, '本地规则引擎（验证夹具）', 'local-rule-v1', 'CHAT', 'local-rule',
       'AI 治理层本地验证夹具，非真实模型部署', 'LOCAL', 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sai_model_config WHERE model_key = 'local-rule-v1');

-- 2) 治理属性：LOCAL 部署 / 数据等级上限 RESTRICTED / 生命周期 PRODUCTION（可调用）
INSERT INTO aig_model_governance (governance_id, model_id, deployment_type, data_level_max,
                                  lifecycle_status, status, del_flag,
                                  owner_tech, owner_biz, owner_security, remark)
SELECT 1763000000000003001, c.id, 'LOCAL', 'RESTRICTED',
       'PRODUCTION', '0', '0',
       'tech-owner-fixture', 'biz-owner-fixture', 'security-owner-fixture', '本地验证夹具'
FROM sai_model_config c
WHERE c.model_key = 'local-rule-v1'
  AND NOT EXISTS (SELECT 1 FROM aig_model_governance g WHERE g.model_id = c.id);

-- 3) 能力-模型绑定：talent_match 主选（usage_type=PRIMARY，priority 最小者优先）
INSERT INTO aig_capability_model (bind_id, capability_code, model_id, usage_type,
                                  priority, status, del_flag, remark)
SELECT 1763000000000004001, 'talent_match', c.id, 'PRIMARY',
       10, '0', '0', '本地验证夹具'
FROM sai_model_config c
WHERE c.model_key = 'local-rule-v1'
  AND NOT EXISTS (SELECT 1 FROM aig_capability_model b
                  WHERE b.capability_code = 'talent_match' AND b.model_id = c.id);

-- 4) 让「字段脱敏」可被验证：端点与密钥引用必须非空，否则
--    「有 aig:model:secret 才看得到」这条规则无法被观察（看到 null 不能证明是脱敏还是本来就没有）。
--    这里的值是无意义的占位，不含任何真实凭据。
UPDATE sai_model_config SET api_endpoint = 'http://127.0.0.1:11434/v1'
WHERE model_key = 'local-rule-v1' AND (api_endpoint IS NULL OR api_endpoint = '');

UPDATE aig_model_governance SET secret_ref = 'REF://fixture/local-rule-v1'
WHERE governance_id = 1763000000000003001 AND (secret_ref IS NULL OR secret_ref = '');

-- 5) 验证结果
SELECT '夹具模型' AS item, COUNT(*) AS cnt FROM sai_model_config WHERE model_key = 'local-rule-v1'
UNION ALL
SELECT '治理属性', COUNT(*) FROM aig_model_governance WHERE governance_id = 1763000000000003001
UNION ALL
SELECT '能力绑定', COUNT(*) FROM aig_capability_model WHERE bind_id = 1763000000000004001
UNION ALL
SELECT '端点可验证', COUNT(*) FROM sai_model_config WHERE model_key = 'local-rule-v1' AND api_endpoint IS NOT NULL
UNION ALL
SELECT '密钥引用可验证', COUNT(*) FROM aig_model_governance WHERE governance_id = 1763000000000003001 AND secret_ref IS NOT NULL;
