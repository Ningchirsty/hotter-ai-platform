-- ----------------------------------------------------------------------------
-- AI 治理层 · 黄金用例种子（设计 §13.2）—— WP3 第七步 b
--
-- 目标库：平台库（与 aig_agent_registry.sql / aig_agent_registry_seed.sql 同库）
-- 前置：先跑 aig_agent_registry.sql（建表）与 aig_agent_registry_seed.sql（四个内置 Agent）
--
-- 【这批用例评测谁】策划 Agent（agent_code = creative_planning，
-- 版本 1765000000000000011 / 0.1.0）。它在注册种子里已把这两条用例写进
-- config_json.golden_cases——评测时「本次用例集合必须等于版本声明的集合」，
-- 而声明放在 config_json 里（Agent/Skill 版本没有 manifest_json；Package 版本反过来）。
--
-- 【为什么这两条是「可判定」的】策划引擎是确定性基座（参数化模板 + 可复现 variantSeed，
-- 无随机数、无时钟），因此「同样的输入 → 逐字相同的输出」不是愿望，而是可断言的判据：
--   · case-plan-deterministic：结构齐全 + 基因自洽 + 三方向 + 文案不含档位枚举 + 可复现
--   · case-plan-blank-levels：缺档位时必须如实说「未设置」，不能默认成某个档
--
-- 【成本上限刻意写 0】策划 Agent 不调模型（成本「可知且为 0」）。把 cost_max 写成 0
-- 意味着：一旦有人给确定性基座接上模型调用，这条用例就会失败——成本范围不只是记账，
-- 它是「这个 Agent 不该花钱」这条约束的可执行版本。
--
-- 【输入快照是内联的】input_snapshot_ref = 'inline:<json>'：用例自带全部输入，
-- 因此评测在确定的输入上运行、可重放。代价是受该列 varchar(500) 限制；
-- 更大的快照将来引 'oss:<对象键>'（执行器对认不出的前缀会报错，不会猜一个默认输入）。
--
-- 幂等：全部 insert ... where not exists，可安全重复执行（本文件不含任何 drop / update）。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、策划 Agent·确定性（结构 + 基因自洽 + 可复现）
-- ----------------------------
insert into aig_evaluation_case
(case_id, case_code, case_name, case_type, scenario_code, input_snapshot_ref, expected_json,
 rubric_json, cost_min, cost_max, data_level, classification, status, del_flag,
 create_dept, create_by, create_time, remark)
select 1767000000000000001, 'case-plan-deterministic', '策划 Agent·确定性（结构+基因+可复现）', 'PLAN', null,
       'inline:{"product_name":"鸢尾花香水","variant_seed":0,"facts":{"product_name":"鸢尾花香水","color":"蓝紫渐变"},"dna":{"styleKeywords":["极简","自然"],"colors":{"background":"#F5F5F3","primary":"#2E6B4F"},"lighting":{"type":"SOFT","direction":"FRONT"},"productRatio":{"min":15,"max":30},"saturation":"LOW","contrastLevel":"MEDIUM","whitespaceLevel":"HIGH"}}',
       '{"required_paths":["directions","screens","direction_count","screen_count","dna_valid","no_enum_leak","reproducible_probe"],"equals":{"direction_count":3,"deterministic":true,"dna_valid":true,"no_enum_leak":true,"reproducible_probe":true,"drafts_mention_product":true},"min_items":{"directions":3,"screens":1}}',
       null, 0.0000, 0.0000, 'INTERNAL', 'DETERMINISTIC_ENGINE', '0', '0',
       1761000000000000103, 1761100000000000001, now(),
       '设计 §13.2；判据全部可判定（不做人工评分，故 rubric_json 留空）；成本上限 0 = 不调模型'
  where not exists (select 1 from (select case_code from aig_evaluation_case) t
                     where t.case_code = 'case-plan-deterministic');

-- ----------------------------
-- 2、策划 Agent·缺档位如实说「未设置」
-- ----------------------------
insert into aig_evaluation_case
(case_id, case_code, case_name, case_type, scenario_code, input_snapshot_ref, expected_json,
 rubric_json, cost_min, cost_max, data_level, classification, status, del_flag,
 create_dept, create_by, create_time, remark)
