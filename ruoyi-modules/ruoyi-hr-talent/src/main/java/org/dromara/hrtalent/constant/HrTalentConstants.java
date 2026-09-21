package org.dromara.hrtalent.constant;

import java.util.Set;

/**
 * 招聘与人才管理模块公共常量。
 * <p>包含权限标识（SPEC §4）、角色标识（设计文档 §6）、表前缀、对象存储键前缀
 * 与解析器版本等跨模块共享的稳定值。禁止在此处硬编码任何密钥或地址。</p>
 *
 * @author hr-talent
 */
public interface HrTalentConstants {

    /**
     * 招聘过程表前缀。
     */
    String TABLE_PREFIX_RECRUIT = "hr_recruit_";

    /**
     * 人才主数据表前缀。
     */
    String TABLE_PREFIX_TALENT = "hr_talent_";
    /**
     * 逻辑删除标志：未删除。
     */
    String DEL_FLAG_NORMAL = "0";

    /**
     * 逻辑删除标志：已删除。
     */
    String DEL_FLAG_DELETED = "1";

    /**
     * 对象存储私有根前缀：附件与导出文件均写在该前缀下，不登记 {@code sys_oss}。
     */
    String OBJECT_KEY_ROOT = "hr-talent-private";

    /**
     * 对象键分段：导出文件。
     */
    String KEY_SEG_EXPORTS = "exports";

    /**
     * 对象键分段：简历。
     */
    String KEY_SEG_RESUME = "resume";

    /**
     * 对象键分段：通用附件。
     */
    String KEY_SEG_ATTACHMENT = "attachment";

    /**
     * 简历解析器版本默认值（可由 {@code hrtalent.resume-parser-version} 覆盖）。
     */
    String DEFAULT_RESUME_PARSER_VERSION = "hr-resume-parser/1.0.0";

    /**
     * 人才导出文件默认名称。
     */
    String EXPORT_FILE_NAME = "talent-ledger.xlsx";

    /* ------------------------------------------------------------------ 角色标识（设计文档 §6） ------------------------------------------------------------------ */

    /**
     * 角色：平台管理员。
     */
    String ROLE_PLATFORM_ADMIN = "hr_platform_admin";

    /**
     * 角色：集团招聘管理员。
     */
    String ROLE_RECRUIT_ADMIN_GROUP = "hr_recruit_admin_group";

    /**
     * 角色：集团人才管理员。
     */
    String ROLE_TALENT_ADMIN_GROUP = "hr_talent_admin_group";

    /**
     * 角色：公司招聘负责人。
     */
    String ROLE_COMPANY_RECRUIT_OWNER = "hr_company_recruit_owner";

    /**
     * 角色：招聘专员。
     */
    String ROLE_RECRUITER = "hr_recruiter";

    /**
     * 角色：用人部门负责人。
     */
    String ROLE_DEPT_OWNER = "hr_dept_owner";

    /**
     * 角色：面试官。
     */
    String ROLE_INTERVIEWER = "hr_interviewer";

    /**
     * 角色：审计查看者。
     */
    String ROLE_AUDITOR = "hr_auditor";

    /**
     * 角色：人才库查阅者。
     */
    String ROLE_TALENT_VIEWER = "hr_talent_viewer";

    /**
     * 集团级管理员角色集合（可跨公司查看，但高度敏感附件仍需独立授权）。
     */
    Set<String> GROUP_LEVEL_ROLE_KEYS = Set.of(ROLE_RECRUIT_ADMIN_GROUP, ROLE_TALENT_ADMIN_GROUP);

    /* ------------------------------------------------------------------ 权限标识（SPEC §4） ------------------------------------------------------------------ */

    /**
     * 权限：管理驾驶舱查看。
     */
    String PERM_DASHBOARD_VIEW = "recruit:dashboard:view";

    /**
     * 权限：管理驾驶舱导出。
     */
    String PERM_DASHBOARD_EXPORT = "recruit:dashboard:export";

