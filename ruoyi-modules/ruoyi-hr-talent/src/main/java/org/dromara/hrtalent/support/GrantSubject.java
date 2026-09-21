package org.dromara.hrtalent.support;

import java.io.Serializable;

/**
 * 人才共享授权主体。
 * <p>对应 {@code hr_talent_scope_grant.grantee_type + grantee_id} 组合，
 * 类型与 ID 必须成对匹配，禁止把不同主体的 ID 混入同一集合后按 {@code IN} 展开
 * （设计文档 §21.14 的 {@code :matchedType} / {@code :matchedIds} 语义）。</p>
 *
 * @param granteeType 授权主体类型（user / role / company_dept / dept 等稳定编码）
 * @param granteeId   授权主体 ID
 * @author hr-talent
 */
public record GrantSubject(String granteeType, Long granteeId) implements Serializable {

    /**
     * 授权主体类型：用户。
     */
    public static final String TYPE_USER = "user";

    /**
     * 授权主体类型：角色。
     */
    public static final String TYPE_ROLE = "role";

    /**
     * 授权主体类型：公司部门。
     */
    public static final String TYPE_COMPANY_DEPT = "company_dept";

    /**
     * 授权主体类型：部门。
     */
    public static final String TYPE_DEPT = "dept";

    /**
     * 是否有效主体（类型与 ID 均不为空）。
     *
     * @return 是否有效
     */
    public boolean valid() {
        return granteeType != null && !granteeType.isBlank() && granteeId != null;
    }

}
