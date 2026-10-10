package org.dromara.aigov.workspace.recommend.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalActionVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleHomeVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.service.IAigPortalService;
import org.dromara.aigov.workspace.recommend.config.AigRecommendProperties;
import org.dromara.aigov.workspace.recommend.domain.bo.AigRecommendSuggestBo;
import org.dromara.aigov.workspace.recommend.domain.vo.AigRecommendResultVo;
import org.dromara.aigov.workspace.recommend.domain.vo.AigRecommendSuggestionVo;
import org.dromara.aigov.workspace.recommend.helper.AigRecommendParser;
import org.dromara.aigov.workspace.recommend.helper.AigRecommendPromptBuilder;
import org.dromara.aigov.workspace.recommend.service.IAigRecommendService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自然语言推荐实现（主文档线增量 7）。
 *
 * <h3>为什么"结果要再求一次交"看起来是多余的</h3>
 * <p>提示词里已经把清单限定成"这个人可见的卡片"，模型也被要求"只能从这里挑"。
 * 但**要求**不等于**保证**：模型完全可能凭常识吐出一个根本没喂给它的编码
 * （比如它记得某个岗位里有个 {@code DETAIL_PAGE_CREATE}）。如果直接把模型说的编码回给前端，
 * 前端就会拿着一个查不到的编码去渲染——或者更糟，如果前端信任了它，就会绕开可见性。
 * 所以这里的交点是**结论的唯一出处**：编码来自模型，卡片本体只可能来自服务端可见清单。</p>
 *
 * <h3>为什么失败要抛错而不是返回空</h3>
 * <p>"没有推荐"和"这次没调成"是两件完全不同的事。若都返回空列表，员工会以为系统认为
 * 没有相关卡片；而真实情况可能是配额耗尽、策略拒绝、模型超时。这类"静默降级"是最难排查的，
 * 因此调用失败一律抛错，理由如实带上。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigRecommendServiceImpl implements IAigRecommendService {

    private final IAigPortalService portalService;
    private final IAigInvokeService invokeService;
    private final AigRecommendProperties properties;

    @Override
    public AigRecommendResultVo suggest(AigRecommendSuggestBo bo, AigPortalActor actor) {
        if (!properties.isEnabled()) {
            throw new ServiceException("自然语言推荐当前是关闭的（aigov.recommend.enabled=false）："
                + "一次推荐就是一次真实计费的模型调用，需由部署方明确开启");
        }
        requireActor(actor);
        String input = bo == null ? null : bo.getInput();
        if (StringUtils.isBlank(input)) {
            throw new ServiceException("请先用一句话描述你要做的事");
        }
        String demand = input.trim();
        int limit = properties.effectiveMaxInputChars();
        if (demand.length() > limit) {
            throw new ServiceException("描述过长（" + demand.length() + " > " + limit
                + " 字符）：请说得简短些");
        }
        if (StringUtils.isBlank(properties.getCapabilityCode())) {
            throw new ServiceException("推荐没有配置能力编码（aigov.recommend.capability-code）："
                + "无法路由，请先登记一条推荐能力");
        }
        AigDataLevelEnum dataLevel = AigDataLevelEnum.find(properties.getDataLevel());
        if (dataLevel == null) {
            throw new ServiceException("推荐配置了非法的数据等级：" + properties.getDataLevel());
        }

        // ★ 候选清单只有一个来源：服务端算出的"当前用户可见卡片"。
        // 调用方无法指定候选（那会让推荐接口变成探测别人岗位编码的工具）。
        List<AigPortalRoleHomeVo> homes = portalService.listMyRoleHomes(actor);
        Map<String, AigRecommendSuggestionVo> visible = visibleCatalog(homes);
        AigRecommendResultVo result = new AigRecommendResultVo();
        if (visible.isEmpty()) {
            // 不是错误：这个人当前确实没有任何可用卡片。如实说明，不去调模型白花钱。
            result.setReason("当前没有任何对你开放的岗位卡片，无法推荐");
            return result;
        }

        AigInvokeBo invokeBo = new AigInvokeBo();
        invokeBo.setCapabilityCode(properties.getCapabilityCode().trim());
        invokeBo.setDataLevel(dataLevel.getCode());
        invokeBo.setPrompt(AigRecommendPromptBuilder.build(homes,
            properties.effectiveMaxCatalogCards(), demand));
        // 刻意不设 agentVersionId / taskId：推荐不属于任何任务，
        // 也不该被算作某个 Agent 版本的调用（那会拿推荐流量伪造灰度证据）。

        AigInvokeVo invoked;
        try {
            invoked = invokeService.invoke(invokeBo);
        } catch (Exception e) {
            log.warn("推荐调用异常, userId={}: {}", actor.userId(), e.getMessage());
            throw new ServiceException("推荐调用失败：" + e.getMessage());
        }
        String output = invoked == null ? null : invoked.getOutput();
        if (invoked == null || StringUtils.isNotBlank(invoked.getErrorCode()) || StringUtils.isBlank(output)) {
            String reason = invoked == null ? "网关没有返回结果"
                : StringUtils.blankToDefault(invoked.getReason(), "模型未产出结果");
            throw new ServiceException("推荐调用未成功：" + reason);
        }

        List<String> codes = AigRecommendParser.parse(output, properties.effectiveMaxCandidates());
        // ★ 交集：模型说错也不放行。一个编码可能在多个岗位里重名，
        // 取岗位编码排序下最先出现的那个（确定性，不随 Map 实现变化）。
        for (String code : codes) {
            AigRecommendSuggestionVo suggestion = visible.get(code);
            if (suggestion != null) {
                result.getSuggestions().add(suggestion);
            }
        }
        log.info("推荐完成, userId={}, 可见卡片={}, 模型返回={}, 过滤后={}",
            actor.userId(), visible.size(), codes.size(), result.getSuggestions().size());
        return result;
    }

    /**
     * 可见卡片索引：卡片编码 → 建议项（附带岗位信息）。
     *
     * <p>用 {@code putIfAbsent} 而不是覆盖：编码跨岗位重名时结果要确定，
     * 不能依赖遍历顺序的巧合。入参已按岗位编码排序，因此"最先出现"是稳定的。</p>
     *
     * @param homes 可见岗位首页
     * @return 索引（保留顺序）
     */
    private Map<String, AigRecommendSuggestionVo> visibleCatalog(List<AigPortalRoleHomeVo> homes) {
        Map<String, AigRecommendSuggestionVo> index = new LinkedHashMap<>();
        if (homes == null) {
            return index;
        }
        for (AigPortalRoleHomeVo home : homes) {
            if (home == null || home.getActions() == null) {
                continue;
            }
            for (AigPortalActionVo action : home.getActions()) {
                if (action == null || StringUtils.isBlank(action.getActionCode())) {
                    continue;
                }
                AigRecommendSuggestionVo suggestion = new AigRecommendSuggestionVo();
                suggestion.setRoleCode(home.getRoleCode());
                suggestion.setRoleName(home.getRoleName());
                suggestion.setAction(action);
                index.putIfAbsent(action.getActionCode().trim(), suggestion);
            }
        }
        return index;
    }

    /**
     * 推荐必须有登录用户（候选清单按人算）。
     *
     * @param actor 用户
     */
    private void requireActor(AigPortalActor actor) {
        if (actor == null || actor.userId() == null) {
            throw new ServiceException("AI 工作台需要登录用户");
        }
    }

}
