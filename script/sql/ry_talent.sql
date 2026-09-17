-- ----------------------------------------------------------------------------
-- 集团人才库 业务表结构  V1
-- 目标库：MySQL 8.4.x（RuoYi-Vue-Plus v6.0.0 基线）
-- 表前缀：tl_
-- 约定：沿用 RuoYi 6.0.0 主键/逻辑删除/审计字段规范
--       主键 bigint（雪花 ASSIGN_ID，MyBatis-Plus 全局 idType: ASSIGN_ID）
--       del_flag char(1) '0'存在 '1'删除  + @TableLogic
--       create_dept/create_by/create_time/update_by/update_time 由 BaseEntity 自动填充
-- 说明：人才库不新增租户字段（6.0.0 已整体移除多租户）。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、人才主档
-- ----------------------------
drop table if exists tl_talent;
create table tl_talent (
    talent_id         bigint(20)      not null                   comment '人才ID',
    talent_no         varchar(32)     not null                   comment '人才编号（对外展示，导出用）',
    name              varchar(50)     not null                   comment '姓名（明文业务字段，按权限显示）',
    gender            char(1)         default '0'                comment '性别（0未知 1男 2女，字典 tl_gender）',
    birth_date        date            default null               comment '出生日期（年龄实时计算，不单独维护）',
    age_only          int(3)          default null               comment '仅识别到的年龄（无出生日期时使用）',
    age_source_date   date            default null               comment '识别年龄对应的识别日期',
    phone_cipher      varchar(255)    default null               comment '手机号密文（@EncryptField AES 写入）',
    phone_hash        char(64)        default null               comment '手机号标准化哈希（SHA-256，用于重复预检）',
    phone_tail4       char(4)         default null               comment '手机号后四位（弱匹配用，非敏感）',
    education         varchar(32)     default null               comment '学历（字典 tl_education）',
    expect_salary_min int(11)         default null               comment '期望薪资下限（整数元/月）',
    expect_salary_max int(11)         default null               comment '期望薪资上限（整数元/月）',
    position          varchar(100)    default null               comment '应聘/意向岗位',
    contact_date      date            default null               comment '联系日期',
    region_code       varchar(16)     not null                   comment '归属区域（GROUP/SZ/ST，字典 tl_region，服务端枚举双重限制）',
    status            varchar(16)     not null default 'NEW'     comment '人才状态（字典 tl_talent_status）',
    share_scope       varchar(16)     not null default 'REGION'  comment '共享范围（REGION区域共享 GROUP全集团 GRANT_ONLY仅授权）',
    source            varchar(32)     default null               comment '来源（字典 tl_source）',
    remark            varchar(500)    default null               comment '备注（禁止写入歧视性/无关敏感标签）',
    del_flag          char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept       bigint(20)      default null               comment '创建部门',
    create_by         bigint(20)      default null               comment '创建者',
    create_time       datetime                                   comment '创建时间',
    update_by         bigint(20)      default null               comment '更新者',
    update_time       datetime                                   comment '更新时间',
    primary key (talent_id),
    unique key uk_tl_talent_no (talent_no),
    key idx_tl_talent_region_status (region_code, status, del_flag),
    key idx_tl_talent_phone_hash (phone_hash),
    key idx_tl_talent_name_region (name, region_code),
    key idx_tl_talent_contact_date (contact_date)
) engine=innodb comment = '人才主档表';

-- ----------------------------
-- 2、人才附件（简历/证件）——一个附件类型可有多个版本，仅一个当前版本
-- ----------------------------
drop table if exists tl_talent_attachment;
create table tl_talent_attachment (
    attachment_id   bigint(20)      not null                   comment '附件ID',
    talent_id       bigint(20)      not null                   comment '人才ID',
    oss_id          bigint(20)      default null               comment '预留：RuoYi sys_oss 文件ID（默认不登记，见设计说明）',
    bucket          varchar(128)    default null               comment '存储桶（私有桶）',
    object_key      varchar(500)    not null                   comment '对象键 talent-private/{talentId}/{attachmentId}/v{version}/original{ext}',
    attachment_type varchar(32)     not null                   comment '附件类型（RESUME/ID_CARD/EDUCATION_CERT/OTHER，字典 tl_attachment_type）',
    original_name   varchar(255)    default null               comment '原始文件名',
    file_ext        varchar(16)     default null               comment '扩展名（小写，白名单校验）',
    mime_type       varchar(128)    default null               comment 'MIME 类型',
    file_size       bigint(20)      default 0                  comment '字节数',
    file_hash       char(64)        default null               comment '文件 SHA-256（去重用）',
    version         int(11)         not null default 1         comment '版本号（同类型自增）',
    is_current      char(1)         default '1'                comment '是否当前版本（1是 0历史）',
    scan_status     varchar(16)     not null default 'PENDING' comment '扫描状态（PENDING/SCANNING/CLEAN/INFECTED/FAILED），非 CLEAN 禁止下载',
    scan_time       datetime        default null               comment '扫描完成时间',
    scan_remark     varchar(255)    default null               comment '扫描结果说明',
    del_flag        char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    primary key (attachment_id),
    key idx_tl_att_talent_type_current (talent_id, attachment_type, is_current),
    key idx_tl_att_file_hash (file_hash),
    key idx_tl_att_scan_status (scan_status)
) engine=innodb comment = '人才附件表';

