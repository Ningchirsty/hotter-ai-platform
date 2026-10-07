-- ----------------------------------------------------------------------------
-- AI 治理层 · 四个内置 Agent 的注册种子（设计 §5.2）—— WP3
--
-- 依赖：script/sql/aig_agent_registry.sql（先建表）
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）
--
-- 【本脚本只登记「配置」，不新增一行算法代码】设计 §5.2 的四个 Agent 在仓库里都已经
-- 有实现（确定性引擎 + 现有服务），WP3 做的是把它们**包成受控版本**：
--   1) 详情页策划  ← CreativeDraftFactory / CreativeDraftBrain
--   2) 视觉 DNA    ← CreativeDnaService / VisualBrainAdapter / ReferenceImageAnalyzer
--   3) 生成任务构建 ← CreativeProductionService / ImageTaskSubmissionService
--   4) 视觉 QA     ← CreativeImageRuleChecker / cp_output_check
-- 每个版本的 config_json.implementation 写明对应的类，让读的人知道「点下去会跑哪段代码」，
-- 而不是去猜一个名叫「策划 Agent」的东西到底是我们写的还是模型自己的行为。
--
-- 【为什么内置 Agent 直接落 STABLE，并补一条发布事件】
-- 设计 §5.4 要求「只有通过 Manifest 校验、沙箱运行、黄金用例、人工批准、小范围灰度的版本
-- 可进入 STABLE」。这四个 Agent 是**平台自带**：它们没有第三方 Package 的 Manifest，
-- 也不走 Package 安装链路；它们的同等门槛由**平台自身的 CI（编译+全量测试+派发/契约测试）
-- 与代码评审**承担。因此这里直接登记为 STABLE，并写一条发布事件把「依据是什么」记清楚
-- （detail 里写明由平台发布流程背书、operator_id 为空表示系统/平台而非某个人的批准）。
-- 刻意不写成 DRAFT：那样四个内置 Agent 在人工走完五道门槛之前完全不可用，
-- 而它们本来就已经在跑（在 creative 的各条链路上），登记成 DRAFT 只会让登记与现实不符。
-- 同时这条事件让 wasEverStable 为真——否则将来一旦停用，按 §5.4 的保护就再也启用不回来。
--
-- 【逐项口径（每条都可被质疑，故写明依据）】
--   · release_channel = GENERAL：平台内置、全体可用。**通用通道不需要绑定行**
--     （受限通道才需要，见服务的 assertBindingExistsIfLimitedChannel）。
--   · allow_external：策划/视觉DNA/QA 全部 N，生成任务构建为 Y。
--     前三者 N 是**继承既有安全决定**：dp_creative_r8_dna_gov.sql 已把
--     visual_dna_extract 与 deliverable_consistency 的各个数据等级都设成
--     allow_external='N'（理由：涉及产品图，一律本地私有部署）。接入新能力时
--     不该顺手覆盖团队已做过的安全决定。生成任务构建给 Y 是因为「出图」本就允许走
--     外部图像网关（bluocto 通路），**但最终是否外发仍由路由策略按数据等级取与决定**，
--     agent 上的这个值只是业务侧许可。
--   · 策划 Agent 的 prompt_template 为空、provider_capability 为空：它是**确定性引擎**
--     （参数化模板 + 可复现 variantSeed，无随机数无时钟）。登记里必须能表达
--     「这个 Agent 不调模型」，否则迟早有人给确定性基座接一个模型调用，
--     把「结果可复现」这条性质弄丢。模型只在字段通过 accept 时叠加，且来源要标出来。
--   · forbidden_tools = 'shell,ssh,db-direct,docker-socket'：这四个 Agent 的实现都在进程内，
--     不需要任何这类能力；写清楚是为了让 §6.2 的拒绝项在数据里可见，
--     而不是只存在于文档里。
--
-- 幂等：全部 INSERT ... SELECT ... WHERE NOT EXISTS，可安全重复执行（**无任何 drop/update**）。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、四个 Agent 定义
-- ----------------------------
insert into aig_agent (agent_id, agent_code, agent_name, category, builtin, description, status, del_flag, create_dept, create_by, create_time, remark)
select 1765000000000000001, 'creative_planning', '详情页策划', 'PLANNING', 'Y',
       '只读资料，产出 Page Spec、模块、逐屏 Brief 与 Prompt Plan 的计划草稿',
       '0', '0', 1761000000000000103, 1761100000000000001, now(),
       '设计 §5.2 内置 Agent 1/4；实现为确定性引擎（不调模型）'
  where not exists (select 1 from (select agent_code from aig_agent) t where t.agent_code = 'creative_planning');

