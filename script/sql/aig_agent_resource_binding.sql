-- ----------------------------------------------------------------------------
-- AI 治理层 · 工具/知识绑定表（附件 07 §5：只有声明字段、无绑定表 → 补绑定表）
--
-- 目标库：平台库（依赖 aig_agent_registry.sql：aig_agent_version 要先存在）
--
-- 【这张表是什么】版本声明的**收窄**：
--   aig_agent_version.allowed_tools / knowledge_scope_json 说"这个版本可以碰哪些工具/知识"；
--   本表说"这些声明里，在什么范围（公司/品牌/场景/角色）下真的给它用、并且启用"。
--   声明是一行逗号分隔文本（无法索引、无法按范围收窄、无法逐条停用），绑定一行一条，三者都能做。
--
-- 【这张表不是什么】Tool Gateway / Knowledge 引擎。附件 07 §10 明确
--   「不建 Runtime / Tool Gateway / Knowledge 引擎；P0 只读展示与如实标注"仅登记/不可测试"」。
--   本表只登记与收窄，**没有任何执行语义**，仓库里也没有调用代码会读它
--   （外发策略必须在网关执行点强制 —— ADR-006，不接受"一张表的声明"）。
--
-- 【硬约束：编码必须命中版本已声明的项】否则本表就成了**第二条声明通道**：
--   治理台上说"这个版本不能用 shell"、绑定表里却给它绑上，而没人能说清以哪个为准。
--   服务层校验：TOOL 必须命中 allowed_tools 且不得命中 forbidden_tools；KNOWLEDGE 必须命中
--   knowledge_scope_json。声明为空时明确提示"先去声明"，而不是只说"没声明"。
--
-- 【刻意不建唯一索引】范围列允许 NULL 表示"不限"，而 MySQL 唯一索引**不约束 NULL**
--   （两行范围全为 NULL 的"重复"绑定照样能插进去）。一个不生效的唯一索引只会给人虚假的安全感，
--   所以重复检查由服务层显式做（逐列 isNull 比对），与 aig_agent_binding 同一处理。
--
-- 幂等：create table if not exists（只建新表，可在存量库直接重放）。
-- ----------------------------------------------------------------------------

create table if not exists aig_agent_resource_binding (
    binding_id         bigint(20)      not null                   comment '绑定ID',
    agent_version_id   bigint(20)      not null                   comment '绑定的 Agent 版本ID',
    resource_type      varchar(16)     not null                   comment '资源类型（TOOL 工具 / KNOWLEDGE 知识范围）',
    resource_code      varchar(128)    not null                   comment '资源编码（必须命中该版本已声明的项）',
    company_id         bigint(20)      default null               comment '限定公司（空=不限）',
    brand_id           bigint(20)      default null               comment '限定品牌（空=不限）',
    scenario_code      varchar(32)     default null               comment '限定业务场景（空=不限）',
    role_scope         varchar(200)    default null               comment '限定角色（逗号分隔的 role_key，空=不限）',
    enabled            char(1)         not null default 'Y'       comment '是否启用（Y/N）',
    del_flag           char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept        bigint(20)      default null               comment '创建部门',
    create_by          bigint(20)      default null               comment '创建者',
    create_time        datetime                                   comment '创建时间',
    update_by          bigint(20)      default null               comment '更新者',
    update_time        datetime                                   comment '更新时间',
    remark             varchar(500)    default null               comment '备注',
    primary key (binding_id),
    key idx_aig_agent_res_bind_ver (agent_version_id, resource_type, enabled, del_flag),
    key idx_aig_agent_res_bind_code (resource_type, resource_code),
    key idx_aig_agent_res_bind_scope (company_id, brand_id, scenario_code)
) engine=innodb comment = 'AI Agent 工具/知识绑定（声明的收窄；仅登记，无执行语义）';

-- 核对：表在
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_agent_resource_binding';

select 'AIG_AGENT_RESOURCE_BINDING_DDL_DONE' as marker;
