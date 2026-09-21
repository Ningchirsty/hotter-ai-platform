package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 连通性测试所需的模型配置快照（仅服务端内部使用，<b>绝不下发到前端</b>）。
 * <p>
 * 这里刻意单独建一个 VO 而不是复用 {@link AigModelVo}：因为要读 {@code sai_model_config.api_key}，
 * 而列表/详情接口的口径是「任何响应都不含 api_key」。把带密钥的快照隔离在内部类型里，
 * 可以避免它被顺手加到对外响应上。
 * </p>
 *
 * @author ai-gov
 */
@Data
public class AigModelTestTargetVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long modelId;

    private String modelKey;

    private String modelName;

    private String modelType;

    private String adapterKey;

    private String apiEndpoint;

    /**
     * 平台侧密钥（仅探测用，禁止外传/打日志）
     */
    private String apiKey;

    private Boolean isEnabled;

    private String providerName;

    private String providerKey;

    /**
     * 治理属性：部署类型（LOCAL 表示本地执行者，不需要出网探测）
     */
    private String deploymentType;
}