select 1767000000000000002, 'case-plan-blank-levels', '策划 Agent·缺档位说「未设置」', 'PLAN', null,
       'inline:{"product_name":"鸢尾花香水","variant_seed":0,"facts":{"product_name":"鸢尾花香水","color":"蓝紫渐变"},"dna":{"styleKeywords":["极简","自然"],"colors":{"background":"#F5F5F3","primary":"#2E6B4F"},"lighting":{"type":"SOFT","direction":"FRONT"},"productRatio":{"min":15,"max":30}}}',
       '{"required_paths":["directions","screens","direction_count","dna_valid","no_enum_leak","reproducible_probe"],"equals":{"direction_count":3,"dna_valid":true,"no_enum_leak":true,"reproducible_probe":true},"min_items":{"directions":3,"screens":1},"must_contain":["未设置"]}',
       null, 0.0000, 0.0000, 'INTERNAL', 'DETERMINISTIC_ENGINE', '0', '0',
       1761000000000000103, 1761100000000000001, now(),
       '基因里缺饱和度/对比度/留白时必须写「未设置」：默认成某个档 = 平台替用户编了一个决定'
  where not exists (select 1 from (select case_code from aig_evaluation_case) t
                     where t.case_code = 'case-plan-blank-levels');

-- ----------------------------
-- 3、视觉 DNA Agent·参考图确定性实测
-- ----------------------------
insert into aig_evaluation_case
(case_id, case_code, case_name, case_type, scenario_code, input_snapshot_ref, expected_json,
 rubric_json, cost_min, cost_max, data_level, classification, status, del_flag,
 create_dept, create_by, create_time, remark)
select 1767000000000000003, 'case-visual-dna-solid-bg', '视觉 DNA·白底红块实测要对', 'VISUAL_DNA', null,
       'inline:{"reference":"classpath:creative/eval/ref-red-block-200.png"}',
       '{"required_paths":["values","recommendations","width","height","observed_ratio","recommendation_count"],"equals":{"width":200,"height":200,"values.colors.primary":"#FF0000","values.saturation":"HIGH","values.sceneType":"纯色底","deterministic_only":true,"model_part_evaluated":false},"min_items":{"recommendations":3}}',
       null, 0.0000, 0.0000, 'INTERNAL', 'DETERMINISTIC_ANALYSIS', '0', '0',
       1761000000000000103, 1761100000000000001, now(),
       '只覆盖「参考图确定性实测」这一段：产出里 model_part_evaluated=false，模型分析那段不在本条用例范围；快照图随平台发布（classpath:creative/eval/）'
  where not exists (select 1 from (select case_code from aig_evaluation_case) t
                     where t.case_code = 'case-visual-dna-solid-bg');

-- ----------------------------
-- 4、视觉 QA Agent·合格交付图判过
-- ----------------------------
insert into aig_evaluation_case
(case_id, case_code, case_name, case_type, scenario_code, input_snapshot_ref, expected_json,
 rubric_json, cost_min, cost_max, data_level, classification, status, del_flag,
 create_dept, create_by, create_time, remark)
select 1767000000000000004, 'case-visual-qa-clean-800', '视觉 QA·合格图判过', 'IMAGE_QA', null,
       'inline:{"artifact":"classpath:creative/eval/qa-clean-800.png","rules":{"schema":"screen-qa/1","square":true,"minSide":800,"alphaForbidden":true,"whiteBackground":{"enabled":true,"minEdgeWhiteness":0.90},"subjectRatio":{"enabled":true,"min":0.10},"edgeBleed":{"enabled":true,"maxRatio":0.05}}}',
       '{"required_paths":["metrics","findings","verdict","failed_codes"],"equals":{"verdict":"PASS","passed":true,"configured":true,"metrics.width":800,"metrics.height":800},"min_items":{"findings":1}}',
       null, 0.0000, 0.0000, 'INTERNAL', 'DETERMINISTIC_ANALYSIS', '0', '0',
       1761000000000000103, 1761100000000000001, now(),
       '规则写在用例里（rules_source=case_snapshot）：评审看用例行就知道这次按什么规则判的；规则随平台漂移不会改历史结论'
  where not exists (select 1 from (select case_code from aig_evaluation_case) t
                     where t.case_code = 'case-visual-qa-clean-800');

-- ----------------------------
-- 5、视觉 QA Agent·长方形图必须判不过
-- ----------------------------
-- 注意语义：这条用例的**运行结论是 PASS**——因为「检查器对这张图给出了预期的判定（判不过）」
-- 就是用例要断言的事。不是「这张图合格」。用例通过 ≠ 产物合格。
insert into aig_evaluation_case
(case_id, case_code, case_name, case_type, scenario_code, input_snapshot_ref, expected_json,
 rubric_json, cost_min, cost_max, data_level, classification, status, del_flag,
 create_dept, create_by, create_time, remark)
