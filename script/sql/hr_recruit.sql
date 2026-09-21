-- ----------------------------------------------------------------------------
-- 招聘与人才管理一体化系统 —— 招聘过程表 DDL（P1 地基）
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）
-- 表前缀：hr_recruit_（recruitment，招聘过程域）
-- 设计依据：《招聘与人才管理一体化系统详细设计方案（RuoYi-Vue-Plus v6.0.0）》
--           §9.2 表命名、§9.3 通用字段、§9.4 字段规则、§9.5 索引设计、§9.6 并发控制、
--           §21.13 数据库物理设计约定
-- 契约文件：docs/hr-talent/SPEC-P1-地基.md
--
-- 文件性质：
--   本文件为**初始化脚本**，包含 drop table if exists，会先清空再重建 21 张招聘过程表；
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
--   3. 关键聚合根（hr_recruit_demand、hr_recruit_job、hr_recruit_application）含 version 乐观锁字段。
--   4. 只做逻辑关联，不建数据库外键；完整性由事务、校验与巡检保证。
--   5. hr_recruit_sensitive_audit 为追加型审计表，不做物理删除（del_flag 保留以对齐通用字段）。
--   6. 敏感字段（电话、背调明细、渠道联系方式）一律保存密文；附件只保存 OSS 对象 ID，不存长期公网 URL。
--   7. 部分表在 §9.2「核心字段」之外，按 §8.x 功能设计补充了字段（§9.2 列名为「核心字段」，非全集）：
--      hr_recruit_job 增加 urgency / headhunter_flag / assistant_ids /
--      first_interviewer_id / second_interviewer_id / expect_arrival_date（依据 §8.3）；
--      hr_recruit_plan_item 增加 control_reason / last_refresh_time / urgency / standard_days /
--      import_batch_id / source_table / source_seq（依据 §8.2.1、§12.1、§7.1.2、§13.1、§19.4）；
--      hr_recruit_application 增加 contact_date / plan_arrival_date / offer_date / offer_result /
--      no_arrival_reason（§8.4、§8.7、§7.2、§14）；hr_recruit_stage_log 增加 next_follow_time（§8.5）；
--      hr_recruit_interviewer 增加 feedback（§8.6、§7.3）；hr_recruit_background 增加
--      check_items / waive_reason（§8.7、§7.4）；hr_recruit_import_error 增加 severity（§8.11）。
--      人才主数据表按 §8.12~§8.16 补充 16 列；完整台账见 docs/hr-talent/P2-决策与缺口台账.md。
--      招聘需求的暂停/关闭原因通过 hr_recruit_demand_change.reason 记录，需求表本身不另设原因列。
--   8. 按 §7.1.5 / §21.15 新增 hr_recruit_plan_item_status_log（计划任务状态变更日志，追加型只插入），
--      用于记录状态刷新的触发事件、原状态、新状态与刷新时间（缺口台账 §4 第 1 项，经用户批准补表）。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、招聘需求来源（设计 §9.2 hr_recruit_demand）
-- ----------------------------
drop table if exists hr_recruit_demand;
create table hr_recruit_demand (
    demand_id             bigint(20)      not null                   comment '需求ID（主键）',
    demand_no             varchar(64)     not null                   comment '需求编号（业务编号，唯一，数据库主键不对外展示）',
    demand_title          varchar(200)    default null               comment '需求标题',
    company_dept_id       bigint(20)      not null                   comment '公司（平台部门）ID',
    company_name          varchar(100)    default null               comment '公司名称快照（历史报表用）',
    use_dept_id           bigint(20)      default null               comment '用工部门ID',
    use_dept_name         varchar(100)    default null               comment '用工部门名称快照',
    recruiter_id          bigint(20)      default null               comment '招聘负责人用户ID',
    apply_date            date            default null               comment '需求申请日期',
    expect_arrival_date   date            default null               comment '期望到岗日期',
    demand_count          int(11)         not null default 0         comment '需求人数（非负整数）',
    hired_count           int(11)         not null default 0         comment '已到岗人数（非负整数）',
    urgency               varchar(32)     not null default 'normal'  comment '紧急程度（normal一般 urgent紧急 very_urgent非常紧急，字典 recruit_urgency）',
    recruit_mode          varchar(32)     default null               comment '招聘形式（internal/social/campus/headhunter/referral/other，字典 recruit_mode）',
    job_name              varchar(200)    default null               comment '需求岗位名称',
    job_level             varchar(32)     default null               comment '岗位职级（字典编码）',
    work_city             varchar(64)     default null               comment '工作城市',
    demand_reason         text                                       comment '需求原因说明',
    status                varchar(32)     not null default 'draft'   comment '需求状态（draft/submitted/recruiting/paused/completed/closed，字典 recruit_demand_status）',
    submitted_by          bigint(20)      default null               comment '提交人用户ID',
    submitted_time        datetime(3)     default null               comment '提交时间',
    confirmed_by          bigint(20)      default null               comment '确认人用户ID',
    confirmed_time        datetime(3)     default null               comment '确认时间',
    closed_by             bigint(20)      default null               comment '关闭人用户ID',
    closed_time           datetime(3)     default null               comment '关闭时间',
    version               int(11)         not null default 0         comment '乐观锁版本号（设计 §9.6）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (demand_id),
    unique key uk_hr_recruit_demand_no (demand_no),
    key idx_hr_recruit_demand_company (company_dept_id, status),
    key idx_hr_recruit_demand_use_dept (use_dept_id, status),
    key idx_hr_recruit_demand_recruiter (recruiter_id, status),
    key idx_hr_recruit_demand_apply_date (apply_date)
) engine=innodb comment = '招聘需求来源表';

