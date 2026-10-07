package org.dromara.aigov.agent.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Package 上传入参（设计 §6.1 身份与来源）。
 *
 * <p><b>包身份与版本号不在入参里</b>：它们以 Manifest 为准（Manifest 的
 * {@code package_code}/{@code version}/{@code checksum} 是必填项）。
 * 如果入参再传一份，就会出现「入参说 1.0.0、Manifest 说 1.0.1」这种两处不一致，
 * 而落库必须选一个——那不是设计要的。上传只补 Manifest 里没有的东西：
 * 来源引用与备注。</p>
 *
 * @author ai-gov
 */
@Data
public class AigPackageUploadBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Manifest 原文（JSON 文本）
     */
    @NotBlank(message = "Manifest 不能为空")
    private String manifestJson;

    /**
     * 来源引用（可信来源地址；可空）
     *
     * <p><b>这不是包体的存放位置</b>：包体的留存由 {@code aigov.package.store-body} 决定，
     * 开启后对象键写在<b>版本</b>的 {@code body_ref} 上（每次上传的包体可能不同，
     * 记在包上会被下一个版本覆盖）。本字段只用来记「这份包是从哪来的」（可信来源地址/备注性引用）。</p>
     */
    @Size(max = 500, message = "来源引用长度不能超过 500")
    private String sourceRef;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
