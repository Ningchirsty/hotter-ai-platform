package org.dromara.aigov.service.invoker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.service.IAigTalentMatchService;
import org.springframework.stereotype.Component;

/**
 * 本地规则调用器（本地私有部署）。
 * <p>不访问任何网络，阶段1 实现 {@code talent_match} 的本地规则匹配，
 * 用于本地端到端验证；snail-ai 未启用时治理层仍可完整跑通。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalRuleModelInvoker implements ModelInvoker {

    /**
     * {@code talent_match} 本地规则实现。
     */
    private final IAigTalentMatchService talentMatchService;

    @Override
    public boolean supports(AigDeploymentTypeEnum deploymentType) {
        return deploymentType == AigDeploymentTypeEnum.LOCAL;
    }

    @Override
    public boolean available() {
        // 纯本地计算，无外部依赖，恒可用
        return true;
    }

    @Override
    public ModelInvokeResult invoke(ModelInvokeRequest request) {
        if (request == null || request.getDeploymentType() != AigDeploymentTypeEnum.LOCAL) {
            return ModelInvokeResult.failure("本地规则调用器仅支持 LOCAL 部署类型", 0L);
        }
        long start = System.currentTimeMillis();
        try {
            if (!AigConstants.CAP_TALENT_MATCH.equals(request.getCapabilityCode())) {
                return ModelInvokeResult.failure("本地规则调用器阶段1仅支持 " + AigConstants.CAP_TALENT_MATCH,
                    System.currentTimeMillis() - start);
            }
            String output = talentMatchService.match(request.getPayload());
            ModelInvokeResult result = ModelInvokeResult.success(output, System.currentTimeMillis() - start);
            result.setModelVersion(request.getModelKey());
            return result;
        } catch (Exception e) {
            // SPI 约定：不向外抛异常
            log.error("本地规则调用异常, capabilityCode={}, modelId={}",
                request.getCapabilityCode(), request.getModelId(), e);
            return ModelInvokeResult.failure("本地规则调用异常：" + e.getClass().getSimpleName(),
                System.currentTimeMillis() - start);
        }
    }

}
