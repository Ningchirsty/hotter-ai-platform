-- ============================================================================
-- 招聘与人才管理一体化系统 · 生产幂等迁移脚本（P1 地基）
-- ----------------------------------------------------------------------------
-- 生成方式（SPEC-P1-地基.md §5）：
--   hr_recruit.sql + hr_talent.sql + hr_talent_menu.sql 三个初始化脚本机械合成：
--     1) 去掉全部 drop table if exists 语句，DDL 一律改为 create table if not exists
--        （重跑不得清空数据）；
--     2) 全部 insert into 改为 insert ignore into（固定主键，重跑不重复插入）。
--
-- 与发布约定的关系（script/deploy/README.md）：
--   发布入口**不执行 SQL**，schema 变更必须手工迁移。本文件供运维在存量环境手工执行。
--
-- 执行顺序：本文件自上而下（招聘过程表 → 人才主数据表 → 菜单/角色/字典）。
-- 前置依赖：平台库已执行 ry_vue.sql（sys_menu / sys_role / sys_dict_type /
--   sys_dict_data / sys_role_menu 的建表与基线数据）。
--
-- 幂等性说明：
--   * 表：create table if not exists，已存在则跳过；不删数据、不改结构。
--     若线上表结构落后于本文件，需要人工 ALTER（本脚本不做结构演进）。
--   * 数据：insert ignore，按主键判重；主键已存在则跳过，**不会更新**既有行。
--     因此本脚本不会覆盖线上已手工调整过的菜单名 / 权限串 / 字典标签。
--
-- *** 当前完整度：三个来源文件均已合入，本文件完整。 ***
-- ============================================================================

