-- ============================================================================
-- 内容生产协同 · 阶段1A 建表脚本
-- ----------------------------------------------------------------------------
-- 依据：docs/content/00-SPEC-阶段1A-内容生产协同.md
-- 表前缀：cp_（人才库 tl_ / AI治理 aig_）
-- 主键：全局 idType=ASSIGN_ID（雪花），因此 DDL 不写 auto_increment
--
-- 设计红线（见 SPEC §0.1）：
--   1. 解析结果一律以 PENDING 落库，不存在「AI 直接写入既定事实」的路径；
--   2. 业务库只存文件引用（file_ref），不存文件本体。
-- ============================================================================

-- ----------------------------
-- 1、轻量产品/SKU
-- ----------------------------
drop table if exists cp_product;
create table cp_product (
    product_id      bigint(20)      not null                   comment '产品ID',
    product_code    varchar(64)     not null                   comment '产品编码（对业务唯一）',
    product_name    varchar(255)    not null                   comment '产品名称',
    sku_code        varchar(64)     default null               comment 'SKU编码（可空=产品级）',
    sku_name        varchar(255)    default null               comment 'SKU名称',
    category        varchar(64)     default null               comment '品类（首期试点：积木花）',
    brand           varchar(64)     default null               comment '品牌（如 趣往）',
    sub_category    varchar(64)     default null               comment '二级分类（如 解构花园-静态花）',
    product_image   varchar(500)    default null               comment '产品图引用（对象存储键或 URL；业务库不存文件本体）',
    main_push       varchar(500)    default null               comment '主推说明（原表该列常填售卖形态/口径说明）',
    product_manager varchar(64)     default null               comment '产品经理',
    size_spec       varchar(255)    default null               comment '尺寸规格（如 257.60*149.30；多形态用换行分隔）',
    price           decimal(12,2)   default null               comment '价格（元）',
    craft           varchar(255)    default null               comment '结构/工艺（如 UV+喷漆、喷漆+镀铬）',
    design_inspiration varchar(1000) default null              comment '设计灵感',
    version         varchar(32)     default null               comment '产品版本（用于影响面追溯）',
    status          char(1)         default '0'                comment '状态（0正常 1停用）',
    remark          varchar(500)    default null               comment '备注',
    del_flag        char(1)         default '0'                comment '删除标志（0存在 1删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    primary key (product_id),
    unique key uk_cp_product_code (product_code, sku_code),
    key idx_cp_product_name (product_name)
) engine=innodb comment = '内容生产-轻量产品SKU';

-- ----------------------------
-- 2、内容生产任务
-- ----------------------------
drop table if exists cp_task;
create table cp_task (
    task_id         bigint(20)      not null                   comment '任务ID',
    task_no         varchar(32)     not null                   comment '任务号（对外展示）',
    task_name       varchar(255)    not null                   comment '任务名称',
    deliverable_type varchar(32)    not null                   comment '交付类型（ECOM_DETAIL/MAIN_IMAGE/EXHIBITION/MANUAL/PACKAGE/VIDEO）',
    product_id      bigint(20)      default null               comment '产品ID',
    sku_code        varchar(64)     default null               comment 'SKU编码',
    deadline        datetime        default null               comment '截止时间',
    owner_id        bigint(20)      default null               comment '任务负责人（互动卡默认指派人）',
    owner_name      varchar(64)     default null               comment '任务负责人姓名',
    data_level      varchar(16)     not null default 'INTERNAL' comment '资料敏感级别（PUBLIC/INTERNAL/RESTRICTED，复用 aig_data_level）',
    allow_external  char(1)         not null default 'N'       comment '是否允许外部AI（Y/N）',
    status          varchar(32)     not null default 'DRAFT'   comment '任务状态（见 SPEC §3.2）',
    block_reason    varchar(500)    default null               comment '当前阻断原因（闸门写入）',
    parse_done_at   datetime        default null               comment '解析完成时间',
    remark          varchar(500)    default null               comment '备注',
    del_flag        char(1)         default '0'                comment '删除标志（0存在 1删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    primary key (task_id),
    unique key uk_cp_task_no (task_no),
    key idx_cp_task_status (status, del_flag),
    key idx_cp_task_owner (owner_id, status),
    key idx_cp_task_product (product_id)
) engine=innodb comment = '内容生产-任务';