-- ----------------------------
-- 2、需求变更历史（设计 §9.2 hr_recruit_demand_change）
-- ----------------------------
drop table if exists hr_recruit_demand_change;
create table hr_recruit_demand_change (
    change_id             bigint(20)      not null                   comment '变更记录ID（主键）',
    demand_id             bigint(20)      not null                   comment '招聘需求ID',
    change_type           varchar(32)     not null                   comment '变更类型（create/update/submit/pause/close等稳定编码）',
    before_json           json                                       comment '变更前快照（结构化）',
    after_json            json                                       comment '变更后快照（结构化）',
    reason                varchar(500)    default null               comment '变更原因',
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
    key idx_hr_recruit_demand_change (demand_id, operate_time)
) engine=innodb comment = '招聘需求变更历史表';

-- ----------------------------
-- 3、公司月度计划表头（设计 §9.2 hr_recruit_plan）
--    一个公司每月只有一张计划表头（company_dept_id + plan_month 唯一）
-- ----------------------------
drop table if exists hr_recruit_plan;
create table hr_recruit_plan (
    plan_id               bigint(20)      not null                   comment '计划ID（主键）',
    plan_no               varchar(64)     not null                   comment '计划编号（业务编号，唯一）',
    company_dept_id       bigint(20)      not null                   comment '公司（平台部门）ID',
    company_name          varchar(100)    default null               comment '公司名称快照',
    plan_month            char(7)         not null                   comment '计划月份（yyyy-MM）',
    status                varchar(32)     not null default 'draft'   comment '计划状态（draft/executing/closed，字典 recruit_plan_status）',
    generated_flag        char(1)         not null default '0'       comment '是否已生成计划任务（0否 1是）',
    generated_time        datetime(3)     default null               comment '生成时间',
    confirmed_by          bigint(20)      default null               comment '确认人用户ID',
    confirmed_time        datetime(3)     default null               comment '确认时间',
    closed_by             bigint(20)      default null               comment '关闭人用户ID',
    closed_time           datetime(3)     default null               comment '关闭时间',
    total_plan_qty        int(11)         not null default 0         comment '计划总人数（非负整数）',
    total_credited_qty    int(11)         not null default 0         comment '累计计入到岗人数（非负整数）',
    total_remaining_qty   int(11)         not null default 0         comment '累计剩余人数（非负整数）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (plan_id),
    unique key uk_hr_recruit_plan_no (plan_no),
    unique key uk_hr_recruit_plan_company_month (company_dept_id, plan_month),
    key idx_hr_recruit_plan_status (status, plan_month)
) engine=innodb comment = '公司月度招聘计划表头';

