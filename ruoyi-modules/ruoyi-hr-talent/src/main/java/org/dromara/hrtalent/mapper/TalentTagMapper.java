package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentTag;
import org.dromara.hrtalent.domain.vo.talent.TalentTagVo;

/**
 * 人才标签字典 Mapper 接口（SPEC-P4 §2.3 C 线）。
 *
 * <p>标签的敏感/歧视性校验在服务层完成（设计文档 §7.6.4、§8.15），
 * 本 Mapper 只负责字典表读写。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentTagMapper extends BaseMapperPlus<TalentTag, TalentTagVo> {

}
