package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitStandard;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitStandardVo;

/**
 * 招聘期限标准 Mapper。
 *
 * <p>字段与列名一一对应，MyBatis-Plus 通用方法即可覆盖，无需 XML。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitStandardMapper extends BaseMapperPlus<RecruitStandard, RecruitStandardVo> {

}
