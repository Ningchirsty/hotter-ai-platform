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
 * 工作台装配 dp_workspace_schema（V0.2 B1）：解决"不同需求展示页面不同"。
 *
 * <p>文档 §10 的红线：以后不许再往 {@code views/creative/} 里按场景加页面，而是按这份装配定义渲染。
 * 本轮只登记配置（种子照文档 §49），前端运行时（registry + CreativeWorkspace）属 D 阶段。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_workspace_schema")
public class DpWorkspaceSchema extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 工作台编码（如 WS_LONG_PAGE）
     */
    private String schemaCode;

    /**
     * 所属交付类型
     */
    private String deliveryType;

    /**
     * 所属场景档案（可空=交付类型级默认）
     */
    private Long profileId;

    /**
     * 版本
     */
    private String version;

    /**
     * 装配定义（panels 列表 + steps→component 映射）
     */
    private String layoutJson;

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
