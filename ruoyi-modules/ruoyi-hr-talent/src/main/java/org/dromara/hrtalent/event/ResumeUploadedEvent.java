package org.dromara.hrtalent.event;

import java.io.Serializable;

/**
 * 简历上传事件（设计文档 §21.6）。
 *
 * <p>由简历服务在上传新版本成功后发布；消费者为文件安全扫描、解析任务服务与资料完整度计算。
 * 一期这些消费者尚未实现（安全扫描与解析引擎按审批结果后接），
 * 先按 §21.6 定义并发布，为后续接入预留。</p>
 *
 * <p><b>事件约束</b>：事件只承载标识与版本号，<b>不携带</b>简历正文、联系方式或对象存储地址；
 * 且不得把必须原子完成的写操作（版本写入、当前版本切换）交给本事件的异步监听器去完成
 * （设计文档 §21.6 末段）。</p>
 *
 * @param resumeId   简历版本ID
 * @param talentId   人才主档ID
 * @param versionNo  简历版本号
 * @param operatorId 操作人用户ID，可为空
 * @author hr-talent
 */
public record ResumeUploadedEvent(
    Long resumeId,
    Long talentId,
    Integer versionNo,
    Long operatorId
) implements Serializable {
}
