-- ============================================================================
-- 内容生产协同 · 成品一致性检查（生成结果 vs 原参考图）增量脚本
-- ----------------------------------------------------------------------------
-- 依据：docs/content/00-SPEC-阶段1A-内容生产协同.md §11（本轮增补章节）
--
-- 要解决什么：
--   阶段1A 只覆盖到「开工前」——上传资料、抽事实、人工确认、出开工包。
--   出稿之后的验收环节是空白：成品图和当初的参考图到底一不一致，只能靠人肉看。
--   本脚本登记一个新能力 deliverable_consistency 与配套的表/菜单/权限。
--
-- 三条边界（务必知悉）：
--   1. 只做验收，不产生产品事实。参考图与 AI 结论不得反向成为产品结构/颜色/
--      数量/包装/参数的依据（SPEC §0.1 红线第 2 条）。
--   2. 默认不外发。RESTRICTED 一律 allow_external='N'；PUBLIC/INTERNAL 允许外部，
--      但业务侧还有第二道闸：任务 cp_task.allow_external != 'Y' 时，若路由指向外部
--      部署模型，检查直接失败而不是"顺手"把未发布素材发出去。
--   3. 本地始终有一条兜底。本地内容引擎以 FALLBACK 绑定本能力：治理台没有绑定
--      视觉模型时，仍能做画布几何与归一化网格结构的确定性比对，结论可解释。
--      一旦绑定 PRIMARY 的视觉模型，路由会优先用它（PRIMARY 排在 FALLBACK 之前）。
--
-- 幂等：建表用 create table if not exists；其余全部 insert ignore /
--       INSERT ... SELECT + NOT EXISTS，可安全重复执行。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、建表（与 cp_content.sql 中的定义一致；已存在的环境执行本段即可补表）
-- ---------------------------------------------------------------------------
create table if not exists cp_output_check (
    check_id        bigint(20)      not null                   comment '检查ID',
    check_no        varchar(32)     not null                   comment '检查单号（对外展示，CK+日期+序号）',
    task_id         bigint(20)      not null                   comment '任务ID',
    reference_file_id bigint(20)    not null                   comment '原参考图附件ID（cp_task_file.file_id）',
    result_file_id  bigint(20)      not null                   comment '生成结果附件ID（cp_task_file.file_id）',
    status          varchar(16)     not null default 'PENDING' comment '检查状态（PENDING/RUNNING/DONE/FAILED）',
    verdict         varchar(16)     default null               comment '检查结论（CONSISTENT/INCONSISTENT/UNCERTAIN），未出结论为 null',
    score           decimal(5,2)    default null               comment '一致性得分 0-100；算不出时留空，不编造',
    summary         varchar(1000)   default null               comment '结论摘要（给用户看的一句话）',
    findings_json   text                                       comment '差异清单 JSON',
    metrics_json    text                                       comment '本地确定性度量 JSON（尺寸/比例/网格差异）',
    model_id        bigint(20)      default null               comment '实际执行的模型ID（sai_model_config.id）',
    model_key       varchar(128)    default null               comment '实际执行的模型标识',
    deployment_type varchar(32)     default null               comment '实际执行的部署类型',
    invoker_name    varchar(64)     default null               comment '实际执行的调用器名称',
    trace_id        varchar(64)     default null               comment '治理层调用链ID（aig_invocation_audit.trace_id）',
    failure_reason  varchar(500)    default null               comment '未取得结论的可读原因（status=FAILED 时必填）',
    checked_by      bigint(20)      default null               comment '检查发起人',
    checked_at      datetime        default null               comment '检查完成时间',
    remark          varchar(500)    default null               comment '备注',
    del_flag        char(1)         default '0'                comment '删除标志（0存在 1删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    primary key (check_id),
    unique key uk_cp_output_check_no (check_no),
    key idx_cp_output_check_task (task_id, del_flag),
    key idx_cp_output_check_status (status, verdict)
) engine=innodb comment = '内容生产-成品一致性检查';

-- ---------------------------------------------------------------------------
-- 二、字典（检查状态 / 检查结论）
-- ---------------------------------------------------------------------------
insert ignore into sys_dict_type values(1765200000000000008, '成品检查状态', 'cp_check_status',  1761000000000000103, 1761100000000000001, sysdate(), null, null, '待检查/检查中/已完成/失败');
insert ignore into sys_dict_type values(1765200000000000009, '成品检查结论', 'cp_check_verdict', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '一致/不一致/无法判定');

insert ignore into sys_dict_data values(1765300000000000801, 1, '待检查', 'PENDING', 'cp_check_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000802, 2, '检查中', 'RUNNING', 'cp_check_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000803, 3, '已完成', 'DONE',    'cp_check_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '拿到结论即算完成，结论可能是"不一致"');
insert ignore into sys_dict_data values(1765300000000000804, 4, '失败',   'FAILED',  'cp_check_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '没拿到结论，原因见 failure_reason');

insert ignore into sys_dict_data values(1765300000000000901, 1, '一致',     'CONSISTENT',   'cp_check_verdict', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未发现差异；否定性结论仍需人工确认关键信息');
insert ignore into sys_dict_data values(1765300000000000902, 2, '不一致',   'INCONSISTENT', 'cp_check_verdict', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '发现差异，见差异清单');
insert ignore into sys_dict_data values(1765300000000000903, 3, '无法判定', 'UNCERTAIN',    'cp_check_verdict', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '证据不足，需人工复核；不强行给结论');

-- ---------------------------------------------------------------------------
-- 三、菜单与权限（挂到「内容生产协同」1765000000000000001 下）
--     图标 image 取自 frontend/src/assets/icons/svg/image.svg（真实存在，已核对）
-- ---------------------------------------------------------------------------
insert ignore into sys_menu values(1765000000000000106, '成品一致性检查', 1765000000000000001, 6, 'check', 'content/check/index', '', 'N', 'Y', 'C', '0', '0', 'content:check:list', 'image', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '出稿验收：把生成结果与原参考图并排比对，输出一致/不一致/无法判定与差异清单');
insert ignore into sys_menu values(1765000000000001601, '检查查询', 1765000000000000106, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:check:query',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '含图片预览');
insert ignore into sys_menu values(1765000000000001602, '发起检查', 1765000000000000106, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:check:run',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '上传参考图与成品图并触发比对');
insert ignore into sys_menu values(1765000000000001603, '检查删除', 1765000000000000106, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:check:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- 角色授权：管理员全部；生产人员可发起与查看（检查是日常动作），删留给管理员
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000000106);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001601);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001602);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001603);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000000106);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000001601);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000001602);

