-- ----------------------------------------------------------------------------
-- 招聘与人才管理一体化系统 —— 人才主数据表 DDL（P1 地基）
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）
-- 表前缀：hr_talent_（talent，人才主数据域）
-- 设计依据：《招聘与人才管理一体化系统详细设计方案（RuoYi-Vue-Plus v6.0.0）》
--           §9.2 表命名、§9.3 通用字段、§9.4 字段规则、§9.5 索引设计、§9.6 并发控制、
--           §21.13 数据库物理设计约定、§21.14 人才数据权限查询规则
-- 契约文件：docs/hr-talent/SPEC-P1-地基.md
--
-- 文件性质：
--   本文件为**初始化脚本**，包含 drop table if exists，会先清空再重建 16 张人才主数据表；
--   生产环境迁移请使用 hr_talent_migration.sql（由 hr_recruit.sql + hr_talent.sql + hr_talent_menu.sql
--   合成，去掉 drop table if exists 并改为 create table if not exists，重跑不清空数据）。
--
-- 统一约定：
--   1. 主键、用户/部门/业务关联 ID 使用 bigint；人数、版本号、重试次数使用 int（人数非负）；
--      状态/类型/字典值使用 varchar(32) 存稳定编码不存中文；业务编号 varchar(64) 建唯一索引；
--      月份 char(7)（yyyy-MM）；日期 date；业务时间 datetime(3)；金额 decimal(12,2)；
--      哈希 char(64)（SHA-256 十六进制）；密文 varchar(512) 或 text；长文本 text；结构化快照 json。
--   2. 每张表均含通用字段 del_flag / create_dept / create_by / create_time / update_by /
--      update_time / remark。
--   3. 关键聚合根 hr_talent_profile 含 version 乐观锁字段（设计 §9.6）。
--   4. 只做逻辑关联，不建数据库外键；完整性由事务、校验与巡检保证。
--   5. 电话、邮箱密文保存，同时保存标准化不可逆哈希用于重复预警；phone_hash / email_hash
--      只建普通索引，**不建唯一约束**（兼容家庭共用联系方式等例外场景）。
--   6. 简历使用独立的 hr_talent_resume 版本表管理，不在通用附件表中重复保存业务记录；
--      附件与简历只保存 OSS 对象 ID，不保存长期公网 URL。
--   7. 主档可见范围（visibility_type + owner_dept_id + hr_talent_scope_grant）按设计 §21.14 统一生成，
--      可见范围与敏感字段权限分离。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、统一人才主档（设计 §9.2 hr_talent_profile）
--    一名自然人原则上只有一个有效主档；招聘流程只新增应聘记录
-- ----------------------------
drop table if exists hr_talent_profile;
create table hr_talent_profile (
    talent_id             bigint(20)      not null                   comment '人才主档ID（主键）',
    talent_no             varchar(64)     not null                   comment '人才编号（业务编号，唯一，数据库主键不对外展示）',
    name                  varchar(64)     not null                   comment '姓名',
    former_name           varchar(64)     default null               comment '曾用名（或英文名，§8.12）',
    gender                varchar(32)     default null               comment '性别（字典编码，如 male/female/unknown）',
    birth_date            date            default null               comment '出生日期（优先保存出生日期）',
    age_snapshot          int(11)         default null               comment '年龄快照（仅导入原值，不反推出生日期）',
    highest_education     varchar(32)     default null               comment '最高学历（high_school/college/bachelor/master/doctor/other，字典 talent_education）',
    phone_cipher          varchar(512)    default null               comment '电话密文（禁止存明文，列表默认脱敏）',
    phone_hash            char(64)        default null               comment '电话标准化不可逆哈希（SHA-256，重复预警用，不唯一）',
    phone_tail4           char(4)         default null               comment '手机号后四位（§8.17 组合检索要求；与脱敏展示同口径的部分信息，禁止在此列存放完整号码）',
    backup_phone_cipher   varchar(512)    default null               comment '备用手机号密文（禁止存明文，§8.17）',
    backup_phone_hash     char(64)        default null               comment '备用手机号标准化不可逆哈希（SHA-256，与 phone_hash 同口径，§8.17）',
    email_cipher          varchar(512)    default null               comment '邮箱密文（禁止存明文）',
    email_hash            char(64)        default null               comment '邮箱标准化（小写）不可逆哈希（SHA-256，重复预警用，不唯一）',
    other_contact_cipher  varchar(512)    default null               comment '其他联系方式密文（如微信/QQ 等，禁止存明文，§8.17）',
    current_city          varchar(64)     default null               comment '当前所在城市',
    expected_city         varchar(64)     default null               comment '期望工作城市',
    current_company       varchar(200)    default null               comment '当前公司',
    current_position      varchar(200)    default null               comment '当前职位',
    expected_position     varchar(200)    default null               comment '期望岗位（§21.15 weakMatches 依赖）',
    expected_salary_min   decimal(12,2)   default null               comment '期望薪资下限（金额，§8.19）',
    expected_salary_max   decimal(12,2)   default null               comment '期望薪资上限（金额，§8.19）',
    work_years            int(11)         default null               comment '工作年限（非负整数）',
    industry              varchar(64)     default null               comment '所属行业',
    owner_id              bigint(20)      default null               comment '人才归属（负责人）用户ID',
    owner_dept_id         bigint(20)      default null               comment '归属部门ID（数据权限按公司/部门判定）',
    owner_dept_name       varchar(100)    default null               comment '归属部门名称快照',
    assistant_ids         varchar(255)    default null               comment '协助人用户ID，多个以英文逗号分隔（§8.19 可见范围）',
    talent_status         varchar(32)     not null default 'draft'   comment '人才生命周期状态（draft/active/recruiting/reserved/hired/do_not_contact/restricted/archived/merged，字典 talent_status）',
    status_reason         varchar(500)    default null               comment '限制/禁止联系状态的原因（§7.6）',
    status_expire_date    date            default null               comment '状态到期日（§7.6.2 到期提醒）',
    visibility_type       varchar(32)     not null default 'owner'   comment '可见范围（group/company/department/owner/explicit，字典 talent_visibility_type）',
    data_level            varchar(32)     not null default 'internal' comment '数据分级（internal/sensitive/highly_sensitive，字典 recruit_data_level）',
    current_resume_id     bigint(20)      default null               comment '当前简历版本ID（同一人才只能有一个当前版本）',
    merged_to_id          bigint(20)      default null               comment '被合并到的目标主档ID（非空表示已合并，不可再新建应聘或改资料）',
    source_type           varchar(32)     default null               comment '主档来源（resume_import/manual/application/merge等稳定编码）',
    source_channel_id     bigint(20)      default null               comment '首次来源渠道ID',
    resume_update_time    datetime(3)     default null               comment '最近简历更新时间',
    last_follow_time      datetime(3)     default null               comment '最近跟进时间',
    next_follow_time      datetime(3)     default null               comment '下次联系时间（§21.10 联系提醒）',
    version               int(11)         not null default 0         comment '乐观锁版本号（设计 §9.6）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (talent_id),
    unique key uk_hr_talent_profile_no (talent_no),
    -- 仅普通索引：不唯一，兼容家庭共用电话/邮箱等例外场景
    key idx_hr_talent_profile_phone (phone_hash),
    key idx_hr_talent_profile_phone_tail4 (phone_tail4),
    key idx_hr_talent_profile_email (email_hash),
    key idx_hr_talent_profile_owner (owner_id, talent_status),
    key idx_hr_talent_profile_city (current_city, talent_status),
    key idx_hr_talent_profile_dept (owner_dept_id, visibility_type),
    key idx_hr_talent_profile_merged (merged_to_id),
    key idx_hr_talent_profile_status (talent_status, del_flag)
) engine=innodb comment = '统一人才主档表';

