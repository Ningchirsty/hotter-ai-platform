package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitApplication;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitApplicationVo;

/**
 * 应聘记录 Mapper 接口（SPEC-P3 §2.2）。
 * <p>仅使用 MyBatis-Plus 通用能力与 Lambda 条件构造器，不新增自定义 XML。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitApplicationMapper extends BaseMapperPlus<RecruitApplication, RecruitApplicationVo> {

}
