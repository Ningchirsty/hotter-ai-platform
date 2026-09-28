package org.dromara.creative.service;

import org.dromara.creative.domain.bo.BrandBriefBo;
import org.dromara.creative.domain.vo.DpBrandBriefVo;

/**
 * 品牌 Brief 服务（委托方的要求）。
 *
 * <p><b>三个方法的职责边界</b>：{@link #get} 只读（没有记录时也返回一个 configured=false 的视图，
 * 不建行）；{@link #save} 是 upsert 且<b>不动状态</b>；{@link #confirm} 是唯一能把状态推进到
 * {@code CONFIRMED} 的入口。这样「保存草稿」与「品牌方确认」在库里就是两种不同的状态，
 * 闸门项才有可判定的依据。</p>
 *
 * @author creative
 */
public interface ICreativeBriefService {

    /**
     * 读取项目的品牌 Brief。
     *
     * <p>没有记录时返回 {@code configured=false}（taskId 已填、status=DRAFT、其余 null），
     * <b>不返回 null</b>：前端要把「还没填」显示成引导入口，而不是当成接口异常。</p>
     *
     * @param taskId 项目ID
     * @return Brief 视图（永不为 null）
     */
    DpBrandBriefVo get(Long taskId);

    /**
     * 保存品牌 Brief（upsert，幂等）。
     *
     * <p><b>不碰 status/confirmedBy/confirmedAt</b>：保存草稿不应该把「品牌方已确认」打回草稿，
     * 也不应该顺手替人确认。若原记录已是 CONFIRMED，保存后仍是 CONFIRMED（返如实带回 status）——
     * 想撤销确认必须重新走确认流程，这个选择是为了让闸门结论不会因为一次保存而翻转。</p>
     *
     * @param taskId 项目ID
     * @param bo     表单
     * @return 保存后的视图
     */
    DpBrandBriefVo save(Long taskId, BrandBriefBo bo);

    /**
     * 品牌方确认（唯一能把状态置为 CONFIRMED 的入口）。
     *
     * @param taskId 项目ID
     * @return 确认后的视图
     */
    DpBrandBriefVo confirm(Long taskId);

}
