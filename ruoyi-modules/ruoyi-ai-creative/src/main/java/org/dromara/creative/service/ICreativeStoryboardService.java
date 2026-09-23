package org.dromara.creative.service;

import org.dromara.creative.domain.bo.CreativeScreenBo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;

import java.util.List;

/**
 * 分镜服务。
 *
 * <p>分镜把「一版视觉方案」拆成逐屏可执行规格：每屏讲什么、画面单独要说什么、用什么规格出图。
 * 它是「视觉门」审核的对象，也是后续批量出图的输入。</p>
 *
 * @author creative
 */
public interface ICreativeStoryboardService {

    /**
     * 生成一版分镜（须已有锁定基因；已选定方向时按方向定制，否则用基因默认）。
     *
     * @param taskId 项目ID
     * @return 新分镜（含屏列表）
     */
    DpStoryboardVo generate(Long taskId);

    /**
     * 最新一版分镜（含屏列表）。
     *
     * @param taskId 项目ID
     * @return 最新分镜；从未生成过返回 null
     */
    DpStoryboardVo latest(Long taskId);

    /**
     * 版本列表（倒序，不含屏）。
     *
     * @param taskId 项目ID
     * @return 版本列表
     */
    List<DpStoryboardVo> versions(Long taskId);

    /**
     * 编辑一屏。
     *
     * <p>未锁定可直接改；分镜已锁定时拒绝并提示先生成新版本——锁定版不可改的纪律与视觉基因一致。</p>
     *
     * @param taskId 项目ID
     * @param bo     编辑内容
     * @return 编辑后的屏
     */
    DpStoryboardScreenVo updateScreen(Long taskId, CreativeScreenBo bo);

    /**
     * 锁定分镜（锁定前要求每屏都有画面独白——没有独白的屏等于没想清楚）。
     *
     * @param taskId       项目ID
     * @param storyboardId 分镜ID（可空＝最新一版）
     * @return 锁定后的分镜
     */
    DpStoryboardVo lock(Long taskId, Long storyboardId);

}
