-- ----------------------------------------------------------------------------
-- 沙箱产物账本 aig_sandbox_artifact（ADR-016：产物进平台）
--
-- 为什么需要这张表：
--   一次沙箱作业的产物原本只是**宿主机的文件**（worker 归档到 /var/lib/hotter-sandbox/archive/），
--   平台侧只有 aig_sandbox_run 里 result.json 原文带的清单（路径/字节数/sha256）。
--   于是"这次外部代码产出了什么"在平台里看不到、也取不到。
--   本表把产物**登记进平台**：元数据在这里，字节在对象存储。
--
-- 四条已确认的设计决定（2026-10-10，用户裁定；详见 ADR-016）：
--   ① **新开本表**（不复用 aig_task_artifact）：沙箱作业不是任务，task_id 会长期为空；
--   ② **逐文件上传**（不是整包）：校验粒度细、单文件失败可重试；
--   ③ **保留尽可能久、尽可能大**，超限时**删最旧**（不拒绝新上传）；
--   ④ **永不非附件下载**：一律 attachment + octet-stream + nosniff，绝不内联渲染。
--
-- 核心不变式：**只信账本、不信上传方**。
--   校验基准是登记门槛时就已经存证的 result.json 里声明的 `path/bytes/sha256`：
--   上传方塞不进清单外的文件，也换不掉清单内文件的内容。
--   `bytes`/`sha256` 都是服务端实算后写入，`object_key` 由服务端按 sha256 生成
--   （**绝不用上传方的 path 拼键**，否则路径穿越就有了落点）。
--
-- 口径（与 aig_sandbox_run / aig_package_rejection 一致）：
--   · **追加型账本**：不做逻辑删除、不带 update_* 列——证据不是可编辑的业务数据；
--   · 唯一键 (sandbox_run_id, path)：同一产物只能登记一次，重复登记会让"存了几份"变成假账；
--   · 产物入库**不参与**发布门槛判定：门槛只看 aig_sandbox_run（两者失败方式不同，别混）。
--
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）；幂等建表，可安全重复执行。
-- ----------------------------------------------------------------------------

create table if not exists aig_sandbox_artifact (
    artifact_id      bigint(20)     not null                 comment '产物记录ID',
    sandbox_run_id   bigint(20)     not null                 comment '所属沙箱运行记录ID（aig_sandbox_run.sandbox_run_id）',
    path             varchar(512)   not null                 comment '产物相对路径（与 result.json 里声明的一致）',
    bytes            bigint(20)     not null                 comment '字节数（服务端实算，必须与账本声明一致）',
    sha256           char(64)       not null                 comment 'SHA-256（服务端实算，必须与账本声明一致）',
    object_key       varchar(255)   not null                 comment '对象存储键（服务端按 sha256 生成，不使用上传方 path）',
    content_type     varchar(128)   default null             comment '上传方声明的类型（仅记录，下载时**不回显**）',
    uploaded_by      bigint(20)     default null             comment '登记人ID',
    create_time      datetime       not null                 comment '登记时间',
    primary key (artifact_id),
    unique key uk_aig_sb_artifact (sandbox_run_id, path),
    key idx_aig_sb_artifact_run (sandbox_run_id),
    key idx_aig_sb_artifact_sha (sha256)
) engine=innodb comment = '沙箱产物账本（追加型；元数据在库、字节在对象存储）';

-- 权限：**不需要新权限点**。登记复用 aig:sandbox:record，下载/列表复用 aig:sandbox:list
--   （与门槛登记同一批人；另开权限点只会多一处需要维护的授权，而这里没有新的角色边界）。

-- 核对：表在不在、列数量
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_sandbox_artifact';

select count(*) as column_count
  from information_schema.columns
 where table_schema = database()
   and table_name = 'aig_sandbox_artifact';

-- 口径自查：新表应为空
select count(*) as existing_rows from aig_sandbox_artifact;

select 'AIG_SANDBOX_ARTIFACT_DDL_DONE' as marker;