-- ----------------------------
-- 2、人才关键字段变更历史（设计 §9.2 hr_talent_profile_change）
-- ----------------------------
drop table if exists hr_talent_profile_change;
create table hr_talent_profile_change (
    change_id             bigint(20)      not null                   comment '变更记录ID（主键）',
    talent_id             bigint(20)      not null                   comment '人才主档ID',
    change_type           varchar(32)     not null                   comment '变更类型（create/update/archive/merge/status等稳定编码）',
    before_json           json                                       comment '变更前快照（结构化）',
    after_json            json                                       comment '变更后快照（结构化）',
    operator_id           bigint(20)      default null               comment '操作人用户ID',
    operate_time          datetime(3)     default null               comment '操作时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (change_id),
    key idx_hr_talent_profile_change (talent_id, operate_time)
) engine=innodb comment = '人才关键字段变更历史表';

-- ----------------------------
-- 3、简历版本（设计 §9.2 hr_talent_resume）
--    新文件新增版本不覆盖旧文件；talent_id + version_no 唯一
-- ----------------------------
drop table if exists hr_talent_resume;
create table hr_talent_resume (
    resume_id             bigint(20)      not null                   comment '简历版本ID（主键）',
    talent_id             bigint(20)      not null                   comment '人才主档ID',
    version_no            int(11)         not null                   comment '简历版本号（非负整数，从1递增）',
    oss_id                varchar(64)     not null                   comment '简历文件 OSS 对象ID（受控访问，不存长期公网URL）',
    original_name         varchar(255)    default null               comment '原始文件名',
    file_suffix           varchar(32)     default null               comment '文件后缀',
    file_size             bigint(20)      default null               comment '文件大小（字节）',
    file_hash             char(64)        default null               comment '文件哈希（SHA-256 十六进制，用于重复文件提示，非唯一）',
    current_flag          char(1)         not null default '1'       comment '是否当前版本（0否 1是，同一人才仅一个当前版本）',
    scan_status           varchar(32)     default null               comment '文件安全扫描状态（pending/scanning/passed/failed等稳定编码，§8.13/§21.6/§8.8）',
    parse_status          varchar(32)     not null default 'pending' comment '解析状态（pending/processing/succeeded/failed/reviewing/confirmed，字典 talent_resume_parse_status，§10）',
    review_status         varchar(32)     not null default 'pending' comment '复核状态（pending待复核/reviewing复核中/confirmed已确认/rejected已否决，字典 talent_resume_review_status）',
    parser_version        varchar(64)     default null               comment '解析器版本',
    source_type           varchar(32)     default null               comment '简历来源（manual人工录入/import导入/parse简历解析/system系统生成，字典 talent_source_type）',
    uploaded_by           bigint(20)      default null               comment '上传人用户ID',
    uploaded_time         datetime(3)     default null               comment '上传时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (resume_id),
    unique key uk_hr_talent_resume_version (talent_id, version_no),
    key idx_hr_talent_resume_hash (file_hash),
    key idx_hr_talent_resume_current (talent_id, current_flag)
) engine=innodb comment = '人才简历版本表';