-- ----------------------------
-- 4、月度招聘计划任务（设计 §9.2 hr_recruit_plan_item）
--    每次新增需求和每次自动结转都生成独立记录，即使公司/部门/岗位相同也不合并；
--    刻意不对「公司 + 岗位 + 月份」建唯一约束（设计 §9.5 明确要求）。
-- ----------------------------
drop table if exists hr_recruit_plan_item;
create table hr_recruit_plan_item (
    item_id                 bigint(20)    not null                   comment '计划任务ID（主键）',
    item_no                 varchar(64)   not null                   comment '计划任务编号（业务编号，唯一）',
    plan_id                 bigint(20)    not null                   comment '所属月度计划表头ID',
    demand_id               bigint(20)    default null               comment '来源招聘需求ID',
    job_id                  bigint(20)    default null               comment '关联岗位执行项ID',
    import_batch_id         bigint(20)    default null               comment '来源导入批次ID（导入生成时记录，§13.1/§19.4 可追溯）',
    source_table            varchar(100)  default null               comment '来源表名（历史迁移来源，§13.1）',
    source_seq              varchar(64)   default null               comment '来源序号（原表行号，用于区分同公司/同部门/同岗位的重复行，§13.1/§13.3）',
    company_dept_id         bigint(20)    not null                   comment '公司（平台部门）ID',
    company_name            varchar(100)  default null               comment '公司名称快照',
    use_dept_id             bigint(20)    default null               comment '用工部门ID',
    use_dept_name           varchar(100)  default null               comment '用工部门名称快照',
    job_name                varchar(200)  default null               comment '岗位名称快照',
    plan_month              char(7)       not null                   comment '计划月份（yyyy-MM）',
    source_type             varchar(32)   not null default 'new'     comment '来源类型（new当月新增 carryover上月结转，字典 recruit_plan_source_type）',
    plan_qty                int(11)       not null default 0         comment '计划人数（非负整数；新增取确认人数，结转取原任务未完成人数）',
    credited_arrival_qty    int(11)       not null default 0         comment '已计入到岗人数（非负整数，一个到岗结果只计入一条有效任务）',
    remaining_qty           int(11)       not null default 0         comment '剩余人数（非负整数）',
    control_status          varchar(32)   not null default 'normal'  comment '人工控制状态（normal/paused/cancelled，字典 recruit_plan_control_status）',
    control_reason          varchar(500)  default null               comment '人工暂停/取消原因（§12.1 要求与人工状态同时展示）',
    execution_status        varchar(32)   not null default 'pending' comment '自动执行阶段（pending/recruiting/interviewing/offer/pending_arrival，字典 recruit_plan_execution_status）',
    completion_status       varchar(32)   not null default 'unfinished' comment '完成与结转状态（unfinished/partial_completed/completed/rolled_over，字典 recruit_plan_completion_status）',
    last_refresh_time       datetime(3)   default null               comment '最后状态刷新时间（§8.2.1、§12.1；供状态校准比对）',
    carryover_enabled       char(1)       not null default '1'       comment '是否允许自动结转（0否 1是）',
    previous_plan_item_id   bigint(20)    default null               comment '前置（来源）计划任务ID，构成跨月结转链',
    root_plan_item_id       bigint(20)    default null               comment '根计划任务ID（结转链起点）',
    carryover_batch_id      bigint(20)    default null               comment '结转批次ID（关联 hr_recruit_plan_rollover.batch_id）',
    carryover_time          datetime(3)   default null               comment '结转生成时间',
    owner_id                bigint(20)    default null               comment '任务负责人用户ID',
    urgency                 varchar(32)   not null default 'normal'  comment '紧急程度（结转时自原任务复制，字典 recruit_urgency，§7.1.2）',
    standard_days           int(11)       default null               comment '招聘期限标准天数（结转时自原任务复制，§7.1.2）',
    del_flag                char(1)       default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept             bigint(20)    default null               comment '创建部门',
    create_by               bigint(20)    default null               comment '创建者',
    create_time             datetime                                 comment '创建时间',
    update_by               bigint(20)    default null               comment '更新者',
    update_time             datetime                                 comment '更新时间',
    remark                  varchar(500)  default null               comment '备注',
    primary key (item_id),
    unique key uk_hr_recruit_plan_item_no (item_no),
    key idx_hr_recruit_plan_item_exec (plan_id, execution_status, completion_status),
    key idx_hr_recruit_plan_item_job_plan (job_id, plan_id),
    key idx_hr_recruit_plan_item_demand (demand_id),
    key idx_hr_recruit_plan_item_root (root_plan_item_id),
    key idx_hr_recruit_plan_item_batch (carryover_batch_id),
    -- 注意：以下为普通查询索引（非唯一），设计 §9.5 禁止对「公司 + 岗位 + 月份」建立唯一约束
    key idx_hr_recruit_plan_item_month (company_dept_id, plan_month)
) engine=innodb comment = '月度招聘计划任务表';

-- ----------------------------
-- 5、月度结转记录（设计 §9.2 hr_recruit_plan_rollover）
--    source_item_id + target_month 唯一，保证结转重试幂等
--    补充：batch_id 为对齐 hr_recruit_plan_item.carryover_batch_id 而新增。
--          设计文档 §9.2 中 plan_item 记 carryover_batch_id(bigint)、rollover 只记
--          batch_no(varchar)，两表批次键口径不一致且无批次表；此处补 batch_id 作为关联键，
--          batch_no 保留为可读编号。经评审确认后落地。
-- ----------------------------
drop table if exists hr_recruit_plan_rollover;
create table hr_recruit_plan_rollover (
    rollover_id           bigint(20)      not null                   comment '结转记录ID（主键）',
    source_item_id        bigint(20)      not null                   comment '来源（上月）计划任务ID',
    target_item_id        bigint(20)      default null               comment '目标（本月）计划任务ID',
    source_month          char(7)         default null               comment '来源月份（yyyy-MM）',
    target_month          char(7)         not null                   comment '目标月份（yyyy-MM）',
    carryover_qty         int(11)         not null default 0         comment '结转人数（非负整数）',
    batch_id              bigint(20)      default null               comment '结转批次ID（一次结转执行一个批次；与 hr_recruit_plan_item.carryover_batch_id 对应）',
    batch_no              varchar(64)     default null               comment '结转批次号（批次的可读编号，同日/同月一批，便于整体重试）',
    execute_time          datetime(3)     default null               comment '执行时间',
    result                varchar(32)     default null               comment '执行结果（success/failed/skipped等稳定编码）',
    failure_reason        varchar(500)    default null               comment '失败原因（单条失败可安全重试）',
    retry_count           int(11)         not null default 0         comment '重试次数（非负整数）',
    operator_id           bigint(20)      default null               comment '操作人用户ID',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (rollover_id),
    unique key uk_hr_recruit_plan_rollover (source_item_id, target_month),
    key idx_hr_recruit_plan_rollover_batch_id (batch_id),
    key idx_hr_recruit_plan_rollover_batch (batch_no),
    key idx_hr_recruit_plan_rollover_target (target_item_id)
) engine=innodb comment = '月度招聘计划结转记录表';

