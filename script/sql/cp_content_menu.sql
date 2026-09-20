-- ============================================================================
-- 内容生产协同 · 阶段1A 菜单、权限、字典
-- ----------------------------------------------------------------------------
-- 依据：docs/content/00-SPEC-阶段1A-内容生产协同.md §7
-- 挂载：业务应用（1764000000000000003）→ 内容生产协同
-- ID 段：菜单 1765* / 角色 17651* / 字典类型 17652* / 字典数据 17653*
--
-- 幂等：全部使用 insert ignore（按主键/唯一键判重），可安全重复执行。
--       —— aigov 的菜单脚本用的是普通 insert，发布后无法重跑，只能再补迁移脚本；
--          本脚本从一开始就避免该问题。
-- 图标：icon 全部取自 frontend/src/assets/icons/svg 中**真实存在**的名字
--       （已逐个核对；不存在的图标名会渲染成空白，main 侧已踩过该坑）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、字典类型
-- ---------------------------------------------------------------------------
insert ignore into sys_dict_type values(1765200000000000001, '内容交付类型',   'cp_deliverable_type', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '电商详情图/主图SKU图/展会宣传图/说明书/包装/视频内容');
insert ignore into sys_dict_type values(1765200000000000002, '内容任务状态',   'cp_task_status',      1761000000000000103, 1761100000000000001, sysdate(), null, null, '阶段1A 可达：草稿→解析中→待确认→条件开工/可开工');
insert ignore into sys_dict_type values(1765200000000000003, '互动卡类型',     'cp_card_type',        1761000000000000103, 1761100000000000001, sysdate(), null, null, '缺料/冲突/审批/补料/例外');
insert ignore into sys_dict_type values(1765200000000000004, '互动卡状态',     'cp_card_status',      1761000000000000103, 1761100000000000001, sysdate(), null, null, '待处理/已处理/暂不确认并阻断/已关闭');
insert ignore into sys_dict_type values(1765200000000000005, '闸门等级',       'cp_gate_level',       1761000000000000103, 1761100000000000001, sysdate(), null, null, '强制阻断/条件流转/非阻断提醒');
insert ignore into sys_dict_type values(1765200000000000006, '资料文件类型',   'cp_file_kind',        1761000000000000103, 1761100000000000001, sysdate(), null, null, 'Excel/Word/PDF/图片/视频/设计稿/其他');
insert ignore into sys_dict_type values(1765200000000000007, '资料解析状态',   'cp_parse_status',     1761000000000000103, 1761100000000000001, sysdate(), null, null, '待解析/解析中/已完成/失败/已跳过');