-- ----------------------------
-- 3、联系跟进记录
-- ----------------------------
drop table if exists tl_talent_contact;
create table tl_talent_contact (
    contact_id     bigint(20)      not null                   comment '联系记录ID',
    talent_id      bigint(20)      not null                   comment '人才ID',
    contact_time   datetime        default null               comment '联系时间',
    contactor_id   bigint(20)      default null               comment '联系人（sys_user.user_id）',
    contact_result varchar(32)     default null               comment '联系结果（字典 tl_contact_result）',
    content        varchar(1000)   default null               comment '联系内容/备注',
    del_flag       char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept    bigint(20)      default null               comment '创建部门',
    create_by      bigint(20)      default null               comment '创建者',
    create_time    datetime                                   comment '创建时间',
    update_by      bigint(20)      default null               comment '更新者',
    update_time    datetime                                   comment '更新时间',
    primary key (contact_id),
    key idx_tl_contact_talent_time (talent_id, contact_time)
) engine=innodb comment = '人才联系记录表';

-- ----------------------------
-- 4、重复人才预警（只预警不自动合并）
-- ----------------------------
drop table if exists tl_talent_duplicate;
create table tl_talent_duplicate (
    duplicate_id      bigint(20)      not null                   comment '预警ID',
    source_talent_id  bigint(20)      not null                   comment '来源人才ID（本次提交的）',
    matched_talent_id bigint(20)      not null                   comment '命中人才ID（库中已存在的）',
    match_rule        varchar(64)     not null                   comment '匹配规则（PHONE_HASH/NAME_PHONE_TAIL4/NAME_REGION）',
    match_score       int(11)         default 0                  comment '匹配分数（0-100）',
    conclusion        varchar(16)     default 'PENDING'          comment '确认结论（PENDING/DIFFERENT/SAME，字典 tl_duplicate_conclusion）',
    confirm_by        bigint(20)      default null               comment '确认人',
    confirm_time      datetime        default null               comment '确认时间',
    confirm_remark    varchar(255)    default null               comment '确认说明',
    del_flag          char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept       bigint(20)      default null               comment '创建部门',
    create_by         bigint(20)      default null               comment '创建者',
    create_time       datetime                                   comment '创建时间',
    update_by         bigint(20)      default null               comment '更新者',
    update_time       datetime                                   comment '更新时间',
    primary key (duplicate_id),
    key idx_tl_dup_source (source_talent_id),
    key idx_tl_dup_matched (matched_talent_id),
    key idx_tl_dup_conclusion (conclusion)
) engine=innodb comment = '重复人才预警表';

-- ----------------------------
-- 5、单条/临时访问授权
-- ----------------------------
drop table if exists tl_talent_access_grant;
create table tl_talent_access_grant (
    grant_id      bigint(20)      not null                   comment '授权ID',
    talent_id     bigint(20)      not null                   comment '人才ID',
    grantee_type  varchar(16)     not null                   comment '被授权主体类型（USER用户 ROLE角色）',
    grantee_id    bigint(20)      not null                   comment '被授权主体ID',
    permission    varchar(64)     not null                   comment '授权动作（VIEW/DOWNLOAD/VIEW_FULL_PHONE，逗号分隔多值）',
    start_time    datetime        default null               comment '生效时间（空表示立即生效）',
    end_time      datetime        default null               comment '失效时间（空表示长期，需审批）',
    grant_by      bigint(20)      default null               comment '授权人',
    grant_time    datetime        default null               comment '授权时间',
    reason        varchar(255)    default null               comment '授权原因（审计用）',
    status        char(1)         default '0'                comment '状态（0正常 1已撤销）',
    del_flag      char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept   bigint(20)      default null               comment '创建部门',
    create_by     bigint(20)      default null               comment '创建者',
    create_time   datetime                                   comment '创建时间',
    update_by     bigint(20)      default null               comment '更新者',
    update_time   datetime                                   comment '更新时间',
    primary key (grant_id),
    key idx_tl_grant_talent (talent_id, grantee_type, grantee_id),
    key idx_tl_grant_subject (grantee_type, grantee_id, status)
) engine=innodb comment = '人才单条访问授权表';

