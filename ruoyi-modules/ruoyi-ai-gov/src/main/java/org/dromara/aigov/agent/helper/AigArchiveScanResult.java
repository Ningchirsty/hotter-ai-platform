package org.dromara.aigov.agent.helper;

import lombok.Data;
import org.dromara.aigov.agent.enums.AigPackageRejectRuleEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * 包体（压缩包）安全检查结论。
 *
 * <p>与 {@code AigManifestScanResult} 并列但<b>不合并</b>：那个查的是"包<b>声明</b>了什么"，
 * 这个查的是"包<b>里装</b>了什么"。两者可以一个过一个不过——例如 Manifest 声明得干干净净、
 * 包里却塞了一个 {@code install.sh}。合并成一个结论会让"到底是声明有问题还是内容有问题"
 * 无法回答，而这两件事的处置不同（改 Manifest vs 换包）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigArchiveScanResult {

    /**
     * 是否通过（{@code true} = 未命中任何拒绝规则）
     */
    private boolean pass;

    /**
     * 命中的拒绝规则（按枚举顺序去重）
     */
    private List<AigPackageRejectRuleEnum> hitRules = new ArrayList<>();

    /**
     * 扫描说明（含扫描范围与边界，可直接给人看）
     */
    private String detail;

    /**
     * 是否真的扫了（非 ZIP 体、或开关关闭时为 false）。
     *
     * <p>它与 {@link #pass} <b>不是一回事</b>：没扫也返回 {@code pass=true}
     * （不因为"我们没查"就拒绝一个合法包），但调用方/日志必须能看出
     * "这次是通过检查，还是根本没检查"。把两者混为一谈，安全结论就成了空话。</p>
     */
    private boolean scanned;

    /**
     * 命中的规则编码清单（便于日志与断言）
     *
     * @return 编码清单
     */
    public List<String> hitRuleCodes() {
        List<String> codes = new ArrayList<>();
        if (hitRules != null) {
            for (AigPackageRejectRuleEnum rule : hitRules) {
                codes.add(rule.getCode());
            }
        }
        return codes;
    }

}
