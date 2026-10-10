package org.dromara.aigov.workspace.recommend.service;

import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.recommend.domain.bo.AigRecommendSuggestBo;
import org.dromara.aigov.workspace.recommend.domain.vo.AigRecommendResultVo;

/**
 * 自然语言推荐服务（主文档线增量 7；设计稿 §6.1 的 {@code POST /intent/suggest}）。
 *
 * <h3>它是什么、不是什么</h3>
 * <p><b>是</b>：把"这个人看得见的卡片清单"和"他的一句话需求"交给一次受治理的模型调用，
 * 让它挑出最相关的几张卡片，再把结果**与可见清单求交**后返回。</p>
 * <p><b>不是</b>：不执行、不启动、不建任务。推荐只是"猜你想要哪张"，最终仍要用户确认，
 * 启动凭证走增量 3 的 Launch Resolver。</p>
 *
 * <h3>三条不可让步</h3>
 * <ol>
 *     <li><b>默认关闭</b>（{@code aigov.recommend.enabled=false}）：这一次调用是真花钱的，
 *         关着时接口**明确报错**，绝不静默返回空列表（返回空会被读成"没有相关卡片"）。</li>
 *     <li><b>必须走网关</b>（{@code IAigInvokeService}）：策略、配额、路由、审计都在那一层；
 *         推荐不开第二条模型通道，也不自己写审计表。</li>
 *     <li><b>结果必须与服务端可见清单求交</b>：提示词里只放可见卡片，但模型完全可能
 *         凭常识吐出不存在的编码——那种一律丢掉，绝不"查不到就放过"。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface IAigRecommendService {

    /**
     * 按自然语言需求推荐卡片。
     *
     * @param bo    入参（一句话需求）
     * @param actor 当前门户用户（候选清单的唯一来源）
     * @return 推荐结果（已过滤；调用失败会抛错而不是返回空）
     * @throws org.dromara.common.core.exception.ServiceException 功能关闭、入参非法、未配置能力、调用失败
     */
    AigRecommendResultVo suggest(AigRecommendSuggestBo bo, AigPortalActor actor);

}
