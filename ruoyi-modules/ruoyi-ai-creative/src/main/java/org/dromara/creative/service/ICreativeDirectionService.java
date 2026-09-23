package org.dromara.creative.service;

import org.dromara.creative.domain.vo.DpVisualDirectionVo;

import java.util.List;

/**
 * 视觉方向服务（A/B/C）。
 *
 * <p>方向的职责是「在同一个锁定基因下给出几种不同取舍」，不是推翻基因：
 * 配色基准、留白、产品占比沿用基因，只在<b>场景、光线气氛、构图、情绪</b>上分叉。
 * 这样选方向时比较的是「同一套规范下的不同拍法」，而不是三张风格打架的图。</p>
 *
 * @author creative
 */
public interface ICreativeDirectionService {

    /**
     * 生成 A/B/C 三个方向（须已有锁定基因）。
     *
     * <p>重复生成会先废弃此前「未选定」的方向，避免页面堆出十几条历史方向；
     * 已被选定的方向不动（它是当前生效方向）。</p>
     *
     * @param taskId 项目ID
     * @return 本次生成的方向列表
     */
    List<DpVisualDirectionVo> generate(Long taskId);

    /**
     * 方向列表（按代号排序）。
     *
     * @param taskId 项目ID
     * @return 方向列表
     */
    List<DpVisualDirectionVo> list(Long taskId);

    /**
     * 选定方向（其余自动置为已弃）。
     *
     * @param taskId      项目ID
     * @param directionId 方向ID
     * @return 选定后的方向
     */
    DpVisualDirectionVo select(Long taskId, Long directionId);

    /**
     * 当前已选定的方向。
     *
     * @param taskId 项目ID
     * @return 已选定方向；未选定时返回 null
     */
    DpVisualDirectionVo selected(Long taskId);

    /**
     * 编辑方向文案（名称/概念/备注）。
     *
     * @param taskId 项目ID
     * @param bo     编辑内容
     * @return 编辑后的方向
     */
    DpVisualDirectionVo update(Long taskId, org.dromara.creative.domain.bo.CreativeDirectionBo bo);

}
