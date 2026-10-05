package org.dromara.creative.helper;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.creative.domain.DpOutputSpec;
import org.dromara.creative.service.ICreativeScenarioConfigService;

import java.util.List;

/**
 * 「该交付类型的默认输出规格」的**唯一取法**（内测冲突 C 的收口）。
 *
 * <p>尺寸的权威只有一个：`dp_output_spec` 里该交付类型的默认项。但"谁需要它"已经有三处——
 * 排版取页宽、终版验收判尺寸、以及开工包交接视图（C5①，让设计侧看到本次要出多大）。
 * 三处各查一遍的代价不是重复几行，而是**迟早出现两个结论**：
 * 排版按 750 渲、验收按别的判、交接凭证上写第三种尺寸，而没人能说清哪个对。</p>
 *
 * <p>放在 helper 而不是某个 service 里：调用方各自持有自己的依赖，不引入服务间环。</p>
 *
 * @author creative
 */
@Slf4j
public final class CreativeOutputSpecs {

    private CreativeOutputSpecs() {
    }

    /**
     * 取该交付类型的默认输出规格。
     *
     * @param deliveryType          交付类型（可空）
     * @param scenarioConfigService 场景配置服务
     * @return 默认规格；没有配置或读取失败时返回 {@code null}（调用方各自决定回落策略：排版回落配置页宽、展示则留空如实说）
     */
    public static DpOutputSpec defaultSpec(String deliveryType, ICreativeScenarioConfigService scenarioConfigService) {
        if (StringUtils.isBlank(deliveryType)) {
            return null;
        }
        try {
            List<DpOutputSpec> specs = scenarioConfigService.listOutputSpecs(deliveryType);
            return specs.isEmpty() ? null : specs.get(0);
        } catch (Exception e) {
            log.warn("读取默认输出规格失败（交付类型 {}）：{}", deliveryType, e.getMessage());
            return null;
        }
    }

    /**
     * 默认输出规格的可读尺寸文案。
     *
     * @param spec 规格（可空）
     * @return 形如「750×自动高度（TAOBAO_DETAIL）」；没有规格时返回 null
     */
    public static String describe(DpOutputSpec spec) {
        if (spec == null || spec.getWidth() == null || spec.getWidth() <= 0) {
            return null;
        }
        String size = "FIXED".equalsIgnoreCase(spec.getHeightMode())
            ? spec.getWidth() + "×" + (spec.getHeight() == null ? "?" : spec.getHeight())
            : spec.getWidth() + "×自动高度";
        return StringUtils.isBlank(spec.getSpecCode()) ? size : size + "（" + spec.getSpecCode() + "）";
    }
}
