-- ------------------------------------------------------------------
-- AI 治理：场景强制绑定（设计 §4.4 路由算法第 4 步）+ 审计补场景列
--
-- 背景（为什么必须补这一块）：
--   设计 §4.4 第 4 步写明「若场景强制绑定 Provider，例如企业设计平台编辑会话，
--   则仅保留指定 Provider」。仓库里此前**完全没有 scenario 这个概念**：
--   路由只按「能力 × 数据等级」判，同一次调用不可能表达「这个场景只准走某家」。
--   后果不是报错，而是静默：路由按优先级自由选，选出的那家**看起来完全正常**，
--   直到有人问「这个场景为什么没走约定的供应商」才发现从来没被约束过。
--
-- 为什么单独一张表，而不是给 aig_route_policy 加列：
--   策略表是「能力 × 数据等级」的治理口径（能否外发、是否要审批、无模型时是否转人工），
--   唯一键就是这两列。把场景塞进去，要么破坏唯一键，要么为每个场景复制一整行并
--   把 allow_external 等治理口径重述一遍——只要有一次漏填或填错，该场景的治理判定
--   就静默变成另一个结论（而「无策略即拒绝」意味着连「没配」都不会报错，只会拒）。
--
-- 语义边界（重要，决定了本表只收紧、不放宽）：
--   绑定只被路由引擎用于**收窄**候选——把不属于指定供应商的候选筛掉。
--   它**不会**让任何被治理策略/数据等级/生命周期/健康状态排除的模型变得可用。
--   因此绑定配错的最坏后果是「该场景无模型可用 → 按策略转人工或拒绝」，
--   不可能变成「数据被发给了不该发的地方」。
--
-- 幂等：本脚本可安全重复执行。
--   建表用 CREATE TABLE IF NOT EXISTS；审计补列用 information_schema 判断后
--   动态执行 DDL（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS），写法与
--   aig_model_capability_tags.sql 一致。
--
-- 兼容性：只新增表与可空列，不改既有列、不动索引与唯一键；既有审计行的
--   scenario_code 为 NULL，含义是「该次调用未带场景」——与「带了场景但没匹配到
--   绑定」不是一回事，后者会在 policy_hit 里写明。
-- ------------------------------------------------------------------

-- 1) 场景强制绑定表
CREATE TABLE IF NOT EXISTS aig_route_scenario_binding (
    bind_id          bigint(20)      not null                   comment '绑定ID',
    scenario_code    varchar(64)     not null                   comment '场景编码（LONG_PAGE/POSTER/MULTI_IMAGE 等）',
    capability_code  varchar(64)     not null                   comment '能力编码',
    provider_id      bigint(20)      not null                   comment '强制使用的供应商ID（sai_model_provider.id）',
    priority         int(11)         default 0                  comment '同一场景×能力下多个供应商时的优先序（升序，仅用于稳定排序）',
    status           char(1)         default '0'                comment '状态（0正常 1停用）',
    del_flag         char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept      bigint(20)      default null               comment '创建部门',
    create_by        bigint(20)      default null               comment '创建者',
    create_time      datetime                                   comment '创建时间',
    update_by        bigint(20)      default null               comment '更新者',
    update_time      datetime                                   comment '更新时间',
    remark           varchar(500)    default null               comment '备注（说明为什么钉死这家，便于事后复核）',
    primary key (bind_id),
    unique key uk_aig_scenario_cap_provider (scenario_code, capability_code, provider_id, del_flag),
    key idx_aig_scenario_lookup (scenario_code, capability_code, status, del_flag)
) engine=innodb comment = 'AI场景强制绑定表（只收窄候选，不放宽治理口径）';

-- 2) 审计补场景列
SET @ddl := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aig_invocation_audit'
        AND COLUMN_NAME = 'scenario_code'),
    'SELECT ''aig_invocation_audit.scenario_code already exists'' AS note',
    'ALTER TABLE aig_invocation_audit ADD COLUMN scenario_code VARCHAR(64) NULL COMMENT ''本次场景编码（为空表示未做场景收窄）'' AFTER data_level')
);
PREPARE hotter_ddl FROM @ddl; EXECUTE hotter_ddl; DEALLOCATE PREPARE hotter_ddl;

-- 3) 核对
SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_route_scenario_binding';

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'aig_invocation_audit'
   AND COLUMN_NAME = 'scenario_code';

SELECT COUNT(*) AS total_bindings,
       SUM(CASE WHEN status = '0' AND del_flag = '0' THEN 1 ELSE 0 END) AS active_bindings
  FROM aig_route_scenario_binding;

-- 4) 权限与菜单（路由策略页面下的功能权限；与 aig:route:edit 分开，
--    因为「能开外发」与「能钉首选供应商」不是同一件事）
INSERT INTO sys_menu
SELECT 1763000000000001305, '场景强制绑定', 1763000000000000103, 5, '', '', '', 'N', 'Y', 'F', '0', '0',
       'aig:route:binding', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null,
       '维护「场景 × 能力 → 指定供应商」的强制绑定（只收窄候选，不放宽治理口径）'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1763000000000001305);

SELECT menu_id, menu_name, perms FROM sys_menu WHERE perms = 'aig:route:binding';

SELECT 'AIG_ROUTE_SCENARIO_BINDING_DONE' AS marker;
