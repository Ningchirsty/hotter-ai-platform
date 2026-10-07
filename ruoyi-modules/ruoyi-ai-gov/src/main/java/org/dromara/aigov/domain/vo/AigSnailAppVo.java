package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * snail-ai 客户端应用（{@code sai_app}）只读视图——用于核对「我们是谁、凭据对不对」。
 *
 * <p><b>为什么这里没有 token 字段</b>：token 是通信凭据。是否一致由 SQL 内算成
 * {@link #tokenMatched} 布尔位（与 {@code AigModelViewMapper}「是否已配置密钥」同一口径），
 * <b>原值不进 Java、不进日志、不进任何响应</b>——需要它的只有数据库比较本身。</p>
 *
 * @author ai-gov
 */
@Data
public class AigSnailAppVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（{@code sai_app.id}）
     */
    private Long id;

    /**
     * 应用唯一标识（{@code sai_app.app_id}，字符串）
     */
    private String appId;

    /**
     * 应用名称（仅用于可读的报错与日志）
     */
    private String appName;

    /**
     * 状态：1=启用 0=停用
     */
    private Integer status;

    /**
     * 库中的 {@code sai_app.token} 与本次传入的配置值是否一致（由 SQL 比较得出）
     */
    private Boolean tokenMatched;

}
