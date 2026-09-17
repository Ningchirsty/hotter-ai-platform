-- ----------------------------------------------------------------------------
-- 集团人才库 菜单 / 角色 / 字典 初始化  V2
-- 依赖 V1__talent_schema.sql
-- 前置：RuoYi-Vue-Plus v6.0.0 已初始化（script/sql/ry_vue.sql）
-- 约定：create_dept=1761000000000000103、create_by=1761100000000000001（与 ry_vue.sql 初始化一致）
-- 幂等：全部为固定 ID 的 insert，重复执行前请先按 ID 清理本节数据
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 一、字典类型
-- ----------------------------
insert into sys_dict_type values(1762200000000000001, '人才归属区域', 'tl_region',              1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才库区域，禁止自由输入');
insert into sys_dict_type values(1762200000000000002, '人才性别',     'tl_gender',              1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才性别');
insert into sys_dict_type values(1762200000000000003, '人才学历',     'tl_education',           1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才学历');
insert into sys_dict_type values(1762200000000000004, '人才状态',     'tl_talent_status',       1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才当前状态');
insert into sys_dict_type values(1762200000000000005, '人才附件类型', 'tl_attachment_type',     1761000000000000103, 1761100000000000001, sysdate(), null, null, '简历/证件附件类型');
insert into sys_dict_type values(1762200000000000006, '人才来源',     'tl_source',              1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才来源渠道');
insert into sys_dict_type values(1762200000000000007, '联系结果',     'tl_contact_result',      1761000000000000103, 1761100000000000001, sysdate(), null, null, '联系跟进结果');
insert into sys_dict_type values(1762200000000000008, '重复确认结论', 'tl_duplicate_conclusion',1761000000000000103, 1761100000000000001, sysdate(), null, null, '重复人才人工确认结论');

-- ----------------------------
-- 二、字典数据
-- ----------------------------
-- 区域
insert into sys_dict_data values(1762300000000000001, 1, '集团共享', 'GROUP', 'tl_region', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '集团共享档案，并非无归属');
insert into sys_dict_data values(1762300000000000002, 2, '深圳',     'SZ',    'tl_region', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '深圳区域');
insert into sys_dict_data values(1762300000000000003, 3, '汕头',     'ST',    'tl_region', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '汕头区域');
-- 性别
insert into sys_dict_data values(1762300000000000011, 1, '未知', '0', 'tl_gender', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '性别未知');
insert into sys_dict_data values(1762300000000000012, 2, '男',   '1', 'tl_gender', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '性别男');
insert into sys_dict_data values(1762300000000000013, 3, '女',   '2', 'tl_gender', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '性别女');
-- 学历
insert into sys_dict_data values(1762300000000000021, 1, '高中及以下', 'HIGH_SCHOOL', 'tl_education', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000022, 2, '大专',       'COLLEGE',     'tl_education', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000023, 3, '本科',       'BACHELOR',    'tl_education', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000024, 4, '硕士',       'MASTER',      'tl_education', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000025, 5, '博士',       'DOCTOR',      'tl_education', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000026, 6, '其他',       'OTHER',       'tl_education', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
-- 人才状态
insert into sys_dict_data values(1762300000000000031, 1, '新入库',   'NEW',       'tl_talent_status', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000032, 2, '跟进中',   'FOLLOWING', 'tl_talent_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000033, 3, '面试中',   'INTERVIEW', 'tl_talent_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000034, 4, '已发offer','OFFER',     'tl_talent_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000035, 5, '已入职',   'ONBOARD',   'tl_talent_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000036, 6, '已归档',   'ARCHIVED',  'tl_talent_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '归档后仅可查看');
-- 附件类型
insert into sys_dict_data values(1762300000000000041, 1, '简历',     'RESUME',         'tl_attachment_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000042, 2, '证件',     'ID_CARD',        'tl_attachment_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000043, 3, '学历证明', 'EDUCATION_CERT', 'tl_attachment_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000044, 4, '其他',     'OTHER',          'tl_attachment_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
-- 来源
insert into sys_dict_data values(1762300000000000051, 1, '招聘网站', 'JOB_SITE',   'tl_source', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000052, 2, '内部推荐', 'REFERRAL',   'tl_source', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000053, 3, '猎头',     'HEADHUNTER', 'tl_source', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000054, 4, '校园招聘', 'CAMPUS',     'tl_source', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000055, 5, '主动投递', 'WALK_IN',    'tl_source', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000056, 6, '其他',     'OTHER',      'tl_source', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
-- 联系结果
insert into sys_dict_data values(1762300000000000061, 1, '已接通',   'CONNECTED',  'tl_contact_result', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000062, 2, '未接听',   'NO_ANSWER',  'tl_contact_result', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000063, 3, '已拒绝',   'REFUSED',    'tl_contact_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000064, 4, '有意向',   'INTERESTED', 'tl_contact_result', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000065, 5, '号码无效', 'INVALID',    'tl_contact_result', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
-- 重复确认结论
insert into sys_dict_data values(1762300000000000071, 1, '待确认', 'PENDING',   'tl_duplicate_conclusion', '', 'warning', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_dict_data values(1762300000000000072, 2, '不同人', 'DIFFERENT', 'tl_duplicate_conclusion', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '可正常入库');
insert into sys_dict_data values(1762300000000000073, 3, '同一人', 'SAME',      'tl_duplicate_conclusion', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '转入已有档案，不自动合并');

-- ----------------------------
-- 三、角色（角色标识为业务契约，不得随意变更）
-- ----------------------------
insert into sys_role values(1762100000000000001, '集团人才库管理员', 'talent_admin',    10, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才库全部权限与授权配置');
insert into sys_role values(1762100000000000002, '集团HR',           'talent_hr_group', 11, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '可见全部区域人才');
insert into sys_role values(1762100000000000003, '深圳HR',           'talent_hr_sz',    12, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅可见 SZ + GROUP');
insert into sys_role values(1762100000000000004, '汕头HR',           'talent_hr_st',    13, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅可见 ST + GROUP');
insert into sys_role values(1762100000000000005, '用人部门查阅者',   'talent_viewer',   14, 5, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅可通过单条授权查看，手机号脱敏');
insert into sys_role values(1762100000000000006, '人才库审计员',     'talent_auditor',  15, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '只读审计记录，不可修改业务数据');

-- ----------------------------
-- 四、菜单（一级目录 + 6 个菜单 + 按钮）
-- column: menu_id, menu_name, parent_id, order_num, path, component, query_param,
--         is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext,
--         create_dept, create_by, create_time, update_by, update_time, remark
-- ----------------------------
-- 一级目录
insert into sys_menu values(1762000000000000001, '集团人才库', 0, 5, 'talent', null, '', 'N', 'Y', 'M', '0', '0', '', 'peoples', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '集团人才库一级目录');

-- 人才档案
insert into sys_menu values(1762000000000000101, '人才档案', 1762000000000000001, 1, 'profile', 'talent/profile/index', '', 'N', 'Y', 'C', '0', '0', 'talent:profile:list', 'user', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才档案检索与维护');
insert into sys_menu values(1762000000000001101, '人才查询',     1762000000000000101, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:query',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1762000000000001102, '人才新增',     1762000000000000101, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:add',     '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1762000000000001103, '人才编辑',     1762000000000000101, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:edit',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅可编辑本区域人才');
insert into sys_menu values(1762000000000001104, '人才归档',     1762000000000000101, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:archive', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1762000000000001105, '查看完整手机', 1762000000000000101, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:phone',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '查看完整手机号会写入敏感审计');
insert into sys_menu values(1762000000000001106, '授权配置',     1762000000000000101, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:grant',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '单条人才访问授权管理');

-- 简历与附件
insert into sys_menu values(1762000000000000102, '简历与附件', 1762000000000000001, 2, 'attachment', 'talent/attachment/index', '', 'N', 'Y', 'C', '0', '0', 'talent:attachment:manage', 'upload', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '附件版本与受控下载');
insert into sys_menu values(1762000000000001201, '附件上传', 1762000000000000102, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:attachment:upload',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1762000000000001202, '附件下载', 1762000000000000102, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:attachment:download', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '需通过服务端二次授权校验');

-- 重复人才预警
insert into sys_menu values(1762000000000000103, '重复人才预警', 1762000000000000001, 3, 'duplicate', 'talent/duplicate/index', '', 'N', 'Y', 'C', '0', '0', 'talent:duplicate:view', 'warning', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工确认重复关系，不自动合并');
insert into sys_menu values(1762000000000001301, '重复确认', 1762000000000000103, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:duplicate:confirm', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- 简历解析复核
insert into sys_menu values(1762000000000000104, '简历解析复核', 1762000000000000001, 4, 'parse-review', 'talent/parse-review/index', '', 'N', 'Y', 'C', '1', '1', 'talent:parse:view', 'education', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '解析服务获批启用前默认隐藏');
insert into sys_menu values(1762000000000001401, '解析确认', 1762000000000000104, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:parse:confirm', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1762000000000001402, '解析重试', 1762000000000000104, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:parse:retry',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- Excel 导出中心
insert into sys_menu values(1762000000000000105, 'Excel导出中心', 1762000000000000001, 5, 'export', 'talent/export/index', '', 'N', 'Y', 'C', '0', '0', 'talent:export:create', 'download', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '异步导出任务，仅可查看本人或授权范围');
insert into sys_menu values(1762000000000001501, '导出下载', 1762000000000000105, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:export:download', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- 敏感操作审计
insert into sys_menu values(1762000000000000106, '敏感操作审计', 1762000000000000001, 6, 'audit', 'talent/audit/index', '', 'N', 'Y', 'C', '0', '0', 'talent:audit:list', 'monitor', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '查看下载/导出/完整信息访问记录');

-- ----------------------------
-- 五、角色-菜单绑定
-- ----------------------------

-- 集团人才库管理员：全部
insert into sys_role_menu values (1762100000000000001, 1762000000000000001);
insert into sys_role_menu values (1762100000000000001, 1762000000000000101);
insert into sys_role_menu values (1762100000000000001, 1762000000000001101);
insert into sys_role_menu values (1762100000000000001, 1762000000000001102);
insert into sys_role_menu values (1762100000000000001, 1762000000000001103);
insert into sys_role_menu values (1762100000000000001, 1762000000000001104);
insert into sys_role_menu values (1762100000000000001, 1762000000000001105);
insert into sys_role_menu values (1762100000000000001, 1762000000000001106);
insert into sys_role_menu values (1762100000000000001, 1762000000000000102);
insert into sys_role_menu values (1762100000000000001, 1762000000000001201);
insert into sys_role_menu values (1762100000000000001, 1762000000000001202);
insert into sys_role_menu values (1762100000000000001, 1762000000000000103);
insert into sys_role_menu values (1762100000000000001, 1762000000000001301);
insert into sys_role_menu values (1762100000000000001, 1762000000000000104);
insert into sys_role_menu values (1762100000000000001, 1762000000000001401);
insert into sys_role_menu values (1762100000000000001, 1762000000000001402);
insert into sys_role_menu values (1762100000000000001, 1762000000000000105);
insert into sys_role_menu values (1762100000000000001, 1762000000000001501);
insert into sys_role_menu values (1762100000000000001, 1762000000000000106);

-- 集团HR / 深圳HR / 汕头HR：业务菜单全量（审计菜单仅审计员与管理员）
insert into sys_role_menu values (1762100000000000002, 1762000000000000001);
insert into sys_role_menu values (1762100000000000002, 1762000000000000101);
insert into sys_role_menu values (1762100000000000002, 1762000000000001101);
insert into sys_role_menu values (1762100000000000002, 1762000000000001102);
insert into sys_role_menu values (1762100000000000002, 1762000000000001103);
insert into sys_role_menu values (1762100000000000002, 1762000000000001104);
insert into sys_role_menu values (1762100000000000002, 1762000000000001105);
insert into sys_role_menu values (1762100000000000002, 1762000000000000102);
insert into sys_role_menu values (1762100000000000002, 1762000000000001201);
insert into sys_role_menu values (1762100000000000002, 1762000000000001202);
insert into sys_role_menu values (1762100000000000002, 1762000000000000103);
insert into sys_role_menu values (1762100000000000002, 1762000000000001301);
insert into sys_role_menu values (1762100000000000002, 1762000000000000104);
insert into sys_role_menu values (1762100000000000002, 1762000000000001401);
insert into sys_role_menu values (1762100000000000002, 1762000000000001402);
insert into sys_role_menu values (1762100000000000002, 1762000000000000105);
insert into sys_role_menu values (1762100000000000002, 1762000000000001501);

insert into sys_role_menu values (1762100000000000003, 1762000000000000001);
insert into sys_role_menu values (1762100000000000003, 1762000000000000101);
insert into sys_role_menu values (1762100000000000003, 1762000000000001101);
insert into sys_role_menu values (1762100000000000003, 1762000000000001102);
insert into sys_role_menu values (1762100000000000003, 1762000000000001103);
insert into sys_role_menu values (1762100000000000003, 1762000000000001104);
insert into sys_role_menu values (1762100000000000003, 1762000000000001105);
insert into sys_role_menu values (1762100000000000003, 1762000000000000102);
insert into sys_role_menu values (1762100000000000003, 1762000000000001201);
insert into sys_role_menu values (1762100000000000003, 1762000000000001202);
insert into sys_role_menu values (1762100000000000003, 1762000000000000103);
insert into sys_role_menu values (1762100000000000003, 1762000000000001301);
insert into sys_role_menu values (1762100000000000003, 1762000000000000104);
insert into sys_role_menu values (1762100000000000003, 1762000000000001401);
insert into sys_role_menu values (1762100000000000003, 1762000000000001402);
insert into sys_role_menu values (1762100000000000003, 1762000000000000105);
insert into sys_role_menu values (1762100000000000003, 1762000000000001501);

insert into sys_role_menu values (1762100000000000004, 1762000000000000001);
insert into sys_role_menu values (1762100000000000004, 1762000000000000101);
insert into sys_role_menu values (1762100000000000004, 1762000000000001101);
insert into sys_role_menu values (1762100000000000004, 1762000000000001102);
insert into sys_role_menu values (1762100000000000004, 1762000000000001103);
insert into sys_role_menu values (1762100000000000004, 1762000000000001104);
insert into sys_role_menu values (1762100000000000004, 1762000000000001105);
insert into sys_role_menu values (1762100000000000004, 1762000000000000102);
insert into sys_role_menu values (1762100000000000004, 1762000000000001201);
insert into sys_role_menu values (1762100000000000004, 1762000000000001202);
insert into sys_role_menu values (1762100000000000004, 1762000000000000103);
insert into sys_role_menu values (1762100000000000004, 1762000000000001301);
insert into sys_role_menu values (1762100000000000004, 1762000000000000104);
insert into sys_role_menu values (1762100000000000004, 1762000000000001401);
insert into sys_role_menu values (1762100000000000004, 1762000000000001402);
insert into sys_role_menu values (1762100000000000004, 1762000000000000105);
insert into sys_role_menu values (1762100000000000004, 1762000000000001501);

-- 用人部门查阅者：仅档案查询 + 受控下载（数据范围由单条授权决定）
insert into sys_role_menu values (1762100000000000005, 1762000000000000001);
insert into sys_role_menu values (1762100000000000005, 1762000000000000101);
insert into sys_role_menu values (1762100000000000005, 1762000000000001101);
insert into sys_role_menu values (1762100000000000005, 1762000000000001202);

-- 人才库审计员：仅审计菜单
insert into sys_role_menu values (1762100000000000006, 1762000000000000001);
insert into sys_role_menu values (1762100000000000006, 1762000000000000106);