-- ============================================================================
-- 来源：script/sql/hr_recruit.sql（招聘过程表 DDL（21 张，hr_recruit_*））
--   create table if not exists 21 条 / insert ignore into 0 条 / drop table 0 条
-- ============================================================================

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
--      check_items / waive_reason（§8.7、§7.4）；hr_recruit_import_error 增加 severity（§8.11）；
--      人才主数据表按 §8.12~§8.16 补充 16 列；完整台账见 docs/hr-talent/P2-决策与缺口台账.md。
--      招聘需求的暂停/关闭原因通过 hr_recruit_demand_change.reason 记录，需求表本身不另设原因列。
--   8. 按 §7.1.5 / §21.15 新增 hr_recruit_plan_item_status_log（计划任务状态变更日志，追加型只插入），
--      用于记录状态刷新的触发事件、原状态、新状态与刷新时间（缺口台账 §4 第 1 项，经用户批准补表）。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、招聘需求来源（设计 §9.2 hr_recruit_demand）
-- ----------------------------
create table if not exists hr_recruit_demand (
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
create table if not exists hr_recruit_demand_change (
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
create table if not exists hr_recruit_plan (
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
create table if not exists hr_recruit_plan_item (
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
-- ----------------------------
create table if not exists hr_recruit_plan_rollover (
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
create table if not exists hr_recruit_plan_application_rel (
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
create table if not exists hr_recruit_job (
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
create table if not exists hr_recruit_application (
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
create table if not exists hr_recruit_stage_log (
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
create table if not exists hr_recruit_interview (
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
create table if not exists hr_recruit_interviewer (
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
create table if not exists hr_recruit_background (
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
create table if not exists hr_recruit_attachment (
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
create table if not exists hr_recruit_standard (
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
create table if not exists hr_recruit_channel (
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
create table if not exists hr_recruit_channel_survey (
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
create table if not exists hr_recruit_peer_company (
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
create table if not exists hr_recruit_import_batch (
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
create table if not exists hr_recruit_import_error (
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
create table if not exists hr_recruit_sensitive_audit (
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
create table if not exists hr_recruit_plan_item_status_log (
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

-- ============================================================================
-- 来源：script/sql/hr_talent.sql（人才主数据表 DDL（16 张，hr_talent_*））
--   create table if not exists 16 条 / insert ignore into 0 条 / drop table 0 条
-- ============================================================================

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
create table if not exists hr_talent_profile (
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
create table if not exists hr_talent_profile_change (
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
create table if not exists hr_talent_resume (
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
create table if not exists hr_talent_education (
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
create table if not exists hr_talent_work (
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
create table if not exists hr_talent_project (
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
create table if not exists hr_talent_pool (
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
create table if not exists hr_talent_pool_member (
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
create table if not exists hr_talent_tag (
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
create table if not exists hr_talent_profile_tag (
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
create table if not exists hr_talent_follow_up (
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
create table if not exists hr_talent_scope_grant (
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
create table if not exists hr_talent_duplicate_case (
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
create table if not exists hr_talent_merge_log (
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
create table if not exists hr_talent_parse_task (
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
create table if not exists hr_talent_parse_result (
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
create table if not exists hr_talent_export_task (
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
-- ----------------------------
create table if not exists hr_talent_group (
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
create table if not exists hr_talent_group_member (
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

-- ============================================================================
-- 来源：script/sql/hr_talent_menu.sql（菜单 / 角色 / 角色菜单绑定 / 数据字典）
--   create table if not exists 0 条 / insert ignore into 486 条 / drop table 0 条
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 招聘与人才管理一体化系统 —— 菜单 / 角色 / 角色菜单绑定 / 数据字典（初始化脚本）
-- 目标库：平台库（MySQL 8.x / MariaDB 11.x）
-- 依赖：script/sql/ry_vue.sql（sys_menu / sys_role / sys_dict_type / sys_dict_data 基线与建表）
-- 设计依据：
--   SPEC-P1-地基.md §4 菜单、角色、权限（菜单树 / 角色清单 / ID 规划）
--   设计文档 §5.1 菜单设计（菜单树权威来源）
--   设计文档 §5.2 权限标识建议（权限字符串权威来源）
--   设计文档 §6   角色与数据权限（9 个角色及默认数据范围）
--   设计文档 §10  数据字典设计（23 组字典的类型与编码值）
--   本次补齐 8 组状态/结果/原因字典（recruit_job_status / recruit_rollover_result /
--   recruit_background_status / recruit_background_failure_reason /
--   recruit_interview_status / recruit_interview_method / recruit_offer_result /
--   recruit_stage_reason_code），字典组 23 → 31
--   设计文档 §21.4 前端目录建议（component 路径 hrtalent/... 的来源）
--
-- 文件性质：
--   本文件为**初始化脚本**，使用普通 insert into（固定主键）。
--   生产环境迁移请使用 hr_talent_migration.sql（由本文件与 hr_recruit.sql /
--   hr_talent.sql 合成，去 drop、insert 改 insert ignore，可重复执行）。
--
-- 段落与顺序：一、字典类型 → 二、字典数据 → 三、角色 → 四、菜单 → 五、角色菜单绑定。
--
-- ID 规划（*** 重要：与 SPEC §4 表格的偏差及原因 ***）：
--   SPEC §4 原规划占用 1763… 段。但该段已被既有模块 aig_ai_gov_menu.sql 实际占用
--   （菜单 1763000000000000001/…101~104、角色 1763100000000000001~003、
--    字典类型 1763200000000000001~006、字典数据 1763300000000000001~053），
--   沿用 1763… 必然主键冲突。故本模块顺延取**完全空闲的 1766… 段**：
--     1766000000000000001              招聘管理一级目录
--     1766000000000000101 ~ …0199      二级菜单（含人才管理二级目录 1766000000000000201）
--     1766000000000000200 ~ …0299      人才管理二级目录及其中菜单
--     1766000000000001000 ~ …3999      按钮
--     1766100000000000001 ~ …009       角色
--     1766200000000000001 ~ …031       字典类型
--     1766300000000000001 ~ …           字典数据
--   已核对既有占用：ry_vue.sql=1761x/1762x、ry_workflow.sql=1762x、
--   aig_ai_gov*=1763x、cp_content*=1764x/1765x、zongxiang*=1764x → 1766… 无冲突。
--
-- 统一约定：
--   create_dept = 1761000000000000103、create_by = 1761100000000000001（与 ry_vue.sql 一致）。
--   字典值一律存稳定英文编码，中文只放 dict_label / remark。
--   component 与前端目录一致：frontend/src/views/hrtalent/<页面>/index.vue。
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 一、字典类型（36 组 = 设计文档 §10 的 23 组 + 前两批补齐的 8 组 + 本批补齐的 5 组）
-- column: dict_id, dict_name, dict_type, create_dept, create_by, create_time,
--         update_by, update_time, remark
-- ----------------------------
insert ignore into sys_dict_type values(1766200000000000001, '招聘需求状态',     'recruit_demand_status',           1761000000000000103, 1761100000000000001, sysdate(), null, null, 'draft/submitted/recruiting/paused/completed/closed');
insert ignore into sys_dict_type values(1766200000000000002, '公司月度计划状态', 'recruit_plan_status',             1761000000000000103, 1761100000000000001, sysdate(), null, null, 'draft/executing/closed');
insert ignore into sys_dict_type values(1766200000000000003, '月度任务来源类型', 'recruit_plan_source_type',        1761000000000000103, 1761100000000000001, sysdate(), null, null, 'new/carryover');
insert ignore into sys_dict_type values(1766200000000000004, '月度任务控制状态', 'recruit_plan_control_status',     1761000000000000103, 1761100000000000001, sysdate(), null, null, 'normal/paused/cancelled');
insert ignore into sys_dict_type values(1766200000000000005, '月度任务执行阶段', 'recruit_plan_execution_status',   1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/recruiting/interviewing/offer/pending_arrival');
insert ignore into sys_dict_type values(1766200000000000006, '月度任务完成状态', 'recruit_plan_completion_status',  1761000000000000103, 1761100000000000001, sysdate(), null, null, 'unfinished/partial_completed/completed/rolled_over');
insert ignore into sys_dict_type values(1766200000000000007, '候选人阶段',       'recruit_candidate_stage',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'new/resume_review/invite/first_interview/second_interview/background/offer/pending_arrival/arrived');
insert ignore into sys_dict_type values(1766200000000000008, '应聘结果',         'recruit_application_result',      1761000000000000103, 1761100000000000001, sysdate(), null, null, 'processing/passed/rejected/withdrawn/paused/talent_pool');
insert ignore into sys_dict_type values(1766200000000000009, '紧急程度',         'recruit_urgency',                 1761000000000000103, 1761100000000000001, sysdate(), null, null, 'normal/urgent/very_urgent');
insert ignore into sys_dict_type values(1766200000000000010, '招聘形式',         'recruit_mode',                    1761000000000000103, 1761100000000000001, sysdate(), null, null, 'internal/social/campus/headhunter/referral/other');
insert ignore into sys_dict_type values(1766200000000000011, '面试结果',         'recruit_interview_result',        1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/pass/fail/reserve/absent');
insert ignore into sys_dict_type values(1766200000000000012, '背调结果',         'recruit_background_result',       1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/pass/fail/waived');
insert ignore into sys_dict_type values(1766200000000000013, '附件类型',         'recruit_attachment_type',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'resume/portfolio/interview/background/offer/other');
insert ignore into sys_dict_type values(1766200000000000014, '招聘数据分级',     'recruit_data_level',              1761000000000000103, 1761100000000000001, sysdate(), null, null, 'internal/sensitive/highly_sensitive');
insert ignore into sys_dict_type values(1766200000000000015, '招聘渠道类型',     'recruit_channel_type',            1761000000000000103, 1761100000000000001, sysdate(), null, null, 'job_site/headhunter/referral/social_media/campus/other');
insert ignore into sys_dict_type values(1766200000000000016, '人才生命周期状态', 'talent_status',                   1761000000000000103, 1761100000000000001, sysdate(), null, null, 'draft/active/recruiting/reserved/hired/do_not_contact/restricted/archived/merged');
insert ignore into sys_dict_type values(1766200000000000017, '人才可见范围',     'talent_visibility_type',          1761000000000000103, 1761100000000000001, sysdate(), null, null, 'group/company/department/owner/explicit');
insert ignore into sys_dict_type values(1766200000000000018, '人才共享授权级别', 'talent_permission_level',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'summary/detail/attachment');
insert ignore into sys_dict_type values(1766200000000000019, '人才池成员状态',   'talent_pool_member_status',       1761000000000000103, 1761100000000000001, sysdate(), null, null, 'active/paused/removed/converted');
insert ignore into sys_dict_type values(1766200000000000020, '人才标签类别',     'talent_tag_category',             1761000000000000103, 1761100000000000001, sysdate(), null, null, 'skill/job_direction/industry/experience/language/certificate/other');
insert ignore into sys_dict_type values(1766200000000000021, '简历解析状态',     'talent_resume_parse_status',      1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/processing/succeeded/failed/reviewing/confirmed');
insert ignore into sys_dict_type values(1766200000000000022, '疑似重复处理状态', 'talent_duplicate_status',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/merged/not_same/ignored/confirmed');
insert ignore into sys_dict_type values(1766200000000000023, '人才联系结果',     'talent_contact_result',           1761000000000000103, 1761100000000000001, sysdate(), null, null, 'connected/no_answer/refused/interested/follow_up_later/invalid');

-- 本次补齐的 6 组字典（原 23 组 → 29 组，ID 顺延 …024~…029）：
--   24. 岗位执行项状态 recruit_job_status（来源 enums/JobStatusEnum.java）
--   25. 月度结转执行结果 recruit_rollover_result（来源 PlanRolloverDomainService.RESULT_* 常量）
--   26. 背调状态 recruit_background_status（来源 enums/BackgroundStatusEnum.java）
--   27. 背调未通过原因分类 recruit_background_failure_reason
--       （设计文档 §10 无此组，按 §8.7「未通过原因分类」新增）
insert ignore into sys_dict_type values(1766200000000000024, '岗位执行项状态',     'recruit_job_status',               1761000000000000103, 1761100000000000001, sysdate(), null, null, 'draft/open/paused/closed');
insert ignore into sys_dict_type values(1766200000000000025, '月度结转执行结果',   'recruit_rollover_result',          1761000000000000103, 1761100000000000001, sysdate(), null, null, 'processing/success/failed/skipped');
insert ignore into sys_dict_type values(1766200000000000026, '背调状态',           'recruit_background_status',        1761000000000000103, 1761100000000000001, sysdate(), null, null, 'draft/checking/finished/cancelled');
insert ignore into sys_dict_type values(1766200000000000027, '背调未通过原因分类', 'recruit_background_failure_reason', 1761000000000000103, 1761100000000000001, sysdate(), null, null, 'info_mismatch/work_experience/education/position_duty/performance/legal_record/other');
--   28. 面试状态 recruit_interview_status（来源 hr_recruit_interview.status 建表注释 +
--       RecruitInterviewServiceImpl.STATUS_* 私有常量）
--   29. 面试方式 recruit_interview_method（来源 hr_recruit_interview.method 建表注释）
insert ignore into sys_dict_type values(1766200000000000028, '面试状态',           'recruit_interview_status',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/scheduled/finished/cancelled/rescheduled');
insert ignore into sys_dict_type values(1766200000000000029, '面试方式',           'recruit_interview_method',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'onsite/video/phone');

-- 本次补齐的另外 2 组字典（原 29 组 → 31 组，ID 顺延 …030~…031）：
--   30. 邀约结果 recruit_offer_result（来源 hr_recruit_application.offer_result 建表注释
--       「accepted/rejected等稳定编码，设计文档 §7.2」+ §14 邀约接受率统计口径）
--   31. 阶段变更原因分类 recruit_stage_reason_code（来源 hr_recruit_stage_log.reason_code
--       建表注释「原因编码（字典编码，不存中文）」+ §8.5「原因分类」）。
--       *** 设计文档未枚举具体编码，下列 8 个编码为本次新定义。***
insert ignore into sys_dict_type values(1766200000000000030, '邀约结果',            'recruit_offer_result',                1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/accepted/rejected');
insert ignore into sys_dict_type values(1766200000000000031, '阶段变更原因分类',    'recruit_stage_reason_code',           1761000000000000103, 1761100000000000001, sysdate(), null, null, 'skill_mismatch/experience_mismatch/salary_mismatch/education_mismatch/communication/candidate_declined/position_closed/other');

-- 本批补齐的 5 组字典（原 31 组 → 36 组，ID 顺延 …032~…036）：
--   32. 学历 talent_education（来源 hr_talent_education.education /
--       hr_talent_profile.highest_education 建表注释「字典编码」，§10 无此组）
--   33. 学位 talent_degree（来源 hr_talent_education.degree 建表注释「字典编码」，§10 无此组）
--   34. 简历解析复核状态 talent_resume_review_status（hr_talent_resume.review_status 专用；
--       该列建表注释要求支持 rejected，而 talent_resume_parse_status 组没有 rejected。
--       hr_talent_resume.parse_status 继续用 talent_resume_parse_status，两者不得混用）
--   35. 数据来源类型 talent_source_type（来源 hr_talent_resume/education/work/project.source_type
--       建表注释「人工/导入/解析等稳定编码」，§10 无此组）
--   36. 人才分组类型 talent_group_type（来源 hr_talent_group.group_type 建表注释 + §8.15）
insert ignore into sys_dict_type values(1766200000000000032, '学历',             'talent_education',            1761000000000000103, 1761100000000000001, sysdate(), null, null, 'high_school/college/bachelor/master/doctor/other');
insert ignore into sys_dict_type values(1766200000000000033, '学位',             'talent_degree',               1761000000000000103, 1761100000000000001, sysdate(), null, null, 'none/bachelor/master/doctor/other');
insert ignore into sys_dict_type values(1766200000000000034, '简历解析复核状态', 'talent_resume_review_status', 1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/reviewing/confirmed/rejected');
insert ignore into sys_dict_type values(1766200000000000035, '数据来源类型',     'talent_source_type',          1761000000000000103, 1761100000000000001, sysdate(), null, null, 'manual/import/parse/system');
insert ignore into sys_dict_type values(1766200000000000036, '人才分组类型',     'talent_group_type',           1761000000000000103, 1761100000000000001, sysdate(), null, null, 'public/personal');

-- ----------------------------
-- 二、字典数据（设计文档 §10 全部编码值 + 前两批补齐的 8 组 + 本批补齐的 5 组）
-- column: dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
--         is_default, create_dept, create_by, create_time, update_by, update_time, remark
-- ----------------------------
-- 1. 招聘需求状态 recruit_demand_status
insert ignore into sys_dict_data values(1766300000000000001, 1, '草稿',     'draft',      'recruit_demand_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '需求草稿，未提交审批');
insert ignore into sys_dict_data values(1766300000000000002, 2, '已提交',   'submitted',  'recruit_demand_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已提交，待招聘负责人确认');
insert ignore into sys_dict_data values(1766300000000000003, 3, '招聘中',   'recruiting', 'recruit_demand_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '可生成岗位需求');
insert ignore into sys_dict_data values(1766300000000000004, 4, '已暂停',   'paused',     'recruit_demand_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '暂停推进，保留历史');
insert ignore into sys_dict_data values(1766300000000000005, 5, '已完成',   'completed',  'recruit_demand_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '需求人数已满足');
insert ignore into sys_dict_data values(1766300000000000006, 6, '已关闭',   'closed',     'recruit_demand_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '终止招聘，不再生成岗位');

-- 2. 公司月度计划状态 recruit_plan_status
insert ignore into sys_dict_data values(1766300000000000011, 1, '草稿',   'draft',     'recruit_plan_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '计划表头尚未确认');
insert ignore into sys_dict_data values(1766300000000000012, 2, '执行中', 'executing', 'recruit_plan_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '计划已确认并进入执行');
insert ignore into sys_dict_data values(1766300000000000013, 3, '已关闭', 'closed',    'recruit_plan_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '月度计划已归档关闭');

-- 3. 月度任务来源类型 recruit_plan_source_type
insert ignore into sys_dict_data values(1766300000000000021, 1, '新建',   'new',       'recruit_plan_source_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本月新增任务');
insert ignore into sys_dict_data values(1766300000000000022, 2, '上月结转', 'carryover', 'recruit_plan_source_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '由上月未完成量结转而来');

-- 4. 月度任务控制状态 recruit_plan_control_status
insert ignore into sys_dict_data values(1766300000000000031, 1, '正常',   'normal',    'recruit_plan_control_status', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '正常执行');
insert ignore into sys_dict_data values(1766300000000000032, 2, '已暂停', 'paused',    'recruit_plan_control_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工暂停该月度任务');
insert ignore into sys_dict_data values(1766300000000000033, 3, '已取消', 'cancelled', 'recruit_plan_control_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工取消该月度任务');

-- 5. 月度任务执行阶段 recruit_plan_execution_status
insert ignore into sys_dict_data values(1766300000000000041, 1, '待启动',   'pending',         'recruit_plan_execution_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '尚无候选人推进');
insert ignore into sys_dict_data values(1766300000000000042, 2, '招聘中',   'recruiting',      'recruit_plan_execution_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人处于简历/邀约阶段');
insert ignore into sys_dict_data values(1766300000000000043, 3, '面试中',   'interviewing',    'recruit_plan_execution_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已有候选人进入面试');
insert ignore into sys_dict_data values(1766300000000000044, 4, '已发Offer', 'offer',          'recruit_plan_execution_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已发出录用通知');
insert ignore into sys_dict_data values(1766300000000000045, 5, '待到岗',   'pending_arrival', 'recruit_plan_execution_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已接受Offer，等待报到');

-- 6. 月度任务完成状态 recruit_plan_completion_status
insert ignore into sys_dict_data values(1766300000000000051, 1, '未完成',   'unfinished',        'recruit_plan_completion_status', '', 'warning', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未完成，月末参与结转');
insert ignore into sys_dict_data values(1766300000000000052, 2, '部分完成', 'partial_completed', 'recruit_plan_completion_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '到岗人数小于计划人数');
insert ignore into sys_dict_data values(1766300000000000053, 3, '已完成',   'completed',         'recruit_plan_completion_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '到岗人数已达到计划人数');
insert ignore into sys_dict_data values(1766300000000000054, 4, '已结转',   'rolled_over',       'recruit_plan_completion_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未完成量已结转到次月');

-- 7. 候选人阶段 recruit_candidate_stage
insert ignore into sys_dict_data values(1766300000000000061, 1, '新简历',     'new',              'recruit_candidate_stage', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人记录已建立');
insert ignore into sys_dict_data values(1766300000000000062, 2, '简历筛选',   'resume_review',    'recruit_candidate_stage', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '招聘专员筛选简历');
insert ignore into sys_dict_data values(1766300000000000063, 3, '已邀约',     'invite',           'recruit_candidate_stage', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已发出面试邀约');
insert ignore into sys_dict_data values(1766300000000000064, 4, '初试',       'first_interview',  'recruit_candidate_stage', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '初试进行中');
insert ignore into sys_dict_data values(1766300000000000065, 5, '复试',       'second_interview', 'recruit_candidate_stage', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '复试进行中');
insert ignore into sys_dict_data values(1766300000000000066, 6, '背调',       'background',       'recruit_candidate_stage', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '背景调查中，敏感阶段');
insert ignore into sys_dict_data values(1766300000000000067, 7, 'Offer',      'offer',            'recruit_candidate_stage', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已发出录用通知');
insert ignore into sys_dict_data values(1766300000000000068, 8, '待到岗',     'pending_arrival',  'recruit_candidate_stage', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '等待候选人报到');
insert ignore into sys_dict_data values(1766300000000000069, 9, '已到岗',     'arrived',          'recruit_candidate_stage', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已报到，计入到岗人数');

-- 8. 应聘结果 recruit_application_result
insert ignore into sys_dict_data values(1766300000000000071, 1, '处理中',   'processing',  'recruit_application_result', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '流程进行中');
insert ignore into sys_dict_data values(1766300000000000072, 2, '已通过',   'passed',      'recruit_application_result', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试通过并已完成后续流程');
insert ignore into sys_dict_data values(1766300000000000073, 3, '未通过',   'rejected',    'recruit_application_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '被淘汰');
insert ignore into sys_dict_data values(1766300000000000074, 4, '已撤回',   'withdrawn',   'recruit_application_result', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人主动放弃');
insert ignore into sys_dict_data values(1766300000000000075, 5, '已暂停',   'paused',      'recruit_application_result', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '流程暂停，可恢复');
insert ignore into sys_dict_data values(1766300000000000076, 6, '转入人才池', 'talent_pool', 'recruit_application_result', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本次未录用但保留为人才');

-- 9. 紧急程度 recruit_urgency
insert ignore into sys_dict_data values(1766300000000000081, 1, '普通',   'normal',      'recruit_urgency', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '常规招聘节奏');
insert ignore into sys_dict_data values(1766300000000000082, 2, '紧急',   'urgent',      'recruit_urgency', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '需要优先安排');
insert ignore into sys_dict_data values(1766300000000000083, 3, '非常紧急', 'very_urgent', 'recruit_urgency', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '最高优先级，需当日响应');

-- 10. 招聘形式 recruit_mode
insert ignore into sys_dict_data values(1766300000000000091, 1, '内部招聘', 'internal',   'recruit_mode', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '内部转岗或推荐');
insert ignore into sys_dict_data values(1766300000000000092, 2, '社会招聘', 'social',     'recruit_mode', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面向社会公开招聘');
insert ignore into sys_dict_data values(1766300000000000093, 3, '校园招聘', 'campus',     'recruit_mode', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '校招渠道');
insert ignore into sys_dict_data values(1766300000000000094, 4, '猎头',     'headhunter', 'recruit_mode', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '委托猎头渠道');
insert ignore into sys_dict_data values(1766300000000000095, 5, '内推',     'referral',   'recruit_mode', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '员工内部推荐');
insert ignore into sys_dict_data values(1766300000000000096, 6, '其他',     'other',      'recruit_mode', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他招聘形式');

-- 11. 面试结果 recruit_interview_result
insert ignore into sys_dict_data values(1766300000000000101, 1, '待反馈', 'pending', 'recruit_interview_result', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试结束待面试官反馈');
insert ignore into sys_dict_data values(1766300000000000102, 2, '通过',   'pass',    'recruit_interview_result', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '进入下一阶段');
insert ignore into sys_dict_data values(1766300000000000103, 3, '不通过', 'fail',    'recruit_interview_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '流程结束');
insert ignore into sys_dict_data values(1766300000000000104, 4, '待定',   'reserve', 'recruit_interview_result', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '暂不确定，保留候选');
insert ignore into sys_dict_data values(1766300000000000105, 5, '未到场', 'absent',  'recruit_interview_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人爽约');

-- 12. 背调结果 recruit_background_result
insert ignore into sys_dict_data values(1766300000000000111, 1, '待背调', 'pending', 'recruit_background_result', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '尚未发起或未回执');
insert ignore into sys_dict_data values(1766300000000000112, 2, '通过',   'pass',    'recruit_background_result', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '背调结论无异常');
insert ignore into sys_dict_data values(1766300000000000113, 3, '不通过', 'fail',    'recruit_background_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '存在重大不一致');
insert ignore into sys_dict_data values(1766300000000000114, 4, '已豁免', 'waived',  'recruit_background_result', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '经授权豁免背调');

-- 13. 附件类型 recruit_attachment_type
insert ignore into sys_dict_data values(1766300000000000121, 1, '简历',       'resume',     'recruit_attachment_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人简历文件');
insert ignore into sys_dict_data values(1766300000000000122, 2, '作品集',     'portfolio',  'recruit_attachment_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '作品或案例材料');
insert ignore into sys_dict_data values(1766300000000000123, 3, '面试材料',   'interview',  'recruit_attachment_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试题、面试记录附件');
insert ignore into sys_dict_data values(1766300000000000124, 4, '背调材料',   'background', 'recruit_attachment_type', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '高敏感，需独立权限');
insert ignore into sys_dict_data values(1766300000000000125, 5, 'Offer附件',  'offer',      'recruit_attachment_type', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '录用通知书等');
insert ignore into sys_dict_data values(1766300000000000126, 6, '其他',       'other',      'recruit_attachment_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他业务附件');

-- 14. 招聘数据分级 recruit_data_level
insert ignore into sys_dict_data values(1766300000000000131, 1, '内部',   'internal',         'recruit_data_level', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '公司内部一般数据');
insert ignore into sys_dict_data values(1766300000000000132, 2, '敏感',   'sensitive',        'recruit_data_level', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '联系方式、薪资期望等');
insert ignore into sys_dict_data values(1766300000000000133, 3, '高敏感', 'highly_sensitive', 'recruit_data_level', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '背调明细、证件信息；默认禁止外发');

-- 15. 招聘渠道类型 recruit_channel_type
insert ignore into sys_dict_data values(1766300000000000141, 1, '招聘网站', 'job_site',     'recruit_channel_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '如主流招聘平台');
insert ignore into sys_dict_data values(1766300000000000142, 2, '猎头',     'headhunter',   'recruit_channel_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '猎头/人力服务商');
insert ignore into sys_dict_data values(1766300000000000143, 3, '内推',     'referral',     'recruit_channel_type', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '员工推荐渠道');
insert ignore into sys_dict_data values(1766300000000000144, 4, '社交媒体', 'social_media', 'recruit_channel_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '社交平台渠道');
insert ignore into sys_dict_data values(1766300000000000145, 5, '校园',     'campus',       'recruit_channel_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '校招渠道');
insert ignore into sys_dict_data values(1766300000000000146, 6, '其他',     'other',        'recruit_channel_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他渠道');

-- 16. 人才生命周期状态 talent_status
insert ignore into sys_dict_data values(1766300000000000151, 1, '草稿',     'draft',           'talent_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资料未完善');
insert ignore into sys_dict_data values(1766300000000000152, 2, '生效',     'active',          'talent_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '有效人才档案');
insert ignore into sys_dict_data values(1766300000000000153, 3, '招聘中',   'recruiting',      'talent_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '关联进行中的应聘流程');
insert ignore into sys_dict_data values(1766300000000000154, 4, '储备',     'reserved',        'talent_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '进入人才储备池');
insert ignore into sys_dict_data values(1766300000000000155, 5, '已入职',   'hired',           'talent_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已报到入职');
insert ignore into sys_dict_data values(1766300000000000156, 6, '请勿联系', 'do_not_contact',  'talent_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人明确要求不再联系');
insert ignore into sys_dict_data values(1766300000000000157, 7, '受限',     'restricted',      'talent_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '查看范围受限，需显式授权');
insert ignore into sys_dict_data values(1766300000000000158, 8, '已归档',   'archived',        'talent_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '长期不活跃，归档保留');
insert ignore into sys_dict_data values(1766300000000000159, 9, '已合并',   'merged',          'talent_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '被合并到主档，不再单独使用');

-- 17. 人才可见范围 talent_visibility_type
insert ignore into sys_dict_data values(1766300000000000161, 1, '集团共享', 'group',      'talent_visibility_type', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '集团内可见');
insert ignore into sys_dict_data values(1766300000000000162, 2, '本公司',   'company',    'talent_visibility_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅本公司可见');
insert ignore into sys_dict_data values(1766300000000000163, 3, '本部门',   'department', 'talent_visibility_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅所属部门可见');
insert ignore into sys_dict_data values(1766300000000000164, 4, '仅负责人', 'owner',      'talent_visibility_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅人才负责人可见');
insert ignore into sys_dict_data values(1766300000000000165, 5, '显式授权', 'explicit',   'talent_visibility_type', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅 hr_talent_scope_grant 中显式授权对象可见');

-- 18. 人才共享授权级别 talent_permission_level
insert ignore into sys_dict_data values(1766300000000000171, 1, '摘要',       'summary',    'talent_permission_level', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅脱敏摘要');
insert ignore into sys_dict_data values(1766300000000000172, 2, '明细',       'detail',     'talent_permission_level', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '可查看明细字段');
insert ignore into sys_dict_data values(1766300000000000173, 3, '含附件',     'attachment', 'talent_permission_level', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '可查看/下载授权附件');

-- 19. 人才池成员状态 talent_pool_member_status
insert ignore into sys_dict_data values(1766300000000000181, 1, '在池',   'active',    'talent_pool_member_status', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '正常在池');
insert ignore into sys_dict_data values(1766300000000000182, 2, '已暂停', 'paused',    'talent_pool_member_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '暂停跟进');
insert ignore into sys_dict_data values(1766300000000000183, 3, '已移出', 'removed',   'talent_pool_member_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已移出人才池');
insert ignore into sys_dict_data values(1766300000000000184, 4, '已转化', 'converted', 'talent_pool_member_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已转化为应聘或入职');

-- 20. 人才标签类别 talent_tag_category
insert ignore into sys_dict_data values(1766300000000000191, 1, '技能',     'skill',         'talent_tag_category', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '技术或业务技能标签');
insert ignore into sys_dict_data values(1766300000000000192, 2, '岗位方向', 'job_direction', 'talent_tag_category', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '适配岗位方向');
insert ignore into sys_dict_data values(1766300000000000193, 3, '行业',     'industry',      'talent_tag_category', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '行业背景标签');
insert ignore into sys_dict_data values(1766300000000000194, 4, '经验',     'experience',    'talent_tag_category', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '经验年限/层级标签');
insert ignore into sys_dict_data values(1766300000000000195, 5, '语言',     'language',      'talent_tag_category', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '语言能力标签');
insert ignore into sys_dict_data values(1766300000000000196, 6, '证书',     'certificate',   'talent_tag_category', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资质证书标签');
insert ignore into sys_dict_data values(1766300000000000197, 7, '其他',     'other',         'talent_tag_category', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他标签');

-- 21. 简历解析状态 talent_resume_parse_status
insert ignore into sys_dict_data values(1766300000000000201, 1, '待解析',   'pending',    'talent_resume_parse_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已上传待解析');
insert ignore into sys_dict_data values(1766300000000000202, 2, '解析中',   'processing', 'talent_resume_parse_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '解析任务执行中');
insert ignore into sys_dict_data values(1766300000000000203, 3, '解析成功', 'succeeded',  'talent_resume_parse_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已产出结构化结果');
insert ignore into sys_dict_data values(1766300000000000204, 4, '解析失败', 'failed',     'talent_resume_parse_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '需人工处理或重试');
insert ignore into sys_dict_data values(1766300000000000205, 5, '待复核',   'reviewing',  'talent_resume_parse_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '解析结果待人工复核');
insert ignore into sys_dict_data values(1766300000000000206, 6, '已确认',   'confirmed',  'talent_resume_parse_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工复核通过并写入档案');

-- 22. 疑似重复处理状态 talent_duplicate_status
insert ignore into sys_dict_data values(1766300000000000211, 1, '待处理',   'pending',  'talent_duplicate_status', '', 'warning', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '疑似重复待人工判定');
insert ignore into sys_dict_data values(1766300000000000212, 2, '已合并',   'merged',   'talent_duplicate_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已合并到主档，写入合并日志');
insert ignore into sys_dict_data values(1766300000000000213, 3, '非同一人', 'not_same', 'talent_duplicate_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '经确认并非同一人');
insert ignore into sys_dict_data values(1766300000000000214, 4, '已忽略',   'ignored',  'talent_duplicate_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '暂不处理，保留预警');
insert ignore into sys_dict_data values(1766300000000000265, 5, '已确认待合并', 'confirmed', 'talent_duplicate_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工确认确为同一人，尚未执行合并；不计入「待处理」工作队列');

-- 23. 人才联系结果 talent_contact_result
insert ignore into sys_dict_data values(1766300000000000221, 1, '已接通',     'connected',       'talent_contact_result', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已取得联系');
insert ignore into sys_dict_data values(1766300000000000222, 2, '未接听',     'no_answer',       'talent_contact_result', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '电话未接听');
insert ignore into sys_dict_data values(1766300000000000223, 3, '已拒绝',     'refused',         'talent_contact_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人拒绝沟通或推荐');
insert ignore into sys_dict_data values(1766300000000000224, 4, '有意向',     'interested',      'talent_contact_result', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人有明确意向');
insert ignore into sys_dict_data values(1766300000000000225, 5, '稍后跟进',   'follow_up_later', 'talent_contact_result', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '约定后续时间再联系');
insert ignore into sys_dict_data values(1766300000000000226, 6, '联系方式无效', 'invalid',       'talent_contact_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '号码或邮箱已失效');

-- 24. 岗位执行项状态 recruit_job_status
insert ignore into sys_dict_data values(1766300000000000227, 1, '草稿',   'draft',  'recruit_job_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '岗位草稿，尚未对外发布');
insert ignore into sys_dict_data values(1766300000000000228, 2, '招聘中', 'open',   'recruit_job_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '岗位已发布，可接收应聘记录');
insert ignore into sys_dict_data values(1766300000000000229, 3, '已暂停', 'paused', 'recruit_job_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '暂停招聘，可恢复为招聘中');
insert ignore into sys_dict_data values(1766300000000000230, 4, '已关闭', 'closed', 'recruit_job_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '结束招聘，仅可通过重新开放回到招聘中');

-- 25. 月度结转执行结果 recruit_rollover_result
insert ignore into sys_dict_data values(1766300000000000231, 1, '处理中', 'processing', 'recruit_rollover_result', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '结转执行中的占位记录');
insert ignore into sys_dict_data values(1766300000000000232, 2, '成功',   'success',    'recruit_rollover_result', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '结转任务已正常完成');
insert ignore into sys_dict_data values(1766300000000000233, 3, '失败',   'failed',     'recruit_rollover_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '执行失败并记录原因，可安全重试');
insert ignore into sys_dict_data values(1766300000000000234, 4, '跳过',   'skipped',    'recruit_rollover_result', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '不落库，仅出现在执行明细中');

-- 26. 背调状态 recruit_background_status
insert ignore into sys_dict_data values(1766300000000000235, 1, '草稿',   'draft',     'recruit_background_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已登记但尚未开始核查');
insert ignore into sys_dict_data values(1766300000000000236, 2, '核查中', 'checking',  'recruit_background_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '背调核查进行中');
insert ignore into sys_dict_data values(1766300000000000237, 3, '已完成', 'finished',  'recruit_background_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已回执背调结论');
insert ignore into sys_dict_data values(1766300000000000238, 4, '已取消', 'cancelled', 'recruit_background_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工取消，或因被新背调替代而失效');

-- 27. 背调未通过原因分类 recruit_background_failure_reason
--     设计文档 §10 无此组，按 §8.7「未通过原因分类」新增
insert ignore into sys_dict_data values(1766300000000000239, 1, '信息不符',         'info_mismatch',   'recruit_background_failure_reason', '', 'warning', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人提供的基础信息与核查结果不一致');
insert ignore into sys_dict_data values(1766300000000000240, 2, '工作经历不一致',   'work_experience', 'recruit_background_failure_reason', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '任职时间、单位或岗位与事实不符');
insert ignore into sys_dict_data values(1766300000000000241, 3, '学历或证书不一致', 'education',       'recruit_background_failure_reason', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '学历学位或资质证书无法核实');
insert ignore into sys_dict_data values(1766300000000000242, 4, '职位职责不一致',   'position_duty',   'recruit_background_failure_reason', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '职位名称或职责范围与事实不符');
insert ignore into sys_dict_data values(1766300000000000243, 5, '业绩表现不一致',   'performance',     'recruit_background_failure_reason', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '业绩或绩效记录与事实不符');
insert ignore into sys_dict_data values(1766300000000000244, 6, '法律或信用记录',   'legal_record',    'recruit_background_failure_reason', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '存在法律纠纷、失信或不良信用记录');
insert ignore into sys_dict_data values(1766300000000000245, 7, '其他',             'other',           'recruit_background_failure_reason', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '不属于上述分类的其他未通过原因');

-- 28. 面试状态 recruit_interview_status
insert ignore into sys_dict_data values(1766300000000000246, 1, '待安排', 'pending',     'recruit_interview_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试已创建但时间未定');
insert ignore into sys_dict_data values(1766300000000000247, 2, '已安排', 'scheduled',   'recruit_interview_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试时间与方式已确定');
insert ignore into sys_dict_data values(1766300000000000248, 3, '已完成', 'finished',    'recruit_interview_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试已结束并回执');
insert ignore into sys_dict_data values(1766300000000000249, 4, '已取消', 'cancelled',   'recruit_interview_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试被取消，保留历史记录');
insert ignore into sys_dict_data values(1766300000000000250, 5, '已改期', 'rescheduled', 'recruit_interview_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '原安排被改期，作为历史记录保留');

-- 29. 面试方式 recruit_interview_method
insert ignore into sys_dict_data values(1766300000000000251, 1, '现场', 'onsite', 'recruit_interview_method', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '线下面试，地点填写在 location');
insert ignore into sys_dict_data values(1766300000000000252, 2, '视频', 'video',  'recruit_interview_method', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '线上视频面试，会议链接填写在 location');
insert ignore into sys_dict_data values(1766300000000000253, 3, '电话', 'phone',  'recruit_interview_method', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '电话面试，location 可为空');

-- 30. 邀约结果 recruit_offer_result
--     hr_recruit_application.offer_result 建表注释「accepted/rejected等稳定编码，设计文档 §7.2」；
--     §14「邀约接受率＝接受邀约人数÷有效邀约人数」按 accepted 统计。
insert ignore into sys_dict_data values(1766300000000000254, 1, '待反馈',        'pending',   'recruit_offer_result', '',     'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已发出邀约，候选人尚未答复');
insert ignore into sys_dict_data values(1766300000000000255, 2, '已接受',        'accepted',  'recruit_offer_result', '',     'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人接受邀约，计入邀约接受率分子');
insert ignore into sys_dict_data values(1766300000000000256, 3, '已拒绝',        'rejected',  'recruit_offer_result', '',     'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人拒绝邀约');

-- 31. 阶段变更原因分类 recruit_stage_reason_code
--     hr_recruit_stage_log.reason_code 建表注释「原因编码（字典编码，不存中文）」+ §8.5「原因分类」；
--     *** 设计文档未枚举具体编码，下列 8 个编码为本次新定义。***
insert ignore into sys_dict_data values(1766300000000000257, 1, '技能不匹配',          'skill_mismatch',        'recruit_stage_reason_code', '', 'warning', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人所具备技能与岗位要求不匹配');
insert ignore into sys_dict_data values(1766300000000000258, 2, '经验年限不符',        'experience_mismatch',   'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '相关工作年限低于岗位要求');
insert ignore into sys_dict_data values(1766300000000000259, 3, '薪资不符',            'salary_mismatch',       'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '期望薪资与岗位预算区间不一致');
insert ignore into sys_dict_data values(1766300000000000260, 4, '学历不符',            'education_mismatch',    'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '学历或专业不满足岗位要求');
insert ignore into sys_dict_data values(1766300000000000261, 5, '沟通表现不符',        'communication',         'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '沟通表达或职业素养不符合要求');
insert ignore into sys_dict_data values(1766300000000000262, 6, '候选人主动放弃',      'candidate_declined',    'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人主动退出流程（区别于企业淘汰）');
insert ignore into sys_dict_data values(1766300000000000263, 7, '岗位已关闭',          'position_closed',       'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '岗位暂停或关闭导致流程终止');
insert ignore into sys_dict_data values(1766300000000000264, 8, '其他',                'other',                 'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '不属于上述分类的其他原因');

-- 32. 学历 talent_education
--     hr_talent_education.education / hr_talent_profile.highest_education 建表注释「字典编码」；
--     §10 无此组，本组 6 个编码为本次新定义。
insert ignore into sys_dict_data values(1766300000000000266, 1, '高中', 'high_school', 'talent_education', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '高中及同等学力');
insert ignore into sys_dict_data values(1766300000000000267, 2, '大专', 'college',     'talent_education', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '大专学历');
insert ignore into sys_dict_data values(1766300000000000268, 3, '本科', 'bachelor',    'talent_education', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本科学历');
insert ignore into sys_dict_data values(1766300000000000269, 4, '硕士', 'master',      'talent_education', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '硕士研究生学历');
insert ignore into sys_dict_data values(1766300000000000270, 5, '博士', 'doctor',      'talent_education', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '博士研究生学历');
insert ignore into sys_dict_data values(1766300000000000271, 6, '其他', 'other',       'talent_education', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他学历形式');

-- 33. 学位 talent_degree
--     hr_talent_education.degree 建表注释「字典编码」；§10 无此组，本组 5 个编码为本次新定义。
insert ignore into sys_dict_data values(1766300000000000272, 1, '无',   'none',     'talent_degree', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未取得学位');
insert ignore into sys_dict_data values(1766300000000000273, 2, '学士', 'bachelor', 'talent_degree', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '学士学位');
insert ignore into sys_dict_data values(1766300000000000274, 3, '硕士', 'master',   'talent_degree', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '硕士学位');
insert ignore into sys_dict_data values(1766300000000000275, 4, '博士', 'doctor',   'talent_degree', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '博士学位');
insert ignore into sys_dict_data values(1766300000000000276, 5, '其他', 'other',    'talent_degree', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他学位类型');

-- 34. 简历解析复核状态 talent_resume_review_status
--     hr_talent_resume.review_status 专用；§10 无此组，本组 4 个编码为本次新定义。
--     parse_status 继续使用 talent_resume_parse_status，两列两字典不得混用。
insert ignore into sys_dict_data values(1766300000000000277, 1, '待复核', 'pending',   'talent_resume_review_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '解析结果待人工复核');
insert ignore into sys_dict_data values(1766300000000000278, 2, '复核中', 'reviewing', 'talent_resume_review_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工复核进行中');
insert ignore into sys_dict_data values(1766300000000000279, 3, '已确认', 'confirmed', 'talent_resume_review_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '复核通过，可写入正式人才字段');
insert ignore into sys_dict_data values(1766300000000000280, 4, '已否决', 'rejected',  'talent_resume_review_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '复核不通过，解析结果不予采信');

-- 35. 数据来源类型 talent_source_type
--     hr_talent_resume/education/work/project.source_type 建表注释「人工/导入/解析等稳定编码」；
--     §10 无此组，本组 4 个编码为本次新定义。
insert ignore into sys_dict_data values(1766300000000000281, 1, '人工录入', 'manual', 'talent_source_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工录入或页面手工维护');
insert ignore into sys_dict_data values(1766300000000000282, 2, '导入',     'import', 'talent_source_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '批量导入或文件导入');
insert ignore into sys_dict_data values(1766300000000000283, 3, '简历解析', 'parse',  'talent_source_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '由简历解析任务写入');
insert ignore into sys_dict_data values(1766300000000000284, 4, '系统生成', 'system', 'talent_source_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '由系统任务或规则自动生成');

-- 36. 人才分组类型 talent_group_type
--     hr_talent_group.group_type 建表注释 + §8.15「公共分组 / 个人收藏」；
--     本组 2 个编码为本次新定义。
insert ignore into sys_dict_data values(1766300000000000285, 1, '公共分组', 'public',   'talent_group_type', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '由部门或管理员维护的公共人才分组');
insert ignore into sys_dict_data values(1766300000000000286, 2, '个人收藏', 'personal', 'talent_group_type', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '用户私有的个人收藏分组');


-- ----------------------------
-- 三、角色（9 个，设计文档 §6）
-- column: role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
--         dept_check_strictly, status, del_flag, create_dept, create_by, create_time,
--         update_by, update_time, remark
-- data_scope：1=全部 3=本部门 4=本部门及以下 5=仅本人（集团/公司/授权部门等跨公司范围
--   由 TalentScopeDomainService 按 §21.14 生成，RoleDept 自定义范围在 P4 随组织机构数据配置）
-- ----------------------------
insert ignore into sys_role values(1766100000000000001, '平台管理员',     'hr_platform_admin',        10, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '全平台：系统配置、账号、菜单和基础运维；原则上不承担招聘业务数据日常维护');
insert ignore into sys_role values(1766100000000000002, '集团招聘管理员', 'hr_recruit_admin_group',   11, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '集团全部组织：全部招聘业务、标准、渠道、统计和审计；附件不得直接物理删除');
insert ignore into sys_role values(1766100000000000003, '集团人才管理员', 'hr_talent_admin_group',    12, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '集团共享人才及授权公司人才：人才主档、人才池、标签、重复治理、共享授权和人才统计；高敏感附件仍需独立权限');
insert ignore into sys_role values(1766100000000000004, '公司招聘负责人', 'hr_company_recruit_owner', 13, 4, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本公司及授权部门：需求、岗位、候选人、面试、背调和报到；不能查看其他公司的候选人明文和背调');
insert ignore into sys_role values(1766100000000000005, '招聘专员',       'hr_recruiter',             14, 5, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本人负责或本部门：录入候选人、跟进、安排面试和上传附件；导出、电话明文和背调需单独授权');
insert ignore into sys_role values(1766100000000000006, '用人部门负责人', 'hr_dept_owner',            15, 3, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本部门岗位和相关候选人：提交需求、查看进度、填写面试意见；不可浏览无关候选人');
insert ignore into sys_role values(1766100000000000007, '面试官',         'hr_interviewer',           16, 5, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '被安排的面试任务：查看必要简历和提交面试反馈；授权随任务结束到期，不可批量导出');
insert ignore into sys_role values(1766100000000000008, '审计查看者',     'hr_auditor',               17, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '审计授权范围：查看敏感操作记录；默认不可下载候选人附件');
insert ignore into sys_role values(1766100000000000009, '人才库查阅者',   'hr_talent_viewer',         18, 4, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '被授权人才池或人才档案：检索、查看脱敏人才摘要及授权附件；不可修改、导出或查看背调资料');

-- ----------------------------
-- 四、菜单（SPEC §4 菜单树；设计文档 §5.1 / §5.2）
-- column: menu_id, menu_name, parent_id, order_num, path, component, query_param,
--         is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext,
--         create_dept, create_by, create_time, update_by, update_time, remark
--
-- 说明：
--   * 「招聘管理」为一级目录（menu_type='M'，parent_id=0，path='recruit'）。
--   * 「人才管理」为二级目录（menu_type='M'，parent_id=招聘管理，path='talent'）。
--   * component 取自设计文档 §21.4 的 views/hrtalent/<页面>/，前端实际文件为 index.vue。
--   * 目录/菜单的 perms 取其自身列表权限；其余动作逐条建 F 按钮，权限串逐条取 §5.2。
--   * 【重要】以下 5 个菜单的 visible 刻意设为 '1'（隐藏），因其**后端整层与前端页面尚未实现**：
--       管理驾驶舱(…101) / 招聘渠道(…109) / 同行信息(…110) / 招聘标准(…111) / 数据导入中心(…112)。
--     DDL、权限常量与菜单已在 P1 就位，但实体/Mapper/服务/控制器/前端页面整体缺失，显示出来只会得到空白页。
--     其中驾驶舱与导入中心属阶段 4；渠道/同行/标准属未分配缺口。**用户裁定：先隐藏，功能另行排期。**
--     详细说明与理由见 `hr_talent_menu.sql` 同名注释块（两文件必须保持一致）。
-- ----------------------------
-- 一级目录
insert ignore into sys_menu values(1766000000000000001, '招聘管理', 0, 6, 'recruit', null, '', 'N', 'Y', 'M', '0', '0', '', 'peoples', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '招聘与人才管理一体化系统（招聘过程 + 人才主数据）');

-- 1 管理驾驶舱
insert ignore into sys_menu values(1766000000000000101, '管理驾驶舱', 1766000000000000001, 1, 'dashboard', 'hrtalent/dashboard/index', '', 'N', 'Y', 'C', '1', '0', 'recruit:dashboard:view', 'monitor', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '招聘概览与核心指标');
-- 2 招聘需求
insert ignore into sys_menu values(1766000000000000102, '招聘需求', 1766000000000000001, 2, 'demand', 'hrtalent/demand/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:demand:list', 'list', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '需求来源、审批与状态流转');
-- 3 公司月度计划
insert ignore into sys_menu values(1766000000000000103, '公司月度计划', 1766000000000000001, 3, 'plan', 'hrtalent/plan/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:plan:list', 'date', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '公司月度招聘计划与月度任务');
-- 4 月度结转中心
insert ignore into sys_menu values(1766000000000000104, '月度结转中心', 1766000000000000001, 4, 'rollover', 'hrtalent/rollover/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:rollover:preview', 'workflow', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未完成月度任务结转预览与执行');
-- 5 岗位需求
insert ignore into sys_menu values(1766000000000000105, '岗位需求', 1766000000000000001, 5, 'job', 'hrtalent/job/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:job:list', 'job', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '岗位需求发布与招聘负责人分配');
-- 6 候选人跟进
insert ignore into sys_menu values(1766000000000000106, '候选人跟进', 1766000000000000001, 6, 'application', 'hrtalent/application/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:candidate:list', 'user', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人应聘记录、阶段流转与跟进');
-- 7 面试管理
insert ignore into sys_menu values(1766000000000000107, '面试管理', 1766000000000000001, 7, 'interview', 'hrtalent/interview/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:interview:list', 'message', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试安排、面试官与面试反馈');
-- 8 背调与报到
insert ignore into sys_menu values(1766000000000000108, '背调与报到', 1766000000000000001, 8, 'background', 'hrtalent/background/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:background:list', 'eye-open', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '背景调查与报到登记（高敏感）');
-- 9 人才管理（二级目录）
insert ignore into sys_menu values(1766000000000000201, '人才管理', 1766000000000000001, 9, 'talent', null, '', 'N', 'Y', 'M', '0', '0', '', 'company', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才主数据二级目录');
--   9.1 人才档案
insert ignore into sys_menu values(1766000000000000202, '人才档案', 1766000000000000201, 1, 'profile', 'hrtalent/talent-profile/index', '', 'N', 'Y', 'C', '0', '0', 'talent:profile:list', 'user', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '一人一档的人才主档');
--   9.2 人才池与分组
insert ignore into sys_menu values(1766000000000000203, '人才池与分组', 1766000000000000201, 2, 'pool', 'hrtalent/talent-pool/index', '', 'N', 'Y', 'C', '0', '0', 'talent:pool:list', 'my-copy', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才池、成员分组与标签');
--   9.3 简历中心
insert ignore into sys_menu values(1766000000000000204, '简历中心', 1766000000000000201, 3, 'resume', 'hrtalent/resume/index', '', 'N', 'Y', 'C', '0', '0', 'talent:resume:list', 'documentation', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '简历上传、解析、版本与人工复核');
--   9.4 重复人才治理
insert ignore into sys_menu values(1766000000000000205, '重复人才治理', 1766000000000000201, 4, 'duplicate', 'hrtalent/duplicate/index', '', 'N', 'Y', 'C', '0', '0', 'talent:duplicate:list', 'search', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '疑似重复人才确认、合并与忽略');
--   9.5 人才共享授权
insert ignore into sys_menu values(1766000000000000206, '人才共享授权', 1766000000000000201, 5, 'grant', 'hrtalent/grant/index', '', 'N', 'Y', 'C', '0', '0', 'talent:grant:list', 'lock', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才可见范围与共享授权（含撤销）');
-- 10 招聘渠道
insert ignore into sys_menu values(1766000000000000109, '招聘渠道', 1766000000000000001, 10, 'channel', 'hrtalent/channel/index', '', 'N', 'Y', 'C', '1', '0', 'recruit:channel:list', 'link', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '招聘渠道主数据与渠道效果');
-- 11 同行信息
insert ignore into sys_menu values(1766000000000000110, '同行信息', 1766000000000000001, 11, 'peer', 'hrtalent/peer/index', '', 'N', 'Y', 'C', '1', '0', 'recruit:peer:list', 'company', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '同行公司与人才市场信息');
-- 12 招聘标准
insert ignore into sys_menu values(1766000000000000111, '招聘标准', 1766000000000000001, 12, 'standard', 'hrtalent/standard/index', '', 'N', 'Y', 'C', '1', '0', 'recruit:standard:list', 'skill', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '岗位招聘标准与评价项');
-- 13 数据导入中心
insert ignore into sys_menu values(1766000000000000112, '数据导入中心', 1766000000000000001, 13, 'import-center', 'hrtalent/import-center/index', '', 'N', 'Y', 'C', '1', '0', 'recruit:import:list', 'upload', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, 'Excel 模板下载、上传、预检与确认导入');
-- 14 敏感操作审计
insert ignore into sys_menu values(1766000000000000113, '敏感操作审计', 1766000000000000001, 14, 'audit', 'hrtalent/audit/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:audit:list', 'eye', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '敏感操作审计记录（只读）');

-- ---- 按钮：管理驾驶舱 recruit:dashboard:view/export ----
insert ignore into sys_menu values(1766000000000001001, '驾驶舱查看', 1766000000000000101, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:dashboard:view',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001002, '驾驶舱导出', 1766000000000000101, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:dashboard:export', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：招聘需求 recruit:demand:list/query/add/edit/submit/pause/close/import/export ----
insert ignore into sys_menu values(1766000000000001101, '需求查询', 1766000000000000102, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:list',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001102, '需求详情', 1766000000000000102, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:query',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001103, '需求新增', 1766000000000000102, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001104, '需求编辑', 1766000000000000102, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001105, '需求提交', 1766000000000000102, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:submit', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001106, '需求暂停', 1766000000000000102, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:pause',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001107, '需求关闭', 1766000000000000102, 7, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:close',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001108, '需求导入', 1766000000000000102, 8, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:import', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001109, '需求导出', 1766000000000000102, 9, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:export', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：公司月度计划 recruit:plan:list/query/add/edit/confirm/close/export ----
insert ignore into sys_menu values(1766000000000001201, '计划查询', 1766000000000000103, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:list',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001202, '计划详情', 1766000000000000103, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:query',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001203, '计划新增', 1766000000000000103, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:add',     '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001204, '计划编辑', 1766000000000000103, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:edit',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001205, '计划确认', 1766000000000000103, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:confirm', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001206, '计划关闭', 1766000000000000103, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:close',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001207, '计划导出', 1766000000000000103, 7, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:export',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：月度结转中心 recruit:rollover:preview/execute/retry/detail ----
insert ignore into sys_menu values(1766000000000001301, '结转预览', 1766000000000000104, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:rollover:preview', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001302, '结转执行', 1766000000000000104, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:rollover:execute', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001303, '结转重试', 1766000000000000104, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:rollover:retry',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001304, '结转明细', 1766000000000000104, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:rollover:detail',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：岗位需求 recruit:job:list/query/add/edit/assign/close ----
insert ignore into sys_menu values(1766000000000001401, '岗位查询', 1766000000000000105, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:list',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001402, '岗位详情', 1766000000000000105, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:query',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001403, '岗位新增', 1766000000000000105, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001404, '岗位编辑', 1766000000000000105, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001405, '分配负责人', 1766000000000000105, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:assign', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001406, '岗位关闭', 1766000000000000105, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:close',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：候选人跟进 recruit:candidate:list/query/add/edit/transfer/stage/phone-view/export ----
insert ignore into sys_menu values(1766000000000001501, '候选人查询', 1766000000000000106, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:list',       '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001502, '候选人详情', 1766000000000000106, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:query',      '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001503, '候选人新增', 1766000000000000106, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:add',        '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001504, '候选人编辑', 1766000000000000106, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:edit',       '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001505, '候选人转移', 1766000000000000106, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:transfer',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001506, '阶段流转',   1766000000000000106, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:stage',      '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001507, '电话明文查看', 1766000000000000106, 7, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:phone-view', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资源级鉴权：查看电话明文并写审计');
insert ignore into sys_menu values(1766000000000001508, '候选人导出', 1766000000000000106, 8, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:export',     '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：候选人附件 recruit:attachment:upload/preview/download/delete ----
-- 挂载在「候选人跟进」下：设计文档 §12 候选人详情包含附件，§5.2 明确附件四权限
insert ignore into sys_menu values(1766000000000001509, '附件上传', 1766000000000000106, 9, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:attachment:upload',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001510, '附件预览', 1766000000000000106, 10, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:attachment:preview',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资源级鉴权：受控预览并写敏感操作审计');
insert ignore into sys_menu values(1766000000000001511, '附件下载', 1766000000000000106, 11, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:attachment:download', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资源级鉴权：受控下载并写敏感操作审计');
insert ignore into sys_menu values(1766000000000001512, '附件删除', 1766000000000000106, 12, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:attachment:delete',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '逻辑删除：集团招聘管理员亦不得物理删除');

-- ---- 按钮：面试管理 recruit:interview:list/schedule/feedback/cancel ----
insert ignore into sys_menu values(1766000000000001601, '面试查询', 1766000000000000107, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:interview:list',     '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001602, '面试安排', 1766000000000000107, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:interview:schedule', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001603, '面试反馈', 1766000000000000107, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:interview:feedback', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001604, '面试取消', 1766000000000000107, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:interview:cancel',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：背调与报到 recruit:background:list/add/edit/view-sensitive ----
insert ignore into sys_menu values(1766000000000001701, '背调查询', 1766000000000000108, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:background:list',           '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001702, '背调新增', 1766000000000000108, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:background:add',            '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001703, '背调编辑', 1766000000000000108, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:background:edit',           '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001704, '背调明细查看', 1766000000000000108, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:background:view-sensitive', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '高敏感：查看背调明细并写审计');

-- ---- 按钮：人才档案 talent:profile:list/query/add/edit/archive/phone-view/export ----
insert ignore into sys_menu values(1766000000000001801, '人才查询', 1766000000000000202, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:list',       '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001802, '人才详情', 1766000000000000202, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:query',      '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001803, '人才新增', 1766000000000000202, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:add',        '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001804, '人才编辑', 1766000000000000202, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:edit',       '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001805, '人才归档', 1766000000000000202, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:archive',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001806, '电话明文查看', 1766000000000000202, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:phone-view', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资源级鉴权：查看电话明文并写审计');
insert ignore into sys_menu values(1766000000000001807, '人才导出', 1766000000000000202, 7, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:export',     '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：人才池与分组 talent:pool:list/add/edit/member/share ----
insert ignore into sys_menu values(1766000000000001901, '人才池查询', 1766000000000000203, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:pool:list',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001902, '人才池新增', 1766000000000000203, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:pool:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001903, '人才池编辑', 1766000000000000203, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:pool:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001904, '池成员维护', 1766000000000000203, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:pool:member', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000001905, '人才池共享', 1766000000000000203, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:pool:share',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：简历中心 talent:resume:list/upload/download/parse/review/version ----
insert ignore into sys_menu values(1766000000000002001, '简历查询', 1766000000000000204, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:list',     '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002002, '简历上传', 1766000000000000204, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:upload',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002003, '简历下载', 1766000000000000204, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:download', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资源级鉴权：受控下载并写审计');
insert ignore into sys_menu values(1766000000000002004, '简历解析', 1766000000000000204, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:parse',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002005, '解析复核', 1766000000000000204, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:review',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002006, '简历版本', 1766000000000000204, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:version',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：重复人才治理 talent:duplicate:list/confirm/merge/ignore ----
insert ignore into sys_menu values(1766000000000002101, '重复查询', 1766000000000000205, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:duplicate:list',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002102, '重复确认', 1766000000000000205, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:duplicate:confirm', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002103, '人才合并', 1766000000000000205, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:duplicate:merge',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002104, '重复忽略', 1766000000000000205, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:duplicate:ignore',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：人才共享授权 talent:grant:list/add/revoke ----
insert ignore into sys_menu values(1766000000000002201, '授权查询', 1766000000000000206, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:grant:list',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002202, '授权新增', 1766000000000000206, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:grant:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002203, '授权撤销', 1766000000000000206, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:grant:revoke', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：招聘渠道 recruit:channel:list/query/add/edit ----
insert ignore into sys_menu values(1766000000000002301, '渠道查询', 1766000000000000109, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:channel:list',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002302, '渠道详情', 1766000000000000109, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:channel:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002303, '渠道新增', 1766000000000000109, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:channel:add',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002304, '渠道编辑', 1766000000000000109, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:channel:edit',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：同行信息 recruit:peer:list/query/add/edit ----
insert ignore into sys_menu values(1766000000000002401, '同行查询', 1766000000000000110, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:peer:list',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002402, '同行详情', 1766000000000000110, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:peer:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002403, '同行新增', 1766000000000000110, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:peer:add',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002404, '同行编辑', 1766000000000000110, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:peer:edit',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：招聘标准 recruit:standard:list/query/add/edit ----
insert ignore into sys_menu values(1766000000000002501, '标准查询', 1766000000000000111, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:standard:list',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002502, '标准详情', 1766000000000000111, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:standard:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002503, '标准新增', 1766000000000000111, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:standard:add',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002504, '标准编辑', 1766000000000000111, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:standard:edit',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：数据导入中心 recruit:import:template/upload/confirm/cancel/detail ----
insert ignore into sys_menu values(1766000000000002601, '导入模板', 1766000000000000112, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:import:template', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002602, '导入上传', 1766000000000000112, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:import:upload',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002603, '导入确认', 1766000000000000112, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:import:confirm',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002604, '导入取消', 1766000000000000112, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:import:cancel',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002605, '导入明细', 1766000000000000112, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:import:detail',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：敏感操作审计 recruit:audit:list/export ----
insert ignore into sys_menu values(1766000000000002701, '审计查询', 1766000000000000113, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:audit:list',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1766000000000002702, '审计导出', 1766000000000000113, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:audit:export', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ----------------------------
-- 四之二、**对既有行的字段修正**（必须用 update，不能用 insert ignore）
-- ----------------------------
-- 【为什么必须有这一段 —— 部署陷阱，勿删】
--   本脚本的 `insert ignore into sys_menu values(...)` 只能保证"行不存在时插入"，
--   **对已存在的行一律跳过**。因此凡是"修改某个已发布菜单的字段值"，
--   光改上面的 insert 语句**对已升级的库完全无效**——新装库生效、升级库静默保持旧值。
--   这类分叉极难排查（"我本地是好的"）。凡改动既有菜单行，**必须在此追加对应的 update**。
--   update 放在 insert 之后：新装库 insert 已写入正确值，update 为幂等空操作；升级库由 update 修正。
--
-- 修正内容：以下 5 个菜单的后端与前端页面尚未实现，须隐藏（visible='1'）。
--   原因与范围见 `hr_talent_menu.sql` 同名注释块。用户裁定：先隐藏，功能另行排期。
--   只改 visible，**不动 status**（status='1' 是"停用"，语义不同）与其它任何字段。
update sys_menu set visible = '1'
 where menu_id in (
   1766000000000000101,  -- 管理驾驶舱（阶段 4）
   1766000000000000109,  -- 招聘渠道（未分配缺口）
   1766000000000000110,  -- 同行信息（未分配缺口）
   1766000000000000111,  -- 招聘标准（未分配缺口）
   1766000000000000112   -- 数据导入中心（阶段 4）
 );

-- ----------------------------
-- 五、角色-菜单绑定（按设计文档 §6 的职责与限制分配）
-- column: role_id, menu_id
-- 约定：角色能看到的页面必授其一、二级目录，否则菜单树不显示；
--       只读角色只授 list/query/detail/detail-view 类权限点。
-- ----------------------------
-- 1) 平台管理员 hr_platform_admin：系统配置、账号、菜单和基础运维；
--    不承担招聘业务数据日常维护 → 只授一级目录 + 管理驾驶舱 + 招聘标准 + 审计（读）。
insert ignore into sys_role_menu values (1766100000000000001, 1766000000000000001);
insert ignore into sys_role_menu values (1766100000000000001, 1766000000000000101);
insert ignore into sys_role_menu values (1766100000000000001, 1766000000000001001);
insert ignore into sys_role_menu values (1766100000000000001, 1766000000000000111);
insert ignore into sys_role_menu values (1766100000000000001, 1766000000000002501);
insert ignore into sys_role_menu values (1766100000000000001, 1766000000000002502);
insert ignore into sys_role_menu values (1766100000000000001, 1766000000000000113);
insert ignore into sys_role_menu values (1766100000000000001, 1766000000000002701);

-- 2) 集团招聘管理员 hr_recruit_admin_group：集团全部招聘业务、标准、渠道、统计和审计。
--    不含人才管理子树（该子树归集团人才管理员）。
--    背调明细（recruit:background:view-sensitive）与候选人电话明文（recruit:candidate:phone-view）
--    属 §6 所述"高敏感资源级权限"：本角色作为集团级招聘负责人默认持有，但记录级仍受可见范围
--    （TalentScopeDomainService）与授权级别（summary/detail/attachment）约束，且每次访问强制写审计。
--    §6 的"不能查看其他公司的候选人明文和背调"由可见范围落地，不再靠"不授权限"实现；
--    公司招聘负责人（hr_company_recruit_owner）仍不授这两项。
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000001);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000101);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001001);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001002);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000102);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001101);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001102);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001103);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001104);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001105);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001106);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001107);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001108);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001109);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000103);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001201);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001202);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001203);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001204);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001205);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001206);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001207);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000104);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001301);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001302);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001303);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001304);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000105);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001401);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001402);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001403);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001404);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001405);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001406);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000106);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001501);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001502);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001503);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001504);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001505);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001506);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001507);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001508);
-- 候选人附件四权限：与候选人的 list/query/add/edit 授权角色保持一致（集团招聘管理员/公司招聘负责人/招聘专员）
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001509);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001510);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001511);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001512);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000107);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001601);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001602);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001603);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001604);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000108);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001701);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001702);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001703);
-- 背调明细查看：集团招聘管理员默认持有（记录级受可见范围约束，每次访问写审计）
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000001704);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000109);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002301);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002302);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002303);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002304);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000110);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002401);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002402);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002403);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002404);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000111);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002501);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002502);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002503);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002504);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000112);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002601);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002602);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002603);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002604);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002605);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000000113);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002701);
insert ignore into sys_role_menu values (1766100000000000002, 1766000000000002702);

-- 3) 集团人才管理员 hr_talent_admin_group：人才主档、人才池、标签、重复治理、共享授权和人才统计。
--    人才电话明文（talent:profile:phone-view）与简历下载（talent:resume:download）为独立登记的
--    资源级权限：默认授给集团人才管理员，但记录级仍受人才可见范围与共享授权级别约束，
--    且每次访问强制写审计（含被拒绝的访问）。
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000000001);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000000101);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001001);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001002);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000000201);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000000202);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001801);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001802);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001803);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001804);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001805);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001806);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001807);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000000203);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001901);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001902);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001903);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001904);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000001905);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000000204);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002001);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002002);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002003);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002004);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002005);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002006);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000000205);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002101);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002102);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002103);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002104);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000000206);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002201);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002202);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002203);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000000113);
insert ignore into sys_role_menu values (1766100000000000003, 1766000000000002701);

-- 4) 公司招聘负责人 hr_company_recruit_owner：本公司及授权部门的需求、岗位、候选人、面试、背调和报到。
--    不能查看其他公司的候选人明文和背调 → 不授 candidate:phone-view、background:view-sensitive、audit:*。
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000000001);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000000101);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001001);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001002);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000000102);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001101);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001102);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001103);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001104);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001105);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001107);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001109);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000000103);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001201);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001202);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001203);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001204);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001206);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001207);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000000104);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001301);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001303);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001304);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000000105);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001401);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001402);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001403);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001404);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001405);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001406);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000000106);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001501);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001502);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001503);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001504);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001505);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001506);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001508);
-- 候选人附件四权限：与候选人的 list/query/add/edit 授权角色保持一致
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001509);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001510);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001511);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001512);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000000107);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001601);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001602);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001603);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001604);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000000108);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001701);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001702);
insert ignore into sys_role_menu values (1766100000000000004, 1766000000000001703);

