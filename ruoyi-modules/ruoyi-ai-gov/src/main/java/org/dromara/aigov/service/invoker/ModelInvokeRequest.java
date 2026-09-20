package org.dromara.aigov.service.invoker;

import lombok.Data;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 模型调用请求（SPI 入参）。
 * <p>由调用编排在路由命中后组装；<b>不含明文密钥</b>，只携带 {@code secretRef} 引用。</p>
 *
 * @author ai-gov
 */
@Data
public class ModelInvokeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 模型ID（sai_model_config.id）
     */
    private Long modelId;

    /**
     * 模型键
     */
    private String modelKey;

    /**
     * 模型类型（CHAT/EMBEDDING/…）
     */
    private String modelType;

    /**
     * 部署类型
     */
    private AigDeploymentTypeEnum deploymentType;

    /**
     * 密钥引用（如 kms://ai/qwen），由调用方解析，禁止明文
     */
    private String secretRef;

    /**
     * API 端点（仅在有 aig:model:secret 权限的链路中可能出现）
     */
    private String endpoint;

    /**
     * 本次数据等级
     */
    private AigDataLevelEnum dataLevel;

    /**
     * 提示词
     */
    private String prompt;

    /**
     * 结构化业务载荷
     */
    private Map<String, Object> payload;

}
