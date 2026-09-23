package org.dromara.creative.service;

import org.dromara.creative.domain.vo.DpGenerationVo;

import java.util.List;

/**
 * 出图生产服务（R2）：逐屏批量出图、候选选定、QA 筛除。
 *
 * <p><b>「只筛除、不放行」怎么落</b>：QA 结论为「不一致」时候选被置为已筛除，
 * 结论为「无法判定」时标为待人工，结论为「一致」也<b>不</b>自动选定——
 * 选定永远是人的动作。这样自动环节只做减法，不做加法。</p>
 *
 * @author creative
 */
public interface ICreativeProductionService {

    /**
     * 单屏生产状态。
     *
     * @param screenId               屏ID
     * @param screenNo               屏号
     * @param screenTypeDesc         屏类型
     * @param status                 屏状态（DRAFT/GENERATING/GENERATED/APPROVED/REJECTED）
     * @param candidateCount         候选数
     * @param selectedGenerationId   已选定的候选ID（可空）
     * @param latestGenerationId     最新候选ID（页面据此调选定/质检）
     * @param latestStatus           最新候选状态
     * @param qaVerdict              最新候选的 QA 结论
     * @param note                   说明（如「已自动重试 2 次」）
     */
    record ScreenProduction(Long screenId, String screenNo, String screenTypeDesc, String status,
                            int candidateCount, Long selectedGenerationId, Long latestGenerationId,
                            String latestStatus, String qaVerdict, String note) {
    }

    /**
     * 一轮批量生产的结果。
     *
     * @param submitted 本次新提交的候选数
     * @param skipped   因已有候选而跳过的屏数
     * @param screens   逐屏状态
     */
    record ProductionRun(int submitted, int skipped, List<ScreenProduction> screens) {
    }

    /**
     * 按已锁定分镜逐屏批量出图。
     *
     * @param taskId 项目ID
     * @param force  为 true 时即使该屏已有候选也再出一张
     * @return 生产结果
     */
    ProductionRun start(Long taskId, boolean force);

    /**
     * 单屏重出（该屏已有候选时也照做，属于「重生成」）。
     *
     * @param taskId   项目ID
     * @param screenId 屏ID
     * @return 新候选
     */
    DpGenerationVo regenerateScreen(Long taskId, Long screenId);

    /**
     * 刷新生产状态：候选状态、QA 结论、屏状态，并对失败候选按策略自动重试。
     *
     * @param taskId 项目ID
     * @return 逐屏状态
     */
    ProductionRun refresh(Long taskId);

    /**
     * 选定候选（人动作）：置为已选定，并触发产出登记与 QA。
     *
     * @param taskId       项目ID
     * @param generationId 候选ID
     * @return 更新后的候选
     */
    DpGenerationVo select(Long taskId, Long generationId);

    /**
     * 只跑 QA（不选定）：把候选登记为任务附件并发起成品一致性检查。
     *
     * @param taskId       项目ID
     * @param generationId 候选ID
     * @return 更新后的候选
     */
    DpGenerationVo runQa(Long taskId, Long generationId);

}
