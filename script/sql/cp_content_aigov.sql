-- ============================================================================
-- 内容生产协同 · 阶段1A 所需的 AI 能力注册（沿用现有 ruoyi-ai-gov，不重建治理层）
-- ----------------------------------------------------------------------------
-- 依据：docs/content/00-SPEC-阶段1A-内容生产协同.md §4.4
--
-- 设计文档 §16.1 要求「内容生产模块只调用业务能力编码，不直接调用具体模型地址」。
-- 因此内容模块不直接调 POI/PDFBox，而是经 aigov 的 invoke 调用
--   document_parse  / brief_precheck
-- 由治理层完成路由判定（本地优先、默认拒绝）与逐次审计。
--
-- 关于「模型」的口径（重要，避免误读）：
--   本文件会登记一条 **本地内容引擎** 模型（LOCAL，adapter_key=local-content）。
--   它不是外部模型，也不是测试夹具，而是对「进程内规则引擎」这一真实执行者的
--   如实登记——路由选中它之后，实际执行的就是 LocalContentInvoker（纯本地规则，
--   不出网）。登记成模型是为了让治理层的「数据等级上限 / 生命周期 / 审计 / 路由」
--   对解析与预检同样生效，与设计文档 §9.2 的路由模型一致。
--
-- 幂等：能力/策略用 insert ignore；模型与绑定用 INSERT ... SELECT 子查询 + NOT EXISTS，
--       可安全重复执行。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、能力定义
-- ---------------------------------------------------------------------------
-- 1) 资料解析：从 Excel/Word/PDF 抽取候选字段
insert ignore into aig_capability values(
  1763000000000001002, 'document_parse', '资料解析',
  '从任务附件（Excel/Word/PDF）中抽取产品事实候选字段，供人工确认；不判断对错、不写入既定事实',
  'TEXT',
  '{"fields":[{"name":"fileRef","type":"string","dataLevel":"INTERNAL"},{"name":"fileKind","type":"string","dataLevel":"INTERNAL"},{"name":"fileName","type":"string","dataLevel":"INTERNAL"}],"forbidden":["apiKey","credential"]}',
  '{"fields":[{"name":"candidates","type":"array"},{"name":"fieldCount","type":"number"},{"name":"pendingConfirm","type":"array"}]}',
  'LOCAL_ONLY',
  '抽取结果一律以「待确认」呈现，不得自动成为产品事实；低可信内容必须人工确认',
  '输出结构不符时有限重试，不写入快照；无法本地抽取的类型标记为跳过并给出可读原因',
  'SUMMARY', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null,
  '内容生产阶段1A：本地 POI/PDFBox + 规则，不出网');

-- 2) 资料预检：冲突与缺失检测
insert ignore into aig_capability values(
  1763000000000001003, 'brief_precheck', '资料预检',
  '比对同一字段的多来源取值以发现冲突，并按闸门规则发现缺失项，产出互动确认卡草稿',
  'TEXT,RERANK',
  '{"fields":[{"name":"candidates","type":"array","dataLevel":"INTERNAL"},{"name":"gateRules","type":"array","dataLevel":"INTERNAL"}],"forbidden":["phone","idCard","resume_raw"]}',
  '{"fields":[{"name":"conflicts","type":"array"},{"name":"missings","type":"array"},{"name":"pendingConfirm","type":"array"}]}',
  'LOCAL_ONLY',
  '冲突只呈现证据，不得由 AI 判定哪个取值正确；必须人工选择或补料',
  '不做臆测补全；无证据即报缺失，不猜值',
  'SUMMARY', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null,
  '内容生产阶段1A：规则比对，不出网');

