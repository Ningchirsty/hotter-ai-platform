-- ----------------------------------------------------------------------------
-- 沙箱运行证据账本 aig_sandbox_run（ADR-015 的第三切片：让 SANDBOX_RUN 门槛可核对）
--
-- 为什么需要这张表：
--   五个发布门槛里，MANIFEST_VALIDATION（scan_result）、GOLDEN_CASE（评测账本）、
--   CANARY（调用审计统计）、HUMAN_APPROVAL（approved_by）都能查到东西，
--   唯独 SANDBOX_RUN 此前**没有任何证据校验**——「在沙箱跑通了」这句话的实际含义
--   只是「调用方这么认为」。而它恰恰是唯一能证明"这段外部代码真的在隔离环境里跑起来过"
--   的一环，最不该只凭声明。
--
-- 证据从哪来（本次口径 = 人工登记，见 ADR-015 补遗）：
--   宿主侧 worker（script/deploy/sandbox-worker.sh）把每次作业的结果写成 result.json。
--   由管理员/运维把这个结果**原样**登记进来（接口：POST /aigov/sandbox/run/record），
--   服务端对原文实算 SHA-256 一并留存 ⇒ 事后改口能被发现。
--   为什么先人工登记而不是自动化：自动化通道要么改后端容器挂载（把"能开容器"变相给平台）、
--   要么打开服务令牌开关，两者都是生产侧变更；先把门槛的证据要求落地，通道后置。
--
-- 口径（与 aig_package_rejection / aig_policy_decision_log 一致）：
--   · **追加型账本**：不做逻辑删除、不带 update_* 列——证据不是可编辑的业务数据；
--   · **一个作业只能登记一次**（job_id 唯一）：重复登记会让"跑通过几次"变成假账，
--     而这正是这条路唯一要防的事；
--   · **原文留存**（result_json）：摘要列是为了查询方便，不是替代原文；
--     判据永远只看原文里那几个字段。
--
-- 权限（aig:sandbox:record / aig:sandbox:list）在 **aig_sandbox_run_perm.sql** 里，
-- 拆开是因为仓库自带一条构建期检查（AigPermissionSeedCoverageTest）只认文件名含
-- menu/perm 的脚本——与其改那条护栏，不如按约定放对地方。
--
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）；幂等建表，可安全重复执行。
-- ----------------------------------------------------------------------------

create table if not exists aig_sandbox_run (
    sandbox_run_id    bigint(20)    not null                 comment '沙箱运行记录ID',
    target_type       varchar(32)   not null                 comment '对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION）',
    target_version_id bigint(20)    not null                 comment '对象版本ID',
    job_id            varchar(128)  not null                 comment '作业ID（worker 队列目录名；唯一：同一作业只能登记一次）',
    agent_code        varchar(64)   default null             comment '作业里的 Agent 编码（可空；来自 request.json）',
    image_ref         varchar(255)  not null                 comment '实际运行的镜像 ref（必须在白名单里，执行器已强制）',
    exit_code         int(11)       not null                 comment '容器退出码（0=跑通；124/137=超时或被杀）',
    timed_out         tinyint(1)    not null default 0       comment '是否超时被杀（1=是）',
    duration_ms       bigint(20)    default null             comment '执行耗时毫秒（执行器实测）',
    network           varchar(16)   not null                 comment '网络模式（none=无网；bridge=允许出网，不满足门槛）',
    scratch_free_mb   bigint(20)    default null             comment '作业结束时 scratch 剩余 MB',
    artifact_count    int(11)       not null default 0       comment '产物个数',
    result_sha256     char(64)      not null                 comment 'result.json 原文 SHA-256（服务端实算，防事后改口）',
    result_json       mediumtext    not null                 comment 'result.json 原文（证据本体，原样留存）',
    recorded_by       bigint(20)    default null             comment '登记人ID（谁把这个结果记进来的）',
    create_time       datetime      not null                 comment '登记时间',
    primary key (sandbox_run_id),
    unique key uk_aig_sandbox_job (job_id),
    key idx_aig_sandbox_target (target_type, target_version_id, create_time)
) engine=innodb comment = '沙箱运行证据账本（追加型；SANDBOX_RUN 门槛的判据来源）';

-- 核对：表在不在、列数量
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_sandbox_run';

select count(*) as column_count
  from information_schema.columns
 where table_schema = database()
   and table_name = 'aig_sandbox_run';

-- 口径自查：账本应为空（新表）
select count(*) as existing_rows from aig_sandbox_run;

select 'AIG_SANDBOX_RUN_DDL_DONE' as marker;
