-- ----------------------------------------------------------------------------
-- Package 注册被拒的**证据账本**（ADR-013 未决项的落地：被拒也要留痕）
--
-- 为什么有这张表：
--   注册一路有三个拒绝点：Manifest 不合法、包体校验和不匹配、包体内容安全检查不通过。
--   在这之前三者都只是"抛异常 + 一句可读原因"——库里什么都不留，
--   事后只剩 oper_log 里那条异常记录，**看不到对方到底交了什么**。
--   而这三个拒绝点恰恰是"有人在往里塞不该塞的东西"时最该留下证据的地方。
--   本表与 ADR-010（制品被拒留 FAIL 证据行）、ADR-011（策略结论留痕）同一口径。
--
-- 口径（与 aig_policy_decision_log / aig_callback 一致）：
--   · **追加型账本**：不做逻辑删除、不带 update_* 列——它是证据，不是可编辑的业务数据；
--   · **独立事务写入**（实现侧 REQUIRES_NEW）：注册失败会回滚调用方事务，
--     证据必须活下来；否则"留痕"只在成功路径上成立，而成功路径本来就不需要它；
--   · **超长字段一律截断到列宽**：被拒往往正是因为字段畸形（超长/非 JSON），
--     不截断会让"记录拒绝原因"这句 SQL 自己报 Data too long，把干净的业务拒绝变成 500。
--
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）；幂等建表，可安全重复执行。
-- ----------------------------------------------------------------------------

create table if not exists aig_package_rejection (
    rejection_id    bigint(20)      not null                    comment '被拒记录ID',
    package_code    varchar(64)     default null                comment '包编码（Manifest 无法解析时为空，此时看 detail）',
    package_version varchar(32)     default null                comment '包版本（同上，可能为空）',
    body_name       varchar(255)    default null                comment '上传的文件名（对方交的是什么，从名字就能看出来）',
    body_sha256     char(64)        default null                comment '包体 SHA-256（服务端实算；为空=连包体都没拿到）',
    body_size       bigint(20)      default null                comment '包体字节数',
    reject_reason   varchar(32)     not null                    comment '拒绝原因（MANIFEST_INVALID/CHECKSUM_MISMATCH/ARCHIVE_UNSAFE）',
    hit_rules       varchar(500)    default null                comment '命中的拒绝规则码（逗号分隔；ARCHIVE_UNSAFE 时来自包体扫描）',
    detail          varchar(1000)   default null                comment '可读明细（字段级原因/扫描发现，截断到列宽）',
    operator_id     bigint(20)      default null                comment '操作者ID（取不到时为空=系统触发）',
    create_time     datetime        not null                    comment '记录时间',
    primary key (rejection_id),
    key idx_aig_pkg_rej_code (package_code, create_time),
    key idx_aig_pkg_rej_reason (reject_reason, create_time),
    key idx_aig_pkg_rej_hash (body_sha256)
) engine=innodb comment = 'Package 注册被拒证据账本（追加型）';

-- 核对：表在不在、列数量
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_package_rejection';

select count(*) as column_count
  from information_schema.columns
 where table_schema = database()
   and table_name = 'aig_package_rejection';

-- 口径自查：按原因分组看被拒情况（新表应为空）
select reject_reason, count(*) as rows_count
  from aig_package_rejection
 group by reject_reason;

select 'AIG_PACKAGE_REJECTION_DDL_DONE' as marker;