    /**
     * 权限：招聘需求列表。
     */
    String PERM_DEMAND_LIST = "recruit:demand:list";

    /**
     * 权限：招聘需求详情。
     */
    String PERM_DEMAND_QUERY = "recruit:demand:query";

    /**
     * 权限：招聘需求新增。
     */
    String PERM_DEMAND_ADD = "recruit:demand:add";

    /**
     * 权限：招聘需求编辑。
     */
    String PERM_DEMAND_EDIT = "recruit:demand:edit";

    /**
     * 权限：招聘需求提交。
     */
    String PERM_DEMAND_SUBMIT = "recruit:demand:submit";

    /**
     * 权限：招聘需求暂停。
     */
    String PERM_DEMAND_PAUSE = "recruit:demand:pause";

    /**
     * 权限：招聘需求关闭。
     */
    String PERM_DEMAND_CLOSE = "recruit:demand:close";

    /**
     * 权限：招聘需求导入。
     */
    String PERM_DEMAND_IMPORT = "recruit:demand:import";

    /**
     * 权限：招聘需求导出。
     */
    String PERM_DEMAND_EXPORT = "recruit:demand:export";

    /**
     * 权限：公司月度计划列表。
     */
    String PERM_PLAN_LIST = "recruit:plan:list";

    /**
     * 权限：公司月度计划详情。
     */
    String PERM_PLAN_QUERY = "recruit:plan:query";

    /**
     * 权限：公司月度计划新增。
     */
    String PERM_PLAN_ADD = "recruit:plan:add";

    /**
     * 权限：公司月度计划编辑。
     */
    String PERM_PLAN_EDIT = "recruit:plan:edit";

    /**
     * 权限：公司月度计划确认。
     */
    String PERM_PLAN_CONFIRM = "recruit:plan:confirm";

    /**
     * 权限：公司月度计划关闭。
     */
    String PERM_PLAN_CLOSE = "recruit:plan:close";

    /**
     * 权限：公司月度计划导出。
     */
    String PERM_PLAN_EXPORT = "recruit:plan:export";

    /**
     * 权限：月度结转预览。
     */
    String PERM_ROLLOVER_PREVIEW = "recruit:rollover:preview";

    /**
     * 权限：月度结转执行。
     */
    String PERM_ROLLOVER_EXECUTE = "recruit:rollover:execute";

    /**
     * 权限：月度结转重试。
     */
    String PERM_ROLLOVER_RETRY = "recruit:rollover:retry";

    /**
     * 权限：月度结转明细。
     */
    String PERM_ROLLOVER_DETAIL = "recruit:rollover:detail";

    /**
     * 权限：岗位需求列表。
     */
    String PERM_JOB_LIST = "recruit:job:list";

    /**
     * 权限：岗位需求详情。
     */
    String PERM_JOB_QUERY = "recruit:job:query";

    /**
     * 权限：岗位需求新增。
     */
    String PERM_JOB_ADD = "recruit:job:add";

    /**
     * 权限：岗位需求编辑。
     */
    String PERM_JOB_EDIT = "recruit:job:edit";

    /**
     * 权限：岗位需求分配。
     */
    String PERM_JOB_ASSIGN = "recruit:job:assign";

    /**
     * 权限：岗位需求关闭。
     */
    String PERM_JOB_CLOSE = "recruit:job:close";

    /**
     * 权限：候选人列表。
     */
    String PERM_CANDIDATE_LIST = "recruit:candidate:list";

    /**
     * 权限：候选人详情。
     */
    String PERM_CANDIDATE_QUERY = "recruit:candidate:query";

    /**
     * 权限：候选人新增。
     */
    String PERM_CANDIDATE_ADD = "recruit:candidate:add";

    /**
     * 权限：候选人编辑。
     */
    String PERM_CANDIDATE_EDIT = "recruit:candidate:edit";

    /**
     * 权限：候选人流转。
     */
    String PERM_CANDIDATE_TRANSFER = "recruit:candidate:transfer";

