package org.dromara.hrtalent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.hrtalent.domain.entity.TalentPoolMember;
import org.dromara.hrtalent.domain.vo.talent.TalentPoolMemberVo;

/**
 * 人才池成员 Mapper 接口（SPEC-P4 §2.3 C 线）。
 *
 * <p>{@code pool_id + talent_id} 由数据库唯一索引
 * {@code uk_hr_talent_pool_member} 兜底防重复；服务层在此基础上实现幂等加入。</p>
 *
 * <p><b>移出成员只改成员状态</b>，不删除本表记录、更不删除人才主档（设计文档 §8.15）。</p>
 *
 * @author hr-talent
 */
@Mapper
public interface TalentPoolMemberMapper extends BaseMapperPlus<TalentPoolMember, TalentPoolMemberVo> {

}