-- ----------------------------
-- 3、任务附件
-- ----------------------------
drop table if exists cp_task_file;
create table cp_task_file (
    file_id         bigint(20)      not null                   comment '附件ID',
    task_id         bigint(20)      not null                   comment '任务ID',
    file_name       varchar(255)    not null                   comment '原始文件名',
    file_ext        varchar(16)     default null               comment '扩展名（小写）',
    file_size       bigint(20)      default null               comment '字节数',
    file_ref        varchar(500)    default null               comment '文件引用（对象存储键；业务库不存文件本体）',
    file_kind       varchar(16)     default null               comment '文件类型（EXCEL/WORD/PDF/IMAGE/VIDEO/DESIGN/OTHER）',
    source_type     varchar(16)     default 'UPLOAD'           comment '来源（UPLOAD上传/REFERENCE引用）',
    data_level      varchar(16)     default null               comment '该文件的数据等级（可高于任务等级）',
    parse_status    varchar(16)     not null default 'PENDING' comment '解析状态（PENDING/PARSING/DONE/FAILED/SKIPPED）',
    parse_message   varchar(500)    default null               comment '解析失败或跳过的可读原因',
    parsed_text_ref varchar(500)    default null               comment '抽取文本的引用（避免正文入库）',
    remark          varchar(500)    default null               comment '备注',
    del_flag        char(1)         default '0'                comment '删除标志（0存在 1删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    primary key (file_id),
    key idx_cp_task_file_task (task_id, del_flag),
    key idx_cp_task_file_parse (parse_status)
) engine=innodb comment = '内容生产-任务附件';

-- ----------------------------
-- 4、产品事实快照
--   同一 task+field_code 允许多行：多来源不同值即构成冲突，证据链不被覆盖
-- ----------------------------
drop table if exists cp_fact_snapshot;
create table cp_fact_snapshot (
    snapshot_id     bigint(20)      not null                   comment '快照行ID',
    task_id         bigint(20)      not null                   comment '任务ID',
    snapshot_version int(11)        not null default 1         comment '快照版本（每次确认递增）',
    field_code      varchar(64)     not null                   comment '事实字段编码',
    field_name      varchar(128)    default null               comment '事实字段名称',
    field_value     varchar(500)    default null               comment '字段值',
    unit            varchar(32)     default null               comment '单位',
    source_file_id  bigint(20)      default null               comment '来源附件ID',
    source_locator  varchar(255)    default null               comment '来源定位（如：产品参数表V2 第3行）',
    source_excerpt  varchar(500)    default null               comment '原文摘录（证据，用户可见）',
    confidence      decimal(5,2)    default null               comment '解析置信度 0-100',
    confirm_status  varchar(16)     not null default 'PENDING' comment '确认状态（PENDING/CONFIRMED/CONFLICT/REJECTED）',
    confirmed_by    bigint(20)      default null               comment '确认人',
    confirmed_at    datetime        default null               comment '确认时间',
    remark          varchar(500)    default null               comment '备注',
    del_flag        char(1)         default '0'                comment '删除标志（0存在 1删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    primary key (snapshot_id),
    key idx_cp_fact_task_field (task_id, field_code, del_flag),
    key idx_cp_fact_status (task_id, confirm_status)
) engine=innodb comment = '内容生产-产品事实快照';

-- ----------------------------
-- 5、闸门规则（表驱动，运营可改）
-- ----------------------------
drop table if exists cp_gate_rule;
create table cp_gate_rule (
    rule_id         bigint(20)      not null                   comment '规则ID',
    deliverable_type varchar(32)    not null                   comment '交付类型',
    field_code      varchar(64)     not null                   comment '要求确认的事实字段编码',
    field_name      varchar(128)    default null               comment '字段名称',
    gate_level      varchar(16)     not null                   comment '闸门等级（BLOCK强制阻断/CONDITION条件流转/NOTICE非阻断提醒）',
    require_present char(1)         not null default 'Y'       comment '是否必须存在（Y=无候选也算未满足）',
    enabled         char(1)         not null default '0'       comment '是否启用（0启用 1停用）',
    sort_no         int(11)         default 0                  comment '排序',
    remark          varchar(500)    default null               comment '备注',
    del_flag        char(1)         default '0'                comment '删除标志（0存在 1删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    primary key (rule_id),
    key idx_cp_gate_type (deliverable_type, enabled, del_flag)
) engine=innodb comment = '内容生产-闸门规则';