    /**
     * 权限：候选人阶段变更。
     */
    String PERM_CANDIDATE_STAGE = "recruit:candidate:stage";

    /**
     * 权限：候选人电话明文查看（资源级二次鉴权）。
     */
    String PERM_CANDIDATE_PHONE_VIEW = "recruit:candidate:phone-view";

    /**
     * 权限：候选人导出。
     */
    String PERM_CANDIDATE_EXPORT = "recruit:candidate:export";

    /**
     * 权限：面试列表。
     */
    String PERM_INTERVIEW_LIST = "recruit:interview:list";

    /**
     * 权限：面试安排。
     */
    String PERM_INTERVIEW_SCHEDULE = "recruit:interview:schedule";

    /**
     * 权限：面试反馈。
     */
    String PERM_INTERVIEW_FEEDBACK = "recruit:interview:feedback";

    /**
     * 权限：面试取消。
     */
    String PERM_INTERVIEW_CANCEL = "recruit:interview:cancel";

    /**
     * 权限：背调列表。
     */
    String PERM_BACKGROUND_LIST = "recruit:background:list";

    /**
     * 权限：背调新增。
     */
    String PERM_BACKGROUND_ADD = "recruit:background:add";

    /**
     * 权限：背调编辑。
     */
    String PERM_BACKGROUND_EDIT = "recruit:background:edit";

    /**
     * 权限：背调明细查看（资源级二次鉴权）。
     */
    String PERM_BACKGROUND_VIEW_SENSITIVE = "recruit:background:view-sensitive";

    /**
     * 权限：人才档案列表。
     */
    String PERM_PROFILE_LIST = "talent:profile:list";

    /**
     * 权限：人才档案详情。
     */
    String PERM_PROFILE_QUERY = "talent:profile:query";

    /**
     * 权限：人才档案新增。
     */
    String PERM_PROFILE_ADD = "talent:profile:add";

    /**
     * 权限：人才档案编辑。
     */
    String PERM_PROFILE_EDIT = "talent:profile:edit";

    /**
     * 权限：人才档案归档。
     */
    String PERM_PROFILE_ARCHIVE = "talent:profile:archive";

    /**
     * 权限：人才电话明文查看（资源级二次鉴权）。
     */
    String PERM_PROFILE_PHONE_VIEW = "talent:profile:phone-view";

    /**
     * 权限：人才档案导出。
     */
    String PERM_PROFILE_EXPORT = "talent:profile:export";

    /**
     * 权限：人才池列表。
     */
    String PERM_POOL_LIST = "talent:pool:list";

    /**
     * 权限：人才池新增。
     */
    String PERM_POOL_ADD = "talent:pool:add";

    /**
     * 权限：人才池编辑。
     */
    String PERM_POOL_EDIT = "talent:pool:edit";

    /**
     * 权限：人才池成员管理。
     */
    String PERM_POOL_MEMBER = "talent:pool:member";

    /**
     * 权限：人才池共享。
     */
    String PERM_POOL_SHARE = "talent:pool:share";

    /**
     * 权限：简历列表。
     */
    String PERM_RESUME_LIST = "talent:resume:list";

    /**
     * 权限：简历上传。
     */
    String PERM_RESUME_UPLOAD = "talent:resume:upload";

    /**
     * 权限：简历下载（资源级二次鉴权）。
     */
    String PERM_RESUME_DOWNLOAD = "talent:resume:download";

    /**
     * 权限：简历解析。
     */
    String PERM_RESUME_PARSE = "talent:resume:parse";

    /**
     * 权限：简历解析结果复核。
     */
    String PERM_RESUME_REVIEW = "talent:resume:review";

    /**
     * 权限：简历版本管理。
     */
    String PERM_RESUME_VERSION = "talent:resume:version";

    /**
     * 权限：重复人才列表。
     */
    String PERM_DUPLICATE_LIST = "talent:duplicate:list";

