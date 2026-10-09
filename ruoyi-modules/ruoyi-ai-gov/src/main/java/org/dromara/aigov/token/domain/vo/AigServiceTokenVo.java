package org.dromara.aigov.token.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.aigov.token.domain.AigServiceToken;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 服务令牌视图对象 aig_service_token。
 *
 * <p><b>这张 VO 刻意不含 {@code tokenHash}</b>：任何"列表/详情"接口都不该把哈希发给前端——
 * 它虽然不是明文，但它是"可离线爆破的校验物"，且对使用者毫无用处。
 * 明文令牌<b>只在创建时返回一次</b>，之后再也取不到。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AigServiceToken.class)
public class AigServiceTokenVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 服务令牌ID
     */
    private Long tokenId;

    /**
     * 服务名（唯一），principal 记为 {@code service:<name>}
     */
    private String name;

    /**
     * 令牌前缀，仅用于人工指认
     */
    private String tokenPrefix;

    /**
     * 授权范围（逗号分隔的权限码；空 = 不授予任何操作）
     */
    private String scopes;

    /**
     * 到期时间（空 = 不过期）
     */
    private LocalDateTime expiresAt;

    /**
     * 最近一次成功认证时间
     */
    private LocalDateTime lastUsedAt;

    /**
     * 最近一次成功认证来源IP
     */
    private String lastUsedIp;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

}