-- ---------------------------------------------------------------------------
-- 四、治理层能力注册
--     required_tags 含 VISION：本能力需要能"看图"的模型；
--     output_schema 只强制 verdict/summary/findings 三个字段——score 是可选的。
--     刻意不把 score 写进必填：算不出一致性分值时必须能如实留空，
--     若模板强制该字段，模型与本地实现都只能编一个数字出来。
-- ---------------------------------------------------------------------------
insert ignore into aig_capability
  (capability_id, capability_code, capability_name, biz_goal, required_tags,
   input_schema, output_schema, data_policy, human_confirm_points, quality_threshold,
   audit_level, status, del_flag, create_dept, create_by, create_time, update_by, update_time, remark)
values
  (1763000000000001004, 'deliverable_consistency', '成品一致性检查',
   '把出稿的成品图与当初的原参考图逐项对照，输出一致/不一致/无法判定与差异清单，供人工验收；不产生产品事实',
   'VISION,TEXT',
   '{"fields":[{"name":"images","type":"array","dataLevel":"RESTRICTED","note":"images[0]必须是参考图、images[1]必须是成品图"},{"name":"localMetrics","type":"object","dataLevel":"INTERNAL"},{"name":"taskNo","type":"string","dataLevel":"INTERNAL"}],"forbidden":["apiKey","credential","phone","idCard"]}',
   '{"fields":[{"name":"verdict","type":"string"},{"name":"summary","type":"string"},{"name":"findings","type":"array"}]}',
   'LOCAL_FIRST',
   '一致性结论仅作为验收提示，不自动确认产品真实性；不一致时必须由人决定返工或阻断',
   '输出结构不符按失败处理，不写入结论；无法判断时必须返回 UNCERTAIN 而非猜测',
   'SUMMARY', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null,
   '内容生产：成品图 vs 原参考图的结构与语义一致性检查');

-- 路由策略：PUBLIC/INTERNAL 允许外部（是否真的外发由任务 allow_external 二次把关）；
--           RESTRICTED 一律不外发。三个等级都允许转人工。
insert ignore into aig_route_policy values(1763000000000002017, 'deliverable_consistency', 'PUBLIC',     'LOCAL', 'Y', 'N', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已发布素材，允许外部视觉模型；仍受任务级 allow_external 约束');
insert ignore into aig_route_policy values(1763000000000002018, 'deliverable_consistency', 'INTERNAL',   'LOCAL', 'Y', 'N', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未发布素材，允许外部视觉模型但需任务显式开启外部 AI');
insert ignore into aig_route_policy values(1763000000000002019, 'deliverable_consistency', 'RESTRICTED', 'LOCAL', 'N', 'N', 'Y', '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未发布结构/设计源文件，仅本地比对，禁止外发');

-- 本地内容引擎以 FALLBACK 绑定：治理台绑定 PRIMARY 视觉模型时优先用模型；
-- 没绑定时仍可完成确定性比对，能力不因"没配模型"而不可用。
insert into aig_capability_model (bind_id, capability_code, model_id, usage_type,
                                  priority, status, del_flag, remark)
select 1763000000000004013, 'deliverable_consistency', c.id, 'FALLBACK', 10, '0', '0',
       '本地兜底：画布几何 + 16×16 归一化网格结构比对'
from sai_model_config c
where c.model_key = 'local-content'
  and not exists (select 1 from aig_capability_model b
                  where b.capability_code = 'deliverable_consistency' and b.model_id = c.id);

-- ---------------------------------------------------------------------------
-- 五、自检
-- ---------------------------------------------------------------------------
select '成品检查表' as item, count(1) as cnt
  from information_schema.TABLES
 where TABLE_SCHEMA = database() and TABLE_NAME = 'cp_output_check';

select '菜单与权限' as item, count(1) as cnt
  from sys_menu
 where menu_id in (1765000000000000106,
                   1765000000000001601, 1765000000000001602, 1765000000000001603);

select c.capability_code, c.capability_name, c.data_policy, c.required_tags, c.status
  from aig_capability c where c.capability_code = 'deliverable_consistency';

select p.capability_code, p.data_level, p.preferred_deployment, p.allow_external, p.fallback_to_manual
  from aig_route_policy p where p.capability_code = 'deliverable_consistency'
 order by p.data_level;

select b.capability_code, b.usage_type, b.priority, m.model_key, g.deployment_type, g.lifecycle_status
  from aig_capability_model b
  join sai_model_config m on m.id = b.model_id
  left join aig_model_governance g on g.model_id = m.id
 where b.capability_code = 'deliverable_consistency';