-- ---------------------------------------------------------------------------
-- 二、路由策略：两个能力 × 三个数据等级，一律仅本地、不外部、可转人工
-- ---------------------------------------------------------------------------
insert ignore into aig_route_policy values(1763000000000002011, 'document_parse', 'PUBLIC',     'LOCAL', 'N', 'N', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '产品资料即便公开也不外发');
insert ignore into aig_route_policy values(1763000000000002012, 'document_parse', 'INTERNAL',   'LOCAL', 'N', 'N', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未公开产品资料，仅本地');
insert ignore into aig_route_policy values(1763000000000002013, 'document_parse', 'RESTRICTED', 'LOCAL', 'N', 'Y', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未发布结构/设计源文件，仅本地且需人工确认');
insert ignore into aig_route_policy values(1763000000000002014, 'brief_precheck', 'PUBLIC',     'LOCAL', 'N', 'N', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into aig_route_policy values(1763000000000002015, 'brief_precheck', 'INTERNAL',   'LOCAL', 'N', 'N', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into aig_route_policy values(1763000000000002016, 'brief_precheck', 'RESTRICTED', 'LOCAL', 'N', 'Y', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---------------------------------------------------------------------------
-- 三、本地内容引擎模型登记（如实描述进程内规则引擎，非外部模型）
--     sai_model_config.id 为自增，故用子查询定位，不写死 ID
-- ---------------------------------------------------------------------------
insert into sai_model_config (provider_id, model_name, model_key, model_type, adapter_key,
                              description, scope, is_default, is_enabled)
select 3, '本地内容引擎（规则）', 'local-content', 'CHAT', 'local-content',
       '内容生产阶段1A 的本地执行者：POI/PDFBox 抽取 + 规则比对，进程内完成，不出网',
       'LOCAL', 0, 1
from dual
where not exists (select 1 from sai_model_config where model_key = 'local-content');

insert into aig_model_governance (governance_id, model_id, deployment_type, data_level_max,
                                  lifecycle_status, status, del_flag,
                                  owner_tech, owner_biz, owner_security, remark)
select 1763000000000003002, c.id, 'LOCAL', 'RESTRICTED', 'PRODUCTION', '0', '0',
       'tech-owner', 'biz-owner', 'security-owner',
       '本地规则引擎：数据不出网，可处理最高 RESTRICTED'
from sai_model_config c
where c.model_key = 'local-content'
  and not exists (select 1 from aig_model_governance g where g.model_id = c.id);

-- 绑定两个能力到该模型（PRIMARY）
insert into aig_capability_model (bind_id, capability_code, model_id, usage_type,
                                  priority, status, del_flag, remark)
select 1763000000000004011, 'document_parse', c.id, 'PRIMARY', 10, '0', '0', '本地内容引擎'
from sai_model_config c
where c.model_key = 'local-content'
  and not exists (select 1 from aig_capability_model b
                  where b.capability_code = 'document_parse' and b.model_id = c.id);

insert into aig_capability_model (bind_id, capability_code, model_id, usage_type,
                                  priority, status, del_flag, remark)
select 1763000000000004012, 'brief_precheck', c.id, 'PRIMARY', 10, '0', '0', '本地内容引擎'
from sai_model_config c
where c.model_key = 'local-content'
  and not exists (select 1 from aig_capability_model b
                  where b.capability_code = 'brief_precheck' and b.model_id = c.id);

-- ---------------------------------------------------------------------------
-- 四、自检
-- ---------------------------------------------------------------------------
select c.capability_code, c.capability_name, c.data_policy, c.status
  from aig_capability c
 where c.capability_code in ('document_parse','brief_precheck','talent_match')
 order by c.capability_code;

select p.capability_code, p.data_level, p.preferred_deployment, p.allow_external, p.fallback_to_manual
  from aig_route_policy p
 where p.capability_code in ('document_parse','brief_precheck')
 order by p.capability_code, p.data_level;

select b.capability_code, c.model_key, c.adapter_key, g.deployment_type, g.data_level_max, g.lifecycle_status
  from aig_capability_model b
  join sai_model_config c on c.id = b.model_id
  left join aig_model_governance g on g.model_id = c.id
 where b.capability_code in ('document_parse','brief_precheck')
 order by b.capability_code;