-- 5) 招聘专员 hr_recruiter：录入候选人、跟进、安排面试和上传附件。
--    导出、电话明文和背调需单独授权 → 不授导出/明文/背调/审计权限。
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000000001);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000000101);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001001);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000000102);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001101);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001102);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000000103);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001201);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001202);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000000105);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001401);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001402);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000000106);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001501);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001502);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001503);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001504);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001506);
-- 候选人附件四权限：与候选人的 list/query/add/edit 授权角色保持一致
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001509);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001510);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001511);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001512);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000000107);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001601);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001602);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001603);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000001604);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000000201);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000000204);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000002001);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000002002);
insert ignore into sys_role_menu values (1766100000000000005, 1766000000000002004);

-- 6) 用人部门负责人 hr_dept_owner：提交需求、查看进度、填写面试意见；不可浏览无关候选人。
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000000001);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000000101);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001001);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000000102);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001101);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001102);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001103);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001105);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000000103);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001201);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001202);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000000105);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001401);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001402);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000000106);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001501);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001502);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000000107);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001601);
insert ignore into sys_role_menu values (1766100000000000006, 1766000000000001603);

-- 7) 面试官 hr_interviewer：查看必要简历并提交面试反馈；不可批量导出。
insert ignore into sys_role_menu values (1766100000000000007, 1766000000000000001);
insert ignore into sys_role_menu values (1766100000000000007, 1766000000000000106);
insert ignore into sys_role_menu values (1766100000000000007, 1766000000000001502);
insert ignore into sys_role_menu values (1766100000000000007, 1766000000000000107);
insert ignore into sys_role_menu values (1766100000000000007, 1766000000000001601);
insert ignore into sys_role_menu values (1766100000000000007, 1766000000000001603);