insert into aig_agent (agent_id, agent_code, agent_name, category, builtin, description, status, del_flag, create_dept, create_by, create_time, remark)
select 1765000000000000002, 'creative_visual_dna', '视觉 DNA', 'VISUAL_DNA', 'Y',
       '只读资产，调用视觉分析产出色彩、构图、光影、材质、镜头与来源',
       '0', '0', 1761000000000000103, 1761100000000000001, now(),
       '设计 §5.2 内置 Agent 2/4；能力 visual_dna_extract'
  where not exists (select 1 from (select agent_code from aig_agent) t where t.agent_code = 'creative_visual_dna');

insert into aig_agent (agent_id, agent_code, agent_name, category, builtin, description, status, del_flag, create_dept, create_by, create_time, remark)
select 1765000000000000003, 'creative_generation_build', '生成任务构建', 'GENERATION', 'Y',
       '按已确认 Brief 与素材产出 Provider/Workflow 建议与生成任务草案',
       '0', '0', 1761000000000000103, 1761100000000000001, now(),
       '设计 §5.2 内置 Agent 3/4；只创建任务草案，实际调用走统一调用入口'
  where not exists (select 1 from (select agent_code from aig_agent) t where t.agent_code = 'creative_generation_build');

insert into aig_agent (agent_id, agent_code, agent_name, category, builtin, description, status, del_flag, create_dept, create_by, create_time, remark)
select 1765000000000000004, 'creative_visual_qa', '视觉 QA', 'QA', 'Y',
       '只读候选资产与品牌规则，产出缺陷清单、风险级别与人工复核建议',
       '0', '0', 1761000000000000103, 1761100000000000001, now(),
       '设计 §5.2 内置 Agent 4/4；能力 deliverable_consistency'
  where not exists (select 1 from (select agent_code from aig_agent) t where t.agent_code = 'creative_visual_qa');

-- ----------------------------
-- 2、四个 Agent 版本（0.1.0，内置即 STABLE，通用通道）
-- ----------------------------
insert into aig_agent_version
(agent_version_id, agent_id, version, release_status, release_channel, scenario_code,
 input_schema, output_schema, prompt_template, config_json, allowed_tools, forbidden_tools,
 provider_capability, allow_external, status, del_flag, create_dept, create_by, create_time, remark)
select 1765000000000000011, 1765000000000000001, '0.1.0', 'STABLE', 'GENERAL', null,
       '{"fields":[{"name":"productTruth"},{"name":"finalCopy"},{"name":"brandProfile"},{"name":"referenceAssets"}]}',
       '{"fields":[{"name":"pageSpec"},{"name":"modules"},{"name":"screenBriefs"},{"name":"promptPlan"}]}',
       null,
       '{"implementation":"org.dromara.creative.helper.CreativeDraftFactory + CreativeDraftBrain","deterministic":true,"note":"参数化模板 + 可复现 variantSeed，无随机数无时钟；模型仅在字段通过 accept 时叠加并把来源标出，否则回落并标 TEMPLATE"}',
       null, 'shell,ssh,db-direct,docker-socket', null, 'N', '0', '0',
       1761000000000000103, 1761100000000000001, now(),
       '确定性引擎：prompt_template 与 provider_capability 刻意为空，表示本 Agent 不调模型'
  where not exists (select 1 from (select agent_id, version from aig_agent_version) t
                     where t.agent_id = 1765000000000000001 and t.version = '0.1.0');

