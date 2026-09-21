package org.dromara.hrtalent.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitSensitiveAudit;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitSensitiveAuditVo;

/**
 * 敏感操作审计 Mapper。
 * <p>本表为追加型审计表，业务代码请通过 {@code support/SensitiveAuditRecorder} 写入，
 * 不要直接调用 Mapper 的插入方法。</p>
 *
 * @author hr-talent
 */
public interface RecruitSensitiveAuditMapper extends BaseMapperPlus<RecruitSensitiveAudit, RecruitSensitiveAuditVo> {
}
