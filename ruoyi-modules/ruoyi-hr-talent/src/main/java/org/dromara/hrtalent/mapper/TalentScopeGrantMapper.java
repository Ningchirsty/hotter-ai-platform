package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentScopeGrant;
import org.dromara.hrtalent.domain.vo.talent.TalentScopeGrantVo;

/**
 * 人才共享授权 Mapper 接口（SPEC-P4 §2.4、设计文档 §21.14）。
 *
 * <p>只使用 MyBatis-Plus 通用能力与 Lambda 条件构造器，不新增自定义 XML。
 * 授权有效性（{@code del_flag} / {@code revoke_flag} / 有效期）由
 * {@code TalentScopeGrantProviderImpl} 与授权服务统一校验，本接口不承担授权判定逻辑。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentScopeGrantMapper extends BaseMapperPlus<TalentScopeGrant, TalentScopeGrantVo> {

}
