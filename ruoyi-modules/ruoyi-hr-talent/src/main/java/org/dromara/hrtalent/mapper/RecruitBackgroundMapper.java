package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.RecruitBackground;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitBackgroundVo;

/**
 * 招聘背调记录 Mapper 接口。
 * <p>仅使用 MyBatis-Plus 通用能力与 Lambda 条件构造器，不新增自定义 XML。
 * 泛型 VO 固定为 {@link RecruitBackgroundVo}（不含背调明细），
 * 保证 {@code selectVoPage} 等通用方法在结构上不会带出 {@code detail_cipher}。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface RecruitBackgroundMapper extends BaseMapperPlus<RecruitBackground, RecruitBackgroundVo> {

}
