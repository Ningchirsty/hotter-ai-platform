-- =====================================================================
-- V2 服务身份：机器令牌（ADR-002 追加前置 / Execution Contract §7.3）
-- =====================================================================
-- 为什么需要这张表（原文依据）：
--   ADR-002：「必须先补"服务身份/机器令牌"。已核实：现有 /aigov/invoke/{capabilityCode}
--   只认 Sa-Token 会话，没有机器身份。Python/外部 Agent 若不解决身份，就只能拿到一个
--   "人"的会话（不可接受）或绕过治理（更不可接受）。」
--   Execution Contract v1 §7.3：「契约假定调用方有稳定的机器身份（principal 的来源之一）。
--   该能力目前不存在，需在阶段 1 之前补。」
--
-- 三条设计约束（决定了下述列的存在）：
--   ① **只存哈希**：库里绝不出现明文令牌 —— token_hash 是 SHA-256 十六进制；
--      token_prefix 只用于"在人面前指认是哪一把"（熵不足，不能用于认证）。
--   ② **最小权限**：scopes 为空 = 该身份不被授予任何操作（默认拒绝），
--      而不是"默认给全部"。审计与权限判定都读这一列。
--   ③ **可吊销 + 可过期**：enabled/expires_at/status 三个维度独立，
--      吊销不需要删行（保留审计痕迹）。
-- =====================================================================

create table if not exists aig_service_token (
    token_id      bigint       not null                   comment '服务令牌ID',
    name          varchar(64)  not null                   comment '服务名（唯一），principal 记为 service:<name>',
    token_hash    char(64)     not null                   comment '令牌 SHA-256（只存哈希，绝不存明文）',
    token_prefix  varchar(16)  not null                   comment '令牌前缀，仅用于人工指认（熵不足，不可用于认证）',
    scopes        varchar(500) default null               comment '授权范围：逗号分隔的权限码；空=不授予任何操作（默认拒绝）',
    expires_at    datetime     default null               comment '到期时间（空=不过期）',
    last_used_at  datetime     default null               comment '最近一次成功认证时间',
    last_used_ip  varchar(64)  default null               comment '最近一次成功认证来源IP',
    status        char(1)      default '0'                comment '状态（0正常 1停用）：**唯一的启停开关**',
    del_flag      char(1)      default '0'                comment '删除标志（0存在 1删除）',
    create_dept   bigint       default null               comment '创建部门',
    create_by     bigint       default null               comment '创建者',
    create_time   datetime     default null               comment '创建时间',
    update_by     bigint       default null               comment '更新者',
    update_time   datetime     default null               comment '更新时间',
    remark        varchar(500) default null               comment '备注：这把令牌给谁用、为什么需要这些 scope',
    primary key (token_id),
    unique key uk_aig_service_token_hash (token_hash),
    key idx_aig_service_token_name (name),
    key idx_aig_service_token_status (status)
) engine = innodb comment = 'V2 服务身份：机器令牌（ADR-002 前置）';

-- ① **只有一个启停开关**：起初我同时设计了 enabled(Y/N) 与 status(0/1)，那是同一件事的两个事实源
--    （本仓已经因为"两处口径不一致"吃过亏，见 ADR-008）。这里只保留 status，与其它 aigov 表一致。
-- ② name 的唯一性刻意**不建唯一索引**：本仓多张表用 (name, del_flag) 做唯一键，
--    其副作用是"同名行删两次会撞键"。这里改为"服务层按 del_flag='0' 校验唯一"，
--    语义更直白（软删后允许复用同名）。
