package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

import java.util.List;

/**
 * 评测运行入参（设计 §13.2）。
 *
 * <p>{@code caseCodes} 必须与版本声明的黄金用例集合<b>完全一致</b>（顺序不限）。
 * 这个约束是刻意的：允许「跑自选的一两条」就等于允许用最容易过的用例去换一个
 * 「黄金用例通过」的结论。</p>
 *
 * @author ai-gov
 */
@Data
public class AigEvaluationRunBo {

    /**
     * 评测对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION）
     */
    private String targetType;

    /**
     * 评测对象版本ID
     */
    private Long targetVersionId;

    /**
     * 本次要跑的用例编码（必须等于版本声明的黄金用例集合）
     */
    private List<String> caseCodes;

    /**
     * 操作人（留空则由平台自动填充当前登录人）
     */
    private Long operatorId;

    /**
     * 备注
     */
    private String remark;

}