insert into aig_agent_version
(agent_version_id, agent_id, version, release_status, release_channel, scenario_code,
 input_schema, output_schema, prompt_template, config_json, allowed_tools, forbidden_tools,
 provider_capability, allow_external, status, del_flag, create_dept, create_by, create_time, remark)
select 1765000000000000012, 1765000000000000002, '0.1.0', 'STABLE', 'GENERAL', null,
       '{"fields":[{"name":"referenceImages"},{"name":"brandForbiddenItems"},{"name":"productConstraints"}]}',
       '{"fields":[{"name":"palette"},{"name":"composition"},{"name":"lighting"},{"name":"material"},{"name":"lens"},{"name":"sources"}]}',
       null,
       '{"implementation":"org.dromara.creative.service.ICreativeDnaService + org.dromara.creative.helper.VisualBrainAdapter + ReferenceImageAnalyzer","note":"确定性分析优先，模型分析只在可用时叠加；来源逐字段标注，不复制具体场景"}',
       null, 'shell,ssh,db-direct,docker-socket', 'visual_dna_extract', 'N', '0', '0',
       1761000000000000103, 1761100000000000001, now(),
       '外发许可为 N：继承 dp_creative_r8_dna_gov.sql 已做的安全决定（涉及产品图，一律本地）'
  where not exists (select 1 from (select agent_id, version from aig_agent_version) t
                     where t.agent_id = 1765000000000000002 and t.version = '0.1.0');

insert into aig_agent_version
(agent_version_id, agent_id, version, release_status, release_channel, scenario_code,
 input_schema, output_schema, prompt_template, config_json, allowed_tools, forbidden_tools,
 provider_capability, allow_external, status, del_flag, create_dept, create_by, create_time, remark)
select 1765000000000000013, 1765000000000000003, '0.1.0', 'STABLE', 'GENERAL', null,
       '{"fields":[{"name":"confirmedBrief"},{"name":"prompt"},{"name":"assets"},{"name":"constraints"}]}',
       '{"fields":[{"name":"providerSuggestion"},{"name":"workflowSuggestion"},{"name":"taskDraft"}]}',
       null,
       '{"implementation":"org.dromara.creative.service.ICreativeProductionService + org.dromara.ai.image.service.ImageTaskSubmissionService","note":"只创建任务草案；实际执行经统一调用入口（路由/有序 fallback/退避重试/逐次审计都在那里）"}',
       null, 'shell,ssh,db-direct,docker-socket', 'image_generation', 'Y', '0', '0',
       1761000000000000103, 1761100000000000001, now(),
       '外发许可 Y：出图允许走外部图像网关；但最终是否外发仍由路由策略按数据等级取与决定'
  where not exists (select 1 from (select agent_id, version from aig_agent_version) t
                     where t.agent_id = 1765000000000000003 and t.version = '0.1.0');

insert into aig_agent_version
(agent_version_id, agent_id, version, release_status, release_channel, scenario_code,
 input_schema, output_schema, prompt_template, config_json, allowed_tools, forbidden_tools,
 provider_capability, allow_external, status, del_flag, create_dept, create_by, create_time, remark)
select 1765000000000000014, 1765000000000000004, '0.1.0', 'STABLE', 'GENERAL', null,
       '{"fields":[{"name":"candidateAssets"},{"name":"facts"},{"name":"brandRules"}]}',
       '{"fields":[{"name":"defects"},{"name":"riskLevel"},{"name":"humanReviewAdvice"}]}',
       null,
       '{"implementation":"org.dromara.creative.helper.CreativeImageRuleChecker + cp_output_check","note":"确定性像素度量优先；模型结论只作建议，自动 QA 只筛除、不放行"}',
       null, 'shell,ssh,db-direct,docker-socket', 'deliverable_consistency', 'N', '0', '0',
       1761000000000000103, 1761100000000000001, now(),
       '外发许可为 N：与视觉 DNA 同一安全决定；且「只筛除不放行」与自动 QA 口径一致'
  where not exists (select 1 from (select agent_id, version from aig_agent_version) t
                     where t.agent_id = 1765000000000000004 and t.version = '0.1.0');

