package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模型供应商选项视图对象。
 * <p>仅用于「新增模型」表单的下拉选项，字段最小化，不含任何连接凭据。</p>
 *
 * @author ai-gov
 */
@Data
public class AigModelProviderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 供应商ID（sai_model_provider.id）
     */
    private Long providerId;

    /**
     * 供应商名称
     */
    private String providerName;

    /**
     * 供应商标识
     */
    private String providerKey;

}
