package org.dromara.hrtalent.support;

/**
 * 招聘与人才管理模块错误码常量（设计文档 §21.9）。
 * <p>服务端日志只记录错误码与必要上下文，不记录电话明文、简历正文、
 * 背调明细与对象存储长期地址；页面按错误码转换为中文提示。</p>
 *
 * @author hr-talent
 */
public interface HrTalentErrorCode {

    /**
     * 月度计划不存在或已关闭。
     */
    String HR_PLAN_001 = "HR_PLAN_001";

    /**
     * 计划任务已完成、取消或结转，不能继续修改。
     */
    String HR_PLAN_002 = "HR_PLAN_002";

    /**
     * 同一来源任务已生成目标月份结转任务。
     */
    String HR_PLAN_003 = "HR_PLAN_003";

    /**
     * 到岗结果无法确定唯一月度任务。
     */
    String HR_PLAN_004 = "HR_PLAN_004";

    /**
     * 应聘阶段跳转不合法。
     */
    String HR_APP_001 = "HR_APP_001";

    /**
     * 缺少进入目标阶段的必填资料。
     */
    String HR_APP_002 = "HR_APP_002";

    /**
     * 疑似存在相同人才，需要确认。
     */
    String HR_TALENT_001 = "HR_TALENT_001";

    /**
     * 无权查看该人才。
     */
    String HR_TALENT_002 = "HR_TALENT_002";

    /**
     * 人才已合并，请访问保留主档。
     */
    String HR_TALENT_003 = "HR_TALENT_003";

    /**
     * 人才合并版本冲突，请重新加载。
     */
    String HR_TALENT_004 = "HR_TALENT_004";

    /**
     * 简历文件类型或大小不符合规则。
     */
    String HR_RESUME_001 = "HR_RESUME_001";

    /**
     * 简历解析服务未启用或不可用。
     */
    String HR_RESUME_002 = "HR_RESUME_002";

    /**
     * 导入批次尚未通过校验。
     */
    String HR_IMPORT_001 = "HR_IMPORT_001";

    /**
     * 中文提示：月度计划不存在或已关闭。
     */
    String MSG_HR_PLAN_001 = "月度计划不存在或已关闭";

    /**
     * 中文提示：计划任务已完成、取消或结转，不能继续修改。
     */
    String MSG_HR_PLAN_002 = "计划任务已完成、取消或结转，不能继续修改";

    /**
     * 中文提示：同一来源任务已生成目标月份结转任务。
     */
    String MSG_HR_PLAN_003 = "同一来源任务已生成目标月份结转任务";

    /**
     * 中文提示：到岗结果无法确定唯一月度任务。
     */
    String MSG_HR_PLAN_004 = "到岗结果无法确定唯一月度任务";

    /**
     * 中文提示：应聘阶段跳转不合法。
     */
    String MSG_HR_APP_001 = "应聘阶段跳转不合法";

    /**
     * 中文提示：缺少进入目标阶段的必填资料。
     */
    String MSG_HR_APP_002 = "缺少进入目标阶段的必填资料";

    /**
     * 中文提示：疑似存在相同人才，需要确认。
     */
    String MSG_HR_TALENT_001 = "疑似存在相同人才，需要确认";

    /**
     * 中文提示：无权查看该人才。
     */
    String MSG_HR_TALENT_002 = "无权查看该人才";

    /**
     * 中文提示：人才已合并，请访问保留主档。
     */
    String MSG_HR_TALENT_003 = "人才已合并，请访问保留主档";

    /**
     * 中文提示：人才合并版本冲突，请重新加载。
     */
    String MSG_HR_TALENT_004 = "人才合并版本冲突，请重新加载";

    /**
     * 中文提示：简历文件类型或大小不符合规则。
     */
    String MSG_HR_RESUME_001 = "简历文件类型或大小不符合规则";

    /**
     * 中文提示：简历解析服务未启用或不可用。
     */
    String MSG_HR_RESUME_002 = "简历解析服务未启用或不可用";

    /**
     * 中文提示：导入批次尚未通过校验。
     */
    String MSG_HR_IMPORT_001 = "导入批次尚未通过校验";

}
