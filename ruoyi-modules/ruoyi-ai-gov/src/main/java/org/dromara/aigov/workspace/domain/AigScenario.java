package org.dromara.aigov.workspace.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 场景包 {@code aig_scenario}（附件 §4、§6.1）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_role_workspace.sql} 的列注释为准</b>，此处不重述。
 * 这样做的理由：列语义只写在一个地方，改列时不会出现"DDL 改了、javadoc 还是旧的"。</p>
 *
 * <p><b>它是"轻量"的</b>：只锁版本与引用，<b>不是流程引擎</b>——真实流程仍由创作域阶段机、
 * 视频链路、内容链路执行（用户拍板 Q2）。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_scenario")
public class AigScenario extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 场景ID
     */
    @TableId(value = "scenario_id")
    private Long scenarioId;

    /**
     * 场景编码（跨版本稳定）
     */
    private String scenarioCode;

    /**
     * 场景名称
     */
    private String scenarioName;

    /**
     * 归属组织编码（F-05：组织树只有 sys_dept 前两级）
     */
    private String ownerOrgCode;

    /**
     * 说明（做什么、不做什么）
     */
    private String description;

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
