package org.dromara.aigov.mapper;

import org.dromara.aigov.domain.AigUserQuota;
import org.dromara.aigov.domain.vo.AigUserQuotaVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 调用人均配额 Mapper（{@code aig_user_quota}）。
 *
 * <p>只有单表读写：**用量**来自调用审计表（{@code aig_invocation_audit}），
 * 它的计数语句写在 {@link AigInvocationAuditMapper} 上——一张表只有一个 Mapper，
 * 便于审查「谁在读这张表」。</p>
 *
 * @author ai-gov
 */
public interface AigUserQuotaMapper extends BaseMapperPlus<AigUserQuota, AigUserQuotaVo> {
}
