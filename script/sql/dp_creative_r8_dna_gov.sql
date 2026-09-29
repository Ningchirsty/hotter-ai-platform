-- ------------------------------------------------------------------
-- V0.2 / R8：FIX-006 —— 正式登记视觉基因抽取能力 visual_dna_extract
--
-- 背景：`VisualBrainAdapter` 的默认能力编码就是 `visual_dna_extract`
-- （`VisualBrainAdapter.java:50`，可用配置 `creative.dna.capability-code` 覆盖），
-- 注释里也写明了"治理台需注册同名能力 + 路由策略 + 模型绑定才能生效"。
-- 但 R4 的治理种子（dp_creative_r4_gov.sql）只登记了 creative_direction_draft /
-- creative_storyboard_draft 两个**文本**能力，于是 DNA 抽取的 dryRun 一直返回
-- "能力不存在或已停用"，页面上只能看到"未启用"，看不出到底缺哪一步。
--
-- 本脚本做三件事：
--   1) 登记能力 visual_dna_extract（required_tags 标 VISION：它是**看图**的能力，
--      不是文本润色——这一点必须写进治理数据，否则后人会给它绑文本模型）；
--   2) 配三个数据等级的路由策略（一律 LOCAL、禁外发，与 R4 同口径）；
--   3) **刻意不写模型绑定**，理由见下。
--
-- 【为什么不绑模型 · 必须如实说明】
--   DNA 抽取是**视觉**任务：`VisualBrainAdapter.analyze()` 会把参考图 base64 作为
--   payload 的 images 发出去（`VisualBrainAdapter.java:160/338-348`），而当前 A100 机器上
--   Ollama 只装了 `qwen2.5:7b-instruct`（**纯文本**，4.36GB），本地调用器
--   `CreativeLocalChatInvoker` 也是文本调用器（`buildBody` 只发 system+user 文本，
--   不转发 images）。
--   如果把文本模型绑到这个能力上，路由会判 MODEL 并"成功返回"，产出的却是一份
--   **没看过图**的基因——那比现在"诚实回落"更糟：它会以模型结论的样子出现。
--   所以这里只登记能力与路由，绑定留给真正部署了视觉模型之后（见文件末尾的启用步骤）。
--
-- 幂等：全部 NOT EXISTS 守卫，可重复执行。
-- ------------------------------------------------------------------

-- 1) 能力登记（aig_capability）---------------------------------------

INSERT INTO aig_capability
(capability_id, capability_code, capability_name, biz_goal, required_tags, input_schema, output_schema,
 data_policy, human_confirm_points, quality_threshold, audit_level, status, del_flag,
 create_dept, create_by, create_time, remark)
SELECT 1764000000000000023, 'visual_dna_extract', '视觉基因抽取（看图）',
       '看参考图，补全/修正视觉基因（风格关键词、配色、光线、留白、产品占比、场景类型）',
       'VISION',
       '{"fields":[{"name":"images","type":"array","note":"参考图 base64（内联）"},{"name":"seedDna","type":"string"},{"name":"task","type":"string"}]}',
       '{"fields":[],"note":"只要求合法 JSON；styleKeywords/colors/lighting/productRatio/sceneType 由业务侧逐字段验收，不合格整体丢弃并回落参数化种子"}',
       'LOCAL_FIRST',
       '基因必须由人确认并锁定，模型只提供可采纳字段',
       '输出必须是合法 JSON；色值必须 #RRGGBB；枚举必须在集合内；数值 0~100；核心字段一个都没有即视为不可用',
       'SUMMARY', '0', '0', 1761000000000000103, 1761100000000000001, NOW(),
       'R8/FIX-006：登记能力与路由，让"缺什么"可见；未绑定模型（当前无视觉模型，绑文本模型会产出没看过图的基因）'
  WHERE NOT EXISTS (SELECT 1 FROM (SELECT capability_code FROM aig_capability) t
                     WHERE t.capability_code = 'visual_dna_extract');

-- 2) 路由策略（aig_route_policy）：三个数据等级都禁外发 ----------------

INSERT INTO aig_route_policy
(policy_id, capability_code, data_level, preferred_deployment, allow_external, require_approval,
 fallback_to_manual, status, del_flag, create_dept, create_by, create_time, remark)
SELECT 1764000000000000036
         + CASE lv.level WHEN 'PUBLIC' THEN 1 WHEN 'INTERNAL' THEN 2 ELSE 3 END AS policy_id,
       'visual_dna_extract', lv.level, 'LOCAL', 'N', 'N', 'Y', '0', '0',
       1761000000000000103, 1761100000000000001, NOW(),
       'R8/FIX-006：基因抽取涉及产品图，一律本地私有部署，禁止外发'
  FROM (SELECT 'PUBLIC' AS level UNION ALL SELECT 'INTERNAL' UNION ALL SELECT 'RESTRICTED') lv
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT capability_code, data_level FROM aig_route_policy) p
                    WHERE p.capability_code = 'visual_dna_extract' AND p.data_level = lv.level);

-- 3) 刻意不写 aig_capability_model 绑定（见文件头说明）。启用步骤（需要人做）：
--    a) 在 A100 机器上拉一个视觉模型（注意：该机磁盘 2026-09-28 实测只剩 5.5GB，
--       先清磁盘再 pull，例如 `ollama pull qwen2.5vl:7b`）；
--    b) 登记模型：sai_model_config（model_key=视觉模型名，model_type=CHAT，
--       api_endpoint=http://192.168.2.223:11434/v1）；
--    c) 治理属性：aig_model_governance（deployment_type=LOCAL、data_level_max=RESTRICTED）；
--    d) 绑定：aig_capability_model（capability_code=visual_dna_extract，usage_type=PRIMARY）；
--    e) 重新跑一次 DNA 抽取，页面上来源会从"由已确认事实推导"变成"视觉模型 + 人工确认"。
--    代码侧已就绪：CreativeLocalChatInvoker 已声明认领本能力，并在 payload 带图时
--    按 OpenAI 视觉格式（content 数组 + image_url data URI）发送。

-- 4) 核对 ------------------------------------------------------------

SELECT cap.capability_code, cap.capability_name, cap.required_tags, r.data_level,
       r.preferred_deployment, r.allow_external, r.fallback_to_manual
  FROM aig_capability cap
  LEFT JOIN aig_route_policy r ON r.capability_code = cap.capability_code
 WHERE cap.capability_code = 'visual_dna_extract'
 ORDER BY r.data_level;

SELECT 'DP_CREATIVE_R8_DNA_GOV_DONE' AS marker;