-- ----------------------------
-- 4、教育经历（设计 §9.2 hr_talent_education）
-- ----------------------------
drop table if exists hr_talent_education;
create table hr_talent_education (
    education_id          bigint(20)      not null                   comment '教育经历ID（主键）',
    talent_id             bigint(20)      not null                   comment '人才主档ID',
    school_name           varchar(200)    default null               comment '学校名称',
    major                 varchar(200)    default null               comment '专业',
    education             varchar(32)     default null               comment '学历（high_school/college/bachelor/master/doctor/other，字典 talent_education）',
    degree                varchar(32)     default null               comment '学位（none/bachelor/master/doctor/other，字典 talent_degree）',
    start_date            date            default null               comment '入学日期',
    end_date              date            default null               comment '毕业日期',
    full_time_flag        char(1)         not null default '1'       comment '是否全日制（0否 1是）',
    source_type           varchar(32)     default null               comment '来源类型（manual人工录入/import导入/parse简历解析/system系统生成，字典 talent_source_type）',
    resume_id             bigint(20)      default null               comment '来源简历版本ID（人工确认为准）',
    sort_no               int(11)         not null default 0         comment '排序号（倒序展示用）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (education_id),
    key idx_hr_talent_education (talent_id, start_date)
) engine=innodb comment = '人才教育经历表';

