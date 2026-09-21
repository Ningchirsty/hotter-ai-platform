package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型供应商视图对象。
 * <p>用于「新增模型」表单的下拉选项与供应商管理列表，字段最小化，不含任何连接凭据。</p>
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

    /**
     * 说明
     */
    private String description;

    /**
     * 图标地址
     */
    private String iconUrl;

    /**
     * 是否启用（停用后不出现在新增模型的下拉里）
     */
    private Boolean isEnabled;

    /**
     * 该供应商下已登记的模型数量（停用前用于提示影响面）
     */
    private Integer modelCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdDt;

}
