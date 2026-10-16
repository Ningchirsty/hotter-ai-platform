package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 能力「配置完整度」聚合视图（能力清单/看板用，设计见
 * {@code docs/aigov/design/P0-能力域-字段级规格与改动清单.md} §4）。
 *
 * <h3>为什么需要它，而不是让前端自己拼</h3>
 * <p>能力卡片要显示「候选 N · 策略 M/4 · 近 24h 调用」与「链路完整 / 缺模型候选 / 候选健康异常」。
 * 若前端对每张卡发 3~4 个请求，一屏 12 张卡就是 40+ 请求，而且**筛选项必须下发到服务端**
 * （"只看缺模型候选的能力"不能在前端拿一页数据筛）。所以这里一次算完。</p>
 *
 * <h3>两条口径</h3>
 * <ul>
 *     <li><b>issueCodes 是"这张卡为什么红"的唯一来源</b>：颜色、排序、筛选都由它推导，
 *         避免"颜色在 CSS 里、规则在别处"两套口径；</li>
 *     <li><b>24h 统计只算窗口内的行</b>：审计表是追加型，窗口有界；窗口内行数在万级以内，
 *         用一次"只取两列"的查询在应用侧聚合，比拼接 SQL 聚合更不容易被方言/别名坑到
 *         （见实现里的注释）。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Data
public class AigCapabilityOverviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 能力ID
     */
    private Long capabilityId;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 能力名称
     */
    private String capabilityName;

    /**
     * 需要的模型能力标签（逗号分隔）
     */
    private String requiredTags;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 最近更新时间（列表按"有问题的在前、其次最近改动的在前"排序）
     */
    private LocalDateTime updateTime;

    /**
     * 启用的模型候选数
     */
    private Integer candidateCount;

    /**
     * 首选候选的模型键（用途 PRIMARY 且优先级最小；没有任何候选时为空）
     */
    private String primaryModelKey;

    /**
     * 候选健康度汇总：{@code NONE}（无候选） / {@code HEALTHY} / {@code UNHEALTHY} / {@code UNKNOWN}
     */
    private String candidateHealthAgg;

    /**
     * 已配置策略的数据等级数（0~4）
     */
    private Integer policyCoveredLevels;

    /**
     * 需要配置的策略总数（固定 4：公开/内部/限制/严格）
     */
    private Integer policyTotalLevels;

    /**
     * 场景指定条数
     */
    private Integer sceneCount;

    /**
     * 近 24h 调用次数
     */
    private Integer calls24h;

    /**
     * 近 24h 失败次数
     */
    private Integer fails24h;

    /**
     * 这张卡"为什么红"的原因码集合；空表示链路完整。
     *
     * <p>取值：{@code NO_MODEL_CANDIDATE} / {@code CANDIDATE_UNHEALTHY} /
     * {@code NO_ROUTE_POLICY} / {@code POLICY_INCOMPLETE}。</p>
     */
    private List<String> issueCodes = new ArrayList<>();

}