-- ----------------------------
-- 6、计划任务与应聘记录关联（设计 §9.2 hr_recruit_plan_application_rel）
--    一个到岗结果只能计入一条当前有效月度任务，避免跨月重复计算
-- ----------------------------
drop table if exists hr_recruit_plan_application_rel;
create table hr_recruit_plan_application_rel (
    rel_id                bigint(20)      not null                   comment '关联ID（主键）',
    plan_item_id          bigint(20)      not null                   comment '月度计划任务ID',
    application_id        bigint(20)      not null                   comment '应聘记录ID',
    relation_type         varchar(32)     default null               comment '关联类型（plan计入/arrival到岗/source来源等稳定编码）',
    effective_start       datetime(3)     default null               comment '关联生效开始时间',
    effective_end         datetime(3)     default null               comment '关联生效结束时间（NULL 表示当前有效）',
    credited_flag         char(1)         not null default '0'       comment '是否已计入到岗统计（0否 1是）',
    credited_time         datetime(3)     default null               comment '计入到岗统计时间',
    operator_id           bigint(20)      default null               comment '操作人用户ID',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (rel_id),
    key idx_hr_recruit_plan_app_rel (plan_item_id, application_id, effective_end),
    key idx_hr_recruit_plan_app_rel_app (application_id, credited_flag)
) engine=innodb comment = '月度计划任务与应聘记录关联表';

-- ----------------------------
-- 7、岗位执行项（设计 §9.2 hr_recruit_job）
-- ----------------------------
drop table if exists hr_recruit_job;
create table hr_recruit_job (
    job_id                bigint(20)      not null                   comment '岗位执行项ID（主键）',
    job_no                varchar(64)     not null                   comment '岗位编号（业务编号，唯一）',
    demand_id             bigint(20)      default null               comment '来源招聘需求ID',
    plan_item_id          bigint(20)      default null               comment '关联月度计划任务ID（计划任务 N ── 1 岗位）',
    job_name              varchar(200)    not null                   comment '岗位名称',
    company_dept_id       bigint(20)      default null               comment '公司（平台部门）ID',
    company_name          varchar(100)    default null               comment '公司名称快照',
    use_dept_id           bigint(20)      default null               comment '用工部门ID',
    use_dept_name         varchar(100)    default null               comment '用工部门名称快照',
    job_level             varchar(32)     default null               comment '岗位职级（字典编码）',
    work_city             varchar(64)     default null               comment '工作城市',
    responsibility        text                                       comment '岗位职责',
    qualification         text                                       comment '任职要求',
    salary_min            decimal(12,2)   default null               comment '薪资低值（金额，低值不得大于高值）',
    salary_max            decimal(12,2)   default null               comment '薪资高值',
    salary_period         varchar(32)     default null               comment '薪资周期（month/year/day等稳定编码）',
    recruit_mode          varchar(32)     default null               comment '招聘形式（internal/social/campus/headhunter/referral/other，字典 recruit_mode）',
    urgency               varchar(32)     not null default 'normal'  comment '紧急程度（normal/urgent/very_urgent，字典 recruit_urgency）',
    headhunter_flag       char(1)         not null default '0'       comment '是否需要猎头（0否 1是）',
    assistant_ids         varchar(255)    default null               comment '协助人用户ID，多个以英文逗号分隔',
    first_interviewer_id  bigint(20)      default null               comment '一面面试官用户ID（岗位计划默认值，实际参与以 hr_recruit_interviewer 为准）',
    second_interviewer_id bigint(20)      default null               comment '二面面试官用户ID（岗位计划默认值，实际参与以 hr_recruit_interviewer 为准）',
    standard_days         int(11)         default null               comment '招聘期限标准天数（来自招聘期限标准）',
    expect_arrival_date   date            default null               comment '预计到岗日期',
    recruit_count         int(11)         not null default 0         comment '岗位招聘人数（非负整数）',
    owner_id              bigint(20)      default null               comment '岗位负责（招聘负责人）用户ID',
    publish_date          date            default null               comment '发布日期',
    close_date            date            default null               comment '关闭日期',
    status                varchar(32)     not null default 'draft'   comment '岗位状态（draft/open/paused/closed等稳定编码）',
    version               int(11)         not null default 0         comment '乐观锁版本号（设计 §9.6）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (job_id),
    unique key uk_hr_recruit_job_no (job_no),
    key idx_hr_recruit_job_demand (demand_id),
    key idx_hr_recruit_job_plan_item (plan_item_id),
    key idx_hr_recruit_job_owner (owner_id, status),
    key idx_hr_recruit_job_company (company_dept_id, status)
) engine=innodb comment = '招聘岗位执行项表';