-- ----------------------------
-- 6、互动确认卡
-- ----------------------------
drop table if exists cp_interaction_card;
create table cp_interaction_card (
    card_id         bigint(20)      not null                   comment '互动卡ID',
    task_id         bigint(20)      not null                   comment '任务ID',
    card_type       varchar(16)     not null                   comment '卡片类型（MISSING缺料/CONFLICT冲突/APPROVAL审批/SUPPLEMENT补料/EXCEPTION例外）',
    field_code      varchar(64)     default null               comment '相关事实字段编码',
    title           varchar(255)    not null                   comment '一句话问题',
    question        varchar(1000)   default null               comment '问题详细描述',
    evidence_json   text                                       comment '证据来源（文件/定位/摘录，多来源）',
    impact_json     text                                       comment '影响对象（交付物/页面/包装/说明书）',
    options_json    text                                       comment '处理选项',
    gate_level      varchar(16)     default null               comment '闸门等级（BLOCK/CONDITION/NOTICE）',
    blocking        char(1)         default 'N'                comment '是否阻断（Y/N，冗余自 gate_level=BLOCK 便于筛选）',
    assignee_id     bigint(20)      default null               comment '责任人（任务指定）',
    assignee_name   varchar(64)     default null               comment '责任人姓名',
    due_at          datetime        default null               comment '截止时间',
    status          varchar(16)     not null default 'PENDING' comment '状态（PENDING/RESOLVED/BLOCKED/CLOSED）',
    resolved_value  varchar(500)    default null               comment '确认后的值',
    resolved_option varchar(64)     default null               comment '所选选项',
    resolved_by     bigint(20)      default null               comment '处理人',
    resolved_at     datetime        default null               comment '处理时间',
    remark          varchar(500)    default null               comment '备注',
    del_flag        char(1)         default '0'                comment '删除标志（0存在 1删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    primary key (card_id),
    key idx_cp_card_task (task_id, status, del_flag),
    key idx_cp_card_assignee (assignee_id, status),
    key idx_cp_card_field (task_id, field_code)
) engine=innodb comment = '内容生产-互动确认卡';

-- ----------------------------
-- 7、设计开工包
-- ----------------------------
drop table if exists cp_work_package;
create table cp_work_package (
    package_id      bigint(20)      not null                   comment '开工包ID',
    task_id         bigint(20)      not null                   comment '任务ID',
    snapshot_version int(11)        not null                   comment '签发时冻结的事实版本',
    content_json    longtext                                   comment '开工包全文（结构见 SPEC §4.5）',
    status          varchar(16)     not null default 'DRAFT'   comment '状态（DRAFT草稿/ISSUED已签发）',
    generated_by    bigint(20)      default null               comment '生成人',
    generated_at    datetime        default null               comment '生成时间',
    issued_by       bigint(20)      default null               comment '签发人',
    issued_at       datetime        default null               comment '签发时间',
    remark          varchar(500)    default null               comment '备注',
    del_flag        char(1)         default '0'                comment '删除标志（0存在 1删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    primary key (package_id),
    key idx_cp_pkg_task (task_id, del_flag)
) engine=innodb comment = '内容生产-设计开工包';

-- ----------------------------
-- 8、异步作业（应用内执行器，不引入 SnailJob）
-- ----------------------------
drop table if exists cp_async_job;
create table cp_async_job (
    job_id          bigint(20)      not null                   comment '作业ID',
    task_id         bigint(20)      not null                   comment '任务ID',
    job_type        varchar(16)     not null                   comment '作业类型（PARSE/PRECHECK/PACKAGE）',
    status          varchar(16)     not null default 'QUEUED'  comment '状态（QUEUED/RUNNING/SUCCESS/FAILED）',
    progress        int(11)         default 0                  comment '进度 0-100',
    message         varchar(500)    default null               comment '失败原因（用户可读）',
    started_at      datetime        default null               comment '开始时间',
    finished_at     datetime        default null               comment '结束时间',
    primary key (job_id),
    key idx_cp_job_task (task_id, job_type),
    key idx_cp_job_status (status)
) engine=innodb comment = '内容生产-异步作业';

