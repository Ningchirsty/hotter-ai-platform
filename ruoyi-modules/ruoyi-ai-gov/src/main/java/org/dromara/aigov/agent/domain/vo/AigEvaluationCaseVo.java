package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigEvaluationCase;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 黄金用例列表视图。
 *
 * <p><b>刻意不带 {@code expectedJson} 与 {@code rubricJson}</b>：它们是判分依据，
 * 列表页带出来容易让人「照抄一份」而不是真的建立评测标准；需要时走详情。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigEvaluationCase.class)
public class AigEvaluationCaseVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用例ID
     */
    private Long caseId;

    /**
     * 用例编码
     */
    private String caseCode;

    /**
     * 用例名称
     */
    private String caseName;

    /**
     * 用例类型
     */
    private String caseType;

    /**
     * 所属业务场景
     */
    private String scenarioCode;

    /**
     * 输入快照引用
     */
    private String inputSnapshotRef;

    /**
     * 成本范围下限
     */
    private BigDecimal costMin;

    /**
     * 成本范围上限
     */
    private BigDecimal costMax;

    /**
     * 数据等级
     */
    private String dataLevel;

    /**
     * 用例分类
     */
    private String classification;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;

}
