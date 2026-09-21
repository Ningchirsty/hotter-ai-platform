package org.dromara.hrtalent.service.talent;

import org.dromara.hrtalent.domain.bo.talent.ParseConfirmBo;
import org.dromara.hrtalent.domain.vo.talent.TalentParseTaskVo;

/**
 * 简历解析任务与人工复核服务接口（SPEC-P4 §2.1，设计文档 §8.21、§11.1）。
 *
 * <p><b>一期边界（务必遵守）</b>：</p>
 * <ul>
 *     <li>创建解析任务<b>只落库一个异步任务</b>，<b>不在 HTTP 请求内同步执行</b>文本提取、OCR 或大模型调用（§11.1）；</li>
 *     <li>解析引擎未获批准接入前，任务停在 {@code pending}；若显式触发而引擎不可用，
 *     任务置 {@code failed} 并写入稳定错误码 {@code HR_RESUME_002} 与明确中文提示；</li>
 *     <li><b>严禁</b>把简历或个人数据发送给未经批准的第三方服务（§8.21），本服务不引入任何外部解析依赖；</li>
 *     <li>解析结果逐字段保存「原始值 / 标准化值 / 置信度 / 来源位置 / 复核结论」，
 *     低置信度字段默认不勾选，<b>正式字段只有在人工确认后才更新</b>人才主档（§8.21）。</li>
 * </ul>
 *
 * @author hr-talent
 */
public interface ITalentParseService {

    /**
     * 为指定简历版本创建异步解析任务（不在请求内执行解析）。
     *
     * @param resumeId 简历版本ID
     * @return 新建的解析任务ID
     */
    Long createTask(Long resumeId);

    /**
     * 查询解析任务与候选结果（自动完成人才资源级鉴权）。
     *
     * @param taskId 解析任务ID
     * @return 解析任务视图（含候选结果）
     */
    TalentParseTaskVo getTask(Long taskId);

    /**
     * 人工确认选定字段并更新人才主档（仅写入勾选项，未勾选项记为已否决）。
     *
     * @param taskId 解析任务ID
     * @param bo     逐字段确认入参
     * @return 复核后的解析任务视图（含最新复核结论）
     */
    TalentParseTaskVo confirm(Long taskId, ParseConfirmBo bo);

}