    /**
     * 权限：重复人才确认。
     */
    String PERM_DUPLICATE_CONFIRM = "talent:duplicate:confirm";

    /**
     * 权限：重复人才合并。
     */
    String PERM_DUPLICATE_MERGE = "talent:duplicate:merge";

    /**
     * 权限：重复人才忽略。
     */
    String PERM_DUPLICATE_IGNORE = "talent:duplicate:ignore";

    /**
     * 权限：人才共享授权列表。
     */
    String PERM_GRANT_LIST = "talent:grant:list";

    /**
     * 权限：人才共享授权新增。
     */
    String PERM_GRANT_ADD = "talent:grant:add";

    /**
     * 权限：人才共享授权撤销。
     */
    String PERM_GRANT_REVOKE = "talent:grant:revoke";

    /**
     * 权限：招聘渠道列表。
     */
    String PERM_CHANNEL_LIST = "recruit:channel:list";

    /**
     * 权限：招聘渠道详情。
     */
    String PERM_CHANNEL_QUERY = "recruit:channel:query";

    /**
     * 权限：招聘渠道新增。
     */
    String PERM_CHANNEL_ADD = "recruit:channel:add";

    /**
     * 权限：招聘渠道编辑。
     */
    String PERM_CHANNEL_EDIT = "recruit:channel:edit";

    /**
     * 权限：同行信息列表。
     */
    String PERM_PEER_LIST = "recruit:peer:list";

    /**
     * 权限：同行信息详情。
     */
    String PERM_PEER_QUERY = "recruit:peer:query";

    /**
     * 权限：同行信息新增。
     */
    String PERM_PEER_ADD = "recruit:peer:add";

    /**
     * 权限：同行信息编辑。
     */
    String PERM_PEER_EDIT = "recruit:peer:edit";

    /**
     * 权限：招聘标准列表。
     */
    String PERM_STANDARD_LIST = "recruit:standard:list";

    /**
     * 权限：招聘标准详情。
     */
    String PERM_STANDARD_QUERY = "recruit:standard:query";

    /**
     * 权限：招聘标准新增。
     */
    String PERM_STANDARD_ADD = "recruit:standard:add";

    /**
     * 权限：招聘标准编辑。
     */
    String PERM_STANDARD_EDIT = "recruit:standard:edit";

    /**
     * 权限：数据导入中心列表。
     */
    String PERM_IMPORT_LIST = "recruit:import:list";

    /**
     * 权限：导入模板下载。
     */
    String PERM_IMPORT_TEMPLATE = "recruit:import:template";

    /**
     * 权限：导入上传。
     */
    String PERM_IMPORT_UPLOAD = "recruit:import:upload";

    /**
     * 权限：导入确认。
     */
    String PERM_IMPORT_CONFIRM = "recruit:import:confirm";

    /**
     * 权限：导入取消。
     */
    String PERM_IMPORT_CANCEL = "recruit:import:cancel";

    /**
     * 权限：导入批次明细。
     */
    String PERM_IMPORT_DETAIL = "recruit:import:detail";

    /**
     * 权限：敏感操作审计列表。
     */
    String PERM_AUDIT_LIST = "recruit:audit:list";

    /**
     * 权限：敏感操作审计导出。
     */
    String PERM_AUDIT_EXPORT = "recruit:audit:export";

    /**
     * 权限：附件上传。
     */
    String PERM_ATTACHMENT_UPLOAD = "recruit:attachment:upload";

    /**
     * 权限：附件下载（资源级二次鉴权）。
     */
    String PERM_ATTACHMENT_DOWNLOAD = "recruit:attachment:download";

    /**
     * 权限：附件预览（资源级二次鉴权）。
     */
    String PERM_ATTACHMENT_PREVIEW = "recruit:attachment:preview";

    /**
     * 权限：附件删除；集团招聘管理员亦不得物理删除（设计文档 §6）。
     */
    String PERM_ATTACHMENT_DELETE = "recruit:attachment:delete";

}
