package org.dromara.creative.service;

import org.dromara.creative.domain.bo.CopyBlockBo;
import org.dromara.creative.domain.bo.CopyBlockReorderBo;
import org.dromara.creative.domain.vo.DpCopyBlockVo;

import java.util.List;

/**
 * 文案与要点块服务（详情页的「文字」）。
 *
 * <p>块是「整页要说的话」，与分镜屏（这一屏这张图配什么字）不是一对一关系：
 * 一条卖点块可能对应一屏，也可能在详情页正文里被引用。因此本服务不碰分镜，
 * 只维护块本身；接线（卖点进分镜、正文进长图）在各自的调用点完成。</p>
 *
 * @author creative
 */
public interface ICreativeCopyService {

    /**
     * 块列表（按 blockType、sortNo 升序）。
     *
     * @param taskId    项目ID
     * @param blockType 块类型（可空＝全部）
     * @return 块列表
     */
    List<DpCopyBlockVo> list(Long taskId, String blockType);

    /**
     * 新增一块。
     *
     * @param taskId 项目ID
     * @param bo     表单（sortNo 可空＝追加到同类型末尾）
     * @return 新块ID
     */
    Long add(Long taskId, CopyBlockBo bo);

    /**
     * 编辑一块（只改传了值的字段）。
     *
     * @param taskId  项目ID
     * @param blockId 块ID
     * @param bo      表单
     */
    void update(Long taskId, Long blockId, CopyBlockBo bo);

    /**
     * 按给定顺序重排同一项目、同一类型下的块。
     *
     * @param taskId 项目ID
     * @param bo     重排表单
     */
    void reorder(Long taskId, CopyBlockReorderBo bo);

    /**
     * 删除一块（逻辑删除，保留历史）。
     *
     * @param taskId  项目ID
     * @param blockId 块ID
     */
    void remove(Long taskId, Long blockId);

    /**
     * 从已确认事实派生参数行（幂等：同项目同类型同来源已存在则跳过）。
     *
     * @param taskId 项目ID
     * @return 本次新增的条数
     */
    int seedFromFacts(Long taskId);

}
