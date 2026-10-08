package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一轮模型健康探测的结果汇总（M-003）。
 *
 * @author ai-gov
 */
@Data
public class AigModelHealthProbeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 本轮实际探测的模型数
     */
    private int probed;

    /**
     * 探测判为健康的数量
     */
    private int healthy;

    /**
     * 探测判为不健康的数量
     */
    private int unhealthy;

    /**
     * 探测抛异常/未支持而跳过的数量
     */
    private int skipped;

    /**
     * 因超过 {@code maxPerRound} 而未在本轮处理的模型数（下一轮继续）
     */
    private int deferred;

    /**
     * 被判定为不健康的模型键（便于日志与告警直接读，不必翻全量日志）
     */
    private List<String> unhealthyModels = new ArrayList<>();

    /**
     * 本轮是否真的跑了（false = 未启用或被前置条件挡下）
     */
    private boolean executed;

    /**
     * 未执行的原因（executed=true 时为空）
     */
    private String skippedReason;

}
