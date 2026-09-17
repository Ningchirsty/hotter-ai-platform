package org.dromara.talent.service;

import org.dromara.talent.domain.bo.TlParseFieldConfirmBo;
import org.dromara.talent.domain.vo.TlParseFieldVo;
import org.dromara.talent.domain.vo.TlParseTaskVo;

import java.util.List;

/**
 * 简历解析任务服务（只存元数据，不存简历正文）。
 *
 * @author talent
 */
public interface ITalentParseService {

    /**
     * 解析任务详情（含字段列表）。
     *
     * @param taskId 任务ID
     * @return 任务详情
     */
    TlParseTaskVo getTask(Long taskId);

    /**
     * 解析字段列表。
     *
     * @param taskId 任务ID
     * @return 字段列表
     */
    List<TlParseFieldVo> listFields(Long taskId);

    /**
     * 人工确认解析字段（只写确认值，不自动覆盖人才档案）。
     *
     * @param taskId 任务ID
     * @param fields 字段确认列表
     */
    void confirmFields(Long taskId, List<TlParseFieldConfirmBo> fields);

    /**
     * 重试解析任务。
     *
     * @param taskId 任务ID
     */
    void retry(Long taskId);

    /**
     * 创建解析任务：依据 {@code TalentProperties.parseEnabled} 决定落 DISABLED 还是 PENDING。
     *
     * @param attachmentId 附件ID
     * @return 任务ID
     */
    Long createTask(Long attachmentId);

}
