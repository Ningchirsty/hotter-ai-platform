package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentFollowUp;
import org.dromara.hrtalent.domain.vo.talent.TalentFollowUpVo;

/**
 * 人才跟进记录 Mapper 接口（SPEC-P4 §2.4）。
 *
 * <p>只使用 MyBatis-Plus 通用能力与 Lambda 条件构造器，不新增自定义 XML。
 * 可见范围不由本 Mapper 决定：调用方必须先经 {@code TalentScopeDomainService} 校验人才可见，
 * 再以 {@code talent_id} 条件查询。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentFollowUpMapper extends BaseMapperPlus<TalentFollowUp, TalentFollowUpVo> {

}
