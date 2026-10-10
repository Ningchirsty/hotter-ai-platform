-- ------------------------------------------------------------------
-- 员工 AI 工作台偏好 aig_user_workspace_pref（主文档线增量 5：门户偏好）
--
-- 依据：附件 §6.1（`POST /ai-workspace/favorites`）与 workspace 设计稿 §5 的表清单
--   （`aig_user_workspace_pref`：user_id, org_id, default_role_code, favorites_json，唯一 (user_id, org_id)）。
--
-- 【它是什么、不是什么】
--   只是**个人偏好**：收藏了哪些岗位、默认打开哪个岗位。它<b>不参与任何可见性判定</b>——
--   收藏一个岗位不会让本来不可见的岗位变得可见（服务层在收藏时校验"该岗位对本人可见"，
--   读取时也仍然以可见岗位为准）。把偏好当授权用，就会得到"收藏过的岗位离职后还能看到"这类问题。
--
-- 【唯一键 (user_id, org_id) 与 org_id 为什么 NOT NULL】
--   同一个用户在换组织后应当有各自的一份默认岗位；而没有部门的用户若把 org_id 存成 NULL，
--   **唯一键不约束 NULL**（本仓已在 aig_task.create_by 与 aig_launch_record.org_id 上各踩过一次），
--   于是"同一个用户 + 同一个无组织"能插出多条偏好行，表现是"我设的默认岗位一会儿变一个"。
--   所以无组织时写 0 哨兵。
--
-- 【为什么 favorites_json 存一串而不是一张关系表】
--   偏好是整体读写的（打开工作台一次性取回），条目少、不需要按收藏反查岗位。
--   多一张表就多一处要与偏好对齐的地方，而它没有任何查询需求。
--
-- 本仓约定：主键 BIGINT 由 MyBatis-Plus 雪花生成（无 AUTO_INCREMENT，裸 SQL 必须显式给 ID）；
--   审计列由 BaseEntity 自动填充；逻辑删除用 del_flag。
-- 幂等：create table if not exists（只建新表，不含 ALTER，存量库可直接重放）。
-- ------------------------------------------------------------------

create table if not exists aig_user_workspace_pref (
    pref_id           bigint(20)   not null                  comment '偏好ID',
    user_id           bigint(20)   not null                  comment '用户ID',
    org_id            bigint(20)   not null default 0         comment '组织（sys_dept.dept_id；无组织用 0）。★不能为 NULL：唯一键不约束 NULL，会让"无组织"的用户能存出多份偏好',
    default_role_code varchar(80)  default null              comment '默认打开的岗位编码（必须对本人可见，服务层校验）',
    favorites_json    longtext     default null              comment '收藏的岗位编码清单（JSON 数组；只是偏好，不参与可见性判定）',
    status            char(1)      default '0'               comment '记录状态（0正常 1停用）',
    del_flag          char(1)      default '0'               comment '删除标志（0代表存在 1代表删除）',
    create_dept       bigint(20)   default null              comment '创建部门',
    create_by         bigint(20)   default null              comment '创建者',
    create_time       datetime                               comment '创建时间',
    update_by         bigint(20)   default null              comment '更新者',
    update_time       datetime                               comment '更新时间',
    remark            varchar(500) default null              comment '备注',
    primary key (pref_id),
    unique key uk_aig_ws_pref_user_org (user_id, org_id),
    key idx_aig_ws_pref_org (org_id, user_id)
) engine=innodb comment = '员工 AI 工作台偏好（收藏与默认岗位；不参与可见性判定）';

-- 核对
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'aig_user_workspace_pref';