-- ----------------------------
-- 8、应聘记录（设计 §9.2 hr_recruit_application）
-- ----------------------------
drop table if exists hr_recruit_application;
create table hr_recruit_application (
    application_id        bigint(20)      not null                   comment '应聘记录ID（主键）',
    application_no        varchar(64)     not null                   comment '应聘编号（业务编号，唯一）',
    talent_id             bigint(20)      not null                   comment '人才主档ID',
    job_id                bigint(20)      not null                   comment '岗位执行项ID',
    resume_id             bigint(20)      default null               comment '本次应聘使用的简历版本ID（不随人才最新简历变更）',
    current_stage         varchar(32)     not null default 'new'     comment '当前阶段（new/resume_review/invite/first_interview/second_interview/background/offer/pending_arrival/arrived，字典 recruit_candidate_stage）',
    current_status        varchar(32)     not null default 'processing' comment '应聘结果（processing/passed/rejected/withdrawn/paused/talent_pool，字典 recruit_application_result）',
    source_channel_id     bigint(20)      default null               comment '来源渠道ID',
    source_type           varchar(32)     default null               comment '来源方式（channel/referral/import等稳定编码）',
    expected_salary_min   decimal(12,2)   default null               comment '期望薪资低值（金额）',
    expected_salary_max   decimal(12,2)   default null               comment '期望薪资高值（金额）',
    recruiter_id          bigint(20)      default null               comment '招聘负责人用户ID',
    contact_date          date            default null               comment '联系日期（§14 跟进联系留痕）',
    next_follow_time      datetime(3)     default null               comment '下次跟进时间',
    plan_arrival_date     date            default null               comment '计划报到日期（录用后约定报到日期，§8.4/附录A）',
    offer_date            date            default null               comment '录用邀约日期（§8.7 录用邀约环节）',
    offer_result          varchar(32)     default null               comment '邀约结果（accepted/rejected等稳定编码，§7.2）',
    arrival_date          date            default null               comment '实际到岗日期',
    no_arrival_reason     varchar(500)    default null               comment '未报到原因（§8.4）',
    apply_time            datetime(3)     default null               comment '应聘（投递）时间',
    stage_enter_time      datetime(3)     default null               comment '进入当前阶段时间',
    reject_reason         varchar(500)    default null               comment '淘汰/撤回原因',
    version               int(11)         not null default 0         comment '乐观锁版本号（设计 §9.6，阶段流转必须校验）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (application_id),
    unique key uk_hr_recruit_application_no (application_no),
    key idx_hr_recruit_application_talent (talent_id),
    key idx_hr_recruit_application_job (job_id),
    key idx_hr_recruit_application_stage (current_stage),
    key idx_hr_recruit_application_recruiter (recruiter_id),
    key idx_hr_recruit_application_follow (next_follow_time),
    key idx_hr_recruit_application_channel (source_channel_id)
) engine=innodb comment = '应聘记录表';

-- ----------------------------
-- 9、阶段历史（设计 §9.2 hr_recruit_stage_log）
-- ----------------------------
drop table if exists hr_recruit_stage_log;
create table hr_recruit_stage_log (
    log_id                bigint(20)      not null                   comment '阶段历史ID（主键）',
    application_id        bigint(20)      not null                   comment '应聘记录ID',
    from_stage            varchar(32)     default null               comment '原阶段（字典 recruit_candidate_stage）',
    to_stage              varchar(32)     default null               comment '目标阶段（字典 recruit_candidate_stage）',
    action_type           varchar(32)     default null               comment '操作动作（move/reject/withdraw/pause/arrive等稳定编码）',
    result                varchar(32)     default null               comment '阶段结果（字典 recruit_application_result）',
    reason_code           varchar(64)     default null               comment '原因编码（字典编码，不存中文）',
    `comment`             varchar(1000)   default null               comment '阶段说明',
    next_follow_time      datetime(3)     default null               comment '本次跟进的下一步日期（逐次留痕，§8.5）',
    operator_id           bigint(20)      default null               comment '操作人用户ID',
    operate_time          datetime(3)     default null               comment '操作时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (log_id),
    key idx_hr_recruit_stage_log (application_id, operate_time)
) engine=innodb comment = '应聘阶段历史表';

-- ----------------------------
-- 10、面试记录（设计 §9.2 hr_recruit_interview）
-- ----------------------------
drop table if exists hr_recruit_interview;
create table hr_recruit_interview (
    interview_id          bigint(20)      not null                   comment '面试记录ID（主键）',
    application_id        bigint(20)      not null                   comment '应聘记录ID',
    round_no              int(11)         not null default 1         comment '面试轮次（非负整数，从1开始）',
    schedule_time         datetime(3)     default null               comment '计划面试时间',
    end_time              datetime(3)     default null               comment '实际结束时间',
    method                varchar(32)     default null               comment '面试方式（onsite现场/video视频/phone电话，字典编码）',
    location              varchar(255)    default null               comment '面试地点或线上链接',
    status                varchar(32)     not null default 'pending' comment '面试状态（pending/scheduled/finished/cancelled等稳定编码）',
    score                 decimal(5,2)    default null               comment '面试评分',
    result                varchar(32)     default null               comment '面试结果（pending/pass/fail/reserve/absent，字典 recruit_interview_result）',
    feedback              text                                       comment '面试意见',
    feedback_time         datetime(3)     default null               comment '反馈时间',
    cancel_reason         varchar(500)    default null               comment '取消原因',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (interview_id),
    key idx_hr_recruit_interview_schedule (schedule_time, status),
    key idx_hr_recruit_interview_application (application_id, round_no)
) engine=innodb comment = '面试记录表';

