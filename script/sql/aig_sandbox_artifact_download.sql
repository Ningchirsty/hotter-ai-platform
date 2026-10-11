-- ----------------------------------------------------------------------------
-- 沙箱产物**下载**账本 aig_sandbox_artifact_download（ADR-016 §安全硬要求 ⑤：谁下载了哪个产物要留痕）
--
-- 为什么单独一张表、而不是只记应用日志：
--   ADR-016 的硬要求是"谁下载了哪个产物要留痕"。应用日志会被轮转、会在容器重建时丢掉，
--   而"不可信内容被谁取走过"是安全事件的第一个问题。日志可以顺手改口径、表不行。
--
-- 为什么它是**独立**账本（不复用 aig_sandbox_artifact）：
--   产物表是"存了什么"（一次作业一条，唯一键 (run_id, path)）；下载表是"谁取过"（可多次、追加型）。
--   合成一张表就得把"下载次数"变成某一列上的计数器——那就再也答不出"谁在什么时候取的"。
--
-- 记什么：run + artifact + path + **当时下发的 sha256/bytes** + 谁 + 从哪个 IP + 何时。
--   记 sha256/bytes 而不是只记 artifact_id：事后核对"当时给他的到底是哪一份字节"。
--
-- 口径（与 aig_sandbox_run / aig_sandbox_artifact 一致）：
--   · **追加型账本**：不做逻辑删除、不带 update_* 列——审计记录不是可编辑的业务数据；
--   · **先记后发**：服务层在真正下发字节之前写这一行；写不进去就**拒绝下发**
--     （拿不出留痕就不把不可信内容交出去，这正是这条要求的意义）。
--
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）；幂等建表，可安全重复执行。
-- ----------------------------------------------------------------------------

create table if not exists aig_sandbox_artifact_download (
    download_id      bigint(20)     not null                 comment '下载记录ID',
    sandbox_run_id   bigint(20)     not null                 comment '所属沙箱运行记录ID',
    artifact_id      bigint(20)     not null                 comment '产物记录ID（aig_sandbox_artifact.artifact_id）',
    path             varchar(512)   not null                 comment '产物相对路径',
    sha256           char(64)       not null                 comment '本次下发的 SHA-256（与账本核对通过后的值）',
    bytes            bigint(20)     not null                 comment '本次下发的字节数',
    downloaded_by    bigint(20)     default null             comment '下载人ID（取自登录态）',
    client_ip        varchar(64)    default null             comment '来源 IP（审计用）',
    create_time      datetime       not null                 comment '下载时间',
    primary key (download_id),
    key idx_aig_sb_dl_run (sandbox_run_id, download_id),
    key idx_aig_sb_dl_artifact (artifact_id),
    key idx_aig_sb_dl_user (downloaded_by, create_time)
) engine=innodb comment = '沙箱产物下载账本（追加型；谁在何时从哪个 IP 取走了哪份产物）';

-- 权限：**不需要新权限点**。查看下载留痕复用 aig:sandbox:list
--   （与"看产物清单"同一批人；这里没有新的角色边界）。

-- 核对：表在不在、列数量
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_sandbox_artifact_download';

select count(*) as column_count
  from information_schema.columns
 where table_schema = database()
   and table_name = 'aig_sandbox_artifact_download';

-- 口径自查：新表应为空
select count(*) as existing_rows from aig_sandbox_artifact_download;

select 'AIG_SANDBOX_ARTIFACT_DOWNLOAD_DDL_DONE' as marker;
