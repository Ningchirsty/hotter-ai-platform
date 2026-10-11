-- ------------------------------------------------------------------
-- 岗位工作台（主文档线）：启动记录 aig_launch_record（增量 3：Launch Resolver）
--
-- 依据：附件 §12（Launch Resolver）、§12.5（错误码）；workspace 设计稿 §6.1/§6.4。
--
-- 【它回答什么问题】
--   「员工点的那张卡片，到底变成了什么？」——prepare/commit 两步之后，这里留下一条记录：
--   谁（user_id/org_id）、按哪个岗位版本的哪张卡片（role_version_id/action_code）、
--   启动到哪（target_type/target_ref）、最终对应哪个平台任务（task_id）。
--   少了这张表，"卡片点了没反应"这类问题只能靠猜。
--
-- 【唯一键为什么是 (org_id, user_id, idempotency_key)】
--   幂等的**作用域是"人 + 组织"**，不是全局：两个不同员工拿到同一个幂等键
--   （例如客户端用同一个随机种子）不应该互相顶掉对方的启动。
--   组织进键是为了让"同一员工换了组织"不误判成同一次启动。
--
-- 【org_id 为什么 NOT NULL + 0 哨兵】（实测教训）
--   唯一键**不约束 NULL**：`(NULL, user, key)` 可以插入任意多条。若"没有部门的用户"
--   把 org_id 存成 NULL，这一整类的幂等就静默失效了——表现是"同一个键提交两次建出两个启动"。
--   所以没有组织时写 0（本仓已有同类先例：aig_task 的 SYSTEM_SUBMITTER_ID）。
--   探针里专门有一条"NULL org 不再能绕过幂等"的断言。
--
-- 【request_digest 是干什么的】
--   同一个幂等键**换了请求内容**不能当成同一次启动（那会让第二次的输入被静默丢弃）。
--   所以除了幂等键，还存请求内容摘要：键相同 + 摘要不同 = 冲突，必须报错而不是"返回第一次的结果"。
--
-- 【为什么没有"先落 PREPARED 再改 COMMITTED"两个状态】
--   本表的记录在 commit 时写入，且 commit 是一个事务：
--   若建任务失败，记录与任务一起回滚——员工看到报错，且**确实没有启动发生**（这与训练台测试调用不同：
--   那里的钱是在调用过程中花掉的，所以必须先留痕；这里启动阶段不产生费用，费用发生在任务执行时）。
--   因此 launch_status 本增量**只会出现 COMMITTED**；取值 FAILED 留给将来"任务已建、后续确认失败"
--   这类需要显式收尾的场景——列先留着，但不要以为现在会用它。
--   票据本身**不落库**（放 Redis，见 IAigLaunchTicketStore）。
--
-- 本仓约定：主键 BIGINT 由 MyBatis-Plus 雪花生成（无 AUTO_INCREMENT，裸 SQL 必须显式给 ID）；
--   审计列由 BaseEntity 自动填充；逻辑删除用 del_flag。
-- 幂等：create table if not exists（只建新表，不含 ALTER，存量库可直接重放）。
-- ------------------------------------------------------------------

create table if not exists aig_launch_record (
    launch_id        bigint(20)   not null                  comment '启动记录ID',
    org_id           bigint(20)   not null default 0         comment '启动时的组织（sys_dept.dept_id；无组织时用 0）。★不能为 NULL：NULL 在唯一键里不被约束，会让 (org,user,key) 的幂等对"没有部门的人"整类失效',
    user_id          bigint(20)   not null                  comment '启动人',
    role_code        varchar(80)  not null                  comment '岗位编码（便于按岗位统计，不必回查版本）',
    role_version_id  bigint(20)   not null                  comment '岗位版本（发布后不可变，所以它能定位"当时是哪份配置"）',
    action_code      varchar(64)  not null                  comment '卡片编码',
    launch_mode      varchar(16)  not null                  comment '启动方式（AigActionLaunchModeEnum）',
    target_type      varchar(24)  not null                  comment '目标类型（AigLaunchTargetTypeEnum）',
    target_ref       varchar(255) not null                  comment '目标引用（scenario://code@version / 能力编码 / routeKey）',
    request_digest   char(64)     not null                  comment '请求内容摘要（规范化 JSON 的 sha256）：同一幂等键换内容=冲突',
    idempotency_key  varchar(64)  not null                  comment '幂等键（由调用方给出，客户端重试必须复用）',
    project_type     varchar(32)  default null              comment '业务域（传给 aig_task 的 project_type）',
    project_id       bigint(20)   default null              comment '业务项目ID（未做项目权校验前不会用它启动）',
    task_id          bigint(20)   default null              comment '平台任务ID（NAVIGATION 类无任务，为空）',
    task_no          varchar(64)  default null              comment '平台任务编号（便于人工对齐）',
    launch_status    varchar(16)  not null default 'COMMITTED' comment '启动状态（COMMITTED/FAILED）',
    error_code       varchar(32)  default null              comment '失败时的错误码（AigLaunchErrorEnum）',
    committed_at     datetime     default null              comment '启动时间',
    status           char(1)      default '0'               comment '记录状态（0正常 1停用）',
    del_flag         char(1)      default '0'               comment '删除标志（0代表存在 1代表删除）',
    create_dept      bigint(20)   default null              comment '创建部门',
    create_by        bigint(20)   default null              comment '创建者',
    create_time      datetime                               comment '创建时间',
    update_by        bigint(20)   default null              comment '更新者',
    update_time      datetime                               comment '更新时间',
    remark           varchar(500) default null              comment '备注',
    primary key (launch_id),
    unique key uk_aig_launch_idem (org_id, user_id, idempotency_key),
    key idx_aig_launch_user (user_id, committed_at),
    key idx_aig_launch_task (task_id),
    key idx_aig_launch_role (role_version_id, action_code)
) engine=innodb comment = '岗位启动记录（prepare 票据在 Redis；commit 落库并带幂等键与请求摘要）';

-- 核对
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_launch_record';
