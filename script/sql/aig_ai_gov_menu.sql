-- ----------------------------------------------------------------------------
-- AI 模型能力接入与配置管控 —— 阶段1 菜单 / 角色 / 字典
-- 依赖：script/sql/ry_vue.sql（平台基础）、script/sql/aig_ai_gov.sql（治理表）
-- ID 段：菜单 1763*、角色 17631*、字典 17632*（1761/1762/9200 已被占用）
-- 设计依据：§2.2 四层分权、§9.1 菜单设计、§9.2 关键配置页面
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 一、字典类型
-- ----------------------------
insert into sys_dict_type values(1763200000000000001, 'AI数据等级',     'aig_data_level',      1761000000000000103, 1761100000000000001, sysdate(), null, null, '公开/内部/限制');
insert into sys_dict_type values(1763200000000000002, 'AI模型可用状态', 'aig_lifecycle_status',1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选→试验→灰度→生产；暂停/退役');
insert into sys_dict_type values(1763200000000000003, 'AI部署类型',     'aig_deployment_type', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本地/集团共享/外部企业服务/外部API');
insert into sys_dict_type values(1763200000000000004, 'AI数据策略',     'aig_data_policy',     1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅本地/本地优先/允许外部');
insert into sys_dict_type values(1763200000000000005, 'AI审计等级',     'aig_audit_level',     1761000000000000103, 1761100000000000001, sysdate(), null, null, '摘要/完整输出/仅哈希');
insert into sys_dict_type values(1763200000000000006, 'AI绑定用途',     'aig_usage_type',      1761000000000000103, 1761100000000000001, sysdate(), null, null, '主选/备选/灰度');

-- ----------------------------
-- 二、字典数据
-- ----------------------------
-- 数据等级（§6.2）
insert into sys_dict_data values(1763300000000000001, 1, '公开',   'PUBLIC',     'aig_data_level', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已发布文案、公共参考、公开产品信息');
insert into sys_dict_data values(1763300000000000002, 2, '内部',   'INTERNAL',   'aig_data_level', '', 'warning', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未公开 Brief、内部创意、一般设计附件');
insert into sys_dict_data values(1763300000000000003, 3, '限制',   'RESTRICTED', 'aig_data_level', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未发布产品结构、源文件、客户资料、人才个人资料；默认禁止外发');
-- 生命周期（§8.3）
insert into sys_dict_data values(1763300000000000011, 1, '候选', 'CANDIDATE',  'aig_lifecycle_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '技术测试中，不可用于业务任务');
insert into sys_dict_data values(1763300000000000012, 2, '试验', 'TRIAL',      'aig_lifecycle_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '限定人员、限定公开或模拟数据使用');
insert into sys_dict_data values(1763300000000000013, 3, '灰度', 'GRAY',       'aig_lifecycle_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '限定业务能力、项目和额度使用');
insert into sys_dict_data values(1763300000000000014, 4, '生产', 'PRODUCTION', 'aig_lifecycle_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已完成技术、安全、业务和成本审批');
insert into sys_dict_data values(1763300000000000015, 5, '暂停', 'SUSPENDED',  'aig_lifecycle_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '暂时禁止新调用，保留历史审计');
insert into sys_dict_data values(1763300000000000016, 6, '退役', 'RETIRED',    'aig_lifecycle_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '停止调用，迁移到备选模型');
-- 部署类型（§4.2）
insert into sys_dict_data values(1763300000000000021, 1, '本地私有',     'LOCAL',               'aig_deployment_type', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '公司批准的私有网络或集团共享可信环境');
insert into sys_dict_data values(1763300000000000022, 2, '集团共享',     'GROUP',               'aig_deployment_type', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '集团层面共享的可信环境');
insert into sys_dict_data values(1763300000000000023, 3, '外部企业服务', 'EXTERNAL_ENTERPRISE', 'aig_deployment_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已通过企业准入的外部服务');
insert into sys_dict_data values(1763300000000000024, 4, '外部API',      'EXTERNAL_API',        'aig_deployment_type', '', 'danger',  'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '公网可访问的外部模型 API');
-- 数据策略
insert into sys_dict_data values(1763300000000000031, 1, '仅本地',   'LOCAL_ONLY',       'aig_data_policy', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '限制级资料默认策略');
insert into sys_dict_data values(1763300000000000032, 2, '本地优先', 'LOCAL_FIRST',      'aig_data_policy', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '内部资料默认策略');
insert into sys_dict_data values(1763300000000000033, 3, '允许外部', 'EXTERNAL_ALLOWED', 'aig_data_policy', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '公开资料可用经批准的外部模型');
-- 审计等级
insert into sys_dict_data values(1763300000000000041, 1, '摘要',     'SUMMARY',   'aig_audit_level', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '只保留输入摘要与输出引用');
insert into sys_dict_data values(1763300000000000042, 2, '完整输出', 'FULL',      'aig_audit_level', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '保留完整输出（受留存策略约束）');
insert into sys_dict_data values(1763300000000000043, 3, '仅哈希',   'HASH_ONLY', 'aig_audit_level', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '只保留输入哈希');
-- 绑定用途
insert into sys_dict_data values(1763300000000000051, 1, '主选', 'PRIMARY',  'aig_usage_type', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1763300000000000052, 2, '备选', 'FALLBACK', 'aig_usage_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '主选不可用时使用');
insert into sys_dict_data values(1763300000000000053, 3, '灰度', 'GRAY',     'aig_usage_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '限定范围灰度');

-- ----------------------------
-- 三、角色（§2.2 配置分权）
-- ----------------------------
insert into sys_role values(1763100000000000001, 'AI数智化管理员',   'aig_admin',    20, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '能力目录、模型登记、路由策略与审计的日常管理');
insert into sys_role values(1763100000000000002, '信息安全授权人',   'aig_security', 21, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '密钥引用、数据出域与调用审计的监督角色');
insert into sys_role values(1763100000000000003, 'AI能力查看者',     'aig_viewer',   22, 5, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '业务人员：全模块只读，看不到端点与密钥引用');

-- ----------------------------
-- 四、菜单
-- column: menu_id, menu_name, parent_id, order_num, path, component, query_param,
--         is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext,
--         create_dept, create_by, create_time, update_by, update_time, remark
-- ----------------------------
-- 一级目录
insert into sys_menu values(1763000000000000001, 'AI平台治理', 0, 6, 'ai-gov', null, '', 'N', 'Y', 'M', '0', '0', '', 'robot', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, 'AI模型能力接入与配置管控');

-- AI 能力目录
insert into sys_menu values(1763000000000000101, 'AI能力目录', 1763000000000000001, 1, 'capability', 'aigov/capability/index', '', 'N', 'Y', 'C', '0', '0', 'aig:capability:list', 'list', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '业务能力模板：定义做什么，不绑定具体模型');
insert into sys_menu values(1763000000000001101, '能力查询', 1763000000000000101, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'aig:capability:query',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1763000000000001102, '能力新增', 1763000000000000101, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'aig:capability:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1763000000000001103, '能力编辑', 1763000000000000101, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'aig:capability:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1763000000000001104, '能力删除', 1763000000000000101, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'aig:capability:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- 模型注册中心
insert into sys_menu values(1763000000000000102, '模型注册中心', 1763000000000000001, 2, 'model', 'aigov/model/index', '', 'N', 'Y', 'C', '0', '0', 'aig:model:list', 'server', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '模型主数据来自 snail-ai，本页登记治理属性');
insert into sys_menu values(1763000000000001201, '模型查询',     1763000000000000102, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'aig:model:query',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1763000000000001202, '治理属性编辑', 1763000000000000102, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'aig:model:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '数据等级/生命周期/限额/责任人');
insert into sys_menu values(1763000000000001203, '查看密钥引用', 1763000000000000102, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'aig:model:secret', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅信息安全授权人可见');

-- 路由策略
insert into sys_menu values(1763000000000000103, '路由策略', 1763000000000000001, 3, 'route', 'aigov/route/index', '', 'N', 'Y', 'C', '0', '0', 'aig:route:list', 'guide', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '按能力与数据等级决定本地/外部与降级');
insert into sys_menu values(1763000000000001301, '策略查询', 1763000000000000103, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'aig:route:query',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1763000000000001302, '策略新增', 1763000000000000103, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'aig:route:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1763000000000001303, '策略编辑', 1763000000000000103, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'aig:route:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1763000000000001304, '策略删除', 1763000000000000103, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'aig:route:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- 调用审计
insert into sys_menu values(1763000000000000104, '调用审计', 1763000000000000001, 4, 'audit', 'aigov/audit/index', '', 'N', 'Y', 'C', '0', '0', 'aig:audit:list', 'monitor', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '逐次调用审计：模型、数据等级、是否外发、策略命中');

-- ----------------------------
-- 五、角色-菜单绑定
-- ----------------------------
-- AI数智化管理员：全部
insert into sys_role_menu values (1763100000000000001, 1763000000000000001);
insert into sys_role_menu values (1763100000000000001, 1763000000000000101);
insert into sys_role_menu values (1763100000000000001, 1763000000000001101);
insert into sys_role_menu values (1763100000000000001, 1763000000000001102);
insert into sys_role_menu values (1763100000000000001, 1763000000000001103);
insert into sys_role_menu values (1763100000000000001, 1763000000000001104);
insert into sys_role_menu values (1763100000000000001, 1763000000000000102);
insert into sys_role_menu values (1763100000000000001, 1763000000000001201);
insert into sys_role_menu values (1763100000000000001, 1763000000000001202);
insert into sys_role_menu values (1763100000000000001, 1763000000000001203);
insert into sys_role_menu values (1763100000000000001, 1763000000000000103);
insert into sys_role_menu values (1763100000000000001, 1763000000000001301);
insert into sys_role_menu values (1763100000000000001, 1763000000000001302);
insert into sys_role_menu values (1763100000000000001, 1763000000000001303);
insert into sys_role_menu values (1763100000000000001, 1763000000000001304);
insert into sys_role_menu values (1763100000000000001, 1763000000000000104);

-- 信息安全授权人：模型注册（含密钥引用）+ 调用审计 + 只读能力/策略
insert into sys_role_menu values (1763100000000000002, 1763000000000000001);
insert into sys_role_menu values (1763100000000000002, 1763000000000000101);
insert into sys_role_menu values (1763100000000000002, 1763000000000001101);
insert into sys_role_menu values (1763100000000000002, 1763000000000000102);
insert into sys_role_menu values (1763100000000000002, 1763000000000001201);
insert into sys_role_menu values (1763100000000000002, 1763000000000001203);
insert into sys_role_menu values (1763100000000000002, 1763000000000000103);
insert into sys_role_menu values (1763100000000000002, 1763000000000001301);
insert into sys_role_menu values (1763100000000000002, 1763000000000000104);

-- AI能力查看者：全模块只读，但看不到端点与密钥引用
-- 只读 = list/query；不授 model:secret（字段脱敏的验证角色），不授任何 add/edit/remove。
-- 说明：若只授能力目录，viewer 连模型清单都打不开，与「查看者」定位不符，
--       且会导致「无 secret 权限时 apiEndpoint/secretRef 必须不下发」这条规则没有任何角色可验证。
insert into sys_role_menu values (1763100000000000003, 1763000000000000001);
insert into sys_role_menu values (1763100000000000003, 1763000000000000101);
insert into sys_role_menu values (1763100000000000003, 1763000000000001101);
insert into sys_role_menu values (1763100000000000003, 1763000000000000102);
insert into sys_role_menu values (1763100000000000003, 1763000000000001201);
insert into sys_role_menu values (1763100000000000003, 1763000000000000103);
insert into sys_role_menu values (1763100000000000003, 1763000000000001301);
insert into sys_role_menu values (1763100000000000003, 1763000000000000104);
