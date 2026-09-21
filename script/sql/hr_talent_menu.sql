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
insert into sys_dict_type values(1766200000000000001, '招聘需求状态',     'recruit_demand_status',           1761000000000000103, 1761100000000000001, sysdate(), null, null, 'draft/submitted/recruiting/paused/completed/closed');
insert into sys_dict_type values(1766200000000000002, '公司月度计划状态', 'recruit_plan_status',             1761000000000000103, 1761100000000000001, sysdate(), null, null, 'draft/executing/closed');
insert into sys_dict_type values(1766200000000000003, '月度任务来源类型', 'recruit_plan_source_type',        1761000000000000103, 1761100000000000001, sysdate(), null, null, 'new/carryover');
insert into sys_dict_type values(1766200000000000004, '月度任务控制状态', 'recruit_plan_control_status',     1761000000000000103, 1761100000000000001, sysdate(), null, null, 'normal/paused/cancelled');
insert into sys_dict_type values(1766200000000000005, '月度任务执行阶段', 'recruit_plan_execution_status',   1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/recruiting/interviewing/offer/pending_arrival');
insert into sys_dict_type values(1766200000000000006, '月度任务完成状态', 'recruit_plan_completion_status',  1761000000000000103, 1761100000000000001, sysdate(), null, null, 'unfinished/partial_completed/completed/rolled_over');
insert into sys_dict_type values(1766200000000000007, '候选人阶段',       'recruit_candidate_stage',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'new/resume_review/invite/first_interview/second_interview/background/offer/pending_arrival/arrived');
insert into sys_dict_type values(1766200000000000008, '应聘结果',         'recruit_application_result',      1761000000000000103, 1761100000000000001, sysdate(), null, null, 'processing/passed/rejected/withdrawn/paused/talent_pool');
insert into sys_dict_type values(1766200000000000009, '紧急程度',         'recruit_urgency',                 1761000000000000103, 1761100000000000001, sysdate(), null, null, 'normal/urgent/very_urgent');
insert into sys_dict_type values(1766200000000000010, '招聘形式',         'recruit_mode',                    1761000000000000103, 1761100000000000001, sysdate(), null, null, 'internal/social/campus/headhunter/referral/other');
insert into sys_dict_type values(1766200000000000011, '面试结果',         'recruit_interview_result',        1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/pass/fail/reserve/absent');
insert into sys_dict_type values(1766200000000000012, '背调结果',         'recruit_background_result',       1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/pass/fail/waived');
insert into sys_dict_type values(1766200000000000013, '附件类型',         'recruit_attachment_type',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'resume/portfolio/interview/background/offer/other');
insert into sys_dict_type values(1766200000000000014, '招聘数据分级',     'recruit_data_level',              1761000000000000103, 1761100000000000001, sysdate(), null, null, 'internal/sensitive/highly_sensitive');
insert into sys_dict_type values(1766200000000000015, '招聘渠道类型',     'recruit_channel_type',            1761000000000000103, 1761100000000000001, sysdate(), null, null, 'job_site/headhunter/referral/social_media/campus/other');
insert into sys_dict_type values(1766200000000000016, '人才生命周期状态', 'talent_status',                   1761000000000000103, 1761100000000000001, sysdate(), null, null, 'draft/active/recruiting/reserved/hired/do_not_contact/restricted/archived/merged');
insert into sys_dict_type values(1766200000000000017, '人才可见范围',     'talent_visibility_type',          1761000000000000103, 1761100000000000001, sysdate(), null, null, 'group/company/department/owner/explicit');
insert into sys_dict_type values(1766200000000000018, '人才共享授权级别', 'talent_permission_level',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'summary/detail/attachment');
insert into sys_dict_type values(1766200000000000019, '人才池成员状态',   'talent_pool_member_status',       1761000000000000103, 1761100000000000001, sysdate(), null, null, 'active/paused/removed/converted');
insert into sys_dict_type values(1766200000000000020, '人才标签类别',     'talent_tag_category',             1761000000000000103, 1761100000000000001, sysdate(), null, null, 'skill/job_direction/industry/experience/language/certificate/other');
insert into sys_dict_type values(1766200000000000021, '简历解析状态',     'talent_resume_parse_status',      1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/processing/succeeded/failed/reviewing/confirmed');
insert into sys_dict_type values(1766200000000000022, '疑似重复处理状态', 'talent_duplicate_status',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/merged/not_same/ignored/confirmed');
insert into sys_dict_type values(1766200000000000023, '人才联系结果',     'talent_contact_result',           1761000000000000103, 1761100000000000001, sysdate(), null, null, 'connected/no_answer/refused/interested/follow_up_later/invalid');

-- 本次补齐的 6 组字典（原 23 组 → 29 组，ID 顺延 …024~…029）：
--   24. 岗位执行项状态 recruit_job_status（来源 enums/JobStatusEnum.java）
--   25. 月度结转执行结果 recruit_rollover_result（来源 PlanRolloverDomainService.RESULT_* 常量）
--   26. 背调状态 recruit_background_status（来源 enums/BackgroundStatusEnum.java）
--   27. 背调未通过原因分类 recruit_background_failure_reason
--       （设计文档 §10 无此组，按 §8.7「未通过原因分类」新增）
insert into sys_dict_type values(1766200000000000024, '岗位执行项状态',     'recruit_job_status',               1761000000000000103, 1761100000000000001, sysdate(), null, null, 'draft/open/paused/closed');
insert into sys_dict_type values(1766200000000000025, '月度结转执行结果',   'recruit_rollover_result',          1761000000000000103, 1761100000000000001, sysdate(), null, null, 'processing/success/failed/skipped');
insert into sys_dict_type values(1766200000000000026, '背调状态',           'recruit_background_status',        1761000000000000103, 1761100000000000001, sysdate(), null, null, 'draft/checking/finished/cancelled');
insert into sys_dict_type values(1766200000000000027, '背调未通过原因分类', 'recruit_background_failure_reason', 1761000000000000103, 1761100000000000001, sysdate(), null, null, 'info_mismatch/work_experience/education/position_duty/performance/legal_record/other');
--   28. 面试状态 recruit_interview_status（来源 hr_recruit_interview.status 建表注释 +
--       RecruitInterviewServiceImpl.STATUS_* 私有常量）
--   29. 面试方式 recruit_interview_method（来源 hr_recruit_interview.method 建表注释）
insert into sys_dict_type values(1766200000000000028, '面试状态',           'recruit_interview_status',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/scheduled/finished/cancelled/rescheduled');
insert into sys_dict_type values(1766200000000000029, '面试方式',           'recruit_interview_method',         1761000000000000103, 1761100000000000001, sysdate(), null, null, 'onsite/video/phone');

-- 本次补齐的另外 2 组字典（原 29 组 → 31 组，ID 顺延 …030~…031）：
--   30. 邀约结果 recruit_offer_result（来源 hr_recruit_application.offer_result 建表注释
--       「accepted/rejected等稳定编码，设计文档 §7.2」+ §14 邀约接受率统计口径）
--   31. 阶段变更原因分类 recruit_stage_reason_code（来源 hr_recruit_stage_log.reason_code
--       建表注释「原因编码（字典编码，不存中文）」+ §8.5「原因分类」）。
--       *** 设计文档未枚举具体编码，下列 8 个编码为本次新定义。***
insert into sys_dict_type values(1766200000000000030, '邀约结果',            'recruit_offer_result',                1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/accepted/rejected');
insert into sys_dict_type values(1766200000000000031, '阶段变更原因分类',    'recruit_stage_reason_code',           1761000000000000103, 1761100000000000001, sysdate(), null, null, 'skill_mismatch/experience_mismatch/salary_mismatch/education_mismatch/communication/candidate_declined/position_closed/other');

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
insert into sys_dict_type values(1766200000000000032, '学历',             'talent_education',            1761000000000000103, 1761100000000000001, sysdate(), null, null, 'high_school/college/bachelor/master/doctor/other');
insert into sys_dict_type values(1766200000000000033, '学位',             'talent_degree',               1761000000000000103, 1761100000000000001, sysdate(), null, null, 'none/bachelor/master/doctor/other');
insert into sys_dict_type values(1766200000000000034, '简历解析复核状态', 'talent_resume_review_status', 1761000000000000103, 1761100000000000001, sysdate(), null, null, 'pending/reviewing/confirmed/rejected');
insert into sys_dict_type values(1766200000000000035, '数据来源类型',     'talent_source_type',          1761000000000000103, 1761100000000000001, sysdate(), null, null, 'manual/import/parse/system');
insert into sys_dict_type values(1766200000000000036, '人才分组类型',     'talent_group_type',           1761000000000000103, 1761100000000000001, sysdate(), null, null, 'public/personal');

-- ----------------------------
-- 二、字典数据（设计文档 §10 全部编码值 + 前两批补齐的 8 组 + 本批补齐的 5 组）
-- column: dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
--         is_default, create_dept, create_by, create_time, update_by, update_time, remark
-- ----------------------------
-- 1. 招聘需求状态 recruit_demand_status
insert into sys_dict_data values(1766300000000000001, 1, '草稿',     'draft',      'recruit_demand_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '需求草稿，未提交审批');
insert into sys_dict_data values(1766300000000000002, 2, '已提交',   'submitted',  'recruit_demand_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已提交，待招聘负责人确认');
insert into sys_dict_data values(1766300000000000003, 3, '招聘中',   'recruiting', 'recruit_demand_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '可生成岗位需求');
insert into sys_dict_data values(1766300000000000004, 4, '已暂停',   'paused',     'recruit_demand_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '暂停推进，保留历史');
insert into sys_dict_data values(1766300000000000005, 5, '已完成',   'completed',  'recruit_demand_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '需求人数已满足');
insert into sys_dict_data values(1766300000000000006, 6, '已关闭',   'closed',     'recruit_demand_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '终止招聘，不再生成岗位');

-- 2. 公司月度计划状态 recruit_plan_status
insert into sys_dict_data values(1766300000000000011, 1, '草稿',   'draft',     'recruit_plan_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '计划表头尚未确认');
insert into sys_dict_data values(1766300000000000012, 2, '执行中', 'executing', 'recruit_plan_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '计划已确认并进入执行');
insert into sys_dict_data values(1766300000000000013, 3, '已关闭', 'closed',    'recruit_plan_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '月度计划已归档关闭');

-- 3. 月度任务来源类型 recruit_plan_source_type
insert into sys_dict_data values(1766300000000000021, 1, '新建',   'new',       'recruit_plan_source_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本月新增任务');
insert into sys_dict_data values(1766300000000000022, 2, '上月结转', 'carryover', 'recruit_plan_source_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '由上月未完成量结转而来');

-- 4. 月度任务控制状态 recruit_plan_control_status
insert into sys_dict_data values(1766300000000000031, 1, '正常',   'normal',    'recruit_plan_control_status', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '正常执行');
insert into sys_dict_data values(1766300000000000032, 2, '已暂停', 'paused',    'recruit_plan_control_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工暂停该月度任务');
insert into sys_dict_data values(1766300000000000033, 3, '已取消', 'cancelled', 'recruit_plan_control_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工取消该月度任务');

-- 5. 月度任务执行阶段 recruit_plan_execution_status
insert into sys_dict_data values(1766300000000000041, 1, '待启动',   'pending',         'recruit_plan_execution_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '尚无候选人推进');
insert into sys_dict_data values(1766300000000000042, 2, '招聘中',   'recruiting',      'recruit_plan_execution_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人处于简历/邀约阶段');
insert into sys_dict_data values(1766300000000000043, 3, '面试中',   'interviewing',    'recruit_plan_execution_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已有候选人进入面试');
insert into sys_dict_data values(1766300000000000044, 4, '已发Offer', 'offer',          'recruit_plan_execution_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已发出录用通知');
insert into sys_dict_data values(1766300000000000045, 5, '待到岗',   'pending_arrival', 'recruit_plan_execution_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已接受Offer，等待报到');

-- 6. 月度任务完成状态 recruit_plan_completion_status
insert into sys_dict_data values(1766300000000000051, 1, '未完成',   'unfinished',        'recruit_plan_completion_status', '', 'warning', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未完成，月末参与结转');
insert into sys_dict_data values(1766300000000000052, 2, '部分完成', 'partial_completed', 'recruit_plan_completion_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '到岗人数小于计划人数');
insert into sys_dict_data values(1766300000000000053, 3, '已完成',   'completed',         'recruit_plan_completion_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '到岗人数已达到计划人数');
insert into sys_dict_data values(1766300000000000054, 4, '已结转',   'rolled_over',       'recruit_plan_completion_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未完成量已结转到次月');

-- 7. 候选人阶段 recruit_candidate_stage
insert into sys_dict_data values(1766300000000000061, 1, '新简历',     'new',              'recruit_candidate_stage', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人记录已建立');
insert into sys_dict_data values(1766300000000000062, 2, '简历筛选',   'resume_review',    'recruit_candidate_stage', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '招聘专员筛选简历');
insert into sys_dict_data values(1766300000000000063, 3, '已邀约',     'invite',           'recruit_candidate_stage', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已发出面试邀约');
insert into sys_dict_data values(1766300000000000064, 4, '初试',       'first_interview',  'recruit_candidate_stage', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '初试进行中');
insert into sys_dict_data values(1766300000000000065, 5, '复试',       'second_interview', 'recruit_candidate_stage', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '复试进行中');
insert into sys_dict_data values(1766300000000000066, 6, '背调',       'background',       'recruit_candidate_stage', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '背景调查中，敏感阶段');
insert into sys_dict_data values(1766300000000000067, 7, 'Offer',      'offer',            'recruit_candidate_stage', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已发出录用通知');
insert into sys_dict_data values(1766300000000000068, 8, '待到岗',     'pending_arrival',  'recruit_candidate_stage', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '等待候选人报到');
insert into sys_dict_data values(1766300000000000069, 9, '已到岗',     'arrived',          'recruit_candidate_stage', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已报到，计入到岗人数');

-- 8. 应聘结果 recruit_application_result
insert into sys_dict_data values(1766300000000000071, 1, '处理中',   'processing',  'recruit_application_result', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '流程进行中');
insert into sys_dict_data values(1766300000000000072, 2, '已通过',   'passed',      'recruit_application_result', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试通过并已完成后续流程');
insert into sys_dict_data values(1766300000000000073, 3, '未通过',   'rejected',    'recruit_application_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '被淘汰');
insert into sys_dict_data values(1766300000000000074, 4, '已撤回',   'withdrawn',   'recruit_application_result', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人主动放弃');
insert into sys_dict_data values(1766300000000000075, 5, '已暂停',   'paused',      'recruit_application_result', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '流程暂停，可恢复');
insert into sys_dict_data values(1766300000000000076, 6, '转入人才池', 'talent_pool', 'recruit_application_result', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本次未录用但保留为人才');

-- 9. 紧急程度 recruit_urgency
insert into sys_dict_data values(1766300000000000081, 1, '普通',   'normal',      'recruit_urgency', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '常规招聘节奏');
insert into sys_dict_data values(1766300000000000082, 2, '紧急',   'urgent',      'recruit_urgency', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '需要优先安排');
insert into sys_dict_data values(1766300000000000083, 3, '非常紧急', 'very_urgent', 'recruit_urgency', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '最高优先级，需当日响应');

-- 10. 招聘形式 recruit_mode
insert into sys_dict_data values(1766300000000000091, 1, '内部招聘', 'internal',   'recruit_mode', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '内部转岗或推荐');
insert into sys_dict_data values(1766300000000000092, 2, '社会招聘', 'social',     'recruit_mode', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面向社会公开招聘');
insert into sys_dict_data values(1766300000000000093, 3, '校园招聘', 'campus',     'recruit_mode', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '校招渠道');
insert into sys_dict_data values(1766300000000000094, 4, '猎头',     'headhunter', 'recruit_mode', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '委托猎头渠道');
insert into sys_dict_data values(1766300000000000095, 5, '内推',     'referral',   'recruit_mode', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '员工内部推荐');
insert into sys_dict_data values(1766300000000000096, 6, '其他',     'other',      'recruit_mode', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他招聘形式');

-- 11. 面试结果 recruit_interview_result
insert into sys_dict_data values(1766300000000000101, 1, '待反馈', 'pending', 'recruit_interview_result', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试结束待面试官反馈');
insert into sys_dict_data values(1766300000000000102, 2, '通过',   'pass',    'recruit_interview_result', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '进入下一阶段');
insert into sys_dict_data values(1766300000000000103, 3, '不通过', 'fail',    'recruit_interview_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '流程结束');
insert into sys_dict_data values(1766300000000000104, 4, '待定',   'reserve', 'recruit_interview_result', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '暂不确定，保留候选');
insert into sys_dict_data values(1766300000000000105, 5, '未到场', 'absent',  'recruit_interview_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人爽约');

-- 12. 背调结果 recruit_background_result
insert into sys_dict_data values(1766300000000000111, 1, '待背调', 'pending', 'recruit_background_result', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '尚未发起或未回执');
insert into sys_dict_data values(1766300000000000112, 2, '通过',   'pass',    'recruit_background_result', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '背调结论无异常');
insert into sys_dict_data values(1766300000000000113, 3, '不通过', 'fail',    'recruit_background_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '存在重大不一致');
insert into sys_dict_data values(1766300000000000114, 4, '已豁免', 'waived',  'recruit_background_result', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '经授权豁免背调');

-- 13. 附件类型 recruit_attachment_type
insert into sys_dict_data values(1766300000000000121, 1, '简历',       'resume',     'recruit_attachment_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人简历文件');
insert into sys_dict_data values(1766300000000000122, 2, '作品集',     'portfolio',  'recruit_attachment_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '作品或案例材料');
insert into sys_dict_data values(1766300000000000123, 3, '面试材料',   'interview',  'recruit_attachment_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试题、面试记录附件');
insert into sys_dict_data values(1766300000000000124, 4, '背调材料',   'background', 'recruit_attachment_type', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '高敏感，需独立权限');
insert into sys_dict_data values(1766300000000000125, 5, 'Offer附件',  'offer',      'recruit_attachment_type', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '录用通知书等');
insert into sys_dict_data values(1766300000000000126, 6, '其他',       'other',      'recruit_attachment_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他业务附件');

-- 14. 招聘数据分级 recruit_data_level
insert into sys_dict_data values(1766300000000000131, 1, '内部',   'internal',         'recruit_data_level', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '公司内部一般数据');
insert into sys_dict_data values(1766300000000000132, 2, '敏感',   'sensitive',        'recruit_data_level', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '联系方式、薪资期望等');
insert into sys_dict_data values(1766300000000000133, 3, '高敏感', 'highly_sensitive', 'recruit_data_level', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '背调明细、证件信息；默认禁止外发');

-- 15. 招聘渠道类型 recruit_channel_type
insert into sys_dict_data values(1766300000000000141, 1, '招聘网站', 'job_site',     'recruit_channel_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '如主流招聘平台');
insert into sys_dict_data values(1766300000000000142, 2, '猎头',     'headhunter',   'recruit_channel_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '猎头/人力服务商');
insert into sys_dict_data values(1766300000000000143, 3, '内推',     'referral',     'recruit_channel_type', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '员工推荐渠道');
insert into sys_dict_data values(1766300000000000144, 4, '社交媒体', 'social_media', 'recruit_channel_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '社交平台渠道');
insert into sys_dict_data values(1766300000000000145, 5, '校园',     'campus',       'recruit_channel_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '校招渠道');
insert into sys_dict_data values(1766300000000000146, 6, '其他',     'other',        'recruit_channel_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他渠道');

-- 16. 人才生命周期状态 talent_status
insert into sys_dict_data values(1766300000000000151, 1, '草稿',     'draft',           'talent_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资料未完善');
insert into sys_dict_data values(1766300000000000152, 2, '生效',     'active',          'talent_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '有效人才档案');
insert into sys_dict_data values(1766300000000000153, 3, '招聘中',   'recruiting',      'talent_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '关联进行中的应聘流程');
insert into sys_dict_data values(1766300000000000154, 4, '储备',     'reserved',        'talent_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '进入人才储备池');
insert into sys_dict_data values(1766300000000000155, 5, '已入职',   'hired',           'talent_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已报到入职');
insert into sys_dict_data values(1766300000000000156, 6, '请勿联系', 'do_not_contact',  'talent_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人明确要求不再联系');
insert into sys_dict_data values(1766300000000000157, 7, '受限',     'restricted',      'talent_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '查看范围受限，需显式授权');
insert into sys_dict_data values(1766300000000000158, 8, '已归档',   'archived',        'talent_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '长期不活跃，归档保留');
insert into sys_dict_data values(1766300000000000159, 9, '已合并',   'merged',          'talent_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '被合并到主档，不再单独使用');

-- 17. 人才可见范围 talent_visibility_type
insert into sys_dict_data values(1766300000000000161, 1, '集团共享', 'group',      'talent_visibility_type', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '集团内可见');
insert into sys_dict_data values(1766300000000000162, 2, '本公司',   'company',    'talent_visibility_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅本公司可见');
insert into sys_dict_data values(1766300000000000163, 3, '本部门',   'department', 'talent_visibility_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅所属部门可见');
insert into sys_dict_data values(1766300000000000164, 4, '仅负责人', 'owner',      'talent_visibility_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅人才负责人可见');
insert into sys_dict_data values(1766300000000000165, 5, '显式授权', 'explicit',   'talent_visibility_type', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅 hr_talent_scope_grant 中显式授权对象可见');

-- 18. 人才共享授权级别 talent_permission_level
insert into sys_dict_data values(1766300000000000171, 1, '摘要',       'summary',    'talent_permission_level', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '仅脱敏摘要');
insert into sys_dict_data values(1766300000000000172, 2, '明细',       'detail',     'talent_permission_level', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '可查看明细字段');
insert into sys_dict_data values(1766300000000000173, 3, '含附件',     'attachment', 'talent_permission_level', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '可查看/下载授权附件');

-- 19. 人才池成员状态 talent_pool_member_status
insert into sys_dict_data values(1766300000000000181, 1, '在池',   'active',    'talent_pool_member_status', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '正常在池');
insert into sys_dict_data values(1766300000000000182, 2, '已暂停', 'paused',    'talent_pool_member_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '暂停跟进');
insert into sys_dict_data values(1766300000000000183, 3, '已移出', 'removed',   'talent_pool_member_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已移出人才池');
insert into sys_dict_data values(1766300000000000184, 4, '已转化', 'converted', 'talent_pool_member_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已转化为应聘或入职');

-- 20. 人才标签类别 talent_tag_category
insert into sys_dict_data values(1766300000000000191, 1, '技能',     'skill',         'talent_tag_category', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '技术或业务技能标签');
insert into sys_dict_data values(1766300000000000192, 2, '岗位方向', 'job_direction', 'talent_tag_category', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '适配岗位方向');
insert into sys_dict_data values(1766300000000000193, 3, '行业',     'industry',      'talent_tag_category', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '行业背景标签');
insert into sys_dict_data values(1766300000000000194, 4, '经验',     'experience',    'talent_tag_category', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '经验年限/层级标签');
insert into sys_dict_data values(1766300000000000195, 5, '语言',     'language',      'talent_tag_category', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '语言能力标签');
insert into sys_dict_data values(1766300000000000196, 6, '证书',     'certificate',   'talent_tag_category', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资质证书标签');
insert into sys_dict_data values(1766300000000000197, 7, '其他',     'other',         'talent_tag_category', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他标签');

-- 21. 简历解析状态 talent_resume_parse_status
insert into sys_dict_data values(1766300000000000201, 1, '待解析',   'pending',    'talent_resume_parse_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已上传待解析');
insert into sys_dict_data values(1766300000000000202, 2, '解析中',   'processing', 'talent_resume_parse_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '解析任务执行中');
insert into sys_dict_data values(1766300000000000203, 3, '解析成功', 'succeeded',  'talent_resume_parse_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已产出结构化结果');
insert into sys_dict_data values(1766300000000000204, 4, '解析失败', 'failed',     'talent_resume_parse_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '需人工处理或重试');
insert into sys_dict_data values(1766300000000000205, 5, '待复核',   'reviewing',  'talent_resume_parse_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '解析结果待人工复核');
insert into sys_dict_data values(1766300000000000206, 6, '已确认',   'confirmed',  'talent_resume_parse_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工复核通过并写入档案');

-- 22. 疑似重复处理状态 talent_duplicate_status
insert into sys_dict_data values(1766300000000000211, 1, '待处理',   'pending',  'talent_duplicate_status', '', 'warning', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '疑似重复待人工判定');
insert into sys_dict_data values(1766300000000000212, 2, '已合并',   'merged',   'talent_duplicate_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已合并到主档，写入合并日志');
insert into sys_dict_data values(1766300000000000213, 3, '非同一人', 'not_same', 'talent_duplicate_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '经确认并非同一人');
insert into sys_dict_data values(1766300000000000214, 4, '已忽略',   'ignored',  'talent_duplicate_status', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '暂不处理，保留预警');
insert into sys_dict_data values(1766300000000000265, 5, '已确认待合并', 'confirmed', 'talent_duplicate_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工确认确为同一人，尚未执行合并；不计入「待处理」工作队列');

-- 23. 人才联系结果 talent_contact_result
insert into sys_dict_data values(1766300000000000221, 1, '已接通',     'connected',       'talent_contact_result', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已取得联系');
insert into sys_dict_data values(1766300000000000222, 2, '未接听',     'no_answer',       'talent_contact_result', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '电话未接听');
insert into sys_dict_data values(1766300000000000223, 3, '已拒绝',     'refused',         'talent_contact_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人拒绝沟通或推荐');
insert into sys_dict_data values(1766300000000000224, 4, '有意向',     'interested',      'talent_contact_result', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人有明确意向');
insert into sys_dict_data values(1766300000000000225, 5, '稍后跟进',   'follow_up_later', 'talent_contact_result', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '约定后续时间再联系');
insert into sys_dict_data values(1766300000000000226, 6, '联系方式无效', 'invalid',       'talent_contact_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '号码或邮箱已失效');

-- 24. 岗位执行项状态 recruit_job_status
insert into sys_dict_data values(1766300000000000227, 1, '草稿',   'draft',  'recruit_job_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '岗位草稿，尚未对外发布');
insert into sys_dict_data values(1766300000000000228, 2, '招聘中', 'open',   'recruit_job_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '岗位已发布，可接收应聘记录');
insert into sys_dict_data values(1766300000000000229, 3, '已暂停', 'paused', 'recruit_job_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '暂停招聘，可恢复为招聘中');
insert into sys_dict_data values(1766300000000000230, 4, '已关闭', 'closed', 'recruit_job_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '结束招聘，仅可通过重新开放回到招聘中');

-- 25. 月度结转执行结果 recruit_rollover_result
insert into sys_dict_data values(1766300000000000231, 1, '处理中', 'processing', 'recruit_rollover_result', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '结转执行中的占位记录');
insert into sys_dict_data values(1766300000000000232, 2, '成功',   'success',    'recruit_rollover_result', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '结转任务已正常完成');
insert into sys_dict_data values(1766300000000000233, 3, '失败',   'failed',     'recruit_rollover_result', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '执行失败并记录原因，可安全重试');
insert into sys_dict_data values(1766300000000000234, 4, '跳过',   'skipped',    'recruit_rollover_result', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '不落库，仅出现在执行明细中');

-- 26. 背调状态 recruit_background_status
insert into sys_dict_data values(1766300000000000235, 1, '草稿',   'draft',     'recruit_background_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已登记但尚未开始核查');
insert into sys_dict_data values(1766300000000000236, 2, '核查中', 'checking',  'recruit_background_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '背调核查进行中');
insert into sys_dict_data values(1766300000000000237, 3, '已完成', 'finished',  'recruit_background_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已回执背调结论');
insert into sys_dict_data values(1766300000000000238, 4, '已取消', 'cancelled', 'recruit_background_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工取消，或因被新背调替代而失效');

-- 27. 背调未通过原因分类 recruit_background_failure_reason
--     设计文档 §10 无此组，按 §8.7「未通过原因分类」新增
insert into sys_dict_data values(1766300000000000239, 1, '信息不符',         'info_mismatch',   'recruit_background_failure_reason', '', 'warning', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人提供的基础信息与核查结果不一致');
insert into sys_dict_data values(1766300000000000240, 2, '工作经历不一致',   'work_experience', 'recruit_background_failure_reason', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '任职时间、单位或岗位与事实不符');
insert into sys_dict_data values(1766300000000000241, 3, '学历或证书不一致', 'education',       'recruit_background_failure_reason', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '学历学位或资质证书无法核实');
insert into sys_dict_data values(1766300000000000242, 4, '职位职责不一致',   'position_duty',   'recruit_background_failure_reason', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '职位名称或职责范围与事实不符');
insert into sys_dict_data values(1766300000000000243, 5, '业绩表现不一致',   'performance',     'recruit_background_failure_reason', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '业绩或绩效记录与事实不符');
insert into sys_dict_data values(1766300000000000244, 6, '法律或信用记录',   'legal_record',    'recruit_background_failure_reason', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '存在法律纠纷、失信或不良信用记录');
insert into sys_dict_data values(1766300000000000245, 7, '其他',             'other',           'recruit_background_failure_reason', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '不属于上述分类的其他未通过原因');

-- 28. 面试状态 recruit_interview_status
insert into sys_dict_data values(1766300000000000246, 1, '待安排', 'pending',     'recruit_interview_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试已创建但时间未定');
insert into sys_dict_data values(1766300000000000247, 2, '已安排', 'scheduled',   'recruit_interview_status', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试时间与方式已确定');
insert into sys_dict_data values(1766300000000000248, 3, '已完成', 'finished',    'recruit_interview_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试已结束并回执');
insert into sys_dict_data values(1766300000000000249, 4, '已取消', 'cancelled',   'recruit_interview_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试被取消，保留历史记录');
insert into sys_dict_data values(1766300000000000250, 5, '已改期', 'rescheduled', 'recruit_interview_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '原安排被改期，作为历史记录保留');

-- 29. 面试方式 recruit_interview_method
insert into sys_dict_data values(1766300000000000251, 1, '现场', 'onsite', 'recruit_interview_method', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '线下面试，地点填写在 location');
insert into sys_dict_data values(1766300000000000252, 2, '视频', 'video',  'recruit_interview_method', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '线上视频面试，会议链接填写在 location');
insert into sys_dict_data values(1766300000000000253, 3, '电话', 'phone',  'recruit_interview_method', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '电话面试，location 可为空');

-- 30. 邀约结果 recruit_offer_result
--     hr_recruit_application.offer_result 建表注释「accepted/rejected等稳定编码，设计文档 §7.2」；
--     §14「邀约接受率＝接受邀约人数÷有效邀约人数」按 accepted 统计。
insert into sys_dict_data values(1766300000000000254, 1, '待反馈',        'pending',   'recruit_offer_result', '',     'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '已发出邀约，候选人尚未答复');
insert into sys_dict_data values(1766300000000000255, 2, '已接受',        'accepted',  'recruit_offer_result', '',     'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人接受邀约，计入邀约接受率分子');
insert into sys_dict_data values(1766300000000000256, 3, '已拒绝',        'rejected',  'recruit_offer_result', '',     'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人拒绝邀约');

-- 31. 阶段变更原因分类 recruit_stage_reason_code
--     hr_recruit_stage_log.reason_code 建表注释「原因编码（字典编码，不存中文）」+ §8.5「原因分类」；
--     *** 设计文档未枚举具体编码，下列 8 个编码为本次新定义。***
insert into sys_dict_data values(1766300000000000257, 1, '技能不匹配',          'skill_mismatch',        'recruit_stage_reason_code', '', 'warning', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人所具备技能与岗位要求不匹配');
insert into sys_dict_data values(1766300000000000258, 2, '经验年限不符',        'experience_mismatch',   'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '相关工作年限低于岗位要求');
insert into sys_dict_data values(1766300000000000259, 3, '薪资不符',            'salary_mismatch',       'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '期望薪资与岗位预算区间不一致');
insert into sys_dict_data values(1766300000000000260, 4, '学历不符',            'education_mismatch',    'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '学历或专业不满足岗位要求');
insert into sys_dict_data values(1766300000000000261, 5, '沟通表现不符',        'communication',         'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '沟通表达或职业素养不符合要求');
insert into sys_dict_data values(1766300000000000262, 6, '候选人主动放弃',      'candidate_declined',    'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人主动退出流程（区别于企业淘汰）');
insert into sys_dict_data values(1766300000000000263, 7, '岗位已关闭',          'position_closed',       'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '岗位暂停或关闭导致流程终止');
insert into sys_dict_data values(1766300000000000264, 8, '其他',                'other',                 'recruit_stage_reason_code', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '不属于上述分类的其他原因');

-- 32. 学历 talent_education
--     hr_talent_education.education / hr_talent_profile.highest_education 建表注释「字典编码」；
--     §10 无此组，本组 6 个编码为本次新定义。
insert into sys_dict_data values(1766300000000000266, 1, '高中', 'high_school', 'talent_education', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '高中及同等学力');
insert into sys_dict_data values(1766300000000000267, 2, '大专', 'college',     'talent_education', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '大专学历');
insert into sys_dict_data values(1766300000000000268, 3, '本科', 'bachelor',    'talent_education', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本科学历');
insert into sys_dict_data values(1766300000000000269, 4, '硕士', 'master',      'talent_education', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '硕士研究生学历');
insert into sys_dict_data values(1766300000000000270, 5, '博士', 'doctor',      'talent_education', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '博士研究生学历');
insert into sys_dict_data values(1766300000000000271, 6, '其他', 'other',       'talent_education', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他学历形式');

-- 33. 学位 talent_degree
--     hr_talent_education.degree 建表注释「字典编码」；§10 无此组，本组 5 个编码为本次新定义。
insert into sys_dict_data values(1766300000000000272, 1, '无',   'none',     'talent_degree', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未取得学位');
insert into sys_dict_data values(1766300000000000273, 2, '学士', 'bachelor', 'talent_degree', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '学士学位');
insert into sys_dict_data values(1766300000000000274, 3, '硕士', 'master',   'talent_degree', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '硕士学位');
insert into sys_dict_data values(1766300000000000275, 4, '博士', 'doctor',   'talent_degree', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '博士学位');
insert into sys_dict_data values(1766300000000000276, 5, '其他', 'other',    'talent_degree', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '其他学位类型');

-- 34. 简历解析复核状态 talent_resume_review_status
--     hr_talent_resume.review_status 专用；§10 无此组，本组 4 个编码为本次新定义。
--     parse_status 继续使用 talent_resume_parse_status，两列两字典不得混用。
insert into sys_dict_data values(1766300000000000277, 1, '待复核', 'pending',   'talent_resume_review_status', '', 'info',    'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '解析结果待人工复核');
insert into sys_dict_data values(1766300000000000278, 2, '复核中', 'reviewing', 'talent_resume_review_status', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工复核进行中');
insert into sys_dict_data values(1766300000000000279, 3, '已确认', 'confirmed', 'talent_resume_review_status', '', 'success', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '复核通过，可写入正式人才字段');
insert into sys_dict_data values(1766300000000000280, 4, '已否决', 'rejected',  'talent_resume_review_status', '', 'danger',  'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '复核不通过，解析结果不予采信');

-- 35. 数据来源类型 talent_source_type
--     hr_talent_resume/education/work/project.source_type 建表注释「人工/导入/解析等稳定编码」；
--     §10 无此组，本组 4 个编码为本次新定义。
insert into sys_dict_data values(1766300000000000281, 1, '人工录入', 'manual', 'talent_source_type', '', 'primary', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人工录入或页面手工维护');
insert into sys_dict_data values(1766300000000000282, 2, '导入',     'import', 'talent_source_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '批量导入或文件导入');
insert into sys_dict_data values(1766300000000000283, 3, '简历解析', 'parse',  'talent_source_type', '', 'warning', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '由简历解析任务写入');
insert into sys_dict_data values(1766300000000000284, 4, '系统生成', 'system', 'talent_source_type', '', 'info',    'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '由系统任务或规则自动生成');

-- 36. 人才分组类型 talent_group_type
--     hr_talent_group.group_type 建表注释 + §8.15「公共分组 / 个人收藏」；
--     本组 2 个编码为本次新定义。
insert into sys_dict_data values(1766300000000000285, 1, '公共分组', 'public',   'talent_group_type', '', 'success', 'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '由部门或管理员维护的公共人才分组');
insert into sys_dict_data values(1766300000000000286, 2, '个人收藏', 'personal', 'talent_group_type', '', 'primary', 'N', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '用户私有的个人收藏分组');


-- ----------------------------
-- 三、角色（9 个，设计文档 §6）
-- column: role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
--         dept_check_strictly, status, del_flag, create_dept, create_by, create_time,
--         update_by, update_time, remark
-- data_scope：1=全部 3=本部门 4=本部门及以下 5=仅本人（集团/公司/授权部门等跨公司范围
--   由 TalentScopeDomainService 按 §21.14 生成，RoleDept 自定义范围在 P4 随组织机构数据配置）
-- ----------------------------
insert into sys_role values(1766100000000000001, '平台管理员',     'hr_platform_admin',        10, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '全平台：系统配置、账号、菜单和基础运维；原则上不承担招聘业务数据日常维护');
insert into sys_role values(1766100000000000002, '集团招聘管理员', 'hr_recruit_admin_group',   11, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '集团全部组织：全部招聘业务、标准、渠道、统计和审计；附件不得直接物理删除');
insert into sys_role values(1766100000000000003, '集团人才管理员', 'hr_talent_admin_group',    12, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '集团共享人才及授权公司人才：人才主档、人才池、标签、重复治理、共享授权和人才统计；高敏感附件仍需独立权限');
insert into sys_role values(1766100000000000004, '公司招聘负责人', 'hr_company_recruit_owner', 13, 4, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本公司及授权部门：需求、岗位、候选人、面试、背调和报到；不能查看其他公司的候选人明文和背调');
insert into sys_role values(1766100000000000005, '招聘专员',       'hr_recruiter',             14, 5, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本人负责或本部门：录入候选人、跟进、安排面试和上传附件；导出、电话明文和背调需单独授权');
insert into sys_role values(1766100000000000006, '用人部门负责人', 'hr_dept_owner',            15, 3, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '本部门岗位和相关候选人：提交需求、查看进度、填写面试意见；不可浏览无关候选人');
insert into sys_role values(1766100000000000007, '面试官',         'hr_interviewer',           16, 5, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '被安排的面试任务：查看必要简历和提交面试反馈；授权随任务结束到期，不可批量导出');
insert into sys_role values(1766100000000000008, '审计查看者',     'hr_auditor',               17, 1, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '审计授权范围：查看敏感操作记录；默认不可下载候选人附件');
insert into sys_role values(1766100000000000009, '人才库查阅者',   'hr_talent_viewer',         18, 4, 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '被授权人才池或人才档案：检索、查看脱敏人才摘要及授权附件；不可修改、导出或查看背调资料');

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
--   * 「招聘管理」的 order_num = 6，避开既有顶层菜单已占用的 1/3/4/5/9：
--     ry_vue.sql 基线为 系统管理=1、系统监控=3、系统工具=4、测试菜单=5、PLUS官网=9；
--     纵享工作空间迁移（zongxiang_workspace_menu.sql）另占 1~5（工作台/AI工具/业务应用/审批协同/管理中心）。
--     取 6 可在「纯基线」与「已套纵享迁移」两种库上都排到最后且不与任何顶层菜单并列。
--   * 【重要】以下 5 个菜单的 visible 刻意设为 '1'（隐藏），因为其**后端整层与前端页面尚未实现**：
--       管理驾驶舱 1766000000000000101   component='hrtalent/dashboard/index'
--       招聘渠道   1766000000000000109   component='hrtalent/channel/index'
--       同行信息   1766000000000000110   component='hrtalent/peer/index'
--       招聘标准   1766000000000000111   component='hrtalent/standard/index'
--       数据导入中心 1766000000000000112 component='hrtalent/import-center/index'
--     DDL（hr_recruit_channel / hr_recruit_peer_company / hr_recruit_standard /
--     hr_recruit_import_batch / hr_recruit_import_error）、权限常量与菜单均已在 P1 地基就位，
--     但实体/Mapper/服务/控制器/前端页面整体缺失，**若显示出来用户点进去只会得到空白页**
--     （前端 loadView() 对缺失组件返回 undefined，不会崩整棵路由树）。
--     其中「管理驾驶舱」「数据导入中心」属设计文档阶段 4（SPEC-P2 §0 / SPEC-P4 §0 均明确不在前期范围）；
--     「招聘渠道 / 同行信息 / 招聘标准」未被指派到任何一期，属未分配的范围缺口。
--     用户裁定：**先隐藏，功能另行排期**。功能实现后把 visible 改回 '0' 即可恢复显示。
--     注意：其下的 F 按钮（权限串）**保留不动**——权限已绑定角色，功能上线后无需再改权限配置。
-- ----------------------------------------
-- ----------------------------
-- 一级目录
insert into sys_menu values(1766000000000000001, '招聘管理', 0, 6, 'recruit', null, '', 'N', 'Y', 'M', '0', '0', '', 'peoples', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '招聘与人才管理一体化系统（招聘过程 + 人才主数据）');

-- 1 管理驾驶舱
insert into sys_menu values(1766000000000000101, '管理驾驶舱', 1766000000000000001, 1, 'dashboard', 'hrtalent/dashboard/index', '', 'N', 'Y', 'C', '1', '0', 'recruit:dashboard:view', 'monitor', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '招聘概览与核心指标');
-- 2 招聘需求
insert into sys_menu values(1766000000000000102, '招聘需求', 1766000000000000001, 2, 'demand', 'hrtalent/demand/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:demand:list', 'list', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '需求来源、审批与状态流转');
-- 3 公司月度计划
insert into sys_menu values(1766000000000000103, '公司月度计划', 1766000000000000001, 3, 'plan', 'hrtalent/plan/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:plan:list', 'date', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '公司月度招聘计划与月度任务');
-- 4 月度结转中心
insert into sys_menu values(1766000000000000104, '月度结转中心', 1766000000000000001, 4, 'rollover', 'hrtalent/rollover/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:rollover:preview', 'workflow', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '未完成月度任务结转预览与执行');
-- 5 岗位需求
insert into sys_menu values(1766000000000000105, '岗位需求', 1766000000000000001, 5, 'job', 'hrtalent/job/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:job:list', 'job', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '岗位需求发布与招聘负责人分配');
-- 6 候选人跟进
insert into sys_menu values(1766000000000000106, '候选人跟进', 1766000000000000001, 6, 'application', 'hrtalent/application/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:candidate:list', 'user', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '候选人应聘记录、阶段流转与跟进');
-- 7 面试管理
insert into sys_menu values(1766000000000000107, '面试管理', 1766000000000000001, 7, 'interview', 'hrtalent/interview/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:interview:list', 'message', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '面试安排、面试官与面试反馈');
-- 8 背调与报到
insert into sys_menu values(1766000000000000108, '背调与报到', 1766000000000000001, 8, 'background', 'hrtalent/background/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:background:list', 'eye-open', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '背景调查与报到登记（高敏感）');
-- 9 人才管理（二级目录）
insert into sys_menu values(1766000000000000201, '人才管理', 1766000000000000001, 9, 'talent', null, '', 'N', 'Y', 'M', '0', '0', '', 'company', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才主数据二级目录');
--   9.1 人才档案
insert into sys_menu values(1766000000000000202, '人才档案', 1766000000000000201, 1, 'profile', 'hrtalent/talent-profile/index', '', 'N', 'Y', 'C', '0', '0', 'talent:profile:list', 'user', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '一人一档的人才主档');
--   9.2 人才池与分组
insert into sys_menu values(1766000000000000203, '人才池与分组', 1766000000000000201, 2, 'pool', 'hrtalent/talent-pool/index', '', 'N', 'Y', 'C', '0', '0', 'talent:pool:list', 'my-copy', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才池、成员分组与标签');
--   9.3 简历中心
insert into sys_menu values(1766000000000000204, '简历中心', 1766000000000000201, 3, 'resume', 'hrtalent/resume/index', '', 'N', 'Y', 'C', '0', '0', 'talent:resume:list', 'documentation', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '简历上传、解析、版本与人工复核');
--   9.4 重复人才治理
insert into sys_menu values(1766000000000000205, '重复人才治理', 1766000000000000201, 4, 'duplicate', 'hrtalent/duplicate/index', '', 'N', 'Y', 'C', '0', '0', 'talent:duplicate:list', 'search', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '疑似重复人才确认、合并与忽略');
--   9.5 人才共享授权
insert into sys_menu values(1766000000000000206, '人才共享授权', 1766000000000000201, 5, 'grant', 'hrtalent/grant/index', '', 'N', 'Y', 'C', '0', '0', 'talent:grant:list', 'lock', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '人才可见范围与共享授权（含撤销）');
-- 10 招聘渠道
insert into sys_menu values(1766000000000000109, '招聘渠道', 1766000000000000001, 10, 'channel', 'hrtalent/channel/index', '', 'N', 'Y', 'C', '1', '0', 'recruit:channel:list', 'link', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '招聘渠道主数据与渠道效果');
-- 11 同行信息
insert into sys_menu values(1766000000000000110, '同行信息', 1766000000000000001, 11, 'peer', 'hrtalent/peer/index', '', 'N', 'Y', 'C', '1', '0', 'recruit:peer:list', 'company', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '同行公司与人才市场信息');
-- 12 招聘标准
insert into sys_menu values(1766000000000000111, '招聘标准', 1766000000000000001, 12, 'standard', 'hrtalent/standard/index', '', 'N', 'Y', 'C', '1', '0', 'recruit:standard:list', 'skill', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '岗位招聘标准与评价项');
-- 13 数据导入中心
insert into sys_menu values(1766000000000000112, '数据导入中心', 1766000000000000001, 13, 'import-center', 'hrtalent/import-center/index', '', 'N', 'Y', 'C', '1', '0', 'recruit:import:list', 'upload', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, 'Excel 模板下载、上传、预检与确认导入');
-- 14 敏感操作审计
insert into sys_menu values(1766000000000000113, '敏感操作审计', 1766000000000000001, 14, 'audit', 'hrtalent/audit/index', '', 'N', 'Y', 'C', '0', '0', 'recruit:audit:list', 'eye', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '敏感操作审计记录（只读）');

-- ---- 按钮：管理驾驶舱 recruit:dashboard:view/export ----
insert into sys_menu values(1766000000000001001, '驾驶舱查看', 1766000000000000101, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:dashboard:view',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001002, '驾驶舱导出', 1766000000000000101, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:dashboard:export', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：招聘需求 recruit:demand:list/query/add/edit/submit/pause/close/import/export ----
insert into sys_menu values(1766000000000001101, '需求查询', 1766000000000000102, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:list',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001102, '需求详情', 1766000000000000102, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:query',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001103, '需求新增', 1766000000000000102, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001104, '需求编辑', 1766000000000000102, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001105, '需求提交', 1766000000000000102, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:submit', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001106, '需求暂停', 1766000000000000102, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:pause',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001107, '需求关闭', 1766000000000000102, 7, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:close',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001108, '需求导入', 1766000000000000102, 8, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:import', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001109, '需求导出', 1766000000000000102, 9, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:demand:export', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：公司月度计划 recruit:plan:list/query/add/edit/confirm/close/export ----
insert into sys_menu values(1766000000000001201, '计划查询', 1766000000000000103, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:list',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001202, '计划详情', 1766000000000000103, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:query',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001203, '计划新增', 1766000000000000103, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:add',     '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001204, '计划编辑', 1766000000000000103, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:edit',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001205, '计划确认', 1766000000000000103, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:confirm', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001206, '计划关闭', 1766000000000000103, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:close',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001207, '计划导出', 1766000000000000103, 7, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:plan:export',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：月度结转中心 recruit:rollover:preview/execute/retry/detail ----
insert into sys_menu values(1766000000000001301, '结转预览', 1766000000000000104, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:rollover:preview', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001302, '结转执行', 1766000000000000104, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:rollover:execute', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001303, '结转重试', 1766000000000000104, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:rollover:retry',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001304, '结转明细', 1766000000000000104, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:rollover:detail',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：岗位需求 recruit:job:list/query/add/edit/assign/close ----
insert into sys_menu values(1766000000000001401, '岗位查询', 1766000000000000105, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:list',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001402, '岗位详情', 1766000000000000105, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:query',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001403, '岗位新增', 1766000000000000105, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001404, '岗位编辑', 1766000000000000105, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001405, '分配负责人', 1766000000000000105, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:assign', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001406, '岗位关闭', 1766000000000000105, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:job:close',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：候选人跟进 recruit:candidate:list/query/add/edit/transfer/stage/phone-view/export ----
insert into sys_menu values(1766000000000001501, '候选人查询', 1766000000000000106, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:list',       '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001502, '候选人详情', 1766000000000000106, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:query',      '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001503, '候选人新增', 1766000000000000106, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:add',        '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001504, '候选人编辑', 1766000000000000106, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:edit',       '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001505, '候选人转移', 1766000000000000106, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:transfer',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001506, '阶段流转',   1766000000000000106, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:stage',      '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001507, '电话明文查看', 1766000000000000106, 7, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:phone-view', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资源级鉴权：查看电话明文并写审计');
insert into sys_menu values(1766000000000001508, '候选人导出', 1766000000000000106, 8, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:candidate:export',     '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：候选人附件 recruit:attachment:upload/preview/download/delete ----
-- 挂载在「候选人跟进」下：设计文档 §12 候选人详情包含附件，§5.2 明确附件四权限
insert into sys_menu values(1766000000000001509, '附件上传', 1766000000000000106, 9, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:attachment:upload',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001510, '附件预览', 1766000000000000106, 10, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:attachment:preview',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资源级鉴权：受控预览并写敏感操作审计');
insert into sys_menu values(1766000000000001511, '附件下载', 1766000000000000106, 11, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:attachment:download', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资源级鉴权：受控下载并写敏感操作审计');
insert into sys_menu values(1766000000000001512, '附件删除', 1766000000000000106, 12, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:attachment:delete',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '逻辑删除：集团招聘管理员亦不得物理删除');

-- ---- 按钮：面试管理 recruit:interview:list/schedule/feedback/cancel ----
insert into sys_menu values(1766000000000001601, '面试查询', 1766000000000000107, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:interview:list',     '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001602, '面试安排', 1766000000000000107, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:interview:schedule', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001603, '面试反馈', 1766000000000000107, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:interview:feedback', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001604, '面试取消', 1766000000000000107, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:interview:cancel',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：背调与报到 recruit:background:list/add/edit/view-sensitive ----
insert into sys_menu values(1766000000000001701, '背调查询', 1766000000000000108, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:background:list',           '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001702, '背调新增', 1766000000000000108, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:background:add',            '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001703, '背调编辑', 1766000000000000108, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:background:edit',           '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001704, '背调明细查看', 1766000000000000108, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:background:view-sensitive', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '高敏感：查看背调明细并写审计');

-- ---- 按钮：人才档案 talent:profile:list/query/add/edit/archive/phone-view/export ----
insert into sys_menu values(1766000000000001801, '人才查询', 1766000000000000202, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:list',       '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001802, '人才详情', 1766000000000000202, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:query',      '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001803, '人才新增', 1766000000000000202, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:add',        '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001804, '人才编辑', 1766000000000000202, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:edit',       '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001805, '人才归档', 1766000000000000202, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:archive',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001806, '电话明文查看', 1766000000000000202, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:phone-view', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资源级鉴权：查看电话明文并写审计');
insert into sys_menu values(1766000000000001807, '人才导出', 1766000000000000202, 7, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:profile:export',     '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：人才池与分组 talent:pool:list/add/edit/member/share ----
insert into sys_menu values(1766000000000001901, '人才池查询', 1766000000000000203, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:pool:list',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001902, '人才池新增', 1766000000000000203, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:pool:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001903, '人才池编辑', 1766000000000000203, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:pool:edit',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001904, '池成员维护', 1766000000000000203, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:pool:member', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000001905, '人才池共享', 1766000000000000203, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:pool:share',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：简历中心 talent:resume:list/upload/download/parse/review/version ----
insert into sys_menu values(1766000000000002001, '简历查询', 1766000000000000204, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:list',     '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002002, '简历上传', 1766000000000000204, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:upload',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002003, '简历下载', 1766000000000000204, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:download', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '资源级鉴权：受控下载并写审计');
insert into sys_menu values(1766000000000002004, '简历解析', 1766000000000000204, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:parse',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002005, '解析复核', 1766000000000000204, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:review',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002006, '简历版本', 1766000000000000204, 6, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:resume:version',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：重复人才治理 talent:duplicate:list/confirm/merge/ignore ----
insert into sys_menu values(1766000000000002101, '重复查询', 1766000000000000205, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:duplicate:list',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002102, '重复确认', 1766000000000000205, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:duplicate:confirm', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002103, '人才合并', 1766000000000000205, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:duplicate:merge',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002104, '重复忽略', 1766000000000000205, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:duplicate:ignore',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：人才共享授权 talent:grant:list/add/revoke ----
insert into sys_menu values(1766000000000002201, '授权查询', 1766000000000000206, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:grant:list',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002202, '授权新增', 1766000000000000206, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:grant:add',    '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002203, '授权撤销', 1766000000000000206, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'talent:grant:revoke', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：招聘渠道 recruit:channel:list/query/add/edit ----
insert into sys_menu values(1766000000000002301, '渠道查询', 1766000000000000109, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:channel:list',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002302, '渠道详情', 1766000000000000109, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:channel:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002303, '渠道新增', 1766000000000000109, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:channel:add',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002304, '渠道编辑', 1766000000000000109, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:channel:edit',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：同行信息 recruit:peer:list/query/add/edit ----
insert into sys_menu values(1766000000000002401, '同行查询', 1766000000000000110, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:peer:list',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002402, '同行详情', 1766000000000000110, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:peer:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002403, '同行新增', 1766000000000000110, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:peer:add',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002404, '同行编辑', 1766000000000000110, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:peer:edit',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：招聘标准 recruit:standard:list/query/add/edit ----
insert into sys_menu values(1766000000000002501, '标准查询', 1766000000000000111, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:standard:list',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002502, '标准详情', 1766000000000000111, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:standard:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002503, '标准新增', 1766000000000000111, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:standard:add',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002504, '标准编辑', 1766000000000000111, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:standard:edit',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：数据导入中心 recruit:import:template/upload/confirm/cancel/detail ----
insert into sys_menu values(1766000000000002601, '导入模板', 1766000000000000112, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:import:template', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002602, '导入上传', 1766000000000000112, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:import:upload',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002603, '导入确认', 1766000000000000112, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:import:confirm',  '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002604, '导入取消', 1766000000000000112, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:import:cancel',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002605, '导入明细', 1766000000000000112, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:import:detail',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ---- 按钮：敏感操作审计 recruit:audit:list/export ----
insert into sys_menu values(1766000000000002701, '审计查询', 1766000000000000113, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:audit:list',   '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu values(1766000000000002702, '审计导出', 1766000000000000113, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'recruit:audit:export', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

-- ----------------------------
-- 五、角色-菜单绑定（按设计文档 §6 的职责与限制分配）
-- column: role_id, menu_id
-- 约定：角色能看到的页面必授其一、二级目录，否则菜单树不显示；
--       只读角色只授 list/query/detail/detail-view 类权限点。
-- ----------------------------
-- 1) 平台管理员 hr_platform_admin：系统配置、账号、菜单和基础运维；
--    不承担招聘业务数据日常维护 → 只授一级目录 + 管理驾驶舱 + 招聘标准 + 审计（读）。
insert into sys_role_menu values (1766100000000000001, 1766000000000000001);
insert into sys_role_menu values (1766100000000000001, 1766000000000000101);
insert into sys_role_menu values (1766100000000000001, 1766000000000001001);
insert into sys_role_menu values (1766100000000000001, 1766000000000000111);
insert into sys_role_menu values (1766100000000000001, 1766000000000002501);
insert into sys_role_menu values (1766100000000000001, 1766000000000002502);
insert into sys_role_menu values (1766100000000000001, 1766000000000000113);
insert into sys_role_menu values (1766100000000000001, 1766000000000002701);

-- 2) 集团招聘管理员 hr_recruit_admin_group：集团全部招聘业务、标准、渠道、统计和审计。
--    不含人才管理子树（该子树归集团人才管理员）。
--    背调明细（recruit:background:view-sensitive）与候选人电话明文（recruit:candidate:phone-view）
--    属 §6 所述"高敏感资源级权限"：本角色作为集团级招聘负责人默认持有，但记录级仍受可见范围
--    （TalentScopeDomainService）与授权级别（summary/detail/attachment）约束，且每次访问强制写审计。
--    §6 的"不能查看其他公司的候选人明文和背调"由可见范围落地，不再靠"不授权限"实现；
--    公司招聘负责人（hr_company_recruit_owner）仍不授这两项。
insert into sys_role_menu values (1766100000000000002, 1766000000000000001);
insert into sys_role_menu values (1766100000000000002, 1766000000000000101);
insert into sys_role_menu values (1766100000000000002, 1766000000000001001);
insert into sys_role_menu values (1766100000000000002, 1766000000000001002);
insert into sys_role_menu values (1766100000000000002, 1766000000000000102);
insert into sys_role_menu values (1766100000000000002, 1766000000000001101);
insert into sys_role_menu values (1766100000000000002, 1766000000000001102);
insert into sys_role_menu values (1766100000000000002, 1766000000000001103);
insert into sys_role_menu values (1766100000000000002, 1766000000000001104);
insert into sys_role_menu values (1766100000000000002, 1766000000000001105);
insert into sys_role_menu values (1766100000000000002, 1766000000000001106);
insert into sys_role_menu values (1766100000000000002, 1766000000000001107);
insert into sys_role_menu values (1766100000000000002, 1766000000000001108);
insert into sys_role_menu values (1766100000000000002, 1766000000000001109);
insert into sys_role_menu values (1766100000000000002, 1766000000000000103);
insert into sys_role_menu values (1766100000000000002, 1766000000000001201);
insert into sys_role_menu values (1766100000000000002, 1766000000000001202);
insert into sys_role_menu values (1766100000000000002, 1766000000000001203);
insert into sys_role_menu values (1766100000000000002, 1766000000000001204);
insert into sys_role_menu values (1766100000000000002, 1766000000000001205);
insert into sys_role_menu values (1766100000000000002, 1766000000000001206);
insert into sys_role_menu values (1766100000000000002, 1766000000000001207);
insert into sys_role_menu values (1766100000000000002, 1766000000000000104);
insert into sys_role_menu values (1766100000000000002, 1766000000000001301);
insert into sys_role_menu values (1766100000000000002, 1766000000000001302);
insert into sys_role_menu values (1766100000000000002, 1766000000000001303);
insert into sys_role_menu values (1766100000000000002, 1766000000000001304);
insert into sys_role_menu values (1766100000000000002, 1766000000000000105);
insert into sys_role_menu values (1766100000000000002, 1766000000000001401);
insert into sys_role_menu values (1766100000000000002, 1766000000000001402);
insert into sys_role_menu values (1766100000000000002, 1766000000000001403);
insert into sys_role_menu values (1766100000000000002, 1766000000000001404);
insert into sys_role_menu values (1766100000000000002, 1766000000000001405);
insert into sys_role_menu values (1766100000000000002, 1766000000000001406);
insert into sys_role_menu values (1766100000000000002, 1766000000000000106);
insert into sys_role_menu values (1766100000000000002, 1766000000000001501);
insert into sys_role_menu values (1766100000000000002, 1766000000000001502);
insert into sys_role_menu values (1766100000000000002, 1766000000000001503);
insert into sys_role_menu values (1766100000000000002, 1766000000000001504);
insert into sys_role_menu values (1766100000000000002, 1766000000000001505);
insert into sys_role_menu values (1766100000000000002, 1766000000000001506);
insert into sys_role_menu values (1766100000000000002, 1766000000000001507);
insert into sys_role_menu values (1766100000000000002, 1766000000000001508);
-- 候选人附件四权限：与候选人的 list/query/add/edit 授权角色保持一致（集团招聘管理员/公司招聘负责人/招聘专员）
insert into sys_role_menu values (1766100000000000002, 1766000000000001509);
insert into sys_role_menu values (1766100000000000002, 1766000000000001510);
insert into sys_role_menu values (1766100000000000002, 1766000000000001511);
insert into sys_role_menu values (1766100000000000002, 1766000000000001512);
insert into sys_role_menu values (1766100000000000002, 1766000000000000107);
insert into sys_role_menu values (1766100000000000002, 1766000000000001601);
insert into sys_role_menu values (1766100000000000002, 1766000000000001602);
insert into sys_role_menu values (1766100000000000002, 1766000000000001603);
insert into sys_role_menu values (1766100000000000002, 1766000000000001604);
insert into sys_role_menu values (1766100000000000002, 1766000000000000108);
insert into sys_role_menu values (1766100000000000002, 1766000000000001701);
insert into sys_role_menu values (1766100000000000002, 1766000000000001702);
insert into sys_role_menu values (1766100000000000002, 1766000000000001703);
-- 背调明细查看：集团招聘管理员默认持有（记录级受可见范围约束，每次访问写审计）
insert into sys_role_menu values (1766100000000000002, 1766000000000001704);
insert into sys_role_menu values (1766100000000000002, 1766000000000000109);
insert into sys_role_menu values (1766100000000000002, 1766000000000002301);
insert into sys_role_menu values (1766100000000000002, 1766000000000002302);
insert into sys_role_menu values (1766100000000000002, 1766000000000002303);
insert into sys_role_menu values (1766100000000000002, 1766000000000002304);
insert into sys_role_menu values (1766100000000000002, 1766000000000000110);
insert into sys_role_menu values (1766100000000000002, 1766000000000002401);
insert into sys_role_menu values (1766100000000000002, 1766000000000002402);
insert into sys_role_menu values (1766100000000000002, 1766000000000002403);
insert into sys_role_menu values (1766100000000000002, 1766000000000002404);
insert into sys_role_menu values (1766100000000000002, 1766000000000000111);
insert into sys_role_menu values (1766100000000000002, 1766000000000002501);
insert into sys_role_menu values (1766100000000000002, 1766000000000002502);
insert into sys_role_menu values (1766100000000000002, 1766000000000002503);
insert into sys_role_menu values (1766100000000000002, 1766000000000002504);
insert into sys_role_menu values (1766100000000000002, 1766000000000000112);
insert into sys_role_menu values (1766100000000000002, 1766000000000002601);
insert into sys_role_menu values (1766100000000000002, 1766000000000002602);
insert into sys_role_menu values (1766100000000000002, 1766000000000002603);
insert into sys_role_menu values (1766100000000000002, 1766000000000002604);
insert into sys_role_menu values (1766100000000000002, 1766000000000002605);
insert into sys_role_menu values (1766100000000000002, 1766000000000000113);
insert into sys_role_menu values (1766100000000000002, 1766000000000002701);
insert into sys_role_menu values (1766100000000000002, 1766000000000002702);

-- 3) 集团人才管理员 hr_talent_admin_group：人才主档、人才池、标签、重复治理、共享授权和人才统计。
--    人才电话明文（talent:profile:phone-view）与简历下载（talent:resume:download）为独立登记的
--    资源级权限：默认授给集团人才管理员，但记录级仍受人才可见范围与共享授权级别约束，
--    且每次访问强制写审计（含被拒绝的访问）。
insert into sys_role_menu values (1766100000000000003, 1766000000000000001);
insert into sys_role_menu values (1766100000000000003, 1766000000000000101);
insert into sys_role_menu values (1766100000000000003, 1766000000000001001);
insert into sys_role_menu values (1766100000000000003, 1766000000000001002);
insert into sys_role_menu values (1766100000000000003, 1766000000000000201);
insert into sys_role_menu values (1766100000000000003, 1766000000000000202);
insert into sys_role_menu values (1766100000000000003, 1766000000000001801);
insert into sys_role_menu values (1766100000000000003, 1766000000000001802);
insert into sys_role_menu values (1766100000000000003, 1766000000000001803);
insert into sys_role_menu values (1766100000000000003, 1766000000000001804);
insert into sys_role_menu values (1766100000000000003, 1766000000000001805);
insert into sys_role_menu values (1766100000000000003, 1766000000000001806);
insert into sys_role_menu values (1766100000000000003, 1766000000000001807);
insert into sys_role_menu values (1766100000000000003, 1766000000000000203);
insert into sys_role_menu values (1766100000000000003, 1766000000000001901);
insert into sys_role_menu values (1766100000000000003, 1766000000000001902);
insert into sys_role_menu values (1766100000000000003, 1766000000000001903);
insert into sys_role_menu values (1766100000000000003, 1766000000000001904);
insert into sys_role_menu values (1766100000000000003, 1766000000000001905);
insert into sys_role_menu values (1766100000000000003, 1766000000000000204);
insert into sys_role_menu values (1766100000000000003, 1766000000000002001);
insert into sys_role_menu values (1766100000000000003, 1766000000000002002);
insert into sys_role_menu values (1766100000000000003, 1766000000000002003);
insert into sys_role_menu values (1766100000000000003, 1766000000000002004);
insert into sys_role_menu values (1766100000000000003, 1766000000000002005);
insert into sys_role_menu values (1766100000000000003, 1766000000000002006);
insert into sys_role_menu values (1766100000000000003, 1766000000000000205);
insert into sys_role_menu values (1766100000000000003, 1766000000000002101);
insert into sys_role_menu values (1766100000000000003, 1766000000000002102);
insert into sys_role_menu values (1766100000000000003, 1766000000000002103);
insert into sys_role_menu values (1766100000000000003, 1766000000000002104);
insert into sys_role_menu values (1766100000000000003, 1766000000000000206);
insert into sys_role_menu values (1766100000000000003, 1766000000000002201);
insert into sys_role_menu values (1766100000000000003, 1766000000000002202);
insert into sys_role_menu values (1766100000000000003, 1766000000000002203);
insert into sys_role_menu values (1766100000000000003, 1766000000000000113);
insert into sys_role_menu values (1766100000000000003, 1766000000000002701);

-- 4) 公司招聘负责人 hr_company_recruit_owner：本公司及授权部门的需求、岗位、候选人、面试、背调和报到。
--    不能查看其他公司的候选人明文和背调 → 不授 candidate:phone-view、background:view-sensitive、audit:*。
insert into sys_role_menu values (1766100000000000004, 1766000000000000001);
insert into sys_role_menu values (1766100000000000004, 1766000000000000101);
insert into sys_role_menu values (1766100000000000004, 1766000000000001001);
insert into sys_role_menu values (1766100000000000004, 1766000000000001002);
insert into sys_role_menu values (1766100000000000004, 1766000000000000102);
insert into sys_role_menu values (1766100000000000004, 1766000000000001101);
insert into sys_role_menu values (1766100000000000004, 1766000000000001102);
insert into sys_role_menu values (1766100000000000004, 1766000000000001103);
insert into sys_role_menu values (1766100000000000004, 1766000000000001104);
insert into sys_role_menu values (1766100000000000004, 1766000000000001105);
insert into sys_role_menu values (1766100000000000004, 1766000000000001107);
insert into sys_role_menu values (1766100000000000004, 1766000000000001109);
insert into sys_role_menu values (1766100000000000004, 1766000000000000103);
insert into sys_role_menu values (1766100000000000004, 1766000000000001201);
insert into sys_role_menu values (1766100000000000004, 1766000000000001202);
insert into sys_role_menu values (1766100000000000004, 1766000000000001203);
insert into sys_role_menu values (1766100000000000004, 1766000000000001204);
insert into sys_role_menu values (1766100000000000004, 1766000000000001206);
insert into sys_role_menu values (1766100000000000004, 1766000000000001207);
insert into sys_role_menu values (1766100000000000004, 1766000000000000104);
insert into sys_role_menu values (1766100000000000004, 1766000000000001301);
insert into sys_role_menu values (1766100000000000004, 1766000000000001303);
insert into sys_role_menu values (1766100000000000004, 1766000000000001304);
insert into sys_role_menu values (1766100000000000004, 1766000000000000105);
insert into sys_role_menu values (1766100000000000004, 1766000000000001401);
insert into sys_role_menu values (1766100000000000004, 1766000000000001402);
insert into sys_role_menu values (1766100000000000004, 1766000000000001403);
insert into sys_role_menu values (1766100000000000004, 1766000000000001404);
insert into sys_role_menu values (1766100000000000004, 1766000000000001405);
insert into sys_role_menu values (1766100000000000004, 1766000000000001406);
insert into sys_role_menu values (1766100000000000004, 1766000000000000106);
insert into sys_role_menu values (1766100000000000004, 1766000000000001501);
insert into sys_role_menu values (1766100000000000004, 1766000000000001502);
insert into sys_role_menu values (1766100000000000004, 1766000000000001503);
insert into sys_role_menu values (1766100000000000004, 1766000000000001504);
insert into sys_role_menu values (1766100000000000004, 1766000000000001505);
insert into sys_role_menu values (1766100000000000004, 1766000000000001506);
insert into sys_role_menu values (1766100000000000004, 1766000000000001508);
-- 候选人附件四权限：与候选人的 list/query/add/edit 授权角色保持一致
insert into sys_role_menu values (1766100000000000004, 1766000000000001509);
insert into sys_role_menu values (1766100000000000004, 1766000000000001510);
insert into sys_role_menu values (1766100000000000004, 1766000000000001511);
insert into sys_role_menu values (1766100000000000004, 1766000000000001512);
insert into sys_role_menu values (1766100000000000004, 1766000000000000107);
insert into sys_role_menu values (1766100000000000004, 1766000000000001601);
insert into sys_role_menu values (1766100000000000004, 1766000000000001602);
insert into sys_role_menu values (1766100000000000004, 1766000000000001603);
insert into sys_role_menu values (1766100000000000004, 1766000000000001604);
insert into sys_role_menu values (1766100000000000004, 1766000000000000108);
insert into sys_role_menu values (1766100000000000004, 1766000000000001701);
insert into sys_role_menu values (1766100000000000004, 1766000000000001702);
insert into sys_role_menu values (1766100000000000004, 1766000000000001703);

-- 5) 招聘专员 hr_recruiter：录入候选人、跟进、安排面试和上传附件。
--    导出、电话明文和背调需单独授权 → 不授导出/明文/背调/审计权限。
insert into sys_role_menu values (1766100000000000005, 1766000000000000001);
insert into sys_role_menu values (1766100000000000005, 1766000000000000101);
insert into sys_role_menu values (1766100000000000005, 1766000000000001001);
insert into sys_role_menu values (1766100000000000005, 1766000000000000102);
insert into sys_role_menu values (1766100000000000005, 1766000000000001101);
insert into sys_role_menu values (1766100000000000005, 1766000000000001102);
insert into sys_role_menu values (1766100000000000005, 1766000000000000103);
insert into sys_role_menu values (1766100000000000005, 1766000000000001201);
insert into sys_role_menu values (1766100000000000005, 1766000000000001202);
insert into sys_role_menu values (1766100000000000005, 1766000000000000105);
insert into sys_role_menu values (1766100000000000005, 1766000000000001401);
insert into sys_role_menu values (1766100000000000005, 1766000000000001402);
insert into sys_role_menu values (1766100000000000005, 1766000000000000106);
insert into sys_role_menu values (1766100000000000005, 1766000000000001501);
insert into sys_role_menu values (1766100000000000005, 1766000000000001502);
insert into sys_role_menu values (1766100000000000005, 1766000000000001503);
insert into sys_role_menu values (1766100000000000005, 1766000000000001504);
insert into sys_role_menu values (1766100000000000005, 1766000000000001506);
-- 候选人附件四权限：与候选人的 list/query/add/edit 授权角色保持一致
insert into sys_role_menu values (1766100000000000005, 1766000000000001509);
insert into sys_role_menu values (1766100000000000005, 1766000000000001510);
insert into sys_role_menu values (1766100000000000005, 1766000000000001511);
insert into sys_role_menu values (1766100000000000005, 1766000000000001512);
insert into sys_role_menu values (1766100000000000005, 1766000000000000107);
insert into sys_role_menu values (1766100000000000005, 1766000000000001601);
insert into sys_role_menu values (1766100000000000005, 1766000000000001602);
insert into sys_role_menu values (1766100000000000005, 1766000000000001603);
insert into sys_role_menu values (1766100000000000005, 1766000000000001604);
insert into sys_role_menu values (1766100000000000005, 1766000000000000201);
insert into sys_role_menu values (1766100000000000005, 1766000000000000204);
insert into sys_role_menu values (1766100000000000005, 1766000000000002001);
insert into sys_role_menu values (1766100000000000005, 1766000000000002002);
insert into sys_role_menu values (1766100000000000005, 1766000000000002004);

-- 6) 用人部门负责人 hr_dept_owner：提交需求、查看进度、填写面试意见；不可浏览无关候选人。
insert into sys_role_menu values (1766100000000000006, 1766000000000000001);
insert into sys_role_menu values (1766100000000000006, 1766000000000000101);
insert into sys_role_menu values (1766100000000000006, 1766000000000001001);
insert into sys_role_menu values (1766100000000000006, 1766000000000000102);
insert into sys_role_menu values (1766100000000000006, 1766000000000001101);
insert into sys_role_menu values (1766100000000000006, 1766000000000001102);
insert into sys_role_menu values (1766100000000000006, 1766000000000001103);
insert into sys_role_menu values (1766100000000000006, 1766000000000001105);
insert into sys_role_menu values (1766100000000000006, 1766000000000000103);
insert into sys_role_menu values (1766100000000000006, 1766000000000001201);
insert into sys_role_menu values (1766100000000000006, 1766000000000001202);
insert into sys_role_menu values (1766100000000000006, 1766000000000000105);
insert into sys_role_menu values (1766100000000000006, 1766000000000001401);
insert into sys_role_menu values (1766100000000000006, 1766000000000001402);
insert into sys_role_menu values (1766100000000000006, 1766000000000000106);
insert into sys_role_menu values (1766100000000000006, 1766000000000001501);
insert into sys_role_menu values (1766100000000000006, 1766000000000001502);
insert into sys_role_menu values (1766100000000000006, 1766000000000000107);
insert into sys_role_menu values (1766100000000000006, 1766000000000001601);
insert into sys_role_menu values (1766100000000000006, 1766000000000001603);

-- 7) 面试官 hr_interviewer：查看必要简历并提交面试反馈；不可批量导出。
insert into sys_role_menu values (1766100000000000007, 1766000000000000001);
insert into sys_role_menu values (1766100000000000007, 1766000000000000106);
insert into sys_role_menu values (1766100000000000007, 1766000000000001502);
insert into sys_role_menu values (1766100000000000007, 1766000000000000107);
insert into sys_role_menu values (1766100000000000007, 1766000000000001601);
insert into sys_role_menu values (1766100000000000007, 1766000000000001603);

-- 8) 审计查看者 hr_auditor：只读审计；默认不可下载候选人附件。
insert into sys_role_menu values (1766100000000000008, 1766000000000000001);
insert into sys_role_menu values (1766100000000000008, 1766000000000000113);
insert into sys_role_menu values (1766100000000000008, 1766000000000002701);
insert into sys_role_menu values (1766100000000000008, 1766000000000002702);

-- 9) 人才库查阅者 hr_talent_viewer：检索、查看脱敏人才摘要及授权附件；不可修改、导出或查看背调资料。
insert into sys_role_menu values (1766100000000000009, 1766000000000000001);
insert into sys_role_menu values (1766100000000000009, 1766000000000000201);
insert into sys_role_menu values (1766100000000000009, 1766000000000000202);
insert into sys_role_menu values (1766100000000000009, 1766000000000001801);
insert into sys_role_menu values (1766100000000000009, 1766000000000001802);
insert into sys_role_menu values (1766100000000000009, 1766000000000000203);
insert into sys_role_menu values (1766100000000000009, 1766000000000001901);

-- ----------------------------
-- 六、自检（执行后人工核对，非必需）
-- ----------------------------
-- 字典：应返回 36 组 / 各类型编码值齐全
-- select dict_type, count(*) as value_cnt from sys_dict_data
--  where dict_type in (select dict_type from sys_dict_type where dict_id between 1766200000000000001 and 1766200000000000036)
--  group by dict_type order by dict_type;
-- 菜单：应返回 1 个一级目录（parent_id=0, menu_type='M'）
-- select menu_id, menu_name, parent_id, menu_type, path, component, perms from sys_menu
--  where menu_id between 1766000000000000001 and 1766000000000000113 order by menu_id;
-- 角色绑定：每个角色的菜单数
-- select role_id, count(*) from sys_role_menu
--  where role_id between 1766100000000000001 and 1766100000000000009 group by role_id;
