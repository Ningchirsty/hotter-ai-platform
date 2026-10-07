package org.dromara.creative.mirror;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.domain.bo.AigTaskMirrorQueryBo;
import org.dromara.aigov.task.domain.vo.AigTaskMirrorVo;
import org.dromara.aigov.task.service.IAigTaskMirrorProvider;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.creative.domain.vo.DpGenerationVo;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.dromara.creative.service.ICreativeGenerationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 创作域生成记录（{@code dp_generation}）的只读镜像。
 *
 * <p><b>这是设计 §9 / 本次拍板口径的落点</b>：新任务只走 {@code aig_task}，
 * 存量 {@code dp_generation} 以<b>只读镜像</b>的形式出现在统一任务视图里——
 * 看得见、但改不了。对它的重试/选定/质检仍走创作域自己的接口与权限
 * （{@code creative:production:select} 等），治理层不替业务域决定权限边界。</p>
 *
 * <p><b>三条刻意的口径</b>：</p>
 * <ol>
 *     <li><b>走纯只读读法，绝不调 {@code queryPage}</b>：创作域的生产中心列表会「顺手刷新」
 *         内核状态——对 {@code QUEUED} 的候选还会真的 {@code dispatchQueued}。
 *         若镜像复用它，治理台里<b>浏览一次列表就可能把出图跑起来并计费</b>。
 *         故这里用 {@code queryReadOnlyPage}/{@code getReadOnly}，它们只读库、不写库、不推进状态。</li>
 *     <li><b>状态原样透传，不映射成 {@code aig_task} 的状态</b>：创作域有自己的 8 态，
 *         其中 {@code APPROVED} 的含义是「这一屏采用这张图」，与任务级通过不是一回事。
 *         映射会让人以为两套状态机按同一规则推进，故只透传原值 + 写明状态机来源，
 *         仅 {@code terminal} 这一个语义被跨来源统一。</li>
 *     <li><b>滞后要写在明面上</b>：因为不做内核刷新，这里显示的是
 *         {@code dp_generation} 的<b>已落库状态</b>，可能与内核实时状态不一致。
 *         与其为了数字好看而偷偷去刷（那就不是只读镜像了），不如把代价写出来。</li>
 * </ol>
 *
 * @author creative
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreativeTaskMirrorProvider implements IAigTaskMirrorProvider {

    /**
     * 来源编码。
     */
    public static final String SOURCE = "DP_GENERATION";

    /**
     * 状态机标识（写进返回体，避免被误当成 {@code aig_task} 的状态）。
     */
    private static final String STATE_MACHINE = "dp_generation（创作域候选，8 态；APPROVED=该屏采用这张）";

    private final ICreativeGenerationService generationService;

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String label() {
        return "创作域生成记录";
    }

    @Override
    public String description() {
        return "创作域存量的出图候选（dp_generation）。只读：重试/选定/质检仍在创作域接口上。"
            + "状态为已落库值，不做内核实时刷新，可能略有滞后。";
    }

    @Override
    public PageResult<AigTaskMirrorVo> queryPage(AigTaskMirrorQueryBo bo, PageQuery pageQuery) {
        PageResult<DpGenerationVo> page = generationService.queryReadOnlyPage(
            bo.getProjectId(), bo.getStatus(), pageQuery);
        List<AigTaskMirrorVo> rows = new ArrayList<>();
        if (page.getRows() != null) {
            for (DpGenerationVo row : page.getRows()) {
                rows.add(toMirror(row));
            }
        }
        return PageResult.build(rows, page.getTotal());
    }

    @Override
    public AigTaskMirrorVo getDetail(String refId) {
        Long generationId = parseRefId(refId);
        DpGenerationVo row = generationService.getReadOnly(generationId);
        return row == null ? null : toMirror(row);
    }

    /**
     * 解析来源侧主键。
     *
     * <p>非数字主键直接报错而不是返回 null：那说明调用方拿错了 ID（不同来源主键类型不同），
     * 返回 null 会让人以为「这条记录不存在」，把参数错误伪装成数据缺失。</p>
     *
     * @param refId 来源侧主键
     * @return 候选ID
     */
    private Long parseRefId(String refId) {
        try {
            return Long.valueOf(refId.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("创作域生成记录的ID必须是数字：" + refId);
        }
    }

    /**
     * 映射成镜像行。
     *
     * @param row 创作域候选视图（只读读法返回）
     * @return 镜像行
     */
    private AigTaskMirrorVo toMirror(DpGenerationVo row) {
        AigTaskMirrorVo mirror = new AigTaskMirrorVo();
        mirror.setSource(SOURCE);
        mirror.setSourceLabel(label());
        mirror.setRefId(row.getId() == null ? null : String.valueOf(row.getId()));
        mirror.setProjectId(row.getTaskId());
        mirror.setProjectName(row.getTaskName());
        mirror.setTitle(row.getCandidateNo() == null ? "候选" : "候选 #" + row.getCandidateNo());
        mirror.setStatus(row.getStatus());
        DpGenerationStatusEnum status = DpGenerationStatusEnum.find(row.getStatus());
        mirror.setStatusLabel(status == null
            ? StringUtils.blankToDefault(row.getStatusDesc(), row.getStatus())
            : status.getDesc());
        mirror.setTerminal(status != null && status.isTerminal());
        mirror.setStateMachine(STATE_MACHINE);
        mirror.setCandidateNo(row.getCandidateNo());
        mirror.setSelected(DpGenerationStatusEnum.APPROVED.getCode().equals(row.getStatus()));
        mirror.setOutputAssetId(row.getOutputAssetId());
        mirror.setErrorCode(row.getErrorCode());
        mirror.setErrorMessage(row.getErrorMessage());
        mirror.setDurationMs(row.getDurationMs());
        mirror.setCreateTime(row.getCreateTime());
        return mirror;
    }

}