-- ---------------------------------------------------------------------------
-- 二、字典数据
-- ---------------------------------------------------------------------------
-- 交付类型
insert ignore into sys_dict_data values(1765300000000000101, 1, '电商详情图',   'ECOM_DETAIL', 'cp_deliverable_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '首期试点品类：积木花');
insert ignore into sys_dict_data values(1765300000000000102, 2, '主图/SKU图',  'MAIN_IMAGE',  'cp_deliverable_type', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000103, 3, '展会宣传图',   'EXHIBITION',  'cp_deliverable_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000104, 4, '说明书',       'MANUAL',      'cp_deliverable_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '不得虚构步骤');
insert ignore into sys_dict_data values(1765300000000000105, 5, '包装',         'PACKAGE',     'cp_deliverable_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000106, 6, '视频内容',     'VIDEO',       'cp_deliverable_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '阶段1A 不处理');
-- 任务状态
insert ignore into sys_dict_data values(1765300000000000201, 1, '草稿',         'DRAFT',             'cp_task_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000202, 2, '解析中',       'PARSING',           'cp_task_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000203, 3, '待确认/待补料','PENDING_CONFIRM',   'cp_task_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '存在未解决的强制阻断项');
insert ignore into sys_dict_data values(1765300000000000204, 4, '条件开工',     'CONDITIONAL_READY', 'cp_task_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '强制项已满足，仍有条件项待补齐');
insert ignore into sys_dict_data values(1765300000000000205, 5, '可开工',       'READY',             'cp_task_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
-- 互动卡类型
insert ignore into sys_dict_data values(1765300000000000301, 1, '缺料',   'MISSING',    'cp_card_type', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000302, 2, '冲突',   'CONFLICT',   'cp_card_type', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '同一字段多个来源取值不一致');
insert ignore into sys_dict_data values(1765300000000000303, 3, '审批',   'APPROVAL',   'cp_card_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000304, 4, '补料',   'SUPPLEMENT', 'cp_card_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000305, 5, '例外',   'EXCEPTION',  'cp_card_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
-- 互动卡状态
insert ignore into sys_dict_data values(1765300000000000401, 1, '待处理',           'PENDING',  'cp_card_status', '', 'warning', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000402, 2, '已处理',           'RESOLVED', 'cp_card_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000403, 3, '暂不确认并阻断',   'BLOCKED',  'cp_card_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '显式选择阻断，任务不得流转');
insert ignore into sys_dict_data values(1765300000000000404, 4, '已关闭',           'CLOSED',   'cp_card_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
-- 闸门等级
insert ignore into sys_dict_data values(1765300000000000501, 1, '强制阻断',     'BLOCK',     'cp_gate_level', '', 'danger',  'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '不确认将导致产品错误、合规风险或无法制作');
insert ignore into sys_dict_data values(1765300000000000502, 2, '条件流转',     'CONDITION', 'cp_gate_level', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '当前可开始，但必须补齐或确定替代方案');
insert ignore into sys_dict_data values(1765300000000000503, 3, '非阻断提醒',   'NOTICE',    'cp_gate_level', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '不影响事实正确性');
-- 文件类型
insert ignore into sys_dict_data values(1765300000000000601, 1, 'Excel',  'EXCEL',  'cp_file_kind', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000602, 2, 'Word',   'WORD',   'cp_file_kind', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000603, 3, 'PDF',    'PDF',    'cp_file_kind', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000604, 4, '图片',   'IMAGE',  'cp_file_kind', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '阶段1A 仅归档，不做 OCR');
insert ignore into sys_dict_data values(1765300000000000605, 5, '视频',   'VIDEO',  'cp_file_kind', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000606, 6, '设计稿', 'DESIGN', 'cp_file_kind', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000607, 7, '其他',   'OTHER',  'cp_file_kind', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
-- 解析状态
insert ignore into sys_dict_data values(1765300000000000701, 1, '待解析', 'PENDING', 'cp_parse_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000702, 2, '解析中', 'PARSING', 'cp_parse_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000703, 3, '已完成', 'DONE',    'cp_parse_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000704, 4, '失败',   'FAILED',  'cp_parse_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_dict_data values(1765300000000000705, 5, '已跳过', 'SKIPPED', 'cp_parse_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '图片/旧版 .doc 等本地无法抽取的类型');

-- ---------------------------------------------------------------------------
-- 三、菜单（挂到「业务应用」1764000000000000003 下）
-- ---------------------------------------------------------------------------
insert ignore into sys_menu values(1765000000000000001, '内容生产协同', 1764000000000000003, 2, 'content', null, '', 'N', 'Y', 'M', '0', '0', '', 'documentation', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '按任务组织的内容生产协同：发现问题→向对的人提问→控制流转→出开工包');

-- 内容任务
insert ignore into sys_menu values(1765000000000000101, '内容任务', 1765000000000000001, 1, 'task', 'content/task/index', '', 'N', 'Y', 'C', '0', '0', 'content:task:list', 'list', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '任务的建立、资料上传、解析与预检');
insert ignore into sys_menu values(1765000000000001101, '任务查询', 1765000000000000101, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:task:query',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1765000000000001102, '任务新增', 1765000000000000101, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:task:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1765000000000001103, '任务编辑', 1765000000000000101, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:task:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '含上传附件、触发解析、触发预检、重算闸门');
insert ignore into sys_menu values(1765000000000001104, '任务删除', 1765000000000000101, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:task:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- 互动确认卡
insert ignore into sys_menu values(1765000000000000102, '互动确认卡', 1765000000000000001, 2, 'card', 'content/card/index', '', 'N', 'Y', 'C', '0', '0', 'content:card:list', 'message', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '按「待我确认」聚合，每张卡带来源、影响、责任人与截止');
insert ignore into sys_menu values(1765000000000001201, '卡片处理', 1765000000000000102, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:card:handle', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '确认值/选项，或显式选择阻断');

-- 设计开工包
insert ignore into sys_menu values(1765000000000000103, '设计开工包', 1765000000000000001, 3, 'workPackage', 'content/workPackage/index', '', 'N', 'Y', 'C', '0', '0', 'content:package:list', 'form', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已确认事实快照 + 缺口与替代 + 允许的 AI 动作 + 输出规格');
insert ignore into sys_menu values(1765000000000001301, '生成开工包', 1765000000000000103, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:package:generate', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1765000000000001302, '签发开工包', 1765000000000000103, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:package:issue',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- 产品与SKU
insert ignore into sys_menu values(1765000000000000104, '产品与SKU', 1765000000000000001, 4, 'product', 'content/product/index', '', 'N', 'Y', 'C', '0', '0', 'content:product:list', 'shopping', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '轻量产品/SKU，用于事实快照与版本影响面追溯');
insert ignore into sys_menu values(1765000000000001401, '产品查询', 1765000000000000104, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:product:query',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1765000000000001402, '产品新增', 1765000000000000104, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:product:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1765000000000001403, '产品编辑', 1765000000000000104, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:product:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1765000000000001404, '产品删除', 1765000000000000104, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:product:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- 闸门规则
insert ignore into sys_menu values(1765000000000000105, '闸门规则', 1765000000000000001, 5, 'gateRule', 'content/gateRule/index', '', 'N', 'Y', 'C', '0', '0', 'content:gateRule:list', 'lock', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '表驱动：各交付类型的强制阻断/条件流转/非阻断提醒项，运营可改');
insert ignore into sys_menu values(1765000000000001501, '规则查询', 1765000000000000105, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:gateRule:query',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1765000000000001502, '规则新增', 1765000000000000105, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:gateRule:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1765000000000001503, '规则编辑', 1765000000000000105, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:gateRule:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert ignore into sys_menu values(1765000000000001504, '规则删除', 1765000000000000105, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'content:gateRule:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---------------------------------------------------------------------------
-- 四、角色
-- ---------------------------------------------------------------------------
insert ignore into sys_role values(1765100000000000001, '内容生产管理员', 'content_admin',  30, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '内容生产协同的全部权限，含闸门规则维护');
insert ignore into sys_role values(1765100000000000002, '内容生产人员',   'content_member', 31, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '任务与互动卡处理，不含删除与闸门规则维护');

-- ---------------------------------------------------------------------------
-- 五、角色授权
-- ---------------------------------------------------------------------------
-- 内容生产管理员：全部
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000000001);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000000101);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001101);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001102);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001103);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001104);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000000102);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001201);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000000103);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001301);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001302);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000000104);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001401);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001402);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001403);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001404);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000000105);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001501);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001502);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001503);
insert ignore into sys_role_menu values (1765100000000000001, 1765000000000001504);

-- 内容生产人员：任务（无删除）+ 卡片处理 + 开工包（可生成，不可签发）+ 只读产品与规则
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000000001);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000000101);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000001101);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000001102);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000001103);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000000102);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000001201);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000000103);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000001301);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000000104);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000001401);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000000105);
insert ignore into sys_role_menu values (1765100000000000002, 1765000000000001501);

-- ---------------------------------------------------------------------------
-- 六、自检
-- ---------------------------------------------------------------------------
select '菜单' as item, count(1) as cnt from sys_menu where menu_id between 1765000000000000001 and 1765000000000001599;
select '字典类型' as item, count(1) as cnt from sys_dict_type where dict_type like 'cp\_%';
select '字典数据' as item, count(1) as cnt from sys_dict_data where dict_type like 'cp\_%';
select r.role_key, count(1) as 授权数 from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
 where r.role_key in ('content_admin','content_member')
 group by r.role_key;