-- ============================================================================
-- 闸门规则种子
-- 依据设计文档 §8.2「不同交付物的强制项」。
-- 只灌「电商详情图」与「展会宣传图」两类作为首批试点；其余交付类型按同一模式后续补。
-- 三个等级都要有样本，便于验证三种流转行为（BLOCK/CONDITION/NOTICE）。
-- ============================================================================
delete from cp_gate_rule where deliverable_type in ('ECOM_DETAIL','EXHIBITION');

-- 说明：此处刻意使用**显式列名**而非位置插入。位置插入漏列时 MySQL 只会报
-- 「Column count doesn't match」，很难一眼看出错了哪列；显式列名可自解释且抗后续加列。
-- 电商详情图：强制阻断项（设计文档 §8.2 原文：SKU、产品名称、主体版本、颜色、数量、参数、包装版本）
insert into cp_gate_rule
  (rule_id, deliverable_type, field_code, field_name, gate_level, require_present, enabled, sort_no, remark,
   del_flag, create_dept, create_by, create_time, update_by, update_time)
values
  (1765100000000000001, 'ECOM_DETAIL', 'sku_code',        'SKU',          'BLOCK',     'Y', '0', 1, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  (1765100000000000002, 'ECOM_DETAIL', 'product_name',    '产品名称',     'BLOCK',     'Y', '0', 2, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  (1765100000000000003, 'ECOM_DETAIL', 'main_version',    '主体版本',     'BLOCK',     'Y', '0', 3, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  (1765100000000000004, 'ECOM_DETAIL', 'color',           '颜色',         'BLOCK',     'Y', '0', 4, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  (1765100000000000005, 'ECOM_DETAIL', 'quantity',        '数量',         'BLOCK',     'Y', '0', 5, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  (1765100000000000006, 'ECOM_DETAIL', 'spec_params',     '参数',         'BLOCK',     'Y', '0', 6, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  (1765100000000000007, 'ECOM_DETAIL', 'package_version', '包装版本',     'BLOCK',     'Y', '0', 7, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  -- 条件流转项：可以开工，但必须补齐或确定替代方案
  (1765100000000000008, 'ECOM_DETAIL', 'reference_image', '参考图',       'CONDITION', 'N', '0', 8, '缺失时可选检索/补拍/主体合成/本次不需要', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  -- 非阻断提醒项：只影响创意与效率
  (1765100000000000009, 'ECOM_DETAIL', 'brand_tone',      '品牌调性说明', 'NOTICE',    'N', '0', 9, '不影响事实正确性', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  -- 展会宣传图（设计文档 §8.2：主产品版本、品牌主张、尺寸、分辨率、播放/印刷要求）
  (1765100000000000011, 'EXHIBITION', 'main_version',   '主产品版本',    'BLOCK',     'Y', '0', 1, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  (1765100000000000012, 'EXHIBITION', 'brand_claim',    '品牌主张',      'BLOCK',     'Y', '0', 2, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  (1765100000000000013, 'EXHIBITION', 'output_size',    '尺寸',          'BLOCK',     'Y', '0', 3, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  (1765100000000000014, 'EXHIBITION', 'resolution',     '分辨率',        'BLOCK',     'Y', '0', 4, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  (1765100000000000015, 'EXHIBITION', 'print_play_req', '播放/印刷要求', 'BLOCK',     'Y', '0', 5, '强制项', '0', 1761000000000000103, 1761100000000000001, now(), null, null),
  (1765100000000000016, 'EXHIBITION', 'reference_image', '参考图',       'CONDITION', 'N', '0', 6, '缺失时可选替代方案', '0', 1761000000000000103, 1761100000000000001, now(), null, null);

-- 自检
select deliverable_type, gate_level, count(1) as cnt
  from cp_gate_rule where del_flag = '0'
 group by deliverable_type, gate_level
 order by deliverable_type, gate_level;