-- ----------------------------
-- 5、工作经历（设计 §9.2 hr_talent_work）
-- ----------------------------
drop table if exists hr_talent_work;
create table hr_talent_work (
    work_id               bigint(20)      not null                   comment '工作经历ID（主键）',
    talent_id             bigint(20)      not null                   comment '人才主档ID',
    company_name          varchar(200)    default null               comment '公司名称',
    department_name       varchar(200)    default null               comment '部门名称',
    position_name         varchar(200)    default null               comment '职位名称',
    industry              varchar(64)     default null               comment '所属行业',
    start_date            date            default null               comment '入职日期',
    end_date              date            default null               comment '离职日期（在职可为空）',
    leave_reason          varchar(500)    default null               comment '离职原因（§8.14）',
    current_flag          char(1)         not null default '0'       comment '是否当前在职（0否 1是）',
    responsibility        text                                       comment '工作职责',
    achievement           text                                       comment '工作业绩',
    source_type           varchar(32)     default null               comment '来源类型（manual人工录入/import导入/parse简历解析/system系统生成，字典 talent_source_type）',
    resume_id             bigint(20)      default null               comment '来源简历版本ID',
    sort_no               int(11)         not null default 0         comment '排序号（倒序展示用）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (work_id),
    key idx_hr_talent_work (talent_id, start_date)
) engine=innodb comment = '人才工作经历表';

-- ----------------------------
-- 6、项目经历（设计 §9.2 hr_talent_project）
-- ----------------------------
drop table if exists hr_talent_project;
create table hr_talent_project (
    project_id            bigint(20)      not null                   comment '项目经历ID（主键）',
    talent_id             bigint(20)      not null                   comment '人才主档ID',
    project_name          varchar(200)    default null               comment '项目名称',
    project_role          varchar(100)    default null               comment '项目角色',
    start_date            date            default null               comment '项目开始日期',
    end_date              date            default null               comment '项目结束日期',
    description           text                                       comment '项目描述',
    responsibility        text                                       comment '项目职责',
    achievement           text                                       comment '项目业绩',
    source_type           varchar(32)     default null               comment '来源类型（manual人工录入/import导入/parse简历解析/system系统生成，字典 talent_source_type）',
    resume_id             bigint(20)      default null               comment '来源简历版本ID',
    work_id               bigint(20)      default null               comment '关联工作经历ID',
    sort_no               int(11)         not null default 0         comment '排序号（倒序展示用）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (project_id),
    key idx_hr_talent_project (talent_id, start_date)
) engine=innodb comment = '人才项目经历表';

-- ----------------------------
-- 7、人才池（设计 §9.2 hr_talent_pool）
-- ----------------------------
drop table if exists hr_talent_pool;
create table hr_talent_pool (
    pool_id               bigint(20)      not null                   comment '人才池ID（主键）',
    pool_code             varchar(64)     not null                   comment '人才池编码（业务编号，唯一）',
    pool_name             varchar(100)    not null                   comment '人才池名称',
    pool_type             varchar(32)     default null               comment '人才池类型（reserve储备/position岗位定向/talent专项等稳定编码）',
    owner_dept_id         bigint(20)      default null               comment '归属部门ID',
    owner_dept_name       varchar(100)    default null               comment '归属部门名称快照',
    manager_id            bigint(20)      default null               comment '池管理员用户ID',
    visibility_type       varchar(32)     not null default 'owner'   comment '可见范围（group/company/department/owner/explicit，字典 talent_visibility_type）',
    pool_desc             varchar(500)    default null               comment '人才池说明',
    member_count          int(11)         not null default 0         comment '成员数量（非负整数，冗余统计）',
    status                varchar(32)     not null default 'active'  comment '状态（active启用/archived归档等稳定编码）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (pool_id),
    unique key uk_hr_talent_pool_code (pool_code),
    key idx_hr_talent_pool_dept (owner_dept_id, status),
    key idx_hr_talent_pool_manager (manager_id, status),
    key idx_hr_talent_pool_visibility (visibility_type, status)
) engine=innodb comment = '人才池表';

