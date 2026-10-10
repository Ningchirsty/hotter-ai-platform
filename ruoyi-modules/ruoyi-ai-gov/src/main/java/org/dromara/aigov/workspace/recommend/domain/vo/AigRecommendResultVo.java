package org.dromara.aigov.workspace.recommend.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 自然语言推荐的结果（主文档线增量 7）。
 *
 * <h3>为什么这里**不带** traceId / 模型 / 策略原因</h3>
 * <p>门户是面向全体员工的契约（见 {@code AigPortalVoBoundaryTest}）：traceId、命中了哪个模型、
 * 走了哪条部署、花了多少钱，都是运维视角。这次调用的审计已经由网关写在
 * {@code aig_invocation_audit} 里——要靠 traceId 排查的人是从那里查，不是从员工的接口拿。
 * 所以这里只回"推荐你用它做这几张卡片"，查不到源头线索。</p>
 *
 * <p>调用失败不会被塞进 {@link #reason}：失败要**报错**（否则界面会显示"没有推荐"，
 * 而真实情况是"这次根本没调成"）。{@link #reason} 只用于"调用成功、但确实没什么可推荐"
 * 这种正常但需要解释的情况（例如当前没有任何可见岗位卡片）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRecommendResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 推荐结果（保序；已按服务端可见清单过滤）
     */
    private List<AigRecommendSuggestionVo> suggestions = new ArrayList<>();

    /**
     * 说明（可空）：调用成功但没有可推荐内容时，如实说明原因
     */
    private String reason;

}
