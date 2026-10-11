package org.dromara.aigov.workspace.portal.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户 ↔ 品牌归属 {@code aig_user_brand}（④）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_user_brand.sql} 的列注释为准</b>。</p>
 *
 * <p><b>它是什么、不是什么</b>：是一张最小<b>主数据登记表</b>——只回答"这个用户属于哪些品牌"，
 * 供岗位可见性判定使用；**不是授权体系**（不建角色/数据权限），也**不是岗位绑定**
 * （绑定在 {@code aig_role_binding}）。品牌名称等主数据不在这里，只存 {@code brandId}。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_user_brand")
public class AigUserBrand extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "user_brand_id")
    private Long userBrandId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 品牌ID（业务主数据；治理层只存 ID，无外键）
     */
    private Long brandId;

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
