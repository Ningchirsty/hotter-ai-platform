package org.dromara.aigov.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.domain.AigUserQuota;

import java.io.Serial;
import java.io.Serializable;

/**
 * 调用人均配额视图（列表用）。
 *
 * <p><b>{@code @AutoMapper} 不是可选装饰</b>：MapStruct-Plus 只为标了这个注解的类生成转换器，
 * 少了它，{@code BaseMapperPlus.selectVoPage} 里的 {@code MapstructUtils.convert(list, voClass)}
 * 就会抛 {@code ConvertException: cannot find converter from AigUserQuota to AigUserQuotaVo}，
 * 表现为 {@code GET /aigov/quota/list} 恒 500（空表也一样）。这个坑在生产上真发生过
 * （R43），{@code AigUserQuotaVoAutoMapperTest} 钉住它。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigUserQuota.class)
public class AigUserQuotaVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long quotaId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户账号（冗余存的一份；以 {@link #userId} 为准）
     */
    private String userName;

    /**
     * 每自然日调用次数上限（null = 不限）
     */
    private Integer dailyLimit;

    /**
     * 每自然月调用次数上限（null = 不限）
     */
    private Integer monthlyLimit;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

}
