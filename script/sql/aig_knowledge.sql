-- ------------------------------------------------------------------
-- AI 治理：知识中心数据模型（设计 §6.4、§8.2）
--
-- 背景：当前 Agent 治理链路没有完成"文档上传 → 分段 → 清洗 → 问题生成"的知识处理闭环，
--   知识只是 aig_agent_version.knowledge_scope_json 里的一串声明编码。本脚本建立
--   集合 / 文档 / 文档版本 / 处理任务 / 分段 五张表，作为知识处理与检索的落点。
--
-- 三条与既有约定一致的纪律：
--   · 正文不落库：分段只存 text_ref（有权限的存储引用）与受限长度的 text_excerpt（引用预览）；
--   · 版本不可变：一个文档版本一旦创建，其 sha256 与 source_ref 不再改写；
--   · 处理状态显式：UPLOADED→SCANNING→PARSING→CHUNKING→INDEXING→READY，失败 FAILED，退役 RETIRED。
--
-- 幂等：create table if not exists。全新库可直接跑；存量库执行本脚本即可。
-- ------------------------------------------------------------------

create table if not exists aig_knowledge_collection (
    collection_id   bigint(20)   not null                  comment '知识集合ID',
    collection_code varchar(64)  not null                  comment '集合编码（租户内唯一）',
    collection_name varchar(160) not null                  comment '集合名称',
    owner_id        bigint(20)   default null              comment '责任人（sys_user.user_id）',
    org_scope       varchar(255) default null              comment '组织范围（可空=不限）',
    data_level      varchar(32)  not null default 'STRICT' comment '数据等级（AigDataLevelEnum；默认严格级）',
    status          char(1)      not null default '0'       comment '记录状态（0正常 1停用）',
    del_flag        char(1)      default '0'               comment '删除标志（0存在 1删除）',
    create_dept     bigint(20)   default null              comment '创建部门',
    create_by       bigint(20)   default null              comment '创建者',
    create_time     datetime                               comment '创建时间',
    update_by       bigint(20)   default null              comment '更新者',
    update_time     datetime                               comment '更新时间',
    remark          varchar(500) default null              comment '备注',
    primary key (collection_id),
    unique key uk_aig_knowledge_collection (collection_code)
) engine=innodb comment = '知识集合（设计 §8.2）';

create table if not exists aig_knowledge_document (
    document_id   bigint(20)   not null                  comment '文档ID',
    collection_id bigint(20)   not null                  comment '所属集合',
    source_name   varchar(255) not null                  comment '文档名（文件名）',
    source_type   varchar(16)  not null                  comment '来源类型（AigKnowledgeSourceTypeEnum：PDF/DOCX/TXT/MARKDOWN）',
    owner_id      bigint(20)   default null              comment '责任人',
    status        char(1)      not null default '0'       comment '记录状态（0正常 1停用）',
    del_flag      char(1)      default '0'               comment '删除标志（0存在 1删除）',
    create_dept   bigint(20)   default null              comment '创建部门',
    create_by     bigint(20)   default null              comment '创建者',
    create_time   datetime                               comment '创建时间',
    update_by     bigint(20)   default null              comment '更新者',
    update_time   datetime                               comment '更新时间',
    remark        varchar(500) default null              comment '备注',
    primary key (document_id),
    key idx_aig_knowledge_document_collection (collection_id, status)
) engine=innodb comment = '知识文档（设计 §8.2）';

create table if not exists aig_knowledge_document_version (
    version_id     bigint(20)   not null                  comment '文档版本ID',
    document_id    bigint(20)   not null                  comment '所属文档',
    version        varchar(32)  not null                  comment '版本号（同文档内唯一）',
    sha256         char(64)     not null                  comment '原文哈希（重复文件提示用；不同业务范围不共享授权）',
    source_ref     varchar(500) default null              comment '原文件存储引用（对象键）',
    effective_from datetime     default null              comment '生效开始（空=立即）',
    effective_to   datetime     default null              comment '生效结束（空=长期）',
    status         varchar(16)  not null default 'UPLOADED' comment '处理/生效状态（AigKnowledgeStatusEnum）',
    del_flag       char(1)      default '0'               comment '删除标志（0存在 1删除）',
    create_dept    bigint(20)   default null              comment '创建部门',
    create_by      bigint(20)   default null              comment '创建者',
    create_time    datetime                               comment '创建时间',
    update_by      bigint(20)   default null              comment '更新者',
    update_time    datetime                               comment '更新时间',
    remark         varchar(500) default null              comment '备注',
    primary key (version_id),
    unique key uk_aig_knowledge_document_version (document_id, version),
    key idx_aig_knowledge_version_sha (sha256),
    key idx_aig_knowledge_version_status (status)
) engine=innodb comment = '知识文档版本（不可变原文；处理状态独立，设计 §8.2）';

create table if not exists aig_knowledge_processing_job (
    job_id             bigint(20)   not null              comment '处理任务ID',
    document_version_id bigint(20)  not null              comment '文档版本',
    stage              varchar(16)  not null              comment '处理阶段（AigKnowledgeStatusEnum 的阶段值）',
    status             varchar(16)  not null              comment '阶段状态（RUNNING/SUCCEEDED/FAILED）',
    attempt            int(11)      not null default 1    comment '第几次尝试',
    error_code         varchar(64)  default null          comment '失败分类',
    error_message      varchar(500) default null          comment '失败原因',
    idempotency_key    varchar(128) not null              comment '幂等键（版本 + 幂等键唯一，重试不产生重复任务）',
    del_flag           char(1)      default '0'           comment '删除标志（0存在 1删除）',
    create_dept        bigint(20)   default null          comment '创建部门',
    create_by          bigint(20)   default null          comment '创建者',
    create_time        datetime                           comment '创建时间',
    update_by          bigint(20)   default null          comment '更新者',
    update_time        datetime                           comment '更新时间',
    remark             varchar(500) default null          comment '备注',
    primary key (job_id),
    unique key uk_aig_knowledge_job (document_version_id, idempotency_key),
    key idx_aig_knowledge_job_version (document_version_id, stage, status)
) engine=innodb comment = '知识处理任务（阶段 + 幂等键，设计 §8.2）';

create table if not exists aig_knowledge_chunk (
    chunk_id            bigint(20)    not null            comment '分段ID',
    document_version_id bigint(20)    not null            comment '文档版本',
    ordinal             int(11)       not null            comment '段序（同版本内唯一）',
    locator             varchar(255)  default null        comment '来源定位（页码/标题/行列）',
    text_ref            varchar(500)  default null        comment '正文存储引用（对象键）',
    text_excerpt        varchar(1000) default null        comment '引用预览（受限长度；完整正文在 text_ref）',
    index_ref           varchar(255)  default null        comment '索引引用（向量/倒排索引中的键）',
    data_level          varchar(32)   not null default 'STRICT' comment '片段数据等级',
    del_flag            char(1)       default '0'          comment '删除标志（0存在 1删除）',
    create_dept         bigint(20)    default null        comment '创建部门',
    create_by           bigint(20)    default null        comment '创建者',
    create_time         datetime                          comment '创建时间',
    update_by           bigint(20)    default null        comment '更新者',
    update_time         datetime                          comment '更新时间',
    remark              varchar(500)  default null        comment '备注',
    primary key (chunk_id),
    unique key uk_aig_knowledge_chunk (document_version_id, ordinal),
    key idx_aig_knowledge_chunk_level (data_level)
) engine=innodb comment = '知识分段（正文用存储引用，设计 §8.2）';
