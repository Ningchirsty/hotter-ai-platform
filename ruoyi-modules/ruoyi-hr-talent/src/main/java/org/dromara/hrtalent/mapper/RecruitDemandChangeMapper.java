package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitDemandChange;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitDemandChangeVo;

/**
 * 招聘需求变更历史 Mapper 接口。
 *
 * <p>变更历史为只追加审计数据，不提供更新与删除入口。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitDemandChangeMapper extends BaseMapperPlus<RecruitDemandChange, RecruitDemandChangeVo> {

}
