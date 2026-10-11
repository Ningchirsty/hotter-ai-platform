-- ------------------------------------------------------------------
-- 用户 ↔ 品牌归属 aig_user_brand（④：让"按品牌定向"的岗位绑定可判定）
--
-- 依据：F-05（岗位绑定只做**可见性过滤**，只存 sys_dept 的组织ID 与 brand_id）。
--   此前本仓**没有任何"用户属于哪个品牌"的数据源**，因此 aig_role_binding.brand_id 恒不可命中。
--
-- 【它是什么、不是什么】
--   是一张**最小主数据登记表**：只回答"这个用户属于哪些品牌"，供岗位可见性判定使用。
--   **不是授权体系**（不为它建角色/菜单数据权限），也**不是岗位绑定**（绑定在 aig_role_binding）。
--   品牌本身的名称/编码等主数据不在本表——这里只存 brand_id（与绑定表同一口径：治理层只存 ID，无外键）。
--
-- 【唯一键 (user_id, brand_id) 与两列为什么 NOT NULL】
--   同一用户对同一品牌只能有一条。两列都 NOT NULL：唯一键不约束 NULL，
--   若 brand_id 可空，就能插出多条"没有品牌"的行（本仓已在 aig_task.create_by、
--   aig_launch_record.org_id、aig_user_workspace_pref.org_id 上各踩过一次）。
--
-- 【运维方式】
--   默认关闭（aigov.user-brand.enabled=false）：表没建/没数据时，品牌绑定对谁都不生效
--   （fail-closed，见 AigUserBrandResolverNotYetAvailable）。运维执行本脚本并打开开关后，
--   由管理端接口（/aigov/user-brand，权限 aig:user-brand:*）或直接维护本表来登记归属。
--
-- 本仓约定：主键 BIGINT 由 MyBatis-Plus 雪花生成（无 AUTO_INCREMENT，裸 SQL 必须显式给 ID）；
--   审计列由 BaseEntity 自动填充；逻辑删除用 del_flag。
-- 幂等：create table if not exists（只建新表，不含 ALTER，存量库可直接重放）。
-- ------------------------------------------------------------------

create table if not exists aig_user_brand (
    user_brand_id bigint(20)   not null                  comment '主键（雪花 ID）',
    user_id       bigint(20)   not null                  comment '用户ID（sys_user.user_id）',
    brand_id      bigint(20)   not null                  comment '品牌ID（业务主数据；治理层只存 ID，无外键）',
    status        char(1)      default '0'                comment '记录状态（0正常 1停用；停用=暂时不参与判定）',
    del_flag      char(1)      default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept   bigint(20)   default null               comment '创建部门',
    create_by     bigint(20)   default null               comment '创建者',
    create_time   datetime                                comment '创建时间',
    update_by     bigint(20)   default null               comment '更新者',
    update_time   datetime                                comment '更新时间',
    remark        varchar(500) default null               comment '备注',
    primary key (user_brand_id),
    unique key uk_aig_user_brand (user_id, brand_id),
    key idx_aig_user_brand_user (user_id, del_flag)
) engine=innodb comment = '用户↔品牌归属（岗位按品牌可见性判定的数据源；不是授权体系）';

-- 核对
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_user_brand';