-- 8) 审计查看者 hr_auditor：只读审计；默认不可下载候选人附件。
insert ignore into sys_role_menu values (1766100000000000008, 1766000000000000001);
insert ignore into sys_role_menu values (1766100000000000008, 1766000000000000113);
insert ignore into sys_role_menu values (1766100000000000008, 1766000000000002701);
insert ignore into sys_role_menu values (1766100000000000008, 1766000000000002702);

-- 9) 人才库查阅者 hr_talent_viewer：检索、查看脱敏人才摘要及授权附件；不可修改、导出或查看背调资料。
insert ignore into sys_role_menu values (1766100000000000009, 1766000000000000001);
insert ignore into sys_role_menu values (1766100000000000009, 1766000000000000201);
insert ignore into sys_role_menu values (1766100000000000009, 1766000000000000202);
insert ignore into sys_role_menu values (1766100000000000009, 1766000000000001801);
insert ignore into sys_role_menu values (1766100000000000009, 1766000000000001802);
insert ignore into sys_role_menu values (1766100000000000009, 1766000000000000203);
insert ignore into sys_role_menu values (1766100000000000009, 1766000000000001901);

-- ----------------------------
-- 六、自检（执行后人工核对，非必需）
-- ----------------------------
-- 字典：应返回 23 组 / 各类型编码值齐全
-- select dict_type, count(*) as value_cnt from sys_dict_data
--  where dict_type in (select dict_type from sys_dict_type where dict_id between 1766200000000000001 and 1766200000000000023)
--  group by dict_type order by dict_type;
-- 菜单：应返回 1 个一级目录（parent_id=0, menu_type='M'）
-- select menu_id, menu_name, parent_id, menu_type, path, component, perms from sys_menu
--  where menu_id between 1766000000000000001 and 1766000000000000113 order by menu_id;
-- 角色绑定：每个角色的菜单数
-- select role_id, count(*) from sys_role_menu
--  where role_id between 1766100000000000001 and 1766100000000000009 group by role_id;