select 1767000000000000005, 'case-visual-qa-not-square', '视觉 QA·长方形图必须判不过', 'IMAGE_QA', null,
       'inline:{"artifact":"classpath:creative/eval/qa-not-square-800x600.png","rules":{"schema":"screen-qa/1","square":true,"minSide":800,"alphaForbidden":true,"whiteBackground":{"enabled":true,"minEdgeWhiteness":0.90},"subjectRatio":{"enabled":true,"min":0.10},"edgeBleed":{"enabled":true,"maxRatio":0.05}}}',
       '{"required_paths":["metrics","findings","verdict","failed_codes"],"equals":{"passed":false,"configured":true},"min_items":{"findings":1},"must_contain":["CANVAS_SQUARE"]}',
       null, 0.0000, 0.0000, 'INTERNAL', 'DETERMINISTIC_ANALYSIS', '0', '0',
       1761000000000000103, 1761100000000000001, now(),
       '这条用例的期望是「判不过」：阈值到了就翻脸，否则检查器只是装饰。用例通过 ≠ 产物合格'
  where not exists (select 1 from (select case_code from aig_evaluation_case) t
                     where t.case_code = 'case-visual-qa-not-square');

-- ----------------------------
-- 6、生成任务构建 Agent·由基因派生提示词
-- ----------------------------
-- 覆盖范围刻意写明：这条用例只跑「草案/prompt 派生」那一段。实际提交（ImageTaskSubmissionService）
-- 花钱且结果非确定，归沙箱试跑与连通性测试——把提交塞进黄金用例的后果是「每次跑评测都花钱」，
-- 那样没人会去跑它。产出里的 submission_not_performed=true 就是这句声明的机器可读版本。
insert into aig_evaluation_case
(case_id, case_code, case_name, case_type, scenario_code, input_snapshot_ref, expected_json,
 rubric_json, cost_min, cost_max, data_level, classification, status, del_flag,
 create_dept, create_by, create_time, remark)
select 1767000000000000006, 'case-generation-build-dna', '生成任务构建·由基因派生提示词', 'IMAGE_QA', null,
       'inline:{"subject":"鸢尾花香水","screen_hint":"HERO 主图","screen_text":"画面独白：鸢尾花香水静置在木桌上，晨光斜切","variant_seed":0,"dna":{"styleKeywords":["极简","自然"],"colors":{"background":"#F5F5F3","primary":"#2E6B4F","secondary":"#C9D8CE"},"lighting":{"type":"SOFT","direction":"FRONT"},"productRatio":{"min":15,"max":30},"saturation":"LOW","contrastLevel":"MEDIUM","whitespaceLevel":"HIGH"}}',
       '{"required_paths":["prompt","negative_prompt","applied","prompt_length","has_negative_prompt"],"equals":{"has_negative_prompt":true,"dna_provided":true,"submission_not_performed":true,"deterministic_only":true,"model_part_evaluated":false,"reproducible_probe":true},"min_items":{"applied":3},"must_contain":["#2E6B4F","画面独白"]}',
       null, 0.0000, 0.0000, 'INTERNAL', 'DETERMINISTIC_ENGINE', '0', '0',
       1761000000000000103, 1761100000000000001, now(),
       '判据钉三件事：主色与屏文案真的进了提示词、负向提示词非空、覆盖范围声明（没提交、没调模型）'
  where not exists (select 1 from (select case_code from aig_evaluation_case) t
                     where t.case_code = 'case-generation-build-dna');

-- ----------------------------
-- 7、生成任务构建 Agent·没有基因时走内置兜底
-- ----------------------------
insert into aig_evaluation_case
(case_id, case_code, case_name, case_type, scenario_code, input_snapshot_ref, expected_json,
 rubric_json, cost_min, cost_max, data_level, classification, status, del_flag,
 create_dept, create_by, create_time, remark)
select 1767000000000000007, 'case-generation-build-fallback-dna', '生成任务构建·无基因走兜底风格', 'IMAGE_QA', null,
       'inline:{"subject":"鸢尾花香水","screen_hint":"HERO 主图"}',
       '{"required_paths":["prompt","negative_prompt","applied","has_negative_prompt"],"equals":{"dna_provided":false,"has_negative_prompt":true,"submission_not_performed":true,"reproducible_probe":true},"min_items":{"applied":1},"must_contain":["现代简约"]}',
       null, 0.0000, 0.0000, 'INTERNAL', 'DETERMINISTIC_ENGINE', '0', '0',
       1761000000000000103, 1761100000000000001, now(),
       '基因缺失时必须走内置兜底风格：提示词不能变成半句话（「静物，」这种）'
  where not exists (select 1 from (select case_code from aig_evaluation_case) t
                     where t.case_code = 'case-generation-build-fallback-dna');
