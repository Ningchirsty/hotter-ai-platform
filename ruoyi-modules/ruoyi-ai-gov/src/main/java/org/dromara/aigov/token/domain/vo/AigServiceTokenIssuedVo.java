package org.dromara.aigov.token.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 签发服务令牌的返回体。
 *
 * <p><b>本 VO 是明文令牌在整个系统里唯一一次出现的地方</b>，因此它有两个刻意的限制：</p>
 * <ol>
 *     <li>它<b>只有签发接口</b>返回，清单/详情接口返回的是不含明文的
 *         {@link AigServiceTokenVo}；</li>
 *     <li>签发接口的 {@code @Log} 必须关掉"记录返回体"（{@code isSaveResponseData = false}）——
 *         否则明文会被写进 {@code sys_oper_log}，变成一处谁都不会去看的凭据副本。
 *         这一点有测试守着，别"顺手"加回去。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Data
public class AigServiceTokenIssuedVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 令牌ID
     */
    private Long tokenId;

    /**
     * 服务名
     */
    private String name;

    /**
     * <b>明文令牌（只此一次，请立刻交付给使用方；平台不留底、无法找回）</b>
     */
    private String token;

    /**
     * 令牌前缀（用于日后在清单里人工指认是哪一把）
     */
    private String tokenPrefix;

    /**
     * 归一化后真正落库的授权范围（逗号分隔的权限码）
     */
    private String scopes;

    /**
     * 到期时间（空 = 不过期）
     */
    private LocalDateTime expiresAt;

    /**
     * 使用方式提示：请求头名与生效路径，避免拿到令牌的人去猜。
     */
    private String usageHint;

}
