package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才共享授权检索业务对象（SPEC-P4 §2.4 {@code GET /talent/profiles/{id}/grants} 与
 * {@code GET /talent/grants}）。
 *
 * <p>只承载检索条件。可见范围<b>不</b>由本对象决定：按人才维度查询前必须先经
 * {@code TalentScopeDomainService} 校验该人才可见。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentScopeGrantQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才主档ID（路径参数覆盖；服务层据此限定查询范围）
     */
    private Long talentId;

    /**
     * 被授权主体类型（user/role/company_dept/dept，精确匹配）
     */
    private String granteeType;

    /**
     * 被授权主体ID（精确匹配，需与 {@link #granteeType} 成对使用）
     */
    private Long granteeId;

    /**
     * 授权级别（summary/detail/attachment，精确匹配）
     */
    private String permissionLevel;

    /**
     * 是否已撤销（0否 1是；不传表示全部）
     */
    private String revokeFlag;

    /**
     * 是否只返回当前有效授权（默认 true：过滤已撤销、未生效与已过期）
     */
    private Boolean onlyActive;

}
