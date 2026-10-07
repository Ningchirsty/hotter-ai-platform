package org.dromara.aigov.agent.manifest;

import lombok.Data;
import org.dromara.aigov.agent.enums.AigPackageRejectRuleEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * Manifest 扫描结论（设计 §6.2）。
 *
 * <p>{@link #detail} 直接落进 {@code aig_package_version.scan_detail}，因此长度被
 * {@link AigPackageManifestValidator#DETAIL_MAX_LENGTH} 硬性截断——一份畸形 Manifest
 * （例如塞 500 个未声明字段）不能让扫描结论写不进库：那会表现为「扫描失败」，
 * 而不是「这个包被拒绝了」，两件事在运维眼里完全不同。</p>
 *
 * @author ai-gov
 */
@Data
public class AigManifestScanResult {

    /**
     * 通过（{@code scan_result} 取值）
     */
    public static final String PASS = "PASS";

    /**
     * 拒绝（{@code scan_result} 取值）
     */
    public static final String REJECT = "REJECT";

    /**
     * 待扫描（{@code scan_result} 取值）。
     *
     * <p>保留给「已提交、尚未扫描」的异步路径；同步扫描器永不返回它——
     * 一个同步调用若返回「待扫描」，调用方拿到的其实是没有结论。</p>
     */
    public static final String PENDING = "PENDING";

    /**
     * 是否通过（{@code true} = 未命中任何拒绝规则）
     */
    private boolean pass;

    /**
     * 命中的拒绝规则（按枚举顺序，去重）
     */
    private List<AigPackageRejectRuleEnum> hitRules = new ArrayList<>();

    /**
     * 扫描说明（落 {@code scan_detail}，已截断至列宽）
     */
    private String detail;

    /**
     * Manifest 原文的 SHA-256（对传入的那串字节计算）
     */
    private String manifestHash;

    /**
     * 解析出的 Manifest 视图；原文无法解析为 JSON 对象时为 {@code null}
     */
    private AigPackageManifest manifest;

    /**
     * 入库取值：PASS / REJECT。
     *
     * @return {@code scan_result} 列的值
     */
    public String scanResult() {
        return pass ? PASS : REJECT;
    }

    /**
     * 命中规则的编码清单（便于日志与断言）。
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
