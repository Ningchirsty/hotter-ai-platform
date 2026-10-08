package org.dromara.aigov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 评测灰度（门槛 {@code CANARY}）的达标阈值。
 *
 * <p><b>为什么这些值必须有默认值、且没有"关闭"开关</b>：{@code CANDIDATE → STABLE}
 * 这道门槛原先<b>没有任何证据校验</b>——只有 {@code MANIFEST_VALIDATION} 与
 * {@code GOLDEN_CASE} 两处 {@code assert*Evidence}，所以「灰度达标」完全由调用方声明。
 * 这正是本模块一路在堵的那类口子（「不生效也不会报错」）。因此这里的取舍与
 * {@link AigPackageProperties#isStoreBody()}（默认关、因为依赖外部设施）<b>刻意相反</b>：
 * 证据校验不是可选项，它一旦能被关掉，门槛就回到了「声明式达标」。
 * 与 {@code assertGoldenCaseEvidence} / {@code assertManifestScanEvidence} 同一口径——那两条也没有开关。</p>
 *
 * <p><b>默认值为何取保守值</b>：这三个阈值决定「什么样的表现才配从灰度转正式」。
 * 取宽了等于没有门槛（例如 {@code minInvocations=0} 时零调用也算达标）；取严了会把
 * 真正可用的版本卡住。默认 {@code 50 次 / 5% / 0 个严重错误} 是一个「能看出问题、
 * 又够得到」的起点；确有需要的部署方可以调，但<b>调宽它就是调宽发布门槛</b>，
 * 应当是一次有意识的运维动作而不是顺手改的默认值。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.canary")
public class AigCanaryProperties {

    /**
     * 灰度期内最少的调用次数。
     *
     * <p>没有足够样本时，"失败率低"只是样本少而不是质量好：调用 1 次成功、
     * 失败率 0%，与调用 500 次成功 499 次，可信度完全不同。因此先要求样本量。</p>
     */
    private int minInvocations = 50;

    /**
     * 灰度期内允许的最高失败率（0.05 = 5%）。
     *
     * <p>判据是「≤」，等于阈值算达标。失败率的分母是<b>全部</b>被归属到该版本的调用
     * （含失败），因为"失败的那次也占用了机会"。</p>
     */
    private double failureRateLimit = 0.05;

    /**
     * 灰度期内允许的严重错误次数（默认 0：一次都不允许）。
     *
     * <p>见 {@link org.dromara.aigov.agent.evaluation.AigCanaryEvidence#SEVERE_CLASS_CODES}，
     * 严重＝「说明版本或平台配置错了」的那几类失败（策略拒绝、Schema 错、鉴权/额度断供），
     * 而不是"上游当时忙"。这类错误一次就足以说明这个版本不该转正式。</p>
     */
    private int severeErrorsAllowed = 0;

}