-- ----------------------------
-- 8、人才池成员（设计 §9.2 hr_talent_pool_member）
--    pool_id + talent_id 唯一，保证同一人才在同一池只有一条有效关系
-- ----------------------------
drop table if exists hr_talent_pool_member;
create table hr_talent_pool_member (
    member_id             bigint(20)      not null                   comment '成员关系ID（主键）',
    pool_id               bigint(20)      not null                   comment '人才池ID',
    talent_id             bigint(20)      not null                   comment '人才主档ID',
    fit_level             varchar(32)     default null               comment '匹配度（high/medium/low等稳定编码）',
    recommended_job       varchar(200)    default null               comment '推荐岗位',
    join_reason           varchar(500)    default null               comment '加入原因',
    next_contact_time     datetime(3)     default null               comment '下次联系时间',
    member_status         varchar(32)     not null default 'active'  comment '成员状态（active/paused/removed/converted，字典 talent_pool_member_status，§10）',
    joined_by             bigint(20)      default null               comment '加入操作人用户ID',
    joined_time           datetime(3)     default null               comment '加入时间',
    removed_time          datetime(3)     default null               comment '移出时间（§8.15/§19.2）',
    removed_reason        varchar(500)    default null               comment '移出原因（§8.15/§19.2）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (member_id),
    unique key uk_hr_talent_pool_member (pool_id, talent_id),
    key idx_hr_talent_pool_member_status (pool_id, member_status),
    key idx_hr_talent_pool_member_talent (talent_id, member_status)
) engine=innodb comment = '人才池成员表';

-- ----------------------------
-- 9、人才标签（设计 §9.2 hr_talent_tag）
-- ----------------------------
drop table if exists hr_talent_tag;
create table hr_talent_tag (
    tag_id                bigint(20)      not null                   comment '标签ID（主键）',
    tag_code              varchar(64)     not null                   comment '标签编码（业务编号，唯一）',
    tag_name              varchar(100)    not null                   comment '标签名称',
    tag_category          varchar(32)     default null               comment '标签分类（skill/job_direction/industry/experience/language/certificate/other，字典 talent_tag_category，§10）',
    sensitive_flag        char(1)         not null default '0'       comment '是否敏感标签（0否 1是，敏感标签展示与授权单独控制）',
    sort_no               int(11)         not null default 0         comment '排序号',
    status                varchar(32)     not null default 'active'  comment '状态（active启用/disabled停用等稳定编码）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (tag_id),
    unique key uk_hr_talent_tag_code (tag_code),
    key idx_hr_talent_tag_category (tag_category, status)
) engine=innodb comment = '人才标签表';

-- ----------------------------
-- 10、人才标签关系（设计 §9.2 hr_talent_profile_tag）
--     talent_id + tag_id 唯一
-- ----------------------------
drop table if exists hr_talent_profile_tag;
create table hr_talent_profile_tag (
    rel_id                bigint(20)      not null                   comment '标签关系ID（主键）',
    talent_id             bigint(20)      not null                   comment '人才主档ID',
    tag_id                bigint(20)      not null                   comment '标签ID',
    source_type           varchar(32)     default null               comment '来源类型（manual人工录入/import导入/parse简历解析/system系统生成，字典 talent_source_type）',
    confirmed_by          bigint(20)      default null               comment '确认人用户ID（人工确认为准）',
    confirmed_time        datetime(3)     default null               comment '确认时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (rel_id),
    unique key uk_hr_talent_profile_tag (talent_id, tag_id),
    key idx_hr_talent_profile_tag_tag (tag_id, talent_id)
) engine=innodb comment = '人才标签关系表';

