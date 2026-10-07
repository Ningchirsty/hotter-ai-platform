package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 黄金用例 aig_evaluation_case（设计 §13.2）。
 *
 * <p><b>为什么用例要把「预期规则」和「评分 Rubric」分开存</b>：它们是两种不同的东西——
 * {@link #expectedJson} 是<b>可机器判定</b>的期望（例如「输出必须含 logo 区域且不遮挡产品主体」），
 * 用于自动判分；{@link #rubricJson} 是<b>人工评分</b>的标准（分几档、每档看什么），
 * 用于人工复核。混成一个字段会导致「自动判分」与「人工评分」互相污染，
 * 而这两者的结论在验收时是要分开看的（自动筛除、人工放行）。</p>
 *
 * <p><b>{@link #costMin}/{@link #costMax} 刻意可为空</b>：算不出成本范围时留空，
 * <b>不允许填 0 冒充</b>——0 与「未知」在这套系统里是两种完全不同的结论，
 * 混在一起会让后续的预算比对得出「这个用例不花钱」这种错误认知。</p>
 *
 * <p><b>{@link #dataLevel} 决定沙箱怎么脱敏</b>：设计 §6.3-4 要求在沙箱项目里跑
 * <b>脱敏</b>用例，脱敏规则就按这个等级来（STRICT 的用例不能因为「只是评测」就整份拿去外发）。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_evaluation_case")
public class AigEvaluationCase extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用例ID
     */
    @TableId(value = "case_id")
    private Long caseId;

    /**
     * 用例编码（唯一）
     */
    private String caseCode;

    /**
     * 用例名称
     */
    private String caseName;

    /**
     * 用例类型（PLAN 详情页策划 / VISUAL_DNA 参考图视觉分析 / IMAGE_QA 图像生成与QA）
     */
    private String caseType;

    /**
     * 所属业务场景
     */
    private String scenarioCode;

    /**
     * 输入快照引用（不存副本）
     */
    private String inputSnapshotRef;

    /**
     * 预期规则（可机器判定）
     */
    private String expectedJson;

    /**
     * 人工评分 Rubric
     */
    private String rubricJson;

    /**
     * 成本范围下限（算不出留空）
     */
    private BigDecimal costMin;

    /**
     * 成本范围上限（算不出留空）
     */
    private BigDecimal costMax;

    /**
     * 数据等级（沙箱按此脱敏）
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
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
