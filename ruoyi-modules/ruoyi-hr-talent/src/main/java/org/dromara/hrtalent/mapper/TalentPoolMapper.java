package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentPool;
import org.dromara.hrtalent.domain.vo.talent.TalentPoolVo;

/**
 * 人才池 Mapper 接口（SPEC-P4 §2.3 C 线）。
 *
 * <p>只使用 MyBatis-Plus 通用能力与 Lambda 条件构造器，不新增自定义 XML。</p>
 *
 * <p><b>授权约束</b>：本 Mapper <b>不</b>实现任何可见范围规则；
 * 人才池的可见性判定统一由
 * {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService} 完成（设计文档 §11.1）。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentPoolMapper extends BaseMapperPlus<TalentPool, TalentPoolVo> {

}