-- ----------------------------
-- 6、解析任务（只存元数据，不存原简历正文）
-- ----------------------------
drop table if exists tl_parse_task;
create table tl_parse_task (
    task_id        bigint(20)      not null                   comment '解析任务ID',
    talent_id      bigint(20)      default null               comment '人才ID',
    attachment_id  bigint(20)      not null                   comment '附件ID',
    status         varchar(16)     not null default 'DISABLED' comment '状态（DISABLED未启用/PENDING/PROCESSING/SUCCESS/FAILED）',
    parser_version varchar(64)     default null               comment '解析服务版本',
    retry_count    int(11)         default 0                  comment '重试次数',
    error_summary  varchar(500)    default null               comment '错误摘要（禁止写入简历正文）',
    start_time     datetime        default null               comment '开始时间',
    finish_time    datetime        default null               comment '结束时间',
    del_flag       char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept    bigint(20)      default null               comment '创建部门',
    create_by      bigint(20)      default null               comment '创建者',
    create_time    datetime                                   comment '创建时间',
    update_by      bigint(20)      default null               comment '更新者',
    update_time    datetime                                   comment '更新时间',
    primary key (task_id),
    key idx_tl_parse_attachment (attachment_id),
    key idx_tl_parse_status (status)
) engine=innodb comment = '简历解析任务表';

-- ----------------------------
-- 7、解析字段（原始解析值 / 置信度 / 人工确认值，全程可追溯）
-- ----------------------------
drop table if exists tl_parse_field;
create table tl_parse_field (
    field_id        bigint(20)      not null                   comment '字段ID',
    task_id         bigint(20)      not null                   comment '解析任务ID',
    field_name      varchar(64)     not null                   comment '字段名（对应 tl_talent 字段）',
    parsed_value    varchar(500)    default null               comment '解析原始值',
    confidence      decimal(5,4)    default null               comment '置信度 0-1',
    confirmed_value varchar(500)    default null               comment '人工确认值',
    confirm_status  char(1)         default '0'                comment '确认状态（0待确认 1已确认 2已忽略）',
    confirm_by      bigint(20)      default null               comment '确认人',
    confirm_time    datetime        default null               comment '确认时间',
    del_flag        char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    primary key (field_id),
    key idx_tl_parse_field_task (task_id, field_name)
) engine=innodb comment = '解析字段复核表';

-- ----------------------------
-- 8、Excel 异步导出任务
-- ----------------------------
drop table if exists tl_export_task;
create table tl_export_task (
    export_id      bigint(20)      not null                   comment '导出任务ID',
    task_name      varchar(128)    default null               comment '任务名称',
    query_snapshot varchar(2000)   default null               comment '查询条件快照（JSON，不含明文手机号）',
    status         varchar(16)     not null default 'PENDING' comment '状态（PENDING/RUNNING/SUCCESS/FAILED/EXPIRED）',
    bucket         varchar(128)    default null               comment '存储桶',
    object_key     varchar(500)    default null               comment '导出文件对象键 talent-private/exports/{exportId}/talent-ledger.xlsx',
    file_name      varchar(255)    default null               comment '下载文件名',
    row_count      int(11)         default 0                  comment '导出行数',
    error_summary  varchar(500)    default null               comment '错误摘要',
    expire_time    datetime        default null               comment '过期时间（默认创建后24小时）',
    finish_time    datetime        default null               comment '完成时间',
    del_flag       char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept    bigint(20)      default null               comment '创建部门',
    create_by      bigint(20)      default null               comment '创建者',
    create_time    datetime                                   comment '创建时间',
    update_by      bigint(20)      default null               comment '更新者',
    update_time    datetime                                   comment '更新时间',
    primary key (export_id),
    key idx_tl_export_creator (create_by, status),
    key idx_tl_export_status (status, expire_time)
) engine=innodb comment = '人才台账导出任务表';

-- ----------------------------
-- 9、敏感操作审计（与 RuoYi 操作日志互补）
-- ----------------------------
drop table if exists tl_sensitive_audit;
create table tl_sensitive_audit (
    audit_id          bigint(20)      not null                   comment '审计ID',
    operator_id       bigint(20)      default null               comment '操作人用户ID',
    operator_name     varchar(50)     default null               comment '操作人账号（冗余，便于离线审计）',
    action            varchar(32)     not null                   comment '动作（VIEW_DETAIL/VIEW_FULL_PHONE/DOWNLOAD/EXPORT/CREATE_GRANT/DELETE/ARCHIVE/UPLOAD）',
    target_type       varchar(32)     default null               comment '对象类型（TALENT/ATTACHMENT/EXPORT/GRANT/PARSE_TASK）',
    target_id         bigint(20)      default null               comment '对象ID',
    talent_id         bigint(20)      default null               comment '关联人才ID（便于按人追溯）',
    result            char(1)         default '0'                comment '结果（0成功 1失败）',
    reason            varchar(255)    default null               comment '原因/失败摘要',
    ip_digest         varchar(64)     default null               comment '客户端IP摘要（不存原始IP）',
    user_agent_digest varchar(64)     default null               comment 'User-Agent 摘要',
    operate_time      datetime                                   comment '操作时间',
    primary key (audit_id),
    key idx_tl_audit_time_operator_action (operate_time, operator_id, action),
    key idx_tl_audit_talent (talent_id),
    key idx_tl_audit_target (target_type, target_id)
) engine=innodb comment = '人才库敏感操作审计表';
