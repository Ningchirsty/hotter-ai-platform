package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 黄金用例定义入参（设计 §13.2）。
 *
 * @author ai-gov
 */
@Data
public class AigEvaluationCaseBo {

    /**
     * 用例编码（全局唯一）
     */
    private String caseCode;

    /**
     * 用例名称
     */
    private String caseName;

    /**
     * 用例类型（PLAN/VISUAL_DNA/IMAGE_QA）
     */
    private String caseType;

    /**
     * 所属业务场景
     */
    private String scenarioCode;

    /**
     * 输入快照引用（对象键/业务ID；只存引用不存副本）
     */
    private String inputSnapshotRef;

    /**
     * 机器判据（JSON；留空表示该用例只做人工 Rubric）
     */
    private String expectedJson;

    /**
     * 人工评分 Rubric（JSON；填了就意味着这条用例需要人工复核）
     */
    private String rubricJson;

    /**
     * 成本范围下限（算不出留空，禁止填 0 冒充）
     */
    private BigDecimal costMin;

    /**
     * 成本范围上限
     */
    private BigDecimal costMax;

    /**
     * 数据等级（沙箱运行须按此脱敏）
     */
    private String dataLevel;

    /**
     * 用例分类
     */
    private String classification;

    /**
     * 备注
     */
    private String remark;

}
