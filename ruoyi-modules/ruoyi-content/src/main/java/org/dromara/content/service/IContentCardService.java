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

    /**
     * 平面设计申请修改品牌要求（品牌 Brief）。
     *
     * <p><b>为什么走互动确认卡</b>：品牌要求的作者是品牌部，平面设计只读；设计想改不能直接改，
     * 但"提需求"必须能被品牌部看到、能被处理、能留下记录。互动卡本来就是这套语义
     * （谁提、谁处理、什么状态、处理说明），复用它比再造一张表更不容易偏离既有流程。</p>
     *
     * <p><b>刻意不阻断</b>：卡的 {@code blocking='N'}、只给一个 {@code SUPPLEMENT} 处理选项——
     * 设计提意见不该把任务卡住（{@code BLOCKED} 状态会让任务停在待确认）。</p>
     *
     * <p><b>幂等</b>：同一任务已存在待处理的同类申请时不重复建卡，直接返回既有的那张
     * （返回的 {@code created=false}），避免连点堆出十张一样的卡。</p>
     *
     * @param taskId  任务ID
     * @param message 设计要求修改的内容（必填，服务端截断到 500）
     * @return 结果（卡片ID、是否新建、标题）
     */
    ChangeRequest raiseBriefChangeRequest(Long taskId, String message);

    /**
     * 申请修改品牌要求的结果。
     *
     * @param cardId  互动卡ID
     * @param created 是否本次新建（false＝已有待处理申请，复用了它）
     * @param title   卡片标题（页面提示用）
     */
    record ChangeRequest(Long cardId, boolean created, String title) {
    }

}
