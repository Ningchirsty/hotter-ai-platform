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
     * 平台侧密钥的<b>库中原值</b>——即 {@code sai_model_config.api_key} 里的 SM4 密文。
     *
     * <p>⚠️ <b>它不能直接当 Bearer 用</b>：必须先经
     * {@code AigModelSecretCipher#decrypt} 解出明文。这里保持「原值」语义是刻意的——
     * 解密只在真正要发请求的地方发生，避免明文在对象之间流转。</p>
     *
     * <p>仅探测用，禁止外传/打日志。</p>
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