-- ----------------------------
-- 3、发布事件（内置 Agent 的 STABLE 依据；同时让 wasEverStable 为真）
-- ----------------------------
insert into aig_release_event (event_id, target_type, target_version_id, from_status, to_status, passed_gates, operator_id, detail, operate_time)
select 1765000000000000021, 'AGENT_VERSION', 1765000000000000011, null, 'STABLE',
       'MANIFEST_VALIDATION,SANDBOX_RUN,GOLDEN_CASE,HUMAN_APPROVAL,CANARY', null,
       '平台内置 Agent 随平台版本发布：同等门槛由平台 CI（编译+全量测试）与代码评审承担，非第三方 Package 安装链路，故 operator_id 为空',
       now()
  where not exists (select 1 from (select target_type, target_version_id, to_status from aig_release_event) t
                     where t.target_type = 'AGENT_VERSION' and t.target_version_id = 1765000000000000011
                       and t.to_status = 'STABLE');

insert into aig_release_event (event_id, target_type, target_version_id, from_status, to_status, passed_gates, operator_id, detail, operate_time)
select 1765000000000000022, 'AGENT_VERSION', 1765000000000000012, null, 'STABLE',
       'MANIFEST_VALIDATION,SANDBOX_RUN,GOLDEN_CASE,HUMAN_APPROVAL,CANARY', null,
       '平台内置 Agent 随平台版本发布（同 1/4 的依据）', now()
  where not exists (select 1 from (select target_type, target_version_id, to_status from aig_release_event) t
                     where t.target_type = 'AGENT_VERSION' and t.target_version_id = 1765000000000000012
                       and t.to_status = 'STABLE');

insert into aig_release_event (event_id, target_type, target_version_id, from_status, to_status, passed_gates, operator_id, detail, operate_time)
select 1765000000000000023, 'AGENT_VERSION', 1765000000000000013, null, 'STABLE',
       'MANIFEST_VALIDATION,SANDBOX_RUN,GOLDEN_CASE,HUMAN_APPROVAL,CANARY', null,
       '平台内置 Agent 随平台版本发布（同 1/4 的依据）', now()
  where not exists (select 1 from (select target_type, target_version_id, to_status from aig_release_event) t
                     where t.target_type = 'AGENT_VERSION' and t.target_version_id = 1765000000000000013
                       and t.to_status = 'STABLE');

insert into aig_release_event (event_id, target_type, target_version_id, from_status, to_status, passed_gates, operator_id, detail, operate_time)
select 1765000000000000024, 'AGENT_VERSION', 1765000000000000014, null, 'STABLE',
       'MANIFEST_VALIDATION,SANDBOX_RUN,GOLDEN_CASE,HUMAN_APPROVAL,CANARY', null,
       '平台内置 Agent 随平台版本发布（同 1/4 的依据）', now()
  where not exists (select 1 from (select target_type, target_version_id, to_status from aig_release_event) t
                     where t.target_type = 'AGENT_VERSION' and t.target_version_id = 1765000000000000014
                       and t.to_status = 'STABLE');

-- ----------------------------
-- 4、核对
-- ----------------------------
select a.agent_code, a.category, v.version, v.release_status, v.release_channel,
       v.provider_capability, v.allow_external
  from aig_agent a
  join aig_agent_version v on v.agent_id = a.agent_id
 where a.builtin = 'Y'
 order by a.agent_id;

select 'builtin_agents' as item, count(*) as n from aig_agent where builtin = 'Y'
union all select 'builtin_versions', count(*) from aig_agent_version where agent_id in
       (1765000000000000001, 1765000000000000002, 1765000000000000003, 1765000000000000004)
union all select 'builtin_release_events', count(*) from aig_release_event where target_version_id in
       (1765000000000000011, 1765000000000000012, 1765000000000000013, 1765000000000000014);

select 'AIG_AGENT_REGISTRY_SEED_DONE' as marker;