-- ----------------------------
-- 11、面试参与人（设计 §9.2 hr_recruit_interviewer）
-- ----------------------------
drop table if exists hr_recruit_interviewer;
create table hr_recruit_interviewer (
    interviewer_id        bigint(20)      not null                   comment '面试参与人ID（主键）',
    interview_id          bigint(20)      not null                   comment '面试记录ID',
    user_id               bigint(20)      not null                   comment '面试官用户ID',
    user_name             varchar(64)     default null               comment '面试官姓名快照',
    interviewer_role      varchar(32)     default null               comment '面试官角色（lead主面/assist协同/hr/tech等稳定编码）',
    feedback_status       varchar(32)     not null default 'pending' comment '反馈状态（pending/submitted/waived等稳定编码）',
    feedback_time         datetime(3)     default null               comment '反馈时间',
    score                 decimal(5,2)    default null               comment '面试官评分',
    feedback              text                                       comment '面试官个人意见/结论（§8.6/§7.3/§6）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (interviewer_id),
    key idx_hr_recruit_interviewer_itv (interview_id),
    key idx_hr_recruit_interviewer_user (user_id, feedback_status)
) engine=innodb comment = '面试参与人表';

-- ----------------------------
-- 12、背调记录（设计 §9.2 hr_recruit_background）
--     背调明细为密文，列表与日志一律脱敏
-- ----------------------------
drop table if exists hr_recruit_background;
create table hr_recruit_background (
    background_id         bigint(20)      not null                   comment '背调记录ID（主键）',
    application_id        bigint(20)      not null                   comment '应聘记录ID（一条应聘记录仅一条当前有效背调）',
    authorized_flag       char(1)         not null default '0'       comment '是否已获得候选人授权（0否 1是）',
    authorize_time        datetime(3)     default null               comment '授权时间',
    checker_id            bigint(20)      default null               comment '背调负责人用户ID',
    check_time            datetime(3)     default null               comment '背调完成时间',
    check_start_date      date            default null               comment '背调开始日期',
    check_end_date        date            default null               comment '背调结束日期',
    result                varchar(32)     default null               comment '背调结果（pending/pass/fail/waived，字典 recruit_background_result）',
    failure_reason_code   varchar(64)     default null               comment '不通过原因编码（字典编码，不存中文）',
    check_items           varchar(500)    default null               comment '背调核查项清单（§8.7/§7.4）',
    detail_cipher         text                                       comment '背调明细密文（禁止截断，禁止日志输出）',
    waive_reason          varchar(500)    default null               comment '免背调授权原因（result=waived 时填写，§7.2）',
    status                varchar(32)     not null default 'draft'   comment '背调状态（draft/checking/finished/cancelled等稳定编码）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (background_id),
    key idx_hr_recruit_background_app (application_id, status),
    key idx_hr_recruit_background_checker (checker_id)
) engine=innodb comment = '招聘背调记录表';

-- ----------------------------
-- 13、业务附件（设计 §9.2 hr_recruit_attachment）
--     只保存 OSS 对象 ID，不保存长期公网 URL
-- ----------------------------
drop table if exists hr_recruit_attachment;
create table hr_recruit_attachment (
    attachment_id         bigint(20)      not null                   comment '附件ID（主键）',
    biz_type              varchar(32)     not null                   comment '业务类型（application/interview/background/offer等稳定编码）',
    biz_id                bigint(20)      not null                   comment '业务对象ID',
    file_type             varchar(32)     default null               comment '附件类型（resume/portfolio/interview/background/offer/other，字典 recruit_attachment_type）',
    oss_id                varchar(64)     not null                   comment 'OSS 对象ID（受控访问，不存长期公网URL）',
    original_name         varchar(255)    default null               comment '原始文件名',
    file_suffix           varchar(32)     default null               comment '文件后缀',
    file_size             bigint(20)      default null               comment '文件大小（字节）',
    file_hash             char(64)        default null               comment '文件哈希（SHA-256 十六进制，用于重复文件提示，非唯一）',
    version_no            int(11)         not null default 1         comment '附件版本号（非负整数，新文件新增版本不覆盖）',
    current_flag          char(1)         not null default '1'       comment '是否当前版本（0否 1是）',
    security_level        varchar(32)     default null               comment '安全级别（字典 recruit_data_level）',
    uploaded_by           bigint(20)      default null               comment '上传人用户ID',
    uploaded_time         datetime(3)     default null               comment '上传时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (attachment_id),
    key idx_hr_recruit_attachment_biz (biz_type, biz_id, current_flag),
    key idx_hr_recruit_attachment_hash (file_hash),
    key idx_hr_recruit_attachment_ver (biz_type, biz_id, version_no)
) engine=innodb comment = '招聘业务附件表';

-- ----------------------------
-- 14、招聘期限标准（设计 §9.2 hr_recruit_standard）
-- ----------------------------
drop table if exists hr_recruit_standard;
create table hr_recruit_standard (
    standard_id           bigint(20)      not null                   comment '标准ID（主键）',
    job_name              varchar(200)    not null                   comment '适用岗位名称',
    company_dept_id       bigint(20)      default null               comment '公司（平台部门）ID（NULL 表示集团通用）',
    company_name          varchar(100)    default null               comment '公司名称快照',
    standard_days         int(11)         not null                   comment '招聘期限标准天数（非负整数）',
    effective_date        date            default null               comment '生效日期',
    expiry_date           date            default null               comment '失效日期',
    status                varchar(32)     not null default 'active'  comment '状态（active生效/inactive停用等稳定编码）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (standard_id),
    key idx_hr_recruit_standard_match (company_dept_id, job_name, status),
    key idx_hr_recruit_standard_effective (effective_date, expiry_date)
) engine=innodb comment = '招聘期限标准表';

