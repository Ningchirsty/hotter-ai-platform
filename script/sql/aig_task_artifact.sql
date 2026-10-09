-- ----------------------------------------------------------------------------
-- AI 任务制品账本（V2 Execution Contract §输出制品 / 事件 AI_TASK_ARTIFACT_ADDED）
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）
-- 表前缀：aig_
--
-- 关键口径：
--   1. 本表是「任务产出了哪些制品」的账本，一行 = 一份制品的登记事实。
--      artifact_id 是**平台铸造的ID**（事件与执行结果的 outputs[].artifactId 引用它），
--      字节本身在对象存储里，由 storage_ref 指向——两者刻意分开：
--      引用要稳定、可比对，而字节的位置会随存储策略变。
--   2. **被拒的制品也留一行**（validation_status=FAIL，validation_detail 给字段级原因）：
--      「生产方交了不合格制品」本身就是运维要看见的事实，只回一个错误码、
--      库里什么都不留，事后就只剩对方的日志可查。默认查询只取 PASS。
--   3. **不给 (task_id, storage_ref) 建唯一键**：同一份对象被重推是常态，
--      而每次被拒都是一条独立的时间事实，唯一键会把「第二次被拒」变成插入失败——
--      证据反而丢了。PASS 行的幂等由服务层按 (task_id, storage_ref, sha256) 先查后返回。
--   4. sha256 是**生产方声明值**：v1 平台不回读对象存储重算，因此 hash_verified 一律写 N。
--      不要把「有 sha256」当成「平台验过」——这两件事必须能分开看。
--   5. 不存服务器本地路径（设计 §9 口径）；storage_ref 的形态由服务层校验。
--   6. 幂等建表（create table if not exists），可安全重复执行。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、任务制品账本
-- ----------------------------
create table if not exists aig_task_artifact (
    artifact_id       bigint(20)      not null                   comment '制品ID（平台铸造；事件与执行结果引用它）',
    task_id           bigint(20)      not null                   comment '任务ID',
    attempt_no        int(11)         not null default 0         comment '所属尝试次数',
    result_id         bigint(20)      default null               comment '关联的任务结果ID（结果可以没有制品，制品也可以多份）',
    artifact_type     varchar(32)     not null                   comment '制品类型（IMAGE/DESIGN_DOCUMENT/…；契约里是开放字符串，长度≤32）',
    mime_type         varchar(128)    not null                   comment 'MIME 类型（小写存储；是否允许由 aigov.artifact.allowed-mime-types 决定）',
    size_bytes        bigint(20)      not null                   comment '字节数（生产方声明值；0 允许）',
    sha256            char(64)        not null                   comment '内容 SHA-256（小写、64位十六进制；v1 为生产方声明值，见 hash_verified）',
    storage_ref       varchar(512)    not null                   comment '对象引用（对象存储键；禁止服务器本地路径——设计 §9 口径）',
    hash_verified     char(1)         not null default 'N'       comment '平台是否回读重算过哈希（Y/N）；v1 一律 N = sha256 是声明值，未被平台验过',
    validation_status varchar(16)     not null                   comment '校验结论（PASS已入库 / FAIL被拒）；被拒记录也是证据，默认查询只取 PASS',
    validation_detail varchar(1000)   default null               comment '校验明细（FAIL 时给字段级原因，一次列全）',
    del_flag          char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept       bigint(20)      default null               comment '创建部门',
    create_by         bigint(20)      default null               comment '创建者',
    create_time       datetime                                   comment '创建时间',
    update_by         bigint(20)      default null               comment '更新者',
    update_time       datetime                                   comment '更新时间',
    remark            varchar(500)    default null               comment '备注',
    primary key (artifact_id),
    key idx_aig_task_artifact_task (task_id, validation_status, create_time),
    key idx_aig_task_artifact_result (result_id),
    key idx_aig_task_artifact_sha (sha256),
    key idx_aig_task_artifact_ref (storage_ref(191))
) engine=innodb comment = 'AI任务制品账本';

-- ----------------------------
-- 2、核对：表在不在、列够不够（列名或数量不对时下面这条会少行）
-- ----------------------------
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_task_artifact';

select count(*) as column_count
  from information_schema.columns
 where table_schema = database()
   and table_name = 'aig_task_artifact';

select index_name, group_concat(column_name order by seq_in_index) as cols
  from information_schema.statistics
 where table_schema = database()
   and table_name = 'aig_task_artifact'
 group by index_name
 order by index_name;

-- ----------------------------
-- 3、口径自查：被拒记录必须是可查的（有 FAIL 行时不该被默认查询吃掉）
-- ----------------------------
select validation_status, count(*) as rows_count
  from aig_task_artifact
 group by validation_status;

select 'AIG_TASK_ARTIFACT_DDL_DONE' as marker;
