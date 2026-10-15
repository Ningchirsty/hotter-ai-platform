-- ------------------------------------------------------------------
-- AI 治理：启动任务模板 aig_launch_template（设计 D11 / §6.8）
--
-- 背景（为什么需要它）：
--   员工通过岗位卡片启动任务时，过去必须自己填「任务类型 / 业务域 / 数据等级 /
--   原始上下文键」——这些是平台实现概念，员工不该、也不容易填对。填错的表现是
--   "任务建出来了却永远不动"，比当场拒绝难查得多。
--
--   本表把"执行字段"绑定到「岗位 + 卡片」上，由服务端推导；员工只填业务输入
--   （business_fields 声明要填哪些）。匹配口径：(role_code, action_code) 精确优先，
--   action_code 为空的行是该岗位的默认模板；都没有时不套用模板，行为与过去一致。
--
-- 为什么独立成表而不是给 aig_role_action 加列：
--   岗位版本发布后内容不可变（加列要走独立的 schema 变更 + 版本迁移）。模板是平台
--   运营侧配置，独立成表后可随时增改而不触碰已发布版本。
--
-- 幂等：create table if not exists。
-- 兼容性：新表，不改变任何既有表与既有启动行为（无模板时 apply 返回 false）。
-- ------------------------------------------------------------------

create table if not exists aig_launch_template (
    template_id           bigint(20)   not null                  comment '模板ID',
    template_code         varchar(64)  not null                  comment '模板编码（唯一，运营引用用）',
    role_code             varchar(80)  not null                  comment '岗位编码',
    action_code           varchar(64)  default ''                comment '卡片编码（空=该岗位默认模板）',
    title                 varchar(160) default null              comment '业务标题（员工看到的名称）',
    task_type             varchar(32)  default null              comment '服务端推导的任务类型（AigTaskTypeEnum.code）',
    project_type          varchar(32)  default null              comment '服务端推导的业务域',
    data_level            varchar(32)  default null              comment '服务端推导的数据等级（AigDataLevelEnum.code；服务端结论，员工不可放宽）',
    business_fields       varchar(1000) default null             comment '暴露给员工填写的业务字段（逗号分隔上下文键，如 sku,market,channel）',
    snapshot_from_context char(1)      not null default 'Y'      comment '是否用业务输入自动组装输入快照（Y/N）',
    enabled               char(1)      not null default 'Y'      comment '是否启用（Y/N）',
    status                char(1)      default '0'               comment '记录状态（0正常 1停用）',
    del_flag              char(1)      default '0'               comment '删除标志（0存在 1删除）',
    create_dept           bigint(20)   default null              comment '创建部门',
    create_by             bigint(20)   default null              comment '创建者',
    create_time           datetime                               comment '创建时间',
    update_by             bigint(20)   default null              comment '更新者',
    update_time           datetime                               comment '更新时间',
    remark                varchar(500) default null              comment '备注',
    primary key (template_id),
    unique key uk_aig_launch_template_code (template_code),
    key idx_aig_launch_template_role (role_code, action_code, enabled, status)
) engine=innodb comment = '启动任务模板：员工只填业务输入，执行字段由服务端推导（D11）';

-- 核对：表已建出且列齐
select column_name, column_type, is_nullable, column_default
  from information_schema.columns
 where table_schema = database() and table_name = 'aig_launch_template'
 order by ordinal_position;