-- ----------------------------
-- 15、渠道档案（设计 §9.2 hr_recruit_channel）
-- ----------------------------
drop table if exists hr_recruit_channel;
create table hr_recruit_channel (
    channel_id            bigint(20)      not null                   comment '渠道ID（主键）',
    channel_code          varchar(64)     not null                   comment '渠道编码（业务编号，唯一）',
    channel_name          varchar(100)    not null                   comment '渠道名称',
    channel_type          varchar(32)     default null               comment '渠道类型（job_site/headhunter/referral/social_media/campus/other，字典 recruit_channel_type）',
    contact_person        varchar(64)     default null               comment '渠道联系人',
    contact_info_cipher   varchar(512)    default null               comment '渠道联系方式密文（禁止存明文）',
    fee_rule              varchar(500)    default null               comment '费用规则说明',
    cooperation_start     date            default null               comment '合作开始日期',
    cooperation_end       date            default null               comment '合作结束日期',
    status                varchar(32)     not null default 'active'  comment '状态（active合作中/paused暂停/closed终止等稳定编码）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (channel_id),
    unique key uk_hr_recruit_channel_code (channel_code),
    key idx_hr_recruit_channel_type (channel_type, status)
) engine=innodb comment = '招聘渠道档案表';

-- ----------------------------
-- 16、渠道调查（设计 §9.2 hr_recruit_channel_survey）
-- ----------------------------
drop table if exists hr_recruit_channel_survey;
create table hr_recruit_channel_survey (
    survey_id             bigint(20)      not null                   comment '调查记录ID（主键）',
    channel_id            bigint(20)      not null                   comment '渠道ID',
    company_name          varchar(200)    default null               comment '被调查企业名称',
    coverage_flag         char(1)         not null default '0'       comment '是否覆盖目标同行（0否 1是）',
    survey_date           date            default null               comment '调查日期',
    source                varchar(64)     default null               comment '信息来源（稳定编码或来源说明）',
    survey_conclusion     text                                       comment '调查结论',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (survey_id),
    key idx_hr_recruit_channel_survey (channel_id, survey_date)
) engine=innodb comment = '招聘渠道调查表';

-- ----------------------------
-- 17、同行企业（设计 §9.2 hr_recruit_peer_company）
-- ----------------------------
drop table if exists hr_recruit_peer_company;
create table hr_recruit_peer_company (
    peer_id               bigint(20)      not null                   comment '同行企业ID（主键）',
    company_name          varchar(200)    not null                   comment '企业名称',
    main_product          varchar(500)    default null               comment '主营产品/业务',
    company_scale         varchar(64)     default null               comment '企业规模（字典编码或区间说明）',
    region                varchar(64)     default null               comment '所在区域',
    address               varchar(255)    default null               comment '详细地址',
    industry              varchar(64)     default null               comment '所属行业',
    contact_info_cipher   varchar(512)    default null               comment '联系方式密文（禁止存明文）',
    source                varchar(64)     default null               comment '信息来源（稳定编码或来源说明）',
    status                varchar(32)     not null default 'active'  comment '状态（active有效/inactive停用等稳定编码）',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (peer_id),
    key idx_hr_recruit_peer_company_name (company_name),
    key idx_hr_recruit_peer_region (region, status)
) engine=innodb comment = '同行企业信息表';

-- ----------------------------
-- 18、导入批次（设计 §9.2 hr_recruit_import_batch）
-- ----------------------------
drop table if exists hr_recruit_import_batch;
create table hr_recruit_import_batch (
    batch_id              bigint(20)      not null                   comment '导入批次ID（主键）',
    batch_no              varchar(64)     not null                   comment '批次编号（业务编号，唯一）',
    import_type           varchar(32)     not null                   comment '导入类型（demand/talent/resume/peer等稳定编码）',
    source_file_oss_id    varchar(64)     default null               comment '源文件 OSS 对象ID（不存长期公网URL）',
    source_file_name      varchar(255)    default null               comment '源文件名称',
    total_count           int(11)         not null default 0         comment '总记录数（非负整数）',
    success_count         int(11)         not null default 0         comment '成功数（非负整数）',
    failure_count         int(11)         not null default 0         comment '失败数（非负整数）',
    status                varchar(32)     not null default 'pending' comment '批次状态（pending/parsing/confirming/success/partial_failed/failed/cancelled等稳定编码）',
    started_time          datetime(3)     default null               comment '开始时间',
    finished_time         datetime(3)     default null               comment '结束时间',
    operator_id           bigint(20)      default null               comment '操作人用户ID',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (batch_id),
    unique key uk_hr_recruit_import_batch_no (batch_no),
    key idx_hr_recruit_import_batch_type (import_type, status),
    key idx_hr_recruit_import_batch_operator (operator_id)
) engine=innodb comment = '招聘数据导入批次表';

-- ----------------------------
-- 19、导入错误（设计 §9.2 hr_recruit_import_error）
-- ----------------------------
drop table if exists hr_recruit_import_error;
create table hr_recruit_import_error (
    error_id              bigint(20)      not null                   comment '导入错误ID（主键）',
    batch_id              bigint(20)      not null                   comment '导入批次ID',
    sheet_name            varchar(100)    default null               comment '工作表名称',
    row_no                int(11)         default null               comment '行号（从1开始）',
    field_name            varchar(64)     default null               comment '出错字段名',
    raw_value             varchar(500)    default null               comment '原始值（仅存必要片段，避免整行敏感数据）',
    error_code            varchar(64)     default null               comment '错误编码（稳定编码，不存中文）',
    severity              varchar(32)     default null               comment '严重级别（error阻断/warning提示，§8.11）',
    error_message         varchar(500)    default null               comment '错误说明',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (error_id),
    key idx_hr_recruit_import_error_batch (batch_id, row_no)
) engine=innodb comment = '招聘数据导入错误表';

