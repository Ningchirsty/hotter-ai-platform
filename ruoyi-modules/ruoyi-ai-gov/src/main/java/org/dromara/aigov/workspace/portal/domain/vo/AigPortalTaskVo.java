package org.dromara.aigov.workspace.portal.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 门户里的"我的任务"（主文档线增量 2）。
 *
 * <h3>刻意比管理台的任务视图少很多字段</h3>
 * <p>不算<b>计费金额</b>（成本是运营视角，不是员工需要知道的）、不算 <b>供应商编码/供应商任务号</b>
 * （那是内部实现，一旦出现在对外契约里就变成不能轻易换的东西）、不算 <b>traceId</b>
 * （排查线索由运维入口提供）、不算 <b>策略原因/错误详情</b>（可能包含内部判定细节
 * 与上游原始报错）。这个取舍有专门用例守着（见 {@code AigPortalTaskVoTest}）：
 * 门户多一个字段不会报错，只会某天把内部细节发给全体员工。</p>
 *
 * @author ai-gov
 */
@Data
public class AigPortalTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID（跳转专业台/回跳时用）
     */
    private Long taskId;

    /**
     * 任务编号（员工沟通时引用）
     */
    private String taskNo;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 场景编码（从岗位卡片启动时带上）
     */
    private String scenarioCode;

    /**
     * 项目类型
     */
    private String projectType;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 任务状态的中文（由服务端给，避免前端各写一份映射）
     */
    private String statusLabel;

    /**
     * 进度百分比
     */
    private Integer progress;

    /**
     * 数据等级（让员工知道这次用的是哪一档数据）
     */
    private String dataLevel;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime finishedAt;

}
