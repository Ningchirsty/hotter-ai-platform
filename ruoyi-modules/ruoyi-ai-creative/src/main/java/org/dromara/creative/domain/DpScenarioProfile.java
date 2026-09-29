package org.dromara.creative.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 场景档案 dp_scenario_profile（V0.2 B1）：描述"一种业务怎么生产"。
 *
 * <p>文档 §7 的原则：Java Service 不判断"是不是小红书/是不是详情页"，而是读这份档案。
 * 本轮只提供**只读**查询；既有流程（阶段机/闸门/排版）暂不按它驱动，B2 才切。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_scenario_profile")
public class DpScenarioProfile extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 档案编码（如 PROFILE_ECOM_DETAIL_V1）
     */
    private String profileCode;

    /**
     * 档案名称
     */
    private String profileName;

    /**
     * 交付类型编码
     */
    private String deliveryType;

    /**
     * 档案版本
     */
    private String version;

    /**
     * 输入要求（结构化）
     */
    private String inputSchemaJson;

    /**
     * 流程总览（步骤编码顺序；权威在 dp_scenario_step，这里冗余便于一次读全）
     */
    private String workflowSchemaJson;

    /**
     * 交付物要求（结构化）
     */
    private String outputSchemaJson;

    /**
     * 工作台装配（引用 dp_workspace_schema.schema_code）
     */
    private String workspaceSchemaJson;

    /**
     * 状态（DRAFT/PUBLISHED/RETIRED）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    @TableLogic
    private String delFlag;

}