-- ----------------------------
-- 11、人才跟进（设计 §9.2 hr_talent_follow_up）
-- ----------------------------
drop table if exists hr_talent_follow_up;
create table hr_talent_follow_up (
    follow_id             bigint(20)      not null                   comment '跟进记录ID（主键）',
    talent_id             bigint(20)      not null                   comment '人才主档ID',
    contact_time          datetime(3)     default null               comment '联系时间',
    contact_method        varchar(32)     default null               comment '联系方式（phone电话/wechat微信/email邮件/onsite面谈等稳定编码）',
    contact_result        varchar(32)     default null               comment '联系结果（connected/no_answer/refused/interested/follow_up_later/invalid，字典 talent_contact_result，§10）',
    intent_change         varchar(500)    default null               comment '意向变化（§8.16）',
    summary               varchar(1000)   default null               comment '跟进摘要（禁止写入电话明文等敏感信息）',
    next_contact_time     datetime(3)     default null               comment '下次联系时间',
    follower_id           bigint(20)      default null               comment '跟进人用户ID',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (follow_id),
    key idx_hr_talent_follow_up (talent_id, contact_time),
    key idx_hr_talent_follow_up_user (follower_id, next_contact_time)
) engine=innodb comment = '人才跟进记录表';

-- ----------------------------
-- 12、人才共享授权（设计 §9.2 hr_talent_scope_grant / §21.14）
--     授权有效期内命中才可见；可见范围与敏感字段权限分离
-- ----------------------------
drop table if exists hr_talent_scope_grant;
create table hr_talent_scope_grant (
    grant_id              bigint(20)      not null                   comment '授权ID（主键）',
    talent_id             bigint(20)      not null                   comment '人才主档ID',
    grantee_type          varchar(32)     not null                   comment '被授权主体类型（user用户/dept部门/company公司/role角色等稳定编码）',
    grantee_id            bigint(20)      not null                   comment '被授权主体ID',
    permission_level      varchar(32)     not null                   comment '授权级别（summary/detail/attachment，字典 talent_permission_level）',
    valid_from            datetime(3)     default null               comment '授权有效期起（须早于有效期止）',
    valid_to              datetime(3)     default null               comment '授权有效期止（NULL 表示长期有效）',
    grant_reason          varchar(500)    default null               comment '授权事由',
    granted_by            bigint(20)      default null               comment '授权人用户ID',
    granted_time          datetime(3)     default null               comment '授权时间',
    revoke_flag           char(1)         not null default '0'       comment '是否已撤销（0否 1是，撤销后不再参与可见范围判定）',
    revoked_by            bigint(20)      default null               comment '撤销人用户ID',
    revoked_time          datetime(3)     default null               comment '撤销时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (grant_id),
    key idx_hr_talent_scope_grant (talent_id, grantee_type, grantee_id, valid_to),
    key idx_hr_talent_scope_grant_grantee (grantee_type, grantee_id, revoke_flag, valid_to)
) engine=innodb comment = '人才共享授权表';

-- ----------------------------
-- 13、疑似重复记录（设计 §9.2 hr_talent_duplicate_case）
-- ----------------------------
drop table if exists hr_talent_duplicate_case;
create table hr_talent_duplicate_case (
    case_id               bigint(20)      not null                   comment '重复案件ID（主键）',
    source_talent_id      bigint(20)      not null                   comment '主档A（待处理侧）人才ID',
    target_talent_id      bigint(20)      not null                   comment '主档B（比对侧）人才ID',
    match_level           varchar(32)     default null               comment '匹配级别（high/medium/low或规则编码）',
    match_reason          varchar(500)    default null               comment '匹配原因（命中规则说明，如电话哈希命中）',
    match_score           decimal(5,2)    default null               comment '匹配得分',
    status                varchar(32)     not null default 'pending' comment '处理状态（pending/merged/not_same/ignored/confirmed，字典 talent_duplicate_status，§10）',
    handled_by            bigint(20)      default null               comment '处理人用户ID',
    handled_time          datetime(3)     default null               comment '处理时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (case_id),
    key idx_hr_talent_duplicate_source (source_talent_id, status),
    key idx_hr_talent_duplicate_target (target_talent_id, status),
    key idx_hr_talent_duplicate_status (status, match_level)
) engine=innodb comment = '人才疑似重复记录表';

