package org.dromara.aigov.token.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 签发服务令牌的入参。
 *
 * <p><b>入参里没有明文令牌</b>——明文由服务端生成、只在响应里出现一次。
 * 让调用方指定令牌等于把"凭据强度"交给调用方决定，且会把弱令牌带进系统。</p>
 *
 * @author ai-gov
 */
@Data
public class AigServiceTokenIssueBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 服务名（未删除的令牌里唯一）
     *
     * <p>刻意限制字符集：这个名字会出现在 principal（{@code service:<name>}）、
     * 会话的 deviceId、审计与排障输出里。允许任意字符会出现"看起来一样"的两个名字
     * （空格、全角、大小写混排），事后从日志上分辨不出是哪台机器在调。</p>
     */
    @NotBlank(message = "服务名不能为空", groups = {AddGroup.class})
    @Size(max = 64, message = "服务名长度不能超过 64", groups = {AddGroup.class})
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
        message = "服务名只能包含字母、数字、点、下划线、中划线，且必须以字母或数字开头",
        groups = {AddGroup.class})
    private String name;

    /**
     * 授权范围（逗号分隔的<b>权限码</b>，例如 {@code aig:capability:query}；空 = 不授予任何操作）
     *
     * <p>这里填的是平台既有的权限码，不是另造一套：scope 会被直接装进会话权限，
     * 因此"令牌能做什么"与"人的角色能做什么"用同一套口径表达，审计里可以直接对齐。</p>
     *
     * <p>长度上限<b>与列宽一致</b>（{@code aig_service_token.scopes} 是 {@code varchar(500)}）：
     * 不一致时超长入参会穿到数据库才被拒，使用者看到的是一句与权限无关的报错。</p>
     */
    @Size(max = 500, message = "授权范围长度不能超过 500", groups = {AddGroup.class})
    private String scopes;

    /**
     * 到期时间（{@code yyyy-MM-dd HH:mm:ss}；空 = 不过期）
     */
    private LocalDateTime expiresAt;

    /**
     * 备注：给谁用、为什么需要这些 scope
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class})
    private String remark;

}
