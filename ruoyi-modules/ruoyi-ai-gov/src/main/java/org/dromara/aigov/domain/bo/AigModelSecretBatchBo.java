package org.dromara.aigov.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.EditGroup;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 供应商维度批量写入模型密钥业务对象（{@code PUT /aigov/model/secret/batch}）。
 *
 * <p><b>为什么需要批量</b>：同一家供应商下的模型通常共用一把 Key
 * （实测 bluocto 的 7 个图像模型就是同一个 token 分组）。此时「逐个模型录入」
 * 要求管理员把同一串明文粘贴 N 次，N 次都可能贴错、也可能贴到一半漏掉，
 * 事后从 {@code api_key} 列看不出哪几个是漏的。批量入口把「一次动作 = 一把 Key
 * 覆盖一组模型」显式化，语义与真实世界的凭据粒度对齐。</p>
 *
 * <p><b>不是「不加密」的捷径</b>：明文在 Service 内只加密<b>一次</b>，
 * 复用同一段 SM4/CBC/PKCS5 密文写入每一行。该做法安全上等价于逐行加密，
 * 因为 {@code AigModelSecretCipher} 用的是固定 IV 的确定性加密——
 * 同一明文逐行加密与加密一次再复制，产出的密文逐字节相同。
 * 因此批量入口没有引入任何新的密文形态，snail-ai 侧的兼容性不变。</p>
 *
 * <p><b>范围默认保守</b>：{@link #modelIds} 为空时作用于该供应商下
 * <b>全部已登记模型</b>（含停用模型——停用只是路由不选它，凭据仍应保持一致，
 * 否则重新启用时会带着旧 Key 静默失败）；显式传入时只作用于列出的模型。</p>
 *
 * @author ai-gov
 */
@Data
public class AigModelSecretBatchBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 供应商ID（{@code sai_model_provider.id}）
     */
    @NotNull(message = "供应商ID不能为空", groups = {EditGroup.class})
    private Long providerId;

    /**
     * 目标模型ID列表；为空表示该供应商下全部已登记模型
     * <p>传入的模型必须确实属于 {@link #providerId}，否则整批拒绝——
     * 避免「以为在改 A 家，实际把 B 家的凭据覆盖了」。</p>
     */
    private List<Long> modelIds;

    /**
     * 明文 API 密钥。
     * <p>明文上限 500：{@code api_key} 列为 {@code VARCHAR(1000)}，SM4 密文经 Base64 后会膨胀。</p>
     */
    @Size(max = 500, message = "API 密钥长度不能超过 500", groups = {EditGroup.class})
    private String apiKey;

    /**
     * 是否清除密钥；为 true 时忽略 {@link #apiKey} 并把目标模型的密钥全部置空
     */
    private Boolean clearKey;

}