-- ----------------------------
-- 14、人才合并日志（设计 §9.2 hr_talent_merge_log）
-- ----------------------------
drop table if exists hr_talent_merge_log;
create table hr_talent_merge_log (
    merge_id              bigint(20)      not null                   comment '合并日志ID（主键）',
    keep_talent_id        bigint(20)      not null                   comment '保留（主）人才主档ID',
    merged_talent_id      bigint(20)      not null                   comment '被合并（从）人才主档ID',
    field_decision_json   json                                       comment '字段取值决策快照（结构化）',
    relation_count_json   json                                       comment '关系迁移数量快照（结构化）',
    merge_reason          varchar(500)    default null               comment '合并原因',
    operator_id           bigint(20)      default null               comment '操作人用户ID',
    operate_time          datetime(3)     default null               comment '操作时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (merge_id),
    key idx_hr_talent_merge_keep (keep_talent_id),
    key idx_hr_talent_merge_merged (merged_talent_id)
) engine=innodb comment = '人才合并日志表';

-- ----------------------------
-- 15、简历解析任务（设计 §9.2 hr_talent_parse_task）
-- ----------------------------
drop table if exists hr_talent_parse_task;
create table hr_talent_parse_task (
    task_id               bigint(20)      not null                   comment '解析任务ID（主键）',
    resume_id             bigint(20)      not null                   comment '简历版本ID',
    talent_id             bigint(20)      default null               comment '人才主档ID（简历已归属人才时冗余）',
    task_status           varchar(32)     not null default 'pending' comment '任务状态（pending/running/success/failed/cancelled等稳定编码）',
    parser_type           varchar(32)     default null               comment '解析器类型（internal内置/ocr/third_party等稳定编码）',
    parser_version        varchar(64)     default null               comment '解析器版本',
    retry_count           int(11)         not null default 0         comment '重试次数（非负整数）',
    started_time          datetime(3)     default null               comment '开始时间',
    finished_time         datetime(3)     default null               comment '完成时间',
    error_code            varchar(64)     default null               comment '错误编码（稳定编码，不存中文）',
    error_message         varchar(500)    default null               comment '错误说明（禁止写入简历正文）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (task_id),
    key idx_hr_talent_parse_task_resume (resume_id, task_status),
    key idx_hr_talent_parse_task_status (task_status, started_time)
) engine=innodb comment = '简历解析任务表';

-- ----------------------------
-- 16、简历候选解析结果（设计 §9.2 hr_talent_parse_result）
--     保存原始值/标准化值/置信度/位置/复核结论；低置信度默认不勾选
-- ----------------------------
drop table if exists hr_talent_parse_result;
create table hr_talent_parse_result (
    result_id             bigint(20)      not null                   comment '解析结果ID（主键）',
    task_id               bigint(20)      not null                   comment '解析任务ID',
    field_path            varchar(128)    default null               comment '字段路径（如 education[0].school_name）',
    raw_value             varchar(1000)   default null               comment '简历原始值（禁止写入日志）',
    normalized_value      varchar(1000)   default null               comment '标准化值（人工确认后可写入正式字段）',
    confidence            decimal(5,4)    default null               comment '置信度（0~1，低置信度默认不勾选）',
    source_location       varchar(128)    default null               comment '来源位置（页码或文本偏移）',
    review_status         varchar(32)     not null default 'pending' comment '复核状态（pending待复核/reviewing复核中/confirmed已确认/rejected已否决，字典 talent_resume_review_status）',
    reviewed_by           bigint(20)      default null               comment '复核人用户ID',
    reviewed_time         datetime(3)     default null               comment '复核时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (result_id),
    key idx_hr_talent_parse_result_task (task_id, field_path),
    key idx_hr_talent_parse_result_review (review_status)
) engine=innodb comment = '简历候选解析结果表';

