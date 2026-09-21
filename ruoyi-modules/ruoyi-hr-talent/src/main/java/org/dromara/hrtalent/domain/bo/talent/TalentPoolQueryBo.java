package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才池检索业务对象（SPEC-P4 §2.3 {@code GET /talent/pools}）。
 *
 * <p>只承载业务筛选条件；<b>可见范围条件不在此处</b>，
 * 由服务层统一经 {@code TalentScopeDomainService} 判定（设计文档 §11.1）。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentPoolQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才池名称（模糊匹配）
     */
    private String poolName;

    /**
     * 人才池编码（模糊匹配）
     */
    private String poolCode;

    /**
     * 人才池类型（稳定编码，精确匹配）
     */
    private String poolType;

    /**
     * 池管理员用户ID
     */
    private Long managerId;

    /**
     * 归属部门ID
     */
    private Long ownerDeptId;

    /**
     * 可见范围（字典 talent_visibility_type 编码，精确匹配）
     */
    private String visibilityType;

    /**
     * 状态（active/archived 等稳定编码，精确匹配）
     */
    private String status;

}
