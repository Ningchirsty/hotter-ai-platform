package org.dromara.content.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentCardQueryBo;
import org.dromara.content.domain.bo.ContentCardResolveBo;
import org.dromara.content.domain.vo.CpInteractionCardVo;

/**
 * 互动确认卡服务。
 *
 * <p>对应设计文档 §7.2：卡片是**具体问题**，用户不必翻整份资料即可处理。
 * 本服务负责「待我确认」的聚合查询与处理动作。</p>
 *
 * @author content
 */
public interface IContentCardService {

    /**
     * 分页查询互动卡。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<CpInteractionCardVo> queryPage(ContentCardQueryBo bo, PageQuery pageQuery);

    /**
     * 处理一张卡：确认某候选值 / 填写其他值 / 补充资料 / 暂不确认并阻断。
     * <p>处理完会重算闸门，任务状态随之刷新。</p>
     *
     * @param bo 处理入参
     * @return 处理后的卡片
     */
    CpInteractionCardVo resolve(ContentCardResolveBo bo);

}
