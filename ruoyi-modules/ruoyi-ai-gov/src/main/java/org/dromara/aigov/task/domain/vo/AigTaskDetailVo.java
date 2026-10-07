package org.dromara.aigov.task.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 任务详情（设计 §7.2 的「底部任务栏 + 左侧阶段树」所需的最小数据）。
 *
 * <p>把任务、输入快照、事件流、候选结果四样一次给全，而不是让前端再发三个请求：
 * 排障时最要紧的是「同时看到」，分三次请求会出现「任务已是成功、事件流还没到位」
 * 这种自相矛盾的中间画面，看的人会以为自己看错了。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务主体
     */
    private AigTaskVo task;

    /**
     * 当前使用的输入快照（不含历史版本；历史版本可在需要时另查）
     */
    private AigTaskSnapshotVo snapshot;

    /**
     * 事件流（按序号升序）
     */
    private List<AigTaskEventVo> events = new ArrayList<>();

    /**
     * 候选结果（按创建时间倒序）
     */
    private List<AigTaskResultVo> results = new ArrayList<>();

}
