-- ----------------------------------------------------------------------------
-- 岗位 Agent Studio：对话式 Copilot 对话账本（附件 07 增量 6「对话式 Copilot」）
--
-- 【这张表是什么】培训助手在某一版草稿上的**逐轮对话**：谁说了什么、模型回了什么、
--   这一轮产出了哪条提案（或被什么规则拒绝）。
--
-- 【为什么是追加型账本、不建"会话表 + 消息表"】
--   这里的对话不是通用聊天：上下文就是"这份草稿当前的这一版内容"。没有跨草稿的会话、
--   没有多轮续写状态——一行＝一轮，每行自带当时的 revision 与 content_hash。
--   引入会话/消息两层只会多一个"这条消息属于哪个会话"的间接层，
--   而回答"这条提案是模型在第几次对话里提的"反而更绕。
--
-- 【为什么必须记 status 与 failure_reason】
--   模型可能调不通（FAILED），也可能**输出了白名单之外的操作**（REJECTED，例如想改
--   agent_category/provider_capability）。后者是这条通道最该被看见的信号：说明提示词与
--   白名单没有约束住模型。只记"成功的那几轮"，这件事就永远沉在日志里。
--
-- 【为什么不建 del_flag】追加型审计账本没有"删除"这个语义（同 aig_package_install_log）。
--
-- 本仓约定：主键 BIGINT 由 MyBatis-Plus 雪花生成（无 AUTO_INCREMENT）；长文本用 longtext；
--   审计列由 BaseEntity 填充。
-- 幂等：create table if not exists（只建新表，可在存量库直接重放）。
-- ----------------------------------------------------------------------------

create table if not exists aig_studio_copilot_turn (
    turn_id         bigint(20)    not null                   comment '对话轮次ID',
    draft_id        bigint(20)    not null                   comment '所属训练草稿',
    revision        int(11)       default null               comment '对话基于的修订号（提案 CAS 的基准）',
    content_hash    char(64)      default null               comment '对话基于的草稿内容哈希',
    user_message    varchar(2000) not null                   comment '用户消息',
    data_level      varchar(16)   not null                   comment '本次数据等级（PUBLIC/INTERNAL/RESTRICTED/STRICT）',
    model_output    longtext      default null               comment '模型原文（按配置截断；完整原文按 traceId 从网关审计查）',
    status          varchar(16)   not null                   comment '结论（RUNNING/SUCCEEDED/REJECTED/FAILED）',
    failure_reason  varchar(500)  default null               comment '拒绝/失败原因（越权路径、值类型不符、未调通等）',
    proposal_id     bigint(20)    default null               comment '产出的提案ID（仅 SUCCEEDED）',
    trace_id        varchar(64)   default null               comment '网关调用链ID',
    model_key       varchar(128)  default null               comment '命中的模型键',
    deployment_type varchar(32)   default null               comment '部署类型（LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API）',
    external_call   char(1)       default null               comment '是否发生外发（Y/N；空=网关没给结论）',
    latency_ms      bigint(20)    default null               comment '耗时（毫秒）',
    create_dept     bigint(20)    default null               comment '创建部门',
    create_by       bigint(20)    default null               comment '创建者',
    create_time     datetime                                 comment '创建时间',
    update_by       bigint(20)    default null               comment '更新者',
    update_time     datetime                                 comment '更新时间',
    primary key (turn_id),
    key idx_aig_studio_copilot_draft (draft_id, turn_id),
    key idx_aig_studio_copilot_trace (trace_id)
) engine=innodb comment = 'Agent Studio 对话式 Copilot 对话账本（只产提案，不改草稿）';

-- 核对：表在
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_studio_copilot_turn';

select 'AIG_STUDIO_COPILOT_TURN_DDL_DONE' as marker;
