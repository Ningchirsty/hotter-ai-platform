package org.dromara.aigov.token.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * V2 服务身份：机器令牌 aig_service_token（ADR-002 追加前置）。
 *
 * <p><b>为什么需要它</b>：治理层的调用入口此前只认 Sa-Token <b>会话</b>，没有机器身份。
 * Python / 外部 Agent 若不解决身份，就只能拿一个"人"的会话（违反最小权限且不可审计）
 * 或绕过治理（更不可接受）。契约 v1 §7.3 亦把"稳定的机器身份"列为阶段 1 之前必须补的能力。</p>
 *
 * <p><b>三条不变量</b>：</p>
 * <ol>
 *     <li><b>只存哈希</b>：{@link #tokenHash} 是 SHA-256，明文只在创建时返回一次；</li>
 *     <li><b>默认拒绝</b>：{@link #scopes} 为空表示不授予任何操作，而不是"默认给全部"；</li>
 *     <li><b>可吊销、可过期</b>：{@link #enabled} / {@link #expiresAt} / {@link #status} 相互独立，
 *         吊销不删行，保留审计痕迹。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_service_token")
public class AigServiceToken extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 服务令牌ID
     */
    @TableId(value = "token_id")
    private Long tokenId;

    /**
     * 服务名（唯一），认证通过后 principal 记为 {@code service:<name>}
     */
    private String name;

    /**
     * 令牌 SHA-256（十六进制，小写）。**绝不存明文。**
     */
    private String tokenHash;

    /**
     * 令牌前缀，仅用于人工指认是哪一把（熵不足，不可用于认证）
     */
    private String tokenPrefix;

    /**
     * 授权范围：逗号分隔的权限码（如 {@code aig:model:edit,aig:task:operate}）。
     * 为空 = 不授予任何操作（默认拒绝）。
     */
    private String scopes;

    /**
     * 到期时间（空 = 不过期）
     */
    private LocalDateTime expiresAt;

    /**
     * 最近一次成功认证时间（用于发现"还在用的令牌"与"僵尸令牌"）
     */
    private LocalDateTime lastUsedAt;

    /**
     * 最近一次成功认证来源IP
     */
    private String lastUsedIp;

    /**
     * 状态（0正常 1停用）。**唯一**的启停开关——不再另设 {@code enabled}：
     * 同一件事有两个列就是两个事实源，本仓已因"两处口径不一致"吃过亏（ADR-008）。
     */
    private String status;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注：这把令牌给谁用、为什么需要这些 scope
     */
    private String remark;

}
