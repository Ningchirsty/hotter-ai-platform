package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentGroup;
import org.dromara.hrtalent.domain.vo.talent.TalentGroupVo;

/**
 * 人才分组 Mapper 接口（SPEC-P4 §2.3 C 线）。
 *
 * <p><b>授权约束</b>：分组可见性统一由
 * {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService} 判定，
 * 本 Mapper 不实现任何授权规则（设计文档 §11.1）。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentGroupMapper extends BaseMapperPlus<TalentGroup, TalentGroupVo> {

}