-- ----------------------------
-- 20、敏感操作审计（设计 §9.2 hr_recruit_sensitive_audit）
--     追加型审计表：只插入不物理删除，del_flag 保留以对齐通用字段
-- ----------------------------
drop table if exists hr_recruit_sensitive_audit;
create table hr_recruit_sensitive_audit (
    audit_id              bigint(20)      not null                   comment '审计ID（主键）',
    event_type            varchar(32)     not null                   comment '事件类型（phone_view/attachment_download/background_view/export等稳定编码）',
    biz_type              varchar(32)     default null               comment '业务类型（demand/talent/application/interview/background等稳定编码）',
    biz_id                bigint(20)      default null               comment '业务对象ID',
    operator_id           bigint(20)      default null               comment '操作人用户ID',
    operator_name         varchar(64)     default null               comment '操作人姓名快照',
    purpose               varchar(255)    default null               comment '操作事由/用途（授权与审计追溯用）',
    result                varchar(32)     default null               comment '操作结果（success/denied/failed等稳定编码）',
    ip                    varchar(64)     default null               comment '客户端IP',
    user_agent            varchar(255)    default null               comment '客户端 User-Agent',
    detail_json           json                                       comment '附加明细快照（结构化，禁止写入敏感明文）',
    event_time            datetime(3)     default null               comment '事件时间',
    del_flag              char(1)         default '0'                comment '删除标志（0代表存在 1代表删除；审计表不物理删除）',
    create_dept           bigint(20)      default null               comment '创建部门',
    create_by             bigint(20)      default null               comment '创建者',
    create_time           datetime                                   comment '创建时间',
    update_by             bigint(20)      default null               comment '更新者',
    update_time           datetime                                   comment '更新时间',
    remark                varchar(500)    default null               comment '备注',
    primary key (audit_id),
    key idx_hr_recruit_sensitive_audit_biz (biz_type, biz_id, event_time),
    key idx_hr_recruit_sensitive_audit_operator (operator_id, event_time),
    key idx_hr_recruit_sensitive_audit_event (event_time)
) engine=innodb comment = '招聘敏感操作审计表';

-- ----------------------------
-- 21、计划任务状态变更日志（设计 §7.1.5「记录触发事件、原状态、新状态和刷新时间」、
--     §21.15 伪代码「append status change log」）
--     追加型日志表：只插入，不更新、不删除（del_flag 保留以对齐通用字段）；
--     仅在 execution_status 或 completion_status 实际发生变化时追加一条，
--     状态未变化的刷新不写日志，避免无意义刷表。
--     设计 §9.2 的表清单未包含本表，属缺口台账 §4 第 1 项，经用户批准补表（36 → 37）。
-- ----------------------------
drop table if exists hr_recruit_plan_item_status_log;
create table hr_recruit_plan_item_status_log (
    log_id                    bigint(20)    not null                   comment '日志ID（主键）',
    item_id                   bigint(20)    not null                   comment '计划任务ID（hr_recruit_plan_item.item_id）',
    plan_id                   bigint(20)    default null               comment '所属计划表头ID（hr_recruit_plan.plan_id，便于按计划查询）',
    trigger_event             varchar(64)   default null               comment '触发事件稳定编码（application_stage_changed/interview_result_changed/candidate_arrived/background_result_changed/manual_refresh）',
    from_execution_status     varchar(32)   default null               comment '原自动执行阶段（字典 recruit_plan_execution_status）',
    to_execution_status       varchar(32)   default null               comment '新自动执行阶段（字典 recruit_plan_execution_status）',
    from_completion_status    varchar(32)   default null               comment '原完成状态（字典 recruit_plan_completion_status）',
    to_completion_status      varchar(32)   default null               comment '新完成状态（字典 recruit_plan_completion_status）',
    refresh_time              datetime(3)   default null               comment '刷新时间（与 hr_recruit_plan_item.last_refresh_time 同源）',
    operator_id               bigint(20)    default null               comment '操作人用户ID（自动刷新时可为空）',
    del_flag                  char(1)       default '0'                comment '删除标志（0代表存在 1代表删除；日志表不物理删除）',
    create_dept               bigint(20)    default null               comment '创建部门',
    create_by                 bigint(20)    default null               comment '创建者',
    create_time               datetime                                 comment '创建时间',
    update_by                 bigint(20)    default null               comment '更新者',
    update_time               datetime                                 comment '更新时间',
    remark                    varchar(500)  default null               comment '备注',
    primary key (log_id),
    key idx_hr_recruit_plan_item_status_log_item (item_id, refresh_time),
    key idx_hr_recruit_plan_item_status_log_plan (plan_id, refresh_time)
) engine=innodb comment = '月度计划任务状态变更日志表（设计 §7.1.5 / §21.15；追加型日志，只插入）';
