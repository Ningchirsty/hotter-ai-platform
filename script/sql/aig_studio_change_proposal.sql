-- ----------------------------------------------------------------------------
-- Agent Studio：变更提案表（增量 21／附件 07 增量 6「Copilot 提案」）
--
-- 背景：训练台的"培训助手（Copilot）"只应**提案**，绝不能直接改草稿。
--   提案必须能被人工审阅后**采纳**，采纳才产生一条新修订（不可变）。
--   没有这张表时，"Copilot 改了草稿"与"人改了草稿"在库里长得一模一样——
--   事后无法回答"这条改动是谁提的、谁批的"。
--
-- 三条刻意的口径：
--   1. 提案**不修改** aig_studio_draft / aig_studio_revision：它只是一份待审对象；
--   2. 采纳时按 (draft_id, base_revision) 做 CAS：提案基于旧修订就已失效，
--      不允许"拿一份针对旧内容的提案覆盖新内容"；
--   3. `content_hash_before/after` 都落库：采纳时重新计算并比对，内容漂移就拒绝。
--
-- 本仓约定：主键 BIGINT 由 MyBatis-Plus 雪花生成（无 AUTO_INCREMENT，裸 SQL 必须显式给 ID）；
--   复杂 JSON 用 longtext；审计列由 BaseEntity 填充；逻辑删除用 del_flag。
-- 幂等：create table if not exists（只建新表，可在存量库直接重放）。
-- ----------------------------------------------------------------------------

create table if not exists aig_studio_change_proposal (
    proposal_id         bigint(20)    not null                   comment '提案ID',
    draft_id            bigint(20)    not null                   comment '所属草稿（aig_studio_draft.draft_id）',
    base_revision       int(11)       not null                   comment '提案基于的修订号（CAS：采纳时必须仍是当前修订）',
    source              varchar(24)   not null default 'COPILOT' comment '提案来源（COPILOT培训助手 / MANUAL人工草拟）',
    operations_json     longtext      not null                   comment '规范化后的操作清单（白名单操作 + JSON Pointer；见 AigStudioProposalPlanner）',
    preview_json        longtext      default null               comment '改动预览（path/before/after；供人工审阅）',
    content_hash_before char(64)      not null                   comment '提案时的草稿内容哈希',
    content_hash_after  char(64)      not null                   comment '应用后预计的内容哈希（采纳时重算比对，漂移即拒绝）',
    status              varchar(16)   not null default 'PENDING' comment '状态（PENDING待人工处理 / APPLIED已采纳 / REJECTED已拒绝）',
    decided_by          bigint(20)    default null               comment '处理人（采纳/拒绝）',
    decided_at          datetime      default null               comment '处理时间',
    decided_reason      varchar(200)  default null               comment '处理说明（拒绝原因；采纳时可为空）',
    summary             varchar(200)  default null               comment '提案说明（页面上那行"想改什么"）',
    del_flag            char(1)       default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept         bigint(20)    default null               comment '创建部门',
    create_by           bigint(20)    default null               comment '创建者',
    create_time         datetime                                 comment '创建时间',
    update_by           bigint(20)    default null               comment '更新者',
    update_time         datetime                                 comment '更新时间',
    primary key (proposal_id),
    key idx_aig_studio_proposal_draft (draft_id, status, create_time)
) engine=innodb comment = '岗位 Agent Studio 变更提案（培训助手只提案，人工采纳才产生新修订）';

-- 核对：表在
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_studio_change_proposal';

select 'AIG_STUDIO_CHANGE_PROPOSAL_DDL_DONE' as marker;
