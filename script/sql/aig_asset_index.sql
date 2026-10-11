-- ------------------------------------------------------------------
-- 资产聚合索引 aig_asset_index（跨域全局分页的数据源；增量 11）
--
-- 依据：设计 §6.1 的"我的资产"要跨图片/视频/内容三个域；分组视图不需要排序，
--   但**真正的跨域全局分页**做不到在查询时把三域合成一个有序流——
--   要么内存归并（受数据量上限约束），要么建这张**聚合索引表**（用户拍板：用索引表）。
--
-- 【它是什么、不是什么】
--   是一张**派生缓存**：内容来自各域的 MyAssetPort（各域自己保证归属过滤），
--   本表只负责"把它们放到一起按时间排序分页"。
--   **不是事实来源**：任何一条都可以随时按域重建；各域删了资产、重建后本表就没了它。
--   因此本表**物理删除**（重建时先删该用户该域的行再插），不设 del_flag——
--   逻辑删除会让旧行占着唯一键，"重建同一对"将被唯一约束挡住（本仓踩过同类坑）。
--
-- 【同步】
--   没有 MQ，也不引入调度依赖：同步入口是**按用户重建**（IAigAssetIndexService#rebuildForUser），
--   由门户的"刷新我的资产"触发；将来若要做定时清扫，调用同一个服务即可。
--   重建是"先删该用户该域、再分页拉取插入"，在同一事务里完成：读者要么看到旧的一份，
--   要么看到新的一份，不会看到中间的空档。
--
-- 本仓约定：主键 BIGINT 由 MyBatis-Plus 雪花生成（无 AUTO_INCREMENT，裸 SQL 必须显式给 ID）。
-- 幂等：create table if not exists（只建新表，不含 ALTER，存量库可直接重放）。
-- ------------------------------------------------------------------

create table if not exists aig_asset_index (
    index_id     bigint(20)   not null                  comment '主键（雪花 ID）',
    user_id      bigint(20)   not null                  comment '用户ID（各域 MyAssetPort 的归属过滤结果）',
    domain       varchar(16)  not null                  comment '域编码（IMAGE/VIDEO/CONTENT）',
    asset_id     bigint(20)   not null                  comment '资产ID（各域自己的主键）',
    asset_type   varchar(32)  default null              comment '资产类型（各域口径）',
    source_kind  varchar(16)  default null              comment '来源（UPLOAD/OUTPUT/REFERENCE…）',
    name         varchar(255) default null              comment '展示名',
    mime_type    varchar(128) default null              comment 'MIME（内容域不存，留空）',
    size_bytes   bigint(20)   default null              comment '字节数',
    task_id      bigint(20)   default null              comment '产出/所属任务ID（可空）',
    asset_time   datetime     default null              comment '资产的创建时间（排序键；来自各域）',
    create_dept  bigint(20)   default null              comment '创建部门',
    create_by    bigint(20)   default null              comment '创建者',
    create_time  datetime                               comment '创建时间（本行写入时间）',
    update_by    bigint(20)   default null              comment '更新者',
    update_time  datetime                               comment '更新时间',
    remark       varchar(500) default null              comment '备注',
    primary key (index_id),
    unique key uk_aig_asset_index (user_id, domain, asset_id),
    key idx_aig_asset_index_user_time (user_id, asset_time)
) engine=innodb comment = '资产聚合索引（跨域全局分页的派生缓存；可按域重建）';

-- 核对
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_asset_index';
