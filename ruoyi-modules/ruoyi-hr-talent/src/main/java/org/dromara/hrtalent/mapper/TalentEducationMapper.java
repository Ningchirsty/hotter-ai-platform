package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentEducation;
import org.dromara.hrtalent.domain.vo.talent.TalentEducationVo;

/**
 * 人才教育经历 Mapper 接口。
 * <p>仅使用 MyBatis-Plus 通用能力与 Lambda 条件构造器，不新增自定义 XML；
 * 逻辑删除由实体 {@code @TableLogic} 自动过滤。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentEducationMapper extends BaseMapperPlus<TalentEducation, TalentEducationVo> {

}
