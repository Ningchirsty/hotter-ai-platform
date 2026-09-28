package org.dromara.content.service;

import org.dromara.content.domain.bo.BrandBriefBo;
import org.dromara.content.domain.vo.CpBrandBriefVo;

/**
 * 品牌 Brief 服务（内容协同侧，品牌部录入与确认）。
 *
 * <p>创作域（视觉工厂）通过本接口**只读**获取品牌要求，用于派生提示词与视觉门判定；
 * 录入与确认只发生在内容任务的页面里。</p>
 *
 * @author content
 */
public interface IContentBrandBriefService {

    /**
     * 读取某任务的品牌 Brief。
     *
     * <p>没有记录时返回 {@code configured=false} 的空视图（不是 null），
     * 让页面能把「还没填」与「接口失败」分开显示。</p>
     *
     * @param taskId 任务ID
     * @return Brief 视图
     */
    CpBrandBriefVo get(Long taskId);

    /**
     * 保存（upsert）：不碰状态与确认人。
     *
     * @param taskId 任务ID
     * @param bo     表单
     * @return 保存后的视图（状态如实带回）
     */
    CpBrandBriefVo save(Long taskId, BrandBriefBo bo);

    /**
     * 品牌方确认：把状态推进为 CONFIRMED 并记录确认人与时间。
     *
     * @param taskId 任务ID
     * @return 确认后的视图
     */
    CpBrandBriefVo confirm(Long taskId);
}
