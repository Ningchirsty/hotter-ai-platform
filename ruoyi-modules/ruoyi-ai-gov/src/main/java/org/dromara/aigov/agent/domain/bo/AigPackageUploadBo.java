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
     * 来源引用（上传存储键或可信来源地址；可空）
     *
     * <p><b>本阶段刻意不落包体</b>：声明式 Package 的安装只读 Manifest，包体仅用于
     * 让服务端核对 {@code checksum}。所以「上传」= 携包体登记并校验哈希，
     * 包体本身不入库、不假装入了对象存储。要留存包体，后续把包体交给平台文件服务并把
     * 返回的存储键填进本字段。</p>
     */
    @Size(max = 500, message = "来源引用长度不能超过 500")
    private String sourceRef;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