-- ----------------------------
-- 17、人才导出任务（设计 §8.20 / §11 / §21.10）
--     P4 新增表，对应 P2-决策与缺口台账 §4 第 4 项结构性缺口。
--     记录筛选条件、字段清单、导出人、用途、记录数与过期时间；结果文件存私有 OSS，
--     只存对象标识不存长期公网地址，到期自动删除。
-- ----------------------------
drop table if exists hr_talent_export_task;
create table hr_talent_export_task (
    task_id               bigint(20)      not null                   comment '导出任务ID（主键）',
    task_no               varchar(64)     not null                   comment '任务编号（业务编号，唯一）',
    export_type           varchar(32)     not null default 'normal'  comment '导出类型（normal普通台账/sensitive敏感台账）',
    scope_json            json                                       comment '筛选条件快照（结构化，禁止写入敏感明文）',
    fields_json           json                                       comment '导出字段清单快照（结构化）',
    exported_by           bigint(20)      default null               comment '导出人用户ID',
    purpose               varchar(255)    default null               comment '导出用途/原因（敏感台账必填，审计追溯用）',
    record_count          int(11)         not null default 0         comment '导出记录数（非负整数）',
    oss_id                varchar(64)     default null               comment '结果文件OSS对象标识（不存长期公网地址）',
    file_name             varchar(255)    default null               comment '结果文件名',
    status                varchar(32)     not null default 'pending' comment '任务状态（pending/running/success/failed/expired等稳定编码）',
    failure_reason        varchar(500)    default null               comment '失败原因',
    expire_time           datetime(3)     default null               comment '结果文件过期时间（到期自动删除）',
    finished_time         datetime(3)     default null               comment '完成时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (task_id),
    unique key uk_hr_talent_export_task_no (task_no),
    key idx_hr_talent_export_task_operator (exported_by, create_time),
    key idx_hr_talent_export_task_expire (status, expire_time)
) engine=innodb comment = '人才导出任务表';

-- ----------------------------
-- 18、人才分组（设计 §8.15、§5.1 菜单「人才池与分组」）
--     P4 新增表，对应 P2-决策与缺口台账 §4 第 3 项结构性缺口。
--     公共分组：团队共同整理，由人才池管理员维护；个人收藏：用户个人快速访问，不改变人才数据权限。
-- ----------------------------
drop table if exists hr_talent_group;
create table hr_talent_group (
    group_id              bigint(20)      not null                   comment '分组ID（主键）',
    group_code            varchar(64)     default null               comment '分组编码（业务编码，可选）',
    group_name            varchar(128)    not null                   comment '分组名称',
    group_type            varchar(32)     not null default 'personal' comment '分组类型（public公共分组/personal个人收藏，字典 talent_group_type）',
    owner_dept_id         bigint(20)      default null               comment '归属部门ID（公共分组的维护部门）',
    owner_id              bigint(20)      default null               comment '负责人/收藏人用户ID',
    visibility_type       varchar(32)     not null default 'owner'   comment '可见范围（group/company/department/owner/explicit，字典 talent_visibility_type）',
    status                varchar(32)     not null default 'active'  comment '状态（active生效/inactive停用等稳定编码）',
    talent_count          int(11)         not null default 0         comment '成员数量冗余计数（非负整数）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (group_id),
    key idx_hr_talent_group_owner (owner_id, group_type, status),
    key idx_hr_talent_group_dept (owner_dept_id, group_type)
) engine=innodb comment = '人才分组表';

-- ----------------------------
-- 19、人才分组成员（设计 §8.15）
--     唯一索引 (group_id, talent_id) 防止重复加入；移出分组只结束关系，不删除人才主档。
-- ----------------------------
drop table if exists hr_talent_group_member;
create table hr_talent_group_member (
    member_id             bigint(20)      not null                   comment '分组成员ID（主键）',
    group_id              bigint(20)      not null                   comment '分组ID',
    talent_id             bigint(20)      not null                   comment '人才主档ID',
    added_by              bigint(20)      default null               comment '加入人用户ID',
    added_time            datetime(3)     default null               comment '加入时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (member_id),
    unique key uk_hr_talent_group_member (group_id, talent_id),
    key idx_hr_talent_group_member_talent (talent_id)
) engine=innodb comment = '人才分组成员表';
